# FetchJet 1.4.1: work list

Branch `release-v1.4.1` (cut from `master` = 1.4.0). Not released. Written 2026-11-13.
Legend: [x] done and tested as said, [ ] not done or not tested yet.

## Downloads

- [x] FTP / FTPS / FTPES: parts, resume, logins in the link or Settings > Network, a server without
  REST starts with one connection, a server that limits connections waits, wrong password and folder
  links are explained. Windows native `test_ftp.py` 34 checks; Android A56 (plain FTP 9 MB in parts,
  SHA-256 equal to the server's). FTPS with a self-signed certificate is refused with "The secure
  connection failed" (a real certificate was not tried). Desktop FTPS uses the system `curl`, one
  per piece. FTPS is not available on iOS / iPad (no TLS library, no curl).
- [x] A file that changes on the server stops the download with a clear message (If-Range / identity):
  `test_robust.py`, Android A56.
- [x] A slow last connection is replaced (`test_slowtail.py` 4 checks).
- [x] Checksums: a checksum typed in the Add dialog or sent by the server is checked before the file is
  saved; ZIP entries are CRC-checked and a damaged range is fetched again once
  (`test_integrity.py` 32 checks; Android A56 right and wrong checksum, wrong server header).
- [x] A site's connection limit is remembered for a day (`test_hostlimit.py` 8 checks).
- [x] IPv4 after a 403: Android, Linux and Windows (`WINHTTP_OPTION_RESOLUTION_HOSTNAME` = 165,
  tested by `winhelpers net`). Not on macOS (no per-request control).
- [x] Test the link in the Add dialog (`test_testlink.py` 51 checks; real window; Android A56).
- [x] A folder remembered per kind of file (Add dialog).
- [x] FTP on iOS / iPad (plain FTP in parts and resume, `ftps://` refused at once with a clear message, password `***`) and the macOS core (`test_ftp` 34 checks on the Mac, FTPS with `FETCHJET_FTPS_INSECURE=1`), 2026-11-13.
- [x] Folder per kind of file: a remembered folder no longer gets the type folder added again (`QueueManagerRepository.kt`), iPhone-tested.

## Torrents

- [x] Details (ratio, uploaded, pieces, trackers with state, peers, add tracker, check again, copy
  magnet, first and last pieces first): desktop real window with a local seeder, Android A56 with a
  real torrent (8 trackers, add tracker, toggle). `test_torrent_details.py` 28 checks.
- [x] Stop sharing at a ratio (Settings > BitTorrent), `TorrentStopRatioTest`.
- [x] A torrent that would write outside its folder is refused (`TorrentPathSafetyTest`).
- [x] Torrents behind an HTTP proxy: not a bug. An HTTP proxy cannot carry UDP and the app's public
  trackers are `udp://`; a hint in Settings > Network and in the magnet timeout error says so
  (`test_torrent_proxy.py` 51 checks).
- [x] iOS / iPad: `FJTorrent.xcframework` rebuilt, details section built and run on the iPhone 17 and iPad Pro simulators (card, trackers, add tracker, first / last pieces, copy magnet, language). Peers rows and a completed torrent not seen (no peer from the local seeder).
- [x] iOS: "Stop sharing at ratio" chips added to Settings > BitTorrent (were missing); iPhone-tested, not re-run on the iPad.

## Desktop

- [x] Command line `/d /p /f /a /n /s` (`test_launch.py` 33 checks).
- [x] Keyboard in the list (arrows, Shift, Home / End, Page Up / Down, Enter, Alt+Enter toggles the
  details, Ctrl+R / P / O / E / N / F / A, Delete, Esc), F1 shows them; they and copy / paste in every
  field work with Arabic, Persian and Russian layouts (`test_keys.py` 41 checks; real keys on the
  Windows window with the Arabic, Persian and Russian layouts).
- [x] The details sheet closes on Esc and Alt+Enter and gives the focus back to the list; Esc in the Add
  dialog's link field closes the dialog; file-type badges are readable in the light theme.
- [x] Speed limit only while the computer is in use (`test_idle_limit.py` 12 checks); wake the computer
  for scheduled downloads on Windows (`test_wake.py` 11 checks).
- [x] Version in the window title.
- [x] Linux (cross-built on Windows, run in WSL): `test_download` 29, `test_robust` 15, `test_slowtail` 4,
  `test_integrity` 32, `test_hostlimit` 8, `test_ftp` 34, `test_testlink` 51, `test_keys` 41, `test_bridge` 33,
  `test_unpair` 8, `test_launch` 33, `test_settings` 87, `test_home` 41, `test_add` 29, `test_network` 17,
  `test_torrent` 17, `test_torrent_details` 28, `test_torrent_proxy` 51, `test_idle_limit` 12: all passed.
- [x] Found on Linux: the bridge port was not reusable for about a minute after a close (no `SO_REUSEADDR`),
  so a quick restart of the app could not bind 28450 (`HttpListener.kt`). Fixed for Linux and macOS; Windows
  keeps it off on purpose (there it would let another program share the port). Back to back `test_bridge`,
  `test_unpair`, `test_launch` now pass on Linux; Windows unchanged (34 / 8 / 33).
- [x] macOS: `corehost` tests run on the Mac, 17 of 19 files pass (`test_slowtail` and the FTP pause check need a throttled server on a fast Mac, see CLAUDE.md); the bridge port is taken again at once after a close, checked on the real app too; app and DMG build.
- [x] macOS: 32 connections per host (the default 6 made two downloads from one server share 6; `test_home` 41/41 after it) and the Cmd symbol in the F1 list (compiled, not seen).
- [x] Add dialog: switching engine while an inspect hangs now cancels it and resets the state; no random `download_<n>` file name (empty and required for a Direct link with no name). Mac native tested, iOS built, Android not compiled here.
- [x] Tray click opens / hides the window on macOS (single click, double click, right-click menu): run on the Mac. Linux: not run.
- [ ] Idle time on macOS (`CGEventSource`): compiled, never run (no real 3 minute idle); Linux idle time is tested only through the fake value.
- [x] macOS window: real keys, Esc / focus, window title, "Test the link" (http and ftp), command line `/d /p /n`: run on the Mac.
- [ ] macOS: non-Latin keyboard layouts and the ZIP / VID / AUD badges in the light theme: not run.

## Browser extension 3.4.0

- [x] The link click is taken after the page's own code (bubble phase, nothing stopped); a PDF link opens in
  the browser unless it is marked `download`; "Download all links" also finds `download` links and
  `data-*` addresses.
- [x] Video button: keyboard (Tab, Enter / Space), sticks to an edge when dragged, one button per group
  of stacked or nested videos, none on a pinned video that opaque page content covers. Checked on the
  real Vimeo page in a separate Chrome at seven scroll positions: no floating button.
- [x] The launch tab (`fetchjet://`) stays until the app answers and comes to the front, so the browser's
  "Open FetchJet?" question is seen. Verified in a separate Chrome (the Store app started); the question
  itself only appears in a visible browser.
- [x] Pairing messages, retry after Allow, "Unpair all", Telegram Web note in 15 languages.
- [x] Tested with `extension/test/content.sim.js` (14 checks), `real.html` and `e2e.html` (pairing, zip and
  download links reach the app, a real PDF stays in the browser).
- [x] Safari on iOS (iPad simulator): 3.4.0 embedded, one video button, a zip click reaches the app, a plain PDF stays in Safari. Tab + Enter not run (code read). No macOS Safari target exists in the Xcode project.
- [ ] Firefox builds with the new behaviour; Edge / Chrome with the store build.
- [x] Decided not to do: the in-page "Part" panel (owner), native messaging, re-sending a form the
  browser posted (could repeat a purchase), the page-controls avoidance (it made the button jump on
  YouTube).

## Release

- [x] `release-notes/1.4.1/*.md` in 15 languages (translations not reviewed by a native speaker).
- [x] `EXTENSION_VERSION` and the three manifests = 3.4.0.
- [x] Mac / iOS / iPad builds and the tests that can run without a person at the window (2026-11-13). Left: the macOS window items above.
- [ ] The owner runs the release from GitHub Actions.
