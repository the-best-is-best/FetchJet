import SwiftUI
import UIKit
import SharedLogic

struct AddHeader: View {
    @EnvironmentObject var store: FJStore
    let onDismiss: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Text("⚡")
                .font(.system(size: 18))
                .frame(width: 36, height: 36)
                .background(FJTheme.color.primaryContainer.opacity(0.4), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 1) {
                Text(store.strings.addDialogTitle)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                Text(store.strings.addDialogSubtitle)
                    .font(FJTheme.font.small)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            Spacer()
            AddFieldIcon(symbol: "✕", action: onDismiss)
        }
    }
}

struct AddUrlSection: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let strings = store.strings
        let url = Binding(
            get: { store.state.inputUrl },
            set: { if $0 != store.state.inputUrl { store.send(DashboardUiEventOnUrlChanged(url: $0)) } }
        )
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                AddLabel(text: strings.addLinkLabel)
                if TextFilePicker.shared.isSupported {
                    Button("📄 \(strings.addImportTxt)") {
                        TextFilePicker.shared.pick { text in
                            guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                            DispatchQueue.main.async { store.send(DashboardUiEventOnUrlChanged(url: text)) }
                        }
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
                }
                if store.host.torrentEnabled && TorrentFilePicker.shared.isSupported {
                    Button("📂 \(strings.openTorrentFile)") {
                        TorrentFilePicker.shared.pick { path in
                            guard let path, !path.isEmpty else { return }
                            DispatchQueue.main.async { store.send(DashboardUiEventOnUrlChanged(url: path)) }
                        }
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(1)
                }
            }
            .foregroundStyle(FJTheme.color.primary)
            AddField(
                icon: "🔗",
                placeholder: strings.urlPlaceholder,
                text: url,
                multiline: true,
                keyboard: .URL,
                trailing: AnyView(trailing)
            )
        }
    }

    @ViewBuilder private var trailing: some View {
        if !store.state.inputUrl.trimmingCharacters(in: .whitespaces).isEmpty {
            AddFieldIcon(symbol: "✕") { store.send(DashboardUiEventOnUrlChanged(url: "")) }
        } else {
            AddFieldChip(label: "📋 \(store.strings.addPaste)") {
                if let clip = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines), !clip.isEmpty {
                    store.send(DashboardUiEventOnUrlChanged(url: clip))
                }
            }
        }
    }
}

struct AddEngineSelector: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let strings = store.strings
        let engine = store.state.detectedEngine
        VStack(alignment: .leading, spacing: 6) {
            AddLabel(text: strings.addDownloadEngine)
            HStack(spacing: 8) {
                chip("⚡ \(strings.engineDirect)", engine == .httpDirect, .httpDirect)
                if store.host.mediaEnabled { chip("🎬 \(strings.engineMedia)", engine == .mediaStream, .mediaStream) }
                if store.host.torrentEnabled { chip("🧲 \(strings.engineTorrent)", engine == .bittorrent, .bittorrent) }
            }
        }
    }

    private func chip(_ label: String, _ selected: Bool, _ type: EngineType) -> some View {
        Button {
            store.send(DashboardUiEventOnEngineSelected(engine: type))
        } label: {
            Text(label)
                .font(.system(size: 12, weight: selected ? .bold : .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .foregroundStyle(selected ? FJTheme.color.onPrimary : FJTheme.color.onSurface)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(selected ? FJTheme.color.primary : FJTheme.color.surfaceVariant.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? FJTheme.color.primary : FJTheme.color.outlineVariant.opacity(0.6), lineWidth: 1.2))
                .shadow(color: selected ? FJTheme.color.primary.opacity(0.3) : .clear, radius: 3)
        }
        .buttonStyle(.plain)
    }
}

struct AddFileNameSection: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let s = store.state
        if !s.suggestedFileName.isEmpty || !s.fileNameOverride.isEmpty {
            let inspecting = s.isInspectingMedia || s.isInspectingTorrent
            let shown = inspecting && s.fileNameOverride.isEmpty
                ? store.strings.findingName
                : (s.fileNameOverride.isEmpty ? s.suggestedFileName : s.fileNameOverride)
            let binding = Binding(
                get: { shown },
                set: { store.send(DashboardUiEventOnFileNameChanged(name: $0)) }
            )
            VStack(alignment: .leading, spacing: 6) {
                AddLabel(text: store.strings.addFileNameLabel)
                AddField(
                    icon: inspecting ? "⏳" : "📝",
                    text: binding,
                    enabled: !inspecting,
                    trailing: (!s.fileNameOverride.isEmpty && s.fileNameOverride != s.suggestedFileName)
                        ? AnyView(AddFieldIcon(symbol: "↺") { store.send(DashboardUiEventOnFileNameChanged(name: "")) })
                        : nil
                )
            }
        }
    }
}

struct AddChecksumSection: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let value = store.state.checksumInput
        VStack(alignment: .leading, spacing: 6) {
            AddLabel(text: store.strings.checksumLabel)
            AddField(
                icon: "#️⃣",
                placeholder: store.strings.checksumHint,
                text: Binding(get: { store.state.checksumInput }, set: { store.send(DashboardUiEventOnChecksumChanged(value: $0)) }),
                trailing: value.isEmpty ? nil : AnyView(AddFieldIcon(symbol: "✕") { store.send(DashboardUiEventOnChecksumChanged(value: "")) })
            )
        }
    }
}

struct AddDestinationSection: View {
    @EnvironmentObject var store: FJStore
    @Binding var showFolderPicker: Bool
    private let resolver = PathResolver()

