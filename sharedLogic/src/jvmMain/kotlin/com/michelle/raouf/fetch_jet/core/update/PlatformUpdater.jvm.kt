package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage
import java.io.File

actual object PlatformUpdater {
    private val updater: DesktopUpdater = when (DesktopOs.current) {
        DesktopOs.WINDOWS -> WindowsUpdater
        DesktopOs.MAC -> MacUpdater
        DesktopOs.LINUX -> LinuxUpdater
    }

    actual val canInstallInApp: Boolean get() = updater.canInstallInApp

    actual val platformKey: String get() = updater.platformKey

    /** The Microsoft Store build is updated by the Store. */
    actual val updatesFromStore: Boolean = WindowsStorePackage.isInstalled

    actual fun latestStoreVersion(): String? = WindowsUpdater.latestStoreVersion()

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? = updater.pickAsset(assets)

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> = runCatching {
        val installer = File(installerPath)
        check(installer.exists()) { "installer not found" }
        updater.installAndExit(installer)
    }
}
