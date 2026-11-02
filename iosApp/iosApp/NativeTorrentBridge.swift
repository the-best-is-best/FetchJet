#if canImport(FJTorrent)
import Foundation
import SharedUI
import FJTorrent

/// BitTorrent engine for iOS (FULL edition): libtorrent-rasterbar through FetchJet's own
/// Objective-C++ wrapper (iosApp/FJTorrent), exposed to Kotlin via `IosTorrentBridge`.
///
/// Kotlin's `TorrentDownloader` owns task state and polls `progress()`; this class only maps
/// its calls onto one shared libtorrent session. libtorrent runs in-process, so transfers only
/// progress while the app is in the foreground.
public final class NativeTorrentBridge: NSObject, IosTorrentBridge {

    private struct Entry {
        var key: String
        var fileCount: Int
    }

    private let lock = NSRecursiveLock()
    private var entries: [String: Entry] = [:]
    private var session: TorrentSession?
    private var sessionError: String?
    private var lastErrorText: String?

    private let engineDirectory: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("FetchJet-torrent-engine")
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }()

    public override init() {
        super.init()
    }

    public func isAvailable() -> Bool {
        lock.lock(); defer {
            lock.unlock()
        }
        if session != nil {
            return true
        }
        return startSession() != nil
    }

    public func statusText() -> String {
        if isAvailable() {
            return "Ready: libtorrent \(TorrentSession.libtorrentVersion) (in-app)"
        }
        return "BitTorrent engine failed to start: \(sessionError ?? "unknown error")"
    }

    public func lastError() -> String? {
        lock.lock(); defer {
            lock.unlock()
        }
        return lastErrorText
    }

    public func fetchMetadata(magnetUri: String?, torrentData: Data?, timeoutSeconds: Int32) -> IosTorrentMetadata? {
        guard let session = sessionOrFail() else {
            return nil
        }

        if let data = torrentData {
            guard let info = try? TorrentSession.info(torrentData: data) else {
                return fail("Invalid or corrupted .torrent file")
            }
            return Self.metadata(info)
        }

        guard let magnet = magnetUri, magnet.lowercased().hasPrefix("magnet:") else {
            return fail("Invalid magnet link")
        }

        let scratch = engineDirectory.appendingPathComponent("metadata-scratch", isDirectory: true)
        try? FileManager.default.createDirectory(at: scratch, withIntermediateDirectories: true)

        let existing = session.find(magnet: magnet)
        let key: String
        if let existing {
            key = existing
        } else {
            do {
                key = try session.add(magnet: magnet, savePath: scratch.path)
            } catch {
                return fail("libtorrent rejected the magnet link: \(error.localizedDescription)")
            }
        }

        let deadline = Date().addingTimeInterval(TimeInterval(max(10, Int(timeoutSeconds))))
        var info = session.info(key)
        while info == nil && Date() < deadline {
            Thread.sleep(forTimeInterval: 0.5)
            info = session.info(key)
        }
        if existing == nil {
            session.remove(key, deleteFiles: true)
        }
        guard let info else {
            return fail("No peers answered within \(timeoutSeconds) s. Check the link or your network.")
        }
        return Self.metadata(info)
    }

    public func add(
        taskId: String,
        torrentData: Data,
        savePath: String,
        selected: [KotlinInt],
        priorityIndexes: [KotlinInt],
        priorityValues: [KotlinInt],
        disableUpload: Bool,
        downloadLimitBytesPerSec: Int64,
        uploadLimitBytesPerSec: Int64
    ) -> Bool {
        guard let session = sessionOrFail() else {
            return false
        }
        guard let info = try? TorrentSession.info(torrentData: torrentData) else {
            _ = fail("Invalid or corrupted .torrent file")
            return false
        }
        try? FileManager.default.createDirectory(atPath: savePath, withIntermediateDirectories: true)

        let priorities = Self.priorities(fileCount: info.fileCount, selected: selected,
                                         priorityIndexes: priorityIndexes, priorityValues: priorityValues)
        let upload = disableUpload ? Int64(32 * 1024) : uploadLimitBytesPerSec

        lock.lock(); defer {
            lock.unlock()
        }
        if let entry = entries[taskId], session.status(entry.key) != nil {
            session.setFilePriorities(priorities, for: entry.key)
            session.setLimits(download: downloadLimitBytesPerSec, upload: upload, for: entry.key)
            session.resume(entry.key)
            return true
        }
        entries.removeValue(forKey: taskId)

        let key: String
        if let existing = session.find(torrentData: torrentData) {
            key = existing
            session.setFilePriorities(priorities, for: key)
        } else {
            do {
                key = try session.add(torrentData: torrentData, savePath: savePath, filePriorities: priorities)
            } catch {
                _ = fail("libtorrent did not accept the torrent: \(error.localizedDescription)")
                return false
            }
        }
        entries[taskId] = Entry(key: key, fileCount: info.fileCount)
        session.setLimits(download: downloadLimitBytesPerSec, upload: upload, for: key)
        session.resume(key)
        return true
    }

    public func pause(taskId: String) {
        lock.lock(); defer {
            lock.unlock()
        }
        if let key = entries[taskId]?.key {
            session?.pause(key)
        }
    }

    public func resume(taskId: String) {
        lock.lock(); defer {
            lock.unlock()
        }
        if let key = entries[taskId]?.key {
            session?.resume(key)
        }
    }

    public func remove(taskId: String, deleteFiles: Bool) {
        lock.lock(); defer {
            lock.unlock()
        }
        guard let entry = entries.removeValue(forKey: taskId) else {
            return
        }
        session?.remove(entry.key, deleteFiles: deleteFiles)
    }

    public func setFileSelection(taskId: String, selected: [KotlinInt],
                                 priorityIndexes: [KotlinInt], priorityValues: [KotlinInt]) {
        lock.lock(); defer {
            lock.unlock()
        }
        guard let entry = entries[taskId] else {
            return
        }
        session?.setFilePriorities(Self.priorities(fileCount: entry.fileCount, selected: selected,
                                                   priorityIndexes: priorityIndexes, priorityValues: priorityValues),
                                   for: entry.key)
    }

    public func setLimits(taskId: String, downloadLimitBytesPerSec: Int64, uploadLimitBytesPerSec: Int64) {
        lock.lock(); defer {
            lock.unlock()
        }
        guard let entry = entries[taskId] else {
            return
        }
        session?.setLimits(download: downloadLimitBytesPerSec, upload: uploadLimitBytesPerSec, for: entry.key)
    }

    public func progress(taskId: String) -> IosTorrentProgress? {
        lock.lock(); defer {
            lock.unlock()
        }
        guard let entry = entries[taskId], let s = session?.status(entry.key) else {
            return nil
        }

        let stateText: String
        switch s.state {
        case .checkingFiles: stateText = "Checking files"
        case .checkingResumeData: stateText = "Checking resume data"
        case .downloadingMetadata: stateText = "Fetching metadata"
        case .downloading: stateText = "Downloading"
        case .finished: stateText = "Finished"
        case .seeding: stateText = "Seeding"
        default: stateText = "Connecting"
        }

        return IosTorrentProgress(
            downloadedBytes: s.totalWantedDone,
            wantedBytes: s.totalWanted,
            downloadRate: s.downloadRate,
            uploadRate: s.uploadRate,
            peers: s.peers,
            seeds: s.seeds,
            isFinished: s.isFinished,
            isSeeding: s.isSeeding,
            isPaused: s.isPaused,
            hasMetadata: s.hasMetadata,
            fileProgress: s.fileProgress.map {
                KotlinLong(value: $0.int64Value)
            },
            stateText: s.isPaused ? "Paused" : (s.errorMessage != nil ? "Error" : stateText),
            error: s.errorMessage
        )
    }

    public func shutdown() {
        lock.lock(); defer {
            lock.unlock()
        }
        entries.removeAll()
        session?.shutdown()
        session = nil
    }

    private func sessionOrFail() -> TorrentSession? {
        lock.lock(); defer {
            lock.unlock()
        }
        if let session {
            return session
        }
        if let s = startSession() {
            return s
        }
        lastErrorText = sessionError
        return nil
    }

    private func startSession() -> TorrentSession? {
        let port = Int.random(in: 20000...59999)
        do {
            let s = try TorrentSession(
                listenPort: port,
                userAgent: "FetchJet/1.0 libtorrent/\(TorrentSession.libtorrentVersion)",
                peerFingerprint: "-FJ1000-"
            )
            session = s
            sessionError = nil
            NSLog("[torrent] libtorrent %@ session started", TorrentSession.libtorrentVersion)
            return s
        } catch {
            sessionError = error.localizedDescription
            return nil
        }
    }

    /// Kotlin's selection + priority maps -> one libtorrent priority per file index.
    private static func priorities(fileCount: Int, selected: [KotlinInt],
                                   priorityIndexes: [KotlinInt], priorityValues: [KotlinInt]) -> [NSNumber] {
        let selectedSet = Set(selected.map {
            $0.intValue
        })
        var prio: [Int: Int] = [:]
        for (k, v) in zip(priorityIndexes, priorityValues) {
            prio[k.intValue] = v.intValue
        }
        return (0..<fileCount).map { i in
            let p: FJFilePriority
            if !selectedSet.contains(i) {
                p = .dontDownload
            } else if (prio[i] ?? 0) > 0 {
                p = .top
            } else if (prio[i] ?? 0) < 0 {
                p = .low
            } else {
                p = .default
            }
            return NSNumber(value: p.rawValue)
        }
    }

    private static func metadata(_ info: FJTorrentInfo) -> IosTorrentMetadata {
        IosTorrentMetadata(
            name: info.name,
            infoHash: info.infoHash,
            totalSize: info.totalSize,
            files: info.files.map {
                IosTorrentFileInfo(index: Int32($0.index), path: $0.path, size: $0.size)
            },
            torrentData: info.torrentData
        )
    }

    private func fail(_ message: String) -> IosTorrentMetadata? {
        lock.lock(); lastErrorText = message; lock.unlock()
        NSLog("[torrent] %@", message)
        return nil
    }
}
#endif
