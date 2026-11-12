package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.edition.AppFeatures
import com.michelle.raouf.fetch_jet.core.filesystem.TextFilePicker
import com.michelle.raouf.fetch_jet.core.filesystem.TorrentFilePicker
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.core.settings.FileCategory
import com.michelle.raouf.fetch_jet.core.util.currentEpochMs
import com.michelle.raouf.fetch_jet.core.util.formatLocalSchedule
import com.michelle.raouf.fetch_jet.core.util.localTimeOnDayEpochMs
import com.michelle.raouf.fetch_jet.core.util.localUtcOffsetMs
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.getClipboardText
import com.michelle.raouf.fetch_jet.nativeapp.dto.AddDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.AddModeCode
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.EngineChoiceDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.QuickFolderDto
import com.michelle.raouf.fetch_jet.platform.desktop.HostHooks
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.canTestLink
import com.tbib.kmp_localization.LocalizationManager
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AddLocal(
    val showTimeFields: Boolean = false,
    val hour: String = "09",
    val minute: String = "00",
    val pm: Boolean = false,
    val dayOffset: Int = 0,
    val timeError: String? = null,
)

internal class EchoField {
    @Volatile
    private var recent: List<String> = emptyList()

    @Volatile
    private var shown: String = ""

    @Volatile
    private var revision = 0

    val forced = MutableStateFlow(0)

    fun fromUi(text: String) {
        recent = (recent + text).takeLast(12)
    }

    fun force() {
        recent = emptyList()
        revision++
        forced.update { it + 1 }
    }

    fun rev(value: String): Int {
        if (value != shown) {
            if (value !in recent) revision++
            shown = value
        }
        return revision
    }
}

internal class AddFields {
    val url = EchoField()
    val fileName = EchoField()
    val checksum = EchoField()
    val destination = EchoField()
    val hour = EchoField()
    val minute = EchoField()
    val playlistFolder = EchoField()
    val linkFolder = EchoField()
}

private data class AddSlice(
    val visible: Boolean,
    val inputUrl: String,
    val destinationPath: String,
    val disableTorrentUpload: Boolean,
    val detectedEngine: EngineType,
    val isLoading: Boolean,
    val errorMessage: String?,
    val isInspectingTorrent: Boolean,
    val torrentFilesReady: Boolean,
    val torrentFileSelected: Boolean,
    val inspectError: String?,
    val isInspectingMedia: Boolean,
    val qualitiesReady: Boolean,
    val qualitySelected: Boolean,
    val playlistCount: Int,
    val hasPlaylist: Boolean,
    val inspectMediaError: String?,
    val isYtDlpInstalled: Boolean,
    val suggestedFileName: String,
    val fileNameOverride: String,
    val sortIntoFolders: Boolean,
    val checksumInput: String,
    val canTestLink: Boolean,
    val testLink: com.michelle.raouf.fetch_jet.presentation.dashboard.TestLinkUi?,
    val linkSelected: Int,
    val linkCount: Int,
    val linkBusy: Boolean,
    val linkNeedsRetry: Boolean,
    val linkFolder: String,
    val scheduleStartMs: Long,
    val selectedConnections: Int,
    val defaultConnections: Int,
    val clipValid: Boolean,
)

private fun DashboardUiState.addSlice() = AddSlice(
    visible = isAddDialogVisible,
    inputUrl = inputUrl,
    destinationPath = destinationPath,
    disableTorrentUpload = disableTorrentUpload,
    detectedEngine = detectedEngine,
    isLoading = isLoading,
    errorMessage = errorMessage,
    isInspectingTorrent = isInspectingTorrent,
    torrentFilesReady = inspectedTorrentFiles.isNotEmpty(),
    torrentFileSelected = inspectedTorrentFiles.any { it.isSelected },
    inspectError = inspectError,
    isInspectingMedia = isInspectingMedia,
    qualitiesReady = inspectedMediaQualities.isNotEmpty(),
    qualitySelected = inspectedMediaQualities.any { it.isSelected },
    playlistCount = inspectedPlaylist?.entries?.count { it.isSelected } ?: 0,
    hasPlaylist = inspectedPlaylist != null,
    inspectMediaError = inspectMediaError,
    isYtDlpInstalled = isYtDlpInstalled,
    suggestedFileName = suggestedFileName,
    fileNameOverride = fileNameOverride,
    sortIntoFolders = sortIntoFolders,
    checksumInput = checksumInput,
    canTestLink = canTestLink(),
    testLink = testLink,
    linkSelected = linkList.count { it.isSelected },
    linkCount = linkList.size,
    linkBusy = linkList.any { it.isSelected && it.isBeingInspected },
    linkNeedsRetry = linkList.any { it.isSelected && it.needsRetry },
    linkFolder = linkListFolder,
    scheduleStartMs = scheduleStartMs,
    selectedConnections = selectedConnections,
    defaultConnections = defaultConnections,
    clipValid = clipCheck(isClipRangeEnabled, clipStartTime, clipEndTime, mediaDurationSeconds).valid,
)

