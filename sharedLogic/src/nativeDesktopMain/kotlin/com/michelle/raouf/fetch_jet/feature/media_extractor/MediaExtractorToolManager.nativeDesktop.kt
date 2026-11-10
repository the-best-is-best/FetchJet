package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.network.AppHttp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopZip
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import com.tbib.kmp_localization.LocalizationManager
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.HashingSink
import okio.blackholeSink

actual fun getMediaExtractorToolStatus(): String {
    val ytdlp = getExistingYtDlpBinary()
    val ffmpeg = getExistingFfmpegBinary()
    return when {
        ytdlp != null && (ffmpeg != null || isSystemFfmpegInstalled()) -> LocalizationManager.Strings.sysToolsReadyYtDlpFfmpeg
        ytdlp != null -> LocalizationManager.Strings.sysToolsReadyYtDlpNoFfmpeg
        else -> LocalizationManager.Strings.sysToolsNotInstalled
    }
}

actual fun getJsRuntimeStatus(): JsRuntimeStatus =
    JsRuntimeStatus(name = "deno", isReady = getExistingDenoBinary() != null)

actual fun isMediaExtractorToolInstalled(): Boolean = getExistingYtDlpBinary() != null

actual fun isFfmpegToolInstalled(): Boolean = getExistingFfmpegBinary() != null || isSystemFfmpegInstalled()

actual fun getMediaToolsDirectory(): String = getToolsDir()

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val READ_TIMEOUT_MS = 60_000L
private const val MAX_TOOL_BYTES = 400L * 1024 * 1024
private val YT_DLP_ASSET: String get() = if (onWindows) "yt-dlp_win.zip" else "yt-dlp_linux.zip"
private val FFMPEG_ASSET: String get() = if (onWindows) "win-64" else "linux-64"
private val FFMPEG_SHA256: String
    get() = if (onWindows) "d1124593b7453fc54dd90ca3819dc82c22ffa957937f33dd650082f1a495b10e"
    else "4348301b0d5e18174925e2022da1823aebbdb07282bbe9adb64b2485e1ef2df7"
private val DENO_ASSET: String get() = if (onWindows) "deno-x86_64-pc-windows-msvc.zip" else "deno-x86_64-unknown-linux-gnu.zip"

private suspend fun readText(url: String): String {
    val response = AppHttp.client.get(url) {
        timeout {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = READ_TIMEOUT_MS
        }
        header(HttpHeaders.UserAgent, "FetchJet")
    }
    if (response.status.value !in 200..299) throw IllegalStateException("HTTP ${response.status.value} for $url")
    return response.bodyAsText()
}

private suspend fun downloadTo(url: String, dest: String, onProgress: (Float?) -> Unit = {}): String {
    val tmp = "$dest.tmp"
    val out = DesktopOs.openFile(tmp, write = true) ?: throw IllegalStateException("cannot write $tmp")
    val digest = HashingSink.sha256(blackholeSink())
    val pending = Buffer()
    var offset = 0L
    var closed = false
    try {
        AppHttp.client.prepareGet(url) {
            timeout {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = READ_TIMEOUT_MS
            }
            header(HttpHeaders.UserAgent, "FetchJet")
        }.execute { response ->
            if (response.status.value !in 200..299) {
                throw IllegalStateException("HTTP ${response.status.value} for $url")
            }
            val length = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            var lastReported = -1
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                if (total + read > MAX_TOOL_BYTES) {
                    throw IllegalStateException("Download exceeds ${MAX_TOOL_BYTES / 1024 / 1024} MB")
                }
                if (!out.write(offset, buffer, 0, read)) throw IllegalStateException("cannot write $tmp")
                offset += read
                pending.write(buffer, 0, read)
                digest.write(pending, read.toLong())
                total += read
                if (length > 0) {
                    val percent = (total * 100 / length).toInt()
                    if (percent != lastReported) {
                        lastReported = percent
                        onProgress(percent / 100f)
                    }
                } else {
                    onProgress(null)
                }
            }
            if (length > 0 && total != length) throw IllegalStateException("download interrupted")
        }
        out.close()
        closed = true
        if (!DesktopOs.move(tmp, dest, replace = true)) throw IllegalStateException("cannot save $dest")
        return digest.hash.hex()
    } finally {
        if (!closed) {
            out.close()
            DesktopOs.deleteFile(tmp)
        }
    }
}

private suspend fun fetchPublishedYtDlpSha256(assetName: String): String? = try {
    readText("https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS")
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.endsWith("  $assetName") || it.endsWith(" $assetName") }
        ?.substringBefore(' ')?.lowercase()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

private fun unzipOne(zipFile: String, fileName: String, toolsDir: String): Boolean {
    val tmp = DesktopOs.join(toolsDir, "$fileName.tmp")
    val found = DesktopZip.unzipEntryTo(zipFile, tmp) { it.name.substringAfterLast('/') == fileName }
    if (!found) return false
    DesktopOs.makeExecutable(tmp)
    if (!DesktopOs.move(tmp, DesktopOs.join(toolsDir, fileName), replace = true)) {
        DesktopOs.deleteFile(tmp)
        throw IllegalStateException("cannot replace $fileName (is it running?)")
    }
    return true
}

