package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TempCleanupTest {
    @Test
    fun aDirectDownloadTakesOnlyItsOwnTempFiles() {
        val engine = EngineType.HTTP_DIRECT
        assertTrue(ownsTempItem("a.bin", "a.bin", "a", engine))
        assertTrue(ownsTempItem("a.bin.part3", "a.bin", "a", engine))
        assertFalse(ownsTempItem("abc.zip.part", "a.bin", "a", engine))
        assertFalse(ownsTempItem("a.zip.part", "a.bin", "a", engine))
        assertFalse(ownsTempItem("a.binary", "a.bin", "a", engine))
    }

    @Test
    fun aNameWithNoStemDoesNotTakeEverything() {
        assertFalse(ownsTempItem("movie.mp4.part", ".bin", "", EngineType.HTTP_DIRECT))
        assertFalse(ownsTempItem("movie.mp4.part", ".bin", "", EngineType.MEDIA_STREAM))
        assertFalse(ownsTempItem("anything", "", "", EngineType.MEDIA_STREAM))
    }

    @Test
    fun aMediaDownloadAlsoTakesYtDlpPieces() {
        val engine = EngineType.MEDIA_STREAM
        assertTrue(ownsTempItem("clip.f137.mp4", "clip.mp4", "clip", engine))
        assertTrue(ownsTempItem("clip.mp4.ytdl", "clip.mp4", "clip", engine))
        assertFalse(ownsTempItem("clipper.f137.mp4", "clip.mp4", "clip", engine))
    }
}
