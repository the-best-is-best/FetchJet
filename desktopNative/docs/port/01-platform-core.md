# 01 - Platform inventory, part A (core)

Written 2026-11-09 on branch `desktop-native`. Scope: every `expect` under
`core/` and `Platform.kt` of `sharedLogic/src/commonMain`, their JVM and iOS actuals, every
non-actual JVM file under `core/` of `jvmMain` and `jvmAndroidMain`, and a plan for the
Kotlin/Native `mingwX64` actuals (the `fjcore.dll` of the native Windows app).

Nothing in the repository was changed except this file. Things marked **VERIFIED** were run on
this PC in a throw-away probe (`E:/tools/probes/platform-core`, section 7). Things marked **NOT
VERIFIED** are read from code or from memory of an API and must be tested before they
are relied on.

Path prefixes used below:

| Prefix    | Folder                                                                                   |
|-----------|------------------------------------------------------------------------------------------|
| `C/`      | `sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`                        |
| `J/`      | `sharedLogic/src/jvmMain/kotlin/com/michelle/raouf/fetch_jet/`                           |
| `JA/`     | `sharedLogic/src/jvmAndroidMain/kotlin/com/michelle/raouf/fetch_jet/`                    |
| `I/`      | `sharedLogic/src/iosMain/kotlin/com/michelle/raouf/fetch_jet/`                           |
| `M/`      | `sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/` (does not exist yet) |
| `UI/`     | `androidUi/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`                           |
| `main.kt` | `desktopApp/src/main/kotlin/com/michelle/raouf/fetch_jet/main.kt`                        |

Contents: 1 cross-cutting facts, 2 on-disk compatibility, 3 the 24 expects one by one,
4 JVM-only helper files, 5 shared low-level helper API, 6 work packages, 7 probe results and
reference code, 8 risks, 9 open questions.

---

## 1. Cross-cutting facts

### 1.1 The expects of this part

24 declarations (the other 20 of the 44 are under `feature/`, part B):

| #  | Expect                                                               | File:line                                              |
|----|----------------------------------------------------------------------|--------------------------------------------------------|
| 1  | `fun getPlatform(): Platform`                                        | `C/Platform.kt:8`                                      |
| 2  | `suspend fun getClipboardText(): String?`                            | `C/Platform.kt:10`                                     |
| 3  | `object BrowserIntegration`                                          | `C/core/browser/BrowserIntegration.kt:47`              |
| 4  | `fun currentAppEdition(): AppEdition`                                | `C/core/edition/AppEdition.kt:18`                      |
| 5  | `fun platformSupportsTorrent(): Boolean`                             | `C/core/edition/AppEdition.kt:21`                      |
| 6  | `fun platformSupportsMediaExtractor(): Boolean`                      | `C/core/edition/AppEdition.kt:24`                      |
| 7  | `fun relocateAppPath(path: String): String`                          | `C/core/filesystem/AppContainerPath.kt:8`              |
| 8  | `class PathResolver()`                                               | `C/core/filesystem/PathResolver.kt:3`                  |
| 9  | `object TextFilePicker`                                              | `C/core/filesystem/TextFilePicker.kt:3`                |
| 10 | `object TorrentFilePicker`                                           | `C/core/filesystem/TorrentFilePicker.kt:8`             |
| 11 | `object DiagnosticsLog`                                              | `C/core/log/DiagnosticsLog.kt:8`                       |
| 12 | `internal fun applyProxyLogin(config, proxy)`                        | `C/core/network/NetworkSettings.kt:226`                |
| 13 | `fun createStreamHttpClient(): HttpClient`                           | `C/core/network/StreamHttpClient.kt:10`                |
| 14 | `fun rawProbeForDiagnostics(url, userAgent): String`                 | `C/core/network/StreamHttpClient.kt:13`                |
| 15 | `class DownloadNotificationManager()`                                | `C/core/notification/DownloadNotificationManager.kt:3` |
| 16 | `object SystemNotice`                                                | `C/core/notification/SystemNotice.kt:4`                |
| 17 | `fun localMinutesOfDay(): Int`                                       | `C/core/power/SpeedSchedule.kt:19`                     |
| 18 | `object SystemPower`                                                 | `C/core/power/WhenDone.kt:17`                          |
| 19 | `internal object CryptoBox`                                          | `C/core/security/CryptoBox.kt:10`                      |
| 20 | `internal object SecretBox`                                          | `C/core/security/Secrets.kt:49`                        |
| 21 | `object StartupLauncher`                                             | `C/core/startup/StartupLauncher.kt:14`                 |
| 22 | `object PlatformUpdater`                                             | `C/core/update/PlatformUpdater.kt:4`                   |
| 23 | `internal fun verifyUpdateSignature(path, signatureBase64): Boolean` | `C/core/update/UpdateSignature.kt:15`                  |
| 24 | `fun localUtcOffsetMs(): Long`                                       | `C/core/util/LocalClock.kt:4`                          |

`Platform.kt`, `AppEdition.kt` and `StreamHttpClient.kt` hold more than one expect each.

### 1.2 Many of these expects are called only by the Compose UI

`androidUi` and `desktopApp (removed)` are not compiled for `mingwX64`. On the native app their job is done
by the Slint UI through the C API of `fjcore.dll`. The actuals must still exist (the module does
not compile without them), and the C API layer must export what the Compose UI used to call:

| Expect                                                                                                                                                                                                                                           | Called from (JVM app)                                                                                                                                                                                                                                 | Called from common code?                                                                                                  |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `getPlatform()`                                                                                                                                                                                                                                  | `UI/TaskList.kt:148`, `UI/ui/AppScrollbar.kt:88`, `UI/ui/FdmSettingsPanel.kt:145-146` (`isJvm`, name starts with "iOS"); `JA/feature/device_sync/DeviceSyncFactory.jvmAndroid.kt:17`                                                                  | no                                                                                                                        |
| `getClipboardText()`                                                                                                                                                                                                                             | `UI/App.kt:106,115` (clipboard watcher, every 800 ms), `UI/ui/AddDownloadSections.kt:187` (Paste button)                                                                                                                                              | no                                                                                                                        |
| `BrowserIntegration`                                                                                                                                                                                                                             | `UI/ui/BrowserInstallSection.kt:63,129,133,160,217`, `main.kt:720` (`setUpOnFirstRun`)                                                                                                                                                                | no                                                                                                                        |
| `TextFilePicker`, `TorrentFilePicker`                                                                                                                                                                                                            | `UI/ui/AddDownloadSections.kt:121-150`                                                                                                                                                                                                                | no                                                                                                                        |
| `PathResolver.openFile / openFileLocation / fileExists / openDirectoryInExplorer / isOpenFolderSupported / getAvailableDirectories / listSubdirectories / createSubdirectory / getParentDirectory / getDocumentsDirectory / getDesktopDirectory` | `UI/App.kt:68-76`, `UI/TaskDetails.kt:603-604`, `UI/ui/SettingsPages.kt:266,303-305`, `UI/ui/SettingsMediaToolsPage.kt:87`, `UI/ui/DirectoryPickerDialog.kt:59-73,288`, `UI/ui/AddDownloadModalDialog.kt:122-124`, `UI/ui/AddDownloadSections.kt:395` | no (only `getDownloadsDirectory`, `getTempDirectory`, `getSettingsDirectory`, `sanitizeFileName` are used by common code) |
| `PathResolver.chooseDirectory`                                                                                                                                                                                                                   | nobody (0 call sites)                                                                                                                                                                                                                                 | no                                                                                                                        |
| `DiagnosticsLog`                                                                                                                                                                                                                                 | Android only (no desktop / common call site)                                                                                                                                                                                                          | no                                                                                                                        |
| `StartupLauncher`, `StartupPreferences`                                                                                                                                                                                                          | `UI/ui/StartupUi.kt:25-95`, `UI/ui/SettingsPages.kt:140`, `main.kt:727-751`                                                                                                                                                                           | no                                                                                                                        |
| `SystemPower.isSupported`                                                                                                                                                                                                                        | `UI/ui/WhenDoneUi.kt:44`                                                                                                                                                                                                                              | `sleep` / `shutDown` from `C/core/power/WhenDone.kt:93-94`                                                                |
| `DownloadNotificationManager.activeTasks` (JVM-only companion)                                                                                                                                                                                   | `main.kt:803` (tray tooltip)                                                                                                                                                                                                                          | the class itself: `C/feature/queue_manager/QueueManagerRepository.kt:42,395,407,423,434`                                  |
| `SystemNotice.notices` (JVM-only member)                                                                                                                                                                                                         | `main.kt:763` (tray balloon)                                                                                                                                                                                                                          | `show`: `C/presentation/dashboard/DashboardDeviceSync.kt:115,302`                                                         |
| `PlatformUpdater`                                                                                                                                                                                                                                | `UI/ui/UpdateUi.kt:60-280`                                                                                                                                                                                                                            | `C/core/update/UpdateManager.kt:150-393`                                                                                  |

### 1.3 What is available for `mingwX64` on this PC (no download needed)

- Gradle cache has `io.ktor:ktor-client-core`, `ktor-client-winhttp`, `ktor-network`,
  `ktor-http-cio`, `ktor-websockets` for `mingwx64`, all 3.6.1; okio 3.18.2; coroutines 1.11.0.
  `sharedLogic/build.gradle.kts:104-109` already adds `ktor-client-winhttp` and `ktor-network`
  to `mingwX64Main`.
- **Not** in the cache for `mingwx64`: `ktor-client-curl`, `ktor-network-tls` (the latter has
  no mingw artifact at all; only iOS and JVM are in the cache). So there is no TLS on raw
  sockets from Kotlin: HTTPS goes through WinHTTP (SChannel) only.
- Kotlin/Native 2.4.20 platform libraries
  (`~/.konan/kotlin-native-prebuilt-windows-x86_64-2.4.20/klib/platform/mingw_x64`):
  `platform.windows`, `platform.posix`, `platform.winhttp`, `platform.zlib`, `platform.iconv`,
  `platform.gdiplus`, `platform.opengl32`.
- `platform.windows` is built from `wtypes.h minwindef.h windows.h commctrl.h dwmapi.h shlobj.h
  shlwapi.h shobjidl.h urlmon.h usp10.h uxtheme.h vfw.h wininet.h winsock2.h ws2tcpip.h ws2def.h`
  with `-DUNICODE -DWINVER=0x0601 -D_WIN32_WINNT=0x0601` and links
  `-lcomctl32 -lcrypt32 -lshlwapi -lshell32 -limm32 -lusp10 -lwininet -lgdi32 -luser32
  -lkernel32 -lbcrypt -luuid -ldwmapi`.
- Checked present in `platform.windows` (symbol found in the klib): `BCryptOpenAlgorithmProvider`,
  `BCryptSetProperty`, `BCryptGenerateSymmetricKey`, `BCryptEncrypt`, `BCryptDecrypt`,
  `BCryptGenRandom`, `BCryptCreateHash`, `BCryptHashData`, `BCryptFinishHash`,
  `BCryptImportKeyPair`, `BCryptVerifySignature`, `BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO`,
  `BCRYPT_ECCKEY_BLOB`, `CryptProtectData`, `CryptUnprotectData`, `CRYPTPROTECT_UI_FORBIDDEN`,
  `DATA_BLOB`, `CryptStringToBinaryW`, `CryptDecodeObjectEx`, `CryptImportPublicKeyInfoEx2`,
  `RegCreateKeyExW`, `RegOpenKeyExW`, `RegSetValueExW`, `RegGetValueW`, `RegQueryValueExW`,
  `RegDeleteValueW`, `RegDeleteKeyW`, `RegDeleteTreeW`, `ShellExecuteW`, `ShellExecuteExW`,
  `SHGetKnownFolderPath`, `FOLDERID_Downloads / Documents / Desktop / Profile`,
  `SHOpenFolderAndSelectItems`, `ILCreateFromPathW`, `GetOpenFileNameW`, `OPENFILENAMEW`,
  `SHBrowseForFolderW`, `IFileOpenDialog`, `IShellItem`, `CLSID_FileOpenDialog`,
  `CoInitializeEx`, `CoCreateInstance`, `CoTaskMemFree`, `OpenClipboard`, `GetClipboardData`,
  `IsClipboardFormatAvailable`, `GetClipboardSequenceNumber`, `AddClipboardFormatListener`,
  `GetTimeZoneInformation`, `GetDynamicTimeZoneInformation`, `GetLocalTime`, `CreateProcessW`,
  `CreatePipe`, `PeekNamedPipe`, `SetHandleInformation`, `STARTUPINFOW`, `CREATE_NO_WINDOW`,
  `DETACHED_PROCESS`, `CreateJobObjectW`, `AssignProcessToJobObject`,
  `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`, `TerminateProcess`, `GetExitCodeProcess`,
  `WaitForSingleObject`, `ExitWindowsEx`, `InitiateSystemShutdownExW`, `OpenProcessToken`,
  `LookupPrivilegeValueW`, `AdjustTokenPrivileges`, `Shell_NotifyIconW`, `NOTIFYICONDATAW`,
  `GetTickCount64`, `GetModuleFileNameW`, `GetTempPathW`, `GetEnvironmentVariableW`,
  `GetCurrentProcessId`, `QueryFullProcessImageNameW`, `LoadLibraryW`, `GetProcAddress`,
  `MoveFileExW`, `ReplaceFileW`, `FindFirstFileW`, `GetFileAttributesW`, `CreateMutexW`,
  `FormatMessageW`.
- **Missing** from `platform.windows`: `SetSuspendState` (powrprof.dll),
  `GetCurrentPackageFullName` (Windows 8 API, the bindings are built for Windows 7),
  `CreateToolhelp32Snapshot`, the `BCRYPT_*_ALGORITHM` / `BCRYPT_CHAIN_MODE_GCM` wide-string
  macros (pass the Kotlin strings `"AES"`, `"SHA256"`, `"ECDSA_P256"`, `"ChainingMode"`,
  `"ChainingModeGCM"`, `"ECCPUBLICBLOB"` instead; VERIFIED). Missing functions are reached with
  `LoadLibraryW` + `GetProcAddress` + a `CFunction` pointer, without a `.def` file.
- **`ole32` and `comdlg32` are not linked by default** (the bindings exist, the import
  libraries are not on the link line). A probe that calls `CoTaskMemFree` failed to link
  (`ld.lld: error: undefined symbol: __declspec(dllimport) CoTaskMemFree`) until the binary got
  `linkerOpts("-lole32")`; one that calls `GetOpenFileNameW` failed the same way until it got
  `-lcomdlg32`. VERIFIED. So anything from ole32 (`CoInitializeEx`, `CoCreateInstance`,
  `CoTaskMemFree`, so also freeing `SHGetKnownFolderPath` results, `IFileOpenDialog`,
  `SHOpenFolderAndSelectItems`) and the classic file dialog need either
  `linkerOpts("-lole32", "-lcomdlg32")` on the `mingwX64` binaries in
  `sharedLogic/build.gradle.kts` (a shared build file: its owner must add it), or a run-time
  lookup (`LoadLibraryW("comdlg32.dll")` + `GetProcAddress(lib, "GetOpenFileNameW")` + calling
  the `CFunction` pointer: VERIFIED that this lookup and call style works, with
  `powrprof.dll!IsPwrSuspendAllowed` and `kernel32!GetCurrentPackageFullName`), or code that
  avoids them (`%USERPROFILE%` paths, `ShellExecuteW`, `explorer /select`). advapi32 (registry),
  shell32, user32, kernel32, crypt32 and bcrypt link without any option (VERIFIED).
- The struct `GUID` is `platform.posix._GUID` in Kotlin (not `platform.windows.GUID`). VERIFIED.
- `const wchar_t*` parameters accept a Kotlin `String` directly (`RegCreateKeyExW(HKEY_CURRENT_USER,
  "Software\\x", ...)`, `BCryptOpenAlgorithmProvider(h.ptr, "AES", null, 0u)`). VERIFIED.
- okio 3.18.2 on mingw uses the wide Win32 / CRT calls (`CreateFileW`, `_wfopen`, `_wstat64`,
  `_wmkdir`, `_wopendir`, `_wremove`, `_wrmdir`, `MoveFileExW`: read from its klib), so Arabic
  file names work through `FileSystem.SYSTEM`. `platform.posix.getenv` / `fopen` are ANSI:
  use `GetEnvironmentVariableW` / `_wgetenv` / okio instead.

### 1.4 The ktor WinHTTP engine (read from the klib `ktor-client-winhttp-mingwX64Main-3.6.1.klib`)

- Config class `WinHttpClientEngineConfig`: `protocolVersion: HttpProtocolVersion` (HTTP/2 is
  switched on through `WINHTTP_OPTION_ENABLE_HTTP_PROTOCOL`; the default value was not
  readable from the klib, so set `HTTP_1_1` explicitly where it matters), `securityProtocols`,
  `sslVerify`, plus the common `proxy`.
- Supports `HttpTimeout` (`WinHttpSetTimeouts`: resolve / connect / send / receive), WebSockets,
  SSE.
- Proxy: only `ProxyType.HTTP` (a `WINHTTP_PROXY_INFO` with `lpszProxy`). Any other type throws
  `IllegalStateException("Proxy of type <type> is unsupported by WinHTTP engine.")`. **No SOCKS.**
- **No proxy credentials**: the engine exposes neither `WinHttpSetCredentials` nor
  `WINHTTP_OPTION_PROXY_USERNAME`. An HTTPS request through an HTTP proxy that asks for a login
  gets 407 from the `CONNECT` and fails.
