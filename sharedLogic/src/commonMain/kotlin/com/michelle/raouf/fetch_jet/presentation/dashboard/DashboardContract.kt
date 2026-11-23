package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.feature.site_grabber.LinkKind


import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.anyVideoSelected
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
    val mediaQualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = emptyList(),
    /** The subtitle tracks this video offers (read with its qualities). */
    val subtitles: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack> = emptyList(),
    /** A file found on a web page (Find files on this page): its name, size (-1 = unknown), type folder and the page it came from. */
    val fileName: String? = null,
    val sizeBytes: Long = -1L,
    val folder: String = "",
    val sourcePage: String = "",
    val downloadedBefore: Boolean = false
) {
    /** A torrent's files or a video's qualities are still being read: it cannot be started yet. */
    val isBeingInspected: Boolean get() = isInspecting || isInspectingMedia

    /** A video whose qualities could not be read: the user retries it or unchecks it. */
    val needsRetry: Boolean get() = engine == EngineType.MEDIA_STREAM && mediaInspectFailed
}

/** Find files on this page: how far to read, progress and the note shown after it. */
data class SiteGrabUi(
    val depth: Int = 1,
    val running: Boolean = false,
    val pagesRead: Int = 0,
    val note: String = "",
    val title: String = "",
    val hiddenKinds: Set<String> = emptySet()
)

/** One type of file among the files found on a page (Video, Images...), how many, and whether its type filter is on (its rows are listed). */
data class GrabGroup(val folder: String, val count: Int, val selected: Boolean)

data class FileConflictData(
    val fileName: String,
    val destinationPath: String,
    val suggestedNewName: String
)

