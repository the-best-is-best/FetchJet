# 09. UI porting spec: network, browser extension, devices, updates, when-done, pairing dialog, tools banner, snackbar

Source of truth: the Compose code on branch `desktop-native` as read on 2026-11-09. All paths are
relative to
`E:/tbib/FetchJet`. `UI/` below means `sharedUI/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`
and
`LOGIC/` means `sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`.

Target: Slint 1.18.1 markup that only renders state computed by a Kotlin presenter (in
`fjcore.dll`).
No business logic in `.slint`.

---

## 0. Scope and coverage

### 0.1 Files assigned to this spec (fully covered here)

| File                             | Lines | Composables / functions                                                                                           |
|----------------------------------|-------|-------------------------------------------------------------------------------------------------------------------|
| `UI/ui/NetworkSettingsUi.kt`     | 227   | `networkSummary`, `ProxyKind.label`, `NetworkPage`, `ProxyCard`, `SiteLoginsCard`                                 |
| `UI/ui/BrowserInstallSection.kt` | 233   | `seenId`, `BrowserInstallRows` (+ 4 dialogs inside it)                                                            |
| `UI/ui/DeviceSendDialog.kt`      | 554   | `DeviceSendSheet`, `DeviceRow`, `PairedChip`, `deviceEmoji`, `ModeRow`, `deviceSyncReason`, `DevicePairingDialog` |
| `UI/ui/UpdateUi.kt`              | 335   | `UpdateDialogHost`, `UpdateDialog`, `UpdatesSettingsCard`, `sizeText`, `installHint`, `plainNotes`                |
| `UI/ui/WhenDoneUi.kt`            | 157   | `whenDoneLabel`, `WhenDoneSettingsCard`, `WhenDoneBanner`, `WhenDoneDialogHost`                                   |

### 0.2 Pieces found elsewhere and ALSO specified here

The bridge pairing dialog, the tools download banner and the snackbar host are not in any file of my
list. They
are specified in full here (sections 9, 10, 11). The small settings pages that only host my files
are specified
here as well so that this document can be used alone.

| Piece                                                                 | Where it is drawn                                                                                                       | Section here |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|--------------|
| Bridge pairing dialog "Allow this browser extension?"                 | `UI/HomeDialogs.kt:143-185` (`BridgePairingDialog`), mounted at `UI/App.kt:813-827`                                     | 9            |
| Tools download banner                                                 | `UI/TaskList.kt:74-101` (`ToolsDownloadBanner`), mounted at `UI/TaskList.kt:193-195`                                    | 10           |
| Snackbar host (two hosts)                                             | `UI/App.kt:170-177` (state + collector), `App.kt:877-882` (main host), `App.kt:539-545` (host inside the details sheet) | 11           |
| Settings > Browser extension page frame + "Browser connections" group | `UI/ui/SettingsPages.kt:669-757` (`BrowserExtensionPage`, `BrowserConnectionsGroup`)                                    | 5.1, 5.4     |
| Settings > Devices page                                               | `UI/ui/SettingsPages.kt:150-244` (`DevicesPage`, `DeviceListGroup`)                                                     | 6.8          |
| Tray submenu "When downloads finish"                                  | `desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt:805, 840-850`                                          | 8.5          |

Overlap with the sibling specs that existed in `desktopNative/docs/port/` when this one was finished
(2026-11-09):

| Piece                                                                                                                                           | Also in                                                                           | Status                                                                                                                                     |
|-------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| Network page, Devices page, Browser extension page (rows, their four dialogs, connections group), `UpdatesSettingsCard`, `WhenDoneSettingsCard` | `08-ui-settings.md` sections 5.1, 5.3, 5.4, 5.5, 5.8 and its units S3, S7, S8, S9 | Read side by side with this document: same rules and values for all five. Port each settings page ONCE (see the unit mapping in part (b)). |
| Bridge pairing dialog (short description), tray when-done submenu, `WhenDone.onExitApp`, "no minimum window size"                               | `10-desktop-shell-packaging.md` (lines 362-375, 469-475, 422)                     | Same facts; the full dialog layout is only here (section 9).                                                                               |
| `DeviceSendSheet`, `DevicePairingDialog`, `UpdateDialog`, `WhenDoneBanner`, `WhenDoneDialogHost`, `ToolsDownloadBanner`, snackbar               | no other spec found                                                               | Only here.                                                                                                                                 |

### 0.3 NOT covered here (owned by other specs; only the contract is restated)

- The Settings shell: side list of sections, live summary lines, `PageHeader`, page scroll and
  `AppScrollbar`
  (`UI/ui/FdmSettingsPanel.kt`, `UI/SettingsContent.kt`, `UI/ui/AppScrollbar.kt`). Section 1.4
  restates the frame my
  pages sit in.
- `SettingsGroup`, `SettingRow`, `SettingSwitch`, `SettingDivider`, `SegmentedPicker`, `StatusLabel`
  (`UI/ui/SettingsKit.kt`) and `FetchJetTextField` (`UI/ui/FetchJetTextField.kt`). They are restated
  in section 2
  because every page here is built from them, but they belong to the settings / text-field specs.
- The other dialogs in `HomeDialogs.kt` (`RestartOnPauseDialog`, `ClipboardOfferDialog`,
  `BrowserFallbackDialog`).
- The "Send to device" entry point (button in the task details: `UI/TaskDetails.kt:881-883`).
- The tray itself (icon, tooltip, Show / Close items): only its when-done submenu is specified here.

---

## 1. Global context

### 1.1 Window and breakpoints

- Desktop window: `rememberWindowState(width = 1100.dp, height = 750.dp)` (`main.kt:769`). **No
  minimum window size
  is set anywhere** (`grep minimumSize` over `desktopApp/` and `sharedUI/` finds nothing). The
  window can be shrunk
  to any size, so the Compose app does reach its tablet / phone layouts on a desktop.
- Root layout breakpoints (`UI/App.kt:194-200`): `< 680dp` = compact (phone layout, bottom
  navigation);
  `680..1099dp` = icon rail; `>= 1100dp` = full sidebar; details side panel only when `>= 1280dp`.
- Settings shell breakpoint (`UI/ui/FdmSettingsPanel.kt:251`): available width `>= 720dp` = section
  list (250dp)
  beside the page; below that the list is the first screen and a page opens over it with a back row
  ("←" 18sp primary + `settingsAllSettings` "All settings", `labelLarge` primary).
- Task list wide rows: `>= 720dp` (other spec).
- What changes below the wide layout for THIS area: nothing inside the pages and dialogs themselves;
  they have no
  width-dependent branches. Only (a) the Settings shell switches to list-then-page, (b) dialogs get
  narrower (section 3.1), (c) the snackbar's second host matters (section 11).

### 1.2 Theme tokens (`UI/theme/Color.kt`, `UI/theme/Theme.kt`)

`FetchJetTheme(isDarkMode)` = `MaterialTheme(colorScheme = dark or light)`; typography and shapes
are the Material 3
defaults. Dark / light values:

| Token                                    | Dark      | Light     |
|------------------------------------------|-----------|-----------|
| primary                                  | `#00E5FF` | `#0284C7` |
| onPrimary                                | `#031625` | `#FFFFFF` |
| primaryContainer                         | `#0C3D60` | `#E0F2FE` |
| onPrimaryContainer                       | `#C7F1FF` | `#0369A1` |
| secondary                                | `#818CF8` | `#4F46E5` |
| secondaryContainer                       | `#2C2F63` | `#EEF2FF` |
| tertiary                                 | `#F43F5E` | `#E11D48` |
| background                               | `#080D1A` | `#F8FAFC` |
| surface                                  | `#0F1728` | `#FFFFFF` |
| surfaceVariant                           | `#172238` | `#F1F5F9` |
| onSurface                                | `#F1F5F9` | `#0F172A` |
| onSurfaceVariant                         | `#94A3B8` | `#475569` |
| outline                                  | `#334155` | `#94A3B8` |
| outlineVariant                           | `#1E2B42` | `#CBD5E1` |
| error                                    | `#F87171` | `#DC2626` |
| `StatusCompleted` (literal, both themes) | `#10B981` | `#10B981` |

Tokens the app does NOT override, so the Material 3 baseline (purple-grey) values apply. They are
visible in this
area and must be reproduced for "same look":

| Token (used by)                                      | Dark      | Light     |
|------------------------------------------------------|-----------|-----------|
| surfaceContainerHigh (every `AlertDialog` container) | `#2B2930` | `#ECE6F0` |
| surfaceContainerHighest (unchecked `Switch` track)   | `#36343B` | `#E6E0E9` |
| inverseSurface (snackbar container)                  | `#E6E0E9` | `#322F35` |
| inverseOnSurface (snackbar text)                     | `#322F35` | `#F5EFF7` |

Note: `desktopNative/ui/theme.slint` currently has `completed: #34D399`; this area uses
`StatusCompleted = #10B981`.

### 1.3 Typography, fonts, direction

Material 3 version pinned by the project: `org.jetbrains.compose.material3:material3:1.12.0-alpha03`
(`gradle/libs.versions.toml`). Values below were read from that version's sources jar in the local
Gradle cache.

| Role                                            | Size / line height | Weight | Letter spacing |
|-------------------------------------------------|--------------------|--------|----------------|
| headlineSmall                                   | 24sp / 32sp        | 400    | 0              |
| titleMedium                                     | 16sp / 24sp        | 500    | 0.2sp          |
| bodyLarge (default `LocalTextStyle` of the app) | 16sp / 24sp        | 400    | 0.5sp          |
| bodyMedium                                      | 14sp / 20sp        | 400    | 0.2sp          |
| bodySmall                                       | 12sp / 16sp        | 400    | 0.4sp          |
| labelLarge (text inside every M3 button)        | 14sp / 20sp        | 500    | 0.1sp          |
| labelSmall                                      | 11sp / 16sp        | 500    | 0.5sp          |

- Line-height rule: M3 text styles use `LineHeightStyle(Center, Trim.None)`, so **a single line of
  text occupies its
  full line height** (a `bodySmall` line is 16dp tall, a `bodyMedium` line 20dp).
- A `Text` with only `fontSize = N.sp` and no `style` inherits the surrounding style. In page
  content that is
  `bodyLarge`: line height 24sp, letter spacing 0.5sp, weight 400. Inside an `AlertDialog`: title
  slot =
  `headlineSmall` colored `onSurface`; text slot = `bodyMedium` colored `onSurfaceVariant`; button
  slot =
  `labelLarge`. Inside any M3 button = `labelLarge` in the button's content color.
- Font family: M3 uses `FontFamily.SansSerif`, which Compose Desktop maps on Windows to **Segoe UI**
  (fallback Arial).
  `FontFamily.Monospace` maps to **Consolas**. Emoji come from the system emoji font.
- `FontWeight.SemiBold` = 600, `Medium` = 500, `Bold` = 700.
- 1dp = 1 logical pixel (Slint `px` in logical pixels), 1sp = 1dp on desktop.
- Direction: the whole UI is RTL for Arabic and Persian (`AppLanguage.isRtl`, `App.kt:179-185`).
  Every `start/end`
  below mirrors. Sizes and speeds are wrapped by `ltr()` = `"\u2066" + text + "\u2069"`
  (`UI/utils/Formatters.kt:5`).
- All strings come from `LocalizationManager.Strings.<member>` (interface `AppLocalization`,
  `LOGIC/core/i18n/data_provider/AppLocalization.kt`; English in `EnglishProvider.kt`). The UI is
  re-created when
  the language changes (`key(language)`), so the presenter must re-emit every string on a language
  change.

### 1.4 Where each piece is mounted

Settings shell, wide layout (`FdmSettingsPanel.kt:250-298`), for orientation only:

```
Row(fill)
  Column(width 250, vertical scroll, padding end 12 top 4, spacing 4)   section list
  Box(width 1, color outlineVariant a=0.35)                              divider
  Box(weight 1)
    Column(vertical scroll, padding start 28, end 14, top 4, bottom 28)
      Column(max width 720)
        PageHeader: title headlineSmall Bold; 4dp; blurb bodyMedium onSurfaceVariant; 20dp
        page(current)                                                    <- my pages start here
    AppScrollbar (overlay, centre end)
```

Pages from this area and their section metadata (`FdmSettingsPanel.kt:51-84, 169-196`):

