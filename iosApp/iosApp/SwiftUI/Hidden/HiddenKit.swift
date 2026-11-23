import SwiftUI
import LocalAuthentication
import UniformTypeIdentifiers
import SharedLogic

struct HideTargets: Identifiable {
    var taskIds: [String] = []
    var playlistIds: [String] = []
    var folders: [String] = []
    let id = UUID()
}

enum HiddenSheetKind: Int, Identifiable {
    case setPin, unlock, change, remove, restore
    var id: Int { rawValue }
}

struct HiddenPinReject {
    let message: String?
    let clearsPin: Bool
}

@MainActor
final class LockCountdown: ObservableObject {
    @Published private(set) var left = 0
    private var ticker: Task<Void, Never>?

    var locked: Bool { left > 0 }

    func start(seconds: Int) {
        ticker?.cancel()
        left = max(0, seconds)
        guard left > 0 else { return }
        let end = Date().addingTimeInterval(Double(seconds))
        ticker = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 250_000_000)
                guard let self else { return }
                let remaining = max(0, Int(end.timeIntervalSinceNow.rounded(.up)))
                if remaining != self.left { self.left = remaining }
                if remaining == 0 { return }
            }
        }
    }

    func message(_ error: String?) -> String? {
        locked ? HiddenTextsKt.hiddenLockedText(seconds: Int32(left)) : error
    }

    func reject(_ h: HiddenUiState, _ s: AppLocalization) -> HiddenPinReject? {
        if h.error == .wrongPin {
            return HiddenPinReject(message: HiddenTextsKt.hiddenWrongPinText(triesLeft: h.triesLeft), clearsPin: true)
        }
        if h.error == .locked {
            start(seconds: Int(h.lockedSeconds))
            return HiddenPinReject(message: nil, clearsPin: true)
        }
        if h.error == .invalidPin {
            return HiddenPinReject(message: s.hiddenInvalidPin, clearsPin: false)
        }
        return nil
    }

    deinit { ticker?.cancel() }
}

struct FJHiddenNoticeDialog: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let s = store.strings
        FJDialogFrame(title: s.hiddenNoticeTitle) {
            Text(s.hiddenNoticeBody).fjBody()
        } buttons: {
            FJDialogButton(title: s.cancel, kind: .outlined) { store.dismissHiddenNotice() }
            FJDialogButton(title: s.hiddenNoticeAccept) { store.acceptHiddenNotice() }
        }
    }
}

struct FJHiddenOpeningDialog: View {
    let fraction: Float

    var body: some View {
        let clamped = min(max(fraction, 0), 1)
        FJDialogFrame(title: HiddenTextsKt.hiddenOpeningText(fraction: clamped)) {
            VStack(spacing: 10) {
                ProgressView(value: Double(clamped))
                    .tint(FJTheme.color.primary)
            }
        } buttons: {
            EmptyView()
        }
    }
}

struct HiddenPinField: View {
    let label: String
    @Binding var text: String
    var disabled = false

    var body: some View {
        SettingsField(label: label, text: $text, secure: true, keyboard: .numberPad)
            .textContentType(.oneTimeCode)
            .disabled(disabled)
            .opacity(disabled ? 0.5 : 1)
            .onChange(of: text) { _, value in
                let cleaned = String(value.filter { $0.isASCII && $0.isNumber }.prefix(6))
                if cleaned != value { text = cleaned }
            }
    }
}

struct HiddenSheetBody<Content: View>: View {
    @EnvironmentObject var store: FJStore
    let title: String
    let onCancel: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) { content }
                    .frame(maxWidth: 480, alignment: .leading)
                    .padding(16)
                    .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(FJTheme.color.background.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.strings.cancel, action: onCancel)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .hiddenPrivacyCover()
    }
}

