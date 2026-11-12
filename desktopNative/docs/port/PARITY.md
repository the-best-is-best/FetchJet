# Feature parity: JVM desktop app vs native Windows / Linux app

Updated 2026-11-10 (night, Linux round 3 + Windows re-verification): the rows below now match the
builds and tests of that round (`STATUS.md` "Linux port, third round"); the Linux column is the
section "Linux" at the end. The text from here to "Summary" is the first version's.

Written 2026-11-10 from reading the code and the port docs only: no build, no test, no app run.
Native status comes from `STATUS.md`, the four hand-over notes in `handover/` and the code as it is
in the working tree today (branch `desktop-native`, uncommitted changes included). Where the docs
and the code disagree, the code wins and the row says so.

Status words:

- `done and checked`: the native code exists and a test or a picture shows it working.
- `written, not run`: the native code exists, but nothing ran it inside the app (helper-level
  tests may exist; they are named in the evidence column).
- `placeholder`: a stub that does nothing (or the feature is hidden on purpose until its engine
  exists).
- `missing`: no native code for it.
- `n/a`: the JVM app does not offer it on Windows either (not counted).

Paths. JVM: `desktopApp/.../main.kt` (the JVM module is `desktopApp (removed)`; there is no
`desktopDefault`), `androidUi/...` = `androidUi/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`,
`sharedLogic/...` = `sharedLogic/src/<set>/kotlin/com/michelle/raouf/fetch_jet/`. Native:
`core/...` = `desktopNative/core/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/nativeapp/`,
`ui/...` = `desktopNative/ui/`, `host/main.cpp` = `desktopNative/host/main.cpp`, `win/...` =
`sharedLogic/src/mingwX64Main/.../platform/win/`. Tests: `corehost/test_*.py`, `host/test_shell.py`,
`winhelpers <mode>` = `desktopNative/tests/winhelpers` (`run.sh <mode>`). Pictures: names written by
`tests/corehost/shots.py` / `shots_settings.py` into `E:\tools\probes\ui\shots` (74 PNG there today).

Important for reading this file: the native core runs the **same** `DashboardViewModel`,
`QueueManagerRepository`, download engines, `BridgePairing`, `UpdateManager`, `WhenDone`,
`SpeedSchedule` etc. from `sharedLogic/commonMain` as the JVM app. Only the UI (Slint + presenters
in `core/presenter/`) and the platform `actual`s (`sharedLogic/src/mingwX64Main`) are new. Of the
581 strings the JVM UI uses, every one is also used by the native UI except `dirPickerSubfolders`
(native uses the Windows folder dialog) and `logSavedTo` (diagnostics export, off on both).

## Summary

Counts (rows below, `n/a` not counted):

| Status | Rows |
|---|---|
| done and checked | 81 |
| written, not run | 70 |
| placeholder | 4 |
| missing | 5 |
| **total** | **160** |

Most "done and checked" rows are UI and logic checked by the core tests without a window; most
"written, not run" rows are the parts that touch Windows itself (tray, registry, dialogs, power,
installers) or the internet.

Top 10 gaps a user would notice first (updated 2026-11-10, night):

1. **Send to device stays hidden.** The shared engine runs (`run.sh sync` 125/0, `syncjvm` 44/0,
   `test_devices` 41/0, Android emulator 17/0, Linux loopback pairing), but
   `DEVICE_SYNC_READY = false` hides the Devices page and the button until a real second device is
   tried.
2. **The tray, close-to-tray, start-hidden and notifications were never seen on Windows** (hidden
   desktops have no taskbar). Linux: tray written in round 4 (StatusNotifierItem over GDBus), tested only against a fake watcher in WSL.
3. **Media tools were never downloaded by the native app**, so real yt-dlp, the ffmpeg merge and
   HLS never ran natively (tests forbid internet and use a fake yt-dlp).
4. **The in-app update was never run** (only the dialog with a made-up release).
5. **No install was ever tried**: MSI, MSIX, `.deb` and the portable `.tar.gz` build and pass their
   table / tree checks; nothing was installed.
6. **Torrents never met the internet**: the engine is in the app on both OSes and passes the local
   seeder tests, but no real torrent / magnet ran.
7. **Clipboard watcher never ran** (tests refuse to read the clipboard).
8. **Start at login, "When downloads finish" actions and the browser integration actions never
   ran.**
9. **SOCKS5 and the yt-dlp speed limit inside the app**: the shared loopback proxy passes the
   helper checks and `test_proxy_limit` on both OSes, but no SOCKS5 proxy was set in Settings and
   no real yt-dlp used the speed-limit proxy.
10. **Dragging the details sheet** is missing; the native CI jobs are written but never started.

