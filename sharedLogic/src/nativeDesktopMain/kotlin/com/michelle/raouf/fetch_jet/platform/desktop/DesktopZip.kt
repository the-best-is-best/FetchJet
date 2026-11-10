@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.desktop

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memset
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

class DesktopZipException(message: String) : Exception(message)

class DesktopZipEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val method: Int,
    val crc: UInt,
    val unixMode: Int,
    internal val headerOffset: Long,
)

object DesktopZip {
    private const val END_SIGNATURE = 0x06054b50
    private const val CENTRAL_SIGNATURE = 0x02014b50
    private const val LOCAL_SIGNATURE = 0x04034b50
    private const val MADE_ON_UNIX = 3
    private const val CP437_HIGH =
        "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "

    private fun u16(bytes: ByteArray, at: Int): Int = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Long = u16(bytes, at).toLong() or (u16(bytes, at + 2).toLong() shl 16)

    private fun readFully(handle: OsFile, offset: Long, count: Int): ByteArray {
        val bytes = ByteArray(count)
        var done = 0
        while (done < count) {
            val read = handle.read(offset + done, bytes, done, count - done)
            if (read <= 0) throw DesktopZipException("unexpected end of the zip file")
            done += read
        }
        return bytes
    }

    private fun decodeName(bytes: ByteArray, utf8: Boolean): String {
        if (utf8 || bytes.all { it >= 0 }) return bytes.decodeToString()
        val out = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(if (v < 0x80) v.toChar() else CP437_HIGH[v - 0x80])
        }
        return out.toString()
    }

    private fun readEntries(handle: OsFile): List<DesktopZipEntry> {
        val size = handle.size
        if (size < 22) throw DesktopZipException("not a zip file")
        val tailSize = minOf(size, 65_557L).toInt()
        val tail = readFully(handle, size - tailSize, tailSize)
        var at = tailSize - 22
        while (at >= 0 && u32(tail, at).toInt() != END_SIGNATURE) at--
        if (at < 0) throw DesktopZipException("no zip directory found")
        val count = u16(tail, at + 10)
        val directorySize = u32(tail, at + 12)
        val directoryOffset = u32(tail, at + 16)
        if (count == 0xFFFF || directoryOffset == 0xFFFFFFFFL) throw DesktopZipException("zip64 archives are not supported")
        if (directoryOffset + directorySize > size) throw DesktopZipException("broken zip directory")
        val directory = readFully(handle, directoryOffset, directorySize.toInt())
        val entries = ArrayList<DesktopZipEntry>(count)
        var cursor = 0
        repeat(count) {
            if (cursor + 46 > directory.size || u32(directory, cursor).toInt() != CENTRAL_SIGNATURE) {
                throw DesktopZipException("broken zip directory entry")
            }
            val madeBy = u16(directory, cursor + 4) shr 8
            val flags = u16(directory, cursor + 8)
            val method = u16(directory, cursor + 10)
            val crc = u32(directory, cursor + 16).toUInt()
            val compressed = u32(directory, cursor + 20)
            val plain = u32(directory, cursor + 24)
            val nameLength = u16(directory, cursor + 28)
            val extraLength = u16(directory, cursor + 30)
            val commentLength = u16(directory, cursor + 32)
            val external = u32(directory, cursor + 38)
            val headerOffset = u32(directory, cursor + 42)
            val name = decodeName(directory.copyOfRange(cursor + 46, cursor + 46 + nameLength), flags and 0x800 != 0)
            if (flags and 1 != 0) throw DesktopZipException("encrypted zip entries are not supported")
            val mode = if (madeBy == MADE_ON_UNIX) ((external shr 16) and 0xFFFF).toInt() else 0
            entries.add(
                DesktopZipEntry(
                    name.replace('\\', '/'), name.endsWith('/') || name.endsWith('\\'), plain, compressed, method, crc, mode, headerOffset
                )
            )
            cursor += 46 + nameLength + extraLength + commentLength
        }
        return entries
    }

    private fun safeRelativePath(name: String): String? {
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." || it.contains(':') }) return null
        return parts.joinToString(DesktopOs.separator.toString())
    }

    private fun extract(handle: OsFile, entry: DesktopZipEntry, sink: (ByteArray, Int) -> Unit) {
        val header = readFully(handle, entry.headerOffset, 30)
        if (u32(header, 0).toInt() != LOCAL_SIGNATURE) throw DesktopZipException("broken zip entry ${entry.name}")
        var position = entry.headerOffset + 30 + u16(header, 26) + u16(header, 28)
        val input = ByteArray(64 * 1024)
        var remaining = entry.compressedSize
        var checksum = 0uL
        var produced = 0L
        when (entry.method) {
            0 -> while (remaining > 0) {
                val want = minOf(remaining, input.size.toLong()).toInt()
                val read = handle.read(position, input, 0, want)
                if (read <= 0) throw DesktopZipException("unexpected end of ${entry.name}")
                checksum = input.usePinned { crc32(checksum.convert(), it.addressOf(0).reinterpret(), read.convert()).convert() }
                sink(input, read)
                produced += read
                position += read
                remaining -= read
            }

            8 -> memScoped {
                val stream = alloc<z_stream>()
                memset(stream.ptr, 0, sizeOf<z_stream>().convert())
                if (inflateInit2(stream.ptr, -15) != Z_OK) throw DesktopZipException("inflate could not start")
                try {
                    val output = ByteArray(128 * 1024)
                    var ended = false
                    while (!ended) {
                        var available = 0
                        if (remaining > 0) {
                            val want = minOf(remaining, input.size.toLong()).toInt()
                            available = handle.read(position, input, 0, want)
                            if (available <= 0) throw DesktopZipException("unexpected end of ${entry.name}")
                            position += available
                            remaining -= available
                        }
                        input.usePinned { pinnedIn ->
                            output.usePinned { pinnedOut ->
                                stream.next_in = pinnedIn.addressOf(0).reinterpret()
                                stream.avail_in = available.convert()
                                var more: Boolean
                                do {
                                    stream.next_out = pinnedOut.addressOf(0).reinterpret()
                                    stream.avail_out = output.size.convert()
                                    val code = inflate(stream.ptr, Z_NO_FLUSH)
                                    if (code != Z_OK && code != Z_STREAM_END && code != Z_BUF_ERROR) {
                                        throw DesktopZipException("corrupt data in ${entry.name} ($code)")
                                    }
                                    val made = output.size - stream.avail_out.toInt()
                                    if (made > 0) {
                                        checksum = crc32(checksum.convert(), pinnedOut.addressOf(0).reinterpret(), made.convert()).convert()
                                        sink(output, made)
                                        produced += made
                                    }
                                    if (code == Z_STREAM_END) ended = true
                                    more = !ended && code == Z_OK && (stream.avail_in.toInt() > 0 || stream.avail_out.toInt() == 0)
                                } while (more)
                            }
                        }
                        if (!ended && remaining <= 0L && available == 0) {
                            throw DesktopZipException("truncated data in ${entry.name}")
                        }
                    }
                } finally {
                    inflateEnd(stream.ptr)
                }
            }

            else -> throw DesktopZipException("compression method ${entry.method} of ${entry.name} is not supported")
        }
        if (produced != entry.size) throw DesktopZipException("wrong size for ${entry.name}")
        if (checksum.toUInt() != entry.crc) throw DesktopZipException("checksum mismatch in ${entry.name}")
    }

    private fun writeEntry(handle: OsFile, entry: DesktopZipEntry, target: String) {
        val out = DesktopOs.openFile(target, write = true) ?: throw DesktopZipException("cannot write $target")
        try {
            var offset = 0L
            extract(handle, entry) { bytes, count ->
                if (!out.write(offset, bytes, 0, count)) throw DesktopZipException("cannot write $target")
                offset += count
            }
        } finally {
            out.close()
        }
        if (entry.unixMode and 0x49 != 0) DesktopOs.makeExecutable(target)
    }

    private inline fun <T> open(zipPath: String, block: (OsFile) -> T): T {
        val handle = DesktopOs.openFile(zipPath, write = false) ?: throw DesktopZipException("cannot open $zipPath")
        try {
            return block(handle)
        } finally {
            handle.close()
        }
    }

    fun entries(zipPath: String): List<DesktopZipEntry> = open(zipPath) { readEntries(it) }

    fun unzip(zipPath: String, targetDir: String): Int = open(zipPath) { handle ->
        val entries = readEntries(handle)
        if (!DesktopOs.createDirectories(targetDir)) throw DesktopZipException("cannot create $targetDir")
        var files = 0
        for (entry in entries) {
            val relative = safeRelativePath(entry.name) ?: throw DesktopZipException("unsafe path in the zip: ${entry.name}")
            val target = DesktopOs.join(targetDir, relative)
            if (entry.isDirectory) {
                if (!DesktopOs.createDirectories(target)) throw DesktopZipException("cannot create $target")
                continue
            }
            val parent = target.substringBeforeLast(DesktopOs.separator)
            if (!DesktopOs.createDirectories(parent)) throw DesktopZipException("cannot create $parent")
            writeEntry(handle, entry, target)
            files++
        }
        files
    }

    fun unzipEntryTo(zipPath: String, targetFile: String, match: (DesktopZipEntry) -> Boolean): Boolean = open(zipPath) { handle ->
        val entry = readEntries(handle).firstOrNull { !it.isDirectory && match(it) } ?: return@open false
        writeEntry(handle, entry, targetFile)
        true
    }
}
