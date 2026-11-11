import SwiftUI
import SharedLogic

@MainActor
final class SettingsLive: ObservableObject {
    @Published private(set) var maxAtOnce: Int32 = 0
    @Published private(set) var visibleToDevices = false
    @Published private(set) var proxy: ProxySettings = ProxySettings(kind: .none, host: "", port: 0, user: "", password: "")
    @Published private(set) var logins: [SiteLogin] = []
    @Published private(set) var allowedOrigins: [String] = []
    @Published private(set) var blockedOrigins: [String] = []
    @Published private(set) var update: UpdateState?
    @Published private(set) var scheduleOn = false
    @Published private(set) var scheduleWindows: [SpeedWindow] = []
    @Published private(set) var scheduleActive = false
    private var timer: Timer?

    init() {
        refresh()
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    deinit { timer?.invalidate() }

    func refresh() {
        let q = QueueManagerRepository.companion.getInstance()
        let max = (q.maxConcurrentDownloads.value as? KotlinInt)?.int32Value ?? 0
        if max != maxAtOnce { maxAtOnce = max }
        let vis = (BehaviorSettings.shared.visibleToDevices.value as? KotlinBoolean)?.boolValue ?? false
        if vis != visibleToDevices { visibleToDevices = vis }
        if let p = NetworkSettings.shared.proxy.value as? ProxySettings, !p.isEqual(proxy) { proxy = p }
        let l = (NetworkSettings.shared.logins.value as? [SiteLogin]) ?? []
        if l.map({ "\($0.site)|\($0.user)" }) != logins.map({ "\($0.site)|\($0.user)" }) { logins = l }
        let a = Self.origins(BridgePairing.shared.legacyAllowed.value)
        if a != allowedOrigins { allowedOrigins = a }
        let b = Self.origins(BridgePairing.shared.blocked.value)
        if b != blockedOrigins { blockedOrigins = b }
        if let u = UpdateManager.shared.state.value as? UpdateState, !(update?.isEqual(u) ?? false) { update = u }
        let on = (SpeedSchedule.shared.enabled.value as? KotlinBoolean)?.boolValue ?? false
        if on != scheduleOn { scheduleOn = on }
        let w = (SpeedSchedule.shared.windows.value as? [SpeedWindow]) ?? []
        if !w.elementsEqual(scheduleWindows, by: { $0.isEqual($1) }) { scheduleWindows = w }
        let active = SpeedSchedule.shared.activeCap.value != nil
        if active != scheduleActive { scheduleActive = active }
    }

    private static func origins(_ value: Any?) -> [String] {
        if let s = value as? Set<String> { return s.sorted() }
        if let s = value as? NSSet { return s.compactMap { $0 as? String }.sorted() }
        if let s = value as? [String] { return s.sorted() }
        return []
    }
}

struct SettingsGroupBox<Content: View>: View {
    var title: String? = nil
    var footer: String? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let title, !title.isEmpty {
                Text(title.uppercased())
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .padding(.horizontal, 4)
            }
            VStack(alignment: .leading, spacing: 0) { content }
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: FJTheme.corner))
                .overlay(RoundedRectangle(cornerRadius: FJTheme.corner).stroke(FJTheme.color.outlineVariant.opacity(0.6)))
            if let footer, !footer.isEmpty {
                Text(footer)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .padding(.horizontal, 4)
            }
        }
    }
}

struct SettingsDivider: View {
    var body: some View {
        Rectangle().fill(FJTheme.color.outlineVariant.opacity(0.6)).frame(height: 0.5).padding(.leading, 16)
    }
}

struct SettingsRow<Trailing: View>: View {
    let title: String
    var subtitle: String? = nil
    var onTap: (() -> Void)? = nil
    @ViewBuilder var trailing: Trailing

    var body: some View {
        let row = HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(FJTheme.font.body.weight(.medium)).foregroundStyle(FJTheme.color.onSurface)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle).font(FJTheme.font.caption).foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        if let onTap {
            Button(action: onTap) { row }.buttonStyle(.plain)
        } else {
            row
        }
    }
}

extension SettingsRow where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, onTap: (() -> Void)? = nil) {
        self.init(title: title, subtitle: subtitle, onTap: onTap) { EmptyView() }
    }
}

struct SettingsSwitchRow: View {
    let title: String
    var subtitle: String? = nil
    let isOn: Bool
    var enabled = true
    let onChange: (Bool) -> Void

    var body: some View {
        SettingsRow(title: title, subtitle: subtitle) {
            Toggle("", isOn: Binding(get: { isOn }, set: { onChange($0) }))
                .labelsHidden()
                .tint(FJTheme.color.primary)
        }
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
    }
}

struct SettingsSegments<T: Hashable>: View {
    let options: [T]
    let selected: T
    let label: (T) -> String
    let onSelect: (T) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 3) {
                ForEach(options, id: \.self) { o in
                    let on = o == selected
                    Button { onSelect(o) } label: {
                        Text(label(o))
                            .font(.system(size: 13, weight: on ? .bold : .medium))
                            .foregroundStyle(on ? FJTheme.color.onPrimaryContainer : FJTheme.color.onSurfaceVariant)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(on ? FJTheme.color.primaryContainer : .clear, in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
        }
    }
}

struct SettingsStatusLabel: View {
    let text: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(text).font(FJTheme.font.caption.weight(.semibold)).foregroundStyle(color)
        }
    }
}

