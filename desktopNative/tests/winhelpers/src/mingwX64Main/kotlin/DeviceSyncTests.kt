import com.michelle.raouf.fetch_jet.feature.device_sync.DeviceSyncTiming
import com.michelle.raouf.fetch_jet.feature.device_sync.MemoryDeviceStore
import com.michelle.raouf.fetch_jet.feature.device_sync.PairingState
import com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState
import com.michelle.raouf.fetch_jet.feature.device_sync.SendMode
import com.michelle.raouf.fetch_jet.feature.device_sync.SendPayload
import com.michelle.raouf.fetch_jet.feature.device_sync.SendRequest
import com.michelle.raouf.fetch_jet.feature.device_sync.SendState
import com.michelle.raouf.fetch_jet.feature.device_sync.winDeviceSync
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.runProcess
import com.michelle.raouf.fetch_jet.platform.win.WinLock
import com.michelle.raouf.fetch_jet.platform.win.winAesGcmSeal
import com.michelle.raouf.fetch_jet.platform.win.winBroadcastAddresses
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winHostName
import com.michelle.raouf.fetch_jet.platform.win.winIpv4Interfaces
import com.michelle.raouf.fetch_jet.platform.win.winList
import com.michelle.raouf.fetch_jet.platform.win.winRandomBytes
import com.michelle.raouf.fetch_jet.platform.win.winReadFile
import com.michelle.raouf.fetch_jet.platform.win.winSetEnv
import com.michelle.raouf.fetch_jet.platform.win.winSha256OfFile
import com.michelle.raouf.fetch_jet.platform.win.winUdpIgnoreUnreachable
import com.michelle.raouf.fetch_jet.platform.win.winWriteFile
import io.ktor.network.selector.Selectable
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ASocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
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
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer

private const val SYNC_UDP = 28481
private const val SYNC_TCP = 28482
private const val HOST_A = "127.0.0.1"
private const val HOST_B = "127.0.0.2"
private const val HOST_C = "127.0.0.3"
private const val HOST_D = "127.0.0.4"
private const val REAL_UDP = 28461
private const val REAL_TCP = 28462

private val syncTiming = DeviceSyncTiming(
    announceEveryMs = 300,
    deviceExpireMs = 1500,
    acceptWaitMs = 4000,
    offerWaitMs = 2500,
    pairRequestGapMs = 1500,
    firstFrameMs = 1500,
    receiveStartMs = 2000,
)

private class SyncNode(val label: String, val host: String, work: String) {
    val store = MemoryDeviceStore()
    val tempDir = "$work\\$label"
    private val lock = WinLock()
    private val received = ArrayList<Pair<SendPayload, List<String>>>()
    val sync = winDeviceSync(
        platformName = "$label · Windows",
        tempDir = tempDir,
        store = store,
        onReceive = { payload, files -> lock.withLock { received.add(payload to files) } },
        discoveryPort = SYNC_UDP,
        tcpPort = SYNC_TCP,
        bindHost = host,
        sendBroadcast = false,
        timing = syncTiming,
    )

    fun receivedCount(): Int = lock.withLock { received.size }
    fun last(): Pair<SendPayload, List<String>>? = lock.withLock { received.lastOrNull() }
    fun sees(other: SyncNode, port: Int? = null): Boolean = sync.discovered.value.any {
        it.id == other.sync.selfId && it.host == other.host && (port == null || it.port == port)
    }
    fun pairedWith(other: SyncNode): Boolean = sync.paired.value.any { it.id == other.sync.selfId }
    fun code(): String? = (sync.pairing.value as? PairingState.Confirm)?.code
    fun failedReason(): String? = (sync.pairing.value as? PairingState.Failed)?.reason
    fun offer(): PairingState.Offer? = sync.pairing.value as? PairingState.Offer
}

private suspend fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
    val mark = TimeSource.Monotonic.markNow()
    while (mark.elapsedNow().inWholeMilliseconds < timeoutMs) {
        if (condition()) return true
        delay(25)
    }
    return condition()
}

private class SendResult {
    val state = AtomicInt(0)
    val done: (Boolean) -> Unit = { ok -> state.value = if (ok) 1 else 2 }
}

private suspend fun sendAndWait(from: SyncNode, to: SyncNode, request: SendRequest, timeoutMs: Long = 15_000): Int {
    val result = SendResult()
    from.sync.send(request, result.done)
    waitFor(timeoutMs) {
        if (to.offer()?.peerId == from.sync.selfId) to.sync.confirmPairing()
        result.state.value != 0
    }
    return result.state.value
}

private fun linkRequest(to: SyncNode, url: String) =
    SendRequest(to.sync.selfId, SendPayload(url = url, fileName = "x.bin"), SendMode.LINK_ONLY, false)

private fun sendFailure(node: SyncNode): String? = (node.sync.sending.value as? SendState.Failed)?.reason