| Section                        | Glyph | Title (EN)                           | Blurb (EN)                                                                  | Page composable                                                           | Live summary line (text, colour)                                                                                                                                         |
|--------------------------------|-------|--------------------------------------|-----------------------------------------------------------------------------|---------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| BROWSER                        | `⧉`   | `sectionBrowser` "Browser extension" | `sectionBrowserBlurb` "Send links and videos from your browser to FetchJet" | `BrowserExtensionPage(isJvm)`                                             | desktop: hard-coded `"Edge · Firefox · Chrome"`, default colour                                                                                                          |
| DEVICES                        | `⇄`   | `sectionDevices` "Devices"           | `sectionDevicesBlurb` "Send downloads between your devices"                 | `DevicesPage(...)`                                                        | `summaryVisible` "Visible" / `summaryHidden` "Hidden" + (`" · N"` when N paired devices > 0); `#10B981` when visible                                                     |
| NETWORK                        | `◎`   | `sectionNetwork` "Network"           | `sectionNetworkBlurb` "Proxy and site logins"                               | `NetworkPage(isIos)`                                                      | `networkSummary(proxy, logins.size)`; `#10B981` when `proxy.isOn`                                                                                                        |
| UPDATES                        | `↻`   | `sectionUpdates` "Updates"           | `sectionUpdatesBlurb` "Your version and new releases"                       | `UpdatesSettingsCard()`                                                   | `summaryVersionAvailable(v)` "v available" in `#10B981`, else `summaryVersion(current)` "Version x" default colour. Section hidden when `UpdateState.supported == false` |
| GENERAL (hosts when-done card) | `◐`   |                                      |                                                                             | `GeneralPage` ends with `WhenDoneSettingsCard()` (`SettingsPages.kt:141`) | not in this spec                                                                                                                                                         |

Root overlays (`UI/App.kt`), in composition order: `UpdateDialogHost()` (800),
`WhenDoneDialogHost()` (802),
clipboard offer (805), **bridge pairing dialog** (813-827), **`DeviceSendSheet`** (830-832), **
`DevicePairingDialog`** (833-840), browser fallback, duplicate, overwrite, then the **main snackbar
host**
(877-882). Each Compose `Dialog` is its own layer: the most recently opened dialog is on top; when
two open in the
same frame the later one in this order is on top. The pairing dialog therefore sits above the send
sheet.

Task list column (`UI/TaskList.kt:192-196`), top to bottom: **`ToolsDownloadBanner`** (conditional),
**`WhenDoneBanner`** (conditional), then search / toolbar / list (other spec).

---

## 2. Building blocks used by this area (restated contract)

### 2.1 `SettingsGroup(title: String? = null, footer: String? = null, content)` —
`UI/ui/SettingsKit.kt:42-79`

```
Column(fillMaxWidth)
  [title] Text(title.uppercase(), labelSmall, Bold, letterSpacing 0.8sp, onSurfaceVariant)  padding start 4, bottom 8
  Surface(shape 14dp, color surface, border 1dp outlineVariant a=0.35, fillMaxWidth)
    Column(fillMaxWidth) { content }
  [footer] Text(footer, bodySmall, onSurfaceVariant)  padding start 4, end 4, top 8
```

Pages stack groups in a `Column(verticalArrangement = spacedBy(24.dp))`.

### 2.2 `SettingDivider()` — `SettingsKit.kt:82-88`

1dp horizontal line, colour `outlineVariant a=0.3`, inset 16dp on both sides.

### 2.3 `SettingRow(title, subtitle = null, enabled = true, onClick = null, trailing = null)` —
`SettingsKit.kt:91-129`

```
Row(fillMaxWidth, min height 56, [clickable when onClick != null && enabled], padding h16 v12, centre vertically)
  Column(weight 1)
    Text(title, bodyMedium, SemiBold, onSurface a=(enabled ? 1 : 0.45))
    [subtitle not blank] 2dp + Text(subtitle, bodySmall, onSurfaceVariant a=(enabled ? 1 : 0.45))   wraps, no max lines
  [trailing] 16dp spacer + trailing
```

Heights: title only 56dp; title + one subtitle line 62dp; with an M3 button or switch as trailing
72dp (those have a
48dp minimum touch target). A clickable row shows the M3 hover overlay (8% `onSurface`) and press
ripple (10%).

### 2.4 `SettingSwitch(title, subtitle, checked, enabled = true, onChange)` —
`SettingsKit.kt:131-142`

`SettingRow` whose whole row toggles (`onChange(!checked)`) with an M3 `Switch` as trailing.

### 2.5 `SegmentedPicker(options, selected, label, onSelect)` — `SettingsKit.kt:145-180`

```
Row(clip 10dp, background surfaceVariant a=0.45, padding 3, spacing 2)
  per option: Box(clip 8dp, background (selected ? primary : transparent), clickable, min height 32, padding h12 v7, centre)
                Text(label, 12sp, (selected ? Bold : Medium), (selected ? onPrimary : onSurface), maxLines 1)
```

The text inherits `bodyLarge` (24sp line height, 0.5sp spacing), so a segment is 38dp tall and the
picker 44dp.
Width = content. No animation. Clicking the already selected option still calls `onSelect`.

### 2.6 `StatusLabel(text, color)` — `SettingsKit.kt:183-197`

`Row(centre)`: 8dp circle in `color`, 6dp,
`Text(text, 12sp, SemiBold, color, maxLines 1, ellipsis)`.

### 2.7 `FetchJetTextField` — `UI/ui/FetchJetTextField.kt:71-124, 244-402`

Signature used here: `FetchJetTextField(value, onValueChange, modifier, label = null, placeholder = null,
enabled = true, keyboardOptions, visualTransformation, ...)`; single line, height 48dp, shape 12dp.

```
Column(alpha (enabled ? 1 : 0.6))
  [label] Text(label, 11sp, SemiBold, letterSpacing 0.3sp, maxLines 1, ellipsis)  padding start 4, bottom 5
          colour: error -> error; focused -> primary; else onSurfaceVariant (animated 180ms)
  Box(fillMaxWidth, clip 12dp)
     background: focused ? primary a=0.06 over surface : surfaceVariant a=0.35 over surface   (180ms)
     border: error -> error 1.5dp; focused -> primary 1.5dp; hovered -> outline 1dp; else outlineVariant a=0.7 1dp (180ms)
     glow when focused or error: drop shadow radius 10dp, offset (0, 2dp), colour accent, alpha 0.28 (220ms)
     Row(height 48, padding start 14 end 14, centre vertically)
        placeholder (when empty): bodyMedium, onSurfaceVariant a=0.65, maxLines 1, ellipsis
        text: bodyMedium, onSurface; caret colour = accent (primary, or error)
```

A labelled field is 24 + 5 + 48 = 77dp tall (the label line inherits the 24sp line height). Password
fields use
`PasswordVisualTransformation()` (every character drawn as `•`). A tap outside the focused field
clears focus (`Modifier.clearFocusOnTapOutside()` on the app root). Desktop: Tab / Shift+Tab move
focus; Enter does nothing in
these fields (no keyboard action is set).

---

## 3. Material 3 defaults this area depends on

Read from `material3-desktop-1.12.0-alpha03-sources.jar` and `ui-desktop-1.12.2-sources.jar` (local
Gradle cache).

### 3.1 `AlertDialog` (every dialog in this area)

- Layer: full-window scrim `#000000 a=0.6`; dialog centred in the window. Open animation 200ms
  ease-out: alpha
  0.2 -> 1, scale 0.95 -> 1, translateY 10dp -> 0 (scrim alpha follows).
- Dismiss: Esc and a click on the scrim both call `onDismissRequest`. There is no default button
  (Enter does nothing
  unless a button has focus; Tab cycles the buttons, Space / Enter activates the focused one).
- Width: content width clamped to `280dp..560dp`, and additionally to the "platform default width":
  580dp when
  `min(windowWidth, windowHeight) >= 600dp`, 440dp when `>= 480dp`, else 320dp. So at the default
  1100x750 window a
  dialog is at most 560dp; in a window shorter or narrower than 600dp it is at most 440dp. A dialog
  whose content
  has a `fillMaxWidth` child (send sheet, pairing dialog, update dialog, load-unpacked dialog,
  when-done countdown)
  takes the maximum; plain text dialogs wrap their text and buttons.
- Container: `Surface`, colour `surfaceContainerHigh` (see 1.2), tonal elevation 0, no shadow,
  default shape 28dp
  corners. Dialogs that pass `shape = RoundedCornerShape(18.dp)`: update, when-done (both), bridge
  pairing. The
  others (send sheet, device pairing, the four browser dialogs) keep 28dp.
- Inner layout: `Column(padding 24)`: title block (start aligned, 16dp bottom padding, style
  `headlineSmall`,
  colour `onSurface`), text block (24dp bottom padding, style `bodyMedium`, colour
  `onSurfaceVariant`,
  `weight(1f, fill = false)`: it shrinks first when the window is short and does not scroll by
  itself), then the
  buttons box aligned to the end.
- Content colour inheritance: a `Text` without a colour inside the text block is `onSurfaceVariant`.
  A nested
  `Surface` whose colour is a translucent variant (for example `surfaceVariant a=0.4`) has no
  matching "on" colour,
  so its content keeps the inherited `onSurfaceVariant`.
- Buttons: a flow row, 8dp between buttons; **dismiss button (s) first, confirm button last (at the
  end / right in
  LTR)**. When they do not fit on one line they stack with the confirm button on top. Each button
  occupies 48dp of
  layout height (40dp visible, centred).

### 3.2 Buttons

|                 | `Button` (filled)                                                                                  | `OutlinedButton`                                                  | `TextButton`                      |
|-----------------|----------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|-----------------------------------|
| Min size        | 58 x 40dp (48dp touch target height)                                                               | same                                                              | same                              |
| Content padding | h24 v8                                                                                             | h24 v8                                                            | h12 v8                            |
| Default shape   | fully rounded (20dp radius at 40dp height)                                                         | same                                                              | same                              |
| Container       | `primary`                                                                                          | transparent                                                       | transparent                       |
| Content colour  | `onPrimary`                                                                                        | **`onSurfaceVariant`**                                            | `primary`                         |
| Border          | none                                                                                               | 1dp **`outlineVariant`**                                          | none                              |
| Disabled        | container `onSurface a=0.10`, content `onSurfaceVariant a=0.38`                                    | content `onSurfaceVariant a=0.38`, border `outlineVariant a=0.10` | content `onSurfaceVariant a=0.38` |
| Label           | `labelLarge` (14sp / 500)                                                                          | same                                                              | same                              |
| States          | hover overlay 8% of content colour, press ripple 10%, focus 10%; filled gets a 1dp shadow on hover | same overlays                                                     | same overlays                     |

Many buttons here pass `shape = RoundedCornerShape(10.dp)`; each composable below says which shape
it uses. Note that
in this Material version the outlined button's label is `onSurfaceVariant`, not `primary`.

### 3.3 Selection controls and indicators

- `Switch`: track 52 x 32dp, fully rounded; touch target 48dp high. Checked: track `primary`, thumb
  24dp `onPrimary`.
  Unchecked: track `surfaceContainerHighest`, 2dp border `outline`, thumb 16dp `outline`. Pressed
  thumb 28dp.
  Disabled checked: track `onSurface a=0.12`, thumb `surface`; disabled unchecked: track `surfaceContainerHighest
  a=0.12`, border `onSurface a=0.12`, thumb `onSurface a=0.38`. Thumb slides with the default M3
  animation.
- `Checkbox`: 18dp box, 2dp corner radius, 2dp stroke, inside a 48dp touch target (40dp circular
  state layer).
  Unchecked: border `onSurfaceVariant`. Checked: fill `primary`, check mark `onPrimary`.
- `RadioButton`: 20dp ring, 2dp stroke, 12dp dot; `primary` when selected, `onSurfaceVariant` when
  not. With an
  `onClick` it has a 48dp touch target; with `onClick = null` it is just 24 x 24dp (20dp + 2dp
  padding).
- `LinearProgressIndicator`: height 4dp, default width 240dp (all uses here pass `fillMaxWidth`),
  round caps.
  Indicator `primary`, track `secondaryContainer`. Determinate (`progress = { f }`): active part
  from the start to
  `f`, then a 4dp gap, then the track, plus a 4dp `primary` stop dot at the very end; the lambda
  form does not
  animate between values. Indeterminate: the M3 two-segment sweep, 1750ms cycle.
- `CircularProgressIndicator` (indeterminate): default 40dp diameter, 4dp stroke, round cap, colour
  `primary`, no
  track; uses below override size / stroke where stated.
- `Divider()` / `HorizontalDivider()`: 1dp, colour `outlineVariant` unless a colour is passed.
- `Surface`: default colour `surface`, content colour `onSurface`, no elevation.

### 3.4 Snackbar

See section 11.

---

