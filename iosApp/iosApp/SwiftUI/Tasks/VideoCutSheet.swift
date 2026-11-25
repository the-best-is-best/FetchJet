import AVFoundation
import SharedLogic
import SwiftUI

struct FJVideoCutSheetModifier: ViewModifier {
    @EnvironmentObject var store: FJStore

    func body(content: Content) -> some View {
        content.sheet(isPresented: Binding(
            get: { store.state.videoCut != nil },
            set: { if !$0 { store.send(DashboardUiEventOnDismissVideoCut.shared) } }
        )) {
            if let page = store.state.videoCut {
                VideoCutSheet(page: page)
                    .environmentObject(store)
                    .interactiveDismissDisabled(page.running || page.mode == .timeline)
            }
        }
    }
}

enum VideoCutPlayable {
    private static var cache: [String: Bool] = [:]

    static func check(_ path: String) -> Bool {
        if let known = cache[path] { return known }
        let ext = (path as NSString).pathExtension.lowercased()
        let ok = ["mp4", "m4v", "mov"].contains(ext) && AVURLAsset(url: URL(fileURLWithPath: path)).isPlayable
        cache[path] = ok
        return ok
    }
}

struct VideoCutSheet: View {
    @EnvironmentObject var store: FJStore
    let page: VideoCutUi

    private var path: String {
        if !page.sourcePath.isEmpty { return page.sourcePath }
        return store.state.allTasks.first { $0.id == page.taskId }.map { store.host.taskFilePath(task: $0) } ?? ""
    }

    var body: some View {
        Group {
            if page.clipsOnly {
                VideoCutClipsOnly(page: page)
                    .presentationDetents([.medium, .large])
            } else if page.running {
                VideoCutProgressView(page: page)
            } else if page.mode == .timeline, !path.isEmpty, page.durationMs > 0 {
                FJTrimView(
                    path: path,
                    durationMs: page.durationMs,
                    startMs: page.startMs,
                    endMs: page.endMs > 0 ? page.endMs : page.durationMs,
                    message: page.message,
                    failed: page.failed,
                    onCut: { start, end in
                        store.send(DashboardUiEventOnVideoCutRangeChanged(startMs: start, endMs: end))
                        store.send(DashboardUiEventOnVideoCutStartClicked.shared)
                    },
                    onClose: { store.send(DashboardUiEventOnDismissVideoCut.shared) }
                )
                .presentationDetents([.large])
                .modifier(VideoCutPageSizing())
            } else if page.mode == .time {
                VideoCutTimeView(page: page)
                    .presentationDetents([.medium, .large])
            } else {
                VideoCutChooser(page: page, timeline: page.timelineAvailable && VideoCutPlayable.check(path))
                    .presentationDetents([.medium, .large])
            }
        }
        .environmentObject(store)
        .environment(\.layoutDirection, store.isRtl ? .rightToLeft : .leftToRight)
    }
}

