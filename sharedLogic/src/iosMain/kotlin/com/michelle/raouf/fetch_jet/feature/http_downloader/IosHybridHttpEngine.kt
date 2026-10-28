package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * iOS HTTP engine that keeps the speed-limit semantics identical to desktop and Android.
 *
 * iOS offers two ways to download and they cannot be combined:
 *  - a background NSURLSession: continues while the app is suspended or killed, but the
 *    system performs the transfer and exposes NO rate limit;
 *  - an in-process socket (the shared Ktor engine): full control, global limit shared
 *    across tasks exactly like other platforms, but only while the app is in the foreground.
 *
 * So the engine is chosen per task when it is started:
 *  - speed limit active (global or per task) -> foreground engine with the limit applied;
 *  - no limit -> background engine.
 * A task stays with the engine that started it (their partial-file formats differ), so a
 * limit change applies to downloads started afterwards.
 */
class IosHybridHttpEngine(
    private val pathResolver: PathResolver,
    private val speedLimitProvider: (String) -> Long
) : HttpDownloadEngine {

    override val engineType: EngineType = EngineType.HTTP_DIRECT

    private val foreground = HttpChunkDownloader(
        pathResolver = pathResolver,
        speedLimitProvider = speedLimitProvider
    )
    private val background = IosBackgroundHttpEngine(pathResolver)

    private enum class Owner { FOREGROUND, BACKGROUND }

    private val owners = mutableMapOf<String, Owner>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val merged: StateFlow<Map<String, DownloadTask>> =
        combine(foreground.getAllTasksFlow(), background.getAllTasksFlow()) { fg, bg -> bg + fg }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun observeTask(taskId: String): Flow<DownloadTask> =
        merged.map { it[taskId] }.filterNotNull()

    override fun getAllTasksFlow(): StateFlow<Map<String, DownloadTask>> = merged

    override fun enqueueTask(task: DownloadTask): DownloadTask {
        // Not started yet: park it with the engine the current limit implies.
        return engineFor(task.id, create = true).enqueueTask(task)
    }

    override suspend fun start(task: DownloadTask): Result<Unit> =
        engineFor(task.id, create = true).start(task)

    override suspend fun pause(taskId: String): Result<Unit> =
        engineFor(taskId, create = false).pause(taskId)

    override suspend fun resume(taskId: String): Result<Unit> =
        engineFor(taskId, create = false).resume(taskId)

    override suspend fun cancel(taskId: String): Result<Unit> {
        val r = engineFor(taskId, create = false).cancel(taskId)
        owners.remove(taskId)
        return r
    }

    private fun engineFor(taskId: String, create: Boolean): HttpDownloadEngine {
        val owner = owners[taskId] ?: run {
            // Unknown task: whichever engine already holds it (after a relaunch), else decide.
            val existing = when {
                foreground.getAllTasksFlow().value.containsKey(taskId) -> Owner.FOREGROUND
                background.getAllTasksFlow().value.containsKey(taskId) -> Owner.BACKGROUND
                else -> null
            }
            // After a relaunch neither engine holds the task yet: the partial data on disk says
            // which engine was downloading it (their partial formats are not interchangeable).
            val onDisk = when {
                FileSystem.SYSTEM.exists("${pathResolver.getSettingsDirectory()}/$taskId.resume".toPath()) -> Owner.BACKGROUND
                FileSystem.SYSTEM.exists("${pathResolver.getTempDirectory()}/task_$taskId".toPath()) -> Owner.FOREGROUND
                else -> null
            }
            val decided = existing ?: onDisk
                ?: if (create && speedLimitProvider(taskId) > 0L) Owner.FOREGROUND else Owner.BACKGROUND
            owners[taskId] = decided
            decided
        }
        return if (owner == Owner.FOREGROUND) foreground else background
    }
}
