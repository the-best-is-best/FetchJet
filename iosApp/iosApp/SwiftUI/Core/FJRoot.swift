import SwiftUI
import UIKit
import SharedLogic

struct FJTabItem: Identifiable {
    let tab: DashboardTab
    let label: String
    let icon: String
    let selectedIcon: String
    let count: Int
    var id: Int32 { tab.ordinal }
}

extension FJStore {
    var tabItems: [FJTabItem] {
        let s = strings
        let tasks = state.allTasks
        var items: [FJTabItem] = [
            FJTabItem(tab: .all, label: s.homeTabAll, icon: "tray.and.arrow.down", selectedIcon: "tray.and.arrow.down.fill", count: tasks.count),
            FJTabItem(tab: .active, label: s.homeTabActive, icon: "bolt", selectedIcon: "bolt.fill",
                      count: tasks.filter { $0.status == .downloading || $0.status == .connecting }.count),
            FJTabItem(tab: .completed, label: s.homeTabDone, icon: "checkmark.circle", selectedIcon: "checkmark.circle.fill",
                      count: tasks.filter { $0.status == .completed }.count),
        ]
        if host.torrentEnabled {
            items.append(FJTabItem(tab: .torrent, label: s.homeTabTorrent, icon: "point.3.connected.trianglepath.dotted",
                                   selectedIcon: "point.3.filled.connected.trianglepath.dotted",
                                   count: tasks.filter { $0.engineType == .bittorrent }.count))
        }
        if host.mediaEnabled {
            items.append(FJTabItem(tab: .media, label: s.homeTabMedia, icon: "film", selectedIcon: "film.fill",
                                   count: tasks.filter { $0.engineType == .mediaStream }.count))
        }
        items.append(FJTabItem(tab: .settings, label: s.homeSettings, icon: "gearshape", selectedIcon: "gearshape.fill", count: 0))
        return items
    }

    func selectTab(_ tab: DashboardTab) {
        selectedTaskId = nil
        send(DashboardUiEventOnTabSelected(tab: tab))
    }
}

struct FJRoot: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        Group {
            if sizeClass == .regular {
                FJSplitShell()
            } else {
                FJPhoneShell()
            }
        }
        .background(FJTheme.color.background.ignoresSafeArea())
        .tint(FJTheme.color.primary)
        .background(AppDialogsHost())
        .sheet(isPresented: Binding(
            get: { store.state.isAddDialogVisible },
            set: { if !$0 { store.send(DashboardUiEventOnToggleAddDialog(show: false)) } }
        )) {
            AddDownloadSheet().environmentObject(store)
        }
        .overlay(alignment: .bottom) { FJToastHost() }
        .environment(\.layoutDirection, store.isRtl ? .rightToLeft : .leftToRight)
        .id(store.language)
        .onAppear { FJInterfaceStyle.apply(store.state.themeMode) }
        .onChange(of: store.state.themeMode) { _, mode in FJInterfaceStyle.apply(mode) }
    }
}

struct FJDeviceSheetModifier: ViewModifier {
    @EnvironmentObject var store: FJStore

    func body(content: Content) -> some View {
        content.sheet(isPresented: Binding(
            get: { store.state.deviceShareTaskId != nil },
            set: { if !$0 { store.send(DashboardUiEventOnDismissShareSheet.shared) } }
        )) {
            if let id = store.state.deviceShareTaskId {
                DeviceSendSheet(taskId: id).environmentObject(store)
            }
        }
    }
}

struct FJListPage: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        ZStack {
            FJTheme.color.background.ignoresSafeArea()
            if store.state.selectedTab == .settings {
                SettingsScreen()
            } else {
                TaskListScreen()
                    .overlay(alignment: .bottomTrailing) { FJAddButton().padding(16) }
            }
        }
    }
}

struct FJAddButton: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        Button {
            store.send(DashboardUiEventOnToggleAddDialog(show: true))
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(FJTheme.color.onPrimary)
                .frame(width: 56, height: 56)
                .background(FJTheme.color.primary, in: RoundedRectangle(cornerRadius: 18))
                .shadow(color: .black.opacity(0.25), radius: 6, y: 3)
        }
        .accessibilityLabel(store.strings.homeAddDownload)
    }
}

