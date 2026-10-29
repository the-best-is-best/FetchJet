import Foundation
import SharedUI
import AVFoundation
import PythonKit
import FFmpegSupport

/// In-app media engine for iOS (FULL edition).
///
/// iOS cannot spawn processes, so yt-dlp runs as a Python module inside this process:
///  * CPython 3.13 from BeeWare's Python-Apple-support (`Frameworks/Python.xcframework`,
///    stdlib copied into the app by the "Install Python Runtime" build phase),
///  * bridged with PythonKit,
///  * ffmpeg/ffprobe run in-process too (`FFmpegSupport`), and `subprocess.Popen` is
///    replaced so yt-dlp's own ffmpeg calls land there,
///  * YouTube's JS challenges are solved by the `yt-dlp-apple-webkit-jsi` plugin (WebKit via
///    ctypes) - the same stack Palladium uses.
///
/// Kotlin talks to this class through `IosMediaExecutionBridge` with plain yt-dlp CLI
/// arguments, so the shared Kotlin code is identical to desktop and Android.
///
/// The Popen replacement and the parse_options/YoutubeDL driver are adapted from
/// kewlbear/YoutubeDL-iOS (MIT).
public final class NativeMediaExtractorBridge: NSObject, IosMediaExecutionBridge {

    // MARK: - State

    private var ready = false
    private var engineVersion: String?
    private var lastError: String?
    private var preparing = false
    /// Why the YouTube JS-challenge plugin is not installed (nil = installed or not tried yet).
    private var jsSolverError: String?
    /// yt-dlp's error from the most recent failed capture/execute (shown to the user by Kotlin).
    private var lastRunError: String?
    private let stateLock = NSLock()
    /// Downloads Kotlin asked to stop (Pause / Cancel). The embedded yt-dlp cannot be killed
    /// like a process, so its progress hook checks this and raises DownloadCancelled.
    private var cancelledRuns = Set<String>()

    /// ALL Python work happens on this one serial queue: PythonKit does not manage the GIL,
    /// so the interpreter must only ever be touched from a single thread.
    private let pythonQueue = DispatchQueue(label: "fetchjet.python", qos: .userInitiated)
    /// fftools are not re-entrant.
    private let ffmpegLock = NSLock()

    public override init() {
        super.init()
        // Warm up in the background so the first Inspect does not pay the boot cost.
        DispatchQueue.global(qos: .utility).async { [weak self] in
            _ = self?.prepareEngine()
        }
    }

    // MARK: - IosMediaExecutionBridge

    public func isAvailable() -> Bool {
        stateLock.lock(); defer { stateLock.unlock() }
        return ready
    }

    public func lastErrorText() -> String? {
        stateLock.lock(); defer {
            stateLock.unlock()
        }
        return lastRunError
    }

    private func recordRunError(_ text: String?) {
        stateLock.lock(); lastRunError = text; stateLock.unlock()
    }

    public func statusText() -> String {
        stateLock.lock(); defer { stateLock.unlock() }
        if ready {
            let js = Self.jsSolverInstalled
                ? " + WebKit JS solver"
                : (jsSolverError.map {
                " (YouTube JS solver missing: \($0))"
            } ?? "")
            let imp = Self.impersonationVersion.map {
                " + curl_cffi \($0)"
            } ?? " (no impersonation: curl_cffi payload not bundled)"
            return "Ready: in-app yt-dlp \(engineVersion ?? "") + ffmpeg\(js)\(imp)"
        }
        if preparing {
            return "Preparing in-app media engine (starting Python, loading yt-dlp)..."
        }
        if let err = lastError { return "Media engine not ready: \(err)" }
        return "Media engine not started. Click 'Download Tools'."
    }

