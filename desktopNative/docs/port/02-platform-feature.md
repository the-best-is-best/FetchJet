# 02: Platform inventory, part B: `feature/` (Windows native port)

Written 2026-11-09 from branch `desktop-native`. Scope: the 20 `expect` declarations under
`sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/feature/` and every JVM-only file
under `feature/` in `jvmMain` and `jvmAndroidMain` that they depend on.

Path shorthand used below:

| Short         | Full path                                                                           |
|---------------|-------------------------------------------------------------------------------------|
| `common/`     | `sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/`                   |
| `jvm/`        | `sharedLogic/src/jvmMain/kotlin/com/michelle/raouf/fetch_jet/`                      |
| `jvmAndroid/` | `sharedLogic/src/jvmAndroidMain/kotlin/com/michelle/raouf/fetch_jet/`               |
| `ios/`        | `sharedLogic/src/iosMain/kotlin/com/michelle/raouf/fetch_jet/`                      |
| `mingw/`      | `sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/` (to be created) |

Nothing in the repo was changed by this task except this file. One probe was written and run
outside the repo (section 10).

---

## 0. Summary table

| #  | Expect                          | Common file:line                                            | JVM actual                                                                          | Native plan                                              | Lines                         | Risk   |
|----|---------------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------|----------------------------------------------------------|-------------------------------|--------|
| 1  | `createHttpEngine`              | `feature/http_downloader/HttpDownloadEngine.kt:19`          | `jvmAndroid/feature/http_downloader/HttpDownloadEngine.jvmAndroid.kt:5`             | return `HttpChunkDownloader` (common)                    | 10                            | low    |
| 2  | `runYtDlpJson`                  | `feature/media_extractor/DirectMediaResolver.kt:141`        | `jvm/.../MediaStreamExtractorPlatform.jvm.kt:104`                                   | `runYtDlp` over `WinProcess`                             | (in 13)                       | medium |
| 3  | `runLocalFfmpeg`                | `feature/media_extractor/HlsDownloader.kt:495`              | `jvm/.../LocalFfmpeg.jvm.kt:8`                                                      | `WinProcess`, 30 min limit                               | 45                            | low    |
| 4  | `getMediaExtractorToolStatus`   | `feature/media_extractor/MediaExtractorToolManager.kt:10`   | `jvm/.../MediaExtractorToolManager.jvm.kt:16`                                       | okio file checks                                         | (in 11)                       | low    |
| 5  | `downloadMediaExtractorTool`    | same `:11`                                                  | same `:422`                                                                         | ktor WinHttp + okio hash + zip                           | 380                           | medium |
| 6  | `autoUpdateMediaTools`          | same `:12`                                                  | same `:440`                                                                         | same                                                     | (in 5)                        | medium |
| 7  | `isMediaExtractorToolInstalled` | same `:13`                                                  | same `:30`                                                                          | okio                                                     | (in 11)                       | low    |
| 8  | `isFfmpegToolInstalled`         | same `:14`                                                  | same `:34`                                                                          | okio + `ffmpeg -version`                                 | (in 11)                       | low    |
| 9  | `getMediaToolsDirectory`        | same `:15`                                                  | same `:38`                                                                          | `<home>/.fetch_jet/tools`                                | (in 11)                       | low    |
| 10 | `getJsRuntimeStatus`            | same `:19`                                                  | same `:27`                                                                          | deno.exe check                                           | (in 11)                       | low    |
| 11 | `toolUpdateSwapsAtomically`     | same `:38`                                                  | same `:477`                                                                         | `true`                                                   | 110 (tool paths file)         | low    |
| 12 | `resolvePlatformStream`         | `feature/media_extractor/MediaStreamExtractorPlatform.kt:6` | `jvm/.../MediaStreamExtractorPlatform.jvm.kt:8`                                     | `runYtDlp`                                               | (in 13)                       | medium |
| 13 | `lastMediaInspectionError`      | same `:9`                                                   | same `:26`                                                                          | volatile var                                             | 130                           | low    |
| 14 | `inspectPlatformQualities`      | same `:32`                                                  | `jvm/.../MediaInspect.jvm.kt:109`                                                   | copy of the JVM parser                                   | 430                           | medium |
| 15 | `downloadMediaWithPlatformTool` | same `:59`                                                  | `jvm/.../MediaYtDlpDownload.jvm.kt:9`                                               | `WinProcess` + line parser + local throttling proxy      | 520 + 280                     | high   |
| 16 | `inspectPlatformPlaylist`       | `feature/media_extractor/Playlist.kt:33`                    | `jvm/.../MediaStreamExtractorPlatform.jvm.kt:85`                                    | `runYtDlp`                                               | (in 13)                       | low    |
| 17 | `createTorrentBackend`          | `feature/torrent_engine/TorrentBackend.kt:78`               | `jvmAndroid/.../TorrentBackend.jvmAndroid.kt:14` + `Libtorrent4jBackend.kt`         | stub first, then `fjtorrent.dll` (C API over libtorrent) | 40, then 350 Kotlin + 500 C++ | high   |
| 18 | `streamUrlToFile`               | `feature/torrent_engine/TorrentStreamer.kt:18`              | `jvm/.../TorrentStreamer.jvm.kt:11`                                                 | `streamUrlWithClient` (common) with WinHttp              | 25                            | low    |
| 19 | `createDeviceSync`              | `feature/device_sync/DeviceSync.kt:112`                     | `jvmAndroid/.../DeviceSyncFactory.jvmAndroid.kt:5` + `DeviceSyncImpl.jvmAndroid.kt` | port over Winsock + CNG (BCrypt)                         | 40 + 760                      | medium |
| 20 | `MulticastLock`                 | `feature/device_sync/MulticastLock.kt:7`                    | `jvm/.../MulticastLock.jvm.kt:3`                                                    | no-op                                                    | 6                             | low    |

Facts checked on this PC (2026-11-09) that the plans rely on:

- `platform.windows` of Kotlin/Native 2.4.20
  (`~/.konan/kotlin-native-prebuilt-windows-x86_64-2.4.20`)
  contains `CreateProcessW`, `CreatePipe`, `PeekNamedPipe`, `SetHandleInformation`,
  `InitializeProcThreadAttributeList`, `UpdateProcThreadAttribute`, `STARTUPINFOEXW`,
  `CreateJobObjectW`, `AssignProcessToJobObject`, `SetInformationJobObject`,
  `TerminateJobObject`, `JOBOBJECT_EXTENDED_LIMIT_INFORMATION`, `WaitForSingleObject`,
  `GetExitCodeProcess`, `TerminateProcess`, `SearchPathW`, `MoveFileExW`, `ReplaceFileW`,
  `GetTempPathW`, `SHGetKnownFolderPath`, `GetComputerNameExW`, all `BCrypt*` functions used
  below (`BCryptOpenAlgorithmProvider`, `BCryptGenerateKeyPair`, `BCryptExportKey`,
  `BCryptImportKeyPair`, `BCryptSecretAgreement`, `BCryptDeriveKey`, `BCryptEncrypt`,
  `BCryptGenRandom`, `BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO`), `CryptProtectData` /
  `CryptUnprotectData`, `getaddrinfo`, `inet_pton`, `inet_ntop`, `INTERFACE_INFO`,
  `SIO_GET_INTERFACE_LIST`. `platform.posix` contains `WSAIoctl`, `WSASocketW`, `WSAPoll`,
  `setsockopt`, `recvfrom`, `sendto`, `closesocket`, `gethostname`. No extra cinterop `.def` is
  needed for process, sockets or crypto.
- Not in the platform klibs: `GetAdaptersAddresses` (`iphlpapi.h`), `CreateToolhelp32Snapshot`.
  Neither is needed: broadcast addresses come from `WSAIoctl(SIO_GET_INTERFACE_LIST)` and
  process trees are killed through a Job object.
- `platform.zlib` exists for `mingw_x64` (static zlib): raw inflate for the zip reader.
- okio 3.18.2 for mingwX64 uses the wide file APIs (`_wfopen`, `_wmkdir`, `_wopendir`,
  `_wstat64`, `_wremove`, `_wrmdir`, `MoveFileExW`, `CreateFileW`): Unicode paths (Arabic
  titles) work, and `FileSystem.SYSTEM.atomicMove` is `MoveFileExW`. okio also gives SHA-256,
  SHA-1 and MD5 in common code (`HashingSink`, `HashingSource`, `ByteString.sha256()`): already
  used by `common/core/util/ChecksumUtil.kt:40`.
- The Gradle cache has `ktor-client-winhttp-mingwx64` and `ktor-network-mingwx64` 3.6.1, and
  `sharedLogic/build.gradle.kts` already adds both to `mingwX64Main`.
- CNG interop with the JVM crypto of device sync is proven by a probe (section 10).
- `C:/msys64/clang64` has clang++, cmake, ninja, lld, Boost 1.92 headers, OpenSSL 3.6.5 (static
  `libssl.a` / `libcrypto.a`) and a static `libc++.a`. The libtorrent source is not on this PC.

Kotlin/JVM constructs that have no Kotlin/Native equivalent and how to replace them in every
file ported from `jvmMain`:

| JVM                                                  | Native replacement                                                                          |
|------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `java.io.File`                                       | `okio.Path` + `FileSystem.SYSTEM`                                                           |
| `ProcessBuilder`, `Process`                          | `WinProcess` (section 9.1)                                                                  |
| `Thread { }` reader threads                          | coroutine on `Dispatchers.IO` (`import kotlinx.coroutines.IO`) doing blocking `ReadFile`    |
| `@Volatile`, `AtomicBoolean`, `AtomicLong`           | `kotlin.concurrent.Volatile`, `kotlin.concurrent.AtomicInt`, `kotlin.concurrent.AtomicLong` |
| `synchronized`, `@Synchronized`, `ConcurrentHashMap` | `FjLock` (section 9.6) around a plain map                                                   |
| `System.currentTimeMillis()`                         | `nowEpochMs()` (`common/feature/queue_manager`, already used by `HlsDownloader.kt:488`)     |
| `System.nanoTime()`                                  | `kotlin.time.TimeSource.Monotonic`                                                          |
| `String.format("%02x")`                              | `toString(16).padStart(2, '0')`                                                             |
| `java.net.HttpURLConnection`                         | ktor `HttpClient` (WinHttp engine) with `useNetworkSettings()`                              |
| `java.util.zip`                                      | `ZipReader` (section 9.4)                                                                   |
| `System.getProperty("user.home")`                    | `PathResolver().getSettingsDirectory()` (part A decides how the home is found)              |
| `System.getenv`                                      | `platform.posix.getenv(...)?.toKString()`                                                   |
| `println` (FJSYNC, torrent diagnostics)              | keep `println` (part A's `DiagnosticsLog` decides where stdout goes)                        |

---

## 1. `createHttpEngine`

**Expect** (`common/feature/http_downloader/HttpDownloadEngine.kt:14-22`):

```kotlin
/**
 * Desktop and Android: the multi-connection Ktor downloader.
 * iOS: a background NSURLSession engine, so downloads keep going when the app is
 * suspended or killed and finish without the silent-audio trick.
 */
expect fun createHttpEngine(
    pathResolver: PathResolver,
    speedLimitProvider: (String) -> Long
): HttpDownloadEngine
```

**Callers:** `common/feature/queue_manager/QueueManagerRepository.kt:82` (one engine for the
whole app; the provider is `getEffectiveDownloadLimit(taskId)`).

**JVM actual** (`jvmAndroid/feature/http_downloader/HttpDownloadEngine.jvmAndroid.kt:5-11`):
returns `HttpChunkDownloader(pathResolver, speedLimitProvider)`. `HttpChunkDownloader`
(`common/feature/http_downloader/HttpChunkDownloader.kt:31`) is common code; all its platform
needs go through `NetworkClient` / `createStreamHttpClient` (part A).

**iOS actual** (`ios/feature/http_downloader/IosBackgroundHttpEngine.kt:377`): returns
`IosHybridHttpEngine` (background NSURLSession or the common engine). Not a model for Windows.

**On-disk:** none of its own. The `.partN` / `.chunks` files in
`<temp>/FetchJetDownloads/task_<id>` are written by common code, so a download paused in the
JVM app resumes in the native app as long as part A's `PathResolver.getTempDirectory()` returns
the same folder (`%TEMP%\FetchJetDownloads`, `jvm/core/filesystem/PathResolver.jvm.kt:51`).

**Native plan:** `mingw/feature/http_downloader/HttpDownloadEngine.mingw.kt`, identical to the
JVM actual. 10 lines. Risk low (all the risk is in part A's WinHttp `StreamHttpClient`).

---

## 2. Media tools: paths, status, install, update

### 2.1 Expects (`common/feature/media_extractor/MediaExtractorToolManager.kt`)

```kotlin
expect fun getMediaExtractorToolStatus(): String                                                  // :10
expect suspend fun downloadMediaExtractorTool(onProgress: (ToolDownloadProgress) -> Unit = {}): Boolean   // :11
expect suspend fun autoUpdateMediaTools(onProgress: (ToolDownloadProgress) -> Unit = {}): Boolean         // :12
expect fun isMediaExtractorToolInstalled(): Boolean                                               // :13
expect fun isFfmpegToolInstalled(): Boolean                                                       // :14
expect fun getMediaToolsDirectory(): String                                                       // :15
data class JsRuntimeStatus(val name: String, val isReady: Boolean)                                // :17
expect fun getJsRuntimeStatus(): JsRuntimeStatus                                                  // :19
internal expect val toolUpdateSwapsAtomically: Boolean                                            // :38
data class ToolDownloadProgress(val tool: String, val fraction: Float? = null)                    // :40
```

None of them has a KDoc. The KDoc of `MediaToolsLock` (same file `:21-27`) explains
`toolUpdateSwapsAtomically`: true = the installed yt-dlp stays usable while an update downloads
(desktop, iOS); false = Android, where inspection waits for the update.

Common code in the same file that the actuals use: `latestGitHubReleaseTag(repo)` (`:42-55`,
GET `https://api.github.com/repos/<repo>/releases/latest`, headers
`Accept: application/vnd.github+json`, `User-Agent: FetchJet`, regex on `"tag_name"`; null on any
error).

### 2.2 Callers and what they rely on

| Caller                                                                                                     | Uses                                                           | Relies on                                                                                                                                          |
|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `common/presentation/dashboard/DashboardTools.kt:13` (`autoUpdateTools`, at app start, under `toolsMutex`) | `autoUpdateMediaTools(::reportToolProgress)`                   | returns `true` only when something was installed (then the toast `sysMediaToolsUpdated`); never throws (wrapped in `runCatching`)                  |
| `DashboardTools.kt:43-45` (every 2 s while the Add dialog is open and yt-dlp is missing)                   | `isMediaExtractorToolInstalled`, `getMediaExtractorToolStatus` | cheap, no blocking                                                                                                                                 |
| `DashboardTools.kt:54-62` (`refreshToolStatus`)                                                            | all five status functions                                      | cheap (the one `ffmpeg -version` spawn is cached)                                                                                                  |
| `common/presentation/dashboard/DashboardSettingsEvents.kt:170` ("Update tools" button)                     | `downloadMediaExtractorTool`                                   | `false` on failure; progress as `ToolDownloadProgress("yt-dlp" / "FFmpeg" / "deno", fraction or null)` (these three names are shown in the banner) |
| `common/presentation/dashboard/DashboardAddDialogEvents.kt:155-159`                                        | installed + status                                             | same                                                                                                                                               |
| `common/feature/media_extractor/MediaStreamDownloader.kt:213`, `MediaDownloadSteps.kt:435`                 | `isMediaExtractorToolInstalled`                                | decides whether the yt-dlp path is tried                                                                                                           |
| `MediaToolsLock.awaitIdle` (`MediaExtractorToolManager.kt:31-35`)                                          | `toolUpdateSwapsAtomically`, `isMediaExtractorToolInstalled`   | see 2.1                                                                                                                                            |

### 2.3 JVM implementation

Files: `jvm/feature/media_extractor/MediaToolPaths.jvm.kt` (117 lines, not an actual) and
`jvm/feature/media_extractor/MediaExtractorToolManager.jvm.kt` (477 lines).

#### Folder layout (must stay the same: the native app reuses the tools the JVM app installed)

All under `<user.home>/.fetch_jet/` (`MediaToolPaths.jvm.kt:6-14`, created on first use):

| Path                                                                                                                                                                                                                                                     | What                                                                                           | Written by            |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|-----------------------|
| `tools/yt-dlp-dir/yt-dlp.exe` + `tools/yt-dlp-dir/_internal/...`                                                                                                                                                                                         | the unpacked ("onedir") yt-dlp build, the one that is run                                      | `installYtDlpFolder`  |
| `tools/yt-dlp.exe`                                                                                                                                                                                                                                       | old one-file build; used only when `yt-dlp-dir` has no program; deleted after a folder install | older versions        |
| `tools/ffmpeg.exe`                                                                                                                                                                                                                                       | ffmpeg 4.4.1 (ffbinaries `win-64`)                                                             | `installFfmpeg`       |
| `tools/deno.exe`                                                                                                                                                                                                                                         | deno, latest release                                                                           | `installDeno`         |
| `tools/2.1.0-39-libtorrent4j.dll`                                                                                                                                                                                                                        | JNI library of the JVM torrent engine; the native app does not use it and must not delete it   | `TorrentNativeLoader` |
| `tools/yt-dlp_win.zip.download`, `tools/yt-dlp_win.zip.download.tmp`, `tools/yt-dlp-dir.new/`, `tools/yt-dlp-dir.old/`, `tools/ffmpeg.zip`, `tools/ffmpeg.zip.tmp`, `tools/ffmpeg.exe.tmp`, `tools/deno.zip`, `tools/deno.zip.tmp`, `tools/deno.exe.tmp` | transient, removed in `finally`                                                                | install functions     |
| `cache/yt-dlp/`                                                                                                                                                                                                                                          | yt-dlp `--cache-dir`                                                                           | yt-dlp itself         |

#### Binary resolution (`MediaToolPaths.jvm.kt`)

- `ytDlpFolder()` `:18` = `tools/yt-dlp-dir`.
- `ytDlpFolderBinary()` `:20-29`: first of `yt-dlp.exe`, `yt-dlp_macos`, `yt-dlp_linux`,
  `yt-dlp_linux_aarch64`, `yt-dlp_linux_armv7l` inside that folder that `isFile && canExecute()`
  (on Windows `canExecute()` is true for any existing file).
- `getExistingYtDlpBinary()` `:31-53`: the folder binary, else `tools/yt-dlp.exe`, else null.
  On purpose no fallback to the working directory or `PATH`.
- `getExistingFfmpegBinary()` `:56-74`: `tools/ffmpeg.exe` or null.
- `getExistingDenoBinary()` `:77-81`: `tools/deno.exe` or null.
- `getYtDlpCacheDir()` `:83-84`: `<home>/.fetch_jet/cache/yt-dlp`, created.
- `ytDlpRuntimeArgs()` `:86-94`, prefixed to **every** yt-dlp call:
  `--js-runtimes deno:<abs path of deno.exe>` (or `--js-runtimes deno` when deno.exe is missing),
  `--js-runtimes node`, `--cache-dir <cache dir>`, then `NetworkSettings.ytDlpArgs()`
  (`common/core/network/NetworkSettings.kt:138-141`: `--proxy <url>` when the app proxy is on;
  url = `http://user:pass@host:port` or `socks5h://...`, user and password URL-encoded,
  `ProxySettings.url()` `:42-47`).
- `isSystemFfmpegInstalled()` `:96-117`: runs `ffmpeg -version` (bare name, found through the
  process search path), stderr merged, stdout discarded, waits 2 s, kills on timeout; true when
  exit code 0. Result cached for the life of the process.

#### Status functions (`MediaExtractorToolManager.jvm.kt:16-40`)

- `getMediaExtractorToolStatus()`: yt-dlp present and (ffmpeg.exe present or system ffmpeg) ->
  `Strings.sysToolsReadyYtDlpFfmpeg`; yt-dlp only -> `sysToolsReadyYtDlpNoFfmpeg`; else
  `sysToolsNotInstalled` (`LocalizationManager.Strings`).
- `getJsRuntimeStatus()` = `JsRuntimeStatus("deno", getExistingDenoBinary() != null)`.
- `isMediaExtractorToolInstalled()` = `getExistingYtDlpBinary() != null`.
- `isFfmpegToolInstalled()` = `getExistingFfmpegBinary() != null || isSystemFfmpegInstalled()`.
- `getMediaToolsDirectory()` = absolute path of `tools`.
- `toolUpdateSwapsAtomically = true` (`:477`).

#### HTTP helper (`:42-111`)

- `openConnection(url)`: `openAppConnection(URL)`
  (`jvmAndroid/core/network/ProxiedConnection.kt:9-21`: through the app proxy, HTTP or SOCKS,
  plus `Authorization: Basic` when a site login matches the host), connect timeout 15 000 ms,
  read timeout 60 000 ms, follows redirects, `User-Agent: FetchJet`; a status outside 200..299
  throws `IllegalStateException("HTTP <code> for <url>")`.
- `downloadTo(url, dest, onProgress)`: streams into `<dest>.tmp` in 64 KiB blocks, SHA-256 of
  the bytes, hard limit `MAX_TOOL_BYTES` = 400 MiB (`"Download exceeds 400 MB"`), then
  `Files.move(tmp, dest, REPLACE_EXISTING, ATOMIC_MOVE)`; returns the lowercase hex digest.
  Progress: with a Content-Length, `onProgress(pct / 100f)` once per whole percent; without one,
  `onProgress(null)` on every block.

#### yt-dlp (`:113-279`)

- Checksum: `fetchPublishedYtDlpSha256(asset)` reads
  `https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS`, takes the line ending
  in `  <asset>` or ` <asset>`, the text before the first space, lowercased. Null on any error.
- Asset for Windows: `yt-dlp_win.zip` (x64), `yt-dlp_win_arm64.zip` (ARM64 process),
  `yt-dlp_win_x86.zip` (32-bit). The native app is mingwX64 only: always `yt-dlp_win.zip`.
- `installYtDlpFolder(toolsDir, asset, onProgress)` `:217-259`:
    1. no published checksum -> throw `"yt-dlp checksum list not available, try again later"`
       (the old copy stays);
    2. download `https://github.com/yt-dlp/yt-dlp/releases/latest/download/<asset>` to
       `tools/<asset>.download`, progress name `"yt-dlp"`;
    3. digest mismatch -> throw `"yt-dlp checksum mismatch (expected X, got Y)"`;
    4. delete `tools/yt-dlp-dir.new`, unzip everything into it (`unzipAll` `:262-279`: every
       entry, refuses a path that leaves the folder: `"bad path in zip: <name>"`);
    5. `yt-dlp-dir.new/yt-dlp.exe` must exist (`"yt-dlp program missing in <asset>"`);
    6. run it with `--version`, 180 s limit; no version -> `"the new yt-dlp does not start"`;
    7. delete `yt-dlp-dir.old`; move `yt-dlp-dir` -> `yt-dlp-dir.old` (fails on Windows while a
       yt-dlp is running: the step then fails and is retried at the next start); move
       `yt-dlp-dir.new` -> `yt-dlp-dir`; delete `yt-dlp-dir.old`; delete `tools/yt-dlp.exe`;
    8. `finally`: delete the zip and `yt-dlp-dir.new`.
- `installedVersion(binary, timeoutSec = 30)` `:152-163`: runs `<binary> --version`, stderr
  merged, waits, kills on timeout (-> null), reads all output after exit, first match of
  `\d+(\.\d+)+`.
- `sameVersion(installed, latestTag)` `:170-171`: true when either is null, or
  `installed == latestTag.removePrefix("v")`. (yt-dlp tags look like `2026.10.26`, deno tags like
  `v2.5.6`.)
- The one-file path (`installYtDlp` `:189-209`) is only for 32-bit Linux; not needed on Windows.

#### ffmpeg (`:281-386`)

- Windows:
  `https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v4.4.1/ffmpeg-4.4.1-win-64.zip`,
  pinned SHA-256 `d1124593b7453fc54dd90ca3819dc82c22ffa957937f33dd650082f1a495b10e`
  (`ffbinariesSha256["win-64"]`, `:306`). Download to `tools/ffmpeg.zip` (progress name
  `"FFmpeg"`), mismatch -> `"ffmpeg checksum mismatch (expected X, got Y)"`, then `unzipOne`
  (`:126-150`: the first non-directory entry whose plain name is `ffmpeg.exe`, written to
  `tools/ffmpeg.exe.tmp` and moved over `tools/ffmpeg.exe`); a zip without it only logs to
  stderr. `finally`: delete the zip.
- `nativeArmFfmpeg` (`:289-292`) and `isX86OnlyBinary` (`:344-361`) are macOS / Linux ARM only:
  leave them out on Windows (on Windows `isArm` would be false for a mingwX64 process anyway).

#### deno (`:388-420`)

- Asset `deno-x86_64-pc-windows-msvc.zip`, base
  `https://github.com/denoland/deno/releases/latest/download/<asset>`; expected digest = first
  64-hex-digit word of `<base>.sha256sum` (missing ->
  `"deno checksum not available, try again later"`);
  download to `tools/deno.zip` (progress name `"deno"`); mismatch ->
  `"deno checksum mismatch (expected X, got Y)"`; `unzipOne(..., "deno.exe", ...)`, missing ->
  `"<asset> did not contain deno.exe"`; `finally`: delete the zip.

#### The two entry points

- `downloadMediaExtractorTool` (`:422-438`, on `Dispatchers.IO`): `installYtDlp`,
  `installFfmpeg`, then `installDeno` in its own try (a deno failure only logs
  `"deno could not be installed: ..."`). Any other exception -> `false`.
- `autoUpdateMediaTools` (`:440-475`, on `Dispatchers.IO`): reads both tags first (`yt-dlp/yt-dlp`,
  `denoland/deno`), then three independent steps, each in its own try (a failure
  logs `"<name> auto-update failed: ..."`), `changed` = any step installed:
    - yt-dlp: no binary, **or** no folder build yet (migration from the one-file build), **or**
      `!sameVersion(installedVersion(bin), ytDlpTag)`;
    - ffmpeg: no `tools/ffmpeg.exe` and no system ffmpeg;
    - deno: no binary or `!sameVersion(installedVersion(bin), denoTag)`.

Java APIs used: `java.io.File`, `java.net.HttpURLConnection`, `java.net.URI`,
`java.nio.file.Files` / `StandardCopyOption`, `java.security.MessageDigest`,
`java.util.zip.ZipInputStream` / `ZipFile`, `ProcessBuilder`, `java.util.concurrent.TimeUnit`.

### 2.4 iOS reference

`ios/feature/media_extractor/MediaExtractorToolManager.ios.kt:236-280`: Python + yt-dlp run in
the app through a Swift bridge; `toolUpdateSwapsAtomically = true`. Not a model for Windows.

### 2.5 Native plan

Files:

- `mingw/feature/media_extractor/MediaToolPaths.mingw.kt` (about 110 lines): the same functions
  as `MediaToolPaths.jvm.kt` returning `okio.Path?` (or `String?`), only the `.exe` names.
  `isSystemFfmpegInstalled` through
  `WinProcess.run(listOf("ffmpeg", "-version"), timeoutMs = 2000, output = DISCARD)`.
  Hardening that keeps behaviour: resolve the bare `ffmpeg` with
  `SearchPathW(<value of PATH>, "ffmpeg.exe")` and start the absolute path, so a file planted in
  the working directory is never run (the JVM code has a comment about exactly this for yt-dlp).
- `mingw/feature/media_extractor/MediaExtractorToolManager.mingw.kt` (about 380 lines): the
  install logic as above, with:
    - HTTP: one
      `HttpClient(WinHttp) { install(HttpTimeout) { connectTimeoutMillis = 15_000; socketTimeoutMillis = 60_000; requestTimeoutMillis = INFINITE }; useNetworkSettings() }`
      per install run, `prepareGet(url) { header("User-Agent", "FetchJet") }.execute { ... }`
      streaming `bodyAsChannel()` into `HashingSink.sha256(FileSystem.SYSTEM.sink(tmp))`
      (`ToolHttp.downloadTo`, section 9.5). GitHub release links redirect to another host: ktor's
      `HttpRedirect` follows them.
    - zip: `ZipReader.unzipAll` / `ZipReader.unzipOne` (section 9.4).
    - swap: `FileSystem.SYSTEM.atomicMove` (folders too: same volume).
    - version: `WinProcess.run(listOf(bin, "--version"), timeoutMs)`.
    - `suspend` + cancellable: use `withContext(Dispatchers.IO)`; check `ensureActive()` in the
      download loop (the JVM code cannot be cancelled mid-download; cancelling is an improvement
      and safe because every step works on temp names).

Estimated size 490 lines for both files. Risk **medium**: (a) the zip reader is new code and the
yt-dlp zip has several hundred entries (Python runtime DLLs); (b) Windows Defender scans the
freshly unpacked files, the first `--version` can take tens of seconds (that is why the limit is
180 s); (c) the folder swap fails while any yt-dlp from that folder is running: keep the
"fail this step, retry next start" behaviour; (d) proxy with login through WinHttp depends on
part A.

---

## 3. Running yt-dlp: `runYtDlpJson`, `resolvePlatformStream`, `inspectPlatformPlaylist`,
`lastMediaInspectionError`

### 3.1 Expects

`common/feature/media_extractor/DirectMediaResolver.kt:140-145`:

```kotlin
/** Runs `yt-dlp -j -f <selector> <url>` and returns its JSON line, or null when the tool failed. */
expect suspend fun runYtDlpJson(
    url: String,
    selector: String,
    headers: Map<String, String>
): String?
```

`common/feature/media_extractor/MediaStreamExtractorPlatform.kt:6-9`:

```kotlin
expect suspend fun resolvePlatformStream(url: String): String?

/** The reason the last inspection failed, as the media engine printed it; null when unknown. */
expect fun lastMediaInspectionError(): String?
```

`common/feature/media_extractor/Playlist.kt:29-36`:

```kotlin
/**
 * The videos of a playlist / channel link, read with `yt-dlp --flat-playlist -J` (titles and
 * links only, no formats: fast even for hundreds of videos). null = not a playlist.
 */
expect suspend fun inspectPlatformPlaylist(
    url: String,
    headers: Map<String, String>
): MediaPlaylist?
```

### 3.2 Callers

- `runYtDlpJson`: `DirectMediaResolver.resolve` (`DirectMediaResolver.kt:47`, `:65`). The
  result goes to `DirectMediaResolver.parse`, which takes the first trimmed line that starts with
  `{` and ends with `}`; so warnings mixed into the output are tolerated, but the JSON must be on
  one line. null = "use yt-dlp's own downloader".
- `resolvePlatformStream`: `MediaYouTubeResolvers.kt:83` and the JVM `inspectPlatformQualities`
  fallback. The caller treats the string as a URL (yt-dlp `-g` may print two lines, video and
  audio; that is passed on as it is).
- `inspectPlatformPlaylist`: `QueueManagerRepository.kt:490`; result of `parsePlaylistJson`
  (common, `Playlist.kt:96`), null = not a playlist.
- `lastMediaInspectionError`: `DashboardViewModel.kt:775`: shown in the Add dialog when no
  quality was found, after `.removePrefix("ERROR:").trim()`.

### 3.3 JVM implementation (`jvm/feature/media_extractor/MediaStreamExtractorPlatform.jvm.kt`)

`runYtDlp(args, timeoutSec = 25)` (`:28-83`), the one place that starts yt-dlp for anything but
a download:

1. Executable: `getExistingYtDlpBinary()`; null -> return null.
2. Command = `[absolute path] + args`. Started **directly**, never through `cmd.exe` (comment
   `:32-33`: `&`, `|`, `^`, `>` in a URL would otherwise be a command injection).
3. `YtDlpSecretArgs.split(command)` (3.4) may move secret arguments into a config file.
4. `ProcessBuilder(command).redirectErrorStream(true).start()`: stderr merged into stdout, no
   environment change, working directory inherited, stdin inherited.
5. A reader thread appends every line + `\n` to a `StringBuilder` (no size limit).
6. `process.waitFor(timeoutSec)`. On timeout: `destroyForcibly()` (the process only, not its
   children), `lastYtDlpError = "yt-dlp took longer than <N> s"`, log
   `"yt-dlp timed out after <N> s: <first two-character flag>"`, return null.
7. Else: log (debug) `"yt-dlp finished in <ms> ms"`, join the reader up to 2 s,
   `output = text.trim()`.
8. Exit code 0 and output not blank -> return output.
9. Otherwise `err` = last line containing `ERROR` (any case), or the last non-blank line;
   `lastYtDlpError = err?.take(400)`; log `"yt-dlp failed (exit <code>): <err or 'no output'>"`;
   return null.
10. Any exception: `lastYtDlpError = e.message`, log `"yt-dlp could not run: ..."`, null.

`lastYtDlpError` (`:24`) is a public `@Volatile var`; it is never cleared after a success.
`lastMediaInspectionError()` (`:26`) returns it.

Exact argument lists (R = `ytDlpRuntimeArgs()`, H = `ytDlpHeaderArgs(headers, url)` from
`common/.../MediaStreamExtractorPlatform.kt:16-26`: `--add-headers Key:Value` per header, the
Cookie dropped for YouTube hosts):

| Function                                                                | Arguments                                                                                                                                                            | Timeout |
|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `resolvePlatformStream(url)` `:8-20`                                    | R, `-g`, `--no-warnings`, `--no-playlist`, `--`, url                                                                                                                 | 25 s    |
| `inspectPlatformPlaylist(url, headers)` `:85-101`                       | R, `--flat-playlist`, `-J`, `--no-warnings`, `--yes-playlist`, H(listUrl), `--`, listUrl; listUrl = `normalizePlaylistUrl(url)`; returns null when yt-dlp is missing | 85 s    |
| `runYtDlpJson(url, selector, headers)` `:104-117` (on `Dispatchers.IO`) | R, `-j`, `--no-warnings`, `--no-playlist`, `-f`, selector, H(url), `--`, url; null when yt-dlp is missing                                                            | 40 s    |
| `inspectPlatformQualities` (section 4)                                  | R, `-j`, `--no-warnings`, `--no-playlist`, H(url), `--`, url                                                                                                         | 90 s    |

`resolvePlatformStream`, `inspectPlatformPlaylist` and `inspectPlatformQualities` call the
blocking `runYtDlp` on the caller's thread (no `withContext`); they cannot be cancelled while
yt-dlp runs.

### 3.4 `YtDlpSecretArgs` (
`jvm/feature/media_extractor/YtDlpSecretArgs.jvm.kt`, 82 lines, not an actual)

A command line is visible to every user (Task Manager), so secrets go to a file:

- Walk the command; stop at the first `--` after index 0 (everything after it is kept as it is).
- A pair `(arg, value)` is secret when (`isSecret` `:59-65`): `--proxy` whose value has a
  scheme part before the first `/` and contains `@`; or `--add-headers` / `--add-header` whose
  header name (text before the first `:`, trimmed, lowercased) is `cookie`, `authorization` or
  `proxy-authorization`.
- No secret -> the command is unchanged. Otherwise write a file with one line per pair:
  `<arg> '<value>'` where `'` inside the value becomes `'"'"'` (yt-dlp reads config files with
  POSIX shlex rules), each line ended by `\n`; the command becomes
  `[exe, "--config-locations", <file>] + rest`.
- File: `Files.createTempFile(<PathResolver().getTempDirectory()>, "fetchjet-ytdlp-", ".conf")`
  (on Windows without special permissions: the user's temp folder is already private).
- `deleteWhenDone(process)`: delete the file when the process exits; `deleteNow()` when the
  start failed. If the file cannot be written the original command is used.

### 3.5 iOS reference

`ios/feature/media_extractor/MediaStreamExtractorPlatform.ios.kt:25-90`: the same argument
lists handed to the in-process Python bridge; `lastMediaInspectionError()` returns null (`:74`).

### 3.6 Native plan

File `mingw/feature/media_extractor/MediaStreamExtractorPlatform.mingw.kt` (about 130 lines) and
`mingw/feature/media_extractor/YtDlpSecretArgs.mingw.kt` (about 80 lines).

- `internal suspend fun runYtDlp(args: List<String>, timeoutSec: Long = 25): String?` built on
  `WinProcess.run(...)` inside `withContext(Dispatchers.IO)`, same texts for `lastYtDlpError`
  and the log lines.
- Use the Job object of `WinProcess` so a timeout kills the whole tree (the old one-file
  `yt-dlp.exe` is a bootloader that starts a second process; the JVM code leaves that child
  alive on timeout).
- Set `PYTHONIOENCODING=utf-8` and `PYTHONUTF8=1` for every yt-dlp start and decode the pipe as
  UTF-8. The JVM code sets them only for downloads; for inspection the JSON is ASCII-escaped so
  nothing changes, but `ERROR:` lines with non-Latin text then arrive readable.
- Secret file: `<temp>/fetchjet-ytdlp-<16 hex from Secrets.randomHex(8)>.conf`, written with
  okio, deleted in a `finally` after the process ended.
- `actual fun lastMediaInspectionError()` reads a `@kotlin.concurrent.Volatile` top-level var.

Risk **medium**: all in `WinProcess` (argument quoting, pipe handling, no console window). See
section 9.1 for the rules.

---

## 4. `inspectPlatformQualities`

**Expect** (`common/feature/media_extractor/MediaStreamExtractorPlatform.kt:28-35`):

```kotlin
/**
 * [requestHeaders]: Cookie / Referer / User-Agent the browser extension captured for the page.
 * Passed to yt-dlp (--add-headers) so sites that need a login (Udemy, Vimeo private ...) work.
 */
expect suspend fun inspectPlatformQualities(
    url: String,
    requestHeaders: Map<String, String> = emptyMap()
): List<MediaQualityEntry>
```

**Callers:** `common/feature/media_extractor/MediaInspectSteps.kt:59` and `:76`
(`inspectPlatformWithCookieRetry`): an exception is caught and counts as an empty list; a
"good" result has a label matching `\b\d{3,4}p\b|\b4K\b`; when the result with browser headers
is weak it is called again with no headers and the bigger list wins. So the labels below are
compared by common code and must not change.

**JVM actual** (`jvm/feature/media_extractor/MediaInspect.jvm.kt:109-447`; helpers in the same
file):

1. yt-dlp missing ->
   `throw Exception("yt-dlp tool is not found. Please click 'Download Tools' in Settings.")`.
2. `runYtDlp(R + ["-j", "--no-warnings", "--no-playlist"] + H + ["--", url], 90)`; null ->
   `emptyList()`.
3. `MiniJson.parse(output)` as a map (null on failure);
   `MediaSubtitleStore.record(url, parseSubtitlesFromRoot(root))` (both common).
4. Title: `root["title"]` or regex `"title"\s*:\s*"([^"]+)"`, then `decodeUnicodeEscapes`
   (`:449-469`: `\uXXXX` and `_uXXXX` -> the character).
5. Duration: `root["duration"]` (long) or regex `"duration"\s*:\s*([0-9.]+)`; when 0 ->
   `probeMediaDurationWithFfmpeg(url)`.
6. For every entry of `formats` (skip a blank `format_id`):
    - `directUrl` = `url` or `manifest_url` or `""`; `ext` default `mp4`; `height`, `vcodec`,
      `acodec` (default `none`), `resolution`, `format_note`;
    - size = `filesize` or `filesize_approx`; when missing: -1 for HLS (`protocol` contains
      `m3u8`, or equals `mhtml`, or the URL contains `.m3u8`), else a HEAD probe
      `probeContentLength(directUrl or url)`, cached per URL for this call (a bitrate is
      computed at `:171-188` but never used);
    - audio only when (`vcodec` is `none` or `resolution` contains `audio only` or `format_note`
      contains `audio`) and (`acodec` is not `none` or ext in m4a, mp3, opus, aac, flac, wav,
      ogg): one entry per ext, label `Audio Only (<EXT>)`;
    - video: height from the field, else from `(\d+)p` in the format id or resolution, else from
      `(\d+)x(\d+)`; snapped to 2160 / 1440 / 1080 / 720 / 540 / 480 / 360 / 240 / 144 by the
      ranges at `:247-258`; label `4K (EXT)`, `1440p (2K - EXT)`, `1080p (Full HD - EXT)`,
      `720p (HD - EXT)`, `540p (EXT)`, `480p (SD - EXT)`, `360p (SD - EXT)`, `240p (Low - EXT)`,
      `144p (Low - EXT)`, `<h>p (EXT)`; with no height: format id `hd` or note containing `hd` ->
      `HD Quality (Direct EXT)` (rank 725), `sd` -> `SD Quality (Direct EXT)` (rank 365);
    - one entry per (label, ext); the label is stored through `withQualityTag(label)`
      (`common/domain/model`); an existing HLS entry is replaced by a direct mp4 of the same
      label (`:328-350`).
7. Empty list: `resolvePlatformStream(url)` -> one entry `Best Quality (yt-dlp Native - MP4)`;
   else, when the URL's extension is a known media type, one `Audio Only (EXT)` /
   `Direct Media (EXT)` entry with the probed size and duration.
8. Sort descending by the rank table at `:415-435`, then set `index`, `isSelected = (i == 0)`,
   the title and the duration on every entry.

Helpers in the same file (not actuals, must be ported):

- `safeEncodeUrl` (`:12-28`): keeps chars 33..126, space -> `%20`, the rest percent-encoded as
  UTF-8 (uppercase hex).
- `probeContentLength(url)` (`:30-47`, public): `HEAD` through `openAppConnection`, connect and
  read timeout 4000 ms, redirects followed, `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)`;
  `Content-Length` when > 0, else -1; -1 on any error.
- `parseDurationFromFfmpegOutput` (`:49-56`): regex `Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)`,
  seconds rounded.
- `probeMediaDurationWithFfmpeg(url)` (`:58-107`): command
  `[tools/ffmpeg.exe or "ffmpeg", "-hide_banner", "-i", safeEncodeUrl(url)]`, stderr merged,
  12 s limit then `destroyForcibly()`, reader joined 1 s, exit code ignored (ffmpeg exits 1:
  no output file), result parsed with the regex; 0 when there is no ffmpeg. This ffmpeg does
  not get the app's proxy.

**iOS reference:** `ios/feature/media_extractor/MediaInspect.ios.kt:98`: the same parser with
the Python bridge as the source. The format loop exists three times already (jvm, android, ios).

**Native plan:** `mingw/feature/media_extractor/MediaInspect.mingw.kt` (about 430 lines): a
copy of the JVM file with `runYtDlp` / `WinProcess` and:

- `probeContentLength` as a `suspend` ktor `HEAD` (WinHttp client with `useNetworkSettings()`,
  `HttpTimeout` 4000 / 4000) or `runBlocking` around it to keep the non-suspend local function
  shape; the simplest is to make `getCachedContentLength` suspend.
- `String.format("%%%02X", b)` -> manual uppercase hex.
- Wrap the whole body in `withContext(Dispatchers.IO)`.

Risk **medium**: long but mechanical; one sizeable behaviour point is that every format without
a size costs one HEAD request in sequence (same as the JVM app).

---

## 5. `downloadMediaWithPlatformTool`

**Expect** (`common/feature/media_extractor/MediaStreamExtractorPlatform.kt:59-79`):

```kotlin
expect suspend fun downloadMediaWithPlatformTool(
    url: String,
    destinationDir: String,
    fileName: String,
    formatId: String?,
    clipStart: String? = null,
    clipEnd: String? = null,
    /**
     * true: download the whole video at full speed and cut it locally afterwards (yt-dlp
     * --remove-chapters with time ranges, stream copy). false: --download-sections, which only
     * fetches the part but through ffmpeg on one connection that YouTube throttles.
     */
    clipByCutting: Boolean = false,
    speedLimitBytesPerSec: Long = 0L,
    speedLimitProvider: (() -> Long)? = null,
    onProgress: (Float, Long, Long, Long, String?) -> Unit,
    /** After the download: (step label, fraction 0..1 or -1 when unknown). See PostProcessTracker. */
    onPostProcess: ((String, Float) -> Unit)? = null,
    /** Cookie / Referer / User-Agent from the browser extension (see [inspectPlatformQualities]). */
    requestHeaders: Map<String, String> = emptyMap()
): Boolean
```

**Caller:** `common/feature/media_extractor/MediaDownloadSteps.kt:111`
(`MediaStreamDownloader.downloadWithYtDlp`). It relies on:

- `onProgress(fraction 0..1, downloadedBytes, totalBytes, speedBytesPerSec, stepText)`;
- the English step texts: `MultiStreamProgress.kt:42-44` looks for `Step 1 of 3`,
  `Step 2 of 3`, `Step 3 of 3`, and `localizeStep` (`common/core/i18n/start/StepText.kt:7`)
  translates the whole text when shown;
- cancellation (pause / remove) stopping yt-dlp and its ffmpeg quickly;
- `MediaToolErrors.last(destinationDir)` holding the last `ERROR` line after a failure;
- the output being in `destinationDir` under the template described below (`findFinishedFileInCache`
  in `MediaDownloadSteps.kt` then finds it).

### 5.1 JVM actual (`jvm/feature/media_extractor/MediaYtDlpDownload.jvm.kt`, 558 lines)

Runs on `Dispatchers.IO`. yt-dlp missing -> `false`.

**Local throttling proxy** (`:25-29`): `LocalThrottlingProxy(speedLimitProvider)` is created
for every download; a failure to create it gives `null`.

**Command, in order** (`:31-163`):

1. absolute yt-dlp path;
2. `ytDlpRuntimeArgs()` (2.3);
3. `ytDlpHeaderArgs(requestHeaders, url)`;
4. with the local proxy: `--proxy http://127.0.0.1:<port>` and
   `--downloader-args "ffmpeg_i:-http_proxy http://127.0.0.1:<port>"`; without it and with a
   limit: `--limit-rate <N>M | <N.N>M | <N>K | <N>` (`:42-56`);
5. format (`:58-99`), `isAudio` = the file name ends in .m4a .mp3 .opus .aac .flac .wav .ogg:
    - no ffmpeg at all: `-f "<id>/bestaudio/best"` (audio), `-f "<id>/best"`, or `-f best`;
    - with ffmpeg: audio `-f "<id>/bestaudio/best"`; video
      `-f "<id>+bestaudio/<id>/best" --merge-output-format mp4`; no id
      `-f "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best" --merge-output-format mp4`;
      plus `--ffmpeg-location <tools dir>` when `tools/ffmpeg.exe` exists;
6. `-c`, `--no-mtime`, `--geo-bypass`, `--no-playlist`;
7. when the URL path (before `?`) ends in `.m3u8` or `.mpd`:
   `--merge-output-format mp4 --remux-video mp4 --hls-prefer-ffmpeg`;
8. clip (`:116-139`), `s` = clipStart or `0`, `e` = clipEnd or `inf`:
    - `clipByCutting` and `ensureCutPluginDir(parentDirOf(destinationDir))` worked:
      `--plugin-dirs <dir> --use-postprocessor <cutPluginArg(parseTimeToSeconds(s), endSecOrNull)>`
      (`common/feature/media_extractor/CutPlugin.kt`; the plugin file is
      `<dir>/fetchjet/yt_dlp_plugins/postprocessor/fetchjetcut.py` with
      `<dir>` = `<parent of the task folder>/fetchjet-ytdlp-plugins`);
    - otherwise `--download-sections "*<s>-<e>"`;
9. `-o <template>` (`:140-156`): `isScript` = the file name ends in
   php|asp|aspx|jsp|html|htm|cgi|do|action;
    - audio with a usable name: `<destinationDir>\<name without extension>` with `%` doubled,
      then `.%(ext)s`;
    - other usable name: `<destinationDir>\<fileName>` with `%` doubled;
    - else `<destinationDir with % doubled>\%(title)s.%(ext)s`;
10. `--newline`;
11.
`--progress-template "download:[download] %(progress._default_template)s FJBYTES=%(progress.downloaded_bytes)s"`;
12. `--`, url.

**Process** (`:165-195`): `YtDlpSecretArgs.split(command)`, stderr merged, environment added:
`PYTHONIOENCODING=utf-8`, `PYTHONUTF8=1`, and with the proxy `http_proxy`, `https_proxy`,
`HTTP_PROXY`, `HTTPS_PROXY`, `all_proxy`, `ALL_PROXY` = `http://127.0.0.1:<port>`. A start
failure deletes the secret file, closes the proxy and returns `false`.

**Phases** (`:197-256`):

- `isClipping` = a clip without `clipByCutting` (section mode: progress comes from ffmpeg).
- `isMergedFormat` = the value after `-f` contains `+`. `totalExpectedPhases` = 1 when clipping
  or not merged, else 3.
- Initial step text: clipping -> `Clipping Video (00:00:00 / <duration>)` (duration through
  `formatDurationSeconds`, which prints `MM:SS` or `HH:MM:SS`; so the literal `00:00:00` start
  is as written in the code) or `Clipping Video...` when the duration is unknown; merged ->
  `Downloading Video (Step 1 of 3)`; else `Downloading Media`.
- `MediaToolErrors.clear(destinationDir)`; `PostProcessTracker(destinationDir)`.

**Ticker thread** (`:258-283`, only with the proxy): every 300 ms, when the proxy has seen
bytes, `onProgress(lastPercent, lastDownloaded, lastTotal, proxy.speed, phase)`.

**Reader thread** (`:289-489`): reads lines as UTF-8; for each line: strip ANSI escapes
(`\x1B\[[0-?]*[ -/]*[@-~]`), trim, skip blank, then `postProcess.onLine(line)` and
`MediaToolErrors.record(destinationDir, line)`, then:

| Condition                                                                                                           | Effect                                                                                                                                                                                                                                                               |
|---------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| not clipping, line contains `Downloading` and `format(s):`                                                          | text after `format(s):` has no `+` -> 1 phase, `Downloading Media`; else 3 phases, `Downloading Video (Step 1 of 3)`                                                                                                                                                 |
| not clipping, line contains `[download] Destination:` or `has already been downloaded`                              | `destinationCount++`; from the second file on reset total / downloaded / percent; 3 phases: first file `Downloading Video (Step 1 of 3)`, later `Downloading Audio (Step 2 of 3)`; else `Downloading Media`; `onProgress(0f, ...)`                                   |
| not clipping, line contains `[Merger]`, `Merging formats` or `[ffmpeg] Merging`                                     | `isMerging = true`, `Merging Audio & Video (Step 3 of 3)`, `onProgress(1f, total, total, 0, phase)`                                                                                                                                                                  |
| not merging, matches `(?:size\|Lsize)=\s*([0-9.]+)\s*([a-zA-Z]+).*?time=([0-9:.]+)` (ffmpeg)                        | downloaded = size x `ytDlpUnitMultiplier(unit, 1.0)`; percent = time / clip duration, 0..0.99; total estimated once when percent >= 0.03, time >= 2 s (downloaded / percent); speed = proxy speed; text `Clipping Video (<cur> / <tot>)` or `Clipping Video (<cur>)` |
| else matches `\[download\]\s+([0-9.]+)%\s+of\s+(?:~\s*)?([0-9.]+\s*[a-zA-Z]+)(?:.*?\bat\s+([0-9.]+\s*[a-zA-Z/]+))?` | total from the size text; downloaded from `FJBYTES=(\d+)` or total x percent; speed: proxy speed when the proxy has seen bytes, else the parsed speed (`b/s` -> x1, else unit table, default x1024); `onProgress(percent / 100, ...)`                                |
| else matches `\[download\]\s+([0-9.]+)%`                                                                            | percent only                                                                                                                                                                                                                                                         |
| else contains `[download]`                                                                                          | `onProgress(0f, ...)` with the last values                                                                                                                                                                                                                           |

`BufferedReader.readLine` ends a line at `\n`, `\r` or `\r\n`. ffmpeg's progress lines (section
clips) end with `\r` only: the native reader must split on `\r` too.

**Wait and cancel** (`:491-525`):
`trackPostProcessing(postProcess, onPostProcess) { while (process.isAlive) { ensureActive(); process.waitFor(150 ms) } }`
(common: reports the post-processing step every 500 ms). Result = exit code 0 and not
cancelled. `finally` (also on cancel): set the cancelled flag, interrupt the ticker, close the
proxy, `process.descendants().forEach { destroyForcibly() }`, `process.destroyForcibly()`, wait
500 ms.

Local helpers: `parseTimeToSeconds` (`:528-544`: `H:M:S`, `M:S` or seconds, doubles),
`formatDurationSeconds` (`:546-558`).

### 5.2 `LocalThrottlingProxy` (
`jvm/feature/media_extractor/MediaThrottlingProxy.jvm.kt`, 300 lines, not an actual)

