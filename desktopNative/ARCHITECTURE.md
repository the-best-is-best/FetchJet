# FetchJet native desktop: architecture contract

Status: DRAFT 1 (2026-11-09). Items marked (probe NN) are confirmed or changed by the reports in
`docs/port/`. Everybody who writes code for the native app follows this file. If something here is
wrong or missing, change this file first, then the code.

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
    platform/win/        low-level Windows helpers (process, files, registry, crypto, sockets, shell)
    core/... feature/... the mingwX64 actuals, in the same package as their expect
desktopNative/
    ARCHITECTURE.md      this file
    core/                Gradle module :desktopNativeCore -> fjcore.dll (depends on :sharedLogic)
        src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/nativeapp/
            Exports.kt       the C API (section 5)
            runtime/         start-up, Main dispatcher, crash log, host calls
            dto/             snapshot DTOs: the source of truth of the UI protocol (section 6)
            actions/         UiAction: everything the UI can ask for (section 7)
            presenter/       UI state logic ported from sharedUI (section 9)
            browserbridge/   the 127.0.0.1:28450 server (port of desktopApp main.kt)
            generated/       generated Kotlin (string table, action decoder)
    host/                C++ host: main.cpp, core_api.h, json.h, apply state, Windows integration
        generated/       generated C++ (DTO parsers, action senders, string setters)
    ui/                  theme.slint, widgets/, pages/, dialogs/, app.slint
        generated/       generated Slint (dto.slint, actions.slint, tr.slint)
    tools/gen/           the Python generators (one command: python tools/gen/gen.py)
    tools/shot/          off-screen screenshots of any .slint component
    tools/testserver/    local HTTP test server (no internet in tests)
    tests/               fixtures (state JSON per scenario), end-to-end scripts
    res/                 icon, .rc, application manifest
    docs/port/           maps of the code to port and probe reports
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
  (release: `linkReleaseSharedMingwX64`). Binary options: `-Xallocator=std` (probe 16).
- Host: CMake + MSVC (`desktopNative/build.cmd`), Slint from `E:/tools/slint` (`SLINT_DIR`).
- Only one Gradle build runs in this repo at a time. Builds run at low priority: the owner uses
  the PC while they run.

## 4. Threads

- UI thread = the Slint event loop thread = Kotlin `Dispatchers.Main` (probe 15). The host gives
  the core a `post` function; the core wraps it in the Main dispatcher. The view model, the
  presenter and every `fj_*` call run on this thread, as on the other platforms.
- Engines keep using `Dispatchers.Default` / `Dispatchers.IO`.
- Nothing blocks the UI thread: no file, network, process or registry work on Main.
- The host never touches Kotlin objects; it only calls the C API. The core never touches Slint;
  it only publishes snapshots and calls host services (section 10).

## 5. C API (`host/core_api.h`, exported by `Exports.kt`)

All strings are UTF-8, NUL-terminated. Strings returned by the core are freed with `fj_free`.

```c
typedef void (*fj_post_fn)(void (*fn)(void*), void* arg);   /* run fn(arg) on the UI thread; callable from any thread */
typedef void (*fj_notify_fn)(unsigned int changed_channels); /* called on the UI thread: bit i = channel i has a new snapshot */
typedef char* (*fj_host_fn)(const char* name, const char* args_json); /* host service, called on the UI thread; result freed with fj_host_free */
typedef void (*fj_host_free_fn)(char* p);

int   fj_start(const char* config_json, fj_post_fn post, fj_notify_fn notify, fj_host_fn host, fj_host_free_fn host_free);
char* fj_snapshot(int channel);                 /* current snapshot of one channel as JSON */
void  fj_action(const char* action_json);       /* {"type":"Pause","id":"..."} */
char* fj_call(const char* name, const char* args_json); /* synchronous query (string formatting, small lookups) */
void  fj_free(char* p);
void  fj_shutdown(void);                        /* flush, stop engines, save; returns when safe to exit */
```

`config_json`: `{ "home": "...", "args": [...], "loginStart": false, "headless": false }`.
`fj_start` returns 0 on success. It must return fast (heavy start-up continues in coroutines).

## 6. Snapshots: core -> UI

The UI is a renderer. Everything it shows arrives as snapshots of DTOs that the presenter builds.

- A channel is one top-level value with its own change bit. Struct channels carry one DTO; list
  channels carry `List<Dto>` and are applied to a stable Slint model row by row (only changed rows
  are written), so lists keep their scroll position.
