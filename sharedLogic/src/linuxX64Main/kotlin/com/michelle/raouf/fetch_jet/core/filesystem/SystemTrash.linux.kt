package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.linux.linuxGetEnv

internal actual fun osMoveToTrash(path: String): Boolean {
    val dataHome = linuxGetEnv("XDG_DATA_HOME")?.takeIf { it.startsWith("/") }
        ?: (linuxGetEnv("HOME") ?: return false).trimEnd('/') + "/.local/share"
    val files = "$dataHome/Trash/files"
    val info = "$dataHome/Trash/info"
    if (!DesktopOs.createDirectories(files) || !DesktopOs.createDirectories(info)) return false
    val name = path.trimEnd('/').substringAfterLast('/')
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    var candidate = name
    var n = 1
    while (DesktopOs.exists("$files/$candidate") || DesktopOs.exists("$info/$candidate.trashinfo")) {
        candidate = name.substring(0, dot) + " ($n)" + name.substring(dot)
        n++
    }
    val record = "[Trash Info]\nPath=${encodePath(path)}\nDeletionDate=${deletionDate()}\n"
    if (!DesktopOs.writeText("$info/$candidate.trashinfo", record)) return false
    if (DesktopOs.move(path, "$files/$candidate", replace = false)) return true
    DesktopOs.deleteFile("$info/$candidate.trashinfo")
    return false
}

private fun encodePath(path: String): String = buildString {
    for (byte in path.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val keep = (c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c.toChar() in "/-_.~"
        if (keep) append(c.toChar()) else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}

private fun deletionDate(): String {
    val local = currentEpochMs() + DesktopOs.localUtcOffsetMs()
    val seconds = local / 1000
    val days = seconds / 86400
    val rest = seconds % 86400
    val z = days + 719468
    val era = z / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    fun two(v: Long) = v.toString().padStart(2, '0')
    return "$year-${two(month)}-${two(day)}T${two(rest / 3600)}:${two(rest % 3600 / 60)}:${two(rest % 60)}"
}
