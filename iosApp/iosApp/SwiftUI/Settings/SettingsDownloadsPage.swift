import SwiftUI
import SharedLogic

struct SettingsDownloadsPage: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var live: SettingsLive
    @State private var pickingFolder = false
    @State private var pathDraft: String?
    private let resolver = PathResolver()

    var body: some View {
        let s = store.strings
        let st = store.state
        let defaultDownloads = resolver.getDownloadsDirectory()
        VStack(alignment: .leading, spacing: 24) {
            SettingsGroupBox(title: s.saveLocationGroup, footer: s.saveLocationFooter) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Text("📁")
                        TextField(s.settingsFolderPath, text: Binding(
                            get: { pathDraft ?? st.destinationPath },
                            set: { pathDraft = $0; store.send(DashboardUiEventOnDestinationPathChanged(path: $0)) }
                        ))
                        .font(FJTheme.font.callout)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    }
                    .padding(.horizontal, 12)
                    .frame(height: 42)
                    .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant))
                    SettingsFlow(spacing: 8) {
                        Button(s.settingsChooseFolder) { pickingFolder = true }
                            .buttonStyle(SettingsButtonStyle(filled: true))
                        if resolver.isOpenFolderSupported() {
                            Button(s.open) { _ = resolver.openDirectoryInExplorer(path: st.destinationPath) }
                                .buttonStyle(SettingsButtonStyle())
                        }
                        if st.destinationPath != defaultDownloads {
                            Button(s.settingsUseDownloads) {
                                pathDraft = nil
                                store.send(DashboardUiEventOnDestinationPathChanged(path: defaultDownloads))
                            }
                            .buttonStyle(SettingsButtonStyle())
                        }
                    }
                }
                .padding(16)
            }
            SettingsGroupBox(title: s.settingsConnectionsGroup, footer: s.connectionsFooter) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(s.settingsConnectionsPerDownload).font(FJTheme.font.body.weight(.medium))
                    SettingsFlow(spacing: 6) {
                        ForEach([0, 1, 4, 8, 16, 32], id: \.self) { n in
                            SettingsChip(label: n == 0 ? s.settingsConnectionsAuto : "\(n)",
                                         selected: st.defaultConnections == Int32(n)) {
                                store.send(DashboardUiEventOnDefaultConnectionsChanged(connections: Int32(n)))
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            SettingsGroupBox(title: s.speedPresetsTitle, footer: s.speedPresetsFooter) {
                HStack {
                    Text(s.speedPresetHigh).font(FJTheme.font.headline)
                    Spacer()
                    Text(s.speedLimitUnlimited).font(FJTheme.font.caption.weight(.bold))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                .padding(16)
                SettingsDivider()
                presetRow(s.speedPresetFast, st.speedPresetFastBytes, st.speedPresetFastUnit, .fast)
                SettingsDivider()
                presetRow(s.speedPresetMedium, st.speedPresetMediumBytes, st.speedPresetMediumUnit, .medium)
                SettingsDivider()
                presetRow(s.speedPresetLow, st.speedPresetLowBytes, st.speedPresetLowUnit, .low)
            }
            SettingsScheduleCard(live: live)
            SettingsGroupBox(title: s.organizeGroup, footer: s.organizeFooter) {
                SettingsSwitchRow(
                    title: s.sortIntoFolders,
                    subtitle: st.sortIntoFolders ? s.sortIntoFoldersOn : s.sortIntoFoldersOff,
                    isOn: st.sortIntoFolders
                ) { store.send(DashboardUiEventOnSortIntoFoldersChanged(on: $0)) }
            }
            SettingsGroupBox(title: s.homeQueueTitle, footer: s.homeQueueFooter) {
                SettingsRow(
                    title: s.homeDownloadsAtSameTime,
                    subtitle: live.maxAtOnce <= 0 ? s.homeNoLimit : s.homeAtATime(count: live.maxAtOnce)
                )
                SettingsSegments(
                    options: [1, 2, 3, 4, 5, 8, 0] as [Int32],
                    selected: live.maxAtOnce,
                    label: { $0 == 0 ? s.homeNoLimit : "\($0)" },
                    onSelect: {
                        QueueManagerRepository.companion.getInstance().setMaxConcurrentDownloads(count: $0)
                        live.refresh()
                    }
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
            }
        }
        .sheet(isPresented: $pickingFolder) {
            SettingsFolderPicker(initialPath: st.destinationPath) { picked in
                pathDraft = nil
                store.send(DashboardUiEventOnDestinationPathChanged(path: picked))
            }
            .environmentObject(store)
        }
    }

    private func presetRow(_ name: String, _ bytes: Int64, _ unit: String, _ preset: SpeedPreset) -> some View {
        SettingsSpeedPresetRow(name: name, bytes: bytes, unit: unit, placeholder: store.strings.speedLimitValue) { b, u in
            store.send(DashboardUiEventOnSpeedPresetChanged(preset: preset, bytes: b, unit: u))
        }
        .padding(16)
    }
}

struct SettingsScheduleCard: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var live: SettingsLive

    var body: some View {
        let s = store.strings
        let sched = SpeedSchedule.shared
        SettingsGroupBox(footer: s.speedScheduleFooter) {
            SettingsSwitchRow(
                title: s.speedScheduleTitle,
                subtitle: live.scheduleOn && live.scheduleActive ? s.speedScheduleActive : nil,
                isOn: live.scheduleOn
            ) { sched.setEnabled(value: $0); live.refresh() }
            if live.scheduleOn {
                if live.scheduleWindows.isEmpty {
                    SettingsDivider()
                    Text(s.speedScheduleEmpty)
                        .font(FJTheme.font.body)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .padding(16)
                }
                ForEach(Array(live.scheduleWindows.enumerated()), id: \.offset) { index, window in
                    SettingsDivider()
                    SettingsScheduleWindowRow(index: index, window: window, live: live)
                        .padding(16)
                }
                SettingsDivider()
                Button("+  " + s.speedScheduleAdd) {
                    sched.addWindow(window: SpeedWindow(startMinutes: 8 * 60, endMinutes: 18 * 60, capBytes: 1024 * 1024, capUnit: "MB/s"))
                    live.refresh()
                }
                .buttonStyle(SettingsButtonStyle(filled: true))
                .padding(16)
            }
        }
    }
}

struct SettingsScheduleWindowRow: View {
    @EnvironmentObject var store: FJStore
    let index: Int
    let window: SpeedWindow
    @ObservedObject var live: SettingsLive

    var body: some View {
        let s = store.strings
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .bottom, spacing: 10) {
                timePicker(s.speedScheduleFrom, Int(window.startMinutes)) {
                    update(SpeedWindow(startMinutes: Int32($0), endMinutes: window.endMinutes, capBytes: window.capBytes, capUnit: window.capUnit))
                }
                timePicker(s.speedScheduleTo, Int(window.endMinutes)) {
                    update(SpeedWindow(startMinutes: window.startMinutes, endMinutes: Int32($0), capBytes: window.capBytes, capUnit: window.capUnit))
                }
                Spacer(minLength: 0)
                Button {
                    SpeedSchedule.shared.removeWindow(index: Int32(index))
                    live.refresh()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .frame(width: 34, height: 34)
                        .background(FJTheme.color.surfaceVariant, in: Circle())
                }
                .buttonStyle(.plain)
            }
            SettingsSpeedPresetRow(name: "", bytes: window.capBytes, unit: window.capUnit, placeholder: s.speedLimitValue) { b, u in
                update(SpeedWindow(startMinutes: window.startMinutes, endMinutes: window.endMinutes, capBytes: b, capUnit: u))
            }
        }
    }

    private func update(_ w: SpeedWindow) {
        SpeedSchedule.shared.updateWindow(index: Int32(index), window: w)
        live.refresh()
    }

    private func timePicker(_ label: String, _ minutes: Int, _ onChange: @escaping (Int) -> Void) -> some View {
        let m = ((minutes % 1440) + 1440) % 1440
        let cal = Calendar.current
        let base = cal.startOfDay(for: Date())
        let date = cal.date(byAdding: .minute, value: m, to: base) ?? base
        return VStack(alignment: .leading, spacing: 4) {
            Text(label).font(FJTheme.font.small.weight(.semibold)).foregroundStyle(FJTheme.color.onSurfaceVariant)
            DatePicker("", selection: Binding(get: { date }, set: { d in
                let c = cal.dateComponents([.hour, .minute], from: d)
                onChange((c.hour ?? 0) * 60 + (c.minute ?? 0))
            }), displayedComponents: .hourAndMinute)
            .labelsHidden()
        }
    }
}

