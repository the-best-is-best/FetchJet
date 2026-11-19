package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaUrls
import com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality
import com.michelle.raouf.fetch_jet.feature.media_extractor.canAddSubtitles
import com.michelle.raouf.fetch_jet.feature.media_extractor.subtitleKeysPresent
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistStore
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LANGUAGE_READS = 3

internal fun DashboardViewModel.handlePlaylistGroupEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnOpenPlaylist -> _uiState.update {
            if (event.playlistId in it.playlists) it.copy(openPlaylistId = event.playlistId, openFolder = null, playlistLanguages = null) else it
        }

        DashboardUiEvent.OnClosePlaylist -> _uiState.update {
            if (it.playlistLanguages?.adding == true) it.copy(openPlaylistId = null)
            else it.copy(openPlaylistId = null, playlistLanguages = null)
        }

        is DashboardUiEvent.OnPlaylistGroupPause -> scope.launch {
            groupTasks(event.playlistId).filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING || it.status == DownloadStatus.QUEUED
            }.forEach { queueRepository.pauseTask(it.id) }
        }

        is DashboardUiEvent.OnPlaylistGroupResume -> scope.launch {
            groupTasks(event.playlistId).filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR }
                .forEach { queueRepository.resumeTask(it.id) }
        }

        is DashboardUiEvent.OnPlaylistGroupRemove -> {
            val ids = groupTasks(event.playlistId).map { it.id }
            if (ids.isNotEmpty()) onEvent(DashboardUiEvent.OnRemoveSelectedClicked(ids))
        }

        is DashboardUiEvent.OnPlaylistCheck -> checkPlaylist(event.playlistId)
        is DashboardUiEvent.OnPlaylistReadLanguages -> readPlaylistLanguages(event.playlistId)
        is DashboardUiEvent.OnPlaylistAddLanguage -> addPlaylistLanguage(event.playlistId, event.langCode)
        DashboardUiEvent.OnPlaylistLanguagesDismiss -> _uiState.update {
            if (it.playlistLanguages?.adding == true) it else it.copy(playlistLanguages = null)
        }

        is DashboardUiEvent.OnOpenFolderGroup -> _uiState.update {
            if (folderTasks(it.allTasks, it.playlists, event.folder).isEmpty()) it
            else it.copy(openFolder = event.folder, openPlaylistId = null, playlistLanguages = null)
        }

        DashboardUiEvent.OnCloseFolderGroup -> _uiState.update { it.copy(openFolder = null) }

        is DashboardUiEvent.OnFolderGroupPause -> scope.launch {
            folderGroupTasks(event.folder).filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING || it.status == DownloadStatus.QUEUED
            }.forEach { queueRepository.pauseTask(it.id) }
        }

        is DashboardUiEvent.OnFolderGroupResume -> scope.launch {
            folderGroupTasks(event.folder).filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR }
                .forEach { queueRepository.resumeTask(it.id) }
        }

        is DashboardUiEvent.OnFolderGroupRemove -> {
            val ids = folderGroupTasks(event.folder).map { it.id }
            if (ids.isNotEmpty()) onEvent(DashboardUiEvent.OnRemoveSelectedClicked(ids))
        }

        else -> Unit
    }
}

/**
 * A download just started: show the group it went into (its playlist, or its folder when
 * downloads are grouped by folder). [askedList] is the playlist the caller put it in, if any;
 * a video that joined a playlist by its folder says so.
 */
internal suspend fun DashboardViewModel.showGroupOf(task: DownloadTask, askedList: String = "") {
    val id = task.playlistId
    if (id.isNotEmpty() && PlaylistStore.get(id) != null) {
        _uiState.update { it.copy(openPlaylistId = id, openFolder = null, playlistLanguages = null) }
        if (askedList.isEmpty()) _uiEffect.emit(
            DashboardUiEffect.ShowToast(LocalizationManager.Strings.playlistVideoJoined.replace("%1", task.playlistTitle))
        )
        return
    }
    if (_uiState.value.groupByFolder) {
        _uiState.update { it.copy(openFolder = folderKey(task.destinationPath), openPlaylistId = null, playlistLanguages = null) }
    }
}

