import SwiftUI
import UIKit
import SharedLogic

enum FJDialogKind: Equatable {
    case pairing(String)
    case receiving
    case whenDone
    case whenDoneError
    case remove
    case missingFile(String)
    case duplicate(String)
    case fileConflict
    case clipboard(String)
    case browserFallback(String)
    case update(String)

    var id: String { String(describing: self) }
}

@MainActor
final class FJDialogsModel: ObservableObject {
    static let shared = FJDialogsModel()
    @Published private(set) var update: UpdateState?
    @Published private(set) var whenDoneSeconds: Int?
    @Published private(set) var whenDonePending: WhenDoneAction?
    @Published private(set) var whenDoneError: String?
    private(set) var offerKey: String?
    private(set) var offerStart = Date()
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        UpdateManager.shared.onAppStart()
        poll()
    }

    func poll() {
        let u = UpdateManager.shared.state.value as? UpdateState
        if !(u?.isEqual(update) ?? (update == nil)) { update = u }
        let secs = (WhenDone.shared.secondsLeft.value as? KotlinInt)?.intValue
        if secs != whenDoneSeconds { whenDoneSeconds = secs }
        let pending = WhenDone.shared.pending.value as? WhenDoneAction
        if pending != whenDonePending { whenDonePending = pending }
        let err = WhenDone.shared.error.value as? String
        if err != whenDoneError { whenDoneError = err }
    }

    func noteOffer(_ offer: PairingStateOffer?) {
        let key = offer.map { "\($0.peerId)|\($0.fileName)" }
        if key != offerKey {
            offerKey = key
            offerStart = Date()
        }
    }

    func active(_ store: FJStore) -> FJDialogKind? {
        let st = store.state
        if let p = st.devicePairing {
            if let o = p as? PairingStateOffer { return .pairing("offer|\(o.peerId)|\(o.fileName)") }
            if let c = p as? PairingStateConfirm { return .pairing("confirm|\(c.peerId)|\(c.code)") }
            if p is PairingStateConnecting { return .pairing("connecting|\(p.peerName)") }
            return .pairing("failed|\(p.peerName)")
        }
        if let r = st.deviceReceiving, r is ReceiveStateReceiving || r is ReceiveStateFailed { return .receiving }
        if whenDoneSeconds != nil { return .whenDone }
        if whenDoneError != nil { return .whenDoneError }
        if st.removeConfirmationTaskId != nil || !st.removeConfirmationTaskIds.isEmpty { return .remove }
        if let id = store.missingFileTaskId, store.task(id) != nil { return .missingFile(id) }
        if !st.isAddDialogVisible {
            if let d = st.duplicateTaskAlert { return .duplicate(d.id) }
            if st.fileConflictAlert != nil { return .fileConflict }
        }
        if let url = st.clipboardOfferUrl { return .clipboard(url) }
        if let url = st.browserFallbackUrl { return .browserFallback(url) }
        if let u = update, u.supported, u.showDialog, let r = u.available { return .update(r.version) }
        return nil
    }
}

final class FJPassthroughWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard !isHidden else { return nil }
        return super.hitTest(point, with: event)
    }
}

@MainActor
final class FJDialogWindow {
    static let shared = FJDialogWindow()
    private var window: FJPassthroughWindow?

    func install(store: FJStore) {
        guard window == nil else { return }
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first else { return }
        let w = FJPassthroughWindow(windowScene: scene)
        w.windowLevel = .alert + 1
        w.backgroundColor = .clear
        let host = UIHostingController(rootView: FJDialogOverlay()
            .environmentObject(store)
            .environmentObject(FJDialogsModel.shared))
        host.view.backgroundColor = .clear
        w.rootViewController = host
        w.overrideUserInterfaceStyle = scene.windows.first { $0 !== w }?.overrideUserInterfaceStyle ?? .unspecified
        w.isHidden = true
        window = w
    }

