@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.torrent

import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FjtEngine(val library: FjtLibrary, private val idleCloseMs: Long = defaultIdleCloseMs(library.host)) {

    private val host: FjtHost get() = library.host

    private class TaskSlot(var numFiles: Int) {
        var lastDebugMs: Long = 0L
    }

    @Volatile
    var verbose: Boolean = false

    private val gate = library.host.newLock()
    private val lifecycle = library.host.newLock()
    private var session: COpaquePointer? = null
    private var inFlight = 0
    private var holds = 0
    private var epoch = 0L
    private var proxy = ""
    private val tasks = HashMap<String, TaskSlot>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isSupported: Boolean get() = library.isAvailable

    val isSessionRunning: Boolean get() = gate.withLock { session != null }

    val taskCount: Int get() = gate.withLock { tasks.size }

    var proxyText: String
        get() = gate.withLock { proxy }
        set(value) {
            gate.withLock { proxy = value }
            if (value.isNotEmpty()) call { api, s -> api.applySettings(s, value) }
        }

    fun load(): Result<FjtApi> = library.load()

    private fun api(): FjtApi = load().getOrElse { throw it }

    private inline fun <T> call(block: (FjtApi, COpaquePointer) -> T): T? {
        val api = library.api ?: return null
        val s = gate.withLock {
            val current = session
            if (current != null) inFlight++
            current
        } ?: return null
        try {
            return block(api, s)
        } finally {
            gate.withLock { inFlight-- }
        }
    }

    private fun hold() {
        gate.withLock {
            holds++
            epoch++
        }
    }

    private fun release() {
        gate.withLock { holds-- }
        scheduleIdleClose()
    }

    private fun ensureSession(api: FjtApi): COpaquePointer = lifecycle.withLock {
        val existing = gate.withLock { session }
        if (existing != null) return@withLock existing
        val created = api.sessionCreate(sessionSettings(20000 + Random.nextInt(40000)))
        val proxyLines = gate.withLock { proxy }
        if (proxyLines.isNotEmpty()) api.applySettings(created, proxyLines)
        gate.withLock {
            session = created
            epoch++
        }
        created
    }

    private fun waitForCalls() {
        while (gate.withLock { inFlight } > 0) host.sleepMs(2)
    }

    private fun scheduleIdleClose() {
        val mark = gate.withLock {
            if (session == null || tasks.isNotEmpty() || holds > 0) null else ++epoch
        } ?: return
        scope.launch {
            delay(idleCloseMs)
            closeIfIdle(mark)
        }
    }

    private fun closeIfIdle(mark: Long) {
        lifecycle.withLock {
            val s = gate.withLock {
                if (epoch != mark || tasks.isNotEmpty() || holds > 0) {
                    null
                } else {
                    val current = session
                    session = null
                    current
                }
            }
            if (s != null) {
                waitForCalls()
                library.api?.sessionDestroy(s, DESTROY_TIMEOUT_MS)
                if (verbose) println("[torrent] session closed (idle)")
            }
        }
    }

    fun shutdown() {
        lifecycle.withLock {
            val s = gate.withLock {
                tasks.clear()
                epoch++
                val current = session
                session = null
                current
            }
            if (s != null) {
                waitForCalls()
                library.api?.sessionDestroy(s, DESTROY_TIMEOUT_MS)
            }
        }
    }

    fun parse(torrent: ByteArray): FjtMeta = api().parse(torrent)

    suspend fun fetchMagnet(uri: String, scratchDir: String, timeoutMs: Long = MAGNET_TIMEOUT_MS): FjtMeta? {
        val api = api()
        hold()
        var job: COpaquePointer? = null
        try {
            ensureSession(api)
            host.createDirectories(scratchDir)
            val link = if (localOnly(host)) uri else withPublicTrackers(uri)
            job = call { a, s -> a.magnetStart(s, link, scratchDir) } ?: throw FjtException("The torrent session was closed")
            val deadline = host.uptimeMs() + timeoutMs
            while (true) {
                val (code, error) = api.magnetPoll(job)
                when {
                    code == FJT_OK -> return api.magnetTake(job) ?: throw FjtException("The magnet metadata could not be read")
                    code == FJT_PENDING -> {
                        if (host.uptimeMs() >= deadline) return null
                        delay(MAGNET_POLL_MS)
                    }

                    else -> throw FjtException(error.ifBlank { "The magnet link failed" })
                }
            }
        } finally {
            job?.let { api.magnetCancel(it) }
            release()
        }
    }

    fun add(
        taskId: String,
        torrent: ByteArray,
        savePath: String,
        priorities: ByteArray,
        disableUpload: Boolean,
        downloadLimit: Long,
        uploadLimit: Long,
        resume: ByteArray? = null,
    ) {
        val api = api()
        val numFiles = if (priorities.isNotEmpty()) priorities.size else api.parse(torrent).numFiles
        hold()
        try {
            ensureSession(api)
            val trackers = if (localOnly(host)) "" else PUBLIC_TRACKERS.joinToString("\n")
            val flags = if (disableUpload) FJT_ADD_DISABLE_UPLOAD else 0
            val (code, error) = call { a, s ->
                a.add(s, taskId, torrent, resume, savePath, priorities, flags, downloadLimit, uploadLimit, trackers)
            } ?: throw FjtException("The torrent session was closed")
            if (code != FJT_OK) throw FjtException(error.ifBlank { "libtorrent did not accept the torrent" })
            gate.withLock {
                val slot = tasks[taskId]
                if (slot == null) tasks[taskId] = TaskSlot(numFiles) else slot.numFiles = numFiles
                epoch++
            }
        } finally {
            release()
        }
    }

    fun pause(taskId: String) {
        call { api, s -> api.pause(s, taskId) }
    }

    fun resume(taskId: String) {
        call { api, s -> api.resume(s, taskId) }
    }

    fun remove(taskId: String, deleteFiles: Boolean) {
        val known = gate.withLock {
            val had = tasks.remove(taskId) != null
            if (had) epoch++
            had
        }
        if (known) call { api, s -> api.remove(s, taskId, deleteFiles) }
        scheduleIdleClose()
    }

    fun setPriorities(taskId: String, priorities: ByteArray) {
        call { api, s -> api.setPriorities(s, taskId, priorities) }
    }

    fun setFileSelection(taskId: String, selected: Set<Int>, priorities: Map<Int, Int>) {
        val numFiles = gate.withLock { tasks[taskId]?.numFiles } ?: return
        setPriorities(taskId, priorities(numFiles, selected, priorities))
    }

    fun setLimits(taskId: String, downloadLimit: Long, uploadLimit: Long) {
        call { api, s -> api.setLimits(s, taskId, downloadLimit, uploadLimit) }
    }

    fun status(taskId: String): FjtStatus? {
        val capacity = gate.withLock { tasks[taskId]?.numFiles } ?: return null
        var (code, status) = call { api, s -> api.status(s, taskId, capacity) } ?: return null
        if (code > capacity) {
            gate.withLock { tasks[taskId]?.numFiles = code }
            val retry = call { api, s -> api.status(s, taskId, code) } ?: return null
            status = retry.second
        }
        if (status != null && verbose) {
            val now = host.uptimeMs()
            val due = gate.withLock {
                val slot = tasks[taskId]
                if (slot != null && now - slot.lastDebugMs >= DEBUG_EVERY_MS) {
                    slot.lastDebugMs = now
                    true
                } else {
                    false
                }
            }
            if (due) debugText(taskId)?.let { println("[torrent $taskId] $it") }
        }
        return status
    }

    fun debugText(taskId: String): String? = call { api, s -> api.debugText(s, taskId) }

    val hasDetails: Boolean get() = library.api?.hasDetails == true

    fun trackers(taskId: String): List<FjtTracker>? = call { api, s -> api.trackers(s, taskId) }

    fun peers(taskId: String): List<FjtPeer>? = call { api, s -> api.peers(s, taskId) }

    fun magnet(taskId: String): String? = call { api, s -> api.magnet(s, taskId) }

    fun pieces(taskId: String): FjtPieces? = call { api, s -> api.pieces(s, taskId) }

    fun addTracker(taskId: String, url: String): Boolean = call { api, s -> api.addTracker(s, taskId, url.trim()) } == FJT_OK

    fun recheck(taskId: String): Boolean = call { api, s -> api.recheck(s, taskId) } == FJT_OK

    fun setFirstLastPiecesFirst(taskId: String, on: Boolean): Boolean = call { api, s -> api.setFirstLast(s, taskId, on) } == FJT_OK

    fun details(taskId: String): FjtDetails? {
        if (!hasDetails) return null
        val status = status(taskId) ?: return null
        val pieces = pieces(taskId)
        return FjtDetails(
            trackers = trackers(taskId).orEmpty(),
            peers = peers(taskId).orEmpty(),
            uploadedBytes = status.totalUpload,
            downloadedBytes = status.totalWantedDone,
            pieceCount = pieces?.total ?: 0,
            piecesDone = pieces?.have ?: 0,
            pieceLength = pieces?.pieceLength ?: 0L,
            magnet = magnet(taskId).orEmpty(),
            firstLast = pieces?.firstLast == true,
        )
    }

    fun saveResume(taskId: String, timeoutMs: Int = 5_000): ByteArray? = call { api, s -> api.saveResume(s, taskId, timeoutMs) }

    fun sessionInfo(): LongArray? = call { api, s -> api.sessionInfo(s) }

    fun applySettings(text: String): Int? = call { api, s -> api.applySettings(s, text) }

    companion object {
        const val MAGNET_TIMEOUT_MS = 60_000L
        const val MAGNET_POLL_MS = 300L
        const val DEBUG_EVERY_MS = 15_000L
        const val DESTROY_TIMEOUT_MS = 3_000
        const val DEFAULT_IDLE_CLOSE_MS = 60_000L

        val PUBLIC_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.dler.org:6969/announce",
        )

        fun localOnly(host: FjtHost): Boolean = host.env("FETCHJET_TORRENT_LOCAL_ONLY") == "1"

        fun defaultIdleCloseMs(host: FjtHost): Long =
            host.env("FETCHJET_TORRENT_IDLE_MS")?.trim()?.toLongOrNull()?.takeIf { it >= 0L } ?: DEFAULT_IDLE_CLOSE_MS

        fun sessionSettings(port: Int): String = listOf(
            "enable_dht=true",
            "enable_lsd=true",
            "enable_upnp=true",
            "enable_natpmp=true",
            "dht_bootstrap_nodes=dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.aelitis.com:6881",
            "listen_interfaces=0.0.0.0:$port,[::]:$port",
            "download_rate_limit=0",
            "upload_rate_limit=0",
            "connections_limit=800",
            "connection_speed=250",
            "torrent_connect_boost=100",
            "max_peerlist_size=4000",
            "peer_connect_timeout=10",
            "request_timeout=20",
            "active_downloads=10",
            "active_seeds=5",
            "active_limit=20",
            "unchoke_slots_limit=16",
            "send_buffer_watermark=3145728",
            "send_buffer_low_watermark=524288",
            "max_queued_disk_bytes=16777216",
            "aio_threads=8",
            "mixed_mode_algorithm=0",
            "enable_outgoing_utp=true",
            "enable_incoming_utp=true",
            "announce_to_all_trackers=true",
            "announce_to_all_tiers=true",
            "prefer_udp_trackers=true",
            "max_web_seed_connections=8",
            "urlseed_wait_retry=10",
            "urlseed_pipeline_size=10",
            "max_metadata_size=2097152",
        ).joinToString("\n")

        fun proxySettings(kind: String, host: String, port: Int, user: String, password: String): String {
            val on = kind != "none" && host.isNotBlank() && port in 1..65535
            val login = user.isNotEmpty()
            val type = when {
                !on -> 0
                kind == "socks5" && login -> 3
                kind == "socks5" -> 2
                login -> 5
                else -> 4
            }
            val udp = !(on && kind == "http")
            fun clean(text: String) = text.replace("\r", "").replace("\n", "")
            return listOf(
                "proxy_type=$type",
                "proxy_hostname=${clean(host)}",
                "proxy_port=$port",
                "proxy_username=${clean(user)}",
                "proxy_password=${clean(password)}",
                "proxy_hostnames=true",
                "proxy_peer_connections=true",
                "proxy_tracker_connections=true",
                "enable_dht=$udp",
                "enable_lsd=$udp",
                "enable_outgoing_utp=$udp",
                "enable_incoming_utp=$udp",
            ).joinToString("\n")
        }

        fun priorities(numFiles: Int, selected: Set<Int>, priorities: Map<Int, Int>): ByteArray =
            ByteArray(maxOf(numFiles, 0)) { i ->
                val p = priorities[i] ?: 0
                when {
                    i !in selected -> FJT_PRIO_SKIP
                    p > 0 -> FJT_PRIO_TOP
                    p < 0 -> FJT_PRIO_LOW
                    else -> FJT_PRIO_DEFAULT
                }
            }

        fun withPublicTrackers(magnet: String): String {
            if (magnet.contains("&tr=", ignoreCase = true) || magnet.contains("?tr=", ignoreCase = true)) return magnet
            return magnet + PUBLIC_TRACKERS.joinToString("") { "&tr=" + urlEncode(it) }
        }

        private fun urlEncode(text: String): String {
            val out = StringBuilder()
            for (b in text.encodeToByteArray()) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '*') {
                    out.append(ch)
                } else if (ch == ' ') {
                    out.append('+')
                } else {
                    out.append('%').append("0123456789ABCDEF"[c ushr 4]).append("0123456789ABCDEF"[c and 15])
                }
            }
            return out.toString()
        }
    }
}