internal fun DashboardViewModel.folderGroupTasks(folder: String): List<DownloadTask> =
    folderTasks(queueRepository.allTasksFlow.value, _uiState.value.playlists, folder)

internal fun DashboardViewModel.groupTasks(playlistId: String): List<DownloadTask> =
    queueRepository.allTasksFlow.value.filter { it.playlistId == playlistId }

/** The saved playlist new videos join: only while "Check" of it is open on its own link. */
internal fun DashboardViewModel.joiningPlaylist(state: DashboardUiState): PlaylistRecord? {
    val id = state.checkingPlaylistId ?: return null
    val record = PlaylistStore.get(id) ?: return null
    val link = state.inputUrl.trim()
    return record.takeIf { it.url.isNotBlank() && it.url.trim() == link }
}

/** Downloads saved on their own in a saved playlist's folder (named after it) become its videos. */
private fun DashboardViewModel.adoptLoneVideos(tasks: List<DownloadTask>) {
    if (PlaylistStore.records.value.isEmpty()) return
    for (task in tasks) {
        if (task.playlistId.isNotEmpty() || task.engineType == com.michelle.raouf.fetch_jet.domain.model.EngineType.BITTORRENT) continue
        val list = PlaylistStore.inFolder(task.destinationPath) ?: continue
        queueRepository.setPlaylist(task.id, list.id, list.title)
    }
}

/** Keeps the state's playlists in step with the store, and forgets lists left without videos. */
internal fun DashboardViewModel.watchPlaylists() {
    PlaylistStore.load()
    scope.launch {
        PlaylistStore.records.collect { records ->
            _uiState.update { it.copy(playlists = records) }
            adoptLoneVideos(queueRepository.allTasksFlow.value)
        }
    }
    scope.launch {
        val seen = HashSet<String>()
        queueRepository.allTasksFlow.collect { tasks ->
            _uiState.value.openFolder?.let { folder ->
                if (folderTasks(tasks, _uiState.value.playlists, folder).isEmpty()) _uiState.update { it.copy(openFolder = null) }
            }
            adoptLoneVideos(tasks)
            val present = tasks.mapNotNullTo(HashSet()) { it.playlistId.takeIf { id -> id.isNotEmpty() } }
            val gone = seen.filter { it !in present }
            seen.addAll(present)
            if (gone.isNotEmpty()) {
                seen.removeAll(gone.toSet())
                PlaylistStore.remove(gone)
                _uiState.update { if (it.openPlaylistId in gone) it.copy(openPlaylistId = null, playlistLanguages = null) else it }
            }
        }
    }
}

private fun DashboardViewModel.checkPlaylist(playlistId: String) {
    val record = PlaylistStore.get(playlistId) ?: return
    if (record.url.isBlank()) return
    onEvent(DashboardUiEvent.OnToggleAddDialog(false))
    _uiState.update {
        it.copy(
            checkingPlaylistId = playlistId,
            playlistQuality = PlaylistQuality.entries.firstOrNull { q -> q.name == record.quality } ?: it.playlistQuality,
            audioConvertFormat = record.audioFormat.ifBlank { null },
        )
    }
    onEvent(DashboardUiEvent.OnUrlChanged(record.url))
    _uiState.update { it.copy(checkingPlaylistId = playlistId) }
    onEvent(DashboardUiEvent.OnToggleAddDialog(true))
}

