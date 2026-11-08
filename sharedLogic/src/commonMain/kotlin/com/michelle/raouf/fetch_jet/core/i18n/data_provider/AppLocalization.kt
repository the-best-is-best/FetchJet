package com.michelle.raouf.fetch_jet.core.i18n.data_provider

import com.tbib.kmp_localization.LocalizationManager
import com.tbib.kmp_localization.LocalizationProvider

interface AppLocalization : LocalizationProvider {
    val languageName: String
    val ok: String
    val cancel: String
    val close: String
    val save: String
    val delete: String
    val remove: String
    val open: String
    val copy: String
    val copied: String
    val retry: String
    val pause: String
    val resume: String
    val apply: String
    val reset: String
    val yes: String
    val no: String
    val back: String
    val done: String
    val selectAll: String
    val selectNone: String
    val language: String
    val languageHint: String
    val systemLanguage: String
    val homeSettings: String
    val homeDownloads: String

    val homeTabAll: String
    val homeTabActive: String
    val homeTabDone: String
    val homeTabTorrent: String
    val homeTabMedia: String
    val homeSidebarTypeHeader: String
    val homeSearchDownloadsPlaceholder: String
    val homeAdd: String
    val homeAddDownload: String
    val homePauseAll: String
    val homeResumeAll: String
    fun homeSortBy(option: String): String
    val homeLightTheme: String
    val homeDarkTheme: String

    fun homeUpdatingMediaTools(tool: String, percent: Int?): String
    val homeSearchDownloadsByNamePlaceholder: String
    fun homeNoDownloadsMatch(query: String): String
    val homeNoTasksInView: String
    val homeTryAnotherName: String
    val homeAddDownloadToGetStarted: String
    fun homeDownloadingWaitingSummary(running: Int, waiting: Int): String
    fun homeQueueStatus(maxAtOnce: Int): String
    fun homeSpeedLimitStatus(limit: String?): String
    val homeGroupDownloading: String
    val homeGroupWaiting: String
    val homeGroupPausedFailed: String
    val homeGroupCompleted: String
    fun homeSelectedCount(count: Int): String
    val homeExport: String

    fun homeStartsAt(time: String): String
    val homeWaitingInQueue: String
    fun homeWaitingInQueuePosition(position: Int): String
    val homeConnecting: String
    val homeStarting: String
    fun homeTimeLeft(eta: String): String
    fun homePausedState(percent: Int, restartsOnResume: Boolean): String
    fun homeFailedState(message: String?): String
    fun homeCompletedState(size: String?): String
    val homeStartNow: String
    val homeColumnName: String
    val homeColumnSize: String
    val homeColumnProgress: String
    val homeColumnSpeed: String
    val homeColumnLeft: String
    val homeFilterAllDownloads: String
    val homeFilterDownloading: String
    val homeFilterWaiting: String
    val homeFilterPaused: String
    val homeFilterCompleted: String
    val homeFilterFailed: String
    val homeFilterMedia: String
    val homeFilterFiles: String
    val homeFilterTorrents: String

    val homeCantResumeTitle: String
    val homeCantResumeClipText: String
    val homeCantResumeServerText: String
    val homePauseAnyway: String
    val homeKeepDownloading: String
    val homeMagnetLink: String
    val homeClipboardOfferTitle: String
    val homeClipboardOfferHint: String
    val homeDownload: String
    val homeNotNow: String
    val homeBrowserFallbackTitle: String
    val bridgePairTitle: String
    val bridgePairText: String
    val bridgePairOldText: String
    val bridgePairAllow: String
    val bridgePairBlock: String
    val extConnectionsGroup: String
    val extConnectionsFooter: String
    val extAllowedLabel: String
    val extBlockedLabel: String
    val extRevoke: String
    val extUnblock: String
    val homeBrowserFallbackText: String
    val homeDownloadInBrowser: String
    val homeNoDiscard: String

    val homeQueueTitle: String
    val homeQueueFooter: String
    val homeDownloadsAtSameTime: String
    val homeNoLimit: String
    fun homeAtATime(count: Int): String

