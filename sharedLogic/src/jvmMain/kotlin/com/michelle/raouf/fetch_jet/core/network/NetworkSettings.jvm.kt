package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.java.JavaHttpConfig
import io.ktor.client.engine.okhttp.OkHttpConfig

internal actual fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings) {
    JvmProxyLogin.install()
    if (!proxy.isOn || !proxy.hasLogin) return
    when (config) {
        is OkHttpConfig -> if (proxy.kind == ProxyKind.HTTP) config.config {
            proxyAuthenticator(okHttpProxyAuthenticator(proxy))
        }

        is JavaHttpConfig -> config.config { authenticator(JvmProxyLogin.authenticator) }
    }
}
