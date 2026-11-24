package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.media_extractor.ensurePlayerLibrary
import com.michelle.raouf.fetch_jet.feature.media_extractor.isPlayerLibraryAvailable
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerProgress
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiJson
import com.michelle.raouf.fetch_jet.nativeapp.dto.EffectKind
import com.michelle.raouf.fetch_jet.presentation.dashboard.folderTasks
import com.michelle.raouf.fetch_jet.presentation.dashboard.playlistTasks
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class PlayerItemJson(val id: String, val title: String, val path: String, val video: Boolean)

@Serializable
private data class PlayerQueueJson(val items: List<PlayerItemJson>, val index: Int, val groupKey: String)

internal fun Presenter.openPlayer(queue: PlaybackQueue, tasks: List<DownloadTask>) {
    vm.ensurePlayerLibrary { pushPlayerEffect(queue, tasks) }
}

private fun Presenter.pushPlayerEffect(queue: PlaybackQueue, tasks: List<DownloadTask>) {
    val byId = tasks.associateBy { it.id }
    val json = PlayerQueueJson(
        items = queue.items.map {
            PlayerItemJson(it.taskId, it.title, byId[it.taskId]?.let { task -> taskFilePath(task) } ?: it.path, it.isVideo)
        },
        index = queue.index,
        groupKey = queue.groupKey
    )
    effect(EffectKind.OPEN_PLAYER, text = UiJson.encodeToString(json))
}

internal fun Presenter.playTask(task: DownloadTask): Boolean {
    if (!MediaPlayback.canPlay(task)) return false
    if (!isPlayerLibraryAvailable()) return false
    if (!pathResolver.fileExists(taskFilePath(task))) return false
    val all = vm.uiState.value.allTasks
    openPlayer(MediaPlayback.queueFor(task, all), all)
    return true
}

internal fun Presenter.playOpenGroup() {
    if (headless || !isPlayerLibraryAvailable()) return
    val state = vm.uiState.value
    val folder = state.openFolder
    val playlist = state.openPlaylistId
    val (key, tasks) = when {
        playlist != null -> playlist to playlistTasks(state.allTasks, playlist)
        folder != null -> "dir:$folder" to folderTasks(state.allTasks, state.playlists, folder)
        else -> return
    }
    val queue = MediaPlayback.queueForGroup(key, tasks) ?: return
    openPlayer(queue, tasks)
}

internal fun playerCall(name: String, args: String): String {
    val obj = try {
        UiJson.parseToJsonElement(args).jsonObject
    } catch (_: Throwable) {
        return ""
    }
    val key = obj["key"]?.jsonPrimitive?.content.orEmpty()
    when (name) {
        "system" -> {
            val path = obj["path"]?.jsonPrimitive?.content.orEmpty()
            if (path.isNotEmpty()) com.michelle.raouf.fetch_jet.core.filesystem.PathResolver().openFile(path)
        }
        "remember" -> PlayerProgress.remember(key, obj["id"]?.jsonPrimitive?.content.orEmpty())
        "clear" -> PlayerProgress.clear(key)
        else -> return ""
    }
    return "ok"
}
