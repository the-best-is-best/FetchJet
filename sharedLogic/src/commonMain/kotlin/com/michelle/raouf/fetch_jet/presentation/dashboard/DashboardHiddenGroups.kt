package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.queue_manager.unhideTasks
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun DashboardUiState.hiddenItems(): List<TaskListItem> =
    groupDownloads(hidden.tasks, hidden.tasks, playlists, groupByFolder)

fun DashboardUiState.hiddenOpenPlaylist(): PlaylistGroup? {
    val id = hidden.openPlaylistId ?: return null
    val record = playlists[id] ?: return null
    val tasks = playlistTasks(hidden.tasks, id)
    return if (tasks.isEmpty()) null else PlaylistGroup(record, tasks)
}

fun DashboardUiState.hiddenOpenFolder(): FolderGroup? {
    if (hiddenOpenPlaylist() != null) return null
    val folder = hidden.openFolder ?: return null
    return folderGroup(hidden.tasks, playlists, folder)
}

fun DashboardUiState.hiddenPageTasks(): List<DownloadTask>? =
    hiddenOpenPlaylist()?.tasks ?: hiddenOpenFolder()?.tasks

fun DashboardUiState.hiddenTaskIdsOfRows(keys: Collection<String>): List<String> =
    keys.flatMap { key ->
        playlistIdOfRow(key)?.let { id -> playlistTasks(hidden.tasks, id).map { it.id } }
            ?: folderOfRow(key)?.let { folder -> folderTasks(hidden.tasks, playlists, folder).map { it.id } }
            ?: listOf(key)
    }.distinct()

internal fun DashboardUiState.openHiddenPlaylist(id: String): DashboardUiState =
    if (hidden.unlocked && id in playlists && hidden.tasks.any { it.playlistId == id })
        copy(hidden = hidden.copy(openPlaylistId = id, openFolder = null))
    else this

internal fun DashboardUiState.openHiddenFolder(folder: String): DashboardUiState =
    if (hidden.unlocked && folderTasks(hidden.tasks, playlists, folder).isNotEmpty())
        copy(hidden = hidden.copy(openFolder = folder, openPlaylistId = null))
    else this

internal fun DashboardUiState.withoutEmptyHiddenGroup(): DashboardUiState {
    val playlistGone = hidden.openPlaylistId?.let { id -> hidden.tasks.none { it.playlistId == id } } == true
    val folderGone = hidden.openFolder?.let { folderTasks(hidden.tasks, playlists, it).isEmpty() } == true
    if (!playlistGone && !folderGone) return this
    return copy(
        hidden = hidden.copy(
            openPlaylistId = if (playlistGone) null else hidden.openPlaylistId,
            openFolder = if (folderGone) null else hidden.openFolder
        )
    )
}

internal fun DashboardUiState.closeHiddenGroup(): DashboardUiState =
    copy(hidden = hidden.copy(openPlaylistId = null, openFolder = null))

internal fun DashboardUiState.hiddenGroupTasks(playlistId: String, folder: String): List<DownloadTask> = when {
    playlistId.isNotEmpty() -> playlistTasks(hidden.tasks, playlistId)
    folder.isNotEmpty() -> folderTasks(hidden.tasks, playlists, folder)
    else -> emptyList()
}

internal fun DashboardViewModel.handleHiddenGroupEvent(event: DashboardUiEvent) {
    val state = _uiState.value
    if (!state.hidden.unlocked) return
    when (event) {
        is DashboardUiEvent.OnHiddenOpenPlaylist -> _uiState.update { it.openHiddenPlaylist(event.playlistId) }
        is DashboardUiEvent.OnHiddenOpenFolder -> _uiState.update { it.openHiddenFolder(event.folder) }
        DashboardUiEvent.OnHiddenCloseGroup -> _uiState.update { it.closeHiddenGroup() }

        is DashboardUiEvent.OnUnhideGroup -> {
            val ids = state.hiddenGroupTasks(event.playlistId, event.folder).map { it.id }
            if (ids.isEmpty()) return
            if (unhideNeedsFileAccess(ids)) return
            scope.launch { reportHiddenMove(queueRepository.unhideTasks(ids)) }
        }

        is DashboardUiEvent.OnHiddenGroupPause -> {
            val ids = state.hiddenGroupTasks(event.playlistId, event.folder).filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING || it.status == DownloadStatus.QUEUED
            }.map { it.id }
            scope.launch { ids.forEach { queueRepository.pauseTask(it) } }
        }

        is DashboardUiEvent.OnHiddenGroupResume -> {
            val ids = state.hiddenGroupTasks(event.playlistId, event.folder).filter {
                it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR
            }.map { it.id }
            scope.launch { ids.forEach { queueRepository.resumeTask(it) } }
        }

        else -> Unit
    }
}