A plain HTTP proxy on `127.0.0.1:<random port>` (`ServerSocket(0, 50, 127.0.0.1)`), one thread
per connection plus one upload thread:

- reads the request head (up to 8192 bytes, until `\r\n\r\n`), ISO-8859-1;
- `CONNECT host:port` (default 443): opens a direct TCP socket to the host, answers
  `HTTP/1.1 200 Connection Established\r\n\r\n`, then relays both ways in 16 384-byte blocks;
- any other method: host from the `Host:` header (or from the absolute URL), default port 80,
  forwards the bytes already read, then relays;
- download direction: `totalDownloadedBytes += n`, then `BlockingRateLimiter.acquire(n)`
  (token bucket: bucket size `max(16 KiB, 20 % of the limit)`, refilled by elapsed time, a
  changed limit resets the bucket, limit <= 0 = no wait), then write + flush;
- `TCP_NODELAY` on both sockets;
- `getInstantaneousDownloadSpeedBytesPerSec()`: recomputed at most every 350 ms, smoothed
  `0.7 x new + 0.3 x old`; 0 after 1200 ms without bytes;
- `close()`: closes the server socket and every open socket.

Purpose: a live speed limit (the user can change it while yt-dlp runs) and a real speed number
for fragmented downloads.

**Observed JVM behaviour to decide on (not changed here):** the proxy opens its outgoing
sockets directly (`java.net.Socket(host, port)`, no `ProxySelector` is installed anywhere in
the app). `ytDlpRuntimeArgs()` adds `--proxy <app proxy>` first and the download adds
`--proxy http://127.0.0.1:<port>` after it; yt-dlp keeps the last one. So a yt-dlp *download*
does not go through the app's proxy on the JVM desktop (inspection and the own-engine download
do). See Open questions.

