# FetchJet app icon

The jet brings the file, the screen plays it. These SVGs are the source of every app icon in the repo.
All of them except the tray use a 108 x 108 view box (the Android adaptive icon grid).

| File                       | What it is                                                                                                   | Used for                                                                                       |
|----------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `fetchjet-icon.svg`        | Full icon: dark radial background with rounded corners (rx 24) plus the artwork with its glow                | Windows .ico, MSIX logos, extension icons, legacy Android PNGs, macOS .icns, in-app logos, docs |
| `fetchjet-foreground.svg`  | Artwork only on transparent, scaled to 66/108 around the centre (the Android adaptive safe zone)              | Android adaptive foreground; the generator scales it back up for iOS, round and dark variants   |
| `fetchjet-background.svg`  | The dark radial background, full square, no rounding                                                          | Android adaptive background, iOS opaque icons, Android round icon                               |
| `fetchjet-mono.svg`        | One-colour artwork (white) at full scale: screen frame, play button, jet                                     | Android 13 themed icon (the generator applies the safe-zone scale)                               |
| `fetchjet-tray.svg`        | Simplified jet + screen for 16 to 32 px: no glow, no background, thicker frame, dark outline around the jet   | macOS menu bar icon, Safari toolbar icons                                                        |

## Regenerating

```
python -I scripts/icons/build-icons.py --sheet <path>/contact-sheet.png
```

Windows only: it renders with headless Microsoft Edge (`FETCHJET_EDGE` overrides the path) and writes every
platform file in place (Android, iOS, Windows, MSIX, installer art, macOS, Linux hicolor, extension, docs, store
images). Work files go to `build/icon-work/`. `--skip-store` leaves the store / listing images alone.

Notes:

- Android vector drawables have no filters, so the glow is replaced by a 10% larger copy of the jet at 35% opacity.
- iOS: `AppIcon.png` and `icon_*` are opaque squares (iOS rounds them), `AppIcon-Dark.png` is the artwork on
  transparent, `AppIcon-Tinted.png` the same in greyscale.
- macOS: the icon body is 824 x 824 inside the 1024 canvas with a soft shadow (Apple's grid).
- Installer bitmaps and store images keep their text: only the logo area is redrawn (background filled from its
  border, then the new icon on top), so a second run gives the same result.
