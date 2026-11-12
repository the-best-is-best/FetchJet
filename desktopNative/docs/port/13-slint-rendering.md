# 13. Slint rendering probe (Slint 1.18.1, Windows, software renderer)

Finished 2026-11-09. Everything here was rendered and looked at; the test files are in
`desktopNative/tools/shot/probes/` and their pictures in `probes/out/`.

## 1. The screenshot tool

`desktopNative/tools/shot/` (see its `README.md`):

- `shot.exe <file.slint> [Component] --out x.png ...` renders any component off screen. It uses the
  Slint **interpreter** (`slint-interpreter.h`, present in the binary package: no C++ build per
  page) on a custom `slint::platform::Platform` whose window adapter draws with
  `slint::platform::SoftwareRenderer` into a pixel buffer (`headless.h`). System fonts work in this
  mode. The picture is pixel-identical to the real `winit-software` window (compared on
  `00-basic.slint`, 321 x 166, zero differing pixels).
- It sets properties and globals (`--set`, `--json`), switches `Theme.dark`, feeds models from JSON,
  sends pointer / key / text events, crops and zooms. One shot of the whole prototype page takes
  0.18 s. Several copies can run at once.
- `deskrun.exe` runs a real EXE on a hidden Windows desktop (no window on the owner's screen, no
  focus change), prints private bytes / working set and saves a BMP of each window. It is the tool
  for whole-app checks and RAM numbers.
- Limit: the C++ interpreter cannot set **enum** properties. DTOs must not use Slint enums
  (use `int` codes or strings).

## 2. Renderer choice (measured)

The prototype EXE (toy core), 4 s after start, on a hidden desktop:

| `SLINT_BACKEND` | private bytes | working set |
|---|---|---|
| `winit-software` | **14.6 MB** | 44.5 MB |
| `winit-skia-software` | 32.3 MB | 78.0 MB |
| `winit-femtovg` | 89.7 MB | 91.2 MB |
| `winit-skia` (GPU) | 235.2 MB | 190.1 MB |

Decision: `winit-software` stays. Skia draws colour emoji by itself but costs 18 MB more in its
cheapest mode, and it has the same Chinese fallback gap (section 5).

The binary package has: interpreter, live preview, testing, winit backend, the software, FemtoVG
and Skia renderers, accessibility (`private/slint_generated_public.h`).

## 3. Arabic and Persian text

`01-arabic-text.slint`, `01b-bidi-details.slint`.

- Shaping is correct: joined letters, lam-alef, diacritics, Persian letters (گ چ پ ژ) and ZWNJ.
- Bidi is correct. The paragraph direction comes from the first strong character: a text that
  starts with Arabic is laid out right-to-left, one that starts with Latin left-to-right.
- Brackets mirror, Arabic-Indic digits render.
- Sizes and speeds inside Arabic text flip their parts unless isolated: `MB 128.00` instead of
  `128.00 MB`. Wrapping the value in **U+2066 ... U+2069** (LRI / PDI: what `ltr()` does in
  sharedUI) fixes it. Do not use U+202A / U+202C (LRE / PDF): a stray bar glyph is drawn.
- `horizontal-alignment` never follows the text: the default and `start` are always left, `end`
  is always right, also for Arabic text. Alignment must come from the `Dir` global (section 4).
- `wrap: word-wrap` works for Arabic; the lines are aligned by `horizontal-alignment`.
- **`overflow: elide` is broken for a text that starts with Arabic / Persian**: no ellipsis, and
  the text is drawn past the left edge over its neighbours. `overflow: clip` (or a parent with
  `clip: true`) cuts it correctly at the box. A name that starts with Latin elides fine. Use the
  `NameText` widget below for any text that can be RTL and too long.
- `TextInput`: Arabic + Latin typing, caret movement with the arrow keys and selection are
  correct; the text read back is in logical order. std-widgets `LineEdit` also shows Arabic text
  and an Arabic placeholder.
- Weights 400 / 600 / 800 and sizes 11 to 22 px render well in Arabic.

