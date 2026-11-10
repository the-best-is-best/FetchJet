package com.michelle.raouf.fetch_jet

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private class DesktopPlatform : Platform {
    override val name: String = DesktopOs.name
}

private val desktopPlatform: Platform = DesktopPlatform()

actual fun getPlatform(): Platform = desktopPlatform

actual suspend fun getClipboardText(): String? = withContext(Dispatchers.IO) {
    try {
        DesktopOs.clipboardText()
    } catch (_: Throwable) {
        null
    }
}
