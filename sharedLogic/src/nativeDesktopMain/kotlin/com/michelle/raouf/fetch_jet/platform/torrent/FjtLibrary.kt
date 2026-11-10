@file:OptIn(ExperimentalForeignApi::class)

package com.michelle.raouf.fetch_jet.platform.torrent

import kotlin.concurrent.AtomicLong
import kotlin.concurrent.AtomicReference
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

const val FJT_OK = 0
const val FJT_PENDING = 1
const val FJT_E_NOT_FOUND = -2
const val FJT_E_SESSION = -4
const val FJT_ABI = 1
const val FJT_ST_COUNT = 16
const val FJT_SI_COUNT = 8
const val FJT_SI_LISTEN_PORT = 0
const val FJT_SI_DHT_RUNNING = 1
const val FJT_SI_TORRENTS = 3
const val FJT_SI_MAGNET_JOBS = 5
const val FJT_SI_LOCAL_ONLY = 6
const val FJT_ADD_DISABLE_UPLOAD = 1
const val FJT_PRIO_SKIP: Byte = 0
const val FJT_PRIO_LOW: Byte = 1
const val FJT_PRIO_DEFAULT: Byte = 4
const val FJT_PRIO_TOP: Byte = 7

private const val ERR_CAP = 1024
private const val MISS_RECHECK_MS = 2_000L

class FjtException(message: String) : Exception(message)

interface FjtLock {
    fun lock()
    fun unlock()
}

inline fun <T> FjtLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

interface FjtModule {
    fun symbol(name: String): COpaquePointer?
}

interface FjtHost {
    val libraryName: String
    fun candidates(): List<String>
    fun isFile(path: String): Boolean
    fun fullPath(path: String): String
    fun open(path: String): FjtModule
    fun newLock(): FjtLock
    fun uptimeMs(): Long
    fun sleepMs(ms: Int)
    fun env(name: String): String?
    fun createDirectories(path: String)
}

class FjtFile(val index: Int, val size: Long, val path: String)

class FjtMeta(
    val name: String,
    val infoHash: String,
    val totalSize: Long,
    val numFiles: Int,
    val files: List<FjtFile>,
    val torrentBytes: ByteArray,
)

class FjtStatus(
    val state: Int,
    val flags: Long,
    val totalWanted: Long,
    val totalWantedDone: Long,
    val downloadRate: Long,
    val uploadRate: Long,
    val peers: Int,
    val seeds: Int,
    val numFiles: Int,
    val connections: Int,
    val uploadLimit: Long,
    val totalUpload: Long,
    val fileProgress: LongArray,
    val error: String?,
) {
    val isPaused: Boolean get() = flags and 1L != 0L
    val isFinished: Boolean get() = flags and 2L != 0L
    val isSeeding: Boolean get() = flags and 4L != 0L
    val hasMetadata: Boolean get() = flags and 8L != 0L
    val hasError: Boolean get() = flags and 16L != 0L

    val stateText: String
        get() = when (state) {
            1 -> "Checking files"
            2 -> "Checking resume data"
            3 -> "Fetching metadata"
            4 -> "Downloading"
            5 -> "Finished"
            6 -> "Seeding"
            else -> "Connecting"
        }
}

private inline fun <T> ByteArray?.pinnedOrNull(block: (CPointer<ByteVar>?) -> T): T {
    if (this == null || isEmpty()) return block(null)
    return usePinned { block(it.addressOf(0)) }
}

private fun CPointer<ByteVar>.text(): String = toKString()

class FjtApi internal constructor(val path: String, private val module: FjtModule, private val libraryName: String) {
    private fun address(name: String): CPointer<CFunction<() -> Unit>> =
        module.symbol(name)?.reinterpret<CFunction<() -> Unit>>() ?: throw FjtException("$libraryName has no export $name")

