# 06 - UI porting spec: Add download dialog (frame, sections, link list, torrent) and the app's text field

Scope: these five Compose files, all under
`androidUi/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/ui/`:

| File                        | Lines | Content                                                                                                                                          |
|-----------------------------|-------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `AddDownloadModalDialog.kt` | 522   | `AddDownloadModalDialog`: dialog frame, dialog-local state, derived flags, section order                                                         |
| `AddDownloadSections.kt`    | 665   | header, URL input, engine selector, file name, checksum, destination, action row, loading banner, inline error, quick folder button, engine chip |
| `AddDownloadLinkList.kt`    | 339   | several-links list (rows, video quality line, title / host helpers)                                                                              |
| `AddDownloadTorrent.kt`     | 319   | torrent inspection block, leech toggle, torrent files card                                                                                       |
| `FetchJetTextField.kt`      | 402   | the app's text field, field buttons, "clear focus on tap outside"                                                                                |

Out of scope (called from the dialog, specified by other documents; only their interface and
spacing are recorded here): `MediaInspectionSection` (`AddDownloadMedia.kt:408`),
`ScheduleStartSection` (`PlaylistScheduleUi.kt:202`), `ConnectionsSection`
(`PlaylistScheduleUi.kt:413`), `AudioConvertSection` (`AudioConvertSection.kt:25`),
`DirectoryPickerDialog` (`DirectoryPickerDialog.kt:52`), `TimeRangePickerDialog`
(`TimeRangePickerDialog.kt:55`), `AppScrollbar` (`AppScrollbar.kt:44`).

How the values were obtained: every layout value is read from the Compose source. Material 3
defaults were read from the source jars in the local Gradle cache
(`org.jetbrains.compose.material3:material3:1.12.0-alpha03`, `org.jetbrains.compose.ui:ui:1.12.2`),
not from memory. Heights marked "derived" are computed from those defaults; they were not
measured on a running app (the app was not started for this document).

Units: 1 dp = 1 Slint logical `px`; 1 sp = 1 px (desktop font scale is 1).

---

## 1. Global facts the whole area depends on

### 1.1 Window and where the dialog lives

- Desktop window: `rememberWindowState(width = 1100.dp, height = 750.dp)`
  (`desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt:769`). **No minimum window
  size is set anywhere** (no `minimumSize` in `desktopApp (removed)` or `androidUi`). The Slint prototype
  already uses `min-width: 900px; min-height: 500px` (`desktopNative/ui/app.slint:415`), which is
  stricter than the Compose app.
- The dialog is shown from `App.kt:553-773`:
  `if (state.isAddDialogVisible) AddDownloadModalDialog(...)`.
  Every parameter is a field of `DashboardUiState` and every callback is one `DashboardUiEvent`
  (table in section 8).
- It is a Compose `Dialog` (common `androidx.compose.ui.window.Dialog`), which on desktop is a
  layer **inside the main window**, not an OS window:
    - `DialogProperties(usePlatformDefaultWidth = false)`; the other defaults apply:
      `dismissOnBackPress = true` (Esc closes), `dismissOnClickOutside = true` (primary-button
      release on the scrim closes), scrim `Color.Black` alpha `0.6`.
    - Content is centred in the window.
    - Appear animation (`Dialog.skiko.kt`): 200 ms ease-out, content alpha 0.2 -> 1, scale 0.95 ->
      1,
      translate-Y 10 dp -> 0; the scrim alpha follows `0.6 * (0.2 + 0.8 * progress)`.
      Disappear: 100 ms, reversed.
    - Closing (Esc, scrim click, the header X, Cancel) always calls `onDismiss` =
      `DashboardUiEvent.OnToggleAddDialog(false)`.
- Layout direction: `App.kt:181-185` provides RTL for Arabic / Persian. All `start` / `end`
  paddings and Row orders below mirror in RTL. Sizes from `formatBytes` are wrapped in the
  Unicode isolates U+2066 / U+2069 (`utils/Formatters.kt:5`).
- Breakpoints: the Add dialog has **no** 720 dp switch. The only width rule is the card width
  formula in 3.3. (The app's own breakpoints are 680 dp compact, 1100 dp medium, 1280 dp side
  panel, `App.kt:194-199`; they do not affect this dialog.)

### 1.2 Theme tokens (`theme/Color.kt`, `theme/Theme.kt`)

| Token                                                | Dark      | Light     |
|------------------------------------------------------|-----------|-----------|
| primary                                              | `#00E5FF` | `#0284C7` |
| onPrimary                                            | `#031625` | `#FFFFFF` |
| primaryContainer                                     | `#0C3D60` | `#E0F2FE` |
| surface                                              | `#0F1728` | `#FFFFFF` |
| surfaceVariant                                       | `#172238` | `#F1F5F9` |
| onSurface                                            | `#F1F5F9` | `#0F172A` |
| onSurfaceVariant                                     | `#94A3B8` | `#475569` |
| outline                                              | `#334155` | `#94A3B8` |
| outlineVariant                                       | `#1E2B42` | `#CBD5E1` |
| error                                                | `#F87171` | `#DC2626` |
| onError                                              | `#450A0A` | `#FFFFFF` |
| errorContainer                                       | `#451A1A` | `#FEE2E2` |
| surfaceContainer (not overridden: Material baseline) | `#211F26` | `#F3EDF7` |

`desktopNative/ui/theme.slint` has all of these except `on-error`, `error-container` and
`surface-container`; they must be added. `surfaceContainer` is only used by the quality dropdown
menu (6.3) and is the purple-grey Material baseline, because the theme does not override it.

Pre-composited colours (alpha token over its real background), for a renderer where stacking
translucent rectangles is expensive:

| Use                         | Expression                                        | Dark                  | Light                 |
|-----------------------------|---------------------------------------------------|-----------------------|-----------------------|
| Field container, idle       | surfaceVariant 0.35 over surface                  | `#121B2E`             | `#FAFCFD`             |
| Field container, focused    | primary 0.06 over surface                         | `#0E2335`             | `#F0F8FC`             |
| Field border, idle          | outlineVariant 0.7                                | `#1A263C`             | `#D9E1E9`             |
| Header icon tile            | primaryContainer 0.4 over surface                 | `#0E263E`             | `#F3FAFF`             |
| Chip / quick folder, idle   | surfaceVariant 0.5 over surface                   | `#131C30`             | `#F8FAFC`             |
| Chip / card / dialog border | outlineVariant 0.6 over surface                   | `#182338`             | `#E0E6ED`             |
| Loading banner              | primaryContainer 0.3 / border primary 0.4         | `#0E2239` / `#09697E` | `#F6FBFF` / `#9ACEE9` |
| Inline error                | errorContainer 0.35                               | `#221823`             | `#FFF5F5`             |
| Torrent error card          | errorContainer 0.25 / border error 0.6            | `#1C1824` / `#9B4D54` | `#FFF8F8` / `#EA7D7D` |
| Torrent files card          | surfaceVariant 0.35                               | `#121B2E`             | `#FAFCFD`             |
| Torrent file row, selected  | primaryContainer 0.4 over surface                 | `#0E263E`             | `#F3FAFF`             |
| Link list panel             | surfaceVariant 0.25 / divider outlineVariant 0.35 | `#111A2C` / `#162034` | `#FCFCFE` / `#EBEEF4` |
| Quality chip (on link list) | primary 0.08 / border primary 0.5                 | `#102A3D` / `#088096` | `#E8F2FA` / `#7FC0E2` |
| Paste chip (on idle field)  | primary 0.12                                      | `#103347`             | `#DCEEF7`             |

### 1.3 Typography rules (Material 3 defaults, no custom `Typography`)

`FetchJetTheme` passes only a colour scheme, so the Material 3 default type scale applies. Font
family is the platform default (Segoe UI on Windows; the prototype sets
`default-font-family: "Segoe UI"`).

| Role                                                                             | Size | Line height | Weight | Letter spacing |
|----------------------------------------------------------------------------------|------|-------------|--------|----------------|
| bodyLarge (the default `LocalTextStyle`)                                         | 16   | 24          | 400    | 0.5            |
| bodyMedium                                                                       | 14   | 20          | 400    | 0.2            |
| bodySmall                                                                        | 12   | 16          | 400    | 0.4            |
| labelLarge (inside `Button`, `OutlinedButton`, `TextButton`, `DropdownMenuItem`) | 14   | 20          | 500    | 0.1            |

Rules that decide the heights in this document:

1. A `Text(...)` with no `style` inherits **bodyLarge**. Passing only `fontSize = 12.sp` changes
   the size but **keeps line height 24 sp and letter spacing 0.5 sp**.
2. Material 3 uses `LineHeightStyle(alignment = Center, trim = None)`
   (`tokens/TypographyTokens.kt:329`): every line box is exactly the line height, with the glyphs
   centred in it. So a bare one-line `Text` is **24 dp tall whatever its font size** (derived).
3. Inside a `Button` / `OutlinedButton` / `TextButton` / `DropdownMenuItem` the inherited style is
   labelLarge: line box 20 dp, letter spacing 0.1 sp.
4. Weights used: `Medium` 500, `SemiBold` 600, `Bold` 700. Segoe UI has no 500 face; which face
   Skia picks for 500 was not checked (see Risks).

Shorthand used below: **T (size, weight, colour)** = bare `Text`, line box 24 dp, letter spacing
0.5; **TB (...)** = text inside a button, line box 20 dp, letter spacing 0.1.

### 1.4 Material 3 component defaults used here (verified in the 1.12.0-alpha03 sources)

- **Minimum interactive size**: `LocalMinimumInteractiveComponentSize = 48.dp`
  (`InteractiveComponentSize.kt:264`), not overridden by the app. Every `Surface(onClick = ...)`,
  `Button`, `OutlinedButton`, `TextButton`, `IconButton` and `Checkbox` reserves **at least
  48 x 48 dp of layout space** and centres its (smaller) visual inside it. A `Modifier.size(...)`
  placed by the caller wins (the header X is 32 dp, the link-row checkbox is 40 dp).
  `ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled` is `false` (default), so the
  denser desktop sizes are not active.
- **Button / OutlinedButton**: visual min size 58 x 40 dp, content padding 24 dp horizontal,
  8 dp vertical, content centred, text style labelLarge. Default shape is a full pill; every
  button here passes its own shape. Filled button: container primary, content onPrimary,
  elevation 0 (1 dp shadow on hover), disabled content onSurfaceVariant alpha 0.38. Outlined:
  transparent container, default content onSurfaceVariant, disabled content onSurfaceVariant 0.38.
- **TextButton**: same min size 58 x 40, default content padding 12 dp horizontal / 8 dp vertical,
  transparent container, content colour primary, shape pill (hover layer is a pill).
- **IconButton**: 40 dp container; here forced to 32 dp by the caller.
- **Checkbox**: box 20 x 20 dp drawn with a 2 dp stroke and 2 dp corner radius, 2 dp padding (24
  dp), state-layer circle 40 dp, layout 48 x 48. Default colours: checked box and border
  primary, check mark onPrimary, unchecked border onSurfaceVariant, unchecked fill transparent.
  `CheckboxDefaults.colors(checkedColor, uncheckedColor)` replaces box / border colours only; the
  check mark stays onPrimary.
