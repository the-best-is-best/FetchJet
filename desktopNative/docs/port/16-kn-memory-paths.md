# 16. Kotlin/Native on Windows: memory, Unicode paths, build speed

Finished 2026-11-09. Kotlin 2.4.20, target `mingwX64`. Measured with a host EXE that loads a
release DLL holding the ktor client, coroutines, kotlinx.serialization and okio
(`E:/tools/probes/kn-memory-paths`: `run/mem.ps1`, `run/results/*.csv`, `run/results/paths-*.log`).

## 1. Memory

### Allocator (this decides everything)

Private bytes in MB of the same scenario with three binaries:

| State | `-Xallocator=std` | `pagedAllocator=false` | default |
|---|---|---|---|
| DLL loaded | 0.9 | 0.9 | 0.9 |
| runtime started | 1.2 | 1.2 | 5.0 |
| HTTP client created | 1.6 | 1.6 | 6.3 |
| after a 16 MB download, idle | 5.0 to 5.7 | 8.2 | 31.0 |
| 300 tasks to JSON, 4 times a second, 30 s | 8.9 to 9.9 | 6.3 | 22.9 |
| after `GC.collect()` | 6.9 to 7.1 | 7.7 | 25.2 |
| peak of a 200 MB burst of large arrays | 208 | 209 | 224 |
| 10 s after the burst | 5.5 | 5.7 | 23.2 |
| peak of a burst of 2 000 000 small objects | 360 | 350 | 239 |
| 10 s later, nothing done | 238 to 261 | 7.4 | 224 |
| after `GC.collect()` | 6.4 to 7.0 | 7.4 | 22.2 |
| 4 coroutines writing files | 7.6 to 8.0 | 7.4 | 32.5 |
| 16 coroutines writing files | 10.8 | 9.5 | 36.9 |
| 32 coroutines writing files | 11.0 to 11.4 | 10.7 | 53.1 |
| end, after `GC.collect()` | 10.5 | 11.2 | 53.2 |

- The default allocator keeps 22 to 53 MB where the other two keep 5 to 11 MB.
- `-Xallocator=std` works but the compiler prints "Std allocator is deprecated ... consider using
  `-Xbinary=pagedAllocator=false`".
- **`pagedAllocator=false` gives the same low numbers, is the supported option, and gives memory
  back faster** (7 MB ten seconds after the small-object burst, where `std` still held 240 MB
  until a collection ran).

Decision for every binary of the native app:

```kotlin
binaries {
    sharedLib {
        binaryOption("pagedAllocator", "false")
    }
}
```

GC settings stay at their defaults (`autotune = true`, `targetHeapBytes = 10 MB`,
`minHeapBytes = 5 MB`, `heapTriggerCoefficient = 0.9`, `regularGCInterval = 10 s`,
`pauseOnTargetHeapOverflow = true`): with this allocator memory returns by itself. A call to
`kotlin.native.runtime.GC.collect()` after a one-off burst (loading a large task cache, reading a
big playlist) is allowed but not needed. Not measured: `gc=cms` / `stwms`, `gcSchedulerType`,
`appStateTracking`, `latin1Strings`, `smallBinary` (variants are prepared in the probe's
`build.gradle.kts`).

### The HTTP engine's share (helper tests, release build)

- one 200 MB download through `bodyAsChannel`: 3 MB before, 13 MB peak, 9 MB after;
- 32 ranged downloads at once, streamed with 64 KB reads, three rounds: 21 to 23 MB while
  running, 18 MB after a collection;
- reading whole 2 MB bodies into memory (`readRawBytes`) 32 at a time makes up to 110 MB of
  garbage until the next collection: download code must stream, never collect bodies.

## 2. Unicode and long paths

Kotlin/Native for mingw links the old `msvcrt.dll`; its narrow file functions use the process
code page. Tested with five names (ASCII, Arabic, Chinese, emoji, mixed), a 250-character name and
two paths longer than 260 characters, under four host manifests:

| Host EXE manifest | okio `FileSystem.SYSTEM` | kotlinx-io | `platform.posix` narrow (`fopen`, `stat`, `opendir`...) | `platform.posix` wide (`_wfopen`...) | Win32 `W` helpers |
|---|---|---|---|---|---|
| none (code page 1252) | Unicode names work; long paths fail | every non-ASCII name fails | every non-ASCII name fails | work; long paths fail | all work |
| `activeCodePage` UTF-8 only | Unicode names work; long paths fail | work; long paths fail | work (not `stat` on the 250-char name, not long paths) | work; long paths fail | all work |
| `longPathAware` only | all work except `list`, `listOrNull`, `deleteRecursively` on long paths | every non-ASCII name fails | non-ASCII names fail | work except `_wopendir` on long paths | all work |
| **both** | all work except `list`, `listOrNull`, `deleteRecursively` on long paths | work except `metadataOrNull` on the 250-char name and `list` on long paths | work except `stat` on the 250-char name and `opendir` on long paths | work except `_wopendir` on long paths | **all work** |

With both settings also: `GetACP() == 65001`; `getenv("USERPROFILE")` returns a usable path for a
profile folder named `مستخدم 用户 😀`; `okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY` works;
`println` writes UTF-8 bytes. Without the manifest `getenv` returns a mangled path.

The manifest the host EXE must carry (`res/app.manifest`, embedded with `/MANIFEST:EMBED
/MANIFESTINPUT:` or through the `.rc` file):

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <assemblyIdentity type="win32" name="FetchJet" version="1.4.0.0"/>
  <application xmlns="urn:schemas-microsoft-com:asm.v3">
    <windowsSettings>
      <activeCodePage xmlns="http://schemas.microsoft.com/SMI/2019/WindowsSettings">UTF-8</activeCodePage>
      <longPathAware xmlns="http://schemas.microsoft.com/SMI/2016/WindowsSettings">true</longPathAware>
    </windowsSettings>
  </application>
</assembly>
```

(DPI awareness is added to the same file by the host work.) `activeCodePage` needs Windows 10
1903 or later; `longPathAware` also needs the system policy `LongPathsEnabled`, so a path over 260
characters can still fail on a PC where it is off.

Rules:

1. The shared code keeps using okio (28 files in `commonMain` do): with the manifest it is safe
   for every name. The only gap is listing or deleting a folder whose own path is longer than 260
   characters.
2. New Windows code uses the helpers in
   `sharedLogic/src/mingwX64Main/.../platform/win/WinFiles.kt` (`CreateFileW`,
   `GetFileAttributesExW`, `FindFirstFileW`, `MoveFileExW`, `GetDiskFreeSpaceExW`... with the
   `\\?\` prefix added inside): they work for every case above, with or without the manifest.
   Tested by `desktopNative/tests/winhelpers` (`files` mode, 12 checks).
3. Never use narrow `platform.posix` file functions. `winGetEnv` instead of `getenv`.
4. Programs built by Kotlin/Native itself (test runners) have no manifest: they must use the
   `W` helpers only.

### Disk details

- Free space: `GetDiskFreeSpaceExW` on a folder with a Unicode name works; a missing folder fails
  (ask the nearest existing parent).
- Pre-allocating 256 MB with `SetFilePointerEx` + `SetEndOfFile` takes 0.3 ms, but the first
  write at the end then takes 84 ms (Windows fills the gap with zeros). A file marked sparse
  (`FSCTL_SET_SPARSE`) avoids that: a 4 GB sparse file with two written blocks allocated 128 KB.
- Writing at offsets from 8 threads through **one** handle works with `WriteFile` + `OVERLAPPED`
  offsets (32 MB in 25 ms) and with one shared okio `FileHandle` (40 ms).
- Opening the same file a second time for writing through okio fails ("being used by another
  process"): okio does not share write access. One handle per file, or the `W` helpers
  (`winOpenFile` shares read, write and delete).
- Joining 8 parts of 4 MB with okio `appendingSink`: 66 ms.

## 3. Build speed (this PC, low priority)

| Task | Time |
|---|---|
| `compileKotlinMingwX64` of a small module, nothing changed | 6 s |
| `linkDebugSharedMingwX64` (ktor, coroutines, serialization, okio) | 42 s to 1 min 10 s |
| `linkReleaseSharedMingwX64` | 1 min 30 s to 1 min 45 s |
| each extra release variant | about 1 min 10 s |

Development: `linkDebugShared` (also gives stack traces with names). Measurements and shipping:
`linkReleaseShared`. One Gradle build at a time, `--priority low`. A release DLL of this size is
6.3 MB, a debug one 14.5 MB.
