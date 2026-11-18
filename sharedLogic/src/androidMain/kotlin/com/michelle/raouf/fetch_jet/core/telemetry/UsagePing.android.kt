package com.michelle.raouf.fetch_jet.core.telemetry

import android.content.pm.ApplicationInfo
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

internal actual fun isDebuggableAndroidApp(): Boolean {
    val info = PathResolver.appContext?.applicationInfo ?: return true
    return info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
