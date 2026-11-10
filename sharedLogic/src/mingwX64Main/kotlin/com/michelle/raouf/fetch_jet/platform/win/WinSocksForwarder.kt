package com.michelle.raouf.fetch_jet.platform.win

object WinSocksForwarder {
    fun portFor(host: String, port: Int, user: String, password: String): Int =
        throw UnsupportedOperationException("SOCKS5 proxies are not available in this build yet")
}
