package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.i18n.start.localizeStep
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.core.util.formatLocalSchedule
import com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs
import com.michelle.raouf.fetch_jet.domain.model.displayChunks
import com.michelle.raouf.fetch_jet.domain.model.DownloadChunk
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.domain.model.canSendToDevice
import com.michelle.raouf.fetch_jet.domain.model.etaSeconds
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChipDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ConnectionDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.DetailActionCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.DetailsDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.FileRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.LinkDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.PieceDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.PieceStateCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.ToneCode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.tbib.kmp_localization.LocalizationManager
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class DetailsInput(val task: DownloadTask?, val unit: String, val language: String, val tick: Long)

internal fun formatEta(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return when {
        s < 60L -> "${s}s"
        s < 3600L -> "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
        s < 100L * 3600L -> "${s / 3600}h ${((s % 3600) / 60).toString().padStart(2, '0')}m"
        else -> "> 99h"
    }
}

internal fun formatAddedTime(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val offset = localUtcOffsetMs()
    val dayMs = 24L * 60 * 60 * 1000
    val local = epochMs + offset
    val day = local.floorDiv(dayMs)
    val today = (currentEpochMs() + offset).floorDiv(dayMs)
    val minutes = local.mod(dayMs) / 60_000L
    val h24 = (minutes / 60).toInt()
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    val clock = "$h12:${(minutes % 60).toString().padStart(2, '0')} ${if (h24 < 12) "AM" else "PM"}"
    return when (today - day) {
        0L -> LocalizationManager.Strings.detailsAddedToday(clock)
        1L -> LocalizationManager.Strings.detailsAddedYesterday(clock)
        else -> {
            val z = day + 719468
            val era = z.floorDiv(146097)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = if (mp < 10) mp + 3 else mp - 9
            val y = yoe + era * 400 + if (m <= 2) 1 else 0
            "${d.toString().padStart(2, '0')}/${m.toString().padStart(2, '0')}/$y"
        }
    }
}

internal fun taskFullPath(task: DownloadTask): String {
    val raw = if (task.fileName.isNotBlank()) {
        val dest = task.destinationPath.trimEnd('/', '\\')
        val sep = if (dest.contains('\\')) "\\" else "/"
        "$dest$sep${task.fileName}"
    } else {
        task.destinationPath
    }
    return decodeUnicodeText(raw).trim()
}

internal fun Presenter.startDetails() {
    val tick = MutableStateFlow(0L)
    val selected = combine(vm.uiState, local) { state, ui ->
        if (state.selectedTab == DashboardTab.SETTINGS) null
        else ui.selectedTaskId?.let { id -> state.allTasks.firstOrNull { it.id == id } }
    }.distinctUntilChanged().throttleLatest(HOME_PERIOD_MS)
    val unit = combine(vm.uiState, AppLanguage.current) { state, language -> state.speedDisplayUnit to language }
        .distinctUntilChanged()
    scope.launch {
        combine(selected, unit, tick) { task, (speedUnit, language), now -> DetailsInput(task, speedUnit, language, now) }
            .collect { input ->
                val task = input.task
                if (task == null) {
                    details.publish(DetailsDto())
                    detailPieces.publish(emptyList())
                    detailConnections.publish(emptyList())
                    detailFiles.publish(emptyList())
                } else {
                    publishDetails(task, input.unit)
                }
            }
    }
    scope.launch {
        while (true) {
            delay(1000)
            val current = details.current
            if (current != null && current.open && (current.postShown || current.speedIcon.isNotEmpty())) {
                tick.update { it + 1 }
            }
        }
    }
}

