package com.michelle.raouf.fetch_jet.core.setup

/**
 * Linux rules shared by the JVM and the native app: `xdg-mime query default` /
 * `xdg-mime default` on the two types. The process and file access are per platform.
 */
object LinuxDefaultApps {
    val MIME_TYPES = listOf("x-scheme-handler/$MAGNET_SCHEME", TORRENT_MIME)
    const val OWN_ENTRY = "fetchjet-links.desktop"
    private val KNOWN_ENTRIES = listOf("fetchjet.desktop", "fetchjet-FetchJet.desktop", OWN_ENTRY)

    fun isOurs(desktopId: String?): Boolean = desktopId != null && desktopId.trim().contains("fetchjet", ignoreCase = true)

    fun dataDirs(env: (String) -> String?, home: String): List<String> {
        val user = env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
        val system = (env("XDG_DATA_DIRS")?.takeIf { it.isNotBlank() } ?: "/usr/local/share:/usr/share").split(':')
        return (listOf(user) + system).filter { it.isNotBlank() }.map { it.trimEnd('/') + "/applications" }
    }

    /** An installed FetchJet entry that passes the link / file on (`%u` / `%U` / `%f` / `%F`). */
    fun pickDesktopId(applicationDirs: List<String>, readText: (String) -> String?): String? {
        for (name in KNOWN_ENTRIES) for (dir in applicationDirs) {
            val text = readText("$dir/$name") ?: continue
            val exec = text.lineSequence().firstOrNull { it.trim().startsWith("Exec=") } ?: continue
            if (listOf("%u", "%U", "%f", "%F").any { exec.contains(it) }) return name
        }
        return null
    }

    fun entryText(exe: String): String {
        val quoted = StringBuilder("\"")
        for (c in exe) {
            if (c == '"' || c == '\\' || c == '$' || c.code == 96) quoted.append('\\')
            quoted.append(c)
        }
        quoted.append('"')
        return buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=FetchJet")
            appendLine("Exec=$quoted %u")
            appendLine("Icon=fetchjet")
            appendLine("Terminal=false")
            appendLine("NoDisplay=true")
            appendLine("MimeType=${MIME_TYPES.joinToString(";")};")
        }
    }
}
