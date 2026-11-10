# FetchJet native desktop: architecture contract

Status: DRAFT 3 (2026-11-10), brought in line with the code that exists. Every rule below that
names a report (`docs/port/13` ... `18`) was measured or tested there. What works and what is
still a placeholder is in `docs/port/STATUS.md`. Everybody who writes code for the native
app follows this file. If something here is wrong or missing, change this file first, then the
code.

## 1. Goal

The Windows desktop app without the JVM: the same features and the same look as the Compose
desktop app, at 50 MB RAM or less (the JVM app idles at about 250 MB).

- Logic: the existing Kotlin Multiplatform module `sharedLogic`, compiled with Kotlin/Native (target
  `mingwX64`) into `fjcore.dll`. Common code is not forked: the native app runs the same
  engines, the same view model and reads the same files in the app home as the JVM app.
- UI: Slint 1.18.1 (`.slint` markup, software renderer) with a small C++ host built with MSVC.
- Windows first. macOS and Linux come later with the same split (their own platform source sets).

## 2. Tree

```
sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/
    platform/win/        low-level Windows helpers: WinCore, WinFiles, WinCrypto, WinSystem,
                         WinProcess, WinZip, WinHttpEngine (reports 17, 18)
    core/... feature/... the mingwX64 actuals, in the same package as their expect
desktopNative/
    ARCHITECTURE.md      this file
    core/                Gradle module :desktopNativeCore -> fjcore.dll (depends on :sharedLogic)
        src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/nativeapp/
            Exports.kt       the C API (section 5)
            NativeApp.kt     start order, host wiring; Launch.kt: arguments and single instance
            DebugCalls.kt    `fj_call("debug....")`, only with `"debug": true` (tests, pictures)
            runtime/         HostMainDispatcher, InstallMain, ChangeNotifier, CrashLog, CStrings
            channels/        StructChannel, ListChannel (row diff), QueueChannel
            dto/             snapshot DTOs + Channels.kt: the source of truth of the UI protocol
            actions/         UiAction: everything the UI can ask for (section 7)
            presenter/       UI state logic ported from sharedUI (section 9)
            bridge/          the 127.0.0.1:28450 server for the browser extension
            host/            HostServices: calls into the C++ host (file and folder dialogs)
            generated/       generated Kotlin (string table)
    host/                C++ host: main.cpp (window, tray, single instance, effects),
                         core_api.h, json.h, bridge_support.h, emoji_atlas.h
        generated/       generated C++ (fj_bridge.h: parsers, row diff, action senders, strings)
    ui/                  theme.slint, widgets/, pages/, app.slint
        generated/       generated Slint (dto.slint, actions.slint, tr.slint) + protocol.json
    tools/gen/           the generator (one command: python tools/gen/gen.py)
    tools/shot/          off-screen screenshots of any .slint component; deskrun (hidden desktop)
    tools/testserver/    local HTTP / HTTPS server and proxies (no internet in tests)
    tools/one-build.sh   runs a build when no other build of this folder is running
    tests/winhelpers/    console tests of platform/win (run.sh)
    tests/corehost/      Python loads fjcore.dll without a window: test_*.py, shots*.py
    tests/host/          the real EXE on a hidden desktop (bridge, second instance)
    tests/fakes/         a fake yt-dlp for media tests without internet
    torrent/             fjtorrent.dll: C API over libtorrent (report 12)
    packaging/           stage folder, MSI (WiX), MSIX
    res/                 icon, .rc, application manifest
    docs/port/           maps of the code to port (01-12) and probe reports (13-18)
```

Generated files are checked in. After changing a DTO, a UiAction or `AppLocalization`, run the
generator and commit its output with the change.

## 3. Build

- The `mingwX64` target of `sharedLogic` (and of `:kmpLocalization`, added from the root build
  script without touching the submodule) exists only with `-Pfetchjet.nativeDesktop=true`. Without
  the property the JVM, Android and iOS builds are exactly as before.
