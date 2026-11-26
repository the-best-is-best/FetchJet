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
    if (runCatching { AppFileSystem.atomicMove(from, to) }.isSuccess) return to
    val staged = "$to.fjcopy".toPath()
    try {
        if (AppFileSystem.exists(staged)) AppFileSystem.delete(staged)
        copyFileInBlocks(from, staged)
        AppFileSystem.atomicMove(staged, to)
    } catch (e: Exception) {
        runCatching { AppFileSystem.delete(staged) }
        throw e
    }
    return to
}

internal const val IO_BLOCK = 1024 * 1024

/** Copies a file with large reads and writes (the default copy moves 8 KB at a time). */
internal fun copyFileInBlocks(from: okio.Path, to: okio.Path) {
    val source = AppFileSystem.openReadOnly(from)
    try {
        val sink = AppFileSystem.openReadWrite(to)
        try {
            sink.resize(0)
            val block = ByteArray(IO_BLOCK)
            var offset = 0L
            while (true) {
                val read = source.read(offset, block, 0, block.size)
                if (read <= 0) break
                sink.write(offset, block, 0, read)
                offset += read
            }
        } finally {
            sink.close()
        }
    } finally {
        source.close()
    }
}

private const val PART_BUFFER = 256 * 1024
private const val PART_FLUSH_MIN = 128 * 1024
private const val PART_FLUSH_MS = 200L

/**
 * Appends to a part file through one big buffer: the bytes reach the file in writes of at least
 * 128 KB (or after 200 ms), not 8 KB at a time. [flush] and [close] put the rest on disk.
 */
internal class PartWriter(path: okio.Path, truncate: Boolean = false) {
    private val handle = AppFileSystem.openReadWrite(path).also { if (truncate) it.resize(0) }
    private var offset = handle.size()
    private val buffer = ByteArray(PART_BUFFER)
    private var filled = 0
    private var lastFlush = kotlin.time.TimeSource.Monotonic.markNow()

    /** Free room in the buffer: read straight into [buffer] from [position]. */
    val array: ByteArray get() = buffer
    val position: Int get() = filled
    val room: Int get() = buffer.size - filled

    fun added(count: Int) {
        filled += count
        if (filled >= PART_FLUSH_MIN || (filled > 0 && lastFlush.elapsedNow().inWholeMilliseconds >= PART_FLUSH_MS)) flush()
    }

    fun flush() {
        if (filled > 0) {
            handle.write(offset, buffer, 0, filled)
            offset += filled
            filled = 0
        }
        lastFlush = kotlin.time.TimeSource.Monotonic.markNow()
    }

    fun close() {
        try {
            flush()
        } finally {
            handle.close()
        }
    }
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
    val tmp = "$path.trim".toPath()
    try {
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
    } catch (e: Exception) {
        runCatching { AppFileSystem.delete(tmp) }
        throw e
    }
}

/** The split of versions that kept no piece map: their parts on disk follow it. */
internal fun legacyChunkCount(fileSize: Long): Int = when {
    fileSize > 100L * 1024 * 1024 -> 8
    fileSize > 20L * 1024 * 1024 -> 6
    else -> 4
}

internal fun calculateOptimalChunkCount(fileSize: Long): Int {
    return when {
        fileSize > 16L * 1024 * 1024 -> 32
        fileSize > 2L * 1024 * 1024 -> 16
        else -> 4
    }
}
