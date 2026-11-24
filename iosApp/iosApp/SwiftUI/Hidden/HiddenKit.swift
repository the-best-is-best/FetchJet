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

    func reject(_ h: HiddenUiState, invalid: String) -> HiddenPinReject? {
        if h.error == .wrongPin {
            return HiddenPinReject(message: HiddenTextsKt.hiddenWrongPinText(triesLeft: h.triesLeft), clearsPin: true)
        }
        if h.error == .locked {
            start(seconds: Int(h.lockedSeconds))
            return HiddenPinReject(message: nil, clearsPin: true)
        }
        if h.error == .invalidPin {
            return HiddenPinReject(message: invalid, clearsPin: false)
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
            Text(s.hiddenNoticeBody).font(FJTheme.font.body).foregroundStyle(FJTheme.color.onSurface)
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

struct PatternPad: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    var disabled = false
    let onDone: (String) -> Void
    @State private var path: [Int] = []
    @State private var finger: CGPoint?

    var body: some View {
        VStack(spacing: 10) {
            GeometryReader { geo in
                let side = min(geo.size.width, geo.size.height)
                let cell = side / 3
                let origin = CGPoint(x: (geo.size.width - side) / 2, y: (geo.size.height - side) / 2)
                let center: (Int) -> CGPoint = { i in
                    CGPoint(x: origin.x + cell * (CGFloat(i % 3) + 0.5), y: origin.y + cell * (CGFloat(i / 3) + 0.5))
                }
                ZStack {
                    Path { p in
                        guard let first = path.first else { return }
                        p.move(to: center(first))
                        for i in path.dropFirst() { p.addLine(to: center(i)) }
                        if let finger { p.addLine(to: finger) }
                    }
                    .stroke(FJTheme.color.primary.opacity(0.7), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                    ForEach(0..<9, id: \.self) { i in
                        let on = path.contains(i)
                        Circle()
                            .fill(on ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant.opacity(0.45))
                            .frame(width: on ? 22 : 16, height: on ? 22 : 16)
                            .overlay(Circle().stroke(FJTheme.color.primary.opacity(on ? 0.35 : 0), lineWidth: 8))
                            .frame(width: cell, height: cell)
                            .contentShape(Rectangle())
                            .position(center(i))
                            .accessibilityElement()
                            .accessibilityLabel("\(i + 1)")
                            .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                            .accessibilityAction { add(i) }
                    }
                }
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { drag in
                            finger = drag.location
                            for i in 0..<9 where !path.contains(i) {
                                let c = center(i)
                                if hypot(c.x - drag.location.x, c.y - drag.location.y) < cell * 0.35 { add(i) }
                            }
                        }
                        .onEnded { _ in finish() }
                )
            }
            .aspectRatio(1, contentMode: .fit)
            .frame(maxWidth: 280)
            if voiceOver {
                HStack(spacing: 8) {
                    FJDialogButton(title: store.strings.cancel, kind: .outlined, enabled: !path.isEmpty) { path = [] }
                    FJDialogButton(title: store.strings.ok, enabled: !path.isEmpty) { finish() }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .environment(\.layoutDirection, .leftToRight)
        .disabled(disabled)
        .opacity(disabled ? 0.5 : 1)
    }

    private func add(_ i: Int) {
        guard !disabled, !path.contains(i) else { return }
        if let last = path.last {
            let r = last / 3 + i / 3
            let c = last % 3 + i % 3
            if r % 2 == 0 && c % 2 == 0 {
                let middle = (r / 2) * 3 + c / 2
                if !path.contains(middle) { path.append(middle) }
            }
        }
        path.append(i)
    }

    private func finish() {
        let digits = path.map { String($0 + 1) }.joined()
        path = []
        finger = nil
        if !digits.isEmpty { onDone(digits) }
    }
}

struct HiddenSecretField: View {
    let type: HiddenLockType
    let label: String
    @Binding var text: String
    var disabled = false
    var onDrawn: () -> Void = {}

    var body: some View {
        if type == .pattern {
            if text.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text(label)
                        .font(FJTheme.font.caption)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    PatternPad(disabled: disabled) { digits in
                        text = digits
                        onDrawn()
                    }
                }
            } else {
                HStack(spacing: 8) {
                    Text(label)
                        .font(FJTheme.font.body)
                        .foregroundStyle(FJTheme.color.onSurface)
                    Spacer(minLength: 0)
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(FJTheme.color.primary)
                        .accessibilityHidden(true)
                }
                .accessibilityElement(children: .combine)
            }
        } else {
            HiddenPinField(label: label, text: $text, disabled: disabled)
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
        .environment(\.layoutDirection, store.isRtl ? .rightToLeft : .leftToRight)
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
    @State private var chosen: HiddenLockType?

    private var type: HiddenLockType { chosen ?? (resetsPin ? store.state.hidden.lockType ?? .pin : .pin) }
    private var texts: HiddenLockTexts { HiddenTextsKt.hiddenLockTexts(type: type) }
    private var valid: Bool { HiddenStore.shared.isValidSecret(type: type, secret: pin) }

    var body: some View {
        let s = store.strings
        let t = texts
        let title = resetsPin ? t.resetTitle : t.setTitle
        HiddenSheetBody(title: title, onCancel: { dismiss() }) {
            Text(t.setHint)
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            HiddenSecretField(type: type, label: t.enter, text: $pin, onDrawn: checkFirst)
            if type == .pin || !pin.isEmpty {
                HiddenSecretField(type: type, label: t.confirm, text: $again, onDrawn: submit)
            }
            if let error { HiddenErrorText(text: error) }
            if type == .pin {
                FJDialogButton(title: title, enabled: valid && !again.isEmpty, fill: true) { submit() }
            }
            FJDialogButton(title: t.switchTo, kind: .text, fill: true) {
                chosen = type == .pin ? .pattern : .pin
                pin = ""
                again = ""
                error = nil
            }
            if !resetsPin {
                FJDialogButton(title: s.hiddenRecoveryRestoreTitle, kind: .text, fill: true) { showRestore = true }
            }
        }
        .sheet(isPresented: $showRestore) { HiddenRestoreSheet().environmentObject(store) }
    }

    private func checkFirst() {
        error = nil
        if !valid {
            error = texts.invalid
            pin = ""
        }
    }

    private func submit() {
        let t = texts
        if pin != again {
            error = t.mismatch
            clearPattern()
            return
        }
        if resetsPin {
            store.send(DashboardUiEventOnHiddenResetPin(pin: pin, lockType: type))
            let h = store.liveHidden
            if h.error == .invalidPin {
                error = t.invalid
                clearPattern()
                return
            }
            if h.unlocked { onSet() }
            dismiss()
            return
        }
        store.send(DashboardUiEventOnHiddenSetPin(pin: pin, lockType: type))
        let h = store.liveHidden
        if h.lockType == nil || h.error == .invalidPin {
            error = t.invalid
            clearPattern()
            return
        }
        onSet()
        dismiss()
    }

    private func clearPattern() {
        if type == .pattern {
            pin = ""
            again = ""
        }
    }
}

struct HiddenUnlockSheet: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    let onUnlocked: () -> Void
    @StateObject private var lock = LockCountdown()
    @State private var pin = ""
    @State private var error: String?

    private var type: HiddenLockType { store.state.hidden.lockType ?? .pin }

    var body: some View {
        let s = store.strings
        let h = store.state.hidden
        let t = HiddenTextsKt.hiddenLockTexts(type: type)
        Group {
            if h.pinResetAllowed {
                HiddenSetPinSheet(resetsPin: true, onSet: onUnlocked)
            } else {
                HiddenSheetBody(title: s.hiddenTitle, onCancel: { dismiss() }) {
                    HiddenSecretField(type: type, label: t.enter, text: $pin, disabled: lock.locked, onDrawn: submit)
                    if let message = lock.message(error) { HiddenErrorText(text: message) }
                    if type == .pin {
                        FJDialogButton(title: s.ok, enabled: pin.count >= 4 && !lock.locked, fill: true) { submit() }
                    }
                    if h.canResetPin {
                        FJDialogButton(title: t.forgot, kind: .text, fill: true) {
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
        let t = HiddenTextsKt.hiddenLockTexts(type: type)
        if !HiddenStore.shared.isValidSecret(type: type, secret: pin) {
            pin = ""
            error = t.invalid
            return
        }
        store.send(DashboardUiEventOnHiddenUnlock(pin: pin))
        let h = store.liveHidden
        if h.unlocked {
            onUnlocked()
            dismiss()
            return
        }
        pin = ""
        error = lock.reject(h, invalid: t.invalid)?.message
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
    @State private var chosen: HiddenLockType?

    private var oldType: HiddenLockType { store.state.hidden.lockType ?? .pin }
    private var newType: HiddenLockType { chosen ?? oldType }

    var body: some View {
        let oldTexts = HiddenTextsKt.hiddenLockTexts(type: oldType)
        let newTexts = HiddenTextsKt.hiddenLockTexts(type: newType)
        HiddenSheetBody(title: oldTexts.change, onCancel: { dismiss() }) {
            HiddenSecretField(type: oldType, label: oldTexts.oldLabel, text: $old, disabled: lock.locked, onDrawn: checkOld)
            if oldType == .pin || !old.isEmpty {
                HiddenSecretField(type: newType, label: newTexts.newLabel, text: $new, disabled: lock.locked, onDrawn: checkNew)
                if newType == .pin || !new.isEmpty {
                    HiddenSecretField(type: newType, label: newTexts.confirm, text: $again, disabled: lock.locked, onDrawn: submit)
                }
            }
            if let message = lock.message(error) { HiddenErrorText(text: message) }
            FJDialogButton(title: oldTexts.change, enabled: old.count >= 4 && new.count >= 4 && !again.isEmpty && !lock.locked, fill: true) { submit() }
            FJDialogButton(title: newTexts.switchTo, kind: .text, fill: true) {
                chosen = newType == .pin ? .pattern : .pin
                new = ""
                again = ""
                error = nil
            }
        }
    }

    private func checkOld() {
        error = nil
        if !HiddenStore.shared.isValidSecret(type: oldType, secret: old) {
            error = HiddenTextsKt.hiddenLockTexts(type: oldType).invalid
            old = ""
        }
    }

    private func checkNew() {
        error = nil
        if !HiddenStore.shared.isValidSecret(type: newType, secret: new) {
            error = HiddenTextsKt.hiddenLockTexts(type: newType).invalid
            new = ""
        }
    }

    private func submit() {
        let t = HiddenTextsKt.hiddenLockTexts(type: newType)
        if new != again {
            error = t.mismatch
            if newType == .pattern {
                new = ""
                again = ""
            }
            return
        }
        store.send(DashboardUiEventOnHiddenChangePin(oldPin: old, newPin: new, newLockType: newType))
        if let reject = lock.reject(store.liveHidden, invalid: t.invalid) {
            error = reject.message
            if reject.clearsPin || oldType == .pattern { old = "" }
            if newType == .pattern {
                new = ""
                again = ""
            }
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

    private var type: HiddenLockType { store.state.hidden.lockType ?? .pin }

    var body: some View {
        let t = HiddenTextsKt.hiddenLockTexts(type: type)
        HiddenSheetBody(title: t.remove, onCancel: { dismiss() }) {
            Text(t.removeWarn)
                .font(FJTheme.font.body)
                .foregroundStyle(FJTheme.color.onSurface)
            HiddenSecretField(type: type, label: t.enter, text: $pin, disabled: lock.locked, onDrawn: submit)
            if let message = lock.message(error) { HiddenErrorText(text: message) }
            if type == .pin {
                FJDialogButton(title: t.remove, kind: .danger, enabled: pin.count >= 4 && !lock.locked, fill: true) { submit() }
            }
        }
    }

    private func submit() {
        let t = HiddenTextsKt.hiddenLockTexts(type: type)
        if !HiddenStore.shared.isValidSecret(type: type, secret: pin) {
            pin = ""
            error = t.invalid
            return
        }
        store.send(DashboardUiEventOnHiddenRemoveLock(pin: pin))
        if let reject = lock.reject(store.liveHidden, invalid: t.invalid) {
            error = reject.message
            if reject.clearsPin || type == .pattern { pin = "" }
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

struct HiddenCoverScreen: View {
    @EnvironmentObject var store: FJStore

    private var canAuth: Bool { DeviceAuth.shared.available() }

    var body: some View {
        let s = store.strings
        ZStack {
            Color.black.ignoresSafeArea()
            if store.state.hidden.covered && canAuth {
                VStack(spacing: 12) {
                    FJDialogButton(title: s.hiddenCoverUnlock, fill: true) { store.send(DashboardUiEventOnHiddenReauth.shared) }
                    FJDialogButton(title: s.hiddenCoverExit, kind: .text, fill: true) { store.send(DashboardUiEventOnHiddenLock.shared) }
                }
                .padding(32)
            }
        }
        .sheet(isPresented: Binding(
            get: { store.state.hidden.covered && !canAuth },
            set: { if !$0 && store.state.hidden.covered { store.send(DashboardUiEventOnHiddenLock.shared) } }
        )) {
            HiddenUnlockSheet(onUnlocked: {}).environmentObject(store)
        }
    }
}

struct HiddenRootGuard: ViewModifier {
    @EnvironmentObject var store: FJStore
    @Environment(\.scenePhase) private var phase
    @State private var latched = false
    @State private var wentBackground = false

    func body(content: Content) -> some View {
        content
            .sheet(item: Binding(
                get: { store.state.hidden.recoveryCode.map { HiddenRecoveryCodeItem(id: $0) } },
                set: { if $0 == nil { store.send(DashboardUiEventOnHiddenRecoveryCodeShown.shared) } }
            )) { item in
                HiddenRecoveryCodeSheet(code: item.id).environmentObject(store)
            }
            .overlay {
                if (phase != .active && latched) || store.state.hidden.covered { HiddenCoverScreen() }
            }
            .onChange(of: phase) { _, new in
                if new == .active {
                    latched = false
                    if wentBackground && store.state.hidden.covered { store.send(DashboardUiEventOnHiddenReauth.shared) }
                    wentBackground = false
                } else if store.state.hidden.unlocked {
                    latched = true
                    if new == .background {
                        wentBackground = true
                        store.hiddenCover()
                    }
                } else if store.state.hidden.pinResetAllowed {
                    store.send(DashboardUiEventOnHiddenResetCancel.shared)
                }
            }
    }
}
