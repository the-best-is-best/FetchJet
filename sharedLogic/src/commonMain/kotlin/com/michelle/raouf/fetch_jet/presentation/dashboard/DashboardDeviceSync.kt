package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.notification.SystemNotice
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.feature.device_sync.SendState
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry
import com.michelle.raouf.fetch_jet.domain.model.canSendToDevice
import com.michelle.raouf.fetch_jet.domain.model.canTransferToDevice
import com.michelle.raouf.fetch_jet.feature.device_sync.SendMode
import com.michelle.raouf.fetch_jet.feature.device_sync.SendPayload
import com.michelle.raouf.fetch_jet.feature.device_sync.SendRequest
import com.michelle.raouf.fetch_jet.feature.queue_manager.nowEpochMs
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * ViewModel glue for "Send a download to another device" (ROADMAP-1.3.0 item 4). The transport is
 * in [com.michelle.raouf.fetch_jet.feature.device_sync]; here we start it, mirror its flows into the
 * UI state, add a received download to the queue (continuing from the sent bytes when present), and
 * drive the Share sheet / pairing / send events.
 */

internal fun DashboardViewModel.initDeviceSync() {
    removeEmptyReceiveFolders()
    deviceSync.setVisible(BehaviorSettings.visibleToDevices.value)
    deviceSync.start()
    scope.launch {
        BehaviorSettings.visibleToDevices.collect { deviceSync.setVisible(it) }
    }
    scope.launch {
        deviceSync.discovered.collect { list ->
            _uiState.update { it.copy(discoveredDevices = list) }
        }
    }
    scope.launch {
        deviceSync.paired.collect { list ->
            _uiState.update { it.copy(pairedDevices = list) }
        }
    }
    scope.launch {
        deviceSync.blocked.collect { list ->
            _uiState.update { it.copy(blockedDevices = list) }
        }
    }
    scope.launch {
        deviceSync.pairing.collect { state ->
            _uiState.update { it.copy(devicePairing = state) }
        }
    }
    scope.launch {
        deviceSync.sending.collect { state ->
            _uiState.update { it.copy(deviceSending = state) }
        }
    }
}

/** A download arrived from another device. Add it here; continue from the sent bytes if any. */
internal fun DashboardViewModel.onDeviceReceive(
    payload: SendPayload,
    localPartialFiles: List<String>
) {
    scope.launch {
        val engine = runCatching { EngineType.valueOf(payload.engineType) }
            .getOrDefault(EngineType.HTTP_DIRECT)
        // Media is re-resolved from its page; a direct link downloads the URL itself.
        val url = if (engine == EngineType.MEDIA_STREAM && payload.originalUrl.isNotBlank())
            payload.originalUrl else payload.url
        val headers = mutableMapOf<String, String>().apply {
            if (payload.cookie.isNotBlank()) put("Cookie", payload.cookie)
            if (payload.referer.isNotBlank()) put("Referer", payload.referer)
            if (payload.userAgent.isNotBlank()) put("User-Agent", payload.userAgent)
        }
        val transfer = localPartialFiles.isNotEmpty() && engine != EngineType.BITTORRENT
        // Hold a transfer QUEUED for a moment so we can drop the received parts into its scratch
        // folder before the engine starts; it then resumes from them instead of refetching.
        val scheduledAt = if (transfer) nowEpochMs() + 4000 else 0L
        // Media: the same format as on the sender, so the received pieces belong to that stream.
        val quality = if (engine == EngineType.MEDIA_STREAM && payload.formatId.isNotBlank()) listOf(
            MediaQualityEntry(
                index = 0,
                qualityLabel = "",
                directUrl = url,
                format = payload.fileName.substringAfterLast('.', ""),
                formatId = payload.formatId,
                originalUrl = payload.originalUrl.ifBlank { url }
            )
        ) else emptyList()
        val result = queueRepository.enqueueTask(
            url = url,
            customFileName = payload.fileName.takeIf { it.isNotBlank() },
            mediaQualities = quality,
            forcedEngineType = engine,
            clipStart = payload.clipStart.takeIf { it.isNotBlank() },
            clipEnd = payload.clipEnd.takeIf { it.isNotBlank() },
            clipByCutting = payload.clipByCutting,
            requestHeaders = headers,
            scheduledAtMs = scheduledAt
        )
        val task = result.getOrNull()
        if (task != null && transfer) {
            placeReceivedParts(task.id, localPartialFiles)
        }
        val fromName = payload.fromDeviceName.ifBlank { payload.fromDeviceId }
        val message = LocalizationManager.Strings.sysDeviceReceived.replace("{name}", fromName)
        _uiEffect.tryEmit(DashboardUiEffect.ShowToast(message))
        SystemNotice.show("device-recv-${task?.id ?: nowEpochMs()}", message, payload.fileName)
    }
}