    /// Boots embedded Python, installs the yt_dlp module (bundled copy or downloaded) and
    /// imports it. Blocking; Kotlin calls it from a background dispatcher.
    public func prepareEngine() -> String? {
        precondition(!Thread.isMainThread, "NativeMediaExtractorBridge must not block the main thread")
        stateLock.lock()
        if ready {
            stateLock.unlock()
            // Engine is up; "Download Tools" doubles as "fetch the YouTube JS solver" if missing.
            ensureJsSolverPlugin()
            return nil
        }
        if preparing { stateLock.unlock(); return "Engine is still starting, try again in a moment" }
        preparing = true
        stateLock.unlock()

        var failure: String?
        var version: String?
        pythonQueue.sync {
            do {
                try EmbeddedPython.boot()
                let modulePath = try Self.ensureYtDlpModule()
                try EmbeddedPython.withGIL {
                    do {
                        try EmbeddedPython.installPopenBridge(ffmpegLock: ffmpegLock)
                        let sys = Python.import("sys")
                        let paths: [String] = Array(sys.path) ?? []
                        if !paths.contains(modulePath) {
                            sys.path.insert(0, modulePath)
                        }
                        let ytdlp = try Python.attemptImport("yt_dlp")
                        version = String(ytdlp.version.__version__)
                        // Does yt-dlp see curl_cffi? (yt_dlp.networking._curlcffi raises
                        // ImportError when the module is missing or an unsupported version.)
                        do {
                            let probe = try Python.import("builtins").eval.throwing.dynamicallyCall(withArguments: [
                                "__import__('importlib').import_module('yt_dlp.networking._curlcffi') and "
                                    + "__import__('curl_cffi').__version__",
                                Python.dict(),
                            ])
                            Self.impersonationVersion = String(probe)
                        } catch {
                            Self.impersonationVersion = nil
                            NSLog("[yt-dlp] curl_cffi unavailable: %@", Self.describe(error))
                        }
                    } catch {
                        // Describe while we still hold the GIL (it touches Python objects).
                        failure = Self.describe(error)
                    }
                }
            } catch {
                failure = (error as? EmbeddedPython.BootError)?.message ?? error.localizedDescription
            }
        }

        stateLock.lock()
        preparing = false
        if let failure {
            NSLog("[yt-dlp] engine boot failed: %@", failure)
            lastError = failure
            stateLock.unlock()
            return failure
        }
        ready = true
        engineVersion = version
        lastError = nil
        stateLock.unlock()
        NSLog("[yt-dlp] engine ready: yt-dlp %@ on Python %@, curl_cffi %@", version ?? "?", EmbeddedPython.versionString,
              Self.impersonationVersion ?? "unavailable")
        // YouTube needs a JS runtime for its n/sig challenges; on iOS that is the
        // yt-dlp-apple-webkit-jsi plugin. Not fatal when it fails: every other site still works.
        ensureJsSolverPlugin()
        return nil
    }

    /// `yt-dlp <args>` with progress, without a way to stop it (kept for older callers).
    public func executeYtDlp(
        args: [String],
        progressCallback: (any IosMediaExecutionProgressCallback)?
    ) -> Int32 {
        executeYtDlpCancellable(runId: UUID().uuidString, args: args, progressCallback: progressCallback)
    }

    /// Stops the download started with this id at its next progress update (a fraction of a
    /// second while downloading). The partial file stays, so a resume continues from it.
    /// An in-process ffmpeg step (clip, merge) still runs to its end.
    public func cancelRun(runId: String) {
        stateLock.lock(); cancelledRuns.insert(runId); stateLock.unlock()
        NSLog("[yt-dlp] cancel requested for %@", runId)
    }

    private func isRunCancelled(_ runId: String) -> Bool {
        stateLock.lock(); defer { stateLock.unlock() }
        return cancelledRuns.contains(runId)
    }

    /// `yt-dlp <args>` with progress; used for the actual download. `runId` lets Kotlin stop it.
    public func executeYtDlpCancellable(
        runId: String,
        args: [String],
        progressCallback: (any IosMediaExecutionProgressCallback)?
    ) -> Int32 {
        defer {
            stateLock.lock(); cancelledRuns.remove(runId); stateLock.unlock()
        }
        guard ensureEngine() else { return 1 }
        let argv = sanitize(args)
        NSLog("[yt-dlp] run: %@", argv.joined(separator: " "))
        var hookCount = 0
        // ffmpeg (clipping / merging / HLS) prints its "size= ... time= ..." progress to stderr;
        // forward it live so the Kotlin parser can animate the bar during those phases.
        EmbeddedPython.liveToolOutput = { line in
            progressCallback?.onProgress(progressPercent: -1, speedBytesPerSec: 0, line: line)
        }
        defer {
            EmbeddedPython.liveToolOutput = nil
        }
        let result = runYtDlp(
            argv: argv,
            isCancelled: { [weak self] in self?.isRunCancelled(runId) ?? false },
            progress: { dict in
                let (percent, speed, line) = Self.describeProgress(dict)
                hookCount += 1
                if hookCount <= 3 || hookCount % 50 == 0 {
                    NSLog("[yt-dlp] hook #%d: %@", hookCount, line)
                }
                progressCallback?.onProgress(progressPercent: percent, speedBytesPerSec: speed, line: line)
            },
            log: { level, message in
                // Surface yt-dlp's own log lines; Kotlin parses "[download]"/"[ffmpeg]" markers.
                progressCallback?.onProgress(progressPercent: -1, speedBytesPerSec: 0, line: message)
                if level == "error" || !message.hasPrefix("[download]") {
                    NSLog("[yt-dlp] %@: %@", level, message)
                }
            }
        )
        NSLog("[yt-dlp] run finished, %d progress hook calls, error: %@", hookCount, result ?? "none")
        recordRunError(result)
        if let error = result {
            progressCallback?.onProgress(progressPercent: -1, speedBytesPerSec: 0, line: "ERROR: \(error)")
            return 1
        }
        return 0
    }

