package com.michelle.raouf.fetch_jet.feature.device_sync

internal actual object MulticastLock {
    actual fun acquire(): Any? = null
    actual fun release(token: Any?) {}
}
