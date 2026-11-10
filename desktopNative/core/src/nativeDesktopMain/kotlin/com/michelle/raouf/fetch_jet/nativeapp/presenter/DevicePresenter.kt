package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.domain.model.canTransferToDevice
import com.michelle.raouf.fetch_jet.feature.device_sync.DiscoveredDevice
import com.michelle.raouf.fetch_jet.feature.device_sync.PairingState
import com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState
import com.michelle.raouf.fetch_jet.feature.device_sync.SendMode
import com.michelle.raouf.fetch_jet.feature.device_sync.SendState
import com.michelle.raouf.fetch_jet.nativeapp.dto.DevicePairingDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.DeviceRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.DeviceSendCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.DeviceSheetDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.PairingCode
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DeviceLocal(
    val taskId: String? = null,
    val selectedId: String? = null,
    val canTransfer: Boolean = false,
    val transfer: Boolean = false,
    val deleteAfter: Boolean = false,
    val query: String = "",
    val manualShown: Boolean = false,
    val manualIp: String = "",
)

internal data class DeviceDebug(
    val devices: List<DiscoveredDevice> = emptyList(),
    val sending: SendState? = null,
    val pairing: PairingState? = null,
    val receiving: ReceiveState? = null,
)

private data class DeviceSlice(
    val taskId: String?,
    val fileName: String,
    val fileSize: String,
    val canTransfer: Boolean,
    val devices: List<DiscoveredDevice>,
    val sending: SendState?,
    val pairing: PairingState?,
    val receiving: ReceiveState?,
)

private fun DashboardUiState.deviceSlice(): DeviceSlice {
    val task = deviceShareTaskId?.let { id -> allTasks.firstOrNull { it.id == id } }
    return DeviceSlice(
        taskId = deviceShareTaskId,
        fileName = task?.fileName.orEmpty(),
        fileSize = task?.let { sizeText(it) }.orEmpty(),
        canTransfer = task?.canTransferToDevice() == true,
        devices = discoveredDevices,
        sending = deviceSending,
        pairing = devicePairing,
        receiving = deviceReceiving,
    )
}

private fun sortedDevices(devices: List<DiscoveredDevice>): List<DiscoveredDevice> =
    devices.sortedWith(compareBy({ !it.paired }, { it.name.lowercase() }))

internal fun deviceSyncReason(reason: String): String {
    val s = LocalizationManager.Strings
    return when {
        reason == "blocked" -> s.pairRefusedBlocked
        reason == "busy" -> s.pairRefusedBusy
        reason == "declined" -> s.pairDeclined
        reason == "expired" -> s.pairExpired
        reason == "no-answer" -> s.deviceNoAnswer
        reason.startsWith("wait:") -> s.pairRefusedWait.replace("{seconds}", reason.removePrefix("wait:"))
        else -> reason
    }
}

private fun deviceEmoji(label: String): String {
    val l = label.lowercase()
    return if ("android" in l || "iphone" in l || "ipad" in l || "ios" in l) "📱" else "💻"
}

private fun deviceHost(device: DiscoveredDevice): String = device.name.substringBefore(" · ")

private fun devicePlatformSuffix(device: DiscoveredDevice): String {
    val host = deviceHost(device)
    val platform = device.name.substringAfter(" · ", "").ifBlank { device.platform }
    return if (platform.isNotBlank() && !platform.equals(host, ignoreCase = true)) " · $platform" else ""
}

private fun Presenter.settleDeviceLocal(s: DeviceSlice) {
    deviceLocal.update { ui ->
        var next = if (ui.taskId != s.taskId) DeviceLocal(taskId = s.taskId, canTransfer = s.canTransfer, transfer = s.canTransfer) else ui
        if (next.canTransfer != s.canTransfer) next = next.copy(canTransfer = s.canTransfer, transfer = s.canTransfer)
        if (s.taskId != null && s.devices.none { it.id == next.selectedId }) {
            next = next.copy(selectedId = sortedDevices(s.devices).firstOrNull()?.id)
        }
        next
    }
}

