package com.michelle.raouf.fetch_jet.nativeapp.presenter

import com.michelle.raouf.fetch_jet.core.filesystem.PathResolver
import com.michelle.raouf.fetch_jet.core.i18n.start.AppLanguage
import com.michelle.raouf.fetch_jet.nativeapp.channels.ListChannel
import com.michelle.raouf.fetch_jet.nativeapp.channels.QueueChannel
import com.michelle.raouf.fetch_jet.nativeapp.channels.StructChannel
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiChannel
import com.michelle.raouf.fetch_jet.nativeapp.dto.Channels
import com.michelle.raouf.fetch_jet.nativeapp.dto.AddDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.AddMediaDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.AddTorrentDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TorrentFileRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChoiceDialogDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ClipPickerDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.DevicePairingDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.DeviceRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.DeviceSheetDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.LinkRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.PlaylistRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.QualityRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.SubtitleRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.AlertDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ChromeDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ConnectionDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.DetailsDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.FileRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.PieceDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.EffectDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.EffectKind
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.SettingsNavDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.ShellDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.StatusDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.TaskRowDto
import com.michelle.raouf.fetch_jet.nativeapp.dto.UpdateDialogDto
import com.michelle.raouf.fetch_jet.core.update.UpdateState
import com.michelle.raouf.fetch_jet.nativeapp.runtime.ChangeNotifier
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEffect
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class LocalUi(
    val sideFilter: SideFilter = SideFilter.ALL,
    val search: String = "",
    val searchRev: Int = 0,
    val selectedTaskId: String? = null,
    val checkedIds: Set<String> = emptySet(),
    val anchorId: String? = null,
    val collapsedGroups: Set<String> = emptySet(),
    val systemDark: Boolean = false,
    val missingFileTaskId: String? = null,
    val restartPauseTaskId: String? = null,
)

internal data class ToastState(val text: String = "", val shown: Boolean = false)

