@file:OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)

package com.michelle.raouf.fetch_jet.platform.proxy

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.TimeSource
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.plus
import kotlinx.cinterop.usePinned

private const val HEAD_LIMIT_BYTES = 64 * 1024
private const val HEAD_TIMEOUT_MS = 30_000
private const val UPSTREAM_TIMEOUT_MS = 60_000
private const val DOWN_BUFFER_BYTES = 64 * 1024
private const val UP_BUFFER_BYTES = 16 * 1024
private const val LIMIT_REFRESH_NANOS = 100_000_000L
private const val CONNECT_ATTEMPTS = 4
private const val CR: Byte = 13
private const val LF: Byte = 10

private val ESTABLISHED = latin1Bytes("HTTP/1.1 200 Connection established\r\n\r\n")
private val REQUEST_HOP_HEADERS = setOf("proxy-authorization", "proxy-connection", "connection", "keep-alive")
private val RESPONSE_HOP_HEADERS = setOf("proxy-connection", "connection", "keep-alive")
private val NO_BYTES = ByteArray(0)

private val sockets: LoopSockets by lazy { loopSockets() }

private fun sendAll(socket: Long, data: CPointer<ByteVar>, count: Int): Boolean {
    var done = 0
    while (done < count) {
        val sent = sockets.send(socket, (data + done)!!, count - done)
        if (sent <= 0) return false
        done += sent
    }
    return true
}

private fun sendAll(socket: Long, bytes: ByteArray, from: Int = 0, count: Int = bytes.size - from): Boolean {
    if (count <= 0) return true
    return bytes.usePinned { sendAll(socket, it.addressOf(from), count) }
}

private fun receive(socket: Long, bytes: ByteArray, from: Int, count: Int): Int =
    bytes.usePinned { sockets.receive(socket, it.addressOf(from), count) }

private fun receiveExact(socket: Long, bytes: ByteArray, count: Int): Boolean {
    var done = 0
    while (done < count) {
        val read = receive(socket, bytes, done, count - done)
        if (read <= 0) return false
        done += read
    }
    return true
}

private fun latin1(bytes: ByteArray, from: Int, until: Int): String =
    CharArray(until - from) { (bytes[from + it].toInt() and 0xFF).toChar() }.concatToString()

private fun latin1Bytes(text: String): ByteArray = ByteArray(text.length) { text[it].code.toByte() }

private fun headEnd(bytes: ByteArray, length: Int): Int {
    var at = 0
    while (at + 3 < length) {
        if (bytes[at] == CR && bytes[at + 1] == LF && bytes[at + 2] == CR && bytes[at + 3] == LF) return at
        at++
    }
    return -1
}

private fun headerName(line: String): String = line.substringBefore(':').trim().lowercase()

private fun sameText(left: String, right: String): Boolean {
    var difference = left.length xor right.length
    for (index in 0 until minOf(left.length, right.length)) {
        difference = difference or (left[index].code xor right[index].code)
    }
    return difference == 0
}

private class ProxyHead(
    val method: String,
    val target: String,
    val version: String,
    val lines: List<String>,
    val rest: ByteArray,
) {
    fun header(name: String): String? {
        for (line in lines) {
            val cut = line.indexOf(':')
            if (cut > 0 && line.substring(0, cut).trim().equals(name, ignoreCase = true)) return line.substring(cut + 1).trim()
        }
        return null
    }
}

private class ProxyAddress(val host: String, val port: Int, val path: String = "/")

private class ProxyFailure(val status: Int, val reason: String)

private val TUNNEL_STOPPED = ProxyFailure(0, "")

private fun readRequestHead(socket: Long): ProxyHead? {
    var buffer = ByteArray(2048)
    var length = 0
    var end = -1
    while (end < 0) {
        if (length == buffer.size) {
            if (length >= HEAD_LIMIT_BYTES) return ProxyHead("", "", "", emptyList(), NO_BYTES)
            buffer = buffer.copyOf(length * 2)
        }
        val read = receive(socket, buffer, length, buffer.size - length)
        if (read <= 0) return null
        length += read
        end = headEnd(buffer, length)
    }
    val lines = latin1(buffer, 0, end).split("\r\n")
    val first = lines[0].split(' ')
    if (first.size != 3 || first.any { it.isEmpty() }) return ProxyHead("", "", "", emptyList(), NO_BYTES)
    return ProxyHead(first[0], first[1], first[2], lines.subList(1, lines.size), buffer.copyOfRange(end + 4, length))
}

