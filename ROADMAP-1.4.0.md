# FetchJet 1.4.0: work list

1.3.0 (2026-11-08) and 1.3.1 (Store update check fix, store listings in 15 languages) are
released. This is the active work list now, on branch `release-v1.4.0`. Mark an item `[x]` when
the owner has tested it.

**1.4.0 is the RAM release (owner's decision, 2026-11-08):** the desktop app must get close to
the light competitors. Measured on the owner's PC the same day, window open, idle (private
bytes): Ezoo 13 MB (Rust + Slint UI, downloads in a separate aria2c), FDM 265 MB (C++ + Qt 6
Quick), FetchJet 457 MB before 1.4.0, ~250 MB after the JVM tuning below. The JVM floor is
~150 MB, so the next step means leaving the JVM on desktop. Android and iOS are not part of
this (Android Compose is native; iOS already runs Kotlin/Native).

## 0. The plan: measure first, then pick the road

Two roads, both keep the Kotlin engines (`sharedLogic`) as the one shared core:

- **Road A, GraalVM native-image:** compile the whole desktop app (Compose included) ahead of
  time into one native exe. No rewrite; Skia stays. Expected ~80-120 MB (estimate). Risks:
  reflection / JNI config (ktor, kotlinx.serialization, libtorrent4j, skiko), Compose Desktop
  support for native-image is not official.
- **Road B, Kotlin/Native core + light native UI:** add `mingwX64` (Windows) and `macosArm64`
  targets to `sharedLogic`, build it as a `.dll` / `.dylib` with a C API, and draw the desktop
  UI with a light toolkit (Slint, like Ezoo) that calls it. Expected ~20-60 MB (estimate).
  macOS reuses most of `iosMain` (Darwin HTTP, Keychain, FJTorrent); Windows needs new actuals
  (WinHttp HTTP client, DPAPI secrets, files, bridge server) and FJTorrent built for Windows.
  Cost: the desktop UI is written again and new UI features are done twice (Compose for
  mobile, Slint for desktop).

Steps:

- [x] A1 spike (2026-11-09): GraalVM CE 25.0.5 (`E:\tools\graalvm`) native-image of the
  desktop app on Windows, VS 2026 C++ tools for the linker. Work files in `E:\tools\fjni`
  (agent config, `build.cmd`, `ni-args.txt`); test home `E:\tools\fjhome` (302 test tasks, the
  owner's `~/.fetch_jet` untouched). Steps that were needed: tracing agent run on GraalVM (the
  app clicked through + a YouTube inspect + a 1 MB download); every `java.awt` / `sun.awt` /
  `sun.java2d` / `sun.font` / Swing plaf class opened to JNI (`config-awt`, 1267 classes: the
  agent missed AWT's native callbacks, e.g. `MouseWheelEvent.getWheelRotation`, and the exe
  crashed on the first mouse-wheel scroll); `-Djava.home=<exe folder>` with the AWT dlls in
  `bin\` (Skia loads `<java.home>\bin\jawt.dll`). Build 2-4 min, exe 147 MB + AWT dlls.
  Results, same 302-task list: window in 2.9-4.2 s; private 193 MB at start, 217 MB after
  scrolling, with `-Xmn16m -Xmx512m` (without them 207 / 273 MB: the default young heap grows
  large). JVM build with the step-0 tuning: 247 / 276-292 MB. So road A saves only ~50-60 MB
  (~20 %): Skia + AWT + the driver stay. Not tried: torrents (libtorrent4j JNI was not
  recorded), media download with ffmpeg merge, tray, bridge.
- [x] B2 spike (2026-11-09): Slint 1.18.1 C++ (prebuilt MSVC package unpacked in `E:\tools\slint`,
  test app `E:\tools\slint-spike`): a FetchJet-like window (sidebar, header, search, 302-row list
  with progress bars). Default renderer = Skia on DirectX 12: 228 MB private (the NVIDIA driver
  again). `SLINT_BACKEND=winit-software`: **9.6 MB at start, 9.8 MB after hard scrolling, 10 % of
  one core while scrolling**, looks right. Exe 0.1 MB + `slint_cpp.dll`.
- [x] B1 spike (2026-11-09): Kotlin/Native `mingwX64` exe (`E:\tools\kn-spike`, Kotlin 2.4.20,
  ktor 3.6.1 WinHttp engine, coroutines, okio): 32 MB file in 8 parallel Range pieces + join,
  4.1-4.8 s (~60 Mbps). Default allocator: 76-124 MB peak, 47-85 MB after. With
  `-Xallocator=std`: **15 MB peak, 12 MB after**, same speed. Exe 4.6 MB.
- [x] B1 size of the port: `sharedLogic` commonMain 30.8k lines build as they are (its libraries
  all publish `mingw_x64`: coroutines, serialization, ktor, okio, lifecycle-viewmodel; the
  owner's `kmpLocalization` needs a `mingwX64()` target added). Platform side: 34 `expect`
  declarations; jvmMain + jvmAndroidMain = 5.7k lines to write again for Windows (HTTP through
  WinHttp, secrets with DPAPI, files, processes for yt-dlp / ffmpeg, bridge server, tray,
  update installer); iosMain (4.6k lines) is the model and macOS can reuse most of it. Torrents:
  FJTorrent (libtorrent C++) built for Windows. UI: `sharedUI` (13.8k lines of Compose) is
  written again in Slint for desktop; mobile keeps Compose.
- Estimate road B, Windows: ~25-35 MB for the whole app (core 12-15 + UI ~10), against 13 MB
  Ezoo, 217 MB road A, ~250 MB today. Work: several weeks (core actuals, C API between the
  Kotlin DLL and the Slint UI, the UI itself, torrents), then macOS.
- [x] Decision (owner, 2026-11-09): road B, on branch `desktop-native`; target: the FetchJet
  process stays under 50 MB when idle or running normal downloads (yt-dlp / ffmpeg run as their
  own processes, like Ezoo's aria2c).
- [x] First native build on `desktop-native` (2026-11-09): `desktopNative/` = Slint UI
  (`ui/app.slint`, `ui/theme.slint`: sidebar with counts, top bar, list, Add dialog, Settings,
  dark / light with the app's colors) + `main.cpp` (loads the engine, polls it every 500 ms) +
  `desktopNative/core` = Kotlin/Native `mingwX64` `fjcore.dll` (ktor WinHttp, 4 Range pieces,
  pause / resume from the `.partN` files, global rate limit; C API `fj_add`, `fj_toggle`,
  `fj_set_limit`, `fj_snapshot`, `fj_free`, `fj_set_destination`). Build: `core\gradlew.bat
  linkReleaseSharedMingwX64`, then `desktopNative\build.cmd` (VS 2026 + Slint in `E:\tools\slint`).
  Measured with a real download at 20 KB/s: **15.8-18 MB private** (UI + engine), writes at
  20 KB/s. Test limit default 20 KB/s (env `FETCHJET_LIMIT_KBS`), `FETCHJET_ADD=<url>` adds a link
  at start. Note: a rate limit throttles what the app reads; Windows still fills the TCP receive
  buffer first, so a small file (1 MB) arrives whole on the wire anyway.
  Known: synthetic clicks (automation tool) in the top ~60 px are not delivered as clicks; the
  owner's real mouse works there.

- [x] Map of the code to port (2026-11-09): `desktopNative/docs/port/01` ... `12` (the 44
  platform `expect`s with work packages, the presentation surface, six UI specs with port units,
  the desktop shell and packaging, i18n, the torrent plan) and the contract
  `desktopNative/ARCHITECTURE.md`.
- [x] Probes (2026-11-09), reports `desktopNative/docs/port/13` ... `18`. What they settled:
    - Renderer: Slint software renderer (14.6 MB private; Skia software 32 MB). Off-screen
      screenshot tool `desktopNative/tools/shot` (any page to a PNG in 0.2 s, no window) and
      `deskrun` (runs the app on a hidden desktop for RAM numbers).
    - Arabic / Persian: shaping and bidi are right; Slint has no layout mirroring, so the UI has
      its own mirrored row / text widgets; simplified Chinese needs the YaHei font; colour emoji
      come from an atlas drawn once by a child process (in-process drawing keeps 13 MB).
    - No std-widgets (too heavy): own widget set. Dialogs are overlays, lists are virtualized.
    - Kotlin `Dispatchers.Main` runs on the UI thread through the host; the Kotlin / C++ bridge
      (snapshots out, actions in) was tested: 700 000 state updates in 3 s reach the UI as 28
      notifications.
    - Memory: binary option `pagedAllocator=false` (5 to 11 MB where the default allocator keeps
      22 to 53 MB). Unicode and long paths work with the EXE manifest (UTF-8 code page).
    - Windows helper layer `sharedLogic/src/mingwX64Main/.../platform/win/` (files, crypto,
      DPAPI, registry, shell, clipboard, processes, zip, HTTP engine) with a test program
      `desktopNative/tests/winhelpers` (155 checks pass; crypto and secrets are byte-compatible
      with the JVM app, so both apps can share one app home).
    - HTTP: ktor's WinHttp engine keeps the connection open after a cancel and has no proxy login
      for HTTPS, so the app has its own engine on raw WinHTTP (`WinHttpEngine.kt`). SOCKS5 needs a
      small forwarder inside the app (not written yet).
    - Local test servers `desktopNative/tools/testserver` (HTTP / HTTPS / proxies on 127.0.0.1).
- [x] The 44 `mingwX64` actuals (2026-11-09):
  `gradlew :sharedLogic:compileKotlinMingwX64 -Pfetchjet.nativeDesktop=true` passes and the JVM
  build is unchanged. Four parts are placeholders for now (list and state:
  `desktopNative/docs/port/STATUS.md`): send to device, torrents, SOCKS5 proxy, the speed-limit
  proxy of yt-dlp downloads.
- [x] Core module `:desktopNativeCore` (2026-11-09): `fjcore.dll` holds the real engines and the
  view model behind a small C API (`fj_start`, `fj_action`, `fj_snapshot`, `fj_call`). First
  test without any window (`desktopNative/tests/corehost`, Python loads the DLL with a scratch
  home, files come from the local test server): 29 checks pass (direct download on 4 connections
  with the right SHA-256, the Add dialog path, pause closes the connection, resume continues
  with ranges, remove deletes the file, `tasks_cache.json` and `app.log` are written).
- [x] Foundations (2026-11-10): the UI protocol (DTOs in channels, actions, presenter), one
  generator (`desktopNative/tools/gen/gen.py`: Slint structs, strings, the C++ bridge), theme and
  widgets, the C++ host `FetchJet.exe`.
- [x] The UI pages (2026-11-10), each drawn from the state of the real core and checked as
  pictures (dark, light, Arabic): home, details, alerts, Add dialog (direct, media, playlist,
  link list, torrent part), settings (every page of the desktop app; BitTorrent and Devices
  stay hidden until their engines work), update dialog, extension dialogs, the send-to-device
  sheet and pairing dialog (checked with made-up devices).
- [x] Browser bridge on 127.0.0.1:28450 inside the core (2026-11-10): every endpoint of the
  desktop app, pairing dialog, 31 checks with a test port.
- [x] Launch and single instance (2026-11-10): link / `fetchjet://` / login-start arguments, a
  second start hands its link to the running app and ends in 0.1 s (checked with the real EXE
  on hidden desktops). Colour emoji in the real app (pictures from a child process, cached).
