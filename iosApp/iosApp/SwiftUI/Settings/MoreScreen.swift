import SwiftUI
import UniformTypeIdentifiers
import SharedLogic

struct FJBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    var text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

struct MoreScreen: View {
    @EnvironmentObject var store: FJStore
    @StateObject private var live = SettingsLive()
    @State private var inSettings = false
    @State private var inHidden = false
    @State private var pinSheet: HiddenSheetKind?
    @State private var restoring = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        ZStack {
            if inSettings {
                SettingsScreen(onBack: { withAnimation(.easeInOut(duration: 0.22)) { inSettings = false } })
                    .transition(.move(edge: .trailing))
            } else if inHidden && store.state.hidden.unlocked {
                HiddenScreen(onBack: { withAnimation(.easeInOut(duration: 0.22)) { inHidden = false } })
                    .transition(.move(edge: .trailing))
            } else {
                page.transition(.move(edge: .leading))
            }
        }
        .sheet(item: $pinSheet) { kind in
            switch kind {
            case .setPin:
                HiddenSetPinSheet(onSet: { withAnimation(.easeInOut(duration: 0.22)) { inHidden = true } }).environmentObject(store)
            case .unlock:
                HiddenUnlockSheet(onUnlocked: { withAnimation(.easeInOut(duration: 0.22)) { inHidden = true } }).environmentObject(store)
            default:
                EmptyView()
            }
        }
        .onChange(of: store.state.hidden.unlocked) { _, unlocked in
            if !unlocked { inHidden = false }
        }
        .fileExporter(
            isPresented: Binding(
                get: { store.state.backup.text != nil },
                set: { if !$0 { store.send(DashboardUiEventOnBackupWritten.shared) } }
            ),
            document: FJBackupDocument(text: store.state.backup.text ?? ""),
            contentType: .data,
            defaultFilename: store.state.backup.fileName
        ) { result in
            if case .failure = result { store.showToast(store.strings.backupFailed) }
        }
        .fileImporter(isPresented: $restoring, allowedContentTypes: [.data, .json]) { result in
            guard case .success(let url) = result else { return }
            let secured = url.startAccessingSecurityScopedResource()
            defer { if secured { url.stopAccessingSecurityScopedResource() } }
            let text = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
            store.send(DashboardUiEventOnBackupRestoreText(text: text))
        }
    }

    private func openHidden() {
        let h = store.state.hidden
        if h.unlocked {
            withAnimation(.easeInOut(duration: 0.22)) { inHidden = true }
        } else if h.lockType == nil {
            pinSheet = .setPin
        } else {
            pinSheet = .unlock
        }
    }

    private var page: some View {
        let s = store.strings
        return ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header(s)
                SettingsGroupBox {
                    SettingsRow(title: s.homeSettings, subtitle: s.moreSettingsHint, onTap: {
                        withAnimation(.easeInOut(duration: 0.22)) { inSettings = true }
                    }) {
                        Image(systemName: "chevron.forward").font(.fj(13, .semibold)).foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                    SettingsDivider()
                    SettingsRow(title: s.browserTitle, subtitle: s.browserHint, onTap: {
                        FJPageBrowser.shared.page = URL(string: "https://www.google.com")
                    }) {
                        Image(systemName: "chevron.forward").font(.fj(13, .semibold)).foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                    SettingsDivider()
                    SettingsRow(title: s.hiddenTitle, onTap: openHidden) {
                        Image(systemName: "chevron.forward").font(.fj(13, .semibold)).foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                }
                SettingsGroupBox {
                    SettingsRow(title: s.backupExport, subtitle: s.backupExportHint, onTap: {
                        store.send(DashboardUiEventOnBackupExportRequested.shared)
                    }) {
                        Image(systemName: "square.and.arrow.up").font(.fj(14, .semibold)).foregroundStyle(FJTheme.color.primary)
                    }
                    SettingsDivider()
                    SettingsRow(title: s.backupRestore, subtitle: s.backupRestoreHint, onTap: { restoring = true }) {
                        Image(systemName: "square.and.arrow.down").font(.fj(14, .semibold)).foregroundStyle(FJTheme.color.primary)
                    }
                }
                everywhere(s)
                community(s)
            }
            .frame(maxWidth: 720)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .padding(.bottom, 24)
            .frame(maxWidth: .infinity)
        }
        .background(FJTheme.color.background.ignoresSafeArea())
        .navigationTitle(s.moreTitle)
        .navigationBarTitleDisplayMode(.large)
    }

    private func header(_ s: AppLocalization) -> some View {
        let u = live.update
        return SettingsGroupBox {
            SettingsRow(title: "FetchJet", subtitle: s.summaryVersion(version: u?.currentVersion ?? ""))
            if let u, u.supported {
                SettingsDivider()
                SettingsRow(title: u.available.map { s.versionIsAvailable(version: $0.version) } ?? s.youHaveLatest) {
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
            }
        }
    }

    private func everywhere(_ s: AppLocalization) -> some View {
        SettingsGroupBox {
            SettingsRow(title: s.moreEverywhereTitle, onTap: { open(MoreLinks.shared.SITE) }) { openArrow }
            SettingsDivider()
            ShareLink(item: MoreLinks.shared.shareText()) {
                SettingsRow(title: s.moreShare) {
                    Image(systemName: "square.and.arrow.up").font(.fj(14, .semibold)).foregroundStyle(FJTheme.color.primary)
                }
            }
            .buttonStyle(.plain)
        }
    }

    private func community(_ s: AppLocalization) -> some View {
        SettingsGroupBox(title: s.moreCommunityTitle) {
            ForEach(Array(MoreLinks.shared.community.enumerated()), id: \.offset) { index, link in
                if index > 0 { SettingsDivider() }
                SettingsRow(title: MoreLinks.shared.communityTitle(key: link.key), onTap: { open(link.url) }) { openArrow }
            }
        }
    }

    private var openArrow: some View {
        Image(systemName: "arrow.up.forward").font(.fj(13, .bold)).foregroundStyle(FJTheme.color.primary)
            .flipsForRightToLeftLayoutDirection(true)
    }

    private func open(_ url: String) {
        if let target = URL(string: url) { openURL(target) }
    }
}
