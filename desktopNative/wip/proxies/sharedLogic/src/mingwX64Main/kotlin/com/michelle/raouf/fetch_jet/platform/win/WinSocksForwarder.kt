package com.michelle.raouf.fetch_jet.platform.win

object WinSocksForwarder {
    const val LOGIN_USER = "fetchjet"

    val loginPassword: String = winRandomBytes(24).toHexString()

    private val lock = WinLock()
    private val forwarders = HashMap<String, WinLoopbackProxy>()

    fun portFor(host: String, port: Int, user: String, password: String): Int = lock.withLock {
        val key = "$host\u0000$port\u0000$user\u0000$password"
        val known = forwarders[key]
        if (known != null && known.isOpen) {
            known.port
        } else {
            val started = WinLoopbackProxy.start(WinSocksUpstream(host, port, user, password), "$LOGIN_USER:$loginPassword")
            forwarders[key] = started
            started.port
        }
    }

    fun closeAll() {
        val open = lock.withLock {
            val all = forwarders.values.toList()
            forwarders.clear()
            all
        }
        for (forwarder in open) forwarder.close()
    }
}
