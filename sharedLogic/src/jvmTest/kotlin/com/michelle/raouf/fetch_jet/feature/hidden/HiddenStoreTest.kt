package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenStoreTest {
    private lateinit var file: File
    private var clock = 1_000_000L

    private fun task(id: String, playlist: String = "") = DownloadTask(
        id = id, url = "http://x/$id", fileName = "$id.bin", destinationPath = "/tmp",
        engineType = EngineType.HTTP_DIRECT, playlistId = playlist
    )

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("hidden", ".json").also { it.delete() }
        clock = 1_000_000L
        HiddenStore.resetForTest(file.absolutePath) { clock }
    }

    @AfterTest
    fun tearDown() {
        HiddenStore.resetForTest(null)
        file.delete()
        File(file.path + ".bak").delete()
        File(file.path + ".bad").delete()
        File(file.path + ".tmp").delete()
    }

    private fun folderHidden(folder: String) = HiddenStore.folderKeyOf(folder) in HiddenStore.state.value.folders

    @Test
    fun nothingIsHiddenWithoutAPin() {
        HiddenStore.hide(listOf("a"))
        assertFalse(HiddenStore.state.value.hides(task("a")))
    }

    @Test
    fun pinNeedsFourToSixDigits() {
        assertFalse(HiddenStore.setPin("123"))
        assertFalse(HiddenStore.setPin("1234567"))
        assertFalse(HiddenStore.setPin("12a4"))
        assertTrue(HiddenStore.setPin("1234"))
        assertFalse(HiddenStore.setPin("5678"))
    }

    @Test
    fun rightAndWrongPin() {
        assertTrue(HiddenStore.setPin("4321"))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("0000"))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("1111"))
    }

    @Test
    fun fiveWrongTriesLockForAWhileThenItOpens() {
        HiddenStore.setPin("4321")
        repeat(4) { HiddenStore.verify("0000") }
        val locked = HiddenStore.verify("0000")
        assertTrue(locked is HiddenCheck.Locked)
        assertTrue(HiddenStore.verify("4321") is HiddenCheck.Locked)
        clock += 31_000
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("0000"))
    }

    @Test
    fun hidingTasksAndPlaylists() {
        HiddenStore.setPin("4321")
        HiddenStore.hide(listOf("a"))
        HiddenStore.hidePlaylist("p1")
        val s = HiddenStore.state.value
        assertTrue(s.hides(task("a")))
        assertFalse(s.hides(task("b")))
        assertTrue(s.hides(task("c", "p1")))
        assertFalse(s.hides(task("d", "p2")))
        HiddenStore.unhide(listOf(task("c", "p1")))
        assertFalse(HiddenStore.state.value.hides(task("e", "p1")))
        assertTrue(HiddenStore.state.value.hides(task("a")))
        HiddenStore.unhide(listOf(task("a")))
        assertFalse(HiddenStore.state.value.hides(task("a")))
    }

    @Test
    fun survivesARestartAndKeepsTheLockCounter() {
        HiddenStore.setPin("4321")
        HiddenStore.hide(listOf("a"))
        HiddenStore.verify("0000")
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
        assertTrue(HiddenStore.state.value.hides(task("a")))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))
    }

    @Test
    fun changeAndRemoveThePin() {
        HiddenStore.setPin("4321")
        HiddenStore.hide(listOf("a"))
        assertEquals(HiddenCheck.Invalid, HiddenStore.changePin("4321", "12"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.changePin("0000", "999999"))
        assertEquals(HiddenCheck.Ok, HiddenStore.changePin("4321", "999999"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("4321"))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("999999"))
        assertEquals(HiddenCheck.Ok, HiddenStore.removeLock("999999"))
        assertFalse(HiddenStore.hasLock())
        assertFalse(HiddenStore.state.value.hides(task("a")))
    }

    @Test
    fun originsAndFoldersSurviveARestart() {
        HiddenStore.setPin("4321")
        HiddenStore.setOrigin("a", "C:\\Users\\me\\Downloads\\Work")
        HiddenStore.hide(listOf("a"))
        HiddenStore.hideFolder("C:/Users/me/Downloads/Work/")
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
        assertEquals("C:\\Users\\me\\Downloads\\Work", HiddenStore.originOf("a"))
        assertTrue(folderHidden("C:\\Users\\me\\Downloads\\Work"))
        assertFalse(folderHidden("C:/Users/me/Downloads"))
    }

    @Test
    fun anOldFileWithoutOriginsStillLoads() {
        HiddenStore.setPin("4321")
        HiddenStore.hide(listOf("a"))
        file.writeText(file.readText().replace(Regex(""",?\s*"(origins|folders)"\s*:\s*(\{[^}]*\}|\[[^\]]*\])"""), ""))
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
        assertTrue(HiddenStore.hasLock())
        assertFalse(file.readText().contains("origins"))
        assertTrue(HiddenStore.state.value.hides(task("a")))
        assertEquals(null, HiddenStore.originOf("a"))
    }

    @Test
    fun theFolderRuleEndsWithItsLastHiddenTask() {
        HiddenStore.setPin("4321")
        HiddenStore.setOrigin("a", "/d/Work")
        HiddenStore.setOrigin("b", "/d/Work")
        HiddenStore.setOrigin("c", "/d/Other")
        HiddenStore.hide(listOf("a", "b", "c"))
        HiddenStore.hideFolder("/d/Work")
        HiddenStore.unhide(listOf(task("a")))
        assertTrue(folderHidden("/d/Work"))
        assertEquals(null, HiddenStore.originOf("a"))
        HiddenStore.unhide(listOf(task("b")))
        assertFalse(folderHidden("/d/Work"))
        assertEquals("/d/Other", HiddenStore.originOf("c"))
    }

    @Test
    fun droppingATaskForgetsItsOrigin() {
        HiddenStore.setPin("4321")
        HiddenStore.setOrigin("a", "/d/Work")
        HiddenStore.hide(listOf("a"))
        HiddenStore.hideFolder("/d/Work")
        HiddenStore.drop(listOf("a"))
        assertFalse(HiddenStore.state.value.hides(task("a")))
        assertEquals(null, HiddenStore.originOf("a"))
        assertFalse(folderHidden("/d/Work"))
    }

    @Test
    fun thePinIsNotStoredInPlainText() {
        HiddenStore.setPin("4321")
        val text = file.readText()
        assertFalse(text.contains("4321"))
        assertTrue(text.contains("salt"))
    }

    @Test
    fun aFailedSaveRollsHideAndOriginBack() {
        assertTrue(HiddenStore.setPin("4321"))
        assertTrue(HiddenStore.hide(listOf("a")))
        val blocker = File.createTempFile("blocker", ".txt")
        try {
            HiddenStore.fileOverride = File(blocker, "sub/hidden.json").absolutePath
            assertFalse(HiddenStore.hide(listOf("b")))
            assertFalse(HiddenStore.state.value.hides(task("b")))
            assertTrue(HiddenStore.state.value.hides(task("a")))
            assertFalse(HiddenStore.setOrigin("b", "/d/Work"))
            assertEquals(null, HiddenStore.originOf("b"))
            assertFalse(HiddenStore.hidePlaylist("p9"))
            assertFalse(HiddenStore.hideFolder("/d/Work"))
            assertTrue(HiddenStore.state.value.playlists.isEmpty())
            assertTrue(HiddenStore.state.value.folders.isEmpty())
            HiddenStore.fileOverride = file.absolutePath
            assertTrue(HiddenStore.hide(listOf("b")))
            assertTrue(HiddenStore.state.value.hides(task("b")))
        } finally {
            blocker.delete()
        }
    }

    @Test
    fun aFailedSaveRollsThePinBack() {
        val blocker = File.createTempFile("blocker", ".txt")
        try {
            HiddenStore.fileOverride = File(blocker, "sub/hidden.json").absolutePath
            assertFalse(HiddenStore.setPin("4321"))
            assertFalse(HiddenStore.hasLock())
            HiddenStore.fileOverride = file.absolutePath
            assertTrue(HiddenStore.setPin("4321"))
            assertTrue(HiddenStore.hide(listOf("a")))
            HiddenStore.fileOverride = File(blocker, "sub/hidden.json").absolutePath
            assertEquals(HiddenCheck.SaveFailed, HiddenStore.changePin("4321", "999999"))
            assertEquals(HiddenCheck.SaveFailed, HiddenStore.removeLock("4321"))
            assertTrue(HiddenStore.hasLock())
            assertTrue(HiddenStore.state.value.hides(task("a")))
            assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))
            assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("999999"))
            HiddenStore.fileOverride = file.absolutePath
            assertEquals(HiddenCheck.Ok, HiddenStore.changePin("4321", "999999"))
            HiddenStore.resetForTest(file.absolutePath) { clock }
            assertEquals(HiddenCheck.Ok, HiddenStore.verify("999999"))
            assertTrue(HiddenStore.state.value.hides(task("a")))
        } finally {
            blocker.delete()
        }
    }

    @Test
    fun hideNeedsALockAndAnEmptyListIsFine() {
        assertFalse(HiddenStore.hide(listOf("a")))
        assertTrue(HiddenStore.setPin("4321"))
        assertTrue(HiddenStore.hide(emptyList()))
    }

    @Test
    fun aDamagedFileFallsBackToTheBackupAndKeepsTheFirstBadCopy() {
        assertTrue(HiddenStore.setPin("4321"))
        assertTrue(HiddenStore.hide(listOf("a")))
        assertTrue(File(file.path + ".bak").exists())
        file.writeText("{ not json")
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
        assertTrue(HiddenStore.hasLock())
        assertTrue(HiddenStore.state.value.hides(task("a")))
        assertEquals("{ not json", File(file.path + ".bad").readText())
        assertTrue(file.readText().contains("salt"))
        file.writeText("second damage")
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
        assertTrue(HiddenStore.state.value.hides(task("a")))
        assertEquals("{ not json", File(file.path + ".bad").readText())
    }

    @Test
    fun aDamagedFileWithNoBackupStartsEmpty() {
        file.writeText("garbage")
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
        assertFalse(HiddenStore.hasLock())
        assertEquals("garbage", File(file.path + ".bad").readText())
    }

    @Test
    fun severalThreadsCanHideAtOnce() {
        assertTrue(HiddenStore.setPin("4321"))
        val threads = (0 until 8).map { t ->
            Thread { for (i in 0 until 25) HiddenStore.hide(listOf("t$t-$i")) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(200, HiddenStore.state.value.ids.size)
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
        assertEquals(200, HiddenStore.state.value.ids.size)
    }
}
