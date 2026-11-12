package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.network.NetworkSettings
import com.michelle.raouf.fetch_jet.core.network.RateLimiter
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.math.max
import kotlin.math.min
import kotlin.time.TimeSource

private class FtpLogin(val user: String, val password: String)

private const val FTP_SPLIT_MIN_BYTES = 2L * 1024 * 1024

/** An FTP / FTPS link of a task: logs in, finds the file and downloads it in parts when the server can resume. */
internal suspend fun HttpChunkDownloader.downloadFtp(taskId: String) {
    val task = tasksState.value[taskId] ?: return
    if (NetworkSettings.proxy.value.isOn) {
        throw Exception("FTP downloads do not use the proxy. Turn the proxy off in Settings > Network to download this link.")
    }
    val target = try {
        parseFtpUrl(task.url)
    } catch (e: IllegalArgumentException) {
        throw Exception(e.message ?: "The FTP link is not valid")
    }
    if (target.isFolder) throw Exception("This FTP link points to a folder, not to a file")
    val login = target.user?.let { FtpLogin(it, target.password.orEmpty()) }
        ?: NetworkSettings.loginFor(target.host)?.let { FtpLogin(it.user, it.password) }
        ?: FtpLogin("anonymous", "fetchjet@localhost")

    if (target.tls && !ftpTlsInProcess) {
        ftpsWithTool(taskId, task, target, login)
        return
    }

    updateTaskState(taskId) { it.copy(status = DownloadStatus.CONNECTING, errorMessage = null) }

    val session = openFtp(target, login)
    var remote = target.path
    var sessionOwned = true
    try {
        var size = session.size(remote)
        if (size == null) {
            if (session.isFolder(remote)) throw Exception("This FTP link points to a folder, not to a file")
            val absolute = session.size(target.absolutePath)
            if (absolute != null) {
                size = absolute
                remote = target.absolutePath
            }
        }
        val modified = session.modified(remote)
        val identity = FileIdentity("", modified, size ?: -1L)

        val rawName = task.fileName.ifBlank { target.fileName }
        val targetFileName = pathResolver.sanitizeFileName(rawName)
        val taskCacheDir = "${pathResolver.getTempDirectory()}/task_$taskId"
        val cachePath = "$taskCacheDir/$targetFileName"
        val finalDestPath = "${task.destinationPath}/$targetFileName"
        try {
            AppFileSystem.createDirectories(taskCacheDir.toPath())
        } catch (_: Exception) {
        }
        syncFileIdentity(taskId, taskCacheDir, cachePath, identity)

        val canResume = session.supportsRest && size != null
        updateTaskState(taskId) { it.copy(fileName = targetFileName, serverSupportsResume = canResume) }

        val hasManifest = AppFileSystem.exists("$cachePath.chunks".toPath())
        if (canResume && size != null && (hasManifest || (size > FTP_SPLIT_MIN_BYTES && connectionsFor(taskId, size) > 1))) {
            sessionOwned = false
            session.close()
            downloadParallelChunks(
                taskId, task.url, cachePath, finalDestPath, size, identity,
                fetchPiece = { piece, partPath, limiter, layout ->
                    ftpFetchPiece(target, login, remote, piece, partPath, limiter, layout)
                }
            )
        } else {
            sessionOwned = false
            ftpSingleStream(taskId, session, remote, size ?: -1L, canResume, cachePath, finalDestPath)
        }
    } finally {
        if (sessionOwned) session.close()
    }
}

private suspend fun openFtp(target: FtpTarget, login: FtpLogin): FtpSession = try {
    FtpSession.open(target, login.user, login.password)
} catch (e: CancellationException) {
    throw e
} catch (e: FtpException) {
    throw ftpFailure(e)
}

