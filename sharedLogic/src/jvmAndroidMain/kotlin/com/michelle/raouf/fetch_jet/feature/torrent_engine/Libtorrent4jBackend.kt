package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

/**
 * Real BitTorrent engine on top of libtorrent (via libtorrent4j): DHT, trackers, peer
 * exchange, magnet metadata, per-file priorities, rate limits, seeding control.
 * One libtorrent session is shared by every torrent task.
 */
internal class Libtorrent4jBackend(private val tempDir: File, private val toolsDir: File) :
    TorrentBackend {

    override val isSupported: Boolean get() = TorrentNativeLoader.isAvailable(toolsDir)

    override fun engineStatus(): String = TorrentNativeLoader.status(toolsDir)

    override suspend fun installEngine(): Result<Unit> = withContext(Dispatchers.IO) {
        TorrentNativeLoader.downloadFromMavenCentral(toolsDir).mapCatching {
            TorrentNativeLoader.ensureLoaded(toolsDir).getOrThrow()
            Unit
        }
    }

    /** Loads the native library (once) or throws a readable error. */
    private fun requireNative() {
        TorrentNativeLoader.ensureLoaded(toolsDir).getOrElse { throw it }
    }

    private val session: SessionManager by lazy {
        requireNative()
        SessionManager(false).also { sm ->
            sm.start()
            try {
                // libtorrent's defaults are tuned for a seedbox-style client with many torrents;
                // a download manager wants each torrent to open connections fast and use the
                // whole line. These are the values the popular desktop clients ship with.
                val sp = SettingsPack()
                sp.isEnableDht = true
                sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
                sp.setString(
                    settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                    "dht.libtorrent.org:25401,router.bittorrent.com:6881," +
                            "router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.aelitis.com:6881"
                )
                // A random port (6881 is often taken by another client, and then libtorrent
                // simply has no listen socket: no incoming peers at all).
                val port =
                    20000 + (System.nanoTime() % 40000L).toInt().let { if (it < 0) -it else it }
                sp.setString(
                    settings_pack.string_types.listen_interfaces.swigValue(),
                    "0.0.0.0:$port,[::]:$port"
                )
                // No global caps: the per-torrent limits in setLimits() are the only ones.
                sp.downloadRateLimit(0)
                sp.uploadRateLimit(0)
                sp.connectionsLimit(800)
                // How many connection attempts per second (default 30): the first minute of a
                // download is spent waiting for peers otherwise.
                sp.setInteger(settings_pack.int_types.connection_speed.swigValue(), 250)
                sp.setInteger(settings_pack.int_types.torrent_connect_boost.swigValue(), 100)
                sp.setInteger(settings_pack.int_types.max_peerlist_size.swigValue(), 4000)
                sp.setInteger(settings_pack.int_types.peer_connect_timeout.swigValue(), 10)
                sp.setInteger(settings_pack.int_types.request_timeout.swigValue(), 20)
                // We take the torrents out of libtorrent's queue, but keep sane values anyway.
                sp.activeDownloads(10)
                sp.activeSeeds(5)
                sp.activeLimit(20)
                sp.setInteger(settings_pack.int_types.unchoke_slots_limit.swigValue(), 16)
                // Disk / socket buffers sized for fast lines (defaults assume ~10 Mbit).
                sp.setInteger(
                    settings_pack.int_types.send_buffer_watermark.swigValue(),
                    3 * 1024 * 1024
                )
                sp.setInteger(
                    settings_pack.int_types.send_buffer_low_watermark.swigValue(),
                    512 * 1024
                )
                sp.setInteger(
                    settings_pack.int_types.max_queued_disk_bytes.swigValue(),
                    16 * 1024 * 1024
                )
                sp.setInteger(settings_pack.int_types.aio_threads.swigValue(), 8)
                // uTP is throttled by many routers; let TCP carry the bulk when both are available.
                sp.setInteger(
                    settings_pack.int_types.mixed_mode_algorithm.swigValue(),
                    settings_pack.bandwidth_mixed_algo_t.prefer_tcp.swigValue()
                )
                sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.prefer_udp_trackers.swigValue(), true)
                // Web seeds (archive.org & co. serve the files over HTTP): more parallel
                // connections and a shorter retry after a failed request.
                sp.setInteger(settings_pack.int_types.max_web_seed_connections.swigValue(), 8)
                sp.setInteger(settings_pack.int_types.urlseed_wait_retry.swigValue(), 10)
                sp.setInteger(settings_pack.int_types.urlseed_pipeline_size.swigValue(), 10)
                sm.applySettings(sp)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Magnets copied from a browser often carry no trackers; metadata then depends on DHT
     * alone, which can take minutes. Adding a few public trackers makes it seconds.
     */
    private fun withPublicTrackers(magnet: String): String {
        if (magnet.contains("&tr=", ignoreCase = true) || magnet.contains(
                "?tr=",
                ignoreCase = true
            )
        ) return magnet
        return magnet + PUBLIC_TRACKERS.joinToString("") {
            "&tr=" + java.net.URLEncoder.encode(
                it,
                "UTF-8"
            )
        }
    }

    /** taskId -> libtorrent handle + metadata of the torrent that was added. */
    private class Entry(
        val handle: TorrentHandle,
        val info: TorrentInfo,
        var disableUpload: Boolean
    )

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    // ---- metadata -------------------------------------------------------------------------

    override suspend fun fetchMetadata(
        magnetUri: String?,
        torrentBytes: ByteArray?
    ): TorrentMetadata =
        withContext(Dispatchers.IO) {
            requireNative()
            val bytes: ByteArray = when {
                torrentBytes != null -> torrentBytes
                !magnetUri.isNullOrBlank() -> {
                    if (!tempDir.exists()) tempDir.mkdirs()
                    session.fetchMagnet(
                        withPublicTrackers(magnetUri),
                        MAGNET_TIMEOUT_SECONDS,
                        tempDir
                    )
                        ?: throw IllegalStateException(
                            "Could not fetch torrent metadata for this magnet link (no peers answered within " +
                                    "$MAGNET_TIMEOUT_SECONDS s). Check the link or your network."
                        )
                }

                else -> throw IllegalArgumentException("A magnet link or a .torrent file is required")
            }
            toMetadata(TorrentInfo.bdecode(bytes), bytes)
        }

    private fun toMetadata(ti: TorrentInfo, bytes: ByteArray): TorrentMetadata {
        val fs = ti.files()
        val files = ArrayList<TorrentFileEntry>(fs.numFiles())
        for (i in 0 until fs.numFiles()) {
            if (fs.padFileAt(i)) continue
            // Each segment is sanitized: a crafted torrent cannot point outside the save folder.
            val path = fs.filePath(i).replace('\\', '/')
                .split('/')
                .map { sanitizePathSegment(it) }
                .filter { it.isNotBlank() }
                .joinToString("/")
            files.add(
                TorrentFileEntry(
                    index = i,
                    path = path,
                    size = fs.fileSize(i),
                    isSelected = true
                )
            )
        }
        return TorrentMetadata(
            name = sanitizePathSegment(ti.name()).ifBlank { "Torrent_Download" },
            files = files,
            totalSize = ti.totalSize(),
            infoHash = ti.infoHash().toHex(),
            torrentBytes = bytes
        )
    }

    // ---- lifecycle ------------------------------------------------------------------------

    override suspend fun add(
        taskId: String,
        torrentBytes: ByteArray,
        savePath: String,
        selected: Set<Int>,
        priorities: Map<Int, Int>,
        disableUpload: Boolean,
        downloadLimitBytesPerSec: Long,
        uploadLimitBytesPerSec: Long
    ) = withContext(Dispatchers.IO) {
        requireNative()
        val ti = TorrentInfo.bdecode(torrentBytes)
        val dir = File(savePath).also { it.mkdirs() }
        val prio = buildPriorities(ti, selected, priorities)

        // Already in the session (resume after pause): just re-apply and resume.
        entries[taskId]?.let { existing ->
            if (existing.handle.isValid) {
                existing.handle.prioritizeFiles(prio)
                existing.handle.resume()
                return@withContext
            }
            entries.remove(taskId)
        }

        session.download(ti, dir, null, prio, null, torrent_flags_t())
        // async_add_torrent: wait briefly for the handle to appear.
        var handle: TorrentHandle? = null
        repeat(50) {
            handle = session.find(ti.infoHash())
            if (handle?.isValid == true) return@repeat
            Thread.sleep(100)
        }
        val th = handle?.takeIf { it.isValid }
            ?: throw IllegalStateException("libtorrent did not accept the torrent")
        th.unsetFlags(TorrentFlags.AUTO_MANAGED)   // we decide when it runs, not the queue
        th.prioritizeFiles(prio)
        // A .torrent file often carries one dead tracker (or none): the public ones plus a
        // DHT announce are what get the first peers quickly.
        try {
            val known = th.trackers().map { it.url().lowercase() }.toSet()
            PUBLIC_TRACKERS.filter { it.lowercase() !in known }
                .forEach { th.addTracker(AnnounceEntry(it)) }
            th.forceReannounce()
            th.forceDHTAnnounce()
        } catch (_: Throwable) {
        }
        entries[taskId] = Entry(th, ti, disableUpload)
        setLimits(taskId, downloadLimitBytesPerSec, uploadLimitBytesPerSec)
        th.resume()
    }

    private fun buildPriorities(
        ti: TorrentInfo,
        selected: Set<Int>,
        priorities: Map<Int, Int>
    ): Array<Priority> {
        val n = ti.files().numFiles()
        return Array(n) { i ->
            when {
                i !in selected -> Priority.IGNORE
                (priorities[i] ?: 0) > 0 -> Priority.TOP_PRIORITY
                (priorities[i] ?: 0) < 0 -> Priority.LOW
                else -> Priority.DEFAULT
            }
        }
    }

    override fun pause(taskId: String) {
        entries[taskId]?.handle?.takeIf { it.isValid }?.pause()
    }

    override fun resume(taskId: String) {
        entries[taskId]?.handle?.takeIf { it.isValid }?.resume()
    }

    override fun remove(taskId: String, deleteFiles: Boolean) {
        val e = entries.remove(taskId) ?: return
        if (!e.handle.isValid) return
        try {
            if (deleteFiles) session.remove(
                e.handle,
                SessionHandle.DELETE_FILES
            ) else session.remove(e.handle)
        } catch (_: Throwable) {
        }
    }

    override fun setFileSelection(taskId: String, selected: Set<Int>, priorities: Map<Int, Int>) {
        val e = entries[taskId] ?: return
        if (!e.handle.isValid) return
        e.handle.prioritizeFiles(buildPriorities(e.info, selected, priorities))
    }

    override fun setLimits(taskId: String, downloadBytesPerSec: Long, uploadBytesPerSec: Long) {
        val e = entries[taskId] ?: return
        if (!e.handle.isValid) return
        // libtorrent: 0 = unlimited. "Download only" keeps a small trickle (not 1 B/s): peers
        // that never get a piece back choke us, and the download crawls at a few KB/s.
        e.handle.downloadLimit = downloadBytesPerSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val up = when {
            e.disableUpload -> 32 * 1024
            else -> uploadBytesPerSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
        e.handle.uploadLimit = up
    }

    override fun progress(taskId: String): TorrentProgress? {
        val e = entries[taskId] ?: return null
        if (!e.handle.isValid) return null
        val st: TorrentStatus = e.handle.status()
        val fileProgress = try {
            e.handle.fileProgress().toList()
        } catch (_: Throwable) {
            emptyList()
        }
        val err = st.errorCode()
        val errorText: String? = if (err != null && err.isError) err.message else null
        val flags = st.flags()
        val paused = flags.and_(TorrentFlags.PAUSED).non_zero()
        logDiagnostics(taskId, e, st)
        return TorrentProgress(
            downloadedBytes = st.totalWantedDone(),
            wantedBytes = st.totalWanted(),
            downloadRate = st.downloadPayloadRate().toLong(),
            uploadRate = st.uploadPayloadRate().toLong(),
            peers = st.numPeers(),
            seeds = st.numSeeds(),
            isFinished = st.isFinished,
            isSeeding = st.isSeeding,
            isPaused = paused,
            hasMetadata = st.hasMetadata(),
            fileProgress = fileProgress,
            stateText = when (st.state()) {
                TorrentStatus.State.CHECKING_FILES -> "Checking files"
                TorrentStatus.State.CHECKING_RESUME_DATA -> "Checking resume data"
                TorrentStatus.State.DOWNLOADING_METADATA -> "Fetching metadata"
                TorrentStatus.State.DOWNLOADING -> "Downloading"
                TorrentStatus.State.FINISHED -> "Finished"
                TorrentStatus.State.SEEDING -> "Seeding"
                else -> "Connecting"
            },
            error = errorText
        )
    }

    // One line every ~15 s per torrent in app.log: enough to see why a download is slow
    // (no listen port, dead trackers, DHT off, no web seeds, few connect candidates).
    private val lastDiag = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun logDiagnostics(taskId: String, e: Entry, st: TorrentStatus) {
        if (!com.michelle.raouf.fetch_jet.core.log.AppLog.verbose) return
        val now = System.currentTimeMillis()
        val last = lastDiag[taskId] ?: 0L
        if (now - last < 15_000L) return
        lastDiag[taskId] = now
        try {
            val trackers = try {
                e.handle.trackers().joinToString(" | ") { t ->
                    val eps = t.endpoints()
                    val msg = eps.firstOrNull()?.let { ep ->
                        val ih = ep.infohashV1()
                        val m = ih.message()
                        when {
                            !m.isNullOrBlank() -> m
                            ih.fails() > 0 -> "fails=${ih.fails()}"
                            ih.isWorking -> "ok"
                            ih.updating() -> "updating"
                            else -> "idle"
                        }
                    } ?: "no endpoint"
                    t.url().substringAfter("://").substringBefore("/") + "=" + msg
                }
            } catch (_: Throwable) {
                "n/a"
            }
            val webInfo = try {
                val seeds = e.handle.urlSeeds()
                val conns = e.handle.peerInfo().filter {
                    it.connectionType() == org.libtorrent4j.PeerInfo.ConnectionType.WEB_SEED ||
                            it.connectionType() == org.libtorrent4j.PeerInfo.ConnectionType.HTTP_SEED
                }
                "webSeeds=${seeds.size}${
                    if (seeds.isNotEmpty()) seeds.joinToString(
                        ",",
                        "[",
                        "]"
                    ) { it.take(60) } else ""
                } " +
                        "webConns=${conns.size} webDown=${conns.sumOf { it.downSpeed() } / 1024}KB/s"
            } catch (_: Throwable) {
                "web=n/a"
            }
            println(
                "[torrent $taskId] state=${st.state()} peers=${st.numPeers()} seeds=${st.numSeeds()} $webInfo " +
                        "conns=${st.numConnections()} candidates=${st.connectCandidates()} " +
                        "listPeers=${st.listPeers()} listSeeds=${st.listSeeds()} " +
                        "down=${st.downloadPayloadRate() / 1024}KB/s up=${st.uploadPayloadRate() / 1024}KB/s " +
                        "dht=${session.isDhtRunning} dhtNodes=${session.dhtNodes()} " +
                        "listenPort=${SessionHandle(session.swig()).listenPort} external=${session.externalAddress()} " +
                        "upLimit=${e.handle.uploadLimit} trackers: $trackers"
            )
        } catch (_: Throwable) {
        }
    }

    override fun shutdown() {
        try {
            entries.clear()
            if (session.isRunning) session.stop()
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val MAGNET_TIMEOUT_SECONDS = 60
        val PUBLIC_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.dler.org:6969/announce"
        )
    }
}
