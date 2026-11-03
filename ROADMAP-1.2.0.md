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

- [x] Proxy (HTTP / SOCKS, with user and password), used by downloads, yt-dlp and torrents.
- [x] Saved logins per site (HTTP basic auth) for downloads that ask for a user and password.

  Built 2026-11-03 (Settings > Network, `core/network/NetworkSettings.kt`, saved in
  `network.json`). Checked on Windows against a local test proxy: HTTP and SOCKS5 with login (wrong
  password refused), the JDK stream client and OkHttp, yt-dlp `--proxy`, a basic-auth
  site (401 without the login, 200 with it). A56 (2026-11-03, phone -> PC over `adb reverse`):
  through WARP SOCKS5 the downloaded `api.ipify.org` said 104.28.245.1 (Cloudflare) and a 32 MB
  file completed; through the test HTTP proxy with login: direct file, basic-auth site, YouTube
  inspect + download (`www.youtube.com`, `googlevideo.com` CONNECTs), the Settings page (Off /
  SOCKS5 / Remove, live apply). Torrents: two leaks found and fixed: with an HTTP proxy libtorrent
  sent DHT / LSD / uTP around it (now off while an HTTP proxy is on), and web seeds used a plain
  `HttpURLConnection` (now the app's client, through the proxy, verified: Debian web seed via the
  proxy with login). WARP's SOCKS5 has no UDP (UDP ASSOCIATE answers 07), so magnets find no
  peers through WARP; libtorrent does send UDP through a SOCKS5 proxy that has it. Not seen: a
  tracker announce through the proxy (the Debian tracker does not answer from Egypt at all).
  Owner-verified site login on a real account (2026-11-03): a private GitHub repo file
  (`raw.githubusercontent.com`, user + fine-grained token) downloads with the saved login.
  Found while testing, fixed 2026-11-03: libtorrent (desktop / Android) dropped a .torrent's own
  trackers and web seeds (libtorrent 2.1 keeps them out of `TorrentInfo`); now read with
  `load_torrent_parsed` and added (`Libtorrent4jBackend.trackersAndWebSeeds`). Verified on the Mac
  desktop with a local tracker (announce received), and with public trackers (WARP off): a tiny
  torrent's own `tracker.bittor.pw` and `tracker.qu.ax` answered ok.
  iOS (Mac simulator, 2026-11-03, `test_proxies.py`): builds; HTTP proxy with login: the update
  check, an 8 MB file (`media.githubusercontent.com`), YouTube inspect + download (
  `www.youtube.com`,
  `manifest.googlevideo.com`, `rr*.googlevideo.com`), plain `http://` (`GET` through the proxy);
  SOCKS5 with login: update check + 4 MB file; wrong proxy password: the task fails (raw iOS text
  "kCFErrorDomainCFNetwork Code=310", not a friendly message); site login with the proxy Off: 200,
  without it 401. Two iOS bugs found and fixed: (1) the background NSURLSession engine never sent
  a saved site login (only User-Agent): a task whose host has a saved login now goes to the
  foreground engine, like a proxy (`IosHybridHttpEngine.hasSiteLogin`); (2) `getIosCookieFile`
  took any `.txt` in Documents that mentions youtube.com as a cookies file (a links list then
  broke every yt-dlp call): now only a real Netscape cookies file is used
  (`looksLikeNetscapeCookies`). iOS torrents still do not use the proxy (owner's call).

## 6b. Security review (2026-11-03, owner's request: "make secure all data")

Done (not committed):

- [x] Secrets encrypted on disk: proxy / site passwords in `network.json` and each task's Cookie
  in `tasks_cache.json` (`core/security/Secrets.kt`, prefix `fjenc1:`). Key per platform:
  Android Keystore (AES-GCM), iOS Keychain (`ThisDeviceOnly`; app installed without a signing
  identity -> key file in Library, no backup), macOS Keychain (`/usr/bin/security`), Windows
  DPAPI (PowerShell, `~/.fetch_jet/secrets.key`), Linux `secret-tool` or a 600 key file. An old
  plain file is encrypted at the first start. Mac desktop + iOS simulator verified.
- [x] iOS settings moved from `Documents/settings` (visible in Files) to Application Support.
- [x] Cookie jar filed under the link's site, not the page's (a page could get another site's
  cookies); shared hosts (`github.io`...) are separate sites. Cookie dropped when a redirect
  leaves the site (Ktor drops only Authorization): `RedirectCookieGuard`. Verified on the Mac.
- [x] Logs: iOS `[yt-dlp] run:` hides proxy login / Cookie; Android 403 log hides Cookie / auth.
- [x] Update file checked against GitHub's SHA-256 `digest` (API and release page); only links of
  FetchJet's releases are used. yt-dlp / deno are not installed when their checksum is missing.
- [x] Desktop `~/.fetch_jet` and the Linux temp folder are private (700); `.bak` deleted with
  the task list; Android backups leave out `network.json` and logs; Android / iOS file names
  `.` / `..` / control characters rejected; extension ignores script clicks (`isTrusted`);
  iOS `fetchjet://add` with several links opens the link list instead of starting them.
- [x] Android Keystore on the A56 (2026-11-03, debug build): a plain `network.json` was
  encrypted at start, no decrypt error after restarts, and the saved site login answered
  `auth-ok` (127.0.0.1 through `adb reverse`).
- [ ] Windows DPAPI not run yet: test that a saved proxy password still works after a restart.

Open, owner's decision:

- [x] Update signing with the owner's own key (free; no Apple / Windows certificate, owner has
  no budget for those): ECDSA P-256. CI runs `scripts/sign-release-file.sh` in every upload step
  and uploads `<file>.sig`; `UpdateManager` refuses a file with no `.sig` or a wrong one
  (`verifyUpdateSignature`, public keys in `core/update/UpdateSignature.kt`, list so a key can
  be rotated). Key made 2026-11-03 on the Mac: `~/Documents/FetchJet-keys/update-signing-key.pem`
  (private, owner keeps a backup off the Mac) -> repository secret `UPDATE_SIGNING_KEY` (added
  2026-11-03). Without
  the secret the upload steps fail on purpose. Checked: openssl signature verifies with the
  app's Java code, one changed byte fails it. 1.1.0 and older have no signature check, so the
  first release that is enforced is the update after 1.2.0.
