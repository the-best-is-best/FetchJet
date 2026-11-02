package com.michelle.raouf.fetch_jet.feature.media_extractor

import java.io.File
import java.util.concurrent.TimeUnit

fun getToolsDir(): File {

    val dir = File(System.getProperty("user.home"), ".fetch_jet/tools")

    if (!dir.exists()) dir.mkdirs()

    return dir

}


fun getExistingYtDlpBinary(): File? {

    val isWin = System.getProperty("os.name").lowercase().contains("win")

    val name = if (isWin) "yt-dlp.exe" else "yt-dlp"


    val toolsFile = File(getToolsDir(), name)

    if (toolsFile.exists() && toolsFile.canExecute()) return toolsFile


    // (No fallback to a file in the current working directory: that would let a planted
    //  yt-dlp.exe / ffmpeg.exe next to a downloaded file get executed.)


    return null

}


fun getExistingFfmpegBinary(): File? {

    val isWin = System.getProperty("os.name").lowercase().contains("win")

    val name = if (isWin) "ffmpeg.exe" else "ffmpeg"


    val toolsFile = File(getToolsDir(), name)

    if (toolsFile.exists() && toolsFile.canExecute()) return toolsFile


    // (No fallback to a file in the current working directory: that would let a planted
    //  yt-dlp.exe / ffmpeg.exe next to a downloaded file get executed.)


    return null

}


fun getExistingDenoBinary(): File? {
    val isWin = System.getProperty("os.name").lowercase().contains("win")
    val toolsFile = File(getToolsDir(), if (isWin) "deno.exe" else "deno")
    return toolsFile.takeIf { it.exists() && it.canExecute() }
}

fun getYtDlpCacheDir(): File =
    File(System.getProperty("user.home"), ".fetch_jet/cache/yt-dlp").apply { mkdirs() }

fun ytDlpRuntimeArgs(): List<String> = buildList {
    add("--js-runtimes")
    add(getExistingDenoBinary()?.let { "deno:${it.absolutePath}" } ?: "deno")
    add("--js-runtimes")
    add("node")
    add("--cache-dir")
    add(getYtDlpCacheDir().absolutePath)
}

@Volatile
private var systemFfmpegCache: Boolean? = null

fun isSystemFfmpegInstalled(): Boolean {
    systemFfmpegCache?.let { return it }
    val result = try {
        val process = ProcessBuilder("ffmpeg", "-version")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()   // previously left running forever
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Exception) {
        false
    }
    systemFfmpegCache = result
    return result
}
