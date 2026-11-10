@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.win

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
import platform.windows.HANDLE
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

class WinZipException(message: String) : Exception(message)

class WinZipEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val method: Int,
    val crc: UInt,
    internal val headerOffset: Long,
)

private const val END_SIGNATURE = 0x06054b50
private const val CENTRAL_SIGNATURE = 0x02014b50
private const val LOCAL_SIGNATURE = 0x04034b50
private const val CP437_HIGH =
    "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "

private fun u16(bytes: ByteArray, at: Int): Int = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

private fun u32(bytes: ByteArray, at: Int): Long =
    u16(bytes, at).toLong() or (u16(bytes, at + 2).toLong() shl 16)

private fun readFully(handle: HANDLE?, offset: Long, count: Int): ByteArray {
    val bytes = ByteArray(count)
    var done = 0
    while (done < count) {
        val read = winReadAt(handle, offset + done, bytes, done, count - done)
        if (read <= 0) throw WinZipException("unexpected end of the zip file")
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

private fun readEntries(handle: HANDLE?): List<WinZipEntry> {
    val size = winFileSize(handle)
    if (size < 22) throw WinZipException("not a zip file")
    val tailSize = minOf(size, 65_557L).toInt()
    val tail = readFully(handle, size - tailSize, tailSize)
    var at = tailSize - 22
    while (at >= 0 && u32(tail, at).toInt() != END_SIGNATURE) at--
    if (at < 0) throw WinZipException("no zip directory found")
    val count = u16(tail, at + 10)
    val directorySize = u32(tail, at + 12)
    val directoryOffset = u32(tail, at + 16)
    if (count == 0xFFFF || directoryOffset == 0xFFFFFFFFL) throw WinZipException("zip64 archives are not supported")
    if (directoryOffset + directorySize > size) throw WinZipException("broken zip directory")
    val directory = readFully(handle, directoryOffset, directorySize.toInt())
    val entries = ArrayList<WinZipEntry>(count)
    var cursor = 0
    repeat(count) {
        if (cursor + 46 > directory.size || u32(directory, cursor).toInt() != CENTRAL_SIGNATURE) {
            throw WinZipException("broken zip directory entry")
        }
        val flags = u16(directory, cursor + 8)
        val method = u16(directory, cursor + 10)
        val crc = u32(directory, cursor + 16).toUInt()
        val compressed = u32(directory, cursor + 20)
        val plain = u32(directory, cursor + 24)
        val nameLength = u16(directory, cursor + 28)
        val extraLength = u16(directory, cursor + 30)
        val commentLength = u16(directory, cursor + 32)
        val headerOffset = u32(directory, cursor + 42)
        val name = decodeName(directory.copyOfRange(cursor + 46, cursor + 46 + nameLength), flags and 0x800 != 0)
        if (flags and 1 != 0) throw WinZipException("encrypted zip entries are not supported")
        entries.add(
            WinZipEntry(name.replace('\\', '/'), name.endsWith('/') || name.endsWith('\\'), plain, compressed, method, crc, headerOffset)
        )
        cursor += 46 + nameLength + extraLength + commentLength
    }
    return entries
}

private fun safeRelativePath(name: String): String? {
    val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
    if (parts.isEmpty() || parts.any { it == ".." || it.contains(':') }) return null
    return parts.joinToString("\\")
}

private fun dataOffset(handle: HANDLE?, entry: WinZipEntry): Long {
    val header = readFully(handle, entry.headerOffset, 30)
    if (u32(header, 0).toInt() != LOCAL_SIGNATURE) throw WinZipException("broken zip entry ${entry.name}")
    return entry.headerOffset + 30 + u16(header, 26) + u16(header, 28)
}

private fun extract(handle: HANDLE?, entry: WinZipEntry, sink: (ByteArray, Int) -> Unit) {
    val start = dataOffset(handle, entry)
    val input = ByteArray(64 * 1024)
    var remaining = entry.compressedSize
    var position = start
    var checksum = 0u
    var produced = 0L
    when (entry.method) {
        0 -> while (remaining > 0) {
            val want = minOf(remaining, input.size.toLong()).toInt()
            val read = winReadAt(handle, position, input, 0, want)
            if (read <= 0) throw WinZipException("unexpected end of ${entry.name}")
            checksum = input.usePinned { crc32(checksum, it.addressOf(0).reinterpret(), read.toUInt()) }
            sink(input, read)
            produced += read
            position += read
            remaining -= read
        }

        8 -> memScoped {
            val stream = alloc<z_stream>()
            memset(stream.ptr, 0, sizeOf<z_stream>().convert())
            if (inflateInit2(stream.ptr, -15) != Z_OK) throw WinZipException("inflate could not start")
            try {
                val output = ByteArray(128 * 1024)
                var ended = false
                while (!ended) {
                    var available = 0
                    if (remaining > 0) {
                        val want = minOf(remaining, input.size.toLong()).toInt()
                        available = winReadAt(handle, position, input, 0, want)
                        if (available <= 0) throw WinZipException("unexpected end of ${entry.name}")
                        position += available
                        remaining -= available
                    }
                    input.usePinned { pinnedIn ->
                        output.usePinned { pinnedOut ->
                            stream.next_in = pinnedIn.addressOf(0).reinterpret()
                            stream.avail_in = available.toUInt()
                            var more: Boolean
                            do {
                                stream.next_out = pinnedOut.addressOf(0).reinterpret()
                                stream.avail_out = output.size.toUInt()
                                val code = inflate(stream.ptr, Z_NO_FLUSH)
                                if (code != Z_OK && code != Z_STREAM_END && code != Z_BUF_ERROR) {
                                    throw WinZipException("corrupt data in ${entry.name} ($code)")
                                }
                                val made = output.size - stream.avail_out.toInt()
                                if (made > 0) {
                                    checksum = crc32(checksum, pinnedOut.addressOf(0).reinterpret(), made.toUInt())
                                    sink(output, made)
                                    produced += made
                                }
                                if (code == Z_STREAM_END) ended = true
                                more = !ended && code == Z_OK && (stream.avail_in > 0u || stream.avail_out == 0u)
                            } while (more)
                        }
                    }
                    if (!ended && remaining <= 0L && available == 0) {
                        throw WinZipException("truncated data in ${entry.name}")
                    }
                }
            } finally {
                inflateEnd(stream.ptr)
            }
        }

        else -> throw WinZipException("compression method ${entry.method} of ${entry.name} is not supported")
    }
    if (produced != entry.size) throw WinZipException("wrong size for ${entry.name}")
    if (checksum != entry.crc) throw WinZipException("checksum mismatch in ${entry.name}")
}

fun winZipEntries(zipPath: String): List<WinZipEntry> {
    val handle = winOpenFile(zipPath, write = false, disposition = WIN_OPEN_EXISTING)
        ?: throw WinZipException("cannot open $zipPath")
    try {
        return readEntries(handle)
    } finally {
        winCloseFile(handle)
    }
}

fun winZipReadEntry(zipPath: String, name: String): ByteArray? {
    val handle = winOpenFile(zipPath, write = false, disposition = WIN_OPEN_EXISTING)
        ?: throw WinZipException("cannot open $zipPath")
    try {
        val entry = readEntries(handle).firstOrNull { it.name == name && !it.isDirectory } ?: return null
        if (entry.size > Int.MAX_VALUE) throw WinZipException("${entry.name} is too large")
        val out = ByteArray(entry.size.toInt())
        var at = 0
        extract(handle, entry) { bytes, count ->
            bytes.copyInto(out, at, 0, count)
            at += count
        }
        return out
    } finally {
        winCloseFile(handle)
    }
}

fun winUnzip(
    zipPath: String,
    targetDir: String,
    onEntry: ((name: String, index: Int, total: Int) -> Unit)? = null,
): Int {
    val handle = winOpenFile(zipPath, write = false, disposition = WIN_OPEN_EXISTING)
        ?: throw WinZipException("cannot open $zipPath")
    try {
        val entries = readEntries(handle)
        if (!winCreateDirectories(targetDir)) throw WinZipException("cannot create $targetDir")
        var files = 0
        entries.forEachIndexed { index, entry ->
            val relative = safeRelativePath(entry.name) ?: throw WinZipException("unsafe path in the zip: ${entry.name}")
            val target = "$targetDir\\$relative"
            onEntry?.invoke(entry.name, index, entries.size)
            if (entry.isDirectory) {
                if (!winCreateDirectories(target)) throw WinZipException("cannot create $target")
                return@forEachIndexed
            }
            val parent = target.substringBeforeLast('\\')
            if (!winCreateDirectories(parent)) throw WinZipException("cannot create $parent")
            val out = winOpenFile(target, write = true, disposition = WIN_CREATE_ALWAYS)
                ?: throw WinZipException("cannot write $target")
            try {
                var offset = 0L
                extract(handle, entry) { bytes, count ->
                    if (!winWriteAt(out, offset, bytes, 0, count)) throw WinZipException("write failed for $target")
                    offset += count
                }
            } finally {
                winCloseFile(out)
            }
            files++
        }
        return files
    } finally {
        winCloseFile(handle)
    }
}

fun winUnzipEntryTo(zipPath: String, targetFile: String, match: (WinZipEntry) -> Boolean): Boolean {
    val handle = winOpenFile(zipPath, write = false, disposition = WIN_OPEN_EXISTING)
        ?: throw WinZipException("cannot open $zipPath")
    try {
        val entry = readEntries(handle).firstOrNull { !it.isDirectory && match(it) } ?: return false
        val out = winOpenFile(targetFile, write = true, disposition = WIN_CREATE_ALWAYS)
            ?: throw WinZipException("cannot write $targetFile")
        try {
            var offset = 0L
            extract(handle, entry) { bytes, count ->
                if (!winWriteAt(out, offset, bytes, 0, count)) throw WinZipException("write failed for $targetFile")
                offset += count
            }
        } finally {
            winCloseFile(out)
        }
        return true
    } finally {
        winCloseFile(handle)
    }
}
