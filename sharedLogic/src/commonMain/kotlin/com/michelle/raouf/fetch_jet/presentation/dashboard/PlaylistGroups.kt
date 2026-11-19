package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

enum class PlaylistGroupStatus { DOWNLOADING, WAITING, PAUSED, DONE, SOME_FAILED }

/** What a group row shows, summed over its downloads (a playlist's videos or a folder's files). */
interface TaskGroupStats {
    val tasks: List<DownloadTask>
    val total: Int get() = tasks.size
    val done: Int get() = tasks.count { it.status == DownloadStatus.COMPLETED }
    val failed: Int get() = tasks.count { it.status == DownloadStatus.ERROR }
    val speed: Long get() = tasks.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.downloadSpeedBytesPerSec }
    val totalBytes: Long get() = tasks.sumOf { maxOf(it.totalBytes, 0L) }
    val downloadedBytes: Long get() = tasks.sumOf {
        if (it.status == DownloadStatus.COMPLETED) maxOf(it.totalBytes, it.downloadedBytes, 0L) else maxOf(it.downloadedBytes, 0L)
    }

    /** Each download counts the same, so a group of unknown sizes still moves. */
    val progress: Float
        get() = if (tasks.isEmpty()) 0f else tasks.sumOf {
            (if (it.status == DownloadStatus.COMPLETED) 1f else it.progress.coerceIn(0f, 1f).takeIf { p -> !p.isNaN() } ?: 0f).toDouble()
        }.toFloat() / tasks.size

    val status: PlaylistGroupStatus
        get() = when {
            tasks.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING } -> PlaylistGroupStatus.DOWNLOADING
            tasks.any { it.waitingInQueue || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.IDLE } -> PlaylistGroupStatus.WAITING
            tasks.any { it.status == DownloadStatus.PAUSED } -> PlaylistGroupStatus.PAUSED
            failed > 0 -> PlaylistGroupStatus.SOME_FAILED
            else -> PlaylistGroupStatus.DONE
        }

    val canPause: Boolean
        get() = tasks.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING || it.status == DownloadStatus.QUEUED }

    val canResume: Boolean
        get() = tasks.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR }

    /** "12 of 41 done · 2 failed" */
    val doneText: String
        get() {
            val s = LocalizationManager.Strings
            val base = s.playlistGroupDone.replace("%1", done.toString()).replace("%2", total.toString())
            return if (failed > 0) base + " · " + s.sysPlaylistDoneFailed.replace("%1", failed.toString()) else base
        }
}

/** One saved playlist in the downloads list: its videos summed up. */
data class PlaylistGroup(
    val record: PlaylistRecord,
    override val tasks: List<DownloadTask>,
) : TaskGroupStats {
    val id: String get() = record.id
    val title: String get() = record.title

    /** "12 of 41 done · 2 failed" */
    val summaryText: String get() = doneText
}

/**
 * One destination folder in the downloads list ("group by folder" on): every download saved
 * there that is not one of a saved playlist's videos. [folder] is the key ([folderKey] of the
 * path), [label] the name shown (the last folder name, or more of the path when two share it).
 */
data class FolderGroup(
    val folder: String,
    val path: String,
    val label: String,
    override val tasks: List<DownloadTask>,
) : TaskGroupStats {
    /** "5 files · 3 of 5 done" */
    val summaryText: String
        get() = LocalizationManager.Strings.folderGroupFiles.replace("%1", total.toString()) + " · " + doneText
}

/** A row of the downloads list: a single download, or a playlist shown as one item. */
sealed interface TaskListItem {
    val key: String

    data class Single(val task: DownloadTask) : TaskListItem {
        override val key: String get() = task.id
    }

    data class Group(val group: PlaylistGroup) : TaskListItem {
        override val key: String get() = PLAYLIST_ROW_PREFIX + group.id
    }

    data class Folder(val group: FolderGroup) : TaskListItem {
        override val key: String get() = FOLDER_ROW_PREFIX + group.folder
    }
}

const val PLAYLIST_ROW_PREFIX = "pl:"
const val FOLDER_ROW_PREFIX = "dir:"

fun playlistIdOfRow(key: String): String? = key.takeIf { it.startsWith(PLAYLIST_ROW_PREFIX) }?.removePrefix(PLAYLIST_ROW_PREFIX)

fun folderOfRow(key: String): String? = key.takeIf { it.startsWith(FOLDER_ROW_PREFIX) }?.removePrefix(FOLDER_ROW_PREFIX)

/** One spelling per folder: forward slashes, no trailing slash, case kept. */
fun folderKey(path: String): String {
    val unified = path.replace('\\', '/').trimEnd('/')
    return unified.ifEmpty { "/" }
}

/** A download belongs to a folder group when it is not one of a saved playlist's videos. */
fun inFolderGroup(task: DownloadTask, records: Map<String, PlaylistRecord>): Boolean =
    task.playlistId.isEmpty() || task.playlistId !in records

