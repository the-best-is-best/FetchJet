# 07 - UI porting spec: Add dialog media part and the small dialogs

Scope: nine Compose files under
`androidUi/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/ui/`:

| File                          | Lines | Composables / functions                                                                                                           |
|-------------------------------|-------|-----------------------------------------------------------------------------------------------------------------------------------|
| `AddDownloadMedia.kt`         | 962   | `MediaQualitiesSelectionCard`, `SubtitlesSelectionCard`, `QuickTimeChip`, `clockText`, `ClipModeOption`, `MediaInspectionSection` |
| `AudioConvertSection.kt`      | 76    | `AudioConvertSection`, `AudioFormatChip` (private)                                                                                |
| `PlaylistScheduleUi.kt`       | 432   | `PlaylistSelectionCard`, `ScheduleStartSection`, `ChoiceChip` (private), `clock` (private), `ConnectionsSection`                  |
| `TimeRangePickerDialog.kt`    | 510   | `TimeRangePickerDialog`, `TimeColon` (private), `TimeFieldColumn` (private)                                                       |
| `DirectoryPickerDialog.kt`    | 445   | `DirectoryPickerDialog`                                                                                                           |
| `DuplicateDownloadDialog.kt`  | 252   | `DuplicateDownloadDialog`                                                                                                         |
| `FileOverwriteDialog.kt`      | 224   | `FileOverwriteDialog`                                                                                                             |
| `MissingFileDialog.kt`        | 49    | `MissingFileDialog`                                                                                                               |
| `RemoveConfirmationDialog.kt` | 90    | `RemoveConfirmationDialog`                                                                                                        |

Everything below was read from the sources on branch `desktop-native` (2026-11-09). Material 3
defaults were checked in the source jars of the exact versions the project uses
(`org.jetbrains.compose.material3:material3:1.12.0-alpha03`, `org.jetbrains.compose.ui:ui:1.12.2`,
see `gradle/libs.versions.toml` lines 14 and 20). Nothing here was measured on a running app; where
a value is derived (not literal in the source) it is marked "derived".

Units: Compose `dp` = Slint logical `px`; Compose `sp` = Slint `px` (desktop font scale is 1).

---

## 1. Facts that apply to every composable in this area

### 1.1 Window and breakpoints

- Desktop window: `rememberWindowState(width = 1100.dp, height = 750.dp)`
  (`desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt:769`). **No minimum window
  size is enforced** (no `minimumSize` anywhere in `desktopApp/`). The user can shrink the window
  to the OS minimum.
- App breakpoints: `isCompact = screenWidth < 680.dp` (`App.kt:195`), `phone = maxWidth < 680.dp`,
  `tablet = maxWidth < 1280.dp` (`App.kt:447-448`). The task list uses 720 dp for wide rows.
- **None of the nine files reads the window width.** The only width-dependent rules are:
    1. the dialog width formulas in 1.2 (they shrink with the window);
    2. `ScheduleStartSection` switches to a two-row layout when *its own* width is `< 420.dp`
       (`PlaylistScheduleUi.kt:296-297`). In the Add dialog that means a window narrower than about
       489 dp (derived: section width = 0.94 x window - 40), so it never happens at 720 dp or more;
    3. chip rows scroll horizontally when they do not fit.

### 1.2 How dialogs are hosted (Compose `Dialog` on desktop)

All dialogs in this area use `androidx.compose.ui.window.Dialog` (directly, or through M3
`AlertDialog`). On Compose Desktop 1.12.2 that is **a layer inside the main window, not an OS
window** (`ui/skikoMain/.../window/Dialog.skiko.kt`):

- Scrim: `Color.Black` at alpha 0.6 over the whole window (`DefaultScrimOpacity = 0.6f`). Stacked
  dialogs each add their own scrim.
- Content is centred in the window.
- Dismiss: primary-button release on the scrim calls `onDismissRequest`; so does Esc
  (`dismissOnBackPress = true`, `dismissOnClickOutside = true` are the defaults and no file
  overrides them).
- Appear animation 200 ms, disappear 100 ms: alpha 0.2 -> 1, scale 0.95 -> 1, translateY 10 dp -> 0
  (`AnimatedLayer*` constants, `isDialogAnimationEnabled = true` by default). The scrim alpha
  follows the same progress.
- A dialog can never be larger than the main window.

Width rule. The custom dialogs pass `DialogProperties(usePlatformDefaultWidth = false)` and then
`Modifier.widthIn(max = M).fillMaxWidth(f)`. Modifier order matters: `widthIn` first caps the
incoming max width to `min(windowWidth, M)`, then `fillMaxWidth(f)` takes the fraction of *that*.
So the real width is `f x min(windowWidth, M)`, **not** `M`:

| Dialog                                          | Source                                                                                                                    | Width (window >= M)  | Height rule                       |
|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|----------------------|-----------------------------------|
| Add dialog (host of the media part)             | `AddDownloadModalDialog.kt:302-306` `widthIn(min=340,max=660).fillMaxWidth(0.94f).heightIn(max=720).padding(vertical=12)` | 620.4 dp             | card <= 696 dp, content scrolls   |
| `TimeRangePickerDialog`                         | `:122-125` `widthIn(max=480).fillMaxWidth(0.92f).padding(vertical=24)`                                                    | 441.6 dp             | wraps content, not scrollable     |
| `DirectoryPickerDialog`                         | `:86-91` `widthIn(max=620).fillMaxWidth(0.94f).heightIn(max=680).padding(vertical=24)`                                    | 582.8 dp             | surface <= 632 dp, not scrollable |
| `DuplicateDownloadDialog`                       | `:59-62` `widthIn(max=520).fillMaxWidth(0.94f).padding(vertical=16)`                                                      | 488.8 dp             | wraps content                     |
| `FileOverwriteDialog`                           | `:51-54` same as Duplicate                                                                                                | 488.8 dp             | wraps content                     |
| `MissingFileDialog`, `RemoveConfirmationDialog` | M3 `AlertDialog`                                                                                                          | 280..560 dp, see 2.8 | wraps content                     |

Stacking order (composition order in `App.kt`, later = on top): Add dialog (`App.kt:556-772`),
Remove (`:776`), Missing file (`:786`), update / when-done / clipboard / pairing / device dialogs,
Duplicate (`:852`), File overwrite (`:862`). `TimeRangePickerDialog` and `DirectoryPickerDialog`
are composed inside `AddDownloadModalDialog` before its own `Dialog` call
(`AddDownloadModalDialog.kt:206-258`); they still appear above the Add dialog because they enter
composition later (when the flag flips). The Duplicate and File overwrite dialogs appear while the
Add dialog is still open underneath.

### 1.3 Theme tokens

`FetchJetTheme` (`theme/Theme.kt`) passes only a colour scheme to `MaterialTheme`; **typography
and shapes are the M3 defaults**. Scheme values (`theme/Color.kt`, `theme/Theme.kt`):

| Token                | Dark      | Light     |
|----------------------|-----------|-----------|
| primary              | `#00E5FF` | `#0284C7` |
| onPrimary            | `#031625` | `#FFFFFF` |
| primaryContainer     | `#0C3D60` | `#E0F2FE` |
| onPrimaryContainer   | `#C7F1FF` | `#0369A1` |
| secondary            | `#818CF8` | `#4F46E5` |
| onSecondary          | `#1E1B4B` | `#FFFFFF` |
| secondaryContainer   | `#2C2F63` | `#EEF2FF` |
| onSecondaryContainer | `#E0E7FF` | `#312E81` |
| tertiary             | `#F43F5E` | `#E11D48` |
| background           | `#080D1A` | `#F8FAFC` |
| surface              | `#0F1728` | `#FFFFFF` |
| surfaceVariant       | `#172238` | `#F1F5F9` |
| onSurface            | `#F1F5F9` | `#0F172A` |
| onSurfaceVariant     | `#94A3B8` | `#475569` |
| outline              | `#334155` | `#94A3B8` |
| outlineVariant       | `#1E2B42` | `#CBD5E1` |
| error                | `#F87171` | `#DC2626` |
| onError              | `#450A0A` | `#FFFFFF` |
| errorContainer       | `#451A1A` | `#FEE2E2` |
| onErrorContainer     | `#FECACA` | `#991B1B` |

Roles the app does **not** override keep the M3 baseline (purple-grey) values. Two of them are
visible in this area:

| Token                   | Dark      | Light     | Used by                          |
|-------------------------|-----------|-----------|----------------------------------|
| surfaceContainerHigh    | `#2B2930` | `#ECE6F0` | container of both `AlertDialog`s |
| surfaceContainerHighest | `#36343B` | `#E6E0E9` | track of an unchecked `Switch`   |

Tonal elevation: `Surface(color = surface, tonalElevation = 6.dp)` is tinted with `surfaceTint`
(= primary, the default) at alpha `(4.5 x ln(7) + 2) / 100 = 0.1076` (`ColorScheme.kt:1154-1158`).
Derived result: **dark `#0D2D3F`, light `#E4F2F9`**. This applies to `TimeRangePickerDialog` and
`DirectoryPickerDialog` only. `Card` never applies tonal elevation in this M3 version
(`Card.kt:88-94` passes only `shadowElevation`), so the Duplicate / Overwrite / Add cards are the
plain `surface` colour.

Status colours used by `DuplicateDownloadDialog` (`theme/Color.kt:74-79`, same in both themes):
`StatusCompleted #10B981`, `StatusCompletedDark #064E3B`, `StatusPaused #F59E0B`,
`StatusPausedDark #78350F`, `StatusConnecting #38BDF8`, `StatusError #F87171`.
(`desktopNative/ui/theme.slint` currently has `completed: #34D399` and `paused: #FBBF24`, which
are not these values.)

Literal colours in this area (not theme dependent): `#0284C7`, `#64748B` (checkbox),
`#451A1A`, `#DC2626`, `#FDE68A`, `#FEE2E2` (engine-not-ready card), `#0284C7`, `#38BDF8`
(inspect button).

### 1.4 Material 3 defaults the look depends on

Typography (`tokens/TypeScaleTokens.kt`; font family = platform default, i.e. Skia's default
system typeface, Segoe UI on Windows; line height style = centre, trim none):

| Role                                     | Size | Line height | Weight | Tracking |
|------------------------------------------|------|-------------|--------|----------|
| bodyLarge (the default `LocalTextStyle`) | 16   | 24          | 400    | 0.5      |
| bodyMedium                               | 14   | 20          | 400    | 0.2      |
| bodySmall                                | 12   | 16          | 400    | 0.4      |
| titleMedium                              | 16   | 24          | 500    | 0.2      |
| labelLarge                               | 14   | 20          | 500    | 0.1      |
| labelMedium                              | 12   | 16          | 500    | 0.5      |
| labelSmall                               | 11   | 16          | 500    | 0.5      |
| headlineSmall                            | 24   | 32          | 400    | 0        |

**Text metric rule (important for matching heights).** `Text(fontSize = N.sp)` without a `style`
keeps everything else from the ambient `LocalTextStyle`:

- in ordinary content (Card, Surface, Column): bodyLarge, so **every line is 24 dp tall** and has
  0.5 sp tracking whatever the font size (a 10 sp caption line is still 24 dp tall);
- inside `Button` / `OutlinedButton` / `TextButton` / `FilterChip` label: labelLarge, so line
  height 20 dp, tracking 0.1 sp and **weight 500 unless `fontWeight` is passed**;
- inside `AlertDialog`: title slot headlineSmall, text slot bodyMedium, buttons labelLarge.

Texts with an explicit `style = MaterialTheme.typography.X` use that role's metrics.

Components (values from the M3 sources of this version):

