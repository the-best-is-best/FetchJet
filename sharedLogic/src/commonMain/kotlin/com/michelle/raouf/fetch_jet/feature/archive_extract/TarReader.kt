package com.michelle.raouf.fetch_jet.feature.archive_extract

import okio.BufferedSink
import okio.BufferedSource

internal class TarEntry(
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val isFile: Boolean,
)

internal class TarReader(private val source: BufferedSource) {
    private var dataLeft = 0L
    private var padding = 0L

    private fun cString(block: ByteArray, from: Int, length: Int): String {
        var end = from
        while (end < from + length && block[end].toInt() != 0) end++
        return block.copyOfRange(from, end).decodeToString()
    }

    private fun number(block: ByteArray, from: Int, length: Int): Long {
        if (block[from].toInt() and 0x80 != 0) {
            var value = (block[from].toInt() and 0x7F).toLong()
            for (i in 1 until length) value = (value shl 8) or (block[from + i].toLong() and 0xFF)
            return value
        }
        var value = 0L
        var seen = false
        for (i in from until from + length) {
            val c = block[i].toInt()
            if (c in '0'.code..'7'.code) {
                value = value * 8 + (c - '0'.code)
                seen = true
            } else if (seen || (c != 0 && c != ' '.code)) {
                break
            }
        }
        return value
    }

    private fun readBlock(): ByteArray? {
        if (!source.request(512)) {
            if (source.exhausted()) return null
            throw ArchiveException("the tar data ends too early")
        }
        return source.readByteArray(512)
    }

    private fun checksumOk(block: ByteArray): Boolean {
        var sum = 0L
        for (i in 0 until 512) sum += if (i in 148..155) 32 else (block[i].toLong() and 0xFF)
        return sum == number(block, 148, 8)
    }

    private fun readData(size: Long): ByteArray {
        if (size > 1 shl 20) throw ArchiveException("the tar header is too large")
        val data = source.readByteArray(size)
        source.skip((512 - size % 512) % 512)
        return data
    }

    private fun paxValue(data: ByteArray, key: String): String? {
        var at = 0
        while (at < data.size) {
            var space = at
            while (space < data.size && data[space].toInt() != ' '.code) space++
            if (space >= data.size) break
            val length = data.copyOfRange(at, space).decodeToString().toIntOrNull() ?: break
            if (length <= 0 || at + length > data.size) break
            var end = at + length
            if (end > space + 1 && data[end - 1].toInt() == '\n'.code) end--
            val record = data.copyOfRange(space + 1, end).decodeToString()
            val cut = record.indexOf('=')
            if (cut > 0 && record.substring(0, cut) == key) return record.substring(cut + 1)
            at += length
        }
        return null
    }

    /** The next entry, or null at the end. The data of the previous entry is skipped if it was not read. */
    fun next(): TarEntry? {
        skipRest()
        var longName: String? = null
        var paxName: String? = null
        var paxSize: Long? = null
        while (true) {
            val block = readBlock() ?: return null
            if (block.all { it.toInt() == 0 }) return null
            if (!checksumOk(block)) throw ArchiveException("this is not a tar file")
            var size = number(block, 124, 12)
            val type = block[156].toInt().toChar()
            when (type) {
                'L' -> {
                    longName = readData(size).decodeToString().trimEnd('\u0000')
                    continue
                }

                'K' -> {
                    readData(size)
                    continue
                }

                'x' -> {
                    val data = readData(size)
                    paxName = paxValue(data, "path") ?: paxName
                    paxSize = paxValue(data, "size")?.toLongOrNull() ?: paxSize
                    continue
                }

                'g' -> {
                    readData(size)
                    continue
                }
            }
            var name = cString(block, 0, 100)
            if (cString(block, 257, 5) == "ustar") {
                val prefix = cString(block, 345, 155)
                if (prefix.isNotEmpty()) name = "$prefix/$name"
            }
            name = paxName ?: longName ?: name
            if (paxSize != null) size = paxSize
            val isFile = type == '0' || type == '\u0000' || type == '7'
            val isDirectory = type == '5' || (isFile && name.endsWith("/"))
            val hasData = isFile && !isDirectory
            dataLeft = if (hasData) size else if (type == '1' || type == '2' || type == '3' || type == '4' || type == '5' || type == '6') 0 else size
            padding = if (dataLeft > 0) (512 - dataLeft % 512) % 512 else 0
            return TarEntry(name, if (hasData) size else 0, isDirectory, hasData)
        }
    }

    /** Copies up to [limit] bytes of the current file into [sink]; returns what was copied. */
    fun copyTo(sink: BufferedSink, limit: Long): Long {
        var copied = 0L
        val chunk = ByteArray(65536)
        while (dataLeft > 0 && copied < limit) {
            val want = minOf(dataLeft, chunk.size.toLong(), limit - copied).toInt()
            val read = source.read(chunk, 0, want)
            if (read <= 0) throw ArchiveException("the tar data ends too early")
            sink.write(chunk, 0, read)
            dataLeft -= read
            copied += read
        }
        return copied
    }

    private fun skipRest() {
        if (dataLeft > 0) {
            source.skip(dataLeft)
            dataLeft = 0
        }
        if (padding > 0) {
            source.skip(padding)
            padding = 0
        }
    }
}
