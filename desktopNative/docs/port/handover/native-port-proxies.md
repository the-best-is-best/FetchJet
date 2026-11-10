# Native port: SOCKS5 forwarder + speed-limit proxy (hand-over, stopped 2026-11-10 01:57)

## State on 2026-11-10 (second session)

Restored into the tree (`WinLock.kt` from `wip/device-sync`, the proxy files from `wip/proxies`),
mode `proxy` registered in winhelpers `Main.kt` and `run.sh`. winhelpers builds; `proxy` (all
groups) **37 passed, 0 failed** against the local test servers; `:sharedLogic:compileKotlinMingwX64
-Pfetchjet.nativeDesktop=true` up to date with these files (compiled). Not yet: a run inside the
app (SOCKS5 proxy in Settings > Network, yt-dlp with a speed limit).

Fixes made at the first compile and run:

- `WinLoopbackProxy.kt`: `THREAD_STACK_BYTES = 524_288uL` (an unsigned product is not a const);
  `upstream` copied to a local before the smart cast.
- WinHTTP sends requests for `127.0.0.1` directly, even with the `<-loopback>` bypass entry of
  `WinHttpEngine.kt` (no proxy log line, 3 ms). The tests therefore reach `127.0.0.1` targets
  through curl (IP-literal check) or a raw socket client `rawProxyFetch` (throttle group).
  In the app this only matters for downloads from 127.0.0.1 with a proxy set.
- Stats of https requests are read from the https server (each test server has its own `/stats`).
- Parallel group: the Python test server itself does not serve 120 throttled ranges at once
  (120 direct curl calls: `maxActive` 70 to 103, about 22 s instead of 5 s), so the check needs
  `maxActive >= 60` plus 120 x 206, right bytes and a forwarder peak of 120 tunnels.
- Open: private bytes stay at about 63 MB after the 120-tunnel rounds and a collection (peak
  150 MB, most of it the WinHTTP client holding 2 x 30 MB of bodies). The 400-tunnel `leak` group
  shows no growth (6652 -> 6988 KB, handles 440 -> 447), so it looks like retained allocator
  memory, not a leak; not proven.

## State in one line (first session)

> History (01:57 state). Out of date: the files are back in the tree, compiled, and `run.sh proxy`
> passes (see "State on 2026-11-10 (second session)" above). The sections below up to "Design"
> describe the first stop.

All code and the test file are written, **nothing was ever compiled and no test ran**. Because no
build was allowed before the stop, the unbuilt code was taken out of the repository again so the
shared build cannot break: the repository's working tree is exactly as it was before this task.

## Where the work is

