# 14. Slint 1.18.1 capabilities for the FetchJet desktop UI

Finished 2026-11-09. Every "works" below was compiled with `E:/tools/slint/bin/slint-compiler.exe`
(11 topic files plus 93 small cases: 62 compile, 31 are the limits listed below) and the behaviour
lines were run head-less with the testing backend (pointer, key and timer events sent from C++).

The compile-checked examples are kept in `docs/port/slint-caps/` (`01-textfield.slint` ...
`11-layout.slint`, `lib/fj-scroll.slint`, and `api_check.cpp` = the C++ side of all of them).
Use them as the starting point of the widget set. The Slint reference pages of this version were
saved as text in `E:/tools/probes/slint-caps/docs/` (not in the repository).

## 1. Text input

- `TextInput` has: `text`, `single-line`, `wrap`, `read-only`, `enabled`, `input-type`
  (`text`, `password`, `number`, `decimal`), `horizontal-alignment`, `vertical-alignment`,
  `font-*`, `color`, `selection-background-color`, `selection-foreground-color`,
  `text-cursor-width`, `preedit-text` (IME), `has-focus`, `cursor-position-byte-offset`,
  `anchor-position-byte-offset`; callbacks `edited`, `accepted`, `cursor-position-changed(pos)`,
  `key-pressed`, `key-released`; functions `focus()`, `clear-focus()`, `select-all()`,
  `clear-selection()`, `set-selection-offsets(a, b)`, `copy()`, `cut()`, `paste()`, `undo()`,
  `redo()`.
- It does **not** have: `placeholder-text`, `max-length`, `password-character`, `selected-text`,
  `focus-gained` / `focus-lost` callbacks. A placeholder is a `Text` under the input shown while
  `text == "" && preedit-text == ""`; focus changes are seen with `changed has-focus => { }`.
- Copy / cut / paste / undo / redo and Ctrl+A / Ctrl+C / Ctrl+X / Ctrl+V / Ctrl+Z work out of the
  box. While a `TextInput` has focus it consumes Ctrl+A and Delete (an outer `KeyBinding` for them
  does not fire); **Escape and unknown shortcuts (Ctrl+N) pass on** to outer handlers.
- `edited` fires for user edits only, not when the text is set from C++ or from a binding: this
  is exactly what the echo rule of `ARCHITECTURE.md` 8.3 needs.
- Select-all on focus: `changed has-focus => { if self.has-focus { self.select-all(); } }`; it
  applies one event-loop tick after the focus.
- Selection offsets are **UTF-8 byte offsets** (an Arabic letter is 2).
- A single-line input does not scroll by itself: keep it in a clipping `Rectangle` and move its
  `x` from `cursor-position-changed` (done in `01-textfield.slint`).
- Paste can be intercepted: `key-pressed(e) => { if e.modifiers.control && e.text == "v" { ... } }`.
- A context menu (Cut / Copy / Paste / Select all / Undo / Redo) = `ContextMenuArea` around the
  `TextInput`, calling the functions above.
- `01-textfield.slint` is a complete `FjTextField` (label slot, placeholder, error border, hover,
  focus border, leading image, trailing `@children` buttons, password with reveal, centered text,
  select-all-on-focus, paste hook, horizontal scrolling). Replace its `drop-shadow-*` glow with
  the nine-slice glow of report 13 (the software renderer draws no drop shadow).
- std-widgets `LineEdit` / `TextEdit` work too but pull in a lot of generated code (section 8).

## 2. Popups, menus, tooltips

- `PopupWindow`: `x`, `y` (relative to its parent element), `width`, `height`, `close-policy`
  (`close-on-click` | `close-on-click-outside` | `no-auto-close`), `show()`, `close()`,
  `is-open` (out), `forward-focus`. It is drawn inside the same window (no native window), so it
  cannot extend past the window edge.
- Behaviour (measured): keys go to the popup while it is open; `close-on-click` closes on any
  click; `close-on-click-outside` keeps it open for clicks inside; a click outside closes it and
  is **not** delivered to what is under it; Escape closes both; `no-auto-close` stays open on
  Escape and outside clicks (close it yourself). Focus returns to the previous element on close.