struct SettingsButtonStyle: ButtonStyle {
    var filled = false
    var danger = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        let tint = danger ? FJTheme.color.error : FJTheme.color.primary
        configuration.label
            .font(FJTheme.font.callout.weight(.semibold))
            .foregroundStyle(filled ? FJTheme.color.onPrimary : tint)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(filled ? tint : .clear, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(filled ? .clear : FJTheme.color.outline.opacity(0.7)))
            .opacity(isEnabled ? (configuration.isPressed ? 0.7 : 1) : 0.45)
    }
}

struct SettingsField: View {
    let label: String?
    var placeholder: String = ""
    @Binding var text: String
    var secure = false
    var keyboard: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let label, !label.isEmpty {
                Text(label).font(FJTheme.font.small.weight(.semibold)).foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            Group {
                if secure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .font(FJTheme.font.body)
            .keyboardType(keyboard)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .padding(.horizontal, 12)
            .frame(height: 42)
            .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant))
        }
    }
}

enum SettingsSpeed {
    static let units = ["KB/s", "MB/s", "Kbps", "Mbps"]

    static func toBytes(_ value: Double, _ unit: String) -> Int64 {
        switch unit {
        case "KB/s": return Int64(value * 1024)
        case "Kbps": return Int64(value * 1000 / 8)
        case "Mbps": return Int64(value * 1_000_000 / 8)
        default: return Int64(value * 1024 * 1024)
        }
    }

    static func valueText(_ bytes: Int64, _ unit: String) -> String {
        if bytes <= 0 { return "" }
        let b = Double(bytes)
        let v: Double
        switch unit {
        case "KB/s": v = b / 1024
        case "Kbps": v = b * 8 / 1000
        case "Mbps": v = b * 8 / 1_000_000
        default: v = b / (1024 * 1024)
        }
        return oneDecimal(v)
    }

    static func shortLabel(_ bytes: Int64) -> String {
        if bytes >= 1024 * 1024 { return oneDecimal(Double(bytes) / (1024 * 1024)) + " MB/s" }
        return "\(bytes / 1024) KB/s"
    }

    static func oneDecimal(_ v: Double) -> String {
        if v == v.rounded(.towardZero) { return String(Int64(v)) }
        return String(Double(Int64(v * 10)) / 10)
    }
}

struct SettingsUnitPicker: View {
    let selected: String
    let onSelect: (String) -> Void

    var body: some View {
        HStack(spacing: 3) {
            ForEach(SettingsSpeed.units, id: \.self) { u in
                let on = u == selected
                Button { onSelect(u) } label: {
                    Text(u)
                        .font(.system(size: 12, weight: on ? .bold : .medium))
                        .foregroundStyle(on ? FJTheme.color.onPrimaryContainer : FJTheme.color.onSurfaceVariant)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 8)
                        .background(on ? FJTheme.color.primaryContainer : .clear, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
        .environment(\.layoutDirection, .leftToRight)
    }
}

struct SettingsSpeedPresetRow: View {
    let name: String
    let bytes: Int64
    let unit: String
    var placeholder: String = ""
    let onChanged: (Int64, String) -> Void
    @State private var currentUnit = "MB/s"
    @State private var text = ""
    @State private var loaded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                if !name.isEmpty { Text(name).font(FJTheme.font.headline) }
                Spacer()
                Text(SettingsSpeed.shortLabel(bytes))
                    .font(FJTheme.font.caption.weight(.bold))
                    .foregroundStyle(FJTheme.color.primary)
            }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { field; SettingsUnitPicker(selected: currentUnit, onSelect: pick) }
                VStack(alignment: .leading, spacing: 8) { field; SettingsUnitPicker(selected: currentUnit, onSelect: pick) }
            }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            currentUnit = SettingsSpeed.units.contains(unit) ? unit : "MB/s"
            text = SettingsSpeed.valueText(bytes, currentUnit)
        }
    }

    private var field: some View {
        HStack(spacing: 6) {
            Text("⚡").font(.system(size: 14))
            TextField(placeholder, text: Binding(get: { text }, set: { v in
                let clean = v.filter { $0.isNumber || $0 == "." }
                text = clean
                onChanged(SettingsSpeed.toBytes(Double(clean) ?? 0, currentUnit), currentUnit)
            }))
            .keyboardType(.decimalPad)
        }
        .padding(.horizontal, 10)
        .frame(width: 130, height: 40)
        .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant))
    }

    private func pick(_ u: String) {
        currentUnit = u
        onChanged(SettingsSpeed.toBytes(Double(text) ?? 0, u), u)
    }
}

struct SettingsChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if selected { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)) }
                Text(label).font(.system(size: 13, weight: .medium))
            }
            .foregroundStyle(selected ? FJTheme.color.onSecondaryContainer : FJTheme.color.onSurfaceVariant)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(selected ? FJTheme.color.secondaryContainer : .clear, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(selected ? .clear : FJTheme.color.outline.opacity(0.6)))
        }
        .buttonStyle(.plain)
    }
}

struct SettingsFlow: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxX: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > width { x = 0; y += rowH + spacing; rowH = 0 }
            x += s.width + spacing
            maxX = max(maxX, x - spacing)
            rowH = max(rowH, s.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > bounds.width { x = 0; y += rowH + spacing; rowH = 0 }
            v.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY + y), proposal: ProposedViewSize(s))
            x += s.width + spacing
            rowH = max(rowH, s.height)
        }
    }
}
