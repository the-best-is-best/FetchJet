# 05 - UI porting spec: task list and task details

Scope: the Compose files below, to be re-implemented in Slint (markup renders state computed by a
Kotlin presenter; no business logic in `.slint`).

| File (under `sharedUI/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`) | Lines | Content                                                                                                   |
|-----------------------------------------------------------------------------|-------|-----------------------------------------------------------------------------------------------------------|
| `TaskList.kt`                                                               | 664   | list area, banners, toolbar, selection bar, section headers, status bar, state grouping                   |
| `ui/TaskRows.kt`                                                            | 542   | one row (wide + compact), kind square, thin bar, quick action, priority chip, column header, `SideFilter` |
| `TaskDetails.kt`                                                            | 938   | details pane and its whole body, chips, buttons, post-process line, formatters                            |
| `ui/PiecesBar.kt`                                                           | 295   | IDM-style piece map + connection cards                                                                    |
| `ui/QueueUi.kt`                                                             | 47    | Settings card "downloads at the same time"                                                                |
| `App.kt` (only placement)                                                   | 887   | where list / details / status bar sit, breakpoints, details sheet                                         |

Related code that these files call and that was read for this spec: `HomeDialogs.kt:19-47`
(`RestartOnPauseDialog`), `ui/AppScrollbar.kt`, `ui/WhenDoneUi.kt:66-91` (`WhenDoneBanner`),
`ui/SettingsKit.kt` (`SettingsGroup`, `SettingRow`, `SegmentedPicker`), `ui/MissingFileDialog.kt`,
`utils/Formatters.kt`, `theme/Color.kt`, `theme/Theme.kt`, and in sharedLogic
`domain/model/DownloadTask.kt`, `presentation/dashboard/DashboardContract.kt`,
`DashboardFilters.kt`, `DashboardTaskEvents.kt`, `core/util/LocalClock.kt`,
`core/i18n/start/StepText.kt`.

Material 3 defaults quoted here were read from the sources jar the project really uses
(`org.jetbrains.compose.material3:material3-desktop:1.12.0-alpha03`, Compose Multiplatform 1.12.2).
They differ from older M3 write-ups in a few places (marked **[M3 1.12]**).

Nothing in this document was checked against the running app (the app was not started). Every
value is read from source. Items that can only be settled by looking at the running JVM app are
listed in section 13.

---

## 1. Placement, breakpoints, window size

### 1.1 Window

- `desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt:769`:
  `rememberWindowState(width = 1100.dp, height = 750.dp)`. The `Window(...)` call (line 862) sets
  no minimum size and nothing else in `main.kt` calls `minimumSize` / `setMinimumSize`. **The
  desktop app enforces no minimum window size**; the window can be shrunk until the phone layout
  shows.
- Density: Compose Desktop uses the OS scale factor, 1 dp = 1 Slint logical `px`. Font scale is 1,
  so 1 sp = 1 dp.

### 1.2 Breakpoints (`App.kt:191-200`, on the whole window width `W`)

| Condition         | Name        | Effect                                                                                                                                                                      |
|-------------------|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `W < 680`         | `isCompact` | phone layout (header, bottom nav, FAB). `TaskListArea(showToolbar = false)`, own search box, groups only on the ALL tab. Out of scope, noted where it changes a composable. |
| `680 <= W < 1100` | `isMedium`  | icon rail `FjRail` (84 dp wide) instead of the sidebar                                                                                                                      |
| `W >= 1100`       |             | full sidebar `FjSidebar` (232 dp wide)                                                                                                                                      |
| `W >= 1280`       | `sidePanel` | selected task's details in a fixed 340 dp panel on the right                                                                                                                |
| `680 <= W < 1280` | "tablet"    | details open as a bottom sheet in a `Dialog` over the list                                                                                                                  |

The default window (1100 x 750) therefore shows: full sidebar + list + **details as a bottom
sheet**, not the side panel. The side panel needs a window of 1280 dp or wider.

### 1.3 The 720 dp rule is about the row, not the window

`TaskRow` and `TaskRowsHeader` use `BoxWithConstraints` on **their own width**
(`TaskRows.kt:269-270`,
`445-446`): `wide = maxWidth >= 720.dp`. The list width is:

- sidebar layout, no side panel: `min(W - 232, 1400) - 32`
- rail layout: `min(W - 84, 1400) - 32`
- sidebar + side panel (W >= 1280, a task selected): `min(W - 232 - 340, 1400) - 32`

Resulting window widths:

| Layout                    | Wide rows when                                                         |
|---------------------------|------------------------------------------------------------------------|
| rail (680..1099)          | `W >= 836` (680..835 shows compact card rows)                          |
| sidebar (>= 1100)         | always (1100 - 264 = 836)                                              |
| sidebar + side panel open | `W >= 1324` (**1280..1323 with details open shows compact card rows**) |

The content column is capped at 1400 dp and centred (`App.kt:331-337`,
`contentAlignment = Alignment.TopCenter`, `widthIn(max = 1400.dp)`).

### 1.4 Layout tree of the wide layout (`App.kt:284-441`)

```
Row (fill)
  FjSidebar 232dp | FjRail 84dp                       (other spec)
  Box weight(1) fillMaxHeight, contentAlignment TopCenter
    Column fillMaxSize, widthIn(max 1400), padding horizontal 16 / vertical 10
      FjTopBar (title = sideFilter.label, search box, speed, theme, Add)   (other spec)
      if Settings tab: SettingsContentView                                 (other spec)
      else:
        Box weight(1) fillMaxWidth
          TaskListArea(tasks = state.filteredTasks.filter { sideFilter.matches(it) },
                       groupByState = sideFilter in {ALL, MEDIA, FILES, TORRENTS},
                       externalSearch = searchQuery, showToolbar = true (default), ...)
        QueueStatusBar(tasks = state.allTasks, state.globalDownloadSpeedLimitBytes, state.speedDisplayUnit)
  if (W >= 1280 && selectedTask != null && tab != SETTINGS):
    Surface color = surface, width 340, fillMaxHeight
      Row
        Box width 1, fillMaxHeight, background outlineVariant alpha 0.4
        TaskDetailsPane(task = selectedTask, fullScreen = false, onClose = { selectedTaskId = null })
```

State owned by `App` (plain `remember`, not saved): `selectedTaskId: String?` (`:131`),
`sideFilter: SideFilter = ALL` (`:133`), `searchQuery: String = ""` (`:134`).
`selectedTask = state.allTasks.firstOrNull { it.id == selectedTaskId }` (`:139`): the details
follow the task even when the current filter / search hides its row, and disappear by themselves
when the task leaves `allTasks` (removed). On the Settings tab the details are not shown; the
selection survives the visit.

### 1.5 Details as a bottom sheet (680 <= W < 1280, `App.kt:445-550`)

```
Dialog(onDismissRequest = { selectedTaskId = null }, usePlatformDefaultWidth = false)
  Box fillMaxSize, clickable without indication -> selectedTaskId = null, contentAlignment BottomCenter
    Surface color = background, shape RoundedCornerShape(topStart 22, topEnd 22)
            modifier: collapsed  = fillMaxWidth, widthIn(max 820), heightIn(max = 0.9 * window height)
                      expanded   = fillMaxWidth, widthIn(max 820), fillMaxHeight(0.94)
            clickable without indication, swallows the tap
      Column
        handle zone: Box fillMaxWidth, padding top 6 / bottom 2, content centred
          Box padding vertical 6, size 44 x 5, RoundedCornerShape(3), onSurface alpha 0.18
        TaskDetailsPane(fullScreen = false, fillHeight = sheetExpanded, onClose = close)
    SnackbarHost align BottomCenter, padding bottom 24
```

- Compose Desktop draws a `Dialog` as a layer inside the same window with a scrim of black at
  alpha 0.6 (`Dialog.skiko.kt`: `DefaultScrimOpacity = 0.6f`).
- `sheetExpanded` is `remember(selectedTaskId) { false }`: every newly selected task starts
  collapsed (as tall as its content, at most 90 % of the window height; content scrolls).
- Handle zone: tap toggles `sheetExpanded`; vertical drag with total `dy < -40 px` expands;
  `dy > 40 px` collapses when expanded, otherwise closes.
- Tap outside the sheet and Escape close it (both set `selectedTaskId = null`).
- Handle zone height: 6 + (6 + 5 + 6) + 2 = 25 dp.
- Phone (`W < 680`): the same dialog, `Surface` fills the window, no rounded corners, no handle,
  `TaskDetailsPane(fullScreen = true)`.

---

## 2. Global rules the look depends on

### 2.1 Colour tokens (`theme/Color.kt`, `theme/Theme.kt`)

| Token                                                    | Dark      | Light     |
|----------------------------------------------------------|-----------|-----------|
| primary                                                  | `#00E5FF` | `#0284C7` |
| onPrimary                                                | `#031625` | `#FFFFFF` |
| primaryContainer                                         | `#0C3D60` | `#E0F2FE` |
| onPrimaryContainer                                       | `#C7F1FF` | `#0369A1` |
| secondary                                                | `#818CF8` | `#4F46E5` |
| secondaryContainer                                       | `#2C2F63` | `#EEF2FF` |
| tertiary                                                 | `#F43F5E` | `#E11D48` |
| background                                               | `#080D1A` | `#F8FAFC` |
| onBackground                                             | `#F8FAFC` | `#0F172A` |
| surface                                                  | `#0F1728` | `#FFFFFF` |
| onSurface                                                | `#F1F5F9` | `#0F172A` |
| surfaceVariant                                           | `#172238` | `#F1F5F9` |
| onSurfaceVariant                                         | `#94A3B8` | `#475569` |
| outline                                                  | `#334155` | `#94A3B8` |
| outlineVariant                                           | `#1E2B42` | `#CBD5E1` |
| error                                                    | `#F87171` | `#DC2626` |
| onError                                                  | `#450A0A` | `#FFFFFF` |
| surfaceContainer (not set by the theme: M3 baseline)     | `#211F26` | `#F3EDF7` |
| surfaceContainerHigh (not set by the theme: M3 baseline) | `#2B2930` | `#ECE6F0` |

Fixed colours (same in both themes):

| Name                           | Hex                   | Used by                                                                       |
|--------------------------------|-----------------------|-------------------------------------------------------------------------------|
| `StatusCompleted`              | `#10B981`             | "Resumable" chip, speed text when completed, "done" pill                      |
| `StatusPaused`                 | `#F59E0B`             | "No resume" chip, paused speed text, "Paused & failed" header, waiting pieces |
| `EngineBitTorrent`             | `#10B981`             | "Torrent" chip                                                                |
| `EngineMediaStream`            | `#818CF8`             | "Media" chip                                                                  |
| row bar completed              | `#34D399`             | `ThinBar` of a completed row                                                  |
| row bar paused / waiting       | `#FBBF24`             | `ThinBar` of a paused or queued row                                           |
| piece active end / active pill | `#7DF9FF`             | `PiecesBar`                                                                   |
| priority high / low            | `#EF4444` / `#3B82F6` | `PriorityChip`                                                                |
| checksum verified              | `#2E7D32`             | details                                                                       |
| kind colours                   | see 4.2               | `KindSquare`                                                                  |

`desktopNative/ui/theme.slint` today lacks: `secondary-container`, `on-background`, `on-error`,
`surface-container`, `surface-container-high`, `status-completed (#10B981)`, `engine-torrent`,
`engine-media`. (That file belongs to another task; this is only a list of what this area needs.)

### 2.2 Typography roles used ([M3 1.12] `TypeScaleTokens`)

Font: `FontFamily.Default` = Segoe UI on Windows. All roles use line-height style
`Alignment.Center, Trim.None`: **a line's box is exactly the line height**.

| Role                                                         | Size | Line height | Weight | Letter spacing |
|--------------------------------------------------------------|------|-------------|--------|----------------|
| bodyLarge (the default `LocalTextStyle`)                     | 16   | 24          | 400    | 0.5            |
| bodyMedium                                                   | 14   | 20          | 400    | 0.2            |
| bodySmall                                                    | 12   | 16          | 400    | 0.4            |
| titleMedium                                                  | 16   | 24          | 500    | 0.2            |
| labelLarge (default inside Button / TextButton / menu items) | 14   | 20          | 500    | 0.1            |
| labelMedium                                                  | 12   | 16          | 500    | 0.5            |
| labelSmall                                                   | 11   | 16          | 500    | 0.5            |
| headlineSmall (AlertDialog title)                            | 24   | 32          | 400    | 0              |

Weights named in code: Medium 500, SemiBold 600, Bold 700, ExtraBold 800.

**Inherited line height rule.** A `Text(..., fontSize = N.sp)` without `style =` keeps the line
height and letter spacing of the ambient style. Outside buttons that is bodyLarge, so the text box
is **24 dp high with 0.5 sp letter spacing** whatever the font size; inside a
Button / OutlinedButton / TextButton / DropdownMenuItem it is labelLarge (**20 dp, 0.1 sp**). All
heights in this document are computed with this rule (for example a chip with 11 sp text and
3 dp vertical padding is 30 dp high, not 19). A font larger than its line box (the 30 sp
percentage, the 48 sp emoji) overflows the box above and below; it is not clipped.

### 2.3 Material 3 component defaults used here ([M3 1.12])

- **Minimum interactive size**: 48 dp (`LocalMinimumInteractiveComponentSize`), applied by
  `Checkbox`, `IconButton`, `Button`, and `Surface(onClick = ...)`. A fixed size given by the caller
  (`Modifier.size(24.dp)`, `Modifier.height(34.dp)`) wins.
  `isPrecisionPointerComponentSizingEnabled`
  is `false` (the app does not set it), so desktop does not get the denser 36 dp sizes.