### 5.3 iOS reference

`ios/feature/media_extractor/MediaYtDlpDownload.ios.kt:60`: the same line parsing fed by the
Python bridge. Useful only to confirm that the parser has no JVM dependency.

### 5.4 Native plan

- `mingw/feature/media_extractor/MediaYtDlpDownload.mingw.kt` (about 520 lines): port of 5.1.
    - `WinProcess.start(command, env = mapOf(...), mergeStderr = true)`, Job object with
      kill-on-close.
    - Reader: a coroutine on `Dispatchers.IO` calling `process.readLine()` (UTF-8, splits on
      `\n`, `\r`, `\r\n`).
    - Ticker: a coroutine with `delay(300)`.
    - Waiting: `while (process.isAlive) { ensureActive(); process.waitFor(150) }` inside
      `trackPostProcessing`.
    - `finally`: `process.killTree()`, `proxy.close()`, delete the secret file.
    - State shared between the reader and the ticker: `@kotlin.concurrent.Volatile` fields, as
      the JVM `ProgressHolder`.
- `mingw/feature/media_extractor/MediaThrottlingProxy.mingw.kt` (about 280 lines): port of 5.2
  over `WinSock` (section 9.2): `tcpListen("127.0.0.1", 0)`, one coroutine on `Dispatchers.IO`
  per direction per connection with blocking `recv` / `send`, `FjLock` around the limiter state,
  `kotlin.concurrent.AtomicLong` counters. `close()` closes the listen socket and all sockets,
  which ends the blocked `recv` calls.

