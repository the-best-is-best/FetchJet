package com.michelle.raouf.fetch_jet.feature.derived

import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask

data class RemovalPlan(
    val clipIds: List<String>,
    val copies: Map<String, List<String>>
) {
    val extraCount: Int get() = clipIds.size + copies.values.sumOf { it.size }
}

object DerivedItems {
    fun count(sourceId: String, tasks: List<DownloadTask>, copies: Map<String, List<String>>): Int =
        tasks.count { it.cutFromTaskId == sourceId } + copies[sourceId].orEmpty().size

    fun removalPlan(ids: Collection<String>, tasks: List<DownloadTask>, copies: Map<String, List<String>>): RemovalPlan {
        val chosen = ids.toSet()
        val all = LinkedHashSet<String>(chosen)
        var frontier: Set<String> = chosen
        while (frontier.isNotEmpty()) {
            val next = tasks.filter { it.cutFromTaskId.isNotEmpty() && it.cutFromTaskId in frontier && it.id !in all }.map { it.id }
            all.addAll(next)
            frontier = next.toSet()
        }
        val clips = all.filter { it !in chosen }
        val owned = all.mapNotNull { id -> copies[id]?.takeIf { it.isNotEmpty() }?.let { id to it } }.toMap()
        return RemovalPlan(clips, owned)
    }

    fun promotedClip(clip: DownloadTask, source: DownloadTask): DownloadTask {
        val offset = if (isPromotedClip(source)) source.cutStartMs else 0L
        return clip.copy(
            url = source.url,
            originalUrl = source.originalUrl,
            engineType = source.engineType,
            requestHeaders = source.requestHeaders,
            formatId = source.formatId,
            playlistId = source.playlistId,
            playlistTitle = source.playlistTitle,
            hidden = source.hidden,
            subtitlesFromPage = source.subtitlesFromPage,
            embedSubtitles = source.embedSubtitles,
            subtitleFormat = source.subtitleFormat,
            status = DownloadStatus.COMPLETED,
            cutFromTaskId = "",
            cutStartMs = clip.cutStartMs + offset,
            cutEndMs = clip.cutEndMs + offset
        )
    }

    fun compressedSource(source: DownloadTask, fileName: String, bytes: Long): DownloadTask = source.copy(
        fileName = fileName,
        totalBytes = bytes,
        downloadedBytes = bytes,
        progress = 1f,
        checksumVerified = null,
        expectedChecksum = null,
        scanResult = null
    )

    fun isPromotedClip(task: DownloadTask): Boolean =
        task.cutFromTaskId.isEmpty() && (task.cutStartMs > 0L || task.cutEndMs > 0L)
}
