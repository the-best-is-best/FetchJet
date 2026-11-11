package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.findOnPath
import com.michelle.raouf.fetch_jet.platform.desktop.startDetached
import kotlin.system.exitProcess
import platform.posix.W_OK
import platform.posix.access
import platform.posix.getpid

internal object LinuxUpdater {
    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private val launcher: String? get() = DesktopApp.launcherPath

    private val debInstall: Boolean get() = launcher?.startsWith("/opt/") == true

    private val portableRoot: String?
        get() = if (debInstall) null else launcher?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }

    val canInstallInApp: Boolean
        get() = if (debInstall) {
            DesktopOs.findOnPath("pkexec") != null && DesktopOs.findOnPath("dpkg") != null
        } else {
            val root = portableRoot ?: return false
            val parent = root.substringBeforeLast('/', "").ifEmpty { "/" }
            access(parent, W_OK) == 0
        }

    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        val deb = assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
        val tar = assets.firstOrNull { it.name.endsWith(".tar.gz", ignoreCase = true) && it.name.contains("linux", ignoreCase = true) }
        return if (portableRoot != null) tar ?: deb else deb ?: tar
    }

    fun installAndExit(installerPath: String) {
        val exe = checkNotNull(launcher) { "FetchJet program not found" }
        val folder = installerPath.substringBeforeLast('/', "").ifEmpty { DesktopOs.tempDir() }
        val script = "$folder/fetchjet-update.sh"
        val log = "$folder/fetchjet-update.log"
        val dollar = '$'
        val body = if (debInstall) {
            """
            pkexec dpkg -i ${q(installerPath)}
            nohup ${q(exe)} >/dev/null 2>&1 &
            """
        } else {
            val root = checkNotNull(portableRoot) { "FetchJet folder not found" }
            val name = exe.substringAfterLast('/')
            """
            TMP=$dollar(mktemp -d /tmp/fetchjet-update.XXXXXX)
            if tar xzf ${q(installerPath)} -C "${dollar}TMP"; then
              SRC=$dollar(find "${dollar}TMP" -mindepth 1 -maxdepth 1 -type d -print -quit)
              if [ -x "${dollar}SRC/$name" ]; then
                rm -rf ${q("$root.old")} && mv ${q(root)} ${q("$root.old")} && mv "${dollar}SRC" ${q(root)} && rm -rf ${q("$root.old")}
              fi
            fi
            rm -rf "${dollar}TMP"
            nohup ${q("$root/$name")} >/dev/null 2>&1 &
            """
        }
        val text = "#!/bin/bash\n" +
            "while kill -0 ${getpid()} 2>/dev/null; do sleep 1; done\n" +
            body.trimIndent() + "\n" +
            "rm -f \"${dollar}0\"\n"
        check(DesktopOs.writeText(script, text)) { "could not write the update script" }
        check(DesktopOs.startDetached(listOf("/bin/bash", "-c", "exec /bin/bash ${q(script)} > ${q(log)} 2>&1"), folder)) {
            "could not start the update script"
        }
        val quit = HostHooks.requestExit
        if (quit != null) quit() else exitProcess(0)
    }
}

actual object PlatformUpdater {
    actual val canInstallInApp: Boolean get() = LinuxUpdater.canInstallInApp

    actual val platformKey: String = "linux"

    actual val updatesFromStore: Boolean = false

    actual fun latestStoreVersion(): String? = null

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? = LinuxUpdater.pickAsset(assets)

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> = runCatching {
        check(DesktopOs.exists(installerPath)) { "installer not found" }
        LinuxUpdater.installAndExit(installerPath)
    }
}
