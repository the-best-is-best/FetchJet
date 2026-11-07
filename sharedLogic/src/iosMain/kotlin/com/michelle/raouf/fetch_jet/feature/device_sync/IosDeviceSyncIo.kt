package com.michelle.raouf.fetch_jet.feature.device_sync

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

internal fun fileExists(path: String): Boolean = FileSystem.SYSTEM.exists(path.toPath())

internal fun fileSize(path: String): Long =
    FileSystem.SYSTEM.metadataOrNull(path.toPath())?.size ?: 0L

internal fun fileName(path: String): String = path.toPath().name

/** Streams a file out in [chunk]-sized byte arrays. */
internal class FileChunkReader(path: String, private val chunk: Int) {
    private val source = FileSystem.SYSTEM.source(path.toPath()).buffer()

    /** The next chunk (1..[chunk] bytes), or null at end of file. */
    fun next(): ByteArray? {
        if (source.exhausted()) return null
        val sink = okio.Buffer()
        val n = source.read(sink, chunk.toLong())
        if (n <= 0L) return null
        return sink.readByteArray()
    }

    fun close() = source.close()
}

/** Appends received bytes to a file. */
internal class FileChunkWriter(path: String) {
    private val sink = FileSystem.SYSTEM.sink(path.toPath()).buffer()

    fun write(bytes: ByteArray) {
        sink.write(bytes)
    }

    fun close() {
        sink.flush()
        sink.close()
    }
}
