package com.michelle.raouf.fetch_jet.feature.media_extractor

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

internal suspend fun MediaStreamDownloader.resolveMediaFileName(
    initialTask: DownloadTask,
    effectiveUrl: String
): String {
    val resolvedTitle = try {
        if (isYouTubeUrl(effectiveUrl)) fetchYouTubeTitle(effectiveUrl) else fetchGenericMediaTitle(
            effectiveUrl
        )
    } catch (_: Exception) {
        null
    }
    val mediaTitle = resolvedTitle ?: extractMediaTitle(effectiveUrl)
    val isInvalidExtension = initialTask.fileName.matches(
        Regex(
            """.*\.(php|asp|aspx|jsp|html|htm|cgi|do|action)$""",
            RegexOption.IGNORE_CASE
        )
    )
    // An HLS / DASH playlist (.m3u8 / .mpd, e.g. captured by the browser extension on Udemy)
    // is downloaded and merged into one video: the file must not keep the playlist's name.
    val isPlaylistName =
        initialTask.fileName.matches(Regex(""".*\.(m3u8|mpd)$""", RegexOption.IGNORE_CASE))
    val rawFileName = when {
        initialTask.fileName.isNotBlank() && isPlaylistName ->
            initialTask.fileName.replace(
                Regex("""\.(m3u8|mpd)$""", RegexOption.IGNORE_CASE),
                ".mp4"
            )

        initialTask.fileName.isNotBlank() && !isInvalidExtension -> initialTask.fileName
        else -> "$mediaTitle.mp4"
    }
    return pathResolver.sanitizeFileName(rawFileName)
}

internal suspend fun MediaStreamDownloader.downloadWithOwnHls(
    taskId: String,
    initialTask: DownloadTask,
    downloadSourceUrl: String,
    cacheDir: String,
    cacheTargetPath: okio.Path
): Boolean = HlsDownloader.download(
    client = client,
    playlistUrl = downloadSourceUrl,
    headers = initialTask.requestHeaders,
    workDir = cacheDir,
    outputFile = cacheTargetPath.toString(),
    parallel = initialTask.maxConnections.takeIf { it > 0 }
        ?: com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings.connections.value.takeIf { it > 0 }
        ?: 6,
    isCancelled = { tasksState.value[taskId]?.status == DownloadStatus.PAUSED },
    onPieces = { pieces, workers ->
        updateTaskState(taskId) {
            if (it.status == DownloadStatus.PAUSED) it
            else it.copy(chunks = pieces, connections = workers)
        }
    },
    onProgress = { fraction, got, est, speed ->
        updateTaskState(taskId) {
            if (it.status == DownloadStatus.PAUSED) it
            else it.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = fraction.coerceIn(0f, 0.99f),
                downloadedBytes = got,
                totalBytes = est,
                approximateTotalBytes = est,
                downloadSpeedBytesPerSec = if (speed > 0L) speed else it.downloadSpeedBytesPerSec,
                currentStepText = "Downloading parts…"
            )
        }
    },
    onPostProcess = { label, fraction ->
        reportPostProcess(
            taskId,
            label,
            fraction
        )
    },
    clipStartSec = initialTask.clipStart?.takeIf { it.isNotBlank() }
        ?.let { parseTimeToSecondsOrNull(it) },
    clipEndSec = initialTask.clipEnd?.takeIf { it.isNotBlank() }
        ?.let { parseTimeToSecondsOrNull(it) }
)

