package com.michelle.raouf.fetch_jet.feature.media_extractor


import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

/**
 * YouTube-style download with FetchJet's own engine. Returns false when the page is not a
 * plain-stream page (yt-dlp should download it), true when the finished file is at
 * [cacheTargetPath]; throws when a download started and failed.
 */
internal suspend fun MediaStreamDownloader.downloadWithOwnEngine(
    taskId: String,
    task: DownloadTask,
    sourceUrl: String,
    cacheDir: String,
    cacheTargetPath: String,
    fileName: String
): Boolean {
    val audioTarget = Regex(
        """\.(mp3|m4a|aac|opus|ogg|flac|wav)$""",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(fileName)
    updateTaskState(taskId) {
        if (it.status == DownloadStatus.PAUSED) it else it.copy(currentStepText = "Resolving streams…")
    }
    var headersUsed = task.requestHeaders
    val resolved =
        DirectMediaResolver.resolve(sourceUrl, task.formatId, audioTarget, headersUsed)
            ?: if (headersUsed.isNotEmpty()) {
                headersUsed = emptyMap()
                DirectMediaResolver.resolve(sourceUrl, task.formatId, audioTarget, headersUsed)
            } else null
    if (resolved == null) {
        updateTaskState(taskId) {
            if (it.status == DownloadStatus.PAUSED) it else it.copy(currentStepText = "yt-dlp fallback · not a plain stream")
        }
        return false
    }
    resolved.hlsUrl?.let { hlsUrl ->
        com.michelle.raouf.fetch_jet.core.log.AppLog.d { "MediaStream: own HLS engine for the chosen format" }
        updateTaskState(taskId) {
            if (it.status == DownloadStatus.PAUSED) it else it.copy(currentStepText = "Downloading parts…")
        }
        return downloadWithOwnHls(
            taskId,
            task.copy(requestHeaders = resolved.hlsHeaders + headersUsed),
            hlsUrl,
            cacheDir,
            cacheTargetPath.toPath()
        )
    }
    // video first, then audio
    val streams = resolved.streams.sortedByDescending { it.hasVideo }
    val connections = task.maxConnections.takeIf { it > 0 }
        ?: com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.connections.value.takeIf { it > 0 }
        ?: 8
    val limiter =
        com.michelle.raouf.fetch_jet.core.network.RateLimiter { speedLimitProvider(taskId) }
    val fetcher = com.michelle.raouf.fetch_jet.core.network.PieceDownloader(streamClient)

    com.michelle.raouf.fetch_jet.core.log.AppLog.d {
        "MediaStream: own engine · ${streams.size} stream(s) · $connections connections · " +
                streams.joinToString { "${if (it.hasVideo) "video" else "audio"} ${it.ext} ${it.size / 1048576} MB" }
    }
    val knownSizes = LongArray(streams.size) { streams[it].size.coerceAtLeast(0L) }
    val doneBytes = LongArray(streams.size)
    val files = ArrayList<String>()
    for ((i, stream) in streams.withIndex()) {
        val label = when {
            streams.size == 1 -> "Downloading"
            stream.hasVideo -> "Downloading video (1/2)"
            else -> "Downloading audio (2/2)"
        }
        val out = "$cacheDir/stream$i.${stream.ext}"
        files.add(out)
        if (FileSystem.SYSTEM.exists(out.toPath()) && knownSizes[i] > 0 &&
            FileSystem.SYSTEM.metadataOrNull(out.toPath())?.size == knownSizes[i]
        ) {
            doneBytes[i] = knownSizes[i]; continue // finished on an earlier run
        }
        fetcher.download(
            urlProvider = { refresh ->
                if (!refresh) stream.url
                else DirectMediaResolver.resolve(
                    sourceUrl,
                    task.formatId,
                    audioTarget,
                    headersUsed
                )
                    ?.streams?.firstOrNull { it.hasVideo == stream.hasVideo && it.hasAudio == stream.hasAudio }?.url
                    ?: stream.url
            },
            headers = stream.headers,
            partBase = "$cacheDir/stream$i",
            outputFile = out,
            totalLength = knownSizes[i].takeIf { it > 0 } ?: -1L,
            connections = connections,
            rateLimiter = limiter,
            onProgress = { p ->
                if (knownSizes[i] <= 0 && p.total > 0) knownSizes[i] = p.total
                doneBytes[i] = p.downloaded
                val total = knownSizes.sum()
                val got = doneBytes.sum()
                updateTaskState(taskId) {
                    if (it.status == DownloadStatus.PAUSED) it
                    else it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = if (total > 0) (got.toFloat() / total).coerceIn(
                            0f,
                            0.99f
                        ) else it.progress,
                        downloadedBytes = got,
                        totalBytes = total,
                        approximateTotalBytes = 0L,
                        downloadSpeedBytesPerSec = p.speedBytesPerSec,
                        chunks = p.chunks,
                        connections = p.connections,
                        currentStepText = label
                    )
                }
            }
        )
    }

    // Merge / remux into the target name with the local ffmpeg.
    updateTaskState(taskId) {
        if (it.status == DownloadStatus.PAUSED) it else it.copy(
            chunks = emptyList(),
            downloadSpeedBytesPerSec = 0L
        )
    }
    val target = cacheTargetPath
    val targetExt = fileName.substringAfterLast('.', "mp4").lowercase()
    val ok: Boolean = when {
        files.size == 2 -> {
            reportPostProcess(taskId, "Merging video and audio", 0.3f)
            runLocalFfmpeg(
                listOf(
                    "-y",
                    "-i",
                    files[0],
                    "-i",
                    files[1],
                    "-c",
                    "copy",
                    "-movflags",
                    "+faststart",
                    target
                )
            ) ||
                    runLocalFfmpeg(
                        listOf(
                            "-y",
                            "-i",
                            files[0],
                            "-i",
                            files[1],
                            "-c:v",
                            "copy",
                            "-c:a",
                            "aac",
                            "-movflags",
                            "+faststart",
                            target
                        )
                    )
        }

        audioTarget && targetExt == "mp3" -> {
            reportPostProcess(taskId, "Converting to MP3", 0.3f)
            runLocalFfmpeg(
                listOf(
                    "-y",
                    "-i",
                    files[0],
                    "-vn",
                    "-c:a",
                    "libmp3lame",
                    "-q:a",
                    "2",
                    target
                )
            )
        }

        files[0].substringAfterLast('.').equals(targetExt, true) -> {
            try {
                FileSystem.SYSTEM.atomicMove(files[0].toPath(), target.toPath()); true
            } catch (_: Exception) {
                false
            }
        }

        else -> {
            reportPostProcess(taskId, "Saving as .$targetExt", 0.3f)
            runLocalFfmpeg(
                listOf(
                    "-y",
                    "-i",
                    files[0],
                    "-c",
                    "copy",
                    "-movflags",
                    "+faststart",
                    target
                )
            ) ||
                    try {
                        FileSystem.SYSTEM.atomicMove(files[0].toPath(), target.toPath()); true
                    } catch (_: Exception) {
                        false
                    }
        }
    }
    if (!ok || !FileSystem.SYSTEM.exists(target.toPath())) throw Exception("ffmpeg could not put the streams together")

    // "Cut afterwards" clip: stream-copy the chosen part out of the full file.
    if (task.clipByCutting && (!task.clipStart.isNullOrBlank() || !task.clipEnd.isNullOrBlank())) {
        reportPostProcess(taskId, "Cutting the clip", 0.7f)
        val cut = "$cacheDir/cut.$targetExt"
        val args = mutableListOf("-y")
        task.clipStart?.takeIf { it.isNotBlank() }?.let { args += listOf("-ss", it) }
        task.clipEnd?.takeIf { it.isNotBlank() }?.let { args += listOf("-to", it) }
        args += listOf("-i", target, "-c", "copy", "-movflags", "+faststart", cut)
        if (runLocalFfmpeg(args) && FileSystem.SYSTEM.exists(cut.toPath())) {
            try {
                FileSystem.SYSTEM.delete(target.toPath())
            } catch (_: Exception) {
            }
            FileSystem.SYSTEM.atomicMove(cut.toPath(), target.toPath())
        }
    }
    for (f in files) try {
        FileSystem.SYSTEM.delete(f.toPath())
    } catch (_: Exception) {
    }
    reportPostProcess(taskId, "", POST_PROCESS_CLEAR)
    return true
}