- Only **one** popup is open at a time, unless a popup is declared **inside** another popup
  (nested: the child opens on top, a click in the parent closes only the child, Escape closes the
  innermost). Opening a sibling popup closes the open one.
- A property or child of a popup cannot be read from the enclosing component
  (`Cannot access ... inside of a PopupWindow from enclosing component`): pass values in.
- Compiler crashes (no error message) with a `ContextMenuArea` written **inline inside an inline
  `PopupWindow`**. It compiles when the popup is its own component
  (`component Dlg inherits PopupWindow { ... }`) or when the `ContextMenuArea` is inside another
  component (a text field widget). Rule: every popup is its own component.
- A drop-down menu anchored to a button with dynamic items, check marks, separators and disabled
  rows is a `PopupWindow` with a `for` over a struct model (`MenuButton` in `02-popups.slint`).
- `ContextMenuArea { Menu { MenuItem { title; enabled; checkable; checked; icon; activated => } MenuSeparator { } Menu { title; ... } } }`:
  nested menus, `for` and `if` items work; the root `Menu` itself cannot be in an `if` / `for`;
  `shortcut:` on items works only inside a `MenuBar`. It opens on right press and on the Menu key
  (when a `FocusScope` inside has focus); Down + Return choose an item.
  **With a `TouchArea` child the right press is eaten**: call `area.show({ x: ..., y: ... })`
  from the `TouchArea`'s `pointer-event` on right-button up (as `04-list.slint` does).
- `MenuBar` exists (native menu bar of the window); the app does not need it.
- `Tooltip { text: ...; }` or `Tooltip { <custom content> }` as a child of any element; one per
  element; it shows after a hover delay and hides on leave; `@markdown(...)` text works.

## 3. Dialogs

Two ways, both tested:

1. **Overlay inside the window** (`ModalOverlay` in `03-dialogs.slint`): a full-size `Rectangle`
   with `z`, a scrim `TouchArea` (blocks clicks and the wheel, optional dismiss on outside click),
   a `FocusScope` around the card. Clicks never reach the covered content. Escape through
   `capture-key-pressed`, Return through `key-pressed`. **Tab leaves the dialog** unless the trap
   scope handles `Key.Tab` / `Key.Backtab` itself (return `accept` and move the focus by hand, or
   simply stop it); disabling the covered `FocusScope` alone is not enough. A `changed open`
   handler runs one tick later (not synchronously).
2. **`PopupWindow` as a dialog** (`no-auto-close`): focus is trapped by the popup itself, Tab
   cycles inside, a context menu inside it opens as a second popup and closes alone. But only one
   popup chain exists at a time, so a dialog that opens another dialog must nest it.

Decision: app dialogs (Add download, confirmations, pickers, pairing) are **overlays** stacked by
`z`; `PopupWindow` is for menus, drop-downs and tooltips (which then also work inside dialogs).

A second top-level window is possible (`export component X inherits Window`, created and shown
from C++; `always-on-top`, `no-frame`, `resize-border-width`, `WindowMoveArea`), and `Dialog`
with `dialog-button-role` exists; neither is needed.

## 4. Lists

- The compiler virtualizes a `for` that is the single child of an element **named `ListView`**
  whose root holds a `Flickable` with `@children`. A custom component called `ListView`
  (`lib/fj-scroll.slint`, no std-widgets, own scroll bar) gets the same virtualization as the
  std one: with 1 000 / 5 000 / 50 000 rows only the ~10 visible rows are instantiated (first
  frame 0.4 to 2 ms). Under another name, or with anything else next to the `for` (two children,
  a wrapper), it is an error or there is no virtualization (`Flickable + VerticalLayout + for`
  built all 5 000 rows: 245 ms).
- Row heights may differ. With uniform rows the content height is exact; with mixed heights it is
  estimated from the rows seen so far (340 000 against 338 000 exact in the test), so the thumb
  can shift slightly. Section headers are part of the row (`header` field: the row is taller and
  draws its header on top), as in `04-list.slint`.
