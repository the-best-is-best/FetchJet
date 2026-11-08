# FetchJet 1.4.0: work list

1.3.0 (2026-11-08) and 1.3.1 (Store update check fix, store listings in 15 languages) are
released. This is the active work list now, on branch `release-v1.4.0`. Mark an item `[x]` when
the owner has tested it.

**State (2026-11-08):** list opened. Item 1 waits for the owner's Task Manager numbers.

## 1. Lower RAM use on desktop (medium)

Why: the owner compared with IDM (under 5 MB). FetchJet runs on the JVM with Compose / Skia, so
~100 MB is the floor and IDM's number cannot be matched, but the app holds much more than it needs
today.

- [x] Measured 2026-11-08 on Windows (NVIDIA GPU), dev run with `FETCHJET_NMT=true` and
  `jcmd <pid> VM.native_memory summary`: process 456 MB private, the same open or in the tray.
  JVM part 225 MB (G1 bookkeeping 61, heap 44 committed / ~30 used, metaspace + symbols 73,
  JIT code 17). The other ~230 MB is the NVIDIA DirectX 12 driver Skia draws with. libtorrent
  was not even loaded. Hiding to the tray frees nothing.
- [x] Software drawing (`SKIKO_RENDER_API=SOFTWARE`): 339 MB private (-118), outside-JVM part
  ~90 MB; idle CPU 0.8 % of one core; the window renders correctly.
- [x] Applied: Windows draws in software unless `skiko.renderApi` / `SKIKO_RENDER_API` is set
  (`main.kt`, first lines of `main`); JVM `-XX:+UseSerialGC -Xms32m -Xmx1g
  -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30` (`desktopApp/build.gradle.kts`). `-Xms32m`
  matters: without it SerialGC keeps the default 512 MB start heap (1/64 of RAM) committed
  forever. Measured with 302 tasks, hard scrolling + Settings and back, then 30 s idle:
  software 247 MB at start, 276 MB after; GPU (`SKIKO_RENDER_API=DIRECT3D`) 389 / 432 MB.
  CPU while scrolling: 89 % of one core on both. GC bookkeeping 61 -> 2 MB.
- [ ] Owner: use the dev build / next release normally and say if anything feels slow
  (resize, big lists, dialogs). Then measure the installed MSIX once.
- [x] Tried and dropped (2026-11-08, same 302-task list, idle 30 s, no mouse): AppCDS dynamic
  archive (`jcmd VM.cds dynamic_dump`, 68 MB file) 247 -> 237 MB; plus C1-only JIT
  (`-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m`) 227 MB. Metaspace 50 -> 3 MB, but
  Windows counts the mapped archive (80 MB) as private, so the gain is only 10-20 MB, for a
  per-build archive in CI that must match the bundled JVM and slower hot code. Not worth it.
  Dev hook kept: env `FETCHJET_JVM_ARGS` adds JVM flags to `:desktopApp:run`.
- [ ] Dropped for now: disposing the window in the tray (the GPU / UI memory did not change when
  hidden, the measured cost is the driver itself) and lazy libtorrent (not loaded at idle).

## 2. Android status bar in dark and light (small)

Why: the owner sees a problem with the status bar on Android (reported 2026-11-08). Not looked
at yet: reproduce first.

- [ ] On the A56, check the status bar (and the navigation bar) with Appearance = Light, Dark and
  System (device in light and in dark), and after switching the theme while the app is open:
  icon color readable against the top bar, no wrong background.
- [ ] Check the screens that are their own window: task details on the phone (a `Dialog`), the Add
  dialog, the pairing / send sheets. A dialog window does not inherit the activity's bar settings.
- [ ] Code today: `MainActivity.onCreate` calls `enableEdgeToEdge` with light bars before the
  theme is known, then `setContent` re-applies it from `App(onDarkModeChanged)` and sets
  `isAppearanceLightStatusBars`. Fix where the check shows the fault; test both themes again.

## 3. Carried over from 1.3.0 (checks)

- [x] Release notes in the app's language (1.3.0 item 6): an installed 1.3.0 in Arabic shows
  the 1.3.1 notes in Arabic in the update dialog (`release-notes-ar.md` on the release).
  Owner-tested 2026-11-08 with the iOS app under PlayCover.
- [ ] Browser extension inside the app: the in-app download of the signed .xpi from the published
  GitHub release (was not testable while 1.3.0 was a draft).
- [ ] Send to device (1.3.0 item 4) on a **real iPhone**: discovery, pairing, send both ways. If
  discovery fails there, "Connect by IP" and sends after pairing still work (unicast).
  Closer than the simulator: the IPA under PlayCover on the Mac (works, 2026-11-08), but it is
  not an iPhone's network sandbox.

## 4. External, waiting

- [ ] Edge Android listing: waiting for Microsoft's answer. Edge iOS: not tested
  (see `docs/edge-android-test-cases.docx`).
- [ ] Edge Add-ons from CI: the `EDGE_PRODUCT_ID` secret is missing, so CI cannot submit the Edge
  version (owner adds it).
