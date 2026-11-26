package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.isDirectory

internal actual object PrivateFolder {
    actual fun ensure(path: String): Boolean = DesktopOs.isDirectory(path) || DesktopOs.createDirectories(path)

    actual fun isOwnFile(path: String): Boolean = DesktopOs.fileInfo(path)?.isDirectory == false
}
