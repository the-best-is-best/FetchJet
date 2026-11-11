import SwiftUI
import SharedLogic

enum SettingsSection: String, CaseIterable, Hashable {
    case general, downloads, browser, devices, network, media, torrent, updates

    var icon: String {
        switch self {
        case .general: return "circle.lefthalf.filled"
        case .downloads: return "arrow.down"
        case .browser: return "safari"
        case .devices: return "arrow.left.arrow.right"
        case .network: return "network"
        case .media: return "play.rectangle"
        case .torrent: return "arrow.up.arrow.down"
        case .updates: return "arrow.clockwise"
        }
    }

    func title(_ s: AppLocalization) -> String {
        switch self {
        case .general: return s.sectionGeneral
        case .downloads: return s.sectionDownloads
        case .browser: return s.sectionBrowser
        case .devices: return s.sectionDevices
        case .network: return s.sectionNetwork
        case .media: return s.sectionMedia
        case .torrent: return s.sectionTorrent
        case .updates: return s.sectionUpdates
        }
    }

    func blurb(_ s: AppLocalization) -> String {
        switch self {
        case .general: return s.sectionGeneralBlurb
        case .downloads: return s.sectionDownloadsBlurb
        case .browser: return s.sectionBrowserBlurb
        case .devices: return s.sectionDevicesBlurb
        case .network: return s.sectionNetworkBlurb
        case .media: return s.sectionMediaBlurb
        case .torrent: return s.sectionTorrentBlurb
        case .updates: return s.sectionUpdatesBlurb
        }
    }
}

struct SettingsScreen: View {
    @EnvironmentObject var store: FJStore
    @StateObject private var live = SettingsLive()
    @State private var open: SettingsSection?

