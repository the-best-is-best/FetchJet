package com.michelle.raouf.fetch_jet.feature.archive_extract

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType

object ArchiveTasks {
    fun isArchive(task: DownloadTask): Boolean =
        task.status == DownloadStatus.COMPLETED &&
            task.engineType != EngineType.BITTORRENT &&
            task.fileName.isNotBlank() &&
            ArchiveExtractor.kindOf(task.fileName) != null

    fun canExtract(task: DownloadTask): Boolean = isArchive(task) && task.postProcessLabel == null
}
