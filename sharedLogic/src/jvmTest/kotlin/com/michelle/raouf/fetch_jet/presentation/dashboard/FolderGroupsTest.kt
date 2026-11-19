package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderGroupsTest {
    private fun task(id: String, dir: String, playlist: String = "", status: DownloadStatus = DownloadStatus.COMPLETED) =
        DownloadTask(
            id = id, url = "https://example.com/$id", fileName = "$id.bin", destinationPath = dir,
            engineType = EngineType.HTTP_DIRECT, status = status, progress = 1f, playlistId = playlist
        )

    @Test
    fun aFolderKeyIgnoresSlashStyleAndTheLastSlash() {
        assertEquals("C:/Users/me/Downloads", folderKey("C:\\Users\\me\\Downloads\\"))
        assertEquals("/storage/emulated/0/Download", folderKey("/storage/emulated/0/Download/"))
    }

    @Test
    fun foldersSharingANameShowMoreOfThePath() {
        val labels = folderLabels(listOf("/d/Work/Video", "/d/Home/Video", "/d/Music"))
        assertEquals("Work/Video", labels["/d/Work/Video"])
        assertEquals("Home/Video", labels["/d/Home/Video"])
        assertEquals("Music", labels["/d/Music"])
    }

    @Test
    fun byFolderEachFolderIsOneRowAndPlaylistsStayTheirOwn() {
        val records = mapOf("p1" to PlaylistRecord(id = "p1", title = "List"))
        val all = listOf(
            task("a", "/d"), task("v1", "/d/List", "p1"), task("b", "/d/Music"), task("c", "/d"),
            task("v2", "/d/List", "p1"), task("o", "/d/Old", "gone")
        )
        val items = groupDownloads(all, all, records, byFolder = true)
        assertEquals(listOf("dir:/d", "pl:p1", "dir:/d/Music", "dir:/d/Old"), items.map { it.key })
        val root = (items[0] as TaskListItem.Folder).group
        assertEquals(2, root.total)
        assertEquals("d", root.label)
    }

    @Test
    fun offItIsThePlaylistGroupingOnly() {
        val all = listOf(task("a", "/d"), task("b", "/d"))
        assertEquals(listOf("a", "b"), groupDownloads(all, all, emptyMap(), byFolder = false).map { it.key })
    }

    @Test
    fun aFolderSumsAllItsDownloadsAndIsGoneWhenEmpty() {
        val all = listOf(task("a", "/d", status = DownloadStatus.PAUSED), task("b", "/d"))
        val group = (groupDownloads(all.take(1), all, emptyMap(), byFolder = true).single() as TaskListItem.Folder).group
        assertEquals(2, group.total)
        assertEquals(1, group.done)
        assertTrue(group.canResume)
        assertNull(folderGroup(emptyList(), emptyMap(), "/d"))
        assertEquals("dir:/d", FOLDER_ROW_PREFIX + group.folder)
        assertEquals("/d", folderOfRow("dir:/d"))
    }
}
