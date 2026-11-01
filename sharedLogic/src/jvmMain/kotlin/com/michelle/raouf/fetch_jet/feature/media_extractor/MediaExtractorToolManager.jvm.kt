package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

actual fun getMediaExtractorToolStatus(): String {
    val ytdlp = getExistingYtDlpBinary()
    val ffmpeg = getExistingFfmpegBinary()

    return when {
        ytdlp != null && (ffmpeg != null || isSystemFfmpegInstalled()) -> "Ready: yt-dlp & ffmpeg installed (Full 1080p+ support)"
        ytdlp != null -> "Ready: yt-dlp installed (No ffmpeg - 720p limit)"
        else -> "Not Installed. Click 'Download Tools' below."
    }
}

actual fun getJsRuntimeStatus(): JsRuntimeStatus =
    JsRuntimeStatus(name = "deno", isReady = getExistingDenoBinary() != null)

actual fun isMediaExtractorToolInstalled(): Boolean {
    return getExistingYtDlpBinary() != null
}

actual fun isFfmpegToolInstalled(): Boolean {
    return getExistingFfmpegBinary() != null || isSystemFfmpegInstalled()
}

actual fun getMediaToolsDirectory(): String {
    return getToolsDir().absolutePath
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000
private const val MAX_TOOL_BYTES = 400L * 1024 * 1024

private val osName = System.getProperty("os.name").lowercase()
private val isWin = osName.contains("win")
private val isMac = osName.contains("mac")
private val isArm =
    System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }

/** Opens an HTTPS connection with timeouts and a status check (a 404 page is not a binary). */
private fun openConnection(url: String): HttpURLConnection {
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    conn.connectTimeout = CONNECT_TIMEOUT_MS
    conn.readTimeout = READ_TIMEOUT_MS
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("User-Agent", "FetchJet")
    if (conn.responseCode !in 200..299) {
        throw IllegalStateException("HTTP ${conn.responseCode} for $url")
    }
    return conn
}

private fun readText(url: String): String =
    openConnection(url).inputStream.bufferedReader().use { it.readText() }

/** Streams [url] into [dest] via a temp file, returning the SHA-256 of the bytes written. */
private fun downloadTo(url: String, dest: File, onProgress: (Float?) -> Unit = {}): String {
    val tmp = File(dest.parentFile, dest.name + ".tmp")
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    val conn = openConnection(url)
    val length = conn.contentLengthLong
    var lastReported = -1
    conn.inputStream.use { input ->
        tmp.outputStream().use { output ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_TOOL_BYTES) throw IllegalStateException("Download exceeds ${MAX_TOOL_BYTES / 1024 / 1024} MB")
                digest.update(buf, 0, n)
                output.write(buf, 0, n)
                if (length > 0) {
                    val pct = (total * 100 / length).toInt()
                    if (pct != lastReported) {
                        lastReported = pct
                        onProgress(pct / 100f)
                    }
                } else {
                    onProgress(null)
                }
            }
        }
    }
    Files.move(
        tmp.toPath(),
        dest.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
    )
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Reads yt-dlp's published SHA2-256SUMS and returns the hash for [assetName], or null. */
private fun fetchPublishedYtDlpSha256(assetName: String): String? {
    return try {
        readText("https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS")
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith("  $assetName") || it.endsWith(" $assetName") }
            ?.substringBefore(' ')?.lowercase()
    } catch (_: Exception) {
        null
    }
}

private fun unzipOne(zipFile: File, fileName: String, toolsDir: File): Boolean {
    ZipInputStream(zipFile.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            // Only the one expected file name; ignore directories and anything with a path
            // (zip-slip guard).
            val plainName = entry.name.substringAfterLast('/').substringAfterLast('\\')
            if (!entry.isDirectory && plainName == fileName) {
                val tmp = File(toolsDir, "$fileName.tmp")
                tmp.outputStream().use { os -> zis.copyTo(os) }
                val outFile = File(toolsDir, fileName)
                Files.move(
                    tmp.toPath(),
                    outFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
                outFile.setExecutable(true)
                return true
            }
            entry = zis.nextEntry
        }
    }
    return false
}

private fun installedVersion(binary: File): String? = try {
    val process = ProcessBuilder(binary.absolutePath, "--version").redirectErrorStream(true).start()
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        null
    } else {
        process.inputStream.bufferedReader().use { it.readText() }
            .let { Regex("\\d+(\\.\\d+)+").find(it)?.value }
    }
} catch (_: Exception) {
    null
}

