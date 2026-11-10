# Native desktop port: status

Last updated: 2026-11-10. This file says what works, what is a placeholder and what comes next.
Rules and structure are in `desktopNative/ARCHITECTURE.md`; the maps of the code to port are
`01` ... `12` in this folder, the probe reports `13` ... `18`.

## Commands

All from the repository root. One Gradle build at a time, low priority.

```
gradlew :sharedLogic:compileKotlinMingwX64 -Pfetchjet.nativeDesktop=true --priority low
gradlew :desktopNativeCore:linkDebugSharedMingwX64 -Pfetchjet.nativeDesktop=true --priority low
python desktopNative/tools/gen/gen.py                        (after a DTO, UiAction or string change)
desktopNative\build.cmd debugShared                          (host EXE; releaseShared for the release DLL)
bash desktopNative/tools/testserver/servers.sh start         (local HTTP / HTTPS / proxies)
python desktopNative/tests/corehost/test_download.py         (core without a window)
python desktopNative/tests/corehost/test_home.py
python desktopNative/tests/corehost/test_add.py
python desktopNative/tests/corehost/test_media.py            (needs tests/fakes/build.cmd once: the fake yt-dlp)
python desktopNative/tests/corehost/test_settings.py
python desktopNative/tests/corehost/test_links.py
python desktopNative/tests/corehost/test_bridge.py           (the browser bridge on test port 28459)
python desktopNative/tests/corehost/test_devices.py          (send-to-device sheet, with made-up devices)
python desktopNative/tests/corehost/test_launch.py           (start arguments: link, fetchjet://, autostart, hand-over)
python desktopNative/tests/host/test_shell.py                (the real EXE on hidden desktops: start with a link, bridge, second instance)
python desktopNative/tests/corehost/shots.py [name part]     (pictures of every screen)
python desktopNative/tests/corehost/shots_settings.py [name part]   (settings, update and extension dialogs)
bash desktopNative/tests/winhelpers/run.sh                   (Windows helper layer, 155 checks)
```

- The core tests use the scratch home `E:/tools/fjhome/corehost/<name>` (`FJ_CORE_WORK` changes
  it) and never the real `~/.fetch_jet`. `FJ_TEST_RELEASE=1` runs the release DLL.
- **Every test or development run switches automatic downloads off**: the app fetches yt-dlp,
  ffmpeg and deno from GitHub by itself at start. Core: start config `"automaticDownloads": false`
  (the test host does it). EXE: `--no-auto-downloads`. Or env `FETCHJET_NO_AUTO_DOWNLOADS=1`.
- The test host (`corehost.py`) refuses the actions that would use the internet, read the
  clipboard or start a power action (`refuse_in_tests`): "Download the tools", "Check now",
  "Watch the clipboard" on, a when-done choice other than "Do nothing".
- More than one session at work: every build goes through `bash desktopNative/tools/one-build.sh
  <command>` (one heavy build at a time).
- The whole app without showing a window (hidden desktop), with memory numbers and a picture:

```
desktopNative\tools\shot\build\Release\deskrun.exe --timeout 15000 --after 6000 --info --capture E:\tools\probes\ui\app.bmp -- E:\tbib\FetchJet\desktopNative\build\Release\FetchJet.exe --home E:\tools\fjhome\apphome1 --downloads E:\tools\fjhome\apphome1\dl --no-bridge --no-focus --no-auto-downloads [--script actions.txt]
```

  `--script` = a text file with one UiAction JSON per line, sent every 500 ms (for tests).

## Trying it on the real desktop (the owner)

`desktopNative\run-dev.cmd` starts the development build next to the installed FetchJet without
disturbing it: its own settings folder (`%LOCALAPPDATA%\FetchJetDev\.fetch_jet`), its own bridge
port (28459, so the installed app keeps the browser extension) and no download of the media
tools unless "Download the tools" is pressed in Settings. Downloads go to the normal Downloads
folder. A plain double-click on `build\Release\FetchJet.exe` would instead talk to the installed
app on port 28450 (and exit when it is running) and fetch the media tools at once.

## How a screen is made

1. DTO in `desktopNative/core/.../nativeapp/dto/` (+ a channel in `Channels.kt`), actions in
   `actions/UiAction.kt`, state logic in `presenter/` (copied rule by rule from `sharedUI`).
2. `python desktopNative/tools/gen/gen.py` writes `ui/generated/*.slint`,
   `host/generated/fj_bridge.h`, `core/.../generated/TrTable.kt`, `ui/generated/protocol.json`.
