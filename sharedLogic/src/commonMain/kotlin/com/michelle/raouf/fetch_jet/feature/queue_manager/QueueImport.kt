package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.core.filesystem.ImportedPaths
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType

internal class ImportedTasks(val added: List<DownloadTask>, val skipped: Int)

internal val safeImportId = Regex("[A-Za-z0-9_-]+")

/**
 * Whether "also delete files" may delete the file at the task's destination. Only a finished task
 * wrote its file there; a finished direct download (or one restored from a backup) must also still
 * have the size it was saved with, so a same-named file of the user's is never taken for it.
 */
internal fun ownsDestinationFile(task: DownloadTask, sizeOnDisk: Long?): Boolean {
    if (task.status != DownloadStatus.COMPLETED || sizeOnDisk == null) return false
    if (task.engineType == EngineType.HTTP_DIRECT && !task.hidden && task.totalBytes > 0) return sizeOnDisk == task.totalBytes
    return true
}

internal fun torrentWroteFiles(task: DownloadTask, partsFileExists: Boolean): Boolean =
    task.status == DownloadStatus.COMPLETED || task.downloadedBytes > 0 || partsFileExists

internal fun QueueManagerRepository.cleanImported(task: DownloadTask, paths: ImportedPaths): DownloadTask = task.copy(
    id = if (safeImportId.matches(task.id)) task.id else generateUniqueTaskId(),
    fileName = if (task.fileName.isBlank()) "" else pathResolver.sanitizeFileName(task.fileName),
    destinationPath = paths.directoryOrDownloads(task.destinationPath),
    totalBytes = task.totalBytes.coerceAtLeast(0L),
    downloadedBytes = task.downloadedBytes.coerceAtLeast(0L),
    maxDownloadSpeedBytesPerSec = task.maxDownloadSpeedBytesPerSec.coerceAtLeast(0L),
    maxUploadSpeedBytesPerSec = task.maxUploadSpeedBytesPerSec.coerceAtLeast(0L),
    maxConnections = task.maxConnections.coerceAtLeast(0),
    scheduledAtMs = task.scheduledAtMs.coerceAtLeast(0L),
    checksumVerified = null,
    scanResult = null,
    expectedChecksum = null,
    errorMessage = null
)

internal fun QueueManagerRepository.importTasks(tasks: List<DownloadTask>, paths: ImportedPaths): ImportedTasks {
    val added = ArrayList<DownloadTask>()
    var skipped = 0
    val known = (allTasksFlow.value.map { it.id } + pendingTasks.value.keys).toHashSet()
    for (incoming in tasks) {
        val task = cleanImported(incoming, paths)
        if (!known.add(task.id)) {
            skipped++
            continue
        }
        when (task.engineType) {
            EngineType.HTTP_DIRECT -> httpEngine.enqueueTask(task)
            EngineType.BITTORRENT -> torrentEngine.enqueueTask(task)
            EngineType.MEDIA_STREAM -> mediaEngine.enqueueTask(task)
        }
        added += task
    }
    return ImportedTasks(added, skipped)
}