## 4. Network page — `UI/ui/NetworkSettingsUi.kt`

### 4.1 `internal fun networkSummary(p: ProxySettings, n: Int): String` (lines 36-40) and
`ProxyKind.label()` (42-46)

Used by the Settings section list (`FdmSettingsPanel.kt:176`).

```
label(NONE) = Strings.netProxyOff ("Off");  label(HTTP) = "HTTP";  label(SOCKS5) = "SOCKS5"     (HTTP / SOCKS5 hard-coded)
proxy = if (p.isOn) "${label(p.kind)} ${p.host}:${p.port}" else Strings.netProxyOff
result = if (n == 0) proxy else proxy + " · " + Strings.netLoginsCount(n)          ("{n} logins")
```

`ProxySettings.isOn = kind != NONE && host.isNotBlank() && port in 1..65535`
(`LOGIC/core/network/NetworkSettings.kt:37`).

### 4.2 `@Composable internal fun NetworkPage(isIos: Boolean)` (lines 49-55)

Used for `SettingsSection.NETWORK`.
`Column(spacedBy(24.dp)) { ProxyCard(isIos); SiteLoginsCard() }`.
On desktop `isIos = false`.

### 4.3 `@Composable private fun ProxyCard(isIos: Boolean)` (lines 57-160)

Purpose: edit the proxy (kind, server, port, optional login) as a draft and save it with a button.
This is the only
settings card with an explicit Save.

Reads: `NetworkSettings.proxy: StateFlow<ProxySettings>` (`saved`). Writes:
`NetworkSettings.setProxy(ProxySettings)`.

UI-local state (all reset to the saved values whenever `saved` changes, `remember(saved)`):
`kind: ProxyKind`, `host: String`, `port: String` (`""` when `saved.port <= 0`), `user: String`,
`password: String`;
plus `justSaved: Boolean` (not reset by `saved`; set false by any edit or kind change, true by
Save).

Derived:

```
draft   = ProxySettings(kind, host.trim(), port.trim().toIntOrNull() ?: 0, user.trim(), password)
invalid = kind != NONE && !draft.isOn
changed = draft != saved                       (data-class equality over all five fields; password not trimmed)
```

Layout:

```
SettingsGroup(title = netProxyGroup "Proxy",
              footer = isIos ? netProxyHintIos : netProxyHint
                       "Used by downloads, video downloads (yt-dlp) and torrents. Applies to downloads started after saving.")
  Box(fillMaxWidth, horizontalScroll, padding 16)
    SegmentedPicker(options = [NONE, HTTP, SOCKS5], selected = kind, label = label(),
                    onSelect = { kind = it; justSaved = false })
  [kind != NONE]
  Column(padding start 16, end 16, bottom 16; spacing 10)
    Row(spacing 10)
      FetchJetTextField(host, label = netProxyHost "Server", placeholder "127.0.0.1", weight 1)
      FetchJetTextField(port, label = netProxyPort "Port",
                        placeholder = (kind == SOCKS5 ? "1080" : "8080"), width 110dp,
                        onValueChange: keep digits only, at most 5 characters)
    Row(spacing 10)
      FetchJetTextField(user, label = netProxyUser "User name (optional)", weight 1)
      FetchJetTextField(password, label = netProxyPassword "Password", weight 1,
                        enabled = user.isNotBlank(), password masking)
    [invalid && (host.isNotBlank() || port.isNotBlank())]
      Text(netProxyBadPort "Enter a server and a port from 1 to 65535", bodySmall, error)
  Row(fillMaxWidth, padding start 16, end 16, bottom 12; arrangement End; centre vertically)
    [justSaved && !changed] StatusLabel(netSaved "Saved", #10B981)
    Box(width 12)
    Button(enabled = changed && !invalid) { Text(netSave "Save") }        default M3 shape (pill)
```

Every field edit also sets `justSaved = false`. The picker box scrolls horizontally only when the
card is narrower
than the picker (no scrollbar is drawn).

Save click:

```
NetworkSettings.setProxy(
    if (kind == NONE) saved.copy(kind = NONE)                       keeps the saved server / login, only turns it off
    else draft.copy(password = if (draft.user.isEmpty()) "" else draft.password))
justSaved = true
```

`setProxy` trims host and user and writes `network.json`; the `saved` flow then emits, the draft
fields reset to it,
`changed` becomes false and "Saved" appears next to the button until the next edit.

Quirk to keep or fix deliberately: with kind `Off`, edits made earlier to the hidden fields still
make `changed`
true; Save then writes a value equal to `saved`, the flow does not emit, and the button stays
enabled with no
"Saved" label.

No tooltips, no keyboard shortcuts, no animation besides the text-field and button defaults.

### 4.4 `@Composable private fun SiteLoginsCard()` (lines 162-227)

Purpose: list, remove and add HTTP basic-auth logins per site.

Reads: `NetworkSettings.logins: StateFlow<List<SiteLogin>>` (`SiteLogin(site, user, password)`),
shown in stored
order. Writes: `NetworkSettings.removeLogin(site)`,
`NetworkSettings.saveLogin(SiteLogin(site, user, password))`.

UI-local state: `site`, `user`, `password` (all `""` initially, not tied to the saved list).
`canAdd = SiteLogin.cleanSite(site).contains('.') && user.isNotBlank()`.

```
SettingsGroup(title = netLoginsGroup "Site logins",
              footer = netLoginsHint "For downloads that ask for a user name and password (HTTP basic auth). A site also covers its sub-domains.")
  [logins.isEmpty()] SettingRow(title = netLoginsEmpty "No saved logins")
  for each login:
     SettingRow(title = login.site, subtitle = login.user)
        trailing: TextButton { Text(netRemove "Remove", color = error) }  -> NetworkSettings.removeLogin(login.site)
     SettingDivider()                                         after EVERY login row, the last one included
  Column(padding 16; spacing 10)
    FetchJetTextField(site, label = netLoginSite "Site (example.com)", placeholder "example.com", fillMaxWidth)
    Row(spacing 10)
      FetchJetTextField(user, label = netUser "User name", weight 1)
      FetchJetTextField(password, label = netProxyPassword "Password", weight 1, password masking)   always enabled
    Row(fillMaxWidth, arrangement End)
      Button(enabled = canAdd) { Text(netLoginAdd "Add") }     default M3 shape (pill)
```

Add click: `NetworkSettings.saveLogin(SiteLogin(site, user, password))`, then all three fields are
cleared.
`saveLogin` stores `cleanSite(site)` and `user.trim()`, replaces an existing login for the same site
(the replaced
one moves to the end of the list) and ignores an empty site. Passwords are never shown.

`SiteLogin.cleanSite(text)` (`NetworkSettings.kt:62-64`): `trim` -> text after `"://"` -> before the
first `/` ->
after `@` -> before `:` -> lowercase -> remove a leading `www.` -> trim trailing dots.

---

## 5. Browser extension page — `UI/ui/BrowserInstallSection.kt` and `UI/ui/SettingsPages.kt:669-757`

### 5.1 `@Composable internal fun BrowserExtensionPage(isDesktop: Boolean)` —
`SettingsPages.kt:675-719`

```
Column(spacedBy(24.dp))
  SettingsGroup(title = extInstallGroup "Install",
                footer = extensionInstallFooter "With the extension, a \"Download with FetchJet\" button shows on videos and links, and the browser can hand its downloads to FetchJet. It talks only to this app on your device.")
    BrowserInstallRows()
    [!isDesktop] Android-only switches (keepReadyForBrowser, popUpOverBrowser)      never shown on desktop
  BrowserConnectionsGroup()
```

On the desktop `isDesktop = true`, so only the browser rows and the connections group exist.

### 5.2 `private fun seenId(browserId: String): String?` — `BrowserInstallSection.kt:49-53`

`"brave"` -> `null` (Brave reports itself as Chrome, so it can never show as connected); an id
starting with
`"org.mozilla."` -> `"firefox"`; anything else -> the id itself.

### 5.3 `@Composable internal fun BrowserInstallRows()` — `BrowserInstallSection.kt:59-233`

Purpose: one row per known browser (installed or not, extension connected or not, outdated or not)
with one button,
plus four dialogs that the button can open.

Reads directly from sharedLogic:

- `BrowserIntegration.browsers(): List<BrowserInfo>` once per entry into the page (`remember`).
  `BrowserInfo(id, name, installed, downloadUrl, hasLocalCopy)`. Desktop list, in this order then
  stably sorted
  installed-first (`LOGIC jvmMain .../core/browser/DesktopBrowsers.kt:55-119`):
  `chrome` "Google Chrome", `edge` "Microsoft Edge", `firefox` "Firefox", `brave` "Brave", `opera`
  "Opera",
  `vivaldi` "Vivaldi". `hasLocalCopy` = not Firefox and the Chromium extension is bundled in the
  app.
- `BridgePairing.seenBrowsers: StateFlow<Map<String, Long>>` (`seen`) and
  `BridgePairing.seenVersions: StateFlow<Map<String, String>>` (`versions`), keyed by `chrome`,
  `edge`, `firefox`,
  `opera`, `vivaldi`, `samsung`.
- `ExtensionStore.versionFor(id): String?` (suspend, network),
  `ExtensionStore.githubXpi(): SignedXpi?` (suspend,
  network; `SignedXpi(version, url, fileName)`), `ExtensionStore.download(xpi): ByteArray?`
  (suspend, network).
- `isOlderExtension(found, wanted = EXTENSION_VERSION)` and `EXTENSION_VERSION = "3.3.0"`
  (`LOGIC/core/update/AppVersion.kt`).
- Constants:
  `EDGE_STORE_PAGE = "https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj"`,
  `FIREFOX_ADDON_URL = "https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/"`.
- Actions: `BrowserIntegration.addExtension(id): AddExtensionResult`,
  `BrowserIntegration.installXpi(id, bytes, fileName): AddExtensionResult`,
  `BrowserIntegration.openBrowser(id)`,
  `BrowserIntegration.openFolder(folder)`, `LocalUriHandler.openUri(url)` (system browser),
  `LocalClipboardManager.setText(...)`.

UI-local state: `missing: BrowserInfo?`,
`steps: Pair<BrowserInfo, AddExtensionResult.LoadUnpacked>?`,
`failure: String?`, `fromFile: Pair<BrowserInfo, String>?`, `busy: String?` (id of the browser whose
signed build is
downloading), and per browser `storeVersion: String?`, `githubXpi: SignedXpi?` (both start null).

Per-browser derivation (exact, lines 88-117 and 136-150):

```
key        = seenId(b.id)
connected  = key != null && key in seen
version    = key?.let { versions[it] }
outdated   = connected && (version == null || isOlderExtension(version))
firefox    = b.id == "firefox" || b.id.startsWith("org.mozilla.")

lookup (once per browser while the condition holds): if b.installed && (outdated || (!connected && firefox))
    storeVersion = ExtensionStore.versionFor(b.id)             only "firefox" and "edge" have a store answer
    if (firefox) githubXpi = ExtensionStore.githubXpi()

storeHasIt      = storeVersion != null && !isOlderExtension(storeVersion)
githubHasIt     = firefox && githubXpi != null && !isOlderExtension(githubXpi.version)
fromGitHub      = (outdated || (!connected && firefox && storeVersion != null)) && !storeHasIt && githubHasIt
waitingForStore = outdated && !storeHasIt && !b.hasLocalCopy && !githubHasIt

subtitle =
   !b.installed -> "⚠️ " + browserNotInstalled                                     "Not installed on this device"
   outdated     -> "⚠️ " + (version == null ? extensionOldVersion(EXTENSION_VERSION)
                                            : extensionNeedsUpdate(version, EXTENSION_VERSION))
                   + "\n" +
                   ( storeHasIt     -> storeHasUpdate(storeVersion)
                     b.hasLocalCopy -> updateFromAppHint
                     githubHasIt    -> updateFromGitHubHint(githubXpi.version)
                     else           -> storeWaiting(EXTENSION_VERSION) )
   connected    -> "✓ " + extensionConnectedVersion(version)                        "Extension connected · 3.3.0"
   else         -> extensionNotAddedYet                                            "Extension not added yet"

label =
   busy == b.id               -> "…"                 (hard-coded U+2026)
   fromGitHub                 -> updateFromGitHub     "Update now"
   outdated && storeHasIt     -> openStore            "Open store"
   outdated && b.hasLocalCopy -> updateFromApp        "Update from FetchJet"
   outdated                   -> openStore            "Open store"
   else                       -> addExtension         "Add extension"

filled button  when b.installed && (!connected || (outdated && !waitingForStore))
outlined button otherwise (not installed; connected and current; outdated but waiting for the store)
button enabled = (busy == null)                      every row's button is disabled while one download runs
```

