import com.michelle.raouf.fetch_jet.feature.device_sync.MemoryDeviceStore
import com.michelle.raouf.fetch_jet.feature.device_sync.PairingState
import com.michelle.raouf.fetch_jet.feature.device_sync.SendMode
import com.michelle.raouf.fetch_jet.feature.device_sync.SendPayload
import com.michelle.raouf.fetch_jet.feature.device_sync.SendRequest
import com.michelle.raouf.fetch_jet.feature.device_sync.winDeviceSync
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.posix.fflush

@OptIn(ExperimentalForeignApi::class)
private fun out(line: String) {
    println(line)
    fflush(null)
}

private fun pairingText(state: PairingState?): String = when (state) {
    is PairingState.Confirm -> "confirm:${state.code}:${state.peerId}"
    is PairingState.Failed -> "failed:${state.reason}"
    is PairingState.Connecting -> "connecting"
    is PairingState.Offer -> "offer:${state.fileName}:${state.size}:${state.peerId}"
    null -> "none"
}

fun deviceSyncPeer(work: String, args: List<String>) = runBlocking {
    val opts = args.mapNotNull { arg -> arg.indexOf('=').takeIf { it > 0 }?.let { arg.substring(0, it) to arg.substring(it + 1) } }.toMap()
    val sync = winDeviceSync(
        platformName = "native peer · Windows",
        tempDir = "$work\\syncpeer",
        store = MemoryDeviceStore(),
        onReceive = { payload, files -> out("RECV\t${payload.url}\t${files.joinToString("|")}") },
        discoveryPort = opts["udp"]?.toIntOrNull() ?: 28491,
        tcpPort = opts["tcp"]?.toIntOrNull() ?: 28492,
        bindHost = opts["host"] ?: "127.0.0.1",
        sendBroadcast = false,
    )
    sync.start()
    out("PEER\t${sync.selfId}")
    val watcher = launch(Dispatchers.IO) {
        var last = ""
        while (true) {
            val now = pairingText(sync.pairing.value)
            if (now != last) {
                last = now
                out("PSTATE\t$now")
            }
            delay(20)
        }
    }
    val report: (Boolean) -> Unit = { ok -> out("SENT\t$ok\t${sync.sending.value}") }
    while (true) {
        val line = withContext(Dispatchers.IO) { readlnOrNull() } ?: break
        val parts = line.trim().split('\t')
        when (parts[0]) {
            "pair" -> sync.startPairingWithHost(parts[1])
            "confirm" -> sync.confirmPairing()
            "cancel" -> sync.cancelPairing()
            "link" -> sync.send(SendRequest(parts[1], SendPayload(url = parts[2], fileName = parts.getOrElse(3) { "link.bin" }), SendMode.LINK_ONLY, false), report)
            "finished" -> sync.send(
                SendRequest(
                    parts[1],
                    SendPayload(url = parts[2], fileName = parts[4], totalBytes = parts[5].toLong(), downloadedBytes = parts[5].toLong()),
                    SendMode.TRANSFER_AND_CONTINUE, false,
                    completedFilePath = parts[3]
                ), report
            )
            "state" -> out("STATE\t${pairingText(sync.pairing.value)}\t${sync.paired.value.joinToString(",") { it.id }}\t${sync.sending.value}")
            "quit" -> break
        }
    }
    watcher.cancel()
    sync.shutdown()
}
