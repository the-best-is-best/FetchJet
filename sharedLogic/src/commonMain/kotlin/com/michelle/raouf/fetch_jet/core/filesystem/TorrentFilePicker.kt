package com.michelle.raouf.fetch_jet.core.filesystem

/**
 * "Open .torrent file": the system file chooser, answering with a local path the torrent
 * engine can read (on Android and iOS the picked document is copied into the app's storage
 * first, since the original lives behind a content / security-scoped URL).
 */
expect object TorrentFilePicker {
    /** False where no chooser can be shown (e.g. Android before the Activity registered one). */
    val isSupported: Boolean

    /** Shows the chooser; [onPicked] gets the local path, or null when the user cancelled. */
    fun pick(onPicked: (String?) -> Unit)
}
