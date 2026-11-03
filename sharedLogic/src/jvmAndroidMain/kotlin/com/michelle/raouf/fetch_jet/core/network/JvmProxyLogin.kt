package com.michelle.raouf.fetch_jet.core.network

import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * The proxy login for the JDK HTTP client (HTTPS tunnels) and for SOCKS5 sockets, which ask
 * the default [Authenticator]. Answers only proxy requests for the configured proxy.
 */
internal object JvmProxyLogin {
    val authenticator = object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val p = NetworkSettings.proxy.value
            if (!p.isOn || !p.hasLogin) return null
            val forProxy = requestorType == RequestorType.PROXY ||
                    requestingProtocol?.startsWith("SOCKS", ignoreCase = true) == true
            if (!forProxy || !requestingHost.equals(p.host, ignoreCase = true)) return null
            return PasswordAuthentication(p.user, p.password.toCharArray())
        }
    }

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
        System.setProperty("jdk.http.auth.proxying.disabledSchemes", "")
        Authenticator.setDefault(authenticator)
    }
}
