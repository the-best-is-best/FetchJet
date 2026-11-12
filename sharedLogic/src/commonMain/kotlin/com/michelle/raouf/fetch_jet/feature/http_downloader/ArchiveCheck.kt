package com.michelle.raouf.fetch_jet.feature.http_downloader

import okio.Buffer
import okio.FileHandle
import okio.Inflater
import okio.InflaterSource
import okio.Source
import okio.Timeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

internal class ArchiveReport(
    val kind: String,
    val problem: String?,
    val damaged: List<LongRange> = emptyList(),
    val entries: Int = 0,
    val crcChecked: Int = 0
) {
    val isDamaged: Boolean get() = problem != null
}

internal class Crc32 {
    private var state = -1

    fun update(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset) {
        var c = state
        for (i in offset until offset + count) {
            c = TABLE[(c xor bytes[i].toInt()) and 0xFF] xor (c ushr 8)
        }
        state = c
    }

    val value: Long get() = state.inv().toLong() and 0xFFFFFFFFL

    private companion object {
        val TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            c
        }
    }
}

internal fun archiveKindOf(fileName: String): String? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "zip", "jar", "apk", "epub", "docx", "xlsx", "pptx" -> "zip"
        "7z" -> "7z"
        "rar" -> "rar"
        else -> null
    }

internal fun checkArchive(kind: String, handle: FileHandle, onBytes: (Long) -> Unit = {}): ArchiveReport =
    try {
        when (kind) {
            "zip" -> checkZip(handle, onBytes)
            "7z" -> check7z(handle)
            "rar" -> checkRar(handle)
            else -> ArchiveReport(kind, null)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ArchiveReport(kind, null)
    }

private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

private fun u64(b: ByteArray, at: Int): Long = u32(b, at) or (u32(b, at + 4) shl 32)

private fun FileHandle.bytesAt(offset: Long, count: Int): ByteArray? {
    if (offset < 0 || count < 0 || offset + count > size()) return null
    val out = ByteArray(count)
    var done = 0
    while (done < count) {
        val read = read(offset + done, out, done, count - done)
        if (read <= 0) return null
        done += read
    }
    return out
}

private class LimitedSource(private val delegate: Source, private var left: Long) : Source {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (left <= 0L) return -1L
        val read = delegate.read(sink, min(byteCount, left))
        if (read > 0) left -= read
        return read
    }

    override fun timeout(): Timeout = delegate.timeout()

    override fun close() = delegate.close()
}

private fun crcOfSource(source: Source, limit: Long, onBytes: (Long) -> Unit): Pair<Long, Long> {
    val crc = Crc32()
    val buffer = Buffer()
    val block = ByteArray(64 * 1024)
    var total = 0L
    while (total <= limit) {
        val read = source.read(buffer, block.size.toLong())
        if (read == -1L) break
        while (!buffer.exhausted()) {
            val n = buffer.read(block)
            if (n <= 0) break
            crc.update(block, 0, n)
            total += n
        }
        onBytes(read)
    }
    return crc.value to total
}

private fun FileHandle.crcOfRange(offset: Long, length: Long, onBytes: (Long) -> Unit = {}): Long {
    val source = LimitedSource(source(offset), length)
    try {
        return crcOfSource(source, length, onBytes).first
    } finally {
        source.close()
    }
}

private fun mergeRanges(ranges: List<LongRange>): List<LongRange> {
    val out = ArrayList<LongRange>()
    for (r in ranges.sortedBy { it.first }) {
        val last = out.lastOrNull()
        if (last != null && r.first <= last.last + 1) out[out.size - 1] = last.first..max(last.last, r.last)
        else out.add(r)
    }
    return out
}

private const val ZIP_END = 0x06054b50L
private const val ZIP_CENTRAL = 0x02014b50L
private const val ZIP_LOCAL = 0x04034b50L
private const val ZIP64_LOCATOR = 0x07064b50L
private const val ZIP64_END = 0x06064b50L
private const val ZIP_MAX_DIRECTORY = 128L * 1024 * 1024
private const val ZIP_INFLATE_BUDGET = 8L * 1024 * 1024 * 1024

