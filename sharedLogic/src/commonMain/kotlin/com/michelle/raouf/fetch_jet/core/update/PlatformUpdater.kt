package com.michelle.raouf.fetch_jet.core.update

/** What this platform can do with a new release. */
expect object PlatformUpdater {
    /**
     * true = FetchJet can download the new release and install it itself. Desktop: one file per
     * OS in jvmMain (`WindowsUpdater`, `MacUpdater`, `LinuxUpdater`). Android opens the system
     * installer. Elsewhere the user is only told about the update.
     */
    val canInstallInApp: Boolean

    /**
     * Name of this platform in release notes: windows / macos / linux / android / ios.
     * A release built for some platforms only lists the others in a
     * `<!-- fetchjet-unchanged: windows,linux -->` line, and the app then knows the new
     * version has nothing new for it.
     */
    val platformKey: String

    /** true = a store (Microsoft Store) installs the updates: FetchJet does not check GitHub. */
    val updatesFromStore: Boolean

    fun latestStoreVersion(): String?

    /** The release file for this platform, or null to send the user to the release page. */
    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset?

    /** Starts the downloaded installer, quits FetchJet; the installer starts it again. */
    fun launchInstallerAndExit(installerPath: String): Result<Unit>
}
