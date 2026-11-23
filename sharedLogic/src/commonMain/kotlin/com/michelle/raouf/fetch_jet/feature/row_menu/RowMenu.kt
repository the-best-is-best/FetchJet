package com.michelle.raouf.fetch_jet.feature.row_menu

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.AppLocalization
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.canSendToDevice
import com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveTasks
import com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
import com.michelle.raouf.fetch_jet.feature.media_extractor.canAddSubtitles

enum class RowMenuEntry(val danger: Boolean = false, val dividerBefore: Boolean = false) {
    OPEN,
    OPEN_FOLDER,
    PAUSE,
    RESUME,
    EXTRACT,
    COMPRESS,
    SUBTITLES,
    SEND_TO_DEVICE(dividerBefore = true),
    COPY_LINK,
    SELECT,
    DETAILS,
    DELETE(danger = true, dividerBefore = true),
}

class RowMenuOptions(
    val canSend: Boolean = true,
    val canCompress: Boolean = true,
    val selectEntry: Boolean = false,
)

object RowMenu {
    private val PAUSABLE = setOf(DownloadStatus.QUEUED, DownloadStatus.CONNECTING, DownloadStatus.DOWNLOADING)
    private val RESUMABLE = setOf(DownloadStatus.IDLE, DownloadStatus.PAUSED, DownloadStatus.ERROR)

    fun entries(task: DownloadTask, options: RowMenuOptions = RowMenuOptions()): List<RowMenuEntry> = buildList {
        val done = task.status == DownloadStatus.COMPLETED
        val busy = task.postProcessLabel != null
        if (done && !busy) add(RowMenuEntry.OPEN)
        if (task.destinationPath.isNotBlank()) add(RowMenuEntry.OPEN_FOLDER)
        if (task.status in PAUSABLE && !busy) add(RowMenuEntry.PAUSE)
        if (task.status in RESUMABLE) add(RowMenuEntry.RESUME)
        if (done && ArchiveTasks.canExtract(task)) add(RowMenuEntry.EXTRACT)
        if (options.canCompress && !busy && AudioCompress.canCompress(task)) add(RowMenuEntry.COMPRESS)
        if (!busy && task.canAddSubtitles()) add(RowMenuEntry.SUBTITLES)
        if (options.canSend && task.canSendToDevice()) add(RowMenuEntry.SEND_TO_DEVICE)
        if (task.url.isNotBlank()) add(RowMenuEntry.COPY_LINK)
        if (options.selectEntry) add(RowMenuEntry.SELECT)
        add(RowMenuEntry.DETAILS)
        add(RowMenuEntry.DELETE)
    }

    fun label(entry: RowMenuEntry, s: AppLocalization): String = when (entry) {
        RowMenuEntry.OPEN -> s.rowMenuOpen
        RowMenuEntry.OPEN_FOLDER -> s.rowMenuOpenFolder
        RowMenuEntry.PAUSE -> s.rowMenuPause
        RowMenuEntry.RESUME -> s.rowMenuResume
        RowMenuEntry.EXTRACT -> s.rowMenuExtract
        RowMenuEntry.COMPRESS -> s.rowMenuCompress
        RowMenuEntry.SUBTITLES -> s.subtitlesAddButton
        RowMenuEntry.SEND_TO_DEVICE -> s.rowMenuSendToDevice
        RowMenuEntry.COPY_LINK -> s.rowMenuCopyLink
        RowMenuEntry.SELECT -> s.rowMenuSelect
        RowMenuEntry.DETAILS -> s.rowMenuDetails
        RowMenuEntry.DELETE -> s.rowMenuDelete
    }
}