/** A server that turns the connection away because there are too many of them is "busy", like HTTP 429. */
private fun ftpFailure(e: FtpException): Exception {
    val text = e.message.orEmpty().lowercase()
    return if (e.code == 421 || e.code == 450 || text.contains("too many") || text.contains("limit")) {
        ServerBusyException(e.message ?: "FTP server busy")
    } else e
}

/** A file that is another file now (size or time changed) must not be mixed with parts of the old one. */
private suspend fun HttpChunkDownloader.syncFileIdentity(taskId: String, taskCacheDir: String, cachePath: String, identity: FileIdentity) {
    if (identity.ifRange.isEmpty() && identity.size <= 0) return
    val identityPath = "$cachePath.identity".toPath()
    val saved = runCatching {
        if (AppFileSystem.exists(identityPath)) FileIdentity.decode(AppFileSystem.read(identityPath) { readUtf8() }) else null
    }.getOrNull()
    if (saved != null && !saved.sameAs(identity)) {
        try {
            deleteTaskScratchDir(taskId)
        } catch (_: Exception) {
        }
        try {
            AppFileSystem.createDirectories(taskCacheDir.toPath())
        } catch (_: Exception) {
        }
    }
    try {
        AppFileSystem.write(identityPath) { writeUtf8(identity.encode()) }
    } catch (_: Exception) {
    }
}

/** One piece of the file over its own FTP connection (REST to the piece's first missing byte, stop at its end). */
private suspend fun HttpChunkDownloader.ftpFetchPiece(
    target: FtpTarget,
    login: FtpLogin,
    remote: String,
    piece: ChunkLayout.Piece,
    partPath: Path,
    rateLimiter: RateLimiter,
    layout: ChunkLayout
) {
    val existing = if (AppFileSystem.exists(partPath)) AppFileSystem.metadataOrNull(partPath)?.size ?: 0L else 0L
    piece.downloaded = min(existing, piece.length)
    if (piece.downloaded < existing) trimFile(partPath, piece.downloaded)
    if (piece.isCompleted) return
    val from = piece.start + piece.downloaded

    val session = openFtp(target, login)
    try {
        val data = try {
            session.retrieve(remote, from)
        } catch (e: FtpException) {
            throw ftpFailure(e)
        }
        try {
            pumpPiece(data.input, piece, partPath, rateLimiter, layout)
        } finally {
            runCatching { data.socket.close() }
        }
    } finally {
        session.close()
    }
}

/** The whole file over one connection (the server cannot resume or does not say the size). */
private suspend fun HttpChunkDownloader.ftpSingleStream(
    taskId: String,
    session: FtpSession,
    remote: String,
    totalLength: Long,
    canResume: Boolean,
    cachePath: String,
    finalDestPath: String
) {
    val partFile = "$cachePath.part".toPath()
    try {
        try {
            partFile.parent?.let { AppFileSystem.createDirectories(it) }
        } catch (_: Exception) {
        }
        val existing = if (AppFileSystem.exists(partFile)) AppFileSystem.metadataOrNull(partFile)?.size ?: 0L else 0L
        val startByte = if (canResume && existing > 0 && totalLength > 0 && existing < totalLength) existing else 0L

        updateTaskState(taskId) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                downloadedBytes = startByte,
                totalBytes = if (totalLength > 0) totalLength else it.totalBytes,
                connections = 1
            )
        }

        val data = try {
            session.retrieve(remote, startByte)
        } catch (e: FtpException) {
            throw ftpFailure(e)
        }
        val sink = if (startByte > 0) AppFileSystem.appendingSink(partFile).buffer() else AppFileSystem.sink(partFile).buffer()
        var downloaded = startByte
        try {
            val buffer = ByteArray(64 * 1024)
            val limiter = RateLimiter { speedLimitProvider(taskId) }
            val clock = TimeSource.Monotonic.markNow()
            val speedometer = Speedometer(windowDurationMs = 1200L)
            var lastUpdateMs = 0L
            while (!data.input.isClosedForRead) {
                val read = data.input.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                downloaded += read
                limiter.acquire(read)
                val nowMs = clock.elapsedNow().inWholeMilliseconds
                if (nowMs - lastUpdateMs >= 200L) {
                    lastUpdateMs = nowMs
                    val speed = speedometer.recordSample(nowMs, downloaded)
                    val effectiveTotal = if (totalLength > 0) totalLength else maxOf(downloaded * 2, 50L * 1024 * 1024)
                    updateTaskState(taskId) {
                        it.copy(
                            downloadedBytes = downloaded,
                            totalBytes = if (totalLength > 0) totalLength else downloaded,
                            progress = (downloaded.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 0.95f),
                            downloadSpeedBytesPerSec = speed
                        )
                    }
                }
            }
            sink.flush()
        } finally {
            sink.close()
            runCatching { data.socket.close() }
        }
        if (totalLength > 0 && downloaded != totalLength) {
            throw Exception("Connection closed early: got $downloaded of $totalLength bytes")
        }
    } finally {
        session.close()
    }

    finishSingleFile(taskId, partFile, finalDestPath, totalLength)
}