private class ZipEntry(
    val offset: Long,
    val compressedSize: Long,
    val size: Long,
    val crc: Long,
    val method: Int,
    val flags: Int
)

internal fun checkZip(
    handle: FileHandle,
    onBytes: (Long) -> Unit = {},
    inflateBudget: Long = ZIP_INFLATE_BUDGET
): ArchiveReport {
    val size = handle.size()
    fun bad(problem: String, ranges: List<LongRange> = emptyList()) = ArchiveReport("zip", problem, ranges)
    if (size < 22) return bad("The file is too short to be a ZIP archive")
    val tailLength = min(size, 22L + 65535L).toInt()
    val tailStart = size - tailLength
    val tailRange = listOf(tailStart..size - 1)
    val tail = handle.bytesAt(tailStart, tailLength) ?: return bad("The end of the archive cannot be read")
    var end = -1
    var at = tailLength - 22
    while (at >= 0) {
        if (u32(tail, at) == ZIP_END && at + 22 + u16(tail, at + 20) <= tailLength) {
            end = at
            break
        }
        at--
    }
    if (end < 0) return bad("The end-of-central-directory record is missing", tailRange)

    val endPosition = tailStart + end
    val disk = u16(tail, end + 4)
    val directoryDisk = u16(tail, end + 6)
    var entryCount = u16(tail, end + 10).toLong()
    var directorySize = u32(tail, end + 12)
    var directoryOffset = u32(tail, end + 16)
    var directoryEnd = endPosition
    val zip64 = disk == 0xFFFF || directoryDisk == 0xFFFF || entryCount == 0xFFFFL ||
            directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL
    if (zip64) {
        val locator = handle.bytesAt(endPosition - 20, 20)
        if (locator == null || u32(locator, 0) != ZIP64_LOCATOR) return bad("The ZIP64 locator is missing", tailRange)
        val recordPosition = u64(locator, 8)
        val record = handle.bytesAt(recordPosition, 56)
        if (record == null || u32(record, 0) != ZIP64_END) return bad("The ZIP64 end record is missing", tailRange)
        if (u32(record, 16) != 0L || u32(record, 20) != 0L) return ArchiveReport("zip", null)
        entryCount = u64(record, 32)
        directorySize = u64(record, 40)
        directoryOffset = u64(record, 48)
        directoryEnd = recordPosition
    } else if (disk != 0 || directoryDisk != 0) {
        return ArchiveReport("zip", null)
    }
    if (entryCount < 0 || directorySize < 0 || directoryOffset < 0) return bad("The central directory is damaged", tailRange)
    val shift = directoryEnd - directorySize - directoryOffset
    if (shift < 0 || directorySize > directoryEnd) return bad("The central directory does not fit in the file", tailRange)
    if (directorySize > ZIP_MAX_DIRECTORY) return ArchiveReport("zip", null)
    val directoryStart = directoryEnd - directorySize
    val directoryRange = if (directorySize > 0) listOf(directoryStart..directoryEnd - 1) else tailRange
    val directory = handle.bytesAt(directoryStart, directorySize.toInt())
        ?: return bad("The central directory cannot be read", directoryRange)

    val entries = ArrayList<ZipEntry>()
    var p = 0
    while (p + 46 <= directory.size) {
        if (u32(directory, p) != ZIP_CENTRAL) break
        val flags = u16(directory, p + 8)
        val method = u16(directory, p + 10)
        val crc = u32(directory, p + 16)
        var compressed = u32(directory, p + 20)
        var plain = u32(directory, p + 24)
        val nameLength = u16(directory, p + 28)
        val extraLength = u16(directory, p + 30)
        val commentLength = u16(directory, p + 32)
        var offset = u32(directory, p + 42)
        val next = p + 46 + nameLength + extraLength + commentLength
        if (next > directory.size) return bad("The central directory is damaged", directoryRange)
        if (plain == 0xFFFFFFFFL || compressed == 0xFFFFFFFFL || offset == 0xFFFFFFFFL) {
            var e = p + 46 + nameLength
            val extraEnd = e + extraLength
            while (e + 4 <= extraEnd) {
                val id = u16(directory, e)
                val length = u16(directory, e + 2)
                if (id == 1) {
                    var q = e + 4
                    val fieldEnd = min(q + length, extraEnd)
                    if (plain == 0xFFFFFFFFL && q + 8 <= fieldEnd) {
                        plain = u64(directory, q); q += 8
                    }
                    if (compressed == 0xFFFFFFFFL && q + 8 <= fieldEnd) {
                        compressed = u64(directory, q); q += 8
                    }
                    if (offset == 0xFFFFFFFFL && q + 8 <= fieldEnd) {
                        offset = u64(directory, q)
                    }
                    break
                }
                e += 4 + length
            }
        }
        if (compressed < 0 || plain < 0 || offset < 0) return bad("The central directory is damaged", directoryRange)
        entries.add(ZipEntry(offset, compressed, plain, crc, method, flags))
        p = next
    }
    val expectedCount = if (zip64) entryCount else entryCount and 0xFFFFL
    val foundCount = if (zip64) entries.size.toLong() else entries.size.toLong() and 0xFFFFL
    if (foundCount != expectedCount) return bad("The central directory is damaged", directoryRange)

    val sorted = entries.sortedBy { it.offset }
    val damaged = ArrayList<LongRange>()
    var crcChecked = 0
    var inflated = 0L
    var processed = 0L
    val progress: (Long) -> Unit = { n -> processed += n; onBytes(processed) }
    for ((index, entry) in sorted.withIndex()) {
        val start = shift + entry.offset
        var limit = directoryStart
        for (later in index + 1 until sorted.size) {
            if (sorted[later].offset > entry.offset) {
                limit = shift + sorted[later].offset
                break
            }
        }
        val extent = start..max(start, min(limit, size) - 1)
        if (start < 0 || start + 30 > directoryStart) {
            damaged.add(max(0L, min(start, size - 1))..max(0L, min(limit, size) - 1))
            continue
        }
        val local = handle.bytesAt(start, 30)
        if (local == null || u32(local, 0) != ZIP_LOCAL) {
            damaged.add(extent)
            continue
        }
        val dataStart = start + 30 + u16(local, 26) + u16(local, 28)
        val dataEnd = dataStart + entry.compressedSize
        if (dataEnd > limit || dataEnd > size) {
            damaged.add(extent)
            continue
        }
        val encrypted = entry.flags and 1 != 0
        val canCheck = !encrypted && (entry.method == 0 || entry.method == 8) && inflated + entry.size <= inflateBudget
        if (!canCheck) {
            progress(entry.compressedSize)
            continue
        }
        if (entry.method == 0 && entry.compressedSize != entry.size) {
            damaged.add(extent)
            continue
        }
        val before = processed
        val raw: Source = LimitedSource(handle.source(dataStart), entry.compressedSize)
        val result = try {
            if (entry.method == 0) crcOfSource(raw, entry.size, progress)
            else {
                val inflating = InflaterSource(raw, Inflater(true))
                try {
                    crcOfSource(inflating, entry.size) { onBytes(processed) }
                } finally {
                    runCatching { inflating.close() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            runCatching { raw.close() }
        }
        processed = before
        progress(entry.compressedSize)
        inflated += entry.size
        crcChecked++
        if (result == null || result.first != entry.crc || result.second != entry.size) damaged.add(extent)
    }
    val merged = mergeRanges(damaged)
    return ArchiveReport(
        "zip",
        if (merged.isEmpty()) null else "${damaged.size} of ${entries.size} files in the archive are damaged",
        merged,
        entries.size,
        crcChecked
    )
}

private val SEVEN_ZIP_SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
private const val SEVEN_ZIP_MAX_HEADER = 64L * 1024 * 1024

internal fun check7z(handle: FileHandle): ArchiveReport {
    val size = handle.size()
    fun bad(problem: String, ranges: List<LongRange> = emptyList()) = ArchiveReport("7z", problem, ranges)
    val head = handle.bytesAt(0, 32) ?: return bad("The file is too short to be a 7z archive")
    val headRange = listOf(0L..31L)
    for (i in SEVEN_ZIP_SIGNATURE.indices) {
        if (head[i] != SEVEN_ZIP_SIGNATURE[i]) return bad("The 7z signature is missing", headRange)
    }
    val startCrc = Crc32().also { it.update(head, 12, 20) }.value
    if (startCrc != u32(head, 8)) return bad("The 7z start header is damaged", headRange)
    val nextOffset = u64(head, 12)
    val nextSize = u64(head, 20)
    val nextCrc = u32(head, 28)
    if (nextOffset < 0 || nextSize < 0 || nextOffset > size || nextSize > size || 32 + nextOffset + nextSize > size) {
        return bad("The 7z header lies outside the file")
    }
    if (nextSize == 0L || nextSize > SEVEN_ZIP_MAX_HEADER) return ArchiveReport("7z", null)
    val headerStart = 32 + nextOffset
    if (handle.crcOfRange(headerStart, nextSize) != nextCrc) {
        return bad("The 7z header is damaged", listOf(headerStart..headerStart + nextSize - 1))
    }
    return ArchiveReport("7z", null)
}

private val RAR_SIGNATURE = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)
private const val RAR5_MAX_HEADER = 2L * 1024 * 1024

internal fun checkRar(handle: FileHandle): ArchiveReport {
    val size = handle.size()
    fun bad(problem: String, ranges: List<LongRange> = emptyList()) = ArchiveReport("rar", problem, ranges)
    val head = handle.bytesAt(0, 8) ?: return bad("The file is too short to be a RAR archive")
    for (i in RAR_SIGNATURE.indices) {
        if (head[i] != RAR_SIGNATURE[i]) return bad("The RAR signature is missing", listOf(0L..7L))
    }
    if (head[6].toInt() == 0) {
        val main = handle.bytesAt(7, 7) ?: return bad("The RAR main header is missing")
        val headerSize = u16(main, 5)
        if ((main[2].toInt() and 0xFF) != 0x73 || headerSize < 13 || 7L + headerSize > size) {
            return bad("The RAR main header is damaged", listOf(0L..min(size - 1, 19L)))
        }
        return ArchiveReport("rar", null)
    }
    if (head[6].toInt() != 1 || head[7].toInt() != 0) return bad("The RAR signature is missing", listOf(0L..7L))
    val probeLength = min(size - 8, 14L).toInt()
    val probe = handle.bytesAt(8, probeLength)
    if (probe == null || probeLength < 6) return bad("The RAR main header is missing")
    val storedCrc = u32(probe, 0)
    var headerSize = 0L
    var sizeBytes = 0
    while (true) {
        if (4 + sizeBytes >= probe.size || sizeBytes >= 5) return bad("The RAR main header is damaged", listOf(0L..min(size - 1, 21L)))
        val b = probe[4 + sizeBytes].toInt() and 0xFF
        headerSize = headerSize or ((b and 0x7F).toLong() shl (7 * sizeBytes))
        sizeBytes++
        if (b and 0x80 == 0) break
    }
    val bodyLength = sizeBytes + headerSize
    val headerRange = listOf(0L..min(size - 1, 12L + bodyLength - 1))
    if (headerSize == 0L || headerSize > RAR5_MAX_HEADER || 12L + bodyLength > size) {
        return bad("The RAR main header is damaged", listOf(0L..min(size - 1, 21L)))
    }
    val body = handle.bytesAt(12, bodyLength.toInt()) ?: return bad("The RAR main header cannot be read", headerRange)
    val crc = Crc32().also { it.update(body) }.value
    if (crc != storedCrc) return bad("The RAR main header is damaged", headerRange)
    return ArchiveReport("rar", null)
}
