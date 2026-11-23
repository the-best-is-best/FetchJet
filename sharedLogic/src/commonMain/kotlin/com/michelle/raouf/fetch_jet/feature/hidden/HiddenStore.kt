package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.security.CryptoBox
import com.michelle.raouf.fetch_jet.core.security.SecretBox
import com.michelle.raouf.fetch_jet.core.security.hexOf
import com.michelle.raouf.fetch_jet.core.security.Secrets
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okio.Path
import okio.Path.Companion.toPath

enum class HiddenLockType { PIN, PATTERN }

data class HiddenState(
    val ids: Set<String> = emptySet(),
    val playlists: Set<String> = emptySet(),
    val folders: Set<String> = emptySet(),
    val lockType: HiddenLockType? = null,
    val noticeAccepted: Boolean = false
) {
    val hasLock: Boolean get() = lockType != null

    fun hides(task: DownloadTask): Boolean =
        task.id in ids || (task.playlistId.isNotEmpty() && task.playlistId in playlists)
}

sealed interface HiddenCheck {
    data object Ok : HiddenCheck
    data class Wrong(val triesLeft: Int) : HiddenCheck
    data class Locked(val secondsLeft: Int) : HiddenCheck
    data object NoLock : HiddenCheck
    data object Invalid : HiddenCheck
    data object SaveFailed : HiddenCheck
}

enum class PinReset { OK, INVALID, EXPIRED, NO_LOCK, SAVE_FAILED }

enum class RecoveryRestore { DONE, WRONG_CODE, NOTHING, SAVE_FAILED }

@Serializable
private data class HiddenEnc(
    val key: String = "",
    val name: String = "",
    val stored: String = ""
)

class HiddenEncInfo(val key: ByteArray, val name: String, val stored: String)

@Serializable
private data class HiddenFile(
    val lockType: String = "",
    val salt: String = "",
    val hash: String = "",
    val ids: List<String> = emptyList(),
    val playlists: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val origins: Map<String, String> = emptyMap(),
    val fails: Int = 0,
    val lockedUntilMs: Long = 0L,
    val enc: Map<String, HiddenEnc> = emptyMap(),
    val noticeAccepted: Boolean = false,
    val rk: String = "",
    val rec: Map<String, String> = emptyMap()
)

object HiddenStore {
    const val PIN_MIN = 4
    const val PIN_MAX = 6
    const val PATTERN_MIN = 4
    const val PATTERN_MAX = 9
    private const val FREE_TRIES = 5
    private const val ROUNDS = 20_000
    const val PIN_RESET_WINDOW_MS = 120_000L

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    private val lock = newStoreLock()

    @kotlin.concurrent.Volatile
    internal var fileOverride: String? = null

    @kotlin.concurrent.Volatile
    internal var clockOverride: (() -> Long)? = null

    private val file get() = (fileOverride ?: "${PathResolver().getSettingsDirectory()}/hidden.json").toPath()
    private val backupFile get() = "$file.bak".toPath()

    private var data: HiddenFile = HiddenFile()
    private val _state = MutableStateFlow(HiddenState())
    val state: StateFlow<HiddenState> = _state.asStateFlow()

    private var loaded = false
    private var startupCleaned = false
    private val keyCache = HashMap<String, ByteArray>()

    @kotlin.concurrent.Volatile
    private var resetUntilMs = 0L

    @kotlin.concurrent.Volatile
    private var recoveryGap = false

    private fun now(): Long = clockOverride?.invoke() ?: kotlin.time.Clock.System.now().toEpochMilliseconds()

    internal fun resetForTest(path: String?, clock: (() -> Long)? = null) = lock.withLock {
        fileOverride = path
        clockOverride = clock
        data = HiddenFile()
        loaded = false
        keyCache.clear()
        resetUntilMs = 0L
        recoveryGap = false
        HiddenMedia.resetForTest()
        _state.value = HiddenState()
    }