3. Page in `desktopNative/ui/pages/`, widgets in `ui/widgets/`.
4. A scenario in `tests/corehost/shots.py`: the real core makes the state, `shot.exe` draws the
   page from it (dark / light, English / Arabic). A check script `tests/corehost/test_*.py`
   covers the logic.

## Done and checked

| Part | Check |
|---|---|
| Windows helper layer (`sharedLogic/src/mingwX64Main/.../platform/win/`) | `tests/winhelpers`: 155 checks |
| Own HTTP engine on WinHTTP (`WinHttpEngine.kt`) | 48 network checks inside the 155 |
| The 44 `mingwX64` actuals | compile; the ones the download path uses run in the core tests |
| `:desktopNativeCore` -> `fjcore.dll` (C API, Main dispatcher, change notifier, crash log) | `test_download.py`: 29 checks |
| Generator (`tools/gen/gen.py`): 624 strings, DTO structs, actions, C++ bridge | compiles in Slint, Kotlin and C++ |
| Home screen: sidebar / rail, top bar, speed control, list (groups, rows wide and narrow, selection, toolbar, banners), status bar | `test_home.py`: 41 checks; pictures dark / light / Arabic / Chinese |
| Details (side panel from 1280, bottom sheet below): numbers, pieces map, connection cards, links, buttons | pictures with a running download (6 pieces) |
| Alerts: remove, missing file, clipboard offer, browser fallback, extension pairing, restart on pause, when-done countdown / error | picture of remove (English, Arabic); the others share the same component |
| Add dialog, direct link: link field (paste, clear, import, open torrent), engine, schedule (now / in 30 min / 1 h / at a time), connections, file name, checksum, destination + quick folders, start | `test_add.py`: 29 checks; pictures |
| Add dialog, media: qualities, subtitles, clip + time picker, audio format, playlist, engine missing; duplicate and file-conflict dialogs | `test_media.py`: 31 checks (fake yt-dlp, no internet); pictures |
| Settings: page list (wide and narrow), General, Downloads (folder, connections, presets, speed schedule, organize, queue), Browser extension, Network (proxy, site logins), Media tools, Updates | `test_settings.py`: 87 checks; pictures dark / light / Arabic; one generic page drawn from rows (`SettingRowDto`) |
| Update dialog (new version, notes, download progress) and the extension dialogs (browser missing, load unpacked steps) | checks inside `test_settings.py` with a made-up release (`debug.update`); pictures |
| Add dialog, several links: list with tags, video title + quality menu per row, retry, folder, select all | `test_links.py`: 17 checks; pictures |
| Browser bridge (`nativeapp/bridge/`: `HttpListener.kt` on ktor-network, `BridgeServer.kt`): `/ping`, `/config`, `/pair`, `/inspect`, `/add`, `/links`, `/show`, `/download`, the Host / Origin / header / token gates, the pairing dialog, 5 s dedupe, inspect cache | `test_bridge.py`: 31 checks (real HTTP requests to 127.0.0.1:28459) |
| Launch (`nativeapp/Launch.kt`, export `fj_prepare`): link / `fetchjet://` / `--autostart=true` arguments, hand-over to a running instance (`/download`, `/ping` + `/show`) | `test_launch.py`: 11 checks; `tests/host/test_shell.py`: 8 checks with the real EXE (a start with a link opens the Add dialog; a second start with a link ends in 0.1 s and the first one gets the link) |
| Colour emoji in the real app (`host/emoji_atlas.h`): pictures made by a child process on demand, cached in `<home>/cache/emoji` | seen in the hidden-desktop picture; no extra memory in the app |
| Send to device: sheet (device list, modes, delete after, connect by IP, sending states) and pairing dialog | `test_devices.py`: 24 checks with made-up devices (`debug.devices`); pictures. The engine behind it is still the placeholder |
| Settings pages BitTorrent (upload limit with custom value, download-only, engine) and Devices | checks inside `test_settings.py` (`debug.sections` shows the two hidden pages); pictures |
| Add dialog, torrent part (inspect, error, file list with priorities, download-only) and torrent files inside the link list | written, compiles; not run: needs the torrent engine |
| Host `FetchJet.exe` (C++): loads the core, applies snapshots, sends actions, file / folder dialogs, dark title bar | ran on a hidden desktop with the settings page: **32.9 MB private**, 70 MB working set, with the debug core |