private suspend fun HttpChunkDownloader.finishSingleFile(taskId: String, partFile: Path, finalDestPath: String, totalLength: Long) {
    val savedPath = copyIntoPlace(partFile, finalDestPath.toPath())
    partFile.parent?.let { deleteRecursively(it) }
    val total = AppFileSystem.metadataOrNull(savedPath)?.size ?: if (totalLength > 0) totalLength else 0L
    updateTaskState(taskId) {
        it.copy(
            fileName = savedPath.name,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = total,
            totalBytes = total,
            progress = 1.0f,
            downloadSpeedBytesPerSec = 0L
        )
    }
}

/** curl's config-file quoting: a double-quoted value with \ and " escaped. */
private fun curlQuote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "") + "\""

private fun curlUrl(target: FtpTarget): String {
    val scheme = if (target.implicitTls) "ftps" else "ftp"
    val host = if (target.host.contains(':')) "[" + target.host + "]" else target.host
    val path = target.path.encodeToByteArray().joinToString("") { b ->
        val c = b.toInt() and 0xFF
        val safe = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || "-._~/".indexOf(c.toChar()) >= 0
        if (safe) c.toChar().toString() else "%" + c.toString(16).uppercase().padStart(2, '0')
    }
    return "$scheme://$host:${target.port}/$path"
}

private fun curlConfig(target: FtpTarget, login: FtpLogin, extra: List<String>): String = buildList {
    add("url = " + curlQuote(curlUrl(target)))
    add("user = " + curlQuote(login.user + ":" + login.password))
    add("silent")
    add("show-error")
    add("fail")
    add("connect-timeout = 20")
    if (!target.implicitTls) add("ssl-reqd")
    if (ftpInsecureForTests()) add("insecure")
    addAll(extra)
}.joinToString("\n", postfix = "\n")

