package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                // DHT + local discovery on; libtorrent's default bootstrap nodes are used.
                val sp = SettingsPack()
                sp.isEnableDht = true
                sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
                sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
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
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.openbittorrent.com:6969/announce"
        )
        return magnet + trackers.joinToString("") {
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
        // libtorrent: 0 = unlimited. A 1 B/s upload limit is how "do not seed" is expressed.
        e.handle.downloadLimit = downloadBytesPerSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val up = when {
            e.disableUpload -> 1
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

    override fun shutdown() {
        try {
            entries.clear()
            if (session.isRunning) session.stop()
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val MAGNET_TIMEOUT_SECONDS = 60
    }
}