    private val abiVersion = address("fjt_abi_version").reinterpret<CFunction<() -> Int>>()
    private val versionTextFn = address("fjt_version_text").reinterpret<CFunction<(CPointer<ByteVar>?, Int) -> Int>>()
    private val sessionCreateFn = address("fjt_session_create").reinterpret<CFunction<(CPointer<ByteVar>?, CPointer<ByteVar>?, Int) -> COpaquePointer?>>()
    private val applySettingsFn = address("fjt_session_apply_settings").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> Int>>()
    private val sessionInfoFn = address("fjt_session_info").reinterpret<CFunction<(COpaquePointer?, CPointer<LongVar>?, Int) -> Int>>()
    private val sessionDestroyFn = address("fjt_session_destroy").reinterpret<CFunction<(COpaquePointer?, Int) -> Unit>>()
    private val metaParseFn = address("fjt_meta_parse").reinterpret<CFunction<(CPointer<ByteVar>?, Long, CPointer<ByteVar>?, Int) -> COpaquePointer?>>()
    private val metaNameFn = address("fjt_meta_name").reinterpret<CFunction<(COpaquePointer?) -> CPointer<ByteVar>?>>()
    private val metaInfoHashFn = address("fjt_meta_info_hash").reinterpret<CFunction<(COpaquePointer?) -> CPointer<ByteVar>?>>()
    private val metaTotalSizeFn = address("fjt_meta_total_size").reinterpret<CFunction<(COpaquePointer?) -> Long>>()
    private val metaNumFilesFn = address("fjt_meta_num_files").reinterpret<CFunction<(COpaquePointer?) -> Int>>()
    private val metaEntryCountFn = address("fjt_meta_entry_count").reinterpret<CFunction<(COpaquePointer?) -> Int>>()
    private val metaEntryIndexFn = address("fjt_meta_entry_index").reinterpret<CFunction<(COpaquePointer?, Int) -> Int>>()
    private val metaEntrySizeFn = address("fjt_meta_entry_size").reinterpret<CFunction<(COpaquePointer?, Int) -> Long>>()
    private val metaEntryPathFn = address("fjt_meta_entry_path").reinterpret<CFunction<(COpaquePointer?, Int) -> CPointer<ByteVar>?>>()
    private val metaTorrentDataFn = address("fjt_meta_torrent_data").reinterpret<CFunction<(COpaquePointer?, CPointer<LongVar>?) -> CPointer<ByteVar>?>>()
    private val metaFreeFn = address("fjt_meta_free").reinterpret<CFunction<(COpaquePointer?) -> Unit>>()
    private val magnetStartFn = address("fjt_magnet_start").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?, CPointer<ByteVar>?, Int) -> COpaquePointer?>>()
    private val magnetPollFn = address("fjt_magnet_poll").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Int) -> Int>>()
    private val magnetTakeFn = address("fjt_magnet_take").reinterpret<CFunction<(COpaquePointer?) -> COpaquePointer?>>()
    private val magnetCancelFn = address("fjt_magnet_cancel").reinterpret<CFunction<(COpaquePointer?) -> Unit>>()
    private val torrentAddFn = address("fjt_torrent_add").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?, Long, CPointer<ByteVar>?, Long, CPointer<ByteVar>?, CPointer<ByteVar>?, Int, Int, Long, Long, CPointer<ByteVar>?, CPointer<ByteVar>?, Int) -> Int>>()
    private val torrentPauseFn = address("fjt_torrent_pause").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> Int>>()
    private val torrentResumeFn = address("fjt_torrent_resume").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> Int>>()
    private val torrentRemoveFn = address("fjt_torrent_remove").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Int) -> Int>>()
    private val torrentPrioritiesFn = address("fjt_torrent_set_file_priorities").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?, Int) -> Int>>()
    private val torrentLimitsFn = address("fjt_torrent_set_limits").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Long, Long) -> Int>>()
    private val torrentSequentialFn = address("fjt_torrent_set_sequential").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Int) -> Int>>()
    private val torrentStatusFn = address("fjt_torrent_status").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<LongVar>?, Int, CPointer<LongVar>?, Int, CPointer<ByteVar>?, Int) -> Int>>()
    private val torrentDebugFn = address("fjt_torrent_debug_text").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<ByteVar>?, Int) -> Int>>()
    private val torrentSaveResumeFn = address("fjt_torrent_save_resume").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, Int) -> COpaquePointer?>>()
    private val bufDataFn = address("fjt_buf_data").reinterpret<CFunction<(COpaquePointer?) -> CPointer<ByteVar>?>>()
    private val bufSizeFn = address("fjt_buf_size").reinterpret<CFunction<(COpaquePointer?) -> Long>>()
    private val bufFreeFn = address("fjt_buf_free").reinterpret<CFunction<(COpaquePointer?) -> Unit>>()

    val abi: Int = abiVersion()

    val version: String = memScoped {
        val buffer = allocArray<ByteVar>(64)
        versionTextFn(buffer, 64)
        buffer.text()
    }

    fun sessionCreate(settings: String): COpaquePointer = memScoped {
        val err = allocArray<ByteVar>(ERR_CAP)
        err[0] = 0
        sessionCreateFn(settings.cstr.ptr, err, ERR_CAP)
            ?: throw FjtException(err.text().ifBlank { "The torrent session could not start" })
    }

    fun applySettings(session: COpaquePointer, settings: String): Int = memScoped {
        applySettingsFn(session, settings.cstr.ptr)
    }

    fun sessionInfo(session: COpaquePointer): LongArray? {
        val out = LongArray(FJT_SI_COUNT)
        val written = out.usePinned { sessionInfoFn(session, it.addressOf(0), FJT_SI_COUNT) }
        return if (written <= 0) null else out
    }

    fun sessionDestroy(session: COpaquePointer, timeoutMs: Int) = sessionDestroyFn(session, timeoutMs)

    private fun readMeta(meta: COpaquePointer, fallbackBytes: ByteArray?): FjtMeta {
        val count = metaEntryCountFn(meta)
        val files = ArrayList<FjtFile>(maxOf(count, 0))
        for (i in 0 until count) {
            files.add(FjtFile(metaEntryIndexFn(meta, i), metaEntrySizeFn(meta, i), metaEntryPathFn(meta, i)?.text() ?: ""))
        }
        val bytes = fallbackBytes ?: memScoped {
            val size = alloc<LongVar>()
            val data = metaTorrentDataFn(meta, size.ptr)
            if (data == null || size.value <= 0L) ByteArray(0) else data.readBytes(size.value.toInt())
        }
        return FjtMeta(
            name = metaNameFn(meta)?.text() ?: "",
            infoHash = metaInfoHashFn(meta)?.text() ?: "",
            totalSize = metaTotalSizeFn(meta),
            numFiles = metaNumFilesFn(meta),
            files = files,
            torrentBytes = bytes,
        )
    }

    fun parse(torrent: ByteArray): FjtMeta = memScoped {
        val err = allocArray<ByteVar>(ERR_CAP)
        err[0] = 0
        val meta = torrent.pinnedOrNull { metaParseFn(it, torrent.size.toLong(), err, ERR_CAP) }
            ?: throw FjtException(err.text().ifBlank { "Not a torrent file" })
        try {
            readMeta(meta, torrent)
        } finally {
            metaFreeFn(meta)
        }
    }

    fun magnetStart(session: COpaquePointer, uri: String, scratchDir: String): COpaquePointer = memScoped {
        val err = allocArray<ByteVar>(ERR_CAP)
        err[0] = 0
        magnetStartFn(session, uri.cstr.ptr, scratchDir.cstr.ptr, err, ERR_CAP)
            ?: throw FjtException(err.text().ifBlank { "The magnet link could not be started" })
    }

    fun magnetPoll(job: COpaquePointer): Pair<Int, String> = memScoped {
        val err = allocArray<ByteVar>(ERR_CAP)
        err[0] = 0
        val code = magnetPollFn(job, err, ERR_CAP)
        code to err.text()
    }

    fun magnetTake(job: COpaquePointer): FjtMeta? {
        val meta = magnetTakeFn(job) ?: return null
        return try {
            readMeta(meta, null)
        } finally {
            metaFreeFn(meta)
        }
    }

    fun magnetCancel(job: COpaquePointer) = magnetCancelFn(job)

    fun add(
        session: COpaquePointer,
        taskId: String,
        torrent: ByteArray,
        resume: ByteArray?,
        savePath: String,
        priorities: ByteArray,
        flags: Int,
        downloadLimit: Long,
        uploadLimit: Long,
        extraTrackers: String,
    ): Pair<Int, String> = memScoped {
        val err = allocArray<ByteVar>(ERR_CAP)
        err[0] = 0
        val code = torrent.pinnedOrNull { t ->
            resume.pinnedOrNull { r ->
                priorities.pinnedOrNull { p ->
                    torrentAddFn(
                        session, taskId.cstr.ptr, t, torrent.size.toLong(), r, resume?.size?.toLong() ?: 0L,
                        savePath.cstr.ptr, p, priorities.size, flags, downloadLimit, uploadLimit,
                        if (extraTrackers.isEmpty()) null else extraTrackers.cstr.ptr, err, ERR_CAP
                    )
                }
            }
        }
        code to err.text()
    }

    fun pause(session: COpaquePointer, taskId: String): Int = memScoped { torrentPauseFn(session, taskId.cstr.ptr) }

    fun resume(session: COpaquePointer, taskId: String): Int = memScoped { torrentResumeFn(session, taskId.cstr.ptr) }

    fun remove(session: COpaquePointer, taskId: String, deleteFiles: Boolean): Int =
        memScoped { torrentRemoveFn(session, taskId.cstr.ptr, if (deleteFiles) 1 else 0) }

    fun setPriorities(session: COpaquePointer, taskId: String, priorities: ByteArray): Int = memScoped {
        priorities.pinnedOrNull { torrentPrioritiesFn(session, taskId.cstr.ptr, it, priorities.size) }
    }

    fun setLimits(session: COpaquePointer, taskId: String, download: Long, upload: Long): Int =
        memScoped { torrentLimitsFn(session, taskId.cstr.ptr, download, upload) }

    fun setSequential(session: COpaquePointer, taskId: String, on: Boolean): Int =
        memScoped { torrentSequentialFn(session, taskId.cstr.ptr, if (on) 1 else 0) }

    fun status(session: COpaquePointer, taskId: String, fileCapacity: Int): Pair<Int, FjtStatus?> = memScoped {
        val err = allocArray<ByteVar>(ERR_CAP)
        err[0] = 0
        val values = LongArray(FJT_ST_COUNT)
        val progress = LongArray(maxOf(fileCapacity, 1))
        val id = taskId.cstr.ptr
        val code = values.usePinned { v ->
            progress.usePinned { p -> torrentStatusFn(session, id, v.addressOf(0), FJT_ST_COUNT, p.addressOf(0), progress.size, err, ERR_CAP) }
        }
        if (code < 0) return@memScoped code to null
        val files = code
        val fileProgress = if (files <= progress.size) progress.copyOf(files) else LongArray(0)
        val error = if (values[1] and 16L != 0L) err.text().ifBlank { "torrent error" } else null
        code to FjtStatus(
            state = values[0].toInt(),
            flags = values[1],
            totalWanted = values[2],
            totalWantedDone = values[3],
            downloadRate = values[4],
            uploadRate = values[5],
            peers = values[6].toInt(),
            seeds = values[7].toInt(),
            numFiles = values[8].toInt(),
            connections = values[9].toInt(),
            uploadLimit = values[13],
            totalUpload = values[14],
            fileProgress = fileProgress,
            error = error,
        )
    }

    fun debugText(session: COpaquePointer, taskId: String): String? = memScoped {
        val cap = 8192
        val out = allocArray<ByteVar>(cap)
        out[0] = 0
        if (torrentDebugFn(session, taskId.cstr.ptr, out, cap) < 0) null else out.text()
    }

    fun saveResume(session: COpaquePointer, taskId: String, timeoutMs: Int): ByteArray? {
        val buf = memScoped { torrentSaveResumeFn(session, taskId.cstr.ptr, timeoutMs) } ?: return null
        return try {
            val size = bufSizeFn(buf)
            val data = bufDataFn(buf)
            if (data == null || size <= 0L) null else data.readBytes(size.toInt())
        } finally {
            bufFreeFn(buf)
        }
    }
}

