import SwiftUI
import SharedLogic

struct AddSheetDialogs: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        ZStack {
            if let task = store.state.duplicateTaskAlert {
                scrim { store.send(DashboardUiEventOnDismissDuplicateDialog.shared) }
                AddDuplicateCard(task: task).transition(.scale(scale: 0.95).combined(with: .opacity))
            } else if let conflict = store.state.fileConflictAlert {
                scrim { store.send(DashboardUiEventOnDismissFileConflictDialog.shared) }
                AddConflictCard(data: conflict).transition(.scale(scale: 0.95).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.18), value: store.state.duplicateTaskAlert?.id)
        .animation(.easeOut(duration: 0.18), value: store.state.fileConflictAlert?.fileName)
    }

    private func scrim(_ onTap: @escaping () -> Void) -> some View {
        Color.black.opacity(0.45).ignoresSafeArea().onTapGesture(perform: onTap)
    }
}

private struct AddDialogFrame<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 14) { content }
            .padding(20)
            .frame(maxWidth: 440)
            .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(FJTheme.color.outlineVariant.opacity(0.6), lineWidth: 1))
            .shadow(color: .black.opacity(0.3), radius: 16)
            .padding(.horizontal, 20)
    }
}

private struct AddDialogTitle: View {
    let icon: String
    let title: String
    let subtitle: String
    let tint: Color
    var body: some View {
        HStack(spacing: 10) {
            Text(icon)
                .font(.system(size: 20))
                .frame(width: 40, height: 40)
                .background(tint.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 17, weight: .bold)).foregroundStyle(FJTheme.color.onSurface)
                Text(subtitle).font(FJTheme.font.small).foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
        }
    }
}

