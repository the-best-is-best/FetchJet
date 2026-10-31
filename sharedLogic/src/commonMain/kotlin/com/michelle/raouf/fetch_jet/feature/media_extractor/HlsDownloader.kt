package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.log.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * FetchJet's own HLS (.m3u8) engine, for sites whose CDN turns yt-dlp / ffmpeg away
 * (Cloudflare "anti-bot" on Udemy and others) while the app's HTTP client, like the browser,
 * gets through. Shared by every platform:
 *  1. the master playlist is read and its renditions become the quality list,
 *  2. the chosen rendition's segments (and AES-128 key, if any) are fetched in parallel with
 *     the browser's Cookie / Referer, into the task's cache folder,
 *  3. a local copy of the playlist points at those files and ffmpeg (offline) joins them
 *     into one .mp4 - ffmpeg never touches the network, so the CDN cannot block it.
 */
object HlsDownloader {

    /** Marks a quality entry / task that this engine handles. */
    const val FORMAT_ID = "fjhls"

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    data class Variant(
        val url: String,
        val bandwidth: Long,
        val width: Int,
        val height: Int,
        val codecs: String
    ) {
        val label: String
            get() = when {
                height > 0 -> "${height}p"
                bandwidth > 0 -> "${bandwidth / 1000} kbps"
                else -> "Stream"
            }
    }

    fun isHlsUrl(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').lowercase().endsWith(".m3u8")

    private fun resolve(base: String, ref: String): String {
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) return ref
        val b = base.substringBefore('?').substringBefore('#')
        if (ref.startsWith("//")) return b.substringBefore("://") + ":" + ref
        if (ref.startsWith("/")) {
            val schemeHost =
                b.substringBefore("://") + "://" + b.substringAfter("://").substringBefore('/')
            return schemeHost + ref
        }
        return b.substringBeforeLast('/') + "/" + ref
    }

    private fun attrs(line: String): Map<String, String> {
        // KEY=VALUE,KEY="quoted, value",...
        val out = mutableMapOf<String, String>()
        var i = 0
        val s = line.substringAfter(':', "")
        while (i < s.length) {
            val eq = s.indexOf('=', i); if (eq < 0) break
            val key = s.substring(i, eq).trim()
            var j = eq + 1
            val value: String
            if (j < s.length && s[j] == '"') {
                val end = s.indexOf('"', j + 1).let { if (it < 0) s.length else it }
                value = s.substring(j + 1, end); j = end + 1
            } else {
                val end = s.indexOf(',', j).let { if (it < 0) s.length else it }
                value = s.substring(j, end); j = end
            }
            out[key] = value
            if (j < s.length && s[j] == ',') j++
            i = j
        }
        return out
    }

