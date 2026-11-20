package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoFormatChoiceTest {
    @Test
    fun theDefaultKeepsTheSmallerCodecAndTheBestSound() {
        assertTrue(playsWiderThan("av01.0.00M.08", "avc1.4D400C"))
        assertTrue(playsWiderThan("vp9", "avc1.4D400C"))
        assertFalse(playsWiderThan("avc1.4D400C", "av01.0.00M.08"))
        assertFalse(playsWiderThan("av01.0.00M.08", "vp9"))
        assertEquals("133+bestaudio/133+bestaudio[ext=m4a]/133/best", videoWithAudioSelector("133"))
        assertEquals("bestvideo[height<=240][protocol^=http]+bestaudio[protocol^=http]/best[height<=240][protocol^=http]", httpsFallbackSelector(240, false))
        assertEquals("240p (Low - MP4) · SD", com.michelle.raouf.fetch_jet.domain.model.withQualityTag("240p (Low - MP4)"))
    }
}
