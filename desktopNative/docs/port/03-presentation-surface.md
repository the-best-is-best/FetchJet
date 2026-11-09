# 03 - Presentation surface: what a UI consumes from `sharedLogic`

Scope: everything the Compose UI (`sharedUI`) and the desktop entry point (`desktopApp/main.kt`)
read from or call in `sharedLogic`, so that a renderer-only Slint UI can be driven through a
bridge (state snapshots out, events in). Read-only analysis of branch `desktop-native`,
2026-11-09. Nothing was built or run for this document.

Path shorthands used below:

| Short     | Full path                                                                                |
|-----------|------------------------------------------------------------------------------------------|
| `DASH/`   | `sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/presentation/dashboard/` |
| `SL/`     | `sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`                        |
| `UI/`     | `sharedUI/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`                           |
| `main.kt` | `desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt`                        |

Files of `DASH/` (3503 lines): `DashboardContract.kt` (453), `DashboardViewModel.kt` (971),
`DashboardAddDialogEvents.kt` (399), `DashboardAddDownload.kt` (657), `DashboardTaskEvents.kt`
(278), `DashboardSettingsEvents.kt` (241), `DashboardDeviceSync.kt` (310), `DashboardFilters.kt`
(114), `DashboardTools.kt` (80).

Headline facts:

- One state object (`DashboardUiState`, 84 properties), 89 events (`DashboardUiEvent`), 1 effect
  (`DashboardUiEffect.ShowToast`).
- The view model covers only about two thirds of what the UI shows. The rest is read **directly**
  from about 15 process-wide singletons (section 4): `UpdateManager`, `NetworkSettings`,
  `SpeedSchedule`, `WhenDone`, `BridgePairing`, `BehaviorSettings`, `QueueManagerRepository`,
  `StartupLauncher`, `BrowserIntegration`, `ExtensionStore`, `AppLanguage`, `PathResolver`, the
  two file pickers, `DiagnosticsLog`.
- A lot of behaviour lives in Compose code, not in the view model (section 5): clipboard watcher,
  auto-inspect of a pasted link, the "can start" rule and the button text of the Add dialog, clip
  validation, the type-folder rewrite of the destination, search / side filter / grouping /
  multi-selection of the list, the schedule time maths, every text format (bytes, speed, ETA,
  dates). A renderer-only UI needs all of it on the Kotlin side of the bridge.
- The view model never uses `Dispatchers.Main`. On mingwX64 it constructs and runs as it is (section
  3.4).

---

## 1. `DashboardUiState` in full

Declared at `DASH/DashboardContract.kt:82-224`. Immutable `data class`, replaced as a whole on
every change (`MutableStateFlow.update { it.copy(...) }`).

### 1.1 Properties

"UI" = whether `sharedUI` reads it. "Set by" = the main writers.

#### Add-download dialog

| #  | Property                          | Type                      | Default                                                                                                                                       | Meaning / set by                                                                                         | UI  |
|----|-----------------------------------|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|-----|
| 1  | `inputUrl`                        | `String`                  | `""`                                                                                                                                          | Text of the link box. `OnUrlChanged` (may be reduced to the single link found in pasted text)            | yes |
| 2  | `destinationPath`                 | `String`                  | `""`, then `pathResolver.getDownloadsDirectory()` in the constructor (`DashboardViewModel.kt:44-52`), then the saved value in `init` (`:116`) | Save folder, also the app-wide default folder (persisted)                                                | yes |
| 3  | `disableTorrentUpload`            | `Boolean`                 | `false`                                                                                                                                       | "Download only, no upload" (persisted)                                                                   | yes |
| 6  | `isAddDialogVisible`              | `Boolean`                 | `false`                                                                                                                                       | `OnToggleAddDialog`, external requests, successful add                                                   | yes |
| 13 | `isLoading`                       | `Boolean`                 | `false`                                                                                                                                       | true while `enqueueTask` runs after "Start"                                                              | yes |
| 14 | `errorMessage`                    | `String?`                 | `null`                                                                                                                                        | Add-dialog error line (validation, enqueue failure)                                                      | yes |
| 15 | `message`                         | `String?`                 | `null`                                                                                                                                        | Never written, never read. Dead                                                                          | no  |
| 16 | `detectedEngine`                  | `EngineType`              | `HTTP_DIRECT`                                                                                                                                 | Engine chosen for the link (auto-detected or `OnEngineSelected`)                                         | yes |
| 17 | `isInspectingTorrent`             | `Boolean`                 | `false`                                                                                                                                       | Torrent metadata is being read                                                                           | yes |
| 18 | `inspectedTorrentName`            | `String?`                 | `null`                                                                                                                                        | Torrent name                                                                                             | yes |
| 19 | `inspectedTorrentFiles`           | `List<TorrentFileEntry>`  | `emptyList()`                                                                                                                                 | Files of the inspected torrent with selection / priority                                                 | yes |
| 20 | `inspectedTorrentWebSeeds`        | `List<String>`            | `emptyList()`                                                                                                                                 | Passed to `enqueueTask` only                                                                             | no  |
| 21 | `inspectError`                    | `String?`                 | `null`                                                                                                                                        | Torrent inspection error                                                                                 | yes |
| 22 | `isInspectingMedia`               | `Boolean`                 | `false`                                                                                                                                       | yt-dlp / playlist inspection running (also true during course reading)                                   | yes |
| 23 | `courseReading`                   | `Pair<Int, Int>?`         | `null`                                                                                                                                        | Browser extension is reading a course: done / total                                                      | yes |
| 24 | `inspectedMediaTitle`             | `String?`                 | `null`                                                                                                                                        | Video title (used for the file name only)                                                                | no  |
| 25 | `suggestedFileName`               | `String`                  | `""`                                                                                                                                          | Name FetchJet would give the file ("File name" box placeholder)                                          | yes |
| 26 | `fileNameOverride`                | `String`                  | `""`                                                                                                                                          | What the user typed in that box; empty = use the suggestion                                              | yes |
| 27 | `checksumInput`                   | `String`                  | `""`                                                                                                                                          | Optional checksum (sha256 / md5 hex) to verify the finished file                                         | yes |
| 28 | `inspectedMediaQualities`         | `List<MediaQualityEntry>` | `emptyList()`                                                                                                                                 | Qualities of the inspected video                                                                         | yes |
| 29 | `inspectedSubtitles`              | `List<SubtitleTrack>`     | `emptyList()`                                                                                                                                 | Subtitle tracks of the inspected video                                                                   | yes |
| 30 | `embedSubtitles`                  | `Boolean`                 | `false`                                                                                                                                       | Embed in the video (true) or separate files (false)                                                      | yes |
| 31 | `inspectMediaError`               | `String?`                 | `null`                                                                                                                                        | Media inspection error                                                                                   | yes |
| 42 | `selectedDownloadSpeedLimitBytes` | `Long`                    | `0L`                                                                                                                                          | Per-download limit for the next add. Its event is never sent by the UI; reset to 0 when the dialog opens | no  |
| 43 | `selectedUploadSpeedLimitBytes`   | `Long`                    | `0L`                                                                                                                                          | Same, upload                                                                                             | no  |
| 57 | `clipStartTime`                   | `String`                  | `""`                                                                                                                                          | Clip start, `"HH:MM:SS"` (also `"00:00"`)                                                                | yes |
| 58 | `clipEndTime`                     | `String`                  | `""`                                                                                                                                          | Clip end                                                                                                 | yes |
| 59 | `isClipRangeEnabled`              | `Boolean`                 | `false`                                                                                                                                       | Clip switch                                                                                              | yes |
| 60 | `clipFastMode`                    | `Boolean`                 | `false`                                                                                                                                       | true = download the whole video then cut; false = fetch only the clip                                    | yes |
| 61 | `audioConvertFormat`              | `String?`                 | `null`                                                                                                                                        | Convert audio-only downloads to this format; null = keep original. Values: `AUDIO_CONVERT_FORMATS`       | yes |
| 62 | `inspectedPlaylist`               | `MediaPlaylist?`          | `null`                                                                                                                                        | A playlist / channel / course: its videos                                                                | yes |
| 63 | `linkList`                        | `List<LinkListEntry>`     | `emptyList()`                                                                                                                                 | 2+ links pasted / imported: one row per link                                                             | yes |
| 64 | `linkListFolder`                  | `String`                  | `""`                                                                                                                                          | Sub-folder for the list's downloads ("" = none)                                                          | yes |
| 65 | `playlistQuality`                 | `PlaylistQuality`         | `PlaylistQuality.BEST`                                                                                                                        | Quality for all playlist videos                                                                          | yes |
| 66 | `scheduleStartMs`                 | `Long`                    | `0L`                                                                                                                                          | Start time of the new download(s), epoch ms; 0 = now                                                     | yes |
| 67 | `selectedConnections`             | `Int`                     | `0`                                                                                                                                           | Connections for this direct download; 0 = app setting / automatic                                        | yes |
| 69 | `mediaDurationSeconds`            | `Long`                    | `0L`                                                                                                                                          | Duration of the inspected video                                                                          | yes |

#### Home / chrome / task list

| #  | Property                        | Type                 | Default       | Meaning / set by                                                           | UI  |
|----|---------------------------------|----------------------|---------------|----------------------------------------------------------------------------|-----|
| 7  | `allTasks`                      | `List<DownloadTask>` | `emptyList()` | Every task, newest first (`QueueManagerRepository.allTasksFlow`)           | yes |
| 8  | `filteredTasks`                 | `List<DownloadTask>` | `emptyList()` | `allTasks` filtered by `selectedTab`, sorted (`DashboardFilters.kt:11-47`) | yes |
| 9  | `selectedTab`                   | `DashboardTab`       | `ALL`         | Current tab; on desktop only `ALL` and `SETTINGS` matter                   | yes |
| 10 | `sortOption`                    | `TaskSortOption`     | `DATE_DESC`   | List order (not persisted)                                                 | yes |
| 11 | `totalDownloadSpeedBytesPerSec` | `Long`               | `0L`          | Sum over tasks with status `DOWNLOADING`                                   | yes |
| 12 | `totalUploadSpeedBytesPerSec`   | `Long`               | `0L`          | Same, upload                                                               | yes |
| 36 | `scrollToTopSignal`             | `Int`                | `0`           | Incremented when a download was just added: the list scrolls to row 0      | yes |

#### Dialog triggers

| #  | Property                    | Type                | Default       | Meaning / set by                                                                                 | UI  |
|----|-----------------------------|---------------------|---------------|--------------------------------------------------------------------------------------------------|-----|
| 32 | `removeConfirmationTaskId`  | `String?`           | `null`        | Remove / cancel confirmation for one task                                                        | yes |
| 33 | `removeConfirmationTaskIds` | `List<String>`      | `emptyList()` | Same for a multi-selection                                                                       | yes |
| 34 | `browserFallbackUrl`        | `String?`           | `null`        | A browser-sent plain file link whose dialog was dismissed: "download it in the browser instead?" | yes |
| 35 | `clipboardOfferUrl`         | `String?`           | `null`        | A link just copied: "Download it?"                                                               | yes |
| 40 | `duplicateTaskAlert`        | `DownloadTask?`     | `null`        | The same link is already in the list                                                             | yes |
| 41 | `fileConflictAlert`         | `FileConflictData?` | `null`        | The target file already exists on disk                                                           | yes |

#### Settings mirrored into the state

| #  | Property                        | Type      | Default                       | Meaning / set by                                                                                                        | UI             |
|----|---------------------------------|-----------|-------------------------------|-------------------------------------------------------------------------------------------------------------------------|----------------|
| 4  | `isDarkMode`                    | `Boolean` | `true`                        | Legacy; still written for older versions. The UI must not use it (it resolves `themeMode`)                              | passed, unused |
| 5  | `themeMode`                     | `String`  | `"system"`                    | `"system"`, `"light"` or `"dark"` (persisted)                                                                           | yes            |
| 37 | `monitorClipboard`              | `Boolean` | `false`                       | Mirror of `BehaviorSettings.monitorClipboard`                                                                           | yes            |
| 38 | `clipboardVideoPages`           | `Boolean` | `false`                       | Mirror of `BehaviorSettings.clipboardVideoPages`                                                                        | yes            |
| 39 | `sortIntoFolders`               | `Boolean` | `false`                       | Mirror of `BehaviorSettings.sortIntoFolders`                                                                            | yes            |
| 44 | `globalDownloadSpeedLimitBytes` | `Long`    | `0L`                          | App-wide download cap, bytes/s; 0 = unlimited. Also follows `queueRepository.globalDownloadSpeedLimit` (speed schedule) | yes            |
| 45 | `globalUploadSpeedLimitBytes`   | `Long`    | `0L`                          | App-wide upload cap (torrents)                                                                                          | yes            |
| 68 | `defaultConnections`            | `Int`     | `0`                           | Mirror of `BehaviorSettings.connections` (0..32, 0 = automatic)                                                         | yes            |
| 70 | `speedDisplayUnit`              | `String`  | `"MB/s"`                      | `"MB/s"`, `"KB/s"`, `"Mbps"`, `"Kbps"` (legacy values `BYTES_PER_SEC` / `BOTH` -> `MB/s`, `BITS_PER_SEC` -> `Mbps`)     | yes            |
| 71 | `speedPresetLowBytes`           | `Long`    | `256L * 1024`                 | Quick preset "Low"                                                                                                      | yes            |
| 72 | `speedPresetLowUnit`            | `String`  | `"KB/s"`                      | Unit the preset is edited in                                                                                            | yes            |
| 73 | `speedPresetMediumBytes`        | `Long`    | `2L * 1024 * 1024` (see note) | Quick preset "Medium"                                                                                                   | yes            |
| 74 | `speedPresetMediumUnit`         | `String`  | `"MB/s"`                      |                                                                                                                         | yes            |
| 75 | `speedPresetFastBytes`          | `Long`    | `3L * 1024 * 1024` (see note) | Quick preset "Fast"                                                                                                     | yes            |
| 76 | `speedPresetFastUnit`           | `String`  | `"MB/s"`                      |                                                                                                                         | yes            |
| 77 | `speedPresetHighBytes`          | `Long`    | `20L * 1024 * 1024`           | Stored but "High" is always shown and applied as Unlimited (0)                                                          | passed, unused |
| 78 | `speedPresetHighUnit`           | `String`  | `"MB/s"`                      |                                                                                                                         | passed, unused |

Note on the preset defaults: the `data class` defaults are 2 MB/s and 3 MB/s, but
`AppSettingsRepository.loadSettings()` (`SL/core/settings/AppSettingsRepository.kt:44-51`) starts
from `mediumBytes = 5L * 1024 * 1024` and `fastBytes = 10L * 1024 * 1024` when the file has no
value, and `init` copies the loaded values into the state. So the effective first-run values are
Low 256 KB/s, Medium **5 MB/s**, Fast **10 MB/s**, High 20 MB/s.

#### Media tools / torrent engine status

| #  | Property               | Type      | Default | Meaning / set by                                                                  | UI  |
|----|------------------------|-----------|---------|-----------------------------------------------------------------------------------|-----|
| 46 | `ytDlpStatus`          | `String`  | `""`    | English status text of the media engine (translated by `localizeStep` when shown) | yes |
| 47 | `isDownloadingYtDlp`   | `Boolean` | `false` | Tools are being downloaded / updated                                              | yes |
| 48 | `toolDownloadName`     | `String?` | `null`  | Tool being downloaded ("yt-dlp", "ffmpeg", "deno")                                | yes |
| 49 | `toolDownloadFraction` | `Float?`  | `null`  | 0..1, null = unknown length                                                       | yes |
| 50 | `isYtDlpInstalled`     | `Boolean` | `true`  |                                                                                   | yes |
| 51 | `isFfmpegInstalled`    | `Boolean` | `true`  |                                                                                   | yes |
| 52 | `jsRuntimeName`        | `String`  | `""`    | "deno", "QuickJS"... blank = no row                                               | yes |
| 53 | `isJsRuntimeReady`     | `Boolean` | `true`  |                                                                                   | yes |
| 54 | `toolsDirectoryPath`   | `String`  | `""`    | Folder of the tools ("Open" row)                                                  | yes |
| 55 | `torrentEngineStatus`  | `String`  | `""`    | English status text of libtorrent                                                 | yes |
| 56 | `isTorrentEngineReady` | `Boolean` | `true`  |                                                                                   | yes |

#### Device sync ("Send to device")

