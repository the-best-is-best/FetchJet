package com.michelle.raouf.fetch_jet.feature.http_downloader

/**
 * RFC 6266: `attachment; filename="a b.pdf"; size=3` or `filename*=UTF-8''a%20b.pdf`.
 * The extended form wins when present; parameters after the name are not part of it.
 */
internal fun contentDispositionFileName(header: String?): String? {
    if (header.isNullOrBlank()) return null
    val params = header.split(';').map { it.trim() }
    params.firstOrNull { it.startsWith("filename*=", ignoreCase = true) }?.let { p ->
        val raw = p.substringAfter('=').trim().trim('"')
        val encoded = raw.substringAfterLast("''", raw)
        val decoded = percentDecode(encoded)
        if (decoded.isNotBlank()) return decoded.substringAfterLast('/').substringAfterLast('\\')
    }
    params.firstOrNull { it.startsWith("filename=", ignoreCase = true) }?.let { p ->
        val name = p.substringAfter('=').trim().trim('"').trim()
        if (name.isNotBlank()) return name.substringAfterLast('/').substringAfterLast('\\')
    }
    return null
}

internal fun percentDecode(s: String): String {
    val bytes = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hex = s.substring(i + 1, i + 3)
            val v = hex.toIntOrNull(16)
            if (v != null) {
                bytes.add(v.toByte()); i += 3; continue
            }
        }
        for (b in c.toString().encodeToByteArray()) bytes.add(b)
        i++
    }
    return bytes.toByteArray().decodeToString()
}

internal fun HttpChunkDownloader.extractFileName(
    url: String,
    contentDisposition: String?,
    contentType: String?
): String {
    contentDispositionFileName(contentDisposition)?.let { return it }

    val urlPathLast = url.substringBefore("?").substringAfterLast("/").trim()
    val hasValidExtension =
        urlPathLast.contains(".") && urlPathLast.substringAfterLast(".").length in 2..5

    if (urlPathLast.isNotBlank() && hasValidExtension) {
        return urlPathLast
    }

    val type = contentType?.lowercase() ?: ""
    val extension = mapMimeTypeToExtension(type)

    val baseName =
        if (urlPathLast.isNotBlank()) urlPathLast.substringBefore(".") else "download_${timeSourceMs()}"
    return "$baseName$extension"
}

internal fun mapMimeTypeToExtension(contentType: String): String {
    return when {
        contentType.contains("application/pdf") -> ".pdf"
        contentType.contains("application/zip") || contentType.contains("x-zip") -> ".zip"
        contentType.contains("application/x-rar") -> ".rar"
        contentType.contains("application/x-7z") -> ".7z"
        contentType.contains("application/x-tar") || contentType.contains("gzip") -> ".tar.gz"
        contentType.contains("video/mp4") -> ".mp4"
        contentType.contains("video/x-matroska") -> ".mkv"
        contentType.contains("video/webm") -> ".webm"
        contentType.contains("audio/mpeg") -> ".mp3"
        contentType.contains("audio/aac") -> ".aac"
        contentType.contains("audio/wav") -> ".wav"
        contentType.contains("image/jpeg") -> ".jpg"
        contentType.contains("image/png") -> ".png"
        contentType.contains("image/gif") -> ".gif"
        contentType.contains("image/webp") -> ".webp"
        contentType.contains("application/json") -> ".json"
        contentType.contains("text/html") -> ".html"
        contentType.contains("text/plain") -> ".txt"
        else -> ".bin"
    }
}
