# Native port: Send to device (device sync) for mingwX64 — hand-over

> Current state (checked 2026-11-10 evening): the engine is compiled, in the tree and switched on
> in the factory; `run.sh sync` 89/0 and `run.sh syncjvm` 30/0. The sections down to "Things
> learned" describe the 01:55 stop and are kept as history; read "2026-11-10 (sixth session)" first (one shared engine), then "2026-11-10 (second session)"
> and "(third session)" for the current state.

Stopped 2026-11-10 01:55 on the coordinator's order (work pauses at 02:00). Status in one line:
**the engine is written but was NEVER compiled and NEVER run; no test exists yet.**

## State of the repository

- Nothing of mine is in the compiled tree. `DeviceSyncFactory.mingw.kt` is untouched (still
  `InertDeviceSync`). `desktopNative/tests/winhelpers/**` is untouched (`Main.kt`, `run.sh`,
  `build.gradle.kts` not edited).
- I started no build, no process, opened no port. No test ran.
- The 5 files I wrote were never compiled, so I moved them out of the source tree (a compile
  error there would break the shared build for every session). They are parked, same relative
  paths, under `E:\tools\probes\device-sync-wip\`:
    - `sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/win/WinLock.kt`
    - `.../platform/win/WinLan.kt`
    - `.../feature/device_sync/DeviceSyncStore.mingw.kt`
    - `.../feature/device_sync/DeviceSyncIo.mingw.kt`
    - `.../feature/device_sync/DeviceSyncImpl.mingw.kt`
- **Git side effect to know:** while the files were in the tree something (the IDE, not me)
  staged them. After parking, `git status` shows them as `AD` (in the index, missing on disk).
  I ran no git command that changes anything. Copying the files back makes the entries normal
  again; if the work is dropped instead, the owner has to unstage them.
- Restore (Git Bash): `cp -r /e/tools/probes/device-sync-wip/sharedLogic /e/tbib/FetchJet/`

## What the parked files are

- `WinLock.kt`: `WinLock` (recursive, over `CRITICAL_SECTION`, freed by a cleaner), `withLock {}`.
- `WinLan.kt`: `winHostName()` (`GetComputerNameExW`, DNS host name), `winIpv4Interfaces()` /
  `winBroadcastAddresses()` (`WSAIoctl(SIO_GET_INTERFACE_LIST)`, 76-byte entries parsed by hand),
  `winUdpIgnoreUnreachable(socket)` (`SIO_UDP_CONNRESET` off, code `0x9800000C`).
- `DeviceSyncStore.mingw.kt`: `internal interface DeviceSyncStore` (same calls as the common
  `PairedDeviceStore`) and `DeviceSyncTiming` (announce / expire / accept wait / pair gap / first
  frame, defaults = the protocol values). Reason: `PairedDeviceStore` is one global object, so two
  instances in one process need their own store.
- `DeviceSyncIo.mingw.kt`: `FileChunkReader` / `FileChunkWriter` on `WinFiles`.
- `DeviceSyncImpl.mingw.kt`: `internal class WinDeviceSync(platformName, tempDir, store,
  onReceive, discoveryPort = 28461, tcpPort = 28462, bindHost = "0.0.0.0", sendBroadcast = env
  FETCHJET_SYNC_NO_BROADCAST != "1", timing)`: a line-by-line port of
  `DeviceSyncImpl.jvmAndroid.kt` on ktor-network (same frames, JSON classes, refusal words, log
  lines). `internal fun shutdown()` and `listeningTcpPort` exist for tests.

Deliberate differences from the JVM file (all inside the engine, none on the wire):
connect failure text is `could not reach the device` (as iOS); a decrypt failure is `bad chunk`;
read timeouts (first frame 20 s, reply 30 s, idle 120 s) where the JVM waits for ever; at most
16 inbound connections at once; a failed receive deletes its `recv-<ms>` folder; `/32`
interfaces are not broadcast to; the TCP server opens a moment after `setVisible(true)` returns
(ktor's bind is suspending, nothing blocks the UI thread).

## Not written yet

1. `DeviceSyncFactory.mingw.kt`: replace `InertDeviceSync` by `WinDeviceSync` with a store that
   delegates to the common `PairedDeviceStore` (under a `WinLock`), `tempDir =
   "$settingsDir/device-sync-tmp"`, platform name `"<winHostName() before the first '.'> \u00B7
   Windows"` (or `Windows` when blank / `localhost`).
