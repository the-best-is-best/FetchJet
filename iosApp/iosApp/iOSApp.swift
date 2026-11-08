import SwiftUI
import SharedUI
import UserNotifications

final class LiveActivityBridge: NSObject, IosDownloadNotificationListener {
    /// Each `Task` below may run out of order: the number taken here, in call order, lets
    /// the manager drop an update that arrives after the download's end (it re-opened the
    /// activity with the last speed).
    private static let seqLock = NSLock()
    private static var seq: UInt64 = 0
    private static func nextSeq() -> UInt64 {
        seqLock.lock(); defer { seqLock.unlock() }
        seq += 1
        return seq
    }

    func onUpdateProgress(
        taskId: String,
        fileName: String,
        engineType: String,
        progress: Double,
        progressPercent: Int32,
        speedText: String,
        downloadedBytesStr: String,
        totalBytesStr: String,
        statusText: String
    ) {
        let order = Self.nextSeq()
        Task { @MainActor in
            LiveActivityManager.shared.startOrUpdateLiveActivity(
                order: order,
                taskId: taskId,
                fileName: fileName,
                engineType: engineType,
                progress: progress,
                progressPercent: Int(progressPercent),
                speedText: speedText,
                downloadedBytesStr: downloadedBytesStr,
                totalBytesStr: totalBytesStr,
                statusText: statusText
            )

            // Background execution is handled by the background NSURLSession engine now
            // (IosBackgroundHttpEngine in sharedLogic); no silent-audio keep-alive needed.
        }
    }

    func onEnd(taskId: String, completed: Bool) {
        let order = Self.nextSeq()
        Task { @MainActor in
            LiveActivityManager.shared.endLiveActivity(order: order, taskId: taskId, completed: completed)
        }
    }
}

/// Receives the wake-up from iOS when background downloads finish while the app is
/// suspended or terminated, and hands the completion handler to the Kotlin engine.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// Device sync "sent / received" notices (ids start with "device-") also show while the app is
    /// open, so a missed toast is still in Notification Center. Other notifications keep iOS's default.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if notification.request.identifier.hasPrefix("device-") {
            completionHandler([.banner, .list, .sound])
        } else {
            completionHandler([])
        }
    }

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        guard identifier == IosBackgroundSessionBridge.shared.SESSION_IDENTIFIER else {
            completionHandler()
            return
        }
        IosBackgroundSessionBridge.shared.completionHandler = completionHandler
        // Touching the queue recreates the background session so it can deliver events.
        _ = QueueManagerRepository.companion.getInstance()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    private let bridge = LiveActivityBridge()
    private let mediaBridge = NativeMediaExtractorBridge()
    private let deviceSyncBridge = NativeDeviceSyncBridge()
    #if FETCHJET_FULL && canImport(FJTorrent)
    private let torrentBridge = NativeTorrentBridge()
    #endif

    init() {
        DownloadNotificationManager.companion.listener = bridge
        IosMediaBridgeRegistry.shared.bridge = mediaBridge
        IosDeviceSyncNativeRegistry.shared.native = deviceSyncBridge
        Task { @MainActor in
            LiveActivityManager.shared.endStaleActivities()
        }
        #if FETCHJET_FULL && canImport(FJTorrent)
        IosTorrentBridgeRegistry.shared.bridge = torrentBridge
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // A .torrent opened from Files / Safari / Mail, a magnet: link, or a
                // fetchjet:// link from the Safari extension.
                .onOpenURL { url in
                    if IosFetchJetLinks.shared.handle(link: url.absoluteString) {
                        return
                    }
                    let target: String?
                    if url.isFileURL {
                        target = TorrentFilePicker.shared.importTorrent(url: url)
                    } else {
                        target = url.absoluteString
                    }
                    if let t = target, !t.isEmpty {
                        ExternalDownloadBridge.shared.dispatch(url: t, cookie: "")
                    }
                }
        }
    }
}