/** Empty `recv-<ts>` folders that older versions left after a link-only receive. */
private fun DashboardViewModel.removeEmptyReceiveFolders() {
    val root = "${queueRepository.pathResolver.getSettingsDirectory()}/device-sync-tmp".toPath()
    runCatching {
        FileSystem.SYSTEM.listOrNull(root)
            ?.filter { it.name.startsWith("recv-") && FileSystem.SYSTEM.listOrNull(it)?.isEmpty() == true }
            ?.forEach { FileSystem.SYSTEM.delete(it) }
    }
}

/** Move the received `.partN` / `.chunks` files into the new task's scratch dir so it resumes. */
private fun DashboardViewModel.placeReceivedParts(taskId: String, files: List<String>) {
    val destDir = "${queueRepository.pathResolver.getTempDirectory()}/task_$taskId"
    runCatching { FileSystem.SYSTEM.createDirectories(destDir.toPath()) }
    for (path in files) {
        val src = path.toPath()
        val name = src.name
        val dst = "$destDir/$name".toPath()
        runCatching {
            if (FileSystem.SYSTEM.exists(dst)) FileSystem.SYSTEM.delete(dst)
            // Same volume (both under the app temp dir) → atomic rename; else copy.
            runCatching { FileSystem.SYSTEM.atomicMove(src, dst) }
                .onFailure {
                    FileSystem.SYSTEM.copy(src, dst)
                    runCatching { FileSystem.SYSTEM.delete(src) }
                }
        }
    }
    // The now-empty `recv-<ts>` staging folders would otherwise pile up in the settings dir.
    files.mapNotNull { it.toPath().parent }.distinct().forEach { recv ->
        runCatching { FileSystem.SYSTEM.deleteRecursively(recv) }
    }
}

internal fun DashboardViewModel.handleDeviceSyncEvent(event: DashboardUiEvent) {
    when (event) {
        is DashboardUiEvent.OnShareTaskClicked ->
            _uiState.update { it.copy(deviceShareTaskId = event.taskId, deviceSending = null) }

        DashboardUiEvent.OnDismissShareSheet -> {
            deviceSync.cancelSend()
            _uiState.update { it.copy(deviceShareTaskId = null) }
        }

        is DashboardUiEvent.OnSendToDeviceClicked -> startDeviceSend(event)

        DashboardUiEvent.OnConfirmPairingClicked -> {
            deviceSync.confirmPairing()
            // Both sides must confirm; the receiver may be a beat behind, so retry on "not paired".
            pendingDeviceSend?.let { req ->
                pendingDeviceSend = null
                scope.launch { sendWithRetry(req, tries = 4) }
            }
        }

        DashboardUiEvent.OnCancelPairingClicked -> {
            deviceSync.cancelPairing()
            pendingDeviceSend = null
            _uiState.update { it.copy(devicePairing = null) }
        }

        is DashboardUiEvent.OnUnpairDeviceClicked -> deviceSync.unpair(event.deviceId)

        DashboardUiEvent.OnBlockPairingClicked -> {
            deviceSync.blockPairing()
            pendingDeviceSend = null
            _uiState.update { it.copy(devicePairing = null) }
        }

        is DashboardUiEvent.OnUnblockDeviceClicked -> deviceSync.unblock(event.deviceId)

        is DashboardUiEvent.OnManualConnectClicked -> {
            val ip = event.ip.trim()
            if (ip.isNotEmpty()) deviceSync.startPairingWithHost(ip)
        }

        else -> Unit
    }
}