private fun sameVersion(installed: String?, latestTag: String?): Boolean =
    latestTag == null || installed == latestTag.removePrefix("v")

private fun installYtDlp(toolsDir: File, onProgress: (ToolDownloadProgress) -> Unit) {
    val assetName = when {
        isWin -> "yt-dlp.exe"
        isMac -> "yt-dlp_macos"
        else -> "yt-dlp_linux"
    }
    val ytDlpFile = File(toolsDir, if (isWin) "yt-dlp.exe" else "yt-dlp")
    val expected = fetchPublishedYtDlpSha256(assetName)
    val staging = File(toolsDir, ytDlpFile.name + ".download")
    val actual = downloadTo(
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$assetName",
        staging
    ) { onProgress(ToolDownloadProgress("yt-dlp", it)) }
    if (expected != null && expected != actual) {
        staging.delete()
        throw IllegalStateException("yt-dlp checksum mismatch (expected $expected, got $actual)")
    }
    Files.move(
        staging.toPath(),
        ytDlpFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
    )
    ytDlpFile.setExecutable(true)
}

private fun installFfmpeg(toolsDir: File, onProgress: (ToolDownloadProgress) -> Unit) {
    val ffmpegZipUrl = when {
        isWin -> "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-win-64.zip"
        isMac -> "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-osx-64.zip"
        else -> "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-linux-64.zip"
    }
    val zipFile = File(toolsDir, "ffmpeg.zip")
    try {
        downloadTo(ffmpegZipUrl, zipFile) { onProgress(ToolDownloadProgress("FFmpeg", it)) }
        val ffmpegFileName = if (isWin) "ffmpeg.exe" else "ffmpeg"
        if (!unzipOne(zipFile, ffmpegFileName, toolsDir)) {
            System.err.println("ffmpeg archive did not contain $ffmpegFileName; yt-dlp will work without merging (720p limit)")
        }
    } finally {
        zipFile.delete()
    }
}

private fun installDeno(toolsDir: File, onProgress: (ToolDownloadProgress) -> Unit) {
    val asset = when {
        isWin -> "deno-x86_64-pc-windows-msvc.zip"
        isMac && isArm -> "deno-aarch64-apple-darwin.zip"
        isMac -> "deno-x86_64-apple-darwin.zip"
        isArm -> "deno-aarch64-unknown-linux-gnu.zip"
        else -> "deno-x86_64-unknown-linux-gnu.zip"
    }
    val base = "https://github.com/denoland/deno/releases/latest/download/$asset"
    val expected = try {
        Regex("\\b[0-9a-fA-F]{64}\\b").find(readText("$base.sha256sum"))?.value?.lowercase()
    } catch (_: Exception) {
        null
    }
    val zipFile = File(toolsDir, "deno.zip")
    try {
        val actual = downloadTo(base, zipFile) { onProgress(ToolDownloadProgress("deno", it)) }
        if (expected != null && expected != actual) {
            throw IllegalStateException("deno checksum mismatch (expected $expected, got $actual)")
        }
        val name = if (isWin) "deno.exe" else "deno"
        if (!unzipOne(
                zipFile,
                name,
                toolsDir
            )
        ) throw IllegalStateException("$asset did not contain $name")
    } finally {
        zipFile.delete()
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
            } catch (e: Exception) {
                System.err.println("deno could not be installed: ${e.message}")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

actual suspend fun autoUpdateMediaTools(onProgress: (ToolDownloadProgress) -> Unit): Boolean =
    withContext(Dispatchers.IO) {
        val toolsDir = getToolsDir()
        var changed = false
        fun step(name: String, needed: () -> Boolean, install: () -> Unit) {
            try {
                if (needed()) {
                    install()
                    changed = true
                }
            } catch (e: Exception) {
                System.err.println("$name auto-update failed: ${e.message}")
            }
        }

        val ytDlpTag = latestGitHubReleaseTag("yt-dlp/yt-dlp")
        val denoTag = latestGitHubReleaseTag("denoland/deno")
        step("yt-dlp", {
            val bin = getExistingYtDlpBinary()
            bin == null || !sameVersion(installedVersion(bin), ytDlpTag)
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
