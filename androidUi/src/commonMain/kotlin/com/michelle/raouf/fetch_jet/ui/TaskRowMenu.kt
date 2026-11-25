package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.AppLocalization
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenu
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuEntry
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuOptions
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuSection
import com.michelle.raouf.fetch_jet.feature.row_menu.section
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun TaskRowMenu(
    task: DownloadTask,
    expanded: Boolean,
    viewModel: DashboardViewModel,
    options: RowMenuOptions,
    extraLabel: String? = null,
    onExtra: () -> Unit = {},
    onDismiss: () -> Unit,
    onPicked: () -> Unit,
    onOpen: () -> Unit,
    onOpenFolder: () -> Unit,
    onSelect: () -> Unit,
    onDetails: () -> Unit
) {
    val strings = LocalizationManager.Strings
    val state by viewModel.uiState.collectAsState()
    val extractingAudio = task.id in state.audioExtracting
    val groups = remember(task, expanded) {
        val list = RowMenu.entries(task, options)
        list.map { it.section }.distinct().map { section -> section to list.filter { it.section == section } }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        groups.forEachIndexed { index, (section, entries) ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Text(
                rowMenuSectionTitle(section, strings),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)
            )
            entries.forEach { entry ->
                if (entry == RowMenuEntry.DELETE && extraLabel != null) {
                    DropdownMenuItem(
                        text = { Text(extraLabel) },
                        onClick = {
                            onPicked()
                            onExtra()
                        }
                    )
                }
                if (entry == RowMenuEntry.EXTRACT_AUDIO && extractingAudio) {
                    DropdownMenuItem(
                        text = { Text(strings.audioExtractRunning) },
                        leadingIcon = { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) },
                        enabled = false,
                        onClick = {}
                    )
                    return@forEach
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            RowMenu.label(entry, strings),
                            color = if (entry.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onPicked()
                        when (entry) {
                            RowMenuEntry.OPEN -> onOpen()
                            RowMenuEntry.OPEN_FOLDER -> onOpenFolder()
                            RowMenuEntry.PAUSE ->
                                if (task.canResume() == false) onDetails()
                                else viewModel.onEvent(DashboardUiEvent.OnPauseClicked(task.id))
                            RowMenuEntry.RESUME -> viewModel.onEvent(DashboardUiEvent.OnResumeClicked(task.id))
                            RowMenuEntry.EXTRACT -> viewModel.onEvent(DashboardUiEvent.OnExtractTaskClicked(task.id))
                            RowMenuEntry.EXTRACT_AUDIO -> viewModel.onEvent(DashboardUiEvent.OnExtractAudioClicked(task.id))
                            RowMenuEntry.COMPRESS -> viewModel.onEvent(DashboardUiEvent.OnAudioCompressClicked(task.id))
                            RowMenuEntry.CUT -> viewModel.onEvent(DashboardUiEvent.OnVideoCutClicked(task.id))
                            RowMenuEntry.SUBTITLES -> viewModel.onEvent(DashboardUiEvent.OnSubtitlesClicked(task.id))
                            RowMenuEntry.SEND_TO_DEVICE -> viewModel.onEvent(DashboardUiEvent.OnShareTaskClicked(task.id))
                            RowMenuEntry.COPY_LINK -> viewModel.onEvent(DashboardUiEvent.OnCopyTaskLink(task.id))
                            RowMenuEntry.SELECT -> onSelect()
                            RowMenuEntry.DETAILS -> onDetails()
                            RowMenuEntry.UNHIDE -> viewModel.onEvent(DashboardUiEvent.OnUnhideTasks(listOf(task.id)))
                            RowMenuEntry.DELETE -> viewModel.onEvent(DashboardUiEvent.OnRemoveClicked(task.id))
                        }
                    }
                )
            }
        }
    }
}

private fun rowMenuSectionTitle(section: RowMenuSection, s: AppLocalization): String = when (section) {
    RowMenuSection.OPEN -> s.rowMenuSectionOpen
    RowMenuSection.MEDIA -> s.rowMenuSectionMedia
    RowMenuSection.DOWNLOAD -> s.rowMenuSectionDownload
    RowMenuSection.MANAGE -> s.rowMenuSectionManage
}