- **CircularProgressIndicator** (indeterminate): colour primary, track transparent, stroke cap
  round, default diameter 40 dp and stroke 4 dp (every use here sets its own size and stroke).
  One cycle is 6000 ms: the arc sweeps between 10 % and 87 % of the circle while the whole
  thing rotates.
- **HorizontalDivider**: 1 dp.
- **Card**: only a shadow elevation, no tonal tint (`Card.kt:88-95`), so
  `containerColor = surface` stays exactly `surface`.
- **Surface** draw order (`Surface.kt:544-563`): shadow, then background, then border on top,
  content clipped to the shape.
- **State layers** (ripple indication on anything clickable): hover = content colour at 0.08,
  pressed / focused = 0.10 (`tokens/StateTokens.kt`). On these surfaces the content colour is
  onSurface, except filled buttons (onPrimary / onError) and text buttons (primary).
- **DropdownMenu**: container `surfaceContainer`, shape 4 dp, shadow 3 dp, 8 dp vertical padding,
  item min height 48 dp, item width 112..280 dp, item horizontal padding 12 dp, item text
  labelLarge in onSurface. Opens with scale 0.8 -> 1 and a fade, both driven by the Material
  motion scheme's short springs (`FastSpatial` / `FastEffects`, `Menu.kt:1742`), not a fixed
  duration; about 150 ms ease-out is a fair stand-in.
- **Text selection** (all fields): selection background primary alpha 0.4, handle primary.

### 1.5 Interaction that is the same everywhere in this area

- No tooltips, no double-click, no long-press, no Ctrl / Shift click and **no key handlers** are
  defined in these five files. Keyboard = Esc closes the dialog (an open dropdown closes first),
  Tab moves focus in declaration order (what Tab does inside the multi-line URL field was not
  checked), Space / Enter activate the focused button or checkbox, the standard text-editing
  keys work in fields. **Enter does not submit the dialog.**
- Nothing is focused when the dialog opens (no focus request).
- Text fields have the Compose Desktop default context menu (Cut / Copy / Paste / Select All).
  The dialog content is wrapped in `ProvideSafeClipboard` (`SafeClipboard.kt:12`), which only
  swallows clipboard exceptions.
- The mouse cursor is an I-beam over the whole field body; clicking anywhere in the field (also on
  its leading emoji) focuses it.

---

## 2. `FetchJetTextField.kt`

### 2.1 `FetchJetTextField` (String overload, lines 71-124; `TextFieldValue` overload, 126-175)

```kotlin
internal fun FetchJetTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, placeholder: String? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true, maxLines: Int = if (singleLine) 1 else 4,
    enabled: Boolean = true, error: String? = null, centered: Boolean = false,
    height: Dp = 48.dp, shape: Shape = RoundedCornerShape(12.dp),
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions, keyboardActions, visualTransformation = VisualTransformation.None)
```

The `TextFieldValue` overload is identical but has no `centered` and no `visualTransformation`
(it exists so the caller controls the caret: the URL and destination fields put the caret at the
end after a programmatic change).

Purpose: the only text field of the app (CLAUDE.md: "Do not add new OutlinedTextFields"). Used
in this area by the URL, file name, checksum, destination and link-list folder fields; elsewhere
by settings, the directory picker, the schedule fields, etc.

It is a `BasicTextField` whose decoration is `FieldBody`, wrapped in `FieldFrame`. One
`MutableInteractionSource` is shared by the frame (hover) and the text field (focus, hover).

### 2.2 `FieldFrame` (private, lines 244-343): label, box, error

Tree (top to bottom), inside `Column(modifier.alpha(if (enabled) 1f else 0.6f))`:

1. **Label** (only when `label != null`): `Text` 11 sp, SemiBold, letter spacing 0.3 sp,
   max 1 line, ellipsis, padding start 4, bottom 5. Line box 24 dp (derived), so the label block
   is 29 dp. Colour animates (tween 180 ms): error -> `error`; focused -> `primary`;
   else `onSurfaceVariant`.
2. **Box** `fillMaxWidth`, clipped to `shape` (default radius 12):
    - background (tween 180): focused -> `primary 0.06` over `surface`; else
      `surfaceVariant 0.35` over `surface`. (Opaque: both are composited over `surface`.)
    - border colour (tween 180): error -> `error`; focused -> `primary`; hovered -> `outline`;
      else `outlineVariant 0.7`.
    - border width (tween 180): 1.5 dp when focused or error, else 1 dp.
    - glow: `dropShadow(shape, radius 10 dp, spread 0, offset (0, 2 dp), colour accent,
     alpha 0.28 * glow)` where `glow` animates 0 -> 1 (tween 220) when focused or error, and
      `accent` = `error` if error else `primary`. Not drawn while `glow <= 0.01`.
    - `hoverable(interaction, enabled)`.
3. **Error text** (`AnimatedVisibility`, fade + expand / shrink vertically): 11 sp, `error`,
   padding start 4, top 4, end 4. Shown when `error` is not null / blank.

Passed to the inner field: text colour `onSurface`, caret colour `accent`, the `focused` flag.

Focus bookkeeping: the frame records its own window bounds and, while focused, publishes them in
the private singleton `FocusedField.bounds` (cleared on dispose / blur). Used by 2.6.

### 2.3 `FieldBody` (private, lines 345-402): what is inside the box

`Row(verticalAlignment = CenterVertically)`:

- height: `singleLine` -> exactly `height` (48 dp); multi-line -> `heightIn(min = height)`.
- padding: start 10 dp if `leading` else 14 dp; end 6 dp if `trailing` else 14 dp;
  top / bottom 0 when single-line, 10 dp when multi-line.
- `leading` slot: centred, padding end 8 dp, alpha animates 0.75 -> 1 when focused (tween 180).
- text area `weight(1f)`, content aligned centre-start (centre when `centered`):
    - placeholder when the value is empty and `placeholder != null`: same `textStyle`, colour
      `onSurfaceVariant 0.65`, max 1 line, ellipsis. It stays visible while focused and empty.
    - the editable text: `textStyle` (bodyMedium 14 / 20, letter spacing 0.2) in `onSurface`,
      start-aligned (centre when `centered`).
- `trailing` slot: centred, padding start 6 dp.

Derived heights: single-line field 48 dp. Multi-line field (URL field, `maxLines = 3`):
1 line -> 48 dp (min), 2 lines -> 60 dp, 3 lines -> 80 dp; past 3 lines the text scrolls inside
the field.

States: `enabled = false` -> whole column at alpha 0.6, no hover, not editable, not focusable.

### 2.4 `FieldIconButton(text, onClick, tint = onSurfaceVariant)` (lines 177-200)

A 30 dp circle; background animates (tween 150) transparent -> `onSurface 0.08` on hover;
clickable (ripple inside the circle); content `Text(text, 14 sp, tint)`. No tooltip, no min
interactive size (it is a plain `Box`). Used with "✕" (clear), "↺" (reset name), "📂" (browse).

### 2.5 `FieldChipButton(text, onClick)` (lines 202-221)

`Box` clipped to radius 8, background `primary 0.12`, padding 10 dp horizontal / 5 dp vertical,
`Text` 12 sp SemiBold `primary`, max 1 line. Height 34 dp (derived: 24 + 10). Used for
"📋 Paste".

### 2.6 `Modifier.clearFocusOnTapOutside()` (lines 227-242)

Applied on the app root `Surface` (`App.kt:188`), the Add dialog `Card`
(`AddDownloadModalDialog.kt:307`) and the directory picker. On a tap (pointer down then up,
moved less than the touch slop, observed on the Initial pass so it never consumes the event):
if a field is focused and the release point is outside that field's bounds, focus is cleared.
Result: clicking any non-field area of the dialog removes the caret; clicking another control
still triggers that control.

Slint: a `TouchArea` behind the dialog content cannot see clicks that children handle. The
equivalent is a pointer-event hook on the dialog root, or calling `clear-focus()` from every
non-field click handler; `FocusScope`-based fields lose focus automatically only when another
focusable element takes it.

---

## 3. `AddDownloadModalDialog.kt`: `AddDownloadModalDialog` (lines 43-522)

### 3.1 Parameters

All parameters map one-to-one to `DashboardUiState` fields or events (call site `App.kt:554-772`):

State in: `inputUrl`, `destinationPath`, `disableTorrentUpload`, `detectedEngine`, `isLoading`,
`errorMessage`, `isInspectingTorrent`, `inspectedTorrentName`, `inspectedTorrentFiles`,
`inspectError`, `isInspectingMedia`, `courseReading`, `inspectedMediaQualities`,
`inspectedSubtitles`, `embedSubtitles`, `inspectMediaError`, `isYtDlpInstalled`, `ytDlpStatus`,
`isDownloadingYtDlp`, `suggestedFileName`, `fileNameOverride`, `sortIntoFolders`,
`checksumInput`, `clipStartTime`, `clipEndTime`, `isClipRangeEnabled`, `mediaDurationSeconds`,
`clipFastMode`, `audioConvertFormat`, `inspectedPlaylist`, `playlistQuality`, `linkList`,
`linkListFolder`, `scheduleStartMs`, `selectedConnections`, `defaultConnections`, plus
`pathResolver: PathResolver` (a `sharedLogic` object read directly).

### 3.2 UI-local state and logic (lines 122-294)

| State                                               | Init                                                                                                    | Logic                                                                                                                                                                 |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `defaultDownloads`, `defaultDocs`, `defaultDesktop` | `pathResolver.getDownloadsDirectory()` / `getDocumentsDirectory()` / `getDesktopDirectory()`, read once | passed to `DestinationSection`                                                                                                                                        |
| `urlFieldState: TextFieldValue`                     | `inputUrl`, caret at end                                                                                | `LaunchedEffect(inputUrl)`: when `inputUrl != field text`, replace the text and put the caret at the end                                                              |
| `pathFieldState: TextFieldValue`                    | `destinationPath`, caret at end                                                                         | same rule with `destinationPath`                                                                                                                                      |
| `showDirectoryPicker`                               | false                                                                                                   | true -> `DirectoryPickerDialog(initialPath = destinationPath, pathResolver, onDirectorySelected = { onDestinationPathChanged(it); close }, onDismissRequest = close)` |
| `showStartTimePicker`, `showEndTimePicker`          | false                                                                                                   | open `TimeRangePickerDialog` (below); toggled by `MediaInspectionSection`                                                                                             |
| `dialogScroll`                                      | `rememberScrollState()`                                                                                 | the dialog's vertical scroll                                                                                                                                          |

Derived values (pure, must move to the presenter, see 11.2):

- `fullVideoDurationHhMm` = `formatSecondsToHhMm(mediaDurationSeconds)` if `> 0` else `"00:10:00"`.
- `currentStartSec` = `parseHhMmToSeconds(clipStartTime.ifBlank { "00:00" })`.
- `currentEndSec` = `parseHhMmToSeconds(clipEndTime.ifBlank { fullVideoDurationHhMm })`.
- `isStartAfterEnd` = `isClipRangeEnabled && currentStartSec >= currentEndSec`.
- `isEndExceedingVideo` =
  `isClipRangeEnabled && mediaDurationSeconds > 0 && currentEndSec > mediaDurationSeconds`.
- `isStartExceedingVideo` =
  `isClipRangeEnabled && mediaDurationSeconds > 0 && currentStartSec >= mediaDurationSeconds`.
