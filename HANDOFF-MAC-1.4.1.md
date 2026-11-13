# 1.4.1: what the Mac still has to build and test

Written 2026-11-12 on the Windows PC, updated 2026-11-13 (last commit of the branch at that time: see `git log`). Everything below was written on Windows and never compiled for
macOS, iOS or iPadOS. Branch `release-v1.4.1`. Read `CLAUDE.md` first (owner rules).

## 0. Get the code

```
cd ~/Desktop/KMP/FetchJet
git fetch && git checkout release-v1.4.1 && git pull
git submodule update --init --recursive
```

## 1. iOS: rebuild FJTorrent first

`iosApp/FJTorrent/include/FJTorrent.h` and `FJTorrent.mm` got new methods (trackers, peers, add
tracker, recheck, magnet link, first / last pieces, pieces, uploaded bytes). The prebuilt
`iosApp/Frameworks/FJTorrent.xcframework` still has the old header, and
`scripts/ios-prepare-frameworks.sh` only builds when it is missing.

1. Delete `iosApp/Frameworks/FJTorrent.xcframework`.
2. Run `scripts/build-libtorrent-ios.command` (log: `build/libtorrent-ios.log`).
3. Likely compile complaints in `FJTorrent.mm`: the range-for over `ep.info_hashes`, the
   `p.flags & lt::peer_info::...` tests, `prioritize_pieces` with the pair vector.
4. `bash scripts/ios-dev-build.command all`.

Swift files touched (all need the build): `NativeTorrentBridge.swift`,
`SwiftUI/Tasks/TaskTorrentDetailsSection.swift` (new), `SwiftUI/Tasks/TaskDetailsScreen.swift`,
`FJDialogs.swift`, `AddSheetDialogs.swift`, `TaskRowView.swift`, `AddTorrentSection.swift`.
Kotlin iosMain touched: `IosTorrentBridge.kt`, `TorrentBackend.ios.kt`, `IosAppHost.kt`
(`displayUrl`), `IosFetchJetLinks.kt`, `WakeTimer.ios.kt`, `IdleSpeedLimit.ios.kt`,
`FtpPlatform.ios.kt`.

`DashboardUiEffect.CopyText` is new: `FJStore.swift` `handle(_:)` does not copy it yet (the torrent
section copies the magnet link itself with `UIPasteboard`). Add a case if another place needs it.

## 2. iOS / iPadOS tests (simulator: iPhone and iPad)

Torrent details (open the details of a running torrent):
1. The Torrent card shows ratio, uploaded, pieces and piece size, refreshing every 2 s.
2. Trackers: each row has a state chip; an error row shows its message.
3. Add tracker: a bad address shows "not a valid tracker address"; a valid `udp://` or `http://`
   one appears in the list and is announced (a local fake tracker confirms it).
4. Peers: address, client, percent, speeds; no web-seed rows.
5. First and last pieces first: on, change the file selection, the switch still holds after ~1 s; off.
6. Check again: the state goes to "Checking files" and comes back.
7. Copy magnet link: the pasteboard holds `magnet:?xt=urn:btih:...`; one "copied" toast.
8. A completed torrent and the edition without the bridge: "not available" text, no endless spinner.
9. Open torrent A, go back, open torrent B; switch the language while details are open.
10. Settings > BitTorrent: "Stop sharing at ratio" chips; a finished torrent stops uploading at it.

Direct downloads:
11. `ftp://` link (plain) downloads in parts and resumes. `ftps://` / `ftpes://` is NOT available
    on iOS (no TLS library, no curl): it must fail with a clear message, not hang.
12. An ftp link with a password: the password shows as `***` in the row, details, duplicate and
    clipboard dialogs.
13. A file that changes on the server mid-download stops with a clear message (local test server
    `desktopNative/tools/testserver/server.py`, option `flipat=4`).
14. A server checksum header (`digest=good` / `digest=bad` on the test server): good completes,
    bad is not saved.
15. A zip with a damaged entry is fetched again once (`wrongpart=3`).
16. Add dialog: the folder picked for a video link is remembered for videos only; the global
    default folder does not change. Check with "Sort into folders" on and off.
17. Add dialog: there is NO "Test the link" button on iOS yet (logic is shared; SwiftUI button
    not written).

## 3. macOS app

Build: `./gradlew :desktopNativeCore:linkReleaseSharedMacosArm64 -Pfetchjet.nativeDesktop=true`,
`desktopNative/torrent/build-mac.sh`, `desktopNative/packaging/mac/make-dmg.sh`.

Never compiled for macOS:
- `host/mac/platform_mac.mm`: tray click now toggles the window (right / option / control click
  opens the menu); ftp links accepted by drag and drop. Check: `[NSApp isActive]` after a status
  item click, `performClick` menu pop-up, right mouse up delivery.
