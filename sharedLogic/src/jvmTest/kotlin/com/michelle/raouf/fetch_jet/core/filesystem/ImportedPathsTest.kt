package com.michelle.raouf.fetch_jet.core.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportedPathsTest {
    @Test
    fun aBackupFromAnotherAndroidUserKeepsItsSubfolder() {
        assertEquals(
            "/storage/emulated/150/Download/IELTS Listening",
            remapStorageUser("/storage/emulated/0/Download/IELTS Listening", "/storage/emulated/150")
        )
    }

    @Test
    fun theSameUserIsLeftAlone() {
        assertEquals(
            "/storage/emulated/0/Download/x",
            remapStorageUser("/storage/emulated/0/Download/x", "/storage/emulated/0")
        )
    }

    @Test
    fun otherPathsAreNotTouched() {
        assertEquals("C:/Users/me/Downloads", remapStorageUser("C:/Users/me/Downloads", "/storage/emulated/150"))
        assertEquals("/home/me/Downloads", remapStorageUser("/home/me/Downloads", null))
    }
}
