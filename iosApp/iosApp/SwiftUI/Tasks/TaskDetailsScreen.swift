import SwiftUI
import UIKit
import SharedLogic

struct TaskDetailsScreen: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    let taskId: String

    var body: some View {
        Group {
            if let task = store.task(taskId) {
                ScrollView {
                    TaskDetailsContent(task: task)
                        .padding(.horizontal, 16)
                        .padding(.top, 4)
                        .padding(.bottom, 24)
                }
            } else {
                Color.clear.onAppear {
                    if store.selectedTaskId == taskId { store.selectedTaskId = nil }
                }
            }
        }
        .background(FJTheme.color.background.ignoresSafeArea())
        .navigationTitle(store.strings.detailsHeaderDownload)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .toolbarBackground(FJTheme.color.background, for: .navigationBar)
    }
}

private struct TaskDetailsContent: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask
    @State private var showFiles = false
    @State private var confirmRestartPause = false

    private var fullPath: String {
        if task.fileName.trimmingCharacters(in: .whitespaces).isEmpty {
            return store.host.decodeUnicodeText(input: task.destinationPath).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return store.host.taskFilePath(task: task)
    }

    var body: some View {
        let s = store.strings
        let isCompleted = task.status == .completed
        VStack(alignment: .leading, spacing: 0) {
            Text(TaskUi.displayName(task, store.host))
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(FJTheme.color.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
                .textSelection(.enabled)

            chips(isCompleted: isCompleted)
                .padding(.top, 10)

            if !task.torrentFiles.isEmpty {
                torrentFiles.padding(.top, 6)
            }

            summaryCard.padding(.top, 8)

            if task.chunks.count > 1 && !isCompleted {
                TaskPiecesBar(task: task).padding(.top, 12)
            }

            if task.status == .error, let err = task.errorMessage, !err.isEmpty {
                Label(store.host.localizeStep(text: err), systemImage: "exclamationmark.triangle")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(FJTheme.color.error)
                    .padding(.top, 6)
            }

            Text(s.detailsSavedTo)
                .font(.system(size: 11))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .padding(.top, 14)
            Text(fullPath)
                .font(.system(size: 12))
                .foregroundStyle(FJTheme.color.onSurface.opacity(0.75))
                .lineLimit(3)
                .truncationMode(.middle)
                .padding(.top, 3)

            links

            if let expected = task.expectedChecksum, !expected.trimmingCharacters(in: .whitespaces).isEmpty {
                checksumLine.padding(.top, 10)
            }

            actions(isCompleted: isCompleted).padding(.top, 18)
        }
        .alert(s.homeCantResumeTitle, isPresented: $confirmRestartPause) {
            Button(s.homePauseAnyway, role: .destructive) {
                store.send(DashboardUiEventOnPauseClicked(taskId: task.id))
            }
            Button(s.homeKeepDownloading, role: .cancel) {}
        } message: {
            Text(task.engineType == .mediaStream ? s.homeCantResumeClipText : s.homeCantResumeServerText)
        }
    }

    private func chips(isCompleted: Bool) -> some View {
        let s = store.strings
        return TaskFlowLayout(spacing: 6) {
            switch task.engineType {
            case .mediaStream: TaskDetailChip(text: s.detailsChipMedia, color: FJTheme.color.engineMedia)
            case .bittorrent: TaskDetailChip(text: s.detailsChipTorrent, color: FJTheme.color.engineTorrent)
            default: TaskDetailChip(text: s.detailsChipFile, color: FJTheme.color.primary)
            }
            if !isCompleted, let resumable = task.canResume() {
                if resumable.boolValue {
                    TaskDetailChip(text: s.detailsChipResumable, color: FJTheme.color.statusCompleted)
                } else {
                    TaskDetailChip(text: s.detailsChipNoResume, color: FJTheme.color.statusPaused)
                }
            }
            if !(task.clipStart ?? "").isEmpty || !(task.clipEnd ?? "").isEmpty {
                TaskDetailChip(
                    text: s.detailsClipChip(start: task.clipStart ?? "00:00", end: task.clipEnd,
                                            cutAfterDownload: task.clipByCutting && !isCompleted),
                    color: FJTheme.color.onSurfaceVariant)
            }
        }
    }

    private var torrentFiles: some View {
        let s = store.strings
        let files = task.torrentFiles
        return VStack(alignment: .leading, spacing: 4) {
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { showFiles.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "folder")
                    Text(s.detailsTorrentFiles(selected: Int32(files.filter { $0.isSelected }.count), total: Int32(files.count)))
                    Image(systemName: showFiles ? "chevron.up" : "chevron.down").font(.system(size: 10, weight: .bold))
                }
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(FJTheme.color.primary)
                .padding(.vertical, 2)
            }
            .buttonStyle(.plain)
            if showFiles {
                ScrollView {
                    LazyVStack(spacing: 2) {
                        ForEach(files, id: \.index) { file in
                            torrentFileRow(file)
                        }
                    }
                    .padding(4)
                }
                .frame(maxHeight: 180)
                .background(FJTheme.color.surfaceVariant.opacity(0.4), in: RoundedRectangle(cornerRadius: 6))
            }
        }
    }

    private func torrentFileRow(_ file: TorrentFileEntry) -> some View {
        let s = store.strings
        let (label, color): (String, Color) = {
            switch file.priority {
            case 1: return (s.detailsPriorityHigh, FJTheme.color.primary)
            case -1: return (s.detailsPriorityLow, FJTheme.color.error)
            default: return (s.detailsPriorityNormal, FJTheme.color.onSurfaceVariant)
            }
        }()
        let host = store.host
        let progressText = !file.isCompleted && file.downloadedBytes > 0
            ? "\(host.formatBytes(bytes: file.downloadedBytes)) / \(host.formatBytes(bytes: file.size))"
            : host.formatBytes(bytes: file.size)
        return HStack(spacing: 6) {
            Button {
                store.send(DashboardUiEventOnTaskTorrentFileSelectionToggled(taskId: task.id, fileIndex: file.index, isSelected: !file.isSelected))
            } label: {
                Image(systemName: file.isSelected ? "checkmark.square.fill" : "square")
                    .font(.system(size: 18))
                    .foregroundStyle(file.isSelected ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
            }
            .buttonStyle(.plain)
            Image(systemName: file.isCompleted ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 11))
                .foregroundStyle(file.isCompleted ? FJTheme.color.statusCompleted : FJTheme.color.onSurfaceVariant)
            Text(file.path)
                .font(.system(size: 12))
                .lineLimit(1)
                .truncationMode(.middle)
                .foregroundStyle(file.isSelected ? FJTheme.color.onSurface : FJTheme.color.onSurfaceVariant.opacity(0.5))
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                let next: Int32 = file.priority == 0 ? 1 : (file.priority == 1 ? -1 : 0)
                store.send(DashboardUiEventOnTaskTorrentFilePriorityChanged(taskId: task.id, fileIndex: file.index, priority: next))
            } label: {
                Text(label)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(color)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 4))
            }
            .buttonStyle(.plain)
            Text(progressText)
                .font(.system(size: 10))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 2)
    }

    private var statusWord: String {
        let s = store.strings
        switch task.status {
        case .completed: return s.detailsStatusCompleted
        case .paused: return s.detailsStatusPaused
        case .error: return s.detailsStatusFailed
        default: return task.waitingInQueue ? s.detailsStatusWaiting : ""
        }
    }

    private var bytesText: String {
        let s = store.strings, host = store.host
        let estimate = task.status == .downloading && task.approximateTotalBytes > 0 && task.approximateTotalBytes == task.totalBytes
        let sectionClip = task.status == .downloading && !task.clipByCutting &&
            (!(task.clipStart ?? "").isEmpty || !(task.clipEnd ?? "").isEmpty)
        if sectionClip { return host.formatBytes(bytes: task.downloadedBytes) }
        let total = task.totalBytes > 0 ? (estimate ? "~" : "") + host.formatBytes(bytes: task.totalBytes) : s.detailsUnknownSize
        return "\(host.formatBytes(bytes: task.downloadedBytes)) / \(total)"
    }

    private var speedText: String {
        let s = store.strings, host = store.host
        let unit = store.state.speedDisplayUnit
        let dl = host.formatSpeed(bytesPerSec: task.downloadSpeedBytesPerSec, unit: unit)
        if task.status == .downloading {
            if let post = task.postProcessLabel { return host.localizeStep(text: post) + "..." }
            if task.currentStepText?.range(of: "Merging", options: .caseInsensitive) != nil { return s.detailsMergingAudioVideo }
            if task.engineType == .bittorrent && task.disableUpload { return s.detailsTorrentSpeedUploadDisabled(download: dl) }
            if task.engineType == .bittorrent {
                return s.detailsTorrentSpeed(download: dl, upload: host.formatSpeed(bytesPerSec: task.uploadSpeedBytesPerSec, unit: unit))
            }
            return dl
        }
        if task.status == .connecting { return s.detailsConnecting }
        if task.waitingInQueue {
            let now = LocalClockKt.currentEpochMs()
            if task.scheduledAtMs > now {
                return "🕑 " + s.detailsStartsAt(time: LocalClockKt.formatLocalSchedule(epochMs: task.scheduledAtMs, nowMs: now))
            }
            if task.queuePosition > 0 { return "⏳ " + s.detailsWaitingInQueuePosition(position: task.queuePosition) }
            return "⏳ " + s.detailsWaitingInQueue
        }
        switch task.status {
        case .completed: return s.detailsStatusCompleted
        case .paused: return s.detailsStatusPaused
        case .error: return s.detailsStatusFailed
        case .queued: return s.detailsStatusQueued
        default: return s.detailsStatusNotStarted
        }
    }

    private var speedColor: Color {
        switch task.status {
        case .downloading: return FJTheme.color.primary
        case .completed: return FJTheme.color.statusCompleted
        case .paused: return FJTheme.color.statusPaused
        case .error: return FJTheme.color.error
        default: return FJTheme.color.onSurfaceVariant
        }
    }

    private var summaryCard: some View {
        let s = store.strings
        let running = TaskUi.isRunning(task)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom) {
                Text("\(TaskUi.percent(task))%")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                Spacer()
                Text(statusWord)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .padding(.bottom, 6)
            }
            if let step = task.currentStepText, !step.isEmpty, running {
                Label(store.host.localizeStep(text: step), systemImage: "bolt.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(FJTheme.color.onPrimaryContainer)
                    .lineLimit(1)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(FJTheme.color.primaryContainer.opacity(0.7), in: RoundedRectangle(cornerRadius: 6))
                    .padding(.top, 8)
                    .padding(.bottom, 4)
            }
            TaskThinBar(fraction: task.progress, color: FJTheme.color.primary, height: 8)
                .padding(.top, 4)
            if let post = task.postProcessLabel, running {
                TaskPostProcessLine(label: post, progress: task.postProcessProgress, startedAtMs: task.postProcessStartedAtMs)
                    .padding(.top, 6)
            }
            HStack(alignment: .top, spacing: 12) {
                TaskDetailStat(label: s.detailsDownloaded, value: bytesText, color: FJTheme.color.onSurface)
                TaskDetailStat(label: task.status == .downloading ? s.detailsSpeed : s.detailsState, value: speedText, color: speedColor)
            }
            .padding(.top, 8)
            HStack(alignment: .top, spacing: 12) {
                TaskDetailStat(label: s.detailsTimeLeft, value: task.etaSeconds().map { TaskUi.eta($0.int64Value) } ?? "—",
                               color: FJTheme.color.onSurface)
                TaskDetailStat(label: s.detailsAdded, value: addedText(task.createdAtEpochMs), color: FJTheme.color.onSurface)
            }
            .padding(.top, 10)
        }
        .padding(14)
        .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 14))
    }

    private func addedText(_ epochMs: Int64) -> String {
        if epochMs <= 0 { return "—" }
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
        let cal = Calendar.current
        let c = cal.dateComponents([.hour, .minute], from: date)
        let h24 = c.hour ?? 0
        let h12 = h24 % 12 == 0 ? 12 : h24 % 12
        let clock = "\(h12):" + String(format: "%02d", c.minute ?? 0) + (h24 < 12 ? " AM" : " PM")
        if cal.isDateInToday(date) { return store.strings.detailsAddedToday(clock: clock) }
        if cal.isDateInYesterday(date) { return store.strings.detailsAddedYesterday(clock: clock) }
        let d = cal.dateComponents([.day, .month, .year], from: date)
        return String(format: "%02d/%02d/%d", d.day ?? 0, d.month ?? 0, d.year ?? 0)
    }

    private var links: some View {
        let s = store.strings
        let page = task.requestHeaders["Referer"] ?? task.originalUrl ?? ""
        var items: [(String, String)] = []
        for (label, link) in [(s.detailsSource, task.url), (s.detailsPage, page)] where !link.isEmpty {
            if !items.contains(where: { $0.1 == link }) { items.append((label, link)) }
        }
        return ForEach(items, id: \.1) { label, link in
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(label)
                        .font(.system(size: 11))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    Spacer()
                    Button {
                        UIPasteboard.general.string = link
                        store.showToast(s.copied)
                    } label: {
                        Text(s.copy)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(FJTheme.color.primary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                    }
                    .buttonStyle(.plain)
                }
                Text(link)
                    .font(.system(size: 12))
                    .foregroundStyle(FJTheme.color.onSurface.opacity(0.75))
                    .lineLimit(4)
                    .textSelection(.enabled)
            }
            .padding(.top, 10)
        }
    }

    private var checksumLine: some View {
        let s = store.strings
        let (icon, text, color): (String, String, Color) = {
            switch task.checksumVerified?.boolValue {
            case .some(true): return ("checkmark.seal.fill", s.checksumVerified, FJTheme.hex(0x2E7D32))
            case .some(false): return ("xmark.octagon.fill", s.checksumMismatch, FJTheme.color.error)
            case .none: return ("hourglass", s.checksumChecking, FJTheme.color.onSurfaceVariant)
            }
        }()
        return Label(text, systemImage: icon)
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(color)
    }

    @ViewBuilder
    private func actions(isCompleted: Bool) -> some View {
        let s = store.strings
        let running = TaskUi.isRunning(task)
        let resolver = PathResolver()
        let canFolder = resolver.isOpenFolderSupported()
        let path = fullPath
        let openFolder = { _ = resolver.openFileLocation(filePath: path) }
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                if isCompleted {
                    TaskDetailButton(text: s.detailsOpenFile, primary: true) { store.openFile(task) }
                    if canFolder { TaskDetailButton(text: s.detailsOpenFolder, primary: false, action: openFolder) }
                } else if running {
                    TaskDetailButton(text: s.pause, primary: true) { pauseClicked() }
                    if canFolder { TaskDetailButton(text: s.detailsOpenFolder, primary: false, action: openFolder) }
                } else if task.waitingInQueue {
                    TaskDetailButton(text: s.detailsStartNow, primary: true) {
                        store.send(DashboardUiEventOnResumeClicked(taskId: task.id))
                    }
                    TaskDetailButton(text: s.pause, primary: false) {
                        store.send(DashboardUiEventOnPauseClicked(taskId: task.id))
                    }
                } else {
                    TaskDetailButton(text: task.status == .error ? s.retry : s.resume, primary: true) {
                        store.send(DashboardUiEventOnResumeClicked(taskId: task.id))
                    }
                    if canFolder { TaskDetailButton(text: s.detailsOpenFolder, primary: false, action: openFolder) }
                }
            }
            if AudioCompress.shared.canCompress(task: task) {
                AudioCompressButton(task: task)
            }
            if task.canSendToDevice() {
                TaskDetailButton(text: s.sysDeviceSendTitle, primary: false) {
                    store.send(DashboardUiEventOnShareTaskClicked(taskId: task.id))
                }
            }
            Button {
                store.send(DashboardUiEventOnCancelClicked(taskId: task.id))
            } label: {
                Text(isCompleted ? s.detailsRemoveFromList : s.detailsCancelDownload)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(FJTheme.color.error)
                    .frame(maxWidth: .infinity)
                    .frame(height: 42)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.error.opacity(0.35), lineWidth: 1))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func pauseClicked() {
        if task.canResume()?.boolValue == false {
            confirmRestartPause = true
        } else {
            store.send(DashboardUiEventOnPauseClicked(taskId: task.id))
        }
    }
}