Layout: for each browser, `SettingDivider()` before every row except the first, then

```
SettingRow(title = b.name, subtitle = subtitle)                      subtitle may be two lines ("\n")
   trailing: Button or OutlinedButton(shape = RoundedCornerShape(10.dp), enabled = busy == null)
                Text(label, fontWeight = SemiBold)
```

Button click:

```
!b.installed                 -> missing = b
fromGitHub && githubXpi != null ->
     busy = b.id
     bytes = ExtensionStore.download(githubXpi)           (coroutine)
     busy = null
     bytes == null -> failure = extensionDownloadFailed
     else          -> handle(b, BrowserIntegration.installXpi(b.id, bytes, githubXpi.fileName))
outdated && storeHasIt       -> openUri(b.id == "edge" ? EDGE_STORE_PAGE : FIREFOX_ADDON_URL)
else                         -> handle(b, BrowserIntegration.addExtension(b.id))

handle(b, result):
   NotInstalled            -> missing = b
   LoadUnpacked(folder, page) -> copy folder to the clipboard; steps = (b, result)
   InstallFromFile(fileName)  -> fromFile = (b, fileName)            Android only in practice
   Failed(reason)          -> failure = reason                       reasons are hard-coded English from sharedLogic
   Opened                  -> nothing
```

Desktop meaning of `addExtension`: Firefox -> opens the Firefox Add-ons page in Firefox (`Opened`);
a Chromium
browser -> unpacks the bundled extension to `<home>/.fetch_jet/extension/chromium`, opens the
browser's extensions
page and returns `LoadUnpacked(folder, page)` with `page` = `chrome://extensions`,
`edge://extensions`,
`brave://extensions`, `opera://extensions` or `vivaldi://extensions`.

Dialogs (all M3 `AlertDialog`, default 28dp shape, default pill buttons; titles are
`Text(..., fontWeight = Bold)`
in `headlineSmall`; body text `bodyMedium` `onSurfaceVariant`):

| State                            | Title                                                       | Body                                              | Confirm (filled)                                                                     | Dismiss                                                                                                                                                | Esc / outside |
|----------------------------------|-------------------------------------------------------------|---------------------------------------------------|--------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|
| `fromFile = (b, name)` (154-168) | `installFromFileTitle(b.name)` "Install FetchJet in {name}" | `installFromFileSteps(b.name, name)` (multi-line) | `openBrowser(b.name)` "Open {name}" -> `BrowserIntegration.openBrowser(b.id)`; close | `OutlinedButton` `close` "Close" -> close                                                                                                              | close         |
| `missing = b` (170-184)          | `browserMissingTitle(b.name)` "{name} is not installed"     | `browserMissingMessage(b.name)`                   | `downloadBrowser(b.name)` "Download {name}" -> `openUri(b.downloadUrl)`; close       | `OutlinedButton` `close` -> close                                                                                                                      | close         |
| `steps = (b, r)` (186-223)       | `loadUnpackedTitle(b.name)` "Add FetchJet to {name}"        | see below                                         | `copyPath` "Copy path" -> clipboard = `r.folder` (dialog stays open)                 | two `TextButton`s in this order: `detailsOpenFolder` "Open folder" -> `BrowserIntegration.openFolder(r.folder)` (stays open); `close` "Close" -> close | close         |
| `failure = reason` (225-232)     | `addExtension` "Add extension"                              | `reason`                                          | `close` "Close" -> close                                                             | none                                                                                                                                                   | close         |

Body of the load-unpacked dialog:

```
Column(spacing 10)
  Text(loadUnpackedSteps(b.name, r.page))        multi-line: "{name} opened its extensions page ({page}). There:\n1. Turn on Developer mode.\n2. Click Load unpacked.\n3. Choose this folder (its path is already copied, just paste it):"
  SelectionContainer                               the path text can be selected with the mouse and copied
    Text(r.folder, Consolas, bodySmall)  fillMaxWidth, clip 8dp, background surfaceVariant, padding 10
  Text(loadUnpackedUpdates, bodySmall, onSurfaceVariant)   "Do this once: the extension then updates by itself whenever FetchJet updates."
```

Button order on screen (LTR): `[Open folder] [Close] [Copy path]`.

### 5.4 `@Composable private fun BrowserConnectionsGroup()` — `SettingsPages.kt:725-757`

Reads `BridgePairing.legacyAllowed: StateFlow<Set<String>>` and
`BridgePairing.blocked: StateFlow<Set<String>>`. **Renders nothing when both are empty.**

```
SettingsGroup(title = extConnectionsGroup "Browser connections",
              footer = extConnectionsFooter "Browser extensions you have allowed or blocked from sending links to FetchJet.")
  allowed.sorted() then blocked.sorted(), SettingDivider() between consecutive rows (not before the first):
     allowed: SettingRow(origin, extAllowedLabel "Allowed") trailing OutlinedButton(shape 10dp) { Text(extRevoke "Revoke") }
              -> BridgePairing.revokeAllowed(origin)
     blocked: SettingRow(origin, extBlockedLabel "Blocked") trailing OutlinedButton(shape 10dp) { Text(extUnblock "Unblock") }
              -> BridgePairing.unblock(origin)
```

`origin` is a string such as `chrome-extension://<id>`; it is the row title and wraps when long.

---

## 6. Devices — `UI/ui/DeviceSendDialog.kt` and `UI/ui/SettingsPages.kt:150-244`

Model types (`LOGIC/feature/device_sync/DeviceSync.kt`, `DeviceSyncModels.kt`):
`DiscoveredDevice(id, name, platform, host, port, paired)`,
`PairedDevice(id, name, platform, lastSeenEpochMs)`,
`enum SendMode { LINK_ONLY, TRANSFER_AND_CONTINUE }`,
`SendState = Waiting(deviceName) | Sending(deviceName, fraction: Float) | Done(deviceName) | Failed(deviceName, reason)`,
`PairingState = Connecting(peerName) | Confirm(peerName, peerId, code) | Failed(peerName, reason)`.

`DashboardUiState` fields used (`LOGIC/presentation/dashboard/DashboardContract.kt:215-223`):
`deviceShareTaskId: String?`, `discoveredDevices`, `pairedDevices`, `blockedDevices`,
`devicePairing: PairingState?`,
`deviceSending: SendState?`, `allTasks`.

Events (`DashboardContract.kt:412-444`): `OnShareTaskClicked(taskId)` (sent by the task details, not
here),
`OnDismissShareSheet`, `OnSendToDeviceClicked(taskId, deviceId, mode, deleteAfterSuccess)`,
`OnConfirmPairingClicked`, `OnCancelPairingClicked`, `OnBlockPairingClicked`,
`OnUnpairDeviceClicked(deviceId)`,
`OnUnblockDeviceClicked(deviceId)`, `OnManualConnectClicked(ip)`.

### 6.1
`@Composable fun DeviceSendSheet(state: DashboardUiState, onEvent: (DashboardUiEvent) -> Unit)` — lines 59-338

Mounted at `App.kt:830-832` while `state.deviceShareTaskId != null`. It is an `AlertDialog` (default
28dp shape,
default pill buttons), not a bottom sheet.

Derived inputs:

```
taskId      = state.deviceShareTaskId          (null -> render nothing)
task        = state.allTasks.firstOrNull { it.id == taskId }
canTransfer = task?.canTransferToDevice() == true
devices     = state.discoveredDevices
sending     = state.deviceSending
isBusy      = sending is Sending || sending is Waiting
streaming   = sending is Sending
```

`canTransferToDevice()` (`LOGIC/domain/model/DownloadTask.kt:109-124`): not a torrent, and (status
COMPLETED, or
`canResume() == true`).

UI-local state (all reset when `taskId` changes):

| State                   | Initial                                                      | Notes                                    |
|-------------------------|--------------------------------------------------------------|------------------------------------------|
| `selectedId: String?`   | `null`                                                       | auto-selection below                     |
| `mode: SendMode`        | `TRANSFER_AND_CONTINUE` when `canTransfer`, else `LINK_ONLY` | also reset when `canTransfer` changes    |
| `deleteAfter: Boolean`  | `false`                                                      |                                          |
| `query: String`         | `""`                                                         | exists only while `devices` is not empty |
| `showManualIp: Boolean` | `false`                                                      |                                          |
| `manualIp: String`      | `""`                                                         |                                          |

Auto-selection, run every time `devices` changes: if no device has `id == selectedId`, select the
first device of
`devices.sortedWith(compareBy({ !it.paired }, { it.name.lowercase() }))`, or `null` when the list is
empty. A valid
user choice is kept.

Layout:

```
AlertDialog(onDismissRequest = { if (!streaming) onEvent(OnDismissShareSheet) })
 title: Text(sysDeviceSendTitle "Send to device", titleMedium, Bold)                (16sp, not headlineSmall)
 text:  Column(fillMaxWidth, spacing 10)
   [task.fileName not blank] Text(fileName, bodyMedium, SemiBold, maxLines 1)        colour onSurfaceVariant (inherited), clipped, no ellipsis

   [devices.isEmpty()]
     Column(fillMaxWidth, padding v18, centre horizontally, spacing 10)
       Box(56dp circle, background primary a=0.12, centre) { CircularProgressIndicator(26dp, stroke 2.5dp) }
       Text(sysDeviceSearching "Looking for devices…", bodyMedium, onSurfaceVariant)
   [else]
     [devices.size > 5] FetchJetTextField(query, placeholder = sysDeviceSearch "Search devices", fillMaxWidth)   no label
     shown = devices.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                    .sortedWith(compareBy({ !it.paired }, { it.name.lowercase() }))
     Column(fillMaxWidth, max height 236, clip 12dp, background surfaceVariant a=0.55,
            border 1dp outline a=0.5 (12dp corners), vertical scroll, no scrollbar)
       [shown.isEmpty()] Text(sysDeviceNoMatch "No device matches", bodySmall, onSurfaceVariant) padding 14
       for each device, in order:
          [same group as the previous row] Divider(colour outline a=0.25) padding start 52
          [first row of a group] Text(device.paired ? sysDevicePairedLabel "Paired" : sysDeviceOthers "Other devices",
                                      labelSmall, onSurfaceVariant) padding start 12, top 8, bottom 2
          DeviceRow(device, selected = device.id == selectedId, onSelect = { selectedId = device.id })
     Divider()
     ModeRow(sysDeviceModeLink "Send link only", sysDeviceModeLinkDesc "The other device downloads it from the start",
             selected = mode == LINK_ONLY) -> mode = LINK_ONLY
     [canTransfer] ModeRow(sysDeviceModeTransfer "Transfer what's downloaded",
                           sysDeviceModeTransferDesc "The other device continues from the same point",
                           selected = mode == TRANSFER_AND_CONTINUE) -> mode = TRANSFER_AND_CONTINUE
     Row(fillMaxWidth, clickable -> deleteAfter = !deleteAfter, centre vertically)
       Checkbox(deleteAfter)  4dp  Text(sysDeviceDeleteAfter "Delete from this device after it's sent", bodyMedium, Medium, onSurface)

   Divider()                                                                    always
   TextButton(aligned start) -> showManualIp = !showManualIp
       Text((showManualIp ? "▾  " : "▸  ") + sysDeviceManualConnect "Connect by IP", bodySmall)     two spaces after the glyph; colour primary
   [showManualIp]
     Row(fillMaxWidth, centre vertically, spacing 8)
       FetchJetTextField(manualIp, placeholder "192.168.1.5", weight 1)                              no label
       OutlinedButton(enabled = manualIp.isNotBlank() && !isBusy) { Text(sysDeviceConnect "Connect") }
            -> onEvent(OnManualConnectClicked(manualIp.trim()))

   status, by `sending`:
     Waiting -> Row(fillMaxWidth, centre vertically, spacing 10)
                  CircularProgressIndicator(18dp, stroke 2dp)
                  Text(sysDeviceWaitingAccept "Waiting for the other device to accept…", bodySmall, onSurfaceVariant)
     Sending -> (an empty spacer item, which adds one more 10dp gap)
                LinearProgressIndicator(progress = sending.fraction, fillMaxWidth)
                Text(sysDeviceSending "Sending…", bodySmall)                                          onSurfaceVariant (inherited)
     Failed  -> Text(sysDeviceSendFailed "Couldn't send" + ": " + deviceSyncReason(sending.reason), bodySmall, error)
     Done    -> Text(sysDeviceSent.replace("{name}", sending.deviceName), bodySmall, primary)          "Sent to {name}"
     null    -> nothing

 confirm: Button(enabled = selectedId != null && !isBusy) { Text(sysDeviceSend "Send") }
            -> onEvent(OnSendToDeviceClicked(taskId, selectedId, mode, deleteAfter))
 dismiss: OutlinedButton(enabled = !streaming) { Text(cancel "Cancel") } -> onEvent(OnDismissShareSheet)
```