private fun Presenter.publishDevices(s: DeviceSlice, ui: DeviceLocal) {
    val strings = LocalizationManager.Strings
    val receiving = s.receiving
    devicePairing.publish(
        when (val pairing = s.pairing) {
            null -> when (receiving) {
                is ReceiveState.Receiving -> DevicePairingDto(
                    state = PairingCode.RECEIVING.ordinal,
                    text = strings.sysDeviceOfferFrom.replace("{device}", receiving.deviceName),
                    peer = receiving.deviceName,
                    fileName = receiving.fileName,
                    progress = receiving.fraction.coerceIn(0f, 1f),
                    indeterminate = receiving.fraction < 0f,
                    percent = if (receiving.fraction < 0f) "" else "${(receiving.fraction.coerceIn(0f, 1f) * 100).toInt()}%",
                )

                is ReceiveState.Failed -> DevicePairingDto(
                    state = PairingCode.RECEIVE_FAILED.ordinal,
                    text = strings.sysDeviceReceiveFailed + ": " + deviceSyncReason(receiving.reason),
                    peer = receiving.deviceName,
                    fileName = receiving.fileName,
                )

                else -> DevicePairingDto()
            }
            is PairingState.Connecting -> DevicePairingDto(state = PairingCode.CONNECTING.ordinal, text = strings.sysDevicePairConnecting)
            is PairingState.Confirm -> DevicePairingDto(
                state = PairingCode.CONFIRM.ordinal,
                text = strings.sysDevicePairBody,
                code = pairing.code,
                peer = pairing.peerName,
            )

            is PairingState.Failed -> DevicePairingDto(
                state = PairingCode.FAILED.ordinal,
                text = strings.sysDevicePairFailed + ": " + deviceSyncReason(pairing.reason),
            )

            is PairingState.Offer -> DevicePairingDto(
                state = PairingCode.OFFER.ordinal,
                text = strings.sysDeviceOfferFrom.replace("{device}", pairing.peerName),
                peer = pairing.peerName,
                fileName = pairing.fileName,
                fileSize = if (pairing.size > 0) formatBytes(pairing.size) else "",
            )
        }
    )
    if (s.taskId == null || ui.taskId != s.taskId) {
        deviceSheet.publish(DeviceSheetDto())
        deviceRows.publish(emptyList())
        return
    }
    val shown = sortedDevices(s.devices.filter { ui.query.isBlank() || it.name.contains(ui.query.trim(), ignoreCase = true) })
    var lastGroup: Boolean? = null
    deviceRows.publish(shown.map { device ->
        val host = deviceHost(device)
        val sameGroup = device.paired == lastGroup
        lastGroup = device.paired
        DeviceRowDto(
            id = device.id,
            name = host,
            platform = devicePlatformSuffix(device),
            emoji = deviceEmoji(device.name + " " + device.platform),
            paired = device.paired,
            selected = device.id == ui.selectedId,
            header = if (sameGroup) "" else if (device.paired) strings.sysDevicePairedLabel else strings.sysDeviceOthers,
            divider = sameGroup,
        )
    })
    val sending = s.sending
    val busy = sending is SendState.Sending || sending is SendState.Waiting
    val streaming = sending is SendState.Sending
    val target = s.devices.firstOrNull { it.id == ui.selectedId }
    deviceSheet.publish(
        DeviceSheetDto(
            open = true,
            fileName = s.fileName,
            fileSize = s.fileSize,
            deviceName = target?.let { deviceHost(it) + devicePlatformSuffix(it) }.orEmpty(),
            deviceEmoji = target?.let { deviceEmoji(it.name + " " + it.platform) }.orEmpty(),
            searching = s.devices.isEmpty(),
            searchShown = s.devices.size > 5,
            search = ui.query,
            searchRev = deviceEcho.of("query").rev(ui.query),
            noMatch = s.devices.isNotEmpty() && shown.isEmpty(),
            canTransfer = s.canTransfer,
            transfer = ui.transfer,
            deleteAfter = ui.deleteAfter,
            manualShown = ui.manualShown,
            manualIp = ui.manualIp,
            manualRev = deviceEcho.of("manual").rev(ui.manualIp),
            connectEnabled = ui.manualIp.isNotBlank() && !busy,
            state = when (sending) {
                null -> DeviceSendCode.NONE.ordinal
                is SendState.Waiting -> DeviceSendCode.WAITING.ordinal
                is SendState.Sending -> DeviceSendCode.SENDING.ordinal
                is SendState.Failed -> DeviceSendCode.FAILED.ordinal
                is SendState.Done -> DeviceSendCode.DONE.ordinal
            },
            stateText = when (sending) {
                null -> ""
                is SendState.Waiting -> strings.sysDeviceWaitingAccept
                is SendState.Sending -> strings.sysDeviceSending
                is SendState.Failed -> strings.sysDeviceSendFailed + ": " + deviceSyncReason(sending.reason)
                is SendState.Done -> strings.sysDeviceSent.replace("{name}", sending.deviceName)
            },
            progress = (sending as? SendState.Sending)?.fraction?.coerceIn(0f, 1f) ?: 0f,
            sendEnabled = ui.selectedId != null && !busy,
            cancelEnabled = !streaming,
            locked = streaming,
        )
    )
}

