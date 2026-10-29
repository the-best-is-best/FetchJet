package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.getPlatform
import com.michelle.raouf.fetch_jet.theme.StatusCompleted
import com.michelle.raouf.fetch_jet.theme.StatusPaused

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FdmSettingsPanel(
    destinationPath: String,
    disableTorrentUpload: Boolean,
    isDarkMode: Boolean,
    globalDownloadSpeedLimitBytes: Long,
    globalUploadSpeedLimitBytes: Long,
    speedDisplayUnit: String = "MB/s",
    ytDlpStatus: String,
    isYtDlpInstalled: Boolean,
    isFfmpegInstalled: Boolean,
    toolsDirectoryPath: String,
    torrentEngineStatus: String = "",
    isTorrentEngineReady: Boolean = true,
    isDownloadingYtDlp: Boolean,
    pathResolver: PathResolver,
    onDestinationPathChanged: (String) -> Unit,
    onDisableTorrentUploadToggled: (Boolean) -> Unit,
    onThemeToggled: () -> Unit,
    onGlobalDownloadSpeedLimitChanged: (Long) -> Unit,
    onGlobalUploadSpeedLimitChanged: (Long) -> Unit,
    onSpeedDisplayUnitChanged: (String) -> Unit = {},
    onDownloadYtDlpClicked: () -> Unit,
    onApplySettings: () -> Unit
) {
    val defaultDownloads = remember { pathResolver.getDownloadsDirectory() }
    val scrollState = rememberScrollState()
    var showSavedMessage by remember { mutableStateOf(false) }
    var speedSavedFeedback by remember { mutableStateOf(false) }
    var showDirectoryPicker by remember { mutableStateOf(false) }

    if (showDirectoryPicker) {
        DirectoryPickerDialog(
            initialPath = destinationPath,
            pathResolver = pathResolver,
            onDirectorySelected = { selected ->
                onDestinationPathChanged(selected)
                showDirectoryPicker = false
            },
            onDismissRequest = {
                showDirectoryPicker = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Page Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ Settings & Preferences",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "💾 Auto-Saved",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "Configure storage paths, speed limits, media tools, and network rules",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Section 1: Storage & Default Download Directory
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📁 Default Download Directory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Files and media extractions will be saved to this folder by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = destinationPath,
                        onValueChange = onDestinationPathChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = {
                            Text("📁", fontSize = 16.sp)
                        },
                        trailingIcon = {
                            if (destinationPath != defaultDownloads && destinationPath.isNotBlank()) {
                                IconButton(onClick = { onDestinationPathChanged(defaultDownloads) }) {
                                    Text(
                                        "↺",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                "Destination directory path...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = 0.45f
                            ),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = 0.25f
                            ),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.6f
                            ),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDirectoryPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                "📁 Browse",
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { onDestinationPathChanged(defaultDownloads) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            )
                        ) {
                            Text(
                                "↺ Reset",
                                maxLines = 1,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Section 2: Bandwidth & Speed Limits
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🌐 Global Traffic & Speed Limits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Control download and upload bandwidth limits applied directly to active downloads in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speed Display Unit Selector
                    Text(
                        text = "Speed Display Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose how download and upload speeds are formatted across the entire application.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val unitOptions = listOf(
                            "MB/s" to "MB/s",
                            "KB/s" to "KB/s",
                            "Mbps" to "Mbps",
                            "Kbps" to "Kbps"
                        )
                        val activeKey = when (speedDisplayUnit) {
                            "BYTES_PER_SEC" -> "MB/s"
                            "BITS_PER_SEC" -> "Mbps"
                            "BOTH" -> "MB/s"
                            else -> speedDisplayUnit
                        }
                        unitOptions.forEach { (key, label) ->
                            FilterChip(
                                selected = activeKey == key,
                                onClick = {
                                    onSpeedDisplayUnitChanged(key)
                                    speedSavedFeedback = true
                                },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    TrafficLimitSelector(
                        title = "Download Speed Limit",
                        speedBytesPerSec = globalDownloadSpeedLimitBytes,
                        onSpeedChanged = {
                            onGlobalDownloadSpeedLimitChanged(it)
                            speedSavedFeedback = true
                        }
                    )
                    if (getPlatform().name.startsWith("iOS", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "iOS: unlimited HTTP downloads run on the system background downloader " +
                                    "(they keep going when the app is closed) and cannot be throttled afterwards - " +
                                    "a limit applies to downloads started after it is set. Torrents follow limit " +
                                    "changes immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    TrafficLimitSelector(
                        title = "Upload Speed Limit",
                        speedBytesPerSec = globalUploadSpeedLimitBytes,
                        onSpeedChanged = {
                            onGlobalUploadSpeedLimitChanged(it)
                            speedSavedFeedback = true
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Single, clean Apply button for the speed limits section
                    Button(
                        onClick = {
                            onApplySettings()
                            speedSavedFeedback = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("⚡ Apply Speed Limits", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (speedSavedFeedback) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = StatusCompleted.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓ Speed limits applied immediately and divided equally across running downloads!",
                                    color = StatusCompleted,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Media Extractor Tools (yt-dlp & FFmpeg)
            // Hidden on mobile (Android/iOS) and only visible on JVM (Desktop)
            if (getPlatform().isJvm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "🎬 Media Extractor Tools (yt-dlp & FFmpeg)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "yt-dlp extracts media from YouTube & video sites; FFmpeg merges separate DASH video & audio streams (1080p, 4K).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Individual Tools Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // yt-dlp Badge Card
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "yt-dlp Engine",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isYtDlpInstalled) "✅ Installed & Ready" else "❌ Not Found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isYtDlpInstalled) StatusCompleted else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // FFmpeg Badge Card
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "FFmpeg Merger",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isFfmpegInstalled) "✅ Installed & Ready" else "❌ Not Found (720p limit)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFfmpegInstalled) StatusCompleted else StatusPaused
                                    )
                                }
                            }
                        }

                        // BitTorrent engine (libtorrent) badge
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "BitTorrent Engine (libtorrent)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isTorrentEngineReady) "✅ Installed & Ready" else "❌ Not Ready",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTorrentEngineReady) StatusCompleted else MaterialTheme.colorScheme.error
                                )
                                if (torrentEngineStatus.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = torrentEngineStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (toolsDirectoryPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tools Location: $toolsDirectoryPath",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isYtDlpInstalled && isFfmpegInstalled && isTorrentEngineReady) {
                                OutlinedButton(
                                    onClick = onDownloadYtDlpClicked,
                                    enabled = !isDownloadingYtDlp,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isDownloadingYtDlp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Updating...")
                                    } else {
                                        Text("🔄 Check for Updates / Reinstall")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onDownloadYtDlpClicked,
                                    enabled = !isDownloadingYtDlp,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isDownloadingYtDlp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Downloading Tools...")
                                    } else {
                                        Text("⬇️ Download Required Tools (yt-dlp & FFmpeg)")
                                    }
                                }
                            }

                            if (toolsDirectoryPath.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        pathResolver.openDirectoryInExplorer(
                                            toolsDirectoryPath
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("📂 Open Tools Folder")
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: BitTorrent Preferences
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🧲 BitTorrent Network Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BitTorrent Leech-Only Mode (Disable Uploading)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Disable peer uploading during BitTorrent downloads to save internet quota and upload bandwidth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = disableTorrentUpload,
                            onCheckedChange = onDisableTorrentUploadToggled
                        )
                    }
                }
            }

            // Section 5: Appearance
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🎨 Appearance & Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dark Theme Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Toggle between Dark Slate theme and Clean Light interface.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { onThemeToggled() }
                        )
                    }
                }
            }

            // Queue: how many downloads run at the same time
            QueueSettingsCard()

            // Startup (desktop): start with the system, hidden in the tray
            StartupSettingsCard()

            // Updates: automatic check, auto download (Windows), check now
            UpdatesSettingsCard()

            // Section 6: Apply / Confirmation Button
            Button(
                onClick = {
                    onApplySettings()
                    showSavedMessage = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("💾 Save & Apply All Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (showSavedMessage) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = StatusCompleted.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✅ All settings saved and applied to all current and future downloads!",
                            color = StatusCompleted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}