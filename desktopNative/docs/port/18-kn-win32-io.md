# 18. Kotlin/Native on Windows: processes, HTTP, sockets, zip

Finished 2026-11-09. Code and tests are in the repository (see report 17 for the layout):
`platform/win/WinProcess.kt`, `WinZip.kt`, `WinHttpEngine.kt`, tests in
`desktopNative/tests/winhelpers` (`process`, `zip`, `net`, `rawhttp` modes), servers in
`desktopNative/tools/testserver` (`README.md` there). Everything ran against 127.0.0.1.

## 1. Local test servers

`tools/testserver/server.py` (files of any size with checkable content and SHA-256, Range, HEAD,
throttle, connection cap with 429 / 503, redirects of four kinds, basic auth, chunked, no length,
ignored Range, slow first byte, mid-body disconnect, ETag, HLS playlists, counters, request log)
and `proxy.py` (HTTP + SOCKS5 proxies, login optional, host map). `server.py --selftest` runs 35
checks. `servers.sh start` runs the usual set.

## 2. Processes (`WinProcess.kt`): 17 checks pass

`WinProcess.start(command, environment, workDir, pipeStdin, mergeStderr, captureOutput, detached,
rawCommandLine)` and the suspend wrapper:

```kotlin
suspend fun winRunProcess(
    command: List<String>,
    environment: Map<String, String?> = emptyMap(),   // added to the current one; null removes
    workDir: String? = null,
    stdinBytes: ByteArray? = null,                    // written, then stdin is closed
    mergeStderr: Boolean = false,                     // like redirectErrorStream(true)
    timeoutMs: Long = -1,
    keepOutput: Boolean = true,                       // false: lines only go to the callbacks
    rawCommandLine: String? = null,                   // for cmd.exe /c lines that must not be re-quoted
    onStarted: ((WinProcess) -> Unit)? = null,
    onStdoutLine: ((String) -> Unit)? = null,
    onStderrLine: ((String) -> Unit)? = null,
): WinProcessResult                                   // exitCode, timedOut, stdout, stderr
```

How it works:

- `CreateProcessW` with `CREATE_NO_WINDOW` (the child reports no console window),
  `CREATE_UNICODE_ENVIRONMENT`, `CREATE_SUSPENDED`; three pipes or `NUL`.
- **Only the three standard handles are inherited** (`PROC_THREAD_ATTRIBUTE_HANDLE_LIST`). Without
  this a child would also inherit every inheritable handle of the app, for example the listening
  socket of the browser bridge or another child's pipe (which then never reports end of file).
- The child is put in a **job object** with `KILL_ON_JOB_CLOSE` before it runs. `kill()` ends the
  whole tree; if the app dies its children die too. `detached = true` skips the job (for a
  browser, the updater script): tested, the detached child finished its work after its handle was
  closed.
- Arguments are quoted by the C runtime rules (`winQuoteArgument`): 16 awkward arguments (spaces,
  quotes, trailing backslashes, empty, Arabic, emoji, `%`, `&|<>^`, tabs, UNC paths) arrive
  unchanged in Python.
- stdout and stderr are read at the same time by two readers: 200 000 + 50 000 lines in 0.95 s,
  no deadlock. Output is split into lines at `\n`, `\r\n` and `\r` (as Java's `readLine` does:
  ffmpeg and yt-dlp progress lines end with `\r`), on bytes, so a UTF-8 character cut between two
  reads is still decoded whole.
- Timeout: the tree is killed and `timedOut` is true (child and grandchild gone in 2.04 s for a
  2 s limit). Cancelling the coroutine kills the process at once.
- 8 processes at once: outputs never mix.
- Exit code, extra environment variable, Arabic working directory, stdin data: all checked. A
  program that does not exist throws `WinException` with code 2.

Python children print UTF-8 only with `-X utf8` or `PYTHONIOENCODING=utf-8`; the media code must
pass that to yt-dlp's Python build as the JVM code does.

## 3. HTTP client: the ktor WinHttp engine is not used

ktor 3.6.1's `ktor-client-winhttp` engine passed most checks but has three faults:

1. **A cancelled or abandoned download keeps its TCP connection open** (the server shows the
   connection as active for as long as the process lives; closing the `HttpClient` does not help).
   A paused download would keep a connection slot on the server: with a server that allows one
   connection per IP, the next attempt gets 429. Raw WinHTTP (sync and async) closes the
   connection when the request handle is closed (`rawhttp` mode), so the fault is in the engine.
2. No proxy login for HTTPS (the `Proxy-Authorization` header is not sent on `CONNECT`), a proxy
   URL with a user name is rejected, SOCKS is refused.
3. Half the speed (14 MB/s against 32 MB/s for the same local 200 MB in debug builds).

`WinHttpEngine.kt` is FetchJet's own ktor engine on the raw `platform.winhttp` API (async mode,
one status callback, no blocked threads): `HttpClient(WinHttpNative) { engine { sslVerify;
proxy; proxyUser; proxyPassword; readBufferBytes } }`. `WinHttpNative.registerAsDefault()` makes
`HttpClient { }` in common code use it (call it once at start; `sharedLogic` must **not** depend
on `ktor-client-winhttp`, or two engines register).

Results, 48 checks pass (debug and release):

