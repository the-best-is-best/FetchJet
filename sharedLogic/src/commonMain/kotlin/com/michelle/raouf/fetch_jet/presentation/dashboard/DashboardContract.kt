package com.michelle.raouf.fetch_jet.presentation.dashboard


import com.michelle.raouf.fetch_jet.domain.model.DownloadTask

import com.michelle.raouf.fetch_jet.domain.model.EngineType

import com.michelle.raouf.fetch_jet.domain.model.TorrentFileEntry


enum class DashboardTab(val title: String) {

    ALL("All Downloads"),

    ACTIVE("Active"),

    COMPLETED("Completed"),

    TORRENT("BitTorrent"),

    MEDIA("Media Extractor"),

    SETTINGS("Settings")

}


enum class TaskSortOption(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    NAME_ASC("Name A–Z")
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

    val inspectedMediaTitle: String? = null,

    val inspectedMediaQualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = emptyList(),

    val inspectMediaError: String? = null,

    val removeConfirmationTaskId: String? = null,
    /** A browser-sent file link the user just dismissed; asks "download it in the browser instead?" */
    val browserFallbackUrl: String? = null,

    val cancelConfirmationTaskId: String? = null,
    val duplicateTaskAlert: DownloadTask? = null,
    val fileConflictAlert: FileConflictData? = null,

    val selectedDownloadSpeedLimitBytes: Long = 0L,

    val selectedUploadSpeedLimitBytes: Long = 0L,

    val globalDownloadSpeedLimitBytes: Long = 0L,

    val globalUploadSpeedLimitBytes: Long = 0L,

    val ytDlpStatus: String = "",

    val isDownloadingYtDlp: Boolean = false,

    val isYtDlpInstalled: Boolean = true,

    val isFfmpegInstalled: Boolean = true,

    val toolsDirectoryPath: String = "",
    val torrentEngineStatus: String = "",
    val isTorrentEngineReady: Boolean = true,
    val clipStartTime: String = "",
    val clipEndTime: String = "",
    val isClipRangeEnabled: Boolean = false,
    /** Clip: download the whole video at full speed and cut it afterwards (uses more data). */
    val clipFastMode: Boolean = false,
    /** A playlist / channel link: its videos (null = a single video). */
    val inspectedPlaylist: com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist? = null,
    val playlistQuality: com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality =
        com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality.BEST,
    /** Start the new download(s) at this time (epoch ms); 0 = now. */
    val scheduleStartMs: Long = 0L,
    val mediaDurationSeconds: Long = 0L,
    val speedDisplayUnit: String = "MB/s"
)


sealed interface DashboardUiEvent {

    data object OnDownloadYtDlpClicked : DashboardUiEvent

    data class OnEngineSelected(val engine: EngineType) : DashboardUiEvent
    data class OnUrlChanged(val url: String) : DashboardUiEvent

    data class OnDestinationPathChanged(val path: String) : DashboardUiEvent

    data class OnDisableTorrentUploadToggled(val disable: Boolean) : DashboardUiEvent

    data class OnDownloadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnUploadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnGlobalDownloadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnGlobalUploadSpeedLimitChanged(val limitBytesPerSec: Long) : DashboardUiEvent

    data class OnSpeedDisplayUnitChanged(val unit: String) : DashboardUiEvent

    data object OnApplySettingsClicked : DashboardUiEvent

    data class OnToggleAddDialog(val show: Boolean) : DashboardUiEvent
    data class OnClipStartTimeChanged(val time: String) : DashboardUiEvent
    data class OnClipEndTimeChanged(val time: String) : DashboardUiEvent
    data class OnToggleClipRange(val enabled: Boolean) : DashboardUiEvent
    data class OnToggleClipFastMode(val enabled: Boolean) : DashboardUiEvent
    data class OnPlaylistEntryToggled(val index: Int, val selected: Boolean) : DashboardUiEvent
    data class OnPlaylistSelectAll(val selected: Boolean) : DashboardUiEvent
    data class OnPlaylistQualityChanged(val quality: com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality) :
        DashboardUiEvent

    /** "Download only this video" for a watch?v=...&list=... link. */
    data object OnPlaylistUseSingleVideo : DashboardUiEvent
    data class OnScheduleChanged(val epochMs: Long) : DashboardUiEvent
    data object OnPauseAllClicked : DashboardUiEvent
    data object OnResumeAllClicked : DashboardUiEvent

    data object OnThemeToggled : DashboardUiEvent

    data object OnAddDownloadClicked : DashboardUiEvent

    data class OnPauseClicked(val taskId: String) : DashboardUiEvent

    data class OnResumeClicked(val taskId: String) : DashboardUiEvent

    data class OnCancelClicked(val taskId: String) : DashboardUiEvent

    data class OnRemoveClicked(val taskId: String) : DashboardUiEvent

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

    data object OnInspectTorrentClicked : DashboardUiEvent

    data object OnInspectMediaClicked : DashboardUiEvent

    data class OnMediaQualitySelectionToggled(val qualityIndex: Int, val isSelected: Boolean) :

        DashboardUiEvent


    data class OnSelectAllMediaQualitiesToggled(val selectAll: Boolean) :

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

}


sealed interface DashboardUiEffect {

    data class ShowToast(val text: String) : DashboardUiEffect

}
