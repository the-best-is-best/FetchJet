package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlin.test.Test
import kotlin.test.assertEquals

class FailedWriteAccessTest {
    private fun task(id: String, status: DownloadStatus, error: String?) = DownloadTask(
        id = id, url = "http://x/$id", fileName = "$id.bin", destinationPath = "/storage/emulated/0/Folder",
        engineType = EngineType.HTTP_DIRECT, status = status, errorMessage = error
    )

    @Test
    fun onlyFailedTasksWithAFolderWriteErrorAreChosen() {
        val tasks = listOf(
            task("a", DownloadStatus.ERROR, "open failed: EACCES (Permission denied)"),
            task("b", DownloadStatus.ERROR, "HTTP Error 404: Not Found"),
            task("c", DownloadStatus.DOWNLOADING, "Permission denied"),
            task("d", DownloadStatus.ERROR, "failed to create folder"),
            task("e", DownloadStatus.ERROR, null)
        )
        assertEquals(listOf("a", "d"), failedForWriteAccess(tasks, emptySet()).map { it.id })
    }

    @Test
    fun aTaskAlreadyAskedAboutIsNotChosenAgain() {
        val tasks = listOf(task("a", DownloadStatus.ERROR, "Permission denied"))
        assertEquals(emptyList(), failedForWriteAccess(tasks, setOf("a")).map { it.id })
    }
}
