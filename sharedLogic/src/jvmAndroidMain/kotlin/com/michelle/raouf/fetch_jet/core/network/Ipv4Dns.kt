package com.michelle.raouf.fetch_jet.core.network

import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.ConnectionPool
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

internal fun ipv4Only(addresses: List<InetAddress>): List<InetAddress> {
    val v4 = addresses.filter { it is Inet4Address }
    return if (v4.isEmpty()) addresses else v4
}

internal class Ipv4PreferringDns(
    private val resolve: (String) -> List<InetAddress> = { Dns.SYSTEM.lookup(it) }
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = resolve(hostname)
        return if (Ipv4Preference.prefers(hostname)) ipv4Only(all) else all
    }
}

internal object Ipv4OkHttp {
    val dns = Ipv4PreferringDns()
    val pool = ConnectionPool()
}

internal fun OkHttpConfig.useIpv4Preference() {
    Ipv4Preference.supported = true
    Ipv4Preference.onAdded = { Ipv4OkHttp.pool.evictAll() }
    config {
        dns(Ipv4OkHttp.dns)
        connectionPool(Ipv4OkHttp.pool)
    }
}
