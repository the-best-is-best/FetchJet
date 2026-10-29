package com.michelle.raouf.fetch_jet.core.update

actual object PlatformUpdater {
    /** iOS apps cannot replace themselves: the user installs the new .ipa (AltStore, Sideloadly...). */
    actual val canInstallInApp: Boolean = false

    actual fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.name.endsWith(".ipa", ignoreCase = true) }

    actual fun launchInstallerAndExit(installerPath: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not available on iOS"))
}
