package com.michelle.raouf.fetch_jet.feature.device_sync

import com.michelle.raouf.fetch_jet.core.security.CryptoBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val DISCOVERY_PORT = 28461
private const val SYNC_TCP_PORT = 28462
private const val ANNOUNCE_EVERY_MS = 2000L
private const val DEVICE_EXPIRE_MS = 8000L
private const val CHUNK = 64 * 1024
private const val ACCEPT_WAIT_MS = 90_000L

private const val T_HELLO: Byte = 1
private const val T_HELLO_ACK: Byte = 2
private const val T_SEND_META: Byte = 3
private const val T_FILE_CHUNK: Byte = 4
private const val T_FILE_END: Byte = 5
private const val T_ACK: Byte = 6
private const val T_ERR: Byte = 7
private const val T_READY: Byte = 8

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

/** One file to stream: either a file on disk or a small in-memory blob (a generated manifest). */
private class OutgoingFile(val wireName: String, val file: File?, val inlineBytes: ByteArray?) {
    val size: Long get() = inlineBytes?.size?.toLong() ?: file?.length() ?: 0L
}

private class Pending(val device: PairedDevice, val key: ByteArray)

@OptIn(ExperimentalEncodingApi::class)
internal class DeviceSyncImpl(
    private val platformName: String,
    private val scratchDirFor: (String) -> String,
    private val tempDir: String,
    private val onReceive: (SendPayload, List<String>) -> Unit
) : DeviceSync {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discovered: StateFlow<List<DiscoveredDevice>> = _discovered.asStateFlow()
    private val _paired = MutableStateFlow(PairedDeviceStore.pairedDevices())
    override val paired: StateFlow<List<PairedDevice>> = _paired.asStateFlow()
    private val _pairing = MutableStateFlow<PairingState?>(null)
    override val pairing: StateFlow<PairingState?> = _pairing.asStateFlow()
    private val _sending = MutableStateFlow<SendState?>(null)
    override val sending: StateFlow<SendState?> = _sending.asStateFlow()

    override val selfId: String = PairedDeviceStore.selfId
    override val selfName: String get() = PairedDeviceStore.selfName.ifBlank { platformName }
    override fun setSelfName(name: String) {
        PairedDeviceStore.selfName = name
    }

    /** id -> last seen (host, port, name, platform). */
    private val seen = LinkedHashMap<String, DiscoveredDevice>()
    private var serverSocket: ServerSocket? = null
    private var multicastToken: Any? = null
    private var pending: Pending? = null
    private var started = false

    override fun start() {
        if (started) return
        started = true
        println("FJSYNC start selfId=$selfId name=$selfName visible=$visible")
        multicastToken = MulticastLock.acquire()
        scope.launch { discoveryListenLoop() }
        scope.launch { expireLoop() }
        applyVisibility()
    }

    override fun stop() {
        started = false
        closeServer()
        MulticastLock.release(multicastToken)
        multicastToken = null
    }

    @Volatile
    private var visible = false
    private var announceJob: Job? = null

    override fun setVisible(visible: Boolean) {
        this.visible = visible
        if (started) applyVisibility()
    }

    @Synchronized
    private fun applyVisibility() {
        if (visible && serverSocket == null) {
            val server = ServerSocket()
            server.reuseAddress = true
            try {
                server.bind(InetSocketAddress(SYNC_TCP_PORT))
            } catch (_: Throwable) {
                server.bind(InetSocketAddress(0))
            }
            serverSocket = server
            println("FJSYNC visible tcpPort=${server.localPort}")
            scope.launch { acceptLoop(server) }
            announceJob = scope.launch { announceLoop(server.localPort) }
        } else if (!visible && serverSocket != null) {
            closeServer()
            println("FJSYNC hidden")
        }
    }

    @Synchronized
    private fun closeServer() {
        announceJob?.cancel()
        announceJob = null
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
    }

    // ---- discovery --------------------------------------------------------

    private suspend fun announceLoop(tcpPort: Int) {
        val socket = DatagramSocket().apply { broadcast = true }
        val body = json.encodeToString(
            Announce.serializer(),
            Announce(id = selfId, name = selfName, plat = platformName, port = tcpPort)
        ).encodeToByteArray()
        var loggedTargets: String? = null
        try {
            while (scope.isActive && started && visible) {
                val targets = broadcastAddresses()
                val sig = targets.joinToString { it.hostAddress ?: "" }
                if (sig != loggedTargets) {
                    loggedTargets = sig
                    println("FJSYNC announce -> [$sig]:$DISCOVERY_PORT")
                }
                for (addr in targets) {
                    try {
                        socket.send(DatagramPacket(body, body.size, addr, DISCOVERY_PORT))
                    } catch (_: Throwable) {
                    }
                }
                kotlinx.coroutines.delay(ANNOUNCE_EVERY_MS)
            }
        } finally {
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address
                    if (a !is java.net.Inet4Address) continue
                    val b = ia.broadcast ?: computeBroadcast(a, ia.networkPrefixLength.toInt())
                    if (b != null) out.add(b)
                }
            }
        } catch (_: Throwable) {
        }
        if (out.isEmpty()) runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        return out.toList()
    }

    private fun computeBroadcast(addr: java.net.Inet4Address, prefix: Int): InetAddress? = try {
        if (prefix !in 1..31) {
            null
        } else {
            val a = addr.address
            val ip = ((a[0].toInt() and 0xff) shl 24) or ((a[1].toInt() and 0xff) shl 16) or
                    ((a[2].toInt() and 0xff) shl 8) or (a[3].toInt() and 0xff)
            val bc = ip or (-0x1 ushr prefix)
            InetAddress.getByAddress(
                byteArrayOf(
                    (bc ushr 24).toByte(), (bc ushr 16).toByte(),
                    (bc ushr 8).toByte(), bc.toByte()
                )
            )
        }
    } catch (_: Throwable) {
        null
    }

    private suspend fun discoveryListenLoop() {
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
        } catch (t: Throwable) {
            println("FJSYNC listen bind failed: ${t.message}")
            return
        }
        val buf = ByteArray(2048)
        while (scope.isActive && started) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val text = String(packet.data, packet.offset, packet.length)
                val ann =
                    runCatching { json.decodeFromString(Announce.serializer(), text) }.getOrNull()
                if (ann == null || ann.t != "fj-ann" || ann.id == selfId) continue
                println("FJSYNC heard ${ann.name} @ ${packet.address.hostAddress}:${ann.port}")
                val device = DiscoveredDevice(
                    id = ann.id, name = ann.name, platform = ann.plat,
                    host = packet.address.hostAddress ?: continue, port = ann.port,
                    paired = PairedDeviceStore.isPaired(ann.id)
                )
                synchronized(seen) { seen[ann.id] = device }
                lastSeenAt[ann.id] = System.currentTimeMillis()
                publishDiscovered()
            } catch (_: Throwable) {
                if (!started) break
            }
        }
        try {
            socket.close()
        } catch (_: Throwable) {
        }
    }

    private val lastSeenAt = HashMap<String, Long>()

    private suspend fun expireLoop() {
        while (scope.isActive && started) {
            kotlinx.coroutines.delay(2000)
            val now = System.currentTimeMillis()
            val expired = lastSeenAt.filterValues { now - it > DEVICE_EXPIRE_MS }.keys
            if (expired.isNotEmpty()) {
                synchronized(seen) { expired.forEach { seen.remove(it); lastSeenAt.remove(it) } }
                publishDiscovered()
            }
        }
    }

    private fun publishDiscovered() {
        val list = synchronized(seen) {
            seen.values.map { it.copy(paired = PairedDeviceStore.isPaired(it.id)) }
        }
        _discovered.value = list
    }

    // ---- server (receiver side) ------------------------------------------

    private suspend fun acceptLoop(server: ServerSocket) {
        while (scope.isActive && started) {
            val socket = try {
                server.accept()
            } catch (_: Throwable) {
                if (!started || server.isClosed) break else continue
            }
            scope.launch { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val (type, body) = readFrame(input) ?: return
            when (type) {
                T_HELLO -> handleHello(body, output)
                T_SEND_META -> handleSend(body, input, output)
                else -> writeFrame(output, T_ERR, "unknown".encodeToByteArray())
            }
            output.flush()
        }
    }

    private fun handleHello(body: ByteArray, output: DataOutputStream) {
        val hello = runCatching {
            json.decodeFromString(
                Hello.serializer(),
                body.decodeToString()
            )
        }.getOrNull()
            ?: return
        val kp = newEcKeyPair()
        val pubB = kp.public.encoded
        val pubA = Base64.decode(hello.pub)
        val key = deriveKey(kp, pubA)
        val ack = Hello(selfId, selfName, platformName, Base64.encode(pubB))
        writeFrame(
            output,
            T_HELLO_ACK,
            json.encodeToString(Hello.serializer(), ack).encodeToByteArray()
        )
        output.flush()
        val code = pairingCode(pubA, pubB)
        pending =
            Pending(PairedDevice(hello.id, hello.name, hello.plat, System.currentTimeMillis()), key)
        _pairing.value = PairingState.Confirm(hello.name, hello.id, code)
    }

    private fun handleSend(body: ByteArray, input: DataInputStream, output: DataOutputStream) {
        // body = [idLen:int][fromId bytes][sealed meta]
        val bb = DataInputStream(body.inputStream())
        val idLen = bb.readInt()
        val idBytes = ByteArray(idLen).also { bb.readFully(it) }
        val fromId = idBytes.decodeToString()
        val sealed = bb.readBytes()
        val key = PairedDeviceStore.keyFor(fromId)
        if (key == null) {
            writeFrame(output, T_ERR, "not paired".encodeToByteArray()); return
        }
        val metaJson =
            runCatching { CryptoBox.open(key, sealed).decodeToString() }.getOrNull() ?: run {
                writeFrame(output, T_ERR, "bad key".encodeToByteArray()); return
            }
        val meta =
            runCatching { json.decodeFromString(SendMeta.serializer(), metaJson) }.getOrNull()
                ?: run {
                    writeFrame(output, T_ERR, "bad meta".encodeToByteArray()); return
                }
        // Paired and able to read the meta: tell the sender it may stream now. Until it sees this
        // it holds the file, so a not-yet-confirmed pairing gets a clean T_ERR, not a broken pipe.
        writeFrame(output, T_READY, ByteArray(0))
        output.flush()
        val written = ArrayList<String>()
        val dir = File(tempDir, "recv-${System.currentTimeMillis()}")
        if (meta.files.isNotEmpty()) dir.mkdirs()
        try {
            for (fm in meta.files) {
                val outFile = File(dir, safeWireName(fm.name) ?: throw IllegalStateException("bad file name"))
                outFile.outputStream().buffered().use { fileOut ->
                    while (true) {
                        val (t, b) = readFrame(input) ?: throw IllegalStateException("stream ended")
                        if (t == T_FILE_END) break
                        if (t != T_FILE_CHUNK) throw IllegalStateException("unexpected frame")
                        fileOut.write(CryptoBox.open(key, b))
                    }
                }
                written.add(outFile.absolutePath)
            }
            onReceive(meta.payload.copy(fromDeviceId = fromId), written)
            writeFrame(output, T_ACK, ByteArray(0))
        } catch (t: Throwable) {
            runCatching { writeFrame(output, T_ERR, (t.message ?: "error").encodeToByteArray()) }
        }
    }

    // ---- client (sender side) --------------------------------------------

    override fun startPairing(deviceId: String) {
        val device = synchronized(seen) { seen[deviceId] } ?: return
        connectAndPair(device.host, device.port, device.name)
    }

    override fun startPairingWithHost(host: String) {
        connectAndPair(host, SYNC_TCP_PORT, host)
    }

    private fun connectAndPair(host: String, port: Int, displayName: String) {
        _pairing.value = PairingState.Connecting(displayName)
        scope.launch {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 5000)
                    val input = DataInputStream(socket.getInputStream().buffered())
                    val output = DataOutputStream(socket.getOutputStream().buffered())
                    val kp = newEcKeyPair()
                    val pubA = kp.public.encoded
                    val hello = Hello(selfId, selfName, platformName, Base64.encode(pubA))
                    writeFrame(
                        output,
                        T_HELLO,
                        json.encodeToString(Hello.serializer(), hello).encodeToByteArray()
                    )
                    output.flush()
                    val (type, b) = readFrame(input) ?: throw IllegalStateException("no reply")
                    if (type != T_HELLO_ACK) throw IllegalStateException("bad reply")
                    val ack = json.decodeFromString(Hello.serializer(), b.decodeToString())
                    val pubB = Base64.decode(ack.pub)
                    val key = deriveKey(kp, pubB)
                    val code = pairingCode(pubA, pubB)
                    val disc = DiscoveredDevice(
                        ack.id, ack.name, ack.plat, host, port,
                        PairedDeviceStore.isPaired(ack.id)
                    )
                    synchronized(seen) { seen[ack.id] = disc }
                    lastSeenAt[ack.id] = System.currentTimeMillis()
                    publishDiscovered()
                    pending = Pending(
                        PairedDevice(
                            ack.id,
                            ack.name,
                            ack.plat,
                            System.currentTimeMillis()
                        ), key
                    )
                    _pairing.value = PairingState.Confirm(ack.name, ack.id, code)
                }
            } catch (t: Throwable) {
                _pairing.value = PairingState.Failed(displayName, t.message ?: "failed")
            }
        }
    }

    override fun confirmPairing() {
        val p = pending ?: return
        PairedDeviceStore.addPairing(p.device, p.key)
        pending = null
        _pairing.value = null
        _paired.value = PairedDeviceStore.pairedDevices()
        publishDiscovered()
    }

    override fun cancelPairing() {
        pending = null
        _pairing.value = null
    }

    override fun unpair(deviceId: String) {
        PairedDeviceStore.removePairing(deviceId)
        _paired.value = PairedDeviceStore.pairedDevices()
        publishDiscovered()
    }

    override fun send(request: SendRequest, onResult: (Boolean) -> Unit) {
        val device = synchronized(seen) { seen[request.deviceId] }
            ?: _paired.value.firstOrNull { it.id == request.deviceId }?.let {
                DiscoveredDevice(it.id, it.name, it.platform, "", 0, true)
            }
        val host = (device as? DiscoveredDevice)?.host
        val port = (device as? DiscoveredDevice)?.port ?: 0
        val name = device?.name ?: request.deviceId
        val key = PairedDeviceStore.keyFor(request.deviceId)
        if (host.isNullOrBlank() || port == 0 || key == null) {
            _sending.value =
                SendState.Failed(name, "device offline or not paired"); onResult(false); return
        }
        _sending.value = SendState.Sending(name, 0f)
        scope.launch {
            val deadline = System.currentTimeMillis() + ACCEPT_WAIT_MS
            while (scope.isActive) {
                val outcome = runCatching { trySendOnce(host, port, key, request, name) }
                    .getOrElse { SendOutcome.Fail(it.message ?: "failed") }
                when (outcome) {
                    SendOutcome.Success -> {
                        _sending.value = SendState.Done(name); onResult(true); return@launch
                    }

                    SendOutcome.WaitAccept -> {
                        if (System.currentTimeMillis() >= deadline) {
                            _sending.value =
                                SendState.Failed(name, "the other device didn't accept")
                            onResult(false); return@launch
                        }
                        _sending.value = SendState.Waiting(name)
                        kotlinx.coroutines.delay(1500)
                    }

                    is SendOutcome.Fail -> {
                        _sending.value = SendState.Failed(name, outcome.reason)
                        onResult(false); return@launch
                    }
                }
            }
        }
    }

    private sealed interface SendOutcome {
        object Success : SendOutcome
        object WaitAccept : SendOutcome
        data class Fail(val reason: String) : SendOutcome
    }

    /** One connect + handshake. Streams the file only after the receiver replies [T_READY]. */
    private fun trySendOnce(
        host: String,
        port: Int,
        key: ByteArray,
        request: SendRequest,
        name: String
    ): SendOutcome {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            // What travels under TRANSFER_AND_CONTINUE: the resume pieces if any are still on disk,
            // otherwise the finished file as one complete piece (its own `.chunks` saying so) so the
            // other side finalizes at 100% without downloading it again. Nothing for LINK_ONLY.
            val outgoing: List<OutgoingFile> = when {
                request.mode != SendMode.TRANSFER_AND_CONTINUE -> emptyList()
                request.partialFiles.isNotEmpty() ->
                    request.partialFiles.map { File(it) }.filter { it.exists() }
                        .map { OutgoingFile(it.name, it, null) }

                request.completedFilePath.isNotBlank() && File(request.completedFilePath).exists() -> {
                    val finished = File(request.completedFilePath)
                    val base = request.payload.fileName.ifBlank { finished.name }
                    val manifest =
                        "0 0 ${(finished.length() - 1).coerceAtLeast(0)}".encodeToByteArray()
                    listOf(
                        OutgoingFile("$base.chunks", null, manifest),
                        OutgoingFile("$base.part0", finished, null)
                    )
                }

                else -> emptyList()
            }
            val meta = SendMeta(
                payload = request.payload,
                mode = request.mode.name,
                files = outgoing.map { FileMeta(it.wireName, it.size) }
            )
            val sealedMeta = CryptoBox.seal(
                key,
                json.encodeToString(SendMeta.serializer(), meta).encodeToByteArray()
            )
            val baos = java.io.ByteArrayOutputStream()
            DataOutputStream(baos).apply {
                val idBytes = selfId.encodeToByteArray()
                writeInt(idBytes.size); write(idBytes); write(sealedMeta); flush()
            }
            writeFrame(output, T_SEND_META, baos.toByteArray())
            output.flush()
            // Wait for the receiver to accept before sending the file. "not paired" = the other
            // user has not confirmed the code yet, so keep waiting instead of failing.
            val (reply, rbody) = readFrame(input) ?: return SendOutcome.Fail("no reply")
            if (reply == T_ERR) {
                val reason = rbody.decodeToString()
                return if (reason == "not paired") SendOutcome.WaitAccept
                else SendOutcome.Fail(reason)
            }
            if (reply != T_READY) return SendOutcome.Fail("unexpected reply")
            val total = outgoing.sumOf { it.size }.coerceAtLeast(1)
            var sent = 0L
            fun progress(n: Int) {
                sent += n
                _sending.value = SendState.Sending(name, (sent.toFloat() / total).coerceIn(0f, 1f))
            }
            for (o in outgoing) {
                val inline = o.inlineBytes
                if (inline != null) {
                    writeFrame(output, T_FILE_CHUNK, CryptoBox.seal(key, inline))
                    progress(inline.size)
                } else o.file!!.inputStream().buffered().use { fin ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        val n = fin.read(buffer)
                        if (n <= 0) break
                        writeFrame(output, T_FILE_CHUNK, CryptoBox.seal(key, buffer.copyOf(n)))
                        progress(n)
                    }
                }
                writeFrame(output, T_FILE_END, ByteArray(0))
            }
            output.flush()
            val (type, _) = readFrame(input) ?: return SendOutcome.Fail("no ack")
            return if (type == T_ACK) SendOutcome.Success else SendOutcome.Fail("receiver rejected")
        }
    }

    // ---- crypto / framing -------------------------------------------------

    private fun newEcKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun deriveKey(mine: KeyPair, peerPubEncoded: ByteArray): ByteArray {
        val peerPub =
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPubEncoded))
        val agree =
            KeyAgreement.getInstance("ECDH").apply { init(mine.private); doPhase(peerPub, true) }
        return MessageDigest.getInstance("SHA-256").digest(agree.generateSecret())
    }

    private fun pairingCode(pubA: ByteArray, pubB: ByteArray): String {
        val ordered = if (compareBytes(pubA, pubB) <= 0) pubA + pubB else pubB + pubA
        val h = MessageDigest.getInstance("SHA-256").digest(ordered)
        val n =
            ((h[0].toInt() and 0xff) shl 16) or ((h[1].toInt() and 0xff) shl 8) or (h[2].toInt() and 0xff)
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val c = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
            if (c != 0) return c
        }
        return a.size - b.size
    }

    private fun readFrame(input: DataInputStream): Pair<Byte, ByteArray>? = try {
        val type = input.readByte()
        val len = input.readInt()
        require(len in 0..(64 * 1024 * 1024)) { "frame too big" }
        val body = ByteArray(len)
        input.readFully(body)
        type to body
    } catch (_: Throwable) {
        null
    }

    private fun writeFrame(output: DataOutputStream, type: Byte, body: ByteArray) {
        output.writeByte(type.toInt())
        output.writeInt(body.size)
        output.write(body)
    }
}
