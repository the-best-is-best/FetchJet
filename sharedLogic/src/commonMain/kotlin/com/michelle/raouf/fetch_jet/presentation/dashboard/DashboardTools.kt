package com.michelle.raouf.fetch_jet.presentation.dashboard

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun DashboardViewModel.autoUpdateTools() {
    scope.launch {
        val changed = toolsMutex.withLock {
            runCatching {
                com.michelle.raouf.fetch_jet.feature.media_extractor.autoUpdateMediaTools(::reportToolProgress)
            }.getOrDefault(false)
        }
        _uiState.update {
            it.copy(
                isDownloadingYtDlp = false,
                toolDownloadName = null,
                toolDownloadFraction = null
            )
        }
        refreshToolStatus()
        if (changed) {
            _uiEffect.emit(DashboardUiEffect.ShowToast("Media tools updated"))
        }
    }
}

/**
 * While the Add dialog is open and the media engine is not ready yet (iOS boots the
 * embedded engine asynchronously; desktop/Android may be downloading yt-dlp), re-check
 * the tool status every couple of seconds so the UI flips to "ready" by itself.
 */
internal fun DashboardViewModel.pollToolStatusWhileNotReady() {
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

internal fun DashboardViewModel.refreshToolStatus() {
    scope.launch {
        val isYtDlp =
            com.michelle.raouf.fetch_jet.feature.media_extractor.isMediaExtractorToolInstalled()
        val isFfmpeg =
            com.michelle.raouf.fetch_jet.feature.media_extractor.isFfmpegToolInstalled()
        val jsRuntime =
            com.michelle.raouf.fetch_jet.feature.media_extractor.getJsRuntimeStatus()
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
                jsRuntimeName = jsRuntime.name,
                isJsRuntimeReady = jsRuntime.isReady,
                toolsDirectoryPath = toolsDir,
                torrentEngineStatus = torrentStatus,
                isTorrentEngineReady = torrentReady
            )
        }
    }
}
