package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

actual suspend fun resolvePlatformStream(url: String): String? =
    runYtDlp(ytDlpRuntimeArgs() + listOf("-g", "--no-warnings", "--no-playlist", "--", url))

@Volatile
var lastYtDlpError: String? = null

actual fun lastMediaInspectionError(): String? = lastYtDlpError

internal suspend fun runYtDlp(args: List<String>, timeoutSec: Long = 25L): String? {
    val executable = getExistingYtDlpBinary() ?: return null
    val secrets = YtDlpSecretArgs.split(listOf(executable) + args)
    return try {
        val started = TimeSource.Monotonic.markNow()
        val result = winRunProcess(
            secrets.command, environment = ytDlpEnvironment, mergeStderr = true, timeoutMs = timeoutSec * 1000
        )
        if (result.timedOut) {
            lastYtDlpError = "yt-dlp took longer than $timeoutSec s"
            AppLog.e("yt-dlp timed out after $timeoutSec s: ${args.firstOrNull { it.startsWith("-") && it.length == 2 } ?: ""}")
            return null
        }
        AppLog.d { "yt-dlp finished in ${started.elapsedNow().inWholeMilliseconds} ms" }
        val output = result.stdout.trim()
        if (result.exitCode == 0 && output.isNotBlank()) {
            output
        } else {
            val err = output.lineSequence().lastOrNull { it.contains("ERROR", ignoreCase = true) }
                ?: output.lineSequence().lastOrNull { it.isNotBlank() }
            lastYtDlpError = err?.take(400)
            AppLog.e("yt-dlp failed (exit ${result.exitCode}): ${err ?: "no output"}")
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        lastYtDlpError = e.message
        AppLog.e("yt-dlp could not run: ${e.message}")
        null
    } finally {
        secrets.deleteNow()
    }
}

actual suspend fun inspectPlatformPlaylist(
    url: String,
    headers: Map<String, String>
): MediaPlaylist? {
    getExistingYtDlpBinary() ?: return null
    val listUrl = normalizePlaylistUrl(url)
    val output = runYtDlp(
        ytDlpRuntimeArgs() + listOf("--flat-playlist", "-J", "--no-warnings", "--yes-playlist") +
            ytDlpHeaderArgs(headers, listUrl) + listOf("--", listUrl),
        timeoutSec = 85L
    ) ?: return null
    return parsePlaylistJson(output)
}

actual suspend fun runYtDlpJson(
    url: String,
    selector: String,
    headers: Map<String, String>
): String? = withContext(Dispatchers.IO) {
    getExistingYtDlpBinary() ?: return@withContext null
    runYtDlp(
        ytDlpRuntimeArgs() + listOf("-j", "--no-warnings", "--no-playlist", "-f", selector) +
            ytDlpHeaderArgs(headers, url) + listOf("--", url),
        timeoutSec = 40L
    )
}
