import SwiftUI
import SharedLogic

struct AddMediaSection: View {
    @EnvironmentObject var store: FJStore
    let validity: AddValidity

    var body: some View {
        let s = store.state
        let strings = store.strings
        VStack(alignment: .leading, spacing: 12) {
            if let course = s.courseReading {
                AddLoadingBanner(
                    title: strings.readingCourseLectures(done: course.first?.int32Value ?? 0, total: course.second?.int32Value ?? 0),
                    subtitle: strings.readingCourseLecturesHint
                )
            } else if s.isInspectingMedia {
                AddLoadingBanner(title: strings.inspectingVideoQualities, subtitle: strings.inspectingVideoQualitiesHint)
            } else if let err = s.inspectMediaError {
                AddFailureCard(title: strings.videoInspectionFailed, message: err, retryLabel: "🔄 \(strings.addRetryInspection)") {
                    store.send(DashboardUiEventOnInspectMediaClicked.shared)
                }
            } else if !s.isYtDlpInstalled {
                engineNotReady(s, strings)
            } else if let playlist = s.inspectedPlaylist {
                AddPlaylistCard(playlist: playlist)
            } else if s.inspectedMediaQualities.isEmpty {
                let ext = (s.inputUrl.components(separatedBy: "?").first ?? "").components(separatedBy: ".").last?.lowercased() ?? ""
                let isAudio = ["mp3", "m4a", "flac", "wav", "aac", "ogg", "opus"].contains(ext)
                AddOutlineButton(
                    label: isAudio ? "🎵 \(strings.inspectAudioFormats)" : "🎬 \(strings.inspectVideoQuality)",
                    enabled: !s.inputUrl.isEmpty
                ) { store.send(DashboardUiEventOnInspectMediaClicked.shared) }
            } else {
                AddQualitiesCard()
                if !s.inspectedSubtitles.isEmpty { AddSubtitlesCard() }
                if s.mediaDurationSeconds > 0 { AddClipCard(validity: validity) }
            }
        }
    }