- Scroll to an item: `content-y` is writable; `ensure-visible(y, h)` in `lib/fj-scroll.slint`.
  The y of a row comes from the presenter (`pure callback row-y(index) -> length`) when heights
  differ.
- Model cost: `set_row_data` 0.0002 ms per row; replacing a 5 000 row vector and redrawing
  0.6 ms; only visible rows are re-evaluated. Updating one visible row re-renders that row only.
  Inserting a row at the top does not re-create the visible rows.
- Pointer: `TouchArea.pointer-event(e)` gives `e.kind` (`down` / `up` / `move` / `cancel`),
  `e.button` (`left` / `right` / `middle`) and `e.modifiers.control / .shift / .alt / .meta`;
  `double-clicked` exists (both clicks also fire `clicked`); `has-hover`, `pressed`, `mouse-x/y`,
  `absolute-position`, `mouse-cursor`.
- The wheel scrolls a `Flickable` under rows that have their own `TouchArea` (120 px per notch,
  applied smoothly over the next frames). `mouse-drag-pan-enabled: false` stops drag-to-scroll.
- Custom scroll bar: `FjScrollBar` in `lib/fj-scroll.slint` (thumb drag, track click, hover
  widening), bound two-way to `content-y`.

## 5. Keyboard

- `FocusScope { key-pressed(e) => { ... accept / reject } capture-key-pressed(e) => { } }`;
  `e.text`, `e.modifiers`, `e.repeat`. `capture-key-pressed` on an outer scope sees every key
  first, even with a `TextInput` focused.
- `KeyBinding { keys: @keys(Control + A); enabled: ...; activated => { } }` inside a `FocusScope`
  (only there; two bindings with the same keys in one scope are a compile error). The innermost
  focused scope wins; unhandled keys bubble outward. `keys` can be a property
  (`in property <keys>`), set from C++ with `slint::Keys::from_parts(...)`.
- `Window` has no `key-pressed` and no `KeyBinding`: wrap the content in a `FocusScope` and
  `forward-focus` to it.
- Key codes: Tab `\u{09}`, Backtab `\u{19}`, Return `\u{0A}`, Escape `\u{1B}`, Delete `\u{7F}`,
  Backspace `\u{08}`, Space `" "`; arrows and the others are private-use characters
  (`Key.UpArrow`, `Key.DownArrow`, `Key.Home`, `Key.End`, `Key.PageUp`, `Key.PageDown`,
  `Key.F5`, `Key.Menu`...). Compare with the `Key` namespace, never with literals.
- Tab order = element order; disabled or invisible scopes are skipped; `focus-on-click`,
  `focus-on-tab-navigation`, `focus-gained(reason)` / `focus-lost(reason)` on `FocusScope`.
  `TextInputInterface.text-input-focused` tells if any text input has focus.
- Ctrl + V on the list (paste a link) = a `KeyBinding` in the list scope; it does not fire while
  a text field has focus (the field pastes).

## 6. Drag and drop, clipboard

- `DropArea { can-drop(event) => DragAction; dropped(event) => DragAction; has-drag; current-action }`
  and `DragArea { data; allow-copy; allow-move; allow-link; dragging; drag-finished(action) => }`
  exist. The payload type is `data-transfer` (opaque in `.slint`); in C++ `slint::DataTransfer`
  has `has_file_paths()`, `file_paths()` (`std::filesystem::path`), `has_plain_text()`,
  `plain_text()`, `image()`, `set_file_paths`, `set_user_data`. The documentation says a
  `DropArea` accepts drops from other applications "on platforms that support it".
  **Not checked at run time** (a real drag needs the mouse): to be tried with the owner's mouse;
  the fallback is `RegisterDragDrop` on `window().win32_hwnd()` in the host.
- Clipboard: `TextInput.copy()/paste()` use the system clipboard. Slint has no general clipboard
  function; the host reads / writes `CF_UNICODETEXT` itself (code in `api_check.cpp`).

## 7. Windows and tray (C++)

