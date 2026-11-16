package com.michelle.raouf.fetch_jet.feature.archive_extract

import okio.Buffer
import okio.BufferedSource
import okio.FileHandle
import okio.Source
import okio.Timeout
import okio.buffer

internal class ZipEntry(
    val name: String,
    val isDirectory: Boolean,
    val isLink: Boolean,
    val encrypted: Boolean,
    val method: Int,
    val crc: Long,
    val size: Long,
    val compressedSize: Long,
    val headerOffset: Long,
)

internal class BoundedSource(private val upstream: Source, private var left: Long) : Source {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (left <= 0) return -1
        val read = upstream.read(sink, minOf(byteCount, left))
        if (read > 0) left -= read
        return read
    }

    override fun timeout(): Timeout = upstream.timeout()
    override fun close() = upstream.close()
}

internal class ZipReader(private val handle: FileHandle) {
    private val fileSize = handle.size()

    private fun readAt(offset: Long, count: Int): ByteArray {
        val bytes = ByteArray(count)
        var done = 0
        while (done < count) {
            val read = handle.read(offset + done, bytes, done, count - done)
            if (read <= 0) throw ArchiveException("the zip file ends too early")
            done += read
        }
        return bytes
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)
    private fun u64(b: ByteArray, at: Int): Long = u32(b, at) or (u32(b, at + 4) shl 32)

    fun entries(): List<ZipEntry> {
        val tail = minOf(fileSize, 65557L).toInt()
        if (tail < 22) throw ArchiveException("this is not a zip file")
        val end = readAt(fileSize - tail, tail)
        var at = tail - 22
        while (at >= 0 && u32(end, at) != END_SIGNATURE) at--
        if (at < 0) throw ArchiveException("this is not a zip file")
        var count = u16(end, at + 10).toLong()
        var centralSize = u32(end, at + 12)
        var centralOffset = u32(end, at + 16)
        if (count == 0xFFFFL || centralSize == 0xFFFFFFFFL || centralOffset == 0xFFFFFFFFL) {
            val locatorAt = fileSize - tail + at - 20
            if (locatorAt >= 0) {
                val locator = readAt(locatorAt, 20)
                if (u32(locator, 0) == LOCATOR64_SIGNATURE) {
                    val record = readAt(u64(locator, 8), 56)
                    if (u32(record, 0) == END64_SIGNATURE) {
                        count = u64(record, 32)
                        centralSize = u64(record, 40)
                        centralOffset = u64(record, 48)
                    }
                }
            }
        }
        if (centralOffset < 0 || centralSize < 0 || centralOffset + centralSize > fileSize || centralSize > 1L shl 30) {
            throw ArchiveException("the zip file is damaged")
        }
        val directory = readAt(centralOffset, centralSize.toInt())
        val list = ArrayList<ZipEntry>()
        var p = 0
        while (p + 46 <= directory.size && list.size < count) {
            if (u32(directory, p) != CENTRAL_SIGNATURE) throw ArchiveException("the zip file is damaged")
            val madeBy = u16(directory, p + 4) ushr 8
            val flags = u16(directory, p + 8)
            val method = u16(directory, p + 10)
            val crc = u32(directory, p + 16)
            var compressed = u32(directory, p + 20)
            var size = u32(directory, p + 24)
            val nameLen = u16(directory, p + 28)
            val extraLen = u16(directory, p + 30)
            val commentLen = u16(directory, p + 32)
            val external = u32(directory, p + 38)
            var offset = u32(directory, p + 42)
            if (p + 46 + nameLen + extraLen + commentLen > directory.size) throw ArchiveException("the zip file is damaged")
            val rawName = directory.copyOfRange(p + 46, p + 46 + nameLen)
            var e = p + 46 + nameLen
            val extraEnd = e + extraLen
            while (e + 4 <= extraEnd) {
                val id = u16(directory, e)
                val len = u16(directory, e + 2)
                if (id == 1) {
                    var q = e + 4
                    if (size == 0xFFFFFFFFL && q + 8 <= e + 4 + len) {
                        size = u64(directory, q); q += 8
                    }
                    if (compressed == 0xFFFFFFFFL && q + 8 <= e + 4 + len) {
                        compressed = u64(directory, q); q += 8
                    }
                    if (offset == 0xFFFFFFFFL && q + 8 <= e + 4 + len) {
                        offset = u64(directory, q)
                    }
                }
                e += 4 + len
            }
            val name = if (flags and 0x800 != 0) rawName.decodeToString() else decodeCp437(rawName)
            val unixMode = if (madeBy == 3) (external ushr 16).toInt() else 0
            val type = unixMode and 0xF000
            list.add(
                ZipEntry(
                    name = name,
                    isDirectory = name.endsWith("/") || type == 0x4000,
                    isLink = type == 0xA000 || type == 0x2000 || type == 0x6000 || type == 0x1000 || type == 0xC000,
                    encrypted = flags and 1 != 0,
                    method = method,
                    crc = crc,
                    size = size,
                    compressedSize = compressed,
                    headerOffset = offset,
                )
            )
            p += 46 + nameLen + extraLen + commentLen
        }
        return list
    }

    /** The decompressed bytes of [entry]; the caller checks the size and the CRC. */
    fun open(entry: ZipEntry): Source {
        val local = readAt(entry.headerOffset, 30)
        if (u32(local, 0) != LOCAL_SIGNATURE) throw ArchiveException("the zip file is damaged")
        val dataAt = entry.headerOffset + 30 + u16(local, 26) + u16(local, 28)
        if (dataAt + entry.compressedSize > fileSize) throw ArchiveException("the zip file is damaged")
        val raw = BoundedSource(handle.source(dataAt), entry.compressedSize).buffer()
        return when (entry.method) {
            0 -> raw
            8 -> InflateSource(raw)
            else -> throw ArchiveException("compression method ${entry.method} is not supported")
        }
    }

    private fun decodeCp437(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            builder.append(if (v < 128) v.toChar() else CP437_HIGH[v - 128])
        }
        return builder.toString()
    }

    private companion object {
        const val END_SIGNATURE = 0x06054b50L
        const val CENTRAL_SIGNATURE = 0x02014b50L
        const val LOCAL_SIGNATURE = 0x04034b50L
        const val LOCATOR64_SIGNATURE = 0x07064b50L
        const val END64_SIGNATURE = 0x06064b50L
        const val CP437_HIGH =
            "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "
    }
}