data class DashboardUiState(

    val inputUrl: String = "",

    val destinationPath: String = "",

    val addFolderRuleSite: String = "",

    val archivePassword: ArchivePasswordAsk? = null,

    val disableTorrentUpload: Boolean = false,

    val isDarkMode: Boolean = true,
    /** "system", "light" or "dark"; the UI resolves "system" from the device. */
    val themeMode: String = "system",

    val isAddDialogVisible: Boolean = false,

    val allTasks: List<DownloadTask> = emptyList(),

    val hidden: HiddenUiState = HiddenUiState(),

    val backup: BackupUiState = BackupUiState(),

    val addHidden: Boolean = false,

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
    val testLink: TestLinkUi? = null,

    val inspectedMediaQualities: List<com.michelle.raouf.fetch_jet.domain.model.MediaQualityEntry> = emptyList(),
    val inspectedSubtitles: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack> = emptyList(),
    val embedSubtitles: Boolean = false,
    val subtitleFormat: String = "srt",

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
    val extractArchives: Boolean = false,

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
    val siteGrab: SiteGrabUi = SiteGrabUi(),
    val linkKind: LinkKind = LinkKind.UNKNOWN,
    val linkProbeUrl: String = "",
    val playlistQuality: com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality =
        com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality.BEST,
    /** The playlist is shown (and queued) from its oldest video. */
    val playlistOldestFirst: Boolean = false,
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
    val deviceReceiving: com.michelle.raouf.fetch_jet.feature.device_sync.ReceiveState? = null,
    val audioCompress: AudioCompressUi? = null,
    val subtitlesPage: SubtitlesUi? = null,
    /** Saved playlists (shown as one row each in the downloads list). */
    val playlists: Map<String, com.michelle.raouf.fetch_jet.feature.playlist_group.PlaylistRecord> = emptyMap(),
    /** The playlist page that is open (its videos instead of the whole list); null = none. */
    val openPlaylistId: String? = null,
    /** The Add dialog was opened by "Check" of this playlist: new videos join it. */
    val checkingPlaylistId: String? = null,
    /** The playlist page's subtitle languages part. */
    val playlistLanguages: PlaylistLanguagesUi? = null,
    /** Settings > Downloads: the list shows each destination folder as one row. */
    val groupByFolder: Boolean = false,
    val videoPlaysEverywhere: Boolean = false,
    /** The folder page that is open (its downloads instead of the whole list); a folder key, null = none. */
    val openFolder: String? = null,
    val torrentDetails: TorrentDetailsUi? = null,
    val audioCopyCounts: Map<String, Int> = emptyMap()
) {
    /** The link box holds one web page address (not a file, video or magnet): the link is worth probing for "Find files on this page". */
    val grabShape: Boolean
        get() = isAddDialogVisible && linkList.all { it.folder.isNotBlank() } && inspectedPlaylist == null &&
                (detectedEngine == EngineType.HTTP_DIRECT || (detectedEngine == EngineType.MEDIA_STREAM && com.michelle.raouf.fetch_jet.feature.queue_manager.LearnedMediaHosts.knows(com.michelle.raouf.fetch_jet.feature.queue_manager.LearnedMediaHosts.hostOf(inputUrl)))) && inputUrl.trim().let { u ->
            (u.startsWith("http://", true) || u.startsWith("https://", true)) && u.none { it.isWhitespace() } &&
                    !com.michelle.raouf.fetch_jet.feature.site_grabber.PageLinks.isFileUrl(u)
        }

    /** What the probe of the link in the box found; [LinkKind.UNKNOWN] while it has not answered for this exact link. */
    val probedAs: LinkKind
        get() = if (linkProbeUrl.isNotEmpty() && linkProbeUrl == inputUrl.trim()) linkKind else LinkKind.UNKNOWN

    /** The link looks like a web page and its probe is still running: neither the scan card nor a wrong state is shown yet. */
    val checkingLink: Boolean
        get() = grabShape && probedAs == LinkKind.UNKNOWN && siteGrab.running.not() && linkList.isEmpty()

    /** The link box holds a web page (the probe says so): "Find files on this page" is offered. */
    val canGrabPage: Boolean
        get() = grabShape && detectedEngine == EngineType.HTTP_DIRECT && probedAs.let { it == LinkKind.PAGE || it == LinkKind.VIDEO_PAGE || it == LinkKind.UNSURE }

    /** The files found on a page are being read or listed: the Direct / Media choice has nothing to say there. */
    val showsEngineChoice: Boolean
        get() = !siteGrab.running && !(linkList.isNotEmpty() && linkList.all { it.folder.isNotBlank() })

    /**
     * The engine is picked from the link itself; the Direct / Media choice (never Torrent) is asked only when the
     * link is a web page that may hold a video, or the check could not tell what it is.
     */
    val engineChoiceShown: Boolean
        get() = showsEngineChoice && linkList.isEmpty() && detectedEngine != EngineType.BITTORRENT &&
                com.michelle.raouf.fetch_jet.core.edition.AppFeatures.mediaExtractor &&
                probedAs.let { it == LinkKind.VIDEO_PAGE || it == LinkKind.UNSURE || it == LinkKind.PAGE }

    /** Reading depth is a choice only for pages crawled link by link (a release page of a known site is read directly). */
    val grabDepthShown: Boolean
        get() = com.michelle.raouf.fetch_jet.feature.site_grabber.SiteAdapters.forUrl(inputUrl) == null

    /** The files found on the page, grouped by type, in the order of the type chips. */
    val grabGroups: List<GrabGroup>
        get() = linkList.filter { it.folder.isNotBlank() }.groupBy { it.folder }.map { (folder, items) ->
            GrabGroup(folder, items.size, folder !in siteGrab.hiddenKinds)
        }.sortedBy { com.michelle.raouf.fetch_jet.core.settings.FileCategory.allFolders.indexOf(it.folder).let { i -> if (i < 0) 99 else i } }

    /** The listed rows with their place in [linkList]: the rows of a type the user turned off are left out. */
    val visibleLinks: List<IndexedValue<LinkListEntry>>
        get() = linkList.withIndex().filter { !isKindHidden(it.value) }

    fun isKindHidden(entry: LinkListEntry): Boolean = entry.folder.isNotBlank() && entry.folder in siteGrab.hiddenKinds

    /** A type chip: off hides its rows and unchecks them, on lists them again, checked except the ones downloaded before. */
    fun withKindShown(folder: String, shown: Boolean): DashboardUiState = copy(
        siteGrab = siteGrab.copy(hiddenKinds = if (shown) siteGrab.hiddenKinds - folder else siteGrab.hiddenKinds + folder),
        linkList = linkList.map { if (it.folder == folder) it.copy(isSelected = shown && !it.downloadedBefore) else it }
    )

    fun withLinkChecked(index: Int, checked: Boolean): DashboardUiState = copy(
        linkList = linkList.mapIndexed { i, entry ->
            if (i == index && !entry.downloadedBefore && !isKindHidden(entry)) entry.copy(isSelected = checked) else entry
        }
    )

    fun withAllLinksChecked(checked: Boolean): DashboardUiState = copy(
        linkList = linkList.map { if (it.downloadedBefore || isKindHidden(it)) it else it.copy(isSelected = checked) }
    )

    /** Files found while the page is still being read: a file of a type that is turned off arrives hidden and unchecked. */
    fun withGrabbedFiles(added: List<LinkListEntry>): DashboardUiState =
        copy(linkList = linkList + added.map { if (isKindHidden(it)) it.copy(isSelected = false) else it })

    val subtitlesOffered: Boolean
        get() = inspectedPlaylist.let { list ->
            if (linkList.isNotEmpty()) {
                linkList.any {
                    it.isSelected && it.engine == EngineType.MEDIA_STREAM &&
                            (it.isBeingInspected || it.mediaQualities.anyVideoSelected())
                } && (inspectedSubtitles.isNotEmpty() || subtitlesListReading || subtitlesListRead)
            } else if (list != null) {
                !list.fromBrowser && list.entries.any { it.isSelected } &&
                        playlistQuality != com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistQuality.AUDIO
            } else inspectedSubtitles.isNotEmpty() && inspectedMediaQualities.anyVideoSelected()
        }

    /** A playlist / link list whose videos are still being read (their subtitle languages too). */
    val subtitlesListReading: Boolean
        get() = if (linkList.isNotEmpty()) linkList.any { it.isSelected && it.isBeingInspected }
        else inspectedPlaylist?.let { pl -> !pl.fromBrowser && pl.entries.any { !it.unavailable && it.size == -1L } } == true

    private val subtitlesListRead: Boolean
        get() = linkList.any { it.isSelected && it.engine == EngineType.MEDIA_STREAM && it.mediaQualities.isNotEmpty() }

    /** Under the list's subtitles: "reading N of M" while reading, "none" when no video has any. */
    val subtitlesListNote: String
        get() {
            if (linkList.isEmpty() && inspectedPlaylist == null) return ""
            val s = com.tbib.kmp_localization.LocalizationManager.Strings
            if (subtitlesListReading) {
                val pl = inspectedPlaylist
                if (linkList.isEmpty() && pl != null) {
                    val readable = pl.entries.filter { !it.unavailable }
                    return s.subtitlesReadingCount.replace("%1", readable.count { it.size != -1L }.toString())
                        .replace("%2", readable.size.toString())
                }
                return s.subtitlesReadingCount.replace("%1", linkList.count { it.isSelected && !it.isBeingInspected }.toString())
                    .replace("%2", linkList.count { it.isSelected }.toString())
            }
            return if (inspectedSubtitles.isEmpty()) s.subtitlesNoneInList else ""
        }

    /** "Oldest first" means that only for a channel's videos, which yt-dlp lists newest first. */
    val playlistOldestFirstOffered: Boolean
        get() = inspectedPlaylist?.fromBrowser == false &&
                com.michelle.raouf.fetch_jet.feature.media_extractor.isChannelVideosUrl(inputUrl)
    val pageScanShown: Boolean
        get() = canGrabPage || siteGrab.running

    val needsFileName: Boolean
        get() = !pageScanShown && addNeedsFileName(detectedEngine, inputUrl, suggestedFileName, fileNameOverride, linkList.isNotEmpty() || inspectedPlaylist != null)

    val fileNameShown: Boolean
        get() = linkList.isEmpty() && inspectedPlaylist == null && !pageScanShown &&
                (suggestedFileName.isNotBlank() || fileNameOverride.isNotBlank() || needsFileName)
}

