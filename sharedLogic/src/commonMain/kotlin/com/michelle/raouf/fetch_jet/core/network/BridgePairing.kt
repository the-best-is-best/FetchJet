package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.core.filesystem.AppFileSystem
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.network.BridgePairing.ASK_COOLDOWN
import com.michelle.raouf.fetch_jet.core.security.Secrets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Who may use the local bridge (127.0.0.1:28450, desktop and Android). Every request that acts
 * needs the app's secret [token] (`X-FetchJet-Token`):
 *  - the FetchJet extension gets it once through `POST /pair`: the app asks the user
 *    ("Allow this browser extension?") and answers with the token, the extension keeps it;
 *  - a second FetchJet instance reads it from `bridge.json` (same user).
 * Extensions from before 1.2.0 (no token) used to be trusted by their `Origin` alone; a local
 * program can fake an `Origin`, so that path is gone and the saved origins are dropped on load.
 *
 * Hardening: a local program can fake a browser `Origin`, so it could pop the "Allow?" dialog.
 * To stop a program flooding the dialog to tire the user into tapping Allow, only one dialog
 * shows at a time (a request from another origin is refused, not queued), no new dialog shows
 * for [ASK_COOLDOWN] after any dialog closed, and at most [MAX_ASKS] dialogs show per
 * [ASK_WINDOW]. Tapping **Block** remembers the origin ([blocked]) so it is refused silently
 * afterwards; tapping outside the dialog is a one-time "no" and is not remembered. Blocked
 * origins are listed in Settings, where the user can unblock. Every refusal is written to the log.
 *
 * When the saved token cannot be decrypted (OS key store unavailable), it is kept on disk as it
 * is and every request is refused until it can be read again; a new token is only made when none
 * was saved.
 */
object BridgePairing {
    const val TOKEN_HEADER = "X-FetchJet-Token"
    private const val ANSWER_TIMEOUT_MS = 120_000L
    private val ASK_COOLDOWN = 8.seconds
    private val ASK_WINDOW = 10.minutes
    private const val MAX_ASKS = 5
    private val REVEAL_RETRY = 30.seconds

    @Serializable
    private data class BridgeFile(
        val token: String = "",
        val legacyOrigins: List<String> = emptyList(),
        val blockedOrigins: List<String> = emptyList(),
        val seenBrowsers: Map<String, Long> = emptyMap(),
        val seenVersions: Map<String, String> = emptyMap(),
        val automation: Boolean = false
    )

    data class Request(val origin: String, val legacy: Boolean = false)

    private val file get() = "${PathResolver().getSettingsDirectory()}/bridge.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val legacyOrigins = MutableStateFlow<Set<String>>(emptySet())
    private val blockedOrigins = MutableStateFlow<Set<String>>(emptySet())
    private val seen = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val versions = MutableStateFlow<Map<String, String>>(emptyMap())
    private val automation = MutableStateFlow(false)

    /** Read from bridge.json on every call, so a flag written by the Settings UI or the CLI process counts at once. */
    val automationOn: Boolean
        get() {
            ensureLoaded()
            val onDisk = readFile()?.automation ?: automation.value
            automation.value = onDisk
            return onDisk
        }

    /** Only the app's own UI / command line (same user, same settings folder) may call this; never a bridge request. */
    fun setAutomation(on: Boolean) {
        ensureLoaded()
        automation.value = on
        save(automationOverride = on)
        println("FetchJet bridge: automation API ${if (on) "on" else "off"}")
    }

    /** The extension version last reported by each browser (`X-FetchJet-Ext`); 3.3.0 and newer send it. */
    val seenVersions: StateFlow<Map<String, String>>
        get() {
            ensureLoaded()
            return versions.asStateFlow()
        }

    /** Always empty now (origins trusted without a token are no longer accepted); kept for the Settings list. */
    val legacyAllowed: StateFlow<Set<String>> = legacyOrigins.asStateFlow()
    val blocked: StateFlow<Set<String>> = blockedOrigins.asStateFlow()

    /**
     * Browsers whose paired extension talked to the app, by browser id (`chrome`, `edge`,
     * `firefox`, `opera`, `vivaldi`, `samsung`) with the time it was last seen (epoch ms).
     */
    val seenBrowsers: StateFlow<Map<String, Long>>
        get() {
            ensureLoaded()
            return seen.asStateFlow()
        }

    private val plainToken = MutableStateFlow<String?>(null)

    @Volatile
    private var storedToken = ""

    @Volatile
    private var lastRevealTry: TimeSource.Monotonic.ValueTimeMark? = null

    /** The secret, or "" while the saved one cannot be decrypted (then nothing is paired). */
    val token: String get() = currentToken().orEmpty()

