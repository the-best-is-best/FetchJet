package com.michelle.raouf.fetch_jet.nativeapp

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopApp
import com.michelle.raouf.fetch_jet.platform.desktop.DesktopOs
import com.michelle.raouf.fetch_jet.platform.desktop.exists
import com.michelle.raouf.fetch_jet.platform.desktop.isDirectory
import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest
import com.michelle.raouf.fetch_jet.core.startup.AUTOSTART_ARG
import com.michelle.raouf.fetch_jet.core.startup.StartupPreferences
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.nativeapp.bridge.isAllowedUrl
import com.michelle.raouf.fetch_jet.nativeapp.bridge.percentDecode
import com.michelle.raouf.fetch_jet.nativeapp.presenter.Presenter
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
internal data class LaunchResult(
    val exit: Boolean = false,
    val hidden: Boolean = false,
    val loginStart: Boolean = false,
    val url: String = "",
    val urls: List<String> = emptyList(),
    val path: String = "",
    val file: String = "",
    val add: Boolean = false,
    val silent: Boolean = false,
    val startQueue: Boolean = false,
)

internal data class LaunchCommand(
    val urls: List<String> = emptyList(),
    val path: String = "",
    val file: String = "",
    val add: Boolean = false,
    val silent: Boolean = false,
    val startQueue: Boolean = false,
) {
    val plain: Boolean get() = !add && !silent && !startQueue && path.isBlank() && file.isBlank() && urls.size <= 1
    val idle: Boolean get() = urls.isEmpty() && !startQueue
}

internal object Launch {
    private const val HOLD_MS = 24 * 60 * 60 * 1000L

    @Volatile
    var pendingUrl: String = ""

    @Volatile
    var loginStart: Boolean = false

    @Volatile
    private var pending: LaunchCommand? = null

    private val waiting = AtomicInt(0)

    private fun cleanValue(arg: String): String = arg.trim().removeSurrounding("\"").trimEnd('"').trim()

    private fun fileNameOnly(name: String): String = name.substringAfterLast('/').substringAfterLast('\\').trim()

    fun parseCommand(args: List<String>): LaunchCommand {
        val urls = ArrayList<String>()
        var path = ""
        var file = ""
        var add = false
        var silent = false
        var startQueue = false
        var index = 0
        while (index < args.size) {
            val arg = args[index].trim()
            index++
            when (arg.lowercase()) {
                "/d", "--download" -> if (index < args.size) {
                    val url = parseUrlFromArg(args[index++])
                    if (url.isNotBlank()) urls.add(url)
                }

                "/p", "--path" -> if (index < args.size) path = cleanValue(args[index++])
                "/f", "--file" -> if (index < args.size) file = cleanValue(args[index++])
                "/a", "--add" -> add = true
                "/n", "--silent" -> silent = true
                "/s", "--start-queue" -> startQueue = true
                else -> if (arg.isNotBlank()) {
                    val url = parseUrlFromArg(arg)
                    if (url.isNotBlank()) urls.add(url)
                }
            }
        }
        val links = urls.distinct()
        return LaunchCommand(
            urls = links,
            path = if (path.isBlank()) "" else DesktopOs.fullPath(path),
            file = if (links.size == 1) fileNameOnly(file) else "",
            add = add,
            silent = silent,
            startQueue = startQueue,
        )
    }

    fun commandFromQuery(values: Map<String, String>): LaunchCommand? {
        val raw = (listOf(values["url"].orEmpty()) + values["more"].orEmpty().split('\n')).map { it.trim() }.filter { it.isNotBlank() }
        if (raw.any { it.length > 8192 || it.any { c -> c < ' ' } }) return null
        val links = raw.map { parseUrlFromArg(it) }
        if (links.any { it.isBlank() }) return null
        val path = values["path"].orEmpty().trim()
        val file = values["file"].orEmpty().trim()
        if (path.length > 4096 || path.any { it < ' ' } || file.length > 255 || file.any { it < ' ' }) return null
        val urls = links.distinct()
        val command = LaunchCommand(
            urls = urls,
            path = path,
            file = if (urls.size == 1) fileNameOnly(file) else "",
            add = values["add"] == "1",
            silent = values["silent"] == "1",
            startQueue = values["start"] == "1",
        )
        return command.takeUnless { it.idle }
    }