- NOT VERIFIED: whether the engine follows the Windows system proxy / WPAD when `proxy` is null (the
  JVM app goes direct unless the app's own proxy is set).
- NOT VERIFIED: that googlevideo answers 206 to ranged requests made by WinHTTP. The JVM comment
  (`J/core/network/StreamHttpClient.jvm.kt:10-14`) says OkHttp got 403 and `java.net.http` got 206
  for the same request. `curl.exe` on Windows (SChannel, like WinHTTP) gets 206 per `CLAUDE.md`.
- Five places in common code build `HttpClient { ... }` with no engine argument
  (`C/core/browser/ExtensionStore.kt:24`, `C/core/update/UpdateManager.kt:127`,
  `C/feature/http_downloader/HttpChunkDownloader.kt:39`,
  `C/feature/media_extractor/MediaStreamDownloader.kt:60`,
  `C/feature/torrent_engine/TorrentDownloader.kt:148`). On Native this relies on the engine
  registering itself when the library loads. The prototype
  (`desktopNative/core/src/mingwX64Main/kotlin/FjCore.kt:87`)
  used `HttpClient(WinHttp)` explicitly, so the default lookup inside a DLL is NOT VERIFIED.

### 1.5 Logging on Native

`println` is the app's log (`C/core/log/AppLog.kt`: `d {}` when `AppLog.verbose`, `e()` always;
plus many direct `println` in the engines). On the JVM `main.kt:277-304` (`installCrashLog`)
redirects `System.out` / `System.err` into `~/.fetch_jet/logs/app.log` through
`JA/core/log/RedactingOutputStream.kt`. Kotlin/Native has no `System.setOut`, but `println`
writes to C runtime file descriptor 1: redirecting fd 1 into a pipe with `_pipe` + `_dup2`
captured a `println` (Arabic text intact), also when the process was started with stdout and
stderr closed. VERIFIED in a console exe; NOT VERIFIED inside `fjcore.dll` loaded by the MSVC
host (the Kotlin DLL has its own C runtime, so the redirect must be done from Kotlin code, not
from `main.cpp`).

### 1.6 Installed build, Store build, development build

The JVM app decides three things from system properties that do not exist on Native:

| JVM test                                                                                                                            | Meaning                                          | Used by                                                                                                 |
|-------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `System.getProperty("jpackage.app-path")` exists as a file (`DesktopInstall.launcherPath`, `J/core/update/DesktopUpdater.kt:32-33`) | running the installed launcher, not Gradle / IDE | in-app update (`WindowsUpdater.canInstallInApp`), relaunch after update, `fetchjet://` registration     |
| process executable name is not `java` (`AutoLaunch.isRunningFromDistributable`)                                                     | same idea                                        | `StartupLauncher.isSupported`                                                                           |
| path contains `\WindowsApps\` (`WindowsStorePackage.isInstalled`, `J/core/platform/WindowsStorePackage.kt:9-14`)                    | Microsoft Store (MSIX) build                     | no in-app update, no Run key, no Edge registry entry, no `fetchjet://` registration, Store update check |

The native app is always an `.exe`, so "installed or development run" needs a new rule (open
question 9.2). It matters on the owner's PC: a development build that registered `fetchjet://`
or the Run key would take them away from the installed FetchJet, and one that used
`C:/Users/miche/.fetch_jet` would touch his real data.

---

## 2. On-disk compatibility (app home `%USERPROFILE%\.fetch_jet`)

The native app must read and write the same files as the JVM app, so a user can go from one to
the other (and back) without losing tasks, settings or saved passwords. Files this part owns
or touches:

| File (under the app home)                                                                                      | Written by                                                                                                                                      | Format                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|----------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `settings.properties`                                                                                          | `C/core/settings/AppSettingsRepository.kt:117-141`                                                                                              | UTF-8, `\n` lines, first line `# FetchJet Settings`, then `key=value` (no escaping): `destinationPath`, `disableTorrentUpload`, `isDarkMode`, `themeMode` (`system`/`light`/`dark`), `globalDownloadSpeedLimitBytes`, `globalUploadSpeedLimitBytes`, `speedDisplayUnit`, `language`, `speedPresetLow/Medium/Fast/HighBytes`, `...Unit`. `destinationPath` is an absolute Windows path with backslashes as the JVM wrote it. Common code: nothing to port. |
| `behavior.properties`, `startup.properties`, `updates.properties`, `queue.properties`                          | common code (`BehaviorSettings`, `StartupPreferences`, `UpdateManager.save` `C/core/update/UpdateManager.kt:500-513`, `QueueManagerRepository`) | same `key=value` style. `updates.properties`: `# FetchJet update settings`, `autoCheck`, `autoInstall`, `lastCheckedMs`, `skippedVersion`. `startup.properties`: `# FetchJet startup settings`, `startHidden`.                                                                                                                                                                                                                                            |
| `network.json`                                                                                                 | `C/core/network/NetworkSettings.kt:167-185`                                                                                                     | kotlinx.serialization, pretty printed, `{"proxy":{"kind":"NONE                                                                                                                                                                                                                                                                                                                                                                                            |HTTP|SOCKS5","host","port","user","password"},"logins":[{"site","user","password"}]}`; every `password` is `fjenc1:...` (or plain text in files from before 1.2.0: encrypted on first load). |
| `bridge.json`                                                                                                  | `C/core/network/BridgePairing.kt:256-272`                                                                                                       | `{"token":"fjenc1:...","legacyOrigins":[],"blockedOrigins":[],"seenBrowsers":{},"seenVersions":{}}`.                                                                                                                                                                                                                                                                                                                                                      |
| `schedule.json`                                                                                                | `C/core/power/SpeedSchedule.kt:151-160`                                                                                                         | `{"enabled":bool,"windows":[{"startMinutes","endMinutes","capBytes","capUnit"}]}`.                                                                                                                                                                                                                                                                                                                                                                        |
| `devices.json`, `tasks_cache.json`, `torrents/<taskId>.torrent`, `device-sync-tmp/`, `tools/`, `cache/yt-dlp/` | part B                                                                                                                                          | `tasks_cache.json` and `devices.json` hold `fjenc1:` values (task URL, original URL, paired audio URL, Cookie header, pairing key) made by `Secrets`.                                                                                                                                                                                                                                                                                                     |
| `secrets.key`                                                                                                  | `J/core/security/WindowsKeyVault.kt`                                                                                                            | one line of text: Base64 of a DPAPI blob (`CryptProtectData`, current user, no entropy) of the raw 32-byte AES key. Written through a temp file `secrets.key.tmp` then renamed.                                                                                                                                                                                                                                                                           |
| `browser-integration.done`                                                                                     | `J/core/browser/BrowserIntegration.jvm.kt:136-141`                                                                                              | text `edge`; its existence means "the Edge registry entry was offered once".                                                                                                                                                                                                                                                                                                                                                                              |
| `extension/chromium/**`                                                                                        | `J/core/browser/DesktopBrowsers.kt:186-228`                                                                                                     | the unpacked Chromium extension (refreshed at every start when `manifest.json` is already there).                                                                                                                                                                                                                                                                                                                                                         |
| `extension/firefox/<name>.xpi`                                                                                 | `J/core/browser/DesktopBrowsers.kt:145-159`                                                                                                     | the signed `.xpi` handed to Firefox.                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `logs/app.log`                                                                                                 | `main.kt:277-304`                                                                                                                               | UTF-8 text, appended; deleted at start when larger than 2 MB (2 * 1024 * 1024 bytes); every line passed through `LogRedact.redact`.                                                                                                                                                                                                                                                                                                                       |

Outside the app home:

| Place                                                                                                                                                      | What                                                                                                                                                                                                                    |
|------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `%TEMP%\FetchJetDownloads\` (`PathResolver.getTempDirectory()`)                                                                                            | scratch files of running downloads (`.partN`, `.chunks`, part B) and `fetchjet-update\<installer>`, `<installer>.part`, `fetchjet-update.cmd`. Unfinished downloads resume only if the native app uses the same folder. |
| `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, value `com.michelle.raouf.fetch_jet`                                                                 | autostart (see 3.21).                                                                                                                                                                                                   |
| `HKCU\Software\Microsoft\Edge\Extensions\phngekpnkcnpkjkcoggcfmlmebmociaj`, value `update_url` = `https://edge.microsoft.com/extensionwebstorebase/v1/crx` | Edge hand-off (see 3.3).                                                                                                                                                                                                |
| `HKCU\Software\Classes\fetchjet` (+ `\DefaultIcon`, `\shell\open\command`)                                                                                 | `fetchjet://` URL scheme (see 3.3).                                                                                                                                                                                     |

### 2.1 Encrypted values (`fjenc1:`), exact format

`C/core/security/Secrets.kt:13-46`:

- stored text = `"fjenc1:"` + standard Base64 with padding (`kotlin.io.encoding.Base64.Default`)
  of `SecretBox.seal(plain UTF-8 bytes)`;
- JVM `SecretBox.seal` (`J/core/security/SecretBox.jvm.kt:28-33`) = 12 random bytes (IV) ++
  AES-256-GCM ciphertext ++ 16-byte tag, no additional data, key = the 32 bytes behind
  `secrets.key`;
- empty strings and strings that already start with `fjenc1:` are stored as they are;
- when sealing throws, the plain text is stored and `Secrets: could not encrypt (<message>),
  stored as is` is logged; when opening throws, `reveal` returns null and logs
  `Secrets: could not decrypt (<message>)`. Callers keep the encrypted text and write it back
  unchanged (`NetworkSettings.lockedProxyPassword`, `lockedLogins`), so a file from another
  Windows user is not destroyed.

The iOS format is different (AES-256-CBC + HMAC-SHA256, 64-byte key,
`I/core/security/SecretBox.ios.kt`);
it is not relevant for Windows: only the JVM desktop format has to be matched.

VERIFIED: a blob made by the exact PowerShell call the JVM app uses
(`[Security.Cryptography.ProtectedData]::Protect($b, $null, CurrentUser)`) is opened by native
`CryptUnprotectData(in, NULL, NULL, NULL, NULL, CRYPTPROTECT_UI_FORBIDDEN, out)`, and a blob
made by native `CryptProtectData` with the same arguments is opened by .NET `Unprotect`.
VERIFIED: native BCrypt AES-256-GCM gives the NIST vector (key 32 x 00, nonce 12 x 00, plain
16 x 00 -> `cea7403d4d606b6e074ec5d3baf39d18`, tag `d0d1c8a799996bf0265b98b5d48ab919`), the same
algorithm Java's `AES/GCM/NoPadding` implements.

---

## 3. The expects, one by one

Size = estimated lines of the new `M/` file (s), without the shared helpers of section 5.
Risk: LOW = straight port, MEDIUM = works but has an untested edge, HIGH = may need a different
design.

### 3.1 `getPlatform()` - `C/Platform.kt:8`

```kotlin
interface Platform {
    val name: String
    val isJvm: Boolean get() = false
}

expect fun getPlatform(): Platform
```

- Callers: `isJvm` means "desktop" in the Compose UI (`UI/TaskList.kt:148` checkbox rows,
  `UI/ui/AppScrollbar.kt:88`, `UI/ui/FdmSettingsPanel.kt:145-245`: Media tools page, engine row,
  browser page text "Edge · Firefox · Chrome"); `name.startsWith("iOS")` at
  `UI/ui/FdmSettingsPanel.kt:146`. `JA/feature/device_sync/DeviceSyncFactory.jvmAndroid.kt:16-29`
  builds the device name: `"<host> · Windows"` on Windows (host =
  `InetAddress.getLocalHost().hostName`
  before the first dot, skipped when blank or `localhost`), and reads `getPlatform().name` only
  to detect Android.
- JVM (`J/Platform.jvm.kt:6-11`):
  `class JVMPlatform : Platform { name = "Java ${System.getProperty("java.version")}"; isJvm = true }`.
- iOS (`I/Platform.ios.kt:5-10`): `name = UIDevice.systemName + " " + systemVersion`.
- Native plan: `class WindowsPlatform : Platform { override val name = "Windows" }` (`isJvm`
  stays false). No common code reads `isJvm`; the Slint UI is always the desktop layout.
  `M/Platform.mingw.kt`, ~10 lines. Risk LOW. The device-sync name (part B) should use
  `GetComputerNameW` to give the same `"<host> · Windows"`.

### 3.2 `getClipboardText()` - `C/Platform.kt:10`

```kotlin
expect suspend fun getClipboardText(): String?
```

- Callers: the clipboard watcher (`UI/App.kt:94-130`: reads once at start to ignore what is
  already there, then every 800 ms; a new text is offered for download when it is a link) and
  the Paste chip of the Add dialog (`UI/ui/AddDownloadSections.kt:185-193`, trims the text).
  Both swallow exceptions; null means "nothing".
- JVM (`J/Platform.jvm.kt:13-23`): on `Dispatchers.IO`,
  `java.awt.Toolkit.getDefaultToolkit().systemClipboard.getContents(null)`,
  returns the `DataFlavor.stringFlavor` data when that flavor is offered, else null; any
  `Throwable` -> null.
- iOS: `UIPasteboard.generalPasteboard.string`.
- Native plan: `withContext(Dispatchers.IO)`; `IsClipboardFormatAvailable(CF_UNICODETEXT)`;
  `OpenClipboard(null)` (retry 3 times 10 ms apart: another program may hold it);
  `GetClipboardData(CF_UNICODETEXT)`; `GlobalLock` -> `toKString()`; `GlobalUnlock`;
  `CloseClipboard`. Windows makes `CF_UNICODETEXT` from `CF_TEXT` by itself. VERIFIED (read the
  length of the current clipboard). The native clipboard watcher (C API side) should compare
  `GetClipboardSequenceNumber()` first and read the text only when it changed.
  In `M/Platform.mingw.kt`, ~35 lines. Risk LOW.

### 3.3 `BrowserIntegration` - `C/core/browser/BrowserIntegration.kt:47`

Types in the same file (lines 3-36):
`const val EDGE_EXTENSION_ID = "phngekpnkcnpkjkcoggcfmlmebmociaj"`,
`const val FIREFOX_ADDON_URL = "https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/"`,
`data class BrowserInfo(id, name, installed, downloadUrl, hasLocalCopy = false)`,
`sealed interface AddExtensionResult { Opened; LoadUnpacked(folder, page); InstallFromFile(fileName); NotInstalled; Failed(reason) }`.

```kotlin
/**
 * Adds the FetchJet extension to the user's browsers the way IDM / FDM do: on Windows a
 * per-user registry entry tells Microsoft Edge to fetch the extension from Edge Add-ons, and
 * Edge then asks the user to turn it on ("New extension added"). Nothing is installed
 * silently; the browser always asks.
 *
 * Chrome only accepts this for extensions in the Chrome Web Store, and Firefox has no
 * equivalent, so for them the app opens the store / install page instead.
 */
expect object BrowserIntegration {
    /** True where the Edge registry hand-off exists (Windows desktop). */
    val canAddToEdgeAutomatically: Boolean

    /** Whether the Edge entry is already written. */
    fun isEdgeRegistered(): Boolean

    /** Writes the Edge entry; Edge asks the user to enable the extension on its next start. */
    fun addToEdge(): Result<Unit>

    /** Removes the entry (the extension itself is removed from Edge's own settings). */
    fun removeFromEdge(): Result<Unit>

    /** Called once at app start: writes the Edge entry the first time FetchJet runs. */
    fun setUpOnFirstRun()

    /** The browsers FetchJet knows on this platform, installed ones first. */
    fun browsers(): List<BrowserInfo>

    /** Adds the extension to browser [id] (see [AddExtensionResult]). */
    fun addExtension(id: String): AddExtensionResult

    /** Shows [folder] in the system file manager. */
    fun openFolder(folder: String)

    /**
     * Installs a signed Firefox build ([bytes] of [fileName], from a FetchJet release) into
     * browser [id]: desktop opens it in Firefox (Firefox asks "Add?"), Android saves it to
     * Downloads for "Install extension from file".
     */
    fun installXpi(id: String, bytes: ByteArray, fileName: String): AddExtensionResult

    /** Starts browser [id]. */
    fun openBrowser(id: String)

    /**
     * Android: "Keep ready for the browser". Android freezes apps in the background, so the
     * Firefox extension could not ask FetchJet for video qualities until the app was opened.
     * A quiet foreground service (with a notification) keeps it answering. Off by default.
     */
    val canKeepReadyForBrowser: Boolean
    fun isKeptReadyForBrowser(): Boolean
    fun setKeepReadyForBrowser(enabled: Boolean)

    /** Called at app start: starts the service again if the user turned the option on. */
    fun restoreKeepReady()

    /**
     * Android 10+: an app in the background may not open its own screen unless it may
     * "display over other apps". Without it a link from Firefox only shows a notification.
     */
    val canPopUpOverBrowser: Boolean
    fun isPopUpAllowed(): Boolean
    fun requestPopUpPermission()
}
```

- Callers: `main.kt:717-722` runs `setUpOnFirstRun()` on a daemon thread at start.
  `UI/ui/BrowserInstallSection.kt`: `browsers()` once (line 63),
  `installXpi(b.id, bytes, xpi.fileName)`
  after `ExtensionStore.download` (129), `addExtension(b.id)` (133), `openBrowser(b.id)` (160),
  `openFolder(r.folder)` for a `LoadUnpacked` result (217). `addToEdge`, `removeFromEdge`,
  `isEdgeRegistered`, `canAddToEdgeAutomatically` have no UI caller today (used only inside
  `setUpOnFirstRun`).
- JVM actual `J/core/browser/BrowserIntegration.jvm.kt` (153 lines), all through `reg.exe`
  (`ProcessBuilder(listOf("reg") + args).redirectErrorStream(true)`, output read fully, then
  `waitFor(10 s)`; timeout -> `-1 to "timeout"`, exception -> `-1 to (message ?: "reg failed")`):
    - `isWindows` (private) = OS name contains "win" **and not** `WindowsStorePackage.isInstalled`
      (the Store build may not change Edge settings; its registry writes are private to the
      package).
    - `EDGE_KEY = "HKCU\Software\Microsoft\Edge\Extensions\$EDGE_EXTENSION_ID"`,
      `EDGE_UPDATE_URL = "https://edge.microsoft.com/extensionwebstorebase/v1/crx"`.
    - `canAddToEdgeAutomatically = isWindows`.
    - `isEdgeRegistered()` = `isWindows && reg query EDGE_KEY /v update_url` exits 0.
    - `addToEdge()`: not Windows -> `failure(UnsupportedOperationException("Windows only"))`;
      `reg add EDGE_KEY /v update_url /t REG_SZ /d EDGE_UPDATE_URL /f`; non-zero ->
      `failure(Exception(output.trim().ifBlank { "reg add failed" }))`.
    - `removeFromEdge()`: `reg delete EDGE_KEY /f`; failure text `"reg delete failed"` when blank.
    - `registerUrlScheme()` (public, JVM-only, lines 63-118): only on Windows, not the Store
      build, and only when `jpackage.app-path` exists (`exe`). Reads the default value of
      `HKCU\Software\Classes\fetchjet\shell\open\command`; when it already contains `exe`
      (ignore case) nothing is written. Else writes four values with `/f`:
      `HKCU\Software\Classes\fetchjet` default = `URL:FetchJet link`; same key, value
      `URL Protocol` = empty string; `...\fetchjet\DefaultIcon` default = `"<exe>",0`;
      `...\fetchjet\shell\open\command` default = `"<exe>" "%1"`. Exceptions swallowed.
    - `setUpOnFirstRun()` (131-144): `registerUrlScheme()`; a daemon thread runs
      `DesktopBrowsers.refreshUnpackedExtension()`; then, only when `isWindows`: marker file
      `<user.home>/.fetch_jet/browser-integration.done`; when it exists return; else create the
      folder, `if (!isEdgeRegistered()) addToEdge()`, write the text `edge` into the marker.
    - `browsers()`, `addExtension`, `openFolder`, `installXpi`, `openBrowser` delegate to
      `DesktopBrowsers` (section 4.1).
    - `canKeepReadyForBrowser = false`, `canPopUpOverBrowser = false`, `isPopUpAllowed() = true`,
      `isKeptReadyForBrowser() = false`, the setters / `restoreKeepReady` / `requestPopUpPermission`
      do nothing.
