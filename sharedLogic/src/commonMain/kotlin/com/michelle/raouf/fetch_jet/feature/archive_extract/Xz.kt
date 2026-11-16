package com.michelle.raouf.fetch_jet.feature.archive_extract

import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout

private const val MAX_DICTIONARY = 128 * 1024 * 1024
private const val CHUNK_OUT = 1 shl 21

private class RangeDecoder(private val data: ByteArray, private var at: Int, private val end: Int) {
    private var range = 0xFFFFFFFFL
    private var code = 0L

    init {
        if (end - at < 5 || data[at].toInt() != 0) throw ArchiveException("the compressed data is damaged")
        at++
        repeat(4) { code = (code shl 8) or (data[at++].toLong() and 0xFF) }
        if (code == range) throw ArchiveException("the compressed data is damaged")
    }

    private fun normalize() {
        if (range < 0x1000000L) {
            if (at >= end) throw ArchiveException("the compressed data is damaged")
            range = (range shl 8) and 0xFFFFFFFFL
            code = ((code shl 8) or (data[at++].toLong() and 0xFF)) and 0xFFFFFFFFL
        }
    }

    fun bit(probs: ShortArray, index: Int): Int {
        val p = probs[index].toInt()
        val bound = (range ushr 11) * p
        val result: Int
        if (code < bound) {
            range = bound
            probs[index] = (p + ((2048 - p) ushr 5)).toShort()
            result = 0
        } else {
            range -= bound
            code -= bound
            probs[index] = (p - (p ushr 5)).toShort()
            result = 1
        }
        normalize()
        return result
    }

    fun direct(count: Int): Int {
        var result = 0
        repeat(count) {
            range = range ushr 1
            val b = if (code >= range) {
                code -= range
                1
            } else 0
            normalize()
            result = (result shl 1) or b
        }
        return result
    }

    fun tree(probs: ShortArray, base: Int, bits: Int): Int {
        var m = 1
        repeat(bits) { m = (m shl 1) or bit(probs, base + m) }
        return m - (1 shl bits)
    }

    fun reverseTree(probs: ShortArray, base: Int, bits: Int): Int {
        var m = 1
        var symbol = 0
        for (i in 0 until bits) {
            val b = bit(probs, base + m)
            m = (m shl 1) or b
            symbol = symbol or (b shl i)
        }
        return symbol
    }
}

private class LenCoder {
    val choice = ShortArray(2) { 1024 }
    val low = ShortArray(16 shl 3) { 1024 }
    val mid = ShortArray(16 shl 3) { 1024 }
    val high = ShortArray(256) { 1024 }

    fun reset() {
        choice.fill(1024)
        low.fill(1024)
        mid.fill(1024)
        high.fill(1024)
    }

    fun decode(rc: RangeDecoder, posState: Int): Int {
        if (rc.bit(choice, 0) == 0) return rc.tree(low, posState shl 3, 3)
        if (rc.bit(choice, 1) == 0) return 8 + rc.tree(mid, posState shl 3, 3)
        return 16 + rc.tree(high, 0, 8)
    }
}

private class Lzma2Decoder(dictionarySize: Long) {
    private val size: Int
    private val dict: ByteArray
    private var dictPos = 0
    private var dictFilled = 0L
    private var processed = 0L

    init {
        if (dictionarySize > MAX_DICTIONARY) throw ArchiveException("this xz file needs a dictionary that is too large")
        size = dictionarySize.toInt().coerceAtLeast(4096)
        dict = ByteArray(size)
    }

    private var lc = 0
    private var lp = 0
    private var pb = 0
    private var state = 0
    private var rep0 = 0
    private var rep1 = 0
    private var rep2 = 0
    private var rep3 = 0
    private var literal = ShortArray(0)
    private val isMatch = ShortArray(12 shl 4)
    private val isRep = ShortArray(12)
    private val isRepG0 = ShortArray(12)
    private val isRepG1 = ShortArray(12)
    private val isRepG2 = ShortArray(12)
    private val isRep0Long = ShortArray(12 shl 4)
    private val posSlot = ShortArray(4 shl 6)
    private val posSpecial = ShortArray(115)
    private val align = ShortArray(16)
    private val lenCoder = LenCoder()
    private val repLenCoder = LenCoder()
    private var haveProps = false
    private var needDictReset = true

