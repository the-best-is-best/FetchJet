package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.canTransferToDevice
import com.michelle.raouf.fetch_jet.feature.device_sync.PairingState
import com.michelle.raouf.fetch_jet.feature.device_sync.SendMode
import com.michelle.raouf.fetch_jet.feature.device_sync.SendState
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.tbib.kmp_localization.LocalizationManager

/**
 * "Send to device" sheet (ROADMAP-1.3.0 item 4). Lists the FetchJet devices found on the LAN,
 * lets the user pick a send mode (link only, or transfer the fetched bytes so the other device
 * continues), optionally delete the local copy once sent, and starts the send. First contact with
 * a device opens [DevicePairingDialog] so both users compare a code.
 */
@Composable
fun DeviceSendSheet(
    state: DashboardUiState,
    onEvent: (DashboardUiEvent) -> Unit
) {
    val taskId = state.deviceShareTaskId ?: return
    val task = state.allTasks.firstOrNull { it.id == taskId }
    val canTransfer = task?.canTransferToDevice() == true
    val devices = state.discoveredDevices

    var selectedId by remember(taskId) { mutableStateOf<String?>(null) }
    var mode by remember(taskId, canTransfer) {
        mutableStateOf(if (canTransfer) SendMode.TRANSFER_AND_CONTINUE else SendMode.LINK_ONLY)
    }
    // Pick the first device (paired first) so Send is ready; keep the user's choice while it's there.
    LaunchedEffect(devices) {
        if (devices.none { it.id == selectedId }) {
            selectedId = devices.sortedWith(compareBy({ !it.paired }, { it.name.lowercase() }))
                .firstOrNull()?.id
        }
    }
    var deleteAfter by remember(taskId) { mutableStateOf(false) }

    val sending = state.deviceSending
    val isBusy = sending is SendState.Sending || sending is SendState.Waiting
    // While only waiting for the other side, the user can still back out; not mid-stream.
    val streaming = sending is SendState.Sending

    AlertDialog(
        onDismissRequest = { if (!streaming) onEvent(DashboardUiEvent.OnDismissShareSheet) },
        title = {
            Text(
                text = LocalizationManager.Strings.sysDeviceSendTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                task?.fileName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                        Text(
                            text = LocalizationManager.Strings.sysDeviceSearching,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    var query by remember(taskId) { mutableStateOf("") }
                    if (devices.size > 5) {
                        FetchJetTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = LocalizationManager.Strings.sysDeviceSearch,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val shown = devices
                        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                        .sortedWith(compareBy({ !it.paired }, { it.name.lowercase() }))
                    // A capped, scrolling list: with many devices the send options and the Send
                    // button below must stay on screen.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 236.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (shown.isEmpty()) {
                            Text(
                                text = LocalizationManager.Strings.sysDeviceNoMatch,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        var lastGroup: Boolean? = null
                        shown.forEach { device ->
                            if (device.paired == lastGroup) {
                                Divider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(start = 52.dp)
                                )
                            }
                            if (device.paired != lastGroup) {
                                lastGroup = device.paired
                                Text(
                                    text = if (device.paired) LocalizationManager.Strings.sysDevicePairedLabel
                                    else LocalizationManager.Strings.sysDeviceOthers,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
                                )
                            }
                            DeviceRow(
                                device = device,
                                selected = device.id == selectedId,
                                onSelect = { selectedId = device.id }
                            )
                        }
                    }

                    Divider()

                    // Send mode
                    ModeRow(
                        title = LocalizationManager.Strings.sysDeviceModeLink,
                        desc = LocalizationManager.Strings.sysDeviceModeLinkDesc,
                        selected = mode == SendMode.LINK_ONLY,
                        enabled = true
                    ) { mode = SendMode.LINK_ONLY }

                    if (canTransfer) {
                        ModeRow(
                            title = LocalizationManager.Strings.sysDeviceModeTransfer,
                            desc = LocalizationManager.Strings.sysDeviceModeTransferDesc,
                            selected = mode == SendMode.TRANSFER_AND_CONTINUE,
                            enabled = true
                        ) { mode = SendMode.TRANSFER_AND_CONTINUE }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteAfter = !deleteAfter },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = deleteAfter, onCheckedChange = { deleteAfter = it })
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = LocalizationManager.Strings.sysDeviceDeleteAfter,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Divider()
                var showManualIp by remember(taskId) { mutableStateOf(false) }
                var manualIp by remember(taskId) { mutableStateOf("") }
                TextButton(
                    onClick = { showManualIp = !showManualIp },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = (if (showManualIp) "▾  " else "▸  ") +
                                LocalizationManager.Strings.sysDeviceManualConnect,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (showManualIp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FetchJetTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            placeholder = "192.168.1.5",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            enabled = manualIp.isNotBlank() && !isBusy,
                            onClick = { onEvent(DashboardUiEvent.OnManualConnectClicked(manualIp.trim())) }
                        ) {
                            Text(LocalizationManager.Strings.sysDeviceConnect)
                        }
                    }
                }

                when (sending) {
                    is SendState.Waiting -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = LocalizationManager.Strings.sysDeviceWaitingAccept,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is SendState.Sending -> {
                        Spacer(Modifier.width(4.dp))
                        LinearProgressIndicator(
                            progress = { sending.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = LocalizationManager.Strings.sysDeviceSending,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    is SendState.Failed -> Text(
                        text = LocalizationManager.Strings.sysDeviceSendFailed + ": " + deviceSyncReason(sending.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    is SendState.Done -> Text(
                        text = LocalizationManager.Strings.sysDeviceSent.replace(
                            "{name}",
                            sending.deviceName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    null -> {}
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedId != null && !isBusy,
                onClick = {
                    val id = selectedId ?: return@Button
                    onEvent(
                        DashboardUiEvent.OnSendToDeviceClicked(taskId, id, mode, deleteAfter)
                    )
                }
            ) {
                Text(LocalizationManager.Strings.sysDeviceSend)
            }
        },
        dismissButton = {
            OutlinedButton(
                enabled = !streaming,
                onClick = { onEvent(DashboardUiEvent.OnDismissShareSheet) }
            ) {
                Text(LocalizationManager.Strings.cancel)
            }
        }
    )
}

/** One compact device line: icon, name · platform, Paired badge, radio. */
@Composable
private fun DeviceRow(
    device: com.michelle.raouf.fetch_jet.feature.device_sync.DiscoveredDevice,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val host = device.name.substringBefore(" · ")
    val plat = device.name.substringAfter(" · ", "").ifBlank { device.platform }
    val showPlat = plat.isNotBlank() && !plat.equals(host, ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.22f else 0.12f),
                    RoundedCornerShape(9.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = deviceEmoji(device.name + " " + device.platform), fontSize = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (showPlat) {
                Text(
                    text = " · $plat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (device.paired) {
            Spacer(Modifier.width(6.dp))
            PairedChip()
        }
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Small rounded "Paired" badge shown next to a device that has been paired before. */
@Composable
private fun PairedChip() {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = LocalizationManager.Strings.sysDevicePairedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun deviceEmoji(label: String): String {
    val l = label.lowercase()
    return when {
        "android" in l || "iphone" in l || "ipad" in l || "ios" in l -> "📱"
        else -> "💻"
    }
}

@Composable
private fun ModeRow(
    title: String,
    desc: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A refusal code from the other device ("blocked", "busy", "wait:<seconds>") in words. */
internal fun deviceSyncReason(reason: String): String = when {
    reason == "blocked" -> LocalizationManager.Strings.pairRefusedBlocked
    reason == "busy" -> LocalizationManager.Strings.pairRefusedBusy
    reason == "declined" -> LocalizationManager.Strings.pairDeclined
    reason == "expired" -> LocalizationManager.Strings.pairExpired
    reason.startsWith("wait:") ->
        LocalizationManager.Strings.pairRefusedWait.replace("{seconds}", reason.removePrefix("wait:"))

    else -> reason
}

/** Shown on both devices during first-time pairing: compare the 6-digit code, then confirm. */
@Composable
fun DevicePairingDialog(
    pairing: PairingState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onBlock: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = LocalizationManager.Strings.sysDevicePairTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (pairing) {
                    is PairingState.Connecting -> {
                        CircularProgressIndicator()
                        Text(
                            text = LocalizationManager.Strings.sysDevicePairConnecting,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is PairingState.Confirm -> {
                        Text(
                            text = LocalizationManager.Strings.sysDevicePairBody,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = pairing.code,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = pairing.peerName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is PairingState.Failed -> Text(
                        text = LocalizationManager.Strings.sysDevicePairFailed + ": " + deviceSyncReason(pairing.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (pairing is PairingState.Confirm) {
                Button(onClick = onConfirm) {
                    Text(LocalizationManager.Strings.sysDevicePairConfirm)
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pairing is PairingState.Confirm) {
                    TextButton(onClick = onBlock) {
                        Text(
                            LocalizationManager.Strings.blockDevice,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                OutlinedButton(onClick = onCancel) {
                    Text(LocalizationManager.Strings.cancel)
                }
            }
        }
    )
}