Behaviour notes:

- While the list is empty the send-mode rows and the checkbox are not shown and Send is disabled;
  "Connect by IP"
  stays available.
- While a send is streaming the dialog cannot be closed (Cancel disabled, Esc and outside clicks
  ignored). While
  only waiting for the other device it can; `OnDismissShareSheet` then also cancels the waiting
  send.
- Sending to a device that is not paired yet starts pairing: `DevicePairingDialog` opens above this
  sheet; after
  "Codes match" the send starts by itself
  (`LOGIC/presentation/dashboard/DashboardDeviceSync.kt:165-176, 244-251`).
- On success the view model sets `deviceShareTaskId = null` (the sheet closes) and emits the toast
  `sysDeviceSent` with the device name; so the `Done` line is normally not seen.
- The view model downgrades the mode to `LINK_ONLY` when the task cannot transfer; the UI does not
  need to.

### 6.2
`@Composable private fun DeviceRow(device: DiscoveredDevice, selected: Boolean, onSelect: () -> Unit)` — lines 341-398

```
host     = device.name.substringBefore(" · ")
plat     = device.name.substringAfter(" · ", "").ifBlank { device.platform }
showPlat = plat.isNotBlank() && !plat.equals(host, ignoreCase = true)

Row(fillMaxWidth, background (selected ? primary a=0.14 : transparent), clickable -> onSelect,
    padding h12 v8, centre vertically)                                                     row height 46dp
  Box(30dp, background primary a=(selected ? 0.22 : 0.12), corners 9dp, centre)
     Text(deviceEmoji(device.name + " " + device.platform), 15sp)
  10dp
  Row(weight 1, centre vertically)
     Text(host, bodyMedium, onSurface, SemiBold, maxLines 1, ellipsis)      takes only the width it needs
     [showPlat] Text(" · " + plat, bodySmall, onSurfaceVariant, maxLines 1)
  [device.paired] 6dp + PairedChip()
  RadioButton(selected, onClick = null) padding start 8                     24dp, display only; the whole row is the click target
```

The separator inside device names is `" · "` (space, U+00B7, space).

### 6.3 `@Composable private fun PairedChip()` — lines 401-417

`Box(background primary a=0.16, fully rounded, padding h8 v2)` with
`Text(sysDevicePairedLabel "Paired", labelSmall, primary)`; height 20dp.

### 6.4 `private fun deviceEmoji(label: String): String` — lines 419-425

Lower-case the label; contains `android`, `iphone`, `ipad` or `ios` -> `"📱"`, else `"💻"`.

### 6.5 `@Composable private fun ModeRow(title, desc, selected, enabled, onSelect)` — lines 427-457

```
Row(fillMaxWidth, clickable(enabled) -> onSelect, centre vertically)
  RadioButton(selected, onClick = onSelect, enabled)          48dp touch target
  4dp
  Column(weight 1)
    Text(title, bodyMedium, onSurface, Medium)
    Text(desc, bodySmall, onSurfaceVariant)
```

Both callers pass `enabled = true`.

### 6.6 `internal fun deviceSyncReason(reason: String): String` — lines 460-469

| `reason`              | Result                                                                                                                  |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------|
| `"blocked"`           | `pairRefusedBlocked` "The other device blocked this one"                                                                |
| `"busy"`              | `pairRefusedBusy` "The other device is answering another pairing request. Try again in a moment."                       |
| `"declined"`          | `pairDeclined` "The other device declined the pairing. Send again to try once more."                                    |
| `"expired"`           | `pairExpired` "The other device didn't answer in time. Send again to try once more."                                    |
| starts with `"wait:"` | `pairRefusedWait.replace("{seconds}", reason.removePrefix("wait:"))` "Wait {seconds} s before asking this device again" |
| anything else         | the reason text unchanged (e.g. `"not paired"`, an exception message)                                                   |

### 6.7
`@Composable fun DevicePairingDialog(pairing: PairingState, onConfirm, onCancel, onBlock = {})` — lines 472-554

Mounted at `App.kt:833-840` while `state.devicePairing != null`, on both the asking and the asked
device.
`AlertDialog`, default 28dp shape, default pill buttons.

```
AlertDialog(onDismissRequest = onCancel)
 title: Text(sysDevicePairTitle "Pair device", titleMedium, Bold)
 text:  Column(fillMaxWidth, centre horizontally, spacing 12)
   Connecting -> CircularProgressIndicator()                               40dp, 4dp stroke
                 Text(sysDevicePairConnecting "Connecting…", bodyMedium)   onSurfaceVariant (inherited)
   Confirm    -> Text(sysDevicePairBody "Check that this code is the same on both devices:", bodyMedium)
                 Text(pairing.code, 34sp, Bold, letterSpacing 8sp, primary)        6 digits, one line
                 Text(pairing.peerName, bodySmall, onSurfaceVariant)
   Failed     -> Text(sysDevicePairFailed "Pairing failed" + ": " + deviceSyncReason(pairing.reason), bodyMedium, error)
 confirm: [Confirm only] Button { Text(sysDevicePairConfirm "Codes match") } -> onConfirm
 dismiss: Row(centre vertically)
            [Confirm only] TextButton { Text(blockDevice "Block", color = error) } -> onBlock;  4dp
            OutlinedButton { Text(cancel "Cancel") } -> onCancel
```

Callbacks at the call site: `onConfirm` -> `OnConfirmPairingClicked`, `onCancel` ->
`OnCancelPairingClicked`,
`onBlock` -> `OnBlockPairingClicked`. Esc / outside = cancel. On-screen button order in the Confirm
state (LTR):
`[Block] [Cancel] [Codes match]`. The code's line box is only 20sp high (it inherits `bodyMedium`'s
line height)
while its glyphs are 34sp, so the digits visually use the 12dp gaps above and below; in Slint give
the code its own
row of about 44dp and check the spacing against a screenshot.

### 6.8
`@Composable internal fun DevicesPage(pairedDevices, onUnpair, blockedDevices, onUnblock)` and
`DeviceListGroup` — `SettingsPages.kt:150-244`

Reads `BehaviorSettings.visibleToDevices: StateFlow<Boolean>` (default false); writes
`BehaviorSettings.setVisibleToDevices(Boolean)` directly. `onUnpair(id)` ->
`OnUnpairDeviceClicked(id)`,
`onUnblock(id)` -> `OnUnblockDeviceClicked(id)` (`UI/SettingsContent.kt:64-67`).

```
Column(spacedBy(24.dp))
  SettingsGroup(title = sysDeviceSendTitle "Send to device")
    SettingSwitch(visibleToDevices "Visible to other devices",
                  visibleToDevicesHint "Other FetchJet devices on this network can find this one and send downloads to it. Off: you can still send to them.",
                  checked = visible)
  DeviceListGroup(title = pairedDevicesTitle "Paired devices", empty = noPairedDevices "No paired devices yet. Pair one from Send to device on a download.",
                  devices = pairedDevices, action = unpairDevice "Unpair", actionIsDanger = true)
  DeviceListGroup(title = blockedDevicesTitle "Blocked devices", empty = noBlockedDevices "No blocked devices",
                  devices = blockedDevices, action = unblockDevice "Unblock", actionIsDanger = false)

DeviceListGroup:
  SettingsGroup(title)
    [devices.isEmpty()] Text(empty, bodySmall, onSurfaceVariant) padding 16
    devices.sortedBy { it.name.lowercase() }, SettingDivider() between rows:
      host = name.substringBefore(" · ");  plat = name.substringAfter(" · ", "").ifBlank { platform }
      Row(fillMaxWidth, padding h16 v10, centre vertically)
        Column(weight 1)
          Text(host, bodyMedium, Medium, maxLines 1)
          [plat not blank and not equal to host ignoring case] Text(plat, bodySmall, onSurfaceVariant, maxLines 1)
        OutlinedButton(shape 10dp) { Text(action, color = actionIsDanger ? error : primary) } -> onAction(device.id)
```

Both groups are always shown (with their empty text when empty).

---

## 7. Updates — `UI/ui/UpdateUi.kt`

State: `UpdateManager.state: StateFlow<UpdateState>` (`LOGIC/core/update/UpdateManager.kt:83-106`):
`supported`, `currentVersion` (= `APP_VERSION`), `autoCheck`, `autoInstall`, `canInstallInApp`,
`checking`,
`lastCheckedMs`, `available: AppRelease?`, `showDialog`, `message: String?`,
`messageIsInstallError`,
`messageIsError`, `downloadProgress: Float` (0..1 while downloading, `-1f` otherwise),
`installerPath: String?`,
`skippedVersion`. `AppRelease(version, tag, title, notes, pageUrl, publishedAt, assets)` with
`notesForDisplay`
(notes without the `<!-- fetchjet-unchanged: ... -->` marker);
`ReleaseAsset(name, url, size, sha256)`.

Platform facts read directly: `PlatformUpdater.updatesFromStore` (true in the Microsoft Store
build),
`PlatformUpdater.pickAsset(assets)` (Windows: first `.msi`, else first `.exe`),
`PlatformUpdater.platformKey`
(`"windows"` on this target),
`MICROSOFT_STORE_PAGE = "ms-windows-store://pdp/?productid=9NP3HJRHMPJS"`,
`UPDATE_REPOSITORY = "the-best-is-best/FetchJet"`.

Actions: `UpdateManager.onAppStart()`, `.checkNow()`, `.setAutoCheck(Boolean)`,
`.setAutoInstall(Boolean)`,
`.showDialog()`, `.dismiss()`, `.skip()`, `.installNow()`; `LocalUriHandler.openUri(url)`.

### 7.1 `@Composable fun UpdateDialogHost()` — lines 47-55

Mounted once at the app root (`App.kt:800`). Calls `UpdateManager.onAppStart()` once when the UI
starts (it does the
automatic check 4 s later, at most every 12 h). Shows `UpdateDialog(state, release)` when
`state.supported && state.showDialog && state.available != null`.

### 7.2
`@Composable private fun UpdateDialog(state: UpdateState, release: AppRelease)` — lines 57-213

```
fromStore   = PlatformUpdater.updatesFromStore
asset       = fromStore ? null : PlatformUpdater.pickAsset(release.assets)
downloading = state.downloadProgress >= 0f
ready       = state.installerPath != null

AlertDialog(onDismissRequest = { if (!downloading) UpdateManager.dismiss() }, shape = 18dp)
 title: Column
   Text("🎉 " + updateNewVersionAvailable, Bold)                      "🎉 New version available", headlineSmall
   4dp
   Text(updateVersionLine(release.version, state.currentVersion), bodyMedium, primary, SemiBold)
                                                                      "FetchJet {v}  ·  you have {current}" (two spaces each side of the dot)
 text: Column
   Text(updateWhatsNew "What's new", labelLarge, SemiBold)            colour onSurfaceVariant (inherited)
   6dp
   Surface(shape 10dp, colour surfaceVariant a=0.4, fillMaxWidth)
     Box(max height 260, vertical scroll, padding 12)                 no scrollbar; mouse wheel scrolls
       Text(plainNotes(release.notesForDisplay).ifBlank { updateNoReleaseNotes "No release notes." }, bodySmall)   colour onSurfaceVariant (inherited, see 3.1)
   exactly one of, in this priority:
     downloading -> 12dp; Text(downloadingUpdate((progress * 100).toInt()), bodySmall)     "Downloading the update… {p}%"
                    4dp;  LinearProgressIndicator(fillMaxWidth): determinate when progress > 0, indeterminate when progress == 0
     ready       -> 12dp; Text(platformKey == "android" ? updateDownloadedAndroid : updateDownloadedDesktop, bodySmall, onSurfaceVariant)
                    desktop: "The update is downloaded. FetchJet will close, install it and open again. Running downloads are paused and can be resumed."
     !state.canInstallInApp -> 12dp; Text(fromStore ? storeInstallsItselfOpenNow : installHint(asset?.name), bodySmall, onSurfaceVariant)
   [state.message != null && state.messageIsInstallError] 8dp; Text(state.message, bodySmall, error)

 confirm (filled Button, shape 10dp), first match:
   canInstallInApp && ready         -> text platformKey == "android" ? updateInstall : updateRestartAndUpdate "Restart & update"
                                       -> UpdateManager.installNow()
   canInstallInApp && asset != null -> enabled = !downloading; text downloading ? updateDownloading "Downloading…" : updateDownloadAndInstall "Download & install"
                                       -> UpdateManager.installNow()
   fromStore                        -> text updateOpenMicrosoftStore "Open Microsoft Store"
                                       -> openUri(MICROSOFT_STORE_PAGE); UpdateManager.dismiss()
   else                             -> text asset != null ? updateDownloadWithSize(sizeText(asset.size)) "Download (12.3 MB)" : updateOpenReleasePage "Open release page"
                                       -> openUri(asset?.url ?: release.pageUrl); UpdateManager.dismiss()

 dismiss: Row(spacing 4)
   [!downloading] TextButton { Text(updateSkipThisVersion "Skip this version") } -> UpdateManager.skip()
   OutlinedButton(shape 10dp) { Text(updateLater "Later") } -> UpdateManager.dismiss()
```