private struct VideoCutHeader: View {
    @EnvironmentObject var store: FJStore
    let page: VideoCutUi

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(store.strings.videoCutTitle, systemImage: "scissors")
                .font(FJTheme.font.headline)
                .foregroundStyle(FJTheme.color.onSurface)
            Text(page.fileName)
                .font(.fj(12))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .lineLimit(2)
                .truncationMode(.middle)
            Text("\(store.strings.videoCutDuration): \u{2066}\(FJTrimFormat.text(page.durationMs))\u{2069}")
                .font(.fj(12))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct VideoCutChooser: View {
    @EnvironmentObject var store: FJStore
    let page: VideoCutUi
    let timeline: Bool

    var body: some View {
        let s = store.strings
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VideoCutHeader(page: page)
                if page.reading {
                    ProgressView().frame(maxWidth: .infinity)
                }
                if !page.message.isEmpty || page.failed {
                    VideoCutMessage(text: page.message.isEmpty ? store.strings.videoCutFailed : page.message,
                                    error: page.failed, clipPath: page.lastClipPath)
                }
                card(icon: "clock", title: s.videoCutByTime, hint: s.videoCutByTimeHint, enabled: true) {
                    store.send(DashboardUiEventOnVideoCutModeChosen(mode: .time))
                }
                card(icon: "film.stack", title: s.videoCutOnTimeline,
                     hint: timeline ? s.videoCutOnTimelineHint : s.videoCutTimelineNeedsMp4, enabled: timeline) {
                    store.send(DashboardUiEventOnVideoCutModeChosen(mode: .timeline))
                }
            }
            .padding(20)
        }
        .background(FJTheme.color.surface.ignoresSafeArea())
    }

    private func card(icon: String, title: String, hint: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.fj(20, .semibold))
                    .foregroundStyle(FJTheme.color.primary)
                    .frame(width: 44, height: 44)
                    .background(FJTheme.color.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.fj(15, .semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                    Text(hint)
                        .font(.fj(12))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward")
                    .font(.fj(13, .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(FJTheme.color.surfaceVariant.opacity(0.25), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant.opacity(0.5), lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
    }
}

struct VideoCutMessage: View {
    let text: String
    let error: Bool
    var clipPath: String = ""

    var body: some View {
        HStack(spacing: 8) {
            Label(text, systemImage: error ? "exclamationmark.triangle" : "checkmark.circle")
                .font(.fj(12, .medium))
                .foregroundStyle(error ? FJTheme.color.error : FJTheme.color.primary)
            Spacer(minLength: 0)
            if !error && !clipPath.isEmpty {
                Button {
                    _ = PathResolver().openFile(filePath: clipPath)
                } label: {
                    Image(systemName: "play.circle.fill")
                        .font(.fj(22))
                        .foregroundStyle(FJTheme.color.primary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct VideoCutClipsOnly: View {
    @EnvironmentObject var store: FJStore
    let page: VideoCutUi

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VideoCutHeader(page: page)
                if let task = store.task(page.taskId) {
                    VideoCutClipsSection(task: task)
                }
            }
            .padding(20)
        }
        .background(FJTheme.color.surface.ignoresSafeArea())
    }
}

private enum VideoCutEnd: String, Identifiable {
    case start, end
    var id: String { rawValue }
}

private struct VideoCutTimeView: View {
    @EnvironmentObject var store: FJStore
    let page: VideoCutUi
    @State private var picking: VideoCutEnd?

    private var endMs: Int64 { page.endMs > 0 ? page.endMs : page.durationMs }
    private var invalid: Bool { !page.reading && page.durationMs > 0 && !page.canStart }

    var body: some View {
        let s = store.strings
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VideoCutHeader(page: page)
                HStack(spacing: 10) {
                    timeCard(s.videoCutFrom, page.startMs) { picking = .start }
                    timeCard(s.videoCutTo, endMs) { picking = .end }
                }
                .environment(\.layoutDirection, .leftToRight)
                Text("\(s.videoCutDuration): \u{2066}\(FJTrimFormat.text(max(endMs - page.startMs, 0)))\u{2069}")
                    .font(.fj(13, .medium))
                    .foregroundStyle(invalid ? FJTheme.color.error : FJTheme.color.onSurfaceVariant)
                if page.reading {
                    ProgressView().frame(maxWidth: .infinity)
                }
                if !page.message.isEmpty || page.failed {
                    VideoCutMessage(text: page.message.isEmpty ? store.strings.videoCutFailed : page.message,
                                    error: page.failed, clipPath: page.lastClipPath)
                }
                HStack(spacing: 10) {
                    FJDialogButton(title: s.cancel, kind: .outlined, fill: true) {
                        store.send(DashboardUiEventOnDismissVideoCut.shared)
                    }
                    FJDialogButton(title: s.videoCutApply, enabled: page.canStart, fill: true) {
                        store.send(DashboardUiEventOnVideoCutStartClicked.shared)
                    }
                }
            }
            .padding(20)
        }
        .background(FJTheme.color.surface.ignoresSafeArea())
        .sheet(item: $picking) { which in
            VideoCutTimePicker(
                title: which == .start ? s.videoCutFrom : s.videoCutTo,
                initialMs: which == .start ? page.startMs : endMs,
                minMs: which == .start ? 0 : page.startMs + 1,
                maxMs: which == .start ? max(endMs - 1, 0) : page.durationMs
            ) { ms in
                if which == .start {
                    store.send(DashboardUiEventOnVideoCutRangeChanged(startMs: ms, endMs: endMs))
                } else {
                    store.send(DashboardUiEventOnVideoCutRangeChanged(startMs: page.startMs, endMs: ms))
                }
            }
            .environmentObject(store)
            .presentationDetents([.height(380)])
        }
    }

    private func timeCard(_ label: String, _ ms: Int64, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.fj(10, .semibold))
                    .foregroundStyle(invalid ? FJTheme.color.error : FJTheme.color.onSurfaceVariant)
                Text(FJTrimFormat.text(ms))
                    .font(.system(size: 16, weight: .bold, design: .monospaced))
                    .foregroundStyle(invalid ? FJTheme.color.error : FJTheme.color.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(invalid ? FJTheme.color.error : FJTheme.color.primary.opacity(0.5), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct VideoCutTimePicker: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    let title: String
    let initialMs: Int64
    let minMs: Int64
    let maxMs: Int64
    let onPicked: (Int64) -> Void
    @State private var h = 0
    @State private var m = 0
    @State private var sec = 0
    @State private var ms = 0

    private var total: Int64 { Int64(h * 3_600_000 + m * 60_000 + sec * 1000 + ms) }
    private var valid: Bool { total >= minMs && total <= maxMs }

    var body: some View {
        VStack(spacing: 12) {
            Text(title).font(FJTheme.font.headline).foregroundStyle(FJTheme.color.onSurface)
            HStack(spacing: 0) {
                if maxMs >= 3_600_000 { wheel($h, 0..<(Int(maxMs / 3_600_000) + 1), "h") }
                wheel($m, 0..<60, "m")
                wheel($sec, 0..<60, "s")
                wheel($ms, 0..<1000, "ms", digits: 3)
            }
            .frame(height: 160)
            .environment(\.layoutDirection, .leftToRight)
            Text("\(FJTrimFormat.text(minMs)) – \(FJTrimFormat.text(maxMs))")
                .font(FJTheme.font.small)
                .foregroundStyle(valid ? FJTheme.color.onSurfaceVariant : FJTheme.color.error)
            HStack(spacing: 10) {
                Button(store.strings.cancel) { dismiss() }
                    .buttonStyle(.bordered)
                Button(store.strings.scheduleSet) {
                    onPicked(total)
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .disabled(!valid)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FJTheme.color.surface.ignoresSafeArea())
        .onAppear {
            let t = Int(max(initialMs, 0))
            h = t / 3_600_000
            m = (t % 3_600_000) / 60_000
            sec = (t % 60_000) / 1000
            ms = t % 1000
        }
    }

    private func wheel(_ value: Binding<Int>, _ range: Range<Int>, _ unit: String, digits: Int = 2) -> some View {
        Picker(unit, selection: value) {
            ForEach(range, id: \.self) { Text(String(format: "%0\(digits)d", $0) + " \(unit)").tag($0) }
        }
        .pickerStyle(.wheel)
        .frame(maxWidth: .infinity)
        .clipped()
    }
}

private struct VideoCutProgressView: View {
    @EnvironmentObject var store: FJStore
    let page: VideoCutUi
    @State private var tapped = false

    private var stopping: Bool { page.stopping || tapped }

    var body: some View {
        let s = store.strings
        let fraction = Double(min(max(page.progress, 0), 1))
        VStack(alignment: .leading, spacing: 16) {
            VideoCutHeader(page: page)
            Text(stopping ? s.videoCutStopping : s.videoCutRunning)
                .font(.fj(14, .semibold))
                .foregroundStyle(FJTheme.color.onSurface)
            ProgressView(value: fraction)
                .tint(FJTheme.color.primary)
            HStack {
                Text("\(Int((fraction * 100).rounded()))%")
                    .font(.fj(13, .semibold))
                    .foregroundStyle(FJTheme.color.primary)
                Spacer()
                Text("\(s.videoCutElapsed) \(clock(page.elapsedMs))")
                Text("·")
                Text("\(s.videoCutLeft) \(page.etaMs >= 0 ? clock(page.etaMs) : "--:--")")
            }
            .font(.fj(12))
            .foregroundStyle(FJTheme.color.onSurfaceVariant)
            .monospacedDigit()
            FJDialogButton(title: s.cancel, kind: .outlined, enabled: !stopping, fill: true) {
                tapped = true
                store.send(DashboardUiEventOnVideoCutCancelClicked.shared)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(FJTheme.color.surface.ignoresSafeArea())
        .presentationDetents([.height(300)])
    }

    private func clock(_ ms: Int64) -> String {
        let t = max(ms, 0) / 1000
        return t >= 3600
            ? String(format: "%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
            : String(format: "%02d:%02d", t / 60, t % 60)
    }
}

struct VideoCutClipsSection: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask
    @State private var confirm: CutClip?

    private var clips: [CutClip] { store.state.videoClips[task.id] ?? [] }

    var body: some View {
        let s = store.strings
        if !clips.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("\(s.videoCutClips) (\(clips.count))")
                    .font(.fj(12, .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                ForEach(clips, id: \.path) { clip in
                    HStack(spacing: 10) {
                        Image(systemName: "film")
                            .font(.fj(14, .semibold))
                            .foregroundStyle(FJTheme.color.primary)
                            .frame(width: 32, height: 32)
                            .background(FJTheme.color.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(clip.name)
                                .font(.fj(13, .semibold))
                                .foregroundStyle(FJTheme.color.onSurface)
                                .lineLimit(1)
                                .truncationMode(.middle)
                            Text("\(FJTrimFormat.text(clip.startMs)) – \(FJTrimFormat.text(clip.endMs)) · \(ByteCountFormatter.string(fromByteCount: clip.bytes, countStyle: .file))")
                                .font(.fj(11))
                                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                                .lineLimit(1)
                                .environment(\.layoutDirection, .leftToRight)
                        }
                        Spacer(minLength: 0)
                        Button {
                            confirm = clip
                        } label: {
                            Image(systemName: "trash")
                                .font(.fj(14, .semibold))
                                .foregroundStyle(FJTheme.color.error)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(s.videoCutDeleteClip)
                    }
                    .padding(.leading, 10)
                    .background(FJTheme.color.surfaceVariant.opacity(0.2), in: RoundedRectangle(cornerRadius: 10))
                    .contentShape(Rectangle())
                    .onTapGesture { _ = PathResolver().openFile(filePath: clip.path) }
                }
            }
            .confirmationDialog(s.videoCutDeleteClip, isPresented: Binding(
                get: { confirm != nil }, set: { if !$0 { confirm = nil } }), titleVisibility: .visible, presenting: confirm) { clip in
                Button(s.videoCutDeleteClip, role: .destructive) {
                    store.send(DashboardUiEventOnVideoCutDeleteClip(path: clip.path))
                }
                Button(s.cancel, role: .cancel) {}
            } message: { clip in
                Text(clip.name)
            }
        }
    }
}

private struct VideoCutPageSizing: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.presentationSizing(.page)
        } else {
            content
        }
    }
}
