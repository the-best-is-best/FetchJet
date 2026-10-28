package com.michelle.raouf.fetch_jet.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class RateLimiter(private val speedLimitProvider: () -> Long) {
    private val mutex = Mutex()
    private var availableTokens: Double = 0.0
    private var lastRefillMark = TimeSource.Monotonic.markNow()
    private var lastLimit = 0L

    suspend fun acquire(bytes: Int) {
        if (bytes <= 0) return
        val currentLimit = speedLimitProvider()
        if (currentLimit <= 0L) return

        var waitDurationMs = 0L

        mutex.withLock {
            val limit = speedLimitProvider()
            if (limit <= 0L) {
                availableTokens = 0.0
                return@withLock
            }

            val now = TimeSource.Monotonic.markNow()

            // If the limit changed, reset tokens immediately to avoid old deficits/surpluses
            if (limit != lastLimit) {
                lastLimit = limit
                availableTokens = 0.0
                lastRefillMark = now
            }

            val elapsedSec = (now - lastRefillMark).inWholeMicroseconds / 1_000_000.0
            lastRefillMark = now

            // Refill tokens: max burst capacity is 200ms worth of data or at least 16KB
            val maxTokens = maxOf(16 * 1024.0, limit * 0.20)
            availableTokens = minOf(maxTokens, availableTokens + elapsedSec * limit.toDouble())

            // Deduct requested bytes
            availableTokens -= bytes.toDouble()

            // If we are in deficit, calculate sleep time to refill back to 0
            if (availableTokens < 0.0) {
                val deficit = -availableTokens
                val waitSec = deficit / limit.toDouble()
                waitDurationMs = (waitSec * 1000.0).toLong()
            }
        }

        if (waitDurationMs > 0L) {
            delay(waitDurationMs.milliseconds)
        }
    }
}