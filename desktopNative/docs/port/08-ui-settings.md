# 08 - UI porting spec: Settings pages

Scope: the Settings tab of the FetchJet desktop app (Compose Multiplatform, JVM) as it must be
rebuilt in Slint for the native Windows app. The Slint side only renders state computed by a Kotlin
presenter and sends events back.

All paths are relative to `E:/tbib/FetchJet`. `UI` =
`androidUi/src/commonMain/kotlin/com/michelle/raouf/fetch_jet`, `LOGIC` =
`sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet`.

Sizes are Compose dp (= Slint logical px). "sp" equals dp at the default font scale.

## 0. Source files

Assigned files:

| File                              | Lines | Contents                                                                                                                                                                                                            |
|-----------------------------------|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `UI/SettingsContent.kt`           | 111   | `SettingsContentView` (state/event glue between `DashboardUiState` and the panel)                                                                                                                                   |
| `UI/ui/FdmSettingsPanel.kt`       | 433   | `SettingsSection`, `FdmSettingsPanel`, `PageHeader`, `SectionNavItem`                                                                                                                                               |
| `UI/ui/SettingsKit.kt`            | 197   | `SettingsGroup`, `SettingDivider`, `SettingRow`, `SettingSwitch`, `SegmentedPicker`, `StatusLabel`                                                                                                                  |
| `UI/ui/SettingsPages.kt`          | 801   | `GeneralPage`, `DevicesPage`, `DeviceListGroup`, `DownloadsPage`, `SpeedScheduleCard`, `TimeField`, `AmPmToggle`, `TorrentPage`, `BrowserExtensionPage`, `BrowserConnectionsGroup`, `LanguageSettingsCard`, helpers |
| `UI/ui/SettingsMediaToolsPage.kt` | 154   | `MediaToolsPage`                                                                                                                                                                                                    |
| `UI/ui/SpeedPresetRow.kt`         | 136   | `SpeedPresetRow`                                                                                                                                                                                                    |
| `UI/ui/SpeedPresetUi.kt`          | 36    | `speedPresetUnits`, `speedPresetToBytes`, `speedPresetValueText`, `speedPresetShortLabel`                                                                                                                           |
| `UI/ui/TrafficLimitSelector.kt`   | 252   | `TrafficLimitSelector`                                                                                                                                                                                              |
| `UI/ui/StartupUi.kt`              | 108   | `StartupSettingsCard`                                                                                                                                                                                               |

Files outside the assignment that are rendered inside settings pages (their settings parts are
specified here because a page cannot be ported without them):

| File                             | Part used                                                                                 | Lines                                               |
|----------------------------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------|
| `UI/ui/NetworkSettingsUi.kt`     | `networkSummary`, `NetworkPage`, `ProxyCard`, `SiteLoginsCard`                            | 36-227                                              |
| `UI/ui/QueueUi.kt`               | `QueueSettingsCard`                                                                       | 19-47                                               |
| `UI/ui/WhenDoneUi.kt`            | `whenDoneLabel`, `WhenDoneSettingsCard`                                                   | 34-64                                               |
| `UI/ui/UpdateUi.kt`              | `UpdatesSettingsCard`                                                                     | 217-294                                             |
| `UI/ui/BrowserInstallSection.kt` | `BrowserInstallRows` and its 4 dialogs                                                    | 45-233                                              |
| `UI/ui/FetchJetTextField.kt`     | `FetchJetTextField`, `FieldIconButton`                                                    | 72-401                                              |
| `UI/ui/AppScrollbar.kt`          | `AppScrollbar`                                                                            | 44-138                                              |
| `UI/ui/DirectoryPickerDialog.kt` | `DirectoryPickerDialog(initialPath, pathResolver, onDirectorySelected, onDismissRequest)` | 52 (dialog itself is specified by the dialogs spec) |

## 1. Hosting, breakpoints, window size

### 1.1 Where the panel lives

`UI/App.kt`:

- Line 194-200: `screenWidth = maxWidth` of the window content; `isCompact = screenWidth < 680.dp`;
  `isMedium = screenWidth in 680.dp..<1100.dp` (icon rail, 84 dp wide); otherwise full sidebar (232
  dp wide); `sidePanel = screenWidth >= 1280.dp` (task details panel, never shown on the Settings
  tab, line 410).
- Line 330-375 (non-compact layout):
  `Row { FjRail | FjSidebar ; Box(weight 1) { Column(fillMaxSize, widthIn(max = 1400.dp), padding(horizontal = 16.dp, vertical = 10.dp)) { FjTopBar(...); if (state.selectedTab == DashboardTab.SETTINGS) SettingsContentView(state, viewModel, pathResolver) else ... } } }`.
- On the Settings tab the top bar is called with `showSearch = false` and
  `title = Strings.homeSettings` (line 340-342). The queue status bar and the task list are not
  shown.
- The Settings tab is entered with `DashboardUiEvent.OnTabSelected(DashboardTab.SETTINGS)`
  (sidebar / rail "Settings" item) and left with `OnTabSelected(DashboardTab.ALL)`.
- `Modifier.widthIn(max = 1400.dp)` is applied after `fillMaxSize()`, so by Compose modifier rules
  it cannot shrink the column: treat the content as full width.
- The root `Surface` (line 187-190) has `clearFocusOnTapOutside()`: a click outside the focused text
  field clears focus.
- The whole UI is wrapped in `key(language)` (line 179-180) and `LocalLayoutDirection` = RTL when
  `AppLanguage.isRtl` (ar, fa). Changing the language recreates the whole tree, so every `remember`
  in the settings pages resets (open section goes back to the first one, drafts are lost). RTL
  mirrors every start/end padding and row order described below.

`SettingsContentView` (`UI/SettingsContent.kt:10-111`) only forwards state and wraps callbacks into
`DashboardUiEvent`s. `FdmSettingsPanel` fills the rest of the column
(`BoxWithConstraints(Modifier.fillMaxSize())`).

### 1.2 Window size

`desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt:769`:
`rememberWindowState(width = 1100.dp, height = 750.dp)`. No minimum window size is set anywhere
(`minimumSize` does not occur in `desktopApp (removed)` or `androidUi`), so the window can be shrunk freely and
every breakpoint below is reachable on desktop.

### 1.3 Which settings layout at which window width

Settings panel width = window width - navigation width - 32 (the 16 dp side paddings).

| Window width (dp) | App navigation                  | Panel width         | Settings layout                                      |
|-------------------|---------------------------------|---------------------|------------------------------------------------------|
| < 680             | bottom bar (compact app layout) | about width - 8..32 | narrow: list of pages, then one page with a back row |
| 680 .. 835        | rail 84                         | 564 .. 719          | narrow                                               |
| 836 .. 1099       | rail 84                         | 720 .. 983          | wide (two panes)                                     |
| >= 1100           | sidebar 232                     | >= 836              | wide (two panes)                                     |

The switch is `val wide = maxWidth >= 720.dp` inside the panel (`FdmSettingsPanel.kt:251`), measured
on the panel, not on the window.

At the default 1100 x 750 window: panel 836 wide; nav column 250; divider 1; page area 585; page
content width = 585 - 28 - 14 = 543 (the 720 cap is reached at panel width >= 1013, i.e. window >=
1277).

## 2. Theme tokens and Material 3 defaults the look depends on

### 2.1 Colour scheme (`UI/theme/Color.kt`, `UI/theme/Theme.kt`)

| Token                       | Dark    | Light   |
|-----------------------------|---------|---------|
| primary                     | #00E5FF | #0284C7 |
| onPrimary                   | #031625 | #FFFFFF |
| primaryContainer            | #0C3D60 | #E0F2FE |
| onPrimaryContainer          | #C7F1FF | #0369A1 |
| secondary                   | #818CF8 | #4F46E5 |
| secondaryContainer          | #2C2F63 | #EEF2FF |
| onSecondaryContainer        | #E0E7FF | #312E81 |
| background                  | #080D1A | #F8FAFC |
| surface                     | #0F1728 | #FFFFFF |
| surfaceVariant              | #172238 | #F1F5F9 |
| onSurface                   | #F1F5F9 | #0F172A |
| onSurfaceVariant            | #94A3B8 | #475569 |
| outline                     | #334155 | #94A3B8 |
| outlineVariant              | #1E2B42 | #CBD5E1 |
| error                       | #F87171 | #DC2626 |
| `StatusCompleted` (literal) | #10B981 | #10B981 |
| `StatusPaused` (literal)    | #F59E0B | #F59E0B |

Tokens the theme does not override keep the Material 3 baseline values (read from
`material3-desktop-1.12.0-alpha03-sources.jar`, `tokens/ColorDarkTokens.kt`, `ColorLightTokens.kt`,
`PaletteTokens.kt`):

| Token                   | Dark    | Light   | Used by                |
|-------------------------|---------|---------|------------------------|
| surfaceContainer        | #211F26 | #F3EDF7 | DropdownMenu container |
| surfaceContainerHigh    | #2B2930 | #ECE6F0 | AlertDialog container  |
| surfaceContainerHighest | #36343B | #E6E0E9 | Switch unchecked track |