    /// `yt-dlp -j <url>` style calls: returns what yt-dlp would print on stdout (the JSON).
    ///
    /// With a logger installed yt-dlp routes `to_stdout` (the -j / -g output) to
    /// `logger.debug`, so the payload arrives through the log callback as "info" lines that
    /// carry no extractor prefix such as "[youtube]". sys.stdout is captured as well, as a
    /// fallback.
    public func captureYtDlp(args: [String]) -> String? {
        guard ensureEngine() else { return nil }
        let argv = sanitize(args)
        let collected = LineCollector()
        let errors = LineCollector()
        var stdoutText = ""
        let r = runYtDlp(
            argv: argv,
            progress: nil,
            log: { level, message in
                switch level {
                case "info", "debug":
                    if !message.hasPrefix("[") {
                        collected.append(message)
                    }
                case "error":
                    errors.append(message)
                    NSLog("[yt-dlp] %@", message)
                default:
                    break
                }
            },
            captureStdout: { stdoutText = $0 }
        )
        var trimmed = collected.joined().trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            trimmed = stdoutText.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if trimmed.isEmpty {
            let reason = [errors.joined(), r ?? ""].filter {
                !$0.isEmpty
            }
            .joined(separator: "\n")
            recordRunError(reason.isEmpty ? "yt-dlp produced no output" : reason)
            NSLog("[yt-dlp] capture failed: %@", reason)
            return nil
        }
        recordRunError(nil)
        return trimmed
    }

    public func executeFfmpeg(
        args: [String],
        lineCallback: (any IosMediaLineCallback)?
    ) -> Int32 {
        // FFmpegSupport's entry point takes a full argv (program name first).
        let argv = (args.first == "ffmpeg") ? args : ["ffmpeg"] + args
        ffmpegLock.lock()
        let code = Int32(clamping: ffmpeg(argv))
        ffmpegLock.unlock()
        lineCallback?.onLine(line: code == 0 ? "ffmpeg finished" : "ffmpeg exited with \(code)")
        return code
    }

    public func probeMediaDuration(urlOrPath: String) -> Int64 {
        let url: URL?
        if urlOrPath.hasPrefix("/") || urlOrPath.hasPrefix("file://") {
            url = URL(fileURLWithPath: urlOrPath.replacingOccurrences(of: "file://", with: ""))
        } else {
            url = URL(string: urlOrPath)
        }
        guard let validUrl = url else { return 0 }
        let asset = AVURLAsset(url: validUrl)
        let durationSec = CMTimeGetSeconds(asset.duration)
        if durationSec.isFinite && durationSec > 0 { return Int64(round(durationSec)) }
        return 0
    }

    // MARK: - yt-dlp driver

    /// Equivalent of running the yt-dlp CLI with `argv` (adapted from YoutubeDL-iOS):
    /// parse_options -> YoutubeDL(opts) with our logger / progress hook -> download(urls).
    private func runYtDlp(
        argv: [String],
        isCancelled: (() -> Bool)? = nil,
        progress: (([String: PythonObject]) -> Void)?,
        log: @escaping (String, String) -> Void,
        captureStdout: ((String) -> Void)? = nil
    ) -> String? {
        precondition(!Thread.isMainThread, "NativeMediaExtractorBridge must not block the main thread")
        var outcome: String?
        pythonQueue.sync {
            // Cancelled while it was still waiting for the Python queue: do not start at all.
            if isCancelled?() == true {
                outcome = "Download cancelled"
                return
            }
            EmbeddedPython.withGIL {
                do {
                    let ytdlp = try Python.attemptImport("yt_dlp")
                    let (parser, _, allUrls, ydlOpts) = try ytdlp.parse_options.throwing
                        .dynamicallyCall(withKeywordArguments: ["argv": argv]).tuple4
                    parser.destroy()

                    ydlOpts["logger"] = Self.makeLogger(log)
                    if let progress {
                        if let isCancelled {
                            ydlOpts["progress_hooks"] = [Self.makeCancellableProgressHook(progress, isCancelled: isCancelled)]
                        } else {
                            ydlOpts["progress_hooks"] = [Self.makeProgressHook(progress)]
                        }
                    }

                    let sys = Python.import("sys")
                    let io = Python.import("io")
                    let originalStdout = sys.stdout
                    let buffer = io.StringIO()
                    if captureStdout != nil {
                        sys.stdout = buffer
                    }
                    defer {
                        if captureStdout != nil {
                            sys.stdout = originalStdout
                            captureStdout?(String(buffer.getvalue()) ?? "")
                        }
                    }

                    let ydl = ytdlp.YoutubeDL(ydlOpts)
                    try ydl.download.throwing.dynamicallyCall(withArguments: allUrls)
                } catch {
                    outcome = Self.describe(error)
                }
            }
        }
        return outcome
    }

