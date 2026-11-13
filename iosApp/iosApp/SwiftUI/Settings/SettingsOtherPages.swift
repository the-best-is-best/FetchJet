import SwiftUI
import SharedLogic

struct SettingsMediaPage: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let s = store.strings
        let st = store.state
        let ready = st.isYtDlpInstalled && st.isFfmpegInstalled
        VStack(alignment: .leading, spacing: 24) {
            SettingsGroupBox(title: s.mediaToolsGroup) {
                SettingsRow(title: "yt-dlp", subtitle: s.ytDlpHint) {
                    st.isYtDlpInstalled
                        ? SettingsStatusLabel(text: s.settingsReady, color: FJTheme.color.statusCompleted)
                        : SettingsStatusLabel(text: s.settingsMissing, color: FJTheme.color.error)
                }
                SettingsDivider()
                SettingsRow(title: "FFmpeg", subtitle: s.ffmpegHint) {
                    st.isFfmpegInstalled
                        ? SettingsStatusLabel(text: s.settingsReady, color: FJTheme.color.statusCompleted)
                        : SettingsStatusLabel(text: s.ffmpegMissing, color: FJTheme.color.statusPaused)
                }
                if !st.jsRuntimeName.isEmpty {
                    SettingsDivider()
                    SettingsRow(title: s.jsRuntimeTitle(name: st.jsRuntimeName), subtitle: s.jsRuntimeHint) {
                        st.isJsRuntimeReady
                            ? SettingsStatusLabel(text: s.settingsReady, color: FJTheme.color.statusCompleted)
                            : SettingsStatusLabel(text: s.missingPress(button: ready ? s.mediaUpdateTools : s.mediaDownloadTheTools),
                                                  color: FJTheme.color.statusPaused)
                    }
                }
            }
            Button {
                store.send(DashboardUiEventOnDownloadYtDlpClicked.shared)
            } label: {
                HStack(spacing: 8) {
                    if st.isDownloadingYtDlp {
                        ProgressView().controlSize(.small)
                        Text(progressText(s, st, ready))
                    } else {
                        Text(ready ? s.mediaUpdateTools : s.mediaDownloadTheTools)
                    }
                }
            }
            .buttonStyle(SettingsButtonStyle(filled: !ready))
            .disabled(st.isDownloadingYtDlp)
            if st.isDownloadingYtDlp, st.toolDownloadName != nil {
                if let f = st.toolDownloadFraction?.floatValue {
                    ProgressView(value: Double(f)).tint(FJTheme.color.primary)
                } else {
                    ProgressView().progressViewStyle(.linear).tint(FJTheme.color.primary)
                }
            }
            if !st.ytDlpStatus.isEmpty {
                Text(store.host.localizeStep(text: st.ytDlpStatus))
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
        }
    }

    private func progressText(_ s: AppLocalization, _ st: DashboardUiState, _ ready: Bool) -> String {
        guard let name = st.toolDownloadName else { return ready ? s.mediaUpdatingTools : s.mediaDownloadingTools }
        guard let f = st.toolDownloadFraction?.floatValue else { return s.mediaDownloadingTool(name: name) }
        return s.mediaDownloadingToolPercent(name: name, percent: Int32(f * 100))
    }
}

struct SettingsTorrentPage: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let s = store.strings
        let st = store.state
        VStack(alignment: .leading, spacing: 24) {
            SettingsGroupBox {
                SettingsTrafficLimit(title: s.speedLimitUploadTorrents, bytes: st.globalUploadSpeedLimitBytes) {
                    store.send(DashboardUiEventOnGlobalUploadSpeedLimitChanged(limitBytesPerSec: $0))
                }
                .padding(16)
            }
            SettingsGroupBox(title: s.torrentSharingGroup) {
                SettingsSwitchRow(
                    title: s.downloadOnlyNoUpload,
                    subtitle: s.downloadOnlyNoUploadHint,
                    isOn: st.disableTorrentUpload
                ) { store.send(DashboardUiEventOnDisableTorrentUploadToggled(disable: $0)) }
            }
            SettingsStopRatioBox()
        }
    }
}

struct SettingsStopRatioBox: View {
    @EnvironmentObject var store: FJStore
    @State private var ratio: Float = 0

    private let choices: [Float] = [0, 0.5, 1, 1.5, 2, 3, 5]

