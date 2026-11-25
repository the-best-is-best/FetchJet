package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.media_extractor.isPlayerLibraryReady
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogKind
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.michelle.raouf.fetch_jet.feature.media_extractor.ensurePlayerLibrary
import com.michelle.raouf.fetch_jet.feature.media_extractor.isPlayerLibraryAvailable
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerProgress
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiJson
import com.michelle.raouf.fetch_jet.nativeapp.dto.EffectKind
import com.michelle.raouf.fetch_jet.presentation.dashboard.folderTasks
import com.michelle.raouf.fetch_jet.presentation.dashboard.playlistTasks
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCutMode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class PlayerItemJson(val id: String, val title: String, val path: String, val video: Boolean)

@Serializable
private data class PlayerQueueJson(val items: List<PlayerItemJson>, val index: Int, val groupKey: String)

@Serializable
private data class PlayerTrimJson(
    val items: List<PlayerItemJson>,
    val index: Int,
    val groupKey: String,
    val trim: Boolean,
    val cutTaskId: String,
    val startMs: Long,
    val endMs: Long,
)

internal data class PlayerTrim(val taskId: String, val startMs: Long, val endMs: Long)

internal data class PlayerAsk(
    val queue: PlaybackQueue,
    val tasks: List<DownloadTask>,
    val remember: Boolean = false,
    val trim: PlayerTrim? = null,
)

internal val playerAsk = MutableStateFlow<PlayerAsk?>(null)

private fun playerMode(): String = BehaviorSettings.desktopPlayer.value

internal fun Presenter.openPlayer(queue: PlaybackQueue, tasks: List<DownloadTask>) {
    when {
        isPlayerLibraryReady() -> pushPlayerEffect(queue, tasks)
        playerMode() == "on" -> vm.ensurePlayerLibrary { pushPlayerEffect(queue, tasks) }
        else -> playerAsk.value = PlayerAsk(queue, tasks)
    }
}

internal fun Presenter.openTrimPlayer(task: DownloadTask, trim: PlayerTrim) {
    val tasks = listOf(task)
    val queue = MediaPlayback.queueFor(task, tasks)
    when {
        isPlayerLibraryReady() -> pushPlayerEffect(queue, tasks, trim)
        playerMode() == "on" -> vm.ensurePlayerLibrary { pushPlayerEffect(queue, tasks, trim) }
        else -> playerAsk.value = PlayerAsk(queue, tasks, trim = trim)
    }
}

internal fun playerAskDialog(ask: PlayerAsk): ChoiceDialogDto {
    val s = LocalizationManager.Strings
    val name = ask.queue.current.title
    return ChoiceDialogDto(
        kind = ChoiceDialogKind.PLAYER_LIBRARY.ordinal,
        icon = "🎬",
        title = s.playerAskTitle,
        name = name,
        nameRtl = startsRightToLeft(name),
        question = s.playerAskText + "\n" + s.playerAskLater,
        primaryIcon = "⬇",
        primaryLabel = s.playerAskDownload,
        secondaryIcon = "↗",
        secondaryLabel = s.playerAskSystem,
        cancelLabel = s.cancel,
        checkLabel = s.playerAskRemember,
        checked = ask.remember,
    )
}

internal fun Presenter.playerAskDownload() {
    val ask = playerAsk.value ?: return
    playerAsk.value = null
    if (ask.remember) BehaviorSettings.setDesktopPlayer("on")
    vm.ensurePlayerLibrary { pushPlayerEffect(ask.queue, ask.tasks, ask.trim) }
}

internal fun Presenter.playerAskSystem() {
    val ask = playerAsk.value ?: return
    playerAsk.value = null
    if (ask.remember) BehaviorSettings.setDesktopPlayer("off")
    if (ask.trim != null) return vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIME))
    val task = ask.tasks.firstOrNull { it.id == ask.queue.current.taskId } ?: return
    val path = taskFilePath(task)
    scope.launch(Dispatchers.IO) {
        if (!pathResolver.openFile(path)) pathResolver.openFileLocation(path)
    }
}

private fun Presenter.pushPlayerEffect(queue: PlaybackQueue, tasks: List<DownloadTask>, trim: PlayerTrim? = null) {
    val byId = tasks.associateBy { it.id }
    val items = queue.items.map {
        PlayerItemJson(it.taskId, it.title, byId[it.taskId]?.let { task -> taskFilePath(task) } ?: it.path, it.isVideo)
    }
    if (trim != null) {
        val json = PlayerTrimJson(items, queue.index, queue.groupKey, true, trim.taskId, trim.startMs, trim.endMs)
        return effect(EffectKind.OPEN_PLAYER, text = UiJson.encodeToString(json))
    }
    val json = PlayerQueueJson(
        items = items,
        index = queue.index,
        groupKey = queue.groupKey
    )
    effect(EffectKind.OPEN_PLAYER, text = UiJson.encodeToString(json))
}

internal fun Presenter.playTask(task: DownloadTask): Boolean {
    if (!MediaPlayback.canPlay(task)) return false
    if (playerMode() == "off" || !isPlayerLibraryAvailable()) return false
    if (!pathResolver.fileExists(taskFilePath(task))) return false
    val all = vm.uiState.value.allTasks
    openPlayer(MediaPlayback.queueFor(task, all), all)
    return true
}

internal fun Presenter.playOpenGroup() {
    if (headless || playerMode() == "off" || !isPlayerLibraryAvailable()) return
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
