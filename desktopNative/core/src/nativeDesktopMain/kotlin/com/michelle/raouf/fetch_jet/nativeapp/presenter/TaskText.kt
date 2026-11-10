package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.core.util.formatLocalSchedule
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.domain.model.etaSeconds
import com.michelle.raouf.fetch_jet.domain.model.STEP_PREPARING_FILE
import com.michelle.raouf.fetch_jet.domain.model.isPreparingFile
import com.michelle.raouf.fetch_jet.nativeapp.dto.BarColorCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.GroupColorCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.RowActionCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.TagColorCode
import com.tbib.kmp_localization.LocalizationManager

private val AudioExt = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
private val VideoExt = setOf("mp4", "mkv", "webm", "mov", "avi")

internal fun taskKindTag(task: DownloadTask): String {
    val ext = task.fileName.substringAfterLast('.', "").lowercase()
    return when {
        task.engineType == EngineType.BITTORRENT -> "MAG"
        task.engineType == EngineType.MEDIA_STREAM -> if (ext in AudioExt) "AUD" else "VID"
        ext in AudioExt -> "AUD"
        ext in VideoExt -> "VID"
        ext.length in 2..4 && ext.all { it.isLetterOrDigit() } -> ext.uppercase()
        else -> "FILE"
    }
}

internal fun kindColorCode(tag: String): TagColorCode = when (tag) {
    "VID" -> TagColorCode.VIDEO
    "AUD" -> TagColorCode.AUDIO
    "MAG" -> TagColorCode.MAGNET
    "ZIP", "RAR", "7Z" -> TagColorCode.ARCHIVE
    "ISO", "IMG", "DMG" -> TagColorCode.DISK
    "EXE", "MSI", "APK" -> TagColorCode.PROGRAM
    else -> TagColorCode.OTHER
}

internal fun etaText(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${(seconds % 60).toString().padStart(2, '0')}s"
    else -> "${seconds / 3600}h ${((seconds % 3600) / 60).toString().padStart(2, '0')}m"
}

internal fun taskDisplayName(task: DownloadTask): String =
    decodeUnicodeText(task.fileName.ifBlank { task.url.substringBefore('?').substringAfterLast('/') }).trim()