    /// yt-dlp logger object. `debug` receives both debug lines ("[debug] ...") and, when a
    /// logger is set, everything yt-dlp would print to stdout/screen.
    private static func makeLogger(_ log: @escaping (String, String) -> Void) -> PythonObject {
        PythonClass("FetchJetLogger", members: [
            "debug": PythonInstanceMethod { params in
                let msg = String(params[1]) ?? ""
                log(msg.hasPrefix("[debug] ") ? "debug" : "info", msg)
                return Python.None
            },
            "info": PythonInstanceMethod { params in
                log("info", String(params[1]) ?? "")
                return Python.None
            },
            "warning": PythonInstanceMethod { params in
                log("warning", String(params[1]) ?? "")
                return Python.None
            },
            "error": PythonInstanceMethod { params in
                log("error", String(params[1]) ?? "")
                return Python.None
            },
        ])
        .pythonObject()
    }

    private static func makeProgressHook(_ progress: @escaping ([String: PythonObject]) -> Void) -> PythonObject {
        PythonFunction { (d: PythonObject) in
            let dict: [String: PythonObject] = Dictionary(d) ?? [:]
            progress(dict)
            return Python.None
        }
        .pythonObject
    }

    /// Progress hook written in Python so it can raise yt-dlp's own DownloadCancelled (a clean
    /// stop that keeps the .part file for resuming) when Kotlin cancelled this download.
    private static func makeCancellableProgressHook(
        _ progress: @escaping ([String: PythonObject]) -> Void,
        isCancelled: @escaping () -> Bool
    ) -> PythonObject {
        let builtins = Python.import("builtins")
        let ns = Python.dict()
        let code = """
                   def _fj_make_hook(on_progress, is_cancelled):
                       from yt_dlp.utils import DownloadCancelled
                       def _hook(d):
                           if is_cancelled(None):
                               raise DownloadCancelled('Cancelled by FetchJet')
                           on_progress(d)
                       return _hook
                   """
        builtins.exec(PythonObject(code), ns)
        // PythonFunction always receives exactly one argument (hence `is_cancelled(None)`).
        let check = PythonFunction { (_: PythonObject) -> PythonConvertible in
            isCancelled()
        }
        return ns["_fj_make_hook"](makeProgressHook(progress), check.pythonObject)
    }

    // MARK: - yt_dlp module (zipimport binary)

    /// Latest official release: a zip with a `#!` prefix, importable straight from sys.path.
    private static let ytDlpDownloadURL = URL(string: "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp")!

