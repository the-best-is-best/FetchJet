# FetchJet: project memory for Claude Code

Last updated: 2026-11-02. Read this first, then `README.md` and `CLAUDE_HANDOFF.md` (the
handoff is from 2026-10-28 and partly outdated).

## Owner rules (follow strictly)

- The owner is Michelle (Egyptian Arabic speaker). **He is a man**: use masculine Egyptian Arabic
  forms (جرّب، عملت، ابعتلي), never feminine (جربتي، عملتي، ابعتيلي). Reply in **Egyptian Arabic**,
  short and direct. No long explanations.
- **Never run `git commit`, `git push`, `git reset`, or change branches on your own.** Make the
  change, show what changed, and let the owner test. Commit only after the owner says it is OK.
- Commit message and PR attribution: follow the rule given by the session, not this file.
- Do not add code comments unless asked.
- Do not change code without the owner's go-ahead when they say "لا مش عتعدل" or similar.
- Do not trigger GitHub Actions releases on your own. The owner runs them.
- Do not upload the browser extension to a store unless the owner asks.
- When the owner says "اشتغل" or "يلا", do the task they just described. Ask at most one question.
- **Verify before applying.** Anything you suspect or are not sure of: read the code (ours or the
  bundled yt-dlp), add a timing/diagnostic log, or test it locally first. Do not ship a guess.
- **iOS:** edit iOS normally whenever a change needs it, even though it cannot be built on Windows.
  It is tested later on a Mac. List every iOS/Swift change that still needs a Mac test (see below).

## What the project is

FetchJet is a Kotlin Multiplatform / Compose Multiplatform download manager (IDM-style). Platforms:
Windows, macOS, Linux (JVM desktop), Android, iOS (sideloaded, full edition), and a Chromium/Firefox
browser extension (MV3).

Features: multi-connection HTTP piece downloads, video downloads through yt-dlp (with FetchJet's own
engine doing the piece fetch and ffmpeg merging), HLS, torrents via libtorrent4j (desktop/Android)
and libtorrent through our own `FJTorrent` wrapper (iOS), queue, scheduling, clipboard monitoring,
browser bridge on `127.0.0.1:28450`.

Competitors the owner compares against: IDM, EZOO (Ezoo Download), and the iOS app they mentioned.
The owner wants speed parity and smooth UX.

## Layout

| Path                          | What                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sharedLogic/`                | All engines and logic. `commonMain`, `jvmMain`, `jvmAndroidMain`, `androidMain`, `iosMain`.                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `sharedUI/`                   | Compose UI shared by desktop, Android, iOS. Home screen: `App.kt` (state + layout), `TaskList.kt`, `TaskDetails.kt`, `HomeNavigation.kt` (sidebar, rail, top bar, bottom nav), `HomeDialogs.kt`, `SettingsContent.kt`. Add dialog: `ui/AddDownloadModalDialog.kt` (state + layout), `ui/AddDownloadSections.kt` (header, URL, engine, name, destination, actions), `ui/AddDownloadMedia.kt`, `ui/AddDownloadTorrent.kt`. Settings: `ui/FdmSettingsPanel.kt`, `ui/SettingsPages.kt`, `ui/SettingsMediaToolsPage.kt`. |
| `desktopApp/`                 | JVM app. `main.kt` has the local bridge (`inspectCached`, `inspectGates`, `recentAdds`).                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `androidApp/`                 | Android app. `ExtensionBridgeServer.kt`, `MainActivity.kt`, `AndroidManifest.xml`.                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `iosApp/`                     | Xcode project (hand-edited, ids `B2…`/`B3…`). Swift bridges: `NativeMediaExtractorBridge.swift`, `NativeTorrentBridge.swift` (on `iosApp/FJTorrent`).                                                                                                                                                                                                                                                                                                                                                               |
| `extension/`                  | Browser extension. `background.js`, `content.js`, `manifest.json` (Chromium), `manifest.firefox.json`. Build: `build.ps1` (Windows) / `build.sh`.                                                                                                                                                                                                                                                                                                                                                                   |
| `.github/workflows/build.yml` | CI. `workflow_dispatch` inputs: `release_version`, `android`, `windows`, `macos`, `linux`, `ios`, `extension`, `stores` (default true).                                                                                                                                                                                                                                                                                                                                                                             |
| `docs/`                       | GitHub Pages download page.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `CLAUDE_HANDOFF.md`           | iOS engine details (Python 3.13 + yt-dlp, curl_cffi). Its torrent part is outdated: see Open issue 8.                                                                                                                                                                                                                                                                                                                                                                                                               |

Version constants:

- `sharedLogic/.../core/update/AppVersion.kt` → `APP_VERSION = "1.2.0"` (branch `release-v1.2.0`;
  `master` has 1.1.0)
- `iosApp/Configuration/Config.xcconfig` → `MARKETING_VERSION = 1.2.0`,
  `CURRENT_PROJECT_VERSION = 4`
- `extension/manifest.json` and `manifest.firefox.json` → `3.2.0` (also `manifest.safari.json`)

## Build and run (Windows `E:\tbib\FetchJet`, or the Mac `~/Desktop/KMP/FetchJet`)

```
.\gradlew.bat :desktopApp:run
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinJvm
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64    (or the iOS target you need)
extension\build.ps1
```

- Verbose engine logs: `fetchjet.verboseLogs=true` in `gradle.properties`, or env
  `FETCHJET_VERBOSE_LOGS` (wins over the property). Android Studio runs use the property, so set it
  to `true` for a measuring build and back to `false` after. CI releases use
  `vars.FETCHJET_VERBOSE_LOGS || 'false'`, so a release is never verbose unless the owner sets it.
- Android verbose builds: inspect logs every yt-dlp line with elapsed ms (`Inspect +NNNNms ...`),
  plus `Inspect: begin/end`, `title fetch`, `runtime ready`, `formats parsed`, `size probe`. The
  owner
  exports the log from the app (Export log -> Downloads) and sends the file.
- Desktop log: `~/.fetch_jet/logs/app.log`.
- Android device: the owner's A56 over wireless debugging (`adb mdns services` finds it when it is
  on).
  `adb install -r` fails if the installed app was signed with another key (release / Windows debug):
  ask before `adb uninstall`, it wipes the app data. Bridge from the Mac:
  `adb forward tcp:28451 tcp:28450`,
  then POST `/add` with header `X-FetchJet: 1`. Permission dialogs on the phone are the owner's to
  tap. POST `/links` (JSON array of `{url, fileName}`) queues downloads directly, no dialog (desktop
  port 28450 too). The UI can be driven with `adb shell input tap` + `uiautomator dump`
  (set `MSYS_NO_PATHCONV=1` in Git Bash); the header shows the total speed (`↓ 10.5 MB/s`).
  The app's task list is `files/settings/tasks_cache.json` (`adb shell run-as <pkg> cat ...`).
- Test files:
  `https://media.githubusercontent.com/media/inventer-dev/speed-test-files/main/<N>MB.bin`
  (1-128 MB) work from Egypt. thinkbroadband returns 403 for Egyptian IPs (server-side block, do
  not try to get around it). Hetzner `*-speed.hetzner.com` allows one connection per IP (429 for
  the rest): good for testing the 429 handling, bad for speed tests.
- Git submodule `packages/kmp-localization` (project `:kmpLocalization`). After a clone or a pull
  that adds it, run `git submodule update --init --recursive`, else every Gradle build fails with
  "Configuring project ':kmpLocalization' without an existing directory" (in Xcode this shows only
  as "Command PhaseScriptExecution failed with a nonzero exit code").