- iOS (`I/core/browser/BrowserIntegration.ios.kt`, 28 lines): everything off; `browsers()` empty;
  `addToEdge` / `removeFromEdge` fail with `UnsupportedOperationException("Desktop only")`;
  `addExtension` / `installXpi` return `NotInstalled`. This is the shape for a first stub.
- On-disk / registry constraints: the marker file and the three registry places of section 2.
  Keep the marker name and content, so a user who removed the Edge entry is not asked again
  after moving to the native app.
- Native plan (`M/core/browser/BrowserIntegration.mingw.kt` ~130 lines +
  `M/core/browser/DesktopBrowsers.kt` ~150 lines, section 4.1):
    - registry through `WinRegistry` (section 5) instead of `reg.exe` (no console flash, no 10 s
      waits): `getString(key, value)`, `setString`, `deleteTree`. Error text for a failed
      write: `"reg add failed"` / `"reg delete failed"` followed by the Win32 error number.
    - `exe` for `registerUrlScheme` = `NativeInstall.launcherPath` (section 5; null in a
      development build, so nothing is registered there).
    - the "bundled extension" is a folder next to the executable (`<exeDir>\extension\chromium`),
      not a jar resource; see 4.1.
    - threads: `setUpOnFirstRun` is called by the C API init on a background thread; the refresh
      of the unpacked extension runs in a `CoroutineScope(Dispatchers.IO)` job.
    - Risk LOW (plain registry and file work), with the development-build caveat of 1.6.

### 3.4 - 3.6 `currentAppEdition()`, `platformSupportsTorrent()`,
`platformSupportsMediaExtractor()` - `C/core/edition/AppEdition.kt:18,21,24`

```kotlin
/**
 * FetchJet ships in two editions (like Palladium does on iOS):
 *  - FULL: everything (plain HTTP, media extractor via yt-dlp, BitTorrent). Desktop, Android,
 *    and the iOS build distributed as an .ipa on GitHub / AltStore.
 *  - STORE: App Store compliant. Plain HTTP downloads only: Apple rejects apps that download
 *    from YouTube-style sites (guideline 5.2.3) and BitTorrent clients.
 *
 * On iOS the edition is read from Info.plist (`FetchJetEdition`, set by the xcconfig
 * variable FETCHJET_EDITION). Everywhere else it is FULL.
 */
enum class AppEdition(val label: String) {
    FULL("Full"),
    STORE("App Store")
}

expect fun currentAppEdition(): AppEdition

/** Whether this platform ships a BitTorrent engine at all (iOS does not, yet). */
expect fun platformSupportsTorrent(): Boolean

/** Whether this platform can run the media extractor (yt-dlp) in-process or as a tool. */
expect fun platformSupportsMediaExtractor(): Boolean

object AppFeatures {
    val edition: AppEdition get() = currentAppEdition()
    val mediaExtractor: Boolean get() = edition == AppEdition.FULL && platformSupportsMediaExtractor()
    val torrent: Boolean get() = edition == AppEdition.FULL && platformSupportsTorrent()
}
```

- Callers: `C/core/update/UpdateManager.kt:144` (`supported = edition != STORE`);
  `AppFeatures.torrent / mediaExtractor` gate the engines
  (`C/presentation/dashboard/DashboardAddDialogEvents.kt:65-66`,
  `DashboardAddDownload.kt:473-481`: a torrent or media link falls back to plain HTTP when the
  feature is off), the bridge `/config` answer (`main.kt:416`), and the UI (sidebar filters,
  Add dialog engine chips, settings sections).
- JVM / Android (`JA/core/edition/AppEdition.jvmAndroid.kt`): `FULL`, `true`, `true`.
- iOS (`I/core/edition/AppEdition.ios.kt`): edition from Info.plist `FetchJetEdition`; the two
  booleans are "the Swift bridge registered itself".
- Native plan (`M/core/edition/AppEdition.mingw.kt`, ~8 lines): `FULL`;
  `platformSupportsMediaExtractor() = true` (yt-dlp / ffmpeg / deno are separate processes,
  part B); `platformSupportsTorrent()` must be `true` only once part B has a native torrent
  backend: while it returns false the UI hides torrents and torrent links are not sent to a
  missing engine, which is the right behaviour for an intermediate build. Risk LOW.

### 3.7 `relocateAppPath()` - `C/core/filesystem/AppContainerPath.kt:8`

```kotlin
/**
 * A path saved by an earlier install, pointed at this install's app folder. iOS gives the app a
 * new data container on reinstall, so a saved absolute path would point at a folder that no
 * longer belongs to the app. Elsewhere the path is returned unchanged.
 */
expect fun relocateAppPath(path: String): String
```

- Callers: `C/core/settings/AppSettingsRepository.kt:63-64` (`destinationPath`),
  `C/feature/queue_manager/TaskCache.kt:136` (each task's destination).
- JVM / Android (`JA/core/filesystem/AppContainerPath.jvmAndroid.kt`): `= path`.
  iOS: rebases `.../Containers/Data/Application/<UUID>` onto `NSHomeDirectory()`.
- Native plan: `actual fun relocateAppPath(path: String): String = path`
  (`M/core/filesystem/AppContainerPath.mingw.kt`, 3 lines). Risk LOW.

### 3.8 `PathResolver` - `C/core/filesystem/PathResolver.kt:3`

```kotlin
expect class PathResolver() {
    fun getDownloadsDirectory(): String
    fun getDocumentsDirectory(): String
    fun getDesktopDirectory(): String
    fun getTempDirectory(): String
    fun getSettingsDirectory(): String
    fun sanitizeFileName(fileName: String): String
    fun isOpenFolderSupported(): Boolean
    fun openDirectoryInExplorer(path: String): Boolean
    fun openFileLocation(filePath: String): Boolean
    fun openFile(filePath: String): Boolean
    fun fileExists(filePath: String): Boolean
    fun chooseDirectory(initialPath: String? = null): String?
    fun getAvailableDirectories(): List<Pair<String, String>>
    fun listSubdirectories(path: String): List<String>
    fun listSubdirectories(path: String, includeHidden: Boolean): List<String>
    fun createSubdirectory(parentPath: String, name: String): String?
    fun getParentDirectory(path: String): String?
}
```

- Callers in common code (the ones the engines rely on):
    - `getSettingsDirectory()`: 13 sites (`BridgePairing.kt:54`, `NetworkSettings.kt:80`,
      `SpeedSchedule.kt:45`, `AppSettingsRepository.kt:32`, `BehaviorSettings.kt:16`,
      `StartupLauncher.kt:32`, `UpdateManager.kt:124`, `PairedDeviceStore.kt:20`,
      `QueueManagerRepository.kt:57,168`, `TorrentDownloader.kt:174`, `DashboardDeviceSync.kt:121`,
      `DashboardViewModel.kt:100`). Always used as `"${dir}/file".toPath()`: the result has no
      trailing separator and okio accepts the mixed `\` and `/`. It is called very often
      (`PathResolver()` is created per call in several objects), so it must be cheap: cache the
      string after the first `mkdirs`.
    - `getTempDirectory()`: 13 sites (`UpdateManager.kt:411`, `HttpChunkDownloader.kt:129,188`,
      `HttpChunkFiles.kt:13,28`, `MediaStreamDownloader.kt:114,164,200`,
      `QueueManagerRepository.kt:707`, `DashboardDeviceSync.kt:131,264,275`,
      `DashboardViewModel.kt:101`).
    - `getDownloadsDirectory()`: 16 sites (default destination).
    - `sanitizeFileName()`: 20 sites; the result is the on-disk file name, so it must be
      character-for-character the same as the JVM one (a resumed task looks for its file by name).
    - UI-only methods: see the table in 1.2. `openDownloadedFile` (`UI/App.kt:68-77`):
      `if (!fileExists(path)) show "file is missing" dialog; else if (!openFile(path)) openFileLocation(path)`.
      `UI/ui/DirectoryPickerDialog.kt` is the app's own folder chooser: start folder =
      `initialPath` or `getDownloadsDirectory()`, quick list = `getAvailableDirectories()`,
      "up" = `getParentDirectory(current)` (null hides the button), list =
      `listSubdirectories(current, showHidden)`, "new folder" = `createSubdirectory(current, name)`.
- JVM actual `J/core/filesystem/PathResolver.jvm.kt` (347 lines), Windows behaviour:
    - `getDownloadsDirectory()` (10-19): `home = System.getProperty("user.home") ?: "."`; when
      `<home>\OneDrive\Downloads` exists -> that; else `<home>\Downloads` (created when missing).
      It does **not** ask Windows for the real Downloads folder (a moved Downloads folder is
      ignored).
    - `getDocumentsDirectory()` (21-30): same with `OneDrive\Documents` / `Documents`.
    - `getDesktopDirectory()` (32-41): same with `OneDrive\Desktop` / `Desktop`.
    - `getSettingsDirectory()` (43-49): `<home>\.fetch_jet`, created when missing (POSIX mode 700
      on macOS / Linux only).
    - `getTempDirectory()` (51-62):
      `File(System.getProperty("java.io.tmpdir") ?: ".", "FetchJetDownloads")`,
      created when missing; on Windows returned as is. `java.io.tmpdir` on Windows is
      `GetTempPathW` (`C:\Users\<user>\AppData\Local\Temp\`), so the result is
      `C:\Users\<user>\AppData\Local\Temp\FetchJetDownloads`.
    - `sanitizeFileName(fileName)` (79-96), exact steps:
        1. `decodeUnicodeEscapes` (337-347): every `\uXXXX` or `_uXXXX` (regex
           `(?:\\u|_u)([0-9a-fA-F]{4})`) becomes that UTF-16 code unit;
        2. replace regex `[\\/:*?"<>|\u0000-\u001f]` with `_`; `trim()`; `trimEnd('.', ' ')`;
        3. `"."` or `".."` -> `""`;
        4. when the text before the first `.` (upper-cased) matches
           `^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])$`
           -> prefix `_`;
        5. `ext = substringAfterLast(".", "")`, `name = substringBeforeLast(".")` when ext is not
           empty; a name longer than 80 chars -> `take(80).trimEnd()`;
        6. result `name.ext` (or `name`); blank -> `"download_file"`.
    - `chooseDirectory(initialPath)` (98-121): Swing `JFileChooser` (system look and feel,
      `DIRECTORIES_ONLY`, title `Select Download Directory`, start folder when it exists), returns
      the absolute path or null. No caller.
    - `isOpenFolderSupported() = true`.
    - `openDirectoryInExplorer(path)` (125-144): target = the path when it is a directory, its
      parent when it is a file, the parent when only the parent exists, else the Downloads
      folder; created when missing; `java.awt.Desktop.getDesktop().open(dir)`; false on exception.
    - `resolveExistingFile(filePath)` (private, 152-166): the file when it exists; else, in its
      parent folder, the newest (`lastModified`) regular file that is not `*.part`, `*.ytdl`,
      `*.temp`, `*.aria2` and whose name without extension equals the stem (ignore case), or
      whose name starts with `"<stem>."` or `"<stem> ("` (ignore case); stem =
      `nameWithoutExtension.ifBlank { name }`. This is how a task whose engine renamed the file
      (`.mkv` after a merge, `name (1).ext`) still opens.
    - `openFileLocation(filePath)` (187-232), Windows: file =
      `resolveExistingFile ?: File(filePath)`;
      missing -> `openDirectoryInExplorer(parent ?: filePath)`; else
      `Desktop.browseFileDirectory(file)` when supported, else the exact command line
      `explorer.exe /select,"<absolute path>"` through `Runtime.exec(String)` (one token; a
      `ProcessBuilder` would re-quote it), else `openDirectoryInExplorer(parent)`.
    - `fileExists(filePath) = resolveExistingFile(filePath) != null`.
    - `openFile(filePath)` (236-283): `resolveExistingFile ?: return false`; a directory ->
      `openDirectoryInExplorer`; `Desktop.open(file)`; when that throws (no associated program,
      `.exe` / `.msi` needing elevation, `.lnk`...) -> `cmd.exe /c start "" "<path>"` (each
      argument pre-quoted), which shows the UAC prompt or Windows' "How do you want to open this
      file?"; false only when that also throws.
    - `getAvailableDirectories()` (285-294): `"Downloads"`, `"Documents"`, `"Desktop"` (in this
      order, English labels) with their paths, each only when it exists or could be created.
    - `listSubdirectories(path, includeHidden)` (300-311): names of the sub-folders, those
      starting with `.` left out unless `includeHidden`, sorted with
      `String.CASE_INSENSITIVE_ORDER`;
      empty on any problem. The Windows "hidden" attribute is not looked at.
    - `createSubdirectory(parentPath, name)` (313-326): `sanitizeFileName(name)`; blank -> null;
      exists or `mkdirs()` -> absolute path, else null.
    - `getParentDirectory(path)` (328-335): the parent when it exists and is readable, else null (so
      `C:\` has no parent: the dialog cannot go to another drive, same as today).
    - APIs: `java.io.File`, `java.nio.file.Files`, `java.awt.Desktop`, `javax.swing.JFileChooser`,
      `Runtime.exec`, `ProcessBuilder`.
- iOS (`I/core/filesystem/PathResolver.ios.kt`, 320 lines): the pure-Kotlin parts
  (`sanitizeFileName`, `decodeUnicodeEscapes`, `getParentDirectory` by string) show the shape
  without `java.io.File`; note its `sanitizeFileName` has no reserved-name step and no
  `download_file` fallback: copy the **JVM** version for Windows.
- Native plan (`M/core/filesystem/PathResolver.mingw.kt`, ~330 lines):
    - folders: `home = WinEnv.userProfile` (`%USERPROFILE%`, same as Java's `user.home`), app
      home = `WinEnv.appHome` (section 5: `%USERPROFILE%\.fetch_jet`, or the test override);
      the three user folders with the same OneDrive test as the JVM; temp =
      `GetTempPathW()` without the trailing `\` + `\FetchJetDownloads`. VERIFIED on this PC:
      `GetTempPathW` = `C:\Users\miche\AppData\Local\Temp\`, known folders =
      `C:\Users\miche\Downloads`,
      `...\Documents`, `...\Desktop`. Return backslash paths without a trailing separator, as
      `File.absolutePath` does. Create folders with okio `createDirectories`. Cache the strings
      in a private top-level `object` (the class is constructed all the time).
    - `sanitizeFileName`: the JVM code verbatim (pure Kotlin; `Regex` works the same on Native).
      Add a unit test with the JVM outputs for: `a/b:c*?.mp4`, `con.txt`, `name.`, `..`, a
      90-character name with an extension, `x_u0041.mp4`, an Arabic name, an empty string.
    - `resolveExistingFile`: okio `FileSystem.SYSTEM.metadataOrNull`, `list(parent)`,
      `metadata.lastModifiedAtMillis`, same filter.
    - `openDirectoryInExplorer`: same choice of folder, then `WinShell.open(dir)`
      (`ShellExecuteW(null, "open", dir, null, null, SW_SHOWNORMAL)`, success when the result is >
      32).
    - `openFileLocation`: `WinShell.reveal(path)`: `explorer.exe /select,"<path>"` through
      `WinProcess.start` (no ole32 needed); fallback `openDirectoryInExplorer(parent)`.
      (`SHOpenFolderAndSelectItems` is nicer, it reuses an open Explorer window, but needs COM
      and `-lole32`.)
    - `openFile`: `ShellExecuteW(null, null, path, null, parentDir, SW_SHOWNORMAL)`; result
      `SE_ERR_NOASSOC` (31) or `SE_ERR_ASSOCINCOMPLETE` (27) ->
      `ShellExecuteW(null, "openas", path, ...)`
      (the "How do you want to open this file?" dialog); return true when either call returns > 32.
      `ShellExecuteW` already shows the UAC prompt for installers.
    - `chooseDirectory`: `WinShell.pickFolder(...)` or simply `null` like iOS (no caller).
    - `listSubdirectories`, `createSubdirectory`, `getParentDirectory`, `getAvailableDirectories`:
      okio + string work, same rules. `getParentDirectory("C:\\")` must return null.
    - Shell calls should run on a thread that called `CoInitializeEx(COINIT_APARTMENTTHREADED)`
      when ole32 is linked; plain `ShellExecuteW` works without it for files, folders and
      `http(s)` URLs.
    - Risk LOW for the folders and names (pure logic, testable), MEDIUM for `openFile` /
      `openFileLocation` (shell behaviour with odd file types is only testable by hand).

### 3.9 `TextFilePicker` - `C/core/filesystem/TextFilePicker.kt:3`

```kotlin
expect object TextFilePicker {
    val isSupported: Boolean

    fun pick(onPicked: (String?) -> Unit)
}
```

- Caller: `UI/ui/AddDownloadSections.kt:121-131` ("Import .txt"): the button is shown when
  `isSupported`; a non-blank result replaces the URL field text (the link list is then built by
  `OnUrlChanged`). The result is the **content** of the file, not its path.
- JVM (`J/core/filesystem/TextFilePicker.jvm.kt`):
  `isSupported = !GraphicsEnvironment.isHeadless()`;
  `pick` runs on the Swing thread: `java.awt.FileDialog(null, "Import links", LOAD)` (the
  native Windows dialog), filter `*.txt` (`setFilenameFilter` + `file = "*.txt"`), then
  `File(dir, file).readText()` (UTF-8) or null (cancel, any `Throwable`); `onPicked` is called
  on the Swing thread.
- iOS: `UIDocumentPickerViewController` for `UTTypePlainText`, reads the text.
- Native plan (`M/core/filesystem/TextFilePicker.mingw.kt`, ~25 lines): `isSupported = true`;
  `pick` starts a job on `Dispatchers.IO` that calls
  `WinShell.pickFile(title = "Import links", filterName = "Text files", pattern = "*.txt", owner = NativeHost.mainWindow)`
  (blocks while the dialog is open), reads the file with okio `readUtf8()` and calls `onPicked`.
  The C API glue must hop to the Slint thread before touching the UI. The dialog title is not
  localized on the JVM either. `GetOpenFileNameW` needs `-lcomdlg32` or the run-time lookup
  of 1.3. Risk LOW-MEDIUM (a modal dialog opened from a worker thread with
  the Slint window as owner: test that it comes up in front and blocks the main window).

### 3.10 `TorrentFilePicker` - `C/core/filesystem/TorrentFilePicker.kt:8`

```kotlin
/**
 * "Open .torrent file": the system file chooser, answering with a local path the torrent
 * engine can read (on Android and iOS the picked document is copied into the app's storage
 * first, since the original lives behind a content / security-scoped URL).
 */
expect object TorrentFilePicker {
    /** False where no chooser can be shown (e.g. Android before the Activity registered one). */
    val isSupported: Boolean

    /** Shows the chooser; [onPicked] gets the local path, or null when the user cancelled. */
    fun pick(onPicked: (String?) -> Unit)
}
```

- Caller: `UI/ui/AddDownloadSections.kt:141-153`, only when `AppFeatures.torrent`; the path is
  put into the URL field.
- JVM (`J/core/filesystem/TorrentFilePicker.jvm.kt`): same as 3.9 with title
  `Open .torrent file`, filter `*.torrent`, returns `File(dir, file).absolutePath`.
- iOS: document picker, the file is copied into the app's storage.
- Native plan (`M/core/filesystem/TorrentFilePicker.mingw.kt`, ~20 lines): as 3.9 with
  `filterName = "Torrent files"`, `pattern = "*.torrent"`, returning the path. Risk LOW-MEDIUM.

### 3.11 `DiagnosticsLog` - `C/core/log/DiagnosticsLog.kt:8`

```kotlin
/**
 * The app's own log file for bug reports. Desktop already writes ~/.fetch_jet/logs/app.log;
 * Android mirrors stdout/stderr into its private files dir and [export] copies that into the
 * public Downloads folder so the user can send it.
 */
expect object DiagnosticsLog {
    /** True where [export] does something (Android). */
    val canExport: Boolean

    /** Starts mirroring println/stack traces into the log file (call once at start). */
    fun install()

    /** Copies the log to a user-visible place; returns its path, or null when it failed. */
    fun export(): String?
}
```

- Callers: Android only. No desktop or common call site.
- JVM (`J/core/log/DiagnosticsLog.jvm.kt`, 7 lines): `canExport = false`, `install()` empty,
  `export() = null`. The real desktop log is installed by `main.kt:277-304`:
  folder `<home>/.fetch_jet/logs`, file `app.log`, deleted first when longer than
  `2L * 1024 * 1024` bytes, opened for append, UTF-8, auto-flush, wrapped in
  `RedactingOutputStream` (section 4.9); first line
  `---- FetchJet started <LocalDateTime.now()> (java <version>) ----`; uncaught exceptions are
  printed as `Uncaught exception in thread <name>:` + stack trace. Then
  `AppLog.verbose = env FETCHJET_VERBOSE_LOGS == "true" (ignore case) ?: system property fetchjet.verboseLogs ?: false`
  and `println("Verbose logs: <value>")` (`main.kt:698-704`).
- iOS: same three no-ops.
- Native plan: the native app has no Kotlin `main`, so the desktop log moves into this
  actual. `M/core/log/DiagnosticsLog.mingw.kt` (~15 lines): `canExport = false`,
  `export() = null`, `install() = NativeLog.install()`. `M/core/log/NativeLog.kt` (~130 lines):
    1. same file, same 2 MB rule, append;
    2. `_pipe(fds, 65536, O_BINARY)`, `_dup2(fds[1], 1)`, `_dup2(fds[1], 2)` (VERIFIED: `println`
       arrives in the pipe);
    3. one reader thread (`newSingleThreadContext` or a `Worker`): `_read` from `fds[0]`, split
       at `\n`, `LogRedact.redact(line)`, append to the file with okio and flush per line;
    4. header line `---- FetchJet started <yyyy-MM-ddTHH:mm:ss local> (native <APP_VERSION>) ----`;
    5. `setUnhandledExceptionHook { ... }` printing `Uncaught exception:` and the stack trace;
    6. `AppLog.verbose` from `GetEnvironmentVariableW("FETCHJET_VERBOSE_LOGS")` (the build-time
       property becomes a constant the build passes in, or is dropped), then `Verbose logs: ...`.
       The C API init function must call `DiagnosticsLog.install()` before anything else prints.
       Risk MEDIUM: the fd redirect is proven only in a console exe (1.5); a burst larger than the
       pipe buffer while the reader is blocked would stall the writer, so the reader must never
       call `println` itself.

### 3.12 `applyProxyLogin()` - `C/core/network/NetworkSettings.kt:226`

```kotlin
/** Platform part: answers the proxy's 407 / SOCKS login for HTTPS tunnels. */
internal expect fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings)
```

- Caller: only `useNetworkSettings()` (`C/core/network/NetworkSettings.kt:214-223`), which
  every app HTTP client goes through:

  ```kotlin
  fun HttpClientConfig<*>.useNetworkSettings() {
      install(RedirectCookieGuard)
      install(SiteLogins)
      install(PlainHttpProxyLogin)
      (this as HttpClientConfig<HttpClientEngineConfig>).engine {
          proxy = NetworkSettings.ktorProxy()
          applyProxyLogin(this, NetworkSettings.proxy.value)
      }
  }
  ```

  `ktorProxy()` (129-136) gives `ProxyBuilder.socks(host, port)` for SOCKS5,
  `ProxyBuilder.http(Url("http://<host>:<port>"))` for HTTP, null when off.
  `PlainHttpProxyLogin` (201-211, common) already adds `Proxy-Authorization: Basic` to plain
  `http://` requests when an HTTP proxy has a login; `SiteLogins` adds `Authorization: Basic`
  for saved sites. What is left for the platform: the login for HTTPS tunnels (`CONNECT`) and
  for SOCKS5. `ProxySettings` (31-50): `kind` (`NONE`/`HTTP`/`SOCKS5`), `host`, `port`, `user`,
  `password`, `isOn`, `hasLogin`, `url()` (`http://user:pass@host:port` or `socks5h://...`,
  URL-encoded login, IPv6 host in brackets).
- JVM (`J/core/network/NetworkSettings.jvm.kt`, 17 lines): `JvmProxyLogin.install()` always (section
  4.10); when the proxy is on and has a login: OkHttp engine + HTTP proxy ->
  `proxyAuthenticator(okHttpProxyAuthenticator(proxy))` (section 4.11); JDK engine ->
  `authenticator(JvmProxyLogin.authenticator)`. SOCKS5 logins are answered by the JVM default
  `java.net.Authenticator`.
- iOS (`I/core/network/NetworkSettings.ios.kt`, 53 lines): sets `config.proxy = null` and
  builds the Darwin proxy dictionary by hand (HTTP + HTTPS keys, or SOCKS keys with user and
  password), plus `handleChallenge` for the HTTP proxy login. It shows that the actual may
  **replace** the proxy the common code just set.
- Behaviour the app relies on (tested on the JVM and iOS with
  `scripts/network-test/test_proxies.py`,
  see `CLAUDE.md`): HTTP proxy with login for `https://` (CONNECT) and `http://`; SOCKS5 with
  login; a wrong password must make the download fail; proxy off = no request reaches the
  proxy; a change applies to downloads started later (`NetworkClient` rebuilds the client when
  `NetworkSettings.revision` moves).
- Native plan. The WinHTTP engine can do neither part (1.4), so:
  `M/core/network/NetworkSettings.mingw.kt` (~20 lines):

  ```kotlin
  internal actual fun applyProxyLogin(config: HttpClientEngineConfig, proxy: ProxySettings) {
      if (!proxy.isOn) return
      if (proxy.kind == ProxyKind.SOCKS5 || proxy.hasLogin) {
          config.proxy = ProxyBuilder.http(Url("http://127.0.0.1:${LocalProxyShim.portFor(proxy)}"))
      }
  }
  ```

  and `M/core/network/LocalProxyShim.kt` (~300 lines): a tiny forwarding proxy on
  `127.0.0.1:<random port>` made with `ktor-network` raw sockets
  (`aSocket(SelectorManager(Dispatchers.IO)).tcp().bind("127.0.0.1", 0)`),
  started on first use, one per distinct `ProxySettings`, closed when the settings change.
  Per connection it reads the request head and:
    - `CONNECT host:port`, upstream HTTP proxy: connect to the real proxy, send
      `CONNECT host:port HTTP/1.1`, `Host`, `Proxy-Authorization: Basic <user:password>`; relay
      the proxy's status line back (so a 407 for a wrong password reaches WinHTTP and the
      download fails), then copy bytes both ways;
    - `CONNECT host:port`, upstream SOCKS5: greeting `05 02 00 02`; when the server picks `02`:
      `01 <ulen> <user> <plen> <password>` and expect `01 00` (RFC 1929); request
      `05 01 00 03 <len> <host> <port>` (the host name goes to the proxy: DNS through the proxy,
      like `socks5h`); on `05 00` answer `HTTP/1.1 200 Connection established`, else `502`;
      then copy bytes both ways;
    - a plain `GET http://host/path` (absolute form): HTTP upstream -> forward unchanged plus
      the `Proxy-Authorization` header; SOCKS5 upstream -> open the SOCKS tunnel to `host:80`,
      rewrite the request line to origin form, drop `Proxy-Connection`, add `Connection: close`,
      copy both ways.

  A plain HTTP proxy without login needs no shim (WinHTTP talks to it directly).
  Risk HIGH until tested with `scripts/network-test/test_proxies.py`: this is new code on the
  download path, and any local program can use the shim's port while it is open (it is bound
  to loopback only; the JVM app has no such listener for the proxy). Alternatives:
  (a) `ktor-client-curl` for `mingwX64` (libcurl does HTTP / SOCKS5 proxies with logins by
  itself) - not in the Gradle cache, a download the owner must approve (open question 9.1); (b) an
  own WinHTTP engine with `WinHttpSetCredentials` (~600 lines, still no SOCKS5).

