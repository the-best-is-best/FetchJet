package com.michelle.raouf.fetch_jet.core.browser

/** Store IDs of the FetchJet extension. */
const val EDGE_EXTENSION_ID = "phngekpnkcnpkjkcoggcfmlmebmociaj"

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
}
