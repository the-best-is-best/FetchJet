package com.michelle.raouf.fetch_jet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isJvm: Boolean = true
}

actual fun getPlatform(): Platform = JVMPlatform()

actual suspend fun getClipboardText(): String? = withContext(Dispatchers.IO) {
    try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val transferable = clipboard.getContents(null)
        if (transferable != null && transferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
            transferable.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
        } else null
    } catch (_: Throwable) {
        null
    }
}