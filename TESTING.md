# FetchJet: manual test checklist (1.2.0)

Every check here was written after a change on 2026-11-03 (security review, bridge pairing,
update signing, yt-dlp start time). Each has the commands to run it again. Status per platform
is in `ROADMAP-1.2.0.md` (items 6b and 6c); update it after a run.

Rules for running these:

- No big downloads: every test uses local servers or files of a few KB. The only large file is
  the yt-dlp zip (24-54 MB), downloaded once by the app itself.
- Clean up after a test: test tasks (`tasks_cache.json`), test files in Downloads, test logins in
  `network.json`, servers left running, `adb reverse` / `adb forward`.
- The desktop folder is `~/.fetch_jet` (Windows `%USERPROFILE%\.fetch_jet`). Android: `run-as
  com.michelle.raouf.fetch_jet` (debug build) and `files/settings/`. iOS simulator:
  `xcrun simctl get_app_container booted com.michelle.raouf.fetch-jet.full data` +
  `Library/Application Support/settings/`.
- Desktop dev run: `./gradlew :desktopApp:run`. Verbose logs: `FETCHJET_VERBOSE_LOGS=true` in the
  environment. Log: `~/.fetch_jet/logs/app.log`.

## 1. Bridge pairing (desktop, Android)

Every bridge call that does something (`/add`, `/links`, `/inspect`, `/download`, `/show`) needs
the header `X-FetchJet-Token`. `/ping` and `/config` stay open.

Desktop (port 28450) or Android (`adb forward tcp:28451 tcp:28450`, then port 28451):

```bash
B=http://127.0.0.1:28450
curl -s -o /dev/null -w "%{http_code}\n" -X POST $B/links -H 'X-FetchJet: 1' -d '[]'          # 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST $B/links -H 'X-FetchJet: 1' -H 'X-FetchJet-Token: x' -d '[]'  # 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST $B/pair -H 'X-FetchJet: 1' -H 'Origin: https://evil.example' # 403
curl -s $B/config                                                     # "pairing":true,"paired":false
# Pair: the app shows "Allow this browser extension?"; tap Allow within 2 minutes.
curl -s -X POST $B/pair -H 'X-FetchJet: 1' -H 'X-FetchJet-Pairing: 1' -H 'Origin: chrome-extension://fjtest'
# -> {"token":"<64 hex>"}; then /links with -H "X-FetchJet-Token: <token>" -> 200
```

- No answer for 2 minutes -> `refused` (403).
- Android: the Allow button can be tapped with `adb shell uiautomator dump` +
  `adb shell input tap x y`.
- Desktop on macOS: the token can also be read without the dialog:
  `java scripts/security-test/BridgeToken.java "$(security find-generic-password -s FetchJet -a secrets-key -w)"`.
- Real extension: build it (`extension/build.sh`), load `build/extension/chrome` (Edge / Chrome:
  Load unpacked) or `build/extension/firefox/manifest.json` (Firefox: `about:debugging` ->
  Load Temporary Add-on). The first link -> Allow -> the link reaches the app. A second link: no
  question. `storage.local` of the extension holds `appToken`.
- Old store extension (before pairing, no `X-FetchJet-Pairing` header): the app asks once
  (text "An older FetchJet extension ..."), the origin is saved in `bridge.json`
  `legacyOrigins`, later links pass.
- Clean up: delete `bridge.json` if a test printed the token (the app makes a new one).

Status: Mac curl + Firefox, A56 curl, Windows curl + Edge (unpacked, owner), Linux (WSL) curl.
Open: Chrome, old store extension, a browser on Linux.

## 2. Saved passwords encrypted (`network.json`)

Write a plain file, start the app, read it back:

```bash
cat > ~/.fetch_jet/network.json <<'J'
{"proxy":{"kind":"NONE","host":"127.0.0.1","port":18080,"user":"fj","password":"secret"},"logins":[{"site":"127.0.0.1","user":"site","password":"pw"}]}
J
# start the app (on iOS / Android the file is written at the first network use)
grep password ~/.fetch_jet/network.json        # both start with fjenc1:
```

Then prove the password still works: `python3 scripts/network-test/test_proxies.py /tmp/p.log`
(site `http://127.0.0.1:18090/x.bin`, user `site`, password `pw`), queue that link through the
bridge (with the token), restart the app, queue it again: the log shows `SITE /x.bin auth-ok`.
Android: `adb reverse tcp:18090 tcp:18090` first.

Key per platform: macOS Keychain item `FetchJet` / `secrets-key`; Windows DPAPI file
`.fetch_jet\secrets.key`; Linux `secret-tool` (or `secrets.key`, mode 600); Android Keystore
alias `fetchjet_secrets`; iOS Keychain, or `Library/Application Support/fetchjet-secrets.key` when
the app has no signing identity (unsigned dev build: the Keychain answers -34018).

