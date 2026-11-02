package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
        _uiState.update { it.copy(errorMessage = LocalizationManager.Strings.sysEnterValidUrlOrMagnet) }
        return
    }

    currentState.inspectedPlaylist?.let { playlist ->
        addPlaylist(currentState, playlist)
        return
    }

    if (currentState.linkList.isNotEmpty()) {
        addLinkList(currentState)
        return
    }

    if (currentState.detectedEngine == EngineType.MEDIA_STREAM) {
        if (currentState.inspectMediaError != null) {
            _uiState.update { it.copy(errorMessage = LocalizationManager.Strings.sysCannotStartVideoInspectionFailed) }
            return
        }
    }

    if (currentState.detectedEngine == EngineType.BITTORRENT) {
        if (currentState.inspectError != null || currentState.inspectedTorrentFiles.isEmpty() || !currentState.inspectedTorrentFiles.any { it.isSelected }) {
            _uiState.update { it.copy(errorMessage = LocalizationManager.Strings.sysCannotStartTorrentInspectionFailed) }
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
            _uiState.update { it.copy(errorMessage = LocalizationManager.Strings.sysClipEndExceedsDuration) }
            return
        }
        if (clipEndSec > 0L && clipStartSec >= clipEndSec) {
            _uiState.update { it.copy(errorMessage = LocalizationManager.Strings.sysClipStartAfterEnd) }
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
                    linkList = emptyList(),

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
            _uiEffect.emit(
                DashboardUiEffect.ShowToast(
                    LocalizationManager.Strings.sysStartedDownload(
                        task.fileName.ifBlank { task.url })
                )
            )
            _uiState.update { it.copy(scrollToTopSignal = it.scrollToTopSignal + 1) }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message
                        ?: LocalizationManager.Strings.sysFailedToQueueDownload
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
                linkList = emptyList(),

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
                errorMessage = if (failed > 0) LocalizationManager.Strings.sysQualitiesNotAdded(
                    failed
                ) else null
            )
        }
        _uiEffect.emit(
            DashboardUiEffect.ShowToast(
                LocalizationManager.Strings.sysAddedQualityDownloads(
                    added,
                    title
                )
            )
        )
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
        _uiState.update { it.copy(errorMessage = LocalizationManager.Strings.sysSelectAtLeastOnePlaylistVideo) }
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
                linkList = emptyList(),

                fileNameOverride = "",

                suggestedFileName = "",
                isLoading = false,
                isAddDialogVisible = false,
                inspectedPlaylist = null,
                inspectedMediaQualities = emptyList(),
                inspectedMediaTitle = null,
                scheduleStartMs = 0L,
                selectedConnections = 0,
                errorMessage = if (failed > 0) LocalizationManager.Strings.sysVideosNotAdded(failed) else null
            )
        }
        singleVideoUrl = null
        _uiEffect.emit(
            DashboardUiEffect.ShowToast(
                LocalizationManager.Strings.sysAddedPlaylistVideos(
                    added,
                    playlist.title
                )
            )
        )
    }
}

