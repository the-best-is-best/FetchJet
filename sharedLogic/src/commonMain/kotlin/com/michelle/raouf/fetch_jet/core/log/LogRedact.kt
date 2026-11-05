package com.michelle.raouf.fetch_jet.core.log

/**
 * What the log files (desktop app.log, Android's exported log) must not keep: Cookie /
 * Authorization values, a login inside a URL (`http://user:pass@`), and the values of a URL's
 * query (signed links: googlevideo `sig=`, session tokens). Parameter names stay, so a log
 * still shows which link it was.
 */
object LogRedact {
    private val header = Regex(
        """(?i)\b(set-cookie|cookie|proxy-authorization|authorization)(["']?\s*[:=]\s*)[^\r\n]*"""
    )
    private val userInfo = Regex("""(?i)\b([a-z][a-z0-9+.-]*://)[^/\s:@'"]+:[^/\s@'"]+@""")
    private val query = Regex("""(https?://[^\s?#"'<>]+)\?([^\s#"'<>]+)""")

    fun redact(line: String): String {
        var s = line
        if (s.contains("cookie", true) || s.contains("authorization", true)) {
            s = header.replace(s) { it.groupValues[1] + it.groupValues[2] + "***" }
        }
        if ('@' in s) s = userInfo.replace(s) { it.groupValues[1] + "***@" }
        if ('?' in s) s = query.replace(s) { m ->
            m.groupValues[1] + "?" + m.groupValues[2].split('&').joinToString("&") { p ->
                if ('=' in p) p.substringBefore('=') + "=…" else p
            }
        }
        return s
    }
}
