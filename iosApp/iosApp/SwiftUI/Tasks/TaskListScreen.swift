import SwiftUI
import SharedLogic

private struct TaskGroup: Identifiable {
    let key: String
    let icon: String
    let title: String
    let color: Color
    let tasks: [DownloadTask]
    var id: String { key }
}

struct TaskListScreen: View {
    @EnvironmentObject var store: FJStore
    @State private var search = ""
    @State private var collapsed: Set<String> = []
    @State private var checked: Set<String> = []

    private var selectionMode: Bool { !checked.isEmpty }

    private var allTasks: [DownloadTask] { store.state.filteredTasks }

    private var shownTasks: [DownloadTask] {
        let q = search.trimmingCharacters(in: .whitespaces)
        if q.isEmpty { return allTasks }
        return allTasks.filter {
            $0.fileName.localizedCaseInsensitiveContains(q) || $0.url.localizedCaseInsensitiveContains(q) ||
                ($0.originalUrl?.localizedCaseInsensitiveContains(q) ?? false)
        }
    }

    private func groups(_ tasks: [DownloadTask]) -> [TaskGroup] {
        let s = store.strings
        let downloading = tasks.filter { TaskUi.matchesDownloading($0) }
        let waiting = tasks.filter { $0.waitingInQueue }
        let completed = tasks.filter { $0.status == .completed }
        let used = Set((downloading + waiting + completed).map { $0.id })
        let stopped = tasks.filter { !used.contains($0.id) }
        return [
            TaskGroup(key: "run", icon: "arrow.down", title: s.homeGroupDownloading, color: FJTheme.color.primary, tasks: downloading),
            TaskGroup(key: "wait", icon: "hourglass", title: s.homeGroupWaiting, color: FJTheme.color.onSurfaceVariant, tasks: waiting),
            TaskGroup(key: "stop", icon: "pause.fill", title: s.homeGroupPausedFailed, color: FJTheme.color.statusPaused, tasks: stopped),
            TaskGroup(key: "done", icon: "checkmark", title: s.homeGroupCompleted, color: FJTheme.color.tertiary, tasks: completed),
        ].filter { !$0.tasks.isEmpty }
    }

    var body: some View {
        let tasks = shownTasks
        VStack(spacing: 0) {
            TaskListHeader()
            VStack(spacing: 0) {
                if store.state.isDownloadingYtDlp, let tool = store.state.toolDownloadName {
                    TaskToolsBanner(tool: tool, fraction: store.state.toolDownloadFraction?.floatValue)
                }
                if !allTasks.isEmpty {
                    TaskSearchField(text: $search)
                        .padding(.bottom, 8)
                }
                if selectionMode {
                    TaskSelectionBar(
                        count: checked.count,
                        allChecked: !tasks.isEmpty && tasks.allSatisfy { checked.contains($0.id) },
                        onCheckAll: { all in checked = all ? Set(tasks.map { $0.id }) : [] },
                        onDelete: { store.send(DashboardUiEventOnRemoveSelectedClicked(taskIds: Array(checked))) },
                        onExport: { store.send(DashboardUiEventOnExportLinksClicked(taskIds: Array(checked))) },
                        onClose: { checked = [] }
                    )
                    .padding(.bottom, 8)
                }
            }
            .padding(.horizontal, 8)

            if tasks.isEmpty {
                emptyState
            } else {
                list(tasks)
            }
        }
        .background(FJTheme.color.background)
        .toolbar(.hidden, for: .navigationBar)
        .onChange(of: store.state.allTasks.map { $0.id }) { _, ids in
            let set = Set(ids)
            if !checked.isSubset(of: set) { checked = checked.intersection(set) }
        }
    }

