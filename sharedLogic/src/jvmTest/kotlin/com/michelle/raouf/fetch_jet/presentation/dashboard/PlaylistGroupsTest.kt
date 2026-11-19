package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistGroupsTest {
    private fun task(id: String, playlist: String = "", status: DownloadStatus = DownloadStatus.PAUSED, progress: Float = 0f) =
        DownloadTask(
            id = id, url = "https://example.com/$id", fileName = "$id.mp4", destinationPath = "/d",
            engineType = EngineType.MEDIA_STREAM, status = status, progress = progress, playlistId = playlist
        )

    @Test
    fun aSavedPlaylistIsOneRowWhereItsFirstVideoIs() {
        val records = mapOf("p1" to PlaylistRecord(id = "p1", title = "List"))
        val all = listOf(task("a"), task("v1", "p1"), task("b"), task("v2", "p1"), task("o", "gone"))
        val items = groupPlaylists(all, all, records)
        assertEquals(listOf("a", "pl:p1", "b", "o"), items.map { it.key })
        val group = (items[1] as TaskListItem.Group).group
        assertEquals(2, group.total)
    }

    @Test
    fun theGroupSumsAllItsVideosEvenWhenOnlySomeAreShown() {
        val records = mapOf("p1" to PlaylistRecord(id = "p1", title = "List"))
        val all = listOf(
            task("v1", "p1", DownloadStatus.COMPLETED, 1f),
            task("v2", "p1", DownloadStatus.PAUSED, 0.5f),
            task("v3", "p1", DownloadStatus.ERROR)
        )
        val group = (groupPlaylists(all.take(1), all, records).single() as TaskListItem.Group).group
        assertEquals(3, group.total)
        assertEquals(1, group.done)
        assertEquals(1, group.failed)
        assertEquals(0.5f, group.progress)
        assertEquals(PlaylistGroupStatus.PAUSED, group.status)
        assertTrue(group.canResume)
    }

    @Test
    fun storeFormatReadsBackAndSkipsBrokenEntries() {
        val record = PlaylistRecord(id = "p1", title = "List", url = "https://x/list", folder = "/d/List",
            quality = "P720", subtitleLanguages = listOf("ar", "en"), embedSubtitles = true)
        val text = PlaylistStore.encode(listOf(record))
        val withBroken = text.replaceFirst("[", "[{\"id\":5,\"title\":[]},")
        assertEquals(listOf(record), PlaylistStore.decode(withBroken))
    }
}
