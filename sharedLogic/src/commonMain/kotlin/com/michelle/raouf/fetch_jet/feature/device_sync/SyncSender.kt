package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class OutgoingFile(val wireName: String, val path: String?, val inlineBytes: ByteArray?, private val files: SyncFiles) {
    val size: Long get() = inlineBytes?.size?.toLong() ?: path?.let { files.size(it) } ?: 0L
}

private sealed interface SendOutcome {
    object Success : SendOutcome
    object WaitAccept : SendOutcome
    object NotPaired : SendOutcome
    class Fail(val reason: String) : SendOutcome
}

internal class SyncSender(private val engine: DeviceSyncEngine) {
    @Volatile
    private var cancelled = false

    private val timing get() = engine.timing
    private val files get() = engine.platform.files
    private val crypto get() = engine.platform.crypto

    fun cancel() {
        cancelled = true
    }

    fun send(request: SendRequest, onResult: (Boolean) -> Unit) {
        val device = engine.discovery.find(request.deviceId)
            ?: engine.paired.value.firstOrNull { it.id == request.deviceId }?.let {
                DiscoveredDevice(it.id, it.name, it.platform, "", 0, true)
            }
        val host = device?.host
        val port = device?.port ?: 0
        val name = device?.name ?: request.deviceId
        val key = engine.store.keyFor(request.deviceId)
        if (host.isNullOrBlank() || port == 0 || key == null) {
            engine.sendingFlow.value = SendState.Failed(name, "device offline or not paired")
            onResult(false)
            return
        }
        cancelled = false
        engine.sendingFlow.value = SendState.Sending(name, 0f)
        engine.scope.launch {
            val deadline = engine.now() + timing.acceptWaitMs + timing.offerWaitMs
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
                        engine.sendingFlow.value = SendState.Done(name)
                        onResult(true)
                        return@launch
                    }

                    SendOutcome.WaitAccept -> {
                        if (engine.now() >= deadline) {
                            engine.forgetPairing(request.deviceId)
                            engine.sendingFlow.value = SendState.Failed(name, "expired")
                            onResult(false)
                            return@launch
                        }
                        engine.sendingFlow.value = SendState.Waiting(name)
                        delay(timing.waitRetryMs)
                        if (cancelled) {
                            engine.sendingFlow.value = null
                            onResult(false)
                            return@launch
                        }
                    }

                    SendOutcome.NotPaired -> {
                        engine.forgetPairing(request.deviceId)
                        engine.sendingFlow.value = SendState.Failed(
                            name, "the other device unpaired this one; send again to pair"
                        )
                        onResult(false)
                        return@launch
                    }

                    is SendOutcome.Fail -> {
                        if (outcome.reason in setOf("blocked", "declined", "expired")) {
                            engine.forgetPairing(request.deviceId)
                        }
                        engine.sendingFlow.value = SendState.Failed(name, outcome.reason)
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
            request.partialFiles.filter { files.size(it) != null }
                .map { OutgoingFile(files.name(it), it, null, files) }

        request.completedFilePath.isNotBlank() && files.size(request.completedFilePath) != null -> {
            val finished = request.completedFilePath
            val base = request.payload.fileName.ifBlank { files.name(finished) }
            val manifest = "0 0 ${((files.size(finished) ?: 0L) - 1).coerceAtLeast(0)}".encodeToByteArray()
            listOf(
                OutgoingFile("$base.chunks", null, manifest, files),
                OutgoingFile("$base.part0", finished, null, files)
            )
        }

        else -> emptyList()
    }

    private suspend fun trySendOnce(host: String, port: Int, key: ByteArray, request: SendRequest, name: String): SendOutcome {
        val connection = engine.platform.network.connect(host, port, timing.connectMs)
            ?: return SendOutcome.Fail("could not reach the device")
        try {
            val outgoing = outgoingFiles(request)
            val meta = SendMeta(
                payload = request.payload,
                mode = request.mode.name,
                files = outgoing.map { FileMeta(it.wireName, it.size) }
            )
            connection.writeFrame(T_SEND_META, idAndSealed(engine.selfId, crypto.seal(key, meta.toBytes())))
            connection.flush()
            val (reply, replyBody) = connection.readFrame(timing.replyMs) ?: return SendOutcome.Fail("no reply")
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
            fun progress(count: Int) {
                sent += count
                engine.sendingFlow.value = SendState.Sending(name, (sent.toFloat() / total).coerceIn(0f, 1f))
            }
            for (file in outgoing) {
                val inline = file.inlineBytes
                val path = file.path
                if (inline != null) {
                    connection.writeFrame(T_FILE_CHUNK, crypto.seal(key, inline))
                    progress(inline.size)
                } else if (path != null) {
                    val reader = files.reader(path, SYNC_CHUNK)
                    try {
                        while (true) {
                            val part = reader.next() ?: break
                            connection.writeFrame(T_FILE_CHUNK, crypto.seal(key, part))
                            connection.flush()
                            progress(part.size)
                        }
                    } finally {
                        reader.close()
                    }
                }
                connection.writeFrame(T_FILE_END)
            }
            connection.flush()
            val (type, _) = connection.readFrame(timing.idleMs) ?: return SendOutcome.Fail("no ack")
            return if (type == T_ACK) SendOutcome.Success else SendOutcome.Fail("receiver rejected")
        } finally {
            connection.closeQuietly()
        }
    }
}
