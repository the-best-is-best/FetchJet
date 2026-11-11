package com.michelle.raouf.fetch_jet.core.setup

import java.io.File

/** Linux (JVM app): xdg-mime. */
internal object LinuxDefaultHandler {
    private fun home() = System.getProperty("user.home").orEmpty()

    fun isDefault(): Boolean? {
        val ids = LinuxDefaultApps.MIME_TYPES.map { type ->
            val result = jvmRun("xdg-mime", "query", "default", type) ?: return null
            if (result.code != 0) return null
            result.out.trim()
        }
        return ids.all { LinuxDefaultApps.isOurs(it) }
    }

    fun makeDefault(launcher: String?): Boolean {
        val dirs = LinuxDefaultApps.dataDirs({ System.getenv(it) }, home())
        var id = LinuxDefaultApps.pickDesktopId(dirs) { path -> runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull() }
        if (id == null) {
            val exe = launcher ?: return false
            val dir = File(dirs.first())
            dir.mkdirs()
            File(dir, LinuxDefaultApps.OWN_ENTRY).writeText(LinuxDefaultApps.entryText(exe))
            jvmRun("update-desktop-database", dir.path)
            id = LinuxDefaultApps.OWN_ENTRY
        }
        return LinuxDefaultApps.MIME_TYPES.all { jvmRun("xdg-mime", "default", id, it)?.code == 0 }
    }
}
