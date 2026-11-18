package com.michelle.raouf.fetch_jet.feature.device_sync

import android.os.Build
import android.provider.Settings
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

internal actual fun deviceSyncHostName(): String? {
    val chosen = runCatching {
        PathResolver.appContext?.contentResolver?.let { Settings.Global.getString(it, Settings.Global.DEVICE_NAME) }
    }.getOrNull()?.trim()
    if (!chosen.isNullOrBlank()) return chosen
    val maker = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    if (model.isBlank()) return null
    return if (maker.isBlank() || model.startsWith(maker, ignoreCase = true)) model
    else maker.replaceFirstChar { it.uppercase() } + " " + model
}