| #  | Property            | Type                     | Default       | Meaning / set by                          | UI  |
|----|---------------------|--------------------------|---------------|-------------------------------------------|-----|
| 79 | `deviceShareTaskId` | `String?`                | `null`        | Task whose "Send to device" sheet is open | yes |
| 80 | `discoveredDevices` | `List<DiscoveredDevice>` | `emptyList()` | Devices seen on the LAN now               | yes |
| 81 | `pairedDevices`     | `List<PairedDevice>`     | `emptyList()` | Paired devices (Settings > Devices)       | yes |
| 82 | `blockedDevices`    | `List<PairedDevice>`     | `emptyList()` | Blocked devices                           | yes |
| 83 | `devicePairing`     | `PairingState?`          | `null`        | Pairing handshake in progress             | yes |
| 84 | `deviceSending`     | `SendState?`             | `null`        | A send in progress / just finished        | yes |

(The `#` column is the position in the declaration, 1..84. The tables group the properties by
use, so the numbers are not consecutive inside a table.)

### 1.2 Nested types

#### Enums of the contract (`DASH/DashboardContract.kt`)

| Enum                        | Constants (declaration order = ordinal)                      | Extra                                                    |
|-----------------------------|--------------------------------------------------------------|----------------------------------------------------------|
| `DashboardTab` (`:11-35`)   | `ALL`, `ACTIVE`, `COMPLETED`, `TORRENT`, `MEDIA`, `SETTINGS` | `title: String` (localized; `TORRENT` -> `"BitTorrent"`) |
| `TaskSortOption` (`:38-53`) | `DATE_DESC`, `DATE_ASC`, `SIZE_DESC`, `SIZE_ASC`, `NAME_ASC` | `label: String` (localized)                              |
| `SpeedPreset` (`:227`)      | `LOW`, `MEDIUM`, `FAST`, `HIGH`                              | `HIGH` is always Unlimited, not editable                 |

#### `LinkListEntry` (`DASH/DashboardContract.kt:55-74`)

| Field                         | Type                      | Default       | Meaning                                        |
|-------------------------------|---------------------------|---------------|------------------------------------------------|
| `url`                         | `String`                  | -             | The link                                       |
| `engine`                      | `EngineType`              | -             | Engine for this link                           |
| `isSelected`                  | `Boolean`                 | `true`        | Row checkbox                                   |
| `isInspecting`                | `Boolean`                 | `false`       | Torrent metadata being read                    |
| `inspectError`                | `String?`                 | `null`        | Torrent could not be read                      |
| `torrentName`                 | `String?`                 | `null`        |                                                |
| `torrentFiles`                | `List<TorrentFileEntry>`  | `emptyList()` | Shown under the row                            |
| `webSeeds`                    | `List<String>`            | `emptyList()` | Not shown                                      |
| `isInspectingMedia`           | `Boolean`                 | `false`       | Video qualities being read                     |
| `mediaInspectFailed`          | `Boolean`                 | `false`       | Qualities could not be read                    |
| `mediaTitle`                  | `String?`                 | `null`        | Real title once read                           |
| `mediaQualities`              | `List<MediaQualityEntry>` | `emptyList()` | Quality picker of the row (single choice)      |
| `isBeingInspected` (computed) | `Boolean`                 |               | `isInspecting                                  || isInspectingMedia` |
| `needsRetry` (computed)       | `Boolean`                 |               | `engine == MEDIA_STREAM && mediaInspectFailed` |

#### `FileConflictData` (`DASH/DashboardContract.kt:76-80`)

`fileName: String`, `destinationPath: String`, `suggestedNewName: String`.

#### `EngineType` (`SL/domain/model/EngineType.kt:3-7`)

`HTTP_DIRECT("HTTP/HTTPS Direct")`, `BITTORRENT("BitTorrent P2P")`,
`MEDIA_STREAM("Media Stream Extractor")`; property `displayName`.

#### `DownloadStatus` (`SL/domain/model/DownloadTask.kt:3-11`)

`IDLE`, `QUEUED`, `CONNECTING`, `DOWNLOADING`, `PAUSED`, `COMPLETED`, `ERROR`.

#### `DownloadTask` (`SL/domain/model/DownloadTask.kt:36-103`)

"UI" = read by `sharedUI` (R = list row `UI/ui/TaskRows.kt`, D = details `UI/TaskDetails.kt`,
L = list logic `UI/TaskList.kt`, P = `UI/ui/PiecesBar.kt`, X = other dialogs).

| Field                         | Type                     | Default       | Meaning                                                                                      | UI                |
|-------------------------------|--------------------------|---------------|----------------------------------------------------------------------------------------------|-------------------|
| `id`                          | `String`                 | -             | `"task_<seq>_<random6>"`                                                                     | R D L X           |
| `url`                         | `String`                 | -             | Download URL (direct stream link, magnet, page)                                              | R D L X           |
| `fileName`                    | `String`                 | -             | May be blank until known                                                                     | R D L X           |
| `destinationPath`             | `String`                 | -             | Folder                                                                                       | D L               |
| `engineType`                  | `EngineType`             | -             |                                                                                              | R D               |
| `status`                      | `DownloadStatus`         | `IDLE`        |                                                                                              | R D L X           |
| `totalBytes`                  | `Long`                   | `0L`          | 0 = unknown                                                                                  | R D               |
| `downloadedBytes`             | `Long`                   | `0L`          |                                                                                              | R D               |
| `downloadSpeedBytesPerSec`    | `Long`                   | `0L`          |                                                                                              | R D               |
| `uploadSpeedBytesPerSec`      | `Long`                   | `0L`          | Torrents                                                                                     | D                 |
| `disableUpload`               | `Boolean`                | `false`       | Torrent leech mode                                                                           | D                 |
| `progress`                    | `Float`                  | `0f`          | 0..1                                                                                         | R D X             |
| `chunks`                      | `List<DownloadChunk>`    | `emptyList()` | Pieces (HTTP engine, own media engine, HLS virtual pieces)                                   | D P               |
| `maxConnections`              | `Int`                    | `0`           | Requested connections                                                                        | no                |
| `connections`                 | `Int`                    | `0`           | Connections in use                                                                           | no                |
| `priority`                    | `Int`                    | `0`           | >0 High, 0 Normal, <0 Low                                                                    | R                 |
| `errorMessage`                | `String?`                | `null`        | English, translated by `localizeStep`                                                        | R D               |
| `createdAtEpochMs`            | `Long`                   | `0L`          | "Added" time, sort key                                                                       | D                 |
| `torrentFiles`                | `List<TorrentFileEntry>` | `emptyList()` |                                                                                              | D                 |
| `webSeedUrls`                 | `List<String>`           | `emptyList()` |                                                                                              | no                |
| `maxDownloadSpeedBytesPerSec` | `Long`                   | `0L`          |                                                                                              | no                |
| `maxUploadSpeedBytesPerSec`   | `Long`                   | `0L`          |                                                                                              | no                |
| `formatId`                    | `String?`                | `null`        | yt-dlp format                                                                                | no                |
| `originalUrl`                 | `String?`                | `null`        | Page URL of a media download (search, "Page" row, export)                                    | D L               |
| `pairedAudioUrl`              | `String?`                | `null`        |                                                                                              | no                |
| `subtitleTracks`              | `List<SubtitleTrack>`    | `emptyList()` |                                                                                              | no                |
| `embedSubtitles`              | `Boolean`                | `false`       |                                                                                              | no                |
| `currentStepText`             | `String?`                | `null`        | English step line ("Step 1 of 3 (...)", "Merging...")                                        | R D               |
| `clipStart`                   | `String?`                | `null`        |                                                                                              | D                 |
| `clipEnd`                     | `String?`                | `null`        |                                                                                              | D                 |
| `clipByCutting`               | `Boolean`                | `false`       |                                                                                              | D                 |
| `approximateTotalBytes`       | `Long`                   | `0L`          | When equal to `totalBytes` while downloading, the size is shown with "~"                     | R D               |
| `requestHeaders`              | `Map<String, String>`    | `emptyMap()`  | Only `["Referer"]` is read (details "Page" row). Holds cookies: never send the map to the UI | D                 |
| `postProcessLabel`            | `String?`                | `null`        | English label of the step after the download; null = none                                    | R D               |
| `postProcessProgress`         | `Float`                  | `-1f`         | 0..1, -1 = unknown length                                                                    | D                 |
| `postProcessStartedAtMs`      | `Long`                   | `0L`          |                                                                                              | D                 |
| `serverSupportsResume`        | `Boolean?`               | `null`        | HTTP only; null = not connected yet                                                          | via `canResume()` |
| `scheduledAtMs`               | `Long`                   | `0L`          | 0 = as soon as a slot is free                                                                | R D               |
| `waitingInQueue`              | `Boolean`                | `false`       | Set by the queue, not stored                                                                 | R D L             |
| `queuePosition`               | `Int`                    | `0`           | 1-based, 0 = none                                                                            | R D               |
| `expectedChecksum`            | `String?`                | `null`        |                                                                                              | D                 |
| `checksumVerified`            | `Boolean?`               | `null`        | true match, false mismatch, null not checked yet                                             | D                 |

Extension functions the UI calls (`SL/domain/model/DownloadTask.kt:109-146`):

- `fun DownloadTask.canResume(): Boolean?` - `BITTORRENT` -> true; `MEDIA_STREAM` ->
  `clipByCutting || (clipStart.isNullOrBlank() && clipEnd.isNullOrBlank())`; `HTTP_DIRECT` ->
  `serverSupportsResume`.
- `fun DownloadTask.canSendToDevice(): Boolean` - not a torrent and (`COMPLETED` or
  `canResume() != false`).
- `fun DownloadTask.canTransferToDevice(): Boolean` - `canSendToDevice()` and (`COMPLETED` or
  `canResume() == true`).
- `fun DownloadTask.etaSeconds(): Long?` - null unless `DOWNLOADING`, no post-process step, the
  step text has no "Merging" / "Step 3 of 3", speed > 0; a section clip extrapolates from
  `progress` (0.02..0.999), otherwise `(totalBytes - downloadedBytes) / speed`.

#### `DownloadChunk` (`SL/domain/model/DownloadTask.kt:13-24`)

`chunkIndex: Int`, `startByte: Long`, `endByte: Long`, `downloadedBytes: Long`,
`isCompleted: Boolean = false`, `isActive: Boolean = false`, `speedBytesPerSec: Long = 0L`,
`note: String? = null` (why the piece waits, e.g. "HTTP 429").

#### `TorrentFileEntry` (`SL/domain/model/DownloadTask.kt:26-34`)

`index: Int`, `path: String`, `size: Long`, `isSelected: Boolean = true`,
`downloadedBytes: Long = 0L`, `isCompleted: Boolean = false`,
`priority: Int = 0` (0 Normal, 1 High, -1 Low).

#### `MediaQualityEntry` (`SL/domain/model/MediaQualityEntry.kt:3-14`)

`index: Int`, `qualityLabel: String`, `directUrl: String`, `format: String`, `size: Long = -1L`,
`isSelected: Boolean = true`, `formatId: String? = null`, `originalUrl: String? = null`,
`videoTitle: String? = null`, `durationSeconds: Long = 0L`.

Helpers used by the UI (same file `:16-38`): `val MediaQualityEntry.isAudioOnly`
(`qualityLabel` contains "Audio", or `format` in mp3 m4a flac wav aac ogg opus),
`fun List<MediaQualityEntry>.isAllAudio()`, `qualityTag(height)`, `withQualityTag(label)`.

#### `SubtitleTrack` (`SL/domain/model/SubtitleTrack.kt:3-10`)

`langCode: String`, `langName: String`, `ext: String`, `url: String`, `isAuto: Boolean = false`,
`isSelected: Boolean = false`.

#### `MediaPlaylist`, `PlaylistEntry`, `PlaylistQuality` (`SL/feature/media_extractor/Playlist.kt`)

- `PlaylistEntry` (`:10-18`): `index: Int`, `title: String`, `url: String`,
  `durationSeconds: Long = 0L`, `isSelected: Boolean = true`, `titled: Boolean = true`.
- `MediaPlaylist` (`:20-27`): `title: String`, `entries: List<PlaylistEntry>`,
  `fromBrowser: Boolean = false` (read by the extension: entries are stream links),
  `folderName: String? = null` (null = use the title, "" = no folder).
- `PlaylistQuality` (`:143-177`): `BEST`, `P1080`, `P720`, `P480`, `P360`, `AUDIO`. Properties:
  `label: String` (localized for `BEST` / `AUDIO`; "1080p", "720p", "480p", "360p" otherwise),
  `formatSelector: String`, `extension: String` ("mp4", `AUDIO` -> "m4a").

#### Device sync (`SL/feature/device_sync/DeviceSync.kt`, `DeviceSyncModels.kt`)

- `SendMode` (`DeviceSync.kt:6-12`): `LINK_ONLY`, `TRANSFER_AND_CONTINUE`.
- `PairingState` (`DeviceSync.kt:15-26`, sealed, common `peerName: String`):
  `Connecting(peerName)`, `Confirm(peerName, peerId: String, code: String)`,
  `Failed(peerName, reason: String)`.
- `SendState` (`DeviceSync.kt:29-37`, sealed, common `deviceName: String`):
  `Waiting(deviceName)`, `Sending(deviceName, fraction: Float)`, `Done(deviceName)`,
  `Failed(deviceName, reason: String)`.
- `PairedDevice` (`DeviceSyncModels.kt:14-19`): `id: String`, `name: String`,
  `platform: String = ""`, `lastSeenEpochMs: Long = 0L`.
- `DiscoveredDevice` (`DeviceSyncModels.kt:22-29`): `id`, `name`, `platform`, `host: String`,
  `port: Int`, `paired: Boolean`.
- Refusal codes inside `reason` (English words, translated by `deviceSyncReason`,
  `UI/ui/DeviceSendDialog.kt:460-469`): `blocked`, `busy`, `declined`, `expired`,
  `wait:<seconds>`; anything else is shown as it is.
- The device name is `"<host> · <platform>"`; the UI splits it at `" · "`.

### 1.3 Update state and pairing state are **not** in `DashboardUiState`

They come from singletons (section 4): `UpdateManager.state: StateFlow<UpdateState>`
and `BridgePairing.pending: StateFlow<BridgePairing.Request?>`.

`UpdateState` (`SL/core/update/UpdateManager.kt:83-106`):

| Field                   | Type          | Default                   | Meaning                                              |
|-------------------------|---------------|---------------------------|------------------------------------------------------|
| `supported`             | `Boolean`     | `true`                    | false in the App Store edition                       |
| `currentVersion`        | `String`      | `APP_VERSION` (`"1.4.0"`) |                                                      |
| `autoCheck`             | `Boolean`     | `true`                    |                                                      |
| `autoInstall`           | `Boolean`     | `false`                   | Download the installer by itself                     |
| `canInstallInApp`       | `Boolean`     | `false`                   | `PlatformUpdater.canInstallInApp`                    |
| `checking`              | `Boolean`     | `false`                   |                                                      |
| `lastCheckedMs`         | `Long`        | `0L`                      |                                                      |
| `available`             | `AppRelease?` | `null`                    | Newer release, null = up to date / not checked       |
| `showDialog`            | `Boolean`     | `false`                   | Show the "new version" dialog                        |
| `message`               | `String?`     | `null`                    | Result line for Settings                             |
| `messageIsInstallError` | `Boolean`     | `false`                   |                                                      |
| `messageIsError`        | `Boolean`     | `false`                   |                                                      |
| `downloadProgress`      | `Float`       | `-1f`                     | 0..1 while the installer downloads                   |
| `installerPath`         | `String?`     | `null`                    | Installer downloaded, waiting for "Restart & update" |
| `skippedVersion`        | `String`      | `""`                      |                                                      |

`AppRelease` (`UpdateManager.kt:50-81`): `version`, `tag`, `title`, `notes`, `pageUrl`,
`publishedAt` (all `String`), `assets: List<ReleaseAsset>`; computed `unchangedPlatforms:
Set<String>` and `notesForDisplay: String`. `ReleaseAsset` (`:44`): `name`, `url`, `size: Long`,
`sha256: String = ""`.

`BridgePairing.Request` (`SL/core/network/BridgePairing.kt:52`): `origin: String`,
`legacy: Boolean`.

---

## 2. Events and effects

### 2.1 `DashboardUiEvent` (`DASH/DashboardContract.kt:230-446`)

89 events. `onEvent` (`DashboardViewModel.kt:602-628`) dispatches by type to four handler
functions; each handler ends with `else -> Unit`, so an event that is dispatched but has no
branch is dropped silently.

Handler files: **A** = `DashboardAddDialogEvents.kt` (`handleAddDialogEvent`, `:6-399`), **T** =
`DashboardTaskEvents.kt` (`handleTaskEvent`, `:10-232`), **S** = `DashboardSettingsEvents.kt`
(`handleSettingsEvent`, `:10-241`), **D** = `DashboardDeviceSync.kt` (`handleDeviceSyncEvent`,
`:153-201`).
"Sync" = everything happens inside `onEvent` on the calling thread; "async" = work is launched
on `scope` (Dispatchers.IO).

#### A. Add dialog (40 events)

