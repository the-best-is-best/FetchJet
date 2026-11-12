# 04 - UI porting spec: home shell, theme, formatters

Scope: the window, the root layout (`App`), navigation (sidebar, rail, top bar, phone header,
bottom bar), the four home alert dialogs, the settings bridge, the theme, the formatters, the
scrollbar and the clipboard guard. Target: a Slint 1.18.1 `.slint` UI that renders state computed
by a Kotlin presenter. No business logic in the UI.

All paths are relative to `E:/tbib/FetchJet`. `UI/` means
`androidUi/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`, `LOGIC/` means
`sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`.

| File                                                              | Lines | Content                                                                                                                                                      |
|-------------------------------------------------------------------|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `UI/App.kt`                                                       | 887   | `missingFileTaskId`, `openDownloadedFile`, `App`                                                                                                             |
| `UI/HomeNavigation.kt`                                            | 770   | `FdmBottomNavigation`, `FjSidebar`, `FjSidebarItem`, `SpeedControl`, `FjTopBar`, `FjPhoneHeader`, `FjRail`, `FjRailItem`                                     |
| `UI/HomeDialogs.kt`                                               | 185   | `RestartOnPauseDialog`, `ClipboardOfferDialog`, `BrowserFallbackDialog`, `BridgePairingDialog`                                                               |
| `UI/SettingsContent.kt`                                           | 111   | `SettingsContentView`                                                                                                                                        |
| `UI/theme/Color.kt`                                               | 79    | colour constants                                                                                                                                             |
| `UI/theme/Theme.kt`                                               | 67    | `FetchJetDarkColorScheme`, `FetchJetLightColorScheme`, `FetchJetTheme`                                                                                       |
| `UI/utils/Formatters.kt`                                          | 171   | `ltr`, `formatBytes`, `formatSizeDisplay`, `formatSpeed`, `decodeUnicodeText`, `formatSecondsToHhMm`, `parseHhMmToSeconds`, `formatSecondsToDisplayFriendly` |
| `UI/ui/AppScrollbar.kt`                                           | 138   | `AppScrollbar` (two overloads), `ScrollbarCore`                                                                                                              |
| `UI/ui/SafeClipboard.kt`                                          | 35    | `ProvideSafeClipboard`                                                                                                                                       |
| `desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt` | 934   | window, tray, single instance, bridge server                                                                                                                 |

How the facts in this document were obtained:

- Everything about the app's own code was read from the files above (line numbers given).
- Material 3 defaults were read from the jar the desktop app really uses,
  `org.jetbrains.compose.material3:material3-desktop:1.12.0-alpha03` (Gradle cache, decoded with
  `javap`), and Compose UI `1.12.2` (`ui-desktop`, `ui-text-desktop`). They are marked **[jar]**.
  This Material version already has the "expressive" token changes, so several defaults differ from
  what older Material 3 documentation says (outlined button colours, tracking values).
- Values marked **[derived]** are computed from the code plus those defaults (for example a row
  height). They were not measured on a screenshot: no window could be opened in this task.

Units: Compose `dp` = Slint logical `px`. On desktop the font scale is 1, so `sp` = `dp`.

Direction: every "start / end" below is left / right in LTR and must be swapped in RTL (Arabic,
Persian). Compose mirrors the whole tree automatically (`App.kt` L181-185).

---

## 1. Window and host (`main.kt`)

### 1.1 Window

`main.kt` L769, L862-870.

| Property                | Value                                                                                                                                                                                                                                         |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Title                   | `"FetchJet - " + Strings.desktopWindowTagline` (English: `FetchJet - High-Velocity Multi-Engine Downloader`). Re-evaluated when the language changes (the scope reads `AppLanguage.current`).                                                 |
| Initial size            | `rememberWindowState(width = 1100.dp, height = 750.dp)`. This is the outer window size (title bar and borders included). Not saved between runs.                                                                                              |
| Position                | platform default (not saved).                                                                                                                                                                                                                 |
| Minimum size            | **none**. The desktop app sets no minimum; the user can shrink it until the phone layout appears (section 2).                                                                                                                                 |
| Resizable / decorations | defaults: resizable, normal OS title bar. No menu bar.                                                                                                                                                                                        |
| Icon                    | `Res.drawable.ic_fetchjet_logo` (vector, `androidUi/src/commonMain/composeResources/drawable/ic_fetchjet_logo.xml`; the prototype already has `desktopNative/ui/logo.svg`, `logo.png`, `res/fetchjet.ico`).                                    |
| Close button            | `onCloseRequest`: if a system tray exists the window is hidden (`isWindowVisible = false`), the process keeps running; without a tray the app exits.                                                                                          |
| Start hidden            | `startInTray = startedAtLogin && StartupPreferences.startHidden && SystemTray.isSupported()` (L750-756). `startedAtLogin` = argument `--autostart=true` (`AUTOSTART_ARG`) or, with no arguments, `WindowsStorePackage.looksLikeLoginStart()`. |
| Renderer                | on Windows `skiko.renderApi=SOFTWARE` unless set by property / `SKIKO_RENDER_API` (L691-695).                                                                                                                                                 |

Important consequence of the 1100 dp outer width **[derived]**: on Windows the client area is
about 16 dp narrower (resize borders), so at the default size the content is about 1084 dp wide.
That is below the 1100 dp breakpoint, so **the layout the user sees at the default window size is
the "medium" one: 84 dp icon rail and the two-row top bar**, with details as a bottom sheet. The
232 dp sidebar appears only after the window is widened or maximised.

### 1.2 What wraps `App()`

- `CompositionLocalProvider(LocalClipboard provides safeClipboard) { App() }` (L908-931): the same
  guard as `ProvideSafeClipboard` (section 10). `App()` is called with no arguments, so
  `onDarkModeChanged` and `onForcedDarkChanged` are unused on desktop (the title bar is never
  themed by the app).
- `LaunchedEffect(bringToFrontTrigger)` (L871-897): show, un-minimise, `toFront`, `requestFocus`,
  a synthetic Alt key tap (lifts the Windows foreground lock), always-on-top for 150 ms.
  Triggered by `triggerFocus()` = `isWindowVisible = true; isMinimized = false; trigger++`.
- `LaunchedEffect(Unit)` (L899-906): a link given on the command line is dispatched 600 ms after
  start: `ExternalDownloadBridge.dispatch(url, "")`, then `triggerFocus()`.

### 1.3 Startup sequence (`main`, L690-757)

1. Renderer property (Windows).
2. `installCrashLog()` (L278-303): stdout / stderr through `RedactingOutputStream` to
   `~/.fetch_jet/logs/app.log`, deleted first if larger than 2 MB; header line
   `---- FetchJet started <time> (java <version>) ----`; default uncaught exception handler.
3. `LocalizationManager.start()`.
4. `AppLog.verbose` = env `FETCHJET_VERBOSE_LOGS` (wins) or property `fetchjet.verboseLogs`.
5. Taskbar icon from `launcher.png` (for non-packaged runs).
6. Daemon thread `browser-integration`: `BrowserIntegration.setUpOnFirstRun()`.
7. `StartupPreferences.launchedAtLogin = startedAtLogin`; the autostart argument is removed from
   the argument list.
8. First remaining argument through `parseUrlFromArg` (L51-94). Accepted: `fetchjet://` or
   `kortex://` with a `url=` query parameter (URL-decoded) or with an `http(s)://` remainder; a
   plain `http://`, `https://`, `magnet:` link; a path ending in `.torrent` that exists (returned
   as an absolute path). Anything else gives `""`.
9. If a link was given: `trySendToRunningInstance(url)` (GET
   `http://127.0.0.1:28450/download?url=...`, headers `X-FetchJet: 1` and the bridge token,
   800 ms timeouts). Success = exit; otherwise keep it as `pendingStartupUrl`.
10. Without a link: `isAnotherInstanceRunning()` (GET `/ping` answers `PONG` within 500 ms; then
    a best-effort GET `/show`). True = exit.

### 1.4 Inside `application { }` (L758-857)

- `ExternalDownloadBridge.onShowAndFocus = { triggerFocus() }` (on the UI thread).
- `ExternalDownloadBridge.openInBrowserFallback = { url -> Desktop.browse(URI(url)) }`.
- `startLocalHttpServer(triggerFocus)`: the bridge on `127.0.0.1:28450` (L305-688; endpoints
  `/ping`, `/inspect`, `/config`, `/pair`, `/add`, `/links`, `/show`, `/download`). Not UI; listed
  here only because it must be started by the host.
- `WhenDone.onExitApp = { exitApplication() }`.
- System notifications: `SystemNotice.notices.collect { (title, text) -> tray notification, type
  Info }` (L762-768).

### 1.5 Tray (L803-857)

| Part                                     | Behaviour                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Icon                                     | the app logo.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| Tooltip                                  | no active download: `Strings.desktopTrayIdleTooltip` ("FetchJet - Running in Background"). Otherwise `Strings.desktopTrayDownloadsHeader` ("FetchJet Downloads:") + one line per task for the first 6 of `DownloadNotificationManager.activeTasks`: `"<name>: <progressPercent>%"`, where a name longer than 25 characters becomes its first 22 characters + `...`; if there are more than 6, a last line `Strings.desktopTrayMore(n - 6)` ("+N more"). Lines are joined with `\n`. Recomputed when the task map or the language changes.                                    |
| Primary action (double click on Windows) | window visible and not minimised: hide it. Otherwise `triggerFocus()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| Menu                                     | 1. `Strings.desktopTrayShowApp` ("Show App") = `triggerFocus()`. 2. Submenu `Strings.whenDoneGroup` ("When downloads finish") with one check item per `WhenDoneAction` (`NOTHING`, `EXIT`, `SLEEP`, `SHUT_DOWN`; labels `whenDoneLabel(choice)` = `whenDoneNothing` "Do nothing", `whenDoneExit` "Exit FetchJet", `whenDoneSleep` "Sleep", `whenDoneShutDown` "Shut down"), checked when `WhenDone.action == choice`, click = `WhenDone.choose(choice)`. 3. Separator. 4. `Strings.desktopTrayCloseApp` ("Close App") = exit. The menu is rebuilt when the language changes. |

The prototype tray in `desktopNative/ui/app.slint` has Show App / Close App only; the "When
downloads finish" submenu and the dynamic tooltip are missing.

---

## 2. Responsive breakpoints (`App.kt` L191-200, L445-449)

`screenWidth` is the width of the window's content area.

| Width          | Name in code | Navigation                                                                       | Top bar                                                | Details of a selected download     |
|----------------|--------------|----------------------------------------------------------------------------------|--------------------------------------------------------|------------------------------------|
| `< 680`        | `isCompact`  | `FjPhoneHeader` on top, `FdmBottomNavigation` at the bottom, floating "+" button | phone header                                           | full-screen dialog page            |
| `680 ..< 1100` | `isMedium`   | `FjRail` (84 dp)                                                                 | `FjTopBar(compact = true)`: title row, search under it | bottom sheet dialog                |
| `>= 1100`      | wide         | `FjSidebar` (232 dp)                                                             | `FjTopBar(compact = false)`: one row                   | bottom sheet dialog while `< 1280` |
| `>= 1280`      | `sidePanel`  | `FjSidebar`                                                                      | one row                                                | fixed side panel, 340 dp           |

- `wideLayout = !isCompact` is written in a `SideEffect`. When it becomes true and the selected
  tab is neither `ALL` nor `SETTINGS`, the UI sends `OnTabSelected(DashboardTab.ALL)` (L143-148):
  in non-compact layouts the list behind is always "All" and the sidebar filter narrows it.
- The task list has its own breakpoint (wide rows from 720 dp); see the task-list spec.
- The port only needs `>= 680` if the native window gets a minimum width of 680 or more (open
  question 1). The compact layout is still documented below (sections 6.1, 6.6, 5.4).

---

## 3. Theme

### 3.1 Colour constants (`UI/theme/Color.kt`)

| Constant                   | Hex       | Constant                   | Hex       |
|----------------------------|-----------|----------------------------|-----------|
| `NeonCyan`                 | `#00E5FF` | `CobaltPrimary`            | `#0284C7` |
| `CyanOnPrimary`            | `#031625` | `CobaltOnPrimary`          | `#FFFFFF` |
| `CyanPrimaryContainer`     | `#0C3D60` | `CobaltLightContainer`     | `#E0F2FE` |
| `CyanOnPrimaryContainer`   | `#C7F1FF` | `CobaltOnLightContainer`   | `#0369A1` |
| `VioletSecondary`          | `#818CF8` | `IndigoLight`              | `#4F46E5` |
| `VioletOnSecondary`        | `#1E1B4B` | `IndigoOnLight`            | `#FFFFFF` |
| `VioletContainer`          | `#2C2F63` | `IndigoLightContainer`     | `#EEF2FF` |
| `VioletOnContainer`        | `#E0E7FF` | `IndigoOnLightContainer`   | `#312E81` |
| `RoseTertiary`             | `#F43F5E` | `RoseRedLight`             | `#E11D48` |
| `RedErrorDark`             | `#F87171` | `RedErrorLight`            | `#DC2626` |
| `RedOnErrorDark`           | `#450A0A` | `RedOnErrorLight`          | `#FFFFFF` |
| `RedErrorContainerDark`    | `#451A1A` | `RedErrorContainerLight`   | `#FEE2E2` |
| `RedOnErrorContainerDark`  | `#FECACA` | `RedOnErrorContainerLight` | `#991B1B` |
| `ObsidianBackground`       | `#080D1A` | `LightBackground`          | `#F8FAFC` |
| `ObsidianSurface`          | `#0F1728` | `LightSurface`             | `#FFFFFF` |
| `ObsidianSurfaceVariant`   | `#172238` | `LightSurfaceVariant`      | `#F1F5F9` |
| `ObsidianOnBackground`     | `#F8FAFC` | `LightOnBackground`        | `#0F172A` |
| `ObsidianOnSurface`        | `#F1F5F9` | `LightOnSurface`           | `#0F172A` |
| `ObsidianOnSurfaceVariant` | `#94A3B8` | `LightOnSurfaceVariant`    | `#475569` |
| `ObsidianOutline`          | `#334155` | `LightOutline`             | `#94A3B8` |
| `ObsidianOutlineVariant`   | `#1E2B42` | `LightOutlineVariant`      | `#CBD5E1` |

