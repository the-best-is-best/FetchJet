# 10 - Desktop shell and packaging: what the JVM app does, for the native Windows host

Written 2026-11-09 from the sources on branch `desktop-native`. Nothing here was run as an app;
it is a code map, plus three facts checked on this PC (marked **checked**).

Short names used below:

| Short       | Full path                                                                     |
|-------------|-------------------------------------------------------------------------------|
| `main.kt`   | `desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt` (933 lines) |
| `LOGIC`     | `sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet`              |
| `JVM`       | `sharedLogic/src/jvmMain/kotlin/com/michelle/raouf/fetch_jet`                 |
| `UI`        | `androidUi/src/commonMain/kotlin/com/michelle/raouf/fetch_jet`                 |
| `build.yml` | `.github/workflows/build.yml`                                                 |

Things found while reading that the owner should know (details in the sections):

1. **`looksLikeLoginStart()` never returns true with Windows PowerShell 5.1.** It runs
   `powershell ... [Environment]::TickCount64`; that property does not exist in .NET Framework 4.x.
   **Checked** on this PC (PowerShell 5.1.26100, CLR 4.0.30319): the command prints nothing, so the
   function returns `false` and the Store build opens its window at login instead of staying in
   the tray. See 1.5.
2. **Opening a `.torrent` while FetchJet runs starts a second full instance** (code reading, not
   run): the path is forwarded to `/download`, which refuses anything that is not
   http/https/magnet (400), so the launcher falls through and skips the single-instance check.
   See 1.4.
3. **`isTrustedRequest` does not accept `safari-web-extension://`** while
   `BridgePairing.isExtensionOrigin` does. Desktop Safari requests with that Origin get 403.
   See 2.2.

---

## 1. Process start

Entry: `fun main(rawArgs: Array<String>)`, `main.kt:690-934`. Order of work:

| #  | What                                                                                                                                                           | Where                                                             |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| 1  | Windows only: if neither system property `skiko.renderApi` nor env `SKIKO_RENDER_API` is set, set `skiko.renderApi=SOFTWARE` (RAM saving, JVM only)            | `main.kt:691-695`                                                 |
| 2  | `installCrashLog()`                                                                                                                                            | `main.kt:696`, body `278-303`                                     |
| 3  | `LocalizationManager.start()` (registers 15 providers, default `en`, applies saved language)                                                                   | `main.kt:697`, `LOGIC/core/i18n/start/LocalizationStart.kt:45-57` |
| 4  | `AppLog.verbose` = env `FETCHJET_VERBOSE_LOGS` == "true" (ignore case), else system property `fetchjet.verboseLogs`, else false; prints `Verbose logs: <bool>` | `main.kt:700-704`                                                 |
| 5  | Taskbar icon from resource `launcher.png` (only matters for Gradle runs on macOS)                                                                              | `main.kt:707-716`                                                 |
| 6  | Daemon thread `browser-integration`: `BrowserIntegration.setUpOnFirstRun()` (section 6)                                                                        | `main.kt:719-723`                                                 |
| 7  | Login-start detection, stored in `StartupPreferences.launchedAtLogin`                                                                                          | `main.kt:726-731`                                                 |
| 8  | Drop `--autostart=true` from the arguments, take the **first** remaining argument only                                                                         | `main.kt:732-734`                                                 |
| 9  | If it parses to a link: try to hand it to a running instance and exit; else keep it as `pendingStartupUrl`                                                     | `main.kt:735-743`                                                 |
| 10 | No link: if another instance answers `/ping`, ask it to show itself and exit                                                                                   | `main.kt:745-747`                                                 |
| 11 | `startInTray` = started at login AND `StartupPreferences.startHidden` AND a system tray exists                                                                 | `main.kt:750-756`                                                 |
| 12 | Compose `application { ... }`: tray, window, bridge server                                                                                                     | `main.kt:758-933`                                                 |

### 1.1 Arguments

`fun parseUrlFromArg(arg: String): String` (`main.kt:51-94`). Returns the link / path, or `""`.

1. `clean` = `arg.trim().removeSurrounding("\"")`.
2. If `clean` starts with `fetchjet://` or `kortex://` (ignore case):
    - if there is a `?` with something after it: split the query on `&`, each part on the first
      `=`; the first key equal to `url` (ignore case) returns `URLDecoder.decode(value, "UTF-8")`
      (so `+` becomes a space), or the raw value when decoding throws;
    - else `remainder` = text after the scheme with leading `/` removed; if it starts with
      `http://` or `https://` (ignore case) return it.
3. If `clean` starts with `http://`, `https://` or `magnet:` (ignore case) return `clean`.
4. If `clean.lowercase()` ends with `.torrent` and `File(clean.removePrefix("file://"))` is a
   file: return its absolute path.
5. Else `""`.

What reaches the first argument on Windows:

| Source                                               | Argument                                                                                                                                                                        |
|------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Browser extension, app not running                   | `fetchjet://download?url=<encodeURIComponent(link)>` (`extension/background.js:18`, `393`). The url may be empty: then `parseUrlFromArg` returns `""` and it is a plain launch. |
| `.torrent` double-click (MSI ProgId, MSIX file type) | the file path                                                                                                                                                                   |
| `magnet:` link (MSIX build only, see 4.3)            | `magnet:?xt=...`                                                                                                                                                                |
| Login start, MSI build                               | `--autostart=true` (`AUTOSTART_ARG`, `LOGIC/core/startup/StartupLauncher.kt:28`)                                                                                                |
| Login start, MSIX build                              | no arguments                                                                                                                                                                    |
| Run alias `fetchjet.exe` (MSIX)                      | whatever the user typed                                                                                                                                                         |

`fetchjet://add?json=...` is iOS only; the desktop does not parse it.

### 1.2 Environment variables and system properties

| Name                                                        | Kind                                                                     | Used for                                                                            | Where                                                                                                                                     |
|-------------------------------------------------------------|--------------------------------------------------------------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `FETCHJET_VERBOSE_LOGS`                                     | env                                                                      | verbose logs for this run; wins over the property                                   | `main.kt:701`                                                                                                                             |
| `fetchjet.verboseLogs`                                      | JVM property, baked in by the launcher (`-Dfetchjet.verboseLogs=<bool>`) | verbose logs                                                                        | `main.kt:702`, `desktopApp/build.gradle.kts:77`                                                                                           |
| `skiko.renderApi` / `SKIKO_RENDER_API`                      | property / env                                                           | Compose renderer; JVM only, nothing to port                                         | `main.kt:691-695`                                                                                                                         |
| `jpackage.app-version`                                      | property set by the jpackage launcher                                    | `"version"` in `/config` (else `"dev"`)                                             | `main.kt:428`                                                                                                                             |
| `jpackage.app-path`                                         | property set by the jpackage launcher = full path of `FetchJet.exe`      | "installed app" test, updater relaunch, `fetchjet://` registration, Store detection | `JVM/core/update/DesktopUpdater.kt:32-33`, `JVM/core/browser/BrowserIntegration.jvm.kt:66`, `JVM/core/platform/WindowsStorePackage.kt:10` |
| `compose.application.resources.dir`, `user.dir`             | properties                                                               | fall-backs for Store detection                                                      | `WindowsStorePackage.kt:11-12`                                                                                                            |
| `user.home`                                                 | property                                                                 | app home `<home>/.fetch_jet`, Downloads / Documents / Desktop                       | `JVM/core/filesystem/PathResolver.jvm.kt:11-49`                                                                                           |
| `java.io.tmpdir`                                            | property                                                                 | `<tmp>/FetchJetDownloads`                                                           | `PathResolver.jvm.kt:51-62`                                                                                                               |
| `LOCALAPPDATA`, `ProgramFiles`, `ProgramFiles(x86)`, `PATH` | env                                                                      | browser detection                                                                   | `JVM/core/browser/DesktopBrowsers.kt:43-45`, `107`                                                                                        |
| `FETCHJET_SYNC_NO_BROADCAST`                                | env                                                                      | device sync test switch (not shell)                                                 | jvmAndroidMain device sync                                                                                                                |
| `FETCHJET_NMT`, `fetchjet.nmt`, `FETCHJET_JVM_ARGS`         | build time only                                                          | extra JVM args                                                                      | `desktopApp/build.gradle.kts:39-41`, `84-86`                                                                                              |

The native host has no `jpackage.*` properties: it must supply the exe path (`GetModuleFileNameW`)
and the version (`APP_VERSION`, `LOGIC/core/update/AppVersion.kt:11`,
now `"1.4.0"`) itself.

### 1.3 App home, temp folder, log file

- Settings folder: `File(user.home, ".fetch_jet")`, created when missing
  (`PathResolver.jvm.kt:43-49`). On Windows that is `%USERPROFILE%\.fetch_jet`. It is **not** under
  AppData, so the MSI build and the Store (MSIX) build share the same data (MSIX only redirects
  AppData and the registry). The native app must keep this exact folder so the user's tasks and
  settings carry over. Files the shell itself touches there: `logs/app.log`, `bridge.json`,
  `startup.properties`, `updates.properties`, `browser-integration.done`, `extension/chromium/`,
  `extension/firefox/`, `secrets.key`.
- Temp folder: `File(java.io.tmpdir, "FetchJetDownloads")` (`PathResolver.jvm.kt:51-55`), on
  Windows `%TEMP%\FetchJetDownloads`. Updates go to `...\FetchJetDownloads\fetchjet-update\`.
- Downloads / Documents / Desktop: `<home>\OneDrive\<name>` when that folder exists, else
  `<home>\<name>` (`PathResolver.jvm.kt:10-41`).
- Log: `installCrashLog()` (`main.kt:278-303`):
    - file `<user.home>/.fetch_jet/logs/app.log`; deleted at start when larger than
      `2 * 1024 * 1024` bytes; opened for append;
    - stdout and stderr are both replaced by a UTF-8 `PrintStream` (autoflush) over
      `RedactingOutputStream`
      (`sharedLogic/src/jvmAndroidMain/.../core/log/RedactingOutputStream.kt`),
      which passes every line through `LogRedact.redact` (cookies, logins, URL query values);
    - first line: `---- FetchJet started <LocalDateTime.now()> (java <version>) ----`;
    - default uncaught-exception handler prints `Uncaught exception in thread <name>:` and the
      stack trace to the same stream;
    - any failure is swallowed (the app starts without a log).
      All engine logging is plain `println`, so the native build needs the same redirect for
      Kotlin/Native `println` (and for the C++ host's own messages).

### 1.4 Single instance

There is no lock or mutex. The bridge port is the lock.

- **With a link** (`main.kt:735-743`): `trySendToRunningInstance(url)` (`main.kt:121-135`) does
  `GET http://127.0.0.1:28450/download?url=<URLEncoder.encode(url, "UTF-8")>` with headers
  `X-FetchJet: 1` and `X-FetchJet-Token: <BridgePairing.token>`, connect and read timeout 800 ms.
  HTTP 2xx = exit. Anything else: the link becomes `pendingStartupUrl` and **the single-instance
  check is skipped**.
- **Without a link** (`main.kt:745-747`): `isAnotherInstanceRunning()` (`main.kt:97-119`) does
  `GET /ping` with `X-FetchJet: 1`, timeouts 500 ms; the answer must be 2xx with body `PONG`
  (trimmed). Then, best effort, `GET /show` with `X-FetchJet: 1` and the token (500 ms), and exit.