private val AddSlice.showsLinkList: Boolean get() = linkCount > 0

private val AddSlice.isInspectionComplete: Boolean
    get() = if (showsLinkList) linkSelected > 0 && !linkBusy && !linkNeedsRetry else when (detectedEngine) {
        EngineType.BITTORRENT -> torrentFilesReady && torrentFileSelected && inspectError == null
        EngineType.MEDIA_STREAM -> inspectMediaError == null && (playlistCount > 0 || (qualitiesReady && qualitySelected))
        EngineType.HTTP_DIRECT -> true
    }

private val AddSlice.isInspecting: Boolean get() = isInspectingTorrent || isInspectingMedia || (showsLinkList && linkBusy)

private val AddSlice.hasActiveError: Boolean get() = errorMessage != null || inspectError != null || inspectMediaError != null

private fun AddSlice.typeFolder(): String? =
    if (sortIntoFolders) FileCategory.folderFor(
        fileNameOverride.ifEmpty { suggestedFileName },
        isMedia = detectedEngine == EngineType.MEDIA_STREAM,
        isTorrent = detectedEngine == EngineType.BITTORRENT,
    ) else null

private fun AddSlice.folderCategory(): String =
    if (!visible || inputUrl.isBlank() || showsLinkList) "" else FileCategory.categoryFor(
        fileNameOverride.ifEmpty { suggestedFileName },
        isMedia = detectedEngine == EngineType.MEDIA_STREAM,
        isTorrent = detectedEngine == EngineType.BITTORRENT,
    )

internal fun Presenter.startAdd() {
    val slices = vm.uiState.map { it.addSlice() }.distinctUntilChanged()
    scope.launch {
        combine(slices, addLocal, AppLanguage.current, addFields.url.forced, addFields.destination.forced) { slice, ui, _, _, _ ->
            buildAdd(slice, ui)
        }.collect { add.publish(it) }
    }
    scope.launch {
        slices.map { Triple(it.visible, it.typeFolder(), it.folderCategory()) }.distinctUntilChanged().collect {
            addFields.destination.force()
        }
    }
    scope.launch {
        slices.map { Triple(it.visible, it.inputUrl, it.detectedEngine) }.distinctUntilChanged().collect { (visible, url, engine) ->
            if (!visible || url.isBlank()) return@collect
            val state = vm.uiState.value
            when (engine) {
                EngineType.BITTORRENT ->
                    if (state.inspectedTorrentFiles.isEmpty() && !state.isInspectingTorrent && state.inspectError == null) {
                        vm.onEvent(DashboardUiEvent.OnInspectTorrentClicked)
                    }

                EngineType.MEDIA_STREAM ->
                    if (state.inspectedMediaQualities.isEmpty() && state.inspectedPlaylist == null &&
                        !state.isInspectingMedia && state.inspectMediaError == null && state.isYtDlpInstalled
                    ) {
                        vm.onEvent(DashboardUiEvent.OnInspectMediaClicked)
                    }

                EngineType.HTTP_DIRECT -> Unit
            }
        }
    }
    scope.launch {
        slices.map { it.visible to it.typeFolder() }.distinctUntilChanged().collect { (visible, typeFolder) ->
            if (!visible) return@collect
            val state = vm.uiState.value
            val base = FileCategory.stripTypeFolders(
                state.destinationPath,
                listOf(pathResolver.getDownloadsDirectory(), pathResolver.getDocumentsDirectory(), pathResolver.getDesktopDirectory()),
            )
            val sep = if (base.contains('\\')) "\\" else "/"
            val full = if (typeFolder != null) base + sep + typeFolder else base
            if (full != state.destinationPath) vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(full))
        }
    }
    scope.launch {
        slices.map { it.visible }.distinctUntilChanged().collect { visible ->
            if (!visible) addLocal.value = AddLocal()
        }
    }
}