internal class Presenter(notifier: ChangeNotifier, val headless: Boolean) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vm = DashboardViewModel()
    val pathResolver = PathResolver()

    val local = MutableStateFlow(LocalUi())
    val toast = MutableStateFlow(ToastState())
    private val toasts = Channel<String>(Channel.UNLIMITED)

    val tr = TrChannel(Channels.TR.id, Channels.TR.name, notifier)
    val shell = StructChannel(Channels.SHELL.id, Channels.SHELL.name, ShellDto.serializer(), notifier)
    val chrome = StructChannel(Channels.CHROME.id, Channels.CHROME.name, ChromeDto.serializer(), notifier)
    val status = StructChannel(Channels.STATUS.id, Channels.STATUS.name, StatusDto.serializer(), notifier)
    val tasks = ListChannel(Channels.TASKS.id, Channels.TASKS.name, TaskRowDto.serializer(), notifier)
    val effects = QueueChannel(Channels.EFFECTS.id, Channels.EFFECTS.name, EffectDto.serializer(), notifier)
    val details = StructChannel(Channels.DETAILS.id, Channels.DETAILS.name, DetailsDto.serializer(), notifier)
    val detailPieces = ListChannel(Channels.DETAIL_PIECES.id, Channels.DETAIL_PIECES.name, PieceDto.serializer(), notifier)
    val detailConnections =
        ListChannel(Channels.DETAIL_CONNECTIONS.id, Channels.DETAIL_CONNECTIONS.name, ConnectionDto.serializer(), notifier)
    val detailFiles = ListChannel(Channels.DETAIL_FILES.id, Channels.DETAIL_FILES.name, FileRowDto.serializer(), notifier)
    val alerts = ListChannel(Channels.ALERTS.id, Channels.ALERTS.name, AlertDto.serializer(), notifier)
    val add = StructChannel(Channels.ADD.id, Channels.ADD.name, AddDto.serializer(), notifier)
    val addLocal = MutableStateFlow(AddLocal())
    val addFields = AddFields()
    val addMedia = StructChannel(Channels.ADD_MEDIA.id, Channels.ADD_MEDIA.name, AddMediaDto.serializer(), notifier)
    val addQualities = ListChannel(Channels.ADD_QUALITIES.id, Channels.ADD_QUALITIES.name, QualityRowDto.serializer(), notifier)
    val addSubtitles = ListChannel(Channels.ADD_SUBTITLES.id, Channels.ADD_SUBTITLES.name, SubtitleRowDto.serializer(), notifier)
    val addPlaylist = ListChannel(Channels.ADD_PLAYLIST.id, Channels.ADD_PLAYLIST.name, PlaylistRowDto.serializer(), notifier)
    val clipPickerChannel = StructChannel(Channels.CLIP_PICKER.id, Channels.CLIP_PICKER.name, ClipPickerDto.serializer(), notifier)
    val clipPicker = MutableStateFlow(ClipPicker())
    val choices = ListChannel(Channels.CHOICES.id, Channels.CHOICES.name, ChoiceDialogDto.serializer(), notifier)
    val settingsNav = StructChannel(Channels.SETTINGS_NAV.id, Channels.SETTINGS_NAV.name, SettingsNavDto.serializer(), notifier)
    val settingsRows = ListChannel(Channels.SETTINGS_ROWS.id, Channels.SETTINGS_ROWS.name, SettingRowDto.serializer(), notifier)
    val settingsLocal = MutableStateFlow(SettingsLocal())
    val settingsEcho = EchoFields()
    val updateDialog = StructChannel(Channels.UPDATE_DIALOG.id, Channels.UPDATE_DIALOG.name, UpdateDialogDto.serializer(), notifier)
    val debugUpdate = MutableStateFlow<UpdateState?>(null)
    val addLinks = ListChannel(Channels.ADD_LINKS.id, Channels.ADD_LINKS.name, LinkRowDto.serializer(), notifier)
    val deviceSheet = StructChannel(Channels.DEVICE_SHEET.id, Channels.DEVICE_SHEET.name, DeviceSheetDto.serializer(), notifier)
    val deviceRows = ListChannel(Channels.DEVICE_ROWS.id, Channels.DEVICE_ROWS.name, DeviceRowDto.serializer(), notifier)
    val devicePairing =
        StructChannel(Channels.DEVICE_PAIRING.id, Channels.DEVICE_PAIRING.name, DevicePairingDto.serializer(), notifier)
    val deviceLocal = MutableStateFlow(DeviceLocal())
    val deviceEcho = EchoFields()
    val debugDevices = MutableStateFlow<DeviceDebug?>(null)
    val addTorrent = StructChannel(Channels.ADD_TORRENT.id, Channels.ADD_TORRENT.name, AddTorrentDto.serializer(), notifier)
    val addTorrentFiles =
        ListChannel(Channels.ADD_TORRENT_FILES.id, Channels.ADD_TORRENT_FILES.name, TorrentFileRowDto.serializer(), notifier)
    val audioCompress = StructChannel(
        Channels.AUDIO_COMPRESS.id, Channels.AUDIO_COMPRESS.name,
        com.michelle.raouf.fetch_jet.nativeapp.dto.AudioCompressDto.serializer(), notifier
    )
    val debugSections = MutableStateFlow(false)
    val mainSetupAsk = MutableStateFlow<com.michelle.raouf.fetch_jet.core.setup.MainDownloaderState?>(null)

    val channels: List<UiChannel> = listOf(
        tr, shell, chrome, status, tasks, effects, details, detailPieces, detailConnections, detailFiles, alerts, add,
        addMedia, addQualities, addSubtitles, addPlaylist, clipPickerChannel, choices, settingsNav, settingsRows, updateDialog, addLinks, deviceSheet,
        deviceRows, devicePairing, addTorrent, addTorrentFiles, audioCompress,
    )

    @kotlin.concurrent.Volatile
    var shownOrder: List<String> = emptyList()

    @kotlin.concurrent.Volatile
    var shownIds: List<String> = emptyList()

    fun start() {
        scope.launch {
            vm.uiEffect.collect { effect ->
                when (effect) {
                    is DashboardUiEffect.ShowToast -> showToast(effect.text)
                }
            }
        }
        scope.launch {
            for (text in toasts) {
                toast.value = ToastState(text, true)
                delay(TOAST_MS)
                toast.update { it.copy(shown = false) }
                delay(TOAST_GAP_MS)
            }
        }
        scope.launch {
            var language: String? = null
            AppLanguage.current.collect { current ->
                if (language != null && language != current) dropToasts()
                language = current
                tr.publishCurrent()
            }
        }
        scope.launch {
            vm.uiState.map { it.scrollToTopSignal }.distinctUntilChanged().collect { signal ->
                if (signal > 0) effect(EffectKind.SCROLL_TO_ROW)
            }
        }
        startShell()
        startHome()
        startDetails()
        startAlerts()
        startAdd()
        startAddMedia()
        startChoices()
        startSettings()
        startUpdates()
        startAddLinks()
        startClipboardWatcher()
        startDevices()
        startAddTorrent()
        startAudioCompress()
        startMainDownloaderSetup()
    }

    fun showToast(text: String) {
        if (text.isNotBlank()) toasts.trySend(text)
    }

    private fun dropToasts() {
        while (toasts.tryReceive().isSuccess) Unit
        toast.update { it.copy(shown = false) }
    }

    fun effect(kind: EffectKind, text: String = "", title: String = "", number: Int = 0) {
        effects.push(EffectDto(kind = kind.ordinal, text = text, title = title, number = number))
    }

    fun stop() {
        scope.cancel()
    }

    companion object {
        const val TOAST_MS = 4000L
        const val TOAST_GAP_MS = 200L
    }
}