| Component                                   | Defaults                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|---------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Button`                                    | min size 58 x 40 dp, content padding 24 dp horizontal / 8 dp vertical, default shape fully rounded (pill); container primary, content onPrimary; disabled container onSurface alpha 0.10, disabled content onSurfaceVariant alpha 0.38. `ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled` is false and the app never sets it, so the 36 dp variant is not used.                                                                 |
| `OutlinedButton`                            | same size / padding / shape; transparent container, content primary, border 1 dp outlineVariant; disabled content onSurfaceVariant alpha 0.38, disabled border outlineVariant alpha 0.10                                                                                                                                                                                                                                                       |
| `TextButton`                                | min size 58 x 40 dp, content padding 12 dp horizontal / 8 dp vertical, content primary                                                                                                                                                                                                                                                                                                                                                         |
| Minimum interactive size                    | `LocalMinimumInteractiveComponentSize = 48.dp` on desktop too. `Button`s, `Surface(onClick = ..)`, `Checkbox`, `Switch`, `FilterChip` reserve **at least 48 x 48 dp of layout** and centre their smaller visual inside. A 40 dp button therefore occupies 48 dp of height (4 dp of air above and below). A plain `Surface` + `Modifier.clickable` does **not** get this. A caller `Modifier.size/height` placed on the component wins over it. |
| `Checkbox`                                  | 20 dp box (18 dp square + 2 dp padding), corner radius 2 dp, stroke 2 dp, layout 48 x 48; checked box primary, check mark onPrimary, unchecked border onSurfaceVariant                                                                                                                                                                                                                                                                         |
| `Switch`                                    | track 52 x 32 dp, layout height 48; thumb 16 dp unchecked, 24 dp checked, 28 dp pressed; checked track primary, thumb onPrimary; unchecked track surfaceContainerHighest, thumb outline, 2 dp border outline                                                                                                                                                                                                                                   |
| `FilterChip`                                | height 32 dp (layout 48), label labelLarge, text inset 16 dp each side (8 dp content padding + 8 dp element spacing), default shape 8 dp; unselected: transparent container, label onSurfaceVariant, border 1 dp outlineVariant; selected: container secondaryContainer, label onSecondaryContainer, no border                                                                                                                                 |
| `IconButton`                                | 40 dp; disabled content alpha 0.38 (only if the content does not set its own colour)                                                                                                                                                                                                                                                                                                                                                           |
| `Card`                                      | shape 12 dp, elevation 0, no border unless given                                                                                                                                                                                                                                                                                                                                                                                               |
| `Surface`                                   | clips its content to `shape`; border drawn inside the bounds                                                                                                                                                                                                                                                                                                                                                                                   |
| `HorizontalDivider`                         | 1 dp thick unless `thickness` given                                                                                                                                                                                                                                                                                                                                                                                                            |
| `CircularProgressIndicator` (indeterminate) | colour primary, transparent track, round cap, default 40 dp / 4 dp stroke (both call sites override size and stroke)                                                                                                                                                                                                                                                                                                                           |
| `AlertDialog`                               | see 2.8                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| State layers                                | hover 8 %, focus 10 %, press 10 % of the content colour, drawn by the M3 ripple; `Modifier.clickable` uses the same indication                                                                                                                                                                                                                                                                                                                 |

Keyboard on desktop: every clickable / button is reachable with Tab and activated with Enter or
Space; Esc closes the top dialog. No file in this area registers a key shortcut.

Right-to-left: `App.kt` sets RTL for `ar` and `fa`; Compose mirrors every `Row`, `start/end`
padding and alignment automatically. Sizes and speeds are wrapped in Unicode isolates (`ltr()` in
`utils/Formatters.kt:5`, U+2066 ... U+2069).

### 1.5 Shared helpers used by this area (owned by other specs, summarised here)

- `FetchJetTextField` (`ui/FetchJetTextField.kt:71-124`): single-line field, default height 48 dp,
  shape 12 dp. Container: `surfaceVariant` alpha 0.35 over `surface`; focused `primary` alpha 0.06
  over `surface`. Border: 1 dp `outlineVariant` alpha 0.7; hover `outline`; focused 1.5 dp
  `primary`; error 1.5 dp `error`; colour and width animate 180 ms. Focus / error glow: drop shadow
  radius 10 dp, offset y 2 dp, accent colour, alpha 0.28 (220 ms). Optional label above: 11 sp
  SemiBold, tracking 0.3 sp, padding start 4 / bottom 5, colour onSurfaceVariant -> primary when
  focused. Inner padding: start 10 dp with a leading slot (else 14), end 14 dp (6 with a trailing
  slot); leading slot has 8 dp end padding and alpha 0.75 -> 1 on focus. Placeholder:
  onSurfaceVariant alpha 0.65. Text style default bodyMedium, colour onSurface, cursor = accent.
  Disabled: whole field alpha 0.6.
- `Modifier.clearFocusOnTapOutside()` (`FetchJetTextField.kt:227-242`): a tap (movement below
  touch slop) outside the focused field's bounds clears focus. Used on the DirectoryPicker surface.
- `ProvideSafeClipboard` (`ui/SafeClipboard.kt`): swallows clipboard exceptions. No visual effect.
- `InspectionLoadingBanner(title, subtitle)` (`ui/AddDownloadSections.kt:534-568`): `Surface`
  fillMaxWidth, shape 10 dp, colour primaryContainer alpha 0.3, border 1 dp primary alpha 0.4;
  `Row` padding 14 / 12, centre vertically: `CircularProgressIndicator` 20 dp, stroke 2.5 dp,
  primary; spacer 12; `Column`: title primary Bold 13 sp; spacer 2; subtitle onSurfaceVariant
  11 sp.
- `AppScrollbar` (`ui/AppScrollbar.kt`): only on the Add dialog's outer scroll. 12 dp wide hit
  strip at the end edge, 6 dp thumb, radius 3 dp, onSurface alpha 0.28 (0.55 while hovered or
  scrolling, 180 ms), min thumb 28 px, drag the thumb or click the track. The inner lists of this
  area have **no** scrollbar.

---

## 2. Composables

### 2.1 `AddDownloadMedia.kt`

#### 2.1.1 `MediaInspectionSection` (lines 407-962)

Purpose: the whole media block of the Add dialog. One of seven mutually exclusive states.

Used at `AddDownloadModalDialog.kt:368-409`, inside the dialog's scrolling column, after
`EngineSelector` and `TorrentInspectionSection`, only when the link list is not shown
(`linkList.isEmpty()`). A `Spacer(14.dp)` follows it (`:410`). Content width in the wide layout:
620.4 - 2 x 20 = 580.4 dp.

Signature (exact):

```kotlin
@Composable
internal fun MediaInspectionSection(
    inputUrl: String,
    detectedEngine: EngineType,
    isInspectingMedia: Boolean,
    courseReading: Pair<Int, Int>?,
    inspectedMediaQualities: List<MediaQualityEntry>,
    inspectMediaError: String?,
    isYtDlpInstalled: Boolean,
    ytDlpStatus: String,
    isDownloadingYtDlp: Boolean,
    onDownloadYtDlp: () -> Unit,
    onInspectMedia: () -> Unit,
    onMediaQualitySelectionToggled: (Int, Boolean) -> Unit,
    onSelectAllMediaQualitiesToggled: (Boolean) -> Unit,
    inspectedSubtitles: List<SubtitleTrack>,
    embedSubtitles: Boolean,
    onSubtitleToggled: (String, Boolean) -> Unit,
    onToggleEmbedSubtitles: (Boolean) -> Unit,
    clipStartTime: String,
    clipEndTime: String,
    isClipRangeEnabled: Boolean,
    mediaDurationSeconds: Long,
    onClipStartTimeChanged: (String) -> Unit,
    onClipEndTimeChanged: (String) -> Unit,
    onToggleClipRange: (Boolean) -> Unit,
    clipFastMode: Boolean,
    onToggleClipFastMode: (Boolean) -> Unit,
    inspectedPlaylist: MediaPlaylist?,
    playlistQuality: PlaylistQuality,
    onPlaylistEntryToggled: (Int, Boolean) -> Unit,
    onPlaylistSelectAll: (Boolean) -> Unit,
    onPlaylistQualityChanged: (PlaylistQuality) -> Unit,
    onPlaylistUseSingleVideo: () -> Unit,
    onPlaylistFolderChanged: (String) -> Unit,
    showStartTimePickerState: MutableState<Boolean>,
    showEndTimePickerState: MutableState<Boolean>,
    fullVideoDurationHhMm: String,
    isStartAfterEnd: Boolean,
    isEndExceedingVideo: Boolean,
    isStartExceedingVideo: Boolean,
    clipValidationErrorMessage: String?
)
```

Callback -> event wiring (all in `App.kt:566-705`):

| Callback                                 | Event                                                                |
|------------------------------------------|----------------------------------------------------------------------|
| `onDownloadYtDlp`                        | `DashboardUiEvent.OnDownloadYtDlpClicked`                            |
| `onInspectMedia`                         | `DashboardUiEvent.OnInspectMediaClicked`                             |
| `onMediaQualitySelectionToggled(i, sel)` | `OnMediaQualitySelectionToggled(qualityIndex = i, isSelected = sel)` |
| `onSelectAllMediaQualitiesToggled(all)`  | `OnSelectAllMediaQualitiesToggled(selectAll = all)`                  |
| `onSubtitleToggled(lang, sel)`           | `OnSubtitleToggled(langCode, isSelected)`                            |
| `onToggleEmbedSubtitles(b)`              | `OnToggleEmbedSubtitles(embed)`                                      |
| `onClipStartTimeChanged(t)`              | `OnClipStartTimeChanged(time)`                                       |
| `onClipEndTimeChanged(t)`                | `OnClipEndTimeChanged(time)`                                         |
| `onToggleClipRange(b)`                   | `OnToggleClipRange(enabled)`                                         |
| `onToggleClipFastMode(b)`                | `OnToggleClipFastMode(enabled)`                                      |
| `onPlaylistEntryToggled(i, sel)`         | `OnPlaylistEntryToggled(index, selected)`                            |
| `onPlaylistSelectAll(b)`                 | `OnPlaylistSelectAll(selected)`                                      |
| `onPlaylistQualityChanged(q)`            | `OnPlaylistQualityChanged(quality)`                                  |
| `onPlaylistUseSingleVideo()`             | `OnPlaylistUseSingleVideo`                                           |
| `onPlaylistFolderChanged(s)`             | `OnPlaylistFolderChanged(name)`                                      |

State it reads: `DashboardUiState.inputUrl, detectedEngine, isInspectingMedia, courseReading,
inspectedMediaQualities, inspectMediaError, isYtDlpInstalled, ytDlpStatus, isDownloadingYtDlp,
inspectedSubtitles, embedSubtitles, clipStartTime, clipEndTime, isClipRangeEnabled,
mediaDurationSeconds, clipFastMode, inspectedPlaylist, playlistQuality`
(`presentation/dashboard/DashboardContract.kt:125-203`).

Top level: renders **nothing** unless `detectedEngine == EngineType.MEDIA_STREAM`. Otherwise
`Spacer(12.dp)` and then the first matching state:

| # | Condition (checked in this order)   | What shows                                                                                                                                                               |
|---|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `courseReading != null`             | `InspectionLoadingBanner(readingCourseLectures(first, second), readingCourseLecturesHint)`                                                                               |
| 2 | `isInspectingMedia`                 | `InspectionLoadingBanner(inspectingVideoQualities, inspectingVideoQualitiesHint)`                                                                                        |
| 3 | `inspectMediaError != null`         | error card (below)                                                                                                                                                       |
| 4 | `!isYtDlpInstalled`                 | engine-not-ready card (below)                                                                                                                                            |
| 5 | `inspectedPlaylist != null`         | `PlaylistSelectionCard` (2.3.1) with `onUseSingleVideo = onPlaylistUseSingleVideo` only if `inputUrl.contains("v=")                                                      || inputUrl.contains("youtu.be/")`, else `null` |
| 6 | `inspectedMediaQualities.isEmpty()` | inspect button (below)                                                                                                                                                   |
| 7 | otherwise                           | `MediaQualitiesSelectionCard`; if `inspectedSubtitles.isNotEmpty()`: spacer 10 + `SubtitlesSelectionCard`; if `mediaDurationSeconds > 0L`: spacer 10 + clip card (2.1.6) |

Auto inspection lives in the parent (`AddDownloadModalDialog.kt:261-278`,
`LaunchedEffect(inputUrl, detectedEngine)`): when `inputUrl` is not blank, the engine is
`MEDIA_STREAM`, `inspectedMediaQualities` is empty, `inspectedPlaylist == null`,
`!isInspectingMedia`, `inspectMediaError == null` and `isYtDlpInstalled`, it sends
`OnInspectMediaClicked`. It re-runs only when the URL or the engine changes (so state 6, the
manual button, is what the user sees when the engine became ready later). This belongs in the
presenter.

State 3, error card (lines 469-514):

- `Card` fillMaxWidth, shape 10 dp, container `errorContainer` alpha 0.25, border 1 dp `error`
  alpha 0.6.
- `Column` padding 12 dp:
    - `Row` centre vertically: `Text("⚠️", 16 sp)`; spacer 6; `Text(videoInspectionFailed)` colour
      `error`, Bold, 13 sp.
    - spacer 4; `Text(inspectMediaError)` onSurfaceVariant 11 sp, wraps freely.
    - spacer 10; `Button(onClick = onInspectMedia)` aligned to the end, shape 8 dp, container
      `error`: `Text("🔄 " + addRetryInspection)` 12 sp SemiBold `onError`. Default padding 24 / 8,
      40 dp tall (48 layout).

State 4, engine-not-ready card (lines 516-556):

- `preparing = isDownloadingYtDlp || ytDlpStatus.startsWith("Preparing", ignoreCase = true)`.
- `Card` fillMaxWidth, shape 10 dp, container literal `#451A1A`, border 1 dp literal `#DC2626`
  (same in the light theme).
- `Column` padding 12 dp:
    - title, Bold 13 sp: preparing -> `"⏳ " + mediaEngineStarting`, colour `#FDE68A`; else
      `"⚠️ " + mediaEngineNotReady`, colour `error`.
    - spacer 2; `Text(localizeStep(ytDlpStatus).ifBlank { mediaEngineRequiresYtDlp })` colour
      `#FEE2E2`, 11 sp.
    - spacer 8; `OutlinedButton(onClick = onDownloadYtDlp, enabled = !preparing)` fillMaxWidth,
      shape 10 dp, default colours and border. Content when preparing:
      `CircularProgressIndicator` 14 dp, stroke 2 dp; spacer 8; `Text(preparingEngine)` 12 sp.
      Otherwise `Text(startMediaEngine)` 12 sp.
- `OnDownloadYtDlpClicked` sets `isDownloadingYtDlp = true` and installs the tools
  (`DashboardSettingsEvents.kt:163`). While the Add dialog is open and the engine is not ready the
  view model polls the status every 2 s for up to 5 minutes (`DashboardTools.kt:35-49`).

State 6, inspect button (lines 567-593):

- `isInputAudio = inputUrl.substringBefore('?').substringAfterLast('.', "").lowercase() in
  listOf("mp3","m4a","flac","wav","aac","ogg","opus")`.
- `OutlinedButton(onClick = onInspectMedia, enabled = inputUrl.isNotBlank())` fillMaxWidth,
  shape 10 dp, border 1 dp literal `#0284C7` (kept when disabled, because it is passed
  explicitly), content colour literal `#38BDF8`.
- `Text` Bold 13 sp: `"🎵 " + inspectAudioFormats` if `isInputAudio`, else
  `"🎬 " + inspectVideoQuality`.

UI-local state: none of its own. It receives the two picker flags as `MutableState<Boolean>` and
sets them to `true` from the start / end time cards.

#### 2.1.2 `MediaQualitiesSelectionCard` (lines 63-208)

```kotlin
@Composable
internal fun MediaQualitiesSelectionCard(
    qualities: List<MediaQualityEntry>,
    onQualitySelectionToggled: (Int, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit
)
```

Used only by `MediaInspectionSection` state 7 (`:595`). Model (`domain/model/MediaQualityEntry.kt`): `index, qualityLabel, directUrl, format, size = -1,
isSelected = true, formatId, originalUrl, videoTitle, durationSeconds = 0`.

Layout:

- `Card` fillMaxWidth, shape 12 dp, container `surfaceVariant` alpha 0.35, border 1 dp
  `outlineVariant` alpha 0.6.
- `Column` padding 12 dp:
    1. Header `Row` fillMaxWidth, SpaceBetween, centre vertically (48 dp tall because of the text
       buttons):
        - title `Text`, 13 sp Bold onSurface, `weight(1f, fill = false)`, 1 line, ellipsis. Text =
          `"$titleIcon $videoTitle"` when a title exists, else `"$titleIcon $defaultTitle"`, where
          `videoTitle = qualities.firstOrNull { !it.videoTitle.isNullOrBlank() }?.videoTitle`,
          `isAudio = qualities.isAllAudio()`, `titleIcon = if (isAudio) "🎵" else "🎬"`,
          `defaultTitle = if (isAudio) availableAudioFormats else availableQualities`.
        - `Row` spacing 4 dp with two `TextButton`s, content padding 6 dp horizontal / 2 dp vertical
          (the 58 x 40 minimum still applies): `addSelectAll` 11 sp colour primary ->
          `onSelectAll(true)`; `addClearSelection` 11 sp colour onSurfaceVariant ->
          `onSelectAll(false)`.
    2. `Text(selectedOfAvailable(qualities.count { it.isSelected }, qualities.size))` 11 sp
       SemiBold primary.
    3. spacer 8.
    4. List box: `Box` fillMaxWidth, `heightIn(max = 140.dp)`, clip 8 dp, background `surface`,
       padding 4 dp, containing `LazyColumn(fillMaxSize, spacing 2 dp)`. Because of `fillMaxSize`
       the box is **always 140 dp tall**, whatever the number of rows (derived). Scrolls vertically
       with the wheel; no scrollbar; nested inside the dialog's own scroll.
    5. One row per quality (`key = index`): `Row` fillMaxWidth, clip 6 dp, background
       `primaryContainer` alpha 0.4 when selected else transparent, clickable, padding 6 dp
       horizontal / 4 dp vertical, centre vertically. Row height 56 dp (derived: 4 + 48 + 4).
        - `Checkbox(checked = isSelected)`, colours `checkedColor = #0284C7`,
          `uncheckedColor = #64748B` (literal, both themes), check mark onPrimary.
        - spacer 6.
        - `Column(weight 1)`: `Text(qualityLabel)` 12 sp SemiBold onSurface; then
          `Text(qualityFormatLine(format.uppercase(), "$sizeText$durText"))` 10 sp onSurfaceVariant,
          with `sizeText = if (size > 0) " • ${formatSizeDisplay(size)}" else ""` and
          `durText = if (durationSeconds > 0) " • ⏱️ ${formatSecondsToDisplayFriendly(durationSeconds)}" else ""`.
          No max lines on either text.

