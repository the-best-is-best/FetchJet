package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.edition.AppEdition
import com.michelle.raouf.fetch_jet.core.edition.currentAppEdition
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getArray
import com.michelle.raouf.fetch_jet.core.util.getLong
import com.michelle.raouf.fetch_jet.core.util.getString
import com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

data class ReleaseAsset(val name: String, val url: String, val size: Long)

data class AppRelease(
    val version: String,
    val tag: String,
    val title: String,
    /** The release description ("what's new"), markdown as written on GitHub. */
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    val assets: List<ReleaseAsset>
)

data class UpdateState(
    /** false in the App Store edition: Apple does not allow pointing users to other installs. */
    val supported: Boolean = true,
    val currentVersion: String = APP_VERSION,
    val autoCheck: Boolean = true,
    /** Windows desktop only: download the installer by itself and offer "Restart & update". */
    val autoInstall: Boolean = false,
    val canInstallInApp: Boolean = false,
    val checking: Boolean = false,
    val lastCheckedMs: Long = 0L,
    /** A newer release than this build (null = up to date or not checked yet). */
    val available: AppRelease? = null,
    /** Show the "new version" dialog (set by a check, cleared by Later / Skip). */
    val showDialog: Boolean = false,
    /** Result line for the settings page: "You're up to date", an error... */
    val message: String? = null,
    /** 0..1 while the installer downloads, -1 otherwise. */
    val downloadProgress: Float = -1f,
    /** Installer downloaded and waiting for "Restart & update". */
    val installerPath: String? = null,
    val skippedVersion: String = ""
)

/**
 * Checks GitHub for a newer published release of FetchJet (drafts are invisible to the API, so
 * a release appears for users only after it is published).
 *
 *  - On start-up (if "check automatically" is on, at most every 12 hours) and from Settings.
 *  - A found update shows a dialog with the version and the release notes; "Skip this version"
 *    silences that version, "Later" until the next start.
 *  - Windows desktop: "Download & install" fetches the .msi and runs it over the installed app
 *    (same upgrade id, so it replaces the old version), then FetchJet starts again.
 *    With "install automatically" on, the download happens by itself and only the restart is asked.
 *  - Android, iOS, macOS, Linux: the dialog links to the right file / the release page.
 */
object UpdateManager {
    private const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