    val output = ByteArray(CHUNK_OUT)
    var outCount = 0

    fun resetDictionary() {
        dictPos = 0
        dictFilled = 0
        processed = 0
        needDictReset = false
    }

    fun setProps(byte: Int) {
        if (byte >= 9 * 5 * 5) throw ArchiveException("the compressed data is damaged")
        lc = byte % 9
        val rest = byte / 9
        lp = rest % 5
        pb = rest / 5
        if (lc + lp > 4) throw ArchiveException("the compressed data is damaged")
        literal = ShortArray(0x300 shl (lc + lp))
        haveProps = true
    }

    fun resetState() {
        if (!haveProps) throw ArchiveException("the compressed data is damaged")
        literal.fill(1024)
        isMatch.fill(1024)
        isRep.fill(1024)
        isRepG0.fill(1024)
        isRepG1.fill(1024)
        isRepG2.fill(1024)
        isRep0Long.fill(1024)
        posSlot.fill(1024)
        posSpecial.fill(1024)
        align.fill(1024)
        lenCoder.reset()
        repLenCoder.reset()
        state = 0
        rep0 = 0
        rep1 = 0
        rep2 = 0
        rep3 = 0
    }

    private fun put(value: Int) {
        dict[dictPos] = value.toByte()
        dictPos++
        if (dictPos == size) dictPos = 0
        dictFilled++
        processed++
        output[outCount++] = value.toByte()
    }

    private fun back(distance: Int): Int {
        var p = dictPos - distance - 1
        if (p < 0) p += size
        return dict[p].toInt() and 0xFF
    }

    fun copyStored(data: ByteArray, from: Int, count: Int) {
        outCount = 0
        for (i in 0 until count) put(data[from + i].toInt() and 0xFF)
    }

    fun decodeChunk(data: ByteArray, from: Int, end: Int, unpacked: Int) {
        if (needDictReset) throw ArchiveException("the compressed data is damaged")
        outCount = 0
        val rc = RangeDecoder(data, from, end)
        val pbMask = (1 shl pb) - 1
        val lpMask = (1 shl lp) - 1
        while (outCount < unpacked) {
            val posState = (processed and pbMask.toLong()).toInt()
            if (rc.bit(isMatch, (state shl 4) + posState) == 0) {
                val prev = if (dictFilled > 0) back(0) else 0
                val litState = (((processed and lpMask.toLong()).toInt()) shl lc) + (prev ushr (8 - lc))
                val base = 0x300 * litState
                var symbol = 1
                if (state >= 7) {
                    if (rep0 >= dictFilled || rep0 >= size) throw ArchiveException("the compressed data is damaged")
                    var matchByte = back(rep0)
                    while (symbol < 0x100) {
                        val matchBit = (matchByte ushr 7) and 1
                        matchByte = matchByte shl 1
                        val b = rc.bit(literal, base + ((1 + matchBit) shl 8) + symbol)
                        symbol = (symbol shl 1) or b
                        if (matchBit != b) break
                    }
                }
                while (symbol < 0x100) symbol = (symbol shl 1) or rc.bit(literal, base + symbol)
                put(symbol and 0xFF)
                state = if (state < 4) 0 else if (state < 10) state - 3 else state - 6
                continue
            }
            val length: Int
            if (rc.bit(isRep, state) == 0) {
                rep3 = rep2
                rep2 = rep1
                rep1 = rep0
                length = lenCoder.decode(rc, posState)
                state = if (state < 7) 7 else 10
                val slot = rc.tree(posSlot, (if (length < 4) length else 3) shl 6, 6)
                if (slot < 4) {
                    rep0 = slot
                } else {
                    val direct = (slot ushr 1) - 1
                    var distance = (2 or (slot and 1)) shl direct
                    distance += if (slot < 14) {
                        rc.reverseTree(posSpecial, distance - slot, direct)
                    } else {
                        (rc.direct(direct - 4) shl 4) + rc.reverseTree(align, 0, 4)
                    }
                    rep0 = distance
                    if (rep0 == -1) throw ArchiveException("the compressed data is damaged")
                }
            } else {
                if (rc.bit(isRepG0, state) == 0) {
                    if (rc.bit(isRep0Long, (state shl 4) + posState) == 0) {
                        if (rep0 >= dictFilled || rep0 >= size) throw ArchiveException("the compressed data is damaged")
                        state = if (state < 7) 9 else 11
                        put(back(rep0))
                        continue
                    }
                } else {
                    val distance: Int
                    if (rc.bit(isRepG1, state) == 0) {
                        distance = rep1
                    } else {
                        if (rc.bit(isRepG2, state) == 0) {
                            distance = rep2
                        } else {
                            distance = rep3
                            rep3 = rep2
                        }
                        rep2 = rep1
                    }
                    rep1 = rep0
                    rep0 = distance
                }
                length = repLenCoder.decode(rc, posState)
                state = if (state < 7) 8 else 11
            }
            if (rep0 < 0 || rep0 >= dictFilled || rep0 >= size) throw ArchiveException("the compressed data is damaged")
            var left = length + 2
            if (outCount + left > unpacked) throw ArchiveException("the compressed data is damaged")
            while (left-- > 0) put(back(rep0))
        }
    }
}

