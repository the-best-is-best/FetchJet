package com.michelle.raouf.fetch_jet.core.power

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WakeForScheduleTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test
    fun nothingScheduledMeansNoTimer() {
        assertNull(wakeTimeFor(emptyList(), now))
    }

    @Test
    fun pastAndPresentStartsAreIgnored() {
        assertNull(wakeTimeFor(listOf(0L, now - minute, now), now))
    }

    @Test
    fun wakesOneMinuteBeforeTheEarliestFutureStart() {
        val starts = listOf(now + 90 * minute, now - minute, now + 30 * minute, now + 45 * minute)
        assertEquals(now + 29 * minute, wakeTimeFor(starts, now))
    }

    @Test
    fun aStartCloserThanTheLeadWakesAtTheStartItself() {
        assertEquals(now + 20_000L, wakeTimeFor(listOf(now + 20_000L, now + 30 * minute), now))
        assertEquals(now + minute, wakeTimeFor(listOf(now + minute), now))
    }

    @Test
    fun theLeadCanBeChanged() {
        assertEquals(now + 25 * minute, wakeTimeFor(listOf(now + 30 * minute), now, leadMs = 5 * minute))
    }
}