struct HiddenErrorText: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.fj(12, .medium))
            .foregroundStyle(FJTheme.color.error)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct HiddenSetPinSheet: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    var resetsPin = false
    let onSet: () -> Void
    @State private var pin = ""
    @State private var again = ""
    @State private var error: String?
    @State private var showRestore = false

    private var valid: Bool { pin.count >= 4 && pin.count <= 6 }

    var body: some View {
        let s = store.strings
        let title = resetsPin ? s.hiddenResetPinTitle : s.hiddenSetPinTitle
        HiddenSheetBody(title: title, onCancel: { dismiss() }) {
            Text(s.hiddenSetPinHint)
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            HiddenPinField(label: s.hiddenEnterPin, text: $pin)
            HiddenPinField(label: s.hiddenConfirmPin, text: $again)
            if let error { HiddenErrorText(text: error) }
            FJDialogButton(title: title, enabled: valid && !again.isEmpty, fill: true) { submit() }
            if !resetsPin {
                FJDialogButton(title: s.hiddenRecoveryRestoreTitle, kind: .text, fill: true) { showRestore = true }
            }
        }
        .sheet(isPresented: $showRestore) { HiddenRestoreSheet().environmentObject(store) }
    }

    private func submit() {
        let s = store.strings
        if pin != again {
            error = s.hiddenPinMismatch
            return
        }
        if resetsPin {
            store.send(DashboardUiEventOnHiddenResetPin(pin: pin))
            let h = store.liveHidden
            if h.error == .invalidPin {
                error = s.hiddenInvalidPin
                return
            }
            if h.unlocked { onSet() }
            dismiss()
            return
        }
        store.send(DashboardUiEventOnHiddenSetPin(pin: pin))
        let h = store.liveHidden
        if h.lockType == nil || h.error == .invalidPin {
            error = s.hiddenInvalidPin
            return
        }
        onSet()
        dismiss()
    }
}

struct HiddenUnlockSheet: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    let onUnlocked: () -> Void
    @StateObject private var lock = LockCountdown()
    @State private var pin = ""
    @State private var error: String?

    var body: some View {
        let s = store.strings
        let h = store.state.hidden
        Group {
            if h.pinResetAllowed {
                HiddenSetPinSheet(resetsPin: true, onSet: onUnlocked)
            } else {
                HiddenSheetBody(title: s.hiddenTitle, onCancel: { dismiss() }) {
                    HiddenPinField(label: s.hiddenEnterPin, text: $pin, disabled: lock.locked)
                    if let message = lock.message(error) { HiddenErrorText(text: message) }
                    FJDialogButton(title: s.ok, enabled: pin.count >= 4 && !lock.locked, fill: true) { submit() }
                    if h.canResetPin {
                        FJDialogButton(title: s.hiddenForgotPin, kind: .text, fill: true) {
                            store.send(DashboardUiEventOnHiddenForgotPin.shared)
                        }
                    }
                }
            }
        }
        .onDisappear {
            if store.state.hidden.pinResetAllowed { store.send(DashboardUiEventOnHiddenResetCancel.shared) }
        }
    }

    private func submit() {
        store.send(DashboardUiEventOnHiddenUnlock(pin: pin))
        let h = store.liveHidden
        if h.unlocked {
            onUnlocked()
            dismiss()
            return
        }
        pin = ""
        error = lock.reject(h, store.strings)?.message
    }
}

struct HiddenChangePinSheet: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var lock = LockCountdown()
    @State private var old = ""
    @State private var new = ""
    @State private var again = ""
    @State private var error: String?

    var body: some View {
        let s = store.strings
        HiddenSheetBody(title: s.hiddenChangePin, onCancel: { dismiss() }) {
            HiddenPinField(label: s.hiddenOldPin, text: $old, disabled: lock.locked)
            HiddenPinField(label: s.hiddenNewPin, text: $new, disabled: lock.locked)
            HiddenPinField(label: s.hiddenConfirmPin, text: $again, disabled: lock.locked)
            if let message = lock.message(error) { HiddenErrorText(text: message) }
            FJDialogButton(title: s.hiddenChangePin, enabled: old.count >= 4 && new.count >= 4 && !again.isEmpty && !lock.locked, fill: true) { submit() }
        }
    }

    private func submit() {
        let s = store.strings
        if new != again {
            error = s.hiddenPinMismatch
            return
        }
        store.send(DashboardUiEventOnHiddenChangePin(oldPin: old, newPin: new))
        if let reject = lock.reject(store.liveHidden, s) {
            error = reject.message
            if reject.clearsPin { old = "" }
        } else {
            dismiss()
        }
    }
}

