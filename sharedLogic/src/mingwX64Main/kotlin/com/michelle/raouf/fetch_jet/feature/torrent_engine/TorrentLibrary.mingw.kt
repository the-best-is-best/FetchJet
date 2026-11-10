package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.platform.torrent.FjtLibrary
import com.michelle.raouf.fetch_jet.platform.win.winTorrentLibrary

internal actual fun nativeTorrentLibrary(): FjtLibrary = winTorrentLibrary
