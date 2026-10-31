package com.michelle.raouf.fetch_jet.core.platform

/**
 * true when this is the Microsoft Store (MSIX) build: Windows installs it under
 * C:\Program Files\WindowsApps\. The Store updates it, so the in-app updater, the registry
 * based "start with Windows" and the automatic Edge setup are turned off there.
 */
object WindowsStorePackage {
    val isInstalled: Boolean by lazy {
        val path = System.getProperty("jpackage.app-path")
            ?: System.getProperty("compose.application.resources.dir")
            ?: System.getProperty("user.dir").orEmpty()
        path.contains("\\WindowsApps\\", ignoreCase = true)
    }

    /**
     * The Store build is started at login by Windows' own startup task, which passes no
     * arguments. Best guess instead: a plain launch within two minutes of boot came from it.
     */
    fun looksLikeLoginStart(): Boolean {
        if (!isInstalled) return false
        return try {
            val p = ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "[Environment]::TickCount64"
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroy(); return false
            }
            val upMs =
                out.lines().lastOrNull { it.isNotBlank() }?.trim()?.toLongOrNull() ?: return false
            upMs < 2 * 60 * 1000L
        } catch (_: Exception) {
            false
        }
    }
}
