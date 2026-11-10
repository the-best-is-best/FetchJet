package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.power.SpeedSchedule
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.power.WhenDoneAction
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.etaSeconds
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChromeDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.MenuOptionDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.NavEntryDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.RowKind
import com.michelle.raouf.fetch_jet.nativeapp.dto.StatusDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TaskRowDto
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskSortOption
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val TURTLE_BYTES = 512L * 1024L
internal const val HOME_PERIOD_MS = 250L

internal data class HomeSlice(
    val allTasks: List<DownloadTask>,
    val filteredTasks: List<DownloadTask>,
    val selectedTab: DashboardTab,
    val sortOption: TaskSortOption,
    val downSpeed: Long,
    val upSpeed: Long,
    val unit: String,
    val limit: Long,
    val low: Long,
    val medium: Long,
    val fast: Long,
    val toolsDownloading: Boolean,
    val toolName: String?,
    val toolFraction: Float?,
)

internal data class HomeControls(
    val selectedTab: DashboardTab,
    val sortOption: TaskSortOption,
    val unit: String,
    val limit: Long,
    val low: Long,
    val medium: Long,
    val fast: Long,
)

internal data class HomeExtras(
    val language: String,
    val maxAtOnce: Int,
    val scheduled: Boolean,
    val whenDone: WhenDoneAction,
)

private fun DashboardUiState.homeSlice() = HomeSlice(
    allTasks = allTasks,
    filteredTasks = filteredTasks,
    selectedTab = selectedTab,
    sortOption = sortOption,
    downSpeed = totalDownloadSpeedBytesPerSec,
    upSpeed = totalUploadSpeedBytesPerSec,
    unit = speedDisplayUnit,
    limit = globalDownloadSpeedLimitBytes,
    low = speedPresetLowBytes,
    medium = speedPresetMediumBytes,
    fast = speedPresetFastBytes,
    toolsDownloading = isDownloadingYtDlp,
    toolName = toolDownloadName,
    toolFraction = toolDownloadFraction,
)

private class CachedRow(
    val task: DownloadTask,
    val checked: Boolean,
    val selected: Boolean,
    val unit: String,
    val language: String,
    val row: TaskRowDto,
)

internal class RowCache {
    private var rows = HashMap<String, CachedRow>()
    private var next = HashMap<String, CachedRow>()

    fun begin() {
        next = HashMap(rows.size + 8)
    }

    fun row(task: DownloadTask, checked: Boolean, selected: Boolean, unit: String, language: String): TaskRowDto {
        val old = rows[task.id]
        val timeDependent = task.waitingInQueue && task.scheduledAtMs > 0
        if (old != null && !timeDependent && old.checked == checked && old.selected == selected &&
            old.unit == unit && old.language == language && (old.task === task || old.task == task)
        ) {
            next[task.id] = old
            return old.row
        }
        val row = buildTaskRow(task, checked, selected, unit)
        next[task.id] = CachedRow(task, checked, selected, unit, language, row)
        return row
    }

    fun end() {
        rows = next
    }
}

internal fun buildTaskRow(task: DownloadTask, checked: Boolean, selected: Boolean, unit: String): TaskRowDto {
    val name = taskDisplayName(task)
    val tag = taskKindTag(task)
    val completed = task.status == DownloadStatus.COMPLETED
    return TaskRowDto(
        kind = RowKind.TASK.ordinal,
        id = task.id,
        name = name,
        nameRtl = startsRightToLeft(name),
        nameHan = hasHanCharacters(name),
        tag = tag.take(4),
        tagColor = kindColorCode(tag).ordinal,
        stateLine = taskStateLine(task, unit),
        stateIsError = task.status == DownloadStatus.ERROR,
        size = sizeText(task),
        progress = task.progress.coerceIn(0f, 1f).let { if (it.isNaN()) 0f else it },
        percent = if (completed) "100%" else "${(task.progress.coerceIn(0f, 1f) * 100).toInt()}%",
        barColor = barColorFor(task).ordinal,
        speed = if (task.status == DownloadStatus.DOWNLOADING && task.downloadSpeedBytesPerSec > 0)
            formatSpeed(task.downloadSpeedBytesPerSec, unit) else "—",
        left = task.etaSeconds()?.let { etaText(it) } ?: "—",
        action = rowActionFor(task).ordinal,
        completed = completed,
        priority = task.priority.coerceIn(-1, 1),
        priorityLabel = priorityLabel(task.priority),
        checked = checked,
        selected = selected,
    )
}