### 3.13 `createStreamHttpClient()` - `C/core/network/StreamHttpClient.kt:10`

```kotlin
/**
 * HTTP client for fetching media streams in pieces. Speaks HTTP/1.1 only: some video CDNs
 * (googlevideo) answer 403 to a ranged request over HTTP/2, while the same request over
 * HTTP/1.1 — what browsers' downloaders, curl and yt-dlp use — gets 206.
 */
expect fun createStreamHttpClient(): HttpClient
```

- Callers: `C/feature/media_extractor/MediaStreamDownloader.kt:56` (wrapped in a
  `NetworkClient`, used by `PieceDownloader` for every media piece),
  `C/feature/media_extractor/MediaExtractorToolManager.kt:42-55` (`latestGitHubReleaseTag`,
  created and closed per call).
- JVM (`J/core/network/StreamHttpClient.jvm.kt:15-37`): when the app proxy is SOCKS5 ->
  `HttpClient(OkHttp)` with `protocols(listOf(Protocol.HTTP_1_1))` (the JDK client has no
  SOCKS); else `HttpClient(Java)` with `protocolVersion = HTTP_1_1`. Both get `streamDefaults()`:
  `install(HttpRedirect) { checkHttpMethod = false }`,
  `install(HttpTimeout) { requestTimeoutMillis = null; connectTimeoutMillis = 60_000L; socketTimeoutMillis = 60_000L }`,
  `useNetworkSettings()`.
- iOS (`I/core/network/StreamHttpClient.ios.kt`): `HttpClient(Darwin)` with
  `setHTTPMaximumConnectionsPerHost(32)` and the same three installs.
- Native plan (`M/core/network/StreamHttpClient.mingw.kt`, ~25 lines):

  ```kotlin
  actual fun createStreamHttpClient(): HttpClient = HttpClient(WinHttp) {
      engine { protocolVersion = HttpProtocolVersion.HTTP_1_1 }
      install(HttpRedirect) { checkHttpMethod = false }
      install(HttpTimeout) {
          requestTimeoutMillis = null
          connectTimeoutMillis = 60_000L
          socketTimeoutMillis = 60_000L
      }
      useNetworkSettings()
  }
  ```

  The same file should hold `internal val defaultAppEngine = WinHttp` or a note for part B:
  if the engine-less `HttpClient { }` calls of common code (1.4) do not find WinHTTP inside the
  DLL, the fix is on the common side (an `expect fun createAppHttpClient(block)`), a change
  outside `M/`. Risk MEDIUM: googlevideo with WinHTTP is untested (1.4); WinHTTP with 32
  parallel ranged requests is untested (the prototype ran 4).

### 3.14 `rawProbeForDiagnostics()` - `C/core/network/StreamHttpClient.kt:13`

```kotlin
/** Diagnostics: the same ranged GET through the platform's plainest HTTP API; returns a short report. */
expect fun rawProbeForDiagnostics(url: String, userAgent: String): String
```

- Caller: `C/core/network/PieceDownloader.kt:299-306`, inside `AppLog.d { }` (verbose builds
  only) when a piece request got 403 / 410; the text is appended to the log line as `diag  : ...`.
- JVM (`J/core/network/StreamHttpClient.jvm.kt:39-88`): `Range: bytes=0-0` GET with a JDK client
  without proxy and one with the default proxy selector,
  `curl.exe -s -o NUL -w %{http_code} -r 0-0 <url>`,
  the proxy system properties, Java version / vendor / home, TLS protocols; result
  `raw(noProxy)=<code> raw(default)=<code> curlFromApp=<code> proxySelector=[...] ... tls=...`;
  any failure -> `raw probe failed: <exception>`.
- iOS: returns `""`.
- Native plan (same file as 3.13, ~15 lines): `""` like iOS for the first version; later
  `"curlFromApp=" + WinProcess.run("curl.exe -s -o NUL -w %{http_code} -r 0-0 \"<url>\"", timeoutMs = 20_000).output.trim()`
  (curl.exe ships with Windows 10 and 11). Risk LOW.

### 3.15 `DownloadNotificationManager` - `C/core/notification/DownloadNotificationManager.kt:3`

```kotlin
expect class DownloadNotificationManager() {
    fun showOrUpdateNotification(
        taskId: String,
        fileName: String,
        progressPercent: Int,
        speedText: String,
        downloadedBytes: Long,
        totalBytes: Long
    )

    fun cancelNotification(taskId: String)
}
```

- Callers: `C/feature/queue_manager/QueueManagerRepository.kt:42` (one instance, a public
  constructor parameter), `:395` and `:434` (`cancelNotification`), `:407` and `:423`
  (`showOrUpdateNotification` on progress and on completion). `main.kt:803-823` collects the
  JVM-only `DownloadNotificationManager.activeTasks` for the tray tooltip: idle text
  `Strings.desktopTrayIdleTooltip`; else `Strings.desktopTrayDownloadsHeader`, one line per
  task for the first 6 (`"<name>: <percent>%"`, a name longer than 25 chars becomes its first
  22 + `...`), then `Strings.desktopTrayMore(count - 6)`.
- JVM (`J/core/notification/DownloadNotificationManager.jvm.kt`, 49 lines):
  `data class JvmTaskProgress(taskId, fileName, progressPercent, speedText)`; companion
  `activeTasks: StateFlow<Map<String, JvmTaskProgress>>`. `showOrUpdateNotification`: when
  `progressPercent >= 100` or `speedText` equals `Completed` (ignore case) the task is removed
  from the map; else it is put with
  `fileName.ifBlank { LocalizationManager.Strings.sysDownloadingFileFallback }`
  and `progressPercent.coerceIn(0, 100)`. `cancelNotification` removes it. No system
  notification is shown on desktop.
- iOS (`I/core/notification/DownloadNotificationManager.ios.kt`, 119 lines): local
  notifications through `UNUserNotificationCenter`.
- Native plan (`M/core/notification/DownloadNotificationManager.mingw.kt`, ~45 lines): the JVM
  file with the data class renamed `DesktopTaskProgress` (or kept), same rules, same companion
  flow. The C API exposes the map (or the ready tooltip text) to whoever owns the tray icon.
  Risk LOW.

### 3.16 `SystemNotice` - `C/core/notification/SystemNotice.kt:4`

```kotlin
/** A one-off system notification (not a download's progress), e.g. a download sent / received. */
expect object SystemNotice {
    fun show(id: String, title: String, text: String)
}
```

- Callers: `C/presentation/dashboard/DashboardDeviceSync.kt:115` (`"device-recv-<id>"`, the
  message, the file name) and `:302` (`"device-sent-<time>"`).
- JVM (`J/core/notification/SystemNotice.jvm.kt`, 15 lines):
  `notices: SharedFlow<Pair<String, String>>`
  (`MutableSharedFlow(extraBufferCapacity = 8)`), `show` does `tryEmit(title to text)` (the id
  is dropped; a ninth waiting notice is lost). `main.kt:762-768` collects it and calls
  `trayState.sendNotification(Notification(title, text, Notification.Type.Info))`: a Windows
  tray balloon / toast from the tray icon.
- iOS: `UNUserNotificationCenter` request with the id.
- Native plan (`M/core/notification/SystemNotice.mingw.kt`, ~15 lines): identical to the JVM
  file. The tray owner shows it with `Shell_NotifyIconW(NIM_MODIFY, ...)`, `uFlags = NIF_INFO`,
  `szInfoTitle = title`, `szInfo = text`, `dwInfoFlags = NIIF_INFO`. Risk LOW (the tray itself
  is not in this part).

### 3.17 `localMinutesOfDay()` - `C/core/power/SpeedSchedule.kt:19`

```kotlin
/** Minutes since local midnight (0..1439), for the schedule ticker. */
expect fun localMinutesOfDay(): Int
```

- Callers: `C/core/power/SpeedSchedule.kt:94` (default argument of `desiredLimit`) and `:119`
  (`reevaluate`, every 30 s).
- JVM / Android (`JA/core/power/SpeedSchedule.jvmAndroid.kt`): `Calendar.getInstance()`,
  `HOUR_OF_DAY * 60 + MINUTE`. iOS: `NSCalendar` hour and minute.
