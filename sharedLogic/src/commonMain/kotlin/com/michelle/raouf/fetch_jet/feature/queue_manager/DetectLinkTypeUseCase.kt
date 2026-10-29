package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.domain.model.EngineType

class DetectLinkTypeUseCase {
    operator fun invoke(url: String): EngineType {
        // "C:\x.torrent" pasted with quotes (Windows "Copy as path") or as file:///... must
        // still be a torrent, not an HTTP download of a local path.
        val trimmed = com.michelle.raouf.fetch_jet.feature.torrent_engine
            .normalizeLocalTorrentPath(url).lowercase()
        return when {
            trimmed.startsWith("magnet:") || trimmed.contains("xt=urn:btih:") || trimmed.endsWith(".torrent") -> EngineType.BITTORRENT
            isMediaSource(trimmed) -> EngineType.MEDIA_STREAM
            else -> EngineType.HTTP_DIRECT
        }
    }

    private fun isMediaSource(url: String): Boolean {
        val cleanUrl = url.substringBefore("?").substringBefore("#")
        val host = cleanUrl.substringAfter("://", "").substringBefore("/").substringBefore(":")
        val mediaDomains = listOf(
            "youtube.com", "youtu.be", "youtube-nocookie.com", "vimeo.com", "tiktok.com",
            "instagram.com", "twitter.com", "x.com", "facebook.com", "fb.watch",
            "twitch.tv", "dailymotion.com", "dai.ly", "bilibili.com", "rumble.com",
            "googlevideo.com", "pipedapi.kavin.rocks", "cobalt.tools",
            "soundcloud.com", "bandcamp.com", "reddit.com", "vk.com", "odysee.com",
            "streamable.com", "ok.ru", "pinterest.com", "bitchute.com"
        )
        // Exact host or sub-domain only: "x.com" must not catch dropbox.com / box.com.
        val isMediaHost = host.isNotEmpty() && mediaDomains.any { d -> host == d || host.endsWith(".$d") }

        // Playlists need the media engine; a plain media *file* (.mp4, .mp3 ...) is a normal
        // HTTP download (chunked, resumable) - except on media hosts, where yt-dlp knows better.
        val playlistExtensions = listOf(".m3u8", ".mpd")
        val isPlaylist = playlistExtensions.any { cleanUrl.endsWith(it) }
        return isMediaHost || isPlaylist
    }
}
