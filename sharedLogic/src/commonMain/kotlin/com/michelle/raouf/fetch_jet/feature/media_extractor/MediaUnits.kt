package com.michelle.raouf.fetch_jet.feature.media_extractor

internal fun ytDlpUnitMultiplier(unit: String, fallback: Double): Double = when {
    unit.contains("gib") || unit.contains("gb") -> 1024.0 * 1024.0 * 1024.0
    unit.contains("mib") || unit.contains("mb") -> 1024.0 * 1024.0
    unit.contains("kib") || unit.contains("kb") -> 1024.0
    else -> fallback
}

internal fun ytDlpUnitPrefixMultiplier(unit: String): Double = when {
    unit.startsWith("g") -> 1024.0 * 1024.0 * 1024.0
    unit.startsWith("m") -> 1024.0 * 1024.0
    unit.startsWith("k") -> 1024.0
    else -> 1.0
}
