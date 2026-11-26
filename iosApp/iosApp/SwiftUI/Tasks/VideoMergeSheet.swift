import SharedLogic
import SwiftUI

struct FJVideoMergeSheetModifier: ViewModifier {
    @EnvironmentObject var store: FJStore

    func body(content: Content) -> some View {
        content.sheet(isPresented: Binding(
            get: { store.state.videoMerge != nil },
            set: { if !$0 { store.send(DashboardUiEventOnDismissMerge.shared) } }
        )) {
            if let page = store.state.videoMerge {
                VideoMergeSheet(page: page)
                    .environmentObject(store)
                    .interactiveDismissDisabled(page.running)
            }
        }
    }
}

enum VideoMergeClock {
    static func text(_ ms: Int64) -> String {
        let t = max(ms, 0) / 1000
        return t >= 3600
            ? String(format: "%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
            : String(format: "%02d:%02d", t / 60, t % 60)
    }
}

struct VideoMergeSheet: View {
    @EnvironmentObject var store: FJStore
    let page: VideoMergeUi

    var body: some View {
        Group {
            if page.running {
                VideoMergeProgressView(page: page)
            } else {
                VideoMergeEditView(page: page)
            }
        }
        .environmentObject(store)
        .environment(\.layoutDirection, store.isRtl ? .rightToLeft : .leftToRight)
    }
}

private struct VideoMergeHeader: View {
    @EnvironmentObject var store: FJStore
    let page: VideoMergeUi

    private var totalMs: Int64 { page.items.reduce(Int64(0)) { $0 + $1.durationMs } }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(store.strings.mergeTitle, systemImage: "arrow.triangle.merge")
                .font(FJTheme.font.headline)
                .foregroundStyle(FJTheme.color.onSurface)
            if !page.checking && totalMs > 0 {
                Text("\(store.strings.videoCutDuration): \u{2066}\(VideoMergeClock.text(totalMs))\u{2069}")
                    .font(.fj(12))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct VideoMergeEditView: View {
    @EnvironmentObject var store: FJStore
    let page: VideoMergeUi

    private var done: Bool { !page.failed && !page.lastPath.isEmpty }

    var body: some View {
        let s = store.strings
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VideoMergeHeader(page: page)
                Text(s.mergeHint)
                    .font(.fj(12))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                VStack(spacing: 8) {
                    ForEach(Array(page.items.enumerated()), id: \.element.taskId) { index, item in
                        VideoMergeRow(item: item, index: index, count: page.items.count)
                    }
                }
                if page.checking {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text(s.mergeChecking)
                            .font(.fj(12, .medium))
                            .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text(s.mergeFileName)
                        .font(.fj(12, .semibold))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    AddField(
                        icon: "🎬",
                        text: Binding(
                            get: { store.state.videoMerge?.fileName ?? page.fileName },
                            set: { store.send(DashboardUiEventOnMergeNameChanged(name: $0)) }
                        ),
                        trailing: AnyView(
                            Text(".\(page.extension)")
                                .font(.fj(13, .semibold))
                                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                                .environment(\.layoutDirection, .leftToRight)
                        ),
                        hasError: page.fileName.trimmingCharacters(in: .whitespaces).isEmpty
                    )
                }
                if !page.message.isEmpty || page.failed {
                    VideoCutMessage(text: page.message.isEmpty ? s.mergeFailed : page.message,
                                    error: page.failed, clipPath: page.lastPath)
                }
                HStack(spacing: 10) {
                    if done {
                        FJDialogButton(title: s.close, fill: true) {
                            store.send(DashboardUiEventOnDismissMerge.shared)
                        }
                    } else {
                        FJDialogButton(title: s.cancel, kind: .outlined, fill: true) {
                            store.send(DashboardUiEventOnDismissMerge.shared)
                        }
                        FJDialogButton(title: s.mergeVideos, enabled: page.canStart, fill: true) {
                            store.send(DashboardUiEventOnMergeStartClicked.shared)
                        }
                    }
                }
            }
            .padding(20)
        }
        .background(FJTheme.color.surface.ignoresSafeArea())
    }
}

private struct VideoMergeRow: View {
    @EnvironmentObject var store: FJStore
    let item: VideoMergeItem
    let index: Int
    let count: Int

