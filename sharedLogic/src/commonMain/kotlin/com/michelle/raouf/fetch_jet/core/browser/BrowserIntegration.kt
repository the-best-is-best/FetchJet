package com.michelle.raouf.fetch_jet.core.browser

/** Store IDs of the FetchJet extension. */
const val EDGE_EXTENSION_ID = "phngekpnkcnpkjkcoggcfmlmebmociaj"

const val FIREFOX_ADDON_URL = "https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/"

/** A browser the extension can be added to, and whether it is on this device. */
data class BrowserInfo(
    val id: String,
    val name: String,
    val installed: Boolean,
    val downloadUrl: String,
    /** The app carries its own copy of the extension for this browser (Chromium on desktop). */
    val hasLocalCopy: Boolean = false
)

sealed interface AddExtensionResult {
    /** The browser opened the page where the user adds the extension (Firefox Add-ons). */
    data object Opened : AddExtensionResult

    /**
     * Chromium browsers: the extension was unpacked to [folder] and the browser's extensions
     * page ([page]) was opened; the user turns on Developer mode and loads the folder.
     */
    data class LoadUnpacked(val folder: String, val page: String) : AddExtensionResult

    /**
     * Firefox for Android: the signed .xpi was saved as Downloads/[fileName]; the user installs
     * it from Firefox's "Install extension from file" (unlocked once in About Firefox).
     */
    data class InstallFromFile(val fileName: String) : AddExtensionResult

    data object NotInstalled : AddExtensionResult
    data class Failed(val reason: String) : AddExtensionResult
}

/**
 * Adds the FetchJet extension to the user's browsers the way IDM / FDM do: on Windows a
 * per-user registry entry tells Microsoft Edge to fetch the extension from Edge Add-ons, and
 * Edge then asks the user to turn it on ("New extension added"). Nothing is installed
 * silently; the browser always asks.
 *
 * Chrome only accepts this for extensions in the Chrome Web Store, and Firefox has no
 * equivalent, so for them the app opens the store / install page instead.
 */
expect object BrowserIntegration {
    /** True where the Edge registry hand-off exists (Windows desktop). */
    val canAddToEdgeAutomatically: Boolean

    /** Whether the Edge entry is already written. */
    fun isEdgeRegistered(): Boolean

    /** Writes the Edge entry; Edge asks the user to enable the extension on its next start. */
    fun addToEdge(): Result<Unit>

    /** Removes the entry (the extension itself is removed from Edge's own settings). */
    fun removeFromEdge(): Result<Unit>

    /** Called once at app start: writes the Edge entry the first time FetchJet runs. */
    fun setUpOnFirstRun()

    /** The browsers FetchJet knows on this platform, installed ones first. */
    fun browsers(): List<BrowserInfo>

    /** Adds the extension to browser [id] (see [AddExtensionResult]). */
    fun addExtension(id: String): AddExtensionResult

    /** Shows [folder] in the system file manager. */
    fun openFolder(folder: String)

    /**
     * Installs a signed Firefox build ([bytes] of [fileName], from a FetchJet release) into
     * browser [id]: desktop opens it in Firefox (Firefox asks "Add?"), Android saves it to
     * Downloads for "Install extension from file".
     */
    fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult

    /** Starts browser [id]. */
    fun openBrowser(id: String)

    /**
     * Android: "Keep ready for the browser". Android freezes apps in the background, so the
     * Firefox extension could not ask FetchJet for video qualities until the app was opened.
     * A quiet foreground service (with a notification) keeps it answering. Off by default.
     */
    val canKeepReadyForBrowser: Boolean
    fun isKeptReadyForBrowser(): Boolean
    fun setKeepReadyForBrowser(enabled: Boolean)

    /** Called at app start: starts the service again if the user turned the option on. */
    fun restoreKeepReady()

    /**
     * Android 10+: an app in the background may not open its own screen unless it may
     * "display over other apps". Without it a link from Firefox only shows a notification.
     */
    val canPopUpOverBrowser: Boolean
    fun isPopUpAllowed(): Boolean
    fun requestPopUpPermission()
}