- iOS dev loop: `scripts/ios-dev-build.command [gradle|xcode|all]` → `build/ios-dev-build.log`.
  An incremental build does not re-embed `FetchJetSafariExtension.appex` into `FetchJet.app` when
  only `extension/` JS changed: rsync `Debug-iphonesimulator/FetchJetSafariExtension.appex` into
  `FetchJet.app/PlugIns/`, then `codesign --force --sign -` the appex and the app (or Clean Build
  Folder). CI builds clean, so it is not affected.
- `iosApp/FJTorrent/` is excluded from formatting (`.clang-format` `DisableFormat`). An IDE
  reformat split `NS_SWIFT_NAME(...)` over lines, Swift then saw no `find`/`add`/`setLimits` and the
  iOS CI archive failed. Keep each `NS_SWIFT_NAME(...)` on one line.
- Windows runner uploads (`gh release upload`) were ~3 Mbit/s while WSL on the same PC got 20-27
  (2026-11-02, both over Wi-Fi; the Killer E2600 Ethernet was not connected). Stopping the Killer
  bandwidth service (KNDBWM) helped only half the time. After the owner switched to the Wi-Fi
  network "Mesho_5G 2", Windows and WSL both upload a steady ~15 Mbit/s. Cause not proven (the
  old Wi-Fi network and/or Killer software). Slow release uploads from Windows = check this first.
- Testing an in-app update on the Mac: back up `/Applications/FetchJet.app`, set `APP_VERSION`
  to an older version (e.g. `1.0.9`), `./gradlew :desktopApp:createDistributable`, set it back,
  `ditto desktopApp/build/compose/binaries/main/app/FetchJet.app /Applications/FetchJet.app`.
  To get the update dialog at start without clicking Settings, set `lastCheckedMs=0` in
  `~/.fetch_jet/updates.properties` (the auto check runs at most twice a day). The update files
  land in `$TMPDIR/FetchJetDownloads/fetchjet-update/` (`fetchjet-update.log`).
- Self-hosted Mac runner: `~/actions-runner`, installed as a launchd service
  (`./svc.sh start|stop|status`, or `./run.sh` in a terminal).

## Key engine pieces (sharedLogic)

- `feature/http_downloader/HttpChunkDownloader.kt`: `ChunkLayout` (pieces, split-half of biggest
  running piece when a worker is free, `.partN` files, `.chunks` manifest). `MAX_CONNECTIONS = 32`,
  `STALL_FAIL_MS = 180_000`. Min piece 512 KB. Connection count order: `task.maxConnections` →
  `BehaviorSettings.connections` → auto by size.
  429 / 503 → `ServerBusyException` → `ChunkLayout.serverBusy`: the connection cap drops to the
  connections still working, then +1 every `CAP_RAISE_MS` (10 s). Before this, a server that allows
  one connection per IP (Hetzner speed files) took 120-138 s for 100 MB instead of 13 s; now ~14 s
  (= curl). Added 2026-11-01.
- `core/network/PieceDownloader.kt`: probe with `Range: bytes=0-0`, retry schedule (2 s, 4 s, fresh
  link, UA-only headers, fresh link). 403/410 → `ExpiredLinkException` → re-resolve via
  `urlProvider(refresh=true)`. Only HTTP 200 to a Range request is `RangeNotHonouredException`.
  416/429/5xx are retried.
- `core/network/StreamHttpClient.kt` (expect/actual): JVM uses Java HTTP client (HTTP/1.1), Android
  uses OkHttp (HTTP/1.1, logs 403), iOS uses Darwin.
- `feature/media_extractor/DirectMediaResolver.kt`: `yt-dlp -j -f <selector>`. Rejects
  m3u8/mpd/non-http formats. Falls back to
  `bestvideo[height<=H][protocol^=http]+bestaudio[protocol^=http]`.
- `feature/media_extractor/MediaStreamDownloader.kt`: `downloadWithOwnEngine` = resolve →
  PieceDownloader per stream → local ffmpeg merge (`runLocalFfmpeg`, copy then re-encode fallback).
  Falls back to yt-dlp on any failure before download. `inspectMediaQualities` retries without
  cookies when no numbered resolution (Facebook), marks site as cookie-unhelpful via
  `SiteCookieJar`.
- `feature/media_extractor/MediaUrls.kt`: `youTubeVideoId()` and `canonical()`
  (shorts/live/embed/youtu.be → watch?v=ID). Used as cache key.
- `feature/media_extractor/HlsDownloader.kt`: segments are virtual pieces (UNIT = 1000 bytes),
  publishes pieces throttled to 250 ms.
- `feature/queue_manager/QueueManagerRepository.kt` + `TaskCache.kt`: tasks persist in
  `tasks_cache.json` through kotlinx.serialization (`PersistedTask`, `TaskCache.encode/decode`,
  `toPersisted()` / `toTask()`). The file format is the same as 1.1.0 (same field names, "" for
  none,
  torrent files as `index:selected:priority;...`), so old files load and 1.1.0 can still read new
  ones. Entries decode one by one (a broken entry is skipped). Tests: `sharedLogic/src/jvmTest`
  (`TaskCacheTest`, `MiniJsonTest`); `FETCHJET_TASKS_FILE=<path>` also checks a real cache file.
- `core/util/MiniJson.kt`: `MiniJson.parse` now wraps kotlinx `parseToJsonElement` and returns plain
  Map/List/String/Long/Double/Boolean, so the `getString/getLong/...` call sites (yt-dlp JSON,
  bridges,
  update check) are unchanged. If the whole text is not JSON it parses the first `{`/`[` line.
- `core/log/DiagnosticsLog.kt`: expect/actual. Android actual tees stdout/stderr to
  `filesDir/logs/app.log` (2 MB rotate). Export via MediaStore Downloads (API 29+). Only installed
  in debug or verbose builds.
- `core/settings/BehaviorSettings.kt`: connections, sortIntoFolders, FileCategory folders
  (`stripTypeFolders`).

Media tools (yt-dlp, ffmpeg, JS runtime):

- `feature/media_extractor/MediaExtractorToolManager.kt` (expect/actual):
  `downloadMediaExtractorTool(onProgress)` (the "Update tools" button), `autoUpdateMediaTools`
  (runs at app start from `DashboardViewModel.autoUpdateTools`, guarded by `toolsMutex`),
  `getJsRuntimeStatus()`, `latestGitHubReleaseTag(repo)`. Progress goes to
  `toolDownloadName/Fraction` in the UI state: progress bar on the Media tools page and
  `ToolsDownloadBanner` above the home list.
- Desktop: tools in `~/.fetch_jet/tools` (yt-dlp, ffmpeg pinned 4.4.1, deno from denoland releases
  with sha256 check). Auto-update compares `--version` with the latest GitHub tag. Every yt-dlp call
  gets `ytDlpRuntimeArgs()`: `--js-runtimes deno:<path>|deno`, `node`, `--cache-dir
  ~/.fetch_jet/cache/yt-dlp`. Desktop inspect is fast (qualities appear on hover), so no EJS cache.
- Android: python/ffmpeg/QuickJS are bundled as `.so` (Android 10+ cannot run downloaded
  binaries, so they can only change with a new APK). yt-dlp (a Python zip, data not a binary) ships
  in
  `res/raw/ytdlp` (copied there by CI) and is auto-updated at start; the owner chose to keep it
  bundled. JS runtime = QuickJS. deno for
  Android exists only as an unofficial ~100 MB package: not used. `RuntimeManager` passes
  `--cache-dir filesDir/yt-dlp-cache` (it used to pass `--no-cache-dir`) and
  `--plugin-dirs filesDir/fetchjet-ejs-cache-plugin`.
