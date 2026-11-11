package com.michelle.raouf.fetch_jet.core.setup

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import com.michelle.raouf.fetch_jet.platform.linux.linuxHome

/** Linux: xdg-mime (works on GNOME, KDE and the rest through mimeapps.list). */
actual object DefaultTorrentHandler {
    actual val supported: Boolean get() = DesktopApp.launcherPath != null
    actual val opensSystemPage: Boolean = false

    private suspend fun run(vararg command: String) =
        runCatching { DesktopOs.runProcess(command.toList(), timeoutMs = 15_000) }.getOrNull()

    actual suspend fun isDefault(): Boolean? {
        val ids = LinuxDefaultApps.MIME_TYPES.map { type ->
            val result = run("xdg-mime", "query", "default", type) ?: return null
            if (result.exitCode != 0) return null
            result.stdout.trim()
        }
        return ids.all { LinuxDefaultApps.isOurs(it) }
    }

    actual suspend fun makeDefault(): Boolean {
        val dirs = LinuxDefaultApps.dataDirs({ DesktopOs.getEnv(it) }, linuxHome())
        var id = LinuxDefaultApps.pickDesktopId(dirs) { DesktopOs.readText(it) }
        if (id == null) {
            val exe = DesktopApp.launcherPath ?: return false
            val dir = dirs.first()
            DesktopOs.createDirectories(dir)
            if (!DesktopOs.writeText("$dir/${LinuxDefaultApps.OWN_ENTRY}", LinuxDefaultApps.entryText(exe))) return false
            run("update-desktop-database", dir)
            id = LinuxDefaultApps.OWN_ENTRY
        }
        return LinuxDefaultApps.MIME_TYPES.all { run("xdg-mime", "default", id, it)?.exitCode == 0 }
    }
}