internal suspend fun MediaStreamDownloader.downloadStreamToPath(

    taskId: String,

    response: HttpResponse,

    targetPath: okio.Path

) {

    val channel: ByteReadChannel = response.bodyAsChannel()

    val totalLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L



    updateTaskState(taskId) {

        it.copy(

            status = DownloadStatus.DOWNLOADING,

            totalBytes = if (totalLength > 0) totalLength else it.totalBytes

        )

    }


    val fileSink = FileSystem.SYSTEM.sink(targetPath).buffer()

    val buffer = ByteArray(8192)

    var downloaded = 0L

    val timeSource = TimeSource.Monotonic

    var mark = timeSource.markNow()

    var bytesSinceMark = 0L

    val rateLimiter = com.michelle.raouf.fetch_jet.core.network.RateLimiter {

        speedLimitProvider(taskId)

    }



    try {

        var chunksSinceUpdate = 0



        while (!channel.isClosedForRead) {
            if (!coroutineContext.isActive || tasksState.value[taskId]?.status == DownloadStatus.PAUSED) {
                // Breaking here let the caller copy a truncated file to the destination
                // and mark it COMPLETED. Abort instead.
                throw CancellationException("Download paused or cancelled")
            }

            val read = channel.readAvailable(buffer, 0, buffer.size)

            if (read <= 0) break



            fileSink.write(buffer, 0, read)

            downloaded += read

            bytesSinceMark += read

            chunksSinceUpdate++

            rateLimiter.acquire(read)


            val elapsedMs = mark.elapsedNow().inWholeMilliseconds

            var speed = 0L



            if (elapsedMs >= 500) {

                speed = (bytesSinceMark * 1000) / elapsedMs

                mark = timeSource.markNow()

                bytesSinceMark = 0L

            }



            if (chunksSinceUpdate >= 16 || elapsedMs >= 500) {

                chunksSinceUpdate = 0

                val effectiveTotal = if (totalLength > 0) totalLength else maxOf(

                    downloaded * 2,

                    50 * 1024 * 1024L

                )

                val prog =

                    if (effectiveTotal > 0) (downloaded.toFloat() / effectiveTotal.toFloat()).coerceIn(

                        0f,

                        0.95f

                    ) else 0f



                updateTaskState(taskId) {

                    it.copy(

                        downloadedBytes = downloaded,

                        totalBytes = if (totalLength > 0) totalLength else downloaded,

                        progress = prog,

                        downloadSpeedBytesPerSec = if (speed > 0) speed else it.downloadSpeedBytesPerSec

                    )

                }

            }

        }


        val effectiveTotal = if (totalLength > 0) totalLength else downloaded

        updateTaskState(taskId) {

            it.copy(

                downloadedBytes = downloaded,

                totalBytes = effectiveTotal,

                progress = 1.0f,

                downloadSpeedBytesPerSec = 0L

            )

        }

    } finally {

        fileSink.close()

    }

}

