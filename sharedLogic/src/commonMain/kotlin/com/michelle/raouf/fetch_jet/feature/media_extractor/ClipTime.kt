package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.tbib.kmp_localization.LocalizationManager

object ClipTime {
    const val UNKNOWN_DURATION_END = "00:10:00"

    /** Media durations are whole seconds, so an end up to this far past them still means "to the end". */
    const val END_TOLERANCE_MS = 1_000L

    /** "HH:MM:SS(.fff)", "MM:SS(.fff)" or "SS(.fff)" (',' also works as the decimal mark); null when it is not a time. */
    fun parseMs(text: String): Long? {
        val clean = text.trim().replace(',', '.')
        if (clean.isEmpty()) return null
        val parts = clean.split(':')
        if (parts.size > 3) return null
        val last = parts.last()
        if (last.count { it == '.' } > 1) return null
        val whole = last.substringBefore('.')
        val fraction = last.substringAfter('.', "")
        if (whole.isEmpty() && fraction.isEmpty()) return null
        if (!digits(whole, allowEmpty = true) || !digits(fraction, allowEmpty = true)) return null
        val seconds = if (whole.isEmpty()) 0L else whole.toLong()
        val milli = if (fraction.isEmpty()) 0L else fraction.take(3).padEnd(3, '0').toLong()
        val upper = parts.dropLast(1).map { if (digits(it, allowEmpty = false)) it.toLong() else return null }
        if (upper.isNotEmpty() && seconds >= 60L) return null
        if (upper.size == 2 && upper[1] >= 60L) return null
        val minutes = upper.lastOrNull() ?: 0L
        val hours = if (upper.size == 2) upper[0] else 0L
        return ((hours * 60L + minutes) * 60L + seconds) * 1000L + milli
    }

    fun parseSeconds(text: String): Double? = parseMs(text)?.let { it / 1000.0 }

    /** Whole seconds, rounded down; anything that is not a time is 0. */
    fun wholeSeconds(text: String): Long = (parseMs(text) ?: 0L) / 1000L

    /** Always HH:MM:SS: a two-part "HH:MM" is read by yt-dlp / ffmpeg as MM:SS. */
    fun format(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        return "${pad(s / 3600)}:${pad((s % 3600) / 60)}:${pad(s % 60)}"
    }

    /** HH:MM:SS, with .fff only when the time has a fraction. */
    fun formatMs(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        val rest = t % 1000L
        val base = format(t / 1000L)
        return if (rest == 0L) base else "$base.${rest.toString().padStart(3, '0')}"
    }

    fun forYtDlp(text: String): String? = parseMs(text)?.let { formatMs(it) }

    private fun digits(text: String, allowEmpty: Boolean): Boolean =
        (allowEmpty || text.isNotEmpty()) && text.length <= 9 && text.all { it in '0'..'9' }

    private fun pad(v: Long) = v.toString().padStart(2, '0')
}

class ClipRangeCheck(
    val enabled: Boolean,
    val durationSeconds: Long,
    val fullText: String,
    val startText: String,
    val endText: String,
    val startMs: Long,
    val endMs: Long,
    val startInvalid: Boolean,
    val endInvalid: Boolean,
    val startAfterEnd: Boolean,
    val startExceeds: Boolean,
    val endExceeds: Boolean,
    val endAtEnd: Boolean,
) {
    val startError: Boolean get() = startInvalid || startAfterEnd || startExceeds
    val endError: Boolean get() = endInvalid || startAfterEnd || endExceeds
    val valid: Boolean get() = !enabled || (!startError && !endError)

    val startSec: Long get() = startMs / 1000L
    val endSec: Long get() = (endMs + 999L) / 1000L

    /** The largest whole second the start can be while staying before the end; -1 when nothing fits. */
    val maxStartSec: Long
        get() {
            val beforeEnd = endSec - 1L
            return if (durationSeconds > 0L) minOf(durationSeconds, beforeEnd) else beforeEnd
        }

    val isSubClip: Boolean get() = enabled && valid && (startMs > 0L || !endAtEnd)

    val message: String?
        get() {
            if (!enabled) return null
            val strings = LocalizationManager.Strings
            return when {
                startInvalid || endInvalid -> strings.invalidTimeRange
                startAfterEnd -> strings.clipStartAfterEnd(startText, endText)
                endExceeds -> strings.clipEndExceedsVideo(endText, fullText)
                startExceeds -> strings.clipStartExceedsVideo(fullText)
                else -> null
            }
        }
}

/** The one range rule of the Add dialog: an end at (or within [ClipTime.END_TOLERANCE_MS] past) the media length means "to the end". */
fun checkClipRange(enabled: Boolean, start: String, end: String, durationSeconds: Long): ClipRangeCheck {
    val durationMs = durationSeconds.coerceAtLeast(0L) * 1000L
    val full = if (durationSeconds > 0L) ClipTime.format(durationSeconds) else ClipTime.UNKNOWN_DURATION_END
    val startText = start.ifBlank { "00:00" }
    val endText = end.ifBlank { full }
    val startParsed = ClipTime.parseMs(startText)
    val endParsed = ClipTime.parseMs(endText)
    val startMs = startParsed ?: 0L
    val endMs = endParsed ?: (ClipTime.parseMs(full) ?: 0L)
    val known = durationMs > 0L
    val endAtEnd = end.isBlank() || (known && endMs >= durationMs)
    val effectiveEnd = if (known && endAtEnd) durationMs else endMs
    val on = enabled
    return ClipRangeCheck(
        enabled = on,
        durationSeconds = durationSeconds,
        fullText = full,
        startText = startText,
        endText = endText,
        startMs = startMs,
        endMs = endMs,
        startInvalid = on && startParsed == null,
        endInvalid = on && endParsed == null,
        startAfterEnd = on && startParsed != null && endParsed != null && startMs >= effectiveEnd,
        startExceeds = on && known && startParsed != null && startMs >= durationMs,
        endExceeds = on && known && endParsed != null && endMs > durationMs + ClipTime.END_TOLERANCE_MS,
        endAtEnd = endAtEnd,
    )
}
