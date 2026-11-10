@file:OptIn(ExperimentalEncodingApi::class)

package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class SyncPending(
    val device: PairedDevice,
    val key: ByteArray,
    val offer: String? = null,
    val offerFile: String = "",
    val offerBytes: Long = 0L,
    val offerHasFiles: Boolean = false,
)

internal class SyncPairing(private val engine: DeviceSyncEngine) {
    @Volatile
    private var pending: SyncPending? = null
    private val lastPairRequestAt = HashMap<String, Long>()
    private val endedPairings = HashMap<String, Pair<Long, String>>()
    private val acceptedOffers = HashMap<String, Long>()

    private val timing get() = engine.timing
    private val store get() = engine.store
    private val crypto get() = engine.platform.crypto

    suspend fun onHello(body: ByteArray, connection: SyncConnection) {
        val hello = helloOf(body) ?: return
        val refusal = pairRefusal(hello.id)
        if (refusal != null) {
            engine.log("pairing from ${hello.name} refused: $refusal")
            connection.writeError(refusal)
            return
        }
        val keys = crypto.newKeyPair()
        val pubA: ByteArray
        val pubB: ByteArray
        val key: ByteArray
        try {
            pubB = keys.publicSpki
            pubA = Base64.decode(hello.pub)
            key = keys.derive(pubA)
        } finally {
            keys.close()
        }
        connection.writeFrame(T_HELLO_ACK, Hello(engine.selfId, engine.selfName, engine.config.platformName, Base64.encode(pubB)).toBytes())
        connection.flush()
        val code = pairingCode(pubA, pubB)
        val request = SyncPending(PairedDevice(hello.id, hello.name, hello.plat, engine.now()), key)
        pending = request
        engine.pairingFlow.value = PairingState.Confirm(hello.name, hello.id, code)
        engine.scope.launch {
            delay(timing.acceptWaitMs)
            if (pending === request) end(request, "expired")
        }
    }

    private fun pairRefusal(fromId: String): String? = engine.lock.withLock {
        val now = engine.now()
        val current = pending
        val last = lastPairRequestAt[fromId]
        when {
            store.isBlocked(fromId) -> "blocked"
            current != null && (current.device.id != fromId || current.offer != null) -> "busy"
            last != null && now - last < timing.pairRequestGapMs ->
                "wait:${(timing.pairRequestGapMs - (now - last) + 999) / 1000}"

            else -> {
                lastPairRequestAt[fromId] = now
                null
            }
        }
    }

    fun startPairing(deviceId: String) {
        val device = engine.discovery.find(deviceId) ?: return
        connectAndPair(device.host, device.port, device.name)
    }

