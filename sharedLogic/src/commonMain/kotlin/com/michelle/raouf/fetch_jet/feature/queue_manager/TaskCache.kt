package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.core.filesystem.relocateAppPath
import com.michelle.raouf.fetch_jet.core.security.Secrets
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

@Serializable
internal data class PersistedTask(
    val id: String = "",
    val url: String = "",
    val fileName: String = "",
    val destinationPath: String = "",
    val engineType: String = EngineType.HTTP_DIRECT.name,
    val status: String = DownloadStatus.PAUSED.name,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progress: Float = 0f,
    val createdAtEpochMs: Long = 0L,
    val formatId: String = "",
    val originalUrl: String = "",
    val pairedAudioUrl: String = "",
    val clipStart: String = "",
    val clipEnd: String = "",
    val currentStepText: String = "",
    val errorMessage: String = "",
    val disableUpload: Boolean = false,
    val clipByCutting: Boolean = false,
    val serverSupportsResume: String = "",
    val maxDownloadSpeedBytesPerSec: Long = 0L,
    val maxUploadSpeedBytesPerSec: Long = 0L,
    val hdrCookie: String = "",
    val hdrReferer: String = "",
    val hdrUserAgent: String = "",
    val torrentFiles: String = "",
    val webSeeds: String = "",
    val scheduledAtMs: Long = 0L,
    val maxConnections: Int = 0,
    val expectedChecksum: String = "",
    val checksumVerified: String = "",
    val subtitles: String = "",
    val embedSubtitles: Boolean = false,
    val priority: Int = 0
)

internal object TaskCache {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    fun encode(tasks: List<DownloadTask>): String =
        json.encodeToString(
            ListSerializer(PersistedTask.serializer()),
            tasks.map { it.toPersisted() })

    fun decode(text: String, onBroken: (Throwable) -> Unit = {}): List<PersistedTask> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            try {
                json.decodeFromJsonElement(PersistedTask.serializer(), element)
                    .takeIf { it.id.isNotBlank() }
            } catch (e: Throwable) {
                onBroken(e)
                null
            }
        }
}

internal fun DownloadTask.toPersisted() = PersistedTask(
    id = id,
    url = sealed(url),
    fileName = fileName,
    destinationPath = destinationPath,
    engineType = engineType.name,
    status = status.name,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    progress = progress,
    createdAtEpochMs = createdAtEpochMs,
    formatId = formatId.orEmpty(),
    originalUrl = sealed(originalUrl.orEmpty()),
    pairedAudioUrl = sealed(pairedAudioUrl.orEmpty()),
    clipStart = clipStart.orEmpty(),
    clipEnd = clipEnd.orEmpty(),
    currentStepText = currentStepText.orEmpty(),
    errorMessage = errorMessage.orEmpty(),
    disableUpload = disableUpload,
    clipByCutting = clipByCutting,
    serverSupportsResume = serverSupportsResume?.toString().orEmpty(),
    maxDownloadSpeedBytesPerSec = maxDownloadSpeedBytesPerSec,
    maxUploadSpeedBytesPerSec = maxUploadSpeedBytesPerSec,
    hdrCookie = sealed(requestHeaders["Cookie"].orEmpty()),
    hdrReferer = requestHeaders["Referer"].orEmpty(),
    hdrUserAgent = requestHeaders["User-Agent"].orEmpty(),
    torrentFiles = torrentFiles.joinToString(";") {
        "${it.index}:${if (it.isSelected) 1 else 0}:${it.priority}"
    },
    webSeeds = webSeedUrls.joinToString(" "),
    scheduledAtMs = scheduledAtMs,
    maxConnections = maxConnections,
    expectedChecksum = expectedChecksum.orEmpty(),
    checksumVerified = checksumVerified?.toString().orEmpty(),
    subtitles = subtitleTracks.joinToString("\u0002") { t ->
        listOf(
            t.langCode, t.langName, t.ext,
            if (t.isAuto) "1" else "0", if (t.isSelected) "1" else "0", sealed(t.url)
        ).joinToString("\u0001")
    },
    embedSubtitles = embedSubtitles,
    priority = priority
)

