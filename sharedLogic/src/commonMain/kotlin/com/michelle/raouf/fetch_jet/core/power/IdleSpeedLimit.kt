package com.michelle.raouf.fetch_jet.core.power

import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

expect fun platformUserIdleMs(): Long?

const val IDLE_UNLIMITED_AFTER_MS = 3 * 60 * 1000L

private const val IDLE_CACHE_MS = 2000L

fun effectiveLimit(configured: Long, onlyInUse: Boolean, idleMs: Long?): Long =
    if (configured > 0L && onlyInUse && idleMs != null && idleMs >= IDLE_UNLIMITED_AFTER_MS) 0L else configured

object UserIdle {
    private class Reading(val at: TimeSource.Monotonic.ValueTimeMark, val idleMs: Long?)

    @Volatile
    private var last: Reading? = null

    @Volatile
    var debugIdleMs: Long? = null

    fun idleMs(): Long? {
        debugIdleMs?.let { return it }
        val cached = last
        if (cached != null && cached.at.elapsedNow().inWholeMilliseconds < IDLE_CACHE_MS) return cached.idleMs
        val fresh = try {
            platformUserIdleMs()
        } catch (_: Throwable) {
            null
        }
        last = Reading(TimeSource.Monotonic.markNow(), fresh)
        return fresh
    }
}
