package com.michelle.raouf.fetch_jet.feature.media_player

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType

data class PlayerItem(
    val taskId: String,
    val title: String,
    val path: String,
    val isVideo: Boolean
)

data class PlaybackQueue(val items: List<PlayerItem>, val index: Int, val groupKey: String = "") {
    val current: PlayerItem get() = items[index]
    val hasNext: Boolean get() = index < items.lastIndex
    val hasPrevious: Boolean get() = index > 0
    val position: String get() = "${index + 1}/${items.size}"

    fun at(newIndex: Int): PlaybackQueue = copy(index = newIndex.coerceIn(0, items.lastIndex))
    fun next(): PlaybackQueue = if (hasNext) copy(index = index + 1) else this
    fun previous(): PlaybackQueue = if (hasPrevious) copy(index = index - 1) else this
}

object MediaPlayback {
    private val VIDEO = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts", "flv")
    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "weba", "mka")

    val SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    const val HOLD_SPEED = 2f
    const val SKIP_MS = 10_000L

    fun isVideoName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO
    fun isAudioName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in AUDIO

    fun canPlay(task: DownloadTask): Boolean =
        task.status == DownloadStatus.COMPLETED &&
                !task.hidden &&
                task.engineType != EngineType.BITTORRENT &&
                (isVideoName(task.fileName) || isAudioName(task.fileName))

    fun pathOf(task: DownloadTask, decode: (String) -> String = { it }): String {
        val dest = task.destinationPath.trimEnd('/', '\\')
        val sep = if (dest.contains('\\')) "\\" else "/"
        return decode("$dest$sep${task.fileName}").trim()
    }

    /**
     * The queue for [start]: the finished audio / video files of its playlist (or, with no playlist,
     * of its folder), in the order the list shows them. A file with no neighbours plays alone.
     */
    fun queueFor(
        start: DownloadTask,
        all: List<DownloadTask>,
        decode: (String) -> String = { it }
    ): PlaybackQueue {
        val group = when {
            start.playlistId.isNotEmpty() -> all.filter { it.playlistId == start.playlistId }
            else -> listOf(start)
        }
        val playable = group.filter { canPlay(it) }.ifEmpty { listOf(start) }
        val items = playable.map {
            PlayerItem(it.id, titleOf(it), pathOf(it, decode), isVideoName(it.fileName))
        }
        val index = playable.indexOfFirst { it.id == start.id }.coerceAtLeast(0)
        return PlaybackQueue(items, index, start.playlistId)
    }

    fun hasPlayable(tasks: List<DownloadTask>): Boolean = tasks.any { canPlay(it) }

    /**
     * The queue for a whole group: its finished audio / video files in list order, starting at the
     * one last played in it ([PlayerProgress]) or, with none remembered, at the first. Null when
     * nothing in the group can be played.
     */
    fun queueForGroup(
        groupKey: String,
        tasks: List<DownloadTask>,
        decode: (String) -> String = { it }
    ): PlaybackQueue? {
        val playable = tasks.filter { canPlay(it) }
        if (playable.isEmpty()) return null
        val items = playable.map { PlayerItem(it.id, titleOf(it), pathOf(it, decode), isVideoName(it.fileName)) }
        val last = PlayerProgress.lastPlayed(groupKey)
        val index = playable.indexOfFirst { it.id == last }.coerceAtLeast(0)
        return PlaybackQueue(items, index, groupKey)
    }

    fun titleOf(task: DownloadTask): String = task.fileName.substringBeforeLast('.')

    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }

    fun speedLabel(speed: Float): String {
        val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
        return "${text}x"
    }
}
