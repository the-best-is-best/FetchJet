package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager
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
        ytdlp != null && (ffmpeg != null || isSystemFfmpegInstalled()) -> LocalizationManager.Strings.sysToolsReadyYtDlpFfmpeg
        ytdlp != null -> LocalizationManager.Strings.sysToolsReadyYtDlpNoFfmpeg
        else -> LocalizationManager.Strings.sysToolsNotInstalled
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

// os.arch is the CPU of the running JVM (the bundled runtime): the tools must match it, as
// they are started by this process. x64 Windows/Linux builds on ARM run emulated, and so do
// the x64 tools they download.
private val osArch = System.getProperty("os.arch").lowercase()
private val isArm = osArch == "aarch64" || osArch == "arm64"
private val isArm32 = !isArm && osArch.startsWith("arm")
private val isX86_32 = osArch in setOf("x86", "i386", "i486", "i586", "i686")

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
        isWin && isArm -> "yt-dlp_arm64.exe"
        isWin && isX86_32 -> "yt-dlp_x86.exe"
        isWin -> "yt-dlp.exe"
        isMac -> "yt-dlp_macos" // universal (arm64 + x86_64)
        isArm -> "yt-dlp_linux_aarch64"
        // No standalone build for 32-bit Linux: the zipapp, which runs on the system python3.
        isArm32 || isX86_32 -> "yt-dlp"
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

// ffbinaries has no Apple Silicon build: its osx-64 ffmpeg needs Rosetta, which Apple is
// retiring. eugeneware/ffmpeg-static (the npm ffmpeg-static binaries) ships a native one; the
// hash pins the exact file, as that release publishes no checksum. Everything else (Intel Macs,
// Windows, Linux on every CPU) keeps ffbinaries.
private const val FFMPEG_STATIC_BASE =
    "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1"

/** (asset, sha256) of the native ARM ffmpeg for this machine, or null to use ffbinaries. */
private val nativeArmFfmpeg: Pair<String, String>? = when {
    isMac && isArm -> "ffmpeg-darwin-arm64" to "a90e3db6a3fd35f6074b013f948b1aa45b31c6375489d39e572bea3f18336584"
    else -> null
}

/** ffbinaries 4.4.1 asset for this OS / CPU. Windows has only x64 (it runs emulated on ARM). */
private val ffbinariesAsset: String = when {
    isWin -> "win-64"
    isMac -> "osx-64"
    isArm -> "linux-arm-64"
    isArm32 && osArch.contains("hf") -> "linux-armhf-32"
    isArm32 -> "linux-armel-32"
    isX86_32 -> "linux-32"
    else -> "linux-64"
}

private fun installNativeArmFfmpeg(
    toolsDir: File,
    asset: String,
    sha256: String,
    onProgress: (ToolDownloadProgress) -> Unit
) {
    val ffmpegFile = File(toolsDir, "ffmpeg")
    val staging = File(toolsDir, "ffmpeg.download")
    val actual = downloadTo(
        "$FFMPEG_STATIC_BASE/$asset",
        staging
    ) { onProgress(ToolDownloadProgress("FFmpeg", it)) }
    if (actual != sha256) {
        staging.delete()
        throw IllegalStateException("ffmpeg checksum mismatch (expected $sha256, got $actual)")
    }
    Files.move(
        staging.toPath(),
        ffmpegFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
    )
    ffmpegFile.setExecutable(true)
}

/**
 * An x86-64-only ffmpeg left by an older build on an ARM machine: a Mach-O that runs only via
 * Rosetta, or an ELF that does not run at all.
 */
private fun isX86OnlyBinary(file: File): Boolean = try {
    val header = ByteArray(20)
    val n = file.inputStream().use { it.read(header) }
    when {
        // MH_MAGIC_64 (0xfeedfacf) + CPU_TYPE_X86_64 (0x01000007), little-endian.
        n >= 8 && header.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0xcf.toByte(), 0xfa.toByte(), 0xed.toByte(), 0xfe.toByte(), 7, 0, 0, 1)
        ) -> true
        // ELF with e_machine = EM_X86_64 (0x3e).
        n >= 20 && header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[18] == 0x3e.toByte() && header[19] == 0.toByte() -> true

        else -> false
    }
} catch (_: Exception) {
    false
}

private fun installFfmpeg(toolsDir: File, onProgress: (ToolDownloadProgress) -> Unit) {
    nativeArmFfmpeg?.let { (asset, sha256) ->
        installNativeArmFfmpeg(toolsDir, asset, sha256, onProgress)
        return
    }
    val ffmpegZipUrl =
        "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-$ffbinariesAsset.zip"
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
    if (isArm32 || isX86_32) throw IllegalStateException("deno has no build for $osArch")
    val asset = when {
        isWin && isArm -> "deno-aarch64-pc-windows-msvc.zip"
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
        step("ffmpeg", {
            val bin = getExistingFfmpegBinary()
            (bin == null && !isSystemFfmpegInstalled()) ||
                    (bin != null && isArm && isX86OnlyBinary(bin))
        }) {
            installFfmpeg(toolsDir, onProgress)
        }
        step("deno", {
            val bin = getExistingDenoBinary()
            bin == null || !sameVersion(installedVersion(bin), denoTag)
        }) { installDeno(toolsDir, onProgress) }
        changed
    }
