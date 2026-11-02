package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.getClipboardText
import kotlinx.coroutines.launch

@Composable
internal fun AddDialogHeader(onDismiss: () -> Unit) {
    // Header Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Add New Download",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Enter link to start downloading",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                "✕",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
internal fun UrlInputSection(
    inputUrl: String,
    urlField: MutableState<TextFieldValue>,
    onUrlChanged: (String) -> Unit
) {
    var urlTextFieldValue by urlField
    val coroutineScope = rememberCoroutineScope()
    // URL Input Field
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Link",
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (com.michelle.raouf.fetch_jet.core.filesystem.TextFilePicker.isSupported) {
            TextButton(
                onClick = {
                    com.michelle.raouf.fetch_jet.core.filesystem.TextFilePicker.pick { text ->
                        if (!text.isNullOrBlank()) {
                            urlTextFieldValue = TextFieldValue(text, TextRange(text.length))
                            onUrlChanged(text)
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "📄 Import .txt",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        // A .torrent on this device: the chooser fills the field with its path.
        if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.torrent &&
            com.michelle.raouf.fetch_jet.core.filesystem.TorrentFilePicker.isSupported
        ) {
            TextButton(
                onClick = {
                    com.michelle.raouf.fetch_jet.core.filesystem.TorrentFilePicker.pick { path ->
                        if (!path.isNullOrBlank()) {
                            urlTextFieldValue =
                                TextFieldValue(path, TextRange(path.length))
                            onUrlChanged(path)
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "📂 Open .torrent file",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))

    FetchJetTextField(
        value = urlTextFieldValue,
        onValueChange = { newValue ->
            urlTextFieldValue = newValue
            if (newValue.text != inputUrl) {
                onUrlChanged(newValue.text)
            }
        },
        placeholder = "Paste links or magnets",
        leading = { Text("🔗", fontSize = 16.sp) },
        trailing = {
            if (urlTextFieldValue.text.isNotBlank()) {
                FieldIconButton("✕", onClick = {
                    urlTextFieldValue = TextFieldValue("", TextRange.Zero)
                    onUrlChanged("")
                })
            } else {
                FieldChipButton("📋 Paste", onClick = {
                    coroutineScope.launch {
                        val clip = getClipboardText()?.trim()
                        if (!clip.isNullOrBlank()) {
                            urlTextFieldValue = TextFieldValue(clip, TextRange(clip.length))
                            onUrlChanged(clip)
                        }
                    }
                })
            }
        },
        singleLine = false,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun EngineSelector(detectedEngine: EngineType, onEngineSelected: (EngineType) -> Unit) {
    // Engine Selector
    Text(
        text = "Download Engine",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EngineChoiceChip(
            label = "⚡ Direct",
            isSelected = detectedEngine == EngineType.HTTP_DIRECT,
            onClick = { onEngineSelected(EngineType.HTTP_DIRECT) },
            modifier = Modifier.weight(1f)
        )
        if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.mediaExtractor) {
            EngineChoiceChip(
                label = "🎬 Media",
                isSelected = detectedEngine == EngineType.MEDIA_STREAM,
                onClick = { onEngineSelected(EngineType.MEDIA_STREAM) },
                modifier = Modifier.weight(1f)
            )
        }
        if (com.michelle.raouf.fetch_jet.core.edition.AppFeatures.torrent) {
            EngineChoiceChip(
                label = "🧲 Torrent",
                isSelected = detectedEngine == EngineType.BITTORRENT,
                onClick = { onEngineSelected(EngineType.BITTORRENT) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun FileNameSection(
    suggestedFileName: String,
    fileNameOverride: String,
    isInspectingMedia: Boolean,
    isInspectingTorrent: Boolean,
    onFileNameChanged: (String) -> Unit
) {
    // File name: what FetchJet will call the file, editable.
    if (suggestedFileName.isNotBlank() || fileNameOverride.isNotBlank()) {
        Text(
            text = "File name",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Locked while the link is still being inspected: the name follows the result.
        val inspecting = isInspectingMedia || isInspectingTorrent
        FetchJetTextField(
            value = if (inspecting && fileNameOverride.isEmpty()) "Finding the name…" else fileNameOverride.ifEmpty { suggestedFileName },
            onValueChange = { onFileNameChanged(it) },
            enabled = !inspecting,
            modifier = Modifier.fillMaxWidth(),
            leading = { Text(if (inspecting) "⏳" else "📝", fontSize = 16.sp) },
            trailing = if (fileNameOverride.isNotBlank() && fileNameOverride != suggestedFileName) {
                { FieldIconButton("↺", onClick = { onFileNameChanged("") }) }
            } else null
        )
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
internal fun DestinationSection(
    destinationPath: String,
    pathField: MutableState<TextFieldValue>,
    showDirectoryPickerState: MutableState<Boolean>,
    sortIntoFolders: Boolean,
    fileNameOverride: String,
    suggestedFileName: String,
    detectedEngine: EngineType,
    pathResolver: PathResolver,
    defaultDownloads: String,
    defaultDocs: String,
    defaultDesktop: String,
    onDestinationPathChanged: (String) -> Unit
) {
    var pathTextFieldValue by pathField
    var showDirectoryPicker by showDirectoryPickerState
    // Save Destination Section
    val typeFolder =
        if (sortIntoFolders) com.michelle.raouf.fetch_jet.core.settings.FileCategory.folderFor(
            fileNameOverride.ifEmpty { suggestedFileName },
            isMedia = detectedEngine == EngineType.MEDIA_STREAM,
            isTorrent = detectedEngine == EngineType.BITTORRENT
        ) else null
    // Sorted-by-type: the field shows the real target folder (e.g. ...\Downloads\Video) and stays editable.
    LaunchedEffect(typeFolder) {
        // The field shows the real target: <base>\<Type>. Any type folder already
        // on the path (ours, from before) is replaced, never stacked.
        val base =
            com.michelle.raouf.fetch_jet.core.settings.FileCategory.stripTypeFolders(
                destinationPath
            )
        val sep = if (base.contains('\\')) "\\" else "/"
        val full = if (typeFolder != null) base + sep + typeFolder else base
        if (full != destinationPath) {
            pathTextFieldValue = TextFieldValue(full, TextRange(full.length))
            onDestinationPathChanged(full)
        }
    }
    Text(
        text = "Save Destination Directory",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))

    FetchJetTextField(
        value = pathTextFieldValue,
        onValueChange = { newValue ->
            pathTextFieldValue = newValue
            if (newValue.text != destinationPath) {
                onDestinationPathChanged(newValue.text)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        leading = { Text("📁", fontSize = 16.sp) },
        trailing = { FieldIconButton("📂", onClick = { showDirectoryPicker = true }) }
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Quick Directory Buttons (Always fit cleanly on mobile!)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickFolderButton(
            icon = "📥",
            label = "Downloads",
            onClick = {
                pathTextFieldValue =
                    TextFieldValue(defaultDownloads, TextRange(defaultDownloads.length))
                onDestinationPathChanged(defaultDownloads)
            },
            modifier = Modifier.weight(1f)
        )
        QuickFolderButton(
            icon = "📄",
            label = "Documents",
            onClick = {
                pathTextFieldValue =
                    TextFieldValue(defaultDocs, TextRange(defaultDocs.length))
                onDestinationPathChanged(defaultDocs)
            },
            modifier = Modifier.weight(1f)
        )
        val isMobile = defaultDesktop.isBlank() || defaultDesktop.contains(
            "storage",
            ignoreCase = true
        ) || defaultDesktop.contains("Documents", ignoreCase = true)
        QuickFolderButton(
            icon = if (isMobile) "🎬" else "💻",
            label = if (isMobile) "Media" else "Desktop",
            onClick = {
                if (isMobile) {
                    val created =
                        pathResolver.createSubdirectory(defaultDownloads, "Media")
                    val path = created ?: "$defaultDownloads/Media"
                    pathTextFieldValue = TextFieldValue(path, TextRange(path.length))
                    onDestinationPathChanged(path)
                } else {
                    pathTextFieldValue =
                        TextFieldValue(defaultDesktop, TextRange(defaultDesktop.length))
                    onDestinationPathChanged(defaultDesktop)
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun AddDialogActions(
    errorMessage: String?,
    inspectError: String?,
    inspectMediaError: String?,
    hasActiveError: Boolean,
    isInspectionComplete: Boolean,
    isInspecting: Boolean,
    isUrlValid: Boolean,
    isClipRangeValid: Boolean,
    isLoading: Boolean,
    canStartDownload: Boolean,
    detectedEngine: EngineType,
    inspectedPlaylist: com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist?,
    scheduleStartMs: Long,
    linkCount: Int = 0,
    onInspectTorrent: () -> Unit,
    onInspectMedia: () -> Unit,
    onAddDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    // Inline Error Notice
    val activeError = errorMessage ?: inspectError ?: inspectMediaError
    if (activeError != null) {
        InlineErrorMessage(
            msg = activeError,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
    }

    // Action Buttons Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.width(10.dp))

        if (hasActiveError && !isInspectionComplete) {
            Button(
                onClick = {
                    if (detectedEngine == EngineType.BITTORRENT) onInspectTorrent() else onInspectMedia()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    "🔄 Retry Inspect",
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Button(
            onClick = onAddDownload,
            enabled = canStartDownload,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = when {
                    isLoading -> "Starting..."
                    isInspecting -> "Inspecting..."
                    hasActiveError -> "Inspection Failed"
                    !isUrlValid -> "Enter URL"
                    linkCount > 0 -> if (scheduleStartMs > 0L) "Schedule $linkCount links" else "Download $linkCount links"
                    !isInspectionComplete && detectedEngine == EngineType.MEDIA_STREAM -> "Inspect Video First"
                    !isInspectionComplete && detectedEngine == EngineType.BITTORRENT -> "Inspect Torrent First"
                    !isClipRangeValid -> "Invalid Time Range"
                    inspectedPlaylist != null && detectedEngine == EngineType.MEDIA_STREAM -> {
                        val n = inspectedPlaylist.entries.count { it.isSelected }
                        if (scheduleStartMs > 0L) "Schedule $n videos" else "Download $n videos"
                    }

                    scheduleStartMs > 0L -> "Schedule Download"
                    else -> "Start Download"
                },
                // On the cyan primary button the readable text colour is onPrimary
                // (black in the dark theme), not onSurface (white).
                color = if (canStartDownload) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.6f
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
internal fun InspectionLoadingBanner(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
internal fun InlineErrorMessage(msg: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚠️", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = msg,
            color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun QuickFolderButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun EngineChoiceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.5f
        )
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
            alpha = 0.6f
        )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = if (isSelected) 3.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                fontSize = 12.sp
            )
        }
    }
}
