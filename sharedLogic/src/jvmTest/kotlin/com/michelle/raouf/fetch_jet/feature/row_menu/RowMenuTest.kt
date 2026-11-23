package com.michelle.raouf.fetch_jet.feature.row_menu

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RowMenuTest {
    private fun task(name: String, status: DownloadStatus, hidden: Boolean) = DownloadTask(
        id = "t", url = "http://x/$name", fileName = name, destinationPath = "/d",
        engineType = EngineType.HTTP_DIRECT, status = status, hidden = hidden
    )

    private val options = RowMenuOptions(selectEntry = true)

    @Test
    fun aHiddenFinishedTaskOffersOnlyWhatDoesNotTouchThePlainFile() {
        val entries = RowMenu.entries(task("a.zip", DownloadStatus.COMPLETED, true), options)
        assertEquals(
            listOf(RowMenuEntry.OPEN, RowMenuEntry.SELECT, RowMenuEntry.DETAILS, RowMenuEntry.UNHIDE, RowMenuEntry.DELETE),
            entries
        )
    }

    @Test
    fun aHiddenRunningOrPausedTaskKeepsPauseAndResume() {
        assertEquals(
            listOf(RowMenuEntry.PAUSE, RowMenuEntry.DETAILS, RowMenuEntry.UNHIDE, RowMenuEntry.DELETE),
            RowMenu.entries(task("a.mp4", DownloadStatus.DOWNLOADING, true))
        )
        assertEquals(
            listOf(RowMenuEntry.RESUME, RowMenuEntry.DETAILS, RowMenuEntry.UNHIDE, RowMenuEntry.DELETE),
            RowMenu.entries(task("a.mp4", DownloadStatus.PAUSED, true))
        )
    }

    @Test
    fun aHiddenTaskNeverOffersFolderExtractCompressSubtitlesSendOrLink() {
        val leaking = setOf(
            RowMenuEntry.OPEN_FOLDER, RowMenuEntry.EXTRACT, RowMenuEntry.COMPRESS,
            RowMenuEntry.SUBTITLES, RowMenuEntry.SEND_TO_DEVICE, RowMenuEntry.COPY_LINK
        )
        for (name in listOf("a.zip", "a.mp3", "a.mp4", "a.bin")) {
            for (status in DownloadStatus.entries) {
                assertTrue(RowMenu.entries(task(name, status, true), options).none { it in leaking }, "$name $status")
            }
        }
    }

    @Test
    fun unhideShowsOnlyForHiddenTasksAndComesBeforeDelete() {
        val plain = RowMenu.entries(task("a.zip", DownloadStatus.COMPLETED, false), options)
        assertFalse(RowMenuEntry.UNHIDE in plain)
        assertTrue(RowMenuEntry.OPEN_FOLDER in plain)
        val hidden = RowMenu.entries(task("a.zip", DownloadStatus.COMPLETED, true), options)
        assertEquals(hidden.indexOf(RowMenuEntry.DELETE) - 1, hidden.indexOf(RowMenuEntry.UNHIDE))
    }
}