Risk **high** (the highest of the media part): long parser with many text rules, two helper
threads, a relay server, and correct tree kill on pause. The texts and regular expressions must
be copied exactly.

---

## 6. `runLocalFfmpeg`

**Expect** (`common/feature/media_extractor/HlsDownloader.kt:494-495`):

```kotlin
/** Runs the platform's ffmpeg on local files only (no network). true = exit code 0. */
expect suspend fun runLocalFfmpeg(args: List<String>): Boolean
```

**Callers:** `HlsDownloader.kt:443`, `:456` (join segments), `MediaAudioConvert.kt:50`,
`MediaDownloadSteps.kt:351`, `:389` (subtitles), `MediaOwnEngine.kt:164`, `:178`, `:215`,
`:244` (merge video + audio, copy then re-encode fallback, cut). They pass the full argument
list after the program name and only look at the Boolean (plus the output file).

**JVM actual** (`jvm/feature/media_extractor/LocalFfmpeg.jvm.kt:8-36`, on `Dispatchers.IO`):
program = `tools/ffmpeg.exe`, else `"ffmpeg"` when `isSystemFfmpegInstalled()`, else return
`false`. Stderr merged; a daemon thread keeps the first ~4000 characters of output; waits up
to 30 minutes, then `destroyForcibly()` and `false`; joins the reader 2 s; a non-zero exit logs
`"ffmpeg (local join) exit <code>: <last 600 chars>"`; an exception logs
`"ffmpeg (local join) could not run: <message>"` and returns `false`. Not cancellable: a paused
task leaves ffmpeg running until it ends.

**iOS actual** (`ios/feature/media_extractor/LocalFfmpeg.ios.kt`):
`bridge.executeFfmpeg(args, null) == 0`.

**Native plan:** `mingw/feature/media_extractor/LocalFfmpeg.mingw.kt` (about 45 lines):
`WinProcess.start`, drain the pipe in a coroutine (keep the first 4000 chars), poll
`waitFor(150)` with `ensureActive()` up to 30 minutes, `killTree()` on timeout or cancellation
(cancellation is an improvement over the JVM code). Risk low.

---

## 7. Torrents

### 7.1 `createTorrentBackend`

**Expect** (`common/feature/torrent_engine/TorrentBackend.kt:78`):
`expect fun createTorrentBackend(): TorrentBackend`
(no KDoc; the interface `TorrentBackend` is at `:32-76` with KDoc on each member:
`isSupported`, `engineStatus()`, `installEngine()`, `fetchMetadata(magnetUri, torrentBytes)`,
`add(taskId, torrentBytes, savePath, selected, priorities, disableUpload, downloadLimitBytesPerSec, uploadLimitBytesPerSec)`,
`pause`, `resume`, `remove(taskId, deleteFiles)`, `setFileSelection`, `setLimits`,
`progress(taskId): TorrentProgress?`, `shutdown()`; data classes `TorrentMetadata` `:6-13` and
`TorrentProgress` `:16-30`).

**Caller:** `common/feature/torrent_engine/TorrentDownloader.kt:48` (default constructor
argument; one `TorrentDownloader` in `QueueManagerRepository.kt:86`). Behaviour it relies on:

- `isSupported == false` -> `fetchAndParseTorrent` and `start` fail with
  `"BitTorrent downloads are not available on this platform"` (`:82`, `:188`): a stub backend
  is a valid first step.
- `fetchMetadata` throws with a readable message; the magnet message
  `"Could not fetch torrent metadata for this magnet link (no peers answered within 60 s). Check the link or your network."`
  is shown to the user.
- `add` is called again for a task that is already in the session (resume): it must re-apply
  the priorities and resume instead of adding twice.
- `progress()` is polled (`:311`); `error != null` fails the task; `stateText` is shown as
  `"<stateText> · <peers> peers (<seeds> seeds)"` (`:363`) and translated by `localizeStep`, so
  the texts must be exactly `Checking files`, `Checking resume data`, `Fetching metadata`,
  `Downloading`, `Finished`, `Seeding`, `Connecting`.
- `fileProgress` = downloaded bytes per file, index = file index in the torrent (pad files
  included in the indexing, they are only left out of the metadata list).
- `engineStatus()` / `installEngine()` are used by Settings (`DashboardTools.kt:63-66`,
  `DashboardSettingsEvents.kt:175-185`).

**JVM actual** (`jvmAndroid/feature/torrent_engine/TorrentBackend.jvmAndroid.kt:6-14`): a lazy
singleton `Libtorrent4jBackend(tempDir = <temp>/torrent-metadata, toolsDir = <settings>/tools)`.

**`Libtorrent4jBackend`** (`jvmAndroid/feature/torrent_engine/Libtorrent4jBackend.kt`, 505
lines; library `org.libtorrent4j` 2.1.0-39 = libtorrent 2.1):

- Session (`:54-132`, created on first use): `SessionManager(false).start()`, then one
  settings pack:

  | Setting | Value |
    |---|---|
  | `enable_dht`, `enable_lsd`, `enable_upnp`, `enable_natpmp` | true |
  | `dht_bootstrap_nodes` | `dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.aelitis.com:6881` |
  | `listen_interfaces` | `0.0.0.0:<p>,[::]:<p>` with p = 20000 + (nanoTime % 40000) |
  | `download_rate_limit`, `upload_rate_limit` | 0 |
  | `connections_limit` | 800 |
  | `connection_speed` | 250 |
  | `torrent_connect_boost` | 100 |
  | `max_peerlist_size` | 4000 |
  | `peer_connect_timeout` | 10 |
  | `request_timeout` | 20 |
  | `active_downloads` / `active_seeds` / `active_limit` | 10 / 5 / 20 |
  | `unchoke_slots_limit` | 16 |
  | `send_buffer_watermark` / `send_buffer_low_watermark` | 3 MiB / 512 KiB |
  | `max_queued_disk_bytes` | 16 MiB |
  | `aio_threads` | 8 |
  | `mixed_mode_algorithm` | `prefer_tcp` |
  | `enable_outgoing_utp`, `enable_incoming_utp` | true |
  | `announce_to_all_trackers`, `announce_to_all_tiers`, `prefer_udp_trackers` | true |
  | `max_web_seed_connections` / `urlseed_wait_retry` / `urlseed_pipeline_size` | 8 / 10 / 10 |

- Proxy (`applyProxy` `:137-164`, re-applied on every change of `NetworkSettings.proxy`):
  `proxy_type` = none / socks5 / socks5_pw / http / http_pw, `proxy_hostname`, `proxy_port`,
  `proxy_username`, `proxy_password`, `proxy_hostnames`, `proxy_peer_connections`,
  `proxy_tracker_connections` = true; with an HTTP proxy `enable_dht`, `enable_lsd`,
  `enable_outgoing_utp`, `enable_incoming_utp` = false (they would go around it), else true.
- `fetchMetadata` (`:195-219`): torrent bytes as given, or
  `session.fetchMagnet(withPublicTrackers(magnet), 60, tempDir)` (null -> the message above);
  neither -> `IllegalArgumentException("A magnet link or a .torrent file is required")`.
  `withPublicTrackers` (`:170-182`) appends `&tr=<URL-encoded>` for the 7 public trackers
  (`:495-503`) only when the magnet has no `tr=` at all. `toMetadata` (`:221-248`): pad files
  skipped; each path segment through `sanitizePathSegment` (common,
  `BencodeParser.kt:215`); name sanitized, default `Torrent_Download`; info hash as hex.
- `add` (`:252-310`): creates the save folder; file priorities from `selected` / `priorities`
  (`:328-342`: not selected -> IGNORE (0); > 0 -> TOP_PRIORITY (7); < 0 -> LOW (1); else
  DEFAULT (4)); already known and valid -> `prioritizeFiles` + `resume`; else
  `session.download(ti, dir, null, prio, null, torrent_flags_t())`, wait up to 5 s (50 x
  100 ms) for the handle (`"libtorrent did not accept the torrent"`), clear `AUTO_MANAGED`,
  re-apply priorities, add the torrent's own trackers and web seeds (`trackersAndWebSeeds`
  `:313-326`: `load_torrent_parsed`, because libtorrent 2.1 keeps them
  out of `torrent_info`), add the public trackers that are not there yet,
  `forceReannounce()`, `forceDHTAnnounce()`, `setLimits`, `resume()`.
- `setLimits` (`:370-381`): download limit clamped to int, 0 = unlimited; upload = 32 KiB/s
  when `disableUpload`, else the given value.
- `progress` (`:383-420`): `totalWantedDone`, `totalWanted`, payload rates, `numPeers`,
  `numSeeds`, `isFinished`, `isSeeding`, paused flag, `hasMetadata`, `fileProgress()`, state
  text as above, `errorCode().message` when it is an error. Verbose builds also log one line
  per torrent every 15 s (`:426-483`).
- `remove` with `SessionHandle.DELETE_FILES` when asked; `shutdown` stops the session.
- No resume data is saved: after an app restart libtorrent checks the files again.

**`TorrentNativeLoader`** (`jvmAndroid/feature/torrent_engine/TorrentNativeLoader.kt`, 237
lines): JVM-only plumbing (extract `lib/x86_64/libtorrent4j.dll` from the jar into
`tools/2.1.0-39-libtorrent4j.dll`, set `libtorrent4j.jni.path`, optional download of
`https://repo1.maven.org/maven2/org/libtorrent4j/libtorrent4j-windows/2.1.0-39/libtorrent4j-windows-2.1.0-39.jar`
with its `.sha1`, 60 MiB limit). Its status texts are the ones to reuse:
`sysTorrentEngineReadyVersion(version)`, `sysTorrentEngineFailedToLoad(message)`,
`sysTorrentEngineAvailable`, `sysTorrentEngineMissing`, and the load-failure hints
(`"dependent libraries"` -> Visual C++ Redistributable, `"denied"` -> antivirus). Nothing of
the loader itself is ported.

**iOS actual** (`ios/feature/torrent_engine/TorrentBackend.ios.kt`, 211 lines +
`IosTorrentBridge.kt`): `IosTorrentBackend` forwards every call to a small native bridge
(`IosTorrentBridge`: `isAvailable`, `statusText`, `fetchMetadata(magnet, data, timeoutSeconds)`,
`add(taskId, data, savePath, selected, priorityIndexes, priorityValues, disableUpload, limits)`,
`pause`, `resume`, `remove`, `setFileSelection`, `setLimits`, `progress`, `lastError`,
`shutdown`) written on `iosApp/FJTorrent/FJTorrent.mm` (424 lines of Objective-C++ over
libtorrent-rasterbar, pinned commit `6da363d2994f17c0b3c0450d124cf73a31a73847` in
`scripts/build-libtorrent-ios.command:16`, CMake options
`-Ddeprecated-functions=OFF -Dwebtorrent=OFF -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF -DBUILD_SHARED_LIBS=OFF`).
This is the right shape for Windows: a thin C API over libtorrent, and a Kotlin backend that
only converts types. ROADMAP-1.4.0.md already names it ("FJTorrent built for Windows").

**On-disk:** nothing of the torrent engine itself is persisted. The `.torrent` bytes of each
task are kept by common code (`TorrentDownloader`), so tasks made by the JVM app resume (with a
file re-check) in the native app.

**Native plan, in two steps:**

Step 1 (so the 44 expects compile and the app runs):
`mingw/feature/torrent_engine/TorrentBackend.mingw.kt` (about 40 lines): a backend with
`isSupported = false`, `engineStatus() = Strings.sysTorrentEngineNotAvailable`,
`installEngine() = Result.failure(UnsupportedOperationException(engineStatus()))`, the rest
no-ops / `throw UnsupportedOperationException`. Part A's `platformSupportsTorrent()` should
return the same answer so the UI hides torrents.

Step 2 (real engine):

1. `desktopNative/fjtorrent/` (new folder, outside `sharedLogic`): `fjtorrent.h` (plain C) and
   `fjtorrent.cpp` (about 500 lines, a port of `FJTorrent.mm` with the session settings, proxy
   handling, public trackers and limits of `Libtorrent4jBackend` listed above), built into
   `fjtorrent.dll`. Suggested C API (UTF-8 strings, caller-supplied error buffers):

   ```c
   typedef struct fjt_session fjt_session;
   typedef struct fjt_status {
       int64_t wanted, wanted_done, down_rate, up_rate;
       int32_t peers, seeds, state, paused, finished, seeding, has_metadata, file_count;
   } fjt_status;

   const char *fjt_version(void);
   fjt_session *fjt_open(char *err, int err_len);
   void fjt_close(fjt_session *s);
   void fjt_set_proxy(fjt_session *s, int type, const char *host, int port, const char *user, const char *password);
   int fjt_fetch_metadata(fjt_session *s, const char *magnet, const uint8_t *torrent, int torrent_len,
                          int timeout_sec, const char *temp_dir,
                          uint8_t **out_torrent, int *out_torrent_len, char **out_json,
                          char *err, int err_len);
   int fjt_add(fjt_session *s, const char *task_id, const uint8_t *torrent, int torrent_len,
               const char *save_path, const uint8_t *file_priorities, int file_count,
               char *err, int err_len);
   void fjt_pause(fjt_session *s, const char *task_id);
   void fjt_resume(fjt_session *s, const char *task_id);
   void fjt_remove(fjt_session *s, const char *task_id, int delete_files);
   void fjt_set_file_priorities(fjt_session *s, const char *task_id, const uint8_t *file_priorities, int file_count);
   void fjt_set_limits(fjt_session *s, const char *task_id, int download, int upload);
   int fjt_status_of(fjt_session *s, const char *task_id, fjt_status *out,
                     int64_t *file_progress, int file_capacity, char *err, int err_len);
   void fjt_free(void *p);
   ```

   `out_json` of `fjt_fetch_metadata` =
   `{"name":..,"infoHash":..,"totalSize":..,"files":[{"index":..,"path":..,"size":..}]}`
   (pad files left out), parsed in Kotlin with `MiniJson`. In C++ use
   `lt::load_torrent_buffer` (the returned `add_torrent_params` already carries the trackers and
   web seeds, so the JVM `trackersAndWebSeeds` workaround is not needed) and for a magnet
   `lt::write_torrent_file_buf` after the metadata alert, as `SessionManager.fetchMagnet` does.