## Home screen

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Sidebar with state / type filters and counts | `androidUi/HomeNavigation.kt`, `App.kt` | done and checked | `test_home` (41): "six state filters", "sidebar counts"; pictures `home-dark`, `home-light` | `ui/pages/Sidebar.slint`, `core/presenter/HomePresenter.kt` |
| Rail on a medium-width window | `HomeNavigation.kt` | done and checked | picture `home-dark-medium` | `ui/pages/Rail.slint` |
| Phone layout (bottom nav, below 680 dp) | `HomeNavigation.kt`, `App.kt` | missing | none | Deliberate: native window minimum 680 x 480 (STATUS "Decisions") |
| Search | `HomeNavigation.kt` top bar | done and checked | `test_home`: "search ignores case", "no match text" | |
| Sort menu | `HomeNavigation.kt`, `DashboardFilters.kt` | done and checked | `test_home`: "sort menu marks name order" | |
| Speed control in the top bar (turtle, presets, unlimited) | `HomeNavigation.kt`, `ui/SpeedPresetRow.kt` | done and checked | `test_home`: "turtle mode", "unlimited again", status bar limit | UI state only; the limit on a running transfer was not measured natively (engine is common code) |
| Theme toggle / follow system theme | `App.kt` (`isSystemInDarkTheme`) | done and checked | `test_home`: "system dark theme is followed", "theme toggle leaves system mode" | Live change from Windows (`WM_SETTINGCHANGE` `ImmersiveColorSet` in `host/main.cpp`) not seen on a real desktop |
| Task list grouped (running / stopped / done), fold a group | `TaskList.kt` | done and checked | `test_home`: "three group headers", "folded group hides its rows" | |
| Task rows wide / narrow, tags, percent, speed, RTL names | `TaskList.kt`, `ui/TaskRows.kt` | done and checked | `test_home`: rows, tags, "Arabic name is flagged"; pictures | |
| Priority chip cycles | `TaskList.kt` | done and checked | `test_home`: "priority chip cycles to High" | |
| Multi-select: checkbox, Ctrl+click, Shift+click range, select all, Esc | `TaskList.kt` (`onKeyEvent`, `TaskRow`) | done and checked | `test_home`: "ctrl click", "shift click checks the range", "escape clears", "select all"; picture `selection-dark` | |
| Delete / Backspace key deletes the selection | `TaskList.kt` | written, not run | none | `ListKey` action exists; no check for the Delete key |
| Selection bar: remove selected (with "also delete files") | `TaskList.kt` `SelectionBar`, `ui/RemoveConfirmationDialog.kt` | done and checked | `test_download`: "remove asks first", "file deleted"; pictures `alert-remove-dark`, `alert-remove-dark-ar` | The test removes one task; the multi-id path is the same alert |
| Export links of the selection to a `.txt` | `DashboardTaskEvents.kt` `exportLinks` | written, not run | none | `ExportChecked` -> common `exportLinks` |
| Pause all / Resume all | `HomeNavigation.kt` | done and checked | `test_home`: "nothing is pausable any more" | |
| Tools download banner above the list | `App.kt` `ToolsDownloadBanner` | written, not run | none | `toolsBanner*` in `dto/HomeDto.kt`; tools download never ran |
| When-done banner above the list | `ui/WhenDoneUi.kt` `WhenDoneBanner` | written, not run | none | Test host refuses any choice but "Do nothing" |
| Status bar (version, limit, counts, total speed) | `App.kt` | done and checked | `test_home`: "status bar texts", "total speed is shown" | `ui/pages/StatusBar.slint` |
| Toast (snackbar) | `App.kt` `SnackbarHost` | done and checked | `test_download`: "a toast was published" | Drawn above page and sheet, under dialogs (STATUS "Decisions") |
| Empty list state | `TaskList.kt` | done and checked | `test_home`: "empty list state"; picture `home-empty-dark` | |
| 15 languages, RTL for ar / fa | `core/i18n`, `App.kt` | done and checked | `test_home`: "tr channel has every string"; pictures `home-light-ar`, `home-dark-zh` | Only Arabic and Chinese seen on screen |
| Colour emoji icons | Compose / Skia | done and checked | pictures `E:\tools\probes\linuxport\r3\after\*` (Windows) and `shots-linux\r3-*` (Linux) | Since round 3: bundled pictures `res/emoji` (Noto Color Emoji) on both OSes; the Windows DirectWrite atlas is only a fallback. Text is the bundled FetchJet Sans font |

## Task details

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Side panel (wide) / bottom sheet (narrow) | `TaskDetails.kt`, `App.kt` (Dialog on phones) | done and checked | pictures `details-side-dark`, `details-sheet-dark`, `details-side-light`, `details-side-dark-ar` | Side panel from 1280 px |
| Numbers: size, speed, time left, added, state | `TaskDetails.kt` | done and checked | same pictures | |
| Pieces map and connection cards | `ui/PiecesBar.kt`, `TaskDetails.kt` | done and checked | pictures with a running download (6 pieces) | |
| Source / Page links with Copy | `TaskDetails.kt` | written, not run | none | Links appear in `DetailsDto.links`; the Copy (clipboard write) was not checked |
| Open file / Open folder | `TaskDetails.kt` | written, not run | none | Skipped when headless; needs a real desktop |
| Pause / Resume / Start now | `TaskDetails.kt` | done and checked | `test_download`: pause / resume checks (ranges, sha256) | Through the same events as the list |
| "Restart on pause" warning for non-resumable downloads | `App.kt` dialog | written, not run | none | Alert shares the checked alert component |
| Checksum result line | `TaskDetails.kt` | written, not run | none | |
| Torrent file list with priorities in details | `TaskDetails.kt` | written, not run | none | Engine now in the app; the details file list itself was not checked |
| "Send to device" button | `TaskDetails.kt`, `ui/DeviceSendDialog.kt` | placeholder | none | Hidden (`DEVICE_SYNC_READY = false`) |
| Drag of the bottom-sheet handle | `TaskDetails.kt` | missing | none | STATUS "Not ported yet" |

