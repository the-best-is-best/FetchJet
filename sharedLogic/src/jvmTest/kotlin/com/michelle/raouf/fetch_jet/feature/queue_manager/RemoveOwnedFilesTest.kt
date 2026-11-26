package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoveOwnedFilesTest {
    private fun task(status: DownloadStatus, engine: EngineType = EngineType.HTTP_DIRECT, total: Long = 100L, done: Long = 0L) =
        DownloadTask("t", "https://x.test/a.bin", "a.bin", "/d", engine, status = status, totalBytes = total, downloadedBytes = done)

    @Test
    fun onlyAFinishedTaskDeletesItsFile() {
        assertTrue(ownsDestinationFile(task(DownloadStatus.COMPLETED), 100L))
        for (s in DownloadStatus.entries.filter { it != DownloadStatus.COMPLETED }) {
            assertFalse(ownsDestinationFile(task(s), 100L), s.name)
        }
        assertFalse(ownsDestinationFile(task(DownloadStatus.COMPLETED), null))
    }

    @Test
    fun aFinishedDirectDownloadWithAnotherSizeIsNotOurs() {
        assertFalse(ownsDestinationFile(task(DownloadStatus.COMPLETED), 42L))
        assertTrue(ownsDestinationFile(task(DownloadStatus.COMPLETED, total = 0L), 42L))
        assertTrue(ownsDestinationFile(task(DownloadStatus.COMPLETED, EngineType.MEDIA_STREAM), 42L))
    }

    @Test
    fun aTorrentThatNeverWroteKeepsTheFolder() {
        assertFalse(torrentWroteFiles(task(DownloadStatus.IDLE, EngineType.BITTORRENT), false))
        assertTrue(torrentWroteFiles(task(DownloadStatus.PAUSED, EngineType.BITTORRENT, done = 5L), false))
        assertTrue(torrentWroteFiles(task(DownloadStatus.ERROR, EngineType.BITTORRENT), true))
        assertTrue(torrentWroteFiles(task(DownloadStatus.COMPLETED, EngineType.BITTORRENT), false))
    }
}