- `isClipRangeValid` = `!isClipRangeEnabled || none of the three`.
- `clipValidationErrorMessage` (first match): not enabled -> null; start after end ->
  `Strings.clipStartAfterEnd(start or "00:00", end or fullVideoDurationHhMm)`; end exceeds ->
  `Strings.clipEndExceedsVideo(end or full, full)`; start exceeds ->
  `Strings.clipStartExceedsVideo(full)`.

Time pickers (lines 206-244):

- Start: title `"⏱️ " + Strings.setStartTimeTitle`,
  `initialHhMm = clipStartTime.ifBlank { "00:00" }`,
  `minTimeSeconds = 0`, `maxDurationSeconds = maxForStart.coerceAtLeast(0)` where
  `maxForStart = if (currentEndSec > 0) (if (mediaDurationSeconds > 0) min(mediaDurationSeconds, currentEndSec - 1) else currentEndSec - 1) else mediaDurationSeconds`.
  Selected -> `OnClipStartTimeChanged(selected)` and close.
- End: title `"⏱️ " + Strings.setEndTimeTitle`,
  `initialHhMm = clipEndTime.ifBlank { fullVideoDurationHhMm }`,
  `maxDurationSeconds = mediaDurationSeconds`, `minTimeSeconds = currentStartSec`. Selected ->
  `OnClipEndTimeChanged(selected)` and close.

**Auto-inspect** (`LaunchedEffect(inputUrl, detectedEngine)`, lines 261-278). Runs when the
dialog appears and every time `inputUrl` or `detectedEngine` changes, with the state of that
moment. If `inputUrl.isNotBlank()`:

- `BITTORRENT`: if `inspectedTorrentFiles.isEmpty() && !isInspectingTorrent && inspectError == null`
  -> `OnInspectTorrentClicked`.
- `MEDIA_STREAM`: if `inspectedMediaQualities.isEmpty() && inspectedPlaylist == null &&
  !isInspectingMedia && inspectMediaError == null && isYtDlpInstalled` -> `OnInspectMediaClicked`.
- `HTTP_DIRECT`: nothing.

This is UI-triggered business behaviour: without it a pasted magnet or video link is never
inspected. It must be reproduced in the presenter (11.1).

Gating flags (lines 280-294):

```
isUrlValid      = inputUrl.isNotBlank()
showsLinkList   = linkList.isNotEmpty()
listInspecting  = showsLinkList && linkList.any { it.isSelected && it.isBeingInspected }
isInspectionComplete =
  if (showsLinkList) linkList.any { it.isSelected } && !listInspecting &&
                     linkList.none { it.isSelected && it.needsRetry }
  else when (detectedEngine) {
    BITTORRENT   -> inspectedTorrentFiles.isNotEmpty() && inspectedTorrentFiles.any { it.isSelected } && inspectError == null
    MEDIA_STREAM -> inspectMediaError == null && (inspectedPlaylist?.entries?.any { it.isSelected } == true ||
                    (inspectedMediaQualities.isNotEmpty() && inspectedMediaQualities.any { it.isSelected }))
    HTTP_DIRECT  -> true }
isInspecting    = isInspectingTorrent || isInspectingMedia || listInspecting
hasActiveError  = errorMessage != null || inspectError != null || inspectMediaError != null
canStartDownload = isUrlValid && isInspectionComplete && !isLoading && !isInspecting && !hasActiveError && isClipRangeValid
```

(`LinkListEntry.isBeingInspected = isInspecting || isInspectingMedia`;
`needsRetry = engine == MEDIA_STREAM && mediaInspectFailed`, `DashboardContract.kt:69-73`.)

### 3.3 Frame (lines 296-321)

```
Dialog (scrim black 0.6, centred)
  Card
    modifier: widthIn(min 340, max 660) . fillMaxWidth(0.94) . heightIn(max 720)
              . padding(vertical 12) . clearFocusOnTapOutside()
    shape RoundedCornerShape(20), container = surface,
    border 1 dp outlineVariant 0.6, shadow elevation 12 dp
    Box(fillMaxWidth)
      Column(fillMaxWidth . verticalScroll(dialogScroll) . padding(20))   <- sections, 3.4
      Box(matchParentSize) { AppScrollbar(dialogScroll, align CenterEnd) }
```

- **Width**: `fillMaxWidth(0.94f)` is applied to the max already capped at 660, so
  `width = clamp(0.94 * min(660, windowWidth), min(340, windowWidth), windowWidth)`.
  For any window 660 dp or wider the card is **620 dp** wide (620.4), not 660. Content width
  inside the 20 dp padding: **580 dp**. Below 660 dp the card is 94 % of the window, never
  under 340 dp (window under about 362 dp: 340 dp, then the window width).
- **Height**: wraps its content; the outer box is at most `min(720, windowHeight)`, and 12 dp of
  it is transparent padding above and below, so the visible card is at most **696 dp** tall (less
  when the window is shorter). Longer content scrolls inside the card.
- Shadow: Compose `shadowElevation = 12 dp` (soft ambient + key shadow below). Suggested Slint
  approximation: `drop-shadow-blur: 24px; drop-shadow-offset-y: 8px; drop-shadow-color: #00000055`.
- Scrollbar (`AppScrollbar.kt`, desktop only, only when the content overflows): a 12 dp wide
  hover zone on the card's end edge with 2 dp vertical padding; thumb 6 dp wide, radius 3,
  min length 28 px, colour `onSurface` alpha 0.28, animating to 0.55 while hovered or scrolling
  (tween 180). Drag the thumb to scroll; click the track to jump (thumb centre goes to the
  click). It overlays the 20 dp end padding, never the content.
- Mouse wheel scrolls the card. The 140 dp torrent file list (5.3) and the 3-line URL field
  scroll on their own first (nested scrolling).

### 3.4 Section order inside the scrolling column (lines 326-513)

`S(n)` = vertical `Spacer` of n dp. Conditions are exact.

1. `AddDialogHeader(onDismiss)` (4.1)
2. S (16)
3. `UrlInputSection(inputUrl, urlFieldState, onUrlChanged)` (4.2)
4. S (14)
5. If `showsLinkList`:
    - `LinkListSection(...)` (6.1), then S (14)

   Else:
    - `EngineSelector(detectedEngine, onEngineSelected)` (4.3)
    - `TorrentInspectionSection(...)` (5.1; draws only when engine is `BITTORRENT`, starts with its
      own S (12))
    - `MediaInspectionSection(...)` (other document; draws only when engine is `MEDIA_STREAM`,
      starts with its own S (12))
    - S (14)
6. `ScheduleStartSection(scheduleStartMs, onScheduleChanged)` (other document), then S (14). Always
   shown.
7. If `detectedEngine == HTTP_DIRECT && !showsLinkList`:
   `ConnectionsSection(selectedConnections, defaultConnections, onConnectionsChanged)` (other
   document), then S (14).
8. If `showsAudioConvert`:
   `AudioConvertSection(selected = audioConvertFormat, nativeFormat = nativeAudioFormat, forAll = audioConvertForAll, onSelected)`
   (other document; ends with its own S (14)), where
    - `audioConvertForAll = showsLinkList || inspectedPlaylist != null`
    - `showsAudioConvert` = link list: any selected entry whose selected quality
      (`mediaQualities.firstOrNull { it.isSelected }`) `isAudioOnly`; playlist:
      `playlistQuality == PlaylistQuality.AUDIO`; otherwise `detectedEngine == MEDIA_STREAM &&
     !isInspectingMedia && inspectedMediaQualities.firstOrNull { it.isSelected }?.isAudioOnly == true`
    - `nativeAudioFormat` = link list: the formats of the selected audio-only qualities of the
      selected entries, `distinct().singleOrNull()` (null when they differ); playlist:
      `playlistQuality.extension`; otherwise the selected quality's `format`.
9. If `!showsLinkList && inspectedPlaylist == null`: `FileNameSection(...)` (4.4; draws only when a
   name exists, ends with its own S (14)).
10. If `!showsLinkList && inspectedPlaylist == null && detectedEngine == HTTP_DIRECT`:
    `ChecksumSection(checksumInput, onChecksumChanged)` (4.5; ends with its own S (14)).
11. `DestinationSection(...)` (4.6). Always shown.
12. `TorrentLeechToggle(detectedEngine, disableTorrentUpload, onDisableTorrentUploadToggled)` (5.2;
    engine `BITTORRENT` only, also in link-list mode only if `detectedEngine` is torrent, which it
    never is there: `OnUrlChanged` forces `HTTP_DIRECT` when a list exists).
13. S (20)
14. `AddDialogActions(...)` (4.7) with
    `linkCount = if (showsLinkList) linkList.count { it.isSelected } else 0`.

---

## 4. `AddDownloadSections.kt`

Section labels all share one style, called **SectionLabel** below:
T (12 sp, SemiBold, onSurface), line box 24 dp, followed by S (6).

### 4.1 `AddDialogHeader(onDismiss)` (lines 49-98)

`Row(fillMaxWidth, SpaceBetween, CenterVertically)`; height 48 dp (derived):

- Left group `Row(CenterVertically)`:
    - 36 x 36 dp tile, radius 10, background `primaryContainer 0.4`, centred `Text("⚡", 18 sp)`.
    - S (10) horizontal.
    - `Column`: `Strings.addDialogTitle` ("Add New Download") 18 sp Bold onSurface;
      `Strings.addDialogSubtitle` ("Enter link to start downloading") 11 sp onSurfaceVariant.
      Two 24 dp line boxes.
- Right: `IconButton` forced to 32 x 32 dp, circle, background `surfaceVariant`, content
  `Text("✕", 14 sp, Bold, onSurfaceVariant)`; hover state layer; click -> `onDismiss`.

### 4.2 `UrlInputSection(inputUrl, urlField, onUrlChanged)` (lines 100-200)

1. Label row `Row(fillMaxWidth, CenterVertically)`:
    - `Strings.addLinkLabel` ("Link"): 12 sp SemiBold onSurface, max 1 line, `weight(1f)`.
    - If `TextFilePicker.isSupported` (true on a desktop with a screen): `TextButton`, content
      padding 8 dp horizontal / 2 dp vertical, text `"📄 " + Strings.addImportTxt`
      ("📄 Import .txt") 12 sp SemiBold primary. Click -> `TextFilePicker.pick { text -> }`: when
      the text is not null / blank, set the field to it (caret at end) and `onUrlChanged(text)`.
      The desktop picker is the native file dialog titled "Import links" filtered to `*.txt`;
      it returns the **file content**.
    - If `AppFeatures.torrent && TorrentFilePicker.isSupported`: same button style,
      `"📂 " + Strings.openTorrentFile` ("📂 Open .torrent file"). Click ->
      `TorrentFilePicker.pick { path -> }`: when not null / blank, set the field to the **path**
      and `onUrlChanged(path)`. Native dialog titled "Open .torrent file", filter `*.torrent`.
    - Derived: each text button is 40 dp tall visually (min height) inside a 48 dp layout box, at
      least 58 dp wide (text width + 16), so the label row is 48 dp tall. The buttons sit side by
      side with no gap other than their own padding.
