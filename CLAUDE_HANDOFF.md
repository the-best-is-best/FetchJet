# FetchJet — handoff notes for the next Claude session (2026-10-28)

Kotlin Multiplatform download manager (Android, iOS, JVM desktop). Compose Multiplatform UI in
`sharedUI`, all logic in `sharedLogic`. Developer: Michelle Raouf. Distribution: desktop + Android
builds, iOS sideloaded with a developer certificate (NOT App Store) → everything is enabled in the
iOS **full** edition.

## iOS media engine (state on 2026-10-28, evening)

**Stack (same as Palladium):** embedded CPython 3.13 from BeeWare Python-Apple-support +
PythonKit, yt-dlp as the official zipimport release file, ffmpeg/ffprobe in-process via
kewlbear FFmpeg-iOS-Lame/FFmpeg-iOS-Support, YouTube JS challenges solved by the
`yt-dlp-apple-webkit-jsi` plugin. The old kewlbear YoutubeDL-iOS / Python-iOS packages were
REMOVED: they embed Python 3.10 and yt-dlp now requires >= 3.11.

1. `iosApp/Frameworks/Python.xcframework` (git-ignored, ~115 MB) comes from
   https://github.com/beeware/Python-Apple-support/releases -> `Python-3.13-iOS-support.bNN.tar.gz`
   (b14 / 3.13.14 was used). Extract so that `iosApp/Frameworks/Python.xcframework/build/utils.sh`
   exists. CI must download it too (`.github/workflows/build.yml` still needs this step).
2. Xcode project (`iosApp.xcodeproj`, edited by hand, ids `B2...`):
    - Python.xcframework linked + "Embed Frameworks" (CodeSignOnCopy);
      `FRAMEWORK_SEARCH_PATHS += $(PROJECT_DIR)/Frameworks`.
    - Run Script "Install Python Runtime" (after Resources, before Embed Frameworks): sources
      `Frameworks/Python.xcframework/build/utils.sh` and runs `install_python`, which copies the
      stdlib to `FetchJet.app/python/lib/python3.13` and turns each `.so` into a signed framework.
      Ad-hoc signs when no identity (simulator / unsigned IPA).
    - Packages: PythonKit (main), FFmpeg-iOS-Lame, FFmpeg-iOS-Support (module `FFmpegSupport`).
3. `iosApp/iosApp/NativeMediaExtractorBridge.swift` (rewritten):
    - `EmbeddedPython.boot()` sets PYTHONHOME/PYTHONPATH/SSL_CERT_FILE (`iosApp/iosApp/cacert.pem`,
      Mozilla roots - the embedded OpenSSL cannot see the iOS trust store) and starts PythonKit.
    - `subprocess.Popen` is replaced by an in-process ffmpeg/ffprobe shim (stdout/stderr captured).
    - yt_dlp module: `Application Support/FetchJet-media-engine/yt-dlp` (copied from the bundled
      `iosApp/iosApp/yt-dlp`, 2026.10.21, which already contains yt_dlp_ejs; "Download Tools"
      can refresh it from GitHub). Driver = parse_options -> YoutubeDL(opts) with logger +
      progress hook (ported from YoutubeDL-iOS, MIT).
    - All Python runs on one serial queue (PythonKit has no GIL management).
    - Every call gets `--plugin-dirs <AppSupport>/FetchJet-media-engine/yt-dlp-plugins
      --remote-components ejs:github --cache-dir <Caches>/yt-dlp`; the JS solver zip is downloaded
      on first launch into that plugin dir. `--js-runtimes`, `--ffmpeg-location`,
      `--external-downloader` are stripped (binary-only options).
    - `iosApp/iosApp/python-packages.zip` (on PYTHONPATH, zipimport): pure-Python companions that
      the desktop/Android yt-dlp builds bundle - requests 2.32.5, urllib3 2.5.0, certifi (where()
      returns $SSL_CERT_FILE), charset_normalizer 3.4.3, idna 3.10, websockets 15.0.1, mutagen
      1.47.0. Taken from the projects' GitHub tags (PyPI not reachable from the agent). Still
      missing vs desktop: brotli, pycryptodomex.
   - curl_cffi (browser impersonation, added 2026-10-28 late - first build pending): the PornHub
     extractor (and other Cloudflare-fronted sites) calls every page with `impersonate=True`;
     without curl_cffi yt-dlp only warns and then gets HTTP 403 / "Unable to download webpage",
     which is why that site worked on desktop (official yt-dlp builds bundle curl_cffi) but not
     on iOS. Fix = Palladium's prebuilt payload tfourj/SwiftCurlCffi-iOS:
     `scripts/ios-prepare-frameworks.sh` step 3 downloads release **v0.16.3** (pinned: the
     bundled yt-dlp 2026.10.21 rejects curl_cffi >= 0.17; `FETCHJET_CURL_CFFI_TAG`/`_URL`
     override) into git-ignored `iosApp/Frameworks/SwiftCurlCffi-iOS/`. "Install Python
     Runtime" unzips `curl_cffi_ios_payload.zip`, copies `site-packages-$PLATFORM_NAME` to
     `FetchJet.app/python-packages/` and passes it to `install_python` (the two .so modules
     become frameworks). `EmbeddedPython.boot()` puts that folder on PYTHONPATH (after
     python-packages.zip so the patched certifi wins); `statusText()` shows "+ curl_cffi X"
     or "(no impersonation...)". The payload is skipped with a warning when its
     `manifest.json` "python" does not match the embedded Python (3.13) - the release is built
     against whatever Python.xcframework Palladium uses; if it mismatches, either move the app
     to that Python (`FETCHJET_PYTHON_VERSION`/`_BUILD` in the prepare script) or build the
     payload locally: `git clone https://github.com/tfourj/SwiftCurlCffi-iOS
      iosApp/Frameworks/SwiftCurlCffi-iOS && PYTHON_XCFRAMEWORK=$PWD/iosApp/Frameworks/Python.xcframework
      iosApp/Frameworks/SwiftCurlCffi-iOS/scripts/build-all.sh --curl-cffi-version 0.16.3`
     (brew install cmake ninja; host python3.13). Not verified yet: a simulator run with the
     payload (import of `yt_dlp.networking._curlcffi`, the `.fwork` loading of
     `curl_cffi._wrapper`).
    - The bridge exposes `lastErrorText()`; iOS Inspect/-g throw yt-dlp's real error instead of
      returning null, and `MediaStreamDownloader` shows it (task error ends with "yt-dlp: ...").
