package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import com.michelle.raouf.fetch_jet.utils.formatBytes

@Composable
internal fun TorrentInspectionSection(
    detectedEngine: EngineType,
    inputUrl: String,
    isInspectingTorrent: Boolean,
    inspectError: String?,
    inspectedTorrentName: String?,
    inspectedTorrentFiles: List<TorrentFileEntry>,
    onInspectTorrent: () -> Unit,
    onFileSelectionToggled: (Int, Boolean) -> Unit,
    onFilePriorityChanged: (Int, Int) -> Unit,
    onSelectAllFilesToggled: (Boolean) -> Unit
) {
    // Torrent File Inspection Section
    if (detectedEngine == EngineType.BITTORRENT) {
        Spacer(modifier = Modifier.height(12.dp))
        if (isInspectingTorrent) {
            InspectionLoadingBanner(
                title = "Inspecting Torrent Metadata...",
                subtitle = "Fetching file manifest and peers"
            )
        } else if (inspectError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                        alpha = 0.25f
                    )
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Torrent Inspection Failed",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = inspectError,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onInspectTorrent,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            "🔄 Retry Inspection",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        } else if (inspectedTorrentFiles.isEmpty()) {
            OutlinedButton(
                onClick = onInspectTorrent,
                enabled = inputUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF38BDF8)
                )
            ) {
                Text(
                    "🧲 Inspect Torrent Files",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            TorrentFilesSelectionCard(
                torrentName = inspectedTorrentName,
                files = inspectedTorrentFiles,
                onFileSelectionToggled = onFileSelectionToggled,
                onFilePriorityChanged = onFilePriorityChanged,
                onSelectAll = onSelectAllFilesToggled
            )
        }
    }
}

@Composable
internal fun TorrentLeechToggle(
    detectedEngine: EngineType,
    disableTorrentUpload: Boolean,
    onDisableTorrentUploadToggled: (Boolean) -> Unit
) {
    // Torrent Leech Mode Toggle
    if (detectedEngine == EngineType.BITTORRENT) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = disableTorrentUpload,
                onCheckedChange = onDisableTorrentUploadToggled,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF0284C7),
                    uncheckedColor = Color(0xFF64748B)
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Disable Torrent Upload (Leech-Only Mode)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun TorrentFilesSelectionCard(
    torrentName: String?,
    files: List<TorrentFileEntry>,
    onFileSelectionToggled: (Int, Boolean) -> Unit,
    onFilePriorityChanged: (Int, Int) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.35f
            )
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = torrentName ?: "Torrent Files",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = {
                        val allSelected = files.all { it.isSelected }
                        onSelectAll(!allSelected)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (files.all { it.isSelected }) "Deselect All" else "Select All",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(files, key = { it.index }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (file.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.4f
                                    ) else Color.Transparent
                                )
                                .clickable { onFileSelectionToggled(file.index, !file.isSelected) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = file.isSelected,
                                onCheckedChange = { checked ->
                                    onFileSelectionToggled(
                                        file.index,
                                        checked
                                    )
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF0284C7),
                                    uncheckedColor = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.path,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatBytes(file.size),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            val priorityLabel = when (file.priority) {
                                1 -> "High"
                                -1 -> "Low"
                                else -> "Normal"
                            }
                            val priorityColor = when (file.priority) {
                                1 -> Color(0xFFEF4444)
                                -1 -> Color(0xFF3B82F6)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                onClick = {
                                    val nextPriority = when (file.priority) {
                                        0 -> 1
                                        1 -> -1
                                        else -> 0
                                    }
                                    onFilePriorityChanged(file.index, nextPriority)
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = priorityColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = priorityLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = priorityColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
