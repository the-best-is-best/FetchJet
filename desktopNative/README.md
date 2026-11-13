# FetchJet native desktop: where things go

Developer map for the native desktop app (Windows, Linux and macOS). Read this before adding
anything. The rules are in `ARCHITECTURE.md`; what works and what is still open is in
`docs/port/STATUS.md`. If a rule here and the code disagree, fix the document in the same change.

## 1. Quick answers: where does a new thing go?

| You want to add | Put it here |
|---|---|
| Download / network logic that is the same on every platform | `sharedLogic/src/commonMain/.../feature/` or `core/network/` |
| A piece that differs per OS (files, process, socket, lock) | `DesktopOs` (`sharedLogic/src/nativeDesktopMain/.../platform/desktop/DesktopOs.kt`, actual per OS: `mingwX64Main/.../platform/desktop/DesktopOs.mingw.kt`, `linuxX64Main/.../platform/desktop/DesktopOs.linux.kt`). Add the function to the expect, then both actuals |
| Windows-only API call (Win32, WinHTTP, registry) | `sharedLogic/src/mingwX64Main/.../platform/win/` (`WinFiles`, `WinSystem`, `WinProcess`, ...) |
| Linux-only API call (libcurl, D-Bus, flock) | `sharedLogic/src/linuxX64Main/.../platform/linux/` |
| Code shared by Windows and Linux only (not JVM / Android / iOS) | `sharedLogic/src/nativeDesktopMain/...` |
| A new HTTP call from the app | Through `NetworkClient { useNetworkSettings() }` (or `openAppConnection` on JVM). Never a bare client: proxy and site logins depend on it |
| A new download body reader | `HttpResponse.downloadChannel()` (`core/network/DownloadBody.kt`), not `bodyAsChannel()`. It keeps the native path free of ktor's copy buffer |
| A new secret written to disk | `core/security/Secrets.kt` (`Secrets.protect` / `reveal`) |
| A new UI string | `AppLocalization` (`sharedLogic/.../core/i18n/data_provider/AppLocalization.kt`), then every provider (the build fails until each has it). Engine status texts stay English and are translated by `localizeStep()` |
| A new field the UI shows (a row, a count, a label) | 1) `desktopNative/core/src/nativeDesktopMain/.../nativeapp/dto/<Area>Dto.kt` (the DTO), 2) `presenter/<Area>Presenter.kt` (fills it), 3) `desktopNative/ui/pages/<Page>.slint` (draws it). Then run `python desktopNative/tools/gen/gen.py` and commit the generated files |
| A new button or click in the UI | `actions/UiAction.kt` (the action), `presenter/ActionDispatcher.kt` (routes it), `presenter/<Area>Presenter.kt` (does it), and the `.slint` page that sends it. Then run the generator |
| A new settings row | `presenter/SettingsRows.kt` (row model), `presenter/SettingsActions.kt` (what a click does), `ui/pages/Settings.slint` (drawing) |
| A new page or dialog | `desktopNative/ui/pages/<Name>.slint`, opened from `app.slint`. A `PopupWindow` goes in its own component, never inside an `if` or `for` |
| A reusable widget (button, text field, menu, scroll) | `desktopNative/ui/widgets/` |
| A new C API function for the host | `Exports.kt` (the `@CName` function), `host/core_api.h` (the header), then the generator if a DTO or action is involved |
| A new endpoint for the browser extension | `nativeapp/bridge/BridgeServer.kt` (and the token check in the same file). Then `tests/corehost/test_bridge.py` |
| A new device-sync behaviour | Protocol and state machine: `sharedLogic/.../commonMain/.../feature/device_sync/` (once, for every OS). Platform sockets and crypto: `WinDeviceSyncPlatform.kt` / `LinuxDeviceSyncPlatform.kt` / `JvmDeviceSyncPlatform.kt` |
| A new torrent feature | `desktopNative/torrent/` (C++ over libtorrent, builds `fjtorrent.dll` / `libfjtorrent.so`), and the Kotlin side `sharedLogic/.../platform/torrent/FjtEngine.kt` |
| A new keyboard shortcut in the list | `ui/pages/TaskList.slint` (a `KeyBinding`, plus its Arabic / Persian / Russian letters: the key text follows the layout), the key code in `ListKeyCode` (protocol), `presenter/ListKeys.kt` and the `ListKey` case in `presenter/ActionDispatcher.kt`, the row in `ui/pages/Shortcuts.slint` and its string in `AppLocalization`. Test: `tests/corehost/test_keys.py` |
| The ▶ / ↗ button of a finished row | `ui/pages/TaskRow.slint` (`QuickAction.play`: `true` when the row's tag is `VID` or `AUD`), the same rule in Compose `TaskRows.kt` and SwiftUI `TaskRowView.swift`. Screenshot: `tests/corehost/shots_playbtn.py` |
| The window size on show | `host/win/platform_win.cpp` `fit_to_work_area()` (called from `show_window` in `host/app.cpp`, empty on Linux / macOS); the repaint guard on resize is the first `Rectangle` in `ui/app.slint` (`resize-stamp`, slint#13537) |
| Copy / paste / cut / select all in a text field | `ui/widgets/TextField.slint` (both `TextField` and `TextArea`): Slint matches the key's text, so the other layouts' letters are listed there |
| A new proxy / SOCKS behaviour | `sharedLogic/.../platform/proxy/LoopbackProxy.kt` (shared), socket layer per OS (`WinLoopSockets.kt`, `LinuxLoopSockets.kt`) |
| A new packaged file (font, icon, licence) | `desktopNative/res/` (icon, fonts, emoji), `packaging/licenses/THIRD-PARTY.txt` (every dependency) |
| A host change (tray, dialogs, focus, single instance) | `desktopNative/host/win/platform_win.cpp`, `host/linux/platform_linux.cpp` or `host/mac/platform_mac.mm` (OS part), `host/app.cpp` (shared part) |
| macOS-only API call (Darwin HTTP, Keychain, LaunchServices, posix_spawn) | `sharedLogic/src/macosArm64Main/.../platform/macos/` and the `*.macos.kt` actuals; Mac packaging in `desktopNative/packaging/mac/` (`make-dmg.sh`, `Info.plist.in`), torrent lib `desktopNative/torrent/build-mac.sh` |

## 2. Layout

```
sharedLogic/src/
  commonMain/        engines and logic shared with JVM, Android and iOS
  nativeDesktopMain/ shared by Windows and Linux only (DesktopOs, the proxy, device-sync glue)
  mingwX64Main/      Windows layer (platform/win/*, DesktopOs.mingw.kt, Win* actuals)
  linuxX64Main/      Linux layer (platform/linux/*, DesktopOs.linux.kt, Linux* actuals)
desktopNative/
  ARCHITECTURE.md    the contract: threads, C API, UI protocol, rules
  README.md          this file
  core/              :desktopNativeCore -> fjcore.dll / libfjcore.so (the Kotlin side of the UI)
    src/nativeDesktopMain/.../nativeapp/
      Exports.kt         C API
      NativeApp.kt       start order, host wiring, memory tuning call
      Launch.kt          arguments, single instance
      DebugCalls.kt      `debug.*` calls (tests and screenshots only)
      runtime/           dispatcher, change notifier, crash log, MemoryTuning (GC settings)
      channels/ dto/     snapshot channels and their DTOs (dto/Channels.kt is the protocol list)
      actions/           UiAction (everything the UI can ask for)
      presenter/         state logic ported from sharedUI
      bridge/            127.0.0.1:28450 server for the browser extension
      host/              calls into the C++ host (dialogs)
      generated/         generated string table
  host/              C++ host (Slint window, tray, dialogs). app.cpp is shared, platform_*.cpp per OS
    generated/         generated C++ (bridge parsers, row diff, action senders)
  ui/                Slint markup: app.slint, pages/, widgets/, theme.slint
    generated/         generated Slint and protocol.json (do not edit by hand)
  tools/gen/gen.py   the generator (DTOs, actions, strings)
  tools/shot/        screenshots of any .slint component; deskrun.exe (hidden desktop)
  tools/testserver/  local HTTP / HTTPS server and proxies: tests never use the internet
  tools/one-build.sh runs one build at a time (the owner uses the PC)
  torrent/           fjtorrent.dll / libfjtorrent.so: C API over libtorrent
  packaging/         stage folder, MSI (WiX), MSIX, Linux .deb and tar.gz
  res/               icon, .rc, manifest, fonts, emoji pictures
  tests/corehost/    Python loads the core without a window (test_*.py, shots*.py)
  tests/host/        the real EXE on a hidden desktop (test_shell.py, test_tray_linux.py)
  tests/winhelpers/  Kotlin test binaries for platform/win (run.sh modes)
  tests/fakes/       fake yt-dlp for media tests
  docs/port/         porting maps (01-12), probe reports (13-18), STATUS.md, LINUX.md, ACCEPTANCE.md
```

## 3. Build and test (short)

Core, Windows (release for measurements, debug while developing):

```
bash desktopNative/tools/one-build.sh ./gradlew.bat :desktopNativeCore:linkReleaseSharedMingwX64 -Pfetchjet.nativeDesktop=true
bash desktopNative/tools/one-build.sh cmd //c "desktopNative\packaging\build-release.cmd"   # EXE + DLLs
```

Core, Linux (cross-built on Windows, run in WSL): `docs/port/LINUX.md`, section 2.

Tests (no window, no internet; they start the core with `"automaticDownloads": false`):

```
cd desktopNative/tests/corehost
FJ_TEST_RELEASE=1 python test_download.py
```

Every new behaviour gets a check in the matching `test_*.py`. A new risky action (downloads, sleep,
clipboard) goes into `refuse_in_tests` in `corehost.py`.

## 4. Rules that are easy to break

- **Generated files:** after a DTO, action or string change run `gen.py` and keep its output in the same change.
- **One DTO change = core and host together.** A stale `fjcore.dll` next to a new EXE shows old data.
- **Memory:** a download reader must not add its own buffer per connection. Speed limits and many
  connections make those buffers pile up (see STATUS.md, "RAM with a speed limit").
- **Long Arabic paths on Windows:** file IO goes through `AppFileSystem` / `DesktopFileSystem`, not okio.
- **Device sync:** the protocol lives once in commonMain. Never copy it into a platform folder.
- **Reserved names:** DTO field names that are C++ keywords or Windows macros (`auto`, `small`, `near`) break the host build; `gen.py` refuses them.
- **Slint:** wrapped text inside `HBox` / `FlexboxLayout` is a binding loop; use `GridLayout`. `row` is a reserved name. Text fields must not live inside a nested model.
- **Port 28450 belongs to the installed FetchJet.** Tests use `"bridge": false` or port 28459.
- **Never install an MSI / MSIX on the development PC.** Use the Windows Sandbox route in ACCEPTANCE.md.

## 5. Where the status lives

- `docs/port/STATUS.md`: what works, what is measured, what is still open (newest at the bottom).
- `docs/port/PARITY.md`: feature-by-feature comparison with the Compose app.
- `docs/port/ACCEPTANCE.md`: manual test steps for the owner.
- `docs/port/LINUX.md`: Linux build and test in WSL.

## 6. Command line

`FetchJet [flags] [link ...]`. Flags are not case-sensitive; only these exact words are flags, so a
path such as `/home/x/file.torrent` is a file. A flag with a value takes the next argument. When a
copy is already open the command is handed to it through the bridge (`/download?cli=1...`, token
required, refused when the request has an `Origin`) and this copy exits. Code: `nativeapp/Launch.kt`,
checks: `tests/corehost/test_launch.py`.

| Flag | Long form | What it does |
|---|---|---|
| `/d URL` | `--download URL` | The link to add (http, https, magnet, `fetchjet://`, a `.torrent` file). Repeatable; a bare link is the same |
| `/p PATH` | `--path PATH` | Folder to save into |
| `/f NAME` | `--file NAME` | File name to save as (only with one link) |
| `/a` | `--add` | Add to the list paused, no Add dialog |
| `/n` | `--silent` | No Add dialog and no window: add and start at once. With the app closed it starts in the tray |
| `/s` | `--start-queue` | Start everything that waits (the same as "Resume all"), after the links of the same command were added |

Without `/n` and `/a`, the Add dialog opens with the link, `/p` and `/f` filled in (several links
open its link list). A video page always goes to the Add dialog: it needs a quality.
