package com.michelle.raouf.fetch_jet.core.network

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
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Who may use the local bridge (127.0.0.1:28450, desktop and Android). Before, any program on
 * the computer / phone, or any browser extension, could queue downloads by sending
 * `X-FetchJet: 1`. Now every request needs the app's secret [token] (`X-FetchJet-Token`):
 *  - the FetchJet extension gets it once through `POST /pair`: the app asks the user
 *    ("Allow this browser extension?") and answers with the token, the extension keeps it;
 *  - a second FetchJet instance reads it from `bridge.json` (same user);
 *  - an extension from before 1.2.0 cannot send it: the user is asked once per extension origin
 *    and the origin is remembered ([isLegacyAllowed]). Drop that path once the stores have 3.2.0.
 *
 * Hardening: a local program can fake a browser `Origin`, so it could pop the "Allow?" dialog.
 * To stop a program flooding the dialog to tire the user into tapping Allow, only one dialog
 * shows at a time (a request from another origin is refused, not queued) and a repeat for the
 * same origin is ignored for [ASK_COOLDOWN] after the last one closed. Tapping **Block**
 * remembers the origin ([blocked]) so it is refused silently afterwards; tapping outside the
 * dialog is a one-time "no" and is not remembered. Allowed and blocked origins are listed in
 * Settings, where the user can revoke or unblock. Every refusal is written to the log.
 */
object BridgePairing {
    const val TOKEN_HEADER = "X-FetchJet-Token"
    private const val ANSWER_TIMEOUT_MS = 120_000L
    private val ASK_COOLDOWN = 8.seconds

    @Serializable
    private data class BridgeFile(
        val token: String = "",
        val legacyOrigins: List<String> = emptyList(),
        val blockedOrigins: List<String> = emptyList(),
        val seenBrowsers: Map<String, Long> = emptyMap(),
        val seenVersions: Map<String, String> = emptyMap()
    )

    data class Request(val origin: String, val legacy: Boolean)

    private val file get() = "${PathResolver().getSettingsDirectory()}/bridge.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val legacyOrigins = MutableStateFlow<Set<String>>(emptySet())
    private val blockedOrigins = MutableStateFlow<Set<String>>(emptySet())
    private val seen = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val versions = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The extension version last reported by each browser (`X-FetchJet-Ext`); 3.3.0 and newer send it. */
    val seenVersions: StateFlow<Map<String, String>>
        get() {
            token // loads the file
            return versions.asStateFlow()
        }

    /** For the Settings list. */
    val legacyAllowed: StateFlow<Set<String>> = legacyOrigins.asStateFlow()
    val blocked: StateFlow<Set<String>> = blockedOrigins.asStateFlow()

    /**
     * Browsers whose paired extension talked to the app, by browser id (`chrome`, `edge`,
     * `firefox`, `opera`, `vivaldi`, `samsung`) with the time it was last seen (epoch ms).
     */
    val seenBrowsers: StateFlow<Map<String, Long>>
        get() {
            token // loads the file
            return seen.asStateFlow()
        }

    /** The secret; made on first use and kept encrypted in bridge.json. */
    val token: String by lazy {
        val stored = load()
        legacyOrigins.value = stored.legacyOrigins.toSet()
        blockedOrigins.value = stored.blockedOrigins.toSet()
        seen.value = stored.seenBrowsers
        versions.value = stored.seenVersions
        Secrets.reveal(stored.token)?.takeIf { it.length >= 32 }
            ?: Secrets.randomHex().also { save(it) }
    }

    private class Question(val request: Request, val answer: CompletableDeferred<Boolean>)

    private val question = MutableStateFlow<Question?>(null)
    private val _pending = MutableStateFlow<Request?>(null)

    /** The question the UI shows; null when nothing waits. */
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    /** When each origin's dialog last closed, for the cooldown that blocks re-pop flooding. */
    private val lastClosed =
        MutableStateFlow<Map<String, TimeSource.Monotonic.ValueTimeMark>>(emptyMap())

    fun isPaired(sent: String?): Boolean {
        if (sent.isNullOrEmpty()) return false
        val mine = token
        if (sent.length != mine.length) return false
        var diff = 0
        for (i in mine.indices) diff = diff or (mine[i].code xor sent[i].code)
        return diff == 0
    }