    var body: some View {
        let s = store.strings
        SettingsGroupBox(title: s.torrentStopRatioTitle, footer: s.torrentStopRatioHint) {
            SettingsFlow(spacing: 6) {
                ForEach(choices, id: \.self) { value in
                    SettingsChip(label: label(value), selected: ratio == value) {
                        ratio = value
                        store.send(DashboardUiEventOnTorrentStopRatioChanged(ratio: value))
                    }
                }
            }
            .padding(16)
        }
        .onAppear {
            ratio = (BehaviorSettings.shared.torrentStopRatio.value as? KotlinFloat)?.floatValue ?? 0
        }
    }

    private func label(_ value: Float) -> String {
        value == 0 ? "0" : (value.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(value)) : String(value))
    }
}

struct SettingsTrafficLimit: View {
    @EnvironmentObject var store: FJStore
    let title: String
    let bytes: Int64
    let onChange: (Int64) -> Void
    @State private var custom = false
    @State private var customUnit = "MB/s"
    @State private var customText = ""
    @State private var loaded = false

    private var presets: [(String, Int64)] {
        [(store.strings.speedLimitUnlimited, 0), ("512 KB/s", 512 * 1024), ("1 MB/s", 1024 * 1024),
         ("5 MB/s", 5 * 1024 * 1024), ("10 MB/s", 10 * 1024 * 1024)]
    }

    var body: some View {
        let s = store.strings
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title).font(FJTheme.font.headline)
                Spacer()
                Text(bytes <= 0 ? s.speedLimitNoLimit : SettingsSpeed.shortLabel(bytes))
                    .font(FJTheme.font.caption.weight(.bold))
                    .foregroundStyle(bytes <= 0 ? FJTheme.color.onSurfaceVariant : FJTheme.color.primary)
            }
            SettingsFlow(spacing: 6) {
                ForEach(presets, id: \.1) { label, value in
                    SettingsChip(label: label, selected: !custom && bytes == value) {
                        custom = false
                        onChange(value)
                    }
                }
                SettingsChip(label: s.speedLimitCustom, selected: custom) {
                    custom = true
                    if customText.isEmpty {
                        customText = "2"
                        onChange(SettingsSpeed.toBytes(2, customUnit))
                    }
                }
            }
            if custom {
                SettingsFlow(spacing: 10) {
                    HStack(spacing: 6) {
                        Text("⚡")
                        TextField(s.speedLimitValue, text: Binding(get: { customText }, set: { v in
                            customText = v.filter { $0.isNumber || $0 == "." }
                            onChange(SettingsSpeed.toBytes(Double(customText) ?? 0, customUnit))
                        }))
                        .keyboardType(.decimalPad)
                    }
                    .padding(.horizontal, 10)
                    .frame(width: 130, height: 40)
                    .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant))
                    SettingsUnitPicker(selected: customUnit) { u in
                        customUnit = u
                        onChange(SettingsSpeed.toBytes(Double(customText) ?? 0, u))
                    }
                }
                .padding(12)
                .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 12))
            }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            custom = bytes > 0 && !presets.contains { $0.1 == bytes }
            customUnit = bytes > 0 && bytes < 1024 * 1024 ? "KB/s" : "MB/s"
            if bytes > 0 {
                customText = bytes >= 1024 * 1024 ? SettingsSpeed.oneDecimal(Double(bytes) / (1024 * 1024)) : "\(bytes / 1024)"
            }
        }
    }
}

struct SettingsBrowserPage: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var live: SettingsLive

    var body: some View {
        let s = store.strings
        VStack(alignment: .leading, spacing: 24) {
            VStack(alignment: .leading, spacing: 8) {
                Text(s.extInstallGroup.uppercased())
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                Text(s.extensionInstallFooter)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            .padding(.horizontal, 4)
            if !live.allowedOrigins.isEmpty || !live.blockedOrigins.isEmpty {
                SettingsGroupBox(title: s.extConnectionsGroup, footer: s.extConnectionsFooter) {
                    ForEach(Array(live.allowedOrigins.enumerated()), id: \.element) { i, origin in
                        if i > 0 { SettingsDivider() }
                        SettingsRow(title: origin, subtitle: s.extAllowedLabel) {
                            Button(s.extRevoke) { BridgePairing.shared.revokeAllowed(origin: origin); live.refresh() }
                                .buttonStyle(SettingsButtonStyle())
                        }
                    }
                    ForEach(Array(live.blockedOrigins.enumerated()), id: \.element) { i, origin in
                        if i > 0 || !live.allowedOrigins.isEmpty { SettingsDivider() }
                        SettingsRow(title: origin, subtitle: s.extBlockedLabel) {
                            Button(s.extUnblock) { BridgePairing.shared.unblock(origin: origin); live.refresh() }
                                .buttonStyle(SettingsButtonStyle())
                        }
                    }
                }
            }
        }
    }
}

