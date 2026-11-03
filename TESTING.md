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

Status: Mac curl + Firefox, A56 curl. Open: Edge / Chrome, old store extension, Windows.

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

Status: Mac, iOS simulator (key file path), A56. Open: Windows, a real iPhone (Keychain path).

## 3. Task list encrypted (`tasks_cache.json`)

```bash
grep -o '"url":"[^"]\{0,14\}' ~/.fetch_jet/tasks_cache.json | sort -u      # fjenc1:...
grep -o '"hdrCookie":"[^"]\{0,14\}' ~/.fetch_jet/tasks_cache.json | sort -u
```

Restart the app: every task is still listed and resumes (its link was decrypted).

Status: Mac, iOS simulator.

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

Status: Mac.

## 5. Logs hide secrets

- Desktop: `~/.fetch_jet/logs/app.log` and Android's exported log go through `LogRedact`:
  `Cookie: ***`, `Authorization: ***`, `http://***@host`, URL query values `?sig=…`.
- Queue a link like `http://127.0.0.1:18090/x.bin?sig=FJSECRETQUERY` with cookie
  `sess=FJSECRETCOOKIE` (verbose build), then `grep -c FJSECRET ~/.fetch_jet/logs/app.log` -> 0.
- iOS: `[yt-dlp] run:` lines in the console show `--proxy http://***@...` and `Cookie:***`.

Status: the redaction code checked on the Mac with sample lines; A56 log clean (non-verbose).

## 6. yt-dlp command line (desktop)

While yt-dlp runs with a cookie or a proxy login:

```bash
ps -axww -o args | grep '[y]t-dlp'     # must show --config-locations <file>, no cookie / password
```

The config file is mode 600 in the app's temp folder and is gone when yt-dlp exits.

Status: Mac.

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

Status: Mac. Open: Windows.

## 9. Other checks

- Public trackers (no download): `python3 scripts/security-test/tiny_torrent.py p.torrent
  udp://tracker.bittor.pw:1337/announce udp://tracker.qu.ax:6969/announce`, serve it on 18100,
  queue it, verbose log `trackers:` shows both `ok`. Delete the task after.
- iOS settings in Application Support (not Documents); several `fetchjet://add?json=[...]`
  links open the Add dialog's list and start nothing.
- libtorrent: `~/.fetch_jet/tools/<version>-libtorrent4j.*` is written again at every start.
- Android backups: `network.json` and `logs/` are excluded (`backup_rules.xml`,
  `data_extraction_rules.xml`).
