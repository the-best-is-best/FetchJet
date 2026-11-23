import SwiftUI
import SharedLogic

private struct HiddenRow: Identifiable {
    let id: String
    var task: DownloadTask? = nil
    var playlist: PlaylistGroup? = nil
    var folder: FolderGroup? = nil
}

private struct HiddenGroupRef {
    let key: String
    let playlistId: String
    let folder: String
    let canPause: Bool
    let canResume: Bool
    let pauseTitle: String
    let resumeTitle: String

    init(_ group: PlaylistGroup, key: String, _ s: AppLocalization) {
        self.key = key
        playlistId = group.id
        folder = ""
        canPause = group.canPause
        canResume = group.canResume
        pauseTitle = s.pause
        resumeTitle = s.resume
    }

    init(_ group: FolderGroup, key: String, _ s: AppLocalization) {
        self.key = key
        playlistId = ""
        folder = group.folder
        canPause = group.canPause
        canResume = group.canResume
        pauseTitle = s.homePauseAll
        resumeTitle = s.homeResumeAll
    }
}

struct HiddenScreen: View {
    @EnvironmentObject var store: FJStore
    let onBack: () -> Void
    @State private var checked: Set<String> = []
    @State private var detailId: String?
    @State private var sheet: HiddenSheetKind?

    private var actions: TaskRowActions {
        TaskRowActions(store: store, showDetails: { detailId = $0.id }, select: { checked.insert($0.id) })
    }

    private var pageTasks: [DownloadTask]? { DashboardHiddenGroupsKt.hiddenPageTasks(store.state) }
    private var inPage: Bool { pageTasks != nil }

    private var rows: [HiddenRow] {
        if let page = pageTasks { return page.map { HiddenRow(id: $0.id, task: $0) } }
        var out: [HiddenRow] = []
        for item in DashboardHiddenGroupsKt.hiddenItems(store.state) {
            if let single = item as? TaskListItemSingle {
                out.append(HiddenRow(id: single.task.id, task: single.task))
            } else if let grouped = item as? TaskListItemGroup {
                out.append(HiddenRow(id: grouped.key, playlist: grouped.group))
            } else if let folder = item as? TaskListItemFolder {
                out.append(HiddenRow(id: folder.key, folder: folder.group))
            }
        }
        return out
    }

    private func rowIds(_ keys: Set<String>) -> [String] {
        DashboardHiddenGroupsKt.hiddenTaskIdsOfRows(store.state, keys: Array(keys))
    }

    private func goBack() {
        if inPage {
            checked = []
            store.send(DashboardUiEventOnHiddenCloseGroup.shared)
        } else {
            onBack()
        }
    }
    private var selectionMode: Bool { !checked.isEmpty }

    private func addHidden() {
        store.send(DashboardUiEventOnHiddenAddDownload.shared)
    }

