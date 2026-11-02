# FetchJet 1.2.0: work list

Branch `release-v1.2.0` (made from `master` after the 1.1.0 release, 2026-11-02).
Ordered by priority: work top to bottom. Mark an item `[x]` when the owner has tested it.

## 0. Branch setup

- [x] Version 1.2.0: `APP_VERSION = "1.2.0"` (`AppVersion.kt`), `MARKETING_VERSION = 1.2.0` and
  `CURRENT_PROJECT_VERSION = 4` (`iosApp/Configuration/Config.xcconfig`).

## 1. Updates reach every user

Why first: every later fix only helps users who can update.

- [x] When the GitHub API answers 403 / 429 (rate limit, often behind a VPN), read
  `github.com/<repo>/releases/latest` instead so the check still works.
  Done: `UpdateManager.fetchLatestFromWebPage` (tag from the redirect, files from
  `expanded_assets`, no notes). JVM test `UpdateWebPageTest` passes against GitHub.
  Not tested on a phone behind a VPN yet. Seen working on the Mac (2026-11-02): after many test
  checks the API answered 403 (60/hour limit used up); the app still found 1.1.0 and its .dmg
  from the web page and installed it. Its notes were empty then ("No release notes."); now
  fixed: CI also uploads the notes as `release-notes.md` (with the unchanged / beta markers) and
  the fallback reads that file, or the notes text on the release page for older releases
  (`notesFromReleasePage`, checked in `UpdateWebPageTest` against v1.1.0). The file path is
  used from the first release built with this `build.yml`.
- [x] Android: download the new APK inside the app and open the system installer
  (`REQUEST_INSTALL_PACKAGES` + `FileProvider`) instead of sending the user to the browser.
  Done: `PlatformUpdater.android` (`canInstallInApp = true`, opens "Install unknown apps"
  first when needed). Tested on the A56 with a 1.0.9 debug build: the auto check found 1.1.0,
  "Download & install" downloaded the APK in the app and opened Android's installer (Play
  Protect prompt). The last step fails only because debug and release keys differ; GitHub
  releases all use the same key. Users on 1.1.0 get it only from their next update.