- The token comes from `BridgePairing.token` (`LOGIC/core/network/BridgePairing.kt:83-91`): the
  second process reads `<home>/.fetch_jet/bridge.json` and decrypts it with the same user key
  (`Secrets`, AES-256-GCM, key in `secrets.key` protected by DPAPI through a PowerShell child,
  `JVM/core/security/WindowsKeyVault.kt:7-31`: the file is base64 of
  `ProtectedData.Protect(key, null, CurrentUser)`).
- The pending link is delivered after the window exists: `delay(600)`, then
  `ExternalDownloadBridge.dispatch(url, "")` and `triggerFocus()` (`main.kt:899-906`).

Weak points to fix in the native host rather than copy:

- A `.torrent` path is forwarded to `/download`, which only accepts http/https/magnet
  (`isAllowedUrl`, `main.kt:190-196`; 400 at `main.kt:668-671`). The launcher then starts a
  second full instance; its `HttpServer.create` fails to bind (caught and printed,
  `main.kt:685-687`) and two processes share `.fetch_jet`.
- Two starts in the same moment both miss `/ping` and both run.
- While the first instance is still starting (bridge not up yet) a second start is not detected.

Proposal: a named mutex (`Local\FetchJet.SingleInstance`) decides who is first; the second
process hands its argument over a channel that only the same user can open (named pipe or
`WM_COPYDATA` to a message-only window) and calls `AllowSetForegroundWindow` for the first
process. Keep answering `/ping` + `/show` + `/download` exactly as now so a JVM build and a
native build on the same PC still find each other.

### 1.5 Login-start detection

`startedAtLogin` (`main.kt:726-730`) is true when:

- any raw argument trimmed equals `--autostart=true` (MSI build, written by the Run key), or
- there are no arguments and `WindowsStorePackage.looksLikeLoginStart()` is true.

`WindowsStorePackage` (`JVM/core/platform/WindowsStorePackage.kt`):

- `isInstalled` (`:9-14`): the path (`jpackage.app-path`, else `compose.application.resources.dir`,
  else `user.dir`) contains `\WindowsApps\` (ignore case).
- `looksLikeLoginStart()` (`:20-40`): false when not the Store build; else runs
  `powershell -NoProfile -NonInteractive -Command [Environment]::TickCount64`, waits 5 s, takes the
  last non-blank output line as milliseconds since boot and returns `upMs < 2 * 60 * 1000`.
  **Checked:** Windows PowerShell 5.1 prints nothing for `TickCount64`, so this is always false.

Native: `GetTickCount64() < 120000` gives the intended behaviour without a child process. The
exact answer for the MSIX build is `AppInstance::GetActivatedEventArgs()` with
`ActivationKind::StartupTask` (C++/WinRT headers are in the installed Windows SDK 10.0.26100.0).

`StartupPreferences` (`LOGIC/core/startup/StartupLauncher.kt:31-65`): file
`<settings>/startup.properties`, key `startHidden` (default **true**), `launchedAtLogin` is a
volatile in-memory flag.

"Start at login" switch (`JVM/core/startup/StartupLauncher.jvm.kt`, library
`io.github.vinceglb:auto-launch` 0.9.0, source read from the Gradle cache):

- `isSupported` (`:15-20`): not the Store build AND the process executable is not `java`.
- `managedBySystemUri` (`:22-23`): `"ms-settings:startupapps"` in the Store build, else null. The
  UI then shows a button that opens it (`UI/ui/StartupUi.kt:25-54`).
- Registry: `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, value name
  `com.michelle.raouf.fetch_jet`, value (REG_SZ), exactly:
  `cmd /c start "com.michelle.raouf.fetch_jet" /D "<folder of exe>" "<exe path>" --autostart=true`
- `isEnabled()` compares the stored value with that exact string for the **current** exe path;
  `enable()` writes it (throws when the exe does not exist); `disable()` deletes the value.

The native app must use the same value name and, if `FetchJet.exe` stays at the same path, the
same string, so a switch the user turned on in the JVM build stays on.

---

## 2. Local bridge server, 127.0.0.1:28450

`fun startLocalHttpServer(onShowAndFocus: () -> Unit)` (`main.kt:305-688`), started from the
Compose `remember` block (`main.kt:798`).

- `com.sun.net.httpserver.HttpServer` bound to `InetAddress.getLoopbackAddress()`, port
  `BRIDGE_PORT = 28450` (`main.kt:138`), backlog 0 (`main.kt:307-308`). Plain HTTP/1.1.
- Executor: cached thread pool, daemon threads named `fetchjet-bridge` (`main.kt:679-683`); the
  comment explains why: `/ping` must answer while `/inspect` runs yt-dlp.
- A bind failure is caught and printed; the app runs without a bridge (`main.kt:685-687`).
- JDK contexts match by path prefix; a path with no context gets the JDK's 404. The extension
  relies on 404 to detect an old app (`extension/background.js:358`, `371`), so unknown paths
  must stay 404.

### 2.1 Reply helper

`HttpExchange.reply(status, body, contentType = "text/plain; charset=UTF-8")` (`main.kt:245-262`):

- always adds `Content-Type`;
- if the request has an `Origin` header **and** `isTrustedRequest` is true, adds
  `Access-Control-Allow-Origin: <that origin>` (echo, never `*`);
- status 204: headers only, no body;
- no `Vary`, no `Access-Control-Max-Age`, no credentials header.

`jsonEscape` (`main.kt:264-275`): escapes `\`, `"`, `\n`, `\r`, `\t`, other chars below 0x20 as
`\u00xx`.

### 2.2 Gates

| Gate                | Rule                                                                                                                                                                                                                            | Fails with                      | Where             |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|-------------------|
| `isTrustedRequest`  | `Host` header (lower-cased, port removed; `[...]` kept for IPv6) must be absent or one of `localhost`, `127.0.0.1`, `[::1]`. `Origin` must be absent or start with `chrome-extension://`, `moz-extension://` or `extension://`. | `403 forbidden` (text)          | `main.kt:203-214` |
| `X-FetchJet` header | must be present and not blank (a web page cannot add it without a CORS preflight, and only extension origins pass the preflight)                                                                                                | `403 missing X-FetchJet header` | per endpoint      |
| `isAuthorized`      | see below                                                                                                                                                                                                                       | `401 pairing required`          | `main.kt:223-243` |
| `isAllowedUrl`      | trimmed length <= 8192, no char below space, starts with `http://`, `https://` or `magnet:` (ignore case)                                                                                                                       | 400                             | `main.kt:190-196` |

`HttpExchange.isAuthorized(onShowAndFocus)` (`main.kt:223-243`):

1. `BridgePairing.isPaired(X-FetchJet-Token)` (constant-time compare,
   `BridgePairing.kt:105-112`) -> `BridgePairing.noteBrowser(User-Agent, now, X-FetchJet-Ext)`,
   allowed.
2. Else `BridgePairing.isLegacyAllowed(Origin)` -> allowed.
3. Else, when `Origin` is an extension origin, is not blocked, and the request has **no**
   `X-FetchJet-Pairing` header (an extension older than 1.2.0): start daemon thread `bridge-pair`
   running `BridgePairing.ask(origin, legacy = true)` and call `onShowAndFocus` on the UI thread.
4. Reply `401 pairing required`, not allowed.

`BRIDGE_ALLOWED_HEADERS` (`main.kt:216-217`), sent as `Access-Control-Allow-Headers` on
preflights:
`Content-Type, X-FetchJet, X-FetchJet-Cookie, X-FetchJet-Referer, X-FetchJet-Token, X-FetchJet-Pairing, X-FetchJet-Ext`

A new endpoint must be added to the token check and to this list (rule from CLAUDE.md).

### 2.3 Endpoints

All of them run `isTrustedRequest` first (403 `forbidden`).

| Path        | Methods                     | Preflight (`OPTIONS` -> 204)                     | Needs `X-FetchJet` | Needs token             | Handler           |
|-------------|-----------------------------|--------------------------------------------------|--------------------|-------------------------|-------------------|
| `/ping`     | any                         | `Allow-Methods: GET, OPTIONS` (no Allow-Headers) | no                 | no                      | `main.kt:310-320` |
| `/config`   | any                         | `GET, OPTIONS` + Allow-Headers                   | no                 | no                      | `main.kt:403-440` |
| `/pair`     | POST                        | `POST, OPTIONS` + Allow-Headers                  | yes                | no (it gives the token) | `main.kt:447-475` |
| `/inspect`  | any (meant GET)             | `GET, OPTIONS` + Allow-Headers                   | yes                | yes                     | `main.kt:322-400` |
| `/add`      | POST (else `405 POST only`) | `POST, OPTIONS` + Allow-Headers                  | yes                | yes                     | `main.kt:559-594` |
| `/links`    | POST (else 405)             | `POST, OPTIONS` + Allow-Headers                  | yes                | yes                     | `main.kt:596-624` |
| `/show`     | any                         | none (an OPTIONS falls into the header check)    | yes                | yes                     | `main.kt:626-632` |
| `/download` | any (meant GET)             | `GET, OPTIONS` + Allow-Headers                   | yes                | yes                     | `main.kt:634-676` |

`/add` and `/links` share `handlePreflightOrReject()` (`main.kt:537-557`): trusted -> OPTIONS ->
POST only -> `X-FetchJet` -> `isAuthorized`.

**`/ping`** -> `200`, body `PONG`, `text/plain; charset=UTF-8`.

**`/config`** -> `200 application/json; charset=UTF-8`. Before answering: when the token is
valid, or `Origin` is an extension origin (this check also accepts `safari-web-extension://`),
or `X-FetchJet-Ext` is not blank, call `BridgePairing.noteBrowser(User-Agent, now, X-FetchJet-Ext)`.
Body, one line, in this key order:

```json
{"app":"FetchJet","version":"<jpackage.app-version or dev>","bridge":2,"mediaExtractor":true,"torrent":true,"language":"<AppLanguage.current, e.g. en>","pairing":true,"pairedMerge":true,"coursePlaylist":true,"paired":<token valid>,"endpoints":["/ping","/config","/pair","/add","/links","/download","/inspect"]}
```

`mediaExtractor` / `torrent` are `AppFeatures.mediaExtractor` / `.torrent`
(`LOGIC/core/edition/AppEdition.kt:26-30`), both true on desktop. The extension polls `/config`
with a 1500 ms timeout and reads `language`, `pairedMerge`, `coursePlaylist`
(`extension/background.js:286-300`).

**`/pair`** (`main.kt:447-475`): requires method POST, `X-FetchJet`, and an `Origin` that
`BridgePairing.isExtensionOrigin` accepts; else `403 forbidden`. Then `onShowAndFocus` on the UI
thread and **the HTTP thread blocks** in `BridgePairing.ask(origin, legacy = false)` until the
user answers, at most 120 s (the extension waits 125 s, `background.js:241`).
Allowed: `noteBrowser(...)`, `200 {"token":"<token>"}` (`application/json`). Refused, blocked,
timed out, another dialog already open, or asked again within 8 s: `403 refused` (text).

**`/inspect?url=<percent-encoded>`** (`main.kt:322-400`):

- query split on `&`, key `url`, `URLDecoder.decode`;
- blank or not allowed -> `400 {"error":"Missing or unsupported url parameter"}`;
- request headers `X-FetchJet-Cookie` and `X-FetchJet-Referer` (trimmed, first 16384 chars, CR/LF
  removed) become `Cookie` / `Referer` for yt-dlp;
