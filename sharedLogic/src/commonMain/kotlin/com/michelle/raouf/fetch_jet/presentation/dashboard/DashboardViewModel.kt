package com.michelle.raouf.fetch_jet.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class DashboardViewModel(

    internal val queueRepository: QueueManagerRepository = QueueManagerRepository.getInstance()

) : ViewModel() {

    internal val settingsRepository =
        com.michelle.raouf.fetch_jet.core.settings.AppSettingsRepository(queueRepository.pathResolver)

    /** Background work; cancelled with the ViewModel ([onCleared]). */
    internal val scope = CoroutineScope(
        viewModelScope.coroutineContext + Dispatchers.IO + SupervisorJob(viewModelScope.coroutineContext[Job])
    )

    internal val _uiState = MutableStateFlow(

        DashboardUiState(

            destinationPath = queueRepository.pathResolver.getDownloadsDirectory()

        )

    )

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    internal val _uiEffect = MutableSharedFlow<DashboardUiEffect>(extraBufferCapacity = 8)

    val uiEffect: SharedFlow<DashboardUiEffect> = _uiEffect.asSharedFlow()

    internal var pendingExternalUrl: String? = null
    internal var pendingExternalHeaders: Map<String, String> = emptyMap()
    internal var pendingExternalFileName: String? = null

    /** Quality the extension chose for a clip: re-applied when the dialog's own inspection finishes. */
    internal var pendingSelectedFormatId: String? = null

    /** An extension download whose file already exists: waits for Overwrite / Save as copy. */
    internal var pendingExternalConflict: com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest? =
        null

    /** The playlist link for which the user chose "download only this video". */
    internal var singleVideoUrl: String? = null

    internal val toolsMutex =
        com.michelle.raouf.fetch_jet.feature.media_extractor.MediaToolsLock.mutex

    internal fun reportToolProgress(p: com.michelle.raouf.fetch_jet.feature.media_extractor.ToolDownloadProgress) {
        _uiState.update {
            it.copy(
                isDownloadingYtDlp = true,
                toolDownloadName = p.tool,
                toolDownloadFraction = p.fraction
            )
        }
    }

    internal val detectLinkTypeUseCase =
        com.michelle.raouf.fetch_jet.feature.queue_manager.DetectLinkTypeUseCase()
    internal var toolStatusPollJob: Job? = null

    init {

        observeQueue()
        observeBehaviorSettings()

        val savedSettings = settingsRepository.loadSettings()
        com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.change(savedSettings.language)

        _uiState.update {
            it.copy(
                destinationPath = savedSettings.destinationPath,
                disableTorrentUpload = savedSettings.disableTorrentUpload,
                isDarkMode = savedSettings.isDarkMode,
                globalDownloadSpeedLimitBytes = savedSettings.globalDownloadSpeedLimitBytes,
                globalUploadSpeedLimitBytes = savedSettings.globalUploadSpeedLimitBytes,
                speedDisplayUnit = savedSettings.speedDisplayUnit
            )
        }

        // Probing the tools spawns `ffmpeg -version` (up to 2 s each, twice) and touches the
        // disk. Doing that here, inside composition, froze the first frame of the app.
        refreshToolStatus()
        autoUpdateTools()

        if (savedSettings.globalDownloadSpeedLimitBytes > 0L) {

            queueRepository.setGlobalDownloadSpeedLimit(savedSettings.globalDownloadSpeedLimitBytes)

        }

        if (savedSettings.globalUploadSpeedLimitBytes > 0L) {

            queueRepository.setGlobalUploadSpeedLimit(savedSettings.globalUploadSpeedLimitBytes)

        }

        com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.onExternalRequest =
            { request -> handleExternalRequest(request) }
        com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.onExternalBatch =
            { requests -> handleExternalBatch(requests) }

    }

    internal fun persistCurrentSettings() {

        val s = _uiState.value

        settingsRepository.saveSettings(

            com.michelle.raouf.fetch_jet.core.settings.AppSettings(

                destinationPath = s.destinationPath,

                disableTorrentUpload = s.disableTorrentUpload,

                isDarkMode = s.isDarkMode,

                globalDownloadSpeedLimitBytes = s.globalDownloadSpeedLimitBytes,

                globalUploadSpeedLimitBytes = s.globalUploadSpeedLimitBytes,

                speedDisplayUnit = s.speedDisplayUnit,

                language = com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.choice

            )

        )

    }

    /** One link from the browser: open the Add dialog pre-filled, remember its headers. */
    fun handleExternalRequest(request: com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest) {
        // Keep the site's login for links of the same site added by hand later.
        com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.remember(
            request.referer.ifBlank { request.url }, request.toRequestHeaders()
        )
        if (request.openClip && request.mediaQualities.isNotEmpty()) {
            openClipDialog(request)
            return
        }
        if (request.mediaQualities.isNotEmpty() || request.direct) {
            startExternalDirectly(request)
            return
        }
        pendingExternalUrl = request.url
        pendingExternalHeaders = request.toRequestHeaders()
        pendingExternalFileName = request.fileName.ifBlank { null }
        onEvent(DashboardUiEvent.OnUrlChanged(request.url))
        onEvent(DashboardUiEvent.OnToggleAddDialog(true))
    }

    /**
     * The scissors button in the extension: the Add dialog opens on this link with the chosen
     * quality selected and the clip range switched on, so the user sets From / To and downloads.
     */
    internal fun openClipDialog(request: com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest) {
        val qualities = request.mediaQualities
        val selected = qualities.firstOrNull { it.isSelected } ?: qualities.first()
        pendingExternalUrl = request.url
        pendingExternalHeaders = request.toRequestHeaders()
        pendingExternalFileName = request.fileName.ifBlank { null }
        pendingSelectedFormatId = selected.formatId
        onEvent(DashboardUiEvent.OnUrlChanged(request.url))
        onEvent(DashboardUiEvent.OnToggleAddDialog(true))
        val duration = qualities.maxOfOrNull { it.durationSeconds }?.coerceAtLeast(0L) ?: 0L
        _uiState.update {
            it.copy(
                detectedEngine = EngineType.MEDIA_STREAM,
                inspectedMediaQualities = qualities.map { q -> q.copy(isSelected = q === selected) },
                inspectedMediaTitle = selected.videoTitle,
                mediaDurationSeconds = duration,
                isInspectingMedia = false,
                inspectMediaError = null,
                isClipRangeEnabled = true,
                clipStartTime = "00:00:00",
                clipEndTime = if (duration > 0) formatSecondsToHhMm(duration) else "00:10:00"
            )
        }
        refreshSuggestedFileName()
    }

    /**
     * The browser already chose everything (quality picked in the extension popup, or a
     * "download now" click): queue it without showing the Add dialog.
     */
    internal fun startExternalDirectly(
        request: com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest,
        forceOverwrite: Boolean = false,
        overrideFileName: String? = null
    ) {
        scope.launch {
            val state = _uiState.value
            val destDir =
                state.destinationPath.ifBlank { queueRepository.pathResolver.getDownloadsDirectory() }
            val qualities = request.mediaQualities
            val selected = qualities.firstOrNull { it.isSelected } ?: qualities.firstOrNull()
            val rawName = request.fileName.ifBlank {
                val title = selected?.videoTitle
                if (!title.isNullOrBlank()) "$title.${selected.format?.ifBlank { "mp4" } ?: "mp4"}" else ""
            }
            val fileName = (overrideFileName ?: rawName).ifBlank { null }
                ?.let { queueRepository.pathResolver.sanitizeFileName(it) }
            // The file is already in the folder: ask, like the Add dialog does, instead of
            // quietly saving "name (1).mp4".
            if (fileName != null && !forceOverwrite && overrideFileName == null &&
                FileSystem.SYSTEM.exists("$destDir/$fileName".toPath())
            ) {
                pendingExternalConflict = request
                _uiState.update {
                    it.copy(
                        fileConflictAlert = FileConflictData(
                            fileName = fileName,
                            destinationPath = destDir,
                            suggestedNewName = getUniqueFileName(destDir, fileName)
                        )
                    )
                }
                com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.showAndFocus()
                return@launch
            }
            val engine =
                if (qualities.isNotEmpty()) EngineType.MEDIA_STREAM else engineForDirectFile(request.url)
            // The same video + quality already downloading or queued (a double click in the
            // popup, the page sending twice): do not start it a second time.
            val wantedFormat = selected?.formatId
            val same = queueRepository.allTasksFlow.value.firstOrNull { t ->
                (t.url == request.url || t.originalUrl == request.url) &&
                        (wantedFormat == null || t.formatId == wantedFormat) &&
                        t.status != DownloadStatus.COMPLETED &&
                        t.status != DownloadStatus.ERROR
            }
            if (same != null) {
                // Paused → continue it; running / queued → just show it. Never a silent no-op.
                if (same.status == DownloadStatus.PAUSED) {
                    queueRepository.resumeTask(same.id)
                }
                _uiState.update {
                    it.copy(
                        scrollToTopSignal = it.scrollToTopSignal + 1,
                        selectedTab = DashboardTab.ALL
                    )
                }
                com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.showAndFocus()
                return@launch
            }
            if (engine == EngineType.MEDIA_STREAM && qualities.isEmpty()) {
                // A video page without a chosen quality still needs the inspector. The name the
                // extension chose (the page title for an HLS playlist) is kept for the file.
                pendingExternalUrl = request.url
                pendingExternalHeaders = request.toRequestHeaders()
                pendingExternalFileName = request.fileName.ifBlank { null }
                onEvent(DashboardUiEvent.OnUrlChanged(request.url))
                onEvent(DashboardUiEvent.OnToggleAddDialog(true))
                return@launch
            }
            queueRepository.enqueueTask(
                url = request.url,
                customDestinationPath = destDir,
                customFileName = fileName,
                disableUpload = state.disableTorrentUpload,
                mediaQualities = qualities,
                maxDownloadSpeedBytesPerSec = state.selectedDownloadSpeedLimitBytes,
                maxUploadSpeedBytesPerSec = state.selectedUploadSpeedLimitBytes,
                forcedEngineType = engine,
                requestHeaders = request.toRequestHeaders()
            ).onSuccess { task ->
                _uiEffect.emit(
                    DashboardUiEffect.ShowToast(
                        LocalizationManager.Strings.sysStartedDownload(
                            task.fileName.ifBlank { task.url })
                    )
                )
                _uiState.update { it.copy(scrollToTopSignal = it.scrollToTopSignal + 1) }
            }.onFailure { error ->
                _uiEffect.emit(
                    DashboardUiEffect.ShowToast(
                        LocalizationManager.Strings.sysCouldNotStartDownload(
                            error.message ?: LocalizationManager.Strings.sysUnknownError
                        )
                    )
                )
            }
        }
    }

    /**
     * "Download all links" from the browser: every link is queued straight away as a plain
     * HTTP download into the default folder (a dialog per link would be unusable). Links the
     * app already has are skipped.
     */
    fun handleExternalBatch(requests: List<com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest>) {
        scope.launch {
            val existing = queueRepository.allTasksFlow.value
            var added = 0
            var skipped = 0
            val destDir =
                _uiState.value.destinationPath.ifBlank { queueRepository.pathResolver.getDownloadsDirectory() }
            for (req in requests) {
                if (!(req.url.startsWith("http://", true) || req.url.startsWith(
                        "https://",
                        true
                    ) || req.url.startsWith("magnet:", true))
                ) {
                    skipped++; continue
                }
                if (existing.any { isSameDownload(it, req.url) }) {
                    skipped++; continue
                }
                val engine = engineForDirectFile(req.url)
                if (engine == EngineType.MEDIA_STREAM) {
                    skipped++; continue
                } // a video page: needs the inspector
                val name = req.fileName.ifBlank { null }
                    ?.let { queueRepository.pathResolver.sanitizeFileName(it) }
                    ?.let { freeFileName(destDir, it) }
                queueRepository.enqueueTask(
                    url = req.url,
                    customDestinationPath = destDir,
                    customFileName = name,
                    disableUpload = _uiState.value.disableTorrentUpload,
                    maxDownloadSpeedBytesPerSec = _uiState.value.selectedDownloadSpeedLimitBytes,
                    maxUploadSpeedBytesPerSec = _uiState.value.selectedUploadSpeedLimitBytes,
                    forcedEngineType = engine,
                    requestHeaders = req.toRequestHeaders()
                ).onSuccess { added++ }.onFailure { skipped++ }
            }
            _uiEffect.emit(
                DashboardUiEffect.ShowToast(
                    LocalizationManager.Strings.sysQueuedFromBrowser(added, skipped)
                )
            )
        }
    }

    /** Mirrors BehaviorSettings into the UI state so screens read state and send events only. */
    internal fun observeBehaviorSettings() {
        scope.launch {
            BehaviorSettings.connections.collect { n ->
                _uiState.update {
                    it.copy(
                        defaultConnections = n
                    )
                }
            }
        }
        scope.launch {
            BehaviorSettings.monitorClipboard.collect { on ->
                _uiState.update {
                    it.copy(
                        monitorClipboard = on
                    )
                }
            }
        }
        scope.launch {
            BehaviorSettings.clipboardVideoPages.collect { on ->
                _uiState.update {
                    it.copy(
                        clipboardVideoPages = on
                    )
                }
            }
        }
        scope.launch {
            BehaviorSettings.sortIntoFolders.collect { on ->
                _uiState.update {
                    it.copy(
                        sortIntoFolders = on
                    )
                }
            }
        }
    }

    internal fun observeQueue() {

        scope.launch {

            queueRepository.allTasksFlow.collect { tasks ->

                _uiState.update { state ->

                    val filtered = filterTasks(tasks, state.selectedTab, state.sortOption)

                    val activeTasks = tasks.filter { it.status == DownloadStatus.DOWNLOADING }

                    val totalDlSpeed = activeTasks.sumOf { it.downloadSpeedBytesPerSec }

                    val totalUlSpeed = activeTasks.sumOf { it.uploadSpeedBytesPerSec }

                    state.copy(

                        allTasks = tasks,

                        filteredTasks = filtered,

                        totalDownloadSpeedBytesPerSec = totalDlSpeed,

                        totalUploadSpeedBytesPerSec = totalUlSpeed

                    )

                }

            }

        }

    }

    fun onEvent(event: DashboardUiEvent) {

        when (event) {
            is DashboardUiEvent.OnEngineSelected, is DashboardUiEvent.OnClipboardLink, DashboardUiEvent.OnClipboardOfferAccepted, DashboardUiEvent.OnClipboardOfferDismissed, is DashboardUiEvent.OnFileNameChanged, is DashboardUiEvent.OnUrlChanged, is DashboardUiEvent.OnDestinationPathChanged, is DashboardUiEvent.OnDisableTorrentUploadToggled, is DashboardUiEvent.OnToggleAddDialog, DashboardUiEvent.OnAddDownloadClicked, DashboardUiEvent.OnInspectTorrentClicked, DashboardUiEvent.OnInspectMediaClicked, is DashboardUiEvent.OnMediaQualitySelectionToggled, is DashboardUiEvent.OnClipStartTimeChanged, is DashboardUiEvent.OnClipEndTimeChanged, is DashboardUiEvent.OnToggleClipFastMode, is DashboardUiEvent.OnPlaylistEntryToggled, is DashboardUiEvent.OnLinkListEntryToggled, is DashboardUiEvent.OnLinkListSelectAll, is DashboardUiEvent.OnLinkListTorrentFileToggled, is DashboardUiEvent.OnLinkListTorrentFilePriority, is DashboardUiEvent.OnLinkListTorrentSelectAll, is DashboardUiEvent.OnPlaylistSelectAll, is DashboardUiEvent.OnPlaylistQualityChanged, DashboardUiEvent.OnPlaylistUseSingleVideo, is DashboardUiEvent.OnScheduleChanged, is DashboardUiEvent.OnConnectionsChanged, is DashboardUiEvent.OnToggleClipRange, is DashboardUiEvent.OnSelectAllMediaQualitiesToggled, is DashboardUiEvent.OnTorrentFileSelectionToggled, is DashboardUiEvent.OnTorrentFilePriorityChanged, is DashboardUiEvent.OnSelectAllTorrentFilesToggled -> handleAddDialogEvent(
                event
            )

            is DashboardUiEvent.OnPauseClicked, is DashboardUiEvent.OnResumeClicked, is DashboardUiEvent.OnCancelClicked, is DashboardUiEvent.OnRemoveClicked, is DashboardUiEvent.OnRemoveSelectedClicked, is DashboardUiEvent.OnExportLinksClicked, is DashboardUiEvent.OnConfirmRemoveClicked, DashboardUiEvent.OnConfirmBrowserFallback, DashboardUiEvent.OnDismissBrowserFallback, DashboardUiEvent.OnConfirmCancelClicked, DashboardUiEvent.OnResumeExistingDuplicateClicked, DashboardUiEvent.OnRestartDuplicateClicked, DashboardUiEvent.OnDismissDuplicateDialog, DashboardUiEvent.OnConfirmOverwriteClicked, is DashboardUiEvent.OnSaveAsCopyClicked, DashboardUiEvent.OnDismissFileConflictDialog, DashboardUiEvent.OnDismissCancelDialog, DashboardUiEvent.OnDismissRemoveDialog, is DashboardUiEvent.OnDownloadSpeedLimitChanged, is DashboardUiEvent.OnUploadSpeedLimitChanged, DashboardUiEvent.OnPauseAllClicked, DashboardUiEvent.OnResumeAllClicked, is DashboardUiEvent.OnTaskTorrentFileSelectionToggled, is DashboardUiEvent.OnTaskTorrentFilePriorityChanged -> handleTaskEvent(
                event
            )

            is DashboardUiEvent.OnMonitorClipboardChanged, is DashboardUiEvent.OnClipboardVideoPagesChanged, is DashboardUiEvent.OnSortIntoFoldersChanged, is DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged, is DashboardUiEvent.OnGlobalUploadSpeedLimitChanged, is DashboardUiEvent.OnSpeedDisplayUnitChanged, DashboardUiEvent.OnApplySettingsClicked, DashboardUiEvent.OnThemeToggled, is DashboardUiEvent.OnLanguageChanged, is DashboardUiEvent.OnTabSelected, is DashboardUiEvent.OnSortChanged, is DashboardUiEvent.OnDefaultConnectionsChanged, DashboardUiEvent.OnDownloadYtDlpClicked -> handleSettingsEvent(
                event
            )
        }

    }

    internal fun inspectTorrent() {

        val url = _uiState.value.inputUrl

        if (url.isBlank()) {

            _uiState.update { it.copy(inspectError = LocalizationManager.Strings.sysEnterTorrentOrMagnetFirst) }

            return

        }

        scope.launch {

            _uiState.update { it.copy(isInspectingTorrent = true, inspectError = null) }

            queueRepository.torrentEngine.fetchAndParseTorrent(url)

                .onSuccess { parsed ->

                    _uiState.update {

                        it.copy(

                            isInspectingTorrent = false,

                            inspectedTorrentName = parsed.name,

                            inspectedTorrentFiles = parsed.files,

                            inspectedTorrentWebSeeds = parsed.webSeeds

                        )

                    }

                }

                .onFailure { err ->

                    _uiState.update {

                        it.copy(

                            isInspectingTorrent = false,

                            inspectError = err.message
                                ?: LocalizationManager.Strings.sysFailedToParseTorrentMetadata

                        )

                    }

                }

        }

    }

    internal fun inspectMedia() {

        val url = _uiState.value.inputUrl

        if (url.isBlank()) {

            _uiState.update { it.copy(inspectMediaError = LocalizationManager.Strings.sysEnterMediaUrlFirst) }

            return

        }

        scope.launch {
            _uiState.update { it.copy(isInspectingMedia = true, inspectMediaError = null) }
            // A playlist / channel link: list its videos (unless the user chose "only this video").
            if (url != singleVideoUrl && com.michelle.raouf.fetch_jet.feature.media_extractor.looksLikePlaylistUrl(
                    url
                )
            ) {
                val playlist = try {
                    withTimeoutOrNull(90_000L) { queueRepository.inspectPlaylist(url) }
                } catch (_: Exception) {
                    null
                }
                if (playlist != null && playlist.entries.size >= 2 && _uiState.value.inputUrl == url) {
                    _uiState.update {
                        it.copy(
                            isInspectingMedia = false,
                            inspectedPlaylist = playlist,
                            inspectedMediaQualities = emptyList(),
                            inspectedMediaTitle = playlist.title,
                            inspectMediaError = null,
                            isClipRangeEnabled = false
                        )
                    }
                    return@launch
                }
                // The link was changed meanwhile: its own inspection takes over.
                if (_uiState.value.inputUrl != url) {
                    _uiState.update { it.copy(isInspectingMedia = false) }
                    return@launch
                }
            }
            try {
                val qualities = withTimeoutOrNull(45_000L) {
                    queueRepository.inspectMediaQualities(
                        url,
                        headersForLink(url)
                    )
                } ?: emptyList()
                if (_uiState.value.inputUrl != url) {
                    // Results of a link that is no longer in the box.
                    _uiState.update { it.copy(isInspectingMedia = false) }
                    return@launch
                }
                if (qualities.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isInspectingMedia = false,
                            inspectedMediaQualities = emptyList(),
                            inspectedPlaylist = null,
                            inspectMediaError = com.michelle.raouf.fetch_jet.feature.media_extractor
                                .lastMediaInspectionError()
                                ?.removePrefix("ERROR:")?.trim()
                                ?.let {
                                    LocalizationManager.Strings.sysUnableToFetchFormatsWithReason(
                                        it
                                    )
                                }
                                ?: LocalizationManager.Strings.sysUnableToFetchFormats
                        )
                    }
                } else {
                    val mediaTitle =
                        qualities.firstOrNull { !it.videoTitle.isNullOrBlank() }?.videoTitle
                    val duration =
                        qualities.map { it.durationSeconds }.maxOrNull()?.coerceAtLeast(0L) ?: 0L
                    val fullEnd = if (duration > 0) formatSecondsToHhMm(duration) else "00:10:00"

                    val wanted = pendingSelectedFormatId
                    val picked = if (wanted != null && qualities.any { it.formatId == wanted })
                        qualities.map { q -> q.copy(isSelected = q.formatId == wanted) } else qualities
                    pendingSelectedFormatId = null
                    _uiState.update {
                        it.copy(
                            isInspectingMedia = false,
                            inspectedMediaQualities = picked,
                            inspectedMediaTitle = mediaTitle,
                            mediaDurationSeconds = duration,
                            inspectMediaError = null,
                            clipStartTime = if (it.isClipRangeEnabled && (it.clipStartTime.isBlank() || it.clipStartTime == "00:00")) "00:00" else it.clipStartTime,
                            clipEndTime = if (it.isClipRangeEnabled && (it.clipEndTime.isBlank() || it.clipEndTime == "00:00")) fullEnd else it.clipEndTime
                        )
                    }
                    refreshSuggestedFileName()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInspectingMedia = false,
                        inspectedMediaQualities = emptyList(),
                        inspectedPlaylist = null,
                        inspectMediaError = e.message
                            ?: LocalizationManager.Strings.sysFailedToInspectMediaQualities
                    )
                }
            }
        }

    }

    internal fun isSameDownload(task: DownloadTask, newUrl: String): Boolean {
        val trimmed = newUrl.trim()
        if (task.url.equals(trimmed, ignoreCase = true)) return true
        if (!task.originalUrl.isNullOrBlank() && task.originalUrl.equals(
                trimmed,
                ignoreCase = true
            )
        ) return true

        if (trimmed.contains("xt=urn:btih:", ignoreCase = true) && task.url.contains(
                "xt=urn:btih:",
                ignoreCase = true
            )
        ) {
            val hash1 = task.url.substringAfter("xt=urn:btih:").substringBefore("&")
            val hash2 = trimmed.substringAfter("xt=urn:btih:").substringBefore("&")
            if (hash1.isNotBlank() && hash1.equals(hash2, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Headers for a link in the Add dialog: the ones the extension sent with this very link,
     * else what the jar remembers for the link's site (a lesson pasted after another one came
     * from the browser), else nothing.
     */
    internal fun headersForLink(url: String): Map<String, String> {
        val own = if (pendingExternalUrl == url) pendingExternalHeaders else emptyMap()
        return com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.headersFor(url, own)
    }

    /** Recomputes the name shown in the Add dialog from the current link / inspection. */
    internal fun refreshSuggestedFileName() {
        val s = _uiState.value
        val url = s.inputUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(suggestedFileName = "") }; return
        }
        val name = try {
            val selected = s.inspectedMediaQualities.firstOrNull { it.isSelected }
            val title = s.inspectedMediaTitle ?: selected?.videoTitle
            val fromTitle = if (!title.isNullOrBlank()) {
                val ext = selected?.format ?: "mp4"
                val clean = queueRepository.pathResolver.sanitizeFileName(title)
                if (clean.endsWith(".$ext", ignoreCase = true)) clean else "$clean.$ext"
            } else null
            resolveTargetFileName(s, url, fromTitle)
        } catch (_: Exception) {
            ""
        }
        _uiState.update { it.copy(suggestedFileName = name) }
    }

    internal fun resolveTargetFileName(
        currentState: DashboardUiState,
        url: String,
        customFileName: String?
    ): String {
        if (!customFileName.isNullOrBlank()) return customFileName
        if (!currentState.inspectedTorrentName.isNullOrBlank()) return currentState.inspectedTorrentName
        // The browser already resolved the real name (Content-Disposition etc.) before the
        // extension cancelled its download; it beats anything we can guess from the URL.
        val browserName = pendingExternalFileName
        if (pendingExternalUrl == url && !browserName.isNullOrBlank() &&
            (currentState.detectedEngine == EngineType.HTTP_DIRECT ||
                    com.michelle.raouf.fetch_jet.feature.media_extractor.HlsDownloader.isHlsUrl(url))
        ) {
            return queueRepository.pathResolver.sanitizeFileName(browserName)
        }
        val mediaTitle =
            currentState.inspectedMediaQualities.firstOrNull { !it.videoTitle.isNullOrBlank() }?.videoTitle
        if (!mediaTitle.isNullOrBlank()) {
            val selectedExt =
                currentState.inspectedMediaQualities.firstOrNull { it.isSelected }?.format ?: "mp4"
            return queueRepository.pathResolver.sanitizeFileName("$mediaTitle.$selectedExt")
        }
        val cleanUrl = url.substringBefore('?')
        val nameFromUrl = cleanUrl.substringAfterLast('/')
        // A pasted playlist link: its name is a hash ("aa004d...m3u8"), and the video is saved
        // as MP4 anyway. Use the site + date so the user sees what to rename.
        if (com.michelle.raouf.fetch_jet.feature.media_extractor.HlsDownloader.isHlsUrl(url) ||
            nameFromUrl.endsWith(".mpd", ignoreCase = true)
        ) {
            val site = com.michelle.raouf.fetch_jet.core.network.SiteCookieJar.siteOf(url)
                .substringBefore('.')
                .replaceFirstChar { it.uppercase() }.ifBlank { "Video" }
            val stamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000 % 100000
            return "$site video $stamp.mp4"
        }
        val isWebScript = nameFromUrl.matches(
            Regex(
                """.*\.(php|asp|aspx|jsp|html|htm|cgi|do|action)$""",
                RegexOption.IGNORE_CASE
            )
        )
        if (nameFromUrl.isNotBlank() && nameFromUrl.contains('.') && !isWebScript) return queueRepository.pathResolver.sanitizeFileName(
            nameFromUrl
        )
        return if (currentState.detectedEngine == EngineType.MEDIA_STREAM) {
            "media_video_${kotlin.random.Random.nextInt(100000, 999999)}.mp4"
        } else {
            "download_${kotlin.random.Random.nextInt(100000, 999999)}"
        }
    }

    /** The name itself when it is free, else "name (n).ext". */
    internal fun freeFileName(destDir: String, fileName: String): String =
        if (FileSystem.SYSTEM.exists("$destDir/$fileName".toPath())) getUniqueFileName(
            destDir,
            fileName
        ) else fileName

    /**
     * Engine for a link the browser hands over as a *file* (one-click download, batch): a
     * direct media file (.mp4, .mp3, googlevideo stream...) is a plain HTTP download even though
     * the link detector would route it to the media inspector. Only real video *pages* (no file
     * extension) and HLS/DASH playlists still need yt-dlp.
     */
    internal fun engineForDirectFile(url: String): EngineType {
        val detected = detectLinkTypeUseCase(url)
        if (detected != EngineType.MEDIA_STREAM) return detected
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        val isPlaylist = ext == "m3u8" || ext == "mpd"
        val isFile =
            ext.isNotEmpty() && ext.length in 2..5 && ext.all { it.isLetterOrDigit() } && !isPlaylist &&
                    ext !in setOf("html", "htm", "php", "asp", "aspx", "jsp")
        val isGoogleVideo = path.contains("googlevideo.com/videoplayback")
        return if (isFile || isGoogleVideo) EngineType.HTTP_DIRECT else EngineType.MEDIA_STREAM
    }

    internal fun getUniqueFileName(destDir: String, originalFileName: String): String {
        val nameWithoutExt = originalFileName.substringBeforeLast('.')
        val ext =
            if (originalFileName.contains('.')) ".${originalFileName.substringAfterLast('.')}" else ""
        var counter = 1
        var candidate = "$nameWithoutExt ($counter)$ext"
        while (FileSystem.SYSTEM.exists("$destDir/$candidate".toPath())) {
            counter++
            candidate = "$nameWithoutExt ($counter)$ext"
        }
        return candidate
    }

}