internal suspend fun probeFtpLink(url: String): LinkProbeResult {
    if (NetworkSettings.proxy.value.isOn) return LinkProbeResult.Failed(LinkProbeCode.FTP_PROXY)
    val target = try {
        parseFtpUrl(url)
    } catch (_: IllegalArgumentException) {
        return LinkProbeResult.Failed(LinkProbeCode.INVALID_LINK)
    }
    if (target.isFolder) return LinkProbeResult.Failed(LinkProbeCode.FOLDER)
    val login = target.user?.let { FtpLogin(it, target.password.orEmpty()) }
        ?: NetworkSettings.loginFor(target.host)?.let { FtpLogin(it.user, it.password) }
        ?: FtpLogin("anonymous", "fetchjet@localhost")

    if (target.tls && !ftpTlsInProcess) {
        val head = runFtpTool(curlConfig(target, login, listOf("head")), listOf("-K", "-"))
            ?: return LinkProbeResult.Failed(LinkProbeCode.FTP_NO_TOOL)
        if (head.exitCode != 0) return LinkProbeResult.Failed(ftpToolProbeCode(head.exitCode))
        val size = Regex("(?i)content-length:\\s*(\\d+)").find(head.output)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
        return LinkProbeResult.Ok(if (size > 0L) size else -1L, size > 0L, target.fileName)
    }

    val session = try {
        FtpSession.open(target, login.user, login.password)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FtpException) {
        return LinkProbeResult.Failed(ftpProbeCode(e.code, e.message.orEmpty()))
    }
    try {
        var answer = session.command("SIZE ${target.path}")
        if (answer.code != 213) {
            if (session.isFolder(target.path)) return LinkProbeResult.Failed(LinkProbeCode.FOLDER)
            val absolute = session.command("SIZE ${target.absolutePath}")
            if (absolute.code == 213 || answer.code == 550) answer = absolute
        }
        if (answer.code == 213) {
            val size = answer.firstLine.trim().toLongOrNull() ?: -1L
            return LinkProbeResult.Ok(if (size > 0L) size else -1L, session.supportsRest && size > 0L, target.fileName)
        }
        if (answer.code == 550 && session.modified(target.path).isEmpty() && session.modified(target.absolutePath).isEmpty()) {
            return LinkProbeResult.Failed(LinkProbeCode.NOT_FOUND)
        }
        return LinkProbeResult.Ok(-1L, false, target.fileName)
    } catch (e: FtpException) {
        return LinkProbeResult.Failed(ftpProbeCode(e.code, e.message.orEmpty()))
    } finally {
        session.close()
    }
}

private fun curlFailure(result: FtpToolResult): Exception {
    val tail = result.output.trim().lines().lastOrNull { it.isNotBlank() }.orEmpty().take(160)
    val reason = when (result.exitCode) {
        6 -> "The server name could not be found"
        7 -> "Could not connect to the FTP server"
        9, 78 -> "The FTP server has no such file or folder"
        28 -> "The FTP server did not answer in time"
        35, 51, 58, 59, 60, 64, 77, 82, 83, 90, 91 -> "The server's certificate could not be verified or the secure connection failed"
        67 -> "The FTP server refused the login"
        else -> "FTP over TLS failed"
    }
    return Exception("$reason (curl ${result.exitCode})${if (tail.isNotEmpty()) ": $tail" else ""}")
}

private fun curlPieceFailure(result: FtpToolResult): Exception {
    val failure = curlFailure(result)
    val text = result.output.lowercase()
    val busy = result.exitCode == 67 || text.contains("421") || text.contains("too many") ||
            ((result.exitCode == 8 || result.exitCode == 9) && text.contains("530"))
    return when {
        busy -> ServerBusyException(failure.message ?: "FTP server busy")
        result.exitCode == 31 || result.exitCode == 36 -> RangeNotHonouredException(failure.message ?: "The FTP server cannot resume")
        else -> failure
    }
}

private fun fileSizeOrZero(path: Path): Long =
    if (AppFileSystem.exists(path)) AppFileSystem.metadataOrNull(path)?.size ?: 0L else 0L

private fun appendFileTo(from: Path, to: Path, limit: Long): Long {
    if (limit <= 0 || !AppFileSystem.exists(from)) return 0L
    var copied = 0L
    try {
        val source = AppFileSystem.source(from).buffer()
        try {
            val sink = AppFileSystem.appendingSink(to).buffer()
            try {
                val buffer = ByteArray(256 * 1024)
                while (copied < limit) {
                    val n = source.read(buffer, 0, min(buffer.size.toLong(), limit - copied).toInt())
                    if (n <= 0) break
                    sink.write(buffer, 0, n)
                    copied += n
                }
                sink.flush()
            } finally {
                sink.close()
            }
        } finally {
            source.close()
        }
    } catch (_: Exception) {
    }
    return copied
}

