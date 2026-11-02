package com.michelle.raouf.fetch_jet.presentation.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtractLinksTest {

    @Test
    fun findsEveryLinkInPastedText() {
        val text = """
            Lesson 1: https://example.com/files/a.zip
            Lesson 2 (https://example.com/files/b.pdf), and the video https://www.youtube.com/watch?v=jNQXAC9IVRw.
            magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel
            https://example.com/files/a.zip
        """
        assertEquals(
            listOf(
                "https://example.com/files/a.zip",
                "https://example.com/files/b.pdf",
                "https://www.youtube.com/watch?v=jNQXAC9IVRw",
                "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel"
            ),
            extractLinks(text)
        )
    }

    @Test
    fun oneLinkIsNotAList() {
        assertEquals(1, extractLinks("https://example.com/a.zip").size)
        assertEquals(0, extractLinks("just text").size)
    }
}
