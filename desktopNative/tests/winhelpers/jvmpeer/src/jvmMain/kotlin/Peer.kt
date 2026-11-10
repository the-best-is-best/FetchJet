import com.michelle.raouf.fetch_jet.feature.device_sync.PairingState
import com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState
import com.michelle.raouf.fetch_jet.feature.device_sync.SendMode
import com.michelle.raouf.fetch_jet.feature.device_sync.SendPayload
import com.michelle.raouf.fetch_jet.feature.device_sync.SendRequest
import com.michelle.raouf.fetch_jet.feature.device_sync.createDeviceSync
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

fun main() {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
    val sync = createDeviceSync(System.getProperty("fj.dir")!!, { "" }) { payload, files ->
        println("RECV\t${payload.url}\t${payload.fromDeviceId}\t${files.joinToString("|")}")
    }
    sync.setVisible(true)
    sync.start()
    val autoAccept = java.util.concurrent.atomic.AtomicBoolean(true)
    Thread {
        while (true) {
            if (autoAccept.get() && sync.pairing.value is PairingState.Offer) sync.confirmPairing()
            Thread.sleep(25)
        }
    }.apply { isDaemon = true }.start()
    Thread {
        var last: ReceiveState? = null
        while (true) {
            val now = sync.receiving.value
            if (now != last) {
                last = now
                val text = when (now) {
                    is ReceiveState.Receiving -> "receiving:${now.fraction}:${now.fileName}"
                    is ReceiveState.Done -> "done:${now.fileName}"
                    is ReceiveState.Failed -> "failed:${now.reason}"
                    null -> "none"
                }
                println("RSTATE\t$text")
            }
            Thread.sleep(2)
        }
    }.apply { isDaemon = true }.start()
    println("PEER\t${sync.selfId}")
    val input = System.`in`.bufferedReader(Charsets.UTF_8)
    while (true) {
        val line = input.readLine() ?: break
        val parts = line.split('\t')
        val report: (Boolean) -> Unit = { ok -> println("SENT\t$ok\t${sync.sending.value}") }
        when (parts[0]) {
            "pair" -> sync.startPairingWithHost(parts[1])
            "confirm" -> sync.confirmPairing()
            "cancel" -> sync.cancelPairing()
            "autoaccept" -> autoAccept.set(parts[1] == "on")
            "unpair" -> sync.unpair(parts[1])
            "link" -> sync.send(SendRequest(parts[1], SendPayload(url = parts[2], fileName = "x.bin"), SendMode.LINK_ONLY, false), report)
            "files" -> sync.send(
                SendRequest(
                    parts[1], SendPayload(url = parts[2], fileName = "big.bin"), SendMode.TRANSFER_AND_CONTINUE, false,
                    partialFiles = parts[3].split('|')
                ), report
            )
            "finished" -> sync.send(
                SendRequest(
                    parts[1],
                    SendPayload(
                        url = parts[2], fileName = parts[4],
                        totalBytes = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
                        downloadedBytes = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                    ),
                    SendMode.TRANSFER_AND_CONTINUE, false,
                    completedFilePath = parts[3]
                ), report
            )
            "media" -> sync.send(
                SendRequest(
                    parts[1],
                    SendPayload(url = parts[2], fileName = "clip.mp4", engineType = "MEDIA_STREAM", formatId = "137+140", originalUrl = parts[3]),
                    SendMode.LINK_ONLY, false
                ), report
            )
            "state" -> {
                val pairing = when (val state = sync.pairing.value) {
                    is PairingState.Confirm -> "confirm:${state.code}:${state.peerId}"
                    is PairingState.Failed -> "failed:${state.reason}"
                    is PairingState.Connecting -> "connecting"
                    is PairingState.Offer -> "offer:${state.fileName}:${state.size}:${state.peerId}"
                    null -> "none"
                }
                val paired = sync.paired.value.joinToString(",") { it.id }
                val seen = sync.discovered.value.joinToString(",") { "${it.id}@${it.host}:${it.port}" }
                println("STATE\t$pairing\t$paired\t$seen\t${sync.sending.value}")
            }
            "quit" -> break
        }
    }
    sync.stop()
    exitProcess(0)
}
