import SwiftUI
import SharedUI

final class LiveActivityBridge: NSObject, IosDownloadNotificationListener {
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
        Task { @MainActor in
            LiveActivityManager.shared.startOrUpdateLiveActivity(
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
        Task { @MainActor in
            LiveActivityManager.shared.endLiveActivity(taskId: taskId, completed: completed)
        }
    }
}

/// Receives the wake-up from iOS when background downloads finish while the app is
/// suspended or terminated, and hands the completion handler to the Kotlin engine.
final class AppDelegate: NSObject, UIApplicationDelegate {
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
    #if FETCHJET_FULL && canImport(LibTorrent)
    private let torrentBridge = NativeTorrentBridge()
    #endif

    init() {
        DownloadNotificationManager.companion.listener = bridge
        IosMediaBridgeRegistry.shared.bridge = mediaBridge
        Task { @MainActor in
            LiveActivityManager.shared.endStaleActivities()
        }
        #if FETCHJET_FULL && canImport(LibTorrent)
        IosTorrentBridgeRegistry.shared.bridge = torrentBridge
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // A .torrent opened from Files / Safari / Mail, or a magnet: link.
                .onOpenURL { url in
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