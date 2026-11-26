import SwiftUI
import QuickLook
import SharedLogic

struct FJToast: Identifiable, Equatable {
    let id = UUID()
    let text: String
}

@MainActor
final class FJStore: ObservableObject {
    let host: IosAppHost
    @Published private(set) var state: DashboardUiState
    @Published private(set) var language: String
    @Published private(set) var isRtl: Bool
    @Published var toast: FJToast?
    @Published var selectedTaskId: String?
    @Published var missingFileTaskId: String?
    @Published var pdf: FJPdfTarget?
    private(set) var lastEffect: DashboardUiEffect?
    var onEffect: ((DashboardUiEffect) -> Void)?
    private var watchers: [IosCancelable] = []
    private var previewWatch: Task<Void, Never>?
    private var lifecycle: [NSObjectProtocol] = []
    private var hiddenWentBackground = false

    init() {
        let host = IosAppHost()
        self.host = host
        FJPlayer.shared.host = host
        self.state = host.state
        self.language = host.language
        self.isRtl = host.isRtl
        FJFont.applyToNavigationBars()
        DeviceAuth.shared.handler = FJDeviceAuth()
        watchers.append(host.watch(
            onState: { [weak self] s in self?.apply(s) },
            onEffect: { [weak self] e in self?.handle(e) }
        ))
        watchers.append(host.watchLanguage { [weak self] lang, rtl in
            FJFont.applyToNavigationBars()
            self?.language = lang
            self?.isRtl = rtl.boolValue
        })
        let center = NotificationCenter.default
        lifecycle.append(center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.appEnteredBackground() }
        })
        lifecycle.append(center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.appBecameActive() }
        })
    }

    private func appEnteredBackground() {
        guard liveHidden.unlocked else { return }
        hiddenWentBackground = true
        Self.dismissPreview()
        hiddenCover()
    }

    private func appBecameActive() {
        guard hiddenWentBackground else { return }
        hiddenWentBackground = false
        if liveHidden.covered { send(DashboardUiEventOnHiddenReauth.shared) }
    }

    var strings: AppLocalization { host.strings() }

    var liveHidden: HiddenUiState { host.state.hidden }

    func send(_ event: DashboardUiEvent) {
        host.send(event: event)
    }

    private func apply(_ s: DashboardUiState) {
        state = s
        if let notice = s.hidden.notice {
            showToast(HiddenTextsKt.hiddenNoticeText(notice: notice))
            send(DashboardUiEventOnHiddenNoticeShown.shared)
        }
        if let notice = s.backup.notice {
            showToast(DashboardBackupKt.backupNoticeText(notice: notice))
            send(DashboardUiEventOnBackupNoticeShown.shared)
        }
    }

    func hide(_ targets: HideTargets, lockAfter: Bool = false) {
        send(DashboardUiEventOnHideTargets(taskIds: targets.taskIds, playlistIds: targets.playlistIds, folders: targets.folders, lockAfter: lockAfter))
    }

    func acceptHiddenNotice() {
        send(DashboardUiEventOnHiddenEncryptNoticeAccepted.shared)
    }

    func dismissHiddenNotice() {
        send(DashboardUiEventOnHiddenEncryptNoticeDismissed.shared)
    }

    func hiddenCover() {
        send(DashboardUiEventOnHiddenClosePlayer.shared)
        send(DashboardUiEventOnHiddenCover.shared)
    }

    func hiddenLock() {
        send(DashboardUiEventOnHiddenClosePlayer.shared)
        send(DashboardUiEventOnHiddenLock.shared)
    }

    func showToast(_ text: String) {
        toast = FJToast(text: text)
    }

    func task(_ id: String?) -> DownloadTask? {
        guard let id else { return nil }
        return state.allTasks.first { $0.id == id } ?? state.hidden.tasks.first { $0.id == id }
    }

    func openFile(_ task: DownloadTask) {
        if task.hidden {
            if let queue = host.hiddenPlaybackQueue(taskId: task.id) {
                send(DashboardUiEventOnHiddenPlayClicked(taskId: task.id))
                FJPlayer.shared.queue = queue
            } else {
                send(DashboardUiEventOnHiddenOpenFile(taskId: task.id))
            }
            return
        }
        if PdfReader.shared.canRead(task: task), !PdfMode.shared.isOff() {
            let path = PdfReader.shared.pathOf(task: task)
            if FileManager.default.fileExists(atPath: path) {
                pdf = FJPdfTarget(path: path, title: PdfReader.shared.titleOf(task: task), task: task, hidden: false)
                return
            }
        }
        if host.inAppPlayerOn(), let queue = host.playbackQueue(task: task) {
            FJPlayer.shared.queue = queue
            return
        }
        if !host.openTaskFile(task: task) {
            missingFileTaskId = task.id
        }
    }

    private func handle(_ effect: DashboardUiEffect) {
        lastEffect = effect
        if let t = effect as? DashboardUiEffectShowToast {
            showToast(t.text)
        }
        if let c = effect as? DashboardUiEffectCopyText {
            UIPasteboard.general.string = c.text
        }
        if let h = effect as? DashboardUiEffectOpenHiddenFile {
            if PdfReader.shared.isPdfName(name: h.path), !PdfMode.shared.isOff(),
               FileManager.default.fileExists(atPath: h.path) {
                let name = (h.path as NSString).lastPathComponent
                pdf = FJPdfTarget(path: h.path, title: (name as NSString).deletingPathExtension, task: nil, hidden: true)
            } else if host.openDownloadedFile(fullPath: h.path) {
                watchHiddenPreview()
            } else {
                showToast(strings.fileMissingTitle)
            }
        }
        onEffect?(effect)
    }

    private func watchHiddenPreview() {
        previewWatch?.cancel()
        previewWatch = Task { [weak self] in
            var seen = false
            var waited = 0
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 500_000_000)
                if Self.previewShown() {
                    seen = true
                } else if seen || waited > 40 {
                    break
                }
                waited += 1
            }
            if !Task.isCancelled { self?.send(DashboardUiEventOnHiddenClosePlayer.shared) }
        }
    }

    private static func dismissPreview() {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        for window in scenes.flatMap({ $0.windows }) {
            var controller = window.rootViewController
            while let c = controller {
                if c is QLPreviewController {
                    c.presentingViewController?.dismiss(animated: false)
                    return
                }
                controller = c.presentedViewController
            }
        }
    }

    static func previewShown() -> Bool {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        for window in scenes.flatMap({ $0.windows }) {
            var controller = window.rootViewController
            while let c = controller {
                if c is QLPreviewController { return true }
                controller = c.presentedViewController
            }
        }
        return false
    }
}
