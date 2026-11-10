package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.media_extractor.freePathNextTo
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.math.min

internal fun HttpChunkDownloader.deleteTaskScratchDir(taskId: String) {
    val dir = "${pathResolver.getTempDirectory()}/task_$taskId".toPath()
    if (!AppFileSystem.exists(dir)) return
    AppFileSystem.listOrNull(dir)?.forEach { child ->
        try {
            AppFileSystem.delete(child)
        } catch (_: Exception) {
        }
    }
    try {
        AppFileSystem.delete(dir)
    } catch (_: Exception) {
    }
}

internal fun HttpChunkDownloader.cleanupTempFiles(task: DownloadTask) {
    val cacheDir = pathResolver.getTempDirectory()
    // Parts are written to <temp>/task_<id>/<name>.partN (see executeDownload).
    val cachePath = "$cacheDir/task_${task.id}/${task.fileName}"
    val name = task.fileName
    try {
        val dir = "$cacheDir/task_${task.id}".toPath()
        AppFileSystem.list(dir).forEach { p ->
            val n = p.name
            if (n.startsWith("$name.part") || n == "$name.chunks") AppFileSystem.delete(p)
        }
    } catch (_: Exception) {
        for (i in 0..ChunkLayout.MAX_CONNECTIONS * 4) {
            try {
                AppFileSystem.delete("${cachePath}.part$i".toPath())
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Copies the finished file to the user's folder. A file with the same name that cannot be
 * deleted (Android: made by an earlier install of the app) keeps its place and the download is
 * saved as `name (1).ext`. Returns the path it was saved to.
 */
internal fun copyIntoPlace(from: okio.Path, target: okio.Path): okio.Path {
    try {
        target.parent?.let { AppFileSystem.createDirectories(it) }
    } catch (_: Exception) {
    }
    val to = if (AppFileSystem.exists(target) &&
        runCatching { AppFileSystem.delete(target) }.isFailure
    ) freePathNextTo(target) else target
    AppFileSystem.copy(from, to)
    return to
}

internal fun deleteRecursively(path: okio.Path) {
    try {
        if (!AppFileSystem.exists(path)) return
        val metadata = AppFileSystem.metadataOrNull(path)
        if (metadata?.isDirectory == true) {
            AppFileSystem.listOrNull(path)?.forEach { child ->
                deleteRecursively(child)
            }
        }
        AppFileSystem.delete(path)
    } catch (_: Exception) {
    }
}

internal fun trimFile(path: okio.Path, keep: Long) {
    try {
        val tmp = "$path.trim".toPath()
        AppFileSystem.source(path).buffer().use { src ->
            AppFileSystem.sink(tmp).buffer().use { out ->
                var left = keep
                val buf = ByteArray(256 * 1024)
                while (left > 0) {
                    val n = src.read(buf, 0, min(buf.size.toLong(), left).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n); left -= n
                }
            }
        }
        AppFileSystem.atomicMove(tmp, path)
    } catch (_: Exception) {
    }
}

internal fun calculateOptimalChunkCount(fileSize: Long): Int {
    return when {
        fileSize > 100 * 1024 * 1024 -> 8 // 8 parallel connections for >100MB
        fileSize > 20 * 1024 * 1024 -> 6  // 6 parallel connections for >20MB
        else -> 4                          // 4 parallel connections for >2MB
    }
}
