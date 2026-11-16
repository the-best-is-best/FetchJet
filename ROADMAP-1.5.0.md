# FetchJet 1.5.0: work list

Not started. Written 2026-11-14 after a look at Mister Download Manager 0.5.2 (Rust + egui, GPL-3.0,
by Ahmed Nefzaoui; unpacked and read, its CLI run on a 64 MB file; its window and its video part were not
tested). The list below is what is worth having here. Each item says what the other app does, so we know
what "done" has to cover. Nothing is built yet.
Owner, 2026-11-16: 1.5.0 may be split into two releases; the Order section below is the proposal. Item 14 (a
smaller Android APK) has the highest priority and its steps are the natural place to cut.
Legend: [x] done and tested as said, [ ] not done or not tested yet.

## Order (2026-11-16)

Two releases if the split happens. The APK size is the highest priority, owner's decision 2026-11-16. The rest is
ordered by value for users, cost and risk. Item numbers are the headings below.

Release A:
1. [x] Item 14, step 0: remove the universal APK from the release (decided, small, first).
2. [x] Item 14, steps 1 and 2: trimmed Python, our own ffmpeg. Highest priority, the biggest visible gain (done and tested on the A56, CI not run yet).
3. Item 15, security scan: starts with item 14 and runs again before the release. It checks items 1 and 2 as they are built.
4. Item 1, Automation API and `status` / `pause` / `start` / `remove` / `delete`. Small and low risk, and tests and
   measurements can drive the real app without the mouse.
