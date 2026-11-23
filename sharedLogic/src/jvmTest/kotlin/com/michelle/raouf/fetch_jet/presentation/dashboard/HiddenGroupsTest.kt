package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HiddenGroupsTest {
    private fun task(id: String, dir: String, playlist: String = "") =
        DownloadTask(
            id = id, url = "https://example.com/$id", fileName = "$id.bin", destinationPath = dir,
            engineType = EngineType.HTTP_DIRECT, status = DownloadStatus.COMPLETED, progress = 1f, playlistId = playlist, hidden = true
        )

    private val records = mapOf("p1" to PlaylistRecord(id = "p1", title = "List"))
    private val hiddenTasks = listOf(
        task("v1", "/d/List", "p1"), task("a", "/d"), task("v2", "/d/List", "p1"), task("b", "/d"), task("c", "/d/Music")
    )

    private fun state(byFolder: Boolean, unlocked: Boolean = true) = DashboardUiState(
        playlists = records,
        groupByFolder = byFolder,
        hidden = HiddenUiState(unlocked = unlocked, tasks = hiddenTasks)
    )

    @Test
    fun hiddenTasksGroupLikeTheMainList() {
        assertEquals(listOf("pl:p1", "a", "b", "c"), state(false).hiddenItems().map { it.key })
        assertEquals(listOf("pl:p1", "dir:/d", "dir:/d/Music"), state(true).hiddenItems().map { it.key })
    }

    @Test
    fun aFolderGroupUsesTheOriginalFolderAndOnlyHiddenFiles() {
        val st = state(true).openHiddenFolder("/d")
        assertEquals(listOf("a", "b"), st.hiddenOpenFolder()!!.tasks.map { it.id })
        assertEquals("/d", st.hiddenOpenFolder()!!.path)
        assertNull(st.hiddenOpenPlaylist())
    }

    @Test
    fun openingOneGroupClosesTheOtherAndNeverTouchesTheMainList() {
        val st = state(true).openHiddenPlaylist("p1")
        assertEquals(listOf("v1", "v2"), st.hiddenOpenPlaylist()!!.tasks.map { it.id })
        assertNull(st.openPlaylistId)
        assertNull(st.openFolder)
        val folder = st.openHiddenFolder("/d")
        assertNull(folder.hiddenOpenPlaylist())
        assertEquals(2, folder.hiddenOpenFolder()!!.total)
        val closed = folder.closeHiddenGroup()
        assertNull(closed.hiddenOpenFolder())
        assertNull(closed.hidden.openFolder)
    }

    @Test
    fun aMissingOrLockedGroupDoesNotOpen() {
        assertNull(state(true).openHiddenPlaylist("nope").hidden.openPlaylistId)
        assertNull(state(true).openHiddenFolder("/nowhere").hidden.openFolder)
        assertNull(state(true, unlocked = false).openHiddenPlaylist("p1").hidden.openPlaylistId)
    }

    @Test
    fun groupTasksAndRowKeysExpandToTheHiddenOnes() {
        val st = state(true)
        assertEquals(listOf("v1", "v2"), st.hiddenGroupTasks("p1", "").map { it.id })
        assertEquals(listOf("a", "b"), st.hiddenGroupTasks("", "/d").map { it.id })
        assertTrue(st.hiddenGroupTasks("", "").isEmpty())
        assertEquals(listOf("v1", "v2", "c"), st.hiddenTaskIdsOfRows(listOf("pl:p1", "c")))
    }
}
