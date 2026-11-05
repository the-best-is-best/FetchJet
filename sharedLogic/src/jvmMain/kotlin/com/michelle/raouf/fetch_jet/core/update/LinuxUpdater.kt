package com.michelle.raouf.fetch_jet.core.update

import java.io.File
import com.michelle.raouf.fetch_jet.core.update.DesktopInstall.shellQuote as q

/**
 * Two kinds of Linux install:
 * - `.deb` (the app runs from `/opt/`): `pkexec dpkg -i` asks for the password and installs it.
 * - portable `.tar.gz`: the new folder replaces the old one.
 */
internal object LinuxUpdater : DesktopUpdater {
    override val platformKey = "linux"

    private val launcherPath = DesktopInstall.launcherPath

    private val debInstall: Boolean = launcherPath?.startsWith("/opt/") == true

    /** Root of the portable build (the folder that holds `bin/`). */
    private val portableRoot: File? =
        if (debInstall) null else launcherPath?.let { File(it).parentFile?.parentFile }

    override val canInstallInApp: Boolean =
        if (debInstall) DesktopInstall.hasCommand("pkexec") && DesktopInstall.hasCommand("dpkg")
        else portableRoot?.parentFile?.canWrite() == true

    override fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        if (portableRoot != null) DesktopInstall.firstAsset(assets, ".tar.gz")
        else DesktopInstall.firstAsset(assets, ".deb")
            ?: DesktopInstall.firstAsset(assets, ".AppImage", ".tar.gz")

    override fun installAndExit(installer: File) {
        DesktopInstall.runShellAfterExit(
            installer,
            if (debInstall) debScript(installer) else portableScript(installer)
        )
    }

    private fun debScript(installer: File) = """
        pkexec dpkg -i ${q(installer.absolutePath)}
        nohup ${q(launcherPath!!)} >/dev/null 2>&1 &
        """

    private fun portableScript(installer: File): String {
        val root = checkNotNull(portableRoot) { "FetchJet folder not found" }.absolutePath
        val launcher = File(launcherPath!!).name
        return """
            TMP=${'$'}(mktemp -d /tmp/fetchjet-update.XXXXXX)
            if tar xzf ${q(installer.absolutePath)} -C "${'$'}TMP"; then
              SRC=${'$'}(find "${'$'}TMP" -mindepth 1 -maxdepth 1 -type d -print -quit)
              if [ -x "${'$'}SRC/bin/$launcher" ]; then
                rm -rf ${q("$root.old")} && mv ${q(root)} ${q("$root.old")} && mv "${'$'}SRC" ${
            q(
                root
            )
        } && rm -rf ${q("$root.old")}
              fi
            fi
            rm -rf "${'$'}TMP"
            nohup ${q("$root/bin/$launcher")} >/dev/null 2>&1 &
            """
    }
}