2. All tests: `DeviceSyncTests.kt`, mode `sync` in `Main.kt` and `run.sh`.
3. The JVM cross-check (plan below).

## Next steps, in order

1. Restore the 5 files (command above).
2. Wire the test project first (it compiles only `platform/win` + my files, about 3 min, so it
   is the fast loop). In `desktopNative/tests/winhelpers/build.gradle.kts` (re-read it first,
   another helper edits it): add `kotlin("plugin.serialization") version "2.4.20"`; a
   `commonMain` srcDir `../../../sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/feature/device_sync`
   with `kotlin.exclude("PairedDeviceStore.kt", "MulticastLock.kt")`; in `mingwX64Main` a second
   srcDir for the mingw `feature/device_sync` with
   `kotlin.exclude("DeviceSyncFactory.mingw.kt", "MulticastLock.mingw.kt")`; dependency
   `org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0`. The common `DeviceSync.kt` has
   `expect fun createDeviceSync`, so the test project needs its own `actual` in a file with
   `package com.michelle.raouf.fetch_jet.feature.device_sync` (it can build a `WinDeviceSync`
   with a test store).
3. Build and fix compile errors (always through the lock, add `--offline`):
   `bash desktopNative/tools/one-build.sh ./gradlew.bat -p desktopNative/tests/winhelpers linkDebugExecutableMingwX64 --priority low --offline`
4. Then `bash desktopNative/tools/one-build.sh ./gradlew.bat :sharedLogic:compileKotlinMingwX64 -Pfetchjet.nativeDesktop=true --priority low --offline`
5. Write the tests, switch the factory, run `bash desktopNative/tests/winhelpers/run.sh sync`.

Places most likely to need a fix at the first compile (written from reading, not compiled):
`kotlin.time.Clock` (opt-in?), `createCleaner` / `nativeHeap.free` in `WinLock`,
`COMPUTER_NAME_FORMAT.ComputerNameDnsHostname`, type of `INVALID_SOCKET`, `return` inside
`lock.withLock { }` in `start()`, `(socket as? Selectable)?.descriptor`, whether `kotlinx.io`
is visible to `sharedLogic` (else use ktor's `buildPacket`).

## Test plan I had settled on

- Instances in one process on **different loopback addresses with the same non-default ports**
  (A = 127.0.0.1, B = 127.0.0.2, C = 127.0.0.3; e.g. UDP 28481, TCP 28482), `bindHost` set,
  `sendBroadcast = false`, and `winSetEnv("FETCHJET_SYNC_NO_BROADCAST", "1")`. Reason: the
  protocol has one discovery port, so two instances on one address with different ports cannot
  find each other without a test-only "peer port" in production code. Binding only loopback
  also avoids the Windows Firewall prompt (a wildcard bind in the test EXE could show it). Not
  yet tried: that 127.0.0.2 binds and keeps its source address on this PC.
- Cases: connect by IP then discovery through direct announces; equal 6-digit code; link only;
  scratch folder (several chunks, empty file, exactly 65536 bytes, Arabic name) and finished
  file, both directions, SHA-256 compared; hidden device (probe answered, not listed); `pending`
  then confirm; `declined`, `expired`, `blocked`, `busy`, `wait:N`, `not paired`, `bad key`;
  unpair both ways; TCP port fallback; stop / start; raw-socket cases (`..` file name, unknown
  frame, oversized frame, silent connection); a probe from a socket that is then closed (the
  `SIO_UDP_CONNRESET` case).
- JVM cross-check (no download needed, everything is in the Gradle cache: Kotlin 2.4.20 with
  the serialization plugin, coroutines 1.11.0, serialization-json 1.12.0 for JVM; Java 21 on
  PATH): a small Gradle project `desktopNative/tests/winhelpers/jvmpeer/` that compiles the
  **real** `DeviceSyncImpl.jvmAndroid.kt` (a copy patched at build time: ports from system
  properties, binds on 127.0.0.x, `NO_BROADCAST = true`; fail the build if a replacement does
  not match exactly once) with a fake `PairedDeviceStore` object, and a `main` driven over
  stdin / stdout by the native test (`WinProcess.start(pipeStdin = true)`). Never run the real
  JVM engine unpatched: it binds 28461 / 28462 on all interfaces and would talk to the owner's
  running app. Fallback if that proves too heavy: a single-file `java SyncPeer.java` in the
  style of `Vectors.java`, and say so in the report.
- Cheap extra cross-checks: `winHostName()` against PowerShell
  `[System.Net.Dns]::GetHostName()`; `winBroadcastAddresses()` against Java's
  `NetworkInterface` list (reading only, no packet).

## Things learned (all from reading, none measured by me)

- ktor-network 3.6.1 on mingwX64 selects with `WSAEventSelect` + `WSAWaitForMultipleEvents`
  (string table of `ktor-network-mingwX64Main-3.6.1.klib`), not `select()`. The limit is still
  64 sockets per selector, and going over it probably kills the selector: keep the inbound cap.
- ktor's UDP receive loop ends for good on any socket error (`DatagramSocketNative`, catches
  `PosixException` and stops). On Windows a datagram sent to a closed port makes the next
  `recvfrom` on the same socket fail with 10054, and the JVM replies to probes **from the listen
  socket**. Hence `winUdpIgnoreUnreachable` plus the re-bind loop in `discoveryListenLoop`.