struct TaskDetailChip: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: 6))
    }
}

struct TaskDetailStat: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(color)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct TaskDetailButton: View {
    let text: String
    let primary: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 13, weight: primary ? .bold : .semibold))
                .lineLimit(1)
                .foregroundStyle(primary ? FJTheme.color.onPrimary : FJTheme.color.onSurface)
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(primary ? FJTheme.color.primary : Color.clear, in: RoundedRectangle(cornerRadius: 10))
                .overlay {
                    if !primary {
                        RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant.opacity(0.6), lineWidth: 1)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct TaskPostProcessLine: View {
    @EnvironmentObject var store: FJStore
    let label: String
    let progress: Float
    let startedAtMs: Int64
    @State private var fallbackStart = Date()

    var body: some View {
        let start = startedAtMs > 0 ? Date(timeIntervalSince1970: TimeInterval(startedAtMs) / 1000) : fallbackStart
        let icon: String = {
            if label.lowercased().hasPrefix("merging") { return "link" }
            if label.lowercased().hasPrefix("saving") { return "folder" }
            if label.range(of: "MP3", options: .caseInsensitive) != nil { return "music.note" }
            return "gearshape"
        }()
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Label(store.host.localizeStep(text: label), systemImage: icon)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(FJTheme.color.tertiary)
                    .lineLimit(1)
                Spacer()
                TimelineView(.periodic(from: .now, by: 1)) { ctx in
                    let sec = max(0, Int(ctx.date.timeIntervalSince(start)))
                    let m = sec / 60, s = sec % 60
                    let elapsed = m >= 60 ? String(format: "%d:%02d:%02d", m / 60, m % 60, s) : String(format: "%d:%02d", m, s)
                    Text((progress >= 0 ? "\(Int(progress * 100))% · " : "") + elapsed)
                        .font(.system(size: 11))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
            }
            TaskThinBar(fraction: progress, color: FJTheme.color.tertiary, height: 5, indeterminate: progress < 0)
        }
    }
}

struct TaskFlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, widest: CGFloat = 0
        for v in subviews {
            let sz = v.sizeThatFits(.unspecified)
            if x > 0 && x + sz.width > maxW { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: proposal.width ?? widest, height: subviews.isEmpty ? 0 : y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for v in subviews {
            let sz = v.sizeThatFits(.unspecified)
            if x > bounds.minX && x + sz.width > bounds.maxX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}