| Event                              | Parameters                                          | What it does                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Lines     | Mode                         |
|------------------------------------|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------------------------|
| `OnEngineSelected`                 | `engine: EngineType`                                | Sets `detectedEngine`, clears qualities, title, playlist, torrent files and both inspect errors, recomputes `suggestedFileName`                                                                                                                                                                                                                                                                                                                                                                              | A 8-21    | sync                         |
| `OnClipboardLink`                  | `url: String`                                       | Ignored while the Add dialog or an offer is up, or when a task already has this `url` / `originalUrl`; else sets `clipboardOfferUrl`                                                                                                                                                                                                                                                                                                                                                                         | A 23-31   | sync                         |
| `OnClipboardOfferAccepted`         | -                                                   | Clears the offer, then `OnUrlChanged(url)` + `OnToggleAddDialog(true)`                                                                                                                                                                                                                                                                                                                                                                                                                                       | A 33-38   | sync                         |
| `OnClipboardOfferDismissed`        | -                                                   | Clears the offer                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | A 40-44   | sync                         |
| `OnFileNameChanged`                | `name: String`                                      | `fileNameOverride`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | A 46-48   | sync                         |
| `OnChecksumChanged`                | `value: String`                                     | `checksumInput`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 50-52   | sync                         |
| `OnUrlChanged`                     | `url: String`                                       | `extractLinks(text)` (regex, max 500, `DashboardAddDownload.kt:462-469`). Exactly one link inside text that has whitespace and grew by more than 1 character -> the box keeps only the link. 2+ links -> `linkList` (engine `HTTP_DIRECT` shown). Detects the engine (`AppFeatures` may force `HTTP_DIRECT`), **resets** every inspection / clip / schedule / connections / file name / checksum field, recomputes the suggested name, starts `inspectListedTorrents()` / `inspectListedVideos()` for a list | A 54-118  | sync + async list inspection |
| `OnDestinationPathChanged`         | `path: String`                                      | `destinationPath`, saves settings                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | A 120-126 | sync (file write)            |
| `OnDisableTorrentUploadToggled`    | `disable: Boolean`                                  | `disableTorrentUpload`, saves settings                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | A 128-134 | sync (file write)            |
| `OnToggleAddDialog`                | `show: Boolean`                                     | Closing: clears `linkListFolder`; if a browser-sent link was pending and it is a plain http(s) file, sets `browserFallbackUrl`. Both ways: re-reads `isMediaExtractorToolInstalled()` / `getMediaExtractorToolStatus()`, sets `isAddDialogVisible`, clears inspection results (keeps `inputUrl` and `linkList` when showing), resets per-download limits when showing. Showing while yt-dlp is not ready starts `pollToolStatusWhileNotReady()` (every 2 s, max 150 times)                                   | A 136-206 | sync + poll job              |
| `OnAddDownloadClicked`             | -                                                   | `addDownload()` (`DashboardAddDownload.kt:14-245`): validation, playlist / link list / several qualities branches, duplicate check (`duplicateTaskAlert`), file-exists check (`fileConflictAlert`), then `enqueueTask`; on success resets the dialog, toast "Started ...", `scrollToTopSignal++`                                                                                                                                                                                                             | A 208     | sync checks + async enqueue  |
| `OnInspectTorrentClicked`          | -                                                   | `inspectTorrent()` (`DashboardViewModel.kt:630-687`)                                                                                                                                                                                                                                                                                                                                                                                                                                                         | A 209     | async                        |
| `OnInspectMediaClicked`            | -                                                   | `inspectMedia()` (`DashboardViewModel.kt:689-823`): playlist first (90 s limit), then qualities (120 s limit)                                                                                                                                                                                                                                                                                                                                                                                                | A 210     | async                        |
| `OnMediaQualitySelectionToggled`   | `qualityIndex: Int, isSelected: Boolean`            | Toggles the quality whose `index == qualityIndex` (multi-select), recomputes the name                                                                                                                                                                                                                                                                                                                                                                                                                        | A 211-219 | sync                         |
| `OnSelectAllMediaQualitiesToggled` | `selectAll: Boolean`                                | All qualities on / off                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | A 335-349 | sync                         |
| `OnSubtitleToggled`                | `langCode: String, isSelected: Boolean`             | Toggles one subtitle track                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | A 221-227 | sync                         |
| `OnToggleEmbedSubtitles`           | `embed: Boolean`                                    | `embedSubtitles`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | A 229-231 | sync                         |
| `OnClipStartTimeChanged`           | `time: String`                                      | `clipStartTime`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 233-235 | sync                         |
| `OnClipEndTimeChanged`             | `time: String`                                      | `clipEndTime`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | A 237-239 | sync                         |
| `OnToggleClipRange`                | `enabled: Boolean`                                  | Refuses to enable without qualities or with unknown duration; fills start `"00:00"` and end = full duration (`"00:10:00"` when unknown) when blank                                                                                                                                                                                                                                                                                                                                                           | A 318-333 | sync                         |
| `OnToggleClipFastMode`             | `enabled: Boolean`                                  | `clipFastMode`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | A 241-243 | sync                         |
| `OnAudioConvertFormatSelected`     | `format: String?`                                   | `audioConvertFormat`, recomputes the name                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | A 245-248 | sync                         |
| `OnPlaylistEntryToggled`           | `index: Int, selected: Boolean`                     | Entry whose `index` matches                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | A 283-288 | sync                         |
| `OnPlaylistSelectAll`              | `selected: Boolean`                                 |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 290-293 | sync                         |
| `OnPlaylistFolderChanged`          | `name: String`                                      | `inspectedPlaylist.folderName`                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | A 295-297 | sync                         |
| `OnPlaylistQualityChanged`         | `quality: PlaylistQuality`                          | `playlistQuality`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | A 299-303 | sync                         |
| `OnPlaylistUseSingleVideo`         | -                                                   | Remembers the link as "single video", clears the playlist, runs `inspectMedia()`                                                                                                                                                                                                                                                                                                                                                                                                                             | A 305-309 | async                        |
| `OnLinkListEntryToggled`           | `index: Int, selected: Boolean`                     | Row at **list position** `index`                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | A 250-254 | sync                         |
| `OnLinkListSelectAll`              | `selected: Boolean`                                 |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 256-258 | sync                         |
| `OnLinkListFolderChanged`          | `name: String`                                      | `linkListFolder`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | A 260     | sync                         |
| `OnLinkListTorrentFileToggled`     | `linkIndex: Int, fileIndex: Int, selected: Boolean` | File with `TorrentFileEntry.index == fileIndex` of row `linkIndex`                                                                                                                                                                                                                                                                                                                                                                                                                                           | A 271-273 | sync                         |
| `OnLinkListTorrentFilePriority`    | `linkIndex: Int, fileIndex: Int, priority: Int`     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 275-277 | sync                         |
| `OnLinkListTorrentSelectAll`       | `linkIndex: Int, selected: Boolean`                 |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 279-281 | sync                         |
| `OnLinkListQualitySelected`        | `linkIndex: Int, qualityIndex: Int`                 | Single choice: selected = `it.index == qualityIndex`                                                                                                                                                                                                                                                                                                                                                                                                                                                         | A 262-267 | sync                         |
| `OnLinkListRetryInspect`           | `linkIndex: Int`                                    | Re-runs `inspectListedVideos()` for every unread video (the index is ignored)                                                                                                                                                                                                                                                                                                                                                                                                                                | A 269     | async                        |
| `OnScheduleChanged`                | `epochMs: Long`                                     | `scheduleStartMs`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | A 311     | sync                         |
| `OnConnectionsChanged`             | `connections: Int`                                  | `selectedConnections`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | A 312-316 | sync                         |
| `OnTorrentFileSelectionToggled`    | `fileIndex: Int, isSelected: Boolean`               | Inspected torrent, by `TorrentFileEntry.index`                                                                                                                                                                                                                                                                                                                                                                                                                                                               | A 351-365 | sync                         |
| `OnTorrentFilePriorityChanged`     | `fileIndex: Int, priority: Int`                     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 367-381 | sync                         |
| `OnSelectAllTorrentFilesToggled`   | `selectAll: Boolean`                                |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | A 383-395 | sync                         |

#### T. Tasks and their dialogs (25 events)

| Event                               | Parameters                                            | What it does                                                                                                                                        | Lines     | Mode         |
|-------------------------------------|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------------|
| `OnPauseClicked`                    | `taskId: String`                                      | `queueRepository.pauseTask`                                                                                                                         | T 12-16   | async        |
| `OnResumeClicked`                   | `taskId: String`                                      | `queueRepository.resumeTask` (also "Start now" and "Retry")                                                                                         | T 18-22   | async        |
| `OnCancelClicked`                   | `taskId: String`                                      | Sets `removeConfirmationTaskId` (same as Remove)                                                                                                    | T 24-38   | sync         |
| `OnRemoveClicked`                   | `taskId: String`                                      | Sets `removeConfirmationTaskId`                                                                                                                     | T 24-38   | sync         |
| `OnRemoveSelectedClicked`           | `taskIds: List<String>`                               | Sets `removeConfirmationTaskIds` when not empty                                                                                                     | T 41-45   | sync         |
| `OnExportLinksClicked`              | `taskIds: List<String>`                               | `exportLinks` (`:234-278`): writes `"<Downloads>/FetchJet links <yyyy-MM-dd HH-mm>.txt"`, toast with the path or the error                          | T 40      | async        |
| `OnConfirmRemoveClicked`            | `deleteFile: Boolean`                                 | Removes the single task and / or the selected ones, clears both confirmation fields                                                                 | T 47-72   | async        |
| `OnConfirmCancelClicked`            | -                                                     | Removes the confirmed task with `deleteFile = false`. Not sent by the UI                                                                            | T 88-100  | async        |
| `OnDismissCancelDialog`             | -                                                     | Clears both confirmation fields. Not sent by the UI                                                                                                 | T 171-180 | sync         |
| `OnDismissRemoveDialog`             | -                                                     | Clears both confirmation fields                                                                                                                     | T 171-180 | sync         |
| `OnConfirmBrowserFallback`          | -                                                     | Clears `browserFallbackUrl`, `ExternalDownloadBridge.triggerFallback(url)` (opens the browser with `fetchjet_fallback=1`)                           | T 74-82   | sync         |
| `OnDismissBrowserFallback`          | -                                                     | Clears `browserFallbackUrl`                                                                                                                         | T 84-86   | sync         |
| `OnResumeExistingDuplicateClicked`  | -                                                     | Resumes the duplicate, closes the dialog and the Add dialog, clears the link                                                                        | T 102-117 | async        |
| `OnRestartDuplicateClicked`         | -                                                     | Removes the duplicate with its file, then `addDownload(forceOverwrite = true, skipDuplicateCheck = true)`                                           | T 119-128 | async        |
| `OnDismissDuplicateDialog`          | -                                                     | Clears `duplicateTaskAlert`                                                                                                                         | T 130-132 | sync         |
| `OnConfirmOverwriteClicked`         | -                                                     | Clears the conflict, removes list entries for the same file, then starts the pending external download or `addDownload(forceOverwrite = true, ...)` | T 134-149 | async        |
| `OnSaveAsCopyClicked`               | `newFileName: String`                                 | Clears the conflict, starts with the new name (external or dialog)                                                                                  | T 151-164 | sync + async |
| `OnDismissFileConflictDialog`       | -                                                     | Clears the conflict and the pending external request                                                                                                | T 166-169 | sync         |
| `OnDownloadSpeedLimitChanged`       | `limitBytesPerSec: Long`                              | `selectedDownloadSpeedLimitBytes`. Not sent by the UI                                                                                               | T 182-186 | sync         |
| `OnUploadSpeedLimitChanged`         | `limitBytesPerSec: Long`                              | `selectedUploadSpeedLimitBytes`. Not sent by the UI                                                                                                 | T 188-192 | sync         |
| `OnPauseAllClicked`                 | -                                                     | `queueRepository.pauseAll()`                                                                                                                        | T 194     | sync         |
| `OnResumeAllClicked`                | -                                                     | `queueRepository.resumeAll()`                                                                                                                       | T 195     | sync         |
| `OnTaskTorrentFileSelectionToggled` | `taskId: String, fileIndex: Int, isSelected: Boolean` | `queueRepository.updateTorrentFileSelection`                                                                                                        | T 196-208 | sync         |
| `OnTaskTorrentFilePriorityChanged`  | `taskId: String, fileIndex: Int, priority: Int`       | `queueRepository.updateTorrentFilePriority`                                                                                                         | T 210-222 | sync         |
| `OnTaskPriorityChanged`             | `taskId: String, priority: Int`                       | `queueRepository.setTaskPriority`                                                                                                                   | T 224-228 | sync         |

#### S. Settings and navigation (15 events)

| Event                               | Parameters                                       | What it does                                                                                                                                                                                             | Lines     | Mode              |
|-------------------------------------|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|-------------------|
| `OnMonitorClipboardChanged`         | `on: Boolean`                                    | `BehaviorSettings.setMonitorClipboard` (state follows through the collector)                                                                                                                             | S 12-14   | sync (file write) |
| `OnClipboardVideoPagesChanged`      | `on: Boolean`                                    | `BehaviorSettings.setClipboardVideoPages`                                                                                                                                                                | S 16-18   | sync (file write) |
| `OnSortIntoFoldersChanged`          | `on: Boolean`                                    | `BehaviorSettings.setSortIntoFolders`                                                                                                                                                                    | S 20-22   | sync (file write) |
| `OnGlobalDownloadSpeedLimitChanged` | `limitBytesPerSec: Long`                         | State, `queueRepository.setGlobalDownloadSpeedLimit`, saves settings                                                                                                                                     | S 24-40   | sync (file write) |
| `OnGlobalUploadSpeedLimitChanged`   | `limitBytesPerSec: Long`                         | Same for upload                                                                                                                                                                                          | S 42-58   | sync (file write) |
| `OnSpeedDisplayUnitChanged`         | `unit: String`                                   | `speedDisplayUnit`, saves                                                                                                                                                                                | S 60-63   | sync (file write) |
| `OnSpeedPresetChanged`              | `preset: SpeedPreset, bytes: Long, unit: String` | Updates LOW / MEDIUM / FAST (HIGH ignored). If the global limit equals the old value of this preset, the new value is applied live. Saves                                                                | S 65-102  | sync (file write) |
| `OnApplySettingsClicked`            | -                                                | Re-applies both global limits, saves. Wired in `UI/SettingsContent.kt:109` but nothing in the panel calls it                                                                                             | S 104-112 | sync              |
| `OnLanguageChanged`                 | `choice: String`                                 | `AppLanguage.change(choice)` (`"system"` or a locale code), saves                                                                                                                                        | S 114-117 | sync (file write) |
| `OnThemeToggled`                    | -                                                | dark <-> light. Wired but not called by the panel (the header uses `OnThemeModeChanged`)                                                                                                                 | S 119-125 | sync              |
| `OnThemeModeChanged`                | `mode: String`                                   | `themeMode` = `"system"` / `"light"` / `"dark"`, also `isDarkMode`, saves                                                                                                                                | S 127-135 | sync (file write) |
| `OnTabSelected`                     | `tab: DashboardTab`                              | `selectedTab` + recomputed `filteredTasks`                                                                                                                                                               | S 137-151 | sync              |
| `OnSortChanged`                     | `sort: TaskSortOption`                           | `sortOption` + recomputed `filteredTasks`                                                                                                                                                                | S 153-160 | sync              |
| `OnDefaultConnectionsChanged`       | `connections: Int`                               | `BehaviorSettings.setConnections` (coerced to 0..32)                                                                                                                                                     | S 162     | sync (file write) |
| `OnDownloadYtDlpClicked`            | -                                                | "Download / Update tools": `downloadMediaExtractorTool(::reportToolProgress)` under `toolsMutex`, then `torrentEngine.installEngine()`, refreshes every tool status field, toast "downloaded" / "failed" | S 163-237 | async             |

#### D. Device sync (9 events)

