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
bash desktopNative/tests/winhelpers/run.sh                   (Windows helper layer; modes crypto ... net proxy sync, plus torrent / syncjvm)
python desktopNative/tests/corehost/test_proxy_limit.py     (shared loopback proxy: limit, CONNECT, SOCKS5 upstream; Windows and Linux)
python desktopNative/tools/fonts/make_fonts.py ...           (FetchJet Sans; LINUX.md "Fonts and icons")
python desktopNative/tools/emoji/extract_emoji.py ...        (emoji pictures; LINUX.md "Fonts and icons")
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
   `actions/UiAction.kt`, state logic in `presenter/` (copied rule by rule from `androidUi`).
2. `python desktopNative/tools/gen/gen.py` writes `ui/generated/*.slint`,
   `host/generated/fj_bridge.h`, `core/.../generated/TrTable.kt`, `ui/generated/protocol.json`.
3. Page in `desktopNative/ui/pages/`, widgets in `ui/widgets/`.
4. A scenario in `tests/corehost/shots.py`: the real core makes the state, `shot.exe` draws the
   page from it (dark / light, English / Arabic). A check script `tests/corehost/test_*.py`
   covers the logic.

## Done and checked

| Part | Check |
|---|---|
| Windows helper layer (`sharedLogic/src/mingwX64Main/.../platform/win/`) and the shared pieces it now compiles (`platform/desktop`, `platform/torrent`, `platform/proxy`) | `tests/winhelpers` (`run.sh`): modes crypto, secrets, system, files, zip 13, process 17, net 46, proxy 37, sync 125, syncjvm 44, torrent 37 (2026-11-10 night); counts per run in the dated sections |
| Own HTTP engine on WinHTTP (`WinHttpEngine.kt`) | `run.sh net`: 46 checks (2026-11-10) |
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
| Browser bridge (`nativeapp/bridge/`: `HttpListener.kt` on ktor-network, `BridgeServer.kt`): `/ping`, `/config`, `/pair`, `/inspect`, `/add`, `/links`, `/show`, `/download`, the Host / Origin / header / token gates, the pairing dialog, 5 s dedupe, inspect cache | `test_bridge.py`: 34 checks (real HTTP requests to 127.0.0.1:28459, or `FJ_BRIDGE_PORT`), including the missing-yt-dlp reason and the empty list for a page without formats |
| Launch (`nativeapp/Launch.kt`, export `fj_prepare`): link / `fetchjet://` / `--autostart=true` arguments, hand-over to a running instance (`/download`, `/ping` + `/show`) | `test_launch.py`: 11 checks; `tests/host/test_shell.py`: 8 checks with the real EXE (a start with a link opens the Add dialog; a second start with a link ends in 0.1 s and the first one gets the link) |
| Colour emoji in the real app: bundled pictures `res/emoji` (Noto Color Emoji, 2026-11-10 night) on Windows and Linux, read by `host/app.cpp`; the DirectWrite atlas (`host/win/emoji_atlas.h`) is only the Windows fallback | pictures `E:\tools\probes\linuxport\r3\after\*`, `E:\tools\probes\ui\shots-linux\r3-*` |
| One bundled UI font on both OSes: FetchJet Sans (Noto Sans + Noto Sans Arabic + DejaVu symbols, 4 weights), registered at start; pause / hourglass / browser icons drawn as paths (`Glyph`) | same pictures; Windows before / after compared (only change found: the clipboard note on Settings > General wraps to two lines) |
| Torrent loader and engine shared (`platform/torrent/FjtLibrary.kt`, `FjtEngine.kt`) with a small host per OS | `run.sh torrent` 37/0; `test_torrent` 17/0 on Windows (release, debug) and Linux (`libfjtorrent.so`, `fjtseed` for Linux) |
| Loopback proxy shared (`platform/proxy/LoopbackProxy.kt` over `WinLoopSockets` / `LinuxLoopSockets`): yt-dlp speed limit, SOCKS5 forwarder | `run.sh proxy` 37/0; `test_proxy_limit` 13/0 on Windows and Linux |
| Send to device: sheet (device list, modes, delete after, connect by IP, sending states) and pairing dialog | `test_devices.py`: 25 checks with made-up devices (`debug.devices`); pictures. The engine behind it is the shared `DeviceSyncEngine` (`winDeviceSync`) since 2026-11-10 (tests: `run.sh sync`, `run.sh syncjvm`); the sheet was not re-checked with it |
| Send confirmation (2026-11-10, owner's request): the sheet's Send opens `SendConfirm` (local `confirming` in `DeviceSheet.slint`) with file name, size, device, mode, and "delete after" in red when ticked; "✓ Send" calls the old `Act.deviceSend()`, "Back" / Escape / a click outside close only the confirmation. New `DeviceSheetDto` fields `fileSize`, `deviceName`, `deviceEmoji`. No "Approve" / "Reject" strings exist in `AppLocalization` (sharedLogic not edited), so the buttons use `sysDeviceSend` and `back` | `test_devices.py` check "the send confirmation gets the device and the size"; pictures `device-confirm-dark`, `device-confirm-light-ar` (`shots_settings.py device`, clicks Send in the picture), Back and Escape seen to return to the sheet unchanged. Not checked: a real click in the EXE (the owner's window holds `build\Release`, so the host and its `fjcore.dll` were not replaced) |
| Settings pages BitTorrent (upload limit with custom value, download-only, engine) and Devices | checks inside `test_settings.py` (`debug.sections` shows the two hidden pages); pictures |
| Add dialog, torrent part (inspect, error, file list with priorities, download-only) and torrent files inside the link list | `test_torrent.py`: 17 checks (.torrent path and magnet with the real `fjtorrent.dll` and a local seeder); the link-list torrent rows were not run |
| Host `FetchJet.exe` (C++): loads the core, applies snapshots, sends actions, file / folder dialogs, dark title bar | ran on a hidden desktop with the settings page: **32.9 MB private**, 70 MB working set, with the debug core |

## Compiled but not run yet

Media tools (yt-dlp / ffmpeg / deno download and update), the real yt-dlp (inspection and download
run in `test_media` / `test_bridge` only with the fake yt-dlp), the in-app updater, start at
login, browser integration, power actions, the file pickers (need a click on a real desktop).

## Not ported yet (UI)

- The torrent part of the Add dialog and the BitTorrent settings page show when
  `fjtorrent.dll` is next to the EXE (`AppFeatures.torrent`); without the DLL a magnet link is
  treated as a plain file link and fails as a download. Hidden until its engine is switched on: the Devices settings page and "Send to device"
  (`DEVICE_SYNC_READY` in `SettingsPresenter.kt`). All four are written (see the table above).
- Drag of the details sheet handle.
- Settings details left out: the segmented pickers do not scroll sideways on a very narrow card
  (they are clipped), no keyboard focus ring on rows.

## Work in progress (updated 2026-11-10 night, after "Linux port, third round")

The hand-over notes are in `desktopNative/docs/port/handover/` (`native-port-torrent.md`,
`native-port-device-sync.md`, `native-port-proxies.md`, `native-port-packaging.md`); their first
sections describe the 01:55 stop and carry a note that points to the current state. The current
builds and test counts are in the dated sections at the end ("Release round", "Problems fixed").
Feature-by-feature status against the JVM app: `desktopNative/docs/port/PARITY.md`.

| Part | State | First step when the work goes on |
|---|---|---|
| Torrents | Shared engine since 2026-11-10 night (Windows and Linux; `libfjtorrent.so` in the Linux packages). Compiled and in the app (`fjtorrent.dll` next to the EXE, session closed by `NativeApp.shutdown()`). `run.sh torrent` 37/0 five times in the release round (the "adding a file by selection" failure was fixed at 15:00, `handover/native-port-torrent.md`); `test_torrent.py` 17/0. Not seen yet: a real torrent on the internet inside the app, the picker button (needs a desktop) | the owner tries a real torrent / magnet in `run-dev.cmd`; `stage.ps1` looks for the DLL under `desktopNative\torrent` only (build with `FJT_BUILD_DIR=desktopNative\torrent\build`) |
| Send to device | Compiled, engine switched on in the factory (`winDeviceSync`, the shared engine). `run.sh sync` 89/0, `run.sh syncjvm` 30/0 (against the real JVM engine). The settings page and "Send to device" stay hidden (`DEVICE_SYNC_READY = false` in `SettingsPresenter.kt`); no port is opened unless the owner turns "Visible" on | build the EXE, then a real test with a second device (phone or the JVM app on another PC) |
| SOCKS5 forwarder, yt-dlp speed-limit proxy | Shared since 2026-11-10 night (`platform/proxy/LoopbackProxy.kt`, `SocksForwarder`, `MediaThrottlingProxy.nativeDesktop.kt`; Linux now uses the proxy for yt-dlp too). `run.sh proxy` 37/0, `test_proxy_limit` 13/0 on both OSes; the one-time leak-check failure is explained in "Problems fixed (2026-11-10)". Not yet run inside the app: a SOCKS5 proxy set in Settings > Network, and yt-dlp with a speed limit. Open: the speed-limit proxy connects directly, as on the JVM (routing it through the app's proxy is the owner's decision) | a run inside the EXE with the local test proxies (`servers.sh`), then the owner's decision on the yt-dlp route |
| CI for the native build | `windows-native` and `linux-native` (2026-11-10 night, `ubuntu-latest`, `.tar.gz` + `.deb`) jobs in `.github/workflows/build.yml`, only when `workflow_dispatch` is started with `native` ticked; artifacts only, no release, no signing. Not runnable yet: needs the Slint SDK zip in the repository variable `FETCHJET_SLINT_SDK_URL`, and the packaging files must be committed. Never triggered by the tools | commit the packaging files, set the variable, then the owner starts it by hand |
| Release build, installers | `build-release.cmd`, `stage.ps1`, `product.wxs` + `build-msi.cmd` (WiX 3.14), `msix\build-msix.ps1` and the two check scripts written and run (2026-11-10 night: stage 220 files with `fonts\` and `emoji\`, MSI and unsigned MSIX checks all passed). `licenses\THIRD-PARTY.txt` now has libtorrent, Boost and OpenSSL. RAM: under 50 MB private at the default limit, about 45-55 MB with a speed limit on | the owner tests install, JVM -> native upgrade and the MSIX on his machine or a VM |


## Placeholders (must be replaced before a release)

| Part | Today | Plan |
|---|---|---|
| Send to device (`DeviceSyncFactory.mingw.kt`) | real engine since 2026-11-10, tested on loopback and against the JVM engine only | see "Work in progress" |
| Torrents (`TorrentBackend.mingw.kt`) | real engine (`fjtorrent.dll`) since 2026-11-10, tested on loopback only | see "Work in progress" |
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
- Glyph icons (play, check, arrows ...) come from the bundled FetchJet Sans (DejaVu symbols since
  2026-11-10 night; before: Segoe UI Symbol); pause, hourglass and the browser icon are drawn as
  paths; emoji icons are bundled pictures. Text is FetchJet Sans, Chinese keeps Microsoft YaHei UI.
  The Compose app uses the system font; not compared side by side on screen.

## Not verified

- No side-by-side comparison with the Compose app on screen (the owner's app is not started by
  the tests). Sizes follow the Compose code and the reports.
- The real window was only seen on a hidden desktop: mouse, keyboard, focus, DPI change between
  monitors and the system file dialogs need a look by the owner.

## Look check against the Compose UI (2026-11-10)

Compared by reading the Compose code (`androidUi` theme, `HomeNavigation.kt`, `TaskList.kt`,
`ui/TaskRows.kt`, `TaskDetails.kt`, `ui/PiecesBar.kt`, `App.kt`, the Add dialog files,
`ui/FdmSettingsPanel.kt`, `ui/SettingsKit.kt`, `ui/UpdateUi.kt`, `ui/RemoveConfirmationDialog.kt`,
`ui/TimeRangePickerDialog.kt`, `ui/FetchJetTextField.kt`) next to the `.slint` files, and by drawing
the existing fixtures again with `shot.exe` (pictures in `E:\tools\probes\ui\shots-parity`, the
originals in `shots` are untouched). Still no picture of the JVM app itself.

Already matching (checked, no change): colour tokens of both schemes, sidebar / rail sizes and
pitch, top bar, speed control, list rows (wide and narrow), group headers, column header, status
bar, settings list and rows, segmented pickers, link list rows, media quality list, checkbox,
switch, buttons, field colours, update dialog. The native host EXE was rebuilt with these files
(`build.cmd debugShared`, EXIT 0).

Changed:
- Toast: no more `opacity` layer (the software renderer drew it above the dialogs); it is now under
  every dialog, as the decision above says. With the details sheet open it sits 24 px above the
  bottom, like the sheet's own snackbar host in Compose.
- Dialog widths follow the Compose modifier order (`widthIn(max)` first, then `fillMaxWidth(f)`):
  Add dialog `0.94 * min(660, W)` (620 px on a wide window, was 660), duplicate / file-exists
  `0.94 * min(520, W)` (489, was 520), time picker `0.92 * min(480, W)` (442, was 480).
- Alerts whose Compose text slot fills the width (remove, when-done countdown, load-unpacked steps)
  are 560 px wide like the AlertDialog maximum; the others still wrap their text (280 to 560).
- Not changed on purpose: the 1400 px content cap and the 820 px sheet cap of `App.kt` never apply
  in Compose (`fillMaxSize()` / `fillMaxWidth()` come before `widthIn`, see `04-ui-home-shell.md`),
  so the content and the sheet stay full width.
- Details numbers: the two columns are equal (Compose `weight(1f)`); before, the longer value made
  its column wider.
- Text fields: the soft focus glow (Compose `dropShadow` 10 dp, primary 28 %, red with an error),
  drawn as five faint rings behind the field.
- 48 px slots for Material buttons and clickable surfaces that were laid out at 40 / 36 px: alert
  and update dialog button rows, the Add dialog engine row and link label row (when Import / Open
  .torrent shows), the media retry and "Start media engine" buttons, the qualities header, the
  audio format chips, the torrent retry, the time picker buttons. Alert titles keep their 24 / 32 px
  line box. Update dialog: 4 px between "Skip" and "Later" (Compose inner row).
- Add dialog: no gap between "Retry" and the start button (the Compose row has none).
- Time picker card: surface with tonal elevation 6 (`#0D2D3F` / `#E4F2F9`, new token
  `Theme.surface-tonal-6`), as the Compose `Surface(tonalElevation = 6.dp)`.

Not matched (and why): shadows of cards, menus, the snackbar and the selected engine chip (the
software renderer has no blur; menus keep a thin border instead); the snackbar's scale-in; the
logo's extra 9 / 11 px corner clip (the PNG already has round corners). Fixtures in `shots` made
before the last DTO change (`updateDialog.statusMuted`, subtitle `auto`) no longer load in
`shot.exe` until `shots.py` / `shots_settings.py` run again with the current core.

## Full verification (2026-11-10)

Builds (12:44-12:58, all through `one-build.sh`, normal priority, offline): release core
`fjcore.dll` 20,921,856 B (12:44, newer than every core source, newest 12:04); `build.cmd
releaseShared` and a normal-priority copy of `build-release.cmd` (`build-release\Release`, 12:47:
`FetchJet.exe` 4,882,432 B, newer than every `.slint`, newest 12:35); `fjtorrent.dll` 9,893,888 B
copied next to both EXEs (byte-equal to `E:\tools\probes\fjtorrent\build`); debug core up to
date (12:13); `build.cmd debugShared`. For the stage, `fjtorrent.dll` was also built in
`desktopNative\torrent\build` (`FJT_BUILD_DIR`, 1.5 min). Logs in `E:\tools\probes\verify`.

| Suite | Debug | Release (`FJ_TEST_RELEASE=1`) |
|---|---|---|
| test_download | 29 / 0 failed | 29 / 0 |
| test_home | 41 / 0 | 41 / 0 |
| test_add | 29 / 0 | 29 / 0 |
| test_media | 31 / 0 | 31 / 0 |
| test_settings | 87 / 0 | 87 / 0 |
| test_links | 17 / 0 | 17 / 0 |
| test_bridge | 31 / 0 | 31 / 0 |
| test_devices | 24 / 0 | 24 / 0 |
| test_launch | 11 / 0 | 11 / 0 |
| test_torrent | 17 / 0 | 17 / 0 |
| host/test_shell | 8 / 0 | 8 / 0 (release EXE) |
| winhelpers sync / proxy / syncjvm | 84/0, 37/0, 30/0 | not run |
| winhelpers torrent | 37/0 with the probes DLL (needs `FETCHJET_TORRENT_DLL`, seeder next to it) | |

RAM, release EXE (`build-release`), hidden desktop, scratch home, 300 MB from 127.0.0.1 with the
fast preset set to 20 MB/s and picked from the speed control: idle 60 s **20.9-25.8 MB private**
(56-61 MB working set); during the download **69-81 MB private** (median 75, working set up to
117): over the 50 MB target; after it **30.4 MB private** (69 MB working set). Measured speed
20,392 KB/s against 20,480 (-0.4 %). File SHA-256 equal to the server.

Packages: stage 56 files, 65,070,870 B (now with `fjtorrent.dll`); `FetchJet-1.4.0.msi`
19,738,624 B, `check-msi.ps1` all passed (56 File rows = 56 extracted = stage); 
`FetchJet-1.4.0-store.msix` 25,756,527 B (unsigned), `check-msix.ps1` all passed. Nothing
installed. `THIRD-PARTY.txt` has no libtorrent / Boost / OpenSSL notice yet.

Failed / found:
- The app binds UDP `0.0.0.0:28461` (device-sync discovery) at every start, also while the
  Devices page is hidden (`DEVICE_SYNC_READY = false`) and "Visible" is off:
  `initDeviceSync()` always calls `start()`, whose listen loop binds `DEVICE_SYNC_ANY_HOST`; the
  announce socket binds `0.0.0.0:<any>`. A test run of the release EXE triggered the Windows
  Firewall prompt this way. There is no switch to keep it off in tests.
- `fjtorrent.dll` built in `desktopNative\torrent\build`: `run.sh torrent` fails "adding a file
  by selection downloads it" (100 s timeout) 4 of 4 runs when loaded from that folder; the same
  bytes copied to `E:\tools\probes\verify\tdll` pass 37/37 twice, and the probes DLL passes. Cause
  not found (path-dependent).

Not verified: speed-limit runs at 512 KB/s and 1 MB/s (direct and the fake yt-dlp media path),
stopped because every app start opens the discovery port; the Add dialog torrent picture (no
scenario in `shots.py`); winhelpers on the release build; anything on a real desktop.

## RAM during a download (2026-11-10, afternoon)

Method: the release EXE (`build-release` host + a fresh release core, copied to
`E:\tools\probes\ram-mem\{base,after}`) under `deskrun` on a hidden desktop, scratch home
`E:\tools\fjhome\ramprobe-*`, `--no-bridge --no-focus --no-auto-downloads`, `--script` that waits
30 s, picks the fast preset (20 MB/s) and starts `127.0.0.1:18200/file/300MB.bin`; an outside
sampler (`GetProcessMemoryInfo` every 1 s, server `/stats` liveBytes) in the session scratchpad
(`ramrun.py`). SHA-256 equal to the server in every run.

What grows (old core): 8 connections at 20 MB/s: **69-81 MB private** (median 75). Same file,
1 connection at 20 MB/s (`behavior.properties connections=1`): 28-31 MB. 8 connections without a
limit (~50-65 MB/s): 29-31 MB. So it was not the snapshots or the UI: it is per connection, and
only while the reader is held back by the rate limiter. Cause: `WinHttpEngine.readBody` copied
the body into a ktor `ByteChannel` from a writer coroutine that always kept a WinHTTP read
pending; a `ByteChannel` holds up to ~1 MB on the write side plus ~1 MB on the read side, so a
throttled reader let each connection run ahead.

Change (`sharedLogic/.../platform/win/WinHttpEngine.kt` only): the response body is now
`WinHttpBodyChannel`, a pull `ByteReadChannel`: `awaitContent` issues one `WinHttpReadData` into
the call's pinned buffer (128 KB, unpinned on `HANDLE_CLOSING`) only when the reader needs bytes;
no writer coroutine, no read-ahead. Errors close it as `ClosedByteChannelException(cause)` (as the
`ByteChannel` did), `cancel` closes the WinHTTP request.

Paired runs, no build running, same machine state:

| | idle | during 300 MB at 20 MB/s | after |
|---|---|---|---|
| old core | 20.7-25.7 MB (median 22.6) | 39.9-82.7 (median **75.5**), ws up to 117.6 | 28.7 |
| new core | 20.7-25.5 (median 22.6) | 45.5-59.9 (median **56.2**), ws up to 96.5 | 29.5 |

**Still over the 50 MB target.** Unlimited speed is unchanged (300 MB: old 7.5 s, new 6.4 and
7.4 s). Tried and dropped: read-ahead of 2 chunks (speed fell), read-ahead bounded to 512 KB per
connection through a kotlinx `Channel` (59-65 MB: whatever stays behind is driven by WinHTTP
reads running ahead of a throttled reader, not by the bytes queued). An early measurement of the
pull channel (27-35 MB) ran while a Gradle link was using the CPU and is not valid. The remaining
~30 MB over idle are not explained yet. Next suspects: Kotlin GC heap growth under 8 throttled
readers (GC target / `RateLimiter` sleeps), the okio `appendingSink(...).buffer()` per piece,
and WinHTTP's own receive buffers. A debug call that prints `kotlin.native.runtime.GC.lastGCInfo`
would separate the Kotlin heap from the native heap.

Tests after the change: corehost `test_download` 29/0, `test_home` 41/0, `test_add` 29/0
(debug core); winhelpers `net` all passed, `proxy` 36/37 once ("400 http downloads ... no handle
or memory left behind": handles +3, private +4.8 MB), then 37/37 on the rerun. Not run: the other
corehost suites, `sync`, the release-core corehost runs.

## Final integration (2026-11-10)

14:18-14:55, all builds through `one-build.sh`, normal priority, `--offline`, nothing downloaded
from the internet. Logs and data in `E:\tools\probes\final`.

Builds: release core `fjcore.dll` 20,937,216 B and debug core 41,967,616 B (both 14:01, Gradle up
to date, newer than every source); `build.cmd releaseShared`, `build.cmd debugShared` and the
`build-release` tree: EXIT 0, `FetchJet.exe` 4,882,432 B (no host or `.slint` change since 12:35, so
not relinked; stage = same bytes). Both CMake caches still pointed `FJ_TORRENT_DLL` at the old
`E:\tools\probes\fjtorrent` DLL: re-configured with `-DFJ_TORRENT_DLL=desktopNative/torrent/build/fjtorrent.dll`,
so `build\Release` and `build-release\Release` now hold the fixed DLL (9,893,888 B, SHA-256
`eb05e9fe...`). `:sharedLogic:compileKotlinJvm`: BUILD SUCCESSFUL. winhelpers link up to date, fake
yt-dlp rebuilt.

| Suite | Debug | Release (`FJ_TEST_RELEASE=1`) |
|---|---|---|
| test_download / home / add / media | 29/0, 41/0, 29/0, 31/0 | 29/0, 41/0, 29/0, 31/0 |
| test_settings / links / devices | 87/0, 17/0, 24/0 | 87/0, 17/0, 24/0 |
| test_torrent (fixed DLL) | 17/0 | 17/0 |
| test_bridge, test_launch (alone) | 31/0, 11/0 | 31/0, 11/0 |
| host/test_shell (alone) | 8/0 (`build\Release`) | 8/0 (`build-release`, a copy of the test with only the EXE path changed) |
| winhelpers net / sync / syncjvm | 46/0, 89/0, 30/0 | not run |
| winhelpers proxy | 36/1, rerun 37/0 (leak check: private 60.3 -> 64.9 MB) | not run |
| winhelpers torrent (fixed DLL) | **36/1 three times** (uTP x2, TCP x1) | not run |

The first debug round had 4 failures caused by the run, not the app: `FETCHJET_TORRENT_DLL` was set
for every suite, and `test_add` / `test_home` / `test_settings` expect no torrent backend. Rerun with
it only for `test_torrent`: all passed. `test_media` failed once in that round ("button counts the
videos": still "Inspecting...") with 8 suites in parallel; it passed in the rerun and in the release
round (8 in parallel).

Speed limit, release EXE, hidden desktop, scratch home, `--no-bridge --no-focus
--no-auto-downloads`, 300 MB from `127.0.0.1:18200`, limit picked with `PickSpeedOption` (the speed
control menu's action), presets 512 KB/s and 1 MB/s. Measured from the bytes on disk (`.partN`)
over ~117 s, first 5 s skipped (the server's own count is off by up to ~14 MB of loopback socket
buffers: 495.5 and 1061.7 KB/s). The two limited runs ran at the same time.

| Limit | Measured | % | Private during (median / max) |
|---|---|---|---|
| 512 KB/s | 511.9 KB/s | 100.0 | 51.3 / 56.4 MB (ws 94) |
| 1 MB/s | 1023.8 KB/s | 100.0 | 52.8 / 59.5 MB (ws 96) |
| unlimited | 53.7 MB/s (300 MB in ~6 s, SHA-256 equal) | | 30.3 / 30.4 MB |
| torrent 512 KB/s (core in `corehost`, `fjtseed` 200 MB) | 508.1 KB/s between quota steps, 498.0 over 40 s | 99.2 / 97.3 | not measured (Python host) |
| torrent 1 MB/s | 1000.5 / 967.7 KB/s | 97.7 / 94.5 | not measured |

No socket on 28461 / 28462 and no listening socket of the app in any EXE run (netstat by PID, twice
per run): the discovery port fix holds in the real EXE.

RAM, release EXE, same sampler (`GetProcessMemoryInfo` every 1 s), 60 s idle then 300 MB:

| | idle 60 s | during | after |
|---|---|---|---|
| default limit (unlimited) | median 22.4, max 25.7 | median 30.2, max 30.7 (4 samples, ~5 s) | 27.2 |
| fast preset 20 MB/s (method of the earlier runs) | median 22.6 | median **55.0**, max 58.4 (ws 95.7) | 28.9 |

Under 50 MB at the default limit; **still over 50 MB whenever a limit is on** (51-55 MB median at
0.5, 1 and 20 MB/s).

Packages: stage 56 files, 65,086,230 B (fixed `fjtorrent.dll`, 14:01 core, the release EXE);
`FetchJet-1.4.0.msi` 19,742,720 B, `check-msi.ps1` all passed (56 extracted = stage);
`FetchJet-1.4.0-store.msix` 25,759,056 B unsigned, `check-msix.ps1` all passed (55 = stage without
the marker). Nothing installed or registered, no staged EXE started.

Pictures: `shots.py` 27 and `shots_settings.py` 34 regenerated, 0 failed (every fixture in
`E:\tools\probes\ui\shots` is now current); 11 key ones in `E:\tools\probes\ui\shots-final` (the
12:49 set moved to `shots-final-1249`).

Failed:
- winhelpers torrent "adding a file by selection downloads it": 3 of 3 runs with the 13:42 DLL,
  stuck 100 s at `conns=0 candidates=0 listPeers=1 listSeeds=1`, so the `nudge_if_stuck` change does
  not recover this state. `test_torrent.py` (no such step) passes.
- RAM over 50 MB with a speed limit on.

Not verified: winhelpers on the release build; RAM of a torrent inside the EXE; the speed limit on
the media (yt-dlp) path; the Add dialog torrent picture and the link-list torrent rows; anything on
a real desktop, install, upgrade, a real second device, real internet torrents.

## Release round (2026-11-10, final)

15:08-15:17, builds through `one-build.sh`, normal priority, `--offline`; only 127.0.0.1 used. Logs
and data in `E:\tools\probes\release-final`.

| Build | Result |
|---|---|
| release / debug core link | UP-TO-DATE, `fjcore.dll` 20,937,216 / 41,967,616 B (14:01) |
| `build.cmd releaseShared`, `debugShared`, `build-release` tree | EXIT 0, `FetchJet.exe` 4,882,432 B (not relinked, no host change) |
| `:sharedLogic:compileKotlinJvm` | BUILD SUCCESSFUL |
| `fjtorrent.dll` next to both EXEs and in the stage | 9,906,176 B, SHA-256 `239895b529b8...6af9d338` (the 15:00 fix) |

| Release suites (`FJ_TEST_RELEASE=1`, release EXE for the shell) | Checks / failed |
|---|---|
| download / home / add / media | 29/0, 41/0, 29/0, 31/0 |
| settings / links / devices / torrent | 87/0, 17/0, 24/0, 17/0 |
| bridge, launch, host/test_shell (alone) | 31/0, 11/0, 8/0 |
| winhelpers sync / proxy (debug winhelpers) | 89/0, 37/0 |
| winhelpers torrent, 5 runs | 37/0 each; selection in 1062 / 1016 / 1047 / 625 / 1047 ms |

The first proxy run failed its first check only because the run set `FJ_TEST_WORK` to another
folder (the test reads the server logs from the default work folder); rerun with the default: 37/0.

| Speed limit, release EXE, 60 MB from 127.0.0.1, first 5 s skipped | Measured | % | Private during (median / max) |
|---|---|---|---|
| 512 KB/s | 507.9 KB/s (115 s) | 99.2 | 47.7 / 52.1 MB (ws 89.5) |
| 1 MB/s | 1013.4 KB/s (55 s) | 99.0 | 44.9 / 49.5 MB (ws 87.8) |

Both files SHA-256 equal to the server; no socket on 28461 / 28462, no listening socket.

RAM, release EXE, default limit: idle 30 s median 23.0, max 25.8 MB private; 150 MB download
(~3 s, only 3 samples) median 31.9, max 32.2 MB; after 27.7 MB. SHA-256 equal.

Packages: stage 56 files, 65,098,518 B; `FetchJet-1.4.0.msi` 19,742,720 B, `check-msi.ps1` all
passed (56 = stage); `FetchJet-1.4.0-store.msix` 25,764,654 B unsigned, `check-msix.ps1` all passed
(55 = stage without the marker). Both hold the fixed `fjtorrent.dll`. Nothing installed or
registered, no staged EXE started.

Not verified: winhelpers built as release (`releaseExecutable` is from 2026-11-09, not used);
the debug-core suites this round; real-internet downloads; anything on a real desktop.

## Problems fixed (2026-11-10)

Builds through `one-build.sh`, `--offline`; only 127.0.0.1 used; scratch homes
`E:\tools\fjhome\fix-*`, logs in `E:\tools\fjhome\fix-logs`. No msiexec, no install.

1. **"ffmpeg (local join) exit 1" repeated (owner's test log).** Not a retry loop: each of the 10
   failures follows its own SoundCloud HLS download (`HLS: N/N segments`, 10 of them, each with a
   different `DURATION`), one failure per track of a set. Cause: the HLS audio is joined into
   `.mka`, then `convertAudioIfNeeded` tried `-c:a copy` into `.mp3` for any `.mka` source; the
   stream is AAC, so ffmpeg refuses ("Exactly one MP3 audio stream is required") and the next
   attempt (`libmp3lame`) makes the file. Reproduced with the 4.4.1 ffmpeg on a generated AAC
   `.mka`: copy exit 1, libmp3lame exit 0. Fix (`MediaAudioConvert.kt`, common): `audioCodecOf`
   reads the codec id from the first 64 KB (`A_AAC`, `A_MPEG/L3`, `A_OPUS`, `A_FLAC`, `A_VORBIS`,
   Ogg heads) and the copy is tried only when it matches the target (unknown codec: as before).
   `runLocalFfmpeg` (mingw) now keeps the last 4-8 KB of ffmpeg's output, so the logged tail is the
   error, not the start of a long description. Test `MediaAudioConvertTest` (jvm): 3/0. The YouTube
   download in the same log (20 + 15 MB from googlevideo) merged without an error. The memory
   growth the report saw (65 MB private) was not investigated: several downloads ran at once.
2. **Inspection without yt-dlp.** `inspectPlatformQualities` (mingw, jvm) throws
   `MediaToolMissingException("yt-dlp is not installed. ...")`; the common inspection keeps the
   fallbacks and throws it only when the result is empty, logged with `AppLog.e`. The bridge
   answers 500 `{"error": ...}` and the extension shows that text (existing path); the Add dialog
   shows it as the inspect error. A page where yt-dlp finds nothing still answers `[]`.
   `test_bridge.py` has 3 new checks (34/0).
3. **Licences.** `packaging/licenses/THIRD-PARTY.txt`: libtorrent 2.1.2 (full `LICENSE` of the
   source, Arvid Norberg, with its bundled parts), Boost 1.92 (BSL 1.0 text from MSYS2 clang64),
   OpenSSL 3.6.5 (Apache-2.0, copyright line from `opensslv.h`). Not covered: the LLVM libc++ /
   mingw-w64 runtime that `-static` may also link into `fjtorrent.dll` (not checked).
4. **Proxy leak check.** 5 reruns of `run.sh proxy` (old test): 2 failed only this check (private
   +4.4 and +4.2 MB), 3 passed (+0.7, +0.4, +2.3 MB); handles +0 / +1 every time. With 6 rounds
   of 400 tunnels after a warm-up the private bytes went 7056, 7540, 7640, 7000, 7724, 11812 KB:
   flat with one 4 MB step, not growth per tunnel. So the allocator grows in steps, not a leak in
   `WinLoopbackProxy.kt`. Test changed (`ProxyTests.kt`): one warm-up round of 400, then 3 rounds
   (`leakrounds=N`), memory = the lower of the last two rounds, limit 8 MB (a leak of 10 KB or
   more per tunnel still fails).
5. **Docs.** "Work in progress" and "Done and checked" above match these results; the hand-over
   notes carry a note at the top of their old sections.

Tests after the changes (debug core relinked, winhelpers relinked): corehost download 29/0, home
41/0, add 29/0, media 31/0, settings 87/0, links 17/0, devices 24/0, bridge 34/0 (port 28471),
torrent 17/0; winhelpers proxy 37/0 three times with the new leak check (memory per round:
58.9 -> 60.9 / 63.4 / 63.5 MB; 67.0 -> 67.5 / 66.0 / 71.7; 67.4 -> 62.8 / 66.1 / 61.8), sync
89/0, torrent 37/0; jvm `MediaAudioConvertTest` 3/0 and `MediaQualityAudioTest` 3/0. Not run:
test_launch, host/test_shell, the release core, winhelpers crypto ... net, Android / iOS builds of
the common change (iOS needs a Mac). Not checked inside the real EXE: a SoundCloud set to MP3
with the new codec check (needs internet).

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

## 2026-11-10: the receiver accepts each device send (native done)

A paired device's send now shows a prompt on the receiver (file name, size, sender, Accept /
Reject). No answer in 60 s or Reject = the sender gets "no-answer" ("The other device did not
respond, try again"). Same pairing path (`pending`, `endPairing`, `confirmPairing` /
`cancelPairing`), no new frame. Design and results: `handover/native-port-device-sync.md`
(fourth session). Native tests: sync 106/0, syncjvm 37/0, test_devices 28/0. JVM engine changed
(compiled only inside the jvmpeer); Compose dialog, Android and iOS: second round.

## 2026-11-10: receive progress and "Preparing file" (all engines written)

Receiver side of "Send to device": every send (link or transfer, any engine) asks first (iOS
engine now too); after Accept the prompt becomes "Receiving a download" with a bar (sizes from the
send metadata; old senders without sizes = moving bar); the new row shows "Preparing file" (ar
"تهيئة الملف") with a moving bar and the sender's sizes until the engine's own first status.
Optional `SendPayload.totalBytes` / `downloadedBytes` (display only), no new frame; every released
decoder ignores unknown keys. Tests: sync 130/0, syncjvm 44/0, test_devices 41/0, test_download
29/0, test_home 41/0; JVM compile and `:androidApp:assembleDebug` OK; iOS not compiled. Details and
the one open rebuild: `handover/native-port-device-sync.md` (fifth session).

## Linux port, first round (2026-11-10 evening)

Layout, commands, downloads and the ordered to-do list: `LINUX.md`. One code base: new shared
source set `nativeDesktopMain` (sharedLogic and `:desktopNativeCore`) between `mingwX64` and the
new `linuxX64` target (same `-Pfetchjet.nativeDesktop=true`); the OS contract is `DesktopOs` /
`DesktopApp` in `sharedLogic/src/nativeDesktopMain/.../platform/desktop/`. 21 of the 44 actuals are
now shared, 23 stay per OS (all 23 are placeholders on Linux). The core has no OS code left. The
C++ host is split: `host/app.cpp` (shared) + `host/win/platform_win.cpp` + `host/linux/platform_linux.cpp`
(`host/main.cpp` and `host/emoji_atlas.h` -> `host/win/`). `gen.py` reads the DTOs from both core
source folders (output checked byte-equal without writing); `stage.ps1` also watches
`nativeDesktopMain` for the core freshness check.

Not moved on purpose (another session edits them): `core/src/mingwX64Main/.../presenter/DevicePresenter.kt`,
`dto/DeviceDto.kt`, `generated/TrTable.kt`; that folder is compiled as part of `nativeDesktopMain`.
Device sync on Linux is an inert placeholder (`DeviceSyncFactory.linux.kt`, no socket).

| Check | Result |
|---|---|
| Windows `:sharedLogic:compileKotlinMingwX64`, `:desktopNativeCore:linkDebugSharedMingwX64` | BUILD SUCCESSFUL |
| Windows host (MSVC, separate folder `E:\tools\probes\linuxport\winhost`, debug core) | EXIT 0 (`build\Release` not touched) |
| Windows corehost after the moves (twice, debug core, port 28473, work `E:\tools\fjhome\linuxport-win`) | download 29/0, home 41/0, add 29/0, settings 87/0, bridge 34/0 |
| `:sharedLogic:compileKotlinLinuxX64`, `:desktopNativeCore:linkDebugSharedLinuxX64` (cross-compiled on Windows) | BUILD SUCCESSFUL, `libfjcore.so` 43,385,488 B |
| Linux host in WSL (CMake 3.31.6, g++, Slint 1.18.1 Linux) | builds; runs under WSLg (X11); pictures `E:\tools\probes\ui\shots-linux\home-en.png`, `add-dialog-en.png`, `download-done-en.png` |
| Real download through the Linux host (`--script`, 64 MB from the WSL test server) | SHA-256 equal; RssAnon 32.7 MB, VmRSS 87.9 MB after |
| Linux corehost (`LANG=en_US.UTF-8`) | download 29/0, home 41/0; add 28/1 (the pasted-text clean-up check saw an old `urlRev` after 0.3 s: not looked at yet); settings 68/5 and bridge 28/5, every failure a Linux placeholder (no when-done power action, no yt-dlp, no in-app updater, no browser list) |

Not done: HTTPS / proxies on Linux, process layer and media, clipboard, dialogs, tray,
notifications, emoji atlas, torrents, device sync, packaging (order in `LINUX.md` section 5).

## 2026-11-10 (late): Send to device has one engine for every platform

The protocol and state machine (discovery, hello + code, pending / declined / expired / no-answer,
offers, send with retry and "pending" polling, receive with progress, unpair, blocked list, frames,
JSON, timings, `FJSYNC` log lines) now live once in `sharedLogic/src/commonMain/.../feature/device_sync/`
(`DeviceSyncEngine`, `SyncDiscovery`, `SyncPairing`, `SyncReceiver`, `SyncSender`, `SyncFrames`,
`SyncWire`, `SyncConfig`, `SyncPlatform`, `DeviceSyncStore`). Platform layers only: `JvmDeviceSyncPlatform.kt`
(JVM + Android), `WinDeviceSyncPlatform.kt` (mingw, ktor-network + WinCrypto + WinFiles; `winDeviceSync()`
replaces `WinDeviceSync`), `IosDeviceSyncPlatform.kt` (Swift bridge, unchanged). Wire unchanged.
The mingw factory takes test ports from env `FETCHJET_SYNC_UDP_PORT` / `FETCHJET_SYNC_TCP_PORT` /
`FETCHJET_SYNC_BIND_HOST` (set by `test_devices.py`, so the corehost run never binds 28461 / 28462).

| Check | Result |
|---|---|
| `run.sh sync` | 130/0 (first run, with the real-port checks); 125/0 after making those 5 opt-in (`realports=1`: they bind 28461 / 28462 on 127.0.0.4) |
| `run.sh syncjvm` (peer now compiles the shared sources, no text patching) | 44/0 |
| `corehost/test_devices.py` (debug `fjcore.dll` rebuilt) | 41/0; only 127.0.0.1:28491 bound (sampled) |
| Android emulator (`tests/winhelpers/emulator_sync.py`: winhelpers `syncpeer` mode -> `adb forward` -> emulator app) | 17/0: pairing with equal codes, link only (downloaded from a loopback server), finished file transfer (SHA-256 equal) |
| `:sharedLogic:compileKotlinJvm`, `compileKotlinMingwX64`, `compileKotlinIosSimulatorArm64` (klib, on Windows), `:androidApp:assembleDebug`, `:desktopNativeCore:linkDebugSharedMingwX64` | BUILD SUCCESSFUL |

Linux: `DeviceSyncFactory.linux.kt` is still the inert placeholder; a real one now needs only a
platform file (ktor-network works on linuxX64; the Windows file's ktor part could move to `nativeDesktopMain`).

## Linux port, second round (2026-11-10, night)

Details and commands: `LINUX.md`. Same rule as before: one code base, no Windows / Linux copies.

- **HTTP engine on Linux** = libcurl through `dlopen` (`platform/linux/LinuxCurl*.kt`), replacing
  ktor CIO, whose Kotlin/Native TLS is a stub. HTTPS, HTTP proxy with login (CONNECT), SOCKS5 with
  login, ranges, cancel closes the connection. The shared proxy pieces of Windows
  (`WinLoopbackProxy`, `WinSocksForwarder`) are Winsock code, not pure Kotlin: not reused; curl
  does proxies itself.
- **Process layer shared**: `DesktopOs.startProcess` + `runProcess` (`DesktopProcess.kt`);
  Windows wraps `WinProcess`, Linux is `posix_spawnp` with a process group. **Media moved to
  `nativeDesktopMain`** (5 files out of `mingwX64Main`, the Linux placeholder deleted); tool
  download picks the Linux assets like the JVM app (`yt-dlp_linux.zip`, ffbinaries `linux-64`,
  deno linux zip); zip through the new shared `DesktopZip` (okio + zlib). Tools were not
  downloaded in any test. Fake yt-dlp builds for Linux (`tests/fakes/build.sh`).
- **Device sync on Linux is real**: `LinuxDeviceSyncPlatform.kt` (ECDH P-256 / AES-GCM / SHA-256
  through libcrypto, okio files, `getifaddrs` broadcast addresses, host name). The ktor-network
  part of the Windows file moved to `nativeDesktopMain/.../device_sync/KtorSyncNetwork.kt`
  (`nativeDeviceSync()`), used by both; `winhelpers/build.gradle.kts` got that folder as a source
  dir (one line).
- Linux: start at login (`~/.config/autostart/fetchjet.desktop`), sleep / shut down
  (`systemctl`, `loginctl`; never run), browser list (shared `DesktopBrowsers`), updater asset
  pick (`.deb` / linux `.tar.gz`, no in-app install), clipboard via wl-clipboard / xclip / xsel
  when present (none in WSL: not run), reveal through `FileManager1.ShowItems`.
- Host: dark mode from the portal / gsettings (`FETCHJET_THEME` forces it), `notify-send`
  notices, zenity / kdialog dialogs (not present in WSL: not run), no tray (close quits).
- Fix in the core: `test_add` "only the link stays" failed on Linux because forcing the URL field
  did not rebuild the Add snapshot when the dialog state was unchanged (it passed on Windows by
  timing). `EchoField.forced` is now part of the snapshot's `combine`.
- Torrent: `desktopNative/torrent/CMakeLists.txt` has a Linux branch; `libfjtorrent.so`
  (8,678,656 B, 34 `fjt_*` exports, libtorrent 2.1.2 with Boost 1.83 headers, links libssl /
  libcrypto / libz / libzstd) builds in WSL; ctypes smoke: ABI 1, `.torrent` parse (name, info
  hash, size), session create / destroy OK. **Not loaded by the app on Linux**: the loader and
  engine (`WinTorrentLibrary.kt`, `WinTorrentEngine.kt`) are in `platform/win`, which
  `winhelpers` compiles alone; sharing them needs that test build changed (LINUX.md 5.1).
- Packaging: `desktopNative/packaging/linux/build-linux.sh` -> `FetchJet-1.4.0-linux-x64.tar.gz`
  (25 MB) and `fetchjet_1.4.0_amd64.deb` (19 MB) with the release core (23.4 MB). The portable
  folder was unpacked and run under WSLg: a download ran at 2.9 MB/s (throttled server),
  RssAnon 23.3 MB, VmRSS 78 MB.

| Check | Result |
|---|---|
| `:sharedLogic:compileKotlinLinuxX64` + `compileKotlinMingwX64`, `:desktopNativeCore:linkDebugSharedLinuxX64`, `linkReleaseSharedLinuxX64`, `linkDebugSharedMingwX64` | BUILD SUCCESSFUL |
| `winhelpers` `compileKotlinMingwX64` (shared `KtorSyncNetwork` folder added) | BUILD SUCCESSFUL |
| Linux host (WSL, CMake) with the new platform services | builds, runs |
| Linux corehost | download 29/0 (HTTP) and 29/0 (HTTPS 18443, `FJ_TEST_SERVER`), home 41/0, add 29/0, media 31/0 (fake yt-dlp), settings 87/0, bridge 33/0 + 1 skip (Edge not installed in WSL), links 17/0, launch 11/0, network 17/0, devices 41/0, sync loopback 6/0 + 3/0 |
| Windows corehost (debug core, port 28473, work `E:\tools\fjhome\linuxport-win`) | download 29/0, home 41/0, add 29/0, media 31/0, settings 87/0, bridge 34/0, links 17/0, torrent 17/0, network 11/0 + 1 skip (HTTPS through the proxy: test certificate not trusted by WinHTTP), launch 11/0 |
| Screenshots `E:\tools\probes\ui\shots-linux\` | `home-dark.png`, `home-light.png`, `add-dialog-dark.png`, `settings-light.png`, `arabic-rtl-download.png`, `portable-release-dark.png` |

Still wrong on screen (WSL fonts: DejaVu, Ubuntu only): 23 of the 51 symbols the UI uses have no
font (`fc-list :charset=`): U+23F1 U+23F3 U+23F8 U+2705 and the emoji U+1F389 U+1F422 U+1F4AC
U+1F4BE U+1F4C1-U+1F4C4 U+1F4CB U+1F4DD U+1F4E4-U+1F4E6 U+1F504 U+1F517 U+1F551 U+1F552 U+1F5D1
U+1F9F2. Seen: Pause all / row pause button and the link / file / folder icons of the Add
dialog are empty; arrows, check marks, sun / moon, warning sign draw (DejaVu). Try with
`fonts-noto-color-emoji` unpacked into `~/fetchjet-native/fonts-root` and a private
`FONTCONFIG_FILE` (`fc-list` then finds Noto Color Emoji): the icons stay empty
(`add-dialog-noto-emoji.png`). Inconclusive: Slint may scan font folders itself and ignore
`FONTCONFIG_FILE`; a real check needs the font in `~/.local/share/fonts` or `/usr/share/fonts`
(not done: outside `~/fetchjet-native`).

Also run after the refactor: `winhelpers` `run.sh sync` 125/0 (work `E:/tools/probes/linuxport/r2/wh-work`).

Downloads this round: `libboost1.83-dev` 10,731,924 B, `fonts-noto-color-emoji` 9,967,220 B
(both `apt-get download`, unpacked under `~/fetchjet-native`, nothing installed).

## Linux port, third round + Windows re-verification (2026-11-10, night)

19:50-21:20, one agent. Builds through `one-build.sh` (normal priority, `--offline`), WSL `-j8`;
only 127.0.0.1 used; scratch homes `E:\tools\fjhome\r3*`, WSL `~/fetchjet-native/work`; the
owner's window (`build\Release`, port 28450) was not touched. Logs and scripts:
`E:\tools\probes\linuxport\r3`. Details and commands: `LINUX.md` (round 3).

What changed (one code base, no Windows / Linux copies):

- **One bundled font and bundled icons on both OSes** (owner's decision during the round):
  FetchJet Sans (Noto Sans + Noto Sans Arabic + DejaVu symbols, 4 weights, 1.46 MB) in
  `res/fonts`, registered at start by `host/app.cpp`; `Theme.font` / new `Theme.symbol-font`
  replace "Segoe UI" / "Segoe UI Symbol" (Chinese keeps YaHei); pause / hourglass / browser icons
  drawn by the shared `Glyph` widget; colour emoji = 160 bundled pictures from Noto Color Emoji
  (`res/emoji`, ~320 KB), the Windows DirectWrite atlas only as fallback. `shot.exe` got
  `--font-dir` / `--emoji-dir`. Licences in `THIRD-PARTY.txt`. Stage, MSI, MSIX, `.deb`, `.tar.gz`
  carry `fonts\` and `emoji\`.
- **Torrents shared**: `platform/torrent/FjtLibrary.kt` + `FjtEngine.kt` + shared backend; per OS
  only the loader host (`WinTorrentHost`, `LinuxTorrentHost`). `libfjtorrent.so` is loaded on Linux
  and packaged. `fjtseed.cpp` builds for Linux and Windows (POSIX block); `test_torrent.py` runs on
  Linux.
- **Deleted Windows copies**: `winRunProcess`, `WinLineSplitter`, `WinProcessResult`,
  `winStartDetached`, `WinZip.kt`, `WinTorrentEngine.kt`, `WinLoopbackProxy.kt`,
  `WinSocksForwarder.kt`, `TorrentBackend.mingw.kt` / `.linux.kt`,
  `MediaThrottlingProxy.mingw.kt` / `.linux.kt`. App and `winhelpers` tests use the shared code;
  `winhelpers` now also compiles `platform/desktop`, `platform/torrent`, `platform/proxy`.
  The core's last `mingwX64Main` files moved to `nativeDesktopMain` (`gen.py`: "nothing changed").
- **Bug found and fixed**: the shared `DesktopZip` read zips through okio, whose Windows file
  handle failed every read ("okio.IOException: The operation completed successfully"); the media
  tools could not have been unpacked on Windows. Now `DesktopOs.openFile` / `OsFile` (Windows
  `CreateFileW` + offset `ReadFile` / `WriteFile`, Linux okio), plus `DesktopOs.writeBytes` /
  `copyFile`, also used by the tool download and the extension copy (no okio file call left on
  Windows). `run.sh zip` 13/0 (copy to an Arabic file name included).
- **Loopback proxy shared** (`platform/proxy/LoopbackProxy.kt`, `SocksForwarder`) over
  `WinLoopSockets` / `LinuxLoopSockets`; Linux yt-dlp now gets the speed-limit proxy. Debug calls
  `debug.proxyStart` / `debug.proxyStop` and the new `test_proxy_limit.py` (both OSes).
- Linux host: file dialogs try xdg-desktop-portal through `gdbus` first (compiled, not run: no
  session bus in WSL). Tray: skipped, the sysroot has no libdbus / sd-bus (close still quits).
- CI: `linux-native` job next to `windows-native` (never started).
- Small test fixes: `test_shell.py` takes `FJ_HOST_EXE`; `test_torrent.py` runs on Linux;
  `ProcessTests` expects `OsProcessException`; winhelpers failures print the stack.

| Build | Result |
|---|---|
| `:sharedLogic:compileKotlinMingwX64` / `LinuxX64`, `:desktopNativeCore:link{Debug,Release}Shared{MingwX64,LinuxX64}` | BUILD SUCCESSFUL; final (21:22) `fjcore.dll` 21,225,472 / 42,621,440 B, `libfjcore.so` 23,634,872 / 46,239,496 B |
| `winhelpers` debug, `shot.exe`, `build-release.cmd`, debug-core host (`E:\tools\probes\linuxport\winhost`) | EXIT 0 |
| WSL: `libfjtorrent.so` 8,678,656 B, `fjtseed` (new), Linux host | EXIT 0 |
| Windows `fjtseed.exe` from the shared source (both build folders); shipped `fjtorrent.dll` unchanged (SHA-256 `239895b529b8...`) | EXIT 0 |

| Windows suites | release core | debug core |
|---|---|---|
| download, home, add, media | 29/0, 41/0, 29/0, 31/0 | 29/0, 41/0, 29/0, 31/0 |
| settings, links, devices, launch | 87/0, 17/0, 41/0, 11/0 | 87/0, 17/0, 41/0, 11/0 |
| torrent, network, proxy_limit (new), bridge (alone) | 17/0, 11/0 + 1 skip, 13/0, 34/0 | 17/0, 11/0 + 1 skip, 13/0, 34/0 |
| host/test_shell (release EXE / debug-core EXE) | 8/0 | 8/0 |
| winhelpers (debug): zip, process, net, proxy, sync, syncjvm, torrent | 13/0, 17/0, 46/0, 37/0, 125/0, 44/0, 37/0 | |

The first pass of home / add / settings after the proxy change failed 1 / 1 / 8 checks on both
OSes only because the run script exported `FETCHJET_TORRENT_DLL` for every test (those tests
expect torrents hidden); rerun without it: all pass. Before the proxy / zip change the full
release and debug runs had also passed (same counts, without proxy_limit).

| Linux suites (WSL, debug core) | Checks / failed |
|---|---|
| download (HTTP), download (HTTPS 18443) | 29/0, 29/0 |
| home, add, media, settings, links | 41/0, 29/0, 31/0, 87/0, 17/0 |
| launch, network, devices, bridge | 11/0, 17/0, 41/0, 33/0 + 1 skip |
| torrent (new on Linux), proxy_limit (new), sync loopback | 17/0, 13/0, 3/0 |

After the last change (okio file calls -> `DesktopOs`, final core 21:22) a subset ran again, all
passing: Windows release download / settings / bridge / media / torrent / proxy_limit / shell,
debug settings / bridge / download; Linux download / settings / media / proxy_limit / bridge /
torrent. The RAM numbers below are from the core before that last change (21:00).

One earlier Linux media run failed 1 check while the Windows release host was being built at
the same time; rerun 31/0.

| RAM, Windows release EXE, hidden desktop (`ram\speedrun.py`) | Private MB median / max |
|---|---|
| idle 60 s | 23.6 / 27.2 |
| 300 MB from 127.0.0.1, unlimited (13 samples) | 30.9 / 31.4 (working set max 69.5); SHA-256 equal |
| 60 MB at 1 MB/s (1008.5 KB/s measured over 55 s) | 45.9 / 51.8 (working set max 89.4); SHA-256 equal |

Linux portable release build during a download: RssAnon 24.5 MB, VmRSS 78.6 MB.

Packages: stage 220 files (56 + 4 fonts + 160 emoji), 67,220,739 B; `FetchJet-1.4.0.msi`
20,644,565 B, `check-msi.ps1` all passed (220 = stage, tree SHA-256 equal);
`FetchJet-1.4.0-store.msix` 26,893,264 B unsigned, `check-msix.ps1` all passed (219);
`FetchJet-1.4.0-linux-x64.tar.gz` 29,915,619 B, `fetchjet_1.4.0_amd64.deb` 22,608,094 B (with
`libfjtorrent.so`, fonts, emoji). Nothing installed. `stage.ps1` prints a warning that
`FetchJet.exe` is older than `host/linux/platform_linux.cpp`: that file is not part of the
Windows EXE.

Pictures: Windows before / after `E:\tools\probes\linuxport\r3\before`, `...\after` (home dark /
light / Arabic / Chinese, settings, Add dialog English / Arabic, media, details, alerts): same
layout, Noto glyphs, the clipboard note on Settings > General now wraps to two lines, the ▾
arrows are slightly larger; nothing clipped. Linux `E:\tools\probes\ui\shots-linux\r3-*.png`:
the pause buttons, link / file / folder icons, emoji and Arabic now draw as on Windows.

Downloads this round: Noto Sans 4 weights (1,726,416 B), Noto Sans Arabic 4 weights (568,176 B),
OFL.txt (4,396 B), `fonttools-4.68.0-py3-none-any.whl` (1,248,357 B).

Not done: tray on Linux (no D-Bus library in the sysroot), portal dialogs not run, any real
desktop / install / internet run (media tools download, real torrents, a second device).

## Linux port, fourth round: file paths, toasts, tray (2026-11-10, late night)

21:25-22:20, one agent. Builds through `one-build.sh` (`--offline`), WSL `-j8`; only 127.0.0.1;
scratch homes `E:\tools\fjhome\r4-*` (Arabic long folders under `E:\tools\fjhome\r4-debug\عربي تجربة`),
WSL `~/fetchjet-native/work`; the owner's window (`build\Release`, port 28450) was not touched.
Logs and scripts: `E:\tools\probes\r4`. No downloads from the internet this round.

1. **Paths with Arabic names and over 260 characters (Windows).** Every okio file call the
   native targets compile (28 commonMain files, 252 calls: part files, `.chunks`, the merge,
   `placeReceivedParts`, tasks cache, settings, bridge / network / schedule files, media steps,
   torrents, updates) went to okio `FileSystem.SYSTEM`. New `winhelpers paths` test (same steps on
   an Arabic folder, short and 305 characters): okio fails 12 of 31 checks, all on the long folder, in a plain
   process; under the app's own manifest (`activeCodePage` UTF-8 + `longPathAware`, with
   `LongPathsEnabled=1` on this PC) it still fails 2: `list` returns the current folder's files
   and `deleteRecursively` fails ("Permission denied"); short Arabic paths worked. Fix: shared
   `DesktopFileSystem` (okio `FileSystem` over `DesktopOs`: `CreateFileW` with `\\?\` paths on
   Windows, okio on Linux), `DesktopOs.openFile(path, write, OsOpen)`, `OsFile.resize`; commonMain
   uses `core/filesystem/AppFileSystem` (`expect val`): `DesktopFileSystem` on Windows / Linux,
   `FileSystem.SYSTEM` on JVM / Android / iOS (unchanged). Linux `OsFile.read` now returns 0 at the
   end of a file (it returned -1, which `copyFile` read as an error). Evidence: `winhelpers paths`
   34/0 with `DesktopFileSystem` (plain process and app manifest); new corehost `test_paths.py`
   (download with pause / resume into an Arabic folder of 285 characters, part files, merge,
   remove, tasks cache, settings, log) 17/0 on Windows and Linux.
2. **English "Started download" toast in the Arabic picture**: not a missing translation (ar has
   `sysStartedDownload`; all 14 toasts are built from `LocalizationManager.Strings`). The picture
   scenario switched to Arabic while toasts made in English were still queued (each stays 4 s).
   Small real gap fixed: the presenter drops queued toasts and hides the current one when the
   language changes (`Presenter.dropToasts`). New `home-light-ar` picture: no English toast.
3. **"Preparing file" bar colour**: `barColorFor` (primary while preparing) was in the code but
   never built; built now. `test_devices` has 2 new checks (primary bar for a handed-over and a
   link-only receive); picture `E:\tools\probes\r4\shots\row-preparing-dark.png`: the row in
   "Waiting" has the cyan bar.
4. **Linux tray**: WSL has `dbus-daemon` / `dbus-run-session` and Python Gio, so a private session
   bus works without sudo. New `host/linux/tray_linux.cpp` (StatusNotifierItem + dbusmenu over
   GDBus, `libgio-2.0.so.0` by `dlopen`, own thread). Test `tests/host/test_tray_linux.py` (fake
   watcher + fake FileChooser portal): 20/0, including the portal folder dialog returning an
   Arabic folder that lands in `settings.properties` (the portal code had never run before).

| Build | Result |
|---|---|
| `:sharedLogic:compileKotlinMingwX64` / `LinuxX64`, `:desktopNativeCore:linkDebugShared{MingwX64,LinuxX64}` | BUILD SUCCESSFUL; `fjcore.dll` 42,679,296 B, `libfjcore.so` 46,303,840 B |
| `:sharedLogic:compileKotlinJvm`, `compileKotlinIosSimulatorArm64`, `:androidApp:compileDebugKotlin` | BUILD SUCCESSFUL |
| `winhelpers` debug; Linux host (WSL, with the tray) | EXIT 0 |

| Windows (debug core) | Checks / failed |
|---|---|
| download, home, add, media, settings, links | 29/0, 41/0, 29/0, 31/0, 87/0, 17/0 |
| devices (+2), launch, torrent, network, proxy_limit, paths (new) | 43/0, 11/0, 17/0, 11/0, 13/0, 17/0 |
| bridge (alone), host/test_shell (alone, debug-core EXE) | 34/0, 8/0 |
| winhelpers zip, process, net, proxy, sync, torrent, paths (new) | 13/0, 17/0, 46/0, 37/0, 125/0, 37/0, 34/0 |

| Linux (WSL, debug core) | Checks / failed |
|---|---|
| download HTTP / HTTPS, home, add, media, settings, links | 29/0, 29/0, 41/0, 29/0, 31/0, 87/0, 17/0 |
| launch, network, devices, proxy_limit, paths (new), torrent | 11/0, 17/0, 43/0, 13/0, 17/0, 17/0 |
| bridge, sync loopback, tray + portal (new) | 33/0 + 1 skip, 3/0, 20/0 |

Test notes: the first `winhelpers proxy` / `torrent` run failed (9 / 1) only because of the test
set-up: proxy servers from a morning run (work `E:\tools\probes\winhelpers-work`, not stopped:
not this round's) share the ports with `SO_REUSEADDR`, so the logs the test reads were the other
folder's; and `FETCHJET_TORRENT_DLL` was not set. Rerun with the default work folder and the
library: 37/0, 37/0. WSL's test servers are reachable from Windows through localhost forwarding:
stop them before Windows proxy tests.

Not done: the tray on a real panel (KDE, GNOME + AppIndicator) and a real xdg-desktop-portal; the
release cores were not rebuilt this round; the media tools download still never ran (internet).

## Final builds (2026-11-10, night)

22:12-22:30, one agent, no product code or script changed. Builds through `one-build.sh`
(`--offline`, normal priority), WSL `-j8`; only 127.0.0.1 (the Windows test servers of the morning
run were reused); scratch homes `E:\tools\fjhome\fn-*`, WSL `~/fetchjet-native/work`; the owner's
window (`build\Release`, port 28450) was not touched. Scripts and logs: `E:\tools\probes\final-night`.

| Build | Result |
|---|---|
| `:desktopNativeCore:linkReleaseShared{MingwX64,LinuxX64}` | EXIT 0, `fjcore.dll` 21,256,704 B, `libfjcore.so` 23,667,576 B (22:17) |
| `packaging\build-release.cmd` | EXIT 0, `FetchJet.exe` 5,046,784 B (20:23, not relinked: no Windows host change), `fjtorrent.dll` 9,906,176 B (SHA-256 `239895b529b8...`), `fonts\`, `emoji\` in place |
| WSL host, `libfjtorrent.so` (8,678,656 B), `fjtseed` | up to date, EXIT 0 |
| `stage.ps1 -RequireFreshCore` | 220 files, 67,251,971 B (warning only for `host/linux/tray_linux.cpp`, not part of the Windows EXE) |
| `FetchJet-1.4.0.msi` | 20,660,949 B, `check-msi.ps1` all passed (220 = stage) |
| `FetchJet-1.4.0-store.msix` (unsigned) | 26,898,733 B, `check-msix.ps1` all passed (219) |
| `FetchJet-1.4.0-linux-x64.tar.gz` / `fetchjet_1.4.0_amd64.deb` | 29,957,904 / 22,630,478 B, copied to `E:\tools\probes\final-linux\`; packaged `libfjcore.so` SHA-256 = the fresh release core |
| Standalone Windows folder `E:\tools\probes\final-win\app` | the stage without `FetchJet.installed`: 219 files, 67,251,971 B |

| Windows, release core (`FJ_TEST_RELEASE=1`) | Checks / failed |
|---|---|
| download, home, add, media, settings, links | 29/0, 41/0, 29/0, 31/0, 87/0, 17/0 |
| devices, launch, torrent, network, proxy_limit, paths | 43/0, 11/0, 17/0, 11/0, 13/0, 17/0 |
| bridge (alone), host/test_shell (alone, `final-win\app\FetchJet.exe`) | 34/0, 8/0 |

| Linux (WSL), release core | Checks / failed |
|---|---|
| download, home, add, settings, paths, torrent | 29/0, 41/0, 29/0, 87/0, 17/0, 17/0 |

| RAM, `final-win\app\FetchJet.exe`, hidden desktop | Private MB median / max |
|---|---|
| idle 60 s | 24.0 / 26.8 |
| 300 MB unlimited (14 samples) | 30.6 / 31.9 (ws max 69.9); SHA-256 equal |
| 300 MB at 1 MB/s, 125 s sampled (127.5 MB done, 1024.0 KB/s) | 55.6 / 62.2 (ws max 98.2); file not finished, so no SHA-256 |

The 1 MB/s number is higher than the 60 MB runs of earlier rounds (45.9 / 51.8): over a longer
download the private memory keeps creeping up; still over the 50 MB target with a limit on.

Linux portable (`.tar.gz` unpacked in `~/fetchjet-native/work/final-portable`, WSLg, scratch
home): 64 MB from the WSL test server (8 x 500 KB/s) in ~21 s, SHA-256 equal to the server;
RssAnon 25.3-26.5 MB during the download (30.7 MB at the end), VmRSS ~82 MB. Pictures
`E:\tools\probes\final-linux\portable-download-dark.png` (47 %, 2.98 MB/s) and
`portable-done-dark.png`; samples `rss.txt`.

Not done: anything installed, signed or on a real desktop; internet downloads.

## RAM with a speed limit: cause found and fixed (2026-11-11, 00:30)

Cause: ktor's `bodyAsChannel()` copies the engine's body into its own `ByteChannel`
(`DefaultTransform.kt`, up to 1-2 MB per connection). With a speed limit the 8 connections fill
these buffers: 13-18 MB of live heap, and the GC kept the same again as reserve.

Fix: `core/network/DownloadBody.kt` (`HttpResponse.downloadChannel()`): the download loops
(`HttpChunkWorkers`, `PieceDownloader`, `MediaOwnEngine`) read the engine's channel directly when
`DownloadBody.direct` is on. Only the native desktop core turns it on (`runtime/MemoryTuning.kt`,
which also sets `GC.targetHeapUtilization = 0.8`); JVM, Android and iOS are unchanged. Env
`FETCHJET_BODY_COPY=1` goes back to the old path; `FETCHJET_GC_*` are for measuring.

Measured with the release core inside `corehost` (no window, 8 connections, local server,
script `E:\tools\probes\gc\measure.py`), private MB median / max:

| Case | Before | After |
|---|---|---|
| idle | 19 | 19 |
| no limit (50 MB/s) | 26.2 / 27.4 | 26.2 / 26.6 |
| limit 1 MB/s, 90 s | 50.2 / 57.1 | 26.7 / 27.3 |
| limit 30 MB/s, 40 s | 54.6 / 56.2 | 27.6 / 28.7 |

Live heap after a collection with a limit: 13-18 MB before, 3 MB after. Release suites after the
change: download 29/0, add 29/0, links 17/0, media 31/0, network 11/0.

Not done: the debug core and the host EXE were not rebuilt (the owner's open window is the old
build), no measurement inside the EXE, Linux not rebuilt, torrent / proxy_limit / devices suites
not re-run.

Later the same night: `build-release.cmd` EXIT 0 (core 00:38, EXE not relinked). Inside the
release EXE (hidden desktop, `speedrun.py`, 300 MB at 1 MB/s, 92 s, 1024.2 KB/s): private
29.0 / 30.2 / 30.8 MB (min / median / max), idle 24.9; it was 55.6 / 62.2 before. Release suites
re-run on the new core: home 41/0, settings 87/0, proxy_limit 13/0, devices 43/0, paths 17/0,
launch 11/0, torrent 17/0. Still not done: debug core + the owner's window, Linux rebuild,
bridge and test_shell.

## 2026-11-11: macOS port (Mac session, commit `02289bf`)

- `macosArm64` in sharedLogic and `:desktopNativeCore`; Mac actuals in `sharedLogic/src/macosArm64Main`
  (DesktopOs/DesktopApp, MacProcess with `posix_spawnp` via dlsym, MacCrypto with
  `CCCryptorGCMOneshot*`, Keychain wrap key, BSD loop sockets, device sync with Security ECDH,
  `MacTorrentHost`, updater swapping the .app from the dmg, LaunchAgent start at login,
  LaunchServices default handler). Host `host/mac/platform_mac.mm` (tray, NSOpenPanel, reopen /
  open-URL / open-documents Apple events installed at process start, Services item).
- Shared changes made for it: `DesktopToolAssets` (per-OS media tool downloads; the Mac used to
  fetch the Linux builds), `BrowserLocator` (per-OS browser search), `SharedSecretKey` (Mac reads
  the JVM Keychain key), `NativeApp.call("openUrl")`, accessibility roles in `ui/`,
  `DEVICE_SYNC_READY = true`, `displayChunks()` (paused pieces not shown active).
- Measured on the Mac: 56-66 MB window open, 34 MB started in the tray, ~100 MB downloading.
  Tested items: see ROADMAP-1.4.0.md, macOS entry. Not run: Windows / Linux with these changes.

