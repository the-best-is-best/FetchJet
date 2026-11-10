import com.michelle.raouf.fetch_jet.feature.device_sync.DeviceSyncTiming
import com.michelle.raouf.fetch_jet.feature.device_sync.MemoryDeviceStore
import com.michelle.raouf.fetch_jet.feature.device_sync.PairingState
import com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState
import com.michelle.raouf.fetch_jet.feature.device_sync.SendMode
import com.michelle.raouf.fetch_jet.feature.device_sync.SendPayload
import com.michelle.raouf.fetch_jet.feature.device_sync.SendRequest
import com.michelle.raouf.fetch_jet.feature.device_sync.SendState
import com.michelle.raouf.fetch_jet.feature.device_sync.winDeviceSync
import com.michelle.raouf.fetch_jet.platform.desktop.OsLineSplitter
import com.michelle.raouf.fetch_jet.platform.win.WinLock
import com.michelle.raouf.fetch_jet.platform.win.WinProcess
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winRandomBytes
import com.michelle.raouf.fetch_jet.platform.win.winReadFile
import com.michelle.raouf.fetch_jet.platform.win.winReadText
import com.michelle.raouf.fetch_jet.platform.win.winSha256OfFile
import com.michelle.raouf.fetch_jet.platform.win.winWriteFile
import kotlin.concurrent.AtomicInt
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private class JvmPeer(private val process: WinProcess) {
    private val lock = WinLock()
    private val lines = ArrayList<String>()

    fun add(line: String) {
        println("JVM| $line")
        lock.withLock { lines.add(line) }
    }

    fun count(): Int = lock.withLock { lines.size }
    fun find(from: Int = 0, test: (String) -> Boolean): String? = lock.withLock { lines.drop(from).firstOrNull(test) }
    fun lines(from: Int = 0): List<String> = lock.withLock { lines.drop(from) }

    fun send(vararg parts: String) {
        process.writeStdin((parts.joinToString("\t") + "\n").encodeToByteArray())
    }

    suspend fun waitLine(timeoutMs: Long, from: Int = 0, test: (String) -> Boolean): String? {
        val mark = TimeSource.Monotonic.markNow()
        while (mark.elapsedNow().inWholeMilliseconds < timeoutMs) {
            find(from, test)?.let { return it }
            delay(25)
        }
        return find(from, test)
    }

    suspend fun state(): List<String> {
        val from = count()
        send("state")
        return waitLine(3000, from) { it.startsWith("STATE\t") }?.split('\t') ?: emptyList()
    }

    suspend fun stateUntil(timeoutMs: Long, test: (List<String>) -> Boolean): List<String> {
        val mark = TimeSource.Monotonic.markNow()
        var last = state()
        while (!test(last) && mark.elapsedNow().inWholeMilliseconds < timeoutMs) {
            delay(150)
            last = state()
        }
        return last
    }
}

private fun sameBytes(a: String, b: String): Boolean {
    val x = winSha256OfFile(a) ?: return false
    val y = winSha256OfFile(b) ?: return false
    return x.contentEquals(y)
}

