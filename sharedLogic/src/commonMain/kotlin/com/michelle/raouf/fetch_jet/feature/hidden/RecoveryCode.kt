package com.michelle.raouf.fetch_jet.feature.hidden

import okio.ByteString.Companion.toByteString

internal object RecoveryCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val SALT = "fetchjet-recovery-1"
    const val KEY_BYTES = 32
    private const val BODY_CHARS = 52
    private const val CHECK_CHARS = 4

    fun encode(key: ByteArray): String {
        require(key.size == KEY_BYTES)
        return (toBase32(key) + checkOf(key)).chunked(4).joinToString("-")
    }

    fun decode(text: String): ByteArray? {
        val clean = StringBuilder()
        for (c in text.uppercase()) {
            when {
                c == '-' || c.isWhitespace() -> Unit
                c == 'O' -> clean.append('0')
                c == 'I' || c == 'L' -> clean.append('1')
                else -> clean.append(c)
            }
        }
        if (clean.length != BODY_CHARS + CHECK_CHARS) return null
        val body = clean.substring(0, BODY_CHARS)
        val key = fromBase32(body) ?: return null
        return if (checkOf(key) == clean.substring(BODY_CHARS)) key else null
    }

    private fun toBase32(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    private fun fromBase32(text: String): ByteArray? {
        val out = ByteArray(KEY_BYTES)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in text) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                if (index >= KEY_BYTES) return null
                out[index++] = ((buffer shr (bits - 8)) and 0xff).toByte()
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        if (index != KEY_BYTES || buffer != 0) return null
        return out
    }

    private fun checkOf(key: ByteArray): String {
        val digest = (SALT.encodeToByteArray() + key).toByteString().sha256().toByteArray()
        val bits = ((digest[0].toInt() and 0xff) shl 12) or ((digest[1].toInt() and 0xff) shl 4) or ((digest[2].toInt() and 0xff) shr 4)
        val out = StringBuilder()
        for (i in CHECK_CHARS - 1 downTo 0) out.append(ALPHABET[(bits shr (i * 5)) and 31])
        return out.toString()
    }
}