struct FJPhoneShell: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        VStack(spacing: 0) {
            NavigationStack {
                FJListPage()
                    .navigationDestination(item: $store.selectedTaskId) { id in
                        TaskDetailsScreen(taskId: id)
                    }
            }
            FJBottomBar()
        }
        .modifier(FJDeviceSheetModifier())
    }
}

struct FJBottomBar: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        HStack(spacing: 0) {
            ForEach(store.tabItems) { item in
                let selected = store.state.selectedTab == item.tab
                Button { store.selectTab(item.tab) } label: {
                    VStack(spacing: 3) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: selected ? item.selectedIcon : item.icon)
                                .symbolRenderingMode(.monochrome)
                                .font(.system(size: 18))
                                .frame(width: 44, height: 26)
                                .background(selected ? FJTheme.color.primaryContainer : .clear,
                                            in: Capsule())
                            if item.count > 0 {
                                Text("\(item.count)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(FJTheme.color.onPrimary)
                                    .padding(.horizontal, 4)
                                    .frame(minWidth: 15, minHeight: 15)
                                    .background(FJTheme.color.primary, in: Capsule())
                                    .offset(x: 4, y: -4)
                            }
                        }
                        Text(item.label)
                            .font(.system(size: 10, weight: selected ? .semibold : .regular))
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .foregroundStyle(selected ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.bottom, 2)
        .background(FJTheme.color.surface.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Divider().overlay(FJTheme.color.outlineVariant) }
    }
}

struct FJSplitShell: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        NavigationSplitView {
            List(store.tabItems.filter { $0.tab != .settings }) { item in
                Button { store.selectTab(item.tab) } label: {
                    HStack {
                        Label(item.label, systemImage: item.icon)
                        Spacer()
                        if item.count > 0 {
                            Text("\(item.count)").font(FJTheme.font.caption)
                                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        }
                    }
                }
                .listRowBackground(store.state.selectedTab == item.tab ? FJTheme.color.primaryContainer : Color.clear)
            }
            .safeAreaInset(edge: .bottom) {
                if let item = store.tabItems.first(where: { $0.tab == .settings }) {
                    let selected = store.state.selectedTab == item.tab
                    Button { store.selectTab(item.tab) } label: {
                        Label(item.label, systemImage: selected ? item.selectedIcon : item.icon)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .padding(.horizontal, 16)
                            .background(selected ? FJTheme.color.primaryContainer : Color.clear,
                                        in: RoundedRectangle(cornerRadius: 10))
                            .contentShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(FJTheme.color.onSurface)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                }
            }
            .navigationTitle("FetchJet")
        } detail: {
            NavigationStack {
                FJListPage()
                    .navigationDestination(item: $store.selectedTaskId) { id in
                        TaskDetailsScreen(taskId: id)
                    }
            }
        }
        .modifier(FJDeviceSheetModifier())
    }
}

struct FJToastHost: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        ZStack {
            if let toast = store.toast {
                Text(toast.text)
                    .font(FJTheme.font.callout)
                    .foregroundStyle(FJTheme.color.onSurface)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant))
                    .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 80)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onTapGesture { store.toast = nil }
                    .task(id: toast.id) {
                        try? await Task.sleep(nanoseconds: 3_500_000_000)
                        if store.toast?.id == toast.id { store.toast = nil }
                    }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: store.toast)
    }
}

enum FJInterfaceStyle {
    static func apply(_ themeMode: String) {
        let style: UIUserInterfaceStyle
        switch themeMode {
        case "light": style = .light
        case "dark": style = .dark
        default: style = .unspecified
        }
        for scene in UIApplication.shared.connectedScenes {
            for window in (scene as? UIWindowScene)?.windows ?? [] {
                window.overrideUserInterfaceStyle = style
            }
        }
    }
}
