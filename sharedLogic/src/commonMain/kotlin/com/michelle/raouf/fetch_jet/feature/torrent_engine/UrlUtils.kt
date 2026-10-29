package com.michelle.raouf.fetch_jet.feature.torrent_engine

/**
 * A .torrent path as users paste it: with the quotes Windows' "Copy as path" adds, or as a
 * file:///C:/x%20y.torrent URI. Returns a plain filesystem path; other strings come back
 * trimmed and unquoted.
 */
fun normalizeLocalTorrentPath(raw: String): String {
    var s = raw.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    if (s.startsWith("file:", ignoreCase = true)) {
        s = s.substring(5).trimStart('/')
        s = percentDecodePath(s)
        // "C:/..." stays; a POSIX path lost its leading slash above.
        if (!(s.length >= 2 && s[1] == ':')) s = "/$s"
    }
    return s
}

/** True when [s] is a path to a .torrent file (not a magnet or http link). */
fun isLocalTorrentPath(s: String): Boolean {
    val p = normalizeLocalTorrentPath(s).lowercase()
    return p.endsWith(".torrent") && !p.startsWith("http://") && !p.startsWith("https://") &&
            !p.startsWith("magnet:")
}

private fun percentDecodePath(s: String): String {
    if (!s.contains('%')) return s
    val out = StringBuilder()
    val bytes = ArrayList<Byte>()
    var i = 0
    fun flush() {
        if (bytes.isNotEmpty()) {
            out.append(bytes.toByteArray().decodeToString()); bytes.clear()
        }
    }
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte()); i += 3; continue
            }
        }
        flush(); out.append(c); i++
    }
    flush()
    return out.toString()
}

fun extractReferer(urlStr: String): String {
    return try {
        val schemeEnd = urlStr.indexOf("://")
        if (schemeEnd != -1) {
            val nextSlash = urlStr.indexOf('/', schemeEnd + 3)
            if (nextSlash != -1) {
                urlStr.substring(0, nextSlash + 1)
            } else {
                "$urlStr/"
            }
        } else {
            urlStr
        }
    } catch (_: Exception) {
        urlStr
    }
}