/** The torrent tab of a task's details: refreshed every 2 s while it is open. */
data class TorrentDetailsUi(
    val taskId: String,
    val supported: Boolean = true,
    val loading: Boolean = true,
    val details: com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentDetails =
        com.michelle.raouf.fetch_jet.feature.torrent_engine.TorrentDetails(),
    /** uploaded / downloaded, 0 when nothing was downloaded yet. */
    val ratio: Float = 0f,
    /** The share ratio at which uploading stops (0 = never), from BehaviorSettings.torrentStopRatio. */
    val stopRatio: Float = 0f,
    /** English code of the last tracker-add failure ("invalid", "refused") or empty. */
    val trackerError: String = ""
)

/**
 * The playlist page's "Add a subtitle language": [available] are the languages read from its
 * videos ([reading] while that runs); [adding] while a new language is fetched for the finished
 * videos ([done] of [total]).
 */
data class PlaylistLanguagesUi(
    val playlistId: String,
    val reading: Boolean = false,
    val available: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack> = emptyList(),
    val adding: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val message: String = "",
)

data class SubtitlesUi(
    val taskId: String,
    val fileName: String,
    val embed: Boolean,
    val format: String = "srt",
    val reading: Boolean = true,
    val readFailed: Boolean = false,
    val tracks: List<com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack> = emptyList(),
    val present: Set<String> = emptySet(),
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val added: List<String> = emptyList(),
    val refused: List<String> = emptyList(),
    val failed: List<String> = emptyList()
) {
    val canAdd: Boolean
        get() = !reading && !running && tracks.any { it.isSelected && subtitleKey(it) !in present }
}