- `slint::Window`: `set_size` / `size` (physical or logical), `set_position` / `position`,
  `scale_factor()`, `is_maximized` / `set_maximized`, `is_minimized` / `set_minimized`,
  `set_fullscreen`, `is_visible`, `show()` / `hide()`, `win32_hwnd()` (for anything else through
  Win32: `GetWindowPlacement`, `SetWindowPos`...), `on_close_requested(() -> CloseRequestResponse)`
  (`HideWindow` | `KeepWindowShown`), `take_snapshot()`, `dispatch_*_event`.
- In `.slint`: `title`, `icon`, `min-width`, `min-height`, `preferred-*`, `always-on-top`,
  `no-frame`, `background`, `default-font-family`, `default-font-size`, and the two-way
  `maximized` / `minimized` / `full-screen`. `Window` has **no** `close-requested` callback and
  setting `x` / `y` on it is deprecated: both are done from C++.
- Close hides to the tray: `on_close_requested` returns `HideWindow`; the event loop must run in
  `slint::EventLoopMode::RunUntilQuit` and end with `slint::quit_event_loop()`.
- `SystemTrayIcon` (top-level component, no window): `icon`, `tooltip`, `title` (no effect on
  Windows), `visible`, `clicked`, a `Menu` with items, sub-menus, `for` items and checkable items.
  It has **no notification (balloon) function**: notifications are sent by the host
  (`Shell_NotifyIconW` with `NIF_INFO` on its own hidden icon, or a toast).
- Saved geometry: read `position()` / `size()` / `is_maximized()` before hiding or quitting and
  apply them before `show()`.
- Several top-level windows work. Globals are **per component instance**: a second window does
  not share the `Theme` / `Tr` globals of the first (set them on each).

## 8. Theme, styles, std-widgets cost

- A `global` with `in-out` / `out` properties drives the theme (`08b-theme-nowidgets.slint`); a
  global cannot contain elements (no `Timer` inside) but can have `changed` handlers, functions
  and `pure` functions.
- The system scheme: `Palette.color-scheme` from `std-widgets.slint` (importing only `Palette`
  adds nothing to the generated code), or the host reads Windows and sets `Theme.system-dark`.
  `Window` has no `color-scheme` property.
- Styles in the package: `fluent` (default on Windows, = `native`), `material`, `cosmic`,
  `cupertino`, `qt`, each with `-dark` / `-light`; chosen at compile time (`--style`, CMake
  `SLINT_STYLE`).
- Generated header size of one element (bytes), a rough measure of code and memory:

  | element | bytes | element | bytes |
  |---|---|---|---|
  | empty `Window` | 18 330 | std `Button` | 129 643 |
  | bare `TextInput` | 25 852 | std `CheckBox` | 87 011 |
  | bare `Flickable` | 20 296 | std `Switch` | 82 947 |
  | bare `PopupWindow` | 34 870 | std `ListView` | 130 903 |
  | custom `ListView` | 41 972 | std `ScrollView` | 108 201 |
  | `SystemTrayIcon` | 31 412 | std `ComboBox` | 274 999 |
  | `Tooltip` | 62 506 | std `LineEdit` | 472 553 |
  | `ContextMenuArea` + `Menu` | 265 756 | std `TextEdit` | 421 064 |
  | `MenuBar` | 376 394 | all std widgets | ~1 000 000 |

  Decision: **no std-widgets** in the app. The widget set is built on `Rectangle`, `Text`,
  `TouchArea`, `FocusScope`, `TextInput`, `Flickable`, `PopupWindow`, `Path`, `Image`.
  `ContextMenuArea` is used only where a native-style context menu is worth its size, otherwise
  the app's own popup menu.

## 9. Structs, models, callbacks, threads (C++)

- `slint::VectorModel<T>` (`push_back`, `set_row_data`, `insert`, `erase`, `set_vector`),
  `FilterModel`, `SortModel`, `MapModel`, `ReverseModel`. Structs can hold arrays, images and
  other structs; array fields are `std::shared_ptr<slint::Model<T>>`.
- Callbacks with return values and `pure callback`s in globals
  (`ui->global<Core>().on_format_bytes(...)`); `public function`s of a component are called with
  `invoke_xxx(...)`; array arguments arrive as models.