Linux: test both key paths. With `secret-tool` and an unlocked keyring the app writes
`secrets.where` (no `secrets.key`); without a working keyring (`secret-tool` missing, or a
Secret Service with no unlocked login keyring: `store` then waits for a password prompt, the app
gives up after 15 s) it writes `secrets.key` mode 600. WSL: `printf pw | gnome-keyring-daemon
--replace --unlock --components=secrets --daemonize` makes an unlocked keyring. A plain
`network.json` is encrypted the first time the network settings are used (a download is
enough), not always right at start.

Status: Mac, iOS simulator (key file path), A56, Windows (DPAPI), Linux (WSL, keyring and key
file). Open: a real iPhone (Keychain path).

## 3. Task list encrypted (`tasks_cache.json`)

```bash
grep -o '"url":"[^"]\{0,14\}' ~/.fetch_jet/tasks_cache.json | sort -u      # fjenc1:...
grep -o '"hdrCookie":"[^"]\{0,14\}' ~/.fetch_jet/tasks_cache.json | sort -u
```

Restart the app: every task is still listed and resumes (its link was decrypted). A task list
from before encryption: after the first start both `tasks_cache.json` and `.bak` are encrypted
(`grep -c '"url":"http' tasks_cache.json.bak` -> 0).

Status: Mac, iOS simulator, Windows (also the `.bak` of a plain list), Linux.

## 4. Cookies and redirects

```bash
python3 scripts/security-test/redirect_server.py /tmp/redirect.log &
# queue through the bridge, with the token:
[{"url":"http://127.0.0.1:18300/r1","fileName":"fjsec-cross.bin","cookie":"sess=SECRET1"},
 {"url":"http://127.0.0.1:18300/s1","fileName":"fjsec-same.bin","cookie":"sess=SECRET2"}]
cat /tmp/redirect.log
```

Expected: `host=localhost:18300 path=/c1.bin cookie=None` (another site: no cookie, no login)
and `path=/k1.bin cookie=sess=SECRET2` (same site: kept).

Status: Mac, Windows, Linux.

## 5. Logs hide secrets

- Desktop: `~/.fetch_jet/logs/app.log` and Android's exported log go through `LogRedact`:
  `Cookie: ***`, `Authorization: ***`, `http://***@host`, URL query values `?sig=…`.
- Queue a link like `http://127.0.0.1:18090/x.bin?sig=FJSECRETQUERY` with cookie
  `sess=FJSECRETCOOKIE` (verbose build), then `grep -c FJSECRET ~/.fetch_jet/logs/app.log` -> 0.
- iOS: `[yt-dlp] run:` lines in the console show `--proxy http://***@...` and `Cookie:***`.

Status: the redaction code checked on the Mac with sample lines; A56 log clean (non-verbose);
Windows and Linux verbose: 0 hits (the direct engine logs no link; a media `/add` with a secret
query, cookie and referer logs `?sig=…` and `headers=[Cookie, Referer]`).

## 6. yt-dlp command line (desktop)

While yt-dlp runs with a cookie or a proxy login:

```bash
ps -axww -o args | grep '[y]t-dlp'     # must show --config-locations <file>, no cookie / password
```

The config file is mode 600 in the app's temp folder and is gone when yt-dlp exits.
Windows: `Get-CimInstance Win32_Process -Filter "Name='yt-dlp.exe'" | Select CommandLine` while
an inspect runs (poll every 150 ms); the config file is in `%TEMP%\FetchJetDownloads`.

Linux: `tr '\0' ' ' < /proc/<pid>/cmdline` of `yt-dlp_linux` while it runs.

Status: Mac, Windows (cookie and proxy login), Linux (cookie).

## 7. Update signing

```bash
UPDATE_SIGNING_KEY="$(cat ~/Documents/FetchJet-keys/update-signing-key.pem)" \
  bash scripts/sign-release-file.sh some-file.dmg        # -> some-file.dmg.sig
```

The app (`verifyUpdateSignature`) must accept the file and refuse it after one byte changes. In a
real release: every asset has a `.sig` next to it, and the in-app update of the following release
installs (a release without `.sig` -> "the release file is not signed").

Status: signature checked on the Mac with Java (same code). Open: the first real release with
`.sig` files, then an in-app update from it.

## 8. yt-dlp folder build (desktop)

After the app starts once with a network:

```bash
ls ~/.fetch_jet/tools/yt-dlp-dir                     # yt-dlp_macos (or yt-dlp.exe / yt-dlp_linux) + _internal
time ~/.fetch_jet/tools/yt-dlp-dir/yt-dlp_macos --version     # well under 1 s (one-file build: 17-36 s)
```

A SoundCloud / YouTube inspect is then a few seconds. The first run after unpacking is ~16 s on a
Mac (macOS checks the new files once). Windows:
`%USERPROFILE%\.fetch_jet\tools\yt-dlp-dir\yt-dlp.exe`.

Status: Mac, Windows (`--version` 0.46 s, one-file 1.8 s), Linux (WSL, fresh install: `--version`
0.3-0.4 s).