Events: row click -> `onQualitySelectionToggled(index, !isSelected)`; checkbox ->
`onQualitySelectionToggled(index, checked)`. Several qualities may be selected at once (it is a
multi-select; each selected quality becomes its own download). The view model also recomputes the
suggested file name after a toggle (`DashboardAddDialogEvents.kt:211-219`).

No UI-local state.

#### 2.1.3 `SubtitlesSelectionCard` (lines 210-326)

```kotlin
@Composable
internal fun SubtitlesSelectionCard(
    subtitles: List<SubtitleTrack>,
    embedSubtitles: Boolean,
    onSubtitleToggled: (String, Boolean) -> Unit,
    onToggleEmbedSubtitles: (Boolean) -> Unit
)
```

Model (`domain/model/SubtitleTrack.kt`): `langCode, langName, ext, url, isAuto = false,
isSelected = false`. Used by state 7 when `inspectedSubtitles.isNotEmpty()` (`:601-609`).

- `Card` as in 2.1.2 (12 dp, `surfaceVariant` 0.35, border `outlineVariant` 0.6), `Column`
  padding 12 dp:
    1. `Text("💬 " + subtitlesTitle)` 13 sp Bold onSurface.
    2. spacer 8.
    3. List box as in 2.1.2 but `heightIn(max = 120.dp)` (always 120 dp tall), key = `langCode`.
       Row: same container rules (selected `primaryContainer` 0.4, clip 6, padding 6 / 4,
       clickable). Content: `Checkbox` (same literal colours); spacer 6; `Text(langName)` 12 sp
       SemiBold onSurface, `weight(1f, fill = false)`, 1 line, ellipsis; if `isAuto`: spacer 6 +
       badge `Surface` shape 4 dp, colour `surfaceVariant` alpha 0.7, `Text(subtitlesAuto)` 9 sp
       onSurfaceVariant, padding 4 / 1.
    4. Only if `subtitles.any { it.isSelected }`: spacer 10; `Row` fillMaxWidth, spacing 8 dp, two
       `ClipModeOption`s of equal weight with `lines = emptyList()`:
       `selected = embedSubtitles`, title `subtitlesEmbedInVideo`, click
       `onToggleEmbedSubtitles(true)`; `selected = !embedSubtitles`, title `subtitlesSeparateFile`,
       click `onToggleEmbedSubtitles(false)`.

Events: row click `onSubtitleToggled(langCode, !isSelected)`, checkbox
`onSubtitleToggled(langCode, checked)`. No UI-local state.

#### 2.1.4 `QuickTimeChip` (lines 328-347)

```kotlin
@Composable
internal fun QuickTimeChip(label: String, onClick: () -> Unit)
```

`Surface(onClick)` shape 6 dp, colour `surfaceVariant` alpha 0.6, border 1 dp `outlineVariant`
alpha 0.5; `Text(label)` 10 sp Medium onSurface, padding 8 dp horizontal / 4 dp vertical. Visual
height 32 dp (derived: 24 line + 8); layout at least 48 x 48 dp (clickable `Surface`). Used only in
the clip card.

#### 2.1.5 `ClipModeOption` (lines 359-405) and `clockText` (lines 349-357)

```kotlin
@Composable
internal fun ClipModeOption(
    selected: Boolean,
    title: String,
    lines: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)

internal fun clockText(totalSeconds: Long): String
```

A radio-style card. Used four times: subtitles embed / separate (2.1.3) and the two clip modes
(2.1.6).

- `accent = primary`.
- `Surface` shape 10 dp; colour `accent` alpha 0.12 when selected else `surfaceVariant` alpha
  0.25; border 2 dp `accent` when selected else 1 dp `outlineVariant` alpha 0.5.
  `modifier.clickable(onClick)` is applied outside the surface, so the hover / press layer is a
  plain rectangle (not clipped to the 10 dp corners).
- `Column` padding 10 dp horizontal / 8 dp vertical:
    - `Row` centre vertically: glyph `Text(if (selected) "◉" else "○")` 14 sp, colour `accent` /
      onSurfaceVariant; spacer 6; `Text(title)` 12 sp Bold, colour `accent` when selected else
      onSurface.
    - spacer 4.
    - one `Text("• $line")` per line, 10 sp onSurfaceVariant (each line 24 dp tall, see 1.4).
- In a `Row` with `weight(1f)` the two cards have equal width but **not** equal height (no
  `IntrinsicSize`): the card with fewer lines is shorter and top-aligned.

`clockText`: `t = max(totalSeconds, 0)`; `h = t / 3600`, `m = (t % 3600) / 60`, `s = t % 60`;
returns `"$h:${m pad2}:${s pad2}"` when `h > 0`, else `"$m:${s pad2}"` (minutes are not padded:
`42:44`, `1:05:09`, `0:07`).

#### 2.1.6 Clip range card (inside `MediaInspectionSection`, lines 611-958)

Shown in state 7 when `mediaDurationSeconds > 0L`.

Derived values computed by the parent (`AddDownloadModalDialog.kt:170-204`) and passed in:

```
fullVideoDurationHhMm = if (mediaDurationSeconds > 0) formatSecondsToHhMm(mediaDurationSeconds) else "00:10:00"
currentStartSec       = parseHhMmToSeconds(clipStartTime.ifBlank { "00:00" })
currentEndSec         = parseHhMmToSeconds(clipEndTime.ifBlank { fullVideoDurationHhMm })
isStartAfterEnd       = isClipRangeEnabled && currentStartSec >= currentEndSec
isEndExceedingVideo   = isClipRangeEnabled && mediaDurationSeconds > 0 && currentEndSec > mediaDurationSeconds
isStartExceedingVideo = isClipRangeEnabled && mediaDurationSeconds > 0 && currentStartSec >= mediaDurationSeconds
isClipRangeValid      = !isClipRangeEnabled || (!isStartAfterEnd && !isEndExceedingVideo && !isStartExceedingVideo)
clipValidationErrorMessage = when {
    !isClipRangeEnabled   -> null
    isStartAfterEnd       -> clipStartAfterEnd(clipStartTime.ifBlank { "00:00" }, clipEndTime.ifBlank { fullVideoDurationHhMm })
    isEndExceedingVideo   -> clipEndExceedsVideo(clipEndTime.ifBlank { fullVideoDurationHhMm }, fullVideoDurationHhMm)
    isStartExceedingVideo -> clipStartExceedsVideo(fullVideoDurationHhMm)
    else -> null
}
```

`isClipRangeValid` also gates the dialog's Start button (`canStartDownload`, `:293-294`).

Local values in the card: `selectedQuality = inspectedMediaQualities.firstOrNull { it.isSelected }`,
`isAnyAudio = selectedQuality?.isAudioOnly == true`
(`isAudioOnly` = label contains "Audio" ignoring case, or format in
mp3 / m4a / flac / wav / aac / ogg / opus).

Layout:

- `Card` fillMaxWidth, shape 12 dp, `surfaceVariant` alpha 0.35, border 1 dp `outlineVariant`
  alpha 0.6; `Column` padding 12 dp.
- Header `Row` fillMaxWidth, SpaceBetween, centre vertically, **whole row clickable** ->
  `onToggleClipRange(!isClipRangeEnabled)`:
    - left `Row`: `Text("✂️", 16 sp)`; spacer 8; `Column`: `Text(clipSectionTitle(isAnyAudio))`
      13 sp Bold onSurface; `Text(clipSectionHint)` 10 sp onSurfaceVariant.
    - right: `Switch(checked = isClipRangeEnabled, onCheckedChange = onToggleClipRange)` with
      checked thumb onPrimary, checked track primary (the defaults).
- Everything below only when `isClipRangeEnabled`:
    1. spacer 10.
    2. Duration banner (the inner `mediaDurationSeconds > 0` test is always true here): `Surface`
       fillMaxWidth, shape 8 dp, `primaryContainer` alpha 0.45; `Row` padding 10 / 6;
       `Text("⏱️ " + mediaDurationFullRange(isAnyAudio, clockText(mediaDurationSeconds),
       formatSecondsToDisplayFriendly(mediaDurationSeconds)))`, style labelSmall, SemiBold,
       primary. Then spacer 8.
    3. `Row` fillMaxWidth, spacing 10 dp, two equal-weight time cards:
        - start card: `Surface` clickable -> `showStartTimePicker = true`; shape 10 dp. Error state
          `isStartHasError = isStartAfterEnd || isStartExceedingVideo`: border 1.5 dp `error` and
          colour `errorContainer` alpha 0.15; normal: border 1 dp `primary` alpha 0.5, colour
          `surface`. `Column` padding 10 dp: `Row` SpaceBetween: `Text(clipStartTimeFrom)` 11 sp
          SemiBold (`error` in error state, else onSurfaceVariant) and `Text("🕒", 12 sp)`; spacer 4;
          `Text(startDisplay)` 20 sp Bold (`error` / `primary`), `startDisplay =
       clipStartTime.ifBlank { "00:00" }`; `Text(formatSecondsToDisplayFriendly(
       parseHhMmToSeconds(startDisplay)))` 10 sp onSurfaceVariant.
        - end card: identical with `isEndHasError = isStartAfterEnd || isEndExceedingVideo`, label
          `clipEndTimeTo`, `endDisplay = clipEndTime.ifBlank { fullVideoDurationHhMm }`, click ->
          `showEndTimePicker = true`.
        - As in `ClipModeOption`, `clickable` is outside the surface (rectangular hover layer).
    4. If `clipValidationErrorMessage != null`: spacer 8; `Surface` fillMaxWidth, shape 8 dp,
       `errorContainer` alpha 0.35, border 1 dp `error` alpha 0.5; `Row` padding 10 / 6:
       `Text("⚠️", 12 sp)`, spacer 6, `Text(message)` 11 sp Medium `error`.
    5. spacer 8; quick chips `Row` fillMaxWidth, `horizontalScroll`, spacing 6 dp (`QuickTimeChip`
       s). With `d = mediaDurationSeconds`:

       | Chip | Shown when | Click |
            |---|---|---|
       | `fullMediaChip(isAnyAudio, clockText(d))` | `d > 0` | `onClipStartTimeChanged("00:00")`, `onClipEndTimeChanged(fullVideoDurationHhMm)` |
       | `clipFirst5Min` | `d <= 0 || d >= 300` | start `"00:00:00"`, end `"00:05:00"` |
       | `clipFirst15Min` | `d <= 0 || d >= 900` | start `"00:00:00"`, end `"00:15:00"` |
       | `clipFirst30Min` | `d <= 0 || d >= 1800` | start `"00:00:00"`, end `"00:30:00"` |
       | `clipFirst1Hour` | `d <= 0 || d >= 3600` | start `"00:00:00"`, end `"01:00:00"` |

    6. spacer 12; `Text(clipModeTitle)` 12 sp SemiBold onSurface; spacer 6.
    7.
    `fullSizeText = selectedQuality?.size?.takeIf { it > 0 }?.let { " (${formatBytes(it)})" } ?: ""`.
    `Row` fillMaxWidth, spacing 8 dp, two equal-weight `ClipModeOption`s:
        - `selected = !clipFastMode`, title `"✂️ " + clipOnlyTitle`, lines `clipOnlyLine1`,
          `clipOnlyLine2`, `clipOnlyLine3`, click `onToggleClipFastMode(false)`.
        - `selected = clipFastMode`, title `"⚡ " + fullThenCutTitle(isAnyAudio)`, lines
          `fullThenCutLine1(isAnyAudio, fullSizeText)`, `fullThenCutLine2`, `fullThenCutLine3`,
          `fullThenCutLine4`, click `onToggleClipFastMode(true)`.

View-model behaviour to keep (`DashboardAddDialogEvents.kt:318-333`): `OnToggleClipRange(true)` is
ignored when there are no inspected qualities or the duration is not known; on enabling, a blank
or `"00:00"` start becomes `"00:00"` and a blank or `"00:00"` end becomes the full duration
(`formatSecondsToHhMm(duration)`, or `"00:10:00"`).

Time pickers opened from the two cards (`AddDownloadModalDialog.kt:206-244`):

|                      | Start picker                                                                                                                                                                                              | End picker                                      |
|----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| title                | `"⏱️ " + setStartTimeTitle`                                                                                                                                                                                | `"⏱️ " + setEndTimeTitle`                        |
| `initialHhMm`        | `clipStartTime.ifBlank { "00:00" }`                                                                                                                                                                       | `clipEndTime.ifBlank { fullVideoDurationHhMm }` |
| `maxDurationSeconds` | `maxForStart.coerceAtLeast(0)` where `maxForStart = if (currentEndSec > 0) (if (mediaDurationSeconds > 0) min(mediaDurationSeconds, currentEndSec - 1) else currentEndSec - 1) else mediaDurationSeconds` | `mediaDurationSeconds`                          |
| `minTimeSeconds`     | `0`                                                                                                                                                                                                       | `currentStartSec`                               |
| on select            | `onClipStartTimeChanged(selected)`, close                                                                                                                                                                 | `onClipEndTimeChanged(selected)`, close         |

### 2.2 `AudioConvertSection.kt`

```kotlin
@Composable
internal fun AudioConvertSection(
    selected: String?,
    nativeFormat: String?,
    forAll: Boolean,
    onSelected: (String?) -> Unit
)
```

Purpose: pick the audio format the download is converted to. Emits into the Add dialog's column (it
has no container of its own).

Where used: `AddDownloadModalDialog.kt:447-452`, after `ScheduleStartSection` and
`ConnectionsSection`, before the file-name field. The parent decides visibility and arguments
(`:430-446`):

```
audioConvertForAll = showsLinkList || inspectedPlaylist != null
showsAudioConvert = when {
    showsLinkList -> linkList.any { e -> e.isSelected && e.mediaQualities.firstOrNull { it.isSelected }?.isAudioOnly == true }
    inspectedPlaylist != null -> playlistQuality == PlaylistQuality.AUDIO
    else -> detectedEngine == MEDIA_STREAM && !isInspectingMedia &&
            inspectedMediaQualities.firstOrNull { it.isSelected }?.isAudioOnly == true
}
nativeAudioFormat = when {
    showsLinkList -> linkList.filter { it.isSelected }
        .mapNotNull { e -> e.mediaQualities.firstOrNull { it.isSelected && it.isAudioOnly }?.format }
        .distinct().singleOrNull()
    inspectedPlaylist != null -> playlistQuality.extension
    else -> inspectedMediaQualities.firstOrNull { it.isSelected }?.format
}
```

`selected = state.audioConvertFormat`; `onSelected` ->
`DashboardUiEvent.OnAudioConvertFormatSelected(format)`
(the view model also refreshes the suggested file name, `DashboardAddDialogEvents.kt:245-248`).

Layout:

- `native = nativeFormat?.lowercase()?.ifBlank { null }`.
- `Text(if (forAll) audioConvertAllTo else audioConvertTo)` 12 sp SemiBold onSurface.
- spacer 6.
- `FlowRow`, horizontal spacing 6 dp, vertical spacing 6 dp (wraps to more lines):
    - first chip: label `native?.uppercase() ?: audioOriginal`, selected when
      `selected == null || selected == native`, click `onSelected(null)`;
    - then one chip per `AUDIO_CONVERT_FORMATS.filter { it != native }`
      (`AUDIO_CONVERT_FORMATS = listOf("mp3","m4a","opus","flac","wav","ogg")`,
      `feature/media_extractor/MediaAudioConvert.kt:8`), label `f.uppercase()`, selected when
      `selected == f`, click `onSelected(f)`.
