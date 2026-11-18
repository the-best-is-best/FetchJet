package com.michelle.raouf.fetch_jet.core.network

class DnsAnswer(val addresses: List<String>, val ttlSeconds: Long)

object DnsWire {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    fun query(id: Int, host: String, type: Int): ByteArray {
        val out = ArrayList<Byte>()
        fun u16(v: Int) {
            out.add((v shr 8 and 0xFF).toByte()); out.add((v and 0xFF).toByte())
        }
        u16(id and 0xFFFF)
        u16(0x0100)
        u16(1); u16(0); u16(0); u16(0)
        for (label in host.trimEnd('.').split('.')) {
            val bytes = label.encodeToByteArray()
            require(bytes.size in 1..63) { "bad name" }
            out.add(bytes.size.toByte())
            bytes.forEach { out.add(it) }
        }
        out.add(0)
        u16(type)
        u16(1)
        return out.toByteArray()
    }

    fun parse(message: ByteArray, id: Int? = null): DnsAnswer {
        fun u8(at: Int): Int = message[at].toInt() and 0xFF
        fun u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)
        fun u32(at: Int): Long = (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        if (message.size < 12) throw IllegalArgumentException("short DNS answer")
        if (id != null && u16(0) != (id and 0xFFFF)) throw IllegalArgumentException("DNS answer for another question")
        val rcode = u16(2) and 0x000F
        if (rcode != 0 && rcode != 3) throw IllegalArgumentException("DNS error $rcode")
        val questions = u16(4)
        val answers = u16(6)

        fun skipName(start: Int): Int {
            var at = start
            while (true) {
                val len = u8(at)
                when {
                    len == 0 -> return at + 1
                    len and 0xC0 == 0xC0 -> return at + 2
                    else -> at += 1 + len
                }
            }
        }

        var at = 12
        repeat(questions) { at = skipName(at) + 4 }
        val found = ArrayList<String>()
        var ttl = Long.MAX_VALUE
        repeat(answers) {
            at = skipName(at)
            val type = u16(at)
            val recordTtl = u32(at + 4)
            val length = u16(at + 8)
            val data = at + 10
            if (data + length > message.size) throw IllegalArgumentException("cut DNS answer")
            when {
                type == TYPE_A && length == 4 -> {
                    found.add((0 until 4).joinToString(".") { u8(data + it).toString() })
                    ttl = minOf(ttl, recordTtl)
                }

                type == TYPE_AAAA && length == 16 -> {
                    found.add((0 until 8).joinToString(":") { u16(data + it * 2).toString(16) })
                    ttl = minOf(ttl, recordTtl)
                }
            }
            at = data + length
        }
        return DnsAnswer(found, if (ttl == Long.MAX_VALUE) 0 else ttl)
    }
}