WSL notes: run the app from a copy of the repo inside WSL (rsync without `build/` and
`.gradle/`, then `sed -i 's/\r$//' gradlew`), so the Linux build does not mix with the Windows
build folders. If the app's window stays empty in WSLg, `wsl --shutdown` and start it again
(a WSLg problem, not the app's). `pkill -f compose.application` typed inside `bash -c '...'`
kills that shell too: put it in a script file.

## 9. Other checks

- A torrent's own trackers: a local HTTP tracker that answers `d8:intervali60e5:peers0:e` and
  logs each request, a tiny torrent with it as first tracker (`tiny_torrent.py t.torrent
  http://127.0.0.1:18101/announce ...`), Add the torrent's URL, Start: the tracker logs
  `event=started` with libtorrent's port. Android: `adb reverse` 18100 and 18101. Status: Mac,
  Windows, A56 (2026-11-04). If the verbose `[torrent ...]` line shows `listenPort=0`, libtorrent
  has no listen socket and announces nothing (seen twice on Windows, see ROADMAP item 6).
- Public trackers (no download): `python3 scripts/security-test/tiny_torrent.py p.torrent
  udp://tracker.bittor.pw:1337/announce udp://tracker.qu.ax:6969/announce`, serve it on 18100,
  queue it, verbose log `trackers:` shows both `ok`. Delete the task after.
- iOS settings in Application Support (not Documents); several `fetchjet://add?json=[...]`
  links open the Add dialog's list and start nothing.
- libtorrent: `~/.fetch_jet/tools/<version>-libtorrent4j.*` is written again at every start.
- Android backups: `network.json` and `logs/` are excluded (`backup_rules.xml`,
  `data_extraction_rules.xml`).

## 10. 1.4.1 checks (extension, FTP, Android)

Extension, without a browser (the click logic against stubs):

```
node extension/test/content.sim.js            # 14 checks
```

Extension in a real Chromium (a separate Chrome with a temporary profile, driven over the DevTools
protocol; the extension is loaded with `Extensions.loadUnpacked`, Chrome needs
`--enable-unsafe-extension-debugging`): serve `extension/` on 127.0.0.1:18460 and open
`/test/real.html` (stacked, separate, pinned and covered videos) or `/test/e2e.html` (links for the
app: a zip, a link marked `download`, a PDF served as `application/pdf`, a `data-url`). The first
click asks "Allow this browser extension?" in the app; run the app on a throw-away folder
(`FetchJet.exe --home <folder>`), a fresh home downloads the media tools (~240 MB) by itself.

FTP to a phone: the test FTP server picks its data ports at random, so start one with a range and
forward them:

```
python desktopNative/tools/testserver/ftp.py --port 2321 --passive-range 30000-30059
adb reverse tcp:2321 tcp:2321                 # and each port 30000-30059
# link: ftp://fj:secret@127.0.0.1:2321/pub/dir/9MB.bin
```

A phone cannot reach the PC's loopback ports for a local torrent seeder (the firewall stops the LAN
route); use a real torrent with a speed limit instead (Sintel, only the small subtitle files).
Test links that carry `ws=` are downloaded over the web seed and have empty torrent details.

## 11. Driving the real Windows window without the mouse (1.4.1)

The native Windows window exposes its controls to UI Automation (Slint accessibility), so a test can press buttons and fill fields from PowerShell without taking over the mouse. Close the Store FetchJet first: it holds the bridge port 28450 and a second instance exits. Use a throw-away home that already has the media tools (a fresh one downloads ~240 MB): `FetchJet.exe --home E:	oolsjhomepphome4`, env `FETCHJET_TORRENT_LOCAL_ONLY=1`.

- Load `UIAutomationClient`, find the window by process id, then: buttons support `InvokePattern` (e.g. `+  Add download`, note the two spaces), engine and schedule chips support `TogglePattern` only, fields support `ValuePattern.SetValue`.
- The Add dialog is virtualised: the controls below the fold (Start / Cancel) are not in the tree, so read the Start state from `tests/corehost/test_add.py` (`primaryEnabled`) or from a screenshot of a taller window.
- Screenshots: `CopyFromScreen` of the window rectangle after `SetProcessDPIAware`; make the window TOPMOST for the shot and remove it. `PrintWindow` returns stale pictures.
- `SendKeys` reaches the window (F1, Esc in a focused field) but did not open the Add dialog with Ctrl+N right after start (the list had no focus). On Linux (WSLg) `xdotool` clicks and keys are not delivered to the window at all: drive Linux with `--script <file>` (one UiAction JSON per line) and `tests/corehost` instead.
- Linux host: `desktopNative/host-build` links `libfjcore.so` built as `debugShared`; copy a fresh `releaseShared` core over it before a window run, or the window shows an old core (it showed `download_<n>` names).
- Core tests added in 1.4.1: `test_addname.py` (empty File name box, Start off, engine switch with a magnet; needs `FETCHJET_TORRENT_DLL`), `test_foldertype.py` (default `Video/`, a remembered folder used as it is, no `Video/Video`), `shots_addname.py` and `shots_playbtn.py` (screenshots of the Add dialog and of the ▶ / ↗ row buttons). FTP over TLS needs `FETCHJET_FTPS_INSECURE=1` against the self-signed test server.
- The window-size bug (slint#13537) is intermittent: 36 resizes in a loop did not show it.
