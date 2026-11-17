# FetchJet 1.5.0: work list

Status 2026-11-17: every item is built and tested as each section says, except items 4, 12, 13 and `get` / `resume`
(dropped by the owner) and item 5 (wake stopped on every system, code kept). Left: the owner's checks (installers, item
17), the Mac build of the Swift changes (see CLAUDE.md Current state), optional extras marked [ ] below, and the release
itself (notes in `release-notes/1.5.0/`).

Written 2026-11-14 after a look at Mister Download Manager 0.5.2 (Rust + egui, GPL-3.0,
by Ahmed Nefzaoui; unpacked and read, its CLI run on a 64 MB file; its window and its video part were not
tested). The list below is what is worth having here. Each item says what the other app does, so we know
what "done" has to cover.
Owner, 2026-11-16: 1.5.0 may be split into two releases; the Order section below is the proposal. Item 14 (a
smaller Android APK) has the highest priority and its steps are the natural place to cut.
Legend: [x] done and tested as said, [ ] not done or not tested yet.

Mac session 2026-11-18 (after `30261ea`): iOS built (one Swift error fixed: `LocalClockKt.localUtcOffsetMs` is the
`expect`, Swift sees only the iOS `actual`; `SettingsDownloadsPage.swift` now reads the offset from `TimeZone.current`).
iPhone 17 / iPad Pro 13" simulators: the row context menu (Open folder, Resume, Send to device, Copy link, Select, Details,
Delete), Copy link filled the clipboard with the "Copied" toast, Settings > Downloads folders-by-site card (rule added for
127.0.0.1, kept) and the monthly report card, the Add dialog shows "Folder rule for 127.0.0.1" and the task went to the rule's
folder (the download itself failed with the simulator's broken background daemon, as known). Fixed on the iPad: the lifted
row of the menu had a white strip over the sidebar (`.contentShape(.contextMenuPreview, ...)` in `TaskListScreen.swift`).
macOS native: core, torrent, `make-dmg.sh` built; corehost rowmenu 19, siterules 15, dns 14, trash 10, archives7z 15 (7-Zip
`7zz` from Homebrew), extract 20, settings 87, automation 41, unsafe_names 12, wake skip (1), scan 23, download 29,
integrity 32, home 41, add 29, network 11 (proxies started by hand): all 0 failed. Fixed: `test_dns` adapter message (the
Darwin client gives only the standard reason phrase, so the loopback proxy also sends `X-FetchJet-Proxy-Error` and
`HttpChunkUrls` reads it on an error status); `test_trash` looked in the XDG trash on the Mac (now `~/.Trash`); the trash
settings rows said "Recycle Bin" on macOS / Linux (`deleteToTrashNotWindows` / `trashArchiveNotWindows`, 15 languages).
Real window on a throw-away `--home`: right-click row menu, Settings > Downloads (folders by site, report), Settings >
Network (DNS + adapter) seen and captured. Not built after the fixes: Windows / Linux native, Android.

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
  Mac session 2026-11-16: iOS `IosAutomationServer.kt` built (Gradle + Xcode, no change needed); iPhone 17 simulator with
  `SIMCTL_CHILD_FETCHJET_AUTOMATION=1` (token in `settings/automation-token.txt`), curl: no token 401, wrong token 401, no
  `X-FetchJet` 403, `Origin` 403, status, add through `fetchjet://add?json=` + Start Download (30 MB, throttled), pause 200,
  pause again 409, start 200, unknown id 404, no id 400, unknown action 404, pause on a finished one 409, delete 200 and the
  file gone, automation off 403, on again 200, relaunch without the env var still listens (saved flag). macOS native:
  core + host + `make-dmg.sh` built (`os::print_out` compiles), `test_automation.py` 40 checks 0 failed, the staged
  `FetchJet.app` on a throw-away `--home`: not running prints `{"error":"FetchJet is not running"}` exit 1, off by default
  (exit 1), `automation on`, `status`, `/d` download, `pause`, pause again (exit 1), `start`, `status <id>`, `delete` (file
  gone), `automation off`; no login item left. Note: `xcodebuild` with `generic/platform=iOS Simulator` fails on a fresh
  clone ("no XCFramework found", FJTorrent is arm64 only); use the simulator id with `ARCHS=arm64 ONLY_ACTIVE_ARCH=YES`.
  Linux (WSL, 2026-11-16): core cross-compiled on Windows, host built in WSL (`os::print_out` compiles), `test_automation.py`
  40 checks 0 failed, the real `FetchJet status` / `pause` with no app running print the error and exit 1.
  Not run: the old JVM desktop app (no `/api`).
- [x] (status, pause, start, remove, delete over the API; memory is read from outside with the OS) Enough for a test on the real app with no mouse: add a download (bridge `/add`), read its state, speed
  and size as JSON, pause / start it, and read the app's memory from outside. Measure idle and during a
  download on the real native app this way, the same way every time.
- [x] Tests in corehost: `test_automation.py` covers off by default (403), no token (401), Origin / foreign Host / no `X-FetchJet` refused, unknown ids (404 with the list), no id (400), unknown action (404), only GET / POST, pause / start 409 on a finished one, delete takes the partial files, and (added 2026-11-17) remove keeps a finished file: 41 checks (Windows). Also `test_unsafe_names.py` 12 (Windows and Linux).
- `get` / `resume` without the window: dropped (owner's decision, 2026-11-17): `/d` already hands a link to the app, and
  tests use corehost and the automation commands.

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
- [x] Setting "Extract archives when their download completes" (off by default): `BehaviorSettings.extractArchives`, a switch under Settings > Downloads > Organize in the Slint, Compose and SwiftUI settings pages, 5 new strings (title, on, off, done, failed) in the 15 languages. Done by `QueueManagerRepository.observeArchiveExtraction`: only a download that finishes while the app runs (a task seen unfinished in this session) and is not a torrent; the result comes back as a toast (`archiveEvents`). Windows: `test_extract.py` 13 checks (setting off, on, a download from before the switch is left alone, zip with several things, tar.gz with one folder, gz of one file, zip-slip name, a broken zip, off again); `test_settings` 87, `test_home` 41, `test_add` 29, `test_keys` 41 still pass. Android: the switch is in Settings > Downloads > Organize (screenshot on the emulator) and the extraction works on the emulator and on the A56 (a zip with several things, a tar.gz with one folder, a .gz, a zip-slip name, a broken zip left alone; the test downloads and files were removed afterwards). The A56 found a crash that no test did: the new fields of `QueueManagerRepository` were declared after its `init` block, so the observer met a null set as soon as the app had old downloads (fields now above `init`, see the same lesson for `DashboardViewModel.init`). Mac session 2026-11-16: iOS builds (one Swift fix: `AddSections.swift`, `trailing` before `hasError`); the switch on the iPhone 17 toggles and `extractArchives=true` is written and survives a relaunch; with it on, a new zip extracts by itself (folder `auto`). Not run: Linux build, the progress line while extracting (the test archives are too small to see it), the toast on iPhone.
- [x] (`feature/archive_extract/`: `Inflate.kt` own DEFLATE, `ZipReader.kt` incl. zip64, `TarReader.kt` ustar / pax / GNU long names, `Gzip.kt`, `Xz.kt` own LZMA2 with the CRC32 check, `ArchiveExtractor.kt`; 16 JVM tests in `ArchiveExtractorTest`, fixtures made with Python in `src/jvmTest/resources/archives`; compiled for mingwX64 and linuxX64; where it goes: a work folder first, then one top-level item next to the archive or a folder named after it, numbered when the name exists; limits 100 000 files, 64 GB, ratio guard; links and `..` / absolute names skipped and listed; a password zip is refused with a clear message). ZIP, TAR, GZ, XZ in our own code (commonMain), with the zip-slip guard, a size and file-count limit and
  numbering instead of overwriting. `DesktopZip.kt` (native desktop) only reads zips for the tools
  download; check whether it can be reused before writing a second reader. XZ needs an LZMA library on
  Android and iOS.

Phase 2:
- [ ] 7Z on Android and iOS with a library, if one of a sensible size is found. Android already ships
  `commons-compress`, but its optional `xz` classes are not packed (the release build uses `-dontwarn`, see
  CLAUDE.md), so 7Z there needs that dependency added and tested. iOS needs a C library through cinterop
  or Swift. Not verified yet.
- [x] 7Z and RAR on desktop through an installed 7-Zip (2026-11-17, Windows session): `ExternalArchiver` (expect/actual;
  native desktop finds `FETCHJET_7Z`, `Program Files-Zipz.exe`, `/opt/homebrew/bin/7zz`, or `7zz` / `7z` / `7za` on
  PATH) runs `7z x` into the same work folder as our own code; afterwards the app counts the files and bytes against the
  limits, deletes any link 7-Zip made, and places the result like the other kinds. Without 7-Zip a .7z / .rar shows no
  Extract button, and an archive that needs it says "install it from 7-zip.org". Not on Android / iOS (no outside program).
  RAR was not tested (7-Zip cannot make a .rar to test with); the code path is the same as .7z.
- RAR is never bundled (the unRAR licence does not allow it inside a GPL-3.0 program) and not on Android or
  iOS (no way to run an outside program there).
- [x] Password-protected archives (desktop, through 7-Zip: ZIP AES / ZipCrypto, 7z, rar): a dialog asks for the password
  (Slint alert with a password field, `ARCHIVE_PASSWORD`; shared `archivePassword` in the UI state, events
  `OnArchivePasswordEntered` / `OnArchivePasswordDismissed`), a wrong one asks again and says so, Cancel closes it; the
  password goes to that one extraction only and is never saved. Our own zip code still refuses a password zip clearly when
  7-Zip is missing. 4 texts in the 15 languages.
- [x] Split archives wait for all the volumes: `.7z.001`, `.zip.001`, `.part1.rar` (`ArchiveExtractor.volumeSet` /
  `volumeNumber`); only the first volume has an Extract button; with "extract when they finish" on, the extraction starts
  when every volume in the list (same folder) is finished and numbered without a gap; an attempt that finds a part missing
  says nothing (no toast) and is tried again when the next part finishes.
- [x] Where the files go (`ExtractPlace`, Settings > Downloads under the extract switch): a folder only when there are
  several things (as before) / always a folder named after the archive / next to the archive (each item numbered when its
  name exists). 4 texts in the 15 languages. Slint only (Android / iOS keep the first behaviour).
- [x] Tests: `ArchiveExtractorTest` 24 (new: volume names, the three places); `tests/corehost/test_archives7z.py` 15
  (Windows, with the installed 7-Zip: .7z through the Extract button, a 3-part split .7z that waits for every part, an AES
  zip: dialog, wrong password, right password, nothing saved, cancel; Linux without 7-Zip: skipped cleanly, 2);
  `test_extract` 16 / 20, `test_settings` 87, `test_trash` 10 still pass.
- [x] Extract, later the same day (2026-11-16): the row shows a second progress line while it extracts (reuses `postProcessLabel` / `postProcessProgress`, text `extractingState`), the Extract button opens the result folder in the file manager when it is done (`PathResolver.openFileLocation`), and the row buttons Play / Pause / Resume / Open / Compress / Extract share one round tinted style with drawn icons (Slint `RowIconButton`, Compose `RowIconButton`, SwiftUI primary tint). Hardening found by a fuzz test of ~880 damaged archives: names with `<>:"|?*` and Windows device names are renamed, every error comes out as an `ArchiveException`. Tests: `ArchiveExtractorTest` 23 (zip64, data descriptor, Arabic / Japanese / CP437 names, GNU long names, 120 MB in 0.8 s on the JVM), `test_extract.py` 16, `test_extract_big.py` 11 (300 MB of text: exact bytes, progress rising 0 to 99 %, release core 25 MB/s, the debug core only 5 MB/s). The A56 (arm64 release, R8): every kind extracted into a root folder, and Files opened on the result; one of the three tar variants was not re-checked there.
- [x] Android storage (2026-11-16): `MANAGE_EXTERNAL_STORAGE` in the manifest and `AllFilesAccess`; before a download starts the app writes a probe file in the folder, and if it cannot (Android refuses a custom root folder) it opens the "All files access" page and says so instead of failing at 99 % with EPERM. Creating a folder in the storage root falls back to `Download/<name>` when the permission is missing.
- [x] Add dialog names (2026-11-16): a link the server names (`examplefile.com/file-download/36`) gets its name by itself (silent probe 0.7 s after the paste, and from "Test the link"); a link nobody can name shows the empty File name box with a red border and "Type a file name to start" (`test_addname.py` 14).
- [x] (the row and the details: a finished zip / tar / gz / xz row shows an Extract button in the place of the open / play button, and the details have an Extract button; works with the setting off, a second press makes a numbered copy, a toast says "Extracting ..." then the result; Slint, Compose and SwiftUI; `ArchiveTasks.canExtract`, event `OnExtractTaskClicked`, actions `RowExtract` / `DetailsExtract`. Tested: `test_extract.py` 16 checks, the Android emulator (both rows tapped, files extracted, screenshot of the icon). Mac session 2026-11-16: iPhone 17 and iPad Pro 13" (light and dark): zip / tar.gz rows show the Extract button, mp3 / mp4 show play (mp3 on iPad also compress), bin shows the open arrow, the details page has Extract; tapping it extracts into a numbered folder (`pack`, `pack (1)`, `pack (2)`) and opens Files on the result; a fake zip gives the toast "Could not extract 300KB.zip: this is not a zip file"; the tar.gz tree equals the source (macOS `tar` adds `._*` files itself). Slint window on the Mac (staged `FetchJet.app`, throw-away `--home`, dark and light): the row buttons are round and tinted, extract on zip / gz, play on mp3 / mp4, compress + play on mp3, open arrow on bin. Found: a zip made by macOS `zip` with an Arabic name and no UTF-8 flag is extracted with a CP437 name (`ZipReader` reads the name as CP437 when bit 11 is not set); the same name inside a tar.gz is right. Not run: the A56, the right-click menu. Still open: the right-click menu, which belongs to item 7.) Extract from the right-click menu and from the details, not only after a download.
- [x] "Move the archive to the Recycle Bin after extracting" (2026-11-17, Windows session; off by default, shown under the
  extract switch) and, asked by the owner the same day, "Deleted files go to the Recycle Bin" (on by default): "Delete with
  the file" sends a finished download (or a torrent's folder) to the system's trash instead of deleting it; unfinished parts
  are still deleted. `core/filesystem/SystemTrash.kt`: Windows `SHFileOperationW` (FOF_ALLOWUNDO; a file too big for the
  Recycle Bin is deleted, Windows' own rule), macOS `NSFileManager.trashItem` (compiled on the Mac only), Linux the
  freedesktop Trash (`$XDG_DATA_HOME/Trash/files` + `.trashinfo`, same drive only: a file on another mount is deleted).
  Android and iOS have no trash for any file: not offered there. 6 texts in the 15 languages; Slint switches only.
  `tests/corehost/test_trash.py` 10 (Windows: the item is found in `$Recycle.Bin` and removed again by the test; Linux:
  `XDG_DATA_HOME` pointed at a test folder). `test_download` 29, `test_paths` 17, `test_settings` 87 still pass.
- [x] Found while testing (2026-11-17): the walk over the extracted files added by the Mac commit (`markExtracted`) listed a
  single extracted file (a `.gz` of one file) as a folder; the listing is lazy, so its error escaped `runCatching` and the
  extraction was reported as failed ("failed to list ..."). Now the folder walk runs only for a folder. `test_extract` also
  waits 3 s before the second Extract press (it could land while the first one was still finishing); 16 (Windows, twice) and
  20 (Linux).
- [x] Texts in the 15 languages; tests in corehost (a good zip, a zip-slip zip, a bomb, a wrong password,
  a name that already exists).

## 3. Own DNS and a network adapter

What the other app does: `--dns` takes "system", a named service ("cloudflare-https", "quad9-tls",
"google-plain", "adguard-https", "nextdns-tls"), DNS server addresses, or a DNS-over-HTTPS address, with no
fall back to the system DNS. `--adapter` sends every connection through one network adapter (by name or
address) and fails if it is not there. Useful where a site is blocked by DNS and with a VPN adapter.

- [x] Done on the native desktop (Windows, Linux, macOS) 2026-11-17. How: the app's own loopback proxy
  (`platform/proxy/LoopbackProxy.kt`, already used for SOCKS on Windows) got a resolver hook and a source address.
  `NetworkSettings.activeProxy()` = the user's proxy if on, else this proxy when DNS or the adapter is set
  (`OwnNetwork.portFor`, `OwnNetwork.nativeDesktop.kt`), else none; `ktorProxy()`, `ytDlpArgs()` and the WinHTTP / curl
  clients take it, so downloads, link probes and yt-dlp all use it with no change of their own. TLS stays end to end
  (CONNECT), so certificates and SNI are untouched. Torrents and FTP keep the system's DNS (said in the hint).
- [x] Settings > Network > "DNS and network adapter": System / Cloudflare / Google / Quad9 / AdGuard (DNS-over-HTTPS to the
  service's IP address, so no system lookup is needed to reach it) / Custom (a DoH address, or DNS server addresses for
  plain DNS over UDP). `DnsWire.kt` builds and reads the DNS messages (A, then AAAA; answers kept for their TTL, 30 s to 1 h).
  A name our DNS cannot resolve fails with "The name of the server could not be resolved"; there is no fall back to the
  system DNS. With a user proxy on, a note says the proxy decides instead. Saved in `network.json` (`dns`, `adapter`).
  8 texts in the 15 languages.
- [x] "Network adapter": its IP address (empty = any); every connection is bound to it (`LoopSockets.bindSource`, Windows /
  Linux / macOS). An address that is not on this computer fails at once with "The network adapter <ip> is not available".
  Picking the adapter by name from a list is not built (it needs GetAdaptersAddresses / getifaddrs).
- [x] Tests: `DnsWireTest` (jvmTest) 3; `tests/corehost/test_dns.py` 14 (Windows and Linux) with a local DoH stub and a UDP
  DNS stub for a made-up name: the system DNS cannot resolve it (download fails and our stubs are not asked), DoH works and
  the stub is asked, UDP works, the adapter 127.0.0.1 works, 203.0.113.7 fails with the clear message, back to System the
  stubs are not asked again. `test_network` 11 (Windows). On Linux `test_network` fails 4 HTTPS-through-proxy checks on the
  test certificate; the same 4 fail with the core built from the last commit (checked by stashing the changes), so it
  is the test setup on WSL, not this work.
- Not on Android / iOS: the settings rows are shown only where `NetworkSettings.ownNetworkSupported` (native desktop).
  Android could use OkHttp's `Dns`, but yt-dlp (Python) would not follow it. The real DoH services were not called from
  here (internet is expensive); only the local stubs.

## 4. Server country and ping in the Add dialog

Dropped (owner's decision, 2026-11-17): not important, not built.

What the other app does: the Add dialog shows the server's country (flag) and its answer time ("United
States, about 95 ms"), found with a country database that stays on the computer.

- Not built (2026-11-17): the country needs an IP-to-country database inside the app (DB-IP Lite is several MB, and
  the APK size is the top priority of this release) or an online lookup (it would send the server's address to a third
  party, against the privacy promise). Owner's call.
- [ ] Optional and cosmetic. Needs an IP-to-country database (DB-IP Lite is CC BY 4.0, attribution
  required) and costs size; decide whether it is worth it before building.

## 5. Wake the computer for a scheduled download on more systems

Stopped on every system (owner's decision, 2026-11-17): `WakeForSchedule.ENABLED = false` hides the setting and never arms a
timer; the code stays. Set it to `true` to bring it back (`test_wake.py` skips while it is off).

Today only Windows has it: Settings > Downloads > "wake for schedule" arms a Waitable Timer one minute before
the first scheduled start (`WakeTimer.mingw.kt`, `test_wake.py`). Every other `WakeTimer.isSupported` is false
and the row is hidden.

- [ ] macOS: `pmset schedule wake "<time>"` (needs admin: a one-time helper or a password prompt); also
  `pmset repeat`. Decide the permission flow before building.
- [ ] Linux: `rtcwake` or a systemd timer with `WakeSystem=true` (needs root; not every BIOS wakes from sleep).
- [ ] Android: `AlarmManager.setExactAndAllowWhileIdle` through WorkManager; needs the exact-alarm permission.
  Less useful (a phone does not sleep like a laptop).
- [ ] iOS: no API wakes an app at a chosen time (background tasks are the system's choice). Not planned.
- Not built in the 2026-11-17 run (owner away, told to go through everything): macOS and Linux both need a decision on
  the permission first (`pmset` needs admin, `rtcwake` / a `WakeSystem` timer needs root or CAP_WAKE_ALARM), which the item
  says to decide before building; WSL cannot sleep, so nothing could be tested. Android has no `WakeForSchedule` wiring and
  no switch today; a real wake there needs an exact-alarm permission and a foreground service started from the alarm
  (Android 12+ rules), and Doze cannot be checked on the emulator in a useful way. Left for the owner's call.
- [ ] Tests: the same `test_wake.py` checks on each system; a real sleep and wake only by hand.
- [ ] Windows: a real sleep and wake (the core test only checks that the timer is armed, 11 checks 2026-11-14).

## 6. Small items left from 1.4.1

- [x] `AudioCompress.slint`: `viewport-width` / `viewport-height` replaced by `content-width` / `content-height` (the Slint deprecation warning). Built on Windows, no warning left; the window was not opened.
- [ ] (draft text below; the application itself is the owner's) Windows MSI signing: apply to SignPath Foundation (free for open source). Needs a "Code signing policy" section and a privacy statement on the download page, 2FA on GitHub and SignPath, and probably a GitHub-hosted runner for the MSI build (the terms do not say which runner is accepted). The reputation check may refuse a project with few stars and downloads.
  Draft for the download page after SignPath approves (do not publish before, it would claim a signature that does not exist yet):
  "Code signing policy. Windows installers are signed with a certificate provided by the SignPath Foundation, signing service by SignPath.io. Roles: author, reviewer and approver of every release: Michelle Raouf (https://github.com/the-best-is-best). Only files built by the project's GitHub Actions workflow from the public repository are signed. Privacy. FetchJet sends one anonymous usage ping a day (a random install id, the app version and the platform; the country is read from the request by Cloudflare) and it can be turned off with the environment variable FETCHJET_NO_USAGE_PING=1. Apart from that it talks to the network only for what the user asks (downloads, link inspection), to check for a new version on GitHub, and to download its media tools (yt-dlp, ffmpeg, deno) from their official release pages. (Check this text against the final privacy statement in the README before publishing it.) The browser extension talks only to the app on 127.0.0.1."
- [x] iOS CI log: about 1850 warnings, all from libtorrent compiled in the build (none from our Swift or Kotlin). `-w` added to `CMAKE_CXX_FLAGS` (libtorrent's C++ only; the Objective-C++ wrapper keeps its warnings) in `scripts/build-libtorrent-ios.command`. Not built (needs the Mac / CI): the script hash is in the CI cache key, so the next iOS run rebuilds libtorrent once (~20 min). The one warning that is ours is Xcode's "Building targets in manual order", left as it is.
- [x] GitHub shows the licence as `NOASSERTION`: `LICENSE` had a 12-line FetchJet header (698 characters) before the GPL text; the rest was byte-identical to gnu.org's `gpl-3.0.txt`. `LICENSE` is now exactly that file (sha256 equal) and the header (name, copyright, the "free software" paragraphs) moved to the new `NOTICE`. GitHub re-reads the licence after the push; check the repository page shows "GPL-3.0".

## 7. Context menu on a download row (owner's request, 2026-11-14)

Right-click on a download row, finished or not, opens a proper menu with the actions for that download
(open, open folder, pause / resume, send to device, copy link, details, delete and so on). On phones and
tablets a long press opens the same menu instead of the details.

- [x] Done 2026-11-17 (Windows session). One list in shared logic: `feature/row_menu/RowMenu.kt` (`RowMenu.entries(task,
  options)`, `RowMenuEntry`: Open, Open folder, Pause, Resume, Extract, Compress audio, Send to device, Copy link, Select,
  Details, Delete... (asks first, the usual remove dialog); a line before Send and before Delete; Delete in the error colour).
  11 texts `rowMenu*` in the 15 languages. New shared event `OnCopyTaskLink` (copies and shows "Copied"). Texts were added
  with the new `scripts/i18n-add-strings.py <spec.json>` (adds a key to `AppLocalization` and the 15 providers at once).
- [x] Desktop (Slint): right-click on a row opens `RowMenuPopup` (`widgets/Menu.slint`) at the pointer, flipped up near the
  bottom; channel `rowMenu` (`RowMenuItemDto`), actions `RowMenu` / `RowMenuPick` / `RowMenuClose`, `RowMenuPresenter.kt`
  (a pick that is not in the current list is ignored). `tests/corehost/test_rowmenu.py` 19 checks (Windows): entries per
  state (finished, zip, mp3, running, paused), labels, dividers, red Delete, Details, Copy link toast, Delete asks first,
  Extract, Pause / Resume, unknown id. Screenshots dark and light (`shots_rowmenu.py`, the shot tool got `--right-click`).
  Not done: Shift+F10 / the Menu key.
- [x] Compose (Android): a long press opens the same menu (`DropdownMenu` under the row, `rowMenu` slot in `TaskRow`); in
  selection mode a long press still checks the row; selection now starts from the menu's "Select". Checked on the emulator
  (Pixel 10 Pro, A56 not connected): menu shown (screenshot), Copy link -> "Copied", Select -> "1 selected".
- [x] SwiftUI (iOS / iPad): `.contextMenu` on the row with the same entries (switch on `entry.name`), the long press that
  started the selection is replaced by "Select" in the menu (in selection mode the menu has select / unselect);
  `FJStore` now copies on `DashboardUiEffect.CopyText`. NOT BUILT: needs the Mac (`TaskListScreen.swift`, `FJStore.swift`).

## 8. Sort into folders: rules by site (owner's request, 2026-11-14)

Today "Sort into folders" only sorts by kind of file (Video, Audio, Documents ...), and a folder the user
picked for a kind is remembered. The addition is rules by site: "everything from this site goes to this
folder", whatever the file type is. Nothing like it exists today (`HostLimits.kt` only limits connections
per host).

- [x] Done 2026-11-17 (Windows session). Rules live in `core/settings/SiteFolderRules.kt` (`SiteFolderRule(site, folder)`,
  `match`: the link's host, then the page / Referer; a site matches itself and its sub-domains, `www.` dropped, the most
  specific site wins, so there is no order to edit) and `BehaviorSettings.siteFolders` (`siteFolders=` JSON line in
  `behavior.properties`; `addSiteFolder`, `removeSiteFolder`). Edit = remove + add.
- [x] A rule wins over the kind-of-file folder: `enqueueTask` uses the rule's folder when no folder was given, and adds no
  type folder to a rule's folder; links that come without the dialog (bridge `/add` direct, `/links`, the list) take the rule's
  folder too (`DashboardViewModel`). No rule = today's behaviour.
- [x] Add dialog: the folder field shows the rule's folder and a line "Folder rule for <site>" (`addFolderRuleSite` in the UI
  state; Slint `AddDialog.slint`, Compose `DestinationSection`, SwiftUI `AddDestinationSection`). A folder picked by hand
  wins and the line goes away; a rule's folder is never saved as the remembered kind folder. Found on the way: the native
  desktop presenter and the SwiftUI section re-added the type folder by themselves when the kind changed (the shared view
  model already does it), which also threw away a remembered kind folder set in `behavior.properties`; both copies removed.
- [x] Settings > Downloads > "Folders by site": the list with Remove, Site + Folder fields, "Choose folder..." and "Add rule"
  (Slint `SettingsRows.kt`, Compose `SiteFoldersCard` with the app's folder picker, SwiftUI `SettingsSiteFoldersCard`).
  7 texts in the 15 languages.
- [x] Tests: `SiteFolderRulesTest` (jvmTest) 6; `tests/corehost/test_siterules.py` 15 (Windows and Linux): add from Settings
  (site cleaned, a bad site refused), rule hit through the dialog (folder, rule line, no Archives folder), no rule = the
  remembered Archives folder, sub-domain without the dialog, a look-alike host, a hand-picked folder wins, remove.
  `test_foldertype` 8, `test_add` 29, `test_addname` 14 still pass. Not run: Compose and SwiftUI screens (Android built;
  iOS needs the Mac: `SettingsDownloadsPage.swift`, `AddSections.swift`).
- [ ] Optional on the same rule: a speed limit or a connection count for that site, and a command to run when the download
  finishes (the item says to decide after the first part is used; not built).

## 9. Scan a file after the download (idea, 2026-11-14; built 2026-11-17)

- [x] Setting "Scan files when they finish" (off by default) in Settings > Downloads, next to "Extract archives",
  in the native desktop app (Windows / macOS / Linux). Android and iOS: no scanner, the setting is not shown
  (`FileScanner.supported` false).
- [x] Windows: the system's attachment scan (`IAttachmentExecute::Save`, what browsers call), so whatever antivirus is
  installed and registered with Windows checks the file (Defender, Kaspersky, ...). It also writes the Mark of the Web
  (`Zone.Identifier`, ZoneId=3 + the source link), so SmartScreen / Office warn before the file runs.
  Verdict: Save OK and the file still there = clean; `INET_E_SECURITY_PROBLEM` = blocked by policy; the file gone =
  threat (the antivirus removed it); anything else = failed. Same reading as Chromium. Code:
  `sharedLogic/src/mingwX64Main/.../feature/file_scan/SystemScan.mingw.kt` (COM by its vtable).
  Ezoo (read in its source, `crates/engine/src/system.rs`) only starts a program the user types and does not read the
  answer; its Mark of the Web is written separately.
- [x] A scanner command (optional, every desktop OS): `[File]` is replaced by the file (or the file is added at the end).
  Exit code 0 = clean, anything else = threat with the scanner's last line, file gone = threat, 15 min = failed.
  On macOS / Linux there is no system scanner: without a command the toast says to add one (e.g. `clamscan`).
- [x] Order: scan, then extract; an archive with a threat (or blocked) is not extracted. The row shows
  "Scanning for viruses…" while it runs, a toast for a threat / block / failure / no scanner (no toast when clean),
  and the details show "Virus scan: ..." (green / red). The verdict is saved with the task (`scanResult` in
  `tasks_cache.json`; older versions ignore the field).
- [x] Texts in the 15 languages. Test `desktopNative/tests/corehost/test_scan.py` (19 checks, real Windows
  attachment scan on a clean file + a fake scanner command): passed 2026-11-17. Also re-run: extract 16 (one run
  failed "the second copy" once, then passed twice), settings 87, home 41, integrity 32, add 29. JVM, Android and
  Linux compile; macOS not built (needs the Mac: only the 5-line `SystemScan.macos.kt` and the shared code).
- [x] Real threat (EICAR, owner's OK, 2026-11-17, served from memory by a local server, not kept in the repo): Defender's
  real-time protection blocks the `.part` while it is written, before any scan: opening it again fails with Windows
  error 225 `ERROR_VIRUS_INFECTED` (checked with `CreateFileW`; 226 = `ERROR_VIRUS_DELETED`). The download used to fail
  with "failed to open ...part". Now `winOpenFile` keeps that error (`winLastOpenBlockedByAntivirus`),
  `DesktopFileSystem.open` throws `ANTIVIRUS_BLOCKED_ERROR` ("The antivirus blocked this file: it found a threat in
  it"), and the repository marks such a task `scanResult = threat` with the threat toast, even with the scan setting
  off. Verified for `eicar-test.com` and `.txt`; `test_scan` 19/19 after it; Linux and Android compile.
  The owner's run (eicar.org) took ~31 s to fail: `fetchWithRetry` retried 1+2+4+8+16 s. It is now
  `AntivirusBlockedException` and is not retried: the task fails in 0.1 s. The message is translated in the 15
  languages (`scanAntivirusBlockedError`, through `localizeStep`).
- [ ] Not tested: another antivirus than Defender.
- [x] macOS "downloaded from the internet" mark (owner's request, 2026-11-17), always on, not tied to the scan
  setting: every finished download (not torrents) gets `com.apple.quarantine` through
  `NSURL.setResourceValue(..., NSURLQuarantinePropertiesKey)` (agent FetchJet, type web download), as Safari and Chrome do;
  Gatekeeper / XProtect then check the file when it is opened. `SystemScan.macos.kt` (`systemMarkFromInternet`), called
  from the repository's completion loop through `FileScanner.markFromInternet` (no-op on Windows, Linux, Android, iOS, JVM).
  **Mac session 2026-11-17:** compiled with no change. Found: macOS keeps only the flags and an event id in the xattr
  (`0281;<time>;;<uuid>`); the agent name goes to `~/Library/Preferences/com.apple.LaunchServices.QuarantineEventsV2`, and the
  data / origin URLs are not stored there at all (Firefox's rows are empty too). "Where from" in Finder is a separate xattr
  that browsers write themselves, so `markWhereFrom` now writes `com.apple.metadata:kMDItemWhereFroms` (binary plist of the
  link and the page) through `NSFileManager.setAttributes` + `NSFileExtendedAttributes` (`setxattr` is not in the
  Kotlin/Native posix klib). Also found: files FetchJet extracted from a marked zip had no mark, so an extracted `.app` opened
  with no Gatekeeper check. `QueueManagerRepository.markExtracted` now marks the extracted item and everything under it
  (no-op off macOS).
  Results: `test_scan.py` 22 / 22 on the Mac (5 new macOS checks: mark with the scan off and on (agent FetchJet read from
  the events database), Where from holds the link, no "could not mark" in the log, an extracted file is marked); on the Mac
  without a command the verdict is "none". Re-run: extract 16, settings 87, home 41, add 29, integrity 32, all passed; iOS
  compiles. Real staged `FetchJet.app` on a throw-away `--home`, local server: a plain file and a zip holding a small
  ad-hoc-signed `Hello.app` sent with `/d ... /p ... /n` both got the mark and the Where from; the zip opened with Archive
  Utility and the `.app` extracted by FetchJet both showed "Hello.app Not Opened: Apple could not verify ..." and
  `spctl` said rejected. No login item left.
- [x] Found on the Mac and fixed (2026-11-17): the zip / tar extraction dropped the Unix permissions
  (`Hello.app/Contents/MacOS/Hello` was `rwxr-xr-x` in the zip, `rw-r--r--` after), so an extracted app or script did
  not start on macOS / Linux ("Launch failed"). `ZipEntry.executable` (Unix zip, any x bit) and `TarEntry.executable`
  (mode field) now go to `Run.write`, which calls `markExecutable` (new expect in `core/filesystem/FileModes.kt`: JVM /
  Android `File.setExecutable(true, false)`, iOS `chmod 0755`, native desktop `DesktopOs.makeExecutable`, no-op on
  Windows). `test_extract.py` has 4 new checks (zip and tar program keep the x bit, plain files are not made runnable),
  skipped on Windows: 20 / 20 on the Mac; `ArchiveExtractorTest` 23 / 23; Android compiles. iOS built (Gradle + Xcode)
  and checked on the iPhone 17 and iPad Pro 13" simulators: a zip from the local server with `tool/run.sh` (755) and
  `tool/note.txt`, Extract button on the row: `run.sh` `rwxr-xr-x`, `note.txt` `rw-r--r--`, Files opened on the folder.
  The simulators' background download daemon was broken again ("unknown error"), so the downloads ran with the speed
  limit on (foreground engine); the limit was switched off after. Not fixed: a zip made by `ditto` without
  `--sequesterRsrc` keeps its `._name` AppleDouble entries as plain files (Archive Utility merges them).
- [x] Settings hint per system (2026-11-17, Windows session): with no built-in scanner (macOS / Linux) it says "This system has no built-in scanner: enter one, for example clamscan [File]." (`scanCommandHintNoScanner`, 15 languages). The hint is now the footer of its own card (scan switch + command): as a note inside the card it was cut to one line (the Windows text too). Screenshots on Linux (en / ar). `test_scan` checks the hint: Windows 20/20, Linux 18/18; `test_settings` 87/87 on both.
- [x] Windows check of the Mac commit 2087bec (2026-11-17): Windows / Linux cores, JVM, Android build; Windows test_extract 16, test_scan 19, test_settings 87, test_home 41, test_add 29, test_integrity 32; Linux test_extract 20; ArchiveExtractorTest 23. test_extract failed once on Windows ("a second press makes a numbered copy", 30 s) while a Gradle build ran beside it, then passed 3 times; cause not proven. A56: Extract of a zip with 4000 files in 40 folders: 37.2 s on the installed release, 40.7 s on the new debug build; a walk over those files on the phone takes ~0.5 s, so no visible cost.
- [ ] Not run on the Mac: EICAR (macOS has no real-time antivirus to block it; XProtect checks only when a file is opened).

## 10. Monthly downloads report (idea, 2026-11-14)

- [x] A page that shows, for a month: how many files, how many GB, and which sites they came from. Read only
  from the history the app already keeps; nothing leaves the device.
- [x] Built 2026-11-17 (owner: "everything on the ROADMAP"): "Downloads by month" at the end of Settings > Downloads on the
  desktop (Slint), Android (Compose) and iOS (SwiftUI, not built here). A month picker (the months that have finished
  downloads, newest first, up to 24), the number of files, the total size, and the five sites with the most bytes (the
  page's site for media, "torrent" for magnets). Counted only from the downloads still in the list
  (`feature/report/DownloadReport.kt`, by the month they were added, local time); nothing is stored or sent. 6 texts in
  the 15 languages. `DownloadReportTest` (jvmTest).

## 11. Portable mode on Windows (idea, 2026-11-14)

- [x] Done 2026-11-17 (Windows session). A file `FetchJet.portable` next to `FetchJet.exe` makes the native app a
  portable copy (`WinInstallKind.PORTABLE`, checked before `FetchJet.installed`): its data folder is
  `<folder>\data\.fetch_jet` (settings, downloads list, logs, the media tools, the cache). `FETCHJET_HOME` and
  `--home` still win.
- [x] No start at login, no registry entries, no in-app installer: `launcherPath` is null for a portable copy (so start at
  login, the torrent / magnet handler and the `fetchjet://` link are not offered and the update dialog points to the
  download page instead of installing the MSI), and the browser integration does not touch the system
  (`mayChangeSystem`). Checked: the portable copy was started from `E:\tools\probes\portable-test\usb` (no focus, no
  bridge): its log says `(portable) home=...\usb\data\.fetch_jet`, `tools`, `cache`, `logs`, `bridge.json` were made
  there, the `Run` key and the `fetchjet` class in HKCU did not change, `~\.fetch_jet` was not touched.
- [x] Packaging: `desktopNative/packaging/make-portable.ps1 -StageZip <stage zip> -Out <zip>` (drops `FetchJet.installed`,
  adds the marker). CI makes `FetchJet-<version>-windows-portable.zip` from the stage zip in the Windows upload step and
  uploads it with its `.sig` (a failure there is only a warning); the download page lists it under Windows as "Portable, no
  install (.zip)" and no longer under the extension. CI not run. Not checked: the first-run "Main downloader" step and the
  media tools download inside a portable copy (the test copy had no internet on purpose).

## 12. Facebook stories (owner's question, 2026-11-14)

Dropped (owner's decision, 2026-11-17): not important, not built.

A link like `facebook.com/stories/<id>/<token>/` is not downloaded: the `FacebookIE` pattern of yt-dlp has no
`/stories/` path (read from its source, `facebook.py`; the link itself was not tried on the network), so the
app answers "unsupported". A story also needs a login and lasts 24 hours.

- Not done (2026-11-17): needs a logged-in Facebook account in a real browser; the session cannot log in as the owner.
- [ ] Try the browser extension on a real story (it may not see the viewer's video); say what it does.
- [ ] If it does not work: decide whether to read stories in the app (Facebook's own GraphQL + the user's
  cookies; big and it breaks often) or to say clearly in the Add dialog that stories are not supported.

## 13. WhatsApp status and other `blob:` videos (owner's question, 2026-11-14)

Dropped (owner's decision, 2026-11-17): not important, not built.

A WhatsApp status has no link. On WhatsApp Web (`web.whatsapp.com`) the browser decrypts it and plays it as a
`blob:` URL inside the page. The extension skips `blob:` on purpose (`content.js` line 90, `background.js` line
595), because the app is outside the browser and cannot open a blob. On Android the app could read WhatsApp's
`.Statuses` folder instead (a file read, not a download; needs a file permission; the path changes between
WhatsApp versions; nothing equal on iOS). Neither was tried.

- Not done (2026-11-17): needs the owner's WhatsApp Web session (QR login on his phone).
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

- [x] Extension: `nativeMessaging` was dead: the host `com.michelle.raouf.fetch_jet_downloader` is registered by no installer or build,
  so `sendNativeMessage` always failed and fell to `fetchjet://`. Call and constant removed from `background.js`, permission
  removed from `manifest.json` and `manifest.firefox.json` (7 permissions left; Safari never had it). `content.sim.js` and
  `capture.sim.js` pass. Store listing text and the Socket report: after the next upload.
- [x] Bridge and Automation API (desktop `BridgeServer.kt`, Android `ExtensionBridgeServer.kt`, `AutomationApi.kt`): bound to
  127.0.0.1 only, Host must be loopback, a web Origin is refused, every acting endpoint needs `X-FetchJet` + the token
  (constant-time compare), `/api` refuses any request with an Origin and is off until switched on. Accepted: whoever holds
  the token (the CLI reads it from `bridge.json`) can switch the API on (`fetchjet automation on`); a local program that can
  read that file can do more than that anyway.
- [x] FIXED 2026-11-17: `enqueueTask` kept the file name it is given (bridge `/links`, device sync,
  the external batch) without `sanitizeFileName`; the engines clean it only when the download starts. A task that fails
  before that keeps a name like `../../x`, and "Delete with the file" (also `/api/delete`) builds `destinationPath/../../x`.
  Now `enqueueTask` cleans the name with `sanitizeFileName` (not for torrents: their name is the torrent folder).
  `tests/corehost/test_unsafe_names.py` 12 checks (Windows): `/links` with `../../x`, `..\..\x`, `/abs/x` and `..` gives
  plain names, and `/api/delete` with the file leaves a file placed outside the folder alone. Not run against the old core.
- [x] FIXED 2026-11-17: `removeTask(deleteFile = true)` deleted every item of the temp folder whose name starts with the
  task's base name (`a.bin` -> anything starting with `a`; `.bin` -> everything). Other downloads' temp files can go with it.
  Now `ownsTempItem` (end of `QueueManagerRepository.kt`): the exact name or `<name>.`, and `<base>.` only for a media
  download (yt-dlp's `name.f137.mp4` pieces); an empty name takes nothing. `TempCleanupTest` (jvmTest) 3/3.
- [x] Archive extraction: `..`, absolute and drive paths refused (`safeParts`), links skipped (zip and tar), limits 100k files /
  64 GB / ratio 2000 from 4 GB, damaged archives (`damagedFilesNeverHangOrCrash`); `ArchiveExtractorTest` 23/23. Accepted: a
  small bomb can still write up to 4 GB before the ratio check stops it.
- [x] Dependencies: osv.dev has no known vulnerability for okhttp 5.5.1, okio 3.18.2, ktor 3.6.1, commons-compress 1.28.0,
  commons-io 2.22.0, gson 2.15.0, libtorrent4j 2.1.0-39, kotlinx-serialization 1.12.0, coroutines 1.11.0, appcompat 1.8.0,
  core 1.19.1, yt-dlp 2026.10.26, curl_cffi 0.16.3 (2026-11-17). Pinned: iOS Python 3.14 b14 + curl_cffi v0.16.3; desktop
  ffmpeg by sha256. yt-dlp and deno: "latest" checked against the sha256 file of the same GitHub release (protects against a
  broken download, not against a hijacked upstream release; accepted).
- [x] FIXED 2026-11-17: the desktop ffmpeg on Windows and Linux was 4.4.1 (ffbinaries, stopped in 2021). Now the same
  `eugeneware/ffmpeg-static` b6.1.1 release as macOS, the `.gz` files (28 MB each, pinned sha256): Windows = gyan.dev 6.1.1
  essentials, Linux x64 = johnvansickle 7.0.2 (both builders are the ones ffmpeg.org links to). BtbN (auto latest) was turned
  down: 184 MB per download. An installed ffmpeg older than 6 is replaced at the next start (`ffmpegNeedsInstall`, also in the
  JVM app for Windows / Linux x64). Tested on Windows: a home with the old 4.4.1 updated itself to 6.1.1, no leftovers, and the
  new one merged h264 + aac with `-c copy` and made an mp3. Linux: the binary checked in WSL (version, encoders); the app's
  update path not run there.
- [x] Repo: no private key, GitHub / AWS / Google / Slack token in the whole history; no key files ever added (`cacert.pem` is the
  public CA list). CI: triggers are tag push and manual only (no `pull_request`, so no outside code on the self-hosted runners);
  every signing key comes from `secrets`.
- [x] FIXED 2026-11-17: actions were pinned by tag, not commit (`actions/*@v4`, `gradle/actions/setup-gradle@v4`, `wdzeng/edge-addon@v2`).
  `wdzeng/edge-addon` is third-party and gets the Edge secrets. Every `uses:` in `build.yml` and `pages.yml` now names the
  commit the tag pointed to on 2026-11-17, with the version as a comment (checkout v4.4.0, cache v4.3.0, setup-java v4.9.1,
  upload-artifact v4.6.2, setup-gradle v4.4.3, edge-addon v2.1.1, Pages actions). CI not run after it. `contents: write` is
  set for the whole workflow; the build jobs upload to the release themselves, so it is needed there (accepted).
- [x] Release files: `UpdateManager` refuses a release file with no `.sig` and one whose signature does not verify; GitHub's
  sha256 is checked first. New `UpdateSignatureTest` (jvmTest): empty / broken signature, a signature from another key and a
  missing file are all refused, 3/3. The native desktop `ecdsaP256Verify` is read, not tested.
- [x] Android: one exported component (`MainActivity`, needed for share / view / magnet / fetchjet links); a `fetchjet://` link
  only fills the Add dialog (the user starts it). Services and the FileProvider are not exported. `usesCleartextTraffic` is
  for plain http downloads only (the bridge is a raw socket). Backups leave out `network.json` and logs; cookies and the
  bridge token are Keystore-encrypted. Accepted: FileProvider shares `files-path "."` (not exported, only URIs the app grants).
  R8 release build: see CLAUDE.md (`-dontwarn` for commons-compress).

## 16. Pages that need a session: Zoom recordings on iOS (owner's report, 2026-11-16)

A Zoom cloud recording (`us06web.zoom.us/rec/play/...`, media on `ssrweb.zoom.us`) answers 403 unless the request carries
the session cookies set after the passcode (HttpOnly, session only) and a zoom.us `Referer`. Measured on the Mac: the
pasted link, the page's own link, curl / Python / Java / URLSession with every cookie readable from `document.cookie`: 403;
a `fetch` inside the page with credentials: 200. Firefox (Mac, Android) gives the extension the full cookies, so the app
downloads there. Safari on iOS gives the extension none (`cookies.getAll` returned 0 with the permission granted), so the
app got the link without them.

- [x] iOS: `fetchjet://browse?url=<page>` opens the page inside the app (`iosApp/iosApp/SwiftUI/Core/FJPageBrowser.swift`,
  a `WKWebView` shown from `FJRoot`, handled in `iOSApp.swift` before `IosFetchJetLinks`). It looks for the first
  `<audio>` / `<video>` with a direct link; "Start Download" hands the link, every cookie of the web view that matches it
  (HttpOnly too), the page as `Referer` and the web view's User-Agent to the Add dialog through `fetchjet://add?json=`.
  The passcode is typed once inside the app (Safari's session is not shared with apps).
- [x] Extension on iOS: links on `zoom.us` open that page in the app (`iosSessionPage` / `openAppAt` in `background.js`,
  list `IOS_SESSION_SITES`); when the cookies API gives nothing, the page's `document.cookie` is sent (`pageCookie` in
  `content.js`, same site only).
- [x] Extension, every browser: the page button also sits on Zoom's audio-only player (`zoomAudio` / `zoomHero` in
  `content.js`: the page has `<audio>`, no `<video>`) and sends the audio's own link.
- [x] Popup: the "Allow access to all sites" banner also hides for `*://*/*`, and on Safari when the open site is allowed
  (`hasSiteAccess` in `popup.js`). Safari grants access site by site, so the `<all_urls>` check alone kept it on screen.
  `options.js` has the old check.
- [x] iOS, every site: the page button's list has "Open this page in FetchJet" (`pageInAppRow` in `content.js`, message
  `openPageInApp` in `background.js`, texts `openPageInApp` / `openPageInAppSub` in the 15 languages): the page opens in
  the in-app page, for sites that need a login. On Safari for iPhone, Zoom plays the recording in a `<video>` holding an
  `.m4a`: the button now shows before it plays and says "audio" (`isZoomMedia`).
- Tested (iPhone 17 simulator): a local server whose media needs an HttpOnly session cookie and the page as Referer:
  the in-app page found the audio and the file was saved whole; the real Zoom page from Safari (page button > Audio) opened
  in the app, the owner typed the passcode, the download started with no 403 (4 MB of 51 MB, then paused).
  `content.sim.js` / `capture.sim.js` pass.
- iPad Pro 13" simulator: Safari shows the desktop Zoom page (`<audio>` 0x0 in a 606x341 player); the button shows once
  the extension has access to `us06web.zoom.us` (Safari asks per site). Owner-checked on the iPad. Firefox on macOS with
  the 3.5.0 build (temporary add-on): owner-checked. The Safari toolbar icon is now `toolbar16-64.png` (transparent).
- Not run: the Zoom download to its end, the Zoom audio button in Chrome / Edge / Firefox on Windows, the popup banner on a
  device where it stayed on screen.
- Open: no address bar or back button in the in-app page, no entry point inside the app, only `zoom.us` is listed.

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

## 17. Windows installers: a nicer MSI and a Setup.exe (owner's request, 2026-11-17)

The owner asked for an .msi and an .exe that look professional, know whether the same version is installed, and offer a
desktop shortcut with a checkbox.

- [x] MSI (`desktopNative/packaging/product.wxs`): a new page "Choose the extras" after the folder page with two checkboxes
  (desktop shortcut, Start menu; both on), and one line saying what is installed: the same version ("it will be installed
  again, a repair"), a newer one (it is replaced by the older one, as before: `AllowDowngrades`), or an older one (updated).
  Detection: three `OnlyDetect` rows in the Upgrade table (`FJ_SAME_VERSION_FOUND`, `FJ_NEWER_VERSION_FOUND`,
  `FJ_OLDER_VERSION_FOUND`). The last page has "Start FetchJet now" (checked; `WixShellExec`). The shortcut components now
  need the property to be exactly "1" (a "0" sent by Setup.exe counts as set in MSI). Images in the app's navy with the
  logo: `installer/WixUIDialog.bmp`, `installer/WixUIBanner.bmp` (made by `installer/make-art.py` from `res/fetchjet.ico` and
  the FetchJet font). `check-msi.ps1` passes on a test MSI (upgrade rows, shortcuts, 169 files extracted with the same
  SHA-256).
- [x] `FetchJet-<version>-setup.exe` (`packaging/bundle.wxs`, `build-setup.cmd`): a WiX Burn bundle around the MSI with its
  own theme (`installer/theme.xml`, `theme.wxl`): a navy header with the logo, "Install FetchJet <version>", the two
  checkboxes (passed to the MSI), the GPL link, Repair / Uninstall when the same version is installed, Burn's own refusal
  when a newer Setup.exe version is installed, and a Launch button at the end. The MSI inside is hidden from "Installed
  apps" (only the bundle shows). Built locally (26 MB); the screen was locked, so the windows were not seen.
- [x] CI: `ci-windows.ps1` builds Setup.exe after the MSI check; the release upload takes it with its `.sig`; the verify run
  keeps it as an artifact; the download page labels it "Setup (.exe)"; README updated. CI not run. The in-app updater
  still installs the MSI (unchanged).
- [ ] For the owner: run `FetchJet-1.5.0-setup.exe` and `FetchJet-1.5.0.msi` from `E:\tools\probes\installer-test\out` (built from
  the debug core, for looking only), on a clean account or a VM if possible: first install, again (same version), the
  checkboxes, Uninstall.