    private var emptyState: some View {
        let s = store.strings
        let q = search.trimmingCharacters(in: .whitespaces)
        return VStack(spacing: 4) {
            Spacer()
            Image(systemName: "tray.and.arrow.down")
                .font(.system(size: 44))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .padding(.bottom, 12)
            Text(q.isEmpty ? s.homeNoTasksInView : s.homeNoDownloadsMatch(query: q))
                .font(FJTheme.font.headline)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Text(q.isEmpty ? s.homeAddDownloadToGetStarted : s.homeTryAnotherName)
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.onSurfaceVariant.opacity(0.7))
                .multilineTextAlignment(.center)
            Spacer()
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func list(_ tasks: [DownloadTask]) -> some View {
        ScrollViewReader { proxy in
            List {
                Color.clear.frame(height: 0).id("top")
                    .listRowInsets(EdgeInsets())
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                if store.state.selectedTab == .all {
                    ForEach(groups(tasks)) { group in
                        let isCollapsed = collapsed.contains(group.key)
                        TaskSectionHeader(icon: group.icon, title: group.title, count: group.tasks.count,
                                          color: group.color, collapsed: isCollapsed) {
                            withAnimation(.easeInOut(duration: 0.15)) {
                                if isCollapsed { collapsed.remove(group.key) } else { collapsed.insert(group.key) }
                            }
                        }
                        .listRowInsets(EdgeInsets(top: 6, leading: 8, bottom: 2, trailing: 8))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        if !isCollapsed {
                            ForEach(group.tasks, id: \.id) { row($0) }
                        }
                    }
                } else {
                    ForEach(tasks, id: \.id) { row($0) }
                }
                Color.clear.frame(height: 80)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .environment(\.defaultMinListRowHeight, 0)
            .contentMargins(.top, 0, for: .scrollContent)
            .listSectionSpacing(0)
            .scrollContentBackground(.hidden)
            .background(FJTheme.color.background)
            .onChange(of: store.state.scrollToTopSignal) { _, signal in
                if signal > 0 { withAnimation { proxy.scrollTo("top", anchor: .top) } }
            }
        }
    }

    private func row(_ task: DownloadTask) -> some View {
        let s = store.strings
        let isChecked = checked.contains(task.id)
        return TaskRowView(task: task, checked: isChecked, selectionMode: selectionMode) { action in
            quickAction(task, action)
        }
        .onTapGesture {
            if selectionMode {
                toggle(task.id)
            } else {
                store.selectedTaskId = task.id
            }
        }
        .onLongPressGesture(minimumDuration: 0.4) {
            checked.insert(task.id)
        }
        .listRowInsets(EdgeInsets(top: 3, leading: 8, bottom: 3, trailing: 8))
        .listRowSeparator(.hidden)
        .listRowBackground(Color.clear)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                store.send(DashboardUiEventOnRemoveClicked(taskId: task.id))
            } label: {
                Label(s.delete_, systemImage: "trash")
            }
        }
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                toggle(task.id)
            } label: {
                Label(isChecked ? s.selectNone : s.selectAll, systemImage: isChecked ? "square" : "checkmark.square")
            }
            .tint(FJTheme.color.primary)
        }
    }

    private func toggle(_ id: String) {
        if checked.contains(id) { checked.remove(id) } else { checked.insert(id) }
    }

    private func quickAction(_ task: DownloadTask, _ action: TaskRowAction) {
        switch action {
        case .pause:
            if task.canResume()?.boolValue == false {
                store.selectedTaskId = task.id
            } else {
                store.send(DashboardUiEventOnPauseClicked(taskId: task.id))
            }
        case .resume, .startNow:
            store.send(DashboardUiEventOnResumeClicked(taskId: task.id))
        case .open:
            store.openFile(task)
        case .none:
            break
        }
    }
}

struct TaskSectionHeader: View {
    let icon: String
    let title: String
    let count: Int
    let color: Color
    let collapsed: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 0) {
                Image(systemName: collapsed ? "chevron.right" : "chevron.down")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(color)
                    .frame(width: 12)
                    .padding(.trailing, 6)
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(color)
                    .padding(.trailing, 6)
                Text(title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(color)
                Text("\(count)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(color)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(color.opacity(0.15), in: Capsule())
                    .padding(.leading, 8)
                Rectangle()
                    .fill(FJTheme.color.outlineVariant.opacity(0.5))
                    .frame(height: 1)
                    .padding(.leading, 10)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct TaskSearchField: View {
    @EnvironmentObject var store: FJStore
    @Binding var text: String
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            TextField(store.strings.homeSearchDownloadsByNamePlaceholder, text: $text)
                .font(FJTheme.font.callout)
                .foregroundStyle(FJTheme.color.onSurface)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focused)
                .submitLabel(.search)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                .buttonStyle(.borderless)
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 44)
        .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(focused ? FJTheme.color.primary : FJTheme.color.outlineVariant, lineWidth: focused ? 1.5 : 1)
        )
    }
}

struct TaskSelectionBar: View {
    @EnvironmentObject var store: FJStore
    let count: Int
    let allChecked: Bool
    let onCheckAll: (Bool) -> Void
    let onDelete: () -> Void
    let onExport: () -> Void
    let onClose: () -> Void

    var body: some View {
        let s = store.strings
        HStack(spacing: 8) {
            Button(action: onClose) {
                Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(FJTheme.color.onSurface)
            Text(s.homeSelectedCount(count: Int32(count)))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(FJTheme.color.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Button { onCheckAll(!allChecked) } label: {
                Text(allChecked ? s.selectNone : s.selectAll)
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(FJTheme.color.primary)
            Spacer(minLength: 0)
            Button(action: onExport) {
                Text(s.homeExport)
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .frame(height: 34)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outline, lineWidth: 1))
            }
            .buttonStyle(.borderless)
            .foregroundStyle(FJTheme.color.primary)
            .fixedSize()
            Button(action: onDelete) {
                Label(s.delete_, systemImage: "trash")
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .frame(height: 34)
                    .background(FJTheme.color.error, in: RoundedRectangle(cornerRadius: 10))
                    .foregroundStyle(FJTheme.color.onError)
            }
            .buttonStyle(.borderless)
            .fixedSize()
        }
    }
}

struct TaskToolsBanner: View {
    @EnvironmentObject var store: FJStore
    let tool: String
    let fraction: Float?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(store.strings.homeUpdatingMediaTools(tool: tool, percent: fraction.map { KotlinInt(int: Int32($0 * 100)) }))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(FJTheme.color.primary)
            if let f = fraction {
                ProgressView(value: Double(min(max(f, 0), 1)))
            } else {
                ProgressView().progressViewStyle(.linear)
            }
        }
        .tint(FJTheme.color.primary)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FJTheme.color.primary.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
        .padding(.bottom, 8)
    }
}