- Native plan (`M/core/power/SpeedSchedule.mingw.kt`, ~10 lines): `GetLocalTime(SYSTEMTIME)`,
  `wHour * 60 + wMinute`. VERIFIED. Risk LOW.

### 3.18 `SystemPower` - `C/core/power/WhenDone.kt:17`

```kotlin
enum class WhenDoneAction { NOTHING, EXIT, SLEEP, SHUT_DOWN }

/** Sleep / shut down the computer (desktop only). */
expect object SystemPower {
    val isSupported: Boolean
    fun sleep(): Result<Unit>
    fun shutDown(): Result<Unit>
}
```

- Callers: `WhenDone.runNow()` (`C/core/power/WhenDone.kt:86-97`) after the 60 s countdown;
  a failure's `message ?: toString()` is shown to the user (`WhenDone.error`).
  `UI/ui/WhenDoneUi.kt:44` hides the card when `!isSupported`. `WhenDone.onExitApp` (set by
  `main.kt:799-800`) quits the app for `EXIT`: the native host must set it too.
- JVM (`J/core/power/SystemPower.jvm.kt`, 41 lines): `isSupported = true`. Windows `sleep()`:
  `powershell -NoProfile -NonInteractive -Command "Add-Type -AssemblyName System.Windows.Forms; [void][System.Windows.Forms.Application]::SetSuspendState('Suspend', $false, $false)"`.
  Windows `shutDown()`: `shutdown /s /t 0`. `run`: `println("WhenDone: <command>")`, start
  with stderr merged, `waitFor(10 s)`; when it ended in time with a non-zero code ->
  `error(output.trim().ifBlank { "exit code <n>" })`; a command still running after 10 s counts
  as success.
- iOS: `isSupported = false`, both fail with
  `UnsupportedOperationException("not available on this platform")`.
- Native plan (`M/core/power/SystemPower.mingw.kt`, ~70 lines):
    - `sleep()`: `SetSuspendState(FALSE, FALSE, FALSE)` from `powrprof.dll` (what the .NET call
      above does), reached with `LoadLibraryW("powrprof.dll")` + `GetProcAddress` because the
      binding is missing (1.3); signature
      `BOOLEAN SetSuspendState(BOOLEAN bHibernate, BOOLEAN bForce, BOOLEAN bWakeupEventsDisabled)`.
      The call returns only after the computer wakes up: run it on its own thread, wait at most
      1 s for an immediate `FALSE` (-> `failure(Exception("sleep failed (<GetLastError>)"))`),
      otherwise return success. Enable `SeShutdownPrivilege` first (`OpenProcessToken`,
      `LookupPrivilegeValueW("SeShutdownPrivilege")`, `AdjustTokenPrivileges`). VERIFIED: the
      function is found by `GetProcAddress`. Fact from the probe: `IsPwrSuspendAllowed()`
      answers 0 on this PC (no classic S3 sleep, a modern-standby machine), so what "Sleep" does
      here must be seen in the owner's test; the JVM app calls the same API, so the behaviour
      is the same either way.
    - `shutDown()`: `WinProcess.run("shutdown.exe /s /t 0", timeoutMs = 10_000)`, same error
      rule as the JVM (exact parity, no privilege code). Print the same `WhenDone: ...` line.
    - Risk MEDIUM: neither action can be tested without sleeping / shutting down the owner's PC (the
      JVM versions were owner-tested; plan the same owner test).

### 3.19 `CryptoBox` - `C/core/security/CryptoBox.kt:10`

```kotlin
/**
 * Authenticated encryption with a *shared* key (AES-256-GCM), for data sent between two paired
 * devices (ROADMAP-1.3.0 item 4). Unlike [SecretBox] (a device-only key for data at rest), the
 * key here is the pairing secret both devices hold, so one device can open what the other sealed.
 *
 * Output layout: 12-byte random nonce ++ ciphertext ++ 16-byte GCM tag. The key must be 32 bytes.
 */
internal expect object CryptoBox {
    fun seal(key: ByteArray, plain: ByteArray): ByteArray

    /** Throws if the tag does not verify (wrong key, or the message was tampered with). */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray
}
```

- Callers: device sync only
  (`JA/feature/device_sync/DeviceSyncImpl.jvmAndroid.kt:463,486,601,621,751,782,789`):
  the metadata frame, every file chunk (`T_FILE_CHUNK`), the `"unpair"` proof. `open` failing
  is caught with `runCatching` and means "wrong key / not paired". The wire format must match
  Android, the JVM desktop and iOS (CryptoKit `AES.GCM` in `NativeDeviceSyncBridge.swift`).
- JVM / Android (`JA/core/security/CryptoBox.jvmAndroid.kt`, 36 lines): `NONCE_LEN = 12`,
  `TAG_BITS = 128`, `SecureRandom` nonce, `Cipher.getInstance("AES/GCM/NoPadding")`,
  `seal = nonce + cipher.doFinal(plain)`; `open` requires `sealed.size > 12`
  (`"sealed message too short"`), else `AEADBadTagException` from `doFinal`. No additional data.
- iOS (`I/core/security/CryptoBox.ios.kt`): throws `NotImplementedError` (the iOS device sync
  encrypts inside the Swift bridge instead).
- Native plan (`M/core/security/CryptoBox.mingw.kt`, ~25 lines on top of `WinCrypto`):
  `seal = nonce(12, BCryptGenRandom) + WinCrypto.aesGcmSeal(key, nonce, plain)`,
  `open`: `require(sealed.size >= 28) { "sealed message too short" }`, split, `aesGcmOpen`
  (throws on `STATUS_AUTH_TAG_MISMATCH` 0xC000A002). VERIFIED against the NIST vector, with an
  empty plain text, and with a flipped tag bit. Keep one AES-GCM algorithm handle for the
  process (opening the provider per chunk would slow file transfers). Risk LOW.

### 3.20 `SecretBox` - `C/core/security/Secrets.kt:49`

```kotlin
/** Platform part: encryption with a key kept by the OS. */
internal expect object SecretBox {
    fun seal(plain: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray

    /** Bytes from the OS's secure random source. */
    fun randomBytes(n: Int): ByteArray
}
```

- Callers: only `Secrets` (same file, 13-46; format in 2.1). `Secrets` is used by
  `BridgePairing.kt:89-90,261` (the bridge token, `Secrets.randomHex()` = 32 random bytes as
  hex), `NetworkSettings.kt:154-175`, `PairedDeviceStore.kt:73,92,106`, `TaskCache.kt:134-201`
  (task URLs and the Cookie header).
- JVM (`J/core/security/SecretBox.jvm.kt`, 46 lines): `vault` = `WindowsKeyVault` on Windows
  (section 4.5). `key` is `by lazy`: `vault.load()`; when null: 32 bytes from `SecureRandom`,
  `vault.store(it)`, then `check(vault.load()?.contentEquals(it) == true) { "key was not saved" }`.
  When `load()` throws (the file exists but DPAPI cannot open it: copied from another user or
  PC) the exception leaves the `lazy` unset, every `seal` / `open` throws again, and no new key
  is ever made over the old file. `seal`: 12-byte IV from `SecureRandom`, `AES/GCM/NoPadding`,
  128-bit tag, result `iv + ciphertext+tag`. `open`: IV = first 12 bytes. `randomBytes(n)`:
  `SecureRandom`.
- iOS (`I/core/security/SecretBox.ios.kt` + `KeyFile.ios.kt`): Keychain key, CBC + HMAC: a
  different format, no use for Windows.
- On-disk: `secrets.key` (section 2) must be read as the JVM wrote it, and written the same way.
- Native plan (`M/core/security/SecretBox.mingw.kt` ~45 lines + `M/core/security/WindowsKeyVault.kt`
  ~40 lines):
    - `WindowsKeyVault.load()`: file missing -> null; else
      `WinCrypto.dpapiUnprotect(Base64.decode(text.trim()))` (throws
      `IllegalStateException("DPAPI Unprotect failed (<GetLastError>)")`).
    - `WindowsKeyVault.store(key)`: `Base64.encode(WinCrypto.dpapiProtect(key))` written to
      `secrets.key.tmp`, then moved over `secrets.key` (`MoveFileExW` with
      `MOVEFILE_REPLACE_EXISTING`, or okio `atomicMove`).
    - `SecretBox`: the JVM logic one to one with `WinCrypto.aesGcmSeal / aesGcmOpen /
    randomBytes`; keep the "throwing load does not create a key" rule.
    - VERIFIED both directions (2.1). In-process DPAPI also removes the PowerShell start the JVM
      pays on first use. Risk LOW.

### 3.21 `StartupLauncher` - `C/core/startup/StartupLauncher.kt:14`

```kotlin
/**
 * "Start FetchJet when I log in" (desktop only, via github.com/vinceglb/AutoLaunch):
 * Windows registry Run key, macOS LaunchAgent, Linux autostart entry. The OS starts the app
 * with `--autostart=true`.
 */
expect object StartupLauncher {
    /** false on Android/iOS, and on desktop when run from Gradle/an IDE instead of the installed app. */
    val isSupported: Boolean

    /**
     * Microsoft Store build: Windows owns the switch (Settings > Apps > Startup); the app only
     * opens that page. null elsewhere.
     */
    val managedBySystemUri: String?
    suspend fun isEnabled(): Boolean
    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}

/** Argument the OS passes when it starts FetchJet at login. */
const val AUTOSTART_ARG = "--autostart=true"
```

Also in that file (common, nothing to port): `object StartupPreferences` (`launchedAtLogin`,
`startHidden` in `startup.properties`, default true).

- Callers: `UI/ui/SettingsPages.kt:140`, `UI/ui/StartupUi.kt:25-95`: when `managedBySystemUri`
  is not null the card shows a button that opens that URI plus the "start hidden" switch; else,
  when `isSupported`, a switch bound to `isEnabled()` / `setEnabled(want)` (re-read after the
  change) plus "start hidden". `main.kt:725-733`: started at login = an argument equal to
  `--autostart=true`, or (Store build, no arguments) `WindowsStorePackage.looksLikeLoginStart()`;
  the argument is removed before the others are read as a link; `main.kt:749-755`: start in the
  tray when started at login and `startHidden` and a tray exists.
- JVM (`J/core/startup/StartupLauncher.jvm.kt`, 31 lines) on the library
  `io.github.vinceglb:auto-launch:0.9.0` (read from its sources jar):
    - `AutoLaunch(appPackageName = "com.michelle.raouf.fetch_jet")`; `appPath` = the process
      executable (`ProcessHandle.current().info().command()`), i.e. `...\FetchJet\FetchJet.exe`.
    - `isSupported = !WindowsStorePackage.isInstalled && AutoLaunch.isRunningFromDistributable`
      (executable name without extension is not `java`); false on any `Throwable`.
    - `managedBySystemUri = "ms-settings:startupapps"` for the Store build, else null.
    - `isEnabled()` = `isSupported &&` the string value `com.michelle.raouf.fetch_jet` under
      `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` **equals exactly**
      `cmd /c start "com.michelle.raouf.fetch_jet" /D "<folder of the exe>" "<exe path>" --autostart=true`
      (missing value -> false; other registry errors -> false through `runCatching`).
    - `setEnabled(true)`: the exe must exist (else `NoSuchFileException`), the key is created
      when missing, the value is set to that string. `setEnabled(false)`: the value is deleted
      when the key exists. Wrapped in `runCatching`.
    - The value name is the registry id and "never change it, or an old entry would be left behind"
      (comment at line 7).
- iOS: unsupported (false / null / failure `"not available on this platform"`).
- Registry compatibility: a user who turned the option on in the JVM app has the `cmd /c start ...`
  value pointing at the old launcher path. If the native installer keeps `FetchJet.exe` at the
  same path, that value keeps working (it starts the native exe with `--autostart=true`).
- Native plan (`M/core/startup/StartupLauncher.mingw.kt`, ~60 lines):
    - `isSupported = !WindowsStorePackage.isInstalled && NativeInstall.launcherPath != null`;
    - `managedBySystemUri` as on the JVM;
    - `isEnabled()`: read the value with `WinRegistry.getString`; enabled when it contains the
      current exe path (ignore case) and `--autostart=true` (this accepts the JVM format and the
      new one);
    - `setEnabled(true)`: write `"<exe path>" --autostart=true` (no `cmd /c start`, which opens
      a console window for a moment at login), same value name; `setEnabled(false)`: delete the
      value;
    - on `Dispatchers.IO`.
      The host must do what `main.kt:725-755` does with the argument. `looksLikeLoginStart()` moves
      to `M/core/platform/WindowsStorePackage.kt` as `GetTickCount64() < 120_000` (section 4.2).
      Risk LOW (registry round trip VERIFIED under a probe key), plus the installed-or-development
      rule of 1.6.

### 3.22 `PlatformUpdater` - `C/core/update/PlatformUpdater.kt:4`

```kotlin
/** What this platform can do with a new release. */
expect object PlatformUpdater {
    /**
     * true = FetchJet can download the new release and install it itself. Desktop: one file per
     * OS in jvmMain (`WindowsUpdater`, `MacUpdater`, `LinuxUpdater`). Android opens the system
     * installer. Elsewhere the user is only told about the update.
     */
    val canInstallInApp: Boolean

    /**
     * Name of this platform in release notes: windows / macos / linux / android / ios.
     * A release built for some platforms only lists the others in a
     * `<!-- fetchjet-unchanged: windows,linux -->` line, and the app then knows the new
     * version has nothing new for it.
     */
    val platformKey: String

    /** true = a store (Microsoft Store) installs the updates: FetchJet does not check GitHub. */
    val updatesFromStore: Boolean

    fun latestStoreVersion(): String?

    /** The release file for this platform, or null to send the user to the release page. */
    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset?

    /** Starts the downloaded installer, quits FetchJet; the installer starts it again. */
    fun launchInstallerAndExit(installerPath: String): Result<Unit>
}
```

- Callers: `C/core/update/UpdateManager.kt`:
    - `:150` `canInstallInApp` into the state; `:391` no download without it;
    - `:232-246` `platformKey` against `AppRelease.unchangedPlatforms`;
    - `:308-319` Store build: `latestStoreVersion() ?: APP_VERSION` becomes a release whose page
      is `ms-windows-store://pdp/?productid=9NP3HJRHMPJS`; an exception from `latestStoreVersion()`
      reaches `runCheck` and is shown as `Strings.sysCouldNotCheckForUpdates(message)`, so the
      exception **message is user-visible**;
    - `:341-342`, `:393` `pickAsset` (null -> `Strings.sysUpdateNoInstallerForSystem`);
    - `:411-458` the download: `<temp>/fetchjet-update/<asset name>` (`.part` while downloading),
      SHA-256 against the release digest, then `<asset>.sig` checked by `verifyUpdateSignature`
      (3.23); error texts `"checksum does not match the release"`,
      `"the release file is not signed"`,
      `"the release file signature is not valid"`, `"download interrupted"`;
    - `:200-216` `installNow()` -> `launchInstallerAndExit(path)`; a failure's message goes into
      `Strings.sysCouldNotStartInstaller(message)`.
      `UI/ui/UpdateUi.kt:60-280` reads `updatesFromStore`, `pickAsset`, `platformKey == "android"`.
- JVM (`J/core/update/PlatformUpdater.jvm.kt`, 29 lines) picks `WindowsUpdater` on Windows (section
  4.4): `canInstallInApp = DesktopInstall.launcherPath != null && !WindowsStorePackage.isInstalled`,
  `platformKey = "windows"`, `updatesFromStore = WindowsStorePackage.isInstalled`,
  `latestStoreVersion() = WindowsUpdater.latestStoreVersion()`,
  `pickAsset` = first `.msi`, else first `.exe` (ignore case),
  `launchInstallerAndExit` =
  `runCatching { check(installer.exists()) { "installer not found" }; updater.installAndExit(installer) }`.
- iOS: `canInstallInApp = false`, `platformKey = "ios"`, `.ipa`, failure `"not available on iOS"`.
- Native plan (`M/core/update/PlatformUpdater.mingw.kt` ~35 lines +
  `M/core/update/WindowsUpdater.kt`
  ~110 lines, section 4.4). `platformKey` stays `"windows"`. Risk MEDIUM: the installer script
  and the JVM-to-native upgrade path depend on packaging decisions that are not made yet (4.4, open
  question 9.5), and `exitProcess` from inside the DLL ends the Slint host without
  its own shutdown (tray icon left until the mouse moves over it): quit through the host.

### 3.23 `verifyUpdateSignature()` - `C/core/update/UpdateSignature.kt:15`

```kotlin
/**
 * Public keys of the update signing key (ECDSA P-256, X.509 SubjectPublicKeyInfo, base64).
 * CI signs every release file with the private key (`scripts/sign-release-file.sh`, secret
 * `UPDATE_SIGNING_KEY`) and uploads `<file>.sig`; the app installs only a file whose signature
 * matches one of these keys. A new key = add it here, ship that version signed with the old
 * key, then sign with the new one. Never remove the key the installed versions trust.
 */
internal val UPDATE_PUBLIC_KEYS = listOf(
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE55ElrDkiL7DyOHMrcoJj9A7RWTSiJx2/64nxGKTr+1TBHM48ZgCOdDttzZd3C39qRsAXg6WpAwMe1K4+ki53Lg=="
)

/** True when [signatureBase64] (the `.sig` file) signs the file at [path] with a key above. */
internal expect fun verifyUpdateSignature(path: String, signatureBase64: String): Boolean
```

- Caller: `C/core/update/UpdateManager.kt:449-456` with the path of the `.part` file and the
  body of `<asset>.sig`. False -> `"the release file signature is not valid"`, nothing is installed.
- The signature (`scripts/sign-release-file.sh`):
  `openssl dgst -sha256 -sign key file | openssl base64 -A`,
  i.e. Base64 (one line, padded) of the **ASN.1 DER** ECDSA signature
  (`SEQUENCE { INTEGER r, INTEGER s }`,
  70-72 bytes) over the SHA-256 of the whole file.
- JVM / Android (`JA/core/update/UpdateSignature.jvmAndroid.kt`, 34 lines): Base64-decode the
  trimmed signature (bad Base64 -> false); for each key: `KeyFactory.getInstance("EC")` with
  `X509EncodedKeySpec`, `Signature.getInstance("SHA256withECDSA")`, the file streamed in 64 KB
  blocks, `verify`; any exception counts as false for that key; true when any key verifies.
