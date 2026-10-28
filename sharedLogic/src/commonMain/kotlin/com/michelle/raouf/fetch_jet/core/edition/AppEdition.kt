package com.michelle.raouf.fetch_jet.core.edition

/**
 * FetchJet ships in two editions (like Palladium does on iOS):
 *  - FULL: everything (plain HTTP, media extractor via yt-dlp, BitTorrent). Desktop, Android,
 *    and the iOS build distributed as an .ipa on GitHub / AltStore.
 *  - STORE: App Store compliant. Plain HTTP downloads only: Apple rejects apps that download
 *    from YouTube-style sites (guideline 5.2.3) and BitTorrent clients.
 *
 * On iOS the edition is read from Info.plist (`FetchJetEdition`, set by the xcconfig
 * variable FETCHJET_EDITION). Everywhere else it is FULL.
 */
enum class AppEdition(val label: String) {
    FULL("Full"),
    STORE("App Store")
}

expect fun currentAppEdition(): AppEdition

/** Whether this platform ships a BitTorrent engine at all (iOS does not, yet). */
expect fun platformSupportsTorrent(): Boolean

/** Whether this platform can run the media extractor (yt-dlp) in-process or as a tool. */
expect fun platformSupportsMediaExtractor(): Boolean

object AppFeatures {
    val edition: AppEdition get() = currentAppEdition()
    val mediaExtractor: Boolean get() = edition == AppEdition.FULL && platformSupportsMediaExtractor()
    val torrent: Boolean get() = edition == AppEdition.FULL && platformSupportsTorrent()
}
