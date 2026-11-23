package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistHelpersTest {
    @Test
    fun oldestFirstIsForChannelVideosOnly() {
        assertTrue(isChannelVideosUrl("https://www.youtube.com/@name"))
        assertTrue(isChannelVideosUrl("https://www.youtube.com/@name/videos"))
        assertTrue(isChannelVideosUrl("https://www.youtube.com/channel/UC123/videos"))
        assertTrue(isChannelVideosUrl("https://youtube.com/c/name"))
        assertFalse(isChannelVideosUrl("https://www.youtube.com/playlist?list=PL123"))
        assertFalse(isChannelVideosUrl("https://www.youtube.com/@name/shorts"))
        assertFalse(isChannelVideosUrl("https://soundcloud.com/x/sets/y"))
    }

    @Test
    fun languagesAreMergedOncePerCode() {
        val a = listOf(SubtitleTrack("en", "English (auto)", "vtt", "u1", isAuto = true))
        val b = listOf(SubtitleTrack("en", "English", "vtt", "u2"), SubtitleTrack("ar", "Arabic", "vtt", "u3"))
        val merged = mergeSubtitleLanguages(mergeSubtitleLanguages(emptyList(), a).map { it.copy(isSelected = true) }, b)
        assertEquals(listOf("ar", "en"), merged.map { it.langCode }.sorted())
        val en = merged.first { it.langCode == "en" }
        assertFalse(en.isAuto)
        assertTrue(en.isSelected)
        assertEquals(listOf("ar"), b.forLanguages(listOf("ar")).map { it.langCode })
        assertTrue(b.forLanguages(listOf("ar")).all { it.isSelected })
    }

    @Test
    fun aYouTubeEntryWithNoTitleAndNoDurationIsUnavailable() {
        val json = """{"_type":"playlist","title":"L","entries":[""" +
                """{"id":"aaaaaaaaaaa","title":"One","url":"https://www.youtube.com/watch?v=aaaaaaaaaaa","duration":10,"ie_key":"Youtube"},""" +
                """{"id":"yi50KlsCBio","url":"https://www.youtube.com/watch?v=yi50KlsCBio","ie_key":"Youtube"}]}"""
        val list = parsePlaylistJson(json)!!
        assertFalse(list.entries[0].unavailable)
        assertTrue(list.entries[1].unavailable)
        assertFalse(list.entries[1].isSelected)
    }
}
