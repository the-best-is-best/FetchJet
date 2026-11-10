package com.michelle.raouf.fetch_jet.presentation.dashboard


import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry
import com.tbib.kmp_localization.LocalizationManager


enum class DashboardTab {

    ALL,

    ACTIVE,

    COMPLETED,

    TORRENT,

    MEDIA,

    SETTINGS;

    val title: String
        get() = when (this) {
            ALL -> LocalizationManager.Strings.sysTabAllDownloads
            ACTIVE -> LocalizationManager.Strings.sysTabActive
            COMPLETED -> LocalizationManager.Strings.sysTabCompleted
            TORRENT -> "BitTorrent"
            MEDIA -> LocalizationManager.Strings.sysTabMediaExtractor
            SETTINGS -> LocalizationManager.Strings.sysTabSettings
        }

}


enum class TaskSortOption {
    DATE_DESC,
    DATE_ASC,
    SIZE_DESC,
    SIZE_ASC,
    NAME_ASC;

    val label: String
        get() = when (this) {
            DATE_DESC -> LocalizationManager.Strings.sysSortNewestFirst
            DATE_ASC -> LocalizationManager.Strings.sysSortOldestFirst
            SIZE_DESC -> LocalizationManager.Strings.sysSortLargestFirst
            SIZE_ASC -> LocalizationManager.Strings.sysSortSmallestFirst
            NAME_ASC -> LocalizationManager.Strings.sysSortNameAz
        }
}

data class LinkListEntry(
    val url: String,
    val engine: EngineType,
    val isSelected: Boolean = true,
    val isInspecting: Boolean = false,
    val inspectError: String? = null,
    val torrentName: String? = null,
    val torrentFiles: List<TorrentFileEntry> = emptyList(),
    val webSeeds: List<String> = emptyList(),
    val isInspectingMedia: Boolean = false,
    val mediaInspectFailed: Boolean = false,
    val mediaTitle: String? = null,
    val mediaQualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = emptyList()
) {
    /** A torrent's files or a video's qualities are still being read: it cannot be started yet. */
    val isBeingInspected: Boolean get() = isInspecting || isInspectingMedia

    /** A video whose qualities could not be read: the user retries it or unchecks it. */
    val needsRetry: Boolean get() = engine == EngineType.MEDIA_STREAM && mediaInspectFailed
}

data class FileConflictData(
    val fileName: String,
    val destinationPath: String,
    val suggestedNewName: String
)

