package com.michelle.raouf.fetch_jet.feature.archive_extract

import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout

class ArchiveException(message: String) : Exception(message)

private const val FAST_BITS = 10

private class Huffman(lengths: IntArray, symbols: Int) {
    val count = IntArray(16)
    val symbol = IntArray(symbols)
    val fast = IntArray(1 shl FAST_BITS)

    init {
        for (i in 0 until symbols) count[lengths[i]]++
        count[0] = 0
        val offsets = IntArray(17)
        for (len in 1..15) offsets[len + 1] = offsets[len] + count[len]
        val next = IntArray(16)
        var code = 0
        for (len in 1..15) {
            code = (code + count[len - 1]) shl 1
            next[len] = code
        }
        for (sym in 0 until symbols) {
            val len = lengths[sym]
            if (len == 0) continue
            symbol[offsets[len]++] = sym
            val c = next[len]++
            if (len <= FAST_BITS) {
                var reversed = 0
                var v = c
                repeat(len) {
                    reversed = (reversed shl 1) or (v and 1)
                    v = v ushr 1
                }
                var at = reversed
                val entry = (len shl 16) or sym
                while (at < (1 shl FAST_BITS)) {
                    fast[at] = entry
                    at += 1 shl len
                }
            }
        }
    }
}

private val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
private val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

private val FIXED_LITERALS: Huffman by lazy {
    val lengths = IntArray(288) { if (it < 144) 8 else if (it < 256) 9 else if (it < 280) 7 else 8 }
    Huffman(lengths, 288)
}
private val FIXED_DISTANCES: Huffman by lazy { Huffman(IntArray(30) { 5 }, 30) }

/** Raw DEFLATE (RFC 1951) over [upstream]. The bytes after the stream stay readable through [leftover]. */
internal class InflateSource(private val upstream: BufferedSource) : Source {
    private var bitBuffer = 0L
    private var bitCount = 0
    private val window = ByteArray(32768)
    private var position = 0L
    private var finalBlock = false
    private var inBlock = false
    private var storedLeft = 0
    private var literals: Huffman = FIXED_LITERALS
    private var distances: Huffman = FIXED_DISTANCES
    private var finished = false
    private val chunk = ByteArray(65536)

    val isFinished: Boolean get() = finished

    private fun fill(bits: Int): Boolean {
        while (bitCount < bits) {
            if (!upstream.request(1)) return false
            bitBuffer = bitBuffer or ((upstream.readByte().toLong() and 0xFF) shl bitCount)
            bitCount += 8
        }
        return true
    }

    private fun need(bits: Int): Int {
        if (!fill(bits)) throw ArchiveException("the compressed data ends too early")
        val value = (bitBuffer and ((1L shl bits) - 1)).toInt()
        bitBuffer = bitBuffer ushr bits
        bitCount -= bits
        return value
    }