| Event                     | Parameters                                                                      | What it does                                                                                                                               | Lines     | Mode              |
|---------------------------|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|-----------|-------------------|
| `OnShareTaskClicked`      | `taskId: String`                                                                | Opens the sheet (`deviceShareTaskId`), clears `deviceSending`                                                                              | D 155-156 | sync              |
| `OnDismissShareSheet`     | -                                                                               | `deviceSync.cancelSend()`, closes the sheet                                                                                                | D 158-161 | sync              |
| `OnSendToDeviceClicked`   | `taskId: String, deviceId: String, mode: SendMode, deleteAfterSuccess: Boolean` | `startDeviceSend` (`:203-252`): builds `SendPayload` / `SendRequest`; paired -> send, else `pendingDeviceSend` + `deviceSync.startPairing` | D 163     | sync + async send |
| `OnConfirmPairingClicked` | -                                                                               | `deviceSync.confirmPairing()`, then sends the pending request once if the pairing was stored                                               | D 165-176 | sync + async      |
| `OnCancelPairingClicked`  | -                                                                               | `deviceSync.cancelPairing()`, clears the pending send and `devicePairing`                                                                  | D 178-182 | sync              |
| `OnBlockPairingClicked`   | -                                                                               | `deviceSync.blockPairing()`, clears the pending send and `devicePairing`                                                                   | D 186-190 | sync              |
| `OnUnpairDeviceClicked`   | `deviceId: String`                                                              | `deviceSync.unpair`                                                                                                                        | D 184     | sync              |
| `OnUnblockDeviceClicked`  | `deviceId: String`                                                              | `deviceSync.unblock`                                                                                                                       | D 192     | sync              |
| `OnManualConnectClicked`  | `ip: String`                                                                    | `deviceSync.startPairingWithHost(ip.trim())` when not empty                                                                                | D 194-197 | sync              |

Events the UI never sends: `OnDownloadSpeedLimitChanged`, `OnUploadSpeedLimitChanged`,
`OnConfirmCancelClicked`, `OnDismissCancelDialog`; `OnThemeToggled` and `OnApplySettingsClicked`
are passed down as lambdas that no page invokes.

Events the view model sends to itself: `OnUrlChanged` and `OnToggleAddDialog(true)` (external
requests, clipboard offer, id-only playlists), from bridge threads and from `scope` coroutines.

### 2.2 `DashboardUiEffect` (`DASH/DashboardContract.kt:449-453`)

One effect: `data class ShowToast(val text: String)`.

- Flow: `_uiEffect = MutableSharedFlow<DashboardUiEffect>(extraBufferCapacity = 8)`
  (`DashboardViewModel.kt:56`), exposed as `uiEffect: SharedFlow<DashboardUiEffect>` (`:58`).
  No replay: an effect emitted while nobody collects is lost.
- How the UI shows it (`UI/App.kt:170-177`): `snackbarHostState.showSnackbar(effect.text)`. The
  host is a Material 3 `SnackbarHost` at the bottom centre, 88 dp above the bottom edge
  (`App.kt:877-882`); a second host is inside the details sheet window (`App.kt:541-545`).
  `showSnackbar` suspends until the snackbar is gone (about 4 s), so toasts are shown one after
  another and the collector is slow on purpose.
- Emitters (all texts already localized): download started / could not start
  (`DashboardViewModel.kt:406-420`, `DashboardAddDownload.kt:228-233`), batch from the browser
  (`DashboardViewModel.kt:515-518`), qualities / playlist / link list added
  (`DashboardAddDownload.kt:326-333`, `:451-458`, `:558-562`), links exported
  (`DashboardTaskEvents.kt:260-275`), tools downloaded / updated (`DashboardSettingsEvents.kt:233`,
  `DashboardTools.kt:25`), device receive / sent (`DashboardDeviceSync.kt:114`, `:301`, with
  `tryEmit`).

Other one-shot signals that are **not** effects but behave like them:

- `scrollToTopSignal` (state counter): `UI/TaskList.kt:269-271` scrolls the list to item 0.
- `ExternalDownloadBridge.showAndFocus()`: host callback, brings the window to the front.
- `SystemNotice.show(id, title, text)`: tray notification (desktop actual exposes
  `SystemNotice.notices: SharedFlow<Pair<String, String>>`, collected in `main.kt:762-768`).
- `ExternalDownloadBridge.openInBrowserFallback(url)`: host callback, opens the default browser.
- `WhenDone.onExitApp()`: host callback, quits the app.

---

## 3. The view model

### 3.1 Public API (`DASH/DashboardViewModel.kt`)

```kotlin
class DashboardViewModel(
    internal val queueRepository: QueueManagerRepository = QueueManagerRepository.getInstance()
) : androidx.lifecycle.ViewModel()

val uiState: StateFlow<DashboardUiState>
val uiEffect: SharedFlow<DashboardUiEffect>
fun onEvent(event: DashboardUiEvent)
fun handleExternalRequest(request: ExternalDownloadRequest)
fun handleExternalBatch(requests: List<ExternalDownloadRequest>, review: Boolean = false)
```

Everything else is `internal` (the handler files are extension functions in the same package
and module). A bridge that lives in the `sharedLogic` module (for example in `mingwX64Main`) can
reach the `internal` members; a separate module cannot.

Creation today: `androidx.lifecycle.viewmodel.compose.viewModel { DashboardViewModel() }`
(`UI/App.kt:86-87`), i.e. one instance per window, created during the first composition. On the
native side `DashboardViewModel()` can be constructed directly; `ViewModel.clear()` is only
reachable through a `ViewModelStore`, which a process-lived instance does not need.

Singletons it uses and therefore creates on first use: `QueueManagerRepository.getInstance()`
(loads `tasks_cache.json`, starts its own `CoroutineScope(Dispatchers.IO + SupervisorJob())` with
a 1 s queue pump, notification / checksum / persistence observers), `AppSettingsRepository`,
`BehaviorSettings`, `SpeedSchedule`, `AppLanguage`, `ExternalDownloadBridge`, `SiteCookieJar`,
`MediaToolsLock`, `WhenDone`, `DeviceSync` (lazy), `SystemNotice`.

### 3.2 What `init` starts (`DashboardViewModel.kt:106-173`)

In order:

1. `observeQueue()` (`:564-600`): `scope.launch { queueRepository.allTasksFlow.collect { ... } }`.
   On every emission: `WhenDone.onTasks(tasks, scope)` (may launch a 60 s countdown on `scope`),
   then `allTasks`, `filteredTasks`, both total speeds.
2. `observeBehaviorSettings()` (`:525-562`): 4 collectors on `scope` for
   `BehaviorSettings.connections`, `.monitorClipboard`, `.clipboardVideoPages`,
   `.sortIntoFolders`.
3. Synchronous: `settingsRepository.loadSettings()` (reads `settings.properties`),
   `AppLanguage.change(savedSettings.language)`, copies 15 settings into the state.
4. `refreshToolStatus()` (`DashboardTools.kt:51-80`): one coroutine; probes yt-dlp, ffmpeg, JS
   runtime, tools folder, torrent engine and writes 8 state fields.
5. `autoUpdateTools()` (`DashboardTools.kt:9-28`): one coroutine; `autoUpdateMediaTools`
   under `toolsMutex` (network), progress through `reportToolProgress`, then
   `refreshToolStatus()` again and a toast when something changed.
6. Synchronous: applies saved global download / upload limits to the repository when > 0.
7. `SpeedSchedule.start { limit -> queueRepository.setGlobalDownloadSpeedLimit(limit) }`
   (`:153-155`): process-lived ticker, every 30 s, on `Dispatchers.Default`; later calls are
   ignored.
8. Collector on `scope`: `queueRepository.globalDownloadSpeedLimit` -> state (`:157-164`).
9. Synchronous: installs `ExternalDownloadBridge.onExternalRequest` and `.onExternalBatch`
   (`:166-169`). Setting `onExternalRequest` replays requests that arrived before (a link passed
   at launch), on the constructing thread.
10. `initDeviceSync()` (`DashboardDeviceSync.kt:32-64`): deletes empty `recv-*` folders,
    `deviceSync.setVisible(BehaviorSettings.visibleToDevices.value)`, `deviceSync.start()`
    (UDP 28461 / TCP 28462 listeners inside the platform implementation), and 6 collectors on
    `scope`: `visibleToDevices`, `discovered`, `paired`, `blocked`, `pairing`, `sending`.

Total: 12 long-lived collectors, 2 one-shot coroutines, 1 external ticker. Later, on demand:
`toolStatusPollJob` (2 s x 150, only one at a time), the `WhenDone` countdown, per-action
coroutines (inspect, enqueue, send).

### 3.3 Threading as written

Scopes and dispatchers:

| Scope                          | Definition                                                                                                               | Dispatcher                                                                                      |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `viewModelScope`               | from androidx lifecycle                                                                                                  | `Dispatchers.Main.immediate`, or nothing when Main is missing                                   |
| `scope` (`:40-42`)             | `CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO + SupervisorJob(viewModelScope.coroutineContext[Job]))` | `Dispatchers.IO` (the later element replaces Main). Every coroutine of the view model runs here |
| `QueueManagerRepository.scope` | `CoroutineScope(Dispatchers.IO + SupervisorJob())` (`QueueManagerRepository.kt:152`)                                     | IO                                                                                              |
| `SpeedSchedule.scope`          | `CoroutineScope(Dispatchers.Default + SupervisorJob())`                                                                  | Default                                                                                         |
| `UpdateManager.scope`          | `CoroutineScope(Dispatchers.IO + SupervisorJob())`                                                                       | IO                                                                                              |

`viewModelScope` is referenced only on line 41, as the parent job. Nothing is ever launched on
it.

Who calls `onEvent` today:

- the Compose main thread (every UI callback);
- bridge HTTP threads: `ExternalDownloadBridge.dispatch` -> `handleExternalRequest` ->
  `onEvent(OnUrlChanged)` + `onEvent(OnToggleAddDialog(true))` (`:238-239`, `:267-268`,
  `:298-299`);
- `scope` coroutines on IO threads: `startExternalDirectly` (`:390-391`),
  `handleExternalBatch` (`:502-503`), `inspectMedia` (`:726`).

So `onEvent` is already entered from several threads at once. There is no single-thread
confinement and no lock; correctness rests on `MutableStateFlow.update` being atomic per call.

Fields mutated outside `StateFlow.update` (plain `var`, no `@Volatile`, no lock):

| Field                                               | Line | Written from                                          |
|-----------------------------------------------------|------|-------------------------------------------------------|
| `pendingExternalUrl: String?`                       | 60   | UI thread, bridge threads, IO coroutines              |
| `pendingExternalHeaders: Map<String, String>`       | 61   | same                                                  |
| `pendingExternalFileName: String?`                  | 62   | same                                                  |
| `pendingExternalPlaylist: MediaPlaylist?`           | 63   | same                                                  |
| `pendingSelectedFormatId: String?`                  | 67   | bridge thread (`openClipDialog`), IO (`inspectMedia`) |
| `pendingExternalConflict: ExternalDownloadRequest?` | 70   | IO (`startExternalDirectly`), UI thread               |
| `singleVideoUrl: String?`                           | 74   | UI thread, IO (`addPlaylist`)                         |
| `toolStatusPollJob: Job?`                           | 91   | whoever calls `OnToggleAddDialog(true)`               |
| `pendingDeviceSend: SendRequest?`                   | 94   | UI thread                                             |

Other non-atomic patterns:

- read-then-update sequences: `_uiState.value` is read, a decision is taken, then `update` runs
  (`OnSpeedPresetChanged`, `OnToggleClipRange`, `addDownload` works on a `currentState`
  snapshot, `refreshSuggestedFileName`). A concurrent event can interleave.
- `update { }` lambdas can run more than once under contention; all of them are pure except
  for the captured snapshot they copy from.
- `_uiEffect.emit` suspends the emitting coroutine when 8 effects are buffered and a collector
  is slow (the Compose collector waits about 4 s per snackbar). `tryEmit` (device sync) drops
  instead.
- Singletons touched from the UI thread and from workers without locks:
  `ExternalDownloadBridge.pending` (a `mutableListOf`, `ExternalDownloadBridge.kt:49`) and its
  callback `var`s, `WhenDone.running` / `countdown`, `SpeedSchedule.lastApplied` / `apply` /
  `started`, `NetworkSettings.lockedLogins`, `AppLanguage.choice`, `AppLog.verbose`.

Synchronous work inside `onEvent` (runs on the caller, today the UI thread):

- `persistCurrentSettings()` (`:175-212`, rewrites `settings.properties`) in 10 events
  (`OnDestinationPathChanged`, `OnDisableTorrentUploadToggled`, both global limits,
  `OnSpeedDisplayUnitChanged`, `OnSpeedPresetChanged`, `OnApplySettingsClicked`,
  `OnLanguageChanged`, `OnThemeToggled`, `OnThemeModeChanged`);
- `BehaviorSettings.set*` (rewrite `behavior.properties`) in 4 events;
- `OnToggleAddDialog`: `isMediaExtractorToolInstalled()` and `getMediaExtractorToolStatus()`;
- `addDownload`: `FileSystem.SYSTEM.exists` and the `getUniqueFileName` loop;
- `OnUrlChanged`: a regex over the pasted text and link-type detection for up to 500 links;
- `pauseAll()` / `resumeAll()`, torrent file selection / priority, task priority;
- `deviceSync.*` calls (`cancelSend`, `confirmPairing`, `unpair`, ...).

All of these are millisecond-scale file or memory operations; none waits on the network.

### 3.4 mingwX64: what breaks without a Main dispatcher, and what the replacement must guarantee

Verified facts:

- `kotlinx-coroutines-core` 1.11.0 for mingwX64 has no Main dispatcher: `Dispatchers.Main` is
  `MissingMainDispatcher`, whose members fail with "Dispatchers.Main is missing on the current
  platform" (class name and message found in the cached
  `kotlinx-coroutines-core-mingwX64Main-1.11.0.klib`; in the library source the failure is a
  `TODO(...)`, i.e. `NotImplementedError`). This was read from the artifacts, not executed.
- `androidx.lifecycle:lifecycle-viewmodel` 2.11.0 has a mingwX64 artifact (in the Gradle cache)
  and its `createViewModelScope()` catches exactly that:
  `try { Dispatchers.Main.immediate } catch (_: NotImplementedError) { EmptyCoroutineContext }
  catch (_: IllegalStateException) { EmptyCoroutineContext }`
  (`commonMain/androidx/lifecycle/viewmodel/internal/CloseableCoroutineScope.kt` in the sources
  jar). So `viewModelScope` becomes `SupervisorJob()` with no dispatcher.
- `DashboardViewModel.scope` adds `Dispatchers.IO` explicitly (import `kotlinx.coroutines.IO`,
  line 14, which is the Native spelling), so nothing in the view model depends on Main.
- `grep "Dispatchers.Main"` over `sharedLogic/src/commonMain`, `jvmMain`, `jvmAndroidMain`,
  `sharedUI/src` and `desktopApp/src`: **0 hits**.

Therefore, in the view model itself **nothing breaks** on mingwX64. `DashboardViewModel()`
constructs, its collectors run on the IO pool, `uiState` updates as on the JVM.

What is lost is everything Compose did on its main thread, and a replacement must provide it:

1. **State delivery.** `collectAsState()` collected `uiState` and 18 other flows (section
   4.1) on the UI thread. A native bridge collects on a worker thread; Slint properties and models
   may only be
   touched on the Slint event-loop thread, so every push must go through
   `slint::invoke_from_event_loop` (or the UI thread pulls a snapshot when notified).
   Guarantee: Slint objects are never touched from a Kotlin thread.
2. **Latest-wins, in order.** State is conflated by `StateFlow`; the bridge must keep that:
   never queue stale snapshots, never apply an older snapshot after a newer one. A version
   counter per snapshot section is enough.
3. **Rate limit.** Engines publish every 200 ms per HTTP task (`HttpChunkWorkers.kt:102`),
   every 700 ms for torrents (`TorrentDownloader.kt:604`, `POLL_MS`), every 250 ms for HLS.
   Compose skipped unchanged frames by itself; the bridge must throttle the task section (4 to 5
   pushes per second is what the eye needs) and stop pushing while the window is hidden
   in the tray.
4. **Ordered, serialized UI events.** Compose delivered UI events one at a time on one thread.
   The UI relies on order: `OnRemoveClicked(id)` immediately followed by
   `OnConfirmRemoveClicked(false)` (`UI/App.kt:792-793`), `OnClipStartTimeChanged` followed by
   `OnClipEndTimeChanged` (quick chips, `UI/ui/AddDownloadMedia.kt:863-902`), `OnUrlChanged`
   followed by the auto-inspect. Guarantee: events from the UI reach `onEvent` FIFO, one at a
   time. Either call `onEvent` directly on the Slint thread (same behaviour as today, the
   synchronous file writes listed in 3.3 then run on the UI thread) or post them to a
   single-threaded Kotlin dispatcher / channel (UI thread never blocks; still FIFO).
5. **Effects are not dropped and do not block.** Start collecting `uiEffect` right after the
   view model is constructed, hand each effect to a non-blocking host queue, let the UI show
   them one after another. A collector that blocks stalls every `emit` caller after 8 effects.
6. **UI-thread timers.** These ran as `LaunchedEffect`s and need an owner on the Kotlin side:
   the clipboard poll (800 ms, `UI/App.kt:100-128`), the tool-status poll is already in the view
   model, the post-process elapsed clock (1 s, `UI/TaskDetails.kt:756-761`), the "Starts at ..."
   texts that depend on the current time.