struct SettingsFolderPicker: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    let initialPath: String
    let onPick: (String) -> Void
    @State private var current = ""
    @State private var subfolders: [String] = []
    @State private var creating = false
    @State private var newName = ""
    @State private var createFailed = false
    private let resolver = PathResolver()

    var body: some View {
        let s = store.strings
        NavigationStack {
            List {
                Section {
                    Text(current)
                        .font(FJTheme.font.mono)
                        .foregroundStyle(FJTheme.color.onSurface)
                        .environment(\.layoutDirection, .leftToRight)
                    if let parent = resolver.getParentDirectory(path: current) {
                        Button { go(parent) } label: { Label(s.dirPickerUp, systemImage: "arrow.up") }
                    }
                }
                .listRowBackground(FJTheme.color.surface)
                let quick = resolver.getAvailableDirectories()
                if !quick.isEmpty {
                    Section {
                        ForEach(Array(quick.enumerated()), id: \.offset) { _, pair in
                            let name = (pair.first as? String) ?? ""
                            let path = (pair.second as? String) ?? ""
                            Button { go(path) } label: { Label(name, systemImage: "folder") }
                        }
                    }
                    .listRowBackground(FJTheme.color.surface)
                }
                Section(s.dirPickerSubfolders(n: Int32(subfolders.count))) {
                    if creating {
                        HStack {
                            TextField(s.dirPickerFolderName, text: $newName)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                            Button(s.dirPickerCreate) {
                                if let made = resolver.createSubdirectory(parentPath: current, name: newName.trimmingCharacters(in: .whitespaces)) {
                                    creating = false
                                    newName = ""
                                    go(made)
                                } else {
                                    createFailed = true
                                }
                            }
                            .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                        if createFailed {
                            Text(s.dirPickerCreateFailed).font(FJTheme.font.caption).foregroundStyle(FJTheme.color.error)
                        }
                    }
                    Button(creating ? s.cancel : "+ " + s.dirPickerNewFolder) {
                        creating.toggle()
                        createFailed = false
                    }
                    if subfolders.isEmpty {
                        Text(s.dirPickerNoSubfolders).foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                    ForEach(subfolders, id: \.self) { path in
                        Button { go(path) } label: {
                            Label(settingsFolderName(path), systemImage: "folder.fill")
                                .foregroundStyle(FJTheme.color.onSurface)
                        }
                    }
                }
                .listRowBackground(FJTheme.color.surface)
            }
            .scrollContentBackground(.hidden)
            .background(FJTheme.color.background)
            .navigationTitle(s.dirPickerSelectFolder)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(s.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(s.dirPickerSelectFolder) { onPick(current); dismiss() }.fontWeight(.semibold)
                }
            }
        }
        .onAppear {
            go(initialPath.isEmpty ? resolver.getDownloadsDirectory() : initialPath)
        }
    }

    private func go(_ path: String) {
        current = path
        subfolders = resolver.listSubdirectories(path: path, includeHidden: false)
    }
}