    private fun decode(h: Huffman): Int {
        fill(15)
        val entry = h.fast[(bitBuffer and ((1 shl FAST_BITS) - 1).toLong()).toInt()]
        if (entry != 0) {
            val len = entry ushr 16
            if (len > bitCount) throw ArchiveException("the compressed data ends too early")
            bitBuffer = bitBuffer ushr len
            bitCount -= len
            return entry and 0xFFFF
        }
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..15) {
            if (bitCount == 0) throw ArchiveException("the compressed data ends too early")
            code = code or (bitBuffer and 1L).toInt()
            bitBuffer = bitBuffer ushr 1
            bitCount--
            val count = h.count[len]
            if (code - count < first) return h.symbol[index + (code - first)]
            index += count
            first += count
            first = first shl 1
            code = code shl 1
        }
        throw ArchiveException("the compressed data is damaged")
    }

    private fun readDynamic() {
        val hlit = need(5) + 257
        val hdist = need(5) + 1
        val hclen = need(4) + 4
        if (hlit > 286 || hdist > 30) throw ArchiveException("the compressed data is damaged")
        val lengths = IntArray(320)
        for (i in 0 until hclen) lengths[CODE_LENGTH_ORDER[i]] = need(3)
        val codeLengths = Huffman(lengths.copyOf(19), 19)
        val all = IntArray(hlit + hdist)
        var i = 0
        while (i < hlit + hdist) {
            val sym = decode(codeLengths)
            if (sym < 16) {
                all[i++] = sym
            } else {
                var repeat: Int
                var value = 0
                when (sym) {
                    16 -> {
                        if (i == 0) throw ArchiveException("the compressed data is damaged")
                        value = all[i - 1]
                        repeat = 3 + need(2)
                    }

                    17 -> repeat = 3 + need(3)
                    else -> repeat = 11 + need(7)
                }
                if (i + repeat > hlit + hdist) throw ArchiveException("the compressed data is damaged")
                while (repeat-- > 0) all[i++] = value
            }
        }
        if (all[256] == 0) throw ArchiveException("the compressed data is damaged")
        literals = Huffman(all.copyOfRange(0, hlit), hlit)
        distances = Huffman(all.copyOfRange(hlit, hlit + hdist), hdist)
    }

    private fun nextBlock() {
        finalBlock = need(1) == 1
        when (need(2)) {
            0 -> {
                bitBuffer = bitBuffer ushr (bitCount and 7)
                bitCount -= bitCount and 7
                val len = need(16)
                val inverse = need(16)
                if (len != (inverse.inv() and 0xFFFF)) throw ArchiveException("the compressed data is damaged")
                storedLeft = len
                inBlock = true
                literals = FIXED_LITERALS
                distances = FIXED_DISTANCES
                stored = true
            }

            1 -> {
                literals = FIXED_LITERALS
                distances = FIXED_DISTANCES
                inBlock = true
                stored = false
            }

            2 -> {
                readDynamic()
                inBlock = true
                stored = false
            }

            else -> throw ArchiveException("the compressed data is damaged")
        }
    }

    private var stored = false

    private fun put(out: ByteArray, at: Int, value: Int) {
        out[at] = value.toByte()
        window[(position and 32767).toInt()] = value.toByte()
        position++
    }

    private fun produce(): Int {
        var n = 0
        while (n < chunk.size - 300) {
            if (!inBlock) {
                if (finalBlock) {
                    finished = true
                    break
                }
                nextBlock()
                continue
            }
            if (stored) {
                if (storedLeft == 0) {
                    inBlock = false
                    continue
                }
                val room = minOf(storedLeft, chunk.size - 300 - n)
                for (k in 0 until room) put(chunk, n++, need(8))
                storedLeft -= room
                continue
            }
            val sym = decode(literals)
            if (sym < 256) {
                put(chunk, n++, sym)
            } else if (sym == 256) {
                inBlock = false
            } else {
                val li = sym - 257
                if (li >= 29) throw ArchiveException("the compressed data is damaged")
                val length = LENGTH_BASE[li] + need(LENGTH_EXTRA[li])
                val di = decode(distances)
                if (di >= 30) throw ArchiveException("the compressed data is damaged")
                val distance = DIST_BASE[di] + need(DIST_EXTRA[di])
                if (distance > position) throw ArchiveException("the compressed data is damaged")
                repeat(length) {
                    put(chunk, n++, window[((position - distance) and 32767).toInt()].toInt() and 0xFF)
                }
            }
        }
        return n
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (finished) return -1
        val n = produce()
        if (n == 0) return -1
        sink.write(chunk, 0, n)
        return n.toLong()
    }

    /** Whole bytes that were read ahead but are not part of the deflate stream. Call after the stream ended. */
    fun leftover(): ByteArray {
        val extra = bitCount / 8
        val bytes = ByteArray(extra)
        var buffer = bitBuffer ushr (bitCount % 8)
        for (i in 0 until extra) {
            bytes[i] = (buffer and 0xFF).toByte()
            buffer = buffer ushr 8
        }
        bitBuffer = 0
        bitCount = 0
        return bytes
    }

    override fun timeout(): Timeout = upstream.timeout()

    override fun close() {
        upstream.close()
    }
}
