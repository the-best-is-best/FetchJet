package com.michelle.raouf.fetch_jet.feature.automation

import com.michelle.raouf.fetch_jet.core.network.BridgePairing
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

class AutomationReply(val code: Int, val body: String)

object AutomationApi {
    const val PREFIX = "/api/"
    val ACTIONS = setOf("pause", "start", "remove", "delete")
    private val PAUSABLE = setOf(DownloadStatus.IDLE, DownloadStatus.QUEUED, DownloadStatus.CONNECTING, DownloadStatus.DOWNLOADING)
    private val STARTABLE = setOf(DownloadStatus.IDLE, DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.ERROR)

    private fun error(code: Int, message: String) =
        AutomationReply(code, buildJsonObject { put("error", message) }.toString())

    private fun taskJson(task: DownloadTask) = buildJsonObject {
        put("id", task.id)
        put("name", task.fileName)
        put("url", task.url)
        put("status", task.status.name.lowercase())
        put("progress", task.progress.toDouble().coerceIn(0.0, 1.0))
        put("downloadedBytes", task.downloadedBytes)
        put("totalBytes", task.totalBytes)
        put("speedBytesPerSec", task.downloadSpeedBytesPerSec)
        put("uploadBytesPerSec", task.uploadSpeedBytesPerSec)
        put("connections", task.connections)
        put("engine", task.engineType.name.lowercase())
        put("destination", task.destinationPath)
        put("error", task.errorMessage.orEmpty())
        put("step", task.currentStepText.orEmpty())
    }

    fun status(id: String?): AutomationReply {
        val tasks = QueueManagerRepository.getInstance().visibleTasksFlow.value
        if (!id.isNullOrBlank()) {
            val task = tasks.firstOrNull { it.id == id } ?: return error(404, "download not found")
            return AutomationReply(200, taskJson(task).toString())
        }
        val body = buildJsonObject {
            put("app", "FetchJet")
            put("version", APP_VERSION)
            put("count", tasks.size)
            put("speedBytesPerSec", tasks.sumOf { it.downloadSpeedBytesPerSec })
            put("downloads", buildJsonArray { tasks.forEach { add(taskJson(it)) } })
        }
        return AutomationReply(200, body.toString())
    }

    suspend fun act(action: String, ids: List<String>): AutomationReply {
        if (action !in ACTIONS) return error(404, "unknown action")
        val wanted = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(500)
        if (wanted.isEmpty()) return error(400, "missing id")
        val repository = QueueManagerRepository.getInstance()
        val byId = repository.visibleTasksFlow.value.associateBy { it.id }
        val missing = wanted.filter { it !in byId }
        val failed = ArrayList<String>()
        var done = 0
        for (id in wanted) {
            val current = byId[id] ?: continue
            val allowed = when (action) {
                "pause" -> current.status in PAUSABLE
                "start" -> current.status in STARTABLE
                else -> true
            }
            if (!allowed) {
                failed.add(id)
                continue
            }
            val result = when (action) {
                "pause" -> repository.pauseTask(id)
                "start" -> repository.resumeTask(id)
                "remove" -> repository.removeTask(id, deleteFile = false)
                else -> repository.removeTask(id, deleteFile = true)
            }
            if (result.isSuccess) done++ else failed.add(id)
        }
        val body = buildJsonObject {
            put("ok", missing.isEmpty() && failed.isEmpty())
            put("done", done)
            put("missing", buildJsonArray { missing.forEach { add(it) } })
            put("failed", buildJsonArray { failed.forEach { add(it) } })
        }
        val code = if (done == 0) 404.takeIf { missing.size == wanted.size } ?: 409 else 200
        return AutomationReply(code, body.toString())
    }

    suspend fun handle(
        method: String,
        path: String,
        hasOrigin: Boolean,
        hasMarker: Boolean,
        token: String?,
        query: Map<String, String>,
    ): AutomationReply {
        if (hasOrigin) return error(403, "forbidden")
        if (method != "GET" && method != "POST") return error(405, "GET or POST only")
        if (!hasMarker) return error(403, "missing X-FetchJet header")
        if (!BridgePairing.isPaired(token)) return error(401, "pairing required")
        val name = path.removePrefix("/api").trim('/')
        if (name == "automation") {
            if (query["on"] == "1") return error(403, "turn automation on in FetchJet itself (Settings or the command line)")
            if (query["on"] == "0" && BridgePairing.automationOn) BridgePairing.setAutomation(false)
            return AutomationReply(200, buildJsonObject { put("automation", BridgePairing.automationOn) }.toString())
        }
        if (!BridgePairing.automationOn) return error(403, "automation is off")
        if (name == "status") return status(query["id"])
        return act(name, (query["id"].orEmpty() + "," + query["ids"].orEmpty()).split(','))
    }
}
