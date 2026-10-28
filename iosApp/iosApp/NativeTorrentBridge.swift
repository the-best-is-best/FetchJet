#if canImport(LibTorrent)
import Foundation
import SharedUI
import LibTorrent

/// BitTorrent engine for iOS (FULL edition): libtorrent-rasterbar through the LibTorrent-Swift
/// wrapper (the one iTorrent uses), exposed to Kotlin via `IosTorrentBridge`.
///
/// Kotlin's `TorrentDownloader` owns task state and polls `progress()`; this class only maps
/// its calls onto one shared libtorrent session. libtorrent runs in-process, so transfers only
/// progress while the app is in the foreground.
///
/// Rate limits: the wrapper has no per-torrent limit API, so the strictest per-task limits are
/// applied session-wide (and "disable upload" becomes a 1 B/s global upload cap while such a
/// task exists).
public final class NativeTorrentBridge: NSObject, IosTorrentBridge {

    // MARK: - State

    private struct Entry {
        var handle: TorrentHandle
        var disableUpload: Bool
        var downloadLimit: Int64
        var uploadLimit: Int64
    }

    private let lock = NSRecursiveLock()
    private var entries: [String: Entry] = [:]
    private var session: Session?
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

    // MARK: - IosTorrentBridge

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
            return "Ready: libtorrent \(LibTorrent.Version.libtorrentVersion) (in-app)"
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

        // .torrent bytes: parse locally, nothing to fetch.
        if let data = torrentData {
            guard let file = TorrentFile(with: data) else {
                return fail("Invalid or corrupted .torrent file")
            }
            return Self.metadata(name: file.name, hashes: file.infoHashes, files: file.files, data: data)
        }

        guard let magnetText = magnetUri, let url = URL(string: magnetText),
              let magnet = MagnetURI(with: url)
        else {
            return fail("Invalid magnet link")
        }

        // Fetch into a scratch storage; the real download is added later by `add`.
        let scratch = engineDirectory.appendingPathComponent("metadata-scratch", isDirectory: true)
        try? FileManager.default.createDirectory(at: scratch, withIntermediateDirectories: true)
        let storageId = registerStorage(path: scratch.path)

        // Reuse a handle that is already in the session (e.g. a task that is downloading).
        let existing = session.torrentsMap[magnet.infoHashes]
        let handle: TorrentHandle
        if let existing {
            handle = existing
        } else {
            guard let added = session.addTorrent(magnet, to: storageId) else {
                return fail("libtorrent rejected the magnet link")
            }
            handle = added
        }

        let deadline = Date().addingTimeInterval(TimeInterval(max(10, Int(timeoutSeconds))))
        var snapshot = handle.snapshot
        while !snapshot.hasMetadata && Date() < deadline {
            Thread.sleep(forTimeInterval: 0.5)
            handle.updateSnapshot()
            snapshot = handle.snapshot
        }
        guard snapshot.hasMetadata else {
            if existing == nil {
                session.removeTorrent(handle, deleteFiles: true)
            }
            return fail("No peers answered within \(timeoutSeconds) s. Check the link or your network.")
        }

        // The wrapper writes <torrentsPath>/<hash>.torrent once metadata arrives.
        let torrentPath = session.torrentFilePath(forInfoHashes: handle.infoHashes)
        var data: Data?
        let fileDeadline = Date().addingTimeInterval(10)
        while data == nil && Date() < fileDeadline {
            data = FileManager.default.contents(atPath: torrentPath)
            if data == nil {
                Thread.sleep(forTimeInterval: 0.2)
            }
        }
        let files = snapshot.files
        let name = snapshot.name
        let hashes = handle.infoHashes
        if existing == nil {
            // Metadata only: do not keep downloading into the scratch folder.
            session.removeTorrent(handle, deleteFiles: true)
        }
        guard let data else {
            return fail("Metadata received but the .torrent file could not be read")
        }
        return Self.metadata(name: name, hashes: hashes, files: files, data: data)
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
        guard let file = TorrentFile(with: torrentData) else {
            _ = fail("Invalid or corrupted .torrent file")
            return false
        }
        try? FileManager.default.createDirectory(atPath: savePath, withIntermediateDirectories: true)

        let fileCount = file.files.count
        let plan = Self.priorityPlan(fileCount: fileCount, selected: selected,
                                     priorityIndexes: priorityIndexes, priorityValues: priorityValues)

        lock.lock(); defer {
            lock.unlock()
        }
        // Resume after pause: the torrent is still in the session.
        if var entry = entries[taskId], entry.handle.snapshot.isValid {
            entry.disableUpload = disableUpload
            entry.downloadLimit = downloadLimitBytesPerSec
            entry.uploadLimit = uploadLimitBytesPerSec
            entries[taskId] = entry
            Self.applyPlan(plan, to: entry.handle)
            applyGlobalLimits()
            entry.handle.resume()
            return true
        }
        entries.removeValue(forKey: taskId)