- Threads: `slint::invoke_from_event_loop(fn)` from any thread (this is the host's `post` for
  the Kotlin Main dispatcher), `slint::blocking_invoke_from_event_loop`. `slint::Timer`
  (repeated, single shot). `slint::run_event_loop(EventLoopMode::RunUntilQuit)` and
  `slint::quit_event_loop()`.
- Images: `slint::Image(SharedPixelBuffer<Rgba8Pixel>)`, `Image::load_from_path`,
  `load_from_data`.
- Enums declared in `.slint` are usable from compiled C++ but **not** from the interpreter the
  screenshot tool uses: DTOs avoid them (report 13).

## 10. Translations

`@tr("...")` with bundled `.po` files exists (`--bundle-translations`,
`slint::select_bundled_translation("ar")`), with plural forms and contexts. It is compile-time
data. FetchJet's texts come from Kotlin (`AppLocalization`, 15 languages, changed at run time), so
the UI uses **plain string properties in a global** (`Tr`) filled by the host, plus `pure
callback`s for the texts with parameters. `@tr` is not used.

## 11. Layout and animation

- `HorizontalLayout` / `VerticalLayout`: `spacing`, `padding(-left/right/top/bottom)`,
  `alignment` (`stretch` / `start` / `end` / `center` / `space-between` / `space-around` /
  `space-evenly`), children `horizontal-stretch` / `vertical-stretch` (Compose `weight`),
  `min-*` / `max-*` / `preferred-*`. `GridLayout` with `row`, `col`, `rowspan`, `colspan` and
  `Row { }`.
- `FlexboxLayout` (`flex-direction`, `flex-wrap: wrap`) = Compose `FlowRow` and the mirrored row
  (report 13).
- `layout-order` on a layout child changes its position without changing the tab order.
- Conditional (`if`) and repeated (`for`) elements, `@children` once per component (several
  slots are passed as properties or separate child components), `z`, `clip`, `opacity`,
  `visible`.
- `animate x, y, width, background, opacity { duration; easing; delay; iteration-count }`,
  `states [ name when cond: { ...; in { animate ... } out { animate ... } } ]`,
  `animation-tick()` for a running spinner (redraws every frame while visible: keep it only on
  screen when something spins).
- Scrolling column: `FjScrollArea` (`Flickable` + `VerticalLayout` + own scroll bar).
- Aspect ratio: `height: self.width * 9 / 16`. Percent sizes only for `width` / `height` /
  `preferred-*`. A binding loop through `preferred-width` (text -> width -> text) is a compile
  error: size chips from `label.preferred-width`, not the other way round.
- String functions available: `is-empty`, `character-count`, `to-float()`, `is-float()`,
  `to-lowercase()`, `to-uppercase()`; **no** `contains` / `substring` / `split`: all text logic
  stays in the presenter.
- Files: one component per file, `import { A, B } from "x.slint";`, `export { A }` re-exports;
  globals are shared by import.

## 12. Build of a large UI (62 files, about 60 components)

| way | generated | compile (MSVC /O1) | notes |
|---|---|---|---|
| one header | 6.0 MB `.h` | 48 s, needs `/bigobj` | every host change recompiles all of it |
| header + 4 `.cpp` (`--cpp-file` x4) | 0.8 MB `.h` + 5.1 MB | 36 s with `/MP4`; the host file alone 4 s | **use this** |

CMake: `slint_target_sources(target ui/app.slint COMPILATION_UNITS 4)`. The `slint-compiler`
step itself takes well under a second.

## 13. Rules that follow (for `ARCHITECTURE.md`)

1. No std-widgets; own widget set. Start from `docs/port/slint-caps/`.
2. Dialogs are in-window overlays with a focus trap that handles Tab; menus are popups; every
   popup is its own component.
3. Long lists: a component named `ListView` with one `for`; headers are part of the rows; the
   presenter knows row positions for scroll-to-row.
4. Selection clicks read the modifiers from `pointer-event`; right click opens the row menu with
   `show(position)` from the row's `TouchArea`.
5. Shortcuts are `KeyBinding`s in the list scope and a root scope; text fields keep their own.
6. Close-to-tray, geometry, notifications, clipboard and drag-drop fallback live in the host.
7. Generated code is split into compilation units.