- iOS Python: **3.14.10** (BeeWare `3.14-b14`) + curl_cffi payload `v0.16.3` (SwiftCurlCffi-iOS,
  built for Python 3.14), pinned in `scripts/ios-prepare-frameworks.sh` (owner's choice,
  2026-11-02). Before, the app embedded 3.13 while the payload was built for 3.14, so curl_cffi was
  skipped and impersonation sites could 403. The script replaces a cached Python / payload of
  another version. SwiftCurlCffi v0.17.0 = Python 3.15 + curl_cffi 0.17: bump PY_VERSION,
  PY_BUILD and CC_TAG together. The Swift bridge is version-agnostic (finds `python3.*`, uses only
  PyGILState_* / PyEval_SaveThread). The "Install Python Runtime" Xcode phase deletes any
  `python/lib/python3.*` in the app that is not the embedded version. An earlier 3.14 build crashed
  on the simulator (EXC_BAD_ACCESS in PythonKit's `PyObject_CallObject`, engine stuck at
  "Preparing engine"): PythonKit took the borrowed `args` / `kwargs` of Swift callbacks with
  `consuming` (one release too many); PythonKit is now vendored at `iosApp/PythonKit` (local Swift
  package, upstream fe7d973, Apache-2.0, fix in its README). **Verified 2026-11-02 on the Mac
  simulator (iPhone 17), clean build with 3.14.10:** log `engine ready: yt-dlp 2026.10.26 on
  Python 3.14.10, curl_cffi 0.16.3`; YouTube `jNQXAC9IVRw` listed 15 formats and downloaded;
  9gag `ae5Ag7B` (Media engine) listed 3 formats and downloaded 1.9 MB; no "no impersonate target"
  warning, no crash report. Not exercised: a YouTube format that needs the ffmpeg merge, a real
  iPhone. Test for curl_cffi: inspect https://9gag.com/gag/ae5Ag7B (pick Media: the link is
  detected as Direct); the log must NOT say "no impersonate target is available".
- iOS: in-process Python + yt-dlp, JS runtime = WebKit plugin (`yt-dlp-apple-webkit-jsi`, an
  `EJSBaseJCP` subclass). Auto-update calls Swift `updateYtDlpModule()` (takes effect next launch).
  deno is impossible on iOS (no JIT for apps, no subprocess).
- `EjsCachePlugin.kt`: tiny yt-dlp plugin that sets
  `EJSBaseJCP._ENABLE_PREPROCESSED_PLAYER_CACHE = True` (yt-dlp keeps it off because the files are
  ~4.3 MB and never rotated). `pruneEjsPlayerCache` keeps the newest 2 `challenge-solver/player*`
  files. `--plugin-dirs` treats each **child** folder as a plugin root, so the file lives at
  `<dir>/fetchjet/yt_dlp_plugins/extractor/fetchjet_ejs_cache.py`.
- Size probes: formats without `filesize`/`clen` get a HEAD request. Skipped for `m3u8` and `mhtml`
  (storyboards) on all platforms.

Bridges:

- Desktop: `ExternalDownloadBridge.onShowAndFocus`. Windows focus trick (Robot Alt tap +
  alwaysOnTop). Dedupe on `/add` for 5 s.
- Android: `ExtensionBridgeServer.deliver()` opens the activity, or posts notification "Link
  received" (channel `fetchjet_links`) when `SYSTEM_ALERT_WINDOW` is not granted. Settings row "Pop
  up over the browser".
- Extension: `background.js` has `downloadItem`, `inspectWithApp`, `sendToFetchJet`,
  `isStreamFragment`, `recentSends` (8 s dedupe), quality labels via regex.
- Extension languages (2026-11-03): `extension/i18n/` holds the extension's texts in the app's
  15 languages: `i18n/i18n.js` (the functions: `fjT(key, vars)`, `fjReason(code)`,
  `fjApplyTexts(document)` for `data-i18n` / `data-i18n-title`, `fjIsRtl`, `fjOnLanguage`,
  `fjUseLanguage`, `fjAddLanguage`) and one file per language (`i18n/en.js`, `i18n/ar.js`...:
  `fjAddLanguage("ar", {...})`). A new text = add the key to `en.js` and every language file,
  then `python scripts/extension-i18n/check.py` (missing / unknown keys, `{placeholders}`). A
  missing key shows the English text. A new language = its file, its code in `KNOWN`
  (`i18n.js`) and the file in every list below. The language is the app's: `/config` (desktop
  `main.kt`, Android `ExtensionBridgeServer`) returns `"language"`, the background stores it as
  `appLanguage` in `storage.local`, and the content script, popup and options follow it live (RTL
  for ar / fa, sizes kept LTR). Without the app: the browser's language. iOS / Safari has no
  `/config`, so it uses Safari's language. The `i18n/` files load first everywhere:
  `importScripts` in the service worker (and the `executeScript` fallback in
  `collectAndSendLinks`), first in `content_scripts.js` and Firefox `background.scripts` of
  all three manifests, `<script>` tags in popup / options; `build.ps1`, `build.sh` and the
  Xcode "Copy Web Extension Files" phase copy the whole folder. Reasons
  (`reason: "app not running"`...) stay English codes
  between scripts and are translated only when shown (`fjReason`); `resolveVideo` returns
  `notRunning: true` instead of the content script matching English text. `start()` now applies
  the saved language at once (before, `/config` answered "en" for the first seconds). Platform
  files hold string keys (`cookiesNote`, `status`, `hint`, `shortcutsText`). Checked with stubbed
  pages: popup and options in Arabic RTL, SoundCloud button "تنزيل هذا الصوت". Owner-verified in the
  installed Edge extension (2026-11-03). Not checked: Firefox, Safari.
- YouTube: only the main video gets a page button (`place()`, `oneVideoSite`): during ads the
  owner saw 3 buttons (ad / preview players). Not re-checked on a real ad yet.
- Extension per-browser UI (2026-11-01): `options.js` / `popup.js` read `FJ_PLATFORM` from
  `platform.js`, which differs per build like the manifest: `platform.js` (Chromium: Chrome, Edge,
  Brave, Opera, Vivaldi, Arc; also used when loading the folder unpacked), `platform.firefox.js`,
  `platform.safari.js`. `build.sh` / `build.ps1` and the Xcode "Copy Web Extension Files" phase copy
  the right one as `platform.js`. It lists the settings rows to hide (`hidden`), the shortcuts page
  text, and on Safari hides "Get qualities" (`qualitiesInPopup: false`). Brave/Opera/Vivaldi/Arc
  install the Chrome build.

## Current state (master, 1.1.0)

- **Check `git status` and `git log` before assuming anything.** The owner always commits himself;
  never ask or offer to commit.
- 2026-11-01 (late, not committed when written): 429 connection cap (above); Android
  `usesCleartextTraffic="true"` (plain `http://` downloads failed with "CLEARTEXT not permitted";
  iOS already has `NSAllowsArbitraryLoads`); extension `hasSiteAccess()` in `shouldTake`: when
  Chrome/Edge site access is "On click" and the page's site is not allowed, browser downloads stay
  in the browser (the `downloads` permission ignores site access). Owner-verified on Edge: the
  browser downloads the file, no "Launching FetchJet" / Untitled tab. The owner said that with the
  extension turned off in `edge://extensions` downloads
  still reached the app: cause unknown, not investigated.
