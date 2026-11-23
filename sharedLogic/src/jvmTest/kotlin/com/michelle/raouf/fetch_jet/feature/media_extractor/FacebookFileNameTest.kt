package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FacebookFileNameTest {
    private val cdn = "https://video.fcai23-1.fna.fbcdn.net/o1/v/t2/f2/m367/AQOZ2iNkgD4-Vo_v5k2P-K0_QgkzzSpQxnJB68c4qEq3L75q.mp4?_nc_cat=103"
    private val code = "AQOZ2iNkgD4-Vo_v5k2P-K0_QgkzzSpQxnJB68c4qEq3L75q.mp4"
    private val at = 1795125600000L - localUtcOffsetMs()

    @Test
    fun storyReelAndVideo() {
        assertEquals("Facebook story 2026-11-19 22-00.mp4", facebookFileName(cdn, "https://m.facebook.com/stories/1/x/", code, at))
        assertEquals("Facebook reel 2026-11-19 22-00.mp4", facebookFileName(cdn, "https://m.facebook.com/reel/114/", code, at))
        assertEquals("Facebook video 2026-11-19 22-00.mp4", facebookFileName(cdn, "https://m.facebook.com/story.php?id=1".replace("story.php", "watch"), "", at))
    }

    @Test
    fun keepsRealNamesAndOtherSites() {
        assertNull(facebookFileName(cdn, "", "My trip.mp4", at))
        assertNull(facebookFileName("https://example.com/AQOZ2iNkgD4-Vo_v5k2P-K0_QgkzzSpQxnJB68.mp4", "", code, at))
    }

    @Test
    fun sameFileLinkIgnoresSignedQueryOnly() {
        val a = "https://video.fbcdn.net/o1/v/AQOZ.mp4?oh=1&oe=2"
        val b = "https://video.fbcdn.net/o1/v/AQOZ.mp4?oh=9&oe=8"
        kotlin.test.assertTrue(com.michelle.raouf.fetch_jet.presentation.dashboard.sameFileLink(a, b))
        kotlin.test.assertFalse(com.michelle.raouf.fetch_jet.presentation.dashboard.sameFileLink("https://x.com/download.php?id=5", "https://x.com/download.php?id=6"))
    }
}
