package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver

@Composable
fun DirectoryPickerDialog(
    initialPath: String,
    pathResolver: PathResolver,
    onDirectorySelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val defaultPath = remember {
        if (initialPath.isNotBlank()) initialPath else pathResolver.getDownloadsDirectory()
    }
    var currentPath by remember { mutableStateOf(defaultPath) }
    var subdirectories by remember { mutableStateOf(listOf<String>()) }
    var showHiddenFolders by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var createFolderError by remember { mutableStateOf<String?>(null) }

    val quickDirectories = remember { pathResolver.getAvailableDirectories() }
    val parentDir = remember(currentPath) { pathResolver.getParentDirectory(currentPath) }

    // Refresh subdirectories whenever currentPath changes
    fun refreshSubdirectories() {
        subdirectories = pathResolver.listSubdirectories(currentPath, showHiddenFolders)
    }

    LaunchedEffect(currentPath, showHiddenFolders) {
        refreshSubdirectories()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📁 Select Folder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            "✕",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Current Path Bar + Up Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (parentDir != null) {
                                currentPath = parentDir
                                isCreatingFolder = false
                            }
                        },
                        enabled = parentDir != null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⬆️ Up", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Presets Row
                if (quickDirectories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickDirectories.forEach { (label, path) ->
                            val isSelected = currentPath == path
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    currentPath = path
                                    isCreatingFolder = false
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Subdirectories Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Subfolders (${subdirectories.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilterChip(
                            selected = showHiddenFolders,
                            onClick = {
                                showHiddenFolders = !showHiddenFolders
                            },
                            label = {
                                Text(
                                    text = if (showHiddenFolders) "👁️ Hidden: On" else "👁️ Hidden: Off",
                                    fontSize = 10.sp
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.6f
                                ),
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    TextButton(
                        onClick = {
                            isCreatingFolder = !isCreatingFolder
                            newFolderName = ""
                            createFolderError = null
                        }
                    ) {
                        Text(
                            text = if (isCreatingFolder) "Cancel" else "➕ New Folder",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Inline New Folder Input
                if (isCreatingFolder) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = 0.35f
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newFolderName,
                                    onValueChange = {
                                        newFolderName = it
                                        createFolderError = null
                                    },
                                    placeholder = { Text("Folder name...", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Text("📁", fontSize = 14.sp)
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(
                                            alpha = 0.7f
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

                                Button(
                                    onClick = {
                                        if (newFolderName.isNotBlank()) {
                                            val createdPath = pathResolver.createSubdirectory(
                                                currentPath,
                                                newFolderName
                                            )
                                            if (createdPath != null) {
                                                currentPath = createdPath
                                                isCreatingFolder = false
                                                newFolderName = ""
                                                refreshSubdirectories()
                                            } else {
                                                createFolderError = "Failed to create folder"
                                            }
                                        }
                                    },
                                    enabled = newFolderName.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Create", fontSize = 12.sp)
                                }
                            }
                            if (createFolderError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = createFolderError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                // Subdirectories List
                if (subdirectories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No subfolders in this directory.\nFiles will be saved directly here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                    ) {
                        items(subdirectories) { folderName ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val separator =
                                            if (currentPath.endsWith("/") || currentPath.endsWith("\\")) "" else "/"
                                        currentPath = "$currentPath$separator$folderName"
                                        isCreatingFolder = false
                                    },
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val isHidden = folderName.startsWith(".")
                                        Text(if (isHidden) "👁️" else "📁", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isHidden) FontWeight.Normal else FontWeight.Medium,
                                            color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isHidden) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "(hidden)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "›",
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", maxLines = 1)
                    }

                    Button(
                        onClick = {
                            onDirectorySelected(currentPath)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Select Folder", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}