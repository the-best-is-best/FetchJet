package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getArray
import com.michelle.raouf.fetch_jet.core.util.getLong
import com.michelle.raouf.fetch_jet.core.util.getString

data class PlaylistEntry(
    val index: Int,
    val title: String,
    val url: String,
    val durationSeconds: Long = 0L,
    val isSelected: Boolean = true
)

data class MediaPlaylist(
    val title: String,
    val entries: List<PlaylistEntry>
)

/**
 * The videos of a playlist / channel link, read with `yt-dlp --flat-playlist -J` (titles and
 * links only, no formats: fast even for hundreds of videos). null = not a playlist.
 */
expect suspend fun inspectPlatformPlaylist(url: String): MediaPlaylist?

/**
 * A bare YouTube channel link ("youtube.com/@name") lists the channel's tabs; its videos are
 * under ".../videos".
 */
fun normalizePlaylistUrl(url: String): String {
    val u = url.trim()
    val lower = u.lowercase()
    if (!lower.contains("youtube.com")) return u
    val path = u.substringBefore('?').substringBefore('#').trimEnd('/')
    val afterHost = path.substringAfter("youtube.com", "")
    val segments = afterHost.split('/').filter { it.isNotBlank() }
    val isChannelRoot = when {
        segments.size == 1 && segments[0].startsWith("@") -> true
        segments.size == 2 && segments[0].lowercase() in listOf("channel", "c", "user") -> true
        else -> false
    }
    return if (isChannelRoot) "$path/videos" else u
}

/** Links that are (or may be) a list of videos rather than one video. */
fun looksLikePlaylistUrl(url: String): Boolean {
    val u = url.trim().lowercase()
    val youtube = u.contains("youtube.com") || u.contains("youtu.be")
    return u.contains("list=") ||
            u.contains("/playlist") ||
            (youtube && (u.contains("/@") || u.contains("/channel/") || u.contains("/c/") || u.contains(
                "/user/"
            ))) ||
            (u.contains("soundcloud.com") && u.contains("/sets/"))
}

/** Parses yt-dlp's `-J --flat-playlist` answer. */
fun parsePlaylistJson(output: String): MediaPlaylist? {
    val line =
        output.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("{") } ?: return null

    @Suppress("UNCHECKED_CAST")
    val root = try {
        MiniJson.parse(line) as? Map<String, Any?>
    } catch (_: Exception) {
        null
    } ?: return null
    val rawEntries = root.getArray("entries") ?: return null
    val entries = mutableListOf<PlaylistEntry>()
    for (raw in rawEntries) {
        @Suppress("UNCHECKED_CAST")
        val e = raw as? Map<String, Any?> ?: continue
        // A channel link lists its tabs ("Videos", "Shorts"...) as sub-playlists, not videos.
        if (e.getString("_type") == "playlist" || e.getString("ie_key") == "YoutubeTab") continue
        val title = e.getString("title")?.trim().orEmpty()
        // Removed videos stay in playlists as placeholders.
        if (title == "[Private video]" || title == "[Deleted video]") continue
        val id = e.getString("id").orEmpty()
        val link = listOfNotNull(e.getString("url"), e.getString("webpage_url"))
            .firstOrNull { it.startsWith("http", ignoreCase = true) }
            ?: if (id.isNotBlank() && (e.getString("ie_key") ?: "").equals(
                    "Youtube",
                    ignoreCase = true
                )
            )
                "https://www.youtube.com/watch?v=$id" else null
        if (link.isNullOrBlank()) continue
        entries.add(
            PlaylistEntry(
                index = entries.size,
                title = title.ifBlank { id.ifBlank { "Video ${entries.size + 1}" } },
                url = link,
                durationSeconds = e.getLong("duration") ?: 0L
            )
        )
    }
    if (entries.isEmpty()) return null
    val title = root.getString("title")?.trim()?.ifBlank { null } ?: "Playlist"
    return MediaPlaylist(title, entries)
}

/** Choices offered for a whole playlist (the formats of every video are not inspected). */
enum class PlaylistQuality(val label: String, val formatSelector: String, val extension: String) {
    BEST("Best", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best", "mp4"),
    P1080(
        "1080p",
        "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
        "mp4"
    ),
    P720(
        "720p",
        "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]/best",
        "mp4"
    ),
    P480(
        "480p",
        "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]/best",
        "mp4"
    ),
    P360(
        "360p",
        "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio/best[height<=360]/best",
        "mp4"
    ),
    AUDIO("Audio only", "bestaudio[ext=m4a]/bestaudio", "m4a")
}