    private fun readFile(path: Path): HiddenFile? = try {
        if (AppFileSystem.exists(path)) json.decodeFromString(HiddenFile.serializer(), AppFileSystem.read(path) { readUtf8() }) else null
    } catch (_: Throwable) {
        null
    }

    internal fun ensureLoaded() = lock.withLock {
        if (loaded) return@withLock
        loaded = true
        val main = file
        val read = readFile(main)
        data = when {
            read != null -> read
            !AppFileSystem.exists(main) -> HiddenFile()
            else -> {
                val bad = "$main.bad".toPath()
                if (!AppFileSystem.exists(bad)) runCatching { AppFileSystem.copy(main, bad) }
                val saved = readFile(backupFile)
                if (saved != null) {
                    data = saved
                    save()
                }
                saved ?: HiddenFile()
            }
        }
        publish()
        migrateRecovery()
        if (!startupCleaned && fileOverride == null) {
            startupCleaned = true
            HiddenMedia.cleanup()
        }
    }

    private fun publish() {
        _state.value = HiddenState(
            ids = data.ids.toSet(),
            playlists = data.playlists.toSet(),
            folders = data.folders.toSet(),
            lockType = if (data.hash.isNotEmpty()) lockTypeOf(data.lockType) else null,
            noticeAccepted = data.noticeAccepted
        )
    }

    private fun writeText(target: Path, text: String) {
        val tmp = "$target.tmp".toPath()
        AppFileSystem.write(tmp) { writeUtf8(text) }
        val swapped = runCatching { AppFileSystem.atomicMove(tmp, target) }.isSuccess
        if (!swapped) {
            AppFileSystem.write(target) { writeUtf8(text) }
            runCatching { AppFileSystem.delete(tmp) }
        }
    }

