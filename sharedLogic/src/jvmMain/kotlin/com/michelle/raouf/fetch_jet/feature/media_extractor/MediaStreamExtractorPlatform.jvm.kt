package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

actual suspend fun resolvePlatformStream(url: String): String? {

    return runYtDlp(
        ytDlpRuntimeArgs() + listOf(
            "-g",
            "--no-warnings",
            "--no-playlist",
            "--",
            url
        )
    )

}

/** Last "ERROR:" line yt-dlp printed (inspection), shown in the Add dialog when it fails. */
@Volatile
var lastYtDlpError: String? = null

actual fun lastMediaInspectionError(): String? = lastYtDlpError

internal fun runYtDlp(args: List<String>, timeoutSec: Long = 25L): String? {
    return try {
        val executableFile = getExistingYtDlpBinary() ?: return null
        val command = mutableListOf<String>()
        // Run yt-dlp directly (never through `cmd.exe /c`: cmd re-parses `&`, `|`, `^`
        // and `>` in the URL, which is a command-injection hole reachable from any web page).
        command.add(executableFile.absolutePath)
        command.addAll(args)
        val secrets = YtDlpSecretArgs.split(command)
        val process = try {
            ProcessBuilder(secrets.command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            secrets.deleteNow(); throw e
        }
        secrets.deleteWhenDone(process)
        val outputBuilder = StringBuilder()
        val readerThread = Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.append(line ?: "").append("\n")
                    }
                }
            } catch (_: Exception) {
            }
        }
        readerThread.start()
        val startedAt = System.currentTimeMillis()
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            readerThread.interrupt()
            lastYtDlpError = "yt-dlp took longer than $timeoutSec s"
            AppLog.e("yt-dlp timed out after $timeoutSec s: ${args.firstOrNull { it.startsWith("-") && it.length == 2 } ?: ""}")
            return null
        }
        AppLog.d { "yt-dlp finished in ${System.currentTimeMillis() - startedAt} ms" }
        readerThread.join(2000)
        val output = outputBuilder.toString().trim()
        if (process.exitValue() == 0 && output.isNotBlank()) {
            output
        } else {
            // Keep the reason (yt-dlp prints "ERROR: ..." on its output) for the Add dialog.
            val err = output.lineSequence().lastOrNull { it.contains("ERROR", ignoreCase = true) }
                ?: output.lineSequence().lastOrNull { it.isNotBlank() }
            lastYtDlpError = err?.take(400)
            AppLog.e("yt-dlp failed (exit ${process.exitValue()}): ${err ?: "no output"}")
            null
        }
    } catch (e: Exception) {
        lastYtDlpError = e.message
        AppLog.e("yt-dlp could not run: ${e.message}")
        null
    }
}

actual suspend fun inspectPlatformPlaylist(url: String): MediaPlaylist? {
    getExistingYtDlpBinary() ?: return null
    val output = runYtDlp(
        ytDlpRuntimeArgs() + listOf(
            "--flat-playlist",
            "-J",
            "--no-warnings",
            "--yes-playlist",
            "--",
            normalizePlaylistUrl(url)
        ),
        timeoutSec = 85L // a channel with hundreds of videos takes a while
    ) ?: return null
    return parsePlaylistJson(output)
}


actual suspend fun runYtDlpJson(
    url: String,
    selector: String,
    headers: Map<String, String>
): String? =
    withContext(Dispatchers.IO) {
        getExistingYtDlpBinary() ?: return@withContext null
        runYtDlp(
            ytDlpRuntimeArgs() + listOf(
                "-j", "--no-warnings", "--no-playlist", "-f", selector
            ) + ytDlpHeaderArgs(headers, url) + listOf("--", url),
            timeoutSec = 40L
        )
    }
