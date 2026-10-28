package com.michelle.raouf.fetch_jet

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual suspend fun getClipboardText(): String? {
    return platform.UIKit.UIPasteboard.generalPasteboard.string
}