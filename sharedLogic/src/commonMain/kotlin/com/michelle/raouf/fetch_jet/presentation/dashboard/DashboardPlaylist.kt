package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaUrls
import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import com.michelle.raouf.fetch_jet.feature.media_extractor.forLanguages
import com.michelle.raouf.fetch_jet.feature.media_extractor.markDownloadedBefore
import com.michelle.raouf.fetch_jet.feature.media_extractor.mergeSubtitleLanguages
import com.michelle.raouf.fetch_jet.feature.media_extractor.playlistSizeFor
import com.michelle.raouf.fetch_jet.feature.media_extractor.subtitlesFoundFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private const val SIZE_READERS = 2

/** The Add-dialog link the running size reader belongs to. */
private var playlistSizeLink: String? = null

/** Each read video's own subtitle tracks (by canonical link), guarded by playlistQualitiesLock. */
internal val playlistSubtitleTracks = mutableMapOf<String, List<SubtitleTrack>>()

/** The playlist with the videos already in the downloads list marked and unticked. */
internal fun DashboardViewModel.markPlaylist(playlist: MediaPlaylist): MediaPlaylist {
    val all = queueRepository.allTasksFlow.value
    val known = all.flatMap { listOfNotNull(it.url, it.originalUrl) }
    val marked = playlist.markDownloadedBefore(known)
    val joining = joiningPlaylist(_uiState.value) ?: return marked
    val inGroup = all.filter { it.playlistId == joining.id }
        .flatMap { listOfNotNull(it.url, it.originalUrl) }
        .map { MediaUrls.canonical(it).lowercase() }.toSet()
    return marked.copy(entries = marked.entries.map { e ->
        if (MediaUrls.canonical(e.url).lowercase() in inGroup) e.copy(downloadedBefore = true, isSelected = false, locked = true) else e
    })
}

/**
 * Reads each video's qualities in the background (two at a time, in the order shown) to show
 * its size for the chosen quality. Stops when the dialog shows something else.
 */
internal fun DashboardViewModel.startPlaylistSizes() {
    val start = _uiState.value
    val playlist = start.inspectedPlaylist
    val link = start.inputUrl
    if (playlistSizeJob?.isActive == true && playlistSizeLink == link && playlist != null) return
    playlistSizeJob?.cancel()
    if (playlist == null || playlist.fromBrowser) return
    playlistSizeLink = link
    val order = playlist.entries.filter { !it.unavailable }.map { it.index to it.url }
    _uiState.update { it.copy(inspectedSubtitles = emptyList()) }
    val readingKey = "playlist-sizes"
    val readingTitle = playlist.title
    playlistSizeJob = scope.launch {
        var next = 0
        var done = 0
        val pick = kotlinx.coroutines.sync.Mutex()
        com.michelle.raouf.fetch_jet.core.notification.BackgroundReading.begin(readingKey, readingTitle, readingText(0, order.size))
        try {
        val workers = (1..SIZE_READERS).map {
            launch {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val item = pick.withLock { order.getOrNull(next++) } ?: break
                    val state = _uiState.value
                    if (state.inspectedPlaylist == null || state.inputUrl != link || !state.isAddDialogVisible) return@launch
                    val key = MediaUrls.canonical(item.second)
                    val cached = playlistQualitiesLock.withLock { playlistQualities[key] }
                    var subtitles = playlistQualitiesLock.withLock { playlistSubtitleTracks[key] }.orEmpty()
                    suspend fun read(): List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = try {
                        queueRepository.inspectMediaQualities(item.second, headersForLink(item.second)).also {
                            subtitles = queueRepository.mediaEngine.subtitlesFoundFor(item.second)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val qualities = cached ?: read().ifEmpty {
                        currentCoroutineContext().ensureActive()
                        read()
                    }
                    pick.withLock { done++ }
                    com.michelle.raouf.fetch_jet.core.notification.BackgroundReading.progress(readingKey, readingText(done, order.size))
                    currentCoroutineContext().ensureActive()
                    if (cached == null && qualities.isNotEmpty()) playlistQualitiesLock.withLock {
                        playlistQualities[key] = qualities
                        playlistSubtitleTracks[key] = subtitles
                    }
                    setPlaylistSize(item.first, playlistSizeFor(_uiState.value.playlistQuality, qualities), unavailable = qualities.isEmpty())
                    if (subtitles.isNotEmpty()) _uiState.update { st ->
                        if (st.inputUrl != link) st else st.copy(inspectedSubtitles = mergeSubtitleLanguages(st.inspectedSubtitles, subtitles))
                    }
                }
            }
        }
        workers.forEach { it.join() }
        } finally {
            com.michelle.raouf.fetch_jet.core.notification.BackgroundReading.end(readingKey)
        }
    }
}

internal fun readingText(done: Int, total: Int): String =
    com.tbib.kmp_localization.LocalizationManager.Strings.sysReadingVideosCount
        .replace("%1", done.toString()).replace("%2", total.toString())

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

private fun DashboardViewModel.setPlaylistSize(index: Int, size: Long, unavailable: Boolean = false) {
    _uiState.update { st ->
        val pl = st.inspectedPlaylist ?: return@update st
        st.copy(inspectedPlaylist = pl.copy(entries = pl.entries.map {
            when {
                it.index != index -> it
                unavailable -> it.copy(size = size, unavailable = true, isSelected = false)
                else -> it.copy(size = size)
            }
        }))
    }
}

/** The tracks of one read video for the chosen languages, or null when that video was not read yet. */
internal suspend fun DashboardViewModel.playlistTracksFor(url: String, languages: List<String>): List<SubtitleTrack>? =
    playlistQualitiesLock.withLock { playlistSubtitleTracks[MediaUrls.canonical(url)] }
        ?.forLanguages(languages)

/**
 * The link list's subtitle languages: the union of the read rows' own tracks, keeping what the
 * user already ticked. Languages no row offers any more drop out.
 */
internal fun DashboardViewModel.refreshLinkListSubtitles() {
    _uiState.update { st ->
        val found = st.linkList.flatMap { it.subtitles }
        val codes = found.map { it.langCode }.toSet()
        st.copy(inspectedSubtitles = mergeSubtitleLanguages(st.inspectedSubtitles.filter { it.langCode in codes }, found))
    }
}