class FjtLibrary(val host: FjtHost) {
    private val foundPath = AtomicReference<String?>(null)
    private val lastMissMs = AtomicLong(-MISS_RECHECK_MS)
    private val loaded = AtomicReference<FjtApi?>(null)
    private val failure = AtomicReference<String?>(null)
    private val loadLock = host.newLock()

    val libraryName: String get() = host.libraryName

    fun candidates(): List<String> = host.candidates()

    fun find(): String? {
        loaded.value?.let { return it.path }
        foundPath.value?.let { return it }
        val now = host.uptimeMs()
        if (now - lastMissMs.value < MISS_RECHECK_MS) return null
        val path = candidates().firstOrNull { host.isFile(it) }
        if (path == null) {
            lastMissMs.value = now
            return null
        }
        foundPath.value = path
        return path
    }

    val isAvailable: Boolean get() = find() != null

    val api: FjtApi? get() = loaded.value

    val loadError: String? get() = failure.value

    fun load(): Result<FjtApi> {
        loaded.value?.let { return Result.success(it) }
        return loadLock.withLock {
            loaded.value?.let { return@withLock Result.success(it) }
            lastMissMs.value = -MISS_RECHECK_MS
            val path = find()
            if (path == null) {
                val message = "${host.libraryName} not found"
                failure.value = message
                return@withLock Result.failure(FjtException(message))
            }
            val full = host.fullPath(path)
            val module = try {
                host.open(full)
            } catch (e: FjtException) {
                foundPath.value = null
                failure.value = e.message
                return@withLock Result.failure(e)
            }
            val api = runCatching { FjtApi(full, module, host.libraryName) }.getOrElse {
                val message = it.message ?: "${host.libraryName} could not be loaded"
                failure.value = message
                return@withLock Result.failure(FjtException(message))
            }
            if (api.abi != FJT_ABI) {
                val message = "${host.libraryName} has ABI ${api.abi}, expected $FJT_ABI"
                failure.value = message
                return@withLock Result.failure(FjtException(message))
            }
            failure.value = null
            loaded.value = api
            Result.success(api)
        }
    }
}