- spacer 14 (the section brings its own bottom gap).

`AudioFormatChip(label, isSelected, onClick)` (private, lines 54-76): `Surface(onClick)` shape
10 dp; selected: colour and border `primary`, text Bold `onPrimary`; unselected: colour
`surfaceVariant` alpha 0.5, border 1 dp `outlineVariant` alpha 0.6, text Medium onSurface. Text
12 sp, padding 12 dp horizontal / 8 dp vertical. Visual height 40 dp; layout at least 48 x 48 dp,
so the visible gap between two wrapped lines is 14 dp (derived).

No UI-local state.

### 2.3 `PlaylistScheduleUi.kt`

#### 2.3.1 `PlaylistSelectionCard` (lines 51-195)

```kotlin
@Composable
fun PlaylistSelectionCard(
    playlist: MediaPlaylist,
    quality: PlaylistQuality,
    onEntryToggled: (Int, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onQualityChanged: (PlaylistQuality) -> Unit,
    onFolderChanged: (String) -> Unit,
    onUseSingleVideo: (() -> Unit)?
)
```

Used only by `MediaInspectionSection` state 5 (`AddDownloadMedia.kt:558-566`).

Models (`feature/media_extractor/Playlist.kt`):
`MediaPlaylist(title, entries, fromBrowser = false, folderName: String? = null)` (folder name:
`null` = use the title, `""` = no folder);
`PlaylistEntry(index, title, url, durationSeconds = 0, isSelected = true, titled = true)`;
`enum PlaylistQuality { BEST, P1080, P720, P480, P360, AUDIO }` with `label` =
`sysPlaylistQualityBest`
("Best"), "1080p", "720p", "480p", "360p", `sysPlaylistQualityAudioOnly` ("Audio only") and
`extension` = "mp4" (all video ones) / "m4a" (AUDIO).

Local values: `selectedCount = entries.count { it.isSelected }`,
`allSelected = selectedCount == entries.size`.

Layout:

- `Card` fillMaxWidth, shape 12 dp, `surfaceVariant` alpha 0.35, border 1 dp `outlineVariant`
  alpha 0.6; `Column` padding 12 dp.
- Header `Row` centre vertically: `Text("📃", 16 sp)`; spacer 8; `Column(weight 1)`:
  `Text(playlist.title)` 13 sp Bold (content colour, i.e. onSurface), max 2 lines, ellipsis;
  `Text(playlistSummary(entries.size, selectedCount))` 11 sp onSurfaceVariant. Then
  `TextButton { Text(if (allSelected) selectNone else selectAll, 12 sp) }` ->
  `onSelectAll(!allSelected)`.
- spacer 8; `FetchJetTextField(value = playlist.folderName ?: playlist.title,
  onValueChange = onFolderChanged, label = playlistFolder, leading = Text("📁", 14 sp))`
  (48 dp, see 1.5). Then `Text(playlistFolderHint)` 10 sp onSurfaceVariant, top padding 4 dp.
- Only when `!playlist.fromBrowser`: spacer 8; `Text(qualityForAllVideos)` 12 sp SemiBold;
  spacer 6; `Row` fillMaxWidth, `horizontalScroll`, spacing 6 dp: one `ChoiceChip(q.label,
  selected = q == quality)` per `PlaylistQuality.entries` -> `onQualityChanged(q)`; then
  `Text(qualityForAllVideosHint)` 10 sp onSurfaceVariant, top padding 4 dp.
- spacer 8; list `Surface` fillMaxWidth, shape 8 dp, colour `surface` alpha 0.6, containing
  `LazyColumn(heightIn(max = 260.dp))` (wraps its content up to 260 dp, then scrolls; no
  scrollbar), `key = entry.index`. Row: fillMaxWidth, clickable ->
  `onEntryToggled(entry.index, !entry.isSelected)`, padding 4 dp horizontal, centre vertically,
  48 dp tall:
    - `Checkbox(checked = entry.isSelected)` default colours -> `onEntryToggled(entry.index, it)`;
    - when `!fromBrowser`: `Text("${entry.index + 1}.")` 11 sp onSurfaceVariant, fixed width 30 dp;
    - `Text(if (fromBrowser) "${entry.title}.mp4" else entry.title)` 12 sp, 1 line, ellipsis,
      weight 1;
    - when `entry.durationSeconds > 0`: `Text(clock(durationSeconds))` 11 sp onSurfaceVariant,
      padding start 6 / end 4.
- When `onUseSingleVideo != null`: `TextButton` aligned to the end,
  `Text(downloadOnlyThisVideo, 12 sp)` -> `onUseSingleVideo()`.

`clock(sec)` (private, lines 403-409) is the same formula as `clockText` without the clamp to 0.

View-model behaviour: `OnPlaylistUseSingleVideo` remembers the URL as a single video, clears
`inspectedPlaylist` and runs `inspectMedia()` (`DashboardAddDialogEvents.kt:305-309`).
No UI-local state.

#### 2.3.2 `ScheduleStartSection` (lines 197-376)

```kotlin
@Composable
fun ScheduleStartSection(scheduleStartMs: Long, onScheduleChanged: (Long) -> Unit)
```

Purpose: when the download (s) start. Emits into the Add dialog's column
(`AddDownloadModalDialog.kt:414-418`, always shown, followed by `Spacer(14.dp)`).
`onScheduleChanged` -> `DashboardUiEvent.OnScheduleChanged(epochMs)`; `0` = now.

UI-local state (plain `remember`, so it lives as long as the Add dialog is open and resets when
it is closed):

```kotlin
var showTimeFields by remember { mutableStateOf(false) }
var hourText by remember { mutableStateOf("09") }
var minuteText by remember { mutableStateOf("00") }
var isPm by remember { mutableStateOf(false) }
var dayOffset by remember { mutableStateOf(0) }
var timeError by remember { mutableStateOf<String?>(null) }
```

Layout:

1. `Text(whenToStart)` 12 sp SemiBold onSurface; spacer 6.
2. `Row` fillMaxWidth, `horizontalScroll`, spacing 6 dp, four `ChoiceChip`s:

   | Label | `selected` | Click |
      |---|---|---|
   | `scheduleNow` | `scheduleStartMs == 0L && !showTimeFields` | `showTimeFields = false; onScheduleChanged(0L)` |
   | `scheduleIn30Min` | always `false` | `showTimeFields = false; onScheduleChanged(currentEpochMs() + 30 * 60_000L)` |
   | `scheduleIn1Hour` | always `false` | `showTimeFields = false; onScheduleChanged(currentEpochMs() + 60 * 60_000L)` |
   | `scheduleAtTime` | `showTimeFields` | if the fields are hidden: prefill from the current local time (below); then `showTimeFields = true` |

   Prefill:
   `minuteOfDay = ((currentEpochMs() + localUtcOffsetMs()).mod(86_400_000) / 60_000).toInt()`,
   `h24 = minuteOfDay / 60`, `hourText = pad2(if (h24 % 12 == 0) 12 else h24 % 12)`,
   `minuteText = pad2(minuteOfDay % 60)`, `isPm = h24 >= 12`, `dayOffset = 0`, `timeError = null`.
3. Only when `showTimeFields`:
    - spacer 8; `Text(scheduleDayLabel)` 11 sp onSurfaceVariant; spacer 4.
    - `Row` fillMaxWidth, `horizontalScroll`, spacing 6 dp, `ChoiceChip`s
      `0 -> scheduleToday`, `1 -> scheduleTomorrow`, `2 -> scheduleIn2Days`,
      `3 -> scheduleIn3Days`, `7 -> scheduleInAWeek`; `selected = dayOffset == d`; click
      `dayOffset = d`.
    - spacer 8; `BoxWithConstraints(fillMaxWidth)`, `compact = maxWidth < 420.dp`; `Column`:
        - `Row` centre vertically:
            - hour `FetchJetTextField(value = hourText, label = scheduleHourLabel, centered = true,
         textStyle = titleMedium, number keyboard)`, width 80 dp (72 when compact);
              `onValueChange = { hourText = it.filter(Char::isDigit).take(2) }`;
            - `Text(" : ")` Bold (16 sp, bodyLarge);
            - minute field, same, label `scheduleMinuteLabel`, state `minuteText`;
            - spacer 8; `ChoiceChip(scheduleAm, selected = !isPm)` -> `isPm = false`; spacer 4;
              `ChoiceChip(schedulePm, selected = isPm)` -> `isPm = true`;
            - when not compact: spacer 10; `Button(onClick = setTime, shape 10 dp)`
              `Text(scheduleSet, 12 sp)`.
            - The row is centre-aligned on the whole field (label + box), so the chips and the
              button
              sit about 14 dp above the centre of the input boxes (derived: label line 24 + 5, box
              48).
        - when compact: spacer 8; `Button(onClick = setTime, shape 10 dp)` fillMaxWidth, height
          40 dp, `Text(scheduleSetTime, 12 sp)`.
    - `timeError?.let { Text(it) }` 11 sp `error`, top padding 4 dp.
4. When `scheduleStartMs > 0L`: spacer 6; `Surface` fillMaxWidth, shape 8 dp,
   `primaryContainer` alpha 0.45;
   `Text("🕑 " + scheduleStartsAt(formatLocalSchedule(scheduleStartMs)))`
   11 sp SemiBold primary, padding 10 / 6.

`setTime`:

```
h12 = hourText.toIntOrNull()?.coerceIn(1, 12) ?: 12
m   = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
hourText = pad2(h12); minuteText = pad2(m)
h24 = (h12 % 12) + if (isPm) 12 else 0
at  = localTimeOnDayEpochMs(dayOffset, h24, m)
if (at <= currentEpochMs()) timeError = scheduleTimePassed
else { timeError = null; onScheduleChanged(at) }
```

Helpers it calls in sharedLogic (`core/util/LocalClock.kt`): `currentEpochMs()`,
`localUtcOffsetMs()` (expect/actual), `localTimeOnDayEpochMs(dayOffset, hour, minute)`,
`formatLocalSchedule(epochMs)`. The last one returns **English, not localised** text:
`"today 1:49 PM"`, `"tomorrow 2:00 AM"`, `"in 3 days, 8:00 AM"`.

Things to keep: the 30 min / 1 hour chips are never highlighted (the banner in step 4 is the
feedback); choosing "Now" hides the time fields; the banner stays visible while the time fields
are open and shows the last accepted time.

#### 2.3.3 `ChoiceChip` (private, lines 378-401)

```kotlin
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit)
```

`accent = primary`. `Surface` shape 16 dp; selected: colour `accent` alpha 0.18, border 1 dp
`accent`, text Bold `accent`; unselected: colour `surface` alpha 0.6, border 1 dp
`outlineVariant` alpha 0.6, text Normal onSurface. Text 12 sp inside a `Box` with padding 12 dp
horizontal / 6 dp vertical. Height 36 dp (derived: 24 + 12); no 48 dp minimum (plain surface +
`Modifier.clickable`, rectangular hover layer). Used by the playlist quality row, the schedule
rows, AM / PM, and the connections row.

#### 2.3.4 `ConnectionsSection` (lines 411-432)

```kotlin
@Composable
fun ConnectionsSection(selected: Int, appDefault: Int, onChanged: (Int) -> Unit)
```

Where used: `AddDownloadModalDialog.kt:421-428`, only when
`detectedEngine == EngineType.HTTP_DIRECT && !showsLinkList`, followed by `Spacer(14.dp)`.
`selected = state.selectedConnections`, `appDefault = state.defaultConnections`,
`onChanged` -> `DashboardUiEvent.OnConnectionsChanged(connections)`.

Layout: `Text(addConnectionsTitle)` 12 sp SemiBold onSurface; spacer 6; `Row` fillMaxWidth,
`horizontalScroll`, spacing 6 dp of `ChoiceChip`s: first
`if (appDefault > 0) connectionsAutoWithDefault(appDefault) else connectionsAuto`, selected when
`selected == 0`, click `onChanged(0)`; then `"1"`, `"4"`, `"8"`, `"16"`, `"32"`, selected when
`selected == n`, click `onChanged(n)`. No UI-local state.

### 2.4 `TimeRangePickerDialog.kt`

```kotlin
@Composable
fun TimeRangePickerDialog(
    title: String,
    initialHhMm: String,
    maxDurationSeconds: Long = 0L,
    minTimeSeconds: Long = 0L,
    onTimeSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
)
```

Purpose: pick a clip start or end time as `HH:MM:SS`. Used twice
(`AddDownloadModalDialog.kt:215-227` and `:231-243`, arguments in the table at the end of 2.1.6).
It does not talk to sharedLogic; it only returns the string.

UI-local state and derived values (lines 63-115):

```
parts = initialHhMm.split(":")
hours   = remember(parts[0].toInt or 0, at least 0)
minutes = remember(parts[1].toInt or 0, in 0..59)
seconds = remember(parts[2].toInt or 0, in 0..59)

formatted           = "${hours pad2}:${minutes pad2}:${seconds pad2}"
currentTotalSeconds = hours * 3600 + minutes * 60 + seconds
maxTypedSeconds     = 359_999            (99:59:59)
applyTotalSeconds(total): t = total.coerceIn(0, 359_999); hours = t / 3600; minutes = (t % 3600) / 60; seconds = t % 60

isExceedingMax = maxDurationSeconds > 0 && currentTotalSeconds > maxDurationSeconds
isBelowMin     = minTimeSeconds > 0 && currentTotalSeconds <= minTimeSeconds
isValid        = !isExceedingMax && !isBelowMin
validationErrorMessage = when {
    isExceedingMax -> pickerExceedsVideo(formatSecondsToHhMm(maxDurationSeconds))
    isBelowMin     -> pickerMustBeAfterStart(formatSecondsToHhMm(minTimeSeconds))
    else -> null }
durationSummary = listOfNotNull("${hours}h" if hours > 0, "${minutes}m" if minutes > 0, "${seconds}s" if seconds > 0)
                    .joinToString(" ").ifEmpty { pickerStartZero }
```

In the conditions below `max in 1..x` means `maxDurationSeconds >= 1 && maxDurationSeconds <= x`.

Layout:

- `Dialog(usePlatformDefaultWidth = false)`; `Surface` width 441.6 dp (see 1.2), vertical padding
  24 dp, shape 18 dp, colour `surface` with tonal elevation 6 dp (dark `#0D2D3F`, light
  `#E4F2F9`), shadow elevation 8 dp, no border.