The prototype `desktopNative/ui/theme.slint` lacks `secondary-container`, `on-secondary-container`,
`on-error`, the three `surface-container*` tokens, and its `completed` (#34D399) differs from
`StatusCompleted` (#10B981); `status-paused` (#F59E0B) matches `StatusPaused`.

Theme mode: the UI resolves `state.themeMode` ("system" / "light" / "dark") in `App.kt:151-156`;
"system" follows the OS.

### 2.2 Typography (default Material 3 `Typography`, default font family; no custom fonts)

Values from `tokens/TypeScaleTokens.kt` of the same jar:

| Role          | Size | Line height | Weight | Letter spacing |
|---------------|------|-------------|--------|----------------|
| headlineSmall | 24   | 32          | 400    | 0              |
| titleMedium   | 16   | 24          | 500    | 0.2            |
| bodyLarge     | 16   | 24          | 400    | 0.5            |
| bodyMedium    | 14   | 20          | 400    | 0.2            |
| bodySmall     | 12   | 16          | 400    | 0.4            |
| labelLarge    | 14   | 20          | 500    | 0.1            |
| labelMedium   | 12   | 16          | 500    | 0.5            |
| labelSmall    | 11   | 16          | 500    | 0.5            |

Rule that changes heights: a `Text` with no `style` uses `LocalTextStyle`, which is `bodyLarge`
under `MaterialTheme`. So `Text(x, fontSize = 12.sp)` is 12 sp with a **24 dp line box** and 0.5
letter spacing. Inside `Button`, `OutlinedButton`, `TextButton`, `FilterChip` and `DropdownMenuItem`
the local style is `labelLarge` (14/20, 500). This document gives the resulting heights where it
matters.

Weights used: Normal 400, Medium 500, SemiBold 600, Bold 700.

Glyphs come from font fallback on Windows (Segoe UI Symbol / Segoe UI Emoji): `◐ ↓ ⧉ ⇄ ◎ ▶ ⇅ ↻`
(nav), `← › ▾ ✕ ↗ ✓ … ·`, emoji `📁 ⚡ ⚠️`.

### 2.3 Component defaults (material3 1.12.0-alpha03,
`ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled` = false, not changed by the app)

- Minimum interactive size: `LocalMinimumInteractiveComponentSize` = 48 dp. `Button`,
  `OutlinedButton`, `TextButton`, `FilterChip`, `Switch` are laid out at least 48 dp tall (the
  visual is centred inside). This is why a `SettingRow` with a switch or button is 72 dp tall.
- `Button`: min height 40, min width 58, content padding 24 horizontal / 8 vertical, default shape
  fully rounded (pill); container `primary`, content `onPrimary`, text `labelLarge`. Disabled:
  container `onSurface` 10 %, content `onSurfaceVariant` 38 %. Hover adds a 1 dp shadow and the 8 %
  state layer.
- `OutlinedButton`: same metrics; transparent container; border 1 dp `outlineVariant` (disabled:
  `outlineVariant` at 10 % alpha); content colour **`onSurfaceVariant`** (not primary in this
  Material version); disabled content `onSurfaceVariant` 38 %.
- `TextButton`: min height 40, content padding 12 horizontal / 8 vertical, pill shape, content
  `primary`, disabled `onSurfaceVariant` 38 %.
- Most settings buttons pass `shape = RoundedCornerShape(10.dp)`; the exceptions (pill shape) are
  named where they occur.
- `Switch`: track 52 x 32, fully rounded. Checked: track `primary`, thumb `onPrimary` 24 dp, 4 dp
  from the end. Unchecked: track `surfaceContainerHighest`, 2 dp border `outline`, thumb `outline`
  16 dp, 8 dp from the start. Pressed thumb 28 dp. Hover/pressed thumb: checked `primaryContainer`,
  unchecked `onSurfaceVariant`; 40 dp circular state layer. Disabled checked: track `onSurface`
  12 %, thumb `surface`. Disabled unchecked: track `surfaceContainerHighest` 12 %, border
  `onSurface` 12 %, thumb `onSurface` 38 %. The thumb animates (position and size) with the fast
  spatial spring (about 200 ms).
- `FilterChip`: height 32, shape 8 dp, label `labelLarge`, 16 dp from each edge to the label (8
  content padding + 8 element spacing), no leading icon. Unselected: transparent, border 1 dp
  `outlineVariant`, label `onSurfaceVariant`. Selected: container `secondaryContainer`, no border,
  label `onSecondaryContainer`.
- `HorizontalDivider`: 1 dp, default colour `outlineVariant` (overridden in `SettingDivider`).
- `Surface`: clips its content to its shape, content colour `onSurface`, no elevation unless stated.
- `DropdownMenu`: container `surfaceContainer`, shape 4 dp, shadow elevation level 2 (3 dp), 8 dp
  vertical padding, items min height 48, 12 dp horizontal padding, min width 112, max width 280,
  text `labelLarge`; enter/exit = scale from 0.8 + fade (120 ms in, 75 ms out); closes on outside
  click and Esc; scrolls when taller than the window minus 48 dp margins.
- `AlertDialog`: container `surfaceContainerHigh`, shape 28 dp, width 280..560, 24 dp padding, title
  `headlineSmall` `onSurface` with 16 dp below, text `bodyMedium` `onSurfaceVariant` with 24 dp
  below, buttons right aligned with 8 dp spacing (dismiss button first, confirm button last).
- `CircularProgressIndicator` (indeterminate): colour `primary`, transparent track, round caps; size
  and stroke given at each use.
- `LinearProgressIndicator`: 4 dp high, colour `primary`, track `secondaryContainer`, round caps;
  the determinate one has a 4 dp gap between indicator and track and a 4 dp stop dot at the end.
- State layers (default ripple indication of `clickable`): hover 8 %, focus 10 %, pressed 10 % of
  the content colour, plus the ripple. `clickable` elements are focusable (Tab) and activate with
  Enter / Space. The mouse cursor stays the arrow on clickable elements; text fields show the
  I-beam.

## 3. Building blocks (`UI/ui/SettingsKit.kt`)

### 3.1 `SettingsGroup` (line 43-79)

`fun SettingsGroup(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit)`

Used by every page. Layout: `Column(fillMaxWidth)`:

1. If `title != null`: `Text(title.uppercase())`, `labelSmall` with weight Bold and letter spacing
   0.8, colour `onSurfaceVariant`, padding start 4, bottom 8. Height 16 + 8.
2.
`Surface(shape = RoundedCornerShape(14.dp), color = surface, border = 1.dp outlineVariant alpha 0.35, fillMaxWidth)`
containing `Column(fillMaxWidth)` with the rows. Rows are clipped to the rounded shape (hover
highlights of the first / last row follow the corners).
3. If `footer != null`: `Text(footer)`, `bodySmall`, `onSurfaceVariant`, padding start 4, end 4, top
   8, wraps over several lines.

No state, no events. Pages stack groups with `Arrangement.spacedBy(24.dp)`.

### 3.2 `SettingDivider` (line 83-88)

`HorizontalDivider`, 1 dp, colour `outlineVariant` alpha 0.3, inset 16 dp on both sides.

### 3.3 `SettingRow` (line 92-129)

`fun SettingRow(title: String, subtitle: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null)`

-
`Row(fillMaxWidth, heightIn(min = 56.dp), [clickable when onClick != null && enabled], padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = CenterVertically)`.
The clickable area and its hover layer cover the full row including the padding.
- Left `Column(weight 1)`: title `bodyMedium` SemiBold, `onSurface` (alpha 0.45 when disabled),
  wraps; if `subtitle` is not blank: 2 dp spacer and subtitle `bodySmall`, `onSurfaceVariant` (alpha
  0.45 when disabled), wraps (multi-line subtitles and `\n` are shown in full).
- If `trailing != null`: 16 dp spacer, then the trailing control at its own width.
- Heights: title only = 56; title + 1-line subtitle = 62; with a 48 dp trailing control (switch,
  button) = 72; with a `SegmentedPicker` (44) = 68.

### 3.4 `SettingSwitch` (line 132-142)

`fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit)`

`SettingRow(title, subtitle, enabled, onClick = { onChange(!checked) }) { Switch(checked, onCheckedChange = onChange, enabled) }`.
Clicking anywhere on the row or on the switch toggles. Disabled: row not clickable, texts at 0.45
alpha, switch in disabled colours.

### 3.5 `SegmentedPicker<T>` (line 146-180)

`fun <T> SegmentedPicker(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit)`

- Outer `Row`: clip 10 dp, background `surfaceVariant` alpha 0.45, padding 3, items spaced 2.
  Width = content.
- Item `Box`: clip 8 dp, background `primary` when selected else transparent, clickable,
  `heightIn(min = 32.dp)`, padding 12 horizontal / 7 vertical, centred text: 12 sp (24 dp line box),
  Bold + `onPrimary` when selected, Medium + `onSurface` otherwise, `maxLines = 1`.
- Item height 38, whole picker 44. No animation between segments. `onSelect` fires on every click,
  also on the already selected option (callers filter when they want to).
- Where the picker can be wider than the card it sits in a
  `Box(fillMaxWidth().horizontalScroll(...))` (speed unit, queue size, when done, proxy kind): on a
  narrow card it scrolls sideways (mouse wheel with Shift, touch drag; no scrollbar drawn).

### 3.6 `StatusLabel` (line 184-197)

`fun StatusLabel(text: String, color: Color)`: `Row(CenterVertically)`: 8 dp filled circle in
`color`, 6 dp spacer, text 12 sp SemiBold in `color`, `maxLines = 1`, ellipsis. Height 24.

## 4. Panel shell (`UI/ui/FdmSettingsPanel.kt`)

### 4.1 `SettingsSection` (line 51-84)

Order and glyphs: `GENERAL("◐")`, `DOWNLOADS("↓")`, `BROWSER("⧉")`, `DEVICES("⇄")`, `NETWORK("◎")`,
`MEDIA("▶")`, `TORRENT("⇅")`, `UPDATES("↻")`.

| Section   | `title` member (English)             | `blurb` member (English)                                                    |
|-----------|--------------------------------------|-----------------------------------------------------------------------------|
| GENERAL   | `sectionGeneral` (General)           | `sectionGeneralBlurb` (Look of the app and how it starts)                   |
| DOWNLOADS | `sectionDownloads` (Downloads)       | `sectionDownloadsBlurb` (Where files go and how many run at once)           |
| BROWSER   | `sectionBrowser` (Browser extension) | `sectionBrowserBlurb` (Send links and videos from your browser to FetchJet) |
| DEVICES   | `sectionDevices` (Devices)           | `sectionDevicesBlurb` (Send downloads between your devices)                 |
| NETWORK   | `sectionNetwork` (Network)           | `sectionNetworkBlurb` (Proxy and site logins)                               |
| MEDIA     | `sectionMedia` (Media tools)         | `sectionMediaBlurb` (yt-dlp and FFmpeg for video sites)                     |
| TORRENT   | `sectionTorrent` (BitTorrent)        | `sectionTorrentBlurb` (Peers, uploading and the torrent engine)             |
| UPDATES   | `sectionUpdates` (Updates)           | `sectionUpdatesBlurb` (Your version and new releases)                       |

### 4.2 `FdmSettingsPanel` (line 93-359)

Signature (line 93-144), all parameters come from `SettingsContentView`:

```
fun FdmSettingsPanel(
    destinationPath: String, disableTorrentUpload: Boolean, isDarkMode: Boolean,
    globalDownloadSpeedLimitBytes: Long, globalUploadSpeedLimitBytes: Long,
    speedDisplayUnit: String = "MB/s",
    speedPresetLowBytes: Long = 256L * 1024, speedPresetLowUnit: String = "KB/s",
    speedPresetMediumBytes: Long = 2L * 1024 * 1024, speedPresetMediumUnit: String = "MB/s",
    speedPresetFastBytes: Long = 3L * 1024 * 1024, speedPresetFastUnit: String = "MB/s",
    speedPresetHighBytes: Long = 20L * 1024 * 1024, speedPresetHighUnit: String = "MB/s",
    ytDlpStatus: String, isYtDlpInstalled: Boolean, isFfmpegInstalled: Boolean,
    jsRuntimeName: String = "", isJsRuntimeReady: Boolean = true, toolsDirectoryPath: String,
    torrentEngineStatus: String = "", isTorrentEngineReady: Boolean = true,
    isDownloadingYtDlp: Boolean, toolDownloadName: String? = null, toolDownloadFraction: Float? = null,
    monitorClipboard: Boolean = false, clipboardVideoPages: Boolean = false,
    sortIntoFolders: Boolean = false, defaultConnections: Int = 0,
    pathResolver: PathResolver,
    onDestinationPathChanged: (String) -> Unit,
    onMonitorClipboardChanged: (Boolean) -> Unit = {}, onClipboardVideoPagesChanged: (Boolean) -> Unit = {},
    onSortIntoFoldersChanged: (Boolean) -> Unit = {}, onDefaultConnectionsChanged: (Int) -> Unit = {},
    pairedDevices: List<PairedDevice> = emptyList(), onUnpairDevice: (String) -> Unit = {},
    themeMode: String = "system", onThemeModeChanged: (String) -> Unit = {},
    blockedDevices: List<PairedDevice> = emptyList(), onUnblockDevice: (String) -> Unit = {},
    onDisableTorrentUploadToggled: (Boolean) -> Unit, onThemeToggled: () -> Unit,
    onLanguageChanged: (String) -> Unit = {},
    onGlobalDownloadSpeedLimitChanged: (Long) -> Unit, onGlobalUploadSpeedLimitChanged: (Long) -> Unit,
    onSpeedDisplayUnitChanged: (String) -> Unit = {},
    onSpeedPresetChanged: (SpeedPreset, Long, String) -> Unit = { _, _, _ -> },
    onDownloadYtDlpClicked: () -> Unit, onApplySettings: () -> Unit
)
```

Parameters that the panel receives but never uses: `isDarkMode`, `globalDownloadSpeedLimitBytes`,
`speedPresetHighBytes`, `speedPresetHighUnit`, `onThemeToggled`,
`onGlobalDownloadSpeedLimitChanged`, `onApplySettings`. There is no Save / Apply button: every
change is applied and saved at once.

Values read directly from sharedLogic (not from `DashboardUiState`):

- `getPlatform().isJvm`, `getPlatform().name` (iOS check). The native Windows build must behave as
  `isJvm == true` here (desktop).
- `UpdateManager.state` (`StateFlow<UpdateState>`),
  `QueueManagerRepository.getInstance().maxConcurrentDownloads`,
  `BehaviorSettings.visibleToDevices`, `NetworkSettings.proxy`, `NetworkSettings.logins`,
  `AppFeatures.mediaExtractor`, `AppFeatures.torrent`.

Visible sections (line 153-160): `MEDIA` when `isJvm || (isIos && AppFeatures.mediaExtractor)`;
`TORRENT` when `AppFeatures.torrent`; `UPDATES` when `updateState.supported`; the others always. On
Windows desktop all 8 are shown (`currentAppEdition()` = FULL, `supported` = true).

`unitKey` (line 161-165): `"BYTES_PER_SEC"` or `"BOTH"` -> `"MB/s"`; `"BITS_PER_SEC"` -> `"Mbps"`;
anything else -> `speedDisplayUnit` unchanged (legacy stored values).

`toolsReady = isYtDlpInstalled && isFfmpegInstalled`.

UI-local state: `var openSection by remember { mutableStateOf<SettingsSection?>(null) }` (line 198).
Not saved: leaving the Settings tab and coming back shows the first section again.

#### Nav summary line (line 169-196) - `summary(s): Pair<String, Color?>`

| Section   | Text                                                                                                                                                                               | Colour                                               |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| GENERAL   | `themeModeLabel(themeMode) + " · " + unitKey` e.g. "System · MB/s"                                                                                                                 | default                                              |
| DOWNLOADS | `folderName(destinationPath) + " · " + (maxAtOnce <= 0 ? Strings.summaryNoQueueLimit : Strings.summaryAtATime(maxAtOnce))` e.g. "Downloads · 3 at a time" / "Downloads · no limit" | default                                              |
| NETWORK   | `networkSummary(netProxy, netLogins.size)`                                                                                                                                         | `StatusCompleted` when `netProxy.isOn`, else default |
| MEDIA     | `Strings.settingsReady` ("Ready") / `Strings.summarySetupNeeded` ("Setup needed")                                                                                                  | `StatusCompleted` / `StatusPaused`                   |
| TORRENT   | `Strings.summaryUploadingOff` when `disableTorrentUpload`, else `Strings.summaryUploadingOn`                                                                                       | default                                              |
| BROWSER   | hard-coded: `"Edge · Firefox · Chrome"` on desktop (`"Safari"` iOS, `"Firefox for Android"` Android)                                                                               | default                                              |
| DEVICES   | `(visibleToDevices ? Strings.summaryVisible : Strings.summaryHidden) + (pairedDevices.isNotEmpty() ? " · ${pairedDevices.size}" : "")`                                             | `StatusCompleted` when visible, else default         |
| UPDATES   | `Strings.summaryVersionAvailable(available.version)` when an update is available, else `Strings.summaryVersion(currentVersion)`                                                    | `StatusCompleted` when available, else default       |

Default colour = `onSurfaceVariant`.

#### Wide layout (line 252-298), panel width >= 720

`current = openSection?.takeIf { it in sections } ?: sections.first()`.

```
Row(fillMaxSize)
  Column(width 250, fillMaxHeight, verticalScroll, padding(end = 12, top = 4), spacedBy(4))
    SectionNavItem(s, hint, hintColor, selected = s == current) { openSection = s }   for each section
    Spacer(height 12)
    Text(Strings.changesSavedAutomatically)  labelSmall, onSurfaceVariant, padding horizontal 12
  Box(width 1, fillMaxHeight, background outlineVariant alpha 0.35)
  Box(weight 1, fillMaxHeight)
    Column(fillMaxSize, verticalScroll(contentScroll), padding(start = 28, end = 14, top = 4, bottom = 28))
      Column(widthIn(max = 720))        start aligned, not centred
        PageHeader(current)
        page(current)
    AppScrollbar(contentScroll, align CenterEnd)
```

- The nav column scrolls on its own (mouse wheel) and has no scrollbar. Usable nav item width 238.
- The page scroll state is one `rememberScrollState()` shared by all pages: switching page keeps the
  current scroll offset (clamped to the new page's height). It is not reset to the top.
- `AppScrollbar` (`UI/ui/AppScrollbar.kt:80-138`): only when the page overflows; a 12 dp wide strip
  at the end edge over the page's 14 dp end padding, 2 dp top/bottom inset; thumb 6 dp wide, corner
  3, colour `onSurface` alpha 0.28, 0.55 while hovered or scrolling (180 ms tween); thumb length
  proportional, minimum 28 px; drag the thumb to scroll; click on the track jumps so the thumb
  centre lands on the click.

#### Narrow layout (line 299-357), panel width < 720

- `current = openSection?.takeIf { it in sections }` (null = list).
- List: `Column(fillMaxSize, verticalScroll, padding 4, spacedBy(6))` of
  `SectionNavItem(..., selected = false, chevron = true)`, then the "Changes are saved
  automatically." text (`labelSmall`, padding horizontal 12, vertical 8).
- Page: `Box(fillMaxSize)` with
  `Column(fillMaxSize, verticalScroll(sectionScroll), padding(start = 4, end = 14), padding(bottom = 24))`:
  a back row `Row(clip 10, clickable { openSection = null }, padding horizontal 6 / vertical 8)`
  with `"←"` 18 sp `primary`, 8 dp spacer, `Strings.settingsAllSettings` ("All settings")
  `labelLarge` `primary`; then `PageHeader(current)` and the page; `AppScrollbar` at the end edge.
  No 720 cap here.
- In the narrow layout the opened page is a new scroll state each time.

### 4.3 `PageHeader` (line 362-371)

Title `headlineSmall` Bold (`onSurface`), 4 dp spacer, blurb `bodyMedium` `onSurfaceVariant`, 20 dp
spacer. Height 32 + 4 + 20 + 20 = 76 for a one-line blurb.

### 4.4 `SectionNavItem` (line 374-431)

`fun SectionNavItem(s: SettingsSection, hint: String, hintColor: Color?, selected: Boolean, chevron: Boolean = false, onClick: () -> Unit)`

-
`Row(fillMaxWidth, clip 12, background = selected ? primary alpha 0.12 : transparent, clickable, heightIn(min = 56), padding(horizontal = 10, vertical = 8), CenterVertically)`.
Actual height 60 (20 title + 24 hint line box + 16 padding).
- Icon tile
  `Box(size 34, clip 9, background = selected ? primary alpha 0.2 : surfaceVariant alpha 0.5)` with
  the glyph centred: 16 sp Bold, `primary` when selected else `onSurfaceVariant`.
- 12 dp spacer.
- `Column(weight 1)`: title `bodyMedium` SemiBold (`primary` when selected else `onSurface`); hint
  12 sp, colour `hintColor ?: onSurfaceVariant`, `maxLines = 1`, ellipsis.
- If `chevron`: `"›"` 22 sp `onSurfaceVariant` (narrow layout only).
- Hover: default state layer. No tooltip.

## 5. Pages

Every page is a `Column(verticalArrangement = Arrangement.spacedBy(24.dp))` of `SettingsGroup`s
placed under `PageHeader`.

### 5.1 General - `GeneralPage` (`SettingsPages.kt:49-143`)

```
internal fun GeneralPage(themeMode: String, unitKey: String, watchClipboard: Boolean, clipboardVideoPages: Boolean,
    onThemeModeChanged: (String) -> Unit, onLanguageChanged: (String) -> Unit, onSpeedDisplayUnitChanged: (String) -> Unit,
    onWatchClipboardChanged: (Boolean) -> Unit, onClipboardVideoPagesChanged: (Boolean) -> Unit)
```

Groups in order:

**1. Language - `LanguageSettingsCard` (line 760-801).** `SettingsGroup(title = Strings.language)`
with one `SettingRow(Strings.language, Strings.languageHint, onClick = { open = true })`. Trailing:
`Box { TextButton { Text("$currentName  ▾", SemiBold) }; DropdownMenu(expanded = open) }` (pill
TextButton, `primary` text, two spaces before `▾`).

- `current = AppLanguage.choice` (`LOGIC/core/i18n/start/LocalizationStart.kt`; `"system"` or a
  locale code; a plain property, not observable).
- `currentName`: when `current == "system"`: `"${Strings.systemLanguage} (${Strings.languageName})"`
  (e.g. "System (English)", `languageName` being the name of the language actually in use);
  otherwise the `languageName` of the provider whose `localeCode == current`, or the code itself.
- Menu items in order: `"system"` shown as `Strings.systemLanguage`, then `appLanguages` in this
  order with their own `languageName` (always in that language, not translated): en English, ar
  العربية, fr Français, de Deutsch, es Español, pt Português, it Italiano, ru Русский, tr Türkçe, id
  Bahasa Indonesia, zh 中文 (简体), ja 日本語, ko 한국어, hi हिन्दी, fa فارسی. 16 items x 48 dp: the
  menu scrolls at the default window height.
- The current item is Bold and `primary`; the others Normal and `onSurface`.
- Click an item: close the menu; if `code != current` send
  `DashboardUiEvent.OnLanguageChanged(code)` (handler: `AppLanguage.change(choice)` +
  `persistCurrentSettings()`, `DashboardSettingsEvents.kt:114-117`).
- UI-local state: `open` (menu shown).

**2. Clipboard.** `SettingsGroup(title = Strings.clipboardGroup, footer = Strings.clipboardFooter)`:

-
`SettingSwitch(Strings.watchClipboard, subtitle = watchClipboard ? Strings.watchClipboardOn : Strings.settingOff, checked = watchClipboard)` ->
`OnMonitorClipboardChanged(Boolean)`.
- `SettingDivider`.
-
`SettingSwitch(Strings.alsoVideoPages, Strings.alsoVideoPagesHint, checked = clipboardVideoPages, enabled = watchClipboard)` ->
`OnClipboardVideoPagesChanged(Boolean)`.

**3. Diagnostics** (line 81-108): only when `DiagnosticsLog.canExport`. The JVM actual returns
`false` (`sharedLogic/src/jvmMain/.../core/log/DiagnosticsLog.jvm.kt:4`), so this group is **not
shown on desktop** (Android only). For reference: group `Strings.diagnosticsGroup`, footer
`Strings.logSavedTo(path)` after a save else `Strings.diagnosticsFooter`; a row (padding 16/12) with
`Strings.saveLogToDownloads` (`bodyMedium` Medium) and an `OutlinedButton(shape 10)` `Strings.save`
that calls `DiagnosticsLog.export()` and stores the result (or `Strings.logCouldNotSave`) in local
state `savedTo`.

**4. Appearance.** `SettingsGroup(title = Strings.appearanceGroup)`:

- `SettingRow(Strings.themeTitle, Strings.themeHint)` with trailing
  `SegmentedPicker(options = ["system", "light", "dark"], selected = themeMode, label = themeModeLabel)`;
  `onSelect = { if (it != themeMode) onThemeModeChanged(it) }` -> `OnThemeModeChanged(mode)`.
- `SettingDivider`.
- `SettingRow(Strings.speedShownIn, Strings.speedShownInHint)` (no trailing), directly followed by
  `Box(fillMaxWidth, horizontalScroll, padding(start = 16, end = 16, bottom = 14))` containing
  `SegmentedPicker(options = ["MB/s", "KB/s", "Mbps", "Kbps"], selected = unitKey, label = identity, onSelect = onSpeedDisplayUnitChanged)` ->
  `OnSpeedDisplayUnitChanged(unit)` (also sent when the selected unit is clicked again). The unit
  labels are hard-coded.

`themeModeLabel(mode)` (line 145-149): `"light"` -> `Strings.settingsThemeLight`, `"dark"` ->
`Strings.settingsThemeDark`, else `Strings.settingsThemeSystem`.

**5. Startup** - `if (StartupLauncher.isSupported) StartupSettingsCard()` (line 140). See 6.4.

**6. When downloads finish** - `WhenDoneSettingsCard()` (`WhenDoneUi.kt:43-64`): returns nothing
unless `SystemPower.isSupported` (true on JVM desktop).
`SettingsGroup(title = Strings.whenDoneGroup, footer = Strings.whenDoneHint)` with
`Box(fillMaxWidth, horizontalScroll, padding 16)` containing
`SegmentedPicker(options = WhenDoneAction.entries, selected = WhenDone.action, label = whenDoneLabel)`;
`onSelect = WhenDone.choose(it)`. Order and labels: NOTHING `Strings.whenDoneNothing`, EXIT
`Strings.whenDoneExit`, SLEEP `Strings.whenDoneSleep`, SHUT_DOWN `Strings.whenDoneShutDown`. The
choice is per session (not saved). The banner and countdown dialog of this feature are outside
settings.

### 5.2 Downloads - `DownloadsPage` (`SettingsPages.kt:248-426`)

```
internal fun DownloadsPage(destinationPath: String, sortIntoFolders: Boolean, defaultConnections: Int, pathResolver: PathResolver,
    onDestinationPathChanged: (String) -> Unit, onSortIntoFoldersChanged: (Boolean) -> Unit, onDefaultConnectionsChanged: (Int) -> Unit,
    lowBytes: Long = 256L * 1024, lowUnit: String = "KB/s", mediumBytes: Long = 2L * 1024 * 1024, mediumUnit: String = "MB/s",
    fastBytes: Long = 3L * 1024 * 1024, fastUnit: String = "MB/s", highBytes: Long = 20L * 1024 * 1024, highUnit: String = "MB/s",
    onPresetChanged: (SpeedPreset, Long, String) -> Unit = { _, _, _ -> })
```

UI-local state: `defaultDownloads = remember { pathResolver.getDownloadsDirectory() }`;
`showDirectoryPicker` (Boolean). When true:
`DirectoryPickerDialog(initialPath = destinationPath, pathResolver, onDirectorySelected = { onDestinationPathChanged(it); close }, onDismissRequest = { close })`.

**1. Save location.**
`SettingsGroup(title = Strings.saveLocationGroup, footer = Strings.saveLocationFooter)`,
`Column(padding 16)`:

-
`FetchJetTextField(value = destinationPath, onValueChange = onDestinationPathChanged, fillMaxWidth, placeholder = Strings.settingsFolderPath, leading = "📁" 15 sp)`,
height 48. Every keystroke sends `OnDestinationPathChanged(text)` (handler: state update +
`persistCurrentSettings()`, `DashboardAddDialogEvents.kt:120-126`). No validation.
- 10 dp spacer.
- `Row(spacedBy(8))`:
    - `Button(shape 10)` `Strings.settingsChooseFolder` ("Choose folder…", SemiBold) -> opens the
      directory picker.
    - `OutlinedButton(shape 10)` `Strings.open` ->
      `pathResolver.openDirectoryInExplorer(destinationPath)`; only when
      `pathResolver.isOpenFolderSupported()` (true on desktop).
    - `OutlinedButton(shape 10)` `Strings.settingsUseDownloads` ("Use Downloads") ->
      `onDestinationPathChanged(defaultDownloads)`; only when `destinationPath != defaultDownloads`.
    - The row does not wrap: on a narrow card the last button is squeezed.

**2. Connections.**
`SettingsGroup(title = Strings.settingsConnectionsGroup, footer = Strings.connectionsFooter)`,
`Column(padding horizontal 16, vertical 12)`:

- `Text(Strings.settingsConnectionsPerDownload)` `bodyMedium` Medium `onSurface`; 8 dp spacer.
- `Row(fillMaxWidth, horizontalScroll, spacedBy(6))` of `FilterChip`s for `[0, 1, 4, 8, 16, 32]`;
  label `Strings.settingsConnectionsAuto` for 0, else the number;
  `selected = defaultConnections == n`; click -> `OnDefaultConnectionsChanged(n)` (handler:
  `BehaviorSettings.setConnections(n)`).

**3. Speed presets.**
`SettingsGroup(title = Strings.speedPresetsTitle, footer = Strings.speedPresetsFooter)`:

- `Column(padding 16)`: `Row(fillMaxWidth, SpaceBetween, CenterVertically)`:
  `Strings.speedPresetHigh` `titleMedium` SemiBold; `Strings.speedLimitUnlimited` ("Unlimited (Max)
  ") `labelMedium` Bold `onSurfaceVariant`. Static: High is always unlimited.
- `SettingDivider`,
  `Column(padding 16) { SpeedPresetRow(Strings.speedPresetFast, fastBytes, fastUnit) }` ->
  `OnSpeedPresetChanged(SpeedPreset.FAST, bytes, unit)`.
- `SettingDivider`, same for `Strings.speedPresetMedium` -> `SpeedPreset.MEDIUM`.
- `SettingDivider`, same for `Strings.speedPresetLow` -> `SpeedPreset.LOW`.
- Handler (`DashboardSettingsEvents.kt:65-102`): stores bytes + unit; if the preset's old value was
  the active global download limit and the bytes changed, the global limit follows the new value;
  `persistCurrentSettings()`. `SpeedPreset.HIGH` is ignored.
- `SpeedPresetRow`: see 6.1.

**4. Speed schedule** - `SpeedScheduleCard()` (line 433-530), private. Reads
`SpeedSchedule.enabled`, `SpeedSchedule.windows`, `SpeedSchedule.activeCap`
(`LOGIC/core/power/SpeedSchedule.kt`) and writes through
`SpeedSchedule.setEnabled / addWindow / updateWindow / removeWindow` (saved to `schedule.json`).

`SettingsGroup(footer = Strings.speedScheduleFooter)` (no title):

-
`SettingSwitch(Strings.speedScheduleTitle, subtitle = (enabled && activeCap != null) ? Strings.speedScheduleActive : null, checked = enabled)` ->
`SpeedSchedule.setEnabled(Boolean)`.
- Only when `enabled`:
    - If `windows.isEmpty()`: `SettingDivider`,
      `Column(padding 16) { Text(Strings.speedScheduleEmpty) }` `bodyMedium` `onSurfaceVariant`.
    - For each window (index, window): `SettingDivider`,
      `key(index) { Column(padding 16) { ... } }`:
        - `Row(fillMaxWidth, spacedBy(10), verticalAlignment = Bottom)`:
          `TimeField(Strings.speedScheduleFrom, window.startMinutes, weight 1)`,
          `TimeField(Strings.speedScheduleTo, window.endMinutes, weight 1)`,
          `Box(height 42, centred) { FieldIconButton("✕") }` -> `SpeedSchedule.removeWindow(index)`.
        - 10 dp spacer.
        - `SpeedPresetRow(name = "", bytes = window.capBytes, unit = window.capUnit)` ->
          `SpeedSchedule.updateWindow(index, window.copy(capBytes = b, capUnit = u))`. With the
          empty name the header line shows only the short label (e.g. "1 MB/s") at the end.
    - `SettingDivider`,
      `Column(padding 16) { Button(shape 10) { Text("+  " + Strings.speedScheduleAdd, SemiBold) } }` ->
      `SpeedSchedule.addWindow(SpeedWindow(startMinutes = 480, endMinutes = 1080, capBytes = 1048576, capUnit = "MB/s"))`.

`TimeField(label, minutes, onChanged, modifier)` (line 533-560), private:

- UI-local state: `text = remember { minutesToHm12(minutes) }`,
  `isPm = remember { normalized(minutes) >= 720 }`.
-
`FetchJetTextField(value = text, label = label, placeholder = "8:00", height = 42.dp, trailing = AmPmToggle)`.
On input: keep only digits and `:`, at most 5 characters, store as text, and when
`parseHm12(text, isPm)` is not null call `onChanged(minutes)` (->
`SpeedSchedule.updateWindow(index, window.copy(startMinutes | endMinutes = it))`). Invalid text is
kept in the field and nothing is sent.
- `AmPmToggle(isPm, onToggle)` (line 563-581):
  `Box(clip 8, background primary alpha 0.14, clickable, padding horizontal 10 / vertical 6)` with
  `"PM"` / `"AM"` (hard-coded) 12 sp SemiBold `primary`; 36 dp tall. Click flips `isPm` and, when
  the text parses, calls `onChanged`.
- Total height 71 (label 24 + 5 + field 42).

Helpers (line 583-607): see section 9.3.

**5. Organize.** `SettingsGroup(title = Strings.organizeGroup, footer = Strings.organizeFooter)`:
`SettingSwitch(Strings.sortIntoFolders, subtitle = sortIntoFolders ? Strings.sortIntoFoldersOn : Strings.sortIntoFoldersOff, checked = sortIntoFolders)` ->
`OnSortIntoFoldersChanged(Boolean)`.

**6. Queue** - `QueueSettingsCard()` (`QueueUi.kt:19-47`). Reads
`QueueManagerRepository.getInstance().maxConcurrentDownloads`.
`SettingsGroup(title = Strings.homeQueueTitle, footer = Strings.homeQueueFooter)`:
`SettingRow(Strings.homeDownloadsAtSameTime, subtitle = max <= 0 ? Strings.homeNoLimit : Strings.homeAtATime(max))`,
then `Box(fillMaxWidth, horizontalScroll, padding(start = 16, end = 16, bottom = 14))` with
`SegmentedPicker(options = [1, 2, 3, 4, 5, 8, 0], selected = max, label = 0 -> Strings.homeNoLimit, else the number)` ->
`repository.setMaxConcurrentDownloads(n)`.

### 5.3 Browser extension - `BrowserExtensionPage(isDesktop: Boolean)` (`SettingsPages.kt:676-719`)

**1. Install.**
`SettingsGroup(title = Strings.extInstallGroup, footer = Strings.extensionInstallFooter) { BrowserInstallRows(); ... }`.
The two extra switches (`keepReadyForBrowser`, `popUpOverBrowser`, line 683-715) are inside
`if (!isDesktop)`: **not on desktop** (and both capabilities are false on JVM).

`BrowserInstallRows()` (`BrowserInstallSection.kt:60-233`):

- Data: `browsers = remember { BrowserIntegration.browsers() }` (evaluated once per page visit);
  `seen = BridgePairing.seenBrowsers` (`Map<String, Long>`), `versions = BridgePairing.seenVersions`
  (`Map<String, String>`).
- Desktop browser list (`sharedLogic/src/jvmMain/.../core/browser/DesktopBrowsers.kt:57-119`),
  stable-sorted with installed ones first: chrome "Google Chrome", edge "Microsoft Edge", firefox
  "Firefox", brave "Brave", opera "Opera", vivaldi "Vivaldi". `hasLocalCopy` = true for the Chromium
  ones when the extension is bundled in the app.
- UI-local state: `missing: BrowserInfo?`, `steps: Pair<BrowserInfo, LoadUnpacked>?`,
  `failure: String?`, `fromFile: Pair<BrowserInfo, String>?`, `busy: String?` (browser id being
  downloaded), and per browser `storeVersion: String?`, `githubXpi: ExtensionStore.SignedXpi?`.
- Per browser `b` (rows separated by `SettingDivider`):
    - `key = seenId(b.id)`: `"brave"` -> null; ids starting with `"org.mozilla."` -> `"firefox"`;
      else the id.
    - `connected = key != null && key in seen`; `version = versions[key]`;
      `outdated = connected && (version == null || isOlderExtension(version))`
      (`LOGIC/core/update/AppVersion.kt:21`, compares dotted numbers with `EXTENSION_VERSION`);
      `firefox = b.id == "firefox" || b.id.startsWith("org.mozilla.")`.
    - Network side effect: when `b.installed && (outdated || (!connected && firefox))`, once per
      page visit: `storeVersion = ExtensionStore.versionFor(b.id)` and, for Firefox,
      `githubXpi = ExtensionStore.githubXpi()` (HTTP requests to the stores / GitHub).
    - `storeHasIt = storeVersion != null && !isOlderExtension(storeVersion)`;
      `githubHasIt = firefox && githubXpi != null && !isOlderExtension(githubXpi.version)`;
      `fromGitHub = (outdated || (!connected && firefox && storeVersion != null)) && !storeHasIt && githubHasIt`;
      `waitingForStore = outdated && !storeHasIt && !b.hasLocalCopy && !githubHasIt`.
    - Subtitle (first match):
        1. `!b.installed`: `"⚠️ " + Strings.browserNotInstalled`
        2. `outdated`:
           `"⚠️ " + (version == null ? Strings.extensionOldVersion(EXTENSION_VERSION) : Strings.extensionNeedsUpdate(version, EXTENSION_VERSION)) + "\n" +`
           one of: `Strings.storeHasUpdate(storeVersion)` when `storeHasIt`;
           `Strings.updateFromAppHint` when `b.hasLocalCopy`;
           `Strings.updateFromGitHubHint(githubXpi.version)` when `githubHasIt`; else
           `Strings.storeWaiting(EXTENSION_VERSION)` (two lines)
        3. `connected`: `"✓ " + Strings.extensionConnectedVersion(version)`
        4. else `Strings.extensionNotAddedYet`
    - `SettingRow(title = b.name, subtitle)` with a trailing button, `shape 10`, text SemiBold,
      `enabled = busy == null`:
        - Label (first match): `"…"` when `busy == b.id`; `Strings.updateFromGitHub` when
          `fromGitHub`; `Strings.openStore` when `outdated && storeHasIt`; `Strings.updateFromApp`
          when `outdated && b.hasLocalCopy`; `Strings.openStore` when `outdated`; else
          `Strings.addExtension`.
        - Style: filled `Button` when
          `b.installed && (!connected || (outdated && !waitingForStore))`, otherwise
          `OutlinedButton`.
        - Click (first match): `!b.installed` -> `missing = b`; `fromGitHub && githubXpi != null` ->
          `busy = b.id`, download `ExtensionStore.download(xpi)`, `busy = null`, null bytes ->
          `failure = Strings.extensionDownloadFailed`, else
          `handle(b, BrowserIntegration.installXpi(b.id, bytes, xpi.fileName))`;
          `outdated && storeHasIt` -> open
          `https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj` for
          edge, else `FIREFOX_ADDON_URL`
          (`https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/`); else
          `handle(b, BrowserIntegration.addExtension(b.id))`.
    - `handle(b, result)`: `NotInstalled` -> `missing = b`; `LoadUnpacked(folder, page)` -> copy
      `folder` to the clipboard and `steps = b to result`; `InstallFromFile(fileName)` ->
      `fromFile = b to fileName`; `Failed(reason)` -> `failure = reason`; `Opened` -> nothing.
- Dialogs (Material `AlertDialog`, titles Bold, dismissed by outside click / Esc):
    - Missing browser: title `Strings.browserMissingTitle(name)`, text
      `Strings.browserMissingMessage(name)`, confirm `Button` `Strings.downloadBrowser(name)` (opens
      `b.downloadUrl`, closes), dismiss `OutlinedButton` `Strings.close`.
    - Load unpacked (Chromium on desktop): title `Strings.loadUnpackedTitle(name)`; body
      `Column(spacedBy(10))`: `Strings.loadUnpackedSteps(name, page)`; the folder path, selectable,
      monospace `bodySmall`, full width, clip 8, background `surfaceVariant`, padding 10;
      `Strings.loadUnpackedUpdates` `bodySmall` `onSurfaceVariant`. Confirm `Button`
      `Strings.copyPath` (copies the folder again, dialog stays open). Dismiss area: `TextButton`
      `Strings.detailsOpenFolder` (`BrowserIntegration.openFolder(folder)`) and `TextButton`
      `Strings.close`.
    - Install from file (Firefox for Android result; not expected on desktop): title
      `Strings.installFromFileTitle(name)`, text `Strings.installFromFileSteps(name, file)`, confirm
      `Button` `Strings.openBrowser(name)` (`BrowserIntegration.openBrowser(id)`), dismiss
      `OutlinedButton` `Strings.close`.
    - Failure: title `Strings.addExtension`, text = reason, confirm `Button` `Strings.close`.
    - Dialog buttons use the default pill shape.

**2. Browser connections** - `BrowserConnectionsGroup()` (line 726-757), private. Reads
`BridgePairing.legacyAllowed` and `BridgePairing.blocked` (`StateFlow<Set<String>>`). Hidden when
both are empty.
`SettingsGroup(title = Strings.extConnectionsGroup, footer = Strings.extConnectionsFooter)`: first
the allowed origins sorted, then the blocked origins sorted, a `SettingDivider` between rows:

- Allowed: `SettingRow(origin, Strings.extAllowedLabel)` + `OutlinedButton(shape 10)`
  `Strings.extRevoke` -> `BridgePairing.revokeAllowed(origin)`.
- Blocked: `SettingRow(origin, Strings.extBlockedLabel)` + `OutlinedButton(shape 10)`
  `Strings.extUnblock` -> `BridgePairing.unblock(origin)`.

### 5.4 Devices - `DevicesPage` (`SettingsPages.kt:153-187`)

`internal fun DevicesPage(pairedDevices: List<PairedDevice>, onUnpair: (String) -> Unit, blockedDevices: List<PairedDevice> = emptyList(), onUnblock: (String) -> Unit = {})`.
`PairedDevice(id, name, platform = "", lastSeenEpochMs = 0)`
(`LOGIC/feature/device_sync/DeviceSyncModels.kt:14`).

1. `SettingsGroup(title = Strings.sysDeviceSendTitle)`:
   `SettingSwitch(Strings.visibleToDevices, Strings.visibleToDevicesHint, checked = BehaviorSettings.visibleToDevices)` ->
   `BehaviorSettings.setVisibleToDevices(Boolean)` (direct call, no `DashboardUiEvent`).
2.
`DeviceListGroup(title = Strings.pairedDevicesTitle, empty = Strings.noPairedDevices, devices = pairedDevices, action = Strings.unpairDevice, actionIsDanger = true)` ->
`OnUnpairDeviceClicked(deviceId)`.
3.
`DeviceListGroup(title = Strings.blockedDevicesTitle, empty = Strings.noBlockedDevices, devices = blockedDevices, action = Strings.unblockDevice, actionIsDanger = false)` ->
`OnUnblockDeviceClicked(deviceId)`.

`DeviceListGroup` (line 191-245), private: `SettingsGroup(title)`:

- Empty list: `Text(empty)` `bodySmall` `onSurfaceVariant`, padding 16.
- Devices sorted by `name.lowercase()`, `SettingDivider` between rows. Per device:
  `host = name.substringBefore(" · ")`;
  `plat = name.substringAfter(" · ", "").ifBlank { platform }`.
- `Row(fillMaxWidth, padding horizontal 16 / vertical 10, CenterVertically)`: `Column(weight 1)`:
  `host` `bodyMedium` Medium `maxLines = 1`; `plat` `bodySmall` `onSurfaceVariant` `maxLines = 1`,
  only when `plat` is not blank and differs from `host` ignoring case. Then
  `OutlinedButton(shape 10)` with the action text coloured `error` (danger) or `primary`. Row height
  68.
- No confirmation dialog: the click acts at once.

### 5.5 Network - `NetworkPage(isIos: Boolean)` (`NetworkSettingsUi.kt:50-55`)

**1. Proxy - `ProxyCard(isIos)` (line 58-160).** Reads `saved = NetworkSettings.proxy`
(`ProxySettings(kind: ProxyKind, host, port: Int, user, password)`,
`ProxyKind { NONE, HTTP, SOCKS5 }`, `isOn = kind != NONE && host.isNotBlank() && port in 1..65535`).

UI-local draft state, re-initialised whenever `saved` changes: `kind`, `host`, `port` (text; `""`
when the saved port is 0), `user`, `password`; plus `justSaved` (Boolean).

Derived:
`draft = ProxySettings(kind, host.trim(), port.trim().toIntOrNull() ?: 0, user.trim(), password)`;
`invalid = kind != NONE && !draft.isOn`; `changed = draft != saved`.

`SettingsGroup(title = Strings.netProxyGroup, footer = Strings.netProxyHint)` (`netProxyHintIos` on
iOS):

- `Box(fillMaxWidth, horizontalScroll, padding 16)`:
  `SegmentedPicker(ProxyKind.entries, selected = kind)`; labels: NONE `Strings.netProxyOff` ("Off"),
  `"HTTP"`, `"SOCKS5"` (hard-coded); select sets `kind`, clears `justSaved`.
- When `kind != NONE`: `Column(padding(start = 16, end = 16, bottom = 16), spacedBy(10))`:
    - `Row(spacedBy(10))`: field `host` (label `Strings.netProxyHost`, placeholder `"127.0.0.1"`,
      weight 1); field `port` (label `Strings.netProxyPort`, placeholder `"1080"` for SOCKS5 else
      `"8080"`, width 110, input filtered to digits, at most 5).
    - `Row(spacedBy(10))`: field `user` (label `Strings.netProxyUser`, weight 1); field `password`
      (label `Strings.netProxyPassword`, weight 1, masked with dots, `enabled = user.isNotBlank()`;
      a disabled field is drawn at 0.6 alpha).
    - When `invalid && (host.isNotBlank() || port.isNotBlank())`: `Text(Strings.netProxyBadPort)`
      `bodySmall` `error`.
    - Every edit clears `justSaved`.
-
`Row(fillMaxWidth, padding(start = 16, end = 16, bottom = 12), Arrangement.End, CenterVertically)`:
`StatusLabel(Strings.netSaved, StatusCompleted)` when `justSaved && !changed`; 12 dp gap;
`Button(enabled = changed && !invalid)` `Strings.netSave` (pill shape).
- Save:
  `NetworkSettings.setProxy(kind == NONE ? saved.copy(kind = NONE) : draft.copy(password = draft.user.isEmpty() ? "" : draft.password))`,
  `justSaved = true`.
- Each field: `FetchJetTextField` with a label, total height 77 (label 24 + 5 + field 48).

**2. Site logins - `SiteLoginsCard()` (line 163-227).** Reads `NetworkSettings.logins`
(`List<SiteLogin(site, user, password)>`). UI-local state: `site`, `user`, `password` (all `""`).
`canAdd = SiteLogin.cleanSite(site).contains('.') && user.isNotBlank()`.

`SettingsGroup(title = Strings.netLoginsGroup, footer = Strings.netLoginsHint)`:

- No logins: `SettingRow(Strings.netLoginsEmpty)`.
- Per login (in stored order): `SettingRow(title = login.site, subtitle = login.user)` +
  `TextButton` with `Strings.netRemove` in `error` colour ->
  `NetworkSettings.removeLogin(login.site)`; then a `SettingDivider` (also after the last one).
- `Column(padding 16, spacedBy(10))`: field `site` (label `Strings.netLoginSite`, placeholder
  `"example.com"`, full width); `Row(spacedBy(10))`: field `user` (label `Strings.netUser`, weight
  1), field `password` (label `Strings.netProxyPassword`, masked, weight 1);
  `Row(fillMaxWidth, Arrangement.End)`: `Button(enabled = canAdd)` `Strings.netLoginAdd` (pill
  shape) -> `NetworkSettings.saveLogin(SiteLogin(site, user, password))` and clear the three fields.

`networkSummary(p, n)` (line 36-40):
`proxy = p.isOn ? "${kindLabel} ${p.host}:${p.port}" : Strings.netProxyOff`; result
`n == 0 ? proxy : proxy + " · " + Strings.netLoginsCount(n)`.

### 5.6 Media tools - `MediaToolsPage` (`SettingsMediaToolsPage.kt:32-154`)

```
internal fun MediaToolsPage(ytDlpStatus: String, isYtDlpInstalled: Boolean, isFfmpegInstalled: Boolean, jsRuntimeName: String,
    isJsRuntimeReady: Boolean, toolsDirectoryPath: String, isDownloadingYtDlp: Boolean, toolDownloadName: String?,
    toolDownloadFraction: Float?, pathResolver: PathResolver, onDownloadYtDlpClicked: () -> Unit)
```

`ready = isYtDlpInstalled && isFfmpegInstalled`. On desktop `toolsDirectoryPath` is passed as is
(iOS passes `""`).

1. `SettingsGroup(title = Strings.mediaToolsGroup)`:
    - `SettingRow("yt-dlp", Strings.ytDlpHint)` +
      `StatusLabel(Strings.settingsReady, StatusCompleted)` or
      `StatusLabel(Strings.settingsMissing, error)`.
    - `SettingDivider`; `SettingRow("FFmpeg", Strings.ffmpegHint)` +
      `StatusLabel(Strings.settingsReady, StatusCompleted)` or
      `StatusLabel(Strings.ffmpegMissing, StatusPaused)`.
    - When `jsRuntimeName` is not blank: `SettingDivider`;
      `SettingRow(Strings.jsRuntimeTitle(jsRuntimeName), Strings.jsRuntimeHint)` +
      `StatusLabel(Strings.settingsReady, StatusCompleted)` or
      `StatusLabel(Strings.missingPress(ready ? Strings.mediaUpdateTools : Strings.mediaDownloadTheTools), StatusPaused)`.
    - When `toolsDirectoryPath` is not blank: `SettingDivider`;
      `SettingRow(Strings.mediaToolsFolder, subtitle = toolsDirectoryPath, onClick = pathResolver.openDirectoryInExplorer(toolsDirectoryPath))` +
      trailing `Text(Strings.open)` in `primary`, SemiBold, 16 sp (default style). The whole row is
      the click target.
    - `"yt-dlp"`, `"FFmpeg"` are hard-coded.
2. `Row(CenterVertically)` with one button, `shape 10`, `enabled = !isDownloadingYtDlp`:
   `OutlinedButton` when `ready`, filled `Button` otherwise -> `OnDownloadYtDlpClicked`. Content:
    - While downloading: `CircularProgressIndicator(size 16, strokeWidth 2)`, 8 dp spacer, text:
      `toolDownloadName == null` ->
      `ready ? Strings.mediaUpdatingTools : Strings.mediaDownloadingTools`;
      `toolDownloadFraction == null` -> `Strings.mediaDownloadingTool(name)`; else
      `Strings.mediaDownloadingToolPercent(name, (fraction * 100).toInt())`.
    - Otherwise: `ready ? Strings.mediaUpdateTools : Strings.mediaDownloadTheTools`, SemiBold.
3. When `isDownloadingYtDlp && toolDownloadName != null`: full-width `LinearProgressIndicator`,
   determinate with `toolDownloadFraction` or indeterminate when it is null.
4. When `ytDlpStatus` is not blank: `Text(localizeStep(ytDlpStatus))` `bodySmall` `onSurfaceVariant`
   (`LOGIC/core/i18n/start/StepText.kt:7`).

Handler of `OnDownloadYtDlpClicked` (`DashboardSettingsEvents.kt:163-237`): sets
`isDownloadingYtDlp`, downloads / updates the tools, also installs the torrent engine if missing,
refreshes all tool fields, emits
`DashboardUiEffect.ShowToast(Strings.sysMediaToolsDownloaded | sysMediaToolsDownloadFailed)`.

### 5.7 BitTorrent - `TorrentPage` (`SettingsPages.kt:610-655`)

`internal fun TorrentPage(disableTorrentUpload: Boolean, onDisableTorrentUploadToggled: (Boolean) -> Unit, uploadLimit: Long, onUploadLimit: (Long) -> Unit, showEngine: Boolean, isTorrentEngineReady: Boolean, torrentEngineStatus: String)`;
called with `uploadLimit = globalUploadSpeedLimitBytes`, `showEngine = isJvm`.

1. `SettingsGroup()` (no title):
   `Column(padding 16) { TrafficLimitSelector(title = Strings.speedLimitUploadTorrents, speedBytesPerSec = uploadLimit) }` ->
   `OnGlobalUploadSpeedLimitChanged(bytes)` (handler: state,
   `queueRepository.setGlobalUploadSpeedLimit`, persist). See 6.2.
2. `SettingsGroup(title = Strings.torrentSharingGroup)`:
   `SettingSwitch(Strings.downloadOnlyNoUpload, Strings.downloadOnlyNoUploadHint, checked = disableTorrentUpload)` ->
   `OnDisableTorrentUploadToggled(Boolean)`.
3. Desktop only (`showEngine`): `SettingsGroup(title = Strings.torrentEngineGroup)`:
   `SettingRow(title = "libtorrent" (hard-coded), subtitle = localizeStep(torrentEngineStatus) or none when blank)` +
   `StatusLabel(Strings.settingsReady, StatusCompleted)` when `isTorrentEngineReady`, else
   `StatusLabel(Strings.settingsNotReady, error)`.

### 5.8 Updates - `UpdatesSettingsCard()` (`UpdateUi.kt:217-294`)

Reads `UpdateManager.state` (`UpdateState`: `supported`, `currentVersion`, `autoCheck`,
`autoInstall`, `canInstallInApp`, `checking`, `available: AppRelease?`, `message: String?`,
`messageIsInstallError`, `messageIsError`, ...). Returns nothing when `!state.supported`.

`SettingsGroup(title = Strings.sectionUpdates)`:

1.
`SettingRow(title = "FetchJet ${state.currentVersion}", subtitle = available != null ? Strings.versionIsAvailable(available.version) : Strings.youHaveLatest)`
with trailing:
    - update available: `Button(shape 10)` `Strings.updateSeeWhatsNew` ->
      `UpdateManager.showDialog()` (the update dialog is outside settings);
    - else `OutlinedButton(shape 10, enabled = !state.checking)`: while checking a
      `CircularProgressIndicator(size 14, strokeWidth 2)` + 8 dp spacer + `Strings.updateChecking`;
      otherwise `Strings.updateCheckNow` -> `UpdateManager.checkNow()`.
2. When `state.message != null`: `Text(message)` `bodySmall`, `error` when
   `messageIsInstallError || messageIsError` else `onSurfaceVariant`, padding (start = 16, end = 16,
   bottom = 12).
3. `SettingDivider`;
   `SettingSwitch(Strings.checkUpdatesAutomatically, Strings.checkUpdatesAutomaticallyHint, checked = state.autoCheck)` ->
   `UpdateManager.setAutoCheck(Boolean)`.
4. `SettingDivider`; then
    - when `state.canInstallInApp`:
      `SettingSwitch(Strings.downloadUpdatesAutomatically, Strings.downloadUpdatesAutomaticallyHint, checked = state.autoInstall, enabled = state.autoCheck)` ->
      `UpdateManager.setAutoInstall(Boolean)` (Android uses
      `downloadUpdatesAutomaticallyHintAndroid`);
    - else
      `SettingRow(Strings.installingUpdates, PlatformUpdater.updatesFromStore ? Strings.installingUpdatesStore : Strings.installingUpdatesManual)`.
    - Windows: `canInstallInApp` = installed (jpackage launcher present) and not the Store package
      (`WindowsUpdater.kt:17`); `updatesFromStore` = Store package.
5. `SettingDivider`;
   `SettingRow(Strings.updateReleaseNotes, Strings.updateReleaseNotesHint, onClick = open "https://github.com/the-best-is-best/FetchJet/releases")` +
   trailing `Text("↗")` `primary` Bold.

## 6. Composables shared by several pages

### 6.1 `SpeedPresetRow` (`SpeedPresetRow.kt:44-136`)

`internal fun SpeedPresetRow(name: String, bytes: Long, unit: String, onChanged: (Long, String) -> Unit)`.
Used for the Fast / Medium / Low presets and for each schedule window cap.

UI-local state (created once, does not follow later changes of `bytes` / `unit`):
`currentUnit = unit if unit in speedPresetUnits else "MB/s"`;
`text = speedPresetValueText(bytes, currentUnit)`.

Layout `Column(fillMaxWidth)`:

- `Row(fillMaxWidth, SpaceBetween, CenterVertically)`: `name` `titleMedium` SemiBold;
  `speedPresetShortLabel(bytes)` `labelMedium` Bold `primary` (this one follows the live `bytes`).
- 8 dp spacer.
- `FlowRow(horizontal spacing 10, vertical spacing 8)` (wraps on a narrow card):
    -
    `FetchJetTextField(value = text, width 130, height 42, placeholder = Strings.speedLimitValue, leading = "⚡" 14 sp, decimal keyboard)`.
    On input: keep digits and `.` only; `num = text.toDoubleOrNull() ?: 0.0`;
    `onChanged(speedPresetToBytes(num, currentUnit), currentUnit)` on every keystroke (empty or
    unparsable text sends 0 bytes).
    - Unit switcher:
      `Surface(shape 10, color surfaceVariant alpha 0.6, border 1 dp outlineVariant alpha 0.4)`,
      `Row(padding 3, spacedBy(3), CenterVertically)` of the 4 units `KB/s, MB/s, Kbps, Mbps`:
      `Box(clip 8, background = selected ? primaryContainer : transparent, clickable, padding horizontal 10 / vertical 8)`,
      text 12 sp, Bold + `onPrimaryContainer` when selected, Medium + `onSurfaceVariant` otherwise.
      Item height 40, switcher height 46. Click: `currentUnit = u`,
      `onChanged(speedPresetToBytes(text.toDoubleOrNull() ?: 0.0, u), u)` (the typed number is kept,
      the byte value changes).

### 6.2 `TrafficLimitSelector` (`TrafficLimitSelector.kt:39-253`)

`fun TrafficLimitSelector(title: String, speedBytesPerSec: Long, onSpeedChanged: (Long) -> Unit)`.
In settings it is used only for the torrent upload limit.

Presets: `Strings.speedLimitUnlimited` -> 0, `"512 KB/s"` -> 524288, `"1 MB/s"` -> 1048576,
`"5 MB/s"` -> 5242880, `"10 MB/s"` -> 10485760 (the four number labels are hard-coded).

UI-local state:

- `isCustom`: re-initialised on every change of `speedBytesPerSec` to
  `speedBytesPerSec > 0 && no preset equals it`.
- `customUnit` (once): `"MB/s"` when the value is <= 0 or >= 1048576, else `"KB/s"`.
- `customText` (once): `""` when <= 0; when >= 1048576 the MB value (integer when whole, else
  truncated to 1 decimal); else `(bytes / 1024).toString()`.

Layout `Column(fillMaxWidth)`:

- `Row(fillMaxWidth, SpaceBetween, CenterVertically)`: `title` `titleMedium` SemiBold; current label
  `labelMedium` Bold: `Strings.speedLimitNoLimit` in `onSurfaceVariant` when the value is <= 0, else
  `"<n> MB/s"` / `"<n> KB/s"` in `primary` (same formatting as `speedPresetShortLabel`).
- 8 dp spacer.
- `FlowRow(spacing 6 / 6)`: one `FilterChip` per preset (label 12 sp),
  `selected = !isCustom && speedBytesPerSec == value`, click: `isCustom = false`,
  `onSpeedChanged(value)`; then a `FilterChip` `Strings.speedLimitCustom` ("Custom..."),
  `selected = isCustom`, click: `isCustom = true` and, when `customText` is blank,
  `customText = "2"` and `onSpeedChanged(calculateBytes(2.0, customUnit))`.
- When `isCustom`: 10 dp spacer and
  `Surface(shape 12, color surfaceVariant alpha 0.35, border 1 dp outlineVariant alpha 0.5, fillMaxWidth)`
  with `FlowRow(padding horizontal 12 / vertical 8, horizontal spacing 10, vertical spacing 8)`:
    - the same number field as 6.1 (width 130, height 42, `"⚡"`, placeholder
      `Strings.speedLimitValue`); input filtered to digits and `.`; every keystroke
      `onSpeedChanged(calculateBytes(num, customUnit))`;
    - the same unit switcher as 6.1 (`KB/s, MB/s, Kbps, Mbps`); click: `customUnit = u`,
      `onSpeedChanged(calculateBytes(num, u))`;
    - when `calculateBytes(num, customUnit) > 0`: hint text `labelSmall` SemiBold `primary`,
      vertically centred, end padding 6: `"= ${num.toLong()} MB/s"`, `"= ${num.toLong()} KB/s"`,
      `"= ${num.toLong()} Mbps (${totalBytes / 1048576} MB/s)"`,
      `"= ${num.toLong()} Kbps (${totalBytes / 1024} KB/s)"`.

`calculateBytes` is identical to `speedPresetToBytes`.

### 6.3 `speedPreset*` helpers (`SpeedPresetUi.kt`)

See section 9.3.

### 6.4 `StartupSettingsCard` (`StartupUi.kt:24-107`)

Reads / writes `StartupLauncher` (expect object: `isSupported`, `managedBySystemUri`,
`suspend isEnabled()`, `suspend setEnabled(Boolean): Result<Unit>`) and
`StartupPreferences.startHidden` (`LOGIC/core/startup/StartupLauncher.kt`, `startup.properties`,
default true).

JVM actual (`sharedLogic/src/jvmMain/.../core/startup/StartupLauncher.jvm.kt`):
`isSupported = !WindowsStorePackage.isInstalled && AutoLaunch.isRunningFromDistributable` (false
when run from Gradle / an IDE); `managedBySystemUri = "ms-settings:startupapps"` in the Store
package, else null.

Normal branch (line 55-106), shown in the installed non-Store app. UI-local state:
`enabled = false`, `busy = true`, `startHidden = StartupPreferences.startHidden`,
`error: String? = null`; on first show `enabled = StartupLauncher.isEnabled(); busy = false`.

`SettingsGroup(title = Strings.startupGroup)`:

-
`SettingSwitch(Strings.startupStartAtLogin, Strings.startupStartAtLoginHint, checked = enabled, enabled = !busy)`;
on change: `busy = true`, `error = null`, then `StartupLauncher.setEnabled(want)`; on failure
`error = Strings.startupCouldNotChange(message)`; then `enabled = StartupLauncher.isEnabled()`,
`busy = false`.
- `SettingDivider`.
-
`SettingSwitch(Strings.startupStartHidden, Strings.startupStartHiddenHint, checked = startHidden, enabled = enabled && !busy)`;
on change: local state and `StartupPreferences.startHidden = it`.
- When `error != null`: `Text(error)` `bodySmall` `error`, padding (start = 16, end = 16, bottom =
  12).

Store branch (line 25-54), when `managedBySystemUri != null`:
`SettingsGroup(title = Strings.startupGroup, footer = Strings.startupStoreFooter)`:
`SettingRow(Strings.startupStartAtLogin, Strings.startupManagedByWindows, onClick = open uri)` +
`OutlinedButton` (pill shape) `Strings.startupOpenWindowsSettings` (SemiBold) that opens
`ms-settings:startupapps`; `SettingDivider`;
`SettingSwitch(Strings.startupStartHidden, Strings.startupStartHiddenHint, checked = startHidden)`.

Reachability: `GeneralPage` calls the card only `if (StartupLauncher.isSupported)`
(`SettingsPages.kt:140`), and `isSupported` is false in the Store package, so the Store branch is
never shown from Settings today (see Open questions).

## 7. External widgets the pages rely on

### 7.1 `FetchJetTextField` (`FetchJetTextField.kt:72-124`, frame 245-343, body 346-401)

Parameters used by settings: `value`, `onValueChange`, `modifier`, `label`, `placeholder`,
`leading`, `trailing`, `enabled`, `height` (default 48; 42 in speed / time fields),
`keyboardOptions`, `visualTransformation` (password dots). Default
`shape = RoundedCornerShape(12.dp)`, text `bodyMedium` `onSurface`, single line.

- Optional label above: 11 sp SemiBold, letter spacing 0.3, `maxLines = 1`, padding start 4 / bottom
  5 (24 dp line box); colour `onSurfaceVariant`, `primary` when focused, `error` on error.
- Box: background `surfaceVariant` alpha 0.35 over `surface`; focused `primary` alpha 0.06 over
  `surface`. Border: 1 dp `outlineVariant` alpha 0.7; hovered 1 dp `outline`; focused 1.5 dp
  `primary`; error 1.5 dp `error`. Focus / error glow: drop shadow radius 10, offset y 2, accent
  colour at alpha 0.28. Colour and width changes animate over 180 ms, the glow over 220 ms.
- Body row: fixed height; padding start 10 with a leading element (else 14), end 6 with a trailing
  element (else 14); leading has 8 dp end padding and alpha 0.75 (1 when focused); placeholder in
  `onSurfaceVariant` alpha 0.65, `maxLines = 1`, ellipsis; trailing has 6 dp start padding. Cursor
  colour `primary`.
- Disabled: whole field at alpha 0.6, no hover.
- Error text under the field (not used by settings).

### 7.2 `FieldIconButton(text, onClick, tint = onSurfaceVariant)` (`FetchJetTextField.kt:178-200`)

30 dp circle, glyph 14 sp, hover background `onSurface` alpha 0.08 (150 ms tween). Used for the
schedule window remove button `"✕"`.

### 7.3 Others

`AppScrollbar` (4.2), `DirectoryPickerDialog` (5.2), Material `AlertDialog` (2.3), `DropdownMenu`
(2.3), `LocalUriHandler.openUri` (opens a URL or `ms-settings:` URI with the OS), clipboard
`setText`.

## 8. Every page and row, Windows desktop

"State" = field of `DashboardUiState` unless a sharedLogic object is named. Events are
`DashboardUiEvent`s unless a direct call is named.

### General

| # | Group                 | Row                            | Control                                                  | Value source                     | Event / call                         | Shown on                           |
|---|-----------------------|--------------------------------|----------------------------------------------------------|----------------------------------|--------------------------------------|------------------------------------|
| 1 | Language              | Language / hint                | dropdown (System + 15 languages)                         | `AppLanguage.choice`             | `OnLanguageChanged(code)`            | all                                |
| 2 | Clipboard             | Watch the clipboard            | switch                                                   | `monitorClipboard`               | `OnMonitorClipboardChanged(on)`      | all                                |
| 3 | Clipboard             | Also video pages               | switch, enabled when row 2 is on                         | `clipboardVideoPages`            | `OnClipboardVideoPagesChanged(on)`   | all                                |
| - | Diagnostics           | Save log to Downloads          | button                                                   | `DiagnosticsLog`                 | `DiagnosticsLog.export()`            | Android only                       |
| 4 | Appearance            | Theme                          | segmented System / Light / Dark                          | `themeMode`                      | `OnThemeModeChanged(mode)`           | all                                |
| 5 | Appearance            | Speed shown in                 | segmented MB/s / KB/s / Mbps / Kbps                      | `speedDisplayUnit` via `unitKey` | `OnSpeedDisplayUnitChanged(unit)`    | all                                |
| 6 | Startup               | Start FetchJet when you log in | switch, disabled while busy                              | `StartupLauncher.isEnabled()`    | `StartupLauncher.setEnabled(want)`   | desktop, installed non-Store build |
| 7 | Startup               | Start hidden in the tray       | switch, enabled when row 6 is on and not busy            | `StartupPreferences.startHidden` | `StartupPreferences.startHidden = v` | same                               |
| 8 | When downloads finish | (picker only)                  | segmented Do nothing / Exit FetchJet / Sleep / Shut down | `WhenDone.action`                | `WhenDone.choose(action)`            | desktop                            |

### Downloads

| #  | Group         | Row                                | Control                                                             | Value source                                    | Event / call                                                      | Shown on                                      |
|----|---------------|------------------------------------|---------------------------------------------------------------------|-------------------------------------------------|-------------------------------------------------------------------|-----------------------------------------------|
| 1  | Save location | folder path                        | text field                                                          | `destinationPath`                               | `OnDestinationPathChanged(text)` per keystroke                    | all                                           |
| 2  | Save location | Choose folder…                     | filled button                                                       | -                                               | opens `DirectoryPickerDialog` -> `OnDestinationPathChanged(path)` | all                                           |
| 3  | Save location | Open                               | outlined button                                                     | `destinationPath`                               | `pathResolver.openDirectoryInExplorer(path)`                      | where `isOpenFolderSupported()` (desktop yes) |
| 4  | Save location | Use Downloads                      | outlined button, only when path != default Downloads                | `pathResolver.getDownloadsDirectory()`          | `OnDestinationPathChanged(default)`                               | all                                           |
| 5  | Connections   | Connections per download           | chips Auto / 1 / 4 / 8 / 16 / 32                                    | `defaultConnections`                            | `OnDefaultConnectionsChanged(n)`                                  | all                                           |
| 6  | Speed presets | High                               | static "Unlimited (Max)"                                            | -                                               | -                                                                 | all                                           |
| 7  | Speed presets | Fast                               | number field + unit switcher                                        | `speedPresetFastBytes/Unit`                     | `OnSpeedPresetChanged(FAST, bytes, unit)`                         | all                                           |
| 8  | Speed presets | Medium                             | same                                                                | `speedPresetMediumBytes/Unit`                   | `OnSpeedPresetChanged(MEDIUM, bytes, unit)`                       | all                                           |
| 9  | Speed presets | Low                                | same                                                                | `speedPresetLowBytes/Unit`                      | `OnSpeedPresetChanged(LOW, bytes, unit)`                          | all                                           |
| 10 | (schedule)    | Speed schedule                     | switch                                                              | `SpeedSchedule.enabled`, `activeCap`            | `SpeedSchedule.setEnabled(on)`                                    | all                                           |
| 11 | (schedule)    | each window: From, To, remove, cap | 2 time fields with AM/PM, icon button, number field + unit switcher | `SpeedSchedule.windows`                         | `updateWindow(i, w)`, `removeWindow(i)`                           | all, when enabled                             |
| 12 | (schedule)    | + Add a time window                | filled button                                                       | -                                               | `SpeedSchedule.addWindow(8:00-18:00, 1 MB/s)`                     | all, when enabled                             |
| 13 | Organize      | Sort into folders by type          | switch                                                              | `sortIntoFolders`                               | `OnSortIntoFoldersChanged(on)`                                    | all                                           |
| 14 | Queue         | Downloads at the same time         | segmented 1 / 2 / 3 / 4 / 5 / 8 / No limit                          | `QueueManagerRepository.maxConcurrentDownloads` | `setMaxConcurrentDownloads(n)` (0 = no limit)                     | all                                           |

### Browser extension

| # | Group               | Row                                                                                 | Control                                                                                        | Value source                                                                                    | Event / call                                                                    | Shown on                                   |
|---|---------------------|-------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|--------------------------------------------|
| 1 | Install             | one row per browser (Chrome, Edge, Firefox, Brave, Opera, Vivaldi; installed first) | filled or outlined button (Add extension / Update now / Open store / Update from FetchJet / …) | `BrowserIntegration.browsers()`, `BridgePairing.seenBrowsers`, `seenVersions`, `ExtensionStore` | `BrowserIntegration.addExtension(id)` / `installXpi` / open store URL / dialogs | desktop list; Android has its own list     |
| - | Install             | Keep ready for the browser, Pop up over the browser                                 | switches                                                                                       | `BrowserIntegration`                                                                            | -                                                                               | Android only                               |
| 2 | Browser connections | each allowed origin                                                                 | outlined button Revoke                                                                         | `BridgePairing.legacyAllowed`                                                                   | `BridgePairing.revokeAllowed(origin)`                                           | all, group hidden when both sets are empty |
| 3 | Browser connections | each blocked origin                                                                 | outlined button Unblock                                                                        | `BridgePairing.blocked`                                                                         | `BridgePairing.unblock(origin)`                                                 | same                                       |

### Devices

| # | Group           | Row                                        | Control                                  | Value source                        | Event / call                               | Shown on |
|---|-----------------|--------------------------------------------|------------------------------------------|-------------------------------------|--------------------------------------------|----------|
| 1 | Send to device  | Visible to other devices                   | switch                                   | `BehaviorSettings.visibleToDevices` | `BehaviorSettings.setVisibleToDevices(on)` | all      |
| 2 | Paired devices  | each device (name, platform) or empty text | outlined button Unpair (error colour)    | `pairedDevices`                     | `OnUnpairDeviceClicked(id)`                | all      |
| 3 | Blocked devices | each device or empty text                  | outlined button Unblock (primary colour) | `blockedDevices`                    | `OnUnblockDeviceClicked(id)`               | all      |

### Network

| # | Group       | Row                                          | Control                                                                                | Value source                     | Event / call                           | Shown on |
|---|-------------|----------------------------------------------|----------------------------------------------------------------------------------------|----------------------------------|----------------------------------------|----------|
| 1 | Proxy       | kind                                         | segmented Off / HTTP / SOCKS5                                                          | draft of `NetworkSettings.proxy` | local draft                            | all      |
| 2 | Proxy       | Server, Port, User name (optional), Password | text fields (when kind is not Off)                                                     | draft                            | local draft                            | all      |
| 3 | Proxy       | Save (+ "Saved" label)                       | filled button, enabled when changed and valid                                          | -                                | `NetworkSettings.setProxy(...)`        | all      |
| 4 | Site logins | each login (site, user) or "No saved logins" | text button Remove                                                                     | `NetworkSettings.logins`         | `NetworkSettings.removeLogin(site)`    | all      |
| 5 | Site logins | Site, User name, Password, Add               | text fields + filled button, enabled when the site has a dot and the user is not blank | local                            | `NetworkSettings.saveLogin(SiteLogin)` | all      |

### Media tools

| # | Group | Row                               | Control                                              | Value source                                                     | Event / call                                 | Shown on                  |
|---|-------|-----------------------------------|------------------------------------------------------|------------------------------------------------------------------|----------------------------------------------|---------------------------|
| 1 | Tools | yt-dlp                            | status label Ready / Missing                         | `isYtDlpInstalled`                                               | -                                            | desktop, iOS full edition |
| 2 | Tools | FFmpeg                            | status label Ready / Missing · up to 720p            | `isFfmpegInstalled`                                              | -                                            | same                      |
| 3 | Tools | JS runtime · name                 | status label, only when `jsRuntimeName` is not blank | `jsRuntimeName`, `isJsRuntimeReady`                              | -                                            | same                      |
| 4 | Tools | Tools folder                      | clickable row + "Open"                               | `toolsDirectoryPath`                                             | `pathResolver.openDirectoryInExplorer(path)` | desktop                   |
| 5 | -     | Update tools / Download the tools | outlined (ready) or filled button, progress          | `isDownloadingYtDlp`, `toolDownloadName`, `toolDownloadFraction` | `OnDownloadYtDlpClicked`                     | same as 1                 |
| 6 | -     | progress bar                      | linear progress                                      | `toolDownloadFraction`                                           | -                                            | same                      |
| 7 | -     | status text                       | text                                                 | `ytDlpStatus` through `localizeStep`                             | -                                            | same                      |

### BitTorrent

| # | Group      | Row                          | Control                                                                                 | Value source                                  | Event / call                             | Shown on                    |
|---|------------|------------------------------|-----------------------------------------------------------------------------------------|-----------------------------------------------|------------------------------------------|-----------------------------|
| 1 | (no title) | Upload (torrents)            | chips Unlimited (Max) / 512 KB/s / 1 MB/s / 5 MB/s / 10 MB/s / Custom..., custom editor | `globalUploadSpeedLimitBytes`                 | `OnGlobalUploadSpeedLimitChanged(bytes)` | where `AppFeatures.torrent` |
| 2 | Sharing    | Download only (don't upload) | switch                                                                                  | `disableTorrentUpload`                        | `OnDisableTorrentUploadToggled(disable)` | same                        |
| 3 | Engine     | libtorrent                   | status label Ready / Not ready                                                          | `isTorrentEngineReady`, `torrentEngineStatus` | -                                        | desktop only                |

### Updates

| #  | Group   | Row                             | Control                                         | Value source                       | Event / call                                | Shown on                                                     |
|----|---------|---------------------------------|-------------------------------------------------|------------------------------------|---------------------------------------------|--------------------------------------------------------------|
| 1  | Updates | FetchJet version                | button See what's new, or Check now / Checking… | `UpdateManager.state`              | `UpdateManager.showDialog()` / `checkNow()` | where `state.supported`                                      |
| 2  | Updates | result message                  | text                                            | `state.message`                    | -                                           | same                                                         |
| 3  | Updates | Check for updates automatically | switch                                          | `state.autoCheck`                  | `UpdateManager.setAutoCheck(on)`            | same                                                         |
| 4  | Updates | Download updates automatically  | switch, enabled when row 3 is on                | `state.autoInstall`                | `UpdateManager.setAutoInstall(on)`          | when `canInstallInApp` (installed MSI build)                 |
| 4' | Updates | Installing updates              | info row                                        | `PlatformUpdater.updatesFromStore` | -                                           | when not `canInstallInApp` (Store build, or run from Gradle) |
| 5  | Updates | Release notes                   | clickable row with "↗"                          | -                                  | open the releases page                      | same                                                         |

## 9. Strings

### 9.1 `LocalizationManager.Strings` members used (English text from
`LOGIC/core/i18n/data_provider/EnglishProvider.kt`)

Shell: `sectionGeneral`, `sectionDownloads`, `sectionBrowser`, `sectionDevices`, `sectionNetwork`,
`sectionMedia`, `sectionTorrent`, `sectionUpdates` and the eight `...Blurb` members (table in 4.1);
`changesSavedAutomatically` "Changes are saved automatically."; `settingsAllSettings` "All
settings"; `summaryNoQueueLimit` "no limit"; `summaryAtATime(n)` "$n at a time";
`summarySetupNeeded` "Setup needed"; `summaryUploadingOff` "Uploading off";
`summaryUploadingOn` "Uploading on"; `summaryVisible` "Visible"; `summaryHidden` "Hidden";
`summaryVersionAvailable(version)` "$version available"; `summaryVersion(version)` "Version $
version"; `settingsReady` "Ready"; `homeSettings` (top bar title).

General: `language` "Language"; `languageHint` "The language of every screen. System follows your
device."; `systemLanguage` "System"; `languageName` "English"; `clipboardGroup` "Clipboard";
`clipboardFooter` "Copy a link to a file (zip, mp4, pdf, apk…) or a magnet link anywhere, and
FetchJet asks whether to download it."; `watchClipboard` "Watch the clipboard"; `watchClipboardOn`
"On · copied file links are offered for download"; `settingOff` "Off"; `alsoVideoPages` "Also video
pages"; `alsoVideoPagesHint` "YouTube, Facebook, TikTok, Vimeo… links are offered too (not only
files)"; `appearanceGroup` "Appearance"; `themeTitle` "Theme"; `themeHint` "Light or dark look for
every screen"; `settingsThemeSystem` "System"; `settingsThemeLight` "Light"; `settingsThemeDark`
"Dark"; `speedShownIn` "Speed shown in"; `speedShownInHint` "Bytes (MB/s) like file sizes, or bits
(Mbps) like your internet plan"; `startupGroup` "Startup"; `startupStartAtLogin` "Start FetchJet
when you log in"; `startupStartAtLoginHint` "Scheduled and queued downloads continue, and the
browser extension always finds the app."; `startupStartHidden` "Start hidden in the tray";
`startupStartHiddenHint` "When started at login, stay in the tray instead of opening the window.";
`startupCouldNotChange(message)` "Could not change the setting: $message"; `startupStoreFooter` "The
Store version is started by Windows: turn FetchJet on under Settings > Apps > Startup.";
`startupManagedByWindows` "Managed by Windows"; `startupOpenWindowsSettings` "Open Windows
settings"; `whenDoneGroup` "When downloads finish"; `whenDoneHint` "For this session only. You get
60 seconds to cancel first."; `whenDoneNothing` "Do nothing"; `whenDoneExit` "Exit FetchJet";
`whenDoneSleep` "Sleep"; `whenDoneShutDown` "Shut down". Android only: `diagnosticsGroup`,
`diagnosticsFooter`, `saveLogToDownloads`, `save`, `logSavedTo(path)`, `logCouldNotSave`.

Downloads: `saveLocationGroup` "Save location"; `saveLocationFooter` "New downloads go here unless
you pick another folder when adding them."; `settingsFolderPath` "Folder path";
`settingsChooseFolder` "Choose folder…"; `open` "Open"; `settingsUseDownloads` "Use Downloads";
`settingsConnectionsGroup` "Connections"; `connectionsFooter` "How many pieces a direct download is
split into. Each piece is a connection; when one finishes it takes half of the biggest remaining
piece, so none sits idle. Auto picks by file size (4–8). Some servers limit connections per
client."; `settingsConnectionsPerDownload` "Connections per download"; `settingsConnectionsAuto`
"Auto"; `speedPresetsTitle` "Speed presets"; `speedPresetsFooter` "Set a speed for Fast, Medium and
Low. Pick one quickly from the header; the turtle is always 512 KB/s."; `speedPresetHigh` "High";
`speedPresetFast` "Fast"; `speedPresetMedium` "Medium"; `speedPresetLow` "Low";
`speedLimitUnlimited` "Unlimited (Max)"; `speedLimitValue` "Value"; `speedScheduleTitle` "Speed
schedule"; `speedScheduleActive` "Scheduled limit active now"; `speedScheduleFooter` "Cap the speed
during set times of day; unlimited outside them."; `speedScheduleEmpty` "No time windows yet — add
one."; `speedScheduleFrom` "From"; `speedScheduleTo` "To"; `speedScheduleAdd` "Add a time window";
`organizeGroup` "Organize"; `organizeFooter` "Sub-folders are created inside the save location:
Video, Music, Archives, Documents, Programs, Images, Torrents."; `sortIntoFolders` "Sort into
folders by type"; `sortIntoFoldersOn` "On · e.g. Downloads/Video/lecture.mp4"; `sortIntoFoldersOff`
"Off · everything goes straight into the folder"; `homeQueueTitle` "Queue"; `homeQueueFooter` "Extra
downloads wait and start by themselves, oldest first, when one finishes. \"Start now\" on a waiting
download skips the queue."; `homeDownloadsAtSameTime` "Downloads at the same time"; `homeNoLimit`
"No limit"; `homeAtATime(count)` "$count at a time".

Browser: `extInstallGroup` "Install"; `extensionInstallFooter` "With the extension, a \"Download
with FetchJet\" button shows on videos and links, and the browser can hand its downloads to
FetchJet. It talks only to this app on your device."; `browserNotInstalled` "Not installed on this
device"; `extensionOldVersion(wanted)` "Connected with an extension older
than ${wanted}: update it"; `extensionNeedsUpdate(found, wanted)` "Extension ${found} is older
than ${wanted}: update it"; `storeHasUpdate(version)` "The store already has ${version}: the browser
updates it by itself."; `updateFromAppHint` "The store does not have it yet: update from the copy
inside FetchJet."; `updateFromGitHubHint(version)` "The store does not have it yet: FetchJet can
install the signed ${version} from its release."; `storeWaiting(wanted)` "Version ${wanted} is
waiting for the store's approval; the browser updates it by itself once approved.";
`extensionConnectedVersion(version)` "Extension connected · ${version}";
`extensionNotAddedYet` "Extension not added yet"; `updateFromGitHub` "Update now";
`openStore` "Open store"; `updateFromApp` "Update from FetchJet"; `addExtension` "Add extension";
`extensionDownloadFailed` "Could not download the extension. Check your connection and try again.";
`browserMissingTitle(name)` "${name} is not installed"; `browserMissingMessage(name)`
"${name} is not on this device. Install it first, then come back and add the extension.";
`downloadBrowser(name)` "Download ${name}"; `close` "Close"; `loadUnpackedTitle(name)` "Add FetchJet
to ${name}"; `loadUnpackedSteps(name, page)` "${name} opened its extensions page
(${page}). There:\n1. Turn on Developer mode.\n2. Click Load unpacked.\n3. Choose this folder (its path is already copied, just paste it):";
`loadUnpackedUpdates` "Do this once: the extension then updates by itself whenever FetchJet updates.";
`copyPath` "Copy path"; `detailsOpenFolder` "Open folder"; `installFromFileTitle(name)`,
`installFromFileSteps(name, file)`, `openBrowser(name)` "Open ${name}"; `extConnectionsGroup`
"Browser connections"; `extConnectionsFooter` "Browser extensions you have allowed or blocked from
sending links to FetchJet."; `extAllowedLabel` "Allowed"; `extBlockedLabel` "Blocked"; `extRevoke`
"Revoke"; `extUnblock` "Unblock". Android only: `keepReadyForBrowser`, `keepReadyOn`,
`keepReadyOff`, `popUpOverBrowser`, `popUpOverBrowserOn`, `popUpOverBrowserOff`.

Devices: `sysDeviceSendTitle` "Send to device"; `visibleToDevices` "Visible to other devices";
`visibleToDevicesHint` "Other FetchJet devices on this network can find this one and send downloads
to it. Off: you can still send to them."; `pairedDevicesTitle` "Paired devices"; `noPairedDevices`
"No paired devices yet. Pair one from Send to device on a download."; `unpairDevice` "Unpair";
`blockedDevicesTitle` "Blocked devices"; `noBlockedDevices` "No blocked devices"; `unblockDevice`
"Unblock".

Network: `netProxyGroup` "Proxy"; `netProxyHint` "Used by downloads, video downloads (yt-dlp) and
torrents. Applies to downloads started after saving."; `netProxyOff` "Off"; `netProxyHost` "Server";
`netProxyPort` "Port"; `netProxyUser` "User name (optional)"; `netProxyPassword` "Password";
`netProxyBadPort` "Enter a server and a port from 1 to 65535"; `netSaved` "Saved"; `netSave` "Save";
`netLoginsGroup` "Site logins"; `netLoginsHint` "For downloads that ask for a user name and password
(HTTP basic auth). A site also covers its sub-domains."; `netLoginsEmpty` "No saved logins";
`netRemove` "Remove"; `netLoginSite` "Site (example.com)"; `netUser` "User name"; `netLoginAdd`
"Add"; `netLoginsCount(n)` "${n} logins".

Media: `mediaToolsGroup` "Tools"; `ytDlpHint` "Finds the video and audio on YouTube and other
sites"; `ffmpegHint` "Joins video + audio (1080p and up), cuts clips, makes MP3"; `settingsMissing`
"Missing"; `ffmpegMissing` "Missing · up to 720p"; `jsRuntimeTitle(name)` "JS runtime · $name";
`jsRuntimeHint` "Solves YouTube's checks so every quality shows up, faster";
`missingPress(button)` "Missing · press $button"; `mediaUpdateTools` "Update tools";
`mediaDownloadTheTools` "Download the tools"; `mediaToolsFolder` "Tools folder";
`mediaUpdatingTools` "Updating…"; `mediaDownloadingTools` "Downloading tools…";
`mediaDownloadingTool(name)` "Downloading $name…";
`mediaDownloadingToolPercent(name, percent)` "Downloading $name… $percent%".

BitTorrent: `speedLimitUploadTorrents` "Upload (torrents)"; `speedLimitNoLimit` "No Limit (Max
Speed)"; `speedLimitCustom` "Custom..."; `torrentSharingGroup` "Sharing"; `downloadOnlyNoUpload`
"Download only (don't upload)"; `downloadOnlyNoUploadHint` "Saves your upload bandwidth and data
quota. Some trackers download slower for peers that don't share."; `torrentEngineGroup` "Engine";
`settingsNotReady` "Not ready".

Updates: `versionIsAvailable(version)` "Version $version is available"; `youHaveLatest` "You have
the version installed here"; `updateSeeWhatsNew` "See what's new"; `updateChecking` "Checking…";
`updateCheckNow` "Check now"; `checkUpdatesAutomatically` "Check for updates automatically";
`checkUpdatesAutomaticallyHint` "Looks for a new version when FetchJet starts (at most twice a
day)."; `downloadUpdatesAutomatically` "Download updates automatically";
`downloadUpdatesAutomaticallyHint` "Downloads a new version in the background; you only confirm the
restart."; `installingUpdates` "Installing updates"; `installingUpdatesStore` "Microsoft Store
installs new versions by itself; FetchJet tells you when one is out."; `installingUpdatesManual` "On
this device FetchJet tells you about a new version and links to it; you install it.";
`updateReleaseNotes` "Release notes"; `updateReleaseNotesHint` "Every version and what changed".

### 9.2 Hard-coded texts

Nav glyphs `◐ ↓ ⧉ ⇄ ◎ ▶ ⇅ ↻`; `"←"`, `"›"`, `"▾"` (after two spaces), `"✕"`, `"↗"`, `"…"` (busy
button), `"+  "` prefix, `"⚠️ "` and `"✓ "` prefixes, `" · "` separators;
`"Edge · Firefox · Chrome"`; `"MB/s"`, `"KB/s"`, `"Mbps"`, `"Kbps"`; `"512 KB/s"`, `"1 MB/s"`,
`"5 MB/s"`, `"10 MB/s"`; `"AM"`, `"PM"`; placeholders `"8:00"`, `"127.0.0.1"`, `"1080"`, `"8080"`,
`"example.com"`; `"HTTP"`, `"SOCKS5"`; `"yt-dlp"`, `"FFmpeg"`, `"libtorrent"`;
`"FetchJet <version>"`; emoji `"📁"`, `"⚡"`; the hint formats `"= N MB/s"` etc. Speed values are not
wrapped in `ltr()` on these pages.

### 9.3 Pure helper functions (exact rules)

- `unitKey(speedDisplayUnit)`: see 4.2.
- `themeModeLabel(mode)`: see 5.1.
- `folderName(path)` (`SettingsPages.kt:659`):
  `path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }`.
- `limitText(bytes)` (`SettingsPages.kt:662`): defined but not called anywhere.
- `networkSummary(p, n)`: see 5.5.
- `whenDoneLabel(action)`: see 5.1.
- `speedPresetUnits = ["KB/s", "MB/s", "Kbps", "Mbps"]`.
- `speedPresetToBytes(value: Double, unit)`: KB/s `(value * 1024.0).toLong()`; MB/s
  `(value * 1024.0 * 1024.0).toLong()`; Kbps `(value * 1000.0 / 8.0).toLong()`; Mbps
  `(value * 1000.0 * 1000.0 / 8.0).toLong()`; unknown unit as MB/s. `toLong()` truncates.
- `speedPresetValueText(bytes, unit)`: `""` when `bytes <= 0`; `v` = KB/s `bytes / 1024.0`; MB/s
  `bytes / 1048576.0`; Kbps `bytes * 8.0 / 1000.0`; Mbps `bytes * 8.0 / 1000000.0`; result: when `v`
  is whole `v.toLong().toString()`, else `((v * 10).toLong() / 10.0).toString()` (truncated, not
  rounded, to one decimal; a value below 0.1 gives `"0.0"`).
- `speedPresetShortLabel(bytes)`: when `bytes >= 1048576`: `mb = bytes / 1048576.0`, whole ->
  `"<mb> MB/s"`, else `"<truncated to 1 decimal> MB/s"`; otherwise `"${bytes / 1024} KB/s"` (integer
  division; 0 gives `"0 KB/s"`).
- `minutesToHm12(m)` (`SettingsPages.kt:583`): `mm = ((m % 1440) + 1440) % 1440`; `h24 = mm / 60`;
  `min = mm % 60`; `h12 = (h24 % 12 == 0) ? 12 : h24 % 12`; `"$h12:${min padded to 2 digits}"`.
  Initial `isPm = mm >= 720`.
- `parseHm12(text, isPm)` (`SettingsPages.kt:594`): split on `:`; must be exactly 2 parts, both
  integers, hour in 1..12, minute in 0..59; `h24` = PM and 12 -> 12; PM -> h + 12; AM and 12 -> 0;
  else h; result `h24 * 60 + m`; null when invalid.
- `SiteLogin.cleanSite(text)` (`LOGIC/core/network/NetworkSettings.kt:62`):
  `text.trim().substringAfter("://").substringBefore('/').substringAfter('@').substringBefore(':').lowercase().removePrefix("www.").trimEnd('.')`.
- `seenId(browserId)`, `isOlderExtension(found, wanted = EXTENSION_VERSION)` and the row rules of
  5.3.
- `DeviceListGroup` host / platform split and sort: see 5.4.
- `TrafficLimitSelector`: initial `customUnit` / `customText`, current label and hint formats: see
  6.2.
- `localizeStep(text)` (`LOGIC/core/i18n/start/StepText.kt:7`): already in sharedLogic; the
  presenter applies it to `ytDlpStatus` and `torrentEngineStatus`.

## 10. (a) Reusable widgets for the Slint port

| Widget                                                  | Behaviour                                                                                                                                                            | Visual spec |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| `SettingsGroup`                                         | optional caption, card, optional footer; children stacked                                                                                                            | 3.1         |
| `SettingDivider`                                        | static                                                                                                                                                               | 3.2         |
| `SettingRow`                                            | title, optional multi-line subtitle, optional trailing slot, optional click, enabled flag                                                                            | 3.3         |
| `SettingSwitchRow`                                      | row + switch, whole row toggles                                                                                                                                      | 3.4         |
| `FjSwitch`                                              | Material 3 switch with animated thumb, hover / pressed / disabled states                                                                                             | 2.3         |
| `SegmentedPicker`                                       | list of labels + selected index, `selected(index)` callback, optional horizontal scroll wrapper                                                                      | 3.5         |
| `StatusLabel`                                           | dot + text; tone = green / amber / error                                                                                                                             | 3.6         |
| `SectionNavItem`                                        | glyph tile, title, hint with optional tone, selected state, optional chevron                                                                                         | 4.4         |
| `PageHeader`                                            | title + blurb                                                                                                                                                        | 4.3         |
| `FjButton` (filled), `FjOutlinedButton`, `FjTextButton` | enabled flag, optional leading spinner, corner radius 10 or pill, optional text colour override (error / primary), 48 dp layout height with a 40 dp visual           | 2.3         |
| `FjFilterChip`                                          | selected flag, 12 or 14 sp label                                                                                                                                     | 2.3         |
| `FjTextField`                                           | label, placeholder, leading glyph, trailing slot, height 48 / 42, password mode, enabled, input filter done by the presenter                                         | 7.1         |
| `FieldIconButton`                                       | 30 dp round glyph button                                                                                                                                             | 7.2         |
| `UnitSwitcher`                                          | the 4 speed units, `primaryContainer` selection                                                                                                                      | 6.1         |
| `SpeedValueEditor`                                      | `FjTextField` (130 x 42, "⚡") + `UnitSwitcher`, wrapping flow                                                                                                       | 6.1         |
| `AmPmToggle`                                            | AM / PM pill                                                                                                                                                         | 5.2         |
| `FjDropdownMenu`                                        | anchored popup list with a highlighted current item, scrollable, closes on outside click / Esc                                                                       | 2.3, 5.1    |
| `FjAlertDialog`                                         | title, body slot, confirm and dismiss buttons                                                                                                                        | 2.3, 5.3    |
| `FjSpinner`                                             | indeterminate circular progress, size 14 / 16, stroke 2                                                                                                              | 2.3         |
| `FjLinearProgress`                                      | determinate (with gap and stop dot) and indeterminate                                                                                                                | 2.3         |
| `AppScrollbar`                                          | overlay scrollbar for a scrolling page                                                                                                                               | 4.2         |
| `FlowRow` layout                                        | wrapping row with horizontal and vertical spacing (chips, speed editor); Slint has no built-in wrap layout, so it needs a manual layout or a fixed two-line fallback | 6.1, 6.2    |

Shared with other areas: `FjTextField`, the three buttons, `FjAlertDialog`, `FjDropdownMenu`,
`AppScrollbar`, `DirectoryPickerDialog`.

## 11. (b) Port units

| Unit                           | Source                                                                                         | Compose lines | Depends on                                                                              | State from the presenter                                                                                                                                                                                                                                | Events sent                                                                                                                                                                    |
|--------------------------------|------------------------------------------------------------------------------------------------|---------------|-----------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| S1 Settings kit                | `SettingsKit.kt` 1-197; visual specs of 2.3                                                    | 197           | theme tokens, `FjSwitch`, buttons, `FjTextField`                                        | none                                                                                                                                                                                                                                                    | none (generic callbacks)                                                                                                                                                       |
| S2 Settings shell              | `FdmSettingsPanel.kt` 51-431; `SettingsContent.kt` 10-111                                      | 490           | S1, `AppScrollbar`                                                                      | visible sections with glyph, title, blurb, summary text and tone; selected section; wide / narrow                                                                                                                                                       | select section, back to list                                                                                                                                                   |
| S3 General page                | `SettingsPages.kt` 49-149 and 760-801; `StartupUi.kt` 24-107; `WhenDoneUi.kt` 34-64            | 320           | S1, `FjDropdownMenu`, `FjTextButton`                                                    | language choice, display name and option list; clipboard flags; theme mode; unit key; startup supported / enabled / busy / hidden / error; when-done supported / action                                                                                 | `OnLanguageChanged`, `OnMonitorClipboardChanged`, `OnClipboardVideoPagesChanged`, `OnThemeModeChanged`, `OnSpeedDisplayUnitChanged`, startup enable / hidden, when-done choose |
| S4 Downloads page              | `SettingsPages.kt` 248-426; `QueueUi.kt` 19-47; `SpeedPresetRow.kt` 44-136; `SpeedPresetUi.kt` | 350           | S1, `FjTextField`, `FjFilterChip`, `SpeedValueEditor`, buttons, `DirectoryPickerDialog` | destination path, default Downloads path, open-folder supported; connections; three presets (text, unit, short label); sort flag; queue size and subtitle                                                                                               | `OnDestinationPathChanged`, choose folder, open folder, `OnDefaultConnectionsChanged`, `OnSpeedPresetChanged`, `OnSortIntoFoldersChanged`, set queue size                      |
| S5 Speed schedule              | `SettingsPages.kt` 433-607                                                                     | 175           | S1, S4 `SpeedValueEditor`, `FjTextField`, `AmPmToggle`, `FieldIconButton`               | enabled, active-now flag, list of windows (from text, from PM, to text, to PM, cap text, cap unit, cap label)                                                                                                                                           | enable, add window, remove window, edit from / to text, toggle AM/PM, edit cap value / unit                                                                                    |
| S6 BitTorrent page             | `SettingsPages.kt` 610-655; `TrafficLimitSelector.kt` 39-253                                   | 300           | S1, `FjFilterChip`, `SpeedValueEditor`, `StatusLabel`, `FlowRow`                        | upload limit: current label and tone, selected chip, custom flag / text / unit / hint; disable-upload flag; engine row visible, ready, status text                                                                                                      | `OnGlobalUploadSpeedLimitChanged`, custom chip, custom text / unit, `OnDisableTorrentUploadToggled`                                                                            |
| S7 Network + Devices pages     | `NetworkSettingsUi.kt` 36-227; `SettingsPages.kt` 153-245                                      | 320           | S1, `FjTextField` (password mode), buttons, `StatusLabel`                               | proxy draft (kind, host, port, user, password), placeholders, error visible, saved label visible, save enabled; logins list; add-login fields and can-add; visible-to-devices; paired and blocked lists (id, host, platform)                            | proxy field edits, save proxy, remove login, add-login edits, add login, set visible, `OnUnpairDeviceClicked`, `OnUnblockDeviceClicked`                                        |
| S8 Browser extension page      | `SettingsPages.kt` 676-757; `BrowserInstallSection.kt` 45-233                                  | 315           | S1, buttons, `FjAlertDialog`, clipboard, open URL                                       | browser rows (id, name, subtitle, button label, filled / outlined, enabled); current dialog (kind, title, text, folder, button labels); allowed and blocked origins                                                                                     | browser button click, dialog actions (download browser, copy path, open folder, open browser, close), revoke, unblock                                                          |
| S9 Media tools + Updates pages | `SettingsMediaToolsPage.kt` 32-154; `UpdateUi.kt` 217-294                                      | 230           | S1, `StatusLabel`, buttons, `FjSpinner`, `FjLinearProgress`                             | tool rows (label, tone), JS runtime row, tools folder, button label / style / enabled / spinner, progress visible / fraction, status text; update title, subtitle, button kind, checking, message and tone, auto-check, auto-install row kind / enabled | `OnDownloadYtDlpClicked`, open tools folder, check now, show update dialog, set auto-check, set auto-install, open release notes                                               |

Order: S1, S2, then the pages in any order. S5 needs the speed editor of S4; S6 needs it too.

## 12. (c) State and logic that move into the Kotlin presenter

### 12.1 UI-local state

| Compose state                                                                               | Where                                   | Rule the presenter must keep                                                                                                                                                      |
|---------------------------------------------------------------------------------------------|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `openSection`                                                                               | `FdmSettingsPanel.kt:198`               | null at start; wide layout shows the first visible section when null or no longer visible; narrow layout shows the list when null; reset when the UI is rebuilt (language change) |
| wide / narrow                                                                               | `FdmSettingsPanel.kt:251`               | panel width >= 720; can be decided in Slint from the panel width                                                                                                                  |
| page scroll offset                                                                          | `FdmSettingsPanel.kt:283`               | one scroll position shared by all pages in the wide layout (Slint `Flickable` state)                                                                                              |
| language menu `open`                                                                        | `SettingsPages.kt:763`                  | pure UI, may stay in Slint                                                                                                                                                        |
| `showDirectoryPicker`, `defaultDownloads`                                                   | `SettingsPages.kt:266-267`              | dialog visibility; default Downloads folder read once                                                                                                                             |
| preset `currentUnit`, `text`                                                                | `SpeedPresetRow.kt:50-51`               | initialised once from the stored bytes + unit; afterwards the text is what the user typed; the short label follows the stored bytes                                               |
| time field `text`, `isPm`                                                                   | `SettingsPages.kt:539-540`              | initialised from the window; invalid text stays in the field and sends nothing                                                                                                    |
| traffic `isCustom`, `customUnit`, `customText`                                              | `TrafficLimitSelector.kt:54-89`         | see 6.2, including the re-initialisation of `isCustom` on every value change                                                                                                      |
| proxy draft `kind`, `host`, `port`, `user`, `password`, `justSaved`                         | `NetworkSettingsUi.kt:60-65`            | draft reset whenever the saved proxy changes; `justSaved` cleared by any edit                                                                                                     |
| new login `site`, `user`, `password`                                                        | `NetworkSettingsUi.kt:165-167`          | cleared after Add                                                                                                                                                                 |
| startup `enabled`, `busy`, `startHidden`, `error`                                           | `StartupUi.kt:57-60`                    | `enabled` queried asynchronously when the page opens, `busy` true until then                                                                                                      |
| browser page `missing`, `steps`, `failure`, `fromFile`, `busy`, `storeVersion`, `githubXpi` | `BrowserInstallSection.kt:66-70, 96-97` | one dialog at a time in practice; the store lookups run once per page visit for browsers that need them                                                                           |
| diagnostics `savedTo`                                                                       | `SettingsPages.kt:83`                   | Android only                                                                                                                                                                      |

Proposed presenter outputs (one flat state object per page so the `.slint` files stay free of
logic): nav entries `{id, glyph, title, hint, hintTone, selected}`; page header `{title, blurb}`;
per page the row view models listed in section 11. All texts arrive already localized; tones are an
enum (default, green `#10B981`, amber `#F59E0B`, error).

### 12.2 Logic to reproduce

All helpers of 9.3, the nav summary rules of 4.2, the section visibility rules of 4.2, the browser
row rules of 5.3, the proxy `draft` / `invalid` / `changed` rules and the save payload of 5.5, the
media button and JS-runtime label rules of 5.6, the updates row rules of 5.8, and the input filters:
digits and `.` (speed), digits and `:` with at most 5 characters (time), digits with at most 5
characters (port).

### 12.3 Existing behaviours to decide on (they are in the Compose app today)

1. Speed schedule rows use `key(index)`: after removing a window, the rows after it keep the text
   typed for the removed position until the page is left. A presenter that rebuilds the row texts
   from `SpeedSchedule.windows` after add / remove avoids this.
2. `TrafficLimitSelector`: `isCustom` is recomputed on every value change, so typing a custom value
   that equals a preset (for example `1` MB/s on the way to `15`) closes the custom editor and
   selects the preset chip.
3. `TrafficLimitSelector` hint uses `num.toLong()`: `2.5 MB/s` shows `= 2 MB/s`.
4. Clearing a speed preset field sends 0 bytes for that preset.
5. The destination path is saved on every keystroke with no validation.
6. `SegmentedPicker` for the speed unit and queue size sends the event even when the selected option
   is clicked again.
7. Unpair / Unblock / Revoke / Remove act without a confirmation.
8. The Store branch of `StartupSettingsCard` is unreachable from the General page.

## 13. Platform dependencies the native build must provide for these pages

- `getPlatform().isJvm` is the "desktop" switch for: showing Media tools, the torrent Engine group,
  the desktop browser summary, the desktop branch of the Browser page, and `AppScrollbar`. The
  mingwX64 `Platform` must make these paths behave as desktop.
- Actuals behind the pages: `StartupLauncher` (JVM uses the AutoLaunch library: registry Run key),
  `SystemPower`, `DiagnosticsLog` (`canExport` false), `BrowserIntegration` / `DesktopBrowsers`,
  `PathResolver.openDirectoryInExplorer` / `getDownloadsDirectory` / `isOpenFolderSupported`,
  `PlatformUpdater` (`canInstallInApp`, `updatesFromStore`, `platformKey`),
  `WindowsStorePackage.isInstalled` (JVM detects it from the `jpackage.app-path` system property
  containing `\WindowsApps\`), URL opening (`https:` and `ms-settings:`), clipboard write.
