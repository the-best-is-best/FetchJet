package com.michelle.raouf.fetch_jet.core.security

object DeviceAuth {
    @kotlin.concurrent.Volatile
    var handler: (suspend (reason: String) -> Boolean)? = null

    fun available(): Boolean = handler != null
}
