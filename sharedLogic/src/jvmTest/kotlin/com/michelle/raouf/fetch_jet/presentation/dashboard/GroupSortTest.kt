package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_player.MediaPlayback
import com.michelle.raouf.fetch_jet.feature.media_player.PlayerProgress
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupSortTest {
    private fun task(id: String, name: String, created: Long, size: Long = 0L, status: DownloadStatus = DownloadStatus.COMPLETED) =
        DownloadTask(
            id = id, url = "https://x/$name", fileName = name, destinationPath = "/d",
            engineType = EngineType.HTTP_DIRECT, status = status, totalBytes = size, createdAtEpochMs = created
        )

    private val added = listOf(task("b", "b.mp4", 200, 30), task("a", "a.mp4", 100, 10), task("c", "c.mp4", 300, 20))

    @Test
    fun aGroupPageFollowsTheChosenSort() {
        assertEquals(listOf("c", "b", "a"), sortTasks(added, TaskSortOption.DATE_DESC).map { it.id })
        assertEquals(listOf("a", "b", "c"), sortTasks(added, TaskSortOption.DATE_ASC).map { it.id })
        assertEquals(listOf("b", "c", "a"), sortTasks(added, TaskSortOption.SIZE_DESC).map { it.id })
        assertEquals(listOf("a", "b", "c"), sortTasks(added, TaskSortOption.NAME_ASC).map { it.id })
    }

    @Test
    fun unfinishedStayOnTop() {
        val list = added + task("d", "d.mp4", 50, status = DownloadStatus.PAUSED)
        assertEquals("d", sortTasks(list, TaskSortOption.DATE_ASC).first().id)
    }

    @Test
    fun groupPlayStartsAtTheTopOfTheShownOrder() {
        PlayerProgress.resetForTest(java.io.File.createTempFile("group-sort", ".json").also { it.delete() }.path)
        val queue = MediaPlayback.queueForGroup("g", sortTasks(added, TaskSortOption.DATE_ASC))!!
        assertEquals(listOf("a", "b", "c"), queue.items.map { it.taskId })
        assertEquals("a", queue.current.taskId)
    }
}