private suspend fun installedVersion(binary: String, timeoutSec: Long = 30): String? = try {
    val result = DesktopOs.runProcess(
        listOf(binary, "--version"), environment = ytDlpEnvironment, mergeStderr = true, timeoutMs = timeoutSec * 1000
    )
    if (result.timedOut) null else Regex("\\d+(\\.\\d+)+").find(result.stdout)?.value
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

private fun sameVersion(installed: String?, latestTag: String?): Boolean =
    latestTag == null || installed == null || installed == latestTag.removePrefix("v")

private suspend fun installYtDlp(toolsDir: String, onProgress: (ToolDownloadProgress) -> Unit) {
    val expected = fetchPublishedYtDlpSha256(YT_DLP_ASSET)
        ?: throw IllegalStateException("yt-dlp checksum list not available, try again later")
    val zip = DesktopOs.join(toolsDir, "$YT_DLP_ASSET.download")
    val live = ytDlpFolder()
    val fresh = DesktopOs.join(toolsDir, "yt-dlp-dir.new")
    val old = DesktopOs.join(toolsDir, "yt-dlp-dir.old")
    try {
        val actual = downloadTo("https://github.com/yt-dlp/yt-dlp/releases/latest/download/$YT_DLP_ASSET", zip) {
            onProgress(ToolDownloadProgress("yt-dlp", it))
        }
        if (expected != actual) {
            throw IllegalStateException("yt-dlp checksum mismatch (expected $expected, got $actual)")
        }
        DesktopOs.deleteRecursively(fresh)
        DesktopZip.unzip(zip, fresh)
        val binary = DesktopOs.join(fresh, ytDlpProgramName())
        check(DesktopOs.fileInfo(binary)?.isDirectory == false) { "yt-dlp program missing in $YT_DLP_ASSET" }
        DesktopOs.makeExecutable(binary)
        check(installedVersion(binary, timeoutSec = 180) != null) { "the new yt-dlp does not start" }
        DesktopOs.deleteRecursively(old)
        if (DesktopOs.exists(live) && !DesktopOs.move(live, old, replace = false)) {
            throw IllegalStateException("yt-dlp is in use, it will be updated at the next start")
        }
        if (!DesktopOs.move(fresh, live, replace = false)) {
            if (DesktopOs.exists(old)) DesktopOs.move(old, live, replace = false)
            throw IllegalStateException("could not put the new yt-dlp in place")
        }
        DesktopOs.deleteRecursively(old)
        DesktopOs.deleteFile(DesktopOs.join(toolsDir, programName("yt-dlp")))
    } finally {
        DesktopOs.deleteFile(zip)
        DesktopOs.deleteRecursively(fresh)
    }
}

private suspend fun installFfmpeg(toolsDir: String, onProgress: (ToolDownloadProgress) -> Unit) {
    val url = "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-$FFMPEG_ASSET.zip"
    val zip = DesktopOs.join(toolsDir, "ffmpeg.zip")
    try {
        val actual = downloadTo(url, zip) { onProgress(ToolDownloadProgress("FFmpeg", it)) }
        if (actual != FFMPEG_SHA256) {
            throw IllegalStateException("ffmpeg checksum mismatch (expected $FFMPEG_SHA256, got $actual)")
        }
        if (!unzipOne(zip, programName("ffmpeg"), toolsDir)) {
            AppLog.e("ffmpeg archive did not contain ${programName("ffmpeg")}; yt-dlp will work without merging (720p limit)")
        }
    } finally {
        DesktopOs.deleteFile(zip)
    }
}

private suspend fun installDeno(toolsDir: String, onProgress: (ToolDownloadProgress) -> Unit) {
    val base = "https://github.com/denoland/deno/releases/latest/download/$DENO_ASSET"
    val expected = try {
        Regex("\\b[0-9a-fA-F]{64}\\b").find(readText("$base.sha256sum"))?.value?.lowercase()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    } ?: throw IllegalStateException("deno checksum not available, try again later")
    val zip = DesktopOs.join(toolsDir, "deno.zip")
    try {
        val actual = downloadTo(base, zip) { onProgress(ToolDownloadProgress("deno", it)) }
        if (expected != actual) {
            throw IllegalStateException("deno checksum mismatch (expected $expected, got $actual)")
        }
        if (!unzipOne(zip, programName("deno"), toolsDir)) throw IllegalStateException("$DENO_ASSET did not contain ${programName("deno")}")
    } finally {
        DesktopOs.deleteFile(zip)
    }
}

actual suspend fun downloadMediaExtractorTool(onProgress: (ToolDownloadProgress) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val toolsDir = getToolsDir()
            installYtDlp(toolsDir, onProgress)
            installFfmpeg(toolsDir, onProgress)
            try {
                installDeno(toolsDir, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("deno could not be installed: ${e.message}")
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("media tools could not be installed: ${e.stackTraceToString()}")
            false
        }
    }

actual suspend fun autoUpdateMediaTools(onProgress: (ToolDownloadProgress) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        if (!DesktopApp.automaticDownloads) return@withContext false
        val toolsDir = getToolsDir()
        var changed = false
        suspend fun step(name: String, needed: suspend () -> Boolean, install: suspend () -> Unit) {
            try {
                if (needed()) {
                    install()
                    changed = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("$name auto-update failed: ${e.message}")
            }
        }

        val ytDlpTag = latestGitHubReleaseTag("yt-dlp/yt-dlp")
        val denoTag = latestGitHubReleaseTag("denoland/deno")
        step("yt-dlp", {
            val bin = getExistingYtDlpBinary()
            bin == null || ytDlpFolderBinary() == null || !sameVersion(installedVersion(bin), ytDlpTag)
        }) { installYtDlp(toolsDir, onProgress) }
        step("ffmpeg", { getExistingFfmpegBinary() == null && !isSystemFfmpegInstalled() }) {
            installFfmpeg(toolsDir, onProgress)
        }
        step("deno", {
            val bin = getExistingDenoBinary()
            bin == null || !sameVersion(installedVersion(bin), denoTag)
        }) { installDeno(toolsDir, onProgress) }
        changed
    }

internal actual val toolUpdateSwapsAtomically: Boolean = true