## Add dialog: direct link

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Link field: type, paste, clear | `ui/AddDownloadSections.kt` | done and checked | `test_add` (29): "typed text is not echoed back"; pictures `add-direct-dark`, `add-direct-light`, `add-direct-light-ar` | |
| Pasted text with one link keeps only the link | `DashboardAddDialogEvents.kt` `OnUrlChanged` | done and checked | `test_add`: "only the link stays" | Common code |
| Engine choice (direct / media) | `AddDownloadSections.kt` | done and checked | `test_add`: "direct engine...", "media engine..." | Torrent engine choice hidden (see Torrent) |
| Schedule: now / in 30 min / in 1 h / at a time | `ui/PlaylistScheduleUi.kt` `ScheduleStartSection` | done and checked | `test_add`: "in 30 minutes", "time fields", "a time tomorrow is accepted"; pictures `add-direct-time-dark`, `add-direct-scheduled-dark` | |
| Connections per download | `PlaylistScheduleUi.kt` `ConnectionsSection` | done and checked | `test_add`: "the chosen 8 connections were used" | |
| File name with reset | `AddDownloadSections.kt` | done and checked | `test_add`: "typed name kept, reset offered", "reset brings the suggestion back" | |
| Checksum field | `AddDownloadSections.kt` | done and checked | `test_add`: "...checksum shown" | Field only; verification after the download was not checked |
| Destination + quick folders | `AddDownloadSections.kt` | done and checked | `test_add`: "quick folder replaces...", "saved in the chosen folder" | |
| Choose a folder | `ui/DirectoryPickerDialog.kt` (own browser) | written, not run | none | Native uses the Windows folder dialog (`host/main.cpp` `pick_path`); needs a click on a real desktop |
| Import links from a `.txt` | `TextFilePicker.jvm.kt` | written, not run | none | `TextFilePicker.mingw.kt`; its dialog title "Import links" / "Text files" is hard-coded English |
| Open a `.torrent` file from the dialog | `TorrentFilePicker.jvm.kt` | written, not run | `test_torrent`: "Open .torrent follows the host's file picker (none when headless)" | Needs a click on a real desktop |
| Duplicate download dialog | `ui/DuplicateDownloadDialog.kt` | done and checked | `test_media`: "the dialog has three answers", "dismiss closes it"; picture `choice-dialog-dark` | `ui/pages/ChoiceDialogs.slint` |
| File already exists dialog | `ui/FileOverwriteDialog.kt` | done and checked | STATUS (inside `test_media`, 31) | Same choice component |
| Start downloads the file | `DashboardAddDownload.kt` | done and checked | `test_add`: "content is right" (sha256); `test_download` (29) | |
| github / gitlab `blob` link turned into `raw` | `DashboardAddDownload.kt` `codeHostRawUrl` | written, not run | none | Common code, not exercised natively |

## Add dialog: media, playlist

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Media inspection: quality list, title, preselected quality | `ui/AddDownloadMedia.kt`, `MediaInspect.jvm.kt` | done and checked | `test_media`: "qualities are listed", "title of the video..."; picture `media-qualities-dark` | With a fake yt-dlp (`tests/fakes/fake_ytdlp.cpp`); real yt-dlp never ran natively |
| Subtitles list, embed switch | `AddDownloadMedia.kt` | done and checked | `test_media`: "subtitles listed, auto ones marked" | fake yt-dlp |
| Clip range with time picker and quick chips | `AddDownloadMedia.kt`, `ui/TimeRangePickerDialog.kt` | done and checked | `test_media`: 8 clip / picker checks; pictures `media-clip-dark*`, `media-picker-dark` | |
| Audio format choice | `ui/AudioConvertSection.kt` | done and checked | `test_media`: "audio quality offers the audio format choice" | |
| Playlist: select entries, folder, quality, "only this video" | `ui/PlaylistScheduleUi.kt` `PlaylistSelectionCard` | done and checked | `test_media`: "four videos, all selected", "title, folder and durations"; pictures `media-playlist-dark*` | |
| Inspection error with Retry | `AddDownloadMedia.kt` | done and checked | `test_media`: "error is shown with retry"; picture `media-error-dark` | |
| "Engine missing" in the dialog | `AddDownloadMedia.kt` | done and checked | STATUS (`test_media`) | The button that downloads the tools never ran |
| Media download (own engine after yt-dlp resolve) | `MediaOwnEngine.kt`, `MediaYtDlpDownload.jvm.kt` | done and checked | `test_media`: "media download completed", sha256 of the 20 MB file | fake yt-dlp returning one direct stream |
| yt-dlp download fallback, progress lines | `MediaYtDlpDownload.jvm.kt` | written, not run | none | `MediaYtDlpDownload.mingw.kt` (501 lines) |
| ffmpeg merge of video + audio, re-encode fallback | `LocalFfmpeg.jvm.kt` | written, not run | none | |
| HLS through FetchJet's own `HlsDownloader` | `HlsDownloader.kt` (common) | written, not run | none | Common code, no native test |
| yt-dlp with the app speed limit (throttling proxy) | `MediaThrottlingProxy.jvm.kt` | written, not run | `winhelpers proxy` (37); `test_proxy_limit` (512 KB/s through the shared proxy, both OSes) | Shared `platform/proxy/LoopbackProxy.kt` (round 3) on Windows and Linux; no real yt-dlp used it |
| Cookie retry (Facebook) / `SiteCookieJar` | `MediaInspectSteps.kt` (common) | written, not run | none | Common code |