- ktor `Socket.close()` drains what was flushed, then closes; `Selectable.descriptor` is public
  on native; `reuseAddress` defaults to false (so the TCP bind fails when 28462 is taken, as the
  JVM's exclusive bind does); received datagrams carry the sender as a numeric `hostname`.
- `platform.posix` has `WSAIoctl`, `WSAStartup`, `socket`, `closesocket`; `platform.windows`
  has `GetComputerNameExW`, the critical-section calls; `GetAdaptersAddresses` is not bound.
  Signatures: `E:\tools\probes\klibdump\posix.txt` / `windows.txt`.
- `PairedDeviceStore` is used only by the sync implementations, so delegating to it keeps one
  `devices.json` format with the JVM app.
- The other session added `platform/win/WinLoopbackProxy.kt` tonight: not mine, left alone.
- Read-only sources I unpacked (may be gone later, re-create from the Gradle cache):
  `ktor-network-3.6.1-sources.jar` and `ktor-io-3.6.1-sources.jar`.

## 2026-11-10 (second session): compiled, tested, factory switched

State in one line: **the engine is in the tree, compiles, and passed every test written for it;
it has not run inside the real EXE and not against a real second device.**

- Restored from `desktopNative/wip/device-sync/` (WinLock.kt was already in place, left as is):
  `platform/win/WinLan.kt`, `feature/device_sync/DeviceSyncStore.mingw.kt`,
  `DeviceSyncIo.mingw.kt`, `DeviceSyncImpl.mingw.kt`. They show as `??` in `git status` now
  (the old `AD` entries are gone). The copies in `desktopNative/wip/device-sync/` are stale
  duplicates and can be deleted.
- First compile had no error. Only change to the draft: `start()` launches its loops in
  `CoroutineScope(scope.coroutineContext + job)` instead of `scope.launch(job)` (deprecated).
- `DeviceSyncFactory.mingw.kt`: `WinDeviceSync` with `SharedDeviceStore` (delegates every call to
  the common `PairedDeviceStore` under one `WinLock`, so `devices.json` keeps the JVM format),
  `tempDir = "$settingsDir/device-sync-tmp"`, platform name `"<host before the first '.'> · Windows"`
  (`Windows` when blank / localhost). Production binds `0.0.0.0` on 28461 / 28462 like the JVM app,
  so the first "visible" in the real EXE may show the Windows Firewall prompt (same as the JVM app).
- `bash desktopNative/tools/one-build.sh ./gradlew.bat :sharedLogic:compileKotlinMingwX64 -Pfetchjet.nativeDesktop=true --priority low --offline`:
  BUILD SUCCESSFUL (only the old expect/actual Beta warnings of `MulticastLock`).
- winhelpers wiring (`desktopNative/tests/winhelpers/build.gradle.kts`): serialization plugin
  2.4.20, `commonMain` srcDir = common `feature/device_sync` minus `PairedDeviceStore.kt` /
  `MulticastLock.kt`, second `mingwX64Main` srcDir = mingw `feature/device_sync` minus
  `DeviceSyncFactory.mingw.kt` / `MulticastLock.mingw.kt`, serialization-json 1.12.0. The test
  project's own `actual fun createDeviceSync` and the in-memory `MemoryDeviceStore` are in
  `src/mingwX64Main/kotlin/DeviceSyncTestSupport.kt`.
- `run.sh sync` (`DeviceSyncTests.kt`, in the default mode list): A / B / C on 127.0.0.1 / .2 / .3,
  UDP 28481, TCP 28482, bound to their own address, no broadcast, short timings (announce 300 ms,
  expire 1.5 s, accept wait 4 s, pair gap 1.5 s, first frame 1.5 s). **84 passed, 0 failed, run
  twice.** Covers: winHostName = .NET `Dns.GetHostName()`; 127.0.0.2 / .3 bind and keep their
  source address; TCP port fallback (C's 28482 taken -> other port, A lists C on it and sends to
  it); pair by IP with equal 6-digit codes; discovery through direct announces; link only;
  scratch folder (200000 B, empty, 65536 B, Arabic name, manifest, a missing path skipped) and
  finished file (manifest `0 0 150000` + part0), both directions, SHA-256 equal; busy; wait:N;
  pending then confirm; hidden (port closed, not listed, its probes answered, connect refused);
  not paired; declined; expired; blocked (send and new pairing) and unblock; bad key and back;
  unpair notice both ways; stop / start (port closed and reopened, A hears B again); raw sockets:
  unknown frame, oversized frame, silent connection closed after 1.5 s, `..` name refused with
  no folder left, `..\..\evil.txt` stored as `evil.txt` inside `recv-*`, tampered chunk
  (`bad chunk`), unknown id (`not paired`); probes sent from 127.0.0.2 while nothing listened on
  127.0.0.2:28481 (A's replies hit a closed port): A's listener kept working, the log has no
  `listen stopped`, so no re-bind was needed.
- `run.sh syncjvm` (`DeviceSyncJvmTests.kt` + `tests/winhelpers/jvmpeer/`): the peer is the
  **real** `DeviceSyncImpl.jvmAndroid.kt` (and the real `CryptoBox`, models), copied at
  configuration time with 7 exact replacements (ports from `-Dfj.udp/-Dfj.tcp`, every bind on
  `-Dfj.host`, `NO_BROADCAST = true`); the build fails if a replacement does not match exactly
  once. Fake in-memory `PairedDeviceStore`. Driven over stdin / stdout (tab-separated commands).
  Build: `bash desktopNative/tools/one-build.sh ./gradlew.bat -p desktopNative/tests/winhelpers/jvmpeer peerClasspath --priority low --offline`
  (fully offline, ~1 min), then `bash desktopNative/tests/winhelpers/run.sh syncjvm`.
  **30 passed, 0 failed.** Native on 127.0.0.1, JVM on 127.0.0.3: pairing both ways with equal
  codes, pending both ways, link, scratch folder both ways (SHA-256, Arabic name), finished file
  JVM -> native, unpair notice both ways, hidden native device probing the JVM, JVM drops it.
- Not done / open:
  1. The real EXE / `fjcore.dll` was not rebuilt; the Devices page and the send sheet were not
     run against the real engine.
  2. No test with a real second device on the LAN (broadcast, the firewall prompt, a phone).
  3. Not tested: more than 16 inbound connections at once; a send cancelled while streaming
     (the JVM has no cancel there either); `winBroadcastAddresses()` only printed
     (`192.168.8.255, 172.28.111.255` on this PC), not compared with Java's list.

## 2026-11-10 (third session): no open port unless the owner asked for it

Bug: `WinDeviceSync.start()` bound UDP `0.0.0.0:28461` (and an announce socket) at every app
start, with "Visible to other devices" off and the Devices page hidden: a Windows Firewall
prompt at each start and a port the owner never turned on.

What the code does now (`DeviceSyncImpl.mingw.kt`):
- `start()` only marks the engine started. The discovery listener (28461), the announce socket
  and the expire loop run in one `network` job that exists only while
  `visible || active`; the TCP server (28462) only while `visible` (as before).
  `applyVisibility()` (under `serverGate`) handles both; `applyNetwork()` cancels and joins the
  job when it is no longer wanted (log `FJSYNC discovery on/off`).
- `active` = the send sheet is open. New `DeviceSync.setActive(active)` (default no-op in the
  common interface, so JVM / Android / iOS are unchanged); `initDeviceSync()` in
  `DashboardDeviceSync.kt` feeds it `uiState.deviceShareTaskId != null`, so it covers open,
  dismiss and the sheet closing itself after a successful send.
- Sending, pairing (also by IP) and unpair are outgoing TCP connects: they work with nothing
  listening. While hidden and the sheet closed, the last seen devices stay in the list (the
  expire loop does not run); they are re-probed and expired as soon as the sheet opens.
- Wire protocol unchanged.

Tests (copied winhelpers.exe, scratch work folder):
- `run.sh sync`: **89 passed, 0 failed** (84 old + 5 new in `realPortChecks`, node D on
  `127.0.0.4` with the real ports 28461 / 28462, sockets read with `Get-NetTCPConnection` /
  `Get-NetUDPEndpoint`; `netstat -ano` did not list a 127.0.0.4 TCP listener on this PC):
  hidden + idle = nothing bound; sheet open = UDP 28461 bound, no TCP; sheet closed = released;
  visible = TCP 28462 and UDP 28461; hidden again = nothing.
- `run.sh syncjvm` (peer rebuilt): **30 passed, 0 failed**.
- `corehost/test_devices.py` (debug `fjcore.dll`, own server.py on 127.0.0.1:18290, scratch
  home): **24 checks, 0 failed**. A sampler of `Get-NetUDPEndpoint -LocalPort 28461` /
  `Get-NetTCPConnection -LocalPort 28462` during the run: nothing bound before the sheet opened,
  UDP `0.0.0.0:28461` while it was open, released after "close"; TCP 28462 never. Core log:
  `FJSYNC start ... visible=false`, `discovery on visible=false active=true`, `discovery off`.
- Not done: the JVM `:sharedLogic:compileKotlinJvm` was not run (the common change is a default
  interface method and one collector); the real EXE was not run; no real second device.

## 2026-11-10 (fourth session): the receiver accepts each send (design, written before the code)

Owner's request: before a paired device receives a send, its user sees the file name, the size
and the sender's name with Accept / Reject. 60 s without an answer = refused. The sender then
shows "The other device did not respond, try again" (ar: "الجهاز الآخر ما ردش، حاول تاني").

No new frame and no new state machine: it reuses the pairing-code path that already exists in
all three engines (JVM / Android, mingw, iOS).

Receiver, on `T_SEND_META` from a paired device whose meta opens:
- `tag` = sender id + url + file name + mode + file sizes (the sender rebuilds the same meta on
  every retry, so each poll has the same tag).
- the tag was accepted in the last 2 min -> remove it, answer `T_READY`, stream as before.
- the offer on screen has this tag -> `T_ERR "pending"` (the sender keeps polling).
- the tag ended in the last 2 min (`endedPairings`, keyed by the tag) -> `T_ERR "no-answer"`.
- something else is on screen (a pairing code or another offer) -> `T_ERR "busy"`.
- otherwise: `pending = Pending(device, key, offer = tag)`, `pairing = PairingState.Offer(sender
  name, sender id, file name, size)`, a timer of `offerWaitMs` (60 s) ends it with
  `endPairing(p, "no-answer")`, answer `T_ERR "pending"`.
- Accept = `confirmPairing()`: for an offer it stores the tag as accepted (no `addPairing`).
  Reject = `cancelPairing()`: for an offer `endPairing(p, "no-answer")`.

Sender: unchanged polling (`"pending"` = wait, 1.5 s). "no-answer" is an ordinary `Fail`: the
pairing is kept (only blocked / declined / expired drop it). The sender's own safety deadline is
`acceptWaitMs + offerWaitMs` (a pairing code accepted late can still be followed by an offer).
The UI translates "no-answer" (`deviceSyncReason`).

Compatibility: new sender -> old receiver: the old one answers `T_READY` at once (no prompt,
as today). Old sender -> new receiver: "pending" = wait, then "no-answer" = a plain failure, the
pairing is kept. The JVM sender now gives up on a receiver that does not answer the meta within
30 s (it used to block without a timeout).

UI: `PairingState.Offer` shows in the same pairing dialog (Compose `DevicePairingDialog`, used on
JVM / Android / iOS; native `PairingDialog` in `DeviceSheet.slint` via `PairingCode.OFFER`). iOS
has no SwiftUI device dialog and `NativeDeviceSyncBridge.swift` only carries sockets and crypto,
so the Swift side does not change.

Results (fourth session, native scope; the owner asked to stop after native):
- `run.sh sync` 106 passed, 0 failed (offer: name / size / sender, wait, accept, reject ->
  "no-answer", timeout -> "no-answer", raw sends asked first).
- `run.sh syncjvm` (peer rebuilt; it compiles the changed JVM file) 37 passed, 0 failed
  (native <-> jvm accept, reject, 3 s timeout via `-Dfetchjet.sync.offerWaitMs`).
- `corehost/test_devices.py` (debug `fjcore.dll` rebuilt) 28 checks, 0 failed.
- Written but not built: `androidUi` `DevicePairingDialog` offer branch, `DashboardDeviceSync`
  guard, `:sharedLogic:compileKotlinJvm`, `:androidApp:assembleDebug`. iOS engine
  (`DeviceSyncImpl.ios.kt`) not changed yet: an old-style receiver there (no prompt).
- Real EXE not rebuilt or run; no second real device.

## 2026-11-10 (fifth session): receive progress and "Preparing file" on the receiver

Owner's design. What the receiver now sees:
1. Every send from a paired device (link only or transfer, any engine: direct, media) shows the
   Accept / Reject prompt first. Already true in the mingw and JVM engines; now also in the iOS
   engine (it had no offer path: added the same one, `offerRefusal` / `acceptedOffers`,
   "no-answer", sender deadline `ACCEPT_WAIT_MS + OFFER_WAIT_MS`).
2. After Accept of a send with bytes, the prompt turns into "Receiving a download" (file, "From
   <device>", bar + percent, Close). Common model: `ReceiveState` (Receiving(deviceName, fileName,
   fraction; < 0 = unknown) / Done / Failed) and `DeviceSync.receiving` + `clearReceiving()`, both
   with defaults. Fraction = bytes received / sum of `FileMeta.size`; total 0 (sender without sizes)
   = moving bar. Accepted but the sender never streams within 30 s (`receiveStartMs`) = Failed
   "no-answer". Done closes the dialog (the old toast stays). Close hides it (`OnDismissReceiveClicked`;
   native: `PairingCancel` with no pairing on screen). Link only: no progress.
3. The new task is enqueued with `currentStepText = "Preparing file"` (`STEP_PREPARING_FILE`,
   `isPreparingFile()` = that step while QUEUED / IDLE / CONNECTING). Rows show "Preparing file ·
   48 MB / 355 MB" (transfer) or "· 355 MB" (link) with a moving bar in the primary colour.
   HTTP and media `updateTaskState` drop the step once the status leaves those states
   (`withoutStalePreparingStep`); media keeps it through its CONNECTING instead of "Connecting...",
   then its own "Preparing download..." replaces it. Strings `stepPreparingFile` (ar "تهيئة الملف"),
   `sysDeviceReceivingTitle`, `sysDeviceReceiveFailed`: AppLocalization + 15 providers + gen.py.

Wire: no new frame. `SendPayload.totalBytes` / `downloadedBytes` (optional, default 0, display only:
`enqueueTask(displayTotalBytes, displayDownloadedBytes)`; the HTTP engine overwrites them at its
first DOWNLOADING update). `FileMeta.size` now defaults to 0 when decoding. Every released decoder
(v1.3.0, v1.3.1 JVM and iOS, and mingw) has `ignoreUnknownKeys = true`, checked in the tags, so old
receivers ignore the new fields; old senders already send sizes.

Tests (loopback, test ports): `run.sh sync` 130/0 (one run had 129/1: "A->C over the fallback
port", could not reach, not in the changed code; rerun 130/0), `run.sh syncjvm` 44/0,
`test_devices.py` 41/0. Pictures in `E:\tools\probes\ui\shots-receive\`
(`shots_settings.py receiv preparing`).

Builds (fifth session): debug `fjcore.dll` built 18:19 (`desktopNative/core/build/bin/mingwX64/debugShared/`)
and the host `desktopNative/build/Release/FetchJet.exe` (+ copied `fjcore.dll`). `:sharedLogic:compileKotlinJvm`
and `:androidApp:assembleDebug` BUILD SUCCESSFUL. A later core rebuild (for the primary bar colour while
preparing, `barColorFor` in `TaskText.kt` / `TaskRows.kt`) failed on files another session was moving
from `mingwX64Main` to `nativeDesktopMain` (`HostHooks` unresolved), not on this work: the 18:19 DLL has
everything except that colour (the preparing bar is yellow in the "Waiting" group during the 4 s hold).
Other suites on the 18:19 DLL: test_download 29/0, test_home 41/0. A `sync` run while a core build ran
in parallel failed 24 checks (A expired B at the 1.5 s test expiry); rerun with nothing building: 130/0.
iOS (`DeviceSyncImpl.ios.kt`) written, not compiled (no Mac). Swift bridge unchanged.
Owner to test with the phone: Android -> PC and PC -> Android, link only (prompt, no progress, row
"Preparing file") and transfer (prompt, receive bar to 100 %, row "Preparing file · x / y").

## 2026-11-10 (sixth session): one shared engine instead of three copies

The JVM / Android, mingw and iOS engines were the same protocol written three times. Now:

- `commonMain/.../feature/device_sync/`: `DeviceSyncEngine.kt` (flows, start / stop / visible /
  active, TCP server with the 16-connection cap, discovery job), `SyncDiscovery.kt` (announce,
  probe, listen with re-bind, expire), `SyncPairing.kt` (hello, code, busy / wait:N / blocked,
  declined / expired / no-answer, offers, unpair), `SyncReceiver.kt` (first frame, meta, stream to
  files, receive progress), `SyncSender.kt` (retry, "pending" polling, deadline), `SyncFrames.kt`,
  `SyncWire.kt`, `SyncConfig.kt` (ports, `DeviceSyncTiming`, `DeviceSyncConfig`), `SyncPlatform.kt`
  (UDP / TCP / crypto / files / lock interfaces), `DeviceSyncStore.kt` (moved from mingw);
  `SharedPairedDeviceStore` (in `PairedDeviceStore.kt`) wraps the global store under a lock.
- Platform files: `jvmAndroidMain/JvmDeviceSyncPlatform.kt`, `mingwX64Main/WinDeviceSyncPlatform.kt`
  (`winDeviceSync(...)`, same parameters as the old `WinDeviceSync` constructor),
  `iosMain/IosDeviceSyncPlatform.kt`. Deleted: `DeviceSyncImpl.jvmAndroid.kt`,
  `DeviceSyncImpl.mingw.kt`, `DeviceSyncIo.mingw.kt`, `DeviceSyncStore.mingw.kt`,
  `DeviceSyncImpl.ios.kt`, `IosDeviceSyncIo.kt`. Swift bridge unchanged.
- Per-platform choices kept as config: mingw opens UDP only while visible or the sheet is open
  (`alwaysDiscover = false`) and stamps log lines; JVM / Android / iOS listen from start to stop
  as before; iOS sweeps its /24 every 30 s (`sweepEveryMs`).
- Behaviour that is now the same everywhere (the mingw rules; nothing on the wire): read timeouts
  (first frame 20 s, reply 30 s, idle 120 s; JVM by `soTimeout`, iOS bridge has none), at most 16
  inbound connections, a failed receive deletes its `recv-*` folder, a connect failure reads
  "could not reach the device" (JVM showed the Java message), a bad chunk reads "bad chunk", an
  exception in a connection is logged instead of escaping the coroutine. The JVM test hooks
  `-Dfetchjet.sync.offerWaitMs` / `receiveStartMs` moved into the JVM peer (production reads no
  system property).
- Tests: winhelpers compiles the common folder minus `PairedDeviceStore.kt`; the JVM peer compiles
  the shared sources directly (`DeviceSyncConfig` ports / host instead of 7 text patches).
  `run.sh sync` real-port checks are opt-in now (`FJ_TEST_ARGS=realports=1`). New: winhelpers mode
  `syncpeer` (stdin-driven native engine) and `tests/winhelpers/emulator_sync.py` (emulator only,
  `-s emulator-5554`; app made visible through `behavior.properties`; `adb forward tcp:28472 tcp:28462`;
  native peer hidden, binds nothing; files served from 127.0.0.1:18295 = 10.0.2.2 in the emulator).
  K/N `readlnOrNull` blocks the peer's stdout until the next stdin line, so the script sends `tick`
  lines while it waits.
- Results: sync 130/0 then 125/0, syncjvm 44/0, test_devices 41/0, emulator 17/0, JVM / Android / mingw /
  iOS-klib compiles OK, `:sharedLogic:jvmTest` 28/29 (the failure is `UpdateWebPageTest`, which
  needs GitHub).
- Not done: the iOS app was not linked or run (needs the Mac: `./gradlew :sharedLogic:compileKotlinIosSimulatorArm64`
  passes on Windows, then Xcode + simulator pairing with a desktop); Linux keeps its inert factory;
  the emulator also reached the internet on its own (Google services and the app's start-up tool check).