/**
 * Puts the finished file into the user's folder. A rename when cache and destination are on
 * the same drive (instant); otherwise a copy that reports its progress on the second line,
 * written under a temporary name so an interrupted copy never leaves a half file behind.
 */
internal suspend fun MediaStreamDownloader.moveIntoPlace(
    taskId: String,
    from: okio.Path,
    target: okio.Path
): okio.Path {
    val to = if (FileSystem.SYSTEM.exists(target) &&
        runCatching { FileSystem.SYSTEM.delete(target) }.isFailure
    ) freePathNextTo(target) else target
    try {
        FileSystem.SYSTEM.atomicMove(from, to)
        return to
    } catch (_: Exception) {
        // different drive: copy below
    }
    val total = FileSystem.SYSTEM.metadataOrNull(from)?.size ?: 0L
    val partial = "$to.fetchjet-part".toPath()
    reportPostProcess(taskId, "Saving to folder", if (total > 0L) 0f else -1f)
    try {
        FileSystem.SYSTEM.source(from).buffer().use { src ->
            FileSystem.SYSTEM.sink(partial).buffer().use { dst ->
                val buffer = okio.Buffer()
                var copied = 0L
                var lastReport = 0L
                while (true) {
                    val n = src.read(buffer, 1L shl 20)
                    if (n == -1L) break
                    dst.write(buffer, n)
                    copied += n
                    coroutineContext.ensureActive()
                    val now = nowEpochMs()
                    if (total > 0L && now - lastReport >= 300L) {
                        lastReport = now
                        reportPostProcess(
                            taskId, "Saving to folder",
                            (copied.toDouble() / total).toFloat().coerceIn(0f, 1f)
                        )
                    }
                }
            }
        }
        FileSystem.SYSTEM.atomicMove(partial, to)
        FileSystem.SYSTEM.delete(from)
    } catch (e: Exception) {
        try {
            FileSystem.SYSTEM.delete(partial)
        } catch (_: Exception) {
        }
        throw e
    }
    return to
}

internal fun freePathNextTo(path: okio.Path): okio.Path {
    val name = path.name
    val base = name.substringBeforeLast('.', name)
    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
    val dir = path.parent ?: return path
    var n = 1
    while (true) {
        val candidate = dir / "$base ($n)$ext"
        if (!FileSystem.SYSTEM.exists(candidate)) return candidate
        n++
    }
}

internal fun MediaStreamDownloader.reportPostProcess(
    taskId: String,
    label: String,
    fraction: Float
) {
    updateTaskState(taskId) {
        // Only a running task gets the line: a late tick from a cancelled job must not
        // pull a paused/queued/restarted task back to DOWNLOADING.
        if (it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.CONNECTING) it
        else if (fraction == POST_PROCESS_CLEAR) it.copy(
            postProcessLabel = null, postProcessProgress = -1f, postProcessStartedAtMs = 0L
        )
        else it.copy(
            status = DownloadStatus.DOWNLOADING,
            postProcessLabel = label,
            postProcessProgress = fraction,
            postProcessStartedAtMs = if (it.postProcessLabel == label && it.postProcessStartedAtMs > 0L)
                it.postProcessStartedAtMs else nowEpochMs(),
            downloadSpeedBytesPerSec = 0L
        )
    }
}