| Topic | Result |
|---|---|
| protocol | HTTP/1.1 only (HTTP/2 is never enabled), cookies and redirects of WinHTTP switched off (ktor handles redirects) |
| headers | `Range`, `Cookie`, `Referer`, `User-Agent`, `Authorization`, custom headers arrive as set; a plain GET sends only `Connection: Keep-Alive`, `Accept: */*`, `User-Agent`, `Host` |
| bodies | `Content-Length`, chunked, and "until the connection closes" all read correctly; 401 / 429 / 503 come back as responses |
| streaming | 200 MB through `bodyAsChannel`: 35 MB/s (the Python server's limit), private bytes 3 MB before, 13 MB at the peak |
| parallel | 16 and 32 ranged requests ran on 16 and 32 connections at once (the server counted them): WinHTTP has no per-server limit |
| redirects | `followRedirects = false` shows the 302 and `Location` (absolute, relative, root, protocol-relative); `true` follows all four and a chain of 5; a loop ends with `SendCountExceedException` |
| cancel | cancelling the coroutine, leaving `execute { }` early, or closing the client closes the connection at once (server: active 0, no more bytes) |
| errors | refused connection, unknown host, connection cut in the middle (also chunked): `WinHttpException` (an `IOException`), or `ClosedByteChannelException` wrapping it while reading a body. **Never a `CancellationException`**, never a silent short body |
| timeouts | connect and socket timeouts come from the `HttpTimeout` plugin values (`WinHttpSetTimeouts`): `ConnectTimeoutException` / `SocketTimeoutException`; request timeout is ktor's `HttpRequestTimeoutException`. A slow but moving body does not time out |
| TLS | a self-signed certificate is refused ("The secure connection could not be trusted (12175): untrusted certificate authority"); `sslVerify = false` accepts it (tests only). Certificates are checked against the Windows store; revocation checking is off (WinHTTP default), so no extra delay |
| HTTP proxy | without login: http and https targets work. With login: set `proxyUser` / `proxyPassword` on the engine; plain http and https (`CONNECT`) both work; a wrong password gives 407. Loopback targets also go through the proxy (`<-loopback>`), as with OkHttp |
| SOCKS5 | **not possible with WinHTTP** (`socks=` speaks SOCKS4 and resolves names itself). Plan: a small loopback HTTP-proxy-to-SOCKS5 forwarder inside the app (accepts `CONNECT` and plain requests from WinHTTP, speaks SOCKS5 with user / password and remote DNS to the real proxy), written with blocking winsock on its own threads. To do in the foundations step |
| ktor Curl engine | not in the Gradle cache (it would be a download and brings libcurl); not checked, not needed |

Error texts (`winHttpErrorText`) are short English sentences with the code, because engine errors
are shown to the user: "Could not connect to the server (12029)", "The server name could not be
resolved (12007)", "The connection was closed by the server (12030)", "The connection timed out
(12002)".

Engine rules that were learned the hard way:

- The callback context is a `StableRef` that is disposed only when `HANDLE_CLOSING` arrives for
  the request handle (the last callback WinHTTP sends).
- One pending operation at a time per request, kept in an atomic slot that is taken exactly once
  (by the completion, the error or the close).
- `WinHttpCloseHandle(request)` is the cancel; it is called from `invokeOnCancellation` and when
  the call's job completes.

## 4. Sockets (ktor-network 3.6.1 on mingwX64)

- A TCP server on 127.0.0.1 speaking minimal HTTP/1.1 answered `curl` GET, a POST with a 3 MB
  body and an `OPTIONS` preflight with CORS headers; 40 requests at once and 300 one after
  another; binding a used port throws; after `close()` the port refuses connections.
- TCP client and UDP (send, receive with the sender's address, receive with a timeout, a datagram
  to 127.255.255.255 with `broadcast = true`) work.
- Caution: ktor-network's selector on Windows uses `select()`, and the `fd_set` of the bindings
  holds 64 sockets. Fine for the browser bridge and device sync (a few sockets); not the tool for
  many parallel tunnels (hence blocking winsock for the SOCKS forwarder).
- Sockets are inheritable handles by default: the handle list in `WinProcess` is what keeps them
  out of child processes.

## 5. Zip (`WinZip.kt`): 11 checks pass

`winUnzip(zip, targetDir, onEntry)`, `winZipEntries(zip)`, `winZipReadEntry(zip, name)`: a zip
reader on `platform.zlib` (stored and deflated entries, CRC-32 checked, UTF-8 and CP437 names,
no zip64, no encryption).

- A 56 MB archive (80 MB of content: Arabic, Chinese and emoji names, a 50 MB random file, a 30 MB
  compressible file, nested folders, an empty file, an empty folder) extracts in 0.2 s with the
  right SHA-256 for every file.
- Refused with `WinZipException`: an entry that climbs out of the target (`../evil.txt`), a cut
  archive, a damaged entry (checksum), a file that is not a zip.
- `C:\Windows\System32\tar.exe -xf` on the same archive: exit code 1, **3 of 10 files missing**
  ("Archive entry has empty or unreadable filename": the Unicode names). Not usable.

## 6. What this fixes in `ARCHITECTURE.md`

- Section 3: `ktor-client-winhttp` leaves the mingwX64 dependencies; `ktor-network` stays.
- Section 11: the helper list (`docs/port/helpers.md` is replaced by reports 17 and 18 plus the
  files themselves); the HTTP engine; child processes always through `WinProcess`.
- Section 13: the test program and the servers are the base of the engine tests.
