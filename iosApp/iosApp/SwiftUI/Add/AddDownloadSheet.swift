import SwiftUI
import SharedLogic

struct AddDownloadSheet: View {
    @EnvironmentObject var store: FJStore
    @State private var showFolderPicker = false

    private var s: DashboardUiState { store.state }
    private var strings: AppLocalization { store.strings }

    var body: some View {
        let v = AddValidity(state: s, store: store)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AddHeader { close() }
                AddUrlSection()
                if v.showsLinkList {
                    AddLinkListSection()
                } else {
                    AddEngineSelector()
                    if s.detectedEngine == .bittorrent { AddTorrentSection() }
                    if s.detectedEngine == .mediaStream { AddMediaSection(validity: v) }
                }
                AddScheduleSection()
                if s.detectedEngine == .httpDirect && !v.showsLinkList { AddConnectionsSection() }
                AddAudioConvertSection(showsLinkList: v.showsLinkList)
                if !v.showsLinkList && s.inspectedPlaylist == nil { AddFileNameSection() }
                if !v.showsLinkList && s.inspectedPlaylist == nil && s.detectedEngine == .httpDirect { AddChecksumSection() }
                AddDestinationSection(showFolderPicker: $showFolderPicker)
                if s.detectedEngine == .bittorrent { AddLeechToggle() }
                AddActions(validity: v, onDismiss: close)
                    .padding(.top, 6)
            }
            .padding(20)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(FJTheme.color.surface.ignoresSafeArea())
        .presentationDragIndicator(.visible)
        .presentationSizing(.form.fitted(horizontal: false, vertical: true))
        .tint(FJTheme.color.primary)
        .environment(\.layoutDirection, store.isRtl ? .rightToLeft : .leftToRight)
        .sheet(isPresented: $showFolderPicker) {
            AddFolderPicker(initialPath: s.destinationPath) { path in
                store.send(DashboardUiEventOnDestinationPathChanged(path: path))
            }
            .environmentObject(store)
        }
        .overlay { AddSheetDialogs() }
        .overlay(alignment: .bottom) { FJToastHost() }
        .onAppear { autoInspect() }
        .onChange(of: s.inputUrl) { _, _ in autoInspect() }
        .onChange(of: s.detectedEngine) { _, _ in autoInspect() }
    }

    private func close() {
        store.send(DashboardUiEventOnToggleAddDialog(show: false))
    }

    private func autoInspect() {
        guard !s.inputUrl.trimmingCharacters(in: .whitespaces).isEmpty, s.linkList.isEmpty else { return }
        if s.detectedEngine == .bittorrent {
            if s.inspectedTorrentFiles.isEmpty && !s.isInspectingTorrent && s.inspectError == nil {
                store.send(DashboardUiEventOnInspectTorrentClicked.shared)
            }
        } else if s.detectedEngine == .mediaStream {
            if s.inspectedMediaQualities.isEmpty && s.inspectedPlaylist == nil && !s.isInspectingMedia
                && s.inspectMediaError == nil && s.isYtDlpInstalled {
                store.send(DashboardUiEventOnInspectMediaClicked.shared)
            }
        }
    }
}

struct AddValidity {
    let showsLinkList: Bool
    let isUrlValid: Bool
    let isInspectionComplete: Bool
    let isInspecting: Bool
    let hasActiveError: Bool
    let activeError: String?
    let fullDurationHhMm: String
    let startSec: Int64
    let endSec: Int64
    let isStartAfterEnd: Bool
    let isEndExceeding: Bool
    let isStartExceeding: Bool
    let isClipRangeValid: Bool
    let clipError: String?
    let canStart: Bool

    @MainActor
    init(state s: DashboardUiState, store: FJStore) {
        let strings = store.strings
        showsLinkList = !s.linkList.isEmpty
        isUrlValid = !s.inputUrl.trimmingCharacters(in: .whitespaces).isEmpty
        let listInspecting = showsLinkList && s.linkList.contains { $0.isSelected && $0.isBeingInspected }
        if showsLinkList {
            isInspectionComplete = s.linkList.contains { $0.isSelected } && !listInspecting
                && !s.linkList.contains { $0.isSelected && $0.needsRetry }
        } else if s.detectedEngine == .bittorrent {
            isInspectionComplete = s.inspectedTorrentFiles.contains { $0.isSelected } && s.inspectError == nil
        } else if s.detectedEngine == .mediaStream {
            isInspectionComplete = s.inspectMediaError == nil && (
                (s.inspectedPlaylist?.entries.contains { $0.isSelected } ?? false)
                || s.inspectedMediaQualities.contains { $0.isSelected })
        } else {
            isInspectionComplete = !s.needsFileName
        }
        isInspecting = s.isInspectingTorrent || s.isInspectingMedia || listInspecting
        activeError = s.errorMessage ?? s.inspectError ?? s.inspectMediaError
        hasActiveError = activeError != nil

        let dur = s.mediaDurationSeconds
        fullDurationHhMm = dur > 0 ? store.host.formatSecondsToHhMm(totalSeconds: dur) : "00:10:00"
        let startText = s.clipStartTime.isEmpty ? "00:00" : s.clipStartTime
        let endText = s.clipEndTime.isEmpty ? fullDurationHhMm : s.clipEndTime
        startSec = store.host.parseHhMmToSeconds(hhMm: startText)
        endSec = store.host.parseHhMmToSeconds(hhMm: endText)
        let on = s.isClipRangeEnabled
        isStartAfterEnd = on && startSec >= endSec
        isEndExceeding = on && dur > 0 && endSec > dur
        isStartExceeding = on && dur > 0 && startSec >= dur
        isClipRangeValid = !on || (!isStartAfterEnd && !isEndExceeding && !isStartExceeding)
        if !on {
            clipError = nil
        } else if isStartAfterEnd {
            clipError = strings.clipStartAfterEnd(start: startText, end: endText)
        } else if isEndExceeding {
            clipError = strings.clipEndExceedsVideo(end: endText, duration: fullDurationHhMm)
        } else if isStartExceeding {
            clipError = strings.clipStartExceedsVideo(duration: fullDurationHhMm)
        } else {
            clipError = nil
        }
        canStart = isUrlValid && isInspectionComplete && !s.isLoading && !isInspecting && !hasActiveError && isClipRangeValid
    }
}
