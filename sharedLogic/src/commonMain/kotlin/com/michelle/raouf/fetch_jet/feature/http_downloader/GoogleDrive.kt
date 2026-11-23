package com.michelle.raouf.fetch_jet.feature.http_downloader

/** What a Drive file that cannot be downloaded fails with (translated where it is shown). */
const val GOOGLE_DRIVE_NO_DOWNLOAD_ERROR =
    "Google Drive did not send the file: its owner turned off downloading, or it is private"

private val DRIVE_FILE_PATH = Regex("""^https?://drive\.google\.com/file/(?:u/\d+/)?d/([\w-]{10,})""", RegexOption.IGNORE_CASE)
private val DRIVE_ID_QUERY = Regex("""^https?://(?:drive\.google\.com/(?:uc|open)|drive\.usercontent\.google\.com/(?:u/\d+/)?(?:download|uc))\?(?:.*&)?id=([\w-]{10,})""", RegexOption.IGNORE_CASE)

/** The file id of a Google Drive file link (view page, uc / open link, usercontent download), or null. */
fun googleDriveFileId(url: String): String? {
    val u = url.trim()
    return DRIVE_FILE_PATH.find(u)?.groupValues?.get(1) ?: DRIVE_ID_QUERY.find(u)?.groupValues?.get(1)
}

/**
 * The link that sends a Drive file itself: `confirm=t` answers Drive's "too large to scan for
 * viruses, download anyway?" page the way the browser button does. A file whose owner turned
 * off downloading still answers with a page, which [isGoogleDriveLink] lets the engine name.
 */
fun googleDriveDownloadUrl(url: String): String? {
    val id = googleDriveFileId(url) ?: return null
    return "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"
}

fun isGoogleDriveLink(url: String): Boolean {
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()
    return host == "drive.google.com" || host == "drive.usercontent.google.com" || host == "docs.google.com"
}
