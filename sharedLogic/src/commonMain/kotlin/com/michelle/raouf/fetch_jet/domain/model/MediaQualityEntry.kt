package com.michelle.raouf.fetch_jet.domain.model

import com.michelle.raouf.fetch_jet.core.util.MediaExtensions

data class MediaQualityEntry(
    val index: Int,
    val qualityLabel: String,
    val directUrl: String,
    val format: String,
    val size: Long = -1L,
    val isSelected: Boolean = true,
    val formatId: String? = null,
    val originalUrl: String? = null,
    val videoTitle: String? = null,
    val durationSeconds: Long = 0L,
    val sizeApprox: Boolean = false
)

val MediaQualityEntry.isAudioOnly: Boolean
    get() = qualityLabel.contains("Audio", ignoreCase = true) ||
            format.lowercase() in MediaExtensions.AUDIO

fun List<MediaQualityEntry>.isAllAudio(): Boolean = isNotEmpty() && all { it.isAudioOnly }

fun List<MediaQualityEntry>.anyVideoSelected(): Boolean = any { it.isSelected && !it.isAudioOnly }

/** Plain words for a resolution so the choice reads at a glance: 720 -> "HD", 2160 -> "4K". */
fun qualityTag(height: Int): String = when {
    height <= 0 -> ""
    height < 720 -> "SD"
    height < 1080 -> "HD"
    height < 1440 -> "Full HD"
    height < 2160 -> "2K"
    height < 4320 -> "4K"
    else -> "8K"
}

/** "1080p" -> "1080p · Full HD"; labels without a height are returned as they are. */
fun withQualityTag(label: String): String {
    val h = Regex("""(\d{3,4})p""").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: return label
    val tag = qualityTag(h)
    return if (tag.isBlank() || label.contains(tag)) label else "$label · $tag"
}

const val MIN_REAL_MEDIA_BYTES = 4096L

fun totalFromContentRange(header: String?): Long =
    header?.substringAfter('/', "")?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: -1L

fun looksLikeNotMedia(contentType: String?): Boolean {
    val t = (contentType ?: "").lowercase()
    return t.startsWith("text/") || t.contains("json") || t.contains("mpegurl") || t.contains("xml")
}

val MediaQualityEntry.hasShownSize: Boolean get() = size > MIN_REAL_MEDIA_BYTES

val MediaQualityEntry.sizeIsEstimate: Boolean
    get() = sizeApprox || formatId?.contains("hls", ignoreCase = true) == true ||
            directUrl.contains(".m3u8", ignoreCase = true)

val MediaQualityEntry.sizeMark: String get() = if (sizeIsEstimate) "~" else ""

fun estimateStreamBytes(bitsPerSecond: Double, durationSeconds: Long): Long =
    if (bitsPerSecond > 0.0 && durationSeconds > 0L) (bitsPerSecond / 8.0 * durationSeconds).toLong() else -1L

fun fillSizeEstimates(list: MutableList<MediaQualityEntry>, estimates: Map<String, Long>) {
    for (i in list.indices) {
        val e = list[i]
        if (e.size > MIN_REAL_MEDIA_BYTES) continue
        val guess = estimates[e.qualityLabel] ?: continue
        list[i] = e.copy(size = guess, sizeApprox = true)
    }
}
