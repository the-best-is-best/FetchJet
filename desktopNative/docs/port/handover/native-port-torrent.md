# Native port: torrent engine (fjtorrent.dll) hand-over

Written 2026-11-10 01:55, stopped by the owner's 02:00 rule. Plan to follow:
`E:\tbib\FetchJet\desktopNative\docs\port\12-torrent-plan.md`.

## State in one line

Only the C side is drafted (3 files, never compiled). No Kotlin was changed, so the shared
build is exactly as before (torrents still "not supported"). No build was started. No process
of mine is running. Nothing was downloaded.

## Files created (all new, all under `E:\tbib\FetchJet\desktopNative\torrent\`)

| File | State |
|---|---|
| `include/fjtorrent.h` | Complete. Lines 498-656 of the plan, one change: `FJT_SI_RESERVED_6` is now `FJT_SI_LOCAL_ONLY` (slot 6 = 1 when the session is local-only). |
| `fjtorrent.def` | Complete: the 34 exports. |
| `fjtorrent.cpp` | Complete draft of all 34 functions, **never compiled**. |
| `tests/` | Empty folder. |

Nothing else in the repository was created or changed. Not touched: `TorrentBackend.mingw.kt`
(still the placeholder), `TorrentStreamer.mingw.kt` (already real: `HttpClient { webSeedDefaults() }`,
could be made `HttpClient(WinHttpNative)` like `AppHttp.mingw.kt`), winhelpers `Main.kt` / `run.sh`.

## Not written yet

1. `desktopNative/torrent/CMakeLists.txt`, `build.cmd`.
2. `desktopNative/torrent/tests/fjtseed.cpp` (loopback seeder + tiny HTTP tracker).
3. Kotlin, new files in `sharedLogic/src/mingwX64Main/.../platform/win/`:
   `WinTorrentLibrary.kt` (LoadLibraryExW + GetProcAddress binding), `WinTorrentEngine.kt`.
4. Kotlin, replace `feature/torrent_engine/TorrentBackend.mingw.kt` with the real backend.
5. `desktopNative/tests/winhelpers/src/mingwX64Main/kotlin/TorrentTests.kt`, plus one line in
   `Main.kt` (`"torrent" -> torrentTests(work, rest)`) and a `torrent)` case in `run.sh`.

## Design decisions already taken (do not redo)