2. `mingw/feature/torrent_engine/FjTorrentBackend.mingw.kt` (about 350 lines): loads
   `fjtorrent.dll` from the app folder with `LoadLibraryW` on first use (keeps libtorrent out of
   memory at idle, one of the RAM items in ROADMAP-1.4.0.md) and calls the functions through
   `GetProcAddress` pointers (`CPointer<CFunction<...>>`), so no cinterop `.def` and no change
   to `sharedLogic/build.gradle.kts`. Kotlin side: `withPublicTrackers` (use the URL encoder of
   `TorrentBackend.ios.kt:174-186`), path sanitizing, priorities table, state texts, proxy
   collection from `NetworkSettings.proxy`, status texts from the loader list above.

Build route for the DLL that needs the least from the network: MSYS2 `clang64` already has the
compiler, CMake, Ninja, Boost 1.92 headers and static OpenSSL 3.6.5; only the libtorrent source
at the pinned commit (with its `deps/try_signal` submodule) is missing. A C API makes the
compiler of the DLL irrelevant to the Kotlin/Native and MSVC sides. Link libc++ and OpenSSL
statically so the DLL depends only on system DLLs.

Risk **high**: needs a source download the owner has to allow, a C++ build that has never been
done on this PC, and a new DLL in the installer. RAM: libtorrent4j.dll is 13 MB on disk; the
session should be created only when the first torrent task starts.

Rejected alternative: calling the existing `libtorrent4j.dll` (JNI/SWIG) without a JVM. Its
exports expect a `JNIEnv`, and the session logic (`fetchMagnet`, alerts) lives in Java classes.

### 7.2 `streamUrlToFile`

**Expect** (`common/feature/torrent_engine/TorrentStreamer.kt:18-25`):

```kotlin
expect suspend fun streamUrlToFile(
    urlStr: String,
    targetPath: Path,
    userAgent: String,
    speedLimitProvider: () -> Long = { 0L },
    startByte: Long = 0L,
    onProgress: (downloaded: Long, speed: Long) -> Unit
): Result<Unit>
```

**Caller:** `TorrentDownloader.kt:500` (web seeds: one call per file per seed, resume from the
size on disk; a result is "done" only when the file reached its size).

**JVM actual** (`jvm/feature/torrent_engine/TorrentStreamer.jvm.kt:11-105`): with the app proxy
on -> `HttpClient { webSeedDefaults() }` + `streamUrlWithClient(...)` (both common, same file
`:28-110`); otherwise `HttpURLConnection` through `openAppConnection`: spaces -> `%20`, headers
`User-Agent`, `Referer` = `extractReferer(url)` (`common/feature/torrent_engine/UrlUtils.kt:50`),
`Range: bytes=<start>-` when resuming, redirects followed; status outside 200..299 ->
`Result.failure(Exception("HTTP <code>: Failed to download from <url>"))`; appends only when
the answer is 206 and `startByte > 0`, else overwrites; 64 KiB blocks, `RateLimiter`, speed
every 150 ms smoothed `0.4 x old + 0.6 x new`; cancellation rethrown.

**iOS actual** (`ios/feature/torrent_engine/TorrentStreamer.ios.kt`, 29 lines):
`HttpClient(Darwin) { webSeedDefaults() }` + `streamUrlWithClient`.

**Native plan:** `mingw/feature/torrent_engine/TorrentStreamer.mingw.kt` (about 25 lines): the
iOS file with `HttpClient(WinHttp)`. Risk low.

---

## 8. Device sync

### 8.1 Expects

`common/feature/device_sync/DeviceSync.kt:107-116`:

```kotlin
/**
 * Builds the platform device-sync. [onReceive] is called on the receiver when a payload arrives
 * (with the local paths of any partial files already written into the scratch folder, ready to
 * resume). JVM + Android share one implementation; iOS gets it in its own phase.
 */
expect fun createDeviceSync(
    settingsDir: String,
    scratchDirFor: (taskId: String) -> String,
    onReceive: (SendPayload, localPartialFiles: List<String>) -> Unit
): DeviceSync
```

`common/feature/device_sync/MulticastLock.kt:3-10`:

```kotlin
/**
 * Android needs a Wi-Fi multicast lock held to receive UDP broadcast/multicast; on the desktop
 * this is a no-op. Held while the device-sync discovery is listening.
 */
internal expect object MulticastLock {
    fun acquire(): Any?
    fun release(token: Any?)
}
```

Interface `DeviceSync` (`DeviceSync.kt:67-105`): flows `discovered`, `paired`, `pairing`,
`sending`, `blocked`; `selfId`, `selfName`, `setSelfName`; `start`, `stop`, `setVisible`;
`startPairing(deviceId)`, `startPairingWithHost(host)`, `confirmPairing`, `cancelPairing`,
`blockPairing`, `unblock`, `unpair`; `send(request, onResult)`, `cancelSend`. Models in
`DeviceSyncModels.kt` (`PairedDevice`, `DiscoveredDevice`, `SendPayload`, `safeWireName`).

**Callers:** `common/presentation/dashboard/DashboardViewModel.kt:98-104` (lazy; settingsDir =
`pathResolver.getSettingsDirectory()`, scratch = `<temp>/task_<id>`, `onReceive` ->
`onDeviceReceive` in `DashboardDeviceSync.kt:67`), and the collectors / event handlers in
`DashboardDeviceSync.kt`. The UI translates the refusal words in `deviceSyncReason`
(`sharedUI/.../DeviceSendDialog.kt`), so they are part of the contract: `pending`,
`not paired`, `declined`, `expired`, `blocked`, `busy`, `wait:<seconds>`, plus the sender-side
texts `device offline or not paired` and
`the other device unpaired this one; send again to pair`.

### 8.2 JVM implementation

`jvm/feature/device_sync/MulticastLock.jvm.kt`: `acquire() = null`, `release` does nothing.

`jvmAndroid/feature/device_sync/DeviceSyncFactory.jvmAndroid.kt:5-29`:
`DeviceSyncImpl(platformName, scratchDirFor, tempDir = "$settingsDir/device-sync-tmp", onReceive)`.
`platformName` on the desktop = `"<host> · Windows"` (separator: space, U+00B7, space) where
host = `InetAddress.getLocalHost().hostName` before the first `.`, left out when blank or
`localhost`; the OS label is `Windows` / `macOS` / `Linux`.

`jvmAndroid/feature/device_sync/DeviceSyncImpl.jvmAndroid.kt` (847 lines). `scratchDirFor` is
stored but never used.

#### Constants (`:34-53`)

| Name                  | Value                                                                                                                            |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `DISCOVERY_PORT`      | UDP 28461                                                                                                                        |
| `SYNC_TCP_PORT`       | TCP 28462                                                                                                                        |
| `ANNOUNCE_EVERY_MS`   | 2000                                                                                                                             |
| `DEVICE_EXPIRE_MS`    | 8000                                                                                                                             |
| `CHUNK`               | 65 536 bytes of plain file data per chunk frame                                                                                  |
| `ACCEPT_WAIT_MS`      | 90 000                                                                                                                           |
| `PAIR_REQUEST_GAP_MS` | 30 000                                                                                                                           |
| Frame types           | `T_HELLO` 1, `T_HELLO_ACK` 2, `T_SEND_META` 3, `T_FILE_CHUNK` 4, `T_FILE_END` 5, `T_ACK` 6, `T_ERR` 7, `T_READY` 8, `T_UNPAIR` 9 |
| `NO_BROADCAST`        | env `FETCHJET_SYNC_NO_BROADCAST == "1"` (test switch)                                                                            |

#### Wire messages (kotlinx.serialization JSON,
`Json { ignoreUnknownKeys = true; encodeDefaults = true }`, UTF-8)

```kotlin
@Serializable private data class Announce(val t: String = "fj-ann", val id: String, val name: String, val plat: String, val port: Int)
@Serializable private data class Hello(val id: String, val name: String, val plat: String, val pub: String)
@Serializable private data class FileMeta(val name: String, val size: Long)
@Serializable private data class SendMeta(val payload: SendPayload, val mode: String, val files: List<FileMeta>)
```

`SendPayload` (`DeviceSyncModels.kt:33-48`): `url`, `fileName`, `engineType`, `formatId`,
`originalUrl`, `cookie`, `referer`, `userAgent`, `fromDeviceId`, `fromDeviceName`, `clipStart`,
`clipEnd`, `clipByCutting`. `mode` = `SendMode.name` (`LINK_ONLY` / `TRANSFER_AND_CONTINUE`).

#### UDP discovery

- Listen socket (`discoveryListenLoop` `:270-321`): one UDP socket, `SO_REUSEADDR` and
  `SO_BROADCAST` set, bound to the wildcard address port 28461, receive buffer 2048 bytes. It
  runs from `start()` whatever the visibility. A bind failure logs
  `FJSYNC listen bind failed: ...` and discovery stays off.
- Each datagram is one `Announce` JSON. Ignored when it does not parse or `id == selfId`.
    - `t == "fj-probe"`: when this device is visible, its TCP server is open and the sender is
      not blocked, reply with this device's announce **from the listen socket to the sender's
      address, port 28461** (not to the source port). The asker is not added to the list.
    - `t == "fj-ann"` and not blocked: remember
      `DiscoveredDevice(id, name, plat, host = sender IPv4 text, port = ann.port, paired)`,
      `lastSeenAt[id] = now`, publish. A new or moved device logs
      `FJSYNC heard <name> @ <ip>:<port>`.
- Announce loop (`:194-231`): a second UDP socket (unbound, `SO_BROADCAST`). Every 2000 ms:
    - body = `Announce(t = "fj-ann", id, name = selfName, plat = platformName, port = <TCP port>)`
      when visible with an open server, else
      `Announce(t = "fj-probe", ..., port = 0)`;
    - when visible, server open and not `NO_BROADCAST`: send to every IPv4 broadcast address of
      every interface that is up and not loopback (`broadcastAddresses` `:233-249`; the
      interface's broadcast address, or computed from the prefix length 1..31; when the list is
      empty `255.255.255.255`), port 28461;
    - always: send the same body directly to every known, not blocked device's host, port 28461.
- Expiry (`:325-335`): every 2000 ms devices not heard for more than 8000 ms are dropped.
- Published list: not blocked, `paired` recomputed from `PairedDeviceStore`.

#### TCP

- Server (`applyVisibility` `:145-162`): only while visible. `SO_REUSEADDR`, bind wildcard
  port 28462; when that fails bind port 0 (any); the real port goes into the announce. Hidden
  -> the server socket is closed. Logs `FJSYNC visible tcpPort=<p>` / `FJSYNC hidden`.
- One request per connection (`handle` `:358-371`): read one frame, dispatch on its type (`T_HELLO`,
  `T_SEND_META`, `T_UNPAIR`; anything else -> `T_ERR "unknown"`), close.
- Frame (`readFrame` `:831-840`, `writeFrame` `:842-846`):
  `[type: 1 byte][length: 4 bytes, big-endian, signed][body]`, length 0..67 108 864 (64 MiB),
  larger -> the connection is dropped. Streams are buffered; no read timeout is set.
- Client connects with a 5000 ms timeout (3000 ms for the unpair notice).

#### Pairing

Initiator (`connectAndPair` `:509-555`; `startPairing(deviceId)` uses the discovered host and
port, `startPairingWithHost(host)` uses port 28462 and the host text as the display name):

1. `pairing = Connecting(displayName)`.
2. New EC key pair; send `T_HELLO` with
   `Hello(selfId, selfName, platformName, pub = Base64(public key))`.
3. Read one frame: `T_ERR` -> `pairing = Failed(displayName, <body text>)`; not `T_HELLO_ACK`
   -> `Failed(..., "bad reply")`; no frame -> `"no reply"`; any other exception ->
   `Failed(displayName, message or "failed")`.
4. From the `Hello` in the ack: key = `deriveKey`, code = `pairingCode(pubA, pubB)`; the peer is
   put in the discovered list; `pending = Pending(PairedDevice(id, name, plat, now), key)`;
   `pairing = Confirm(peerName, peerId, code)`.

Responder (`handleHello` `:373-406`):

1. `pairRefusal(fromId)` (`:413-425`): `blocked`; `busy` when another device's request is on
   screen; `wait:<seconds>` when the same device asked less than 30 s ago
   (`(30000 - elapsed + 999) / 1000`). A refusal is sent as `T_ERR <reason>` and logged
   `FJSYNC pairing from <name> refused: <reason>`.
2. New key pair, key, `T_HELLO_ACK` with its own `Hello`, code, `pending`, `pairing = Confirm(...)`.
3. After 90 000 ms, when this pending is still the current one: `endPairing(p, "expired")`.