/** The languages its videos offer: the tracks of the first few videos, read one at a time. */
private fun DashboardViewModel.readPlaylistLanguages(playlistId: String) {
    if (_uiState.value.playlistLanguages?.let { it.playlistId == playlistId && (it.reading || it.adding) } == true) return
    _uiState.update { it.copy(playlistLanguages = PlaylistLanguagesUi(playlistId, reading = true)) }
    scope.launch {
        val found = ArrayList<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack>()
        for (task in groupTasks(playlistId).sortedByDescending { it.status == DownloadStatus.COMPLETED }.take(LANGUAGE_READS)) {
            val tracks = try {
                queueRepository.inspectSubtitlesOf(task)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            for (t in tracks.orEmpty()) if (found.none { it.langCode == t.langCode }) found.add(t.copy(isSelected = false))
            if (found.isNotEmpty()) break
        }
        val saved = PlaylistStore.get(playlistId)?.subtitleLanguages.orEmpty()
        _uiState.update { st ->
            val page = st.playlistLanguages?.takeIf { it.playlistId == playlistId } ?: return@update st
            st.copy(
                playlistLanguages = page.copy(
                    reading = false,
                    available = found.filter { it.langCode !in saved },
                    message = if (found.isEmpty()) LocalizationManager.Strings.subtitlesNoneInList else ""
                )
            )
        }
    }
}

/**
 * Adds a subtitle language to a saved playlist: saved for its next videos, fetched now for its
 * finished ones (each with its own tracks), and left on the unfinished ones for download time.
 */
private fun DashboardViewModel.addPlaylistLanguage(playlistId: String, langCode: String) {
    val record = PlaylistStore.get(playlistId) ?: return
    if (_uiState.value.playlistLanguages?.adding == true) return
    PlaylistStore.put(record.copy(subtitleLanguages = (record.subtitleLanguages + langCode).distinct()))
    val tasks = groupTasks(playlistId)
    val finished = tasks.filter { it.status == DownloadStatus.COMPLETED && it.canAddSubtitles() }
    for (task in tasks) {
        if (task.status == DownloadStatus.COMPLETED) continue
        if (task.subtitleTracks.any { it.langCode == langCode }) continue
        val wanted = (task.subtitleLanguages.split(',').map { it.trim() }.filter { it.isNotEmpty() } + langCode).distinct()
        queueRepository.setSubtitleLanguages(task.id, wanted.joinToString(","))
    }
    _uiState.update { st ->
        val page = st.playlistLanguages ?: PlaylistLanguagesUi(playlistId)
        st.copy(
            playlistLanguages = page.copy(
                playlistId = playlistId,
                adding = finished.isNotEmpty(),
                done = 0,
                total = finished.size,
                available = page.available.filter { it.langCode != langCode },
                message = ""
            )
        )
    }
    if (finished.isEmpty()) return
    scope.launch {
        var added = 0
        for ((i, task) in finished.withIndex()) {
            try {
                val present = task.subtitleKeysPresent()
                val tracks = queueRepository.inspectSubtitlesOf(task).orEmpty()
                    .filter { it.langCode == langCode && subtitleKey(it) !in present }
                    .take(1)
                if (tracks.isNotEmpty()) {
                    val result = queueRepository.addSubtitles(task.id, tracks) { }
                    if (result != null && result.added.isNotEmpty()) added++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            _uiState.update { st ->
                val page = st.playlistLanguages?.takeIf { it.playlistId == playlistId } ?: return@update st
                st.copy(playlistLanguages = page.copy(done = i + 1))
            }
        }
        _uiState.update { st ->
            val page = st.playlistLanguages?.takeIf { it.playlistId == playlistId } ?: return@update st
            st.copy(
                playlistLanguages = page.copy(
                    adding = false,
                    message = LocalizationManager.Strings.playlistLanguageAdded
                        .replace("%1", added.toString()).replace("%2", finished.size.toString())
                )
            )
        }
    }
}

/** Subtitle languages of a saved playlist, as names when its videos told them, else codes. */
fun playlistLanguagesText(record: PlaylistRecord): String =
    record.subtitleLanguages.joinToString(", ")
