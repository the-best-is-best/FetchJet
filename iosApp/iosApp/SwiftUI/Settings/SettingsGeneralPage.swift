import SwiftUI
import SharedLogic

struct SettingsGeneralPage: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let s = store.strings
        let st = store.state
        VStack(alignment: .leading, spacing: 24) {
            SettingsGroupBox(title: s.language) {
                SettingsRow(title: s.language, subtitle: s.languageHint) {
                    languageMenu(s)
                }
            }
            SettingsGroupBox(title: s.clipboardGroup, footer: s.clipboardFooter) {
                SettingsSwitchRow(
                    title: s.watchClipboard,
                    subtitle: st.monitorClipboard ? s.watchClipboardOn : s.settingOff,
                    isOn: st.monitorClipboard
                ) { store.send(DashboardUiEventOnMonitorClipboardChanged(on: $0)) }
                SettingsDivider()
                SettingsSwitchRow(
                    title: s.alsoVideoPages,
                    subtitle: s.alsoVideoPagesHint,
                    isOn: st.clipboardVideoPages,
                    enabled: st.monitorClipboard
                ) { store.send(DashboardUiEventOnClipboardVideoPagesChanged(on: $0)) }
            }
            SettingsGroupBox(title: s.appearanceGroup) {
                SettingsRow(title: s.themeTitle, subtitle: s.themeHint)
                SettingsSegments(
                    options: ["system", "light", "dark"],
                    selected: st.themeMode,
                    label: { settingsThemeLabel($0, s) },
                    onSelect: { if $0 != st.themeMode { store.send(DashboardUiEventOnThemeModeChanged(mode: $0)) } }
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
                SettingsDivider()
                SettingsRow(title: s.speedShownIn, subtitle: s.speedShownInHint)
                SettingsSegments(
                    options: ["MB/s", "KB/s", "Mbps", "Kbps"],
                    selected: settingsUnitKey(st.speedDisplayUnit),
                    label: { $0 },
                    onSelect: { store.send(DashboardUiEventOnSpeedDisplayUnitChanged(unit: $0)) }
                )
                .environment(\.layoutDirection, .leftToRight)
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
            }
        }
    }

    private func languageMenu(_ s: AppLocalization) -> some View {
        let app = AppLanguage.shared
        let current = app.choice
        let langs = store.host.languages()
        let currentName = current == app.SYSTEM
            ? "\(s.systemLanguage) (\(s.languageName))"
            : (langs.first { $0.localeCode == current }?.languageName ?? current)
        return Menu {
            Button {
                if current != app.SYSTEM { store.send(DashboardUiEventOnLanguageChanged(choice: app.SYSTEM)) }
            } label: {
                if current == app.SYSTEM { Label(s.systemLanguage, systemImage: "checkmark") } else { Text(s.systemLanguage) }
            }
            ForEach(langs, id: \.localeCode) { lang in
                Button {
                    if lang.localeCode != current { store.send(DashboardUiEventOnLanguageChanged(choice: lang.localeCode)) }
                } label: {
                    if lang.localeCode == current { Label(lang.languageName, systemImage: "checkmark") } else { Text(lang.languageName) }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text(currentName).font(FJTheme.font.callout.weight(.semibold)).lineLimit(1)
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(FJTheme.color.primary)
        }
    }
}
