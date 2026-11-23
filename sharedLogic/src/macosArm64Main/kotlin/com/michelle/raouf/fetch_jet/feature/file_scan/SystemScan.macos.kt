package com.michelle.raouf.fetch_jet.feature.file_scan

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSPropertyListBinaryFormat_v1_0
import platform.Foundation.NSPropertyListSerialization
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
    if (!ok) println("quarantine: could not mark a file")
    markWhereFrom(path, listOfNotNull(webUrl(sourceUrl)?.absoluteString, pageUrl?.let { webUrl(it) }?.absoluteString))
}

@OptIn(ExperimentalForeignApi::class)
private fun markWhereFrom(path: String, sources: List<String>) {
    if (sources.isEmpty()) return
    val data = NSPropertyListSerialization.dataWithPropertyList(sources, NSPropertyListBinaryFormat_v1_0, 0u, null) ?: return
    val attributes = mapOf<Any?, Any?>("NSFileExtendedAttributes" to mapOf("com.apple.metadata:kMDItemWhereFroms" to data))
    if (!NSFileManager.defaultManager.setAttributes(attributes, path, null)) {
        println("quarantine: could not write where from on a file")
    }
}

private fun webUrl(text: String): NSURL? =
    text.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("ftp://", true) }
        ?.let { NSURL.URLWithString(it) }
