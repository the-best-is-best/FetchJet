package com.michelle.raouf.fetch_jet.feature.torrent_engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorrentStopRatioTest {
    private val mb = 1024L * 1024L

    @Test
    fun neverStopsWhenTheRatioIsOff() {
        assertFalse(shouldStopSharing(500 * mb, 100 * mb, 0f, true))
        assertFalse(shouldStopSharing(500 * mb, 100 * mb, -1f, true))
        assertFalse(shouldStopSharing(500 * mb, 100 * mb, Float.NaN, true))
    }

    @Test
    fun neverStopsATorrentThatIsStillDownloading() {
        assertFalse(shouldStopSharing(500 * mb, 100 * mb, 1f, false))
    }

    @Test
    fun stopsAtTheRatioAndAbove() {
        assertFalse(shouldStopSharing(100 * mb - 1, 100 * mb, 1f, true))
        assertTrue(shouldStopSharing(100 * mb, 100 * mb, 1f, true))
        assertTrue(shouldStopSharing(101 * mb, 100 * mb, 1f, true))
        assertFalse(shouldStopSharing(149 * mb, 100 * mb, 1.5f, true))
        assertTrue(shouldStopSharing(150 * mb, 100 * mb, 1.5f, true))
    }

    @Test
    fun aRatioBelowOneIsExact() {
        assertFalse(shouldStopSharing(9, 100, 0.1f, true))
        assertTrue(shouldStopSharing(10, 100, 0.1f, true))
        assertTrue(shouldStopSharing(70, 100, 0.7f, true))
        assertFalse(shouldStopSharing(69, 100, 0.7f, true))
    }

    @Test
    fun nothingUploadedOrNothingWantedNeverStops() {
        assertFalse(shouldStopSharing(0, 100 * mb, 0.001f, true))
        assertFalse(shouldStopSharing(10 * mb, 0, 1f, true))
    }

    @Test
    fun bigTorrentsDoNotOverflow() {
        val wanted = 4_000_000_000_000L
        assertFalse(shouldStopSharing(wanted * 2 - 1, wanted, 2f, true))
        assertTrue(shouldStopSharing(wanted * 2, wanted, 2f, true))
        assertTrue(shouldStopSharing(wanted * 100, wanted, 100f, true))
    }
}