- Channels (ids are fixed, new ones are appended): 0 `chrome`, 1 `tasks` (list), 2 `details`,
  3 `add`, 4 `mediaQualities` (list), 5 `torrentFiles` (list), 6 `linkList` (list),
  7 `playlist` (list), 8 `settings`, 9 `dialogs`, 10 `devices` (list), 11 `tr` (strings).
  The exact list lives in `dto/Channels.kt`.
- DTO rules (`dto/*.kt`, parsed by the generator, so keep them plain):
    - `@Serializable data class XxxDto(` one property per line `val name: Type = default,` `)`.
    - Types: `String`, `Int`, `Boolean`, `Float`, an enum declared in `dto/`, another `...Dto`,
      `List<String>`, `List<Int>`, `List<...Dto>`. Every property has a default.
    - No `Long`, no `Double`, no nullable types: Slint has 32-bit `int` and `float` only. Sizes,
      speeds, dates and durations are sent as ready-to-show strings. "Absent" is `""`, `0`, or a
      `hasXxx: Boolean` next to the value.
    - Text that the user edits follows the echo rule (section 8.3).
    - Names are spelled the same in Kotlin, JSON, Slint and C++ (camelCase, no conversion).
- The generator emits `ui/generated/dto.slint` (structs + enums + `export global Core` with one
  `in property` per channel) and `host/generated/dto_parse.{h,cpp}` (JSON -> Slint struct, row
  diff).
- Throttle: `chrome`, `tasks` and `details` publish at most every 250 ms while downloads run;
  other channels publish at once. No change, no notification: an idle app does not wake up.
- Fixtures (`tests/fixtures/**.json`) use the same JSON, keyed by channel name. The screenshot
  tool and the tests feed them through the same generated parsers.

## 7. Actions: UI -> core

- `actions/UiAction.kt`: `sealed interface UiAction` with `data class Name(val a: String, ...)`
  and `data object Name`. Parameter types: `String`, `Int`, `Boolean`, `Float`, `List<String>`,
  `List<Int>`. No `Long` (send KB, minutes, or text), no objects (send ids or indexes).
- The generator emits `ui/generated/actions.slint` (`export global Act` with one callback per
  action, same name with a lower-case first letter: `Act.pause(id)`), the C++ senders (JSON with
  correct escaping -> `fj_action`) and `generated/ActionDecoder.kt` (JSON -> `UiAction`).
- `presenter/ActionDispatcher.kt` has one exhaustive `when (action)`: view-model events go to
  `DashboardViewModel.onEvent(...)`, UI-local ones change presenter state. A new action does not
  compile until it is handled.

## 8. Strings, direction, text fields

