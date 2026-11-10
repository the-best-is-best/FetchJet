package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.platform.win.WinHttpConfig
import com.michelle.raouf.fetch_jet.platform.win.WinSocksForwarder
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.http.Url

internal actual fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings) {
    val engine = config as? WinHttpConfig ?: return
    if (!proxy.isOn) return
    if (proxy.kind == ProxyKind.SOCKS5) {
        val port = WinSocksForwarder.portFor(proxy.host, proxy.port, proxy.user, proxy.password)
        engine.proxy = ProxyBuilder.http(Url("http://127.0.0.1:$port"))
        engine.proxyUser = WinSocksForwarder.LOGIN_USER
        engine.proxyPassword = WinSocksForwarder.loginPassword
        return
    }
    if (proxy.hasLogin) {
        engine.proxyUser = proxy.user
        engine.proxyPassword = proxy.password
    }
}
