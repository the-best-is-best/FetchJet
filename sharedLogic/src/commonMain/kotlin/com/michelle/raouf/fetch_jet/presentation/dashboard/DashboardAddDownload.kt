package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

internal fun DashboardViewModel.addDownload(
    forceOverwrite: Boolean = false,
    overrideFileName: String? = null,
    skipDuplicateCheck: Boolean = false
) {
    val currentState = _uiState.value
    val url = currentState.inputUrl
    if (url.isBlank()) {
        _uiState.update { it.copy(errorMessage = "Please enter a valid URL or Magnet link") }
        return
    }

    currentState.inspectedPlaylist?.let { playlist ->
        addPlaylist(currentState, playlist)
        return
    }

    if (currentState.detectedEngine == EngineType.MEDIA_STREAM) {
        if (currentState.inspectMediaError != null) {
            _uiState.update { it.copy(errorMessage = "Cannot start download: Video inspection failed.") }
            return
        }
    }

    if (currentState.detectedEngine == EngineType.BITTORRENT) {
        if (currentState.inspectError != null || currentState.inspectedTorrentFiles.isEmpty() || !currentState.inspectedTorrentFiles.any { it.isSelected }) {
            _uiState.update { it.copy(errorMessage = "Cannot start download: Torrent inspection failed or no files selected.") }
            return
        }
    }

    val isClippingActive =
        currentState.isClipRangeEnabled && currentState.detectedEngine == EngineType.MEDIA_STREAM
    val clipStartSec =
        if (isClippingActive && currentState.clipStartTime.isNotBlank()) parseHhMmToSeconds(
            currentState.clipStartTime
        ) else 0L
    val clipEndSec =
        if (isClippingActive && currentState.clipEndTime.isNotBlank()) parseHhMmToSeconds(
            currentState.clipEndTime
        ) else currentState.mediaDurationSeconds
    val mediaDur = currentState.mediaDurationSeconds
    val fullEndHhMm = if (mediaDur > 0L) formatSecondsToHhMm(mediaDur) else ""
    val isClipEndAtEnd = currentState.clipEndTime.isBlank() ||
            (fullEndHhMm.isNotBlank() && currentState.clipEndTime == fullEndHhMm) ||
            (mediaDur > 0L && clipEndSec >= mediaDur)

    if (isClippingActive) {
        if (mediaDur > 0L && !isClipEndAtEnd && clipEndSec > mediaDur) {
            _uiState.update { it.copy(errorMessage = "Clip end time exceeds video duration.") }
            return
        }
        if (clipEndSec > 0L && clipStartSec >= clipEndSec) {
            _uiState.update { it.copy(errorMessage = "Clip start time must be before end time.") }
            return
        }
    }

    val isActualSubClip =
        isClippingActive && (clipStartSec > 0L || (!isClipEndAtEnd && clipEndSec in 1 until mediaDur) || (mediaDur == 0L && clipEndSec > 0L))

    val allTasks = queueRepository.allTasksFlow.value

    // 1. Check for duplicate in queue
    if (!skipDuplicateCheck) {
        val duplicate = allTasks.find { isSameDownload(it, url) }
        if (duplicate != null) {
            _uiState.update { it.copy(duplicateTaskAlert = duplicate) }
            return
        }
    }

    // Several qualities ticked: each one is its own download ("Title (720p).mp4", ...).
    val selectedQualities = currentState.inspectedMediaQualities.filter { it.isSelected }
    if (currentState.detectedEngine == EngineType.MEDIA_STREAM && selectedQualities.size > 1) {
        addSeveralQualities(
            state = currentState,
            url = url,
            qualities = selectedQualities,
            clipStart = if (isActualSubClip && currentState.clipStartTime.isNotBlank()) normalizeHhMmForYtDlp(
                currentState.clipStartTime
            ) else null,
            clipEnd = if (isActualSubClip && !isClipEndAtEnd && currentState.clipEndTime.isNotBlank()) normalizeHhMmForYtDlp(
                currentState.clipEndTime
            ) else null,
            clipByCutting = isActualSubClip && currentState.clipFastMode
        )
        return
    }

    val selectedQuality = currentState.inspectedMediaQualities.firstOrNull { it.isSelected }
    val videoTitle = currentState.inspectedMediaTitle ?: selectedQuality?.videoTitle
    // The name typed in the dialog wins; its extension is kept when the user left it out.
    val typedName =
        currentState.fileNameOverride.trim().takeIf { it.isNotBlank() }?.let { typed ->
            val ext = currentState.suggestedFileName.substringAfterLast('.', "")
            if (!typed.contains('.') && ext.isNotBlank()) "$typed.$ext" else typed
        }
    val customFileName = overrideFileName ?: typedName ?: if (!videoTitle.isNullOrBlank()) {
        val ext = selectedQuality?.format ?: "mp4"
        val sanitized = queueRepository.pathResolver.sanitizeFileName(videoTitle)
        if (sanitized.endsWith(".$ext", ignoreCase = true)) sanitized else "$sanitized.$ext"
    } else null

    val destDir =
        currentState.destinationPath.ifBlank { queueRepository.pathResolver.getDownloadsDirectory() }
    val targetName = resolveTargetFileName(currentState, url, customFileName)

    // 2. Check if file already exists on disk
    if (!forceOverwrite) {
        val targetPath = "$destDir/$targetName".toPath()
        if (FileSystem.SYSTEM.exists(targetPath)) {
            val suggested = getUniqueFileName(destDir, targetName)
            _uiState.update {
                it.copy(
                    fileConflictAlert = FileConflictData(
                        fileName = targetName,
                        destinationPath = destDir,
                        suggestedNewName = suggested
                    )
                )
            }
            return
        }
    }

    scope.launch {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        queueRepository.enqueueTask(
            url = url,
            customDestinationPath = currentState.destinationPath,
            // A synthesized placeholder ("download_123456") must not be forced on the engine:
            // left null, the HTTP engine names the file from Content-Disposition / final URL.
            customFileName = customFileName
                ?: targetName.takeUnless {
                    currentState.detectedEngine == EngineType.HTTP_DIRECT && it.startsWith(
                        "download_"
                    ) && !it.contains('.')
                },
            disableUpload = currentState.disableTorrentUpload,
            torrentFiles = currentState.inspectedTorrentFiles,
            mediaQualities = currentState.inspectedMediaQualities,
            torrentName = currentState.inspectedTorrentName,
            webSeedUrls = currentState.inspectedTorrentWebSeeds,
            maxDownloadSpeedBytesPerSec = currentState.selectedDownloadSpeedLimitBytes,
            maxUploadSpeedBytesPerSec = currentState.selectedUploadSpeedLimitBytes,
            forcedEngineType = currentState.detectedEngine,
            clipStart = if (isActualSubClip && currentState.clipStartTime.isNotBlank()) normalizeHhMmForYtDlp(
                currentState.clipStartTime
            ) else null,
            clipEnd = if (isActualSubClip && !isClipEndAtEnd && currentState.clipEndTime.isNotBlank()) normalizeHhMmForYtDlp(
                currentState.clipEndTime
            ) else null,
            clipByCutting = isActualSubClip && currentState.clipFastMode,
            requestHeaders = headersForLink(url),
            scheduledAtMs = currentState.scheduleStartMs,
            maxConnections = currentState.selectedConnections
        ).onSuccess { task ->
            pendingExternalUrl = null
            pendingExternalHeaders = emptyMap()
            pendingExternalFileName = null
            _uiState.update {
                it.copy(
                    inputUrl = "",

                    fileNameOverride = "",

                    suggestedFileName = "",
                    isLoading = false,
                    isAddDialogVisible = false,
                    inspectedTorrentFiles = emptyList(),
                    inspectedTorrentName = null,
                    inspectedMediaQualities = emptyList(),
                    inspectedPlaylist = null,
                    inspectedMediaTitle = null,
                    duplicateTaskAlert = null,
                    fileConflictAlert = null,
                    clipStartTime = "",
                    clipEndTime = "",
                    isClipRangeEnabled = false,
                    clipFastMode = false,
                    scheduleStartMs = 0L,
                    selectedConnections = 0,
                    mediaDurationSeconds = 0L
                )
            }
            _uiEffect.emit(DashboardUiEffect.ShowToast("Started download: ${task.fileName.ifBlank { task.url }}"))
            _uiState.update { it.copy(scrollToTopSignal = it.scrollToTopSignal + 1) }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to queue download"
                )
            }
        }
    }
}

