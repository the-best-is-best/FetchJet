package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.tbib.kmp_localization.LocalizationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipTimeTest {

    @Test
    fun parsesFractions() {
        assertEquals(12_500L, ClipTime.parseMs("12.500"))
        assertEquals(30_500L, ClipTime.parseMs("00:00:30.5"))
        assertEquals(3_723_250L, ClipTime.parseMs("1:02:03.25"))
        assertEquals(90_000L, ClipTime.parseMs("1:30"))
        assertEquals(12_345L, ClipTime.parseMs("00:00:12,345"))
        assertEquals(12_345L, ClipTime.parseMs("12.3456"))
        assertEquals(0L, ClipTime.parseMs("00:00"))
        assertEquals(30.5, ClipTime.parseSeconds("00:00:30.5"))
        assertEquals(30L, ClipTime.wholeSeconds("00:00:30.5"))
    }

    @Test
    fun rejectsBadInput() {
        for (bad in listOf("", " ", "abc", "1:2:3:4", "00:61", "00:60:00", "1..2", "-5", "1:-2", "::", ".", "1:", "١٢")) {
            assertNull(ClipTime.parseMs(bad), bad)
        }
        assertEquals(0L, ClipTime.wholeSeconds("abc"))
    }

    @Test
    fun formats() {
        assertEquals("00:04:37", ClipTime.format(277))
        assertEquals("00:00:00", ClipTime.format(-3))
        assertEquals("00:00:30.500", ClipTime.formatMs(30_500))
        assertEquals("01:00:00", ClipTime.formatMs(3_600_000))
        assertEquals("00:00:30.500", ClipTime.forYtDlp("30.5"))
    }

    @Test
    fun fractionalStartIsASubClip() {
        val c = checkClipRange(true, "00:00:30.5", "", 120)
        assertTrue(c.valid)
        assertTrue(c.isSubClip)
        assertEquals(30_500L, c.startMs)
        assertTrue(c.endAtEnd)
    }

    @Test
    fun endAtDurationIsAtEndNotAnError() {
        val exact = checkClipRange(true, "00:00:10", "00:02:00", 120)
        assertTrue(exact.valid)
        assertTrue(exact.endAtEnd)
        val slightlyPast = checkClipRange(true, "00:00:10", "00:02:00.800", 120)
        assertTrue(slightlyPast.valid)
        assertTrue(slightlyPast.endAtEnd)
        val past = checkClipRange(true, "00:00:10", "00:02:05", 120)
        assertFalse(past.valid)
        assertTrue(past.endExceeds)
        assertFalse(past.isSubClip)
    }

    @Test
    fun fullRangeIsNotASubClip() {
        val c = checkClipRange(true, "00:00", "00:02:00", 120)
        assertTrue(c.valid)
        assertFalse(c.isSubClip)
        assertFalse(checkClipRange(false, "00:00:10", "00:00:20", 120).isSubClip)
    }

    @Test
    fun startAfterEndAndFractionsBelowOneSecond() {
        assertTrue(checkClipRange(true, "00:00:20", "00:00:10", 120).startAfterEnd)
        val tight = checkClipRange(true, "00:00:12", "00:00:12.500", 120)
        assertTrue(tight.valid)
        assertEquals(12L, tight.maxStartSec)
        assertEquals(0L, checkClipRange(true, "", "00:00:00.900", 120).maxStartSec)
    }

    @Test
    fun startPastTheEndAndBadText() {
        val c = checkClipRange(true, "00:02:00", "", 120)
        assertTrue(c.startExceeds)
        assertFalse(c.valid)
        val bad = checkClipRange(true, "1:2:3:4", "", 120)
        assertTrue(bad.startInvalid)
        assertFalse(bad.valid)
        assertTrue(checkClipRange(false, "1:2:3:4", "", 120).valid)
    }

    @Test
    fun messages() {
        LocalizationManager.start()
        assertNull(checkClipRange(true, "00:00:10", "", 120).message)
        assertNotNull(checkClipRange(true, "00:00:20", "00:00:10", 120).message)
        assertNotNull(checkClipRange(true, "x", "", 120).message)
    }
}
