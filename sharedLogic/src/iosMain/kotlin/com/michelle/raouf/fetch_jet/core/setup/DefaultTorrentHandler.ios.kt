package com.michelle.raouf.fetch_jet.core.setup

actual object DefaultTorrentHandler {
    actual val supported: Boolean = false
    actual val opensSystemPage: Boolean = false
    actual suspend fun isDefault(): Boolean? = null
    actual suspend fun makeDefault(): Boolean = false
}
