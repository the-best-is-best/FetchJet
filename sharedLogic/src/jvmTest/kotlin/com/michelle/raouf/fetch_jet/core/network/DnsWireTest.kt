package com.michelle.raouf.fetch_jet.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DnsWireTest {
    private fun answerFor(query: ByteArray, records: List<Pair<Int, ByteArray>>, rcode: Int = 0): ByteArray {
        var end = 12
        while (query[end].toInt() != 0) end += 1 + query[end]
        end += 5
        val out = ArrayList<Byte>()
        out.add(query[0]); out.add(query[1])
        out.add(0x81.toByte()); out.add((0x80 or rcode).toByte())
        out.addAll(listOf(0, 1, 0, records.size.toByte(), 0, 0, 0, 0))
        for (i in 12 until end) out.add(query[i])
        for ((type, data) in records) {
            out.addAll(listOf(0xC0.toByte(), 12, 0, type.toByte(), 0, 1, 0, 0, 0x0E, 0x10, 0, data.size.toByte()))
            data.forEach { out.add(it) }
        }
        return out.toByteArray()
    }

    @Test
    fun aQuestionHasTheNameInLabels() {
        val q = DnsWire.query(0x1234, "files.example.com", DnsWire.TYPE_A)
        assertEquals(0x12, q[0].toInt())
        assertEquals(0x34, q[1].toInt())
        assertEquals(5, q[12].toInt())
        assertEquals("files", q.copyOfRange(13, 18).decodeToString())
    }

    @Test
    fun aAndAaaaAreRead() {
        val q = DnsWire.query(7, "example.com", DnsWire.TYPE_A)
        val a = DnsWire.parse(answerFor(q, listOf(DnsWire.TYPE_A to byteArrayOf(93, 0, 2, 1))), 7)
        assertEquals(listOf("93.0.2.1"), a.addresses)
        assertEquals(3600, a.ttlSeconds)
        val v6 = ByteArray(16).also { it[0] = 0x20; it[1] = 0x01; it[15] = 1 }
        val b = DnsWire.parse(answerFor(q, listOf(DnsWire.TYPE_AAAA to v6)), 7)
        assertEquals(listOf("2001:0:0:0:0:0:0:1"), b.addresses)
    }

    @Test
    fun noSuchNameGivesNothingAndOtherErrorsThrow() {
        val q = DnsWire.query(9, "nothing.example", DnsWire.TYPE_A)
        assertTrue(DnsWire.parse(answerFor(q, emptyList(), rcode = 3), 9).addresses.isEmpty())
        assertFailsWith<IllegalArgumentException> { DnsWire.parse(answerFor(q, emptyList(), rcode = 2), 9) }
        assertFailsWith<IllegalArgumentException> { DnsWire.parse(answerFor(q, emptyList()), 10) }
        assertFailsWith<IllegalArgumentException> { DnsWire.parse(byteArrayOf(1, 2, 3)) }
    }
}
