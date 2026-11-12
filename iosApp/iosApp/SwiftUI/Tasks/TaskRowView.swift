import SwiftUI
import SharedLogic

enum TaskRowAction { case pause, resume, startNow, open, none }

@MainActor
enum TaskUi {
    static let audioExt: Set<String> = ["mp3", "m4a", "aac", "flac", "wav", "ogg", "opus"]

    static func displayName(_ task: DownloadTask, _ host: IosAppHost) -> String {
        var raw = task.fileName
        if raw.trimmingCharacters(in: .whitespaces).isEmpty {
            let noQuery = task.url.components(separatedBy: "?").first ?? task.url
            raw = noQuery.components(separatedBy: "/").last ?? noQuery
        }
        return host.decodeUnicodeText(input: raw).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func kindTag(_ task: DownloadTask) -> String {
        let name = task.fileName
        let ext: String = name.contains(".") ? (name.components(separatedBy: ".").last ?? "").lowercased() : ""
        if task.engineType == .bittorrent { return "MAG" }
        if task.engineType == .mediaStream { return audioExt.contains(ext) ? "AUD" : "VID" }
        if audioExt.contains(ext) { return "AUD" }
        if ["mp4", "mkv", "webm", "mov", "avi"].contains(ext) { return "VID" }
        if (2...4).contains(ext.count) && ext.allSatisfy({ $0.isLetter || $0.isNumber }) { return ext.uppercased() }
        return "FILE"
    }

    static func kindColor(_ tag: String) -> Color {
        switch tag {
        case "VID": return FJTheme.hex(0xC4B5FD)
        case "AUD": return FJTheme.hex(0x6EE7B7)
        case "MAG": return FJTheme.hex(0xF9A8D4)
        case "ZIP", "RAR", "7Z": return FJTheme.hex(0xFCD34D)
        case "ISO", "IMG", "DMG": return FJTheme.hex(0x93C5FD)
        case "EXE", "MSI", "APK": return FJTheme.hex(0xFDBA74)
        default: return FJTheme.hex(0xB7C0D8)
        }
    }

    static func eta(_ seconds: Int64) -> String {
        let s = max(0, seconds)
        if s < 60 { return "\(s)s" }
        if s < 3600 { return "\(s / 60)m " + String(format: "%02lld", s % 60) + "s" }
        if s < 100 * 3600 { return "\(s / 3600)h " + String(format: "%02lld", (s % 3600) / 60) + "m" }
        return "> 99h"
    }

    static func percent(_ task: DownloadTask) -> Int32 {
        Int32(min(max(task.progress, 0), 1) * 100)
    }

    static func isRunning(_ task: DownloadTask) -> Bool {
        task.status == .downloading || task.status == .connecting
    }

    static func stateLine(_ task: DownloadTask, _ store: FJStore) -> String {
        let s = store.strings, host = store.host
        let unit = store.state.speedDisplayUnit
        if task.isPreparingFile() {
            let step = host.localizeStep(text: DownloadTaskKt.STEP_PREPARING_FILE)
            return task.totalBytes > 0
                ? "\(step) · \(host.formatBytes(bytes: task.downloadedBytes)) / \(host.formatBytes(bytes: task.totalBytes))"
                : step
        }
        if task.waitingInQueue {
            let now = LocalClockKt.currentEpochMs()
            if task.scheduledAtMs > now {
                return s.homeStartsAt(time: LocalClockKt.formatLocalSchedule(epochMs: task.scheduledAtMs, nowMs: now))
            }
            return task.queuePosition > 0 ? s.homeWaitingInQueuePosition(position: task.queuePosition) : s.homeWaitingInQueue
        }
        if let post = task.postProcessLabel { return host.localizeStep(text: post) }
        switch task.status {
        case .connecting: return s.homeConnecting
        case .queued: return s.homeStarting
        case .downloading:
            var out = "\(percent(task))%"
            if let step = task.currentStepText, step.hasPrefix("Step") {
                let head = step.components(separatedBy: " (").first ?? step
                out += " · " + host.localizeStep(text: head)
            }
            if task.downloadSpeedBytesPerSec > 0 {
                out += " · " + host.formatSpeed(bytesPerSec: task.downloadSpeedBytesPerSec, unit: unit)
            }
            if let e = task.etaSeconds() { out += " · " + s.homeTimeLeft(eta: eta(e.int64Value)) }
            return out
        case .paused:
            return s.homePausedState(percent: percent(task), restartsOnResume: task.canResume()?.boolValue == false)
        case .error:
            return s.homeFailedState(message: task.errorMessage.map { host.localizeStep(text: $0) })
        case .completed:
            return s.homeCompletedState(size: task.totalBytes > 0 ? host.formatBytes(bytes: task.totalBytes) : nil)
        default:
            return task.status.name.lowercased().capitalized
        }
    }

    static func rowAction(_ task: DownloadTask) -> TaskRowAction {
        if task.waitingInQueue { return .startNow }
        switch task.status {
        case .downloading, .connecting, .queued: return .pause
        case .paused, .error: return .resume
        case .completed: return .open
        default: return .none
        }
    }

    static func barColor(_ task: DownloadTask) -> Color {
        if task.status == .error { return FJTheme.color.error }
        if task.status == .completed { return FJTheme.hex(0x34D399) }
        if task.isPreparingFile() { return FJTheme.color.primary }
        if task.status == .paused || task.waitingInQueue { return FJTheme.hex(0xFBBF24) }
        return FJTheme.color.primary
    }

    static func nextPriority(_ p: Int32) -> Int32 {
        p > 0 ? -1 : (p < 0 ? 0 : 1)
    }

    static func matchesDownloading(_ t: DownloadTask) -> Bool {
        !t.waitingInQueue && (t.status == .downloading || t.status == .connecting || t.status == .queued)
    }
}

struct TaskThinBar: View {
    let fraction: Float
    let color: Color
    var height: CGFloat = 5
    var indeterminate = false
    @State private var phase: CGFloat = -0.3

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(FJTheme.color.onSurface.opacity(0.08))
                if indeterminate {
                    Capsule().fill(color)
                        .frame(width: geo.size.width * 0.3)
                        .offset(x: geo.size.width * phase)
                        .onAppear {
                            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) { phase = 1.0 }
                        }
                } else {
                    Capsule().fill(color)
                        .frame(width: geo.size.width * CGFloat(min(max(fraction, 0), 1)))
                }
            }
            .clipShape(Capsule())
        }
        .frame(height: height)
    }
}

