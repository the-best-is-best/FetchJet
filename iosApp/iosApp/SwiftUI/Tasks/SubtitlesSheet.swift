import SwiftUI
import SharedLogic

struct SubtitlesButton: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask

    var body: some View {
        TaskDetailButton(text: store.strings.subtitlesAddButton, primary: false, icon: "captions.bubble") {
            store.send(DashboardUiEventOnSubtitlesClicked(taskId: task.id))
        }
        .sheet(isPresented: Binding(
            get: { store.state.subtitlesPage?.taskId == task.id },
            set: { if !$0 { store.send(DashboardUiEventOnDismissSubtitles.shared) } })) {
            if let page = store.state.subtitlesPage {
                SubtitlesSheet(page: page).environmentObject(store)
            }
        }
    }
}

struct SubtitlesSheet: View {
    @EnvironmentObject var store: FJStore
    let page: SubtitlesUi

    var body: some View {
        let s = store.strings
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                header(s)
                if page.reading {
                    Text(s.subtitlesReading)
                        .font(FJTheme.font.callout)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    ProgressView().frame(maxWidth: .infinity)
                } else if page.readFailed {
                    Text(s.subtitlesReadFailed)
                        .font(FJTheme.font.callout)
                        .foregroundStyle(FJTheme.color.error)
                } else if page.tracks.isEmpty {
                    Text(s.subtitlesNone)
                        .font(FJTheme.font.callout)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                } else {
                    Text(DashboardSubtitlesKt.subtitlesNoteText(page: page))
                        .font(FJTheme.font.callout)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    VStack(spacing: 0) {
                        ForEach(page.tracks, id: \.self) { track in
                            trackRow(track, s)
                        }
                    }
                }
                if page.running {
                    Text(DashboardSubtitlesKt.subtitlesProgressText(done: page.done, total: page.total))
                        .font(FJTheme.font.callout)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    ProgressView(value: page.total > 0 ? Double(page.done) / Double(page.total) : 0)
                }
                result(s.subtitlesAddedList, page.added, FJTheme.color.primary)
                result(s.subtitlesRefusedList, page.refused, FJTheme.color.error)
                result(s.subtitlesFailedList, page.failed, FJTheme.color.error)
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 12)
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                store.send(DashboardUiEventOnSubtitlesAddClicked.shared)
            } label: {
                Text(s.subtitlesAddSelected)
                    .font(.fj(15, .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(FJTheme.color.primary)
            .disabled(!page.canAdd)
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
        }
        .background(FJTheme.color.surface.ignoresSafeArea())
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(page.running)
    }

    private func header(_ s: AppLocalization) -> some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text(s.subtitlesAddButton)
                    .font(.fj(22, .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                Text(store.host.decodeUnicodeText(input: page.fileName))
                    .font(FJTheme.font.callout)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Spacer(minLength: 0)
            Button {
                store.send(DashboardUiEventOnDismissSubtitles.shared)
            } label: {
                Image(systemName: "xmark")
                    .font(.fj(15, .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(s.close)
            .disabled(page.running)
            .opacity(page.running ? 0.45 : 1)
        }
    }

    private func trackRow(_ track: SubtitleTrack, _ s: AppLocalization) -> some View {
        let key = DashboardContractKt.subtitleKey(track: track)
        let there = page.present.contains(key)
        let enabled = !there && !page.running
        let checked = track.isSelected || there
        return Button {
            store.send(DashboardUiEventOnSubtitleTrackToggled(key: key, selected: !track.isSelected))
        } label: {
            HStack(spacing: 12) {
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .font(.fj(20))
                    .foregroundStyle(checked && enabled ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                Text(DashboardSubtitlesKt.trackLabel(track: track))
                    .font(.fj(17))
                    .foregroundStyle(enabled ? FJTheme.color.onSurface : FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
                Spacer(minLength: 0)
                if there {
                    Text(s.subtitlesAlreadyThere)
                        .font(FJTheme.font.caption)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityAddTraits(checked ? .isSelected : [])
    }

    @ViewBuilder
    private func result(_ template: String, _ names: [String], _ color: Color) -> some View {
        if !names.isEmpty {
            Text(DashboardSubtitlesKt.subtitlesListText(template: template, names: names))
                .font(FJTheme.font.callout)
                .foregroundStyle(color)
        }
    }
}
