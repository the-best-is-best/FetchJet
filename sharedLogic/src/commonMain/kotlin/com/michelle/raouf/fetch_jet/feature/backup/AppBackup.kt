package com.michelle.raouf.fetch_jet.feature.backup

import com.michelle.raouf.fetch_jet.core.filesystem.ImportedPaths
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.i18n.start.appLanguages
import com.michelle.raouf.fetch_jet.core.security.Secrets
import com.michelle.raouf.fetch_jet.core.settings.AppSettingsRepository
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistStore
import com.michelle.raouf.fetch_jet.feature.queue_manager.LearnedMediaHosts
import com.michelle.raouf.fetch_jet.feature.queue_manager.PersistedTask
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.feature.queue_manager.TaskCache
import com.michelle.raouf.fetch_jet.feature.queue_manager.importTasks
import com.michelle.raouf.fetch_jet.feature.queue_manager.safeImportId
import com.michelle.raouf.fetch_jet.feature.queue_manager.toPersisted
import com.michelle.raouf.fetch_jet.feature.queue_manager.toTask
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

enum class BackupFailure { BACKUP_HIDDEN_BLOCKED, BACKUP_FAILED }

sealed interface BackupExport {
    class Ready(val text: String, val fileName: String, val tasks: Int) : BackupExport
    data class Failed(val failure: BackupFailure) : BackupExport
}

data class BackupResult(
    val valid: Boolean = true,
    val tasksAdded: Int = 0,
    val tasksSkipped: Int = 0,
    val needRestart: Int = 0,
    val settingsApplied: Int = 0,
    val playlistsAdded: Int = 0,
    val wrongPlatform: Boolean = false,
    val errors: Int = 0
)

object AppBackup {
    const val FORMAT_VERSION = 1
    const val EXTENSION = "fjbackup"
    private const val FORMAT = "fetchjet-backup"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    fun fileName(epochMs: Long = currentEpochMs()): String = "FetchJet-backup-${dateOf(epochMs)}.$EXTENSION"

