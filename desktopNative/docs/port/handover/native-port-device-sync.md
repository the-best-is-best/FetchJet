# Native port: Send to device (device sync) for mingwX64 — hand-over

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