- `SiteCookieJar.remember(targetUrl, headers)`;
- `inspectCached(targetUrl, headers)` (`main.kt:158-187`): key =
  `MediaUrls.canonical(url)`; cache = access-ordered `LinkedHashMap`, max 40 entries, entry valid
  30 minutes and only when not empty; one gate object per key so two requests for the same video
  run yt-dlp once; a miss calls
  `QueueManagerRepository.getInstance().inspectMediaQualities(url, headers)` under `runBlocking`;
  the result is cached only when at least one entry has a non-blank `formatId`;
- `200` JSON array, one object per quality, keys in this order:
  `{"index":<int>,"quality":"<qualityLabel>","format":"<format>","size":<long>,"directUrl":"...","title":"<videoTitle or empty>","formatId":"<formatId or empty>","duration":<durationSeconds>}`
- exception -> `500 {"error":"<message or Failed to inspect>"}`.

**`/add`** (`main.kt:559-594`), body = one JSON object (`readJsonBody`, `main.kt:483-493`:
`Content-Length` above 4 MiB -> treated as no body; parsed with `MiniJson.parse`).
`Map.toRequest()` (`main.kt:495-535`) builds an `ExternalDownloadRequest`
(`LOGIC/core/network/ExternalDownloadBridge.kt:4-35`):

| JSON key                         | Type                                    | Handling                                                                                                                                                                               |
|----------------------------------|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `url`                            | string, required                        | trimmed; must pass `isAllowedUrl`, else the item is dropped                                                                                                                            |
| `cookie`, `referer`, `userAgent` | string                                  | trimmed, first 16384 chars, CR/LF removed                                                                                                                                              |
| `fileName`                       | string                                  | trimmed, first 255 chars                                                                                                                                                               |
| `formatId`                       | string                                  | when not blank: `inspectCached(url, {Cookie, Referer})`; the entry with this `formatId` is marked selected (`mediaQualities` = the whole list); no match or an exception -> empty list |
| `direct`                         | boolean                                 | start now with the default folder, no Add dialog                                                                                                                                       |
| `openClip`                       | boolean                                 | open the Add dialog with the clip range on                                                                                                                                             |
| `audioUrl`                       | string                                  | trimmed (video-only stream + its audio, merged by the app)                                                                                                                             |
| `playlist`                       | object `{title, entries:[{title,url}]}` | `parseExternalPlaylist` (`LOGIC/feature/media_extractor/Playlist.kt:73-93`): at most 2000 entries, http(s) only, titles cut to 200                                                     |
| `reading`                        | object `{done:int,total:int}`           | both clamped to >= 0                                                                                                                                                                   |
| `readingFailed`                  | string                                  | trimmed, 300 chars                                                                                                                                                                     |
| `group`                          | string                                  | trimmed, 150 chars                                                                                                                                                                     |

Answers:

- no object / no usable url -> `400 {"error":"missing or unsupported url"}`;
- `formatId` given but no matching quality ->
  `409 {"error":"quality not available any more, inspect again"}`
  (the extension re-inspects, `background.js:356`);
- else `200 {"ok":true}`.

Dedupe (`main.kt:580-591`): key =
`url + "|" + (formatId or "") + "|" + openClip + "|" + reading + "|" + readingFailed + "|" + (playlist entry count or 0)`
(`reading` is the Kotlin `Pair.toString()`, `(d, t)` or `null`). Map `recentAdds`; entries older
than 5000 ms are removed on every call. A repeat is **not** dispatched but still gets
`onShowAndFocus` and `200 {"ok":true}`.

**`/links`** (`main.kt:596-624`): body = JSON array of the same objects, or an object with a
`links` array. Each item through `toRequest()`, then `distinctBy { url }`, first 500. Empty ->
`400 {"error":"no usable links"}`. Else `ExternalDownloadBridge.dispatchBatch(requests)`
(`review = false`), `onShowAndFocus`, `200 {"ok":true,"accepted":<n>}`.

**`/show`** -> `onShowAndFocus`, `200 OK` (text). Used by a second app instance.

**`/download?url=<enc>&cookie=<enc>`** (`main.kt:634-676`): legacy GET and the second-instance
hand-over. Blank / not allowed url -> `400 unsupported url` (text). Else
`ExternalDownloadBridge.dispatch(url, cookie)`, `onShowAndFocus`, `200 OK` (text).

### 2.4 Pairing state (`LOGIC/core/network/BridgePairing.kt`, common code)

- `TOKEN_HEADER = "X-FetchJet-Token"` (`:39`), `ANSWER_TIMEOUT_MS = 120_000` (`:40`),
  `ASK_COOLDOWN = 8 s` (`:41`).
- File `<settings>/bridge.json` (`:54`), pretty JSON:
  `token` (encrypted by `Secrets.protect`, prefix `fjenc1:`), `legacyOrigins`, `blockedOrigins`,
  `seenBrowsers` (browser id -> epoch ms), `seenVersions` (browser id -> extension version).
- `token` (`:83-91`): lazy; a stored token shorter than 32 chars or one that cannot be decrypted
  is replaced by `Secrets.randomHex()` (64 hex chars) and saved.
- `ask(origin, legacy)` (`:143-180`): blocked origin -> false; same origin already asking ->
  waits for that answer; another origin asking, or asked again within 8 s of the last close ->
  false at once; else publishes `pending` and waits up to 120 s.
- UI side: `BridgePairing.pending` is collected in `UI/App.kt:814-827` and shown by
  `BridgePairingDialog` (`UI/HomeDialogs.kt:145-185`): title `bridgePairTitle`
  ("Allow this browser extension?"), text `bridgePairText` or `bridgePairOldText` (legacy), the
  origin in small text (2 lines max). **Filled button = Block** -> `BridgePairing.block()`;
  **outlined button = Allow** -> `answer(true)`; click outside -> `answer(false)` (not remembered).
- `noteBrowser(userAgent, nowMs, extVersion)` (`:210-220`): browser id from the User-Agent
  (`browserIdFromUserAgent`, `:222-233`: edge, opera, vivaldi, samsung, firefox, chrome); written
  at most once an hour per browser unless the version changed (version max 20 chars).
- Settings lists: `legacyAllowed`, `blocked`, `revokeAllowed(origin)`, `unblock(origin)`.

### 2.5 From the bridge to the view model

`ExternalDownloadBridge` (`LOGIC/core/network/ExternalDownloadBridge.kt:37-97`):

- `dispatch(request)` (`:71-77`): trims the url, prints
  `ExternalDownloadBridge: dispatched a URL (<n> chars)`, calls `onExternalRequest`; when the UI
  does not exist yet the request waits in `pending` and is replayed when the handler is set
  (`:39-48`).
- `dispatchBatch(requests, review = false)` (`:79-89`) -> `onExternalBatch`.
- `onShowAndFocus` / `showAndFocus()` (`:64-67`): set by the host (`main.kt:779-780`); also called
  from common code: clipboard offer (`UI/App.kt:136-138`), file-name conflict and duplicate task
  (`LOGIC/presentation/dashboard/DashboardViewModel.kt:353`, `381`), batch with media pages
  (`:512`), When-done countdown (`LOGIC/core/power/WhenDone.kt:70`).
- `openInBrowserFallback` (`:57`): host opens a URL in the default browser (`main.kt:782-797`,
  `java.awt.Desktop.browse`); `triggerFallback(url)` appends `fetchjet_fallback=1`.

`DashboardViewModel.init` sets the handlers (`DashboardViewModel.kt:166-169`):

- `handleExternalRequest(request)` (`:215-254`): `SiteCookieJar.remember`; `openClip` + qualities
  -> `openClipDialog`; qualities or `direct` -> `startExternalDirectly` (no dialog; duplicate of a
  running task resumes / scrolls to it); `reading` / `readingFailed` -> `showCourseReading`;
  else `OnUrlChanged(url)` + `OnToggleAddDialog(true)` (Add dialog pre-filled), and a `playlist`
  is shown at once.
- `handleExternalBatch(requests, review)` (`:431-...`): files are queued directly (2 or more
  files of one page go into a folder named after `group`), media pages open in the Add dialog's
  link list, a toast `sysQueuedFromBrowser(added, skipped)` is emitted.

The host only has to provide: the HTTP server, `onShowAndFocus`, `openInBrowserFallback`, and the
UI for `BridgePairing.pending`. Everything else is common Kotlin and moves into `fjcore.dll`
unchanged. The server code itself (`main.kt:138-688`) is plain Kotlin apart from
`com.sun.net.httpserver`, `URLDecoder` and `SwingUtilities.invokeLater`.

---

## 3. Window, tray, notifications, input

### 3.1 Window (`main.kt:769-870`)

| Item         | Value                                                                                                                                  | Where                                   |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| Title        | `"FetchJet - " + Strings.desktopWindowTagline` (English: `FetchJet - High-Velocity Multi-Engine Downloader`); follows the app language | `main.kt:867`, `EnglishProvider.kt:467` |
| First size   | 1100 x 750 dp                                                                                                                          | `main.kt:769`                           |
| Position     | platform default                                                                                                                       | `rememberWindowState` defaults          |
| Minimum size | none set                                                                                                                               | -                                       |
| Saved state  | none: size, position and maximised state are **not** saved between runs                                                                | -                                       |
| Icon         | `Res.drawable.ic_fetchjet_logo` (`androidUi/src/commonMain/composeResources/drawable/ic_fetchjet_logo.xml`), same painter for the tray  | `main.kt:770`, `869`                    |
| Decoration   | standard OS frame, resizable                                                                                                           | defaults                                |
| Close button | tray supported -> hide the window (process stays); no tray -> exit                                                                     | `main.kt:861-866`                       |
| Start hidden | `isWindowVisible = !startInTray`                                                                                                       | `main.kt:759`                           |
| Content      | `App()` with a clipboard wrapper that swallows clipboard exceptions                                                                    | `main.kt:908-931`                       |

`App(onDarkModeChanged = null, onForcedDarkChanged = null)` (`UI/App.kt:80-85`); the desktop host
passes nothing, so the title bar does not follow the theme. Nothing is drawn outside `androidUi`
except the tray and its menu.

The prototype (`desktopNative/ui/app.slint:411-413`) uses `preferred-width: 1200px` and a fixed
English title, and `main.cpp:329` finds the window with `FindWindowW` by that English title:
that breaks once the title is localized. Use the Slint window handle instead.

### 3.2 Bring to front (`triggerFocus`, `main.kt:772-776`, effect `871-897`)

`triggerFocus` sets `isWindowVisible = true`, `isMinimized = false` and bumps a counter. The
effect then does, in order: window state NORMAL, visible, `toFront()`, `requestFocus()`;
`java.awt.Robot` presses and releases **Alt** (a synthetic key press lifts Windows' foreground
lock); `isAlwaysOnTop = true`, `toFront()`, `requestFocus()`; `delay(150)`; `isAlwaysOnTop = false`.

Native equivalent: `ShowWindow(SW_RESTORE)`; `SendInput` Alt down/up (or `AttachThreadInput` with
the foreground thread); `SetWindowPos(HWND_TOPMOST)`; `SetForegroundWindow`; after 150 ms
`SetWindowPos(HWND_NOTOPMOST)`. When the trigger is a second instance started by the user, that
process may call `AllowSetForegroundWindow(<pid of first>)` first and the Alt trick is not needed.

Who calls it: every bridge endpoint that acts, the tray, the pending startup link, and the
common-code callers listed in 2.5.

### 3.3 Tray (`main.kt:761-857`)

