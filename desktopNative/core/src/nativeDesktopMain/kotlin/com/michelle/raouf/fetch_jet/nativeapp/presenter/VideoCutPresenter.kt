package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.feature.video_cut.CutClip
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCutMode
import com.michelle.raouf.fetch_jet.nativeapp.dto.VideoClipDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.VideoCutDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoCutUi
import com.tbib.kmp_localization.LocalizationManager
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings

private fun cutClock(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val base = formatSecondsToHhMm(t / 1000L)
    val rest = t % 1000L
    return if (rest == 0L) base else base + "." + rest.toString().padStart(3, '0')
}

private fun cutLength(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val rest = t % 1000L
    val base = clockText(t / 1000L)
    return if (rest == 0L) base else base + "." + rest.toString().padStart(3, '0')
}

internal fun videoClipRow(clip: CutClip): VideoClipDto = VideoClipDto(
    path = clip.path,
    name = clip.name,
    nameRtl = startsRightToLeft(clip.name),
    size = formatBytes(clip.bytes),
    range = ltr(cutLength(clip.startMs) + " – " + cutLength(clip.endMs)),
)

internal fun buildVideoCut(page: VideoCutUi?, known: List<CutClip>): VideoCutDto {
    if (page == null) return VideoCutDto()
    val length = page.endMs - page.startMs
    val bad = !VideoCut.isValidRange(page.startMs, page.endMs, page.durationMs)
    return VideoCutDto(
        open = true,
        chooser = page.mode == null && !page.clipsOnly,
        timeline = page.mode == VideoCutMode.TIMELINE,
        clipsOnly = page.clipsOnly,
        reading = page.reading,
        timelineOff = !page.timelineAvailable,
        fileName = page.fileName,
        fileNameRtl = startsRightToLeft(page.fileName),
        duration = if (page.durationMs > 0L) ltr(cutLength(page.durationMs)) else "",
        start = ltr(cutClock(page.startMs)),
        startFriendly = formatSecondsToDisplayFriendly(page.startMs / 1000L),
        end = ltr(cutClock(page.endMs)),
        endFriendly = formatSecondsToDisplayFriendly(page.endMs / 1000L),
        length = if (length > 0L) ltr(cutLength(length)) else "",
        rangeError = bad,
        canStart = page.canStart,
        running = page.running,
        stopping = page.stopping,
        progress = page.progress.coerceIn(0f, 1f).let { if (it.isNaN()) 0f else it },
        percent = if (page.running) ltr((page.progress.coerceIn(0f, 1f) * 100f).toInt().toString() + "%") else "",
        elapsed = if (page.running) ltr(clockText(page.elapsedMs / 1000L)) else "",
        timeLeft = if (!page.running) "" else if (page.etaMs < 0L) "—" else ltr(clockText((page.etaMs + 999L) / 1000L)),
        failed = page.failed,
        needsTools = page.failed && page.message == LocalizationManager.Strings.videoCutNeedsTools,
        message = page.message,
        clips = (known + page.clips).distinctBy { it.path }.map { videoClipRow(it) },
    )
}

internal fun Presenter.videoCutChoose(timeline: Boolean) {
    val page = vm.uiState.value.videoCut ?: return
    if (!timeline) return vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIME))
    if (!page.timelineAvailable) return vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIME))
    vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIMELINE))
    val task = task(page.taskId) ?: return
    if (headless) return
    openTrimPlayer(task, PlayerTrim(page.taskId, page.startMs, page.endMs))
}

internal fun Presenter.videoCutPickTime(end: Boolean) {
    val page = vm.uiState.value.videoCut ?: return
    val duration = (page.durationMs / 1000L).coerceAtLeast(0L)
    val startSec = page.startMs / 1000L
    val endSec = (page.endMs + 999L) / 1000L
    val initial = if (end) endSec else startSec
    clipPicker.value = ClipPicker(
        which = if (end) 2 else 1,
        maxSeconds = if (end) duration else (endSec - 1L).coerceAtLeast(0L),
        minSeconds = if (end) startSec else 0L,
        hoursRev = clipPicker.value.hoursRev + 1,
        minutesRev = clipPicker.value.minutesRev + 1,
        secondsRev = clipPicker.value.secondsRev + 1,
        forCut = true,
    ).withTotal(initial)
}

internal fun Presenter.videoCutPicked(which: Int, seconds: Long) {
    val page = vm.uiState.value.videoCut ?: return
    val ms = seconds * 1000L
    if (which == 1) {
        vm.onEvent(DashboardUiEvent.OnVideoCutRangeChanged(ms, page.endMs))
    } else {
        val end = if (page.durationMs > 0L && ms >= page.durationMs / 1000L * 1000L) page.durationMs else ms
        vm.onEvent(DashboardUiEvent.OnVideoCutRangeChanged(page.startMs, end))
    }
}

internal fun Presenter.videoCutApply(startMs: Int, endMs: Int) {
    vm.uiState.value.videoCut ?: return
    vm.onEvent(DashboardUiEvent.OnVideoCutRangeChanged(startMs.toLong(), endMs.toLong()))
    vm.onEvent(DashboardUiEvent.OnVideoCutStartClicked)
}

internal fun Presenter.videoCutDeleteClip(path: String) {
    val page = vm.uiState.value.videoCut
    if (page != null) return vm.onEvent(DashboardUiEvent.OnVideoCutDeleteClip(path))
    val owner = vm.uiState.value.videoClips.entries.firstOrNull { (_, clips) -> clips.any { it.path == path } }?.key ?: return
    vm.onEvent(DashboardUiEvent.OnVideoCutShowClips(owner))
    vm.onEvent(DashboardUiEvent.OnVideoCutDeleteClip(path))
    vm.onEvent(DashboardUiEvent.OnDismissVideoCut)
}
