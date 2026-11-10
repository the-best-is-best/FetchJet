# Native port: torrent engine (fjtorrent.dll) hand-over

Written 2026-11-10 01:55, stopped by the owner's 02:00 rule. Plan to follow:
`E:\tbib\FetchJet\desktopNative\docs\port\12-torrent-plan.md`.

## State in one line (updated 2026-11-10 12:35)

Working on loopback: `run.sh torrent` 37/37 (uTP and TCP), `tests/corehost/test_torrent.py`
17/17, wired into the app (DLL copy, shutdown). See the last section "Session 2026-11-10 noon".

## Session 2026-11-10 morning: what was done

- `desktopNative/torrent/CMakeLists.txt` (libtorrent `EXCLUDE_FROM_ALL`, `-w -includeiterator`
  on `torrent-rasterbar`: `src/escape_string.cpp` uses `std::back_inserter` without
  `<iterator>` and fails with this libc++; the source folder is not edited), `build.cmd`
  (extra arguments go to `cmake --build`, e.g. `--target fjtorrent fjtseed`). Note: `build.cmd`
  returns 0 even when ninja fails (the `start /wait` exit code is lost): read the log.
- `fjtorrent.cpp` compiled on the first try. One change after the tests: `pick_listen_port`
  (before the session starts, the configured port is test-bound for TCP and UDP; when it fails,
  up to 64 random ports in 20000-59999 are tried). Cause: Windows reserves 100-port blocks
  (`netsh int ipv4 show excludedportrange`: here 55767-55866, 61353-62793 TCP, 52213-54341 UDP);
  a random port inside one gave `listenPort=0` (the `max_retry_port_bind=10` retries stay inside
  the block). Very likely the `listenPort=0` CLAUDE.md records for the JVM app on Windows.
- `tests/fjtseed.cpp`: as planned, plus `peer=0` (no peer, no tracker in the torrent), `v1=1`,
  `life=<s>`. TCP and UDP listen on the same port (with port 0 they differ, libtorrent tries uTP
  first, the attempt fails and the next try waits `min_reconnect_time`).
- Kotlin: `platform/win/WinTorrentLibrary.kt` (binding, DLL search, load, `WinTorrentLock` on
  SRWLOCK), `platform/win/WinTorrentEngine.kt`, `feature/torrent_engine/TorrentBackend.mingw.kt`.
  `WinLock.kt` was missing when this started (parked by the device-sync work), so the torrent
  code has its own `WinTorrentLock`; switch to `WinLock` once that file is final.