/** One download per ticked quality of the same video, each with its own file name. */
internal fun DashboardViewModel.addSeveralQualities(
    state: DashboardUiState,
    url: String,
    qualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>,
    clipStart: String?,
    clipEnd: String?,
    clipByCutting: Boolean
) {
    val destDir =
        state.destinationPath.ifBlank { queueRepository.pathResolver.getDownloadsDirectory() }
    val title =
        state.inspectedMediaTitle ?: qualities.firstNotNullOfOrNull { it.videoTitle } ?: "video"
    val baseName = queueRepository.pathResolver.sanitizeFileName(title)
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    scope.launch {
        var added = 0
        var failed = 0
        val usedNames = mutableSetOf<String>()
        for (q in qualities) {
            // "1080p (Full HD - MP4)" -> "1080p"; "Audio Only (M4A)" -> "Audio"
            val tag = queueRepository.pathResolver.sanitizeFileName(
                q.qualityLabel.substringBefore(" (").replace("Audio Only", "Audio").trim()
                    .ifBlank { q.format }
            )
            val ext = q.format.ifBlank { "mp4" }
            var fileName = "$baseName ($tag).$ext"
            if (fileName.lowercase() in usedNames || FileSystem.SYSTEM.exists("$destDir/$fileName".toPath())) {
                var n = 2
                while ("$baseName ($tag) ($n).$ext".lowercase() in usedNames ||
                    FileSystem.SYSTEM.exists("$destDir/$baseName ($tag) ($n).$ext".toPath())
                ) n++
                fileName = "$baseName ($tag) ($n).$ext"
            }
            usedNames.add(fileName.lowercase())
            queueRepository.enqueueTask(
                url = url,
                customDestinationPath = destDir,
                customFileName = fileName,
                // only this quality selected for this download
                mediaQualities = state.inspectedMediaQualities.map { it.copy(isSelected = it.index == q.index && it.formatId == q.formatId) },
                maxDownloadSpeedBytesPerSec = state.selectedDownloadSpeedLimitBytes,
                forcedEngineType = EngineType.MEDIA_STREAM,
                clipStart = clipStart,
                clipEnd = clipEnd,
                clipByCutting = clipByCutting,
                requestHeaders = headersForLink(url),
                scheduledAtMs = state.scheduleStartMs
            ).onSuccess { added++ }.onFailure { failed++ }
        }
        pendingExternalUrl = null
        pendingExternalHeaders = emptyMap()
        pendingExternalFileName = null
        _uiState.update {
            it.copy(
                inputUrl = "",

                fileNameOverride = "",

                suggestedFileName = "",
                isLoading = false,
                isAddDialogVisible = false,
                inspectedMediaQualities = emptyList(),
                inspectedPlaylist = null,
                inspectedMediaTitle = null,
                clipStartTime = "",
                clipEndTime = "",
                isClipRangeEnabled = false,
                clipFastMode = false,
                scheduleStartMs = 0L,
                selectedConnections = 0,
                mediaDurationSeconds = 0L,
                errorMessage = if (failed > 0) "$failed quality(s) could not be added." else null
            )
        }
        _uiEffect.emit(DashboardUiEffect.ShowToast("Added $added downloads of \"$title\""))
    }
}

