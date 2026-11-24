package com.michelle.raouf.fetch_jet.feature.media_player

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackQueueTest {
    private fun task(
        id: String,
        name: String,
        playlist: String = "",
        status: DownloadStatus = DownloadStatus.COMPLETED
    ) = DownloadTask(
        id = id,
        url = "http://x/$name",
        fileName = name,
        destinationPath = "/d",
        engineType = EngineType.HTTP_DIRECT,
        status = status,
        playlistId = playlist
    )

    @Test
    fun playlistFilesFormTheQueueInOrder() {
        val all = listOf(
            task("1", "a.mp4", "p"),
            task("2", "b.mp3", "p"),
            task("3", "c.zip", "p"),
            task("4", "d.mp4", "p", DownloadStatus.DOWNLOADING),
            task("5", "e.mp4", "other")
        )
        val q = MediaPlayback.queueFor(all[1], all)
        assertEquals(listOf("1", "2"), q.items.map { it.taskId })
        assertEquals(1, q.index)
        assertEquals("2/2", q.position)
        assertFalse(q.hasNext)
        assertTrue(q.hasPrevious)
    }

    @Test
    fun aFileWithoutPlaylistPlaysAlone() {
        val all = listOf(task("1", "a.mp4"), task("2", "b.mp4"))
        val q = MediaPlayback.queueFor(all[0], all)
        assertEquals(1, q.items.size)
        assertEquals("/d/a.mp4", q.current.path)
        assertTrue(q.current.isVideo)
    }

    @Test
    fun groupQueueStartsAtFirstThenAtLastPlayed() {
        PlayerProgress.resetForTest(java.io.File.createTempFile("player-progress", ".json").also { it.delete() }.path)
        val all = listOf(task("1", "a.mp4"), task("2", "b.mp3"), task("3", "c.zip"), task("4", "d.mp4"))
        assertEquals(null, MediaPlayback.queueForGroup("g", listOf(task("9", "x.zip"))))
        assertEquals(0, MediaPlayback.queueForGroup("g", all)!!.index)
        PlayerProgress.remember("g", "4")
        val q = MediaPlayback.queueForGroup("g", all)!!
        assertEquals(listOf("1", "2", "4"), q.items.map { it.taskId })
        assertEquals(2, q.index)
        assertEquals("g", q.groupKey)
        PlayerProgress.clear("g")
        assertEquals(0, MediaPlayback.queueForGroup("g", all)!!.index)
        PlayerProgress.resetForTest(null)
    }

    @Test
    fun timeAndSpeedLabels() {
        assertEquals("1:05", MediaPlayback.formatTime(65_000))
        assertEquals("1:01:01", MediaPlayback.formatTime(3_661_000))
        assertEquals("2x", MediaPlayback.speedLabel(2f))
        assertEquals("1.5x", MediaPlayback.speedLabel(1.5f))
    }
}