internal fun PersistedTask.toTask(
    defaultDestination: String,
    forceStatus: DownloadStatus? = null
): DownloadTask {
    val engine = runCatching { EngineType.valueOf(engineType) }.getOrDefault(EngineType.HTTP_DIRECT)
    val stored = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.PAUSED)
    val restoredStatus = forceStatus ?: when (stored) {
        DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING -> DownloadStatus.PAUSED
        else -> stored
    }
    return DownloadTask(
        id = id,
        url = Secrets.reveal(url) ?: url,
        fileName = fileName,
        destinationPath = relocateAppPath(destinationPath.ifBlank { defaultDestination }),
        engineType = engine,
        status = restoredStatus,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        progress = progress,
        createdAtEpochMs = createdAtEpochMs,
        formatId = formatId.ifBlank { null },
        originalUrl = (Secrets.reveal(originalUrl) ?: originalUrl).ifBlank { null },
        pairedAudioUrl = (Secrets.reveal(pairedAudioUrl) ?: pairedAudioUrl).ifBlank { null },
        clipStart = clipStart.ifBlank { null },
        clipEnd = clipEnd.ifBlank { null },
        clipByCutting = clipByCutting,
        serverSupportsResume = serverSupportsResume.toBooleanStrictOrNull(),
        currentStepText = currentStepText.ifBlank { null },
        errorMessage = errorMessage.ifBlank { null }
            ?.takeIf { restoredStatus == DownloadStatus.ERROR },
        disableUpload = disableUpload,
        maxDownloadSpeedBytesPerSec = maxDownloadSpeedBytesPerSec,
        maxUploadSpeedBytesPerSec = maxUploadSpeedBytesPerSec,
        requestHeaders = buildMap {
            Secrets.reveal(hdrCookie)?.ifBlank { null }?.let { put("Cookie", it) }
            hdrReferer.ifBlank { null }?.let { put("Referer", it) }
            hdrUserAgent.ifBlank { null }?.let { put("User-Agent", it) }
        },
        torrentFiles = torrentFiles.ifBlank { null }?.split(';')?.mapNotNull { entry ->
            val f = entry.split(':')
            val index = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            TorrentFileEntry(
                index = index,
                path = "",
                size = 0L,
                isSelected = f.getOrNull(1) != "0",
                priority = f.getOrNull(2)?.toIntOrNull() ?: 0
            )
        } ?: emptyList(),
        webSeedUrls = webSeeds.split(' ').filter { it.isNotBlank() },
        scheduledAtMs = scheduledAtMs,
        maxConnections = maxConnections,
        expectedChecksum = expectedChecksum.ifBlank { null },
        checksumVerified = checksumVerified.toBooleanStrictOrNull(),
        subtitleTracks = subtitles.ifBlank { null }?.split('\u0002')?.mapNotNull { entry ->
            val f = entry.split('\u0001')
            val code = f.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = f.getOrNull(5).orEmpty()
            SubtitleTrack(
                langCode = code,
                langName = f.getOrNull(1).orEmpty().ifBlank { code },
                ext = f.getOrNull(2).orEmpty(),
                url = Secrets.reveal(url) ?: url,
                isAuto = f.getOrNull(3) == "1",
                isSelected = f.getOrNull(4) != "0"
            )
        } ?: emptyList(),
        embedSubtitles = embedSubtitles,
        priority = priority
    )
}

// Links (signed tokens, session ids) and cookies are stored encrypted. The cache is written
// every second: encrypt each value once, not on every write.
private val sealedValues = mutableMapOf<String, String>()

private fun sealed(value: String): String {
    if (value.isEmpty()) return ""
    return sealedValues[value] ?: Secrets.protect(value).also {
        if (sealedValues.size > 2000) sealedValues.clear()
        sealedValues[value] = it
    }
}