    private static var engineDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("FetchJet-media-engine")
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }

    private static var ytDlpModuleFile: URL {
        engineDirectory.appendingPathComponent("yt-dlp")
    }

    /// Returns the path of the yt_dlp zip to put on sys.path. Uses the copy already in
    /// Application Support, else the one shipped in the app bundle (iosApp/iosApp/yt-dlp),
    /// else downloads the latest release.
    private static func ensureYtDlpModule() throws -> String {
        let fm = FileManager.default
        let dest = ytDlpModuleFile
        if fm.fileExists(atPath: dest.path) {
            return dest.path
        }
        if let bundled = Bundle.main.url(forResource: "yt-dlp", withExtension: nil),
           fm.fileExists(atPath: bundled.path) {
            try? fm.removeItem(at: dest)
            try fm.copyItem(at: bundled, to: dest)
            return dest.path
        }
        try downloadFile(from: ytDlpDownloadURL, to: dest)
        return dest.path
    }

    /// "Download Tools" also refreshes yt-dlp to the latest release (takes effect on next launch,
    /// since a loaded module cannot be swapped inside a running interpreter).
    public func updateYtDlpModule() -> String? {
        do {
            try Self.downloadFile(from: Self.ytDlpDownloadURL, to: Self.ytDlpModuleFile)
            return nil
        } catch {
            return Self.describe(error)
        }
    }

    private static func downloadFile(from url: URL, to dest: URL) throws {
        let sem = DispatchSemaphore(value: 0)
        var outcome: Result<Void, Error> = .failure(NSError(domain: "FetchJet", code: -1))
        let task = URLSession.shared.downloadTask(with: url) { tmp, response, error in
            defer {
                sem.signal()
            }
            if let error {
                outcome = .failure(error); return
            }
            guard let tmp, let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                outcome = .failure(NSError(domain: "FetchJet", code: 2, userInfo: [
                    NSLocalizedDescriptionKey: "HTTP \((response as? HTTPURLResponse)?.statusCode ?? -1) while downloading \(url.lastPathComponent)"
                ]))
                return
            }
            do {
                try? FileManager.default.removeItem(at: dest)
                try FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
                try FileManager.default.moveItem(at: tmp, to: dest)
                outcome = .success(())
            } catch {
                outcome = .failure(error)
            }
        }
        task.resume()
        sem.wait()
        try outcome.get()
    }

    // MARK: - YouTube JS challenge solver (yt-dlp-apple-webkit-jsi)

    /// Latest release zip of https://github.com/grqz/yt-dlp-apple-webkit-jsi (pure Python,
    /// drives WebKit through ctypes). yt-dlp loads plugin zips straight from a `--plugin-dirs`
    /// folder, so no extraction is needed.
    private static let jsSolverPluginURL = URL(string:
                                               "https://github.com/grqz/yt-dlp-apple-webkit-jsi/releases/latest/download/yt-dlp-apple-webkit-jsi.zip")!

    private static var pluginsDirectory: URL {
        engineDirectory.appendingPathComponent("yt-dlp-plugins")
    }

    private static var cacheDirectory: URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            .appendingPathComponent("yt-dlp")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private static var jsSolverPluginFile: URL {
        pluginsDirectory.appendingPathComponent("yt-dlp-apple-webkit-jsi.zip")
    }

    /// curl_cffi version yt-dlp loaded, nil when impersonation is unavailable.
    private(set) static var impersonationVersion: String?

    private static var jsSolverInstalled: Bool {
        FileManager.default.fileExists(atPath: jsSolverPluginFile.path)
    }

    /// Downloads the plugin zip if it is not there yet. Blocking (call off the main thread).
    private func ensureJsSolverPlugin() {
        if Self.jsSolverInstalled {
            return
        }
        do {
            try Self.downloadFile(from: Self.jsSolverPluginURL, to: Self.jsSolverPluginFile)
            stateLock.lock(); jsSolverError = nil; stateLock.unlock()
            NSLog("[yt-dlp] installed YouTube JS solver plugin at %@", Self.jsSolverPluginFile.path)
        } catch {
            let text = Self.describe(error)
            stateLock.lock(); jsSolverError = text; stateLock.unlock()
            NSLog("[yt-dlp] could not install YouTube JS solver plugin: %@", text)
        }
    }

    // MARK: - Helpers

    private func ensureEngine() -> Bool {
        if isAvailable() { return true }
        return prepareEngine() == nil
    }

    /// Drop options that only make sense for a yt-dlp *binary* (JS runtime binaries,
    /// external downloaders) and that would error inside the embedded module, and add the
    /// iOS specifics: plugin dir (WebKit JS solver), EJS remote components, persistent cache.
    private func sanitize(_ args: [String]) -> [String] {
        var out: [String] = []
        var skipNext = false
        for a in args {
            if skipNext { skipNext = false; continue }
            switch a {
            case "--js-runtimes", "--external-downloader", "--downloader", "--ffmpeg-location":
                skipNext = true
            case "--no-progress":
                continue
            default:
                out.append(a)
            }
        }
        // Progress goes through the hook, never through stdout parsing here.
        if !out.contains("--newline") { out.insert("--newline", at: 0) }
        if Self.jsSolverInstalled, !out.contains("--plugin-dirs") {
            out.insert(contentsOf: ["--plugin-dirs", Self.pluginsDirectory.path], at: 0)
        }
        if !out.contains("--remote-components") {
            out.insert(contentsOf: ["--remote-components", "ejs:github"], at: 0)
        }
        if !out.contains("--cache-dir"), !out.contains("--no-cache-dir") {
            out.insert(contentsOf: ["--cache-dir", Self.cacheDirectory.path], at: 0)
        }
        return out
    }

    /// Turns yt-dlp's progress dict into what the Kotlin side already understands:
    /// a percentage, a speed, and a classic "[download]  42.0% of ... at ..." line.
    private static func describeProgress(_ d: [String: PythonObject]) -> (Float, Int64, String) {
        let status = d["status"].flatMap { String($0) } ?? ""
        let downloaded = d["downloaded_bytes"].flatMap { Double($0) } ?? 0
        let total = d["total_bytes"].flatMap { Double($0) }
            ?? d["total_bytes_estimate"].flatMap { Double($0) } ?? 0
        let speed = d["speed"].flatMap { Double($0) } ?? 0
        let percent: Float = total > 0 ? Float(downloaded / total * 100.0) : (status == "finished" ? 100 : -1)
        let line: String
        switch status {
        case "downloading":
            if total > 0 {
                line = "[download] \(String(format: "%.1f%%", percent)) of \(human(total)) at \(human(speed))/s"
            } else {
                // Size unknown (live/HLS): keep the shape the Kotlin parser understands.
                line = "[download]   0.0% of ~\(human(max(downloaded, 1))) at \(human(speed))/s"
            }
        case "finished":
            line = "[download] 100% of \(human(total > 0 ? total : downloaded))"
        default:
            line = "[download] \(status)"
        }
        return (percent, Int64(speed), line)
    }

    private static func human(_ bytes: Double) -> String {
        if bytes <= 0 { return "?" }
        let units = ["B", "KiB", "MiB", "GiB", "TiB"]
        var v = bytes; var i = 0
        while v >= 1024 && i < units.count - 1 { v /= 1024; i += 1 }
        return String(format: "%.2f%@", v, units[i])
    }

    /// PythonKit's `PythonError` has a useless `localizedDescription`; pull out the real
    /// Python exception and its traceback.
    fileprivate static func describe(_ error: Error) -> String {
        guard let pe = error as? PythonError else {
            return error.localizedDescription
        }
        switch pe {
        case .exception(let exc, let traceback):
            var text = String(Python.repr(exc)) ?? "\(exc)"
            if let tb = traceback {
                let tbMod = Python.import("traceback")
                let lines = tbMod.format_exception(Python.type(exc), exc, tb)
                if let joined = String(Python.str("").join(lines)) {
                    text = joined.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }
            return text
        case .invalidCall(let obj):
            return "Invalid Python call: \(obj)"
        case .invalidModule(let name):
            return "Python module not found: \(name)"
        }
    }
}