private fun parseAuthority(text: String, defaultPort: Int): ProxyAddress? {
    val authority = text.trim()
    val host: String
    val portText: String
    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close < 0) return null
        host = authority.substring(1, close)
        val after = authority.substring(close + 1)
        if (after.isNotEmpty() && !after.startsWith(":")) return null
        portText = after.removePrefix(":")
    } else {
        val cut = authority.lastIndexOf(':')
        if (cut >= 0 && authority.indexOf(':') == cut) {
            host = authority.substring(0, cut)
            portText = authority.substring(cut + 1)
        } else {
            host = authority
            portText = ""
        }
    }
    val port = if (portText.isEmpty()) defaultPort else portText.toIntOrNull() ?: return null
    if (host.isEmpty() || port !in 1..65535 || host.any { it <= ' ' || it == '/' || it.code == 92 }) return null
    return ProxyAddress(host, port)
}

private fun plainTarget(head: ProxyHead): ProxyAddress? {
    val target = head.target
    val scheme = target.indexOf("://")
    if (scheme > 0) {
        if (!target.substring(0, scheme).equals("http", ignoreCase = true)) return null
        val after = target.substring(scheme + 3)
        val cut = after.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = (if (cut < 0) after else after.substring(0, cut)).substringAfterLast('@')
        val tail = if (cut < 0) "" else after.substring(cut)
        val address = parseAuthority(authority, 80) ?: return null
        return ProxyAddress(address.host, address.port, if (tail.startsWith("/")) tail else "/$tail")
    }
    if (!target.startsWith("/")) return null
    val address = parseAuthority(head.header("Host") ?: return null, 80) ?: return null
    return ProxyAddress(address.host, address.port, target)
}

private fun originRequest(head: ProxyHead, address: ProxyAddress): ByteArray {
    val out = StringBuilder()
    out.append(head.method).append(' ').append(address.path).append(' ').append(head.version).append("\r\n")
    var hasHost = false
    for (line in head.lines) {
        val name = headerName(line)
        if (name in REQUEST_HOP_HEADERS) continue
        if (name == "host") hasHost = true
        out.append(line).append("\r\n")
    }
    if (!hasHost) {
        out.append("Host: ").append(if (':' in address.host) "[${address.host}]" else address.host)
        if (address.port != 80) out.append(':').append(address.port)
        out.append("\r\n")
    }
    out.append("Connection: close\r\n\r\n")
    return latin1Bytes(out.toString())
}

private fun closingResponseHead(head: String): ByteArray {
    val out = StringBuilder()
    for ((index, line) in head.split("\r\n").withIndex()) {
        if (index > 0 && headerName(line) in RESPONSE_HOP_HEADERS) continue
        out.append(line).append("\r\n")
    }
    out.append("Connection: close\r\n\r\n")
    return latin1Bytes(out.toString())
}

private fun answer(socket: Long, status: Int, reason: String) {
    val challenge = if (status == 407) "Proxy-Authenticate: Basic realm=\"FetchJet\"\r\n" else ""
    sendAll(socket, latin1Bytes("HTTP/1.1 $status $reason\r\n${challenge}Content-Length: 0\r\nConnection: close\r\n\r\n"))
}

private fun socksLost(): ProxyFailure =
    if (sockets.lastErrorTimedOut()) ProxyFailure(504, "The SOCKS5 proxy did not answer in time")
    else ProxyFailure(502, "The SOCKS5 proxy closed the connection")

private fun socksRefusal(code: Int): String = "The SOCKS5 proxy could not reach the server: " + when (code) {
    1 -> "general failure"
    2 -> "not allowed by the proxy"
    3 -> "network unreachable"
    4 -> "host unreachable"
    5 -> "connection refused"
    6 -> "timed out"
    7 -> "command not supported"
    8 -> "address type not supported"
    else -> "error $code"
}