internal fun extractLinks(text: String): List<String> =
    Regex("""(?:https?://|magnet:\?)[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ':', ')', ']', '}', '!', '?') }
        .filter { it.length > 10 }
        .distinct()
        .take(500)
        .toList()

internal fun DashboardViewModel.engineForListedLink(url: String): EngineType {
    if (url.startsWith("magnet:", ignoreCase = true)) {
        return if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.torrent) EngineType.BITTORRENT
        else EngineType.HTTP_DIRECT
    }
    val engine = engineForDirectFile(url)
    return when {
        engine == EngineType.MEDIA_STREAM && !com.michelle.raouf.fetch_jet.core.edition.AppFeatures.mediaExtractor ->
            EngineType.HTTP_DIRECT

        engine == EngineType.BITTORRENT && !com.michelle.raouf.fetch_jet.core.edition.AppFeatures.torrent ->
            EngineType.HTTP_DIRECT

        else -> engine
    }
}

internal fun DashboardViewModel.addLinkList(state: DashboardUiState) {
    val selected = state.linkList.filter { it.isSelected }
    if (selected.any { it.isBeingInspected || it.needsRetry }) return
    if (selected.isEmpty()) {
        _uiState.update { it.copy(errorMessage = LocalizationManager.Strings.sysSelectAtLeastOneLink) }
        return
    }
    val destDir =
        state.destinationPath.ifBlank { queueRepository.pathResolver.getDownloadsDirectory() }
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    scope.launch {
        val existing = queueRepository.allTasksFlow.value
        var added = 0
        var skipped = 0
        for (entry in selected) {
            if (existing.any { isSameDownload(it, entry.url) }) {
                skipped++
                continue
            }
            if (entry.torrentFiles.isNotEmpty() && entry.torrentFiles.none { it.isSelected }) {
                skipped++
                continue
            }
            val quality = entry.mediaQualities.firstOrNull { it.isSelected }
            val videoName = if (quality != null && !entry.mediaTitle.isNullOrBlank())
                queueRepository.pathResolver.sanitizeFileName(entry.mediaTitle) + "." + quality.format.ifBlank { "mp4" }
            else null
            queueRepository.enqueueTask(
                url = entry.url,
                customDestinationPath = destDir,
                customFileName = videoName,
                mediaQualities = entry.mediaQualities,
                requestHeaders = if (quality != null) headersForLink(entry.url) else emptyMap(),
                disableUpload = state.disableTorrentUpload,
                torrentFiles = entry.torrentFiles,
                torrentName = entry.torrentName,
                webSeedUrls = entry.webSeeds,
                maxDownloadSpeedBytesPerSec = state.selectedDownloadSpeedLimitBytes,
                maxUploadSpeedBytesPerSec = state.selectedUploadSpeedLimitBytes,
                forcedEngineType = entry.engine,
                scheduledAtMs = state.scheduleStartMs
            ).onSuccess { added++ }.onFailure { skipped++ }
        }
        _uiState.update {
            it.copy(
                inputUrl = "",
                linkList = emptyList(),
                fileNameOverride = "",
                suggestedFileName = "",
                isLoading = false,
                isAddDialogVisible = false,
                scheduleStartMs = 0L,
                selectedConnections = 0,
                scrollToTopSignal = it.scrollToTopSignal + 1
            )
        }
        _uiEffect.emit(
            DashboardUiEffect.ShowToast(
                LocalizationManager.Strings.sysAddedLinkDownloads(added, skipped)
            )
        )
    }
}

internal fun DashboardViewModel.updateListedTorrentFiles(
    linkIndex: Int,
    change: (List<com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry>) -> List<com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry>
) {
    _uiState.update { st ->
        st.copy(linkList = st.linkList.mapIndexed { i, entry ->
            if (i == linkIndex) entry.copy(torrentFiles = change(entry.torrentFiles)) else entry
        })
    }
}

/**
 * Video links in the list: their title and qualities are read one at a time, so each row can show
 * the real name and offer a quality. A video that cannot be read must be retried (or unchecked)
 * before the list can start.
 */
internal fun DashboardViewModel.inspectListedVideos() {
    val urls = _uiState.value.linkList
        .filter { it.engine == EngineType.MEDIA_STREAM && it.mediaQualities.isEmpty() && !it.isInspectingMedia }
        .map { it.url }
    if (urls.isEmpty()) return
    fun update(url: String, change: (LinkListEntry) -> LinkListEntry) = _uiState.update { st ->
        st.copy(linkList = st.linkList.map { if (it.url == url) change(it) else it })
    }
    urls.forEach { url -> update(url) { it.copy(isInspectingMedia = true, mediaInspectFailed = false) } }
    scope.launch {
        for (url in urls) {
            if (_uiState.value.linkList.none { it.url == url }) continue
            val qualities = try {
                withTimeoutOrNull(120_000L) {
                    queueRepository.inspectMediaQualities(url, headersForLink(url))
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            update(url) {
                it.copy(
                    isInspectingMedia = false,
                    mediaInspectFailed = qualities.isEmpty(),
                    mediaTitle = qualities.firstOrNull { q -> !q.videoTitle.isNullOrBlank() }?.videoTitle,
                    mediaQualities = qualities
                )
            }
        }
    }
}

internal fun DashboardViewModel.inspectListedTorrents() {
    val urls = _uiState.value.linkList
        .filter { it.engine == EngineType.BITTORRENT && it.torrentFiles.isEmpty() && !it.isInspecting }
        .map { it.url }
    if (urls.isEmpty()) return
    fun update(url: String, change: (LinkListEntry) -> LinkListEntry) = _uiState.update { st ->
        st.copy(linkList = st.linkList.map { if (it.url == url) change(it) else it })
    }
    urls.forEach { url -> update(url) { it.copy(isInspecting = true, inspectError = null) } }
    scope.launch {
        for (url in urls) {
            if (_uiState.value.linkList.none { it.url == url }) continue
            queueRepository.torrentEngine.fetchAndParseTorrent(url)
                .onSuccess { parsed ->
                    update(url) {
                        it.copy(
                            isInspecting = false,
                            torrentName = parsed.name,
                            torrentFiles = parsed.files,
                            webSeeds = parsed.webSeeds
                        )
                    }
                }
                .onFailure { err ->
                    update(url) {
                        it.copy(
                            isInspecting = false,
                            inspectError = err.message
                                ?: LocalizationManager.Strings.sysCouldNotReadTorrent
                        )
                    }
                }
        }
    }
}
