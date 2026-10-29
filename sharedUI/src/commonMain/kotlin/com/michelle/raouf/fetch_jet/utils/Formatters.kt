package com.michelle.raouf.fetch_jet.utils

import kotlin.math.round

/**
 * Formats a raw byte count into human-readable digital storage units (B, KB, MB, GB, TB).
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = (value * 10).toLong() / 10.0
    return "$formatted ${units[unitIndex]}"
}

/**
 * Formats size in bytes specifically for media stream choices (KB/MB).
 */
fun formatSizeDisplay(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return ""
    val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        val rounded = (mb * 10).toLong() / 10.0
        "$rounded MB"
    } else {
        val kb = sizeBytes / 1024
        "$kb KB"
    }
}

/**
 * Formats a raw transfer speed (bytes/sec) according to the user's preferred display unit.
 */
fun formatSpeed(bytesPerSec: Long, unit: String = "MB/s"): String {
    val normalizedUnit = when (unit) {
        "BYTES_PER_SEC" -> "MB/s"
        "BITS_PER_SEC" -> "Mbps"
        "BOTH" -> "MB/s"
        else -> unit
    }
    if (bytesPerSec <= 0L) {
        return when (normalizedUnit) {
            "KB/s" -> "0 KB/s"
            "Kbps" -> "0 Kbps"
            "Mbps" -> "0 Mbps"
            else -> "0 KB/s"
        }
    }

    return when (normalizedUnit) {
        "MB/s" -> {
            if (bytesPerSec < 1024L) {
                "$bytesPerSec B/s"
            } else if (bytesPerSec < 1024L * 1024L) {
                val kb = bytesPerSec.toDouble() / 1024.0
                val formatted = round(kb * 10) / 10.0
                "$formatted KB/s"
            } else if (bytesPerSec < 10L * 1024L * 1024L) {
                val mb = bytesPerSec.toDouble() / (1024.0 * 1024.0)
                val formatted = round(mb * 100) / 100.0
                "$formatted MB/s"
            } else {
                val mb = bytesPerSec.toDouble() / (1024.0 * 1024.0)
                val formatted = round(mb * 10) / 10.0
                "$formatted MB/s"
            }
        }

        "KB/s" -> {
            val kb = bytesPerSec.toDouble() / 1024.0
            val formatted = round(kb * 10) / 10.0
            "$formatted KB/s"
        }

        "Kbps" -> {
            val bits = bytesPerSec.toDouble() * 8.0
            val kbps = bits / 1000.0
            val formatted = round(kbps * 10) / 10.0
            "$formatted Kbps"
        }

        else -> { // "Mbps"
            val bits = bytesPerSec.toDouble() * 8.0
            val mbps = bits / 1_000_000.0
            if (mbps < 0.1) {
                val kbps = bits / 1000.0
                val formatted = round(kbps * 10) / 10.0
                "$formatted Kbps"
            } else if (mbps < 10.0) {
                val formatted = round(mbps * 100) / 100.0
                "$formatted Mbps"
            } else {
                val formatted = round(mbps * 10) / 10.0
                "$formatted Mbps"
            }
        }
    }
}

/**
 * Decodes escaped Unicode sequences (such as \u0627 or _u0627) into actual readable characters.
 */
fun decodeUnicodeText(input: String): String {
    val regex = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")
    return regex.replace(input) { m ->
        try {
            val codePoint = m.groupValues[1].toInt(16)
            codePoint.toChar().toString()
        } catch (_: Exception) {
            m.value
        }
    }
}

/**
 * Formats total seconds into a HH:MM string (padded with leading zeros).
 */
fun formatSecondsToHhMm(totalSeconds: Long): String {
    // Always HH:MM:SS. A two-part "HH:MM" is read by yt-dlp / ffmpeg as MM:SS, so a
    // 10-minute clip came out as 10 seconds; three parts are unambiguous everywhere.
    val s = if (totalSeconds < 0L) 0L else totalSeconds
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return "${hours.toString().padStart(2, '0')}:${
        minutes.toString().padStart(2, '0')
    }:${seconds.toString().padStart(2, '0')}"
}

/**
 * Converts a HH:MM formatted string into descriptive text (e.g. "1h 30m").
 */
fun formatHhMmToEnglishText(hhMm: String): String {
    val parts = hhMm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val s = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val out = buildList {
        if (h > 0) add("${h}h")
        if (m > 0) add("${m}m")
        if (s > 0) add("${s}s")
    }
    return if (out.isEmpty()) "0s" else out.joinToString(" ")
}

/**
 * Parses a HH:MM or HH:MM:SS string into total seconds.
 */
fun parseHhMmToSeconds(hhMm: String): Long {
    val parts = hhMm.split(":")
    val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val s = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return (h * 3600L + m * 60L + s).coerceAtLeast(0L)
}

/**
 * Formats total seconds into a friendly description including hours, minutes, and seconds.
 */
fun formatSecondsToDisplayFriendly(totalSeconds: Long): String {
    val s = if (totalSeconds < 0L) 0L else totalSeconds
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return when {
        hours > 0 && minutes > 0 && seconds > 0 -> "${hours}h ${minutes}m ${seconds}s"
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 && seconds > 0 -> "${hours}h ${seconds}s"
        hours > 0 -> "${hours}h"
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}