    var body: some View {
        let s = store.strings
        ZStack {
            if let open, sections.contains(open) {
                SettingsPageScreen(section: open, live: live)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button {
                                withAnimation(.easeInOut(duration: 0.22)) { self.open = nil }
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "chevron.backward").font(.system(size: 15, weight: .semibold))
                                    Text(s.settingsAllSettings)
                                }
                            }
                        }
                    }
                    .simultaneousGesture(DragGesture(minimumDistance: 30).onEnded { v in
                        let dx = store.isRtl ? -v.translation.width : v.translation.width
                        if dx > 80 && abs(v.translation.height) < 60 {
                            withAnimation(.easeInOut(duration: 0.22)) { self.open = nil }
                        }
                    })
                    .transition(.move(edge: .trailing))
            } else {
                list(s).transition(.move(edge: .leading))
            }
        }
    }

    private func list(_ s: AppLocalization) -> some View {
        ScrollView {
            VStack(spacing: 6) {
                ForEach(sections, id: \.self) { section in
                    Button {
                        withAnimation(.easeInOut(duration: 0.22)) { open = section }
                    } label: {
                        navItem(section, s)
                    }
                    .buttonStyle(.plain)
                }
                Text(s.changesSavedAutomatically)
                    .font(FJTheme.font.small)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .padding(.bottom, 24)
        }
        .background(FJTheme.color.background.ignoresSafeArea())
        .navigationTitle(s.homeSettings)
        .navigationBarTitleDisplayMode(.large)
    }

    private var sections: [SettingsSection] {
        SettingsSection.allCases.filter {
            switch $0 {
            case .media: return store.host.mediaEnabled
            case .torrent: return store.host.torrentEnabled
            case .updates: return live.update?.supported ?? true
            default: return true
            }
        }
    }

    private func navItem(_ section: SettingsSection, _ s: AppLocalization) -> some View {
        let (hint, hintColor) = summary(section, s)
        return HStack(spacing: 12) {
            Image(systemName: section.icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .frame(width: 34, height: 34)
                .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 9))
            VStack(alignment: .leading, spacing: 2) {
                Text(section.title(s))
                    .font(FJTheme.font.body.weight(.semibold))
                    .foregroundStyle(FJTheme.color.onSurface)
                Text(hint)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(hintColor ?? FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.forward")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(minHeight: 56)
        .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
    }

    private func summary(_ section: SettingsSection, _ s: AppLocalization) -> (String, Color?) {
        let st = store.state
        switch section {
        case .general:
            return (settingsThemeLabel(st.themeMode, s) + " · " + settingsUnitKey(st.speedDisplayUnit), nil)
        case .downloads:
            let q = live.maxAtOnce <= 0 ? s.summaryNoQueueLimit : s.summaryAtATime(n: live.maxAtOnce)
            return (settingsFolderName(st.destinationPath) + " · " + q, nil)
        case .network:
            return (settingsNetworkSummary(live.proxy, live.logins.count, s), live.proxy.isOn ? FJTheme.color.statusCompleted : nil)
        case .media:
            return st.isYtDlpInstalled && st.isFfmpegInstalled
                ? (s.settingsReady, FJTheme.color.statusCompleted)
                : (s.summarySetupNeeded, FJTheme.color.statusPaused)
        case .torrent:
            return (st.disableTorrentUpload ? s.summaryUploadingOff : s.summaryUploadingOn, nil)
        case .browser:
            return ("Safari", nil)
        case .devices:
            let base = live.visibleToDevices ? s.summaryVisible : s.summaryHidden
            let n = st.pairedDevices.count
            return (base + (n > 0 ? " · \(n)" : ""), live.visibleToDevices ? FJTheme.color.statusCompleted : nil)
        case .updates:
            if let a = live.update?.available {
                return (s.summaryVersionAvailable(version: a.version), FJTheme.color.statusCompleted)
            }
            return (s.summaryVersion(version: live.update?.currentVersion ?? ""), nil)
        }
    }
}

struct SettingsPageScreen: View {
    @EnvironmentObject var store: FJStore
    let section: SettingsSection
    @ObservedObject var live: SettingsLive

    var body: some View {
        let s = store.strings
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text(section.blurb(s))
                    .font(FJTheme.font.callout)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                page
            }
            .frame(maxWidth: 720)
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 32)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(FJTheme.color.background.ignoresSafeArea())
        .navigationTitle(section.title(s))
        .navigationBarTitleDisplayMode(.large)
    }

    @ViewBuilder private var page: some View {
        switch section {
        case .general: SettingsGeneralPage()
        case .downloads: SettingsDownloadsPage(live: live)
        case .browser: SettingsBrowserPage(live: live)
        case .devices: SettingsDevicesPage(live: live)
        case .network: SettingsNetworkPage(live: live)
        case .media: SettingsMediaPage()
        case .torrent: SettingsTorrentPage()
        case .updates: SettingsUpdatesPage(live: live)
        }
    }
}

func settingsThemeLabel(_ mode: String, _ s: AppLocalization) -> String {
    switch mode {
    case "light": return s.settingsThemeLight
    case "dark": return s.settingsThemeDark
    default: return s.settingsThemeSystem
    }
}

func settingsUnitKey(_ unit: String) -> String {
    switch unit {
    case "BYTES_PER_SEC", "BOTH": return "MB/s"
    case "BITS_PER_SEC": return "Mbps"
    default: return unit
    }
}

func settingsFolderName(_ path: String) -> String {
    let trimmed = path.trimmingCharacters(in: CharacterSet(charactersIn: "/\\"))
    let last = trimmed.split(whereSeparator: { $0 == "/" || $0 == "\\" }).last.map(String.init) ?? ""
    return last.isEmpty ? path : last
}

func settingsProxyKindLabel(_ kind: ProxyKind, _ s: AppLocalization) -> String {
    if kind == .http { return "HTTP" }
    if kind == .socks5 { return "SOCKS5" }
    return s.netProxyOff
}

func settingsNetworkSummary(_ p: ProxySettings, _ n: Int, _ s: AppLocalization) -> String {
    let proxy = p.isOn ? "\(settingsProxyKindLabel(p.kind, s)) \(p.host):\(p.port)" : s.netProxyOff
    return n == 0 ? proxy : proxy + " · " + s.netLoginsCount(n: Int32(n))
}