private struct AddDialogButton: View {
    let label: String
    let fill: Color?
    let textColor: Color
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(textColor)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(fill ?? .clear, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(fill == nil ? FJTheme.color.outlineVariant : .clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct AddDuplicateCard: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask

    var body: some View {
        let strings = store.strings
        let color: Color = {
            switch task.status {
            case .completed: return FJTheme.color.statusCompleted
            case .paused: return FJTheme.color.statusPaused
            case .error: return FJTheme.color.statusError
            case .downloading, .connecting: return FJTheme.color.statusConnecting
            default: return FJTheme.color.onSurfaceVariant
            }
        }()
        AddDialogFrame {
            AddDialogTitle(icon: "📋", title: strings.duplicateDialogTitle, subtitle: strings.duplicateDialogSubtitle, tint: FJTheme.color.statusPaused)
            VStack(alignment: .leading, spacing: 8) {
                Text(task.fileName.isEmpty ? task.url : task.fileName)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(2)
                HStack {
                    Text(strings.duplicateDialogStatus(status: task.status.name))
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(color)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                    Spacer()
                    if task.progress > 0 {
                        Text("\(Int(task.progress * 100))%")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(FJTheme.color.primary)
                    }
                }
                if task.progress > 0 && task.status != .completed {
                    ProgressView(value: Double(task.progress)).tint(FJTheme.color.primary)
                }
            }
            .padding(12)
            .background(FJTheme.color.surfaceVariant.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
            Text(strings.duplicateDialogQuestion)
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            VStack(spacing: 8) {
                AddDialogButton(label: "▶️ \(strings.duplicateDialogResumeExisting)", fill: FJTheme.color.primary, textColor: FJTheme.color.onPrimary) {
                    store.send(DashboardUiEventOnResumeExistingDuplicateClicked.shared)
                }
                AddDialogButton(label: "🔄 \(strings.duplicateDialogRedownload)", fill: FJTheme.color.surfaceVariant, textColor: FJTheme.color.onSurface) {
                    store.send(DashboardUiEventOnRestartDuplicateClicked.shared)
                }
                AddDialogButton(label: strings.cancel, fill: nil, textColor: FJTheme.color.onSurfaceVariant) {
                    store.send(DashboardUiEventOnDismissDuplicateDialog.shared)
                }
            }
        }
    }
}

struct AddConflictCard: View {
    @EnvironmentObject var store: FJStore
    let data: FileConflictData

    var body: some View {
        let strings = store.strings
        AddDialogFrame {
            AddDialogTitle(icon: "⚠️", title: strings.overwriteDialogTitle, subtitle: strings.overwriteDialogSubtitle, tint: FJTheme.color.error)
            VStack(alignment: .leading, spacing: 8) {
                item(strings.overwriteDialogFileNameLabel, data.fileName)
                item(strings.overwriteDialogFolderLabel, data.destinationPath)
                item(strings.overwriteDialogNewCopyLabel, data.suggestedNewName)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(FJTheme.color.surfaceVariant.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
            Text(strings.overwriteDialogQuestion)
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            VStack(spacing: 8) {
                AddDialogButton(label: "📝 \(strings.overwriteDialogSaveAsCopy(name: data.suggestedNewName))", fill: FJTheme.color.primary, textColor: FJTheme.color.onPrimary) {
                    store.send(DashboardUiEventOnSaveAsCopyClicked(newFileName: data.suggestedNewName))
                }
                AddDialogButton(label: "⚠️ \(strings.overwriteDialogOverwriteExisting)", fill: FJTheme.color.error, textColor: FJTheme.color.onError) {
                    store.send(DashboardUiEventOnConfirmOverwriteClicked.shared)
                }
                AddDialogButton(label: strings.cancel, fill: nil, textColor: FJTheme.color.onSurfaceVariant) {
                    store.send(DashboardUiEventOnDismissFileConflictDialog.shared)
                }
            }
        }
    }

    private func item(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).font(.system(size: 10, weight: .semibold)).foregroundStyle(FJTheme.color.onSurfaceVariant)
            Text(value).font(.system(size: 12, weight: .medium)).foregroundStyle(FJTheme.color.onSurface).lineLimit(2).truncationMode(.middle)
        }
    }
}

struct AddFolderPicker: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    let initialPath: String
    let onSelected: (String) -> Void
    private let resolver = PathResolver()
    @State private var path = ""
    @State private var subfolders: [String] = []
    @State private var showHidden = false
    @State private var creating = false
    @State private var newName = ""
    @State private var createError: String?

    var body: some View {
        let strings = store.strings
        NavigationStack {
            List {
                Section {
                    Text(path)
                        .font(FJTheme.font.mono)
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(3)
                        .truncationMode(.head)
                    if let parent = resolver.getParentDirectory(path: path) {
                        Button("⬆️ \(strings.dirPickerUp)") { go(parent) }
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(Array(resolver.getAvailableDirectories().enumerated()), id: \.offset) { _, pair in
                                if let name = pair.first as? String, let p = pair.second as? String {
                                    AddChoiceChip(label: name, selected: p == path) { go(p) }
                                }
                            }
                        }
                    }
                }
                .listRowBackground(FJTheme.color.surfaceVariant.opacity(0.5))
                Section {
                    if creating {
                        HStack {
                            TextField(strings.dirPickerFolderName, text: $newName)
                                .textInputAutocapitalization(.never)
                            Button(strings.dirPickerCreate) { create() }
                                .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                        if let createError { Text(createError).font(FJTheme.font.small).foregroundStyle(FJTheme.color.error) }
                    }
                    if subfolders.isEmpty {
                        Text(strings.dirPickerNoSubfolders).foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                    ForEach(subfolders, id: \.self) { sub in
                        Button {
                            go((path as NSString).appendingPathComponent(sub))
                        } label: {
                            HStack {
                                Text("📁")
                                Text(sub).foregroundStyle(FJTheme.color.onSurface)
                                if sub.hasPrefix(".") {
                                    Text(strings.dirPickerHiddenTag).font(FJTheme.font.small).foregroundStyle(FJTheme.color.onSurfaceVariant)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").foregroundStyle(FJTheme.color.onSurfaceVariant)
                            }
                        }
                    }
                } header: {
                    HStack {
                        Text(strings.dirPickerSubfolders(n: Int32(subfolders.count)))
                        Spacer()
                        Button(showHidden ? strings.dirPickerHiddenOn : strings.dirPickerHiddenOff) {
                            showHidden.toggle()
                            refresh()
                        }
                        .font(FJTheme.font.small)
                        Button(creating ? strings.cancel : "➕ \(strings.dirPickerNewFolder)") {
                            creating.toggle()
                            newName = ""
                            createError = nil
                        }
                        .font(FJTheme.font.small)
                    }
                    .textCase(nil)
                }
                .listRowBackground(FJTheme.color.surfaceVariant.opacity(0.5))
            }
            .scrollContentBackground(.hidden)
            .background(FJTheme.color.surface)
            .navigationTitle("📁 \(strings.dirPickerSelectFolder)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(strings.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(strings.dirPickerSelectFolder) {
                        onSelected(path)
                        dismiss()
                    }
                    .fontWeight(.bold)
                }
            }
        }
        .tint(FJTheme.color.primary)
        .onAppear {
            path = initialPath.isEmpty ? resolver.getDownloadsDirectory() : initialPath
            refresh()
        }
    }

    private func go(_ p: String) {
        path = p
        creating = false
        refresh()
    }

    private func refresh() {
        subfolders = resolver.listSubdirectories(path: path, includeHidden: showHidden)
    }

    private func create() {
        if let created = resolver.createSubdirectory(parentPath: path, name: newName.trimmingCharacters(in: .whitespaces)) {
            newName = ""
            creating = false
            createError = nil
            go(created)
        } else {
            createError = store.strings.dirPickerCreateFailed
        }
    }
}
