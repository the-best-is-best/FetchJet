package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.media_extractor.isPlayerLibraryReady
import com.michelle.raouf.fetch_jet.feature.media_extractor.isPlayerLibraryOutdated
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogKind
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.michelle.raouf.fetch_jet.feature.media_extractor.ensurePlayerLibrary
import com.michelle.raouf.fetch_jet.feature.media_extractor.isPlayerLibraryAvailable
import com.michelle.raouf.fetch_jet.feature.media_player.HiddenPlayback
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.media_player.PlaybackQueue
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerMode
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerProgress
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerSubtitles
import com.michelle.raouf.fetch_jet.feature.media_player.SubtitleChoice
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiJson
import com.michelle.raouf.fetch_jet.nativeapp.dto.EffectKind
import com.michelle.raouf.fetch_jet.presentation.dashboard.folderTasks
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenGroupQueue
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenQueueFor
import com.michelle.raouf.fetch_jet.presentation.dashboard.playlistTasks
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCut
import com.michelle.raouf.fetch_jet.feature.video_cut.VideoCutMode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class PlayerItemJson(val id: String, val title: String, val path: String, val video: Boolean, val hidden: Boolean = false)

@Serializable
private data class SubtitleFileJson(val path: String, val label: String)

@Serializable
private data class SubtitlesJson(val files: List<SubtitleFileJson>, val choice: String)

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
    val trim: PlayerTrim? = null,
)

internal val playerAsk = MutableStateFlow<PlayerAsk?>(null)

internal val hiddenPlayer = MutableStateFlow(false)

private class Preparing(val id: String, val job: Job)

private val preparing = MutableStateFlow<Preparing?>(null)

internal fun Presenter.openPlayer(queue: PlaybackQueue, tasks: List<DownloadTask>) {
    when {
        isPlayerLibraryReady() -> pushPlayerEffect(queue, tasks)
        PlayerMode.isOn() || isPlayerLibraryOutdated() -> vm.ensurePlayerLibrary { pushPlayerEffect(queue, tasks) }
        else -> playerAsk.value = PlayerAsk(queue, tasks)
    }
}

internal fun Presenter.openTrimPlayer(task: DownloadTask, trim: PlayerTrim) {
    val tasks = listOf(task)
    val queue = MediaPlayback.queueFor(task, tasks)
    if (isPlayerLibraryReady()) {
        vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIMELINE))
        pushPlayerEffect(queue, tasks, trim)
    } else if (isPlayerLibraryOutdated()) {
        vm.ensurePlayerLibrary {
            vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIMELINE))
            pushPlayerEffect(queue, tasks, trim)
        }
    } else {
        playerAsk.value = PlayerAsk(queue, tasks, trim = trim)
    }
}

internal fun playerAskDialog(ask: PlayerAsk): ChoiceDialogDto {
    val s = LocalizationManager.Strings
    val name = ask.queue.current.title
    if (ask.trim != null) {
        return ChoiceDialogDto(
            kind = ChoiceDialogKind.PLAYER_LIBRARY.ordinal,
            icon = "🎞️",
            title = s.videoCutOnTimeline,
            name = name,
            nameRtl = startsRightToLeft(name),
            question = s.videoCutNeedsPlayerLibrary,
            primaryIcon = "⬇",
            primaryLabel = s.playerAskDownload,
            secondaryIcon = "⏱️",
            secondaryLabel = s.videoCutByTime,
            cancelLabel = s.cancel,
        )
    }
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
    )
}

internal fun Presenter.playerAskDownload() {
    val ask = playerAsk.value ?: return
    playerAsk.value = null
    if (ask.trim != null) {
        vm.ensurePlayerLibrary {
            vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIMELINE))
            pushPlayerEffect(ask.queue, ask.tasks, ask.trim)
        }
        return
    }
    PlayerMode.set(PlayerMode.ON)
    vm.ensurePlayerLibrary { pushPlayerEffect(ask.queue, ask.tasks) }
}

internal fun Presenter.playerAskSystem() {
    val ask = playerAsk.value ?: return
    playerAsk.value = null
    if (ask.trim != null) return vm.onEvent(DashboardUiEvent.OnVideoCutModeChosen(VideoCutMode.TIME))
    PlayerMode.set(PlayerMode.OFF)
    val task = ask.tasks.firstOrNull { it.id == ask.queue.current.taskId } ?: return
    if (ask.queue.current.hidden) return vm.onEvent(DashboardUiEvent.OnHiddenOpenFile(task.id))
    val path = taskFilePath(task)
    scope.launch(Dispatchers.IO) {
        if (!pathResolver.openFile(path)) pathResolver.openFileLocation(path)
    }
}

internal fun Presenter.playerLibraryDownload() {
    vm.ensurePlayerLibrary { settingsPoke() }
}

private fun Presenter.pushPlayerEffect(queue: PlaybackQueue, tasks: List<DownloadTask>, trim: PlayerTrim? = null) {
    val byId = tasks.associateBy { it.id }
    val items = queue.items.map {
        if (it.hidden) PlayerItemJson(it.taskId, it.title, "", it.isVideo, hidden = true)
        else PlayerItemJson(it.taskId, it.title, byId[it.taskId]?.let { task -> taskFilePath(task) } ?: it.path, it.isVideo)
    }
    if (items.any { it.hidden }) hiddenPlayer.value = true
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
    if (task.hidden) return playHiddenTask(task.id)
    if (!MediaPlayback.canPlay(task)) return false
    if (PlayerMode.isOff() || !isPlayerLibraryAvailable()) return false
    if (!pathResolver.fileExists(taskFilePath(task))) return false
    val all = vm.uiState.value.allTasks
    openPlayer(MediaPlayback.queueFor(task, all), all)
    return true
}

