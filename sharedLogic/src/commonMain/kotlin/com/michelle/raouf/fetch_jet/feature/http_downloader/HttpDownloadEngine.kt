package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.engine.DownloadEngine
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import kotlinx.coroutines.flow.StateFlow

/** What the queue needs from the plain-HTTP engine, whatever implements it. */
interface HttpDownloadEngine : DownloadEngine {
    fun enqueueTask(task: DownloadTask): DownloadTask
    fun getAllTasksFlow(): StateFlow<Map<String, DownloadTask>>
}

/**
 * Desktop and Android: the multi-connection Ktor downloader.
 * iOS: a background NSURLSession engine, so downloads keep going when the app is
 * suspended or killed and finish without the silent-audio trick.
 */
expect fun createHttpEngine(
    pathResolver: PathResolver,
    speedLimitProvider: (String) -> Long
): HttpDownloadEngine