4. Kotlin: `platformSupportsMediaExtractor()` (iOS) = "Swift bridge registered"; readiness is
   shown through `getMediaExtractorToolStatus()`; the Add dialog shows that status, has a
   "Download / Start media engine" button and polls every 2 s while not ready
   (`DashboardViewModel.pollToolStatusWhileNotReady`).
5. Signing / install without a paid account: bundle id is now `com.michelle.raouf.fetch-jet(.full)`
   (underscores are rejected by Apple's portal). Team `Z7W9947668` (free Personal Team). Run from
   Xcode on the Simulator (no account needed) or on a cable-connected iPhone; unsigned IPA from
   `scripts/build-ios-ipa.sh` + Sideloadly/AltStore for other devices. PlayCover is NOT a target
   (it runs iOS apps on macOS; this app needs iOS).
6. Dev loop: `scripts/ios-dev-build.command [gradle|xcode|all]` -> `build/ios-dev-build.log` and
   `build/ios-dev-build.errors.txt`.

Not yet verified at the time of writing: first simulator run of the new Python 3.13 stack
(possible follow-ups: PythonKit API nits in the Swift file, `install_python` signing on the
simulator, WebKit JSI plugin behaviour, Palladium's two small patches to that plugin).

## iOS BitTorrent engine (added 2026-10-28, evening - first build pending)

Same bridge pattern as media. Native side = libtorrent-rasterbar + the LibTorrent-Swift wrapper
(XITRIX, used by iTorrent; GPL-compatible), packaged as `iosApp/Frameworks/LibTorrent.xcframework`.

1. Build phase "Prepare Native Frameworks" (`scripts/ios-prepare-frameworks.sh`, first phase of the
   iosApp target) downloads Python.xcframework and builds LibTorrent.xcframework when they are
   missing, so a plain Xcode Run / CI works from a clean clone (needs `brew install cmake boost`).
   The libtorrent part can also be run by hand: double-click
   `scripts/build-libtorrent-ios.command` (log: `build/libtorrent-ios.log`, ends with
   `DONE_MARKER exit=N`). It clones LibTorrent-Swift into `iosApp/ThirdParty/` (git-ignored), runs
   its `make.sh` (cmake -G Xcode for libtorrent), archives the `LibTorrent` framework for simulator
   and device, and runs `xcodebuild -create-xcframework`. The xcframework is linked + embedded in
   `iosApp.xcodeproj` (ids `B3...`); the project does not build without it. CI needs this step too.
   LibTorrent.framework is dynamic and links `@rpath/OpenSSL.framework/OpenSSL`, so the app target
   also depends on the krzyzanowskim/OpenSSL package (SwiftPM embeds it) - without it the app
   aborts in dyld at launch. The build script compiles libtorrent itself per platform (the
   wrapper project has no dependency on the CMake target) and calls CMake directly (its make.sh
   uses macOS 10.13 / watchOS targets that Xcode 27 rejects).
2. Kotlin (iosMain/torrent_engine): `IosTorrentBridge.kt` (interface + Swift-friendly data classes,
   NSData for torrent bytes), `TorrentBackend.ios.kt` (`IosTorrentBackend` -> bridge, public
   trackers appended to bare magnets, path sanitizing). `platformSupportsTorrent()` = bridge
   registered.
3. Swift: `iosApp/iosApp/NativeTorrentBridge.swift` (`#if canImport(LibTorrent)`), registered in
   `iOSApp.swift` under `#if FETCHJET_FULL`. One session (DHT/LSD/uTP/UPnP/NAT-PMP, port 6881),
   one wrapper "storage" per save folder, magnet metadata via a scratch storage + the
   `<hash>.torrent` the wrapper writes, priorities 0/1/4/7, snapshot -> `IosTorrentProgress`.
   Limits: the wrapper has no per-torrent API, so the strictest per-task limit is applied
   session-wide; "disable upload" = 1 B/s global upload cap while such a task exists.
4. Known limits: foreground only (in-process libtorrent); no fast-resume persistence across
   launches yet (Kotlin re-adds tasks). LibTorrent-Swift has no LICENSE file in its repo
   (iTorrent itself is GPL-3.0) - confirm with the author before a public release.
5. CI: `.github/workflows/build.yml` job `build-ios-ipa` (macos-latest): latest Xcode,
   `brew install cmake boost`, `actions/cache` on `iosApp/Frameworks` keyed by the two scripts,
   `scripts/ios-prepare-frameworks.sh`, package resolution, `scripts/build-ios-ipa.sh full`
   (now also logs to `build/ios-ipa-full.log`); logs are uploaded as an artifact on failure.
   First run on an empty cache takes ~40 min (libtorrent); cached runs ~10 min.

## Verified on the iOS Simulator (2026-10-28, late)

- Media: YouTube Inspect + download (incl. clipping via in-process ffmpeg, live progress) work;
  `--no-playlist` added to the three iOS yt-dlp calls (a watch?v=...&list=... link used to make
  Inspect walk the whole mix). Age-gated/+18 content needs cookies.txt (as on every platform).
- Torrent: .torrent parsed and files listed; priorities are set on the TorrentFile before add
  (setting them on a fresh handle aborted); Kotlin->Swift params are plain lists (no Map).
- Live Activity: ended 4 s after completion / immediately on pause-remove, only when no tracked
  download remains; stale activities cleaned at launch (`LiveActivityManager.endStaleActivities`).
- Open Folder (iOS) = Files app via `shareddocuments://<resolved path>/`
  (`LSSupportsOpeningDocumentsInPlace` added). The simulator opens the "On My iPhone" root;
  expected to land in the folder on a real device - unverified.
- Speed limits on iOS: deliberate policy (see note in Settings + `IosHybridHttpEngine` doc):
  unlimited HTTP downloads use the background NSURLSession and cannot be re-throttled; decided
  to keep it.
- Completed-task action row: on widths < 520 dp a single row (Open File / Folder / trash).
- Not tested yet: real iPhone (signing of ~70 embedded frameworks by Sideloadly/AltStore,
  background suspension), magnet metadata fetch end to end, GitHub Actions iOS job.

## Editions

`iosApp/Configuration/Config.xcconfig`: `FETCHJET_EDITION = full|store` → bundle id suffix, Swift
flag `FETCHJET_FULL`/`FETCHJET_STORE`, Info.plist key `FetchJetEdition`. Kotlin reads it via
`core/edition/AppFeatures` (hides Media/Torrent in the store edition).
`scripts/build-ios-ipa.sh full`
builds an unsigned IPA; CI job `build-ios-ipa` in `.github/workflows/build.yml`.

## What already works (desktop/Android, tested on Windows today)

- Open File / Open Folder fixed (`PathResolver.jvm.kt`, `.android.kt`).
- Real BitTorrent engine via libtorrent4j 2.1.0-39 (`jvmAndroidMain/torrent_engine/`), with
  `TorrentNativeLoader` extracting the bundled DLL/so/dylib (libtorrent4j does not do it).
- Pause/resume, 206-only ranged requests, CancellationException handling in
  `HttpChunkDownloader`; media failure/pause bugs in `MediaStreamDownloader`.
- Security: local bridge `main.kt` bound to loopback with Origin/Host checks; no `cmd.exe /c`
  wrapping of yt-dlp/ffmpeg; `--no-check-certificates` removed; torrent path sanitizing.
- Sort chips (date/size/name) + "In progress / Completed" grouping in the All tab.
- Cancel of a browser-sent link asks "Download in browser instead?" only for plain files.

## Remaining backlog (priority order)

1. iOS: verify the Python 3.13 media engine end-to-end (above); optional embedded QuickJS-NG as a
   second JS runtime like Palladium.
2. iOS: BitTorrent — build libtorrent-rasterbar + Boost as an xcframework and implement
   `TorrentBackend` for iOS via Kotlin/Native cinterop (same interface as desktop).
3. `MediaStreamDownloader.kt`: remove third-party resolvers (Invidious/cobalt), stream bodies with
   `prepareGet().execute` instead of `client.get` (whole file in RAM).
4. `DashboardViewModel` should be a lifecycle-aware ViewModel; split `App.kt` /
   `AddDownloadModalDialog.kt`; string resources (i18n); replace `MiniJson` with
   kotlinx.serialization.
5. Licensing: project bundles GPL-3.0 code (YTDLnis copy in `com/deniscerri`, youtubedl-android,
   YoutubeDL-iOS is MIT) → add a GPL-3.0 LICENSE and third-party notices.