    internal fun dateOf(epochMs: Long): String {
        val z = epochMs.floorDiv(86_400_000L) + 719_468L
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    private val userInfo = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*://)[^/?#]*@")

    internal fun stripUserInfo(url: String): String = url.replace(userInfo, "$1")

    private val speedUnits = setOf("MB/s", "KB/s", "Mbps", "Kbps")

    private fun PersistedTask.unsealed(): PersistedTask = copy(
        url = stripUserInfo(Secrets.reveal(url).orEmpty()),
        originalUrl = stripUserInfo(Secrets.reveal(originalUrl).orEmpty()),
        pairedAudioUrl = stripUserInfo(Secrets.reveal(pairedAudioUrl).orEmpty()),
        hdrCookie = "",
        hdrReferer = "",
        webSeeds = webSeeds.split(' ').filter { it.isNotBlank() }.joinToString(" ") { stripUserInfo(it) },
        subtitles = subtitles.split('\u0002').filter { it.isNotEmpty() }.joinToString("\u0002") { entry ->
            val f = entry.split('\u0001').toMutableList()
            if (f.size > 5) f[5] = stripUserInfo(Secrets.reveal(f[5]).orEmpty())
            f.joinToString("\u0001")
        }
    )

    fun create(repo: QueueManagerRepository): BackupExport = try {
        val tasks = repo.allTasksFlow.value
        if (tasks.any { it.hidden }) {
            BackupExport.Failed(BackupFailure.BACKUP_HIDDEN_BLOCKED)
        } else {
            val now = currentEpochMs()
            val persisted = tasks.map { it.toPersisted().unsealed() }
            val settings = AppSettingsRepository(repo.pathResolver).loadSettings()
            val settingsMap = linkedMapOf(
                "destinationPath" to settings.destinationPath,
                "disableTorrentUpload" to settings.disableTorrentUpload.toString(),
                "isDarkMode" to settings.isDarkMode.toString(),
                "themeMode" to settings.themeMode,
                "globalDownloadSpeedLimitBytes" to settings.globalDownloadSpeedLimitBytes.toString(),
                "globalUploadSpeedLimitBytes" to settings.globalUploadSpeedLimitBytes.toString(),
                "speedDisplayUnit" to settings.speedDisplayUnit,
                "language" to settings.language,
                "speedPresetLowBytes" to settings.speedPresetLowBytes.toString(),
                "speedPresetLowUnit" to settings.speedPresetLowUnit,
                "speedPresetMediumBytes" to settings.speedPresetMediumBytes.toString(),
                "speedPresetMediumUnit" to settings.speedPresetMediumUnit,
                "speedPresetFastBytes" to settings.speedPresetFastBytes.toString(),
                "speedPresetFastUnit" to settings.speedPresetFastUnit,
                "speedPresetHighBytes" to settings.speedPresetHighBytes.toString(),
                "speedPresetHighUnit" to settings.speedPresetHighUnit,
                "maxConcurrentDownloads" to repo.maxConcurrentDownloads.value.toString()
            )
            PlaylistStore.load()
            val root = JsonObject(
                mapOf(
                    "format" to JsonPrimitive(FORMAT),
                    "version" to JsonPrimitive(FORMAT_VERSION),
                    "appVersion" to JsonPrimitive(APP_VERSION),
                    "platform" to JsonPrimitive(devicePlatformId()),
                    "createdAtMs" to JsonPrimitive(now),
                    "tasks" to json.parseToJsonElement(json.encodeToString(ListSerializer(PersistedTask.serializer()), persisted)),
                    "settings" to JsonObject(settingsMap.mapValues { JsonPrimitive(it.value) }),
                    "behavior" to JsonObject(BehaviorSettings.snapshot().mapValues { JsonPrimitive(it.value) }),
                    "playlists" to json.parseToJsonElement(PlaylistStore.encode(PlaylistStore.records.value.values.map { it.copy(url = stripUserInfo(it.url)) })),
                    "learnedHosts" to JsonArray(LearnedMediaHosts.all().sorted().map { JsonPrimitive(it) })
                )
            )
            BackupExport.Ready(json.encodeToString(JsonObject.serializer(), root), fileName(now), persisted.size)
        }
    } catch (_: Throwable) {
        BackupExport.Failed(BackupFailure.BACKUP_FAILED)
    }

    private fun JsonElement?.strings(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        (this as? JsonObject)?.forEach { (k, v) ->
            if (v is JsonPrimitive && v !is JsonNull) v.contentOrNull?.let { out[k] = it }
        }
        return out
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    fun restore(text: String, repo: QueueManagerRepository): BackupResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Throwable) {
            null
        }
        if (root == null || root["format"].text() != FORMAT || ((root["version"] as? JsonPrimitive)?.intOrNull ?: 0) < 1) {
            return BackupResult(valid = false, errors = 1)
        }
        val samePlatform = root["platform"].text()?.let { it == devicePlatformId() } ?: true
        val paths = ImportedPaths(repo.pathResolver, samePlatform, trustedRoots(repo))
        var errors = 0
        var added = 0
        var skipped = 0
        var needRestart = 0
        try {
            val array = root["tasks"] as? JsonArray ?: JsonArray(emptyList())
            val entries = TaskCache.decode(array.toString()) { skipped++ }
            val downloads = paths.downloads
            val tasks = ArrayList<DownloadTask>()
            for (entry in entries) try {
                val task = entry.copy(hdrCookie = "").toTask(downloads)
                tasks += if (task.status == DownloadStatus.COMPLETED) task else task.copy(
                    status = DownloadStatus.PAUSED,
                    downloadedBytes = 0L,
                    progress = 0f,
                    chunks = emptyList(),
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L
                )
            } catch (_: Throwable) {
                skipped++
            }
            val imported = repo.importTasks(tasks, paths)
            added = imported.added.size
            skipped += imported.skipped
            needRestart = imported.added.count { it.status != DownloadStatus.COMPLETED }
        } catch (_: Throwable) {
            errors++
        }
        var applied = 0
        try {
            applied += restoreSettings(root["settings"].strings(), repo, paths)
        } catch (_: Throwable) {
            errors++
        }
        try {
            val before = BehaviorSettings.snapshot()
            BehaviorSettings.restore(root["behavior"].strings(), paths)
            val after = BehaviorSettings.snapshot()
            applied += (before.keys + after.keys).count { before[it] != after[it] }
        } catch (_: Throwable) {
            errors++
        }
        var playlists = 0
        try {
            val list = root["playlists"] as? JsonArray
            if (list != null) {
                PlaylistStore.load()
                for (decoded in PlaylistStore.decode(list.toString())) {
                    if (!safeImportId.matches(decoded.id)) continue
                    val record = decoded.copy(folder = if (decoded.folder.isBlank()) "" else paths.directoryOrDownloads(decoded.folder))
                    if (PlaylistStore.get(record.id) == null) {
                        PlaylistStore.put(record)
                        playlists++
                    }
                }
            }
        } catch (_: Throwable) {
            errors++
        }
        try {
            (root["learnedHosts"] as? JsonArray)?.forEach { host ->
                val h = host.text().orEmpty().trim()
                if (h.isNotEmpty()) LearnedMediaHosts.learn("https://$h/")
            }
        } catch (_: Throwable) {
            errors++
        }
        return BackupResult(
            valid = true,
            tasksAdded = added,
            tasksSkipped = skipped,
            needRestart = needRestart,
            settingsApplied = applied,
            playlistsAdded = playlists,
            wrongPlatform = !samePlatform,
            errors = errors
        )
    }