internal fun taskStateLine(task: DownloadTask, speedUnit: String): String {
    val strings = LocalizationManager.Strings
    val pct = (task.progress.coerceIn(0f, 1f) * 100).toInt()
    if (task.isPreparingFile()) return preparingLine(task)
    if (task.waitingInQueue) {
        return if (task.scheduledAtMs > currentEpochMs()) strings.homeStartsAt(formatLocalSchedule(task.scheduledAtMs))
        else if (task.queuePosition > 0) strings.homeWaitingInQueuePosition(task.queuePosition)
        else strings.homeWaitingInQueue
    }
    val postLabel = task.postProcessLabel
    if (postLabel != null) return localizeStep(postLabel)
    return when {
        task.status == DownloadStatus.CONNECTING -> strings.homeConnecting
        task.status == DownloadStatus.QUEUED -> strings.homeStarting
        task.status == DownloadStatus.DOWNLOADING -> buildString {
            append("$pct%")
            task.currentStepText?.takeIf { it.startsWith("Step") }
                ?.let { append(" · ").append(localizeStep(it.substringBefore(" ("))) }
            if (task.downloadSpeedBytesPerSec > 0) {
                append(" · ").append(formatSpeed(task.downloadSpeedBytesPerSec, speedUnit))
            }
            task.etaSeconds()?.let { append(" · ").append(strings.homeTimeLeft(etaText(it))) }
        }

        task.status == DownloadStatus.PAUSED -> strings.homePausedState(pct, task.canResume() == false)
        task.status == DownloadStatus.ERROR -> strings.homeFailedState(task.errorMessage?.let { localizeStep(it) })
        task.status == DownloadStatus.COMPLETED ->
            strings.homeCompletedState(if (task.totalBytes > 0) formatBytes(task.totalBytes) else null)

        else -> task.status.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}

internal fun preparingLine(task: DownloadTask): String {
    val step = localizeStep(STEP_PREPARING_FILE)
    return when {
        task.totalBytes > 0L && task.downloadedBytes > 0L -> "$step · ${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
        task.totalBytes > 0L -> "$step · ${formatBytes(task.totalBytes)}"
        else -> step
    }
}

internal fun sizeText(task: DownloadTask): String = when {
    task.totalBytes > 0 && task.status == DownloadStatus.DOWNLOADING &&
        task.approximateTotalBytes == task.totalBytes -> "~" + formatBytes(task.totalBytes)

    task.totalBytes > 0 -> formatBytes(task.totalBytes)
    task.downloadedBytes > 0 -> formatBytes(task.downloadedBytes)
    else -> "—"
}

internal fun rowActionFor(task: DownloadTask): RowActionCode = when {
    task.waitingInQueue -> RowActionCode.START_NOW
    task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING ||
        task.status == DownloadStatus.QUEUED -> RowActionCode.PAUSE

    task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.ERROR -> RowActionCode.RESUME
    task.status == DownloadStatus.COMPLETED -> RowActionCode.OPEN
    else -> RowActionCode.NONE
}

internal fun barColorFor(task: DownloadTask): BarColorCode = when {
    task.status == DownloadStatus.ERROR -> BarColorCode.ERROR
    task.status == DownloadStatus.COMPLETED -> BarColorCode.COMPLETED
    task.isPreparingFile() -> BarColorCode.PRIMARY
    task.status == DownloadStatus.PAUSED || task.waitingInQueue -> BarColorCode.PAUSED
    else -> BarColorCode.PRIMARY
}

internal fun nextPriority(current: Int): Int = when {
    current > 0 -> -1
    current < 0 -> 0
    else -> 1
}

internal fun priorityLabel(priority: Int): String = when {
    priority > 0 -> LocalizationManager.Strings.priorityHigh
    priority < 0 -> LocalizationManager.Strings.priorityLow
    else -> LocalizationManager.Strings.priorityNormal
}

internal enum class SideFilter {
    ALL,
    DOWNLOADING,
    WAITING,
    PAUSED,
    COMPLETED,
    FAILED,
    MEDIA,
    FILES,
    TORRENTS;

    val label: String
        get() = when (this) {
            ALL -> LocalizationManager.Strings.homeFilterAllDownloads
            DOWNLOADING -> LocalizationManager.Strings.homeFilterDownloading
            WAITING -> LocalizationManager.Strings.homeFilterWaiting
            PAUSED -> LocalizationManager.Strings.homeFilterPaused
            COMPLETED -> LocalizationManager.Strings.homeFilterCompleted
            FAILED -> LocalizationManager.Strings.homeFilterFailed
            MEDIA -> LocalizationManager.Strings.homeFilterMedia
            FILES -> LocalizationManager.Strings.homeFilterFiles
            TORRENTS -> LocalizationManager.Strings.homeFilterTorrents
        }

    val groupsByState: Boolean get() = this == ALL || this == MEDIA || this == FILES || this == TORRENTS

    fun matches(t: DownloadTask): Boolean = when (this) {
        ALL -> true
        DOWNLOADING -> !t.waitingInQueue && (t.status == DownloadStatus.DOWNLOADING ||
            t.status == DownloadStatus.CONNECTING || t.status == DownloadStatus.QUEUED)

        WAITING -> t.waitingInQueue
        PAUSED -> !t.waitingInQueue && (t.status == DownloadStatus.PAUSED || t.status == DownloadStatus.IDLE)
        COMPLETED -> t.status == DownloadStatus.COMPLETED
        FAILED -> t.status == DownloadStatus.ERROR
        MEDIA -> t.engineType == EngineType.MEDIA_STREAM
        FILES -> t.engineType == EngineType.HTTP_DIRECT
        TORRENTS -> t.engineType == EngineType.BITTORRENT
    }
}

internal class TaskStateGroup(
    val key: String,
    val icon: String,
    val title: String,
    val tasks: List<DownloadTask>,
    val color: GroupColorCode,
)

internal fun taskStateGroups(tasks: List<DownloadTask>): List<TaskStateGroup> {
    val downloading = tasks.filter { SideFilter.DOWNLOADING.matches(it) }
    val waiting = tasks.filter { SideFilter.WAITING.matches(it) }
    val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val used = HashSet<String>(downloading.size + waiting.size + completed.size)
    downloading.forEach { used.add(it.id) }
    waiting.forEach { used.add(it.id) }
    completed.forEach { used.add(it.id) }
    val stopped = tasks.filter { it.id !in used }
    val strings = LocalizationManager.Strings
    return listOf(
        TaskStateGroup("run", "⬇", strings.homeGroupDownloading, downloading, GroupColorCode.PRIMARY),
        TaskStateGroup("wait", "⏳", strings.homeGroupWaiting, waiting, GroupColorCode.MUTED),
        TaskStateGroup("stop", "⏸", strings.homeGroupPausedFailed, stopped, GroupColorCode.PAUSED),
        TaskStateGroup("done", "✓", strings.homeGroupCompleted, completed, GroupColorCode.TERTIARY),
    ).filter { it.tasks.isNotEmpty() }
}

internal fun anyPausable(tasks: List<DownloadTask>) = tasks.any {
    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING ||
        it.status == DownloadStatus.QUEUED
}

internal fun anyResumable(tasks: List<DownloadTask>) =
    tasks.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR }

internal fun taskFilePath(task: DownloadTask): String {
    val dest = task.destinationPath.trimEnd('/', '\\')
    val sep = if (dest.contains('\\')) "\\" else "/"
    return decodeUnicodeText("$dest$sep${task.fileName}").trim()
}
