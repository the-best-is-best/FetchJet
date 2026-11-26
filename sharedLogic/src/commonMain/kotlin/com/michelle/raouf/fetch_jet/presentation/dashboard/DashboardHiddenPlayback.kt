package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.feature.media_player.HiddenPlayback
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerMode
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerRequests

internal fun DashboardViewModel.attachHiddenPlayback() {
    HiddenPlayback.attach(queueRepository.pathResolver) { id ->
        val hidden = _uiState.value.hidden
        if (!hidden.unlocked || hidden.covered) null else hidden.tasks.firstOrNull { it.id == id && it.hidden }
    }
}

fun DashboardViewModel.hiddenQueueFor(taskId: String): PlaybackQueue? {
    val hidden = uiState.value.hidden
    if (!hidden.unlocked || PlayerMode.isOff()) return null
    val task = hidden.tasks.firstOrNull { it.id == taskId } ?: return null
    if (!MediaPlayback.canPlay(task, allowHidden = true)) return null
    return MediaPlayback.queueFor(task, hidden.tasks, allowHidden = true)
}

fun DashboardViewModel.hiddenGroupQueue(): PlaybackQueue? {
    val state = uiState.value
    if (!state.hidden.unlocked || PlayerMode.isOff()) return null
    val playlist = state.hiddenOpenPlaylist()
    val (key, tasks) = when {
        playlist != null -> playlist.record.id to playlist.tasks
        else -> state.hiddenOpenFolder()?.let { FOLDER_ROW_PREFIX + it.folder to it.tasks } ?: return null
    }
    return MediaPlayback.queueForGroup(key, tasks, allowHidden = true)
}

fun DashboardUiState.hiddenGroupCanPlay(): Boolean =
    hidden.unlocked && !PlayerMode.isOff() &&
            hiddenPageTasks()?.let { MediaPlayback.hasPlayable(it, allowHidden = true) } == true

internal fun DashboardViewModel.playHidden(taskId: String) {
    val queue = hiddenQueueFor(taskId)
    if (queue == null) {
        onEvent(DashboardUiEvent.OnHiddenOpenFile(taskId))
        return
    }
    PlayerRequests.open(queue)
}

internal fun DashboardViewModel.playHiddenGroup() {
    hiddenGroupQueue()?.let { PlayerRequests.open(it) }
}

internal fun closeHiddenPlayer() {
    HiddenPlayback.releaseAll()
    if (PlayerRequests.queue.value?.items?.any { it.hidden } == true) PlayerRequests.close()
}