Saved copies (same relative paths as in the repo): `E:\tools\probes\proxy-handover\`

| File (under `E:\tools\probes\proxy-handover\`) | Kind | Compiled? |
|---|---|---|
| `sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/win/WinLoopbackProxy.kt` | new, about 600 lines: the shared loopback HTTP proxy (winsock, threads, SOCKS5 client, rate limiter, counters) | never |
| `.../platform/win/WinSocksForwarder.kt` | replaces the stub: `portFor`, `closeAll`, `LOGIN_USER`, `loginPassword` | never |
| `.../feature/media_extractor/MediaThrottlingProxy.mingw.kt` | replaces the stub: thin wrapper on `WinLoopbackProxy` | never |
| `.../core/network/NetworkSettings.mingw.kt` | two lines changed in the SOCKS5 branch (`engine.proxyUser = WinSocksForwarder.LOGIN_USER`, `engine.proxyPassword = WinSocksForwarder.loginPassword`) | never |
| `desktopNative/tests/winhelpers/src/mingwX64Main/kotlin/ProxyTests.kt` | new: mode `proxy`, groups `forward refuse parallel cancel close leak throttle` | never |

In the repository now:

- `WinSocksForwarder.kt`, `MediaThrottlingProxy.mingw.kt`, `NetworkSettings.mingw.kt`: the original
  stubs, restored byte for byte from the git index (`git show :path`, read only). `git status` shows
  them as `A ` (no difference to the index). These compiled before this task.
- `WinLoopbackProxy.kt` and `ProxyTests.kt`: removed from the working tree. `git status` shows them
  as `AD`: something else (an IDE auto-add, not this helper: no `git add` was ever run) had staged
  them while they existed. The index entries were left alone (no git write commands allowed). They
  turn back into `A` / `AM` when the files are copied back.
- `Main.kt` and `run.sh` of `desktopNative/tests/winhelpers`: **not touched yet**.

## Dependency on the device-sync helper

The saved code uses `WinLock` (`lock.withLock { }`, a `CRITICAL_SECTION` wrapper) from
`platform/win/WinLock.kt`, a file the device-sync helper created at 01:47. At 01:57 `WinLock.kt` and
`WinLan.kt` were gone from the working tree again (`AD` in `git status`; not removed by this helper,
its author seems to have parked them the same way). At the next start:

- if `WinLock.kt` is back: nothing to do;
- if not: add an own lock to `WinLoopbackProxy.kt` and use it in `WinSocksForwarder.kt` too, for
  example `internal class WinSpinGate { private val held = AtomicInt(0); fun enter() { while
  (!held.compareAndSet(0, 1)) SwitchToThread() }; fun leave() { held.value = 0 } }` plus an inline
  extension `locked { }`. Three uses: `ProxyRateLimiter.lock`, `WinLoopbackProxy.lock`,
  `WinLoopbackProxy.speedLock`, `WinSocksForwarder.lock`.

## Not written yet

1. Registering the mode: in `Main.kt` add `"proxy" -> proxyTests(work, rest)` to the `when` and
   `proxy` to the usage line; in `run.sh` add `proxy` to the default `MODES`. Re-read both files
   right before editing (the device-sync helper adds its own mode there).
2. Every fix the first compile and the first test run will ask for.

## Next steps, in order

```
cd /e/tbib/FetchJet
cp -r /e/tools/probes/proxy-handover/sharedLogic /e/tools/probes/proxy-handover/desktopNative .
ls sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/win/      (is WinLock.kt there?)
bash desktopNative/tools/one-build.sh ./gradlew.bat -p desktopNative/tests/winhelpers linkDebugExecutableMingwX64 --priority low
bash desktopNative/tools/testserver/servers.sh status      (start only when they do not run: "start" kills and restarts them)
FJ_TEST_ARGS="only=forward" bash desktopNative/tests/winhelpers/run.sh proxy      (then refuse, parallel, cancel, close, leak, throttle, then all)
bash desktopNative/tools/one-build.sh ./gradlew.bat :sharedLogic:compileKotlinMingwX64 -Pfetchjet.nativeDesktop=true --priority low
```

- The winhelpers build command is taken from the old logs (`E:\tools\probes\winhelpers-build-16.log`
  shows the tasks `linkDebugExecutableMingwX64` / `linkReleaseExecutableMingwX64`); it was not run by
  this helper. That project compiles only the folder `platform/win` plus the test files, so the proxy
  core is tested there; the two actuals (`MediaThrottlingProxy.mingw.kt`, `NetworkSettings.mingw.kt`)
  are compiled only by the `:sharedLogic` build.
- Test options (through `FJ_TEST_ARGS`): `only=<groups>`, `server=18200`, `https=18443`,
  `socks=11081`, `socksopen=11082`. The tests read the proxy logs
  `<work>\servers\proxy-login.log` and `proxy-open.log` (work = `E:/tools/probes/winhelpers-work`).
- While iterating, copy the built exe somewhere else before running it if the other helper builds
  the same project (the link step replaces `winhelpers.exe`).

## Design (what the saved code does)

- One class `WinLoopbackProxy.start(upstream: WinSocksUpstream? = null, login: String? = null,
  speedLimit: (() -> Long)? = null)`: listens on `127.0.0.1:0`, `port`, `activeTunnels`,
  `downloadedBytes`, `uploadedBytes`, `downloadSpeedBytesPerSec()` (same smoothing as the JVM),
  `isOpen`, `close()`. `upstream == null` connects directly (the speed-limit proxy), otherwise through
  SOCKS5 (the forwarder).
- Threads: `CreateThread` + `staticCFunction`, blocking winsock, one acceptor thread and two threads
  per tunnel (one per direction), 512 KB stack reservation, 64 KB / 16 KB native buffers. No
  `select`, so no 64-socket limit. Not coroutines on purpose: the IO dispatcher of Kotlin/Native
  never gives worker threads back.
- Stop without handle reuse: a tunnel is stopped with `shutdown(SD_BOTH)` + `CancelIoEx` on both
  sockets under the proxy lock; `closesocket` runs only when both threads of the tunnel have ended.
  The listener is closed by the acceptor thread itself; `close()` wakes it with a loopback connect
  and waits for the thread, so the port is free when `close()` returns. `close()` does not wait for
  tunnels.
- SOCKS5: greeting `05 01 00` or `05 02 00 02`, RFC 1929 login, CONNECT with the host name
  (ATYP 3, DNS at the proxy) or the binary address for IP literals (`inet_pton`), 60 s limit for
  the negotiation, 30 s for the request head.
- Failures are answered as HTTP statuses with an English reason phrase (the engine shows
  "HTTP Error <code>: <reason>"): 407 for a refused / missing SOCKS5 login, 502 for an unreachable
  proxy or target, 504 for a timeout. Never 503 (the download engine treats 503 as "server busy").
- Plain `GET http://...`: request rewritten to origin form, `Proxy-*` headers dropped,
  `Connection: close` added; the response head also gets `Connection: close`. So one tunnel per
  plain request and a kept-alive connection can never carry a request for another host.
