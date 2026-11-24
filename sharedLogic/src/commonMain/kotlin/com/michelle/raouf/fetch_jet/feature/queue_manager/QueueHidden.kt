package com.michelle.raouf.fetch_jet.feature.queue_manager

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenFiles
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMedia
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenMove
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.michelle.raouf.fetch_jet.feature.hidden.sameDirectory
import com.michelle.raouf.fetch_jet.feature.media_extractor.subtitleSideFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import okio.Path
import okio.Path.Companion.toPath

data class HiddenMoveReport(val moved: Int = 0, val copiesLeft: Int = 0, val failed: Int = 0, val skipped: Int = 0) {
    operator fun plus(other: HiddenMoveReport) =
        HiddenMoveReport(moved + other.moved, copiesLeft + other.copiesLeft, failed + other.failed, skipped + other.skipped)

    internal fun with(result: HiddenMove): HiddenMoveReport = when (result) {
        HiddenMove.FAILED -> copy(failed = failed + 1)
        HiddenMove.COPIED_SOURCE_KEPT -> copy(copiesLeft = copiesLeft + 1)
        else -> copy(moved = moved + 1)
    }
}

internal class MovedFiles(val result: HiddenMove, val fileName: String)

private fun HiddenMove.leftBehind() = this == HiddenMove.COPIED_SOURCE_KEPT || this == HiddenMove.FAILED

internal fun QueueManagerRepository.shouldStartHidden(task: DownloadTask, destDir: String, asked: Boolean): Boolean {
    val state = HiddenStore.state.value
    if (!state.hasLock) return false
    return asked ||
            (task.playlistId.isNotEmpty() && task.playlistId in state.playlists) ||
            HiddenStore.folderKeyOf(destDir) in state.folders
}

internal fun QueueManagerRepository.startHidden(task: DownloadTask, originDir: String): DownloadTask {
    val dir = HiddenFiles.prepare(pathResolver, task.id)
    if (!HiddenStore.setOrigin(task.id, originDir)) return task
    if (!HiddenStore.hide(listOf(task.id))) {
        HiddenStore.drop(listOf(task.id), keepKeys = true)
        return task
    }
    return task.copy(destinationPath = dir)
}

internal fun QueueManagerRepository.forgetHidden(taskId: String) {
    if (HiddenStore.originOf(taskId) == null && taskId !in HiddenStore.state.value.ids) return
    HiddenStore.drop(listOf(taskId))
    HiddenFiles.deleteDirFor(pathResolver, taskId)
}

private fun QueueManagerRepository.setEngineDestination(task: DownloadTask, dir: String, fileName: String?) {
    when (task.engineType) {
        EngineType.HTTP_DIRECT -> httpEngine.setDestination(task.id, dir, fileName)
        EngineType.MEDIA_STREAM -> mediaEngine.setDestination(task.id, dir, fileName)
        EngineType.BITTORRENT -> Unit
    }
}

private fun QueueManagerRepository.shouldEncrypt(task: DownloadTask, toDir: String, plain: Path): Boolean =
    HiddenStore.noticeAccepted() &&
            HiddenMedia.encryptable(task) &&
            HiddenFiles.isPrivate(pathResolver, toDir) &&
            AppFileSystem.metadataOrNull(plain)?.let { it.isRegularFile } == true