    private var detail: String {
        var parts = [store.host.formatBytes(bytes: item.bytes)]
        if item.durationMs > 0 { parts.append(VideoMergeClock.text(item.durationMs)) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        let s = store.strings
        HStack(spacing: 10) {
            Text("\(index + 1)")
                .font(.fj(13, .bold))
                .foregroundStyle(item.notSame ? FJTheme.color.onError : FJTheme.color.primary)
                .frame(width: 32, height: 32)
                .background(item.notSame ? FJTheme.color.error : FJTheme.color.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 2) {
                Text(item.fileName)
                    .font(.fj(13, .semibold))
                    .foregroundStyle(item.notSame ? FJTheme.color.error : FJTheme.color.onSurface)
                    .lineLimit(2)
                    .truncationMode(.middle)
                HStack(spacing: 4) {
                    if item.notSame {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(FJTheme.color.error)
                    }
                    Text(detail)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .environment(\.layoutDirection, .leftToRight)
                }
                .font(.fj(11))
                .lineLimit(1)
            }
            Spacer(minLength: 0)
            moveButton("chevron.up", s.mergeMoveUp, enabled: index > 0) {
                store.send(DashboardUiEventOnMergeMoveClicked(taskId: item.taskId, up: true))
            }
            moveButton("chevron.down", s.mergeMoveDown, enabled: index < count - 1) {
                store.send(DashboardUiEventOnMergeMoveClicked(taskId: item.taskId, up: false))
            }
        }
        .padding(.leading, 10)
        .padding(.vertical, 4)
        .background(item.notSame ? FJTheme.color.errorContainer.opacity(0.35) : FJTheme.color.surfaceVariant.opacity(0.2),
                    in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10)
            .stroke(item.notSame ? FJTheme.color.error : Color.clear, lineWidth: 1))
    }

    private func moveButton(_ icon: String, _ label: String, enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.fj(14, .semibold))
                .foregroundStyle(FJTheme.color.primary)
                .frame(width: 40, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
        .accessibilityLabel(label)
    }
}

private struct VideoMergeProgressView: View {
    @EnvironmentObject var store: FJStore
    let page: VideoMergeUi
    @State private var tapped = false

    private var stopping: Bool { page.stopping || tapped }

    var body: some View {
        let s = store.strings
        let fraction = Double(min(max(page.progress, 0), 1))
        VStack(alignment: .leading, spacing: 16) {
            VideoMergeHeader(page: page)
            Text(stopping ? s.videoCutStopping : s.mergeRunning)
                .font(.fj(14, .semibold))
                .foregroundStyle(FJTheme.color.onSurface)
                .lineLimit(2)
                .truncationMode(.middle)
            ProgressView(value: fraction)
                .tint(FJTheme.color.primary)
            HStack {
                Text("\(Int((fraction * 100).rounded()))%")
                    .font(.fj(13, .semibold))
                    .foregroundStyle(FJTheme.color.primary)
                Spacer()
                Text("\(s.videoCutElapsed) \(VideoMergeClock.text(page.elapsedMs))")
                Text("·")
                Text("\(s.videoCutLeft) \(page.etaMs >= 0 ? VideoMergeClock.text(page.etaMs) : "--:--")")
            }
            .font(.fj(12))
            .foregroundStyle(FJTheme.color.onSurfaceVariant)
            .monospacedDigit()
            FJDialogButton(title: s.cancel, kind: .outlined, enabled: !stopping, fill: true) {
                tapped = true
                store.send(DashboardUiEventOnMergeCancelClicked.shared)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(FJTheme.color.surface.ignoresSafeArea())
        .presentationDetents([.height(300)])
    }
}
