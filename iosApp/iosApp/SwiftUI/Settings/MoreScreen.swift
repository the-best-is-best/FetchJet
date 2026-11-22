import SwiftUI
import SharedLogic

struct MoreScreen: View {
    @EnvironmentObject var store: FJStore
    @StateObject private var live = SettingsLive()
    @State private var inSettings = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        ZStack {
            if inSettings {
                SettingsScreen(onBack: { withAnimation(.easeInOut(duration: 0.22)) { inSettings = false } })
                    .transition(.move(edge: .trailing))
            } else {
                page.transition(.move(edge: .leading))
            }
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
