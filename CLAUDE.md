# FetchJet: project memory for Claude Code

Last updated: 2026-11-01 (night). Read this first, then `README.md` and `CLAUDE_HANDOFF.md` (the
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

| Path                          | What                                                                                                                                                          |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sharedLogic/`                | All engines and logic. `commonMain`, `jvmMain`, `jvmAndroidMain`, `androidMain`, `iosMain`.                                                                   |
| `sharedUI/`                   | Compose UI shared by desktop, Android, iOS. Key files: `App.kt`, `PiecesBar.kt`, `AddDownloadModalDialog.kt`, `FdmSettingsPanel.kt`, `PlaylistScheduleUi.kt`. |
| `desktopApp/`                 | JVM app. `main.kt` has the local bridge (`inspectCached`, `inspectGates`, `recentAdds`).                                                                      |
| `androidApp/`                 | Android app. `ExtensionBridgeServer.kt`, `MainActivity.kt`, `AndroidManifest.xml`.                                                                            |
| `iosApp/`                     | Xcode project (hand-edited, ids `B2…`/`B3…`). Swift bridges: `NativeMediaExtractorBridge.swift`, `NativeTorrentBridge.swift` (on `iosApp/FJTorrent`).         |
| `extension/`                  | Browser extension. `background.js`, `content.js`, `manifest.json` (Chromium), `manifest.firefox.json`. Build: `build.ps1` (Windows) / `build.sh`.             |
| `.github/workflows/build.yml` | CI. `workflow_dispatch` inputs: `release_version`, `android`, `windows`, `macos`, `linux`, `ios`, `extension`, `stores` (default true).                       |
| `docs/`                       | GitHub Pages download page.                                                                                                                                   |
| `CLAUDE_HANDOFF.md`           | iOS engine details (Python 3.13 + yt-dlp, curl_cffi). Its torrent part is outdated: see Open issue 8.                                                         |

Version constants:

- `sharedLogic/.../core/update/AppVersion.kt` → `APP_VERSION = "1.1.0"`
- `iosApp/Configuration/Config.xcconfig` → `MARKETING_VERSION = 1.1.0`,
  `CURRENT_PROJECT_VERSION = 3`
- `extension/manifest.json` and `manifest.firefox.json` → `3.1.0`

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
- iOS dev loop: `scripts/ios-dev-build.command [gradle|xcode|all]` → `build/ios-dev-build.log`.

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
- `feature/queue_manager/QueueManagerRepository.kt`: persistence in `tasks.json` written by hand
  (`escapeJson`, `extractJsonField`, `splitJsonObjects`). Serialization migration
  (kotlinx.serialization, `MiniJson`) is **deferred to 1.2.0**. Do not start it unless asked.
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
- Android: python/ffmpeg/QuickJS/aria2c are bundled as `.so` (Android 10+ cannot run downloaded
  binaries, so they can only change with a new APK). yt-dlp (a Python zip, data not a binary) ships
  in
  `res/raw/ytdlp` (copied there by CI) and is auto-updated at start; the owner chose to keep it
  bundled. JS runtime = QuickJS. deno for
  Android exists only as an unofficial ~100 MB package: not used. `RuntimeManager` passes
  `--cache-dir filesDir/yt-dlp-cache` (it used to pass `--no-cache-dir`) and
  `--plugin-dirs filesDir/fetchjet-ejs-cache-plugin`.
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

## Current state (release-v1.1.0)

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
  `codeHostRawUrl` in `enqueueTask`: `github.com/o/r/blob/...` → `/raw/...` (and GitLab `/-/blob/`);
  blob links are HTML pages and failed as "Protected by Cloudflare". Verified desktop + A56. The 429
  cap also verified on the A56 (Hetzner 100 MB in ~20 s, no slow tail).
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
7. **Android manifest:** unused permissions (`READ_MEDIA_*`, `ACCESS_NETWORK_STATE`) removed on
   2026-11-01 with the dead-code cleanup.
8. **Licensing:** GPL-3.0 `LICENSE` file exists in the root. iOS torrent no longer uses
   LibTorrent-Swift (XITRIX, no LICENSE upstream): replaced on 2026-11-01 by our own wrapper
   `iosApp/FJTorrent` (Objective-C++, ~400 lines) over libtorrent-rasterbar (BSD, arvidn, pinned
   commit
   in `scripts/build-libtorrent-ios.command`), shipped as static `FJTorrent.xcframework`.
   Simulator-tested: magnet metadata, .torrent file, file selection, pause (disk stops) / resume
   (continues from the same %), download to completion at 4-7 MB/s. Not tested: a real iPhone.

## Release plan (1.1.0)

- Draft release. The owner runs workflow_dispatch **from the `release-v1.1.0` branch**: version
  `1.1.0`, only the platforms they choose, `stores` as they decide. Not released yet (2026-11-01).
- Stability (2026-11-01): Windows, macOS, Linux, Android and the Chrome / Edge / Firefox extension
  are stable. Android re-tested on the A56 over wireless adb after the dead-code cleanup
  (direct 10 MB, YouTube with merged video+audio, Sintel torrent 123 MB, no crash in logcat). iOS =
  Beta (simulator only, never run on a real iPhone; the owner cannot test one).
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

Udemy works on the simulator (owner-tested 2026-11-01). Still needs a real iPhone: sideload with
the extra Safari extension appex (bundle id `<app>.SafariExtension`). No App Store release is
planned (owner's decision: a YouTube/media downloader is rejected there), iOS ships sideloaded only.

## Backlog (from CLAUDE_HANDOFF.md, adjusted)

1. iOS: verify the Python 3.13 media engine on a real device or simulator.
2. iOS: torrent flow end to end: done on the simulator (FJTorrent), real iPhone pending.
3. `MediaStreamDownloader`: remove Invidious/cobalt resolvers. Stream bodies with
   `prepareGet().execute` instead of `client.get`.
4. `DashboardViewModel`: lifecycle ViewModel (partly done). Split `App.kt` and
   `AddDownloadModalDialog.kt`. String resources. `MiniJson` → kotlinx.serialization (1.2.0).
5. Playlist queue in the extension (`/playlist`), not ported from Ezoo yet.
6. Merge captured video-only + audio-only pairs inside the app.
7. iOS Safari extension: done (2026-11-01, simulator-tested). Target `FetchJetSafariExtension`
   copies the same `extension/` JS at build time (`manifest.safari.json` -> manifest.json). On iOS
   (`ON_IOS` in background.js) every send goes through `fetchjet://add?json=...`
   (`IosFetchJetLinks.kt`); media is found via the Performance API in content.js (no webRequest).
   Udemy works on the simulator (2026-11-01). Not tested yet: a real iPhone.

## Working style with the owner

- Show the diff or the changed files, then wait for the test result.
- When a fix is "by chance", say so. Do not claim it works until the owner confirms.
- Use the owner's own terms: "fetch jet", "الاكستنشن", "الاندرويد", "الفيديو".
