# FetchJet: project memory for Claude Code

Last updated: 2026-11-01. Read this first, then `README.md` and `CLAUDE_HANDOFF.md` (the handoff is
from 2026-10-28 and partly outdated).

## Owner rules (follow strictly)

- The owner is Michelle (Egyptian Arabic speaker). Reply in **Egyptian Arabic**, short and direct.
  No long explanations.
- **Never run `git commit`, `git push`, `git reset`, or change branches on your own.** Make the
  change, show what changed, and let the owner test. Commit only after the owner says it is OK.
- Commit message and PR attribution: follow the rule given by the session, not this file.
- Do not add code comments unless asked.
- Do not change code without the owner's go-ahead when they say "لا مش عتعدل" or similar.
- Do not trigger GitHub Actions releases on your own. The owner runs them.
- Do not upload the browser extension to a store unless the owner asks.
- When the owner says "اشتغل" or "يلا", do the task they just described. Ask at most one question.

## What the project is

FetchJet is a Kotlin Multiplatform / Compose Multiplatform download manager (IDM-style). Platforms:
Windows, macOS, Linux (JVM desktop), Android, iOS (sideloaded, full edition), and a Chromium/Firefox
browser extension (MV3).

Features: multi-connection HTTP piece downloads, video downloads through yt-dlp (with FetchJet's own
engine doing the piece fetch and ffmpeg merging), HLS, torrents via libtorrent4j (desktop/Android)
and LibTorrent (iOS), queue, scheduling, clipboard monitoring, browser bridge on `127.0.0.1:28450`.

Competitors the owner compares against: IDM, EZOO (Ezoo Download), and the iOS app they mentioned.
The owner wants speed parity and smooth UX.

## Layout

