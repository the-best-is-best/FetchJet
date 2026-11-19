package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleDriveTest {
    private val id = "14Qaoi22B4nMigF_yTL_1HHu3soM7cxUu"
    private val direct = "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"

    @Test
    fun everyDriveSpellingGivesTheDirectLink() {
        for (link in listOf(
            "https://drive.google.com/file/d/$id/view?usp=sharing",
            "https://drive.google.com/file/u/1/d/$id/view",
            "https://drive.google.com/uc?export=download&id=$id",
            "https://drive.google.com/open?id=$id",
            "https://drive.usercontent.google.com/download?id=$id&export=download",
        )) assertEquals(direct, googleDriveDownloadUrl(link), link)
    }

    @Test
    fun otherLinksAreLeftAlone() {
        assertNull(googleDriveDownloadUrl("https://example.com/file/d/$id/view"))
        assertNull(googleDriveDownloadUrl("https://drive.google.com/drive/folders/$id"))
        assertEquals(
            "https://github.com/o/r/raw/main/a.zip",
            com.michelle.raouf.fetch_jet.feature.queue_manager.codeHostRawUrl("https://github.com/o/r/blob/main/a.zip")
        )
    }

    @Test
    fun driveHostsAreKnown() {
        assertTrue(isGoogleDriveLink(direct))
        assertTrue(isGoogleDriveLink("https://drive.google.com/uc?id=$id"))
    }
}