- Icon: the app logo. Tooltip (`main.kt:807-822`), rebuilt when the active tasks or the language
  change:
    - no active task: `Strings.desktopTrayIdleTooltip` ("FetchJet - Running in Background");
    - else `Strings.desktopTrayDownloadsHeader` ("FetchJet Downloads:") + one line per task for the
      first 6: `<name>: <percent>%` (a name longer than 25 chars becomes its first 22 + `...`) +
      `Strings.desktopTrayMore(n)` ("+n more") when there are more than 6.
      Source: `DownloadNotificationManager.activeTasks`
      (`JVM/core/notification/DownloadNotificationManager.jvm.kt:19-49`):
      a task is removed at 100 % or when its speed text is "Completed". Windows cuts a tray tooltip
      at 127 chars.
- Primary action (`onAction`, `main.kt:828-834`): visible and not minimised -> hide; else
  `triggerFocus()`. In Compose/AWT on Windows this fires on **double-click**; the prototype uses a
  single click (`app.slint:33`).
- Menu (`main.kt:835-856`), rebuilt on language change:
    1. `Strings.desktopTrayShowApp` ("Show App") -> `triggerFocus()`
    2. submenu `Strings.whenDoneGroup` ("When downloads finish"): one check item per
       `WhenDoneAction` (`NOTHING`, `EXIT`, `SLEEP`, `SHUT_DOWN`; labels `whenDoneLabel`,
       `UI/ui/WhenDoneUi.kt:34-39`: "Do nothing", "Exit FetchJet", "Sleep", "Shut down"), checked =
       `WhenDone.action`, click -> `WhenDone.choose(choice)`
    3. separator
    4. `Strings.desktopTrayCloseApp` ("Close App") -> `exitApplication()`
- `WhenDone.onExitApp` is set to exit the application (`main.kt:799-800`).

The prototype tray (`app.slint:27-39`) has Show App / Close App only: the When-done submenu and
the localized labels are missing.

### 3.4 Notifications

Only one-off notices: `SystemNotice.show(id, title, text)`
(`JVM/core/notification/SystemNotice.jvm.kt:8-15`) emits into a `SharedFlow` (buffer 8); the host
collects it and calls `trayState.sendNotification(Notification(title, text, Type.Info))`
(`main.kt:762-768`), which is a Windows tray balloon / toast. The only callers are device sync (
"download received" / "sent", `LOGIC/presentation/dashboard/DashboardDeviceSync.kt:115`, `302`).
There is no per-download desktop notification: progress is the tray tooltip.

Native: `Shell_NotifyIconW` with `NIF_INFO` on the tray icon gives the same result without an
AppUserModelID. Whether Slint's `SystemTrayIcon` exposes a notification call was not checked.

### 3.5 Keyboard, drops, clipboard, pickers

- Keyboard shortcuts exist only in the task list (`UI/TaskList.kt:163-180`): Ctrl/Cmd+A select
  all, Esc clear selection, Delete (or Cmd+Backspace) delete selected; plus Ctrl+click and
  Shift+click on rows. No global hot keys, no menu bar, no accelerators in `main.kt`.
- File or link drag-and-drop: **not implemented** anywhere (no drop target in `androidUi` or
  `main.kt`).
- Clipboard watcher (`UI/App.kt:92-128`): when the setting is on, reads the clipboard text every
  800 ms (`getClipboardText()`, `JVM/Platform.jvm.kt:13-23`, AWT system clipboard, string flavour
  only), skips what was there at start, and offers a changed text through
  `ClipboardLinks.downloadableUrl`. The effect lives in the window's composition; the window is
  hidden, not destroyed, when closed to the tray, so the polling most likely continues there (not
  run to confirm). Native: `AddClipboardFormatListener` + `WM_CLIPBOARDUPDATE` avoids polling.
- Folder picker `PathResolver.chooseDirectory` (`PathResolver.jvm.kt:98-121`): Swing
  `JFileChooser`, directories only, title "Select Download Directory". Native: `IFileOpenDialog`
  with `FOS_PICKFOLDERS`.
- `.torrent` picker (`JVM/core/filesystem/TorrentFilePicker.jvm.kt:16-32`): AWT `FileDialog`
  "Open .torrent file", filter `*.torrent`, returns the path. Links file picker
  (`JVM/core/filesystem/TextFilePicker.jvm.kt:16-31`): "Import links", `*.txt`, returns the file's
  **text**.
- Open file / folder (`PathResolver.jvm.kt:125-283`): `Desktop.open`; show in Explorer =
  `explorer.exe /select,"<path>"`; fallback `cmd.exe /c start "" "<path>"`. Native:
  `ShellExecuteW` and `SHOpenFolderAndSelectItems`.
- Links opened by the UI through Compose's `LocalUriHandler` (the native UI needs
  `ShellExecuteW(open)` for them): `ms-settings:startupapps` (`UI/ui/StartupUi.kt:36`),
  `ms-windows-store://pdp/?productid=9NP3HJRHMPJS` (`UI/ui/UpdateUi.kt:178`), release and store
  pages, browser download pages.
- Power (`JVM/core/power/SystemPower.jvm.kt:12-31`): sleep =
  `powershell -NoProfile -NonInteractive -Command "Add-Type -AssemblyName System.Windows.Forms; [void][System.Windows.Forms.Application]::SetSuspendState('Suspend', $false, $false)"`;
  shut down = `shutdown /s /t 0`. Native: `SetSuspendState(FALSE, FALSE, FALSE)` (powrprof) and
  `shutdown.exe` or `InitiateSystemShutdownExW`.

---

## 4. Packaging today

### 4.1 Compose / jpackage settings (`desktopApp/build.gradle.kts`)

| Setting           | Value                                                                                                                                                                                                                                                                                                  | Line       |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| main class        | `com.michelle.raouf.fetch_jet.MainKt`                                                                                                                                                                                                                                                                  | 75         |
| JVM args          | `-Dfetchjet.verboseLogs=<bool>`, `-XX:+UseSerialGC`, `-Xms32m`, `-Xmx1g`, `-XX:MinHeapFreeRatio=10`, `-XX:MaxHeapFreeRatio=30` (+ `-XX:NativeMemoryTracking=summary` and `FETCHJET_JVM_ARGS` for measuring runs)                                                                                       | 76-86      |
| ProGuard          | off                                                                                                                                                                                                                                                                                                    | 90-92      |
| formats           | Dmg, Msi, Exe, Deb, AppImage                                                                                                                                                                                                                                                                           | 95-101     |
| `packageName`     | `FetchJet`                                                                                                                                                                                                                                                                                             | 102        |
| `packageVersion`  | `APP_VERSION` up to the first `-` (read from `AppVersion.kt` by regex)                                                                                                                                                                                                                                 | 25-28, 103 |
| `description`     | `FetchJet High-Velocity Multi-Engine Downloader`                                                                                                                                                                                                                                                       | 104        |
| `vendor`          | `Michelle Raouf`                                                                                                                                                                                                                                                                                       | 105        |
| file association  | mime `application/x-bittorrent`, extension `torrent`, description `BitTorrent file`                                                                                                                                                                                                                    | 107-111    |
| Windows           | `shortcut = true`, `menu = true`, `menuGroup = "FetchJet"`, `dirChooser = true`, `perUserInstall = true`, `upgradeUuid = "7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30"` ("Never change this value"), icon `desktopApp/src/main/resources/launcher.ico` (46051 bytes)                                          | 130-142    |
| bundled extension | task `bundleExtension` copies from `extension/`: `background.js`, `yt-core.js`, `content.js`, `popup.html`, `popup.js`, `options.html`, `options.js`, `icon16.png`, `icon48.png`, `icon128.png`, `manifest.json`, `platform.js`, `i18n/**`, `_locales/**` into resources `fetchjet-extension/chromium` | 45-71      |
| build check       | `extension/manifest.json` version must equal `EXTENSION_VERSION` (now `3.3.0`), else the build fails                                                                                                                                                                                                   | 46-57      |

The launcher adds (seen in `desktopApp/build/compose/tmp/packageMsi.args.txt` and the installed
`app\FetchJet.cfg`): `-Djpackage.app-version=<ver>`,
`-Dcompose.application.resources.dir=$APPDIR\resources`,
`-Dcompose.application.configure.swing.globals=true`, `-Dskiko.library.path=$APPDIR`.

### 4.2 What `packageMsi` / `packageExe` / `createDistributable` produce

- `desktopApp/build/compose/binaries/main/msi/FetchJet-<ver>.msi`
- `desktopApp/build/compose/binaries/main/exe/FetchJet-<ver>.exe` (jpackage's EXE: a bootstrapper
  around the same MSI)
