package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.startDetached
import kotlin.system.exitProcess
import platform.posix.W_OK
import platform.posix.access
import platform.posix.getpid

internal object MacUpdater {
    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"

    val appBundle: String?
        get() {
            var path = DesktopApp.launcherPath ?: return null
            while (path.length > 1) {
                if (path.endsWith(".app")) return path
                path = path.substringBeforeLast('/', "")
            }
            return null
        }

    val canInstallInApp: Boolean
        get() {
            val app = appBundle ?: return false
            val parent = app.substringBeforeLast('/', "").ifEmpty { "/" }
            return access(app, W_OK) == 0 && access(parent, W_OK) == 0
        }

    private val nativeDmg = Regex("""^FetchJet-.+-arm64\.dmg$""", RegexOption.IGNORE_CASE)

    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { nativeDmg.matches(it.name) }

    fun installAndExit(installerPath: String) {
        val app = checkNotNull(appBundle) { "FetchJet.app not found" }
        val folder = installerPath.substringBeforeLast('/', "").ifEmpty { DesktopOs.tempDir() }
        val script = "$folder/fetchjet-update.sh"
        val log = "$folder/fetchjet-update.log"
        val pid = getpid()
        val dollar = '$'
        val text = """
            #!/bin/bash
            while kill -0 $pid 2>/dev/null; do sleep 1; done
            MNT=$dollar(mktemp -d "$dollar{TMPDIR:-/tmp}/fetchjet-update.XXXXXX")
            if hdiutil attach -nobrowse -noautoopen -quiet -mountpoint "${dollar}MNT" ${q(installerPath)}; then
              SRC=$dollar(find "${dollar}MNT" -maxdepth 1 -name '*.app' -print -quit)
              if [ -n "${dollar}SRC" ] && ditto "${dollar}SRC" ${q("$app.new")}; then
                rm -rf ${q(app)} && mv ${q("$app.new")} ${q(app)}
              fi
              hdiutil detach -quiet "${dollar}MNT"
            fi
            rmdir "${dollar}MNT" 2>/dev/null
            rm -f ${q(installerPath)}
            rm -rf ${q("$app.new")}
            xattr -dr com.apple.quarantine ${q(app)} 2>/dev/null
            open ${q(app)}
            rm -f "${dollar}0"
        """.trimIndent() + "\n"
        check(DesktopOs.writeText(script, text)) { "could not write the update script" }
        check(DesktopOs.startDetached(listOf("/bin/bash", "-c", "exec /bin/bash ${q(script)} > ${q(log)} 2>&1"), folder)) {
            "could not start the update script"
        }
        val quit = HostHooks.requestExit
        if (quit != null) quit() else exitProcess(0)
    }
}

actual object PlatformUpdater {
    actual val canInstallInApp: Boolean get() = MacUpdater.canInstallInApp

    actual val platformKey: String = "macos"

    actual val updatesFromStore: Boolean = false

    actual fun latestStoreVersion(): String? = null

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? = MacUpdater.pickAsset(assets)

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> = runCatching {
        check(DesktopOs.exists(installerPath)) { "installer not found" }
        MacUpdater.installAndExit(installerPath)
    }
}