private fun socksHandshake(socket: Long, proxy: SocksUpstream, address: ProxyAddress): ProxyFailure? {
    val user = proxy.user.encodeToByteArray()
    val password = proxy.password.encodeToByteArray()
    if (user.size > 255 || password.size > 255) return ProxyFailure(407, "The SOCKS5 user name or password is too long")
    val reply = ByteArray(4)
    if (!sendAll(socket, if (user.isEmpty()) byteArrayOf(5, 1, 0) else byteArrayOf(5, 2, 0, 2))) return socksLost()
    if (!receiveExact(socket, reply, 2)) return socksLost()
    if (reply[0].toInt() != 5) return ProxyFailure(502, "The proxy did not answer as a SOCKS5 proxy")
    when (reply[1].toInt() and 0xFF) {
        0 -> Unit
        2 -> {
            if (user.isEmpty()) return ProxyFailure(407, "The SOCKS5 proxy asks for a user name and password")
            val login = ByteArray(3 + user.size + password.size)
            login[0] = 1
            login[1] = user.size.toByte()
            user.copyInto(login, 2)
            login[2 + user.size] = password.size.toByte()
            password.copyInto(login, 3 + user.size)
            if (!sendAll(socket, login)) return socksLost()
            if (!receiveExact(socket, reply, 2)) return socksLost()
            if (reply[1].toInt() != 0) return ProxyFailure(407, "The SOCKS5 proxy refused the user name or password")
        }

        else -> return ProxyFailure(
            407,
            if (user.isEmpty()) "The SOCKS5 proxy asks for a user name and password"
            else "The SOCKS5 proxy accepts no login that FetchJet knows"
        )
    }
    val literal = sockets.numericAddress(address.host)
    val name = if (literal == null) address.host.encodeToByteArray() else NO_BYTES
    if (literal == null && (name.isEmpty() || name.size > 255)) return ProxyFailure(400, "Bad Request")
    val request = ByteArray(6 + (literal?.size ?: (name.size + 1)))
    request[0] = 5
    request[1] = 1
    var at = 4
    if (literal != null) {
        request[3] = if (literal.size == 4) 1 else 4
        literal.copyInto(request, at)
        at += literal.size
    } else {
        request[3] = 3
        request[at++] = name.size.toByte()
        name.copyInto(request, at)
        at += name.size
    }
    request[at] = (address.port shr 8).toByte()
    request[at + 1] = address.port.toByte()
    if (!sendAll(socket, request)) return socksLost()
    if (!receiveExact(socket, reply, 4)) return socksLost()
    if (reply[0].toInt() != 5) return ProxyFailure(502, "The proxy did not answer as a SOCKS5 proxy")
    val code = reply[1].toInt() and 0xFF
    if (code != 0) return ProxyFailure(if (code == 6) 504 else 502, socksRefusal(code))
    val bound = when (reply[3].toInt()) {
        1 -> 6
        4 -> 18
        3 -> {
            if (!receiveExact(socket, reply, 1)) return socksLost()
            (reply[0].toInt() and 0xFF) + 2
        }

        else -> return ProxyFailure(502, "The proxy did not answer as a SOCKS5 proxy")
    }
    if (!receiveExact(socket, ByteArray(bound), bound)) return socksLost()
    return null
}

private class ProxyRateLimiter(private val speedLimit: () -> Long) {
    private val lock = sockets.newLock()
    private val clock = TimeSource.Monotonic.markNow()
    private var asked = false
    private var askedAtNanos = 0L
    private var limit = 0L
    private var lastLimit = 0L
    private var tokens = 0.0
    private var refilledAtNanos = 0L

    private fun currentLimit(now: Long): Long {
        if (!asked || now - askedAtNanos >= LIMIT_REFRESH_NANOS) {
            asked = true
            askedAtNanos = now
            limit = try {
                speedLimit()
            } catch (_: Throwable) {
                0L
            }
        }
        return limit
    }

    fun chunkBytes(most: Int): Int {
        val current = lock.withLock { currentLimit(clock.elapsedNow().inWholeNanoseconds) }
        return if (current <= 0L) most else (current / 10L).coerceIn(4096L, most.toLong()).toInt()
    }

