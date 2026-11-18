package com.michelle.raouf.fetch_jet.feature.archive_extract

internal class Crc32 {
    private var value = -1

    fun update(bytes: ByteArray, offset: Int, count: Int) {
        var c = value
        var i = offset
        val end = offset + count
        while (i < end) {
            c = TABLE[(c xor bytes[i].toInt()) and 0xFF] xor (c ushr 8)
            i++
        }
        value = c
    }

    fun result(): Long = (value.inv()).toLong() and 0xFFFFFFFFL

    private companion object {
        val TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor -0x12477ce0 else c ushr 1 }
            c
        }
    }
}
