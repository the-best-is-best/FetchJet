package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.feature.http_downloader.LinkProbeResult
import com.michelle.raouf.fetch_jet.feature.http_downloader.probeLink
import com.michelle.raouf.fetch_jet.feature.site_grabber.LinkKind
import com.michelle.raouf.fetch_jet.feature.site_grabber.LinkKinds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val LINK_PROBE_SETTLE_MS = 500L
internal const val URL_SETTLE_TYPED_MS = 700L
internal const val URL_SETTLE_PASTE_MS = 150L
internal const val URL_PASTE_JUMP = 8

internal suspend fun DashboardViewModel.probedLink(shown: String): LinkProbeResult {
    val key = shown.trim()
    linkProbeCache[key]?.let { return it }
    val running = linkProbeFlights[key]
    if (running != null) return running.await()
    val flight = CompletableDeferred<LinkProbeResult>()
    linkProbeFlights[key] = flight
    try {
        val url = com.michelle.raouf.fetch_jet.feature.queue_manager.codeHostRawUrl(key)
        val result = probeLink(url, headersForLink(key))
        linkProbeCache[key] = result
        flight.complete(result)
        return result
    } catch (e: CancellationException) {
        flight.cancel()
        throw e
    } finally {
        linkProbeFlights.remove(key)
    }
}

internal fun DashboardViewModel.scheduleLinkProbe() {
    val state = _uiState.value
    val url = state.inputUrl.trim()
    if (state.grabShape && state.linkProbeUrl == url && state.linkKind != LinkKind.UNKNOWN) return
    linkProbeJob?.cancel()
    linkProbeJob = null
    if (!state.grabShape || state.siteGrab.running) return
    linkProbeJob = scope.launch {
        if (linkProbeCache[url] == null) delay(LINK_PROBE_SETTLE_MS)
        val kind = LinkKinds.of(probedLink(url))
        _uiState.update { if (it.inputUrl.trim() == url) it.copy(linkKind = kind, linkProbeUrl = url) else it }
    }
}

internal fun DashboardViewModel.cancelLinkProbe() {
    linkProbeJob?.cancel()
    linkProbeJob = null
    linkProbeCache.clear()
}
