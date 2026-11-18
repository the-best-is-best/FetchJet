package com.michelle.raouf.fetch_jet.core.telemetry

import platform.Foundation.NSProcessInfo

internal actual fun usagePlatform(): String? {
    val environment = NSProcessInfo.processInfo.environment
    if (environment[USAGE_PING_OFF_ENV] == "1") return null
    if (environment["SIMULATOR_DEVICE_NAME"] != null || environment["SIMULATOR_UDID"] != null) return null
    return "ios"
}