struct TaskKindSquare: View {
    let tag: String
    var size: CGFloat = 40

    var body: some View {
        Text(String(tag.prefix(4)))
            .font(.system(size: 9, weight: .heavy))
            .foregroundStyle(TaskUi.kindColor(tag))
            .frame(width: size, height: size)
            .background(FJTheme.color.onSurface.opacity(0.06), in: RoundedRectangle(cornerRadius: size / 4))
    }
}

struct TaskPriorityChip: View {
    @EnvironmentObject var store: FJStore
    let priority: Int32
    let onCycle: () -> Void

    var body: some View {
        let s = store.strings
        let label = priority > 0 ? s.priorityHigh : (priority < 0 ? s.priorityLow : s.priorityNormal)
        let color = priority > 0 ? FJTheme.hex(0xEF4444) : (priority < 0 ? FJTheme.hex(0x3B82F6) : FJTheme.color.onSurfaceVariant)
        Button(action: onCycle) {
            Text(label)
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(color)
                .lineLimit(1)
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(color.opacity(0.4), lineWidth: 1))
        }
        .buttonStyle(.borderless)
    }
}

struct TaskQuickActionButton: View {
    @EnvironmentObject var store: FJStore
    let action: TaskRowAction
    var size: CGFloat = 44
    let onTap: () -> Void

    var body: some View {
        if action == .none {
            Color.clear.frame(width: size, height: size)
        } else {
            let s = store.strings
            let (icon, label): (String, String) = {
                switch action {
                case .pause: return ("pause.fill", s.pause)
                case .resume: return ("play.fill", s.resume)
                case .startNow: return ("play.fill", s.homeStartNow)
                case .open: return ("arrow.up.right", s.open)
                case .none: return ("", "")
                }
            }()
            Button(action: onTap) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .frame(width: size, height: size)
                    .overlay(Circle().stroke(FJTheme.color.outlineVariant.opacity(0.7), lineWidth: 1))
                    .contentShape(Circle())
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(label)
        }
    }
}

struct TaskRowView: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask
    let checked: Bool
    let selectionMode: Bool
    let onQuickAction: (TaskRowAction) -> Void

    var body: some View {
        let tag = TaskUi.kindTag(task)
        let selected = store.selectedTaskId == task.id
        let accent = FJTheme.color.primary
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                if selectionMode {
                    Image(systemName: checked ? "checkmark.square.fill" : "square")
                        .font(.system(size: 20))
                        .foregroundStyle(checked ? accent : FJTheme.color.onSurfaceVariant)
                        .frame(width: 24, height: 24)
                        .padding(.trailing, 12)
                }
                TaskKindSquare(tag: tag, size: 40)
                    .padding(.trailing, 12)
                VStack(alignment: .leading, spacing: 3) {
                    Text(TaskUi.displayName(task, store.host))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Text(TaskUi.stateLine(task, store))
                        .font(.system(size: 12))
                        .foregroundStyle(task.status == .error ? FJTheme.color.error : FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if task.status != .completed {
                    TaskPriorityChip(priority: task.priority) {
                        store.send(DashboardUiEventOnTaskPriorityChanged(taskId: task.id, priority: TaskUi.nextPriority(task.priority)))
                    }
                    .padding(.leading, 8)
                }
                if !selectionMode && AudioCompressPlace.onRow && AudioCompress.shared.canCompress(task: task) {
                    AudioCompressRowButton(task: task)
                        .padding(.leading, 8)
                }
                if !selectionMode {
                    TaskQuickActionButton(action: TaskUi.rowAction(task), size: 44) {
                        onQuickAction(TaskUi.rowAction(task))
                    }
                    .padding(.leading, 8)
                }
            }
            if task.status != .completed {
                TaskThinBar(fraction: task.progress, color: TaskUi.barColor(task), indeterminate: task.isPreparingFile())
                    .padding(.top, 10)
            }
        }
        .padding(12)
        .background(selected ? accent.opacity(0.09) : FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(checked ? accent.opacity(0.7) : (selected ? accent.opacity(0.45) : FJTheme.color.outlineVariant.opacity(0.35)), lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 12))
    }
}
