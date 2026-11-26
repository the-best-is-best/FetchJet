package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.video_merge.VideoMerge
import com.michelle.raouf.fetch_jet.nativeapp.dto.VideoMergeDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.VideoMergeItemDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.VideoMergeUi

private fun mergeClock(ms: Long): String = if (ms > 0L) ltr(clockText((ms + 999L) / 1000L)) else ""

internal fun buildVideoMerge(page: VideoMergeUi?): VideoMergeDto {
    if (page == null) return VideoMergeDto()
    val last = page.items.lastIndex
    val movable = !page.running && page.lastPath.isEmpty()
    val progress = page.progress.coerceIn(0f, 1f).let { if (it.isNaN()) 0f else it }
    return VideoMergeDto(
        open = true,
        items = page.items.mapIndexed { index, item ->
            VideoMergeItemDto(
                id = item.taskId,
                name = item.fileName,
                nameRtl = startsRightToLeft(item.fileName),
                size = if (item.bytes > 0L) formatBytes(item.bytes) else "",
                duration = mergeClock(item.durationMs),
                notSame = item.notSame,
                canUp = index > 0 && movable,
                canDown = index < last && movable,
            )
        },
        fileName = page.fileName,
        extension = if (page.extension.isBlank()) "" else ltr("." + page.extension),
        total = if (page.checking) "" else mergeClock(page.items.sumOf { it.durationMs }),
        checking = page.checking,
        canStart = page.canStart && page.lastPath.isEmpty(),
        running = page.running,
        stopping = page.stopping,
        progress = progress,
        percent = if (page.running) ltr((progress * 100f).toInt().toString() + "%") else "",
        elapsed = if (page.running) ltr(clockText(page.elapsedMs / 1000L)) else "",
        timeLeft = if (!page.running) "" else if (page.etaMs < 0L) "—" else ltr(clockText((page.etaMs + 999L) / 1000L)),
        failed = page.failed,
        saved = !page.running && page.lastPath.isNotEmpty(),
        message = page.message,
    )
}

internal fun Presenter.mergeTargets(checked: Collection<String>): List<DownloadTask> {
    if (checked.isEmpty() || local.value.hiddenOpen) return emptyList()
    return expandPlaylistRows(checked).mapNotNull { task(it) }.sortedWith(compareBy({ it.createdAtEpochMs }, { it.fileName }))
}

internal fun Presenter.canMergeChecked(checked: Collection<String>): Boolean =
    checked.isNotEmpty() && VideoMerge.canMergeAll(mergeTargets(checked))

internal fun Presenter.mergeChecked() {
    val tasks = mergeTargets(local.value.checkedIds)
    if (VideoMerge.canMergeAll(tasks)) vm.onEvent(DashboardUiEvent.OnMergeVideosClicked(tasks.map { it.id }))
}