    private fun save(): Boolean {
        publish()
        return try {
            file.parent?.let { AppFileSystem.createDirectories(it) }
            val text = json.encodeToString(HiddenFile.serializer(), data)
            writeText(file, text)
            runCatching { writeText(backupFile, text) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun commit(next: HiddenFile): Boolean {
        val before = data
        data = next
        if (save()) return true
        data = before
        publish()
        return false
    }

    fun isValidPin(pin: String): Boolean = isValidSecret(HiddenLockType.PIN, pin)

    fun isValidSecret(type: HiddenLockType, secret: String): Boolean = when (type) {
        HiddenLockType.PIN -> secret.length in PIN_MIN..PIN_MAX && secret.all { it in '0'..'9' }
        HiddenLockType.PATTERN -> secret.length in PATTERN_MIN..PATTERN_MAX && secret.all { it in '1'..'9' } && secret.toSet().size == secret.length
    }

    private fun lockTypeOf(text: String): HiddenLockType? = when (text) {
        "", "pin" -> HiddenLockType.PIN
        "pattern" -> HiddenLockType.PATTERN
        else -> null
    }

    private fun lockTypeText(type: HiddenLockType): String = when (type) {
        HiddenLockType.PIN -> "pin"
        HiddenLockType.PATTERN -> "pattern"
    }

    private fun withSecret(d: HiddenFile, type: HiddenLockType, secret: String): HiddenFile {
        val salt = Secrets.randomHex(16)
        return d.copy(lockType = lockTypeText(type), salt = salt, hash = hashOf(salt, secret), fails = 0, lockedUntilMs = 0L)
    }

    private fun hashOf(salt: String, pin: String): String {
        var bytes = (salt + ":" + pin).encodeUtf8()
        repeat(ROUNDS) { bytes = (salt + bytes.hex()).encodeUtf8().sha256() }
        return bytes.hex()
    }

    fun hasLock(): Boolean = lock.withLock {
        ensureLoaded()
        _state.value.hasLock
    }

    fun setPin(secret: String, type: HiddenLockType = HiddenLockType.PIN): Boolean = lock.withLock {
        ensureLoaded()
        if (_state.value.hasLock || !isValidSecret(type, secret)) return@withLock false
        commit(withSecret(data, type, secret))
    }

    fun verify(secret: String): HiddenCheck = lock.withLock {
        ensureLoaded()
        val type = _state.value.lockType ?: return@withLock HiddenCheck.NoLock
        val t = now()
        if (data.lockedUntilMs > t) return@withLock HiddenCheck.Locked(((data.lockedUntilMs - t + 999) / 1000).toInt())
        if (isValidSecret(type, secret) && hashOf(data.salt, secret) == data.hash) {
            if (data.fails != 0 || data.lockedUntilMs != 0L) {
                data = data.copy(fails = 0, lockedUntilMs = 0L)
                save()
            }
            return@withLock HiddenCheck.Ok
        }
        val fails = data.fails + 1
        if (fails >= FREE_TRIES) {
            val step = (fails - FREE_TRIES).coerceAtMost(5)
            val wait = 30_000L shl step
            data = data.copy(fails = fails, lockedUntilMs = t + wait)
            save()
            return@withLock HiddenCheck.Locked((wait / 1000).toInt())
        }
        data = data.copy(fails = fails)
        save()
        HiddenCheck.Wrong(FREE_TRIES - fails)
    }

    fun changePin(old: String, new: String, newType: HiddenLockType = HiddenLockType.PIN): HiddenCheck = lock.withLock {
        if (!isValidSecret(newType, new)) return@withLock HiddenCheck.Invalid
        val check = verify(old)
        if (check != HiddenCheck.Ok) return@withLock check
        if (commit(withSecret(data, newType, new))) HiddenCheck.Ok else HiddenCheck.SaveFailed
    }

    fun removeLock(secret: String): HiddenCheck = lock.withLock {
        val check = verify(secret)
        if (check != HiddenCheck.Ok) return@withLock check
        if (commit(HiddenFile(enc = data.enc, noticeAccepted = data.noticeAccepted, rk = data.rk, rec = data.rec))) HiddenCheck.Ok else HiddenCheck.SaveFailed
    }

    fun hide(ids: Collection<String>): Boolean = lock.withLock {
        ensureLoaded()
        if (!_state.value.hasLock) return@withLock false
        if (ids.isEmpty()) return@withLock true
        commit(data.copy(ids = (data.ids + ids).distinct()))
    }

    fun hidePlaylist(playlistId: String): Boolean = lock.withLock {
        ensureLoaded()
        if (!_state.value.hasLock || playlistId.isEmpty()) return@withLock false
        commit(data.copy(playlists = (data.playlists + playlistId).distinct()))
    }

    fun hideFolder(folder: String): Boolean = lock.withLock {
        ensureLoaded()
        val key = folderKeyOf(folder)
        if (!_state.value.hasLock || key.isEmpty()) return@withLock false
        commit(data.copy(folders = (data.folders + key).distinct()))
    }

    fun setOrigin(taskId: String, directory: String): Boolean = lock.withLock {
        ensureLoaded()
        if (data.origins[taskId] == directory) return@withLock true
        commit(data.copy(origins = data.origins + (taskId to directory)))
    }

    fun originOf(taskId: String): String? = lock.withLock {
        ensureLoaded()
        data.origins[taskId]
    }

    private fun without(ids: Set<String>, playlists: Set<String>, dropKeys: Boolean): HiddenFile {
        val closed = data.origins.filterKeys { it in ids }.values.map { folderKeyOf(it) }.toSet()
        val origins = data.origins.filterKeys { it !in ids }
        val live = origins.values.map { folderKeyOf(it) }.toSet()
        return data.copy(
            ids = data.ids.filter { it !in ids },
            playlists = data.playlists.filter { it !in playlists },
            origins = origins,
            folders = data.folders.filter { it !in closed || it in live },
            enc = if (dropKeys) data.enc.filterKeys { it !in ids } else data.enc,
            rec = if (dropKeys) data.rec.filterKeys { it !in ids } else data.rec
        )
    }

    fun unhide(tasks: Collection<DownloadTask>) = lock.withLock {
        ensureLoaded()
        val ids = tasks.map { it.id }.toSet()
        val lists = tasks.map { it.playlistId }.filter { it.isNotEmpty() }.toSet()
        data = without(ids, lists, dropKeys = true)
        keyCache.keys.removeAll(ids)
        save()
        Unit
    }

    fun drop(ids: Collection<String>, keepKeys: Boolean = false) = lock.withLock {
        ensureLoaded()
        val gone = ids.toSet()
        if (data.ids.none { it in gone } && data.origins.keys.none { it in gone } && (keepKeys || data.enc.keys.none { it in gone })) return@withLock
        data = without(gone, emptySet(), dropKeys = !keepKeys)
        if (!keepKeys) keyCache.keys.removeAll(gone)
        save()
        Unit
    }

    fun hasEncrypted(taskId: String): Boolean = lock.withLock {
        ensureLoaded()
        taskId in data.enc
    }

    fun encryptedInfo(taskId: String): HiddenEncInfo? = lock.withLock {
        ensureLoaded()
        val entry = data.enc[taskId] ?: return@withLock null
        val name = Secrets.reveal(entry.name) ?: return@withLock null
        val key = keyCache[taskId] ?: run {
            val hex = Secrets.reveal(entry.key) ?: return@withLock null
            hexBytes(hex)?.also { keyCache[taskId] = it } ?: return@withLock null
        }
        HiddenEncInfo(key, name, entry.stored)
    }

    fun setEncrypted(taskId: String, key: ByteArray, name: String, stored: String): Boolean = lock.withLock {
        ensureLoaded()
        val hex = hexOf(key)
        val keyText = Secrets.protect(hex)
        val nameText = Secrets.protect(name)
        if (Secrets.reveal(keyText) != hex || Secrets.reveal(nameText) != name) return@withLock false
        val (withKey, rk) = withRecoveryKey(data)
        if (rk == null) recoveryGap = true
        val rec = if (rk != null) withKey.rec + (taskId to wrapRec(rk, taskId, hex, name)) else withKey.rec - taskId
        if (!commit(withKey.copy(enc = withKey.enc + (taskId to HiddenEnc(keyText, nameText, stored)), rec = rec))) return@withLock false
        keyCache[taskId] = key
        true
    }

    fun clearEncrypted(taskId: String) = lock.withLock {
        ensureLoaded()
        if (taskId !in data.enc) return@withLock
        data = data.copy(enc = data.enc - taskId, rec = data.rec - taskId)
        keyCache.remove(taskId)
        save()
        Unit
    }

    fun noticeAccepted(): Boolean = lock.withLock {
        ensureLoaded()
        data.noticeAccepted
    }

    fun acceptNotice() = lock.withLock {
        ensureLoaded()
        if (data.noticeAccepted) return@withLock
        val (withKey, _) = withRecoveryKey(data)
        data = withKey.copy(noticeAccepted = true)
        save()
        Unit
    }

    fun hasRecoveryKey(): Boolean = lock.withLock {
        ensureLoaded()
        data.rk.isNotEmpty()
    }

    fun takeRecoveryGap(): Boolean = lock.withLock {
        val gap = recoveryGap
        recoveryGap = false
        gap
    }

    fun allowPinReset(): Boolean = lock.withLock {
        ensureLoaded()
        if (!_state.value.hasLock) return@withLock false
        resetUntilMs = now() + PIN_RESET_WINDOW_MS
        true
    }

    fun pinResetAllowed(): Boolean = resetUntilMs > now()

    fun cancelPinReset() {
        resetUntilMs = 0L
    }

    fun resetPin(secret: String, type: HiddenLockType = HiddenLockType.PIN): PinReset = lock.withLock {
        ensureLoaded()
        if (!_state.value.hasLock) return@withLock PinReset.NO_LOCK
        if (!pinResetAllowed()) return@withLock PinReset.EXPIRED
        if (!isValidSecret(type, secret)) return@withLock PinReset.INVALID
        if (!commit(withSecret(data, type, secret))) return@withLock PinReset.SAVE_FAILED
        resetUntilMs = 0L
        PinReset.OK
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun wrapRec(rk: ByteArray, id: String, keyHex: String, name: String): String =
        Base64.encode(CryptoBox.seal(rk, "$id\n$keyHex\n$name".encodeToByteArray()))

    @OptIn(ExperimentalEncodingApi::class)
    private fun unwrapRec(rk: ByteArray, id: String, wrapped: String): Pair<String, String>? = try {
        val parts = CryptoBox.open(rk, Base64.decode(wrapped)).decodeToString().split('\n', limit = 3)
        if (parts.size == 3 && parts[0] == id && hexBytes(parts[1]) != null) parts[1] to parts[2] else null
    } catch (_: Throwable) {
        null
    }

    private fun withRecoveryKey(d: HiddenFile): Pair<HiddenFile, ByteArray?> {
        if (d.rk.isNotEmpty()) return d to Secrets.reveal(d.rk)?.let { hexBytes(it) }?.takeIf { it.size == RecoveryCode.KEY_BYTES }
        val rk = SecretBox.randomBytes(RecoveryCode.KEY_BYTES)
        val text = Secrets.protect(hexOf(rk))
        if (Secrets.reveal(text) != hexOf(rk)) return d to null
        return d.copy(rk = text) to rk
    }

    private fun fillRec(d: HiddenFile, rk: ByteArray): HiddenFile {
        var rec = d.rec.filterKeys { it in d.enc }
        for ((id, e) in d.enc) {
            if (id in rec) continue
            val keyHex = Secrets.reveal(e.key) ?: continue
            val name = Secrets.reveal(e.name) ?: continue
            rec = rec + (id to wrapRec(rk, id, keyHex, name))
        }
        return d.copy(rec = rec)
    }

    private fun migrateRecovery() {
        if (data.enc.isEmpty()) return
        val (withKey, rk) = withRecoveryKey(data)
        if (rk == null) return
        val next = fillRec(withKey, rk)
        if (next != data) commit(next)
    }

    fun recoveryCode(): String? = lock.withLock {
        ensureLoaded()
        val (withKey, rk) = withRecoveryKey(data)
        if (rk == null) return@withLock null
        val next = fillRec(withKey, rk)
        if (next != data && !commit(next)) return@withLock null
        RecoveryCode.encode(rk)
    }

    fun restoreWithCode(code: String): RecoveryRestore = lock.withLock {
        ensureLoaded()
        val rk = RecoveryCode.decode(code) ?: return@withLock RecoveryRestore.WRONG_CODE
        val candidates = data.rec.keys.filter { it in data.enc }
        if (candidates.isEmpty()) return@withLock RecoveryRestore.NOTHING
        var enc = data.enc
        var opened = 0
        for (id in candidates) {
            val (keyHex, name) = unwrapRec(rk, id, data.rec.getValue(id)) ?: continue
            enc = enc + (id to data.enc.getValue(id).copy(key = Secrets.protect(keyHex), name = Secrets.protect(name)))
            opened++
        }
        if (opened == 0) return@withLock RecoveryRestore.WRONG_CODE
        val rkText = Secrets.protect(hexOf(rk))
        if (Secrets.reveal(rkText) != hexOf(rk)) return@withLock RecoveryRestore.SAVE_FAILED
        val next = fillRec(data.copy(enc = enc, rk = rkText), rk)
        if (!commit(next)) return@withLock RecoveryRestore.SAVE_FAILED
        keyCache.clear()
        RecoveryRestore.DONE
    }

    private fun hexBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val v = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            out[i] = v.toByte()
        }
        return out
    }

    fun folderKeyOf(path: String): String = normalizedDir(path)
}