    private fun currentToken(): String? {
        ensureLoaded()
        plainToken.value?.let { return it }
        if (storedToken.isEmpty()) {
            val fresh = Secrets.randomHex()
            if (plainToken.compareAndSet(null, fresh)) save()
            return plainToken.value
        }
        val last = lastRevealTry
        if (last != null && last.elapsedNow() < REVEAL_RETRY) return null
        lastRevealTry = TimeSource.Monotonic.markNow()
        val opened = Secrets.reveal(storedToken)
        if (opened == null) {
            println("FetchJet bridge: the saved token cannot be read; every request is refused until it can")
            return null
        }
        if (opened.length < 32) {
            if (plainToken.compareAndSet(null, Secrets.randomHex())) save()
            return plainToken.value
        }
        plainToken.compareAndSet(null, opened)
        return plainToken.value
    }

    /** Settings: every paired browser must pair again (a new secret replaces the old one). */
    fun unpairAll() {
        ensureLoaded()
        plainToken.value = Secrets.randomHex()
        seen.value = emptyMap()
        versions.value = emptyMap()
        save()
        println("FetchJet bridge: every paired browser was unpaired")
    }

    private val loaded: Boolean by lazy {
        val stored = readFile() ?: BridgeFile()
        blockedOrigins.value = stored.blockedOrigins.toSet()
        seen.value = stored.seenBrowsers
        versions.value = stored.seenVersions
        automation.value = stored.automation
        storedToken = stored.token
        if (stored.legacyOrigins.isNotEmpty()) {
            println("FetchJet bridge: dropped ${stored.legacyOrigins.size} origin(s) trusted without a token")
            save()
        }
        true
    }

    private fun ensureLoaded() {
        loaded
    }

    private class Question(val request: Request, val answer: CompletableDeferred<Boolean>)

    private val question = MutableStateFlow<Question?>(null)
    private val _pending = MutableStateFlow<Request?>(null)

    /** The question the UI shows; null when nothing waits. */
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    /** When each origin's dialog last closed, for the cooldown that blocks re-pop flooding. */
    private val lastClosed =
        MutableStateFlow<Map<String, TimeSource.Monotonic.ValueTimeMark>>(emptyMap())

    /** When the last dialogs were shown (any origin), for [MAX_ASKS] per [ASK_WINDOW]. */
    private val shownAt = MutableStateFlow<List<TimeSource.Monotonic.ValueTimeMark>>(emptyList())

    fun isPaired(sent: String?): Boolean {
        if (sent.isNullOrEmpty()) return false
        val mine = currentToken() ?: return false
        if (mine.isEmpty() || sent.length != mine.length) return false
        var diff = 0
        for (i in mine.indices) diff = diff or (mine[i].code xor sent[i].code)
        return diff == 0
    }

    fun isExtensionOrigin(origin: String?): Boolean =
        origin != null && (origin.startsWith("chrome-extension://") ||
                origin.startsWith("moz-extension://") || origin.startsWith("extension://") ||
                origin.startsWith("safari-web-extension://"))

    /**
     * The first check of every bridge request: the `Host` header (when sent) is a loopback name,
     * and the `Origin` (when sent) is a browser extension. Web pages and DNS-rebinding hosts fail.
     */
    fun isLocalRequest(hostHeader: String?, origin: String?): Boolean {
        val raw = hostHeader?.lowercase()
        val host = when {
            raw == null -> null
            raw.startsWith("[") -> raw.substringBefore("]") + "]"
            else -> raw.substringBefore(':')
        }
        if (host != null && host !in setOf("localhost", "127.0.0.1", "[::1]")) return false
        return origin == null || isExtensionOrigin(origin)
    }

    /** A user-blocked origin is refused without asking again. */
    fun isBlocked(origin: String?): Boolean {
        ensureLoaded()
        return origin != null && origin in blockedOrigins.value
    }

    private fun recentlyClosed(origin: String): Boolean =
        lastClosed.value[origin]?.let { it.elapsedNow() < ASK_COOLDOWN } ?: false

    private fun anyRecentlyClosed(): Boolean =
        lastClosed.value.values.any { it.elapsedNow() < ASK_COOLDOWN }

    private fun tooManyAsks(): Boolean =
        shownAt.value.count { it.elapsedNow() < ASK_WINDOW } >= MAX_ASKS

    private fun markClosed(origin: String) {
        lastClosed.update { all ->
            (all + (origin to TimeSource.Monotonic.markNow())).filterValues { it.elapsedNow() < ASK_COOLDOWN * 2 }
        }
    }

