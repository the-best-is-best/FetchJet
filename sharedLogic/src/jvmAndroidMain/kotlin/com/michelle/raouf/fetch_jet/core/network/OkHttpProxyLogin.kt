package com.michelle.raouf.fetch_jet.core.network

import okhttp3.Authenticator
import okhttp3.Credentials

internal fun okHttpProxyAuthenticator(proxy: ProxySettings) = Authenticator { _, response ->
    if (response.request.header("Proxy-Authorization") != null) null
    else response.request.newBuilder()
        .header("Proxy-Authorization", Credentials.basic(proxy.user, proxy.password))
        .build()
}