- **Layering.** `tests/winhelpers/build.gradle.kts` compiles only the folder `platform/win`, so
  the testable engine lives there: `WinTorrentLibrary` (binding) + `WinTorrentEngine` (session
  life cycle, task map, priorities, magnet poll, FetchJet's session settings, public trackers).
  `TorrentBackend.mingw.kt` stays a thin adapter: `TorrentMetadata` / `TorrentProgress` mapping,
  `sanitizePathSegment`, localized status strings, `NetworkSettings.proxy` -> 12 proxy lines.
- **DLL search**: env `FETCHJET_TORRENT_DLL` (only that path when set), else the folder of the
  module that holds the Kotlin code (`GetModuleHandleExW(FROM_ADDRESS | UNCHANGED_REFCOUNT)` with
  a `staticCFunction` pointer, so it works for `fjcore.dll` loaded by python), else `WinApp.exeDir`.
  `isSupported`: true once found (cached); a miss is re-checked at most every 2 s. Never loads.
- **Load**: `SetErrorMode(1)` around `LoadLibraryExW(fullPath, null, LOAD_WITH_ALTERED_SEARCH_PATH)`,
  resolve all 34 exports, `fjt_abi_version() == 1`. Hints: 126 missing file / dependency, 193 not
  64-bit, 5 / 225 blocked. Result cached; `installEngine` retries a failed load. Never `FreeLibrary`.
- **Locks**: `platform.windows` has `SRWLOCK`, `InitializeSRWLock`, `AcquireSRWLockExclusive`,
  `ReleaseSRWLockExclusive` (checked in `E:\tools\probes\klibdump\windows.txt`). Two locks:
  `gate` (short: session pointer, `inFlight`, `holds`, `epoch`, task map) and `lifecycle`
  (create / destroy, may block). UI-thread calls (`pause`, `remove`, `setLimits`, `progress`)
  take only `gate`.
- **Life cycle**: every call that passes the session pointer counts in `inFlight`; `add` and a
  magnet job also take a `hold`. Idle = no task, no hold. When idle, a coroutine waits
  `idleCloseMs` (60 000; env `FETCHJET_TORRENT_IDLE_MS` for tests) and, if `epoch` is unchanged,
  clears the pointer, waits `inFlight == 0`, calls `fjt_session_destroy(s, 3000)`. `remove` never
  blocks. `shutdown()` does the same at once. Parking of paused / finished torrents (plan 6.2,
  owner's questions 3 and 6) is NOT done: baseline = destroy after the last torrent is removed.
- **Session settings text** = the table in plan 2.2 + `max_metadata_size=2097152` (lt4j adds it),
  port `20000 + Random.nextInt(40000)`, then the proxy lines (plan 2.3), in that order.
- **Same as `Libtorrent4jBackend`**: 7 `PUBLIC_TRACKERS` as `extra_trackers` and as `&tr=` on a
  magnet without `tr=`; magnet wait 60 s, poll every 300 ms with `delay` (cancellable); priorities
  array of `num_files` (not selected 0, >0 -> 7, <0 -> 1, else 4); exact state texts; error texts
  of plan 1.3. One deliberate difference: a re-`add` of a known task updates its "download only"
  flag (the JVM keeps the old one).
- **Verbose line** every 15 s: `"[torrent $taskId] " + fjt_torrent_debug_text`.

## Local-only mode (coordinator's request, implemented in `fjtorrent.cpp`)

`fjt_session_create` reads env `FETCHJET_TORRENT_LOCAL_ONLY` itself (`GetEnvironmentVariableA`,
so a value set with `SetEnvironmentVariableW` in the same process is seen). When it is `1`:
listen `127.0.0.1:<same port>`, DHT / LSD / UPnP / NAT-PMP off (also re-forced in
`fjt_session_apply_settings`), empty `dht_bootstrap_nodes`, a proxy whose host is not local is
switched off, an `ip_filter` blocks every address outside 127.0.0.0/8, and `keep_local()` drops
every tracker, web seed and peer whose host is not `127.0.0.1` / `localhost` in
`fjt_torrent_add` (torrent, resume data, `extra_trackers`) and `fjt_magnet_start`.
Kotlin must also skip the public trackers when the variable is set, and the tests must set it
(`run.sh` export + `winSetEnv` at the start of the test). To test the filter safely, put a
second tracker `http://127.0.0.2:1/announce` in the test torrent: it must be dropped, and it
stays on the machine even if the filter were wrong.

**How two local sessions find each other** (asked for the UI test): a tracker on 127.0.0.1
inside the `.torrent` (or `&tr=` in the magnet). `fjtseed` is meant to run that tracker itself.
Its announce path must start with `/announce` (libtorrent's `ssrf_mitigation` refuses other
paths on loopback). A magnet can also carry `&x.pe=127.0.0.1:<port>`, but that only helps the
metadata fetch: a `.torrent` file cannot hold peers. There is no connect-peer export.

## Planned `fjtseed.exe` (not written)

`key=value` arguments: `dir=`, `out=<.torrent>`, `name=`, `files=a.bin:1500000,sub/b.bin:24000000,c.txt:70000`,
`piece=262144`, `rate=<upload B/s>`, `swarm=0|1`, `web=0|1`. It makes deterministic files, binds
the tracker socket on `127.0.0.1:0`, builds a hybrid torrent (pad files give gaps in the file
indices: good test), starts a session (listen 127.0.0.1, DHT / LSD / UPnP / NAT-PMP off, same
ip filter), adds it with `seed_mode`, registers its own port in the tracker's peer table, then
prints `READY tracker=<port> peer=<port> hash=<v1 hex> torrent=<path>` and one line per request
(`ANNOUNCE hash= port= event=`, `GET <path> range=`). Winsock thread per connection, keep-alive,
`Range` support for the web-seed mode. The Kotlin test starts it with `WinProcess.start`.

Planned checks: metadata and file list from the bytes (indices with gaps, `numFiles`), listen port
and `FJT_SI_LOCAL_ONLY`, 0 unknown setting names, download with a 2 MB/s limit (rate within 30 %),
our announce seen by the tracker, pause (bytes stop), re-`add` (continues, never below the paused
value), unlimited to the end, SHA-256 of each file (`winSha256OfFile`), add a file by selection,
two task ids sharing one handle and the 32 768 cap, remove, session gone after the idle time,
private bytes (`winMemoryUse()`) before load / loaded / downloading / after destroy, magnet with
`tr=` then download from the generated bytes, web-seed-only torrent, save / restore resume data.

## Phase 2 commands

The sources are at `E:\tools\libtorrent-2.1.2` (read-only, `version.hpp` = 2.1.2.0). Write
`CMakeLists.txt` first (plan 5.2 step 2: `add_subdirectory(${LIBTORRENT_SOURCE_DIR} libtorrent EXCLUDE_FROM_ALL)`,
`add_library(fjtorrent SHARED fjtorrent.cpp fjtorrent.def)`, `FJT_BUILD`, `PREFIX ""`,
`-static -Wl,--gc-sections -s`, `add_compile_options(-ffunction-sections -fdata-sections)` before
the subdirectory, target `fjtseed` linked to `torrent-rasterbar ws2_32`). Then, from cmd:

```
set PATH=C:\msys64\clang64\bin;%PATH%
cmd /c start /low /b /wait cmake -S E:\tbib\FetchJet\desktopNative\torrent -B E:\tools\probes\fjtorrent\build -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_CXX_STANDARD=17 -DLIBTORRENT_SOURCE_DIR=E:/tools/libtorrent-2.1.2 -DBUILD_SHARED_LIBS=OFF -DOPENSSL_USE_STATIC_LIBS=ON -Ddeprecated-functions=OFF -Dwebtorrent=OFF -Dbuild_tests=OFF -Dbuild_examples=OFF -Dbuild_tools=OFF -Dpython-bindings=OFF
cmd /c start /low /b /wait cmake --build E:\tools\probes\fjtorrent\build -j 4
```

Run it in the background, log to `E:\tools\probes\fjtorrent\build.log`. Stop and report if CMake
wants to fetch anything. Then check the exports (`llvm-readobj --coff-exports fjtorrent.dll`: only
the 34 names) and the imports (system DLLs only). Kotlin and tests:

```
bash desktopNative/tools/one-build.sh ./gradlew.bat :sharedLogic:compileKotlinMingwX64 -Pfetchjet.nativeDesktop=true --priority low
cd desktopNative/tests/winhelpers && bash ../../tools/one-build.sh <its gradle command, see the other winhelpers-build logs in E:\tools\probes> linkDebugExecutableMingwX64 --priority low
FETCHJET_TORRENT_LOCAL_ONLY=1 FETCHJET_TORRENT_DLL=E:/tools/probes/fjtorrent/build/fjtorrent.dll bash desktopNative/tests/winhelpers/run.sh torrent
```

Report asked for: DLL size, build time, what each test proved, RAM numbers.

## libtorrent 2.1.2 API: checked against the real headers

- `pread_disk_io_constructor` exists (`pread_disk_io.hpp`) and is already the Windows default.
- `session_params`: `disk_io_constructor`, `ip_filter` members; constructors from `settings_pack`.
- `settings_pack`: `setting_by_name(string_view)`, `has_val`, `get_str`, `clear(int)`,
  `type_mask` / `string_type_base` / `int_type_base` / `bool_type_base`, templated `set_int` for flags.
- `add_torrent_params` with `deprecated-functions=OFF` (ABI >= 4): `ti` is
  `shared_ptr<torrent_info const>`, **no `http_seeds`**; `trackers`, `tracker_tiers`, `url_seeds`,
  `peers` (`vector<tcp::endpoint>`), `dht_nodes`, `file_priorities` are plain vectors.
- `torrent_handle`: `url_seeds()` -> `std::set<std::string>`, `get_peer_info(vector&)`, `flags()`,
  `status(status_flags_t)`, `file_progress()`, `save_resume_data(flags)`, **`get_resume_data(flags)`
  (synchronous, could replace the alert wait in `fjt_torrent_save_resume`)**, `torrent_file()`,
  `force_reannounce(int seconds = 0, reannounce_flags_t = {})`, `operator<` / `operator==`.

Still to check when compiling (written from memory and from the lt4j 2.1.0 swig sources in
`C:\Users\miche\.gradle\caches\modules-2\files-2.1\org.libtorrent4j\libtorrent4j\2.1.0-39\975bcab1ce7e408f6c9b2b74b5c961abfcf3e413\libtorrent4j-2.1.0-39-sources.jar`):
`announce_endpoint::info_hashes[protocol_version::V1]` and the fields `fails`, `updating`,
`start_sent`, `message`; `peer_info::web_seed` / `http_seed` compared with `==`;
`session_stats_alert::counters()`; `save_resume_data_alert::params`; whether that alert needs
`alert_category::storage` in the mask; `find_torrent(sha256_hash)`; `port_filter::blocked`;
`lt::make_address`; `torrent_info::layout()`; `write_torrent_file_buf(atp, write_flags::allow_missing_piece_layer)`;
`read_resume_data(span, ec)`; `find_metric_idx("dht.dht_nodes")`; `torrent_flags::need_save_resume`.
For the seeder: `create_torrent(std::vector<create_file_entry>, piece_size, flags)` (the
`file_storage` constructor is deprecated), `set_piece_hashes(t, parentPath, ec)`, `bencode(back_inserter, t.generate())`.

## Other things learned

- `lt::session::abort()` only makes the proxy; the abort itself starts in the session's destructor
  and the proxy's destructor joins. So `fjt_session_destroy` moves the only reference into the
  worker thread (done that way in the draft).
- Per-torrent limits (`set_download_limit`) also apply to loopback peers; the session-wide limits
  do not (local peer class). Use the seeder's per-torrent upload limit to slow a test down.
- The port filter blocks peer ports below 1024 except 80 and 443: test ports must be high.
- Process exit without `shutdown()` can hang in OpenSSL's exit handler in rare cases: the core's
  `NativeApp.shutdown()` should call `createTorrentBackend().shutdown()` (core file, not mine).
  `desktopNative/CMakeLists.txt` needs one more `copy_if_different` line for `fjtorrent.dll`.
- When the DLL is missing, `engineStatus()` should say `sysTorrentEngineNotAvailable`
  (`sysTorrentEngineMissing` tells the user to click "Download Tools", which cannot help here).
- `AppFeatures.torrent` is read by the presenters on every publish: keep `isSupported` cached.
- The Write tool writes LF; `.cmd` files with LF work (avoid `goto` / labels). clang64 has
  clang++, cmake, ninja, lld, static libc++ / OpenSSL, `lib/cmake/Boost-1.92.0`.
- Helpers ready for the test: `WinProcess.start` (job object kills the seeder with the test),
  `winSha256OfFile`, `winMemoryUse`, `winSetEnv`, `winCreateDirectories`, `winDeleteRecursively`.
