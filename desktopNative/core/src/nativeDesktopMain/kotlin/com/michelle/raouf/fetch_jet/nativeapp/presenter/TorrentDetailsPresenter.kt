package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentPeerInfo
import com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentTrackerInfo
import com.michelle.raouf.fetch_jet.nativeapp.dto.ToneCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.TorrentDetailsDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TorrentPeerDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TorrentTrackerDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TrackerStateCode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.TorrentDetailsUi
import com.tbib.kmp_localization.LocalizationManager
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class TorrentLocal(val pendingTracker: String = "", val trackerRev: Int = 0)

private const val TORRENT_PEER_ROWS = 50

private fun ratioText(value: Float): String {
    val hundredths = if (value.isNaN() || value <= 0f) 0 else (value * 100f).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

private fun trackerName(url: String): String {
    val host = url.substringAfter("://", url).substringBefore('/').substringBefore('?')
    return host.ifBlank { url }
}

private fun trackerRow(tracker: TorrentTrackerInfo): TorrentTrackerDto {
    val s = LocalizationManager.Strings
    val state = when (tracker.state) {
        "working" -> TrackerStateCode.WORKING
        "updating" -> TrackerStateCode.UPDATING
        "error" -> TrackerStateCode.ERROR
        "disabled" -> TrackerStateCode.DISABLED
        else -> TrackerStateCode.WAITING
    }
    return TorrentTrackerDto(
        name = ltr(trackerName(tracker.url)),
        state = state.ordinal,
        stateLabel = when (state) {
            TrackerStateCode.WORKING -> s.torrentDetailsStateWorking
            TrackerStateCode.UPDATING -> s.torrentDetailsStateUpdating
            TrackerStateCode.WAITING -> s.torrentDetailsStateWaiting
            TrackerStateCode.ERROR -> s.torrentDetailsStateError
            TrackerStateCode.DISABLED -> s.torrentDetailsStateDisabled
        },
        tone = when (state) {
            TrackerStateCode.WORKING -> ToneCode.COMPLETED.ordinal
            TrackerStateCode.UPDATING -> ToneCode.PRIMARY.ordinal
            TrackerStateCode.ERROR -> ToneCode.ERROR.ordinal
            TrackerStateCode.WAITING, TrackerStateCode.DISABLED -> ToneCode.MUTED.ordinal
        },
        message = if (state == TrackerStateCode.ERROR) tracker.message.trim() else "",
    )
}

private fun peerRow(peer: TorrentPeerInfo, unit: String): TorrentPeerDto = TorrentPeerDto(
    address = ltr(peer.address),
    client = peer.client.trim(),
    down = "↓ " + formatSpeed(peer.downloadRate, unit),
    up = "↑ " + formatSpeed(peer.uploadRate, unit),
    percent = ltr("${(peer.progress.coerceIn(0f, 1f) * 100f).toInt()}%"),
    seed = peer.isSeed,
)

private fun buildTorrentDetails(id: String?, state: TorrentDetailsUi?, unit: String, ui: TorrentLocal): TorrentDetailsDto {
    if (id == null) return TorrentDetailsDto()
    val page = state?.takeIf { it.taskId == id }
    if (page != null && !page.supported) return TorrentDetailsDto(shown = true, id = id, trackerRev = ui.trackerRev)
    if (page == null || page.loading) {
        return TorrentDetailsDto(shown = true, id = id, supported = true, loading = true, trackerRev = ui.trackerRev)
    }
    val s = LocalizationManager.Strings
    val details = page.details
    val peers = details.peers.sortedByDescending { it.downloadRate + it.uploadRate }.take(TORRENT_PEER_ROWS)
    return TorrentDetailsDto(
        shown = true,
        id = id,
        supported = true,
        ratio = ltr(ratioText(page.ratio) + if (page.stopRatio > 0f) " / " + ratioText(page.stopRatio) else ""),
        uploaded = formatBytes(details.uploadedBytes),
        pieces = ltr("${details.piecesDone} / ${details.pieceCount}"),
        pieceSize = if (details.pieceSize > 0L) formatBytes(details.pieceSize) else "—",
        piecesFraction = if (details.pieceCount > 0) (details.piecesDone.toFloat() / details.pieceCount).coerceIn(0f, 1f) else 0f,
        trackersTitle = s.torrentDetailsTrackers + " · " + ltr(details.trackers.size.toString()),
        trackers = details.trackers.map { trackerRow(it) },
        trackerRev = ui.trackerRev,
        trackerError = when (page.trackerError) {
            "" -> ""
            "invalid" -> s.torrentDetailsTrackerInvalid
            else -> s.torrentDetailsTrackerRefused
        },
        peersTitle = s.torrentDetailsPeers + " · " + ltr(details.peers.size.toString()),
        peers = peers.map { peerRow(it, unit) },
        firstLast = details.firstLastPiecesFirst,
        magnet = details.magnetLink,
    )
}

internal fun Presenter.startTorrentDetails() {
    val openId = combine(vm.uiState, local) { state, ui ->
        val id = ui.selectedTaskId
        if (id == null || state.selectedTab == DashboardTab.SETTINGS) null
        else state.allTasks.firstOrNull { it.id == id }?.takeIf { it.engineType == EngineType.BITTORRENT }?.id
    }.distinctUntilChanged()
    val page = vm.uiState.map { it.torrentDetails }.distinctUntilChanged()
    scope.launch {
        var opened = false
        openId.collect { id ->
            torrentLocal.update { TorrentLocal(trackerRev = it.trackerRev + 1) }
            withContext(Dispatchers.Main) {
                if (id != null) vm.onEvent(DashboardUiEvent.OnTorrentDetailsOpened(id))
                else if (opened) vm.onEvent(DashboardUiEvent.OnTorrentDetailsClosed)
            }
            opened = id != null
        }
    }
    scope.launch {
        page.collect { current ->
            val pending = torrentLocal.value.pendingTracker
            if (pending.isNotEmpty() && current != null && current.details.trackers.any { it.url.trim().equals(pending, ignoreCase = true) }) {
                torrentLocal.update { if (it.pendingTracker == pending) it.copy(pendingTracker = "", trackerRev = it.trackerRev + 1) else it }
            }
        }
    }
    scope.launch {
        combine(
            openId,
            page,
            vm.uiState.map { it.speedDisplayUnit }.distinctUntilChanged(),
            AppLanguage.current,
            torrentLocal,
        ) { id, current, unit, _, ui -> buildTorrentDetails(id, current, unit, ui) }
            .collect { torrentDetails.publish(it) }
    }
}

private fun Presenter.openTorrentId(): String? =
    local.value.selectedTaskId?.takeIf { id -> task(id)?.engineType == EngineType.BITTORRENT }

internal fun Presenter.torrentAddTracker(url: String) {
    val id = openTorrentId() ?: return
    val clean = url.trim()
    if (clean.isEmpty()) return
    torrentLocal.update { it.copy(pendingTracker = clean) }
    vm.onEvent(DashboardUiEvent.OnTorrentAddTracker(id, clean))
}

internal fun Presenter.torrentRecheck() {
    openTorrentId()?.let { vm.onEvent(DashboardUiEvent.OnTorrentRecheck(it)) }
}

internal fun Presenter.torrentSetFirstLast(on: Boolean) {
    openTorrentId()?.let { vm.onEvent(DashboardUiEvent.OnTorrentFirstLastChanged(it, on)) }
}

internal fun Presenter.torrentCopyMagnet() {
    openTorrentId()?.let { vm.onEvent(DashboardUiEvent.OnTorrentCopyMagnet(it)) }
}