7. **Suspend calls made from the UI.** `StartupLauncher.isEnabled()` / `setEnabled()`,
   `ExtensionStore.versionFor()` / `githubXpi()` / `download()`, `getClipboardText()` were
   launched from composition scopes. The bridge needs its own scope for them and must publish
   their results as state.
8. **Callbacks into the host** (`onShowAndFocus`, `openInBrowserFallback`, `onExitApp`, file
   pickers) are invoked from arbitrary Kotlin threads. The C++ side must only enqueue to the
   Slint event loop from them, never touch UI objects directly.
9. **Thread affinity of Win32 APIs** used by future mingw actuals: clipboard access and the
   file / folder dialogs want a thread with a message loop (STA for the shell dialogs). Running
   them on a coroutine worker is not safe by default; either the C++ host performs them on the
   UI thread and returns the result as an event, or the actual marshals to a dedicated thread.

Not required: a real `Dispatchers.Main` implementation. Nothing calls it. Installing one
(`Dispatchers.setMain` exists only in `kotlinx-coroutines-test`) would add risk for no gain.

---

## 4. Other `sharedLogic` symbols used directly by `sharedUI` and `main.kt`

Found with `grep "com.michelle.raouf.fetch_jet.(core|domain|feature|presentation)"` over
`sharedUI/src` and `desktopApp/src` (imports and fully qualified uses). Domain models and the
contract types are in sections 1 and 2.

### 4.1 Observable singletons (the UI collects their flows)

| Symbol                                                                  | Kind                                                    | What the UI reads                                                                                                                                                                                        | What the UI calls                                                                                                                                                                                                                                                   | Changes                                                                              | Where used                                                                                                                          |
|-------------------------------------------------------------------------|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `core.update.UpdateManager`                                             | `object`                                                | `state: StateFlow<UpdateState>`                                                                                                                                                                          | `onAppStart()`, `checkNow()`, `setAutoCheck(Boolean)`, `setAutoInstall(Boolean)`, `showDialog()`, `dismiss()`, `skip()`, `installNow()`                                                                                                                             | On check (start + manual), during the installer download (progress), on user actions | `UI/ui/UpdateUi.kt:48-292`, `UI/ui/FdmSettingsPanel.kt:147`                                                                         |
| `core.update.PlatformUpdater`                                           | `expect object`                                         | `updatesFromStore: Boolean`, `platformKey: String`, `canInstallInApp`                                                                                                                                    | `pickAsset(assets): ReleaseAsset?`                                                                                                                                                                                                                                  | Constant                                                                             | `UI/ui/UpdateUi.kt:60-61, 132, 167, 270, 280`                                                                                       |
| `core.network.NetworkSettings`                                          | `object`                                                | `proxy: StateFlow<ProxySettings>`, `logins: StateFlow<List<SiteLogin>>`                                                                                                                                  | `setProxy(ProxySettings)`, `saveLogin(SiteLogin)`, `removeLogin(site: String)`                                                                                                                                                                                      | Only on user edits                                                                   | `UI/ui/NetworkSettingsUi.kt`, `UI/ui/FdmSettingsPanel.kt:151-152`                                                                   |
| `core.power.SpeedSchedule`                                              | `object`                                                | `enabled: StateFlow<Boolean>`, `windows: StateFlow<List<SpeedWindow>>`, `activeCap: StateFlow<Long?>`                                                                                                    | `setEnabled(Boolean)`, `addWindow(SpeedWindow)`, `updateWindow(index, SpeedWindow)`, `removeWindow(index)`                                                                                                                                                          | User edits; `activeCap` at window boundaries (30 s ticker)                           | `UI/ui/SettingsPages.kt:433-530`, `UI/HomeNavigation.kt:333`                                                                        |
| `core.power.WhenDone`                                                   | `object`                                                | `action: StateFlow<WhenDoneAction>`, `secondsLeft: StateFlow<Int?>`, `pending: StateFlow<WhenDoneAction>`, `error: StateFlow<String?>`, `COUNTDOWN_SECONDS = 60`                                         | `choose(WhenDoneAction)`, `cancel()`, `runNow()`, `dismissError()`; host sets `onExitApp`                                                                                                                                                                           | `secondsLeft` every second during the countdown, else on user action                 | `UI/ui/WhenDoneUi.kt`, `main.kt:799-800, 805, 840-849`                                                                              |
| `core.power.SystemPower`                                                | `expect object`                                         | `isSupported: Boolean`                                                                                                                                                                                   | - (called by `WhenDone`)                                                                                                                                                                                                                                            | Constant                                                                             | `UI/ui/WhenDoneUi.kt:44`                                                                                                            |
| `core.network.BridgePairing`                                            | `object`                                                | `pending: StateFlow<Request?>`, `legacyAllowed: StateFlow<Set<String>>`, `blocked: StateFlow<Set<String>>`, `seenBrowsers: StateFlow<Map<String, Long>>`, `seenVersions: StateFlow<Map<String, String>>` | `answer(allow: Boolean)`, `block()`, `revokeAllowed(origin)`, `unblock(origin)`; host: `token`, `TOKEN_HEADER`, `isPaired`, `isLegacyAllowed`, `isExtensionOrigin`, `isBlocked`, `ask(origin, legacy)` (suspends up to 120 s), `noteBrowser(ua, nowMs, extVersion)` | `pending` when an extension asks; `seen*` at most hourly per browser                 | `UI/App.kt:814-827`, `UI/ui/SettingsPages.kt:726-757`, `UI/ui/BrowserInstallSection.kt:64-65`, `main.kt:111, 130, 223-243, 417-475` |
| `core.settings.BehaviorSettings`                                        | `object`                                                | `visibleToDevices: StateFlow<Boolean>` (the other four are mirrored into the state)                                                                                                                      | `setVisibleToDevices(Boolean)`                                                                                                                                                                                                                                      | User toggle                                                                          | `UI/ui/FdmSettingsPanel.kt:149-150`, `UI/ui/SettingsPages.kt:159-167`                                                               |
| `feature.queue_manager.QueueManagerRepository`                          | class, singleton `getInstance()`                        | `maxConcurrentDownloads: StateFlow<Int>` (0..20, 0 = no limit, default 3)                                                                                                                                | `setMaxConcurrentDownloads(Int)`; `main.kt`: `inspectMediaQualities(url, headers)` (suspend)                                                                                                                                                                        | User edit                                                                            | `UI/ui/QueueUi.kt:20-43`, `UI/ui/FdmSettingsPanel.kt:148`, `UI/TaskList.kt:429`, `main.kt:179-180`                                  |
| `core.i18n.start.AppLanguage`                                           | `object`                                                | `current: StateFlow<String>` (active locale code), `choice: String` (`"system"` or a code), `isRtl: Boolean`, `SYSTEM = "system"`                                                                        | (changed through `OnLanguageChanged`)                                                                                                                                                                                                                               | On language change                                                                   | `UI/App.kt:179-184`, `UI/ui/SettingsPages.kt:760-795`, `main.kt:430, 804`                                                           |
| `core.notification.DownloadNotificationManager` (jvm actual, companion) | `StateFlow<Map<String, JvmTaskProgress>>` `activeTasks` | `taskId`, `fileName`, `progressPercent`, `speedText` of running tasks (tray tooltip, first 6)                                                                                                            | -                                                                                                                                                                                                                                                                   | With task progress                                                                   | `main.kt:803-822`                                                                                                                   |
| `core.notification.SystemNotice` (jvm actual)                           | `SharedFlow<Pair<String, String>>` `notices`            | title, text                                                                                                                                                                                              | -                                                                                                                                                                                                                                                                   | Device sent / received                                                               | `main.kt:762-768`                                                                                                                   |

Types that travel with them:

- `ProxyKind` enum: `NONE`, `HTTP`, `SOCKS5`. `ProxySettings(kind = NONE, host = "", port = 0,
  user = "", password = "")` with `isOn`, `hasLogin` (`SL/core/network/NetworkSettings.kt:27-50`).
  `SiteLogin(site, user, password)` with `SiteLogin.cleanSite(text)` (`:52-66`). The UI reads the
  stored proxy password back into its password field (`NetworkSettingsUi.kt:64`).
- `SpeedWindow(startMinutes = 0, endMinutes = 0, capBytes = 1 MiB, capUnit = "MB/s")`, minutes
  since local midnight (`SL/core/power/SpeedSchedule.kt:21-36`).
- `WhenDoneAction` enum: `NOTHING`, `EXIT`, `SLEEP`, `SHUT_DOWN` (`SL/core/power/WhenDone.kt:14`).

### 4.2 Non-observable objects and functions

