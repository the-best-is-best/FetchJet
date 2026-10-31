package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getLong
import com.michelle.raouf.fetch_jet.core.util.getObjectArray
import com.michelle.raouf.fetch_jet.core.util.getString

/** One plain HTTP(S) stream of a video page: a file FetchJet's own engine can fetch in pieces. */
data class ResolvedStream(
    val url: String,
    val ext: String,
    val size: Long,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val headers: Map<String, String>
)

data class ResolvedMedia(
    val title: String?,
    val streams: List<ResolvedStream>,
    val durationSeconds: Long
)

/**
 * yt-dlp as a *resolver only*: `-j -f <selector>` gives the direct stream URL(s) for the
 * chosen quality; the download itself is done by [com.michelle.raouf.fetch_jet.core.network.PieceDownloader]
 * (several connections, pause/resume, piece map) and the merge by the local ffmpeg.
 * Returns null when the page needs yt-dlp's own downloader (HLS/DASH fragments, DRM…).
 */
object DirectMediaResolver {
    /** The selector yt-dlp gets, same choices as the download path. */
    fun selector(formatId: String?, audioOnly: Boolean): String = when {
        formatId.isNullOrBlank() -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best"
        audioOnly -> "$formatId/bestaudio/best"
        else -> "$formatId+bestaudio/$formatId/best"
    }

    suspend fun resolve(
        url: String,
        formatId: String?,
        audioOnly: Boolean,
        headers: Map<String, String>
    ): ResolvedMedia? {
        val sel = selector(formatId, audioOnly)
        val json = runYtDlpJson(url, sel, headers)
        if (json == null) {
            com.michelle.raouf.fetch_jet.core.log.AppLog.d { "DirectMediaResolver: yt-dlp gave no JSON for -f $sel" }
            return null
        }
        val parsed = parse(json)
        if (parsed != null) return parsed
        com.michelle.raouf.fetch_jet.core.log.AppLog.d { "DirectMediaResolver: not a plain stream (-f $sel): $lastReject" }
        // The chosen format is an HLS/DASH manifest (Android's yt-dlp often picks the iOS
        // client's m3u8 itags): ask for the same picture size as plain https files instead.
        val h = lastRejectHeight
        if (lastReject.contains("protocol=") || lastReject.contains("playlist")) {
            val httpsSel = when {
                audioOnly -> "bestaudio[protocol^=http]/best[protocol^=http]"
                h > 0 -> "bestvideo[height<=$h][protocol^=http]+bestaudio[protocol^=http]/best[height<=$h][protocol^=http]"
                else -> "bestvideo[protocol^=http]+bestaudio[protocol^=http]/best[protocol^=http]"
            }
            val json2 = runYtDlpJson(url, httpsSel, headers) ?: return null
            val parsed2 = parse(json2)
            com.michelle.raouf.fetch_jet.core.log.AppLog.d {
                if (parsed2 != null) "DirectMediaResolver: https formats found with -f $httpsSel"
                else "DirectMediaResolver: still no plain stream (-f $httpsSel): $lastReject"
            }
            return parsed2
        }
        return null
    }

    /** Why the last [parse] said no (for the log), and the rejected format's height. */
    private var lastReject: String = ""
    private var lastRejectHeight: Int = 0

    @Suppress("UNCHECKED_CAST")
    fun parse(output: String): ResolvedMedia? {
        val line = output.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("{") && it.endsWith("}") }
            ?: output.trim()
        val root = try {
            MiniJson.parse(line) as? Map<String, Any?>
        } catch (_: Exception) {
            null
        } ?: run { lastReject = "bad json (${line.take(60)})"; return null }
        val title = root.getString("title")
        val duration = root.getLong("duration") ?: 0L
        val chosen: List<Map<String, Any?>> = root.getObjectArray("requested_formats")
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(root)
        val streams = chosen.map { f ->
            val protocol = (f.getString("protocol") ?: "https").lowercase()
            lastRejectHeight = (f["height"] as? Number)?.toInt() ?: 0
            if (protocol != "https" && protocol != "http") {
                lastReject = "protocol=$protocol format=${f.getString("format_id")}"; return null
            }
            val u = f.getString("url")?.takeIf { it.startsWith("http") } ?: run {
                lastReject = "no url for format=${f.getString("format_id")}"; return null
            }
            if (u.contains(".m3u8", true) || u.contains(".mpd", true)) {
                lastReject = "playlist url format=${f.getString("format_id")}"; return null
            }
            val vcodec = f.getString("vcodec") ?: "none"
            val acodec = f.getString("acodec") ?: "none"
            val hdrs = (f["http_headers"] as? Map<String, Any?>)
                ?.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }?.toMap() ?: emptyMap()
            ResolvedStream(
                url = u,
                ext = f.getString("ext") ?: "mp4",
                size = f.getLong("filesize") ?: f.getLong("filesize_approx") ?: -1L,
                hasVideo = !vcodec.equals("none", true),
                hasAudio = !acodec.equals("none", true),
                headers = hdrs
            )
        }
        if (streams.isEmpty() || streams.size > 2) {
            lastReject = "${streams.size} streams"; return null
        }
        return ResolvedMedia(title, streams, duration)
    }
}

/** Runs `yt-dlp -j -f <selector> <url>` and returns its JSON line, or null when the tool failed. */
expect suspend fun runYtDlpJson(
    url: String,
    selector: String,
    headers: Map<String, String>
): String?
