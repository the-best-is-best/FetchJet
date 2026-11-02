package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class Speedometer(private val windowDurationMs: Long = 1200L) {
    private val samples = ArrayDeque<Pair<Long, Long>>() // (timeMs, totalBytes)
    private val mutex = Mutex()

    suspend fun recordSample(timeMs: Long, totalBytes: Long): Long {
        return mutex.withLock {
            samples.addLast(timeMs to totalBytes)

            while (samples.size > 2 && (timeMs - samples.first().first) > windowDurationMs) {
                samples.removeFirst()
            }

            if (samples.size < 2) return@withLock 0L

            val oldest = samples.first()
            val newest = samples.last()
            val durationMs = newest.first - oldest.first
            val bytesDelta = newest.second - oldest.second

            if (durationMs > 100L && bytesDelta >= 0L) {
                (bytesDelta * 1000L) / durationMs
            } else {
                0L
            }
        }
    }

    suspend fun reset() {
        mutex.withLock { samples.clear() }
    }
}