    fun connectAndPair(host: String, port: Int, displayName: String) {
        engine.pairingFlow.value = PairingState.Connecting(displayName)
        engine.scope.launch {
            try {
                val connection = engine.platform.network.connect(host, port, timing.connectMs)
                    ?: throw IllegalStateException("could not reach the device")
                try {
                    exchangeHello(connection, host, port)
                } finally {
                    connection.closeQuietly()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                engine.pairingFlow.value = PairingState.Failed(displayName, t.message ?: "failed")
            }
        }
    }

    private suspend fun exchangeHello(connection: SyncConnection, host: String, port: Int) {
        val keys = crypto.newKeyPair()
        try {
            val pubA = keys.publicSpki
            connection.writeFrame(T_HELLO, Hello(engine.selfId, engine.selfName, engine.config.platformName, Base64.encode(pubA)).toBytes())
            connection.flush()
            val (type, body) = connection.readFrame(timing.replyMs) ?: throw IllegalStateException("no reply")
            if (type == T_ERR) throw IllegalStateException(body.decodeToString())
            if (type != T_HELLO_ACK) throw IllegalStateException("bad reply")
            val ack = helloOf(body) ?: throw IllegalStateException("bad reply")
            val pubB = Base64.decode(ack.pub)
            val key = keys.derive(pubB)
            val code = pairingCode(pubA, pubB)
            engine.discovery.remember(DiscoveredDevice(ack.id, ack.name, ack.plat, host, port, store.isPaired(ack.id)))
            pending = SyncPending(PairedDevice(ack.id, ack.name, ack.plat, engine.now()), key)
            engine.pairingFlow.value = PairingState.Confirm(ack.name, ack.id, code)
        } finally {
            keys.close()
        }
    }

    fun confirm() {
        val request = pending ?: return
        val tag = request.offer
        if (tag != null) {
            engine.log("send offer from ${request.device.name} accepted")
            val token = engine.lock.withLock {
                acceptedOffers[tag] = engine.now()
                if (request.offerHasFiles) engine.receiver.newAcceptedToken(tag) else 0L
            }
            pending = null
            if (request.offerHasFiles) engine.receiver.awaitAcceptedStream(request, tag, token)
            engine.pairingFlow.value = null
            return
        }
        store.addPairing(request.device, request.key)
        pending = null
        engine.pairingFlow.value = null
        engine.publishPaired()
        engine.discovery.publish()
    }

    fun cancel() {
        val request = pending
        if (request == null) {
            engine.pairingFlow.value = null
        } else {
            end(request, if (request.offer != null) "no-answer" else "declined")
        }
    }

    fun block() {
        val request = pending ?: return
        store.block(request.device)
        pending = null
        engine.pairingFlow.value = null
        engine.blockedFlow.value = store.blockedDevices()
        engine.publishPaired()
        engine.discovery.publish()
    }

    fun unblock(deviceId: String) {
        store.unblock(deviceId)
        engine.blockedFlow.value = store.blockedDevices()
        engine.discovery.publish()
    }

    private fun end(request: SyncPending, why: String) {
        engine.log("pairing code for ${request.device.name} closed: $why")
        engine.lock.withLock { endedPairings[request.offer ?: request.device.id] = engine.now() to why }
        pending = null
        engine.pairingFlow.value = null
    }

    private fun endedReason(id: String): String? = engine.lock.withLock {
        val ended = endedPairings[id]
        if (ended != null && engine.now() - ended.first < timing.endedMs) ended.second else null
    }

    fun unpairedSendRefusal(fromId: String): String = when {
        store.isBlocked(fromId) -> "blocked"
        pending.let { it != null && it.device.id == fromId && it.offer == null } -> "pending"
        else -> endedReason(fromId) ?: "not paired"
    }

    fun offerRefusal(fromId: String, key: ByteArray, meta: SendMeta): String? {
        val payload = meta.payload
        val tag = offerTag(fromId, meta)
        val fileName = offerFileName(payload)
        val size = meta.files.filterNot { it.name.endsWith(".chunks") }.sumOf { it.size }
        val (refusal, offer) = engine.lock.withLock {
            val now = engine.now()
            val accepted = acceptedOffers.remove(tag)
            val current = pending
            val ended = endedPairings[tag]
            when {
                accepted != null && now - accepted < timing.endedMs -> null to null
                current != null && current.offer == tag -> "pending" to null
                ended != null && now - ended.first < timing.endedMs -> {
                    endedPairings.remove(tag)
                    ended.second to null
                }

                current != null -> "busy" to null
                else -> {
                    val known = store.pairedDevices().firstOrNull { it.id == fromId }
                    val name = known?.name ?: payload.fromDeviceName.ifBlank { fromId }
                    val asked = SyncPending(
                        PairedDevice(fromId, name, known?.platform ?: "", now), key, tag,
                        offerFile = fileName,
                        offerBytes = meta.files.sumOf { it.size.coerceAtLeast(0L) },
                        offerHasFiles = meta.files.isNotEmpty(),
                    )
                    pending = asked
                    "pending" to asked
                }
            }
        }
        if (offer == null) return refusal
        engine.log("send offer from ${offer.device.name}: $fileName")
        engine.pairingFlow.value = PairingState.Offer(offer.device.name, fromId, fileName, size)
        engine.scope.launch {
            delay(timing.offerWaitMs)
            if (pending === offer) end(offer, "no-answer")
        }
        return "pending"
    }

    fun forgetAcceptedOffer(tag: String) {
        engine.lock.withLock { acceptedOffers.remove(tag) }
    }

    fun unpair(deviceId: String) {
        val key = store.keyFor(deviceId)
        val device = engine.discovery.find(deviceId)
        engine.forgetPairing(deviceId)
        if (key == null || device == null || device.host.isBlank() || device.port <= 0) return
        engine.scope.launch {
            val connection = engine.platform.network.connect(device.host, device.port, timing.unpairConnectMs) ?: return@launch
            try {
                connection.writeFrame(T_UNPAIR, idAndSealed(engine.selfId, crypto.seal(key, "unpair".encodeToByteArray())))
                connection.flush()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            } finally {
                connection.closeQuietly()
            }
        }
    }

    fun onUnpair(body: ByteArray) {
        val (fromId, sealed) = splitIdAndSealed(body) ?: return
        val key = store.keyFor(fromId) ?: return
        val genuine = try {
            crypto.open(key, sealed).decodeToString() == "unpair"
        } catch (_: Throwable) {
            false
        }
        if (genuine) {
            engine.log("unpaired by $fromId")
            engine.forgetPairing(fromId)
        }
    }

    private fun pairingCode(pubA: ByteArray, pubB: ByteArray): String {
        val ordered = if (compareBytes(pubA, pubB) <= 0) pubA + pubB else pubB + pubA
        val hash = crypto.sha256(ordered)
        val number = ((hash[0].toInt() and 0xff) shl 16) or ((hash[1].toInt() and 0xff) shl 8) or
            (hash[2].toInt() and 0xff)
        return (number % 1_000_000).toString().padStart(6, '0')
    }
}

internal fun offerTag(fromId: String, meta: SendMeta): String =
    "$fromId|${meta.payload.url}|${meta.payload.fileName}|${meta.mode}"

private fun compareBytes(a: ByteArray, b: ByteArray): Int {
    val shared = minOf(a.size, b.size)
    for (i in 0 until shared) {
        val difference = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
        if (difference != 0) return difference
    }
    return a.size - b.size
}