| Path                          | What                                                                                                                                                          |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sharedLogic/`                | All engines and logic. `commonMain`, `jvmMain`, `jvmAndroidMain`, `androidMain`, `iosMain`.                                                                   |
| `sharedUI/`                   | Compose UI shared by desktop, Android, iOS. Key files: `App.kt`, `PiecesBar.kt`, `AddDownloadModalDialog.kt`, `FdmSettingsPanel.kt`, `PlaylistScheduleUi.kt`. |
| `desktopApp/`                 | JVM app. `main.kt` has the local bridge (`inspectCached`, `inspectGates`, `recentAdds`).                                                                      |
| `androidApp/`                 | Android app. `ExtensionBridgeServer.kt`, `MainActivity.kt`, `AndroidManifest.xml`.                                                                            |
| `iosApp/`                     | Xcode project (hand-edited, ids `B2…`/`B3…`). Swift bridges: `NativeMediaExtractorBridge.swift`, `NativeTorrentBridge.swift`.                                 |
| `extension/`                  | Browser extension. `background.js`, `content.js`, `manifest.json` (Chromium), `manifest.firefox.json`. Build: `build.ps1` (Windows) / `build.sh`.             |
| `.github/workflows/build.yml` | CI. `workflow_dispatch` inputs: `release_version`, `android`, `windows`, `macos`, `linux`, `ios`, `extension`, `stores` (default true).                       |
| `docs/`                       | GitHub Pages download page.                                                                                                                                   |
| `CLAUDE_HANDOFF.md`           | iOS engine details (Python 3.13 + yt-dlp, curl_cffi, LibTorrent).                                                                                             |

Version constants:

- `sharedLogic/.../core/update/AppVersion.kt` → `APP_VERSION = "1.1.0"`
- `iosApp/Configuration/Config.xcconfig` → `MARKETING_VERSION = 1.1.0`,
  `CURRENT_PROJECT_VERSION = 3`
- `extension/manifest.json` and `manifest.firefox.json` → `3.1.0`

## Build and run (Windows dev machine, path `E:\tbib\FetchJet`)

```
.\gradlew.bat :desktopApp:run
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :sharedLogic:compileKotlinJvm
.\gradlew.bat :sharedLogic:compileKotlinIosSimulatorArm64    (or the iOS target you need)
extension\build.ps1
```

- Verbose engine logs: `fetchjet.verboseLogs=true` in `gradle.properties`, or env
  `FETCHJET_VERBOSE_LOGS`.
- Desktop log: `~/.fetch_jet/logs/app.log`.
- Android log: `adb logcat` (the owner had trouble with adb earlier; ask before assuming it works).
- iOS dev loop: `scripts/ios-dev-build.command [gradle|xcode|all]` → `build/ios-dev-build.log`.

## Key engine pieces (sharedLogic)

- `feature/http_downloader/HttpChunkDownloader.kt`: `ChunkLayout` (pieces, split-half of biggest
  running piece when a worker is free, `.partN` files, `.chunks` manifest). `MAX_CONNECTIONS = 32`,
  `STALL_FAIL_MS = 180_000`. Min piece 512 KB. Connection count order: `task.maxConnections` →
  `BehaviorSettings.connections` → auto by size.
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
  (`stripTypeFolders`, `withTypeFolder`).

Bridges:

- Desktop: `ExternalDownloadBridge.onShowAndFocus`. Windows focus trick (Robot Alt tap +
  alwaysOnTop). Dedupe on `/add` for 5 s.
- Android: `ExtensionBridgeServer.deliver()` opens the activity, or posts notification "Link
  received" (channel `fetchjet_links`) when `SYSTEM_ALERT_WINDOW` is not granted. Settings row "Pop
  up over the browser".
- Extension: `background.js` has `downloadItem`, `inspectWithApp`, `sendToFetchJet`,
  `isStreamFragment`, `recentSends` (8 s dedupe), quality labels via regex.

## Current state (release-v1.1.0)

- HEAD (per reflog): `cb1071c` "Restrict diagnostics logging to debug builds and lower internal log
  levels". **Check `git status` before assuming anything.** The owner may have committed or changed
  files since.
- The batch "diagnostics debug-only, AppLog.d conversion, MainActivity gate,
  `DiagnosticsLog.canExport`" was pending review in the last session. HEAD's message for `cb1071c`
  matches it, so it is probably committed. Confirm with `git status` and `git log -1`.
- Verified on Windows: YouTube own-engine download and ffmpeg merge at 46–53 Mbps with 8 pieces.
  Owner tested a direct Ubuntu ISO download and it worked.
- Verified on Android (log): YouTube 240p https fallback at 6–8 MB/s after yt-dlp returned m3u8 and
  the resolver rejected it.
- Facebook: share/fb.watch links are passed to yt-dlp unchanged. Logged-in cookies can hide
  qualities, so the inspection retries without cookies.
- Duplicate protection: extension 8 s, app `/add` 5 s. A duplicate add resumes a paused task or
  scrolls to the existing item and brings the app forward.

## Open issues

1. **Android speed:** the owner measured 4–5 Mbps on Android against 70–80 Mbps on Windows for the
   same content. Not solved. Earlier speculative analysis was wrong and rejected. **Do not guess the
   cause. Ask for a side-by-side measurement with the same video and the same time window.**
2. **Android slow inspect:** yt-dlp on the phone takes ~20–30 s per call. Mitigated by the canonical
   cache key and a per-video gate.
3. **Extension slowness on Android:** multiple downloads share bandwidth. Unclear if that is the
   cause.
4. **Udemy:** course page returns 403 from yt-dlp when cookies are sent. The HLS lecture path is
   unchanged.
5. **googlevideo 403 right after resolve:** happens for a few seconds. Probe retries and link
   renewal mitigate it. A later curl with the same URL returns 206.
6. **Firefox:** unlisted AMO signing is needed for the xpi. Not done. Mobile Firefox also needs a
   signed xpi.
7. **Android manifest:** `READ_MEDIA_AUDIO`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (or
   `READ_EXTERNAL_STORAGE`) are declared but not needed. Remove them only when the owner says so.
   Check that nothing else uses them.
8. **Licensing:** GPL-3.0 `LICENSE` file exists in the root. LibTorrent-Swift has no LICENSE
   upstream: confirm with the author before a public release.

## Release plan (1.1.0)

- Draft release. The owner runs workflow_dispatch: version `1.1.0`, only the platforms they choose,
  `stores` as they decide. Current plan: extension only, `stores` OFF, then other platforms added to
  the same draft.
- Do not run it yourself.

## Backlog (from CLAUDE_HANDOFF.md, adjusted)

1. iOS: verify the Python 3.13 media engine on a real device or simulator.
2. iOS: LibTorrent flow end to end.
3. `MediaStreamDownloader`: remove Invidious/cobalt resolvers. Stream bodies with
   `prepareGet().execute` instead of `client.get`.
4. `DashboardViewModel`: lifecycle ViewModel (partly done). Split `App.kt` and
   `AddDownloadModalDialog.kt`. String resources. `MiniJson` → kotlinx.serialization (1.2.0).
5. Playlist queue in the extension (`/playlist`), not ported from Ezoo yet.
6. Merge captured video-only + audio-only pairs inside the app.

## Working style with the owner

- Show the diff or the changed files, then wait for the test result.
- When a fix is "by chance", say so. Do not claim it works until the owner confirms.
- Use the owner's own terms: "fetch jet", "الاكستنشن", "الاندرويد", "الفيديو".