## Add dialog: several links

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Link list with tags, select all, folder, start | `ui/AddDownloadLinkList.kt`, `DashboardAddDownload.kt` | done and checked | `test_links` (17): "list of four links", "both completed", "they went into the folder"; pictures `add-links-dark*` | |
| Per-row video title + quality menu, Retry, failed video blocks start | `AddDownloadLinkList.kt` | done and checked | `test_links`: "the video got its title...", "a video that failed blocks the start" | fake yt-dlp |
| Torrents / magnets inside the list (file list per row) | `AddDownloadLinkList.kt`, `TorrentFilesSelectionCard` | written, not run | none | Engine in the app; the link-list torrent rows were not run |
| Media pages from a browser batch open the list | `handleExternalBatch` (common) | written, not run | `test_bridge` covers `/links` with files only | |

## Add dialog: torrent

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Inspect magnet / `.torrent`, error | `ui/AddDownloadTorrent.kt` | done and checked | `test_torrent` (17, local seeder) on Windows and Linux | `ui/pages/AddTorrent.slint`, `core/presenter/AddTorrentPresenter.kt` |
| File list with selection and priorities, select all | `AddDownloadTorrent.kt` | done and checked | `test_torrent`: "c.txt is left out", "c.txt was not downloaded in full" |  |
| Download only (no upload) | `AddDownloadTorrent.kt` | written, not run | none | |

## Settings: page list

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Section list with summaries, wide and narrow | `ui/FdmSettingsPanel.kt` | done and checked | `test_settings` (87); pictures `settings-narrow-list-dark*`, `settings-narrow-page-dark` | `core/presenter/SettingsPresenter.kt`, one generic page from `SettingRowDto` |
| Keyboard focus ring on rows, sideways scroll of pickers | Compose | missing | none | STATUS "Not ported yet" |

## Settings: General

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Language (system + 15) | `ui/SettingsPages.kt` `LanguageSettingsCard` | done and checked | `test_settings`; pictures `settings-general-dark`, `settings-general-light-ar` | |
| Watch the clipboard + also video pages (switches) | `SettingsPages.kt` `GeneralPage` | done and checked | `test_settings`: "clipboard watch is off and the second switch is disabled" | The switch on is refused in tests; the watcher itself: see Clipboard |
| Theme System / Light / Dark | `GeneralPage` | done and checked | `test_settings`; pictures | |
| Speed shown in (unit) | `GeneralPage` | done and checked | `test_settings` | |
| Start at login + start hidden | `ui/StartupUi.kt`, `StartupLauncher.jvm.kt` | written, not run | registry helpers: `winhelpers system` (Run key) | `StartupLauncher.mingw.kt`; rows hidden in a development build (`launcherPath == null`) |
| Store build: "managed by Windows" + open startup settings | `StartupUi.kt` | written, not run | none | `managedBySystemUri = ms-settings:startupapps` |
| When downloads finish (choice) | `ui/WhenDoneUi.kt` `WhenDoneSettingsCard` | done and checked | `test_settings`: "when-done choice is listed" | The actions: see When downloads finish |
| Diagnostics "Save log to Downloads" | `SettingsPages.kt` (`DiagnosticsLog.canExport`) | n/a | | `canExport` is false on JVM and native |

## Settings: Downloads

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Save folder: path, Choose folder, Open, Use Downloads | `SettingsPages.kt` `DownloadsPage` | done and checked | `test_settings`; pictures `settings-downloads-*` | Choose folder dialog itself not run (real desktop) |
| Connections per download | `DownloadsPage` | done and checked | `test_settings` | |
| Speed presets (fast / medium / low, units) | `ui/SpeedPresetUi.kt` | done and checked | `test_settings`; pictures | |
| Speed schedule (time windows with caps) | `SettingsPages.kt` `SpeedScheduleCard` | done and checked | `test_settings`; picture `settings-downloads-dark-3` | Applying the cap at the time was not checked natively |
| Sort into folders | `DownloadsPage` | done and checked | `test_settings` | Effect on file placement not checked natively |
| Downloads at the same time (queue) | `ui/QueueUi.kt` | done and checked | `test_settings` | |

## Settings: Browser extension

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Browser list with state (connected / old / not added / not installed) | `SettingsPages.kt` `BrowserExtensionPage`, `ui/BrowserInstallSection.kt` | done and checked | `test_bridge`: "Edge is shown as connected with its version"; picture `settings-browser-dark` | |
| Add / update actions: unpack extension, open browser, open store, Firefox `.xpi` | `DesktopBrowsers.kt`, `BrowserIntegration.jvm.kt` | written, not run | none | `BrowserIntegration.mingw.kt` |
| Store versions (AMO, Edge) and GitHub `.xpi` lookup | `ExtensionStore.kt` (common) | written, not run | none | Needs internet |
| Dialogs: browser missing, load-unpacked steps | `BrowserInstallSection.kt` | done and checked | `test_settings`; picture `extension-steps-dark` | |
| Allowed / blocked origins with Revoke / Unblock | `SettingsPages.kt` `BrowserConnectionsGroup` | written, not run | none | Rows exist in `SettingsRows.kt`; no check |

