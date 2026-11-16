package com.michelle.raouf.fetch_jet.core.telemetry

import platform.Foundation.NSProcessInfo

internal actual fun usagePlatform(): String? {
    if (NSProcessInfo.processInfo.environment[USAGE_PING_OFF_ENV] == "1") return null
    return "ios"
}
