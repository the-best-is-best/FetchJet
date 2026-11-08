package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.timeIntervalSince1970
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Epoch milliseconds (iosMain uses NSDate, not the now-deprecated kotlin.system.getTimeMillis). */
private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

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
private const val T_UNPAIR: Byte = 9
private const val PAIR_REQUEST_GAP_MS = 30_000L

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

private class Pending(val device: PairedDevice, val key: ByteArray, val keyPairId: Long)

/**
 * iOS device sync. Same wire protocol as [DeviceSyncImpl] on JVM/Android (UDP announce on
 * [DISCOVERY_PORT], TCP on [SYNC_TCP_PORT], the same frame types and the same ECDH + AES-256-GCM),
 * so an iPhone discovers, pairs with and transfers to/from a Windows / macOS / Android device.
 * The raw sockets and the crypto come from the native [IosDeviceSyncNative] bridge; everything
 * else (state, flows, pairing, framing) is here and shared with the other platforms by shape.
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class)
internal class IosDeviceSync(
    private val native: IosDeviceSyncNative,
    private val platformName: String,
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
    private val _blocked = MutableStateFlow(PairedDeviceStore.blockedDevices())
    override val blocked: StateFlow<List<PairedDevice>> = _blocked.asStateFlow()
    private val lastPairRequestAt = HashMap<String, Long>()
    private val _sending = MutableStateFlow<SendState?>(null)
    override val sending: StateFlow<SendState?> = _sending.asStateFlow()

    override val selfId: String = PairedDeviceStore.selfId
    override val selfName: String get() = PairedDeviceStore.selfName.ifBlank { platformName }
    override fun setSelfName(name: String) {
        PairedDeviceStore.selfName = name
    }

    private val lock = NSLock()
    private val seen = LinkedHashMap<String, DiscoveredDevice>()
    private val lastSeenAt = HashMap<String, Long>()
    private var pending: Pending? = null
    private var started = false
    private var tcpPort = SYNC_TCP_PORT

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    override fun start() {
        if (started) return
        started = true
        println("FJSYNC start selfId=$selfId name=$selfName visible=$visible")
        val udpListener = object : IosUdpPacketListener {
            override fun onPacket(senderIp: String, data: NSData) {
                onDiscoveryPacket(senderIp, data)
            }
        }
        native.startUdpListen(DISCOVERY_PORT, udpListener)
        scope.launch { expireLoop() }
        applyVisibility()
    }

    override fun stop() {
        started = false
        closeServer()
        native.stopUdp()
    }

    private var visible = false
    private var serverOpen = false
    private var announceJob: Job? = null

    override fun setVisible(visible: Boolean) {
        this.visible = visible
        if (started) applyVisibility()
    }

    private fun applyVisibility() {
        locked { applyVisibilityLocked() }
    }

    private fun applyVisibilityLocked() {
        if (visible && !serverOpen) {
            val tcpListener = object : IosTcpConnectionListener {
                override fun onConnection(connId: Long) {
                    scope.launch { handle(connId) }
                }
            }
            tcpPort = native.startTcpServer(SYNC_TCP_PORT, tcpListener)
                .let { if (it <= 0) SYNC_TCP_PORT else it }
            serverOpen = true
            println("FJSYNC visible tcpPort=$tcpPort")
            announceJob = scope.launch { announceLoop() }
        } else if (!visible && serverOpen) {
            closeServerLocked()
            println("FJSYNC hidden")
        }
    }

    private fun closeServer() {
        locked { closeServerLocked() }
    }

    private fun closeServerLocked() {
        announceJob?.cancel()
        announceJob = null
        if (serverOpen) native.stopTcpServer()
        serverOpen = false
    }

    // ---- discovery --------------------------------------------------------

    private suspend fun announceLoop() {
        val body = json.encodeToString(
            Announce.serializer(),
            Announce(id = selfId, name = selfName, plat = platformName, port = tcpPort)
        ).encodeToByteArray()
        while (scope.isActive && started && visible) {
            native.sendUdpBroadcast(DISCOVERY_PORT, body.toNSData())
            delay(ANNOUNCE_EVERY_MS)
        }
    }

    private fun onDiscoveryPacket(senderIp: String, data: NSData) {
        val text = data.toByteArray().decodeToString()
        val ann = runCatching { json.decodeFromString(Announce.serializer(), text) }.getOrNull()
        if (ann == null || ann.t != "fj-ann" || ann.id == selfId) return
        val device = DiscoveredDevice(
            id = ann.id, name = ann.name, platform = ann.plat,
            host = senderIp, port = ann.port, paired = PairedDeviceStore.isPaired(ann.id)
        )
        locked {
            seen[ann.id] = device
            lastSeenAt[ann.id] = nowMs()
        }
        publishDiscovered()
    }

    private suspend fun expireLoop() {
        while (scope.isActive && started) {
            delay(2000)
            val now = nowMs()
            val expired =
                locked { lastSeenAt.filterValues { now - it > DEVICE_EXPIRE_MS }.keys.toList() }
            if (expired.isNotEmpty()) {
                locked { expired.forEach { seen.remove(it); lastSeenAt.remove(it) } }
                publishDiscovered()
            }
        }
    }

    private fun publishDiscovered() {
        _discovered.value = locked {
            seen.values.filterNot { PairedDeviceStore.isBlocked(it.id) }
                .map { it.copy(paired = PairedDeviceStore.isPaired(it.id)) }
        }
    }

    // ---- server (receiver side) ------------------------------------------

    private fun handle(connId: Long) {
        try {
            val (type, body) = readFrame(connId) ?: return
            when (type) {
                T_HELLO -> handleHello(body, connId)
                T_SEND_META -> handleSend(body, connId)
                T_UNPAIR -> handleUnpair(body)
                else -> writeFrame(connId, T_ERR, "unknown".encodeToByteArray())
            }
        } finally {
            native.tcpClose(connId)
        }
    }

    private fun handleHello(body: ByteArray, connId: Long) {
        val hello = runCatching {
            json.decodeFromString(Hello.serializer(), body.decodeToString())
        }.getOrNull() ?: return
        pairRefusal(hello.id)?.let { reason ->
            println("FJSYNC pairing from ${hello.name} refused: $reason")
            writeFrame(connId, T_ERR, reason.encodeToByteArray()); return
        }
        val kp = native.generateEcKeyPair() ?: return
        val pubB = kp.publicX509.toByteArray()
        val pubA = Base64.decode(hello.pub)
        val key = native.deriveKey(kp.keyId, pubA.toNSData())?.toByteArray() ?: run {
            native.releaseKeyPair(kp.keyId); return
        }
        native.releaseKeyPair(kp.keyId)
        val ack = Hello(selfId, selfName, platformName, Base64.encode(pubB))
        writeFrame(
            connId,
            T_HELLO_ACK,
            json.encodeToString(Hello.serializer(), ack).encodeToByteArray()
        )
        val code = pairingCode(pubA, pubB)
        val p = Pending(PairedDevice(hello.id, hello.name, hello.plat, nowMs()), key, -1)
        pending = p
        _pairing.value = PairingState.Confirm(hello.name, hello.id, code)
        // The sender stops waiting after ACCEPT_WAIT_MS: close the code then too (as declined).
        scope.launch {
            delay(ACCEPT_WAIT_MS)
            if (pending === p) endPairing(p, "expired")
        }
    }

    private fun handleSend(body: ByteArray, connId: Long) {
        // body = [idLen:int][fromId bytes][sealed meta]
        var off = 0
        val idLen = readInt(body, off); off += 4
        val fromId = body.copyOfRange(off, off + idLen).decodeToString(); off += idLen
        val sealed = body.copyOfRange(off, body.size)
        val key = PairedDeviceStore.keyFor(fromId)
        if (key == null) {
            val reason = when {
                PairedDeviceStore.isBlocked(fromId) -> "blocked"
                pending?.device?.id == fromId -> "pending"
                else -> endedPairingReason(fromId) ?: "not paired"
            }
            if (reason != "pending") println("FJSYNC send from $fromId refused: $reason")
            writeFrame(connId, T_ERR, reason.encodeToByteArray()); return
        }
        val metaJson =
            native.aesOpen(key.toNSData(), sealed.toNSData())?.toByteArray()?.decodeToString()
                ?: run { writeFrame(connId, T_ERR, "bad key".encodeToByteArray()); return }
        val meta =
            runCatching { json.decodeFromString(SendMeta.serializer(), metaJson) }.getOrNull()
                ?: run { writeFrame(connId, T_ERR, "bad meta".encodeToByteArray()); return }
        // Paired and able to read the meta: tell the sender it may stream now.
        writeFrame(connId, T_READY, ByteArray(0))
        val written = ArrayList<String>()
        val dir = "$tempDir/recv-${nowMs()}"
        if (meta.files.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        }
        try {
            for (fm in meta.files) {
                val outPath = "$dir/${safeWireName(fm.name) ?: throw IllegalStateException("bad file name")}"
                val handle = FileChunkWriter(outPath)
                try {
                    while (true) {
                        val (t, b) = readFrame(connId)
                            ?: throw IllegalStateException("stream ended")
                        if (t == T_FILE_END) break
                        if (t != T_FILE_CHUNK) throw IllegalStateException("unexpected frame")
                        val plain = native.aesOpen(key.toNSData(), b.toNSData())?.toByteArray()
                            ?: throw IllegalStateException("bad chunk")
                        handle.write(plain)
                    }
                } finally {
                    handle.close()
                }
                written.add(outPath)
            }
            onReceive(meta.payload.copy(fromDeviceId = fromId), written)
            writeFrame(connId, T_ACK, ByteArray(0))
        } catch (t: Throwable) {
            runCatching { writeFrame(connId, T_ERR, (t.message ?: "error").encodeToByteArray()) }
        }
    }

    // ---- client (sender side) --------------------------------------------

    override fun startPairing(deviceId: String) {
        val device = locked { seen[deviceId] } ?: return
        connectAndPair(device.host, device.port, device.name)
    }

    override fun startPairingWithHost(host: String) {
        connectAndPair(host, SYNC_TCP_PORT, host)
    }

    private fun connectAndPair(host: String, port: Int, displayName: String) {
        _pairing.value = PairingState.Connecting(displayName)
        scope.launch {
            val connId = native.tcpConnect(host, port, 5000)
            if (connId < 0) {
                _pairing.value = PairingState.Failed(displayName, "could not reach the device")
                return@launch
            }
            try {
                val kp = native.generateEcKeyPair()
                    ?: throw IllegalStateException("crypto unavailable")
                val pubA = kp.publicX509.toByteArray()
                val hello = Hello(selfId, selfName, platformName, Base64.encode(pubA))
                writeFrame(
                    connId,
                    T_HELLO,
                    json.encodeToString(Hello.serializer(), hello).encodeToByteArray()
                )
                val (type, b) = readFrame(connId) ?: throw IllegalStateException("no reply")
                if (type == T_ERR) throw IllegalStateException(b.decodeToString())
                if (type != T_HELLO_ACK) throw IllegalStateException("bad reply")
                val ack = json.decodeFromString(Hello.serializer(), b.decodeToString())
                val pubB = Base64.decode(ack.pub)
                val key = native.deriveKey(kp.keyId, pubB.toNSData())?.toByteArray()
                    ?: throw IllegalStateException("key agreement failed")
                native.releaseKeyPair(kp.keyId)
                val code = pairingCode(pubA, pubB)
                val disc = DiscoveredDevice(
                    ack.id, ack.name, ack.plat, host, port, PairedDeviceStore.isPaired(ack.id)
                )
                locked { seen[ack.id] = disc; lastSeenAt[ack.id] = nowMs() }
                publishDiscovered()
                pending =
                    Pending(PairedDevice(ack.id, ack.name, ack.plat, nowMs()), key, -1)
                _pairing.value = PairingState.Confirm(ack.name, ack.id, code)
            } catch (t: Throwable) {
                _pairing.value = PairingState.Failed(displayName, t.message ?: "failed")
            } finally {
                native.tcpClose(connId)
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

    /** "blocked", "busy" (another request on screen), "wait:<seconds>", or null to show it. */
    private fun pairRefusal(fromId: String): String? = locked {
        val now = nowMs()
        val last = lastPairRequestAt[fromId]
        when {
            PairedDeviceStore.isBlocked(fromId) -> "blocked"
            pending.let { it != null && it.device.id != fromId } -> "busy"
            last != null && now - last < PAIR_REQUEST_GAP_MS ->
                "wait:${(PAIR_REQUEST_GAP_MS - (now - last) + 999) / 1000}"

            else -> {
                lastPairRequestAt[fromId] = now
                null
            }
        }
    }

    override fun blockPairing() {
        val p = pending ?: return
        PairedDeviceStore.block(p.device)
        pending = null
        _pairing.value = null
        _blocked.value = PairedDeviceStore.blockedDevices()
        _paired.value = PairedDeviceStore.pairedDevices()
        publishDiscovered()
    }

    override fun unblock(deviceId: String) {
        PairedDeviceStore.unblock(deviceId)
        _blocked.value = PairedDeviceStore.blockedDevices()
        publishDiscovered()
    }

    /** Device id -> (when, why) its code was closed without "Codes match": "declined" / "expired". */
    private val endedPairings = HashMap<String, Pair<Long, String>>()

    override fun cancelPairing() {
        val p = pending
        if (p != null) endPairing(p, "declined") else _pairing.value = null
    }

    private fun endPairing(p: Pending, why: String) {
        println("FJSYNC pairing code for ${p.device.name} closed: $why")
        locked { endedPairings[p.device.id] = nowMs() to why }
        pending = null
        _pairing.value = null
    }

    private fun endedPairingReason(id: String): String? = locked {
        val e = endedPairings[id]
        if (e != null && nowMs() - e.first < 120_000L) e.second else null
    }

    override fun unpair(deviceId: String) {
        val key = PairedDeviceStore.keyFor(deviceId)
        val device = locked { seen[deviceId] }
        forgetPairing(deviceId)
        // Tell the other device too (best effort: only if it is on the network and visible now).
        if (key != null && device != null && device.host.isNotBlank() && device.port > 0) {
            scope.launch {
                val connId = native.tcpConnect(device.host, device.port, 3000)
                if (connId >= 0) {
                    try {
                        val sealed = native.aesSeal(key.toNSData(), "unpair".encodeToByteArray().toNSData())
                            ?.toByteArray()
                        if (sealed != null) writeFrame(connId, T_UNPAIR, idAndSealed(sealed))
                    } finally {
                        native.tcpClose(connId)
                    }
                }
            }
        }
    }

    private fun forgetPairing(deviceId: String) {
        PairedDeviceStore.removePairing(deviceId)
        _paired.value = PairedDeviceStore.pairedDevices()
        publishDiscovered()
    }

    /** The other device unpaired this one: drop it here too, if the notice is really from it. */
    private fun handleUnpair(body: ByteArray) {
        val idLen = readInt(body, 0)
        val fromId = body.copyOfRange(4, 4 + idLen).decodeToString()
        val key = PairedDeviceStore.keyFor(fromId) ?: return
        val opened = native.aesOpen(key.toNSData(), body.copyOfRange(4 + idLen, body.size).toNSData())
            ?.toByteArray()?.decodeToString()
        if (opened == "unpair") {
            println("FJSYNC unpaired by $fromId")
            forgetPairing(fromId)
        }
    }

    /** `[idLen:int][selfId][sealed]`, the body of a frame that says who sends it. */
    private fun idAndSealed(sealed: ByteArray): ByteArray {
        val idBytes = selfId.encodeToByteArray()
        return writeInt(idBytes.size) + idBytes + sealed
    }

    private var sendCancelled = false

    override fun cancelSend() {
        sendCancelled = true
    }

    override fun send(request: SendRequest, onResult: (Boolean) -> Unit) {
        val device = locked { seen[request.deviceId] }
            ?: _paired.value.firstOrNull { it.id == request.deviceId }?.let {
                DiscoveredDevice(it.id, it.name, it.platform, "", 0, true)
            }
        val host = device?.host
        val port = device?.port ?: 0
        val name = device?.name ?: request.deviceId
        val key = PairedDeviceStore.keyFor(request.deviceId)
        if (host.isNullOrBlank() || port == 0 || key == null) {
            _sending.value =
                SendState.Failed(name, "device offline or not paired"); onResult(false); return
        }
        sendCancelled = false
        _sending.value = SendState.Sending(name, 0f)
        scope.launch {
            val deadline = nowMs() + ACCEPT_WAIT_MS
            while (scope.isActive) {
                val outcome = runCatching { trySendOnce(host, port, key, request, name) }
                    .getOrElse { SendOutcome.Fail(it.message ?: "failed") }
                when (outcome) {
                    SendOutcome.Success -> {
                        _sending.value = SendState.Done(name); onResult(true); return@launch
                    }

                    SendOutcome.WaitAccept -> {
                        if (nowMs() >= deadline) {
                            forgetPairing(request.deviceId)
                            _sending.value = SendState.Failed(name, "expired")
                            onResult(false); return@launch
                        }
                        _sending.value = SendState.Waiting(name)
                        delay(1500)
                        if (sendCancelled) {
                            _sending.value = null; onResult(false); return@launch
                        }
                    }

                    SendOutcome.NotPaired -> {
                        forgetPairing(request.deviceId)
                        _sending.value = SendState.Failed(
                            name, "the other device unpaired this one; send again to pair"
                        )
                        onResult(false); return@launch
                    }

                    is SendOutcome.Fail -> {
                        if (outcome.reason in setOf("blocked", "declined", "expired")) forgetPairing(request.deviceId)
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
        object NotPaired : SendOutcome
        data class Fail(val reason: String) : SendOutcome
    }

    /** What travels for TRANSFER_AND_CONTINUE: resume pieces, or the finished file as one piece. */
    private class OutgoingFile(
        val wireName: String,
        val path: String?,
        val inlineBytes: ByteArray?
    ) {
        val size: Long
            get() = inlineBytes?.size?.toLong() ?: path?.let { fileSize(it) } ?: 0L
    }

    private fun trySendOnce(
        host: String,
        port: Int,
        key: ByteArray,
        request: SendRequest,
        name: String
    ): SendOutcome {
        val connId = native.tcpConnect(host, port, 5000)
        if (connId < 0) return SendOutcome.Fail("could not reach the device")
        try {
            val outgoing: List<OutgoingFile> = when {
                request.mode != SendMode.TRANSFER_AND_CONTINUE -> emptyList()
                request.partialFiles.isNotEmpty() ->
                    request.partialFiles.filter { fileExists(it) }
                        .map { OutgoingFile(fileName(it), it, null) }

                request.completedFilePath.isNotBlank() && fileExists(request.completedFilePath) -> {
                    val finished = request.completedFilePath
                    val base = request.payload.fileName.ifBlank { fileName(finished) }
                    val manifest =
                        "0 0 ${(fileSize(finished) - 1).coerceAtLeast(0)}".encodeToByteArray()
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
            val sealedMeta = native.aesSeal(
                key.toNSData(),
                json.encodeToString(SendMeta.serializer(), meta).encodeToByteArray().toNSData()
            )?.toByteArray() ?: return SendOutcome.Fail("encryption failed")
            val idBytes = selfId.encodeToByteArray()
            val metaFrame = writeInt(idBytes.size) + idBytes + sealedMeta
            writeFrame(connId, T_SEND_META, metaFrame)
            // Wait for the receiver to accept before sending the file.
            val (reply, rbody) = readFrame(connId) ?: return SendOutcome.Fail("no reply")
            if (reply == T_ERR) {
                val reason = rbody.decodeToString()
                return when (reason) {
                    "pending" -> SendOutcome.WaitAccept
                    "not paired" -> SendOutcome.NotPaired
                    else -> SendOutcome.Fail(reason)
                }
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
                    val sealedChunk =
                        native.aesSeal(key.toNSData(), inline.toNSData())?.toByteArray()
                            ?: return SendOutcome.Fail("encryption failed")
                    writeFrame(connId, T_FILE_CHUNK, sealedChunk)
                    progress(inline.size)
                } else {
                    val reader = FileChunkReader(o.path!!, CHUNK)
                    try {
                        while (true) {
                            val part = reader.next() ?: break
                            val sealedChunk =
                                native.aesSeal(key.toNSData(), part.toNSData())?.toByteArray()
                                    ?: return SendOutcome.Fail("encryption failed")
                            writeFrame(connId, T_FILE_CHUNK, sealedChunk)
                            progress(part.size)
                        }
                    } finally {
                        reader.close()
                    }
                }
                writeFrame(connId, T_FILE_END, ByteArray(0))
            }
            val (type, _) = readFrame(connId) ?: return SendOutcome.Fail("no ack")
            return if (type == T_ACK) SendOutcome.Success else SendOutcome.Fail("receiver rejected")
        } finally {
            native.tcpClose(connId)
        }
    }

    // ---- crypto helpers ---------------------------------------------------

    private fun pairingCode(pubA: ByteArray, pubB: ByteArray): String {
        val ordered = if (compareBytes(pubA, pubB) <= 0) pubA + pubB else pubB + pubA
        val h = native.sha256(ordered.toNSData()).toByteArray()
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

    // ---- framing over the native TCP connection ---------------------------

    private fun readFrame(connId: Long): Pair<Byte, ByteArray>? {
        val head = native.tcpReadFully(connId, 5)?.toByteArray() ?: return null
        if (head.size < 5) return null
        val type = head[0]
        val len = readInt(head, 1)
        if (len < 0 || len > 64 * 1024 * 1024) return null
        if (len == 0) return type to ByteArray(0)
        val body = native.tcpReadFully(connId, len)?.toByteArray() ?: return null
        if (body.size < len) return null
        return type to body
    }

    private fun writeFrame(connId: Long, type: Byte, body: ByteArray): Boolean {
        val frame = ByteArray(5 + body.size)
        frame[0] = type
        frame[1] = ((body.size ushr 24) and 0xff).toByte()
        frame[2] = ((body.size ushr 16) and 0xff).toByte()
        frame[3] = ((body.size ushr 8) and 0xff).toByte()
        frame[4] = (body.size and 0xff).toByte()
        body.copyInto(frame, 5)
        return native.tcpWrite(connId, frame.toNSData())
    }

    private fun writeInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(),
        ((value ushr 8) and 0xff).toByte(),
        (value and 0xff).toByte()
    )

    private fun readInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xff) shl 24) or ((b[off + 1].toInt() and 0xff) shl 16) or
                ((b[off + 2].toInt() and 0xff) shl 8) or (b[off + 3].toInt() and 0xff)
}