## Settings: Network

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Proxy form (kind, host, port, user, password, Save, bad port error) | `ui/NetworkSettingsUi.kt` `ProxyCard` | done and checked | `test_settings`; pictures `settings-network-*` | |
| Site logins (add / remove) | `NetworkSettingsUi.kt` `SiteLoginsCard` | done and checked | `test_settings` | |

## Settings: Media tools, BitTorrent, Devices, Updates

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Media tools page: yt-dlp / FFmpeg / deno state, tools folder, button, progress | `ui/SettingsMediaToolsPage.kt` | done and checked | `test_settings`; pictures `settings-media-dark`, `settings-media-dark-ar` | The download behind the button: see Media tools |
| BitTorrent page: upload limit (presets + custom), download only, engine state | `SettingsPages.kt` `TorrentPage` | done and checked | `test_settings`; `test_torrent`: "the BitTorrent settings page is listed"; pictures `settings-torrent-*` | Shown when the torrent library is next to the core / EXE |
| Devices page: visible switch, paired / blocked lists | `SettingsPages.kt` `DevicesPage` | placeholder | `test_settings` with `debug.sections`; picture `settings-devices-dark` | Hidden (`DEVICE_SYNC_READY = false`) |
| Updates page: Check now, auto check, auto install, release notes link | `ui/UpdateUi.kt` `UpdatesSettingsCard` | done and checked | `test_settings` (made-up release, `debug.update`); pictures `settings-updates-dark`, `settings-updates-available-dark` | "Check now" is refused in tests |

## Update dialog and in-app install

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Dialog: new version, notes, Skip / Later, download progress | `ui/UpdateUi.kt` `UpdateDialog` | done and checked | `test_settings` (`debug.update`); pictures `update-dialog-dark`, `update-dialog-progress-dark` | `ui/pages/UpdateDialog.slint`, `core/presenter/UpdatePresenter.kt` |
| Update check against GitHub (API + web-page fallback) | `UpdateManager` (common) | written, not run | none | Runs only when automatic downloads are allowed |
| Signature check of the downloaded file | `UpdateSignature` (common) + platform verify | written, not run | `winhelpers crypto` (ECDSA P-256 checks) | `UpdateSignature.mingw.kt` |
| Install: `msiexec /i ... /passive`, quit, restart | `WindowsUpdater.kt` | written, not run | none | `PlatformUpdater.mingw.kt` writes `fetchjet-update.cmd`; picks `.msi` then `.exe`; off in development builds |
| Store build: ask the Store, "Open Microsoft Store" | `WindowsUpdater.latestStoreVersion` | written, not run | none | Same PowerShell WinRT script as the JVM |

## Browser bridge and pairing

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| `/ping`, `/config`, `/pair`, `/add`, `/links`, `/inspect`, `/show`, `/download` on 127.0.0.1:28450 | `desktopApp/.../main.kt` `startLocalHttpServer` | done and checked | `test_bridge` (31, real HTTP on test port 28459); `test_shell`: "the bridge of the real app answers" | `core/bridge/BridgeServer.kt`, `HttpListener.kt` on ktor-network |
| Host / Origin / `X-FetchJet` / token gates, CORS preflight | `main.kt` `isTrustedRequest`, `isAuthorized` | done and checked | `test_bridge`: foreign Host, web origin, header, 401, 405, 4 MB body | |
| Pairing dialog (Allow / Block, click outside = no), token stored encrypted | `BridgePairing` (common), `App.kt` dialog | done and checked | `test_bridge`: "a click outside means no", "allow gives a token", "the token is stored encrypted" | |
| Pairing hardening: one dialog at a time, 8 s repeat ignore, Block remembered | `BridgePairing` (common) | written, not run | none | Common code; not covered by `test_bridge` |
| 5 s dedupe on `/add`, inspect cache | `main.kt` `recentAdds`, `inspectCached` | done and checked | `test_bridge`: "the same click twice does not add twice", "an unknown quality asks the extension to inspect again" | |
| Browser fallback (open the link in the browser) | `main.kt` `openInBrowserFallback`, `App.kt` dialog | written, not run | none | `winShellOpen`, skipped when headless |
| `fetchjet://` registration, Edge extension registry entry on first run | `BrowserIntegration.jvm.kt` `setUpOnFirstRun` | written, not run | registry helpers: `winhelpers system` | Not done in a development build (writes nothing to the registry) |

## Extension dialogs (in the app)

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Extension pairing alert | `App.kt` | done and checked | `test_bridge` (pairing) | Alert component pictured with "remove" only |
| Browser missing / load unpacked steps | `BrowserInstallSection.kt` | done and checked | picture `extension-steps-dark` | |

