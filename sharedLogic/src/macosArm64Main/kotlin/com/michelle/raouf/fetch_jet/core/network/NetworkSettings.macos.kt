package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import platform.Foundation.NSURLAuthenticationMethodHTTPBasic
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialWithUser

internal actual fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings) {
    if (config !is DarwinClientEngineConfig) return
    config.proxy = null
    config.configureSession {
        setHTTPMaximumConnectionsPerHost(32)
        if (proxy.isOn) connectionProxyDictionary = if (proxy.kind == ProxyKind.SOCKS5) buildMap<Any?, Any?> {
            put("SOCKSEnable", 1)
            put("SOCKSProxy", proxy.host)
            put("SOCKSPort", proxy.port)
            if (proxy.hasLogin) {
                put("SOCKSUser", proxy.user)
                put("SOCKSPassword", proxy.password)
            }
        } else mapOf<Any?, Any?>(
            "HTTPEnable" to 1,
            "HTTPProxy" to proxy.host,
            "HTTPPort" to proxy.port,
            "HTTPSEnable" to 1,
            "HTTPSProxy" to proxy.host,
            "HTTPSPort" to proxy.port
        )
    }
    if (!proxy.isOn) return
    if (proxy.hasLogin && proxy.kind == ProxyKind.HTTP) config.handleChallenge { _, _, challenge, completionHandler ->
        val space = challenge.protectionSpace
        if (space.isProxy() && space.authenticationMethod == NSURLAuthenticationMethodHTTPBasic &&
            challenge.previousFailureCount == 0L
        ) {
            val credential = NSURLCredential.credentialWithUser(
                proxy.user,
                proxy.password,
                NSURLCredentialPersistence.NSURLCredentialPersistenceForSession
            )
            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
        } else {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
        }
    }
}
