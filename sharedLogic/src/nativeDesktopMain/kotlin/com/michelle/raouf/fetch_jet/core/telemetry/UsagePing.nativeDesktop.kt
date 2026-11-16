package com.michelle.raouf.fetch_jet.core.telemetry

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs

internal actual fun usagePlatform(): String? {
    if (DesktopOs.getEnv(USAGE_PING_OFF_ENV) == "1") return null
    return when (DesktopOs.name.lowercase()) {
        "windows" -> "windows"
        "macos" -> "macos"
        "linux" -> "linux"
        else -> null
    }
}
