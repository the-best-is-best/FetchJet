# Handoff: where 2.0.0 stands (2026-11-22, end of the long session)

Read `CLAUDE.md` first (owner rules: Egyptian Arabic, masculine forms, short; never commit / push / reset / change branches
without his word; no code comments; no speculative edits in the tree). Branch `release-v2.0.0`. The owner runs the release himself
tomorrow (2026-11-23): never start CI.

## What this last commit contains

- Queue: unique task id (a task could vanish in a 50-file queue), the slot check and start are atomic (`pumpLock`), the limit of
  simultaneous downloads is never passed, two downloads that end on the same file name in one folder get `name (1).ext`
  (`HttpChunkNameClaims.kt`; a file on disk that no task owns is kept when the server picked the name).
- iOS: the merged task flow of `IosHybridHttpEngine` is a live `StateFlow`, `IosBackgroundHttpEngine` delegate queue is serial (two
  finishes took the same free name). Built and checked on the Mac simulator by the Mac session.
- Add dialog: the Direct / Media choice shows for any web page and sits above the "Find files on this page" card; that card shows
  only with Direct (`canGrabPage` needs `detectedEngine == HTTP_DIRECT`).
- Device sync (native desktop): `KtorSyncNetwork.listen` uses `reuseAddress` outside Windows (the TCP port stayed in TIME_WAIT for
  ~30 s, a reopened app took a random port and pairing by IP failed; it was also the cause of the on-and-off S2 / S4 failures in
  `test_send_subtitles` on Linux).
- Tests: `test_samename`, `test_slots`, `test_learned`, `test_links` (same-name section), `test_grab` (a plain page shows the card and
  the engine choice), `desktopNative/tests/bench/` (benchmark scripts; see "Open" below).
- Three old code comments no longer name an adult site (`CLAUDE_HANDOFF.md`, `NativeMediaExtractorBridge.swift`,
  `ios-prepare-frameworks.sh`).
- `ROADMAP-2.1.0.md`: hidden downloads (PIN) and "open the app directly" extension option, decided for 2.1.0.

## Test state on this tree

- Windows (release core and host, 19 corehost files + `jvmTest` 297 + `assembleDebug` / `assembleRelease`): all passed. `stress_queue.py`
  100 runs, 0 lost tasks.
- Linux (WSL): core builds, corehost files passed, `test_send_subtitles` 8/8 over two runs after the `reuseAddress` fix.
- Android emulator: add dialog order, More page, 10 links with the same name, a 20-file queue (never more than 3 at once), dialog empties
  on close: all passed. The A56 has the debug build.
- Mac: the patch with the iOS fixes and the same-name rule was built and tested by the Mac session (`jvmTest` 297, macOS core + dmg,
  iPhone 17 / iPad, the macOS window with the More page). The Mac must `git fetch` and drop its own uncommitted copy of these changes
  (they are in this commit) before pulling.

## Open

1. Release tomorrow (owner): merge `release-v2.0.0` into `master` (the privacy page is published from `master`), run the CI release with
   `2.0.0`, update the Microsoft Store privacy text (the daily anonymous usage ping), read the 15 release notes
   (`release-notes/2.0.0/`, the English one was written first, the other 14 are machine translations).
2. `desktopNative/tests/bench/` was pushed by mistake in `2170412` (the owner said it must not be pushed): it names a competitor (MDM) and
   has paths of this PC. Options: `git rm -r` it in a new commit, or amend the tip of `release-v2.0.0` and force-push. Waiting for his
   choice (`stress_queue.py` is useful for the queue tests).
3. History: the word of an adult site was in the root commit comments (`a13768e`). A script that removes it from the whole history
   (git filter-repo, force-push, every SHA and tag changes) is at `E:\tbib\purge-history.sh`, never run; the owner decides, and
   not before the release.
4. A link of a site whose yt-dlp extractor needs a blocked host (example: a TNAFlix video page; its metadata comes from
   `cdn-fck.tnaflix.com`, which resets the connection on this network and on the phone at times): yt-dlp waits ~22 s and fails, then the
   page fallback shows ONE generic "High Quality HD Stream" (the first `<source>` mp4 of the page) although the page lists 144p to
   720p links (`...-720p.mp4`). Proposal, not implemented: fail over to the page fallback sooner on a connection error, and let
   `extractMultipleWebQualities` (`MediaPageScraping.kt`) read every `-NNNp.mp4` link with its size. The owner has not said go.
5. `LearnedMediaHosts` keeps the hosts where a Media inspect found qualities in `learned_hosts.txt` (settings folder, 500 lines).
   No screen lists or removes them (`forget` exists, unused). The owner cleared the file on the A56.
6. Not decided: a settings switch for the usage ping; the iPhone Duo compress-button idiom; adaptive connections per host.
7. The Extension 3.6.0 is unchanged. The A56 is the owner's phone: do not open the app or take screenshots on it unasked.
