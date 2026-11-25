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
            listOf(RowMenuEntry.OPEN, RowMenuEntry.DETAILS, RowMenuEntry.SELECT, RowMenuEntry.UNHIDE, RowMenuEntry.DELETE),
            entries
        )
    }

    @Test
    fun aHiddenRunningOrPausedTaskKeepsPauseAndResume() {
        assertEquals(
            listOf(RowMenuEntry.DETAILS, RowMenuEntry.PAUSE, RowMenuEntry.UNHIDE, RowMenuEntry.DELETE),
            RowMenu.entries(task("a.mp4", DownloadStatus.DOWNLOADING, true))
        )
        assertEquals(
            listOf(RowMenuEntry.DETAILS, RowMenuEntry.RESUME, RowMenuEntry.UNHIDE, RowMenuEntry.DELETE),
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

    @Test
    fun entriesComeGroupedOpenMediaDownloadManage() {
        val video = RowMenu.entries(task("a.mp4", DownloadStatus.COMPLETED, false), options)
        assertEquals(
            listOf(
                RowMenuEntry.OPEN, RowMenuEntry.OPEN_FOLDER, RowMenuEntry.DETAILS,
                RowMenuEntry.CUT, RowMenuEntry.EXTRACT_AUDIO,
                RowMenuEntry.SEND_TO_DEVICE, RowMenuEntry.COPY_LINK,
                RowMenuEntry.SELECT, RowMenuEntry.DELETE
            ),
            video
        )
        assertTrue(RowMenu.startsSection(video, video.indexOf(RowMenuEntry.CUT)))
        assertFalse(RowMenu.startsSection(video, video.indexOf(RowMenuEntry.EXTRACT_AUDIO)))
        val running = RowMenu.entries(task("a.bin", DownloadStatus.DOWNLOADING, false), options)
        assertEquals(
            listOf(RowMenuEntry.OPEN_FOLDER, RowMenuEntry.DETAILS, RowMenuEntry.PAUSE, RowMenuEntry.SEND_TO_DEVICE, RowMenuEntry.COPY_LINK, RowMenuEntry.SELECT, RowMenuEntry.DELETE),
            running
        )
        for (name in listOf("a.zip", "a.mp3", "a.mp4", "a.bin")) {
            for (status in DownloadStatus.entries) {
                for (hidden in listOf(false, true)) {
                    val sections = RowMenu.entries(task(name, status, hidden), options).map { it.section.ordinal }
                    assertEquals(sections.sorted(), sections, "$name $status $hidden")
                }
            }
        }
    }

    @Test
    fun everyEntryHasASection() {
        val expected = mapOf(
            RowMenuEntry.OPEN to RowMenuSection.OPEN, RowMenuEntry.OPEN_FOLDER to RowMenuSection.OPEN,
            RowMenuEntry.DETAILS to RowMenuSection.OPEN,
            RowMenuEntry.EXTRACT to RowMenuSection.MEDIA, RowMenuEntry.COMPRESS to RowMenuSection.MEDIA,
            RowMenuEntry.CUT to RowMenuSection.MEDIA, RowMenuEntry.EXTRACT_AUDIO to RowMenuSection.MEDIA,
            RowMenuEntry.SUBTITLES to RowMenuSection.MEDIA,
            RowMenuEntry.PAUSE to RowMenuSection.DOWNLOAD, RowMenuEntry.RESUME to RowMenuSection.DOWNLOAD,
            RowMenuEntry.SEND_TO_DEVICE to RowMenuSection.DOWNLOAD, RowMenuEntry.COPY_LINK to RowMenuSection.DOWNLOAD,
            RowMenuEntry.SELECT to RowMenuSection.MANAGE, RowMenuEntry.UNHIDE to RowMenuSection.MANAGE,
            RowMenuEntry.DELETE to RowMenuSection.MANAGE
        )
        assertEquals(RowMenuEntry.entries.toSet(), expected.keys)
        expected.forEach { (entry, section) -> assertEquals(section, entry.section, entry.name) }
    }
}
