package com.michelle.raouf.fetch_jet.feature.http_downloader

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.feature.queue_manager.firstFreeFileName
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

/**
 * Settles the final file name of a download once it is known (named by the caller or by the
 * server). A name another download of the same folder already holds (it is running, or it
 * finished and its file is still there) is never taken twice: this one gets the first free
 * "name (n).ext". A task keeps its claim while it exists, so a resume keeps its own name. A
 * file that no download owns is still replaced, as before (the Replace choice of the Add dialog),
 * unless [keepFiles]: the name came from the server, so nobody chose to replace that file.
 */
internal suspend fun HttpChunkDownloader.claimFileName(taskId: String, wanted: String, keepFiles: Boolean = false): String =
    nameClaimLock.withLock {
        val me = tasksState.value[taskId] ?: return@withLock wanted
        if (taskId in nameClaims) return@withLock wanted
        val dir = me.destinationPath.trimEnd('/', '\\')
        val others = tasksState.value.values.filter {
            it.id != taskId && it.destinationPath.trimEnd('/', '\\') == dir
        }
        fun heldByOther(name: String): Boolean = others.any {
            it.fileName.equals(name, ignoreCase = true) &&
                    (it.id in nameClaims ||
                            (it.status == DownloadStatus.COMPLETED && AppFileSystem.exists("$dir/${it.fileName}".toPath())))
        }

        val taken = heldByOther(wanted) || (keepFiles && AppFileSystem.exists("$dir/$wanted".toPath()))
        val chosen = if (!taken) wanted else firstFreeFileName(wanted) { name ->
            heldByOther(name) || AppFileSystem.exists("$dir/$name".toPath())
        }
        nameClaims.add(taskId)
        if (chosen != me.fileName) updateTaskState(taskId) { it.copy(fileName = chosen) }
        chosen
    }
