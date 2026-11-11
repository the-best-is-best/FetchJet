package com.michelle.raouf.fetch_jet.core.setup

/**
 * FetchJet as the app that opens `.torrent` files and `magnet:` links. Only ever changed after
 * the user taps a button; where the OS forbids apps to change it (Windows, Android) the OS's own
 * page is opened instead.
 */
expect object DefaultTorrentHandler {
    val supported: Boolean

    /** true when [makeDefault] only opens the system's settings page (the user picks there). */
    val opensSystemPage: Boolean

    /** null = could not be checked. */
    suspend fun isDefault(): Boolean?

    /** true when it was done, or the system page was opened. */
    suspend fun makeDefault(): Boolean
}

const val FETCHJET_BUNDLE_ID = "com.michelle.raouf.fetch_jet"
const val TORRENT_MIME = "application/x-bittorrent"
const val MAGNET_SCHEME = "magnet"
