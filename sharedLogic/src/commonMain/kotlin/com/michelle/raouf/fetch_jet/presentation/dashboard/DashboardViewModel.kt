package com.michelle.raouf.fetch_jet.presentation.dashboard


import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

    private val queueRepository: QueueManagerRepository = QueueManagerRepository.getInstance()

) {

    private val settingsRepository =
        com.michelle.raouf.fetch_jet.core.settings.AppSettingsRepository(queueRepository.pathResolver)


    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    private val _uiState = MutableStateFlow(

        DashboardUiState(

            destinationPath = queueRepository.pathResolver.getDownloadsDirectory()

        )

    )

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()


    private val _uiEffect = MutableSharedFlow<DashboardUiEffect>()

    val uiEffect: SharedFlow<DashboardUiEffect> = _uiEffect.asSharedFlow()


    private var pendingExternalUrl: String? = null
    private var pendingExternalHeaders: Map<String, String> = emptyMap()
    private var pendingExternalFileName: String? = null

    /** The playlist link for which the user chose "download only this video". */
    private var singleVideoUrl: String? = null


    init {

        observeQueue()

        val savedSettings = settingsRepository.loadSettings()

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


    private fun persistCurrentSettings() {

        val s = _uiState.value

        settingsRepository.saveSettings(

            com.michelle.raouf.fetch_jet.core.settings.AppSettings(

                destinationPath = s.destinationPath,

                disableTorrentUpload = s.disableTorrentUpload,

                isDarkMode = s.isDarkMode,

                globalDownloadSpeedLimitBytes = s.globalDownloadSpeedLimitBytes,

                globalUploadSpeedLimitBytes = s.globalUploadSpeedLimitBytes,

                speedDisplayUnit = s.speedDisplayUnit

            )

        )

    }


    fun handleExternalUrl(url: String, cookie: String = "") =
        handleExternalRequest(
            com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest(
                url,
                cookie
            )
        )

    /** One link from the browser: open the Add dialog pre-filled, remember its headers. */
    fun handleExternalRequest(request: com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest) {
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
     * The browser already chose everything (quality picked in the extension popup, or a
     * "download now" click): queue it without showing the Add dialog.
     */
    private fun startExternalDirectly(request: com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest) {
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
            val fileName = rawName.ifBlank { null }
                ?.let { queueRepository.pathResolver.sanitizeFileName(it) }
                ?.let { freeFileName(destDir, it) }
            val engine =
                if (qualities.isNotEmpty()) EngineType.MEDIA_STREAM else engineForDirectFile(request.url)
            if (engine == EngineType.MEDIA_STREAM && qualities.isEmpty()) {
                // A video page without a chosen quality still needs the inspector.
                pendingExternalUrl = request.url
                pendingExternalHeaders = request.toRequestHeaders()
                pendingExternalFileName = null
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
                _uiEffect.emit(DashboardUiEffect.ShowToast("Started download: ${task.fileName.ifBlank { task.url }}"))
            }.onFailure { error ->
                _uiEffect.emit(DashboardUiEffect.ShowToast("Could not start download: ${error.message ?: "unknown error"}"))
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
                    "Queued $added download${if (added == 1) "" else "s"} from the browser" +
                            (if (skipped > 0) " ($skipped skipped)" else "")
                )
            )
        }
    }


    private var toolStatusPollJob: kotlinx.coroutines.Job? = null

    /**
     * While the Add dialog is open and the media engine is not ready yet (iOS boots the
     * embedded engine asynchronously; desktop/Android may be downloading yt-dlp), re-check
     * the tool status every couple of seconds so the UI flips to "ready" by itself.
     */
    private fun pollToolStatusWhileNotReady() {
        toolStatusPollJob?.cancel()
        toolStatusPollJob = scope.launch {
            repeat(150) { // ~5 minutes
                val st = _uiState.value
                if (!st.isAddDialogVisible || st.isYtDlpInstalled) return@launch
                kotlinx.coroutines.delay(2000)
                val installed =
                    com.michelle.raouf.fetch_jet.feature.media_extractor.isMediaExtractorToolInstalled()
                val status =
                    com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaExtractorToolStatus()
                _uiState.update { it.copy(isYtDlpInstalled = installed, ytDlpStatus = status) }
            }
        }
    }

    private fun refreshToolStatus() {
        scope.launch {
            val isYtDlp =
                com.michelle.raouf.fetch_jet.feature.media_extractor.isMediaExtractorToolInstalled()
            val isFfmpeg =
                com.michelle.raouf.fetch_jet.feature.media_extractor.isFfmpegToolInstalled()
            val toolsDir =
                com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaToolsDirectory()
            val statusText =
                com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaExtractorToolStatus()
            val torrentStatus = runCatching { queueRepository.torrentEngine.engineStatus() }
                .getOrElse { "BitTorrent engine error: ${it.message}" }
            val torrentReady =
                runCatching { queueRepository.torrentEngine.isSupported }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    ytDlpStatus = statusText,
                    isYtDlpInstalled = isYtDlp,
                    isFfmpegInstalled = isFfmpeg,
                    toolsDirectoryPath = toolsDir,
                    torrentEngineStatus = torrentStatus,
                    isTorrentEngineReady = torrentReady
                )
            }
        }
    }

    private fun observeQueue() {

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


    private val detectLinkTypeUseCase =

        com.michelle.raouf.fetch_jet.feature.queue_manager.DetectLinkTypeUseCase()


    fun onEvent(event: DashboardUiEvent) {

        when (event) {

            is DashboardUiEvent.OnEngineSelected -> {
                _uiState.update {
                    it.copy(
                        detectedEngine = event.engine,
                        inspectedMediaQualities = emptyList(),
                        inspectedPlaylist = null,
                        inspectedTorrentFiles = emptyList(),
                        inspectError = null,
                        inspectMediaError = null
                    )
                }
            }

            is DashboardUiEvent.OnUrlChanged -> {

                // App Store edition: media and torrent engines are not shipped.
                val engine = when (val detected = detectLinkTypeUseCase(event.url)) {
                    EngineType.MEDIA_STREAM -> if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.mediaExtractor) detected else EngineType.HTTP_DIRECT
                    EngineType.BITTORRENT -> if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.torrent) detected else EngineType.HTTP_DIRECT
                    else -> detected
                }

                val defaultQualities =

                    emptyList<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry>()



                _uiState.update {

                    it.copy(

                        inputUrl = event.url,

                        detectedEngine = engine,

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
                        scheduleStartMs = 0L,
                        mediaDurationSeconds = 0L
                    )

                }

            }


            is DashboardUiEvent.OnDestinationPathChanged -> {

                _uiState.update { it.copy(destinationPath = event.path) }

                persistCurrentSettings()

            }


            is DashboardUiEvent.OnDisableTorrentUploadToggled -> {

                _uiState.update { it.copy(disableTorrentUpload = event.disable) }

                persistCurrentSettings()

            }


            is DashboardUiEvent.OnToggleAddDialog -> {

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
                        scheduleStartMs = 0L,

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


            is DashboardUiEvent.OnGlobalDownloadSpeedLimitChanged -> {

                _uiState.update {

                    it.copy(

                        globalDownloadSpeedLimitBytes = event.limitBytesPerSec

                    )

                }

                queueRepository.setGlobalDownloadSpeedLimit(event.limitBytesPerSec)

                persistCurrentSettings()

            }


            is DashboardUiEvent.OnGlobalUploadSpeedLimitChanged -> {

                _uiState.update {

                    it.copy(

                        globalUploadSpeedLimitBytes = event.limitBytesPerSec

                    )

                }

                queueRepository.setGlobalUploadSpeedLimit(event.limitBytesPerSec)

                persistCurrentSettings()

            }


            is DashboardUiEvent.OnSpeedDisplayUnitChanged -> {
                _uiState.update { it.copy(speedDisplayUnit = event.unit) }
                persistCurrentSettings()
            }

            DashboardUiEvent.OnApplySettingsClicked -> {

                queueRepository.setGlobalDownloadSpeedLimit(_uiState.value.globalDownloadSpeedLimitBytes)

                queueRepository.setGlobalUploadSpeedLimit(_uiState.value.globalUploadSpeedLimitBytes)

                persistCurrentSettings()

            }


            DashboardUiEvent.OnThemeToggled -> {

                _uiState.update { it.copy(isDarkMode = !it.isDarkMode) }

                persistCurrentSettings()

            }


            DashboardUiEvent.OnAddDownloadClicked -> addDownload()

            is DashboardUiEvent.OnPauseClicked -> {

                scope.launch { queueRepository.pauseTask(event.taskId) }

            }


            is DashboardUiEvent.OnResumeClicked -> {

                scope.launch { queueRepository.resumeTask(event.taskId) }

            }


            is DashboardUiEvent.OnCancelClicked, is DashboardUiEvent.OnRemoveClicked -> {

                val taskId = when (event) {

                    is DashboardUiEvent.OnCancelClicked -> event.taskId

                    is DashboardUiEvent.OnRemoveClicked -> event.taskId

                    else -> return

                }

                _uiState.update { it.copy(removeConfirmationTaskId = taskId) }

            }


            is DashboardUiEvent.OnConfirmRemoveClicked -> {

                val taskId = _uiState.value.removeConfirmationTaskId

                if (taskId != null) {

                    _uiState.update { it.copy(removeConfirmationTaskId = null) }

                    scope.launch { queueRepository.removeTask(taskId, event.deleteFile) }

                }

            }


            DashboardUiEvent.OnConfirmBrowserFallback -> {
                val url = _uiState.value.browserFallbackUrl
                _uiState.update { it.copy(browserFallbackUrl = null) }
                if (url != null) {
                    com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge.triggerFallback(
                        url
                    )
                }
            }
            DashboardUiEvent.OnDismissBrowserFallback -> {
                _uiState.update { it.copy(browserFallbackUrl = null) }
            }
            DashboardUiEvent.OnConfirmCancelClicked -> {

                val taskId = _uiState.value.removeConfirmationTaskId

                if (taskId != null) {

                    _uiState.update { it.copy(removeConfirmationTaskId = null) }

                    scope.launch { queueRepository.removeTask(taskId, false) }

                }

            }


            DashboardUiEvent.OnResumeExistingDuplicateClicked -> {
                val duplicate = _uiState.value.duplicateTaskAlert
                if (duplicate != null) {
                    scope.launch {
                        queueRepository.resumeTask(duplicate.id)
                        _uiState.update {
                            it.copy(
                                duplicateTaskAlert = null,
                                isAddDialogVisible = false,
                                inputUrl = ""
                            )
                        }
                    }
                }
            }

            DashboardUiEvent.OnRestartDuplicateClicked -> {
                val duplicate = _uiState.value.duplicateTaskAlert
                if (duplicate != null) {
                    scope.launch {
                        queueRepository.removeTask(duplicate.id, deleteFile = true)
                        _uiState.update { it.copy(duplicateTaskAlert = null) }
                        addDownload(forceOverwrite = true, skipDuplicateCheck = true)
                    }
                }
            }

            DashboardUiEvent.OnDismissDuplicateDialog -> {
                _uiState.update { it.copy(duplicateTaskAlert = null) }
            }

            DashboardUiEvent.OnConfirmOverwriteClicked -> {
                _uiState.update { it.copy(fileConflictAlert = null) }
                addDownload(forceOverwrite = true, skipDuplicateCheck = true)
            }

            is DashboardUiEvent.OnSaveAsCopyClicked -> {
                val newName = event.newFileName
                _uiState.update { it.copy(fileConflictAlert = null) }
                addDownload(
                    forceOverwrite = false,
                    overrideFileName = newName,
                    skipDuplicateCheck = true
                )
            }

            DashboardUiEvent.OnDismissFileConflictDialog -> {
                _uiState.update { it.copy(fileConflictAlert = null) }
            }

            DashboardUiEvent.OnDismissCancelDialog, DashboardUiEvent.OnDismissRemoveDialog -> {

                _uiState.update { it.copy(removeConfirmationTaskId = null) }

            }


            is DashboardUiEvent.OnDownloadSpeedLimitChanged -> {

                _uiState.update { it.copy(selectedDownloadSpeedLimitBytes = event.limitBytesPerSec) }

            }

            is DashboardUiEvent.OnUploadSpeedLimitChanged -> {

                _uiState.update { it.copy(selectedUploadSpeedLimitBytes = event.limitBytesPerSec) }

            }


            is DashboardUiEvent.OnTabSelected -> {

                _uiState.update { state ->

                    state.copy(

                        selectedTab = event.tab,

                        filteredTasks = filterTasks(state.allTasks, event.tab, state.sortOption)

                    )

                }

            }

            is DashboardUiEvent.OnSortChanged -> {
                _uiState.update { state ->
                    state.copy(
                        sortOption = event.sort,
                        filteredTasks = filterTasks(state.allTasks, state.selectedTab, event.sort)
                    )
                }
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

            is DashboardUiEvent.OnToggleClipRange -> {
                val hasMediaData = _uiState.value.inspectedMediaQualities.isNotEmpty()
                val durationSec = _uiState.value.mediaDurationSeconds
                if ((!hasMediaData || durationSec <= 0L) && event.enabled) {
                    return
                }
                val fullEndTime = if (durationSec > 0) formatSecondsToHhMm(durationSec) else "00:10"
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


            is DashboardUiEvent.OnTaskTorrentFileSelectionToggled -> {

                queueRepository.updateTorrentFileSelection(

                    event.taskId,

                    event.fileIndex,

                    event.isSelected

                )

            }


            is DashboardUiEvent.OnTaskTorrentFilePriorityChanged -> {

                queueRepository.updateTorrentFilePriority(

                    event.taskId,

                    event.fileIndex,

                    event.priority

                )

            }


            DashboardUiEvent.OnDownloadYtDlpClicked -> {

                _uiState.update { it.copy(isDownloadingYtDlp = true) }

                scope.launch {

                    val success =

                        com.michelle.raouf.fetch_jet.feature.media_extractor.downloadMediaExtractorTool()

                    // Same button also installs the BitTorrent engine if it is missing.
                    val torrentInstall =
                        runCatching { queueRepository.torrentEngine.installEngine() }
                            .getOrElse { Result.failure(it) }
                    val torrentStatus2 =
                        runCatching { queueRepository.torrentEngine.engineStatus() }
                            .getOrElse { "BitTorrent engine error: ${it.message}" }
                    val torrentReady2 = torrentInstall.isSuccess ||
                            runCatching { queueRepository.torrentEngine.isSupported }.getOrDefault(
                                false
                            )
                    val newStatus =

                        com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaExtractorToolStatus()

                    val isYtDlp =
                        com.michelle.raouf.fetch_jet.feature.media_extractor.isMediaExtractorToolInstalled()

                    val isFfmpeg =
                        com.michelle.raouf.fetch_jet.feature.media_extractor.isFfmpegToolInstalled()

                    val toolsDir =
                        com.michelle.raouf.fetch_jet.feature.media_extractor.getMediaToolsDirectory()

                    _uiState.update {

                        it.copy(

                            isDownloadingYtDlp = false,

                            ytDlpStatus = newStatus,

                            isYtDlpInstalled = isYtDlp,

                            isFfmpegInstalled = isFfmpeg,

                            toolsDirectoryPath = toolsDir,
                            torrentEngineStatus = torrentInstall.exceptionOrNull()?.let { e ->
                                "BitTorrent engine install failed: ${e.message}"
                            } ?: torrentStatus2,
                            isTorrentEngineReady = torrentReady2

                        )

                    }

                    val msg =

                        if (success) "Media tools downloaded successfully!" else "Failed to download media tools"

                    _uiEffect.emit(DashboardUiEffect.ShowToast(msg))

                }

            }

        }

    }


    private fun inspectTorrent() {

        val url = _uiState.value.inputUrl

        if (url.isBlank()) {

            _uiState.update { it.copy(inspectError = "Please enter a torrent or magnet link first") }

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

                            inspectError = err.message ?: "Failed to parse torrent metadata"

                        )

                    }

                }

        }

    }


    private fun inspectMedia() {

        val url = _uiState.value.inputUrl

        if (url.isBlank()) {

            _uiState.update { it.copy(inspectMediaError = "Please enter a media URL first") }

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
                    queueRepository.inspectMediaQualities(url)
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
                            inspectMediaError = "Unable to fetch video formats. Please verify the URL or check your internet connection."
                        )
                    }
                } else {
                    val mediaTitle =
                        qualities.firstOrNull { !it.videoTitle.isNullOrBlank() }?.videoTitle
                    val duration =
                        qualities.map { it.durationSeconds }.maxOrNull()?.coerceAtLeast(0L) ?: 0L
                    val fullEnd = if (duration > 0) formatSecondsToHhMm(duration) else "00:10"

                    _uiState.update {
                        it.copy(
                            isInspectingMedia = false,
                            inspectedMediaQualities = qualities,
                            inspectedMediaTitle = mediaTitle,
                            mediaDurationSeconds = duration,
                            inspectMediaError = null,
                            clipStartTime = if (it.isClipRangeEnabled && (it.clipStartTime.isBlank() || it.clipStartTime == "00:00")) "00:00" else it.clipStartTime,
                            clipEndTime = if (it.isClipRangeEnabled && (it.clipEndTime.isBlank() || it.clipEndTime == "00:00")) fullEnd else it.clipEndTime
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInspectingMedia = false,
                        inspectedMediaQualities = emptyList(),
                        inspectedPlaylist = null,
                        inspectMediaError = e.message ?: "Failed to inspect media qualities"
                    )
                }
            }
        }

    }


    private fun isSameDownload(task: DownloadTask, newUrl: String): Boolean {
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

    private fun resolveTargetFileName(
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
            currentState.detectedEngine == EngineType.HTTP_DIRECT
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
    private fun freeFileName(destDir: String, fileName: String): String =
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
    private fun engineForDirectFile(url: String): EngineType {
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

    private fun getUniqueFileName(destDir: String, originalFileName: String): String {
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

    private fun addDownload(
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

        val selectedQuality = currentState.inspectedMediaQualities.firstOrNull { it.isSelected }
        val videoTitle = currentState.inspectedMediaTitle ?: selectedQuality?.videoTitle
        val customFileName = overrideFileName ?: if (!videoTitle.isNullOrBlank()) {
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
                    ?: targetName.takeUnless { currentState.detectedEngine == EngineType.HTTP_DIRECT && it.startsWith("download_") && !it.contains('.') },
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
                requestHeaders = if (pendingExternalUrl == url) pendingExternalHeaders else emptyMap(),
                scheduledAtMs = currentState.scheduleStartMs
            ).onSuccess { task ->
                pendingExternalUrl = null
                pendingExternalHeaders = emptyMap()
                pendingExternalFileName = null
                _uiState.update {
                    it.copy(
                        inputUrl = "",
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
                        mediaDurationSeconds = 0L
                    )
                }
                _uiEffect.emit(DashboardUiEffect.ShowToast("Started download: ${task.fileName.ifBlank { task.url }}"))
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

    /**
     * Queues every selected video of a playlist as its own download, all with the chosen
     * quality. The download queue (Settings -> downloads at the same time) runs them a few at a
     * time; a schedule applies to all of them.
     */
    private fun addPlaylist(
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
                    isLoading = false,
                    isAddDialogVisible = false,
                    inspectedPlaylist = null,
                    inspectedMediaQualities = emptyList(),
                    inspectedMediaTitle = null,
                    scheduleStartMs = 0L,
                    errorMessage = if (failed > 0) "$failed video(s) could not be added." else null
                )
            }
            singleVideoUrl = null
            _uiEffect.emit(DashboardUiEffect.ShowToast("Added $added videos from \"${playlist.title}\""))
        }
    }

    /**
     * Filters by tab, then sorts. Unfinished downloads always come before completed ones
     * (the UI shows them as two groups), and the chosen sort applies inside each group.
     */
    private fun filterTasks(
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
        return unfinished.sortedWith(comparator) + finished.sortedWith(comparator)
    }

    private fun filterByTab(tasks: List<DownloadTask>, tab: DashboardTab): List<DownloadTask> {

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

    private fun formatSecondsToHhMm(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }

    private fun normalizeHhMmForYtDlp(hhMm: String): String {
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

    private fun parseHhMmToSeconds(hhMm: String): Long {
        if (hhMm.isBlank()) return 0L
        val parts = hhMm.split(":")
        val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val s = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        return (h * 3600L + m * 60L + s).coerceAtLeast(0L)
    }

}