        let handle: TorrentHandle
        if let existing = session.torrentsMap[file.infoHashes] {
            // Same torrent already in the session (e.g. left over from a metadata fetch).
            handle = existing
            Self.applyPlan(plan, to: handle)
        } else {
            // The wrapper's intended flow: priorities go on the TorrentFile and become
            // add_torrent_params.file_priorities, so libtorrent starts with the right set.
            for (prio, indexes) in plan where !indexes.isEmpty {
                file.setFilesPriority(prio, at: indexes.map {
                    NSNumber(value: $0)
                })
            }
            let storageId = registerStorage(path: savePath)
            guard let added = session.addTorrent(file, to: storageId) else {
                _ = fail("libtorrent did not accept the torrent")
                return false
            }
            handle = added
        }
        entries[taskId] = Entry(handle: handle, disableUpload: disableUpload,
                                downloadLimit: downloadLimitBytesPerSec, uploadLimit: uploadLimitBytesPerSec)
        applyGlobalLimits()
        handle.resume()
        return true
    }

    public func pause(taskId: String) {
        lock.lock(); defer {
            lock.unlock()
        }
        entries[taskId]?.handle.pause()
    }

    public func resume(taskId: String) {
        lock.lock(); defer {
            lock.unlock()
        }
        entries[taskId]?.handle.resume()
    }

    public func remove(taskId: String, deleteFiles: Bool) {
        lock.lock(); defer {
            lock.unlock()
        }
        guard let entry = entries.removeValue(forKey: taskId) else {
            return
        }
        session?.removeTorrent(entry.handle, deleteFiles: deleteFiles)
        applyGlobalLimits()
    }

    public func setFileSelection(taskId: String, selected: [KotlinInt],
                                 priorityIndexes: [KotlinInt], priorityValues: [KotlinInt]) {
        lock.lock(); defer {
            lock.unlock()
        }
        guard let entry = entries[taskId] else {
            return
        }
        let count = entry.handle.snapshot.files.count
        Self.applyPlan(Self.priorityPlan(fileCount: count, selected: selected,
                                         priorityIndexes: priorityIndexes, priorityValues: priorityValues),
                       to: entry.handle)
    }

    public func setLimits(taskId: String, downloadLimitBytesPerSec: Int64, uploadLimitBytesPerSec: Int64) {
        lock.lock(); defer {
            lock.unlock()
        }
        guard var entry = entries[taskId] else {
            return
        }
        entry.downloadLimit = downloadLimitBytesPerSec
        entry.uploadLimit = uploadLimitBytesPerSec
        entries[taskId] = entry
        applyGlobalLimits()
    }

    public func progress(taskId: String) -> IosTorrentProgress? {
        lock.lock(); defer {
            lock.unlock()
        }
        guard let entry = entries[taskId] else {
            return nil
        }
        entry.handle.updateSnapshot()
        let s = entry.handle.snapshot
        guard s.isValid else {
            return nil
        }

        let stateText: String
        var error: String?
        switch s.state {
        case .checkingFiles: stateText = "Checking files"
        case .checkingResumeData: stateText = "Checking resume data"
        case .downloadingMetadata: stateText = "Fetching metadata"
        case .downloading: stateText = "Downloading"
        case .finished: stateText = "Finished"
        case .seeding: stateText = "Seeding"
        case .paused: stateText = "Paused"
        case .storageError:
            stateText = "Storage error"
            error = "Cannot write to the download folder"
        @unknown default: stateText = "Connecting"
        }
        if s.isStorageMissing {
            error = "Download folder is missing"
        }

        return IosTorrentProgress(
            downloadedBytes: Int64(s.totalWantedDone),
            wantedBytes: Int64(s.totalWanted),
            downloadRate: Int64(s.downloadRate),
            uploadRate: Int64(s.uploadRate),
            peers: Int32(s.numberOfPeers),
            seeds: Int32(s.numberOfSeeds),
            isFinished: s.isFinished,
            isSeeding: s.isSeed,
            isPaused: s.isPaused,
            hasMetadata: s.hasMetadata,
            fileProgress: s.files.map {
                KotlinLong(value: Int64($0.downloaded))
            },
            stateText: stateText,
            error: error
        )
    }

    public func shutdown() {
        lock.lock(); defer {
            lock.unlock()
        }
        entries.removeAll()
        session?.pause()
        session = nil
    }

    // MARK: - Session

    private func sessionOrFail() -> Session? {
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

    /// Creates the libtorrent session (DHT, LSD, UPnP/NAT-PMP, uTP, encryption enabled).
    private func startSession() -> Session? {
        let fm = FileManager.default
        let downloads = fm.urls(for: .documentDirectory, in: .userDomainMask).first!
            .appendingPathComponent("Torrents", isDirectory: true)
        let torrents = engineDirectory.appendingPathComponent("torrents", isDirectory: true)
        let resume = engineDirectory.appendingPathComponent("fastresume", isDirectory: true)
        for dir in [downloads, torrents, resume] {
            try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        }

        let settings = Session.Settings()
        settings.agentName = "FetchJet/1.0 libtorrent/\(LibTorrent.Version.libtorrentVersion)"
        settings.peerFingerprint = "-FJ1000-"
        settings.preallocateStorage = false
        settings.maxActiveTorrents = 20
        settings.maxDownloadingTorrents = 10
        settings.maxUploadingTorrents = 10
        settings.maxDownloadSpeed = 0
        settings.maxUploadSpeed = 0
        settings.isDhtEnabled = true
        settings.isLsdEnabled = true
        settings.isUtpEnabled = true
        settings.isUpnpEnabled = true
        settings.isNatEnabled = true
        settings.encryptionPolicy = .enabled
        settings.validateHttpsTrackers = false
        settings.port = 6881
        settings.portBindRetries = 10
        settings.outgoingInterfaces = ""
        settings.listenInterfaces = "0.0.0.0:6881,[::]:6881"
        settings.proxyType = .none
        settings.proxyHostname = ""
        settings.proxyHostPort = 0
        settings.proxyAuthRequired = false
        settings.proxyUsername = ""
        settings.proxyPassword = ""
        settings.proxyPeerConnections = false

        let s = Session(downloads, torrentsPath: torrents, fastResumePath: resume,
                        settings: settings, storages: [:])
        session = s
        sessionError = nil
        NSLog("[torrent] libtorrent %@ session started", LibTorrent.Version.libtorrentVersion)
        return s
    }

    /// Every save folder is a "storage" for the wrapper; register one per path.
    private func registerStorage(path: String) -> UUID {
        guard let session else {
            return UUID()
        }
        var storages = session.storages
        if let existing = storages.values.first(where: { $0.url.path == path }) {
            return existing.uuid
        }
        let model = StorageModel()
        model.uuid = UUID()
        model.name = (path as NSString).lastPathComponent
        model.url = URL(fileURLWithPath: path, isDirectory: true)
        model.pathBookmark = Data()
        model.resolved = true
        model.allowed = true
        storages[model.uuid] = model
        session.storages = storages
        return model.uuid
    }

    /// Kotlin's selection + priority maps -> file indexes grouped per libtorrent priority.
    private static func priorityPlan(fileCount: Int, selected: [KotlinInt],
                                     priorityIndexes: [KotlinInt], priorityValues: [KotlinInt]) -> [FileEntry.Priority: [Int]] {
        let selectedSet = Set(selected.map {
            $0.intValue
        })
        var prio: [Int: Int] = [:]
        for (k, v) in zip(priorityIndexes, priorityValues) {
            prio[k.intValue] = v.intValue
        }
        var plan: [FileEntry.Priority: [Int]] = [.dontDownload: [], .lowPriority: [], .defaultPriority: [], .topPriority: []]
        for i in 0..<fileCount {
            let p: FileEntry.Priority
            if !selectedSet.contains(i) {
                p = .dontDownload
            } else if (prio[i] ?? 0) > 0 {
                p = .topPriority
            } else if (prio[i] ?? 0) < 0 {
                p = .lowPriority
            } else {
                p = .defaultPriority
            }
            plan[p, default: []].append(i)
        }
        return plan
    }

    /// Applies a plan to a live handle. Guarded: libtorrent throws (and would abort the app)
    /// when the handle is not ready yet; a failed call is retried by the next refresh.
    private static func applyPlan(_ plan: [FileEntry.Priority: [Int]], to handle: TorrentHandle) {
        ObjCatch.try {
            for (prio, indexes) in plan where !indexes.isEmpty {
                handle.setFilesPriority(prio, at: indexes.map {
                    NSNumber(value: $0)
                })
            }
        }
    }

    /// The wrapper only has session-wide limits: apply the strictest active per-task values.
    private func applyGlobalLimits() {
        guard let session else {
            return
        }
        let downs = entries.values.map {
            $0.downloadLimit
        }
        .filter {
            $0 > 0
        }
        let ups = entries.values.map {
            $0.uploadLimit
        }
        .filter {
            $0 > 0
        }
        let noUpload = entries.values.contains {
            $0.disableUpload
        }
        let settings = session.settings
        settings.maxDownloadSpeed = UInt(downs.min() ?? 0)
        settings.maxUploadSpeed = noUpload ? 1 : UInt(ups.min() ?? 0)
        session.settings = settings
    }

    private static func metadata(name: String, hashes: TorrentHashes, files: [FileEntry], data: Data) -> IosTorrentMetadata {
        var total: Int64 = 0
        let infos: [IosTorrentFileInfo] = files.map { f in
            total += Int64(f.size)
            return IosTorrentFileInfo(index: f.index, path: f.path, size: Int64(f.size))
        }
        return IosTorrentMetadata(
            name: name,
            infoHash: (hashes.best as NSData).hex(),
            totalSize: total,
            files: infos,
            torrentData: data
        )
    }

    private func fail(_ message: String) -> IosTorrentMetadata? {
        lock.lock(); lastErrorText = message; lock.unlock()
        NSLog("[torrent] %@", message)
        return nil
    }
}
#endif