internal fun QueueManagerRepository.moveTaskFiles(
    task: DownloadTask,
    fromDir: String,
    toDir: String,
    renameIfTaken: Boolean
): MovedFiles {
    val from = fromDir.trimEnd('/', '\\')
    val to = toDir.trimEnd('/', '\\')
    val name = task.fileName
    if (name.isBlank()) return MovedFiles(HiddenMove.NOTHING_TO_MOVE, name)
    var finalName = name
    val decrypting = HiddenStore.hasEncrypted(task.id) && HiddenFiles.isPrivate(pathResolver, from)
    if (!decrypting && task.engineType != EngineType.BITTORRENT && !AppFileSystem.exists("$from/$name".toPath())) return MovedFiles(HiddenMove.NOTHING_TO_MOVE, name)
    val encrypting = !decrypting && shouldEncrypt(task, to, "$from/$name".toPath())
    if (!encrypting && AppFileSystem.exists("$to/$name".toPath())) {
        if (!renameIfTaken) return MovedFiles(HiddenMove.FAILED, name)
        finalName = freeNameIn(to, name)
    }
    val main = when {
        decrypting -> HiddenMedia.unhideFile(pathResolver, task.id, "$to/$finalName".toPath())
        encrypting -> HiddenMedia.hideFile(pathResolver, task.id, "$from/$name".toPath(), name)
        else -> HiddenFiles.move("$from/$name".toPath(), "$to/$finalName".toPath())
    }
    if (main == HiddenMove.FAILED) return MovedFiles(HiddenMove.FAILED, name)
    var result = main
    if (task.engineType == EngineType.BITTORRENT) {
        magnetHexInfoHash(task.url)?.let { hash ->
            val part = HiddenFiles.move("$from/.$hash.parts".toPath(), "$to/.$hash.parts".toPath())
            if (part.leftBehind()) result = HiddenMove.COPIED_SOURCE_KEPT
        }
    } else if (main != HiddenMove.NOTHING_TO_MOVE && (task.subtitleTracks.isNotEmpty() || task.subtitlesFromPage)) {
        val base = name.substringBeforeLast('.', name)
        val newBase = finalName.substringBeforeLast('.', finalName)
        for (side in task.copy(destinationPath = from).subtitleSideFiles()) {
            val target = "$to/${newBase + side.name.removePrefix(base)}".toPath()
            if (AppFileSystem.exists(target)) continue
            val one = HiddenFiles.move(side, target)
            if (one.leftBehind()) result = HiddenMove.COPIED_SOURCE_KEPT
        }
    }
    return MovedFiles(result, finalName)
}

internal suspend fun QueueManagerRepository.relocateTask(task: DownloadTask, toDir: String): HiddenMove {
    val to = toDir.trimEnd('/', '\\')
    if (sameDirectory(task.destinationPath, to)) return HiddenMove.NOTHING_TO_MOVE
    try {
        AppFileSystem.createDirectories(to.toPath())
    } catch (_: Exception) {
    }
    var waiting = false
    pendingTasks.update { map ->
        val w = map[task.id]
        waiting = w != null
        if (w == null) map else map + (task.id to w.copy(destinationPath = toDir))
    }
    if (waiting) return HiddenMove.NOTHING_TO_MOVE
    if (task.engineType == EngineType.BITTORRENT) {
        val extra = magnetHexInfoHash(task.url)?.let { listOf(".$it.parts") }.orEmpty()
        return torrentEngine.relocate(task.id, toDir, extra)
    }
    if (task.status == DownloadStatus.COMPLETED) {
        val moved = moveTaskFiles(task, task.destinationPath, toDir, true)
        if (moved.result == HiddenMove.FAILED) return HiddenMove.FAILED
        setEngineDestination(task, toDir, moved.fileName.takeIf { it != task.fileName })
        return moved.result
    }
    finishWatch.update { it + (task.id to task.destinationPath) }
    setEngineDestination(task, toDir, null)
    return HiddenMove.NOTHING_TO_MOVE
}

data class HiddenWorkState(val done: Int = 0, val total: Int = 0, val fraction: Float = 0f) {
    val active: Boolean get() = total > 0
}

object HiddenWork {
    private val _state = MutableStateFlow(HiddenWorkState())
    val state: StateFlow<HiddenWorkState> = _state

    internal val leaving = MutableStateFlow<Set<String>>(emptySet())

    internal val owned = MutableStateFlow<Set<String>>(emptySet())

    internal fun begin(count: Int) {
        if (count > 0) _state.update { it.copy(total = it.total + count) }
    }

    internal fun finish(remaining: Int) {
        if (remaining <= 0) return
        _state.update {
            val total = it.total - remaining
            if (total <= it.done) HiddenWorkState() else it.copy(total = total)
        }
    }

    internal fun step() {
        _state.update { if (it.done + 1 >= it.total) HiddenWorkState() else it.copy(done = it.done + 1, fraction = 0f) }
    }

    internal fun progress(fraction: Float) {
        _state.update { if (it.total > 0) it.copy(fraction = fraction.coerceIn(0f, 1f)) else it }
    }
}

private suspend fun QueueManagerRepository.hideOne(task: DownloadTask): HiddenMove {
    val dir = HiddenFiles.prepare(pathResolver, task.id)
    return relocateTask(task, dir)
}

