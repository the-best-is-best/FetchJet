package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentDetails
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TORRENT_DETAILS_POLL_MS = 2000L

private val TRACKER_SCHEMES = listOf("http://", "https://", "udp://", "ws://", "wss://")

private var torrentDetailsJob: Job? = null

internal fun DashboardViewModel.handleTorrentDetailsEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnTorrentDetailsOpened -> openTorrentDetails(event.taskId)

        DashboardUiEvent.OnTorrentDetailsClosed -> {
            torrentDetailsJob?.cancel()
            torrentDetailsJob = null
            _uiState.update { it.copy(torrentDetails = null) }
        }

        is DashboardUiEvent.OnTorrentAddTracker -> addTorrentTracker(event.taskId, event.url)

        is DashboardUiEvent.OnTorrentRecheck -> scope.launch {
            runCatching { queueRepository.torrentEngine.recheck(event.taskId) }
            refreshTorrentDetails(event.taskId)
        }

        is DashboardUiEvent.OnTorrentFirstLastChanged -> scope.launch {
            runCatching { queueRepository.torrentEngine.setFirstLastPiecesFirst(event.taskId, event.on) }
            refreshTorrentDetails(event.taskId)
        }

        is DashboardUiEvent.OnTorrentCopyMagnet -> copyTorrentMagnet(event.taskId)

        is DashboardUiEvent.OnTorrentStopRatioChanged -> {
            BehaviorSettings.setTorrentStopRatio(event.ratio)
            val saved = BehaviorSettings.torrentStopRatio.value
            _uiState.update { state ->
                val page = state.torrentDetails ?: return@update state
                state.copy(torrentDetails = page.copy(stopRatio = saved))
            }
        }

        else -> Unit
    }
}

internal fun isValidTrackerUrl(url: String): Boolean {
    val scheme = TRACKER_SCHEMES.firstOrNull { url.startsWith(it, ignoreCase = true) } ?: return false
    return url.length > scheme.length && url.none { it.isWhitespace() }
}

internal fun torrentShareRatio(details: TorrentDetails): Float =
    if (details.downloadedBytes > 0L) (details.uploadedBytes.toDouble() / details.downloadedBytes).toFloat() else 0f

private fun DashboardViewModel.openTorrentDetails(taskId: String) {
    torrentDetailsJob?.cancel()
    val supported = queueRepository.torrentEngine.supportsDetails
    _uiState.update {
        it.copy(
            torrentDetails = TorrentDetailsUi(
                taskId = taskId,
                supported = supported,
                loading = true,
                stopRatio = BehaviorSettings.torrentStopRatio.value
            )
        )
    }
    torrentDetailsJob = scope.launch {
        while (isActive && _uiState.value.torrentDetails?.taskId == taskId) {
            refreshTorrentDetails(taskId)
            if (!supported) break
            delay(TORRENT_DETAILS_POLL_MS)
        }
    }
}

private fun DashboardViewModel.refreshTorrentDetails(taskId: String) {
    if (_uiState.value.torrentDetails?.taskId != taskId) return
    val details = runCatching { queueRepository.torrentEngine.details(taskId) }.getOrNull()
    _uiState.update { state ->
        val page = state.torrentDetails?.takeIf { it.taskId == taskId } ?: return@update state
        state.copy(
            torrentDetails = page.copy(
                loading = false,
                details = details ?: page.details,
                ratio = details?.let { torrentShareRatio(it) } ?: page.ratio
            )
        )
    }
}

private fun DashboardViewModel.setTrackerError(taskId: String, code: String) {
    _uiState.update { state ->
        val page = state.torrentDetails?.takeIf { it.taskId == taskId } ?: return@update state
        state.copy(torrentDetails = page.copy(trackerError = code))
    }
}

private fun DashboardViewModel.addTorrentTracker(taskId: String, rawUrl: String) {
    val url = rawUrl.trim()
    if (!isValidTrackerUrl(url)) {
        setTrackerError(taskId, "invalid")
        return
    }
    scope.launch {
        val added = runCatching { queueRepository.torrentEngine.addTracker(taskId, url) }.getOrDefault(false)
        setTrackerError(taskId, if (added) "" else "refused")
        if (added) refreshTorrentDetails(taskId)
    }
}

private fun DashboardViewModel.copyTorrentMagnet(taskId: String) {
    scope.launch {
        val shown = _uiState.value.torrentDetails?.takeIf { it.taskId == taskId }?.details?.magnetLink.orEmpty()
        val link = shown.ifBlank {
            runCatching { queueRepository.torrentEngine.details(taskId) }.getOrNull()?.magnetLink.orEmpty()
        }.ifBlank {
            queueRepository.allTasksFlow.value.firstOrNull { it.id == taskId }?.url?.trim()
                ?.takeIf { it.startsWith("magnet:", ignoreCase = true) }.orEmpty()
        }
        if (link.isBlank()) return@launch
        _uiEffect.emit(DashboardUiEffect.CopyText(link))
        _uiEffect.emit(DashboardUiEffect.ShowToast(LocalizationManager.Strings.copied))
    }
}