1. Every user-visible text comes from `AppLocalization` (the app's 15 languages). The generator
   reads `AppLocalization.kt` and emits `ui/generated/tr.slint` (`export global Tr`): one
   `in property <string>` per `val` (same name: `Tr.homeAdd`), one function per `fun`
   (`Tr.homeSortBy(x)`, backed by a pure callback into `fj_call` and re-evaluated when the
   language changes). Channel `tr` carries the values; a language change republishes it.
2. `Tr.rtl` is true for Arabic and Persian. Layouts follow section 12.4 (probe 13).
   Numbers, sizes and speeds inside RTL text are wrapped by the presenter (Unicode isolates), as
   `ltr()` does in sharedUI.
3. Echo rule for editable text: the widget owns the text while the user types and sends an action
   on each edit. The presenter does not echo it back. When the core itself replaces the text (paste
   clean-up, a suggested file name, a reset), the DTO's `xxxRev: Int` next to `xxx: String`
   changes, and only then the widget takes `xxx`.

## 9. Presenter (Kotlin, `presenter/`)

Ports the state logic that sharedUI keeps in Compose (`remember` state, filtering, grouping,
selection, formatting) so the Slint side has no logic:

- owns `DashboardViewModel` and the UI-local state (side filter, search, checked rows, collapsed
  groups, open details, settings page, dialog-local values);
- combines the view-model state with the other flows the UI reads (queue, speed schedule, when
  done, language, pairing, updates, device sync, notifications) into the channel DTOs;
- reproduces the pure helpers of sharedUI (`taskStateLine`, `taskKindTag`, `rowActionFor`,
  `SideFilter`, `taskStateGroups`, `Formatters.kt`) with the same rules. They stay duplicated on
  purpose until the Compose desktop app is retired; sharedUI is not edited.

## 10. Host (C++)

- Starts Slint (`SLINT_BACKEND=winit-software`), loads `fjcore.dll`, calls `fj_start`.
- `notify(mask)`: for each set bit, `fj_snapshot` -> generated parser -> `Core` global / models.
- Binds `Act` callbacks to `fj_action`, `Tr` functions to `fj_call`.
- Host services for the core (`fj_host_fn`, JSON in / JSON out): `pickFile`, `pickFolder`,
  `saveFile`, `showWindow`, `hideWindow`, `notify` (tray balloon), `setTrayTooltip`, `quit`,
  `openPath`, `revealPath`, `clipboardText`. Modal Windows dialogs are fine: they pump messages.
- Window: icon, title, minimum size, saved geometry, close hides to the tray, tray icon and menu,
  single instance (a second start shows the first), start hidden at login.
- The EXE carries the application manifest (`res/app.manifest`): `activeCodePage` UTF-8,
  `longPathAware`, per-monitor DPI awareness (probe 16). The core relies on the UTF-8 code page.
- Color emoji used as icons are rasterized once with DirectWrite / Direct2D into images (probe 13).

## 11. Platform layer (sharedLogic `mingwX64Main`)

- One actual per expect, in the expect's package, file name `Xxx.mingw.kt`. Port the behaviour of
  the JVM actual (same edge cases, same texts that common code compares, same files and formats
  in the app home). The iOS actual is the second reference.
- Low-level work goes through `platform/win/` helpers (one place for `CreateProcessW`, registry,
  DPAPI, BCrypt, sockets, shell, clipboard). Actuals do not call Win32 directly when a helper
  exists. The helper API is listed in `docs/port/helpers.md` (written with the foundations).
- Paths are UTF-8 Kotlin strings; Win32 calls use the wide (`W`) functions through the helpers.
- The app home is `%USERPROFILE%/.fetch_jet`, or the `home` passed to `fj_start` (tests always
  pass one). The native app reads and writes the same files as the JVM app; secrets written by one
  must open in the other.
- UI-facing actuals (`TextFilePicker`, `TorrentFilePicker`, `SystemNotice`,
  `DownloadNotificationManager`, focus) call `platform/win/HostHooks` (function variables that
  `:desktopNativeCore` sets at start); they never open windows themselves.
- Torrents: `fjtorrent.dll` (C API over libtorrent) is loaded lazily on first use. Without the
  DLL `platformSupportsTorrent()` is false and the torrent UI is hidden.

## 12. UI rules (Slint)

1. No logic in `.slint` beyond presentation (visibility, hover, focus, local open/closed state of
   a menu, text being typed). No filtering, sorting, formatting or business rules.
2. Colors, type sizes, radii and spacing come from `ui/theme.slint` (`Theme`), which mirrors
   `sharedUI/theme` (both schemes). No literal colors in pages except the fixed status colors
   that sharedUI also hard-codes.
3. Pages use the widgets in `ui/widgets/` (buttons, text field, checkbox, switch, menu, dialog
   frame, scroll, chips, progress, emoji icon). A missing widget is added there, not inlined.
4. Direction: pages never assume left-to-right (probe 13 defines the wrappers).
5. Sizes are logical pixels equal to Compose dp; font sizes equal Compose sp.
6. Long lists are virtualized (`ListView` or the list widget defined after probe 14).
7. One component per file, `PascalCase.slint`, imported by `app.slint`. File owners are fixed
   during parallel work: nobody edits another unit's file.

## 13. Tests

- No internet in tests. HTTP engine tests use `tools/testserver/server.py` on 127.0.0.1.
- Core tests run without any window: a console runner loads `fjcore.dll`, starts it with a
  scratch home, sends actions and checks snapshots and files on disk.
- Pages are checked with `tools/shot` (off-screen PNG from a fixture) in dark and light, English
  and Arabic.
- Never use the owner's real home (`C:/Users/miche/.fetch_jet`) and never touch the installed app.

## 14. Rules for everybody

- No `git commit`, `push`, `reset`, branch change. The owner commits.
- No code comments in source files (owner's rule). Generated files carry one header line.
- No downloads from the internet (tools, packages, test files). Ask through the report instead.
- No mouse / keyboard automation, no window brought to the foreground, no visible test windows.
- Builds at low priority; one Gradle build in the repo at a time.