suspend fun QueueManagerRepository.hideTasks(ids: Collection<String>): HiddenMoveReport {
    if (!HiddenStore.hasLock()) return HiddenMoveReport()
    val wanted = ids.toSet()
    val chosen = allTasksFlow.value.filter {
        it.id in wanted && !(it.hidden && HiddenFiles.isPrivate(pathResolver, it.destinationPath))
    }
    val targets = chosen.filter { !HiddenMedia.mediaOnly || HiddenMedia.mayBeMedia(it) }
    val skipped = chosen.size - targets.size
    if (targets.isEmpty()) return HiddenMoveReport(skipped = skipped)
    val targetIds = targets.map { it.id }
    var remaining = 0
    HiddenWork.owned.update { it + targetIds }
    try {
        val newOrigins = targets.filter { HiddenStore.originOf(it.id) == null }
        val stored = newOrigins.all { HiddenStore.setOrigin(it.id, it.destinationPath) } && HiddenStore.hide(targetIds)
        if (!stored) {
            HiddenStore.drop(newOrigins.map { it.id }, keepKeys = true)
            return HiddenMoveReport(failed = targets.size, skipped = skipped)
        }
        HiddenWork.begin(targets.size)
        remaining = targets.size
        return hideLock.withLock {
            var report = HiddenMoveReport(skipped = skipped)
            for (task in targets) {
                val current = allTasksFlow.value.find { it.id == task.id }
                if (current == null) HiddenStore.drop(listOf(task.id))
                var result = when {
                    current == null || task.id !in HiddenStore.state.value.ids -> HiddenMove.NOTHING_TO_MOVE
                    HiddenFiles.isPrivate(pathResolver, current.destinationPath) -> HiddenMove.MOVED
                    else -> try {
                        hideOne(current)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        HiddenMove.FAILED
                    }
                }
                if (result == HiddenMove.FAILED &&
                    allTasksFlow.value.find { it.id == task.id }?.let { HiddenFiles.isPrivate(pathResolver, it.destinationPath) } == true
                ) result = HiddenMove.MOVED
                if (result == HiddenMove.FAILED) HiddenStore.unhide(listOf(task.copy(playlistId = "")))
                report = report.with(result)
                HiddenWork.owned.update { it - task.id }
                remaining--
                HiddenWork.step()
            }
            report
        }
    } finally {
        HiddenWork.owned.update { it - targetIds.toSet() }
        HiddenWork.finish(remaining)
    }
}

suspend fun QueueManagerRepository.hidePlaylistTasks(playlistId: String): HiddenMoveReport {
    val report = hideTasks(allTasksFlow.value.filter { it.playlistId == playlistId }.map { it.id })
    if (report.failed == 0 && report.skipped == 0) HiddenStore.hidePlaylist(playlistId)
    return report
}

suspend fun QueueManagerRepository.unhideTasks(ids: Collection<String>): HiddenMoveReport = hideLock.withLock {
    val wanted = ids.toSet()
    var report = HiddenMoveReport()
    val shown = ArrayList<DownloadTask>()
    val gone = ArrayList<DownloadTask>()
    val leaving = allTasksFlow.value.filter { it.id in wanted && (it.hidden || HiddenStore.state.value.hides(it)) }
    val leavingIds = leaving.map { it.id }.toSet()
    var remaining = 0
    HiddenWork.owned.update { it + leavingIds }
    HiddenWork.leaving.update { it + leavingIds }
    try {
        HiddenWork.begin(leaving.size)
        remaining = leaving.size
        for (old in leaving) {
            val current = allTasksFlow.value.find { it.id == old.id }
            if (current == null) {
                gone.add(old)
                remaining--
                HiddenWork.step()
                continue
            }
            val task = current.copy(destinationPath = old.destinationPath)
            val result = if (HiddenFiles.isPrivate(pathResolver, task.destinationPath)) {
                try {
                    relocateTask(task, HiddenStore.originOf(task.id) ?: pathResolver.getDownloadsDirectory())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    HiddenMove.FAILED
                }
            } else HiddenMove.NOTHING_TO_MOVE
            report = report.with(result)
            if (result != HiddenMove.FAILED) shown.add(task)
            remaining--
            HiddenWork.step()
        }
    } finally {
        HiddenWork.finish(remaining)
        HiddenStore.unhide(shown + gone)
        HiddenWork.leaving.update { it - leavingIds }
        HiddenWork.owned.update { it - leavingIds }
    }
    shown.forEach { HiddenFiles.deleteDirFor(pathResolver, it.id) }
    report
}

suspend fun QueueManagerRepository.unhideEverything(): HiddenMoveReport =
    unhideTasks(allTasksFlow.value.filter { it.hidden }.map { it.id })

