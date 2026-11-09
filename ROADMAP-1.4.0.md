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
  linkReleaseSharedMingwX64`, then `desktopNativeuild.cmd` (VS 2026 + Slint in `E:	ools\slint`).
  Measured with a real download at 20 KB/s: **15.8-18 MB private** (UI + engine), writes at
  20 KB/s. Test limit default 20 KB/s (env `FETCHJET_LIMIT_KBS`), `FETCHJET_ADD=<url>` adds a link
  at start. Note: a rate limit throttles what the app reads; Windows still fills the TCP receive
  buffer first, so a small file (1 MB) arrives whole on the wire anyway.
  Known: synthetic clicks (automation tool) in the top ~60 px are not delivered as clicks; the
  owner's real mouse works there.

- [ ] Then the chosen road, platform by platform (Windows first: the owner's machine and the
  Store), with every feature checked against the current app before the switch.

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