    private func engineNotReady(_ s: DashboardUiState, _ strings: AppLocalization) -> some View {
        let preparing = s.isDownloadingYtDlp || s.ytDlpStatus.lowercased().hasPrefix("preparing")
        let status = store.host.localizeStep(text: s.ytDlpStatus)
        return VStack(alignment: .leading, spacing: 8) {
            Text(preparing ? "⏳ \(strings.mediaEngineStarting)" : "⚠️ \(strings.mediaEngineNotReady)")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(preparing ? FJTheme.hex(0xFDE68A) : FJTheme.color.error)
            Text(status.isEmpty ? strings.mediaEngineRequiresYtDlp : status)
                .font(FJTheme.font.small)
                .foregroundStyle(FJTheme.hex(0xFEE2E2))
            Button {
                store.send(DashboardUiEventOnDownloadYtDlpClicked.shared)
            } label: {
                HStack(spacing: 8) {
                    if preparing { ProgressView().controlSize(.small).tint(.white) }
                    Text(preparing ? strings.preparingEngine : strings.startMediaEngine)
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outline, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(preparing)
        }
        .padding(12)
        .background(FJTheme.hex(0x451A1A), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.hex(0xDC2626), lineWidth: 1))
    }
}

struct AddQualitiesCard: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let qualities = store.state.inspectedMediaQualities
        let strings = store.strings
        let isAudio = MediaQualityEntryKt.isAllAudio(qualities)
        let icon = isAudio ? "🎵" : "🎬"
        let videoTitle = qualities.first { !($0.videoTitle ?? "").isEmpty }?.videoTitle
        AddCard {
            HStack {
                Text("\(icon) \(videoTitle.map { store.host.decodeUnicodeText(input: $0) } ?? (isAudio ? strings.availableAudioFormats : strings.availableQualities))")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(1)
                Spacer(minLength: 4)
                Button(strings.addSelectAll) { store.send(DashboardUiEventOnSelectAllMediaQualitiesToggled(selectAll: true)) }
                    .font(.system(size: 11))
                Button(strings.addClearSelection) { store.send(DashboardUiEventOnSelectAllMediaQualitiesToggled(selectAll: false)) }
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            Text(strings.selectedOfAvailable(selected: Int32(qualities.filter { $0.isSelected }.count), total: Int32(qualities.count)))
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(FJTheme.color.primary)
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(qualities, id: \.index) { q in
                        row(q, strings)
                    }
                }
                .padding(4)
            }
            .frame(height: min(CGFloat(qualities.count) * 44 + 8, 230))
            .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 8))
        }
    }

    private func row(_ q: MediaQualityEntry, _ strings: AppLocalization) -> some View {
        Button {
            store.send(DashboardUiEventOnMediaQualitySelectionToggled(qualityIndex: q.index, isSelected: !q.isSelected))
        } label: {
            HStack(spacing: 8) {
                AddCheckbox(checked: q.isSelected)
                VStack(alignment: .leading, spacing: 1) {
                    Text(q.qualityLabel)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                    let size = q.size > 0 ? " • \(store.host.formatSizeDisplay(bytes: q.size))" : ""
                    let dur = q.durationSeconds > 0 ? " • ⏱️ \(store.host.formatSecondsToDisplayFriendly(totalSeconds: q.durationSeconds))" : ""
                    Text(strings.qualityFormatLine(format: q.format.uppercased(), details: size + dur))
                        .font(.system(size: 10))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 5)
            .background(q.isSelected ? FJTheme.color.primaryContainer.opacity(0.4) : .clear, in: RoundedRectangle(cornerRadius: 6))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct AddSubtitlesCard: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let subs = store.state.inspectedSubtitles
        let strings = store.strings
        AddCard {
            Text("💬 \(strings.subtitlesTitle)")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(FJTheme.color.onSurface)
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(subs, id: \.langCode) { sub in
                        Button {
                            store.send(DashboardUiEventOnSubtitleToggled(langCode: sub.langCode, isSelected: !sub.isSelected))
                        } label: {
                            HStack(spacing: 8) {
                                AddCheckbox(checked: sub.isSelected)
                                Text(sub.langName)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(FJTheme.color.onSurface)
                                    .lineLimit(1)
                                if sub.isAuto {
                                    Text(strings.subtitlesAuto)
                                        .font(.system(size: 9))
                                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(FJTheme.color.surfaceVariant.opacity(0.7), in: RoundedRectangle(cornerRadius: 4))
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 6)
                            .padding(.vertical, 5)
                            .background(sub.isSelected ? FJTheme.color.primaryContainer.opacity(0.4) : .clear, in: RoundedRectangle(cornerRadius: 6))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(4)
            }
            .frame(height: min(CGFloat(subs.count) * 34 + 8, 160))
            .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 8))
            if subs.contains(where: { $0.isSelected }) {
                HStack(spacing: 8) {
                    AddOptionCard(selected: store.state.embedSubtitles, title: strings.subtitlesEmbedInVideo) {
                        store.send(DashboardUiEventOnToggleEmbedSubtitles(embed: true))
                    }
                    AddOptionCard(selected: !store.state.embedSubtitles, title: strings.subtitlesSeparateFile) {
                        store.send(DashboardUiEventOnToggleEmbedSubtitles(embed: false))
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 2)
            }
        }
    }
}

struct AddClipCard: View {
    @EnvironmentObject var store: FJStore
    let validity: AddValidity
    @State private var picking: AddClipEnd?

    var body: some View {
        let s = store.state
        let strings = store.strings
        let v = validity
        let selected = s.inspectedMediaQualities.first { $0.isSelected }
        let isAudio = selected?.isAudioOnly == true
        let dur = s.mediaDurationSeconds
        AddCard {
            Button {
                store.send(DashboardUiEventOnToggleClipRange(enabled: !s.isClipRangeEnabled))
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(strings.clipSectionTitle(isAudio: isAudio))
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(FJTheme.color.onSurface)
                        Text(strings.clipSectionHint)
                            .font(.system(size: 10))
                            .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                    Spacer()
                    Toggle("", isOn: Binding(
                        get: { s.isClipRangeEnabled },
                        set: { store.send(DashboardUiEventOnToggleClipRange(enabled: $0)) }
                    ))
                    .labelsHidden()
                }
            }
            .buttonStyle(.plain)
            if s.isClipRangeEnabled {
                Text("⏱️ \(strings.mediaDurationFullRange(isAudio: isAudio, clock: store.addClockText(dur), friendly: store.host.formatSecondsToDisplayFriendly(totalSeconds: dur)))")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(FJTheme.color.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(FJTheme.color.primaryContainer.opacity(0.45), in: RoundedRectangle(cornerRadius: 8))
                HStack(spacing: 10) {
                    timeCard(strings.clipStartTimeFrom, s.clipStartTime.isEmpty ? "00:00" : s.clipStartTime, v.isStartAfterEnd || v.isStartExceeding) { picking = .start }
                    timeCard(strings.clipEndTimeTo, s.clipEndTime.isEmpty ? v.fullDurationHhMm : s.clipEndTime, v.isStartAfterEnd || v.isEndExceeding) { picking = .end }
                }
                if let err = v.clipError { AddInlineError(message: err) }
                AddChipRow {
                    AddChoiceChip(label: strings.fullMediaChip(isAudio: isAudio, duration: store.addClockText(dur)), selected: false) {
                        setRange("00:00", v.fullDurationHhMm)
                    }
                    if dur >= 300 { AddChoiceChip(label: strings.clipFirst5Min, selected: false) { setRange("00:00:00", "00:05:00") } }
                    if dur >= 900 { AddChoiceChip(label: strings.clipFirst15Min, selected: false) { setRange("00:00:00", "00:15:00") } }
                    if dur >= 1800 { AddChoiceChip(label: strings.clipFirst30Min, selected: false) { setRange("00:00:00", "00:30:00") } }
                    if dur >= 3600 { AddChoiceChip(label: strings.clipFirst1Hour, selected: false) { setRange("00:00:00", "01:00:00") } }
                }
                Text(strings.clipModeTitle)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                let sizeText = (selected?.size ?? 0) > 0 ? " (\(store.host.formatBytes(bytes: selected!.size)))" : ""
                HStack(spacing: 8) {
                    AddOptionCard(selected: !s.clipFastMode, title: "✂️ \(strings.clipOnlyTitle)",
                                  lines: [strings.clipOnlyLine1, strings.clipOnlyLine2, strings.clipOnlyLine3]) {
                        store.send(DashboardUiEventOnToggleClipFastMode(enabled: false))
                    }
                    AddOptionCard(selected: s.clipFastMode, title: "⚡ \(strings.fullThenCutTitle(isAudio: isAudio))",
                                  lines: [strings.fullThenCutLine1(isAudio: isAudio, sizeText: sizeText), strings.fullThenCutLine2, strings.fullThenCutLine3, strings.fullThenCutLine4]) {
                        store.send(DashboardUiEventOnToggleClipFastMode(enabled: true))
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .sheet(item: $picking) { which in
            let maxForStart: Int64 = v.endSec > 0 ? (dur > 0 ? min(dur, v.endSec - 1) : v.endSec - 1) : dur
            AddTimePicker(
                title: "⏱️ \(which == .start ? strings.setStartTimeTitle : strings.setEndTimeTitle)",
                initialSeconds: which == .start ? v.startSec : v.endSec,
                minSeconds: which == .start ? 0 : v.startSec,
                maxSeconds: which == .start ? max(maxForStart, 0) : dur
            ) { secs in
                let text = store.host.formatSecondsToHhMm(totalSeconds: secs)
                if which == .start {
                    store.send(DashboardUiEventOnClipStartTimeChanged(time: text))
                } else {
                    store.send(DashboardUiEventOnClipEndTimeChanged(time: text))
                }
            }
            .environmentObject(store)
            .presentationDetents([.height(360)])
        }
    }

    private func setRange(_ start: String, _ end: String) {
        store.send(DashboardUiEventOnClipStartTimeChanged(time: start))
        store.send(DashboardUiEventOnClipEndTimeChanged(time: end))
    }

    private func timeCard(_ label: String, _ value: String, _ error: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(error ? FJTheme.color.error : FJTheme.color.onSurfaceVariant)
                Text(value)
                    .font(.system(size: 16, weight: .bold, design: .monospaced))
                    .foregroundStyle(error ? FJTheme.color.error : FJTheme.color.primary)
                Text(store.host.formatSecondsToDisplayFriendly(totalSeconds: store.host.parseHhMmToSeconds(hhMm: value)))
                    .font(.system(size: 10))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(error ? FJTheme.color.errorContainer.opacity(0.15) : FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(error ? FJTheme.color.error : FJTheme.color.primary.opacity(0.5), lineWidth: error ? 1.5 : 1))
        }
        .buttonStyle(.plain)
    }
}

enum AddClipEnd: String, Identifiable {
    case start, end
    var id: String { rawValue }
}

struct AddTimePicker: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.dismiss) private var dismiss
    let title: String
    let initialSeconds: Int64
    let minSeconds: Int64
    let maxSeconds: Int64
    let onPicked: (Int64) -> Void
    @State private var h = 0
    @State private var m = 0
    @State private var sec = 0

    private var total: Int64 { Int64(h * 3600 + m * 60 + sec) }
    private var valid: Bool { total >= minSeconds && (maxSeconds <= 0 || total <= maxSeconds) }

    var body: some View {
        VStack(spacing: 12) {
            Text(title).font(FJTheme.font.headline).foregroundStyle(FJTheme.color.onSurface)
            HStack(spacing: 0) {
                wheel($h, 0..<(max(Int(maxSeconds) / 3600, 0) + 1), "h")
                wheel($m, 0..<60, "m")
                wheel($sec, 0..<60, "s")
            }
            .frame(height: 160)
            .environment(\.layoutDirection, .leftToRight)
            Text("\(store.host.formatSecondsToHhMm(totalSeconds: minSeconds)) – \(store.host.formatSecondsToHhMm(totalSeconds: maxSeconds))")
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
            let t = Int(max(initialSeconds, 0))
            h = t / 3600
            m = (t % 3600) / 60
            sec = t % 60
        }
    }

    private func wheel(_ value: Binding<Int>, _ range: Range<Int>, _ unit: String) -> some View {
        Picker(unit, selection: value) {
            ForEach(range, id: \.self) { Text(String(format: "%02d", $0) + " \(unit)").tag($0) }
        }
        .pickerStyle(.wheel)
        .frame(maxWidth: .infinity)
        .clipped()
    }
}

struct AddPlaylistCard: View {
    @EnvironmentObject var store: FJStore
    let playlist: MediaPlaylist

    var body: some View {
        let strings = store.strings
        let selected = playlist.entries.filter { $0.isSelected }.count
        let all = selected == playlist.entries.count
        let url = store.state.inputUrl
        AddCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(playlist.title)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(2)
                    Text(strings.playlistSummary(videos: Int32(playlist.entries.count), selected: Int32(selected)))
                        .font(FJTheme.font.small)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                Spacer()
                Button(all ? strings.selectNone : strings.selectAll) {
                    store.send(DashboardUiEventOnPlaylistSelectAll(selected: !all))
                }
                .font(.system(size: 12, weight: .semibold))
            }
            AddLabel(text: strings.playlistFolder)
            AddField(icon: "📁", text: Binding(
                get: { playlist.folderName ?? playlist.title },
                set: { store.send(DashboardUiEventOnPlaylistFolderChanged(name: $0)) }
            ))
            Text(strings.playlistFolderHint)
                .font(.system(size: 10))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            if !playlist.fromBrowser {
                AddLabel(text: strings.qualityForAllVideos)
                AddChipRow {
                    ForEach(PlaylistQuality.entries, id: \.ordinal) { q in
                        AddChoiceChip(label: q.label, selected: q == store.state.playlistQuality) {
                            store.send(DashboardUiEventOnPlaylistQualityChanged(quality: q))
                        }
                    }
                }
                Text(strings.qualityForAllVideosHint)
                    .font(.system(size: 10))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(playlist.entries, id: \.index) { entry in
                        Button {
                            store.send(DashboardUiEventOnPlaylistEntryToggled(index: entry.index, selected: !entry.isSelected))
                        } label: {
                            HStack(spacing: 8) {
                                AddCheckbox(checked: entry.isSelected)
                                if !playlist.fromBrowser {
                                    Text("\(entry.index + 1).")
                                        .font(.system(size: 11))
                                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                                }
                                Text(playlist.fromBrowser ? "\(entry.title).mp4" : entry.title)
                                    .font(.system(size: 12))
                                    .foregroundStyle(FJTheme.color.onSurface)
                                    .lineLimit(1)
                                Spacer(minLength: 0)
                                if entry.durationSeconds > 0 {
                                    Text(store.addClockText(entry.durationSeconds))
                                        .font(.system(size: 11))
                                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                                }
                            }
                            .padding(.horizontal, 6)
                            .padding(.vertical, 6)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(height: min(CGFloat(playlist.entries.count) * 34 + 4, 280))
            .background(FJTheme.color.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
            if url.contains("v=") || url.contains("youtu.be/") {
                HStack {
                    Spacer()
                    Button(strings.downloadOnlyThisVideo) { store.send(DashboardUiEventOnPlaylistUseSingleVideo.shared) }
                        .font(.system(size: 12, weight: .semibold))
                }
            }
        }
    }
}

struct AddAudioConvertSection: View {
    @EnvironmentObject var store: FJStore
    let showsLinkList: Bool

    var body: some View {
        let s = store.state
        let forAll = showsLinkList || s.inspectedPlaylist != nil
        let shows: Bool = {
            if showsLinkList {
                return s.linkList.contains { e in e.isSelected && (e.mediaQualities.first { $0.isSelected }?.isAudioOnly ?? false) }
            }
            if s.inspectedPlaylist != nil { return s.playlistQuality == .audio }
            return s.detectedEngine == .mediaStream && !s.isInspectingMedia
                && (s.inspectedMediaQualities.first { $0.isSelected }?.isAudioOnly ?? false)
        }()
        let nativeFormat: String? = {
            if showsLinkList {
                let formats = Set(s.linkList.filter { $0.isSelected }.compactMap { e in e.mediaQualities.first { $0.isSelected && $0.isAudioOnly }?.format })
                return formats.count == 1 ? formats.first : nil
            }
            if s.inspectedPlaylist != nil { return s.playlistQuality.extension }
            return s.inspectedMediaQualities.first { $0.isSelected }?.format
        }()
        if shows {
            let native = nativeFormat?.lowercased().isEmpty == false ? nativeFormat!.lowercased() : nil
            let selected = s.audioConvertFormat
            VStack(alignment: .leading, spacing: 6) {
                AddLabel(text: forAll ? store.strings.audioConvertAllTo : store.strings.audioConvertTo)
                AddChipRow {
                    AddChoiceChip(label: native?.uppercased() ?? store.strings.audioOriginal, selected: selected == nil || selected == native) {
                        store.send(DashboardUiEventOnAudioConvertFormatSelected(format: nil))
                    }
                    ForEach(MediaAudioConvertKt.AUDIO_CONVERT_FORMATS.filter { $0 != native }, id: \.self) { f in
                        AddChoiceChip(label: f.uppercased(), selected: selected == f) {
                            store.send(DashboardUiEventOnAudioConvertFormatSelected(format: f))
                        }
                    }
                }
            }
        }
    }
}