- winhelpers: `TorrentTests.kt`, `"torrent"` in `Main.kt`, `torrent)` in `run.sh`. The real
  winhelpers project does not build right now because of the unfinished `ProxyTests.kt` of the
  proxy work (syntax errors), so the tests were linked from a copy without it:
  `E:\tools\probes\torrent-winhelpers` (same files, absolute `srcDir`, no `proxy` line), built
  with `./gradlew.bat -p E:/tools/probes/torrent-winhelpers linkDebugExecutableMingwX64 --offline`.
  Run: `FETCHJET_TORRENT_LOCAL_ONLY=1 FETCHJET_TORRENT_DLL=E:/tools/probes/fjtorrent/build/fjtorrent.dll
  <exe> E:/tools/probes/fjtorrent/work torrent` (the seeder is taken from the DLL's folder).
- Not done: `desktopNative/CMakeLists.txt` copy line, `NativeApp.shutdown()` call, parking of
  paused torrents, `TorrentStreamer.mingw.kt` on WinHttp.

## Files created (all new, all under `E:\tbib\FetchJet\desktopNative\torrent\`)

> History (01:55 state). Out of date since the morning session: `fjtorrent.cpp` compiles,
> `tests/fjtseed.cpp` exists, and every item of "Not written yet" below except the parking of
> paused torrents and `TorrentStreamer.mingw.kt` on WinHttp is done. Current results: the
> sections from "Session 2026-11-10 noon" on (`run.sh torrent` 37/0 five times in the release
> round).

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

## Test results (2026-11-10, 127.0.0.1 only, five runs; logs `E:\tools\probes\fjtorrent\test-run*.log`)

Last run (`test-run5.log`): **34 passed, 3 failed**. Passed: load / ABI / version 2.1.2.0,
garbage refused, metadata (pad files skipped, indices 0, 2, 4 of numFiles 6, paths, total size),
session listens, DHT off, local-only on, 0 unknown setting names, progress non-null after add,
announce seen by the tracker, 127.0.0.2 tracker dropped, pause flag, full download + SHA-256 of
every file, file left out not downloaded, fileProgress length = numFiles, file added by
selection downloaded, two ids share one handle, download-only upload cap 32768, remove one id
keeps the other, resume data saved (51 KB), session destroyed after the idle time, restore from
resume data (finished in ~220 ms without downloading), magnet with `tr=` (metadata in ~0.3 s),
download from the magnet's generated bytes, web-seed-only download (`GET /web/...` seen),
shutdown.

Failed (all one cause): the **first** connection to the seeder. The downloader connects over
uTP using the v2 (truncated) hash `f4445a...` of the hybrid torrent; the seeder logs
`incoming connection (uTP)` then `disconnecting ... stopping torrent`. libtorrent then waits
`min_reconnect_time` (60 s) before the next try (that one works, the download finishes). So
"download starts within 30 s", "2 MB/s limit within 30 %" and "re-add resumes" never had bytes
to measure. Not yet known whether this is the seeder's fault (test tool) or would hit the app
with real hybrid torrents. Next step: print the downloader's `peer_disconnected_alert` too, and
try the seeder with `v1=1`, and with `enable_outgoing_utp=false` on the downloader, to split
"v2 hash" from "uTP". Run 4 also had one flaky failure of the magnet download (`m1` reported
finished but the files in `dl2` did not match; passed in runs 3 and 5): suspect a leftover
handle (removed task `t3` or the magnet's temporary torrent) found by `find_existing` in
`fjt_torrent_add` before its async removal ends. Not proven.

Private bytes of the test process (`winMemoryUse`): before load 4.9 MB, DLL loaded 5.4 MB,
peak while downloading 6.8 MB, after the session was destroyed 10.5 MB, after shutdown 11.3 MB.
The rise after destroy is not explained (heap not returned to Windows? later sessions for the
magnet and web-seed tests); measure in the real app before drawing conclusions.

## Session 2026-11-10 noon (11:59-12:35): first connection fixed, 37/37, wired into the app

**Cause of the first-connection failure: the test seeder, not the app.** Evidence: with
`alert_category::status` added to `fjtseed` (run 6), the seeder logged `hash for piece 81 failed`,
then `state changed to: downloading`, then `disconnecting (uTP) ... stopping torrent`. In
libtorrent 2.1 the v2 piece layers of a .torrent live in `add_torrent_params::merkle_trees`, not
in `torrent_info`; `fjtseed` added the torrent with `atp.ti = ti` only, so in `seed_mode` the
first requested piece could not be v2-verified (`set_block_hash` -> unknown), libtorrent left seed
mode "as non-seed", ran `force_recheck()`, and that disconnects every peer with
`stopping_torrent`. uTP was not involved (TCP-only runs behave the same). Fix: `fjtseed` adds the
full `load_torrent_buffer` result (trackers / url seeds cleared). After it the first uTP
connection downloads at once (run 7).

**App fixes found by the now-working tests (all in `fjtorrent.cpp`):**
1. Resume path of `fjt_torrent_add`: when the saved resume data has no merkle trees, the ones from
   the .torrent are kept (same 2.1 piece-layer rule).
2. Pause -> resume, and new files selected on a finished torrent, waited `min_reconnect_time`
   (60 s, x failcount) before the seeder was dialled again (libtorrent keeps `last_connected` of a
   peer it was just connected to). The JVM app has the same delay. Now `wake()` /
   `prioritize()` toggle `upload_mode` on and off (libtorrent resets `last_connected` of all
   peers when it leaves upload mode, `torrent.cpp:1079`), and for 30 s after such a user action
   `fjt_torrent_status` repeats that at most every 2 s while the torrent has peers in its list
   but no connection and no connect candidate (`nudge_if_stuck`). Resume now continues in < 1 s
   (re-add check), adding a file to a finished torrent ~1 s (was 60-100 s).
3. "Finished" was reported before the bytes were on disk (pread_disk_io keeps writes queued):
   one TCP run read the magnet download's files right after `isFinished` and found them wrong;
   a minute later they were right. `fjt_torrent_status` now sets `FJT_F_FINISHED` /
   `FJT_F_SEEDING` only after libtorrent's `cache_flushed_alert` for that torrent (posted by
   libtorrent when a torrent finishes; `flush_cache()` is asked once as a fallback).

**Speed-limit check:** libtorrent hands download quota in chunks of up to 3 s of the limit, so on
loopback `totalWantedDone` moves in steps (1.3, 1.9, 2.6, 3.8, 5.2 MB, with 1-3 s pauses). A
4 s window gave 2.6 MB/s or 1.4 MB/s depending on where the steps fell. The check now samples
every 250 ms for 9 s and measures between the first and the last step: 2.22-2.31 MB/s for the
2 MB/s limit in all runs (whole-window average 1.78-2.21 MB/s). Same numbers over TCP, so uTP is
not the reason.

**Wiring:** `desktopNative/CMakeLists.txt` copies `FJ_TORRENT_DLL` (default
`desktopNative/torrent/build/fjtorrent.dll` if it exists, else
`E:/tools/probes/fjtorrent/build/fjtorrent.dll`) next to `FetchJet.exe` when it exists (a
missing DLL only prints a status line, so CI without the DLL still builds). Checked:
`desktopNative\build.cmd debugShared` put `fjtorrent.dll` into `build\Release` (that copy is the
12:19 DLL, before fixes 2 and 3: the next app build copies the new one). `NativeApp.shutdown()`
calls `createTorrentBackend().shutdown()` after `presenter.stop()`. `AppFeatures.torrent` is
`createTorrentBackend().isSupported` = the DLL was found (env, next to `fjcore.dll`, next to the
EXE), so nothing else was needed. `build.cmd` now calls cmake directly (normal priority, the
owner's new rule; also fixes the lost exit code).

**Tests (all seen passing, 127.0.0.1 only, local-only on):**
- `run.sh torrent` (real winhelpers project now, no scratch copy): 37/37 in runs 10 (uTP), 11
  (uTP and `FJ_TEST_ARGS=tcp`), 12 (uTP). Run 10 TCP failed only on fix 3's case (before fix 3).
  Logs `E:\tools\probes\fjtorrent\test-run*.log`. Private bytes run 12: before load 4.9 MB, DLL
  loaded 5.4, downloading 7.8, after destroy 5.9, after shutdown 7.3.
- `tests/corehost/test_torrent.py` (new; home `E:\tools\fjhome\corehost\torrent`, bridge off,
  automatic downloads off; starts two `fjtseed` seeders): 17/17, twice. Covers: torrent engine
  offered in the Add dialog, BitTorrent page listed in Settings, a .torrent path (what the picker
  returns) -> file list -> leave c.txt out -> start -> COMPLETED with SHA-256 equal, a magnet with
  `tr=` -> metadata in 0.1 s -> COMPLETED with SHA-256 equal, the app announced to the `tr=`
  tracker, `fj_shutdown` with a live session < 5 s, no crash log. The "Open .torrent" button
  needs the host's file picker, so headless it is only checked to follow `canImportTxt`.
- `run.sh proxy` 37/37, `run.sh sync` 84/84 after the edits. `:sharedLogic:compileKotlinMingwX64
  -Pfetchjet.nativeDesktop=true` and the winhelpers link pass.

**Still open:** a real torrent / magnet from the internet in the running app (never done: no
downloads allowed); the Add dialog's link-list torrent rows; the picker button on a real desktop;
parking of paused torrents (idle close still waits for the last task to be removed);
`TorrentStreamer.mingw.kt` on WinHttp; `fjt_magnet_take` writes the .torrent without v2 piece
layers (allowed, the test downloads from it; `get_resume_data(save_info_dict)` could carry them);
`stage.ps1` (packaging, not touched) finds `fjtorrent.dll` only under `desktopNative\torrent`, so
for a package build run `build.cmd` with `FJT_BUILD_DIR=<repo>\desktopNative\torrent\build` (a
full libtorrent build there, ~3 min); `WinTorrentLock` could move to `WinLock`. The test's
`FJ_TEST_ARGS=tcp` option (downloader with uTP off) is kept for such comparisons.

## Session 2026-11-10 afternoon (13:15-14:00): "adding a file by selection" failure is not path-dependent

**Finding: the DLL location is not the cause.** The DLL in `desktopNative\torrent\build` and the
copy in `E:\tools\probes\verify\tdll` are the same bytes (SHA-256 `f4dec5eb...`, 9,893,888 B; the
probes DLL `215c007d...` differs only by build-path strings: same source, flags and compiler in
both `CMakeCache.txt` / `build.ninja`). The DLL imports only system DLLs, so the folder cannot
change what it loads. Re-run with the same test exe (12:25 build), the same work folder
(`E:\tools\probes\tdebug\work`) and only `FETCHJET_TORRENT_DLL` changed: the build folder failed
(13:16) and **the tdll copy failed too, 2 of 2** (13:21-13:27, "after 100109 / 100125 ms"). Two
more tdll runs in between stopped earlier with `seeder: ERROR no listen port` (the seeder's
libtorrent did not listen within 5 s; both seeder copies start fine by hand), so the machine was
busy then (other agents' Gradle builds held the build lock). Earlier passes (13:05 probes DLL,
13:08-13:09 tdll) and the 13:00-13:04 failures fit the same picture: time-clustered, not
path-clustered. After 13:31 every run passed: build folder 4/4, old DLL copy 3/3 (one during a
4.6 GB Gradle build), so the failure could not be caught again with a trace.

**Weak spot found in the code (fixed):** `nudge_if_stuck` (`fjtorrent.cpp`) skipped the nudge when
`connect_candidates > 0`. Every trace shows that state right after the finish: `state=finished
conns=0 candidates=1 listPeers=1`. libtorrent counts a peer as a candidate even while
`min_reconnect_time` (60 s x (failcount + 1), `peer_list.cpp:556`) holds it back, so when the
first `upload_mode` toggle in `prioritize()` lands before the seed's `last_connected` is set
again (a late close of the finish-time connection, or a reconnect made while the torrent is still
finished and dropped by the seeder as "upload to upload": the new file priorities are applied only
when the disk job returns, `torrent.cpp:6122` / `on_file_priority`), the nudge never repeats and the new file waits
60-120 s: the 100 s timeout seen. Now the nudge repeats every 2 s for 30 s whenever the torrent is
unfinished, not paused, has peers in its list and no connection (`connect_candidates` no longer
checked). Not proven to be the exact path of the failed runs (no trace of a failing run);
proven only that the old condition cannot recover from it.

**Test change:** the selection wait in `TorrentTests.kt` prints `debugText` every 2 s (from +2 s,
so it does not change the timing) and the seeder lines after the selection, so a future failure
shows the stuck state.

**Results with the fixed DLL** (`desktopNative\torrent\build\fjtorrent.dll` 13:42, SHA-256
`eb05e9fe...`, built through `one-build.sh` + `build.cmd` with `FJT_BUILD_DIR`): `run.sh torrent`
37/37 twice from the build folder (selection 438 ms) and 37/37 from a copy in
`E:\tools\probes\tdebug\new` (641 ms). Logs `E:\tools\probes\tdebug\*.log`.

**Not done:** the copies next to `FetchJet.exe` (`build\Release`, `build-release\Release`) and the
MSI / MSIX staged at 13:09-13:15 still hold the old DLL: re-run the app build / `stage.ps1` and the
package scripts. `test_torrent.py` not re-run with the new DLL.

## Session 2026-11-10 afternoon (14:55-15:55): "adding a file by selection" cause found and fixed

**Cause: a libtorrent 2.1.2 bug, a stale "finished" flag in the peer list.** The peer list keeps
its own copy of "torrent finished" (`peer_list::m_finished`) and counts a seed as a connect
candidate only while that copy is false (`peer_list.cpp:483`). The copy is refreshed only inside
`connect_one_peer` / `find_connect_candidates` / `erase_peers`. `torrent::want_peers()`
(`torrent.cpp:8792`) refuses to connect when the candidate count is 0, so after
`resume_download()` (finished -> downloading when a file is selected, `torrent.cpp:9041`, which
does not refresh the count) nothing ever calls those functions again: the torrent waits forever
with `conns=0 candidates=0 listPeers=1 listSeeds=1` (only a new non-seed peer from a tracker /
DHT / PEX would unlock it). Whether it happens depends on timing: right after the finish the peer
list still says "not finished" (`candidates=1`); within ~0.5-1 s the session's connect loop runs
`connect_one_peer` for the finished torrent (`seeding_outgoing_connections` is on), which flips the
copy to "finished" and the count to 0. The test selected c.txt a few hundred ms after the finish,
so it passed when it beat that tick and failed when the machine was slower. The `upload_mode`
toggle / `nudge_if_stuck` cannot help (it only resets `last_connected`). Real users add a file
long after the finish, so in the app this was the normal case, not a rare one, on any torrent
whose known peers are all seeds.

**Evidence:** with the selection made 3 s after the finish (`TorrentTests.kt` now waits 3 s and
prints the state at the finish and 3 s later), the old DLL (`eb05e9fe...`) failed every time:
`finished: state=finished ... conns=0 candidates=1 listPeers=1 listSeeds=1`, `finished +3 s:
... candidates=0`, then `state=downloading conns=0 candidates=0` for 100 s (log
`E:\tools\probes\tsel\before-3.log`). The fixed DLL with the same test: c.txt in 1016 ms.

**Fix (`fjtorrent.cpp`, `refill_if_no_candidates`, called from `fjt_torrent_status`):** when a
torrent is downloading, not paused, unfinished, has no connection, no connect candidate and peers
in its list, at most every 5 s: `get_resume_data()` (its `peers` = the list's connectable,
non-banned, never-failed peers), and if that is not empty, `clear_peers()` then `connect_peer()`
each of them (fresh entries, so they count as candidates; the next connect attempt refreshes the
stale flag). When the list holds only failed / banned / unconnectable peers, `peers` is empty and
nothing is cleared, so a healthy "no candidates" state is left alone.

**Test tool fix (`tests/fjtseed.cpp`):** "seeder: ERROR no listen port" was not machine load. The
seeder bound a TCP ephemeral port and asked libtorrent to listen there; Windows (Hyper-V / WinNAT)
had excluded UDP 50246-50845 while TCP ephemeral ports were at ~50600, so the uTP socket could
not bind and libtorrent did not listen (20 of 20 TCP ephemeral ports were refused for UDP at
14:58). It now picks a random port in 20000-59999 that binds for both TCP and UDP, as the app's
`pick_listen_port` does.

**Results (new DLL `desktopNative\torrent\build\fjtorrent.dll` 15:00, 9,906,176 B, SHA-256
`239895b5...`; built through `one-build.sh` + `build.cmd` with `FJT_BUILD_DIR`):** `run.sh torrent`
(with the 3 s wait) 5 runs in a row: 37/37 each, selection done in 1016 / 1016 / 1062 / 1046 /
625 ms. `tests/corehost/test_torrent.py` (`FETCHJET_TORRENT_DLL` set only for it): 17/0. Logs
`E:\tools\probes\tsel\`. The new DLL was copied over `build\Release` and `build-release\Release`
`fjtorrent.dll` (the next CMake build copies it too). Not done: TCP-only run (`FJ_TEST_ARGS=tcp`),
release EXE suites, the MSI / MSIX (they still hold an older DLL), a real swarm from the internet.
