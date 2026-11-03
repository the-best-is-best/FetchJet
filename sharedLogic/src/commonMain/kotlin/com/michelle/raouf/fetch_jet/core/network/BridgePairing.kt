package com.michelle.raouf.fetch_jet.core.network

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.network.BridgePairing.isLegacyAllowed
import com.michelle.raouf.fetch_jet.core.network.BridgePairing.token
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

/**
 * Who may use the local bridge (127.0.0.1:28450, desktop and Android). Before, any program on
 * the computer / phone, or any browser extension, could queue downloads by sending
 * `X-FetchJet: 1`. Now every request needs the app's secret [token] (`X-FetchJet-Token`):
 *  - the FetchJet extension gets it once through `POST /pair`: the app asks the user
 *    ("Allow this browser extension?") and answers with the token, the extension keeps it;
 *  - a second FetchJet instance reads it from `bridge.json` (same user);
 *  - an extension from before 1.2.0 cannot send it: the user is asked once per extension origin
 *    and the origin is remembered ([isLegacyAllowed]). Drop that path once the stores have 3.2.0.
 */
object BridgePairing {
    const val TOKEN_HEADER = "X-FetchJet-Token"
    private const val ANSWER_TIMEOUT_MS = 120_000L

    @Serializable
    private data class BridgeFile(
        val token: String = "",
        val legacyOrigins: List<String> = emptyList()
    )

    data class Request(val origin: String, val legacy: Boolean)

    private val file get() = "${PathResolver().getSettingsDirectory()}/bridge.json".toPath()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val legacyOrigins = MutableStateFlow<Set<String>>(emptySet())

    /** The secret; made on first use and kept encrypted in bridge.json. */
    val token: String by lazy {
        val stored = load()
        legacyOrigins.value = stored.legacyOrigins.toSet()
        Secrets.reveal(stored.token)?.takeIf { it.length >= 32 }
            ?: Secrets.randomHex().also { save(it) }
    }

    private class Question(val request: Request, val answer: CompletableDeferred<Boolean>)

    private val question = MutableStateFlow<Question?>(null)
    private val _pending = MutableStateFlow<Request?>(null)

    /** The question the UI shows; null when nothing waits. */
    val pending: StateFlow<Request?> = _pending.asStateFlow()

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

    /**
     * Asks the user (the bridge thread waits, at most 2 minutes). True = allowed; a legacy
     * origin is then remembered. A second request for the same origin waits for the same answer;
     * one for another origin replaces the question (the first one is refused).
     */
    suspend fun ask(origin: String, legacy: Boolean): Boolean {
        var mine: Question? = null
        question.update { q ->
            if (q != null && q.answer.isActive && q.request.origin == origin) {
                mine = q; q
            } else {
                Question(Request(origin, legacy), CompletableDeferred()).also { mine = it }
            }
        }
        val asked = mine!!
        val replaced = question.value
        if (replaced === asked) _pending.value = asked.request
        val allowed = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { asked.answer.await() } ?: false
        if (question.compareAndSet(asked, null)) _pending.value = null
        return allowed
    }

    /** Called by the dialog. */
    fun answer(allow: Boolean) {
        val q = question.value ?: return
        if (allow && q.request.legacy) {
            legacyOrigins.update { it + q.request.origin }
            save(token)
        }
        q.answer.complete(allow)
        if (question.compareAndSet(q, null)) _pending.value = null
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
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileSystem.SYSTEM.write(file) {
                writeUtf8(
                    json.encodeToString(
                        BridgeFile.serializer(),
                        BridgeFile(Secrets.protect(plainToken), legacyOrigins.value.sorted())
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }
}
