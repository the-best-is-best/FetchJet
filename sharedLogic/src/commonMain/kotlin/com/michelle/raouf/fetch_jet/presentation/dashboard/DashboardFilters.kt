package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType

/**
 * Filters by tab, then sorts. Unfinished downloads always come before completed ones
 * (the UI shows them as two groups), and the chosen sort applies inside each group.
 */
internal fun DashboardViewModel.filterTasks(
    tasks: List<DownloadTask>,
    tab: DashboardTab,
    sort: TaskSortOption = TaskSortOption.DATE_DESC
): List<DownloadTask> {
    val filtered = filterByTab(tasks, tab)
    val sizeOf =
        { t: DownloadTask -> if (t.totalBytes > 0) t.totalBytes else t.approximateTotalBytes }
    val comparator: Comparator<DownloadTask> = when (sort) {
        TaskSortOption.DATE_DESC -> compareByDescending { it.createdAtEpochMs }
        TaskSortOption.DATE_ASC -> compareBy { it.createdAtEpochMs }
        TaskSortOption.SIZE_DESC -> compareByDescending<DownloadTask> { sizeOf(it) }.thenByDescending { it.createdAtEpochMs }
        TaskSortOption.SIZE_ASC -> compareBy<DownloadTask> { sizeOf(it) }.thenByDescending { it.createdAtEpochMs }
        TaskSortOption.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName.ifBlank { it.url } }
    }
    val (unfinished, finished) = filtered.partition { it.status != DownloadStatus.COMPLETED }
    // Unfinished: what is downloading now on top, then the queue in its order, then paused,
    // then failed. The chosen sort applies inside each group.
    val stateRank = { t: DownloadTask ->
        when {
            t.waitingInQueue -> 1
            t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.CONNECTING ||
                    t.status == DownloadStatus.QUEUED -> 0

            t.status == DownloadStatus.PAUSED -> 2
            else -> 3
        }
    }
    val queueOrder = { t: DownloadTask ->
        if (t.waitingInQueue) (if (t.queuePosition > 0) t.queuePosition.toLong() else Long.MAX_VALUE / 2 + t.scheduledAtMs / 1000)
        else 0L
    }
    val unfinishedOrder = compareBy<DownloadTask> { stateRank(it) }
        .thenBy { queueOrder(it) }
        .then(comparator)
    return unfinished.sortedWith(unfinishedOrder) + finished.sortedWith(comparator)
}

internal fun DashboardViewModel.filterByTab(
    tasks: List<DownloadTask>,
    tab: DashboardTab
): List<DownloadTask> {

    return when (tab) {

        DashboardTab.ALL -> tasks

        DashboardTab.ACTIVE -> tasks.filter {

            it.status == DownloadStatus.DOWNLOADING ||

                    it.status == DownloadStatus.CONNECTING ||

                    it.status == DownloadStatus.QUEUED

        }

        DashboardTab.COMPLETED -> tasks.filter { it.status == DownloadStatus.COMPLETED }

        DashboardTab.TORRENT -> tasks.filter { it.engineType == EngineType.BITTORRENT }

        DashboardTab.MEDIA -> tasks.filter { it.engineType == EngineType.MEDIA_STREAM }

        DashboardTab.SETTINGS -> tasks

    }

}

internal fun DashboardViewModel.formatSecondsToHhMm(totalSeconds: Long): String {
    // HH:MM:SS - the seconds matter: a 4:37 video used to get an end time of 00:04,
    // cutting the last 37 seconds off every "full video" clip.
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "${hours.toString().padStart(2, '0')}:${
        minutes.toString().padStart(2, '0')
    }:${seconds.toString().padStart(2, '0')}"
}

internal fun DashboardViewModel.normalizeHhMmForYtDlp(hhMm: String): String {
    val parts = hhMm.split(":")
    if (parts.size == 2) {
        val h = parts[0].padStart(2, '0')
        val m = parts[1].padStart(2, '0')
        return "$h:$m:00"
    }
    if (parts.size == 3) {
        val h = parts[0].padStart(2, '0')
        val m = parts[1].padStart(2, '0')
        val s = parts[2].padStart(2, '0')
        return "$h:$m:$s"
    }
    return hhMm
}

internal fun DashboardViewModel.parseHhMmToSeconds(hhMm: String): Long {
    if (hhMm.isBlank()) return 0L
    val parts = hhMm.split(":")
    val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val s = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return (h * 3600L + m * 60L + s).coerceAtLeast(0L)
}
