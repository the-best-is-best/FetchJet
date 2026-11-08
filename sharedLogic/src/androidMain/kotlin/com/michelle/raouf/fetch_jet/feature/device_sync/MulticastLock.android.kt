package com.michelle.raouf.fetch_jet.feature.device_sync

import android.content.Context
import android.net.wifi.WifiManager
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

internal actual object MulticastLock {
    actual fun acquire(): Any? = try {
        val ctx = PathResolver.appContext ?: return null
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.createMulticastLock("fetchjet-device-sync")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (_: Throwable) {
        null
    }

    actual fun release(token: Any?) {
        try {
            (token as? WifiManager.MulticastLock)?.takeIf { it.isHeld }?.release()
        } catch (_: Throwable) {
        }
    }
}