// MARK: - Embedded CPython (BeeWare Python-Apple-support)

enum EmbeddedPython {
    struct BootError: LocalizedError {
        let message: String
        var errorDescription: String? {
            message
        }
    }

    private static var booted = false
    private static var popenInstalled = false

    private static let sinkLock = NSLock()
    private static var _liveToolOutput: ((String) -> Void)?
    /// Receives every stderr/stdout line of in-process ffmpeg/ffprobe while a yt-dlp run is
    /// active (set by `executeYtDlp`).
    static var liveToolOutput: ((String) -> Void)? {
        get {
            sinkLock.lock(); defer {
                sinkLock.unlock()
            }; return _liveToolOutput
        }
        set {
            sinkLock.lock(); _liveToolOutput = newValue; sinkLock.unlock()
        }
    }
    private(set) static var versionString = "?"

    // CPython C-API entry points PythonKit does not expose. RTLD_DEFAULT finds them whether
    // Python.framework was linked or dlopen'ed by PythonKit.
    private typealias GILEnsureFn = @convention(c) () -> Int32
    private typealias GILReleaseFn = @convention(c) (Int32) -> Void
    private typealias SaveThreadFn = @convention(c) () -> UnsafeMutableRawPointer?
    private static let rtldDefault = UnsafeMutableRawPointer(bitPattern: -2)

    private static func cFunction<T>(_ name: String, _: T.Type) -> T? {
        guard let p = dlsym(rtldDefault, name) else {
            return nil
        }
        return unsafeBitCast(p, to: T.self)
    }

    private static let gilEnsure = cFunction("PyGILState_Ensure", GILEnsureFn.self)
    private static let gilRelease = cFunction("PyGILState_Release", GILReleaseFn.self)
    private static let saveThread = cFunction("PyEval_SaveThread", SaveThreadFn.self)

    /// PythonKit initialises the interpreter on the first thread that touches it and never
    /// lets go of the GIL. Serial GCD queues do not pin a thread, so every later call must
    /// acquire a thread state explicitly or CPython dereferences a null `tstate`
    /// (EXC_BAD_ACCESS at 0x10 inside PyImport_ImportModule). Wrap ALL Python access in this.
    static func withGIL<T>(_ body: () throws -> T) rethrows -> T {
        let state = gilEnsure?() ?? 0
        defer {
            gilRelease?(state)
        }
        return try body()
    }

