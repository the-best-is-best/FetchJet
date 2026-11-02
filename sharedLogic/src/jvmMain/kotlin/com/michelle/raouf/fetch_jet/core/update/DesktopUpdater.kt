package com.michelle.raouf.fetch_jet.core.update

import java.io.File
import kotlin.system.exitProcess

internal enum class DesktopOs {
    WINDOWS, MAC, LINUX;

    companion object {
        val current: DesktopOs = System.getProperty("os.name").orEmpty().lowercase().let {
            when {
                it.contains("win") -> WINDOWS
                it.contains("mac") -> MAC
                else -> LINUX
            }
        }
    }
}

/** How one desktop OS installs a downloaded release over the running FetchJet. */
internal interface DesktopUpdater {
    val platformKey: String
    val canInstallInApp: Boolean
    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset?

    /** Starts the install in a separate process, then quits FetchJet. Does not return. */
    fun installAndExit(installer: File)
}

internal object DesktopInstall {
    /** Path of the installed launcher; null when run from Gradle / an IDE. */
    val launcherPath: String? =
        System.getProperty("jpackage.app-path")?.takeIf { File(it).exists() }

    fun firstAsset(assets: List<ReleaseAsset>, vararg extensions: String): ReleaseAsset? =
        assets.firstOrNull { a -> extensions.any { a.name.endsWith(it, ignoreCase = true) } }

    fun hasCommand(name: String): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, name).canExecute() }

    /** Single-quotes a value for a bash script. */
    fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Writes `fetchjet-update.sh` next to the installer and runs it in the background: it waits
     * until this process has quit, runs [body], then deletes itself. Output goes to
     * `fetchjet-update.log` in the same folder.
     */
    fun runShellAfterExit(installer: File, body: String): Nothing {
        val pid = ProcessHandle.current().pid()
        val script = File(installer.parentFile, "fetchjet-update.sh")
        script.writeText(
            "#!/bin/bash\n" +
                    "while kill -0 $pid 2>/dev/null; do sleep 1; done\n" +
                    body.trimIndent() + "\n" +
                    "rm -f \"\$0\"\n"
        )
        script.setExecutable(true)
        ProcessBuilder("/bin/bash", script.absolutePath)
            .directory(installer.parentFile)
            .redirectOutput(File(installer.parentFile, "fetchjet-update.log"))
            .redirectErrorStream(true)
            .start()
        exitProcess(0)
    }
}