internal suspend fun MediaStreamDownloader.downloadWithYtDlp(
    taskId: String,
    initialTask: DownloadTask,
    downloadSourceUrl: String,
    cacheDir: String,
    fileName: String,
    isClipping: Boolean,
    currentLimit: Long,
    streams: MultiStreamProgress
): Boolean = downloadMediaWithPlatformTool(
    url = downloadSourceUrl,
    destinationDir = cacheDir,
    fileName = fileName,
    formatId = initialTask.formatId,
    clipStart = initialTask.clipStart,
    clipEnd = initialTask.clipEnd,
    clipByCutting = initialTask.clipByCutting,
    speedLimitBytesPerSec = currentLimit,
    speedLimitProvider = { speedLimitProvider(taskId) },
    requestHeaders = initialTask.requestHeaders,
    onProgress = { progress, downloadedBytes, reportedTotalBytes, speedBytesPerSec, stepText ->
        if (tasksState.value[taskId]?.status != DownloadStatus.PAUSED) {
            val combined = if (isClipping) null
            else streams.update(
                progress,
                downloadedBytes,
                reportedTotalBytes,
                stepText
            )
            updateTaskState(taskId) {
                if (it.status == DownloadStatus.PAUSED) it
                else if (combined != null) {
                    // Video + audio counted together: bar == downloaded / total.
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = combined.fraction,
                        downloadedBytes = combined.downloaded,
                        totalBytes = combined.total,
                        approximateTotalBytes = if (combined.totalIsEstimate) combined.total else 0L,
                        downloadSpeedBytesPerSec = if (speedBytesPerSec > 0L) speedBytesPerSec else it.downloadSpeedBytesPerSec,
                        currentStepText = stepText ?: it.currentStepText
                    )
                } else if (isClipping) {
                    // A section clip's size is only an estimate: the one made when the
                    // download was added (full size x clip share) assumes an even bitrate
                    // and was shown as a fixed total - the clip then passed it
                    // ("90.7 MB / 76.7 MB"). Follow the real rate instead: bytes so far /
                    // share of the clip time done, never below what is already on disk.
                    val got =
                        if (downloadedBytes > 0L) downloadedBytes else it.downloadedBytes
                    val live =
                        if (got > 0L && progress >= 0.03f) (got / progress).toLong() else 0L
                    val clipTotal = (if (live > 0L) live else maxOf(
                        it.totalBytes,
                        reportedTotalBytes
                    ))
                        .coerceAtLeast(got)
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = if (progress > 0f) progress else it.progress,
                        downloadedBytes = got,
                        totalBytes = clipTotal,
                        approximateTotalBytes = clipTotal, // shown with "~"
                        downloadSpeedBytesPerSec = if (speedBytesPerSec > 0L) speedBytesPerSec else it.downloadSpeedBytesPerSec,
                        currentStepText = stepText ?: it.currentStepText
                    )
                } else {
                    val newApprox =
                        if (isClipping && downloadedBytes > 0 && progress > 0.02f) {
                            (downloadedBytes / progress).toLong()
                        } else it.approximateTotalBytes

                    val newTotal = when {
                        it.totalBytes > 0L -> it.totalBytes
                        reportedTotalBytes > 0L -> reportedTotalBytes
                        else -> 0L
                    }

                    val finalProgress = if (progress > 0f) progress
                    else if (newTotal > 0L && downloadedBytes > 0L) {
                        (downloadedBytes.toFloat() / newTotal.toFloat()).coerceIn(
                            0f,
                            0.99f
                        )
                    } else it.progress

                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = finalProgress,
                        downloadedBytes = if (downloadedBytes > 0) downloadedBytes else it.downloadedBytes,
                        totalBytes = newTotal,
                        approximateTotalBytes = newApprox,
                        downloadSpeedBytesPerSec = if (speedBytesPerSec > 0L) speedBytesPerSec else it.downloadSpeedBytesPerSec,
                        currentStepText = stepText ?: it.currentStepText
                    )
                }
            }
        }
    },
    onPostProcess = { label, fraction ->
        reportPostProcess(
            taskId,
            label,
            fraction
        )
    }
)

internal fun findFinishedFileInCache(
    cacheDir: String,
    cacheTargetPath: okio.Path,
    fileName: String
): okio.Path? {
    val baseName = fileName.substringBeforeLast(".")
    val searchPrefix = baseName.take(15)
    var foundCachePath: okio.Path? = null
    if (FileSystem.SYSTEM.exists(cacheTargetPath)) {
        foundCachePath = cacheTargetPath
    } else {
        try {
            val list = FileSystem.SYSTEM.list(cacheDir.toPath())
            foundCachePath = list.firstOrNull {
                val n = it.name
                !n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".temp") &&
                        !Regex("""\.f\d+\.\w+$""").containsMatchIn(n) &&
                        (n == fileName || n.startsWith(baseName) || n.startsWith(
                            searchPrefix
                        ) || baseName.startsWith(n.substringBeforeLast(".")))
            } ?: list.filter {
                val n = it.name.lowercase()
                !n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".temp") &&
                        (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(
                            ".m4a"
                        ) || n.endsWith(".mp3"))
            }.maxByOrNull {
                try {
                    FileSystem.SYSTEM.metadata(it).lastModifiedAtMillis ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }
        } catch (_: Exception) {
        }
    }
    return foundCachePath
}

