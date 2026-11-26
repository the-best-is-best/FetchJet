package com.michelle.raouf.fetch_jet.feature.hidden

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HiddenEncryptionStoreTest {
    private lateinit var home: File
    private lateinit var file: File
    private var oldHome: String? = null

    @BeforeTest
    fun setUp() {
        oldHome = System.getProperty("user.home")
        home = File.createTempFile("fjhome", "").also { it.delete(); it.mkdirs() }
        System.setProperty("user.home", home.absolutePath)
        file = File(home, "hidden-enc.json")
        HiddenStore.resetForTest(file.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        HiddenStore.resetForTest(File(home, "hidden-gone.json").absolutePath)
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    @Test
    fun anOldFileWithoutTheNewFieldsStillLoads() {
        file.writeText("""{"lockType":"pin","salt":"s","hash":"h","ids":["a"],"playlists":["p"],"origins":{"a":"/x"},"fails":2}""")
        HiddenStore.resetForTest(file.absolutePath)
        HiddenStore.ensureLoaded()
        val s = HiddenStore.state.value
        assertEquals(setOf("a"), s.ids)
        assertEquals(setOf("p"), s.playlists)
        assertTrue(s.hasLock)
        assertFalse(s.noticeAccepted)
        assertFalse(HiddenStore.hasEncrypted("a"))
        assertEquals("/x", HiddenStore.originOf("a"))
        assertNull(HiddenStore.encryptedInfo("a"))
    }

    @Test
    fun aKeyAndANameArePersistedProtectedAndComeBackAfterAReload() {
        assertTrue(HiddenStore.setPin("1234"))
        val key = ByteArray(32) { (it * 3).toByte() }
        assertTrue(HiddenStore.setEncrypted("t1", key, "My Secret Movie.mp4", "abc.fjh"))
        HiddenStore.acceptNotice()
        val text = file.readText()
        assertFalse(text.contains("My Secret Movie"))
        assertFalse(text.contains("00030609"))
        assertTrue(text.contains("abc.fjh"))
        HiddenStore.resetForTest(file.absolutePath)
        HiddenStore.ensureLoaded()
        assertTrue(HiddenStore.noticeAccepted())
        assertTrue(HiddenStore.hasEncrypted("t1"))
        val info = assertNotNull(HiddenStore.encryptedInfo("t1"))
        assertContentEquals(key, info.key)
        assertEquals("My Secret Movie.mp4", info.name)
        assertEquals("abc.fjh", info.stored)
    }

    @Test
    fun removingThePinKeepsTheKeysAndTheNotice() {
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.setEncrypted("t1", ByteArray(32) { 7 }, "a.mp4", "x.fjh"))
        HiddenStore.acceptNotice()
        assertEquals(HiddenCheck.Ok, HiddenStore.removeLock("1234"))
        assertFalse(HiddenStore.state.value.hasLock)
        assertTrue(HiddenStore.hasEncrypted("t1"))
        assertNotNull(HiddenStore.encryptedInfo("t1"))
        assertTrue(HiddenStore.noticeAccepted())
    }

    @Test
    fun unhideDropAndForgetClearTheKeys() {
        assertTrue(HiddenStore.setPin("1234"))
        for (id in listOf("a", "b", "c")) {
            HiddenStore.hide(listOf(id))
            assertTrue(HiddenStore.setEncrypted(id, ByteArray(32) { 1 }, "$id.mp4", "$id.fjh"))
        }
        HiddenStore.drop(listOf("a"), keepKeys = true)
        assertTrue(HiddenStore.hasEncrypted("a"))
        HiddenStore.drop(listOf("a"))
        assertFalse(HiddenStore.hasEncrypted("a"))
        HiddenStore.drop(listOf("b"))
        assertFalse(HiddenStore.hasEncrypted("b"))
        assertTrue(HiddenStore.hasEncrypted("c"))
        HiddenStore.clearEncrypted("c")
        assertFalse(HiddenStore.hasEncrypted("c"))
    }

    @Test
    fun aBrokenFileIsBackedUpBeforeItIsReplaced() {
        file.writeText("{ not json")
        HiddenStore.resetForTest(file.absolutePath)
        HiddenStore.ensureLoaded()
        assertTrue(File(file.path + ".bad").exists())
        assertEquals("{ not json", File(file.path + ".bad").readText())
    }
}