private fun Presenter.buildAdd(s: AddSlice, ui: AddLocal): AddDto {
    if (!s.visible) return AddDto(urlRev = addFields.url.rev(s.inputUrl))
    val strings = LocalizationManager.Strings
    val inspecting = s.isInspectingMedia || s.isInspectingTorrent
    val complete = s.isInspectionComplete
    val isUrlValid = s.inputUrl.isNotBlank()
    val canStart = isUrlValid && complete && !s.isLoading && !s.isInspecting && !s.hasActiveError && s.clipValid
    val engines = buildList {
        add(EngineChoiceDto(EngineType.HTTP_DIRECT.ordinal, "⚡", strings.engineDirect, s.detectedEngine == EngineType.HTTP_DIRECT))
        if (AppFeatures.mediaExtractor) {
            add(EngineChoiceDto(EngineType.MEDIA_STREAM.ordinal, "🎬", strings.engineMedia, s.detectedEngine == EngineType.MEDIA_STREAM))
        }
        if (AppFeatures.torrent) {
            add(EngineChoiceDto(EngineType.BITTORRENT.ordinal, "🧲", strings.engineTorrent, s.detectedEngine == EngineType.BITTORRENT))
        }
    }
    val fileNameShown = !s.showsLinkList && !s.hasPlaylist && (s.suggestedFileName.isNotBlank() || s.fileNameOverride.isNotBlank())
    val fileName = if (inspecting && s.fileNameOverride.isEmpty()) strings.findingName else s.fileNameOverride.ifEmpty { s.suggestedFileName }
    val downloads = pathResolver.getDownloadsDirectory()
    val documents = pathResolver.getDocumentsDirectory()
    val desktop = pathResolver.getDesktopDirectory()
    val primaryLabel = when {
        s.isLoading -> strings.addStarting
        s.isInspecting -> strings.addInspecting
        s.hasActiveError -> strings.addInspectionFailed
        !isUrlValid -> strings.addEnterUrl
        s.showsLinkList && s.linkSelected > 0 ->
            if (s.scheduleStartMs > 0L) strings.scheduleLinks(s.linkSelected) else strings.downloadLinks(s.linkSelected)

        !complete && s.detectedEngine == EngineType.MEDIA_STREAM -> strings.inspectVideoFirst
        !complete && s.detectedEngine == EngineType.BITTORRENT -> strings.inspectTorrentFirst
        !s.clipValid -> strings.invalidTimeRange
        s.hasPlaylist && s.detectedEngine == EngineType.MEDIA_STREAM ->
            if (s.scheduleStartMs > 0L) strings.scheduleVideos(s.playlistCount) else strings.downloadVideos(s.playlistCount)

        s.scheduleStartMs > 0L -> strings.addScheduleDownload
        else -> strings.addStartDownload
    }
    return AddDto(
        visible = true,
        url = s.inputUrl,
        urlRev = addFields.url.rev(s.inputUrl),
        canImportTxt = TextFilePicker.isSupported,
        canOpenTorrent = AppFeatures.torrent && TorrentFilePicker.isSupported,
        mode = when {
            s.showsLinkList -> AddModeCode.LINK_LIST.ordinal
            s.hasPlaylist -> AddModeCode.PLAYLIST.ordinal
            else -> AddModeCode.SINGLE.ordinal
        },
        engine = s.detectedEngine.ordinal,
        engines = engines,
        scheduleNow = s.scheduleStartMs == 0L && !ui.showTimeFields,
        scheduleAtTime = ui.showTimeFields,
        scheduleHour = ui.hour,
        scheduleMinute = ui.minute,
        scheduleRev = addFields.hour.rev(ui.hour) + addFields.minute.rev(ui.minute),
        schedulePm = ui.pm,
        scheduleDays = listOf(
            0 to strings.scheduleToday,
            1 to strings.scheduleTomorrow,
            2 to strings.scheduleIn2Days,
            3 to strings.scheduleIn3Days,
            7 to strings.scheduleInAWeek,
        ).map { (day, label) -> ChoiceDto(day, label, ui.dayOffset == day) },
        scheduleError = ui.timeError.orEmpty(),
        scheduleStartsAt = if (s.scheduleStartMs > 0L) strings.scheduleStartsAt(formatLocalSchedule(s.scheduleStartMs)) else "",
        connectionsShown = s.detectedEngine == EngineType.HTTP_DIRECT && !s.showsLinkList,
        connections = buildList {
            add(
                ChoiceDto(
                    0,
                    if (s.defaultConnections > 0) strings.connectionsAutoWithDefault(s.defaultConnections) else strings.connectionsAuto,
                    s.selectedConnections == 0,
                )
            )
            for (n in listOf(1, 4, 8, 16, 32)) add(ChoiceDto(n, n.toString(), s.selectedConnections == n))
        },
        fileNameShown = fileNameShown,
        fileName = fileName,
        fileNameRev = addFields.fileName.rev(fileName),
        fileNameBusy = inspecting,
        fileNameCanReset = s.fileNameOverride.isNotBlank() && s.fileNameOverride != s.suggestedFileName,
        checksumShown = !s.showsLinkList && !s.hasPlaylist && s.detectedEngine == EngineType.HTTP_DIRECT,
        checksum = s.checksumInput,
        checksumRev = addFields.checksum.rev(s.checksumInput),
        testLinkShown = s.canTestLink,
        testLinkBusy = s.testLink?.testing == true,
        testLinkOk = s.testLink?.ok == true,
        testLinkText = com.michelle.raouf.fetch_jet.presentation.dashboard.testLinkText(s.testLink, s.suggestedFileName) { formatBytes(it) },
        destination = s.destinationPath,
        destinationRev = addFields.destination.rev(s.destinationPath),
        quickFolders = listOf(
            QuickFolderDto("📥", strings.quickFolderDownloads, downloads),
            QuickFolderDto("📄", strings.quickFolderDocuments, documents),
            QuickFolderDto("💻", strings.quickFolderDesktop, desktop),
        ),
        linkSummary = if (s.showsLinkList) strings.selectedOfLinks(s.linkSelected, s.linkCount) else "",
        linkAllSelected = s.showsLinkList && s.linkSelected == s.linkCount,
        linkFolder = s.linkFolder,
        linkFolderRev = addFields.linkFolder.rev(s.linkFolder),
        leechShown = s.detectedEngine == EngineType.BITTORRENT,
        leech = s.disableTorrentUpload,
        error = s.errorMessage ?: s.inspectError ?: s.inspectMediaError ?: "",
        showRetry = s.hasActiveError && !complete,
        primaryLabel = primaryLabel,
        primaryEnabled = canStart,
        loading = s.isLoading,
    )
}