- **Button**: min 58 x 40, shape pill, content padding 24 horizontal / 8 vertical, label style
  labelLarge, container primary, content onPrimary. Disabled: container onSurface alpha 0.1,
  content onSurfaceVariant alpha 0.38. Hover: 1 dp shadow (`Level1`) and the state layer.
- **OutlinedButton**: same size and padding, transparent container, border 1 dp **outlineVariant**
  (disabled: outlineVariant alpha 0.1), content **onSurfaceVariant** (disabled: alpha 0.38). (Older
  M3 used outline / primary.)
- **TextButton**: min 58 x 40, shape pill, content padding 12 horizontal / 8 vertical, content
  primary, disabled onSurfaceVariant alpha 0.38.
- **IconButton**: 40 dp circle in a 48 dp slot, content colour = ambient content colour.
- **Checkbox / TriStateCheckbox**: drawn box 20 x 20 dp (2 dp padding inside a 24 dp slot),
  corner radius 2 dp, stroke 2 dp. Unchecked: transparent fill, border onSurfaceVariant. Checked
  and indeterminate: fill and border primary, mark onPrimary, stroke 2 dp, square cap. Check mark
  path in box units: (0.2, 0.5) -> (0.4, 0.7) -> (0.8, 0.3). Indeterminate: a line (0.2, 0.5) ->
  (0.8, 0.5). State layer: unbounded ripple of radius 20 dp.
- **State layers** (ripple on `clickable`, buttons, rows): content colour at alpha 0.08 hovered,
  0.10 focused, 0.10 pressed.
- **LinearProgressIndicator**: height 4 dp, default width 240 dp, colour primary, track
  secondaryContainer, round stroke caps, 4 dp gap between indicator and track, a 4 dp stop dot in
  primary at the end of the track (determinate only). Indeterminate: two lines, head / tail
  durations 1000 / 1000 ms and 850 / 850 ms, delays 0 / 250 / 650 / 900 ms, one cycle 1750 ms.
- **DropdownMenu**: container surfaceContainer, shape 4 dp, shadow 3 dp, vertical padding 8 dp,
  item min height 48 dp, item width 112..280 dp, item horizontal padding 12 dp, item text
  labelLarge in onSurface, opens with scale 0.8 -> 1 and a fade, at least 48 dp from the window's
  top / bottom edge.
- **AlertDialog**: width 280..560 dp, container surfaceContainerHigh, default shape 28 dp, padding
  24 dp all sides, title headlineSmall in onSurface with 16 dp below, text bodyMedium in
  onSurfaceVariant with 24 dp below, buttons end-aligned with 8 dp between, dismiss button first
  then confirm button; scrim black alpha 0.6; Escape and click outside call `onDismissRequest`.
- **Surface**: content colour = `contentColorFor(color)`: surface -> onSurface, background ->
  onBackground, any colour that is not a scheme colour (for example `primary.copy(alpha = 0.09f)`)
  keeps the ambient content colour.

Ambient content colour: the root `Surface` is `background`, so texts without `color =` in the list
area are **onBackground**; inside an unselected row (`surface`) they are **onSurface**; inside the
side panel (`surface`) onSurface; inside the sheet (`background`) onBackground. In both themes
onBackground and onSurface are equal or within one shade; the port may use `on-surface` for both.

### 2.4 Glyphs and emoji

All icons in this area are text glyphs: `⏸ ▶ ↗ ✕ ← ▼ ▲ ✓ ⬇ ⏳ 📥 📁 ✅ ⚪ ⚡ 🔗 🎵 ⚙️ ⚠️ ❌ 🕑 🗑 🔍`.
Compose (Skia) takes them from the system fallback fonts (Segoe UI Symbol for the monochrome
ones, tinted by the text colour; Segoe UI Emoji for the coloured ones, which ignore the text
colour). Which glyph comes from which font was not verified on screen.

### 2.5 Right-to-left

`App.kt:179-185` sets `LayoutDirection.Rtl` for Arabic and Persian: every Row mirrors (start / end
swap). Sizes and speeds from `Formatters.kt` are wrapped in U+2066 ... U+2069 isolates so
"6.41 MB/s" stays left-to-right. `PiecesBar`'s canvas is not mirrored (pieces always fill left to
right).

### 2.6 What is not there

No tooltips anywhere in these files (`onClickLabel` is accessibility text only). No double-click
handler. No context menu. No drag and drop. No animations except: the piece sheen, the
indeterminate progress bars, the scrollbar fade, the ripple / state layers, the menu open
transition, `animateScrollToItem(0)`.

---

## 3. `TaskList.kt`

### 3.1 `ToolsDownloadBanner(tool: String?, fraction: Float?)` - lines 74-101

Purpose: "Updating media tools" strip above the list. Used by `TaskListArea` (line 193-195) when
`uiState.isDownloadingYtDlp && uiState.toolDownloadName != null`.

```
Surface shape RoundedCornerShape(12), color primary alpha 0.10, fillMaxWidth, padding bottom 8
  Column padding horizontal 14 / vertical 10
    Text homeUpdatingMediaTools(tool.orEmpty(), fraction?.let { (it * 100).toInt() })
         bodySmall, SemiBold, primary
    Spacer 6
    LinearProgressIndicator fillMaxWidth   (determinate when fraction != null, else indeterminate)
```

Height: 10 + 16 + 6 + 4 + 10 = 46 dp, plus 8 dp below. The bar uses all M3 defaults (4 dp high,
gap, stop dot, track secondaryContainer). English text: `Updating media tools · <tool> 42%`, or
`Updating media tools · <tool>…` when the fraction is unknown.

### 3.2 `TaskListArea(...)` - lines 103-363

```kotlin
internal fun TaskListArea(
    tasks: List<DownloadTask>,
    viewModel: DashboardViewModel,
    speedDisplayUnit: String = "MB/s",
    selectedTab: DashboardTab = DashboardTab.ALL,
    sortOption: TaskSortOption = TaskSortOption.DATE_DESC,
    selectedTaskId: String? = null,
    onSelect: (String?) -> Unit = {},
    externalSearch: String? = null,
    groupByState: Boolean = selectedTab == DashboardTab.ALL,
    showToolbar: Boolean = true
)
```

Used by `App.kt:243` (phone) and `App.kt:378` (wide). In the wide layout `tasks` is already sorted
by the view model (`filterTasks`, section 12.4) and filtered by the sidebar; `externalSearch` is
the top bar's text (never null), `showToolbar = true`.

#### UI-local state

| State             | Type / initial                         | Lifetime   | Meaning                                               |
|-------------------|----------------------------------------|------------|-------------------------------------------------------|
| `collapsedGroups` | `mutableStateMapOf<String, Boolean>()` | `remember` | folded section keys (`run`, `wait`, `stop`, `done`)   |
| `localSearch`     | `""`                                   | `remember` | own search box, phone only (`externalSearch == null`) |
| `checkedIds`      | `emptySet<String>()`                   | `remember` | multi-selection                                       |
| `anchorId`        | `null`                                 | `remember` | last row clicked / checked: start of a Shift range    |
| `listFocus`       | `FocusRequester`                       | `remember` | keyboard focus of the list column                     |
| `listState`       | `rememberLazyListState()`              | `remember` | scroll position                                       |
| `desktop`         | `getPlatform().isJvm`                  | constant   | native desktop port: `true`                           |

All of it is lost when the list leaves the composition (opening Settings and coming back resets
folded groups, the selection and the scroll position). Nothing is saved to disk.

#### Derived values

- `searchQuery = externalSearch ?: localSearch`.
- `shownTasks`: `q = searchQuery.trim()`; empty -> all; else tasks where `fileName`, `url` or
  `originalUrl` contains `q`, case-insensitive (lines 127-134).
