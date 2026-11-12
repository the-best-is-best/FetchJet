package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.util.hashFileHex
import com.michelle.raouf.fetch_jet.core.util.parseExpectedChecksum
import com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.math.min

internal const val STEP_CHECKING_FILE = "Checking the file"
internal const val STEP_CHECKING_ARCHIVE = "Checking the archive"
internal const val STEP_REFETCHING_DAMAGED = "Fetching the damaged part again"
internal const val NOTE_ARCHIVE_DAMAGED = "The archive may be damaged"

internal class ChecksumMismatchException(val algorithm: String) :
    Exception("The file does not match its checksum ($algorithm). It was not saved.")

internal class ExpectedDigest(val algorithm: String, val hex: String, val fromServer: Boolean)

internal class IntegrityResult(val verified: Boolean?, val note: String?)

internal fun pickExpectedDigest(userText: String?, server: ServerChecksum?): ExpectedDigest? {
    val user = parseExpectedChecksum(userText)
    if (user != null) return ExpectedDigest(user.algorithm, user.hex, false)
    return server?.let { ExpectedDigest(it.algorithm, it.hex, true) }
}

internal fun HttpChunkDownloader.reportIntegrityStep(taskId: String, label: String?, fraction: Float = -1f) {
    updateTaskState(taskId) {
        if (label == null) {
            if (it.postProcessLabel == null) it
            else it.copy(postProcessLabel = null, postProcessProgress = -1f, postProcessStartedAtMs = 0L)
        } else it.copy(
            postProcessLabel = label,
            postProcessProgress = fraction,
            postProcessStartedAtMs = if (it.postProcessLabel == label && it.postProcessStartedAtMs > 0L)
                it.postProcessStartedAtMs else nowEpochMs(),
            downloadSpeedBytesPerSec = 0L
        )
    }
}

private suspend fun HttpChunkDownloader.stepProgress(taskId: String, label: String, total: Long): (Long) -> Unit {
    val job = currentCoroutineContext()[Job]
    var lastPercent = -1
    reportIntegrityStep(taskId, label, if (total > 0) 0f else -1f)
    return { done ->
        job?.ensureActive()
        if (total > 0) {
            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
            if (percent != lastPercent) {
                lastPercent = percent
                reportIntegrityStep(taskId, label, percent / 100f)
            }
        }
    }
}

private suspend fun HttpChunkDownloader.checksumMatches(taskId: String, file: Path, digest: ExpectedDigest): Boolean? {
    val total = AppFileSystem.metadataOrNull(file)?.size ?: 0L
    val actual = hashFileHex(file, digest.algorithm, stepProgress(taskId, STEP_CHECKING_FILE, total)) ?: return null
    return actual.equals(digest.hex, ignoreCase = true)
}

private suspend fun HttpChunkDownloader.archiveReport(taskId: String, file: Path, kind: String): ArchiveReport {
    val total = AppFileSystem.metadataOrNull(file)?.size ?: 0L
    val progress = stepProgress(taskId, STEP_CHECKING_ARCHIVE, total)
    val handle = try {
        AppFileSystem.openReadOnly(file)
    } catch (_: Exception) {
        return ArchiveReport(kind, null)
    }
    try {
        return checkArchive(kind, handle, progress)
    } finally {
        runCatching { handle.close() }
    }
}

internal suspend fun HttpChunkDownloader.checkAssembledFile(
    taskId: String,
    file: Path,
    fileName: String,
    serverChecksum: ServerChecksum?,
    refetch: (suspend (List<LongRange>) -> Boolean)? = null
): IntegrityResult {
    val digest = pickExpectedDigest(tasksState.value[taskId]?.expectedChecksum, serverChecksum)
    val kind = archiveKindOf(fileName)
    if (digest == null && kind == null) return IntegrityResult(null, null)
    try {
        var matches = digest?.let { checksumMatches(taskId, file, it) }
        var note: String? = null
        if (matches != true && kind != null) {
            var report = archiveReport(taskId, file, kind)
            if (report.isDamaged && report.damaged.isNotEmpty() && refetch != null) {
                reportIntegrityStep(taskId, STEP_REFETCHING_DAMAGED, -1f)
                if (refetch(report.damaged)) {
                    matches = digest?.let { checksumMatches(taskId, file, it) }
                    report = if (matches == true) ArchiveReport(kind, null) else archiveReport(taskId, file, kind)
                }
            }
            if (report.isDamaged) note = NOTE_ARCHIVE_DAMAGED
        }
        if (digest != null && matches == false) throw ChecksumMismatchException(digest.algorithm)
        return IntegrityResult(matches, note)
    } finally {
        reportIntegrityStep(taskId, null)
    }
}

internal suspend fun assemblePieces(pieces: List<ChunkLayout.Piece>, partPath: (Int) -> Path, target: Path) {
    val sink = AppFileSystem.sink(target).buffer()
    try {
        val block = ByteArray(256 * 1024)
        for (piece in pieces) {
            val part = partPath(piece.index)
            if (!AppFileSystem.exists(part)) throw Exception("Piece ${piece.index} is missing")
            val source = AppFileSystem.source(part).buffer()
            try {
                var left = piece.length
                while (left > 0) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(block, 0, min(block.size.toLong(), left).toInt())
                    if (read <= 0) break
                    sink.write(block, 0, read)
                    left -= read
                }
            } finally {
                source.close()
            }
        }
        sink.flush()
    } finally {
        sink.close()
    }
}

private fun copyHead(from: Path, to: Path, count: Long) {
    AppFileSystem.source(from).buffer().use { source ->
        AppFileSystem.sink(to).buffer().use { sink ->
            val block = ByteArray(256 * 1024)
            var left = count
            while (left > 0) {
                val read = source.read(block, 0, min(block.size.toLong(), left).toInt())
                if (read <= 0) throw Exception("The part file is shorter than expected")
                sink.write(block, 0, read)
                left -= read
            }
        }
    }
}

internal suspend fun refetchDamagedRanges(
    ranges: List<LongRange>,
    pieces: List<ChunkLayout.Piece>,
    partPath: (Int) -> Path,
    fetch: suspend (ChunkLayout.Piece) -> Unit
): Boolean {
    val hit = pieces.filter { piece -> ranges.any { it.first <= piece.end && it.last >= piece.start } }
    if (hit.isEmpty()) return false
    val moved = ArrayList<ChunkLayout.Piece>()
    fun backupOf(piece: ChunkLayout.Piece): Path = "${partPath(piece.index)}.bak".toPath()
    try {
        for (piece in hit) {
            val firstBad = ranges.filter { it.first <= piece.end && it.last >= piece.start }.minOf { it.first }
            val keep = (firstBad - piece.start).coerceIn(0L, piece.length)
            val part = partPath(piece.index)
            val backup = backupOf(piece)
            runCatching { AppFileSystem.delete(backup) }
            AppFileSystem.atomicMove(part, backup)
            moved.add(piece)
            if (keep > 0) copyHead(backup, part, keep)
            piece.downloaded = keep
        }
        for (piece in hit) {
            fetchWithRetry(maxRetries = 3) {
                fetch(piece)
                if (!piece.isCompleted) throw Exception("Piece ${piece.index} is incomplete")
            }
        }
        for (piece in moved) runCatching { AppFileSystem.delete(backupOf(piece)) }
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        for (piece in moved) {
            val part = partPath(piece.index)
            runCatching { AppFileSystem.delete(part) }
            runCatching { AppFileSystem.atomicMove(backupOf(piece), part) }
            piece.downloaded = piece.length
        }
        return false
    }
}
