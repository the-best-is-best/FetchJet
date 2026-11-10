package com.michelle.raouf.fetch_jet.nativeapp.presenter

import kotlin.math.round

internal fun ltr(text: String): String = if (text.isEmpty()) text else "⁦$text⁩"

internal fun formatBytes(bytes: Long): String = ltr(formatBytesPlain(bytes))

private fun formatBytesPlain(bytes: Long): String {
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

internal fun formatSizeDisplay(sizeBytes: Long): String = ltr(formatSizeDisplayPlain(sizeBytes))

private fun formatSizeDisplayPlain(sizeBytes: Long): String {
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

internal fun formatSpeed(bytesPerSec: Long, unit: String = "MB/s"): String =
    ltr(formatSpeedPlain(bytesPerSec, unit))

private fun formatSpeedPlain(bytesPerSec: Long, unit: String): String {
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

        else -> {
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

private val unicodeEscape = Regex("""(?:\\u|_u)([0-9a-fA-F]{4})""")

internal fun decodeUnicodeText(input: String): String {
    if (!input.contains("\\u") && !input.contains("_u")) return input
    return unicodeEscape.replace(input) { m ->
        try {
            val codePoint = m.groupValues[1].toInt(16)
            codePoint.toChar().toString()
        } catch (_: Exception) {
            m.value
        }
    }
}

internal fun formatSecondsToHhMm(totalSeconds: Long): String {
    val s = if (totalSeconds < 0L) 0L else totalSeconds
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return "${hours.toString().padStart(2, '0')}:${
        minutes.toString().padStart(2, '0')
    }:${seconds.toString().padStart(2, '0')}"
}

internal fun parseHhMmToSeconds(hhMm: String): Long {
    val parts = hhMm.split(":")
    val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val s = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return (h * 3600L + m * 60L + s).coerceAtLeast(0L)
}

internal fun formatSecondsToDisplayFriendly(totalSeconds: Long): String {
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

internal val speedPresetUnits = listOf("KB/s", "MB/s", "Kbps", "Mbps")

internal fun speedPresetToBytes(value: Double, unit: String): Long = when (unit) {
    "KB/s" -> (value * 1024.0).toLong()
    "MB/s" -> (value * 1024.0 * 1024.0).toLong()
    "Kbps" -> (value * 1000.0 / 8.0).toLong()
    "Mbps" -> (value * 1000.0 * 1000.0 / 8.0).toLong()
    else -> (value * 1024.0 * 1024.0).toLong()
}

internal fun speedPresetValueText(bytes: Long, unit: String): String {
    if (bytes <= 0L) return ""
    val v = when (unit) {
        "KB/s" -> bytes.toDouble() / 1024.0
        "MB/s" -> bytes.toDouble() / (1024.0 * 1024.0)
        "Kbps" -> bytes.toDouble() * 8.0 / 1000.0
        "Mbps" -> bytes.toDouble() * 8.0 / (1000.0 * 1000.0)
        else -> bytes.toDouble() / (1024.0 * 1024.0)
    }
    return if (v == v.toLong().toDouble()) v.toLong().toString()
    else ((v * 10).toLong() / 10.0).toString()
}

internal fun speedPresetShortLabel(bytes: Long): String =
    if (bytes >= 1024L * 1024L) {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        val n = if (mb == mb.toLong().toDouble()) mb.toLong().toString()
        else ((mb * 10).toLong() / 10.0).toString()
        "$n MB/s"
    } else "${bytes / 1024L} KB/s"

internal fun startsRightToLeft(text: String): Boolean {
    for (ch in text) {
        val code = ch.code
        if (code in 0x0590..0x08FF || code in 0xFB1D..0xFDFF || code in 0xFE70..0xFEFF) return true
        if (ch.isLetter()) return false
    }
    return false
}

internal fun hasHanCharacters(text: String): Boolean {
    for (ch in text) {
        val code = ch.code
        if (code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF || code in 0x3040..0x30FF || code in 0xAC00..0xD7AF) return true
    }
    return false
}
