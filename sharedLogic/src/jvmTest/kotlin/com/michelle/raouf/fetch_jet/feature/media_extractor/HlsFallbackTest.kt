package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HlsFallbackTest {
    @Test
    fun selectorKeepsThePictureSize() {
        assertEquals("best[height<=480][protocol^=m3u8]", hlsFallbackSelector(480))
        assertEquals("best[protocol^=m3u8]", hlsFallbackSelector(0))
    }

    @Test
    fun heightComesFromTheStreamOrTheChosenQuality() {
        assertEquals(480, chosenHeight(listOf(1080, 480), "x", ""))
        assertEquals(480, chosenHeight(listOf(0), "480p-2000k", ""))
        assertEquals(720, chosenHeight(emptyList(), null, "https://cdn.example/v/720P_4000K_1.mp4?a=1080P_"))
        assertEquals(0, chosenHeight(emptyList(), "best", "https://cdn.example/v.mp4"))
    }

    @Test
    fun plainStreamKeepsItsHeight() {
        val json = """{"title":"t","duration":935,"format_id":"480p-2000k","protocol":"https","ext":"mp4","height":480,"vcodec":"avc1","acodec":"mp4a","url":"https://cdn.example/480P_2000K.mp4?validto=1"}"""
        val media = assertNotNull(DirectMediaResolver.parse(json))
        assertEquals(480, media.streams.single().height)
    }

    @Test
    fun hlsFormatGivesThePlaylist() {
        val json = """{"title":"t","duration":935,"format_id":"hls-968","protocol":"m3u8_native","ext":"mp4","height":480,"url":"https://cdn.example/hls/480P_2000K.mp4/index-v1-a1.m3u8"}"""
        val media = assertNotNull(DirectMediaResolver.parse(json))
        assertEquals("https://cdn.example/hls/480P_2000K.mp4/index-v1-a1.m3u8", media.hlsUrl)
    }
}