- iOS: always false (iOS never installs itself).
- Native plan (`M/core/update/UpdateSignature.mingw.kt`, ~45 lines on top of `WinCrypto`):
    1. signature = Base64 decode of `signatureBase64.trim()` (false on failure);
    2. hash = SHA-256 of the file, streamed (okio
       `HashingSource.sha256(FileSystem.SYSTEM.source(path))`
       read to the end; or BCrypt hashing);
    3. for each key: `WinCrypto.ecdsaP256Verify(Base64.decode(key), hash, signature)`.
       `ecdsaP256Verify`: the key is a 91-byte P-256 SubjectPublicKeyInfo whose last 65 bytes are
       `04 || X || Y`; build a `BCRYPT_ECCKEY_BLOB` (`45 43 53 31` =
       `BCRYPT_ECDSA_PUBLIC_P256_MAGIC`,
       `20 00 00 00` = 32, then X, then Y),
       `BCryptImportKeyPair(alg("ECDSA_P256"), null, "ECCPUBLICBLOB", ...)`;
       convert the DER signature to raw `r || s` (each left-padded / stripped to 32 bytes: BCrypt
       wants IEEE P1363 form, not DER);
       `BCryptVerifySignature(key, null, hash, 32, raw, 64, 0) == 0`.
       VERIFIED with a key and signature made by `openssl` exactly as CI does (true), and with a
       changed hash (false). The real key above has the expected 26-byte header (checked with
       `openssl base64 -d | xxd`). Risk LOW; add a unit test with a fixed key / file / signature.

### 3.24 `localUtcOffsetMs()` - `C/core/util/LocalClock.kt:4`

```kotlin
/** Offset of the device's local time from UTC right now, in milliseconds (+3 h in Cairo). */
expect fun localUtcOffsetMs(): Long
```

- Callers: `C/core/util/LocalClock.kt:19,29` (`localTimeOnDayEpochMs`, `formatLocalSchedule`),
  `C/presentation/dashboard/DashboardTaskEvents.kt:245`, `UI/TaskDetails.kt:912`,
  `UI/ui/PlaylistScheduleUi.kt:240` (scheduled downloads).
- JVM / Android (`JA/core/util/LocalClock.jvmAndroid.kt`):
  `TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()` (daylight saving included).
- iOS: `NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate()) * 1000`.
- Native plan (`M/core/util/LocalClock.mingw.kt`, ~15 lines): `GetTimeZoneInformation(tz)`;
  `bias = tz.Bias + (DaylightBias when the result is TIME_ZONE_ID_DAYLIGHT (2), StandardBias when TIME_ZONE_ID_STANDARD (1))`;
  result `-bias * 60_000L`. VERIFIED: 7 200 000 on this PC today (Cairo, winter time, UTC+2).
  Risk LOW.

---

## 4. JVM-only files under `core/` that are not actuals (they must be ported or replaced too)

### 4.1 `J/core/browser/DesktopBrowsers.kt` (229 lines, `internal object DesktopBrowsers`)

Used by `BrowserIntegration.jvm.kt` only. Behaviour on Windows:

- Folders: `home = System.getProperty("user.home")`, `localAppData = env LOCALAPPDATA`,
  `programFiles = env ProgramFiles ?: "C:\Program Files"`,
  `programFilesX86 = env ProgramFiles(x86) ?: "C:\Program Files (x86)"`.
  `win(relative...)`: for each relative path, in this order: `ProgramFiles`, `ProgramFiles(x86)`,
  `LOCALAPPDATA` (blank ones skipped) + `\` + relative. `executable(spec)` = the first of those
  that is a regular file.
- The browser list (`specs`, lines 55-98), in this order:

  | id | name | firefox | extensions page | download URL | Windows relative path(s) |
    |----|------|---------|-----------------|--------------|--------------------------|
  | `chrome` | Google Chrome | no | `chrome://extensions` | `https://www.google.com/chrome/` | `Google\Chrome\Application\chrome.exe` |
  | `edge` | Microsoft Edge | no | `edge://extensions` | `https://www.microsoft.com/edge/download` | `Microsoft\Edge\Application\msedge.exe` |
  | `firefox` | Firefox | yes | `FIREFOX_ADDON_URL` | `https://www.mozilla.org/firefox/new/` | `Mozilla Firefox\firefox.exe` |
  | `brave` | Brave | no | `brave://extensions` | `https://brave.com/download/` | `BraveSoftware\Brave-Browser\Application\brave.exe` |
  | `opera` | Opera | no | `opera://extensions` | `https://www.opera.com/download` | `Programs\Opera\opera.exe`, `Opera\opera.exe` |
  | `vivaldi` | Vivaldi | no | `vivaldi://extensions` | `https://vivaldi.com/download/` | `Vivaldi\Application\vivaldi.exe` |

- `bundled` (lazy): the class loader has the resource `fetchjet-extension/chromium/manifest.json`
  (put into the jar by `desktopApp/build.gradle.kts:58-71`, task `bundleExtension`, which copies
  from `extension/`: `background.js`, `yt-core.js`, `content.js`, `popup.html`, `popup.js`,
  `options.html`, `options.js`, `icon16.png`, `icon48.png`, `icon128.png`, `manifest.json`,
  `platform.js`, `i18n/**`, `_locales/**`).
- `browsers()`:
  `BrowserInfo(id, name, installed = executable != null, downloadUrl, hasLocalCopy = !firefox && bundled)`,
  then `sortedByDescending { it.installed }` (stable: installed first, list order kept).
- `addExtension(id)`: unknown id -> `Failed("unknown browser")`; not installed -> `NotInstalled`;
  Firefox -> start `"<exe>" "<FIREFOX_ADDON_URL>"` -> `Opened`, or
  `Failed("could not start Firefox")`;
  Chromium -> `unpackChromiumExtension()` (null ->
  `Failed("the extension is not bundled in this build")`),
  start `"<exe>" "<extensions page>"` (result ignored), return
  `LoadUnpacked(folder.absolutePath, page)`.
- `openBrowser(id)`: start the exe with no argument; errors ignored.
- `installXpi(id, bytes, fileName)`: id must be the Firefox spec (else `Failed("not Firefox")`);
  not installed -> `NotInstalled`; `safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")`;
  file `<home>/.fetch_jet/extension/firefox/<safeName>`; write the bytes; start
  `"<exe>" "<file>"` -> `Opened` / `Failed("could not start Firefox")`; exception ->
  `Failed(message ?: "could not save the extension")`.
- `openFolder(folder)`: `explorer.exe <folder>`; errors ignored.
- `chromiumFolder = <home>/.fetch_jet/extension/chromium`.
- `refreshUnpackedExtension()`: when `chromiumFolder/manifest.json` exists, unpack again (so
  the unpacked extension follows the app version at every start).
- `unpackChromiumExtension()` (`@Synchronized`): copies every bundled file over the target (never
  deletes files that are no longer bundled); returns the folder, or null when nothing
  is bundled or on any exception.
- APIs: `java.io.File`, `ProcessBuilder`, `JarURLConnection`, `JarFile`, the class loader.

Native port (`M/core/browser/DesktopBrowsers.kt`, ~150 lines): the same table (Windows
columns only), `WinEnv` for the folders, `WinProcess.start` for launching, okio for the copy.
The bundled extension becomes a folder shipped with the app: `<exeDir>\extension\chromium\`
with the same file list (a packaging / build step outside this part must copy it, and
`EXTENSION_VERSION` must still equal `extension/manifest.json`). `bundled` =
that folder has `manifest.json`. Unpack = recursive copy, overwriting. Guard the copy with a
`Mutex` / lock like `@Synchronized`. Risk LOW.

### 4.2 `J/core/platform/WindowsStorePackage.kt` (41 lines)

- `isInstalled` (lazy):
  `(System.getProperty("jpackage.app-path") ?: System.getProperty("compose.application.resources.dir") ?: System.getProperty("user.dir")).contains("\\WindowsApps\\", ignoreCase = true)`.
- `looksLikeLoginStart()`: false when not the Store build; else runs
  `powershell -NoProfile -NonInteractive -Command "[Environment]::TickCount64"` (5 s limit) and
  returns `uptimeMs < 2 * 60 * 1000`.
- Users: `BrowserIntegration.jvm.kt:11,65`, `StartupLauncher.jvm.kt:17,23`,
  `PlatformUpdater.jvm.kt:18`, `WindowsUpdater.kt:18`, `main.kt:730`.

Native port (`M/core/platform/WindowsStorePackage.kt`, ~25 lines): `isInstalled` =
`NativeInstall.exePath.contains("\\WindowsApps\\", ignoreCase = true)` (optionally confirmed
with `GetCurrentPackageFullName` through `GetProcAddress(kernel32)`: VERIFIED that it returns
15700, `APPMODEL_ERROR_NO_PACKAGE`, outside a package); `looksLikeLoginStart()` =
`isInstalled && GetTickCount64() < 120_000u` (VERIFIED that `GetTickCount64` is callable).
Risk LOW. Facts to keep in mind for the Store build: registry writes are private to the
package, `%USERPROFILE%\.fetch_jet` is the same folder as for the MSI build, and child
processes keep the package identity (needed by 4.4's Store check).

### 4.3 `J/core/update/DesktopUpdater.kt` (67 lines)

- `enum DesktopOs { WINDOWS, MAC, LINUX }` with `current` from `os.name`.
-
`interface DesktopUpdater { val platformKey; val canInstallInApp; fun pickAsset(assets); fun installAndExit(installer: File) }`.
- `object DesktopInstall`:
    - `launcherPath: String?` = `System.getProperty("jpackage.app-path")` when that file exists
      (null from Gradle / an IDE);
    - `firstAsset(assets, vararg extensions)` = first asset whose name ends with one of them (ignore
      case);
    - `hasCommand(name)` (PATH search, Linux), `shellQuote(value)` (bash),
      `runShellAfterExit(installer, body)`
      (writes `fetchjet-update.sh`, macOS / Linux only).

Native port: `DesktopOs` and the interface are not needed (one OS). Keep `firstAsset` (pure
Kotlin) and replace `launcherPath` by `NativeInstall.launcherPath` (section 5). ~15 lines
inside `M/core/update/WindowsUpdater.kt`.

### 4.4 `J/core/update/WindowsUpdater.kt` (100 lines,
`internal object WindowsUpdater : DesktopUpdater`)

- `platformKey = "windows"`;
  `canInstallInApp = DesktopInstall.launcherPath != null && !WindowsStorePackage.isInstalled`;
  `pickAsset` = `firstAsset(assets, ".msi") ?: firstAsset(assets, ".exe")`.
- `installAndExit(installer)` (23-45):
    - `install` = `msiexec /i "<installer>" /passive /norestart` for an `.msi`, else
      `"<installer>"`;
    - `relaunch` = `start "" "<launcherPath>"` (empty when there is no launcher path);
    - `pid` = this process id;
    - writes `fetchjet-update.cmd` next to the installer (UTF-8, no BOM, CRLF):

      ```
      @echo off
      :wait
      tasklist /FI "PID eq <pid>" 2>nul | find "<pid>" >nul && (timeout /t 1 /nobreak >nul & goto wait)
      <install>
      <relaunch>
      del "%~f0"
      ```

    - starts `cmd.exe /c start "FetchJet update" /min <script path>` with the installer's folder
      as working directory, then `exitProcess(0)`.
    - The MSI is a per-user install with a fixed upgrade id (`desktopApp/build.gradle.kts:135-138`:
      `perUserInstall = true`,
      `upgradeUuid = "7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30"`, `dirChooser = true`,
      `packageName = "FetchJet"`), so `msiexec /i` replaces the old version without an admin prompt.
- `latestStoreVersion()` (70-99): `askStore()`, on an exception `Thread.sleep(3000)` and one
  more try (the second exception is thrown to the caller). `askStore()` runs
  `powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand <base64 of the script in UTF-16LE>`
  with stderr discarded, reads all of stdout, waits 90 s (`error("Microsoft Store did not answer")`
  on timeout). Lines are trimmed; a line starting with `FJERR` ->
  `error("Microsoft Store: <rest, or "unknown error">")`; the first line starting with `FJSTORE`
  is `FJSTORE v1,v2,...`; none -> `error("Microsoft Store did not answer")`; the result is the
  newest version by `isNewer` (`C/core/update/UpdateManager.kt:592`), or null when the list is
  empty (= no update waiting). The script (the `$` are escaped as `${'$'}` in the Kotlin source):

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

Native port (`M/core/update/WindowsUpdater.kt`, ~110 lines):

- `installAndExit`: same script text with `GetCurrentProcessId()`; write it with okio; start it
  with
  `WinProcess.start("cmd.exe /c start \"FetchJet update\" /min \"<script path>\"", workDir = installerDir)`
  (the JVM gets the quotes around a path with spaces from `ProcessBuilder`; here they must be
  written); then quit through `NativeHost.requestExit` with `exitProcess(0)` as the fallback
  after a short wait. A batch file is read in the console code page: a user name with
  non-ASCII letters in the temp path breaks the JVM script too; adding `chcp 65001 >nul` as the
  second line fixes it (a small improvement, not parity).
- `latestStoreVersion`: keep the PowerShell script exactly (a WinRT call from Kotlin/Native
  would need COM activation and async plumbing by hand), run it with
  `WinProcess.run(..., timeoutMs = 90_000, mergeStderr = false)`; same texts, same single retry
  after 3 s (`delay` is not available in a non-suspend function: use
  `platform.windows.Sleep(3000u)`).
- Upgrade path facts for the planner: the JVM app's updater will install whatever single
  `.msi` (or `.exe`) the release carries and then run `start "" "<old launcher path>"`. For a
  user to arrive on the native app through the in-app update, the native installer must be an
  MSI with the same upgrade id and per-user scope, and must put `FetchJet.exe` at the same
  place (`<install dir>\FetchJet.exe`); the release must carry `<file>.sig`
  (`scripts/sign-release-file.sh`) and exactly one Windows `.msi`.

### 4.5 `J/core/security/KeyVault.kt` (51 lines) and `WindowsKeyVault.kt` (31 lines)

- `interface KeyVault { fun load(): ByteArray?; fun store(key: ByteArray) }` ("The key, or
  null when none was saved yet. Throws when the store cannot be read now.").
- `KeyVaultTools`: `SERVICE = "FetchJet"`, `ACCOUNT = "secrets-key"` (macOS / Linux keyrings),
  `settingsDir = <user.home>/.fetch_jet`, `hex` / `unhex`, `run(vararg command, input)` (starts
  the command with stderr discarded, writes `input` to stdin, waits 15 s, kills it and throws
  `"<command> timed out"`, then reads stdout), `writePrivate(file, text)` (writes
  `<name>.tmp` with owner-only permissions, renames it over the file; when the rename fails:
  delete the target and rename again).
- `WindowsKeyVault`: `file = settingsDir/secrets.key`. `load()`: null when the file is missing;
  else `Base64.decode(dpapi("Unprotect", text.trim()))`. `store(key)`:
  `writePrivate(file, dpapi("Protect", Base64(key)))`. `dpapi(method, base64)` runs
  `powershell.exe -NoProfile -NonInteractive -Command <script>` with the Base64 text on stdin:

  ```powershell
  Add-Type -AssemblyName System.Security; $b = [Convert]::FromBase64String([Console]::In.ReadLine()); [Convert]::ToBase64String([Security.Cryptography.ProtectedData]::<method>($b, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser))
  ```

  `check(exit == 0 && out.isNotEmpty()) { "DPAPI <method> failed (<exit>)" }`; result = last
  output line, trimmed. So the file content is Base64 (DPAPI blob), and the blob opens to the 32
  raw key bytes.

Native port: `M/core/security/WindowsKeyVault.kt` (~40 lines, see 3.20) with
`WinCrypto.dpapiProtect / dpapiUnprotect`; `KeyVaultTools.run` is replaced by `WinProcess.run`
(section 5) and is not needed for the vault any more.

### 4.6 macOS / Linux files (not needed for the Windows port; listed so nothing is forgotten)

| File                                    | What it does                                                                                                                                                                                         | For a later native macOS / Linux port                                                                                                   |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `J/core/security/MacKeyVault.kt` (28)   | key as hex in the login Keychain through `/usr/bin/security` (`find-generic-password -s FetchJet -a secrets-key -w`, exit 44 = not found; `security -i` with `add-generic-password -U ...` on stdin) | Security framework (`SecItemCopyMatching`, as in `I/core/security/SecretBox.ios.kt`), same service / account so the existing item opens |
| `J/core/security/LinuxKeyVault.kt` (56) | `secrets.key` (hex, mode 600) or the Secret Service through `secret-tool`; `secrets.where` remembers which                                                                                           | same tools through a process helper                                                                                                     |
| `J/core/update/MacUpdater.kt` (44)      | `.dmg`: mount, `ditto` over `FetchJet.app`, reopen (bash script after exit)                                                                                                                          | same script                                                                                                                             |
| `J/core/update/LinuxUpdater.kt` (62)    | `.deb` with `pkexec dpkg -i`, or the portable `.tar.gz` folder swap                                                                                                                                  | same script                                                                                                                             |

### 4.7 The library `io.github.vinceglb:auto-launch:0.9.0`

Not a project file, but `StartupLauncher.jvm.kt` is only a wrapper around it; its Windows
behaviour is written out in 3.21 (it uses JNA `Advapi32Util`). Nothing else of it is used.

### 4.8 `JA/core/network/ProxiedConnection.kt` (21 lines)

`internal fun openAppConnection(url: URL): HttpURLConnection`: opens the connection through
the app proxy when it is on (`Proxy.Type.SOCKS` or `HTTP`, unresolved address,
`JvmProxyLogin.install()`), and sets `Authorization: Basic` from
`NetworkSettings.loginFor(url.host)`.
Users (all part B): `J/feature/media_extractor/MediaExtractorToolManager.jvm.kt:60` (tool
downloads), `J/feature/media_extractor/MediaInspect.jvm.kt:34` (size probes),
`J/feature/torrent_engine/TorrentStreamer.jvm.kt:37` (web seeds),
`JA/feature/torrent_engine/TorrentNativeLoader.kt:207`.

Native replacement: there is no `HttpURLConnection`. Every such call becomes a ktor request on
one shared client that already goes through `useNetworkSettings()` (so proxy and site logins
apply): `M/core/network/AppHttp.kt` (~40 lines, section 5). The rule from `CLAUDE.md` stays:
never a bare client.

### 4.9 `JA/core/log/RedactingOutputStream.kt` (36 lines)

An `OutputStream` that collects bytes until `\n`, passes the line (UTF-8) through
`LogRedact.redact` (`C/core/log/LogRedact.kt`: Cookie / Authorization header values -> `***`,
`scheme://user:pass@` -> `scheme://***@`, URL query values -> `name=…`) and writes it on;
`close` flushes a last unfinished line. Used by `main.kt:286` (desktop log) and the Android log.
Native replacement: the reader thread of `NativeLog` (3.11) does the same per line.