internal fun Presenter.addSetUrl(text: String) {
    addFields.url.fromUi(text)
    vm.onEvent(DashboardUiEvent.OnUrlChanged(text))
    if (vm.uiState.value.inputUrl != text) addFields.url.force()
}

internal fun Presenter.addReplaceUrl(text: String) {
    addFields.url.force()
    vm.onEvent(DashboardUiEvent.OnUrlChanged(text))
}

internal fun Presenter.addPasteUrl() {
    scope.launch {
        val clip = try {
            getClipboardText()?.trim()
        } catch (_: Throwable) {
            null
        }
        if (!clip.isNullOrBlank()) withContext(Dispatchers.Main) { addReplaceUrl(clip) }
    }
}

internal fun Presenter.addImportLinks() {
    TextFilePicker.pick { text -> if (!text.isNullOrBlank()) addReplaceUrl(text) }
}

internal fun Presenter.addOpenTorrent() {
    TorrentFilePicker.pick { path -> if (!path.isNullOrBlank()) addReplaceUrl(path) }
}

internal fun Presenter.addPickDestination() {
    val pick = HostHooks.pickFolder ?: return
    val current = vm.uiState.value.destinationPath
    scope.launch(Dispatchers.IO) {
        val chosen = try {
            pick("Select Download Directory", current)
        } catch (_: Throwable) {
            null
        }
        if (!chosen.isNullOrBlank()) withContext(Dispatchers.Main) {
            addFields.destination.force()
            vm.onEvent(DashboardUiEvent.OnDestinationPathChanged(chosen))
        }
    }
}

internal fun Presenter.addScheduleAtTime() {
    addLocal.update { ui ->
        if (ui.showTimeFields) ui else {
            val minuteOfDay = ((currentEpochMs() + localUtcOffsetMs()).mod(24L * 60 * 60 * 1000) / 60_000L).toInt()
            val h24 = minuteOfDay / 60
            ui.copy(
                showTimeFields = true,
                hour = (if (h24 % 12 == 0) 12 else h24 % 12).toString().padStart(2, '0'),
                minute = (minuteOfDay % 60).toString().padStart(2, '0'),
                pm = h24 >= 12,
                dayOffset = 0,
                timeError = null,
            )
        }
    }
}

internal fun Presenter.addScheduleQuick(minutes: Int) {
    addLocal.update { it.copy(showTimeFields = false) }
    vm.onEvent(DashboardUiEvent.OnScheduleChanged(if (minutes <= 0) 0L else currentEpochMs() + minutes * 60_000L))
}

internal fun Presenter.addApplyScheduleTime() {
    val ui = addLocal.value
    val h12 = ui.hour.toIntOrNull()?.coerceIn(1, 12) ?: 12
    val m = ui.minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val h24 = (h12 % 12) + if (ui.pm) 12 else 0
    val at = localTimeOnDayEpochMs(ui.dayOffset, h24, m)
    val passed = at <= currentEpochMs()
    addLocal.update {
        it.copy(
            hour = h12.toString().padStart(2, '0'),
            minute = m.toString().padStart(2, '0'),
            timeError = if (passed) LocalizationManager.Strings.scheduleTimePassed else null,
        )
    }
    if (!passed) vm.onEvent(DashboardUiEvent.OnScheduleChanged(at))
}

internal fun Presenter.addRetryInspect() {
    if (vm.uiState.value.detectedEngine == EngineType.BITTORRENT) vm.onEvent(DashboardUiEvent.OnInspectTorrentClicked)
    else vm.onEvent(DashboardUiEvent.OnInspectMediaClicked)
}
