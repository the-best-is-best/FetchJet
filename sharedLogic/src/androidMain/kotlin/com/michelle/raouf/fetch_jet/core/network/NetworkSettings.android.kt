package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.okhttp.OkHttpConfig

internal actual fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings) {
    JvmProxyLogin.install()
    if (config is OkHttpConfig) config.useIpv4Preference()
    if (!proxy.isOn || !proxy.hasLogin) return
    if (config is OkHttpConfig && proxy.kind == ProxyKind.HTTP) config.config {
        proxyAuthenticator(okHttpProxyAuthenticator(proxy))
    }
}