internal fun Presenter.startDevices() {
    val slices = combine(vm.uiState.map { it.deviceSlice() }.distinctUntilChanged(), debugDevices) { slice, fake ->
        if (fake == null) slice else slice.copy(devices = fake.devices, sending = fake.sending, pairing = fake.pairing, receiving = fake.receiving)
    }.distinctUntilChanged()
    scope.launch {
        slices.collect { settleDeviceLocal(it) }
    }
    scope.launch {
        combine(slices, deviceLocal, AppLanguage.current) { slice, ui, _ -> slice to ui }
            .collect { (slice, ui) -> publishDevices(slice, ui) }
    }
}

private fun Presenter.deviceSendingNow(): SendState? {
    val fake = debugDevices.value
    return if (fake != null) fake.sending else vm.uiState.value.deviceSending
}

private fun Presenter.deviceStreaming(): Boolean = deviceSendingNow() is SendState.Sending

internal fun Presenter.deviceSelect(id: String) {
    deviceLocal.update { it.copy(selectedId = id) }
}

internal fun Presenter.deviceSetMode(transfer: Boolean) {
    deviceLocal.update { it.copy(transfer = transfer && it.canTransfer) }
}

internal fun Presenter.deviceSetDeleteAfter(on: Boolean) {
    deviceLocal.update { it.copy(deleteAfter = on) }
}

internal fun Presenter.deviceSearch(text: String) {
    deviceEcho.of("query").fromUi(text)
    deviceLocal.update { it.copy(query = text) }
}

internal fun Presenter.deviceToggleManual() {
    deviceLocal.update { it.copy(manualShown = !it.manualShown) }
}

internal fun Presenter.deviceManualIp(text: String) {
    deviceEcho.of("manual").fromUi(text)
    deviceLocal.update { it.copy(manualIp = text) }
}

internal fun Presenter.deviceManualConnect() {
    val ui = deviceLocal.value
    val sending = deviceSendingNow()
    if (ui.manualIp.isBlank() || sending is SendState.Sending || sending is SendState.Waiting) return
    vm.onEvent(DashboardUiEvent.OnManualConnectClicked(ui.manualIp.trim()))
}

internal fun Presenter.deviceSend() {
    val ui = deviceLocal.value
    val taskId = vm.uiState.value.deviceShareTaskId ?: return
    val target = ui.selectedId ?: return
    val sending = deviceSendingNow()
    if (sending is SendState.Sending || sending is SendState.Waiting) return
    vm.onEvent(
        DashboardUiEvent.OnSendToDeviceClicked(
            taskId,
            target,
            if (ui.transfer) SendMode.TRANSFER_AND_CONTINUE else SendMode.LINK_ONLY,
            ui.deleteAfter,
        )
    )
}

internal fun Presenter.pairingCancel() {
    val fake = debugDevices.value
    val pairing = if (fake != null) fake.pairing else vm.uiState.value.devicePairing
    when {
        pairing != null -> vm.onEvent(DashboardUiEvent.OnCancelPairingClicked)
        fake != null -> debugDevices.value = fake.copy(receiving = null)
        else -> vm.onEvent(DashboardUiEvent.OnDismissReceiveClicked)
    }
}

internal fun Presenter.deviceDismiss() {
    if (deviceStreaming()) return
    vm.onEvent(DashboardUiEvent.OnDismissShareSheet)
}
