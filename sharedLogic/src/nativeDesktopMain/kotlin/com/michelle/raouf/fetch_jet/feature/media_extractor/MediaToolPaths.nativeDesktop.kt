package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal val onWindows: Boolean get() = DesktopOs.name == "Windows"

internal fun programName(base: String): String = if (onWindows) "$base.exe" else base

internal fun ytDlpProgramName(): String = if (onWindows) "yt-dlp.exe" else "yt-dlp_linux"

private fun isFile(path: String): Boolean = DesktopOs.fileInfo(path)?.isDirectory == false

internal fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun getToolsDir(): String {
    val dir = DesktopOs.join(DesktopPaths.settings(), "tools")
    if (!DesktopOs.fileInfo(dir).let { it != null && it.isDirectory }) DesktopOs.createDirectories(dir)
    return dir
}

internal fun ytDlpFolder(): String = DesktopOs.join(getToolsDir(), "yt-dlp-dir")

internal fun ytDlpFolderBinary(): String? = DesktopOs.join(ytDlpFolder(), ytDlpProgramName()).takeIf { isFile(it) }

fun getExistingYtDlpBinary(): String? =
    ytDlpFolderBinary() ?: DesktopOs.join(getToolsDir(), programName("yt-dlp")).takeIf { isFile(it) }

fun getExistingFfmpegBinary(): String? = DesktopOs.join(getToolsDir(), programName("ffmpeg")).takeIf { isFile(it) }

fun getExistingDenoBinary(): String? = DesktopOs.join(getToolsDir(), programName("deno")).takeIf { isFile(it) }

fun getYtDlpCacheDir(): String {
    val dir = DesktopOs.join(DesktopOs.join(DesktopPaths.settings(), "cache"), "yt-dlp")
    if (DesktopOs.fileInfo(dir)?.isDirectory != true) DesktopOs.createDirectories(dir)
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
        val run = runBlocking {
            DesktopOs.runProcess(listOf("ffmpeg", "-version"), mergeStderr = true, timeoutMs = 2000, keepOutput = false)
        }
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
        val result = DesktopOs.runProcess(
            listOf(bin) + args, mergeStderr = true, timeoutMs = 30L * 60 * 1000, keepOutput = false,
            onStdoutLine = {
                out.appendLine(it)
                if (out.length > 8000) out.deleteRange(0, out.length - 4000)
            }
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
            if (file != null) DesktopOs.deleteFile(file)
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
            val path = DesktopOs.join(PathResolver().getTempDirectory(), "fetchjet-ytdlp-" + DesktopOs.randomBytes(8).hex() + ".conf")
            val text = secret.chunked(2).joinToString("\n") { (k, v) -> "$k ${quote(v)}" } + "\n"
            check(DesktopOs.writeText(path, text))
            DesktopOs.restrictToOwner(path)
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
