package com.michelle.raouf.fetch_jet.feature.http_downloader

/** Where an ftp:// / ftps:// / ftpes:// link points and how to talk to it. */
internal class FtpTarget(
    val host: String,
    val port: Int,
    /** The path as the user wrote it, without the first slash (relative to the login folder, RFC 1738). */
    val path: String,
    /** The control and data connections are encrypted. */
    val tls: Boolean,
    /** TLS from the first byte (port 990 style); otherwise AUTH TLS on the plain connection. */
    val implicitTls: Boolean,
    val user: String?,
    val password: String?,
) {
    val fileName: String get() = path.trimEnd('/').substringAfterLast('/')
    val isFolder: Boolean get() = path.isEmpty() || path.endsWith("/")
    val absolutePath: String get() = "/$path"
}

internal fun isFtpUrl(url: String): Boolean {
    val s = url.trimStart()
    return s.startsWith("ftp://", ignoreCase = true) || s.startsWith("ftps://", ignoreCase = true) ||
            s.startsWith("ftpes://", ignoreCase = true)
}

fun isFtpLink(url: String): Boolean = isFtpUrl(url)

fun ftpFileName(url: String): String =
    try {
        if (isFtpUrl(url)) parseFtpUrl(url).fileName else ""
    } catch (_: Exception) {
        ""
    }

fun displayUrl(url: String): String {
    val start = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return url
    val end = url.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) url.length else it }
    if (end <= start) return url
    val at = url.lastIndexOf('@', end - 1)
    if (at < start) return url
    val colon = url.indexOf(':', start)
    if (colon < 0 || colon > at) return url
    return url.substring(0, colon + 1) + "***" + url.substring(at)
}

/**
 * ftp://[user[:password]@]host[:port]/path - plain. ftps:// is TLS from the first byte (default port 990,
 * like curl and FileZilla; port 21 means explicit TLS); ftpes:// is explicit TLS (AUTH TLS), default port 21.
 */
internal fun parseFtpUrl(url: String): FtpTarget {
    val text = url.trim()
    val scheme = text.substringBefore("://").lowercase()
    require(scheme == "ftp" || scheme == "ftps" || scheme == "ftpes") { "Not an FTP link" }
    val afterScheme = text.substringAfter("://")
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val rawPath = afterScheme.removePrefix(authority).substringBefore('?').substringBefore('#')
    val at = authority.lastIndexOf('@')
    val userInfo = if (at >= 0) authority.substring(0, at) else ""
    val hostPort = if (at >= 0) authority.substring(at + 1) else authority
    val host: String
    var explicitPort: Int? = null
    if (hostPort.startsWith("[")) {
        host = hostPort.substringAfter('[').substringBefore(']')
        explicitPort = hostPort.substringAfter(']', "").removePrefix(":").toIntOrNull()
    } else {
        host = hostPort.substringBefore(':')
        explicitPort = hostPort.substringAfter(':', "").toIntOrNull()
    }
    require(host.isNotBlank()) { "The FTP link has no server name" }
    val tls = scheme != "ftp"
    val port = explicitPort ?: if (scheme == "ftps") 990 else 21
    val implicit = scheme == "ftps" && port != 21
    val user = userInfo.substringBefore(':').takeIf { userInfo.isNotEmpty() && it.isNotEmpty() }?.let(::percentDecode)
    val password = if (userInfo.contains(':')) percentDecode(userInfo.substringAfter(':')) else null
    val path = percentDecode(rawPath.removePrefix("/"))
    return FtpTarget(host, port, path, tls, implicit, user, password)
}