- [ ] macOS / Windows "unknown developer" warnings stay (needs paid certificates).
- [x] Bridge pairing token (owner chose it): `core/network/BridgePairing.kt`. Desktop and Android
  bridges need `X-FetchJet-Token` on `/add`, `/links`, `/inspect`, `/download`, `/show` (401
  "pairing required" without it; `/ping`, `/config` stay open, `/config` says `"pairing":true`
  and `"paired"`). The extension (`appFetch` / `pairWithApp` in background.js) calls
  `POST /pair` on a 401; the app shows "Allow this browser extension?" (`BridgePairingDialog`,
  15 languages) and answers the token (kept encrypted in `bridge.json`). An extension from before
  this change (no `X-FetchJet-Pairing` header) makes the app ask once; its origin is remembered
  (`legacyOrigins`), to be dropped once the stores have the new extension. A second desktop
  instance reads the token itself. Tested on the Mac with curl: no / wrong token 401, a web
  page origin cannot pair (403), the dialog shows, no answer in 2 min = refused. After Allow:
  `/pair` answered the token, `/links` with it = 200, `/config` `"paired":true`. Owner-tested in
  Firefox on the Mac
  (temporary add-on, 2026-11-03): Allow shown, link reached the app. A56: no token = 401, `/pair`
  showed the
  dialog, Allow (tapped through adb) gave the token, `/links` with it = 200. Not tested yet:
  Chrome / Edge, the old store extension.
- [x] Desktop: Cookie / Authorization headers and the proxy login go to yt-dlp in a 600 config
  file (`--config-locations`, `YtDlpSecretArgs.jvm.kt`, deleted when yt-dlp exits), not on the
  command line (`ps` showed them to every user). Checked with `ps` on the Mac.
- [x] Log files (desktop `app.log`, Android exported log) go through `LogRedact`: Cookie /
  Authorization values, `user:pass@` and URL query values become `***` / `…`.
- [x] `tasks_cache.json`: task links (`url`, `originalUrl`) are encrypted too (signed tokens).
  1.1.0 cannot read a 1.2.0 task list any more (downgrade only).
- [x] libtorrent: the library inside the app is written to the tools folder at every start and
  loaded from there (before, a file already in that user-writable folder was loaded as is).
- [ ] ffmpeg (ffbinaries) download has no checksum (needs one download per OS to pin the hash).

## 6c. yt-dlp start time on desktop (2026-11-03)

- [x] Measured on the Mac: `yt-dlp --version` 17-36 s, a SoundCloud `-j` 37.5 s of which ~2 s
  is real work. The one-file build (`yt-dlp_macos`) unpacks itself into a temp folder on every
  run (22 leftover `_MEI*` folders in `/var/folders`). Desktop now installs the unpacked build
  (`yt-dlp_macos.zip`, `yt-dlp_win.zip` / `_win_arm64` / `_win_x86`, `yt-dlp_linux.zip` /
  `_aarch64` / `_armv7l`; 32-bit x86 Linux keeps the zipapp) into `tools/yt-dlp-dir`
  (`installYtDlpFolder`: SHA2-256SUMS check, unpack beside, `--version` check, folder swap).
  `getExistingYtDlpBinary` prefers it; a one-file install switches once at the next start.
  Android / iOS are not affected (Python file, no unpacking).
- [x] Inspection / media downloads no longer wait for a tool update on desktop and iOS
  (`toolUpdateSwapsAtomically`); Android still waits (its update replaces files in place).
- [x] A yt-dlp whose `--version` cannot be read in time counts as up to date (before: a 37 MB
  re-download at every start when the Mac was busy).
- [x] Mac verified 2026-11-03: the app switched to `tools/yt-dlp-dir` by itself (the one-file
  copy deleted); `--version` 0.3-0.5 s (was 17-36 s); owner: SoundCloud inspect fast now. The
  first run of freshly unpacked files takes ~16 s (macOS checks them once), so the install's
  start check waits up to 180 s (with 30 s it failed while the app was starting).
- [ ] Windows and Linux not run yet.

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