- `DesktopOs.macos.kt` `userIdleMs()`: `CGEventSourceSecondsSinceLastEventType(
  kCGEventSourceStateCombinedSessionState, UInt.MAX_VALUE)`. Check the cinterop types.
- `WakeTimer.macos.kt`: unsupported stub (the row must be hidden).
- `fjtorrent` C++: seven new exports (`fjt_torrent_trackers`, `_peers`, `_add_tracker`, `_recheck`,
  `_magnet`, `_set_first_last`, `_pieces`); the mac export list is generated from `fjtorrent.def`.
- FTP over TLS uses the system `curl` (one curl per piece).
- IPv4 after a 403: not available on macOS (Darwin has no per-request control).

Tests on the Mac app:
1. Torrent section in the details pane (same list as iOS 1-10).
2. Keyboard in the list: arrows, Shift+arrows, Home / End, PageUp / PageDown, Enter, Alt+Enter,
   Cmd/Ctrl+R / P / O / E / N / F, F1. The list must scroll to the focused row.
3. Add dialog: "Test the link" for http(s) and ftp links.
4. Settings > Downloads: "Limit speed only while I use the computer" (leave the Mac idle 3 min).
5. Command line: `FetchJet.app/Contents/MacOS/FetchJet /d <url> /p <folder> /n`, `/a`, `/s`.
6. Window title shows "FetchJet 1.4.1 - ...".
7. Tray: one click opens / hides the window; double click does not toggle twice.
8. Esc in the Add dialog's link field closes the dialog; Alt+Enter and Esc close the details sheet and
   Cmd+N works right after (the focus goes back to the list).
9. A non-Latin keyboard (add Arabic or Russian in System Settings): Cmd+N, Cmd+V in a text field,
   Cmd+A / C / X. Slint matches the key's text, so the letters of each layout are listed in
   `ui/pages/TaskList.slint` and `ui/widgets/TextField.slint`; check that Cmd (not Ctrl) is the
   modifier these bindings use on macOS.
10. Light theme: the file-type badges (ZIP, MSIX, VID, AUD) are readable.

## 4. Safari extension (iOS and macOS)

Extension 3.4.0 (the three manifests and `EXTENSION_VERSION` match). Changed since 3.3: pairing messages,
"hide on this site", per-site button position that snaps to an edge, Google Drive "Original file",
private windows, POST downloads left in the browser, the browser keeps a download until the app has it,
a file link is taken after the page's own click code, a plain PDF link opens in the browser, "Download all
links" also finds `data-*` links, one video button per group of stacked videos (none on a pinned video
covered by page content), the button works with Tab / Enter, a Telegram Web note in 15 languages, the
launch tab (`fetchjet://`) stays until the app answers (desktop only; iOS sends through `fetchjet://add`).

Run in a real Chromium (a separate Chrome with the extension loaded, the real Vimeo page at seven scroll
positions, the app paired and receiving links) and seen in Edge. NOT run in Safari or Firefox. On the
Mac: load the Safari target, open a page with a video, check one button appears, Tab + Enter opens it,
a zip link click reaches the app and a plain PDF link stays in Safari. The Safari target copies the same
files at build time; after an incremental build re-embed the appex (see `CLAUDE.md`). The extension tests
and how to run them: `TESTING.md` section 10.

## 4b. Done on Windows and the A56 (no need to repeat)

Real keys and mouse on the native Windows window: F1, Alt+Enter, Ctrl+N / R / P / A / F, Delete, Home / End,
Esc, light theme, Arabic / Persian / Russian layouts, Test the link, the checksum field, a torrent with
details, add tracker, first / last pieces, copy magnet. Android A56: Test the link, BitTorrent settings,
a real torrent with 8 trackers, add tracker, first / last toggle; FTP download in parts (9 MB, SHA-256 equal to the
server's), a correct and a wrong checksum (the wrong one is refused and not saved), a wrong checksum header from the
server (refused), a file that changes on the server (stops with a clear message), FTPS / FTPES with a self-signed
certificate (refused: "The secure connection failed"; a real certificate was not tried). The test FTP server has
`--passive-range LOW-HIGH` so its data ports can be forwarded with `adb reverse`. The extension version is now 3.4.0.

## 4c. Linux (WSL) and the bridge port

Linux was run in WSL (19 corehost test files passed). It found that the bridge port 28450 could not be taken again
for about a minute after a close; `nativeapp/bridge/HttpListener.kt` now sets `reuseAddress` on every OS except
Windows. On the Mac: close the app and start it again at once; the log must say `bridge: listening on 127.0.0.1:28450`.
Run `test_bridge`, `test_unpair`, `test_launch` back to back.

## 5. Update the docs after the Mac run

Put the results in `CLAUDE.md` (Current state) and delete this file.
