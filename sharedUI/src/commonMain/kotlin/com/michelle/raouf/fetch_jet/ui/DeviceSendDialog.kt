package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.EngineType
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
    val transferable = task?.engineType == EngineType.HTTP_DIRECT
    val devices = state.discoveredDevices

    var selectedId by remember(taskId) { mutableStateOf<String?>(null) }
    var mode by remember(taskId) { mutableStateOf(SendMode.LINK_ONLY) }
    var deleteAfter by remember(taskId) { mutableStateOf(false) }

    val sending = state.deviceSending
    val isBusy = sending is SendState.Sending

    AlertDialog(
        onDismissRequest = { if (!isBusy) onEvent(DashboardUiEvent.OnDismissShareSheet) },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = LocalizationManager.Strings.sysDeviceSearching,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    devices.forEach { device ->
                        val selected = device.id == selectedId
                        Surface(
                            onClick = { selectedId = device.id },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = device.platform +
                                                if (device.paired) "  ·  " + LocalizationManager.Strings.sysDevicePairedLabel else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = selected,
                                    onClick = { selectedId = device.id }
                                )
                            }
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

                    if (transferable) {
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
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = LocalizationManager.Strings.sysDeviceDeleteAfter,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Divider()
                var manualIp by remember(taskId) { mutableStateOf("") }
                Text(
                    text = LocalizationManager.Strings.sysDeviceManualConnect,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

                when (sending) {
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
                        text = LocalizationManager.Strings.sysDeviceSendFailed + ": " + sending.reason,
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
                enabled = !isBusy,
                onClick = { onEvent(DashboardUiEvent.OnDismissShareSheet) }
            ) {
                Text(LocalizationManager.Strings.cancel)
            }
        }
    )
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

/** Shown on both devices during first-time pairing: compare the 6-digit code, then confirm. */
@Composable
fun DevicePairingDialog(
    pairing: PairingState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
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
                        text = LocalizationManager.Strings.sysDevicePairFailed + ": " + pairing.reason,
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
            OutlinedButton(onClick = onCancel) {
                Text(LocalizationManager.Strings.cancel)
            }
        }
    )
}
