package com.michelle.raouf.fetch_jet.feature.file_scan

internal actual fun systemMarkFromInternet(path: String, sourceUrl: String, pageUrl: String?) = Unit

internal actual fun systemScannerAvailable(): Boolean = false

internal actual suspend fun systemScan(path: String, sourceUrl: String): ScanResult? = null
