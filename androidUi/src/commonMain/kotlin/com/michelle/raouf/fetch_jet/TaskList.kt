package com.michelle.raouf.fetch_jet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskSortOption
import com.michelle.raouf.fetch_jet.theme.StatusPaused
import com.michelle.raouf.fetch_jet.ui.RowAction
import com.michelle.raouf.fetch_jet.ui.SideFilter
import com.michelle.raouf.fetch_jet.ui.TaskRow
import com.michelle.raouf.fetch_jet.ui.TaskRowsHeader
import com.michelle.raouf.fetch_jet.utils.decodeUnicodeText
import com.michelle.raouf.fetch_jet.utils.formatSpeed
import com.tbib.kmp_localization.LocalizationManager

@Composable
internal fun ToolsDownloadBanner(tool: String?, fraction: Float?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = LocalizationManager.Strings.homeUpdatingMediaTools(
                    tool.orEmpty(),
                    fraction?.let { (it * 100).toInt() }),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun TaskListArea(
    tasks: List<DownloadTask>,
    viewModel: DashboardViewModel,
    speedDisplayUnit: String = "MB/s",
    selectedTab: DashboardTab = DashboardTab.ALL,
    sortOption: TaskSortOption = TaskSortOption.DATE_DESC,
    selectedTaskId: String? = null,
    onSelect: (String?) -> Unit = {},
    /** Search typed in the top bar (wide layouts); null = this list shows its own search box. */
    externalSearch: String? = null,
    /** Downloading / Waiting / Paused & failed / Completed headers. */
    groupByState: Boolean = selectedTab == DashboardTab.ALL,
    /** Phone: Pause all / Resume all / Sort live in the header's ⋮ menu, not above the list. */
    showToolbar: Boolean = true
) {
    // The ViewModel already orders the list (running first, then waiting, paused, failed).
    // Mixed views show one header per state; tap a header to fold / unfold it.
    val grouped = groupByState
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }
    // Search by file name (or link) in the current view.
    var localSearch by remember { mutableStateOf("") }
    val searchQuery = externalSearch ?: localSearch
    val allTasks = tasks
    val shownTasks = remember(allTasks, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) allTasks
        else allTasks.filter {
            it.fileName.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) ||
                    (it.originalUrl?.contains(q, ignoreCase = true) == true)
        }
    }
    val unfinished =
        remember(shownTasks) { shownTasks.filter { it.status != DownloadStatus.COMPLETED } }

    val uiState by viewModel.uiState.collectAsState()

    var checkedIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(allTasks) {
        val ids = allTasks.mapTo(HashSet()) { it.id }
        if (!ids.containsAll(checkedIds)) checkedIds = checkedIds.filterTo(HashSet()) { it in ids }
    }
    val selectionMode = checkedIds.isNotEmpty()
    var anchorId by remember { mutableStateOf<String?>(null) }
    val listFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val desktop = remember { getPlatform().isJvm }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selectionMode,
        onBackCompleted = { checkedIds = emptySet() }
    )

    val tasks = shownTasks
    val shownOrder = if (grouped) {
        taskStateGroups(tasks).filter { collapsedGroups[it.key] != true }.flatMap { it.tasks }
    } else tasks
    Column(
        modifier = if (!desktop) Modifier.fillMaxSize() else Modifier.fillMaxSize()
            .focusRequester(listFocus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    e.key == Key.A && (e.isCtrlPressed || e.isMetaPressed) -> {
                        checkedIds = shownOrder.mapTo(HashSet()) { it.id }
                        true
                    }

                    e.key == Key.Escape && selectionMode -> {
                        checkedIds = emptySet()
                        true
                    }

                    e.key == Key.Delete || (e.key == Key.Backspace && e.isMetaPressed) -> {
                        when {
                            selectionMode -> viewModel.onEvent(
                                DashboardUiEvent.OnRemoveSelectedClicked(checkedIds.toList())
                            )

                            selectedTaskId != null -> viewModel.onEvent(
                                DashboardUiEvent.OnRemoveClicked(selectedTaskId)
                            )
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        if (uiState.isDownloadingYtDlp && uiState.toolDownloadName != null) {
            ToolsDownloadBanner(uiState.toolDownloadName, uiState.toolDownloadFraction)
        }
        com.michelle.raouf.fetch_jet.ui.WhenDoneBanner()
        if (allTasks.isNotEmpty() && externalSearch == null) {
            com.michelle.raouf.fetch_jet.ui.FetchJetTextField(
                value = searchQuery,
                onValueChange = { localSearch = it },
                placeholder = LocalizationManager.Strings.homeSearchDownloadsByNamePlaceholder,
                leading = { Text("🔍", fontSize = 14.sp) },
                trailing = if (searchQuery.isNotEmpty()) {
                    {
                        com.michelle.raouf.fetch_jet.ui.FieldIconButton(
                            "✕",
                            onClick = { localSearch = "" })
                    }
                } else null,
                height = 44.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        if (selectionMode) {
            SelectionBar(
                count = checkedIds.size,
                allChecked = tasks.isNotEmpty() && tasks.all { it.id in checkedIds },
                onCheckAll = { all ->
                    checkedIds = if (all) tasks.mapTo(HashSet()) { it.id } else emptySet()
                },
                onDelete = { viewModel.onEvent(DashboardUiEvent.OnRemoveSelectedClicked(checkedIds.toList())) },
                onExport = { viewModel.onEvent(DashboardUiEvent.OnExportLinksClicked(checkedIds.toList())) },
                onClose = { checkedIds = emptySet() }
            )
        } else if (tasks.isNotEmpty() && showToolbar) {
            ListToolbar(
                tasks = unfinished,
                sortOption = sortOption,
                onPauseAll = { viewModel.onEvent(DashboardUiEvent.OnPauseAllClicked) },
                onResumeAll = { viewModel.onEvent(DashboardUiEvent.OnResumeAllClicked) },
                onSort = { viewModel.onEvent(DashboardUiEvent.OnSortChanged(it)) }
            )
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📥", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) LocalizationManager.Strings.homeNoDownloadsMatch(
                            searchQuery.trim()
                        ) else LocalizationManager.Strings.homeNoTasksInView,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) LocalizationManager.Strings.homeTryAnotherName else LocalizationManager.Strings.homeAddDownloadToGetStarted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            return@Column
        }

        TaskRowsHeader(
            allChecked = tasks.all { it.id in checkedIds },
            someChecked = selectionMode,
            onCheckAll = { all ->
                checkedIds = if (all) tasks.mapTo(HashSet()) { it.id } else emptySet()
            }
        )
        // A download just added (dialog or extension) sits at the top: bring it into view.
        val listState = rememberLazyListState()
        LaunchedEffect(uiState.scrollToTopSignal) {
            if (uiState.scrollToTopSignal > 0) listState.animateScrollToItem(0)
        }
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Phone: room so the floating add button never hides the last row.
            contentPadding = PaddingValues(top = 4.dp, bottom = if (showToolbar) 16.dp else 88.dp)
        ) {
            val taskItem: @Composable (DownloadTask) -> Unit = { task ->
                TaskRow(
                    task = task,
                    selected = task.id == selectedTaskId,
                    speedDisplayUnit = speedDisplayUnit,
                    onClick = {
                        anchorId = task.id
                        if (desktop) listFocus.requestFocus()
                        onSelect(task.id)
                    },
                    checked = task.id in checkedIds,
                    selectionMode = selectionMode,
                    onCheckedChange = { on ->
                        anchorId = task.id
                        if (desktop) listFocus.requestFocus()
                        checkedIds = if (on) checkedIds + task.id else checkedIds - task.id
                    },
                    onCtrlClick = if (!desktop) null else ({
                        anchorId = task.id
                        listFocus.requestFocus()
                        checkedIds =
                            if (task.id in checkedIds) checkedIds - task.id else checkedIds + task.id
                    }),
                    onShiftClick = if (!desktop) null else ({
                        listFocus.requestFocus()
                        val ids = shownOrder.map { it.id }
                        val from = ids.indexOf(anchorId ?: selectedTaskId).takeIf { it >= 0 } ?: 0
                        val to = ids.indexOf(task.id)
                        if (to >= 0) {
                            checkedIds =
                                checkedIds + ids.subList(minOf(from, to), maxOf(from, to) + 1)
                        }
                    }),
                    onLongClick = { checkedIds = checkedIds + task.id },
                    onCompress = if (com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress.canCompress(task)) {
                        { viewModel.onEvent(DashboardUiEvent.OnAudioCompressClicked(task.id)) }
                    } else null,
                    onQuickAction = { action ->
                        when (action) {
                            // A download that cannot resume warns before pausing: in its details.
                            RowAction.PAUSE -> if (task.canResume() == false) onSelect(task.id)
                            else viewModel.onEvent(DashboardUiEvent.OnPauseClicked(task.id))

                            RowAction.RESUME, RowAction.START_NOW ->
                                viewModel.onEvent(DashboardUiEvent.OnResumeClicked(task.id))

                            RowAction.OPEN -> {
                                val dest = task.destinationPath.trimEnd('/', '\\')
                                val sep = if (dest.contains('\\')) "\\" else "/"
                                openDownloadedFile(decodeUnicodeText("$dest$sep${task.fileName}").trim(), task.id)
                            }

                            RowAction.NONE -> {}
                        }
                    },
                    onPriorityChanged = { priority ->
                        viewModel.onEvent(DashboardUiEvent.OnTaskPriorityChanged(task.id, priority))
                    }
                )
            }

            if (grouped) {
                taskStateGroups(tasks).forEach { group ->
                    val collapsed = collapsedGroups[group.key] == true
                    item(key = "header-" + group.key) {
                        SectionHeader(
                            emoji = group.emoji,
                            title = group.title,
                            count = group.tasks.size,
                            color = group.color(),
                            collapsed = collapsed,
                            onToggle = { collapsedGroups[group.key] = !collapsed }
                        )
                    }
                    if (!collapsed) items(group.tasks, key = { it.id }) { taskItem(it) }
                }
            } else {
                items(tasks, key = { it.id }) { taskItem(it) }
            }
        }
            com.michelle.raouf.fetch_jet.ui.AppScrollbar(
                listState,
                Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun SectionHeader(
    emoji: String,
    title: String,
    count: Int,
    color: Color,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onToggle != null) Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle() } else Modifier)
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onToggle != null) {
            Text(
                text = if (collapsed) "▶" else "▼",
                fontSize = 11.sp,
                color = color,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Text(emoji, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    }
}

/** Bottom line of the wide layout: what runs, what waits, the queue size and the speed limit. */
@Composable
internal fun QueueStatusBar(
    tasks: List<DownloadTask>,
    globalLimitBytes: Long,
    speedDisplayUnit: String
) {
    val maxAtOnce by com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository.getInstance()
        .maxConcurrentDownloads.collectAsState()
    val running = tasks.count {
        !it.waitingInQueue && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING)
    }
    val waiting = tasks.count { it.waitingInQueue }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val style = MaterialTheme.typography.labelMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            LocalizationManager.Strings.homeDownloadingWaitingSummary(running, waiting),
            style = style,
            color = color,
            maxLines = 1
        )
        Text(
            LocalizationManager.Strings.homeQueueStatus(maxAtOnce),
            style = style,
            color = color,
            maxLines = 1
        )
        Text(
            LocalizationManager.Strings.homeSpeedLimitStatus(
                if (globalLimitBytes > 0) formatSpeed(
                    globalLimitBytes,
                    speedDisplayUnit
                ) else null
            ),
            style = style, color = color
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "v" + com.michelle.raouf.fetch_jet.core.update.APP_VERSION,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** One header group in the list: Downloading / Waiting / Paused & failed / Completed. */
internal class TaskStateGroup(
    val key: String,
    val emoji: String,
    val title: String,
    val tasks: List<DownloadTask>,
    val color: @Composable () -> Color
)

internal fun taskStateGroups(tasks: List<DownloadTask>): List<TaskStateGroup> {
    val downloading = tasks.filter { SideFilter.DOWNLOADING.matches(it) }
    val waiting = tasks.filter { SideFilter.WAITING.matches(it) }
    val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val used = (downloading + waiting + completed).map { it.id }.toSet()
    // Paused, failed and anything idle.
    val stopped = tasks.filter { it.id !in used }
    return listOf(
        TaskStateGroup(
            "run",
            "⬇",
            LocalizationManager.Strings.homeGroupDownloading,
            downloading
        ) { MaterialTheme.colorScheme.primary },
        TaskStateGroup(
            "wait",
            "⏳",
            LocalizationManager.Strings.homeGroupWaiting,
            waiting
        ) { MaterialTheme.colorScheme.onSurfaceVariant },
        TaskStateGroup(
            "stop",
            "⏸",
            LocalizationManager.Strings.homeGroupPausedFailed,
            stopped
        ) { StatusPaused },
        TaskStateGroup(
            "done",
            "✓",
            LocalizationManager.Strings.homeGroupCompleted,
            completed
        ) { MaterialTheme.colorScheme.tertiary }
    ).filter { it.tasks.isNotEmpty() }
}

internal fun anyPausable(tasks: List<DownloadTask>) = tasks.any {
    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING ||
            it.status == DownloadStatus.QUEUED
}

internal fun anyResumable(tasks: List<DownloadTask>) =
    tasks.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR }

/** Above the list: Pause all · Resume all ········ Sort: Newest first ▾ */
@Composable
internal fun ListToolbar(
    tasks: List<DownloadTask>,
    sortOption: TaskSortOption,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSort: (TaskSortOption) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPauseAll,
            enabled = anyPausable(tasks),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                "⏸  ${LocalizationManager.Strings.homePauseAll}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        OutlinedButton(
            onClick = onResumeAll,
            enabled = anyResumable(tasks),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                "▶  ${LocalizationManager.Strings.homeResumeAll}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        var open by remember { mutableStateOf(false) }
        Box {
            androidx.compose.material3.TextButton(
                onClick = { open = true },
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    "${LocalizationManager.Strings.homeSortBy(sortOption.label)}  ▾",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                TaskSortOption.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (opt == sortOption) "✓  " else "     ") + opt.label,
                                fontSize = 13.sp
                            )
                        },
                        onClick = { open = false; onSort(opt) }
                    )
                }
            }
        }
    }
}

/** Shown instead of the toolbar while rows are checked: 3 selected · Select all ···· Delete  ✕ */
@Composable
internal fun SelectionBar(
    count: Int,
    allChecked: Boolean,
    onCheckAll: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
            Text(
                "✕",
                fontSize = 14.sp
            )
        }
        Text(
            LocalizationManager.Strings.homeSelectedCount(count),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        androidx.compose.material3.TextButton(
            onClick = { onCheckAll(!allChecked) },
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(
                if (allChecked) LocalizationManager.Strings.selectNone else LocalizationManager.Strings.selectAll,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onExport,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                LocalizationManager.Strings.homeExport,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        androidx.compose.material3.Button(
            onClick = onDelete,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                "🗑  ${LocalizationManager.Strings.delete}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onError
            )
        }
    }
}
