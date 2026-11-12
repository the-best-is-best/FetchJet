package com.michelle.raouf.fetch_jet.feature.http_downloader

internal class ServerBusyException(message: String) : Exception(message)

internal data class ResolvedHttpMetadata(
    val finalUrl: String,
    val statusCode: Int,
    val statusDescription: String,
    val contentType: String,
    val contentLength: Long,
    val acceptRanges: Boolean,
    val contentDisposition: String?,
    val etag: String = "",
    val lastModified: String = "",
    val pageText: String = "",
    val serverChecksum: ServerChecksum? = null
) {
    val identity: FileIdentity get() = FileIdentity(etag, lastModified, contentLength)
}

/** What tells one version of a file on a server from another. */
internal class FileIdentity(val etag: String, val lastModified: String, val size: Long) {
    /** The value for an If-Range header: a strong ETag, else Last-Modified (a weak ETag never matches). */
    val ifRange: String get() = etag.takeIf { it.isNotEmpty() && !it.startsWith("W/", ignoreCase = true) } ?: lastModified

    /** Size first, then Last-Modified, then the ETag (servers behind a load balancer may give every node its own ETag). */
    fun sameAs(other: FileIdentity): Boolean {
        if (size > 0 && other.size > 0 && size != other.size) return false
        if (lastModified.isNotEmpty() && other.lastModified.isNotEmpty()) return lastModified == other.lastModified
        if (etag.isNotEmpty() && other.etag.isNotEmpty()) return etag == other.etag
        return true
    }

    fun encode(): String = "$etag\n$lastModified\n$size"

    companion object {
        fun decode(text: String): FileIdentity? {
            val lines = text.lines()
            if (lines.size < 3) return null
            return FileIdentity(lines[0].trim(), lines[1].trim(), lines[2].trim().toLongOrNull() ?: -1L)
        }
    }
}

/** The If-Range value the pieces send; emptied when the server proves it cannot match its own validator. */
internal class IfRangeState(@kotlin.concurrent.Volatile var value: String)