    /**
     * Asks the user (the bridge thread waits, at most 2 minutes). True = allowed. A second
     * request for the same origin waits for the same answer. [onShown] runs only when this call
     * put a new dialog up (to bring the app forward). A request for another origin while a dialog
     * is up, within [ASK_COOLDOWN] of any dialog closing, or past [MAX_ASKS] per [ASK_WINDOW], is
     * refused at once so a program cannot flood the user.
     */
    suspend fun ask(origin: String, onShown: () -> Unit = {}): Boolean {
        if (isBlocked(origin)) {
            println("FetchJet bridge: refused a blocked origin $origin")
            return false
        }
        if (currentToken() == null) {
            println("FetchJet bridge: refused pairing from $origin, the saved token cannot be read")
            return false
        }
        var mine: Question? = null
        var created = false
        question.update { q ->
            when {
                q != null && q.answer.isActive && q.request.origin == origin -> {
                    mine = q; created = false; q
                }

                q != null && q.answer.isActive -> {
                    mine = null; created = false; q
                }

                recentlyClosed(origin) || anyRecentlyClosed() || tooManyAsks() -> {
                    mine = null; created = false; q
                }

                else -> Question(Request(origin), CompletableDeferred()).also {
                    mine = it; created = true
                }
            }
        }
        val asked = mine
        if (asked == null) {
            println("FetchJet bridge: ignored a pairing request from $origin (another dialog, a recent one, or too many)")
            return false
        }
        if (created) {
            shownAt.update { list -> list.filter { it.elapsedNow() < ASK_WINDOW } + TimeSource.Monotonic.markNow() }
            if (question.value === asked) _pending.value = asked.request
            onShown()
        }
        val allowed = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { asked.answer.await() } ?: false
        if (question.compareAndSet(asked, null)) _pending.value = null
        markClosed(origin)
        return allowed
    }

    /** The dialog's Allow (true) or its tap-outside / one-time "no" (false). Not remembered. */
    fun answer(allow: Boolean) {
        val q = question.value ?: return
        q.answer.complete(allow)
        markClosed(q.request.origin)
        if (question.compareAndSet(q, null)) _pending.value = null
    }

    /** The dialog's Block: refuse and remember the origin so it is never asked again. */
    fun block() {
        val q = question.value ?: return
        blockedOrigins.update { it + q.request.origin }
        save()
        println("FetchJet bridge: user blocked ${q.request.origin}")
        q.answer.complete(false)
        markClosed(q.request.origin)
        if (question.compareAndSet(q, null)) _pending.value = null
    }

    /**
     * Called for a request that carried the token: remembers which browser the extension runs
     * in (from its User-Agent). Written to disk at most once an hour per browser.
     */
    fun noteBrowser(userAgent: String?, nowMs: Long, extVersion: String? = null) {
        val id = browserIdFromUserAgent(userAgent) ?: return
        ensureLoaded()
        val version = extVersion?.trim()?.takeIf { it.isNotEmpty() && it.length <= 20 }
        val versionChanged = version != null && versions.value[id] != version
        val last = seen.value[id]
        if (!versionChanged && last != null && nowMs - last < 3_600_000L) return
        seen.update { it + (id to nowMs) }
        if (version != null) versions.update { it + (id to version) }
        save()
    }

    fun browserIdFromUserAgent(userAgent: String?): String? {
        val ua = userAgent ?: return null
        return when {
            ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/") -> "edge"
            ua.contains("OPR/") || ua.contains("OPT/") -> "opera"
            ua.contains("Vivaldi") -> "vivaldi"
            ua.contains("SamsungBrowser") -> "samsung"
            ua.contains("Firefox/") || ua.contains("FxiOS/") -> "firefox"
            ua.contains("Chrome/") || ua.contains("CriOS/") -> "chrome"
            else -> null
        }
    }

    /** Settings: kept for the Settings list; there are no trusted origins any more. */
    fun revokeAllowed(origin: String) {
        legacyOrigins.update { it - origin }
    }

    /** Settings: let a blocked origin ask again. */
    fun unblock(origin: String) {
        blockedOrigins.update { it - origin }
        save()
    }

    private fun readFile(): BridgeFile? = try {
        if (!AppFileSystem.exists(file)) BridgeFile()
        else json.decodeFromString(
            BridgeFile.serializer(),
            AppFileSystem.read(file) { readUtf8() })
    } catch (_: Throwable) {
        null
    }

    private fun save(automationOverride: Boolean? = null) {
        try {
            val plain = plainToken.value
            val tokenText = if (plain != null) Secrets.protect(plain) else storedToken
            if (plain != null) storedToken = tokenText
            val automationValue = automationOverride ?: (readFile()?.automation ?: automation.value)
            automation.value = automationValue
            val text = json.encodeToString(
                BridgeFile.serializer(),
                BridgeFile(
                    tokenText,
                    emptyList(),
                    blockedOrigins.value.sorted(),
                    seen.value,
                    versions.value,
                    automationValue
                )
            )
            file.parent?.let { AppFileSystem.createDirectories(it) }
            AppFileSystem.write(file) { writeUtf8(text) }
        } catch (_: Throwable) {
        }
    }
}
