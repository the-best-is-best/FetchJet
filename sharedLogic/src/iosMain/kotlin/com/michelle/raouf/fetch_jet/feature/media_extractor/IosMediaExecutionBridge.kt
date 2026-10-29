package com.michelle.raouf.fetch_jet.feature.media_extractor

/**
 * Progress callback for streaming yt-dlp or downloader execution on iOS.
 */
fun interface IosMediaExecutionProgressCallback {
    fun onProgress(progressPercent: Float, speedBytesPerSec: Long, line: String)
}

/**
 * Line-by-line callback for streaming CLI output (ffmpeg, aria2, etc.).
 */
fun interface IosMediaLineCallback {
    fun onLine(line: String)
}

/**
 * Native iOS bridge interface to be implemented in Swift (via SPM / Frameworks: FFmpegKit, PythonKit, AVFoundation, etc.)
 * matching Android's ytdlnis / ffmpeg / aria2c architecture.
 */
interface IosMediaExecutionBridge {
    fun isAvailable(): Boolean

    /** One line for Settings: engine ready / yt-dlp version / why it is not ready. */
    fun statusText(): String

    /**
     * Prepares the in-app engine: starts the embedded Python and downloads/updates the
     * yt_dlp module if needed. Blocking; call from a background dispatcher.
     * @return null on success, otherwise an error message.
     */
    fun prepareEngine(): String?

    /**
     * Executes yt-dlp arguments with real-time progress callbacks.
     * @param args list of CLI arguments (e.g. ["-j", url] or ["-f", formatId, "-o", template, url])
     * @param progressCallback callback receiving progress percentage (0.0 to 100.0), speed in B/s, and raw line output
     * @return exit code (0 for success)
     */
    fun executeYtDlp(
        args: List<String>,
        progressCallback: IosMediaExecutionProgressCallback?
    ): Int

    /**
     * Same as [executeYtDlp], identified by [runId] so that [cancelRun] can stop it: the
     * embedded yt-dlp cannot be killed like a process, so Pause / Cancel ask it to stop at its
     * next progress update (partial file kept for resuming).
     */
    fun executeYtDlpCancellable(
        runId: String,
        args: List<String>,
        progressCallback: IosMediaExecutionProgressCallback?
    ): Int

    /** Asks the download started with [runId] to stop as soon as possible. */
    fun cancelRun(runId: String)

    /**
     * Captures full stdout of yt-dlp (e.g. JSON metadata from `yt-dlp -j ...`).
     */
    fun captureYtDlp(args: List<String>): String?

    /** The yt-dlp error of the most recent failed capture/execute (for user-facing messages). */
    fun lastErrorText(): String?

    /**
     * Executes FFmpeg arguments with optional line output callback.
     * @param args list of CLI arguments (e.g. ["-i", input, "-c", "copy", output])
     * @param lineCallback callback receiving stdout/stderr lines
     * @return exit code (0 for success)
     */
    fun executeFfmpeg(
        args: List<String>,
        lineCallback: IosMediaLineCallback?
    ): Int

    /**
     * Probes media duration in seconds for local files or remote media streams using native AVFoundation or ffmpeg.
     */
    fun probeMediaDuration(urlOrPath: String): Long
}

/**
 * Registry singleton allowing Swift / SPM implementation to inject itself into SharedLogic at startup.
 */
object IosMediaBridgeRegistry {
    var bridge: IosMediaExecutionBridge? = null
}