    private suspend fun fetchText(
        client: HttpClient,
        url: String,
        headers: Map<String, String>
    ): String {
        val r = client.get(url) { applyHeaders(headers) }
        if (!r.status.isSuccess()) throw Exception("HTTP ${r.status.value} for playlist")
        return r.bodyAsText()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(h: Map<String, String>) {
        h.forEach { (k, v) -> if (v.isNotBlank()) header(k, v) }
        if (h.keys.none { it.equals("User-Agent", true) }) header(HttpHeaders.UserAgent, BROWSER_UA)
        header(HttpHeaders.Accept, "*/*")
    }

    /**
     * Renditions of a master playlist, best first. A media playlist (segments, no renditions)
     * gives one variant = the URL itself. Throws when the playlist cannot be read.
     */
    suspend fun listVariants(
        client: HttpClient,
        url: String,
        headers: Map<String, String>
    ): List<Variant> {
        val text = fetchText(client, url, headers)
        if (!text.trimStart().startsWith("#EXTM3U")) throw Exception("Not an HLS playlist")
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val variants = mutableListOf<Variant>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                val a = attrs(l)
                val res = a["RESOLUTION"]?.split('x')
                val next = lines.drop(i + 1).firstOrNull { !it.startsWith("#") } ?: ""
                if (next.isNotBlank()) variants.add(
                    Variant(
                        url = resolve(url, next),
                        bandwidth = (a["AVERAGE-BANDWIDTH"] ?: a["BANDWIDTH"])?.toLongOrNull()
                            ?: 0L,
                        width = res?.getOrNull(0)?.toIntOrNull() ?: 0,
                        height = res?.getOrNull(1)?.toIntOrNull() ?: 0,
                        codecs = a["CODECS"] ?: ""
                    )
                )
            }
            i++
        }
        if (variants.isEmpty()) {
            // a media playlist: one quality
            if (lines.none { it.startsWith("#EXTINF") }) throw Exception("Playlist has no streams")
            return listOf(Variant(url, 0L, 0, 0, ""))
        }
        return variants.distinctBy { it.url }
            .sortedWith(compareByDescending<Variant> { it.height }.thenByDescending { it.bandwidth })
    }

    /** Total duration (seconds) of a rendition, from its playlist's #EXTINF lines. */
    suspend fun durationSeconds(
        client: HttpClient,
        playlistUrl: String,
        headers: Map<String, String>
    ): Long = try {
        val (segments, _) = readMedia(client, playlistUrl, headers)
        segments.sumOf { it.duration }.toLong()
    } catch (_: Exception) {
        0L
    }

    private class Segment(
        val url: String,
        val duration: Double,
        val keyUrl: String?,
        val iv: String?,
        val byteRange: String?,
        val isInit: Boolean = false
    )

    private suspend fun readMedia(
        client: HttpClient,
        url: String,
        headers: Map<String, String>
    ): Pair<List<Segment>, List<String>> {
        var text = fetchText(client, url, headers)
        var lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // Given a master by mistake: follow its best rendition.
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            val best = listVariants(client, url, headers).first()
            text = fetchText(client, best.url, headers)
            lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            return readMediaLines(best.url, lines)
        }
        return readMediaLines(url, lines)
    }

    private fun readMediaLines(
        url: String,
        lines: List<String>
    ): Pair<List<Segment>, List<String>> {
        val segments = mutableListOf<Segment>()
        val header = mutableListOf<String>()
        var keyUrl: String? = null
        var iv: String? = null
        var duration = 0.0
        var byteRange: String? = null
        for (l in lines) {
            when {
                l.startsWith("#EXT-X-KEY") -> {
                    val a = attrs(l)
                    if (a["METHOD"] == "NONE") {
                        keyUrl = null; iv = null
                    } else {
                        keyUrl = a["URI"]?.let { resolve(url, it) }; iv = a["IV"]
                        if (a["METHOD"] != null && a["METHOD"] != "AES-128") throw Exception("DRM-protected stream (${a["METHOD"]})")
                    }
                }

                l.startsWith("#EXTINF") -> duration =
                    l.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0

                l.startsWith("#EXT-X-BYTERANGE") -> byteRange = l.substringAfter(':')
                l.startsWith("#EXT-X-MAP") -> {
                    val a = attrs(l); a["URI"]?.let {
                        segments.add(
                            Segment(
                                resolve(url, it),
                                0.0,
                                keyUrl,
                                iv,
                                a["BYTERANGE"],
                                isInit = true
                            )
                        )
                    }
                }

                l.startsWith("#EXT-X-TARGETDURATION") || l.startsWith("#EXT-X-VERSION") ||
                        l.startsWith("#EXT-X-PLAYLIST-TYPE") || l.startsWith("#EXT-X-MEDIA-SEQUENCE") ||
                        l.startsWith("#EXT-X-INDEPENDENT-SEGMENTS") -> header.add(l)

                l.startsWith("#") -> {}
                else -> {
                    segments.add(
                        Segment(
                            resolve(url, l),
                            duration,
                            keyUrl,
                            iv,
                            byteRange
                        )
                    ); duration = 0.0; byteRange = null
                }
            }
        }
        if (segments.isEmpty()) throw Exception("Playlist has no segments")
        return segments to header
    }

    /**
     * Downloads [playlistUrl] (a rendition or a media playlist) into [outputFile] (.mp4).
     * [workDir] keeps the segments between runs, so a paused / failed download continues where
     * it stopped. [isCancelled] is polled between segments. [onProgress](fraction, bytes so far,
     * estimated total, speed B/s).
     */
    suspend fun download(
        client: HttpClient,
        playlistUrl: String,
        headers: Map<String, String>,
        workDir: String,
        outputFile: String,
        parallel: Int = 6,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float, Long, Long, Long) -> Unit = { _, _, _, _ -> },
        onPostProcess: ((String, Float) -> Unit)? = null,
        /** Clip: only the parts between these seconds are fetched (null = whole video). */
        clipStartSec: Double? = null,
        clipEndSec: Double? = null
    ): Boolean = withContext(Dispatchers.Default) {
        val fs = FileSystem.SYSTEM
        val dir = workDir.toPath()
        fs.createDirectories(dir)
        val (allSegments, headerLines) = readMedia(client, playlistUrl, headers)
        // Clip = the segments that overlap [start, end] (init segment always kept); ffmpeg
        // then trims the few seconds at both edges exactly.
        var clipOffsetSec = 0.0
        val segments = if (clipStartSec == null && clipEndSec == null) allSegments else {
            val start = clipStartSec ?: 0.0
            val end = clipEndSec ?: Double.MAX_VALUE
            var t = 0.0
            val kept = mutableListOf<Segment>()
            var firstKeptStart: Double? = null
            for (seg in allSegments) {
                if (seg.isInit) {
                    kept.add(seg); continue
                }
                val segEnd = t + seg.duration
                if (segEnd > start && t < end) {
                    if (firstKeptStart == null) firstKeptStart = t
                    kept.add(seg)
                }
                t = segEnd
            }
            clipOffsetSec = firstKeptStart ?: 0.0
            kept
        }
        if (segments.none { !it.isInit }) throw Exception("Nothing to download in that time range")
        AppLog.d { "HLS: ${segments.size}/${allSegments.size} segments from $playlistUrl" }

        // Keys (usually one) first: tiny, and needed by ffmpeg.
        val keyFiles = mutableMapOf<String, String>()
        segments.mapNotNull { it.keyUrl }.distinct().forEachIndexed { idx, k ->
            val name = "key$idx.bin"
            val p = dir / name
            if (!fs.exists(p)) fs.write(p) { write(fetchBytes(client, k, headers, null)) }
            keyFiles[k] = name
        }

        val total = segments.size
        var done = 0
        var bytes = 0L
        val startedAt = nowMs()
        val lock = Mutex()
        val sem = Semaphore(parallel.coerceIn(1, 12))
        val segName: (Int) -> String = { i -> "seg${i.toString().padStart(6, '0')}.ts" }

        // What is already there counts (resume).
        segments.indices.forEach { i ->
            val p = dir / segName(i)
            if (fs.exists(p)) {
                done++; bytes += fs.metadata(p).size ?: 0L
            }
        }
        onProgress(if (total > 0) done.toFloat() / total else 0f, bytes, 0L, 0L)

        coroutineScope {
            segments.mapIndexed { i, seg ->
                async {
                    val p = dir / segName(i)
                    if (fs.exists(p)) return@async
                    if (isCancelled()) throw CancellationException("paused")
                    sem.withPermit {
                        if (isCancelled()) throw CancellationException("paused")
                        var attempt = 0
                        var data: ByteArray? = null
                        while (data == null) {
                            try {
                                data = fetchBytes(client, seg.url, headers, seg.byteRange)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (++attempt >= 4) throw Exception("Segment ${i + 1}/$total failed: ${e.message}")
                                kotlinx.coroutines.delay(600L * attempt)
                            }
                        }
                        val tmp = dir / (segName(i) + ".part")
                        fs.write(tmp) { write(data) }
                        fs.atomicMove(tmp, p)
                        val snapshot = lock.withLock {
                            done++; bytes += data.size
                            Triple(done, bytes, nowMs() - startedAt)
                        }
                        val (d, b, elapsed) = snapshot
                        val est = if (d > 0) (b.toDouble() / d * total).toLong() else 0L
                        val speed = if (elapsed > 300) b * 1000 / elapsed else 0L
                        onProgress(d.toFloat() / total, b, est, speed)
                    }
                }
            }.awaitAll()
        }
        if (isCancelled()) return@withContext false

        // Local playlist: same tags, files instead of URLs, local key.
        val local = buildString {
            appendLine("#EXTM3U")
            headerLines.filterNot { it.startsWith("#EXT-X-INDEPENDENT-SEGMENTS") }
                .forEach { appendLine(it) }
            var lastKey: String? = null
            segments.forEachIndexed { i, s ->
                if (s.keyUrl != lastKey) {
                    if (s.keyUrl == null) appendLine("#EXT-X-KEY:METHOD=NONE")
                    else appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"${keyFiles[s.keyUrl]}\"" + (s.iv?.let { ",IV=$it" }
                        ?: ""))
                    lastKey = s.keyUrl
                }
                if (s.isInit) {
                    appendLine("#EXT-X-MAP:URI=\"${segName(i)}\"")
                } else {
                    appendLine("#EXTINF:${s.duration},")
                    appendLine(segName(i))
                }
            }
            appendLine("#EXT-X-ENDLIST")
        }
        val localPlaylist = dir / "local.m3u8"
        fs.write(localPlaylist) { writeUtf8(local) }

        onPostProcess?.invoke("Joining ${total} parts into MP4…", -1f)
        val trim = buildList {
            if (clipStartSec != null || clipEndSec != null) {
                val ss = ((clipStartSec ?: 0.0) - clipOffsetSec).coerceAtLeast(0.0)
                if (ss > 0.0) {
                    add("-ss"); add(ss.toString())
                }
                if (clipEndSec != null) {
                    add("-to"); add((clipEndSec - clipOffsetSec).coerceAtLeast(ss + 0.1).toString())
                }
            }
        }
        val input = listOf(
            "-y", "-hide_banner", "-loglevel", "error",
            "-allowed_extensions", "ALL", "-protocol_whitelist", "file,crypto,data",
            "-i", localPlaylist.toString()
        ) + trim
        val ok = runLocalFfmpeg(
            input + listOf(
                "-c",
                "copy",
                "-bsf:a",
                "aac_adtstoasc",
                "-movflags",
                "+faststart",
                outputFile
            )
        )
        if (!ok) {
            // Without the bitstream filter (streams that are not AAC-in-TS).
            val ok2 = runLocalFfmpeg(input + listOf("-c", "copy", outputFile))
            if (!ok2) throw Exception("ffmpeg could not join the parts")
        }
        // Segments are no longer needed.
        try {
            fs.list(dir)
                .filter { it.name.startsWith("seg") || it.name.startsWith("key") || it.name == "local.m3u8" }
                .forEach { fs.delete(it) }
        } catch (_: Exception) {
        }
        true
    }

    private suspend fun fetchBytes(
        client: HttpClient,
        url: String,
        headers: Map<String, String>,
        byteRange: String?
    ): ByteArray {
        val r = client.get(url) {
            applyHeaders(headers)
            // "#EXT-X-BYTERANGE:<length>@<offset>"
            byteRange?.let { br ->
                val len = br.substringBefore('@').toLongOrNull();
                val off = br.substringAfter('@', "0").toLongOrNull() ?: 0L
                if (len != null) header(HttpHeaders.Range, "bytes=$off-${off + len - 1}")
            }
        }
        if (!r.status.isSuccess()) throw Exception("HTTP ${r.status.value}")
        return r.bodyAsBytes()
    }

    private fun nowMs(): Long = com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs()
}

/** Runs the platform's ffmpeg on local files only (no network). true = exit code 0. */
expect suspend fun runLocalFfmpeg(args: List<String>): Boolean