## Launch and single instance

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Start with a link, `fetchjet://`, old `kortex://`, magnet, `.torrent` path | `main.kt` `parseUrlFromArg` | done and checked | `test_launch` (11) | `core/Launch.kt` |
| `--autostart=true`: hidden in the tray when "start hidden" is on | `main.kt` | done and checked | `test_launch`: "started at login: hidden in the tray by default" | Logic only; the hidden start on a real desktop was not seen |
| Store startup task detection (no arguments right after boot) | `WindowsStorePackage.looksLikeLoginStart` | written, not run | none | Native: Store build and uptime < 120 s |
| Second start hands the link to the running app / brings it forward | `main.kt` `trySendToRunningInstance`, `isAnotherInstanceRunning` | done and checked | `test_shell` (8): "the second instance ends by itself, quickly", "the first instance got the link" | Plus a named mutex in `host/main.cpp` |
| Bring the window to the front (Alt tap + top-most for 150 ms) | `main.kt` (Robot Alt) | written, not run | none | `host/main.cpp` `show_window`; tests run with `--no-focus` |

## Tray and shell

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Tray icon with tooltip of active downloads | `main.kt` `Tray` | written, not run | none | `Shell_NotifyIconW`; tooltip text built in `ShellPresenter.kt` (same rules) and cut to 128 chars by Windows |
| Tray left click shows / hides the window | `main.kt` `onAction` | written, not run | none | |
| Tray menu: Show, When done (checked item), Close | `main.kt` `menu` | written, not run | none | RTL menu in Arabic |
| Close button hides to tray (quit when no tray) | `main.kt` `onCloseRequest` | written, not run | none | |
| Tray icon back after Explorer restarts | AWT | written, not run | none | `TaskbarCreated` message |
| Window title, dark title bar in dark mode | `main.kt` (light title bar always) | written, not run | none | Deliberate difference (STATUS "Decisions"); not verified in a picture |
| System file / folder dialogs | Compose dialogs, own folder browser | written, not run | none | `IFileOpenDialog` |

## Clipboard watcher

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Watch the clipboard for downloadable links (and video pages) | `App.kt` (polling `getClipboardText`) | written, not run | clipboard helpers: `winhelpers system` | `core/presenter/ClipboardPresenter.kt` (800 ms, `GetClipboardSequenceNumber`); refused in tests |
| Clipboard offer dialog, brings the window forward | `HomeDialogs.kt` `ClipboardOfferDialog` | written, not run | none | Alert component |

## Start at login

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| MSI build: `HKCU\...\Run` value `com.michelle.raouf.fetch_jet` = `"<exe>" --autostart=true` | `StartupLauncher.jvm.kt` (AutoLaunch) | written, not run | `winhelpers system` (Run key read / write / delete) | Same value name as the JVM; that the JVM's AutoLaunch writes the same name and text was not verified |
| MSIX: startup task `FetchJetStartup` enabled | `desktopApp/msix/AppxManifest.xml` | written, not run | `check-msix.ps1` all passed (manifest) | Not installed |

## Notifications

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Tray notifications (`SystemNotice`, used by Send to device) | `main.kt` `trayState.sendNotification` | written, not run | none | `NIF_INFO` balloon with `NIIF_RESPECT_QUIET_TIME`; the only sender today is device sync, which is a placeholder |

## When downloads finish

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Countdown dialog with cancel, error alert | `ui/WhenDoneUi.kt` `WhenDoneDialogHost` | written, not run | none | Alert component; refused in tests |
| Exit the app | `WhenDone.onExitApp` | written, not run | none | `EffectKind.EXIT` |
| Sleep | `SystemPower.jvm.kt` | written, not run | `winhelpers system` (`winCanSuspend`) | `SetSuspendState` |
| Shut down | `SystemPower.jvm.kt` | written, not run | none | `shutdown /s /t 0` |

## Media tools (yt-dlp / ffmpeg / deno)

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| "Download the tools" / "Update tools" with progress | `MediaExtractorToolManager.jvm.kt` | written, not run | none | `MediaExtractorToolManager.mingw.kt`; refused in tests |
| yt-dlp unpacked build (`yt-dlp_win.zip`), SHA2-256SUMS check | `MediaExtractorToolManager.jvm.kt` (`tools/yt-dlp-dir`) | written, not run | zip helpers: `winhelpers zip` | |
| ffmpeg 4.4.1 with pinned sha256 | same | written, not run | none | |
| deno with sha256 | same | written, not run | none | |
| Auto update at start (`--version` vs latest tag), `MediaToolsLock` | `autoUpdateMediaTools`, `DashboardViewModel.autoUpdateTools` | written, not run | none | Off in every test / dev run |
| yt-dlp found and reported installed | `MediaToolPaths.jvm.kt` | done and checked | `test_media`: "the media engine is seen as installed" | fake yt-dlp |

