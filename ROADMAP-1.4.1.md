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
- [ ] FTP on iOS / iPad and the macOS native app: Mac session.

## Torrents

- [x] Details (ratio, uploaded, pieces, trackers with state, peers, add tracker, check again, copy
  magnet, first and last pieces first): desktop real window with a local seeder, Android A56 with a
  real torrent (8 trackers, add tracker, toggle). `test_torrent_details.py` 28 checks.
- [x] Stop sharing at a ratio (Settings > BitTorrent), `TorrentStopRatioTest`.
- [x] A torrent that would write outside its folder is refused (`TorrentPathSafetyTest`).
- [x] Torrents behind an HTTP proxy: not a bug. An HTTP proxy cannot carry UDP and the app's public
  trackers are `udp://`; a hint in Settings > Network and in the magnet timeout error says so
  (`test_torrent_proxy.py` 51 checks).
- [ ] iOS: `FJTorrent.xcframework` must be rebuilt (new methods), SwiftUI details section never built.

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
- [ ] Tray click opens / hides the window on Linux and macOS, idle time on macOS (`CGEventSource`),
  Linux idle time through `gdbus`: written, never run.

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
- [ ] Firefox and Safari builds with the new behaviour; Edge / Chrome with the store build.
- [x] Decided not to do: the in-page "Part" panel (owner), native messaging, re-sending a form the
  browser posted (could repeat a purchase), the page-controls avoidance (it made the button jump on
  YouTube).

## Release

- [x] `release-notes/1.4.1/*.md` in 15 languages (translations not reviewed by a native speaker).
- [x] `EXTENSION_VERSION` and the three manifests = 3.4.0.
- [ ] Mac / iOS / iPad / Linux builds and tests: `HANDOFF-MAC-1.4.1.md`.
- [ ] The owner runs the release from GitHub Actions.
