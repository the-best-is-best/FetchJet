package com.michelle.raouf.fetch_jet.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenu
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuEntry
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuOptions
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
    val entries = remember(task, expanded) { RowMenu.entries(task, options) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
            if (entry.dividerBefore) HorizontalDivider()
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
                        RowMenuEntry.COMPRESS -> viewModel.onEvent(DashboardUiEvent.OnAudioCompressClicked(task.id))
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
