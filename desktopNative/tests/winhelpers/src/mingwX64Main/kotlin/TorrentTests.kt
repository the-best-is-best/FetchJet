@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import com.michelle.raouf.fetch_jet.platform.torrent.FJT_SI_DHT_RUNNING
import com.michelle.raouf.fetch_jet.platform.torrent.FJT_SI_LISTEN_PORT
import com.michelle.raouf.fetch_jet.platform.torrent.FJT_SI_LOCAL_ONLY
import com.michelle.raouf.fetch_jet.platform.torrent.FJT_SI_TORRENTS
import com.michelle.raouf.fetch_jet.platform.torrent.FjtEngine
import com.michelle.raouf.fetch_jet.platform.torrent.FjtMeta
import com.michelle.raouf.fetch_jet.platform.torrent.withLock
import com.michelle.raouf.fetch_jet.platform.desktop.OsLineSplitter
import com.michelle.raouf.fetch_jet.platform.win.WinProcess
import com.michelle.raouf.fetch_jet.platform.win.WinTorrentHost
import com.michelle.raouf.fetch_jet.platform.win.toHexString
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winDeleteRecursively
import com.michelle.raouf.fetch_jet.platform.win.winFileInfo
import com.michelle.raouf.fetch_jet.platform.win.winMemoryUse
import com.michelle.raouf.fetch_jet.platform.win.winReadFile
import com.michelle.raouf.fetch_jet.platform.win.winSetEnv
import com.michelle.raouf.fetch_jet.platform.win.winSha256OfFile
import com.michelle.raouf.fetch_jet.platform.win.winTorrentLibrary
import com.michelle.raouf.fetch_jet.platform.win.winUptimeMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private class Seeder(val process: WinProcess, private val scope: CoroutineScope) {
    private val lock = WinTorrentHost.newLock()
    private val all = ArrayList<String>()

    val lines: List<String> get() = lock.withLock { all.toList() }

    fun start() {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            val splitter = OsLineSplitter { line -> lock.withLock { all.add(line) } }
            while (true) {
                val n = process.readStdout(buffer)
                if (n <= 0) break
                splitter.feed(buffer, n)
            }
            splitter.finish()
        }
    }

    suspend fun ready(timeoutMs: Long): Map<String, String>? {
        val end = winUptimeMs() + timeoutMs
        while (winUptimeMs() < end) {
            lines.firstOrNull { it.startsWith("ERROR") }?.let {
                T.info("seeder: $it")
                return null
            }
            val line = lines.firstOrNull { it.startsWith("READY ") }
            if (line != null) {
                return line.removePrefix("READY ").split(' ').mapNotNull {
                    val cut = it.indexOf('=')
                    if (cut > 0) it.substring(0, cut) to it.substring(cut + 1) else null
                }.toMap()
            }
            delay(100)
        }
        return null
    }

    fun stop() {
        process.kill()
        process.close()
    }
}

private suspend fun waitUntil(timeoutMs: Long, stepMs: Long = 200, condition: () -> Boolean): Boolean {
    val end = winUptimeMs() + timeoutMs
    while (winUptimeMs() < end) {
        if (condition()) return true
        delay(stepMs)
    }
    return condition()
}

private fun mb(bytes: Long?): String = if (bytes == null) "?" else ((bytes * 10 / (1024 * 1024)) / 10.0).toString() + " MB"

private fun sha(path: String): String? = winSha256OfFile(path)?.toHexString()

private fun normal(path: String) = path.replace('\\', '/')

private fun allPriorities(meta: FjtMeta, skip: Set<Int> = emptySet()): ByteArray =
    FjtEngine.priorities(meta.numFiles, meta.files.map { it.index }.filter { it !in skip }.toSet(), emptyMap())

private fun sameFiles(meta: FjtMeta, seedRoot: String, downloadRoot: String, only: (String) -> Boolean = { true }): Pair<Boolean, String> {
    val bad = ArrayList<String>()
    var checked = 0
    for (f in meta.files) {
        val rel = normal(f.path)
        if (!only(rel)) continue
        checked++
        val a = sha("$seedRoot\\${rel.replace('/', '\\')}")
        val b = sha("$downloadRoot\\${rel.replace('/', '\\')}")
        if (a == null || a != b) bad.add(rel)
    }
    return (bad.isEmpty() && checked > 0) to "checked=$checked bad=$bad"
}

