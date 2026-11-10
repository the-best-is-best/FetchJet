package com.michelle.raouf.fetch_jet

import com.michelle.raouf.fetch_jet.platform.win.winClipboardText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private class WindowsPlatform : Platform {
    override val name: String = "Windows"
}

private val windowsPlatform: Platform = WindowsPlatform()

actual fun getPlatform(): Platform = windowsPlatform

actual suspend fun getClipboardText(): String? = withContext(Dispatchers.IO) {
    try {
        winClipboardText()
    } catch (_: Throwable) {
        null
    }
}
