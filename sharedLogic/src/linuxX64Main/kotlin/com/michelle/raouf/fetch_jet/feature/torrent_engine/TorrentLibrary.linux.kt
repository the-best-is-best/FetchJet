package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.platform.linux.linuxTorrentLibrary
import com.michelle.raouf.fetch_jet.platform.torrent.FjtLibrary

internal actual fun nativeTorrentLibrary(): FjtLibrary = linuxTorrentLibrary