    val homePiecesLabel: String
    fun homeSegmentsCount(count: Int): String
    fun homePiecesCount(count: Int): String
    fun homeActiveCount(count: Int): String
    fun homeWaitingCount(count: Int): String
    fun homeDoneCount(count: Int): String
    fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?): String
    fun homeRetryingNote(note: String?): String
    fun clipStartAfterEnd(start: String, end: String): String
    fun clipEndExceedsVideo(end: String, duration: String): String
    fun clipStartExceedsVideo(duration: String): String
    val setStartTimeTitle: String
    val setEndTimeTitle: String
    val addDialogTitle: String
    val addDialogSubtitle: String
    val addLinkLabel: String
    val addImportTxt: String
    val openTorrentFile: String
    val urlPlaceholder: String
    val addPaste: String
    val addDownloadEngine: String
    val engineDirect: String
    val engineMedia: String
    val engineTorrent: String
    val addFileNameLabel: String
    val findingName: String
    val saveDestination: String
    val quickFolderDownloads: String
    val quickFolderDocuments: String
    val quickFolderMedia: String
    val quickFolderDesktop: String
    val addRetryInspect: String
    val addStarting: String
    val addInspecting: String
    val addInspectionFailed: String
    val addEnterUrl: String
    fun scheduleLinks(n: Int): String
    fun downloadLinks(n: Int): String
    val inspectVideoFirst: String
    val inspectTorrentFirst: String
    val invalidTimeRange: String
    fun scheduleVideos(n: Int): String
    fun downloadVideos(n: Int): String
    val addScheduleDownload: String
    val addStartDownload: String
    val availableAudioFormats: String
    val availableQualities: String
    val subtitlesTitle: String
    val subtitlesAuto: String
    val subtitlesEmbedInVideo: String
    val subtitlesSeparateFile: String
    val addSelectAll: String
    val addClearSelection: String
    fun selectedOfAvailable(selected: Int, total: Int): String
    fun qualityFormatLine(format: String, details: String): String
    val inspectingVideoQualities: String
    val inspectingVideoQualitiesHint: String
    fun readingCourseLectures(done: Int, total: Int): String
    val readingCourseLecturesHint: String
    val videoInspectionFailed: String
    val addRetryInspection: String
    val mediaEngineStarting: String
    val mediaEngineNotReady: String
    val mediaEngineRequiresYtDlp: String
    val preparingEngine: String
    val startMediaEngine: String
    val inspectAudioFormats: String
    val inspectVideoQuality: String
    fun clipSectionTitle(isAudio: Boolean): String
    val clipSectionHint: String
    val audioConvertTo: String
    val audioConvertAllTo: String
    val audioOriginal: String
    val fileMissingTitle: String
    fun fileMissingMessage(name: String): String
    val browserNotInstalled: String
    val extensionConnected: String
    val extensionNotAddedYet: String
    val addExtension: String
    fun browserMissingTitle(name: String): String
    fun browserMissingMessage(name: String): String
    fun downloadBrowser(name: String): String
    fun loadUnpackedTitle(name: String): String
    fun loadUnpackedSteps(name: String, page: String): String
    val loadUnpackedUpdates: String
    val copyPath: String
    fun extensionConnectedVersion(version: String): String
    fun extensionNeedsUpdate(found: String, wanted: String): String
    fun extensionOldVersion(wanted: String): String
    val updateExtension: String
    fun storeHasUpdate(version: String): String
    val updateFromAppHint: String
    fun storeWaiting(wanted: String): String
    val openStore: String
    val updateFromApp: String
    fun updateFromGitHubHint(version: String): String
    val updateFromGitHub: String
    val extensionDownloadFailed: String
    fun installFromFileTitle(name: String): String
    fun installFromFileSteps(name: String, file: String): String
    fun openBrowser(name: String): String
    fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String): String
    val clipStartTimeFrom: String
    val clipEndTimeTo: String
    fun fullMediaChip(isAudio: Boolean, duration: String): String
    val clipFirst5Min: String
    val clipFirst15Min: String
    val clipFirst30Min: String
    val clipFirst1Hour: String
    val clipModeTitle: String
    val clipOnlyTitle: String
    val clipOnlyLine1: String
    val clipOnlyLine2: String
    val clipOnlyLine3: String
    fun fullThenCutTitle(isAudio: Boolean): String
    fun fullThenCutLine1(isAudio: Boolean, sizeText: String): String
    val fullThenCutLine2: String
    val fullThenCutLine3: String
    val fullThenCutLine4: String
    val inspectingTorrent: String
    val inspectingTorrentHint: String
    val torrentInspectionFailed: String
    val inspectTorrentFiles: String
    val disableTorrentUpload: String
    val torrentFiles: String
    val addDeselectAll: String
    val torrentPriorityHigh: String
    val torrentPriorityLow: String
    val torrentPriorityNormal: String
    fun selectedOfLinks(selected: Int, total: Int): String
    val readingTorrentFiles: String
    fun couldNotReadTorrentFiles(error: String): String
    val linkTagVideo: String
    val linkTagAudio: String
    val linkTagTorrent: String
    val linkTagFile: String
    val linkListMagnet: String
    fun linkListYouTubeVideo(id: String): String
    fun playlistSummary(videos: Int, selected: Int): String
    val qualityForAllVideos: String
    val playlistFolder: String
    val playlistFolderHint: String
    val qualityForAllVideosHint: String
    val downloadOnlyThisVideo: String
    val whenToStart: String
    val scheduleNow: String
    val scheduleIn30Min: String
    val scheduleIn1Hour: String
    val scheduleAtTime: String
    val scheduleDayLabel: String
    val scheduleToday: String
    val scheduleTomorrow: String
    val scheduleIn2Days: String
    val scheduleIn3Days: String
    val scheduleInAWeek: String
    val scheduleTimePassed: String
    val scheduleHourLabel: String
    val scheduleMinuteLabel: String
    val scheduleSet: String
    val scheduleSetTime: String
    fun scheduleStartsAt(time: String): String
    val addConnectionsTitle: String
    val connectionsAuto: String
    fun connectionsAutoWithDefault(n: Int): String
    fun pickerExceedsVideo(duration: String): String
    fun pickerMustBeAfterStart(start: String): String
    val pickerStartZero: String
    val pickerHours: String
    val pickerMinutes: String
    val pickerSeconds: String
    fun pickerSelectedTime(time: String, summary: String): String
    fun pickerEndOfVideo(time: String): String
    fun pickerMin(time: String): String
    fun pickerApply(time: String): String
    val pickerInvalidTime: String
    val dirPickerSelectFolder: String
    val dirPickerUp: String
    fun dirPickerSubfolders(n: Int): String
    val dirPickerHiddenOn: String
    val dirPickerHiddenOff: String
    val dirPickerNewFolder: String
    val dirPickerFolderName: String
    val dirPickerCreateFailed: String
    val dirPickerCreate: String
    val dirPickerNoSubfolders: String
    val dirPickerHiddenTag: String
    val speedLimitUnlimited: String
    val speedPresetsTitle: String
    val speedPresetsFooter: String
    val speedPresetHigh: String
    val speedPresetFast: String
    val speedPresetMedium: String
    val speedPresetLow: String
    val speedScheduleTitle: String
    val speedScheduleFooter: String
    val speedScheduleAdd: String
    val speedScheduleFrom: String
    val speedScheduleTo: String
    val speedScheduleActive: String
    val speedScheduleEmpty: String
    val priorityLabel: String
    val priorityHigh: String
    val priorityNormal: String
    val priorityLow: String
    val checksumLabel: String
    val checksumHint: String
    val checksumVerified: String
    val checksumMismatch: String
    val checksumChecking: String
    val speedLimitNoLimit: String
    val speedLimitCustom: String
    val speedLimitValue: String
    val scheduleAm: String
    val schedulePm: String
    val detailsChipMedia: String
    val detailsChipTorrent: String
    val detailsChipFile: String
    val detailsChipResumable: String
    val detailsChipNoResume: String
    fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean): String
    fun detailsTorrentFiles(selected: Int, total: Int): String
    val detailsPriorityHigh: String
    val detailsPriorityLow: String
    val detailsPriorityNormal: String
    val detailsStatusCompleted: String
    val detailsStatusPaused: String
    val detailsStatusFailed: String
    val detailsStatusWaiting: String
    val detailsStatusQueued: String
    val detailsStatusNotStarted: String
    val detailsUnknownSize: String
    val detailsMergingAudioVideo: String
    fun detailsTorrentSpeedUploadDisabled(download: String): String
    fun detailsTorrentSpeed(download: String, upload: String): String
    val detailsConnecting: String
    fun detailsStartsAt(time: String): String
    fun detailsWaitingInQueuePosition(position: Int): String
    val detailsWaitingInQueue: String
    val detailsDownloaded: String
    val detailsSpeed: String
    val detailsState: String
    val detailsTimeLeft: String
    val detailsAdded: String
    val detailsSavedTo: String
    val detailsSource: String
    val detailsPage: String
    val detailsOpenFile: String
    val detailsOpenFolder: String
    val detailsStartNow: String
    val detailsRemoveFromList: String
    val detailsCancelDownload: String
    val detailsHeaderDownload: String
    val detailsHeaderDetails: String
    fun detailsAddedToday(clock: String): String
    fun detailsAddedYesterday(clock: String): String

    fun removeDialogTitle(count: Int): String
    fun removeDialogMessage(count: Int): String
    val removeDialogAlsoDeleteFiles: String

    val duplicateDialogTitle: String
    val duplicateDialogSubtitle: String
    fun duplicateDialogStatus(status: String): String
    val duplicateDialogQuestion: String
    val duplicateDialogResumeExisting: String
    val duplicateDialogRedownload: String

    val overwriteDialogTitle: String
    val overwriteDialogSubtitle: String
    val overwriteDialogFileNameLabel: String
    val overwriteDialogFolderLabel: String
    val overwriteDialogNewCopyLabel: String
    val overwriteDialogQuestion: String
    fun overwriteDialogSaveAsCopy(name: String): String
    val overwriteDialogOverwriteExisting: String

    val desktopWindowTagline: String
    val desktopTrayIdleTooltip: String
    val desktopTrayDownloadsHeader: String
    fun desktopTrayMore(count: Int): String
    val desktopTrayShowApp: String
    val desktopTrayCloseApp: String
    val sectionGeneral: String
    val sectionGeneralBlurb: String
    val sectionDownloads: String
    val sectionDownloadsBlurb: String
    val sectionBrowser: String
    val sectionBrowserBlurb: String
    val sectionSpeed: String
    val sectionSpeedBlurb: String
    val sectionMedia: String
    val sectionMediaBlurb: String
    val sectionTorrent: String
    val sectionTorrentBlurb: String
    val sectionUpdates: String
    val sectionUpdatesBlurb: String
    val summaryNoQueueLimit: String
    fun summaryAtATime(n: Int): String
    val summaryNoSpeedLimit: String
    val summarySetupNeeded: String
    val summaryUploadingOff: String
    val summaryUploadingOn: String
    fun summaryVersionAvailable(version: String): String
    fun summaryVersion(version: String): String
    val changesSavedAutomatically: String
    val settingsAllSettings: String

    val clipboardGroup: String
    val clipboardFooter: String
    val watchClipboard: String
    val watchClipboardOn: String
    val settingOff: String
    val alsoVideoPages: String
    val alsoVideoPagesHint: String
    val diagnosticsGroup: String
    fun logSavedTo(path: String): String
    val diagnosticsFooter: String
    val saveLogToDownloads: String
    val logCouldNotSave: String
    val appearanceGroup: String
    val themeTitle: String
    val themeHint: String
    val settingsThemeDark: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val speedShownIn: String
    val speedShownInHint: String

    val saveLocationGroup: String
    val saveLocationFooter: String
    val settingsFolderPath: String
    val settingsChooseFolder: String
    val settingsUseDownloads: String
    val settingsConnectionsGroup: String
    val connectionsFooter: String
    val settingsConnectionsPerDownload: String
    val settingsConnectionsAuto: String
    val organizeGroup: String
    val organizeFooter: String
    val sortIntoFolders: String
    val sortIntoFoldersOn: String
    val sortIntoFoldersOff: String

    val speedLimitsFooter: String
    val speedLimitsFooterIos: String
    val speedLimitDownload: String
    val speedLimitUploadTorrents: String

    val torrentSharingGroup: String
    val downloadOnlyNoUpload: String
    val downloadOnlyNoUploadHint: String
    val torrentEngineGroup: String
    val settingsReady: String
    val settingsNotReady: String

    val extInstallGroup: String
    val extensionInstallFooter: String
    val edgeAddedHint: String
    val edgeFromStoreHint: String
    val openInEdgeStore: String
    val edgeAddedNote: String
    val extAddToEdge: String
    val firefoxFromStoreHint: String
    val extAddToFirefox: String
    val chromeZipHint: String
    val getTheZip: String
    val firefoxAndroidHint: String
    val keepReadyForBrowser: String
    val keepReadyOn: String
    val keepReadyOff: String
    val popUpOverBrowser: String
    val popUpOverBrowserOn: String
    val popUpOverBrowserOff: String

    val mediaToolsGroup: String
    val ytDlpHint: String
    val settingsMissing: String
    val ffmpegHint: String
    val ffmpegMissing: String
    fun jsRuntimeTitle(name: String): String
    val jsRuntimeHint: String
    fun missingPress(button: String): String
    val mediaToolsFolder: String
    val mediaUpdatingTools: String
    val mediaDownloadingTools: String
    fun mediaDownloadingTool(name: String): String
    fun mediaDownloadingToolPercent(name: String, percent: Int): String
    val mediaUpdateTools: String
    val mediaDownloadTheTools: String

    val updateNewVersionAvailable: String
    fun updateVersionLine(version: String, current: String): String
    val updateWhatsNew: String
    val updateNoReleaseNotes: String
    fun downloadingUpdate(percent: Int): String
    val updateDownloadedAndroid: String
    val updateDownloadedDesktop: String
    val storeInstallsItselfOpenNow: String
    val updateInstall: String
    val updateRestartAndUpdate: String
    val updateDownloading: String
    val updateDownloadAndInstall: String
    val updateOpenMicrosoftStore: String
    fun updateDownloadWithSize(size: String): String
    val updateOpenReleasePage: String
    val updateSkipThisVersion: String
    val updateLater: String
    fun versionIsAvailable(version: String): String
    val youHaveLatest: String
    val updateSeeWhatsNew: String
    val updateChecking: String
    val updateCheckNow: String
    val checkUpdatesAutomatically: String
    val checkUpdatesAutomaticallyHint: String
    val downloadUpdatesAutomatically: String
    val downloadUpdatesAutomaticallyHintAndroid: String
    val downloadUpdatesAutomaticallyHint: String
    val installingUpdates: String
    val installingUpdatesStore: String
    val installingUpdatesManual: String
    val updateReleaseNotes: String
    val updateReleaseNotesHint: String
    val installHintReleasePage: String
    val installHintApk: String
    val installHintIpa: String
    val installHintDmg: String
    val installHintDeb: String
    val installHintOther: String

    val startupGroup: String
    val startupStoreFooter: String
    val startupStartAtLogin: String
    val startupManagedByWindows: String
    val startupOpenWindowsSettings: String
    val startupStartHidden: String
    val startupStartHiddenHint: String
    val startupStartAtLoginHint: String
    fun startupCouldNotChange(message: String): String
    val whenDoneGroup: String
    val whenDoneHint: String
    val whenDoneNothing: String
    val whenDoneExit: String
    val whenDoneSleep: String
    val whenDoneShutDown: String
    val whenDoneTitle: String
    fun whenDoneExitIn(seconds: Int): String
    fun whenDoneSleepIn(seconds: Int): String
    fun whenDoneShutDownIn(seconds: Int): String
    val whenDoneNow: String
    fun whenDoneBanner(action: String): String
    fun whenDoneFailed(message: String): String
    val sectionNetwork: String
    val sectionNetworkBlurb: String
    val netProxyGroup: String
    val netProxyOff: String
    val netProxyHost: String
    val netProxyPort: String
    val netProxyUser: String
    val netProxyPassword: String
    val netUser: String
    val netSave: String
    val netSaved: String
    val netProxyHint: String
    val netProxyHintIos: String
    val netProxyBadPort: String
    val netLoginsGroup: String
    val netLoginsHint: String
    val netLoginSite: String
    val netLoginAdd: String
    val netLoginsEmpty: String
    val netRemove: String
    fun netLoginsCount(n: Int): String
    val sysTabAllDownloads: String
    val sysTabActive: String
    val sysTabCompleted: String
    val sysTabMediaExtractor: String
    val sysTabSettings: String
    val sysSortNewestFirst: String
    val sysSortOldestFirst: String
    val sysSortLargestFirst: String
    val sysSortSmallestFirst: String
    val sysSortNameAz: String
    val sysEnterValidUrlOrMagnet: String
    val sysCannotStartVideoInspectionFailed: String
    val sysCannotStartTorrentInspectionFailed: String
    val sysClipEndExceedsDuration: String
    val sysClipStartAfterEnd: String
    fun sysStartedDownload(name: String): String
    val sysFailedToQueueDownload: String
    fun sysQualitiesNotAdded(count: Int): String
    fun sysAddedQualityDownloads(count: Int, title: String): String
    val sysSelectAtLeastOnePlaylistVideo: String
    fun sysVideosNotAdded(count: Int): String
    fun sysAddedPlaylistVideos(count: Int, playlistTitle: String): String
    val sysSelectAtLeastOneLink: String
    fun sysAddedLinkDownloads(added: Int, skipped: Int): String
    val sysCouldNotReadTorrent: String
    fun sysCouldNotStartDownload(reason: String): String
    val sysUnknownError: String
    fun sysQueuedFromBrowser(added: Int, skipped: Int): String
    val sysEnterTorrentOrMagnetFirst: String
    val sysFailedToParseTorrentMetadata: String
    val sysEnterMediaUrlFirst: String
    fun sysUnableToFetchFormatsWithReason(reason: String): String
    val sysUnableToFetchFormats: String
    val sysFailedToInspectMediaQualities: String
    fun sysTorrentEngineError(message: String): String
    fun sysTorrentEngineInstallFailed(message: String): String
    val sysMediaToolsDownloaded: String
    val sysMediaToolsDownloadFailed: String
    val sysMediaToolsUpdated: String
    fun sysSavedLinks(count: Int, path: String): String
    fun sysCouldNotSaveLinks(reason: String): String
    val sysErrorFallback: String
    val sysDownloadFailed: String
    val sysDownloadFailedNetwork: String
    val sysCouldNotStart: String
    val sysUnknownErrorCapital: String
    fun sysCouldNotSaveFileTo(dir: String): String
    val sysPlaylistFallbackTitle: String
    val sysPlaylistQualityBest: String
    val sysPlaylistQualityAudioOnly: String
    val sysToolsReadyYtDlpFfmpeg: String
    val sysToolsReadyYtDlpNoFfmpeg: String
    val sysToolsNotInstalled: String
    val sysToolsInstalledBuiltIn: String
    fun sysYtDlpVersionReady(version: String): String
    val sysMediaEngineBridgeNotRegistered: String
    val sysWebKitBuiltIn: String
    val sysTorrentEngineBuiltIn: String
    fun sysTorrentEngineReadyVersion(version: String): String
    fun sysTorrentEngineFailedToLoad(message: String): String
    val sysTorrentEngineAvailable: String
    val sysTorrentEngineMissing: String
    val sysTorrentEngineNotAvailable: String
    fun sysCouldNotStartInstaller(message: String): String
    val sysCheckingForUpdates: String
    val sysNoPublishedRelease: String
    fun sysVersionNoChangesForDevice(version: String, current: String): String
    fun sysVersionAvailable(version: String): String
    fun sysUpToDate(current: String): String
    fun sysCouldNotCheckForUpdates(reason: String): String
    val sysNetworkError: String
    val sysUpdateNoInstallerForSystem: String
    fun sysDownloadingAsset(name: String): String
    fun sysVersionReadyToInstall(version: String): String
    fun sysUpdateDownloadFailed(message: String): String
    val sysAllowUnknownAppsThenInstall: String
    val sysDownloadsChannelName: String
    val sysDownloadsChannelDescription: String
    val sysNotificationAppTitle: String
    val sysAppRunningInBackground: String
    val sysNotificationDownloadsFallback: String
    fun sysFilesDownloading(count: Int): String
    fun sysPercentActive(percent: Int, count: Int): String
    fun sysActiveCount(count: Int): String
    fun sysDownloadCompleteSize(size: String): String
    fun sysMoreDownloading(count: Int): String
    val sysComplete: String
    fun sysNotificationDownloadsTitle(speed: String): String
    fun sysDownloadingPercent(count: Int, percent: Int): String
    val sysDownloadTaskFallback: String
    val sysBrowserReadyTitle: String
    val sysBrowserReadyText: String
    val sysTurnOff: String
    val sysBrowserReadyChannelName: String
    val sysBrowserReadyChannelDescription: String
    val sysDownloadingFileFallback: String
    val sysUnknownSize: String
    val sysStatusCompleted: String
    val sysStatusDownloading: String
    val sysDownloadCompleteTitle: String
    fun sysFinishedDownloading(size: String): String
    val sysSentToFetchJet: String
    val sysLinksChannelName: String
    val sysLinksChannelDescription: String
    val sysLinkReceivedTitle: String
    val sysLinkReceivedText: String

    val stepCompleted: String
    val stepSeeding: String
    val stepFetchingMetadata: String
    val stepConnectingToPeers: String
    val stepCheckingFiles: String
    val stepCheckingResumeData: String
    val stepDownloading: String
    val stepFinished: String
    val stepConnecting: String
    val stepPaused: String
    val stepError: String
    val stepResolvingStreams: String
    val stepDownloadingParts: String
    val stepPreparingDownload: String
    val stepClippingVideo: String
    val stepDownloadingMedia: String
    val stepAlreadyDownloaded: String
    val stepDownloadingVideo: String
    val stepDownloadingAudio: String
    val stepMergingAudioVideo: String
    val stepConvertingToMp3: String
    val stepCuttingClip: String
    val stepSavingToFolder: String
    val stepFinalizingVideo: String
    val stepConvertingVideo: String
    val stepRepackagingVideo: String
    val stepYtDlpFallback: String
    val stepNotPlainStream: String
    val stepSingleConnection: String
    val stepServerNoRanges: String
    val stepAgeRestrictedExpiredCookies: String
    val stepAgeRestrictedPlaceCookies: String
    val stepPreparingMediaEngine: String
    val stepMediaEngineNotStarted: String
    fun stepNumber(step: Int, total: Int): String
    fun stepSavingAs(ext: String): String
    fun stepJoiningParts(count: Int): String
    fun stepNoDataFor(seconds: Int): String
    fun stepPeers(peers: Int, seeds: Int): String
    fun stepDownloadingFrom(host: String): String
    fun stepMediaEngineNotReady(error: String): String
    fun stepTorrentEngineFailed(error: String): String
    fun stepReadyWith(details: String): String

    // --- Device sync (ROADMAP-1.3.0 item 4) ---
    val sysDeviceSendTitle: String
    val sysDeviceNoDevices: String
    val sysDeviceSearching: String
    val sysDeviceModeLink: String
    val sysDeviceModeLinkDesc: String
    val sysDeviceModeTransfer: String
    val sysDeviceModeTransferDesc: String
    val sysDeviceDeleteAfter: String
    val sysDeviceSend: String
    val sysDevicePairTitle: String
    val sysDevicePairBody: String
    val sysDevicePairConfirm: String
    val sysDevicePairConnecting: String
    val sysDevicePairFailed: String
    val sysDeviceSending: String
    val sysDeviceWaitingAccept: String
    val sysDeviceSent: String
    val sysDeviceSendFailed: String
    val sysDeviceReceived: String
    val visibleToDevices: String
    val visibleToDevicesHint: String
    val sysDeviceSearch: String
    val sysDeviceOthers: String
    val sysDeviceNoMatch: String
    val sectionDevices: String
    val sectionDevicesBlurb: String
    val pairedDevicesTitle: String
    val noPairedDevices: String
    val unpairDevice: String
    val summaryVisible: String
    val summaryHidden: String
    val blockDevice: String
    val unblockDevice: String
    val blockedDevicesTitle: String
    val noBlockedDevices: String
    val pairRefusedBlocked: String
    val pairRefusedWait: String
    val pairRefusedBusy: String
    val pairDeclined: String
    val pairExpired: String
    val sysDevicePairedLabel: String
    val sysDeviceThisDevice: String
    val sysDeviceManualConnect: String
    val sysDeviceConnect: String
}

val LocalizationManager.Strings: AppLocalization
    get() = LocalizationManager.getProvider()