internal suspend fun MediaStreamDownloader.moveFinishedFileIntoPlace(
    taskId: String,
    initialTask: DownloadTask,
    foundCachePath: okio.Path?,
    finalTargetPath: okio.Path,
    cacheDir: String
): Boolean {
    var downloadedRealFile = false
    if (foundCachePath != null && FileSystem.SYSTEM.exists(foundCachePath)) {
        val completedSize = try {
            FileSystem.SYSTEM.metadata(foundCachePath).size ?: 0L
        } catch (_: Exception) {
            0L
        }
        if (completedSize > 1024L) {
            try {
                finalTargetPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            } catch (_: Exception) {
            }
            val actualFinalPath =
                "${initialTask.destinationPath}/${foundCachePath.name}".toPath()
            val savedPath = moveIntoPlace(taskId, foundCachePath, actualFinalPath)
            deleteRecursively(cacheDir.toPath())
            updateTaskState(taskId) {
                it.copy(
                    fileName = savedPath.name,
                    status = DownloadStatus.COMPLETED,
                    progress = 1.0f,
                    downloadedBytes = completedSize,
                    totalBytes = completedSize,
                    downloadSpeedBytesPerSec = 0L,
                    currentStepText = "Completed"
                )
            }
            downloadedRealFile = true
        }
    }
    return downloadedRealFile
}

internal suspend fun MediaStreamDownloader.downloadWithHttpFallback(
    taskId: String,
    initialTask: DownloadTask,
    effectiveUrl: String,
    cacheDir: String,
    cacheTargetPath: okio.Path,
    finalTargetPath: okio.Path
): Boolean {
    var downloadedRealFile = false
    if (isYouTubeUrl(effectiveUrl) && isMediaExtractorToolInstalled()) {
        throw Exception("Failed to download YouTube media. Verify the URL or select a different quality.")
    }

    val isDirectVideo =
        initialTask.url.contains("googlevideo.com") || initialTask.url.contains("fbcdn.net") || initialTask.url.contains(
            "video-downloads"
        ) || initialTask.url.endsWith(".mp4") || initialTask.url.endsWith(".webm") || initialTask.url.contains(
            "manifest"
        ) || initialTask.url.contains("m3u8") || initialTask.url.contains("videoplayback")

    val directStreamUrl = if (isDirectVideo) {
        initialTask.url
    } else if (isYouTubeUrl(effectiveUrl)) {
        resolveAnyValidYouTubeStream(effectiveUrl)
    } else {
        val response: HttpResponse = client.get(effectiveUrl) {
            header(HttpHeaders.UserAgent, desktopUserAgent)
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,video/*,*/*;q=0.8"
            )
        }
        if (response.status.isSuccess()) {
            val contentType = response.headers[HttpHeaders.ContentType] ?: ""
            if (contentType.contains("video") || contentType.contains("octet-stream")) {
                downloadStreamToPath(taskId, response, cacheTargetPath)
                try {
                    finalTargetPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                } catch (_: Exception) {
                }
                if (FileSystem.SYSTEM.exists(finalTargetPath)) {
                    FileSystem.SYSTEM.delete(finalTargetPath)
                }
                FileSystem.SYSTEM.copy(cacheTargetPath, finalTargetPath)
                deleteRecursively(cacheDir.toPath())
                downloadedRealFile = true
                null
            } else {
                val htmlBody = response.bodyAsText()
                extractHighQualityVideoUrl(effectiveUrl, htmlBody)
            }
        } else null
    }

    if (!downloadedRealFile && !directStreamUrl.isNullOrBlank()) {
        try {
            val videoResponse: HttpResponse = client.get(directStreamUrl) {
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                header(HttpHeaders.Accept, "*/*")
                header("Sec-Fetch-Mode", "navigate")
                if (directStreamUrl.contains("googlevideo.com")) {
                    header("Referer", "https://www.youtube.com/")
                    header("Origin", "https://www.youtube.com")
                } else if (directStreamUrl.contains("fbcdn.net") || directStreamUrl.contains(
                        "facebook.com"
                    )
                ) {
                    header("Referer", "https://www.facebook.com/")
                    header("Origin", "https://www.facebook.com")
                }
            }
            if (videoResponse.status.isSuccess()) {
                downloadStreamToPath(taskId, videoResponse, cacheTargetPath)
                try {
                    finalTargetPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                } catch (_: Exception) {
                }
                if (FileSystem.SYSTEM.exists(finalTargetPath)) {
                    FileSystem.SYSTEM.delete(finalTargetPath)
                }
                FileSystem.SYSTEM.copy(cacheTargetPath, finalTargetPath)
                deleteRecursively(cacheDir.toPath())
                downloadedRealFile = true
            } else {
                throw Exception("HTTP Error: ${videoResponse.status.value}")
            }
        } catch (innerE: CancellationException) {
            throw innerE
        } catch (innerE: Exception) {
            throw Exception("Failed to fetch stream: ${innerE.message}")
        }
    }
    return downloadedRealFile
}