/** An .xz file (LZMA2 only, as `xz` writes by default); the checksum of each block is checked for CRC32. */
internal class XzSource(private val upstream: BufferedSource) : Source {
    private var started = false
    private var finished = false
    private var checkType = 0
    private var decoder: Lzma2Decoder? = null
    private var crc = Crc32()
    private var compressedBytes = 0L
    private var headerBytes = 0L
    private var inBlock = false

    private fun u8(): Int {
        if (!upstream.request(1)) throw ArchiveException("the compressed data ends too early")
        return upstream.readByte().toInt() and 0xFF
    }

    private fun varint(counter: LongArray?): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val b = u8()
            counter?.set(0, counter[0] + 1)
            value = value or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
            if (shift > 56) throw ArchiveException("the compressed data is damaged")
        }
    }

    private fun checkSize(): Int = when (checkType) {
        0 -> 0
        1 -> 4
        4 -> 8
        10 -> 32
        else -> when (checkType) {
            2, 3 -> 4
            5, 6 -> 8
            7, 8, 9 -> 16
            11, 12 -> 32
            13, 14, 15 -> 64
            else -> 0
        }
    }

    private fun streamHeader(): Boolean {
        while (upstream.request(1) && upstream.buffer[0].toInt() == 0) {
            upstream.skip(1)
        }
        if (!upstream.request(1)) return false
        if (!upstream.request(12)) throw ArchiveException("this is not an xz file")
        val header = upstream.readByteArray(12)
        val magic = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
        for (i in 0 until 6) if (header[i] != magic[i]) throw ArchiveException("this is not an xz file")
        checkType = header[7].toInt() and 0x0F
        return true
    }

    private fun blockHeader(first: Int) {
        val size = (first + 1) * 4
        val rest = upstream.readByteArray((size - 1).toLong())
        val flags = rest[0].toInt() and 0xFF
        var at = 1
        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (true) {
                if (at >= rest.size - 4) throw ArchiveException("the compressed data is damaged")
                val b = rest[at++].toInt() and 0xFF
                value = value or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return value
                shift += 7
                if (shift > 56) throw ArchiveException("the compressed data is damaged")
            }
        }
        if (flags and 0x3C != 0) throw ArchiveException("this xz file uses a feature that is not supported")
        val filters = (flags and 3) + 1
        if (flags and 0x40 != 0) varint()
        if (flags and 0x80 != 0) varint()
        var dictionary = -1L
        repeat(filters) {
            val id = varint()
            val propsSize = varint().toInt()
            if (id != 0x21L || propsSize != 1 || filters != 1) {
                throw ArchiveException("this xz file uses a filter that is not supported")
            }
            val b = rest[at].toInt() and 0xFF
            at += propsSize
            dictionary = if (b > 40) throw ArchiveException("the compressed data is damaged")
            else if (b == 40) 0xFFFFFFFFL else (2L or (b and 1).toLong()) shl (b / 2 + 11)
        }
        val crcAt = rest.size - 4
        val expected = (rest[crcAt].toLong() and 0xFF) or ((rest[crcAt + 1].toLong() and 0xFF) shl 8) or
            ((rest[crcAt + 2].toLong() and 0xFF) shl 16) or ((rest[crcAt + 3].toLong() and 0xFF) shl 24)
        val check = Crc32()
        check.update(byteArrayOf(first.toByte()), 0, 1)
        check.update(rest, 0, crcAt)
        if (check.result() != expected) throw ArchiveException("the compressed data is damaged")
        decoder = Lzma2Decoder(dictionary)
        crc = Crc32()
        compressedBytes = 0
        headerBytes = size.toLong()
        inBlock = true
    }

    private fun endIndexAndFooter() {
        val counter = longArrayOf(1)
        val records = varint(counter)
        for (i in 0 until records) {
            varint(counter)
            varint(counter)
        }
        val pad = (4 - counter[0] % 4) % 4
        upstream.skip(pad)
        upstream.skip(4)
        if (!upstream.request(12)) throw ArchiveException("the compressed data ends too early")
        upstream.skip(12)
    }

    private fun endBlock() {
        val pad = (4 - compressedBytes % 4) % 4
        if (!upstream.request(pad + checkSize())) throw ArchiveException("the compressed data ends too early")
        upstream.skip(pad)
        if (checkType == 1) {
            val b = upstream.readByteArray(4)
            val expected = (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
            if (crc.result() != expected) throw ArchiveException("the file is damaged (checksum mismatch)")
        } else {
            upstream.skip(checkSize().toLong())
        }
        inBlock = false
        decoder = null
    }

    private fun nextChunk(sink: Buffer): Int {
        val d = decoder!!
        val control = u8()
        compressedBytes++
        if (control == 0) {
            endBlock()
            return 0
        }
        if (control == 1 || control == 2) {
            if (control == 1) d.resetDictionary()
            val hi = u8()
            val lo = u8()
            compressedBytes += 2
            val count = ((hi shl 8) or lo) + 1
            if (!upstream.request(count.toLong())) throw ArchiveException("the compressed data ends too early")
            val data = upstream.readByteArray(count.toLong())
            compressedBytes += count
            d.copyStored(data, 0, count)
        } else if (control >= 0x80) {
            val unpacked = (((control and 0x1F) shl 16) or (u8() shl 8) or u8()) + 1
            val packed = ((u8() shl 8) or u8()) + 1
            compressedBytes += 4
            val mode = (control ushr 5) and 3
            if (mode == 3) d.resetDictionary()
            if (mode >= 2) {
                d.setProps(u8())
                compressedBytes++
            }
            if (mode >= 1) d.resetState()
            if (!upstream.request(packed.toLong())) throw ArchiveException("the compressed data ends too early")
            val data = upstream.readByteArray(packed.toLong())
            compressedBytes += packed
            d.decodeChunk(data, 0, data.size, unpacked)
            if (d.outCount != unpacked) throw ArchiveException("the compressed data is damaged")
        } else {
            throw ArchiveException("the compressed data is damaged")
        }
        crc.update(d.output, 0, d.outCount)
        sink.write(d.output, 0, d.outCount)
        return d.outCount
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        while (true) {
            if (finished) return -1
            if (!started) {
                if (!streamHeader()) {
                    finished = true
                    return -1
                }
                started = true
            }
            if (!inBlock) {
                val first = u8()
                if (first == 0) {
                    endIndexAndFooter()
                    started = false
                    continue
                }
                blockHeader(first)
            }
            val n = nextChunk(sink)
            if (n > 0) return n.toLong()
        }
    }

    override fun timeout(): Timeout = upstream.timeout()

    override fun close() {
        upstream.close()
    }
}