- [x] macOS: download the .dmg in the app, then a script waits for FetchJet to quit, mounts it
  (`hdiutil attach`), replaces `/Applications/FetchJet.app` (`ditto`), detaches and opens the
  new app. A file our app downloads gets no quarantine flag, so no "could not verify" prompt.
  Done in `MacUpdater.kt` (only when `/Applications/FetchJet.app` is writable).
  Tested on the Mac (2026-11-02): a local 1.0.9 build in `/Applications` found 1.1.0 ("Check
  now"), "Download & install" downloaded the .dmg, quit, swapped the app and opened 1.1.0 by
  itself (~12 s after the download). Downloads list and settings kept, update log empty (no
  errors), DMG detached. The script now also deletes the empty mount folder and the downloaded
  .dmg (retested the same way: only the empty `fetchjet-update.log` stays). Retested again after
  splitting the desktop updater into `WindowsUpdater` / `MacUpdater` / `LinuxUpdater`: same result.
- [x] Linux: `.deb` install with `pkexec dpkg -i` (asks for the password), or replace the folder
  of the portable `.tar.gz` build. Done in `LinuxUpdater.kt`: `.deb` when the app runs
  from `/opt/` and `pkexec` + `dpkg` exist, else the `.tar.gz` folder swap. The folder-swap
  shell steps were tested in WSL (wait for quit, swap, relaunch, path with a space). The
  `.deb` path: counted as tested (owner's decision, 2026-11-02, no Linux desktop available);
  revisit only if a user reports a problem.
- [ ] Microsoft Store build: the Store itself says whether an update is waiting (StoreContext
  through PowerShell), the app tells the user and opens the Store page. Check works inside the
  installed MSIX; the dialog is seen only when a newer Store version exists.

## 2. Several links at once (Add dialog)

Why: the most visible gap against IDM / Ezoo, and small.

- [x] Paste many links (one per line, or mixed text) in the Add dialog: show them as a list with
  a checkbox each, then start all checked ones together.
- [x] Each link picks its own type (file, video, magnet), like the `/links` bridge does.
- [x] Import a `.txt` file of links (same list screen). Tested: A56, Mac desktop, iOS simulator.
- [x] Export the links of the selected downloads (or all) to a `.txt` file.

Done 2026-11-02. Two or more links in the link field (`extractLinks`, any separator, up to 500,
duplicates dropped) replace the engine / name / connections sections with `LinkListSection`
(📄 file, 🎬 video, 🧲 magnet, checkbox each, Select all); the button says "Download N links".
`addLinkList` queues each with its own engine: files as they are, videos with no format (best,
named after the video title), magnets with all files; links already in the list are skipped.
"📄 Import .txt" (`TextFilePicker`, desktop FileDialog / Android document picker / iOS
document picker) fills the field with the file's text. "Export" in the selection bar saves
`FetchJet links <date time>.txt` in the downloads folder (page link for videos).
Tested on the A56: 3 typed links (2 files + YouTube) -> list -> unchecking works -> all three
downloaded; Export wrote the 3 links. Import tested on the A56 (2026-11-02): the picker returned
a .txt with 3 links and all 3 were listed. Desktop import: the macOS file dialog opens.
iOS picker tested on the simulator (2026-11-02). Parser test: `ExtractLinksTest`.
Torrents in the list (owner's request): every `.torrent` / magnet link in the list is read
right away (`inspectListedTorrents`, one at a time) and its files show under its row with the
same `TorrentFilesSelectionCard` (checkbox + priority per file, Select all); the chosen files go
to `enqueueTask`. While a checked torrent is being read the button waits ("Inspecting...");
if it cannot be read the row says so and all its files download. Tested on the A56:
sintel.torrent listed its files; a magnet with a cut tracker failed after 60 s and showed the
message.
Videos in the list (owner's request, 2026-11-02): every video / audio link is read the same way
(`inspectListedVideos`, one at a time): the row shows the real title and a quality button
(`VideoQualityLine` in `AddDownloadLinkList.kt`, a dropdown with every quality and size); the
chosen quality goes to `enqueueTask(mediaQualities, customFileName = "<title>.<ext>")`. A video
that cannot be read shows "Failed to inspect media qualities" and a Retry button
(`OnLinkListRetryInspect`); it is never downloaded with a guessed quality: the Download button stays
disabled until it is retried successfully or unchecked (`LinkListEntry.needsRetry`, owner's rule).
Tested on the A56 with a broken YouTube id: error + Retry shown, "Download 2 links" disabled.
The Download button also stays disabled
("Inspecting...") while any checked video or torrent is still being read
(`LinkListEntry.isBeingInspected`, also checked in `addLinkList`). Tested on the A56 with a .txt
of a direct file + YouTube + SoundCloud + Sintel magnet: button disabled while reading, titles
"Me at the zoo" / "Flickermood", picked "Audio Only (M4A)" for YouTube -> `Me at the zoo.m4a`
(format 139), SoundCloud m4a, 4MB.bin and Sintel all started.
Desktop (Mac, Arabic UI): the list, its RTL layout and the import work; the video row failed to
read on a slow network: the log showed `yt-dlp timed out after 25 s: -j`, then the `-g` fallback
also timed out (58 s, one guessed quality). Fixed: the desktop inspect call (`-j`,
`MediaInspect.jvm.kt`) waits up to 90 s, and the list / single-link inspection limit is 120 s
(was 45). Retested through the bridge `/inspect`: yt-dlp took 36.8 s, 15 qualities. The list
itself in the UI not re-run (screen locked); it calls the same `inspectMediaQualities`.

## 3. Arabic

Why: the biggest gap for users in Egypt (Ezoo has 8 languages). Large, so after the quick wins.

- [x] Move the UI text to the owner's `kmpLocalization` library (not Compose resources):
  ~570 strings in `AppLocalization`, one `XxxProvider` per language. Done 2026-11-02, builds on
  desktop + Android.
- [x] Arabic + 13 more languages (fr de es pt it ru tr id zh ja ko hi fa), right-to-left for
  ar/fa, language setting (System / each language) in Settings > General. Arabic tested on the
  A56 (RTL, saved after restart) and on the Mac desktop (home, Add dialog, link list RTL; sizes
  stay left-to-right). Owner to review the Arabic wording; iOS build on the Mac.

## 4. When downloads finish (desktop)

- [x] Option: do nothing / exit FetchJet / sleep / shut down the computer when all downloads
  finish. 60 s countdown with Cancel before sleep or shut down.
- [ ] Windows, macOS, Linux. Not on Android / iOS.

Done 2026-11-02. Owner-tested on Windows: Exit FetchJet (countdown dialog, Cancel, Now). Sleep /
shut down not run on purpose (they really turn the computer off). `core/power/WhenDone.kt` (choice,
countdown, trigger: the
last running download leaves QUEUED/CONNECTING/DOWNLOADING and at least one of them COMPLETED;
pausing everything does not trigger; a new download during the countdown stops it). The choice
is not saved (this session only) and goes back to "Do nothing" after it runs or on Cancel.
Commands in `SystemPower.jvm.kt`: Windows `shutdown /s /t 0` and PowerShell
`SetSuspendState('Suspend')`; macOS `pmset sleepnow` and `osascript ... System Events to shut
down` (may ask for Automation permission the first time: test on the Mac); Linux `systemctl
suspend|poweroff`. UI: Settings > General card, tray submenu, a banner above the list with
Cancel, and the countdown dialog (Cancel / Now) that brings the window forward. Unit test
`WhenDoneTest` (4 cases) passes.

## 5. Keyboard selection (desktop)

- [x] Ctrl+click, Shift+click (range), Ctrl+A, Delete key, Esc to clear, on top of the
  checkboxes added in 1.1.0.

Done 2026-11-02. Owner-tested on Windows: Shift+click range, Ctrl+click toggle, Ctrl+A, Esc.
Kept in commonMain on purpose (a keyboard on a phone or tablet works too). `TaskList.kt`: the list
column is focusable and takes
the keys (`onKeyEvent`, so a focused search field keeps its own Ctrl+A / Delete); Ctrl/Cmd+A
checks every shown row (collapsed groups excluded), Esc clears, Delete (or Cmd+Backspace) opens
the remove dialog for the checked rows, or for the open row when none is checked. `TaskRow`
reads `LocalWindowInfo.keyboardModifiers`: Ctrl/Cmd+click toggles a row, Shift+click checks the
range from the last clicked row in the order shown.

## 6. Network settings

- [ ] Proxy (HTTP / SOCKS, with user and password), used by downloads, yt-dlp and torrents.
- [ ] Saved logins per site (HTTP basic auth) for downloads that ask for a user and password.

## 7. Clean-up and older backlog

- [ ] `MediaStreamDownloader`: remove the Invidious / Cobalt / Piped resolvers; stream bodies with
  `prepareGet().execute` instead of `client.get`.
- [ ] Extension: playlist queue (`/playlist`).
- [ ] Merge a captured video-only + audio-only pair inside the app.

## Checks still open from 1.1.0 (owner, any time)

- [x] iOS: build 1.1.0 on the Mac (clean code, kotlinx.serialization, navigationevent).
- [ ] Microsoft Store MSIX: starts with Windows by default (install, open once, restart).
- [ ] Edge Android listing: waiting for Microsoft's answer. Edge iOS: not tested.
- [x] Udemy logged in on a real iPhone: counted as done (owner's decision, no iPhone; works on
  the simulator). Do not ask again.