## Proxy

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| HTTP proxy with login for downloads | `NetworkSettings.jvm.kt` (`JvmProxyLogin`) | done and checked | `winhelpers net` group `proxy` (open / login proxy, http and https targets) | Helper level (WinHTTP); not used inside the app yet |
| SOCKS5 proxy (with login) for downloads | OkHttp SOCKS in `StreamHttpClient.jvm.kt` | written, not run | `winhelpers proxy`: 37; `test_proxy_limit` SOCKS5 checks (both OSes) | Windows: shared `SocksForwarder` in front of WinHTTP; Linux: curl does SOCKS5 itself (`test_network`). Not set in Settings in the app |
| Site logins (`Authorization: Basic`) | `useNetworkSettings` (common) | done and checked | `winhelpers net` group `basic` | Helper level |
| yt-dlp `--proxy` | `ytDlpRuntimeArgs` | written, not run | none | |
| Torrent proxy settings | `Libtorrent4jBackend.applyProxy` | written, not run | none | Shared `FjtEngine.proxySettings` |
| yt-dlp speed-limit proxy goes direct, not through the app proxy | same on JVM | written, not run | `test_proxy_limit` | Kept as on the JVM (owner's open question) |

## Send to device (device sync)

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Send sheet: device list, modes, delete after, connect by IP, sending states | `ui/DeviceSendDialog.kt` | placeholder | `test_devices` (24, made-up devices); pictures `device-sheet-*` | UI done and checked, hidden in the app |
| Pairing dialog (6-digit code, confirm, cancel, block) | `DeviceSendDialog.kt` | placeholder | `test_devices`; picture `device-pairing-dark` | UI done and checked, hidden in the app |
| Engine: UDP 28461 announce, TCP 28462, frames, encryption, refusal words | `DeviceSyncImpl.jvmAndroid.kt` | written, not run | `run.sh sync` 125/0, `syncjvm` 44/0; Android emulator 17/0; Linux `test_sync_loopback` | Shared `DeviceSyncEngine` (commonMain); switched on in the factory, UI hidden (`DEVICE_SYNC_READY = false`) |

## Torrent

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Engine library | libtorrent4j 2.1.0 | done and checked | `winhelpers torrent` 37/0; `test_torrent` 17/0 (Windows release / debug, Linux) | Shared `platform/torrent/FjtLibrary.kt` + `FjtEngine.kt` over `fjtorrent.dll` / `libfjtorrent.so` |
| Metadata, file list, priorities, download, pause / resume, resume data | `Libtorrent4jBackend` | written, not run | `winhelpers torrent` (metadata, SHA-256 of files, resume data restore) | Not inside the app |
| Magnet with trackers, web-seed-only torrents | same | written, not run | `winhelpers torrent` (magnet `tr=`, `GET /web/...`) | |
| Torrent's own trackers / web seeds added, 7 public trackers | `Libtorrent4jBackend.trackersAndWebSeeds` | written, not run | `winhelpers torrent`: "announce seen by the tracker" | |
| Upload limit / download only | `TorrentPage`, backend | written, not run | `winhelpers torrent`: "download-only upload cap 32768" | |
| DLL shipped next to the EXE | jar | done and checked | stage / MSI / MSIX tree checks; `.deb` / `.tar.gz` with `libfjtorrent.so` | CMake copies `fjtorrent.dll`; `stage.ps1` takes it from `desktopNative\torrent\build` |
| Clean shutdown of the session | lt4j | done and checked | `test_torrent`: "fj_shutdown with a torrent session returns within 5 s" | `NativeApp.shutdown()` closes the session |
| Parking paused / finished torrents | n/a on JVM | missing | none | Plan 6.2; baseline = destroy after the last torrent is removed |
| Web seeds over WinHTTP | `TorrentStreamer.jvm.kt` | written, not run | none | `TorrentStreamer.mingw.kt` uses the default ktor client, not `WinHttpNative` |

## Windows packaging

| Feature | JVM app | Native status | Evidence | Note |
|---|---|---|---|---|
| Release build with version resource | jpackage | done and checked | `packaging/build-release.cmd` EXIT 0; version resource read back (1.4.0, Michelle Raouf, ...) | `app.manifest` still has a fixed `1.4.0.0` |
| Per-user MSI: `%LOCALAPPDATA%\FetchJet`, shortcuts, upgrade of any other version | jpackage MSI | written, not run | `check-msi.ps1` all passed (22 table checks, `msiexec /a` tree = stage) | Never installed; JVM MSI -> native MSI upgrade not tried |
| `.torrent` association with the JVM ProgId name | jpackage MSI | written, not run | `check-msi.ps1` (`.torrent` rows) | ProgId name read from the 1.0.9 MSI only |
| `fetchjet://` / `magnet:` in the MSI | runtime registration (`fetchjet://`) | missing | none | Owner's call (packaging hand-over "Left" 3); the app still registers `fetchjet://` at run time |
| MSIX for the Store (same identity, startup task, protocols, alias) | `desktopApp/msix/AppxManifest.xml` + CI | written, not run | `check-msix.ps1` all passed | Unsigned, never installed or submitted |
| Licence notices (Kotlin libs, Slint, VC runtime) | `THIRD_PARTY_NOTICES.md` | done and checked | staged and tree-compared by `check-msi.ps1` | libtorrent, Boost and OpenSSL notices added to `THIRD-PARTY.txt` on 2026-11-10 (from the libtorrent 2.1.2 and MSYS2 clang64 licence files) |
| CI job for the native build | `.github/workflows/build.yml` (JVM) | written, not run | YAML parses | `windows-native` and `linux-native` jobs, only with the `native` input; never started |
| RAM of the release build | 457 -> ~250 MB measured (ROADMAP 1.4.0) | done and checked | STATUS "Linux port, third round" (hidden desktop, release EXE) | Idle / 300 MB / 1 MB/s numbers there |

## Linux

The Linux app (2026-11-10, rounds 1-3) is the same core and UI built for `linuxX64`; only the OS
layer differs (`LINUX.md`). Status words as above; "same as Windows" means the row above is shared
code and the Linux run of the same test passed. Tests ran in WSL (Ubuntu 26.04, WSLg), not on a
real Linux desktop.

| Area | Linux status | Evidence (Linux) | Note |
|---|---|---|---|
| Home, details, Add dialog (direct, media, links), settings pages, alerts | done and checked (same as Windows) | `test_home` 41, `test_add` 29, `test_media` 31, `test_links` 17, `test_settings` 87, `test_devices` 41 | Pictures `shots-linux\r3-*` |
| Fonts and icons | done and checked | pictures `r3-add-dialog-dark`, `r3-arabic-rtl-download`, `r3-settings-dark`, `r3-portable-release-dark` | Bundled FetchJet Sans + emoji pictures, same as Windows; CJK falls back to the system |
| Direct downloads (HTTP, HTTPS, ranges, pause / resume) | done and checked | `test_download` 29 over HTTP and over HTTPS | libcurl through `dlopen` |
| HTTP proxy / SOCKS5 with login for downloads | done and checked | `test_network` 17 | curl does both itself |
| yt-dlp speed-limit proxy | written, not run | `test_proxy_limit` 13 (shared loopback proxy, 512 KB/s held) | No real yt-dlp used it (round 3; before it was `--limit-rate`) |
| Browser bridge and pairing | done and checked | `test_bridge` 33 + 1 skip (no Edge in WSL) | |
| Launch arguments, single instance, hand-over | done and checked | `test_launch` 11 | `flock` in `$XDG_RUNTIME_DIR` |
| Torrents (engine, `.torrent`, magnet, file selection, shutdown) | done and checked | `test_torrent` 17 (local seeder `fjtseed` built for Linux) | Shared `FjtLibrary` / `FjtEngine`; `libfjtorrent.so` in the packages |
| Send to device | written, not run in the app | `test_sync_loopback` (two cores, pairing, link send) | UI hidden like on Windows |
| Media tools download (yt-dlp / ffmpeg / deno Linux assets) | written, not run | none | Tests forbid internet |
| Tray, close to tray, start hidden | done and checked (fake watcher only) | `tests/host/test_tray_linux.py` 20/0 (round 4) | `host/linux/tray_linux.cpp`: StatusNotifierItem + dbusmenu over GDBus (`libgio` by `dlopen`); no watcher = close quits as before; not seen on a real KDE / GNOME panel |
| Notifications | written, not run | none | `notify-send` (not in WSL) |
| File / folder dialogs | written, not run | none | xdg-desktop-portal through `gdbus` (round 3), then zenity / kdialog; none of them in WSL |
| Dark mode from the system | done and checked | pictures with `FETCHJET_THEME` | Portal / gsettings / GTK_THEME read, not seen changing live |
| Clipboard watcher | written, not run | none | wl-paste / xclip / xsel, none in WSL |
| Start at login, sleep / shut down, in-app update | written, not run | none | autostart `.desktop`; `systemctl`; update = offer the `.deb` / `.tar.gz`, no in-app install |
| Secrets | written, not run | `test_settings` | AES-GCM with a key file; Secret Service not used |
| Packages | done and checked | `build-linux.sh`: `.tar.gz` and `.deb` built; the portable folder ran a download | Never installed (`dpkg -i` needs sudo) |
| CI | written, not run | YAML parses | `linux-native` job (`ubuntu-latest`, artifacts only) |

## Things in the existing docs that look wrong or out of date

1. `STATUS.md` "Work in progress" is older than the hand-over notes: it says the torrent draft and
   the proxy files were "never compiled" and parked, and the device-sync files parked outside the
   tree. The hand-overs say `fjtorrent.dll` builds (34 / 37 checks) and the proxy code compiled
   and passed 37 checks; the device-sync files and `WinLock.kt` / `WinLan.kt` are in the tree now
   (untracked, not `AD`).
2. `STATUS.md` points to the hand-over notes in the side-session folder; those copies are the
   01:55 versions. The newer ones are `desktopNative/docs/port/handover/`.
3. `native-port-torrent.md` contradicts itself: the top says it builds and tests ran, while
   "Files created" still says `fjtorrent.cpp` "never compiled", `tests/` "empty", and
   "Not written yet" lists files that exist.
4. `native-port-proxies.md` keeps the first-session sections ("removed from the working tree",
   "the three files are back to their placeholder text") under the newer state.
5. `native-port-packaging.md` section 5 "Not written yet" lists `stage.ps1`, the MSI and MSIX
   scripts, which section 9 says were written and run.
6. `STATUS.md` "Compiled but not run yet" lists "yt-dlp inspection and download", but
   `test_media` runs both (with a fake yt-dlp); what never ran is the real yt-dlp.
7. `STATUS.md` "Done and checked" contains the torrent Add-dialog row whose own text is "written,
   compiles; not run".
8. `STATUS.md` says winhelpers has 155 checks; `run.sh` now also runs `proxy` by default (37 more)
   and has a `torrent` mode, so the number is stale.
9. The device-sync hand-over says the factory switch and tests are "not written"; winhelpers now
   has `DeviceSyncTestSupport.kt`, so another session is working on it and that note is stale too.
