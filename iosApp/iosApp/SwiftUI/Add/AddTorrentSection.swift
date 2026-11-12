import SwiftUI
import SharedLogic

struct AddTorrentSection: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let s = store.state
        let strings = store.strings
        if s.isInspectingTorrent {
            AddLoadingBanner(title: strings.inspectingTorrent, subtitle: strings.inspectingTorrentHint)
        } else if let err = s.inspectError {
            AddFailureCard(title: strings.torrentInspectionFailed, message: err, retryLabel: "🔄 \(strings.addRetryInspection)") {
                store.send(DashboardUiEventOnInspectTorrentClicked.shared)
            }
        } else if s.inspectedTorrentFiles.isEmpty {
            AddOutlineButton(label: "🧲 \(strings.inspectTorrentFiles)", enabled: !s.inputUrl.isEmpty) {
                store.send(DashboardUiEventOnInspectTorrentClicked.shared)
            }
        } else {
            AddTorrentFilesCard(
                name: s.inspectedTorrentName,
                files: s.inspectedTorrentFiles,
                onToggle: { i, on in store.send(DashboardUiEventOnTorrentFileSelectionToggled(fileIndex: i, isSelected: on)) },
                onPriority: { i, p in store.send(DashboardUiEventOnTorrentFilePriorityChanged(fileIndex: i, priority: p)) },
                onSelectAll: { store.send(DashboardUiEventOnSelectAllTorrentFilesToggled(selectAll: $0)) }
            )
        }
    }
}

struct AddTorrentFilesCard: View {
    @EnvironmentObject var store: FJStore
    let name: String?
    let files: [TorrentFileEntry]
    let onToggle: (Int32, Bool) -> Void
    let onPriority: (Int32, Int32) -> Void
    let onSelectAll: (Bool) -> Void