- `:desktopNativeCore` is included by `settings.gradle.kts` under the same property.
- Core:
  `gradlew :desktopNativeCore:linkDebugSharedMingwX64 -Pfetchjet.nativeDesktop=true --priority low`
  (release: `linkReleaseSharedMingwX64`). Debug while developing (stack traces with names),
  release for measurements and shipping.
- Binary options: `binaryOption("pagedAllocator", "false")` (report 16: 5 to 11 MB where the
  default allocator keeps 22 to 53 MB; `-Xallocator=std` is deprecated) and
  `linkerOpts("-lole32")`. No cinterop `.def` files: the built-in `platform.windows`,
  `platform.winhttp` and `platform.zlib` cover the app (report 17).
- mingwX64 dependencies of `sharedLogic`: `ktor-network` only. **Not `ktor-client-winhttp`**: the
  app has its own engine (section 11).
- Host: CMake + MSVC (`desktopNative/build.cmd`), Slint from `E:/tools/slint` (`SLINT_DIR`),
  `slint_target_sources(... COMPILATION_UNITS 4)` so a host change recompiles in seconds
  (report 14).
- Only one heavy build runs on this PC at a time, at low priority (the owner uses the PC while
  they run). When more than one session works here, every build goes through
  `bash desktopNative/tools/one-build.sh <command>`.

## 4. Threads

- UI thread = the Slint event loop thread = Kotlin `Dispatchers.Main` (report 15). The host gives
  the core a `post` function; `HostMainDispatcher` wraps it and `installMainDispatcher` installs
  it in `fj_start`, **before the first `ViewModel` is created** (a view model created earlier
  keeps a scope without a dispatcher). The view model, the presenter and every `fj_*` call run on
  this thread, as on the other platforms.
- Engines keep using `Dispatchers.Default` / `Dispatchers.IO`.
- Nothing blocks the UI thread: no file, network, process or registry work on Main.
- The host never touches Kotlin objects; it only calls the C API. The core never touches Slint;
  it only publishes snapshots and calls host services (section 10).

## 5. C API (`host/core_api.h`, exported by `Exports.kt`)

All strings are UTF-8, NUL-terminated. Strings returned by the core are `malloc`-ed copies, freed
with `fj_free`.

```c
typedef void (*fj_post_fn)(void (*fn)(void*), void* arg);   /* run fn(arg) on the UI thread; callable from any thread */
typedef void (*fj_notify_fn)(unsigned int changed_channels); /* called on the UI thread: bit i = channel i has a new snapshot */
typedef char* (*fj_host_fn)(const char* name, const char* args_json); /* host service, called on the UI thread; result freed with fj_host_free */
typedef void (*fj_host_free_fn)(char* p);

char* fj_prepare(const char* config_json);      /* before the window exists: {"exit":true} when a running instance took over, else {"hidden":..,"url":..} */
int   fj_start(const char* config_json, fj_post_fn post, fj_notify_fn notify, fj_host_fn host, fj_host_free_fn host_free);
char* fj_snapshot(int channel);                 /* current snapshot of one channel as JSON (| 0x100 = a list in full) */
void  fj_action(const char* action_json);       /* {"type":"Pause","id":"..."} */
char* fj_call(const char* name, const char* args_json); /* synchronous query (string formatting, small lookups) */
void  fj_free(char* p);
void  fj_shutdown(void);                        /* flush, stop engines, save; returns when safe to exit */
```

`config_json` (`StartConfig` in `NativeApp.kt`): `home`, `downloads` (folder overrides), `args`
(the program arguments that are not host switches), `headless` (no window: nothing is opened or
shown), `bridge` + `bridgePort`, `debug`, `verbose`, `kind` (`development` / `installed` /
`store`, normally found by `WinApp`), `automaticDownloads` (false = no tool download and no
update check by itself), `secondInstance`.
`fj_start` returns 0 on success. It must return fast (heavy start-up continues in coroutines).

Start: the host calls `fj_prepare` first. It reads the arguments (`fetchjet://`, a link,
`--autostart=true`), and when another FetchJet answers on the bridge port it hands the link over
(`/download`) or asks it to show itself (`/ping` + `/show`) and tells the host to exit. A named
mutex in the host makes the second start wait for the first one's bridge.

