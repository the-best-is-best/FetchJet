import SwiftUI
import SharedLogic

struct SettingsNetworkPage: View {
    @ObservedObject var live: SettingsLive

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            SettingsProxyCard(live: live)
            SettingsLoginsCard(live: live)
        }
    }
}

struct SettingsProxyCard: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var live: SettingsLive
    @State private var kind: ProxyKind = .none
    @State private var host = ""
    @State private var port = ""
    @State private var user = ""
    @State private var password = ""
    @State private var justSaved = false
    @State private var loadedFrom: ProxySettings?

    var body: some View {
        let s = store.strings
        let draft = ProxySettings(
            kind: kind,
            host: host.trimmingCharacters(in: .whitespaces),
            port: Int32(port.trimmingCharacters(in: .whitespaces)) ?? 0,
            user: user.trimmingCharacters(in: .whitespaces),
            password: password
        )
        let invalid = kind != .none && !draft.isOn
        let changed = !draft.isEqual(live.proxy)
        SettingsGroupBox(title: s.netProxyGroup, footer: s.netProxyHintIos) {
            SettingsSegments(
                options: ProxyKind.entries,
                selected: kind,
                label: { settingsProxyKindLabel($0, s) },
                onSelect: { kind = $0; justSaved = false }
            )
            .padding(16)
            if kind != .none {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 10) {
                        SettingsField(label: s.netProxyHost, placeholder: "127.0.0.1", text: edited($host), keyboard: .URL)
                        SettingsField(label: s.netProxyPort, placeholder: kind == .socks5 ? "1080" : "8080",
                                      text: Binding(get: { port }, set: { v in
                                          port = String(v.filter { $0.isNumber }.prefix(5)); justSaved = false
                                      }), keyboard: .numberPad)
                            .frame(width: 110)
                    }
                    HStack(spacing: 10) {
                        SettingsField(label: s.netProxyUser, text: edited($user))
                        SettingsField(label: s.netProxyPassword, text: edited($password), secure: true)
                            .disabled(user.trimmingCharacters(in: .whitespaces).isEmpty)
                            .opacity(user.trimmingCharacters(in: .whitespaces).isEmpty ? 0.5 : 1)
                    }
                    if invalid && (!host.isEmpty || !port.isEmpty) {
                        Text(s.netProxyBadPort).font(FJTheme.font.caption).foregroundStyle(FJTheme.color.error)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
            HStack(spacing: 12) {
                Spacer()
                if justSaved && !changed {
                    SettingsStatusLabel(text: s.netSaved, color: FJTheme.color.statusCompleted)
                }
                Button(s.netSave) {
                    let saved = live.proxy
                    let toSave = kind == .none
                        ? ProxySettings(kind: .none, host: saved.host, port: saved.port, user: saved.user, password: saved.password)
                        : ProxySettings(kind: draft.kind, host: draft.host, port: draft.port, user: draft.user,
                                        password: draft.user.isEmpty ? "" : draft.password)
                    NetworkSettings.shared.setProxy(p: toSave)
                    live.refresh()
                    load(live.proxy)
                    justSaved = true
                }
                .buttonStyle(SettingsButtonStyle(filled: true))
                .disabled(!changed || invalid)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
        .onAppear { if loadedFrom == nil { load(live.proxy) } }
        .onChange(of: live.proxy) { _, p in load(p) }
    }

    private func edited(_ b: Binding<String>) -> Binding<String> {
        Binding(get: { b.wrappedValue }, set: { b.wrappedValue = $0; justSaved = false })
    }

    private func load(_ p: ProxySettings) {
        loadedFrom = p
        kind = p.kind
        host = p.host
        port = p.port > 0 ? "\(p.port)" : ""
        user = p.user
        password = p.password
    }
}

struct SettingsLoginsCard: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var live: SettingsLive
    @State private var site = ""
    @State private var user = ""
    @State private var password = ""

    var body: some View {
        let s = store.strings
        let canAdd = SiteLogin.companion.cleanSite(text: site).contains(".") && !user.trimmingCharacters(in: .whitespaces).isEmpty
        SettingsGroupBox(title: s.netLoginsGroup, footer: s.netLoginsHint) {
            if live.logins.isEmpty {
                SettingsRow(title: s.netLoginsEmpty)
            }
            ForEach(live.logins, id: \.site) { login in
                SettingsRow(title: login.site, subtitle: login.user) {
                    Button(s.netRemove) {
                        NetworkSettings.shared.removeLogin(site: login.site)
                        live.refresh()
                    }
                    .font(FJTheme.font.callout.weight(.semibold))
                    .foregroundStyle(FJTheme.color.error)
                    .buttonStyle(.plain)
                }
                SettingsDivider()
            }
            VStack(alignment: .leading, spacing: 10) {
                SettingsField(label: s.netLoginSite, placeholder: "example.com", text: $site, keyboard: .URL)
                HStack(spacing: 10) {
                    SettingsField(label: s.netUser, text: $user)
                    SettingsField(label: s.netProxyPassword, text: $password, secure: true)
                }
                HStack {
                    Spacer()
                    Button(s.netLoginAdd) {
                        NetworkSettings.shared.saveLogin(login: SiteLogin(site: site, user: user, password: password))
                        site = ""; user = ""; password = ""
                        live.refresh()
                    }
                    .buttonStyle(SettingsButtonStyle(filled: true))
                    .disabled(!canAdd)
                }
            }
            .padding(16)
        }
    }
}