    fun acquire(bytes: Int, stopping: AtomicInt) {
        if (bytes <= 0) return
        var waitMs = 0L
        lock.withLock {
            val now = clock.elapsedNow().inWholeNanoseconds
            val current = currentLimit(now)
            if (current <= 0L) {
                tokens = 0.0
            } else {
                if (current != lastLimit) {
                    lastLimit = current
                    tokens = 0.0
                    refilledAtNanos = now
                }
                val elapsedSeconds = (now - refilledAtNanos) / 1_000_000_000.0
                refilledAtNanos = now
                val most = maxOf(16 * 1024.0, current * 0.20)
                tokens = minOf(most, tokens + elapsedSeconds * current.toDouble()) - bytes.toDouble()
                if (tokens < 0.0) waitMs = (-tokens / current.toDouble() * 1000.0).toLong()
            }
        }
        while (waitMs > 0L && stopping.value == 0) {
            val slice = minOf(waitMs, 50L)
            sockets.sleepMs(slice.toInt())
            waitMs -= slice
        }
    }
}

private class ProxyTunnel(val client: Long) {
    var remote = sockets.noSocket
    var released = false
    val stopping = AtomicInt(0)
    val threads = AtomicInt(1)
}

class SocksUpstream(val host: String, val port: Int, val user: String = "", val password: String = "")

