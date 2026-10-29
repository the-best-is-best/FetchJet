package com.michelle.raouf.fetch_jet.core.update

import java.io.File
import kotlin.system.exitProcess

actual object PlatformUpdater {
    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val isWindows = os.contains("win")
    private val isMac = os.contains("mac")

    /** Path of the installed launcher (FetchJet.exe); null when run from Gradle / an IDE. */
    private val launcherPath: String? =
        System.getProperty("jpackage.app-path")?.takeIf { File(it).exists() }

    /**
     * Only the installed Windows build updates itself: its MSI has a fixed upgrade id, so a
     * newer MSI replaces it in place (per-user install, no admin prompt).
     */
    actual val canInstallInApp: Boolean = isWindows && launcherPath != null

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        fun first(vararg ext: String) =
            assets.firstOrNull { a -> ext.any { a.name.endsWith(it, ignoreCase = true) } }
        return when {
            isWindows -> first(".msi") ?: first(".exe")
            isMac -> first(".dmg")
            else -> first(".deb") ?: first(".AppImage", ".tar.gz")
        }
    }

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> = runCatching {
        check(isWindows) { "in-app install is only available on Windows" }
        val installer = File(installerPath)
        check(installer.exists()) { "installer not found" }
        // A small batch file runs after FetchJet has quit: waits a moment so our files are
        // released, installs over the old version, then starts the new one.
        val install = if (installer.name.endsWith(".msi", ignoreCase = true))
            "msiexec /i \"${installer.absolutePath}\" /passive /norestart"
        else
            "\"${installer.absolutePath}\""
        val relaunch = launcherPath?.let { "start \"\" \"$it\"" } ?: ""
        val script = File(installer.parentFile, "fetchjet-update.cmd")
        // Wait until this process is really gone (shutdown can take a few seconds), else the
        // installer finds FetchJet's files in use.
        val pid = ProcessHandle.current().pid()
        script.writeText(
            "@echo off\r\n" +
                    ":wait\r\n" +
                    "tasklist /FI \"PID eq $pid\" 2>nul | find \"$pid\" >nul && (timeout /t 1 /nobreak >nul & goto wait)\r\n" +
                    "$install\r\n" +
                    "$relaunch\r\n" +
                    "del \"%~f0\"\r\n"
        )
        ProcessBuilder("cmd.exe", "/c", "start", "\"FetchJet update\"", "/min", script.absolutePath)
            .directory(installer.parentFile)
            .start()
        exitProcess(0)
    }
}
