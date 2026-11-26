package com.michelle.raouf.fetch_jet.feature.media_player

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia
import com.michelle.raouf.fetch_jet.feature.hidden.newStoreLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

object HiddenPlayback {
    private val lock = newStoreLock()
    private val serial = Mutex()
    private var copies: Map<String, String> = emptyMap()
    private var pathResolver: PathResolver? = null
    private var find: (String) -> DownloadTask? = { null }

    fun attach(pathResolver: PathResolver, find: (String) -> DownloadTask?) = lock.withLock {
        this.pathResolver = pathResolver
        this.find = find
    }

    fun needsPrepare(item: PlayerItem): Boolean = item.hidden

    suspend fun prepare(taskId: String, onProgress: (Float) -> Unit): String? = serial.withLock {
        val (resolver, lookup) = lock.withLock { (pathResolver ?: PathResolver()) to find }
        val task = lookup(taskId)?.takeIf { it.hidden && MediaPlayback.canPlay(it, allowHidden = true) } ?: return@withLock null
        val kept = lock.withLock { copies[taskId] }
        if (kept != null && AppFileSystem.exists(kept.toPath())) {
            releaseOthers(taskId)
            onProgress(1f)
            return@withLock kept
        }
        releaseAll()
        val path = HiddenMedia.openPlayable(taskId, task.fileName, onProgress, resolver) ?: return@withLock null
        if (HiddenMedia.isPlayCopy(path, resolver)) lock.withLock { copies = copies + (taskId to path) }
        path
    }

    fun release(taskId: String) {
        val path = lock.withLock {
            val p = copies[taskId]
            copies = copies - taskId
            p
        } ?: return
        deleteCopy(path)
    }

    fun releaseAll() {
        val all = lock.withLock {
            val c = copies.values.toList()
            copies = emptyMap()
            c
        }
        all.forEach(::deleteCopy)
    }

    private fun releaseOthers(taskId: String) {
        lock.withLock { copies.keys.filter { it != taskId } }.forEach(::release)
    }

    internal fun copyOf(taskId: String): String? = lock.withLock { copies[taskId] }

    private fun deleteCopy(path: String) {
        runCatching {
            val folder = path.toPath().parent ?: return
            if (AppFileSystem.exists(folder)) AppFileSystem.deleteRecursively(folder)
        }
    }
}