Rules (report 15):

- Every exported function and every `staticCFunction` body is wrapped in `guarded("name",
  fallback) { }`: an exception that leaves one ends the process.
- `CrashLog.install("<home>/logs/native-crash.log", fatal, terminateOnUnhandled = true,
  quietAbort = true)` is the first thing `fj_start` does.
- Exports never take or return a Kotlin `String`; only `char*`.
- The host never calls `FreeLibrary` on `fjcore.dll` (it crashes). Exit = `fj_shutdown()`, then
  return from `main`.
- `fj_action` may be called from any thread: on the UI thread it runs at once, otherwise it is
  posted to Main. `fj_snapshot` / `fj_call` / `fj_free` are thread-safe.

## 6. Snapshots: core -> UI

The UI is a renderer. Everything it shows arrives as snapshots of DTOs that the presenter builds.

- A channel is one top-level value with its own change bit. Struct channels carry one DTO; list
  channels carry `List<Dto>` and are applied to a stable Slint model row by row (the snapshot
  is a diff against what the host got last: `{"n":N,"at":[i...],"rows":[...]}`), so lists keep
  their scroll position and their row elements; queue channels (`effects`) are emptied by the
  read.
- The list of channels is `dto/Channels.kt` (ids are fixed, new ones are appended, at most 32).
- Mechanism (report 15): a collector on `Dispatchers.Default` builds the DTO, the channel keeps
  it and calls `ChangeNotifier.mark(channel)`, which posts **one** `notify(mask)` to the UI
  thread for all channels changed meanwhile. The home pipeline takes the task part of the view
  model at most every 250 ms; what the user changes himself (tab, sort, speed limit, selection,
  search) is published at once. No change, no notification: an idle app does not wake up.
- One-shot things (show the window, scroll to the top, a system notification, exit) go through
  the `effects` queue, not through state.
- DTO rules (`dto/*.kt`, parsed by the generator, so keep them plain):
    - `@Serializable data class XxxDto(` one property per line `val name: Type = default,` `)`.
    - Types: `String`, `Int`, `Boolean`, `Float`, another `...Dto`, `List<String>`, `List<Int>`,
      `List<...Dto>`. Every property has a default and the default is the zero value (`""`, `0`,
      `false`, `0f`, `emptyList()`, `XxxDto()`): defaults are not sent, and the host fills a
      missing key with zero. "Nothing picked" in a list of options is therefore index + 1 with 0
      for none.
    - A property name must not be a C++ keyword or a Windows macro (`auto`, `small`, `near`...):
      the generator refuses it.
    - A text field never lives in a nested list (`List<Dto>` inside a row): Slint rebuilds the
      elements of a nested list on every change and the field would lose focus. Rows carry
      their fields as members (`one`, `two`).
    - No `Long`, no `Double`, no nullable types: Slint has 32-bit `int` and `float` only. Sizes,
      speeds, dates and durations are sent as ready-to-show strings. "Absent" is `""`, `0`, or a
      `hasXxx: Boolean` next to the value.
    - **Enums travel as their ordinal** (`Int` in JSON, `int` in Slint). The generator emits one
      Slint global per enum with a constant per value (`TaskStateCode.downloading`). Slint enums
      are not used in DTOs: the screenshot tool's interpreter cannot set them (report 13).
    - Text that the user edits follows the echo rule (section 8.3).
    - A single-line text that comes from the user or the web (file name, title, path, URL) has
      two flags next to it, computed by the presenter: `xxxRtl` (it starts with a right-to-left
      letter) and `xxxHan` (it contains a CJK ideograph). Slint cannot inspect characters
      (report 13).
    - Names are spelled the same in Kotlin, JSON, Slint and C++ (camelCase, no conversion).
- The generator emits `ui/generated/dto.slint` (structs + enum constant globals +
  `export global Core` with one `in property` per channel), `host/generated/fj_bridge.h`
  (JSON -> Slint struct, row diff, action senders, string setters) and
  `ui/generated/protocol.json` (channels, enums, structs, actions: read by the tests).