- [x] Integration (2026-11-11): every suite of `desktopNative/tests/corehost` passes on the
  release core on Windows (download 29, home 41, add 29, media 31, settings 87, links 17, launch 11,
  network 11, devices 43, proxy_limit 13, paths 17, torrent 17, bridge 34; no internet); on Linux
  (WSL) the download, add, media, links, network, proxy_limit, home and torrent suites pass,
  plus the real EXE on a hidden desktop (`tests/host/test_shell.py` 8/0, run on a scratch copy of
  the new build).
- [x] RAM with a speed limit (2026-11-11): 56 MB -> 30 MB inside the release EXE at 1 MB/s (300
  MB file, 92 s); idle 25 MB. Cause: ktor's copy buffer in front of every download body; the
  native download loops now read the engine's channel directly (`core/network/DownloadBody.kt`),
  and the GC keeps a 0.8 reserve. Details and numbers: `desktopNative/docs/port/STATUS.md`, last
  section. Not measured: RAM with torrents, with several downloads, with yt-dlp media downloads.
- [ ] Owner's look on a real desktop: mouse, keyboard, tray, close to tray, clipboard watcher
  (`desktopNative\run-dev.cmd`). Not seen yet on this PC's own desktop.
- [x] Send to device (2026-11-10 / 11): one engine in commonMain for every OS; receiver approval
  for every send (60 s, "no-answer"), receive progress and the "Preparing file" step. Tested in
  earlier runs on both builds over loopback. Open: a real phone with a new Android build; iOS
  needs a Mac run.
