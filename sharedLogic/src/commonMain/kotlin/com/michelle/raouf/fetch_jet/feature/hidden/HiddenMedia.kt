package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.security.SecretBox
import com.michelle.raouf.fetch_jet.core.security.Secrets
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.queue_manager.HiddenWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath

data class HiddenOpening(val taskId: String, val fraction: Float)

object HiddenMedia {
    const val PLAY_FOLDER = "hidden_play"

    private val extensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "mts", "mpg", "mpeg", "wmv", "flv", "3gp", "3g2", "ogv",
        "mp3", "m4a", "flac", "wav", "aac", "ogg", "oga", "opus", "wma", "weba", "mka", "aiff", "aif", "amr", "ac3", "m4b"
    )

    @kotlin.concurrent.Volatile
    internal var tempOverride: String? = null

    @kotlin.concurrent.Volatile
    var mediaOnly: Boolean = true

    internal val triedIds = MutableStateFlow<Set<String>>(emptySet())

    fun isMedia(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in extensions

    fun encryptable(task: DownloadTask): Boolean = task.engineType != EngineType.BITTORRENT && isMedia(task.fileName)

    fun mayBeMedia(task: DownloadTask): Boolean =
        task.engineType != EngineType.BITTORRENT &&
                (task.fileName.isBlank() || task.engineType == EngineType.MEDIA_STREAM || isMedia(task.fileName))

    fun noticeRequired(tasks: List<DownloadTask>, ids: Collection<String>, accepted: Boolean): Boolean {
        if (accepted) return false
        val wanted = ids.toSet()
        return tasks.any { it.id in wanted && !it.hidden && mayBeMedia(it) }
    }

    internal fun resetForTest() {
        triedIds.value = emptySet()
        mediaOnly = true
    }

    private fun percentSteps(onFraction: (Float) -> Unit): (Long, Long) -> Unit {
        var lastStep = -1
        return { done, total ->
            val step = if (total <= 0L) 100 else (done * 100 / total).toInt()
            if (step != lastStep) {
                lastStep = step
                onFraction(step / 100f)
            }
        }
    }

    private fun tempRoot(pathResolver: PathResolver): String =
        (tempOverride ?: pathResolver.getTempDirectory()).replace('\\', '/').trimEnd('/') + "/" + PLAY_FOLDER

    internal fun hideFile(pathResolver: PathResolver, taskId: String, plain: Path, name: String): HiddenMove {
        val dir = HiddenFiles.dirFor(pathResolver, taskId).toPath()
        val key = SecretBox.randomBytes(32)
        val stored = Secrets.randomHex(16) + ".fjh"
        val target = dir / stored
        val tmp = dir / (Secrets.randomHex(8) + ".tmp")
        val old = HiddenStore.encryptedInfo(taskId)
        var entrySet = false
        try {
            AppFileSystem.createDirectories(dir)
            val total = AppFileSystem.metadataOrNull(plain)?.size ?: 0L
            val steps = percentSteps(HiddenWork::progress)
            val size = HiddenCrypt.encrypt(plain, tmp, key) { done -> steps(done, total) }
            if (!HiddenCrypt.verifyTail(tmp, key, size)) throw HiddenCryptException("the encrypted copy did not verify")
            if (!HiddenStore.setEncrypted(taskId, key, name, stored)) throw HiddenCryptException("the key could not be saved")
            entrySet = true
            AppFileSystem.atomicMove(tmp, target)
        } catch (t: Throwable) {
            AppLog.e("Hidden: encrypting failed (${t::class.simpleName}: ${t.message})")
            runCatching { if (AppFileSystem.exists(tmp)) AppFileSystem.delete(tmp) }
            if (entrySet) {
                if (old != null) HiddenStore.setEncrypted(taskId, old.key, old.name, old.stored) else HiddenStore.clearEncrypted(taskId)
            }
            return HiddenMove.FAILED
        }
        if (old != null && old.stored != stored) runCatching { AppFileSystem.delete(dir / old.stored) }
        val removed = runCatching { AppFileSystem.delete(plain) }.isSuccess && !AppFileSystem.exists(plain)
        return if (removed) HiddenMove.MOVED else HiddenMove.COPIED_SOURCE_KEPT
    }

    internal fun unhideFile(pathResolver: PathResolver, taskId: String, target: Path): HiddenMove {
        val info = HiddenStore.encryptedInfo(taskId)
        if (info == null) {
            AppLog.e("Hidden: the key of a file cannot be opened on this device")
            return HiddenMove.FAILED
        }
        val src = HiddenFiles.dirFor(pathResolver, taskId).toPath() / info.stored
        if (!AppFileSystem.exists(src)) return HiddenMove.FAILED
        val parent = target.parent ?: return HiddenMove.FAILED
        val part = parent / (Secrets.randomHex(6) + ".fjpart")
        try {
            HiddenCrypt.decrypt(src, part, info.key, percentSteps(HiddenWork::progress))
            AppFileSystem.atomicMove(part, target)
        } catch (t: Throwable) {
            AppLog.e("Hidden: decrypting failed (${t::class.simpleName}: ${t.message})")
            runCatching { if (AppFileSystem.exists(part)) AppFileSystem.delete(part) }
            return HiddenMove.FAILED
        }
        HiddenStore.clearEncrypted(taskId)
        runCatching { AppFileSystem.delete(src) }
        return HiddenMove.MOVED
    }

    suspend fun openPlayable(
        taskId: String,
        fileName: String = "",
        onProgress: (Float) -> Unit = {},
        pathResolver: PathResolver = PathResolver()
    ): String? = withContext(Dispatchers.Default) {
        val dir = HiddenFiles.dirFor(pathResolver, taskId).toPath()
        if (!HiddenStore.hasEncrypted(taskId)) {
            val plain = dir / fileName
            return@withContext if (fileName.isNotBlank() && AppFileSystem.exists(plain)) plain.toString() else null
        }
        val info = HiddenStore.encryptedInfo(taskId) ?: return@withContext null
        val src = dir / info.stored
        if (!AppFileSystem.exists(src)) return@withContext null
        val folder = tempRoot(pathResolver).toPath() / Secrets.randomHex(8)
        val safeName = pathResolver.sanitizeFileName(info.name).ifBlank { "video" }
        val out = folder / safeName
        try {
            AppFileSystem.createDirectories(folder)
            HiddenCrypt.decrypt(src, out, info.key, percentSteps(onProgress))
            out.toString()
        } catch (t: Throwable) {
            AppLog.e("Hidden: opening failed (${t::class.simpleName}: ${t.message})")
            runCatching { AppFileSystem.deleteRecursively(folder) }
            null
        }
    }

    fun cleanup(pathResolver: PathResolver = PathResolver()) {
        runCatching {
            val root = tempRoot(pathResolver).toPath()
            if (AppFileSystem.exists(root)) AppFileSystem.deleteRecursively(root)
        }
    }
}
