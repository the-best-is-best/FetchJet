package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlinx.coroutines.flow.update

internal fun DashboardViewModel.handleAddDialogEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnEngineSelected -> {
            _uiState.update {
                it.copy(
                    detectedEngine = if (com.michelle.raouf.fetch_jet.feature.http_downloader.isFtpLink(it.inputUrl)) EngineType.HTTP_DIRECT else event.engine,
                    inspectedMediaQualities = emptyList(),
                    inspectedMediaTitle = null,
                    inspectedPlaylist = null,
                    inspectedTorrentFiles = emptyList(),
                    inspectError = null,
                    inspectMediaError = null
                )
            }
            refreshSuggestedFileName()
        }

        is DashboardUiEvent.OnClipboardLink -> {
            val s = _uiState.value
            // Not while a dialog is up, and never twice for the same text or a link already queued.
            // Already queued, or a dialog is up: say nothing. (The watcher itself only reports a
            // text once, so copying the same link again later asks again.)
            if (s.isAddDialogVisible || s.clipboardOfferUrl != null) return
            if (queueRepository.allTasksFlow.value.any { it.url == event.url || it.originalUrl == event.url }) return
            _uiState.update { it.copy(clipboardOfferUrl = event.url) }
        }

        DashboardUiEvent.OnClipboardOfferAccepted -> {
            val url = _uiState.value.clipboardOfferUrl ?: return
            _uiState.update { it.copy(clipboardOfferUrl = null) }
            onEvent(DashboardUiEvent.OnUrlChanged(url))
            onEvent(DashboardUiEvent.OnToggleAddDialog(true))
        }

        DashboardUiEvent.OnClipboardOfferDismissed -> _uiState.update {
            it.copy(
                clipboardOfferUrl = null
            )
        }

        is DashboardUiEvent.OnFileNameChanged -> {
            _uiState.update { it.copy(fileNameOverride = event.name) }
        }

        is DashboardUiEvent.OnChecksumChanged -> {
            _uiState.update { it.copy(checksumInput = event.value) }
        }

        DashboardUiEvent.OnTestLinkClicked -> testLink()

        is DashboardUiEvent.OnUrlChanged -> {
            val links = extractLinks(event.url)
            val inputUrl = links.singleOrNull()
                ?.takeIf {
                    event.url.trim().any { c -> c.isWhitespace() } &&
                            event.url.length - _uiState.value.inputUrl.length > 1
                }
                ?: event.url

            // App Store edition: media and torrent engines are not shipped.
            val engine = when (val detected = detectLinkTypeUseCase(inputUrl)) {
                EngineType.MEDIA_STREAM -> if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.mediaExtractor) detected else EngineType.HTTP_DIRECT
                EngineType.BITTORRENT -> if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.torrent) detected else EngineType.HTTP_DIRECT
                else -> detected
            }

            val defaultQualities =

                emptyList<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>()
            val linkList =
                if (links.size >= 2) links.map {
                    LinkListEntry(
                        it,
                        engineForListedLink(it)
                    )
                } else emptyList()

            _uiState.update {

                it.copy(

                    inputUrl = inputUrl,
                    testLink = if (it.inputUrl == inputUrl) it.testLink else null,

                    detectedEngine = if (linkList.isNotEmpty()) EngineType.HTTP_DIRECT else engine,
                    linkList = linkList,

                    errorMessage = null,

                    inspectedTorrentFiles = emptyList(),

                    inspectedTorrentName = null,

                    inspectedMediaQualities = defaultQualities,
                    inspectedPlaylist = null,

                    inspectedMediaTitle = null,
                    inspectMediaError = null,
                    inspectError = null,
                    clipStartTime = "",
                    clipEndTime = "",
                    isClipRangeEnabled = false,
                    clipFastMode = false,
                    audioConvertFormat = null,
                    scheduleStartMs = 0L,
                    selectedConnections = 0,
                    mediaDurationSeconds = 0L,
                    fileNameOverride = "",
                    checksumInput = "",
                    suggestedFileName = ""
                )
            }
            if (_uiState.value.testLink == null) testLinkJob?.cancel()
            refreshSuggestedFileName()
            if (linkList.any { it.engine == EngineType.BITTORRENT }) inspectListedTorrents()
            if (linkList.any { it.engine == EngineType.MEDIA_STREAM }) inspectListedVideos()
        }

        is DashboardUiEvent.OnDestinationPathChanged -> setDestinationFolder(event.path)

        is DashboardUiEvent.OnDisableTorrentUploadToggled -> {

            _uiState.update { it.copy(disableTorrentUpload = event.disable) }

            persistCurrentSettings()

        }

        is DashboardUiEvent.OnToggleAddDialog -> {

            if (!event.show) _uiState.update { it.copy(linkListFolder = "") }
            if (!event.show) clearTestLink()
            if (!event.show && pendingExternalUrl != null) {
                val urlToFallback = pendingExternalUrl!!
                pendingExternalUrl = null
                // The extension cancelled the browser's own download when it handed us this
                // link. Offer to give it back to the browser ONLY when the browser could
                // actually download it (a plain file over http/https) - never for magnets,
                // .torrent files or video pages, where opening the browser is pointless.
                val isPlainFile = urlToFallback.startsWith("http", ignoreCase = true) &&
                        detectLinkTypeUseCase(urlToFallback) == EngineType.HTTP_DIRECT
                if (isPlainFile) {
                    _uiState.update { it.copy(browserFallbackUrl = urlToFallback) }
                }
            }

            val installed =

                com.michelle.raouf.fetch_jet.feature.media_extractor.isMediaExtractorToolInstalled()

            val status =

                com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaExtractorToolStatus()

            val url = if (event.show) _uiState.value.inputUrl else ""

            val defaultQualities =

                emptyList<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>()

            _uiState.update { state ->

                state.copy(

                    isAddDialogVisible = event.show,

                    errorMessage = null,

                    inspectedTorrentFiles = emptyList(),

                    inspectedTorrentName = null,

                    inspectedMediaQualities = defaultQualities,
                    inspectedPlaylist = null,
                    linkList = if (event.show) state.linkList else emptyList(),
                    audioConvertFormat = null,
                    scheduleStartMs = 0L,

                    selectedConnections = 0,

                    inspectedMediaTitle = null,

                    inspectMediaError = null,

                    inspectError = null,

                    ytDlpStatus = status,

                    isYtDlpInstalled = installed,

                    selectedDownloadSpeedLimitBytes = if (event.show) 0L else state.selectedDownloadSpeedLimitBytes,

                    selectedUploadSpeedLimitBytes = if (event.show) 0L else state.selectedUploadSpeedLimitBytes

                )

            }
            if (event.show && !installed) pollToolStatusWhileNotReady()

        }

        DashboardUiEvent.OnAddDownloadClicked -> {
            rememberAddFolder()
            addDownload()
        }
        DashboardUiEvent.OnInspectTorrentClicked -> inspectTorrent()
        DashboardUiEvent.OnInspectMediaClicked -> inspectMedia()
        is DashboardUiEvent.OnMediaQualitySelectionToggled -> {
            _uiState.update { state ->
                val updated = state.inspectedMediaQualities.map { q ->
                    if (q.index == event.qualityIndex) q.copy(isSelected = event.isSelected) else q
                }
                state.copy(inspectedMediaQualities = updated)
            }
            refreshSuggestedFileName()
        }

        is DashboardUiEvent.OnSubtitleToggled -> {
            _uiState.update { state ->
                state.copy(inspectedSubtitles = state.inspectedSubtitles.map { s ->
                    if (s.langCode == event.langCode) s.copy(isSelected = event.isSelected) else s
                })
            }
        }

        is DashboardUiEvent.OnToggleEmbedSubtitles -> {
            _uiState.update { it.copy(embedSubtitles = event.embed) }
        }

        is DashboardUiEvent.OnClipStartTimeChanged -> {
            _uiState.update { it.copy(clipStartTime = event.time) }
        }

        is DashboardUiEvent.OnClipEndTimeChanged -> {
            _uiState.update { it.copy(clipEndTime = event.time) }
        }

        is DashboardUiEvent.OnToggleClipFastMode -> {
            _uiState.update { it.copy(clipFastMode = event.enabled) }
        }

        is DashboardUiEvent.OnAudioConvertFormatSelected -> {
            _uiState.update { it.copy(audioConvertFormat = event.format) }
            refreshSuggestedFileName()
        }

        is DashboardUiEvent.OnLinkListEntryToggled -> _uiState.update { st ->
            st.copy(linkList = st.linkList.mapIndexed { i, entry ->
                if (i == event.index) entry.copy(isSelected = event.selected) else entry
            })
        }

        is DashboardUiEvent.OnLinkListSelectAll -> _uiState.update { st ->
            st.copy(linkList = st.linkList.map { it.copy(isSelected = event.selected) })
        }

        is DashboardUiEvent.OnLinkListFolderChanged -> _uiState.update { it.copy(linkListFolder = event.name) }

        is DashboardUiEvent.OnLinkListQualitySelected -> _uiState.update { st ->
            st.copy(linkList = st.linkList.mapIndexed { i, entry ->
                if (i != event.linkIndex) entry
                else entry.copy(mediaQualities = entry.mediaQualities.map { it.copy(isSelected = it.index == event.qualityIndex) })
            })
        }

        is DashboardUiEvent.OnLinkListRetryInspect -> inspectListedVideos()

        is DashboardUiEvent.OnLinkListTorrentFileToggled -> updateListedTorrentFiles(event.linkIndex) { files ->
            files.map { if (it.index == event.fileIndex) it.copy(isSelected = event.selected) else it }
        }

        is DashboardUiEvent.OnLinkListTorrentFilePriority -> updateListedTorrentFiles(event.linkIndex) { files ->
            files.map { if (it.index == event.fileIndex) it.copy(priority = event.priority) else it }
        }

        is DashboardUiEvent.OnLinkListTorrentSelectAll -> updateListedTorrentFiles(event.linkIndex) { files ->
            files.map { it.copy(isSelected = event.selected) }
        }

        is DashboardUiEvent.OnPlaylistEntryToggled -> _uiState.update { st ->
            val pl = st.inspectedPlaylist ?: return@update st
            st.copy(inspectedPlaylist = pl.copy(entries = pl.entries.map {
                if (it.index == event.index) it.copy(isSelected = event.selected) else it
            }))
        }

        is DashboardUiEvent.OnPlaylistSelectAll -> _uiState.update { st ->
            val pl = st.inspectedPlaylist ?: return@update st
            st.copy(inspectedPlaylist = pl.copy(entries = pl.entries.map { it.copy(isSelected = event.selected) }))
        }

        is DashboardUiEvent.OnPlaylistFolderChanged -> _uiState.update {
            it.copy(inspectedPlaylist = it.inspectedPlaylist?.copy(folderName = event.name))
        }

        is DashboardUiEvent.OnPlaylistQualityChanged -> _uiState.update {
            it.copy(
                playlistQuality = event.quality
            )
        }

        DashboardUiEvent.OnPlaylistUseSingleVideo -> {
            singleVideoUrl = _uiState.value.inputUrl
            _uiState.update { it.copy(inspectedPlaylist = null) }
            inspectMedia()
        }

        is DashboardUiEvent.OnScheduleChanged -> _uiState.update { it.copy(scheduleStartMs = event.epochMs) }
        is DashboardUiEvent.OnConnectionsChanged -> _uiState.update {
            it.copy(
                selectedConnections = event.connections
            )
        }

        is DashboardUiEvent.OnToggleClipRange -> {
            val hasMediaData = _uiState.value.inspectedMediaQualities.isNotEmpty()
            val durationSec = _uiState.value.mediaDurationSeconds
            if ((!hasMediaData || durationSec <= 0L) && event.enabled) {
                return
            }
            val fullEndTime =
                if (durationSec > 0) formatSecondsToHhMm(durationSec) else "00:10:00"
            _uiState.update {
                it.copy(
                    isClipRangeEnabled = event.enabled,
                    clipStartTime = if (event.enabled && (it.clipStartTime.isBlank() || it.clipStartTime == "00:00")) "00:00" else it.clipStartTime,
                    clipEndTime = if (event.enabled && (it.clipEndTime.isBlank() || it.clipEndTime == "00:00")) fullEndTime else it.clipEndTime
                )
            }
        }

        is DashboardUiEvent.OnSelectAllMediaQualitiesToggled -> {

            _uiState.update { state ->

                val updated = state.inspectedMediaQualities.map { q ->

                    q.copy(isSelected = event.selectAll)

                }

                state.copy(inspectedMediaQualities = updated)

            }

        }

        is DashboardUiEvent.OnTorrentFileSelectionToggled -> {

            _uiState.update { state ->

                val updatedFiles = state.inspectedTorrentFiles.map { file ->

                    if (file.index == event.fileIndex) file.copy(isSelected = event.isSelected) else file

                }

                state.copy(inspectedTorrentFiles = updatedFiles)

            }

        }

        is DashboardUiEvent.OnTorrentFilePriorityChanged -> {

            _uiState.update { state ->

                val updatedFiles = state.inspectedTorrentFiles.map { file ->

                    if (file.index == event.fileIndex) file.copy(priority = event.priority) else file

                }

                state.copy(inspectedTorrentFiles = updatedFiles)

            }

        }

        is DashboardUiEvent.OnSelectAllTorrentFilesToggled -> {

            _uiState.update { state ->

                val updatedFiles =

                    state.inspectedTorrentFiles.map { it.copy(isSelected = event.selectAll) }

                state.copy(inspectedTorrentFiles = updatedFiles)

            }

        }

        else -> Unit
    }
}