fun subtitleKey(track: com.michelle.raouf.fetch_jet.domain.model.SubtitleTrack): String =
    track.langCode + if (track.isAuto) ".auto" else ""

data class AudioCompressUi(
    val taskId: String,
    val fileName: String,
    val originalBytes: Long,
    val durationMs: Long = 0L,
    val reading: Boolean = true,
    val settings: com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressSettings =
        com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressSettings(),
    val estimatedBytes: Long = 0L,
    val running: Boolean = false,
    val progress: Float = 0f,
    val failed: Boolean = false,
    val copies: List<com.michelle.raouf.fetch_jet.feature.audio_compress.CompressedCopy> = emptyList(),
    val formats: List<String> = listOf(com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress.OUTPUT_EXTENSION)
) {
    val format: String
        get() = com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress.formatFor(settings, fileName, formats)
    val firstLevel: Int
        get() = com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress.firstShrinkingLevel(originalBytes, durationMs)
    val bitrateChoices: List<Int>
        get() = com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress
            .shrinkingBitrates(originalBytes, durationMs, settings)
    val nothingToGain: Boolean
        get() = !reading && durationMs > 0L &&
                firstLevel > com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompress.LEVELS.size
}

/** Which named quick speed preset a setting refers to. HIGH is always Unlimited (not editable). */
enum class SpeedPreset { LOW, MEDIUM, FAST, HIGH }


sealed interface DashboardUiEvent {

    data object OnDownloadYtDlpClicked : DashboardUiEvent

    data class OnEngineSelected(val engine: EngineType) : DashboardUiEvent
    data class OnUrlChanged(val url: String) : DashboardUiEvent

    data class OnDestinationPathChanged(val path: String) : DashboardUiEvent
    data class OnFileNameChanged(val name: String) : DashboardUiEvent
    data class OnChecksumChanged(val value: String) : DashboardUiEvent
    data object OnTestLinkClicked : DashboardUiEvent

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
    data class OnPlaylistOldestFirst(val on: Boolean) : DashboardUiEvent
    data class OnLinkListEntryToggled(val index: Int, val selected: Boolean) : DashboardUiEvent
    data class OnLinkListSelectAll(val selected: Boolean) : DashboardUiEvent
    data class OnLinkListFolderChanged(val name: String) : DashboardUiEvent
    data object OnGrabPageClicked : DashboardUiEvent
    data object OnGrabCancelClicked : DashboardUiEvent
    data class OnGrabDepthChanged(val depth: Int) : DashboardUiEvent
    data class OnGrabGroupToggled(val folder: String, val selected: Boolean) : DashboardUiEvent
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
    data class OnExtractArchivesChanged(val on: Boolean) : DashboardUiEvent

    data object OnInspectTorrentClicked : DashboardUiEvent

    data object OnInspectMediaClicked : DashboardUiEvent

    data class OnMediaQualitySelectionToggled(val qualityIndex: Int, val isSelected: Boolean) :

        DashboardUiEvent


    data class OnSelectAllMediaQualitiesToggled(val selectAll: Boolean) :

        DashboardUiEvent

    data class OnSubtitleToggled(val langCode: String, val isSelected: Boolean) :
        DashboardUiEvent

    data class OnSubtitleFormatSelected(val format: String) : DashboardUiEvent

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

    data class OnAudioCompressClicked(val taskId: String) : DashboardUiEvent
    data class OnSubtitlesClicked(val taskId: String) : DashboardUiEvent
    data class OnSubtitleTrackToggled(val key: String, val selected: Boolean) : DashboardUiEvent
    data object OnSubtitlesAddClicked : DashboardUiEvent
    data object OnDismissSubtitles : DashboardUiEvent
    data class OnOpenPlaylist(val playlistId: String) : DashboardUiEvent
    data object OnClosePlaylist : DashboardUiEvent
    data class OnPlaylistGroupPause(val playlistId: String) : DashboardUiEvent
    data class OnPlaylistGroupResume(val playlistId: String) : DashboardUiEvent
    data class OnPlaylistGroupRemove(val playlistId: String) : DashboardUiEvent
    data class OnPlaylistCheck(val playlistId: String) : DashboardUiEvent
    data class OnPlaylistReadLanguages(val playlistId: String) : DashboardUiEvent
    data class OnPlaylistAddLanguage(val playlistId: String, val langCode: String) : DashboardUiEvent
    data object OnPlaylistLanguagesDismiss : DashboardUiEvent
    data class OnGroupByFolderChanged(val on: Boolean) : DashboardUiEvent
    data class OnVideoPlaysEverywhereChanged(val on: Boolean) : DashboardUiEvent
    data class OnOpenFolderGroup(val folder: String) : DashboardUiEvent
    data object OnCloseFolderGroup : DashboardUiEvent
    data class OnFolderGroupPause(val folder: String) : DashboardUiEvent
    data class OnFolderGroupResume(val folder: String) : DashboardUiEvent
    data class OnFolderGroupRemove(val folder: String) : DashboardUiEvent
    data class OnExtractTaskClicked(val taskId: String) : DashboardUiEvent