fun deviceSyncJvmTests(work: String, args: List<String>) = runBlocking {
    val opts = args.mapNotNull { arg -> arg.indexOf('=').takeIf { it > 0 }?.let { arg.substring(0, it) to arg.substring(it + 1) } }.toMap()
    val java = opts["java"]
    val classpath = opts["cp"]?.let { winReadText(it)?.trim() }
    if (java == null || classpath.isNullOrBlank()) {
        T.check("jvm peer classpath available (build jvmpeer first)", false, "java=$java cp=${opts["cp"]}")
        return@runBlocking
    }
    val root = "$work\\syncjvm"
    winDeleteRecursively(root)
    winCreateDirectories(root)
    val udp = 28481
    val tcp = 28482

    val store = MemoryDeviceStore()
    val receivedLock = WinLock()
    val received = ArrayList<Pair<SendPayload, List<String>>>()
    val a = winDeviceSync(
        platformName = "native · Windows", tempDir = "$root\\a", store = store,
        onReceive = { payload, files -> receivedLock.withLock { received.add(payload to files) } },
        discoveryPort = udp, tcpPort = tcp, bindHost = "127.0.0.1", sendBroadcast = false,
        timing = DeviceSyncTiming(),
    )
    fun receivedCount() = receivedLock.withLock { received.size }
    fun lastReceived() = receivedLock.withLock { received.lastOrNull() }

    val process = WinProcess.start(
        listOf(
            java, "-Dfetchjet.sync.offerWaitMs=3000", "-Dfj.host=127.0.0.3", "-Dfj.udp=$udp", "-Dfj.tcp=$tcp", "-Dfj.dir=$root\\jvm",
            "-cp", classpath, "PeerKt"
        ),
        pipeStdin = true, mergeStderr = true,
    )
    val peer = JvmPeer(process)
    val splitter = OsLineSplitter { peer.add(it) }
    val reader = launch(Dispatchers.IO) {
        val buffer = ByteArray(8192)
        while (true) {
            val n = process.readStdout(buffer)
            if (n <= 0) break
            splitter.feed(buffer, n)
        }
        splitter.finish()
    }

    val nativeAccepts = AtomicInt(1)
    val acceptor = launch(Dispatchers.IO) {
        while (true) {
            if (nativeAccepts.value == 1 && a.pairing.value is PairingState.Offer) a.confirmPairing()
            delay(25)
        }
    }

    try {
        val hello = peer.waitLine(20_000) { it.startsWith("PEER\t") }
        T.check("jvm peer started", hello != null)
        val jId = hello?.substringAfter('\t') ?: return@runBlocking
        T.check("jvm peer listens on 127.0.0.3 with the test port", peer.waitLine(5000) { it == "FJSYNC visible tcpPort=$tcp" } != null)

        a.setVisible(true)
        a.start()
        delay(500)

        a.startPairingWithHost("127.0.0.3")
        val aCode = run {
            val mark = TimeSource.Monotonic.markNow()
            while ((a.pairing.value as? PairingState.Confirm) == null && mark.elapsedNow().inWholeMilliseconds < 8000) delay(25)
            (a.pairing.value as? PairingState.Confirm)?.code
        }
        val jState = peer.stateUntil(5000) { it.getOrNull(1)?.startsWith("confirm:") == true }
        val jCode = jState.getOrNull(1)?.split(':')?.getOrNull(1)
        T.check("native -> jvm pairing: same 6-digit code on both (ECDH + code interop)", aCode != null && aCode == jCode, "$aCode / $jCode")
        T.check("jvm sees the native id in the request", jState.getOrNull(1)?.endsWith(":${a.selfId}") == true)
        a.confirmPairing()

        val pendingResult = AtomicInt(0)
        a.send(SendRequest(jId, SendPayload(url = "https://example.invalid/pending"), SendMode.LINK_ONLY, false)) { pendingResult.value = if (it) 1 else 2 }
        val sawWaiting = run {
            val mark = TimeSource.Monotonic.markNow()
            while (a.sending.value !is SendState.Waiting && mark.elapsedNow().inWholeMilliseconds < 5000) delay(25)
            a.sending.value is SendState.Waiting
        }
        T.check("native sender waits on 'pending' from the jvm receiver", sawWaiting, "${a.sending.value}")
        var from = peer.count()
        peer.send("confirm")
        val mark1 = TimeSource.Monotonic.markNow()
        while (pendingResult.value == 0 && mark1.elapsedNow().inWholeMilliseconds < 8000) delay(25)
        T.check("native -> jvm link goes through after the jvm confirms", pendingResult.value == 1, "${a.sending.value}")
        val recvLink = peer.waitLine(3000, from) { it.startsWith("RECV\t") }?.split('\t')
        T.check("jvm got the link with the native id", recvLink?.getOrNull(1) == "https://example.invalid/pending" && recvLink.getOrNull(2) == a.selfId, "$recvLink")

        val st = peer.stateUntil(5000) { s -> s.getOrNull(3)?.contains("${a.selfId}@127.0.0.1:$tcp") == true }
        T.check("jvm lists the native device through its direct announces", st.getOrNull(3)?.contains("${a.selfId}@127.0.0.1:$tcp") == true, "${st.getOrNull(3)}")
        T.check("both sides paired", st.getOrNull(2) == a.selfId && store.isPaired(jId))

        val src = "$root\\src"
        winCreateDirectories(src)
        val pieces = listOf(
            "$src\\big.bin.part0" to winRandomBytes(200_000),
            "$src\\big.bin.part1" to ByteArray(0),
            "$src\\big.bin.part2" to winRandomBytes(65536),
            "$src\\ملف عربي.part3" to winRandomBytes(1000),
            "$src\\big.bin.chunks" to "0 0 199999\n".encodeToByteArray(),
        )
        for ((path, bytes) in pieces) winWriteFile(path, bytes)
        from = peer.count()
        val filesResult = AtomicInt(0)
        a.send(
            SendRequest(jId, SendPayload(url = "https://example.invalid/big", fileName = "big.bin"), SendMode.TRANSFER_AND_CONTINUE, false, partialFiles = pieces.map { it.first })
        ) { filesResult.value = if (it) 1 else 2 }
        val recvFiles = peer.waitLine(15_000, from) { it.startsWith("RECV\t") }?.split('\t')
        val jFiles = recvFiles?.getOrNull(3)?.split('|') ?: emptyList()
        T.check("native -> jvm scratch folder: jvm wrote every piece", jFiles.size == pieces.size, "$jFiles")
        if (jFiles.size == pieces.size) {
            for ((index, piece) in pieces.withIndex()) {
                val name = piece.first.substringAfterLast('\\')
                T.check("native -> jvm: $name same name and SHA-256", jFiles[index].replace('/', '\\').substringAfterLast('\\') == name && sameBytes(piece.first, jFiles[index]), jFiles[index])
            }
        }
        val mark2 = TimeSource.Monotonic.markNow()
        while (filesResult.value == 0 && mark2.elapsedNow().inWholeMilliseconds < 5000) delay(25)
        T.check("native -> jvm scratch folder: jvm acked", filesResult.value == 1, "${a.sending.value}")

        val finished = "$src\\done.bin"
        winWriteFile(finished, winRandomBytes(150_001))

        peer.send("autoaccept", "off")
        val offerResult = AtomicInt(0)
        a.send(
            SendRequest(jId, SendPayload(url = "https://example.invalid/offer", fileName = "offer.mp4"), SendMode.TRANSFER_AND_CONTINUE, false, completedFilePath = finished)
        ) { offerResult.value = if (it) 1 else 2 }
        val offerState = peer.stateUntil(5000) { it.getOrNull(1)?.startsWith("offer:") == true }
        T.check("native -> jvm: jvm asks first (file name, size, sender id)", offerState.getOrNull(1) == "offer:offer.mp4:150001:${a.selfId}", "${offerState.getOrNull(1)}")
        T.check("native -> jvm: native waits for the answer", a.sending.value is SendState.Waiting && offerResult.value == 0, "${a.sending.value}")
        val rFrom = peer.count()
        peer.send("confirm")
        val markOffer = TimeSource.Monotonic.markNow()
        while (offerResult.value == 0 && markOffer.elapsedNow().inWholeMilliseconds < 8000) delay(25)
        T.check("native -> jvm: accepted on the jvm, the file goes through", offerResult.value == 1, "${a.sending.value}")
        val rDone = peer.waitLine(3000, rFrom) { it == "RSTATE\tdone:offer.mp4" }
        val rLines = peer.lines(rFrom).filter { it.startsWith("RSTATE\treceiving:") }
        val rFractions = rLines.mapNotNull { it.split(':').getOrNull(1)?.toFloatOrNull() }
        T.check("native -> jvm: the jvm receiver shows progress from 0 after Accept", rLines.firstOrNull() == "RSTATE\treceiving:0.0:offer.mp4" && rFractions.zipWithNext().all { (x, y) -> y >= x }, "$rLines")
        T.check("native -> jvm: the jvm receiver ends Done", rDone != null, "${peer.lines(rFrom).filter { it.startsWith("RSTATE") }}")

        val rejectResult = AtomicInt(0)
        a.send(SendRequest(jId, SendPayload(url = "https://example.invalid/no"), SendMode.LINK_ONLY, false)) { rejectResult.value = if (it) 1 else 2 }
        val rejectState = peer.stateUntil(5000) { it.getOrNull(1)?.startsWith("offer:") == true }
        peer.send("cancel")
        val markReject = TimeSource.Monotonic.markNow()
        while (rejectResult.value == 0 && markReject.elapsedNow().inWholeMilliseconds < 8000) delay(25)
        T.check("native -> jvm: rejected on the jvm -> 'no-answer', still paired", rejectState.getOrNull(1)?.startsWith("offer:") == true && rejectResult.value == 2 && (a.sending.value as? SendState.Failed)?.reason == "no-answer" && store.isPaired(jId), "${a.sending.value}")

        val timeoutResult = AtomicInt(0)
        val markTimeout = TimeSource.Monotonic.markNow()
        a.send(SendRequest(jId, SendPayload(url = "https://example.invalid/no"), SendMode.LINK_ONLY, false)) { timeoutResult.value = if (it) 1 else 2 }
        while (timeoutResult.value == 0 && markTimeout.elapsedNow().inWholeMilliseconds < 12_000) delay(25)
        T.check("native -> jvm: nobody answers on the jvm -> 'no-answer' after its wait", timeoutResult.value == 2 && (a.sending.value as? SendState.Failed)?.reason == "no-answer" && markTimeout.elapsedNow().inWholeMilliseconds >= 3000 && store.isPaired(jId), "${a.sending.value} after ${markTimeout.elapsedNow()}")
        peer.send("autoaccept", "on")

        nativeAccepts.value = 0
        val fromAsk = peer.count()
        peer.send("link", a.selfId, "https://example.invalid/refused-here")
        val markAsk = TimeSource.Monotonic.markNow()
        while (a.pairing.value !is PairingState.Offer && markAsk.elapsedNow().inWholeMilliseconds < 5000) delay(25)
        val nativeOffer = a.pairing.value as? PairingState.Offer
        T.check("jvm -> native: native asks first", nativeOffer?.peerId == jId && nativeOffer.fileName == "x.bin", "$nativeOffer")
        a.cancelPairing()
        val refused = peer.waitLine(8000, fromAsk) { it.startsWith("SENT\t") }
        T.check("jvm -> native: rejected here -> jvm sender fails with 'no-answer'", refused?.startsWith("SENT\tfalse") == true && refused.contains("no-answer"), "$refused")
        var before = receivedCount()
        val mediaFrom = peer.count()
        peer.send("media", a.selfId, "https://example.invalid/stream.mp4", "https://example.invalid/watch?v=abc")
        val markMedia = TimeSource.Monotonic.markNow()
        while (a.pairing.value !is PairingState.Offer && markMedia.elapsedNow().inWholeMilliseconds < 5000) delay(25)
        val mediaOffer = a.pairing.value as? PairingState.Offer
        T.check("jvm -> native media link: native asks first", mediaOffer?.peerId == jId && mediaOffer.fileName == "clip.mp4" && receivedCount() == before, "$mediaOffer")
        a.confirmPairing()
        val mediaSent = peer.waitLine(8000, mediaFrom) { it.startsWith("SENT\t") }
        val mediaGot = lastReceived()?.first
        T.check("jvm -> native media link: arrives with engine and format after Accept", mediaSent?.startsWith("SENT\ttrue") == true && receivedCount() == before + 1 &&
            mediaGot?.engineType == "MEDIA_STREAM" && mediaGot.formatId == "137+140" && mediaGot.originalUrl == "https://example.invalid/watch?v=abc", "$mediaSent $mediaGot")
        T.check("jvm -> native media link: no receive progress", a.receiving.value == null, "${a.receiving.value}")
        nativeAccepts.value = 1

        before = receivedCount()
        from = peer.count()
        peer.send("finished", a.selfId, "https://example.invalid/video", finished, "video.mp4", "450000", "150001")
        val sent = peer.waitLine(15_000, from) { it.startsWith("SENT\t") }
        T.check("jvm -> native finished file: jvm onResult true", sent?.startsWith("SENT\ttrue") == true, "$sent")
        T.check("jvm -> native: the native receiver ends Done", (a.receiving.value as? ReceiveState.Done)?.fileName == "video.mp4", "${a.receiving.value}")
        T.check("jvm -> native: display sizes arrive", lastReceived()?.first?.totalBytes == 450_000L && lastReceived()?.first?.downloadedBytes == 150_001L, "${lastReceived()?.first}")
        val got = lastReceived()
        T.check("jvm -> native finished file: manifest + piece", receivedCount() == before + 1 && got?.second?.map { it.substringAfterLast('\\') } == listOf("video.mp4.chunks", "video.mp4.part0"), "${got?.second}")
        if (got != null && got.second.size == 2) {
            T.check("jvm -> native: manifest text", winReadFile(got.second[0])?.decodeToString() == "0 0 150000")
            T.check("jvm -> native: piece SHA-256", sameBytes(finished, got.second[1]))
            T.check("jvm -> native: sender id", got.first.fromDeviceId == jId)
        }

        before = receivedCount()
        from = peer.count()
        peer.send("files", a.selfId, "https://example.invalid/big2", pieces.joinToString("|") { it.first })
        val sent2 = peer.waitLine(15_000, from) { it.startsWith("SENT\t") }
        val got2 = lastReceived()
        T.check("jvm -> native scratch folder: done", sent2?.startsWith("SENT\ttrue") == true && receivedCount() == before + 1 && got2?.second?.size == pieces.size, "$sent2 ${got2?.second}")
        if (got2 != null && got2.second.size == pieces.size) {
            T.check("jvm -> native scratch folder: every SHA-256 equal (Arabic name included)", pieces.withIndex().all { (i, p) ->
                got2.second[i].substringAfterLast('\\') == p.first.substringAfterLast('\\') && sameBytes(p.first, got2.second[i])
            })
        }

        peer.send("unpair", a.selfId)
        val mark3 = TimeSource.Monotonic.markNow()
        while (store.isPaired(jId) && mark3.elapsedNow().inWholeMilliseconds < 5000) delay(25)
        T.check("jvm unpair notice is accepted by native", !store.isPaired(jId))

        from = peer.count()
        peer.send("pair", "127.0.0.1")
        val mark4 = TimeSource.Monotonic.markNow()
        while ((a.pairing.value as? PairingState.Confirm) == null && mark4.elapsedNow().inWholeMilliseconds < 8000) delay(25)
        val aCode2 = (a.pairing.value as? PairingState.Confirm)?.code
        val jState2 = peer.stateUntil(5000) { it.getOrNull(1)?.startsWith("confirm:") == true }
        T.check("jvm -> native pairing: same code", aCode2 != null && jState2.getOrNull(1)?.split(':')?.getOrNull(1) == aCode2, "$aCode2 / ${jState2.getOrNull(1)}")
        peer.send("confirm")
        delay(300)
        from = peer.count()
        peer.send("link", a.selfId, "https://example.invalid/from-jvm")
        delay(800)
        before = receivedCount()
        a.confirmPairing()
        val sent3 = peer.waitLine(10_000, from) { it.startsWith("SENT\t") }
        T.check("jvm sender waited on native 'pending', then sent", sent3?.startsWith("SENT\ttrue") == true && receivedCount() == before + 1 && lastReceived()?.first?.url == "https://example.invalid/from-jvm", "$sent3")

        a.unpair(jId)
        val st2 = peer.stateUntil(5000) { it.getOrNull(2)?.isEmpty() == true }
        T.check("native unpair notice is accepted by jvm", st2.getOrNull(2)?.isEmpty() == true, "$st2")

        a.setVisible(false)
        delay(11_000)
        val st3 = peer.state()
        T.check("hidden native device: jvm drops it", st3.getOrNull(3)?.contains(a.selfId) == false, "${st3.getOrNull(3)}")
        T.check("hidden native device: its probes are answered by the jvm (still lists it)", a.discovered.value.any { it.id == jId && it.host == "127.0.0.3" && it.port == tcp }, "${a.discovered.value}")
        T.check("no jvm exception printed", peer.find { it.contains("Exception") } == null, "${peer.find { it.contains("Exception") }}")
    } finally {
        acceptor.cancel()
        try {
            peer.send("quit")
        } catch (_: Throwable) {
        }
        if (!process.waitFor(5000)) process.kill()
        process.waitFor(3000)
        withTimeoutOrNull(3000) { reader.join() }
        process.close()
        a.shutdown()
    }
}