internal fun Presenter.playHiddenTask(taskId: String): Boolean {
    if (PlayerMode.isOff() || !isPlayerLibraryAvailable()) return false
    val queue = vm.hiddenQueueFor(taskId) ?: return false
    openPlayer(queue, vm.uiState.value.hidden.tasks)
    return true
}

internal fun Presenter.playOpenGroup() {
    if (headless || PlayerMode.isOff() || !isPlayerLibraryAvailable()) return
    val state = vm.uiState.value
    if (local.value.hiddenOpen) {
        val queue = vm.hiddenGroupQueue() ?: return
        return openPlayer(queue, state.hidden.tasks)
    }
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

internal fun Presenter.debugPlayHidden(taskId: String): String {
    val queue = (if (taskId.isEmpty()) vm.hiddenGroupQueue() else vm.hiddenQueueFor(taskId)) ?: return ""
    pushPlayerEffect(queue, vm.uiState.value.hidden.tasks)
    return "ok"
}

internal fun Presenter.playerPrepare(id: String) {
    if (id.isEmpty()) return
    hiddenPlayer.value = true
    val old = preparing.value
    if (old != null && old.id != id) old.job.cancel()
    var last = -1
    val job = scope.launch(Dispatchers.IO) {
        old?.job?.join()
        val path = try {
            HiddenPlayback.prepare(id) { fraction ->
                val percent = (fraction.coerceIn(0f, 1f) * 100f).toInt()
                if (percent != last) {
                    last = percent
                    effect(EffectKind.PLAYER_PREPARING, title = id, number = percent)
                }
            }
        } catch (e: CancellationException) {
            HiddenPlayback.release(id)
            throw e
        } catch (_: Throwable) {
            null
        }
        if (!isActive) {
            HiddenPlayback.release(id)
            return@launch
        }
        withContext(Dispatchers.Main) {
            val open = hiddenPlayer.value && vm.uiState.value.hidden.unlocked
            if (preparing.value?.id == id) preparing.value = null
            if (!open && path != null) scope.launch(Dispatchers.IO) { HiddenPlayback.release(id) }
            effect(EffectKind.PLAYER_PATH, text = if (open) path.orEmpty() else "", title = id)
        }
    }
    preparing.value = Preparing(id, job)
}

internal fun Presenter.playerRelease(id: String) {
    if (id.isEmpty()) return
    val running = preparing.value?.takeIf { it.id == id }
    if (running != null) preparing.value = null
    scope.launch(Dispatchers.IO) {
        running?.job?.cancelAndJoin()
        HiddenPlayback.release(id)
    }
}

internal fun Presenter.playerClosed() {
    hiddenPlayer.value = false
    preparing.value?.job?.cancel()
    preparing.value = null
    vm.onEvent(DashboardUiEvent.OnHiddenClosePlayer)
}

internal fun Presenter.closeHiddenPlayer() {
    if (!hiddenPlayer.value) return
    hiddenPlayer.value = false
    preparing.value?.job?.cancel()
    preparing.value = null
    effect(EffectKind.CLOSE_PLAYER)
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
            val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
            if (obj["hidden"]?.jsonPrimitive?.content == "true") {
                val presenter = com.michelle.raouf.fetch_jet.nativeapp.NativeApp.presenter
                hiddenPlayer.value = false
                if (id.isNotEmpty()) presenter.vm.onEvent(DashboardUiEvent.OnHiddenOpenFile(id))
            } else if (path.isNotEmpty()) com.michelle.raouf.fetch_jet.core.filesystem.PathResolver().openFile(path)
        }
        "remember" -> PlayerProgress.remember(key, obj["id"]?.jsonPrimitive?.content.orEmpty())
        "clear" -> PlayerProgress.clear(key)
        "subtitles" -> return playerSubtitles(obj["id"]?.jsonPrimitive?.content.orEmpty(), obj["path"]?.jsonPrimitive?.content.orEmpty(), obj["hidden"]?.jsonPrimitive?.content == "true")
        "subtitle" -> {
            val id = obj["id"]?.jsonPrimitive?.content.orEmpty()
            val choice = SubtitleChoice.decode(obj["choice"]?.jsonPrimitive?.content.orEmpty()) ?: return ""
            if (obj["hidden"]?.jsonPrimitive?.content == "true" || isHiddenTask(id)) return ""
            PlayerSubtitles.remember(id, choice)
        }
        "waveform" -> {
            val path = obj["path"]?.jsonPrimitive?.content.orEmpty()
            val bars = obj["bars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val durationMs = obj["durationMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            if (path.isEmpty() || bars <= 0) return ""
            val peaks = runBlocking { VideoCut.waveform(path, bars.coerceAtMost(4000), durationMs) }
            return UiJson.encodeToString(peaks)
        }
        else -> return ""
    }
    return "ok"
}

private fun isHiddenTask(id: String): Boolean {
    if (id.isEmpty()) return true
    val state = com.michelle.raouf.fetch_jet.nativeapp.NativeApp.presenter.vm.uiState.value
    return state.hidden.tasks.any { it.id == id }
}

private fun playerSubtitles(id: String, path: String, hidden: Boolean): String {
    val language = PlayerSubtitles.appLanguage()
    val secret = hidden || isHiddenTask(id)
    val files = if (secret) emptyList() else PlayerSubtitles.sideFiles(path, language)
    val choice = PlayerSubtitles.initialChoice(id, files, language, remember = !secret)
    return UiJson.encodeToString(SubtitlesJson(files.map { SubtitleFileJson(it.path, it.label) }, choice?.encode().orEmpty()))
}