private fun magnetEncode(text: String): String = buildString {
    for (c in text) {
        if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') append(c) else append('%').append(c.code.toString(16).padStart(2, '0').uppercase())
    }
}

fun torrentTests(work: String, options: List<String>) = runBlocking {
    winSetEnv("FETCHJET_TORRENT_LOCAL_ONLY", "1")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val root = "$work\\torrent"
    winDeleteRecursively(root)
    winCreateDirectories(root)
    val seeders = ArrayList<Seeder>()
    val idleMs = 3_000L
    val engine = FjtEngine(winTorrentLibrary, idleCloseMs = idleMs)
    if ("tcp" in options) engine.proxyText = "enable_outgoing_utp=false\nenable_incoming_utp=false"
    val memory = LinkedHashMap<String, Long?>()
    memory["before load"] = winMemoryUse()?.privateBytes

    fun startSeeder(vararg args: String): Seeder? {
        val dll = winTorrentLibrary.candidates().firstOrNull() ?: return null
        val exe = options.firstOrNull { it.startsWith("seed=") }?.removePrefix("seed=")
            ?: (dll.substringBeforeLast('\\') + "\\fjtseed.exe")
        if (winFileInfo(exe) == null) {
            T.info("seeder not found: $exe")
            return null
        }
        val process = WinProcess.start(listOf(exe) + args, environment = mapOf("FETCHJET_TORRENT_LOCAL_ONLY" to "1"), mergeStderr = true)
        return Seeder(process, scope).also {
            it.start()
            seeders.add(it)
        }
    }

    try {
        val loaded = winTorrentLibrary.load()
        val api = loaded.getOrNull()
        T.check("fjtorrent.dll loads with ABI 1 and all 34 exports", api != null && api.abi == 1, loaded.exceptionOrNull()?.message ?: "path=${api?.path}")
        if (api == null) return@runBlocking
        T.check("libtorrent version is 2.1.2.0", api.version == "2.1.2.0", api.version)
        T.check("the 7 optional details exports are there", api.hasDetails && engine.hasDetails)
        memory["DLL loaded"] = winMemoryUse()?.privateBytes
        T.check("a session is not started by loading", !engine.isSessionRunning)

        var badParse = false
        try {
            engine.parse("not a torrent".encodeToByteArray())
        } catch (e: Exception) {
            badParse = true
        }
        T.check("garbage bytes are refused by fjt_meta_parse", badParse)

        val seed = startSeeder(
            "dir=$root\\seed", "out=$root\\pack.torrent", "name=pack",
            "files=a.bin:1500000,sub/b.bin:24000000,c.txt:70000", "piece=262144", "swarm=0"
        )
        val ready = seed?.ready(60_000)
        T.check("seeder is ready on 127.0.0.1", ready != null, ready?.toString() ?: seed?.lines?.takeLast(5).toString())
        if (seed == null || ready == null) return@runBlocking
        val torrentBytes = winReadFile("$root\\pack.torrent") ?: ByteArray(0)
        val meta = engine.parse(torrentBytes)
        val indices = meta.files.map { it.index }
        val paths = meta.files.map { normal(it.path) }
        T.info("meta: name=${meta.name} numFiles=${meta.numFiles} entries=$indices paths=$paths total=${meta.totalSize} hash=${meta.infoHash}")
        T.check("metadata: name, info hash and three files", meta.name == "pack" && meta.infoHash == ready["hash"] && meta.files.size == 3)
        T.check("pad files are left out: numFiles > entries and indices have gaps", meta.numFiles > meta.files.size && indices != indices.indices.map { indices.first() + it }, "numFiles=${meta.numFiles} indices=$indices")
        T.check("paths keep the folder (pack/sub/b.bin)", paths.contains("pack/sub/b.bin"), paths.toString())
        T.check("total size counts pad files (desktop rule)", meta.totalSize >= meta.files.sumOf { it.size })

        val cIndex = meta.files.first { normal(it.path).endsWith("c.txt") }.index
        val dl1 = "$root\\dl1"
        winCreateDirectories(dl1)
        val limit = 2_000_000L
        engine.add("t1", torrentBytes, dl1, allPriorities(meta, setOf(cIndex)), disableUpload = false, downloadLimit = limit, uploadLimit = 0)
        T.check("add starts a session", engine.isSessionRunning)
        val info = engine.sessionInfo()
        T.check("session listens on a port, DHT is off, local-only is on", info != null && info[FJT_SI_LISTEN_PORT] > 0 && info[FJT_SI_DHT_RUNNING] == 0L && info[FJT_SI_LOCAL_ONLY] == 1L, info?.toList().toString())
        val settingsCheck = FjtEngine.sessionSettings(1).lines().filter { !it.startsWith("listen_interfaces=") }.joinToString("\n")
        val unknown = engine.applySettings(settingsCheck)
        T.check("every FetchJet session setting name is known to libtorrent 2.1.2", unknown == 0, "unknown=$unknown")
        T.check("progress is not null right after add", engine.status("t1") != null)

        val started = waitUntil(30_000) { (engine.status("t1")?.totalWantedDone ?: 0L) > 1_000_000L }
        T.check("download starts from the local seeder", started, engine.debugText("t1") ?: "")
        seed.lines.filter { it.startsWith("ALERT") }.take(12).forEach { T.info("seeder $it") }
        val t0 = winUptimeMs()
        val samples = ArrayList<Pair<Long, Long>>()
        var finishedEarly = false
        repeat(36) {
            delay(250)
            val st = engine.status("t1")
            if (st != null) samples.add((winUptimeMs() - t0) to st.totalWantedDone)
            if (st?.isFinished == true) finishedEarly = true
        }
        memory["downloading"] = winMemoryUse()?.privateBytes
        T.info("rate samples (ms:bytes): " + samples.joinToString(" ") { "${it.first}:${it.second}" })
        val steps = samples.zipWithNext().filter { (a, b) -> b.second > a.second }.map { it.second }
        val first = steps.firstOrNull()
        val last = steps.lastOrNull()
        val name = "2 MB/s per-torrent limit holds (within 30 %, measured between quota steps)"
        if (first != null && last != null && steps.size >= 3 && last.first - first.first >= 4_000L && !finishedEarly) {
            val rate = (last.second - first.second) * 1000 / (last.first - first.first)
            val all = (samples.last().second - samples.first().second) * 1000 / maxOf(samples.last().first - samples.first().first, 1)
            T.check(name, rate in (limit * 7 / 10)..(limit * 13 / 10), "between steps $rate B/s over ${last.first - first.first} ms, whole window $all B/s")
        } else {
            T.check(name, false, "finished too early or too few steps: ${steps.size}")
        }
        val announced = seed.lines.filter { it.startsWith("ANNOUNCE") }
        T.check("our announce reached the local tracker", announced.any { it.contains("hash=${meta.infoHash}") }, announced.take(3).toString())
        val debug = engine.debugText("t1") ?: ""
        T.info("debug: $debug")
        T.check("the non-local tracker 127.0.0.2 was dropped by the local-only filter", debug.contains("trackers:") && !debug.contains("127.0.0.2") && debug.contains("localOnly=true"))

        val trackerUrl = "http://127.0.0.1:${ready["tracker"]}/announce"
        val trackerWorks = waitUntil(15_000) { engine.trackers("t1")?.any { it.url == trackerUrl && it.state == "working" } == true }
        T.check("trackers: the seeder's tracker is listed as working", trackerWorks, engine.trackers("t1")?.joinToString { "${it.url}=${it.state}:${it.message}" } ?: "null")
        val peerList = engine.peers("t1")
        T.check("peers: the local seeder is listed as a seed on 127.0.0.1", peerList != null && peerList.any { it.address.startsWith("127.0.0.1:") && it.isSeed }, peerList?.joinToString { "${it.address} ${it.client} ${it.downloadRate} ${it.progressPermille} ${it.isSeed}" } ?: "null")
        val secondUrl = "http://127.0.0.1:${ready["tracker"]}/announce2"
        val refusedScheme = engine.addTracker("t1", "ftp://127.0.0.1/announce")
        val refusedRemote = engine.addTracker("t1", "http://10.1.2.3:6969/announce")
        T.check("add tracker: a bad scheme and (local-only) a non-local url are refused", !refusedScheme && !refusedRemote, "scheme=$refusedScheme remote=$refusedRemote")
        val addedTracker = engine.addTracker("t1", secondUrl)
        val secondWorks = waitUntil(20_000) { engine.trackers("t1")?.any { it.url == secondUrl && it.state == "working" } == true }
        val trackerUrls = engine.trackers("t1")?.map { it.url } ?: emptyList()
        T.check("add tracker: a second local tracker is added and announced to", addedTracker && secondWorks && trackerUrls.count { it == secondUrl } == 1 && trackerUrls.none { it.contains("10.1.2.3") }, engine.trackers("t1")?.joinToString { "${it.url}=${it.state}:${it.message}" } ?: "null")
        engine.addTracker("t1", secondUrl)
        T.check("add tracker: the same url twice stays one entry", engine.trackers("t1")?.count { it.url == secondUrl } == 1)
        val link = engine.magnet("t1") ?: ""
        T.check("magnet link starts with magnet:?xt=urn:btih: and has the info hash", link.startsWith("magnet:?xt=urn:btih:${meta.infoHash}", ignoreCase = true), link.take(200))
        val pieceLength = 262_144L
        val expectedPieces = ((meta.totalSize + pieceLength - 1) / pieceLength).toInt()
        val pc = engine.pieces("t1")
        T.check("pieces: count and piece length match the torrent, some are done, first/last is off", pc != null && pc.total == expectedPieces && pc.pieceLength == pieceLength && pc.have in 1 until pc.total && !pc.firstLast, "total=${pc?.total} expected=$expectedPieces have=${pc?.have} length=${pc?.pieceLength} firstLast=${pc?.firstLast}")
        val firstLastOn = engine.setFirstLastPiecesFirst("t1", true)
        T.check("first/last pieces first: set and reported", firstLastOn && engine.pieces("t1")?.firstLast == true)
        val shown = engine.details("t1")
        T.check("details: trackers, peers, pieces, magnet and the flag in one read", shown != null && shown.trackers.size >= 2 && shown.pieceCount == expectedPieces && shown.pieceLength == pieceLength && shown.magnet == link && shown.firstLast && shown.downloadedBytes > 0L, "trackers=${shown?.trackers?.size} peers=${shown?.peers?.size} pieces=${shown?.piecesDone}/${shown?.pieceCount} up=${shown?.uploadedBytes}")
        T.check("details of an unknown task are null", engine.details("nope") == null && engine.trackers("nope") == null && !engine.recheck("nope"))

        engine.pause("t1")
        delay(1_500)
        val p1 = engine.status("t1")
        delay(2_000)
        val p2 = engine.status("t1")
        T.check("pause: flag set and bytes stop", p1 != null && p2 != null && p2.isPaused && p2.totalWantedDone - p1.totalWantedDone <= 65_536, "p1=${p1?.totalWantedDone} p2=${p2?.totalWantedDone}")
        val pausedAt = p2?.totalWantedDone ?: 0L

        engine.add("t1", torrentBytes, dl1, allPriorities(meta, setOf(cIndex)), disableUpload = false, downloadLimit = 0, uploadLimit = 0)
        var lowest = Long.MAX_VALUE
        val moved = waitUntil(10_000) {
            val d = engine.status("t1")?.totalWantedDone ?: 0L
            lowest = minOf(lowest, d)
            d > pausedAt + 1_000_000L || engine.status("t1")?.isFinished == true
        }
        T.check("re-add of a known task resumes it, never below the paused value", moved && lowest >= pausedAt, "paused=$pausedAt lowest=$lowest")

        var peak = 0L
        val done = waitUntil(90_000) {
            peak = maxOf(peak, winMemoryUse()?.privateBytes ?: 0L)
            engine.status("t1")?.isFinished == true
        }
        memory["peak while downloading"] = peak
        val sDone = engine.status("t1")
        T.check("unlimited: download finishes", done, "${sDone?.stateText} ${sDone?.totalWantedDone}/${sDone?.totalWanted}")
        val (same, detail) = sameFiles(meta, "$root\\seed", dl1) { !it.endsWith("c.txt") }
        T.check("SHA-256 of a.bin and sub/b.bin equal the seeder's", same, detail)
        val cProgress = sDone?.fileProgress?.getOrNull(cIndex)
        T.check("file left out (priority 0) is not downloaded", cProgress != null && cProgress < 70_000L, "progress[$cIndex]=$cProgress of ${sDone?.fileProgress?.size}")
        T.check("fileProgress is indexed by libtorrent index (length numFiles)", sDone?.fileProgress?.size == meta.numFiles)

        val seedLinesBefore = seed.lines.size
        T.info("finished: ${engine.debugText("t1")}")
        delay(3_000)
        T.info("finished +3 s: ${engine.debugText("t1")}")
        engine.setFileSelection("t1", meta.files.map { it.index }.toSet(), emptyMap())
        val selStart = winUptimeMs()
        var lastTrace = selStart
        val cDone = waitUntil(100_000) {
            val now = winUptimeMs()
            if (now - lastTrace >= 2_000L) {
                lastTrace = now
                T.info("selection +${now - selStart} ms: ${engine.debugText("t1")}")
            }
            (engine.status("t1")?.fileProgress?.getOrNull(cIndex) ?: 0L) >= 70_000L && engine.status("t1")?.isFinished == true
        }
        seed.lines.drop(seedLinesBefore).take(20).forEach { T.info("seeder after selection: $it") }
        T.check("adding a file by selection downloads it", cDone && sameFiles(meta, "$root\\seed", dl1) { it.endsWith("c.txt") }.first, "after ${winUptimeMs() - selStart} ms")

        T.check("first/last pieces first is still reported after a selection change", engine.pieces("t1")?.firstLast == true)
        val firstLastOff = engine.setFirstLastPiecesFirst("t1", false)
        val pcAll = engine.pieces("t1")
        T.check("first/last pieces first: turned off; all pieces are done", firstLastOff && pcAll != null && !pcAll.firstLast && pcAll.have == pcAll.total, "have=${pcAll?.have} total=${pcAll?.total} firstLast=${pcAll?.firstLast}")
        val recheckStart = winUptimeMs()
        val rechecking = engine.recheck("t1")
        val leftFinished = waitUntil(5_000, stepMs = 20) { engine.status("t1")?.isFinished != true }
        val recheckDone = waitUntil(60_000) {
            val p = engine.pieces("t1")
            engine.status("t1")?.isFinished == true && p != null && p.have == p.total
        }
        T.check("recheck: the files are checked again and the torrent ends finished", rechecking && recheckDone, "left finished=$leftFinished, done after ${winUptimeMs() - recheckStart} ms, state=${engine.status("t1")?.stateText}")
        T.check("recheck keeps the files (SHA-256 still equal)", sameFiles(meta, "$root\\seed", dl1).first)

        engine.add("t2", torrentBytes, dl1, allPriorities(meta), disableUpload = true, downloadLimit = 0, uploadLimit = 0)
        val st2 = engine.status("t2")
        T.check("a second task id shares the handle; download-only caps upload at 32768", st2 != null && st2.uploadLimit == 32_768L, "upLimit=${st2?.uploadLimit}")
        T.check("one torrent in the session for two task ids", engine.sessionInfo()?.get(FJT_SI_TORRENTS) == 1L)
        engine.remove("t2", false)
        T.check("removing one id keeps the other", engine.status("t1") != null && engine.status("t2") == null)

        val resume = engine.saveResume("t1", 5_000)
        T.check("resume data is saved", resume != null && resume.size > 100, "size=${resume?.size}")

        engine.remove("t1", false)
        T.check("no task left after remove", engine.taskCount == 0)
        val closed = waitUntil(idleMs + 5_000) { !engine.isSessionRunning }
        T.check("the session is destroyed after the idle time (${idleMs} ms)", closed)
        delay(500)
        memory["after destroy"] = winMemoryUse()?.privateBytes

        if (resume != null) {
            val tStart = winUptimeMs()
            engine.add("t3", torrentBytes, dl1, allPriorities(meta), disableUpload = false, downloadLimit = 0, uploadLimit = 0, resume = resume)
            val quick = waitUntil(15_000) { engine.status("t3")?.isFinished == true }
            T.check("restore from resume data: finished without downloading again", quick, "after ${winUptimeMs() - tStart} ms, state=${engine.status("t3")?.stateText}")
            engine.remove("t3", false)
        }

        val tracker = ready["tracker"] ?: "0"
        val magnet = "magnet:?xt=urn:btih:${meta.infoHash}&dn=pack&tr=" + magnetEncode("http://127.0.0.1:$tracker/announce")
        val mStart = winUptimeMs()
        val fromMagnet = try {
            engine.fetchMagnet(magnet, "$root\\scratch", 30_000)
        } catch (e: Exception) {
            T.info("magnet: $e")
            null
        }
        T.check("magnet with tr= gives the metadata", fromMagnet != null && fromMagnet.numFiles == meta.numFiles && fromMagnet.files.map { it.index } == indices, "in ${winUptimeMs() - mStart} ms")
        if (fromMagnet != null) {
            val reparsed = engine.parse(fromMagnet.torrentBytes)
            T.check("the generated .torrent parses to the same hash", reparsed.infoHash == meta.infoHash)
            val dl2 = "$root\\dl2"
            winCreateDirectories(dl2)
            engine.add("m1", fromMagnet.torrentBytes, dl2, allPriorities(fromMagnet), disableUpload = false, downloadLimit = 0, uploadLimit = 0)
            val mDone = waitUntil(90_000) { engine.status("m1")?.isFinished == true }
            val mSame = sameFiles(meta, "$root\\seed", dl2)
            T.check("download from the generated bytes completes with the same files", mDone && mSame.first, "${mSame.second} ${engine.debugText("m1")}")
            engine.remove("m1", false)
        }

        val web = startSeeder("dir=$root\\webseed", "out=$root\\web.torrent", "name=webpack", "files=w1.bin:3000000,w2.bin:500000", "piece=65536", "peer=0", "web=1", "v1=1")
        val webReady = web?.ready(60_000)
        T.check("web-seed server is ready", webReady != null)
        if (web != null && webReady != null) {
            val webBytes = winReadFile("$root\\web.torrent") ?: ByteArray(0)
            val webMeta = engine.parse(webBytes)
            val dl3 = "$root\\dl3"
            winCreateDirectories(dl3)
            engine.add("w1", webBytes, dl3, allPriorities(webMeta), disableUpload = false, downloadLimit = 0, uploadLimit = 0)
            val wDone = waitUntil(60_000) { engine.status("w1")?.isFinished == true }
            val gets = web.lines.count { it.startsWith("GET /web/") }
            T.check("web-seed-only torrent downloads over HTTP", wDone && gets > 0 && sameFiles(webMeta, "$root\\webseed", dl3).first, "gets=$gets ${engine.debugText("w1")}")
            engine.remove("w1", false)
        }

        engine.shutdown()
        T.check("shutdown closes the session", !engine.isSessionRunning)
        delay(500)
        memory["after shutdown"] = winMemoryUse()?.privateBytes
    } catch (e: Throwable) {
        T.check("torrent tests ran without an exception", false, e.toString())
    } finally {
        engine.shutdown()
        seeders.forEach { it.stop() }
        T.info("private bytes: " + memory.entries.joinToString(", ") { "${it.key} ${mb(it.value)}" })
    }
}