- Also 2026-11-01 (late): Android crash `ForegroundServiceDidNotStartInTimeException` when a
  download failed right after it started (service stopped before `startForeground`): `stop()` now
  waits for `onStartCommand` (`awaitingForeground` / `stopRequested` in
  `DownloadForegroundService`); 9 instant-failing downloads on the A56, no crash. `errorMessage` is
  now saved in `tasks_cache.json` (after a restart failed tasks showed "Failed: error"). Details
  page shows "Source" (download URL) and "Page" (Referer / original URL) with a Copy button.
- Switching the Add dialog engine Media → Direct kept the media name (`x.mp4`);
  `OnEngineSelected` now clears `inspectedMediaTitle` and recomputes the suggested name.
  `codeHostRawUrl` in `enqueueTask`: `github.com/o/r/blob/...` → `/raw/...` (and GitLab `/-/blob/`);
  blob links are HTML pages and failed as "Protected by Cloudflare". Verified desktop + A56. The 429
  cap also verified on the A56 (Hetzner 100 MB in ~20 s, no slow tail).
- Multi-select delete in the task list. Wide rows (>= 720 dp: desktop, laptop, tablet)
  always show a checkbox, plus a select-all checkbox in the column header. Phone rows: long-press
  starts selection, then a tap toggles a row; Android back clears it (`NavigationBackHandler`,
  dependency `navigationevent-compose` 1.1.0 added to `sharedUI`). While rows are checked,
  `SelectionBar` (TaskList.kt) replaces the toolbar: count, Select all, Delete, ✕. Delete sends
  `OnRemoveSelectedClicked(ids)` → `removeConfirmationTaskIds` → the same `RemoveConfirmationDialog`
  (with count and "also delete files"). Tested on the A56 (long-press, tap, back, Select all +
  Delete).
