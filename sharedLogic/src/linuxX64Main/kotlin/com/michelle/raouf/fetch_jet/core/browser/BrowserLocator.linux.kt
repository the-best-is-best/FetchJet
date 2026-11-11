package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.findOnPath
import com.michelle.raouf.fetch_jet.platform.desktop.startDetached

internal actual object BrowserLocator {
    actual fun hasCandidates(paths: BrowserPaths): Boolean = paths.linux.isNotEmpty()

    actual fun find(paths: BrowserPaths): String? = paths.linux.firstNotNullOfOrNull { DesktopOs.findOnPath(it) }

    actual fun start(executable: String, args: List<String>): Boolean =
        DesktopOs.startDetached(listOf(executable) + args)
}
