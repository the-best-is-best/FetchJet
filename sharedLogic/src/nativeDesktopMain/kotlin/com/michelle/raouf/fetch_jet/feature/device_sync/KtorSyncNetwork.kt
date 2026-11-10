package com.michelle.raouf.fetch_jet.feature.device_sync

import io.ktor.network.selector.Selectable
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ASocket
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.writeFully
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal fun nativeDeviceSync(
    platformName: String,
    tempDir: String,
    store: DeviceSyncStore,
    onReceive: (SendPayload, List<String>) -> Unit,
    discoveryPort: Int,
    tcpPort: Int,
    bindHost: String,
    sendBroadcast: Boolean,
    timing: DeviceSyncTiming,
    platform: DeviceSyncPlatform,
): DeviceSyncEngine = DeviceSyncEngine(
    config = DeviceSyncConfig(
        platformName = platformName,
        tempDir = tempDir,
        discoveryPort = discoveryPort,
        tcpPort = tcpPort,
        bindHost = bindHost,
        sendBroadcast = sendBroadcast,
        alwaysDiscover = false,
        logTimestamps = true,
        timing = timing,
    ),
    platform = platform,
    store = store,
    onReceive = onReceive,
)

internal class KtorSyncNetwork(
    private val lock: SyncLock,
    private val broadcastAddresses: () -> List<String>,
    private val ignoreUnreachable: (Int) -> Unit,
) : SyncNetwork {
    @Volatile
    private var openSelector: SelectorManager? = null

    private val selector: SelectorManager
        get() = openSelector ?: lock.withLock {
            openSelector ?: SelectorManager(Dispatchers.IO).also { openSelector = it }
        }

    override suspend fun bindDiscovery(host: String?, port: Int): SyncUdpSocket {
        val socket = aSocket(selector).udp().bind(InetSocketAddress(host ?: DEVICE_SYNC_ANY_HOST, port)) {
            reuseAddress = true
            broadcast = true
        }
        quiet(socket)
        return KtorUdpSocket(socket)
    }

    override suspend fun openAnnouncer(host: String?): SyncUdpSocket {
        val socket = aSocket(selector).udp().bind(InetSocketAddress(host ?: DEVICE_SYNC_ANY_HOST, 0)) { broadcast = true }
        quiet(socket)
        return KtorUdpSocket(socket)
    }

    override suspend fun listen(host: String?, port: Int): SyncServerSocket =
        KtorServerSocket(aSocket(selector).tcp().bind(host ?: DEVICE_SYNC_ANY_HOST, port))

    override suspend fun connect(host: String, port: Int, timeoutMs: Long): SyncConnection? {
        var opened: Socket? = null
        val connected = try {
            withTimeoutOrNull(timeoutMs) {
                aSocket(selector).tcp().connect(host, port).also { opened = it }
            }
        } catch (e: CancellationException) {
            closeSocketQuietly(opened)
            throw e
        } catch (_: Throwable) {
            null
        }
        if (connected == null) {
            closeSocketQuietly(opened)
            return null
        }
        return KtorConnection(connected)
    }

    override suspend fun broadcast(via: SyncUdpSocket, port: Int, body: ByteArray): List<String> =
        via.sendToAll(broadcastAddresses().ifEmpty { listOf("255.255.255.255") }, port, body)

    override fun close() {
        val used = lock.withLock { openSelector.also { openSelector = null } }
        try {
            used?.close()
        } catch (_: Throwable) {
        }
    }

    private fun quiet(socket: ASocket) {
        val descriptor = (socket as? Selectable)?.descriptor ?: return
        if (descriptor > 0) ignoreUnreachable(descriptor)
    }
}

private class KtorUdpSocket(private val socket: BoundDatagramSocket) : SyncUdpSocket {
    override suspend fun receive(): SyncDatagram {
        val datagram = socket.receive()
        val host = (datagram.address as? InetSocketAddress)?.hostname ?: ""
        return SyncDatagram(host, datagram.packet.readByteArray())
    }

    override suspend fun send(host: String, port: Int, body: ByteArray) {
        val packet = Buffer()
        packet.write(body)
        socket.send(Datagram(packet, InetSocketAddress(host, port)))
    }

    override fun close() = socket.close()
}

private class KtorServerSocket(private val server: ServerSocket) : SyncServerSocket {
    override val port: Int = (server.localAddress as? InetSocketAddress)?.port ?: 0

    override suspend fun accept(): SyncConnection = KtorConnection(server.accept())

    override fun close() = server.close()
}

private class KtorConnection(private val socket: Socket) : SyncConnection {
    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel(autoFlush = false)

    override suspend fun read(count: Int, timeoutMs: Long): ByteArray? = try {
        withTimeoutOrNull(timeoutMs) { input.readByteArray(count) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    override suspend fun write(bytes: ByteArray) = output.writeFully(bytes)

    override suspend fun flush() = output.flush()

    override fun close() = socket.close()
}

private fun closeSocketQuietly(socket: ASocket?) {
    try {
        socket?.close()
    } catch (_: Throwable) {
    }
}