5. Item 6, small items left from 1.4.1 (the licence check and the SignPath application are cheap and block nothing).
6. Item 2 phase 1, extract ZIP / TAR / GZ / XZ on every platform.
7. Item 7, context menu on a download row (owner's request, three UIs).
8. Item 8, sort into folders: rules by site (owner's request).

Release B:
9. [x] Item 14 step 3, Python inside the app (`useLegacyPackaging` false). Done early, with Release A, tested on the A56.
10. Item 3, own DNS and network adapter.
11. Item 2 phase 2, 7Z / RAR, passwords, split archives, folder choices.
12. Item 12, Facebook stories (first a test, then a decision).
13. Item 13, WhatsApp status and `blob:` videos (first a test, then a decision).
14. Item 11, portable mode on Windows.
15. Item 9, scan a file after the download.
16. Item 5, wake the computer on macOS / Linux / Android.
17. Item 10, monthly downloads report. Cosmetic.
18. Item 4, server country and ping. Cosmetic, last, maybe never.

## 1. Automation API and a fuller command line (owner's order 2026-11-14)

What the other app does: a local API (off by default, turned on with a command) and CLI commands `status`
(JSON), `pause`, `start`, `remove`, `delete` by download id, plus `get` / `resume` without the window and
a JSON progress stream for programs. It also ships an MCP server so an AI agent can drive the app, and
`pair` (a code and a QR for a phone or a browser).
FetchJet today: `/d /p /f /a /n /s` on the native desktop command line (1.4.1) and the browser bridge on
`127.0.0.1:28450` with a pairing token (`/add` and `/links` already queue downloads).

Decided 2026-11-14 (owner): only `status` plus pause / start / remove / delete from the command line. No MCP
server (it would widen what any local program or AI can do to the app, and nobody asked for it); no `pair`
QR for now. It goes first because it also lets a test drive and read the real app without the mouse (the
mouse takes the owner's PC and clicked the wrong window on 2026-11-14).

- [x] CLI commands that talk to the running app: `FetchJet status [id]` (JSON), `pause`, `start`, `remove`, `delete`
  by download id (several ids with a comma or a space), and `FetchJet automation on|off|` (no word = show). Off by default
  (`automation` in `bridge.json`), token protected. Written once in `feature/automation/AutomationApi.kt`
  (`handle(...)` does the checks, `status`, `act`): HTTP `/api/status`, `/api/pause|start|remove|delete?id=`,
  `/api/automation?on=1|0` in the desktop `BridgeServer.kt` and the Android `ExtensionBridgeServer.kt`, and on iOS in
  `IosAutomationServer.kt`. A request with an `Origin` header is refused (a web page or the browser extension, even paired),
  the `X-FetchJet` header and the exact token are needed, `pause` / `start` on a download where they make no sense answer 409.
  The desktop CLI prints the JSON and exits with 0 or 1 (`LaunchResult.output` / `code`, `os::print_out` in the three
  hosts; on Windows it attaches to the parent console). Tested: `tests/corehost/test_automation.py` 40 checks (Windows),
  the real `FetchJet.exe status` / `pause` with no app running (prints the error, exit 1, no window), the A56 over
  `adb forward` (pair, off 403, on, status, pause, start, delete, 409 on a finished one, off).
  Not run: macOS / Linux hosts (`print_out` is a plain `fwrite`), the old JVM desktop app (no `/api`), iOS (below).
- [x] (status, pause, start, remove, delete over the API; memory is read from outside with the OS) Enough for a test on the real app with no mouse: add a download (bridge `/add`), read its state, speed
  and size as JSON, pause / start it, and read the app's memory from outside. Measure idle and during a
  download on the real native app this way, the same way every time.
- [ ] `get` / `resume` without the window and a JSON progress stream: not decided, ask before building.
- [ ] Tests in corehost (off by default answers 401, token required, ids that do not exist, delete keeps or
  removes the file as asked).

## 2. Extract archives after a download

What Mister Download Manager does (read from its texts, not run): "Extract archives when their download
completes" in Options, "Extract when done" in its Link grabber, and Extract in the right-click menu.
ZIP, TAR, GZ and XZ are done by the app itself; 7Z and RAR by 7-Zip when it is installed (not bundled,
because of the unRAR licence). A split archive (`.part1.rar`, `.7z.001`) waits until every volume is in.
A password-protected archive asks for the password. The place is one of: a folder named after the
archive when it holds several things, always a folder, or straight into the archive's folder. A file that
already exists is kept and the new one is numbered. Option to move the archive to the Recycle Bin after.
Entries that leave the folder (links, `..` names) are skipped, and there is a limit on the size and the
number of files.

Decided 2026-11-14 (owner): every platform extracts with the app's own code, it does not depend on the
phone. Two phases.

Phase 1, all platforms (Windows, macOS, Linux, Android, iOS):
- [ ] Setting "Extract archives when their download completes" (off by default).
- [ ] ZIP, TAR, GZ, XZ in our own code (commonMain), with the zip-slip guard, a size and file-count limit and
  numbering instead of overwriting. `DesktopZip.kt` (native desktop) only reads zips for the tools
  download; check whether it can be reused before writing a second reader. XZ needs an LZMA library on
  Android and iOS.

Phase 2:
- [ ] 7Z on Android and iOS with a library, if one of a sensible size is found. Android already ships
  `commons-compress`, but its optional `xz` classes are not packed (the release build uses `-dontwarn`, see
  CLAUDE.md), so 7Z there needs that dependency added and tested. iOS needs a C library through cinterop
  or Swift. Not verified yet.
- [ ] 7Z and RAR on desktop through an installed 7-Zip; a clear message when it is missing.
- RAR is never bundled (the unRAR licence does not allow it inside a GPL-3.0 program) and not on Android or
  iOS (no way to run an outside program there).
- [ ] Password-protected archives (ZIP AES at least), asked once per archive, never saved.
- [ ] Split archives wait for all the volumes.
- [ ] Where the files go: a folder named after the archive / always a folder / next to the archive.
- [ ] Extract from the right-click menu and from the details, not only after a download.
- [ ] "Move the archive to the Recycle Bin after" (Trash on macOS / Linux; decide for Android and iOS).
- [ ] Texts in the 15 languages; tests in corehost (a good zip, a zip-slip zip, a bomb, a wrong password,
  a name that already exists).

## 3. Own DNS and a network adapter

What the other app does: `--dns` takes "system", a named service ("cloudflare-https", "quad9-tls",
"google-plain", "adguard-https", "nextdns-tls"), DNS server addresses, or a DNS-over-HTTPS address, with no
fall back to the system DNS. `--adapter` sends every connection through one network adapter (by name or
address) and fails if it is not there. Useful where a site is blocked by DNS and with a VPN adapter.

- [ ] Settings > Network: DNS = System / a named service / custom (DoH address or server list).
  Decide how the lookup is done on each platform (our HTTP clients do the lookup themselves: WinHTTP,
  OkHttp, Darwin; the torrent engine and yt-dlp have their own).
- [ ] "Use this network adapter" (desktop, maybe Android); fail clearly when it is missing.
- [ ] Tests with a local DNS / DoH stub; check that it does not leak to the system DNS.

## 4. Server country and ping in the Add dialog

What the other app does: the Add dialog shows the server's country (flag) and its answer time ("United
States, about 95 ms"), found with a country database that stays on the computer.

- [ ] Optional and cosmetic. Needs an IP-to-country database (DB-IP Lite is CC BY 4.0, attribution
  required) and costs size; decide whether it is worth it before building.

## 5. Wake the computer for a scheduled download on more systems

Today only Windows has it: Settings > Downloads > "wake for schedule" arms a Waitable Timer one minute before
the first scheduled start (`WakeTimer.mingw.kt`, `test_wake.py`). Every other `WakeTimer.isSupported` is false
and the row is hidden.

- [ ] macOS: `pmset schedule wake "<time>"` (needs admin: a one-time helper or a password prompt); also
  `pmset repeat`. Decide the permission flow before building.
- [ ] Linux: `rtcwake` or a systemd timer with `WakeSystem=true` (needs root; not every BIOS wakes from sleep).
- [ ] Android: `AlarmManager.setExactAndAllowWhileIdle` through WorkManager; needs the exact-alarm permission.
  Less useful (a phone does not sleep like a laptop).
- [ ] iOS: no API wakes an app at a chosen time (background tasks are the system's choice). Not planned.
- [ ] Tests: the same `test_wake.py` checks on each system; a real sleep and wake only by hand.
- [ ] Windows: a real sleep and wake (the core test only checks that the timer is armed, 11 checks 2026-11-14).

## 6. Small items left from 1.4.1

- [x] `AudioCompress.slint`: `viewport-width` / `viewport-height` replaced by `content-width` / `content-height` (the Slint deprecation warning). Built on Windows, no warning left; the window was not opened.
- [ ] Windows MSI signing: apply to SignPath Foundation (free for open source). Needs a "Code signing policy" section and a privacy statement on the download page, 2FA on GitHub and SignPath, and probably a GitHub-hosted runner for the MSI build (the terms do not say which runner is accepted). The reputation check may refuse a project with few stars and downloads.
- [ ] iOS CI log: about 1850 warnings, all from libtorrent compiled in the build (none from our Swift or Kotlin). Optional: silence them in `scripts/build-libtorrent-ios.command`; the one warning that is ours is Xcode's "Building targets in manual order".
- [ ] GitHub shows the licence as `NOASSERTION`: check that `LICENSE` is the unmodified GPL-3.0 text (SignPath needs an OSI licence it can recognise).

## 7. Context menu on a download row (owner's request, 2026-11-14)

Right-click on a download row, finished or not, opens a proper menu with the actions for that download
(open, open folder, pause / resume, send to device, copy link, details, delete and so on). On phones and
tablets a long press opens the same menu instead of the details.

- [ ] Desktop (Slint): a right-click menu on every row, with the actions that fit the state of the download.
  Nothing like it exists in `desktopNative/ui` today.
- [ ] Compose (Android): a long press opens the menu. A long press now starts the multi-select; decide where
  selection moves (a "Select" entry in the menu, or the checkbox).
- [ ] SwiftUI (iOS / iPad): a long press shows the same menu (`contextMenu`).
- [ ] One list of actions in shared logic, so the three UIs show the same entries; texts in the 15 languages;
  tests in corehost for the entries per state.

## 8. Sort into folders: rules by site (owner's request, 2026-11-14)

Today "Sort into folders" only sorts by kind of file (Video, Audio, Documents ...), and a folder the user
picked for a kind is remembered. The addition is rules by site: "everything from this site goes to this
folder", whatever the file type is. Nothing like it exists today (`HostLimits.kt` only limits connections
per host).

- [ ] A list of rules in Settings: site (host and its sub-domains) -> folder; edit, delete, order.
- [ ] A rule wins over the kind-of-file folder; no rule keeps today's behaviour.
- [ ] Add dialog: the folder field shows the rule's folder and says it came from a rule.
- [ ] Optional on the same rule: a speed limit or a connection count for that site, and a command to run when
  the download finishes (decide after the first part is used).
- [ ] Same on the three UIs (Slint, Compose, SwiftUI); texts in the 15 languages; tests in corehost
  (rule hit, sub-domain, no rule, a rule beside a remembered kind folder).

## 9. Scan a file after the download (idea, 2026-11-14)

- [ ] Setting "Scan finished files" (off by default): run the system's own scanner on the file when the
  download completes (Windows Defender on Windows; decide for macOS and Linux). A file the scanner flags is
  marked in the list with a clear message and is not opened.
- [ ] Decide what happens when no scanner is found (say so once, do nothing else).
- [ ] Texts in the 15 languages; tests in corehost with a fake scanner command.

## 10. Monthly downloads report (idea, 2026-11-14)

- [ ] A page that shows, for a month: how many files, how many GB, and which sites they came from. Read only
  from the history the app already keeps; nothing leaves the device.
- [ ] Cosmetic. Decide whether it is worth a page before building.

## 11. Portable mode on Windows (idea, 2026-11-14)

- [ ] Run from a folder or a USB stick with no install, keeping its data next to the program. The native app
  already runs on another data folder with `--home <folder>`; the work is to choose that folder by default
  when a marker file sits next to `FetchJet.exe`.
- [ ] No start at login, no registry entries, no tray autostart in this mode; the in-app update must not
  assume an installed copy.
- [ ] Check the first-run steps and the media tools download land inside the folder.

## 12. Facebook stories (owner's question, 2026-11-14)

A link like `facebook.com/stories/<id>/<token>/` is not downloaded: the `FacebookIE` pattern of yt-dlp has no
`/stories/` path (read from its source, `facebook.py`; the link itself was not tried on the network), so the
app answers "unsupported". A story also needs a login and lasts 24 hours.

- [ ] Try the browser extension on a real story (it may not see the viewer's video); say what it does.
- [ ] If it does not work: decide whether to read stories in the app (Facebook's own GraphQL + the user's
  cookies; big and it breaks often) or to say clearly in the Add dialog that stories are not supported.

## 13. WhatsApp status and other `blob:` videos (owner's question, 2026-11-14)

A WhatsApp status has no link. On WhatsApp Web (`web.whatsapp.com`) the browser decrypts it and plays it as a
`blob:` URL inside the page. The extension skips `blob:` on purpose (`content.js` line 90, `background.js` line
595), because the app is outside the browser and cannot open a blob. On Android the app could read WhatsApp's
`.Statuses` folder instead (a file read, not a download; needs a file permission; the path changes between
WhatsApp versions; nothing equal on iOS). Neither was tried.

- [ ] Try on WhatsApp Web: can the content script `fetch(blobUrl)` and hand the bytes to the app (or save them
  from the browser)? Say what it does before building anything.
- [ ] If it works, it applies to any site that plays video from a blob, not only WhatsApp; decide how the app
  receives bytes (the bridge takes links, not files).
- [ ] Android `.Statuses` reader: outside the plan unless the owner asks.

## 14. A smaller Android APK, and no extracted binaries (highest priority, owner's order 2026-11-16)

Measured 2026-11-16 on the 1.4.1 release `androidApp-arm64-v8a-release.apk` (82.9 MB; the app's own code,
`classes.dex`, is only 3.2 MB). The rest is prebuilt bundles that are already zips, so APK compression does
not help:

- `libffmpeg.zip.so` 35.4 MB (184 files, raw 78 MB: libavcodec 6.6, libaom 2.8, SvtAv1Enc 2.0, libavfilter 1.9,
  libx265 1.0, librav1e 0.9, libopenmpt 0.7, gnutls, gio ...) from `youtubedl-android:ffmpeg`.
- `libpython.zip.so` 32.5 MB (Python 3.14, curl_cffi 7.3, Cryptodome 1.6, libcrypto 2.4, tk / tcl / libX11 4.0,
  pip / ensurepip 3.5, idlelib 0.5, two copies of sqlite and of libc++_shared ~2.3) from
  `ytdlnis_packages.python`.
- `libtorrent4j.so` 6.2 MB, `assets/ytdlp` 2.9 MB, `libbash.zip.so` 1.5 MB.
- The universal APK is 281.9 MB (all four ABIs); the per-ABI APKs are 67 to 83 MB.

Why `useLegacyPackaging = true` (`androidApp/build.gradle.kts`): `PackageBase.kt` reads `libpython.zip.so` and
`libffmpeg.zip.so` from `applicationInfo.nativeLibraryDir` and unpacks them at run time, and Android 10+
cannot run a downloaded binary, so the files must exist on disk. Its cost: the APK holds compressed libraries
and the install takes more space. Setting it to `false` while the app still runs these as programs would break
them. The way out is the same as on iOS: libraries of our own, loaded inside the app, nothing to run.

Steps, each tested on the A56 before the next (the likely split point between two releases is after step 2):

- [x] Step 1, trim what we ship of Python (about 8 MB, low risk): remove tkinter, tcl / tk, libX11,
  pip / ensurepip, idlelib, lib2to3, pydoc_data; check the two copies of sqlite and of libc++_shared before
  removing one. Done when the build repacks the zip and yt-dlp, curl_cffi (the 9gag test in CLAUDE.md) and
  YouTube / SoundCloud / HLS still work.
- [x] Step 2, our own ffmpeg (about 25 MB, medium risk): build ffmpeg with the NDK with only what the app uses
  (stream copy and merge, the audio encoders of Convert and Compress audio, subtitles embed, the
  re-encode fallback), no AV1 / x265 / openmpt, as a library called from the app (JNI), not a program.
  List every ffmpeg call first (`runLocalFfmpeg`, audio convert / compress, subtitles, clip cut, HLS) so no
  encoder is missing; a missing encoder breaks a feature.
  - Step 2 inventory (read from the code 2026-11-16, not built yet): the app's own calls (`runLocalFfmpeg`, `MediaOwnEngine`,
    `MediaAudioConvert`, `AudioCompress`, `HlsDownloader`, clip cut, subtitles embed) use only: stream copy (`-c copy`,
    `-bsf:a`, `-movflags +faststart`, `-ss` / `-to`, `-map`, `-map_metadata`), audio decode of anything yt-dlp serves
    (aac, mp3, opus, vorbis, flac, pcm, alac), audio encode `aac` (native), `libmp3lame`, `libopus`, `libvorbis`
    (or native `vorbis`), `flac`, `pcm_s16le`, subtitle encode `mov_text` (+ srt / webvtt / ass read), muxers mp4, ipod (m4a),
    matroska / webm, ogg, opus, mp3, flac, wav, adts, srt; demuxers mov, matroska, ogg, mp3, flac, wav, aac, srt,
    webvtt, ass. No video re-encode anywhere (the fallback is `-c:v copy -c:a aac`). yt-dlp also calls ffmpeg and ffprobe
    for its post-processors (merge, extract audio, embed thumbnail = mjpeg / png / webp decode + mjpeg / png encode,
    embed subs, metadata, chapters / SponsorBlock = concat), so the build must include `ffprobe`. Plan: `ffmpeg` and
    `ffprobe` as static executables built with `--disable-everything` + the list above, delivered as `libffmpeg.so` /
    `libffprobe.so` in `jniLibs` (no zip, no `libffmpeg.zip.so`), and the `youtubedl-android:ffmpeg` dependency removed.
    Expected: ~4 MB per program per ABI uncompressed, ~2 MB each in the APK, so about 31 MB saved on arm64.
    Open: where to build it (Linux NDK in WSL is a ~650 MB download; the Windows NDK has no Linux toolchain; or a CI job).
  - Step 2 and 3 done in code 2026-11-16 (not committed, not in CI yet). Design: nothing is run from `nativeLibraryDir` any
    more, so `useLegacyPackaging` is gone (`extractNativeLibs=false`, checked on the A56: `lib/arm64` is empty on disk).
    `androidApp/src/main/cpp/fjexec.c` (`libfjexec.so`): `fjexec_spawn` forks, redirects the three fds, `dlopen`s the
    target library and calls its `main` (`Py_BytesMain` for Python), then `_exit`. Kotlin: `NativeExec` / `NativeProcess`
    (a `Process`), used by `RuntimeManager.launch` for python / ffmpeg / ffprobe / qjs; `PackageBase` unpacks the Python zip
    straight from the APK entry and creates placeholder files in `no_backup/ytdlnis/bin` so yt-dlp finds a path.
    `assets/fj_python/sitecustomize.py` makes `subprocess.Popen` start ffmpeg / ffprobe / qjs through `fjexec_spawn`
    (yt-dlp reports `ffmpeg 7.1.1`, `ffprobe 7.1.1` and runs the QuickJS challenge solver). Python's libs that have no
    `DT_SONAME` (`libandroid-support`, `libandroid-posix-semaphore`, `libsqlite3`) are lifted out of the zip into jniLibs
    by `slimPython`, because bionic only matches NEEDED names against a soname or the APK lib dir
    (`android_update_LD_LIBRARY_PATH` is not available to apps). ffmpeg / ffprobe are our own static builds
    (`scripts/android-ffmpeg/`, NDK on Windows + MSYS2, lame + opus, `--disable-everything`, ~4.5 MB each, `main` exported),
    kept in `androidApp/ffmpeg-libs/<abi>/` (gitignored, 4 ABIs); the `youtubedl-android:ffmpeg` and `ytdlnis_packages.bash`
    dependencies are removed (bash was unused).
  - Sizes (release, debug-signed): arm64-v8a 53.2 MB, armeabi-v7a 41.5, x86 47.4, x86_64 48.2. With
    `useLegacyPackaging = true` and our ffmpeg the arm64 file was 39.5 MB (libs compressed in the APK but extracted on the
    device); without it the libs are stored raw (page aligned), so the file is bigger but nothing is copied at install.
  - Tested on the A56 (arm64 release): 9gag (curl_cffi), YouTube inspect + download with the local merge, SoundCloud HLS,
    Compress audio (1.6 MB, aac encoder of our ffmpeg). Emulator x86_64 (debug): YouTube inspect 15 formats with the
    quickjs solver, download + merge. Direct 8 MB.bin earlier. Not tested: cookies, audio Convert to mp3 / opus / ogg
    through the app (the encoders were run by hand on the A56), subtitles embed, clip cut, Python 3.14 free-threading
    (not used), 16 KB page-size devices (our libs link with 16 KB alignment; the Python zip content does not).
  - More A56 tests (arm64 release, 2026-11-16): Convert to mp3 231 KB, opus 349 KB, ogg 178 KB, flac 1.8 MB, wav 3.2 MB
    from the Add dialog; subtitles embedded (`mov_text`, ffprobe shows it); 19 ffmpeg command checks by hand (mka, mov_text,
    srt to vtt, jpg / png / scale, cover art into mp3 and m4a, concat, ffprobe). The first ffmpeg build lacked the `movtext`
    encoder and the `matroska_audio` muxer (found by these checks, fixed in `ffmpeg-config.sh`). Cookies (A56, 2026-11-16): a
    test `cookies.txt` in the app's external files folder was read by yt-dlp in the forked Python (YouTube inspect listed the
    qualities) and written back with the new YouTube cookies and the two test ones kept; the test file was removed after.
  - CI: `build.yml` Android job caches `androidApp/ffmpeg-libs` (key = hash of `scripts/android-ffmpeg/`) and builds it with
    `build-linux.sh` (Linux and macOS runners) or `build-all.sh` (Windows runner, needs MSYS2 and the NDK path in the
    script). `build-linux.sh arm64-v8a` was run in WSL with the Linux NDK r28c and its binaries passed the 19 checks;
    `RUNNER_ANDROID` is `win-host` (the owner's PC), so the real CI path is `build-all.sh` (MSYS2), the one that built the local
    APKs. `build-linux.sh` is the fallback for `ubuntu-latest`: it was also run in WSL for all four ABIs and the libs match the
    Windows ones (same encoder / muxer / demuxer lists, sizes within 0.1%; the x86_64 one was run on the emulator). macOS is not
    used by the Android job. The workflow was not run on GitHub yet.
  - Open: CI has to build the ffmpeg libs (they are not in git, `preBuild` fails without them); CMake builds `libfjexec`
    in the Android job (NDK 28.2.13676358).
- [x] Step 3, Python inside the app, like iOS (high risk, needs its own release or a long beta): `libpython`
  loaded in the process, the stdlib in a zip, extension modules (curl_cffi, Cryptodome) as real `.so` files in
  `jniLibs`. Today `RuntimeManager` runs yt-dlp as a process; read it before planning (not verified here).
  After steps 2 and 3 nothing is run from `nativeLibraryDir`, so `useLegacyPackaging` can be `false`
  (check 16 KB page alignment of every `.so` at the same time).
- [x] Step 0, first (owner's decision 2026-11-16): remove the 282 MB universal APK from the release (24 downloads
  of 1.4.1 against 148 for arm64). Files that mention it: `build.yml` (the upload filter, line 247 to 250),
  `androidApp/build.gradle.kts` (`isUniversalApk = true`), `README.md` and `docs/index.html` ("take universal if
  unsure": say arm64-v8a instead and keep the list of the other ABIs), and `PlatformUpdater.android.kt`, which
  falls back to the universal file when no ABI file matches (the in-app update of an app that was installed from
  the universal APK keeps working through its own ABI file; check an x86 emulator install). The `.aab` is separate.
- Step 0 and step 1 done in code 2026-11-16 (not committed, A56 tests not run yet):
  - Step 0: `isUniversalApk = false`, the upload filter has no `*universal*`, README and `docs/index.html` say
    arm64-v8a, `PlatformUpdater.android.kt` has no universal fallback (an app installed from an old universal
    APK still gets its own ABI file through `Build.SUPPORTED_ABIS`; a 32-bit x86 device matches the `x86_64`
    file name first, no x86 APK is uploaded anyway). The `ndk { abiFilters }` block is now only for the bundle
    run: with `isUniversalApk = false` AGP refuses abiFilters together with ABI splits.
  - Step 1: task `slimPython` in `androidApp/build.gradle.kts` (commons-compress in `buildscript`) opens the
    `libpython.zip.so` of each ABI inside the `ytdlnis_packages.python` aar, copies every entry raw (symlinks
    and modes kept) except tkinter / turtle / idlelib / idle_test / pydoc_data / ensurepip / pip, the test C
    modules, tcl / tk / itcl / tdbc / thread, the X11, xcb, Xft, fontconfig, freetype, png libs, `usr/lib/jvm`,
    pkgconfig, cmake, static libs, `usr/etc/fonts` and the unused second sqlite copy
    (`libsqlite3.53.3.so`; `_sqlite3` links `libsqlite3.so` -> `libsqlite3.so.3.53.3`, kept). The result goes to
    jniLibs as `libpythonslim.zip.so` (`Python.kt` `bundledZipName`) and the original is excluded
    (`packaging.jniLibs.excludes`). `libc++_shared.so` is kept twice: curl_cffi needs the hash-named copy and the
    plain one is on `LD_LIBRARY_PATH` for ffmpeg / others.
  - Checked with an ELF reader on all four output zips: every kept library's `DT_NEEDED` is still in the zip
    (or is a system library), no dangling symlink, `testzip` clean. Python zip: arm64 33.2 -> 22.4 MB, v7a 23.4 ->
    13.3, x86 25.2 -> 14.4, x86_64 25.5 -> 14.7 (about 10.5 MB saved, more than the 8 MB expected).
  - `assembleRelease` (debug-signed locally): arm64-v8a 69.0 MB, armeabi-v7a 54.7, x86 60.4, x86_64 64.5, no
    universal file. Before (computed as after + the zip difference, no second build): about 79.4 / 64.3 / 70.7 /
    74.8 MB. The 1.4.1 arm64 file was 82.9 MB.
  - x86_64 emulator (debug build, airplane mode, no network): the package unpacks and `libpython.so` runs; imports
    of sqlite3 (3.53.3), ssl, hashlib, ctypes, zlib, bz2, lzma, brotli, websockets, requests, urllib3, certifi,
    mutagen, Cryptodome, readline, curses all work. curl_cffi is not in the armeabi-v7a / x86 / x86_64 packages at
    all (also in the original zips), so it can only be tested on the A56.
  - A56 (arm64 release APK, debug-signed, installed after `adb uninstall` with the owner's OK, app data wiped), all
    completed: 9gag `ae5Ag7B` (inspect + download 1.9 MB, so curl_cffi works), YouTube `jNQXAC9IVRw` (15 formats,
    "Merging audio and video" step ran, 464.8 KB), SoundCloud `forss/flickermood` (m4a 2.4 MB), direct 8MB.bin
    (1.3 MB/s). Cookies were tested later (see step 2). Not run: the exact log line "no impersonate target" (release build has no log; the
    9gag success is the proof here, a verbose build would show the line).
- [x] Result, measured (release APK, debug-signed, 2026-11-16): arm64-v8a 53.2 MB (1.4.1: 82.9 MB), armeabi-v7a 41.5,
  x86_64 48.2, x86 47.5. With `useLegacyPackaging` false the libs are stored raw, so the file is larger than the legacy 39.5 MB
  but nothing is extracted on the device (owner's choice). Still to do before the release: the first CI run, the signed APK
  from CI, the security scan (item 15), a 16 KB page-size device.

## 15. Security scan (owner's order 2026-11-16, with the APK work and before the release)

Last review: 2026-11-04 (CLAUDE.md "Bridge pairing"), before the command line, the Automation API, archive
extraction and the extension 3.4. Socket.dev lists the Firefox extension with "Permission" (8 instances: downloads,
contextMenus, storage, notifications, cookies, nativeMessaging, webRequest, scripting) and "Wildcard Host
Permission" (`<all_urls>`). Those two are information, not a finding, and `<all_urls>` is needed (page button,
`webRequest`, cookies of the page's site).

- [ ] Extension: is `nativeMessaging` still used? Its only call is `sendNativeMessage` in `background.js`. If there is
  no native host, remove the permission (7 left). Say in the store listing that every request goes to
  `127.0.0.1:28450` only. Read the Socket report again after the next upload.
- [ ] Bridge and the new Automation API: token on every endpoint, off by default, constant-time compare, no
  endpoint that reads or deletes files outside the download folder, `delete` only a download's own file.
- [ ] Archive extraction (item 2): zip-slip, symlinks, a bomb (size and file-count limit), a path outside the
  folder; fuzz a few damaged archives. Do this before the feature ships, not after.
- [ ] Dependencies: known vulnerabilities in the Gradle libraries, the bundled Python packages (yt-dlp, curl_cffi,
  Cryptodome) and the media tools (ffmpeg, deno). Pin and write down the versions.
- [ ] The repo and CI: a secrets scan of the history, the permissions of each job in `build.yml`, actions pinned to a
  commit, signing keys only from secrets.
- [ ] Release files: the `.sig` check of the in-app updater still refuses an unsigned or changed file.
- [ ] Android: R8 build, exported components and permissions in `AndroidManifest.xml`, cleartext traffic
  (`usesCleartextTraffic="true"` is on for plain http downloads: confirm it is the only reason).
- [ ] Write the result here: what was found, what was fixed, what was accepted and why.

## Looked at and not planned

Image galleries (gallery-dl), RSS feeds, the old Windows themes (Luna, Aero, Aqua), debrid accounts,
`torrent-create`. Say so if one of them is wanted.

## Notes from the comparison (2026-11-14, this PC, same network, same 64 MB file)

- Idle memory, Private Working Set after 30 and 60 s, window open: FetchJet 1.4.1 17.4 MB, Ezoo 16.3 MB,
  FDM 160 MB, Mister Download Manager 188.7 MB (GUI process; its CLI used 0.4 MB).
- 64 MB, 8 connections, alternating runs: round 1 curl 8.7 s, FetchJet 10.3 s, Mister Download Manager 13.5 s;
  round 2 curl 21.3 s, FetchJet 19.1 s, Mister Download Manager 21.5 s. The network moves a lot between
  runs; the differences are inside that noise.
- Seen once, not repeated: in the 32-connection test one 64 MB download ended with the task in ERROR
  "read failed" at 99% although the saved file was complete and its hash matched. The message comes from a
  local file read (`DesktopFileSystem.kt`, `winReadAt` returns -1 without logging `GetLastError`). It did not
  come back in the later runs (two more at 32 connections, two at 8) and the cause was not found. Not a work
  item; if it shows again, log the Windows error code first.