- "Later" stays enabled during a download (it only hides the dialog; the download continues). Esc /
  outside click is
  ignored while downloading.
- On-screen button order (LTR): `[Skip this version] [Later] [primary action]`.
- On Windows the three real variants are: MSI build (`canInstallInApp` true) = Download & install ->
  progress ->
  Restart & update; Store build = "Open Microsoft Store" + the store hint; a build run without an
  installed launcher (`canInstallInApp` false, not Store) = "Download (size)" + `installHintOther`.

### 7.3 `@Composable fun UpdatesSettingsCard()` — lines 216-294

Settings > Updates page content (one group). Renders nothing when `!state.supported`.

```
SettingsGroup(title = sectionUpdates "Updates")
  SettingRow(title = "FetchJet " + state.currentVersion,                        "FetchJet " hard-coded
             subtitle = state.available != null ? versionIsAvailable(available.version) "Version {v} is available"
                                                : youHaveLatest "You have the version installed here")
     trailing:
       [state.available != null] Button(shape 10dp) { Text(updateSeeWhatsNew "See what's new") } -> UpdateManager.showDialog()
       [else] OutlinedButton(shape 10dp, enabled = !state.checking) -> UpdateManager.checkNow()
                 [state.checking] CircularProgressIndicator(14dp, stroke 2dp); 8dp
                 Text(state.checking ? updateChecking "Checking…" : updateCheckNow "Check now")
  [state.message != null]
     Text(state.message, bodySmall, colour = (messageIsInstallError || messageIsError) ? error : onSurfaceVariant)
        padding start 16, end 16, bottom 12
  SettingDivider()
  SettingSwitch(checkUpdatesAutomatically "Check for updates automatically",
                checkUpdatesAutomaticallyHint "Looks for a new version when FetchJet starts (at most twice a day).",
                checked = state.autoCheck) -> UpdateManager.setAutoCheck(it)
  SettingDivider()
  [state.canInstallInApp]
     SettingSwitch(downloadUpdatesAutomatically "Download updates automatically",
                   desktop: downloadUpdatesAutomaticallyHint "Downloads a new version in the background; you only confirm the restart.",
                   checked = state.autoInstall, enabled = state.autoCheck) -> UpdateManager.setAutoInstall(it)
  [else]
     SettingRow(installingUpdates "Installing updates",
                updatesFromStore ? installingUpdatesStore "Microsoft Store installs new versions by itself; FetchJet tells you when one is out."
                                 : installingUpdatesManual "On this device FetchJet tells you about a new version and links to it; you install it.")
  SettingDivider()
  SettingRow(updateReleaseNotes "Release notes", updateReleaseNotesHint "Every version and what changed",
             onClick -> openUri("https://github.com/the-best-is-best/FetchJet/releases"))
     trailing: Text("↗", primary, Bold)                                           16sp (inherits bodyLarge)
```

`state.message` texts are produced in sharedLogic (already localized): `sysCheckingForUpdates`,
`sysUpToDate(current)`,
`sysVersionNoChangesForDevice(v, current)`, `sysNoPublishedRelease`,
`sysCouldNotCheckForUpdates(reason)`,
`sysDownloadingAsset(name)`, `sysVersionReadyToInstall(v)`, `sysUpdateDownloadFailed(msg)`,
`sysUpdateNoInstallerForSystem`, `sysCouldNotStartInstaller(msg)`. The disabled auto-install switch
row is drawn at
alpha 0.45 (title and subtitle) and does not react to clicks.

### 7.4 Pure helpers (lines 296-335)

```
sizeText(bytes: Long)  = if (bytes > 0) "(" + formatBytes(bytes) + ")" else ""

formatBytes(bytes)     = ltr(plain)            UI/utils/Formatters.kt:10-23
   plain: bytes <= 0 -> "0 B"; else divide by 1024 while value >= 1024 (units B, KB, MB, GB, TB);
          number = (value * 10).toLong() / 10.0  (truncated to one decimal, always printed with one decimal: "12.0 MB")
          text = "$number $unit"

installHint(assetName: String?) =
   null              -> installHintReleasePage  "Open the release page to download the new version."
   ends with ".apk"  -> installHintApk
   ends with ".ipa"  -> installHintIpa
   ends with ".dmg"  -> installHintDmg
   ends with ".deb"  -> installHintDeb
   else              -> installHintOther        "Download the new version and install it over this one."
   (suffix tests ignore case)

plainNotes(md: String): per line, in this order:
   trimEnd()
   remove a leading  ^#{1,6}\s*
   replace a leading ^\s*[-*]\s+  with "• "
   remove every "**"
   replace  \[([^\]]+)]\((https?://[^)]+)\)  with the link text ($1)
   remove   <!--.*?-->
 then join with "\n", collapse 3 or more consecutive "\n" into "\n\n", trim.
```

---

## 8. When downloads finish — `UI/ui/WhenDoneUi.kt`

State (`LOGIC/core/power/WhenDone.kt`): `WhenDone.action: StateFlow<WhenDoneAction>` (`NOTHING, EXIT, SLEEP,
SHUT_DOWN`; per session, never saved), `WhenDone.secondsLeft: StateFlow<Int?>` (60 down to 1 during
the countdown,
else null), `WhenDone.pending: StateFlow<WhenDoneAction>` (the action the countdown is for),
`WhenDone.error: StateFlow<String?>`, `WhenDone.COUNTDOWN_SECONDS = 60`, `SystemPower.isSupported`
(true on
desktop). Actions: `WhenDone.choose(action)`, `.cancel()`, `.runNow()`, `.dismissError()`. The host
must set
`WhenDone.onExitApp` (quit the app) at start (`main.kt:799-800`). When the countdown starts
sharedLogic calls
`ExternalDownloadBridge.showAndFocus()` so the window comes to the front.

### 8.1 `fun whenDoneLabel(action: WhenDoneAction): String` — lines 34-39

`NOTHING` -> `whenDoneNothing` "Do nothing"; `EXIT` -> `whenDoneExit` "Exit FetchJet"; `SLEEP` ->
`whenDoneSleep`
"Sleep"; `SHUT_DOWN` -> `whenDoneShutDown` "Shut down". Also used by the tray menu.

### 8.2 `@Composable fun WhenDoneSettingsCard()` — lines 42-64

Last card of Settings > General (`SettingsPages.kt:141`). Renders nothing when
`!SystemPower.isSupported`.

```
SettingsGroup(title = whenDoneGroup "When downloads finish",
              footer = whenDoneHint "For this session only. You get 60 seconds to cancel first.")
  Box(fillMaxWidth, horizontalScroll, padding 16)
    SegmentedPicker(options = [NOTHING, EXIT, SLEEP, SHUT_DOWN], selected = action, label = whenDoneLabel,
                    onSelect = { WhenDone.choose(it) })
```

Applied at once; no local state.

### 8.3 `@Composable internal fun WhenDoneBanner()` — lines 67-92

Above the download list (`TaskList.kt:196`), under the tools banner. Renders nothing while
`action == NOTHING`.

```
Surface(shape 12dp, colour tertiary a=0.12, fillMaxWidth, padding bottom 8)              48dp high + 8dp gap below
  Row(padding start 14, end 4, centre vertically)
    Text(whenDoneBanner(whenDoneLabel(action)), bodySmall, SemiBold, tertiary, weight 1)   "When downloads finish: Shut down"
    TextButton { Text(cancel "Cancel") } -> WhenDone.cancel()                              label colour primary
```

### 8.4 `@Composable fun WhenDoneDialogHost()` — lines 95-157

Mounted at the app root (`App.kt:802`). Two independent dialogs.

Countdown, shown while `secondsLeft != null`:

```
text = pending == SLEEP     ? whenDoneSleepIn(seconds)     "The computer will sleep in {s} s."
     : pending == SHUT_DOWN ? whenDoneShutDownIn(seconds)  "The computer will shut down in {s} s."
     :                        whenDoneExitIn(seconds)      "FetchJet will exit in {s} s."

AlertDialog(onDismissRequest = {}, shape = 18dp)             Esc and outside clicks do nothing
 title: Text(whenDoneTitle "All downloads finished", Bold)   headlineSmall
 text:  Column { Text(text, bodyMedium); 12dp; LinearProgressIndicator(progress = seconds / 60f, fillMaxWidth) }
 confirm: Button(shape 10dp) { Text(cancel "Cancel") } -> WhenDone.cancel()
 dismiss: OutlinedButton(shape 10dp) { Text(whenDoneNow "Now") } -> WhenDone.runNow()
```

The bar starts full and drops by 1/60 every second, without animation between steps. The safe choice
(Cancel) is
the filled button at the end; `[Now] [Cancel]` on screen in LTR. When the count reaches zero
sharedLogic runs the
action itself.

Error, shown while `error != null`:

```
AlertDialog(onDismissRequest = { WhenDone.dismissError() }, shape = 18dp)
 title: Text(whenDoneGroup "When downloads finish", Bold)
 text:  Text(whenDoneFailed(message))                        "Could not do it: {message}"
 confirm: Button(shape 10dp) { Text(ok "OK") } -> WhenDone.dismissError()
```

### 8.5 Tray submenu — `desktopApp/.../main.kt:805, 840-850`

In the tray menu, between "Show App" and the separator: submenu `whenDoneGroup` "When downloads
finish" with one
checkable item per `WhenDoneAction.entries` (label `whenDoneLabel(choice)`, checked when it equals
the current
`WhenDone.action`); clicking an item calls `WhenDone.choose(choice)`. The menu is rebuilt when the
language changes.

---

## 9. Bridge pairing dialog — `UI/HomeDialogs.kt:143-185`

`@Composable internal fun BridgePairingDialog(request: BridgePairing.Request, onAllow: () -> Unit, onBlock: () -> Unit, onDismiss: () -> Unit)`

Shown while `BridgePairing.pending: StateFlow<BridgePairing.Request?>` is not null
(`Request(origin: String, legacy: Boolean)`; `LOGIC/core/network/BridgePairing.kt`). Call site
(`App.kt:813-827`):
`onAllow` -> `BridgePairing.answer(true)`, `onBlock` -> `BridgePairing.block()`, `onDismiss` (Esc /
outside click)
-> `BridgePairing.answer(false)`.

```
AlertDialog(onDismissRequest = onDismiss, shape = 18dp)
 title: Text(bridgePairTitle "Allow this browser extension?", Bold)                      headlineSmall
 text:  Column
   Text(request.legacy ? bridgePairOldText : bridgePairText, bodyMedium)
        new:    "A browser extension asks to send links to FetchJet. Allow it only if you just installed or reset the FetchJet extension."
        legacy: "An older FetchJet extension wants to send links. Allow it if it is yours; updating the extension makes this question unnecessary."
   8dp
   Text(request.origin, bodySmall, onSurfaceVariant, maxLines 2, ellipsis)                e.g. chrome-extension://abcdef...
 confirm: Button(shape 10dp) { Text(bridgePairBlock "Block") } -> onBlock                 the prominent button is Block, on purpose
 dismiss: OutlinedButton(shape 10dp) { Text(bridgePairAllow "Allow") } -> onAllow
```

On screen (LTR): `[Allow] [Block]`, Block filled. Rules that live in sharedLogic and must not be
re-implemented in
the UI: only one request is pending at a time; the request times out after 120 s (then `pending`
becomes null and
the dialog disappears with no user action); Block is remembered, a dismiss is a one-time "no"; the
bridge thread
that asked is blocked until the answer. The desktop app also brings its window to the front when a
pairing request
arrives (the bridge server calls `onShowAndFocus`), so the native host must do the same.

---

## 10. Tools download banner — `UI/TaskList.kt:74-101`