### 4.10 `JA/core/network/JvmProxyLogin.kt` (32 lines)

A `java.net.Authenticator` that answers only proxy requests (`RequestorType.PROXY` or a
protocol starting with `SOCKS`) for the configured proxy host with the saved user / password;
`install()` (once) clears `jdk.http.auth.tunneling.disabledSchemes` and
`jdk.http.auth.proxying.disabledSchemes` and sets it as the default authenticator.
Native replacement: `LocalProxyShim` (3.12).

### 4.11 `JA/core/network/OkHttpProxyLogin.kt` (11 lines)

`okHttpProxyAuthenticator(proxy)`: on a 407, repeats the request once with
`Proxy-Authorization: Basic` (returns null when the request already had the header, so a wrong
password fails instead of looping). Native replacement: `LocalProxyShim` (3.12), which must
keep the "wrong password fails, no loop" behaviour.

### 4.12 Pieces of `main.kt` that belong to this area (the native host or the C API must do them)

| `main.kt` lines           | What                                                                                          | Where it goes                                                                                                                      |
|---------------------------|-----------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| 277-304 `installCrashLog` | log file, 2 MB rule, redaction, uncaught exceptions                                           | `NativeLog` (3.11)                                                                                                                 |
| 698-704                   | `AppLog.verbose` from the environment                                                         | `NativeLog.install`                                                                                                                |
| 717-722                   | `BrowserIntegration.setUpOnFirstRun()` on a background thread                                 | C API init                                                                                                                         |
| 725-733, 749-755          | `--autostart=true`, `StartupPreferences.launchedAtLogin`, start hidden in the tray            | host (`main.cpp`) + C API                                                                                                          |
| 762-768                   | `SystemNotice.notices` -> tray balloon                                                        | tray owner                                                                                                                         |
| 799-800                   | `WhenDone.onExitApp`                                                                          | host                                                                                                                               |
| 803-823                   | tray tooltip from `DownloadNotificationManager.activeTasks`                                   | tray owner                                                                                                                         |
| 874-892                   | bring the window to the front: `toFront`, a synthetic Alt key press, always-on-top for 150 ms | host: `ExternalDownloadBridge.onShowAndFocus`; on Win32 `AllowSetForegroundWindow` / `SetForegroundWindow` with the same Alt trick |

---

## 5. Shared low-level helpers (one set for every package)

All in package `com.michelle.raouf.fetch_jet.core.platform.win`, folder `M/core/platform/win/`,
`internal`, no dependency on the rest of the app except `PathResolver`-free code (the helpers
must not call `PathResolver`, which is built on them). Every Win32 call uses the `W` functions.
Recommended names and signatures:

```kotlin
internal object WinEnv {
    fun get(name: String): String?
    val userProfile: String
    val localAppData: String
    val programFiles: String
    val programFilesX86: String
    val tempDir: String
    val appHome: String
    val computerName: String
    val pid: Int
    fun uptimeMs(): Long
    fun lastErrorText(code: UInt = GetLastError()): String
}

internal object NativeInstall {
    val exePath: String
    val exeDir: String
    val isDevelopmentBuild: Boolean
    val launcherPath: String?
    val bundledExtensionDir: String?
}

internal object NativeHost {
    var mainWindow: COpaquePointer?
    var requestExit: (() -> Unit)?
    var runOnUiThread: ((() -> Unit) -> Unit)?
}

internal class ProcessResult(val exitCode: Int, val output: String, val timedOut: Boolean)

internal class ChildProcess {
    val pid: Int
    fun readLine(): String?
    fun write(bytes: ByteArray)
    fun closeInput()
    fun isAlive(): Boolean
    fun waitFor(timeoutMs: Long): Int?
    fun kill()
    fun close()
}

internal object WinProcess {
    fun quote(arg: String): String
    fun commandLine(args: List<String>): String
    fun run(
        commandLine: String,
        stdin: ByteArray? = null,
        timeoutMs: Long = 15_000,
        workDir: String? = null,
        mergeStderr: Boolean = true,
        environment: Map<String, String>? = null
    ): ProcessResult
    fun start(commandLine: String, workDir: String? = null): Boolean
    fun open(
        commandLine: String,
        workDir: String? = null,
        mergeStderr: Boolean = true,
        environment: Map<String, String>? = null
    ): ChildProcess
}

internal object WinRegistry {
    fun getString(key: String, value: String?): String?
    fun setString(key: String, value: String?, data: String): Int
    fun deleteValue(key: String, value: String): Int
    fun deleteTree(key: String): Int
    fun keyExists(key: String): Boolean
}

internal object WinCrypto {
    fun randomBytes(n: Int): ByteArray
    fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray
    fun aesGcmOpen(key: ByteArray, nonce: ByteArray, cipherAndTag: ByteArray): ByteArray
    fun dpapiProtect(data: ByteArray): ByteArray
    fun dpapiUnprotect(data: ByteArray): ByteArray
    fun sha256(data: ByteArray): ByteArray
    fun ecdsaP256Verify(spkiDer: ByteArray, sha256: ByteArray, derSignature: ByteArray): Boolean
}

internal object WinShell {
    fun open(target: String, verb: String? = null, workDir: String? = null): Int
    fun openUrl(url: String): Boolean
    fun reveal(filePath: String): Boolean
    fun clipboardText(): String?
    fun clipboardSequence(): UInt
    fun pickFile(title: String, filterName: String, pattern: String, owner: COpaquePointer?): String?
    fun pickFolder(title: String, initialPath: String?, owner: COpaquePointer?): String?
}

internal object WinTime {
    fun utcOffsetMs(): Long
    fun minutesOfDay(): Int
    fun localDateTimeText(): String
}
```

Notes:

- `WinEnv.appHome`: the value of the environment variable `FETCHJET_HOME` when it is set and
  not blank (new, for tests: the owner's real `C:/Users/miche/.fetch_jet` must never be used by
  a test run), else `userProfile + "\.fetch_jet"`. `PathResolver.getSettingsDirectory()`, the
  key vault, the browser files and the log must all use it (the JVM code repeats
  `user.home/.fetch_jet` in five places: `PathResolver.jvm.kt:44`, `KeyVault.kt:18`,
  `BrowserIntegration.jvm.kt:137`, `DesktopBrowsers.kt:150,186`, `main.kt:280`; part B has two
  more in `MediaToolPaths.jvm.kt:8,84`).
- `NativeInstall.exePath`: `GetModuleFileNameW(null, ...)` gives the host executable also when
  called from inside `fjcore.dll` (VERIFIED for an exe; the DLL case follows from the API:
  a null module means the process executable). `launcherPath` = `exePath` unless
  `isDevelopmentBuild` (rule: open question 9.2).
- `WinProcess.quote`: the `CommandLineToArgvW` rules (wrap in `"` when the argument is empty or
  has a space, tab or `"`; double the backslashes before a `"` and at the end; escape `"` as `\"`).
  `run` passes the command line unchanged to `CreateProcessW` with `CREATE_NO_WINDOW`
  (VERIFIED: `"C:\a b\c d.exe" "%1"` arrives as written).
- `WinProcess.run` must not read the pipe with a blocking `ReadFile` loop only: the first
  probe version did, and a child that kept the pipe open ignored the timeout. The working loop
  polls `PeekNamedPipe`, reads what is there, checks the deadline, calls `TerminateProcess` on
  timeout (VERIFIED, section 7). The JVM helpers have the same weakness where they call
  `readText()` before `waitFor()` (`BrowserIntegration.reg`, `WindowsUpdater.askStore`).
- `WinProcess.open` is for part B (yt-dlp, ffmpeg, deno: line-by-line output, cancel). Put the
  child in a job object with `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` so `kill()` also ends its
  children (yt-dlp starts ffmpeg and deno).
- `WinRegistry` works under `HKEY_CURRENT_USER` only (nothing else is needed); the `Int`
  results are Win32 error codes, 0 = success, 2 = not found (VERIFIED).
- `WinCrypto` strings: `"AES"`, `"ChainingMode"`, `"ChainingModeGCM"`, `"SHA256"`,
  `"ECDSA_P256"`, `"ECCPUBLICBLOB"`; `BCryptGenRandom(null, buf, n, 2u)` (2 =
  `BCRYPT_USE_SYSTEM_PREFERRED_RNG`).
- `WinShell.pickFile` with `GetOpenFileNameW` (`OFN_EXPLORER or OFN_FILEMUSTEXIST or
  OFN_PATHMUSTEXIST or OFN_NOCHANGEDIR or OFN_HIDEREADONLY`, filter string
  `"<filterName>\u0000<pattern>\u0000All files\u0000*.*\u0000\u0000"`) needs `-lcomdlg32`
  or the run-time lookup of 1.3 (VERIFIED: without it the link fails);
  `IFileOpenDialog` needs `-lole32` and COM vtable calls. `OFN_NOCHANGEDIR` matters: without it
  the dialog changes the process working directory.
- Not in this list because part A does not need them, but part B will: unzip (Windows 10 and
  11 ship `tar.exe`, which extracts `.zip`: `tar -xf <zip> -C <dir>`; or `platform.zlib` plus a
  zip reader), sockets (`ktor-network`), the local bridge HTTP server.

`M/core/network/AppHttp.kt` (replacement for `openAppConnection`, 4.8):

```kotlin
internal object AppHttp {
    val client: HttpClient
    suspend fun head(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
    suspend fun download(
        url: String,
        target: Path,
        headers: Map<String, String> = emptyMap(),
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    )
}
```

`client` is
`NetworkClient { HttpClient(WinHttp) { install(HttpTimeout) {...}; useNetworkSettings() } }.current`.

---

## 6. Work packages

Every package creates only new files under `M/` (plus tests under
`sharedLogic/src/mingwX64Test/` if the build owner adds that source set). No package edits
common code. Sizes are Kotlin lines.

| Package                         | New files                                                                                                                                                                                                                                                                                                          | Expects covered                    | Ports from                                                                                                                                                                   | Helpers needed                                                                                                 | Size | Order                                                                                                                       |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|------|-----------------------------------------------------------------------------------------------------------------------------|
| **A0 Win helpers**              | `core/platform/win/WinEnv.kt`, `NativeInstall.kt`, `NativeHost.kt`, `WinProcess.kt`, `WinRegistry.kt`, `WinCrypto.kt`, `WinShell.kt`, `WinTime.kt`; `core/platform/WindowsStorePackage.kt`                                                                                                                         | none (foundation)                  | `J/core/platform/WindowsStorePackage.kt`, `J/core/security/KeyVault.kt` (`KeyVaultTools.run`), `J/core/update/DesktopUpdater.kt` (`DesktopInstall`), probe code of section 7 | Win32: process with pipes, registry, DPAPI, BCrypt, clipboard, shell open, file dialog, time zone              | ~750 | first; needs the decision of 9.2, and `-lcomdlg32` (file dialog) / `-lole32` (COM) in the build file or the run-time lookup |
| **A1 Small actuals**            | `Platform.mingw.kt`, `core/edition/AppEdition.mingw.kt`, `core/filesystem/AppContainerPath.mingw.kt`, `core/power/SpeedSchedule.mingw.kt`, `core/power/SystemPower.mingw.kt`, `core/util/LocalClock.mingw.kt`, `core/notification/DownloadNotificationManager.mingw.kt`, `core/notification/SystemNotice.mingw.kt` | 3.1, 3.2, 3.4-3.7, 3.15-3.18, 3.24 | `J/Platform.jvm.kt`, `JA/core/edition/*`, `JA/core/filesystem/*`, `JA/core/power/*`, `J/core/power/SystemPower.jvm.kt`, `JA/core/util/*`, `J/core/notification/*`            | `WinShell.clipboardText`, `WinTime`, `WinProcess.run`, `LoadLibraryW` for `SetSuspendState`                    | ~200 | after A0                                                                                                                    |
| **A2 Files**                    | `core/filesystem/PathResolver.mingw.kt`, `TextFilePicker.mingw.kt`, `TorrentFilePicker.mingw.kt`                                                                                                                                                                                                                   | 3.8, 3.9, 3.10                     | `J/core/filesystem/*.jvm.kt`                                                                                                                                                 | `WinEnv`, `WinShell.open / reveal / pickFile / pickFolder`, `WinProcess.start`, okio                           | ~380 | after A0                                                                                                                    |
| **A3 Security**                 | `core/security/CryptoBox.mingw.kt`, `SecretBox.mingw.kt`, `WindowsKeyVault.kt`, `core/update/UpdateSignature.mingw.kt`                                                                                                                                                                                             | 3.19, 3.20, 3.23                   | `JA/core/security/CryptoBox.jvmAndroid.kt`, `J/core/security/SecretBox.jvm.kt`, `KeyVault.kt`, `WindowsKeyVault.kt`, `JA/core/update/UpdateSignature.jvmAndroid.kt`          | `WinCrypto`, `WinEnv.appHome`, okio                                                                            | ~160 | after A0                                                                                                                    |
| **A4 Network**                  | `core/network/NetworkSettings.mingw.kt`, `StreamHttpClient.mingw.kt`, `LocalProxyShim.kt`, `AppHttp.kt`                                                                                                                                                                                                            | 3.12, 3.13, 3.14                   | `J/core/network/*.jvm.kt`, `JA/core/network/JvmProxyLogin.kt`, `OkHttpProxyLogin.kt`, `ProxiedConnection.kt`; shape from `I/core/network/NetworkSettings.ios.kt`             | `ktor-client-winhttp`, `ktor-network` sockets; `WinProcess.run` only for the optional curl probe               | ~400 | independent of A0 (can start at once); test with `scripts/network-test/test_proxies.py`                                     |
| **A5 Log**                      | `core/log/DiagnosticsLog.mingw.kt`, `core/log/NativeLog.kt`                                                                                                                                                                                                                                                        | 3.11                               | `main.kt:277-304,698-704`, `JA/core/log/RedactingOutputStream.kt`                                                                                                            | `platform.posix` `_pipe / _dup2 / _read`, a reader thread, `WinEnv.appHome`, `WinTime.localDateTimeText`, okio | ~150 | after A0                                                                                                                    |
| **A6 Install, startup, update** | `core/startup/StartupLauncher.mingw.kt`, `core/update/PlatformUpdater.mingw.kt`, `core/update/WindowsUpdater.kt`                                                                                                                                                                                                   | 3.21, 3.22                         | `J/core/startup/StartupLauncher.jvm.kt` + AutoLaunch 0.9.0, `J/core/update/PlatformUpdater.jvm.kt`, `DesktopUpdater.kt`, `WindowsUpdater.kt`                                 | `WinRegistry`, `WinProcess.run / start`, `NativeInstall`, `NativeHost.requestExit`, `WindowsStorePackage`      | ~210 | after A0; packaging facts of 4.4                                                                                            |
| **A7 Browser**                  | `core/browser/BrowserIntegration.mingw.kt`, `core/browser/DesktopBrowsers.kt`                                                                                                                                                                                                                                      | 3.3                                | `J/core/browser/BrowserIntegration.jvm.kt`, `DesktopBrowsers.kt`                                                                                                             | `WinRegistry`, `WinProcess.start`, `WinShell.open`, `WinEnv`, `NativeInstall.bundledExtensionDir`, okio        | ~280 | after A0; the extension folder next to the exe comes from packaging                                                         |

Total about 2 500 lines.

Order and advice:

1. The module links only when all 44 actuals exist. To get compiler feedback early, a first
   pass should create every file of A1-A7 as a compiling stub (the iOS actuals of
   `BrowserIntegration`, `SystemPower`, `StartupLauncher`, `PlatformUpdater`, `DiagnosticsLog`,
   `UpdateSignature` are ready-made "off" shapes), then the packages replace the bodies. Two
   stubs are not harmless and should be real from the start: `PathResolver` (settings folder,
   file names) and `SecretBox` (a stub that throws makes `Secrets.protect` store passwords and
   cookies in plain text; a stub that returns the input would write `fjenc1:` values the JVM
   app cannot open).
2. A0 first, then A1, A2, A3, A5, A6, A7 in parallel (disjoint files). A4 does not need A0.
3. A3 before anything writes `network.json`, `bridge.json`, `devices.json` or `tasks_cache.json`
   from the native app (see 1).
4. A5 before debugging the others (without it `println` goes nowhere in the GUI process).
5. The C API / host work (not in this part) needs from part A: `NativeHost` setters,
   `DiagnosticsLog.install()`, `BrowserIntegration.setUpOnFirstRun()`, the clipboard watcher
   (`WinShell.clipboardSequence` + `getClipboardText`), `DownloadNotificationManager.activeTasks`,
   `SystemNotice.notices`, `WhenDone.onExitApp`, `ExternalDownloadBridge.onShowAndFocus`,
   `StartupPreferences`.

---

## 7. Probe: what was run, results, reference code

Folder `E:/tools/probes/platform-core` (outside the repository): a copy of the Gradle wrapper
of `desktopNative/core`, `build.gradle.kts` with one `mingwX64` executable (`fjprobe`,
`linkerOpts("-lole32")`), one source file `src/mingwX64Main/kotlin/Probe.kt` (about 510 lines),
built offline with `gradlew.bat --priority low --offline linkDebugExecutableMingwX64` (12 s
after the first build). Test inputs in `home/` (a throw-away P-256 key made with Git's
`openssl`, a file, its `.sig` and SHA-256). Nothing was downloaded; nothing under
`C:/Users/miche/.fetch_jet` was read or written. Registry: one value under
`HKCU\Software\FetchJetProbe\PlatformCore` was written, read and deleted (both keys removed).

