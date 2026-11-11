package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.presentation.dashboard.renamedPart
import kotlin.test.Test
import kotlin.test.assertEquals

class FreeFileNameTest {
    @Test
    fun freeNameIsKept() {
        assertEquals("128MB.bin", firstFreeFileName("128MB.bin") { false })
    }

    @Test
    fun takenNameGetsNumber() {
        val taken = setOf("128MB.bin", "128MB (1).bin")
        assertEquals("128MB (2).bin", firstFreeFileName("128MB.bin") { it in taken })
    }

    @Test
    fun nameWithoutExtension() {
        assertEquals("README (1)", firstFreeFileName("README") { it == "README" })
        assertEquals(".env (1)", firstFreeFileName(".env") { it == ".env" })
    }

    @Test
    fun receivedPartsFollowTheFreeName() {
        assertEquals("128MB (1).bin.part0", renamedPart("128MB.bin.part0", "128MB.bin", "128MB (1).bin"))
        assertEquals("128MB (1).bin.chunks", renamedPart("128MB.bin.chunks", "128MB.bin", "128MB (1).bin"))
        assertEquals("stream0.mp4", renamedPart("stream0.mp4", "v.mp4", "v (1).mp4"))
        assertEquals("a.bin.part0", renamedPart("a.bin.part0", "a.bin", "a.bin"))
    }
}
