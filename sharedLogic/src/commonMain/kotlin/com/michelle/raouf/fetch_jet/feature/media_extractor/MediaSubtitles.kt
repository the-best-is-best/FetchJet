package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import kotlin.concurrent.Volatile

/**
 * Subtitle tracks found during the last inspect of a URL, kept here so each platform's
 * [inspectPlatformQualities] (which already parses the yt-dlp `-j` JSON) can hand them to the
 * common inspect code without changing its return type. Same idea as [MediaToolErrors].
 */
object MediaSubtitleStore {
    @Volatile
    private var byUrl: Map<String, List<SubtitleTrack>> = emptyMap()

    fun record(url: String, subs: List<SubtitleTrack>) {
        byUrl = byUrl + (url.trim() to subs)
    }

    fun last(url: String): List<SubtitleTrack> = byUrl[url.trim()] ?: emptyList()

    fun clear(url: String) {
        byUrl = byUrl - url.trim()
    }
}

private val SUBTITLE_EXT_PRIORITY = listOf("srt", "vtt", "ass", "ssa")

/**
 * Reads the `subtitles` and `automatic_captions` objects from a yt-dlp `-j` root (a MiniJson map).
 * Manual subtitles win over automatic ones for the same language. YouTube's auto-translations
 * (their track URL carries `tlang=`) are skipped: only the genuine auto-caption (the spoken
 * language) is kept, so the list stays short instead of 100+ machine translations.
 */
@Suppress("UNCHECKED_CAST")
fun parseSubtitlesFromRoot(root: Map<String, Any?>?): List<SubtitleTrack> {
    if (root == null) return emptyList()
    val result = linkedMapOf<String, SubtitleTrack>()

    fun pickTrack(lang: String, tracks: List<Map<String, Any?>>, isAuto: Boolean): SubtitleTrack? {
        val usable = tracks.filter { t ->
            val url = t["url"]?.toString() ?: return@filter false
            url.isNotBlank() && !(isAuto && url.contains("tlang="))
        }
        if (usable.isEmpty()) return null
        val chosen = usable.minByOrNull { t ->
            val ext = (t["ext"]?.toString() ?: "").lowercase()
            val p = SUBTITLE_EXT_PRIORITY.indexOf(ext)
            if (p < 0) SUBTITLE_EXT_PRIORITY.size else p
        } ?: usable.first()
        val url = chosen["url"]?.toString() ?: return null
        val ext = (chosen["ext"]?.toString() ?: "srt").lowercase()
        val name = chosen["name"]?.toString()?.takeIf { it.isNotBlank() } ?: lang
        return SubtitleTrack(
            langCode = lang,
            langName = name,
            ext = ext,
            url = url,
            isAuto = isAuto
        )
    }

    fun ingest(key: String, isAuto: Boolean) {
        val map = root[key] as? Map<String, Any?> ?: return
        for ((lang, v) in map) {
            if (result.containsKey(lang)) continue
            val tracks = (v as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: continue
            pickTrack(lang, tracks, isAuto)?.let { result[lang] = it }
        }
    }

    ingest("subtitles", isAuto = false)
    ingest("automatic_captions", isAuto = true)
    return result.values.toList()
}