    private fun openAddDialog(presenter: Presenter, urls: List<String>, path: String, file: String) {
        val vm = presenter.vm
        if (path.isNotBlank()) {
            presenter.addFields.destination.force()
            vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(path))
        }
        if (urls.size == 1) {
            ExternalDownloadBridge.dispatch(ExternalDownloadRequest(url = urls[0], fileName = file))
            val name = QueueManagerRepository.getInstance().pathResolver.sanitizeFileName(file)
            if (file.isNotBlank() && name.isNotBlank()) {
                presenter.addFields.fileName.force()
                vm.onEvent(DashboardUiEvent.OnFileNameChanged(name))
            }
        } else {
            vm.onEvent(DashboardUiEvent.OnUrlChanged(urls.joinToString("\n")))
            vm.onEvent(DashboardUiEvent.OnToggleAddDialog(true))
        }
    }

    private suspend fun addDirectly(presenter: Presenter, urls: List<String>, path: String, file: String, start: Boolean) {
        val repository = QueueManagerRepository.getInstance()
        val state = presenter.vm.uiState.value
        val folder = path.ifBlank { state.destinationPath.ifBlank { repository.pathResolver.getDownloadsDirectory() } }
        if (path.isNotBlank()) DesktopOs.createDirectories(path)
        val name = file.takeIf { it.isNotBlank() }?.let { repository.pathResolver.sanitizeFileName(it) }?.takeIf { it.isNotBlank() }
        val pages = ArrayList<String>()
        for (url in urls) {
            val known = repository.allTasksFlow.value.any {
                it.url.equals(url, ignoreCase = true) || it.originalUrl.equals(url, ignoreCase = true)
            }
            if (known) continue
            val engine = repository.detectLinkTypeUseCase(url)
            if (engine == EngineType.MEDIA_STREAM) {
                pages.add(url)
                continue
            }
            val added = repository.enqueueTask(
                url = url,
                customDestinationPath = folder,
                customFileName = name,
                disableUpload = state.disableTorrentUpload,
                maxDownloadSpeedBytesPerSec = state.selectedDownloadSpeedLimitBytes,
                maxUploadSpeedBytesPerSec = state.selectedUploadSpeedLimitBytes,
                forcedEngineType = engine,
                sortByType = path.isBlank(),
                scheduledAtMs = if (start) 0L else currentEpochMs() + HOLD_MS,
                keepExistingFiles = true,
            )
            if (!start) added.getOrNull()?.let { repository.pauseTask(it.id) }
        }
        if (pages.isNotEmpty()) {
            openAddDialog(presenter, pages, path, "")
            ExternalDownloadBridge.showAndFocus()
        }
    }

    suspend fun run(command: LaunchCommand) {
        try {
            val presenter = NativeApp.presenter
            when {
                command.urls.isEmpty() -> Unit
                command.add -> addDirectly(presenter, command.urls, command.path, command.file, start = false)
                command.silent -> {
                    val (batch, direct) =
                        if (command.path.isNotBlank()) emptyList<String>() to command.urls else command.urls.partition { isAllowedUrl(it) }
                    if (batch.isNotEmpty()) {
                        ExternalDownloadBridge.dispatchBatch(batch.map { ExternalDownloadRequest(url = it, fileName = command.file) })
                    }
                    if (direct.isNotEmpty()) addDirectly(presenter, direct, command.path, command.file, start = true)
                }

                else -> openAddDialog(presenter, command.urls, command.path, command.file)
            }
            if (command.startQueue) QueueManagerRepository.getInstance().resumeAll()
            if (!command.silent) ExternalDownloadBridge.showAndFocus()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("launch: command failed: ${e.message}")
        }
    }

    private fun runWhenStarted() {
        if (!waiting.compareAndSet(0, 1)) return
        CoroutineScope(Dispatchers.Default).launch {
            while (!NativeApp.isRunning) delay(100)
            delay(600)
            waiting.value = 0
            val command = pending
            pending = null
            if (command != null) run(command)
        }
    }

    fun parseUrlFromArg(arg: String): String {
        val clean = arg.trim().removeSurrounding("\"")
        val schemePrefix = when {
            clean.startsWith("fetchjet://", ignoreCase = true) -> "fetchjet://"
            clean.startsWith("kortex://", ignoreCase = true) -> "kortex://"
            else -> null
        }
        if (schemePrefix != null) {
            val queryIndex = clean.indexOf('?')
            if (queryIndex != -1 && queryIndex < clean.length - 1) {
                val query = clean.substring(queryIndex + 1)
                for (param in query.split("&")) {
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].equals("url", ignoreCase = true)) {
                        return try {
                            percentDecode(parts[1])
                        } catch (_: Throwable) {
                            parts[1]
                        }
                    }
                }
            }
            val remainder = clean.substring(schemePrefix.length).trimStart('/')
            if (remainder.startsWith("http://", ignoreCase = true) || remainder.startsWith("https://", ignoreCase = true) ||
                com.michelle.raouf.fetch_jet.feature.http_downloader.isFtpLink(remainder)
            ) {
                return remainder
            }
        }
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("magnet:", ignoreCase = true) ||
            com.michelle.raouf.fetch_jet.feature.http_downloader.isFtpLink(clean)
        ) {
            return clean
        }
        val path = clean.removePrefix("file://")
        if (clean.lowercase().endsWith(".torrent") && DesktopOs.exists(path) && !DesktopOs.isDirectory(path)) {
            return DesktopOs.fullPath(path)
        }
        return ""
    }

    private fun get(port: Int, path: String, timeoutMs: Long, withToken: Boolean): Pair<Int, String>? = try {
        runBlocking {
            val client = HttpClient(DesktopOs.httpEngine) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                    requestTimeoutMillis = timeoutMs * 3
                }
            }
            try {
                val response = client.get("http://127.0.0.1:$port$path") {
                    header("X-FetchJet", "1")
                    if (withToken) header(BridgePairing.TOKEN_HEADER, BridgePairing.token)
                }
                response.status.value to response.bodyAsText()
            } finally {
                client.close()
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun sendToRunningInstance(port: Int, url: String): Boolean {
        val answer = get(port, "/download?url=" + url.encodeURLParameter(spaceToPlus = true), 800, withToken = true)
        return answer != null && answer.first in 200..299
    }

    private fun sendCommand(port: Int, command: LaunchCommand): Boolean {
        fun value(text: String) = text.encodeURLParameter(spaceToPlus = true)
        val path = buildString {
            append("/download?cli=1")
            command.urls.firstOrNull()?.let { append("&url=").append(value(it)) }
            if (command.urls.size > 1) append("&more=").append(value(command.urls.drop(1).joinToString("\n")))
            if (command.path.isNotBlank()) append("&path=").append(value(command.path))
            if (command.file.isNotBlank()) append("&file=").append(value(command.file))
            if (command.add) append("&add=1")
            if (command.silent) append("&silent=1")
            if (command.startQueue) append("&start=1")
        }
        val answer = get(port, path, 800, withToken = true)
        return answer != null && answer.first in 200..299
    }

    private fun anotherInstanceRunning(port: Int): Boolean {
        val answer = get(port, "/ping", 500, withToken = false) ?: return false
        val running = answer.first in 200..299 && answer.second.trim() == "PONG"
        if (running) get(port, "/show", 500, withToken = true)
        return running
    }

    fun prepare(config: StartConfig): LaunchResult {
        val port = if (config.bridgePort > 0) config.bridgePort else com.michelle.raouf.fetch_jet.nativeapp.bridge.BRIDGE_PORT
        val startedAtLogin = config.args.any { it.trim() == AUTOSTART_ARG } ||
            (config.args.isEmpty() && DesktopApp.looksLikeLoginStart())
        val command = parseCommand(config.args.filter { it.trim() != AUTOSTART_ARG })
        val url = command.urls.firstOrNull().orEmpty()
        if (config.bridge) {
            val attempts = if (config.secondInstance) 10 else 1
            for (attempt in 1..attempts) {
                val handed = when {
                    command.idle -> anotherInstanceRunning(port)
                    command.plain && isAllowedUrl(url) -> sendToRunningInstance(port, url)
                    else -> sendCommand(port, command)
                }
                if (handed) return LaunchResult(exit = true)
                if (attempt < attempts) runBlocking { delay(500) }
            }
            if (config.secondInstance) return LaunchResult(exit = true)
        }
        val waits = !command.plain && !command.idle
        pendingUrl = if (command.plain) url else ""
        pending = if (waits) command else null
        if (waits) runWhenStarted()
        loginStart = startedAtLogin
        val quiet = waits && command.silent
        val hidden = quiet || startedAtLogin && try {
            StartupPreferences.startHidden
        } catch (_: Throwable) {
            false
        }
        return LaunchResult(
            exit = false,
            hidden = hidden,
            loginStart = startedAtLogin,
            url = url,
            urls = command.urls,
            path = command.path,
            file = command.file,
            add = command.add,
            silent = command.silent,
            startQueue = command.startQueue,
        )
    }
}
