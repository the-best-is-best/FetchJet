package com.michelle.raouf.fetch_jet.feature.archive_extract

import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout

/** A .gz file (RFC 1952), one or several members, with the CRC32 and size of each checked. */
internal class GzipSource(private val upstream: BufferedSource) : Source {
    private var inflater: InflateSource? = null
    private var crc = Crc32()
    private var size = 0L
    private var done = false

    private fun startMember(): Boolean {
        if (!upstream.request(1)) return false
        if (!upstream.request(10)) throw ArchiveException("this is not a gzip file")
        val head = upstream.readByteArray(10)
        if ((head[0].toInt() and 0xFF) != 0x1F || (head[1].toInt() and 0xFF) != 0x8B || head[2].toInt() != 8) {
            throw ArchiveException("this is not a gzip file")
        }
        val flags = head[3].toInt() and 0xFF
        if (flags and 4 != 0) {
            if (!upstream.request(2)) throw ArchiveException("the compressed data ends too early")
            val len = (upstream.readByte().toInt() and 0xFF) or ((upstream.readByte().toInt() and 0xFF) shl 8)
            upstream.skip(len.toLong())
        }
        if (flags and 8 != 0) skipZeroTerminated()
        if (flags and 16 != 0) skipZeroTerminated()
        if (flags and 2 != 0) upstream.skip(2)
        inflater = InflateSource(upstream)
        crc = Crc32()
        size = 0
        return true
    }

    private fun skipZeroTerminated() {
        while (true) {
            if (!upstream.request(1)) throw ArchiveException("the compressed data ends too early")
            if (upstream.readByte().toInt() == 0) return
        }
    }

    private fun endMember() {
        val inf = inflater!!
        val left = inf.leftover()
        val trailer = ByteArray(8)
        val fromLeft = minOf(left.size, 8)
        left.copyInto(trailer, 0, 0, fromLeft)
        if (fromLeft < 8) {
            if (!upstream.request((8 - fromLeft).toLong())) throw ArchiveException("the compressed data ends too early")
            upstream.readByteArray((8 - fromLeft).toLong()).copyInto(trailer, fromLeft)
        } else if (left.size > 8) {
            throw ArchiveException("the compressed data is damaged")
        }
        val expectedCrc = u32(trailer, 0)
        val expectedSize = u32(trailer, 4)
        if (crc.result() != expectedCrc || (size and 0xFFFFFFFFL) != expectedSize) {
            throw ArchiveException("the file is damaged (checksum mismatch)")
        }
        inflater = null
    }

    private fun u32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

    override fun read(sink: Buffer, byteCount: Long): Long {
        while (true) {
            if (done) return -1
            if (inflater == null && !startMember()) {
                done = true
                return -1
            }
            val staging = Buffer()
            val n = inflater!!.read(staging, byteCount)
            if (n < 0) {
                endMember()
                continue
            }
            val bytes = staging.readByteArray()
            crc.update(bytes, 0, bytes.size)
            size += bytes.size
            sink.write(bytes)
            return bytes.size.toLong()
        }
    }

    override fun timeout(): Timeout = upstream.timeout()

    override fun close() {
        upstream.close()
    }
}
