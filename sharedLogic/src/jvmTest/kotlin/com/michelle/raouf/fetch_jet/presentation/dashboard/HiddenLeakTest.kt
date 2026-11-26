package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.hidden.logName
import com.michelle.raouf.fetch_jet.feature.hidden.shownName
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenLeakTest {
    private fun task(id: String, playlist: String = "", hidden: Boolean = false) = DownloadTask(
        id = id, url = "http://x/$id", fileName = "secret-$id.mp4", destinationPath = "/tmp",
        engineType = EngineType.HTTP_DIRECT, playlistId = playlist, hidden = hidden
    )

    private fun record(id: String) = PlaylistRecord(id = id, title = "Title $id", url = "http://x/list/$id")

    @Test
    fun hiddenPlaylistsStayOutOfTheStateWhileLocked() {
        val records = mapOf("open" to record("open"), "secret" to record("secret"), "mixed" to record("mixed"))
        val tasks = listOf(
            task("a", "open"),
            task("b", "secret", hidden = true),
            task("c", "mixed", hidden = true),
            task("d", "mixed")
        )
        val locked = shownPlaylists(records, tasks, unlocked = false)
        assertEquals(setOf("open", "mixed"), locked.keys)
        assertEquals(records, shownPlaylists(records, tasks, unlocked = true))
        assertEquals(records, shownPlaylists(records, tasks.filter { !it.hidden }, unlocked = false))
    }

    @Test
    fun hiddenTasksHaveNoNameInLogsOrMessages() {
        val hidden = task("h", hidden = true)
        assertFalse(hidden.logName().contains("secret"))
        assertFalse(hidden.shownName().contains("secret"))
        assertTrue(hidden.logName().contains("h"))
        assertEquals("secret-v.mp4", task("v").shownName())
        assertEquals("secret-v.mp4", task("v").logName())
    }
}