private fun headerRow(group: TaskStateGroup, collapsed: Boolean) = TaskRowDto(
    kind = RowKind.HEADER.ordinal,
    id = "header-" + group.key,
    headerKey = group.key,
    headerIcon = group.icon,
    headerTitle = group.title,
    headerCount = group.tasks.size,
    headerColor = group.color.ordinal,
    headerCollapsed = collapsed,
)

private fun navEntry(filter: SideFilter, label: String, all: List<DownloadTask>, ui: LocalUi, settings: Boolean) =
    NavEntryDto(
        filter = filter.ordinal,
        label = label,
        count = all.count { filter.matches(it) },
        selected = !settings && ui.sideFilter == filter,
    )

internal fun Presenter.startHome() {
    val queue = QueueManagerRepository.getInstance()
    val cache = RowCache()
    val slices = vm.uiState.map { it.homeSlice() }.distinctUntilChanged().throttleLatest(HOME_PERIOD_MS)
    val extras = combine(
        AppLanguage.current,
        queue.maxConcurrentDownloads,
        SpeedSchedule.activeCap,
        WhenDone.action,
    ) { language, maxAtOnce, cap, whenDone -> HomeExtras(language, maxAtOnce, cap != null, whenDone) }
    val controls = vm.uiState.map {
        HomeControls(
            selectedTab = it.selectedTab,
            sortOption = it.sortOption,
            unit = it.speedDisplayUnit,
            limit = it.globalDownloadSpeedLimitBytes,
            low = it.speedPresetLowBytes,
            medium = it.speedPresetMediumBytes,
            fast = it.speedPresetFastBytes,
        )
    }.distinctUntilChanged()
    scope.launch {
        combine(slices, local, extras, controls) { slice, ui, extra, now ->
            Triple(
                slice.copy(
                    selectedTab = now.selectedTab,
                    sortOption = now.sortOption,
                    unit = now.unit,
                    limit = now.limit,
                    low = now.low,
                    medium = now.medium,
                    fast = now.fast,
                ),
                ui,
                extra,
            )
        }.collect { (slice, ui, extra) -> publishHome(slice, ui, extra, cache) }
    }
}

