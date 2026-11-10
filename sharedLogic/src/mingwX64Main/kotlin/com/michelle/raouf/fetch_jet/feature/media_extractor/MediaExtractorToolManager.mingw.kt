@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.network.AppHttp
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WIN_CREATE_ALWAYS
import com.michelle.raouf.fetch_jet.platform.win.WinSha256
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winCloseFile
import com.michelle.raouf.fetch_jet.platform.win.winDeleteFile
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winExists
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winMove
import com.michelle.raouf.fetch_jet.platform.win.winOpenFile
import com.michelle.raouf.fetch_jet.platform.win.winRunProcess
import com.michelle.raouf.fetch_jet.platform.win.winUnzip
import com.michelle.raouf.fetch_jet.platform.win.winUnzipEntryTo
import com.michelle.raouf.fetch_jet.platform.win.winWriteAt
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
private const val YT_DLP_ASSET = "yt-dlp_win.zip"
private const val FFMPEG_ASSET = "win-64"
private const val FFMPEG_SHA256 = "d1124593b7453fc54dd90ca3819dc82c22ffa957937f33dd650082f1a495b10e"
private const val DENO_ASSET = "deno-x86_64-pc-windows-msvc.zip"

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
    val digest = WinSha256()
    val handle = winOpenFile(tmp, write = true, disposition = WIN_CREATE_ALWAYS)
        ?: throw IllegalStateException("cannot write $tmp")
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
                digest.update(buffer, 0, read)
                if (!winWriteAt(handle, total, buffer, 0, read)) throw IllegalStateException("cannot write $tmp")
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
        winCloseFile(handle)
        closed = true
        if (!winMove(tmp, dest, replace = true)) throw IllegalStateException("cannot save $dest")
        return digest.digest().toHexString()
    } finally {
        digest.close()
        if (!closed) {
            winCloseFile(handle)
            winDeleteFile(tmp)
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
    val tmp = "$toolsDir\\$fileName.tmp"
    val found = winUnzipEntryTo(zipFile, tmp) { it.name.substringAfterLast('/').substringAfterLast('\\') == fileName }
    if (!found) return false
    if (!winMove(tmp, "$toolsDir\\$fileName", replace = true)) {
        winDeleteFile(tmp)
        throw IllegalStateException("cannot replace $fileName (is it running?)")
    }
    return true
}

private suspend fun installedVersion(binary: String, timeoutSec: Long = 30): String? = try {
    val result = winRunProcess(
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
    val zip = "$toolsDir\\$YT_DLP_ASSET.download"
    val live = ytDlpFolder()
    val fresh = "$toolsDir\\yt-dlp-dir.new"
    val old = "$toolsDir\\yt-dlp-dir.old"
    try {
        val actual = downloadTo("https://github.com/yt-dlp/yt-dlp/releases/latest/download/$YT_DLP_ASSET", zip) {
            onProgress(ToolDownloadProgress("yt-dlp", it))
        }
        if (expected != actual) {
            throw IllegalStateException("yt-dlp checksum mismatch (expected $expected, got $actual)")
        }
        winDeleteRecursively(fresh)
        winUnzip(zip, fresh)
        val binary = "$fresh\\yt-dlp.exe"
        check(winFileInfo(binary)?.isDirectory == false) { "yt-dlp program missing in $YT_DLP_ASSET" }
        check(installedVersion(binary, timeoutSec = 180) != null) { "the new yt-dlp does not start" }
        winDeleteRecursively(old)
        if (winExists(live) && !winMove(live, old, replace = false)) {
            throw IllegalStateException("yt-dlp is in use, it will be updated at the next start")
        }
        if (!winMove(fresh, live, replace = false)) {
            if (winExists(old)) winMove(old, live, replace = false)
            throw IllegalStateException("could not put the new yt-dlp in place")
        }
        winDeleteRecursively(old)
        winDeleteFile("$toolsDir\\yt-dlp.exe")
    } finally {
        winDeleteFile(zip)
        winDeleteRecursively(fresh)
    }
}

private suspend fun installFfmpeg(toolsDir: String, onProgress: (ToolDownloadProgress) -> Unit) {
    val url = "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-$FFMPEG_ASSET.zip"
    val zip = "$toolsDir\\ffmpeg.zip"
    try {
        val actual = downloadTo(url, zip) { onProgress(ToolDownloadProgress("FFmpeg", it)) }
        if (actual != FFMPEG_SHA256) {
            throw IllegalStateException("ffmpeg checksum mismatch (expected $FFMPEG_SHA256, got $actual)")
        }
        if (!unzipOne(zip, "ffmpeg.exe", toolsDir)) {
            AppLog.e("ffmpeg archive did not contain ffmpeg.exe; yt-dlp will work without merging (720p limit)")
        }
    } finally {
        winDeleteFile(zip)
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
    val zip = "$toolsDir\\deno.zip"
    try {
        val actual = downloadTo(base, zip) { onProgress(ToolDownloadProgress("deno", it)) }
        if (expected != actual) {
            throw IllegalStateException("deno checksum mismatch (expected $expected, got $actual)")
        }
        if (!unzipOne(zip, "deno.exe", toolsDir)) throw IllegalStateException("$DENO_ASSET did not contain deno.exe")
    } finally {
        winDeleteFile(zip)
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
        if (!WinApp.automaticDownloads) return@withContext false
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
