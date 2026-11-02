# FetchJet 1.2.0: work list

Branch `release-v1.2.0` (made from `master` after the 1.1.0 release, 2026-11-02).
Ordered by priority: work top to bottom. Mark an item `[x]` when the owner has tested it.

## 0. Branch setup

- [x] Version 1.2.0: `APP_VERSION = "1.2.0"` (`AppVersion.kt`), `MARKETING_VERSION = 1.2.0` and
  `CURRENT_PROJECT_VERSION = 4` (`iosApp/Configuration/Config.xcconfig`).

## 1. Updates reach every user

Why first: every later fix only helps users who can update.

- [ ] When the GitHub API answers 403 / 429 (rate limit, often behind a VPN), read
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
- [ ] macOS: download the .dmg in the app, then a script waits for FetchJet to quit, mounts it
  (`hdiutil attach`), replaces `/Applications/FetchJet.app` (`ditto`), detaches and opens the
  new app. A file our app downloads gets no quarantine flag, so no "could not verify" prompt.
  Done in `MacUpdater.kt` (only when `/Applications/FetchJet.app` is writable).
  Tested on the Mac (2026-11-02): a local 1.0.9 build in `/Applications` found 1.1.0 ("Check
  now"), "Download & install" downloaded the .dmg, quit, swapped the app and opened 1.1.0 by
  itself (~12 s after the download). Downloads list and settings kept, update log empty (no
  errors), DMG detached. The script now also deletes the empty mount folder and the downloaded
  .dmg (retested the same way: only the empty `fetchjet-update.log` stays). Retested again after
  splitting the desktop updater into `WindowsUpdater` / `MacUpdater` / `LinuxUpdater`: same result.
- [ ] Linux: `.deb` install with `pkexec dpkg -i` (asks for the password), or replace the folder
  of the portable `.tar.gz` build. Done in `LinuxUpdater.kt`: `.deb` when the app runs
  from `/opt/` and `pkexec` + `dpkg` exist, else the `.tar.gz` folder swap. The folder-swap
  shell steps were tested in WSL (wait for quit, swap, relaunch, path with a space). The
  `.deb` path needs a desktop Linux with a password prompt.
- [ ] Microsoft Store build: the Store itself says whether an update is waiting (StoreContext
  through PowerShell), the app tells the user and opens the Store page. Check works inside the
  installed MSIX; the dialog is seen only when a newer Store version exists.

## 2. Several links at once (Add dialog)

Why: the most visible gap against IDM / Ezoo, and small.

- [ ] Paste many links (one per line, or mixed text) in the Add dialog: show them as a list with
  a checkbox each, then start all checked ones together.
- [ ] Each link picks its own type (file, video, magnet), like the `/links` bridge does.
- [ ] Import a `.txt` file of links (same list screen).
- [ ] Export the links of the selected downloads (or all) to a `.txt` file.

## 3. Arabic

Why: the biggest gap for users in Egypt (Ezoo has 8 languages). Large, so after the quick wins.

- [ ] Move the UI text (~350 strings in ~20 files) to Compose string resources.
- [ ] Arabic translation and right-to-left layout. Language setting (system / English / Arabic).

## 4. When downloads finish (desktop)

- [ ] Option: do nothing / exit FetchJet / sleep / shut down the computer when all downloads
  finish. 60 s countdown with Cancel before sleep or shut down.
- [ ] Windows, macOS, Linux. Not on Android / iOS.

## 5. Keyboard selection (desktop)

- [ ] Ctrl+click, Shift+click (range), Ctrl+A, Delete key, Esc to clear, on top of the
  checkboxes added in 1.1.0.

## 6. Network settings

- [ ] Proxy (HTTP / SOCKS, with user and password), used by downloads, yt-dlp and torrents.
- [ ] Saved logins per site (HTTP basic auth) for downloads that ask for a user and password.

## 7. Clean-up and older backlog

- [ ] `MediaStreamDownloader`: remove the Invidious / Cobalt / Piped resolvers; stream bodies with
  `prepareGet().execute` instead of `client.get`.
- [ ] Extension: playlist queue (`/playlist`).
- [ ] Merge a captured video-only + audio-only pair inside the app.

## Checks still open from 1.1.0 (owner, any time)

- [ ] iOS: build 1.1.0 on the Mac (clean code, kotlinx.serialization, navigationevent).
- [ ] Microsoft Store MSIX: starts with Windows by default (install, open once, restart).
- [ ] Edge Android listing: waiting for Microsoft's answer. Edge iOS: not tested.
- [ ] Udemy logged in on a real iPhone.
