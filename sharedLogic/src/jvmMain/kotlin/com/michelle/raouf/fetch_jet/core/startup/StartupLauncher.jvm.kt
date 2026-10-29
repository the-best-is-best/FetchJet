package com.michelle.raouf.fetch_jet.core.startup

import io.github.vinceglb.autolaunch.AutoLaunch

actual object StartupLauncher {
    // Registry value / LaunchAgent label. Same id as the macOS bundle; never change it, or an
    // old entry would be left behind.
    private val autoLaunch by lazy { AutoLaunch(appPackageName = "com.michelle.raouf.fetch_jet") }

    /** Only the installed app has a stable path the OS can start. */
    actual val isSupported: Boolean
        get() = try {
            AutoLaunch.isRunningFromDistributable
        } catch (_: Throwable) {
            false
        }

    actual suspend fun isEnabled(): Boolean =
        isSupported && runCatching { autoLaunch.isEnabled() }.getOrDefault(false)

    actual suspend fun setEnabled(enabled: Boolean): Result<Unit> = runCatching {
        if (enabled) autoLaunch.enable() else autoLaunch.disable()
    }
}