private fun Presenter.publishHome(slice: HomeSlice, ui: LocalUi, extra: HomeExtras, cache: RowCache) {
    val strings = LocalizationManager.Strings
    val settings = slice.selectedTab == DashboardTab.SETTINGS
    val inFilter = slice.filteredTasks.filter { ui.sideFilter.matches(it) }
    val query = ui.search.trim()
    val shown = if (query.isEmpty()) inFilter else inFilter.filter {
        it.fileName.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) ||
            (it.originalUrl?.contains(query, ignoreCase = true) == true)
    }

    if (ui.checkedIds.isNotEmpty()) {
        val ids = inFilter.mapTo(HashSet()) { it.id }
        if (!ids.containsAll(ui.checkedIds)) {
            local.update { state -> state.copy(checkedIds = state.checkedIds.filterTo(HashSet()) { it in ids }) }
            return
        }
    }

    val selectedTask = ui.selectedTaskId?.let { id -> slice.allTasks.firstOrNull { it.id == id } }
    val detailsOpen = selectedTask != null && !settings
    val selectionMode = ui.checkedIds.isNotEmpty()

    val rows = ArrayList<TaskRowDto>(shown.size + 4)
    val order = ArrayList<String>(shown.size)
    cache.begin()
    if (ui.sideFilter.groupsByState) {
        for (group in taskStateGroups(shown)) {
            val collapsed = group.key in ui.collapsedGroups
            rows.add(headerRow(group, collapsed))
            if (collapsed) continue
            for (task in group.tasks) {
                rows.add(cache.row(task, task.id in ui.checkedIds, task.id == ui.selectedTaskId, slice.unit, extra.language))
                order.add(task.id)
            }
        }
    } else {
        for (task in shown) {
            rows.add(cache.row(task, task.id in ui.checkedIds, task.id == ui.selectedTaskId, slice.unit, extra.language))
            order.add(task.id)
        }
    }
    cache.end()
    shownOrder = order
    shownIds = shown.map { it.id }
    tasks.publish(rows)

    val unfinished = shown.filter { it.status != DownloadStatus.COMPLETED }
    val turtleActive = slice.limit == TURTLE_BYTES
    val modeLabel = when (slice.limit) {
        0L -> strings.speedPresetHigh
        slice.low -> strings.speedPresetLow
        slice.medium -> strings.speedPresetMedium
        slice.fast -> strings.speedPresetFast
        else -> speedPresetShortLabel(slice.limit)
    }
    val speedOptions = listOf(
        MenuOptionDto(strings.speedPresetLow + "  ·  " + speedPresetShortLabel(slice.low), slice.limit == slice.low),
        MenuOptionDto(strings.speedPresetMedium + "  ·  " + speedPresetShortLabel(slice.medium), slice.limit == slice.medium),
        MenuOptionDto(strings.speedPresetFast + "  ·  " + speedPresetShortLabel(slice.fast), slice.limit == slice.fast),
        MenuOptionDto(strings.speedPresetHigh + "  ·  " + strings.speedLimitUnlimited, slice.limit == 0L),
    )
    val stateFilters = listOf(
        SideFilter.ALL, SideFilter.DOWNLOADING, SideFilter.WAITING, SideFilter.PAUSED, SideFilter.COMPLETED, SideFilter.FAILED,
    )
    val typeFilters = buildList {
        if (AppFeatures.mediaExtractor) add(SideFilter.MEDIA)
        add(SideFilter.FILES)
        if (AppFeatures.torrent) add(SideFilter.TORRENTS)
    }
    val all = slice.allTasks
    chrome.publish(
        ChromeDto(
            settingsOpen = settings,
            title = if (settings) strings.homeSettings else ui.sideFilter.label,
            navState = stateFilters.map { navEntry(it, it.label, all, ui, settings) },
            navType = typeFilters.map { navEntry(it, it.label, all, ui, settings) },
            navRail = listOf(
                navEntry(SideFilter.ALL, strings.homeTabAll, all, ui, settings),
                navEntry(SideFilter.DOWNLOADING, strings.homeTabActive, all, ui, settings),
                navEntry(SideFilter.WAITING, strings.homeFilterWaiting, all, ui, settings),
                navEntry(SideFilter.COMPLETED, strings.homeTabDone, all, ui, settings),
                navEntry(SideFilter.FAILED, strings.homeFilterFailed, all, ui, settings),
            ),
            search = ui.search,
            searchRev = ui.searchRev,
            turtleActive = turtleActive,
            modeLabel = modeLabel,
            capped = slice.limit > 0 && !turtleActive,
            scheduled = extra.scheduled,
            speedOptions = speedOptions,
            sortLabel = strings.homeSortBy(slice.sortOption.label),
            sortOptions = TaskSortOption.entries.map { MenuOptionDto(it.label, it == slice.sortOption) },
            anyPausable = anyPausable(unfinished),
            anyResumable = anyResumable(unfinished),
            showToolbar = shown.isNotEmpty(),
            selectionMode = selectionMode,
            selectedCountText = if (selectionMode) strings.homeSelectedCount(ui.checkedIds.size) else "",
            allChecked = shown.isNotEmpty() && shown.all { it.id in ui.checkedIds },
            listEmpty = shown.isEmpty(),
            emptyTitle = if (shown.isNotEmpty()) "" else if (query.isNotBlank()) strings.homeNoDownloadsMatch(query) else strings.homeNoTasksInView,
            emptyHint = if (shown.isNotEmpty()) "" else if (query.isNotBlank()) strings.homeTryAnotherName else strings.homeAddDownloadToGetStarted,
            toolsBannerShown = slice.toolsDownloading && slice.toolName != null,
            toolsBannerText = if (slice.toolsDownloading && slice.toolName != null)
                strings.homeUpdatingMediaTools(slice.toolName, slice.toolFraction?.let { (it * 100).toInt() }) else "",
            toolsBannerFraction = slice.toolFraction?.coerceIn(0f, 1f) ?: 0f,
            toolsBannerIndeterminate = slice.toolFraction == null,
            whenDoneBannerShown = extra.whenDone != WhenDoneAction.NOTHING,
            whenDoneBannerText = if (extra.whenDone != WhenDoneAction.NOTHING) strings.whenDoneBanner(whenDoneLabel(extra.whenDone)) else "",
            detailsOpen = detailsOpen,
        )
    )

    val running = all.count {
        !it.waitingInQueue && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING)
    }
    val waiting = all.count { it.waitingInQueue }
    status.publish(
        StatusDto(
            downSpeed = formatSpeed(slice.downSpeed, slice.unit),
            upSpeed = formatSpeed(slice.upSpeed, slice.unit),
            summary = strings.homeDownloadingWaitingSummary(running, waiting),
            queue = strings.homeQueueStatus(extra.maxAtOnce),
            limit = strings.homeSpeedLimitStatus(if (slice.limit > 0) formatSpeed(slice.limit, slice.unit) else null),
            version = "v$APP_VERSION",
        )
    )
}
