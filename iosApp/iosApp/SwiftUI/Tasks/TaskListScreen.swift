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

private struct ListRow: Identifiable {
    let id: String
    let task: DownloadTask?
    let group: PlaylistGroup?
    var folder: FolderGroup? = nil
}

struct TaskListScreen: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var search = ""
    @State private var collapsed: Set<String> = []
    @State private var checked: Set<String> = []
    @State private var menuPicked = false
    @State private var listWidth: CGFloat = 0

    private var selectionMode: Bool { !checked.isEmpty }

    private var previewWidth: CGFloat {
        let row = max(listWidth - 16, 200)
        return sizeClass == .regular ? min(row, 360) : row
    }

    private var openGroup: PlaylistGroup? {
        guard let id = store.state.openPlaylistId, let record = store.state.playlists[id] else { return nil }
        return PlaylistGroup(record: record, tasks: PlaylistGroupsKt.playlistTasks(all: store.state.allTasks, playlistId: id))
    }

    private var openFolderGroup: FolderGroup? {
        guard store.state.groupByFolder, let folder = store.state.openFolder else { return nil }
        return PlaylistGroupsKt.folderGroup(all: store.state.allTasks, records: store.state.playlists, folder: folder)
    }

    private var inGroupPage: Bool { store.state.openPlaylistId != nil || openFolderGroup != nil }

    private var allTasks: [DownloadTask] {
        if let id = store.state.openPlaylistId, store.state.playlists[id] != nil {
            return store.state.allTasks.filter { $0.playlistId == id }
        }
        if let folder = openFolderGroup {
            return PlaylistGroupsKt.folderTasks(all: store.state.allTasks, records: store.state.playlists, folder: folder.folder)
        }
        return store.state.filteredTasks
    }

    private var shownTasks: [DownloadTask] {
        let q = search.trimmingCharacters(in: .whitespaces)
        if q.isEmpty { return allTasks }
        let records = store.state.playlists
        let inPage = inGroupPage
        return allTasks.filter {
            $0.fileName.localizedCaseInsensitiveContains(q) || $0.url.localizedCaseInsensitiveContains(q) ||
                ($0.originalUrl?.localizedCaseInsensitiveContains(q) ?? false) ||
                (!inPage && PlaylistGroupsKt.matchesPlaylistTitle(task: $0, records: records, query: q))
        }
    }

    private var folding: Bool {
        !inGroupPage && (store.state.groupByFolder || !store.state.playlists.isEmpty)
    }

    private func rows(_ tasks: [DownloadTask], placed: inout Set<String>) -> [ListRow] {
        guard folding else { return tasks.map { ListRow(id: $0.id, task: $0, group: nil) } }
        var out: [ListRow] = []
        for item in PlaylistGroupsKt.groupDownloads(shown: tasks, all: store.state.allTasks, records: store.state.playlists,
                                                    byFolder: store.state.groupByFolder) {
            if let single = item as? TaskListItemSingle {
                out.append(ListRow(id: single.task.id, task: single.task, group: nil))
            } else if let grouped = item as? TaskListItemGroup {
                if placed.insert(grouped.key).inserted {
                    out.append(ListRow(id: grouped.key, task: nil, group: grouped.group))
                }
            } else if let folder = item as? TaskListItemFolder {
                if placed.insert(folder.key).inserted {
                    out.append(ListRow(id: folder.key, task: nil, group: nil, folder: folder.group))
                }
            }
        }
        return out
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
                if let group = openGroup {
                    PlaylistPageBar(group: group)
                        .padding(.bottom, 8)
                } else if let folder = openFolderGroup {
                    FolderPageBar(group: folder)
                        .padding(.bottom, 8)
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
                .font(.fj(44))
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
                if store.state.selectedTab == .all && !inGroupPage {
                    let sections = groups(tasks)
                    let sectionRows: [[ListRow]] = {
                        var placed = Set<String>()
                        return sections.map { rows($0.tasks, placed: &placed) }
                    }()
                    ForEach(Array(sections.enumerated()), id: \.element.id) { index, group in
                        let isCollapsed = collapsed.contains(group.key)
                        let items = sectionRows[index]
                        if !items.isEmpty {
                            TaskSectionHeader(icon: group.icon, title: group.title, count: items.count,
                                              color: group.color, collapsed: isCollapsed) {
                                withAnimation(.easeInOut(duration: 0.15)) {
                                    if isCollapsed { collapsed.remove(group.key) } else { collapsed.insert(group.key) }
                                }
                            }
                            .listRowInsets(EdgeInsets(top: 6, leading: 8, bottom: 2, trailing: 8))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                            if !isCollapsed {
                                ForEach(items) { item($0) }
                            }
                        }
                    }
                } else {
                    let items: [ListRow] = {
                        var placed = Set<String>()
                        return rows(tasks, placed: &placed)
                    }()
                    ForEach(items) { item($0) }
                }
                Color.clear.frame(height: 80)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { listWidth = $0 }
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

    @ViewBuilder
    private func item(_ entry: ListRow) -> some View {
        if let group = entry.group {
            groupRow(group)
        } else if let folder = entry.folder {
            folderRow(folder)
        } else if let task = entry.task {
            row(task)
        }
    }

    private func groupRow(_ group: PlaylistGroup) -> some View {
        let s = store.strings
        let ids = group.tasks.map { $0.id }
        let isChecked = !ids.isEmpty && ids.allSatisfy { checked.contains($0) }
        return PlaylistGroupRowView(group: group, checked: isChecked, selectionMode: selectionMode)
            .onTapGesture {
                if selectionMode {
                    if isChecked { checked.subtract(ids) } else { checked.formUnion(ids) }
                } else {
                    search = ""
                    store.send(DashboardUiEventOnOpenPlaylist(playlistId: group.id))
                }
            }
            .contextMenu {
                Button(s.open) { store.send(DashboardUiEventOnOpenPlaylist(playlistId: group.id)) }
                if group.canPause {
                    Button(s.pause) { store.send(DashboardUiEventOnPlaylistGroupPause(playlistId: group.id)) }
                }
                if group.canResume {
                    Button(s.resume) { store.send(DashboardUiEventOnPlaylistGroupResume(playlistId: group.id)) }
                }
                if !group.record.url.isEmpty {
                    Button(group.isPage ? s.grabCheckNew : s.playlistCheckNew) { store.send(DashboardUiEventOnPlaylistCheck(playlistId: group.id)) }
                }
                Divider()
                Button(group.isPage ? s.folderDeleteAll : s.playlistDeleteAll, role: .destructive) {
                    store.send(DashboardUiEventOnPlaylistGroupRemove(playlistId: group.id))
                }
            }
            .listRowInsets(EdgeInsets(top: 3, leading: 8, bottom: 3, trailing: 8))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button(role: .destructive) {
                    store.send(DashboardUiEventOnPlaylistGroupRemove(playlistId: group.id))
                } label: {
                    Label(group.isPage ? s.folderDeleteAll : s.playlistDeleteAll, systemImage: "trash")
                }
            }
    }

    private func folderRow(_ group: FolderGroup) -> some View {
        let s = store.strings
        let ids = group.tasks.map { $0.id }
        let isChecked = !ids.isEmpty && ids.allSatisfy { checked.contains($0) }
        return FolderGroupRowView(group: group, checked: isChecked, selectionMode: selectionMode)
            .onTapGesture {
                if selectionMode {
                    if isChecked { checked.subtract(ids) } else { checked.formUnion(ids) }
                } else {
                    search = ""
                    store.send(DashboardUiEventOnOpenFolderGroup(folder: group.folder))
                }
            }
            .contextMenu {
                Button(s.open) { store.send(DashboardUiEventOnOpenFolderGroup(folder: group.folder)) }
                if group.canPause {
                    Button(s.homePauseAll) { store.send(DashboardUiEventOnFolderGroupPause(folder: group.folder)) }
                }
                if group.canResume {
                    Button(s.homeResumeAll) { store.send(DashboardUiEventOnFolderGroupResume(folder: group.folder)) }
                }
                Divider()
                Button(s.folderDeleteAll, role: .destructive) {
                    store.send(DashboardUiEventOnFolderGroupRemove(folder: group.folder))
                }
            }
            .listRowInsets(EdgeInsets(top: 3, leading: 8, bottom: 3, trailing: 8))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button(role: .destructive) {
                    store.send(DashboardUiEventOnFolderGroupRemove(folder: group.folder))
                } label: {
                    Label(s.folderDeleteAll, systemImage: "trash")
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
        .contextMenu {
            if selectionMode {
                Button(isChecked ? s.selectNone : s.selectAll) {
                    menuPicked = true
                    toggle(task.id)
                }
            } else {
                rowMenu(task)
            }
        } preview: {
            TaskRowView(task: task, checked: isChecked, selectionMode: selectionMode) { _ in }
                .environmentObject(store)
                .frame(width: previewWidth)
                .fixedSize(horizontal: false, vertical: true)
                .onAppear { menuPicked = false }
                .onDisappear {
                    if !menuPicked { checked.insert(task.id) }
                }
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

    @ViewBuilder
    private func rowMenu(_ task: DownloadTask) -> some View {
        let s = store.strings
        let entries = RowMenu.shared.entries(task: task, options: RowMenuOptions(canSend: true, canCompress: true, selectEntry: true))
        ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
            if entry.dividerBefore { Divider() }
            Button(role: entry.danger ? ButtonRole.destructive : nil) {
                pickRowMenu(task, entry)
            } label: {
                Text(RowMenu.shared.label(entry: entry, s: s))
            }
        }
    }

    private func pickRowMenu(_ task: DownloadTask, _ entry: RowMenuEntry) {
        menuPicked = true
        switch entry.name {
        case "OPEN":
            store.openFile(task)
        case "OPEN_FOLDER":
            let resolver = PathResolver()
            if resolver.isOpenFolderSupported() {
                _ = resolver.openFileLocation(filePath: store.host.taskFilePath(task: task))
            } else {
                store.selectedTaskId = task.id
            }
        case "PAUSE":
            quickAction(task, .pause)
        case "RESUME":
            store.send(DashboardUiEventOnResumeClicked(taskId: task.id))
        case "EXTRACT":
            store.send(DashboardUiEventOnExtractTaskClicked(taskId: task.id))
        case "COMPRESS":
            store.send(DashboardUiEventOnAudioCompressClicked(taskId: task.id))
        case "SUBTITLES":
            store.selectedTaskId = task.id
            store.send(DashboardUiEventOnSubtitlesClicked(taskId: task.id))
        case "SEND_TO_DEVICE":
            store.send(DashboardUiEventOnShareTaskClicked(taskId: task.id))
        case "COPY_LINK":
            store.send(DashboardUiEventOnCopyTaskLink(taskId: task.id))
        case "SELECT":
            checked.insert(task.id)
        case "DETAILS":
            store.selectedTaskId = task.id
        case "DELETE":
            store.send(DashboardUiEventOnRemoveClicked(taskId: task.id))
        default:
            break
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
                    .font(.fj(10, .bold))
                    .foregroundStyle(color)
                    .frame(width: 12)
                    .padding(.trailing, 6)
                Image(systemName: icon)
                    .font(.fj(12, .bold))
                    .foregroundStyle(color)
                    .padding(.trailing, 6)
                Text(title)
                    .font(.fj(14, .bold))
                    .foregroundStyle(color)
                Text("\(count)")
                    .font(.fj(11, .bold))
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
                .font(.fj(14))
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
                Image(systemName: "xmark").font(.fj(14, .semibold))
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(FJTheme.color.onSurface)
            Text(s.homeSelectedCount(count: Int32(count)))
                .font(.fj(13, .semibold))
                .foregroundStyle(FJTheme.color.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Button { onCheckAll(!allChecked) } label: {
                Text(allChecked ? s.selectNone : s.selectAll)
                    .font(.fj(12, .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(FJTheme.color.primary)
            Spacer(minLength: 0)
            Button(action: onExport) {
                Text(s.homeExport)
                    .font(.fj(12, .semibold))
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
                    .font(.fj(12, .semibold))
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
                .font(.fj(12, .semibold))
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
                .font(.fj(24, .bold))
                .foregroundStyle(FJTheme.color.onBackground)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 0) {
                Text("↓ " + store.host.formatSpeed(bytesPerSec: st.totalDownloadSpeedBytesPerSec, unit: st.speedDisplayUnit))
                    .font(.fj(12, .semibold))
                    .foregroundStyle(FJTheme.color.primary)
                    .lineLimit(1)
                Text("↑ " + store.host.formatSpeed(bytesPerSec: st.totalUploadSpeedBytesPerSec, unit: st.speedDisplayUnit))
                    .font(.fj(11))
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
                    .font(.fj(18, .bold))
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
                Text("🐢").font(.fj(17))
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
                        .font(.fj(12, .semibold))
                        .foregroundStyle(capped ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                    Image(systemName: "chevron.down")
                        .font(.fj(8, .bold))
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

struct PlaylistGroupRowView: View {
    @EnvironmentObject var store: FJStore
    let group: PlaylistGroup
    let checked: Bool
    let selectionMode: Bool

    var body: some View {
        let host = store.host
        let accent = FJTheme.color.primary
        let running = group.status == .downloading
        let size = group.totalBytes > 0 ? " · " + host.formatBytes(bytes: group.totalBytes) : ""
        let speed = running && group.speed > 0 ? " · " + host.formatSpeed(bytesPerSec: group.speed, unit: store.state.speedDisplayUnit) : ""
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                if selectionMode {
                    Image(systemName: checked ? "checkmark.square.fill" : "square")
                        .font(.fj(20))
                        .foregroundStyle(checked ? accent : FJTheme.color.onSurfaceVariant)
                        .frame(width: 24, height: 24)
                        .padding(.trailing, 12)
                }
                TaskKindSquare(tag: "LIST", size: 40)
                    .padding(.trailing, 12)
                VStack(alignment: .leading, spacing: 3) {
                    Text(group.title)
                        .font(.fj(14, .semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Text(group.summaryText + size + speed)
                        .font(.fj(12))
                        .foregroundStyle(group.failed > 0 ? FJTheme.color.error : FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.forward")
                    .font(.fj(12, .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .padding(.leading, 8)
                if !selectionMode && (group.canPause || group.canResume) {
                    TaskQuickActionButton(action: group.canPause ? .pause : .resume, size: 44) {
                        if group.canPause {
                            store.send(DashboardUiEventOnPlaylistGroupPause(playlistId: group.id))
                        } else {
                            store.send(DashboardUiEventOnPlaylistGroupResume(playlistId: group.id))
                        }
                    }
                    .padding(.leading, 8)
                }
            }
            if group.status != .done {
                TaskThinBar(
                    fraction: group.progress,
                    color: group.status == .paused || group.status == .waiting ? FJTheme.hex(0xFBBF24)
                        : (group.status == .someFailed ? FJTheme.color.error : accent)
                )
                .padding(.top, 10)
            }
        }
        .padding(12)
        .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(checked ? accent.opacity(0.7) : FJTheme.color.outlineVariant.opacity(0.35), lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct PlaylistPageBar: View {
    @EnvironmentObject var store: FJStore
    let group: PlaylistGroup

    private func languageName(_ code: String) -> String {
        for task in group.tasks {
            if let track = task.subtitleTracks.first(where: { $0.langCode == code }), !track.langName.isEmpty {
                return track.langName
            }
        }
        return code
    }

    private func pageNote(_ page: PlaylistLanguagesUi) -> String {
        let s = store.strings
        if page.reading { return s.playlistReadingLanguages }
        if page.adding {
            return s.playlistLanguageAdding
                .replacingOccurrences(of: "%1", with: String(page.done))
                .replacingOccurrences(of: "%2", with: String(page.total))
        }
        return page.message
    }

    var body: some View {
        let s = store.strings
        let languages = group.record.subtitleLanguages
        let page: PlaylistLanguagesUi? = {
            guard let p = store.state.playlistLanguages, p.playlistId == group.id else { return nil }
            return p
        }()
        let subtitlesLine = languages.isEmpty ? s.playlistNoSubtitles
            : s.playlistSubtitlesFor.replacingOccurrences(of: "%1", with: languages.map { languageName($0) }.joined(separator: ", "))
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Button {
                    store.send(DashboardUiEventOnClosePlaylist.shared)
                } label: {
                    Label(s.playlistBack, systemImage: "chevron.backward")
                        .font(.fj(13, .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                }
                .buttonStyle(.borderless)
                Spacer(minLength: 0)
                Menu {
                    let resolver = PathResolver()
                    if resolver.isOpenFolderSupported(), let folder = group.tasks.first?.destinationPath ?? Optional(group.record.folder), !folder.isEmpty {
                        Button(s.rowMenuOpenFolder) { _ = resolver.openDirectoryInExplorer(path: folder) }
                    }
                    if !group.record.url.isEmpty {
                        Button(group.isPage ? s.grabCheckNew : s.playlistCheckNew) { store.send(DashboardUiEventOnPlaylistCheck(playlistId: group.id)) }
                    }
                    if group.canPause {
                        Button(s.homePauseAll) { store.send(DashboardUiEventOnPlaylistGroupPause(playlistId: group.id)) }
                    }
                    if group.canResume {
                        Button(s.homeResumeAll) { store.send(DashboardUiEventOnPlaylistGroupResume(playlistId: group.id)) }
                    }
                    Divider()
                    Button(group.isPage ? s.folderDeleteAll : s.playlistDeleteAll, role: .destructive) {
                        store.send(DashboardUiEventOnPlaylistGroupRemove(playlistId: group.id))
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.fj(18))
                        .foregroundStyle(FJTheme.color.primary)
                        .frame(width: 36, height: 36)
                }
            }
            HStack(spacing: 10) {
                TaskKindSquare(tag: "LIST", size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(group.title)
                        .font(.fj(16, .bold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(2)
                    Text(group.summaryText)
                        .font(.fj(12))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                Spacer(minLength: 0)
            }
            if !group.record.url.isEmpty {
                Button {
                    store.send(DashboardUiEventOnPlaylistCheck(playlistId: group.id))
                } label: {
                    Label(group.isPage ? s.grabCheckNew : s.playlistCheckNew, systemImage: "arrow.triangle.2.circlepath")
                        .font(.fj(13, .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(FJTheme.color.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.primary.opacity(0.5), lineWidth: 1))
                }
                .buttonStyle(.borderless)
            }
            if !group.isPage {
            HStack(spacing: 8) {
                Text("💬 " + subtitlesLine)
                    .font(.fj(12))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(2)
                Spacer(minLength: 0)
                if page == nil {
                    Button {
                        store.send(DashboardUiEventOnPlaylistReadLanguages(playlistId: group.id))
                    } label: {
                        Text("+ " + s.playlistAddLanguage)
                            .font(.fj(12, .semibold))
                            .foregroundStyle(FJTheme.color.primary)
                    }
                    .buttonStyle(.borderless)
                }
            }
            if let page {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        if page.reading || page.adding { ProgressView().controlSize(.small) }
                        Text(pageNote(page))
                            .font(.fj(11))
                            .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        Spacer(minLength: 0)
                        if !page.adding {
                            Button {
                                store.send(DashboardUiEventOnPlaylistLanguagesDismiss.shared)
                            } label: {
                                Image(systemName: "xmark")
                                    .font(.fj(11, .semibold))
                                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                                    .frame(width: 28, height: 28)
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                    if !page.reading && !page.adding && !page.available.isEmpty {
                        AddChipRow {
                            ForEach(page.available, id: \.langCode) { track in
                                AddChoiceChip(label: "+ " + (track.langName.isEmpty ? track.langCode : track.langName), selected: false) {
                                    store.send(DashboardUiEventOnPlaylistAddLanguage(playlistId: group.id, langCode: track.langCode))
                                }
                            }
                        }
                    }
                }
                .padding(10)
                .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 10))
            }
            }
        }
        .padding(12)
        .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant.opacity(0.35), lineWidth: 1))
    }
}

struct FolderGroupRowView: View {
    @EnvironmentObject var store: FJStore
    let group: FolderGroup
    let checked: Bool
    let selectionMode: Bool

    var body: some View {
        let host = store.host
        let accent = FJTheme.color.primary
        let running = group.status == .downloading
        let size = group.totalBytes > 0 ? " · " + host.formatBytes(bytes: group.totalBytes) : ""
        let speed = running && group.speed > 0 ? " · " + host.formatSpeed(bytesPerSec: group.speed, unit: store.state.speedDisplayUnit) : ""
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                if selectionMode {
                    Image(systemName: checked ? "checkmark.square.fill" : "square")
                        .font(.fj(20))
                        .foregroundStyle(checked ? accent : FJTheme.color.onSurfaceVariant)
                        .frame(width: 24, height: 24)
                        .padding(.trailing, 12)
                }
                TaskKindSquare(tag: "DIR", size: 40)
                    .padding(.trailing, 12)
                VStack(alignment: .leading, spacing: 3) {
                    Text(group.label)
                        .font(.fj(14, .semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(1)
                        .truncationMode(.head)
                    Text(group.summaryText + size + speed)
                        .font(.fj(12))
                        .foregroundStyle(group.failed > 0 ? FJTheme.color.error : FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.forward")
                    .font(.fj(12, .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .padding(.leading, 8)
                if !selectionMode && (group.canPause || group.canResume) {
                    TaskQuickActionButton(action: group.canPause ? .pause : .resume, size: 44) {
                        if group.canPause {
                            store.send(DashboardUiEventOnFolderGroupPause(folder: group.folder))
                        } else {
                            store.send(DashboardUiEventOnFolderGroupResume(folder: group.folder))
                        }
                    }
                    .padding(.leading, 8)
                }
            }
            if group.status != .done {
                TaskThinBar(
                    fraction: group.progress,
                    color: group.status == .paused || group.status == .waiting ? FJTheme.hex(0xFBBF24)
                        : (group.status == .someFailed ? FJTheme.color.error : accent)
                )
                .padding(.top, 10)
            }
        }
        .padding(12)
        .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(checked ? accent.opacity(0.7) : FJTheme.color.outlineVariant.opacity(0.35), lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct FolderPageBar: View {
    @EnvironmentObject var store: FJStore
    let group: FolderGroup

    var body: some View {
        let s = store.strings
        let resolver = PathResolver()
        let canOpen = resolver.isOpenFolderSupported()
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Button {
                    store.send(DashboardUiEventOnCloseFolderGroup.shared)
                } label: {
                    Label(s.playlistBack, systemImage: "chevron.backward")
                        .font(.fj(13, .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                }
                .buttonStyle(.borderless)
                Spacer(minLength: 0)
                Menu {
                    if canOpen {
                        Button(s.rowMenuOpenFolder) { _ = resolver.openDirectoryInExplorer(path: group.path) }
                    }
                    if group.canPause {
                        Button(s.homePauseAll) { store.send(DashboardUiEventOnFolderGroupPause(folder: group.folder)) }
                    }
                    if group.canResume {
                        Button(s.homeResumeAll) { store.send(DashboardUiEventOnFolderGroupResume(folder: group.folder)) }
                    }
                    Divider()
                    Button(s.folderDeleteAll, role: .destructive) {
                        store.send(DashboardUiEventOnFolderGroupRemove(folder: group.folder))
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.fj(18))
                        .foregroundStyle(FJTheme.color.primary)
                        .frame(width: 36, height: 36)
                }
            }
            HStack(spacing: 10) {
                TaskKindSquare(tag: "DIR", size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(group.label)
                        .font(.fj(16, .bold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(2)
                    Text(group.summaryText)
                        .font(.fj(12))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    Text(group.path)
                        .font(.fj(11))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant.opacity(0.8))
                        .lineLimit(2)
                        .truncationMode(.middle)
                }
                Spacer(minLength: 0)
            }
            if canOpen {
                Button {
                    _ = resolver.openDirectoryInExplorer(path: group.path)
                } label: {
                    Label(s.rowMenuOpenFolder, systemImage: "folder")
                        .font(.fj(13, .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(FJTheme.color.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.primary.opacity(0.5), lineWidth: 1))
                }
                .buttonStyle(.borderless)
            }
            HStack(spacing: 8) {
                if group.canPause {
                    Button(s.homePauseAll) { store.send(DashboardUiEventOnFolderGroupPause(folder: group.folder)) }
                        .font(.fj(12, .semibold))
                        .buttonStyle(.borderless)
                }
                if group.canResume {
                    Button(s.homeResumeAll) { store.send(DashboardUiEventOnFolderGroupResume(folder: group.folder)) }
                        .font(.fj(12, .semibold))
                        .buttonStyle(.borderless)
                }
                Spacer(minLength: 0)
                Button(s.folderDeleteAll, role: .destructive) {
                    store.send(DashboardUiEventOnFolderGroupRemove(folder: group.folder))
                }
                .font(.fj(12, .semibold))
                .foregroundStyle(FJTheme.color.error)
                .buttonStyle(.borderless)
            }
        }
        .padding(12)
        .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant.opacity(0.35), lineWidth: 1))
    }
}