- `Column` fillMaxWidth, padding 20 dp, children centred horizontally:
    1. Header `Row` fillMaxWidth, SpaceBetween, centre vertically: `Text(title)` titleMedium Bold
       onSurface; `TextButton(onClick = onDismissRequest) { Text("✕", 16 sp, onSurfaceVariant) }`.
    2. spacer 14.
    3. `Row` fillMaxWidth, centred, centre vertically: `TimeFieldColumn` (hours), `TimeColon`,
       `TimeFieldColumn` (minutes), `TimeColon`, `TimeFieldColumn` (seconds):

       | Column | label | maxValue | onValueChange | onUp | onDown | upEnabled | downEnabled |
            |---|---|---|---|---|---|---|---|
       | hours | `pickerHours` | 99 | `hours = it` | `nextSec = (hours + 1) * 3600 + minutes * 60 + seconds`; if `max in 1..nextSec` -> `applyTotalSeconds(max)` else `hours++` | `if (hours > 0) hours--` | `max <= 0 || currentTotalSeconds < max` | `hours > 0` |
       | minutes | `pickerMinutes` | 59 | `minutes = it` | `nextSec = currentTotalSeconds + 60`; if `max in 1..nextSec` -> `applyTotalSeconds(max)`; else if `minutes < 59` `minutes++` else `minutes = 0; hours++` | `if (minutes > 0) minutes--` else if `hours > 0` `hours--; minutes = 59` | same | `minutes > 0 || hours > 0` |
       | seconds | `pickerSeconds` | 59 | `seconds = it` | `nextSec = currentTotalSeconds + 1`; if `max in 1..nextSec` -> `applyTotalSeconds(max)` else `applyTotalSeconds(currentTotalSeconds + 1)` | `if (currentTotalSeconds > 0) applyTotalSeconds(currentTotalSeconds - 1)` | same | `currentTotalSeconds > 0` |

       (`hours++` from the arrow is not capped at 99.)
    4. spacer 14.
    5. Summary `Surface` fillMaxWidth, shape 8 dp, `surfaceVariant` alpha 0.4; `Row` padding
       12 / 8, content centred: `Text("⏱️ " + pickerSelectedTime(formatted, durationSummary))`
       bodySmall SemiBold primary.
    6. If `validationErrorMessage != null`: spacer 8; `Surface` fillMaxWidth, shape 8 dp,
       `errorContainer` alpha 0.8; `Row` padding 12 / 6: `Text("⚠️ $validationErrorMessage")`
       bodySmall Bold onErrorContainer.
    7. spacer 14.
    8. Chip `Row` fillMaxWidth, `horizontalScroll`, spacing 6 dp. All are M3
       `FilterChip(selected = false)` with shape 16 dp and an 11 sp label (see 1.4 for the
       defaults: 32 dp tall, 1 dp `outlineVariant` border, transparent, label onSurfaceVariant):

       | Label | Shown | Click |
            |---|---|---|
       | `"+5m"`, `"+15m"`, `"+30m"` (literal) | always | `targetSec = (hours * 60 + minutes + N) * 60`; if `max in 1..targetSec` -> `applyTotalSeconds(max)` else `applyTotalSeconds(targetSec)` (this resets seconds to 0) |
       | `"+1h"` (literal) | always | `targetSec = (hours * 60 + minutes + 60) * 60`; if `max in 1..targetSec` -> `applyTotalSeconds(max)` else `hours++` (seconds kept) |
       | `pickerEndOfVideo("${pad2(max / 3600)}:${pad2((max % 3600) / 60)}")` | `maxDurationSeconds > 0` | `applyTotalSeconds(max)`; container `secondaryContainer` alpha 0.5 |
       | `pickerMin(formatSecondsToHhMm(minTimeSeconds + 60))` when `minTimeSeconds > 0`, else literal `"00:00"` | always | `minTimeSeconds > 0` -> `applyTotalSeconds((minTimeSeconds / 60 + 1) * 60)`; else `applyTotalSeconds(0)` |

       Note: the "End of Video" label drops the seconds, and in the start picker `max` is
       `end - 1 s`, so the label can differ from the real video end. The "Min" label shows
       `min + 60 s` while the click goes to the first whole minute after `min`; they differ when
       `min` has seconds.
    9. spacer 18; `HorizontalDivider` `outlineVariant` alpha 0.4; spacer 16.
    10. Actions `Row` fillMaxWidth, spacing 10 dp, two equal-weight buttons, shape 8 dp:
        `OutlinedButton(onDismissRequest) { Text(cancel) }` and
        `Button(enabled = isValid, container primary)` with
        `Text(if (isValid) pickerApply(formatted) else pickerInvalidTime, Bold)`; click (only when
        valid) -> `onTimeSelected(formatted)`.

`TimeColon` (private, lines 415-424): `Text(":")` 36 sp Bold onSurface, padding 8 dp horizontal /
10 dp vertical. It is centred on the whole 172 dp column (arrows + box + label), so it sits about
10 dp below the centre of the digit boxes (derived).

`TimeFieldColumn` (private, lines 431-510):

```kotlin
@Composable
private fun TimeFieldColumn(
    label: String, value: Int, maxValue: Int,
    onValueChange: (Int) -> Unit, onUp: () -> Unit, onDown: () -> Unit,
    upEnabled: Boolean, downEnabled: Boolean
)
```

- Local state: `text` (starts as `pad2(value)`), `focused`.
  `LaunchedEffect(value) { if (!focused || text.toIntOrNull() != value) text = pad2(value) }`.
- `Column` centred, spacing 4 dp:
    - `IconButton(onUp, enabled = upEnabled)` 40 x 40 dp, `Text("▲")` 16 sp Bold `primary`. The
      glyph colour is explicit, so a disabled arrow looks the same as an enabled one; it only stops
      reacting.
    - `Card` 80 x 64 dp, shape 12 dp, container `primaryContainer` alpha 0.9 when focused, 0.6
      otherwise. Centred `BasicTextField`, single line, 32 sp Bold, centred, colour
      onPrimaryContainer, cursor `primary`, 6 dp horizontal padding, number keyboard.
        - typing: `digits = raw.filter(Char::isDigit).take(2)`;
          `parsed = digits.toIntOrNull() ?: 0`;
          `n = min(parsed, maxValue)`; `text = if (parsed > maxValue) n.toString() else digits`;
          `onValueChange(n)`. So an empty box counts as 0 while typing, and "75" minutes snaps to
          "59" at once.
        - on blur: `n = (text.toIntOrNull() ?: 0).coerceIn(0, maxValue)`; `onValueChange(n)`;
          `text = pad2(n)`.
    - `IconButton(onDown, enabled = downEnabled)` 40 x 40 dp, `Text("▼")` same style.
    - `Text(label)` labelSmall SemiBold onSurfaceVariant.

Mouse / keyboard: scrim click or Esc = cancel. Tab moves through ✕, arrows, the three boxes, the
chips and the buttons. No auto-repeat on the arrows (one click = one step). No wheel handling on
the boxes.

### 2.5 `DirectoryPickerDialog.kt`

```kotlin
@Composable
fun DirectoryPickerDialog(
    initialPath: String,
    pathResolver: PathResolver,
    onDirectorySelected: (String) -> Unit,
    onDismissRequest: () -> Unit
)
```

Purpose: in-app folder browser. Used in two places:

- Add dialog: `AddDownloadModalDialog.kt:246-258`, opened by the 📂 `FieldIconButton` in the
  destination field (`AddDownloadSections.kt:356`); `initialPath = destinationPath`; select ->
  `DashboardUiEvent.OnDestinationPathChanged(path)` and close.
- Settings > Downloads: `SettingsPages.kt:267-277`, opened by the "Choose folder" button
  (`:294-295`); select -> the page's `onDestinationPathChanged(selected)` and close.

sharedLogic calls (directly from the UI, on the UI thread): `PathResolver.getDownloadsDirectory()`,
`getAvailableDirectories()`, `getParentDirectory(path)`, `listSubdirectories(path, includeHidden)`,
`createSubdirectory(parent, name)` (`core/filesystem/PathResolver.kt`, JVM actual in
`jvmMain/.../PathResolver.jvm.kt`). JVM behaviour that the picker depends on:

- `getAvailableDirectories()` = up to three pairs with **hard-coded English labels**
  `"Downloads"`, `"Documents"`, `"Desktop"` (each added if it exists or can be created; on Windows
  the `OneDrive\...` variant is preferred when it exists).
- `listSubdirectories` = directories only, sorted case-insensitively; "hidden" means **the name
  starts with a dot**. The Windows hidden attribute is ignored, so e.g. `AppData` is always
  listed.