- `unfinished = shownTasks.filter { status != COMPLETED }` (feeds the toolbar's enabled rules).
- `selectionMode = checkedIds.isNotEmpty()`.
- `LaunchedEffect(allTasks)` (line 141-144): ids no longer in `tasks` (the parameter, **before**
  the search filter) are dropped from `checkedIds`. Rows hidden only by the search stay checked.
- `shownOrder` (line 156-158): grouped -> tasks of the groups that are not folded, in group order;
  otherwise `shownTasks`. Used by Ctrl+A and Shift+click.

#### Layout tree (wide layout)

```
Column fillMaxSize, focusRequester(listFocus), focusable, onKeyEvent(...)
  [ToolsDownloadBanner]            if uiState.isDownloadingYtDlp && uiState.toolDownloadName != null
  [WhenDoneBanner]                 if WhenDone.action != NOTHING         (section 8.3)
  [FetchJetTextField search]       only if externalSearch == null (phone)   (section 3.2.1)
  SelectionBar                     if selectionMode
  else ListToolbar(unfinished)     if shownTasks.isNotEmpty() && showToolbar
  if shownTasks.isEmpty(): empty state, fills the rest, then nothing else
  TaskRowsHeader(allChecked = shownTasks.all { id in checkedIds }, someChecked = selectionMode)
  Box fillMaxSize
    LazyColumn fillMaxSize, spacedBy 6, contentPadding top 4 / bottom 16
               (bottom 88 when showToolbar = false)
      grouped:   per group: SectionHeader item (key "header-" + key), then its rows unless folded
      otherwise: rows
    AppScrollbar(listState) aligned CenterEnd            (section 8.4)
```

Empty state (lines 235-258), centred in the remaining space:

```
Column centred horizontally
  Text "📥" fontSize 48 (line box 24 dp, glyph overflows)
  Spacer 12
  Text titleMedium, onSurfaceVariant:
       searchQuery not blank -> homeNoDownloadsMatch(searchQuery.trim())   "No downloads match "x""
       else                  -> homeNoTasksInView                          "No tasks in this view"
  Text bodySmall, onSurfaceVariant alpha 0.7:
       searchQuery not blank -> homeTryAnotherName            "Try another name"
       else                  -> homeAddDownloadToGetStarted   "Add a download to get started"
```

When the list is empty the toolbar is hidden; the selection bar can still show (checked rows
hidden by the search).

#### Scrolling

`LaunchedEffect(uiState.scrollToTopSignal)`: when the signal is > 0 and changes,
`listState.animateScrollToItem(0)` (a download was just added). Mouse wheel scrolls the list.

#### Keyboard (desktop only, lines 160-191; only while the list column has focus)

The list takes focus when a row is clicked, Ctrl/Shift-clicked or its checkbox changes.

| Key (key down)               | Action                                                                                                                                                 | Consumed    |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| Ctrl+A or Cmd+A              | `checkedIds = shownOrder ids` (rows of folded groups are not included)                                                                                 | yes         |
| Escape, when `selectionMode` | `checkedIds = empty`                                                                                                                                   | yes         |
| Delete, or Cmd+Backspace     | `selectionMode` -> `OnRemoveSelectedClicked(checkedIds.toList())`; else if `selectedTaskId != null` -> `OnRemoveClicked(selectedTaskId)`; else nothing | yes, always |
| anything else                |                                                                                                                                                        | no          |

Android back (`NavigationBackHandler`, lines 149-153) clears the selection; no desktop meaning.

#### Row callbacks (lines 280-336)

| Row callback                                                       | Code                                                                                                                                                                                                                  |
|--------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `onClick` (plain click, not in selection mode)                     | `anchorId = id`; focus list; `onSelect(id)` (opens / switches details; clicking the selected row again changes nothing)                                                                                               |
| `onCheckedChange(on)` (checkbox, or plain click in selection mode) | `anchorId = id`; focus list; add / remove id                                                                                                                                                                          |
| `onCtrlClick` (desktop)                                            | `anchorId = id`; focus list; toggle id                                                                                                                                                                                |
| `onShiftClick` (desktop)                                           | focus list; `ids = shownOrder ids`; `from = indexOf(anchorId ?: selectedTaskId)` or 0 when not found; `to = indexOf(id)`; if `to >= 0`: **add** `ids[min..max]` to `checkedIds` (never removes; `anchorId` unchanged) |
| `onLongClick`                                                      | add id (mouse press-and-hold also triggers it on desktop)                                                                                                                                                             |
| `onQuickAction(PAUSE)`                                             | `task.canResume() == false` -> `onSelect(id)` (the warning lives in the details); else `OnPauseClicked(id)`                                                                                                           |
| `onQuickAction(RESUME / START_NOW)`                                | `OnResumeClicked(id)`                                                                                                                                                                                                 |
| `onQuickAction(OPEN)`                                              | `dest = destinationPath.trimEnd('/', '\\')`; `sep = "\\"` if dest contains `\` else `/`; `openDownloadedFile(decodeUnicodeText("$dest$sep$fileName").trim(), id)` (section 8.2)                                       |
| `onPriorityChanged(p)`                                             | `OnTaskPriorityChanged(id, p)`                                                                                                                                                                                        |

Header / selection bar "check all": `checkedIds = if (all) shownTasks ids else empty` (includes
rows of folded groups, unlike Ctrl+A).

Section header toggle: `collapsedGroups[key] = !collapsed`.

#### View-model state read

`viewModel.uiState`: `isDownloadingYtDlp`, `toolDownloadName`, `toolDownloadFraction`,
`scrollToTopSignal`. Parameters passed from `App`: `filteredTasks`, `speedDisplayUnit`,
`selectedTab`, `sortOption`.

#### 3.2.1 Own search box (phone only, lines 197-213)

Shown when `tasks.isNotEmpty() && externalSearch == null`: `FetchJetTextField` height 44,
fillMaxWidth, padding bottom 8, placeholder `homeSearchDownloadsByNamePlaceholder`
("Search downloads by name…"), leading `🔍` 14 sp, trailing `FieldIconButton("✕")` clearing the
text when not empty. Not shown at 680 dp and wider (the top bar has the search).

### 3.3
`SectionHeader(emoji, title, count, color, collapsed = false, onToggle: (() -> Unit)? = null)` - lines 365-420

Used by `TaskListArea` (line 342) as a lazy item, always with `onToggle`.

```
Row fillMaxWidth, clip RoundedCornerShape(8), clickable { onToggle() }, padding top 6 / bottom 2,
    vertically centred
  Text "▶" if collapsed else "▼", fontSize 11, color = group colour, padding end 6
  Text emoji, fontSize 14 (ambient colour)
  Spacer 6
  Text title, labelLarge, Bold, group colour
  Spacer 8
  Surface CircleShape, color = group colour alpha 0.15
    Text count, labelSmall, Bold, group colour, padding horizontal 8 / vertical 2
  Spacer 10
  Box weight(1), height 1, background outlineVariant alpha 0.5
```

Height: 6 + 24 + 2 = 32 dp (the count pill is 20 dp high). The whole row is the click target, with
the hover / press state layer clipped to 8 dp corners.

Groups (`taskStateGroups`, lines 483-516), in this order, empty groups dropped:

| key    | emoji | title                                     | members                                                  | colour           |
|--------|-------|-------------------------------------------|----------------------------------------------------------|------------------|
| `run`  | `⬇`  | `homeGroupDownloading` "Downloading"      | `SideFilter.DOWNLOADING.matches`                         | primary          |
| `wait` | `⏳`  | `homeGroupWaiting` "Waiting"              | `waitingInQueue`                                         | onSurfaceVariant |
| `stop` | `⏸`   | `homeGroupPausedFailed` "Paused & failed" | everything not in the other three (paused, failed, idle) | `#F59E0B`        |
| `done` | `✓`  | `homeGroupCompleted` "Completed"          | `status == COMPLETED`                                    | tertiary         |

### 3.4 `QueueStatusBar(tasks, globalLimitBytes: Long, speedDisplayUnit: String)` - lines 422-472

Bottom line of the wide layout (`App.kt:400`), `tasks = state.allTasks` (not filtered).

Reads directly: `QueueManagerRepository.getInstance().maxConcurrentDownloads` (StateFlow<Int>) and
`core.update.APP_VERSION`.

```
Row fillMaxWidth, padding top 6, spacedBy 18, vertically centred; all labelMedium, onSurfaceVariant
  Text homeDownloadingWaitingSummary(running, waiting)   maxLines 1   "2 downloading · 1 waiting"
  Text homeQueueStatus(maxAtOnce)                        maxLines 1   "Queue: 3 at a time" / "Queue: no limit" (max <= 0)
  Text homeSpeedLimitStatus(if (limit > 0) formatSpeed(limit, unit) else null)   "Speed limit: 5.0 MB/s" / "Speed limit: off"
  Spacer weight(1)
  Text "v" + APP_VERSION                                 maxLines 1, softWrap false   "v1.4.0"
```

- `running = tasks.count { !waitingInQueue && (status == DOWNLOADING || status == CONNECTING) }`
- `waiting = tasks.count { waitingInQueue }`

Height 16 + 6 = 22 dp. No interaction.

### 3.5 `ListToolbar(tasks, sortOption, onPauseAll, onResumeAll, onSort)` - lines 526-595

Above the list when no row is checked. `tasks` = `unfinished` (shown, not completed).

```
Row fillMaxWidth, padding bottom 8, spacedBy 8, vertically centred
  OutlinedButton height 34, RoundedCornerShape(10), contentPadding horizontal 12
      enabled = anyPausable(tasks)
      Text "⏸  " + homePauseAll, fontSize 12, SemiBold            "⏸  Pause all"
  OutlinedButton same, enabled = anyResumable(tasks)
      Text "▶  " + homeResumeAll, fontSize 12, SemiBold            "▶  Resume all"
  Spacer weight(1)
  Box
    TextButton height 34, contentPadding horizontal 10
      Text homeSortBy(sortOption.label) + "  ▾", fontSize 12, SemiBold     "Sort: Newest first  ▾"
    DropdownMenu(expanded = open)
      for opt in TaskSortOption.entries:
        DropdownMenuItem text = ("✓  " if opt == sortOption else 5 spaces) + opt.label, fontSize 13
```

- Two spaces between glyph and word in the button labels.
- Buttons use the [M3 1.12] defaults of 2.3: outlined text onSurfaceVariant, border
  outlineVariant; disabled text alpha 0.38, border alpha 0.1. TextButton text primary, pill shape.
- `anyPausable(tasks)`: any `DOWNLOADING`, `CONNECTING` or `QUEUED`.
  `anyResumable(tasks)`: any `PAUSED` or `ERROR`. (Also used by the phone header,
  `HomeNavigation.kt:637-642`.)
- Events: `OnPauseAllClicked`, `OnResumeAllClicked`, `OnSortChanged(opt)`. **Pause all / Resume
  all act on every task** (`queueRepository.pauseAll()` / `resumeAll()`), while the enabled rule
  looks only at the rows shown.
- Local state: `open` (menu expanded). Clicking an item closes the menu, then `onSort(opt)`.
- Sort options in order: `DATE_DESC` `sysSortNewestFirst` "Newest first", `DATE_ASC`
  `sysSortOldestFirst` "Oldest first", `SIZE_DESC` `sysSortLargestFirst` "Largest first",
  `SIZE_ASC` `sysSortSmallestFirst` "Smallest first", `NAME_ASC` `sysSortNameAz` "Name A–Z".
- The menu opens under the button, start edges aligned, and moves to stay inside the window.
- Row height 34 + 8 = 42 dp.

### 3.6 `SelectionBar(count, allChecked, onCheckAll, onDelete, onExport, onClose)` - lines 597-664

Replaces the toolbar while `checkedIds` is not empty.

```
Row fillMaxWidth, padding bottom 8, spacedBy 8, vertically centred
  IconButton size 34 -> onClose           Text "✕" fontSize 14
  Text homeSelectedCount(count), fontSize 13, SemiBold                    "3 selected"
  TextButton height 34, contentPadding horizontal 10 -> onCheckAll(!allChecked)
      Text selectNone if allChecked else selectAll, fontSize 12, SemiBold   "Select none" / "Select all"
  Spacer weight(1)
  OutlinedButton height 34, RoundedCornerShape(10), contentPadding horizontal 12 -> onExport
      Text homeExport, fontSize 12, SemiBold                              "Export"
  Button height 34, RoundedCornerShape(10), contentPadding horizontal 12, containerColor error -> onDelete
      Text "🗑  " + delete, fontSize 12, SemiBold, onError                 "🗑  Delete"
```

- `count = checkedIds.size` (can include rows hidden by the search).
- `allChecked = shownTasks.isNotEmpty() && shownTasks.all { id in checkedIds }`.
- Events: `OnRemoveSelectedClicked(checkedIds.toList())` (opens the shared
  `RemoveConfirmationDialog` through `removeConfirmationTaskIds`),
  `OnExportLinksClicked(checkedIds.toList())` (writes `FetchJet links <stamp>.txt` into Downloads
  and shows a toast). Neither clears the selection by itself; removed tasks drop out through the
  pruning effect.

---

## 4. `ui/TaskRows.kt`

### 4.1 `TaskRow` - lines 237-399

```kotlin
fun TaskRow(
    task: DownloadTask,
    selected: Boolean,
    speedDisplayUnit: String,
    onClick: () -> Unit,
    onQuickAction: (RowAction) -> Unit,
    checked: Boolean = false,
    selectionMode: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    onLongClick: () -> Unit = {},
    onCtrlClick: (() -> Unit)? = null,
    onShiftClick: (() -> Unit)? = null,
    onPriorityChanged: (Int) -> Unit = {}
)
```

Used only by `TaskListArea`.

Computed once per row:

-
`name = decodeUnicodeText(task.fileName.ifBlank { task.url.substringBefore('?').substringAfterLast('/') }).trim()`
- `tag = taskKindTag(task)`, `action = rowActionFor(task)`, `stateLine = taskStateLine(task, unit)`
- container: `selected` -> primary alpha 0.09, else surface
- border 1 dp: `selected` -> primary alpha 0.45, else outlineVariant alpha 0.35
- `stateColor`: `ERROR` -> error, else onSurfaceVariant
- shape `RoundedCornerShape(12)`

Click handling on the whole row (`combinedClickable`, lines 275-287), keyboard modifiers read from
`LocalWindowInfo.keyboardModifiers` at click time:

1. Ctrl or Meta held and `onCtrlClick != null` -> `onCtrlClick()`
2. else Shift held and `onShiftClick != null` -> `onShiftClick()`
3. else `selectionMode` -> `onCheckedChange(!checked)`
4. else `onClick()`

Long press -> `onLongClick()`. Hover / press show the state layer clipped to the 12 dp shape. The
checkbox, the priority chip and the quick action handle their own clicks (the row click does not
fire).

#### Wide row (row width >= 720 dp), lines 289-353

```
Surface shape 12, container, border, fillMaxWidth
  Row padding horizontal 12 / vertical 9, vertically centred, spacedBy 12
    Checkbox checked, size 24                                    always visible
    KindSquare(tag, 34)
    Column weight(1)
      Text name       fontSize 13, SemiBold, maxLines 1, ellipsis
      Text stateLine  fontSize 11, stateColor, maxLines 1, ellipsis, padding top 2
    Text sizeText(task)   fontSize 12, onSurfaceVariant, width 76, maxLines 1
    Column width 140
      ThinBar(task.progress, barColor(task))                     5 dp high
      Text percent    fontSize 11, onSurfaceVariant, padding top 3
    Text speed        fontSize 12, SemiBold, width 84, maxLines 1
    Text eta          fontSize 12, onSurfaceVariant, width 64, maxLines 1
    [PriorityChip]    if status != COMPLETED
    QuickActionButton(action, 34)
```

- percent: `COMPLETED` -> `"100%"`, else `"${(progress.coerceIn(0f, 1f) * 100).toInt()}%"`.
- speed: `status == DOWNLOADING && downloadSpeedBytesPerSec > 0` ->
  `formatSpeed(downloadSpeedBytesPerSec, unit)`, else `"—"` (em dash).
- eta: `task.etaSeconds()?.let { etaText(it) } ?: "—"`.
- **Row height 68 dp** (9 + 24 + 2 + 24 + 9); pitch 74 dp with the list's 6 dp spacing.
- Column widths after the name: 76, 140, 84, 64, [chip], 34, each preceded by 12 dp.
- The priority chip's slot is at least 48 dp wide (minimum interactive size) and wider for longer
  labels; rows with a chip have their fixed columns shifted left by (chip slot + 12) compared with
  completed rows **and with the column header**, which reserves no slot for the chip.

#### Compact row (row width < 720 dp), lines 354-396

```
Surface shape 12, container, border, fillMaxWidth
  Column padding 12
    Row vertically centred
      [Checkbox size 24, Spacer 12]          only if selectionMode
      KindSquare(tag, 40)
      Spacer 12
      Column weight(1)
        Text name       fontSize 14, SemiBold, maxLines 1, ellipsis
        Text stateLine  fontSize 12, stateColor, maxLines 1, ellipsis, padding top 3
      [Spacer 8, PriorityChip]               if status != COMPLETED
      Spacer 8
      [QuickActionButton(action, 44)]        if !selectionMode
    [Spacer 10, ThinBar(progress, barColor)] if status != COMPLETED
```

Height: 90 dp unfinished (12 + 51 + 10 + 5 + 12), 75 dp completed. No size / speed / eta columns
(the state line carries speed and time left). The column header is hidden.

### 4.2 `KindSquare(tag: String, size: Int)` - lines 165-181 (private)

`Box` of `size` dp, `RoundedCornerShape((size / 4).dp)` (integer division: 34 -> 8, 40 -> 10),
background onSurface alpha 0.06, centred `Text(tag.take(4))` 9 sp ExtraBold in `kindColor(tag)`.

`taskKindTag(task)` (lines 62-72), `ext = fileName.substringAfterLast('.', "").lowercase()`, first
match wins:

1. `engineType == BITTORRENT` -> `"MAG"`
2. `engineType == MEDIA_STREAM` -> `"AUD"` if `ext` in `{mp3, m4a, aac, flac, wav, ogg, opus}` else
   `"VID"`
3. `ext` in that audio set -> `"AUD"`
4. `ext` in `{mp4, mkv, webm, mov, avi}` -> `"VID"`
5. `ext.length in 2..4` and all letters / digits -> `ext.uppercase()`
6. else `"FILE"`

`kindColor(tag)` (lines 74-82), same in light and dark:

| Tag                 | Colour    |
|---------------------|-----------|
| `VID`               | `#C4B5FD` |
| `AUD`               | `#6EE7B7` |
| `MAG`               | `#F9A8D4` |
| `ZIP`, `RAR`, `7Z`  | `#FCD34D` |
| `ISO`, `IMG`, `DMG` | `#93C5FD` |
| `EXE`, `MSI`, `APK` | `#FDBA74` |
| anything else       | `#B7C0D8` |

### 4.3 `ThinBar(fraction: Float, color: Color, height: Int = 5)` - lines 183-200 (private)

Track: fillMaxWidth, `height` dp, `RoundedCornerShape(height)` (pill), onSurface alpha 0.08. Fill:
`fillMaxWidth(fraction.coerceIn(0f, 1f))`, full height, same pill shape, `color`. No animation.

`barColor(task)` (lines 157-163): `ERROR` -> error; `COMPLETED` -> `#34D399`; `PAUSED` or
`waitingInQueue` -> `#FBBF24`; else primary.

### 4.4 `QuickActionButton(action: RowAction, sizeDp: Int, onClick)` - lines 202-231 (private)

`NONE` -> `Spacer(width = sizeDp)`. Otherwise a circle of `sizeDp`: transparent fill, border 1 dp
outlineVariant alpha 0.7, clickable (state layer clipped to the circle), centred glyph 13 sp in
onSurface.

| Action      | Glyph | Accessibility label        |
|-------------|-------|----------------------------|
| `PAUSE`     | `⏸`   | `pause` "Pause"            |
| `RESUME`    | `▶`   | `resume` "Resume"          |
| `START_NOW` | `▶`   | `homeStartNow` "Start now" |
| `OPEN`      | `↗`   | `open` "Open"              |

`rowActionFor(task)` (lines 147-155), first match wins: `waitingInQueue` -> `START_NOW`;
`DOWNLOADING`, `CONNECTING`, `QUEUED` -> `PAUSE`; `PAUSED`, `ERROR` -> `RESUME`; `COMPLETED` ->
`OPEN`; else (`IDLE`) -> `NONE`.

### 4.5 `PriorityChip(priority: Int, onCycle)` - lines 409-436 (private)

```
Surface(onClick = onCycle) shape RoundedCornerShape(6), color = c alpha 0.15, border 1 dp c alpha 0.4
  Text label, fontSize 10, SemiBold, c, maxLines 1, padding horizontal 7 / vertical 3
```

| priority | label                     | c                |
|----------|---------------------------|------------------|
| `> 0`    | `priorityHigh` "High"     | `#EF4444`        |
| `< 0`    | `priorityLow` "Low"       | `#3B82F6`        |
| `0`      | `priorityNormal` "Normal" | onSurfaceVariant |

Visible chip: 30 dp high (3 + 24 + 3), text width + 14 wide, centred in a slot of at least
48 x 48 dp. Click cycles `nextPriority` (lines 402-406): `> 0` -> `-1`, `< 0` -> `0`, `0` -> `1`
(Normal -> High -> Low -> Normal), sent as `OnTaskPriorityChanged(task.id, next)`. Shown on every
row whose status is not `COMPLETED`.

### 4.6
`TaskRowsHeader(allChecked = false, someChecked = false, onCheckAll: (Boolean) -> Unit = {})` - lines 439-502

Renders nothing when its width is below 720 dp.

```
Row fillMaxWidth, padding start 12 / end 12 / bottom 4, spacedBy 12, vertically centred
    all texts labelSmall, Bold, onSurfaceVariant
  TriStateCheckbox size 24: On if allChecked, Indeterminate if someChecked, else Off
                            onClick -> onCheckAll(!allChecked)
  Spacer 34
  Text homeColumnName      "NAME"       weight(1)
  Text homeColumnSize      "SIZE"       width 76
  Text homeColumnProgress  "PROGRESS"   width 140
  Text homeColumnSpeed     "SPEED"      width 84
  Text homeColumnLeft      "LEFT"       width 64
  Spacer 34
```

Height 24 + 4 = 28 dp. Columns are not clickable (sorting is only in the toolbar menu).

### 4.7 `taskStateLine(task, speedUnit): String` - lines 91-132

`pct = (progress.coerceIn(0f, 1f) * 100).toInt()`. First match wins:

1. `waitingInQueue`:
    - `scheduledAtMs > currentEpochMs()` -> `homeStartsAt(formatLocalSchedule(scheduledAtMs))`
      "Starts today 2:05 PM"
    - `queuePosition > 0` -> `homeWaitingInQueuePosition(queuePosition)` "Waiting in queue (#2)"
    - else `homeWaitingInQueue` "Waiting in queue"
2. `postProcessLabel != null` -> `localizeStep(postProcessLabel)`
3. `CONNECTING` -> `homeConnecting` "Connecting…"
4. `QUEUED` -> `homeStarting` "Starting…"
5. `DOWNLOADING` -> parts joined with `" · "`:
    - `"$pct%"`
    - if `currentStepText` starts with `"Step"`:
      `localizeStep(currentStepText.substringBefore(" ("))`
    - if `downloadSpeedBytesPerSec > 0`: `formatSpeed(speed, speedUnit)`
    - if `etaSeconds() != null`: `homeTimeLeft(etaText(eta))` "3m 20s left"
6. `PAUSED` -> `homePausedState(pct, canResume() == false)` "Paused · 42%" (+ " · restarts on
   resume")
7. `ERROR` -> `homeFailedState(errorMessage?.let { localizeStep(it) })` "Failed: <message>" /
   "Failed: error"
8. `COMPLETED` -> `homeCompletedState(if (totalBytes > 0) formatBytes(totalBytes) else null)`
   "Completed · 128.0 MB" / "Completed"
9. else -> `status.name.lowercase()` with the first letter upper-cased ("Idle")

### 4.8 `sizeText(task)` - lines 134-142 (private)

1. `totalBytes > 0 && status == DOWNLOADING && approximateTotalBytes == totalBytes` ->
   `"~" + formatBytes(totalBytes)`
2. `totalBytes > 0` -> `formatBytes(totalBytes)`
3. `downloadedBytes > 0` -> `formatBytes(downloadedBytes)`
4. else `"—"`

### 4.9 `etaText(seconds)` - lines 84-88 (private, used by the row)

`< 60` -> `"${s}s"`; `< 3600` -> `"${s / 60}m ${(s % 60) padded to 2}s"`; else
`"${s / 3600}h ${((s % 3600) / 60) padded to 2}m"`. No upper cap (the details use `formatEta`,
which caps at `"> 99h"`).

### 4.10 `SideFilter` - lines 505-542

The sidebar's filter (the sidebar itself is another spec; the list depends on `matches`).

| Value         | `label`                                  | `matches(t)`                                                     |
|---------------|------------------------------------------|------------------------------------------------------------------|
| `ALL`         | `homeFilterAllDownloads` "All downloads" | true                                                             |
| `DOWNLOADING` | `homeFilterDownloading` "Downloading"    | `!waitingInQueue && status in {DOWNLOADING, CONNECTING, QUEUED}` |
| `WAITING`     | `homeFilterWaiting` "Waiting"            | `waitingInQueue`                                                 |
| `PAUSED`      | `homeFilterPaused` "Paused"              | `!waitingInQueue && status in {PAUSED, IDLE}`                    |
| `COMPLETED`   | `homeFilterCompleted` "Completed"        | `status == COMPLETED`                                            |
| `FAILED`      | `homeFilterFailed` "Failed"              | `status == ERROR`                                                |
| `MEDIA`       | `homeFilterMedia` "Video & audio"        | `engineType == MEDIA_STREAM`                                     |
| `FILES`       | `homeFilterFiles` "Files"                | `engineType == HTTP_DIRECT`                                      |
| `TORRENTS`    | `homeFilterTorrents` "Torrents"          | `engineType == BITTORRENT`                                       |

State grouping is on for `ALL`, `MEDIA`, `FILES`, `TORRENTS` and off for the five state filters
(`App.kt:388-393`).

---

## 5. `TaskDetails.kt`

### 5.1
`TaskDetailsPane(task, viewModel, speedDisplayUnit, fullScreen, onClose, fillHeight = true)` - lines 820-887

Used by the side panel (`App.kt:430`, `fullScreen = false`, `fillHeight = true`) and the sheet
(`App.kt:529`, `fullScreen = phone`, `fillHeight = phone || sheetExpanded`).

```
Column (fillMaxSize if fillHeight else fillMaxWidth)
  Row fillMaxWidth, padding start 8 / end 8 / top 8 / bottom 4, vertically centred
    [IconButton -> onClose, Text "←" fontSize 20]                    only if fullScreen
    Text weight(1), padding start (0 if fullScreen else 8)
         fullScreen: detailsHeaderDownload "Download", titleMedium, Bold, letterSpacing 0, onSurface
         else:       detailsHeaderDetails  "DETAILS",  labelMedium, Bold, letterSpacing 0.8, onSurfaceVariant
    [IconButton -> onClose, Text "✕" fontSize 15]                    only if !fullScreen
  Column (fillMaxSize if fillHeight else fillMaxWidth), verticalScroll,
         padding start 16 / end 16 / top 4 / bottom 16
    DownloadTaskItem(...)
```

- Header height 8 + 48 + 4 = 60 dp (the icon button's slot is 48 dp, its circle 40 dp).
- The body scrolls with the mouse wheel; **no `AppScrollbar` here**.
- Side panel content width: 340 - 1 (divider) - 32 = 307 dp, and 299 dp inside
  `DownloadTaskItem`'s own 4 dp padding. Sheet: up to 820 - 32 - 8 = 780 dp.
- Wiring to events:

| Callback                                                  | Event                                                                                 |
|-----------------------------------------------------------|---------------------------------------------------------------------------------------|
| `onPause`                                                 | `OnPauseClicked(task.id)`                                                             |
| `onResume`                                                | `OnResumeClicked(task.id)`                                                            |
| `onCancel`                                                | `OnCancelClicked(task.id)` (opens the remove confirmation, same as `OnRemoveClicked`) |
| `onFileSelectionToggled(i, sel)`                          | `OnTaskTorrentFileSelectionToggled(task.id, i, sel)`                                  |
| `onFilePriorityChanged(i, p)`                             | `OnTaskTorrentFilePriorityChanged(task.id, i, p)`                                     |
| `onShare` (only when `task.canSendToDevice()`, else null) | `OnShareTaskClicked(task.id)`                                                         |

### 5.2 `DownloadTaskItem(...)` - lines 71-688

```kotlin
internal fun DownloadTaskItem(
    task: DownloadTask,
    speedDisplayUnit: String = "MB/s",
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onFileSelectionToggled: (Int, Boolean) -> Unit,
    onFilePriorityChanged: (Int, Int) -> Unit,
    onShare: (() -> Unit)? = null
)
```

Used only by `TaskDetailsPane`.

Computed:

-
`displayName = decodeUnicodeText(fileName.ifBlank { url.substringBefore('?').substringAfterLast('/') }).trim()`
- `fullPath`: `fileName` not blank -> `dest = destinationPath.trimEnd('/', '\\')`,
  `sep = "\\"` if dest contains `\` else `"/"`, `"$dest$sep$fileName"`; blank -> `destinationPath`;
  then `decodeUnicodeText(...).trim()`
- `isCompleted = status == COMPLETED`
- `running = status == DOWNLOADING || status == CONNECTING`
- `canFolder = PathResolver().isOpenFolderSupported()` (desktop: `true`)

UI-local state: `showFiles = false` (torrent files open), `confirmRestartPause = false` (dialog).
Both are plain `remember` inside the pane: they survive switching to another task (the composable
is reused) and reset when the pane closes.

`pauseClicked`: `task.canResume() == false` -> `confirmRestartPause = true`, else `onPause()`.
While `confirmRestartPause`: `RestartOnPauseDialog(isClip = engineType == MEDIA_STREAM, onConfirm
= { close; onPause() }, onDismiss = { close })` (section 8.1).

Layout, top to bottom (a transparent `Card` with no elevation wraps a
`Column(padding horizontal 4 / vertical 4)`):

**(1) Name** - `Text(displayName)` titleMedium, Bold, onSurface, fillMaxWidth, wraps without limit.

**(2)** `Spacer 10`

**(3) Chips** - `FlowRow` fillMaxWidth, 6 dp between chips, 6 dp between lines; `DetailChip`s:

| Chip   | When                                     | Text                                                                                                                                                    | Colour                          |
|--------|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|
| type   | always                                   | `MEDIA_STREAM` -> `detailsChipMedia` "Media"; `BITTORRENT` -> `detailsChipTorrent` "Torrent"; `HTTP_DIRECT` -> `detailsChipFile` "File"                 | `#818CF8` / `#10B981` / primary |
| resume | `!isCompleted` and `canResume() != null` | `true` -> `detailsChipResumable` "Resumable"; `false` -> `detailsChipNoResume` "No resume"                                                              | `#10B981` / `#F59E0B`           |
| clip   | `clipStart` or `clipEnd` not blank       | `detailsClipChip(clipStart ?: "00:00", clipEnd, clipByCutting && !isCompleted)` "Clip 00:01:00–00:02:00" (+ " · cut after download"; end null -> "End") | onSurfaceVariant                |

**(4) Torrent files** - only if `task.torrentFiles.isNotEmpty()` (lines 178-286):

```
Spacer 6
Row fillMaxWidth, clickable { showFiles = !showFiles }, padding vertical 2
  Text "📁 " + detailsTorrentFiles(selectedCount, total) + " " + ("▲" if showFiles else "▼")
       bodySmall, Bold, primary                      "📁 Torrent Files (3 selected / 5) ▼"
if showFiles:
  Spacer 4
  Box fillMaxWidth, height 110, background surfaceVariant alpha 0.4 with RoundedCornerShape(6), padding 4
    LazyColumn fillMaxSize, spacedBy 2, key = file.index
      Row fillMaxWidth, padding horizontal 4 / vertical 2, vertically centred
        Checkbox checked = file.isSelected -> onFileSelectionToggled(file.index, checked)
        Spacer 4
        Text ("✅" if file.isCompleted else "⚪") + " " + file.path
             bodySmall, maxLines 1, weight(1)
             colour: selected -> onSurface, else onSurfaceVariant alpha 0.5
        Surface clickable, RoundedCornerShape(4), color = pc alpha 0.15
          Text priorityLabel, labelSmall, Bold, pc, padding horizontal 6 / vertical 2
        Spacer 8
        Text fileProgressText, labelSmall, onSurfaceVariant
```

- The checkbox here has no size modifier: its slot is 48 x 48 dp, so each file row is 52 dp high
  and the 110 dp box shows about two rows; the inner list scrolls (no scrollbar).
- File name has no ellipsis (clipped at the end of its space).
- File priority: `1` -> `detailsPriorityHigh` "High" in primary; `-1` -> `detailsPriorityLow`
  "Low" in error; else `detailsPriorityNormal` "Normal" in onSurfaceVariant. Click cycles
  `0 -> 1 -> -1 -> 0` and calls `onFilePriorityChanged(file.index, next)`.
- `fileProgressText`: completed -> `formatBytes(size)`; `downloadedBytes > 0` ->
  `"${formatBytes(downloadedBytes)} / ${formatBytes(size)}"`; else `formatBytes(size)`.

**(5)** `Spacer 8`

**(6) Summary card** - `Surface` RoundedCornerShape (14), surfaceVariant alpha 0.35, fillMaxWidth,
`Column(padding 14)` (lines 291-483):

```
Row aligned to the bottom
  Text "${(progress.coerceIn(0f, 1f) * 100).toInt()}%", fontSize 30, Bold, onSurface
       (line box 24 dp; the glyphs overflow it)
  Spacer weight(1)
  Text status word, labelMedium, onSurfaceVariant, padding bottom 6
       COMPLETED -> detailsStatusCompleted "Completed"; PAUSED -> detailsStatusPaused "Paused";
       ERROR -> detailsStatusFailed "Failed"; otherwise detailsStatusWaiting "Waiting" if
       waitingInQueue, else ""
Spacer 4
[step chip]  if currentStepText not blank and status in {DOWNLOADING, CONNECTING}:
  Spacer 4
  Surface RoundedCornerShape(6), primaryContainer alpha 0.7
    Text "⚡ " + localizeStep(currentStepText), labelSmall, Bold, onPrimaryContainer,
         maxLines 1, ellipsis, padding horizontal 8 / vertical 4
LinearProgressIndicator progress = task.progress, fillMaxWidth, height 8, clip RoundedCornerShape(4),
                        gapSize 0, no stop indicator   (primary on secondaryContainer)
[post-process line]  if postProcessLabel != null and status in {DOWNLOADING, CONNECTING}:
  Spacer 6
  PostProcessLine(postProcessLabel, postProcessProgress, postProcessStartedAtMs)
Spacer 8
Row spacedBy 12:  DetailStat(detailsDownloaded "Downloaded", bytesText, onSurface)  weight(1)
                  DetailStat(detailsSpeed "Speed" if DOWNLOADING else detailsState "State", speedText, speedColor)  weight(1)
Spacer 10
Row spacedBy 12:  DetailStat(detailsTimeLeft "Time left", eta?.let { formatEta(it) } ?: "—", onSurface)  weight(1)
                  DetailStat(detailsAdded "Added", formatAddedTime(createdAtEpochMs), onSurface)        weight(1)
```

There is no spacer between the step chip and the progress bar (the bar touches the chip).

`bytesText` (lines 365-378):

-
`totalIsEstimate = status == DOWNLOADING && approximateTotalBytes > 0 && approximateTotalBytes == totalBytes`
- `sectionClipRunning = status == DOWNLOADING && !clipByCutting && (clipStart or clipEnd not blank)`
- `sectionClipRunning` -> `formatBytes(downloadedBytes)`
- else `"${formatBytes(downloadedBytes)} / ${X}"` where `X` = `totalBytes > 0` ->
  `("~" if totalIsEstimate) + formatBytes(totalBytes)`, else `detailsUnknownSize` "Unknown"

`speedText` (lines 380-435), first match wins:

1. `status == DOWNLOADING`:
    1. `postProcessLabel != null` -> `localizeStep(postProcessLabel) + "..."`
    2. `currentStepText` contains "Merging" (ignore case) -> `detailsMergingAudioVideo` "Merging
       Audio & Video..."
    3. `BITTORRENT && disableUpload` -> `detailsTorrentSpeedUploadDisabled(formatSpeed(dl, unit))`
       "DL: 2.1 MB/s (UL Disabled)"
    4. `BITTORRENT` -> `detailsTorrentSpeed(formatSpeed(dl, unit), formatSpeed(ul, unit))` "DL: 2.1
       MB/s | UL: 0 KB/s"
    5. else `formatSpeed(downloadSpeedBytesPerSec, unit)`
2. `status == CONNECTING` -> `detailsConnecting` "Connecting..."
3. `waitingInQueue`:
    - `scheduledAtMs > currentEpochMs()` ->
      `"🕑 " + detailsStartsAt(formatLocalSchedule(scheduledAtMs))`
    - `queuePosition > 0` -> `"⏳ " + detailsWaitingInQueuePosition(queuePosition)`
    - else `"⏳ " + detailsWaitingInQueue`
4. else by status: `COMPLETED` -> `detailsStatusCompleted`; `PAUSED` -> `detailsStatusPaused`;
   `ERROR` -> `detailsStatusFailed`; `QUEUED` -> `detailsStatusQueued` "Queued"; else
   `detailsStatusNotStarted` "Not started"

`speedColor`: `DOWNLOADING` -> primary; `COMPLETED` -> `#10B981`; `PAUSED` -> `#F59E0B`; `ERROR` ->
error; else onSurfaceVariant.

**(7) Pieces** - if `task.chunks.size > 1 && status != COMPLETED`: `Spacer 12`,
`PiecesBar(task, speedDisplayUnit)` (section 6).

**(8) Error** - if `status == ERROR && errorMessage` not blank: `Spacer 6`,
`Text("⚠️ " + localizeStep(errorMessage))` bodySmall, Medium, error, wraps.

**(9) Saved to** - `Spacer 14`; `Text(detailsSavedTo)` "Saved to", labelSmall, onSurfaceVariant;
`Text(fullPath)` bodySmall, onSurface alpha 0.75, maxLines 3, ellipsis, padding top 3.

**(10) Links** (lines 519-563) - the list
`[(detailsSource "Source", task.url), (detailsPage "Page", requestHeaders["Referer"] ?: originalUrl.orEmpty())]`,
filtered to non-blank links, then `distinctBy` link (a Page equal to the Source is dropped). For
each:

```
Spacer 10
Row vertically centred
  Text label, labelSmall, onSurfaceVariant, weight(1)
  Text copy "Copy", labelSmall, primary, SemiBold, clickable, padding horizontal 6 / vertical 2
       -> clipboard.setText(link)  (errors ignored; no toast)
SelectionContainer
  Text link, bodySmall, onSurface alpha 0.75, maxLines 4, ellipsis, padding top 3
```

The link text can be selected with the mouse and copied (the only selectable text in this area).

**(11) Checksum** - if `expectedChecksum` not blank: `Spacer 10`,
`Text("$icon $text")` bodySmall, Medium, padding top 3:

| `checksumVerified` | Icon | Text                                     | Colour           |
|--------------------|------|------------------------------------------|------------------|
| `true`             | `✅` | `checksumVerified` "Checksum verified"   | `#2E7D32`        |
| `false`            | `❌` | `checksumMismatch` "Checksum mismatch"   | error            |
| `null`             | `⏳` | `checksumChecking` "Verifying checksum…" | onSurfaceVariant |

**(12)** `Spacer 18`

**(13) Main actions** - `Row` fillMaxWidth, spacedBy 8; each button `weight(1)`, height 42 (lines
601-654). First matching case:

| Case                                 | Primary (`DetailPrimaryButton`)                                          | Secondary (`DetailSecondaryButton`)                                                               |
|--------------------------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `isCompleted`                        | `detailsOpenFile` "Open file" -> `openDownloadedFile(fullPath, task.id)` | `detailsOpenFolder` "Open folder" -> `PathResolver().openFileLocation(fullPath)` (if `canFolder`) |
| `running` (DOWNLOADING / CONNECTING) | `pause` "Pause" -> `pauseClicked` (may ask first)                        | "Open folder" (if `canFolder`)                                                                    |
| `waitingInQueue`                     | `detailsStartNow` "Start now" -> `onResume`                              | `pause` "Pause" -> `onPause` (no question)                                                        |
| else (PAUSED, ERROR, IDLE, QUEUED)   | `retry` "Retry" if `ERROR` else `resume` "Resume" -> `onResume`          | "Open folder" (if `canFolder`)                                                                    |

When the secondary button is absent the primary takes the whole width.

**(14) Send to device** - if `onShare != null`: `Spacer 8`, `OutlinedButton` fillMaxWidth,
height 42, RoundedCornerShape (10), default colours; `Text(sysDeviceSendTitle)` "Send to device",
13 sp, SemiBold. `canSendToDevice()`: not a torrent, and (`COMPLETED` or `canResume() != false`).

**(15) Cancel / remove** - `Spacer 8`, `OutlinedButton` fillMaxWidth, height 42,
RoundedCornerShape (10), border 1 dp error alpha 0.35, content error, transparent container;
`Text` 13 sp SemiBold: `detailsRemoveFromList` "Remove from list" if completed, else
`detailsCancelDownload` "Cancel download". -> `onCancel`.

Values read directly from sharedLogic: `PathResolver` (`isOpenFolderSupported`,
`openFileLocation`, and through `openDownloadedFile` `fileExists` / `openFile`), `localizeStep`,
`currentEpochMs`, `formatLocalSchedule`, `localUtcOffsetMs`, and the `DownloadTask` extensions
`canResume`, `canSendToDevice`, `etaSeconds`.

### 5.3 `DetailChip(text: String, color: Color)` - lines 691-702

`Surface` RoundedCornerShape (6), `color` alpha 0.14, no border; `Text` 11 sp, SemiBold, `color`,
padding horizontal 8 / vertical 3. Height 30 dp. Not clickable.

### 5.4 `DetailPrimaryButton(text, modifier, onClick)` - lines 704-716

`Button` RoundedCornerShape (10), container primary, content onPrimary, contentPadding horizontal
8; `Text` 13 sp, Bold, maxLines 1.

### 5.5 `DetailSecondaryButton(text, modifier, onClick)` - lines 718-731

`OutlinedButton` RoundedCornerShape (10), border 1 dp outlineVariant alpha 0.6, transparent
container, content onSurface, contentPadding horizontal 8; `Text` 13 sp, SemiBold, maxLines 1.

### 5.6 `DetailStat(label, value, color, modifier = Modifier)` - lines 889-907

`Column`: `Text(label)` labelSmall, onSurfaceVariant; `Text(value)` bodyMedium, SemiBold, `color`,
maxLines 2, ellipsis, padding top 2. Height 16 + 2 + 20 = 38 dp (58 with two value lines).

### 5.7 `PostProcessLine(label: String, progress: Float, startedAtMs: Long)` - lines 748-817

Second progress line (merging, converting, saving).

- `start = startedAtMs` if `> 0`, else the time this label first appeared (`remember(label)`).
- `elapsedSec` recomputed every 1000 ms: `((now - start) / 1000).coerceAtLeast(0)`.
- `elapsedText`: minutes >= 60 -> `"H:MM:SS"`, else `"M:SS"` (minutes not padded).
- Icon, tested on the raw English label: starts with "Merging" -> `🔗`; starts with "Saving" ->
  `📁`; contains "MP3" -> `🎵`; else `⚙️` (all ignore case).

```
Column fillMaxWidth
  Row fillMaxWidth, SpaceBetween, vertically centred
    Text "$icon ${localizeStep(label)}", labelSmall, SemiBold, tertiary, maxLines 1, ellipsis,
         weight(1, fill = false)
    Text (if progress >= 0: "${(progress * 100).toInt()}% · ") + elapsedText, labelSmall, onSurfaceVariant
  Spacer 4
  LinearProgressIndicator fillMaxWidth, height 5, clip RoundedCornerShape(3), colour tertiary
      progress >= 0: determinate (progress.coerceIn(0f, 1f)), gapSize 0, no stop indicator
      else: indeterminate with the M3 defaults
```

Track: secondaryContainer. Height 16 + 4 + 5 = 25 dp.

### 5.8 `formatEta(seconds: Long): String` - lines 734-742

`s = seconds.coerceAtLeast(0)`: `< 60` -> `"${s}s"`; `< 3600` ->
`"${s / 60}m ${(s % 60) padded 2}s"`;
`< 100 * 3600` -> `"${s / 3600}h ${((s % 3600) / 60) padded 2}m"`; else `"> 99h"`.

### 5.9 `formatAddedTime(epochMs: Long): String` - lines 910-938

- `epochMs <= 0` -> `"—"`.
- `offset = localUtcOffsetMs()`, `dayMs = 86_400_000`, `local = epochMs + offset`,
  `day = local.floorDiv(dayMs)`, `today = (currentEpochMs() + offset).floorDiv(dayMs)`.
- `minutes = local.mod(dayMs) / 60_000`, `h24 = minutes / 60`,
  `h12 = 12 if h24 % 12 == 0 else h24 % 12`,
  `clock = "$h12:${(minutes % 60) padded 2} ${"AM" if h24 < 12 else "PM"}"`.
- `today - day == 0` -> `detailsAddedToday(clock)` "Today 2:05 PM"; `== 1` ->
  `detailsAddedYesterday(clock)` "Yesterday 9:10 AM"; else `"DD/MM/YYYY"` from the civil-date
  algorithm in lines 925-935 (copy as is).

---

## 6. `ui/PiecesBar.kt`

### 6.1 `PiecesBar(task: DownloadTask, speedDisplayUnit: String)` - lines 56-221

Used by `DownloadTaskItem` when `chunks.size > 1 && status != COMPLETED`.

Computed:

- `chunks = task.chunks.sortedBy { startByte }`
- `total = chunks.maxOf { endByte + 1 }`; if not `> 0` the composable renders nothing
- `manySegments = chunks.size > 40`
- `active = chunks.filter { isActive }`
- `stalled = chunks.filter { !isActive && !isCompleted && note != null }`
- `finished = chunks.count { isCompleted }`
- colours: `done = activeA = primary`, `activeB = #7DF9FF`, `waiting = #F59E0B`,
  `track = surfaceVariant alpha 0.55`, `gap = surface`
- `sheen`: infinite float animation from -0.4 to 1.4 in 1600 ms, linear, restarting

```
Column
  Row fillMaxWidth, vertically centred, spacedBy 6
    Text homePiecesLabel "Pieces", labelSmall, onSurfaceVariant, weight(1)
    Pill(homeSegmentsCount(n) "510 segments" if manySegments else homePiecesCount(n) "8 pieces",
         fg onSurfaceVariant, bg surfaceVariant alpha 0.5)
    [Pill(homeActiveCount(active.size) "4 active", fg #7DF9FF, bg #7DF9FF alpha 0.14, dot = true)]   if active not empty
    [Pill(homeWaitingCount(stalled.size) "1 waiting", fg #F59E0B, bg #F59E0B alpha 0.14)]            if stalled not empty
    [Pill(homeDoneCount(finished) "3 done", fg #10B981, bg #10B981 alpha 0.14)]                      if finished > 0
  Spacer 8
  Canvas fillMaxWidth, height 22, clip RoundedCornerShape(6)
  manySegments:
    Spacer 6
    Text homeSegmentsProgress(finished, n, active "#3, #7" list or null), fontSize 11, onSurfaceVariant,
         maxLines 2, ellipsis                   "Segments 120/510 · downloading #121, #122"
  else if (active + stalled) not empty:
    Spacer 10
    (active + stalled).sortedBy { chunkIndex }, two per line:
      Row fillMaxWidth, spacedBy 8
        ConnectionCard weight(1)   tint = #7DF9FF if active else #F59E0B
        [second ConnectionCard weight(1), or Spacer weight(1) when the line has one card]
      Spacer 6
```

Canvas drawing (lines 123-182). `w`, `h` are the canvas size in device pixels; **the constants
below are device pixels, not dp**:

- corner radius of every rectangle: 3 px
- `gapPx`: `w / n > 60` -> 2; `w / n > 6` -> 1; else 0
- for each chunk `c`: `len = max(1, endByte - startByte + 1)`,
  `x0 = w * startByte / total + gapPx / 2`, `x1 = w * (endByte + 1) / total - gapPx / 2`,
  `segW = max(1, x1 - x0)`, `fill = (downloadedBytes / len).coerceIn(0, 1)`, `fw = segW * fill`
    1. track: round rect (x0, 0, segW, h) in `track`
    2. if `fill > 0`:
        - completed: round rect (x0, 0, segW, h) in primary
        - active: round rect (x0, 0, fw, h) with a horizontal gradient primary -> `#7DF9FF` from
          `x0` to `x0 + fw`; then the sheen: the same rect with a horizontal gradient
          transparent -> white alpha 0.45 -> transparent between `sx - 0.18 fw` and `sx + 0.18 fw`,
          `sx = x0 + fw * sheen`
        - else (partly done, idle): round rect (x0, 0, fw, h) in `#F59E0B` alpha 0.85 if
          `note != null`, else primary alpha 0.6
    3. if `fill == 0 && note != null`: round rect (x0, h - 3, segW, 3) in `#F59E0B` alpha 0.5
- if `gapPx > 0`: for each chunk except the first, a rect `gapPx` wide centred on its start `x`,
  full height, in `surface`

The sheen animation keeps the canvas redrawing every frame while the bar is on screen.

### 6.2 `Pill(text, fg, bg, dot = false)` - lines 223-236 (private)

`Row` clip `RoundedCornerShape(50 %)`, background `bg`, padding horizontal 8 / vertical 3,
vertically centred; optional dot (6 dp circle in `fg`, then `Spacer 5`); `Text` 10 sp, SemiBold,
`fg`, maxLines 1. Height 30 dp.

### 6.3
`ConnectionCard(c: DownloadChunk, speedDisplayUnit, tint, modifier)` - lines 238-295 (private)

`len = max(1, endByte - startByte + 1)`, `frac = (downloadedBytes / len).coerceIn(0, 1)`.

```
Column clip RoundedCornerShape(10), background surfaceVariant alpha 0.35, padding horizontal 10 / vertical 8
  Row vertically centred
    Box clip RoundedCornerShape(5), background tint alpha 0.18, padding horizontal 6 / vertical 1
      Text "#${chunkIndex + 1}", fontSize 10, Bold, tint
    Spacer 8
    Text weight(1), fontSize 11, SemiBold, maxLines 1, ellipsis
         active: formatSpeed(c.speedBytesPerSec, unit) in onSurface
         else:   homeRetryingNote(c.note) "HTTP 429 · retrying" in tint
    Text "${(frac * 100).toInt()}%", fontSize 11, Bold, onSurface alpha 0.85
  Spacer 6
  Box fillMaxWidth, height 4, clip RoundedCornerShape(2), background surfaceVariant
    [Box fillMaxWidth(frac), height 4, clip RoundedCornerShape(2), background tint]   if frac > 0
```

Height 8 + 26 + 6 + 4 + 8 = 52 dp. Not clickable.

---

## 7. `ui/QueueUi.kt`

### 7.1 `QueueSettingsCard()` - lines 18-47

Used by `ui/SettingsPages.kt:424` (Settings, after the "sort into folders" group). Reads and writes
sharedLogic directly, no `DashboardUiEvent`:

- `QueueManagerRepository.getInstance().maxConcurrentDownloads: StateFlow<Int>` (0 = no limit)
- `QueueManagerRepository.getInstance().setMaxConcurrentDownloads(n)` (coerced to 0..20, saved,
  the queue reacts at once)

```
SettingsGroup(title = homeQueueTitle "Queue", footer = homeQueueFooter)
  SettingRow(title = homeDownloadsAtSameTime "Downloads at the same time",
             subtitle = homeNoLimit "No limit" if max <= 0 else homeAtATime(max) "3 at a time")
  Box fillMaxWidth, horizontalScroll, padding start 16 / end 16 / bottom 14
    SegmentedPicker(options = [1, 2, 3, 4, 5, 8, 0], selected = max,
                    label = homeNoLimit if 0 else the number, onSelect = setMaxConcurrentDownloads)
```

Footer text: "Extra downloads wait and start by themselves, oldest first, when one finishes.
"Start now" on a waiting download skips the queue."

A stored value that is not one of the seven options (6, 7, 9..20) leaves no segment selected while
the subtitle still shows it.

Building blocks from `ui/SettingsKit.kt` (owned by the settings spec; values repeated so this card
can be built alone):

- `SettingsGroup` (lines 43-79): caption `title.uppercase()` labelSmall, Bold, letterSpacing 0.8,
  onSurfaceVariant, padding start 4 / bottom 8; body `Surface` RoundedCornerShape (14), surface,
  border 1 dp outlineVariant alpha 0.35, fillMaxWidth; footer bodySmall, onSurfaceVariant, padding
  start 4 / end 4 / top 8.
- `SettingRow` (lines 92-129): fillMaxWidth, min height 56, padding horizontal 16 / vertical 12;
  title bodyMedium SemiBold onSurface; `Spacer 2`; subtitle bodySmall onSurfaceVariant.
- `SegmentedPicker` (lines 146-180): `Row` clip RoundedCornerShape (10), background surfaceVariant
  alpha 0.45, padding 3, spacedBy 2; each option a `Box` clip RoundedCornerShape (8), background
  primary if selected else transparent, clickable, min height 32, padding horizontal 12 /
  vertical 7; `Text` 12 sp, maxLines 1, Bold + onPrimary if selected, else Medium + onSurface.
  Segment height 38 dp (7 + 24 + 7).

---

## 8. Related pieces outside the five files

### 8.1 `RestartOnPauseDialog(isClip, onConfirm, onDismiss)` - `HomeDialogs.kt:19-47`

`AlertDialog`, shape RoundedCornerShape (18), otherwise the defaults of 2.3:

- title `homeCantResumeTitle` "This download can't be resumed", headlineSmall with Bold
- text bodyMedium: `isClip` -> `homeCantResumeClipText` "Clips cut while downloading can't
  continue where they stopped. If you pause now, the clip will start again from the beginning
  when you resume."; else `homeCantResumeServerText` "The server doesn't support resuming. If you
  pause now, the download will start again from the beginning when you resume."
- dismiss `OutlinedButton` RoundedCornerShape (10): `homeKeepDownloading` "Keep downloading"
- confirm `Button` RoundedCornerShape (10): `homePauseAnyway` "Pause anyway" -> `onPause()`

Opened only from the details' Pause button (the row's quick action opens the details instead).

### 8.2 `openDownloadedFile(fullPath, taskId)` - `App.kt:68-77`, and the missing-file dialog

```
resolver = PathResolver()
if (!resolver.fileExists(fullPath)) { missingFileTaskId.value = taskId; return }
if (!resolver.openFile(fullPath)) resolver.openFileLocation(fullPath)
```

`missingFileTaskId` is a global `mutableStateOf<String?>` (`App.kt:62`). While set and the task
still exists, `MissingFileDialog` shows (`App.kt:786-797`, `ui/MissingFileDialog.kt`): title
`fileMissingTitle` "File not found" (titleMedium, Bold), text `fileMissingMessage(fileName)`,
confirm `Button` in error colour `detailsRemoveFromList` "Remove from list" -> clear the id, then
`OnRemoveClicked(id)` followed by `OnConfirmRemoveClicked(false)` (removes without the question
and without deleting files); dismiss `OutlinedButton` `close` "Close".

### 8.3 `WhenDoneBanner()` - `ui/WhenDoneUi.kt:66-91`

Sits in `TaskListArea` under the tools banner. Hidden while `WhenDone.action == NOTHING`.
`Surface` RoundedCornerShape (12), tertiary alpha 0.12, fillMaxWidth, padding bottom 8;
`Row(padding start 14 / end 4)`: `Text(whenDoneBanner(label))` "When downloads finish: Sleep",
bodySmall, SemiBold, tertiary, weight (1); `TextButton` `cancel` "Cancel" -> `WhenDone.cancel()`.
Height 48 dp. (Owned by the "when done" spec.)

### 8.4 `AppScrollbar(listState)` - `ui/AppScrollbar.kt`

Overlay at the list's end edge, desktop only, only when the content is taller than the viewport.
Zone 12 dp wide, full height, 2 dp vertical padding. Thumb 6 dp wide at the outer edge,
RoundedCornerShape (3), onSurface alpha 0.28, alpha 0.55 while hovered or scrolling (180 ms
tween). Thumb height = `track * track / (track + maxScroll)`, at least 28 px. Click on the track
jumps so the thumb centres on the click; dragging the thumb scrolls proportionally. It overlaps
the rows' right padding (rows keep their full width).

### 8.5 Remove confirmation

`OnCancelClicked`, `OnRemoveClicked` (single) and `OnRemoveSelectedClicked` (several) only set
`removeConfirmationTaskId` / `removeConfirmationTaskIds`; the shared `RemoveConfirmationDialog`
(`App.kt:776-784`, other spec) answers with `OnConfirmRemoveClicked(deleteFile)` or
`OnDismissRemoveDialog`.

---

## 9. Strings (`LocalizationManager.Strings` members, English for orientation)

List area: `homeUpdatingMediaTools(tool, percent?)`, `homeSearchDownloadsByNamePlaceholder`,
`homeNoDownloadsMatch(query)`, `homeNoTasksInView`, `homeTryAnotherName`,
`homeAddDownloadToGetStarted`, `homeGroupDownloading`, `homeGroupWaiting`,
`homeGroupPausedFailed`, `homeGroupCompleted`, `homeDownloadingWaitingSummary(running, waiting)`,
`homeQueueStatus(maxAtOnce)`, `homeSpeedLimitStatus(limit?)`, `homePauseAll`, `homeResumeAll`,
`homeSortBy(option)`, `sysSortNewestFirst`, `sysSortOldestFirst`, `sysSortLargestFirst`,
`sysSortSmallestFirst`, `sysSortNameAz`, `homeSelectedCount(count)`, `selectAll`, `selectNone`,
`homeExport`, `delete`.

Rows: `homeColumnName`, `homeColumnSize`, `homeColumnProgress`, `homeColumnSpeed`,
`homeColumnLeft`, `pause`, `resume`, `homeStartNow`, `open`, `priorityHigh`, `priorityLow`,
`priorityNormal`, `homeStartsAt(time)`, `homeWaitingInQueuePosition(position)`,
`homeWaitingInQueue`, `homeConnecting`, `homeStarting`, `homeTimeLeft(eta)`,
`homePausedState(percent, restartsOnResume)`, `homeFailedState(message?)`,
`homeCompletedState(size?)`, and the `homeFilter*` labels of 4.10.

Details: `detailsHeaderDetails`, `detailsHeaderDownload`, `detailsChipMedia`,
`detailsChipTorrent`, `detailsChipFile`, `detailsChipResumable`, `detailsChipNoResume`,
`detailsClipChip(start, end?, cutAfterDownload)`, `detailsTorrentFiles(selected, total)`,
`detailsPriorityHigh`, `detailsPriorityLow`, `detailsPriorityNormal`, `detailsStatusCompleted`,
`detailsStatusPaused`, `detailsStatusFailed`, `detailsStatusWaiting`, `detailsStatusQueued`,
`detailsStatusNotStarted`, `detailsUnknownSize`, `detailsMergingAudioVideo`,
`detailsTorrentSpeedUploadDisabled(download)`, `detailsTorrentSpeed(download, upload)`,
`detailsConnecting`, `detailsStartsAt(time)`, `detailsWaitingInQueuePosition(position)`,
`detailsWaitingInQueue`, `detailsDownloaded`, `detailsSpeed`, `detailsState`, `detailsTimeLeft`,
`detailsAdded`, `detailsAddedToday(clock)`, `detailsAddedYesterday(clock)`, `detailsSavedTo`,
`detailsSource`, `detailsPage`, `copy`, `checksumVerified`, `checksumMismatch`,
`checksumChecking`, `detailsOpenFile`, `detailsOpenFolder`, `detailsStartNow`, `retry`,
`sysDeviceSendTitle`, `detailsRemoveFromList`, `detailsCancelDownload`, `homeCantResumeTitle`,
`homeCantResumeClipText`, `homeCantResumeServerText`, `homePauseAnyway`, `homeKeepDownloading`,
plus every `step*` member reached through `localizeStep`.

Pieces: `homePiecesLabel`, `homeSegmentsCount(count)`, `homePiecesCount(count)`,
`homeActiveCount(count)`, `homeWaitingCount(count)`, `homeDoneCount(count)`,
`homeSegmentsProgress(finished, total, downloading?)`, `homeRetryingNote(note?)`.

Queue card: `homeQueueTitle`, `homeQueueFooter`, `homeDownloadsAtSameTime`, `homeNoLimit`,
`homeAtATime(count)`.

Hard-coded (not localized): all glyphs of 2.4; `"—"`; `"~"`; `"%"`; `" · "`; `"v" + APP_VERSION`;
`"#N"`; `" / "`; the units inside `formatBytes` / `formatSpeed` (`B KB MB GB TB`, `B/s KB/s MB/s
Kbps Mbps`); `etaText` / `formatEta` suffixes `s m h` and `"> 99h"`; `AM` / `PM`; the
`DD/MM/YYYY` date; `formatLocalSchedule`'s `"today "`, `"tomorrow "`, `"in N days, "` (English in
every language); the fallback state word of 4.7 rule 9 ("Idle").

---

## 10. (a) Reusable widgets the Slint port needs for this area

| Widget                                       | Behaviour                                                       | Visual spec                                                                                                                                                                                                                                                                                                                         |
|----------------------------------------------|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FjCheckBox` (two- and tri-state)            | click toggles; `indeterminate` input                            | 24 px slot, 20 px box, radius 2, 2 px stroke; unchecked border on-surface-variant; checked / indeterminate fill primary, mark on-primary (path in 2.3); hover halo 40 px circle of on-surface 8 %                                                                                                                                   |
| `StateLayer` (mix-in for anything clickable) | hover / press overlay                                           | content colour 8 % hover, 10 % press, clipped to the owner's shape                                                                                                                                                                                                                                                                  |
| `FjOutlinedButton`                           | enabled / disabled, `clicked`                                   | height input (34 or 42), radius 10, border 1 px outline-variant (inputs for border and text colour: default on-surface-variant; details secondary: outline-variant 60 % + on-surface; cancel: error 35 % + error); disabled text 38 %, border 10 %; min width 58; label 12 or 13 px, weight 600; horizontal padding input (12 or 8) |
| `FjFilledButton`                             | `clicked`                                                       | same geometry; container input (primary or error), text on-primary / on-error; weight 600 or 700                                                                                                                                                                                                                                    |
| `FjTextButton`                               | `clicked`                                                       | height 34, pill, text primary 12 px weight 600, horizontal padding 10, no border                                                                                                                                                                                                                                                    |
| `FjIconButton`                               | `clicked`                                                       | circle (34 or 40 px in a 48 px slot), glyph text, hover layer                                                                                                                                                                                                                                                                       |
| `FjDropdownMenu`                             | opens under its anchor, closes on pick / outside click / Escape | surface-container, radius 4, shadow, vertical padding 8, items 48 px high, 12 px side padding, width 112..280, text 13 px on-surface                                                                                                                                                                                                |
| `FjAlertDialog`                              | modal, scrim black 60 %, Escape / outside = dismiss             | width 280..560, surface-container-high, radius 18 here, padding 24, title 24 / 32 px bold, text 14 / 20 px on-surface-variant, buttons right-aligned, 8 px apart, dismiss before confirm                                                                                                                                            |
| `ThinBar`                                    | fraction 0..1                                                   | track on-surface 8 %, pill, height 5 (4 in connection cards with track surface-variant and radius 2)                                                                                                                                                                                                                                |
| `FjProgressBar`                              | determinate (fraction) or indeterminate                         | height 4 / 5 / 8, radius input, colour input (primary / tertiary), track secondary-container; variants: plain (no gap, no stop dot) and M3 default (4 px gap + stop dot) for the tools banner                                                                                                                                       |
| `Chip` (soft label)                          | static                                                          | radius 6, tint 14 % background, tint text 11 px weight 600, padding 8 / 3, height 30                                                                                                                                                                                                                                                |
| `Pill`                                       | static, optional dot                                            | fully round, padding 8 / 3, text 10 px weight 600, height 30, dot 6 px + 5 px gap                                                                                                                                                                                                                                                   |
| `PriorityChip`                               | click cycles                                                    | radius 6, tint 15 % background, 1 px tint 40 % border, text 10 px weight 600, padding 7 / 3, visible height 30 in a 48 px minimum slot                                                                                                                                                                                              |
| `KindSquare`                                 | static                                                          | 34 px (radius 8) or 40 px (radius 10), on-surface 6 %, tag 9 px weight 800 in the kind colour                                                                                                                                                                                                                                       |
| `QuickActionButton`                          | click; empty variant keeps the space                            | circle 34 / 44 px, 1 px outline-variant 70 % border, glyph 13 px on-surface                                                                                                                                                                                                                                                         |
| `SectionHeader`                              | click folds / unfolds                                           | section 3.3                                                                                                                                                                                                                                                                                                                         |
| `Banner`                                     | text + optional progress or trailing text button                | radius 12, tint 10 % / 12 % background                                                                                                                                                                                                                                                                                              |
| `OverlayScrollbar`                           | section 8.4                                                     | section 8.4                                                                                                                                                                                                                                                                                                                         |
| `SegmentedPicker`                            | one of N                                                        | section 7.1                                                                                                                                                                                                                                                                                                                         |
| `SelectableText`                             | mouse selection + copy, at most 4 lines                         | 12 / 16 px, on-surface 75 %                                                                                                                                                                                                                                                                                                         |
| `BottomSheet` host                           | section 1.5                                                     | section 1.5                                                                                                                                                                                                                                                                                                                         |
| `PiecesMap`                                  | section 6.1                                                     | section 6.1                                                                                                                                                                                                                                                                                                                         |

Slint notes (to check when implementing; none of this was compiled):

- The local Slint 1.18.1 compiler contains `FlexboxLayout` / `flex-wrap`, `ListView`,
  `PopupWindow`, `ContextMenuArea`: candidates for the chips' `FlowRow`, the lazy list, the sort
  menu.
- Compose gives every `Text` an explicit line box (2.2). In Slint set explicit heights on rows,
  chips and pills (68 / 32 / 30 / 28 / 42 px ...) instead of relying on font metrics.
- `PiecesMap`: Compose draws on a canvas; Slint has none. Up to 40 pieces could be `Rectangle`s
  from a model; hundreds of HLS segments (510 in a measured SoundCloud case) should not be one
  element each. A pixel buffer rendered by the host (the bar is only 22 px high) with a few
  overlay rectangles for the active pieces' sheen keeps element count and memory flat.

---

## 11. (b) Port units

Sizes are Compose lines. "Presenter" = the Kotlin side that turns `DashboardUiState` into Slint
models and receives callbacks.

### U1 - Row widgets (about 300 lines)

- Source: `ui/TaskRows.kt` 59-231 (helpers, `KindSquare`, `ThinBar`, `QuickActionButton`),
  402-436 (`nextPriority`, `PriorityChip`).
- Widgets: `KindSquare`, `ThinBar`, `QuickActionButton`, `PriorityChip`, `StateLayer`,
  `FjCheckBox`.
- Depends on: theme tokens only.
- Presenter: the pure helpers of 12.3 (`taskKindTag`, kind colour index, `rowActionFor`, bar
  colour kind, `nextPriority`).
- Events: none by itself.

### U2 - Task row and column header (about 330 lines)

- Source: `ui/TaskRows.kt` 233-399 (`TaskRow` wide + compact), 438-502 (`TaskRowsHeader`).
- Depends on: U1.
- Presenter state per row: `id`, `name`, `kindTag`, `kindColor`, `stateLine`, `stateIsError`,
  `sizeText`, `progress`, `percentText`, `barKind` (primary / error / completed / paused),
  `speedText`, `etaText`, `showPriority`, `priority` (-1 / 0 / 1), `priorityLabel`, `action`
  (0 none, 1 pause, 2 resume, 3 start now, 4 open), `selected`, `checked`; for the header:
  `allChecked`, `someChecked`; for both: `wide` (row width >= 720) and `selectionMode`.
- Callbacks: `row-clicked(id, ctrl, shift)`, `row-long-pressed(id)`, `row-check-changed(id, on)`,
  `row-quick-action(id)`, `row-priority-clicked(id)`, `header-check-all(on)`.

### U3 - List chrome (about 330 lines)

- Source: `TaskList.kt` 74-101 (`ToolsDownloadBanner`), 365-420 (`SectionHeader`), 422-472
  (`QueueStatusBar`), 526-595 (`ListToolbar`), 597-664 (`SelectionBar`); placement of
  `WhenDoneBanner` (8.3).
- Widgets: `Banner`, `FjProgressBar`, `SectionHeader`, `FjOutlinedButton`, `FjFilledButton`,
  `FjTextButton`, `FjIconButton`, `FjDropdownMenu`.
- Presenter state: tools banner (`visible`, `text`, `fraction` or -1), when-done banner (`visible`,
  `text`), toolbar (`anyPausable`, `anyResumable`, `sortLabel`, `sortIndex`, the five
  sort labels), selection bar (`count text`, `allChecked`, labels), status bar (three texts +
  version).
- Events: `OnPauseAllClicked`, `OnResumeAllClicked`, `OnSortChanged(opt)`,
  `OnRemoveSelectedClicked(ids)`, `OnExportLinksClicked(ids)`, `WhenDone.cancel()`; presenter
  only: check all / none, clear selection.

### U4 - List area and its controller (about 400 lines, mostly presenter)

- Source: `TaskList.kt` 103-363 (`TaskListArea`), 474-524 (`TaskStateGroup`, `taskStateGroups`,
  `anyPausable`, `anyResumable`); `ui/TaskRows.kt` 504-542 (`SideFilter`); `App.kt` 131-134,
  139, 377-404 (placement, search, filter, selection); `ui/AppScrollbar.kt`.
- Depends on: U2, U3, `OverlayScrollbar`.
- Presenter state: one flat list model of items `{ kind: header | row, header fields (key, emoji,
  title, count, colour kind, collapsed), row fields of U2 }`, `empty` + two empty-state texts,
  `scrollToTopSignal`, `selectionMode`.
- Presenter logic: everything in 12.1 and 12.2 (search, sidebar filter, grouping, folded groups,
  selection set, anchor, Ctrl / Shift click, Ctrl+A, Escape, Delete, pruning, quick-action
  routing, `openDownloadedFile`).
- Events: `OnPauseClicked`, `OnResumeClicked`, `OnTaskPriorityChanged`, `OnRemoveClicked`,
  `OnRemoveSelectedClicked`.

### U5 - Details host (about 210 lines)

- Source: `TaskDetails.kt` 819-887 (`TaskDetailsPane`); `App.kt` 409-439 (side panel), 445-550
  (sheet).
- Widgets: `FjIconButton`, `BottomSheet` host.
- Depends on: U6, U7.
- Presenter state: `detailsVisible`, `detailsMode` (side panel / sheet from the window width),
  `sheetExpanded`.
- Events: close (presenter: `selectedTaskId = null`).

### U6 - Details body, upper part (about 480 lines)

- Source: `TaskDetails.kt` 71-483 (name, chips, torrent files, summary card), 690-702
  (`DetailChip`), 733-817 (`formatEta`, `PostProcessLine`), 889-938 (`DetailStat`,
  `formatAddedTime`).
- Widgets: `Chip`, `FjCheckBox`, `FjProgressBar`, flow layout.
- Depends on: U8 (`PiecesMap`) for the slot under the card.
- Presenter state: `name`; chips list `{ text, colour kind }`; torrent files: `visible`, `header
  text`, `open`, rows `{ index, selected, text, priorityLabel, priorityKind, progressText }`;
  summary: `percentText`, `statusWord`, `stepChipVisible`, `stepChipText`, `progress`;
  post-process: `visible`, `text`, `rightText` (percent + elapsed, refreshed every second),
  `fraction` or -1; stats: `bytesText`, `speedLabel`, `speedText`, `speedColorKind`, `etaText`,
  `addedText`.
- Events: `OnTaskTorrentFileSelectionToggled`, `OnTaskTorrentFilePriorityChanged`; presenter
  only: toggle `torrentFilesOpen`.

### U7 - Details body, lower part (about 260 lines)

- Source: `TaskDetails.kt` 485-688 (pieces slot, error, saved-to, links, checksum, action
  buttons), 704-731 (`DetailPrimaryButton`, `DetailSecondaryButton`); `HomeDialogs.kt` 19-47
  (`RestartOnPauseDialog`); `App.kt` 62-77, 786-797 (`openDownloadedFile`, missing file).
- Widgets: `FjFilledButton`, `FjOutlinedButton`, `SelectableText`, `FjAlertDialog`.
- Presenter state: `errorVisible`, `errorText`; `savedToPath`; links `{ label, link }` (0..2);
  checksum `{ visible, text, colour kind }`; buttons: `primaryLabel`, `primaryAction` (open file /
  pause / start now / resume), `secondaryVisible`, `secondaryLabel`, `secondaryAction` (open
  folder / pause), `shareVisible`, `cancelLabel`; `restartDialogVisible`, `restartDialogIsClip`.
- Events: `OnPauseClicked`, `OnResumeClicked`, `OnCancelClicked`, `OnShareTaskClicked`,
  `OnRemoveClicked` + `OnConfirmRemoveClicked(false)` (missing file); host actions: copy to
  clipboard, open file, open folder.

### U8 - Pieces map (295 lines)

- Source: `ui/PiecesBar.kt` 1-295.
- Widgets: `Pill`, `PiecesMap`, `ThinBar`.
- Presenter state: `visible`; header pills (`countText`, `activeText` or empty, `waitingText` or
  empty, `doneText` or empty); segments `{ start fraction, end fraction, fill, state: idle /
  active / done / stalled }` or a rendered buffer; `manySegments`, `segmentsProgressText`;
  connection cards `{ numberText, text, textIsTint, percentText, fraction, active }`.
- Events: none.

### U9 - Queue settings card (47 lines + kit)

- Source: `ui/QueueUi.kt` 1-47; `ui/SettingsKit.kt` 43-79, 92-129, 146-180.
- Widgets: `SegmentedPicker`, settings group / row (settings spec).
- Presenter state: `max` (int), `subtitle`.
- Events: `QueueManagerRepository.setMaxConcurrentDownloads(n)`.

Order: U1 -> U2 -> U3 -> U4, then U8 -> U6 -> U7 -> U5; U9 with the settings pages.

---

## 12. (c) State and helpers that move into the Kotlin presenter

`sharedUI` is Compose-only and will not be compiled for mingwX64, so everything below that lives in
`sharedUI` today has to exist in the presenter. Helpers already in `sharedLogic` are called as they
are: `DownloadTask.canResume()`, `canSendToDevice()`, `etaSeconds()`
(`domain/model/DownloadTask.kt:109-146`), `localizeStep` (`core/i18n/start/StepText.kt`),
`formatLocalSchedule`, `currentEpochMs`, `localUtcOffsetMs` (`core/util/LocalClock.kt`),
`filterTasks` (`presentation/dashboard/DashboardFilters.kt`), `TaskSortOption.label`,
`DashboardViewModel.onEvent`.

### 12.1 UI-local state to hold in the presenter

| State                          | Today                    | Initial | Reset / rules                                                                                                                                                                               |
|--------------------------------|--------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `selectedTaskId: String?`      | `App.kt:131`             | null    | set by a plain row click and by the quick action of a non-resumable running task; cleared by close / outside tap / Escape on the sheet; details hidden when the task is gone or on Settings |
| `sideFilter: SideFilter`       | `App.kt:133`             | `ALL`   | set by the sidebar / rail; selecting one also sends `OnTabSelected(ALL)`                                                                                                                    |
| `searchQuery: String`          | `App.kt:134`             | `""`    | top bar text; kept across Settings                                                                                                                                                          |
| `collapsedGroups: Set<String>` | `TaskList.kt:122`        | empty   | keys `run`, `wait`, `stop`, `done`; today reset when the list leaves the screen                                                                                                             |
| `checkedIds: Set<String>`      | `TaskList.kt:140`        | empty   | pruned to the ids of the sidebar-filtered list (before search) on every list change; today reset when the list leaves the screen                                                            |
| `anchorId: String?`            | `TaskList.kt:146`        | null    | set on click, checkbox change, Ctrl+click; not on Shift+click                                                                                                                               |
| `sortMenuOpen`                 | `TaskList.kt:567`        | false   | can stay in Slint                                                                                                                                                                           |
| `torrentFilesOpen`             | `TaskDetails.kt:94`      | false   | resets when the details close, not when the task changes                                                                                                                                    |
| `confirmRestartPause`          | `TaskDetails.kt:95`      | false   |                                                                                                                                                                                             |
| `sheetExpanded`                | `App.kt:452`             | false   | resets whenever `selectedTaskId` changes                                                                                                                                                    |
| `missingFileTaskId: String?`   | `App.kt:62`              | null    | set by `openDownloadedFile`                                                                                                                                                                 |
| post-process elapsed clock     | `TaskDetails.kt:753-762` |         | 1 s tick; start = `postProcessStartedAtMs`, or first sight of the label when 0                                                                                                              |
| list scroll position           | `TaskList.kt:268`        | top     | scroll to top when `scrollToTopSignal` increases                                                                                                                                            |

`sortOption` is already view-model state (`DashboardUiState.sortOption`, default `DATE_DESC`, not
saved to disk).

### 12.2 List pipeline (exact order)

1. `base = state.filteredTasks` (view model: tab filter + sort, 12.4). In the wide layout the tab
   is always `ALL`.
2. `filtered = base.filter { sideFilter.matches(it) }` (4.10).
3. Prune `checkedIds` to ids in `filtered`.
4. `shown = filtered` if `searchQuery.trim()` is empty, else those whose `fileName`, `url` or
   `originalUrl` contains the trimmed query, ignoring case.
5. `grouped = sideFilter in {ALL, MEDIA, FILES, TORRENTS}`.
6. Groups (3.3): `downloading = shown.filter(DOWNLOADING.matches)`, `waiting =
   shown.filter(WAITING.matches)`, `completed = shown.filter { status == COMPLETED }`, `stopped` =
   the rest; order run, wait, stop, done; empty groups dropped; each keeps the order of `shown`.
7. Items: grouped -> for each group a header then, unless folded, its rows; else the rows.
8. `shownOrder` = the row items of step 7 (folded groups excluded).
9. Toolbar: `unfinished = shown.filter { status != COMPLETED }`; `anyPausable` / `anyResumable`
   (3.5). Toolbar visible when nothing is checked and `shown` is not empty.
10. Selection bar visible when `checkedIds` is not empty; `count = checkedIds.size`;
    `allChecked = shown.isNotEmpty() && shown.all { it.id in checkedIds }`.
11. Column header: `allChecked = shown.all { ... }`, `someChecked = checkedIds.isNotEmpty()`.
12. Empty state when `shown` is empty; texts by `searchQuery.isNotBlank()`.

Click / key rules: 3.2 "Keyboard" and "Row callbacks".

### 12.3 Pure helpers to reproduce (all rules given above)

| Helper                                                                                                  | Where today                                                                     | Rule      |
|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|-----------|
| `taskKindTag`, `kindColor`                                                                              | `ui/TaskRows.kt:62-82`                                                          | 4.2       |
| `etaText`                                                                                               | `ui/TaskRows.kt:84-88`                                                          | 4.9       |
| `taskStateLine`                                                                                         | `ui/TaskRows.kt:91-132`                                                         | 4.7       |
| `sizeText`                                                                                              | `ui/TaskRows.kt:134-142`                                                        | 4.8       |
| `rowActionFor`                                                                                          | `ui/TaskRows.kt:147-155`                                                        | 4.4       |
| `barColor`                                                                                              | `ui/TaskRows.kt:157-163`                                                        | 4.3       |
| `nextPriority`                                                                                          | `ui/TaskRows.kt:402-406`                                                        | 4.5       |
| `SideFilter.matches` / `label`                                                                          | `ui/TaskRows.kt:505-542`                                                        | 4.10      |
| `taskStateGroups`                                                                                       | `TaskList.kt:483-516`                                                           | 3.3, 12.2 |
| `anyPausable`, `anyResumable`                                                                           | `TaskList.kt:518-524`                                                           | 3.5       |
| row percent / speed / eta cells                                                                         | `ui/TaskRows.kt:327-348`                                                        | 4.1       |
| display name, full path                                                                                 | `ui/TaskRows.kt:253-255`, `TaskDetails.kt:82-92`                                | 4.1, 5.2  |
| chips, `bytesText`, `speedText`, `speedColor`, status word, button matrix, links list, checksum verdict | `TaskDetails.kt:133-174`, `306-311`, `365-445`, `520-525`, `570-588`, `610-653` | 5.2       |
| `formatEta`                                                                                             | `TaskDetails.kt:734-742`                                                        | 5.8       |
| `formatAddedTime`                                                                                       | `TaskDetails.kt:910-938`                                                        | 5.9       |
| post-process icon and elapsed text                                                                      | `TaskDetails.kt:763-775`                                                        | 5.7       |
| pieces: sets, pill texts, segment geometry, card texts                                                  | `ui/PiecesBar.kt:58-73`, `126-141`, `185-218`, `246-247`                        | 6         |
| `openDownloadedFile`                                                                                    | `App.kt:68-77`                                                                  | 8.2       |
| `formatBytes`, `formatSpeed`, `ltr`, `decodeUnicodeText`                                                | `utils/Formatters.kt`                                                           | below     |

`utils/Formatters.kt`:

- `ltr(text)`: empty -> unchanged, else `"⁦" + text + "⁩"`. `formatBytes` and
  `formatSpeed` return their result wrapped in it.
- `formatBytes(bytes)`: `<= 0` -> `"0 B"`. Divide by 1024 while `>= 1024` through units
  `B, KB, MB, GB, TB`; value **truncated** to one decimal `(value * 10).toLong() / 10.0`; result
  `"$value $unit"` with Kotlin's `Double` text, so always one decimal ("512.0 B", "1.5 MB").
- `formatSpeed(bytesPerSec, unit = "MB/s")`: unit aliases `"BYTES_PER_SEC"` -> `"MB/s"`,
  `"BITS_PER_SEC"` -> `"Mbps"`, `"BOTH"` -> `"MB/s"`.
    - `<= 0`: `"0 KB/s"` for `KB/s`, `"0 Kbps"` for `Kbps`, `"0 Mbps"` for `Mbps`, otherwise
      `"0 KB/s"`.
    - `MB/s`: `< 1024` -> `"$n B/s"` (integer); `< 1 MiB` -> KB with `round(x * 10) / 10` +
      `" KB/s"`;
      `< 10 MiB` -> MB with `round(x * 100) / 100` + `" MB/s"`; else MB with `round(x * 10) / 10`.
    - `KB/s`: `round(bytes / 1024 * 10) / 10` + `" KB/s"`.
    - `Kbps`: `round(bytes * 8 / 1000 * 10) / 10` + `" Kbps"`.
    - anything else (`Mbps`): `mbps = bytes * 8 / 1_000_000`; `< 0.1` -> kbps one decimal +
      `" Kbps"`; `< 10` -> two decimals + `" Mbps"`; else one decimal + `" Mbps"`.
    - Rounding is `kotlin.math.round` (half to even) and the number is printed with Kotlin's
      `Double.toString` ("5.0 MB/s", "6.41 MB/s").
- `decodeUnicodeText(input)`: replace every `\uXXXX` or `_uXXXX` (4 hex digits) with that
  character.

### 12.4 View-model ordering the list relies on (
`DashboardFilters.kt:11-47`, already in sharedLogic)

Unfinished tasks first, then completed. Unfinished are ordered by state rank (0 downloading /
connecting / queued, 1 waiting in queue, 2 paused, 3 others), then queue order (position, or
scheduled time for those without one), then the chosen sort; completed by the chosen sort only.
Sort keys: date = `createdAtEpochMs`; size = `totalBytes` or `approximateTotalBytes` when 0, ties
newest first; name = `fileName` (or `url` when blank), case-insensitive.

---

## 13. Behaviours worth a decision, and what was not verified

Behaviours of the Compose code that a faithful port reproduces and that look unintended:

1. Column header vs rows: the header has no slot for the priority chip, so SIZE / PROGRESS / SPEED
   / LEFT line up only with completed rows (4.1, 4.6).
2. With the side panel open at window widths 1280..1323 the rows fall back to the compact card
   layout (1.3).
3. "Pause all" / "Resume all" are enabled from the rows shown but act on every task (3.5).
4. "Select all" (bar and header) includes rows of folded groups; Ctrl+A does not (3.2).
5. Folded groups, the selection and the scroll position are lost when Settings is opened (3.2).
6. The torrent file list in the details is a 110 dp box whose rows are 52 dp high (5.2).
7. The step chip touches the progress bar under it (5.2).
8. A stored queue size outside `1, 2, 3, 4, 5, 8, 0` selects no segment (7.1).
9. A `QUEUED` task (starting, not waiting in the queue) shows "Resume" in the details while its
   row shows the pause action (4.4, 5.2).
10. Kind tag colours are the same pastel tones on the light theme (4.2).

Not verified (source reading only, the app was not run):

- Actual glyph fonts and colours of the emoji / symbols on Windows (2.4).
- Pixel sizes: all heights are derived from the M3 line-height rules, not measured.
- Whether Slint 1.18.1's `FlexboxLayout` is usable from the C++ package without an experimental
  flag, and how Slint renders colour emoji with the renderer the prototype uses.
- Mouse long-press on a row starting the selection on desktop (it follows from
  `combinedClickable`; not tried).