Theme-independent constants (same file, L71-79), used by task rows and details:

| Constant              | Hex       |
|-----------------------|-----------|
| `EngineBitTorrent`    | `#10B981` |
| `EngineMediaStream`   | `#818CF8` |
| `StatusCompleted`     | `#10B981` |
| `StatusCompletedDark` | `#064E3B` |
| `StatusPaused`        | `#F59E0B` |
| `StatusPausedDark`    | `#78350F` |
| `StatusConnecting`    | `#38BDF8` |
| `StatusError`         | `#F87171` |

### 3.2 Complete colour schemes (`UI/theme/Theme.kt`)

`FetchJetTheme(isDarkMode: Boolean = false, content)` (L57-67) = `MaterialTheme(colorScheme =
dark or light scheme, content)`. Typography and shapes are the Material defaults. The schemes are
built with `darkColorScheme(...)` / `lightColorScheme(...)` and set 21 roles; **every other role
keeps the Material baseline value** (purple-tinted greys). Those baseline roles are visible in
the app: alert dialogs, dropdown menus, snack bars and switches use them.

"app" = set in `Theme.kt`; "M3" = Material baseline **[jar]**.

| Role                      | Dark      | Source    | Light     | Source    |
|---------------------------|-----------|-----------|-----------|-----------|
| `primary`                 | `#00E5FF` | app       | `#0284C7` | app       |
| `onPrimary`               | `#031625` | app       | `#FFFFFF` | app       |
| `primaryContainer`        | `#0C3D60` | app       | `#E0F2FE` | app       |
| `onPrimaryContainer`      | `#C7F1FF` | app       | `#0369A1` | app       |
| `inversePrimary`          | `#6750A4` | M3        | `#D0BCFF` | M3        |
| `secondary`               | `#818CF8` | app       | `#4F46E5` | app       |
| `onSecondary`             | `#1E1B4B` | app       | `#FFFFFF` | app       |
| `secondaryContainer`      | `#2C2F63` | app       | `#EEF2FF` | app       |
| `onSecondaryContainer`    | `#E0E7FF` | app       | `#312E81` | app       |
| `tertiary`                | `#F43F5E` | app       | `#E11D48` | app       |
| `onTertiary`              | `#492532` | M3        | `#FFFFFF` | M3        |
| `tertiaryContainer`       | `#633B48` | M3        | `#FFD8E4` | M3        |
| `onTertiaryContainer`     | `#FFD8E4` | M3        | `#31111D` | M3        |
| `background`              | `#080D1A` | app       | `#F8FAFC` | app       |
| `onBackground`            | `#F8FAFC` | app       | `#0F172A` | app       |
| `surface`                 | `#0F1728` | app       | `#FFFFFF` | app       |
| `onSurface`               | `#F1F5F9` | app       | `#0F172A` | app       |
| `surfaceVariant`          | `#172238` | app       | `#F1F5F9` | app       |
| `onSurfaceVariant`        | `#94A3B8` | app       | `#475569` | app       |
| `surfaceTint`             | `#00E5FF` | = primary | `#0284C7` | = primary |
| `inverseSurface`          | `#E6E0E9` | M3        | `#322F35` | M3        |
| `inverseOnSurface`        | `#322F35` | M3        | `#F5EFF7` | M3        |
| `error`                   | `#F87171` | app       | `#DC2626` | app       |
| `onError`                 | `#450A0A` | app       | `#FFFFFF` | app       |
| `errorContainer`          | `#451A1A` | app       | `#FEE2E2` | app       |
| `onErrorContainer`        | `#FECACA` | app       | `#991B1B` | app       |
| `outline`                 | `#334155` | app       | `#94A3B8` | app       |
| `outlineVariant`          | `#1E2B42` | app       | `#CBD5E1` | app       |
| `scrim`                   | `#000000` | M3        | `#000000` | M3        |
| `surfaceBright`           | `#3B383E` | M3        | `#FEF7FF` | M3        |
| `surfaceDim`              | `#141218` | M3        | `#DED8E1` | M3        |
| `surfaceContainerLowest`  | `#0F0D13` | M3        | `#FFFFFF` | M3        |
| `surfaceContainerLow`     | `#1D1B20` | M3        | `#F7F2FA` | M3        |
| `surfaceContainer`        | `#211F26` | M3        | `#F3EDF7` | M3        |
| `surfaceContainerHigh`    | `#2B2930` | M3        | `#ECE6F0` | M3        |
| `surfaceContainerHighest` | `#36343B` | M3        | `#E6E0E9` | M3        |

The "fixed" roles (`primaryFixed`, `onPrimaryFixed`, ...) also keep the baseline; nothing in this
area uses them.

Where the baseline roles show up in this area:

| Component                   | Role                                  | Dark                  | Light                 |
|-----------------------------|---------------------------------------|-----------------------|-----------------------|
| `AlertDialog` container     | `surfaceContainerHigh`                | `#2B2930`             | `#ECE6F0`             |
| `DropdownMenu` container    | `surfaceContainer`                    | `#211F26`             | `#F3EDF7`             |
| `Snackbar` container / text | `inverseSurface` / `inverseOnSurface` | `#E6E0E9` / `#322F35` | `#322F35` / `#F5EFF7` |

The existing `desktopNative/ui/theme.slint` has the app roles but none of the baseline ones; add
at least `surface-container`, `surface-container-high`, `surface-container-highest`,
`inverse-surface`, `inverse-on-surface`, `on-background`, `on-secondary`, `secondary-container`,
`on-secondary-container`, `error-container`, `on-error`, `on-error-container`, `scrim`.

### 3.3 Translucent colours used by the shell, flattened **[derived]**

Useful when a Slint element cannot take an alpha colour directly.

| Use                           | Expression                                               | Dark                   | Light     |
|-------------------------------|----------------------------------------------------------|------------------------|-----------|
| Sidebar / rail background     | `surfaceVariant` 30% over `background`                   | `#0C1323`              | `#F6F8FB` |
| Selected nav item             | `onSurface` 8% over the sidebar                          | `#1E2534`              | `#E4E6EA` |
| Nav item hover (state layer)  | `onBackground` 8% over the sidebar                       | `#1F2534`              | `#E4E6EA` |
| Count badge, not selected     | `onSurface` 7% over the sidebar                          | `#1C2332`              | `#E6E8EC` |
| Sidebar divider               | `outlineVariant` 40% over the sidebar                    | `#131D2F`              | `#E5EAF1` |
| Speeds chip                   | `surfaceVariant` 45% over `background`                   | `#0F1628`              | `#F5F8FB` |
| Mode chip, no limit           | `surfaceVariant` 40% over `background`                   | `#0E1526`              | `#F5F8FB` |
| Mode chip, limited            | `primary` 18% over `background`                          | `#073443`              | `#CCE5F2` |
| Turtle button, active         | `primary` 22% over `background`                          | `#063D4C`              | `#C2E0F0` |
| Text field container          | `surfaceVariant` 35% over `surface` (opaque in the code) | `#121B2E`              | `#FAFCFD` |
| Text field container, focused | `primary` 6% over `surface` (opaque in the code)         | `#0E2335`              | `#F0F8FC` |
| Text field border, idle       | `outlineVariant` 70%                                     | `#1A263C` on the field | `#D9E1E9` |
| Details panel divider         | `outlineVariant` 40% over `surface`                      | `#151F32`              | `#EAEEF3` |

### 3.4 Typography **[jar]**

Font family for every role: `FontFamily.SansSerif`, which Compose Desktop resolves on Windows to
**Segoe UI** (fallback Arial). Weights: Normal 400, Medium 500, SemiBold 600, Bold 700. Segoe UI
has no 500 face, so Medium most likely renders as Regular (not verified on screen).

| Role             | Size | Line height | Weight | Letter spacing |
|------------------|------|-------------|--------|----------------|
| `displayLarge`   | 57   | 64          | 400    | -0.2           |
| `displayMedium`  | 45   | 52          | 400    | 0              |
| `displaySmall`   | 36   | 44          | 400    | 0              |
| `headlineLarge`  | 32   | 40          | 400    | 0              |
| `headlineMedium` | 28   | 36          | 400    | 0              |
| `headlineSmall`  | 24   | 32          | 400    | 0              |
| `titleLarge`     | 22   | 28          | 400    | 0              |
| `titleMedium`    | 16   | 24          | 500    | 0.2            |
| `titleSmall`     | 14   | 20          | 500    | 0.1            |
| `bodyLarge`      | 16   | 24          | 400    | 0.5            |
| `bodyMedium`     | 14   | 20          | 400    | 0.2            |
| `bodySmall`      | 12   | 16          | 400    | 0.4            |
| `labelLarge`     | 14   | 20          | 500    | 0.1            |
| `labelMedium`    | 12   | 16          | 500    | 0.5            |
| `labelSmall`     | 11   | 16          | 500    | 0.5            |

Rules the look depends on:

1. `MaterialTheme` provides `bodyLarge` as the ambient text style. A `Text` that only passes
   `fontSize = N.sp` therefore keeps **line height 24 and letter spacing 0.5** from `bodyLarge`.
   The default line-height style is `Alignment.Center` + `Trim.None`, so a one-line text is as
   tall as its line height. Example: a `Text("3", fontSize = 11.sp)` is 24 dp tall, not about
    15. Many sizes in this document follow from this.
2. Slots that change the ambient style: `Button` / `OutlinedButton` / `TextButton` content and
   `DropdownMenuItem` text = `labelLarge`; `AlertDialog` title = `headlineSmall`, its text =
   `bodyMedium`; `Badge` = `labelSmall`.
3. A `Text` without `color` uses the ambient content colour: `onBackground` inside the root
   surface, `onPrimary` inside a filled button, and so on.

### 3.5 Shapes and elevations **[jar]**

Shapes: extraSmall 4, small 8, medium 12, large 16, extraLarge 28, full = pill.
Elevation levels: 0, 1, 3, 6, 8, 12 dp (levels 0 to 5).

### 3.6 Material component defaults used in this area **[jar]**

State layers (hover / focus / press ripple) are an overlay of the content colour: hover 8%,
focus 10%, pressed 10%, dragged 16%. Every `Modifier.clickable` in the app shows them (the
ambient indication is the Material ripple), clipped to the element's shape.

This Material version has a denser "precision pointer" sizing (dialog padding 20 instead of 24,
button min height 36 instead of 40, ...). It is **off** in the desktop build:
`ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled` is false, the app never sets it,
and the desktop implementation registers no pointer listeners. The values below are the normal
ones.