data class DashboardUiState(

    val inputUrl: String = "",

    val destinationPath: String = "",

    val disableTorrentUpload: Boolean = false,

    val isDarkMode: Boolean = true,
    /** "system", "light" or "dark"; the UI resolves "system" from the device. */
    val themeMode: String = "system",

    val isAddDialogVisible: Boolean = false,

    val allTasks: List<DownloadTask> = emptyList(),

    val filteredTasks: List<DownloadTask> = emptyList(),

    val selectedTab: DashboardTab = DashboardTab.ALL,
    val sortOption: TaskSortOption = TaskSortOption.DATE_DESC,

    val totalDownloadSpeedBytesPerSec: Long = 0L,

    val totalUploadSpeedBytesPerSec: Long = 0L,

    val isLoading: Boolean = false,

    val errorMessage: String? = null,

    val message: String? = null,

    val detectedEngine: EngineType = EngineType.HTTP_DIRECT,

    val isInspectingTorrent: Boolean = false,

    val inspectedTorrentName: String? = null,

    val inspectedTorrentFiles: List<TorrentFileEntry> = emptyList(),

    val inspectedTorrentWebSeeds: List<String> = emptyList(),

    val inspectError: String? = null,

    val isInspectingMedia: Boolean = false,
    /** The browser extension is reading a course's lectures: done / total, null when not. */
    val courseReading: Pair<Int, Int>? = null,

    val inspectedMediaTitle: String? = null,
    /** Name FetchJet would give the file (shown in the Add dialog's "File name" box). */
    val suggestedFileName: String = "",
    /** What the user typed in that box; empty = use [suggestedFileName]. */
    val fileNameOverride: String = "",
    /** Optional checksum the user typed to verify the finished file (sha256 / md5 hex). */
    val checksumInput: String = "",

    val inspectedMediaQualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = emptyList(),
    val inspectedSubtitles: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack> = emptyList(),
    val embedSubtitles: Boolean = false,

    val inspectMediaError: String? = null,

    val removeConfirmationTaskId: String? = null,
    val removeConfirmationTaskIds: List<String> = emptyList(),
    /** A browser-sent file link the user just dismissed; asks "download it in the browser instead?" */
    val browserFallbackUrl: String? = null,
    /** A file / magnet link just copied: "Download it?" (Settings > General > Watch the clipboard). */
    val clipboardOfferUrl: String? = null,
    /** Bumped when a download was just added: the list scrolls to the top to show it. */
    val scrollToTopSignal: Int = 0,
    /** Behaviour settings (Settings > General / Downloads), mirrored from BehaviorSettings. */
    val monitorClipboard: Boolean = false,
    val clipboardVideoPages: Boolean = false,
    val sortIntoFolders: Boolean = false,

    val duplicateTaskAlert: DownloadTask? = null,
    val fileConflictAlert: FileConflictData? = null,

    val selectedDownloadSpeedLimitBytes: Long = 0L,

    val selectedUploadSpeedLimitBytes: Long = 0L,

    val globalDownloadSpeedLimitBytes: Long = 0L,

    val globalUploadSpeedLimitBytes: Long = 0L,

    val ytDlpStatus: String = "",

    val isDownloadingYtDlp: Boolean = false,
    val toolDownloadName: String? = null,
    val toolDownloadFraction: Float? = null,

    val isYtDlpInstalled: Boolean = true,

    val isFfmpegInstalled: Boolean = true,

    val jsRuntimeName: String = "",
    val isJsRuntimeReady: Boolean = true,

    val toolsDirectoryPath: String = "",
    val torrentEngineStatus: String = "",
    val isTorrentEngineReady: Boolean = true,
    val clipStartTime: String = "",
    val clipEndTime: String = "",
    val isClipRangeEnabled: Boolean = false,
    /** Clip: download the whole video at full speed and cut it afterwards (uses more data). */
    val clipFastMode: Boolean = false,
    /** Audio-only downloads are converted to this format (null = keep the original). */
    val audioConvertFormat: String? = null,
    /** A playlist / channel link: its videos (null = a single video). */
    val inspectedPlaylist: com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist? = null,
    val linkList: List<LinkListEntry> = emptyList(),
    /** The folder the list's downloads go into, under the chosen one ("" = none). */
    val linkListFolder: String = "",
    val playlistQuality: com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality =
        com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality.BEST,
    /** Start the new download(s) at this time (epoch ms); 0 = now. */
    val scheduleStartMs: Long = 0L,
    /** Connections for this direct download (0 = app setting / automatic). */
    val selectedConnections: Int = 0,
    /** App-wide default connections per direct download (Settings > Downloads); 0 = automatic. */
    val defaultConnections: Int = 0,
    val mediaDurationSeconds: Long = 0L,
    val speedDisplayUnit: String = "MB/s",
    /** Named quick speed presets (Settings > Speed), picked fast from the header. */
    val speedPresetLowBytes: Long = 256L * 1024,
    val speedPresetLowUnit: String = "KB/s",
    val speedPresetMediumBytes: Long = 2L * 1024 * 1024,
    val speedPresetMediumUnit: String = "MB/s",
    val speedPresetFastBytes: Long = 3L * 1024 * 1024,
    val speedPresetFastUnit: String = "MB/s",
    val speedPresetHighBytes: Long = 20L * 1024 * 1024,
    val speedPresetHighUnit: String = "MB/s",
    /** The task whose "Send to device" sheet is open (null = closed). */
    val deviceShareTaskId: String? = null,
    /** Devices found on the LAN running FetchJet. */
    val discoveredDevices: List<com.michelle.raouf.fetch_jet.feature.device_sync.DiscoveredDevice> = emptyList(),
    val pairedDevices: List<com.michelle.raouf.fetch_jet.feature.device_sync.PairedDevice> = emptyList(),
    val blockedDevices: List<com.michelle.raouf.fetch_jet.feature.device_sync.PairedDevice> = emptyList(),
    /** Non-null while a secure pairing handshake with a device is in progress. */
    val devicePairing: com.michelle.raouf.fetch_jet.feature.device_sync.PairingState? = null,
    /** Non-null while a send is in progress / just finished. */
    val deviceSending: com.michelle.raouf.fetch_jet.feature.device_sync.SendState? = null,
    val deviceReceiving: com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState? = null
)