- MSIX (Store build): the startup task is now `Enabled="true"` (owner's decision, 2026-11-02),
  so FetchJet starts at login by default, hidden in the tray (`looksLikeLoginStart`). Windows
  runs it only after the user opened the app once; the user turns it off in Settings > Apps >
  Startup or Task Manager. The MSI / macOS / Linux builds stay opt-in (AutoLaunch toggle).
  Owner-verified on the previous MSIX (task enabled by hand in Task Manager > Startup apps):
  FetchJet starts at login. Not yet tested: the new default (on without touching it).
- 1.2.0 updates (branch `release-v1.2.0`): `UpdateManager` falls back to the release web pages
  (`fetchLatestFromWebPage`) when the API answers 403/429. In-app install on every platform:
  Android opens the system installer (tested on the A56 up to the installer), macOS swaps
  `/Applications/FetchJet.app` from the downloaded .dmg, Linux installs the .deb with `pkexec`
  or swaps the portable folder (script run after the app quits, `fetchjet-update.sh/.log` next
  to the download). Desktop code: `PlatformUpdater.jvm.kt` only picks the OS; each OS has its
  own file in `jvmMain/.../core/update/`: `WindowsUpdater.kt` (MSI + Store check),
  `MacUpdater.kt`, `LinuxUpdater.kt`, shared helpers in `DesktopUpdater.kt`
  (`DesktopInstall.runShellAfterExit`, `shellQuote`, `firstAsset`). Change one OS = edit its file.
  macOS in-app update verified 2026-11-02 on the Mac (local 1.0.9 in `/Applications` -> 1.1.0,
  swapped and reopened by itself, data kept; the script also deletes the temp mount folder and
  the .dmg). Linux `.deb` path counted as tested (owner's decision, no Linux desktop): do not
  list it as an open test again; revisit only on a user report. Release notes without the API: CI
  uploads
  `release-notes.md` with each release (markers included); the web-page fallback reads it, or the
  notes text on the release page (`notesFromReleasePage`) for releases without it. The API
  limit is 60 checks/hour per IP: repeated update tests use it up (then the fallback runs).
  Windows MSI in-app
  update verified 2026-11-02: a local 1.0.9 MSI found 1.1.0, downloaded it, restarted into
  1.1.0 (test MSI uninstalled after; the Store MSIX and `~/.fetch_jet` were untouched).
  The Store MSIX asks the Store, not GitHub (owner's request), so it never announces a version
  Microsoft has not approved yet: `WindowsUpdater.latestStoreVersion()` runs PowerShell with
  WinRT `StoreContext.GetAppAndOptionalStorePackageUpdatesAsync` (works because child processes
  of the MSIX keep its package identity; outside the package it just answers "no update"). A
  found update shows the dialog with "Open Microsoft Store"
  (`ms-windows-store://pdp/?productid=9NP3HJRHMPJS`). Tested inside the installed MSIX with
  `Invoke-CommandInDesktopPackage` (answer: no update for 1.1.0). It never downloads the MSI.
- 1.2.0 several links at once: see ROADMAP-1.2.0.md item 2 (`linkList` in the UI state,
  `extractLinks` / `addLinkList` in `DashboardAddDownload.kt`, `exportLinks` in
  `DashboardTaskEvents.kt`, `TextFilePicker` expect/actual, `ui/AddDownloadLinkList.kt`).
  Torrents and magnets in the list are read one by one (`inspectListedTorrents`) and show their
  files under their row (`TorrentFilesSelectionCard`: checkbox + priority per file).
- 1.2.0 languages (owner's library `kmpLocalization`, git submodule `packages/kmp-localization`,
  owner's private repo; CI checks it out with secret `SUBMODULE_TOKEN`; FetchJet's
  `libs.versions.toml` + root `build.gradle.kts` carry the catalog names it needs). Use it the
  library's way (owner's rule, do not add layers): one `interface AppLocalization :
  LocalizationProvider` with every string (`core/i18n/data_provider/AppLocalization.kt`), one
  `object XxxProvider : AppLocalization` per language in the same folder (15: en ar fr de es pt it
  ru tr id zh ja ko hi fa), text read as `LocalizationManager.Strings.x`, and
  `LocalizationManager.start()` (`core/i18n/start/LocalizationStart.kt`) called at every entry:
  `MainActivity.onCreate`, desktop `main`, iOS `MainViewController`, and the two Android services
  (START_STICKY can restart them without the activity; `start()` runs once). The library is not
  observable, so `AppLanguage` (same file) keeps the saved choice (`language=` in
  settings.properties, "system" = device language) and a StateFlow; `App.kt` and the desktop tray
  re-compose on it (`key`) and set RTL for ar/fa. Settings > General > Language. A new string =
  add it to `AppLocalization`, then the build fails until every provider has it. Engine step /
  status texts (`currentStepText`, `speedText`, `postProcessLabel`, torrent states, `ytDlpStatus`)
  stay English in the engines and in tasks_cache.json (code compares them) and are translated only
  where shown, by `localizeStep()` (`core/i18n/start/StepText.kt`, ~55 texts, `step*` members);
  an unknown text passes through unchanged. Sizes and speeds from `utils/Formatters.kt` are wrapped
  in `ltr()` (Unicode isolate) so "6.41 MB/s" does not flip in Arabic. Still English: free-form
  error tails / exception messages, units, brand names. In Kotlin, `"$n个"` / `"$nرابط"` does not
  compile (letters continue the name): use
  `${n}`. Verified on the A56: Arabic + RTL (home, settings, a live download, toast and
  notification in Arabic), choice kept after a restart; Chinese on screen (back to LTR). Not
  checked: the other 13 languages on screen, desktop. iOS build (CI "Build unsigned IPA") passed
  2026-11-02 with these changes.
- 1.2.0 media fixes (2026-11-02, A56):
    - 9gag.com, kick.com, mixcloud.com detected as Media (`DetectLinkTypeUseCase`, clipboard
      `videoHosts`); soundcloud.com already was. The extension's `VIDEO_PAGE` list was not changed.
    - `MediaToolsLock` (`MediaExtractorToolManager.kt`, shared with the view model's tool updates):
      inspection and media downloads wait while yt-dlp is being updated (a SoundCloud inspect right
      after a fresh install failed during the update).
    - A single HLS format (SoundCloud audio, `protocol=m3u8_native`) now goes to FetchJet's own
      `HlsDownloader` (`DirectMediaResolver` returns `hlsUrl`, `downloadWithOwnEngine` hands it
      over)
      instead of yt-dlp's one-segment-at-a-time download. HLS default parallel 6 -> 16 (PC test on
      SoundCloud segments: 6 = 2.7 MB/s, 16 = 5.0, 24 = 4.0). A56: 62 MB / 510 segments in 29 s (8 s
      of it resolving); before, minutes. YouTube still tries https formats first.
    - Pasted / shared text with exactly one link ("Listen to ... on #SoundCloud
      https://...") keeps
      only the link (`OnUrlChanged`), only when the text grew by more than one character at once, so
      typing is not touched. Owner-verified on the A56: SoundCloud Share -> Copy -> Paste leaves
      only the link.
    - `/links` skips video pages on purpose (they need the inspector); test media with `/add`.
- 1.2.0 file split (pure move, same package, `private` -> `internal` where another file needs
  it; owner's request, 2026-11-02):
    - `HttpChunkDownloader.kt` 1316 -> 254 (class API only) + `HttpChunkLayout.kt`,
      `HttpChunkSpeedometer.kt`, `HttpChunkModels.kt`, `HttpChunkWorkers.kt` (download loop, piece
      workers, single stream), `HttpChunkUrls.kt` (metadata probe, redirects),
      `HttpChunkHeaders.kt`,
      `HttpChunkRetry.kt`, `HttpChunkFileNames.kt`, `HttpChunkFiles.kt`.
    - `MediaStreamExtractorPlatform.jvm.kt` 1530 -> 107 + `MediaInspect.jvm.kt`,
      `MediaYtDlpDownload.jvm.kt`, `MediaThrottlingProxy.jvm.kt`, `MediaToolPaths.jvm.kt`.
    - `….android.kt` 1025 -> 114 + `MediaInspect.android.kt`, `MediaDownload.android.kt`.
    - `….ios.kt` 1270 -> 93 + `MediaInspect.ios.kt`, `MediaYtDlpDownload.ios.kt`,
      `MediaYtDlpRun.ios.kt` (iOS not compiled on Windows: needs a Mac build).
    - One real fix in the move: `resolveUrl` (relative redirects) gave `https://a.com/a.com/dir/x`
      for `Location: x` and broke `//host/path`; now correct.
    - Desktop + Android build; A56: 64 MB direct download OK after the split.
  - Mac check 2026-11-02 (after the split): iOS builds (Gradle + Xcode) and on the simulator:
    YouTube 720p WEBM merged by ffmpeg (VP9 + Opus, 3:33), SoundCloud `forss/flickermood`
    (full 3:33 m4a, ~8 s), 9gag, YouTube 240p. The SoundCloud log line `ERROR: [soundcloud] ...
      Requested format is not available` is expected and harmless: `DirectMediaResolver.resolve`
    gets an HLS format, then asks for a plain https one (`bestaudio[protocol^=http]`); SoundCloud
    has none, so yt-dlp says so and the HLS link goes to FetchJet's own `HlsDownloader`
    (common code, so the same line can appear on every platform).
  - iOS gives the app a new data container on reinstall, and the saved download folder
    (`destinationPath` in settings.properties and in each task) kept the old absolute path: on
    the simulator files were written into the old container (a real iPhone would refuse).
    `relocateAppPath` (`core/filesystem/AppContainerPath.kt`, expect/actual: iOS rebases
    `.../Containers/Data/Application/<UUID>` onto `NSHomeDirectory()`, JVM/Android return the
    path) is applied when settings and tasks are loaded. Verified on the simulator: after two
    reinstalls the Add dialog shows the current container and the file lands there. Counted as
    tested (owner's decision): no real iPhone; revisit only if a user reports a save problem.
  - Direct downloads failed with "failed to delete <file>" when a file with the same name was
    made by an earlier install of the app (Android scoped storage: owned by the old uid).
    `copyIntoPlace` (`HttpChunkFiles.kt`) now saves as `name (1).ext` then, like the media
    path's `moveIntoPlace`, and the task's `fileName` follows. Verified on the A56: with the old
    `1MB.bin` / `2MB.bin` in Download, both completed as `1MB (1).bin` / `2MB (1).bin`.
  - Android import `.txt` verified on the A56: the system picker returned the file and its 3
    links (2 files + 1 YouTube) were listed and started. Desktop import (macOS file dialog) also
    works (owner picked the file). iOS import verified on the simulator: the document picker
    shows only .txt files as selectable; a file in the app's Documents (visible in Files under
    FetchJet) filled the list with its 2 links. The automation tools may not drive the macOS Open
    panel.
  - Link list videos: title + quality picker per row, Download disabled while any checked video
    or torrent is read and while a checked video failed (Retry button, never a guessed best).
    See ROADMAP-1.2.0.md item 2. A56-tested. Desktop inspect timeouts raised (slow network:
    yt-dlp `-j` took 25-37 s and was killed at 25 s): `-j` waits 90 s, list / single-link limit
    120 s. `runYtDlp` now logs `yt-dlp timed out after N s: -j` and (verbose) the run time.
    Verified through the bridge `/inspect` (36.8 s, 15 qualities).
  - Arabic on the Mac desktop: RTL home, Add dialog and link list OK. Test trick: write
    `language=ar` into `~/.fetch_jet/settings.properties` and restart (remove it after).
    `gradlew :desktopApp:run` rebuilds the jars under a running dev app: quit the old window
    first, or clicks hit `ClassNotFoundException` (seen with `DashboardUiEvent$OnTabSelected`).
    - Follow-ups done: unused iOS `resolveEffectiveMediaUrl` / `cleanEscapedUrl` / user agent
      removed; size/speed units of yt-dlp progress lines now go through `ytDlpUnitMultiplier` /
      `ytDlpUnitPrefixMultiplier` (`MediaUnits.kt`, same results as the old copies); Android
      `contentLengthCache` is a ConcurrentHashMap cleared above 500 entries; extension `VIDEO_PAGE`
      has 9gag `/gag/` and kick `/videos|clips/` pages, extension version 3.2.0 (not uploaded; 3.1.1
      was never released).
      SoundCloud track / set pages are in `VIDEO_PAGE` too; they have no `<video>`, so
      `audioHero()` (content.js, `AUDIO_HERO` list) gives the track header as the element the
      page button sits on: `.fullListenHero` in the classic layout (logged out), or the
      `<section>` around `main h1` in the new MUI layout (logged in, hashed `mui-*` classes).
      The header never "plays", so its button shows at once (`place()`: any non-`VIDEO`
      anchor) and a click asks the app (`lookUp`); waiting for pushed items failed in the
      owner's Edge (no button, hero found). Checked on a local copy of each layout (button, click,
      list). In the new layout the track is a same-site frame
      (`soundcloud.com/n/<artist>/<track>?embedded=crossfade`), so the hero is looked up in
      frames too and `audioPageUrl()` sends the clean `soundcloud.com/<artist>/<track>` link.
      Owner-verified in Edge, logged in (new layout) and logged out (classic layout).
      `/discover/sets/...` (personal "Related
      tracks", private) gets no button: yt-dlp answers 404 there (also through the app with the
      extension's cookies; these are system playlists). Instead "Download all links on this
      page" on SoundCloud also collects track page links (`pushTrack` in `collectLinks`: two
      path parts, not `sets`, `/n/` and `?in=` removed) so the app's link list reads each
      track. Checked on the real `forss/sets/soulhack` DOM: its 11 tracks; `tags/`,
      `getstarted/`, `company/` ... are excluded in `VIDEO_PAGE`.
      The app used to skip every media page of a batch (`/links`, "Download all links"):
      `handleExternalBatch` now queues the files and opens the Add dialog's link list with the
      media pages (each read for title + quality). Owner-verified on the "Related tracks" page
      (Edge, 2026-11-03). SoundCloud renders only part of a long list: scroll down first.
      Audio vs video: `MediaQualityEntry.isAudioOnly` / `List.isAllAudio()` (all qualities are
      sound only). The link list tags such rows "AUDIO" (`linkTagAudio`), and the Add dialog's
      🎵 / "audio formats" title uses it too (it used `any`, so a YouTube video with an "Audio
      Only" option showed as audio). Test `MediaQualityAudioTest`.
      A56 after all of it: 64 MB direct, YouTube `jNQXAC9IVRw` (own engine, https streams),
      SoundCloud (own HLS) all completed.
- 1.2.0 text fields: every field in the app is `FetchJetTextField` (`ui/FetchJetTextField.kt`, on
  `BasicTextField`; String and `TextFieldValue` overloads). Focus = primary border + soft glow
  (`dropShadow`), hover on desktop, optional `label`, `error`, `centered`. Buttons inside a field:
  `FieldIconButton` (✕ ↺ 📂), `FieldChipButton` (Paste). Do not add new `OutlinedTextField`s; use
  this. `Modifier.clearFocusOnTapOutside()` (same file) drops focus and hides the keyboard on a
  tap outside the focused field; it is on the `App.kt` root `Surface`, the Add dialog `Card` and
  the `DirectoryPickerDialog` `Surface`. A new `Dialog` with a field needs it on its root too (a
  dialog is its own window). Verified on the A56 (look + keyboard closes); desktop and iOS not
  checked yet.
- 1.2.0 "When downloads finish" (desktop, ROADMAP item 4): `core/power/WhenDone.kt` +
  `SystemPower` (expect/actual, jvm only) + `ui/WhenDoneUi.kt` (Settings > General card, banner
  above the list, countdown dialog) + tray submenu in `main.kt`. Per session, not saved. Unit test
  `WhenDoneTest`. Owner-tested on Windows with Exit; sleep / shut down not run on purpose; macOS
  shut down via System Events may need Automation
  permission (Mac test).
- 1.2.0 keyboard selection in the task list (ROADMAP item 5): Ctrl/Cmd+click, Shift+click range,
  Ctrl/Cmd+A, Delete, Esc (`TaskList.kt` `onKeyEvent` on the focusable list column, `TaskRow`
  `onCtrlClick` / `onShiftClick` via `LocalWindowInfo.keyboardModifiers`). In commonMain on
  purpose (owner: a keyboard on a phone works too). Owner-tested on Windows.
- 1.2.0 network settings (ROADMAP item 6): Settings > Network (`ui/NetworkSettingsUi.kt`),
  `core/network/NetworkSettings.kt` (`network.json` in the settings folder, password in plain
  text). Every app HTTP client is built in a `NetworkClient { ... useNetworkSettings() }` (rebuilt
  after a change: applies to downloads started later). `useNetworkSettings` sets the ktor proxy,
  sends `Authorization: Basic` to saved sites (and sub-domains) and `Proxy-Authorization` for
  plain http; `applyProxyLogin` (expect/actual) answers the HTTPS tunnel login: OkHttp
  `proxyAuthenticator`, JDK `authenticator` + `jdk.http.auth.tunneling.disabledSchemes=""`,
  SOCKS5 login via the default `java.net.Authenticator` (`JvmProxyLogin`). The JDK engine has no
  SOCKS, so the desktop stream client uses OkHttp while a SOCKS5 proxy is on. The JVM default
  ktor engine is OkHttp (checked). iOS Darwin: ktor sends only http:// through an HTTP proxy, so
  the proxy dictionary (HTTPS keys, SOCKS user) is set by hand. yt-dlp gets `--proxy` (desktop
  `ytDlpRuntimeArgs`, Android `RuntimeManager.buildYTDLCommand`, iOS every bridge / popen call);
  libtorrent4j `proxy_type` etc. (`Libtorrent4jBackend.applyProxy`, live; with an HTTP proxy
  DHT / LSD / uTP are turned off, they would go around it). Every `HttpURLConnection` (web seeds,
  size probes, tool downloads, yt-dlp updater, native loader) opens through `openAppConnection`
  (`jvmAndroidMain/.../core/network/ProxiedConnection.kt`); web seeds use the ktor client
  (`streamUrlWithClient`, common) while a proxy is on, because Android's `HttpURLConnection`
  did not answer the proxy's 407. A new network call = `NetworkClient` / `useNetworkSettings`
  or `openAppConnection`, never a bare client. Not on iOS torrents (FJTorrent would need a new
  method + xcframework rebuild). Test proxy: a local Python script (HTTP CONNECT + SOCKS5 with
  login, basic-auth site) was used on Windows; see ROADMAP item 6.
- **Mac to-do for item 6 (network), not built on a Mac yet (2026-11-03).** Windows and the A56
  are verified (see ROADMAP item 6). iOS files changed, never compiled:
  `iosMain/.../core/network/NetworkSettings.ios.kt` (new: Darwin proxy dictionary with HTTPS
  keys and SOCKS user/password, `handleChallenge` for the HTTP proxy login),
  `StreamHttpClient.ios.kt`, `IosHybridHttpEngine.kt` (proxy on -> foreground engine, the
  background NSURLSession ignores it), `MediaInspect.ios.kt` (size probes),
  `MediaStreamExtractorPlatform.ios.kt` / `MediaYtDlpDownload.ios.kt` / `MediaYtDlpRun.ios.kt`
  (`NetworkSettings.ytDlpArgs()` before every yt-dlp call), `TorrentStreamer.ios.kt` (now calls
  the common `streamUrlWithClient`). Steps on the Mac:
    1. `./gradlew :sharedLogic:compileKotlinIosSimulatorArm64`, fix compile errors (likely spots:
       `handleChallenge` lambda types, `NSURLCredential.credentialWithUser`, `isProxy()`), then
       the Xcode build and the simulator.
    2. Test proxy: `python3 scripts/network-test/test_proxies.py /tmp/proxies.log` on the Mac (HTTP
       proxy `127.0.0.1:18080`, SOCKS5 `127.0.0.1:11080`, both user `fj` password `secret`;
       a basic-auth site `http://127.0.0.1:18090/x.bin` user `site` password `pw`; every request
       is written to the log). The simulator's 127.0.0.1 is the Mac itself.
    3. In the app, Settings > Network: HTTP proxy with the login, then download
       `https://media.githubusercontent.com/media/inventer-dev/speed-test-files/main/8MB.bin`
       and a YouTube video (`jNQXAC9IVRw`); the log must show `HTTP-PROXY OK CONNECT
     media.githubusercontent.com`, `www.youtube.com`, `googlevideo.com`. Then SOCKS5 (log:
       `SOCKS OK ...`). A wrong password must fail. Proxy Off: nothing in the log.
    4. Site login: proxy Off, save `127.0.0.1` / `site` / `pw`, download the 18090 link (log:
       `SITE ... auth-ok`); without the login it fails with 401.
    5. Known gaps on iOS: torrents do not use the proxy (FJTorrent has no proxy setting; adding
       it = a new `FJTorrent` method + Swift bridge + rebuild the xcframework, owner's call).
       Web seeds (HTTP) of a torrent do use it.
    6. Update ROADMAP item 6 and this note with the result.
- Open (found 2026-11-03, not fixed): libtorrent on desktop / Android sends no tracker
  announce at all, even without a proxy (no tracker alert, nothing at a proxy; the Ubuntu 26.10
  tracker answers curl with peers). Torrents find peers through DHT only. Not looked into yet.
- `:androidApp:assembleRelease` failed in R8 (missing optional `zstd` / `xz` classes of
  commons-compress, used only by `ZipUtils` for zip). Fixed with `-dontwarn` in
  `androidApp/proguard-rules.pro`. The minified release APK was installed on the A56: GitHub
  download and YouTube inspect (17 s, 4K listed) work, no crash. Always build the release locally
  before a release: the debug build does not run R8.
- 2026-11-01: dead code and unused permissions removed on all platforms. Fixed: `DashboardUiEffect`
  messages now show (SnackbarHost in `App.kt`); desktop `inspectCached` caches only results with a
  real `formatId`; NPE when a link arrived during `DashboardViewModel.init` (fields moved above
  `init`).
- Android inspect on Samsung A56 (measured from logs, 2026-11-01): 30-40 s at the start of the
  day ->
  19-23 s after the size-probe fixes -> **8.7-10.4 s** with the EJS player cache (QuickJS 10-14 s ->
  ~1.3 s). The first inspect after YouTube ships a new player still pays the full ~20 s once.
- Remaining Android inspect time: Python start ~2-3 s, webpage/player API ~2 s, QuickJS ~1.3 s,
  one size probe ~1 s.
- Verified on Windows: YouTube own-engine download and ffmpeg merge at 46–53 Mbps with 8 pieces.
  Owner tested a direct Ubuntu ISO download and it worked.
- Verified on Android (log): YouTube 240p https fallback at 6–8 MB/s after yt-dlp returned m3u8 and
  the resolver rejected it.
- Facebook: share/fb.watch links are passed to yt-dlp unchanged. Logged-in cookies can hide
  qualities, so the inspection retries without cookies.
- Duplicate protection: extension 8 s, app `/add` 5 s. A duplicate add resumes a paused task or
  scrolls to the existing item and brings the app forward.

## Open issues

1. **Android speed:** a side-by-side test (same video, same time) showed the same speed on phone
   and desktop. The earlier 4-5 vs 70-80 Mbps gap was not reproduced. Closed unless it comes back.
2. **Android inspect:** now ~9-10 s on the A56 (see Current state). The app also caches qualities
   in memory for 30 min per canonical URL (refresh / back shows them at once).
3. **Several downloads at once:** measured 2026-11-01, 3 files (128+64+32 MB) together: desktop
   ~90 Mbps total, Android A56 ~10.5 MB/s total. No problem found. Speed limit verified on the A56
   while downloading: 1 MB/s → 1.0, 5 MB/s → 5.0, applied at once and shared between the running
   downloads. Not verified: switching back to Unlimited mid-download.
4. **Udemy:** works (owner confirmed, 2026-11-01). Closed.
5. **googlevideo 403 right after resolve:** happens for a few seconds. Probe retries and link
   renewal mitigate it. A later curl with the same URL returns 206.
6. **Browser extension stores: done in CI** (`build-extension` in `build.yml`). Firefox is signed
   on AMO with the `AMO_JWT_*` secrets: `stores` on = listed (Firefox Add-ons), off = unlisted and
   the `.xpi` is attached to the release. Edge: submitted with the `EDGE_*` secrets; the owner
   publishes it on Microsoft's side by hand. Read `build.yml` before calling anything here "not done".
   6b. **Edge for Android (2026-11-02):** the store extension works on Edge Canary for Android
   (owner-tested, installed by id). Owner submitted Microsoft's Edge mobile extension request form
   with a value statement and test cases: `docs/edge-android-test-cases.docx` (generated by a
   docx-js script, 7 cases), linked through the Office viewer from the raw `master` URL, so keep
   that file name and path. Waiting for Microsoft. Edge iOS: not tested. Samsung Internet:
   extensions are a closed partner program (Galaxy Store), not applied.
7. **Android manifest:** unused permissions (`READ_MEDIA_*`, `ACCESS_NETWORK_STATE`) removed on
   2026-11-01 with the dead-code cleanup.
8. **Licensing:** GPL-3.0 `LICENSE` file exists in the root. iOS torrent no longer uses
   LibTorrent-Swift (XITRIX, no LICENSE upstream): replaced on 2026-11-01 by our own wrapper
   `iosApp/FJTorrent` (Objective-C++, ~400 lines) over libtorrent-rasterbar (BSD, arvidn, pinned
   commit
   in `scripts/build-libtorrent-ios.command`), shipped as static `FJTorrent.xcframework`.
   Simulator-tested: magnet metadata, .torrent file, file selection, pause (disk stops) / resume
   (continues from the same %), download to completion at 4-7 MB/s. Owner tested torrent on a real
   iPhone: works (2026-11-01).

## Release plan

- **1.2.0:** branch `release-v1.2.0` (from `master`, 2026-11-02). Ordered work list:
  `ROADMAP-1.2.0.md`. When the owner asks what is next, take the first unchecked item there.

- 1.1.0 history: a
  `release-v1.2.0` branch existed for one night (2026-11-01); the owner then decided to ship all of
  its work (clean code, kotlinx.serialization, multi-select delete) in 1.1.0 itself, since 1.1.0
  never reached users. The branch was deleted (local only, never pushed). On 2026-11-02
  `release-v1.1.0` was merged into `master` (PR #3, `0b6035e`) and the local branch deleted
  (`origin/release-v1.1.0` still exists). Work from `master` now. Not built for iOS yet, but low
  risk: every change is in `commonMain` with
  official multiplatform libraries (kotlinx.serialization, navigationevent, Compose), and
  `iosMain` / Swift were not touched. The next Mac build confirms it; it does not block the
  commit (owner's decision, 2026-11-02).
- **1.1.0 released 2026-11-02** (run #48 from `master`, all platforms, `stores` on, `beta` empty),
  published as a normal release. Publishing created the `v1.1.0` tag, and the tag push started
  run #49, which rebuilt the published release (`--clobber` deletes each file before uploading
  it again, so files were missing for a while). `build.yml` now skips a tag push when the
  release already exists. Uploads in all build jobs run in parallel (one `gh release upload`
  per file). Microsoft Store: MSIX 1.1.0 submitted (replaces 1.0.1; `runFullTrust` warning is
  normal, justified in Submission options). Edge Add-ons: new extension version submitted with
  certification notes (tester needs the FetchJet desktop app). Both under review.
- Stability (2026-11-01): all platforms are stable: Windows, macOS, Linux, Android, iOS and the
  Chrome / Edge / Firefox / Safari extension. Android re-tested on the A56 over wireless adb after
  the dead-code cleanup (direct 10 MB, YouTube with merged video+audio, Sintel torrent 123 MB, no
  crash in logcat). iOS is no longer Beta (owner's decision): the owner tested the app (video
  download, torrent) and the Safari extension on a real iPhone, all work. Udemy logged in is not
  tested there, but the owner says it does not justify Beta. So the release runs with `beta` empty.
- `beta` input: comma list of beta platforms (`ios`, `ios,linux`, `all`; empty = all stable). It
  writes `<!-- fetchjet-beta: ... -->` into the notes; the downloads page (`docs/index.html`) marks
  those platforms. Never make the release a GitHub "pre-release": the app reads `/releases/latest`,
  which skips pre-releases, so users would not get the update. The page shows the badge only after
  `docs/` reaches the branch GitHub Pages builds from.
- Do not run it yourself.
- **macOS = Apple Silicon only** (one `FetchJet-x.y.z-arm64.dmg`; the job fails if the DMG is not
  arm64). Intel was dropped on 2026-11-01 (owner's decision, no Intel Mac to test on):
  libtorrent4j-macos ships arm64 only since 2.1.0-32; a self-built x86_64 libtorrent4j worked in a
  standalone test, but the Intel app (under Rosetta) crashed silently 5-25 s into a torrent download
  (no hs_err; lldb blocked by the JDK's hardened runtime). Compose Desktop JVM itself still supports
  macos-x64. Do not bring Intel back without an Intel Mac to test on.

## iOS: verified on the Mac simulator (2026-11-01, iPhone 17, iOS 27)

All done and pushed (`a751617` + the Udemy preview fix):

1. Build OK (Kotlin iosMain + Swift); `updateYtDlpModule(progressCallback)` conforms.
2. `--plugin-dirs` in argv is ignored on iOS: Swift calls `parse_options` + `YoutubeDL(opts)`, not
   `_real_main`, so `plugin_dirs` stays `default`. The plugin folder still loads because for a
   zipimport yt-dlp `default` searches `<folder of the zip>/yt-dlp-plugins`, i.e. our folder.
   `CutPlugin` (own folder) would not load on iOS, but iOS does not need it: both clip modes
   tested OK (see 7).
3. EJS cache works: second YouTube inspect logs `Loading challenge-solver.player... from cache`.
4. Auto-update was broken (raced the engine warm-up in `init()`); `prepareEngine()` now waits.
   yt-dlp is no longer bundled: downloaded on first start, auto-updated, % shown in banner/button.
5. Media tools settings page shown on iOS (full edition).
6. Safari extension: see Backlog 7.
7. YouTube quality on iOS was always 360p (fixed 2026-11-01, `MediaStreamExtractorPlatform.ios.kt`):
   ffmpeg runs in-process so `getExistingFfmpegBinary()` is null, and the code then asked for a
   single progressive format (`401/best` -> 18); plus a forced `youtube:player_client=android,web`
   (removed on desktop long ago) got SABR-only formats. Now `hasFfmpeg` = binary OR bridge ready,
   no forced client. Verified: 4K clip "Only the clip" = 3840x2160, 33 s, with audio; "Full video,
   then cut" = exact 11 s (cut by the app's own ffmpeg step, not CutPlugin).

Udemy works on the simulator (owner-tested 2026-11-01). Safari extension (appex bundle id
`<app>.SafariExtension`) sideloaded and working on the owner's real iPhone (2026-11-01). Video
download and torrent also work there. Udemy logged in on a real iPhone: counted as done (owner's
decision, do not ask again). No App
Store release is
planned (owner's decision: a YouTube/media downloader is rejected there), iOS ships sideloaded only.

## Backlog (from CLAUDE_HANDOFF.md, adjusted)

1. iOS: Python 3.13 media engine: done (simulator + owner's real iPhone, 2026-11-01).
2. iOS: torrent flow end to end: done (FJTorrent, simulator + owner's real iPhone, 2026-11-01).
3. `MediaStreamDownloader`: remove Invidious/cobalt resolvers. Stream bodies with
   `prepareGet().execute` instead of `client.get`.
4. Clean-code plan (in 1.1.0) (owner's request): kotlinx.serialization done (step 1); `App.kt` split
   from 2637 to ~700 lines (step 2, pure move, same package, `private` → `internal`). Step 3: Add
   dialog 1884 → ~360 lines (sections get `MutableState` for the dialog-local fields and
   re-delegate them, so their bodies are unchanged) and the settings pages split (step 3). Tested on
   the A56: dialog fields, folder buttons, engine switch, start. Step 4 (done): `DashboardViewModel`
   1949 → ~745 lines: `onEvent` dispatches to `handleAddDialogEvent` / `handleTaskEvent` /
   `handleSettingsEvent` (files `DashboardAddDialogEvents.kt`, `DashboardTaskEvents.kt`,
   `DashboardSettingsEvents.kt`), plus `DashboardAddDownload.kt`, `DashboardTools.kt`,
   `DashboardFilters.kt`. `MediaStreamDownloader` 2619 → ~1200: `MediaYouTubeResolvers.kt`,
   `MediaPageScraping.kt`, `MediaOwnEngine.kt`. Both use extension functions in the same package, so
   class members went from `private` to `internal`. When adding a new `DashboardUiEvent`, add it to
   the dispatcher in `onEvent` and to the matching handler (the dispatcher `when` is exhaustive; a
   handler's `else -> Unit` would silently drop an event that is dispatched but not handled). Tested
   on the A56: YouTube 4K (video+audio merged, 243 MB), small YouTube, direct downloads, release
   build. Step 5 (done): `executeMediaDownload` 515 → ~210 lines (stages in `MediaDownloadSteps.kt`:
   `resolveMediaFileName`, `downloadWithOwnHls`, `downloadWithYtDlp`, `findFinishedFileInCache`,
   `moveFinishedFileIntoPlace`, `downloadWithHttpFallback`) and `inspectMediaQualities` 380 → ~85
   (`MediaInspectSteps.kt`: `inspectHlsVariants`, `inspectPlatformWithCookieRetry`,
   `withTitleAndDuration`, `inspectYouTubeFallbacks` (Piped/Cobalt/embed, still the old public
   APIs), `inspectWebPage`). `MediaStreamDownloader.kt` is now ~600 lines. Tested on the A56: HLS
   (mux test stream, 184p + 1080p via multi-quality), YouTube. String resources: not done on
   purpose (~350 strings in ~20 files); worth it only when a second language (Arabic) is added.
   `moveIntoPlace` now returns the saved path: when the old file cannot be deleted (Android: a file
   made by an earlier install of the app is not ours) it saves as `name (1).ext` instead of failing
   with "failed to delete".
5. Playlist queue in the extension (`/playlist`), not ported from Ezoo yet.
6. Merge captured video-only + audio-only pairs inside the app.
7. iOS Safari extension: done (2026-11-01, simulator-tested). Target `FetchJetSafariExtension`
   copies the same `extension/` JS at build time (`manifest.safari.json` -> manifest.json). On iOS
   (`ON_IOS` in background.js) every send goes through `fetchjet://add?json=...`
   (`IosFetchJetLinks.kt`); media is found via the Performance API in content.js (no webRequest).
   Udemy works on the simulator (2026-11-01). The owner tested the extension on a real iPhone: it
   works (2026-11-01).
   Qualities are not listed inside Safari on iOS: iOS suspends the app while Safari is in front
   (no local server), so the page button shows "Open in FetchJet" and the app lists them.
   Probe tried on 2026-11-01: running the app's Python + yt-dlp inside the extension's native
   handler (dlopen of the app's `Python.framework`, `sys.executable` set to the app binary so the
   `.fwork` modules load) worked on the simulator: 49-61 YouTube formats up to 4K in 12-13 s,
   but a 165 MB memory peak. The simulator does not enforce the extension memory limit, so it may
   be killed on a real iPhone. **The owner decided to keep "Open in FetchJet"**: the probe code
   was removed. Do not bring it back unless the owner asks.

## Working style with the owner

- Show the diff or the changed files, then wait for the test result.
- When a fix is "by chance", say so. Do not claim it works until the owner confirms.
- Use the owner's own terms: "fetch jet", "الاكستنشن", "الاندرويد", "الفيديو".
