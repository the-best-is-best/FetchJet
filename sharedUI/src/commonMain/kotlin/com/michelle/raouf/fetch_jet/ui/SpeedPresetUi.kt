package com.michelle.raouf.fetch_jet.ui

/** The speed units a preset can be entered in. Bits (Kbps/Mbps) are 1/8 of the byte units. */
val speedPresetUnits = listOf("KB/s", "MB/s", "Kbps", "Mbps")

/** A typed number in a chosen unit → bytes per second. */
fun speedPresetToBytes(value: Double, unit: String): Long = when (unit) {
    "KB/s" -> (value * 1024.0).toLong()
    "MB/s" -> (value * 1024.0 * 1024.0).toLong()
    "Kbps" -> (value * 1000.0 / 8.0).toLong()
    "Mbps" -> (value * 1000.0 * 1000.0 / 8.0).toLong()
    else -> (value * 1024.0 * 1024.0).toLong()
}

/** Bytes per second shown as a number in the given unit (for the editor field). */
fun speedPresetValueText(bytes: Long, unit: String): String {
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

/** A short label for a preset's speed, e.g. "20 MB/s" or "512 KB/s". */
fun speedPresetShortLabel(bytes: Long): String =
    if (bytes >= 1024L * 1024L) {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        val n = if (mb == mb.toLong().toDouble()) mb.toLong().toString()
        else ((mb * 10).toLong() / 10.0).toString()
        "$n MB/s"
    } else "${bytes / 1024L} KB/s"
