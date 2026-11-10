package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class SyncReceiver(private val engine: DeviceSyncEngine) {
    private val acceptedTokens = HashMap<String, Long>()
    private var receiveCounter = 0L

    @Volatile
    private var receiveToken = 0L

    @Volatile
    private var hiddenReceive = -1L

    private val files get() = engine.platform.files
    private val crypto get() = engine.platform.crypto
    private val receiveRoot: String by lazy { files.absolute(engine.config.tempDir) }

    suspend fun handle(connection: SyncConnection) {
        val (type, body) = connection.readFrame(engine.timing.firstFrameMs) ?: return
        when (type) {
            T_HELLO -> engine.pairings.onHello(body, connection)
            T_SEND_META -> onSend(body, connection)
            T_UNPAIR -> engine.pairings.onUnpair(body)
            else -> connection.writeError("unknown")
        }
        connection.flush()
    }

    private suspend fun onSend(body: ByteArray, connection: SyncConnection) {
        val (fromId, sealed) = splitIdAndSealed(body) ?: return
        val key = engine.store.keyFor(fromId)
        if (key == null) {
            val reason = engine.pairings.unpairedSendRefusal(fromId)
            if (reason != "pending") engine.log("send from $fromId refused: $reason")
            connection.writeError(reason)
            return
        }
        val metaJson = try {
            crypto.open(key, sealed).decodeToString()
        } catch (_: Throwable) {
            connection.writeError("bad key")
            return
        }
        val meta = sendMetaOf(metaJson) ?: run {
            connection.writeError("bad meta")
            return
        }
        val refusal = engine.pairings.offerRefusal(fromId, key, meta)
        if (refusal != null) {
            if (refusal != "pending") engine.log("send from $fromId refused: $refusal")
            connection.writeError(refusal)
            connection.flush()
            return
        }
        connection.writeFrame(T_READY)
        connection.flush()
        receive(fromId, key, meta, connection)
    }

    private suspend fun receive(fromId: String, key: ByteArray, meta: SendMeta, connection: SyncConnection) {
        val dir = files.child(receiveRoot, "recv-${engine.now()}")
        val written = ArrayList<String>()
        val showName = senderName(fromId, meta.payload)
        val shownFile = offerFileName(meta.payload)
        val withBytes = meta.files.isNotEmpty()
        val token = if (withBytes) receiveTokenFor(offerTag(fromId, meta)) else 0L
        val total = meta.files.sumOf { it.size.coerceAtLeast(0L) }
        var received = 0L
        var shownPermille = -1
        fun progress(): Float = if (total > 0L) (received.toFloat() / total).coerceIn(0f, 1f) else -1f
        if (withBytes) show(token, ReceiveState.Receiving(showName, shownFile, progress()))
        try {
            if (withBytes) files.createDirectories(dir)
            for (file in meta.files) {
                val name = safeWireName(file.name) ?: throw IllegalStateException("bad file name")
                val path = files.child(dir, name)
                receiveFile(path, key, connection) { count ->
                    received += count
                    val permille = if (total > 0L) (progress() * 1000).toInt() else -1
                    if (permille != shownPermille) {
                        shownPermille = permille
                        show(token, ReceiveState.Receiving(showName, shownFile, progress()))
                    }
                }
                written.add(path)
            }
            engine.onReceive(meta.payload.copy(fromDeviceId = fromId), written)
            if (withBytes) show(token, ReceiveState.Done(showName, shownFile))
        } catch (e: CancellationException) {
            if (withBytes) files.deleteRecursively(dir)
            throw e
        } catch (t: Throwable) {
            if (withBytes) {
                files.deleteRecursively(dir)
                show(token, ReceiveState.Failed(showName, shownFile, t.message ?: "error"))
            }
            try {
                connection.writeError(t.message ?: "error")
                connection.flush()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
            return
        }
        connection.writeFrame(T_ACK)
        connection.flush()
    }

    private suspend fun receiveFile(path: String, key: ByteArray, connection: SyncConnection, onBytes: (Int) -> Unit) {
        val writer = files.writer(path)
        try {
            while (true) {
                val (type, chunk) = connection.readFrame(engine.timing.idleMs)
                    ?: throw IllegalStateException("stream ended")
                if (type == T_FILE_END) break
                if (type != T_FILE_CHUNK) throw IllegalStateException("unexpected frame")
                val plain = try {
                    crypto.open(key, chunk)
                } catch (_: Throwable) {
                    throw IllegalStateException("bad chunk")
                }
                writer.write(plain)
                onBytes(plain.size)
            }
        } finally {
            writer.close()
        }
    }

    private fun senderName(fromId: String, payload: SendPayload): String =
        engine.store.pairedDevices().firstOrNull { it.id == fromId }?.name ?: payload.fromDeviceName.ifBlank { fromId }

    private fun receiveTokenFor(tag: String): Long {
        val token = engine.lock.withLock { acceptedTokens.remove(tag) ?: ++receiveCounter }
        receiveToken = token
        return token
    }

    fun newAcceptedToken(tag: String): Long = engine.lock.withLock {
        (++receiveCounter).also { acceptedTokens[tag] = it }
    }

    fun awaitAcceptedStream(request: SyncPending, tag: String, token: Long) {
        receiveToken = token
        val fraction = if (request.offerBytes > 0L) 0f else -1f
        show(token, ReceiveState.Receiving(request.device.name, request.offerFile, fraction))
        engine.scope.launch {
            delay(engine.timing.receiveStartMs)
            val unused = engine.lock.withLock {
                (acceptedTokens[tag] == token).also {
                    if (it) {
                        acceptedTokens.remove(tag)
                        engine.pairings.forgetAcceptedOffer(tag)
                    }
                }
            }
            if (unused) show(token, ReceiveState.Failed(request.device.name, request.offerFile, "no-answer"))
        }
    }

    private fun show(token: Long, state: ReceiveState) {
        if (token == receiveToken && token != hiddenReceive) engine.receivingFlow.value = state
    }

    fun clear() {
        hiddenReceive = receiveToken
        engine.receivingFlow.value = null
    }
}
