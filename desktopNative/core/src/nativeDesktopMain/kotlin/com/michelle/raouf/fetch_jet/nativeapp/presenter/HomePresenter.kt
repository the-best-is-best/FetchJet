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
import com.michelle.raouf.fetch_jet.domain.model.isPreparingFile
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChromeDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.MenuOptionDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.NavEntryDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.RowKind
import com.michelle.raouf.fetch_jet.nativeapp.dto.StatusDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TaskRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TagColorCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.BarColorCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.RowActionCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.TextChoiceDto
import com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord
import com.michelle.raouf.fetch_jet.presentation.dashboard.PLAYLIST_ROW_PREFIX
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenLockType
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenLockTexts
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenOpeningText
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenWorkingText
import com.michelle.raouf.fetch_jet.presentation.dashboard.PlaylistGroup
import com.michelle.raouf.fetch_jet.presentation.dashboard.PlaylistGroupStatus
import com.michelle.raouf.fetch_jet.presentation.dashboard.PlaylistLanguagesUi
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskListItem
import com.michelle.raouf.fetch_jet.presentation.dashboard.FOLDER_ROW_PREFIX
import com.michelle.raouf.fetch_jet.presentation.dashboard.FolderGroup
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskGroupStats
import com.michelle.raouf.fetch_jet.presentation.dashboard.folderGroup
import com.michelle.raouf.fetch_jet.presentation.dashboard.folderKey
import com.michelle.raouf.fetch_jet.presentation.dashboard.groupDownloads
import com.michelle.raouf.fetch_jet.presentation.dashboard.matchesPlaylistTitle
import com.michelle.raouf.fetch_jet.presentation.dashboard.playlistTasks
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardTab
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.HiddenUiState
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
    val playlists: Map<String, PlaylistRecord> = emptyMap(),
    val openPlaylistId: String? = null,
    val playlistLanguages: PlaylistLanguagesUi? = null,
    val groupByFolder: Boolean = false,
    val openFolder: String? = null,
    val hidden: HiddenUiState = HiddenUiState(),
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
    playlists = playlists,
    openPlaylistId = openPlaylistId,
    playlistLanguages = playlistLanguages,
    groupByFolder = groupByFolder,
    openFolder = openFolder,
    hidden = hidden,
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
        canCompress = com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress.canCompress(task),
        canExtract = com.michelle.raouf.fetch_jet.feature.archive_extract.ArchiveTasks.canExtract(task),
        id = task.id,
        name = name,
        nameRtl = startsRightToLeft(name),
        nameHan = hasHanCharacters(name),
        tag = tag.take(4),
        tagColor = kindColorCode(tag).ordinal,
        stateLine = taskStateLine(task, unit),
        stateIsError = task.status == DownloadStatus.ERROR,
        size = sizeText(task),
        progress = (if (task.postProcessLabel != null && task.postProcessProgress >= 0f) task.postProcessProgress else task.progress).coerceIn(0f, 1f).let { if (it.isNaN()) 0f else it },
        preparing = task.isPreparingFile(),
        percent = if (completed && task.postProcessLabel == null) "100%" else "${((if (task.postProcessLabel != null && task.postProcessProgress >= 0f) task.postProcessProgress else task.progress).coerceIn(0f, 1f) * 100).toInt()}%",
        barColor = barColorFor(task).ordinal,
        speed = if (task.status == DownloadStatus.DOWNLOADING && task.downloadSpeedBytesPerSec > 0)
            formatSpeed(task.downloadSpeedBytesPerSec, unit) else "—",
        left = task.etaSeconds()?.let { etaText(it) } ?: "—",
        action = rowActionFor(task).ordinal,
        completed = completed,
        busy = task.postProcessLabel != null,
        priority = task.priority.coerceIn(-1, 1),
        priorityLabel = priorityLabel(task.priority),
        checked = checked,
        selected = selected,
    )
}

internal fun buildGroupRow(group: PlaylistGroup, checked: Boolean, unit: String): TaskRowDto =
    buildStatsRow(group, PLAYLIST_ROW_PREFIX + group.id, group.title, "LIST", TagColorCode.VIDEO, group.summaryText, checked, unit)