private suspend fun HttpChunkDownloader.ftpsFetchPiece(
    taskId: String,
    target: FtpTarget,
    login: FtpLogin,
    connections: Int,
    piece: ChunkLayout.Piece,
    partPath: Path,
    layout: ChunkLayout
) {
    val tempPath = "$partPath.curl".toPath()
    if (AppFileSystem.exists(tempPath)) runCatching { AppFileSystem.delete(tempPath) }
    if (AppFileSystem.exists(tempPath)) runCatching { AppFileSystem.sink(tempPath).close() }

    val existing = fileSizeOrZero(partPath)
    piece.downloaded = min(existing, piece.length)
    if (piece.downloaded < existing) trimFile(partPath, piece.downloaded)
    if (piece.isCompleted) return
    val have = piece.downloaded
    val from = piece.start + have
    val to = piece.end

    val limit = speedLimitProvider(taskId)
    val extra = buildList {
        add("range = " + curlQuote("$from-$to"))
        add("output = " + curlQuote(tempPath.toString()))
        add("speed-limit = 1")
        add("speed-time = 60")
        if (limit > 0) add("limit-rate = " + max(limit / connections.coerceAtLeast(1), 1024L))
    }

    var result: FtpToolResult? = null
    var stoppedEarly = false
    try {
        coroutineScope {
            val tool = async { runFtpTool(curlConfig(target, login, extra), listOf("-K", "-")) }
            var windowStart = TimeSource.Monotonic.markNow()
            var windowFrom = have
            while (tool.isActive) {
                withTimeoutOrNull(300) { tool.join() }
                val fetched = fileSizeOrZero(tempPath)
                val now = min(have + fetched, piece.length)
                if (now > piece.downloaded) {
                    piece.downloaded = now
                    piece.lastError = null
                    layout.lastProgressMs = layout.nowMs()
                }
                if (!tool.isActive) break
                if (have + fetched >= piece.length) {
                    stoppedEarly = true
                    tool.cancel()
                    break
                }
                val elapsed = windowStart.elapsedNow().inWholeMilliseconds
                if (elapsed >= 1000) {
                    piece.speedBytesPerSec = (now - windowFrom).coerceAtLeast(0L) * 1000 / elapsed
                    layout.noteSpeed(piece.speedBytesPerSec)
                    windowStart = TimeSource.Monotonic.markNow()
                    windowFrom = now
                    if (layout.isStraggler(piece)) throw SlowConnectionException()
                }
            }
            tool.join()
            if (!tool.isCancelled) result = tool.await()
        }
    } finally {
        val saved = appendFileTo(tempPath, partPath, piece.length - have)
        piece.downloaded = min(have + saved, piece.length)
        withContext(NonCancellable) {
            var tries = 0
            while (AppFileSystem.exists(tempPath) && runCatching { AppFileSystem.delete(tempPath) }.isFailure && tries++ < 10) delay(50)
        }
    }

    if (piece.isCompleted) return
    if (stoppedEarly) throw Exception("Piece ${piece.index}: the downloaded bytes could not be saved")
    val done = result ?: throw Exception("FTP over TLS needs the curl program, and it was not found on this device")
    if (done.exitCode != 0) throw curlPieceFailure(done)
    throw Exception("Piece ${piece.index}: connection closed early")
}