| Check                                                                                                                          | Result                                                                                                   |
|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| AES-256-GCM (BCrypt), NIST vector, seal and open                                                                               | `cea7403d4d606b6e074ec5d3baf39d18` / tag `d0d1c8a799996bf0265b98b5d48ab919`, opens back to 16 zero bytes |
| AES-GCM round trip with Arabic text, empty plain text                                                                          | ok; sealed size = plain + 28                                                                             |
| AES-GCM with one tag bit flipped                                                                                               | fails with `0xc000a002`                                                                                  |
| ECDSA P-256 verify of an `openssl dgst -sha256 -sign` signature (DER -> r,s)                                                   | true; false with a changed hash                                                                          |
| SHA-256 (BCrypt) of `abc`                                                                                                      | `ba7816bf...15ad`                                                                                        |
| DPAPI: blob from the JVM app's PowerShell call -> native `CryptUnprotectData`                                                  | same 32 bytes                                                                                            |
| DPAPI: native `CryptProtectData` -> .NET `Unprotect`                                                                           | same 32 bytes                                                                                            |
| `CreateProcessW` with pipes, `CREATE_NO_WINDOW`, stdout + stderr merged, exit code                                             | `exit=3`, both lines captured                                                                            |
| timeout: `ping -n 6` with a 1 s limit                                                                                          | killed, `timedOut=true` (the first version with a blocking read was not)                                 |
| stdin to the child (`findstr "^"`, Arabic line)                                                                                | echoed back intact                                                                                       |
| verbatim command line with quotes and spaces                                                                                   | arrives unchanged                                                                                        |
| `println` captured through `_pipe` + `_dup2(…, 1)`                                                                             | captured, also with stdout / stderr closed at start                                                      |
| `SHGetKnownFolderPath` Downloads / Documents / Desktop / Profile                                                               | `C:\Users\miche\Downloads`, `...\Documents`, `...\Desktop`, `C:\Users\miche`                             |
| `GetTimeZoneInformation` offset, `GetLocalTime`                                                                                | 7 200 000 ms, correct minute of day                                                                      |
| `GetModuleFileNameW`, `GetTempPathW`, `GetTickCount64`, `GetCurrentProcessId`                                                  | ok                                                                                                       |
| clipboard `CF_UNICODETEXT` read (only the length was printed)                                                                  | ok                                                                                                       |
| registry create / set `REG_SZ` / `RegGetValueW` / missing value / delete                                                       | 0, same text back, 2, 0                                                                                  |
| linking a call to `CoTaskMemFree` without `-lole32`, to `GetOpenFileNameW` without `-lcomdlg32`                                | link errors (1.3)                                                                                        |
| run-time lookup: `powrprof.dll!IsPwrSuspendAllowed` called through a `CFunction` pointer; `SetSuspendState` found (not called) | works; answer 0 on this PC                                                                               |
| `kernel32!GetCurrentPackageFullName` through `GetProcAddress`                                                                  | 15700 (not a packaged process)                                                                           |

Reference code from the probe (compiles and runs with Kotlin 2.4.20; imports from
`kotlinx.cinterop.*`, `platform.windows.*`, `platform.posix.memset`):

```kotlin
fun ok(status: Int, what: String) {
    check(status == 0) { "$what failed: 0x${status.toUInt().toString(16)}" }
}

fun gcm(encrypt: Boolean, key: ByteArray, nonce: ByteArray, input: ByteArray, tag: ByteArray): ByteArray = memScoped {
    val hAlg = alloc<COpaquePointerVar>()
    ok(BCryptOpenAlgorithmProvider(hAlg.ptr, "AES", null, 0u), "open AES")
    try {
        val mode = "ChainingModeGCM"
        ok(
            BCryptSetProperty(
                hAlg.value, "ChainingMode", mode.wcstr.ptr.reinterpret(),
                ((mode.length + 1) * 2).toUInt(), 0u
            ), "set GCM"
        )
        val hKey = alloc<COpaquePointerVar>()
        key.usePinned { k ->
            ok(
                BCryptGenerateSymmetricKey(
                    hAlg.value, hKey.ptr, null, 0u, k.addressOf(0).reinterpret(), key.size.toUInt(), 0u
                ), "key"
            )
        }
        try {
            val info = alloc<BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO>()
            memset(info.ptr, 0, sizeOf<BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO>().toULong())
            info.cbSize = sizeOf<BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO>().toUInt()
            info.dwInfoVersion = 1u
            val inBuf = allocArray<UByteVar>(input.size + 1)
            val outBuf = allocArray<UByteVar>(input.size + 1)
            val nonceBuf = allocArray<UByteVar>(nonce.size)
            val tagBuf = allocArray<UByteVar>(tag.size)
            for (i in input.indices) inBuf[i] = input[i].toUByte()
            for (i in nonce.indices) nonceBuf[i] = nonce[i].toUByte()
            for (i in tag.indices) tagBuf[i] = tag[i].toUByte()
            info.pbNonce = nonceBuf
            info.cbNonce = nonce.size.toUInt()
            info.pbTag = tagBuf
            info.cbTag = tag.size.toUInt()
            val done = alloc<UIntVar>()
            val status = if (encrypt) BCryptEncrypt(
                hKey.value, inBuf, input.size.toUInt(), info.ptr, null, 0u,
                outBuf, input.size.toUInt(), done.ptr, 0u
            ) else BCryptDecrypt(
                hKey.value, inBuf, input.size.toUInt(), info.ptr, null, 0u,
                outBuf, input.size.toUInt(), done.ptr, 0u
            )
            ok(status, if (encrypt) "BCryptEncrypt" else "BCryptDecrypt")
            if (encrypt) for (i in tag.indices) tag[i] = tagBuf[i].toByte()
            ByteArray(done.value.toInt()) { outBuf[it].toByte() }
        } finally {
            BCryptDestroyKey(hKey.value)
        }
    } finally {
        BCryptCloseAlgorithmProvider(hAlg.value, 0u)
    }
}

fun derToRaw(der: ByteArray): ByteArray? {
    var p = 0
    fun len(): Int {
        var l = der[p++].toInt() and 0xff
        if (l and 0x80 != 0) {
            val n = l and 0x7f
            l = 0
            repeat(n) { l = (l shl 8) or (der[p++].toInt() and 0xff) }
        }
        return l
    }
    fun int32(): ByteArray? {
        if (der[p++].toInt() != 0x02) return null
        val l = len()
        var v = der.copyOfRange(p, p + l)
        p += l
        while (v.size > 32 && v[0].toInt() == 0) v = v.copyOfRange(1, v.size)
        if (v.size > 32) return null
        return ByteArray(32 - v.size) + v
    }
    return try {
        if (der[p++].toInt() != 0x30) return null
        len()
        val r = int32() ?: return null
        val s = int32() ?: return null
        r + s
    } catch (_: Throwable) {
        null
    }
}

fun verifyP256(spki: ByteArray, hash: ByteArray, derSignature: ByteArray): Boolean = memScoped {
    if (spki.size < 65) return@memScoped false
    val point = spki.copyOfRange(spki.size - 65, spki.size)
    if (point[0].toInt() != 4) return@memScoped false
    val raw = derToRaw(derSignature) ?: return@memScoped false
    val blob = byteArrayOf(0x45, 0x43, 0x53, 0x31, 32, 0, 0, 0) + point.copyOfRange(1, 65)
    val hAlg = alloc<COpaquePointerVar>()
    if (BCryptOpenAlgorithmProvider(hAlg.ptr, "ECDSA_P256", null, 0u) != 0) return@memScoped false
    val hKey = alloc<COpaquePointerVar>()
    val imported = blob.usePinned {
        BCryptImportKeyPair(
            hAlg.value, null, "ECCPUBLICBLOB", hKey.ptr, it.addressOf(0).reinterpret(), blob.size.toUInt(), 0u
        )
    }
    if (imported != 0) {
        BCryptCloseAlgorithmProvider(hAlg.value, 0u)
        return@memScoped false
    }
    val status = hash.usePinned { h ->
        raw.usePinned { s ->
            BCryptVerifySignature(
                hKey.value, null, h.addressOf(0).reinterpret(), hash.size.toUInt(),
                s.addressOf(0).reinterpret(), raw.size.toUInt(), 0u
            )
        }
    }
    BCryptDestroyKey(hKey.value)
    BCryptCloseAlgorithmProvider(hAlg.value, 0u)
    status == 0
}

fun dpapi(protect: Boolean, data: ByteArray): ByteArray = memScoped {
    val input = alloc<DATA_BLOB>()
    val output = alloc<DATA_BLOB>()
    val buf = allocArray<UByteVar>(data.size + 1)
    for (i in data.indices) buf[i] = data[i].toUByte()
    input.cbData = data.size.toUInt()
    input.pbData = buf
    val done = if (protect) CryptProtectData(
        input.ptr, null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), output.ptr
    ) else CryptUnprotectData(
        input.ptr, null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN.toUInt(), output.ptr
    )
    check(done != 0) { "DPAPI ${if (protect) "Protect" else "Unprotect"} failed (${GetLastError()})" }
    val out = output.pbData!!.reinterpret<ByteVar>().readBytes(output.cbData.toInt())
    LocalFree(output.pbData)
    out
}

class RunResult(val exit: Int, val out: String, val timedOut: Boolean)

fun runProcess(commandLine: String, stdin: ByteArray?, timeoutMs: Long): RunResult = memScoped {
    val sa = alloc<SECURITY_ATTRIBUTES>()
    sa.nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
    sa.bInheritHandle = 1
    sa.lpSecurityDescriptor = null
    val outRead = alloc<HANDLEVar>()
    val outWrite = alloc<HANDLEVar>()
    val inRead = alloc<HANDLEVar>()
    val inWrite = alloc<HANDLEVar>()
    check(CreatePipe(outRead.ptr, outWrite.ptr, sa.ptr, 0u) != 0) { "CreatePipe ${GetLastError()}" }
    check(CreatePipe(inRead.ptr, inWrite.ptr, sa.ptr, 0u) != 0) { "CreatePipe ${GetLastError()}" }
    SetHandleInformation(outRead.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)
    SetHandleInformation(inWrite.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)
    val si = alloc<STARTUPINFOW>()
    memset(si.ptr, 0, sizeOf<STARTUPINFOW>().toULong())
    si.cb = sizeOf<STARTUPINFOW>().toUInt()
    si.dwFlags = STARTF_USESTDHANDLES.toUInt()
    si.hStdInput = inRead.value
    si.hStdOutput = outWrite.value
    si.hStdError = outWrite.value
    val pi = alloc<PROCESS_INFORMATION>()
    val started = CreateProcessW(
        null, commandLine.wcstr.ptr, null, null, 1, CREATE_NO_WINDOW.toUInt(), null, null, si.ptr, pi.ptr
    )
    CloseHandle(outWrite.value)
    CloseHandle(inRead.value)
    if (started == 0) {
        val error = GetLastError()
        CloseHandle(outRead.value)
        CloseHandle(inWrite.value)
        error("CreateProcess failed ($error)")
    }
    val written = alloc<UIntVar>()
    if (stdin != null && stdin.isNotEmpty()) stdin.usePinned {
        WriteFile(inWrite.value, it.addressOf(0), stdin.size.toUInt(), written.ptr, null)
    }
    CloseHandle(inWrite.value)
    val collected = ArrayList<Byte>()
    val buffer = allocArray<ByteVar>(4096)
    val read = alloc<UIntVar>()
    val available = alloc<UIntVar>()
    val deadline = GetTickCount64() + timeoutMs.toULong()
    var timedOut = false
    while (true) {
        available.value = 0u
        val alive = PeekNamedPipe(outRead.value, null, 0u, null, available.ptr, null) != 0
        if (available.value > 0u) {
            if (ReadFile(outRead.value, buffer, 4096u, read.ptr, null) != 0) {
                for (i in 0 until read.value.toInt()) collected.add(buffer[i])
            }
            continue
        }
        if (!alive) break
        if (GetTickCount64() >= deadline) {
            timedOut = true
            TerminateProcess(pi.hProcess, 1u)
            break
        }
        WaitForSingleObject(pi.hProcess, 15u)
    }
    CloseHandle(outRead.value)
    WaitForSingleObject(pi.hProcess, 2000u)
    val code = alloc<UIntVar>()
    GetExitCodeProcess(pi.hProcess, code.ptr)
    CloseHandle(pi.hThread)
    CloseHandle(pi.hProcess)
    RunResult(code.value.toInt(), collected.toByteArray().decodeToString(), timedOut)
}

fun localUtcOffsetMs(): Long = memScoped {
    val tz = alloc<TIME_ZONE_INFORMATION>()
    val id = GetTimeZoneInformation(tz.ptr)
    val bias = tz.Bias + when (id) {
        2u -> tz.DaylightBias
        1u -> tz.StandardBias
        else -> 0
    }
    -bias * 60_000L
}

fun redirectProbe(): String = memScoped {
    val fds = allocArray<IntVar>(2)
    if (_pipe(fds, 65536u, O_BINARY) != 0) return@memScoped "pipe failed"
    val saved = _dup(1)
    val dup = _dup2(fds[1], 1)
    println("hello-redirect")
    if (saved >= 0) _dup2(saved, 1)
    if (dup != 0) return@memScoped "dup2 failed ($dup) saved=$saved"
    _close(fds[1])
    val buf = allocArray<ByteVar>(256)
    val n = _read(fds[0], buf, 255u)
    _close(fds[0])
    if (n <= 0) "nothing read ($n)" else buf.readBytes(n).decodeToString().trim()
}
```

Known limits of this reference code: `runProcess` writes all of stdin before it starts
reading, so an input larger than the pipe buffer whose child also writes a lot would block (fine for
the small inputs of this part; a big input needs a writer thread); it polls every
15 ms; a production version should put each child in a job object.

---

## 8. Risks

| #  | Risk                                                                                  | Level      | Why / what to do                                                                                                                                                                                                                      |
|----|---------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Proxy with login and SOCKS5 need a new local forwarding proxy (`LocalProxyShim`)      | HIGH       | The ktor WinHTTP engine has no SOCKS and no proxy credentials (read from its klib). New code on the download path; test with `scripts/network-test/test_proxies.py`; the shim's port is usable by any local program while it is open. |
| 2  | WinHTTP against googlevideo and with 32 parallel ranged requests is untested          | MEDIUM     | The JVM had to leave OkHttp for the JDK client because of 403s. First real test of part B's media engine decides.                                                                                                                     |
| 3  | Engine-less `HttpClient { }` in five common files may not find WinHTTP inside the DLL | MEDIUM     | Not verified; a fix would be in common code (outside `M/`). Check on the first run.                                                                                                                                                   |
| 4  | `println` capture inside `fjcore.dll` in the GUI host                                 | MEDIUM     | Verified only in a console exe. Without it there is no `app.log`.                                                                                                                                                                     |
| 5  | A development build acting as the installed app (URL scheme, Run key, real app home)  | MEDIUM     | Needs the rule of 9.2 before A0 is written.                                                                                                                                                                                           |
| 6  | Sleep / shut down cannot be tested without doing it on the owner's PC                 | MEDIUM     | Owner test, as for the JVM version.                                                                                                                                                                                                   |
| 7  | JVM-to-native upgrade through the in-app updater                                      | MEDIUM     | Depends on the installer: same MSI upgrade id, per-user, `FetchJet.exe` at the same path, `.sig` files, one `.msi` per release (4.4).                                                                                                 |
| 8  | `exitProcess` from the DLL after starting the installer                               | LOW-MEDIUM | Ends the host without its shutdown code; go through `NativeHost.requestExit`.                                                                                                                                                         |
| 9  | File dialogs and shell calls from worker threads                                      | LOW-MEDIUM | Owner window, focus, COM apartment; hand test.                                                                                                                                                                                        |
| 10 | A stub or bug in `SecretBox` writes secrets the JVM app cannot read, or plain text    | MEDIUM     | Make A3 real before the native app writes any settings file; never create a new key when `secrets.key` exists but does not open.                                                                                                      |
| 11 | `ole32` and `comdlg32` are not linked                                                 | LOW        | Add `linkerOpts("-lole32", "-lcomdlg32")` (shared build file), or use the run-time lookup, or avoid them as proposed.                                                                                                                 |
| 12 | Process helper deadlocks / ignored timeouts                                           | LOW-MEDIUM | Use the polling loop of section 7; add a job object for part B.                                                                                                                                                                       |
| 13 | Batch update script with a non-ASCII temp path                                        | LOW        | Same weakness as today; `chcp 65001` line.                                                                                                                                                                                            |
| 14 | WinHTTP may follow the Windows system proxy when the app proxy is off                 | LOW        | Not verified; the JVM app goes direct.                                                                                                                                                                                                |

---

## 9. Open questions

1. **Proxy design.** Keep WinHTTP + `LocalProxyShim` (nothing to download), or allow one
   download of `io.ktor:ktor-client-curl` for `mingwX64` (size unknown, libcurl handles HTTP /
   SOCKS5 proxies with logins and is the same stack as `curl.exe`)? Internet use is the
   owner's call.
2. **What marks a development build?** Proposal: `desktopNative/build.cmd` drops a file
   `fjdev.flag` next to the exe; when it is there `NativeInstall.isDevelopmentBuild` is true:
   no `fetchjet://` registration, no Run key, no in-app install, and the app home is
   `FETCHJET_HOME` or `<exeDir>\home` instead of `%USERPROFILE%\.fetch_jet`. The installer
   never ships the flag. Is the extra environment variable `FETCHJET_HOME` acceptable?
3. **User folders.** Keep the JVM rule (`%USERPROFILE%\OneDrive\Downloads` if it exists, else
   `%USERPROFILE%\Downloads`) for identical defaults, or ask Windows
   (`SHGetKnownFolderPath(FOLDERID_Downloads)`, right when the user moved the folder; needs
   `-lole32`)? On this PC both give the same paths.
4. **Run key text.** Write the simple `"<exe>" --autostart=true` (no console flash at login)
   while still recognising the JVM app's `cmd /c start ...` value, or keep the exact old string?
5. **Installer and packaging** (outside part A, but several answers here depend on it): MSI
   with the same upgrade id and `FetchJet.exe` at the same path; where the Chromium extension
   folder sits next to the exe; the MSIX manifest (protocol `fetchjet`, startup task) for the
   Store build.
6. **Torrents.** `platformSupportsTorrent()` should stay false until part B has a native
   torrent backend: is a first native build without torrents acceptable?
7. **Who owns the tray icon, the balloons, the clipboard watcher and the "bring to front"
   trick**: `main.cpp` or Kotlin (`Shell_NotifyIconW` is available in `platform.windows`)?
   Part A only provides the data (`activeTasks`, `notices`, `getClipboardText`).
8. **Verbose logs at build time.** The JVM build bakes `fetchjet.verboseLogs` into the
   launcher; the native build would need a generated constant. Is the environment variable
   `FETCHJET_VERBOSE_LOGS` alone enough?
9. **`getPlatform().name`.** `"Windows"` is proposed; does anything outside this part want the
   Windows version in it?
