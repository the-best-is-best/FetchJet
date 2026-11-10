package com.michelle.raouf.fetch_jet.nativeapp

import com.michelle.raouf.fetch_jet.core.browser.BrowserInfo
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.update.AppRelease
import com.michelle.raouf.fetch_jet.core.update.ReleaseAsset
import com.michelle.raouf.fetch_jet.core.update.UPDATE_REPOSITORY
import com.michelle.raouf.fetch_jet.core.update.UpdateState
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadRequest
import com.michelle.raouf.fetch_jet.feature.device_sync.DiscoveredDevice
import com.michelle.raouf.fetch_jet.feature.device_sync.PairingState
import com.michelle.raouf.fetch_jet.feature.device_sync.SendState
import com.michelle.raouf.fetch_jet.nativeapp.presenter.BrowserDialog
import com.michelle.raouf.fetch_jet.nativeapp.presenter.DeviceDebug
import com.michelle.raouf.fetch_jet.nativeapp.presenter.showBrowserDialog
import com.michelle.raouf.fetch_jet.platform.win.winMemoryUse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal object DebugCalls {

    fun call(name: String, args: String): String = when (name) {
        "tasks" -> tasks()
        "state" -> state()
        "memory" -> memory()
        "stats" -> stats()
        "addDirect" -> addDirect(args)
        "extensionSteps" -> extensionSteps(args)
        "update" -> update(args)
        "devices" -> devices(args)
        "sections" -> {
            NativeApp.presenter.debugSections.value = text(args, "all") == "true"
            "ok"
        }
        else -> ""
    }

    private fun text(args: String, key: String): String = try {
        (Json.parseToJsonElement(args.ifBlank { "{}" }) as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
    } catch (_: Throwable) {
        ""
    }

    private fun devices(args: String): String {
        val list = text(args, "devices")
        if (list.isBlank() && text(args, "pairing").isBlank() && text(args, "sending").isBlank()) {
            NativeApp.presenter.debugDevices.value = null
            return "cleared"
        }
        val found = list.split(';').filter { it.isNotBlank() }.mapIndexed { index, item ->
            val parts = item.split('|')
            DiscoveredDevice(
                id = "device-$index",
                name = parts[0],
                platform = parts.getOrElse(1) { "" },
                host = "127.0.0.1",
                port = 0,
                paired = parts.getOrElse(2) { "" } == "paired",
            )
        }
        val sendingText = text(args, "sending")
        val sending = when {
            sendingText == "waiting" -> SendState.Waiting("Galaxy A56")
            sendingText.startsWith("sending:") -> SendState.Sending("Galaxy A56", sendingText.substringAfter(':').toFloatOrNull() ?: 0f)
            sendingText.startsWith("failed:") -> SendState.Failed("Galaxy A56", sendingText.substringAfter(':'))
            sendingText == "done" -> SendState.Done("Galaxy A56")
            else -> null
        }
        val pairingText = text(args, "pairing")
        val pairing = when {
            pairingText == "connecting" -> PairingState.Connecting("Galaxy A56")
            pairingText.startsWith("confirm:") -> PairingState.Confirm("Galaxy A56 · Android", "device-0", pairingText.substringAfter(':'))
            pairingText.startsWith("failed:") -> PairingState.Failed("Galaxy A56", pairingText.substringAfter(':'))
            else -> null
        }
        NativeApp.presenter.debugDevices.value = DeviceDebug(found, sending, pairing)
        return "ok"
    }

    private fun update(args: String): String {
        val version = text(args, "version")
        if (version.isBlank()) {
            NativeApp.presenter.debugUpdate.value = null
            return "cleared"
        }
        val assetName = text(args, "assetName")
        val progress = text(args, "progress").toFloatOrNull() ?: -1f
        NativeApp.presenter.debugUpdate.value = UpdateState(
            canInstallInApp = text(args, "canInstall") == "true",
            available = AppRelease(
                version = version,
                tag = "v$version",
                title = "FetchJet $version",
                notes = text(args, "notes"),
                pageUrl = "https://github.com/$UPDATE_REPOSITORY/releases/tag/v$version",
                publishedAt = "",
                assets = if (assetName.isBlank()) emptyList()
                else listOf(ReleaseAsset(assetName, "", text(args, "assetSize").toLongOrNull() ?: 0L)),
            ),
            showDialog = text(args, "show") != "false",
            downloadProgress = progress,
            installerPath = text(args, "installer").ifBlank { null },
            message = text(args, "error").ifBlank { null },
            messageIsInstallError = text(args, "error").isNotBlank(),
        )
        return "ok"
    }

    private fun extensionSteps(args: String): String {
        val browser = BrowserInfo(
            id = "chrome",
            name = text(args, "name").ifBlank { "Google Chrome" },
            installed = true,
            downloadUrl = "",
            hasLocalCopy = true,
        )
        NativeApp.presenter.showBrowserDialog(
            BrowserDialog.Steps(browser, text(args, "folder"), text(args, "page").ifBlank { "chrome://extensions" })
        )
        return "ok"
    }

    private fun tasks(): String = buildJsonArray {
        for (task in NativeApp.presenter.vm.uiState.value.allTasks) {
            addJsonObject {
                put("id", task.id)
                put("url", task.url)
                put("fileName", task.fileName)
                put("destinationPath", task.destinationPath)
                put("engine", task.engineType.name)
                put("status", task.status.name)
                put("totalBytes", task.totalBytes)
                put("downloadedBytes", task.downloadedBytes)
                put("speed", task.downloadSpeedBytesPerSec)
                put("progress", task.progress)
                put("connections", task.connections)
                put("chunks", task.chunks.size)
                put("error", task.errorMessage.orEmpty())
                put("step", task.currentStepText.orEmpty())
                put("waitingInQueue", task.waitingInQueue)
                put("resumable", task.serverSupportsResume?.toString() ?: "unknown")
                put("checksumVerified", task.checksumVerified?.toString() ?: "unknown")
            }
        }
    }.toString()

    private fun state(): String {
        val s = NativeApp.presenter.vm.uiState.value
        return buildJsonObject {
            put("inputUrl", s.inputUrl)
            put("destinationPath", s.destinationPath)
            put("isAddDialogVisible", s.isAddDialogVisible)
            put("isLoading", s.isLoading)
            put("errorMessage", s.errorMessage.orEmpty())
            put("detectedEngine", s.detectedEngine.name)
            put("suggestedFileName", s.suggestedFileName)
            put("fileNameOverride", s.fileNameOverride)
            put("duplicate", s.duplicateTaskAlert?.id.orEmpty())
            put("fileConflict", s.fileConflictAlert?.fileName.orEmpty())
            put("removeConfirmation", s.removeConfirmationTaskId.orEmpty())
            put("tasks", s.allTasks.size)
            put("downSpeed", s.totalDownloadSpeedBytesPerSec)
            put("globalLimit", s.globalDownloadSpeedLimitBytes)
            put("ytDlpInstalled", s.isYtDlpInstalled)
            put("ffmpegInstalled", s.isFfmpegInstalled)
            put("ytDlpStatus", s.ytDlpStatus)
            put("toolsDownloading", s.isDownloadingYtDlp)
            put("torrentReady", s.isTorrentEngineReady)
            put("linkList", s.linkList.size)
            putJsonArray("qualities") { for (q in s.inspectedMediaQualities) add(kotlinx.serialization.json.JsonPrimitive(q.qualityLabel)) }
            put("inspectMediaError", s.inspectMediaError.orEmpty())
            put("isInspectingMedia", s.isInspectingMedia)
        }.toString()
    }

    private fun memory(): String {
        val use = winMemoryUse()
        return buildJsonObject {
            put("privateBytes", use?.privateBytes ?: -1L)
            put("workingSet", use?.workingSetBytes ?: -1L)
        }.toString()
    }

    private fun stats(): String = buildJsonObject {
        put("marks", NativeApp.notifier.marks.value)
        put("posts", NativeApp.notifier.posts.value)
        put("deliveries", NativeApp.notifier.deliveries.value)
        put("hostPosts", NativeApp.main.hostPosts)
        put("tasksRun", NativeApp.main.tasksRun)
    }.toString()

    private fun addDirect(args: String): String {
        val url = text(args, "url")
        if (url.isBlank()) return "no url"
        ExternalDownloadBridge.dispatch(
            ExternalDownloadRequest(url = url, fileName = text(args, "fileName"), referer = text(args, "referer"), direct = true)
        )
        return "ok"
    }
}
