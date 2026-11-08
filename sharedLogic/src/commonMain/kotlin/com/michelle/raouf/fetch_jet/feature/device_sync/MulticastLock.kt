package com.michelle.raouf.fetch_jet.feature.device_sync

/**
 * Android needs a Wi-Fi multicast lock held to receive UDP broadcast/multicast; on the desktop
 * this is a no-op. Held while the device-sync discovery is listening.
 */
internal expect object MulticastLock {
    fun acquire(): Any?
    fun release(token: Any?)
}