    var body: some View {
        let strings = store.strings
        let all = files.allSatisfy { $0.isSelected }
        AddCard {
            HStack {
                Text(name ?? strings.torrentFiles)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(1)
                Spacer()
                Button(all ? strings.addDeselectAll : strings.addSelectAll) { onSelectAll(!all) }
                    .font(.system(size: 11, weight: .semibold))
            }
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(files, id: \.index) { file in row(file, strings) }
                }
                .padding(4)
            }
            .frame(height: min(CGFloat(files.count) * 46 + 8, 240))
            .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 8))
        }
    }

    private func row(_ file: TorrentFileEntry, _ strings: AppLocalization) -> some View {
        let label: String
        let color: Color
        switch file.priority {
        case 1: label = strings.torrentPriorityHigh; color = FJTheme.hex(0xEF4444)
        case -1: label = strings.torrentPriorityLow; color = FJTheme.hex(0x3B82F6)
        default: label = strings.torrentPriorityNormal; color = FJTheme.color.onSurfaceVariant
        }
        return HStack(spacing: 8) {
            Button { onToggle(file.index, !file.isSelected) } label: {
                HStack(spacing: 8) {
                    AddCheckbox(checked: file.isSelected)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(file.path)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(FJTheme.color.onSurface)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        Text(store.host.formatBytes(bytes: file.size))
                            .font(.system(size: 10))
                            .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button {
                let next: Int32 = file.priority == 0 ? 1 : (file.priority == 1 ? -1 : 0)
                onPriority(file.index, next)
            } label: {
                Text(label)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(color)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(color.opacity(0.4), lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .background(file.isSelected ? FJTheme.color.primaryContainer.opacity(0.4) : .clear, in: RoundedRectangle(cornerRadius: 6))
    }
}

struct AddLinkListSection: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let links = store.state.linkList
        let strings = store.strings
        let selected = links.filter { $0.isSelected }.count
        let all = selected == links.count
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(strings.selectedOfLinks(selected: Int32(selected), total: Int32(links.count)))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                Spacer()
                Button(all ? strings.selectNone : strings.selectAll) { store.send(DashboardUiEventOnLinkListSelectAll(selected: !all)) }
                    .font(.system(size: 12, weight: .semibold))
            }
            AddLabel(text: strings.playlistFolder)
            AddField(icon: "📁", text: Binding(
                get: { store.state.linkListFolder },
                set: { store.send(DashboardUiEventOnLinkListFolderChanged(name: $0)) }
            ))
            Text(strings.playlistFolderHint)
                .font(.system(size: 10))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            VStack(spacing: 0) {
                ForEach(Array(links.enumerated()), id: \.offset) { i, link in
                    if i > 0 { Divider().overlay(FJTheme.color.outlineVariant.opacity(0.35)) }
                    linkRow(link, Int32(i))
                    if link.engine == .mediaStream && link.isSelected { qualityLine(link, Int32(i), strings) }
                    if link.engine == .bittorrent && link.isSelected { torrentPart(link, Int32(i), strings) }
                }
            }
            .background(FJTheme.color.surfaceVariant.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant.opacity(0.6), lineWidth: 1))
        }
    }

    private func linkRow(_ link: LinkListEntry, _ index: Int32) -> some View {
        let strings = store.strings
        let (tag, color): (String, Color) = {
            switch link.engine {
            case .mediaStream:
                return MediaQualityEntryKt.isAllAudio(link.mediaQualities)
                    ? (strings.linkTagAudio, FJTheme.hex(0xFBBF24)) : (strings.linkTagVideo, FJTheme.hex(0xE879F9))
            case .bittorrent: return (strings.linkTagTorrent, FJTheme.hex(0x34D399))
            default: return (strings.linkTagFile, FJTheme.hex(0x38BDF8))
            }
        }()
        return Button {
            store.send(DashboardUiEventOnLinkListEntryToggled(index: index, selected: !link.isSelected))
        } label: {
            HStack(spacing: 8) {
                AddCheckbox(checked: link.isSelected)
                VStack(alignment: .leading, spacing: 1) {
                    Text(link.mediaTitle.map { store.host.decodeUnicodeText(input: $0) } ?? AddLinkText.title(link.url, strings, store))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(1)
                    Text(AddLinkText.host(link.url))
                        .font(.system(size: 10))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Text(tag)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(color)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
            }
            .padding(.leading, 10)
            .padding(.trailing, 12)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func qualityLine(_ link: LinkListEntry, _ index: Int32, _ strings: AppLocalization) -> some View {
        Group {
            if link.isInspectingMedia {
                HStack(spacing: 6) {
                    ProgressView().controlSize(.mini)
                    Text(strings.inspectingVideoQualities)
                        .font(.system(size: 11))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
            } else if !link.mediaQualities.isEmpty {
                let current = link.mediaQualities.first { $0.isSelected } ?? link.mediaQualities[0]
                Menu {
                    ForEach(link.mediaQualities, id: \.index) { q in
                        Button(qualityText(q)) { store.send(DashboardUiEventOnLinkListQualitySelected(linkIndex: index, qualityIndex: q.index)) }
                    }
                } label: {
                    Text(qualityText(current) + "  ▾")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(FJTheme.color.primary.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(FJTheme.color.primary.opacity(0.5), lineWidth: 1))
                }
            } else if link.mediaInspectFailed {
                HStack(spacing: 6) {
                    Text(strings.sysFailedToInspectMediaQualities)
                        .font(.system(size: 11))
                        .foregroundStyle(FJTheme.color.error)
                    Button(strings.retry) { store.send(DashboardUiEventOnLinkListRetryInspect(linkIndex: index)) }
                        .font(.system(size: 11, weight: .bold))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, 40)
        .padding(.trailing, 12)
        .padding(.bottom, 8)
    }

    @ViewBuilder
    private func torrentPart(_ link: LinkListEntry, _ index: Int32, _ strings: AppLocalization) -> some View {
        Group {
            if link.isInspecting {
                HStack(spacing: 6) {
                    ProgressView().controlSize(.mini)
                    Text(strings.readingTorrentFiles)
                        .font(.system(size: 11))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
            } else if !link.torrentFiles.isEmpty {
                AddTorrentFilesCard(
                    name: link.torrentName,
                    files: link.torrentFiles,
                    onToggle: { f, on in store.send(DashboardUiEventOnLinkListTorrentFileToggled(linkIndex: index, fileIndex: f, selected: on)) },
                    onPriority: { f, p in store.send(DashboardUiEventOnLinkListTorrentFilePriority(linkIndex: index, fileIndex: f, priority: p)) },
                    onSelectAll: { store.send(DashboardUiEventOnLinkListTorrentSelectAll(linkIndex: index, selected: $0)) }
                )
            } else if let err = link.inspectError {
                Text(strings.couldNotReadTorrentFiles(error: err))
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.error)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.bottom, 10)
    }

    private func qualityText(_ q: MediaQualityEntry) -> String {
        q.qualityLabel + (q.size > 0 ? " · " + store.host.formatBytes(bytes: q.size) : "")
    }
}

enum AddLinkText {
    @MainActor
    static func title(_ url: String, _ strings: AppLocalization, _ store: FJStore) -> String {
        if url.lowercased().hasPrefix("magnet:") {
            let dn = url.components(separatedBy: "dn=").dropFirst().first?.components(separatedBy: "&").first ?? ""
            let name = (dn.replacingOccurrences(of: "+", with: " ").removingPercentEncoding ?? dn)
            return name.isEmpty ? strings.linkListMagnet : store.host.decodeUnicodeText(input: name)
        }
        let afterScheme = url.components(separatedBy: "#")[0].components(separatedBy: "?")[0].components(separatedBy: "://").last ?? ""
        var path = ""
        if let slash = afterScheme.firstIndex(of: "/") { path = String(afterScheme[afterScheme.index(after: slash)...]) }
        while path.hasSuffix("/") { path.removeLast() }
        let lastRaw = path.components(separatedBy: "/").last ?? ""
        let last = lastRaw.removingPercentEncoding ?? lastRaw
        let h = host(url)
        if h.contains("youtube.com") || h.contains("youtu.be") {
            let id = url.components(separatedBy: "v=").dropFirst().first?.components(separatedBy: "&").first ?? ""
            return strings.linkListYouTubeVideo(id: id.isEmpty ? last : id)
        }
        return last.isEmpty ? h : store.host.decodeUnicodeText(input: last)
    }

    static func host(_ url: String) -> String {
        if url.lowercased().hasPrefix("magnet:") { return "BitTorrent" }
        let after = url.components(separatedBy: "://").dropFirst().first ?? url
        var h = after.components(separatedBy: "/")[0].components(separatedBy: "?")[0]
        h = h.components(separatedBy: "@").last ?? h
        if h.hasPrefix("www.") { h.removeFirst(4) }
        return h
    }
}