| Component                 | Defaults                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Surface`                 | colour `surface`, content colour = matching "on" colour (or inherited when the colour is not a scheme role, for example a colour with alpha), no elevation, rectangle.                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `Button` (filled)         | visual min height 40, min width 58, layout height at least 48 (minimum interactive size; the 40 or 44 dp shape is centred in it). Content padding 24 horizontal, 8 vertical. Default shape pill (the app always passes its own). Container `primary`, content `onPrimary`, text `labelLarge`. Disabled: container `onSurface` 10%, content `onSurfaceVariant` 38%. Hover: shadow elevation 1 dp + state layer.                                                                                                                                                                                                     |
| `OutlinedButton`          | same sizes and padding. Transparent container, **border 1 dp `outlineVariant`**, **content `onSurfaceVariant`** (this Material version; older ones used `primary`). Disabled content `onSurfaceVariant` 38%.                                                                                                                                                                                                                                                                                                                                                                                                       |
| `TextButton`              | padding 12 horizontal, 8 vertical; content `primary`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `IconButton`              | 48 x 48 in layout, 40 dp round state layer, 24 dp icon area, content colour = ambient content colour; disabled 38%.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `AlertDialog`             | min width 280, max width 560, centred. Container `surfaceContainerHigh`, tonal elevation 0, default shape 28 (the app passes 18). Padding 24 on all sides. Title: `headlineSmall`, colour `onSurface`, 16 below. Text: `bodyMedium`, colour `onSurfaceVariant`, 24 below. Buttons: right-aligned wrapping row, 8 between buttons, 8 between wrapped rows, order `dismissButton` then `confirmButton` (confirm is the last one, at the end edge). Behind it a scrim of black 60% over the whole window. Click on the scrim or Escape = `onDismissRequest`. Dialogs are drawn inside the main window (no OS window). |
| `DropdownMenu`            | container `surfaceContainer`, shape 4, shadow elevation 3 dp, vertical padding 8, width 112 to 280, at least 48 from the window's top and bottom edges and 8 from the sides. Opens under its anchor, start edges aligned. Click outside or Escape closes it.                                                                                                                                                                                                                                                                                                                                                       |
| `DropdownMenuItem`        | height 48, horizontal padding 12, text `labelLarge` in `onSurface`; disabled: `onSurface` 38%.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `HorizontalDivider`       | 1 dp, `outlineVariant`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `Snackbar`                | container `inverseSurface`, text `inverseOnSurface` in `bodyMedium`, shape 4, shadow 6 dp, min height 48 (one line) / 68 (two lines), text inset 16 at the start, max width 600, plus 12 dp outer padding added by the host. `showSnackbar(text)` without an action stays 4 s; messages queue one at a time. Enter: fade 150 ms + scale from 0.8; exit: fade 75 ms.                                                                                                                                                                                                                                                |
| `LinearProgressIndicator` | 4 dp thick, track `secondaryContainer`, indicator `primary`, round caps, 4 dp stop dot at the end.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `Checkbox`                | 18 dp box, corner 2, outline 2 dp `onSurfaceVariant`; checked: fill `primary`, mark `onPrimary`; 40 dp state layer, 48 dp layout; disabled 38% `onSurface`.                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `Switch`                  | track 52 x 32, outline 2 dp; off: track `surfaceContainerHighest`, outline and 16 dp thumb `outline`; on: track `primary`, 24 dp thumb `onPrimary`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `RadioButton`             | 20 dp icon, 40 dp state layer; selected `primary`, unselected `onSurfaceVariant`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `FloatingActionButton`    | 56 dp, default shape 16 (the app passes 18), shadow 6 dp.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| Plain tooltip             | `inverseSurface` / `inverseOnSurface`, `bodySmall`, shape 4. (No tooltip is used in this area.)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |

---

## 4. Formatters (`UI/utils/Formatters.kt`)

These live in `androidUi`, which is not compiled for the native target, so the presenter needs its
own copy. Numbers are printed with Kotlin's `Double.toString()` (`1.0`, `12.5`); the port must
produce the same text (test with the examples).

### 4.1 `fun ltr(text: String): String` (L5)

Empty text stays empty. Otherwise the text is wrapped in U+2066 (left-to-right isolate) and
U+2069 (pop directional isolate) so sizes and speeds do not flip in Arabic. Check that Slint's
text renderer draws these two characters as nothing; if it shows boxes, apply the wrapping only
when it is supported (risk 4).

### 4.2 `fun formatBytes(bytes: Long): String` (L10-23) = `ltr(...)` of:

- `bytes <= 0` gives `0 B`.
- Otherwise divide by 1024 while the value is `>= 1024` and the unit is below TB; units
  `B, KB, MB, GB, TB`.
- One decimal, **truncated** (`(value * 10).toLong() / 10.0`), always printed, also for bytes.

| Input             | Output      |
|-------------------|-------------|
| 0, -5             | `0 B`       |
| 1                 | `1.0 B`     |
| 512               | `512.0 B`   |
| 1023              | `1023.0 B`  |
| 1024              | `1.0 KB`    |
| 1536              | `1.5 KB`    |
| 1048576           | `1.0 MB`    |
| 2086666 (1.99 MB) | `1.9 MB`    |
| 157286400         | `150.0 MB`  |
| 1500000000        | `1.3 GB`    |
| 5368709120        | `5.0 GB`    |
| 1024^5            | `1024.0 TB` |

### 4.3 `fun formatSizeDisplay(sizeBytes: Long): String` (L28-40) = `ltr(...)` of:

- `<= 0` gives the empty string.
- At least 1 MB: megabytes with one truncated decimal + ` MB` (never GB).
- Below 1 MB: `sizeBytes / 1024` as an integer + ` KB`.

| Input      | Output      |
|------------|-------------|
| 0          | (empty)     |
| 500        | `0 KB`      |
| 524288     | `512 KB`    |
| 1048575    | `1023 KB`   |
| 1048576    | `1.0 MB`    |
| 2086666    | `1.9 MB`    |
| 2147483648 | `2048.0 MB` |

### 4.4 `fun formatSpeed(bytesPerSec: Long, unit: String = "MB/s"): String` (L45-112) =
`ltr(...)` of:

1. Normalise the unit: `BYTES_PER_SEC` and `BOTH` become `MB/s`, `BITS_PER_SEC` becomes `Mbps`,
   anything else is used as it is. The setting offers `MB/s`, `KB/s`, `Mbps`, `Kbps`
   (`UI/ui/SettingsPages.kt` L133).
2. `bytesPerSec <= 0`: `KB/s` gives `0 KB/s`, `Kbps` gives `0 Kbps`, `Mbps` gives `0 Mbps`, any
   other unit (including `MB/s`) gives `0 KB/s`.
3. Positive values, rounding with `kotlin.math.round` (half to even) and always printing the
   decimal:
    - `MB/s`: below 1024: `<n> B/s` (integer). Below 1 MiB: KiB with 1 decimal + ` KB/s`. Below
      10 MiB: MiB with 2 decimals + ` MB/s`. Otherwise MiB with 1 decimal + ` MB/s`.
    - `KB/s`: KiB with 1 decimal + ` KB/s`, whatever the size.
    - `Kbps`: `bytes * 8 / 1000` with 1 decimal + ` Kbps`.
    - Any other unit is treated as `Mbps`: `m = bytes * 8 / 1 000 000`; `m < 0.1`: kilobits with
      1 decimal + ` Kbps`; `m < 10`: 2 decimals + ` Mbps`; otherwise 1 decimal + ` Mbps`.

"2 decimals" means `round(x * 100) / 100.0` printed by `toString`, so `1.0` and `5.5` keep their
short form.

| bytes/s   | `MB/s`       | `KB/s`          | `Mbps`       | `Kbps`          |
|-----------|--------------|-----------------|--------------|-----------------|
| 0         | `0 KB/s`     | `0 KB/s`        | `0 Mbps`     | `0 Kbps`        |
| 10        | `10 B/s`     | `0.0 KB/s`      | `0.1 Kbps`   | `0.1 Kbps`      |
| 512       | `512 B/s`    | `0.5 KB/s`      | `4.1 Kbps`   | `4.1 Kbps`      |
| 1024      | `1.0 KB/s`   | `1.0 KB/s`      | `8.2 Kbps`   | `8.2 Kbps`      |
| 1536      | `1.5 KB/s`   | `1.5 KB/s`      | `12.3 Kbps`  | `12.3 Kbps`     |
| 12499     | `12.2 KB/s`  | `12.2 KB/s`     | `100.0 Kbps` | `100.0 Kbps`    |
| 524288    | `512.0 KB/s` | `512.0 KB/s`    | `4.19 Mbps`  | `4194.3 Kbps`   |
| 1048576   | `1.0 MB/s`   | `1024.0 KB/s`   | `8.39 Mbps`  | `8388.6 Kbps`   |
| 6721372   | `6.41 MB/s`  | `6563.8 KB/s`   | `53.8 Mbps`  | `53771.0 Kbps`  |
| 10485760  | `10.0 MB/s`  | `10240.0 KB/s`  | `83.9 Mbps`  | `83886.1 Kbps`  |
| 11010048  | `10.5 MB/s`  | `10752.0 KB/s`  | `88.1 Mbps`  | `88080.4 Kbps`  |
| 104857600 | `100.0 MB/s` | `102400.0 KB/s` | `838.9 Mbps` | `838860.8 Kbps` |

### 4.5 `fun decodeUnicodeText(input: String): String` (L117-127)

Replaces every match of the regex `(?:\\u|_u)([0-9a-fA-F]{4})` (a backslash + `u`, or `_u`,
followed by 4 hex digits) with the UTF-16 unit of that value; a failure leaves the match as it
is. Example: the 6 characters `\u0627` or `_u0627` become the Arabic letter alef. Used for file
names and paths in the task list and details (`TaskList.kt` L326, `TaskDetails.kt` L84, L92,
`TaskRows.kt` L253).

### 4.6 `fun formatSecondsToHhMm(totalSeconds: Long): String` (L132-142)

Always `HH:MM:SS`, each part padded to 2 digits; negative input counts as 0; hours are not
capped. Examples: 0 gives `00:00:00`, 75 gives `00:01:15`, 3661 gives `01:01:01`, 360000 gives
`100:00:00`. (The name is historical: two parts were read as MM:SS by yt-dlp / ffmpeg.)

### 4.7 `fun parseHhMmToSeconds(hhMm: String): Long` (L147-153)

Split on `:`; part 0 = hours, part 1 = minutes, part 2 = seconds; a missing or non-numeric part
is 0 (no trimming); result `h * 3600 + m * 60 + s`, never below 0. Examples: `00:10:00` gives
600, `01:30` gives 5400, `10` gives 36000, `abc` gives 0.

### 4.8 `fun formatSecondsToDisplayFriendly(totalSeconds: Long): String` (L158-171)

Negative counts as 0. Parts `h`, `m`, `s` that are zero are left out, but seconds are shown when
everything is zero. Not localised, no `ltr`.

| Input | Output  | Input | Output     |
|-------|---------|-------|------------|
| 0     | `0s`    | 3600  | `1h`       |
| 59    | `59s`   | 3601  | `1h 1s`    |
| 60    | `1m`    | 3660  | `1h 1m`    |
| 61    | `1m 1s` | 3661  | `1h 1m 1s` |

### 4.9 Related helper used by the top bar: `speedPresetShortLabel(bytes: Long)`

`UI/ui/SpeedPresetUi.kt` L30-36. At least 1 MiB: megabytes, as an integer when whole, otherwise
one truncated decimal, + ` MB/s`. Below: `bytes / 1024` (integer division) + ` KB/s`. No `ltr`.
Examples: 262144 gives `256 KB/s`, 524288 gives `512 KB/s`, 2097152 gives `2 MB/s`, 2621440
gives `2.5 MB/s`, 1500000 gives `1.4 MB/s`, 20971520 gives `20 MB/s`.

---

## 5. `App.kt`

### 5.1 Top-level declarations

- `internal val missingFileTaskId = mutableStateOf<String?>(null)` (L62): global UI state, the
  task whose file is gone. Shown by `MissingFileDialog` (L786-797).
- `internal fun openDownloadedFile(fullPath: String, taskId: String)` (L68-77): with a new
  `PathResolver()`: if `!fileExists(fullPath)` set `missingFileTaskId = taskId` and stop;
  otherwise `openFile(fullPath)`, and when that returns false `openFileLocation(fullPath)`.
  Called from the task list (`TaskList.kt` L326) and the details pane.

### 5.2
`@Composable fun App(onDarkModeChanged: ((Boolean) -> Unit)? = null, onForcedDarkChanged: ((Boolean?) -> Unit)? = null)` (L81-887)

Purpose: the whole application UI. Used by `desktopApp/.../main.kt` L930 (no arguments),
`androidApp/.../MainActivity.kt` and `androidUi/src/iosMain/.../MainViewController.kt`.

Owns `viewModel = DashboardViewModel()`, `state = viewModel.uiState` (`DashboardUiState`,
`LOGIC/presentation/dashboard/DashboardContract.kt` L82) and one `PathResolver`.

#### UI-local state

| State               | Type, initial                                         | Logic                                                                                                                                                                                                                                                                                         |
|---------------------|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `clipboardFirstRun` | Boolean, `true`                                       | see the clipboard watcher below                                                                                                                                                                                                                                                               |
| `selectedTaskId`    | String?, `null`                                       | the download whose details are open. Set by the list (`onSelect`), cleared by the details close button, the sheet's dismiss and drag-down. `selectedTask = state.allTasks.firstOrNull { it.id == selectedTaskId }`: a task that disappears closes the details (the id itself is not cleared). |
| `sideFilter`        | `SideFilter`, `ALL`                                   | sidebar / rail filter (non-compact layouts)                                                                                                                                                                                                                                                   |
| `searchQuery`       | String, `""`                                          | top-bar search (non-compact layouts)                                                                                                                                                                                                                                                          |
| `wideLayout`        | Boolean, `false`                                      | `!isCompact`, see section 2                                                                                                                                                                                                                                                                   |
| `sheetExpanded`     | Boolean, `false`, reset when `selectedTaskId` changes | bottom sheet height (L452)                                                                                                                                                                                                                                                                    |
| `dragTotal`         | Float, 0                                              | drag distance on the sheet handle (L487)                                                                                                                                                                                                                                                      |
| `snackbarHostState` | `SnackbarHostState`                                   | toast queue                                                                                                                                                                                                                                                                                   |
| `missingFileTaskId` | global, see 5.1                                       |                                                                                                                                                                                                                                                                                               |

#### Effects

1. **Clipboard watcher** (L95-128), keyed on `state.monitorClipboard` and
   `state.clipboardVideoPages`:
    - Watching off: `clipboardFirstRun = false`, stop.
    - `last` = the current clipboard text if `clipboardFirstRun` (so what was copied before the
      app started is ignored), else `null` (so turning the switch on, or changing "video pages",
      looks at the current clipboard right away). Then `clipboardFirstRun = false`.
    - Loop forever: wait 800 ms; `text = getClipboardText()` (errors = `null`); if `text != null
     && text != last`: `last = text`, and if `ClipboardLinks.downloadableUrl(text,
     clipboardVideoPages)` is not null send `DashboardUiEvent.OnClipboardLink(url)`.
    - On desktop the composition stays alive while the window is hidden in the tray, so the
      watcher keeps running there.
    - `ClipboardLinks.downloadableUrl` is in `LOGIC/core/settings/BehaviorSettings.kt` L254-274
      (already shared logic). `getClipboardText()` is an `expect` in `LOGIC/Platform.kt` L10; it
      needs a native actual (Windows clipboard, `null` on any failure).
2. `LaunchedEffect(state.clipboardOfferUrl)` (L136-138): when it becomes non-null call
   `ExternalDownloadBridge.showAndFocus()` (the window comes to the front).
3. `LaunchedEffect(wideLayout)` (L143-148): see section 2.
4. **Theme** (L151-168): `darkMode = when (state.themeMode) { "light" -> false; "dark" -> true;
   else -> isSystemInDarkTheme() }`. It follows the OS setting live in "system" mode. Callbacks
   `onDarkModeChanged(darkMode)` and `onForcedDarkChanged(false / true / null)` are for the host;
   unused on desktop. Use `darkMode`, never `state.isDarkMode`, for anything theme-dependent
   (exception: section 8).
5. **Toasts** (L170-177): `viewModel.uiEffect.collect { ShowToast(text) ->
   snackbarHostState.showSnackbar(text) }`. `DashboardUiEffect` has only `ShowToast(val text:
   String)` (`DashboardContract.kt` L449-451).
6. **Language and direction** (L179-185): `language = AppLanguage.current` (StateFlow); the whole
   tree is inside `key(language)`: when the language changes everything is rebuilt and the state
   remembered inside it is reset (open menus, `sheetExpanded`, and the task list's own state such
   as checked rows and folded groups). `selectedTaskId`, `sideFilter` and `searchQuery` are
   declared outside the `key` block, so they **survive** a language change.
   `LocalLayoutDirection` = RTL when `AppLanguage.isRtl` (language code before `-` in `ar, fa,
   ur, he`).

#### Root

```
FetchJetTheme(isDarkMode = darkMode)
  Surface(fillMaxSize, color = background)  + clearFocusOnTapOutside()
    BoxWithConstraints(fillMaxSize)            layout by width, section 2
    [details sheet dialog]                     5.5
    [dialogs]                                  5.6
    Box(fillMaxSize, padding bottom 88, align BottomCenter) { SnackbarHost }   5.7