private suspend fun ownSyncSockets(): List<String> {
    val script = "Get-NetTCPConnection -LocalAddress $HOST_D -ErrorAction SilentlyContinue | ForEach-Object { 'TCP ' + \$_.LocalAddress + ':' + \$_.LocalPort }; " +
        "Get-NetUDPEndpoint -LocalAddress $HOST_D -ErrorAction SilentlyContinue | ForEach-Object { 'UDP ' + \$_.LocalAddress + ':' + \$_.LocalPort }"
    val out = DesktopOs.runProcess(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script), timeoutMs = 30_000).stdout
    return out.lines().map { it.trim() }.filter { it.startsWith("TCP ") || it.startsWith("UDP ") }.distinct().sorted()
}

private suspend fun realPortChecks(root: String) {
    val d = winDeviceSync(
        platformName = "d · Windows",
        tempDir = "$root\\d",
        store = MemoryDeviceStore(),
        onReceive = { _, _ -> },
        bindHost = HOST_D,
        sendBroadcast = false,
        timing = syncTiming,
    )
    try {
        d.setVisible(false)
        d.start()
        delay(1500)
        val idle = ownSyncSockets()
        T.check("visibility off, no send: nothing listens on $REAL_UDP/$REAL_TCP", idle.isEmpty() && !d.discoveryRunning && d.listeningTcpPort == 0, "$idle")
        d.setActive(true)
        val running = waitFor(3000) { d.discoveryRunning }
        delay(300)
        val sheet = ownSyncSockets()
        T.check("share sheet open: discovery port $REAL_UDP bound, no TCP $REAL_TCP", running && "UDP $HOST_D:$REAL_UDP" in sheet && sheet.none { it.startsWith("TCP ") } && d.listeningTcpPort == 0, "$sheet")
        d.setActive(false)
        val closed = waitFor(3000) { !d.discoveryRunning } && ownSyncSockets().isEmpty()
        T.check("share sheet closed: port $REAL_UDP released", closed, "${ownSyncSockets()}")
        d.setVisible(true)
        val on = waitFor(3000) { d.listeningTcpPort == REAL_TCP && d.discoveryRunning }
        delay(300)
        val sockets = ownSyncSockets()
        T.check("visibility on: TCP $REAL_TCP listens and UDP $REAL_UDP is bound", on && "TCP $HOST_D:$REAL_TCP" in sockets && "UDP $HOST_D:$REAL_UDP" in sockets, "port=${d.listeningTcpPort} $sockets")
        d.setVisible(false)
        val off = waitFor(3000) { d.listeningTcpPort == 0 && !d.discoveryRunning } && ownSyncSockets().isEmpty()
        T.check("visibility off again: nothing listens", off, "${ownSyncSockets()}")
    } finally {
        d.shutdown()
    }
}

private fun intBytes(value: Int): ByteArray = byteArrayOf(
    ((value ushr 24) and 0xff).toByte(), ((value ushr 16) and 0xff).toByte(),
    ((value ushr 8) and 0xff).toByte(), (value and 0xff).toByte()
)

private suspend fun rawWrite(output: ByteWriteChannel, type: Int, body: ByteArray, length: Int = body.size) {
    output.writeByte(type.toByte())
    output.writeInt(length)
    output.writeFully(body)
    output.flush()
}

private suspend fun rawRead(input: ByteReadChannel, timeoutMs: Long): Pair<Int, ByteArray>? = withTimeoutOrNull(timeoutMs) {
    try {
        val type = input.readByte().toInt()
        val length = input.readInt()
        type to (if (length == 0) ByteArray(0) else input.readByteArray(length))
    } catch (_: Throwable) {
        null
    }
}

private suspend fun rawClosed(input: ByteReadChannel, timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
    try {
        input.readByte()
        false
    } catch (_: Throwable) {
        true
    }
} ?: false

private fun recvDirs(node: SyncNode): List<String> =
    (winList(node.tempDir) ?: emptyList()).filter { it.startsWith("recv-") }

private fun same(a: String, b: String): Boolean {
    val x = winSha256OfFile(a) ?: return false
    val y = winSha256OfFile(b) ?: return false
    return x.contentEquals(y)
}

private fun closeQuiet(socket: ASocket?) {
    try {
        socket?.close()
    } catch (_: Throwable) {
    }
}