`@Composable internal fun ToolsDownloadBanner(tool: String?, fraction: Float?)`

Shown as the first item of the task list column when
`uiState.isDownloadingYtDlp && uiState.toolDownloadName != null` (`TaskList.kt:193-195`), with
`tool = uiState.toolDownloadName`, `fraction = uiState.toolDownloadFraction`
(`DashboardContract.kt:169-171`). Display only, no events.

```
Surface(shape 12dp, colour primary a=0.1, fillMaxWidth, padding bottom 8)                 46dp high + 8dp gap below
  Column(padding h14 v10)
    Text(homeUpdatingMediaTools(tool.orEmpty(), fraction?.let { (it * 100).toInt() }), bodySmall, SemiBold, primary)
         "Updating media tools · {tool} {p}%"   or, without a fraction,   "Updating media tools · {tool}…"
    6dp
    LinearProgressIndicator(fillMaxWidth): determinate (fraction) when fraction != null, else indeterminate
```

The Settings > Media tools page shows the same progress in its own way (other spec).

---

## 11. Snackbar (toast) — `UI/App.kt:170-177, 539-545, 877-882`

- One `SnackbarHostState` for the app. A collector on `viewModel.uiEffect` (`SharedFlow`, extra
  buffer 8) calls
  `snackbarHostState.showSnackbar(effect.text)` for every `DashboardUiEffect.ShowToast(text)`.
  `showSnackbar`
  suspends until that snackbar is gone, so messages are shown **one at a time, in order**; each
  stays 4 s (`SnackbarDuration.Short`, no action, no dismiss button). Up to 8 further messages wait;
  more are dropped by the
  emitters that use `tryEmit`.
