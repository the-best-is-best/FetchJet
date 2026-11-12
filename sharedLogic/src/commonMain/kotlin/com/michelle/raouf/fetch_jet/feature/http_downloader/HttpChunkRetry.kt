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
        } catch (e: FileChangedException) {
            throw e // the file is another file now: retrying cannot help
        } catch (e: ChecksumMismatchException) {
            throw e
        } catch (e: Exception) {
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }
    return block()
}

/** The server answered a ranged request with 200: ranges are not supported for this URL. */
internal class RangeNotHonouredException(message: String) : Exception(message)

/** A ranged request carried If-Range and got the whole file: the server's file is not the one the parts came from. */
internal class FullResponseToConditionalRange : Exception("200 to a request with If-Range")

/** The connection of a piece crawls at the end of the download: drop it, a new one takes the piece. */
internal class SlowConnectionException : Exception("slow connection at the end")

/** The file on the server changed while (or since) the parts were downloaded. */
internal class FileChangedException(message: String) : Exception(message)

internal fun shortError(e: Exception): String =
    (e.message ?: e::class.simpleName ?: "error").substringBefore('\n').take(60)