```

`Modifier.clearFocusOnTapOutside()` (`UI/ui/FetchJetTextField.kt` L227-242): a tap (movement not
above the touch slop) anywhere outside the focused text field clears the focus.

### 5.3 Non-compact layout (`>= 680`, L284-440)

```
Row(fillMaxSize)
  FjRail(...)        if isMedium           6.7
  FjSidebar(...)     otherwise             6.2
  Box(weight 1, fillMaxHeight, contentAlignment TopCenter)
    Column(fillMaxSize, padding horizontal 16, vertical 10)
      FjTopBar(...)                                              6.5
      if selectedTab == SETTINGS:
        SettingsContentView(state, viewModel, pathResolver)      8
      else:
        Box(weight 1, fillMaxWidth) { TaskListArea(...) }
        QueueStatusBar(...)
  Surface details panel   if sidePanel && selectedTask != null && selectedTab != SETTINGS
```

- The column is written `fillMaxSize().widthIn(max = 1400.dp)`. Because `fillMaxSize` comes
  first, the 1400 dp cap never applies: **the content always spans the full width** [derived
  from how Compose size modifiers combine; not checked on a very wide screen]. Open question 3.
- `FjRail` / `FjSidebar` arguments: `tasks = state.allTasks`, `current = sideFilter`,
  `settingsSelected = state.selectedTab == SETTINGS`,
  `onSelect = { f -> sideFilter = f; OnTabSelected(DashboardTab.ALL) }`,
  `onSettings = { OnTabSelected(DashboardTab.SETTINGS) }`.
- `FjTopBar` arguments: `showSearch = selectedTab != SETTINGS`, `compact = isMedium`,
  `title = if SETTINGS Strings.homeSettings else sideFilter.label`, `searchQuery`,
  `onSearchChange = { searchQuery = it }`, `totalDlSpeed = state.totalDownloadSpeedBytesPerSec`,
  `totalUlSpeed = state.totalUploadSpeedBytesPerSec`, `speedDisplayUnit`, `isDarkMode =
  darkMode`, `downloadLimitBytes = state.globalDownloadSpeedLimitBytes`, the three preset byte
  values, `onPickDownloadLimit = { OnGlobalDownloadSpeedLimitChanged(it) }`,
  `onThemeToggled = { OnThemeModeChanged(if (darkMode) "light" else "dark") }`,
  `onAdd = { OnToggleAddDialog(true) }`.
- `TaskListArea` arguments (the component is specified in the task-list document; signature
  `UI/TaskList.kt` L104-118): `tasks = state.filteredTasks.filter { sideFilter.matches(it) }`,
  `viewModel`, `speedDisplayUnit`, `selectedTab`, `groupByState = sideFilter in {ALL, MEDIA,
  FILES, TORRENTS}` (state headers only when the view mixes states), `sortOption`,
  `selectedTaskId`, `onSelect = { selectedTaskId = it }`, `externalSearch = searchQuery`.
- `QueueStatusBar(tasks = state.allTasks, globalLimitBytes = state.globalDownloadSpeedLimitBytes,
  speedDisplayUnit)`: defined in `UI/TaskList.kt` L422-472, repeated here because it is the
  shell's bottom line:
    - `Row(fillMaxWidth, padding top 6, spacing 18, centred vertically)`, every text
      `labelMedium` (12 / 16, weight 500) in `onSurfaceVariant`, one line.
    - Text 1: `Strings.homeDownloadingWaitingSummary(running, waiting)` ("3 downloading · 2
      waiting"). `running` = tasks with `!waitingInQueue` and status `DOWNLOADING` or
      `CONNECTING`; `waiting` = tasks with `waitingInQueue`.
    - Text 2: `Strings.homeQueueStatus(maxAtOnce)` ("Queue: no limit" when `<= 0`, else "Queue: N
      at a time"); `maxAtOnce` is read directly from
      `QueueManagerRepository.getInstance().maxConcurrentDownloads` (StateFlow).
    - Text 3: `Strings.homeSpeedLimitStatus(limit)` ("Speed limit: off" or "Speed limit: 5.0
      MB/s"); `limit = formatSpeed(globalLimitBytes, speedDisplayUnit)` when the limit is `> 0`,
      else `null`.
    - Spacer (weight 1), then `"v" + APP_VERSION`, no wrapping.
- Vertical budget of the content column **[derived]**: 10 padding, top bar row 48 + 10 below it
  (wide; the medium bar is 48 + 10 + 44 + 10 with search), the list, status bar 6 + 16, 10
  padding.

**Details side panel** (L410-439), only `>= 1280`:

```
Surface(color = surface, width 340, fillMaxHeight)
  Row
    Box(width 1, fillMaxHeight, background outlineVariant 40%)
    TaskDetailsPane(task = selectedTask, viewModel, speedDisplayUnit,
                    fullScreen = false, onClose = { selectedTaskId = null })
```

`TaskDetailsPane` is `UI/TaskDetails.kt` L821 (details document).

### 5.4 Compact layout (`< 680`, L202-282)

```
Column(fillMaxSize)
  FjPhoneHeader(...)                         6.6
  Box(weight 1, fillMaxWidth, padding horizontal 8, vertical 4)
    if SETTINGS: SettingsContentView
    else:
      TaskListArea(tasks = state.filteredTasks, ..., showToolbar = false)   own search box
      FloatingActionButton(align BottomEnd, padding end 10, bottom 14,
                           shape 18, container primary, content onPrimary)
        Text("+", 26 sp, Bold)               click: OnToggleAddDialog(true)
  FdmBottomNavigation(...)                   6.1
```

Differences from the wide layouts: the tab (`state.selectedTab`) filters the list instead of
`sideFilter`; no top-bar search (`externalSearch` is null, the list shows its own field); no
`QueueStatusBar`; header title = `Strings.homeSettings` or `Strings.homeDownloads`; counts for the
bottom bar: all = `allTasks.size`, active = status `DOWNLOADING` or `CONNECTING`, completed =
`COMPLETED`, torrent = `engineType == BITTORRENT`, media = `engineType == MEDIA_STREAM`.

### 5.5 Details as a dialog (`< 1280`, L445-550)

Shown when `selectedTask != null && selectedTab != SETTINGS` and the width is below 1280.

```
Dialog(onDismissRequest = { selectedTaskId = null }, usePlatformDefaultWidth = false)
  scrim: black 60% over the whole window (Compose default)
  Box(fillMaxSize, clickable without indication = close,
      contentAlignment = TopCenter (phone) / BottomCenter (tablet))
    Surface(color = background,
            shape = none (phone) / top corners 22 (tablet),
            size: phone: fill;
                  tablet collapsed: full width, height = content, at most 90% of the window;
                  tablet expanded:  full width, 94% of the window height;
            clickable without indication = swallow)
      Column
        [tablet] handle: Box(fillMaxWidth, padding top 6, bottom 2)
                   pill: 44 x 5, corner 3, onSurface 18%, 6 above and below
        TaskDetailsPane(task, viewModel, speedDisplayUnit, fullScreen = phone,
                        onClose = { selectedTaskId = null },
                        fillHeight = phone || sheetExpanded)
    SnackbarHost(snackbarHostState, align BottomCenter, padding bottom 24)
```

- The sheet is written `fillMaxWidth().widthIn(max = 820.dp)`; as in 5.3 the cap has no effect,
  so the sheet is as wide as the window [derived]. Open question 3.
- Handle behaviour: click toggles `sheetExpanded`. Vertical drag, decided at release from the
  summed distance in pixels: below -40 = expand; above 40 while expanded = collapse; above 40
  while collapsed = close.
- Close: click outside the sheet, Escape, the pane's own close button.
- The second `SnackbarHost` exists because the dialog is a separate layer and would hide
  messages. If the Slint port draws the toast above everything, one host is enough.

### 5.6 Dialogs hosted by `App` (L552-875), in composition order (a later one is drawn above)

| #  | Condition                                                                      | Component                                                                         | Events                                                                                                      |
|----|--------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| 1  | `state.isAddDialogVisible`                                                     | `AddDownloadModalDialog` (`UI/ui/AddDownloadModalDialog.kt` L44)                  | see below                                                                                                   |
| 2  | `removeConfirmationTaskId != null \|\| removeConfirmationTaskIds.isNotEmpty()` | `RemoveConfirmationDialog(count = 1 if the single id is set, else the list size)` | `OnConfirmRemoveClicked(deleteFile)`, `OnDismissRemoveDialog`                                               |
| 3  | `missingFileTaskId != null` and that task still exists                         | `MissingFileDialog(fileName = task.fileName)`                                     | Remove: clear the id, then `OnRemoveClicked(id)` and `OnConfirmRemoveClicked(false)`; dismiss: clear the id |
| 4  | always composed                                                                | `UpdateDialogHost()` (`UI/ui/UpdateUi.kt` L48)                                    | own                                                                                                         |
| 5  | always composed                                                                | `WhenDoneDialogHost()` (`UI/ui/WhenDoneUi.kt` L96)                                | own                                                                                                         |
| 6  | `state.clipboardOfferUrl != null`                                              | `ClipboardOfferDialog(url)` (7.2)                                                 | `OnClipboardOfferAccepted`, `OnClipboardOfferDismissed`                                                     |
| 7  | `BridgePairing.pending` (StateFlow) not null                                   | `BridgePairingDialog(request)` (7.4)                                              | `BridgePairing.answer(true)`, `BridgePairing.block()`, dismiss = `BridgePairing.answer(false)`              |
| 8  | `state.deviceShareTaskId != null`                                              | `DeviceSendSheet(state) { viewModel.onEvent(it) }`                                | own                                                                                                         |
| 9  | `state.devicePairing != null`                                                  | `DevicePairingDialog(pairing)`                                                    | `OnConfirmPairingClicked`, `OnCancelPairingClicked`, `OnBlockPairingClicked`                                |
| 10 | `state.browserFallbackUrl != null`                                             | `BrowserFallbackDialog(url)` (7.3)                                                | `OnConfirmBrowserFallback`, `OnDismissBrowserFallback`                                                      |
| 11 | `state.duplicateTaskAlert != null`                                             | `DuplicateDownloadDialog(task)`                                                   | `OnResumeExistingDuplicateClicked`, `OnRestartDuplicateClicked`, `OnDismissDuplicateDialog`                 |
| 12 | `state.fileConflictAlert != null`                                              | `FileOverwriteDialog(data)`                                                       | `OnConfirmOverwriteClicked`, `OnSaveAsCopyClicked(newName)`, `OnDismissFileConflictDialog`                  |

Components 1-5, 8, 9, 11, 12 are specified in their own documents.

Add-dialog wiring (L553-773): state fields read: `inputUrl, destinationPath,
disableTorrentUpload, detectedEngine, isLoading, errorMessage, isInspectingTorrent,
inspectedTorrentName, inspectedTorrentFiles, inspectError, isInspectingMedia, courseReading,
inspectedMediaQualities, inspectedSubtitles, embedSubtitles, inspectMediaError, isYtDlpInstalled,
ytDlpStatus, isDownloadingYtDlp, suggestedFileName, fileNameOverride, sortIntoFolders,
checksumInput, clipStartTime, clipEndTime, isClipRangeEnabled, mediaDurationSeconds,
clipFastMode, audioConvertFormat, inspectedPlaylist, playlistQuality, linkList, linkListFolder,
scheduleStartMs, selectedConnections, defaultConnections`, plus `pathResolver`. Callbacks, each a
`DashboardUiEvent`: `OnSubtitleToggled(lang, selected)`, `OnToggleEmbedSubtitles(embed)`,
`OnDownloadYtDlpClicked`, `OnUrlChanged(text)`, `OnFileNameChanged(name)`,
`OnChecksumChanged(value)`, `OnDestinationPathChanged(path)`,
`OnDisableTorrentUploadToggled(on)`, `OnInspectTorrentClicked`, `OnInspectMediaClicked`,
`OnTorrentFileSelectionToggled(index, selected)`, `OnTorrentFilePriorityChanged(index,
priority)`, `OnSelectAllTorrentFilesToggled(all)`, `OnMediaQualitySelectionToggled(index,
selected)`, `OnSelectAllMediaQualitiesToggled(all)`, `OnClipStartTimeChanged(text)`,
`OnClipEndTimeChanged(text)`, `OnToggleClipRange(on)`, `OnToggleClipFastMode(on)`,
`OnAudioConvertFormatSelected(format)`, `OnPlaylistEntryToggled(index, selected)`,
`OnPlaylistSelectAll(all)`, `OnPlaylistQualityChanged(quality)`, `OnPlaylistUseSingleVideo`,
`OnPlaylistFolderChanged(name)`, `OnLinkListEntryToggled(index, on)`, `OnLinkListSelectAll(all)`,
`OnLinkListFolderChanged(name)`, `OnLinkListTorrentFileToggled(link, file, on)`,
`OnLinkListTorrentFilePriority(link, file, priority)`, `OnLinkListQualitySelected(link,
quality)`, `OnLinkListRetryInspect(link)`, `OnLinkListTorrentSelectAll(link, on)`,
`OnScheduleChanged(ms)`, `OnConnectionsChanged(n)`, `OnEngineSelected(engine)`,
`OnAddDownloadClicked`, dismiss = `OnToggleAddDialog(false)`.

### 5.7 Main toast (L877-882)

`SnackbarHost` aligned bottom-centre, 88 dp above the bottom edge (plus the snack bar's own 12
dp padding), at most 600 dp wide, Material look (3.6). Text only, no action, 4 s, one at a time.

---

## 6. `HomeNavigation.kt`

### 6.1
`internal fun FdmBottomNavigation(selectedTab: DashboardTab, allCount: Int, activeCount: Int, completedCount: Int, torrentCount: Int, mediaCount: Int, onTabSelected: (DashboardTab) -> Unit)` (L68-184)

Compact layout only (`App.kt` L271-281).

```
Surface(color = surface, tonalElevation 3, fillMaxWidth)
  Column
    Box(fillMaxWidth, height 0.5, outlineVariant 35%)
    NavigationBar(transparent, height 58)
      one NavigationBarItem per tab, equal widths