/** FTP over TLS where ktor has no TLS: the system's curl does the transfer into the part file. */
private suspend fun HttpChunkDownloader.ftpsWithTool(taskId: String, task: DownloadTask, target: FtpTarget, login: FtpLogin) {
    updateTaskState(taskId) { it.copy(status = DownloadStatus.CONNECTING, errorMessage = null) }
    val noTool = Exception("FTP over TLS needs the curl program, and it was not found on this device")
    val head = runFtpTool(curlConfig(target, login, listOf("head")), listOf("-K", "-")) ?: throw noTool
    if (head.exitCode != 0) throw curlFailure(head)
    val size = Regex("(?i)content-length:\\s*(\\d+)").find(head.output)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
    val modified = Regex("(?i)last-modified:\\s*([^\\r\\n]+)").find(head.output)?.groupValues?.get(1)?.trim().orEmpty()
    val identity = FileIdentity("", modified, size)

    val targetFileName = pathResolver.sanitizeFileName(task.fileName.ifBlank { target.fileName })
    val taskCacheDir = "${pathResolver.getTempDirectory()}/task_$taskId"
    val cachePath = "$taskCacheDir/$targetFileName"
    val finalDestPath = "${task.destinationPath}/$targetFileName"
    try {
        AppFileSystem.createDirectories(taskCacheDir.toPath())
    } catch (_: Exception) {
    }
    syncFileIdentity(taskId, taskCacheDir, cachePath, identity)

    var canResume = size > 0
    val connections = if (size > 0) connectionsFor(taskId, size) else 1
    val hasManifest = AppFileSystem.exists("$cachePath.chunks".toPath())
    if (size > 0 && (hasManifest || (size > FTP_SPLIT_MIN_BYTES && connections > 1))) {
        val probe = runFtpTool(curlConfig(target, login, listOf("range = " + curlQuote("1-1"))), listOf("-K", "-")) ?: throw noTool
        when (probe.exitCode) {
            0 -> {
                updateTaskState(taskId) { it.copy(fileName = targetFileName, serverSupportsResume = true) }
                downloadParallelChunks(
                    taskId, task.url, cachePath, finalDestPath, size, identity,
                    fetchPiece = { piece, partPath, _, layout ->
                        ftpsFetchPiece(taskId, target, login, connections, piece, partPath, layout)
                    }
                )
                return
            }
            31, 36 -> canResume = false
            else -> throw curlFailure(probe)
        }
    }
    updateTaskState(taskId) { it.copy(fileName = targetFileName, serverSupportsResume = canResume) }

    val partFile = "$cachePath.part".toPath()
    val existing = if (AppFileSystem.exists(partFile)) AppFileSystem.metadataOrNull(partFile)?.size ?: 0L else 0L
    val resume = canResume && existing > 0 && existing < size
    if (!resume && existing > 0) runCatching { AppFileSystem.delete(partFile) }
    updateTaskState(taskId) {
        it.copy(status = DownloadStatus.DOWNLOADING, downloadedBytes = if (resume) existing else 0L, totalBytes = if (size > 0) size else it.totalBytes, connections = 1)
    }

    val extra = buildList {
        add("output = " + curlQuote(partFile.toString()))
        if (resume) add("continue-at = -")
    }
    val result = coroutineScope {
        val watcher = launch {
            val clock = kotlin.time.TimeSource.Monotonic.markNow()
            val speedometer = Speedometer(windowDurationMs = 1200L)
            while (isActive) {
                delay(300)
                val have = AppFileSystem.metadataOrNull(partFile)?.size ?: 0L
                val speed = speedometer.recordSample(clock.elapsedNow().inWholeMilliseconds, have)
                val effectiveTotal = if (size > 0) size else maxOf(have * 2, 50L * 1024 * 1024)
                updateTaskState(taskId) {
                    it.copy(
                        downloadedBytes = have,
                        totalBytes = if (size > 0) size else have,
                        progress = (have.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 0.95f),
                        downloadSpeedBytesPerSec = speed
                    )
                }
            }
        }
        try {
            runFtpTool(curlConfig(target, login, extra), listOf("-K", "-")) ?: throw noTool
        } finally {
            watcher.cancel()
        }
    }
    if (result.exitCode != 0) throw curlFailure(result)
    val got = AppFileSystem.metadataOrNull(partFile)?.size ?: 0L
    if (size > 0 && got != size) throw Exception("Connection closed early: got $got of $size bytes")
    finishSingleFile(taskId, partFile, finalDestPath, size)
}