struct SettingsDevicesPage: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var live: SettingsLive

    var body: some View {
        let s = store.strings
        VStack(alignment: .leading, spacing: 24) {
            SettingsGroupBox(title: s.sysDeviceSendTitle) {
                SettingsSwitchRow(title: s.visibleToDevices, subtitle: s.visibleToDevicesHint, isOn: live.visibleToDevices) {
                    BehaviorSettings.shared.setVisibleToDevices(on: $0)
                    live.refresh()
                }
            }
            deviceList(title: s.pairedDevicesTitle, empty: s.noPairedDevices, devices: store.state.pairedDevices,
                       action: s.unpairDevice, danger: true) {
                store.send(DashboardUiEventOnUnpairDeviceClicked(deviceId: $0))
            }
            deviceList(title: s.blockedDevicesTitle, empty: s.noBlockedDevices, devices: store.state.blockedDevices,
                       action: s.unblockDevice, danger: false) {
                store.send(DashboardUiEventOnUnblockDeviceClicked(deviceId: $0))
            }
        }
    }

    private func deviceList(title: String, empty: String, devices: [PairedDevice], action: String, danger: Bool,
                            onAction: @escaping (String) -> Void) -> some View {
        let sorted = devices.sorted { $0.name.lowercased() < $1.name.lowercased() }
        return SettingsGroupBox(title: title) {
            if sorted.isEmpty {
                Text(empty).font(FJTheme.font.caption).foregroundStyle(FJTheme.color.onSurfaceVariant).padding(16)
            }
            ForEach(Array(sorted.enumerated()), id: \.element.id) { i, device in
                if i > 0 { SettingsDivider() }
                let parts = device.name.components(separatedBy: " · ")
                let host = parts.first ?? device.name
                let tail = parts.count > 1 ? parts.dropFirst().joined(separator: " · ") : ""
                let plat = tail.trimmingCharacters(in: .whitespaces).isEmpty ? device.platform : tail
                SettingsRow(title: host, subtitle: !plat.isEmpty && plat.lowercased() != host.lowercased() ? plat : nil) {
                    Button(action) { onAction(device.id) }
                        .buttonStyle(SettingsButtonStyle(danger: danger))
                }
            }
        }
    }
}

struct SettingsUpdatesPage: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var live: SettingsLive
    @Environment(\.openURL) private var openURL

    var body: some View {
        let s = store.strings
        if let u = live.update, u.supported {
            SettingsGroupBox(title: s.sectionUpdates) {
                SettingsRow(title: "FetchJet \(u.currentVersion)",
                            subtitle: u.available.map { s.versionIsAvailable(version: $0.version) } ?? s.youHaveLatest) {
                    if u.available != nil {
                        Button(s.updateSeeWhatsNew) { UpdateManager.shared.showDialog(); live.refresh() }
                            .buttonStyle(SettingsButtonStyle(filled: true))
                    } else {
                        Button {
                            UpdateManager.shared.checkNow()
                            live.refresh()
                        } label: {
                            HStack(spacing: 6) {
                                if u.checking { ProgressView().controlSize(.mini) }
                                Text(u.checking ? s.updateChecking : s.updateCheckNow)
                            }
                        }
                        .buttonStyle(SettingsButtonStyle())
                        .disabled(u.checking)
                    }
                }
                if let msg = u.message {
                    Text(msg)
                        .font(FJTheme.font.caption)
                        .foregroundStyle(u.messageIsInstallError || u.messageIsError ? FJTheme.color.error : FJTheme.color.onSurfaceVariant)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 12)
                }
                SettingsDivider()
                SettingsSwitchRow(title: s.checkUpdatesAutomatically, subtitle: s.checkUpdatesAutomaticallyHint, isOn: u.autoCheck) {
                    UpdateManager.shared.setAutoCheck(enabled: $0)
                    live.refresh()
                }
                SettingsDivider()
                SettingsRow(title: s.installingUpdates, subtitle: s.installingUpdatesManual)
                SettingsDivider()
                SettingsRow(title: s.updateReleaseNotes, subtitle: s.updateReleaseNotesHint, onTap: {
                    if let url = URL(string: "https://github.com/the-best-is-best/FetchJet/releases") { openURL(url) }
                }) {
                    Image(systemName: "arrow.up.forward").font(.system(size: 13, weight: .bold)).foregroundStyle(FJTheme.color.primary)
                }
            }
        }
    }
}
