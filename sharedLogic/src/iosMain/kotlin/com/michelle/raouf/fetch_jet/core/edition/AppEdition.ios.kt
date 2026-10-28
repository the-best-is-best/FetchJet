package com.michelle.raouf.fetch_jet.core.edition

import platform.Foundation.NSBundle

private val cached: AppEdition by lazy {
    val value = NSBundle.mainBundle.objectForInfoDictionaryKey("FetchJetEdition") as? String
    if (value?.trim()?.lowercase() == "store") AppEdition.STORE else AppEdition.FULL
}

actual fun currentAppEdition(): AppEdition = cached

// libtorrent via the Swift NativeTorrentBridge (LibTorrent-Swift), registered at startup in
// the FULL edition.
actual fun platformSupportsTorrent(): Boolean =
    com.michelle.raouf.fetch_jet.feature.torrent_engine.IosTorrentBridgeRegistry.bridge != null

// The Swift side registers the in-app engine (yt-dlp via PythonKit + ffmpeg) at startup in
// the FULL edition. Whether that engine is *ready* (Python booted, yt_dlp module present) is a
// separate question answered by isMediaExtractorToolInstalled()/getMediaExtractorToolStatus(),
// exactly like desktop before yt-dlp is downloaded - so the UI shows the engine with its status
// and the "Download Tools" action instead of hiding it.
actual fun platformSupportsMediaExtractor(): Boolean =
    com.michelle.raouf.fetch_jet.feature.media_extractor.IosMediaBridgeRegistry.bridge != null
