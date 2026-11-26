package com.michelle.raouf.fetch_jet.feature.hidden

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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

class HiddenRecoveryTest {
    private lateinit var home: File
    private lateinit var file: File
    private var oldHome: String? = null
    private var clock = 1_000_000L

    private val k1 = ByteArray(32) { it.toByte() }
    private val k2 = ByteArray(32) { (it + 9).toByte() }

    @BeforeTest
    fun setUp() {
        oldHome = System.getProperty("user.home")
        home = File.createTempFile("fjhome", "").also { it.delete(); it.mkdirs() }
        System.setProperty("user.home", home.absolutePath)
        file = File(home, "hidden-recovery.json")
        clock = 1_000_000L
        HiddenStore.resetForTest(file.absolutePath) { clock }
    }

    @AfterTest
    fun tearDown() {
        HiddenStore.resetForTest(File(home, "hidden-gone.json").absolutePath)
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun reload() = HiddenStore.resetForTest(file.absolutePath) { clock }

    private fun rewrite(change: (MutableMap<String, JsonElement>) -> Unit) {
        val map = Json.parseToJsonElement(file.readText()).jsonObject.toMutableMap()
        change(map)
        file.writeText(JsonObject(map).toString())
        File(file.path + ".bak").delete()
    }

    private fun loseDeviceKey() {
        rewrite { map ->
            map["rk"] = JsonPrimitive("fjenc1:AAAA")
            val enc = map.getValue("enc").jsonObject.mapValues { (_, e) ->
                JsonObject(e.jsonObject + mapOf("key" to JsonPrimitive("fjenc1:AAAA"), "name" to JsonPrimitive("fjenc1:AAAA")))
            }
            map["enc"] = JsonObject(enc)
        }
        reload()
    }

    private fun twoFiles() {
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.setEncrypted("t1", k1, "One.mp4", "a.fjh"))
        assertTrue(HiddenStore.setEncrypted("t2", k2, "Two.mp3", "b.fjh"))
    }

    @Test
    fun theCodeRoundTripsAndIsForgivingAboutSpacingAndCase() {
        val key = ByteArray(32) { (it * 7 + 3).toByte() }
        val code = RecoveryCode.encode(key)
        assertEquals(14, code.split('-').size)
        assertTrue(code.split('-').all { it.length == 4 })
        assertContentEquals(key, RecoveryCode.decode(code))
        assertContentEquals(key, RecoveryCode.decode(code.lowercase().replace('-', ' ')))
        assertContentEquals(key, RecoveryCode.decode("  " + code.replace("-", "") + "\n"))
        assertContentEquals(key, RecoveryCode.decode(code.replace('0', 'O').replace('1', 'l')))
    }

    @Test
    fun anyTypoOrSwapOrWrongLengthIsRejected() {
        val key = ByteArray(32) { (it * 7 + 3).toByte() }
        val plain = RecoveryCode.encode(key).replace("-", "")
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        for (i in plain.indices) {
            val other = alphabet.first { it != plain[i] }
            assertNull(RecoveryCode.decode(plain.substring(0, i) + other + plain.substring(i + 1)), "position $i")
        }
        for (i in 0 until plain.length - 1) {
            if (plain[i] == plain[i + 1]) continue
            val swapped = plain.substring(0, i) + plain[i + 1] + plain[i] + plain.substring(i + 2)
            assertNull(RecoveryCode.decode(swapped), "swap $i")
        }
        assertNull(RecoveryCode.decode(plain.dropLast(1)))
        assertNull(RecoveryCode.decode(plain + "0"))
        assertNull(RecoveryCode.decode(""))
        assertNull(RecoveryCode.decode(plain.replaceRange(0, 1, "U")))
    }

    @Test
    fun oneCodeForTheWholeVaultAndNoKeyInTheClear() {
        twoFiles()
        val code = assertNotNull(HiddenStore.recoveryCode())
        assertEquals(code, HiddenStore.recoveryCode())
        val text = file.readText()
        assertFalse(text.contains(code.replace("-", "")))
        val obj = Json.parseToJsonElement(text).jsonObject
        assertEquals(setOf("t1", "t2"), obj.getValue("rec").jsonObject.keys)
        assertTrue((obj.getValue("rk") as JsonPrimitive).content.isNotEmpty())
        reload()
        assertEquals(code, HiddenStore.recoveryCode())
    }