struct TaskListHeader: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.colorScheme) private var colorScheme
    private static let turtleBytes: Int64 = 512 * 1024

    private func shortLabel(_ bytes: Int64) -> String {
        if bytes >= 1024 * 1024 {
            let mb = Double(bytes) / (1024 * 1024)
            let n = mb == mb.rounded(.down) ? String(Int64(mb)) : String(Double(Int64(mb * 10)) / 10)
            return "\(n) MB/s"
        }
        return "\(bytes / 1024) KB/s"
    }

    var body: some View {
        let s = store.strings
        let st = store.state
        let unfinished = st.allTasks.filter { $0.status != .completed }
        let canPause = unfinished.contains { $0.status == .downloading || $0.status == .connecting || $0.status == .queued }
        let canResume = unfinished.contains { $0.status == .paused || $0.status == .error }
        let dark = store.host.isDark(themeMode: st.themeMode, systemDark: colorScheme == .dark)
        HStack(spacing: 6) {
            Text(s.homeDownloads)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(FJTheme.color.onBackground)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 0) {
                Text("↓ " + store.host.formatSpeed(bytesPerSec: st.totalDownloadSpeedBytesPerSec, unit: st.speedDisplayUnit))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(FJTheme.color.primary)
                    .lineLimit(1)
                Text("↑ " + store.host.formatSpeed(bytesPerSec: st.totalUploadSpeedBytesPerSec, unit: st.speedDisplayUnit))
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
            }
            .fixedSize()
            speedControl
            Menu {
                Button(s.homePauseAll) { store.send(DashboardUiEventOnPauseAllClicked.shared) }.disabled(!canPause)
                Button(s.homeResumeAll) { store.send(DashboardUiEventOnResumeAllClicked.shared) }.disabled(!canResume)
                Menu(s.homeSortBy(option: st.sortOption.label)) {
                    ForEach(TaskSortOption.entries, id: \.ordinal) { opt in
                        Button {
                            store.send(DashboardUiEventOnSortChanged(sort: opt))
                        } label: {
                            if opt == st.sortOption { Label(opt.label, systemImage: "checkmark") } else { Text(opt.label) }
                        }
                    }
                }
                Divider()
                Button(dark ? s.homeLightTheme : s.homeDarkTheme) {
                    store.send(DashboardUiEventOnThemeModeChanged(mode: dark ? "light" : "dark"))
                }
            } label: {
                Image(systemName: "ellipsis")
                    .rotationEffect(.degrees(90))
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var speedControl: some View {
        let s = store.strings
        let st = store.state
        let limit = st.globalDownloadSpeedLimitBytes
        let turtle = limit == Self.turtleBytes
        let capped = limit > 0 && !turtle
        let mode: String = {
            switch limit {
            case 0: return s.speedPresetHigh
            case st.speedPresetLowBytes: return s.speedPresetLow
            case st.speedPresetMediumBytes: return s.speedPresetMedium
            case st.speedPresetFastBytes: return s.speedPresetFast
            default: return shortLabel(limit)
            }
        }()
        let options: [(Int64, String)] = [
            (st.speedPresetLowBytes, "\(s.speedPresetLow)  ·  \(shortLabel(st.speedPresetLowBytes))"),
            (st.speedPresetMediumBytes, "\(s.speedPresetMedium)  ·  \(shortLabel(st.speedPresetMediumBytes))"),
            (st.speedPresetFastBytes, "\(s.speedPresetFast)  ·  \(shortLabel(st.speedPresetFastBytes))"),
            (0, "\(s.speedPresetHigh)  ·  \(s.speedLimitUnlimited)"),
        ]
        return HStack(spacing: 4) {
            Button {
                store.send(DashboardUiEventOnGlobalDownloadSpeedLimitChanged(limitBytesPerSec: turtle ? 0 : Self.turtleBytes))
            } label: {
                Text("🐢").font(.system(size: 17))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(turtle ? FJTheme.color.primary.opacity(0.22) : .clear, in: RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            Menu {
                ForEach(options, id: \.0) { value, label in
                    Button {
                        store.send(DashboardUiEventOnGlobalDownloadSpeedLimitChanged(limitBytesPerSec: value))
                    } label: {
                        if value == limit { Label(label, systemImage: "checkmark") } else { Text(label) }
                    }
                }
            } label: {
                HStack(spacing: 2) {
                    Text(mode)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(capped ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .background(capped ? FJTheme.color.primary.opacity(0.18) : FJTheme.color.surfaceVariant.opacity(0.4),
                            in: RoundedRectangle(cornerRadius: 10))
            }
        }
        .fixedSize()
    }
}
