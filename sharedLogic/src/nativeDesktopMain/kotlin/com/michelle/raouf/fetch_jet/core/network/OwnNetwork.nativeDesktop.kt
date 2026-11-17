package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.proxy.LoopbackProxy
import com.michelle.raouf.fetch_jet.platform.proxy.loopSockets
import com.michelle.raouf.fetch_jet.platform.proxy.withLock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray

internal actual val ownDnsSupported: Boolean = true

internal actual fun localNetworkProxyPort(dns: DnsSettings, adapter: String): Int? = OwnNetwork.portFor(dns, adapter)

internal object OwnNetwork {
    private val lock by lazy { loopSockets().newLock() }
    private var proxy: LoopbackProxy? = null
    private var key = ""

    fun portFor(dns: DnsSettings, adapter: String): Int? {
        if (!dns.isOn && adapter.isBlank()) {
            lock.withLock {
                proxy?.close()
                proxy = null
                key = ""
            }
            return null
        }
        return lock.withLock {
            val wanted = "${dns.mode}|${dns.custom}|$adapter"
            val open = proxy
            if (open != null && open.isOpen && wanted == key) return@withLock open.port
            open?.close()
            OwnDns.clear()
            val started = LoopbackProxy.start(
                resolver = if (dns.isOn) { host -> OwnDns.resolve(host, dns) } else null,
                source = adapter.ifBlank { null },
            )
            println("FetchJet network: own proxy on 127.0.0.1:${started.port} (dns ${dns.mode}${if (adapter.isNotBlank()) ", adapter $adapter" else ""})")
            proxy = started
            key = wanted
            started.port
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal object OwnDns {
    private class Cached(val addresses: List<String>, val until: TimeSource.Monotonic.ValueTimeMark)

    private val lock by lazy { loopSockets().newLock() }
    private val cache = HashMap<String, Cached>()
    private val doh by lazy {
        HttpClient(DesktopOs.httpEngine) {
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000L
                connectTimeoutMillis = 6_000L
                socketTimeoutMillis = 8_000L
            }
        }
    }

    fun clear() = lock.withLock { cache.clear() }

    fun resolve(host: String, dns: DnsSettings): List<String>? {
        val name = host.lowercase().trimEnd('.')
        lock.withLock { cache[name]?.takeIf { it.until.hasNotPassedNow() }?.let { return it.addresses } }
        val answer = try {
            runBlocking { lookUp(name, dns) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("FetchJet network: own DNS failed for $name: ${e.message}")
            return null
        }
        if (answer.addresses.isEmpty()) return null
        val ttl = answer.ttlSeconds.coerceIn(30L, 3600L)
        lock.withLock { cache[name] = Cached(answer.addresses, TimeSource.Monotonic.markNow() + kotlin.time.Duration.parse("${ttl}s")) }
        return answer.addresses
    }

    private suspend fun lookUp(name: String, dns: DnsSettings): DnsAnswer {
        val v4 = ask(name, DnsWire.TYPE_A, dns)
        if (v4.addresses.isNotEmpty()) return v4
        return ask(name, DnsWire.TYPE_AAAA, dns)
    }

    private suspend fun ask(name: String, type: Int, dns: DnsSettings): DnsAnswer {
        val id = Random.nextInt(0, 0x10000)
        val question = DnsWire.query(id, name, type)
        dns.dohUrl()?.let { url ->
            val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(question)
            val separator = if ('?' in url) '&' else '?'
            val response = doh.get("$url${separator}dns=$encoded") { header("Accept", "application/dns-message") }
            if (!response.status.isSuccess()) throw IllegalStateException("DNS-over-HTTPS answered ${response.status.value}")
            return DnsWire.parse(response.bodyAsBytes(), id)
        }
        var last: Throwable? = null
        for (server in dns.servers()) {
            try {
                return udp(server, question, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no DNS server")
    }

    private suspend fun udp(server: String, question: ByteArray, id: Int): DnsAnswer {
        val host = server.substringBeforeLast(':').takeIf { server.count { it == ':' } == 1 } ?: server
        val port = server.substringAfterLast(':', "").takeIf { server.count { it == ':' } == 1 }?.toIntOrNull() ?: 53
        val selector = SelectorManager(Dispatchers.IO)
        try {
            aSocket(selector).udp().connect(InetSocketAddress(host, port)).use { socket ->
                return withTimeout(4_000L) {
                    socket.send(Datagram(buildPacket { writeFully(question) }, socket.remoteAddress))
                    DnsWire.parse(socket.receive().packet.readByteArray(), id)
                }
            }
        } finally {
            selector.close()
        }
    }
}
