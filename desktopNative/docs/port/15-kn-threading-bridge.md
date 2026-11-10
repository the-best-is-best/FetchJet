# 15. Kotlin/Native on Windows: Main dispatcher and the DLL boundary

Finished 2026-11-09. Kotlin 2.4.20, kotlinx-coroutines 1.11.0, lifecycle-viewmodel 2.11.0,
target `mingwX64`. Every line below comes from a DLL that was built and run by a console host
(`E:/tools/probes/kn-threading`: Kotlin sources, `host/host.cpp`, outputs in `run/`). The reusable
files were moved into the repository:

```
desktopNative/core/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/nativeapp/runtime/
    HostMainDispatcher.kt   Dispatchers.Main on the host's UI thread
    InstallMain.kt          installs it
    ChangeNotifier.kt       coalesced "channel changed" notifications
    CrashLog.kt             unhandled-exception hook, crash log, guarded { }
    CStrings.kt             UTF-8 strings across the boundary
```

## 1. Main dispatcher

### What exists without one

- `Dispatchers.Main` on mingwX64 is a placeholder: `Dispatchers.Main.immediate` and
  `Dispatchers.Main.dispatch(...)` throw `NotImplementedError("Dispatchers.Main is missing on the
  current platform")`.
- `viewModelScope` (lifecycle 2.11.0, `createViewModelScope`) catches that error and falls back to
  `EmptyCoroutineContext + SupervisorJob()`: `viewModelScope.launch { }` then runs on a
  `Dispatchers.Default` worker, on any thread. A view model written for one UI thread would be
  updated from many threads. So a real Main dispatcher is required, and it must be installed
  **before the first `ViewModel` is created**: a view model created earlier keeps its
  dispatcher-less scope for ever (tested).

### The dispatcher

`HostMainDispatcher(post)` wraps the host function
`post(void (*fn)(void*), void* arg)` ("run fn(arg) on the UI thread, callable from any thread").

- `dispatch` puts the block in an unbounded queue and posts **one** drain call to the host; the
  next post happens only after that drain started. A burst of 100 000 `launch(Dispatchers.Main)`
  from 4 threads used 94 host posts.
- A drain runs at most 4096 blocks or 8 ms, then posts itself again, so the host's event loop
  keeps painting.
- `immediate`: `isDispatchNeeded` is false on the UI thread (compared by Windows thread id), true
  elsewhere.
- `delay` on Main works without extra code (the default delay timer resumes through `dispatch`).
- `close()` (at shutdown): later dispatches cancel the coroutine and run its cancellation on
  `Dispatchers.Default`, so nothing hangs waiting for a UI thread that is gone.
- An exception that escapes a block is written to the crash log, the drain goes on.

### Installing it

`Dispatchers.injectMain(dispatcher)` is the hook kotlinx-coroutines has on Native. It is
`@PublishedApi internal`, so the call needs
`@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")`; Kotlin 2.4.20 compiles it with the warning
"compiler behavior is UNSPECIFIED". `kotlinx-coroutines-test`'s `Dispatchers.setMain` does exactly
the same call with the same suppression (its Native source says "required in K2"), so the hook is
as stable as that library. If a future compiler refuses the suppression, the fallback is to add
`kotlinx-coroutines-test` and call `Dispatchers.setMain(dispatcher)` (one small artifact, not in
the Gradle cache today; it wraps the dispatcher in a `TestMainDispatcher`).

### Results (27 checks, all pass)

- `launch(Dispatchers.Main)` and `withContext(Dispatchers.Main)` from background threads run on the
  UI thread and come back; `Main.immediate` runs inline on the UI thread and posts from elsewhere.
- `delay(200)` and `withTimeoutOrNull(100)` inside Main resume on the UI thread on time (215 ms,
  108 ms); 200 coroutines with delays all resume there; the UI thread is not blocked meanwhile.
- A `ViewModel` created after the install: `viewModelScope` is `Main.immediate`;
  `viewModelScope.launch` called on the UI thread starts inline; called from a background thread
  it moves to the UI thread.
- The `DashboardViewModel` pattern (`viewModelScope.coroutineContext + Dispatchers.IO`) runs off the
  UI thread; `ViewModelStore.clear()` calls `onCleared` and cancels both scopes.
- A `StateFlow` collected on Main while 4 Default and 4 IO workers update it 800 000 times: the
  collector sees values in order, on the UI thread only, and the final value.
- An exception in a coroutine on Main reaches its `CoroutineExceptionHandler`; Main keeps working.
- After `close()`: a coroutine parked on Main is cancelled and its `finally` runs; a new
  `launch(Dispatchers.Main)` is cancelled without running.
- `withContext(Dispatchers.Main)` round trip from a Default thread: 47 us (debug DLL).

## 2. The DLL boundary