    private val pathResolver = PathResolver()
    private val settingsFile get() = "${pathResolver.getSettingsDirectory()}/updates.properties".toPath()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000L
                socketTimeoutMillis = 60_000L
            }
        }
    }
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var started = false

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        val supported = currentAppEdition() != AppEdition.STORE
        val saved = load()
        _state.value = UpdateState(
            supported = supported,
            autoCheck = saved["autoCheck"]?.toBooleanStrictOrNull() ?: true,
            autoInstall = saved["autoInstall"]?.toBooleanStrictOrNull() ?: false,
            canInstallInApp = PlatformUpdater.canInstallInApp,
            lastCheckedMs = saved["lastCheckedMs"]?.toLongOrNull() ?: 0L,
            skippedVersion = saved["skippedVersion"] ?: ""
        )
    }

    /** Call once when the UI starts. */
    fun onAppStart() {
        if (started) return
        started = true
        val s = _state.value
        if (!s.supported || !s.autoCheck) return
        checkJob = scope.launch {
            delay(4_000) // let the app settle before going to the network
            val due = nowEpochMs() - _state.value.lastCheckedMs >= AUTO_CHECK_INTERVAL_MS
            if (due) runCheck(manual = false)
        }
    }

    /** "Check now" in Settings: always asks GitHub and always reports the result. */
    fun checkNow() {
        if (!_state.value.supported || _state.value.checking) return
        checkJob = scope.launch { runCheck(manual = true) }
    }

    fun setAutoCheck(enabled: Boolean) {
        _state.update { it.copy(autoCheck = enabled) }
        save()
    }

    fun setAutoInstall(enabled: Boolean) {
        _state.update { it.copy(autoInstall = enabled) }
        save()
        val s = _state.value
        if (enabled && s.available != null && s.installerPath == null) startDownload(s.available)
    }

    fun showDialog() = _state.update { it.copy(showDialog = it.available != null) }

    /** "Later": hide until the next start (or the next manual check). */
    fun dismiss() = _state.update { it.copy(showDialog = false) }

    /** "Skip this version": never show this version again on its own. */
    fun skip() {
        val v = _state.value.available?.version ?: return
        _state.update { it.copy(showDialog = false, skippedVersion = v) }
        save()
    }

    /** Windows: download the installer (if needed) and install. */
    fun installNow() {
        val s = _state.value
        val path = s.installerPath
        if (path != null && FileSystem.SYSTEM.exists(path.toPath())) {
            PlatformUpdater.launchInstallerAndExit(path).onFailure { e ->
                _state.update { it.copy(message = "Could not start the installer: ${e.message}") }
            }
            return
        }
        s.available?.let { startDownload(it, installWhenDone = true) }
    }

    // ---------------------------------------------------------------------------------------

    private suspend fun runCheck(manual: Boolean) {
        _state.update {
            it.copy(
                checking = true,
                message = if (manual) "Checking for updates…" else it.message
            )
        }
        val result = runCatching { fetchLatestRelease() }
        val now = nowEpochMs()
        result.onSuccess { latest ->
            val newer = latest != null && isNewer(latest.version, APP_VERSION)
            val skipped = newer && !manual && latest!!.version == _state.value.skippedVersion
            _state.update {
                it.copy(
                    checking = false,
                    lastCheckedMs = now,
                    available = if (newer) latest else null,
                    showDialog = newer && !skipped,
                    message = when {
                        latest == null -> "No published release yet."
                        newer -> "Version ${latest.version} is available."
                        else -> "You're up to date ($APP_VERSION)."
                    }
                )
            }
            save()
            val s = _state.value
            if (newer && !skipped && s.canInstallInApp && s.autoInstall && s.installerPath == null) {
                startDownload(latest!!)
            }
        }.onFailure { e ->
            _state.update {
                it.copy(
                    checking = false,
                    message = if (manual) "Could not check for updates: ${e.message ?: "network error"}" else it.message
                )
            }
        }
    }

    private suspend fun fetchLatestRelease(): AppRelease? {
        val response =
            client.get("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest") {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "FetchJet/$APP_VERSION")
            }
        if (response.status.value == 404) return null // nothing published yet
        if (!response.status.isSuccess()) throw Exception("GitHub answered HTTP ${response.status.value}")
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(response.bodyAsText()) as? Map<String, Any?>
            ?: throw Exception("unreadable answer")
        val tag = json.getString("tag_name") ?: throw Exception("release has no tag")
        val assets = json.getArray("assets").orEmpty().mapNotNull { a ->
            @Suppress("UNCHECKED_CAST")
            val m = a as? Map<String, Any?> ?: return@mapNotNull null
            val name = m.getString("name") ?: return@mapNotNull null
            val url = m.getString("browser_download_url") ?: return@mapNotNull null
            ReleaseAsset(name, url, m.getLong("size") ?: 0L)
        }
        return AppRelease(
            version = tag.trim().removePrefix("v").removePrefix("V"),
            tag = tag,
            title = json.getString("name")?.ifBlank { null } ?: "FetchJet $tag",
            notes = json.getString("body").orEmpty().trim(),
            pageUrl = json.getString("html_url")
                ?: "https://github.com/$UPDATE_REPOSITORY/releases/latest",
            publishedAt = json.getString("published_at").orEmpty(),
            assets = assets
        )
    }

    private fun startDownload(release: AppRelease, installWhenDone: Boolean = false) {
        if (!PlatformUpdater.canInstallInApp) return
        if (downloadJob?.isActive == true) return
        val asset = PlatformUpdater.pickAsset(release.assets) ?: run {
            _state.update { it.copy(message = "Update download failed: this release has no installer for this system.") }
            return
        }
        downloadJob = scope.launch {
            _state.update { it.copy(downloadProgress = 0f, message = "Downloading ${asset.name}…") }
            val dir = "${pathResolver.getTempDirectory()}/fetchjet-update".toPath()
            val target = dir / asset.name
            val part = dir / "${asset.name}.part"
            val ok = runCatching {
                FileSystem.SYSTEM.createDirectories(dir)
                client.prepareGet(asset.url) {
                    header("User-Agent", "FetchJet/$APP_VERSION")
                }.execute { response ->
                    if (!response.status.isSuccess()) throw Exception("HTTP ${response.status.value}")
                    val total = response.contentLength() ?: asset.size
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    FileSystem.SYSTEM.sink(part).buffer().use { sink ->
                        while (isActive && !channel.isClosedForRead) {
                            val n = channel.readAvailable(buffer, 0, buffer.size)
                            if (n <= 0) break
                            sink.write(buffer, 0, n)
                            done += n
                            if (total > 0L) _state.update {
                                it.copy(
                                    downloadProgress = (done.toFloat() / total).coerceIn(
                                        0f,
                                        1f
                                    )
                                )
                            }
                        }
                    }
                    if (total > 0L && done < total) throw Exception("download interrupted")
                }
                if (FileSystem.SYSTEM.exists(target)) FileSystem.SYSTEM.delete(target)
                FileSystem.SYSTEM.atomicMove(part, target)
            }
            ok.onSuccess {
                _state.update {
                    it.copy(
                        downloadProgress = -1f,
                        installerPath = target.toString(),
                        showDialog = true,
                        message = "Version ${release.version} is ready to install."
                    )
                }
                if (installWhenDone) installNow()
            }.onFailure { e ->
                try {
                    FileSystem.SYSTEM.delete(part)
                } catch (_: Throwable) {
                }
                _state.update {
                    it.copy(
                        downloadProgress = -1f,
                        message = "Update download failed: ${e.message}"
                    )
                }
            }
        }
    }

    // ---- settings file (key=value) -----------------------------------------------------------

    private fun load(): Map<String, String> = try {
        if (!FileSystem.SYSTEM.exists(settingsFile)) emptyMap()
        else FileSystem.SYSTEM.read(settingsFile) { readUtf8() }.lineSequence()
            .map { it.trim() }
            .filter { it.contains('=') && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun save() {
        val s = _state.value
        try {
            settingsFile.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(settingsFile) {
                writeUtf8("# FetchJet update settings\n")
                writeUtf8("autoCheck=${s.autoCheck}\n")
                writeUtf8("autoInstall=${s.autoInstall}\n")
                writeUtf8("lastCheckedMs=${s.lastCheckedMs}\n")
                writeUtf8("skippedVersion=${s.skippedVersion}\n")
            }
        } catch (_: Throwable) {
        }
    }
}

/** true when [candidate] is a later version than [current] ("1.2.0" > "1.1.9", "1.0.0" > "1.0.0-beta"). */
fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String): Pair<List<Int>, String> {
        val clean = v.trim().removePrefix("v").removePrefix("V")
        val main = clean.substringBefore('-').substringBefore('+')
        val pre = if (clean.contains('-')) clean.substringAfter('-').substringBefore('+') else ""
        return main.split('.').map { it.toIntOrNull() ?: 0 } to pre
    }
    val (a, aPre) = parts(candidate)
    val (b, bPre) = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    // same numbers: a release beats its pre-release
    return when {
        aPre.isEmpty() && bPre.isNotEmpty() -> true
        aPre.isNotEmpty() && bPre.isEmpty() -> false
        else -> aPre > bPre
    }
}