- Fixtures use the same JSON, keyed by channel name: `corehost.fixture()` dumps the state of the
  real core and the screenshot tool draws it (`--json`).

## 7. Actions: UI -> core

- `actions/UiAction.kt`: `sealed interface UiAction` with `data class Name(val a: String, ...)`
  and `data object Name`. Parameter types: `String`, `Int`, `Boolean`, `Float`, `List<String>`,
  `List<Int>`. No `Long` (send KB, minutes, or text), no objects (send ids or indexes).
- Each action is `@Serializable` with `@SerialName("Name")` equal to its class name; the core
  decodes `{"type":"Name",...}` with kotlinx.serialization.
- The generator emits `ui/generated/actions.slint` (`export global Act` with one callback per
  action, same name with a lower-case first letter: `Act.pause(id)`) and the C++ senders (JSON
  with correct escaping -> `fj_action`).
- `presenter/ActionDispatcher.kt` has one exhaustive `when (action)`: view-model events go to
  `DashboardViewModel.onEvent(...)`, UI-local ones change presenter state. A new action does not
  compile until it is handled.

## 8. Strings, direction, text fields

1. Every user-visible text comes from `AppLocalization` (the app's 15 languages). The generator
   reads `AppLocalization.kt` and emits `ui/generated/tr.slint` (`export global Tr`): one
   `in property <string>` per `val` (same name: `Tr.homeAdd`). Texts with parameters (`fun` in
   `AppLocalization`) are formatted by the presenter and arrive inside the DTOs. Channel `tr`
   carries the values; a language change republishes it and every DTO. Slint's own `@tr` is not
   used (report 14).
2. Direction and font come with the `tr` channel: the host sets `Dir.rtl` (true for Arabic and
   Persian) and `Theme.font` (`Microsoft YaHei UI` for Chinese, `Segoe UI` otherwise: the default
   fallback drops simplified Chinese characters, report 13). Slint has no layout mirroring:
   layouts follow section 12.4. Numbers, sizes and speeds inside RTL text are wrapped by the
   presenter in U+2066 ... U+2069, as `ltr()` does in sharedUI.
3. Echo rule for editable text: the widget owns the text while the user types and sends an action
   on each edit (`TextInput.edited` fires for user edits only). The presenter does not echo it
   back. When the core itself replaces the text (paste clean-up, a suggested file name, a reset),
   the DTO's `xxxRev: Int` next to `xxx: String` changes, and only then the widget takes `xxx`.

## 9. Presenter (Kotlin, `presenter/`)

Ports the state logic that sharedUI keeps in Compose (`remember` state, filtering, grouping,
selection, formatting) so the Slint side has no logic:

- owns `DashboardViewModel` and the UI-local state (side filter, search, checked rows, collapsed
  groups, open details, settings page, dialog-local values);
- combines the view-model state with the other flows the UI reads (queue, speed schedule, when
  done, language, pairing, updates, device sync, notifications) into the channel DTOs;
- reproduces the pure helpers of sharedUI (`taskStateLine`, `taskKindTag`, `rowActionFor`,
  `SideFilter`, `taskStateGroups`, `Formatters.kt`) with the same rules. They stay duplicated on
  purpose until the Compose desktop app is retired; sharedUI is not edited;
- knows the position of every list row (group headers are part of the rows), so "scroll to this
  task" is a number for the UI (report 14).

## 10. Host (C++)

- Starts Slint (`SLINT_BACKEND=winit-software`: 14.6 MB private where the Skia software renderer
  takes 32 MB, report 13), loads `fjcore.dll`, calls `fj_start`.
- `post` = `slint::invoke_from_event_loop`. `notify(mask)`: for each set bit, `fj_snapshot` ->
  generated parser -> `Core` global / models.
- Binds `Act` callbacks to `fj_action`.
- Host services for the core (`fj_host_fn`, JSON in / text out): `pickFile`, `pickFolder` (the
  Windows dialogs, called from a Kotlin IO thread). Everything else the core does itself
  (opening files and links, clipboard) or asks for through the `effects` queue (show the window,
  notification, exit).
- Window: icon, title, minimum size 680 x 480, first size 1100 x 750, nothing saved (as in the
  Compose app). Close hides to the tray (`on_close_requested` -> `HideWindow`, event loop in
  `RunUntilQuit`); without a tray icon close quits. The tray is Win32 (`Shell_NotifyIconW` on a
  hidden helper window, `TrackPopupMenu` for the menu, a balloon for notifications); the helper
  window also hears `TaskbarCreated` and the system theme change. Single instance: named mutex
  + the bridge port (section 5). Bring to front: `SetForegroundWindow`, then an Alt tap and
  150 ms topmost when Windows refuses (as the JVM app does).
- Host switches (not passed to the core as arguments): `--home`, `--downloads`, `--debug`,
  `--no-bridge`, `--bridge-port N`, `--no-focus`, `--no-auto-downloads`, `--action <json>`,
  `--script <file>`.
- The EXE carries the application manifest (`res/app.manifest`): `activeCodePage` UTF-8,
  `longPathAware`, per-monitor DPI awareness (report 16). The core relies on the UTF-8 code page.
- Colour emoji used as icons: the software renderer draws them monochrome, and rasterizing them
  in-process with DirectWrite keeps 13 MB. A child process (`FetchJet.exe --emoji-cache <dir>
  --emoji-list <file>`) draws them into an atlas in the app home (`cache/emoji/`). The atlas
  grows on demand: `Emoji.icon(glyph, pixels, revision)` returns the cached picture or notes
  the miss, and the child is started for the misses (first start, a new icon, a scale change, a
  new emoji font). Until a picture exists the icon is the monochrome glyph (report 13;
  `host/emoji_atlas.h`).
- Drag and drop of links or files onto the window: `DropArea` if it receives drops from other
  programs (to be tried with a real mouse), otherwise `RegisterDragDrop` on `win32_hwnd()`.

## 11. Platform layer (sharedLogic `mingwX64Main`)

- One actual per expect, in the expect's package, file name `Xxx.mingw.kt`. Port the behaviour of
  the JVM actual (same edge cases, same texts that common code compares, same files and formats
  in the app home). The iOS actual is the second reference.
- Low-level work goes through `platform/win/` (reports 17 and 18 list what is there and what was
  tested). Actuals do not call Win32 directly when a helper exists; a missing helper is added
  there with a test in `desktopNative/tests/winhelpers`.
- Files: common code keeps okio (safe with the EXE manifest, report 16). New Windows code uses
  `WinFiles` (wide API, long paths). Never narrow `platform.posix` file functions, never
  `getenv` (`winGetEnv`).
- HTTP: `WinHttpNative` (`WinHttpEngine.kt`), FetchJet's own ktor engine on raw WinHTTP:
  HTTP/1.1 only, cancel closes the connection, proxy login for http and https, exceptions are
  never cancellations. `WinHttpNative.registerAsDefault()` runs at start so `HttpClient { }` in
  common code uses it. SOCKS5 goes through a loopback forwarder inside the app (WinHTTP has no
  SOCKS5).
- Child processes (yt-dlp, ffmpeg, deno, PowerShell, shutdown): always `winRunProcess` /
  `WinProcess` (no console window, only the three standard handles inherited, job object so the
  whole tree dies with a cancel, a timeout or the app).
- Archives: `WinZip` (the system `tar.exe` loses files with Unicode names).
- Secrets: `secrets.key` is the same DPAPI blob the JVM app writes and `fjenc1:` values are the
  same AES-GCM layout, so both apps can use one app home (report 17).
- The app home is `%USERPROFILE%/.fetch_jet`, or the `home` passed to `fj_start` (tests always
  pass one).
- UI-facing actuals (`TextFilePicker`, `TorrentFilePicker`, `SystemNotice`,
  `DownloadNotificationManager`, focus) call `platform/win/HostHooks` (function variables that
  `:desktopNativeCore` sets at start); they never open windows themselves.
- Torrents: `fjtorrent.dll` (C API over libtorrent) is loaded lazily on first use. Without the
  DLL `platformSupportsTorrent()` is false and the torrent UI is hidden.

## 12. UI rules (Slint)

1. No logic in `.slint` beyond presentation (visibility, hover, focus, local open/closed state of
   a menu, text being typed). No filtering, sorting, formatting or business rules. Slint has no
   `contains` / `substring` anyway.
2. Colors, type sizes, radii and spacing come from `ui/theme.slint` (`Theme`), which mirrors
   `sharedUI/theme` (both schemes). No literal colors in pages except the fixed status colors
   that sharedUI also hard-codes.
3. Pages use the widgets in `ui/widgets/`. **No std-widgets** (one `LineEdit` is 470 KB of
   generated code, report 14): buttons, text field, checkbox, switch, menu, dialog frame, scroll
   area, list, chips, progress, emoji icon are the app's own, built on `Rectangle`, `Text`,
   `TouchArea`, `FocusScope`, `TextInput`, `Flickable`, `PopupWindow`, `Path`, `Image`. A missing
   widget is added there, not inlined. The starting points are in `docs/port/slint-caps/`.
4. Direction: pages never assume left-to-right. `Dir.rtl`; `HBox` (a `FlexboxLayout` that
   reverses) and `DText` for chrome, dialogs and settings; a `GridLayout` with mirrored columns
   for list rows (flexbox is 4.5 times slower to lay out); computed `x` for absolute positions;
   `NameText` for user text that can be too long (`overflow: elide` is broken for text that
   starts with Arabic) (report 13).
5. Sizes are logical pixels equal to Compose dp; font sizes equal Compose sp.
6. The software renderer has no `drop-shadow`, no rotation, no rounded corners on a gradient and
   no rounded clipping of images (report 13). Glow and shadows are nine-slice PNGs tinted with
   `colorize`; a spinner animates an arc; icons are `Path`s, atlas images, or glyphs with
   `font-family: "Segoe UI Symbol"`.
7. Long lists use the app's own component named `ListView` with exactly one `for` inside (the
   compiler virtualizes it by that name, report 14). Group headers are part of the rows.