private fun DashboardViewModel.startDeviceSend(event: DashboardUiEvent.OnSendToDeviceClicked) {
    val task = queueRepository.allTasksFlow.value.firstOrNull { it.id == event.taskId } ?: return
    if (!task.canSendToDevice()) return
    val mode = if (event.mode == SendMode.TRANSFER_AND_CONTINUE && task.canTransferToDevice())
        SendMode.TRANSFER_AND_CONTINUE else SendMode.LINK_ONLY
    val transfer = mode == SendMode.TRANSFER_AND_CONTINUE
    // Finished: the file itself travels as one complete piece (any engine), and the other device
    // takes it as a finished direct download, with no network. Not finished: the scratch files
    // travel and the other device continues from them, like after a pause.
    val completedFile = if (transfer && task.status == DownloadStatus.COMPLETED)
        completedTransferFile(task) else ""
    val partialFiles = when {
        !transfer || task.status == DownloadStatus.COMPLETED -> emptyList()
        task.engineType == EngineType.HTTP_DIRECT -> gatherPartialFiles(task.id, task.fileName)
        else -> gatherScratchFiles(task.id)
    }
    val headers = task.requestHeaders
    val payload = SendPayload(
        url = task.url,
        fileName = task.fileName,
        engineType = (if (completedFile.isNotBlank()) EngineType.HTTP_DIRECT else task.engineType).name,
        formatId = task.formatId.orEmpty(),
        originalUrl = task.originalUrl.orEmpty(),
        cookie = headers["Cookie"] ?: headers["cookie"] ?: "",
        referer = headers["Referer"] ?: headers["referer"] ?: "",
        userAgent = headers["User-Agent"] ?: headers["user-agent"] ?: "",
        fromDeviceId = deviceSync.selfId,
        fromDeviceName = deviceSync.selfName,
        clipStart = task.clipStart.orEmpty(),
        clipEnd = task.clipEnd.orEmpty(),
        clipByCutting = task.clipByCutting
    )
    val request = SendRequest(
        deviceId = event.deviceId,
        payload = payload,
        mode = mode,
        deleteAfterSuccess = event.deleteAfterSuccess,
        partialFiles = partialFiles,
        completedFilePath = completedFile,
        sourceTaskId = task.id
    )
    val alreadyPaired = deviceSync.paired.value.any { it.id == event.deviceId }
    if (alreadyPaired) {
        scope.launch { sendWithRetry(request, tries = 1) }
    } else {
        // First time: pair (both users compare the 6-digit code), then send once confirmed.
        pendingDeviceSend = request
        deviceSync.startPairing(event.deviceId)
    }
}

/** A finished download's saved file, if it is really there. */
private fun DashboardViewModel.completedTransferFile(task: DownloadTask): String {
    if (task.engineType == EngineType.BITTORRENT || task.status != DownloadStatus.COMPLETED) return ""
    if (task.fileName.isBlank()) return ""
    val path = task.destinationPath.trimEnd('/', '\\') + "/" + task.fileName
    return if (FileSystem.SYSTEM.exists(path.toPath())) path else ""
}

/** Every file in a media task's scratch folder (own-engine pieces, HLS segments, yt-dlp .part). */
private fun DashboardViewModel.gatherScratchFiles(taskId: String): List<String> {
    val dir = "${queueRepository.pathResolver.getTempDirectory()}/task_$taskId".toPath()
    if (!FileSystem.SYSTEM.exists(dir)) return emptyList()
    return runCatching {
        FileSystem.SYSTEM.list(dir)
            .filter { FileSystem.SYSTEM.metadataOrNull(it)?.isRegularFile == true }
            .map { it.toString() }
    }.getOrDefault(emptyList())
}

/** The task's downloaded pieces: `<temp>/task_<id>/<name>.partN` + `<name>.chunks`. */
private fun DashboardViewModel.gatherPartialFiles(taskId: String, fileName: String): List<String> {
    val dir = "${queueRepository.pathResolver.getTempDirectory()}/task_$taskId".toPath()
    if (!FileSystem.SYSTEM.exists(dir)) return emptyList()
    return runCatching {
        FileSystem.SYSTEM.list(dir).filter { p ->
            val n = p.name
            n.startsWith("$fileName.part") || n == "$fileName.chunks"
        }.map { it.toString() }
    }.getOrDefault(emptyList())
}

private suspend fun DashboardViewModel.sendWithRetry(request: SendRequest, tries: Int) {
    var attempt = 0
    suspend fun attemptOnce() {
        deviceSync.send(request) { ok ->
            if (ok) {
                // Delete here only now that the transfer has fully succeeded (owner's requirement).
                if (request.deleteAfterSuccess && request.sourceTaskId.isNotBlank()) {
                    scope.launch {
                        queueRepository.removeTask(request.sourceTaskId, deleteFile = true)
                    }
                }
                _uiState.update { it.copy(deviceShareTaskId = null) }
                val toName = (deviceSync.sending.value as? SendState.Done)?.deviceName
                    ?: deviceSync.paired.value.firstOrNull { it.id == request.deviceId }?.name
                    ?: request.deviceId
                val message = LocalizationManager.Strings.sysDeviceSent.replace("{name}", toName)
                _uiEffect.tryEmit(DashboardUiEffect.ShowToast(message))
                SystemNotice.show("device-sent-${nowEpochMs()}", message, request.payload.fileName)
            } else if (attempt < tries - 1) {
                attempt++
                scope.launch { delay(1500); attemptOnce() }
            }
        }
    }
    attemptOnce()
}