    data class OnCopyTaskLink(val taskId: String) : DashboardUiEvent

    data class OnHideTargets(
        val taskIds: List<String> = emptyList(),
        val playlistIds: List<String> = emptyList(),
        val folders: List<String> = emptyList(),
        val lockAfter: Boolean = false
    ) : DashboardUiEvent

    data object OnHiddenNoticeShown : DashboardUiEvent
    data class OnAddHiddenToggled(val on: Boolean) : DashboardUiEvent
    data object OnHiddenAddDownload : DashboardUiEvent
    data class OnUnhideTasks(val taskIds: List<String>) : DashboardUiEvent
    data object OnUnhideAll : DashboardUiEvent
    data class OnHiddenOpenPlaylist(val playlistId: String) : DashboardUiEvent
    data class OnHiddenOpenFolder(val folder: String) : DashboardUiEvent
    data object OnHiddenCloseGroup : DashboardUiEvent
    data class OnUnhideGroup(val playlistId: String = "", val folder: String = "") : DashboardUiEvent
    data class OnHiddenGroupPause(val playlistId: String = "", val folder: String = "") : DashboardUiEvent
    data class OnHiddenGroupResume(val playlistId: String = "", val folder: String = "") : DashboardUiEvent
    data class OnHiddenSetPin(val pin: String) : DashboardUiEvent
    data class OnHiddenUnlock(val pin: String) : DashboardUiEvent
    data object OnHiddenLock : DashboardUiEvent
    data class OnHiddenChangePin(val oldPin: String, val newPin: String) : DashboardUiEvent
    data class OnHiddenRemoveLock(val pin: String) : DashboardUiEvent
    data class OnHiddenOpenFile(val taskId: String) : DashboardUiEvent
    data object OnHiddenClosePlayer : DashboardUiEvent
    data object OnHiddenEncryptNoticeAccepted : DashboardUiEvent
    data object OnHiddenEncryptNoticeDismissed : DashboardUiEvent
    data object OnHiddenShowRecoveryCode : DashboardUiEvent
    data object OnHiddenRecoveryCodeShown : DashboardUiEvent
    data class OnHiddenRestoreWithCode(val code: String) : DashboardUiEvent
    data object OnHiddenForgotPin : DashboardUiEvent
    data class OnHiddenResetPin(val pin: String) : DashboardUiEvent
    data object OnHiddenResetCancel : DashboardUiEvent
    data object OnBackupExportRequested : DashboardUiEvent
    data object OnBackupWritten : DashboardUiEvent
    data class OnBackupRestoreText(val text: String) : DashboardUiEvent
    data object OnBackupNoticeShown : DashboardUiEvent

    data class OnArchivePasswordEntered(val taskId: String, val password: String) : DashboardUiEvent

    data object OnArchivePasswordDismissed : DashboardUiEvent
    data class OnTorrentDetailsOpened(val taskId: String) : DashboardUiEvent
    data object OnTorrentDetailsClosed : DashboardUiEvent
    data class OnTorrentAddTracker(val taskId: String, val url: String) : DashboardUiEvent
    data class OnTorrentRecheck(val taskId: String) : DashboardUiEvent
    data class OnTorrentFirstLastChanged(val taskId: String, val on: Boolean) : DashboardUiEvent
    data class OnTorrentCopyMagnet(val taskId: String) : DashboardUiEvent
    data class OnTorrentStopRatioChanged(val ratio: Float) : DashboardUiEvent

    data class OnAudioCompressSettingsChanged(
        val settings: com.michelle.raouf.fetch_jet.feature.audio_compress.AudioCompressSettings
    ) : DashboardUiEvent

    data object OnAudioCompressStartClicked : DashboardUiEvent

    data class OnAudioCompressDeleteCopy(val path: String) : DashboardUiEvent

    data object OnDismissAudioCompress : DashboardUiEvent

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

    data class CopyText(val text: String) : DashboardUiEffect

    data class OpenHiddenFile(val path: String) : DashboardUiEffect

}

data class ArchivePasswordAsk(val taskId: String, val name: String, val wrong: Boolean)