    @Test
    fun filesOpenAgainOnADeviceWhoseKeyIsLost() {
        twoFiles()
        val code = assertNotNull(HiddenStore.recoveryCode())
        loseDeviceKey()
        assertNull(HiddenStore.encryptedInfo("t1"))
        assertNull(HiddenStore.encryptedInfo("t2"))
        assertNull(HiddenStore.recoveryCode())

        val wrong = RecoveryCode.encode(ByteArray(32) { 7 })
        assertEquals(RecoveryRestore.WRONG_CODE, HiddenStore.restoreWithCode(wrong))
        assertEquals(RecoveryRestore.WRONG_CODE, HiddenStore.restoreWithCode("not a code"))
        assertNull(HiddenStore.encryptedInfo("t1"))

        assertEquals(RecoveryRestore.DONE, HiddenStore.restoreWithCode(code.lowercase().replace("-", " ")))
        val one = assertNotNull(HiddenStore.encryptedInfo("t1"))
        assertContentEquals(k1, one.key)
        assertEquals("One.mp4", one.name)
        assertEquals("a.fjh", one.stored)
        val two = assertNotNull(HiddenStore.encryptedInfo("t2"))
        assertContentEquals(k2, two.key)
        assertEquals("Two.mp3", two.name)
        assertEquals(code, HiddenStore.recoveryCode())

        reload()
        assertContentEquals(k1, assertNotNull(HiddenStore.encryptedInfo("t1")).key)
        assertEquals(code, HiddenStore.recoveryCode())
        assertTrue(HiddenStore.state.value.hasLock)
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("1234"))
    }

    @Test
    fun filesAddedAfterTheRestoreAreCoveredByTheSameCode() {
        twoFiles()
        val code = assertNotNull(HiddenStore.recoveryCode())
        loseDeviceKey()
        assertEquals(RecoveryRestore.DONE, HiddenStore.restoreWithCode(code))
        assertTrue(HiddenStore.setEncrypted("t3", ByteArray(32) { 5 }, "Three.mp4", "c.fjh"))
        loseDeviceKey()
        assertEquals(RecoveryRestore.DONE, HiddenStore.restoreWithCode(code))
        assertEquals("Three.mp4", assertNotNull(HiddenStore.encryptedInfo("t3")).name)
    }

    @Test
    fun aKeyAddedWhenTheRecoveryKeyIsUnreadableIsSavedButFlagged() {
        twoFiles()
        assertFalse(HiddenStore.takeRecoveryGap())
        loseDeviceKey()
        assertTrue(HiddenStore.setEncrypted("t3", ByteArray(32) { 5 }, "Three.mp4", "c.fjh"))
        assertTrue(HiddenStore.takeRecoveryGap())
        assertFalse(HiddenStore.takeRecoveryGap())
        val rec = Json.parseToJsonElement(file.readText()).jsonObject.getValue("rec").jsonObject
        assertFalse("t3" in rec.keys)
    }

    @Test
    fun existingKeysAreWrappedUnderTheCodeWhenAnOldFileIsLoaded() {
        twoFiles()
        rewrite { it.remove("rk"); it.remove("rec") }
        reload()
        HiddenStore.ensureLoaded()
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(setOf("t1", "t2"), obj.getValue("rec").jsonObject.keys)
        assertTrue((obj.getValue("rk") as JsonPrimitive).content.isNotEmpty())
        val code = assertNotNull(HiddenStore.recoveryCode())
        loseDeviceKey()
        assertEquals(RecoveryRestore.DONE, HiddenStore.restoreWithCode(code))
        assertContentEquals(k2, assertNotNull(HiddenStore.encryptedInfo("t2")).key)
    }

    @Test
    fun aVaultWithoutEncryptedFilesHasNothingToRestore() {
        assertTrue(HiddenStore.setPin("1234"))
        val code = assertNotNull(HiddenStore.recoveryCode())
        assertEquals(RecoveryRestore.NOTHING, HiddenStore.restoreWithCode(code))
    }

    @Test
    fun keysAlreadyLostBeforeTheUpgradeCannotBeRecovered() {
        twoFiles()
        rewrite { it.remove("rk"); it.remove("rec") }
        loseDeviceKey()
        rewrite { it.remove("rk") }
        reload()
        val code = assertNotNull(HiddenStore.recoveryCode())
        assertEquals(RecoveryRestore.NOTHING, HiddenStore.restoreWithCode(code))
        assertNull(HiddenStore.encryptedInfo("t1"))
    }

    @Test
    fun anotherVaultsCodeOrATamperedEntryDoesNotOpenAFile() {
        twoFiles()
        val code = assertNotNull(HiddenStore.recoveryCode())
        rewrite { map ->
            val rec = map.getValue("rec").jsonObject
            map["rec"] = JsonObject(mapOf("t1" to rec.getValue("t2"), "t2" to rec.getValue("t1")))
        }
        loseDeviceKey()
        assertEquals(RecoveryRestore.WRONG_CODE, HiddenStore.restoreWithCode(code))
    }

    @Test
    fun unhidingAFileDropsItsRecoveryEntry() {
        twoFiles()
        HiddenStore.clearEncrypted("t1")
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(setOf("t2"), obj.getValue("rec").jsonObject.keys)
        HiddenStore.drop(listOf("t2"))
        assertEquals(emptySet(), Json.parseToJsonElement(file.readText()).jsonObject.getValue("rec").jsonObject.keys)
    }

    @Test
    fun theRecoveryKeySurvivesRemovingTheLock() {
        twoFiles()
        val code = assertNotNull(HiddenStore.recoveryCode())
        assertEquals(HiddenCheck.Ok, HiddenStore.removeLock("1234"))
        assertEquals(code, HiddenStore.recoveryCode())
    }

    @Test
    fun theFirstAcceptedNoticeCreatesTheKey() {
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(((Json.parseToJsonElement(file.readText()).jsonObject["rk"] as? JsonPrimitive)?.content).orEmpty().isEmpty())
        HiddenStore.acceptNotice()
        assertTrue((Json.parseToJsonElement(file.readText()).jsonObject["rk"] as JsonPrimitive).content.isNotEmpty())
        assertNotNull(HiddenStore.recoveryCode())
    }

    @Test
    fun forgotPinNeedsTheDeviceAuthFirstAndIsOneShot() {
        assertTrue(HiddenStore.setPin("1234"))
        assertEquals(PinReset.EXPIRED, HiddenStore.resetPin("5678"))
        assertTrue(HiddenStore.allowPinReset())
        assertTrue(HiddenStore.pinResetAllowed())
        assertEquals(PinReset.INVALID, HiddenStore.resetPin("12"))
        assertTrue(HiddenStore.pinResetAllowed())
        assertEquals(PinReset.OK, HiddenStore.resetPin("5678"))
        assertFalse(HiddenStore.pinResetAllowed())
        assertEquals(PinReset.EXPIRED, HiddenStore.resetPin("9999"))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("5678"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("1234"))
    }

    @Test
    fun theAllowanceExpiresAfterTwoMinutesAndOnCancel() {
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.allowPinReset())
        clock += HiddenStore.PIN_RESET_WINDOW_MS - 1
        assertTrue(HiddenStore.pinResetAllowed())
        clock += 2
        assertFalse(HiddenStore.pinResetAllowed())
        assertEquals(PinReset.EXPIRED, HiddenStore.resetPin("5678"))
        assertTrue(HiddenStore.allowPinReset())
        HiddenStore.cancelPinReset()
        assertEquals(PinReset.EXPIRED, HiddenStore.resetPin("5678"))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("1234"))
    }

    @Test
    fun resettingClearsTheLockoutAndKeepsEverythingElse() {
        twoFiles()
        assertTrue(HiddenStore.hide(listOf("t1")))
        repeat(5) { HiddenStore.verify("0000") }
        assertTrue(HiddenStore.verify("1234") is HiddenCheck.Locked)
        assertTrue(HiddenStore.allowPinReset())
        assertEquals(PinReset.OK, HiddenStore.resetPin("4321"))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))
        assertEquals(setOf("t1"), HiddenStore.state.value.ids)
        assertContentEquals(k1, assertNotNull(HiddenStore.encryptedInfo("t1")).key)
    }

    @Test
    fun noResetWithoutALock() {
        assertFalse(HiddenStore.allowPinReset())
        assertEquals(PinReset.NO_LOCK, HiddenStore.resetPin("1234"))
    }
}
