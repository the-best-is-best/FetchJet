package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaUrls
import com.michelle.raouf.fetch_jet.feature.media_extractor.markDownloadedBefore
import com.michelle.raouf.fetch_jet.feature.media_extractor.playlistSizeFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private const val SIZE_READERS = 2

/** The playlist with the videos already in the downloads list marked and unticked. */
internal fun DashboardViewModel.markPlaylist(playlist: MediaPlaylist): MediaPlaylist {
    val known = queueRepository.allTasksFlow.value.flatMap { listOfNotNull(it.url, it.originalUrl) }
    return playlist.markDownloadedBefore(known)
}

/**
 * Reads each video's qualities in the background (two at a time, in the order shown) to show
 * its size for the chosen quality. Stops when the dialog shows something else.
 */
internal fun DashboardViewModel.startPlaylistSizes() {
    playlistSizeJob?.cancel()
    val start = _uiState.value
    val playlist = start.inspectedPlaylist ?: return
    if (playlist.fromBrowser) return
    val link = start.inputUrl
    val order = playlist.entries.map { it.index to it.url }
    playlistSizeJob = scope.launch {
        var next = 0
        val pick = kotlinx.coroutines.sync.Mutex()
        val workers = (1..SIZE_READERS).map {
            launch {
                while (true) {
                    val item = pick.withLock { order.getOrNull(next++) } ?: break
                    val state = _uiState.value
                    if (state.inspectedPlaylist == null || state.inputUrl != link || !state.isAddDialogVisible) return@launch
                    val key = MediaUrls.canonical(item.second)
                    val cached = playlistQualitiesLock.withLock { playlistQualities[key] }
                    val qualities = cached ?: try {
                        queueRepository.inspectMediaQualities(item.second, headersForLink(item.second))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (cached == null) playlistQualitiesLock.withLock { playlistQualities[key] = qualities }
                    setPlaylistSize(item.first, playlistSizeFor(_uiState.value.playlistQuality, qualities))
                }
            }
        }
        workers.forEach { it.join() }
    }
}

/** After a quality change: every size already read is worked out again for the new quality. */
internal fun DashboardViewModel.refreshPlaylistSizes() {
    scope.launch {
        val cache = playlistQualitiesLock.withLock { playlistQualities.toMap() }
        _uiState.update { st ->
            val pl = st.inspectedPlaylist ?: return@update st
            st.copy(inspectedPlaylist = pl.copy(entries = pl.entries.map { e ->
                val qualities = cache[MediaUrls.canonical(e.url)]
                if (qualities == null) e else e.copy(size = playlistSizeFor(st.playlistQuality, qualities))
            }))
        }
    }
}

private fun DashboardViewModel.setPlaylistSize(index: Int, size: Long) {
    _uiState.update { st ->
        val pl = st.inspectedPlaylist ?: return@update st
        st.copy(inspectedPlaylist = pl.copy(entries = pl.entries.map { if (it.index == index) it.copy(size = size) else it }))
    }
}