```

Tabs in order (icon, label, badge count): `ALL` U+1F4E5 inbox tray, `Strings.homeTabAll` "All",
`allCount`; `ACTIVE` U+26A1 high voltage, `homeTabActive` "Active", `activeCount`; `COMPLETED`
U+2705 check mark button, `homeTabDone` "Done", `completedCount`; `TORRENT` U+1F9F2 magnet,
`homeTabTorrent` "Torrent", `torrentCount` (only if `AppFeatures.torrent`); `MEDIA` U+1F3AC
clapper board, `homeTabMedia` "Media", `mediaCount` (only if `AppFeatures.mediaExtractor`);
`SETTINGS` U+2699 U+FE0F gear, `homeSettings` "Settings", no badge.

Item: icon text 18 sp; if the count is above 0 a `Badge` (container `primary`, content
`onPrimary`, text 9 sp Bold) at the icon's top end; label 10 sp, one line, ellipsis, Bold when
selected else Medium; label always shown. Colours: selection pill `primary` 16%, selected icon
and text `primary`, unselected `onSurfaceVariant`. Click: `onTabSelected(tab)`.

### 6.2
`internal fun FjSidebar(tasks: List<DownloadTask>, current: SideFilter, settingsSelected: Boolean, onSelect: (SideFilter) -> Unit, onSettings: () -> Unit)` (L187-269)

Wide layout (`>= 1100`), `App.kt` L305-321.

```
Surface(width 232, fillMaxHeight, color = surfaceVariant 30%)
  Column(fillMaxHeight, padding horizontal 12, vertical 14)
    Row(centred vertically, padding start 6, bottom 18)                  brand
      Image(logo, 32 x 32, clip corner 9)
      Spacer(width 10)
      Text("FetchJet", titleMedium, Bold)                               hard-coded
    FjSidebarItem x 6: ALL, DOWNLOADING, WAITING, PAUSED, COMPLETED, FAILED
    Box(padding vertical 10, horizontal 8; fillMaxWidth; height 1; outlineVariant 40%)
    Text(Strings.homeSidebarTypeHeader, labelSmall, Bold, onSurfaceVariant,
         padding start 10, bottom 4)                                     "TYPE"
    FjSidebarItem: MEDIA (if AppFeatures.mediaExtractor), FILES,
                   TORRENTS (if AppFeatures.torrent)
    Spacer(weight 1)
    FjSidebarItem(Strings.homeSettings, count = null, selected = settingsSelected, onSettings)
```

For each filter `f`: label `f.label`, count `tasks.count { f.matches(it) }` (all tasks, the
search text does not change the counts), selected `!settingsSelected && current == f`, click
`onSelect(f)`.

`SideFilter` (`UI/ui/TaskRows.kt` L505-542):

| Value         | Label member (English)                     | `matches(t)`                                                        |
|---------------|--------------------------------------------|---------------------------------------------------------------------|
| `ALL`         | `homeFilterAllDownloads` ("All downloads") | always                                                              |
| `DOWNLOADING` | `homeFilterDownloading` ("Downloading")    | `!t.waitingInQueue` and status in `DOWNLOADING, CONNECTING, QUEUED` |
| `WAITING`     | `homeFilterWaiting` ("Waiting")            | `t.waitingInQueue`                                                  |
| `PAUSED`      | `homeFilterPaused` ("Paused")              | `!t.waitingInQueue` and status in `PAUSED, IDLE`                    |
| `COMPLETED`   | `homeFilterCompleted` ("Completed")        | status `COMPLETED`                                                  |
| `FAILED`      | `homeFilterFailed` ("Failed")              | status `ERROR`                                                      |
| `MEDIA`       | `homeFilterMedia` ("Video & audio")        | `engineType == MEDIA_STREAM`                                        |
| `FILES`       | `homeFilterFiles` ("Files")                | `engineType == HTTP_DIRECT`                                         |
| `TORRENTS`    | `homeFilterTorrents` ("Torrents")          | `engineType == BITTORRENT`                                          |

No hover or key handling of its own; no scrolling (the column does not scroll, even in a short
window).

### 6.3
`internal fun FjSidebarItem(label: String, count: Int?, selected: Boolean, onClick: () -> Unit)` (L271-306)

```
Row(fillMaxWidth, padding vertical 1, clip corner 9,
    background = onSurface 8% if selected else transparent,
    clickable, padding horizontal 10, vertical 9, centred vertically)
  Text(label, bodyMedium, weight SemiBold if selected else Medium,
       colour onSurface if selected else onSurfaceVariant, weight 1)
  if count != null:
    Surface(shape corner 10, colour primary if selected else onSurface 7%)
      Text(count, 11 sp, Bold, colour onPrimary if selected else onSurfaceVariant,
           padding horizontal 7, vertical 1)
```

- Sizes **[derived]**: the badge text has line height 24 (rule 1 of 3.4), so the badge is 26
  high and `text width + 14` wide. A row with a badge is 26 + 18 = 44 high, 46 with its outer
  padding; the Settings row (no badge) is 20 + 18 = 38, 40 with padding. The label may wrap (no
  `maxLines`).
- Hover: Material state layer (`onBackground` 8%) over the rounded row; press: ripple 10%.
- Used by `FjSidebar` only.

### 6.4
`private fun SpeedControl(limitBytes: Long, lowBytes: Long, mediumBytes: Long, fastBytes: Long, onPick: (Long) -> Unit)` (L308-400)

Used by `FjTopBar` (both variants) and `FjPhoneHeader`. `private const val TURTLE_BYTES = 512L *
1024L`.

Computed values:

- `turtleActive = limitBytes == TURTLE_BYTES`.
- `modeLabel`, first match wins: `0` = `Strings.speedPresetHigh` ("High"); `lowBytes` =
  `speedPresetLow` ("Low"); `mediumBytes` = `speedPresetMedium` ("Medium"); `fastBytes` =
  `speedPresetFast` ("Fast"); anything else = `speedPresetShortLabel(limitBytes)` (for example
  `512 KB/s` while the turtle is on).
- `capped = limitBytes > 0 && !turtleActive`.
- `scheduled = SpeedSchedule.activeCap.value != null` (read directly from the shared-logic
  StateFlow `LOGIC/core/power/SpeedSchedule.kt` L58: a speed-schedule window is capping now).

```
Row(centred vertically, spacing 4)
  Box(clip corner 10, background primary 22% if turtleActive else transparent,
      clickable, padding horizontal 8, vertical 6)                      turtle button
    Text(U+1F422 turtle, 18 sp)
  Box
    Row(clip corner 10,
        background primary 18% if capped else surfaceVariant 40%,
        clickable = open the menu, padding horizontal 9, vertical 6, centred)   mode chip
      if scheduled: Text(U+1F552 clock + space, 11 sp)
      Text(modeLabel, 12 sp, SemiBold, primary if capped else onSurfaceVariant, 1 line)
      Text(" " + U+25BE, 10 sp, onSurfaceVariant)                       small down triangle
    DropdownMenu(expanded = open)
      4 x DropdownMenuItem
```

- Both parts are 36 high **[derived]** (24 line + 12 padding).
- Turtle click: `onPick(if (turtleActive) 0L else TURTLE_BYTES)`.
- UI-local state: `open` (Boolean, false).
- Menu options, in this order (two spaces around the middle dot U+00B7):
    1. value `lowBytes`: `"<speedPresetLow>  ·  <speedPresetShortLabel(lowBytes)>"`
    2. value `mediumBytes`: `"<speedPresetMedium>  ·  <speedPresetShortLabel(mediumBytes)>"`
    3. value `fastBytes`: `"<speedPresetFast>  ·  <speedPresetShortLabel(fastBytes)>"`
    4. value `0`: `"<speedPresetHigh>  ·  <speedLimitUnlimited>"` ("High · Unlimited (Max)")

  An option whose value equals `limitBytes` gets `"  " + U+2713` (check mark) appended; with
  equal presets several options are ticked. Click: close the menu, `onPick(value)`.
- "High" always means no limit (0); `state.speedPresetHighBytes` is not used here.
- `onPick` is wired to `OnGlobalDownloadSpeedLimitChanged(limitBytesPerSec)`, which updates the
  state, calls `queueRepository.setGlobalDownloadSpeedLimit` and saves the settings
  (`LOGIC/presentation/dashboard/DashboardSettingsEvents.kt` L24-38).

### 6.5
`internal fun FjTopBar(showSearch: Boolean, compact: Boolean = false, title: String, searchQuery: String, onSearchChange: (String) -> Unit, totalDlSpeed: Long, totalUlSpeed: Long, speedDisplayUnit: String, isDarkMode: Boolean, downloadLimitBytes: Long = 0L, speedPresetLowBytes: Long = 256L * 1024, speedPresetMediumBytes: Long = 2L * 1024 * 1024, speedPresetFastBytes: Long = 3L * 1024 * 1024, onPickDownloadLimit: (Long) -> Unit = {}, onThemeToggled: () -> Unit, onAdd: () -> Unit)` (L403-564)

Used by `App.kt` L339-368.

**Search field** (L422-444), a `FetchJetTextField` (`UI/ui/FetchJetTextField.kt` L72):

- `value = searchQuery`, `onValueChange = onSearchChange`, single line, height 44, corner 12.
- Placeholder `Strings.homeSearchDownloadsPlaceholder` ("Search downloads"), `bodyMedium`,
  `onSurfaceVariant` 65%.
- Leading: `Text(U+2315 telephone recorder, used as a magnifier, 17 sp, onSurfaceVariant)`,
  shown at 75% opacity, 100% when focused; field padding start 10, then 8 after the glyph.
- Trailing, only when the query is not empty: `FieldIconButton(U+2715 multiplication x)` = clear
  (`onSearchChange("")`): 30 dp circle, glyph 14 sp `onSurfaceVariant`, hover background
  `onSurface` 8% (150 ms); 6 before it and 6 after it. Without it the end padding is 14.
- Frame: container = `surfaceVariant` 35% over `surface`, focused = `primary` 6% over `surface`;
  border 1 dp `outlineVariant` 70%, hover `outline`, focus 1.5 dp `primary`; focus glow = drop
  shadow radius 10, offset y 2, `primary` at 28%; text `bodyMedium` in `onSurface`; caret
  `primary`; colour and border changes animate over 180 ms, the glow over 220 ms.
- No shortcut focuses it and it does not take focus by itself. The query filters by file name,
  link and original link, case-insensitive (`UI/TaskList.kt` L127-134).

**Wide variant** (`compact == false`, L508-563):

```
Row(fillMaxWidth, padding bottom 10, centred vertically, spacing 10)
  if showSearch: search field (weight 1)
  else:          Text(title, headlineSmall, Bold, weight 1)
  Surface(shape corner 12, colour surfaceVariant 45%)                    speeds chip
    Row(padding horizontal 12, vertical 10, spacing 10)
      Text(U+2193 + " " + formatSpeed(totalDlSpeed, unit), 12 sp, Bold, primary)
      Text(U+2191 + " " + formatSpeed(totalUlSpeed, unit), 12 sp, SemiBold, onSurfaceVariant)
  SpeedControl(...)
  IconButton(onThemeToggled)
    Text(U+2600 sun if isDarkMode else U+263E last quarter moon, 18 sp)
  Button(onAdd, shape corner 12, contentPadding horizontal 18, height 44)
    Text("+  " + Strings.homeAddDownload, Bold)                          "+  Add download"
