package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionTunerTest {
    private class Run(val tuner: ConnectionTuner, val lateSpeed: Double, val caps: List<Int>)

    private fun simulate(
        seconds: Int,
        maxConnections: Int = 32,
        start: Int = ConnectionTuner.START_CONNECTIONS,
        remaining: (Long) -> Long = { Long.MAX_VALUE / 4 },
        speedAt: (cap: Int, nowMs: Long) -> Double
    ): Run {
        val tuner = ConnectionTuner(maxConnections, start)
        var bytes = 0L
        var now = 0L
        val caps = ArrayList<Int>()
        var lateBytes = 0L
        val lateFrom = (seconds - 20) * 1000L
        while (now < seconds * 1000L) {
            now += 200
            val cap = tuner.cap
            val gained = (speedAt(cap, now) * 0.2).toLong()
            bytes += gained
            if (now >= lateFrom) lateBytes += gained
            tuner.onSample(now, bytes, cap, remaining(bytes))
            caps.add(tuner.cap)
        }
        return Run(tuner, lateBytes / 20.0, caps)
    }

    private val kb = 1024.0
    private val mb = 1024.0 * 1024.0

    @Test
    fun aNetworkThatFillsUpWithFewConnectionsStaysSmall() {
        val perConnection = 500 * kb
        val total = 2 * mb
        val run = simulate(150) { cap, _ -> min(cap * perConnection, total) }
        assertTrue(run.tuner.cap in 4..8, "cap ${run.tuner.cap}")
        assertTrue(run.lateSpeed >= total * 0.9, "speed ${run.lateSpeed}")
    }

    @Test
    fun aServerThatLimitsEachConnectionGetsTheMostConnections() {
        val run = simulate(150) { cap, _ -> cap * 20 * kb }
        assertEquals(32, run.tuner.cap)
        assertTrue(run.lateSpeed >= 32 * 20 * kb * 0.9)
    }

    @Test
    fun aWeakNetworkDoesNotKeepThirtyTwoConnections() {
        val total = 0.75 * mb
        val run = simulate(150) { cap, _ -> min(cap * 100 * kb, total) }
        assertTrue(run.tuner.cap <= 12, "cap ${run.tuner.cap}")
        assertTrue(run.lateSpeed >= total * 0.9, "speed ${run.lateSpeed}")
    }

    @Test
    fun tooManyConnectionsThatHurtAreLeft() {
        val run = simulate(200) { cap, _ ->
            min(cap * 120 * kb, 1.6 * mb) * (1.0 - 0.03 * max(0, cap - 12))
        }
        assertTrue(run.tuner.cap <= 20, "cap ${run.tuner.cap}")
        assertTrue(run.lateSpeed >= 1.3 * mb, "speed ${run.lateSpeed}")
    }

    @Test
    fun theTunerRelearnsWhenTheNetworkChanges() {
        val run = simulate(260) { cap, now ->
            val total = if (now < 100_000) 4 * mb else 1 * mb
            min(cap * 300 * kb, total)
        }
        assertTrue(run.lateSpeed >= 0.85 * mb, "speed ${run.lateSpeed}")
        assertTrue(run.tuner.cap <= 8, "cap ${run.tuner.cap}")
    }

    @Test
    fun theEndOfAFileIsLeftAlone() {
        val total = 40L * 1024 * 1024
        val run = simulate(60, remaining = { total - it }) { cap, _ -> min(cap * 500 * kb, 2 * mb) }
        val lastChange = run.caps.indexOfLast { it != run.caps.last() }
        val finalTailStartsAt = run.caps.indices.firstOrNull { (it + 1) * 200L * 2 * mb / 1000 >= total - 6L * 1024 * 1024 } ?: run.caps.size
        assertTrue(lastChange < finalTailStartsAt + 20, "cap changed at the very end")
    }

    @Test
    fun aHighRememberedStartComesDownFastWhenTheNetworkIsTheLimit() {
        val total = 2 * mb
        val run = simulate(60, start = 32) { cap, _ -> min(cap * 500 * kb, total) }
        assertTrue(run.tuner.cap <= 8, "cap ${run.tuner.cap}")
        assertTrue(run.lateSpeed >= total * 0.9, "speed ${run.lateSpeed}")
    }

    @Test
    fun aHighRememberedStartStaysWhenManyConnectionsReallyHelp() {
        val run = simulate(80, start = 32) { cap, _ -> cap * 20 * kb }
        assertEquals(32, run.tuner.cap)
    }

    @Test
    fun aSmallMaxIsNeverExceeded() {
        val run = simulate(100, maxConnections = 8) { cap, _ -> cap * 50 * kb }
        assertTrue(run.caps.all { it in ConnectionTuner.MIN_CONNECTIONS..8 })
        assertEquals(8, run.tuner.cap)
    }

    @Test
    fun theBestCountIsRememberedPerHost() {
        ConnectionTuner.forgetAll()
        ConnectionTuner.learn("example.com", 12)
        assertEquals(12, ConnectionTuner.learnedFor("example.com"))
        assertEquals(null, ConnectionTuner.learnedFor("other.com"))
        ConnectionTuner.forgetAll()
    }
}