private fun Presenter.publishDetails(task: DownloadTask, unit: String) {
    val strings = LocalizationManager.Strings
    val completed = task.status == DownloadStatus.COMPLETED
    val name = taskDisplayName(task)
    val fullPath = taskFullPath(task)

    val chips = ArrayList<ChipDto>(3)
    chips += when (task.engineType) {
        EngineType.MEDIA_STREAM -> ChipDto(strings.detailsChipMedia, ToneCode.MEDIA.ordinal)
        EngineType.BITTORRENT -> ChipDto(strings.detailsChipTorrent, ToneCode.TORRENT.ordinal)
        EngineType.HTTP_DIRECT -> ChipDto(strings.detailsChipFile, ToneCode.PRIMARY.ordinal)
    }
    if (!completed) {
        when (task.canResume()) {
            true -> chips += ChipDto(strings.detailsChipResumable, ToneCode.COMPLETED.ordinal)
            false -> chips += ChipDto(strings.detailsChipNoResume, ToneCode.PAUSED.ordinal)
            null -> Unit
        }
    }
    if (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank()) {
        chips += ChipDto(
            strings.detailsClipChip(task.clipStart ?: "00:00", task.clipEnd, task.clipByCutting && !completed),
            ToneCode.MUTED.ordinal,
        )
    }

    val running = task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING
    val stepLine = if (!task.currentStepText.isNullOrBlank() && running) localizeStep(task.currentStepText.orEmpty()) else ""

    val postLabel = task.postProcessLabel
    val postShown = postLabel != null && running
    var postIcon = ""
    var postRight = ""
    if (postShown && postLabel != null) {
        val start = if (task.postProcessStartedAtMs > 0L) task.postProcessStartedAtMs else currentEpochMs()
        val elapsed = ((currentEpochMs() - start) / 1000L).coerceAtLeast(0L)
        val m = elapsed / 60
        val sec = elapsed % 60
        val elapsedText = if (m >= 60) "${m / 60}:${(m % 60).toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
        else "$m:${sec.toString().padStart(2, '0')}"
        postIcon = when {
            postLabel.startsWith("Merging", ignoreCase = true) -> "🔗"
            postLabel.startsWith("Saving", ignoreCase = true) -> "📁"
            postLabel.contains("MP3", ignoreCase = true) -> "🎵"
            else -> "⚙️"
        }
        postRight = (if (task.postProcessProgress >= 0f) "${(task.postProcessProgress * 100).toInt()}% · " else "") + elapsedText
    }

    val totalIsEstimate = task.status == DownloadStatus.DOWNLOADING &&
        task.approximateTotalBytes > 0L && task.approximateTotalBytes == task.totalBytes
    val sectionClipRunning = task.status == DownloadStatus.DOWNLOADING && !task.clipByCutting &&
        (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank())
    val bytesText = if (sectionClipRunning) formatBytes(task.downloadedBytes)
    else "${formatBytes(task.downloadedBytes)} / ${
        if (task.totalBytes > 0) (if (totalIsEstimate) "~" else "") + formatBytes(task.totalBytes) else strings.detailsUnknownSize
    }"

    var speedIcon = ""
    val speedText = if (task.status == DownloadStatus.DOWNLOADING) {
        if (task.postProcessLabel != null) {
            "${task.postProcessLabel?.let { localizeStep(it) }}..."
        } else if (task.currentStepText?.contains("Merging", ignoreCase = true) == true) {
            strings.detailsMergingAudioVideo
        } else if (task.engineType == EngineType.BITTORRENT && task.disableUpload) {
            strings.detailsTorrentSpeedUploadDisabled(formatSpeed(task.downloadSpeedBytesPerSec, unit))
        } else if (task.engineType == EngineType.BITTORRENT) {
            strings.detailsTorrentSpeed(formatSpeed(task.downloadSpeedBytesPerSec, unit), formatSpeed(task.uploadSpeedBytesPerSec, unit))
        } else {
            formatSpeed(task.downloadSpeedBytesPerSec, unit)
        }
    } else if (task.status == DownloadStatus.CONNECTING) {
        strings.detailsConnecting
    } else if (task.waitingInQueue) {
        if (task.scheduledAtMs > currentEpochMs()) {
            speedIcon = "🕑"
            strings.detailsStartsAt(formatLocalSchedule(task.scheduledAtMs))
        } else if (task.queuePosition > 0) {
            speedIcon = "⏳"
            strings.detailsWaitingInQueuePosition(task.queuePosition)
        } else {
            speedIcon = "⏳"
            strings.detailsWaitingInQueue
        }
    } else {
        when (task.status) {
            DownloadStatus.COMPLETED -> strings.detailsStatusCompleted
            DownloadStatus.PAUSED -> strings.detailsStatusPaused
            DownloadStatus.ERROR -> strings.detailsStatusFailed
            DownloadStatus.QUEUED -> strings.detailsStatusQueued
            else -> strings.detailsStatusNotStarted
        }
    }
    val speedTone = when (task.status) {
        DownloadStatus.DOWNLOADING -> ToneCode.PRIMARY
        DownloadStatus.COMPLETED -> ToneCode.COMPLETED
        DownloadStatus.PAUSED -> ToneCode.PAUSED
        DownloadStatus.ERROR -> ToneCode.ERROR
        else -> ToneCode.MUTED
    }
    val eta = task.etaSeconds()

    val links = listOf(
        strings.detailsPage to (task.requestHeaders["Referer"] ?: task.originalUrl.orEmpty()),
    ).filter { (_, link) -> link.isNotBlank() }.distinctBy { (_, link) -> link }.map { (label, link) -> LinkDto(label, link) }

    val checksumShown = !task.expectedChecksum.isNullOrBlank()
    val canFolder = pathResolver.isOpenFolderSupported()
    var primary = DetailActionCode.NONE
    var primaryLabel = ""
    var secondary = DetailActionCode.NONE
    var secondaryLabel = ""
    when {
        completed -> {
            primary = DetailActionCode.OPEN_FILE
            primaryLabel = strings.detailsOpenFile
            if (canFolder) {
                secondary = DetailActionCode.OPEN_FOLDER
                secondaryLabel = strings.detailsOpenFolder
            }
        }

        running -> {
            primary = DetailActionCode.PAUSE
            primaryLabel = strings.pause
            if (canFolder) {
                secondary = DetailActionCode.OPEN_FOLDER
                secondaryLabel = strings.detailsOpenFolder
            }
        }

        task.waitingInQueue -> {
            primary = DetailActionCode.START_NOW
            primaryLabel = strings.detailsStartNow
            secondary = DetailActionCode.PAUSE
            secondaryLabel = strings.pause
        }

        else -> {
            primary = DetailActionCode.RESUME
            primaryLabel = if (task.status == DownloadStatus.ERROR) strings.retry else strings.resume
            if (canFolder) {
                secondary = DetailActionCode.OPEN_FOLDER
                secondaryLabel = strings.detailsOpenFolder
            }
        }
    }

    val chunks = task.displayChunks()
    val total = chunks.maxOfOrNull { it.endByte + 1 }?.takeIf { it > 0 }
    val piecesShown = task.chunks.size > 1 && !completed && total != null
    val pills = ArrayList<ChipDto>(4)
    var piecesSummary = ""
    val pieces = ArrayList<PieceDto>(if (piecesShown) chunks.size else 0)
    val connections = ArrayList<ConnectionDto>(8)
    if (piecesShown && total != null) {
        val manySegments = chunks.size > 40
        val active = chunks.filter { it.isActive }
        val stalled = chunks.filter { !it.isActive && !it.isCompleted && it.note != null }
        val finished = chunks.count { it.isCompleted }
        pills += ChipDto(
            if (manySegments) strings.homeSegmentsCount(chunks.size) else strings.homePiecesCount(chunks.size),
            ToneCode.MUTED.ordinal,
        )
        if (active.isNotEmpty()) pills += ChipDto(strings.homeActiveCount(active.size), ToneCode.ACTIVE.ordinal, dot = true)
        if (stalled.isNotEmpty()) pills += ChipDto(strings.homeWaitingCount(stalled.size), ToneCode.PAUSED.ordinal)
        if (finished > 0) pills += ChipDto(strings.homeDoneCount(finished), ToneCode.COMPLETED.ordinal)
        for (c in chunks) pieces += pieceOf(c, total)
        if (manySegments) {
            piecesSummary = strings.homeSegmentsProgress(
                finished,
                chunks.size,
                if (active.isNotEmpty()) active.joinToString(", ") { "#${it.chunkIndex + 1}" } else null,
            )
        } else {
            for (c in (active + stalled).sortedBy { it.chunkIndex }) {
                val len = max(1L, c.endByte - c.startByte + 1)
                val frac = (c.downloadedBytes.toFloat() / len).coerceIn(0f, 1f)
                connections += ConnectionDto(
                    label = "#${c.chunkIndex + 1}",
                    text = if (c.isActive) formatSpeed(c.speedBytesPerSec, unit) else strings.homeRetryingNote(c.note),
                    percent = "${(frac * 100).toInt()}%",
                    fraction = frac,
                    active = c.isActive,
                )
            }
        }
    }

    val files = task.torrentFiles.map { file ->
        FileRowDto(
            index = file.index,
            path = file.path,
            pathRtl = startsRightToLeft(file.path),
            sizeText = if (file.isCompleted) formatBytes(file.size)
            else if (file.downloadedBytes > 0) "${formatBytes(file.downloadedBytes)} / ${formatBytes(file.size)}"
            else formatBytes(file.size),
            selected = file.isSelected,
            completed = file.isCompleted,
            priority = file.priority,
            priorityLabel = when (file.priority) {
                1 -> strings.detailsPriorityHigh
                -1 -> strings.detailsPriorityLow
                else -> strings.detailsPriorityNormal
            },
        )
    }

    detailPieces.publish(pieces)
    detailConnections.publish(connections)
    detailFiles.publish(files)
    details.publish(
        DetailsDto(
            open = true,
            id = task.id,
            name = name,
            nameRtl = startsRightToLeft(name),
            nameHan = hasHanCharacters(name),
            chips = chips,
            hasFiles = task.torrentFiles.isNotEmpty(),
            filesTitle = if (task.torrentFiles.isEmpty()) "" else strings.detailsTorrentFiles(
                task.torrentFiles.count { it.isSelected }, task.torrentFiles.size
            ),
            percent = "${(task.progress.coerceIn(0f, 1f) * 100).toInt()}%",
            statusWord = when (task.status) {
                DownloadStatus.COMPLETED -> strings.detailsStatusCompleted
                DownloadStatus.PAUSED -> strings.detailsStatusPaused
                DownloadStatus.ERROR -> strings.detailsStatusFailed
                else -> if (task.waitingInQueue) strings.detailsStatusWaiting else ""
            },
            stepLine = stepLine,
            progress = task.progress.coerceIn(0f, 1f).let { if (it.isNaN()) 0f else it },
            postShown = postShown,
            postIcon = postIcon,
            postLabel = if (postShown && postLabel != null) localizeStep(postLabel) else "",
            postRight = postRight,
            postProgress = task.postProcessProgress.coerceIn(0f, 1f),
            postIndeterminate = task.postProcessProgress < 0f,
            downloaded = bytesText,
            speedLabel = if (task.status == DownloadStatus.DOWNLOADING) strings.detailsSpeed else strings.detailsState,
            speedIcon = speedIcon,
            speedValue = speedText,
            speedTone = speedTone.ordinal,
            timeLeft = if (eta != null) formatEta(eta) else "—",
            added = formatAddedTime(task.createdAtEpochMs),
            piecesShown = piecesShown,
            piecePills = pills,
            piecesSummary = piecesSummary,
            error = if (task.status == DownloadStatus.ERROR && !task.errorMessage.isNullOrBlank())
                localizeStep(task.errorMessage.orEmpty()) else "",
            fullPath = fullPath,
            fullPathRtl = startsRightToLeft(fullPath),
            links = links,
            checksumShown = checksumShown,
            checksumIcon = if (!checksumShown) "" else when (task.checksumVerified) {
                true -> "✅"
                false -> "❌"
                null -> "⏳"
            },
            checksumText = if (!checksumShown) "" else when (task.checksumVerified) {
                true -> strings.checksumVerified
                false -> strings.checksumMismatch
                null -> strings.checksumChecking
            },
            checksumTone = when (task.checksumVerified) {
                true -> ToneCode.CHECKSUM_OK.ordinal
                false -> ToneCode.ERROR.ordinal
                null -> ToneCode.MUTED.ordinal
            },
            primaryAction = primary.ordinal,
            primaryLabel = primaryLabel,
            secondaryAction = secondary.ordinal,
            secondaryLabel = secondaryLabel,
            canShare = task.canSendToDevice(),
            removeLabel = if (completed) strings.detailsRemoveFromList else strings.detailsCancelDownload,
        )
    )
}

