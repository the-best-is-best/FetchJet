# 12. Torrent engine plan for the native Windows app

Date: 2026-11-09. Branch `desktop-native`. Nothing was downloaded, installed or built for this
document.
Everything marked **verified** was read from files on this PC or from the web page named next to it.
Everything marked **estimate** or **not verified** still has to be checked by whoever implements it.

Paths are relative to `E:/tbib/FetchJet` unless they start with a drive letter.
Short names used below:

- `TE/` = `sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/feature/torrent_engine/`
- `JA/` =
  `sharedLogic/src/jvmAndroidMain/kotlin/com/michelle/raouf/fetch_jet/feature/torrent_engine/`
- `IOS/` = `sharedLogic/src/iosMain/kotlin/com/michelle/raouf/fetch_jet/feature/torrent_engine/`
- lt4j = libtorrent4j 2.1.0-39 (its Java sources were read from the Gradle cache:
  `C:/Users/miche/.gradle/caches/modules-2/files-2.1/org.libtorrent4j/libtorrent4j/2.1.0-39/975bcab1ce7e408f6c9b2b74b5c961abfcf3e413/libtorrent4j-2.1.0-39-sources.jar`,
  file `org/libtorrent4j/SessionManager.java`)

## 0. Summary and recommendation

- The torrent engine owns 2 of the 44 missing `expect` declarations: `createTorrentBackend()`
  (`TE/TorrentBackend.kt:78`) and `streamUrlToFile(...)` (`TE/TorrentStreamer.kt:18-25`).
- The contract is pull-only: no callbacks, no events. Common code polls `progress(taskId)` every 700
  ms per
  running torrent. This fits a plain C DLL that never calls back into Kotlin.
- `iosApp/FJTorrent/FJTorrent.mm` (424 lines) is the right model. Its libtorrent calls can be reused
  almost
  line for line; the Objective-C shell (NSString / NSData / NSArray / NSError / ARC / GCD) has to be
  replaced.
  It lacks six things the desktop needs (section 3.3).
- Proposed: `fjtorrent.dll` with a plain C API (section 4), keyed by the app's task id, settings
  passed as
  `name=value` text using libtorrent's own setting names, all outputs through caller buffers or
  opaque
  handles. No struct crosses the boundary, so Kotlin/Native needs no cinterop `.def` and no change
  to
  `sharedLogic/build.gradle.kts`: only `LoadLibraryExW` + `GetProcAddress` from `platform.windows`.
- Build: this PC already has everything except the libtorrent source. `C:/msys64/clang64` holds
  clang 23.1.4,
  libc++, lld, CMake 4.4.5, Ninja 1.13.2, **Boost 1.92.0** and **OpenSSL 3.6.5 (static and
  shared)**.
  Recommended route B: one download of 6,706,453 bytes (`libtorrent-rasterbar-2.1.2.tar.gz`), result
  is one
  self-contained `fjtorrent.dll` (estimate 7-10 MB) with no extra DLLs.
