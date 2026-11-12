package com.michelle.raouf.fetch_jet.feature.torrent_engine

import com.michelle.raouf.fetch_jet.core.filesystem.DesktopPaths
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.core.network.ProxyKind
import com.michelle.raouf.fetch_jet.core.network.ProxySettings
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.join
import com.michelle.raouf.fetch_jet.platform.torrent.FjtEngine
import com.michelle.raouf.fetch_jet.platform.torrent.FjtLibrary
import com.michelle.raouf.fetch_jet.platform.torrent.FjtMeta
import com.tbib.kmp_localization.LocalizationManager
import kotlin.concurrent.AtomicInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal expect fun nativeTorrentLibrary(): FjtLibrary

private class NativeTorrentBackend(private val library: FjtLibrary) : TorrentBackend {
    private val engine = FjtEngine(library)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val proxyWatch = AtomicInt(0)

    override val isSupported: Boolean get() = engine.isSupported

    override fun engineStatus(): String {
        val api = library.api
        val failure = library.loadError
        return when {
            api != null -> LocalizationManager.Strings.sysTorrentEngineReadyVersion(api.version)
            !library.isAvailable -> LocalizationManager.Strings.sysTorrentEngineNotAvailable
            failure != null -> LocalizationManager.Strings.sysTorrentEngineFailedToLoad(failure)
            else -> LocalizationManager.Strings.sysTorrentEngineAvailable
        }
    }

    override suspend fun installEngine(): Result<Unit> = withContext(Dispatchers.IO) {
        library.load().map { }
    }

    private fun proxyLines(p: ProxySettings): String = FjtEngine.proxySettings(
        kind = when (p.kind) {
            ProxyKind.NONE -> "none"
            ProxyKind.HTTP -> "http"
            ProxyKind.SOCKS5 -> "socks5"
        },
        host = p.host,
        port = p.port,
        user = p.user,
        password = p.password,
    )

    private fun watchProxy() {
        if (!proxyWatch.compareAndSet(0, 1)) return
        engine.proxyText = proxyLines(NetworkSettings.proxy.value)
        scope.launch {
            NetworkSettings.proxy.collect { engine.proxyText = proxyLines(it) }
        }
    }

    private fun toMetadata(meta: FjtMeta): TorrentMetadata {
        val files = meta.files.map { f ->
            val path = f.path.replace('\\', '/')
                .split('/')
                .map { sanitizePathSegment(it) }
                .filter { it.isNotBlank() }
                .joinToString("/")
            TorrentFileEntry(index = f.index, path = path, size = f.size, isSelected = true)
        }
        return TorrentMetadata(
            name = sanitizePathSegment(meta.name).ifBlank { "Torrent_Download" },
            files = files,
            totalSize = meta.totalSize,
            infoHash = meta.infoHash,
            torrentBytes = meta.torrentBytes,
        )
    }

    override suspend fun fetchMetadata(magnetUri: String?, torrentBytes: ByteArray?): TorrentMetadata =
        withContext(Dispatchers.IO) {
            when {
                torrentBytes != null -> toMetadata(engine.parse(torrentBytes))
                !magnetUri.isNullOrBlank() -> {
                    watchProxy()
                    val scratch = DesktopOs.join(DesktopPaths.temp(), "torrent-metadata")
                    DesktopOs.createDirectories(scratch)
                    val meta = engine.fetchMagnet(magnetUri, scratch)
                        ?: throw IllegalStateException(
                            "Could not fetch torrent metadata for this magnet link (no peers answered within " +
                                    "${FjtEngine.MAGNET_TIMEOUT_MS / 1000} s). Check the link or your network." +
                                    if (NetworkSettings.proxy.value.isOn) " A proxy is on: a torrent that has only UDP trackers or DHT cannot find peers through an HTTP proxy, or through a SOCKS5 proxy without UDP support." else ""
                        )
                    toMetadata(meta)
                }

                else -> throw IllegalArgumentException("A magnet link or a .torrent file is required")
            }
        }

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
        watchProxy()
        engine.verbose = AppLog.verbose
        val meta = engine.parse(torrentBytes)
        val prio = FjtEngine.priorities(meta.numFiles, selected, priorities)
        DesktopOs.createDirectories(savePath)
        engine.add(
            taskId = taskId,
            torrent = torrentBytes,
            savePath = savePath,
            priorities = prio,
            disableUpload = disableUpload,
            downloadLimit = downloadLimitBytesPerSec.coerceIn(0L, Int.MAX_VALUE.toLong()),
            uploadLimit = uploadLimitBytesPerSec.coerceIn(0L, Int.MAX_VALUE.toLong()),
        )
    }

    override fun pause(taskId: String) = engine.pause(taskId)

    override fun resume(taskId: String) = engine.resume(taskId)

    override fun remove(taskId: String, deleteFiles: Boolean) = engine.remove(taskId, deleteFiles)

    override fun setFileSelection(taskId: String, selected: Set<Int>, priorities: Map<Int, Int>) =
        engine.setFileSelection(taskId, selected, priorities)

    override fun setLimits(taskId: String, downloadBytesPerSec: Long, uploadBytesPerSec: Long) =
        engine.setLimits(
            taskId,
            downloadBytesPerSec.coerceIn(0L, Int.MAX_VALUE.toLong()),
            uploadBytesPerSec.coerceIn(0L, Int.MAX_VALUE.toLong())
        )

    override fun progress(taskId: String): TorrentProgress? {
        engine.verbose = AppLog.verbose
        val st = engine.status(taskId) ?: return null
        return TorrentProgress(
            downloadedBytes = st.totalWantedDone,
            wantedBytes = st.totalWanted,
            downloadRate = st.downloadRate,
            uploadRate = st.uploadRate,
            peers = st.peers,
            seeds = st.seeds,
            isFinished = st.isFinished,
            isSeeding = st.isSeeding,
            isPaused = st.isPaused,
            hasMetadata = st.hasMetadata,
            fileProgress = st.fileProgress.toList(),
            stateText = st.stateText,
            error = st.error,
            uploadedBytes = st.totalUpload,
        )
    }

    override val supportsDetails: Boolean get() = engine.hasDetails

    override fun details(taskId: String): TorrentDetails? {
        val d = engine.details(taskId) ?: return null
        return TorrentDetails(
            trackers = d.trackers.map { TorrentTrackerInfo(it.url, it.state, it.message) },
            peers = d.peers.map {
                TorrentPeerInfo(
                    address = it.address,
                    client = it.client,
                    downloadRate = it.downloadRate,
                    uploadRate = it.uploadRate,
                    progress = (it.progressPermille / 1000f).coerceIn(0f, 1f),
                    isSeed = it.isSeed,
                )
            },
            uploadedBytes = d.uploadedBytes,
            downloadedBytes = d.downloadedBytes,
            pieceCount = d.pieceCount,
            piecesDone = d.piecesDone,
            pieceSize = d.pieceLength,
            magnetLink = d.magnet,
            firstLastPiecesFirst = d.firstLast,
        )
    }

    override fun addTracker(taskId: String, url: String): Boolean = engine.addTracker(taskId, url)

    override fun recheck(taskId: String): Boolean = engine.recheck(taskId)

    override fun setFirstLastPiecesFirst(taskId: String, on: Boolean): Boolean = engine.setFirstLastPiecesFirst(taskId, on)

    override fun shutdown() = engine.shutdown()
}

private val backend: TorrentBackend by lazy { NativeTorrentBackend(nativeTorrentLibrary()) }

actual fun createTorrentBackend(): TorrentBackend = backend
