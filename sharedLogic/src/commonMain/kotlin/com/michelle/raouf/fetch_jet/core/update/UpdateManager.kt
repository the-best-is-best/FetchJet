package com.michelle.raouf.fetch_jet.core.update

import com.michelle.raouf.fetch_jet.core.edition.AppEdition
import com.michelle.raouf.fetch_jet.core.edition.currentAppEdition
import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.network.NetworkClient
import com.michelle.raouf.fetch_jet.core.network.useNetworkSettings
import com.michelle.raouf.fetch_jet.core.util.MiniJson
import com.michelle.raouf.fetch_jet.core.util.getArray
import com.michelle.raouf.fetch_jet.core.util.getLong
import com.michelle.raouf.fetch_jet.core.util.getString
import com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs
import com.tbib.kmp_localization.LocalizationManager
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
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
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
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/** [sha256]: GitHub's `digest` of the file (lower-case hex), "" when unknown (web-page fallback). */
data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String = "")

/** Only files of FetchJet's own releases are downloaded and installed. */
internal const val RELEASE_DOWNLOAD_PREFIX =
    "https://github.com/$UPDATE_REPOSITORY/releases/download/"

data class AppRelease(
    val version: String,
    val tag: String,
    val title: String,
    /** The release description ("what's new"), markdown as written on GitHub. */
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    val assets: List<ReleaseAsset>
) {
    /**
     * Platforms this release did not rebuild (their files are copies of the previous
     * release). Read from a `<!-- fetchjet-unchanged: windows,linux -->` line in the notes.
     */
    val unchangedPlatforms: Set<String>
        get() = Regex(
            """<!--\s*fetchjet-unchanged:\s*([a-z0-9,\s_-]*)-->""",
            RegexOption.IGNORE_CASE
        )
            .find(notes)?.groupValues?.get(1)
            ?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    /** The notes without the machine-readable marker line. */
    val notesForDisplay: String
        get() = notes.replace(
            Regex(
                """<!--\s*fetchjet-unchanged:[^>]*-->\s*""",
                RegexOption.IGNORE_CASE
            ), ""
        ).trim()
}

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
    val messageIsInstallError: Boolean = false,
    val messageIsError: Boolean = false,
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
    private val clients = NetworkClient {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000L
                socketTimeoutMillis = 60_000L
            }
            useNetworkSettings()
        }
    }
    private val client get() = clients.current
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
        scope.launch {
            AppLanguage.current.drop(1).collect { refreshNotesForLanguage() }
        }
        val s = _state.value
        if (!s.supported || !s.autoCheck) return
        checkJob = scope.launch {
            delay(4_000) // let the app settle before going to the network
            val due = nowEpochMs() - _state.value.lastCheckedMs >= AUTO_CHECK_INTERVAL_MS
            if (due) runCheck(manual = false)
        }
    }

    private suspend fun refreshNotesForLanguage() {
        val release = _state.value.available ?: return
        val fresh = runCatching { withNotesInAppLanguage(release) }.getOrNull() ?: return
        _state.update { it.copy(available = fresh) }
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
        if (path != null && AppFileSystem.exists(path.toPath())) {
            PlatformUpdater.launchInstallerAndExit(path).onFailure { e ->
                _state.update {
                    it.copy(
                        message = LocalizationManager.Strings.sysCouldNotStartInstaller(
                            e.message.toString()
                        ), messageIsInstallError = true
                    )
                }
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
                message = if (manual) LocalizationManager.Strings.sysCheckingForUpdates else it.message,
                messageIsInstallError = if (manual) false else it.messageIsInstallError,
                messageIsError = if (manual) false else it.messageIsError
            )
        }
        val result = runCatching {
            val latest = fetchLatestRelease()
            val forThisPlatform =
                if (latest != null && !PlatformUpdater.updatesFromStore &&
                    latest.unchangedPlatforms.contains(PlatformUpdater.platformKey)
                ) {
                    runCatching { fetchNewestBuiltForThisPlatform() }.getOrNull()
                        ?.takeIf { isNewer(it.version, APP_VERSION) } ?: latest
                } else latest
            forThisPlatform?.let { withNotesInAppLanguage(it) }
        }
        val now = nowEpochMs()
        result.onSuccess { latest ->
            val isNewerVersion = latest != null && isNewer(latest.version, APP_VERSION)
            // A release that only fixed other platforms ships this platform's previous file
            // unchanged: nothing to install here, say so instead of offering the same build.
            val unchangedHere =
                isNewerVersion && latest!!.unchangedPlatforms.contains(PlatformUpdater.platformKey)
            val newer = isNewerVersion && !unchangedHere
            val skipped = newer && !manual && latest!!.version == _state.value.skippedVersion
            _state.update {
                it.copy(
                    checking = false,
                    lastCheckedMs = now,
                    available = if (newer) latest else null,
                    showDialog = newer && !skipped,
                    message = when {
                        latest == null -> LocalizationManager.Strings.sysNoPublishedRelease
                        unchangedHere -> LocalizationManager.Strings.sysVersionNoChangesForDevice(
                            latest!!.version,
                            APP_VERSION
                        )

                        newer -> LocalizationManager.Strings.sysVersionAvailable(latest.version)
                        else -> LocalizationManager.Strings.sysUpToDate(APP_VERSION)
                    },
                    messageIsInstallError = false,
                    messageIsError = false
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
                    message = if (manual) LocalizationManager.Strings.sysCouldNotCheckForUpdates(
                        e.message ?: LocalizationManager.Strings.sysNetworkError
                    ) else it.message,
                    messageIsInstallError = if (manual) false else it.messageIsInstallError,
                    messageIsError = if (manual) true else it.messageIsError
                )
            }
        }
    }

    /**
     * Swaps the notes for `release-notes-<lang>.md` when the release has one for the app's
     * language. The `<!-- fetchjet-... -->` markers of the original notes are kept.
     */
    private suspend fun withNotesInAppLanguage(release: AppRelease): AppRelease {
        val lang = com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage.current.value
            .substringBefore('-').substringBefore('_').lowercase()
        if (lang.isBlank() || lang == "en") return release
        val file = release.assets.firstOrNull { it.name == releaseNotesFileFor(lang) } ?: return release
        val text = runCatching {
            client.get(file.url) { header("User-Agent", "FetchJet/$APP_VERSION") }
                .takeIf { it.status.isSuccess() }?.bodyAsText()?.trim()
        }.getOrNull()
        if (text.isNullOrBlank()) return release
        val markers = release.notes.lines().filter { it.trim().startsWith("<!-- fetchjet-") }
            .filter { m -> !text.contains(m.trim()) }
        return release.copy(notes = (markers + text).joinToString("\n"))
    }

    private suspend fun fetchLatestRelease(): AppRelease? {
        if (PlatformUpdater.updatesFromStore) {
            val version = PlatformUpdater.latestStoreVersion() ?: APP_VERSION
            return AppRelease(
                version = version,
                tag = "v$version",
                title = "FetchJet $version",
                notes = "",
                pageUrl = MICROSOFT_STORE_PAGE,
                publishedAt = "",
                assets = emptyList()
            )
        }
        val response =
            client.get("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest") {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "FetchJet/$APP_VERSION")
            }
        if (response.status.value == 404) return null // nothing published yet
        if (response.status.value == 403 || response.status.value == 429) return fetchLatestFromWebPage()
        if (!response.status.isSuccess()) throw Exception("GitHub answered HTTP ${response.status.value}")
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(response.bodyAsText()) as? Map<String, Any?>
            ?: throw Exception("unreadable answer")
        return releaseFromJson(json) ?: throw Exception("release has no tag")
    }

    private suspend fun fetchNewestBuiltForThisPlatform(): AppRelease? {
        val response =
            client.get("https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=30") {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "FetchJet/$APP_VERSION")
            }
        if (!response.status.isSuccess()) return null
        return newestBuiltFor(response.bodyAsText(), PlatformUpdater.platformKey) { assets ->
            !PlatformUpdater.canInstallInApp || PlatformUpdater.pickAsset(assets) != null
        }
    }

    internal suspend fun fetchLatestFromWebPage(): AppRelease? {
        val latest = client.get("https://github.com/$UPDATE_REPOSITORY/releases/latest") {
            header("User-Agent", "FetchJet/$APP_VERSION")
        }
        if (latest.status.value == 404) return null
        if (!latest.status.isSuccess()) throw Exception("GitHub answered HTTP ${latest.status.value}")
        val pageHtml = latest.bodyAsText()
        val pageUrl = latest.call.request.url.toString()
        val tag =
            pageUrl.substringAfter("/releases/tag/", "").substringBefore('?').substringBefore('#')
        if (tag.isBlank()) return null
        val assetsHtml =
            client.get("https://github.com/$UPDATE_REPOSITORY/releases/expanded_assets/$tag") {
                header("User-Agent", "FetchJet/$APP_VERSION")
            }.bodyAsText()
        // The page shows each file's digest too ("Copy to clipboard digest for <name>").
        val digests = Regex("""digest for ([^"]+)" type="button" value="sha256:([0-9a-f]{64})"""")
            .findAll(assetsHtml).associate { it.groupValues[1] to it.groupValues[2] }
        val assets = Regex("""href="(/$UPDATE_REPOSITORY/releases/download/[^"]+)"""")
            .findAll(assetsHtml)
            .map { it.groupValues[1] }
            .distinct()
            .map { path ->
                val name = path.substringAfterLast('/')
                ReleaseAsset(name, "https://github.com$path", 0L, digests[name].orEmpty())
            }
            .toList()
        val notes = assets.firstOrNull { it.name == RELEASE_NOTES_FILE }?.let { file ->
            runCatching {
                client.get(file.url) { header("User-Agent", "FetchJet/$APP_VERSION") }
                    .takeIf { it.status.isSuccess() }?.bodyAsText()?.trim()
            }.getOrNull()
        } ?: notesFromReleasePage(pageHtml)
        return AppRelease(
            version = tag.trim().removePrefix("v").removePrefix("V"),
            tag = tag,
            title = "FetchJet $tag",
            notes = notes,
            pageUrl = pageUrl,
            publishedAt = "",
            assets = assets
        )
    }

    private fun startDownload(release: AppRelease, installWhenDone: Boolean = false) {
        if (!PlatformUpdater.canInstallInApp) return
        if (downloadJob?.isActive == true) return
        val asset = PlatformUpdater.pickAsset(release.assets) ?: run {
            _state.update {
                it.copy(
                    message = LocalizationManager.Strings.sysUpdateNoInstallerForSystem,
                    messageIsInstallError = true
                )
            }
            return
        }
        downloadJob = scope.launch {
            _state.update {
                it.copy(
                    downloadProgress = 0f,
                    message = LocalizationManager.Strings.sysDownloadingAsset(asset.name),
                    messageIsInstallError = false,
                    messageIsError = false
                )
            }
            val dir = "${pathResolver.getTempDirectory()}/fetchjet-update".toPath()
            val target = dir / asset.name
            val part = dir / "${asset.name}.part"
            val ok = runCatching {
                AppFileSystem.createDirectories(dir)
                client.prepareGet(asset.url) {
                    header("User-Agent", "FetchJet/$APP_VERSION")
                }.execute { response ->
                    if (!response.status.isSuccess()) throw Exception("HTTP ${response.status.value}")
                    val total = response.contentLength() ?: asset.size
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    val hashing = okio.HashingSink.sha256(AppFileSystem.sink(part))
                    hashing.buffer().use { sink ->
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
                    // GitHub's own SHA-256 of the file: a changed or broken download is not installed.
                    if (asset.sha256.isNotEmpty() && hashing.hash.hex() != asset.sha256) {
                        throw Exception("checksum does not match the release")
                    }
                }
                // Signed by FetchJet's own key in CI (<file>.sig): a file that GitHub serves but
                // was not built by the owner's release workflow is never installed.
                val sigAsset = release.assets.firstOrNull { it.name == "${asset.name}.sig" }
                    ?: throw Exception("the release file is not signed")
                val sig = client.get(sigAsset.url) {
                    header("User-Agent", "FetchJet/$APP_VERSION")
                }.bodyAsText()
                if (!verifyUpdateSignature(part.toString(), sig)) {
                    throw Exception("the release file signature is not valid")
                }
                if (AppFileSystem.exists(target)) AppFileSystem.delete(target)
                AppFileSystem.atomicMove(part, target)
            }
            ok.onSuccess {
                _state.update {
                    it.copy(
                        downloadProgress = -1f,
                        installerPath = target.toString(),
                        showDialog = true,
                        message = LocalizationManager.Strings.sysVersionReadyToInstall(release.version),
                        messageIsInstallError = false,
                        messageIsError = false
                    )
                }
                if (installWhenDone) installNow()
            }.onFailure { e ->
                try {
                    AppFileSystem.delete(part)
                } catch (_: Throwable) {
                }
                _state.update {
                    it.copy(
                        downloadProgress = -1f,
                        message = LocalizationManager.Strings.sysUpdateDownloadFailed(e.message.toString()),
                        messageIsInstallError = true
                    )
                }
            }
        }
    }

    // ---- settings file (key=value) -----------------------------------------------------------

    private fun load(): Map<String, String> = try {
        if (!AppFileSystem.exists(settingsFile)) emptyMap()
        else AppFileSystem.read(settingsFile) { readUtf8() }.lineSequence()
            .map { it.trim() }
            .filter { it.contains('=') && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun save() {
        val s = _state.value
        try {
            settingsFile.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(settingsFile) {
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

const val MICROSOFT_STORE_PAGE = "ms-windows-store://pdp/?productid=9NP3HJRHMPJS"

/** Release file with the raw notes (markers included), uploaded by CI next to the builds. */
const val RELEASE_NOTES_FILE = "release-notes.md"

/** The notes in one app language (e.g. `release-notes-ar.md`); English is [RELEASE_NOTES_FILE]. */
fun releaseNotesFileFor(lang: String): String = "release-notes-$lang.md"

/**
 * The notes shown on a GitHub release page, as plain markdown-like text. Used for releases
 * without [RELEASE_NOTES_FILE]; the page has no `<!-- fetchjet-... -->` markers.
 */
internal fun notesFromReleasePage(html: String): String {
    val start = html.indexOf("data-test-selector=\"body-content\"")
    if (start < 0) return ""
    val open = html.indexOf('>', start)
    val end = html.indexOf("</div>", open)
    if (open < 0 || end < 0) return ""
    return html.substring(open + 1, end)
        .replace("\n", "")
        .replace(Regex("<h[1-6][^>]*>"), "\n## ")
        .replace(Regex("<p[^>]*>"), "\n")
        .replace(Regex("<li[^>]*>"), "- ")
        .replace(Regex("<br\\s*/?>|</p>|</li>|</h[1-6]>|</ul>|</ol>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

internal fun newestBuiltFor(
    releasesJson: String,
    platformKey: String,
    hasInstaller: (List<ReleaseAsset>) -> Boolean
): AppRelease? {
    val list = MiniJson.parse(releasesJson) as? List<*> ?: return null
    return list.asSequence()
        .mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            if (m["draft"] == true || m["prerelease"] == true) return@mapNotNull null
            releaseFromJson(m)
        }
        .filter { !it.unchangedPlatforms.contains(platformKey) && hasInstaller(it.assets) }
        .maxWithOrNull { a, b ->
            if (isNewer(a.version, b.version)) 1 else if (isNewer(b.version, a.version)) -1 else 0
        }
}

internal fun releaseFromJson(json: Map<String, Any?>): AppRelease? {
    val tag = json.getString("tag_name") ?: return null
    val assets = json.getArray("assets").orEmpty().mapNotNull { a ->
        @Suppress("UNCHECKED_CAST")
        val m = a as? Map<String, Any?> ?: return@mapNotNull null
        val name = m.getString("name") ?: return@mapNotNull null
        val url = m.getString("browser_download_url")
            ?.takeIf { it.startsWith(RELEASE_DOWNLOAD_PREFIX) } ?: return@mapNotNull null
        val sha = m.getString("digest").orEmpty().lowercase()
            .takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:").orEmpty()
        ReleaseAsset(name, url, m.getLong("size") ?: 0L, sha)
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
