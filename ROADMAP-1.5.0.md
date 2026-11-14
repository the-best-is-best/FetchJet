# FetchJet 1.5.0: work list

Not started. Written 2026-11-14 after a look at Mister Download Manager 0.5.2 (Rust + egui, GPL-3.0,
by Ahmed Nefzaoui; unpacked and read, its CLI run on a 64 MB file; its window and its video part were not
tested). The list below is what is worth having here. Each item says what the other app does, so we know
what "done" has to cover. Nothing is built yet.
Legend: [x] done and tested as said, [ ] not done or not tested yet.

## 1. Automation API and a fuller command line (first, owner's order 2026-11-14)

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

- [ ] CLI commands that talk to the running app: `status` (JSON), `pause`, `start`, `remove`, `delete` by
  download id. Off by default, turned on with a command, token protected like the bridge. A new endpoint goes
  into the bridge token check (see CLAUDE.md "Bridge pairing").
- [ ] Enough for a test on the real app with no mouse: add a download (bridge `/add`), read its state, speed
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
