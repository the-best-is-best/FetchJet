package com.michelle.raouf.fetch_jet.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MiniJsonTest {

    @Test
    fun keepsTheValueTypesCallersExpect() {
        @Suppress("UNCHECKED_CAST")
        val root = MiniJson.parse(
            """{"title":"A \"b\" é","duration":212,"tbr":1234.5,"live":false,"x":null,"formats":[{"format_id":"137","filesize":712445280}]}"""
        ) as Map<String, Any?>
        assertEquals("A \"b\" é", root.getString("title"))
        assertEquals(212L, root["duration"])
        assertEquals(1234.5, root.getDouble("tbr"))
        assertEquals(false, root["live"])
        assertNull(root["x"])
        assertEquals(712445280L, root.getObjectArray("formats").single().getLong("filesize"))
    }

    @Test
    fun readsTheJsonLineWhenOtherLinesSurroundIt() {
        @Suppress("UNCHECKED_CAST")
        val root = MiniJson.parse("WARNING: something\n{\"id\":\"x\"}\n") as Map<String, Any?>
        assertEquals("x", root.getString("id"))
    }

    @Test
    fun readsTheBridgeRequests() {
        @Suppress("UNCHECKED_CAST")
        val list =
            MiniJson.parse("""[{"url":"https://a/b.zip","fileName":"b.zip"},{"url":"magnet:?xt=1"}]""") as List<Map<String, Any?>>
        assertEquals(listOf("https://a/b.zip", "magnet:?xt=1"), list.map { it.getString("url") })
    }

    @Test
    fun rejectsText() {
        assertFailsWith<Exception> { MiniJson.parse("not json at all {") }
    }
}