- [x] SOCKS5 forwarder and the yt-dlp speed-limit proxy (2026-11-10): shared
  `platform/proxy/LoopbackProxy.kt`; `run.sh proxy` 37/0 and `test_proxy_limit` 13/0 on both OSes.
  Open: the yt-dlp route through the app's own proxy is the owner's decision.
- [x] Torrents (2026-11-10 / 11): `fjtorrent.dll` and `libfjtorrent.so` built from libtorrent
  2.1.2; `test_torrent` 17/0 on both OSes. Not tested on the real internet.
- [ ] Release build and installers: built and checked on Windows (MSI 20.7 MB and MSIX 26.9 MB
  unsigned, both checks pass) and Linux (`.deb` 22.6 MB, `tar.gz` 30 MB). Open: the MSI / MSIX
  install test (the Windows Sandbox feature is not enabled on the PC yet; the owner must enable it),
  the CI jobs `windows-native` and `linux-native` (never run; `windows-native` needs the Slint SDK
  URL variable), and the version-resource check.
- [x] Linux port in WSL (2026-11-10 / 11): same code as Windows through `nativeDesktopMain`;
  release core cross-built; the Linux run of the download, add, media, links, network, proxy_limit,
  home and torrent suites passes in WSL (Ubuntu 26.04). The settings, devices, paths, launch and
  bridge suites were not re-run on Linux in this round. Open: a real
  Linux desktop (tray and portal dialogs were tested only against fakes).
- [ ] macOS: not started. iOS device sync: needs a Mac run (`IosDeviceSyncPlatform.kt`).

## Step 0 (done): JVM tuning on desktop


Why: the owner compared with IDM (under 5 MB). FetchJet runs on the JVM with Compose / Skia, so
~100 MB is the floor and IDM's number cannot be matched, but the app holds much more than it needs
today.

- [x] Measured 2026-11-08 on Windows (NVIDIA GPU), dev run with `FETCHJET_NMT=true` and
  `jcmd <pid> VM.native_memory summary`: process 456 MB private, the same open or in the tray.
  JVM part 225 MB (G1 bookkeeping 61, heap 44 committed / ~30 used, metaspace + symbols 73,
  JIT code 17). The other ~230 MB is the NVIDIA DirectX 12 driver Skia draws with. libtorrent
  was not even loaded. Hiding to the tray frees nothing.
- [x] Software drawing (`SKIKO_RENDER_API=SOFTWARE`): 339 MB private (-118), outside-JVM part
  ~90 MB; idle CPU 0.8 % of one core; the window renders correctly.
- [x] Applied: Windows draws in software unless `skiko.renderApi` / `SKIKO_RENDER_API` is set
  (`main.kt`, first lines of `main`); JVM `-XX:+UseSerialGC -Xms32m -Xmx1g
  -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30` (`desktopApp/build.gradle.kts`). `-Xms32m`
  matters: without it SerialGC keeps the default 512 MB start heap (1/64 of RAM) committed
  forever. Measured with 302 tasks, hard scrolling + Settings and back, then 30 s idle:
  software 247 MB at start, 276 MB after; GPU (`SKIKO_RENDER_API=DIRECT3D`) 389 / 432 MB.
  CPU while scrolling: 89 % of one core on both. GC bookkeeping 61 -> 2 MB.
- [ ] Owner: use the dev build / next release normally and say if anything feels slow
  (resize, big lists, dialogs). Then measure the installed MSIX once.
