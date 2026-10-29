package com.michelle.raouf.fetch_jet.core.update

import android.os.Build

actual object PlatformUpdater {
    /** An Android app cannot replace itself: the user opens the APK (or the release page). */
    actual val canInstallInApp: Boolean = false
    actual val platformKey: String = "android"

    /** The APK built for this device's CPU (the release has one per ABI), else any APK. */
    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val abis = Build.SUPPORTED_ABIS.orEmpty()
        return abis.firstNotNullOfOrNull { abi ->
            apks.firstOrNull {
                it.name.contains(
                    abi,
                    ignoreCase = true
                )
            }
        }
            ?: apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not available on Android"))
}
