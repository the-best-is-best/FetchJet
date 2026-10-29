package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.concurrent.Volatile

/**
 * Follows yt-dlp's output and reports how far the work *after* the download has got:
 * merging the separately downloaded video and audio into one file, fixing an HLS container,
 * converting to MP3. yt-dlp prints nothing while ffmpeg runs, so on a long video the task used
 * to sit at 100 % for minutes looking frozen.
 *
 * Progress is measured from the file ffmpeg is writing: when streams are merged they are copied,
 * not re-encoded, so the output grows to roughly the size of the inputs. When the expected size
 * is unknown (MP3 conversion) the step is still reported, without a percentage.
 *
 * Same code on desktop, Android and iOS: each platform feeds every yt-dlp line to [onLine] and
 * runs the download inside [trackPostProcessing].
 */
class PostProcessTracker(
    /** yt-dlp's output folder: fallback when a path in its output cannot be used as printed. */
    private val workDir: String? = null
) {
    private val downloadedParts = mutableListOf<String>()

    @Volatile
    var label: String? = null
        private set

    @Volatile
    private var watchedPath: String? = null

    @Volatile
    private var fallbackPath: String? = null

    @Volatile
    private var expectedBytes = 0L

    val isActive: Boolean get() = label != null

    fun onLine(rawLine: String) {
        val line = rawLine.trim()
        when {
            line.startsWith("[download] Destination:") -> {
                // A new download (next playlist item, next format) ends any previous step.
                label = null
                expectedBytes = 0L
                addPart(line.substringAfter("Destination:").trim())
            }

            line.startsWith("[download] ") && line.endsWith("has already been downloaded") ->
                addPart(
                    line.removePrefix("[download] ").removeSuffix("has already been downloaded")
                        .trim()
                )

            // [Merger] Merging formats into "C:\...\Title.mp4"
            line.startsWith("[Merger] Merging formats into") -> {
                val out = quotedOr(line, line.substringAfter("Merging formats into"))
                start("Merging audio + video", tempOf(out), out, partsSize())
            }

            // [FixupM3u8] Fixing MPEG-TS in MP4 container of "Title.mp4"
            line.startsWith("[FixupM3u8]") || line.startsWith("[FixupM4a]") || line.startsWith("[FixupStretched]") -> {
                val target = quotedOr(line, line.substringAfterLast(' '))
                start("Finalizing video", tempOf(target), null, sizeOf(target))
            }

            // [VideoRemuxer] Remuxing video from webm to mp4; Destination: Title.mp4
            line.startsWith("[VideoRemuxer]") || line.startsWith("[VideoConvertor]") -> {
                val out = unquote(line.substringAfter("Destination:", ""))
                if (out.isNotBlank()) {
                    val convert = line.startsWith("[VideoConvertor]")
                    start(
                        if (convert) "Converting video" else "Repackaging video", tempOf(out), out,
                        if (convert) 0L else partsSize()
                    )
                }
            }

            // [FetchJetCut] Cutting clip from Title.mp4   (our plugin, writes Title.temp.mp4)
            line.startsWith("[FetchJetCut] Cutting clip from") -> {
                val target = line.substringAfter("Cutting clip from ").trim()
                start("Cutting clip", tempOf(target), null, 0L)
            }

            // [ModifyChapters] Removing chapters from Title.mp4   (the local cut of a clip)
            line.startsWith("[ModifyChapters]") -> {
                val target = quotedOr(line, line.substringAfter(" from ", ""))
                if (target.isNotBlank()) start("Cutting clip", tempOf(target), null, 0L)
                else start("Cutting clip", "", null, 0L)
            }

            // [ExtractAudio] Destination: Title.mp3   (re-encode: final size not known)
            line.startsWith("[ExtractAudio] Destination:") -> {
                val out = unquote(line.substringAfter("Destination:"))
                start("Converting to MP3", out, null, 0L)
            }
        }
    }

    /** 0..0.99 while the step runs, -1 when it runs but its size cannot be predicted. */
    fun fraction(): Float {
        val expected = expectedBytes.takeIf { it > 0L } ?: dirPartsSize().also {
            if (it > 0L) expectedBytes = it
        }
        if (expected <= 0L) return -1f
        val written = watchedPath?.let { sizeOf(it) }?.takeIf { it > 0L }
            ?: fallbackPath?.let { sizeOf(it) }?.takeIf { it > 0L }
            ?: newestTempInDir()
        return (written.toDouble() / expected.toDouble()).toFloat().coerceIn(0f, 0.99f)
    }

    // ---- folder fallbacks (paths printed with a wrong encoding, relative paths...) ----

    private fun dirEntries(): List<okio.Path> = try {
        workDir?.let { FileSystem.SYSTEM.listOrNull(it.toPath()) } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    /** Size of the separately downloaded streams ("Title.f137.mp4", "Title.f140.m4a"). */
    private fun dirPartsSize(): Long {
        if (label?.startsWith("Merging") != true) return 0L
        val partName = Regex("""\.f[0-9A-Za-z_-]+\.[A-Za-z0-9]+$""")
        return dirEntries().filter { partName.containsMatchIn(it.name) }
            .sumOf { sizeOf(it.toString()) }
    }

    /** The file ffmpeg is writing right now ("Title.temp.mp4"). */
    private fun newestTempInDir(): Long =
        dirEntries().filter { it.name.contains(".temp.") }.maxOfOrNull { sizeOf(it.toString()) }
            ?: 0L

    private fun start(stepLabel: String, watch: String, fallback: String?, expected: Long) {
        watchedPath = watch
        fallbackPath = fallback
        expectedBytes = expected
        label = stepLabel
    }

    // downloadedParts is only touched from onLine (the single thread that reads yt-dlp's
    // output); the watcher thread reads only the @Volatile fields.
    private fun addPart(path: String) {
        val p = unquote(path)
        if (p.isNotBlank() && p !in downloadedParts) downloadedParts.add(p)
    }

    private fun partsSize(): Long = downloadedParts.sumOf { sizeOf(it) }

    private fun sizeOf(path: String): Long = try {
        FileSystem.SYSTEM.metadataOrNull(path.toPath())?.size ?: 0L
    } catch (_: Throwable) {
        0L
    }

    private fun unquote(s: String): String = s.trim().trim('"', '\'').trim()

    /** The path between the first and last double quote (titles may contain anything else). */
    private fun quotedOr(line: String, fallback: String): String {
        val a = line.indexOf('"')
        val b = line.lastIndexOf('"')
        return if (a >= 0 && b > a) line.substring(a + 1, b) else unquote(fallback)
    }

    /** yt-dlp/ffmpeg write "Title.temp.mp4" and rename it when done. */
    private fun tempOf(path: String): String {
        val slash = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        val dot = path.lastIndexOf('.')
        return if (dot > slash) path.substring(
            0,
            dot
        ) + ".temp" + path.substring(dot) else "$path.temp"
    }
}

/** Fraction value meaning "no post-processing step any more" (hide the second line). */
const val POST_PROCESS_CLEAR = -2f

/**
 * Runs [block] (the blocking yt-dlp call) and, while it runs, reports the post-processing step
 * every half second through [onPostProcess] (label, fraction or -1 when unknown).
 */
suspend fun <T> trackPostProcessing(
    tracker: PostProcessTracker,
    onPostProcess: ((String, Float) -> Unit)?,
    block: suspend () -> T
): T {
    if (onPostProcess == null) return block()
    return coroutineScope {
        val watcher = launch(Dispatchers.Default) {
            var shown = false
            while (isActive) {
                delay(500)
                val label = tracker.label
                try {
                    if (label != null) {
                        onPostProcess(label, tracker.fraction())
                        shown = true
                    } else if (shown) {
                        onPostProcess("", POST_PROCESS_CLEAR) // step over, downloading again
                        shown = false
                    }
                } catch (_: Throwable) {
                }
            }
        }
        try {
            block()
        } finally {
            watcher.cancel()
        }
    }
}