struct HiddenRemovePinSheet: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var lock = LockCountdown()
    @State private var pin = ""
    @State private var error: String?

    var body: some View {
        let s = store.strings
        HiddenSheetBody(title: s.hiddenRemovePin, onCancel: { dismiss() }) {
            Text(s.hiddenRemoveWarn)
                .font(FJTheme.font.body)
                .foregroundStyle(FJTheme.color.onSurface)
            HiddenPinField(label: s.hiddenEnterPin, text: $pin, disabled: lock.locked)
            if let message = lock.message(error) { HiddenErrorText(text: message) }
            FJDialogButton(title: s.hiddenRemovePin, kind: .danger, enabled: pin.count >= 4 && !lock.locked, fill: true) { submit() }
        }
    }

    private func submit() {
        store.send(DashboardUiEventOnHiddenRemoveLock(pin: pin))
        if let reject = lock.reject(store.liveHidden, store.strings) {
            error = reject.message
            if reject.clearsPin { pin = "" }
        } else {
            dismiss()
        }
    }
}

struct HiddenRestoreSheet: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    @State private var code = ""

    private var trimmed: String { code.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        let s = store.strings
        HiddenSheetBody(title: s.hiddenRecoveryRestoreTitle, onCancel: { dismiss() }) {
            Text(s.hiddenRecoveryRestoreHint)
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            SettingsField(label: s.hiddenRecoveryEnter, text: $code)
            FJDialogButton(title: s.hiddenRecoveryRestoreTitle, enabled: !trimmed.isEmpty, fill: true) {
                store.send(DashboardUiEventOnHiddenRestoreWithCode(code: trimmed))
                dismiss()
            }
        }
    }
}

struct HiddenRecoveryCodeItem: Identifiable {
    let id: String
}

struct HiddenRecoveryCodeSheet: View {
    @EnvironmentObject var store: FJStore
    let code: String

    var body: some View {
        let s = store.strings
        HiddenSheetBody(title: s.hiddenRecoveryTitle, onCancel: { store.send(DashboardUiEventOnHiddenRecoveryCodeShown.shared) }) {
            Text(s.hiddenRecoveryBody)
                .font(FJTheme.font.body)
                .foregroundStyle(FJTheme.color.onSurface)
            Text(code)
                .font(.system(size: 17, weight: .semibold, design: .monospaced))
                .foregroundStyle(FJTheme.color.onSurface)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .environment(\.layoutDirection, .leftToRight)
                .padding(12)
                .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant))
            FJDialogButton(title: s.hiddenRecoveryCopy, kind: .outlined, fill: true) {
                UIPasteboard.general.setItems(
                    [[UTType.plainText.identifier: code]],
                    options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)]
                )
            }
            FJDialogButton(title: s.done, fill: true) {
                store.send(DashboardUiEventOnHiddenRecoveryCodeShown.shared)
            }
        }
    }
}

final class FJDeviceAuth: NSObject, KotlinSuspendFunction1 {
    func invoke(p1: Any?, completionHandler: @escaping (Any?, Error?) -> Void) {
        let reason = (p1 as? String) ?? ""
        let context = LAContext()
        var failure: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &failure) else {
            completionHandler(nil, failure ?? NSError(domain: "FetchJet.DeviceAuth", code: -1))
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { ok, _ in
            completionHandler(KotlinBoolean(value: ok), nil)
        }
    }
}

struct HiddenCoverView: View {
    var body: some View {
        FJTheme.color.background
            .ignoresSafeArea()
            .overlay {
                Image(systemName: "eye.slash")
                    .font(.fj(44))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
    }
}

struct HiddenPrivacyCover: ViewModifier {
    @Environment(\.scenePhase) private var phase

    func body(content: Content) -> some View {
        content.overlay {
            if phase != .active { HiddenCoverView() }
        }
    }
}

extension View {
    func hiddenPrivacyCover() -> some View { modifier(HiddenPrivacyCover()) }
}

struct HiddenRootGuard: ViewModifier {
    @EnvironmentObject var store: FJStore
    @Environment(\.scenePhase) private var phase
    @State private var latched = false

    func body(content: Content) -> some View {
        content
            .sheet(item: Binding(
                get: { store.state.hidden.recoveryCode.map { HiddenRecoveryCodeItem(id: $0) } },
                set: { if $0 == nil { store.send(DashboardUiEventOnHiddenRecoveryCodeShown.shared) } }
            )) { item in
                HiddenRecoveryCodeSheet(code: item.id).environmentObject(store)
            }
            .overlay {
                if phase != .active && latched { HiddenCoverView() }
            }
            .onChange(of: phase) { _, new in
                if new == .active {
                    latched = false
                } else if store.state.hidden.unlocked {
                    latched = true
                    if new == .background { store.hiddenLock() }
                } else if store.state.hidden.pinResetAllowed {
                    store.send(DashboardUiEventOnHiddenResetCancel.shared)
                }
            }
    }
}