```slint
export component NameText inherits Rectangle {
    in property <string> text;
    in property <bool> text-rtl;
    in property <color> ink;
    in property <color> behind;
    in property <length> font-size: 13px;
    in property <int> font-weight: 600;
    height: t.preferred-height;
    min-width: 0;
    preferred-width: t.preferred-width;
    horizontal-stretch: 1;
    clip: true;
    t := Text {
        x: 0;
        width: parent.width;
        text: root.text;
        color: root.ink;
        font-size: root.font-size;
        font-weight: root.font-weight;
        overflow: root.text-rtl ? clip : elide;
        horizontal-alignment: Dir.rtl ? right : left;
    }
    if root.text-rtl && t.preferred-width > root.width: Rectangle {
        x: 0;
        width: 28px;
        background: @linear-gradient(90deg, root.behind 0%, root.behind.with-alpha(0.92) 35%, root.behind.transparentize(1) 100%);
    }
}
```

`text-rtl` ("the text starts with a right-to-left letter") is computed by the presenter and sent
next to the text (`nameRtl: Boolean`); Slint cannot inspect characters. `behind` is the colour
under the text (the fade needs it).

## 4. Right-to-left layout

Slint 1.18.1 has **no** layout direction or mirroring (`HorizontalLayout` never mirrors; there is
no `text-direction` / `layout-direction` in the compiler). It does have `FlexboxLayout` with
`flex-direction: row | row-reverse`.

Tested approach (`02-rtl-layout.slint`, `02b-hbox-features.slint`): one global and three small
wrappers; pages are written once and mirror themselves.

```slint
export global Dir {
    in-out property <bool> rtl: false;
    out property <TextHorizontalAlignment> start: rtl ? TextHorizontalAlignment.right : TextHorizontalAlignment.left;
    out property <TextHorizontalAlignment> end: rtl ? TextHorizontalAlignment.left : TextHorizontalAlignment.right;
}

export component HBox inherits FlexboxLayout {
    in property <length> padding-start;
    in property <length> padding-end;
    flex-direction: Dir.rtl ? row-reverse : row;
    flex-wrap: no-wrap;
    alignment: stretch;
    padding-left: Dir.rtl ? root.padding-end : root.padding-start;
    padding-right: Dir.rtl ? root.padding-start : root.padding-end;
}

export component DText inherits Text {
    horizontal-alignment: Dir.start;
}
```

What works inside `HBox` (all checked in both directions): `if` and `for` children, `spacing`,
`padding-start` / `padding-end`, `horizontal-stretch` (1 : 2 split, a stretch spacer), fixed
widths, nested `HBox`, `alignment: start | end | center | space-between` (start = right in RTL),
`cross-axis-alignment: center`, a chip sized by its content
(`Rectangle { width: box.preferred-width; box := HBox { ... } }`).

Other mirroring rules:

- absolute positions: `x: Dir.rtl ? parent.width - self.width - 8px : 8px;`
- a progress fill starts at the right in RTL (`x: Dir.rtl ? parent.width - self.width : 0;`);
- directional glyphs (back arrow, chevron) swap with `Dir.rtl`; symmetric icons do not change;
- a `GridLayout` cell can also mirror with `col: Dir.rtl ? (n - 1 - i) : i`.

Cost, 200 full task rows built at once (`rtlbench`, no virtualization):

| row layout | relayout | flip direction | private bytes |
|---|---|---|---|
| `HorizontalLayout` (never mirrors) | 6.0 to 6.6 ms | - | 15.1 MB |
| `GridLayout` with computed columns | 8.5 to 10.0 ms | 32 ms | 16.6 MB |
| `HBox` (`FlexboxLayout`) | 28.9 to 29.8 ms | 46 ms | 17.7 MB |

`FlexboxLayout` is about 4.5 times slower to lay out than `HorizontalLayout`. Rule: `HBox` for the
chrome, dialogs and settings (few elements); **task rows and other long-list rows use a
`GridLayout` with mirrored columns**, and long lists are virtualized, so only the visible rows are
laid out.

## 5. Fonts and fallback