private fun pieceOf(c: DownloadChunk, total: Long): PieceDto {
    val len = max(1L, c.endByte - c.startByte + 1)
    val fill = (c.downloadedBytes.toFloat() / len).coerceIn(0f, 1f)
    val state = when {
        c.isCompleted -> PieceStateCode.COMPLETED
        c.isActive -> PieceStateCode.ACTIVE
        c.note != null -> PieceStateCode.WAITING
        else -> PieceStateCode.IDLE
    }
    return PieceDto(
        start = c.startByte.toFloat() / total,
        end = (c.endByte + 1).toFloat() / total,
        fill = fill,
        state = state.ordinal,
    )
}

internal fun Presenter.detailsAction(code: Int, primary: Boolean) {
    val id = local.value.selectedTaskId ?: return
    val task = task(id) ?: return
    when (DetailActionCode.entries.getOrNull(code)) {
        DetailActionCode.OPEN_FILE -> openDownloadedFile(taskFullPath(task), id)
        DetailActionCode.OPEN_FOLDER -> {
            if (headless) return
            scope.launch(Dispatchers.IO) { pathResolver.openFileLocation(taskFullPath(task)) }
        }

        DetailActionCode.PAUSE ->
            if (primary && task.canResume() == false) local.update { it.copy(restartPauseTaskId = id) }
            else vm.onEvent(DashboardUiEvent.OnPauseClicked(id))

        DetailActionCode.START_NOW, DetailActionCode.RESUME -> vm.onEvent(DashboardUiEvent.OnResumeClicked(id))
        DetailActionCode.NONE, null -> Unit
    }
}
