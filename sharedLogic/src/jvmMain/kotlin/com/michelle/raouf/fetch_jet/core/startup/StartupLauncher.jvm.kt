package com.michelle.raouf.fetch_jet.core.startup

import com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage
import io.github.vinceglb.autolaunch.AutoLaunch

actual object StartupLauncher {
    // Registry value / LaunchAgent label. Same id as the macOS bundle; never change it, or an
    // old entry would be left behind.
    private val autoLaunch by lazy { AutoLaunch(appPackageName = "com.michelle.raouf.fetch_jet") }

    /**
     * Only the installed app has a stable path the OS can start. The Store (MSIX) build is not
     * allowed to write the Run key; Windows lists it under Settings > Apps > Startup instead.
     */
    actual val isSupported: Boolean
        get() = try {
            !WindowsStorePackage.isInstalled && AutoLaunch.isRunningFromDistributable
        } catch (_: Throwable) {
            false
        }

    actual val managedBySystemUri: String?
        get() = if (WindowsStorePackage.isInstalled) "ms-settings:startupapps" else null

    actual suspend fun isEnabled(): Boolean =
        isSupported && runCatching { autoLaunch.isEnabled() }.getOrDefault(false)

    actual suspend fun setEnabled(enabled: Boolean): Result<Unit> = runCatching {
        if (enabled) autoLaunch.enable() else autoLaunch.disable()
    }
}
