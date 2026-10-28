package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
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

/** Opens an HTTPS connection with timeouts and a status check (a 404 page is not a binary). */
private fun openStream(url: String): InputStream {
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    conn.connectTimeout = CONNECT_TIMEOUT_MS
    conn.readTimeout = READ_TIMEOUT_MS
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("User-Agent", "FetchJet")
    if (conn.responseCode !in 200..299) {
        throw IllegalStateException("HTTP ${conn.responseCode} for $url")
    }
    return conn.inputStream
}

/** Streams [url] into [dest] via a temp file, returning the SHA-256 of the bytes written. */
private fun downloadTo(url: String, dest: File): String {
    val tmp = File(dest.parentFile, dest.name + ".tmp")
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    openStream(url).use { input ->
        tmp.outputStream().use { output ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_TOOL_BYTES) throw IllegalStateException("Download exceeds ${MAX_TOOL_BYTES / 1024 / 1024} MB")
                digest.update(buf, 0, n)
                output.write(buf, 0, n)
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
        openStream("https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS")
            .bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .firstOrNull { it.endsWith("  $assetName") || it.endsWith(" $assetName") }
                    ?.substringBefore(' ')?.lowercase()
            }
    } catch (_: Exception) {
        null
    }
}

actual suspend fun downloadMediaExtractorTool(): Boolean = withContext(Dispatchers.IO) {
    try {
        val toolsDir = getToolsDir()
        if (!toolsDir.exists()) toolsDir.mkdirs()

        val osName = System.getProperty("os.name").lowercase()
        val isWin = osName.contains("win")
        val isMac = osName.contains("mac")

        // 1. yt-dlp, verified against the checksum list yt-dlp publishes with every release.
        val ytDlpName = when {
            isWin -> "yt-dlp.exe"
            isMac -> "yt-dlp_macos"
            else -> "yt-dlp_linux"
        }
        val ytDlpUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$ytDlpName"
        val ytDlpFile = File(toolsDir, if (isWin) "yt-dlp.exe" else "yt-dlp")

        val expected = fetchPublishedYtDlpSha256(ytDlpName)
        val staging = File(toolsDir, ytDlpFile.name + ".download")
        val actual = downloadTo(ytDlpUrl, staging)
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

        // 2. ffmpeg. Pinned to one release; the archive is unpacked only if it contains the
        //    binary, and the install only counts as successful if it really landed.
        val ffmpegZipUrl = when {
            isWin -> "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-win-64.zip"
            isMac -> "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-osx-64.zip"
            else -> "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-linux-64.zip"
        }

        val zipFile = File(toolsDir, "ffmpeg.zip")
        downloadTo(ffmpegZipUrl, zipFile)

        val ffmpegFileName = if (isWin) "ffmpeg.exe" else "ffmpeg"
        var extracted = false
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // Only the one expected file name; ignore directories and anything with a path
                // (zip-slip guard).
                val plainName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (!entry.isDirectory && plainName == ffmpegFileName) {
                    val tmp = File(toolsDir, "$ffmpegFileName.tmp")
                    tmp.outputStream().use { os -> zis.copyTo(os) }
                    val outFile = File(toolsDir, ffmpegFileName)
                    Files.move(
                        tmp.toPath(),
                        outFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                    outFile.setExecutable(true)
                    extracted = true
                    break
                }
                entry = zis.nextEntry
            }
        }
        zipFile.delete()
        if (!extracted) {
            System.err.println("ffmpeg archive did not contain $ffmpegFileName; yt-dlp will work without merging (720p limit)")
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