- [x] Tried and dropped (2026-11-08, same 302-task list, idle 30 s, no mouse): AppCDS dynamic
  archive (`jcmd VM.cds dynamic_dump`, 68 MB file) 247 -> 237 MB; plus C1-only JIT
  (`-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m`) 227 MB. Metaspace 50 -> 3 MB, but
  Windows counts the mapped archive (80 MB) as private, so the gain is only 10-20 MB, for a
  per-build archive in CI that must match the bundled JVM and slower hot code. Not worth it.
  Dev hook kept: env `FETCHJET_JVM_ARGS` adds JVM flags to `:desktopApp:run`.
- [ ] Dropped for now: disposing the window in the tray (the GPU / UI memory did not change when
  hidden, the measured cost is the driver itself) and lazy libtorrent (not loaded at idle).

## Also in 1.4.0: update check skipped an older build for this platform (bug, fixed in code)

Owner's Mac on 1.1.0 (2026-11-08) said "Version 1.3.1 has no changes for this device; you have
the latest build for it (1.1.0)", though 1.3.0 has a macOS build. When the latest release marks
the platform `fetchjet-unchanged`, the app now reads `/releases?per_page=30` and offers the
newest published release that is not unchanged for this platform and has its installer
(`fetchNewestBuiltForThisPlatform` in `UpdateManager.kt`). With today's releases: macOS /
Android / Linux on 1.1.0 or 1.2.0 → 1.3.0. Compiles (JVM + Android). Unit test
`NewestBuiltForTest` on the real release list (`jvmTest/resources/releases-2026-11-08.json`):
macOS / Android / Linux → 1.3.0, Windows / iOS → 1.3.1, drafts and pre-releases ignored.
Also tested on the A56 (below).

- [ ] Installed apps up to 1.3.x keep the old logic: they get the fix only from a release that
  includes their platform, so 1.4.0 must be built for every platform.
- [x] Tested 2026-11-08 on the A56: debug build with `APP_VERSION` set to 1.2.0 (old app
  uninstalled, owner's OK) showed "FetchJet 1.2.0 · Version 1.3.0 is available" while 1.3.1 is
  the latest.

## Also in 1.4.0: Android status bar in dark and light (small) — not reproduced

Owner's report (2026-11-08): in dark mode the status bar icons (battery, clock...) should be
white but were not visible (dark icons on the dark top bar). Later it was fine by itself.
Tried on the A56 the same day (debug build 1.2.0-labelled, phone in dark): cold start, home
screen, Add dialog, Settings, in-app Light -> Dark switch, back from Home and from Recents:
white icons in dark, dark icons in light every time (screenshots checked). Nothing changed in
the code. If it comes back, note the screen, the theme (Light / Dark / System), and what was
done just before (switched theme, came from a notification, phone theme changed at night...).
Where to look then: `MainActivity.onCreate` sets light bars before the theme is known, then
`setContent` re-applies them from `App(onDarkModeChanged)`; screens that are their own window
(task details `Dialog`, send sheets) do not inherit the activity's bar settings.

## Also in 1.4.0: carried over from 1.3.0 (checks)

- [x] Release notes in the app's language (1.3.0 item 6): an installed 1.3.0 in Arabic shows
  the 1.3.1 notes in Arabic in the update dialog (`release-notes-ar.md` on the release).
  Owner-tested 2026-11-08 with the iOS app under PlayCover.
- [ ] Browser extension inside the app: the in-app download of the signed .xpi from the published
  GitHub release (was not testable while 1.3.0 was a draft).
- [ ] Send to device (1.3.0 item 4) on a **real iPhone**: discovery, pairing, send both ways. If
  discovery fails there, "Connect by IP" and sends after pairing still work (unicast).
  Closer than the simulator: the IPA under PlayCover on the Mac (works, 2026-11-08), but it is
  not an iPhone's network sandbox.

## External, waiting

- [ ] Edge Android listing: waiting for Microsoft's answer. Edge iOS: not tested
  (see `docs/edge-android-test-cases.docx`).
- [ ] Edge Add-ons from CI: the `EDGE_PRODUCT_ID` secret is missing, so CI cannot submit the Edge
  version (owner adds it).