`03-font-fallback.slint`, `03b-fallback-details.slint`, `03c-font-per-language.slint`, with
`default-font-family: "Segoe UI"`.

- Japanese, Korean, traditional Chinese, Hindi (conjuncts and matras), Russian, Turkish,
  Indonesian / German / French / Spanish / Portuguese / Italian accents, Arabic and Persian all
  render through the automatic fallback, also bold and at 11 / 13 px.
- **Simplified Chinese loses characters** (载 设 暂 简 ... are simply not drawn: `下载管理器`
  shows as `下 管理器`). The fallback picks a Japanese font that lacks them. The Skia renderer
  has the same gap (it draws boxes).
- `font-family: "Microsoft YaHei UI"` shows every Chinese character and still falls back
  correctly for Japanese, Korean, Hindi, Russian and Arabic. A comma list
  (`"Segoe UI, Microsoft YaHei UI"`) does **not** work (it is taken as one unknown name).
  `Microsoft JhengHei UI` also covers both Chinese variants. A family that does not exist falls
  back to the default without an error.
- Rule: `Theme.font` = `"Microsoft YaHei UI"` when the app language is `zh`, `"Segoe UI"`
  otherwise, set as the window's `default-font-family`. User text that can hold Chinese while the
  app is in another language (file names, titles, paths, URLs) gets a per-text flag from the
  presenter (`nameHan: Boolean`: the text contains a CJK ideograph) and uses
  `font-family: nameHan ? "Microsoft YaHei UI" : Theme.font`. Checked: Latin, Japanese and Korean
  names look the same with either family.
- Symbols: with the default family ✓ ↗ ↓ ↑ ▼ … • · — ★ render, but **✕ ↺ ⟳ ⋮ ☰ are missing**.
  `font-family: "Segoe UI Symbol"` has all of them (monochrome). Icons are drawn as `Path`
  (section 7) or as images; a text glyph used as an icon must name `"Segoe UI Symbol"`.

## 6. Colour emoji

The Compose app uses emoji as icons (`probes/emoji-used-by-compose.txt`: 63 of them). The software
renderer draws them monochrome (outline glyphs of Segoe UI Emoji); no option of the software
renderer draws colour glyphs.

`emoji_d2d.h` (`fj::EmojiRasterizer`) draws them in colour with DirectWrite + Direct2D
(`D2D1_DRAW_TEXT_OPTIONS_ENABLE_COLOR_FONT`, WIC bitmap target, un-premultiplied RGBA into
`slint::SharedPixelBuffer`), fitted to a square box (`renderIcon`) or as text (`renderText`).
Looked at 14, 18, 24 and 48 px on dark and light (`04-emoji-100.png`, `04-emoji-150.png`): same
pictures as Windows shows elsewhere, crisp at every size. Flags (🇪🇬) draw as the letters `EG`:
Windows has no flag glyphs (the Compose app shows the same).

Cost, 252 images (63 emoji x 4 sizes, 823 KB of pixels), `emojimem`:

| step | private bytes |
|---|---|
| start | 2.7 MB |
| D2D + DirectWrite + WIC factories | +0.2 MB |
| first glyph | +2.4 MB |
| all 252 images | **+27.1 MB**, 1.14 s (4.5 ms each) |
| after releasing D2D + DirectWrite + WIC | **+13.3 MB stays** |

In-process rasterizing is too expensive for a 50 MB app. The same work in a **child process**
that writes a cache, read by the app:

| | time | private bytes in the app |
|---|---|---|
| child writes 252 PNG (211 KB) + a raw atlas (823 KB) | 1.6 s, in the child | 0 |
| app loads the 252 PNG files | 42.7 ms | +1.5 MB |
| app loads the raw atlas | **1.7 ms** | **+0.8 MB** |

Design for the host:

- `FetchJet.exe --emoji-cache <dir>` (the same EXE, no window) rasterizes the app's emoji list at
  the sizes the UI uses, multiplied by the current scale factor, and writes `atlas.bin` (raw RGBA,
  one image after another) plus `atlas.json` (key, emoji list version, sizes, scale, the size and
  time of `seguiemj.ttf`).
