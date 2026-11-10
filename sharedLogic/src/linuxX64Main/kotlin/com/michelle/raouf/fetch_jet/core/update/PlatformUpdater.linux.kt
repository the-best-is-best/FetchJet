package com.michelle.raouf.fetch_jet.core.update

actual object PlatformUpdater {
    actual val canInstallInApp: Boolean = false

    actual val platformKey: String = "linux"

    actual val updatesFromStore: Boolean = false

    actual fun latestStoreVersion(): String? = null

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".tar.gz", ignoreCase = true) && it.name.contains("linux", ignoreCase = true) }

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("in-app update is not supported on Linux yet"))
}