Both sides: `confirmPairing()` stores `PairedDeviceStore.addPairing(device, key)` and clears
the state. `cancelPairing()` -> `endPairing(pending, "declined")`. `endPairing` (`:576-581`)
remembers `(time, reason)` per device id for 120 s (`endedPairingReason`), logs
`FJSYNC pairing code for <name> closed: <why>`. `blockPairing()` -> `PairedDeviceStore.block`,
clears the state. Nothing travels on the wire when the user confirms: the sender finds out by
sending (next part).

#### Sending (`send` `:646-703`, `trySendOnce` `:713-799`)

- Target = the discovered device, else the paired entry (then host is empty). No host, port 0
  or no key -> `sending = Failed(name, "device offline or not paired")`, `onResult(false)`.
- Loop with a deadline of 90 s:
    - `Success` -> `Done`, `onResult(true)`;
    - `WaitAccept` (the receiver answered `pending`): `sending = Waiting(name)`, wait 1500 ms,
      stop silently when `cancelSend()` was called (`sending = null`, `onResult(false)`); past
      the deadline: forget the pairing, `Failed(name, "expired")`;
    - `NotPaired` (answer `not paired`): forget the pairing,
      `Failed(name, "the other device unpaired this one; send again to pair")`;
    - `Fail(reason)`: for `blocked`, `declined`, `expired` forget the pairing;
      `Failed(name, reason)`.