- `desktopApp/build/compose/binaries/main/app/FetchJet/` (app image: `FetchJet.exe`, `app\`,
  `runtime\`), the input of the MSIX step.

The MSI, **checked** by decompiling the local `FetchJet-1.0.9.msi` (109,887,120 bytes, 277 files)
with the installed WiX `dark.exe`:

| Item                | Value                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Product             | `Name="FetchJet"`, `Manufacturer="Michelle Raouf"`, `Language="1033"`, `Codepage="1252"`, `Version="<ver>"`; `Id` (ProductCode) differs per version                                                                                                                                                                                                                                                                                                                    |
| UpgradeCode         | `{7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30}`                                                                                                                                                                                                                                                                                                                                                                                                                               |
| Package             | `InstallPrivileges="limited"` (per user, no admin, no UAC), `Platform="x64"`, `InstallerVersion="200"`, `Compressed="yes"`, one embedded cabinet `Data.cab`                                                                                                                                                                                                                                                                                                            |
| Install folder      | `INSTALLDIR` = `LocalAppDataFolder\FetchJet` = `%LOCALAPPDATA%\FetchJet`; the user can change it (UI `WixUI_InstallDir`, plus jpackage's "folder already exists" dialog)                                                                                                                                                                                                                                                                                               |
| Layout              | `INSTALLDIR\FetchJet.exe`, `INSTALLDIR\app\` (jars, `FetchJet.cfg`, `resources\`), `INSTALLDIR\runtime\`                                                                                                                                                                                                                                                                                                                                                               |
| Shortcuts           | Desktop `FetchJet` (condition `JP_INSTALL_DESKTOP_SHORTCUT`, default 1); Start menu `Programs\FetchJet\FetchJet` (condition `JP_INSTALL_STARTMENU_SHORTCUT`, default 1); both target `FetchJet.exe`, working dir `INSTALLDIR`                                                                                                                                                                                                                                          |
| `.torrent`          | `HKCR\.torrent` = `<progid>`, `Content Type` = `application/x-bittorrent`; `HKCR\<progid>` = `BitTorrent file`; `...\shell\open` = `Open with [ProductName]`; `...\shell\open\command` = `"<INSTALLDIR>\FetchJet.exe" "%1" %*`; `HKCR\MIME\Database\Content Type\application/x-bittorrent\Extension` = `.torrent`. `<progid>` is a jpackage hash (`progidb6b76d118e2c3f3e9f64d373ac4670d0` in this build). Per-user install, so these land in `HKCU\Software\Classes`. |
| Per-version key     | `HKCU\Software\Michelle Raouf\FetchJet\<ver>` (`ProductCode` values as component key paths, and the install folder for `RemoveFolderEx` on uninstall)                                                                                                                                                                                                                                                                                                                  |
| Add/Remove Programs | icon = launcher icon, comments = the description, contact = vendor, `ARPINSTALLLOCATION` = `[INSTALLDIR]`                                                                                                                                                                                                                                                                                                                                                              |
| Upgrade             | `Upgrade` table on the UpgradeCode: older or equal versions -> `JP_UPGRADABLE_FOUND`, newer -> `JP_DOWNGRADABLE_FOUND`, both removed; **no downgrade block**; `RemoveExistingProducts` at sequence 798 (before `CostInitialize`), so the old product is fully removed first                                                                                                                                                                                            |
| Custom actions      | jpackage DLL: `CheckInstallDir`, `FindRelatedProductsEx`                                                                                                                                                                                                                                                                                                                                                                                                               |
| Not in the MSI      | `fetchjet://` and `magnet:` handlers, the Run key, the Edge key: the app writes those itself at run time (sections 1.5 and 6); `magnet:` is not registered at all in the MSI build                                                                                                                                                                                                                                                                                     |

Version resource of the launcher, **checked** on the installed Store package's `FetchJet.exe`
(1.2.0, 497,664 bytes):

| Field                           | Value                                            |
|---------------------------------|--------------------------------------------------|
| CompanyName                     | `Michelle Raouf`                                 |
| FileDescription                 | `FetchJet High-Velocity Multi-Engine Downloader` |
| FileVersion / ProductVersion    | `1.2.0`                                          |
| InternalName / OriginalFilename | `FetchJet.exe`                                   |
| ProductName                     | `FetchJet`                                       |
| LegalCopyright                  | `Copyright (C) 2026`                             |

`desktopNative/res/app.rc` holds only `1 ICON "fetchjet.ico"`; it needs a `VERSIONINFO` block
with these values.

### 4.3 MSIX manifest (`desktopApp/msix/AppxManifest.xml`)

| Item                               | Value                                                                                                                                                                                                            |
|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Identity                           | `Name="MichelleRaouf.FetchJet"`, `Publisher="CN=81BF98FB-E3BB-479B-BCA6-3F56A36736A0"`, `ProcessorArchitecture="x64"`, `Version="__VERSION__"` (CI writes `<APP_VERSION>.0`). From Partner Center: never change. |
| Package family (installed here)    | `MichelleRaouf.FetchJet_7hajc9fhnzk5a`, installed 1.2.0.0, signed by the Store                                                                                                                                   |
| Properties                         | DisplayName `FetchJet`, PublisherDisplayName `Michelle Raouf`, Logo `Assets\StoreLogo.png`                                                                                                                       |
| Target                             | `Windows.Desktop`, `MinVersion="10.0.17763.0"`, `MaxVersionTested="10.0.26100.0"`                                                                                                                                |
| Languages                          | `en-us ar fr de es pt it ru tr id zh-cn ja ko hi fa`                                                                                                                                                             |
| Application                        | `Id="FetchJet"`, `Executable="FetchJet.exe"`, `EntryPoint="Windows.FullTrustApplication"`                                                                                                                        |
| VisualElements                     | DisplayName `FetchJet`, Description `Download manager for files, videos, playlists and torrents`, `BackgroundColor="transparent"`, `Square150x150Logo`, `Square44x44Logo`, `Wide310x150Logo`                     |
| File type                          | `uap:FileTypeAssociation Name="torrent"`, display name `BitTorrent file`, `.torrent`, content type `application/x-bittorrent`                                                                                    |
| Protocols                          | `fetchjet` ("FetchJet link") and `magnet` ("Magnet link")                                                                                                                                                        |
| Startup task                       | `desktop:StartupTask TaskId="FetchJetStartup" Enabled="true" DisplayName="FetchJet"`, `Executable="FetchJet.exe"`; Windows starts it with **no arguments**, only after the user opened the app once              |
| Execution alias                    | `fetchjet.exe`                                                                                                                                                                                                   |
| Capabilities                       | `internetClient`, `internetClientServer`, `privateNetworkClientServer`, `rescap:runFullTrust`                                                                                                                    |
| Assets (`desktopApp/msix/Assets/`) | `StoreLogo.png`; `Square44x44Logo.png` + `.targetsize-{16,24,32,48,256}.png` + the same with `_altform-unplated`; `Square150x150Logo.png` + `.scale-200.png`; `Wide310x150Logo.png` + `.scale-200.png`           |

The manifest does not depend on the JVM. For the native app only the files next to it change, as
long as the executable stays `FetchJet.exe` at the package root.

### 4.4 CI, Windows job (`build.yml:315-406`, job `build-desktop-windows`)

1. Runner `vars.RUNNER_WINDOWS` or `windows-latest`. Checkout with submodules
   (`secrets.SUBMODULE_TOKEN`), Temurin JDK 21, `gradle/actions/setup-gradle@v4`.
2. `shell: cmd`:
   `call .\gradlew.bat :desktopApp:packageExe :desktopApp:packageMsi :desktopApp:createDistributable`
   (`build.yml:341`). jpackage needs WiX 3 on the PATH.
3. MSIX (`build.yml:345-389`, bash, `MSYS_NO_PATHCONV=1`, `MSYS2_ARG_CONV_EXCL="*"`):
    - `V` = the release version or `APP_VERSION`, cut at `-`, padded to three numbers;
      `MSIX_VERSION="$V.0"`;
    - tools = newest `/c/Program Files (x86)/Windows Kits/10/bin/10.*/x64` that has `makeappx.exe`,
      else the NuGet package `microsoft.windows.sdk.buildtools` is downloaded;
    - stage `$RUNNER_TEMP/msix` = `desktopApp/build/compose/binaries/main/app/FetchJet/.` +
      `desktopApp/msix/Assets`;
    - manifest: `perl -0pe 's/>\s*([^<>\s][^<>]*?)\s*</>$1</g; s/__VERSION__/<MSIX_VERSION>/g'`
      (trims whitespace around text values, sets the version);
    - `makepri.exe createconfig /cf priconfig.xml /dq en-US /o`, then
      `makepri.exe new /pr <stage> /cf priconfig.xml /mn <stage>\AppxManifest.xml /of <stage>\resources.pri /o`
      (a failure is only a warning);
    - `makeappx.exe pack /o /d <stage> /p desktopApp/build/msix/FetchJet-<V>-store.msix`.
      The `.msix` is **unsigned**; the owner uploads it in Partner Center and the Store signs it.
4. Upload (`build.yml:391-406`, release runs only): files =
   `binaries/main/exe/*.exe`, `binaries/main/msi/*.msi`, `build/msix/*.msix`;
   `bash scripts/sign-release-file.sh <files>` writes `<file>.sig`; every file and `.sig` goes up
   with `gh release upload "$TAG" <file> --clobber`, in parallel.
5. The `release` job (`build.yml:660-753`) writes the notes with the
   `<!-- fetchjet-unchanged: ... -->` / `<!-- fetchjet-beta: ... -->` markers, uploads
   `release-notes.md`, `release-notes-<lang>.md` and `SHA256SUMS.txt`.

`scripts/sign-release-file.sh`: needs secret `UPDATE_SIGNING_KEY` (PEM, ECDSA P-256); for each
file `openssl dgst -sha256 -sign <key> <file> | openssl base64 -A > <file>.sig` (base64 of the
DER signature). There is **no Authenticode signing** (no `signtool` step): the MSI and EXE are
unsigned for Windows / SmartScreen.

`scripts/setup-windows-runner.ps1`: installs with winget Git, Temurin JDK 21 and WiX 3
(`WiXToolset.WiXToolset`), needs the Android SDK, registers a self-hosted runner in `C:\ar` with
label `win-host`, writes `.env` (`JAVA_HOME`, `ANDROID_HOME`, PATH = JDK bin; WiX bin; Git bin),
and a scheduled task "GitHub Runner FetchJet" at logon. `C:\ar` does not exist on this PC now.
A native build on that runner also needs Visual Studio (MSVC + CMake), the Slint package and the
Kotlin/Native toolchain; none of that is in the script.

---

## 5. In-app update on Windows

Common logic: `LOGIC/core/update/UpdateManager.kt`. Platform part: `expect object PlatformUpdater`
(`LOGIC/core/update/PlatformUpdater.kt`), JVM actual `JVM/core/update/PlatformUpdater.jvm.kt`,
Windows code `JVM/core/update/WindowsUpdater.kt`, helpers `JVM/core/update/DesktopUpdater.kt`.

```kotlin
expect object PlatformUpdater {
    val canInstallInApp: Boolean
    val platformKey: String
    val updatesFromStore: Boolean
    fun latestStoreVersion(): String?
    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset?
    fun launchInstallerAndExit(installerPath: String): Result<Unit>
}
internal expect fun verifyUpdateSignature(path: String, signatureBase64: String): Boolean
```

Windows values:

| Member             | Value                                                                                                         | Where                                                |
|--------------------|---------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| `platformKey`      | `"windows"`                                                                                                   | `WindowsUpdater.kt:15`                               |
| `canInstallInApp`  | installed launcher path known (`jpackage.app-path` exists) AND not the Store build                            | `WindowsUpdater.kt:17-18`, `DesktopUpdater.kt:32-33` |
| `updatesFromStore` | `WindowsStorePackage.isInstalled`                                                                             | `PlatformUpdater.jvm.kt:18`                          |
| `pickAsset`        | first asset whose name ends with `.msi`, else first ending with `.exe` (ignore case). `.msix` does not match. | `WindowsUpdater.kt:20-21`                            |

### 5.1 Check (common code, unchanged for native)

- `UpdateManager.onAppStart()` (`:157-167`): 4 s after start, when `autoCheck` is on and the last
  check is 12 h old. Settings file `<settings>/updates.properties`: `autoCheck`, `autoInstall`,
  `lastCheckedMs`, `skippedVersion` (`:490-513`).
- `GET https://api.github.com/repos/the-best-is-best/FetchJet/releases/latest` with
  `Accept: application/vnd.github+json`, `User-Agent: FetchJet/<APP_VERSION>` (`:320-331`);
  404 = nothing published; 403 / 429 = web-page fallback (`fetchLatestFromWebPage`, `:346-388`).
- When the latest release lists this platform in `<!-- fetchjet-unchanged: ... -->`, the newest
  release that was built for it is looked up in `/releases?per_page=30` (`:334-344`, `548-565`).
- Notes in the app language: asset `release-notes-<lang>.md` (`:292-305`).

### 5.2 MSI build: download, verify, swap

`startDownload` (`UpdateManager.kt:390-486`):

1. Only assets whose URL starts with
   `https://github.com/the-best-is-best/FetchJet/releases/download/` exist in the list (`:47-48`,
   `573-574`).
2. Target `<temp>/FetchJetDownloads/fetchjet-update/<asset name>`, written as `<name>.part`,
   SHA-256 computed while writing; `User-Agent: FetchJet/<APP_VERSION>`.
3. Fewer bytes than the length -> "download interrupted". GitHub's `digest` (sha256) known and
   different -> "checksum does not match the release".
4. Asset `<name>.sig` must exist ("the release file is not signed"); its text is checked with
   `verifyUpdateSignature(part, sig)`: ECDSA P-256 / SHA-256, DER signature in base64, against
   every key in `UPDATE_PUBLIC_KEYS` (`LOGIC/core/update/UpdateSignature.kt:10-12`, one key,
   X.509 SubjectPublicKeyInfo base64 starting `MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE55El...`).
   JVM actual: `sharedLogic/src/jvmAndroidMain/.../core/update/UpdateSignature.jvmAndroid.kt:9-34`
   (`SHA256withECDSA`). Native actual: Windows CNG (`BCryptVerifySignature` with
   `BCRYPT_ECDSA_P256_ALGORITHM`; the DER signature must be converted to raw r||s, and the key
   imported from the SPKI with `CryptDecodeObjectEx` / `CryptImportPublicKeyInfoEx2`).
5. `.part` is moved onto the target; state gets `installerPath`; the dialog shows
   "Restart & update". With `autoInstall` on, the download starts by itself after a check.

`installNow()` (`:200-216`) -> `PlatformUpdater.launchInstallerAndExit(path)`
(`PlatformUpdater.jvm.kt:24-28`) -> `WindowsUpdater.installAndExit(installer)`
(`WindowsUpdater.kt:23-45`):

- writes `fetchjet-update.cmd` next to the installer, CRLF line ends:

  ```bat
  @echo off
  :wait
  tasklist /FI "PID eq <pid>" 2>nul | find "<pid>" >nul && (timeout /t 1 /nobreak >nul & goto wait)
  msiexec /i "<installer path>" /passive /norestart
  start "" "<launcher path>"
  del "%~f0"
  ```

  For an `.exe` installer the fourth line is just `"<installer path>"`. `<pid>` is this process;
  `<launcher path>` is `jpackage.app-path` (the line is empty when unknown).
- starts it: `cmd.exe /c start "FetchJet update" /min <script path>`, working directory = the
  installer's folder;
- `exitProcess(0)`.

The MSI replaces the old install because of the fixed UpgradeCode and the per-user scope (no
admin prompt). `/passive` shows only a progress bar; it installs into the default folder.

### 5.3 Store build: ask the Store, never GitHub

`fetchLatestRelease()` (`UpdateManager.kt:307-319`): when `updatesFromStore`, the "release" is
`AppRelease(version = latestStoreVersion() ?: APP_VERSION, tag = "v<version>", title = "FetchJet <version>", notes = "", pageUrl = MICROSOFT_STORE_PAGE, assets = [])`,
with `MICROSOFT_STORE_PAGE = "ms-windows-store://pdp/?productid=9NP3HJRHMPJS"` (`:516`). The
dialog button opens that link (`UI/ui/UpdateUi.kt:178`). Nothing is downloaded.

`WindowsUpdater.latestStoreVersion()` (`WindowsUpdater.kt:71-99`): `askStore()`, and on an
exception one retry after 3000 ms. `askStore()`:

- command:
  `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand <base64 of the script in UTF-16LE>`,
  stderr discarded, stdout read to the end, wait up to 90 s (else "Microsoft Store did not answer");
- a line starting with `FJERR` -> error `Microsoft Store: <message>`;
- a line starting with `FJSTORE` -> the comma list after it; the newest by `isNewer`, or null
  when the list is empty (= no update); no such line -> "Microsoft Store did not answer".

The script (`WindowsUpdater.kt:49-68`), exactly:

```powershell
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Services.Store.StoreContext, Windows.Services.Store, ContentType = WindowsRuntime]
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    $op = [Windows.Services.Store.StoreContext]::GetDefault().GetAppAndOptionalStorePackageUpdatesAsync()
    $task = $asTask.MakeGenericMethod([System.Collections.Generic.IReadOnlyList[Windows.Services.Store.StorePackageUpdate]]).Invoke($null, @($op))
    if (-not $task.Wait(60000)) { "FJERR the Store did not answer in time"; exit }
    $versions = @($task.Result | ForEach-Object { $v = $_.Package.Id.Version; "$($v.Major).$($v.Minor).$($v.Build)" })
    "FJSTORE " + ($versions -join ',')
} catch {
    $e = $_.Exception
    while ($e.InnerException) { $e = $e.InnerException }
    "FJERR " + ($e.Message -replace '\s+', ' ').Trim()
}
```

It works only because a child process of the packaged app keeps the package identity; outside
the package the Store answers "no update". The native build can keep this child process (needs a
`CreateProcessW` + pipe helper in the mingwX64 actual) or call
`StoreContext::GetAppAndOptionalStorePackageUpdatesAsync` through C++/WinRT in the host.

---

## 6. Browser extension helpers on the desktop

Common contract: `expect object BrowserIntegration`
(`LOGIC/core/browser/BrowserIntegration.kt:47-101`), constants
`EDGE_EXTENSION_ID = "phngekpnkcnpkjkcoggcfmlmebmociaj"` (`:4`),
`FIREFOX_ADDON_URL = "https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/"` (`:6`).
JVM actual: `JVM/core/browser/BrowserIntegration.jvm.kt` + `JVM/core/browser/DesktopBrowsers.kt`.

### 6.1 At every start: `setUpOnFirstRun()` (`BrowserIntegration.jvm.kt:131-144`)

1. `registerUrlScheme()` (`:63-118`): only on Windows, not the Store build, and only when the
   installed exe path is known. Skipped when
   `reg query HKCU\Software\Classes\fetchjet\shell\open\command /ve` already contains the exe
   path. Else four `reg add ... /f` calls:

   | Key | Value name | Data |
      |---|---|---|
   | `HKCU\Software\Classes\fetchjet` | (default) | `URL:FetchJet link` |
   | `HKCU\Software\Classes\fetchjet` | `URL Protocol` | empty string |
   | `HKCU\Software\Classes\fetchjet\DefaultIcon` | (default) | `"<exe>",0` |
   | `HKCU\Software\Classes\fetchjet\shell\open\command` | (default) | `"<exe>" "%1"` |

2. Daemon thread: `DesktopBrowsers.refreshUnpackedExtension()` (`DesktopBrowsers.kt:189-191`):
   when `<home>/.fetch_jet/extension/chromium/manifest.json` exists, unpack the bundled copy over
   it, so a loaded-unpacked extension updates with the app.
3. Windows and not the Store build: when marker `<home>/.fetch_jet/browser-integration.done`
   does not exist: if Edge is not registered, register it; write the marker with text `edge`.

Every registry call is a `reg.exe` child with a 10 s timeout (`:17-25`). Native: `RegSetKeyValueW`
directly.

### 6.2 Edge hand-off (not the Store build)

- Key `HKCU\Software\Microsoft\Edge\Extensions\phngekpnkcnpkjkcoggcfmlmebmociaj`.
- `isEdgeRegistered()` (`:27-28`): `reg query <key> /v update_url` exits 0.
- `addToEdge()` (`:30-47`):
  `reg add <key> /v update_url /t REG_SZ /d https://edge.microsoft.com/extensionwebstorebase/v1/crx /f`.
- `removeFromEdge()` (`:49-56`): `reg delete <key> /f`.
- `canAddToEdgeAutomatically` (`:15`) = Windows and not the Store build.
- Android-only members are constants on desktop (`:146-152`): `canKeepReadyForBrowser = false`,
  `canPopUpOverBrowser = false`, `isPopUpAllowed() = true`, the rest do nothing.

### 6.3 Browser detection and actions (`DesktopBrowsers.kt`)

Detection is by file only, no registry. For each relative path the three roots are tried in this
order: `%ProgramFiles%` (default `C:\Program Files`), `%ProgramFiles(x86)%`
(default `C:\Program Files (x86)`), `%LOCALAPPDATA%` (`:43-50`).

| id        | Name           | Windows relative path(s)                            | Extensions page        | Download page                             |
|-----------|----------------|-----------------------------------------------------|------------------------|-------------------------------------------|
| `chrome`  | Google Chrome  | `Google\Chrome\Application\chrome.exe`              | `chrome://extensions`  | `https://www.google.com/chrome/`          |
| `edge`    | Microsoft Edge | `Microsoft\Edge\Application\msedge.exe`             | `edge://extensions`    | `https://www.microsoft.com/edge/download` |
| `firefox` | Firefox        | `Mozilla Firefox\firefox.exe`                       | `FIREFOX_ADDON_URL`    | `https://www.mozilla.org/firefox/new/`    |
| `brave`   | Brave          | `BraveSoftware\Brave-Browser\Application\brave.exe` | `brave://extensions`   | `https://brave.com/download/`             |
| `opera`   | Opera          | `Programs\Opera\opera.exe`, `Opera\opera.exe`       | `opera://extensions`   | `https://www.opera.com/download`          |
| `vivaldi` | Vivaldi        | `Vivaldi\Application\vivaldi.exe`                   | `vivaldi://extensions` | `https://vivaldi.com/download/`           |

- `browsers()` (`:115-119`): `BrowserInfo(id, name, installed, downloadUrl, hasLocalCopy)`,
  installed ones first; `hasLocalCopy` = not Firefox and the extension is bundled.
- `addExtension(id)` (`:121-132`): unknown id -> `Failed("unknown browser")`; no exe ->
  `NotInstalled`; Firefox -> start `firefox.exe <FIREFOX_ADDON_URL>` -> `Opened`; Chromium ->
  `unpackChromiumExtension()`, start `<exe> <extensions page>`, return
  `LoadUnpacked(<folder>, <page>)` (the UI then tells the user to turn on Developer mode and load
  the folder).
- `unpackChromiumExtension()` (`:194-228`): copies resource tree `fetchjet-extension/chromium/`
  (from the jar or from a classes folder) to `<home>/.fetch_jet/extension/chromium`; null when the
  build has no bundled copy.
- `installXpi(id, bytes, fileName)` (`:145-159`): Firefox only; the name is cleaned with
  `[^A-Za-z0-9._-]` -> `_`; saved to `<home>/.fetch_jet/extension/firefox/<name>`; then
  `firefox.exe <file path>` (Firefox asks "Add?").
- `openBrowser(id)` (`:134-142`): start the exe with no arguments.
- `openFolder(folder)` (`:173-182`): `explorer.exe <folder>`.
- `launch(exe, url)` (`:161-171`): a child process `<exe> <url>`, true unless starting throws.

### 6.4 Store versions and the signed `.xpi` (`LOGIC/core/browser/ExtensionStore.kt`, common, ktor)

- Firefox: `GET https://addons.mozilla.org/api/v5/addons/addon/fetchjet-download-manager/`,
  JSON `current_version.version` (`:33-34`, `103-108`).
- Edge:
  `GET https://edge.microsoft.com/extensionwebstorebase/v1/crx?response=updatecheck&acceptformat=crx3&prodversion=142.0&x=id%3Dphngekpnkcnpkjkcoggcfmlmebmociaj%26installsource%3Dondemand%26uc`,
  regex `<updatecheck[^>]*\sversion="([^"]+)"` (`:35-36`, `110-113`).
- `githubXpi()` (`:55-84`):
  `GET https://api.github.com/repos/the-best-is-best/FetchJet/releases?per_page=10`,
  assets named `^fetchjet_download_manager-([0-9.]+)\.xpi$` whose URL starts with the repository's
  release download prefix, newest version; `download(xpi)` returns the bytes (more than 1024).
- All with `User-Agent: FetchJet/<APP_VERSION>`, connect 10 s, socket 15 s.

### 6.5 How the extension starts the app (`extension/background.js:379-397`)

1. Native messaging host `com.michelle.raouf.fetch_jet_downloader` (`background.js:17`,
   permission in `extension/manifest.json`). **No code in the repository registers such a host**,
   so this step always fails and falls through.
2. Opens a tab on `fetchjet://download?url=<encodeURIComponent(link)>` and closes it after 1.5 s.
   This needs the registry entries of 6.1 (MSI build) or the manifest protocol (MSIX).

For the native app the bundled extension can be plain files in the install folder (for example
`INSTALLDIR\extension\chromium\`) copied to `.fetch_jet\extension\chromium`, with the same file
list as `bundleExtension` (4.1).

---

## 7. CHECKLIST: what the native host (or its mingwX64 actuals) must do

"Host" = the C++ / Slint process; "core" = Kotlin in `fjcore.dll`. Where the JVM code is plain
Kotlin over JDK calls, it can move to a `mingwX64Main` actual; the checklist says what must exist,
not which side writes it.

### Start-up

| #   | Responsibility                                                                                                                                                             | Source                                                            |
|-----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| S1  | Redirect all output to `%USERPROFILE%\.fetch_jet\logs\app.log`: delete above 2 MiB, append, UTF-8, redact each line, start banner, log crashes                             | `main.kt:278-303`                                                 |
| S2  | Start localization before any text is shown                                                                                                                                | `main.kt:697`, `LOGIC/core/i18n/start/LocalizationStart.kt:45-57` |
| S3  | `AppLog.verbose` from env `FETCHJET_VERBOSE_LOGS`, else a build-time default                                                                                               | `main.kt:700-704`                                                 |
| S4  | Run `BrowserIntegration.setUpOnFirstRun()` on a background thread                                                                                                          | `main.kt:719-723`                                                 |
| S5  | Detect `--autostart=true`, remove it from the arguments, set `StartupPreferences.launchedAtLogin`                                                                          | `main.kt:726-733`                                                 |
| S6  | Store build with no arguments: decide "started at login" (uptime under 2 minutes, or the startup-task activation kind); the PowerShell call of the JVM build does not work | `WindowsStorePackage.kt:20-40`                                    |
| S7  | Parse the first argument: `fetchjet://` / `kortex://` (`?url=` or embedded http link), http(s), `magnet:`, existing `.torrent` path                                        | `main.kt:51-94`, `734-737`                                        |
| S8  | With a link: hand it to the running instance (`GET /download?url=` + `X-FetchJet: 1` + token, 800 ms) and exit on 2xx                                                      | `main.kt:121-135`, `738-741`                                      |
| S9  | Without a link: `GET /ping` (500 ms, body `PONG`), then `GET /show` with the token, exit                                                                                   | `main.kt:97-119`, `745-747`                                       |
| S10 | Fix: real single-instance lock, and hand `.torrent` paths to the first instance                                                                                            | 1.4                                                               |
| S11 | Start hidden in the tray when started at login and `StartupPreferences.startHidden`                                                                                        | `main.kt:750-759`, `LOGIC/core/startup/StartupLauncher.kt:39-41`  |
| S12 | Deliver the pending start link ~600 ms after the UI exists: `ExternalDownloadBridge.dispatch(url, "")`, then focus                                                         | `main.kt:899-906`                                                 |
| S13 | Know the exe path and the version without `jpackage.*` properties                                                                                                          | `DesktopUpdater.kt:32-33`, `main.kt:428`                          |
| S14 | Detect the Store build: exe path contains `\WindowsApps\`                                                                                                                  | `WindowsStorePackage.kt:9-14`                                     |
| S15 | App home `%USERPROFILE%\.fetch_jet`, temp `%TEMP%\FetchJetDownloads`, OneDrive-aware Downloads / Documents / Desktop                                                       | `PathResolver.jvm.kt:10-62`                                       |

### Bridge

| #   | Responsibility                                                                                                              | Source                                                                              |
|-----|-----------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| B1  | HTTP/1.1 server on `127.0.0.1:28450` only, concurrent requests, 404 for unknown paths, a bind failure must not stop the app | `main.kt:305-308`, `679-687`                                                        |
| B2  | Host / Origin gate (`isTrustedRequest`) on every endpoint                                                                   | `main.kt:203-214`                                                                   |
| B3  | Replies echo the trusted `Origin` in `Access-Control-Allow-Origin`; 204 without body                                        | `main.kt:245-262`                                                                   |
| B4  | Preflights per endpoint with the exact method and header lists                                                              | `main.kt:216-217`, `314-318`, `326-334`, `407-415`, `451-456`, `541-549`, `638-646` |
| B5  | `X-FetchJet` header required on `/pair`, `/inspect`, `/add`, `/links`, `/show`, `/download`                                 | `main.kt:337`, `459`, `553`, `628`, `647`                                           |
| B6  | Token / legacy-origin check, legacy pairing prompt, `noteBrowser`                                                           | `main.kt:223-243`, `BridgePairing.kt:105-128`, `210-220`                            |
| B7  | `/ping`                                                                                                                     | `main.kt:310-320`                                                                   |
| B8  | `/config` with the exact JSON and the real app version                                                                      | `main.kt:403-440`                                                                   |
| B9  | `/pair`: bring the window forward, block up to 120 s on `BridgePairing.ask`, answer the token or 403                        | `main.kt:447-475`, `BridgePairing.kt:143-180`                                       |
| B10 | `/inspect` with the 30-minute, 40-entry, per-key-gated cache                                                                | `main.kt:144-187`, `322-400`                                                        |
| B11 | `/add`: body limit 4 MiB, field limits, `formatId` -> cached qualities, 409, 5 s dedupe                                     | `main.kt:483-535`, `559-594`                                                        |
| B12 | `/links`: array or `{links:[...]}`, distinct by url, max 500                                                                | `main.kt:596-624`                                                                   |
| B13 | `/show` and `/download`                                                                                                     | `main.kt:626-676`                                                                   |
| B14 | URL rule `isAllowedUrl` everywhere a link enters                                                                            | `main.kt:190-196`                                                                   |
| B15 | Pairing dialog: Block is the filled button, Allow the outlined one, click outside = one-time no                             | `UI/App.kt:814-827`, `UI/HomeDialogs.kt:145-185`                                    |
| B16 | Settings lists of allowed / blocked origins with Revoke / Unblock                                                           | `BridgePairing.kt:69-70`, `236-245`                                                 |

### Window and tray

| #   | Responsibility                                                                                     | Source                                                                                        |
|-----|----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| W1  | Title `FetchJet - <desktopWindowTagline>`, updated on language change                              | `main.kt:867`                                                                                 |
| W2  | First size 1100 x 750, default position, nothing saved                                             | `main.kt:769`                                                                                 |
| W3  | App icon on the window, taskbar and tray                                                           | `main.kt:770`, `826`, `869`                                                                   |
| W4  | Close = hide to tray (exit when there is no tray)                                                  | `main.kt:861-866`                                                                             |
| W5  | Bring-to-front with the foreground-lock workaround and 150 ms topmost                              | `main.kt:772-776`, `871-897`                                                                  |
| W6  | Set `ExternalDownloadBridge.onShowAndFocus` and `openInBrowserFallback`                            | `main.kt:779-797`                                                                             |
| W7  | Tray tooltip from the active downloads (6 lines, 25-char names, "+n more")                         | `main.kt:803-822`, `DownloadNotificationManager.jvm.kt:19-49`                                 |
| W8  | Tray primary action toggles hide / show                                                            | `main.kt:828-834`                                                                             |
| W9  | Tray menu: Show App, When downloads finish (4 check items), separator, Close App; localized        | `main.kt:835-856`                                                                             |
| W10 | `WhenDone.onExitApp` quits the app                                                                 | `main.kt:799-800`                                                                             |
| W11 | Show `SystemNotice` notices as tray notifications                                                  | `main.kt:762-768`, `SystemNotice.jvm.kt:8-15`                                                 |
| W12 | RTL for `ar` / `fa` and live language change                                                       | `LocalizationStart.kt:59-77`                                                                  |
| W13 | Task-list keys: Ctrl+A, Esc, Delete, Ctrl+click, Shift+click                                       | `UI/TaskList.kt:163-180`                                                                      |
| W14 | Clipboard watcher and its offer dialog                                                             | `UI/App.kt:92-138`, `JVM/Platform.jvm.kt:13-23`                                               |
| W15 | Folder picker, `.torrent` picker (path), `.txt` picker (text)                                      | `PathResolver.jvm.kt:98-121`, `TorrentFilePicker.jvm.kt:16-32`, `TextFilePicker.jvm.kt:16-31` |
| W16 | Open file, open folder, show in Explorer, open links and `ms-settings:` / `ms-windows-store:` URIs | `PathResolver.jvm.kt:125-283`, `UI/ui/StartupUi.kt:36`, `UI/ui/UpdateUi.kt:178`               |
| W17 | Sleep and shut down                                                                                | `SystemPower.jvm.kt:12-31`                                                                    |

### System integration

| #  | Responsibility                                                                                                                                                                        | Source                                                                                      |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| I1 | "Start at login": `HKCU\...\Run` value `com.michelle.raouf.fetch_jet` with the exact command string; off in the Store build, which opens `ms-settings:startupapps`                    | `StartupLauncher.jvm.kt:9-30`, auto-launch 0.9.0 `PlatformAutoLaunchWindows`                |
| I2 | Register `fetchjet://` in HKCU at start (MSI build)                                                                                                                                   | `BrowserIntegration.jvm.kt:63-118`                                                          |
| I3 | Edge extension key: query, add, delete; first-run marker                                                                                                                              | `BrowserIntegration.jvm.kt:12-56`, `131-144`                                                |
| I4 | Browser list by exe path, open extensions page, open browser, open folder                                                                                                             | `DesktopBrowsers.kt:43-182`                                                                 |
| I5 | Ship the Chromium extension, unpack / refresh `.fetch_jet\extension\chromium`                                                                                                         | `DesktopBrowsers.kt:184-228`, `desktopApp/build.gradle.kts:45-71`                           |
| I6 | Save a signed `.xpi` and open it in Firefox                                                                                                                                           | `DesktopBrowsers.kt:145-159`                                                                |
| I7 | Secrets key: `secrets.key` = base64 of the DPAPI blob (current user) of the 32-byte AES key; values `fjenc1:` + base64(12-byte IV + AES-256-GCM); must read files the JVM build wrote | `WindowsKeyVault.kt:7-31`, `SecretBox.jvm.kt:10-46`, `LOGIC/core/security/Secrets.kt:13-46` |

### Updates

| #  | Responsibility                                                                                                                     | Source                                                           |
|----|------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| U1 | `canInstallInApp`, `platformKey = "windows"`, `updatesFromStore`, `pickAsset` (`.msi`, then `.exe`)                                | `WindowsUpdater.kt:15-21`, `PlatformUpdater.jvm.kt:13-22`        |
| U2 | ECDSA P-256 / SHA-256 check of `<file>.sig` (DER, base64) against `UPDATE_PUBLIC_KEYS`                                             | `UpdateSignature.kt:10-15`, `UpdateSignature.jvmAndroid.kt:9-34` |
| U3 | Write and start `fetchjet-update.cmd` (wait for the pid, `msiexec /i ... /passive /norestart`, relaunch, delete itself), then exit | `WindowsUpdater.kt:23-45`                                        |
| U4 | Store build: ask the Store from inside the package, one retry after 3 s, 90 s limit                                                | `WindowsUpdater.kt:49-99`                                        |
| U5 | Call `UpdateManager.onAppStart()` when the UI starts                                                                               | `UpdateManager.kt:157-167`                                       |

### Packaging

| #  | Responsibility                                                                                                                                                                        | Source                                                  |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| P1 | MSI: UpgradeCode `7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30`, per user, `%LOCALAPPDATA%\FetchJet\FetchJet.exe`, Desktop + Start-menu shortcuts, `.torrent` ProgId, version = `APP_VERSION` | `desktopApp/build.gradle.kts:102-142`, 4.2              |
| P2 | MSIX: same identity, `Executable="FetchJet.exe"`, same extensions and assets, version `<APP_VERSION>.0`, `resources.pri`                                                              | `desktopApp/msix/AppxManifest.xml`, `build.yml:345-389` |
| P3 | Exe version resource and icon                                                                                                                                                         | 4.2                                                     |
| P4 | `.sig` for every release file; asset names `FetchJet-<ver>.msi`, `FetchJet-<ver>.exe`, `FetchJet-<ver>-store.msix`                                                                    | `build.yml:391-406`, `scripts/sign-release-file.sh`     |

---

## 8. Proposed packaging plan for the native app

### 8.1 Tools already on this PC (looked for, nothing installed)

| Tool                                 | Found at                                                      | Notes                                                                                                                                                                                 |
|--------------------------------------|---------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| WiX Toolset 3.14                     | `C:\Program Files (x86)\WiX Toolset v3.14\bin`                | `candle.exe`, `light.exe`, `heat.exe`, `dark.exe`, `torch.exe`, `lit.exe`, `insignia.exe`; `WixUIExtension.dll`, `WixUtilExtension.dll`, `WixBalExtension.dll`                        |
| Windows SDK 10.0.26100.0             | `C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64` | `makeappx.exe`, `makepri.exe`, `signtool.exe`, `mt.exe`, `rc.exe`, `MakeCert.exe`, `pvk2pfx.exe`, `makecat.exe`. Older `bin` folders (14393 - 17134) have no `makeappx.exe` in `x64`. |
| MSVC 14.51.36231 (VS 2026 Community) | `C:\Program Files\Microsoft Visual Studio\18\Community\VC`    | `dumpbin.exe`; redistributable DLLs in `Redist\MSVC\14.51.36231\x64\Microsoft.VC145.CRT` (`msvcp140.dll`, `vcruntime140.dll`, `vcruntime140_1.dll`, ...)                              |
| CMake                                | `C:\Program Files\CMake\bin`                                  | used by `desktopNative/build.cmd`                                                                                                                                                     |
| OpenSSL, Perl                        | Git Bash (`/mingw64/bin/openssl`, `/usr/bin/perl`)            | same commands as CI                                                                                                                                                                   |
| .NET SDK                             | not found (`dotnet` host only)                                | WiX 4/5 (a dotnet tool) is therefore not available; use WiX 3.14                                                                                                                      |
| Inno Setup, NSIS                     | not found                                                     | -                                                                                                                                                                                     |
| Self-hosted runner `C:\ar`           | not found                                                     | the Windows CI job runs elsewhere or on `windows-latest`                                                                                                                              |

### 8.2 Files to ship

Runtime dependencies, read with `dumpbin /dependents`:

- `fetchjet_native.exe` (prototype, 764,928 bytes): `slint_cpp.dll`, `MSVCP140.dll`,
  `VCRUNTIME140.dll`, `VCRUNTIME140_1.dll`, the UCRT api sets, `KERNEL32`, `USER32`.
- `slint_cpp.dll` (27,976,192 bytes): `MSVCP140.dll`, `VCRUNTIME140.dll`, UCRT, and system DLLs
  (`d3d12`, `dxgi`, `D3DCOMPILER_47`, `opengl32`, `dwrite`, ...).
- `fjcore.dll` (toy core, 5,485,568 bytes): `KERNEL32.dll`, `msvcrt.dll`, `WINHTTP.dll` only.

Stage folder (one folder feeds both the MSI and the MSIX):

```
FetchJet.exe            the Slint host, renamed; version resource + icon + manifest
fjcore.dll
slint_cpp.dll
msvcp140.dll            from Redist\MSVC\14.51.36231\x64\Microsoft.VC145.CRT
vcruntime140.dll
vcruntime140_1.dll
extension\chromium\...  same file list as the bundleExtension task
licenses\...            GPL-3.0 LICENSE, Slint and other third-party notices
```

The exe must be named `FetchJet.exe` and sit at the root: the JVM updater's relaunch line, the
Run key string, the `fetchjet://` command and the MSIX manifest all point there. The UCRT is part
of Windows 10 and later (the manifest's minimum is 10.0.17763). yt-dlp, ffmpeg and deno stay
downloads into `.fetch_jet\tools`, as now.

### 8.3 MSI with WiX 3.14

1.
`heat.exe dir <stage> -cg AppFiles -dr INSTALLDIR -gg -sfrag -srd -sreg -var var.Stage -out files.wxs`
(`-ag` is not suitable for per-user components; they need a registry key path, see 3).
2. A hand-written `product.wxs` that mirrors 4.2:
    -
    `Product Id="*" UpgradeCode="7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30" Name="FetchJet" Manufacturer="Michelle Raouf" Language="1033" Version="<APP_VERSION>"`;
    - `Package InstallScope="perUser" InstallPrivileges="limited" Platform="x64" Compressed="yes"`,
      `MediaTemplate EmbedCab="yes"`;
    - `MajorUpgrade Schedule="afterInstallValidate" AllowDowngrades="yes"` (the JVM MSI allows
      downgrades and removes the old product before copying files);
    - `Directory`: `LocalAppDataFolder\FetchJet` as `INSTALLDIR`, `DesktopFolder`,
      `ProgramMenuFolder\FetchJet`;
    - shortcuts named `FetchJet` on the Desktop and in the Start menu, working dir `INSTALLDIR`;
    - `.torrent`: `ProgId` with description `BitTorrent file`, verb `open` =
      `"[INSTALLDIR]FetchJet.exe" "%1"`, content type `application/x-bittorrent`;
    - `Icon` + `ARPPRODUCTICON`, `ARPCOMMENTS`, `ARPCONTACT`;
    - `WixUI_InstallDir` with `WIXUI_INSTALLDIR=INSTALLDIR`;
    - recommended extra: remember the chosen `INSTALLDIR` in
      `HKCU\Software\Michelle Raouf\FetchJet` and read it back with a `RegistrySearch`, so a
      `/passive` upgrade lands in the folder the user picked. The JVM MSI does not do this.
3. Per-user rules: every component under `LocalAppDataFolder` needs an HKCU registry value as its
   key path and a `RemoveFolder`; expect ICE38 / ICE64 / ICE91 from `light` and handle them as
   jpackage does (registry key paths, `-sice:ICE91`).
4.
`candle.exe -arch x64 -dStage=<stage> -dVersion=<ver> -ext WixUtilExtension product.wxs files.wxs`,
then
`light.exe -ext WixUIExtension -ext WixUtilExtension -cultures:en-us -out FetchJet-<ver>.msi *.wixobj`.
5. The optional registry entries (`fetchjet://`, later `magnet:`) can move into the MSI so they
   are removed on uninstall; the app's own start-up registration can stay as a repair.

Upgrade from the JVM build: same UpgradeCode, same scope, higher version -> the old product (`app\`,
`runtime\`, ~277 files) is removed and the native files are installed in one
`msiexec /i ... /passive /norestart`, which is exactly what the installed JVM updater runs. Its
relaunch line `start "" "%LOCALAPPDATA%\FetchJet\FetchJet.exe"` then starts the native app. This
is the path every existing MSI user will take, so it needs a real test (on a clean user profile
or a VM, not on the owner's install).

Release order matters: the JVM updater takes the **first** asset ending in `.msi`. A release must
not carry two MSIs for Windows.

### 8.4 EXE installer

jpackage's `.exe` is only a wrapper around the MSI, and the updater prefers the `.msi`. Options:
a WiX Burn bundle (`WixBalExtension` is installed) that chains the MSI, or stop shipping the
`.exe`. `docs/index.html` (the downloads page) must be checked for links to the `.exe` before
dropping it. Owner's decision.

### 8.5 MSIX with makeappx

Same steps as CI (4.4) with the native stage folder in place of the jpackage app image:

```
perl -0pe 's/>\s*([^<>\s][^<>]*?)\s*</>$1</g; s/__VERSION__/<ver>.0/g' desktopApp/msix/AppxManifest.xml > <stage>\AppxManifest.xml
copy desktopApp (removed)\msix\Assets -> <stage>\Assets
makepri.exe createconfig /cf priconfig.xml /dq en-US /o
makepri.exe new /pr <stage> /cf priconfig.xml /mn <stage>\AppxManifest.xml /of <stage>\resources.pri /o
makeappx.exe pack /o /d <stage> /p FetchJet-<ver>-store.msix
```

Tools: `C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64`. The manifest needs no
change if the exe keeps its name; the VC runtime DLLs ship app-local (no `PackageDependency`
needed). The package stays unsigned; the Store signs it.

Local testing needs a signed package (a self-signed certificate whose subject equals the
manifest `Publisher`) or a loose-file registration. **Do not do either on this PC with the real
identity**: `MichelleRaouf.FetchJet` 1.2.0.0 from the Store is installed here and a test package
with the same identity would replace it. Test with a changed `Identity Name` or on another
machine / VM.

Inside the MSIX: registry writes are private to the package (no Run key, no Edge key, no
`fetchjet://` registration: the manifest covers start-up, protocols and file types),
`%USERPROFILE%\.fetch_jet` is the real folder, and child processes (PowerShell for the Store
check, yt-dlp, ffmpeg) keep the package identity.

### 8.6 Version resource and CI

- `desktopNative/res/app.rc`: add `VERSIONINFO` (values in 4.2) and take the numbers from
  `APP_VERSION` at configure time (CMake can read `AppVersion.kt` with the same regex as
  `desktopApp/build.gradle.kts:25-28`). An application manifest (per-monitor DPI v2, long paths,
  common controls 6) belongs next to it.
- CI: a native Windows job needs MSVC, CMake, the Slint C++ package and the Kotlin/Native
  mingwX64 toolchain on the runner (`scripts/setup-windows-runner.ps1` installs none of them),
  then: Gradle link of `fjcore.dll` with `-Pfetchjet.nativeDesktop=true`, CMake build, stage, WiX,
  makeappx, `scripts/sign-release-file.sh`, upload. Keep the asset names of P4.
- Authenticode signing does not exist today (the JVM `FetchJet.exe` is a known jpackage launcher;
  a new unsigned native exe may be treated differently by SmartScreen / antivirus). `signtool.exe`
  is installed, a certificate is not.

---

## 9. Open questions

1. Where does the bridge server live: Kotlin in `fjcore.dll` (the handler code is nearly plain
   Kotlin; needs an HTTP server or raw sockets for mingwX64) or C++ in the host calling into the
   core? This decides who owns `inspectCached`, `recentAdds` and the JSON parsing.
2. Single instance: keep the port-only scheme for parity, or add the mutex + private channel (and
   so fix the `.torrent` second-instance case)?
3. Tray primary action: double-click as in the JVM app, or single click as in the prototype?
4. Does Slint 1.18.1 `SystemTrayIcon` support check items / submenus / dynamic menu text and a
   notification call? If not, the tray must be Win32 (`Shell_NotifyIconW` + `TrackPopupMenu`).
5. Keep shipping an `.exe` installer (Burn) or MSI only?
6. Store "started at login": uptime heuristic (as intended) or the startup-task activation kind?
7. Should the native MSI also register `magnet:` (today only the MSIX build has it)?
8. Should the window size / position be saved? The JVM app does not; "same look" says no.
9. Is the native-messaging step in the extension meant to be implemented one day
   (`com.michelle.raouf.fetch_jet_downloader` is registered nowhere)?
