# FetchJet 1.4.0: work list

1.3.0 (2026-11-08) and 1.3.1 (Store update check fix, store listings in 15 languages) are
released. This is the active work list now, on branch `release-v1.4.0`. Mark an item `[x]` when
the owner has tested it.

**State (2026-11-08):** list opened. Item 1 waits for the owner's Task Manager numbers.

## 1. Lower RAM use on desktop (medium)

Why: the owner compared with IDM (under 5 MB). FetchJet runs on the JVM with Compose / Skia, so
~100 MB is the floor and IDM's number cannot be matched, but the app holds much more than it needs
today.

- [ ] Measure first (owner, Task Manager > Details > Memory (private working set)): window open and
  idle, and hidden in the tray. Same on the MSI and the Store MSIX.
- [ ] JVM limits: `desktopApp/build.gradle.kts` passes only `-Dfetchjet.verboseLogs`, so the heap
  may grow to 1/4 of the RAM and is rarely given back. Try a max heap, a GC that returns free
  memory to Windows, and measure again.
- [ ] Hidden in the tray, the window is only made invisible (`main.kt` `Window(visible =
  isWindowVisible)`), so the whole UI and its Skia surfaces stay in memory. Dispose the window
  while hidden (downloads keep running), rebuild it on show. Check the Windows focus trick and the
  bridge `onShowAndFocus` still work.
- [ ] libtorrent: start the session only when a torrent exists, stop it when none is left.
- [ ] Measure again, write the before / after numbers here.

## 2. Carried over from 1.3.0 (checks)

- [x] Release notes in the app's language (1.3.0 item 6): an installed 1.3.0 in Arabic shows
  the 1.3.1 notes in Arabic in the update dialog (`release-notes-ar.md` on the release).
  Owner-tested 2026-11-08 with the iOS app under PlayCover.
- [ ] Browser extension inside the app: the in-app download of the signed .xpi from the published
  GitHub release (was not testable while 1.3.0 was a draft).
- [ ] Send to device (1.3.0 item 4) on a **real iPhone**: discovery, pairing, send both ways. If
  discovery fails there, "Connect by IP" and sends after pairing still work (unicast).
  Closer than the simulator: the IPA under PlayCover on the Mac (works, 2026-11-08), but it is
  not an iPhone's network sandbox.

## 3. External, waiting

- [ ] Edge Android listing: waiting for Microsoft's answer. Edge iOS: not tested
  (see `docs/edge-android-test-cases.docx`).
- [ ] Edge Add-ons from CI: the `EDGE_PRODUCT_ID` secret is missing, so CI cannot submit the Edge
  version (owner adds it).