    var body: some View {
        let s = store.strings
        let rows = self.rows
        VStack(spacing: 0) {
            VStack(spacing: 8) {
                Text(s.hiddenNoteEncrypted)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
                if let playlist = DashboardHiddenGroupsKt.hiddenOpenPlaylist(store.state) {
                    HiddenGroupBar(tag: "LIST", title: playlist.title, summary: playlist.summaryText,
                                   canPause: playlist.canPause, canResume: playlist.canResume,
                                   playlistId: playlist.id, folder: "")
                } else if let folder = DashboardHiddenGroupsKt.hiddenOpenFolder(store.state) {
                    HiddenGroupBar(tag: "DIR", title: folder.label, summary: folder.summaryText,
                                   canPause: folder.canPause, canResume: folder.canResume,
                                   playlistId: "", folder: folder.folder)
                }
                if selectionMode {
                    TaskSelectionBar(
                        count: checked.count,
                        allChecked: !rows.isEmpty && rows.allSatisfy { checked.contains($0.id) },
                        onCheckAll: { all in checked = all ? Set(rows.map { $0.id }) : [] },
                        onDelete: { store.send(DashboardUiEventOnRemoveSelectedClicked(taskIds: rowIds(checked))) },
                        onUnhide: {
                            store.send(DashboardUiEventOnUnhideTasks(taskIds: rowIds(checked)))
                            checked = []
                        },
                        onClose: { checked = [] }
                    )
                }
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 8)
            if store.state.hidden.tasks.isEmpty {
                emptyState(s)
            } else {
                list(rows)
            }
        }
        .background(FJTheme.color.background.ignoresSafeArea())
        .overlay(alignment: .bottomTrailing) {
            if !selectionMode { FJAddButton(action: addHidden).padding(16) }
        }
        .navigationTitle(s.hiddenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: goBack) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.backward").font(.fj(15, .semibold))
                        Text(inPage ? s.playlistBack : s.moreTitle).font(.fj(17))
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if !store.state.hidden.tasks.isEmpty {
                        Button { store.send(DashboardUiEventOnUnhideAll.shared) } label: { Text(s.hiddenUnhideAll) }
                    }
                    Button { store.send(DashboardUiEventOnHiddenShowRecoveryCode.shared) } label: { Text(s.hiddenRecoveryTitle) }
                    Button { sheet = .restore } label: { Text(s.hiddenRecoveryRestoreTitle) }
                    Button { sheet = .change } label: { Text(s.hiddenChangePin) }
                    Button(role: .destructive) { sheet = .remove } label: { Text(s.hiddenRemovePin) }
                    Button(action: onBack) { Label(s.hiddenLock, systemImage: "lock") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(item: $sheet) { kind in
            switch kind {
            case .change: HiddenChangePinSheet().environmentObject(store)
            case .remove: HiddenRemovePinSheet().environmentObject(store)
            case .restore: HiddenRestoreSheet().environmentObject(store)
            default: EmptyView()
            }
        }
        .sheet(isPresented: Binding(get: { detailId != nil }, set: { if !$0 { detailId = nil } })) {
            if let id = detailId {
                NavigationStack {
                    TaskDetailsScreen(taskId: id)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button(s.done) { detailId = nil }
                            }
                        }
                }
                .environmentObject(store)
                .hiddenPrivacyCover()
            }
        }
        .onChange(of: rows.map { $0.id }) { _, ids in
            let set = Set(ids)
            if !checked.isSubset(of: set) { checked = checked.intersection(set) }
        }
        .onChange(of: store.state.hidden.tasks.map { $0.id }) { _, ids in
            if let id = detailId, !ids.contains(id) { detailId = nil }
        }
        .onDisappear { store.hiddenLock() }
    }

    private func emptyState(_ s: AppLocalization) -> some View {
        VStack(spacing: 8) {
            Spacer()
            Image(systemName: "eye.slash")
                .font(.fj(44))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            Text(s.hiddenEmpty)
                .font(FJTheme.font.headline)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func list(_ rows: [HiddenRow]) -> some View {
        List {
            ForEach(rows) { entry in rowView(entry) }
            Color.clear.frame(height: 40)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .environment(\.defaultMinListRowHeight, 0)
        .scrollContentBackground(.hidden)
        .background(FJTheme.color.background)
    }

    @ViewBuilder
    private func rowView(_ entry: HiddenRow) -> some View {
        if let group = entry.playlist {
            groupRow(HiddenGroupRef(group, key: entry.id, store.strings)) {
                PlaylistGroupRowView(group: group, checked: checked.contains(entry.id), selectionMode: selectionMode, hiddenMode: true)
            }
        } else if let folder = entry.folder {
            groupRow(HiddenGroupRef(folder, key: entry.id, store.strings)) {
                FolderGroupRowView(group: folder, checked: checked.contains(entry.id), selectionMode: selectionMode, hiddenMode: true)
            }
        } else if let task = entry.task {
            row(task)
        }
    }

    private func openGroup(_ ref: HiddenGroupRef) {
        if ref.playlistId.isEmpty {
            store.send(DashboardUiEventOnHiddenOpenFolder(folder: ref.folder))
        } else {
            store.send(DashboardUiEventOnHiddenOpenPlaylist(playlistId: ref.playlistId))
        }
    }

    private func unhideGroup(_ ref: HiddenGroupRef) {
        store.send(DashboardUiEventOnUnhideGroup(playlistId: ref.playlistId, folder: ref.folder))
    }

    private func groupRow<Content: View>(_ ref: HiddenGroupRef, @ViewBuilder content: () -> Content) -> some View {
        let s = store.strings
        let isChecked = checked.contains(ref.key)
        return content()
            .onTapGesture {
                if selectionMode { toggle(ref.key) } else { openGroup(ref) }
            }
            .contextMenu {
                if selectionMode {
                    Button(isChecked ? s.selectNone : s.selectAll) { toggle(ref.key) }
                } else {
                    Button(s.open) { openGroup(ref) }
                    Button { unhideGroup(ref) } label: { Label(s.hiddenUnhide, systemImage: "eye") }
                    if ref.canPause {
                        Button(ref.pauseTitle) { store.send(DashboardUiEventOnHiddenGroupPause(playlistId: ref.playlistId, folder: ref.folder)) }
                    }
                    if ref.canResume {
                        Button(ref.resumeTitle) { store.send(DashboardUiEventOnHiddenGroupResume(playlistId: ref.playlistId, folder: ref.folder)) }
                    }
                    Button(s.rowMenuSelect) { checked.insert(ref.key) }
                }
            }
            .listRowInsets(EdgeInsets(top: 3, leading: 8, bottom: 3, trailing: 8))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
            .swipeActions(edge: .leading, allowsFullSwipe: false) {
                Button { unhideGroup(ref) } label: { Label(s.hiddenUnhide, systemImage: "eye") }
                    .tint(FJTheme.color.primary)
            }
    }

    private func row(_ task: DownloadTask) -> some View {
        let s = store.strings
        let isChecked = checked.contains(task.id)
        return TaskRowView(task: task, checked: isChecked, selectionMode: selectionMode) { action in
            actions.quick(task, action)
        }
        .onTapGesture {
            if selectionMode { toggle(task.id) } else { detailId = task.id }
        }
        .contextMenu {
            if selectionMode {
                Button(isChecked ? s.selectNone : s.selectAll) { toggle(task.id) }
            } else {
                rowMenu(task)
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
        .swipeActions(edge: .leading, allowsFullSwipe: false) {
            Button {
                store.send(DashboardUiEventOnUnhideTasks(taskIds: [task.id]))
            } label: {
                Label(s.hiddenUnhide, systemImage: "eye")
            }
            .tint(FJTheme.color.primary)
        }
    }

    @ViewBuilder
    private func rowMenu(_ task: DownloadTask) -> some View {
        let s = store.strings
        let entries = RowMenu.shared.entries(task: task, options: RowMenuOptions(canSend: false, canCompress: false, selectEntry: true))
        ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
            if entry.dividerBefore { Divider() }
            Button(role: entry.danger ? ButtonRole.destructive : nil) {
                actions.pick(task, entry)
            } label: {
                Text(RowMenu.shared.label(entry: entry, s: s))
            }
        }
    }

    private func toggle(_ id: String) {
        if checked.contains(id) { checked.remove(id) } else { checked.insert(id) }
    }
}

private struct HiddenGroupBar: View {
    @EnvironmentObject var store: FJStore
    let tag: String
    let title: String
    let summary: String
    let canPause: Bool
    let canResume: Bool
    let playlistId: String
    let folder: String

    var body: some View {
        let s = store.strings
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                TaskKindSquare(tag: tag, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.fj(16, .bold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(2)
                    Text(summary)
                        .font(.fj(12))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 8) {
                if canPause {
                    Button(s.homePauseAll) {
                        store.send(DashboardUiEventOnHiddenGroupPause(playlistId: playlistId, folder: folder))
                    }
                    .font(.fj(12, .semibold))
                    .buttonStyle(.borderless)
                }
                if canResume {
                    Button(s.homeResumeAll) {
                        store.send(DashboardUiEventOnHiddenGroupResume(playlistId: playlistId, folder: folder))
                    }
                    .font(.fj(12, .semibold))
                    .buttonStyle(.borderless)
                }
                Spacer(minLength: 0)
                Button {
                    store.send(DashboardUiEventOnUnhideGroup(playlistId: playlistId, folder: folder))
                } label: {
                    Label(s.hiddenUnhide, systemImage: "eye")
                        .font(.fj(12, .semibold))
                }
                .buttonStyle(.borderless)
                .foregroundStyle(FJTheme.color.primary)
            }
        }
        .padding(12)
        .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant.opacity(0.35), lineWidth: 1))
    }
}
