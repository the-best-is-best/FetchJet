package com.michelle.raouf.fetch_jet.feature.file_scan

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.Foundation.NSURLQuarantinePropertiesKey

internal actual fun systemScannerAvailable(): Boolean = false

internal actual suspend fun systemScan(path: String, sourceUrl: String): ScanResult? = null

@OptIn(ExperimentalForeignApi::class)
internal actual fun systemMarkFromInternet(path: String, sourceUrl: String, pageUrl: String?) {
    val properties = mutableMapOf<Any?, Any?>(
        "LSQuarantineAgentName" to "FetchJet",
        "LSQuarantineType" to "LSQuarantineTypeWebDownload",
    )
    webUrl(sourceUrl)?.let { properties["LSQuarantineDataURL"] = it }
    pageUrl?.let { webUrl(it) }?.let { properties["LSQuarantineOriginURL"] = it }
    val ok = NSURL.fileURLWithPath(path).setResourceValue(properties, NSURLQuarantinePropertiesKey, null)
    if (!ok) println("quarantine: could not mark $path")
}

private fun webUrl(text: String): NSURL? =
    text.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("ftp://", true) }
        ?.let { NSURL.URLWithString(it) }