    private fun trustedRoots(repo: QueueManagerRepository): List<String> = buildList {
        runCatching { AppSettingsRepository(repo.pathResolver).loadSettings().destinationPath }.getOrNull()?.let { add(it) }
        addAll(BehaviorSettings.categoryFolders.value.values)
        BehaviorSettings.siteFolders.value.forEach { add(it.folder) }
    }

    private fun restoreSettings(values: Map<String, String>, repo: QueueManagerRepository, paths: ImportedPaths): Int {
        if (values.isEmpty()) return 0
        val store = AppSettingsRepository(repo.pathResolver)
        val old = store.loadSettings()
        var applied = 0
        fun <T> counted(next: T, current: T): T {
            if (next != current) applied++
            return next
        }
        fun long(key: String, fallback: Long): Long =
            values[key]?.toLongOrNull()?.coerceAtLeast(0L)?.let { counted(it, fallback) } ?: fallback
        fun unit(key: String, fallback: String): String =
            values[key]?.takeIf { it in speedUnits }?.let { counted(it, fallback) } ?: fallback
        fun flag(key: String, fallback: Boolean): Boolean =
            values[key]?.toBooleanStrictOrNull()?.let { counted(it, fallback) } ?: fallback
        val language = values["language"]?.takeIf { code -> code == AppLanguage.SYSTEM || appLanguages.any { it.localeCode == code } }
        val next = old.copy(
            destinationPath = values["destinationPath"]?.let { paths.directory(it) }?.let { counted(it, old.destinationPath) }
                ?: old.destinationPath,
            disableTorrentUpload = flag("disableTorrentUpload", old.disableTorrentUpload),
            isDarkMode = flag("isDarkMode", old.isDarkMode),
            themeMode = values["themeMode"]?.takeIf { it in setOf("system", "light", "dark") }?.let { counted(it, old.themeMode) } ?: old.themeMode,
            globalDownloadSpeedLimitBytes = long("globalDownloadSpeedLimitBytes", old.globalDownloadSpeedLimitBytes),
            globalUploadSpeedLimitBytes = long("globalUploadSpeedLimitBytes", old.globalUploadSpeedLimitBytes),
            speedDisplayUnit = unit("speedDisplayUnit", old.speedDisplayUnit),
            language = language?.let { counted(it, old.language) } ?: old.language,
            speedPresetLowBytes = long("speedPresetLowBytes", old.speedPresetLowBytes),
            speedPresetLowUnit = unit("speedPresetLowUnit", old.speedPresetLowUnit),
            speedPresetMediumBytes = long("speedPresetMediumBytes", old.speedPresetMediumBytes),
            speedPresetMediumUnit = unit("speedPresetMediumUnit", old.speedPresetMediumUnit),
            speedPresetFastBytes = long("speedPresetFastBytes", old.speedPresetFastBytes),
            speedPresetFastUnit = unit("speedPresetFastUnit", old.speedPresetFastUnit),
            speedPresetHighBytes = long("speedPresetHighBytes", old.speedPresetHighBytes),
            speedPresetHighUnit = unit("speedPresetHighUnit", old.speedPresetHighUnit)
        )
        store.saveSettings(next)
        values["maxConcurrentDownloads"]?.toIntOrNull()?.let {
            val limit = it.coerceAtLeast(0)
            if (limit != repo.maxConcurrentDownloads.value) applied++
            repo.setMaxConcurrentDownloads(limit)
        }
        return applied
    }
}
