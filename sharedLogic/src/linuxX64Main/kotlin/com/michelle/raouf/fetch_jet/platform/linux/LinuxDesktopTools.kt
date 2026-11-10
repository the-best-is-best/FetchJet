package com.michelle.raouf.fetch_jet.platform.linux

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.findOnPath
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import kotlinx.coroutines.runBlocking

object LinuxDesktopTools {
    private fun onPath(command: String): String? = DesktopOs.findOnPath(command)

    private val onWayland: Boolean get() = !linuxGetEnv("WAYLAND_DISPLAY").isNullOrBlank()

    private fun readCommand(): List<String>? = when {
        onWayland && onPath("wl-paste") != null -> listOf("wl-paste", "--no-newline", "--type", "text")
        onPath("xclip") != null -> listOf("xclip", "-o", "-selection", "clipboard")
        onPath("xsel") != null -> listOf("xsel", "--clipboard", "--output")
        onPath("wl-paste") != null -> listOf("wl-paste", "--no-newline", "--type", "text")
        else -> null
    }

    private fun writeCommand(): List<String>? = when {
        onWayland && onPath("wl-copy") != null -> listOf("wl-copy")
        onPath("xclip") != null -> listOf("xclip", "-i", "-selection", "clipboard")
        onPath("xsel") != null -> listOf("xsel", "--clipboard", "--input")
        onPath("wl-copy") != null -> listOf("wl-copy")
        else -> null
    }

    val hasClipboard: Boolean get() = readCommand() != null

    fun clipboardText(): String? {
        val command = readCommand() ?: return null
        return try {
            val result = runBlocking { DesktopOs.runProcess(command, timeoutMs = 2_000) }
            if (result.timedOut || result.exitCode != 0) null else result.stdout.removeSuffix("\n")
        } catch (_: Throwable) {
            null
        }
    }

    fun setClipboardText(text: String): Boolean {
        val command = writeCommand() ?: return false
        return try {
            val result = runBlocking { DesktopOs.runProcess(command, stdinBytes = text.encodeToByteArray(), timeoutMs = 2_000) }
            !result.timedOut && result.exitCode == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun revealInFileManager(path: String): Boolean {
        val uri = "file://" + path.split('/').joinToString("/") { part -> percentEncode(part) }
        if (onPath("gdbus") != null) {
            val shown = try {
                runBlocking {
                    DesktopOs.runProcess(
                        listOf(
                            "gdbus", "call", "--session", "--timeout", "3", "--dest", "org.freedesktop.FileManager1",
                            "--object-path", "/org/freedesktop/FileManager1", "--method", "org.freedesktop.FileManager1.ShowItems",
                            "['" + uri.replace("'", "%27") + "']", ""
                        ),
                        mergeStderr = true, timeoutMs = 5_000
                    )
                }
            } catch (_: Throwable) {
                null
            }
            if (shown != null && !shown.timedOut && shown.exitCode == 0) return true
        }
        val folder = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
        return linuxSpawnDetached(listOf("xdg-open", folder))
    }

    private fun percentEncode(part: String): String {
        val out = StringBuilder()
        for (byte in part.encodeToByteArray()) {
            val v = byte.toInt() and 0xFF
            val c = v.toChar()
            if (c.isLetterOrDigit() && v < 0x80 || c in "-._~") out.append(c)
            else out.append('%').append("0123456789ABCDEF"[v shr 4]).append("0123456789ABCDEF"[v and 15])
        }
        return out.toString()
    }
}