```

- Row height 48 **[derived]** (the icon button); speeds chip 44 high; the button's visible shape
  is 44 high.
- With the search visible there is **no title** in the wide bar: the current filter is only shown
  by the sidebar selection.
- The theme glyph has no explicit colour: `onBackground`.

**Medium variant** (`compact == true`, L446-507):

```
Column(fillMaxWidth, padding bottom 10)
  Row(fillMaxWidth, centred vertically, spacing 10)
    Text(title, headlineSmall, Bold, 1 line, ellipsis, weight 1)
    Text(U+2193 + " " + formatSpeed(dl), 12 sp, Bold, primary, 1 line)        no chip background
    Text(U+2191 + " " + formatSpeed(ul), 12 sp, SemiBold, onSurfaceVariant, 1 line)
    SpeedControl(...)
    IconButton(onThemeToggled) { same glyph }
    Button(onAdd, shape corner 12, contentPadding horizontal 16, height 44)
      Text("+  " + Strings.homeAdd, Bold, 1 line)                             "+  Add"
  if showSearch:
    Spacer(height 10)
    search field (fillMaxWidth)
```

Events: `onSearchChange` (UI-local `searchQuery`), `onPickDownloadLimit`, `onThemeToggled`
(leaves "system" mode for good: it sends the opposite of the resolved theme), `onAdd`.

### 6.6
`internal fun FjPhoneHeader(tasks: List<DownloadTask>, showListActions: Boolean, totalDlSpeed: Long, totalUlSpeed: Long, speedDisplayUnit: String, sortOption: TaskSortOption, isDarkMode: Boolean, downloadLimitBytes: Long = 0L, speedPresetLowBytes: Long = 256L * 1024, speedPresetMediumBytes: Long = 2L * 1024 * 1024, speedPresetFastBytes: Long = 3L * 1024 * 1024, onPickDownloadLimit: (Long) -> Unit = {}, onPauseAll: () -> Unit, onResumeAll: () -> Unit, onSort: (TaskSortOption) -> Unit, onThemeToggled: () -> Unit, title: String)` (L567-671)

Compact layout only (`App.kt` L204-228).

```
Row(fillMaxWidth, padding start 16, end 8, top 12, bottom 6, centred vertically, spacing 6)
  Text(title, headlineSmall, Bold, weight 1)
  Column(aligned to the end)
    Text(U+2193 + " " + formatSpeed(dl), 12 sp, SemiBold, primary)
    Text(U+2191 + " " + formatSpeed(ul), 11 sp, onSurfaceVariant)
  SpeedControl(...)
  Box
    IconButton { Text(U+22EE vertical ellipsis, 22 sp, Bold) }            opens the menu
    DropdownMenu
```

UI-local state: `open` (menu), `sortOpen` (sort list inside the menu). Closing the menu also
closes the sort list.

Menu items:

1. Only if `showListActions` (`selectedTab != SETTINGS`), with `unfinished = tasks.filter {
   status != COMPLETED }`:
    - `Strings.homePauseAll` ("Pause all"), enabled when `anyPausable(unfinished)`; click: close,
      `OnPauseAllClicked`.
    - `Strings.homeResumeAll` ("Resume all"), enabled when `anyResumable(unfinished)`; click:
      close, `OnResumeAllClicked`.
    - `Strings.homeSortBy(sortOption.label) + "  "` + U+25BE (closed) or U+25B4 (open) ("Sort:
      Newest first"); click toggles `sortOpen`.
    - If `sortOpen`: one item per `TaskSortOption` (`DATE_DESC` `sysSortNewestFirst` "Newest
      first", `DATE_ASC` `sysSortOldestFirst` "Oldest first", `SIZE_DESC` `sysSortLargestFirst`
      "Largest first", `SIZE_ASC` `sysSortSmallestFirst` "Smallest first", `NAME_ASC`
      `sysSortNameAz` "Name A–Z"), text 13 sp, prefixed with U+2713 + two spaces for the current
      one and five spaces for the others; click: close both, `OnSortChanged(opt)`.
    - `HorizontalDivider`.
2. `Strings.homeLightTheme` ("Light theme") when dark, else `Strings.homeDarkTheme` ("Dark
   theme"); click: close, `onThemeToggled`.

`anyPausable(tasks)` = any status in `DOWNLOADING, CONNECTING, QUEUED`; `anyResumable(tasks)` =
any status in `PAUSED, ERROR` (`UI/TaskList.kt` L518-524).

### 6.7
`internal fun FjRail(tasks: List<DownloadTask>, current: SideFilter, settingsSelected: Boolean, onSelect: (SideFilter) -> Unit, onSettings: () -> Unit)` (L674-724)

Medium layout (`680 ..< 1100`), `App.kt` L288-303. This is what the default window size shows.

```
Surface(width 84, fillMaxHeight, colour surfaceVariant 30%)
  Column(fillMaxHeight, padding vertical 14, centred horizontally, spacing 4)
    Image(logo, 38 x 38, clip corner 11)
    Spacer(height 10)
    FjRailItem x 5
    Spacer(weight 1)
    FjRailItem(Strings.homeSettings, count = null, selected = settingsSelected,
               glyph = U+2699 gear, onClick = onSettings)
```

The five items use **short labels** and there is no Paused filter and no type filters:

| Filter        | Label member (English)          |
|---------------|---------------------------------|
| `ALL`         | `homeTabAll` ("All")            |
| `DOWNLOADING` | `homeTabActive` ("Active")      |
| `WAITING`     | `homeFilterWaiting` ("Waiting") |
| `COMPLETED`   | `homeTabDone` ("Done")          |
| `FAILED`      | `homeFilterFailed` ("Failed")   |

Count and selection as in the sidebar. If `sideFilter` is `PAUSED`, `MEDIA`, `FILES` or
`TORRENTS` (chosen earlier in the wide layout) no rail item is highlighted, the list stays
filtered and the top-bar title still shows that filter's label.

### 6.8
`internal fun FjRailItem(label: String, count: Int?, selected: Boolean, glyph: String? = null, onClick: () -> Unit)` (L726-770)

```
Column(width 68, clip corner 12,
       background onSurface 8% if selected else transparent,
       clickable, padding vertical 8, centred horizontally, spacing 5)
  Surface(shape corner 11,
          colour primary if (selected && count != null) else onSurface 7%)
    Text(count or glyph or "", 15 sp if glyph != null else 11 sp, Bold, centred,
         colour onPrimary if (selected && count != null),
                primary   if selected (the Settings item),
                onSurfaceVariant otherwise,
         min width 26 including padding, padding horizontal 6, vertical 2)
  Text(label, 11 sp, SemiBold, onSurface if selected else onSurfaceVariant, 1 line)
```

Sizes **[derived]**: badge 28 high, at least 26 wide; label 24 high; item 8 + 28 + 5 + 24 + 8 =
73 high. Hover and press: Material state layer over the rounded item.

---

## 7. `HomeDialogs.kt`

All four are `AlertDialog(onDismissRequest, shape = RoundedCornerShape(18.dp), title, text,
confirmButton, dismissButton)` with the Material defaults of 3.6: container
`surfaceContainerHigh` (dark `#2B2930`, light `#ECE6F0`), width 280 to 560, padding 24, title
`headlineSmall` + Bold in `onSurface`, body colour `onSurfaceVariant` unless stated, buttons at
the end edge with the confirm button last. Buttons: `Button(shape corner 10)` (filled, `primary`
/ `onPrimary`) and `OutlinedButton(shape corner 10)` (1 dp `outlineVariant` border, text
`onSurfaceVariant`), both 40 high, padding 24, text `labelLarge`. Scrim click and Escape call
`onDismissRequest`. No UI-local state, no key shortcuts (Enter does nothing).

### 7.1
`internal fun RestartOnPauseDialog(isClip: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit)` (L19-47)

Used by the details card (`UI/TaskDetails.kt` L101) when the user pauses a download that cannot
resume.

| Part               | Content                                                                                                                                                                                                                                                                                                                                                                            |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Title              | `Strings.homeCantResumeTitle` ("This download can't be resumed")                                                                                                                                                                                                                                                                                                                   |
| Text               | `bodyMedium`: `Strings.homeCantResumeClipText` if `isClip` ("Clips cut while downloading can't continue where they stopped. If you pause now, the clip will start again from the beginning when you resume.") else `Strings.homeCantResumeServerText` ("The server doesn't support resuming. If you pause now, the download will start again from the beginning when you resume.") |
| Confirm (filled)   | `Strings.homePauseAnyway` ("Pause anyway") = `onConfirm`                                                                                                                                                                                                                                                                                                                           |
| Dismiss (outlined) | `Strings.homeKeepDownloading` ("Keep downloading") = `onDismiss`                                                                                                                                                                                                                                                                                                                   |

### 7.2
`internal fun ClipboardOfferDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit)` (L49-101)

Used by `App.kt` L805-811.

`name` = `Strings.homeMagnetLink` ("Magnet link") if the URL starts with `magnet:` (ignoring
case); otherwise `url.substringBefore('?').substringAfterLast('/')`, or the whole URL when that
is blank.

| Part               | Content                                                                                                                                                                                                                                                                            |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Title              | `Strings.homeClipboardOfferTitle` ("Download the link you copied?")                                                                                                                                                                                                                |
| Text               | Column: `name` (`bodyMedium`, SemiBold, max 2 lines, ellipsis); 6 space; `url` (`bodySmall`, `onSurfaceVariant`, max 2 lines, ellipsis); 8 space; `Strings.homeClipboardOfferHint` ("Turn this off in Settings > General > Watch the clipboard.", `bodySmall`, `onSurfaceVariant`) |
| Confirm (filled)   | `Strings.homeDownload` ("Download") = `OnClipboardOfferAccepted` (the view model clears the offer, sends `OnUrlChanged(url)` and `OnToggleAddDialog(true)`)                                                                                                                        |
| Dismiss (outlined) | `Strings.homeNotNow` ("Not now") = `OnClipboardOfferDismissed`                                                                                                                                                                                                                     |

### 7.3
`internal fun BrowserFallbackDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit)` (L103-141)

Used by `App.kt` L843-849.

| Part               | Content                                                                                                                                                                                                                                                                                                                                                                                                                                |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Title              | `Strings.homeBrowserFallbackTitle` ("Download in browser instead?")                                                                                                                                                                                                                                                                                                                                                                    |
| Text               | Column: `Strings.homeBrowserFallbackText` ("You cancelled this file in FetchJet. The browser stopped its own download when it handed the link over, so it will only continue if you send it back.", `bodyMedium`); 8 space; `url.substringAfterLast('/').substringBefore('?')`, or the whole URL when blank (`bodySmall`, `onSurfaceVariant`, max 2 lines, ellipsis). Note the order of the two substring calls is the reverse of 7.2. |
| Confirm (filled)   | `Strings.homeDownloadInBrowser` ("Download in browser") = `OnConfirmBrowserFallback` (clears the URL, `ExternalDownloadBridge.triggerFallback(url)`)                                                                                                                                                                                                                                                                                   |
| Dismiss (outlined) | `Strings.homeNoDiscard` ("No, discard") = `OnDismissBrowserFallback`                                                                                                                                                                                                                                                                                                                                                                   |

### 7.4
`internal fun BridgePairingDialog(request: BridgePairing.Request, onAllow: () -> Unit, onBlock: () -> Unit, onDismiss: () -> Unit)` (L144-185)

Used by `App.kt` L814-827. `BridgePairing.Request(val origin: String, val legacy: Boolean)`
(`LOGIC/core/network/BridgePairing.kt` L52); `BridgePairing.pending: StateFlow<Request?>` (L99).

| Part                             | Content                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Title                            | `Strings.bridgePairTitle` ("Allow this browser extension?")                                                                                                                                                                                                                                                                                                                                                                                                |
| Text                             | Column: `Strings.bridgePairOldText` if `request.legacy` ("An older FetchJet extension wants to send links. Allow it if it is yours; updating the extension makes this question unnecessary.") else `Strings.bridgePairText` ("A browser extension asks to send links to FetchJet. Allow it only if you just installed or reset the FetchJet extension."), `bodyMedium`; 8 space; `request.origin` (`bodySmall`, `onSurfaceVariant`, max 2 lines, ellipsis) |
| Confirm slot (filled, prominent) | `Strings.bridgePairBlock` ("**Block**") = `BridgePairing.block()`                                                                                                                                                                                                                                                                                                                                                                                          |
| Dismiss slot (outlined)          | `Strings.bridgePairAllow` ("Allow") = `BridgePairing.answer(true)`                                                                                                                                                                                                                                                                                                                                                                                         |
| Scrim click / Escape             | `BridgePairing.answer(false)` (a one-time "no", not remembered)                                                                                                                                                                                                                                                                                                                                                                                            |

Keep Block as the prominent button on purpose. The request is answered `false` by the logic
after 2 minutes; `pending` then becomes null and the dialog must close by itself.

---

## 8. `SettingsContent.kt`

`internal fun SettingsContentView(state: DashboardUiState, viewModel: DashboardViewModel,
pathResolver: PathResolver)` (L9-111). Used by `App.kt` L237 and L371. It has no layout of its
own: it calls `FdmSettingsPanel` (`UI/ui/FdmSettingsPanel.kt` L93, settings document) with:

