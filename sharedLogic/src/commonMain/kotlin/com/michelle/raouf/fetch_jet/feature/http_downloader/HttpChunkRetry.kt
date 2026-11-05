package com.michelle.raouf.fetch_jet.feature.http_downloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal suspend fun <T> fetchWithRetry(
    maxRetries: Int = 6,
    initialDelayMs: Long = 1000L,
    maxDelayMs: Long = 30_000L,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries - 1) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RangeNotHonouredException) {
            throw e // retrying will not make the server honour Range
        } catch (e: Exception) {
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }
    return block()
}

/** The server answered a ranged request with 200: ranges are not supported for this URL. */
internal class RangeNotHonouredException(message: String) : Exception(message)

internal fun shortError(e: Exception): String =
    (e.message ?: e::class.simpleName ?: "error").substringBefore('\n').take(60)