    /// Points PythonKit at the embedded interpreter and starts it. Idempotent. Must run on the
    /// Python queue before anything else touches `Python.*`.
    static func boot() throws {
        if booted {
            return
        }
        let bundle = Bundle.main
        let pythonHome = bundle.bundleURL.appendingPathComponent("python")
        let libRoot = pythonHome.appendingPathComponent("lib")
        guard let versionFolder = (try? FileManager.default.contentsOfDirectory(atPath: libRoot.path))?
            .first(where: { $0.hasPrefix("python3.") })
        else {
            throw BootError(message: "Embedded Python runtime is missing from the app bundle (\(libRoot.path)). " +
                "Check that iosApp/Frameworks/Python.xcframework exists and the 'Install Python Runtime' build phase ran.")
        }
        let stdlib = libRoot.appendingPathComponent(versionFolder)
        let dynload = stdlib.appendingPathComponent("lib-dynload")

        setenv("PYTHONHOME", pythonHome.path, 1)
        // python-packages.zip = the pure-Python companions the desktop/Android yt-dlp builds
        // ship with (requests, urllib3, certifi, charset_normalizer, idna, websockets, mutagen);
        // yt-dlp picks them up automatically when importable. Imported via zipimport.
        var pythonPath = [stdlib.path, dynload.path]
        // python-packages/ = curl_cffi + cffi from tfourj/SwiftCurlCffi-iOS (installed by the
        // "Install Python Runtime" build phase; its .so modules were turned into frameworks).
        // yt-dlp uses it for browser impersonation, which the PornHub extractor and other
        // Cloudflare-fronted sites require (HTTP 403 without it).
        let nativePackages = bundle.bundleURL.appendingPathComponent("python-packages")
        if FileManager.default.fileExists(atPath: nativePackages.path) {
            pythonPath.insert(nativePackages.path, at: 0)
        }
        // The zip goes first so its patched certifi (where() -> SSL_CERT_FILE) shadows the
        // payload's copy; curl_cffi asks certifi for its CA bundle too.
        if let pkgs = bundle.url(forResource: "python-packages", withExtension: "zip") {
            pythonPath.insert(pkgs.path, at: 0)
        }
        setenv("PYTHONPATH", pythonPath.joined(separator: ":"), 1)
        setenv("PYTHONIOENCODING", "utf-8", 1)
        setenv("PYTHONUNBUFFERED", "1", 1)
        setenv("PYTHONDONTWRITEBYTECODE", "1", 1)
        // Tell PythonKit which libpython to use (it is also linked, so this is belt and braces).
        if let fw = bundle.privateFrameworksURL?.appendingPathComponent("Python.framework/Python").path,
           FileManager.default.fileExists(atPath: fw) {
            setenv("PYTHON_LIBRARY", fw, 1)
        }
        // OpenSSL inside the embedded Python has no access to the iOS trust store: give it a CA
        // bundle (Mozilla roots, shipped as iosApp/iosApp/cacert.pem) or every HTTPS call fails.
        if let ca = bundle.url(forResource: "cacert", withExtension: "pem") {
            setenv("SSL_CERT_FILE", ca.path, 1)
            setenv("REQUESTS_CA_BUNDLE", ca.path, 1)
        }
        // yt-dlp / plugins write caches & config lookups relative to HOME.
        let home = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        setenv("HOME", home.path, 0)

        let sys = Python.import("sys")   // initialises the interpreter; this thread now holds the GIL
        let info = sys.version_info
        let major = Int(info.major) ?? 0, minor = Int(info.minor) ?? 0
        versionString = "\(major).\(minor).\(Int(info.micro) ?? 0)"
        // Hand the GIL back so any thread can take it with PyGILState_Ensure (see withGIL).
        _ = saveThread?()
        booted = true
        guard (major, minor) >= (3, 11) else {
            throw BootError(message: "Embedded Python is \(versionString); yt-dlp needs 3.11 or newer.")
        }
    }

