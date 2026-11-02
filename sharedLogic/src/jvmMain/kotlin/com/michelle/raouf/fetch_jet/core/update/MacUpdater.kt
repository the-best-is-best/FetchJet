package com.michelle.raouf.fetch_jet.core.update

import java.io.File
import com.michelle.raouf.fetch_jet.core.update.DesktopInstall.shellQuote as q

/**
 * Mounts the downloaded .dmg, replaces the running FetchJet.app with the one inside and opens
 * it. Only when FetchJet.app and its folder are writable (normally `/Applications`).
 */
internal object MacUpdater : DesktopUpdater {
    override val platformKey = "macos"

    private val appBundle: File? = DesktopInstall.launcherPath?.let { path ->
        generateSequence(File(path)) { it.parentFile }.firstOrNull { it.name.endsWith(".app") }
    }

    override val canInstallInApp: Boolean =
        appBundle?.let { it.canWrite() && it.parentFile?.canWrite() == true } == true

    override fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        DesktopInstall.firstAsset(assets, ".dmg")

    override fun installAndExit(installer: File) {
        val app = checkNotNull(appBundle) { "FetchJet.app not found" }.absolutePath
        DesktopInstall.runShellAfterExit(
            installer,
            """
            MNT=${'$'}(mktemp -d "${'$'}{TMPDIR:-/tmp}/fetchjet-update.XXXXXX")
            if hdiutil attach -nobrowse -noautoopen -quiet -mountpoint "${'$'}MNT" ${q(installer.absolutePath)}; then
              SRC=${'$'}(find "${'$'}MNT" -maxdepth 1 -name '*.app' -print -quit)
              if [ -n "${'$'}SRC" ] && ditto "${'$'}SRC" ${q("$app.new")}; then
                rm -rf ${q(app)} && mv ${q("$app.new")} ${q(app)}
              fi
              hdiutil detach -quiet "${'$'}MNT"
            fi
            rmdir "${'$'}MNT" 2>/dev/null
            rm -f ${q(installer.absolutePath)}
            rm -rf ${q("$app.new")}
            xattr -dr com.apple.quarantine ${q(app)} 2>/dev/null
            open ${q(app)}
            """
        )
    }
}