- State: `destinationPath, disableTorrentUpload, isDarkMode (= state.isDarkMode, not the resolved
  theme), globalDownloadSpeedLimitBytes, globalUploadSpeedLimitBytes, speedDisplayUnit,
  speedPresetLowBytes / LowUnit, speedPresetMediumBytes / MediumUnit, speedPresetFastBytes /
  FastUnit, speedPresetHighBytes / HighUnit, ytDlpStatus, isYtDlpInstalled, isFfmpegInstalled,
  jsRuntimeName, isJsRuntimeReady, toolsDirectoryPath, torrentEngineStatus, isTorrentEngineReady,
  isDownloadingYtDlp, toolDownloadName, toolDownloadFraction, monitorClipboard,
  clipboardVideoPages, sortIntoFolders, defaultConnections, themeMode, pairedDevices,
  blockedDevices`, plus `pathResolver`.
- Events: `OnDestinationPathChanged(path)`, `OnMonitorClipboardChanged(on)`,
  `OnClipboardVideoPagesChanged(on)`, `OnSortIntoFoldersChanged(on)`, `OnThemeModeChanged(mode)`,
  `OnUnpairDeviceClicked(device)`, `OnUnblockDeviceClicked(device)`,
  `OnDefaultConnectionsChanged(n)`, `OnDisableTorrentUploadToggled(on)`, `OnThemeToggled`,
  `OnGlobalDownloadSpeedLimitChanged(bytes)`, `OnGlobalUploadSpeedLimitChanged(bytes)`,
  `OnSpeedDisplayUnitChanged(unit)`, `OnSpeedPresetChanged(preset, bytes, unit)`,
  `OnDownloadYtDlpClicked`, `OnLanguageChanged(choice)`, `OnApplySettingsClicked`.

In the non-compact layouts it sits directly in the content column under the top bar (which then
shows the title "Settings" instead of the search field); in the compact layout inside the padded
content box.

---

## 9. `AppScrollbar.kt`

- `fun AppScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier)` (L43-54): for a
  scrolling column. `maxScroll = scrollState.maxValue`, `current = scrollState.value`, `active =
  isScrollInProgress`, jump = `scrollTo(target)`, drag = `scrollBy(delta)`.
- `fun AppScrollbar(listState: LazyListState, modifier: Modifier = Modifier)` (L56-77): for a
  lazy list, with estimated sizes: `avgItem` = average size of the visible items (0 when none),
  `content = avgItem * totalItemsCount` (spacing is ignored), `viewport = viewportEndOffset -
  viewportStartOffset`, `maxScroll = max(content - viewport, 0)`, `current =
  firstVisibleItemIndex * avgItem + firstVisibleItemScrollOffset`; jump = `scrollBy(target -
  current)`.
- `private fun ScrollbarCore(maxScroll: Float, currentScroll: Float, active: Boolean, onScrollTo:
  (Float) -> Unit, onScrollBy: (Float) -> Unit, modifier: Modifier)` (L79-138).

Used as the last child of a `Box` around the scrolling content, aligned to the end edge:
`UI/TaskList.kt` L357 (task list), `UI/ui/AddDownloadModalDialog.kt` L516,
`UI/ui/FdmSettingsPanel.kt`
L296 and L354.

Behaviour and look:

- Drawn only on the desktop (`getPlatform().isJvm`) and only when `maxScroll > 0`.
- Hit area: full height, 12 wide, 2 padding at the top and bottom. Thumb: 6 wide, at the end
  edge of that area, corner 3, colour `onSurface` at 28%, or 55% while the pointer is over the
  12 dp area or the content is scrolling; the change animates over 180 ms. No visible track.
- Geometry, in pixels, with `track` = area height: `thumb = clamp(track * track / (track +
  maxScroll), 28, track)` (the minimum is 28 pixels, not dp); `travel = max(track - thumb, 1)`;
  `thumbTop = clamp(current / maxScroll * travel, 0, travel)`.
- Click on the area: `onScrollTo(clamp((y - thumb / 2) / travel, 0, 1) * maxScroll)` (the thumb
  centres on the click).
- Drag on the thumb: `onScrollBy(dragY / travel * maxScroll)`.
- The mouse wheel is handled by the scrolling container, not by this component.

---

## 10. `SafeClipboard.kt`

`fun ProvideSafeClipboard(content: @Composable () -> Unit)` (L11-35): replaces `LocalClipboard`
with a wrapper whose `getClipEntry()` returns `null` and whose `setClipEntry()` does nothing when
the platform clipboard throws (it can be locked by another program on Windows). Used around the
Add dialog (`UI/ui/AddDownloadModalDialog.kt` L300) and the folder picker
(`UI/ui/DirectoryPickerDialog.kt` L84); `main.kt` L908-931 has an identical inline copy around
`App()`.

Nothing to draw. Port rule: every clipboard read or write (text-field copy / paste, the Paste
and Copy buttons, the clipboard watcher) must fail silently, never crash.

---

## 11. Strings and glyphs used in this area

All members are on `LocalizationManager.Strings` (`AppLocalization`,
`LOGIC/core/i18n/data_provider/`; English in `EnglishProvider.kt`).

| Member                                                                                                                | English                                                                 | Where                             |
|-----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|-----------------------------------|
| `desktopWindowTagline`                                                                                                | High-Velocity Multi-Engine Downloader                                   | window title                      |
| `desktopTrayIdleTooltip`                                                                                              | FetchJet - Running in Background                                        | tray                              |
| `desktopTrayDownloadsHeader`                                                                                          | FetchJet Downloads:                                                     | tray                              |
| `desktopTrayMore(count)`                                                                                              | +N more                                                                 | tray                              |
| `desktopTrayShowApp`                                                                                                  | Show App                                                                | tray                              |
| `desktopTrayCloseApp`                                                                                                 | Close App                                                               | tray                              |
| `whenDoneGroup`                                                                                                       | When downloads finish                                                   | tray                              |
| `whenDoneNothing` / `whenDoneExit` / `whenDoneSleep` / `whenDoneShutDown`                                             | Do nothing / Exit FetchJet / Sleep / Shut down                          | tray                              |
| `homeSettings`                                                                                                        | Settings                                                                | sidebar, rail, bottom bar, titles |
| `homeDownloads`                                                                                                       | Downloads                                                               | phone header title                |
| `homeTabAll` / `homeTabActive` / `homeTabDone` / `homeTabTorrent` / `homeTabMedia`                                    | All / Active / Done / Torrent / Media                                   | bottom bar, rail                  |
| `homeSidebarTypeHeader`                                                                                               | TYPE                                                                    | sidebar                           |
| `homeFilterAllDownloads`                                                                                              | All downloads                                                           | sidebar, title                    |
| `homeFilterDownloading`                                                                                               | Downloading                                                             | sidebar, title                    |
| `homeFilterWaiting`                                                                                                   | Waiting                                                                 | sidebar, rail, title              |
| `homeFilterPaused`                                                                                                    | Paused                                                                  | sidebar, title                    |
| `homeFilterCompleted`                                                                                                 | Completed                                                               | sidebar, title                    |
| `homeFilterFailed`                                                                                                    | Failed                                                                  | sidebar, rail, title              |
| `homeFilterMedia`                                                                                                     | Video & audio                                                           | sidebar, title                    |
| `homeFilterFiles`                                                                                                     | Files                                                                   | sidebar, title                    |
| `homeFilterTorrents`                                                                                                  | Torrents                                                                | sidebar, title                    |
| `homeSearchDownloadsPlaceholder`                                                                                      | Search downloads                                                        | top bar                           |
| `homeAdd`                                                                                                             | Add                                                                     | medium top bar                    |
| `homeAddDownload`                                                                                                     | Add download                                                            | wide top bar                      |
| `speedPresetHigh` / `speedPresetFast` / `speedPresetMedium` / `speedPresetLow`                                        | High / Fast / Medium / Low                                              | speed control                     |
| `speedLimitUnlimited`                                                                                                 | Unlimited (Max)                                                         | speed control menu                |
| `homePauseAll` / `homeResumeAll`                                                                                      | Pause all / Resume all                                                  | phone menu                        |
| `homeSortBy(option)`                                                                                                  | Sort: `<option>`                                                        | phone menu                        |
| `sysSortNewestFirst` / `sysSortOldestFirst` / `sysSortLargestFirst` / `sysSortSmallestFirst` / `sysSortNameAz`        | Newest first / Oldest first / Largest first / Smallest first / Name A–Z | phone menu                        |
| `homeLightTheme` / `homeDarkTheme`                                                                                    | Light theme / Dark theme                                                | phone menu                        |
| `homeDownloadingWaitingSummary(running, waiting)`                                                                     | N downloading · M waiting                                               | status bar                        |
| `homeQueueStatus(maxAtOnce)`                                                                                          | Queue: no limit / Queue: N at a time                                    | status bar                        |
| `homeSpeedLimitStatus(limit)`                                                                                         | Speed limit: off / Speed limit: `<limit>`                               | status bar                        |
| `homeCantResumeTitle`, `homeCantResumeClipText`, `homeCantResumeServerText`, `homePauseAnyway`, `homeKeepDownloading` | see 7.1                                                                 | dialog                            |
| `homeMagnetLink`, `homeClipboardOfferTitle`, `homeClipboardOfferHint`, `homeDownload`, `homeNotNow`                   | see 7.2                                                                 | dialog                            |
| `homeBrowserFallbackTitle`, `homeBrowserFallbackText`, `homeDownloadInBrowser`, `homeNoDiscard`                       | see 7.3                                                                 | dialog                            |
| `bridgePairTitle`, `bridgePairText`, `bridgePairOldText`, `bridgePairAllow`, `bridgePairBlock`                        | see 7.4                                                                 | dialog                            |

Hard-coded texts: `FetchJet` (sidebar brand, window title prefix), `"v" + APP_VERSION`, the `+`
of the add buttons, the unit suffixes of the formatters (`B, KB, MB, GB, TB, B/s, KB/s, MB/s,
Kbps, Mbps, h, m, s`).

Glyphs (all drawn as text in Compose; the emoji ones are colour emoji from Segoe UI Emoji):