- libtorrent 2.1 (the version iOS is pinned to) no longer uses memory-mapped disk IO on Windows by
  default (verified in the pinned commit's `src/session.cpp`), which removes the best-known
  libtorrent 2.0 RAM problem.
- Idle RAM: the DLL is not loaded until the first torrent action. After the last torrent stops, the
  session is
  destroyed. Two decisions are the owner's: what to do with finished torrents that keep seeding
  (they keep the
  session alive today), and whether the desktop speed tuning (800 connections, 16 MiB disk queue)
  may exceed
  the 50 MB budget while a torrent downloads.

## 1. The `TorrentBackend` contract

### 1.1 Types (`TE/TorrentBackend.kt`)

| Lines | Declaration                                                                                                                                                                                                                                                                                                                             |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 6-13  | `data class TorrentMetadata(val name: String, val files: List<TorrentFileEntry>, val totalSize: Long, val infoHash: String, val torrentBytes: ByteArray)`                                                                                                                                                                               |
| 16-30 | `data class TorrentProgress(val downloadedBytes: Long, val wantedBytes: Long, val downloadRate: Long, val uploadRate: Long, val peers: Int, val seeds: Int, val isFinished: Boolean, val isSeeding: Boolean, val isPaused: Boolean, val hasMetadata: Boolean, val fileProgress: List<Long>, val stateText: String, val error: String?)` |
| 37-76 | `interface TorrentBackend`                                                                                                                                                                                                                                                                                                              |
| 78    | `expect fun createTorrentBackend(): TorrentBackend`                                                                                                                                                                                                                                                                                     |

`TorrentFileEntry` is in
`sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/domain/model/DownloadTask.kt:26-34`:
`index: Int, path: String, size: Long, isSelected: Boolean = true, downloadedBytes: Long = 0, isCompleted: Boolean = false, priority: Int = 0`
(0 normal, 1 high, -1 low).

### 1.2 Methods

| Line  | Signature                                                                                                                                                                                                         | What the caller expects                                                                                                                                                                                            |
|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 38    | `val isSupported: Boolean`                                                                                                                                                                                        | Cheap, called from the UI state refresh (`presentation/dashboard/DashboardTools.kt:66`, `DashboardSettingsEvents.kt:183`) and before every start (`TE/TorrentDownloader.kt:82`, `:188`). Must not load the engine. |
| 41    | `fun engineStatus(): String`                                                                                                                                                                                      | One localized line for Settings (`DashboardTools.kt:63`, `DashboardSettingsEvents.kt:180`). Must not load the engine.                                                                                              |
| 44    | `suspend fun installEngine(): Result<Unit>`                                                                                                                                                                       | Called by the "Update tools" button (`DashboardSettingsEvents.kt:177`).                                                                                                                                            |
| 50    | `suspend fun fetchMetadata(magnetUri: String?, torrentBytes: ByteArray?): TorrentMetadata`                                                                                                                        | Blocking is allowed (both platforms run it in `withContext(Dispatchers.IO)`). `.torrent` bytes win over the magnet. Throws on failure.                                                                             |
| 56-65 | `suspend fun add(taskId: String, torrentBytes: ByteArray, savePath: String, selected: Set<Int>, priorities: Map<Int, Int>, disableUpload: Boolean, downloadLimitBytesPerSec: Long, uploadLimitBytesPerSec: Long)` | Adds and starts, or re-applies priorities and resumes when `taskId` is already in the session. Throws on failure.                                                                                                  |
| 67    | `fun pause(taskId: String)`                                                                                                                                                                                       | Not suspending: must return fast. Unknown id = no-op.                                                                                                                                                              |
| 68    | `fun resume(taskId: String)`                                                                                                                                                                                      | Same. Never called by common code today (resume goes through `add`).                                                                                                                                               |
| 71    | `fun remove(taskId: String, deleteFiles: Boolean)`                                                                                                                                                                | Common code always passes `deleteFiles = false` (`TE/TorrentDownloader.kt:224`); files are deleted by `QueueManagerRepository.removeTask` itself.                                                                  |
| 72    | `fun setFileSelection(taskId: String, selected: Set<Int>, priorities: Map<Int, Int>)`                                                                                                                             | Unknown id = no-op.                                                                                                                                                                                                |
| 73    | `fun setLimits(taskId: String, downloadBytesPerSec: Long, uploadBytesPerSec: Long)`                                                                                                                               | 0 = unlimited.                                                                                                                                                                                                     |
| 74    | `fun progress(taskId: String): TorrentProgress?`                                                                                                                                                                  | `null` = "nothing yet, ask again".                                                                                                                                                                                 |
| 75    | `fun shutdown()`                                                                                                                                                                                                  | **Never called** by common, desktop or Android code (only defined; grep found no caller). The JVM session lives until the process exits.                                                                           |

### 1.3 Data rules the backend must keep

- **File index** = libtorrent's file index. Pad files are left out of `TorrentMetadata.files`, so
  indices can
  have gaps (`JA/Libtorrent4jBackend.kt:224-225`). `fileProgress` is indexed by the same libtorrent
  index (`TE/TorrentDownloader.kt:328`: `p.fileProgress.getOrNull(f.index)`).
- **Paths**: every segment goes through `sanitizePathSegment` (`TE/BencodeParser.kt:215`), `\`
  becomes `/`,
  blank segments are dropped (`JA/Libtorrent4jBackend.kt:227-231`). Name:
  `sanitizePathSegment(name)` or
  `"Torrent_Download"` when blank (`:242`).
- **`totalSize`** on desktop = `TorrentInfo.totalSize()` (pad files included, `:244`). iOS sums the
  non-pad
  files (`iosApp/FJTorrent/FJTorrent.mm:80-92`). Windows should follow the desktop.
- **`infoHash`** = hex of the v1 hash on desktop (`:245`); iOS uses `info_hash_t::get_best()`
  (`FJTorrent.mm:45-47`). Not used by common code for anything but the data class.
- **`torrentBytes`**: for a `.torrent` the same bytes that came in (`:218`); for a magnet the bytes
  made by
  `write_torrent_file_buf` from the full add-params (lt4j `SessionManager.java:671`), so the
  magnet's `tr=`
  trackers survive. `TorrentDownloader` stores them in `<settings>/torrents/<taskId>.torrent`
  (`TE/TorrentDownloader.kt:173-183`) and feeds them back after a restart (`:113-124`).
- **Priorities** (`JA/Libtorrent4jBackend.kt:328-342`): array of `numFiles` entries (pad files
  included);
  index not in `selected` -> 0 (ignore); priority > 0 -> 7 (top); < 0 -> 1 (low); else 4 (default).
- **Limits** (`:370-381`): 0 = unlimited; values are clamped to `0..Int.MAX_VALUE`. When the task
  has
  `disableUpload`, the upload limit is always `32 * 1024` B/s whatever is passed.
- **Progress mapping** (`:383-420`): `downloadedBytes = total_wanted_done`,
  `wantedBytes = total_wanted`,
  rates = payload rates, `peers = num_peers`, `seeds = num_seeds`, `isPaused` =
  `torrent_flags::paused`,
  `error` = `errc.message()` or null.
- **`stateText`** must be exactly one of `"Checking files"`, `"Checking resume data"`,
  `"Fetching metadata"`,
  `"Downloading"`, `"Finished"`, `"Seeding"`, `"Connecting"` (`:409-417`). Common code shows
  `"${p.stateText} · ${p.peers} peers (${p.seeds} seeds)"` (`TE/TorrentDownloader.kt:363`) and
  `localizeStep`
  translates by exact lower-case match (`core/i18n/start/StepText.kt:78-88`).
- **Error texts** used today: `"A magnet link or a .torrent file is required"`,
  `"Could not fetch torrent metadata for this magnet link (no peers answered within 60 s). Check the link or your network."`,
  `"libtorrent did not accept the torrent"`.

### 1.4 How `TorrentDownloader` drives it (`TE/TorrentDownloader.kt`)

- One coroutine per task on `CoroutineScope(Dispatchers.IO + SupervisorJob())` (`:60`, `:196-201`).
- `runTorrent` (`:234-384`): `resolveMetadata` (`:110-145`) -> save the copy -> build selection ->
  **own HTTP pass over the web seeds first** (`:281-295`, `downloadViaWebSeeds` `:431-553`, uses
  `streamUrlToFile`) -> `backend.add(...)` (`:297-306`) -> poll loop (`:310-367`).
- Poll loop, every `POLL_MS = 700` (`:604`):
    - `progress == null` -> wait and retry, **forever** (`:311-315`). A backend that keeps returning
      null leaves
      the task in "Connecting" until it is cancelled.
    - `error != null` -> exception -> `backend.pause(taskId)` and status `ERROR` (`:316`,
      `:370-382`).
    - limits are re-read from the providers each pass; `setLimits` is called on the first pass and
      on every
      change (`:319-324`).
    - `isFinished` -> status `COMPLETED`; with `disableUpload` it calls `backend.pause(taskId)`;
      then the loop **returns** (`:335-352`). With upload enabled the torrent keeps seeding inside
      the session with nobody
      polling it, until the app exits or the task is removed.
- `pause` (`:203-214`): cancels the job, then `backend.pause`. Called from whatever context the UI
  uses, so it
  must not block.
- `cancel` (`:222-232`): `backend.remove(taskId, false)`, deletes `<taskId>.torrent`.
- `updateFileSelection` / `updateFilePriority` -> `applySelection` (`:584-594`):
  `backend.setFileSelection`,
  and relaunches the runner when something is selected, nothing runs and the task is not paused.
- `MAX_TORRENT_FILE_BYTES = 20 * 1024 * 1024` for downloaded `.torrent` files (`:605`).

### 1.5 Threading

- `fetchMetadata` and `add` run on `Dispatchers.IO` and may block (JVM `add` waits up to 5 s for the
  handle,
  `JA/Libtorrent4jBackend.kt:280-284`; `fetchMetadata` up to 60 s).
- Everything else is a plain function called from several threads at once (one poller per torrent,
  UI
  actions). JVM uses a `ConcurrentHashMap` (`:191`), iOS an `NSRecursiveLock`
  (`iosApp/iosApp/NativeTorrentBridge.swift:19`).
- No listener, no flow, no callback goes from the backend to common code.

### 1.6 The second expect: `streamUrlToFile`

`TE/TorrentStreamer.kt:18-25`. The iOS actual (`IOS/TorrentStreamer.ios.kt`, 29 lines) is the model:
build a
ktor client with `webSeedDefaults()` (`TE/TorrentStreamer.kt:28-35`) and call the common
`streamUrlWithClient` (`:37-110`). The Windows actual is the same with `HttpClient(WinHttp)`
(`libs.ktor.client.winhttp` is already a `mingwX64Main` dependency,
`sharedLogic/build.gradle.kts:104-108`).
The JVM actual's `HttpURLConnection` branch
(`sharedLogic/src/jvmMain/.../torrent_engine/TorrentStreamer.jvm.kt:35-105`)
is not needed.

## 2. What `Libtorrent4jBackend` does beyond wrapping

File: `JA/Libtorrent4jBackend.kt` (505 lines). Factory: `JA/TorrentBackend.jvmAndroid.kt:6-14`
(`tempDir = <temp>/torrent-metadata`, `toolsDir = <settings>/tools`).

### 2.1 Lazy start

- `session` is `by lazy` (`:54-132`): the native library and the session start on the first use.
- `isSupported` / `engineStatus` only look at files (`:38-40`, `JA/TorrentNativeLoader.kt:71-85`).
  `engineStatus` returns `sysTorrentEngineAvailable` ("Ready: BitTorrent engine available (loads on
  first
  torrent)") before the load, `sysTorrentEngineReadyVersion(v)` after,
  `sysTorrentEngineFailedToLoad(msg)`
  on a failed load, `sysTorrentEngineMissing` when the file is absent (strings:
  `core/i18n/data_provider/AppLocalization.kt:651-656`).
- `TorrentNativeLoader` (237 lines) extracts `libtorrent4j.dll` (13,076,480 bytes, verified) from
  the jar into
  `<settings>/tools/2.1.0-39-libtorrent4j.dll` at each start, or downloads the jar from Maven
  Central with a
  SHA-1 check (`:159-204`). None of that is needed on the native app: the DLL ships next to the exe.

### 2.2 Session settings (`:62-125`)

| Setting (libtorrent name)                                                  | Value                                                                                                                           |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `enable_dht`, `enable_lsd`, `enable_upnp`, `enable_natpmp`                 | true                                                                                                                            |
| `dht_bootstrap_nodes`                                                      | `dht.libtorrent.org:25401,router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.aelitis.com:6881` |
| `listen_interfaces`                                                        | `0.0.0.0:<port>,[::]:<port>`, port = `20000 + (nanoTime % 40000)` (`:74-79`)                                                    |
| `download_rate_limit`, `upload_rate_limit`                                 | 0 (no global cap)                                                                                                               |
| `connections_limit`                                                        | 800                                                                                                                             |
| `connection_speed`                                                         | 250                                                                                                                             |
| `torrent_connect_boost`                                                    | 100                                                                                                                             |
| `max_peerlist_size`                                                        | 4000                                                                                                                            |
| `peer_connect_timeout`                                                     | 10                                                                                                                              |
| `request_timeout`                                                          | 20                                                                                                                              |
| `active_downloads` / `active_seeds` / `active_limit`                       | 10 / 5 / 20                                                                                                                     |
| `unchoke_slots_limit`                                                      | 16                                                                                                                              |
| `send_buffer_watermark`                                                    | 3145728                                                                                                                         |
| `send_buffer_low_watermark`                                                | 524288                                                                                                                          |
| `max_queued_disk_bytes`                                                    | 16777216                                                                                                                        |
| `aio_threads`                                                              | 8                                                                                                                               |
| `mixed_mode_algorithm`                                                     | `prefer_tcp` (enum value 0)                                                                                                     |
| `enable_outgoing_utp`, `enable_incoming_utp`                               | true                                                                                                                            |
| `announce_to_all_trackers`, `announce_to_all_tiers`, `prefer_udp_trackers` | true                                                                                                                            |
| `max_web_seed_connections`                                                 | 8                                                                                                                               |
| `urlseed_wait_retry`                                                       | 10                                                                                                                              |
| `urlseed_pipeline_size`                                                    | 10                                                                                                                              |

Added silently by lt4j's `SessionManager.start` (`SessionManager.java:116-159`):

- `alert_mask` = all categories minus the six log categories (`:135`, `:1122-1136`).
- `max_metadata_size` = `2 * 1024 * 1024` when not set (`:138-140`).
- a port filter that blocks peers on ports 0-79, 81-442 and 444-1023 (`:150-154`).
- an "alertsLoop" thread: `wait_for_alert_ms(500)` + `pop_alerts` (`:52`, `:1154-1230`).

Not set on desktop (libtorrent defaults stay): user agent, peer fingerprint, encryption policy,
`validate_https_trackers`. iOS sets all four (section 3.1).

### 2.3 Proxy (`:128-164`)

- Collected from `NetworkSettings.proxy` (a `StateFlow<ProxySettings>`,
  `core/network/NetworkSettings.kt:31-50`, `:90`) in its own scope right after the session starts,
  so it is
  applied once at start and again on every change, live.
- `proxy_type`: off -> `none` (0); SOCKS5 + login -> `socks5_pw` (3); SOCKS5 -> `socks5` (2);
  HTTP + login -> `http_pw` (5); HTTP -> `http` (4).
- `proxy_hostname`, `proxy_port`, `proxy_username`, `proxy_password` from the settings.
- `proxy_hostnames`, `proxy_peer_connections`, `proxy_tracker_connections` = true.
- **UDP rule** (`:155-160`): `udp = !(proxy.isOn && proxy.kind == HTTP)`; `enable_dht`,
  `enable_lsd`,
  `enable_outgoing_utp`, `enable_incoming_utp` are all set to `udp`. So an HTTP proxy turns DHT, LSD
  and uTP
  off; SOCKS5 and "no proxy" turn them on again. UPnP / NAT-PMP are not touched.

### 2.4 Metadata

- `.torrent`: `TorrentInfo.bdecode(bytes)` -> `toMetadata` (`:218-248`).
- Magnet (`:203-214`): `withPublicTrackers` (`:170-182`) appends the 7 `PUBLIC_TRACKERS`
  (`:495-503`) as
  `&tr=` only when the magnet has no `tr=` at all, then `session.fetchMagnet(uri, 60, tempDir)`.
- lt4j `fetchMagnet` (`SessionManager.java:632-748`): `parse_magnet_uri`; if the hash is already in
  the
  session it reuses that torrent; else it adds it with save path = `tempDir`, flags
  `-auto_managed +upload_mode +stop_when_ready` (`:719-721`), resumes it, waits for
  `metadata_received`, asks
  for `save_resume_data(save_info_dict)`, turns the answer into bytes with `write_torrent_file_buf`,
  and
  removes the temporary torrent. Timeout -> returns null -> the "no peers answered" exception.

### 2.5 Adding (`:252-310`)

1. Known `taskId` with a valid handle -> `prioritizeFiles` + `resume()` and return (`:268-275`).
2. `session.download(ti, dir, null, prio, null, flags)` = `async_add_torrent` with **no resume
   data**; when a
   torrent with the same hash already exists lt4j only re-applies the priorities
   (`SessionManager.java:487-565`).
3. Poll `session.find(infoHash)` up to 50 x 100 ms (`:279-286`).
4. `unsetFlags(AUTO_MANAGED)` (`:287`), `prioritizeFiles` again.
5. **Trackers and web seeds through `load_torrent_parsed`** (`:289-296`, `:313-326`): libtorrent 2.1
   keeps a
   `.torrent`'s `announce` / `announce-list` / `url-list` in `add_torrent_params`, not in
   `torrent_info`;
   adding from `TorrentInfo` alone drops them, so they are read again and added with `addTracker` /
   `addUrlSeed`. A C++ wrapper that adds the `add_torrent_params` returned by `load_torrent_buffer`
   does not
   have this problem (FJTorrent already does it that way).
6. `PUBLIC_TRACKERS` not already present (lower-case compare) are added, then `forceReannounce()`
   and
   `forceDHTAnnounce()` (`:299-306`).
7. `setLimits`, `resume()` (`:308-309`).

### 2.6 Things the task list asks about that do not exist today

- **Resume data**: not saved, not loaded. A paused torrent stays in the session (no recheck on
  resume). After
  an app restart the torrent is added fresh and libtorrent rechecks every file. `TorrentDownloader`
  relies on
  that recheck after its own HTTP pass (`TE/TorrentDownloader.kt:293`).
- **Sequential download**: not used anywhere (grep for `sequential` in Kotlin, Swift and FJTorrent:
  nothing).
- **Alerts**: the app consumes none. Only lt4j's own loop reads them.
- **Session state** (DHT nodes) is not saved between runs.

### 2.7 Stats and diagnostics

- `progress()` calls `handle.status()` and `handle.fileProgress()` on every poll (`:386-391`).
- In verbose builds one line per torrent every 15 s (`:424-483`): state, peers, seeds, web seed list
  and
  connections, `numConnections`, `connectCandidates`, `listPeers`, `listSeeds`, rates,
  `isDhtRunning`,
  `dhtNodes`, listen port, external address, upload limit, and per tracker the best endpoint's
  message /
  fails / ok / updating.
- CLAUDE.md records a `listenPort=0` seen twice on Windows; iOS sets `max_retry_port_bind = 10`,
  desktop does
  not.

### 2.8 `shutdown` (`:485-491`)

Clears the map and calls `session.stop()`, which sleeps 750 ms, joins the alert thread and deletes
the native
session (`SessionManager.java:174-217`). Nobody calls it.

## 3. The FJTorrent wrapper (iOS)

Files: `iosApp/FJTorrent/include/FJTorrent.h` (96 lines), `iosApp/FJTorrent/FJTorrent.mm` (424
lines),
`iosApp/FJTorrent/CMakeLists.txt` (12 lines), `iosApp/iosApp/NativeTorrentBridge.swift` (332 lines),
`IOS/TorrentBackend.ios.kt` (211 lines), `IOS/IosTorrentBridge.kt` (98 lines),
`scripts/build-libtorrent-ios.command` (97 lines).

### 3.1 API (`FJTorrent.h`)

| Lines  | Declaration                                                                                                                                                                                        | libtorrent calls (`FJTorrent.mm` lines)                                                                                                                                                                                                                                                                                                                           |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 5-13   | `FJTorrentState`: Connecting 0, CheckingFiles 1, CheckingResumeData 2, DownloadingMetadata 3, Downloading 4, Finished 5, Seeding 6                                                                 |                                                                                                                                                                                                                                                                                                                                                                   |
| 15-20  | `FJFilePriority`: DontDownload 0, Low 1, Default 4, Top 7                                                                                                                                          |                                                                                                                                                                                                                                                                                                                                                                   |
| 22-26  | `FJTorrentFile`: `index`, `path`, `size`                                                                                                                                                           |                                                                                                                                                                                                                                                                                                                                                                   |
| 28-35  | `FJTorrentInfo`: `name`, `infoHash`, `fileCount`, `files`, `totalSize`, `torrentData`                                                                                                              | built in `infoWith:hashes:data:` (73-95) from `ti.name()`, `ti.layout()`, `pad_file_at`, `file_path`, `file_size`                                                                                                                                                                                                                                                 |
| 37-51  | `FJTorrentStatus`: `state`, `isPaused`, `isFinished`, `isSeeding`, `hasMetadata`, `errorMessage`, `totalWanted`, `totalWantedDone`, `downloadRate`, `uploadRate`, `peers`, `seeds`, `fileProgress` |                                                                                                                                                                                                                                                                                                                                                                   |
| 56     | `+libtorrentVersion`                                                                                                                                                                               | `lt::version_str` (124-126)                                                                                                                                                                                                                                                                                                                                       |
| 60-63  | `-initWithListenPort:userAgent:peerFingerprint:error:`                                                                                                                                             | settings pack (135-153): `listen_interfaces`, `max_retry_port_bind=10`, `user_agent`, `peer_fingerprint`, DHT/LSD/UPnP/NAT-PMP/uTP on, `out_enc_policy=in_enc_policy=pe_enabled`, `validate_https_trackers=false`, `active_limit=20`, `active_downloads=10`, `active_seeds=10`, `alert_mask=alert_category::error`; `lt::session(lt::session_params(pack))` (154) |
| 65     | `+infoFromTorrentData:error:`                                                                                                                                                                      | `lt::load_torrent_buffer(span, ec, load_torrent_limits{})` (162-175)                                                                                                                                                                                                                                                                                              |
| 67     | `-findMagnet:`                                                                                                                                                                                     | `parse_magnet_uri` + `find_torrent(v1)` / `find_torrent(v2)` (192-210)                                                                                                                                                                                                                                                                                            |
| 69     | `-findTorrentData:`                                                                                                                                                                                | `load_torrent_buffer` + the same lookup (212-221)                                                                                                                                                                                                                                                                                                                 |
| 71     | `-addMagnet:savePath:error:`                                                                                                                                                                       | `parse_magnet_uri`, `save_path`, flags `&= ~(paused                                                                                                                                                                                                                                                                                                               | auto_managed)`, `add_torrent(atp, ec)` (223-248) |
| 73-76  | `-addTorrentData:savePath:filePriorities:error:`                                                                                                                                                   | `load_torrent_buffer(span)` (keeps trackers and web seeds), `save_path`, same flags, `file_priorities`, `add_torrent(atp, ec)` (250-282)                                                                                                                                                                                                                          |
| 78     | `-statusFor:`                                                                                                                                                                                      | `h.status()`, `h.file_progress()` when it has metadata (284-336)                                                                                                                                                                                                                                                                                                  |
| 80     | `-infoFor:`                                                                                                                                                                                        | `h.torrent_file()`, trackers copied from `h.trackers()`, `write_torrent_file_buf(atp, write_flags::allow_missing_piece_layer)` (338-356)                                                                                                                                                                                                                          |
| 82, 84 | `-pause:`, `-resume:`                                                                                                                                                                              | `h.pause()`, `h.resume()` (358-366)                                                                                                                                                                                                                                                                                                                               |
| 86     | `-remove:deleteFiles:`                                                                                                                                                                             | `remove_torrent(h, delete_files or {})` (368-378)                                                                                                                                                                                                                                                                                                                 |
| 88     | `-setFilePriorities:for:`                                                                                                                                                                          | `h.prioritize_files(vector<download_priority_t>)` (380-391)                                                                                                                                                                                                                                                                                                       |
| 90     | `-setDownloadLimit:uploadLimit:for:`                                                                                                                                                               | `set_download_limit` / `set_upload_limit`, clamped to `0..INT32_MAX` (393-403)                                                                                                                                                                                                                                                                                    |
| 92     | `-shutdown`                                                                                                                                                                                        | `session->pause()`, then `delete` on a background queue (405-418)                                                                                                                                                                                                                                                                                                 |

State: `std::unique_ptr<lt::session>`, `std::map<std::string, lt::torrent_handle>` keyed by the
info-hash hex,
one `std::recursive_mutex` (118-122). Every libtorrent call is inside `try/catch`.

### 3.2 What the Swift and Kotlin layers add

- `NativeTorrentBridge.swift`: lazy session with a random port 20000-59999, user agent
  `"FetchJet/1.0 libtorrent/<version>"`, fingerprint `"-FJ1000-"` (271-287); `taskId -> key` map
  (14-20);
  magnet fetch = add the magnet as a normal torrent into
  `<Application Support>/FetchJet-torrent-engine/metadata-scratch`,
  poll `info(key)` every 0.5 s until the deadline, then `remove(key, deleteFiles: true)` (72-103);
  priorities
  mapping (290-312); `stateText` becomes `"Paused"` when paused and `"Error"` on error (243), which
  the desktop
  does not do.
- `IOS/TorrentBackend.ios.kt`: sanitizing (63-71), its own `withPublicTrackers` with 5 trackers
  (160-172,
  the desktop has 7), `MAGNET_TIMEOUT_SECONDS = 60` (189).
- **Difference found** (not a Windows task, but do not copy it): Swift applies the 32 KiB/s
  "download only"
  upload cap only inside `add` (line 128). `setLimits` (200-208) passes the raw value, and
  `TorrentDownloader`
  calls `setLimits` on its first poll, so on iOS the cap is replaced by the user's limit (0 =
  unlimited) about
  0.7 s after the start. The desktop keeps the cap because it remembers `disableUpload` per task.

### 3.3 Reuse for a Windows C API

Reusable as they are (pure C++): `FJHex`, `FJKey` (33-47), the bodies of `infoFromTorrentData`,
`existing`,
`findMagnet`, `findTorrentData`, `addMagnet`, `addTorrentData`, `statusFor`, `infoFor`, `pause`,
`resume`,
`remove`, `setFilePriorities`, `setDownloadLimit`, the state switch and the handle map with its
mutex. That is
about 220 of the 424 lines.

To replace: all Objective-C types and classes (property declarations 53-115, `FJString`,
`FJSetError`,
`FJSpan` over `NSData`), `NSError**` error passing, the `dispatch_async` deletion in `shutdown` (use
`session::abort()` and let the returned `session_proxy` die on a worker thread, or a bounded join).

Missing for the desktop, to add in the Windows wrapper:

1. Settings from the caller (the tuned list of 2.2) and live `apply_settings` (proxy, section 2.3).
2. Extra trackers after add + `force_reannounce()` + `force_dht_announce()`.
3. Magnet fetch that does not start downloading data: lt4j's flags `upload_mode | stop_when_ready`
   without
   `auto_managed`, and a way to cancel it (iOS blocks a thread for up to 60 s).
4. The "download only" cap remembered per torrent.
5. A diagnostics line (section 2.7), the listen port, DHT state.
6. Optional: resume data (section 6.2 explains why Windows wants it) and the sequential flag.

`CMakeLists.txt` line 7 (`add_subdirectory(${LIBTORRENT_SOURCE_DIR} libtorrent)`) and line 12
(`target_link_libraries(FJTorrent PUBLIC torrent-rasterbar)`) are the same pattern the Windows build
uses.

### 3.4 The pinned libtorrent (`scripts/build-libtorrent-ios.command`)

- Line 16: `LIBTORRENT_COMMIT="6da363d2994f17c0b3c0450d124cf73a31a73847"`. Verified on
  `api.github.com/repos/arvidn/libtorrent/commits/<sha>`: dated 2026-09-24, message
  `Merge branch 'RC_2_0' into RC_2_1`. So it is the 2.1 release branch, one day before the release
  `v2.1.2`
  (published 2026-09-25, target `RC_2_1`). Whether the tag `v2.1.2` is exactly this commit was **not
  verified**.
- Lines 25-36: `git fetch --depth 1 origin <commit>` +
  `git submodule update --init --recursive --depth 1`
  into `iosApp/ThirdParty/libtorrent` (that folder does not exist on this PC).
- Line 39: `FLAGS="-DTORRENT_HAVE_MMAP=0 -DNDEBUG"`.
- Lines 43-61, CMake options: `-DCMAKE_CXX_STANDARD=17`, `-DBOOST_ROOT=<brew prefix>`,
  `-Ddeprecated-functions=OFF`, `-Dwebtorrent=OFF`,
  `-Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF`,
  `-DBUILD_SHARED_LIBS=OFF`.
- Boost: only the headers from Homebrew (lines 6 and 21: `brew install cmake boost`, check for
  `include/boost`). No Boost library is linked. OpenSSL is not mentioned: the iOS build takes
  whatever CMake
  finds (none on iOS, so no HTTPS trackers there; `validate_https_trackers=false` has no effect
  then).
- Line 82: `libtool -static` merges `libFJTorrent.a` and `libtorrent-rasterbar.a`.

Read from the pinned commit's `CMakeLists.txt` (raw.githubusercontent.com, verified):

- `cmake_minimum_required(VERSION 3.20.0)`; options `BUILD_SHARED_LIBS` (ON), `static_runtime`
  (OFF),
  `deprecated-functions`, `webtorrent`, `i2p`, `logging`, `extensions`, `mutable-torrents`,
  `streaming`
  (all ON by default), `encryption` (ON), `gnutls` (OFF).
- Boost: `find_public_dependency(Boost REQUIRED)` + `Boost::headers`. Compiled Boost.System is only
  asked for
  when Boost < 1.69. So **headers only**.
- TLS: `find_public_dependency(OpenSSL)` (not REQUIRED). Found -> `TORRENT_USE_OPENSSL`. Not
  found -> GnuTLS,
  then plain crypto; the build still works, without HTTPS trackers and HTTPS web seeds.
- Windows: `_WIN32_WINNT=0x0A00`, `BOOST_ASIO_ENABLE_CANCELIO`, `WIN32_LEAN_AND_MEAN`; MSVC also
  gets
  `BOOST_ALL_NO_LIB`; link libraries `bcrypt mswsock ws2_32 iphlpapi` (+ `dbghelp crypt32`).
- Sources include `deps/try_signal/*.cpp` (a submodule; it is inside the release tarball).
- Disk backends compiled in: mmap, **pread** (`pread_disk_io.cpp`, `pread_storage.cpp`), posix.

## 4. Proposed C API: `fjtorrent.h`

### 4.1 Design rules

1. **Plain C, UTF-8, x64.** Only `int32_t`, `int64_t`, pointers to bytes and opaque handles. No
   struct is
   passed, so the Kotlin side needs no cinterop definition and there is no packing or CRT question.
2. **No callbacks.** libtorrent's threads never enter the caller. Kotlin/Native code is only ever
   run on
   Kotlin threads. Everything is polled, which is what the contract does anyway.
3. **Keyed by task id.** The DLL keeps `task_id -> torrent_handle` (and the "download only" flag).
   Two task
   ids with the same info-hash share one handle; the torrent leaves the session when the last id is
   removed.
4. **Settings as text.** `name=value` lines separated by `\n`, names are libtorrent's own
   (`lt::setting_by_name`), values are decimal integers, `0`/`1` for booleans, raw text for strings
   (a value
   ends at the line end; `\r` and `\n` cannot be part of a value). Unknown names are skipped and
   counted. The
   Kotlin side owns the list in 2.2 and the proxy lines in 2.3; the DLL owns `alert_mask`, the port
   filter
   and the disk backend.
5. **Memory.** The caller never frees DLL memory with its own allocator and the DLL never frees
   caller
   memory. Text and numbers go into caller buffers; variable-size results are opaque objects with
   accessors
   and a `*_free` function. Pointers returned by accessors live until that object is freed.
6. **Text out convention.** `char* out, int32_t cap`: the DLL writes at most `cap - 1` bytes plus a
   NUL and
   returns the full length needed (like `snprintf`). `out` may be NULL with `cap` 0.
7. **Errors.** `int32_t` result: 0 ok, 1 pending, negative = error code. Functions that can fail
   with a
   reason take `char* err, int32_t err_cap` and write an English message there.

### 4.2 The header

To be created as `desktopNative/torrent/include/fjtorrent.h` (new folder, no existing file is
touched).
It carries no comments (owner's rule); the tables after it are the documentation.

```c
#ifndef FJTORRENT_H
#define FJTORRENT_H

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32) && defined(FJT_BUILD)
#define FJT_API __declspec(dllexport)
#elif defined(_WIN32)
#define FJT_API __declspec(dllimport)
#else
#define FJT_API
#endif

#if defined(_WIN32)
#define FJT_CALL __cdecl
#else
#define FJT_CALL
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define FJT_ABI_VERSION 1

#define FJT_OK 0
#define FJT_PENDING 1
#define FJT_E_ARG (-1)
#define FJT_E_NOT_FOUND (-2)
#define FJT_E_PARSE (-3)
#define FJT_E_SESSION (-4)
#define FJT_E_TIMEOUT (-5)
#define FJT_E_FAILED (-6)

#define FJT_PRIO_SKIP 0
#define FJT_PRIO_LOW 1
#define FJT_PRIO_DEFAULT 4
#define FJT_PRIO_TOP 7

#define FJT_ADD_DISABLE_UPLOAD 1
#define FJT_ADD_SEQUENTIAL 2

#define FJT_STATE_CONNECTING 0
#define FJT_STATE_CHECKING_FILES 1
#define FJT_STATE_CHECKING_RESUME_DATA 2
#define FJT_STATE_DOWNLOADING_METADATA 3
#define FJT_STATE_DOWNLOADING 4
#define FJT_STATE_FINISHED 5
#define FJT_STATE_SEEDING 6

#define FJT_F_PAUSED 1
#define FJT_F_FINISHED 2
#define FJT_F_SEEDING 4
#define FJT_F_HAS_METADATA 8
#define FJT_F_ERROR 16
#define FJT_F_NEED_SAVE_RESUME 32

#define FJT_ST_STATE 0
#define FJT_ST_FLAGS 1
#define FJT_ST_TOTAL_WANTED 2
#define FJT_ST_TOTAL_WANTED_DONE 3
#define FJT_ST_DOWNLOAD_RATE 4
#define FJT_ST_UPLOAD_RATE 5
#define FJT_ST_PEERS 6
#define FJT_ST_SEEDS 7
#define FJT_ST_NUM_FILES 8
#define FJT_ST_CONNECTIONS 9
#define FJT_ST_CONNECT_CANDIDATES 10
#define FJT_ST_LIST_PEERS 11
#define FJT_ST_LIST_SEEDS 12
#define FJT_ST_UPLOAD_LIMIT 13
#define FJT_ST_TOTAL_UPLOAD 14
#define FJT_ST_RESERVED 15
#define FJT_ST_COUNT 16

#define FJT_SI_LISTEN_PORT 0
#define FJT_SI_DHT_RUNNING 1
#define FJT_SI_DHT_NODES 2
#define FJT_SI_TORRENTS 3
#define FJT_SI_ACTIVE_TORRENTS 4
#define FJT_SI_MAGNET_JOBS 5
#define FJT_SI_RESERVED_6 6
#define FJT_SI_RESERVED_7 7
#define FJT_SI_COUNT 8

typedef struct fjt_session fjt_session;
typedef struct fjt_meta fjt_meta;
typedef struct fjt_magnet fjt_magnet;
typedef struct fjt_buf fjt_buf;

FJT_API int32_t FJT_CALL fjt_abi_version(void);
FJT_API int32_t FJT_CALL fjt_version_text(char* out, int32_t cap);

FJT_API fjt_session* FJT_CALL fjt_session_create(const char* settings, char* err, int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_session_apply_settings(fjt_session* session, const char* settings);
FJT_API int32_t FJT_CALL fjt_session_info(fjt_session* session, int64_t* out, int32_t count);
FJT_API void FJT_CALL fjt_session_destroy(fjt_session* session, int32_t timeout_ms);

FJT_API fjt_meta* FJT_CALL fjt_meta_parse(const uint8_t* torrent, int64_t size, char* err, int32_t err_cap);
FJT_API const char* FJT_CALL fjt_meta_name(const fjt_meta* meta);
FJT_API const char* FJT_CALL fjt_meta_info_hash(const fjt_meta* meta);
FJT_API int64_t FJT_CALL fjt_meta_total_size(const fjt_meta* meta);
FJT_API int32_t FJT_CALL fjt_meta_num_files(const fjt_meta* meta);
FJT_API int32_t FJT_CALL fjt_meta_entry_count(const fjt_meta* meta);
FJT_API int32_t FJT_CALL fjt_meta_entry_index(const fjt_meta* meta, int32_t entry);
FJT_API int64_t FJT_CALL fjt_meta_entry_size(const fjt_meta* meta, int32_t entry);
FJT_API const char* FJT_CALL fjt_meta_entry_path(const fjt_meta* meta, int32_t entry);
FJT_API const uint8_t* FJT_CALL fjt_meta_torrent_data(const fjt_meta* meta, int64_t* size);
FJT_API void FJT_CALL fjt_meta_free(fjt_meta* meta);

FJT_API fjt_magnet* FJT_CALL fjt_magnet_start(fjt_session* session, const char* magnet_uri, const char* scratch_dir, char* err, int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_magnet_poll(fjt_magnet* job, char* err, int32_t err_cap);
FJT_API fjt_meta* FJT_CALL fjt_magnet_take(fjt_magnet* job);
FJT_API void FJT_CALL fjt_magnet_cancel(fjt_magnet* job);

FJT_API int32_t FJT_CALL fjt_torrent_add(
    fjt_session* session,
    const char* task_id,
    const uint8_t* torrent,
    int64_t torrent_size,
    const uint8_t* resume,
    int64_t resume_size,
    const char* save_path,
    const uint8_t* file_priorities,
    int32_t priority_count,
    int32_t add_flags,
    int64_t download_limit,
    int64_t upload_limit,
    const char* extra_trackers,
    char* err,
    int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_torrent_pause(fjt_session* session, const char* task_id);
FJT_API int32_t FJT_CALL fjt_torrent_resume(fjt_session* session, const char* task_id);
FJT_API int32_t FJT_CALL fjt_torrent_remove(fjt_session* session, const char* task_id, int32_t delete_files);
FJT_API int32_t FJT_CALL fjt_torrent_set_file_priorities(fjt_session* session, const char* task_id, const uint8_t* file_priorities, int32_t priority_count);
FJT_API int32_t FJT_CALL fjt_torrent_set_limits(fjt_session* session, const char* task_id, int64_t download_limit, int64_t upload_limit);
FJT_API int32_t FJT_CALL fjt_torrent_set_sequential(fjt_session* session, const char* task_id, int32_t on);
FJT_API int32_t FJT_CALL fjt_torrent_status(
    fjt_session* session,
    const char* task_id,
    int64_t* status,
    int32_t status_count,
    int64_t* file_progress,
    int32_t file_capacity,
    char* err,
    int32_t err_cap);
FJT_API int32_t FJT_CALL fjt_torrent_debug_text(fjt_session* session, const char* task_id, char* out, int32_t cap);
FJT_API fjt_buf* FJT_CALL fjt_torrent_save_resume(fjt_session* session, const char* task_id, int32_t timeout_ms);

FJT_API const uint8_t* FJT_CALL fjt_buf_data(const fjt_buf* buf);
FJT_API int64_t FJT_CALL fjt_buf_size(const fjt_buf* buf);
FJT_API void FJT_CALL fjt_buf_free(fjt_buf* buf);

#ifdef __cplusplus
}
#endif

#endif
```

### 4.3 Function reference

Library:

| Function           | Behaviour                                                                      |
|--------------------|--------------------------------------------------------------------------------|
| `fjt_abi_version`  | Returns `FJT_ABI_VERSION`. The core refuses a DLL that answers another number. |
| `fjt_version_text` | `lt::version_str`, e.g. `2.1.2.0`. Used in `sysTorrentEngineReadyVersion`.     |

Session:

| Function                     | Behaviour                                                                                                                                                                                                                                                   |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fjt_session_create`         | Builds a `settings_pack` from the text, forces `alert_mask` (section 6.3), `max_retry_port_bind=10`, the disk backend (section 6.1), creates `lt::session`, installs lt4j's port filter (2.2). Returns NULL and fills `err` on failure. Blocks about 0.1 s. |
| `fjt_session_apply_settings` | `session.apply_settings(pack)` with only the given names. Returns the number of unknown names (0 = all applied), or a negative code. Used for the proxy lines of 2.3, live.                                                                                 |
| `fjt_session_info`           | Fills `out[0..count)` by the `FJT_SI_*` slots: listen port (0 = no listen socket), DHT running, DHT node count (-1 when unknown), torrents in the session, torrents not paused, running magnet jobs. Returns the number of slots written.                   |
| `fjt_session_destroy`        | Clears the map, cancels magnet jobs, pauses the session, `abort()`s it and waits up to `timeout_ms` for the network thread; past the timeout the wait continues on a detached thread inside the DLL. The pointer is dead after the call. NULL = no-op.      |

Metadata (no session needed for `fjt_meta_parse`):

| Function                                              | Behaviour                                                                                                                                      |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `fjt_meta_parse`                                      | `load_torrent_buffer(span, ec, load_torrent_limits{})`. NULL + `err` when it is not a torrent.                                                 |
| `fjt_meta_name`, `fjt_meta_info_hash`                 | Raw name (not sanitized: Kotlin sanitizes as in 1.3) and the 40-char hex of the v1 hash (`get_best()` when there is no v1).                    |
| `fjt_meta_total_size`                                 | `ti.total_size()` (desktop rule, pad files included).                                                                                          |
| `fjt_meta_num_files`                                  | Number of files in libtorrent's layout, pad files included = the length a priority array must have.                                            |
| `fjt_meta_entry_count` / `_index` / `_size` / `_path` | The non-pad files in order; `_index` is the libtorrent file index, `_path` uses the torrent's own separators. Out-of-range entry -> -1 / NULL. |
| `fjt_meta_torrent_data`                               | The bencoded torrent: the input bytes for `fjt_meta_parse`, the generated file for a magnet.                                                   |
| `fjt_meta_free`                                       | Frees the object and every pointer it returned. NULL = no-op.                                                                                  |

Magnet:

| Function            | Behaviour                                                                                                                                                                                                                                                              |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fjt_magnet_start`  | `parse_magnet_uri`; reuses a torrent with the same hash, else adds it with `save_path = scratch_dir`, flags without `auto_managed` and `paused`, with `upload_mode` and `stop_when_ready`, and resumes it. Returns a job, or NULL + `err` (bad link). Returns at once. |
| `fjt_magnet_poll`   | `FJT_PENDING` while there is no metadata; `FJT_OK` when the metadata is ready; negative + `err` when it failed. Cheap (one `torrent_file()` call).                                                                                                                     |
| `fjt_magnet_take`   | After `FJT_OK`: the metadata, built as in `FJTorrent.mm:338-356` (trackers copied, `write_torrent_file_buf`). The caller owns it (`fjt_meta_free`). NULL when not ready.                                                                                               |
| `fjt_magnet_cancel` | Always called exactly once per job, also after a successful take: removes the temporary torrent (with its files) when this job added it, frees the job.                                                                                                                |

The 60 s timeout and the "no peers answered" text stay in Kotlin: poll every 300 ms with `delay`,
which makes
the wait cancellable (the JVM and iOS versions block a thread and cannot be cancelled).

Torrents:

| Function                                  | Behaviour                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|-------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fjt_torrent_add`                         | (1) Known `task_id` with a valid handle: `prioritize_files`, limits, `resume()`, `FJT_OK`. (2) Else `load_torrent_buffer(torrent)`; when `resume` is given, `read_resume_data(resume)` first and the torrent's `ti`, trackers and web seeds are put into it. `save_path` (UTF-8, created by the caller), flags without `paused` and `auto_managed`, `file_priorities` (one byte per file, `FJT_PRIO_*`), `sequential_download` when asked. `add_torrent(atp, ec)`; "already in session" -> share the existing handle. (3) Every line of `extra_trackers` (`\n` separated) not already present (lower-case compare) is added, then `force_reannounce()` and `force_dht_announce()`. (4) Limits as in `fjt_torrent_set_limits`, `resume()`. `FJT_E_PARSE` / `FJT_E_FAILED` + `err` on failure. `resume`, `extra_trackers` may be NULL. |
| `fjt_torrent_pause`, `fjt_torrent_resume` | `h.pause()` / `h.resume()`. `FJT_E_NOT_FOUND` for an unknown id (the caller ignores it). Do not block.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `fjt_torrent_remove`                      | Forgets the id; removes the torrent from the session when no other id uses it (`delete_files` != 0 -> `session_handle::delete_files`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `fjt_torrent_set_file_priorities`         | `h.prioritize_files`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `fjt_torrent_set_limits`                  | Clamps to `0..INT32_MAX`; 0 = unlimited. When the torrent was added with `FJT_ADD_DISABLE_UPLOAD` the upload limit is always 32768.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `fjt_torrent_set_sequential`              | Sets or clears `torrent_flags::sequential_download`. Not used by the current contract.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `fjt_torrent_status`                      | `h.status(query_accurate_download_counters)` into `status[0..status_count)` by the `FJT_ST_*` slots (`status_count` must be >= 16); `h.file_progress()` into `file_progress` (up to `file_capacity` entries, indexed by libtorrent file index) when the torrent has metadata. Returns the number of files (so a too-small buffer is detected), or `FJT_E_NOT_FOUND`. When `FJT_F_ERROR` is set, `err` holds `errc.message()`. Also drains the alert queue when the last drain is older than 1 s.                                                                                                                                                                                                                                                                                                                                     |
| `fjt_torrent_debug_text`                  | The diagnostics line of 2.7, formatted in C++. Called at most every 15 s in verbose builds.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `fjt_torrent_save_resume`                 | `save_resume_data()` and wait up to `timeout_ms` for the answer; returns `write_resume_data_buf` bytes, or NULL (nothing to save, timeout, error). Blocks: call it on an IO thread.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

### 4.4 Ownership and threading rules

- `fjt_session*`: created and destroyed by the core. One session is enough; several are allowed.
- `fjt_meta*`, `fjt_buf*`: owned by the caller after they are returned; independent of the session
  (they stay
  valid after `fjt_session_destroy`).
- `fjt_magnet*`: owned by the caller; must be cancelled before `fjt_session_destroy` (destroy marks
  leftovers
  dead so a late `poll` returns `FJT_E_SESSION` and a late `cancel` only frees).
- Every function may be called from any thread. Calls on one session are serialized inside the DLL
  by one
  recursive mutex, as in `FJTorrent.mm:118-122`.
- The only blocking calls: `fjt_session_create` (~0.1 s), `fjt_torrent_add` (one round trip to the
  network
  thread; the file check runs afterwards in the background), `fjt_torrent_save_resume` and
  `fjt_session_destroy` (up to their timeouts). The rest are message posts or one short round trip.
- **Destroy versus other calls is the caller's job**: the core clears its session pointer first,
  waits until
  its in-flight call counter is 0, then calls `fjt_session_destroy`.
- The DLL holds no global session and no static object with a destructor that touches libtorrent, so
  a
  process exit without `fjt_session_destroy` does not hang (unwritten blocks are lost, see 6.5).
- The DLL never shows UI, never writes outside `save_path`, `scratch_dir`, and never reads the
  registry.

### 4.5 The Kotlin/Native side

New files only (all under
`sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/feature/torrent_engine/`):

- `FjTorrentLibrary.kt`: finds `fjtorrent.dll` next to the running exe
  (`GetModuleFileNameW(null, ...)`),
  loads it with `LoadLibraryExW(fullPath, null, LOAD_WITH_ALTERED_SEARCH_PATH)` (full path only,
  never a bare
  name: no search-order hijack), resolves every export once with `GetProcAddress` and
  `reinterpret<CFunction<...>>()`, checks `fjt_abi_version() == 1`. A missing export = failed load.
  Keeps the
  `Result` like `TorrentNativeLoader.loadResult`. `GetLastError` hints: 126 = file or a dependency
  missing,
  193 = wrong architecture, 5 / 225 = blocked (antivirus).
- `TorrentBackend.mingw.kt`: `actual fun createTorrentBackend()` returning one lazy instance, like
  `JA/TorrentBackend.jvmAndroid.kt`.
    - `isSupported` = loaded, or the file exists (`GetFileAttributesW`). `engineStatus` as in 2.1.
      `installEngine` = try the load; failure -> `Result.failure` with the reason (nothing is
      downloaded).
    - `fetchMetadata`: `.torrent` -> `fjt_meta_parse` (no session). Magnet -> session +
      `fjt_magnet_start`,
      poll with `delay(300)` up to 60 s, `take`, `cancel` in `finally`. Scratch folder:
      `PathResolver().getTempDirectory() + "/torrent-metadata"`. Mapping to `TorrentMetadata` as in
      1.3.
    - `add`: priorities array as in 1.3 (`fjt_meta_parse` gives `num_files`), settings text = the
      list of 2.2,
      `extra_trackers` = the 7 `PUBLIC_TRACKERS`, optional resume bytes from
      `<settings>/torrents/<taskId>.resume`.
    - `progress`: one `LongArray(16)` + one `LongArray(numFiles)` pinned per call; state number ->
      the exact
      texts of 1.3. Must return non-null after a successful `add` (1.4).
    - Proxy: collect `NetworkSettings.proxy` while a session exists and send the 12 lines of 2.3
      with
      `fjt_session_apply_settings`.
    - Idle handling as in 6.2.
- `TorrentStreamer.mingw.kt`: section 1.6.

Later, in shared files that this task may not touch: `desktopNative/CMakeLists.txt` needs one more
`copy_if_different` line for `fjtorrent.dll`, and the host should call the core's shutdown on quit
(6.5).

## 5. Build options on this PC

### 5.1 What is already here (verified locally)

| Item                    | Where                                                                      | Notes                                                                                                                                                                                                        |
|-------------------------|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MSYS2                   | `C:/msys64`                                                                | Only the **clang64** environment has packages (`mingw64/bin`, `ucrt64/bin` are empty). 292 packages installed.                                                                                               |
| clang / lld / libc++    | `C:/msys64/clang64/bin/clang++.exe`                                        | clang 23.1.4, target `x86_64-w64-windows-gnu`; `lib/libc++.a` 4,112,010 B, `libc++abi.a`, `libunwind.a`, `libwinpthread.a` present (static linking possible).                                                |
| CMake, Ninja            | `C:/msys64/clang64/bin`                                                    | CMake 4.4.5, Ninja 1.13.2.                                                                                                                                                                                   |
| **Boost 1.92.0**        | `C:/msys64/clang64/include/boost`                                          | Package `mingw-w64-clang-x86_64-boost-1.92.0-4`; 186 MB of headers; CMake config in `C:/msys64/clang64/lib/cmake/Boost-1.92.0`. `asio.hpp`, `multiprecision/cpp_int.hpp`, `system/error_code.hpp` present.   |
| **OpenSSL 3.6.5**       | `C:/msys64/clang64`                                                        | Headers in `include/openssl` (142 files); static `lib/libssl.a` 1,576,882 B and `lib/libcrypto.a` 9,096,366 B; shared `bin/libssl-3-x64.dll` 1,021,952 B, `bin/libcrypto-3-x64.dll` 5,520,896 B.             |
| Visual Studio 2026      | `C:/Program Files/Microsoft Visual Studio/18/Community`                    | MSVC 14.51.36231; CMake 4.3.1-msvc1 in `Common7/IDE/CommonExtensions/Microsoft/CMake/CMake/bin` (there is no `C:/Program Files/CMake`); `FindBoost.cmake` still ships with it.                               |
| vcpkg                   | `C:/Program Files/Microsoft Visual Studio/18/Community/VC/vcpkg/vcpkg.exe` | The Visual Studio copy: read-only, manifest mode, git registry. No package cache exists (`%LOCALAPPDATA%/vcpkg` is absent), no `VCPKG_ROOT`.                                                                 |
| libtorrent              | not present                                                                | No source, no headers, no library anywhere checked (`C:/msys64`, `E:/tools`, NuGet cache, vcpkg).                                                                                                            |
| libtorrent4j.dll        | Gradle cache, inside `libtorrent4j-windows-2.1.0-39.jar`                   | 13,076,480 B; contains libtorrent 2.1.0.0, OpenSSL 3.6.1, Boost 1.87; imports only system DLLs. Its export table is 3,659 `Java_...` JNI functions and nothing else, so it **cannot** be used without a JVM. |
| OpenSSL for MSVC        | not present                                                                | Python's and Git's `libssl` DLLs have no headers / import libraries for MSVC.                                                                                                                                |
| Kotlin/Native toolchain | `C:/Users/miche/.konan/dependencies`                                       | Has its own clang and mingw sysroot but no Boost and no OpenSSL; not a build route.                                                                                                                          |

The MSYS2 package database on disk is from 2026-11-02 (`C:/msys64/var/lib/pacman/sync/clang64.db`).
It lists
`mingw-w64-clang-x86_64-libtorrent-rasterbar-2.1.2-1`: download 7,847,563 B, installed 77,854,733 B,
depends
only on `boost` and `openssl` (both installed). That MSYS2 builds this exact package proves that
libtorrent
2.1.2 compiles with this clang + libc++ + Boost 1.92 + OpenSSL 3.6 combination.

### 5.2 Route B (recommended): clang64 + local Boost + local OpenSSL + libtorrent source

Download (one file):

| File                                | Size                                                       | From                                                                                              |
|-------------------------------------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `libtorrent-rasterbar-2.1.2.tar.gz` | 6,706,453 B (6.4 MiB), verified in the GitHub releases API | `https://github.com/arvidn/libtorrent/releases/download/v2.1.2/libtorrent-rasterbar-2.1.2.tar.gz` |

Alternative for the exact iOS commit: `git fetch --depth 1` of
`6da363d2994f17c0b3c0450d124cf73a31a73847`
plus `git submodule update --init --depth 1 deps/try_signal` (the other submodules are only for
GnuTLS,
WebTorrent and the tests). Size not measured, estimate 8-12 MB.

Steps (cmd, not Git Bash; nothing is installed):

1. Unpack to `E:\tools\libtorrent-2.1.2` with the Windows `tar.exe`.
2. New files in the repo: `desktopNative/torrent/CMakeLists.txt`,
   `desktopNative/torrent/fjtorrent.cpp`,
   `desktopNative/torrent/fjtorrent.def` (the export list),
   `desktopNative/torrent/include/fjtorrent.h`.
   The CMake file follows `iosApp/FJTorrent/CMakeLists.txt`:
   `add_subdirectory(${LIBTORRENT_SOURCE_DIR} libtorrent EXCLUDE_FROM_ALL)`,
   `add_library(fjtorrent SHARED fjtorrent.cpp fjtorrent.def)`,
   `target_compile_definitions(fjtorrent PRIVATE FJT_BUILD)`,
   `target_link_libraries(fjtorrent PRIVATE torrent-rasterbar)`,
   `set_target_properties(fjtorrent PROPERTIES PREFIX "")`,
   and for this toolchain `target_link_options(fjtorrent PRIVATE -static -Wl,--gc-sections -s)`.
3. Configure and build at low priority:

```
set PATH=C:\msys64\clang64\bin;%PATH%
cmd /c start /low /b /wait cmake -S E:\tbib\FetchJet\desktopNative\torrent -B E:\tools\probes\fjtorrent\build -G Ninja ^
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_CXX_STANDARD=17 ^
  -DLIBTORRENT_SOURCE_DIR=E:/tools/libtorrent-2.1.2 ^
  -DBUILD_SHARED_LIBS=OFF -DOPENSSL_USE_STATIC_LIBS=ON ^
  -Ddeprecated-functions=OFF -Dwebtorrent=OFF ^
  -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF
cmd /c start /low /b /wait cmake --build E:\tools\probes\fjtorrent\build -j 4
```

The options are the iOS ones (script lines 58-61). Boost and OpenSSL are found by clang64's CMake on
its
own. `add_library(... SHARED ...)` stays shared even with `BUILD_SHARED_LIBS=OFF`.

4. Copy `fjtorrent.dll` next to `fetchjet_native.exe`.

Result: **one file, `fjtorrent.dll`**, estimate 7-10 MB (libtorrent + OpenSSL + libc++ linked in;
the
reference point is `libtorrent4j.dll`, 13.1 MB with the same contents plus the JNI layer). It
imports only
Windows DLLs (`KERNEL32`, `WS2_32`, `MSWSOCK`, `IPHLPAPI`, `bcrypt`, `CRYPT32`, `ADVAPI32`, `USER32`
and the
`api-ms-win-crt-*` UCRT set that is part of Windows 10/11). No Visual C++ redistributable is
involved.
Build time: about 300 source files, estimate 10-20 minutes at low priority with 4 jobs.

Optional size trims to try later: `-Dlogging=OFF`, `-Di2p=OFF`.

### 5.3 Route C: clang64 + the prebuilt MSYS2 libtorrent package

Download (one file, through pacman, which is an install the owner must approve):

| File                                                                  | Size                                                                                           |
|-----------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `mingw-w64-clang-x86_64-libtorrent-rasterbar-2.1.2-1-any.pkg.tar.zst` | 7,847,563 B (7.5 MiB) download, 77,854,733 B installed (from the local database of 2026-11-02) |

Steps: `C:\msys64\usr\bin\pacman.exe -S --needed mingw-w64-clang-x86_64-libtorrent-rasterbar` (no
`-y`, no
`-u`), then build only `fjtorrent.cpp` with `find_package(LibtorrentRasterbar)`: seconds instead of
minutes.

Result, two possibilities (the package's file list is not on this PC, **not verified**):

- the package has a static library (its 77.9 MB installed size suggests it): link as in route B, one
  `fjtorrent.dll`;
- only the shared library: ship `fjtorrent.dll` (estimate 0.2 MB) + `libtorrent-rasterbar` DLL
  (estimate
  5-7 MB) + `libc++.dll` 1,583,616 B + `libunwind.dll` 60,928 B + `libssl-3-x64.dll` 1,021,952 B +
  `libcrypto-3-x64.dll` 5,520,896 B (+ `libwinpthread-1.dll` 78,848 B if imported): about 14-16 MB
  in 6-7
  files.

Weak points: built with MSYS2's options (deprecated API on, WebTorrent as packaged), not the iOS
ones; if the
mirror has replaced 2.1.2-1 since 2026-11-02 the download fails and a database refresh (`-Sy`, about
2.5 MB)
may then want a newer Boost (15,115,595 B + 1,882,884 B).

### 5.4 Route A: MSVC + Boost headers + libtorrent source

Downloads: the same source tarball (6,706,453 B). Boost: none, by pointing MSVC at the MSYS2 headers
through a
junction so that the mingw system headers next to them are not picked up:

```
mkdir E:\tools\boost_1_92_0
mklink /J E:\tools\boost_1_92_0\boost C:\msys64\clang64\include\boost
```

then
`-G "Visual Studio 18 2026" -A x64 -DBOOST_ROOT=E:/tools/boost_1_92_0 -DBoost_NO_BOOST_CMAKE=ON -Dstatic_runtime=ON`
plus the libtorrent options of route B. Using MSYS2's Boost headers with MSVC is plausible (Boost
headers are
the same for every compiler) but **not verified**.

The problem is TLS: there is no OpenSSL for MSVC on this PC. Either

- build without it: `fjtorrent.dll` estimate 4-5 MB, one file, but **no HTTPS trackers and no HTTPS
  web
  seeds** (a regression against libtorrent4j, which has OpenSSL 3.6.1 inside; `torrent.ubuntu.com`
  announces
  over HTTPS, for example); or
- get OpenSSL for MSVC: a prebuilt package (estimate 60-280 MB depending on the distributor) or the
  source (estimate 50 MB) plus a native Perl to build it (estimate 150+ MB). All **estimates, not
  verified**.

Result with OpenSSL: one `fjtorrent.dll`, estimate 8-10 MB, static CRT.

### 5.5 Route D: vcpkg

`vcpkg install libtorrent` in manifest mode with the Visual Studio copy. It clones the vcpkg
registry and
downloads every Boost module libtorrent uses, the OpenSSL source and the tools to build it (Perl,
NASM).
Estimate 300-500 MB of downloads (**not verified**), and the longest build. Result: one
`fjtorrent.dll`,
estimate 8-10 MB. Sensible only on CI, where downloads do not cost the owner.

### 5.6 Comparison

| Route                      | Local download        | TLS trackers | Files shipped | Build time           | Same options as iOS |
|----------------------------|-----------------------|--------------|---------------|----------------------|---------------------|
| B clang64 + source         | 6.4 MiB               | yes          | 1             | 10-20 min (estimate) | yes                 |
| C clang64 + pacman package | 7.5 MiB               | yes          | 1 or 6-7      | under 1 min          | no                  |
| A MSVC, no OpenSSL         | 6.4 MiB               | **no**       | 1             | 10-20 min (estimate) | yes                 |
| A MSVC + OpenSSL           | 60-300 MB (estimate)  | yes          | 1             | longer               | yes                 |
| D vcpkg                    | 300-500 MB (estimate) | yes          | 1             | longest              | no                  |

The C API is identical in every route, so the choice can change later (for example route B locally
and
another one on CI) without touching Kotlin or the UI.

## 6. Risks, and keeping idle RAM at zero

### 6.1 Memory-mapped disk IO

- libtorrent 2.0.x on 64-bit Windows uses `mmap_disk_io` by default: file pages are mapped into the
  process,
  the working set grows with the data read and written, and Windows trims it late. This is the
  well-known
  libtorrent 2.0 memory complaint.
- In the pinned 2.1 commit `default_disk_io_constructor` (`src/session.cpp`, verified) chooses under
  `#if TORRENT_HAVE_PREAD || defined TORRENT_WINDOWS` the new multi-threaded **pread** backend; mmap
  is only
  the `#elif`. ChangeLog 2.1.0: "add a multi-threaded, pread ()-based, disk I/O backend
  (pread_disk_io)".
  So the default on Windows is already not mmap.
- The iOS flag `-DTORRENT_HAVE_MMAP=0` (script line 39) does nothing on Windows (the Windows macro
  is
  `TORRENT_HAVE_MAP_VIEW_OF_FILE`).
- The wrapper should still set `session_params::disk_io_constructor` to the pread constructor
  explicitly (header `libtorrent/pread_disk_io.hpp`; exact symbol name to be read from the source),
  so that a later
  switch to a 2.0.x source cannot bring mmap back silently.

### 6.2 RAM

- **Before the first torrent action: zero.** `isSupported` and `engineStatus` only test that the
  file exists.
  The DLL is loaded by the first `fetchMetadata` / `add`. Parsing a `.torrent` loads the DLL but
  creates no
  session (no threads, no sockets). Restored tasks that are paused or completed never touch the
  backend.
- **While a torrent runs**: the desktop tuning of 2.2 is generous: up to 16 MiB of queued disk
  blocks, a
  3 MiB send-buffer watermark per uploading peer, 800 connections with their receive buffers, 4000
  peer-list
  entries, 8 disk threads. Estimate 30-80 MB at full speed (**not measured**), which is above the
  whole app's
  50 MB target. Knobs if the owner wants the budget to hold during torrents: `connections_limit`,
  `max_queued_disk_bytes`, `send_buffer_watermark`, `aio_threads`, `max_peerlist_size`. Each costs
  speed.
- **After the last torrent**: the core keeps a small idle timer. When the session has no running
  magnet job
  and no torrent that is not paused (`FJT_SI_ACTIVE_TORRENTS == 0`, `FJT_SI_MAGNET_JOBS == 0`) for
  60 s, it
  "parks": for every remaining (paused) torrent `fjt_torrent_save_resume` ->
  `<settings>/torrents/<taskId>.resume`,
  then `fjt_session_destroy`. The next `add` creates the session again and passes the resume bytes,
  so there
  is no recheck. This is invisible to common code: its poll loop has already ended for paused and
  finished
  tasks, `pause` / `setLimits` / `setFileSelection` on an unknown id are no-ops, and a resume always
  goes
  through `add`. `remove` deletes the `.resume` file (common code only deletes `<taskId>.torrent`).
- **Seeding blocks this.** A finished torrent with upload enabled stays active in the session for
  the rest of
  the app run (1.4), so the session never becomes idle. Owner's decision needed (open question 3).
- **Unloading the DLL**: do not depend on `FreeLibrary`. OpenSSL pins the module it lives in by
  default, a
  DLL with live threads must never be unloaded, and the gain over a destroyed session is small.
  First version:
  destroy the session and leave the image mapped; measure the private bytes that remain (**to
  measure**). If
  that is not close to zero, the fallback is to host the same DLL in a small helper process that
  exits when
  idle.

### 6.3 Alerts and polling cost

- No alert thread (lt4j wakes one every 500 ms). `alert_mask` is reduced to what the wrapper reads:
  errors,
  plus the category of `save_resume_data_alert` (to be read from `alert_types.hpp`). The queue is
  drained
  inside `fjt_torrent_status`, at most once per second, so it cannot grow.
- Per poll and torrent: one `status()` and one `file_progress()` round trip to libtorrent's network
  thread,
  every 700 ms. Use `status(query_accurate_download_counters)` instead of the default "everything"
  flags (no
  piece bitfield, name, save path copies). Negligible CPU; no JNI objects as on the JVM.
- `fjt_torrent_debug_text` (trackers, peer list) is heavier: verbose builds only, every 15 s, as
  today.

### 6.4 Antivirus, SmartScreen, firewall

- Never download the DLL at run time and never load it from a user-writable folder (the JVM app
  extracts
  `libtorrent4j.dll` into `~/.fetch_jet/tools`, and `TorrentNativeLoader.kt:118-119` already has an
  "antivirus blocked the library" hint). Ship it inside the installer / MSIX next to the exe and
  sign it with
  the same certificate as the exe.
- Load by full path with `LoadLibraryExW` (4.5).
- BitTorrent code is sometimes flagged as "potentially unwanted". Lazy loading helps: the DLL is not
  touched
  for users who never open a torrent. A blocked load must end as a readable `engineStatus` line, not
  a crash.
- The first listen socket triggers the Windows Firewall prompt for `fetchjet_native.exe` (the JVM
  app gets it
  for its own exe). With lazy loading it appears at the first torrent, not at app start.

### 6.5 Shutdown

- Nobody calls `TorrentBackend.shutdown()` today. On the JVM the process just ends. In the native
  app the
  host should call it on quit (through an fjcore export), with a 2-3 s budget: save resume data,
  `fjt_session_destroy`. Without it nothing hangs (4.4), but blocks still queued for the disk are
  lost and
  the next start rechecks.

### 6.6 Other

- **Toolchain drift**: MSYS2 is a rolling distribution. Do not run `pacman -Syu` between builds;
  record the
  versions used (clang 23.1.4, Boost 1.92.0, OpenSSL 3.6.5).
- **CI**: the release is built on GitHub Actions. The runner needs the same route (MSYS2 clang64
  packages, or
  another route); downloads there cost the owner nothing, but the workflow is a shared file and the
  owner
  runs releases himself.
- **Setting names**: the list of 2.2 comes from lt4j 2.1.0's enum, so every name exists in 2.1. The
  numeric
  enum values used as text (`proxy_type` 0/2/3/4/5, `mixed_mode_algorithm` 0) must be checked
  against
  `settings_pack.hpp` of the source that is built. Unknown names are counted, not fatal.
- **Duplicate torrents**: two tasks with the same info-hash share one handle (4.1 rule 3). lt4j
  behaves the
  same way by accident (both map entries hold the same handle; removing one removes it for both).
- **Paths**: libtorrent takes UTF-8 paths on Windows and converts them itself; Kotlin strings pass
  as
  `.cstr`. Long paths are libtorrent's own handling (**not tested**).
- **Licences**: libtorrent (BSD-3-Clause), Boost (BSL-1.0), OpenSSL 3 (Apache-2.0), libc++
  (Apache-2.0 with
  LLVM exception): all fine inside a GPL-3.0 app; their notices must ship with it.
- **FJTorrent.mm formatting**: lines 252-257 and 380-382 show a method signature split over several
  lines by
  a reformat. It still compiles; it is mentioned only because the file is supposed to be excluded
  from
  formatting.

## 7. Open questions for the owner

1. Build route: B (one 6.4 MiB download, one DLL, TLS, same options as iOS) is the recommendation.
   OK?
2. Source: the `v2.1.2` release tarball, or the exact iOS commit `6da363d` through git (one day
   older,
   size not measured)?
3. Seeding: today a finished torrent with upload enabled seeds until the app closes, which keeps the
   engine
   in RAM. Keep that, or stop seeding after some time / ratio so the engine can be released?
4. RAM while a torrent downloads: keep the desktop speed tuning (estimate 30-80 MB for the engine
   alone) or
   trim it to stay near the 50 MB target?
5. CI: which route should the release workflow use to produce `fjtorrent.dll`?
6. Resume data and "parking" (6.2) are new behaviour compared with the JVM app (no recheck after a
   restart,
   engine released while everything is paused). Wanted in the first version, or parity first?
7. User agent / peer id: desktop sends libtorrent's defaults, iOS sends `FetchJet/1.0 ...` and
   `-FJ1000-`.
   Which one for Windows?

## 8. Not verified (must be checked during implementation)

- That `v2.1.2` equals the pinned commit.
- The file list of the MSYS2 libtorrent package (static library or not).
- MSVC compiling against MSYS2's Boost headers (route A only).
- The exact names `pread_disk_io_constructor` and the alert category of `save_resume_data_alert`.
- Every size marked "estimate", every RAM figure, and the build time.
- Whether lld's MinGW driver honours the `.def` file together with `-static` exactly as written in
  5.2.