    func setVisible(_ visible: Bool) {
        guard let window, window.isHidden == visible else { return }
        window.isHidden = !visible
        if visible { window.rootViewController?.view.setNeedsLayout() }
    }

    func applyStyle(_ themeMode: String) {
        switch themeMode {
        case "light": window?.overrideUserInterfaceStyle = .light
        case "dark": window?.overrideUserInterfaceStyle = .dark
        default: window?.overrideUserInterfaceStyle = .unspecified
        }
    }
}

struct AppDialogsHost: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject private var model = FJDialogsModel.shared
    private let timer = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect()

    var body: some View {
        let active = model.active(store)
        Color.clear
            .frame(width: 0, height: 0)
            .onAppear {
                model.start()
                FJDialogWindow.shared.install(store: store)
                FJDialogWindow.shared.applyStyle(store.state.themeMode)
                model.noteOffer(store.state.devicePairing as? PairingStateOffer)
                FJDialogWindow.shared.setVisible(model.active(store) != nil)
            }
            .onReceive(timer) { _ in model.poll() }
            .onChange(of: active?.id, initial: true) { _, _ in
                model.noteOffer(store.state.devicePairing as? PairingStateOffer)
                FJDialogWindow.shared.setVisible(active != nil)
            }
            .onChange(of: store.state.themeMode) { _, mode in FJDialogWindow.shared.applyStyle(mode) }
    }
}

struct FJDialogOverlay: View {
    @EnvironmentObject var store: FJStore
    @EnvironmentObject var model: FJDialogsModel

    var body: some View {
        let active = model.active(store)
        ZStack {
            if let active {
                Color.black.opacity(0.45)
                    .ignoresSafeArea()
                    .onTapGesture { tapOutside(active) }
                    .transition(.opacity)
                dialog(active)
                    .id(active.id)
                    .transition(.scale(scale: 0.94).combined(with: .opacity))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .animation(.easeOut(duration: 0.18), value: active?.id)
        .environment(\.layoutDirection, store.isRtl ? .rightToLeft : .leftToRight)
        .id(store.language)
    }

    private func tapOutside(_ kind: FJDialogKind) {
        switch kind {
        case .remove: store.send(DashboardUiEventOnDismissRemoveDialog.shared)
        case .missingFile: store.missingFileTaskId = nil
        case .clipboard: store.send(DashboardUiEventOnClipboardOfferDismissed.shared)
        case .browserFallback: store.send(DashboardUiEventOnDismissBrowserFallback.shared)
        case .update:
            if (model.update?.downloadProgress ?? -1) < 0 { UpdateManager.shared.dismiss() }
        case .whenDoneError: WhenDone.shared.dismissError()
        case .duplicate: store.send(DashboardUiEventOnDismissDuplicateDialog.shared)
        case .fileConflict: store.send(DashboardUiEventOnDismissFileConflictDialog.shared)
        default: break
        }
    }

    @ViewBuilder
    private func dialog(_ kind: FJDialogKind) -> some View {
        switch kind {
        case .pairing:
            if let p = store.state.devicePairing { FJPairingDialog(pairing: p) }
        case .receiving:
            if let r = store.state.deviceReceiving { FJReceiveDialog(receiving: r) }
        case .whenDone: FJWhenDoneDialog()
        case .whenDoneError: FJWhenDoneErrorDialog()
        case .remove: FJRemoveDialog()
        case .missingFile(let id):
            if let t = store.task(id) { FJMissingFileDialog(task: t) }
        case .duplicate:
            if let t = store.state.duplicateTaskAlert { FJDuplicateDialog(task: t) }
        case .fileConflict:
            if let d = store.state.fileConflictAlert { FJFileConflictDialog(data: d) }
        case .clipboard(let url): FJClipboardOfferDialog(url: url)
        case .browserFallback(let url): FJBrowserFallbackDialog(url: url)
        case .update:
            if let u = model.update, let r = u.available { FJUpdateDialog(state: u, release: r) }
        }
    }
}
