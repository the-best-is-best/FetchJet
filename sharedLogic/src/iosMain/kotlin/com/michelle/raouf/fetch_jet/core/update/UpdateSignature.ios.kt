package com.michelle.raouf.fetch_jet.core.update

/** iOS never installs an update itself (sideloaded app, see PlatformUpdater.ios.kt). */
internal actual fun verifyUpdateSignature(path: String, signatureBase64: String): Boolean = false