- Main host:
  `Box(fillMaxSize, padding bottom 88, contentAlignment BottomCenter) { SnackbarHost(state) }`, the
  last
  child of the root surface. It is below every dialog layer (dimmed by a dialog's scrim).
- Second host: inside the task-details `Dialog` used when the window is narrower than 1280dp
  (`BottomCenter`, padding bottom 24), because that dialog layer would cover the main host. At
  `>= 1280dp` the
  details are a side panel and only the main host draws. In Slint one toast layer above everything
  replaces both.
- Look (M3 `Snackbar`): 12dp outer margin; width = available width up to 600dp; min height 48dp;
  shape 4dp corners;
  container `inverseSurface`, text `inverseOnSurface` (section 1.2), 6dp shadow; text `bodyMedium`,
  padding h16
  v14, wraps to more lines when long. Appears with a fade plus scale 0.8 -> 1, leaves with the
  reverse (M3 "fast"
  springs, about 150-200ms). So the visible snackbar's bottom edge is 100dp above the window bottom.
- Messages come from sharedLogic already localized. Emit sites: `DashboardDeviceSync.kt:114`
  (`sysDeviceReceived`
  "Download received from {name}"), `:301` (`sysDeviceSent` "Sent to {name}"),
  `DashboardTools.kt:25`
  (`sysMediaToolsUpdated` "Media tools updated"), `DashboardAddDownload.kt:229, 327, 452, 559`,
  `DashboardSettingsEvents.kt:233`, `DashboardTaskEvents.kt:261, 270`,
  `DashboardViewModel.kt:407, 415, 516`.
- Device send / receive also call `SystemNotice.show(id, title, text)`, which the desktop shows as a
  tray
  notification (`main.kt:762-768`).

---

## 12. Interaction summary for the whole area

- Mouse: single left click only. No double click, long press, right click, drag, Ctrl / Shift click
  or tooltip
  anywhere in this area.
- Hover: M3 buttons, clickable rows (`SettingRow` with `onClick`, `SettingSwitch`, `DeviceRow`,
  `ModeRow`, the
  delete-after row, segmented options) show the 8% state layer; text fields change their border to
  `outline`.
- Keyboard: Tab order follows layout order; Space / Enter activates the focused button, switch,
  checkbox or radio;
  Esc closes a dialog through its `onDismissRequest` (ignored by the when-done countdown, by the
  update dialog
  while downloading and by the send sheet while streaming). No accelerator keys.
- Scrolling: the settings page scrolls as a whole (shell). Inner scroll areas: the two
  segmented-picker boxes (horizontal, only when too narrow), the device list (max 236dp), the
  release notes box (max 260dp). None of them
  draws a scrollbar.
- Text selection: only the folder path in the load-unpacked dialog is selectable.
- Animations: text-field focus (180ms), M3 defaults for switch / radio / checkbox / progress /
  dialog / snackbar.
  Nothing custom.
- Hard-coded (not localized) texts: `"HTTP"`, `"SOCKS5"`, placeholders `"127.0.0.1"`, `"8080"`,
  `"1080"`,
  `"example.com"`, `"192.168.1.5"`, the glyphs `"⚠️ "`, `"✓ "`, `"…"`, `"🎉 "`, `"↗"`, `"▸  "`,
  `"▾  "`, `" · "`,
  `"📱"`, `"💻"`, the prefix `"FetchJet "` and the section summary `"Edge · Firefox · Chrome"`.

---

## (a) Reusable widgets the Slint port needs for this area

Widgets marked (shared) are also needed by other areas; one implementation should serve all.

| Widget                                                                       | Behaviour                                                                                                                                                                        | Visual spec                                                                                                                                                                                                                                                                                                                                       |
|------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FjDialog` (shared)                                                          | Modal overlay over the whole window; properties: `title`, `corner-radius` (28 or 18), `dismissable`; slots for title, body and actions; callbacks `dismissed` (Esc, scrim click) | Scrim `#000000` 60%; container `dialog-container` colour (`#2B2930` dark / `#ECE6F0` light); width rule of section 3.1; padding 24; title 24sp/32 `on-surface` (or a custom title block), 16dp gap; body 14sp/20 `on-surface-variant`, 24dp gap; actions row end-aligned, 8dp gaps, 48dp row height; 200ms fade / scale 0.95 / 10dp slide on open |
| `FjButton` filled / outlined / text (shared)                                 | `enabled`, `clicked`, optional leading spinner                                                                                                                                   | Section 3.2; property `radius` (pill or 10dp); property `text-color` override (error / primary)                                                                                                                                                                                                                                                   |
| `FjSwitch` (shared)                                                          | toggle                                                                                                                                                                           | Section 3.3                                                                                                                                                                                                                                                                                                                                       |
| `FjCheckbox` (shared)                                                        | toggle                                                                                                                                                                           | Section 3.3                                                                                                                                                                                                                                                                                                                                       |
| `FjRadio` (shared)                                                           | display-only (24dp) and clickable (48dp) variants                                                                                                                                | Section 3.3                                                                                                                                                                                                                                                                                                                                       |
| `FjLinearProgress` (shared)                                                  | `progress` 0..1 or `indeterminate`                                                                                                                                               | 4dp high, round caps, `primary` on `secondary-container`, 4dp gap and end dot when determinate                                                                                                                                                                                                                                                    |
| `FjSpinner` (shared)                                                         | indeterminate ring                                                                                                                                                               | sizes 14 / 18 / 26 / 40dp with strokes 2 / 2 / 2.5 / 4dp, `primary`                                                                                                                                                                                                                                                                               |
| `FjTextField` (shared)                                                       | `text`, `label`, `placeholder`, `enabled`, `password`, `digits-only` + `max-length` (or filtering in the presenter)                                                              | Section 2.7                                                                                                                                                                                                                                                                                                                                       |
| `SettingsGroup`, `SettingRow`, `SettingSwitchRow`, `SettingDivider` (shared) | Section 2                                                                                                                                                                        | Section 2                                                                                                                                                                                                                                                                                                                                         |
| `SegmentedPicker` (shared)                                                   | model of labels + selected index, `selected(index)`                                                                                                                              | Section 2.5, inside a horizontal `Flickable`                                                                                                                                                                                                                                                                                                      |
| `StatusLabel` (shared)                                                       | dot + text                                                                                                                                                                       | Section 2.6                                                                                                                                                                                                                                                                                                                                       |
| `InfoBanner`                                                                 | text + optional progress + optional text button                                                                                                                                  | 12dp corners, tint = accent at 10% (primary) or 12% (tertiary), text 12sp/16 600 in the accent; covers `ToolsDownloadBanner` and `WhenDoneBanner`                                                                                                                                                                                                 |
| `DeviceListRow`                                                              | selectable row: emoji tile, host, platform, Paired chip, radio                                                                                                                   | Section 6.2 / 6.3                                                                                                                                                                                                                                                                                                                                 |
| `ModeRow`                                                                    | radio + title + description                                                                                                                                                      | Section 6.5                                                                                                                                                                                                                                                                                                                                       |
| `PathBox`                                                                    | selectable monospace text                                                                                                                                                        | Consolas 12sp/16, `surface-variant` background, 8dp corners, padding 10                                                                                                                                                                                                                                                                           |
| `Toast` (shared)                                                             | queue-fed, shows one message for 4 s                                                                                                                                             | Section 11                                                                                                                                                                                                                                                                                                                                        |

---

## (b) Port units

Each unit lists the presenter state it needs (suggested field names) and the events it sends.
"Direct" means the
Compose UI calls a sharedLogic object itself; in the port these calls move into the presenter.

Mapping to `08-ui-settings.md` so that no settings page is ported twice:

| Unit here                                                        | Same code as                | Who should port it       |
|------------------------------------------------------------------|-----------------------------|--------------------------|
| N1 Network page                                                  | 08 unit S7 (Network part)   | one of the two, not both |
| N2 Browser extension page                                        | 08 unit S8                  | one of the two, not both |
| N4, Devices page part                                            | 08 unit S7 (Devices part)   | one of the two, not both |
| N5, settings card part                                           | 08 unit S9 (Updates part)   | one of the two, not both |
| N6, settings card part                                           | 08 unit S3 (when-done card) | one of the two, not both |
| N3, N4 pairing dialog, N5 update dialog, N6 banner + dialogs, N7 | none                        | this spec                |

### Unit N1 — Network settings page (227 lines)

- Source: `UI/ui/NetworkSettingsUi.kt:1-227`.
- Widgets: `SettingsGroup`, `SettingRow`, `SettingDivider`, `SegmentedPicker`, `FjTextField`,
  `FjButton`,
  `StatusLabel`. Depends on the Settings shell only.
- State: `proxyKindLabels[3]`, `proxyKindIndex`, `proxyHost`, `proxyPort`, `proxyUser`,
  `proxyPassword`,
  `proxyFieldsVisible`, `proxyPortPlaceholder`, `proxyPasswordEnabled`, `proxyShowError`,
  `proxySaveEnabled`,
  `proxyShowSaved`, `proxyFooter`; `logins[] {site, user}`, `loginsEmpty`, `loginSite`, `loginUser`,
  `loginPassword`,
  `loginAddEnabled`; section summary text and colour (`networkSummary`).
- Events: `proxyKindSelected(index)`, `proxyHostEdited(text)`, `proxyPortEdited(text)`,
  `proxyUserEdited(text)`,
  `proxyPasswordEdited(text)`, `proxySaveClicked()`, `loginSiteEdited`, `loginUserEdited`,
  `loginPasswordEdited`,
  `loginAddClicked()`, `loginRemoveClicked(site)`. Direct calls behind them:
  `NetworkSettings.setProxy`,
  `.saveLogin`, `.removeLogin`.

### Unit N2 — Browser extension page (about 320 lines)

- Source: `UI/ui/BrowserInstallSection.kt:1-233`, `UI/ui/SettingsPages.kt:669-757` (desktop branch
  only).
- Widgets: `SettingsGroup`, `SettingRow`, `SettingDivider`, `FjButton`, `FjDialog`, `PathBox`.
- State: `browserRows[] {id, name, subtitle, buttonLabel, buttonFilled, buttonEnabled}`;
  `browserDialog` (none / missing / steps / fromFile / failure) with `dialogTitle`, `dialogBody`,
  `dialogFolder`,
  `dialogFootnote`, button labels; `connections[] {origin, label, actionLabel, kind}` and
  `connectionsVisible`.
- Events: `browserButtonClicked(id)`, `browserDialogPrimary()`, `browserDialogOpenFolder()`,
  `browserDialogClosed()`,
  `connectionRevoke(origin)`, `connectionUnblock(origin)`. Direct calls behind them:
  `BrowserIntegration.*`,
  `ExtensionStore.*` (network, async), `BridgePairing.revokeAllowed / unblock`, open URL, set
  clipboard.
- Needs from the platform layer: a `BrowserIntegration` actual for mingwX64 (browser detection,
  launching, the
  bundled Chromium extension as files instead of a jar resource), "open URL", "set clipboard text".

### Unit N3 — Send-to-device sheet (about 410 lines)

- Source: `UI/ui/DeviceSendDialog.kt:59-469`.
- Widgets: `FjDialog` (28dp), `FjTextField`, `DeviceListRow`, `ModeRow`, `FjCheckbox`, `FjButton`,
  `FjSpinner`,
  `FjLinearProgress`.
- State: `sendSheetVisible`, `sendFileName`, `sendSearching`, `sendShowSearch`, `sendQuery`,
  `sendRows[] {kind: header | device, headerText, id, host, platform, showPlatform, emoji, paired, selected, dividerAbove}`,
  `sendNoMatch`, `sendShowModes`, `sendCanTransfer`, `sendModeIndex`, `sendDeleteAfter`,
  `sendManualOpen`,
  `sendManualIp`, `sendConnectEnabled`, `sendStatusKind` (none / waiting / sending / failed / done),
  `sendStatusText`, `sendFraction`, `sendEnabled`, `sendCancelEnabled`, `sendDismissable`.
- Events: `sendDeviceSelected(id)`, `sendQueryEdited(text)`, `sendModeSelected(index)`,
  `sendDeleteAfterToggled()`,
  `sendManualToggled()`, `sendManualIpEdited(text)`, `sendConnectClicked()` ->
  `OnManualConnectClicked(ip.trim())`,
  `sendClicked()` -> `OnSendToDeviceClicked(taskId, deviceId, mode, deleteAfter)`,
  `sendCancelled()` ->
  `OnDismissShareSheet`.
- Depends on Unit N4 (pairing dialog opens above it) and on the task details unit (entry point).

### Unit N4 — Device pairing dialog and Devices settings page (about 180 lines)

- Source: `UI/ui/DeviceSendDialog.kt:471-554`, `UI/ui/SettingsPages.kt:150-244`.
- Widgets: `FjDialog` (28dp), `FjSpinner`, `FjButton`, `SettingsGroup`, `SettingSwitchRow`,
  `SettingDivider`.
- State: `pairVisible`, `pairKind` (connecting / confirm / failed), `pairCode`, `pairPeerName`,
  `pairErrorText`;
  `visibleToDevices`, `pairedRows[] {id, host, platform}`, `blockedRows[] {id, host, platform}`,
  section summary.
- Events: `pairConfirmed()` -> `OnConfirmPairingClicked`, `pairCancelled()` ->
  `OnCancelPairingClicked`,
  `pairBlocked()` -> `OnBlockPairingClicked`, `visibleToggled(on)` ->
  `BehaviorSettings.setVisibleToDevices`,
  `unpairClicked(id)` -> `OnUnpairDeviceClicked`, `unblockClicked(id)` -> `OnUnblockDeviceClicked`.

### Unit N5 — Updates: dialog and settings card (335 lines)

- Source: `UI/ui/UpdateUi.kt:1-335`.
- Widgets: `FjDialog` (18dp), `FjButton` (10dp), `FjLinearProgress`, `FjSpinner`, `SettingsGroup`,
  `SettingRow`,
  `SettingSwitchRow`, `SettingDivider`.
- State: dialog: `updVisible`, `updVersionLine`, `updNotes` (already plain text), `updPhase` (idle /
  downloading /
  ready / manualHint), `updProgress`, `updProgressKnown`, `updPhaseText`, `updErrorText`,
  `updPrimaryLabel`,
  `updPrimaryEnabled`, `updShowSkip`, `updDismissable`. Card: `updSupported`, `updRowTitle`,
  `updRowSubtitle`,
  `updHasAvailable`, `updChecking`, `updMessage`, `updMessageIsError`, `updAutoCheck`,
  `updCanInstallInApp`,
  `updAutoInstall`, `updInstallInfoText`; section summary.
- Events: `updPrimaryClicked()` (presenter decides between `installNow`, open store, open asset or
  release page, and
  whether to `dismiss`), `updSkipClicked()`, `updLaterClicked()`, `updDismissed()`,
  `updSeeWhatsNewClicked()`,
  `updCheckNowClicked()`, `updAutoCheckToggled(on)`, `updAutoInstallToggled(on)`,
  `updReleaseNotesClicked()`.
  The presenter calls `UpdateManager.onAppStart()` once at start.
- Needs from the platform layer: a `PlatformUpdater` actual for mingwX64 and "open URL" (including
  the
  `ms-windows-store:` scheme).

### Unit N6 — When downloads finish (157 lines + tray wiring)

- Source: `UI/ui/WhenDoneUi.kt:1-157`; tray submenu `desktopApp/.../main.kt:805, 840-850`; exit hook
  `main.kt:799-800`.
- Widgets: `SettingsGroup`, `SegmentedPicker`, `InfoBanner`, `FjDialog` (18dp), `FjLinearProgress`,
  `FjButton`.
- State: `wdSupported`, `wdLabels[4]`, `wdIndex`, `wdBannerVisible`, `wdBannerText`,
  `wdCountdownVisible`,
  `wdCountdownText`, `wdCountdownFraction`, `wdErrorVisible`, `wdErrorText`.
- Events: `wdSelected(index)` -> `WhenDone.choose`, `wdCancel()` -> `WhenDone.cancel` (banner and
  countdown),
  `wdRunNow()` -> `WhenDone.runNow`, `wdErrorDismissed()` -> `WhenDone.dismissError`. Host duties:
  set
  `WhenDone.onExitApp`, bring the window forward when the countdown starts, mirror the choice in the
  tray submenu.
- Needs from the platform layer: a `SystemPower` actual for mingwX64 (sleep, shut down,
  `isSupported = true`).

### Unit N7 — Root overlays: bridge pairing dialog, tools banner, toast (about 110 lines)

- Source: `UI/HomeDialogs.kt:143-185`, `UI/App.kt:813-827`; `UI/TaskList.kt:74-101, 193-195`;
  `UI/App.kt:170-177, 539-545, 877-882`.
- Widgets: `FjDialog` (18dp), `FjButton` (10dp), `InfoBanner`, `Toast`.
- State: `bpVisible`, `bpText`, `bpOrigin`; `toolsBannerVisible`, `toolsBannerText`,
  `toolsBannerKnown`,
  `toolsBannerFraction`; `toastVisible`, `toastText`.
- Events: `bpAllow()` -> `BridgePairing.answer(true)`, `bpBlock()` -> `BridgePairing.block()`,
  `bpDismissed()` ->
  `BridgePairing.answer(false)`. The toast and the banner send nothing.

Suggested order: widgets first (`FjDialog`, buttons, progress, text field: shared with other areas),
then N7 and N6 (small, exercise dialog + banner + toast), N1, N5, N4, N3, N2.

---

## (c) UI-local state that moves into the Kotlin presenter, and helper functions to reproduce

### State

| Today in Compose                                                                                                                 | Rule to keep                                                                                                         |
|----------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Proxy draft: `kind`, `host`, `port`, `user`, `password` (`NetworkSettingsUi.kt:60-64`)                                           | Reset to `NetworkSettings.proxy.value` every time that flow emits. Port keeps digits only, max 5.                    |
| `justSaved` (`:65`)                                                                                                              | false on every edit and on a kind change; true on Save. "Saved" shows when `justSaved && !changed`.                  |
| Site-login form: `site`, `user`, `password` (`:165-167`)                                                                         | Cleared after Add. Not reset by anything else.                                                                       |
| Browser dialogs: `missing`, `steps`, `failure`, `fromFile` (`BrowserInstallSection.kt:66-69`)                                    | Four independent nullable states; one dialog per state.                                                              |
| `busy` (`:70`)                                                                                                                   | Id of the browser whose signed build is downloading; disables all row buttons.                                       |
| Per-browser `storeVersion`, `githubXpi` (`:96-97`)                                                                               | Fetched lazily under the condition in 5.3; keep them cached per browser while the page is open.                      |
| `browsers` (`:63`)                                                                                                               | Computed once per entry into the page (browser detection is not live).                                               |
| Send sheet: `selectedId`, `mode`, `deleteAfter`, `query`, `showManualIp`, `manualIp` (`DeviceSendDialog.kt:69-80, 137, 235-236`) | All reset when the sheet opens for another task; `mode` also when `canTransfer` changes; auto-selection rule of 6.1. |
| Snackbar queue (`App.kt:170-177`)                                                                                                | FIFO, one visible, 4 s each.                                                                                         |

There is no local state in: the pairing dialogs, the update UI, the when-done UI, the Devices page,
the tools
banner. They render sharedLogic state directly.

### Pure functions the presenter must reproduce (exact rules are in the sections named)

| Function                                                                                                                                                                                      | Section                                 |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| `networkSummary`, `ProxyKind.label`                                                                                                                                                           | 4.1                                     |
| `draft` / `invalid` / `changed` / Save payload of the proxy card                                                                                                                              | 4.3                                     |
| `canAdd` and `SiteLogin.cleanSite`                                                                                                                                                            | 4.4                                     |
| `seenId` and the whole per-browser derivation (`connected`, `outdated`, `storeHasIt`, `githubHasIt`, `fromGitHub`, `waitingForStore`, subtitle, label, button style, click routing, `handle`) | 5.2, 5.3                                |
| `isOlderExtension(found, wanted)`: split on `.`, parse ints (non-numeric = 0), compare position by position padding with 0; equal = not older                                                 | `LOGIC/core/update/AppVersion.kt:21-30` |
| Connections list: `allowed.sorted()` then `blocked.sorted()`, hidden when both empty                                                                                                          | 5.4                                     |
| Device list: filter by `name.contains(query.trim(), ignoreCase)`, sort `(!paired, name.lowercase())`, group headers, dividers only inside a group, search box only when more than 5 devices   | 6.1                                     |
| Device name split: `host` / `plat` / `showPlat`                                                                                                                                               | 6.2, 6.8                                |
| `deviceEmoji`                                                                                                                                                                                 | 6.4                                     |
| `deviceSyncReason`                                                                                                                                                                            | 6.6                                     |
| `isBusy`, `streaming`, Send / Connect / Cancel enabled rules, default mode                                                                                                                    | 6.1                                     |
| Paired / blocked device lists sorted by `name.lowercase()`                                                                                                                                    | 6.8                                     |
| Update dialog: `fromStore`, `asset`, `downloading`, `ready`, phase priority, primary button choice, percent = `(progress * 100).toInt()`                                                      | 7.2                                     |
| `sizeText`, `formatBytes`, `installHint`, `plainNotes`                                                                                                                                        | 7.4                                     |
| Updates card: row subtitle, message colour, switch enabled rule                                                                                                                               | 7.3                                     |
| `whenDoneLabel`, banner text, countdown text by `pending`, fraction = `seconds / 60f`                                                                                                         | 8                                       |
| Tools banner text: percent = `(fraction * 100).toInt()` or none                                                                                                                               | 10                                      |
| Section summaries for Browser, Devices, Network, Updates                                                                                                                                      | 1.4                                     |

---

## Risks and things to verify while porting

1. Dialog container colour is the Material baseline `surfaceContainerHigh` (`#2B2930` / `#ECE6F0`),
   not one of the
   app's navy tokens; outlined buttons are `onSurfaceVariant` on an `outlineVariant` border. Both
   are easy to get
   wrong from memory of older Material versions; compare with a screenshot of the running JVM app.
2. Emoji and symbol glyphs (`🎉 ⚠️ ✓ 📱 💻 ▸ ▾ ↗ ⧉ ⇄ ◎ ↻`) depend on Slint's renderer and font
   fallback; colour emoji
   may render monochrome or as boxes. Not tested here.
3. `ltr()` adds U+2066 / U+2069 around sizes; check that Slint does not draw them as visible boxes.
4. The pairing code text overflows its line box in Compose (6.7); match by eye.
5. Three of the 44 missing mingwX64 actuals are behind this area: `BrowserIntegration`,
   `PlatformUpdater`,
   `SystemPower` (plus `SystemNotice` for the tray notification). The JVM `BrowserIntegration`
   unpacks the Chromium
   extension from a jar resource and uses `~/.fetch_jet/extension/...`; the native build needs
   another source for
   those files.
6. `BridgePairing.ask` blocks the bridge thread until the UI answers; the native event loop must
   stay responsive
   and deliver `answer` / `block` from the UI thread without deadlocking the Kotlin side.
7. `ExtensionStore` lookups and the `.xpi` download are network calls triggered just by opening the
   Browser page;
   the presenter must run them off the UI thread and tolerate the page being closed meanwhile.
8. The when-done countdown dialog must not be dismissable by Esc or an outside click, and the app
   window must come
   to the front when it starts.