/**
 * Queues every selected video of a playlist as its own download, all with the chosen
 * quality. The download queue (Settings -> downloads at the same time) runs them a few at a
 * time; a schedule applies to all of them.
 */
internal fun DashboardViewModel.addPlaylist(
    state: DashboardUiState,
    playlist: com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist
) {
    val selected = playlist.entries.filter { it.isSelected }
    if (selected.isEmpty()) {
        _uiState.update { it.copy(errorMessage = "Select at least one video of the playlist.") }
        return
    }
    val quality = state.playlistQuality
    val destDir =
        state.destinationPath.ifBlank { queueRepository.pathResolver.getDownloadsDirectory() }
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    scope.launch {
        var added = 0
        var failed = 0
        val usedNames = mutableSetOf<String>()
        for (entry in selected) {
            val name = queueRepository.pathResolver.sanitizeFileName(entry.title)
            var fileName = if (name.endsWith(
                    ".${quality.extension}",
                    ignoreCase = true
                )
            ) name else "$name.${quality.extension}"
            // Two videos with the same title, or a file already in the folder: "Title (2).mp4".
            if (fileName.lowercase() in usedNames || FileSystem.SYSTEM.exists("$destDir/$fileName".toPath())) {
                val base = fileName.substringBeforeLast('.')
                val ext = fileName.substringAfterLast('.')
                var n = 2
                while ("$base ($n).$ext".lowercase() in usedNames || FileSystem.SYSTEM.exists("$destDir/$base ($n).$ext".toPath())) n++
                fileName = "$base ($n).$ext"
            }
            usedNames.add(fileName.lowercase())
            val pseudoQuality = com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry(
                index = 0,
                qualityLabel = quality.label,
                directUrl = entry.url,
                format = quality.extension,
                size = -1L,
                isSelected = true,
                formatId = quality.formatSelector,
                originalUrl = entry.url,
                videoTitle = entry.title,
                durationSeconds = entry.durationSeconds
            )
            queueRepository.enqueueTask(
                url = entry.url,
                customDestinationPath = destDir,
                customFileName = fileName,
                mediaQualities = listOf(pseudoQuality),
                maxDownloadSpeedBytesPerSec = state.selectedDownloadSpeedLimitBytes,
                forcedEngineType = EngineType.MEDIA_STREAM,
                scheduledAtMs = state.scheduleStartMs
            ).onSuccess { added++ }.onFailure { failed++ }
        }
        _uiState.update {
            it.copy(
                inputUrl = "",

                fileNameOverride = "",

                suggestedFileName = "",
                isLoading = false,
                isAddDialogVisible = false,
                inspectedPlaylist = null,
                inspectedMediaQualities = emptyList(),
                inspectedMediaTitle = null,
                scheduleStartMs = 0L,
                selectedConnections = 0,
                errorMessage = if (failed > 0) "$failed video(s) could not be added." else null
            )
        }
        singleVideoUrl = null
        _uiEffect.emit(DashboardUiEffect.ShowToast("Added $added videos from \"${playlist.title}\""))
    }
}