| Glyph                                                              | Code point                                | Where                          |
|--------------------------------------------------------------------|-------------------------------------------|--------------------------------|
| turtle                                                             | U+1F422                                   | speed control                  |
| clock (three o'clock)                                              | U+1F552                                   | speed control, schedule active |
| small down / up triangle                                           | U+25BE / U+25B4                           | mode chip, phone sort item     |
| check mark                                                         | U+2713                                    | menus                          |
| middle dot                                                         | U+00B7                                    | speed menu labels              |
| telephone recorder (magnifier look)                                | U+2315                                    | search field                   |
| multiplication x                                                   | U+2715                                    | clear search                   |
| down / up arrow                                                    | U+2193 / U+2191                           | speeds                         |
| sun / last quarter moon                                            | U+2600 / U+263E                           | theme button                   |
| gear                                                               | U+2699 (rail), U+2699 U+FE0F (bottom bar) | settings                       |
| vertical ellipsis                                                  | U+22EE                                    | phone menu                     |
| inbox tray, high voltage, check mark button, magnet, clapper board | U+1F4E5, U+26A1, U+2705, U+1F9F2, U+1F3AC | bottom bar                     |

The prototype runs Slint with the `winit-software` backend and already replaces the search, sun,
moon and chevron glyphs with vector paths (`GlyphIcon` in `desktopNative/ui/app.slint`). Colour
emoji (turtle, clock) will most likely not render with that backend: draw them as images or
paths (risk 2).

---

## (a) Reusable widgets the Slint port needs for this area

| Widget                               | Behaviour                                                                                                                                             | Visual spec                                                                                                                           |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `Theme` global                       | `dark` flag, every role of 3.2, status colours of 3.1, type roles of 3.4                                                                              | extend `desktopNative/ui/theme.slint`                                                                                                 |
| `StateLayer` (hover / press overlay) | fills its parent, follows its corner radius; shows the content colour at 8% on hover, 10% while pressed                                               | used by every clickable below; a plain overlay is enough (no ripple animation)                                                        |
| `NavItem`                            | label, optional count, selected, click                                                                                                                | 6.3                                                                                                                                   |
| `RailItem`                           | label, optional count or glyph, selected, click                                                                                                       | 6.8                                                                                                                                   |
| `CountBadge`                         | number, selected                                                                                                                                      | sidebar: corner 10, 26 high, padding 7; rail: corner 11, 28 high, min width 26; 11 px Bold; colours in 6.3 / 6.8                      |
| `FjTextField`                        | text, placeholder, leading and trailing slots, height, error, focus glow                                                                              | summary in 6.5; full spec with the Add dialog document                                                                                |
| `FieldIconButton`                    | 30 px round button with a glyph                                                                                                                       | 6.5                                                                                                                                   |
| `ChipButton`                         | rounded (corner 10) clickable chip with content                                                                                                       | turtle button and mode chip, 6.4                                                                                                      |
| `SpeedControl`                       | turtle toggle + mode chip + menu                                                                                                                      | 6.4                                                                                                                                   |
| `PopupMenu` + `MenuItem`             | anchored under a widget, closes on outside click and Escape, items can be disabled                                                                    | 3.6: container `surfaceContainer`, corner 4, shadow, vertical padding 8, item 48 high, padding 12, 14 px weight 500, width 112 to 280 |
| `IconButton`                         | 48 px box, 40 px round state layer, glyph or path                                                                                                     | 3.6                                                                                                                                   |
| `FilledButton`                       | text, enabled, corner radius, height, horizontal padding                                                                                              | 3.6; 14 px, min width 58, `primary` / `onPrimary`, disabled colours                                                                   |
| `OutlinedButton`                     | same                                                                                                                                                  | 3.6; border 1 px `outlineVariant`, text `onSurfaceVariant`                                                                            |
| `AlertDialog`                        | modal overlay in the window: scrim black 60%, card centred, title, body slot, 1-2 buttons, scrim click / Escape = dismiss                             | 3.6 and 7: corner 18, container `surfaceContainerHigh`, width 280 to 560, padding 24, title 24 / 32 Bold, body 14 / 20                |
| `Toast` (snack bar)                  | queue of texts, 4 s each, fade + scale                                                                                                                | 3.6 and 5.7                                                                                                                           |
| `FjScrollbar`                        | overlay scrollbar bound to a `Flickable` (viewport offset and size): thumb drag, click to jump, hover highlight                                       | 9                                                                                                                                     |
| `BottomSheet`                        | modal overlay: scrim, sheet at the bottom with top corners 22, handle (click = expand / collapse, drag), content slot, outside click / Escape = close | 5.5                                                                                                                                   |
| `SidePanel`                          | 340 px surface with a 1 px start divider                                                                                                              | 5.3                                                                                                                                   |
| `StatusBar`                          | four texts in a row                                                                                                                                   | 5.3                                                                                                                                   |
| `GlyphIcon`                          | vector replacements for the glyphs of section 11                                                                                                      | exists in the prototype for 4 glyphs                                                                                                  |

Slint notes: `Text` has no line-height setting that matches Compose's fixed line boxes, so give
texts the explicit heights listed as **[derived]** and centre them vertically. Slint has no
automatic RTL mirroring: build rows from a `Theme.rtl` flag (risk 3).

---

## (b) Port units

| Unit                                              | Compose source                                                                                                                                              | Lines                  | Depends on                                                                  | State from the presenter                                                                                                                                                                                        | Events to the presenter                                                                     |
|---------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **S1 Theme and base widgets**                     | `theme/Color.kt` 1-79, `theme/Theme.kt` 1-67, Material defaults of 3.6                                                                                      | about 150 + widget kit | nothing                                                                     | `theme-dark`, `rtl`                                                                                                                                                                                             | none                                                                                        |
| **S2 Formatters and shell helpers (Kotlin only)** | `utils/Formatters.kt` 1-171, `ui/SpeedPresetUi.kt` 30-36, `SideFilter` in `ui/TaskRows.kt` 505-542, `anyPausable` / `anyResumable` in `TaskList.kt` 518-524 | about 230              | nothing                                                                     | n/a                                                                                                                                                                                                             | n/a (unit tests from the tables of section 4)                                               |
| **S3 Window host, tray, single instance**         | `main.kt` 51-135 and 690-934                                                                                                                                | about 330              | C++ host, Slint `SystemTrayIcon`                                            | window title, tray tooltip, When-done choice, notifications to show, "show and focus" requests, start hidden                                                                                                    | show app, quit, When-done choice, window close (hide or quit)                               |
| **S4 Root shell**                                 | `App.kt` 81-91, 129-200, 283-442, 877-882; `TaskList.kt` 422-472 (status bar)                                                                               | about 330              | S1; slots for the task list, details, settings                              | settings-selected flag, status-bar texts (3 + version), selected task present, toast text queue                                                                                                                 | none of its own (layout by window width stays in Slint)                                     |
| **S5 Sidebar and rail**                           | `HomeNavigation.kt` 186-306, 673-770                                                                                                                        | about 220              | S1 (`NavItem`, `RailItem`, `CountBadge`)                                    | 9 filter entries (label, short label, count, selected, visible), settings selected, feature flags, "TYPE" and "Settings" texts                                                                                  | filter selected (index), settings selected                                                  |
| **S6 Top bar and speed control**                  | `HomeNavigation.kt` 308-564                                                                                                                                 | about 260              | S1 (`FjTextField`, `ChipButton`, `PopupMenu`, `IconButton`, `FilledButton`) | title, show-search, search text, placeholder, down / up speed texts, turtle active, mode label, capped, scheduled, 4 menu options (label, checked), dark flag, add-button texts (short and long)                | search changed, turtle toggled, speed option picked (index), theme toggled, add clicked     |
| **S7 Home alert dialogs and dialog host**         | `HomeDialogs.kt` 19-185, `App.kt` 775-875                                                                                                                   | about 290              | S1 (`AlertDialog`, buttons)                                                 | one record per dialog: clipboard offer (name, url), browser fallback (file name), bridge pairing (origin, legacy), restart-on-pause (is clip), missing file (file name), remove confirmation (count); all texts | confirm / dismiss per dialog (table in 5.6, section 7)                                      |
| **S8 Details container**                          | `App.kt` 409-439, 444-550                                                                                                                                   | about 140              | S1 (`BottomSheet`, `SidePanel`), details document                           | selected task id                                                                                                                                                                                                | close details                                                                               |
| **S9 Shell logic in the presenter (Kotlin only)** | `App.kt` 62-77, 92-185                                                                                                                                      | about 130              | S2, native actual of `getClipboardText`                                     | n/a                                                                                                                                                                                                             | n/a: clipboard watcher, theme resolution, language / RTL, toast queue, `openDownloadedFile` |
| **S10 Scrollbar**                                 | `ui/AppScrollbar.kt` 43-138                                                                                                                                 | about 100              | S1                                                                          | none (reads the `Flickable`)                                                                                                                                                                                    | none                                                                                        |
| **S11 Compact layout (optional)**                 | `App.kt` 202-282, `HomeNavigation.kt` 68-184, 566-671                                                                                                       | about 300              | S1, S6 (`SpeedControl`, `PopupMenu`)                                        | tab counts, selected tab, sort option and labels, pausable / resumable flags                                                                                                                                    | tab selected, pause all, resume all, sort changed, theme toggled, add clicked               |

`SettingsContent.kt` (1-111) and the Add-dialog wiring (`App.kt` 552-773) are pure wiring: port
them with the settings and Add-dialog units; the field and event lists are in section 8 and 5.6.

Suggested order: S1, S2, S9, S4 + S5 + S6 (in parallel), S7, S8, S10, S3; S11 only if the
compact layout is kept.

---

## (c) UI-local state to move into the Kotlin presenter, and helpers to reproduce

### State

| State today (Compose)                                           | Move to presenter as                                       | Rules                                                                                                                                                                                                                                                                |
|-----------------------------------------------------------------|------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sideFilter` (`App.kt` L133)                                    | `sideFilter: SideFilter`                                   | initial `ALL`; selecting a filter also sends `OnTabSelected(ALL)`; kept while Settings is open; not saved                                                                                                                                                            |
| `searchQuery` (L134)                                            | `searchQuery: String`                                      | initial empty; filter rule in 6.5; kept when the language changes and while Settings is open                                                                                                                                                                         |
| `selectedTaskId` (L131)                                         | `selectedTaskId: String?`                                  | details are shown only while the task exists and the tab is not Settings; kept when the language changes                                                                                                                                                             |
| `sheetExpanded`, `dragTotal` (L452, L487)                       | keep in Slint                                              | pure presentation                                                                                                                                                                                                                                                    |
| `wideLayout` (L140)                                             | keep in Slint, or one "layout is not compact" notification | when it turns true and the tab is not `ALL` / `SETTINGS`: `OnTabSelected(ALL)`                                                                                                                                                                                       |
| `clipboardFirstRun` + watcher loop (L95-128)                    | presenter coroutine                                        | 5.2 effect 1                                                                                                                                                                                                                                                         |
| `missingFileTaskId` (L62)                                       | presenter field                                            | 5.1, 5.6 row 3                                                                                                                                                                                                                                                       |
| `snackbarHostState` (L170)                                      | presenter toast queue fed by `uiEffect`                    | one at a time, 4 s                                                                                                                                                                                                                                                   |
| resolved `darkMode` (L151-156)                                  | presenter or Slint                                         | `themeMode` "light" / "dark" / anything else = system. Slint can resolve "system" itself with `Palette.color-scheme`; the toggle sends `OnThemeModeChanged(if (dark) "light" else "dark")`                                                                           |
| `open` of the speed menu, `open` / `sortOpen` of the phone menu | keep in Slint                                              |                                                                                                                                                                                                                                                                      |
| direct reads of shared logic from the UI                        | presenter collects them                                    | `SpeedSchedule.activeCap`, `BridgePairing.pending`, `QueueManagerRepository.maxConcurrentDownloads`, `AppLanguage.current` / `isRtl`, `AppFeatures.torrent` / `mediaExtractor`, `WhenDone.action`, `DownloadNotificationManager.activeTasks`, `SystemNotice.notices` |

### Derived values the presenter must compute

1. **Navigation entries**: for each `SideFilter`, `count = allTasks.count { f.matches(it) }` and
   `selected = selectedTab != SETTINGS && sideFilter == f` (table in 6.2). Sidebar order `ALL,
   DOWNLOADING, WAITING, PAUSED, COMPLETED, FAILED`, then `MEDIA` (if `mediaExtractor`), `FILES`,
   `TORRENTS` (if `torrent`). Rail: `ALL, DOWNLOADING, WAITING, COMPLETED, FAILED` with the short
   labels of 6.7.
2. **Visible list** in non-compact layouts: `state.filteredTasks` (tab `ALL`, already sorted by
   `filterTasks`, `LOGIC/presentation/dashboard/DashboardFilters.kt` L11-47) `.filter {
   sideFilter.matches(it) }`, then the search filter, then grouping when `sideFilter in {ALL,
   MEDIA, FILES, TORRENTS}` (grouping rules: task-list document, `taskStateGroups`,
   `TaskList.kt` L483-516).
3. **Top-bar title**: `homeSettings` when the tab is `SETTINGS`, else `sideFilter.label`.
4. **Speeds**: `"↓ " + formatSpeed(totalDownloadSpeedBytesPerSec, speedDisplayUnit)` and `"↑ " +
   formatSpeed(totalUploadSpeedBytesPerSec, speedDisplayUnit)`.
5. **Speed control**: `turtleActive`, `modeLabel`, `capped`, `scheduled`, the four option labels
   and their tick (6.4); `speedPresetShortLabel` (4.9).
6. **Status bar**: the three texts of 5.3 and `"v" + APP_VERSION`.
7. **Dialog texts**: `name` of the clipboard offer (7.2), file name of the browser fallback (7.3),
   text choice of the pairing dialog (7.4) and of the restart dialog (7.1).
8. **Tray tooltip** (1.5).
9. **Compact layout only**: tab counts (5.4), `anyPausable` / `anyResumable` over the unfinished
   tasks (6.6).
10. **Formatters** of section 4, with unit tests built from its tables.

---

## Risks

1. **Baseline Material colours are part of the look.** Dialogs (`#2B2930` / `#ECE6F0`), menus
   (`#211F26` / `#F3EDF7`) and toasts use purple-grey baseline roles, not the app palette. Copy
   them for "the same look", or ask the owner if he prefers the app's `surface` colours there.
2. **Emoji and symbol glyphs.** The app draws icons as text (turtle, clock, gear, magnifier...).
   With Slint's software renderer colour emoji will most likely be missing; they need image or
   path replacements, and the result should be compared with the Compose app.
3. **RTL.** Arabic and Persian mirror the whole layout in Compose. Slint does not mirror layouts
   by itself and its bidirectional text support must be checked early, with Arabic strings.
4. **Isolate characters.** `ltr()` adds U+2066 / U+2069; they must be invisible in Slint.
5. **Line boxes.** Many sizes come from Compose's 24 dp line box for texts with only a font size
   (3.4 rule 1). Heights marked [derived] were not measured; compare one screenshot of the JVM
   app (sidebar row pitch 46, rail item 73, chips 36 / 44) before fixing the numbers in the
   widgets.
6. **Number formatting.** `Double.toString()` output must be the same in the presenter build
   (`1.0 KB`, `6.41 MB/s`); cover it with tests.
7. **Font weight 500.** Segoe UI has no Medium face; Compose most likely shows Regular. If Slint
   picks Semibold for 500 the labels will look heavier.
8. **Material version.** The defaults here are from `material3 1.12.0-alpha03`; older Material
   documentation gives different values (outlined button text colour, tracking, dialog button
   spacing).

## Open questions

1. Minimum window size: the JVM app has none (the phone layout appears below 680 dp). Should the
   native window enforce a minimum (the prototype uses 900 x 500) and drop the compact layout (unit
   S11)?
2. Default size: keep 1100 x 750 outer size, which opens in the rail layout (about 1084 dp of
   content), or open slightly wider so the full sidebar shows at start?
3. Width caps: the code asks for a 1400 dp cap on the content column and 820 dp on the details
   sheet, but as written they have no effect. Keep the real behaviour (full width) or apply the
   intended caps? Needs one look at the JVM app on a wide screen to confirm.
4. Title bar: the JVM window keeps the system's light title bar in dark mode. Match it, or use a
   dark title bar in the native app?
5. Toast position while the details sheet is open: Compose shows it 24 dp above the bottom
   inside the sheet layer and 88 dp above the bottom otherwise. One position for both in Slint?
6. The clipboard watcher keeps polling every 800 ms while the window is hidden in the tray. Keep
   polling, or use the Windows clipboard-change notification in the native host (same result,
   less work for the CPU)?
