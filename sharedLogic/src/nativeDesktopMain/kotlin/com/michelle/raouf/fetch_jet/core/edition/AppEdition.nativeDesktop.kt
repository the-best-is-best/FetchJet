package com.michelle.raouf.fetch_jet.core.edition

import com.michelle.raouf.fetch_jet.feature.torrent_engine.createTorrentBackend

actual fun currentAppEdition(): AppEdition = AppEdition.FULL

actual fun platformSupportsTorrent(): Boolean = createTorrentBackend().isSupported

actual fun platformSupportsMediaExtractor(): Boolean = true