/** Which named quick speed preset a setting refers to. HIGH is always Unlimited (not editable). */
enum class SpeedPreset { LOW, MEDIUM, FAST, HIGH }


sealed interface DashboardUiEvent {

    data object OnDownloadYtDlpClicked : DashboardUiEvent

    data class OnEngineSelected(val engine: EngineType) : DashboardUiEvent
    data class OnUrlChanged(val url: String) : DashboardUiEvent

    data class OnDestinationPathChanged(val path: String) : DashboardUiEvent
    data class OnFileNameChanged(val name: String) : DashboardUiEvent
    data class OnChecksumChanged(val value: String) : DashboardUiEvent

    data class OnDisableTorrentUploadToggled(val disable: Boolean) : DashboardUiEvent

    data class OnDownloadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnUploadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnGlobalDownloadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnGlobalUploadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnSpeedDisplayUnitChanged(val unit: String) : DashboardUiEvent

    /** Edit one named quick preset's speed (Settings > Speed). */
    data class OnSpeedPresetChanged(val preset: SpeedPreset, val bytes: Long, val unit: String) :
        DashboardUiEvent

    data object OnApplySettingsClicked : DashboardUiEvent

    data class OnToggleAddDialog(val show: Boolean) : DashboardUiEvent
    data class OnClipStartTimeChanged(val time: String) : DashboardUiEvent
    data class OnClipEndTimeChanged(val time: String) : DashboardUiEvent
    data class OnToggleClipRange(val enabled: Boolean) : DashboardUiEvent
    data class OnToggleClipFastMode(val enabled: Boolean) : DashboardUiEvent
    data class OnAudioConvertFormatSelected(val format: String?) : DashboardUiEvent
    data class OnPlaylistEntryToggled(val index: Int, val selected: Boolean) : DashboardUiEvent
    data class OnPlaylistSelectAll(val selected: Boolean) : DashboardUiEvent
    data class OnPlaylistFolderChanged(val name: String) : DashboardUiEvent
    data class OnPlaylistQualityChanged(val quality: com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality) :
        DashboardUiEvent

    /** "Download only this video" for a watch?v=...&list=... link. */
    data object OnPlaylistUseSingleVideo : DashboardUiEvent
    data class OnLinkListEntryToggled(val index: Int, val selected: Boolean) : DashboardUiEvent
    data class OnLinkListSelectAll(val selected: Boolean) : DashboardUiEvent
    data class OnLinkListFolderChanged(val name: String) : DashboardUiEvent
    data class OnLinkListTorrentFileToggled(
        val linkIndex: Int,
        val fileIndex: Int,
        val selected: Boolean
    ) : DashboardUiEvent

    data class OnLinkListTorrentFilePriority(
        val linkIndex: Int,
        val fileIndex: Int,
        val priority: Int
    ) : DashboardUiEvent

    data class OnLinkListTorrentSelectAll(val linkIndex: Int, val selected: Boolean) :
        DashboardUiEvent

    data class OnLinkListQualitySelected(val linkIndex: Int, val qualityIndex: Int) :
        DashboardUiEvent

    data class OnLinkListRetryInspect(val linkIndex: Int) : DashboardUiEvent

    data class OnExportLinksClicked(val taskIds: List<String>) : DashboardUiEvent
    data class OnScheduleChanged(val epochMs: Long) : DashboardUiEvent
    data class OnConnectionsChanged(val connections: Int) : DashboardUiEvent
    data class OnDefaultConnectionsChanged(val connections: Int) : DashboardUiEvent
    data object OnPauseAllClicked : DashboardUiEvent
    data object OnResumeAllClicked : DashboardUiEvent

    data object OnThemeToggled : DashboardUiEvent

    /** "system", "light" or "dark". */
    data class OnThemeModeChanged(val mode: String) : DashboardUiEvent
    data class OnLanguageChanged(val choice: String) : DashboardUiEvent

    data object OnAddDownloadClicked : DashboardUiEvent

    data class OnPauseClicked(val taskId: String) : DashboardUiEvent

    data class OnResumeClicked(val taskId: String) : DashboardUiEvent

    data class OnCancelClicked(val taskId: String) : DashboardUiEvent

    data class OnRemoveClicked(val taskId: String) : DashboardUiEvent

    data class OnRemoveSelectedClicked(val taskIds: List<String>) : DashboardUiEvent

    data class OnConfirmRemoveClicked(val deleteFile: Boolean) : DashboardUiEvent