internal fun buildFolderRow(group: FolderGroup, checked: Boolean, unit: String): TaskRowDto =
    buildStatsRow(group, FOLDER_ROW_PREFIX + group.folder, group.label, "DIR", TagColorCode.OTHER, group.summaryText, checked, unit)

private fun buildStatsRow(
    group: TaskGroupStats,
    key: String,
    title: String,
    tag: String,
    tagColor: TagColorCode,
    summary: String,
    checked: Boolean,
    unit: String,
): TaskRowDto {
    val status = group.status
    val progress = group.progress.coerceIn(0f, 1f).let { if (it.isNaN()) 0f else it }
    return TaskRowDto(
        kind = RowKind.GROUP.ordinal,
        id = key,
        name = title,
        nameRtl = startsRightToLeft(title),
        nameHan = hasHanCharacters(title),
        tag = tag,
        tagColor = tagColor.ordinal,
        stateLine = summary,
        stateIsError = status == PlaylistGroupStatus.SOME_FAILED,
        size = when {
            group.totalBytes > 0 && status != PlaylistGroupStatus.DONE -> formatBytes(group.downloadedBytes) + " / " + formatBytes(group.totalBytes)
            group.totalBytes > 0 -> formatBytes(group.totalBytes)
            else -> "—"
        },
        progress = progress,
        percent = "${(progress * 100).toInt()}%",
        barColor = when (status) {
            PlaylistGroupStatus.DONE -> BarColorCode.COMPLETED
            PlaylistGroupStatus.PAUSED -> BarColorCode.PAUSED
            PlaylistGroupStatus.SOME_FAILED -> BarColorCode.ERROR
            else -> BarColorCode.PRIMARY
        }.ordinal,
        speed = if (group.speed > 0) formatSpeed(group.speed, unit) else "—",
        left = "—",
        action = when {
            group.canPause -> RowActionCode.PAUSE
            group.canResume -> RowActionCode.RESUME
            else -> RowActionCode.NONE
        }.ordinal,
        completed = status == PlaylistGroupStatus.DONE,
        checked = checked,
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

/** A language code as the name a video's track gave it, else the code itself. */
private fun languageName(code: String, tasks: List<DownloadTask>): String =
    tasks.firstNotNullOfOrNull { t -> t.subtitleTracks.firstOrNull { it.langCode == code }?.langName?.takeIf { n -> n.isNotBlank() } } ?: code

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
    val hiddenScreen = ui.hiddenOpen && slice.hidden.unlocked && !settings
    val lockTexts = hiddenLockTexts(slice.hidden.lockType ?: HiddenLockType.PIN)
    val records = slice.playlists
    val pool = if (hiddenScreen) slice.hidden.tasks else slice.allTasks
    val openGroup = (if (hiddenScreen) slice.hidden.openPlaylistId else slice.openPlaylistId)?.let { id ->
        val members = playlistTasks(pool, id)
        records[id]?.takeIf { !hiddenScreen || members.isNotEmpty() }?.let { PlaylistGroup(it, members) }
    }
    val openFolderGroup = if (openGroup != null) null else (if (hiddenScreen) slice.hidden.openFolder else slice.openFolder)?.let { folderGroup(pool, records, it) }
    val pageOpen = openGroup != null || openFolderGroup != null
    val pageBase = if (hiddenScreen) pool.sortedByDescending { it.createdAtEpochMs } else slice.filteredTasks
    val inFilter = when {
        openGroup != null -> pageBase.filter { it.playlistId == openGroup.id }
        openFolderGroup != null -> {
            val ids = openFolderGroup.tasks.mapTo(HashSet()) { it.id }
            pageBase.filter { it.id in ids }
        }
        hiddenScreen -> pageBase
        else -> slice.filteredTasks.filter { ui.sideFilter.matches(it) }
    }
    val query = ui.search.trim()
    val shown = if (query.isEmpty()) inFilter else inFilter.filter {
        it.fileName.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) ||
            (it.originalUrl?.contains(query, ignoreCase = true) == true) ||
            (!pageOpen && matchesPlaylistTitle(it, records, query))
    }
    val folding = !pageOpen && (records.isNotEmpty() || slice.groupByFolder)

    if (ui.checkedIds.isNotEmpty()) {
        val ids = inFilter.mapTo(HashSet()) { it.id }
        if (folding) inFilter.forEach {
            if (it.playlistId in records) ids.add(PLAYLIST_ROW_PREFIX + it.playlistId)
            else if (slice.groupByFolder) ids.add(FOLDER_ROW_PREFIX + folderKey(it.destinationPath))
        }
        if (!ids.containsAll(ui.checkedIds)) {
            local.update { state -> state.copy(checkedIds = state.checkedIds.filterTo(HashSet()) { it in ids }) }
            return
        }
    }

    val selectedTask = ui.selectedTaskId?.let { id -> slice.allTasks.firstOrNull { it.id == id } ?: slice.hidden.tasks.firstOrNull { it.id == id } }
    val detailsOpen = selectedTask != null && !settings
    val selectionMode = ui.checkedIds.isNotEmpty()

    val rows = ArrayList<TaskRowDto>(shown.size + 4)
    val order = ArrayList<String>(shown.size)
    var heads = 0
    var done = 0
    var focusRow = 0
    var focusHeads = 0
    var focusDone = 0
    fun addTask(task: DownloadTask) {
        if (task.id == ui.focusId) {
            focusRow = rows.size + 1
            focusHeads = heads
            focusDone = done
        }
        if (task.status == DownloadStatus.COMPLETED) done++
        val row = cache.row(task, task.id in ui.checkedIds, task.id == ui.selectedTaskId, slice.unit, extra.language)
        val opening = slice.hidden.opening
        rows.add(
            if (hiddenScreen && opening != null && opening.taskId == task.id)
                row.copy(stateLine = hiddenOpeningText(opening.fraction), stateIsError = false)
            else row
        )
        order.add(task.id)
    }
    fun addGroup(group: PlaylistGroup) {
        val key = PLAYLIST_ROW_PREFIX + group.id
        if (key == ui.focusId) {
            focusRow = rows.size + 1
            focusHeads = heads
            focusDone = done
        }
        rows.add(buildGroupRow(group, key in ui.checkedIds, slice.unit))
        order.add(key)
    }
    fun addFolder(group: FolderGroup) {
        val key = FOLDER_ROW_PREFIX + group.folder
        if (key == ui.focusId) {
            focusRow = rows.size + 1
            focusHeads = heads
            focusDone = done
        }
        rows.add(buildFolderRow(group, key in ui.checkedIds, slice.unit))
        order.add(key)
    }
    fun addItems(items: List<TaskListItem>) {
        for (item in items) when (item) {
            is TaskListItem.Single -> addTask(item.task)
            is TaskListItem.Group -> addGroup(item.group)
            is TaskListItem.Folder -> addFolder(item.group)
        }
    }
    val placedGroups = HashSet<String>()
    fun itemsOf(tasks: List<DownloadTask>): List<TaskListItem> =
        if (!folding) tasks.map { TaskListItem.Single(it) }
        else groupDownloads(tasks, pool, records, slice.groupByFolder)
            .filter { it is TaskListItem.Single || placedGroups.add(it.key) }
    cache.begin()
    if (ui.sideFilter.groupsByState && !pageOpen && !hiddenScreen) {
        for (group in taskStateGroups(shown)) {
            val items = itemsOf(group.tasks)
            if (items.isEmpty()) continue
            val collapsed = group.key in ui.collapsedGroups
            rows.add(headerRow(group, collapsed).copy(headerCount = items.size))
            heads++
            if (collapsed) continue
            addItems(items)
        }
    } else {
        addItems(itemsOf(shown))
    }
    cache.end()
    shownOrder = order
    shownIds = order.toList()
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
            title = if (settings) strings.moreTitle else openGroup?.title ?: openFolderGroup?.label ?: if (hiddenScreen) strings.hiddenTitle else ui.sideFilter.label,
            navState = stateFilters.map { navEntry(it, it.label, all, ui, settings || hiddenScreen) },
            navType = typeFilters.map { navEntry(it, it.label, all, ui, settings || hiddenScreen) },
            navRail = listOf(
                navEntry(SideFilter.ALL, strings.homeTabAll, all, ui, settings || hiddenScreen),
                navEntry(SideFilter.DOWNLOADING, strings.homeTabActive, all, ui, settings || hiddenScreen),
                navEntry(SideFilter.WAITING, strings.homeFilterWaiting, all, ui, settings || hiddenScreen),
                navEntry(SideFilter.COMPLETED, strings.homeTabDone, all, ui, settings || hiddenScreen),
                navEntry(SideFilter.FAILED, strings.homeFilterFailed, all, ui, settings || hiddenScreen),
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
            showToolbar = shown.isNotEmpty() && !pageOpen && !hiddenScreen,
            selectionMode = selectionMode,
            selectedCountText = if (selectionMode) strings.homeSelectedCount(ui.checkedIds.size) else "",
            allChecked = order.isNotEmpty() && order.all { it in ui.checkedIds },
            listEmpty = shown.isEmpty(),
            emptyTitle = if (shown.isNotEmpty()) "" else if (query.isNotBlank()) strings.homeNoDownloadsMatch(query) else if (hiddenScreen) strings.hiddenEmpty else strings.homeNoTasksInView,
            emptyHint = if (shown.isNotEmpty() || hiddenScreen && query.isBlank()) "" else if (query.isNotBlank()) strings.homeTryAnotherName else strings.homeAddDownloadToGetStarted,
            toolsBannerShown = slice.toolsDownloading && slice.toolName != null,
            toolsBannerText = if (slice.toolsDownloading && slice.toolName != null)
                strings.homeUpdatingMediaTools(slice.toolName, slice.toolFraction?.let { (it * 100).toInt() }) else "",
            toolsBannerFraction = slice.toolFraction?.coerceIn(0f, 1f) ?: 0f,
            toolsBannerIndeterminate = slice.toolFraction == null,
            hiddenWorkShown = slice.hidden.working,
            hiddenWorkText = if (slice.hidden.working) hiddenWorkingText(slice.hidden.workDone, slice.hidden.workTotal) else "",
            hiddenWorkFraction = if (slice.hidden.working) slice.hidden.workFraction.coerceIn(0f, 1f) else 0f,
            whenDoneBannerShown = extra.whenDone != WhenDoneAction.NOTHING,
            whenDoneBannerText = if (extra.whenDone != WhenDoneAction.NOTHING) strings.whenDoneBanner(whenDoneLabel(extra.whenDone)) else "",
            detailsOpen = detailsOpen,
            focusId = if (focusRow > 0) ui.focusId.orEmpty() else "",
            focusRev = ui.focusRev,
            focusRow = focusRow,
            focusHeads = focusHeads,
            focusDone = focusDone,
            playlistOpen = pageOpen && !settings,
            playlistSummary = openGroup?.let { it.title + "  ·  " + it.summaryText }
                ?: openFolderGroup?.let { it.label + "  ·  " + it.summaryText }.orEmpty(),
            playlistSubtitles = openGroup?.let { g ->
                if (g.record.subtitleLanguages.isEmpty()) strings.playlistNoSubtitles
                else strings.playlistSubtitlesFor.replace("%1", g.record.subtitleLanguages.joinToString(", ") { code -> languageName(code, g.tasks) })
            }.orEmpty(),
            playlistCanPause = (openGroup ?: openFolderGroup)?.canPause == true,
            playlistCanResume = (openGroup ?: openFolderGroup)?.canResume == true,
            playlistCanCheck = openGroup?.record?.url?.isNotBlank() == true,
            playlistLanguagesShown = openGroup != null && !openGroup.isPage && slice.playlistLanguages?.playlistId == openGroup.id,
            playlistLanguagesBusy = slice.playlistLanguages?.let { it.reading || it.adding } == true,
            playlistLanguagesNote = slice.playlistLanguages?.let { page ->
                when {
                    page.reading -> strings.playlistReadingLanguages
                    page.adding -> strings.playlistLanguageAdding.replace("%1", page.done.toString()).replace("%2", page.total.toString())
                    else -> page.message
                }
            }.orEmpty(),
            playlistLanguageChoices = slice.playlistLanguages?.available.orEmpty().map {
                TextChoiceDto(it.langCode, it.langName.ifBlank { it.langCode }, false)
            },
            groupIsFolder = openFolderGroup != null,
            groupIsPage = openGroup?.isPage == true,
            groupFolderPath = openFolderGroup?.path.orEmpty(),
            hiddenOpen = hiddenScreen,
            hiddenNote = if (hiddenScreen) strings.hiddenNoteEncrypted else "",
            hiddenChangeLabel = if (hiddenScreen) lockTexts.change else "",
            hiddenRemoveLabel = if (hiddenScreen) lockTexts.remove else "",
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