fun deviceSyncTests(work: String, args: List<String>) = runBlocking {
    winSetEnv("FETCHJET_SYNC_NO_BROADCAST", "1")
    val opts = args.mapNotNull { arg -> arg.indexOf('=').takeIf { it > 0 }?.let { arg.substring(0, it) to arg.substring(it + 1) } }.toMap()
    val root = "$work\\sync"
    winDeleteRecursively(root)
    winCreateDirectories(root)

    val hostName = winHostName()
    T.info("winHostName=$hostName")
    opts["host"]?.let { T.check("winHostName equals .NET Dns.GetHostName", hostName.equals(it, ignoreCase = true), "$hostName vs $it") }
    T.info("interfaces=" + winIpv4Interfaces().joinToString { "${it.address}/${it.prefixBits} up=${it.up} lo=${it.loopback}" })
    T.info("broadcast=" + winBroadcastAddresses().joinToString())
    T.check("interface list contains 127.0.0.1", winIpv4Interfaces().any { it.address == "127.0.0.1" && it.loopback })

    val selector = SelectorManager(Dispatchers.IO)
    val blocker = aSocket(selector).tcp().bind(HOST_C, SYNC_TCP)

    val a = SyncNode("a", HOST_A, root)
    val b = SyncNode("b", HOST_B, root)
    val c = SyncNode("c", HOST_C, root)
    for (node in listOf(a, b, c)) {
        node.sync.setVisible(true)
        node.sync.start()
    }

    try {
        T.check("A listens on its TCP port", waitFor(3000) { a.sync.listeningTcpPort == SYNC_TCP }, "${a.sync.listeningTcpPort}")
        T.check("B listens on 127.0.0.2 with the same port", waitFor(3000) { b.sync.listeningTcpPort == SYNC_TCP }, "${b.sync.listeningTcpPort}")
        T.check("C falls back to another TCP port when its port is taken", waitFor(3000) { c.sync.listeningTcpPort > 0 } && c.sync.listeningTcpPort != SYNC_TCP, "${c.sync.listeningTcpPort}")
        closeQuiet(blocker)
        val cPort = c.sync.listeningTcpPort

        a.sync.startPairingWithHost(HOST_B)
        T.check("pair by IP: both show a code", waitFor(5000) { a.code() != null && b.code() != null })
        T.check("pair by IP: the codes are equal and 6 digits", a.code() == b.code() && a.code()?.matches(Regex("\\d{6}")) == true, "${a.code()} / ${b.code()}")
        a.sync.confirmPairing()
        b.sync.confirmPairing()
        T.check("pair by IP: both store the pairing", waitFor(2000) { a.pairedWith(b) && b.pairedWith(a) })
        T.check("pair by IP: pairing state cleared", a.sync.pairing.value == null && b.sync.pairing.value == null)
        T.check("A lists B (from the pairing reply)", waitFor(3000) { a.sees(b, SYNC_TCP) })
        T.check("B lists A through direct announces (no broadcast)", waitFor(3000) { b.sees(a, SYNC_TCP) })
        T.check("discovered entries say paired", waitFor(2000) { a.sync.discovered.value.firstOrNull { it.id == b.sync.selfId }?.paired == true })
        T.check("names come through", a.sync.discovered.value.firstOrNull { it.id == b.sync.selfId }?.platform == "b · Windows")

        var before = b.receivedCount()
        val link = SendRequest(b.sync.selfId, SendPayload(url = "https://example.invalid/a.bin", fileName = "a.bin", cookie = "k=v"), SendMode.LINK_ONLY, false)
        T.check("link only A->B: onResult true", sendAndWait(a, b, link) == 1, "${a.sync.sending.value}")
        T.check("link only A->B: sending state Done", a.sync.sending.value is SendState.Done)
        val linkGot = b.last()
        T.check(
            "link only A->B: payload arrives with sender id, no files",
            b.receivedCount() == before + 1 && linkGot?.first?.url == "https://example.invalid/a.bin" &&
                linkGot.first.cookie == "k=v" && linkGot.first.fromDeviceId == a.sync.selfId && linkGot.second.isEmpty()
        )

        val src = "$root\\src"
        winCreateDirectories(src)
        val pieces = listOf(
            "$src\\big.bin.part0" to winRandomBytes(200_000),
            "$src\\big.bin.part1" to ByteArray(0),
            "$src\\big.bin.part2" to winRandomBytes(65536),
            "$src\\ملف عربي.part3" to winRandomBytes(1000),
            "$src\\big.bin.chunks" to "0 0 199999\n1 200000 200000\n".encodeToByteArray(),
        )
        for ((path, bytes) in pieces) winWriteFile(path, bytes)
        before = b.receivedCount()
        val scratch = SendRequest(
            b.sync.selfId, SendPayload(url = "https://example.invalid/big.bin", fileName = "big.bin"),
            SendMode.TRANSFER_AND_CONTINUE, false, partialFiles = pieces.map { it.first } + "$src\\missing.part9"
        )
        T.check("scratch folder A->B: onResult true", sendAndWait(a, b, scratch) == 1, "${a.sync.sending.value}")
        val scratchGot = b.last()
        T.check("scratch folder A->B: every existing piece arrives (missing one skipped)", b.receivedCount() == before + 1 && scratchGot?.second?.size == pieces.size, "${scratchGot?.second}")
        if (scratchGot != null && scratchGot.second.size == pieces.size) {
            for ((index, piece) in pieces.withIndex()) {
                val name = piece.first.substringAfterLast('\\')
                val got = scratchGot.second[index]
                T.check("scratch folder A->B: $name same name and SHA-256 (${piece.second.size} bytes)", got.substringAfterLast('\\') == name && same(piece.first, got), got)
            }
            T.check("scratch folder A->B: written under the receive folder", scratchGot.second.all { it.startsWith(b.tempDir + "\\recv-") })
        }

        val finished = "$src\\done.bin"
        val finishedBytes = winRandomBytes(150_001)
        winWriteFile(finished, finishedBytes)
        before = a.receivedCount()
        val whole = SendRequest(
            a.sync.selfId, SendPayload(url = "https://example.invalid/video", fileName = "video.mp4"),
            SendMode.TRANSFER_AND_CONTINUE, false, completedFilePath = finished
        )
        T.check("finished file B->A: onResult true", sendAndWait(b, a, whole) == 1, "${b.sync.sending.value}")
        val wholeGot = a.last()
        T.check("finished file B->A: manifest + one piece", a.receivedCount() == before + 1 && wholeGot?.second?.map { it.substringAfterLast('\\') } == listOf("video.mp4.chunks", "video.mp4.part0"), "${wholeGot?.second}")
        if (wholeGot != null && wholeGot.second.size == 2) {
            T.check("finished file B->A: manifest text", winReadFile(wholeGot.second[0])?.decodeToString() == "0 0 150000")
            T.check("finished file B->A: piece SHA-256", same(finished, wholeGot.second[1]))
        }

        before = b.receivedCount()
        val asked = SendResult()
        val offered = SendRequest(
            b.sync.selfId, SendPayload(url = "https://example.invalid/offer?x=1", fileName = "offer.mp4"),
            SendMode.TRANSFER_AND_CONTINUE, false, completedFilePath = finished
        )
        a.sync.send(offered, asked.done)
        T.check("offer: B is asked before anything is sent", waitFor(4000) { b.offer() != null }, "${b.sync.pairing.value}")
        val offer = b.offer()
        T.check("offer: file name, size and sender name shown", offer?.fileName == "offer.mp4" && offer.size == 150_001L && offer.peerId == a.sync.selfId && offer.peerName == a.sync.selfName, "$offer")
        T.check("offer: sender waits", waitFor(2000) { a.sync.sending.value is SendState.Waiting } && asked.state.value == 0 && b.receivedCount() == before, "${a.sync.sending.value}")
        b.sync.confirmPairing()
        T.check("offer accepted: the file arrives", waitFor(6000) { asked.state.value == 1 } && b.receivedCount() == before + 1 && b.last()?.second?.size == 2, "${a.sync.sending.value}")
        T.check("offer accepted: B's prompt closed", b.sync.pairing.value == null)

        val bigFile = "$src\\progress.bin"
        winWriteFile(bigFile, winRandomBytes(3_000_000))
        val traceLock = WinLock()
        val trace = ArrayList<ReceiveState?>()
        val watcher = launch(Dispatchers.IO) { b.sync.receiving.collect { s -> traceLock.withLock { trace.add(s) } } }
        val progressed = SendResult()
        before = b.receivedCount()
        a.sync.send(
            SendRequest(
                b.sync.selfId,
                SendPayload(url = "https://example.invalid/progress", fileName = "progress.mkv", totalBytes = 9_000_000, downloadedBytes = 3_000_000),
                SendMode.TRANSFER_AND_CONTINUE, false, completedFilePath = bigFile
            ),
            progressed.done
        )
        T.check("receive: B is asked first", waitFor(4000) { b.offer()?.fileName == "progress.mkv" }, "${b.sync.pairing.value}")
        b.sync.confirmPairing()
        val atAccept = b.sync.receiving.value as? ReceiveState.Receiving
        T.check("receive: right after Accept B shows file, sender and 0", atAccept?.fileName == "progress.mkv" && atAccept.deviceName == a.sync.selfName && atAccept.fraction == 0f, "$atAccept")
        T.check("receive: the send completes and B ends Done", waitFor(15_000) { progressed.state.value == 1 && b.sync.receiving.value is ReceiveState.Done }, "${b.sync.receiving.value}")
        delay(200)
        watcher.cancel()
        val seen = traceLock.withLock { trace.toList() }
        val fractions = seen.mapNotNull { (it as? ReceiveState.Receiving)?.fraction }
        T.check("receive: progress starts at 0 and only goes up", fractions.firstOrNull() == 0f && fractions.zipWithNext().all { (x, y) -> y >= x }, "$fractions")
        T.check("receive: progress seen between 0 and 1", fractions.any { it > 0f && it <= 1f }, "$fractions")
        T.check("receive: last state is Done for the file", (seen.lastOrNull() as? ReceiveState.Done)?.fileName == "progress.mkv", "${seen.lastOrNull()}")
        T.check("receive: display sizes travel in the payload", b.receivedCount() == before + 1 && b.last()?.first?.totalBytes == 9_000_000L && b.last()?.first?.downloadedBytes == 3_000_000L, "${b.last()?.first}")
        b.sync.clearReceiving()
        T.check("receive: Hide clears the state", b.sync.receiving.value == null)

        before = b.receivedCount()
        val directLink = SendResult()
        a.sync.send(linkRequest(b, "https://example.invalid/direct-link"), directLink.done)
        T.check("direct link only: B is asked, no size", waitFor(4000) { b.offer()?.fileName == "x.bin" } && b.offer()?.size == 0L, "${b.sync.pairing.value}")
        T.check("direct link only: nothing arrives before Accept", b.receivedCount() == before)
        b.sync.confirmPairing()
        T.check("direct link only: arrives after Accept", waitFor(6000) { directLink.state.value == 1 } && b.receivedCount() == before + 1, "${a.sync.sending.value}")
        T.check("direct link only: no receive progress", b.sync.receiving.value == null, "${b.sync.receiving.value}")

        before = b.receivedCount()
        val mediaResult = SendResult()
        val media = SendRequest(
            b.sync.selfId,
            SendPayload(
                url = "https://example.invalid/stream.mp4", fileName = "clip.mp4", engineType = "MEDIA_STREAM",
                formatId = "137+140", originalUrl = "https://example.invalid/watch?v=abc"
            ),
            SendMode.LINK_ONLY, false
        )
        a.sync.send(media, mediaResult.done)
        T.check("media link: B is asked first", waitFor(4000) { b.offer()?.fileName == "clip.mp4" }, "${b.sync.pairing.value}")
        T.check("media link: nothing arrives before Accept", b.receivedCount() == before)
        b.sync.confirmPairing()
        T.check("media link: arrives with engine, format and page", waitFor(6000) { mediaResult.state.value == 1 } &&
            b.last()?.first?.engineType == "MEDIA_STREAM" && b.last()?.first?.formatId == "137+140" &&
            b.last()?.first?.originalUrl == "https://example.invalid/watch?v=abc", "${b.last()?.first}")
        T.check("media link: no receive progress", b.sync.receiving.value == null, "${b.sync.receiving.value}")

        val rejected = SendResult()
        val again = linkRequest(b, "https://example.invalid/rejected")
        a.sync.send(again, rejected.done)
        T.check("reject: B is asked", waitFor(4000) { b.offer()?.fileName == "x.bin" }, "${b.sync.pairing.value}")
        b.sync.cancelPairing()
        T.check("reject: the send fails", waitFor(5000) { rejected.state.value == 2 }, "${a.sync.sending.value}")
        T.check("reject: reason no-answer", sendFailure(a) == "no-answer", "${sendFailure(a)}")
        T.check("reject: nothing received, both still paired", b.receivedCount() == before + 1 && a.pairedWith(b) && b.pairedWith(a))

        val unanswered = SendResult()
        val timeoutStart = TimeSource.Monotonic.markNow()
        a.sync.send(again, unanswered.done)
        T.check("timeout: the same send is asked again after a reject", waitFor(4000) { b.offer() != null }, "${b.sync.pairing.value}")
        T.check("timeout: the send fails when nobody answers", waitFor(syncTiming.offerWaitMs + 5000) { unanswered.state.value == 2 }, "${a.sync.sending.value}")
        T.check("timeout: reason no-answer, after the offer wait", sendFailure(a) == "no-answer" && timeoutStart.elapsedNow().inWholeMilliseconds >= syncTiming.offerWaitMs, "${sendFailure(a)} after ${timeoutStart.elapsedNow()}")
        T.check("timeout: B's prompt closed, nothing received, still paired", b.sync.pairing.value == null && b.receivedCount() == before + 1 && a.pairedWith(b) && b.pairedWith(a))

        c.sync.startPairingWithHost(HOST_A)
        T.check("C pairs with A: both show the same code", waitFor(5000) { a.code() != null && c.code() != null } && a.code() == c.code(), "${a.code()} / ${c.code()}")
        T.check("A lists C on its fallback port (direct announces)", waitFor(3000) { a.sees(c, cPort) })

        b.sync.startPairingWithHost(HOST_A)
        T.check("busy: another device's code is on screen", waitFor(5000) { b.failedReason() == "busy" }, "${b.sync.pairing.value}")
        b.sync.cancelPairing()
        T.check("busy: A still shows C's code", a.code() == c.code() && (a.sync.pairing.value as? PairingState.Confirm)?.peerId == c.sync.selfId)

        c.sync.startPairingWithHost(HOST_A)
        T.check("wait:N for a repeat request", waitFor(5000) { c.failedReason()?.startsWith("wait:") == true }, "${c.sync.pairing.value}")

        c.sync.confirmPairing()
        T.check("C confirmed its side", waitFor(1000) { c.pairedWith(a) } && !a.pairedWith(c))
        val pendingResult = SendResult()
        c.sync.send(linkRequest(a, "https://example.invalid/pending"), pendingResult.done)
        T.check("pending: sender waits while A reads the code", waitFor(3000) { c.sync.sending.value is SendState.Waiting }, "${c.sync.sending.value}")
        before = a.receivedCount()
        a.sync.confirmPairing()
        T.check("pending: after the code, A is asked to accept the send", waitFor(4000) { a.offer()?.peerId == c.sync.selfId }, "${a.sync.pairing.value}")
        a.sync.confirmPairing()
        T.check("pending: send goes through after A confirms", waitFor(6000) { pendingResult.state.value == 1 }, "${c.sync.sending.value}")
        T.check("pending: A got the link from C", a.receivedCount() == before + 1 && a.last()?.first?.fromDeviceId == c.sync.selfId)

        before = c.receivedCount()
        T.check("A->C over the fallback port", sendAndWait(a, c, linkRequest(c, "https://example.invalid/to-c")) == 1 && c.receivedCount() == before + 1, "${a.sync.sending.value}")

        c.sync.setVisible(false)
        T.check("hidden: C closes its TCP port", waitFor(2000) { c.sync.listeningTcpPort == 0 })
        delay(syncTiming.deviceExpireMs + 1000)
        T.check("hidden: A no longer lists C", !a.sync.discovered.value.any { it.id == c.sync.selfId }, "${a.sync.discovered.value}")
        T.check("hidden: C still lists A (its probes are answered)", c.sees(a, SYNC_TCP), "${c.sync.discovered.value}")
        T.check("hidden: C refuses connections", run {
            val probe = withTimeoutOrNull(2000) { runCatching { aSocket(selector).tcp().connect(HOST_C, cPort) }.getOrNull() }
            closeQuiet(probe)
            probe == null
        })

        a.sync.unpair(c.sync.selfId)
        T.check("not paired: A forgot C, C (hidden) was not told", waitFor(1000) { !a.pairedWith(c) } && c.pairedWith(a))
        T.check("not paired: C's send fails", sendAndWait(c, a, linkRequest(a, "https://example.invalid/np")) == 2)
        T.check("not paired: reason", sendFailure(c) == "the other device unpaired this one; send again to pair", "${sendFailure(c)}")
        T.check("not paired: C dropped its pairing", !c.pairedWith(a))

        suspend fun pairCWithA(): Boolean {
            delay(syncTiming.pairRequestGapMs + 200)
            c.sync.startPairingWithHost(HOST_A)
            val ok = waitFor(5000) { a.code() != null && c.code() != null && a.code() == c.code() }
            c.sync.confirmPairing()
            return ok && waitFor(1000) { c.pairedWith(a) }
        }

        T.check("declined: pairing started", pairCWithA())
        a.sync.cancelPairing()
        T.check("declined: send fails", sendAndWait(c, a, linkRequest(a, "https://example.invalid/d")) == 2)
        T.check("declined: reason", sendFailure(c) == "declined", "${sendFailure(c)}")
        T.check("declined: C dropped its pairing", !c.pairedWith(a))

        T.check("expired: pairing started", pairCWithA())
        val expiredStart = TimeSource.Monotonic.markNow()
        T.check("expired: send fails", sendAndWait(c, a, linkRequest(a, "https://example.invalid/e"), 12_000) == 2)
        T.check("expired: reason", sendFailure(c) == "expired", "${sendFailure(c)} after ${expiredStart.elapsedNow()}")
        T.check("expired: A closed the code", waitFor(2000) { a.sync.pairing.value == null })
        T.check("expired: C dropped its pairing", !c.pairedWith(a))

        T.check("blocked: pairing started", pairCWithA())
        a.sync.blockPairing()
        T.check("blocked: A lists C as blocked", a.sync.blocked.value.any { it.id == c.sync.selfId })
        T.check("blocked: send fails", sendAndWait(c, a, linkRequest(a, "https://example.invalid/b")) == 2)
        T.check("blocked: reason", sendFailure(c) == "blocked", "${sendFailure(c)}")
        c.sync.startPairingWithHost(HOST_A)
        T.check("blocked: a new pairing is refused", waitFor(5000) { c.failedReason() == "blocked" }, "${c.sync.pairing.value}")
        a.sync.unblock(c.sync.selfId)
        T.check("unblock: list empty", a.sync.blocked.value.isEmpty())

        T.check("bad key: pairing started", pairCWithA())
        a.sync.confirmPairing()
        T.check("bad key: both paired", waitFor(1000) { a.pairedWith(c) && c.pairedWith(a) })
        val realKey = c.store.keyFor(a.sync.selfId)!!
        c.store.forceKey(a.sync.selfId, winRandomBytes(32))
        T.check("bad key: send fails", sendAndWait(c, a, linkRequest(a, "https://example.invalid/k")) == 2)
        T.check("bad key: reason", sendFailure(c) == "bad key", "${sendFailure(c)}")
        c.store.forceKey(a.sync.selfId, realKey)
        before = a.receivedCount()
        T.check("right key again: send works", sendAndWait(c, a, linkRequest(a, "https://example.invalid/k2")) == 1 && a.receivedCount() == before + 1)

        c.sync.unpair(a.sync.selfId)
        T.check("unpair notice C->A: A drops C", waitFor(3000) { !a.pairedWith(c) } && !c.pairedWith(a))

        val keyAB = a.store.keyFor(b.sync.selfId)!!
        val bId = b.sync.selfId.encodeToByteArray()

        suspend fun rawConnect() = aSocket(selector).tcp().connect(HOST_A, SYNC_TCP)

        suspend fun rawApprove(meta: String): Boolean {
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            rawWrite(output, 3, intBytes(bId.size) + bId + winAesGcmSeal(keyAB, meta.encodeToByteArray()))
            val reply = rawRead(input, 3000)
            closeQuiet(socket)
            val asked = reply?.first == 7 && reply.second.decodeToString() == "pending" && waitFor(2000) { a.offer() != null }
            a.sync.confirmPairing()
            return asked
        }

        run {
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            rawWrite(output, 42, "x".encodeToByteArray())
            val reply = rawRead(input, 3000)
            T.check("raw: unknown frame gets ERR unknown", reply?.first == 7 && reply.second.decodeToString() == "unknown", "${reply?.first}")
            closeQuiet(socket)
        }
        run {
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            rawWrite(output, 1, ByteArray(0), length = Int.MAX_VALUE)
            T.check("raw: oversized frame closes the connection", rawClosed(input, 3000))
            closeQuiet(socket)
        }
        run {
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val mark = TimeSource.Monotonic.markNow()
            val closed = rawClosed(input, 5000)
            T.check("raw: silent connection closed after the first-frame wait", closed && mark.elapsedNow().inWholeMilliseconds >= 1000, "${mark.elapsedNow()}")
            closeQuiet(socket)
        }
        run {
            val dirsBefore = recvDirs(a).size
            before = a.receivedCount()
            val meta = """{"payload":{"url":"https://example.invalid/dots"},"mode":"TRANSFER_AND_CONTINUE","files":[{"name":"..","size":1}]}"""
            T.check("raw: a raw send is asked first ('pending', then accepted)", rawApprove(meta))
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            rawWrite(output, 3, intBytes(bId.size) + bId + winAesGcmSeal(keyAB, meta.encodeToByteArray()))
            val ready = rawRead(input, 3000)
            val err = rawRead(input, 3000)
            T.check("raw: '..' file name refused", ready?.first == 8 && err?.first == 7 && err.second.decodeToString() == "bad file name", "${ready?.first} ${err?.first} ${err?.second?.decodeToString()}")
            closeQuiet(socket)
            T.check("raw: refused receive leaves no folder and no payload", waitFor(1000) { recvDirs(a).size == dirsBefore } && a.receivedCount() == before)
        }
        run {
            before = a.receivedCount()
            val meta = """{"payload":{"url":"https://example.invalid/escape"},"mode":"TRANSFER_AND_CONTINUE","files":[{"name":"..\\..\\evil.txt","size":3}]}"""
            T.check("raw: a raw send is asked first ('pending', then accepted)", rawApprove(meta))
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            rawWrite(output, 3, intBytes(bId.size) + bId + winAesGcmSeal(keyAB, meta.encodeToByteArray()))
            val ready = rawRead(input, 3000)
            rawWrite(output, 4, winAesGcmSeal(keyAB, "abc".encodeToByteArray()))
            rawWrite(output, 5, ByteArray(0))
            val ack = rawRead(input, 3000)
            closeQuiet(socket)
            val path = a.last()?.second?.firstOrNull() ?: ""
            T.check("raw: '..\\..\\evil.txt' lands as evil.txt inside the receive folder", ready?.first == 8 && ack?.first == 6 && a.receivedCount() == before + 1 && path.startsWith(a.tempDir + "\\recv-") && path.endsWith("\\evil.txt"), path)
            T.check("raw: nothing written outside", winReadFile("$root\\evil.txt") == null && winReadFile("${a.tempDir}\\evil.txt") == null)
        }
        run {
            before = a.receivedCount()
            val meta = """{"payload":{"url":"https://example.invalid/old","fileName":"old.bin"},"mode":"TRANSFER_AND_CONTINUE","files":[{"name":"old.bin.part0"}]}"""
            T.check("old sender without sizes: asked first", rawApprove(meta))
            T.check("old sender without sizes: indeterminate bar after Accept", (a.sync.receiving.value as? ReceiveState.Receiving)?.fraction == -1f, "${a.sync.receiving.value}")
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            rawWrite(output, 3, intBytes(bId.size) + bId + winAesGcmSeal(keyAB, meta.encodeToByteArray()))
            val ready = rawRead(input, 3000)
            rawWrite(output, 4, winAesGcmSeal(keyAB, "abcdef".encodeToByteArray()))
            rawWrite(output, 5, ByteArray(0))
            val ack = rawRead(input, 3000)
            closeQuiet(socket)
            T.check("old sender without sizes: the file arrives", ready?.first == 8 && ack?.first == 6 && a.receivedCount() == before + 1 && a.last()?.first?.totalBytes == 0L, "${ack?.first}")
            T.check("old sender without sizes: ends Done", (a.sync.receiving.value as? ReceiveState.Done)?.fileName == "old.bin", "${a.sync.receiving.value}")
        }
        run {
            val meta = """{"payload":{"url":"https://example.invalid/never","fileName":"never.bin"},"mode":"TRANSFER_AND_CONTINUE","files":[{"name":"never.bin.part0","size":10}]}"""
            T.check("accepted, never streamed: asked first", rawApprove(meta))
            T.check("accepted, never streamed: 0 after Accept", (a.sync.receiving.value as? ReceiveState.Receiving)?.fraction == 0f, "${a.sync.receiving.value}")
            T.check("accepted, never streamed: no-answer after the wait", waitFor(syncTiming.receiveStartMs + 2000) { (a.sync.receiving.value as? ReceiveState.Failed)?.reason == "no-answer" }, "${a.sync.receiving.value}")
            a.sync.clearReceiving()
            T.check("accepted, never streamed: Close clears it", a.sync.receiving.value == null)
        }
        run {
            before = a.receivedCount()
            val meta = """{"payload":{"url":"https://example.invalid/tamper"},"mode":"TRANSFER_AND_CONTINUE","files":[{"name":"t.bin","size":3}]}"""
            T.check("raw: a raw send is asked first ('pending', then accepted)", rawApprove(meta))
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            rawWrite(output, 3, intBytes(bId.size) + bId + winAesGcmSeal(keyAB, meta.encodeToByteArray()))
            val ready = rawRead(input, 3000)
            val chunk = winAesGcmSeal(keyAB, "abc".encodeToByteArray())
            chunk[chunk.size - 1] = (chunk[chunk.size - 1].toInt() xor 1).toByte()
            rawWrite(output, 4, chunk)
            val err = rawRead(input, 3000)
            closeQuiet(socket)
            T.check("raw: tampered chunk refused", ready?.first == 8 && err?.first == 7 && err.second.decodeToString() == "bad chunk" && a.receivedCount() == before, "${err?.second?.decodeToString()}")
        }
        run {
            val socket = rawConnect()
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            val fake = "ffffffffffffffffffffffffffffffff".encodeToByteArray()
            rawWrite(output, 3, intBytes(fake.size) + fake + ByteArray(40))
            val reply = rawRead(input, 3000)
            T.check("raw: send from an unknown id gets 'not paired'", reply?.first == 7 && reply.second.decodeToString() == "not paired", "${reply?.second?.decodeToString()}")
            closeQuiet(socket)
        }

        b.sync.stop()
        T.check("stop: B closes its TCP port", waitFor(2000) { b.sync.listeningTcpPort == 0 })
        T.check("stop: A drops B after the expiry", waitFor(syncTiming.deviceExpireMs + 2000) { !a.sees(b) })
        val udp = aSocket(selector).udp().bind(InetSocketAddress(HOST_B, 0))
        val descriptor = (udp as? Selectable)?.descriptor
        T.check("SIO_UDP_CONNRESET can be switched off on a ktor socket", descriptor != null && descriptor > 0 && winUdpIgnoreUnreachable(descriptor.toULong()), "$descriptor")
        val probe = """{"t":"fj-probe","id":"probe-from-closed-port","name":"p","plat":"p","port":0}""".encodeToByteArray()
        repeat(3) {
            val packet = Buffer()
            packet.write(probe)
            udp.send(Datagram(packet, InetSocketAddress(HOST_A, SYNC_UDP)))
            delay(100)
        }
        closeQuiet(udp)
        delay(500)
        b.sync.start()
        T.check("start again: B reopens its TCP port", waitFor(3000) { b.sync.listeningTcpPort == SYNC_TCP })
        T.check("start again: A hears B (A's listener survived replies to a closed port)", waitFor(3000) { a.sees(b, SYNC_TCP) })
        T.check("probe sender is not listed", !a.sync.discovered.value.any { it.id == "probe-from-closed-port" })
        before = b.receivedCount()
        T.check("after restart: A->B send works", sendAndWait(a, b, linkRequest(b, "https://example.invalid/again")) == 1 && b.receivedCount() == before + 1)

        a.sync.unpair(b.sync.selfId)
        T.check("unpair notice A->B: B drops A", waitFor(3000) { !b.pairedWith(a) } && !a.pairedWith(b))
    } finally {
        for (node in listOf(a, b, c)) node.sync.shutdown()
        closeQuiet(blocker)
        selector.close()
    }
    if (opts["realports"] == "1") realPortChecks(root) else T.info("real-port checks skipped: they bind $REAL_UDP / $REAL_TCP on $HOST_D (pass realports=1)")
}