## Compiled but not run yet

Media tools (yt-dlp / ffmpeg / deno download and update), yt-dlp inspection and download, the
in-app updater, start at login, browser integration, power actions, the file pickers (need a
click on a real desktop).

## Not ported yet (UI)

- Hidden until their engines work: the torrent part of the Add dialog and the BitTorrent
  settings page (shown when `AppFeatures.torrent` is true; until then a magnet link is treated
  as a plain file link and fails as a download), the Devices settings page and "Send to device"
  (`DEVICE_SYNC_READY` in `SettingsPresenter.kt`). All four are written (see the table above).
- Drag of the details sheet handle.
- Settings details left out: the segmented pickers do not scroll sideways on a very narrow card
  (they are clipped), no keyboard focus ring on rows.

## Work in progress (stopped 2026-11-10 at 02:00, the owner's stop time)

Four parts were being written by helpers and were stopped unfinished. The full hand-over notes
(files, decisions already taken, exact commands, likely compile errors, test plans) are
`native-port-torrent.md`, `native-port-device-sync.md`, `native-port-proxies.md` and
`native-port-packaging.md` in
`C:\Users\miche\AppData\Local\Claude-Data\claude_pzs8sxrjxfjjc\side-session-notes\local_444deb2c-732f-4fa4-81c1-96b11b677c58`.
The EXE and `fjcore.dll` in `desktopNative/build/Release` are from 01:46, before the edits below:
they are the tested ones.

