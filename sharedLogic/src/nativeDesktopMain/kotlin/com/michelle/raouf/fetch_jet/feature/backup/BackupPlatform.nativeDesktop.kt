package com.michelle.raouf.fetch_jet.feature.backup

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs

internal actual fun devicePlatformId(): String = when (DesktopOs.name.lowercase()) {
    "windows" -> "windows"
    "macos" -> "macos"
    "linux" -> "linux"
    else -> "unknown"
}