2. S (6)
3. `FetchJetTextField` (TextFieldValue overload), `fillMaxWidth`, `singleLine = false`,
   `maxLines = 3`, placeholder `Strings.urlPlaceholder` ("Paste links or magnets"), leading
   `Text("🔗", 16 sp)`.
    - On every edit: store the new value locally; if its text differs from `inputUrl` ->
      `onUrlChanged(text)` = `OnUrlChanged(text)`.
    - Trailing: if the text `isNotBlank()` -> `FieldIconButton("✕")`: field = "",
      `onUrlChanged("")`.
      Else -> `FieldChipButton("📋 " + Strings.addPaste)` ("📋 Paste"): read
      `getClipboardText()?.trim()` (a `sharedLogic` suspend function); when not null / blank, set
      the field (caret at end) and `onUrlChanged(clip)`.
    - Enter inserts a new line (several links, one per line).

What `OnUrlChanged` does in the view model (`DashboardAddDialogEvents.kt:54-118`), because the
field must follow it: links are extracted from the text; when the text contains whitespace, holds
exactly one link and grew by more than one character at once (a paste), `inputUrl` becomes just
that link (the field text is then replaced through the `LaunchedEffect(inputUrl)` rule in 3.2);
two or more links build `linkList` and force `detectedEngine = HTTP_DIRECT`; inspection results,
clip, schedule, connections, file name override and checksum are all reset.

### 4.3 `EngineSelector(detectedEngine, onEngineSelected)` (lines 202-239) and
`EngineChoiceChip` (626-665)

- SectionLabel `Strings.addDownloadEngine` ("Download Engine"), S (6).
- `Row(fillMaxWidth, spacedBy 8)`, every chip `weight(1f)`:
    - `"⚡ " + Strings.engineDirect` ("⚡ Direct") -> `OnEngineSelected(HTTP_DIRECT)`; always.
    - `"🎬 " + Strings.engineMedia` ("🎬 Media") -> `OnEngineSelected(MEDIA_STREAM)`; only if
      `AppFeatures.mediaExtractor`.
    - `"🧲 " + Strings.engineTorrent` ("🧲 Torrent") -> `OnEngineSelected(BITTORRENT)`; only if
      `AppFeatures.torrent`.
      On the desktop both features are on (full edition): three equal chips.
- `EngineChoiceChip(label, isSelected, onClick, modifier)`: `Surface(onClick)`, radius 10,
  border 1.2 dp, content `Box(fillMaxWidth, padding vertical 10)` with a centred `Text` in
  bodySmall (line 16 dp, letter spacing 0.4) at 12 sp.

  | | Selected | Not selected |
    |---|---|---|
  | container | `primary` | `surfaceVariant 0.5` |
  | text | `onPrimary`, Bold | `onSurface`, Medium |
  | border | `primary` | `outlineVariant 0.6` |
  | shadow | 3 dp | none |

  Derived: visual height 36 dp inside a 48 dp layout row (6 dp transparent above and below).
  Hover / press state layer in the content colour. No animation between states.
- Selecting an engine clears qualities, playlist, torrent files and both inspect errors and
  recomputes the suggested name (`DashboardAddDialogEvents.kt:8-21`); auto-inspect (3.2) then
  fires for the new engine.

### 4.4
`FileNameSection(suggestedFileName, fileNameOverride, isInspectingMedia, isInspectingTorrent, onFileNameChanged)` (lines 241-272)

Drawn only when `suggestedFileName.isNotBlank() || fileNameOverride.isNotBlank()`.

- SectionLabel `Strings.addFileNameLabel` ("File name"), S (6).
- `inspecting = isInspectingMedia || isInspectingTorrent`.
- `FetchJetTextField` (String overload, single line, `fillMaxWidth`):
    - `value` = if `inspecting && fileNameOverride.isEmpty()` -> `Strings.findingName`
      ("Finding the name…"); else `fileNameOverride.ifEmpty { suggestedFileName }`.
    - `enabled = !inspecting` (disabled look: alpha 0.6).
    - leading `Text(if (inspecting) "⏳" else "📝", 16 sp)`.
    - trailing: `FieldIconButton("↺")` -> `onFileNameChanged("")`, only when
      `fileNameOverride.isNotBlank() && fileNameOverride != suggestedFileName`.
    - every edit -> `OnFileNameChanged(text)`. Because the shown value falls back to the suggested
      name, the first keystroke sends the suggested name with that edit applied; deleting
      everything sends "" and the suggested name comes back.
- S (14).

### 4.5 `ChecksumSection(value, onValueChange)` (lines 274-297)

- SectionLabel `Strings.checksumLabel` ("Checksum (optional)"), S (6).
- `FetchJetTextField` single line, `fillMaxWidth`, placeholder `Strings.checksumHint`
  ("sha256 or md5"), leading `Text("#️⃣", 16 sp)`, trailing `FieldIconButton("✕")` ->
  `onValueChange("")` only when `value.isNotBlank()`. Every edit -> `OnChecksumChanged(text)`.
- S (14).

### 4.6 `DestinationSection(...)` (lines 299-408) and `QuickFolderButton` (591-624)

```kotlin
internal fun DestinationSection(destinationPath: String, pathField: MutableState<TextFieldValue>,
    showDirectoryPickerState: MutableState<Boolean>, sortIntoFolders: Boolean,
    fileNameOverride: String, suggestedFileName: String, detectedEngine: EngineType,
    pathResolver: PathResolver, defaultDownloads: String, defaultDocs: String,
    defaultDesktop: String, onDestinationPathChanged: (String) -> Unit)
```

Logic that runs without any click (**type folder**, lines 317-337):

-
`typeFolder = if (sortIntoFolders) FileCategory.folderFor(fileNameOverride.ifEmpty { suggestedFileName }, isMedia = engine == MEDIA_STREAM, isTorrent = engine == BITTORRENT) else null`
(`sharedLogic/.../core/settings/BehaviorSettings.kt:159`).
- `LaunchedEffect(typeFolder)` (runs when the section first appears and whenever `typeFolder`
  changes, **not** when only the path changes):
  `base = FileCategory.stripTypeFolders(destinationPath, listOf(defaultDownloads, defaultDocs, defaultDesktop))`;
  `sep = if (base.contains('\\')) "\\" else "/"`;
  `full = if (typeFolder != null) base + sep + typeFolder else base`;
  if `full != destinationPath`: set the field (caret at end) and `OnDestinationPathChanged(full)`.
  So with "sort into folders" on, the field shows e.g. `...\Downloads\Video`, and a type folder
  already on the path is replaced, never stacked. `OnDestinationPathChanged` also saves the
  settings (`DashboardAddDialogEvents.kt:120-126`).

Layout:

- SectionLabel `Strings.saveDestination` ("Save Destination Directory"), S (6).
- `FetchJetTextField` (TextFieldValue overload), single line, `fillMaxWidth`, leading
  `Text("📁", 16 sp)`, trailing `FieldIconButton("📂")` -> `showDirectoryPicker = true`. Every
  edit: store locally; if the text differs from `destinationPath` ->
  `OnDestinationPathChanged(text)`.
  Always editable.
- S (8).
- `Row(fillMaxWidth, spacedBy 8)` of three `QuickFolderButton`s, each `weight(1f)`:
    1. "📥" + `Strings.quickFolderDownloads` ("Downloads") -> field = `defaultDownloads`,
       `OnDestinationPathChanged(defaultDownloads)`.
    2. "📄" + `Strings.quickFolderDocuments` ("Documents") -> same with `defaultDocs`.
    3.
    `isMobile = defaultDesktop.isBlank() || defaultDesktop.contains("storage", ignoreCase = true) || defaultDesktop.contains("Documents", ignoreCase = true)`.
        - not mobile (the desktop case): "💻" + `Strings.quickFolderDesktop` ("Desktop") ->
          `defaultDesktop`.
        - mobile: "🎬" + `Strings.quickFolderMedia` ("Media") ->
          `pathResolver.createSubdirectory(defaultDownloads, "Media") ?: "$defaultDownloads/Media"`.
          On Windows this heuristic turns "mobile" if the Desktop path itself contains "Documents"
          or
          "storage" (a redirected Desktop folder). The native port should pass `isMobile = false`.
- `QuickFolderButton(icon, label, onClick, modifier)`: `Surface(onClick)`, radius 8, container
  `surfaceVariant 0.5`, border 1 dp `outlineVariant 0.6`; `Row(fillMaxWidth, centred, padding
  6 dp horizontal / 8 dp vertical)`: `Text(icon, 12 sp)`, S (4), label 11 sp Medium onSurface,
  max 1 line, ellipsis. Derived: visual height 40 dp in a 48 dp layout row.

The section has no trailing spacer.

### 4.7 `AddDialogActions(...)` (lines 410-532)

```kotlin
internal fun AddDialogActions(errorMessage: String?, inspectError: String?, inspectMediaError: String?,
    hasActiveError: Boolean, isInspectionComplete: Boolean, isInspecting: Boolean, isUrlValid: Boolean,
    isClipRangeValid: Boolean, isLoading: Boolean, canStartDownload: Boolean, detectedEngine: EngineType,
    inspectedPlaylist: MediaPlaylist?, scheduleStartMs: Long, linkCount: Int = 0,
    onInspectTorrent: () -> Unit, onInspectMedia: () -> Unit, onAddDownload: () -> Unit, onDismiss: () -> Unit)
```

1. `activeError = errorMessage ?: inspectError ?: inspectMediaError`. If not null:
   `InlineErrorMessage(activeError)` full width with 12 dp below it (4.9).
