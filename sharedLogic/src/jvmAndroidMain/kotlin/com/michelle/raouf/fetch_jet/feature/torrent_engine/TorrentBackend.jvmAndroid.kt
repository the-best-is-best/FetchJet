package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import java.io.File

private val backendInstance: TorrentBackend by lazy {
    val resolver = PathResolver()
    Libtorrent4jBackend(
        tempDir = File(resolver.getTempDirectory(), "torrent-metadata"),
        toolsDir = File(resolver.getSettingsDirectory(), "tools")
    )
}

actual fun createTorrentBackend(): TorrentBackend = backendInstance
