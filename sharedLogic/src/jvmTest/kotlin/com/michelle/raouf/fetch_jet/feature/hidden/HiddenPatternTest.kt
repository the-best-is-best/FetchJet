package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.EnglishProvider
import com.michelle.raouf.fetch_jet.presentation.dashboard.lockTextsOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenPatternTest {
    private lateinit var file: File
    private var clock = 1_000_000L

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("hidden-pattern", ".json").also { it.delete() }
        clock = 1_000_000L
        HiddenStore.resetForTest(file.absolutePath) { clock }
    }

    @AfterTest
    fun tearDown() {
        HiddenStore.resetForTest(null)
        listOf("", ".bak", ".bad", ".tmp").forEach { File(file.path + it).delete() }
    }

    private fun reload() {
        HiddenStore.resetForTest(file.absolutePath) { clock }
        HiddenStore.ensureLoaded()
    }

    private fun storedType(): String =
        (Json.parseToJsonElement(file.readText()).jsonObject["lockType"] as JsonPrimitive).content

    @Test
    fun patternRules() {
        val p = HiddenLockType.PATTERN
        assertTrue(HiddenStore.isValidSecret(p, "1235"))
        assertTrue(HiddenStore.isValidSecret(p, "123456789"))
        assertFalse(HiddenStore.isValidSecret(p, "123"))
        assertFalse(HiddenStore.isValidSecret(p, "1231"))
        assertFalse(HiddenStore.isValidSecret(p, "1230"))
        assertFalse(HiddenStore.isValidSecret(p, "12a4"))
        assertFalse(HiddenStore.isValidSecret(p, "1234567891"))
        assertTrue(HiddenStore.isValidSecret(HiddenLockType.PIN, "0000"))
        assertFalse(HiddenStore.isValidSecret(HiddenLockType.PIN, "1234567"))
    }

    @Test
    fun setAndVerifyAPattern() {
        assertFalse(HiddenStore.setPin("1231", HiddenLockType.PATTERN))
        assertTrue(HiddenStore.setPin("1235789", HiddenLockType.PATTERN))
        assertEquals(HiddenLockType.PATTERN, HiddenStore.state.value.lockType)
        assertEquals("pattern", storedType())
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("1235789"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("9875321"))
        assertEquals(HiddenCheck.Wrong(3), HiddenStore.verify("0000"))
        reload()
        assertEquals(HiddenLockType.PATTERN, HiddenStore.state.value.lockType)
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("1235789"))
    }

    @Test
    fun patternBacksOffLikeThePin() {
        HiddenStore.setPin("14789", HiddenLockType.PATTERN)
        repeat(4) { HiddenStore.verify("12345") }
        assertEquals(HiddenCheck.Locked(30), HiddenStore.verify("12345"))
        assertTrue(HiddenStore.verify("14789") is HiddenCheck.Locked)
        clock += 31_000
        assertEquals(HiddenCheck.Locked(60), HiddenStore.verify("12345"))
        clock += 61_000
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("14789"))
    }

    @Test
    fun changePinToPatternAndBack() {
        HiddenStore.setPin("4321")
        HiddenStore.hide(listOf("a"))
        assertEquals(HiddenCheck.Invalid, HiddenStore.changePin("4321", "123", HiddenLockType.PATTERN))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.changePin("0000", "1235", HiddenLockType.PATTERN))
        assertEquals(HiddenCheck.Ok, HiddenStore.changePin("4321", "1235", HiddenLockType.PATTERN))
        assertEquals(HiddenLockType.PATTERN, HiddenStore.state.value.lockType)
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("4321"))
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("1235"))
        assertEquals(HiddenCheck.Ok, HiddenStore.changePin("1235", "098765"))
        assertEquals(HiddenLockType.PIN, HiddenStore.state.value.lockType)
        assertEquals("pin", storedType())
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("098765"))
        assertEquals(setOf("a"), HiddenStore.state.value.ids)
        assertEquals(HiddenCheck.Ok, HiddenStore.removeLock("098765"))
        assertFalse(HiddenStore.hasLock())
    }

    @Test
    fun forgotPinResetsToAPattern() {
        HiddenStore.setPin("1234")
        assertTrue(HiddenStore.allowPinReset())
        assertEquals(PinReset.INVALID, HiddenStore.resetPin("12", HiddenLockType.PATTERN))
        assertEquals(PinReset.OK, HiddenStore.resetPin("7531", HiddenLockType.PATTERN))
        assertEquals(HiddenLockType.PATTERN, HiddenStore.state.value.lockType)
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("7531"))
        assertEquals(HiddenCheck.Wrong(4), HiddenStore.verify("1234"))
    }

    @Test
    fun oldFilesLoadAsPin() {
        HiddenStore.setPin("4321")
        val text = file.readText()
        file.writeText(text.replace("\"lockType\":\"pin\"", "\"lockType\":\"\""))
        File(file.path + ".bak").delete()
        reload()
        assertEquals(HiddenLockType.PIN, HiddenStore.state.value.lockType)
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))

        file.writeText(text.replace(",\"lockType\":\"pin\"", "").replace("\"lockType\":\"pin\",", ""))
        reload()
        assertEquals(HiddenLockType.PIN, HiddenStore.state.value.lockType)
        assertEquals(HiddenCheck.Ok, HiddenStore.verify("4321"))
    }

    @Test
    fun textsFollowTheLockType() {
        val en = EnglishProvider
        val pin = lockTextsOf(en, HiddenLockType.PIN)
        val pattern = lockTextsOf(en, HiddenLockType.PATTERN)
        assertEquals(en.hiddenWrongPin, pin.wrong)
        assertEquals(en.hiddenWrongPattern, pattern.wrong)
        assertEquals(en.hiddenPatternTooShort, pattern.invalid)
        assertEquals(en.hiddenUsePattern, pin.switchTo)
        assertEquals(en.hiddenUsePin, pattern.switchTo)
    }
}
