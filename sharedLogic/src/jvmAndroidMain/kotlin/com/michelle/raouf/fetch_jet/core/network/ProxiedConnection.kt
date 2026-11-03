package com.michelle.raouf.fetch_jet.core.network

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** [HttpURLConnection] through the app's proxy, with the saved site login. */
internal fun openAppConnection(url: URL): HttpURLConnection {
    val p = NetworkSettings.proxy.value
    val connection = if (p.isOn) {
        JvmProxyLogin.install()
        val type = if (p.kind == ProxyKind.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
        url.openConnection(Proxy(type, InetSocketAddress.createUnresolved(p.host, p.port)))
    } else url.openConnection()
    connection as HttpURLConnection
    NetworkSettings.loginFor(url.host)?.let {
        connection.setRequestProperty("Authorization", basicAuth(it.user, it.password))
    }
    return connection
}