2. `Row(fillMaxWidth, Arrangement.End, CenterVertically)`; derived: 48 dp layout height, the
   buttons are 40 dp tall:
    - **Cancel**: `OutlinedButton`, radius 10, border 1 dp `outlineVariant` (full alpha), text
      `Strings.cancel` ("Cancel") TB (13 sp, Medium, onSurface). Click -> `onDismiss`.
    - S (10) horizontal.
    - **Retry** (only if `hasActiveError && !isInspectionComplete`): `Button`, radius 10,
      container `error`, text `"🔄 " + Strings.addRetryInspect` ("🔄 Retry Inspect")
      TB (13 sp, Bold, onError). Click ->
      `if (detectedEngine == BITTORRENT) onInspectTorrent() else onInspectMedia()`.
      There is **no spacer** between Retry and the primary button: they touch.
    - **Primary**: `Button`, radius 10, `enabled = canStartDownload`, container `primary`,
      disabled container `surfaceVariant`. Click -> `onAddDownload` = `OnAddDownloadClicked`.
        - If `isLoading`: a 16 dp `CircularProgressIndicator`, stroke 2 dp, colour `onSurface`,
          then S (8) horizontal, before the text.
        - Text TB (13 sp, Bold), colour `onPrimary` when `canStartDownload`, else
          `onSurfaceVariant 0.6`. Label, first match wins:

          | # | Condition | Text |
                 |---|---|---|
          | 1 | `isLoading` | `Strings.addStarting` "Starting..." |
          | 2 | `isInspecting` | `Strings.addInspecting` "Inspecting..." |
          | 3 | `hasActiveError` | `Strings.addInspectionFailed` "Inspection Failed" |
          | 4 | `!isUrlValid` | `Strings.addEnterUrl` "Enter URL" |
          | 5 | `linkCount > 0` | `scheduleStartMs > 0` ? `Strings.scheduleLinks(linkCount)` "Schedule N links" : `Strings.downloadLinks(linkCount)` "Download N links" |
          | 6 | `!isInspectionComplete && engine == MEDIA_STREAM` | `Strings.inspectVideoFirst` "Inspect Video First" |
          | 7 | `!isInspectionComplete && engine == BITTORRENT` | `Strings.inspectTorrentFirst` "Inspect Torrent First" |
          | 8 | `!isClipRangeValid` | `Strings.invalidTimeRange` "Invalid Time Range" |
          | 9 | `inspectedPlaylist != null && engine == MEDIA_STREAM` | `n = entries.count { it.isSelected }`; `scheduleStartMs > 0` ? `Strings.scheduleVideos(n)` "Schedule N videos" : `Strings.downloadVideos(n)` "Download N videos" |
          | 10 | `scheduleStartMs > 0` | `Strings.addScheduleDownload` "Schedule Download" |
          | 11 | else | `Strings.addStartDownload` "Start Download" |

      Note for row 5 with nothing checked: `linkCount == 0` falls through to row 11 ("Start
      Download", disabled).

On success the view model closes the dialog itself (`isAddDialogVisible = false` in
`DashboardAddDownload.kt`); a failure sets `errorMessage`.

### 4.8 `InspectionLoadingBanner(title, subtitle)` (lines 534-568)

`Surface` `fillMaxWidth`, radius 10, colour `primaryContainer 0.3`, border 1 dp `primary 0.4`.
`Row(padding 14 dp horizontal / 12 dp vertical, CenterVertically)`: 20 dp
`CircularProgressIndicator` (stroke 2.5 dp, primary), S (12), `Column`: title 13 sp Bold primary,
S (2), subtitle 11 sp onSurfaceVariant. Derived height 74 dp. Used by the torrent block (5.1) and
by `MediaInspectionSection`.

### 4.9 `InlineErrorMessage(msg, modifier)` (lines 570-589)

`Row(fillMaxWidth)`, clipped to radius 8, background `errorContainer 0.35`, padding 10 dp
horizontal / 6 dp vertical, `CenterVertically`: `Text("⚠️", 14 sp)`, S (6), message 11 sp Medium
`error`, no line limit (wraps; the row grows). Derived height 36 dp for one line.

---

## 5. `AddDownloadTorrent.kt`

### 5.1 `TorrentInspectionSection(...)` (lines 50-145)

```kotlin
internal fun TorrentInspectionSection(detectedEngine: EngineType, inputUrl: String,
    isInspectingTorrent: Boolean, inspectError: String?, inspectedTorrentName: String?,
    inspectedTorrentFiles: List<TorrentFileEntry>, onInspectTorrent: () -> Unit,
    onFileSelectionToggled: (Int, Boolean) -> Unit, onFilePriorityChanged: (Int, Int) -> Unit,
    onSelectAllFilesToggled: (Boolean) -> Unit)
```

Draws nothing unless `detectedEngine == BITTORRENT`. Then S (12) and exactly one of:

1. `isInspectingTorrent` -> `InspectionLoadingBanner(Strings.inspectingTorrent`
   ("Inspecting Torrent Metadata...")`, Strings.inspectingTorrentHint` ("Fetching file manifest
   and peers")`)`.
2. `inspectError != null` -> error card: `Card` `fillMaxWidth`, radius 10, container
   `errorContainer 0.25`, border 1 dp `error 0.6`; `Column(padding 12)`:
    - `Row`: `Text("⚠️", 16 sp)`, S (6), `Strings.torrentInspectionFailed` ("Torrent Inspection
      Failed") 13 sp Bold `error`.
    - S (4); the `inspectError` text, 11 sp onSurfaceVariant, wraps.
    - S (10); `Button` aligned to the end, radius 8, container `error`, text
      `"🔄 " + Strings.addRetryInspection` ("🔄 Retry Inspection") TB (12 sp, SemiBold, onError).
      Click -> `OnInspectTorrentClicked`.
3. `inspectedTorrentFiles.isEmpty()` -> `OutlinedButton` `fillMaxWidth`, radius 10, border 1 dp
   literal `#0284C7`, content colour literal `#38BDF8`, text `"🧲 " + Strings.inspectTorrentFiles`
   ("🧲 Inspect Torrent Files") TB (13 sp, Bold). `enabled = inputUrl.isNotBlank()` (disabled:
   text `onSurfaceVariant 0.38`, the border stays `#0284C7`). Click -> `OnInspectTorrentClicked`.
   Seen mostly when Torrent is chosen with an empty link, since auto-inspect starts at once
   otherwise.
4. else ->
   `TorrentFilesSelectionCard(inspectedTorrentName, inspectedTorrentFiles, onFileSelectionToggled, onFilePriorityChanged, onSelectAllFilesToggled)`.

### 5.2
`TorrentLeechToggle(detectedEngine, disableTorrentUpload, onDisableTorrentUploadToggled)` (lines 147-176)

Only when `detectedEngine == BITTORRENT`: S (10), `Row(fillMaxWidth, CenterVertically)`:
`Checkbox(checked = disableTorrentUpload)` with checked colour `#0284C7`, unchecked border
`#64748B` (48 x 48 layout), S (6), `Strings.disableTorrentUpload` ("Disable Torrent Upload
(Leech-Only Mode)") 12 sp onSurface. Only the checkbox is clickable, not the text. Change ->
`OnDisableTorrentUploadToggled(checked)` (also saved to settings).

### 5.3
`TorrentFilesSelectionCard(torrentName, files, onFileSelectionToggled, onFilePriorityChanged, onSelectAll)` (lines 178-319)

Used by 5.1 and, per torrent row, by the link list (6.1).

`Card` `fillMaxWidth`, radius 12, container `surfaceVariant 0.35`, border 1 dp
`outlineVariant 0.6`; `Column(padding 12)`:

1. Header `Row(fillMaxWidth, SpaceBetween, CenterVertically)`; derived 48 dp:
    - `torrentName ?: Strings.torrentFiles` ("Torrent Files") 13 sp Bold onSurface, max 1 line,
      ellipsis. It has no weight: a name wider than the row takes the whole width and pushes the
      button out of view (existing behaviour; see Open questions).
    - `TextButton`, content padding 6 dp horizontal / 2 dp vertical, text 11 sp `primary`:
      `Strings.addDeselectAll` ("Deselect All") when `files.all { it.isSelected }`, else
      `Strings.addSelectAll` ("Select All"). Click -> `onSelectAll(!files.all { it.isSelected })`.
2. S (6)
3. List box: `fillMaxWidth`, `heightIn(max = 140)`, radius 8, background `surface`, padding 4,
   holding a `LazyColumn(fillMaxSize, spacedBy 2)` keyed by `file.index`. Because the list fills
   the available height, the box is **always 140 dp tall** (132 dp viewport) even with one file;
   more files scroll (mouse wheel; no scrollbar is drawn here).
4. File row `Row(fillMaxWidth, CenterVertically)`, radius 6, background `primaryContainer 0.4`
   when `file.isSelected` else transparent, whole row clickable ->
   `onFileSelectionToggled(file.index, !file.isSelected)`, padding 6 dp horizontal / 4 dp
   vertical. Derived row height 56 dp (48 + 8), so about 2.3 rows are visible.
    - `Checkbox(checked = file.isSelected)`, colours `#0284C7` / `#64748B`; change ->
      `onFileSelectionToggled(file.index, checked)`.
    - S (6)
    - `Column(weight 1)`: `file.path` 12 sp onSurface, max 1 line, ellipsis;
      `formatBytes(file.size)` 10 sp onSurfaceVariant.
    - S (6)
    - Priority chip: `Surface(onClick)`, radius 4, colour `c 0.15`, border 1 dp `c 0.4`, text
      10 sp SemiBold `c`, padding 6 dp horizontal / 2 dp vertical (visual about 28 dp tall inside
      a 48 x 48 layout box). Shown for every file, selected or not.

      | `file.priority` | Label | `c` | Click sets |
           |---|---|---|---|
      | 0 | `Strings.torrentPriorityNormal` "Normal" | onSurfaceVariant | 1 |
      | 1 | `Strings.torrentPriorityHigh` "High" | `#EF4444` | -1 |
      | -1 | `Strings.torrentPriorityLow` "Low" | `#3B82F6` | 0 |

      Click -> `onFilePriorityChanged(file.index, next)`.

Derived card height: 12 + 48 + 6 + 140 + 12 = 218 dp.

Events when used from the dialog: `OnTorrentFileSelectionToggled(fileIndex, isSelected)`,
`OnTorrentFilePriorityChanged(fileIndex, priority)`, `OnSelectAllTorrentFilesToggled(selectAll)`.
`fileIndex` is `TorrentFileEntry.index` (the torrent's own file index), not the list position.

---

## 6. `AddDownloadLinkList.kt`

Shown instead of the engine / inspection block when `linkList.isNotEmpty()` (two or more links
pasted, a `.txt` imported, or a batch from the browser extension).

`LinkListEntry` (`DashboardContract.kt:55-74`): `url`, `engine`, `isSelected = true`,
`isInspecting`, `inspectError`, `torrentName`, `torrentFiles`, `webSeeds`, `isInspectingMedia`,
`mediaInspectFailed`, `mediaTitle`, `mediaQualities`.

### 6.1 `LinkListSection(...)` (lines 45-161)

```kotlin
internal fun LinkListSection(links: List<LinkListEntry>, onToggled: (Int, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit, folder: String = "", onFolderChanged: (String) -> Unit = {},
    onTorrentFileToggled: (Int, Int, Boolean) -> Unit, onTorrentFilePriority: (Int, Int, Int) -> Unit,
    onTorrentSelectAll: (Int, Boolean) -> Unit, onQualitySelected: (Int, Int) -> Unit,
    onRetryInspect: (Int) -> Unit)
```

`selected = links.count { it.isSelected }`, `allSelected = selected == links.size`.

1. `Row(fillMaxWidth, CenterVertically)`; derived 48 dp:
    - `Strings.selectedOfLinks(selected, links.size)` ("3 of 5 links") 13 sp SemiBold onSurface,
      `weight(1f)`.
    - `TextButton` (default padding 12 / 8): `Strings.selectNone` ("Select none") if `allSelected`
      else `Strings.selectAll` ("Select all"), TB (12 sp, SemiBold, primary). Click ->
      `OnLinkListSelectAll(!allSelected)`.
2. `FetchJetTextField(value = folder, label = Strings.playlistFolder` ("Save in folder")`,
   leading = Text("📁", 14 sp))`, single line, full width. Edit -> `OnLinkListFolderChanged(text)`.
3. `Strings.playlistFolderHint` ("Every file goes into this folder. Clear it to save them
   directly.") 10 sp onSurfaceVariant, padding top 4, bottom 8.
4. Panel `Surface` `fillMaxWidth`, radius 12, colour `surfaceVariant 0.25`, border 1 dp
   `outlineVariant 0.6`; a plain `Column` (not lazy: with many links the whole dialog scrolls;
   the list itself has no inner scroll; up to 500 links, `extractLinks().take(500)`). For each
   link at `index`:
    - if `index > 0`: `HorizontalDivider` 1 dp `outlineVariant 0.35`, full width.
    - `LinkRow(link) { onToggled(index, it) }` (6.2).
    - if `link.engine == MEDIA_STREAM && link.isSelected`: `VideoQualityLine` (6.3).
    - if `link.engine == BITTORRENT && link.isSelected`:
      `Column(padding start 12, end 12, bottom 10)` with one of:
        - `link.isInspecting` -> `Row`: 14 dp `CircularProgressIndicator` (stroke 2 dp, primary),
          S (8), `Strings.readingTorrentFiles` ("Reading the torrent's files…") 12 sp
          onSurfaceVariant.
        - `link.torrentFiles.isNotEmpty()` ->
          `TorrentFilesSelectionCard(link.torrentName, link.torrentFiles, ...)`
          with events `OnLinkListTorrentFileToggled(index, fileIndex, on)`,
          `OnLinkListTorrentFilePriority(index, fileIndex, priority)`,
          `OnLinkListTorrentSelectAll(index, on)`.
        - `link.inspectError != null` -> `Strings.couldNotReadTorrentFiles(link.inspectError)`
          ("Could not read its files (…); all of them will download.") 12 sp `error`.
        - otherwise nothing (the 10 dp bottom padding remains).

   Unchecking a row hides its quality line / torrent block.

### 6.2 `LinkRow(link, onToggle)` (private, lines 163-211)

`Row(fillMaxWidth, CenterVertically)`, whole row clickable -> `onToggle(!link.isSelected)`,
padding start 4, end 12, top 6, bottom 6. Derived height 60 dp.

- `Checkbox(checked = link.isSelected)` forced to 40 x 40 dp, default colours (primary /
  onSurfaceVariant). Change -> `onToggle(checked)`.
- S (4)
- `Column(weight 1)`:
    - title: `link.mediaTitle?.let { decodeUnicodeText(it) } ?: linkTitle(link.url)`, 13 sp
      SemiBold onSurface, max 1 line, ellipsis.
    - host: `linkHost(link.url)`, 11 sp onSurfaceVariant, max 1 line, ellipsis.
- S (8)
- Tag: `Box` radius 6, background `tagColor 0.15`, padding 7 dp horizontal / 3 dp vertical,
  text 10 sp Bold `tagColor`:

  | Engine | Tag | Colour |
    |---|---|---|
  | `MEDIA_STREAM`, `link.mediaQualities.isAllAudio()` | `Strings.linkTagAudio` "AUDIO" | `#FBBF24` |
  | `MEDIA_STREAM`, otherwise (also before the qualities are known) | `Strings.linkTagVideo` "VIDEO" | `#E879F9` |
  | `BITTORRENT` | `Strings.linkTagTorrent` "TORRENT" | `#34D399` |
  | `HTTP_DIRECT` | `Strings.linkTagFile` "FILE" | `#38BDF8` |

Both the row and the checkbox send `OnLinkListEntryToggled(index, selected)`.

### 6.3 `VideoQualityLine(link, onRetry, onSelected)` (private, lines 213-285)

`Box(padding start 48, end 12, bottom 8)` with one of:

1. `link.isInspectingMedia` -> `Row`: 14 dp spinner (stroke 2 dp, primary), S (8),
   `Strings.inspectingVideoQualities` ("Inspecting Video Qualities...") 12 sp onSurfaceVariant.
2. `link.mediaQualities.isNotEmpty()`:
    - UI-local `open: Boolean = false` (per row).
    - `current = link.mediaQualities.firstOrNull { it.isSelected } ?: link.mediaQualities.first()`.
    - Chip: `Surface` radius 8, colour `primary 0.08`, border 1 dp `primary 0.5`, clickable ->
      `open = true`; text `qualityText(current) + "  ▾"` 12 sp SemiBold `primary`, padding 10 dp
      horizontal / 6 dp vertical. Derived height 36 dp. Width wraps the text.
    - `DropdownMenu(expanded = open, onDismissRequest = { open = false })`, anchored below the
      chip, start-aligned; one `DropdownMenuItem` per quality in list order, text
      `qualityText(q)` 13 sp, **Bold** when `q.index == current.index` else Normal. Click ->
      `open = false`, `OnLinkListQualitySelected(linkIndex, q.index)` (the view model marks only
      that quality selected). Click outside or Esc closes the menu. The menu scrolls when it does
      not fit the window (48 dp margin to the window edges).
3. `link.mediaInspectFailed` -> `Row`: `Strings.sysFailedToInspectMediaQualities` ("Failed to
   inspect media qualities") 12 sp `error` (`weight(1f, fill = false)`), S (8), `TextButton`
   `Strings.retry` ("Retry") TB (12 sp, SemiBold, primary) -> `OnLinkListRetryInspect(linkIndex)`
   (the view model re-reads every listed video that still needs it).
4. otherwise nothing (8 dp bottom padding remains).

### 6.4 Pure helpers in this file (lines 287-339)

Listed with exact rules in 11.2: `qualityText`, `linkTitle`, `linkHost`, `percentDecode`.

---

## 7. Strings

All through `LocalizationManager.Strings` (`AppLocalization`, English in
`sharedLogic/.../core/i18n/data_provider/EnglishProvider.kt`).

| Member                                                                                      | English                                                                            | Where                      |
|---------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|----------------------------|
| `addDialogTitle`                                                                            | Add New Download                                                                   | header                     |
| `addDialogSubtitle`                                                                         | Enter link to start downloading                                                    | header                     |
| `addLinkLabel`                                                                              | Link                                                                               | URL label                  |
| `addImportTxt`                                                                              | Import .txt                                                                        | URL label row              |
| `openTorrentFile`                                                                           | Open .torrent file                                                                 | URL label row              |
| `urlPlaceholder`                                                                            | Paste links or magnets                                                             | URL field                  |
| `addPaste`                                                                                  | Paste                                                                              | URL field chip             |
| `addDownloadEngine`                                                                         | Download Engine                                                                    | engine label               |
| `engineDirect` / `engineMedia` / `engineTorrent`                                            | Direct / Media / Torrent                                                           | engine chips               |
| `addFileNameLabel`                                                                          | File name                                                                          | file name                  |
| `findingName`                                                                               | Finding the name…                                                                  | file name while inspecting |
| `checksumLabel` / `checksumHint`                                                            | Checksum (optional) / sha256 or md5                                                | checksum                   |
| `saveDestination`                                                                           | Save Destination Directory                                                         | destination                |
| `quickFolderDownloads` / `quickFolderDocuments` / `quickFolderDesktop` / `quickFolderMedia` | Downloads / Documents / Desktop / Media                                            | quick folders              |
| `cancel`                                                                                    | Cancel                                                                             | actions                    |
| `addRetryInspect`                                                                           | Retry Inspect                                                                      | actions                    |
| `addStarting` / `addInspecting` / `addInspectionFailed` / `addEnterUrl`                     | Starting... / Inspecting... / Inspection Failed / Enter URL                        | primary button             |
| `scheduleLinks(n)` / `downloadLinks(n)`                                                     | Schedule $n links / Download $n links                                              | primary button             |
| `inspectVideoFirst` / `inspectTorrentFirst` / `invalidTimeRange`                            | Inspect Video First / Inspect Torrent First / Invalid Time Range                   | primary button             |
| `scheduleVideos(n)` / `downloadVideos(n)`                                                   | Schedule $n videos / Download $n videos                                            | primary button             |
| `addScheduleDownload` / `addStartDownload`                                                  | Schedule Download / Start Download                                                 | primary button             |
| `clipStartAfterEnd(start, end)`                                                             | Start time ($start) must be before end time ($end)                                 | clip validation            |
| `clipEndExceedsVideo(end, duration)`                                                        | End time ($end) exceeds video duration ($duration)                                 | clip validation            |
| `clipStartExceedsVideo(duration)`                                                           | Start time exceeds video duration ($duration)                                      | clip validation            |
| `setStartTimeTitle` / `setEndTimeTitle`                                                     | Set Start Time (From) / Set End Time (To)                                          | time picker titles         |
| `inspectingTorrent` / `inspectingTorrentHint`                                               | Inspecting Torrent Metadata... / Fetching file manifest and peers                  | torrent banner             |
| `torrentInspectionFailed`                                                                   | Torrent Inspection Failed                                                          | torrent error card         |
| `addRetryInspection`                                                                        | Retry Inspection                                                                   | torrent error card         |
| `inspectTorrentFiles`                                                                       | Inspect Torrent Files                                                              | torrent inspect button     |
| `disableTorrentUpload`                                                                      | Disable Torrent Upload (Leech-Only Mode)                                           | leech toggle               |
| `torrentFiles`                                                                              | Torrent Files                                                                      | files card title fallback  |
| `addSelectAll` / `addDeselectAll`                                                           | Select All / Deselect All                                                          | files card                 |
| `torrentPriorityNormal` / `torrentPriorityHigh` / `torrentPriorityLow`                      | Normal / High / Low                                                                | priority chip              |
| `selectedOfLinks(selected, total)`                                                          | $selected of $total links                                                          | link list header           |
| `selectAll` / `selectNone`                                                                  | Select all / Select none                                                           | link list header           |
| `playlistFolder` / `playlistFolderHint`                                                     | Save in folder / Every file goes into this folder. Clear it to save them directly. | link list folder           |
| `readingTorrentFiles`                                                                       | Reading the torrent's files…                                                       | link list torrent          |
| `couldNotReadTorrentFiles(error)`                                                           | Could not read its files ($error); all of them will download.                      | link list torrent          |
| `linkTagVideo` / `linkTagAudio` / `linkTagTorrent` / `linkTagFile`                          | VIDEO / AUDIO / TORRENT / FILE                                                     | link row tag               |
| `inspectingVideoQualities`                                                                  | Inspecting Video Qualities...                                                      | quality line               |
| `sysFailedToInspectMediaQualities`                                                          | Failed to inspect media qualities                                                  | quality line               |
| `retry`                                                                                     | Retry                                                                              | quality line               |
| `linkListMagnet`                                                                            | Magnet link                                                                        | `linkTitle`                |
| `linkListYouTubeVideo(id)`                                                                  | YouTube video $id                                                                  | `linkTitle`                |

Hard-coded texts: the emoji / glyph prefixes `⚡ 🔗 📋 📄 📂 📁 📥 💻 🎬 🧲 📝 ⏳ #️⃣ 🔄 ⚠️ ⏱️`,
the glyphs `✕`, `↺`, `▾` (preceded by two spaces), the separator `" · "` in `qualityText`, the
host text `"BitTorrent"` for magnets, the native file-dialog titles "Import links" and
"Open .torrent file", and the fallback times `"00:00"` / `"00:10:00"`. Error messages
(`errorMessage`, `inspectError`, `inspectMediaError`) arrive as ready text from the view model.

---

## 8. Events and direct `sharedLogic` reads

| UI action                                                     | Event                                                                                                                                                                                |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| URL edited / cleared / pasted / file imported                 | `OnUrlChanged(url)`                                                                                                                                                                  |
| engine chip                                                   | `OnEngineSelected(engine)`                                                                                                                                                           |
| file name edited / reset                                      | `OnFileNameChanged(name)` ("" = reset)                                                                                                                                               |
| checksum edited / cleared                                     | `OnChecksumChanged(value)`                                                                                                                                                           |
| destination edited / picked / quick folder / type folder rule | `OnDestinationPathChanged(path)`                                                                                                                                                     |
| leech checkbox                                                | `OnDisableTorrentUploadToggled(disable)`                                                                                                                                             |
| inspect / retry (torrent) and auto-inspect                    | `OnInspectTorrentClicked`                                                                                                                                                            |
| retry (media) and auto-inspect                                | `OnInspectMediaClicked`                                                                                                                                                              |
| torrent file row / checkbox                                   | `OnTorrentFileSelectionToggled(fileIndex, isSelected)`                                                                                                                               |
| torrent priority chip                                         | `OnTorrentFilePriorityChanged(fileIndex, priority)`                                                                                                                                  |
| torrent Select All / Deselect All                             | `OnSelectAllTorrentFilesToggled(selectAll)`                                                                                                                                          |
| link row / checkbox                                           | `OnLinkListEntryToggled(index, selected)`                                                                                                                                            |
| link list Select all / none                                   | `OnLinkListSelectAll(selected)`                                                                                                                                                      |
| link list folder                                              | `OnLinkListFolderChanged(name)`                                                                                                                                                      |
| link list quality menu                                        | `OnLinkListQualitySelected(linkIndex, qualityIndex)`                                                                                                                                 |
| link list Retry                                               | `OnLinkListRetryInspect(linkIndex)`                                                                                                                                                  |
| link list torrent file / priority / select all                | `OnLinkListTorrentFileToggled(linkIndex, fileIndex, selected)` / `OnLinkListTorrentFilePriority(linkIndex, fileIndex, priority)` / `OnLinkListTorrentSelectAll(linkIndex, selected)` |
| time pickers                                                  | `OnClipStartTimeChanged(time)` / `OnClipEndTimeChanged(time)`                                                                                                                        |
| primary button                                                | `OnAddDownloadClicked`                                                                                                                                                               |
| X, Cancel, Esc, scrim click                                   | `OnToggleAddDialog(false)`                                                                                                                                                           |

Passed through to the out-of-scope sections: `OnSubtitleToggled`, `OnToggleEmbedSubtitles`,
`OnDownloadYtDlpClicked`, `OnMediaQualitySelectionToggled`, `OnSelectAllMediaQualitiesToggled`,
`OnToggleClipRange`, `OnToggleClipFastMode`, `OnAudioConvertFormatSelected`,
`OnPlaylistEntryToggled`, `OnPlaylistSelectAll`, `OnPlaylistQualityChanged`,
`OnPlaylistUseSingleVideo`, `OnPlaylistFolderChanged`, `OnScheduleChanged`, `OnConnectionsChanged`.

`sharedLogic` objects the UI calls directly (the presenter must call them instead):

- `PathResolver.getDownloadsDirectory()`, `getDocumentsDirectory()`, `getDesktopDirectory()`,
  `createSubdirectory(parent, name)`.
- `TextFilePicker.isSupported` / `pick { text }`, `TorrentFilePicker.isSupported` / `pick { path }`
  (expect objects in `core/filesystem`; they need mingwX64 actuals, e.g. `GetOpenFileNameW`).
- `AppFeatures.mediaExtractor`, `AppFeatures.torrent` (`core/edition/AppEdition.kt:26`).
- `getClipboardText()` (`Platform.kt:10`, suspend, needs a mingwX64 actual).
- `FileCategory.folderFor(...)`, `FileCategory.stripTypeFolders(...)`
  (`core/settings/BehaviorSettings.kt`).
- `MediaQualityEntry.isAudioOnly`, `List<MediaQualityEntry>.isAllAudio()`
  (`domain/model/MediaQualityEntry.kt:16-20`).
- `PlaylistQuality.AUDIO`, `PlaylistQuality.extension` (`feature/media_extractor/Playlist.kt:143`).

---

## 9. (a) Reusable widgets the Slint port needs for this area

| Widget                       | Behaviour                                                                                                                                                                                                                                                                                                                                                         | Visual spec                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **FjTextField**              | Single-line or multi-line (max N lines, then inner scroll) editable text on Slint `TextInput` (not `LineEdit`). Properties: `text` (two-way), `label`, `placeholder`, `enabled`, `error`, `centered`, `height`, slots or flags for a leading glyph and a trailing element. Callback `edited(text)`. Function `set-text-caret-end(text)` for programmatic changes. | Section 2.2 and 2.3: radius 12; idle fill surfaceVariant 0.35 over surface, focused fill primary 0.06 over surface; border 1 px outlineVariant 0.7, hover outline, focus 1.5 px primary, error 1.5 px error; focus / error glow blur 10 px, offset-y 2 px, accent at 0.28; animations 180 ms (glow 220 ms); text 14 px, letter spacing 0.2, onSurface; placeholder onSurfaceVariant 0.65; label 11 px SemiBold above (5 px gap, 4 px inset); error text 11 px below; disabled = opacity 0.6; selection primary 0.4; caret primary. Paddings: start 10 / 14, end 6 / 14, multi-line 10 top and bottom. |
| **FieldIconButton**          | Click; hover highlight (150 ms).                                                                                                                                                                                                                                                                                                                                  | 30 px circle, glyph 14 px onSurfaceVariant, hover fill onSurface 0.08.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **FieldChipButton**          | Click.                                                                                                                                                                                                                                                                                                                                                            | Radius 8, fill primary 0.12, text 12 px SemiBold primary, padding 10 / 5.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **SectionLabel**             | Static.                                                                                                                                                                                                                                                                                                                                                           | 12 px SemiBold onSurface, letter spacing 0.5, 24 px line box, 6 px gap below.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **FjButton** (filled)        | Click, `enabled`, optional leading spinner.                                                                                                                                                                                                                                                                                                                       | Height 40 px in a 48 px row, min width 58, padding 24 horizontal, radius parameter (10 or 8), fill parameter (primary / error), disabled fill surfaceVariant, text 13 or 12 px with letter spacing 0.1, hover layer content colour 0.08, pressed 0.10.                                                                                                                                                                                                                                                                                                                                                |
| **FjOutlinedButton**         | Click, `enabled`.                                                                                                                                                                                                                                                                                                                                                 | Same box, transparent fill, 1 px border (outlineVariant, or `#0284C7`), text colour parameter.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **FjTextButton**             | Click.                                                                                                                                                                                                                                                                                                                                                            | Transparent, text primary, pill hover layer primary 0.08, min 58 x 40 in a 48 px row, padding parameter (12 / 8 default, 8 / 2 and 6 / 2 variants).                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **FjCheckbox**               | Toggle, `checked`, colour parameters.                                                                                                                                                                                                                                                                                                                             | 20 px box, 2 px stroke, radius 2, check mark onPrimary, 40 px hover circle, layout 48 px (40 px variant for link rows). Checked / unchecked colours: default primary / onSurfaceVariant; torrent variant `#0284C7` / `#64748B`.                                                                                                                                                                                                                                                                                                                                                                       |
| **ChoiceChip** (engine chip) | Single-choice, equal widths.                                                                                                                                                                                                                                                                                                                                      | 4.3: radius 10, border 1.2 px, selected fill primary + shadow, idle fill surfaceVariant 0.5, text 12 px letter spacing 0.4, visual height 36 in a 48 px row.                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **QuickFolderButton**        | Click.                                                                                                                                                                                                                                                                                                                                                            | 4.6: radius 8, fill surfaceVariant 0.5, border outlineVariant 0.6, glyph 12 px + label 11 px Medium, visual height 40 in a 48 px row.                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **Spinner**                  | Indeterminate rotation.                                                                                                                                                                                                                                                                                                                                           | Arc in a colour parameter (primary / onSurface), round caps, sizes 14 / 16 / 20 px, strokes 2 / 2 / 2.5 px.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **LoadingBanner**            | Static + spinner.                                                                                                                                                                                                                                                                                                                                                 | 4.8.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **InlineError**              | Static, wraps.                                                                                                                                                                                                                                                                                                                                                    | 4.9.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **TagBadge**                 | Static.                                                                                                                                                                                                                                                                                                                                                           | 6.2: radius 6, fill colour 0.15, text 10 px Bold in the colour, padding 7 / 3.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **PriorityChip**             | Click cycles 0 -> 1 -> -1 -> 0.                                                                                                                                                                                                                                                                                                                                   | 5.3: radius 4, fill colour 0.15, border colour 0.4, text 10 px SemiBold, padding 6 / 2. (`desktopNative/ui/app.slint:182` already has a `PriorityChip` to align.)                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **DropdownMenu** (popup)     | Opens under an anchor, closes on outside click / Esc / item click, scrolls when long.                                                                                                                                                                                                                                                                             | 1.4: fill surfaceContainer, radius 4, shadow, 8 px vertical padding, items 48 px high, 12 px horizontal padding, width 112..280 px, text 13 px onSurface, hover layer.                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **ModalDialogFrame**         | Scrim, centred card, Esc and scrim click close, open / close animation, focus stays inside, vertical scroll with overlay scrollbar.                                                                                                                                                                                                                               | 1.1 and 3.3.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **OverlayScrollbar**         | Drag thumb, click track, fades.                                                                                                                                                                                                                                                                                                                                   | 3.3.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **TorrentFilesCard**         | 5.3; reused by the dialog and by each torrent row of the link list (and its look is shared with the task details).                                                                                                                                                                                                                                                | 5.3.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

---

## 10. (b) Port units

| Unit                                            | Source                                                                                                                                                                                            | Depends on                                                                                                                                          | State from the presenter                                                                                                                                                                                                                                                                                                    | Events sent                                                                                                                                                                                                                      |
|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **U1 Text field kit**                           | `FetchJetTextField.kt:71-402` (402 lines)                                                                                                                                                         | theme tokens only                                                                                                                                   | none (pure widget)                                                                                                                                                                                                                                                                                                          | `edited(text)`, button `clicked`                                                                                                                                                                                                 |
| **U2 Dialog frame, header, actions**            | `AddDownloadModalDialog.kt:296-521`; `AddDownloadSections.kt:49-98, 410-532, 534-589` (about 520 lines)                                                                                           | ModalDialogFrame, OverlayScrollbar, FjButton, FjOutlinedButton, Spinner, InlineError; hosts U3-U5 and the out-of-scope sections in the order of 3.4 | `visible`, the section visibility flags of 3.4 (`showsLinkList`, `showEngineBlock`, `showConnections`, `showAudioConvert`, `showFileName`, `showChecksum`, `showLeechToggle`), `activeError`, `showRetry`, `primaryLabel`, `primaryEnabled`, `isLoading`                                                                    | `OnToggleAddDialog(false)`, `OnAddDownloadClicked`, retry (`OnInspectTorrentClicked` / `OnInspectMediaClicked`, chosen by the presenter)                                                                                         |
| **U3 Basic sections**                           | `AddDownloadSections.kt:100-408, 591-665` (about 390 lines)                                                                                                                                       | U1, SectionLabel, FjTextButton, ChoiceChip, QuickFolderButton                                                                                       | `url` (+ a "replace text" signal), `canImportTxt`, `canOpenTorrent`, `engine`, `showMediaChip`, `showTorrentChip`, `fileNameText`, `fileNameEnabled`, `fileNameIcon`, `fileNameCanReset`, `checksum`, `destination` (+ replace signal), `thirdQuickFolderIcon`, `thirdQuickFolderLabel`                                     | `OnUrlChanged`, import / open-torrent / paste requests, `OnEngineSelected`, `OnFileNameChanged`, `OnChecksumChanged`, `OnDestinationPathChanged`, quick folder (1..3), browse (opens the directory picker)                       |
| **U4 Torrent block**                            | `AddDownloadTorrent.kt:50-319` (270 lines)                                                                                                                                                        | LoadingBanner, FjButton, FjOutlinedButton, FjCheckbox, FjTextButton, PriorityChip                                                                   | `torrentMode` (hidden / inspecting / error / needsInspect / files), `inspectError`, `inspectEnabled`, `torrentTitle`, `allFilesSelected`, `files: [{index, path, sizeText, selected, priority}]`, `disableUpload`                                                                                                           | `OnInspectTorrentClicked`, `OnTorrentFileSelectionToggled`, `OnTorrentFilePriorityChanged`, `OnSelectAllTorrentFilesToggled`, `OnDisableTorrentUploadToggled`                                                                    |
| **U5 Link list**                                | `AddDownloadLinkList.kt:45-285` (240 lines of UI)                                                                                                                                                 | U1, FjCheckbox, FjTextButton, TagBadge, Spinner, DropdownMenu, TorrentFilesCard from U4                                                             | `headerText`, `allSelected`, `folder`, `rows: [{title, host, tagText, tagColor, selected, kind, mediaMode (none / inspecting / qualities / failed), currentQualityText, qualities: [{index, text, current}], torrentMode (none / inspecting / files / error), torrentError, torrentTitle, allFilesSelected, files: [...]}]` | `OnLinkListEntryToggled`, `OnLinkListSelectAll`, `OnLinkListFolderChanged`, `OnLinkListQualitySelected`, `OnLinkListRetryInspect`, `OnLinkListTorrentFileToggled`, `OnLinkListTorrentFilePriority`, `OnLinkListTorrentSelectAll` |
| **U6 Add-dialog presenter (Kotlin, no markup)** | `AddDownloadModalDialog.kt:122-294, 430-446`; `AddDownloadSections.kt:317-337, 385-388, 432, 500-521`; `AddDownloadLinkList.kt:58-59, 165-171, 287-339`; `AddDownloadTorrent.kt:212-218, 282-298` | `DashboardViewModel` + `DashboardUiState` (both already in `sharedLogic` commonMain), the helpers of 11.2                                           | whole `DashboardUiState`                                                                                                                                                                                                                                                                                                    | produces the view models above; runs auto-inspect and the type-folder rule                                                                                                                                                       |

Suggested order: U1, U6, U2 + U3 (a working direct download), U4, U5.

---

## 11. (c) What moves into the Kotlin presenter

### 11.1 UI-local state and effects

| Compose state / effect                                                                      | Where                                             | In the port                                                                                                                                                                                                                                                     |
|---------------------------------------------------------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `urlFieldState`, `pathFieldState` (text + caret) and their `LaunchedEffect` sync            | `AddDownloadModalDialog.kt:127-161`               | The Slint field keeps its own text. The presenter pushes a new text only when the state value differs from what the field last reported, and the field then puts the caret at the end. Never push back the text the user just typed (it would reset the caret). |
| `showDirectoryPicker`                                                                       | `:163`                                            | Presenter flag `directoryPickerOpen`, or a direct call to the picker document's entry point. Result -> `OnDestinationPathChanged`.                                                                                                                              |
| `showStartTimePicker`, `showEndTimePicker`                                                  | `:165-168`                                        | Presenter flags; picker parameters computed as in 3.2.                                                                                                                                                                                                          |
| `defaultDownloads`, `defaultDocs`, `defaultDesktop`                                         | `:122-124`                                        | Read once from `PathResolver` in the presenter.                                                                                                                                                                                                                 |
| clip validation values and message                                                          | `:170-204`                                        | Presenter (pure, 11.2).                                                                                                                                                                                                                                         |
| **Auto-inspect** `LaunchedEffect(inputUrl, detectedEngine)`                                 | `:261-278`                                        | Presenter: while `isAddDialogVisible`, on every change of `(inputUrl, detectedEngine)` and when the dialog becomes visible, apply the rule of 3.2 against the current state.                                                                                    |
| gating flags, `canStartDownload`, primary label, `activeError`, retry visibility and target | `:280-294`, `AddDownloadSections.kt:432, 462-521` | Presenter.                                                                                                                                                                                                                                                      |
| section visibility, `showsAudioConvert`, `nativeAudioFormat`, `audioConvertForAll`          | `:338-469`                                        | Presenter.                                                                                                                                                                                                                                                      |
| **Type-folder rule** `LaunchedEffect(typeFolder)`                                           | `AddDownloadSections.kt:317-337`                  | Presenter: when the dialog opens and when `typeFolder` changes (not on other path changes), compute `full` and send `OnDestinationPathChanged(full)` if different.                                                                                              |
| `isMobile` third quick folder                                                               | `AddDownloadSections.kt:385-388`                  | Presenter constant for the desktop: Desktop folder.                                                                                                                                                                                                             |
| file name field value / enabled / icon / reset visibility                                   | `AddDownloadSections.kt:259-268`                  | Presenter.                                                                                                                                                                                                                                                      |
| link list `selected`, `allSelected`, tag, title, host                                       | `AddDownloadLinkList.kt:58-59, 165-171, 187-195`  | Presenter (row view models).                                                                                                                                                                                                                                    |
| `open` of each quality menu                                                                 | `AddDownloadLinkList.kt:228`                      | May stay in Slint (purely visual, per row).                                                                                                                                                                                                                     |
| torrent "all selected", priority label / colour / next value                                | `AddDownloadTorrent.kt:212-218, 282-298`          | "All selected" and `sizeText` in the presenter; the label / colour / next-priority mapping may live in the Slint `PriorityChip` (three fixed cases), with the three labels passed as strings.                                                                   |
| field hover / focus / glow animations, `FocusedField.bounds`                                | `FetchJetTextField.kt`                            | Stay in Slint.                                                                                                                                                                                                                                                  |
| `dialogScroll`                                                                              | `:318`                                            | Stays in Slint (reset to top each time the dialog opens, as Compose does by recreating it).                                                                                                                                                                     |
| import `.txt`, open `.torrent`, paste                                                       | `AddDownloadSections.kt:121-193`                  | Presenter callbacks: run the picker / read the clipboard (`trim()`, ignore null or blank), then set the field text (caret at end) and send `OnUrlChanged`.                                                                                                      |

### 11.2 Pure helpers to reproduce exactly

- `formatBytes(bytes)` (`utils/Formatters.kt:10-23`): `bytes <= 0` -> `"0 B"`; divide by 1024
  while `>= 1024` up to TB; truncate (not round) to one decimal with
  `(value * 10).toLong() / 10.0`; print the Kotlin `Double` (`"512.0 B"`, `"1.5 GB"`, always one
  decimal) + space + unit; wrap the result in U+2066 ... U+2069.
- `formatSecondsToHhMm(totalSeconds)` (`:132-142`): negative -> 0; always `HH:MM:SS`, each part
  zero-padded to 2.
- `parseHhMmToSeconds(text)` (`:147-153`): split on `:`; parts 0, 1, 2 are hours, minutes,
  seconds (missing or non-numeric = 0), so `"00:10"` is 10 **minutes**; result
  `(h * 3600 + m * 60 + s).coerceAtLeast(0)`.
- `decodeUnicodeText(input)` (`:117-127`): replace every `\uXXXX` or `_uXXXX` (4 hex digits) by
  that UTF-16 code unit.
- `qualityText(q)` = `q.qualityLabel + (if (q.size > 0) " · " + formatBytes(q.size) else "")`.
- `linkHost(url)`: starts with `magnet:` (ignore case) -> `"BitTorrent"`; else
  `url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")`.
- `linkTitle(url)`:
    - magnet: `name = url.substringAfter("dn=", "").substringBefore('&')`; result
      `decodeUnicodeText(percentDecode(name.replace('+', ' '))).ifBlank { Strings.linkListMagnet }`.
    - else
      `path = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")`;
      `last = percentDecode(path.trimEnd('/').substringAfterLast('/'))`; `host = linkHost(url)`;
        - host contains `youtube.com` or `youtu.be`:
          `id = url.substringAfter("v=", "").substringBefore('&').ifBlank { last }` ->
          `Strings.linkListYouTubeVideo(id)`;
        - `last` not blank -> `decodeUnicodeText(last)`;
        - else `host`.
- `percentDecode(text)`: no `%` -> unchanged; otherwise walk the string, a `%` followed by two
  hex digits (`i + 2 <= lastIndex`) becomes that byte, every other character is appended as its
  UTF-8 bytes, and the byte array is decoded as UTF-8 (invalid sequences become U+FFFD).
- `MediaQualityEntry.isAudioOnly`: `qualityLabel` contains "Audio" (ignore case) or
  `format.lowercase()` is one of `mp3, m4a, flac, wav, aac, ogg, opus`.
  `isAllAudio()` = not empty and all audio-only.
- `FileCategory.folderFor` / `stripTypeFolders`: already in `sharedLogic`; call them, do not copy.
- Priority cycle: `0 -> 1 -> -1 -> 0`.
- Card width (3.3) if the presenter ever needs it: `clamp(0.94 * min(660, W), min(340, W), W)`.

All of U6 can be a thin class over `DashboardViewModel.uiState` (a `StateFlow`), because the view
model, its state and its events live in `sharedLogic/src/commonMain` already.

---

## 12. Risks and open questions

Risks:

1. **Emoji**. Every icon in this area is an emoji drawn by Skia with the Windows colour emoji
   font. The prototype runs Slint with `SLINT_BACKEND=winit-software` (`desktopNative/main.cpp:153`)
   and draws icons as `Path` glyphs. Whether that renderer draws colour emoji was not tested
   here; if it does not, about 17 glyphs need replacement icons (list in section 7).
2. **Field glow and card shadow** need `drop-shadow-*`; its support and cost in the software
   renderer were not tested.
3. **Multi-line URL field**: Slint `TextInput` does not limit itself to 3 lines or scroll; the
   widget must clip, track the caret and scroll. Caret-at-end after a programmatic text change
   also needs explicit code.
4. **Vertical metrics are derived, not measured**: the 24 dp line boxes and 48 dp interactive
   rows come from the Material sources. A side-by-side screenshot of the JVM dialog is the only
   real check; rule 3 / 4 of this task forbade starting the app.
5. **Weight 500** (`FontWeight.Medium`) on Segoe UI: the face Skia picks was not checked.
6. **RTL**: Compose mirrors every Row and start / end padding automatically; Slint layouts do
   not. Arabic / Persian need explicit mirroring in each widget of this area.
7. **Min interactive size** makes several Compose rows taller than they look (engine chips,
   quick folders, torrent file rows, buttons). Porting only the visual sizes would make the
   dialog noticeably shorter than the original.
8. **Auto-inspect and the type-folder rule live in the UI today.** If the presenter does not
   reproduce them, pasted magnets / video links are never inspected and "sort into folders"
   stops updating the destination.
9. `TextFilePicker`, `TorrentFilePicker`, `getClipboardText` are expect declarations without a
   mingwX64 actual yet.
10. The link list is not virtualised (up to 500 rows, each possibly with a 218 dp torrent card
    or a quality menu): memory and layout time in Slint should be watched, a `ListView` cannot
    be nested inside the scrolling dialog without a fixed height.

Open questions for the owner:

1. Dialog width: Compose gives 620 dp on a wide window (94 % of the 660 cap). Keep 620, or use
   the 660 the code seems to intend?
2. Retry and the primary button touch (no gap) in the action row. Keep, or add the same 10 dp
   gap as after Cancel?
3. Torrent files card: a long torrent name pushes "Select All" out of view. Keep, or let the
   name ellipsize and keep the button visible?
4. The torrent file box is always 140 dp tall even for one file. Keep?
5. Minimum window size: Compose has none, the prototype uses 900 x 500. Which one is wanted?