- The forwarder asks for its own login (`Proxy-Authorization: Basic`, user `fetchjet`, a random
  password per process) so other local programs or users cannot use the user's SOCKS5 account
  through the loopback port. `applyProxyLogin` gives it to the engine as `proxyUser` /
  `proxyPassword`. Evidence that WinHTTP sends it on the first request (no extra 407 round trip):
  `proxy-login.log`, lines of 22:51:54 (`OK` without a `407` before it for the engine login).
- Forwarders are kept for the life of the process (one per host / port / user / password);
  `WinSocksForwarder.closeAll()` exists for shutdown and tests. A forwarder whose acceptor died is
  replaced on the next `portFor`.
- Speed limit: port of the JVM token bucket (bucket `max(16 KiB, 20 %)`, a changed limit resets
  it). Two deliberate differences: the provider is asked at most every 100 ms (it allocates lists),
  and a read is `limit / 10` bytes (4 KB to 64 KB) so low limits stay smooth. Sleeps are cut into
  50 ms slices so `close()` is not held up.
- **The speed-limit proxy connects directly, as on the JVM**: a yt-dlp download therefore does not
  use the app's proxy (open question 2 of `docs/port/02-platform-feature.md`). Passing a
  `WinSocksUpstream` to `WinLoopbackProxy.start` in `LocalThrottlingProxy.start` would route it
  through a SOCKS5 app proxy; an HTTP app proxy would need a new upstream kind. Owner's decision.

## Things assumed and not yet proven (check these first)

1. `CancelIoEx` ends a blocking `recv` / `send` / `connect` of another thread (test group `close`).
2. Threads made with `CreateThread` give their Kotlin runtime data back at exit (group `leak`: 400
   tunnels, then a collection; a hang in `GC.collect()` there would mean they do not).
3. WinHTTP hands a non-200 answer to `CONNECT` (502 / 407 with the reason phrase) to the caller;
   if it reports its own error instead, the tests still pass ("fails cleanly"), only the text differs.
4. Likely compile trouble spots: the `if (...) 1 else 4` assigned to a `Byte`, `return` inside
   `memScoped` in `openSocket` / `numericAddress`, `toCPointer` / `toLong` on the thread handle,
   `ExperimentalEncodingApi` for `Base64`, overloads of `sendAll`.

## Facts learned that the tests rely on

- The winsock functions exist in `platform.windows` (`accept`, `bind`, `connect`, `recv`, `send`,
  `shutdown`, `closesocket`, `setsockopt`, `getsockname`, `GetAddrInfoW`, `inet_pton`, `CancelIoEx`);
  `WSASocketW` and `WSAData` are in `platform.posix`. Signatures: `E:\tools\probes\klibdump\`.
- `proxy.py` listens with a backlog of 5: 120 tunnels must be started with a small delay between
  them (the test uses 12 ms), not all in the same millisecond. `server.py` has a backlog of 128.
- A refused connection on Windows takes 1 to 2 s (the system retries), so the "proxy not running"
  checks allow 15 s.
- The other sessions use the same test servers: the tests prove "nothing reached the server" with
  the per-key counters (`/stats?of=<key>`), not with log line counts.

## Processes and ports

This helper started no build, no server and no other process, and opened no port. The test servers
(HTTP 18200, HTTPS 18443, proxies 18081 / 18082 / 11081 / 11082) were already running before it
started and were left as they were.
