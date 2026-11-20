package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.update
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.site_grabber.GrabOptions
import com.michelle.raouf.fetch_jet.feature.site_grabber.GrabResult
import com.michelle.raouf.fetch_jet.feature.site_grabber.HttpPageFetcher
import com.michelle.raouf.fetch_jet.feature.site_grabber.PageLinks
import com.michelle.raouf.fetch_jet.feature.site_grabber.SiteGrabber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun grabGroupName(folder: String): String {
    val s = LocalizationManager.Strings
    return when (folder) {
        "Video" -> s.grabGroupVideo
        "Music" -> s.grabGroupMusic
        "Archives" -> s.grabGroupArchives
        "Documents" -> s.grabGroupDocuments
        "Programs" -> s.grabGroupPrograms
        "Images" -> s.grabGroupImages
        else -> s.grabGroupOther
    }
}

fun grabDepthName(depth: Int): String {
    val s = LocalizationManager.Strings
    return when (depth) {
        2 -> s.grabDepth2
        3 -> s.grabDepth3
        else -> s.grabDepth1
    }
}

fun grabDepthShortName(depth: Int): String {
    val s = LocalizationManager.Strings
    return when (depth) {
        2 -> s.grabDepthShort2
        3 -> s.grabDepthShort3
        else -> s.grabDepthShort1
    }
}

internal fun DashboardViewModel.handleSiteGrabEvent(event: DashboardUiEvent) {
    when (event) {
        DashboardUiEvent.OnGrabPageClicked -> startPageGrab()
        DashboardUiEvent.OnGrabCancelClicked -> stopPageGrab()
        is DashboardUiEvent.OnGrabDepthChanged ->
            _uiState.update { it.copy(siteGrab = it.siteGrab.copy(depth = event.depth.coerceIn(1, 3))) }

        is DashboardUiEvent.OnGrabGroupToggled -> _uiState.update { st ->
            st.copy(linkList = st.linkList.map { if (it.folder == event.folder && !it.downloadedBefore) it.copy(isSelected = event.selected) else it })
        }

        else -> Unit
    }
}

internal fun DashboardViewModel.stopPageGrab() {
    siteGrabJob?.cancel()
    siteGrabJob = null
    _uiState.update { it.copy(siteGrab = it.siteGrab.copy(running = false)) }
}

internal fun DashboardViewModel.startPageGrab() {
    val state = _uiState.value
    val url = state.inputUrl.trim()
    if (!state.canGrabPage || state.siteGrab.running) return
    siteGrabJob?.cancel()
    _uiState.update { it.copy(siteGrab = it.siteGrab.copy(running = true, pagesRead = 0, note = ""), errorMessage = null) }
    siteGrabJob = scope.launch {
        val strings = LocalizationManager.Strings
        try {
            val fetcher = HttpPageFetcher(headersForLink(url))
            val result = SiteGrabber(fetcher).grab(url, GrabOptions(depth = state.siteGrab.depth)) { progress ->
                _uiState.update { it.copy(siteGrab = it.siteGrab.copy(pagesRead = progress.pagesRead)) }
            }
            val known = queueRepository.allTasksFlow.value.flatMap { listOfNotNull(it.url, it.originalUrl) }
                .map { it.trim().lowercase() }.toSet()
            val entries = result.files.map { file ->
                val before = file.url.trim().lowercase() in known
                LinkListEntry(
                    isSelected = !before,
                    downloadedBefore = before,
                    url = file.url,
                    engine = EngineType.HTTP_DIRECT,
                    fileName = file.name,
                    sizeBytes = file.size,
                    folder = file.folder,
                    sourcePage = file.page
                )
            }
            val host = PageLinks.hostOf(url).removePrefix("www.")
            _uiState.update {
                if (it.inputUrl.trim() != url) it.copy(siteGrab = it.siteGrab.copy(running = false))
                else it.copy(
                    linkList = entries,
                    linkListFolder = if (entries.isEmpty()) "" else host,
                    siteGrab = it.siteGrab.copy(running = false, pagesRead = result.pagesRead, note = grabNote(result), title = result.title)
                )
            }
            fillSizes(fetcher, url, entries.filter { it.sizeBytes < 0 }.take(SIZE_PROBES))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(siteGrab = it.siteGrab.copy(running = false, note = e.message ?: strings.grabNothing))
            }
        }
    }
}

private const val SIZE_PROBES = 150

private suspend fun DashboardViewModel.fillSizes(fetcher: HttpPageFetcher, pageUrl: String, wanted: List<LinkListEntry>) {
    if (wanted.isEmpty()) return
    val gate = Semaphore(6)
    coroutineScope {
        wanted.map { entry ->
            async {
                gate.withPermit {
                    val size = runCatching { fetcher.probe(entry.url) }.getOrNull()?.size ?: -1L
                    if (size >= 0) _uiState.update { st ->
                        if (st.inputUrl.trim() != pageUrl) st
                        else st.copy(linkList = st.linkList.map { if (it.url == entry.url) it.copy(sizeBytes = size) else it })
                    }
                }
            }
        }.awaitAll()
    }
}

internal fun grabNote(result: GrabResult): String {
    val s = LocalizationManager.Strings
    val lines = ArrayList<String>()
    lines.add(
        if (result.files.isEmpty()) s.grabNothing
        else s.grabFound.replace("%1", result.files.size.toString()).replace("%2", result.pagesRead.toString())
    )
    if (result.refused > 0) lines.add(s.grabRefused.replace("%1", result.refused.toString()))
    if (result.needScript > 0) lines.add(s.grabScript.replace("%1", result.needScript.toString()))
    if (result.reachedLimit) lines.add(s.grabLimit)
    return lines.joinToString("\n")
}
