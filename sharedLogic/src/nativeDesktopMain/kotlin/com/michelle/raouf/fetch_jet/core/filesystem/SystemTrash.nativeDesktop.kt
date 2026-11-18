package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.nativePath

internal expect fun osMoveToTrash(path: String): Boolean

actual object SystemTrash {
    actual val isSupported: Boolean = true

    actual fun moveToTrash(path: String): Boolean {
        val native = DesktopOs.nativePath(path)
        if (!DesktopOs.exists(native)) return false
        val moved = runCatching { osMoveToTrash(native) }.getOrDefault(false)
        if (!moved) println("FetchJet: could not move to the trash: $native")
        return moved && !DesktopOs.exists(native)
    }
}