class LoopbackProxy private constructor(
    private val listener: Long,
    val port: Int,
    private val upstream: SocksUpstream?,
    private val login: String?,
    speedLimit: (() -> Long)?,
) {
    private val limiter = speedLimit?.let { ProxyRateLimiter(it) }
    private val lock = sockets.newLock()
    private val tunnels = HashSet<ProxyTunnel>()
    private val running = AtomicInt(1)
    private val closed = AtomicInt(0)
    private val acceptorDone = AtomicInt(0)
    private val active = AtomicInt(0)
    private val downloaded = AtomicLong(0L)
    private val uploaded = AtomicLong(0L)
    private val clock = TimeSource.Monotonic.markNow()
    private val speedLock = sockets.newLock()
    private var speedAtMs = 0L
    private var speedAtBytes = 0L
    private var speed = 0L

    val isOpen: Boolean get() = running.value != 0

    val activeTunnels: Int get() = active.value

    val downloadedBytes: Long get() = downloaded.value

    val uploadedBytes: Long get() = uploaded.value

    fun downloadSpeedBytesPerSec(): Long = speedLock.withLock {
        val now = clock.elapsedNow().inWholeMilliseconds
        val elapsedMs = now - speedAtMs
        if (elapsedMs >= 350L) {
            val total = downloaded.value
            val delta = total - speedAtBytes
            if (delta > 0L) {
                val instant = delta * 1000L / elapsedMs
                speed = if (speed <= 0L) instant else (instant * 0.7 + speed * 0.3).toLong()
                speedAtMs = now
                speedAtBytes = total
            } else if (elapsedMs >= 1200L) {
                speed = 0L
                speedAtMs = now
                speedAtBytes = total
            }
        }
        speed
    }

    fun close() {
        if (!closed.compareAndSet(0, 1)) return
        running.value = 0
        var tries = 0
        while (acceptorDone.value == 0 && tries++ < 20) {
            knock()
            var waited = 0
            while (acceptorDone.value == 0 && waited < 250) {
                sockets.sleepMs(10)
                waited += 10
            }
        }
        val open = lock.withLock { tunnels.toList() }
        for (tunnel in open) stop(tunnel)
    }

    private fun knock() {
        val socket = sockets.newTcp(false)
        if (socket == sockets.noSocket) return
        sockets.connectLoopback(socket, port)
        sockets.close(socket)
    }

    private fun acceptLoop() {
        try {
            var failures = 0
            while (running.value != 0) {
                val client = sockets.accept(listener)
                if (running.value == 0) {
                    if (client != sockets.noSocket) sockets.close(client)
                    break
                }
                if (client == sockets.noSocket) {
                    if (++failures > 50) break
                    sockets.sleepMs(20)
                    continue
                }
                failures = 0
                val tunnel = ProxyTunnel(client)
                lock.withLock { tunnels.add(tunnel) }
                active.incrementAndGet()
                if (!sockets.startThread { serve(tunnel) }) finish(tunnel)
            }
            running.value = 0
            sockets.close(listener)
        } finally {
            acceptorDone.value = 1
        }
    }

    private fun adopt(tunnel: ProxyTunnel, socket: Long): Boolean = lock.withLock {
        if (tunnel.stopping.value != 0) {
            false
        } else {
            tunnel.remote = socket
            true
        }
    }

    private fun discard(tunnel: ProxyTunnel) {
        val old = lock.withLock {
            val socket = tunnel.remote
            tunnel.remote = sockets.noSocket
            socket
        }
        if (old != sockets.noSocket) sockets.close(old)
    }

    private fun stop(tunnel: ProxyTunnel) {
        lock.withLock {
            if (!tunnel.released && tunnel.stopping.compareAndSet(0, 1)) {
                sockets.interrupt(tunnel.client)
                if (tunnel.remote != sockets.noSocket) sockets.interrupt(tunnel.remote)
            }
        }
    }

    private fun finish(tunnel: ProxyTunnel) {
        if (tunnel.threads.decrementAndGet() != 0) return
        lock.withLock {
            tunnel.released = true
            tunnels.remove(tunnel)
        }
        sockets.close(tunnel.client)
        if (tunnel.remote != sockets.noSocket) sockets.close(tunnel.remote)
        active.decrementAndGet()
    }

    private fun serve(tunnel: ProxyTunnel) {
        try {
            handshake(tunnel)
        } catch (_: Throwable) {
        } finally {
            stop(tunnel)
            finish(tunnel)
        }
    }

    private fun handshake(tunnel: ProxyTunnel) {
        val client = tunnel.client
        sockets.setNoDelay(client)
        sockets.setTimeouts(client, HEAD_TIMEOUT_MS)
        val head = readRequestHead(client) ?: return
        if (head.method.isEmpty()) return answer(client, 400, "Bad Request")
        if (login != null && !sameText(head.header("Proxy-Authorization").orEmpty(), login)) {
            return answer(client, 407, "Proxy Authentication Required")
        }
        val tunnelled = head.method.equals("CONNECT", ignoreCase = true)
        val address = (if (tunnelled) parseAuthority(head.target, 443) else plainTarget(head))
            ?: return answer(client, 400, "Bad Request")
        val socks = upstream
        val failure = if (socks == null) {
            openSocket(tunnel, address.host, address.port, "the server")
        } else {
            openSocket(tunnel, socks.host, socks.port, "the SOCKS5 proxy") ?: run {
                sockets.setTimeouts(tunnel.remote, UPSTREAM_TIMEOUT_MS)
                socksHandshake(tunnel.remote, socks, address)
            }
        }
        if (failure != null) {
            if (failure.status != 0) answer(client, failure.status, failure.reason)
            return
        }
        val remote = tunnel.remote
        sockets.setTimeouts(client, 0)
        sockets.setTimeouts(remote, 0)
        val first = if (tunnelled) NO_BYTES else originRequest(head, address)
        if (tunnelled && !sendAll(client, ESTABLISHED)) return
        if (!sendAll(remote, first) || !sendAll(remote, head.rest)) return
        uploaded.addAndGet((first.size + head.rest.size).toLong())
        tunnel.threads.incrementAndGet()
        if (!sockets.startThread { pumpUp(tunnel) }) {
            tunnel.threads.decrementAndGet()
            return
        }
        pumpDown(tunnel, !tunnelled)
    }

    private fun openSocket(tunnel: ProxyTunnel, host: String, port: Int, what: String): ProxyFailure? {
        val found = sockets.resolve(host, port) ?: return ProxyFailure(502, "The name of $what could not be resolved")
        var attempts = 0
        for (v6 in booleanArrayOf(false, true)) {
            for (address in found) {
                if (address.isV6 != v6) continue
                if (attempts >= CONNECT_ATTEMPTS) break
                attempts++
                val socket = sockets.newTcp(v6)
                if (socket == sockets.noSocket) continue
                if (!adopt(tunnel, socket)) {
                    sockets.close(socket)
                    return TUNNEL_STOPPED
                }
                if (sockets.connect(socket, address)) {
                    sockets.setNoDelay(socket)
                    return null
                }
                discard(tunnel)
                if (tunnel.stopping.value != 0) return TUNNEL_STOPPED
            }
        }
        return ProxyFailure(502, "Could not connect to $what")
    }

    private fun pumpUp(tunnel: ProxyTunnel) {
        try {
            val buffer = nativeHeap.allocArray<ByteVar>(UP_BUFFER_BYTES)
            try {
                while (tunnel.stopping.value == 0) {
                    val read = sockets.receive(tunnel.client, buffer, UP_BUFFER_BYTES)
                    if (read <= 0) break
                    uploaded.addAndGet(read.toLong())
                    if (!sendAll(tunnel.remote, buffer, read)) break
                }
            } finally {
                nativeHeap.free(buffer)
            }
        } catch (_: Throwable) {
        } finally {
            stop(tunnel)
            finish(tunnel)
        }
    }

    private fun pumpDown(tunnel: ProxyTunnel, plain: Boolean) {
        if (plain && !passResponseHead(tunnel)) return
        val buffer = nativeHeap.allocArray<ByteVar>(DOWN_BUFFER_BYTES)
        try {
            while (tunnel.stopping.value == 0) {
                val read = sockets.receive(tunnel.remote, buffer, limiter?.chunkBytes(DOWN_BUFFER_BYTES) ?: DOWN_BUFFER_BYTES)
                if (read <= 0) break
                downloaded.addAndGet(read.toLong())
                limiter?.acquire(read, tunnel.stopping)
                if (!sendAll(tunnel.client, buffer, read)) break
            }
        } finally {
            nativeHeap.free(buffer)
        }
    }

    private fun passResponseHead(tunnel: ProxyTunnel): Boolean {
        var pending = NO_BYTES
        val chunk = ByteArray(8192)
        while (true) {
            val end = headEnd(pending, pending.size)
            if (end >= 0) {
                val status = latin1(pending, 0, end).substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                if (status == 101) return sendAll(tunnel.client, pending)
                if (status in 100..199) {
                    if (!sendAll(tunnel.client, pending, 0, end + 4)) return false
                    pending = pending.copyOfRange(end + 4, pending.size)
                    continue
                }
                return sendAll(tunnel.client, closingResponseHead(latin1(pending, 0, end))) &&
                    sendAll(tunnel.client, pending, end + 4)
            }
            if (pending.size >= HEAD_LIMIT_BYTES) return sendAll(tunnel.client, pending)
            val read = receive(tunnel.remote, chunk, 0, limiter?.chunkBytes(chunk.size) ?: chunk.size)
            if (read <= 0) {
                sendAll(tunnel.client, pending)
                return false
            }
            downloaded.addAndGet(read.toLong())
            limiter?.acquire(read, tunnel.stopping)
            pending += chunk.copyOf(read)
        }
    }

    companion object {
        fun start(
            upstream: SocksUpstream? = null,
            login: String? = null,
            speedLimit: (() -> Long)? = null,
        ): LoopbackProxy {
            sockets.prepare()
            val listener = sockets.newTcp(false)
            if (listener == sockets.noSocket) throw LoopbackProxyException("socket: " + sockets.lastErrorText())
            val port = sockets.listenOnLoopback(listener)
            if (port <= 0) {
                val text = sockets.lastErrorText()
                sockets.close(listener)
                throw LoopbackProxyException("listen on 127.0.0.1: $text")
            }
            val expected = login?.let { "Basic " + Base64.encode(it.encodeToByteArray()) }
            val proxy = LoopbackProxy(listener, port, upstream, expected, speedLimit)
            if (!sockets.startThread { proxy.acceptLoop() }) {
                val text = sockets.lastErrorText()
                sockets.close(listener)
                throw LoopbackProxyException("thread: $text")
            }
            return proxy
        }
    }
}

object SocksForwarder {
    const val LOGIN_USER = "fetchjet"

    val loginPassword: String by lazy { sockets.randomHex(24) }

    private val lock by lazy { sockets.newLock() }
    private val forwarders = HashMap<String, LoopbackProxy>()

    fun portFor(host: String, port: Int, user: String, password: String): Int = lock.withLock {
        val key = "$host\u0000$port\u0000$user\u0000$password"
        val known = forwarders[key]
        if (known != null && known.isOpen) {
            known.port
        } else {
            val started = LoopbackProxy.start(SocksUpstream(host, port, user, password), "$LOGIN_USER:$loginPassword")
            forwarders[key] = started
            started.port
        }
    }

    fun closeAll() {
        val open = lock.withLock {
            val all = forwarders.values.toList()
            forwarders.clear()
            all
        }
        for (forwarder in open) forwarder.close()
    }
}