    private var typeFolder: String? {
        let s = store.state
        guard s.sortIntoFolders else { return nil }
        let name = s.fileNameOverride.isEmpty ? s.suggestedFileName : s.fileNameOverride
        return FileCategory.shared.folderFor(fileName: name, isMedia: s.detectedEngine == .mediaStream, isTorrent: s.detectedEngine == .bittorrent)
    }

    var body: some View {
        let strings = store.strings
        let downloads = resolver.getDownloadsDirectory()
        let docs = resolver.getDocumentsDirectory()
        let desktop = resolver.getDesktopDirectory()
        let isMobile = desktop.isEmpty || desktop.localizedCaseInsensitiveContains("storage") || desktop.localizedCaseInsensitiveContains("Documents")
        VStack(alignment: .leading, spacing: 8) {
            AddLabel(text: strings.saveDestination)
            AddField(
                icon: "📁",
                text: Binding(get: { store.state.destinationPath }, set: { store.send(DashboardUiEventOnDestinationPathChanged(path: $0)) }),
                trailing: AnyView(AddFieldIcon(symbol: "📂") { showFolderPicker = true })
            )
            HStack(spacing: 8) {
                quick("📥", strings.quickFolderDownloads) { set(downloads) }
                quick("📄", strings.quickFolderDocuments) { set(docs) }
                if isMobile {
                    quick("🎬", strings.quickFolderMedia) {
                        set(resolver.createSubdirectory(parentPath: downloads, name: "Media") ?? "\(downloads)/Media")
                    }
                } else {
                    quick("💻", strings.quickFolderDesktop) { set(desktop) }
                }
            }
        }
        .onAppear { applyTypeFolder(downloads, docs, desktop) }
        .onChange(of: typeFolder) { _, _ in applyTypeFolder(downloads, docs, desktop) }
    }

    private func set(_ path: String) {
        store.send(DashboardUiEventOnDestinationPathChanged(path: path))
    }

    private func applyTypeFolder(_ downloads: String, _ docs: String, _ desktop: String) {
        let current = store.state.destinationPath
        let base = FileCategory.shared.stripTypeFolders(path: current, keep: [downloads, docs, desktop])
        let sep = base.contains("\\") ? "\\" : "/"
        let full = typeFolder.map { base + sep + $0 } ?? base
        if full != current { set(full) }
    }

    private func quick(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(icon).font(.system(size: 12))
                Text(label)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .padding(.horizontal, 6)
            .background(FJTheme.color.surfaceVariant.opacity(0.5), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(FJTheme.color.outlineVariant.opacity(0.6), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct AddLeechToggle: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let on = store.state.disableTorrentUpload
        Button {
            store.send(DashboardUiEventOnDisableTorrentUploadToggled(disable: !on))
        } label: {
            HStack(spacing: 8) {
                AddCheckbox(checked: on)
                Text(store.strings.disableTorrentUpload)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurface)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
        }
        .buttonStyle(.plain)
    }
}

struct AddActions: View {
    @EnvironmentObject var store: FJStore
    let validity: AddValidity
    let onDismiss: () -> Void

    var body: some View {
        let s = store.state
        let strings = store.strings
        let v = validity
        VStack(spacing: 12) {
            if let err = v.activeError { AddInlineError(message: err) }
            HStack(spacing: 10) {
                Spacer(minLength: 0)
                Button(action: onDismiss) {
                    Text(strings.cancel)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant, lineWidth: 1))
                }
                .buttonStyle(.plain)
                if v.hasActiveError && !v.isInspectionComplete {
                    Button {
                        if s.detectedEngine == .bittorrent {
                            store.send(DashboardUiEventOnInspectTorrentClicked.shared)
                        } else {
                            store.send(DashboardUiEventOnInspectMediaClicked.shared)
                        }
                    } label: {
                        Text("🔄 \(strings.addRetryInspect)")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(FJTheme.color.onError)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(FJTheme.color.error, in: RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                }
                Button {
                    store.send(DashboardUiEventOnAddDownloadClicked.shared)
                } label: {
                    HStack(spacing: 8) {
                        if s.isLoading { ProgressView().controlSize(.small) }
                        Text(startLabel(s, strings, v))
                            .font(.system(size: 13, weight: .bold))
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(v.canStart ? FJTheme.color.onPrimary : FJTheme.color.onSurfaceVariant.opacity(0.6))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(v.canStart ? FJTheme.color.primary : FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .disabled(!v.canStart)
            }
        }
    }

    private func startLabel(_ s: DashboardUiState, _ strings: AppLocalization, _ v: AddValidity) -> String {
        let linkCount = v.showsLinkList ? Int32(s.linkList.filter { $0.isSelected }.count) : 0
        if s.isLoading { return strings.addStarting }
        if v.isInspecting { return strings.addInspecting }
        if v.hasActiveError { return strings.addInspectionFailed }
        if !v.isUrlValid { return strings.addEnterUrl }
        if linkCount > 0 { return s.scheduleStartMs > 0 ? strings.scheduleLinks(n: linkCount) : strings.downloadLinks(n: linkCount) }
        if !v.isInspectionComplete && s.detectedEngine == .mediaStream { return strings.inspectVideoFirst }
        if !v.isInspectionComplete && s.detectedEngine == .bittorrent { return strings.inspectTorrentFirst }
        if !v.isClipRangeValid { return strings.invalidTimeRange }
        if let pl = s.inspectedPlaylist, s.detectedEngine == .mediaStream {
            let n = Int32(pl.entries.filter { $0.isSelected }.count)
            return s.scheduleStartMs > 0 ? strings.scheduleVideos(n: n) : strings.downloadVideos(n: n)
        }
        if s.scheduleStartMs > 0 { return strings.addScheduleDownload }
        return strings.addStartDownload
    }
}