internal fun QueueManagerRepository.finishHiddenRemoval(task: DownloadTask, deleteFile: Boolean) {
    finishWatch.update { it - task.id }
    var keepDir = false
    val privateDir = HiddenFiles.dirFor(pathResolver, task.id)
    val inPrivate = HiddenFiles.isPrivate(pathResolver, task.destinationPath) || AppFileSystem.exists(privateDir.toPath())
    if (!deleteFile && inPrivate) {
        val origin = HiddenStore.originOf(task.id) ?: pathResolver.getDownloadsDirectory()
        try {
            AppFileSystem.createDirectories(origin.toPath())
        } catch (_: Exception) {
        }
        keepDir = moveTaskFiles(task, privateDir, origin, true).result == HiddenMove.FAILED
    }
    HiddenStore.drop(listOf(task.id), keepKeys = keepDir)
    if (!keepDir) HiddenFiles.deleteDirFor(pathResolver, task.id)
}

private suspend fun QueueManagerRepository.reconcileFinished(task: DownloadTask, wrongDir: String) {
    val name = task.fileName
    val dest = task.destinationPath.trimEnd('/', '\\')
    if (name.isBlank() || dest.isBlank()) return
    if (AppFileSystem.exists("$dest/$name".toPath())) return
    if (!AppFileSystem.exists("${wrongDir.trimEnd('/', '\\')}/$name".toPath())) return
    val moved = moveTaskFiles(task, wrongDir, dest, true)
    if (moved.result != HiddenMove.FAILED && moved.fileName != name) setEngineDestination(task, dest, moved.fileName)
}

private fun QueueManagerRepository.pendingEncryption(tasks: List<DownloadTask>): List<DownloadTask> {
    if (!HiddenStore.state.value.noticeAccepted) return emptyList()
    return tasks.filter {
        it.hidden && it.status == DownloadStatus.COMPLETED && it.id !in HiddenMedia.triedIds.value &&
                HiddenMedia.encryptable(it) && HiddenFiles.isPrivate(pathResolver, it.destinationPath)
    }
}

private fun QueueManagerRepository.encryptFinished(tasks: List<DownloadTask>) {
    for (old in tasks) {
        val task = allTasksFlow.value.find { it.id == old.id && it.hidden && it.status == DownloadStatus.COMPLETED } ?: continue
        HiddenMedia.triedIds.update { it + task.id }
        val plain = "${task.destinationPath.trimEnd('/', '\\')}/${task.fileName}".toPath()
        if (AppFileSystem.metadataOrNull(plain)?.isRegularFile != true) continue
        HiddenMedia.hideFile(pathResolver, task.id, plain, task.fileName)
    }
}

suspend fun QueueManagerRepository.encryptHiddenPending() = hideLock.withLock {
    encryptFinished(pendingEncryption(allTasksFlow.value))
}

internal fun QueueManagerRepository.observeHiddenTasks() {
    val tried = HashSet<String>()
    scope.launch {
        allTasksFlow.collect { tasks ->
            val watch = finishWatch.value
            val stray = tasks.filter {
                it.hidden && it.id !in tried && it.id !in HiddenWork.owned.value && HiddenStore.state.value.hides(it) && !HiddenFiles.isPrivate(pathResolver, it.destinationPath)
            }
            val finished = if (watch.isEmpty()) emptyList() else
                tasks.filter { it.status == DownloadStatus.COMPLETED && it.id in watch }
            for (t in tasks) if (t.hidden && t.status != DownloadStatus.COMPLETED && t.id in HiddenMedia.triedIds.value) HiddenMedia.triedIds.update { it - t.id }
            val encrypt = pendingEncryption(tasks)
            if (stray.isEmpty() && finished.isEmpty() && encrypt.isEmpty()) return@collect
            try {
                hideLock.withLock {
                    for (old in stray) {
                        tried.add(old.id)
                        val task = allTasksFlow.value.find { it.id == old.id && it.hidden && HiddenStore.state.value.hides(it) } ?: continue
                        if (HiddenFiles.isPrivate(pathResolver, task.destinationPath)) continue
                        if (HiddenStore.originOf(task.id) == null) HiddenStore.setOrigin(task.id, task.destinationPath)
                        try {
                            hideOne(task)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                    for (old in finished) {
                        val wrong = finishWatch.value[old.id] ?: continue
                        finishWatch.update { it - old.id }
                        val task = allTasksFlow.value.find { it.id == old.id } ?: continue
                        reconcileFinished(task, wrong)
                    }
                    if (encrypt.isNotEmpty()) encryptFinished(pendingEncryption(allTasksFlow.value))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