| Symbol                                                                                                                                                 | Kind                                                        | What the UI reads / calls                                                                                                                                                                                                                                                                                                                                                                                   | Changes                                     | Where used                                                                                                                                                                                                                                                      |
|--------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `core.filesystem.PathResolver`                                                                                                                         | `expect class` (the UI creates instances)                   | `getDownloadsDirectory()`, `getDocumentsDirectory()`, `getDesktopDirectory()`, `isOpenFolderSupported()`, `openDirectoryInExplorer(path)`, `openFileLocation(filePath)`, `openFile(filePath)`, `fileExists(filePath)`, `getAvailableDirectories(): List<Pair<String, String>>`, `listSubdirectories(path, includeHidden)`, `createSubdirectory(parent, name): String?`, `getParentDirectory(path): String?` | Per call                                    | `UI/App.kt:68-77, 90`, `UI/TaskDetails.kt:93, 603-604`, `UI/ui/DirectoryPickerDialog.kt:58-73, 288`, `UI/ui/AddDownloadModalDialog.kt:122-124`, `UI/ui/AddDownloadSections.kt:395`, `UI/ui/SettingsPages.kt:266, 303-305`, `UI/ui/SettingsMediaToolsPage.kt:87` |
| `core.filesystem.TextFilePicker`                                                                                                                       | `expect object`                                             | `isSupported`, `pick(onPicked: (String?) -> Unit)` (returns the file's **text**)                                                                                                                                                                                                                                                                                                                            | Per call                                    | `UI/ui/AddDownloadSections.kt:121-124`                                                                                                                                                                                                                          |
| `core.filesystem.TorrentFilePicker`                                                                                                                    | `expect object`                                             | `isSupported`, `pick(onPicked: (String?) -> Unit)` (returns a local **path**)                                                                                                                                                                                                                                                                                                                               | Per call                                    | `UI/ui/AddDownloadSections.kt:142-147`                                                                                                                                                                                                                          |
| `getClipboardText()` (`SL/Platform.kt:10`)                                                                                                             | `expect suspend fun`: `String?`                             | Clipboard watcher and the Paste button                                                                                                                                                                                                                                                                                                                                                                      | Polled every 800 ms while the watcher is on | `UI/App.kt:106, 115`, `UI/ui/AddDownloadSections.kt:187`                                                                                                                                                                                                        |
| `getPlatform()` (`SL/Platform.kt:8`)                                                                                                                   | `expect fun`: `Platform` (`name: String`, `isJvm: Boolean`) | `isJvm` is used as "this is the desktop app": keyboard selection, Media and Torrent-engine settings, Browser page, scrollbars                                                                                                                                                                                                                                                                               | Constant                                    | `UI/ui/FdmSettingsPanel.kt:145-146`, `UI/TaskList.kt:148`, `UI/ui/AppScrollbar.kt`                                                                                                                                                                              |
| `core.edition.AppFeatures`                                                                                                                             | `object`                                                    | `mediaExtractor: Boolean`, `torrent: Boolean` (hide tabs, filters, engine chips, settings pages)                                                                                                                                                                                                                                                                                                            | Constant                                    | `UI/HomeNavigation.kt:118-127, 250-252`, `UI/ui/FdmSettingsPanel.kt:155-156`, `UI/ui/AddDownloadSections.kt:142, 222, 230`, `main.kt:416, 429`                                                                                                                  |
| `core.settings.ClipboardLinks`                                                                                                                         | `object`                                                    | `downloadableUrl(text, videoPages): String?`                                                                                                                                                                                                                                                                                                                                                                | Pure                                        | `UI/App.kt:121-125`                                                                                                                                                                                                                                             |
| `core.settings.FileCategory`                                                                                                                           | `object`                                                    | `folderFor(fileName, isMedia, isTorrent): String?`, `stripTypeFolders(path, keep): String`, `allFolders`                                                                                                                                                                                                                                                                                                    | Pure                                        | `UI/ui/AddDownloadSections.kt:317-336`                                                                                                                                                                                                                          |
| `core.i18n.start.appLanguages`                                                                                                                         | `val List<AppLocalization>` (15)                            | `localeCode`, `languageName` of each                                                                                                                                                                                                                                                                                                                                                                        | Constant                                    | `UI/ui/SettingsPages.kt:766-781`                                                                                                                                                                                                                                |
| `core.i18n.start.localizeStep(text)`                                                                                                                   | `fun`: `String`                                             | Translates English engine / status texts for display                                                                                                                                                                                                                                                                                                                                                        | Pure (depends on the language)              | `UI/TaskDetails.kt`, `UI/ui/TaskRows.kt`, `UI/ui/AddDownloadMedia.kt`, `UI/ui/SettingsPages.kt:641`, `UI/ui/SettingsMediaToolsPage.kt:148`                                                                                                                      |
| `core.i18n.start.start` (`LocalizationManager.start()`)                                                                                                | extension `fun`                                             | Registers the 15 providers, default "en", applies the saved language                                                                                                                                                                                                                                                                                                                                        | Once                                        | `main.kt:697`                                                                                                                                                                                                                                                   |
| `core.i18n.data_provider.Strings` (`LocalizationManager.Strings`)                                                                                      | extension `val`: `AppLocalization`                          | Every visible text: **625 `val` strings and 138 `fun` strings with parameters** (`SL/core/i18n/data_provider/AppLocalization.kt`, 793 lines)                                                                                                                                                                                                                                                                | On language change                          | 33 files                                                                                                                                                                                                                                                        |
| `core.startup.StartupLauncher`                                                                                                                         | `expect object`                                             | `isSupported`, `managedBySystemUri: String?`, `suspend isEnabled()`, `suspend setEnabled(Boolean): Result<Unit>`                                                                                                                                                                                                                                                                                            | Per call                                    | `UI/ui/StartupUi.kt`, `UI/ui/SettingsPages.kt:140`                                                                                                                                                                                                              |
| `core.startup.StartupPreferences`                                                                                                                      | `object`                                                    | `startHidden: Boolean` (get / set, file-backed), `launchedAtLogin` (set by the host)                                                                                                                                                                                                                                                                                                                        | User edit                                   | `UI/ui/StartupUi.kt:28-95`, `main.kt:731, 751`                                                                                                                                                                                                                  |
| `core.startup.AUTOSTART_ARG`                                                                                                                           | `const` `"--autostart=true"`                                | Launch argument filter                                                                                                                                                                                                                                                                                                                                                                                      | Constant                                    | `main.kt:727, 733`                                                                                                                                                                                                                                              |
| `core.browser.BrowserIntegration`                                                                                                                      | `expect object`                                             | `browsers(): List<BrowserInfo>`, `addExtension(id): AddExtensionResult`, `installXpi(id, bytes, fileName): AddExtensionResult`, `openFolder(folder)`, `openBrowser(id)`, `setUpOnFirstRun()` (host). Android-only: `canKeepReadyForBrowser`, `isKeptReadyForBrowser()`, `setKeepReadyForBrowser()`, `canPopUpOverBrowser`, `isPopUpAllowed()`, `requestPopUpPermission()`                                   | Per call                                    | `UI/ui/BrowserInstallSection.kt`, `UI/ui/SettingsPages.kt:684-712`, `main.kt:720`                                                                                                                                                                               |
| `core.browser.BrowserInfo`                                                                                                                             | data class                                                  | `id`, `name`, `installed`, `downloadUrl`, `hasLocalCopy`                                                                                                                                                                                                                                                                                                                                                    |                                             | `UI/ui/BrowserInstallSection.kt`                                                                                                                                                                                                                                |
| `core.browser.AddExtensionResult`                                                                                                                      | sealed                                                      | `Opened`, `LoadUnpacked(folder, page)`, `InstallFromFile(fileName)`, `NotInstalled`, `Failed(reason)`                                                                                                                                                                                                                                                                                                       |                                             | `UI/ui/BrowserInstallSection.kt:73-84`                                                                                                                                                                                                                          |
| `core.browser.ExtensionStore`                                                                                                                          | `object`                                                    | `suspend versionFor(id): String?`, `suspend githubXpi(): SignedXpi?`, `suspend download(xpi): ByteArray?`; `SignedXpi(version, url, fileName)`                                                                                                                                                                                                                                                              | Network, per call                           | `UI/ui/BrowserInstallSection.kt:96-130`                                                                                                                                                                                                                         |
| `core.browser.EDGE_EXTENSION_ID`, `FIREFOX_ADDON_URL`                                                                                                  | `const`                                                     | Store links                                                                                                                                                                                                                                                                                                                                                                                                 | Constant                                    | `UI/ui/BrowserInstallSection.kt:45-46, 132`                                                                                                                                                                                                                     |
| `core.update.APP_VERSION` (`"1.4.0"`), `EXTENSION_VERSION` (`"3.3.0"`), `UPDATE_REPOSITORY`, `MICROSOFT_STORE_PAGE`, `isOlderExtension(found, wanted)` | `const` / `fun`                                             | Status bar version, extension rows, links                                                                                                                                                                                                                                                                                                                                                                   | Constant                                    | `UI/TaskList.kt:465`, `UI/ui/BrowserInstallSection.kt`, `UI/ui/UpdateUi.kt:178, 289`                                                                                                                                                                            |
| `core.log.DiagnosticsLog`                                                                                                                              | `expect object`                                             | `canExport`, `export(): String?` (Android only today)                                                                                                                                                                                                                                                                                                                                                       | Per call                                    | `UI/ui/SettingsPages.kt:81-105`                                                                                                                                                                                                                                 |
| `core.log.AppLog`                                                                                                                                      | `object`                                                    | `verbose` (set by the host)                                                                                                                                                                                                                                                                                                                                                                                 | Once                                        | `main.kt:700-704`                                                                                                                                                                                                                                               |
| `core.log.RedactingOutputStream`                                                                                                                       | jvm class                                                   | Log file redaction                                                                                                                                                                                                                                                                                                                                                                                          | -                                           | `main.kt:286`                                                                                                                                                                                                                                                   |
| `core.util.currentEpochMs()`, `localUtcOffsetMs()`, `formatLocalSchedule(epochMs)`, `localTimeOnDayEpochMs(dayOffset, hour, minute)`                   | `fun`                                                       | Schedule section, "Starts at", "Added"                                                                                                                                                                                                                                                                                                                                                                      | Pure / clock                                | `UI/ui/PlaylistScheduleUi.kt`, `UI/ui/TaskRows.kt:94-95`, `UI/TaskDetails.kt:413-417, 912-916`                                                                                                                                                                  |
| `core.util.MiniJson`, `getString`                                                                                                                      | `object` / `fun`                                            | Bridge request bodies                                                                                                                                                                                                                                                                                                                                                                                       | -                                           | `main.kt:489-533`                                                                                                                                                                                                                                               |
| `feature.media_extractor.AUDIO_CONVERT_FORMATS`                                                                                                        | `val` `listOf("mp3", "m4a", "opus", "flac", "wav", "ogg")`  | Chips of the convert section                                                                                                                                                                                                                                                                                                                                                                                | Constant                                    | `UI/ui/AudioConvertSection.kt`                                                                                                                                                                                                                                  |
| `feature.media_extractor.MediaUrls.canonical(url)`                                                                                                     | `fun`                                                       | Cache key of the bridge's inspect cache                                                                                                                                                                                                                                                                                                                                                                     | Pure                                        | `main.kt:162`                                                                                                                                                                                                                                                   |
| `feature.media_extractor.parseExternalPlaylist(any)`                                                                                                   | `fun`                                                       | `/add` body `playlist`                                                                                                                                                                                                                                                                                                                                                                                      | Pure                                        | `main.kt:524-526`                                                                                                                                                                                                                                               |
| `core.network.ExternalDownloadBridge`                                                                                                                  | `object`                                                    | Host sets `onShowAndFocus`, `openInBrowserFallback`; calls `dispatch(request)`, `dispatch(url, cookie)`, `dispatchBatch(requests)`; the UI calls `showAndFocus()`                                                                                                                                                                                                                                           | Per request                                 | `main.kt:591, 617, 672, 779-797, 902`, `UI/App.kt:137`                                                                                                                                                                                                          |
| `core.network.ExternalDownloadRequest`                                                                                                                 | data class                                                  | Built by the bridge server (`url`, `cookie`, `referer`, `userAgent`, `fileName`, `mediaQualities`, `direct`, `openClip`, `audioUrl`, `playlist`, `reading`, `readingFailed`, `group`)                                                                                                                                                                                                                       |                                             | `main.kt:495-535`                                                                                                                                                                                                                                               |
| `core.network.SiteCookieJar.remember(url, headers)`                                                                                                    | `fun`                                                       | Bridge `/inspect`                                                                                                                                                                                                                                                                                                                                                                                           |                                             | `main.kt:373-375`                                                                                                                                                                                                                                               |
| `core.platform.WindowsStorePackage.looksLikeLoginStart()`                                                                                              | jvm `fun`                                                   | Store build started at login                                                                                                                                                                                                                                                                                                                                                                                | Once                                        | `main.kt:730`                                                                                                                                                                                                                                                   |

---

## 5. Logic and state that live in the Compose UI today

A renderer-only Slint UI cannot keep these. Each needs a home in the Kotlin bridge (or, for pure
view state, in `.slint` properties).

### 5.1 Behaviour implemented in composables

| #  | Behaviour                                                                                                                                                                                                                               | Where                                                                             | Notes for the bridge                                                                                |
|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| 1  | Clipboard watcher: poll every 800 ms while `monitorClipboard`; skip what was in the clipboard at app start; `ClipboardLinks.downloadableUrl(text, clipboardVideoPages)` -> `OnClipboardLink`                                            | `UI/App.kt:95-128`                                                                | A Kotlin coroutine, or a Win32 clipboard-format listener in the host                                |
| 2  | Bring the window forward when `clipboardOfferUrl` becomes non-null                                                                                                                                                                      | `UI/App.kt:136-138`                                                               | Host effect                                                                                         |
| 3  | Wide layout forces `selectedTab` back to `ALL` (the sidebar filters by itself)                                                                                                                                                          | `UI/App.kt:143-148`                                                               | On desktop the tab is effectively `ALL` or `SETTINGS`                                               |
| 4  | Theme: `"system"` resolved with the OS setting; header button sends `OnThemeModeChanged(if (dark) "light" else "dark")`                                                                                                                 | `UI/App.kt:151-156, 226, 360`                                                     | The host knows the OS theme                                                                         |
| 5  | Effects -> snackbar queue                                                                                                                                                                                                               | `UI/App.kt:170-177`                                                               | Section 2.2                                                                                         |
| 6  | Language change re-creates the whole tree and switches RTL (`AppLanguage.isRtl`)                                                                                                                                                        | `UI/App.kt:179-185`                                                               | All strings and the layout direction must be re-sent                                                |
| 7  | Side filter (`SideFilter`: `ALL`, `DOWNLOADING`, `WAITING`, `PAUSED`, `COMPLETED`, `FAILED`, `MEDIA`, `FILES`, `TORRENTS`) with `matches(task)` and per-filter counts                                                                   | `UI/ui/TaskRows.kt:505-541`, `UI/HomeNavigation.kt:222-259`                       | Pure functions of `allTasks`                                                                        |
| 8  | Search by `fileName`, `url`, `originalUrl` (case-insensitive)                                                                                                                                                                           | `UI/TaskList.kt:127-134`                                                          |                                                                                                     |
| 9  | State groups "run" / "wait" / "stop" / "done" with collapse; grouped only for filters `ALL`, `MEDIA`, `FILES`, `TORRENTS`                                                                                                               | `UI/TaskList.kt:483-516`, `UI/App.kt:388-393`                                     |                                                                                                     |
| 10 | Multi-selection: `checkedIds`, anchor, Ctrl / Shift click, Ctrl+A, Esc, Delete; selection pruned when tasks disappear                                                                                                                   | `UI/TaskList.kt:140-190, 280-313`                                                 |                                                                                                     |
| 11 | Row quick action: `rowActionFor(task)`; Pause on a task with `canResume() == false` opens the details instead; Open builds the path and calls `openDownloadedFile`                                                                      | `UI/ui/TaskRows.kt:145-155`, `UI/TaskList.kt:314-330`                             |                                                                                                     |
| 12 | Open a finished file: missing -> `MissingFileDialog` (Remove sends `OnRemoveClicked` + `OnConfirmRemoveClicked(false)`); cannot open -> reveal in folder                                                                                | `UI/App.kt:62-77, 786-797`                                                        |                                                                                                     |
| 13 | "Pausing loses the data" confirmation before `OnPauseClicked`                                                                                                                                                                           | `UI/TaskDetails.kt:95-106`, `UI/HomeDialogs.kt:20-47`                             |                                                                                                     |
| 14 | Selected task for the details pane; closes when the task disappears                                                                                                                                                                     | `UI/App.kt:131, 139`                                                              |                                                                                                     |
| 15 | Scroll to top on `scrollToTopSignal`                                                                                                                                                                                                    | `UI/TaskList.kt:269-271`                                                          | Host effect                                                                                         |
| 16 | Auto-inspect when the link or the engine changes: torrent -> `OnInspectTorrentClicked` if no files, not inspecting, no error; media -> `OnInspectMediaClicked` if no qualities, no playlist, not inspecting, no error, yt-dlp installed | `UI/ui/AddDownloadModalDialog.kt:261-278`                                         | Must be triggered by the bridge after `OnUrlChanged`, `OnEngineSelected`, `OnToggleAddDialog(true)` |
| 17 | Clip validation (start >= end, end > duration, start >= duration) and its message                                                                                                                                                       | `UI/ui/AddDownloadModalDialog.kt:170-204`                                         |                                                                                                     |
| 18 | `canStartDownload` = url not blank && inspection complete && !loading && !inspecting && no error && clip valid; the label of the main button (10 cases)                                                                                 | `UI/ui/AddDownloadModalDialog.kt:280-294`, `UI/ui/AddDownloadSections.kt:500-520` |                                                                                                     |
| 19 | Destination rewrite when "Sort into folders" is on: `stripTypeFolders(destination, [Downloads, Documents, Desktop]) + sep + folderFor(name)` -> `OnDestinationPathChanged`                                                              | `UI/ui/AddDownloadSections.kt:317-336`                                            | Writes the persisted default folder as a side effect                                                |
| 20 | Quick folder buttons (Downloads, Documents, Desktop)                                                                                                                                                                                    | `UI/ui/AddDownloadSections.kt:346-405`                                            |                                                                                                     |
| 21 | Import `.txt` (`TextFilePicker`), open `.torrent` (`TorrentFilePicker`), Paste (`getClipboardText`) -> `OnUrlChanged`                                                                                                                   | `UI/ui/AddDownloadSections.kt:121-197`                                            |                                                                                                     |
| 22 | Schedule section: Now / in 30 min / in 1 h / at a time (12-hour fields, AM / PM, day offset 0, 1, 2, 3, 7), "time passed" error -> `OnScheduleChanged(epochMs)`                                                                         | `UI/ui/PlaylistScheduleUi.kt:202-370`                                             | 64-bit result                                                                                       |
| 23 | Audio-convert section visibility and its "native format"                                                                                                                                                                                | `UI/ui/AddDownloadModalDialog.kt:430-452`                                         |                                                                                                     |
| 24 | "Download only this video" offered when the link contains `v=` or `youtu.be/`                                                                                                                                                           | `UI/ui/AddDownloadMedia.kt:565`                                                   |                                                                                                     |
| 25 | Device sheet: default device = first of (paired first, then by name); default mode = transfer when `canTransferToDevice()`; local `deleteAfter`, search (shown above 5 devices), manual IP                                              | `UI/ui/DeviceSendDialog.kt:64-80, 137-265`                                        |                                                                                                     |
| 26 | Remove dialog: local "also delete files" checkbox, count                                                                                                                                                                                | `UI/ui/RemoveConfirmationDialog.kt:33-77`                                         |                                                                                                     |
| 27 | `UpdateManager.onAppStart()` at first composition                                                                                                                                                                                       | `UI/ui/UpdateUi.kt:50`                                                            | Call once from the bridge                                                                           |
| 28 | Browser rows: connected / outdated / store / GitHub decisions and the button action                                                                                                                                                     | `UI/ui/BrowserInstallSection.kt:59-150`                                           | Network calls included                                                                              |
| 29 | Startup card: async `isEnabled()`, busy flag, error text                                                                                                                                                                                | `UI/ui/StartupUi.kt:55-100`                                                       |                                                                                                     |
| 30 | Proxy card: draft fields, `invalid`, `changed`, Save; site login form                                                                                                                                                                   | `UI/ui/NetworkSettingsUi.kt:57-225`                                               |                                                                                                     |
| 31 | Folder browser: current path, sub-folders, hidden toggle, new folder                                                                                                                                                                    | `UI/ui/DirectoryPickerDialog.kt:52-445`                                           | Could be replaced by the native folder dialog (owner's decision)                                    |
| 32 | Settings sections list and the one-line summary of each                                                                                                                                                                                 | `UI/ui/FdmSettingsPanel.kt:51-196`                                                |                                                                                                     |
| 33 | Tray: tooltip (first 6 running tasks), menu Show / When done / Close; close-to-tray; single instance; `fetchjet://` and file arguments; the local HTTP bridge                                                                           | `main.kt`                                                                         | Host work, see 6.6                                                                                  |

### 5.2 Formatting done in the UI (must move with the logic)

All are pure Kotlin without Compose types and can be copied as they are:

- `UI/utils/Formatters.kt`: `ltr`, `formatBytes`, `formatSizeDisplay`, `formatSpeed(bytesPerSec,
  unit)`, `decodeUnicodeText`, `formatSecondsToHhMm`, `parseHhMmToSeconds`,
  `formatSecondsToDisplayFriendly`.
- `UI/ui/TaskRows.kt`: `taskKindTag` (`:62-72`), `etaText` (`:84-88`), `taskStateLine`
  (`:91-132`), `sizeText` (`:134-142`), `rowActionFor` (`:147-155`), `nextPriority`
  (`:402-406`), the colour choices `kindColor` / `barColor` (as indices).
- `UI/TaskDetails.kt`: chips (`:133-175`), `bytesText` / `speedText` / status (`:306-434`),
  `formatEta` (`:734-742`), `formatAddedTime` (`:910-937`), post-process line (`:750-793`),
  checksum verdict (`:565-590`), button set by state (`:601-683`).
- `UI/ui/PiecesBar.kt:57-215`: counts, segment geometry, the active / waiting list.
- `UI/ui/AddDownloadLinkList.kt`: `linkTitle`, `linkHost`, `percentDecode`, `qualityText`
  (`:287-339`), row tag (`:165-171`).
- `UI/ui/SpeedPresetUi.kt`: `speedPresetToBytes`, `speedPresetValueText`,
  `speedPresetShortLabel`; `UI/ui/SettingsPages.kt`: `folderName`, `limitText`,
  `minutesToHm12`, `parseHm12`, `themeModeLabel`; `UI/ui/NetworkSettingsUi.kt:36-46`
  `networkSummary`; `UI/ui/DeviceSendDialog.kt:460-469` `deviceSyncReason`;
  `UI/ui/WhenDoneUi.kt:34-39` `whenDoneLabel`; `UI/ui/UpdateUi.kt:296-335` `sizeText`,
  `installHint`, `plainNotes`.
- `UI/HomeNavigation.kt:308-332`: the speed-control label (`TURTLE_BYTES = 512L * 1024L`).

---

## 6. Recommended bridge DTOs

### 6.1 Rules that shape the DTOs

1. **Slint has no 64-bit integer.** `.slint` `int` is 32-bit and `float` is 32-bit. Byte counts,
   speeds and epoch milliseconds (`Long`) must never cross into Slint as numbers. Kotlin sends
   them **formatted as text** (section 5.2) plus small numbers the UI needs (`progress: float`,
   state enums as `int`). Values that must come back are identified by index / id, not by value.
2. **Kotlin owns every text.** 625 plain strings and 138 parameterised ones exist only in the
   Kotlin providers. Either Kotlin sends finished strings inside the DTOs (recommended for
   everything dynamic) plus one "static strings" table for labels, re-sent when
   `AppLanguage.current` changes, or the strings are exported to Slint translations at build
   time (open question 3). The layout direction (`AppLanguage.isRtl`) travels with the table.
3. **Sections with independent versions**, so a 5 Hz task tick does not rebuild the settings
   pages: `chrome`, `rows`, `details`, `addDialog`, `dialogs`, `settings`, `devices`, `update`,
   `strings`. Kotlin raises a "section N changed" notification; the host pulls the section on
   the Slint thread.
4. **Never serialize** `DownloadTask.requestHeaders`, `SiteLogin.password`, the bridge token,
   stream URLs of qualities. The details row "Page" needs only the `Referer` value.
5. Lists can be long (link list 500, playlist hundreds, torrent files thousands, tasks
   unbounded): rows are flat structs; the selected task's chunks and torrent files are sent
   only in the `details` section.
6. Encoding: the prototype (`desktopNative/core/src/mingwX64Main/kotlin/FjCore.kt`) already
   returns a C string from `fj_snapshot()` and frees it with `fj_free()`.
   `kotlinx.serialization` JSON is already a dependency of `sharedLogic`, so `@Serializable`
   DTOs encoded as JSON per section is the least-effort path; a compact line format is the
   alternative if JSON parsing cost in C++ matters.

### 6.2 Snapshot structures

Types: `s` string, `i` int32, `f` float32, `b` bool, `[T]` list.

**Chrome / home** (`chrome`)

| Field                                                    | Type                                                   | Source                                                                                      |
|----------------------------------------------------------|--------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `tab`                                                    | i                                                      | 0 list, 1 settings (`selectedTab == SETTINGS`)                                              |
| `title`                                                  | s                                                      | filter label or "Settings"                                                                  |
| `downSpeed`, `upSpeed`                                   | s                                                      | `"↓ " + formatSpeed(total, unit)`                                                           |
| `limitLabel`                                             | s                                                      | speed-control chip label (`HomeNavigation.kt:325-331`)                                      |
| `limitPreset`                                            | i                                                      | 0 unlimited, 1 low, 2 medium, 3 fast, 4 turtle (512 KB/s), 5 custom                         |
| `limitCapped`, `turtleActive`, `scheduleActive`          | b                                                      | `limitBytes > 0 && !turtle`, `limit == 512 KiB`, `SpeedSchedule.activeCap != null`          |
| `presetMenu`                                             | `[s]` x4                                               | "Low · 256 KB/s", "Medium · ...", "Fast · ...", "High · Unlimited"                          |
| `darkResolved`                                           | b                                                      | resolved theme                                                                              |
| `navState`                                               | `[NavEntry]`                                           | label, count, selected for `ALL`, `DOWNLOADING`, `WAITING`, `PAUSED`, `COMPLETED`, `FAILED` |
| `navType`                                                | `[NavEntry]`                                           | `MEDIA` (if `AppFeatures.mediaExtractor`), `FILES`, `TORRENTS` (if `AppFeatures.torrent`)   |
| `settingsSelected`                                       | b                                                      |                                                                                             |
| `search`                                                 | s                                                      | current query                                                                               |
| `sortIndex`, `sortLabels`                                | i, `[s]`                                               | `TaskSortOption` ordinal and the 5 labels                                                   |
| `anyPausable`, `anyResumable`                            | b                                                      | `TaskList.kt:518-524` over the unfinished shown tasks                                       |
| `statusSummary`, `statusQueue`, `statusLimit`, `version` | s                                                      | `QueueStatusBar` (`TaskList.kt:424-471`)                                                    |
| `toolsBanner`                                            | `{visible: b, text: s, fraction: f, indeterminate: b}` | `isDownloadingYtDlp && toolDownloadName != null`                                            |
| `whenDoneBanner`                                         | `{visible: b, text: s}`                                | `WhenDone.action != NOTHING`                                                                |
| `selectionCount`, `allChecked`                           | i, b                                                   | selection bar                                                                               |
| `emptyTitle`, `emptyHint`                                | s                                                      | empty list texts                                                                            |
| `rtl`                                                    | b                                                      | `AppLanguage.isRtl`                                                                         |

**Task rows** (`rows`, one flat list with group headers folded in, as the prototype does)

| Field                                                                  | Type          | Source                                                              |
|------------------------------------------------------------------------|---------------|---------------------------------------------------------------------|
| `id`                                                                   | s             | `task.id` (the UI echoes it back in events)                         |
| `name`                                                                 | s             | `decodeUnicodeText(fileName or last URL segment).trim()`            |
| `tag`                                                                  | s             | `taskKindTag` (max 4 characters)                                    |
| `tagColor`                                                             | i             | index into the 7 kind colours (`TaskRows.kt:74-82`)                 |
| `stateLine`                                                            | s             | `taskStateLine(task, unit)`                                         |
| `stateIsError`                                                         | b             | `status == ERROR`                                                   |
| `size`                                                                 | s             | `sizeText(task)`                                                    |
| `progress`                                                             | f             | 0..1                                                                |
| `percent`                                                              | s             | `"100%"` when completed                                             |
| `barColor`                                                             | i             | 0 primary, 1 completed, 2 paused / waiting, 3 error                 |
| `speed`                                                                | s             | or `"—"`                                                            |
| `left`                                                                 | s             | `etaText` or `"—"`                                                  |
| `action`                                                               | i             | `RowAction` ordinal: 0 PAUSE, 1 RESUME, 2 START_NOW, 3 OPEN, 4 NONE |
| `actionLabel`                                                          | s             |                                                                     |
| `priority`                                                             | i             | -1, 0, 1; chip hidden when completed                                |
| `priorityLabel`                                                        | s             |                                                                     |
| `completed`                                                            | b             |                                                                     |
| `checked`, `selected`                                                  | b             | selection set, details selection                                    |
| `header`, `headerIcon`, `headerCount`, `headerKind`, `headerCollapsed` | s, s, i, i, b | first row of each state group (`taskStateGroups`)                   |

**Task details** (`details`, only for the selected task)

| Field                                                                       | Type                                                                                                                     | Source                                                                                                                                                      |
|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `visible`, `id`                                                             | b, s                                                                                                                     |                                                                                                                                                             |
| `name`, `fullPath`                                                          | s                                                                                                                        | `TaskDetails.kt:82-92`                                                                                                                                      |
| `chips`                                                                     | `[{text: s, color: i}]`                                                                                                  | engine, resumable / no resume, clip range                                                                                                                   |
| `percent`, `statusWord`                                                     | s                                                                                                                        | `:299-311`                                                                                                                                                  |
| `stepLine`                                                                  | s                                                                                                                        | `"⚡ " + localizeStep(currentStepText)` when running                                                                                                        |
| `progress`                                                                  | f                                                                                                                        |                                                                                                                                                             |
| `post`                                                                      | `{visible: b, icon: s, label: s, right: s, progress: f, indeterminate: b}`                                               | `PostProcessLine`; the elapsed clock ticks every second                                                                                                     |
| `downloaded`, `speedLabel`, `speedValue`, `speedColor`, `timeLeft`, `added` | s, s, s, i, s, s                                                                                                         | `:365-478`                                                                                                                                                  |
| `pieces`                                                                    | `{visible: b, title: s, pills: [{text, kind}], segments: [{x0: f, x1: f, fill: f, state: i}], summary: s, cards: [...]}` | `PiecesBar`; visible when `chunks.size > 1 && status != COMPLETED`; positions are fractions of `total = max(endByte + 1)` computed in Kotlin (64-bit maths) |
| `error`                                                                     | s                                                                                                                        | `"⚠️ " + localizeStep(errorMessage)`                                                                                                                        |
| `links`                                                                     | `[{label: s, url: s}]`                                                                                                   | Source = `url`; Page = `requestHeaders["Referer"] ?: originalUrl`                                                                                           |
| `checksum`                                                                  | `{visible: b, text: s, kind: i}`                                                                                         |                                                                                                                                                             |
| `torrent`                                                                   | `{visible: b, title: s, files: [TorrentFileRow]}`                                                                        |                                                                                                                                                             |
| `buttons`                                                                   | `{primary: i, primaryLabel: s, secondary: i, secondaryLabel: s, canShare: b, removeLabel: s}`                            | primary / secondary action codes: open file, open folder, pause, start now, resume, retry (`:601-683`)                                                      |
| `confirmRestartPause`                                                       | `{visible: b, isClip: b}`                                                                                                |                                                                                                                                                             |

`TorrentFileRow` (shared by details, Add dialog and link list): `index: i`, `path: s`,
`sizeText: s`, `selected: b`, `completed: b`, `priority: i` (-1, 0, 1), `priorityLabel: s`.

**Add dialog** (`addDialog`)

| Field                                       | Type                                                                                                                                                                                                                  | Source                                                                                                                                                                                  |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `visible`                                   | b                                                                                                                                                                                                                     | `isAddDialogVisible`                                                                                                                                                                    |
| `url`                                       | s                                                                                                                                                                                                                     | `inputUrl` (the Slint text field must not be overwritten while the user types unless the value differs, as `AddDownloadModalDialog.kt:136-143` does)                                    |
| `canImportTxt`, `canOpenTorrent`            | b                                                                                                                                                                                                                     | pickers' `isSupported` (+ `AppFeatures.torrent`)                                                                                                                                        |
| `mode`                                      | i                                                                                                                                                                                                                     | 0 single link, 1 link list, 2 playlist                                                                                                                                                  |
| `engine`                                    | i                                                                                                                                                                                                                     | `EngineType` ordinal: 0 HTTP_DIRECT, 1 BITTORRENT, 2 MEDIA_STREAM                                                                                                                       |
| `engineChoices`                             | `[i]`                                                                                                                                                                                                                 | hidden engines removed per `AppFeatures`                                                                                                                                                |
| `torrent`                                   | `{state: i, name: s, error: s, files: [TorrentFileRow], selectedCount: i}`                                                                                                                                            | state: 0 idle, 1 inspecting, 2 error, 3 ready                                                                                                                                           |
| `media`                                     | `{state: i, bannerTitle: s, bannerHint: s, error: s, engineStatus: s, preparing: b}`                                                                                                                                  | state: 0 hidden, 1 course reading, 2 inspecting, 3 error, 4 engine missing, 5 playlist, 6 not inspected, 7 qualities (order of `MediaInspectionSection`, `AddDownloadMedia.kt:453-594`) |
| `qualities`                                 | `[{index: i, label: s, line: s, selected: b}]`                                                                                                                                                                        | `MediaQualityEntry`; `line` = format + size + duration                                                                                                                                  |
| `qualitiesTitle`, `qualitiesCount`          | s                                                                                                                                                                                                                     |                                                                                                                                                                                         |
| `subtitles`                                 | `[{code: s, name: s, auto: b, selected: b}]`, `embedSubtitles: b`                                                                                                                                                     |                                                                                                                                                                                         |
| `clip`                                      | `{available: b, enabled: b, start: s, end: s, startFriendly: s, endFriendly: s, fullRange: s, startError: b, endError: b, error: s, chips: [{label: s, start: s, end: s}], fastMode: b, isAudio: b, fullSizeText: s}` | available when `mediaDurationSeconds > 0`                                                                                                                                               |
| `playlist`                                  | `{title: s, summary: s, folder: s, fromBrowser: b, qualityIndex: i, qualityLabels: [s], canUseSingle: b, allSelected: b, entries: [{index: i, title: s, duration: s, selected: b}]}`                                  |                                                                                                                                                                                         |
| `schedule`                                  | `{mode: i, showFields: b, hour: s, minute: s, pm: b, dayOffset: i, error: s, startsAt: s}`                                                                                                                            | the section's local state moves into the bridge                                                                                                                                         |
| `connections`                               | `{visible: b, selected: i, autoLabel: s}`                                                                                                                                                                             | choices 0, 1, 4, 8, 16, 32                                                                                                                                                              |
| `audioConvert`                              | `{visible: b, forAll: b, selected: s, nativeLabel: s, formats: [s]}`                                                                                                                                                  |                                                                                                                                                                                         |
| `fileName`                                  | `{visible: b, suggested: s, override: s, busy: b}`                                                                                                                                                                    |                                                                                                                                                                                         |
| `checksum`                                  | `{visible: b, value: s}`                                                                                                                                                                                              |                                                                                                                                                                                         |
| `destination`                               | `{path: s, quick: [{icon: s, label: s}]}`                                                                                                                                                                             |                                                                                                                                                                                         |
| `leech`                                     | `{visible: b, on: b}`                                                                                                                                                                                                 | torrents only                                                                                                                                                                           |
| `error`                                     | s                                                                                                                                                                                                                     | `errorMessage ?: inspectError ?: inspectMediaError`                                                                                                                                     |
| `showRetry`                                 | b                                                                                                                                                                                                                     | `hasActiveError && !isInspectionComplete`                                                                                                                                               |
| `primaryLabel`, `primaryEnabled`, `loading` | s, b, b                                                                                                                                                                                                               | `AddDownloadSections.kt:500-520`                                                                                                                                                        |

**Link list** (inside `addDialog` when `mode == 1`)

`folder: s`, `allSelected: b`, `summary: s`, `rows: [LinkRow]` with `LinkRow` = `index: i`,
`title: s` (`mediaTitle` or `linkTitle(url)`), `host: s`, `tag: s`, `tagColor: i`,
`selected: b`, `state: i` (0 ready, 1 inspecting, 2 failed / retry, 3 error), `error: s`,
`qualityIndex: i`, `qualityLabels: [s]`, `qualityIndexes: [i]`, `torrentName: s`,
`files: [TorrentFileRow]`.

**Settings** (`settings`)

| Group       | Fields                                                                                                                                                                                                                                                                                                                                            |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sections    | `[{id: i, glyph: s, title: s, blurb: s, summary: s, summaryColor: i}]` for `GENERAL`, `DOWNLOADS`, `BROWSER`, `DEVICES`, `NETWORK`, `MEDIA`, `TORRENT`, `UPDATES` (filtered as `FdmSettingsPanel.kt:153-160`)                                                                                                                                     |
| General     | `languageChoice: s`, `languages: [{code: s, name: s}]` (system + 15), `watchClipboard: b`, `clipboardVideoPages: b`, `themeMode: i` (0 system, 1 light, 2 dark), `speedUnit: i` (MB/s, KB/s, Mbps, Kbps), `startup: {supported: b, managedBySystem: b, enabled: b, busy: b, startHidden: b, error: s}`, `whenDone: {supported: b, action: i}`     |
| Downloads   | `destination: s`, `isDefaultDestination: b`, `canOpenFolder: b`, `defaultConnections: i`, `presets: [{name: s, valueText: s, unit: s}]` (Fast, Medium, Low), `schedule: {enabled: b, active: b, windows: [{start: s, startPm: b, end: s, endPm: b, capText: s, capUnit: s}]}`, `sortIntoFolders: b`, `maxAtOnce: i` (choices 1, 2, 3, 4, 5, 8, 0) |
| Network     | `proxy: {kind: i, host: s, port: s, user: s, hasPassword: b}`, `logins: [{site: s, user: s}]` (never the passwords; see open question 5)                                                                                                                                                                                                          |
| Media tools | `ytDlpReady: b`, `ffmpegReady: b`, `jsRuntimeName: s`, `jsRuntimeReady: b`, `toolsFolder: s`, `downloading: b`, `buttonLabel: s`, `progress: f`, `indeterminate: b`, `status: s` (`localizeStep(ytDlpStatus)`)                                                                                                                                    |
| Torrent     | `uploadLimitText: s`, `uploadLimitUnit: s`, `leech: b`, `engineReady: b`, `engineStatus: s`                                                                                                                                                                                                                                                       |
| Browser     | `browsers: [{id: s, name: s, subtitle: s, buttonLabel: s, showButton: b, busy: b}]`, `allowedOrigins: [s]`, `blockedOrigins: [s]`, `dialog: {kind: i, text: s, folder: s}` (missing browser, load-unpacked steps, install from file, failure)                                                                                                     |
| Devices     | `visible: b`, `paired: [{id: s, host: s, platform: s}]`, `blocked: [...]`                                                                                                                                                                                                                                                                         |
| Updates     | see below                                                                                                                                                                                                                                                                                                                                         |

**Dialogs** (`dialogs`, each `visible` + texts)

- `remove`: `count: i`, `title: s`, `message: s`.
- `missingFile`: `taskId: s`, `fileName: s`.
- `clipboardOffer`: `url: s`, `name: s`.
- `browserFallback`: `url: s`.
- `duplicate`: `name: s`, `statusText: s`, `statusKind: i`, `percent: s`, `progress: f`,
  `showBar: b`.
- `fileConflict`: `fileName: s`, `folder: s`, `suggestedNewName: s`.
- `bridgePairing` (`BridgePairing.pending`): `origin: s`, `legacy: b`.
- `whenDone`: `secondsLeft: i`, `text: s`, `fraction: f`, `error: s`.
- `folderPicker` (if the Compose-style browser is kept): `path: s`, `parent: s`,
  `quick: [{label: s, path: s}]`, `folders: [s]`, `showHidden: b`, `creating: b`, `error: s`.

**Device sync** (`devices`)

- `sheet`: `visible: b`, `taskId: s`, `fileName: s`, `canTransfer: b`,
  `devices: [{id: s, host: s, platform: s, emoji: s, paired: b, groupHeader: s}]`,
  `selectedId: s`, `mode: i`, `deleteAfter: b`, `sendState: i` (0 none, 1 waiting, 2 sending,
  3 done, 4 failed), `sendFraction: f`, `sendText: s`, `busy: b`, `streaming: b`.
- `pairing` (`devicePairing`): `visible: b`, `kind: i` (0 connecting, 1 confirm, 2 failed),
  `peerName: s`, `code: s`, `text: s`.

**Updates** (`update`, from `UpdateManager.state` + `PlatformUpdater`)

`supported: b`, `currentVersion: s`, `availableVersion: s`, `checking: b`, `message: s`,
`messageIsError: b`, `autoCheck: b`, `autoInstall: b`, `canInstallInApp: b`, `fromStore: b`,
`dialog: {visible: b, versionLine: s, notes: s, downloading: b, progress: f, progressText: s,
ready: b, hint: s, installError: s, primaryKind: i, primaryLabel: s, primaryUrl: s}`
(primaryKind: 0 restart and update, 1 download and install, 2 open the Store, 3 open a link).

**Bridge pairing** is the `bridgePairing` dialog plus the two origin lists in Settings > Browser.

### 6.3 Host effects (Kotlin -> host, one-shot, queued)

| Effect         | Payload               | Origin                                    |
|----------------|-----------------------|-------------------------------------------|
| `toast`        | `text: s`             | `DashboardUiEffect.ShowToast`             |
| `showAndFocus` | -                     | `ExternalDownloadBridge.showAndFocus()`   |
| `scrollToTop`  | -                     | `scrollToTopSignal` changed               |
| `openUrl`      | `url: s`              | browser fallback, store / release links   |
| `notify`       | `title: s`, `text: s` | `SystemNotice.show`                       |
| `exitApp`      | -                     | `WhenDone.onExitApp`                      |
| `copyText`     | `text: s`             | Copy buttons, load-unpacked folder        |
| `trayTooltip`  | `text: s`             | running tasks summary (`main.kt:807-822`) |

### 6.4 Events the UI must be able to send

Plain-value events map 1:1 to `DashboardUiEvent` with `string` / `int` / `bool` arguments. The
ones below need an encoding decision.

| Event(s)                                                                                                                                                                                                                           | Problem                                                    | Encoding                                                                                                                                                                                     |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OnEngineSelected(EngineType)`, `OnTabSelected(DashboardTab)`, `OnSortChanged(TaskSortOption)`, `OnPlaylistQualityChanged(PlaylistQuality)`, `OnSpeedPresetChanged(SpeedPreset, ...)`, `OnSendToDeviceClicked(..., SendMode, ...)` | enum parameters                                            | the enum ordinal as `int` (orders are listed in section 1.2), resolved with `entries[i]` in Kotlin                                                                                           |
| `OnGlobalDownloadSpeedLimitChanged(Long)`, `OnGlobalUploadSpeedLimitChanged(Long)`, `OnDownloadSpeedLimitChanged(Long)`, `OnUploadSpeedLimitChanged(Long)`                                                                         | 64-bit value                                               | header: preset index (0 unlimited, 1 low, 2 medium, 3 fast, 4 turtle); settings: `(valueText: string, unit: string)` converted with `speedPresetToBytes`                                     |
| `OnSpeedPresetChanged(preset, bytes: Long, unit)`                                                                                                                                                                                  | 64-bit value                                               | `(preset: int, valueText: string, unit: string)`                                                                                                                                             |
| `OnScheduleChanged(epochMs: Long)`                                                                                                                                                                                                 | 64-bit value computed from the clock                       | intents: `schedule-now`, `schedule-in(minutes: int)`, `schedule-at(dayOffset: int, hour12: int, minute: int, pm: bool)`; Kotlin computes and reports the "time passed" error in the snapshot |
| `OnRemoveSelectedClicked(List<String>)`, `OnExportLinksClicked(List<String>)`                                                                                                                                                      | list of ids                                                | keep the selection set in the bridge: `toggle-check(id)`, `check-range(id)`, `check-all(bool)`, `clear-checks`, then `delete-checked` / `export-checked`                                     |
| `OnAudioConvertFormatSelected(String?)`                                                                                                                                                                                            | nullable                                                   | `""` = null                                                                                                                                                                                  |
| `OnClipboardLink(url)`                                                                                                                                                                                                             | sent by the watcher, not by a control                      | internal to the bridge                                                                                                                                                                       |
| `OnSaveAsCopyClicked(newFileName)`                                                                                                                                                                                                 | value comes from the snapshot                              | no argument: Kotlin uses `fileConflictAlert.suggestedNewName`                                                                                                                                |
| `OnConfirmRemoveClicked(deleteFile)`                                                                                                                                                                                               | plain, but the Missing-file flow sends two events in a row | one bridge event `remove-missing(id)`                                                                                                                                                        |
| task row quick action                                                                                                                                                                                                              | logic in the UI                                            | `row-action(id)`: Kotlin decides pause / resume / open / open details (5.1 item 11)                                                                                                          |
| `TextFilePicker.pick { }`, `TorrentFilePicker.pick { }`                                                                                                                                                                            | lambdas                                                    | `import-links-file`, `open-torrent-file`: Kotlin (or the host) shows the dialog and feeds `OnUrlChanged`                                                                                     |
| Paste button                                                                                                                                                                                                                       | suspend `getClipboardText()`                               | `paste-url`                                                                                                                                                                                  |
| `NetworkSettings.setProxy(ProxySettings)`, `saveLogin(SiteLogin)`                                                                                                                                                                  | objects                                                    | `save-proxy(kind: int, host, port: string, user, password)`, `add-login(site, user, password)`, `remove-login(site)`                                                                         |
| `SpeedSchedule.addWindow / updateWindow(SpeedWindow)`                                                                                                                                                                              | object                                                     | `schedule-add`, `schedule-set-time(index, isEnd: bool, text, pm)`, `schedule-set-cap(index, valueText, unit)`, `schedule-remove(index)`, `schedule-enable(bool)`                             |
| `WhenDone.choose(WhenDoneAction)`                                                                                                                                                                                                  | enum                                                       | ordinal; plus `when-done-cancel`, `when-done-run-now`, `when-done-dismiss-error`                                                                                                             |
| `UpdateManager.*`                                                                                                                                                                                                                  | no parameters except two booleans                          | `update-check`, `update-show`, `update-dismiss`, `update-skip`, `update-install`, `update-auto-check(bool)`, `update-auto-install(bool)`                                                     |
| `BridgePairing.answer(Boolean)`, `block()`, `revokeAllowed(origin)`, `unblock(origin)`                                                                                                                                             | plain                                                      | as they are                                                                                                                                                                                  |
| `StartupLauncher.setEnabled(Boolean)`                                                                                                                                                                                              | suspend, `Result`                                          | `startup-enable(bool)`; result in the snapshot (`busy`, `error`)                                                                                                                             |
| `BrowserIntegration.addExtension(id)` / `installXpi(...)`                                                                                                                                                                          | sealed result, bytes                                       | `browser-add(id)`; Kotlin runs the decision of 5.1 item 28 and publishes `dialog`                                                                                                            |
| `QueueManagerRepository.setMaxConcurrentDownloads(Int)`, `BehaviorSettings.setVisibleToDevices(Boolean)`                                                                                                                           | plain                                                      | as they are                                                                                                                                                                                  |
| `PathResolver.open*`                                                                                                                                                                                                               | plain                                                      | `open-folder(path)`, `open-tools-folder`, `details-open-file`, `details-open-folder`                                                                                                         |
| UI-only state moved to the bridge                                                                                                                                                                                                  | -                                                          | `set-search(text)`, `set-filter(int)`, `select-task(id)`, `toggle-group(key)`, `window-shown(bool)` (stop the task ticks while hidden)                                                       |

Complete list of 1:1 events with plain arguments (name: argument types):

`OnUrlChanged: s`, `OnDestinationPathChanged: s`, `OnFileNameChanged: s`, `OnChecksumChanged: s`,
`OnDisableTorrentUploadToggled: b`, `OnToggleAddDialog: b`, `OnAddDownloadClicked`,
`OnInspectTorrentClicked`, `OnInspectMediaClicked`, `OnMediaQualitySelectionToggled: i, b`,
`OnSelectAllMediaQualitiesToggled: b`, `OnSubtitleToggled: s, b`, `OnToggleEmbedSubtitles: b`,
`OnClipStartTimeChanged: s`, `OnClipEndTimeChanged: s`, `OnToggleClipRange: b`,
`OnToggleClipFastMode: b`, `OnPlaylistEntryToggled: i, b`, `OnPlaylistSelectAll: b`,
`OnPlaylistFolderChanged: s`, `OnPlaylistUseSingleVideo`, `OnLinkListEntryToggled: i, b`,
`OnLinkListSelectAll: b`, `OnLinkListFolderChanged: s`, `OnLinkListTorrentFileToggled: i, i, b`,
`OnLinkListTorrentFilePriority: i, i, i`, `OnLinkListTorrentSelectAll: i, b`,
`OnLinkListQualitySelected: i, i`, `OnLinkListRetryInspect: i`, `OnConnectionsChanged: i`,
`OnTorrentFileSelectionToggled: i, b`, `OnTorrentFilePriorityChanged: i, i`,
`OnSelectAllTorrentFilesToggled: b`, `OnClipboardOfferAccepted`, `OnClipboardOfferDismissed`,
`OnPauseClicked: s`, `OnResumeClicked: s`, `OnCancelClicked: s`, `OnRemoveClicked: s`,
`OnConfirmRemoveClicked: b`, `OnDismissRemoveDialog`, `OnConfirmBrowserFallback`,
`OnDismissBrowserFallback`, `OnResumeExistingDuplicateClicked`, `OnRestartDuplicateClicked`,
`OnDismissDuplicateDialog`, `OnConfirmOverwriteClicked`, `OnDismissFileConflictDialog`,
`OnPauseAllClicked`, `OnResumeAllClicked`, `OnTaskTorrentFileSelectionToggled: s, i, b`,
`OnTaskTorrentFilePriorityChanged: s, i, i`, `OnTaskPriorityChanged: s, i`,
`OnMonitorClipboardChanged: b`, `OnClipboardVideoPagesChanged: b`, `OnSortIntoFoldersChanged: b`,
`OnSpeedDisplayUnitChanged: s`, `OnLanguageChanged: s`, `OnThemeModeChanged: s`,
`OnDefaultConnectionsChanged: i`, `OnDownloadYtDlpClicked`, `OnShareTaskClicked: s`,
`OnDismissShareSheet`, `OnConfirmPairingClicked`, `OnCancelPairingClicked`,
`OnBlockPairingClicked`, `OnUnpairDeviceClicked: s`, `OnUnblockDeviceClicked: s`,
`OnManualConnectClicked: s`.

A generic entry point `fj_event(name: const char*, args: const char*)` with a tab- or
JSON-encoded argument list covers all of them with one exported function; typed exports per
event are the alternative.

### 6.5 Pure view state that can stay in `.slint`

Open menus, the expanded state of the torrent-files accordion, the settings page being shown,
text being typed before it is committed, the "also delete files" checkbox of the remove dialog,
manual-IP field visibility, hover and focus.

### 6.6 What the native host must take over from `main.kt`

| Responsibility                                                                                                                                                                                                                                                                                    | `main.kt` lines  |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| Launch arguments: `fetchjet://` / `kortex://` URLs, http(s), magnet, `.torrent` path (`parseUrlFromArg`); `--autostart=true` filter                                                                                                                                                               | 51-94, 726-743   |
| Single instance: `GET /ping` on `127.0.0.1:28450` answers `PONG`; forward a link with `GET /download?url=` + token; `GET /show`                                                                                                                                                                   | 97-135, 745-747  |
| Local HTTP bridge on port 28450 (loopback only): `/ping`, `/config`, `/pair`, `/add`, `/links`, `/show`, `/download`, `/inspect`, with the Host / Origin checks, the `X-FetchJet` header gate, token check, CORS preflight, 4 MB body limit, 5 s `/add` dedupe, 30 min inspect cache (40 entries) | 138-688          |
| Log file `logs/app.log` (2 MB reset, redaction), uncaught exceptions                                                                                                                                                                                                                              | 278-303          |
| `LocalizationManager.start()`, `AppLog.verbose` from `FETCHJET_VERBOSE_LOGS`                                                                                                                                                                                                                      | 697-704          |
| `BrowserIntegration.setUpOnFirstRun()` on a background thread                                                                                                                                                                                                                                     | 719-723          |
| Start hidden in the tray at login (`StartupPreferences.startHidden`)                                                                                                                                                                                                                              | 750-756          |
| Window 1100 x 750, title `"FetchJet - " + desktopWindowTagline`, close = hide to tray                                                                                                                                                                                                             | 769, 862-870     |
| Tray: tooltip, left click toggles the window, menu Show / When downloads finish (4 check items) / Close                                                                                                                                                                                           | 803-857          |
| `onShowAndFocus`: show, un-minimize, bring to front (Windows foreground-lock workaround)                                                                                                                                                                                                          | 772-776, 871-897 |
| `openInBrowserFallback`: open the default browser                                                                                                                                                                                                                                                 | 782-797          |
| `WhenDone.onExitApp`                                                                                                                                                                                                                                                                              | 799-800          |
| A link passed at launch is dispatched 600 ms after the window exists                                                                                                                                                                                                                              | 899-906          |
| Tray notifications from `SystemNotice`                                                                                                                                                                                                                                                            | 762-768          |

---

## 7. Risks

1. **Hidden UI logic.** Section 5 lists 33 behaviours and about 40 formatting functions that
   are not in the view model. Missing one changes behaviour silently (for example no
   auto-inspect means a pasted YouTube link never shows qualities; no type-folder rewrite means
   "Sort into folders" stops showing the real target).
2. **`getPlatform().isJvm` means "desktop"** in the UI. A mingw actual that answers `false`
   would hide the Media tools page and the torrent-engine row and show the phone variants in
   any shared decision copied from Compose. The bridge must decide "desktop" itself.
3. **64-bit values and Slint.** Any `Long` pushed into a Slint `int` overflows above 2 GB / on
   every epoch value. Section 6.1 rule 1.
4. **Update rate vs the 50 MB goal.** Rebuilding and serializing every row 5 times per task per
   second allocates continuously. Throttle, diff by row version, send chunks only for the
   selected task, stop while hidden.
5. **Secrets in the state.** `DownloadTask.requestHeaders` (cookies), quality `directUrl`s (signed
   URLs), `ProxySettings.password`, `SiteLogin.password`, the bridge token. A generic
   "serialize the state" bridge would leak them into the UI process memory, logs and crash
   dumps.
6. **Existing data races** (section 3.3) are unchanged by the port but become easier to hit if
   UI events are delivered from more than one thread. Keep a single ordered event path.
7. **Effect loss.** `uiEffect` has no replay; a bridge that subscribes late or blocks loses or
   delays toasts, and a blocked collector eventually suspends the emitting coroutines.
8. **Language switch.** Compose re-created the whole tree; the bridge must re-send every
   section and the static strings, and time-dependent texts ("Starts at ...", "today 2:05 PM")
   must be recomputed on a timer, not only on state change.
9. **`ExternalDownloadBridge` replay.** Requests that arrive before the view model exists are
   queued and replayed when `onExternalRequest` is set, on the constructing thread, before the
   UI may be ready to show the Add dialog.
10. **Dead or half-wired API.** `OnThemeToggled`, `OnApplySettingsClicked`,
    `OnConfirmCancelClicked`, `OnDismissCancelDialog`, both per-download limit events, state
    `message`, `isDarkMode`, `speedPresetHigh*`: implementing UI for them would be wasted work.

## 8. Open questions

1. Which window widths must the native app support? Compose has three layouts: phone below
   680 dp (bottom tabs, floating add button), rail from 680 to 1100 dp, sidebar from 1100 dp,
   and a fixed details side panel only from 1280 dp (a bottom sheet below that). A desktop-only
   port could set a minimum window width and drop the phone layout.
2. Folder choice: keep the in-app folder browser (`DirectoryPickerDialog`, built on
   `PathResolver.listSubdirectories`) to look the same, or use the Windows folder dialog?
3. Strings: send them from Kotlin at run time (one table of 625 entries per language switch,
   parameterised ones formatted in Kotlin), or generate Slint translation files from the 15
   providers at build time? The second needs a generator and loses the single source of truth
   for the 138 parameterised strings.
4. Where do UI events run: directly on the Slint thread (same as Compose today, a few
   synchronous file writes on the UI thread) or on a single-threaded Kotlin dispatcher?
5. Proxy password: the Compose field shows the stored password (masked). Should the native UI
   receive it, or only "a password is saved" with an empty field that replaces it when typed?
6. Should the bridge live inside `sharedLogic` (`mingwX64Main`, can reach the `internal`
   members and the handler functions) or in its own module (needs a few members made public)?
7. File dialogs and clipboard on Windows: implemented in the Kotlin mingw actuals (thread
   affinity to solve) or in the C++ host with the result sent back as an event?
8. Is the tray tooltip still wanted in the native app (it needs the running-task summary even
   while the window is hidden and task snapshots are paused)?
