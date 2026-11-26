package com.michelle.raouf.fetch_jet.feature.hidden

internal object ChaCha20 {
    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val BLOCK_SIZE = 64

    private const val C0 = 0x61707865
    private const val C1 = 0x3320646e
    private const val C2 = 0x79622d32
    private const val C3 = 0x6b206574

    private fun le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or
                ((b[o + 1].toInt() and 0xff) shl 8) or
                ((b[o + 2].toInt() and 0xff) shl 16) or
                ((b[o + 3].toInt() and 0xff) shl 24)

    private fun putLe(v: Int, b: ByteArray, o: Int) {
        b[o] = v.toByte()
        b[o + 1] = (v ushr 8).toByte()
        b[o + 2] = (v ushr 16).toByte()
        b[o + 3] = (v ushr 24).toByte()
    }

    private fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        var xa = x[a]
        var xb = x[b]
        var xc = x[c]
        var xd = x[d]
        xa += xb; xd = (xd xor xa).rotateLeft(16)
        xc += xd; xb = (xb xor xc).rotateLeft(12)
        xa += xb; xd = (xd xor xa).rotateLeft(8)
        xc += xd; xb = (xb xor xc).rotateLeft(7)
        x[a] = xa
        x[b] = xb
        x[c] = xc
        x[d] = xd
    }

    fun xor(
        key: ByteArray,
        nonce: ByteArray,
        counter: Int,
        src: ByteArray,
        srcOff: Int,
        dst: ByteArray,
        dstOff: Int,
        len: Int
    ) {
        require(key.size == KEY_SIZE) { "key must be 32 bytes" }
        require(nonce.size == NONCE_SIZE) { "nonce must be 12 bytes" }
        require(srcOff >= 0 && dstOff >= 0 && len >= 0 && srcOff + len <= src.size && dstOff + len <= dst.size)
        val init = IntArray(16)
        init[0] = C0; init[1] = C1; init[2] = C2; init[3] = C3
        for (i in 0 until 8) init[4 + i] = le(key, i * 4)
        init[13] = le(nonce, 0)
        init[14] = le(nonce, 4)
        init[15] = le(nonce, 8)
        val x = IntArray(16)
        var ctr = counter
        var pos = 0
        while (pos < len) {
            init[12] = ctr
            for (i in 0 until 16) x[i] = init[i]
            for (round in 0 until 10) {
                quarter(x, 0, 4, 8, 12)
                quarter(x, 1, 5, 9, 13)
                quarter(x, 2, 6, 10, 14)
                quarter(x, 3, 7, 11, 15)
                quarter(x, 0, 5, 10, 15)
                quarter(x, 1, 6, 11, 12)
                quarter(x, 2, 7, 8, 13)
                quarter(x, 3, 4, 9, 14)
            }
            val n = if (len - pos < BLOCK_SIZE) len - pos else BLOCK_SIZE
            if (n == BLOCK_SIZE) {
                for (i in 0 until 16) {
                    val o = pos + i * 4
                    putLe(le(src, srcOff + o) xor (x[i] + init[i]), dst, dstOff + o)
                }
            } else {
                val ks = ByteArray(BLOCK_SIZE)
                for (i in 0 until 16) putLe(x[i] + init[i], ks, i * 4)
                for (i in 0 until n) dst[dstOff + pos + i] = (src[srcOff + pos + i].toInt() xor ks[i].toInt()).toByte()
            }
            ctr++
            pos += BLOCK_SIZE
        }
    }
}
