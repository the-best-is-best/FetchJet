@file:OptIn(ExperimentalEncodingApi::class)

package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.platform.win.WinEcdhP256
import com.michelle.raouf.fetch_jet.platform.win.WinLock
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmOpen
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmSeal
import com.michelle.raouf.fetch_jet.platform.win.winBroadcastAddresses
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winFullPath
import com.michelle.raouf.fetch_jet.platform.win.winGetEnv
import com.michelle.raouf.fetch_jet.platform.win.winSha256
import com.michelle.raouf.fetch_jet.platform.win.winUdpIgnoreUnreachable
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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val DEVICE_SYNC_DISCOVERY_PORT = 28461
internal const val DEVICE_SYNC_TCP_PORT = 28462
internal const val DEVICE_SYNC_ANY_HOST = "0.0.0.0"

private const val CHUNK = 64 * 1024
private const val MAX_FRAME = 64 * 1024 * 1024
private const val MAX_DATAGRAM = 2048
private const val MAX_INBOUND = 16
private const val CONNECT_MS = 5000L
private const val UNPAIR_CONNECT_MS = 3000L
private const val REPLY_MS = 30_000L
private const val IDLE_MS = 120_000L
private const val WAIT_RETRY_MS = 1500L
private const val ENDED_PAIRING_MS = 120_000L
private const val REBIND_MS = 1000L

private const val T_HELLO: Byte = 1
private const val T_HELLO_ACK: Byte = 2
private const val T_SEND_META: Byte = 3
private const val T_FILE_CHUNK: Byte = 4
private const val T_FILE_END: Byte = 5
private const val T_ACK: Byte = 6
private const val T_ERR: Byte = 7
private const val T_READY: Byte = 8
private const val T_UNPAIR: Byte = 9

@Serializable
private data class Announce(
    val t: String = "fj-ann",
    val id: String,
    val name: String,
    val plat: String,
    val port: Int
)

@Serializable
private data class Hello(val id: String, val name: String, val plat: String, val pub: String)

@Serializable
private data class FileMeta(val name: String, val size: Long)

@Serializable
private data class SendMeta(val payload: SendPayload, val mode: String, val files: List<FileMeta>)

private class OutgoingFile(val wireName: String, val path: String?, val inlineBytes: ByteArray?) {
    val size: Long get() = inlineBytes?.size?.toLong() ?: path?.let { syncFileSize(it) } ?: 0L
}

private class Pending(val device: PairedDevice, val key: ByteArray)

private sealed interface SendOutcome {
    object Success : SendOutcome
    object WaitAccept : SendOutcome
    object NotPaired : SendOutcome
    class Fail(val reason: String) : SendOutcome
}

