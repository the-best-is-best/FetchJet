# shot: off-screen screenshots of Slint components

`shot.exe` renders any `.slint` component to a PNG without opening a window. It loads the file with
the Slint interpreter (no C++ build per page), draws it with the same software renderer and the
same system fonts as the app (`SLINT_BACKEND=winit-software`), and can set properties, feed models
from JSON and send clicks, keys and text before the shot. Nothing appears on the screen and nothing
takes focus, so it is safe to run while the PC is in use. Several copies can run at the same time.

`deskrun.exe` runs a real program (the app itself) on a new hidden Windows desktop, then reports
its memory and its windows and can save a picture of each window. Use it for whole-app checks and
RAM numbers.

## Build (once)

```
desktopNative\tools\shot\build.cmd
```

Needs the Slint C++ package in `E:/tools/slint` (`SLINT_DIR`), CMake and Visual Studio 2026.
Result: `build/Release/shot.exe`, `deskrun.exe` (and a copy of `slint_cpp.dll`). `build.cmd <dir>`
builds into another folder. Run the build at low priority while the owner works:
`cmd /c start /low /b /wait build.cmd`.

## Use

```
shot <file.slint> [Component] --out x.png [options]
```

| Option | Meaning |
|---|---|
| `--width N --height N` | logical size; default = the component's preferred size |
| `--scale F` | scale factor (1.25 = 125%); the PNG is `width*F x height*F` pixels |
| `--dark` / `--light` | sets the bool `Theme.dark` (see `--theme-global`) and the std-widgets colour scheme |
| `--theme-global G.prop` | the bool that `--dark` / `--light` set (default `Theme.dark`) |
| `--color-scheme dark\|light` | only the std-widgets `Palette` scheme |
| `--set name=value` | set a property of the component; `Global.name=value` for an exported global |
| `--json file.json` | properties, structs, arrays and globals from a JSON file (below) |
| `--export Name` | also export a global that the file only imports (so `--set` / `--json` can reach it) |
| `--style S` | std-widgets style (default `fluent`) |
| `-I dir` | extra include path |
| `--hover x,y` `--click x,y` `--press x,y` `--release x,y` `--leave` | pointer events at logical coordinates, in the order given |
| `--scroll x,y,dx,dy` | wheel event |
| `--type TEXT` `--key NAME` | text and key events to the focused element (`--key Left`, `Return`, `Escape`, `Tab`, `Backspace`, `Delete`, ...) |
| `--wait MS` | advance timers and animations by MS |
| `--crop x,y,w,h` | keep a logical rectangle |
| `--zoom N` | enlarge the result N times (nearest neighbour), to look at details |
| `--wrap [--bg #rrggbb] [--font F] [--bind 'a: 1; b: "x";']` | put a component that is not a `Window` inside a window |
| `--emoji-callback G.name` | implement `pure callback name(string, int) -> image` of global `G` with DirectWrite colour emoji |
| `--emoji-family F` | the emoji font (default `Segoe UI Emoji`) |
| `--tail TEXT` | Slint source appended to the file before compiling |
| `--list` | print the public properties, callbacks and globals, then exit |
| `--stats` | print timings and memory of the run |
| `--quiet` | no summary line |

Exit code 0 = PNG written. Compile errors of the `.slint` file are printed with file and line.

### JSON

Top-level keys are property names of the component. A key that is the name of an exported global
holds an object with that global's properties. Names may be written `camelCase`, `snake_case` or
`kebab-case`.

```json
{
  "down-speed": "12.4 MB/s",
  "filter": 0,
  "limited": false,
  "tasks": [ { "id": 1, "name": "ubuntu.iso", "progress": 0.42 } ],
  "Theme": { "dark": true },
  "Emoji": { "turtle": "@emoji:🐢@18" }
}
```

- object = struct, array = model, number / bool / string as they are;
- a top-level property of type `brush` / `color` takes `"#rrggbb"`; inside a struct write
  `"@color:#rrggbb"` (also `#rgb`, `#rrggbbaa`);
- a top-level `image` property takes a path (relative to the JSON file); inside a struct write
  `"@image:path.png"`;
- `"@emoji:TEXT@PX"` = that emoji drawn in colour into a PX x PX image (`@PXt` = as text, not
  fitted to a square);
- enum-typed properties cannot be set (the C++ interpreter has no enum values): DTOs use `int`
  or `string` instead of Slint enums.

### Examples (tested)

The prototype page with its example state, dark, 1200 x 760:

```
cd desktopNative\tools\shot
build\Release\shot.exe ..\..\ui\app.slint AppWindow --json examples\app-state.json --width 1200 --height 760 --dark --out out\app-dark.png
```

The same in light, at 150%, with the Add dialog open, and a zoomed crop of the first rows:

```
build\Release\shot.exe ..\..\ui\app.slint AppWindow --json examples\app-state.json --width 1200 --height 760 --light --scale 1.5 --out out\app-light-150.png
build\Release\shot.exe ..\..\ui\app.slint AppWindow --json examples\app-state.json --width 1200 --height 760 --dark --set show-add=true --out out\app-add.png
build\Release\shot.exe ..\..\ui\app.slint AppWindow --json examples\app-state.json --width 1200 --height 760 --dark --crop 232,150,560,150 --zoom 3 --out out\app-zoom.png
```

A widget that is not a window, with Arabic direction (Git Bash quoting):

```
build/Release/shot.exe probes/01c-rtl-elide-workaround.slint NameText --wrap --bg "#0F1728" --bind 'text: "تقرير المبيعات السنوي.pdf"; text-rtl: true;' --set Dir.rtl=true --width 260 --height 30 --out out/name-rtl.png
```

What a component accepts:

```
build\Release\shot.exe ..\..\ui\app.slint AppWindow --list
```

Typing into a field, then looking at the caret:

```
build\Release\shot.exe probes\01b-bidi-details.slint BidiDetails --click 200,720 --type "ملف abc 12 جديد" --key Left --out out\typed.png
```

All rendering probes: `bash probes/run-all.sh` (writes `probes/out/*.png`).

One run of the whole prototype page takes about 0.2 s (compile 95 ms, first frame 30 ms).

## deskrun

```
deskrun [--timeout MS] [--after MS [--info] [--capture file.bmp]] -- program args...
```

Starts the program on a new hidden desktop: its windows exist but are never shown and cannot take
focus. `--after MS`: after MS milliseconds print private bytes, working set and every window
(class, title, rectangle), save a BMP of each window with `--capture`, then stop the program.
`--timeout MS` stops a program that does not end.

```
set SLINT_BACKEND=winit-software
build\Release\deskrun.exe --timeout 9000 --after 4000 --info --capture E:\tools\probes\cap.bmp -- E:\tbib\FetchJet\desktopNative\build\Release\fetchjet_native.exe
```

The tray icon cannot be created on a hidden desktop (`Shell_NotifyIconW(NIM_ADD) failed` is
expected there).

## Files

- `shot.cpp`: the tool (arguments, JSON to interpreter values, events, PNG output).
- `headless.h`: the off-screen Slint platform (`slint::platform::SoftwareRenderer` into a pixel
  buffer) and the PNG writer (WIC).
- `emoji_d2d.h`: colour emoji through DirectWrite + Direct2D into `slint::Image`
  (`fj::EmojiRasterizer`); the app's host reuses it.
- `json.h`: a small JSON reader.
- `deskrun.cpp`: the hidden-desktop runner.
- `probes/`: the rendering probes of `docs/port/13-slint-rendering.md` with their PNGs in
  `probes/out/`.
