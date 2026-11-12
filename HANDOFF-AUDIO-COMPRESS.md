# Audio compress: hand-over (2026-11-11)

Owner's feature request, in his words (condensed): while downloading sound he picks the quality;
plus a "compress audio" button that works on every device; it opens a page that shows the sound's
size and the compression settings, and the new size changes live as he edits them. Reference app:
"M4A Audio Compressor" (Android): cards Details (Total length, Compressed length ≈), Mode
(Simple / Advanced), Simple = "Compression level" slider 1-6 ("A higher value means greater
compression"), Advanced = Bit rate, Sample rate, Channels, Profile. No output-folder card: the
file goes next to the original.

His answers:

- The compressed file is saved **under another name next to the original**; the original is kept.
- When a sound has compressed copies, a button shows it; the page lists every copy and each can
  be deleted.
- Video: no compress. He must download **audio only**, then compress.
- Tools (yt-dlp, ffmpeg, deno): download on first start or when they were deleted; updates stay
  per platform as they are now. Nothing to change there.

Decision already told to him: **no "Profile" (HE-AAC)**. The encoder (libfdk_aac) is non-free and
is not in our ffmpeg; the native `aac` encoder (AAC-LC) exists in every ffmpeg build we ship.
Output is always `.m4a`.

## State of the code (all uncommitted, branch `desktop-native`)

Shared logic (commonMain), written and the ffmpeg commands checked by hand on Windows:

- `sharedLogic/.../feature/audio_compress/AudioCompress.kt`: settings, levels 1-6
  (160/128/96/64/48/32 kbps, level 6 mono), Advanced lists, `encodeFor` (sample rate "Auto"
  lowers itself for <= 48 kbps), `estimateBytes`, `durationMs` (ffmpeg `-c copy -f null` with
  `-progress <file>`, reads `out_time_ms`), `compress` (writes `<name> (64k).part.m4a`, then
  renames; progress from the same progress file), `canCompress` (COMPLETED, not torrent, audio
  extension).
- `.../feature/audio_compress/AudioCopiesStore.kt`: `audio_copies.json` in the settings folder
  (task id -> paths). The task model and `tasks_cache.json` are NOT changed.
- `.../presentation/dashboard/DashboardAudioCompress.kt` + `DashboardContract.kt`
  (`AudioCompressUi`, `audioCompress`, `audioCopyCounts`, five `OnAudioCompress…` events) +
  dispatcher entry and `observeAudioCopies()` in `DashboardViewModel.kt`.
- 20 strings `audioCompress*` in `AppLocalization.kt` and all 15 providers.

UI:

- Android / JVM Compose: `sharedUI/.../ui/AudioCompressDialog.kt`, button in `TaskDetails.kt`,
  hook in `App.kt`. `:androidApp:assembleDebug` **builds**. Not run on the phone.
- Native desktop: `desktopNative/core/.../dto/AudioCompressDto.kt`,
  `presenter/AudioCompressPresenter.kt`, channel 27 `audioCompress`, actions `DetailsCompress`
  and `AudioCompress*` in `UiAction.kt` / `ActionDispatcher.kt`, `ui/pages/AudioCompress.slint`,
  button in `ui/pages/Details.slint`, `ui/app.slint`; `gen.py` was run. A Windows build
  (core + host) was started and its **result is unknown**: build again and fix what fails.
  Chips are used instead of dropdowns / a slider (no slider widget in the Slint kit).
- iOS: `iosApp/iosApp/SwiftUI/Tasks/AudioCompressSheet.swift` + the button in
  `TaskDetailsScreen.swift`. **Never compiled.**

## Left to do

1. Windows native: build, then a corehost test (`desktopNative/tests/corehost`, new
   `test_compress.py`: a finished local audio task, `DetailsCompress`, change settings, check the
   estimate changes, `AudioCompressStart`, the copy exists and is listed, `AudioCompressDelete`).
   A test sound: `ffmpeg -f lavfi -i "sine=frequency=440:duration=180" src.wav` (see
   `E:\tools\probes\audio`). Then a real look at the page (screenshot through `deskrun.exe`).
2. Android on the A56 (wireless adb, the owner must open Wireless debugging): install, download
   an audio-only YouTube sound or a local `.mp3` from the test server, compress in both modes,
   compare the estimate with the real size, delete a copy. Check that the Android ffmpeg has the
   `aac` encoder and accepts `-progress <file>` (temp dir = `FileSystem.SYSTEM_TEMPORARY_DIRECTORY`).
   On Android the output is written into `Download` next to the original: check it is allowed.
3. Linux in WSL (`~/fetchjet-native` only): build + the same corehost test.
4. macOS + iOS on the Mac: needs the code pushed first (**ask the owner before any commit or
   push**). The Mac session is the peer named "iOS checks for desktop-native (dabc01e)"; ask it to
   pull, build macOS native and iOS, and run the page on the simulator. iOS risks: the Swift names
   of the Kotlin API (`AudioCompress.shared.LEVELS`, `AudioCompressSettings(...)` init labels,
   `KotlinInt` in `audioCopyCounts`), and whether the in-process ffmpeg bridge accepts `-progress`.
5. JVM desktop compiles? (`./gradlew.bat :sharedLogic:compileKotlinJvm :desktopApp:compileKotlin`).
6. Open design points to raise with the owner only if they bite: the estimate is ~12-20 % high on
   very simple sound (fine, it is shown with "≈"); compress cannot be cancelled once started;
   "choose the quality while downloading" already exists (the quality list in the Add dialog).
7. Docs when it works: ROADMAP-1.4.0.md, CLAUDE.md, `desktopNative/README.md` if a rule changed,
   release notes line.

## Rules (the owner's, strict)

Read `CLAUDE.md` and the memory index first. Short Egyptian Arabic, masculine. No code comments.
No commit / push / branch change without his word. One heavy build at a time through
`bash desktopNative/tools/one-build.sh <command>`. Test downloads from the local test server
(`desktopNative/tools/testserver/servers.sh start`, `adb reverse tcp:18200 tcp:18200`); his
internet is expensive: a test once pulled ~100 MB by mistake, do not repeat it. Never run the
installed FetchJet, never install an MSI / MSIX on this PC, do not take over his mouse. When he
says something is broken, reproduce it his way.

## Update, later the same day

Done since the list above was written: Windows native builds; `desktopNative/tests/corehost/test_compress.py`
passes (40 checks) after the review fixes; `shots_compress.py` takes the page's screenshots
(`E:\tools\probes\ui\shots\compress-*.png`); the dialog scrolls when taller than the window; Android
debug and `:desktopApp:compileKotlin` build. Review fixes applied: temp progress file through
`PathResolver().getTempDirectory()` + `AppFileSystem`, source path built like "open file", delete
only the task's own copies, counts pruned, bitrate capped by the sample rate, iOS `relocateAppPath`.
Known and not fixed: `runLocalFfmpeg` stops after 30 minutes; a running compress cannot be cancelled.
Still not run: Android on the A56, Linux, macOS, iOS. Nothing is committed.

### UI the owner asked to improve ("هل في احسن من كدا")

He was offered these five changes and did not object; he then enabled design plugins and asked that
they be used properly to improve the app's UI/UX. Do them on all three UIs (Slint, Compose, SwiftUI):

1. The size is the main thing: show it large at the top, `10.0 MB → ≈ 243 KB`, with the saving
   (`−97%`) and a small bar.
2. Simple mode: a real slider with 6 stops like the reference app, ends labelled "higher quality" /
   "smaller file" (new strings in all 15 languages). Slint has no slider widget: build
   `ui/widgets/Slider.slint` from the drag code in `ui/widgets/Scroll.slint`, with RTL.
3. Advanced mode: three dropdowns in one row (Bit rate / Sample rate / Channels) instead of five
   chip rows. In Slint use `PickMenu` (`ui/widgets/Menu.slint`), each in its own component, never
   inside an `if` / `for`.
4. Compressed copies as cards: name, size, Open and Delete (red bin icon).
5. The Cancel button's outline is invisible in dark mode: make it read as a button.

Plugins enabled on his account (their skills load when a session starts): Design
(`design:design-critique`, `design:ux-copy`, `design:accessibility-review`), frontend-design,
UI Design Review (`ui-review`, `ui-polish`, `ui-compare`), SwiftUI App Design, Mobile App Design
(iOS). Use them: critique the real screenshots before and after each change, and show him the
screenshots (`SendUserFile`), never a description. There is no plugin for Compose or Slint.

### His latest words on the UI (same evening)

"المهم الشكل يطلع موحد طبعا وممكن تعمل تصاميم للصفحات ال شايف ممكن تتحسن اراجعها": the look must
be **unified** across the platforms, and he wants **designs (mockups) of the pages that can be
improved, to review before anything is built**. A first mockup page exists:
`E:\tools\probes\ui\mockups\fetchjet-ui-proposals.html` (four pages: compress page, Add dialog,
task details, download list, plus the shared rules). It was written but he has **not seen or
approved it yet**: show it to him (`SendUserFile`, render), improve it with the design plugins, and
build only what he approves. Current real screenshots to design from: `E:\tools\probes\ui\shots\`
(`home-dark.png`, `details-side-dark.png`, `add-direct-dark.png`, `compress-*.png`).