internal class WinDeviceSync(
    private val platformName: String,
    tempDir: String,
    private val store: DeviceSyncStore,
    private val onReceive: (SendPayload, List<String>) -> Unit,
    private val discoveryPort: Int = DEVICE_SYNC_DISCOVERY_PORT,
    private val tcpPort: Int = DEVICE_SYNC_TCP_PORT,
    private val bindHost: String = DEVICE_SYNC_ANY_HOST,
    private val sendBroadcast: Boolean = winGetEnv("FETCHJET_SYNC_NO_BROADCAST") != "1",
    private val timing: DeviceSyncTiming = DeviceSyncTiming(),
) : DeviceSync {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t -> println("FJSYNC error: ${t.message}") }
    )
    private val lock = WinLock()
    private val serverGate = Mutex()
    private val receiveRoot = winFullPath(tempDir.replace('/', '\\'))

    private val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discovered: StateFlow<List<DiscoveredDevice>> = _discovered.asStateFlow()
    private val _paired = MutableStateFlow(store.pairedDevices())
    override val paired: StateFlow<List<PairedDevice>> = _paired.asStateFlow()
    private val _pairing = MutableStateFlow<PairingState?>(null)
    override val pairing: StateFlow<PairingState?> = _pairing.asStateFlow()
    private val _sending = MutableStateFlow<SendState?>(null)
    override val sending: StateFlow<SendState?> = _sending.asStateFlow()
    private val _blocked = MutableStateFlow(store.blockedDevices())
    override val blocked: StateFlow<List<PairedDevice>> = _blocked.asStateFlow()

    override val selfId: String = store.selfId
    override val selfName: String get() = store.selfName.ifBlank { platformName }

    override fun setSelfName(name: String) {
        store.selfName = name
    }

    private val seen = LinkedHashMap<String, DiscoveredDevice>()
    private val lastSeenAt = HashMap<String, Long>()
    private val lastPairRequestAt = HashMap<String, Long>()
    private val endedPairings = HashMap<String, Pair<Long, String>>()
    private val inbound = AtomicInt(0)

    @Volatile
    private var pending: Pending? = null

    @Volatile
    private var started = false

    @Volatile
    private var visible = false

    @Volatile
    private var sendCancelled = false

    @Volatile
    private var session: Job? = null

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    private var serverPort = 0

    @Volatile
    private var openSelector: SelectorManager? = null

    private val selector: SelectorManager
        get() = openSelector ?: lock.withLock {
            openSelector ?: SelectorManager(Dispatchers.IO).also { openSelector = it }
        }

    internal val listeningTcpPort: Int get() = serverPort

    override fun start() {
        val job = lock.withLock {
            if (started) return
            started = true
            SupervisorJob(scope.coroutineContext[Job]).also { session = it }
        }
        println("FJSYNC start selfId=$selfId name=$selfName visible=$visible")
        scope.launch(job) { discoveryListenLoop() }
        scope.launch(job) { expireLoop() }
        scope.launch(job) { announceLoop() }
        applyVisibility()
    }

    override fun stop() {
        val job = lock.withLock {
            started = false
            session.also { session = null }
        }
        job?.cancel()
        applyVisibility()
    }

    internal fun shutdown() {
        stop()
        val listener = server
        server = null
        serverPort = 0
        closeQuietly(listener)
        scope.cancel()
        val used = lock.withLock { openSelector.also { openSelector = null } }
        try {
            used?.close()
        } catch (_: Throwable) {
        }
    }

    override fun setVisible(visible: Boolean) {
        this.visible = visible
        if (started) applyVisibility()
    }

    private fun applyVisibility() {
        scope.launch {
            serverGate.withLock {
                val wanted = visible && started
                val listener = server
                if (wanted && listener == null) {
                    val bound = bindServer() ?: return@withLock
                    serverPort = (bound.localAddress as? InetSocketAddress)?.port ?: 0
                    server = bound
                    println("FJSYNC visible tcpPort=$serverPort")
                    scope.launch { acceptLoop(bound) }
                } else if (!wanted && listener != null) {
                    server = null
                    serverPort = 0
                    closeQuietly(listener)
                    if (started) println("FJSYNC hidden")
                }
            }
        }
    }

    private suspend fun bindServer(): ServerSocket? {
        try {
            return aSocket(selector).tcp().bind(bindHost, tcpPort)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        return try {
            aSocket(selector).tcp().bind(bindHost, 0)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            println("FJSYNC tcp bind failed: ${t.message}")
            null
        }
    }

    private fun announceBytes(): ByteArray {
        val port = serverPort
        val shown = visible && port > 0
        return json.encodeToString(
            Announce.serializer(),
            Announce(
                t = if (shown) "fj-ann" else "fj-probe",
                id = selfId, name = selfName, plat = platformName, port = if (shown) port else 0
            )
        ).encodeToByteArray()
    }

    private suspend fun announceLoop() {
        val socket = try {
            aSocket(selector).udp().bind(InetSocketAddress(bindHost, 0)) { broadcast = true }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            println("FJSYNC announce socket failed: ${t.message}")
            return
        }
        ignoreUnreachable(socket)
        var loggedTargets: String? = null
        try {
            while (true) {
                val body = announceBytes()
                if (visible && serverPort > 0 && sendBroadcast) {
                    val targets = winBroadcastAddresses().ifEmpty { listOf("255.255.255.255") }
                    val signature = targets.joinToString()
                    if (signature != loggedTargets) {
                        loggedTargets = signature
                        println("FJSYNC announce -> [$signature]:$discoveryPort")
                    }
                    for (target in targets) sendDatagram(socket, body, target)
                }
                val hosts = lock.withLock {
                    seen.values.filterNot { store.isBlocked(it.id) }.map { it.host }
                }.distinct()
                for (host in hosts) sendDatagram(socket, body, host)
                delay(timing.announceEveryMs)
            }
        } finally {
            closeQuietly(socket)
        }
    }

    private suspend fun discoveryListenLoop() {
        while (true) {
            val socket = try {
                aSocket(selector).udp().bind(InetSocketAddress(bindHost, discoveryPort)) {
                    reuseAddress = true
                    broadcast = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                println("FJSYNC listen bind failed: ${t.message}")
                return
            }
            ignoreUnreachable(socket)
            try {
                while (true) {
                    val datagram = socket.receive()
                    try {
                        onDatagram(socket, datagram)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                println("FJSYNC listen stopped: ${t.message}")
            } finally {
                closeQuietly(socket)
            }
            delay(REBIND_MS)
        }
    }

    private suspend fun onDatagram(socket: BoundDatagramSocket, datagram: Datagram) {
        val senderIp = (datagram.address as? InetSocketAddress)?.hostname ?: return
        val bytes = datagram.packet.readByteArray()
        if (bytes.size > MAX_DATAGRAM) return
        val ann = try {
            json.decodeFromString(Announce.serializer(), bytes.decodeToString())
        } catch (_: Throwable) {
            return
        }
        if (ann.id == selfId) return
        if (ann.t == "fj-probe") {
            if (visible && serverPort > 0 && !store.isBlocked(ann.id)) {
                sendDatagram(socket, announceBytes(), senderIp)
            }
            return
        }
        if (ann.t != "fj-ann" || store.isBlocked(ann.id)) return
        val known = lock.withLock { seen[ann.id] }
        if (known == null || known.host != senderIp || known.port != ann.port) {
            println("FJSYNC heard ${ann.name} @ $senderIp:${ann.port}")
        }
        val device = DiscoveredDevice(
            id = ann.id, name = ann.name, platform = ann.plat,
            host = senderIp, port = ann.port, paired = store.isPaired(ann.id)
        )
        lock.withLock {
            seen[ann.id] = device
            lastSeenAt[ann.id] = nowMs()
        }
        publishDiscovered()
    }

    private suspend fun sendDatagram(socket: BoundDatagramSocket, body: ByteArray, host: String) {
        try {
            val packet = Buffer()
            packet.write(body)
            socket.send(Datagram(packet, InetSocketAddress(host, discoveryPort)))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    private fun ignoreUnreachable(socket: ASocket) {
        val descriptor = (socket as? Selectable)?.descriptor ?: return
        if (descriptor > 0) winUdpIgnoreUnreachable(descriptor.toULong())
    }

    private suspend fun expireLoop() {
        while (true) {
            delay(timing.announceEveryMs)
            val now = nowMs()
            val changed = lock.withLock {
                val expired = lastSeenAt.filterValues { now - it > timing.deviceExpireMs }.keys.toList()
                expired.forEach {
                    seen.remove(it)
                    lastSeenAt.remove(it)
                }
                expired.isNotEmpty()
            }
            if (changed) publishDiscovered()
        }
    }

    private fun publishDiscovered() {
        lock.withLock {
            _discovered.value = seen.values.filterNot { store.isBlocked(it.id) }
                .map { it.copy(paired = store.isPaired(it.id)) }
        }
    }

    private fun publishPaired() {
        lock.withLock { _paired.value = store.pairedDevices() }
    }

    private suspend fun acceptLoop(listener: ServerSocket) {
        while (server === listener) {
            val socket = try {
                listener.accept()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (server !== listener) break
                delay(200)
                continue
            }
            if (inbound.value >= MAX_INBOUND) {
                closeQuietly(socket)
                continue
            }
            inbound.incrementAndGet()
            scope.launch {
                try {
                    handle(socket)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    println("FJSYNC connection failed: ${t.message}")
                } finally {
                    inbound.decrementAndGet()
                    closeQuietly(socket)
                }
            }
        }
    }

    private suspend fun handle(socket: Socket) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = false)
        val (type, body) = withTimeoutOrNull(timing.firstFrameMs) { readFrame(input) } ?: return
        when (type) {
            T_HELLO -> handleHello(body, output)
            T_SEND_META -> handleSend(body, input, output)
            T_UNPAIR -> handleUnpair(body)
            else -> writeFrame(output, T_ERR, "unknown".encodeToByteArray())
        }
        output.flush()
    }

    private suspend fun handleHello(body: ByteArray, output: ByteWriteChannel) {
        val hello = try {
            json.decodeFromString(Hello.serializer(), body.decodeToString())
        } catch (_: Throwable) {
            return
        }
        val refusal = pairRefusal(hello.id)
        if (refusal != null) {
            println("FJSYNC pairing from ${hello.name} refused: $refusal")
            writeFrame(output, T_ERR, refusal.encodeToByteArray())
            return
        }
        val keys = WinEcdhP256()
        val pubA: ByteArray
        val pubB: ByteArray
        val key: ByteArray
        try {
            pubB = keys.publicSpki
            pubA = Base64.decode(hello.pub)
            key = keys.deriveSha256(pubA)
        } finally {
            keys.close()
        }
        val ack = Hello(selfId, selfName, platformName, Base64.encode(pubB))
        writeFrame(output, T_HELLO_ACK, json.encodeToString(Hello.serializer(), ack).encodeToByteArray())
        output.flush()
        val code = pairingCode(pubA, pubB)
        val request = Pending(PairedDevice(hello.id, hello.name, hello.plat, nowMs()), key)
        pending = request
        _pairing.value = PairingState.Confirm(hello.name, hello.id, code)
        scope.launch {
            delay(timing.acceptWaitMs)
            if (pending === request) endPairing(request, "expired")
        }
    }

    private fun pairRefusal(fromId: String): String? = lock.withLock {
        if (store.isBlocked(fromId)) return "blocked"
        val current = pending
        if (current != null && current.device.id != fromId) return "busy"
        val now = nowMs()
        val last = lastPairRequestAt[fromId]
        if (last != null && now - last < timing.pairRequestGapMs) {
            return "wait:${(timing.pairRequestGapMs - (now - last) + 999) / 1000}"
        }
        lastPairRequestAt[fromId] = now
        null
    }

    override fun blockPairing() {
        val request = pending ?: return
        store.block(request.device)
        pending = null
        _pairing.value = null
        _blocked.value = store.blockedDevices()
        publishPaired()
        publishDiscovered()
    }

    override fun unblock(deviceId: String) {
        store.unblock(deviceId)
        _blocked.value = store.blockedDevices()
        publishDiscovered()
    }

    private suspend fun handleSend(body: ByteArray, input: ByteReadChannel, output: ByteWriteChannel) {
        val (fromId, sealed) = splitIdAndSealed(body) ?: return
        val key = store.keyFor(fromId)
        if (key == null) {
            val reason = when {
                store.isBlocked(fromId) -> "blocked"
                pending?.device?.id == fromId -> "pending"
                else -> endedPairingReason(fromId) ?: "not paired"
            }
            if (reason != "pending") println("FJSYNC send from $fromId refused: $reason")
            writeFrame(output, T_ERR, reason.encodeToByteArray())
            return
        }
        val metaJson = try {
            winAesGcmOpen(key, sealed).decodeToString()
        } catch (_: Throwable) {
            writeFrame(output, T_ERR, "bad key".encodeToByteArray())
            return
        }
        val meta = try {
            json.decodeFromString(SendMeta.serializer(), metaJson)
        } catch (_: Throwable) {
            writeFrame(output, T_ERR, "bad meta".encodeToByteArray())
            return
        }
        writeFrame(output, T_READY, ByteArray(0))
        output.flush()
        val dir = "$receiveRoot\\recv-${nowMs()}"
        val written = ArrayList<String>()
        try {
            if (meta.files.isNotEmpty()) winCreateDirectories(dir)
            for (file in meta.files) {
                val name = safeWireName(file.name) ?: throw IllegalStateException("bad file name")
                val path = "$dir\\$name"
                receiveFile(path, key, input)
                written.add(path)
            }
            onReceive(meta.payload.copy(fromDeviceId = fromId), written)
        } catch (e: CancellationException) {
            if (meta.files.isNotEmpty()) winDeleteRecursively(dir)
            throw e
        } catch (t: Throwable) {
            if (meta.files.isNotEmpty()) winDeleteRecursively(dir)
            try {
                writeFrame(output, T_ERR, (t.message ?: "error").encodeToByteArray())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
            return
        }
        writeFrame(output, T_ACK, ByteArray(0))
    }

    private suspend fun receiveFile(path: String, key: ByteArray, input: ByteReadChannel) {
        val writer = FileChunkWriter(path)
        try {
            while (true) {
                val (type, chunk) = withTimeoutOrNull(IDLE_MS) { readFrame(input) }
                    ?: throw IllegalStateException("stream ended")
                if (type == T_FILE_END) break
                if (type != T_FILE_CHUNK) throw IllegalStateException("unexpected frame")
                val plain = try {
                    winAesGcmOpen(key, chunk)
                } catch (_: Throwable) {
                    throw IllegalStateException("bad chunk")
                }
                writer.write(plain)
            }
        } finally {
            writer.close()
        }
    }

    override fun startPairing(deviceId: String) {
        val device = lock.withLock { seen[deviceId] } ?: return
        connectAndPair(device.host, device.port, device.name)
    }

    override fun startPairingWithHost(host: String) {
        connectAndPair(host, tcpPort, host)
    }

    private fun connectAndPair(host: String, port: Int, displayName: String) {
        _pairing.value = PairingState.Connecting(displayName)
        scope.launch {
            try {
                val socket = connect(host, port, CONNECT_MS)
                    ?: throw IllegalStateException("could not reach the device")
                try {
                    exchangeHello(socket, host, port)
                } finally {
                    closeQuietly(socket)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _pairing.value = PairingState.Failed(displayName, t.message ?: "failed")
            }
        }
    }

    private suspend fun exchangeHello(socket: Socket, host: String, port: Int) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = false)
        val keys = WinEcdhP256()
        try {
            val pubA = keys.publicSpki
            val hello = Hello(selfId, selfName, platformName, Base64.encode(pubA))
            writeFrame(output, T_HELLO, json.encodeToString(Hello.serializer(), hello).encodeToByteArray())
            output.flush()
            val (type, body) = withTimeoutOrNull(REPLY_MS) { readFrame(input) }
                ?: throw IllegalStateException("no reply")
            if (type == T_ERR) throw IllegalStateException(body.decodeToString())
            if (type != T_HELLO_ACK) throw IllegalStateException("bad reply")
            val ack = json.decodeFromString(Hello.serializer(), body.decodeToString())
            val pubB = Base64.decode(ack.pub)
            val key = keys.deriveSha256(pubB)
            val code = pairingCode(pubA, pubB)
            val found = DiscoveredDevice(ack.id, ack.name, ack.plat, host, port, store.isPaired(ack.id))
            lock.withLock {
                seen[ack.id] = found
                lastSeenAt[ack.id] = nowMs()
            }
            publishDiscovered()
            pending = Pending(PairedDevice(ack.id, ack.name, ack.plat, nowMs()), key)
            _pairing.value = PairingState.Confirm(ack.name, ack.id, code)
        } finally {
            keys.close()
        }
    }

    override fun confirmPairing() {
        val request = pending ?: return
        store.addPairing(request.device, request.key)
        pending = null
        _pairing.value = null
        publishPaired()
        publishDiscovered()
    }

    override fun cancelPairing() {
        val request = pending
        if (request != null) endPairing(request, "declined") else _pairing.value = null
    }

    private fun endPairing(request: Pending, why: String) {
        println("FJSYNC pairing code for ${request.device.name} closed: $why")
        lock.withLock { endedPairings[request.device.id] = nowMs() to why }
        pending = null
        _pairing.value = null
    }

    private fun endedPairingReason(id: String): String? = lock.withLock {
        val ended = endedPairings[id]
        if (ended != null && nowMs() - ended.first < ENDED_PAIRING_MS) ended.second else null
    }

    override fun unpair(deviceId: String) {
        val key = store.keyFor(deviceId)
        val device = lock.withLock { seen[deviceId] }
        forgetPairing(deviceId)
        if (key != null && device != null && device.host.isNotBlank() && device.port > 0) {
            scope.launch {
                val socket = connect(device.host, device.port, UNPAIR_CONNECT_MS) ?: return@launch
                try {
                    val output = socket.openWriteChannel(autoFlush = false)
                    writeFrame(output, T_UNPAIR, idAndSealed(winAesGcmSeal(key, "unpair".encodeToByteArray())))
                    output.flush()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                } finally {
                    closeQuietly(socket)
                }
            }
        }
    }

    private fun forgetPairing(deviceId: String) {
        store.removePairing(deviceId)
        publishPaired()
        publishDiscovered()
    }

    private fun handleUnpair(body: ByteArray) {
        val (fromId, sealed) = splitIdAndSealed(body) ?: return
        val key = store.keyFor(fromId) ?: return
        val genuine = try {
            winAesGcmOpen(key, sealed).decodeToString() == "unpair"
        } catch (_: Throwable) {
            false
        }
        if (genuine) {
            println("FJSYNC unpaired by $fromId")
            forgetPairing(fromId)
        }
    }

    private fun idAndSealed(sealed: ByteArray): ByteArray {
        val idBytes = selfId.encodeToByteArray()
        return intBytes(idBytes.size) + idBytes + sealed
    }

    override fun cancelSend() {
        sendCancelled = true
    }

    override fun send(request: SendRequest, onResult: (Boolean) -> Unit) {
        val device = lock.withLock { seen[request.deviceId] }
            ?: _paired.value.firstOrNull { it.id == request.deviceId }?.let {
                DiscoveredDevice(it.id, it.name, it.platform, "", 0, true)
            }
        val host = device?.host
        val port = device?.port ?: 0
        val name = device?.name ?: request.deviceId
        val key = store.keyFor(request.deviceId)
        if (host.isNullOrBlank() || port == 0 || key == null) {
            _sending.value = SendState.Failed(name, "device offline or not paired")
            onResult(false)
            return
        }
        sendCancelled = false
        _sending.value = SendState.Sending(name, 0f)
        scope.launch {
            val deadline = nowMs() + timing.acceptWaitMs
            while (true) {
                val outcome = try {
                    trySendOnce(host, port, key, request, name)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    SendOutcome.Fail(t.message ?: "failed")
                }
                when (outcome) {
                    SendOutcome.Success -> {
                        _sending.value = SendState.Done(name)
                        onResult(true)
                        return@launch
                    }

                    SendOutcome.WaitAccept -> {
                        if (nowMs() >= deadline) {
                            forgetPairing(request.deviceId)
                            _sending.value = SendState.Failed(name, "expired")
                            onResult(false)
                            return@launch
                        }
                        _sending.value = SendState.Waiting(name)
                        delay(WAIT_RETRY_MS)
                        if (sendCancelled) {
                            _sending.value = null
                            onResult(false)
                            return@launch
                        }
                    }

                    SendOutcome.NotPaired -> {
                        forgetPairing(request.deviceId)
                        _sending.value = SendState.Failed(
                            name, "the other device unpaired this one; send again to pair"
                        )
                        onResult(false)
                        return@launch
                    }

                    is SendOutcome.Fail -> {
                        if (outcome.reason in setOf("blocked", "declined", "expired")) {
                            forgetPairing(request.deviceId)
                        }
                        _sending.value = SendState.Failed(name, outcome.reason)
                        onResult(false)
                        return@launch
                    }
                }
            }
        }
    }

    private fun outgoingFiles(request: SendRequest): List<OutgoingFile> = when {
        request.mode != SendMode.TRANSFER_AND_CONTINUE -> emptyList()

        request.partialFiles.isNotEmpty() ->
            request.partialFiles.filter { syncFileSize(it) != null }
                .map { OutgoingFile(syncFileName(it), it, null) }

        request.completedFilePath.isNotBlank() && syncFileSize(request.completedFilePath) != null -> {
            val finished = request.completedFilePath
            val base = request.payload.fileName.ifBlank { syncFileName(finished) }
            val manifest = "0 0 ${((syncFileSize(finished) ?: 0L) - 1).coerceAtLeast(0)}".encodeToByteArray()
            listOf(
                OutgoingFile("$base.chunks", null, manifest),
                OutgoingFile("$base.part0", finished, null)
            )
        }

        else -> emptyList()
    }

    private suspend fun trySendOnce(
        host: String,
        port: Int,
        key: ByteArray,
        request: SendRequest,
        name: String
    ): SendOutcome {
        val socket = connect(host, port, CONNECT_MS) ?: return SendOutcome.Fail("could not reach the device")
        try {
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            val outgoing = outgoingFiles(request)
            val meta = SendMeta(
                payload = request.payload,
                mode = request.mode.name,
                files = outgoing.map { FileMeta(it.wireName, it.size) }
            )
            val sealedMeta = winAesGcmSeal(key, json.encodeToString(SendMeta.serializer(), meta).encodeToByteArray())
            writeFrame(output, T_SEND_META, idAndSealed(sealedMeta))
            output.flush()
            val (reply, replyBody) = withTimeoutOrNull(REPLY_MS) { readFrame(input) }
                ?: return SendOutcome.Fail("no reply")
            if (reply == T_ERR) {
                return when (val reason = replyBody.decodeToString()) {
                    "pending" -> SendOutcome.WaitAccept
                    "not paired" -> SendOutcome.NotPaired
                    else -> SendOutcome.Fail(reason)
                }
            }
            if (reply != T_READY) return SendOutcome.Fail("unexpected reply")
            val total = outgoing.sumOf { it.size }.coerceAtLeast(1)
            var sent = 0L
            for (file in outgoing) {
                val inline = file.inlineBytes
                if (inline != null) {
                    writeFrame(output, T_FILE_CHUNK, winAesGcmSeal(key, inline))
                    sent += inline.size
                    _sending.value = SendState.Sending(name, (sent.toFloat() / total).coerceIn(0f, 1f))
                } else {
                    val reader = FileChunkReader(file.path ?: continue, CHUNK)
                    try {
                        while (true) {
                            val part = reader.next() ?: break
                            writeFrame(output, T_FILE_CHUNK, winAesGcmSeal(key, part))
                            output.flush()
                            sent += part.size
                            _sending.value = SendState.Sending(name, (sent.toFloat() / total).coerceIn(0f, 1f))
                        }
                    } finally {
                        reader.close()
                    }
                }
                writeFrame(output, T_FILE_END, ByteArray(0))
            }
            output.flush()
            val (type, _) = withTimeoutOrNull(IDLE_MS) { readFrame(input) } ?: return SendOutcome.Fail("no ack")
            return if (type == T_ACK) SendOutcome.Success else SendOutcome.Fail("receiver rejected")
        } finally {
            closeQuietly(socket)
        }
    }

    private suspend fun connect(host: String, port: Int, timeoutMs: Long): Socket? {
        var opened: Socket? = null
        val connected = try {
            withTimeoutOrNull(timeoutMs) {
                aSocket(selector).tcp().connect(host, port).also { opened = it }
            }
        } catch (e: CancellationException) {
            closeQuietly(opened)
            throw e
        } catch (_: Throwable) {
            null
        }
        if (connected == null) closeQuietly(opened)
        return connected
    }
}

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

private fun closeQuietly(socket: ASocket?) {
    try {
        socket?.close()
    } catch (_: Throwable) {
    }
}

private fun pairingCode(pubA: ByteArray, pubB: ByteArray): String {
    val ordered = if (compareBytes(pubA, pubB) <= 0) pubA + pubB else pubB + pubA
    val hash = winSha256(ordered)
    val number = ((hash[0].toInt() and 0xff) shl 16) or ((hash[1].toInt() and 0xff) shl 8) or
        (hash[2].toInt() and 0xff)
    return (number % 1_000_000).toString().padStart(6, '0')
}

private fun compareBytes(a: ByteArray, b: ByteArray): Int {
    val shared = minOf(a.size, b.size)
    for (i in 0 until shared) {
        val difference = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
        if (difference != 0) return difference
    }
    return a.size - b.size
}

private fun intBytes(value: Int): ByteArray = byteArrayOf(
    ((value ushr 24) and 0xff).toByte(),
    ((value ushr 16) and 0xff).toByte(),
    ((value ushr 8) and 0xff).toByte(),
    (value and 0xff).toByte()
)

private fun splitIdAndSealed(body: ByteArray): Pair<String, ByteArray>? {
    if (body.size < 4) return null
    val idLength = ((body[0].toInt() and 0xff) shl 24) or ((body[1].toInt() and 0xff) shl 16) or
        ((body[2].toInt() and 0xff) shl 8) or (body[3].toInt() and 0xff)
    if (idLength < 0 || idLength > body.size - 4) return null
    return body.decodeToString(4, 4 + idLength) to body.copyOfRange(4 + idLength, body.size)
}

private suspend fun readFrame(input: ByteReadChannel): Pair<Byte, ByteArray>? = try {
    val type = input.readByte()
    val length = input.readInt()
    require(length in 0..MAX_FRAME) { "frame too big" }
    type to (if (length == 0) ByteArray(0) else input.readByteArray(length))
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    null
}

private suspend fun writeFrame(output: ByteWriteChannel, type: Byte, body: ByteArray) {
    output.writeByte(type)
    output.writeInt(body.size)
    output.writeFully(body)
}