- One attempt:
    1. connect (5000 ms);
    2. files: nothing for `LINK_ONLY`; for `TRANSFER_AND_CONTINUE` the existing
       `request.partialFiles` under their own names; or, for a finished file,
       `"<base>.chunks"` with the text `0 0 <size - 1>` (never below 0) held in memory and
       `"<base>.part0"` = the finished file (base = `payload.fileName` or the file's name);
    3. send `T_SEND_META`, body = `[idLen: int32 BE][selfId UTF-8][seal(key, JSON(SendMeta))]`;
    4. read one frame: `T_ERR` with `pending` / `not paired` / other text (see above); not
       `T_READY` -> `Fail("unexpected reply")`; nothing -> `Fail("no reply")`;
    5. per file: `T_FILE_CHUNK` frames, each body = `seal(key, up to 65 536 plain bytes)` (an
       in-memory file is one chunk), then one `T_FILE_END` with an empty body; progress =
       sent plain bytes / total (at least 1);
    6. read one frame: `T_ACK` -> success; other -> `Fail("receiver rejected")`; nothing ->
       `Fail("no ack")`.

Receiver (`handleSend` `:443-496`):

1. Split the body into the sender id and the sealed meta. No stored key -> `T_ERR` with
   `blocked`, `pending` (that device's code is on screen now), the remembered `declined` /
   `expired`, or `not paired`; logged unless `pending`.
2. Open the meta: failure -> `T_ERR "bad key"`; JSON failure -> `T_ERR "bad meta"`.
3. `T_READY` (empty body), flush.
4. Folder `<tempDir>/recv-<epoch ms>` (only when there are files). Per `FileMeta`: name through
   `safeWireName` (null -> error `"bad file name"`); frames until `T_FILE_END`, every
   `T_FILE_CHUNK` body opened with the key and appended; other frame -> `"unexpected frame"`;
   end of stream -> `"stream ended"`.
5. `onReceive(meta.payload.copy(fromDeviceId = fromId), <absolute paths written>)`, then
   `T_ACK`. Any exception -> `T_ERR <message or "error">`.

#### Unpair (`unpair` `:589-607`, `handleUnpair` `:616-627`)

Forget locally; when the peer is on the network (host and port known) and a key existed:
connect (3000 ms), send `T_UNPAIR` with body
`[idLen: int32 BE][selfId][seal(key, "unpair")]`. The receiver forgets the sender only when the
sealed text opens to `unpair` with that sender's key; logs `FJSYNC unpaired by <id>`. No reply.

#### Crypto, exact parameters

| Step                   | JVM code                                                                                                        | Parameters                                                                                                                                                                                                                      |
|------------------------|-----------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Key pair               | `KeyPairGenerator.getInstance("EC").initialize(256)` (`:803-804`)                                               | NIST P-256 (secp256r1)                                                                                                                                                                                                          |
| Public key on the wire | `public.encoded`, standard Base64 **with padding** (`kotlin.io.encoding.Base64`)                                | X.509 SubjectPublicKeyInfo DER, 91 bytes: fixed prefix `30 59 30 13 06 07 2A 86 48 CE 3D 02 01 06 08 2A 86 48 CE 3D 03 01 07 03 42 00` (26 bytes), then `04`, X (32 bytes, big-endian), Y (32 bytes)                            |
| Shared key             | `KeyAgreement("ECDH")` then `SHA-256(generateSecret())` (`:806-812`)                                            | secret = the X coordinate of the shared point, 32 bytes big-endian; key = its SHA-256, 32 bytes; no salt, no info                                                                                                               |
| Pairing code           | `pairingCode` (`:814-820`)                                                                                      | order the two DER public keys by unsigned byte comparison (shorter first on a common prefix), concatenate smaller ++ larger, SHA-256, `n = (h[0] << 16) \| (h[1] << 8) \| h[2]`, `n % 1 000 000` as 6 digits with leading zeros |
| Message encryption     | `CryptoBox` (`common/core/security/CryptoBox.kt:10`, actual `jvmAndroid/core/security/CryptoBox.jvmAndroid.kt`) | AES-256-GCM, 12 random bytes of nonce, 128-bit tag, no additional data; output = nonce (12) ++ ciphertext ++ tag (16); a wrong tag throws                                                                                       |
| Stored pairing key     | `PairedDeviceStore` (`common/feature/device_sync/PairedDeviceStore.kt`)                                         | Base64 of the 32 bytes, then `Secrets.protect` (`fjenc1:` + Base64 of `SecretBox.seal`)                                                                                                                                         |

`CryptoBox` belongs to `core/` (part A lists the expect); its natural implementation is the
same CNG code as the ECDH below, so the helper in section 9 covers both.

#### On disk

- `<settings>/devices.json` (`PairedDeviceStore`, common): pretty-printed JSON
  `{ "selfId": "<32 hex>", "selfName": "...", "peers": [ { "id", "name", "platform", "lastSeenEpochMs", "key": "fjenc1:..." } ], "blocked": [ { "id", "name", "platform", "lastSeenEpochMs" } ] }`.
  The native app reads the same file. The peers' keys open only when part A's `SecretBox` for
  Windows can open what the JVM `WindowsKeyVault` sealed; if it cannot, `keyFor` returns null:
  the device still shows as paired but a send fails with `device offline or not paired` and an
  incoming send is answered `not paired` (the other side then drops its pairing and pairs
  again). `selfId` is kept either way, so other devices still recognise this one.
- `<settings>/device-sync-tmp/recv-<epoch ms>/<wire name>`: received partial files, moved by
  common code (`placeReceivedParts`) into the task's scratch folder.

Java APIs used: `java.net.DatagramSocket`, `DatagramPacket`, `ServerSocket`, `Socket`,
`InetAddress`, `InetSocketAddress`, `NetworkInterface`; `java.io.DataInputStream` /
`DataOutputStream` / `File`; `java.security.KeyPairGenerator`, `KeyFactory`,
`X509EncodedKeySpec`, `MessageDigest`; `javax.crypto.KeyAgreement`, `Cipher`,
`GCMParameterSpec`, `SecretKeySpec`.

### 8.3 iOS reference (the template to port from)

`ios/feature/device_sync/DeviceSyncImpl.ios.kt` (751 lines) is the same state machine written
without any Java type: all sockets and crypto go through the interface `IosDeviceSyncNative`
(`ios/feature/device_sync/IosDeviceSyncNative.kt:35-97`: `generateEcKeyPair`, `deriveKey`,
`releaseKeyPair`, `aesSeal`, `aesOpen`, `sha256`, `startUdpListen`, `sendUdpBroadcast`,
`sendUdpTo`, `sendUdpToSubnet`, `stopUdp`, `startTcpServer`, `stopTcpServer`, `tcpConnect`,
`tcpReadFully`, `tcpWrite`, `tcpClose`), files through okio (`IosDeviceSyncIo.kt`:
`FileChunkReader`, `FileChunkWriter`), locks through `NSLock`, big-endian ints by hand (`readInt` /
`writeInt` at the end of the file). Differences from the JVM file: it sweeps the
/24 subnet every 30 s (`SWEEP_EVERY_MS`, only because an iPhone cannot broadcast) and uses
`NSData`. `ios/feature/device_sync/DeviceSyncFactory.ios.kt` falls back to a no-op object when
the bridge is missing; `MulticastLock.ios.kt` is a no-op.

### 8.4 Native plan

- `mingw/feature/device_sync/MulticastLock.mingw.kt` (6 lines): no-op.
- `mingw/feature/device_sync/DeviceSyncFactory.mingw.kt` (about 40 lines): `createDeviceSync`
    + `deviceSyncPlatformName()` = `"<gethostname() before the first '.'> · Windows"` (or just
      `Windows`).
- `mingw/feature/device_sync/DeviceSyncImpl.mingw.kt` (about 760 lines): start from
  `DeviceSyncImpl.ios.kt`, replace `NSData` by `ByteArray`, `NSLock` by `FjLock`, `nowMs()` by
  `nowEpochMs()`, the native bridge by `WinSock` + `WinCrypto` (section 9), drop the subnet
  sweep (the desktop broadcasts), keep the JVM behaviours listed in 8.2 (probe reply from the
  listen socket to port 28461, bind fallback to port 0, log lines). Copy
  `FileChunkReader` / `FileChunkWriter` from `IosDeviceSyncIo.kt` into the same folder (they
  are plain okio).
- The UDP receive loop and the TCP accept loop block in `recvfrom` / `accept`: run each in a
  coroutine on `Dispatchers.IO`; `stop()` / hiding closes the socket, which ends the call.
- Sockets: IPv4 only (`AF_INET`) is enough; every existing implementation announces over IPv4.

Risk **medium**: the crypto is proven (section 10); the remaining risks are Winsock details (closing
a socket another thread is blocked on, `SO_REUSEADDR` semantics on Windows) and the
Windows Firewall: the first `bind` + `listen` of a new executable shows the Windows Security
prompt, and until the user allows it other devices cannot reach TCP 28462 or UDP 28461. The
installer of the native app needs the same firewall handling the JVM app has today (part A /
packaging).

---

## 9. Shared low-level helpers (recommended API)

All in `mingw/core/win/` (package `com.michelle.raouf.fetch_jet.core.win`), `internal`, so the
packages below (and part A) use one implementation. Signatures only; every function is blocking
unless marked `suspend`.

### 9.1 `WinProcess.kt` (about 300 lines)

```kotlin
internal enum class ProcessOutput { PIPE, DISCARD }

internal class ProcessResult(val exitCode: Int, val output: String, val timedOut: Boolean)

internal class WinProcess private constructor() {
    val isAlive: Boolean
    fun waitFor(timeoutMs: Long): Boolean
    fun exitCode(): Int
    fun readLine(): String?
    fun readAvailableText(maxChars: Int): String
    fun killTree()
    fun close()

    companion object {
        fun start(
            command: List<String>,
            env: Map<String, String> = emptyMap(),
            output: ProcessOutput = ProcessOutput.PIPE,
            workingDir: String? = null
        ): WinProcess

        fun run(
            command: List<String>,
            timeoutMs: Long,
            env: Map<String, String> = emptyMap(),
            output: ProcessOutput = ProcessOutput.PIPE,
            maxChars: Int = Int.MAX_VALUE
        ): ProcessResult

        fun quoteArgument(arg: String): String
        fun findOnPath(exeName: String): String?
    }
}
```

Rules the implementation must follow (they are what the Java runtime does for the JVM app, or
fix a gap listed above):

- `CreateProcessW` with `lpApplicationName = NULL`, the command line built from
  `quoteArgument` of each item (MSVC CRT rules: quote when empty or containing space, tab or
  `"`; a `"` becomes `\"`; backslashes before a `"` or before the closing quote are doubled).
  Never go through `cmd.exe`.
- Flags
  `CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT | CREATE_SUSPENDED | EXTENDED_STARTUPINFO_PRESENT`,
  then
  `AssignProcessToJobObject` and `ResumeThread`. `CREATE_NO_WINDOW` matters: the app is a GUI
  process and yt-dlp / ffmpeg / deno are console programs; without it a console window flashes
  for each start.
- One Job object per process with `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`; `killTree()` =
  `TerminateJobObject`. This replaces `process.descendants().forEach { destroyForcibly() }` and
  also ends the children when the app itself dies.
- stdout and stderr -> the write end of one anonymous pipe (`CreatePipe`), stdin -> `NUL`.
  Only these handles may be inherited: pass them with `STARTUPINFOEXW` +
  `PROC_THREAD_ATTRIBUTE_HANDLE_LIST` (several processes are started at the same time; a
  leaked pipe handle in another child keeps the pipe open and `readLine` never sees the end).
  Close the parent's copy of the write end right after the start.
- Environment = the parent's block plus `env` (case-insensitive names), as a UTF-16 block
  sorted by name.
- `readLine()` decodes UTF-8 and ends a line at `\n`, `\r` or `\r\n` (see 5.1).
- `output = DISCARD` -> stdout and stderr to `NUL`.
- `run` = start, drain the pipe on the calling thread while waiting, `killTree()` on timeout.

### 9.2 `WinSock.kt` (about 350 lines)

```kotlin
internal class UdpPacket(val senderIp: String, val senderPort: Int, val data: ByteArray)

internal class UdpSocket {
    fun sendTo(host: String, port: Int, data: ByteArray): Boolean
    fun receive(maxBytes: Int = 2048): UdpPacket?
    fun close()
}

internal class TcpSocket {
    fun setNoDelay(on: Boolean)
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int
    fun readFully(length: Int): ByteArray?
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size): Boolean
    fun close()
}

internal class TcpServer {
    val localPort: Int
    fun accept(): TcpSocket?
    fun close()
}

internal object WinSock {
    fun udpBind(port: Int, reuseAddress: Boolean, broadcast: Boolean): UdpSocket?
    fun udpOpen(broadcast: Boolean): UdpSocket?
    fun tcpListen(bindHost: String?, port: Int, reuseAddress: Boolean, backlog: Int = 50): TcpServer?
    fun tcpConnect(host: String, port: Int, timeoutMs: Int): TcpSocket?
    fun broadcastAddresses(): List<String>
    fun hostName(): String?
}
```

`WSAStartup(2.2)` once, lazily. `tcpConnect` = non-blocking connect + `select` with the
timeout, then back to blocking. `broadcastAddresses()` = `WSAIoctl(SIO_GET_INTERFACE_LIST)` on
a UDP socket: for every entry that is up and not loopback, `address | ~netmask`; empty ->
`255.255.255.255`. `read` returns -1 at end of stream or after `close()` from another thread.

### 9.3 `WinCrypto.kt` (about 250 lines; shared with part A's `CryptoBox` / `SecretBox`)

```kotlin
internal class EcdhP256KeyPair {
    val publicX509: ByteArray
    fun deriveSha256(peerPublicX509: ByteArray): ByteArray
    fun close()
}

internal object WinCrypto {
    fun randomBytes(count: Int): ByteArray
    fun newEcdhP256(): EcdhP256KeyPair
    fun aesGcmSeal(key: ByteArray, plain: ByteArray): ByteArray
    fun aesGcmOpen(key: ByteArray, sealed: ByteArray): ByteArray
}
```

CNG calls (each verified by the probe in section 10):

- Key pair: `BCryptOpenAlgorithmProvider("ECDH_P256")`, `BCryptGenerateKeyPair(256)`,
  `BCryptFinalizeKeyPair`.
- `publicX509`: `BCryptExportKey("ECCPUBLICBLOB")` gives 72 bytes = magic `0x314B4345`
  (`ECK1`, little-endian ULONG), `cbKey = 32`, X, Y; the DER form is the 26-byte prefix of 8.2 +
  `04` + the last 64 bytes.
- `deriveSha256`: check the peer key is 91 bytes with that prefix (else throw), build the same
  72-byte blob from its last 64 bytes, `BCryptImportKeyPair("ECCPUBLICBLOB")`,
  `BCryptSecretAgreement`, `BCryptDeriveKey("HASH")` with one `BCryptBuffer`
  (`KDF_HASH_ALGORITHM` = 0, value the UTF-16 text `SHA256` with its terminator), 32 bytes out.
  This equals Java's `SHA-256(generateSecret())`. (`BCryptDeriveKey("TRUNCATE")` returns the raw
  secret in little-endian order: hashing that without reversing gives a different key.)
- AES-GCM: `BCryptOpenAlgorithmProvider("AES")`,
  `BCryptSetProperty("ChainingMode", "ChainingModeGCM")`,
  `BCryptGenerateSymmetricKey`, `BCryptEncrypt` / `BCryptDecrypt` with
  `BCRYPT_AUTHENTICATED_CIPHER_MODE_INFO` (`cbSize`, `dwInfoVersion = 1`, 12-byte nonce,
  16-byte tag, no auth data, no IV, flags 0). A wrong tag returns `0xC000A002`
  (`STATUS_AUTH_TAG_MISMATCH`): throw.
- `randomBytes`: `BCryptGenRandom(NULL, ..., BCRYPT_USE_SYSTEM_PREFERRED_RNG)`.
- SHA-256 / SHA-1 for the pairing code and the tool downloads: okio (`ByteString.sha256()`,
  `HashingSink`), no CNG needed.

### 9.4 `ZipReader.kt` (about 250 lines)

```kotlin
internal object ZipReader {
    fun unzipAll(zip: Path, targetDir: Path)
    fun unzipOne(zip: Path, plainFileName: String, target: Path): Boolean
}
```

Reads the end-of-central-directory record and the central directory with okio
(`FileSystem.SYSTEM.openReadOnly`), methods 0 (stored) and 8 (deflate through `platform.zlib`
`inflateInit2(-15)`), CRC-32 check with `platform.zlib.crc32`, UTF-8 or CP437 names, refuses
entries whose normalised path leaves `targetDir` (`"bad path in zip: <name>"`), writes each
file through a temp name for `unzipOne`. Zip64 is not needed for these archives (all far below
4 GiB) but an archive that uses it must fail with a clear message.

### 9.5 `ToolHttp.kt` (about 120 lines)

```kotlin
internal object ToolHttp {
    suspend fun readText(url: String): String
    suspend fun downloadTo(url: String, dest: Path, maxBytes: Long, onProgress: (Float?) -> Unit): String
    suspend fun contentLength(url: String, userAgent: String, timeoutMs: Long): Long
}
```

`downloadTo` returns the lowercase SHA-256 hex and moves `<dest>.tmp` over `dest` at the end;
a status outside 200..299 throws `IllegalStateException("HTTP <code> for <url>")`.

### 9.6 `FjLock.kt` (about 30 lines)

```kotlin
internal class FjLock {
    inline fun <T> withLock(block: () -> T): T
}
```

Over `SRWLOCK` (`AcquireSRWLockExclusive`) or a `CRITICAL_SECTION` (recursive, closer to Java's
`synchronized`; `DeviceSyncImpl.jvmAndroid.kt` nests `synchronized(seen)` inside
`@Synchronized` methods, so pick the recursive one or keep the two locks separate as the JVM
file does).

---

## 10. Probe: CNG against the JVM crypto of device sync

Folder `E:/tools/probes/feature-inventory/crypto/` (`Bc.cs`, `JavaSide.java`, `run.ps1`; run
with `powershell -NoProfile -ExecutionPolicy Bypass -File run.ps1`, console only). It uses the
same `bcrypt.dll` calls the native code will make (through P/Invoke) and the same Java calls as
`DeviceSyncImpl.jvmAndroid.kt` / `CryptoBox.jvmAndroid.kt` (JDK 21 from
`C:/Program Files/Eclipse Adoptium`). Result on 2026-11-09:

```
win blob: magic=0x314b4345 cbKey=32 exported=72
java publen=91 prefix=3059301306072a8648ce3d020106082a8648ce3d03010703420004
java key             : 78bf3be6...b60c3350
KDF_HASH(SHA256)     : 78bf3be6...b60c3350  match=True
SHA256(rev(TRUNCATE)): 78bf3be6...b60c3350  match=True
SHA256(TRUNCATE)     : 1a15cb4f...528104f1  match=False
java secret length   : 32
win opened java msg  : True
win tampered msg     : decrypt failed 0xc000a002
java opened windows msg: true
```

So: Java accepts the 91-byte key built from the CNG blob, CNG accepts Java's key, both sides
derive the same 32-byte key with `BCRYPT_KDF_HASH` + SHA-256, and AES-256-GCM messages open in
both directions with the `nonce ++ ciphertext ++ tag` layout. Not covered by the probe: a real
pairing between the native app and a phone (needs the app), and the pairing-code function (plain
SHA-256 arithmetic, same code on every platform).

---

## 11. Work packages

Every package creates only new files; the file sets are disjoint. "Needs" = helpers of
section 9 or other parts.

| Package                              | New files                                                                                                                                                                                                                                                 | Expects covered                                                       | Ported from                                                                                                                                      | Needs                                                                                                                                                       | Order                                                                                                                                                                                                                                        |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **F0 helpers**                       | `mingw/core/win/WinProcess.kt`, `WinSock.kt`, `WinCrypto.kt`, `ZipReader.kt`, `ToolHttp.kt`, `FjLock.kt`                                                                                                                                                  | none (part A's `CryptoBox` can be a 10-line wrapper over `WinCrypto`) | new; behaviour of Java `ProcessBuilder`, `java.net`, `javax.crypto`, `java.util.zip` as used in sections 2-8                                     | `platform.windows`, `platform.posix`, `platform.zlib`, ktor WinHttp, okio                                                                                   | first; must be agreed with part A so `core/win` is written once                                                                                                                                                                              |
| **F1 small actuals**                 | `mingw/feature/http_downloader/HttpDownloadEngine.mingw.kt`, `mingw/feature/device_sync/MulticastLock.mingw.kt`, `mingw/feature/torrent_engine/TorrentStreamer.mingw.kt`, `mingw/feature/torrent_engine/TorrentBackend.mingw.kt` (the stub of 7.1 step 1) | 1, 17 (stub), 18, 20                                                  | `HttpDownloadEngine.jvmAndroid.kt`, `MulticastLock.jvm.kt`, `TorrentStreamer.ios.kt`, `TorrentBackend.ios.kt` (shape)                            | part A: `PathResolver`, `StreamHttpClient`, `useNetworkSettings` for WinHttp                                                                                | any time; no helper needed                                                                                                                                                                                                                   |
| **F2 media tools**                   | `mingw/feature/media_extractor/MediaToolPaths.mingw.kt`, `MediaExtractorToolManager.mingw.kt`, `YtDlpSecretArgs.mingw.kt`                                                                                                                                 | 4-11                                                                  | `MediaToolPaths.jvm.kt`, `MediaExtractorToolManager.jvm.kt`, `YtDlpSecretArgs.jvm.kt`                                                            | `WinProcess.run`, `ZipReader`, `ToolHttp`                                                                                                                   | after F0. Its internal API (`getExistingYtDlpBinary`, `getExistingFfmpegBinary`, `getExistingDenoBinary`, `isSystemFfmpegInstalled`, `ytDlpRuntimeArgs`, `getToolsDir`, `YtDlpSecretArgs.split`) is used by F3 and F4: fix these names first |
| **F3 yt-dlp run + inspect + ffmpeg** | `mingw/feature/media_extractor/MediaStreamExtractorPlatform.mingw.kt`, `MediaInspect.mingw.kt`, `LocalFfmpeg.mingw.kt`                                                                                                                                    | 2, 3, 12, 13, 14, 16                                                  | `MediaStreamExtractorPlatform.jvm.kt`, `MediaInspect.jvm.kt`, `LocalFfmpeg.jvm.kt`                                                               | `WinProcess`, `ToolHttp.contentLength`, the F2 names                                                                                                        | after F0; can be written in parallel with F2 against the agreed names                                                                                                                                                                        |
| **F4 yt-dlp download**               | `mingw/feature/media_extractor/MediaYtDlpDownload.mingw.kt`, `MediaThrottlingProxy.mingw.kt`                                                                                                                                                              | 15                                                                    | `MediaYtDlpDownload.jvm.kt`, `MediaThrottlingProxy.jvm.kt`                                                                                       | `WinProcess`, `WinSock` (TCP), `FjLock`, the F2 names                                                                                                       | after F0; parallel with F2 / F3                                                                                                                                                                                                              |
| **F5 device sync**                   | `mingw/feature/device_sync/DeviceSyncFactory.mingw.kt`, `DeviceSyncImpl.mingw.kt`, `DeviceSyncIo.mingw.kt`                                                                                                                                                | 19                                                                    | `DeviceSyncImpl.ios.kt` (structure), `DeviceSyncImpl.jvmAndroid.kt` (desktop behaviour), `IosDeviceSyncIo.kt`, `DeviceSyncFactory.jvmAndroid.kt` | `WinSock` (UDP + TCP), `WinCrypto`, `FjLock`; part A: `CryptoBox` is not needed if the file calls `WinCrypto` directly, `SecretBox` (for `devices.json`) is | after F0; independent of F2-F4                                                                                                                                                                                                               |
| **F6 torrent engine**                | `desktopNative/fjtorrent/fjtorrent.h`, `fjtorrent.cpp`, `CMakeLists.txt`, a build script; `mingw/feature/torrent_engine/FjTorrentBackend.mingw.kt`; then `TorrentBackend.mingw.kt` of F1 is changed to return it                                          | 17 (real)                                                             | `iosApp/FJTorrent/FJTorrent.mm`, `Libtorrent4jBackend.kt`, `TorrentBackend.ios.kt`                                                               | libtorrent source (download), MSYS2 clang64 toolchain already here; `LoadLibraryW` / `GetProcAddress`                                                       | last; blocked on the owner's answer about the download; the app works without it (torrents hidden)                                                                                                                                           |

Until F2-F5 exist, the build can be unblocked with one-line placeholder actuals (return null /
false / empty list, `NoopDeviceSync` as in `DeviceSyncFactory.ios.kt:13-34`); the common code
already treats those answers as "tool missing".

Estimated new code: F0 1300 lines, F1 80, F2 570, F3 610, F4 800, F5 830, F6 350 Kotlin + 500
C++.

---

## 12. Open questions

1. Torrents: may the libtorrent source (pinned commit `6da363d2...`, a shallow git fetch of
   some tens of MB with the `try_signal` submodule) be downloaded? Without it the native app
   ships with torrents off. Everything else for the build is already on the PC (MSYS2 clang64).
2. yt-dlp downloads and the app proxy: on the JVM desktop the local throttling proxy connects
   directly, so a yt-dlp download ignores the app's proxy (5.2). Copy this, or make the local
   proxy connect through the app proxy (HTTP CONNECT / SOCKS5) in the native app?
3. `runLocalFfmpeg` and the inspection calls are not cancellable on the JVM. The plan makes
   them cancellable (kill the tree on cancel). Confirm this is wanted (it changes nothing for
   the user except that a pause during a merge stops ffmpeg at once).
4. Windows Firewall: who adds the inbound rule for the native executable (UDP 28461, TCP 28462,
   and libtorrent's listen port later): the installer, or the first-run system prompt?
5. Part A must say whether its Windows `SecretBox` can open values the JVM app sealed. If not,
   existing pairings in `devices.json` need a new pairing after the switch (8.2, On disk).
6. SOCKS5 with a login through WinHttp (tool downloads, size probes, web seeds) depends on part
   A's `applyProxyLogin` for the WinHttp engine. Whether WinHTTP can do SOCKS5 with a user and
   password at all was not checked here; if it cannot, these calls need another route while
   such a proxy is on (the JVM app switches the stream client to OkHttp for SOCKS5).
