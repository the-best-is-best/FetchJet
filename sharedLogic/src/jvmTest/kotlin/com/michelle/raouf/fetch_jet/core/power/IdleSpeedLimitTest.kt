package com.michelle.raouf.fetch_jet.core.power

import kotlin.test.Test
import kotlin.test.assertEquals

class IdleSpeedLimitTest {
    private val limit = 512 * 1024L

    @Test
    fun settingOffKeepsTheLimit() {
        assertEquals(limit, effectiveLimit(limit, onlyInUse = false, idleMs = 0L))
        assertEquals(limit, effectiveLimit(limit, onlyInUse = false, idleMs = IDLE_UNLIMITED_AFTER_MS * 10))
        assertEquals(limit, effectiveLimit(limit, onlyInUse = false, idleMs = null))
    }

    @Test
    fun inUseKeepsTheLimit() {
        assertEquals(limit, effectiveLimit(limit, onlyInUse = true, idleMs = 0L))
        assertEquals(limit, effectiveLimit(limit, onlyInUse = true, idleMs = IDLE_UNLIMITED_AFTER_MS - 1))
    }

    @Test
    fun idleLiftsTheLimit() {
        assertEquals(0L, effectiveLimit(limit, onlyInUse = true, idleMs = IDLE_UNLIMITED_AFTER_MS))
        assertEquals(0L, effectiveLimit(limit, onlyInUse = true, idleMs = IDLE_UNLIMITED_AFTER_MS * 20))
    }

    @Test
    fun unknownIdleCountsAsInUse() {
        assertEquals(limit, effectiveLimit(limit, onlyInUse = true, idleMs = null))
    }

    @Test
    fun noLimitStaysNoLimit() {
        assertEquals(0L, effectiveLimit(0L, onlyInUse = true, idleMs = 0L))
        assertEquals(0L, effectiveLimit(0L, onlyInUse = true, idleMs = IDLE_UNLIMITED_AFTER_MS))
        assertEquals(0L, effectiveLimit(0L, onlyInUse = false, idleMs = null))
    }

    @Test
    fun idleTimeIsThreeMinutes() {
        assertEquals(180_000L, IDLE_UNLIMITED_AFTER_MS)
    }

    @Test
    fun fakedIdleTimeIsReadAtOnce() {
        try {
            UserIdle.debugIdleMs = 600_000L
            assertEquals(600_000L, UserIdle.idleMs())
            UserIdle.debugIdleMs = 0L
            assertEquals(0L, UserIdle.idleMs())
        } finally {
            UserIdle.debugIdleMs = null
        }
    }
}
