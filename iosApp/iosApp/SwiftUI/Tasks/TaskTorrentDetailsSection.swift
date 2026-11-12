import SwiftUI
import UIKit
import SharedLogic

struct TaskTorrentDetailsSection: View {
    @EnvironmentObject var store: FJStore
    let taskId: String
    @State private var visible = false
    @State private var newTracker = ""

    private var current: TorrentDetailsUi? {
        guard let ui = store.state.torrentDetails, ui.taskId == taskId else { return nil }
        return ui
    }

    var body: some View {
        let s = store.strings
        VStack(alignment: .leading, spacing: 0) {
            Text(s.torrentDetailsTitle)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .padding(.bottom, 10)
            if let ui = current, !ui.loading {
                if ui.supported {
                    content(ui)
                } else {
                    Text(s.torrentDetailsUnsupported)
                        .font(.system(size: 12))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 14))
        .onAppear {
            visible = true
            store.send(DashboardUiEventOnTorrentDetailsOpened(taskId: taskId))
        }
        .onDisappear {
            visible = false
            if store.state.torrentDetails?.taskId == taskId {
                store.send(DashboardUiEventOnTorrentDetailsClosed.shared)
            }
        }
        .onChange(of: store.state.torrentDetails?.taskId) { _, openId in
            if visible && openId != taskId {
                store.send(DashboardUiEventOnTorrentDetailsOpened(taskId: taskId))
            }
        }
    }

    private func ratioText(_ value: Float) -> String {
        store.host.ltr(text: String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), Double(value)))
    }

    @ViewBuilder
    private func content(_ ui: TorrentDetailsUi) -> some View {
        let s = store.strings
        let host = store.host
        let details = ui.details
        let ratio = ratioText(ui.ratio) + (ui.stopRatio > 0 ? " / " + ratioText(ui.stopRatio) : "")

        HStack(alignment: .top, spacing: 12) {
            TaskDetailStat(label: s.torrentDetailsRatio, value: ratio, color: FJTheme.color.onSurface)
            TaskDetailStat(label: s.torrentDetailsUploaded, value: host.formatBytes(bytes: details.uploadedBytes),
                           color: FJTheme.color.onSurface)
        }
        HStack(alignment: .top, spacing: 12) {
            TaskDetailStat(label: s.torrentDetailsPieces,
                           value: host.ltr(text: "\(details.piecesDone) / \(details.pieceCount)"),
                           color: FJTheme.color.onSurface)
            TaskDetailStat(label: s.torrentDetailsPieceSize,
                           value: details.pieceSize > 0 ? host.formatBytes(bytes: details.pieceSize) : "—",
                           color: FJTheme.color.onSurface)
        }
        .padding(.top, 10)

        Group {
            divider
            caption("\(s.torrentDetailsTrackers) (\(details.trackers.count))")
            if details.trackers.isEmpty {
                emptyLine(s.torrentDetailsNoTrackers)
            } else {
                rows(count: details.trackers.count, scrollAbove: 6, maxHeight: 180) {
                    ForEach(Array(details.trackers.enumerated()), id: \.offset) { _, tracker in
                        trackerRow(tracker)
                    }
                }
            }
            addTrackerRow(ui)
        }

        Group {
            divider
            caption("\(s.torrentDetailsPeers) (\(details.peers.count))")
            if details.peers.isEmpty {
                emptyLine(s.torrentDetailsNoPeers)
            } else {
                rows(count: min(details.peers.count, 30), scrollAbove: 6, maxHeight: 220) {
                    ForEach(Array(details.peers.prefix(30).enumerated()), id: \.offset) { _, peer in
                        peerRow(peer)
                    }
                }
            }
            divider
        }

        Toggle(isOn: Binding(
            get: { details.firstLastPiecesFirst },
            set: { store.send(DashboardUiEventOnTorrentFirstLastChanged(taskId: taskId, on: $0)) }
        )) {
            Text(s.torrentDetailsFirstLast)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(FJTheme.color.onSurface)
        }
        .tint(FJTheme.color.primary)

        HStack(spacing: 8) {
            TaskDetailButton(text: s.torrentDetailsRecheck, primary: false) {
                store.send(DashboardUiEventOnTorrentRecheck(taskId: taskId))
            }
            TaskDetailButton(text: s.torrentDetailsCopyMagnet, primary: false) {
                UIPasteboard.general.string = details.magnetLink
                store.send(DashboardUiEventOnTorrentCopyMagnet(taskId: taskId))
            }
            .disabled(details.magnetLink.trimmingCharacters(in: .whitespaces).isEmpty)
            .opacity(details.magnetLink.trimmingCharacters(in: .whitespaces).isEmpty ? 0.45 : 1)
        }
        .padding(.top, 10)
    }

    @ViewBuilder
    private func rows<Rows: View>(count: Int, scrollAbove: Int, maxHeight: CGFloat,
                                  @ViewBuilder _ content: () -> Rows) -> some View {
        let list = VStack(alignment: .leading, spacing: 6) { content() }
        if count > scrollAbove {
            ScrollView { list }
                .frame(height: maxHeight)
                .padding(.top, 6)
        } else {
            list.padding(.top, 6)
        }
    }

    private var divider: some View {
        Rectangle()
            .fill(FJTheme.color.outlineVariant.opacity(0.3))
            .frame(height: 1)
            .padding(.vertical, 12)
    }

    private func caption(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(FJTheme.color.onSurfaceVariant)
    }

    private func emptyLine(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12))
            .foregroundStyle(FJTheme.color.onSurfaceVariant.opacity(0.7))
            .padding(.top, 6)
    }

    private func trackerHost(_ url: String) -> String {
        var rest = url
        if let r = rest.range(of: "://") { rest = String(rest[r.upperBound...]) }
        if let slash = rest.firstIndex(of: "/") { rest = String(rest[..<slash]) }
        if let at = rest.lastIndex(of: "@") { rest = String(rest[rest.index(after: at)...]) }
        return rest.isEmpty ? url : rest
    }

    private func trackerRow(_ tracker: TorrentTrackerInfo) -> some View {
        let s = store.strings
        let (label, color): (String, Color) = {
            switch tracker.state {
            case "working": return (s.torrentDetailsStateWorking, FJTheme.color.statusCompleted)
            case "updating": return (s.torrentDetailsStateUpdating, FJTheme.color.primary)
            case "waiting": return (s.torrentDetailsStateWaiting, FJTheme.color.statusPaused)
            case "error": return (s.torrentDetailsStateError, FJTheme.color.error)
            case "disabled": return (s.torrentDetailsStateDisabled, FJTheme.color.onSurfaceVariant)
            default: return (tracker.state, FJTheme.color.onSurfaceVariant)
            }
        }()
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 8) {
                Text(store.host.ltr(text: trackerHost(tracker.url)))
                    .font(.system(size: 12))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)
                TaskDetailChip(text: label, color: color)
            }
            if tracker.state == "error" && !tracker.message.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(tracker.message)
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.error)
                    .lineLimit(2)
            }
        }
    }

    private func peerRow(_ peer: TorrentPeerInfo) -> some View {
        let host = store.host
        let unit = store.state.speedDisplayUnit
        let percent = Int(min(max(peer.progress, 0), 1) * 100)
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 8) {
                Text(host.ltr(text: peer.address))
                    .font(.system(size: 12))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(host.ltr(text: "\(percent)%"))
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(peer.isSeed ? FJTheme.color.statusCompleted : FJTheme.color.onSurfaceVariant)
            }
            HStack(spacing: 8) {
                Text(peer.client)
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("↓ \(host.formatSpeed(bytesPerSec: peer.downloadRate, unit: unit))  ↑ \(host.formatSpeed(bytesPerSec: peer.uploadRate, unit: unit))")
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
            }
        }
    }

    private func addTrackerRow(_ ui: TorrentDetailsUi) -> some View {
        let s = store.strings
        let error: String? = {
            switch ui.trackerError {
            case "": return nil
            case "invalid": return s.torrentDetailsTrackerInvalid
            case "refused": return s.torrentDetailsTrackerRefused
            default: return ui.trackerError
            }
        }()
        let blank = newTracker.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                TextField(s.torrentDetailsTrackerHint, text: $newTracker)
                    .font(.system(size: 13))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .onSubmit { submitTracker() }
                    .padding(.horizontal, 10)
                    .frame(height: 42)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(error == nil ? FJTheme.color.outlineVariant.opacity(0.6) : FJTheme.color.error, lineWidth: 1)
                    )
                Button(action: submitTracker) {
                    Text(s.torrentDetailsAddTracker)
                        .font(.system(size: 13, weight: .bold))
                        .lineLimit(1)
                        .foregroundStyle(FJTheme.color.onPrimary)
                        .padding(.horizontal, 14)
                        .frame(height: 42)
                        .background(FJTheme.color.primary, in: RoundedRectangle(cornerRadius: 10))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(blank)
                .opacity(blank ? 0.45 : 1)
            }
            if let error {
                Text(error)
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.error)
            }
        }
        .padding(.top, 10)
    }

    private func submitTracker() {
        let url = newTracker.trimmingCharacters(in: .whitespacesAndNewlines)
        if url.isEmpty { return }
        store.send(DashboardUiEventOnTorrentAddTracker(taskId: taskId, url: url))
        newTracker = ""
    }
}
