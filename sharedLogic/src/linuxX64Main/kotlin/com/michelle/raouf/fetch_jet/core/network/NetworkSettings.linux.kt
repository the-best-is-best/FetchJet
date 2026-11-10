package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.platform.linux.LinuxCurlConfig
import io.ktor.client.engine.HttpClientEngineConfig

internal actual fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings) {
    val engine = config as? LinuxCurlConfig ?: return
    if (proxy.isOn && proxy.hasLogin) {
        engine.proxyUser = proxy.user
        engine.proxyPassword = proxy.password
    }
}