    /// Replaces `subprocess.Popen` so yt-dlp's ffmpeg / ffprobe invocations run in-process via
    /// FFmpegSupport. Anything else raises FileNotFoundError, which yt-dlp handles as
    /// "tool not available". Adapted from YoutubeDL-iOS.
    static func installPopenBridge(ffmpegLock: NSLock) throws {
        if popenInstalled {
            return
        }
        let subprocess = try Python.attemptImport("subprocess")
        let builtins = Python.import("builtins")
        let code = """
                   import errno, os, shlex
                   import subprocess as _sp

                   class _FetchJetPopen:
                       handler = None

                       def __init__(self, args, *pargs, **kwargs):
                           if isinstance(args, str):
                               args = shlex.split(args)
                           args = [str(a) for a in args]
                           tool = os.path.basename(args[0]) if args else ''
                           if tool in ('ffmpeg', 'ffprobe'):
                               self._fj_args = args
                               self.returncode = None
                               self.stdin = None
                               self.stdout = None
                               self.stderr = None
                               self.pid = 0
                           else:
                               raise FileNotFoundError(errno.ENOENT, os.strerror(errno.ENOENT), args[0] if args else '')

                       def communicate(self, input=None, timeout=None):
                           out, err = _FetchJetPopen.handler(self._fj_args)
                           self.returncode = int(_FetchJetPopen.last_code)
                           return (out, err)

                       def poll(self):
                           return self.returncode

                       def wait(self, timeout=None):
                           if self.returncode is None:
                               self.communicate()
                           return self.returncode

                       def kill(self):
                           pass

                       terminate = kill
                       send_signal = lambda self, sig: None

                       def __enter__(self):
                           return self

                       def __exit__(self, *exc):
                           return False

                   _sp.Popen = _FetchJetPopen
                   """
        builtins.exec(PythonObject(code), subprocess.__dict__)

        let handler = PythonFunction { (argsObj: PythonObject) -> PythonObject in
            let args: [String] = Array(argsObj) ?? []
            let (code, out, err) = runFFTool(args, lock: ffmpegLock)
            subprocess._FetchJetPopen.last_code = PythonObject(code)
            // yt-dlp opens Popen with text=True/encoding set for ffprobe JSON; return str.
            return Python.tuple([PythonObject(out), PythonObject(err)])
        }
        subprocess._FetchJetPopen.handler = handler.pythonObject
        subprocess._FetchJetPopen.last_code = PythonObject(0)
        popenInstalled = true
    }

    /// Runs ffmpeg/ffprobe in-process, capturing what they write to fd 1 / fd 2 so yt-dlp can
    /// parse version strings and ffprobe JSON exactly like with a real process.
    private static func runFFTool(_ args: [String], lock: NSLock) -> (Int, String, String) {
        guard let tool = args.first.map({ ($0 as NSString).lastPathComponent }) else {
            return (1, "", "")
        }
        lock.lock(); defer {
            lock.unlock()
        }

        let savedOut = dup(STDOUT_FILENO)
        let savedErr = dup(STDERR_FILENO)
        let outPipe = Pipe()
        let errPipe = Pipe()
        var outData = Data()
        var errData = Data()
        let group = DispatchGroup()
        let live = liveToolOutput
        for (pipe, sink) in [(outPipe, 0), (errPipe, 1)] {
            group.enter()
            DispatchQueue.global(qos: .utility).async {
                // Read incrementally so progress lines (ffmpeg separates them with \r) reach
                // the UI while the tool is still running.
                var all = Data()
                var pending = Data()
                let handle = pipe.fileHandleForReading
                while true {
                    let chunk = handle.availableData
                    if chunk.isEmpty {
                        break
                    }
                    all.append(chunk)
                    guard let live else {
                        continue
                    }
                    pending.append(chunk)
                    while let idx = pending.firstIndex(where: { $0 == 0x0A || $0 == 0x0D }) {
                        let lineData = pending.subdata(in: pending.startIndex..<idx)
                        pending.removeSubrange(pending.startIndex...idx)
                        if let line = String(data: lineData, encoding: .utf8)?
                            .trimmingCharacters(in: .whitespaces), !line.isEmpty {
                            live(line)
                        }
                    }
                }
                if let live, !pending.isEmpty,
                   let line = String(data: pending, encoding: .utf8)?.trimmingCharacters(in: .whitespaces),
                   !line.isEmpty {
                    live(line)
                }
                if sink == 0 {
                    outData = all
                } else {
                    errData = all
                }
                group.leave()
            }
        }
        fflush(stdout); fflush(stderr)
        dup2(outPipe.fileHandleForWriting.fileDescriptor, STDOUT_FILENO)
        dup2(errPipe.fileHandleForWriting.fileDescriptor, STDERR_FILENO)

        let code: Int
        switch tool {
        case "ffprobe": code = ffprobe(args)
        default: code = ffmpeg(args)
        }

        fflush(stdout); fflush(stderr)
        dup2(savedOut, STDOUT_FILENO)
        dup2(savedErr, STDERR_FILENO)
        close(savedOut); close(savedErr)
        try? outPipe.fileHandleForWriting.close()
        try? errPipe.fileHandleForWriting.close()
        group.wait()

        return (code,
            String(data: outData, encoding: .utf8) ?? "",
            String(data: errData, encoding: .utf8) ?? "")
    }
}

/// Thread-safe line accumulator for log callbacks.
private final class LineCollector: @unchecked Sendable {
    private let lock = NSLock()
    private var lines: [String] = []

    func append(_ line: String) {
        lock.lock(); lines.append(line); lock.unlock()
    }

    func joined() -> String {
        lock.lock(); defer {
            lock.unlock()
        }; return lines.joined(separator: "\n")
    }
}