    data object OnConfirmCancelClicked : DashboardUiEvent

    data object OnDismissCancelDialog : DashboardUiEvent

    data object OnDismissRemoveDialog : DashboardUiEvent
    data object OnResumeExistingDuplicateClicked : DashboardUiEvent
    data object OnRestartDuplicateClicked : DashboardUiEvent
    data object OnDismissDuplicateDialog : DashboardUiEvent
    data object OnConfirmOverwriteClicked : DashboardUiEvent
    data class OnSaveAsCopyClicked(val newFileName: String) : DashboardUiEvent
    data object OnDismissFileConflictDialog : DashboardUiEvent

    data class OnTabSelected(val tab: DashboardTab) : DashboardUiEvent
    data class OnSortChanged(val sort: TaskSortOption) : DashboardUiEvent
    data object OnConfirmBrowserFallback : DashboardUiEvent
    data object OnDismissBrowserFallback : DashboardUiEvent
    data class OnClipboardLink(val url: String) : DashboardUiEvent
    data object OnClipboardOfferAccepted : DashboardUiEvent
    data object OnClipboardOfferDismissed : DashboardUiEvent
    data class OnMonitorClipboardChanged(val on: Boolean) : DashboardUiEvent
    data class OnClipboardVideoPagesChanged(val on: Boolean) : DashboardUiEvent
    data class OnSortIntoFoldersChanged(val on: Boolean) : DashboardUiEvent

    data object OnInspectTorrentClicked : DashboardUiEvent

    data object OnInspectMediaClicked : DashboardUiEvent

    data class OnMediaQualitySelectionToggled(val qualityIndex: Int, val isSelected: Boolean) :

        DashboardUiEvent


    data class OnSelectAllMediaQualitiesToggled(val selectAll: Boolean) :

        DashboardUiEvent

    data class OnSubtitleToggled(val langCode: String, val isSelected: Boolean) :
        DashboardUiEvent

    data class OnToggleEmbedSubtitles(val embed: Boolean) :
        DashboardUiEvent

    data class OnTorrentFileSelectionToggled(val fileIndex: Int, val isSelected: Boolean) :

        DashboardUiEvent


    data class OnTorrentFilePriorityChanged(val fileIndex: Int, val priority: Int) :

        DashboardUiEvent


    data class OnSelectAllTorrentFilesToggled(val selectAll: Boolean) : DashboardUiEvent


    data class OnTaskTorrentFileSelectionToggled(

        val taskId: String,

        val fileIndex: Int,

        val isSelected: Boolean

    ) :

        DashboardUiEvent


    data class OnTaskTorrentFilePriorityChanged(

        val taskId: String,

        val fileIndex: Int,

        val priority: Int

    ) :

        DashboardUiEvent


    data class OnTaskPriorityChanged(

        val taskId: String,

        val priority: Int

    ) :

        DashboardUiEvent

    /** Open the "Send to device" sheet for a task. */
    data class OnShareTaskClicked(val taskId: String) : DashboardUiEvent

    /** Close the "Send to device" sheet. */
    data object OnDismissShareSheet : DashboardUiEvent

    /** Send the shared task to a chosen device (pairs first if needed). */
    data class OnSendToDeviceClicked(
        val taskId: String,
        val deviceId: String,
        val mode: com.michelle.raouf.fetch_jet.feature.device_sync.SendMode,
        val deleteAfterSuccess: Boolean
    ) : DashboardUiEvent

    /** User confirmed the 6-digit pairing code matches on both devices. */
    data object OnConfirmPairingClicked : DashboardUiEvent

    /** User cancelled / rejected the pairing. */
    data object OnCancelPairingClicked : DashboardUiEvent

    /** Forget a previously paired device. */
    data class OnUnpairDeviceClicked(val deviceId: String) : DashboardUiEvent

    /** Block the device asking to pair now. */
    data object OnBlockPairingClicked : DashboardUiEvent

    data object OnDismissReceiveClicked : DashboardUiEvent

    /** Allow a blocked device to ask again. */
    data class OnUnblockDeviceClicked(val deviceId: String) : DashboardUiEvent

    /** Pair by typing the other device's IP (for networks that block UDP discovery). */
    data class OnManualConnectClicked(val ip: String) : DashboardUiEvent

}


sealed interface DashboardUiEffect {

    data class ShowToast(val text: String) : DashboardUiEffect

}