/** The downloads of one folder group, from all the tasks. */
fun folderTasks(all: List<DownloadTask>, records: Map<String, PlaylistRecord>, folder: String): List<DownloadTask> =
    all.filter { inFolderGroup(it, records) && folderKey(it.destinationPath) == folder }

/**
 * Names for folder keys: the last folder name, and when two folders share it, enough of the
 * path in front of it to tell them apart ("Work/Video", "Home/Video").
 */
fun folderLabels(folders: Collection<String>): Map<String, String> {
    val parts = folders.associateWith { key -> key.split('/').filter { it.isNotEmpty() } }
    val out = HashMap<String, String>()
    for (key in folders) {
        val mine = parts.getValue(key)
        if (mine.isEmpty()) {
            out[key] = key
            continue
        }
        var depth = 1
        while (depth < mine.size) {
            val tail = mine.takeLast(depth)
            val clash = parts.any { (other, p) -> other != key && p.size >= depth && p.takeLast(depth) == tail }
            if (!clash) break
            depth++
        }
        out[key] = mine.takeLast(depth).joinToString("/")
    }
    return out
}

/** The folder group for [folder] built from all the tasks (null when it has none left). */
fun folderGroup(all: List<DownloadTask>, records: Map<String, PlaylistRecord>, folder: String): FolderGroup? {
    val tasks = folderTasks(all, records, folder)
    if (tasks.isEmpty()) return null
    val labels = folderLabels(all.filter { inFolderGroup(it, records) }.mapTo(HashSet()) { folderKey(it.destinationPath) })
    return FolderGroup(folder, tasks.first().destinationPath, labels[folder] ?: folder, tasks)
}

/** The videos of a saved playlist, from all the tasks. */
fun playlistTasks(all: List<DownloadTask>, playlistId: String): List<DownloadTask> =
    all.filter { it.playlistId == playlistId }

/**
 * The downloads list with each saved playlist folded into one row, placed where its first
 * shown video is. [shown] is the filtered / searched list, [all] every task (a group sums all
 * its videos, not only the shown ones). Tasks of a playlist without a record stay single rows.
 */
fun groupPlaylists(
    shown: List<DownloadTask>,
    all: List<DownloadTask>,
    records: Map<String, PlaylistRecord>,
): List<TaskListItem> {
    if (records.isEmpty()) return shown.map { TaskListItem.Single(it) }
    val members = all.filter { it.playlistId.isNotEmpty() && it.playlistId in records }.groupBy { it.playlistId }
    val placed = HashSet<String>()
    val out = ArrayList<TaskListItem>(shown.size)
    for (task in shown) {
        val id = task.playlistId
        val record = records[id]
        if (id.isEmpty() || record == null) {
            out.add(TaskListItem.Single(task))
        } else if (placed.add(id)) {
            out.add(TaskListItem.Group(PlaylistGroup(record, members[id].orEmpty())))
        }
    }
    return out
}

/**
 * The downloads list as rows: saved playlists folded into one row each and, when [byFolder] is
 * on, every other download folded into its destination folder's row. A group sits where its
 * first shown download is; it sums all its downloads ([all]), not only the shown ones.
 */
fun groupDownloads(
    shown: List<DownloadTask>,
    all: List<DownloadTask>,
    records: Map<String, PlaylistRecord>,
    byFolder: Boolean,
): List<TaskListItem> {
    val items = groupPlaylists(shown, all, records)
    if (!byFolder) return items
    val members = all.filter { inFolderGroup(it, records) }.groupBy { folderKey(it.destinationPath) }
    val labels = folderLabels(members.keys)
    val placed = HashSet<String>()
    val out = ArrayList<TaskListItem>(items.size)
    for (item in items) {
        if (item !is TaskListItem.Single || !inFolderGroup(item.task, records)) {
            out.add(item)
            continue
        }
        val key = folderKey(item.task.destinationPath)
        if (!placed.add(key)) continue
        val tasks = members[key].orEmpty().ifEmpty { listOf(item.task) }
        out.add(TaskListItem.Folder(FolderGroup(key, tasks.first().destinationPath, labels[key] ?: key, tasks)))
    }
    return out
}

/** Search: a playlist matches by its title too, and then shows with all its videos. */
fun matchesPlaylistTitle(task: DownloadTask, records: Map<String, PlaylistRecord>, query: String): Boolean =
    task.playlistId.isNotEmpty() && records[task.playlistId]?.title?.contains(query, ignoreCase = true) == true

/** The link of the saved playlist a download belongs to ("" when it is not one of a playlist's videos). */
fun playlistLinkOf(task: DownloadTask): String =
    if (task.playlistId.isEmpty()) "" else com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistStore.get(task.playlistId)?.url.orEmpty()