8. Dialogs are overlays inside the window (scrim + focus trap that handles Tab), stacked by `z`.
   `PopupWindow` is for menus, drop-downs and tooltips; every popup is its own component (an
   inline `ContextMenuArea` in an inline popup crashes the compiler).
9. Keyboard shortcuts are `KeyBinding`s inside a `FocusScope` (the window has none of its own).
   A focused text field keeps Ctrl+A, Delete and typing; Escape and unknown shortcuts pass on.
10. One file per page or dialog in `ui/pages/` (its small private components stay in the same
    file), one file per widget family in `ui/widgets/`. File owners are fixed during parallel
    work: nobody edits another unit's file.
11. The toast is drawn above the page and the details sheet and under the dialogs.

## 13. Tests

- No internet in tests. HTTP engine tests use `tools/testserver` on 127.0.0.1
  (`servers.sh start`).
- `platform/win` is tested by `tests/winhelpers` (`run.sh`; 155 checks on 2026-11-09), with Java
  cross-checks for the crypto.
- Core tests run without any window: `tests/corehost/corehost.py` (Python, ctypes) loads
  `fjcore.dll`, starts it with a scratch home, sends actions and checks snapshots and files on
  disk (`test_*.py`). It refuses the actions that would use the internet, read the clipboard or
  start a power action.
- Pages are checked with `tools/shot` (off-screen PNG from the state of the real core) in dark
  and light, English and Arabic (`shots*.py`). The whole app is checked with `deskrun` (hidden
  desktop: memory, window list, picture of each window; `tests/host`).
- Never use the owner's real home (`C:/Users/miche/.fetch_jet`) and never touch the installed app.

## 14. Rules for everybody

- No `git commit`, `push`, `reset`, branch change. The owner commits.
- No code comments in source files (owner's rule). Generated files carry one header line.
- No downloads from the internet (tools, packages, test files). Ask through the report instead.
- No mouse / keyboard automation, no window brought to the foreground, no visible test windows.
- Builds at low priority; one heavy build at a time (`tools/one-build.sh`).
