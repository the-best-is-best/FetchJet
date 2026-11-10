package com.michelle.raouf.fetch_jet.core.startup

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.linux.linuxHome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

actual object StartupLauncher {
    private const val FILE_NAME = "fetchjet.desktop"

    private fun autostartDir(): String {
        val config = DesktopOs.getEnv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: (linuxHome() + "/.config")
        return DesktopOs.join(config, "autostart")
    }

    private fun entryFile(): String = DesktopOs.join(autostartDir(), FILE_NAME)

    private fun quoted(path: String): String {
        val out = StringBuilder("\"")
        for (c in path) {
            if (c == '"' || c == '\\' || c == '$' || c.code == 96) out.append('\\')
            out.append(c)
        }
        return out.append('"').toString()
    }

    actual val isSupported: Boolean
        get() = DesktopApp.launcherPath != null

    actual val managedBySystemUri: String? = null

    actual suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        val exe = DesktopApp.launcherPath ?: return@withContext false
        val text = DesktopOs.readText(entryFile()) ?: return@withContext false
        text.contains(exe) && text.contains(AUTOSTART_ARG) && text.lineSequence().none { it.trim() == "Hidden=true" }
    }

    actual suspend fun setEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (enabled) {
                val exe = DesktopApp.launcherPath ?: error("FetchJet is not installed")
                check(DesktopOs.exists(exe)) { "FetchJet was not found" }
                check(DesktopOs.createDirectories(autostartDir())) { "could not create ${autostartDir()}" }
                val text = buildString {
                    appendLine("[Desktop Entry]")
                    appendLine("Type=Application")
                    appendLine("Name=FetchJet")
                    appendLine("Exec=${quoted(exe)} $AUTOSTART_ARG")
                    appendLine("Icon=fetchjet")
                    appendLine("Terminal=false")
                    appendLine("X-GNOME-Autostart-enabled=true")
                }
                check(DesktopOs.writeText(entryFile(), text)) { "could not write the start-at-login entry" }
            } else if (DesktopOs.exists(entryFile())) {
                check(DesktopOs.deleteFile(entryFile())) { "could not remove the start-at-login entry" }
            }
        }
    }
}
