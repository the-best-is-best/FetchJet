package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

internal class ConnectionTuner(
    val maxConnections: Int,
    startConnections: Int = START_CONNECTIONS
) {
    private enum class Mode { CLIMB, DESCEND, HOLD, PROBE_UP, PROBE_DOWN }

    @Volatile
    var cap: Int = startConnections.coerceIn(MIN_CONNECTIONS, maxConnections)
        private set

    private var mode = if (startConnections > START_CONNECTIONS) Mode.DESCEND else Mode.CLIMB
    private var windowStartMs = -1L
    private var windowStartBytes = 0L
    private var settleUntilMs = 0L
    private var holdUntilMs = 0L
    private var bestCap = 0
    private var bestSpeed = 0L
    private var probeUpNext = true
    private var lowWindows = 0

    val bestKnown: Int get() = if (bestCap > 0) bestCap else cap

    fun onSample(nowMs: Long, totalBytes: Long, running: Int, remainingBytes: Long) {
        if (remainingBytes < FREEZE_BYTES) {
            windowStartMs = -1L
            return
        }
        if (nowMs < settleUntilMs) {
            windowStartMs = -1L
            return
        }
        if (running < cap - 1) {
            windowStartMs = -1L
            return
        }
        if (windowStartMs < 0L) {
            windowStartMs = nowMs
            windowStartBytes = totalBytes
            return
        }
        if (nowMs - windowStartMs < WINDOW_MS) return
        val speed = (totalBytes - windowStartBytes) * 1000L / (nowMs - windowStartMs)
        windowStartMs = -1L
        decide(nowMs, speed)
    }

    private fun decide(nowMs: Long, speed: Long) {
        when (mode) {
            Mode.CLIMB -> {
                if (bestSpeed == 0L || speed >= bestSpeed * UP_GAIN) {
                    bestCap = cap
                    bestSpeed = speed
                    if (cap >= maxConnections) enterHold(nowMs) else setCap(grow(cap, true), nowMs)
                } else {
                    setCap(bestCap, nowMs)
                    enterHold(nowMs)
                }
            }

            Mode.DESCEND -> {
                if (bestSpeed == 0L || speed >= bestSpeed * DOWN_KEEP) {
                    bestCap = cap
                    bestSpeed = max(speed, bestSpeed)
                    if (cap <= MIN_CONNECTIONS) enterHold(nowMs) else setCap(max(MIN_CONNECTIONS, cap / 2), nowMs)
                } else {
                    setCap(bestCap, nowMs)
                    enterHold(nowMs)
                }
            }

            Mode.PROBE_UP -> {
                if (speed >= bestSpeed * UP_GAIN) {
                    bestCap = cap
                    bestSpeed = speed
                } else {
                    setCap(bestCap, nowMs)
                }
                enterHold(nowMs)
            }

            Mode.PROBE_DOWN -> {
                if (speed >= bestSpeed * DOWN_KEEP) {
                    bestCap = cap
                    bestSpeed = max(speed, (bestSpeed * DOWN_KEEP).toLong())
                } else {
                    setCap(bestCap, nowMs)
                }
                enterHold(nowMs)
            }

            Mode.HOLD -> {
                lowWindows = if (bestSpeed > 0L && speed < bestSpeed * COLLAPSE) lowWindows + 1 else 0
                if (lowWindows >= 2) {
                    bestCap = 0
                    bestSpeed = 0L
                    mode = Mode.CLIMB
                    setCap(START_CONNECTIONS.coerceIn(MIN_CONNECTIONS, maxConnections), nowMs)
                    return
                }
                bestSpeed = (bestSpeed + speed) / 2
                if (nowMs >= holdUntilMs) startProbe(nowMs)
            }
        }
    }

    private fun startProbe(nowMs: Long) {
        val up = probeUpNext
        probeUpNext = !probeUpNext
        val target = if (up) grow(cap, false) else shrink(cap)
        if (target == cap) {
            holdUntilMs = nowMs + HOLD_MS
            return
        }
        mode = if (up) Mode.PROBE_UP else Mode.PROBE_DOWN
        setCap(target, nowMs)
    }

    private fun enterHold(nowMs: Long) {
        mode = Mode.HOLD
        holdUntilMs = nowMs + HOLD_MS
        lowWindows = 0
    }

    private fun setCap(value: Int, nowMs: Long) {
        cap = value.coerceIn(MIN_CONNECTIONS, maxConnections)
        settleUntilMs = nowMs + SETTLE_MS
        windowStartMs = -1L
    }

    private fun grow(from: Int, fast: Boolean): Int =
        min(maxConnections, if (fast) from * 2 else if (from < 8) from + 2 else from + 4)

    private fun shrink(from: Int): Int = max(MIN_CONNECTIONS, if (from <= 8) from - 2 else from - 4)

    companion object {
        const val MIN_CONNECTIONS = 2
        const val START_CONNECTIONS = 4
        const val WINDOW_MS = 3_000L
        const val SETTLE_MS = 1_500L
        const val HOLD_MS = 20_000L
        const val FREEZE_BYTES = 6L * 1024 * 1024
        const val UP_GAIN = 1.10
        const val DOWN_KEEP = 0.95
        const val COLLAPSE = 0.6

        @Volatile
        private var learned: Map<String, Int> = emptyMap()

        fun learnedFor(host: String): Int? = learned[host]

        fun learn(host: String, connections: Int) {
            if (host.isEmpty()) return
            learned = learned + (host to connections)
        }

        fun forgetAll() {
            learned = emptyMap()
        }
    }
}