- `getParentDirectory` = parent if it exists and is readable, else `null` (so `C:\` has no parent
  and there is no way to reach another drive from inside the picker).
- `createSubdirectory` = `sanitizeFileName(name)`, returns the absolute path if the folder exists
  or was created, else `null`.

UI-local state (lines 58-78):

```kotlin
val defaultPath = remember { if (initialPath.isNotBlank()) initialPath else pathResolver.getDownloadsDirectory() }
var currentPath by remember { mutableStateOf(defaultPath) }
var subdirectories by remember { mutableStateOf(listOf<String>()) }
var showHiddenFolders by remember { mutableStateOf(false) }
var isCreatingFolder by remember { mutableStateOf(false) }
var newFolderName by remember { mutableStateOf("") }
var createFolderError by remember { mutableStateOf<String?>(null) }
val quickDirectories = remember { pathResolver.getAvailableDirectories() }
val parentDir = remember(currentPath) { pathResolver.getParentDirectory(currentPath) }
LaunchedEffect(currentPath, showHiddenFolders) { subdirectories = pathResolver.listSubdirectories(currentPath, showHiddenFolders) }
```

Layout:

- `Dialog(usePlatformDefaultWidth = false)` > `ProvideSafeClipboard` > `Surface` width 582.8 dp,
  `heightIn(max = 680.dp)` then vertical padding 24 dp (surface at most 632 dp tall),
  `clearFocusOnTapOutside()`, shape 16 dp, colour `surface` with tonal elevation 6 dp (dark
  `#0D2D3F`, light `#E4F2F9`), shadow elevation 8 dp.
- `Column` fillMaxWidth, padding 20 dp (not scrollable):
    1. Header `Row` SpaceBetween: `Text("📁 " + dirPickerSelectFolder)` titleMedium Bold
       onSurface; `TextButton(onDismissRequest) { Text("✕", 16 sp, onSurfaceVariant) }`.
    2. spacer 10.
    3. Path `Row` fillMaxWidth, centre vertically, spacing 8 dp:
        - `Surface(weight 1)` shape 8 dp, `surfaceVariant` alpha 0.5; `Row` padding 12 / 8:
          `Text(currentPath)` bodySmall onSurface, 1 line, ellipsis (read only, not editable, not
          selectable);
        - `OutlinedButton(enabled = parentDir != null, shape 8 dp)`
          `Text("⬆️ " + dirPickerUp, 12 sp)` -> `currentPath = parentDir; isCreatingFolder = false`.
    4. spacer 12.
    5. If `quickDirectories.isNotEmpty()`: `Row` fillMaxWidth, `horizontalScroll`, spacing 6 dp;
       one `FilterChip` per `(label, path)`: `selected = currentPath == path` (exact string
       compare), label `Text(label)` labelSmall, shape 20 dp, selected container
       `primaryContainer`, selected label onPrimaryContainer; click `currentPath = path;
     isCreatingFolder = false`. Then spacer 12.
    6. Section header `Row` fillMaxWidth, SpaceBetween, centre vertically:
        - left `Row` spacing 8 dp: `Text(dirPickerSubfolders(subdirectories.size))` labelMedium
          SemiBold onSurfaceVariant; `FilterChip(selected = showHiddenFolders)` height 28 dp, shape
          14 dp, label
          `Text("👁️ " + (if (showHiddenFolders) dirPickerHiddenOn else dirPickerHiddenOff), 10 sp)`,
          selected container `primaryContainer` alpha 0.6, selected label onPrimaryContainer; click
          toggles `showHiddenFolders`.
        - right `TextButton`: `Text(if (isCreatingFolder) cancel else "➕ " + dirPickerNewFolder)`
          labelMedium, primary; click `isCreatingFolder = !isCreatingFolder; newFolderName = "";
       createFolderError = null`.
    7. If `isCreatingFolder`: spacer 6; `Card` fillMaxWidth, shape 8 dp, `surfaceVariant` alpha
       0.35; `Column` padding 10 dp:
        - `Row` fillMaxWidth, centre vertically, spacing 8 dp:
          `FetchJetTextField(value = newFolderName, placeholder = dirPickerFolderName,
          leading = Text("📁", 14 sp), height = 44.dp, weight 1)` with
          `onValueChange = { newFolderName = it; createFolderError = null }`;
          `Button(enabled = newFolderName.isNotBlank(), shape 8 dp) { Text(dirPickerCreate, 12 sp) }`.
          Click: `created = pathResolver.createSubdirectory(currentPath, newFolderName)`; if not
          null: `currentPath = created; isCreatingFolder = false; newFolderName = ""`; refresh the
          list; else `createFolderError = dirPickerCreateFailed`.
        - if `createFolderError != null`: spacer 4; `Text(error)` labelSmall, colour `error`.
        - spacer 6 after the card. Enter in the field does nothing (no keyboard action is wired).
    8. spacer 6; `HorizontalDivider` `outlineVariant` alpha 0.5; spacer 6.
    9. List:
        - empty: `Box` fillMaxWidth, height 140 dp, centred `Text(dirPickerNoSubfolders)` bodySmall,
          onSurfaceVariant alpha 0.7, centre aligned (two lines, the string contains `\n`).
        - else `LazyColumn` fillMaxWidth, `heightIn(min = 120.dp, max = 220.dp)`, no scrollbar. Per
          folder name:
            - `Surface` fillMaxWidth, clickable, shape 6 dp, transparent. Click:
              `separator = if (currentPath.endsWith("/") || currentPath.endsWith("\\")) "" else "/"`;
              `currentPath = "$currentPath$separator$folderName"; isCreatingFolder = false`.
              **Single click enters the folder**; there is no "selected row" state and no double
              click.
            - `Row` fillMaxWidth, padding 8 dp horizontal / 10 dp vertical, SpaceBetween, centre
              vertically: left `Row(weight 1)`: `isHidden = folderName.startsWith(".")`;
              `Text(if (isHidden) "👁️" else "📁", 16 sp)`; spacer 10; `Text(folderName)` bodyMedium,
              weight Normal + onSurfaceVariant when hidden, else Medium + onSurface, 1 line,
              ellipsis;
              when hidden: spacer 6 + `Text(dirPickerHiddenTag)` 10 sp, colour `outline`. Right:
              `Text("›", 18 sp, onSurfaceVariant)`.
            - under each row: `HorizontalDivider` 0.5 dp, `outlineVariant` alpha 0.25.
    10. spacer 16; `HorizontalDivider` `outlineVariant` alpha 0.5; spacer 16.
    11. Actions `Row` fillMaxWidth, spacing 10 dp, equal weights, shape 8 dp:
        `OutlinedButton(onDismissRequest) { Text(cancel, maxLines 1) }`;
        `Button(container primary) { Text(dirPickerSelectFolder, Bold, maxLines 1) }` ->
        `onDirectorySelected(currentPath)`.

Quirks of the current behaviour:

- The returned path is whatever `currentPath` holds: after entering a subfolder on Windows it
  contains a forward slash (`C:\Users\me\Downloads/Movies`). Going "Up" or creating a folder
  normalises it (`File.absolutePath`).
- `initialPath` is not checked: a folder that does not exist shows "No subfolders", "Up" may be
  disabled, and "Select Folder" still returns it.
- With the new-folder card open and a long list, the column needs about 660 dp (derived) but only
  632 dp are available, so the bottom of the dialog is squeezed. Give the list the flexible
  height in the port.
- Folder listing runs on the UI thread (a slow network folder freezes the dialog).

#### Could a native Windows folder picker replace it?

**What the JVM desktop app shows today:** this in-app Compose dialog, in both places. It is a
layer inside the main window, themed and translated by the app. `PathResolver.chooseDirectory()`
(Swing `JFileChooser`, `PathResolver.jvm.kt:98-121`) exists but **has no caller** anywhere in
`androidUi`, `desktopApp (removed)` or `androidApp`; it is dead code.

**`IFileOpenDialog` with `FOS_PICKFOLDERS`** (plus `FOS_FORCEFILESYSTEM`, owner = the main HWND,
`SetFolder` to the initial path when it exists, else the Downloads folder) covers the function
fully and adds things the in-app picker cannot do:

| Capability                                             | In-app picker today                                   | `IFileOpenDialog`                                                           |
|--------------------------------------------------------|-------------------------------------------------------|-----------------------------------------------------------------------------|
| browse down / up                                       | yes                                                   | yes                                                                         |
| switch drive, network places, libraries                | **no** (no parent above `C:\`)                        | yes                                                                         |
| type or paste a path                                   | no (only in the destination field outside the picker) | yes (address bar)                                                           |
| quick places                                           | Downloads, Documents, Desktop chips                   | Quick access / This PC pane                                                 |
| new folder                                             | yes (inline)                                          | yes                                                                         |
| show dot-folders toggle                                | yes (name starts with ".")                            | follows Explorer's own "hidden items" setting; dot-folders are always shown |
| modal over the app with dimmed background              | yes (scrim)                                           | modal OS window, no scrim                                                   |
| app theme (dark / light as chosen in the app)          | yes                                                   | follows the Windows theme, not the app's                                    |
| app language (15 languages, chosen in the app) and RTL | yes                                                   | follows the Windows display language                                        |
| returns a non-existing initial folder                  | yes                                                   | no (must pick an existing folder)                                           |
| path format                                            | may contain `/`                                       | always a normal Windows path                                                |

So nothing functional is lost; what changes is the look (system window instead of the app's own
style) and the language / theme source. That conflicts with the stated goal "same look as the
Compose app", so it is the owner's call (see Open questions). It would remove one whole port unit
(about 445 lines of Compose) and the six presenter fields in section 6. Technical notes if it is
chosen: it must be created on the UI thread in a single-threaded COM apartment, `Show(hwnd)`
blocks in its own modal message loop (the Slint window keeps painting but the callback that
opened it does not return until the dialog closes), and the result comes from
`IShellItem::GetDisplayName(SIGDN_FILESYSPATH)`.

### 2.6 `DuplicateDownloadDialog.kt`

```kotlin
@Composable
fun DuplicateDownloadDialog(
    task: DownloadTask,
    onResumeExisting: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
)
```

Shown when `state.duplicateTaskAlert != null` (`App.kt:851-859`). Events:
`onResumeExisting` -> `OnResumeExistingDuplicateClicked`, `onRestart` ->
`OnRestartDuplicateClicked`, `onDismiss` (scrim, Esc, Cancel) -> `OnDismissDuplicateDialog`.
Reads `task.fileName`, `task.url`, `task.status`, `task.progress` (Float 0..1).

View-model behaviour (`DashboardTaskEvents.kt:102-132`): resume = resume the existing task, clear
the alert, close the Add dialog, clear the URL and the link list; restart = remove the old task
**with its file**, clear the alert, add again with `forceOverwrite = true`; dismiss = clear the
alert only (the Add dialog stays open).

Layout:

- `Dialog(usePlatformDefaultWidth = false)`; `Card` width 488.8 dp, vertical padding 16 dp, shape
  20 dp, container `surface`, border 1 dp `outlineVariant` alpha 0.6, shadow elevation 12 dp.
- `Column` fillMaxWidth, padding 20 dp:
    1. Header `Row` centre vertically: icon `Box` 44 x 44 dp, clip 12 dp, background
       `primaryContainer` alpha 0.4, centred `Text("🔄", 22 sp)`; spacer 12; `Column`:
       `Text(duplicateDialogTitle)` 17 sp Bold onSurface; `Text(duplicateDialogSubtitle)` 12 sp
       onSurfaceVariant.
    2. spacer 16.
    3. Detail `Card` fillMaxWidth, shape 12 dp, `surfaceVariant` alpha 0.35, border 1 dp
       `outlineVariant` alpha 0.6; `Column` padding 14 dp:
        - `Text(task.fileName.ifBlank { task.url })` 13 sp Bold onSurface, max 2 lines, ellipsis.
        - spacer 8.
        - `Row` fillMaxWidth, SpaceBetween, centre vertically:
            - badge `Card` shape 6 dp, container `statusBg`,
              `Text(duplicateDialogStatus(task.status.name))`
              11 sp Bold `statusColor`, padding 8 / 3. The status is the raw enum name
              (`DOWNLOADING`, `PAUSED`, ...), not translated.

              | `task.status` | statusColor | statusBg |
                       |---|---|---|
              | COMPLETED | `#10B981` | `#064E3B` |
              | DOWNLOADING, CONNECTING | `#38BDF8` | primaryContainer |
              | PAUSED | `#F59E0B` | `#78350F` |
              | ERROR | `#F87171` | errorContainer |
              | IDLE, QUEUED | onSurfaceVariant | surfaceVariant |

            - if `task.progress > 0f`: `Text("${(task.progress * 100).toInt()}%")` 12 sp Bold
              `#38BDF8`.
        - if `task.progress > 0f && task.status != COMPLETED`: spacer 8; `LinearProgressIndicator`
          fillMaxWidth, height 6 dp, clip 3 dp, colour `primary`, track `surfaceVariant`, no gap, no
          stop dot.
    4. spacer 14; `Text(duplicateDialogQuestion)` 12 sp onSurface.
    5. spacer 18.
    6. `Column` fillMaxWidth, spacing 8 dp (each button is 40 dp inside 48 dp of layout, so the
       visible gap is 16 dp):
        - `Button(onResumeExisting)` fillMaxWidth, shape 10 dp, container `primary`:
          `Text("▶️ " + duplicateDialogResumeExisting)` Bold `onPrimary`.
        - `OutlinedButton(onRestart)` fillMaxWidth, shape 10 dp, border 1 dp `outlineVariant` alpha
          0.6, content onSurface: `Text("🔄 " + duplicateDialogRedownload)` Medium.
        - `TextButton(onDismiss)` fillMaxWidth: `Text(cancel)` 13 sp onSurfaceVariant.

No UI-local state. The task is a snapshot taken when the alert was raised; progress does not
update live.

### 2.7 `FileOverwriteDialog.kt`

```kotlin
@Composable
fun FileOverwriteDialog(
    data: FileConflictData,
    onOverwrite: () -> Unit,
    onSaveAsCopy: (String) -> Unit,
    onDismiss: () -> Unit
)
```

`FileConflictData(fileName, destinationPath, suggestedNewName)`
(`DashboardContract.kt:76-80`). Shown when `state.fileConflictAlert != null`
(`App.kt:861-874`). Events: `onOverwrite` -> `OnConfirmOverwriteClicked`,
`onSaveAsCopy(name)` -> `OnSaveAsCopyClicked(newFileName = name)`, `onDismiss` ->
`OnDismissFileConflictDialog`.

View-model behaviour (`DashboardTaskEvents.kt:134-169`): overwrite = clear the alert, remove list
entries with the same file name and folder (keeping files), then start with
`forceOverwrite = true` (or the pending external download); save as copy = clear the alert and
start with `overrideFileName = newName`; dismiss = clear the alert and drop the pending external
download.

Layout: the same shell as 2.6 (`Card` 488.8 dp, shape 20 dp, `surface`, border `outlineVariant`
0.6, shadow 12 dp, `Column` padding 20 dp).

1. Header: icon box 44 dp, clip 12 dp, background `errorContainer` alpha 0.4, `Text("⚠️", 22 sp)`;
   spacer 12; `Text(overwriteDialogTitle)` 17 sp Bold onSurface;
   `Text(overwriteDialogSubtitle)` 12 sp onSurfaceVariant.
2. spacer 16.
3. Detail `Card` (12 dp, `surfaceVariant` 0.35, border `outlineVariant` 0.6); `Column` padding
   14 dp, spacing 8 dp; three `Row`s aligned to the top, label then value with no spacer (the
   label strings end with a space), all 12 sp, values max 2 lines with ellipsis:

   | Label | Label style | Value | Value style |
      |---|---|---|---|
   | `overwriteDialogFileNameLabel` | Bold onSurfaceVariant | `data.fileName` | SemiBold onSurface |
   | `overwriteDialogFolderLabel` | Bold onSurfaceVariant | `data.destinationPath` | Normal onSurface |
   | `overwriteDialogNewCopyLabel` | Bold primary | `data.suggestedNewName` | SemiBold primary |

4. spacer 14; `Text(overwriteDialogQuestion)` 12 sp onSurface; spacer 18.
5. `Column` fillMaxWidth, spacing 8 dp:
    - `Button` fillMaxWidth, shape 10 dp, container `primary`:
      `Text("📝 " + overwriteDialogSaveAsCopy(data.suggestedNewName))` Bold `onPrimary` (no max
      lines: a long name wraps and the button grows) -> `onSaveAsCopy(data.suggestedNewName)`.
    - `OutlinedButton` fillMaxWidth, shape 10 dp, border 1 dp `error`, content `error`:
      `Text("⚠️ " + overwriteDialogOverwriteExisting)` Medium -> `onOverwrite()`.
    - `TextButton` fillMaxWidth: `Text(cancel)` 13 sp onSurfaceVariant -> `onDismiss()`.

No UI-local state.

### 2.8 `MissingFileDialog.kt`

```kotlin
@Composable
fun MissingFileDialog(fileName: String, onRemove: () -> Unit, onDismiss: () -> Unit)
```

Shown when the UI-global `missingFileTaskId` (`App.kt:62`, a top-level `mutableStateOf<String?>`)
is set and the task still exists (`App.kt:786-797`). It is set by
`openDownloadedFile(fullPath, taskId)` (`App.kt:68-77`) when
`PathResolver().fileExists(fullPath)` is false (opening a finished download whose file is gone).

- `onRemove`: `missingFileTaskId.value = null`, then `OnRemoveClicked(missingId)` immediately
  followed by `OnConfirmRemoveClicked(false)` (the task is removed from the list without the
  confirmation dialog and without deleting files).
- `onDismiss` (Close, scrim, Esc): `missingFileTaskId.value = null`.

It is an M3 `AlertDialog` (defaults from `AlertDialog.kt` / `DialogTokens.kt`):

- container `surfaceContainerHigh` (dark `#2B2930`, light `#ECE6F0`, see 1.3), shape 28 dp, no
  border, no shadow, tonal elevation 0;
- width: content width + 48 dp, clamped to 280..560 dp. The outer `Dialog` uses the platform
  default width: at most 580 dp when the smaller window side is >= 600 dp, 440 dp when >= 480 dp,
  else 320 dp;
- padding 24 dp on all sides; title has 16 dp below it; text has 24 dp below it;
- buttons: end-aligned row, 8 dp apart, dismiss button first then the confirm button (confirm is
  at the end edge); they wrap under each other when they do not fit (confirm on top);
- title colour onSurface; text colour onSurfaceVariant; both buttons keep the M3 default **pill**
  shape (no `shape` is passed), 40 dp tall, 24 dp side padding.

Content:

- title: `Text(fileMissingTitle)` titleMedium Bold.
- text: `Text(fileMissingMessage(fileName))` bodyMedium.
- confirm: `Button(onRemove, container error) { Text(detailsRemoveFromList, colour onError) }`.
- dismiss: `OutlinedButton(onDismiss) { Text(close) }`.

No UI-local state inside the dialog.

### 2.9 `RemoveConfirmationDialog.kt`

```kotlin
@Composable
fun RemoveConfirmationDialog(
    onConfirm: (deleteFile: Boolean) -> Unit,
    onDismiss: () -> Unit,
    count: Int = 1
)
```

Shown when `state.removeConfirmationTaskId != null || state.removeConfirmationTaskIds.isNotEmpty()`
(`App.kt:775-783`);
`count = if (removeConfirmationTaskId != null) 1 else removeConfirmationTaskIds.size`.
The flags are set by `OnCancelClicked(taskId)` / `OnRemoveClicked(taskId)` (single) and
`OnRemoveSelectedClicked(taskIds)` (multi-select) (`DashboardTaskEvents.kt:24-45`).

- `onConfirm(deleteFile)` -> `DashboardUiEvent.OnConfirmRemoveClicked(deleteFile)`: clears both
  fields and calls `queueRepository.removeTask(id, deleteFile)` for the one id or for each id
  (`DashboardTaskEvents.kt:47-72`).
- `onDismiss` -> `DashboardUiEvent.OnDismissRemoveDialog`: clears both fields.

UI-local state: `var deleteFile by remember { mutableStateOf(false) }` (always starts unchecked;
not remembered between openings).

M3 `AlertDialog` with the same defaults as 2.8. Because the text column is `fillMaxWidth`, the
dialog takes the maximum width (560 dp on a normal window).

- title: `Text(removeDialogTitle(count))` titleMedium Bold.
- text: `Column` fillMaxWidth, spacing 12 dp:
    - `Text(removeDialogMessage(count))` bodyMedium (onSurfaceVariant from the dialog);
    - `Row` fillMaxWidth, clickable (toggles `deleteFile`), centre vertically:
      `Checkbox(checked = deleteFile)` default colours; spacer 8;
      `Text(removeDialogAlsoDeleteFiles)` bodyMedium onSurface.
- confirm: `Button(container error) { Text(remove, colour onError) }` -> `onConfirm(deleteFile)`.
- dismiss: `OutlinedButton { Text(cancel) }` -> `onDismiss()`.

Keyboard: Esc = dismiss. Enter is not bound to "Remove" (it only activates the focused control).

---

## 3. Strings

All through `LocalizationManager.Strings.<member>` (`core/i18n/data_provider/AppLocalization.kt`;
English from `EnglishProvider.kt`). `val` unless a signature is shown.

### 3.1 `AddDownloadMedia.kt` (and the clip strings its parent builds)

| Member                                                                      | English                                                                                           |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `availableAudioFormats`                                                     | Available Audio Formats                                                                           |
| `availableQualities`                                                        | Available Qualities                                                                               |
| `addSelectAll`                                                              | Select All                                                                                        |
| `addClearSelection`                                                         | Clear                                                                                             |
| `selectedOfAvailable(selected: Int, total: Int)`                            | Selected: $selected of $total available                                                           |
| `qualityFormatLine(format: String, details: String)`                        | Format: $format$details                                                                           |
| `subtitlesTitle`                                                            | Subtitles                                                                                         |
| `subtitlesAuto`                                                             | auto                                                                                              |
| `subtitlesEmbedInVideo`                                                     | Embed in video                                                                                    |
| `subtitlesSeparateFile`                                                     | Separate file                                                                                     |
| `readingCourseLectures(done: Int, total: Int)`                              | Reading the course lectures: $done / $total                                                       |
| `readingCourseLecturesHint`                                                 | The browser extension reads them in the course tab: keep it open                                  |
| `inspectingVideoQualities`                                                  | Inspecting Video Qualities...                                                                     |
| `inspectingVideoQualitiesHint`                                              | Scanning available video resolutions and audio streams                                            |
| `videoInspectionFailed`                                                     | Video Inspection Failed                                                                           |
| `addRetryInspection`                                                        | Retry Inspection                                                                                  |
| `mediaEngineStarting`                                                       | Media engine is starting...                                                                       |
| `mediaEngineNotReady`                                                       | Media engine is not ready                                                                         |
| `mediaEngineRequiresYtDlp`                                                  | Video stream extraction requires yt-dlp installed or embedded.                                    |
| `preparingEngine`                                                           | Preparing engine...                                                                               |
| `startMediaEngine`                                                          | Download / Start media engine                                                                     |
| `inspectAudioFormats`                                                       | Inspect & Select Audio Formats                                                                    |
| `inspectVideoQuality`                                                       | Inspect & Select Video Quality                                                                    |
| `clipSectionTitle(isAudio: Boolean)`                                        | Clip Audio Section (Optional) / Clip Video Section (Optional)                                     |
| `clipSectionHint`                                                           | Download a specific time range only                                                               |
| `mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String)` | Audio/Video Duration: $clock ($friendly) - Full range selected by default                         |
| `clipStartTimeFrom`                                                         | Start Time (From)                                                                                 |
| `clipEndTimeTo`                                                             | End Time (To)                                                                                     |
| `fullMediaChip(isAudio: Boolean, duration: String)`                         | Full Audio ($duration) / Full Video ($duration)                                                   |
| `clipFirst5Min` / `clipFirst15Min` / `clipFirst30Min` / `clipFirst1Hour`    | First 5 min / First 15 min / First 30 min / First 1 hour                                          |
| `clipModeTitle`                                                             | How to download the clip                                                                          |
| `clipOnlyTitle`                                                             | Only the clip                                                                                     |
| `clipOnlyLine1` / `2` / `3`                                                 | Downloads only the part you chose / Slower: YouTube limits the speed / Pause starts the clip over |
| `fullThenCutTitle(isAudio: Boolean)`                                        | Full Audio, then cut / Full Video, then cut                                                       |
| `fullThenCutLine1(isAudio: Boolean, sizeText: String)`                      | Downloads the whole Audio$sizeText / Downloads the whole Video$sizeText                           |
| `fullThenCutLine2` / `3` / `4`                                              | Faster: full speed / Then keeps only your part / Pause / resume works                             |
| `clipStartAfterEnd(start: String, end: String)` (parent)                    | Start time ($start) must be before end time ($end)                                                |
| `clipEndExceedsVideo(end: String, duration: String)` (parent)               | End time ($end) exceeds video duration ($duration)                                                |
| `clipStartExceedsVideo(duration: String)` (parent)                          | Start time exceeds video duration ($duration)                                                     |
| `setStartTimeTitle` / `setEndTimeTitle` (parent)                            | Set Start Time (From) / Set End Time (To)                                                         |

Also `localizeStep(ytDlpStatus)` (`core/i18n/start/StepText.kt:7`): translates the English engine
status text; unknown text passes through.

### 3.2 `AudioConvertSection.kt`

| Member              | English              |
|---------------------|----------------------|
| `audioConvertAllTo` | Convert all audio to |
| `audioConvertTo`    | Convert to           |
| `audioOriginal`     | Original             |

### 3.3 `PlaylistScheduleUi.kt`

| Member                                                                                           | English                                                               |
|--------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| `playlistSummary(videos: Int, selected: Int)`                                                    | Playlist · $videos videos · $selected selected                        |
| `selectNone` / `selectAll`                                                                       | Select none / Select all                                              |
| `playlistFolder`                                                                                 | Save in folder                                                        |
| `playlistFolderHint`                                                                             | Every file goes into this folder. Clear it to save them directly.     |
| `qualityForAllVideos`                                                                            | Quality for all videos                                                |
| `qualityForAllVideosHint`                                                                        | If a video has no such quality, the closest lower one is taken.       |
| `sysPlaylistQualityBest` / `sysPlaylistQualityAudioOnly` (through `PlaylistQuality.label`)       | Best / Audio only                                                     |
| `downloadOnlyThisVideo`                                                                          | Download only this video instead                                      |
| `whenToStart`                                                                                    | When to start                                                         |
| `scheduleNow` / `scheduleIn30Min` / `scheduleIn1Hour` / `scheduleAtTime`                         | Now / In 30 min / In 1 hour / At a time…                              |
| `scheduleDayLabel`                                                                               | Day                                                                   |
| `scheduleToday` / `scheduleTomorrow` / `scheduleIn2Days` / `scheduleIn3Days` / `scheduleInAWeek` | Today / Tomorrow / In 2 days / In 3 days / In a week                  |
| `scheduleHourLabel` / `scheduleMinuteLabel`                                                      | Hour / Minute                                                         |
| `scheduleAm` / `schedulePm`                                                                      | AM / PM                                                               |
| `scheduleSet` / `scheduleSetTime`                                                                | Set / Set time                                                        |
| `scheduleTimePassed`                                                                             | That time has already passed today. Pick a later time or another day. |
| `scheduleStartsAt(time: String)`                                                                 | Starts $time (FetchJet must be running)                               |
| `addConnectionsTitle`                                                                            | Connections                                                           |
| `connectionsAuto` / `connectionsAutoWithDefault(n: Int)`                                         | Auto / Auto ($n)                                                      |

### 3.4 `TimeRangePickerDialog.kt`

| Member                                              | English                            |
|-----------------------------------------------------|------------------------------------|
| `pickerHours` / `pickerMinutes` / `pickerSeconds`   | Hours / Minutes / Seconds          |
| `pickerExceedsVideo(duration: String)`              | Exceeds video duration ($duration) |
| `pickerMustBeAfterStart(start: String)`             | Must be after start time ($start)  |
| `pickerStartZero`                                   | Start (00:00)                      |
| `pickerSelectedTime(time: String, summary: String)` | Selected Time: $time ($summary)    |
| `pickerEndOfVideo(time: String)`                    | End of Video ($time)               |
| `pickerMin(time: String)`                           | Min ($time)                        |
| `pickerApply(time: String)`                         | Apply ($time)                      |
| `pickerInvalidTime`                                 | Invalid Time                       |
| `cancel`                                            | Cancel                             |

### 3.5 `DirectoryPickerDialog.kt`

| Member                                     | English                                                              |
|--------------------------------------------|----------------------------------------------------------------------|
| `dirPickerSelectFolder`                    | Select Folder                                                        |
| `dirPickerUp`                              | Up                                                                   |
| `dirPickerSubfolders(n: Int)`              | Subfolders ($n)                                                      |
| `dirPickerHiddenOn` / `dirPickerHiddenOff` | Hidden: On / Hidden: Off                                             |
| `dirPickerNewFolder`                       | New Folder                                                           |
| `dirPickerFolderName`                      | Folder name…                                                         |
| `dirPickerCreate`                          | Create                                                               |
| `dirPickerCreateFailed`                    | Failed to create folder                                              |
| `dirPickerNoSubfolders`                    | No subfolders in this directory.\nFiles will be saved directly here. |
| `dirPickerHiddenTag`                       | (hidden)                                                             |
| `cancel`                                   | Cancel                                                               |

### 3.6 Small dialogs

| Member                                                                                        | English                                                                               |
|-----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `duplicateDialogTitle`                                                                        | Download Already Exists                                                               |
| `duplicateDialogSubtitle`                                                                     | This item is already in your download queue                                           |
| `duplicateDialogStatus(status: String)`                                                       | Status: $status                                                                       |
| `duplicateDialogQuestion`                                                                     | Would you like to resume the existing download or start over from scratch?            |
| `duplicateDialogResumeExisting`                                                               | Resume Existing                                                                       |
| `duplicateDialogRedownload`                                                                   | Re-download (Start Fresh)                                                             |
| `overwriteDialogTitle`                                                                        | File Already Exists                                                                   |
| `overwriteDialogSubtitle`                                                                     | A file with this name already exists in destination                                   |
| `overwriteDialogFileNameLabel` / `overwriteDialogFolderLabel` / `overwriteDialogNewCopyLabel` | "File Name: " / "Folder: " / "New Copy: " (trailing space)                            |
| `overwriteDialogQuestion`                                                                     | Would you like to save this download as a new copy or overwrite the existing file?    |
| `overwriteDialogSaveAsCopy(name: String)`                                                     | Save as Copy ($name)                                                                  |
| `overwriteDialogOverwriteExisting`                                                            | Overwrite Existing File                                                               |
| `fileMissingTitle`                                                                            | File not found                                                                        |
| `fileMissingMessage(name: String)`                                                            | "$name" is no longer on this device. It may have been moved, renamed or deleted.      |
| `detailsRemoveFromList`                                                                       | Remove from list                                                                      |
| `close`                                                                                       | Close                                                                                 |
| `removeDialogTitle(count: Int)`                                                               | Remove Download / Remove $count Downloads (count > 1)                                 |
| `removeDialogMessage(count: Int)`                                                             | Are you sure you want to remove this download task? / ...these $count download tasks? |
| `removeDialogAlsoDeleteFiles`                                                                 | Also delete downloaded file(s) from disk                                              |
| `remove`                                                                                      | Remove                                                                                |
| `cancel`                                                                                      | Cancel                                                                                |

### 3.7 Hard-coded texts (not in the localisation interface)

- Emoji / glyph prefixes and icons: 🎬 🎵 💬 ⚠️ 🔄 ⏳ ✂️ ⏱️ 🕒 ⚡ 📃 📁 🕑 👁️ ➕ ⬆️ ▶️ 📝, and the
  glyphs `◉ ○ • ▲ ▼ ✕ › :`.
- Picker chips `+5m`, `+15m`, `+30m`, `+1h`, `00:00`; the duration summary units `h`, `m`, `s`.
- `formatSecondsToDisplayFriendly` units `h m s`; `formatBytes` / `formatSizeDisplay` units.
- `formatLocalSchedule`: `today`, `tomorrow`, `in N days,`, `AM`, `PM`.
- `" : "` between hour and minute; `"${index + 1}."`; the `".mp4"` suffix for browser playlists;
  connection counts `1 4 8 16 32`; audio formats in upper case; the percentage `NN%`.
- Status enum names in the duplicate dialog badge.
- Quick folder labels `Downloads`, `Documents`, `Desktop` (from `PathResolver`).

---

## 4. (a) Reusable widgets the Slint port needs for this area

| Widget                           | Behaviour                                                                                                                                                                                                             | Visual spec                                                                                                                                                                                                                          |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ModalLayer`                     | Full-window overlay; scrim click and Esc fire `dismiss`; stackable (a second layer above the first, each with its own scrim); content centred; 200 ms in / 100 ms out (alpha 0.2 -> 1, scale 0.95 -> 1, y 10 px -> 0) | scrim black alpha 0.6                                                                                                                                                                                                                |
| `DialogCard`                     | Panel for Duplicate / Overwrite (and the Add dialog)                                                                                                                                                                  | `surface`, radius 20, border 1 px `outlineVariant` 0.6, shadow of elevation 12, padding 20                                                                                                                                           |
| `DialogSurface`                  | Panel for the time picker and the directory picker                                                                                                                                                                    | tinted surface (dark `#0D2D3F`, light `#E4F2F9`), radius 18 or 16, shadow of elevation 8, padding 20                                                                                                                                 |
| `AlertPanel`                     | Title, message slot, end-aligned buttons                                                                                                                                                                              | `surfaceContainerHigh` (dark `#2B2930`, light `#ECE6F0`), radius 28, padding 24, title 16/24 bold, text 14/20 onSurfaceVariant, gaps 16 and 24, buttons 8 px apart, width 280..560                                                   |
| `FilledButton`                   | enabled / disabled, hover 8 %, press 10 % overlay of the content colour                                                                                                                                               | 40 px tall in a 48 px slot, padding 24 / 8, label 14/20 weight 500 unless overridden; `radius` parameter (8, 10 or pill); `background` parameter (primary or error); disabled: onSurface 0.10 container, onSurfaceVariant 0.38 label |
| `OutlineButton`                  | same states                                                                                                                                                                                                           | transparent, border 1 px (`outlineVariant` default; overridable colour), content colour parameter (primary default); disabled label onSurfaceVariant 0.38, border `outlineVariant` 0.10                                              |
| `TextLinkButton`                 | same states                                                                                                                                                                                                           | min 58 x 40 in a 48 px slot, padding 12 / 8 (overridable to 6 / 2), content primary or a given colour                                                                                                                                |
| `M3Checkbox`                     | toggles; 48 x 48 slot                                                                                                                                                                                                 | 18 px box, radius 2, stroke 2; colour parameters (default primary / onSurfaceVariant; the media lists use `#0284C7` / `#64748B`); check mark onPrimary                                                                               |
| `M3Switch`                       | toggles, thumb grows 16 -> 24                                                                                                                                                                                         | track 52 x 32, see 1.4                                                                                                                                                                                                               |
| `InfoCard`                       | plain container used by qualities, subtitles, clip, playlist                                                                                                                                                          | radius 12, `surfaceVariant` 0.35, border 1 px `outlineVariant` 0.6, padding 12                                                                                                                                                       |
| `SelectRow`                      | checkbox row with selected highlight; click anywhere toggles                                                                                                                                                          | radius 6, padding 6 / 4, selected `primaryContainer` 0.4                                                                                                                                                                             |
| `ChoiceChip`                     | single-select pill                                                                                                                                                                                                    | radius 16, padding 12 / 6, 12 px text, height 36; selected primary 0.18 + 1 px primary border + bold primary text; unselected `surface` 0.6 + `outlineVariant` 0.6 border                                                            |
| `FormatChip` (AudioFormatChip)   | single-select                                                                                                                                                                                                         | radius 10, padding 12 / 8, 12 px; selected filled primary, bold onPrimary; unselected `surfaceVariant` 0.5, border `outlineVariant` 0.6; 40 px in a 48 px slot                                                                       |
| `QuickChip` (QuickTimeChip)      | action chip                                                                                                                                                                                                           | radius 6, padding 8 / 4, 10 px medium; `surfaceVariant` 0.6, border `outlineVariant` 0.5; 32 px in a 48 px slot                                                                                                                      |
| `M3FilterChip`                   | action or toggle chip                                                                                                                                                                                                 | 32 px tall (28 for the hidden toggle), text inset 16, radius parameter (14, 16, 20), 1 px `outlineVariant` border when unselected, optional container colours                                                                        |
| `RadioCard` (ClipModeOption)     | selectable card with glyph, title and bullet lines                                                                                                                                                                    | radius 10, padding 10 / 8; selected primary 0.12 + 2 px primary border; unselected `surfaceVariant` 0.25 + 1 px `outlineVariant` 0.5                                                                                                 |
| `TimeValueCard`                  | clickable start / end card with error state                                                                                                                                                                           | radius 10, padding 10; normal `surface` + 1 px primary 0.5; error `errorContainer` 0.15 + 1.5 px error                                                                                                                               |
| `Banner`                         | one-line tinted info / error strip                                                                                                                                                                                    | radius 8, padding 10 / 6 (12 / 8 and 12 / 6 in the picker); colours per call site                                                                                                                                                    |
| `LoadingBanner`                  | spinner + title + subtitle (shared with the torrent part)                                                                                                                                                             | see 1.5                                                                                                                                                                                                                              |
| `Spinner`                        | indeterminate ring                                                                                                                                                                                                    | 14 px / 2 px stroke and 20 px / 2.5 px stroke, primary                                                                                                                                                                               |
| `StepperField` (TimeFieldColumn) | ▲ / two-digit editable box / ▼ / label, per-box validation                                                                                                                                                            | box 80 x 64, radius 12, `primaryContainer` 0.6 (0.9 focused), 32 px bold digits                                                                                                                                                      |
| `ProgressBar`                    | determinate                                                                                                                                                                                                           | 6 px tall, radius 3, primary on `surfaceVariant`                                                                                                                                                                                     |
| `StatusBadge`                    | coloured label                                                                                                                                                                                                        | radius 6, padding 8 / 3, 11 px bold                                                                                                                                                                                                  |
| `IconTile`                       | 44 px rounded square with an emoji / icon                                                                                                                                                                             | radius 12, tinted container alpha 0.4                                                                                                                                                                                                |
| `FjTextField`                    | the app text field (label, leading, placeholder, centred, error)                                                                                                                                                      | see 1.5; owned by the text-field spec                                                                                                                                                                                                |
| `HScrollRow`                     | horizontally scrollable chip row without a scrollbar                                                                                                                                                                  | spacing 6                                                                                                                                                                                                                            |
| `Divider`                        | 1 px or 0.5 px line                                                                                                                                                                                                   | `outlineVariant` at the given alpha                                                                                                                                                                                                  |

---

## 5. (b) Port units

| Unit                                                                 | Source (file: lines)                                                                                                        | Compose lines | Depends on widgets                                                                                                                        | Depends on units                                           | State from the presenter                                                                                                                                                                                                                                                                                    | Events it sends                                                                                                                                                                                                                                                                                                                    |
|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **M1 Media inspection states + quality and subtitle lists**          | `AddDownloadMedia.kt`: 63-326, 407-609                                                                                      | about 470     | LoadingBanner, Spinner, FilledButton, OutlineButton, TextLinkButton, InfoCard, SelectRow, M3Checkbox, RadioCard                           | M4 (playlist card for state 5), M2 (clip card for state 7) | `mediaState` (one of 7), banner title / subtitle, error text, engine card (`preparing`, title, status text), inspect button (`enabled`, label), qualities card (title, counter text, rows: index, label, detail line, selected), subtitles (rows: langCode, name, isAuto, selected; `anySelected`, `embed`) | `OnInspectMediaClicked`, `OnDownloadYtDlpClicked`, `OnMediaQualitySelectionToggled(index, selected)`, `OnSelectAllMediaQualitiesToggled(all)`, `OnSubtitleToggled(langCode, selected)`, `OnToggleEmbedSubtitles(embed)`                                                                                                            |
| **M2 Clip range card**                                               | `AddDownloadMedia.kt`: 328-405, 611-958; derived values from `AddDownloadModalDialog.kt`: 170-204                           | about 430     | InfoCard, M3Switch, Banner, TimeValueCard, QuickChip, HScrollRow, RadioCard                                                               | M3 (opens the picker)                                      | `clipVisible`, `clipEnabled`, title, hint, duration banner text, start / end display + friendly text + error flags, validation message, quick chips (label + start + end), mode title, two option titles and line lists, `clipFastMode`                                                                     | `OnToggleClipRange(enabled)`, `OnClipStartTimeChanged(time)`, `OnClipEndTimeChanged(time)`, `OnToggleClipFastMode(enabled)`, open start / end picker                                                                                                                                                                               |
| **M3 Time range picker dialog**                                      | `TimeRangePickerDialog.kt`: 1-510; launch arguments from `AddDownloadModalDialog.kt`: 206-244                               | about 550     | ModalLayer, DialogSurface, StepperField, Banner, M3FilterChip, HScrollRow, Divider, OutlineButton, FilledButton, TextLinkButton           | none                                                       | picker model: title, three box texts, arrow enabled flags, summary text, error text, chip list (label, visible), apply label, `isValid`                                                                                                                                                                     | to the presenter only: arrow / chip / typing / blur actions, apply, cancel; apply ends in `OnClipStartTimeChanged` or `OnClipEndTimeChanged`                                                                                                                                                                                       |
| **M4 Playlist, schedule, connections, audio convert**                | `PlaylistScheduleUi.kt`: 1-432; `AudioConvertSection.kt`: 1-76                                                              | about 510     | InfoCard, FjTextField, ChoiceChip, FormatChip, HScrollRow, M3Checkbox, TextLinkButton, FilledButton, Banner                               | none                                                       | playlist (title, summary, select-all label, folder text, `fromBrowser`, quality chips, rows: index, number text, title, duration text, selected; `showUseSingleVideo`); schedule (see section 6); connections (auto label, selected); audio convert (visible, title, chips)                                 | `OnPlaylistEntryToggled`, `OnPlaylistSelectAll`, `OnPlaylistFolderChanged`, `OnPlaylistQualityChanged`, `OnPlaylistUseSingleVideo`, `OnScheduleChanged(epochMs)`, `OnConnectionsChanged(n)`, `OnAudioConvertFormatSelected(format or null)`                                                                                        |
| **M5 Directory picker dialog** (skip if the native picker is chosen) | `DirectoryPickerDialog.kt`: 1-445                                                                                           | about 445     | ModalLayer, DialogSurface, M3FilterChip, HScrollRow, FjTextField, OutlineButton, FilledButton, TextLinkButton, Divider                    | none                                                       | picker model: current path, `canGoUp`, quick places (label, selected), subfolder rows (name, hidden), count text, hidden toggle, new-folder open / name / error                                                                                                                                             | to the presenter: up, quick place, enter folder, toggle hidden, toggle new folder, name changed, create, select, cancel; select ends in `OnDestinationPathChanged(path)` (Add dialog) or the settings path change                                                                                                                  |
| **M6 Small dialogs**                                                 | `DuplicateDownloadDialog.kt`, `FileOverwriteDialog.kt`, `MissingFileDialog.kt`, `RemoveConfirmationDialog.kt` (whole files) | about 615     | ModalLayer, DialogCard, AlertPanel, IconTile, InfoCard, StatusBadge, ProgressBar, FilledButton, OutlineButton, TextLinkButton, M3Checkbox | none                                                       | duplicate (name, status text, badge colours, percent text, show bar, progress); overwrite (file name, folder, new name, button label); missing (visible, message); remove (visible, title, message, `deleteFile`)                                                                                           | `OnResumeExistingDuplicateClicked`, `OnRestartDuplicateClicked`, `OnDismissDuplicateDialog`, `OnConfirmOverwriteClicked`, `OnSaveAsCopyClicked(newFileName)`, `OnDismissFileConflictDialog`, `OnRemoveClicked(id)` + `OnConfirmRemoveClicked(false)` (missing file), `OnConfirmRemoveClicked(deleteFile)`, `OnDismissRemoveDialog` |

M1 is the only unit that contains other units. Suggested order: widgets, M6, M4, M1, M2, M3, M5.

---

## 6. (c) UI-local state that should move into the Kotlin presenter, and pure helpers

### 6.1 State

| Today (Compose)                                                                                                                                                  | Where                                                       | Presenter field(s)                                         | Rules                                                                                                                                       |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `showStartTimePicker`, `showEndTimePicker`                                                                                                                       | `AddDownloadModalDialog.kt:165-168`                         | `timePicker: none / start / end` + the picker model below  | closed on apply and on dismiss; reset when the Add dialog closes                                                                            |
| `showDirectoryPicker` (Add dialog) and (Settings)                                                                                                                | `AddDownloadModalDialog.kt:163-164`, `SettingsPages.kt:267` | `dirPicker: none / addDialog / settings` + the model below | same                                                                                                                                        |
| time picker `hours`, `minutes`, `seconds`; per box `text`, `focused`                                                                                             | `TimeRangePickerDialog.kt:68-71`, `:443-444`                | `h`, `m`, `s`, three box texts                             | all rules of 2.4 (arrows, chips, typing, blur, validity, labels)                                                                            |
| schedule `showTimeFields`, `hourText`, `minuteText`, `isPm`, `dayOffset`, `timeError`                                                                            | `PlaylistScheduleUi.kt:203-208`                             | same six fields                                            | rules of 2.3.2; reset when the Add dialog closes                                                                                            |
| directory picker `currentPath`, `subdirectories`, `showHiddenFolders`, `isCreatingFolder`, `newFolderName`, `createFolderError`, `quickDirectories`, `parentDir` | `DirectoryPickerDialog.kt:58-78`                            | same                                                       | rules of 2.5; list refreshed when the path or the hidden flag changes; should run off the UI thread                                         |
| remove dialog `deleteFile`                                                                                                                                       | `RemoveConfirmationDialog.kt:35`                            | `removeDeleteFile`                                         | false every time the dialog opens                                                                                                           |
| `missingFileTaskId` (global)                                                                                                                                     | `App.kt:62-77`, `:786-797`                                  | `missingFileTaskId`                                        | set when opening a finished file that `PathResolver.fileExists` cannot find; cleared on remove / close; hidden if the task no longer exists |
| auto inspection `LaunchedEffect`                                                                                                                                 | `AddDownloadModalDialog.kt:261-278`                         | presenter reaction to URL / engine changes                 | condition in 2.1.1                                                                                                                          |
| derived clip values                                                                                                                                              | `AddDownloadModalDialog.kt:170-204`                         | computed                                                   | formulas in 2.1.6                                                                                                                           |
| audio-convert visibility and native format                                                                                                                       | `AddDownloadModalDialog.kt:430-446`                         | computed                                                   | formulas in 2.2                                                                                                                             |
| `preparing`, `isInputAudio`, `onUseSingleVideo` condition                                                                                                        | `AddDownloadMedia.kt:523-524`, `:568-577`, `:565`           | computed                                                   | formulas in 2.1.1                                                                                                                           |

Nothing in this area has filters, sorting or collapsed groups.

### 6.2 Pure helpers the presenter must reproduce

`androidUi/.../utils/Formatters.kt` is in the Compose module, so the native presenter cannot call
it as it is; the functions are plain Kotlin and can be moved or copied.

| Helper                                                                               | Source                                                         | Exact rule                                                                                                                                                                                                     |
|--------------------------------------------------------------------------------------|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ltr(text)`                                                                          | `Formatters.kt:5`                                              | empty -> empty; else U+2066 + text + U+2069                                                                                                                                                                    |
| `formatBytes(bytes)`                                                                 | `:10-23`                                                       | `<= 0` -> `"0 B"`; divide by 1024 while `>= 1024` up to TB; value truncated (not rounded) to one decimal `(v * 10).toLong() / 10.0`; `"$value $unit"` (a Double, so `"512.0 B"`, `"1.5 GB"`); wrapped in `ltr` |
| `formatSizeDisplay(bytes)`                                                           | `:28-40`                                                       | `<= 0` -> `""`; `mb = bytes / 1048576.0`; `mb >= 1` -> truncated to one decimal + `" MB"`; else `"${bytes / 1024} KB"` (integer division); wrapped in `ltr`                                                    |
| `formatSecondsToHhMm(sec)`                                                           | `:132-142`                                                     | negative -> 0; always `HH:MM:SS`, each part padded to 2 (hours can be longer)                                                                                                                                  |
| `parseHhMmToSeconds(text)`                                                           | `:147-153`                                                     | split on `:`; part 0 = hours, 1 = minutes, 2 = seconds, missing or non-numeric = 0; `h * 3600 + m * 60 + s`, at least 0. A two-part text is **hours:minutes**                                                  |
| `formatSecondsToDisplayFriendly(sec)`                                                | `:158-172`                                                     | negative -> 0; non-zero parts joined with spaces as `Nh`, `Nm`, `Ns`; all zero -> `"0s"`                                                                                                                       |
| `clockText(sec)` / `clock(sec)`                                                      | `AddDownloadMedia.kt:350-357`, `PlaylistScheduleUi.kt:403-409` | `h > 0` -> `h:MM:SS`, else `m:SS` (first part not padded); `clockText` clamps negatives to 0                                                                                                                   |
| `MediaQualityEntry.isAudioOnly`, `List.isAllAudio()`                                 | `domain/model/MediaQualityEntry.kt:16-20`                      | already in sharedLogic                                                                                                                                                                                         |
| quality detail line                                                                  | `AddDownloadMedia.kt:188-197`                                  | `qualityFormatLine(format.uppercase(), sizeText + durText)`, see 2.1.2                                                                                                                                         |
| qualities card title                                                                 | `AddDownloadMedia.kt:80-92`                                    | see 2.1.2                                                                                                                                                                                                      |
| `fullSizeText`                                                                       | `AddDownloadMedia.kt:918-919`                                  | `" (${formatBytes(size)})"` when the selected quality has `size > 0`, else `""`                                                                                                                                |
| time picker functions                                                                | `TimeRangePickerDialog.kt:63-115` and the click handlers       | see 2.4                                                                                                                                                                                                        |
| schedule prefill and `setTime`                                                       | `PlaylistScheduleUi.kt:236-249`, `:281-295`                    | see 2.3.2                                                                                                                                                                                                      |
| `currentEpochMs`, `localUtcOffsetMs`, `localTimeOnDayEpochMs`, `formatLocalSchedule` | `sharedLogic/.../core/util/LocalClock.kt`                      | already in sharedLogic (`localUtcOffsetMs` is an `expect` that needs a mingwX64 actual)                                                                                                                        |
| duplicate dialog badge and percent                                                   | `DuplicateDownloadDialog.kt:140-175`                           | table in 2.6; percent = `(progress * 100).toInt()`                                                                                                                                                             |
| `localizeStep`                                                                       | `sharedLogic/.../core/i18n/start/StepText.kt`                  | already in sharedLogic                                                                                                                                                                                         |
| folder join on entering a subfolder                                                  | `DirectoryPickerDialog.kt:355-357`                             | append `/` unless the path ends with `/` or `\` (better: use the platform separator in the port)                                                                                                               |

Slint cannot pad or format numbers itself, so every label in this area should arrive from the
presenter as a ready string.

---

## 7. Risks and open points for the port

1. **Emoji as icons.** This area uses about 20 colour emoji as icons. The prototype runs the
   `winit-software` renderer (`desktopNative/main.cpp:152-153`); whether it draws colour emoji
   was not checked here. If it does not, each one needs a vector or image replacement.
2. **Shadows and the focus glow.** Dialog elevation (8 / 12 dp) and the text-field glow need
   drop shadows; support in the software renderer was not checked here.
3. **Text heights.** Compose gives every unstyled text line 24 dp. A port that uses natural line
   heights will come out visibly more compact than the Compose app (rows of 56 dp become about
   40). Decide once whether to copy the Compose metrics or accept the tighter look.
4. **48 dp slots.** Buttons and clickable chips reserve 48 dp of layout around a 32 to 40 dp
   visual. The same decision as point 3.
5. **AlertDialog colours.** The two alert dialogs use the M3 baseline `surfaceContainerHigh`
   (purple-grey), which does not match the rest of the app's palette. Copy as is, or use the
   app's `surface`?
6. **Right-to-left.** Compose mirrors layouts for Arabic and Persian automatically. Each row in
   this area must be mirrored by hand in Slint if it has no automatic mirroring.
7. **Isolate characters.** Sizes are wrapped in U+2066 / U+2069; check that the Slint text
   renderer does not draw them as boxes.
8. **Untranslated texts** exist today (list in 3.7). Port as they are unless the owner wants them
   translated.
9. **Directory picker quirks** (mixed separators, no drive switching, UI-thread listing, layout
   squeeze) are described in 2.5; the port can fix them without changing the look.
10. Values marked "derived" (dialog widths, row heights, tinted surface colours, alignment
    offsets) come from reading the layout code and the M3 sources, not from a screenshot.