- At start the host loads the atlas when its key matches and fills an `Emoji` global
  (`pure callback icon(string, int) -> image` or one image property per icon). Otherwise it starts
  the child and loads the atlas when the child ends (about 1.6 s, first start and after a Windows
  font update or a scale change); until then the icons are the monochrome glyphs.
- The cache lives in the app home (`cache/emoji/`), never in the repository or the installer: the
  pictures come from the user's own Windows font.

## 7. Software renderer: what works and what does not

`05-renderer-limits.slint`, `05b-renderer-details.slint`.

| Feature | Result |
|---|---|
| `border-radius`, per-corner radius, borders with alpha (1 / 2 / 6 px) | work, anti-aliased |
| `clip: true` + `border-radius` over child **rectangles** | works (rounded clip) |
| `clip: true` + `border-radius` over an **Image** | **not rounded**: the image keeps square corners. Use images that are already round, or a rounded container with rounded children |
| `@linear-gradient` (any angle, alpha stops), `@radial-gradient`, `@conic-gradient` | work |
| gradient background **with** `border-radius` | **corners stay square**. Gradients only on square shapes or as an image |
| `drop-shadow-*` | **not drawn at all** |
| glow / shadow from a nine-slice PNG (`Image` with `nine-slice`, `colorize` for the tint) | works and looks right: the focus glow of the text field and card shadows use this (`probes/assets/glow-white.png`, `shadow-black.png`) |
| glow from 4 stacked translucent rings | works, cheaper, a little banded |
| `opacity` on a group | works |
| `rotation-angle` on `Image` / `Text`, `transform-rotation` | **ignored**: nothing rotates |
| `Path` strokes (1 to 3 px, round caps) and fills (Material icon paths at 18 / 24 / 36) | work, anti-aliased |
| arc progress ring (`Path` + `ArcTo` with computed end point) | works: the spinner animates the arc, it cannot rotate |
| SVG image with gradients (`ui/logo.svg`) at 16 to 64 px | works |
| PNG scaling down (512 px logo to 16..64) and up, `image-rendering: pixelated` | work |
| `colorize` on PNG and SVG | works |
| text at 10 to 14 px, weights 400 to 700, dark and light | clear (greyscale anti-aliasing) |
| `letter-spacing`, italic | work; text stroke (outline) is not drawn |
| std-widgets fluent (Button, CheckBox, Switch, ComboBox with its popup, Slider, SpinBox, ProgressIndicator) | render; they follow `--color-scheme` |
| `ScrollView` with rounded rows | rows are cut straight at the viewport edge (fine) |
| translucent overlay (`#000000a0`) over content with a card on top | works |
| elide in the middle of a text | not available (`overflow: elide` cuts the end) |
| scale factors 1 / 1.25 / 1.5 / 1.75 / 2 (`06-scale.slint`) | lines, radii, rings and text stay sharp; 1 px lines keep their weight |

## 8. Rules that follow (for `ARCHITECTURE.md` section 12)

1. Renderer: `winit-software`. No `drop-shadow`, no rotation, no gradient on rounded shapes, no
   rounded clipping of images.
2. Direction: `Dir` global; `HBox` / `DText` for chrome and dialogs; mirrored `GridLayout` for list
   rows; computed `x` for absolute positions; `NameText` for long user text.
3. The presenter sends `xxxRtl` and `xxxHan` flags next to user text shown in one line (names,
   titles, paths), and wraps sizes / speeds in U+2066 ... U+2069 when the language is RTL.
4. Font: `Theme.font` by language (`zh` = `Microsoft YaHei UI`, others `Segoe UI`); symbol glyphs
   name `Segoe UI Symbol`; icons are `Path` or atlas images.
5. Colour emoji: atlas made by a child process, cached in the app home, loaded as images.
6. Glow and shadows: nine-slice PNGs tinted with `colorize`.
7. DTOs: no Slint enums (ints or strings), so the screenshot tool can feed any page.
