package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.filesystem.WinPaths
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteFile
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winIsDirectory
import com.michelle.raouf.fetch_jet.platform.win.winRandomBytes
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import com.michelle.raouf.fetch_jet.platform.win.winWriteText
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private fun isFile(path: String): Boolean = winFileInfo(path)?.isDirectory == false

fun getToolsDir(): String {
    val dir = WinPaths.settings() + "\\tools"
    if (!winIsDirectory(dir)) winCreateDirectories(dir)
    return dir
}

internal fun ytDlpFolder(): String = getToolsDir() + "\\yt-dlp-dir"

internal fun ytDlpFolderBinary(): String? = (ytDlpFolder() + "\\yt-dlp.exe").takeIf { isFile(it) }

fun getExistingYtDlpBinary(): String? =
    ytDlpFolderBinary() ?: (getToolsDir() + "\\yt-dlp.exe").takeIf { isFile(it) }

fun getExistingFfmpegBinary(): String? = (getToolsDir() + "\\ffmpeg.exe").takeIf { isFile(it) }

fun getExistingDenoBinary(): String? = (getToolsDir() + "\\deno.exe").takeIf { isFile(it) }

fun getYtDlpCacheDir(): String {
    val dir = WinPaths.settings() + "\\cache\\yt-dlp"
    if (!winIsDirectory(dir)) winCreateDirectories(dir)
    return dir
}

fun ytDlpRuntimeArgs(): List<String> = buildList {
    add("--js-runtimes")
    add(getExistingDenoBinary()?.let { "deno:$it" } ?: "deno")
    add("--js-runtimes")
    add("node")
    add("--cache-dir")
    add(getYtDlpCacheDir())
    addAll(NetworkSettings.ytDlpArgs())
}

internal val ytDlpEnvironment: Map<String, String?> = mapOf("PYTHONIOENCODING" to "utf-8", "PYTHONUTF8" to "1")

@Volatile
private var systemFfmpegCache: Boolean? = null

fun isSystemFfmpegInstalled(): Boolean {
    systemFfmpegCache?.let { return it }
    val result = try {
        val run = runBlocking { winRunProcess(listOf("ffmpeg", "-version"), mergeStderr = true, timeoutMs = 2000, keepOutput = false) }
        !run.timedOut && run.exitCode == 0
    } catch (_: Throwable) {
        false
    }
    systemFfmpegCache = result
    return result
}

actual suspend fun runLocalFfmpeg(args: List<String>): Boolean = withContext(Dispatchers.IO) {
    val bin = getExistingFfmpegBinary()
        ?: (if (isSystemFfmpegInstalled()) "ffmpeg" else null)
        ?: return@withContext false
    try {
        val out = StringBuilder()
        val result = winRunProcess(
            listOf(bin) + args, mergeStderr = true, timeoutMs = 30L * 60 * 1000, keepOutput = false,
            onStdoutLine = { if (out.length < 4000) out.appendLine(it) }
        )
        if (result.timedOut) return@withContext false
        if (result.exitCode != 0) {
            AppLog.e("ffmpeg (local join) exit ${result.exitCode}: ${out.toString().trim().takeLast(600)}")
        }
        result.exitCode == 0
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.e("ffmpeg (local join) could not run: ${e.message}")
        false
    }
}

internal object YtDlpSecretArgs {
    private val SECRET_HEADERS = setOf("cookie", "authorization", "proxy-authorization")

    class Split(val command: List<String>, private val file: String?) {
        fun deleteNow() {
            if (file != null) winDeleteFile(file)
        }
    }

    fun split(command: List<String>): Split {
        val keep = mutableListOf<String>()
        val secret = mutableListOf<String>()
        var i = 0
        while (i < command.size) {
            val arg = command[i]
            if (i > 0 && arg == "--") {
                keep += command.subList(i, command.size)
                break
            }
            val value = command.getOrNull(i + 1)
            if (i > 0 && value != null && isSecret(arg, value)) {
                secret += arg
                secret += value
                i += 2
                continue
            }
            keep += arg
            i++
        }
        if (secret.isEmpty()) return Split(command, null)
        val file = try {
            val path = PathResolver().getTempDirectory() + "\\fetchjet-ytdlp-" + winRandomBytes(8).toHexString() + ".conf"
            val text = secret.chunked(2).joinToString("\n") { (k, v) -> "$k ${quote(v)}" } + "\n"
            check(winWriteText(path, text))
            path
        } catch (_: Throwable) {
            return Split(command, null)
        }
        return Split(listOf(keep.first(), "--config-locations", file) + keep.drop(1), file)
    }

    private fun isSecret(arg: String, value: String): Boolean = when (arg) {
        "--proxy" -> value.substringBefore('/', "").isNotEmpty() && '@' in value
        "--add-headers", "--add-header" -> value.substringBefore(':').trim().lowercase() in SECRET_HEADERS
        else -> false
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
}
