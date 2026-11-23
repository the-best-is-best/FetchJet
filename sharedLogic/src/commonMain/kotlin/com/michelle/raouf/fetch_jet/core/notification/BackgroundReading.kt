package com.michelle.raouf.fetch_jet.core.notification

/**
 * Work the user waits for while the app may go to the background (reading a playlist's or a
 * link list's videos). On Android it keeps a small foreground notification up so the system
 * does not freeze the app or kill yt-dlp meanwhile; elsewhere it does nothing.
 */
expect object BackgroundReading {
    fun begin(key: String, title: String, text: String)
    fun progress(key: String, text: String)
    fun end(key: String)
}