    fun isExtensionOrigin(origin: String?): Boolean =
        origin != null && (origin.startsWith("chrome-extension://") ||
                origin.startsWith("moz-extension://") || origin.startsWith("extension://") ||
                origin.startsWith("safari-web-extension://"))

    fun isLegacyAllowed(origin: String?): Boolean {
        token // loads the file
        return origin != null && origin in legacyOrigins.value
    }

    /** A user-blocked origin is refused without asking again. */
    fun isBlocked(origin: String?): Boolean {
        token // loads the file
        return origin != null && origin in blockedOrigins.value
    }

    private fun recentlyClosed(origin: String): Boolean =
        lastClosed.value[origin]?.let { it.elapsedNow() < ASK_COOLDOWN } ?: false

    private fun markClosed(origin: String) {
        lastClosed.update { it + (origin to TimeSource.Monotonic.markNow()) }
    }

    /**
     * Asks the user (the bridge thread waits, at most 2 minutes). True = allowed; a legacy
     * origin is then remembered. A second request for the same origin waits for the same answer.
     * A request for another origin while a dialog is up, or a repeat within [ASK_COOLDOWN] of the
     * last one closing, is refused at once (no second dialog) so a program cannot flood the user.
     */
    suspend fun ask(origin: String, legacy: Boolean): Boolean {
        if (isBlocked(origin)) {
            println("FetchJet bridge: refused a blocked origin $origin")
            return false
        }
        var mine: Question? = null
        var refused = false
        question.update { q ->
            when {
                q != null && q.answer.isActive && q.request.origin == origin -> {
                    mine = q; refused = false; q
                }

                q != null && q.answer.isActive -> {
                    // Another dialog is already up: do not replace it or queue behind it.
                    mine = null; refused = true; q
                }

                recentlyClosed(origin) -> {
                    mine = null; refused = true; q
                }

                else -> Question(Request(origin, legacy), CompletableDeferred()).also {
                    mine = it; refused = false
                }
            }
        }
        if (refused || mine == null) {
            println("FetchJet bridge: ignored a repeated pairing request from $origin")
            return false
        }
        val asked = mine
        if (question.value === asked) _pending.value = asked.request
        val allowed = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { asked.answer.await() } ?: false
        if (question.compareAndSet(asked, null)) _pending.value = null
        markClosed(origin)
        return allowed
    }

    /** The dialog's Allow (true) or its tap-outside / one-time "no" (false). Not remembered. */
    fun answer(allow: Boolean) {
        val q = question.value ?: return
        if (allow && q.request.legacy) {
            legacyOrigins.update { it + q.request.origin }
            save(token)
        }
        q.answer.complete(allow)
        markClosed(q.request.origin)
        if (question.compareAndSet(q, null)) _pending.value = null
    }

    /** The dialog's Block: refuse and remember the origin so it is never asked again. */
    fun block() {
        val q = question.value ?: return
        blockedOrigins.update { it + q.request.origin }
        legacyOrigins.update { it - q.request.origin }
        save(token)
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
        token // loads the file
        val version = extVersion?.trim()?.takeIf { it.isNotEmpty() && it.length <= 20 }
        val versionChanged = version != null && versions.value[id] != version
        val last = seen.value[id]
        if (!versionChanged && last != null && nowMs - last < 3_600_000L) return
        seen.update { it + (id to nowMs) }
        if (version != null) versions.update { it + (id to version) }
        save(token)
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

    /** Settings: stop trusting a previously allowed origin. */
    fun revokeAllowed(origin: String) {
        legacyOrigins.update { it - origin }
        save(token)
    }

    /** Settings: let a blocked origin ask again. */
    fun unblock(origin: String) {
        blockedOrigins.update { it - origin }
        save(token)
    }

    private fun load(): BridgeFile = try {
        if (!FileSystem.SYSTEM.exists(file)) BridgeFile()
        else json.decodeFromString(
            BridgeFile.serializer(),
            FileSystem.SYSTEM.read(file) { readUtf8() })
    } catch (_: Throwable) {
        BridgeFile()
    }

    private fun save(plainToken: String) {
        try {
            val text = json.encodeToString(
                BridgeFile.serializer(),
                BridgeFile(
                    Secrets.protect(plainToken),
                    legacyOrigins.value.sorted(),
                    blockedOrigins.value.sorted(),
                    seen.value,
                    versions.value
                )
            )
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(file) { writeUtf8(text) }
        } catch (_: Throwable) {
        }
    }
}
