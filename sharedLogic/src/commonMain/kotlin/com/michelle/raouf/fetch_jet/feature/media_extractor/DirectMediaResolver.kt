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
        val json = runYtDlpJson(url, selector(formatId, audioOnly), headers) ?: return null
        return parse(json)
    }

    @Suppress("UNCHECKED_CAST")
    fun parse(output: String): ResolvedMedia? {
        val line = output.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("{") && it.endsWith("}") }
            ?: output.trim()
        val root = try {
            MiniJson.parse(line) as? Map<String, Any?>
        } catch (_: Exception) {
            null
        } ?: return null
        val title = root.getString("title")
        val duration = root.getLong("duration") ?: 0L
        val chosen: List<Map<String, Any?>> = root.getObjectArray("requested_formats")
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(root)
        val streams = chosen.map { f ->
            val protocol = (f.getString("protocol") ?: "https").lowercase()
            if (protocol != "https" && protocol != "http") return null
            val u = f.getString("url")?.takeIf { it.startsWith("http") } ?: return null
            if (u.contains(".m3u8", true) || u.contains(".mpd", true)) return null
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
        if (streams.isEmpty() || streams.size > 2) return null
        return ResolvedMedia(title, streams, duration)
    }
}

/** Runs `yt-dlp -j -f <selector> <url>` and returns its JSON line, or null when the tool failed. */
expect suspend fun runYtDlpJson(
    url: String,
    selector: String,
    headers: Map<String, String>
): String?
