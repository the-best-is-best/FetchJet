package com.michelle.raouf.fetch_jet.feature.device_sync

/** iOS has no Wi-Fi multicast lock; the real LAN discovery arrives with the iOS phase of item 4. */
internal actual object MulticastLock {
    actual fun acquire(): Any? = null
    actual fun release(token: Any?) {}
}
