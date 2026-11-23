package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs

/**
 * A readable name for a video taken straight from Facebook's servers, whose own name is a long
 * code ("AQNYQpq5-wZWq....mp4"): "Facebook story 2026-11-19 22-00.mp4". Null when the link is not
 * Facebook's or the name is already a real title.
 */
fun facebookFileName(url: String, referer: String, current: String, nowMs: Long = currentEpochMs()): String? {
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()
    if (!host.endsWith("fbcdn.net")) return null
    val base = current.substringBeforeLast('.')
    if (base.isNotBlank() && (base.contains(' ') || base.length < 30)) return null
    val ext = current.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: "mp4"
    val page = referer.lowercase()
    val kind = when {
        "/stories/" in page || "story.php" in page || "/story/" in page -> "story"
        "/reel/" in page || "/reels/" in page -> "reel"
        else -> "video"
    }
    return "Facebook $kind ${localStamp(nowMs)}.$ext"
}

private fun localStamp(nowMs: Long): String {
    val local = nowMs + localUtcOffsetMs()
    val dayMs = 24L * 60 * 60 * 1000
    val days = local.floorDiv(dayMs)
    val minutes = local.mod(dayMs) / 60_000L
    val z = days + 719468
    val era = z.floorDiv(146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    fun two(n: Long) = n.toString().padStart(2, '0')
    return "$year-${two(month)}-${two(day)} ${two(minutes / 60)}-${two(minutes % 60)}"
}
