package com.michelle.raouf.fetch_jet.domain.model

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
    val durationSeconds: Long = 0L
)

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