| Part | State | First step when the work goes on |
|---|---|---|
| Torrents | libtorrent 2.1.2 sources are in `E:\tools\libtorrent-2.1.2` (the one download the owner allowed, 6.4 MB; tarball in `E:\tools\libtorrent-dl`). `desktopNative/torrent/`: `include/fjtorrent.h`, `fjtorrent.def` (34 exports), `fjtorrent.cpp` (a draft of all 34 functions, **never compiled**; it has the local-only switch `FETCHJET_TORRENT_LOCAL_ONLY=1` for tests). Not written: `CMakeLists.txt`, the test seeder `fjtseed`, the Kotlin binding / engine / backend, the tests. `TorrentBackend.mingw.kt` is still "not supported" | write `CMakeLists.txt`, build libtorrent + the DLL (MSYS2 clang64, Ninja, low priority, 4 jobs), fix what the draft's first compile shows |
| Send to device | the engine is written (`WinDeviceSync`: a port of `DeviceSyncImpl.jvmAndroid.kt` on ktor-network, plus lock, LAN, store and file helpers): 5 files, **never compiled**, parked outside the tree in `E:\tools\probes\device-sync-wip\` (same relative paths). `git status` shows them as `AD`: the IDE staged them while they were in the tree, nobody ran `git add`. Not written: the factory switch (`DeviceSyncFactory.mingw.kt` is still the placeholder), all tests, the JVM cross-check | `cp -r /e/tools/probes/device-sync-wip/sharedLogic /e/tbib/FetchJet/`, wire the winhelpers test project, compile |
| SOCKS5 forwarder, yt-dlp speed-limit proxy | written: `platform/win/WinLoopbackProxy.kt` (the loopback proxy, 31 KB), the new `WinSocksForwarder.kt`, `MediaThrottlingProxy.mingw.kt` and `NetworkSettings.mingw.kt` on top of it, and the tests `ProxyTests.kt`: **never compiled, no test ran**. Parked outside the tree in `E:\tools\probes\proxy-handover\` (same relative paths); the three files in the tree are back to their placeholder text; `WinLoopbackProxy.kt` and `ProxyTests.kt` show as `AD` in `git status`. The code uses `WinLock.kt` of the device-sync files | restore the device-sync files first (`WinLock.kt`), then `cp -r /e/tools/probes/proxy-handover/. /e/tbib/FetchJet/`, add the `proxy` mode to winhelpers (`Main.kt`, `run.sh`), compile, run against the local test proxies |
| Release build, installers | the release `fjcore.dll` links (20.3 MB; debug 40.3 MB; 3 min 14 s). Version resource of the EXE (`res/version.h.in`, `res/app.rc`, version lines in `CMakeLists.txt`): written, checked with `rc.exe` only, **not yet through a host build** (the next `build.cmd` is its first test; the note says how to take it out). `packaging/build-release.cmd`: written, never run. Not written: stage script, MSI (WiX), MSIX, their checks. No RAM number of the release build yet | `build.cmd debugShared` (first test of the version resource), then `packaging\build-release.cmd` |

## Placeholders (must be replaced before a release)

| Part | Today | Plan |
|---|---|---|
| Send to device (`DeviceSyncFactory.mingw.kt`) | does nothing | see "Work in progress" |
| Torrents (`TorrentBackend.mingw.kt`) | "not supported", the UI hides torrents | see "Work in progress" |
| SOCKS5 proxy (`WinSocksForwarder.kt`), speed-limit proxy for yt-dlp (`MediaThrottlingProxy.mingw.kt`) | see "Work in progress" | a small loopback HTTP proxy inside the app |
| Tray icon and menu, close to tray, single instance, bring to front, notifications, theme change | written in `host/main.cpp` (Win32 `Shell_NotifyIcon`, a hidden helper window, a named mutex); never seen on a real desktop: a hidden desktop has no taskbar | the owner tries it with `run-dev.cmd`; then fix what he reports |

## Decisions taken while porting (owner can veto)

- Development build rule (`platform/win/WinApp.kt`): an EXE without the file `FetchJet.installed`
  next to it (and not in `WindowsApps`) is a development build: its home is
  `%LOCALAPPDATA%\FetchJetDev\.fetch_jet`, it writes nothing to the registry and does not update
  itself. So a test run can never touch the installed app or its data. Environment overrides:
  `FETCHJET_HOME`, `FETCHJET_DOWNLOADS`, `FETCHJET_LOG_CONSOLE=1`, `FETCHJET_VERBOSE_LOGS=true`,
  `FETCHJET_NO_AUTO_DOWNLOADS=1`.
- Folder choice uses the Windows folder dialog (the Compose app has its own folder browser because
  Compose has no system dialog).
- Window: minimum 680 x 480, so the phone layout of the Compose UI (below 680) is not ported.
- Title bar follows the app theme (dark in dark mode); the JVM window keeps the light one.
- The toast is drawn above the page and the details sheet but under dialogs, like the Compose app
  (above them it covered the dialog buttons).
- The log redirect (`app.log`) takes the process's standard output. A host that wants its own
  console output sets `FETCHJET_LOG_CONSOLE=1`.
- Glyph icons (pause, play, check, arrows) come from Segoe UI Symbol; emoji icons are separate
  images. Which font Compose picked for each glyph was not compared on screen.

## Not verified

- No side-by-side comparison with the Compose app on screen (the owner's app is not started by
  the tests). Sizes follow the Compose code and the reports.
- The real window was only seen on a hidden desktop: mouse, keyboard, focus, DPI change between
  monitors and the system file dialogs need a look by the owner.

## How the settings page works

- `presenter/SettingsPresenter.kt` (sections, local state, publish), `SettingsRows.kt` (one
  builder per page, copied from the Compose pages), `SettingsActions.kt` (what each id does).
- A row has a `kind` (`SettingKind`: SWITCH, INFO, MENU, PICKER, CHIPS, NOTE, FIELDS, PRESET,
  BUTTONS, PROGRESS), an `id`, and flags `first` / `last` (card corners), `divider`, `bare`
  (outside a card), `caption` / `footer` (group title and note). A new setting = one row in the
  builder + its id in `SettingsActions.kt`; a new look = a new kind in `ui/pages/Settings.slint`.
- Actions: `SettingToggle(id, on)`, `SettingPick(id, index)`, `SettingText(id, text)`,
  `SettingClick(id)`, `SetSettingsSection(index)` (-1 = back to the list on a narrow window).
- Text fields: up to two per row (`one`, `two`), never in a nested list, so typing does not
  rebuild the field. The presenter bumps `rev` only when it changes the text itself.
- The update check at start (`UpdateManager.onAppStart`) runs only when automatic downloads
  are allowed.

## Next

1. Compile check of the tree as it was left at the stop: `:sharedLogic:compileKotlinMingwX64`
   (the proxy files), `build.cmd debugShared` (the version resource), then all the tests again.
2. The four parts of "Work in progress": torrents first (the owner asked for them), then send
   to device, the proxies, the release build with the installers and RAM under load.
3. Shell on a real desktop: tray, close to tray, bring to front, start hidden at login, the
   clipboard watcher, mouse and keyboard (needs the owner's eyes: `desktopNative\run-dev.cmd`).
4. CI job for the native build: a new gated `workflow_dispatch` input, artifact only (the
   release keeps its files until the switch). Written only, never started by the tools.