| Point | Result |
|---|---|
| `@CName("fj_x") fun ...` | exported with that C name; loaded with `LoadLibrary` + `GetProcAddress` from an MSVC host, no import library needed |
| first call into the DLL | 0.7 ms (runtime start); later calls about 2 us |
| C function pointers | `CPointer<CFunction<(...) -> R>>` parameters, called with `invoke`; `staticCFunction` gives a pointer the host (or `qsort`) can call |
| strings | UTF-8 `char*` both ways. In: `toKString()` (invalid bytes become U+FFFD, no crash). Out: `malloc` + copy (`toMallocCString`), the host frees with `fj_free`. 100 KB string, release DLL: decode 0.40 ms, encode 0.30 ms, copy 2 us. Never return a Kotlin `String` from an export |
| objects | `StableRef.create(obj).asCPointer()` as an opaque handle; `dispose()` lets the GC free it |
| threads | 160 000 calls from 8 foreign threads at once: correct (atomics and `StateFlow.update` used). First call on a new thread 0.08 ms (release), 0.66 ms (debug) |
| threads made in Kotlin | `CreateThread` + `staticCFunction` runs Kotlin code fine |
| stack traces | have function names in a debug DLL (`kfun:...`), addresses only in a release DLL |

### Exceptions (each case run in its own process)

| Case | No hook | With `CrashLog.install` |
|---|---|---|
| exception leaves an exported function | process ends at once, exit code 3, "Uncaught Kotlin exception" on stderr | the hook writes the log; the process still ends (exit 3), or with `terminateOnUnhandled` exit code 70 after calling the host's fatal callback |
| exception inside `guarded("name", fallback) { }` | - | logged, the function returns the fallback, the host continues |
| uncaught exception in a coroutine (Default or Main) | process ends, exit 3 | logged, **the process keeps running** |
| coroutine with a `CoroutineExceptionHandler` | handler called, process continues | the same |
| exception in a `staticCFunction` callback | process ends, exit 3 | logged, process ends (exit 3, or 70) |
| access violation in native code | process ends (0xC0000005), nothing logged | the same: only a host `SetUnhandledExceptionFilter` can see it |
| `abort()` | exit 3 | with the `SIGABRT` handler: logged, exit code 71 |

Rules: every exported function and every `staticCFunction` body is wrapped in `guarded { }`;
`CrashLog.install(home/logs/native-crash.log, fatal, terminateOnUnhandled = true, quietAbort = true)`
runs first in `fj_start`. In a GUI program an uncaught Kotlin exception would otherwise end in
the C runtime's `abort()` message box.

(`_set_abort_behavior` does not link on mingw: the runtime is the old `msvcrt.dll`.)

### Process exit

With 8 delay loops, 8 blocking IO coroutines, 4 busy coroutines, a stalled HTTP request and 4
running HTTP streams alive:

| How the host ends | Result |
|---|---|
| returns from `main` | clean exit, code 0, no hang |
| `ExitProcess(0)` | clean exit |
| `fj_shutdown()`-style `cancelAndJoin` + `client.close()`, then return | 31 ms, clean |
| `FreeLibrary` on the Kotlin DLL | **crash** (access violation): never unload `fjcore.dll` |

### Debug and release

| | link time (low priority) | DLL size |
|---|---|---|
| `linkDebugSharedMingwX64` | 1 min 09 s | 14.5 MB |
| `linkReleaseSharedMingwX64` | 1 min 32 s | 6.3 MB |

(ktor client core + WinHttp, coroutines, serialization-json, lifecycle-viewmodel; the real core
is bigger.) Use the debug DLL while developing (stack traces with names), the release DLL for
measurements and for shipping.

## 3. The bridge shape (tested as `fj_start` / `fj_action` / `fj_snapshot` / `fj_call` / `fj_free` / `fj_shutdown`)

- `fj_start(config_json, post, notify, host, host_free)` returned in 1.7 ms; private bytes of the
  host went from 1.2 MB to 1.7 MB.
- Snapshots: each channel keeps its last JSON as a `ByteArray` in an atomic reference. A collector
  per channel (on `Dispatchers.Default`) encodes the new value, stores it and calls
  `ChangeNotifier.mark(channel)`; throttled channels then wait 250 ms before taking the next value
  (`StateFlow` keeps only the latest).
- `ChangeNotifier` keeps a bit mask of changed channels and posts **one** delivery to the UI
  thread; `notify(mask)` is called there once for all bits set meanwhile. Storm test: 8 workers
  made 718 388 state updates in 3 s; the host received **28** notifications.
- `fj_snapshot(channel)` copies the stored bytes: 8.7 us for a 112 KB tasks snapshot (450 rows).
  Encoding those 450 rows to JSON takes 1.7 ms in a release DLL (10.8 ms debug), off the UI thread.
- `fj_action(json)` runs the action at once when called on the UI thread, otherwise posts it to
  Main. Calls from 6 foreign threads at once worked.
- The core called a host service (`host("clipboardText", args_json)`), got UTF-8 back and freed it
  with `host_free`.
- An action that throws is logged by `guarded`; the host keeps running.
- `fj_shutdown()`: stop the notifier, cancel the scope, close the Main dispatcher, drain it, join
  with a 3 s limit.

## 4. What this fixes in `ARCHITECTURE.md`

- Section 4 stands: UI thread = `Dispatchers.Main`, installed in `fj_start` before any view model.
- Section 5: add `guarded` everywhere, the crash log, "never `FreeLibrary`", "never return a Kotlin
  `String`".
- Section 6: the snapshot / notification mechanism above is the implementation of "throttle" and
  "no change, no notification".
