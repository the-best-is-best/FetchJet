# Native desktop port: Linux

Started 2026-11-10. The Linux app is the Windows native app (Kotlin/Native core + Slint UI + a
small C++ host) built from the same files. Only the OS layer is separate. Results and open work
are at the end; the dated logs are in `STATUS.md` ("Linux port, first round", "Linux port,
second round", "Linux port, third round", "Linux port, fourth round").

## 1. Layout

### Kotlin (`sharedLogic`, only with `-Pfetchjet.nativeDesktop=true`)

```
commonMain
 └ nativeMain                      (default hierarchy; iOS also hangs here)
    └ nativeDesktopMain            everything desktop-native that is not OS-specific
       ├ mingwX64Main              Windows layer only (platform/win, WinHttp, DPAPI, registry, torrent)
       └ linuxX64Main              Linux layer only (platform/linux, platform/desktop/DesktopOs.linux.kt)
```

- `nativeDesktopMain/.../platform/desktop/DesktopOs.kt`: `expect object DesktopOs` (environment,
  thread id, memory, local time, clipboard, open / reveal, user folders, files, crypto, secret
  protection, the HTTP engine, **child processes** (`startProcess` -> `OsChildProcess`),
  `deleteRecursively`, `makeExecutable`) and `expect object DesktopApp` (app home, install kind,
  automatic downloads, login start, `exeDir`, `launcherPath`).
- Shared helpers next to it:
  - `DesktopProcess.kt`: `OsChildProcess`, `OsLineSplitter`, `DesktopOs.runProcess(...)` (pipes,
    stdin, timeout, kill on cancel, exit code), `startDetached`, `runForOutput`, `findOnPath`.
  - `DesktopZip.kt`: zip reader / unzip over okio + `platform.zlib` (keeps Unix exec bits).
- Windows actuals (`DesktopOs.mingw.kt`) are one line each over `platform/win`
  (`startProcess` wraps `WinProcess`). The old Windows copies `winRunProcess`, `WinLineSplitter`,
  `WinProcessResult`, `winStartDetached` and `WinZip.kt` are deleted (round 3); the app
  (`SystemPower.mingw.kt`, `PlatformUpdater.mingw.kt`) and the `winhelpers` tests use the shared
  `DesktopOs.runProcess`, `OsLineSplitter`, `DesktopZip`.
- `DesktopFileSystem.kt` (round 4): an okio `FileSystem` over `DesktopOs` (`fileInfo`, `listEntries`,
  `openFile(path, write, OsOpen)`, `move`, `deleteFile`, `createDirectories`); `OsFile` has
  `resize` now. Every okio file call of the shared code goes through
  `core/filesystem/AppFileSystem` (`expect val`): `DesktopFileSystem` in `nativeDesktopMain` (both
  OSes), `FileSystem.SYSTEM` on JVM / Android / iOS (unchanged). Why: okio's own Windows file system
  fails on paths longer than 260 characters (see round 4 in `STATUS.md`).
- `winhelpers` (Windows test build) compiles, besides `platform/win`: the shared
  `nativeDesktopMain/.../platform/desktop` (expect side, in its `commonMain`, + okio), the Windows
  `platform/desktop/DesktopOs.mingw.kt`, `nativeDesktopMain/.../platform/torrent` and the device
  sync folders. One line per folder in `tests/winhelpers/build.gradle.kts`.
- Torrents are shared (round 3): `nativeDesktopMain/.../platform/torrent/FjtLibrary.kt` (the
  `fjt_*` function table, `.torrent` / magnet / status calls, the loader with ABI check) and
  `FjtEngine.kt` (session life cycle, idle close, magnet polling, priorities, proxy lines) work on
  an `FjtHost` interface (library name, candidates, open + symbol lookup, lock, clock, sleep, env,
  folders). Per OS only the host: `platform/win/WinTorrentLibrary.kt` (`WinTorrentHost`,
  `LoadLibraryExW` + `GetProcAddress`, SRW lock, the folder of `fjcore.dll` found with
  `GetModuleHandleExW`) and `linuxX64Main/.../platform/linux/LinuxTorrentHost.kt` (`dlopen` /
  `dlsym`, pthread lock, the folder of `libfjcore.so` read from `/proc/self/maps`, then the EXE
  folder). The backend (`TorrentBackend.nativeDesktop.kt`) is shared too; each OS has a one-line
  `nativeTorrentLibrary()` actual. `FETCHJET_TORRENT_DLL` overrides the path on both OSes.
- Linux layer (`linuxX64Main/.../platform/linux/`):
  - `LinuxSystem.kt` env, cwd, `/proc/self/exe`, `localtime_r`.
  - `LinuxProcess.kt` `posix_spawnp` (found with `dlsym`) + pipes, own process group
    (`kill(-pgid)` = kill tree), signal mask / SIGPIPE reset, `addchdir_np` or a `/bin/sh cd`
    fallback, `waitpid` reaping.
  - `LinuxCurl.kt` + `LinuxCurlEngine.kt`: the HTTP engine, a ktor engine over **libcurl loaded
    with `dlopen`** (`libcurl.so.4`, no build-time dependency). HTTPS, HTTP proxy with login
    (CONNECT, `CURLOPT_PROXYUSERNAME`), SOCKS5 with login (`socks5h://`), redirects left to ktor,
    ranges, timeouts, cancel = the write / progress callback aborts and curl closes the
    connection. `FETCHJET_CA_BUNDLE` adds a CA file (used by the tests). ktor CIO was dropped: on
    Kotlin/Native its TLS is a stub ("TLS sessions are not supported on Native platform").
  - `LinuxCrypto.kt` AES-GCM, ECDSA P-256 verify, **ECDH P-256** (device sync) through
    `libcrypto.so.3` with `dlopen`.
  - `LinuxLan.kt` IPv4 interfaces / broadcast addresses (`getifaddrs`), host name, a recursive
    pthread lock.
  - `LinuxDesktopTools.kt` clipboard through `wl-paste` / `wl-copy`, `xclip` or `xsel` when one
    is installed; "show in folder" through `org.freedesktop.FileManager1.ShowItems` (gdbus), else
    `xdg-open` on the folder.
- Media (yt-dlp / ffmpeg / deno) is shared: `nativeDesktopMain/.../feature/media_extractor/`
  (`MediaToolPaths`, `MediaInspect`, `MediaYtDlpDownload`, `MediaStreamExtractorPlatform`,
  `MediaExtractorToolManager`, all `.nativeDesktop.kt`). Assets per OS like the JVM app:
  `yt-dlp_win.zip` / `yt-dlp_linux.zip` (unpacked build, program `yt-dlp.exe` / `yt-dlp_linux`),
  ffbinaries 4.4.1 `win-64` / `linux-64` (pinned SHA-256), `deno-x86_64-pc-windows-msvc.zip` /
  `deno-x86_64-unknown-linux-gnu.zip`. The yt-dlp speed-limit proxy is shared too (round 3, see
  "Loopback proxy" below), so Linux now gives yt-dlp `--proxy` with live limit changes instead of
  `--limit-rate`. Zip files are read and written through `DesktopOs.openFile` (`OsFile`: Windows
  `CreateFileW` / `ReadFile` / `WriteFile` at an offset, Linux okio): okio's own Windows file handle
  failed every read ("The operation completed successfully"), so the media tools could not have
  been unpacked on Windows (found by the retargeted `ZipTests`, round 3).
- Loopback proxy (round 3): `nativeDesktopMain/.../platform/proxy/LoopbackProxy.kt` (HTTP proxy
  with CONNECT, plain http forwarding, SOCKS5 client with login, token-bucket speed limit,
  `SocksForwarder`) over a small socket layer `LoopSockets` (`expect fun loopSockets()`):
  `platform/win/WinLoopSockets.kt` (Winsock, `CreateThread`) and
  `platform/linux/LinuxLoopSockets.kt` (BSD sockets, `MSG_NOSIGNAL`, `FD_CLOEXEC`, detached
  pthreads). `WinLoopbackProxy.kt` / `WinSocksForwarder.kt` are gone. Windows uses the forwarder
  for SOCKS5 in front of WinHTTP; Linux does not need it (curl speaks SOCKS5).
- Browsers: `DesktopBrowsers.nativeDesktop.kt` (shared detection / unpacked extension / .xpi),
  Windows paths under Program Files, Linux commands on `PATH` (same list as the JVM app).
- Device sync: protocol in commonMain; `nativeDesktopMain/.../feature/device_sync/KtorSyncNetwork.kt`
  (ktor-network UDP / TCP, shared by Windows and Linux, also compiled by `winhelpers`) +
  `nativeDeviceSync(...)`; per OS only crypto / files / lock / interfaces
  (`WinDeviceSyncPlatform.kt`, `linuxX64Main/.../LinuxDeviceSyncPlatform.kt`).
- Linux actuals that are real now: `SystemPower` (`systemctl suspend|poweroff`, then `loginctl`),
  `StartupLauncher` (`$XDG_CONFIG_HOME/autostart/fetchjet.desktop`, `Exec="<exe>" --autostart=true`,
  only for a packaged build), `PlatformUpdater` (no in-app install; offers the `.deb` /
  `linux…tar.gz` asset), `BrowserIntegration` (shared browser list), `createDeviceSync`.

### Core (`desktopNative/core`)

Everything is in `core/src/nativeDesktopMain` (round 3 moved `DevicePresenter.kt`, `DeviceDto.kt`
and `generated/TrTable.kt` there; `core/src/mingwX64Main` is gone). `tools/gen/gen.py` reads and
writes only that folder; after the move it reported "nothing changed".

### C++ host (`desktopNative/host`)

```
host/app.cpp              shared: also registers every font in <exe>/fonts and answers the emoji
                          icons from <exe>/emoji/<code>-<size>.png (host/emoji_files.h)
host/app_host.h           the contract (fjhost::os::*)
host/win/platform_win.cpp Windows
host/linux/platform_linux.cpp  dlopen libfjcore.so, flock single instance + bridge hand-over,
                          dark mode (FETCHJET_THEME, portal color-scheme via gdbus, gsettings,
                          GTK_THEME), notices (notify-send), file / folder dialogs (xdg-desktop-
                          portal through gdbus, else zenity, else kdialog); emoji_fallback = none
                          (the bundled pictures cover every emoji the UI uses)
host/linux/tray_linux.cpp tray (round 4): StatusNotifierItem + com.canonical.dbusmenu over GDBus,
                          libgio-2.0.so.0 loaded with dlopen (no build-time dependency), own
                          thread + GMainContext; registers with org.kde.StatusNotifierWatcher.
                          No watcher (or no libgio, or FETCHJET_NO_TRAY=1) = no tray, close
                          quits as before; the watcher going away shows the window again
```

### Packaging (`desktopNative/packaging/linux/build-linux.sh`, run inside Linux)

Stages `FetchJet`, `libslint_cpp.so`, `libfjcore.so` (release by default), optional
`libfjtorrent.so`, `fonts/` (4 files), `emoji/` (160 pictures), `extension/chromium`, licenses,
icon, `FetchJet.installed`, a `.desktop` file,
then writes `FetchJet-<ver>-linux-x64.tar.gz` (portable folder) and `fetchjet_<ver>_amd64.deb`
(`/opt/fetchjet`, `/usr/bin/fetchjet`, desktop entry, hicolor icon; `Depends` libcurl4, libssl3,
libinput10, libxkbcommon0, fontconfig, freetype, libgbm1, libudev1; `Recommends` notify-send,
zenity / kdialog, wl-clipboard / xclip). Inputs through `FJ_HOST_BUILD`, `FJ_CORE_KIND`,
`FJ_CORE`, `FJ_TORRENT_SO`, `FJ_PACKAGE_OUT` (default `~/fetchjet-native/package`).

### Fonts and icons (both OSes, round 3)

One font ships with the app and is used on Windows and Linux: **FetchJet Sans**
(`desktopNative/res/fonts/FetchJetSans-{Regular,Medium,SemiBold,Bold}.ttf`, 364-367 KB each,
1.46 MB together). It is made by `desktopNative/tools/fonts/make_fonts.py` (fontTools): Noto Sans
(Latin, Latin Extended, Greek, Cyrillic, punctuation) + Noto Sans Arabic (Arabic, Persian,
presentation forms) + the UI's symbol glyphs from DejaVu Sans / DejaVu Sans Mono (arrows,
triangles, check / cross, sun / moon, gear, warning, bolt, scissors, search), merged per weight,
vertical metrics taken from Noto Sans, renamed. Why merged: Slint 1.18 does not fall back from a
registered font to another registered font (tested: family "Noto Sans" drew Arabic with a
system font), so Latin, Arabic and symbols must be one family. The host registers every
`<exe>/fonts/*.ttf` at start (`register_fonts()` in `host/app.cpp`, Slint
`window_handle().register_font_from_path`); `Theme.font` (text) and the new `Theme.symbol-font`
(every former `"Segoe UI Symbol"`) are "FetchJet Sans". Chinese keeps "Microsoft YaHei UI"
(`uiFontFor`, core), because the fallback from FetchJet Sans picked a Japanese font that lacks
simplified glyphs (载, 设); other CJK / Devanagari text falls back to the system.

Three glyphs no free font here has are drawn as paths by the shared `Glyph` widget
(`ui/widgets/Icons.slint`): pause (two bars), hourglass, and the "two windows" browser icon.
`Glyph` replaces the text glyphs in `Buttons.slint`, the row quick action and group header
(`TaskRow.slint`) and the settings page icons (`Settings.slint`).

Colour emoji: `desktopNative/res/emoji/<hex>-<20|32|48|64>.png` (40 emoji, 160 files, ~320 KB),
cut from Noto Color Emoji by `desktopNative/tools/emoji/extract_emoji.py` (pure Python: reads
cmap / CBLC / CBDT, scales with an area filter, writes PNG). It scans `ui/` and `core/src` for the
emoji the UI uses. `host/app.cpp` answers `Emoji.icon` from these files on both OSes (smallest
size >= the asked one); Windows keeps its DirectWrite atlas (`os::emoji_fallback`) only for an
emoji that has no file (none today). Licences: `packaging/licenses/THIRD-PARTY.txt`
("Fonts and icons", OFL 1.1 and Bitstream Vera texts).

Picture tool: `shot.exe --font-dir <dir> --emoji-dir <dir>` (round 3) does the same;
`tests/corehost/shots.py` passes both.

## 2. Commands

Windows (repository root, through the shared lock):

```
bash desktopNative/tools/one-build.sh ./gradlew.bat :sharedLogic:compileKotlinLinuxX64 :sharedLogic:compileKotlinMingwX64 -Pfetchjet.nativeDesktop=true --offline -q --continue
bash desktopNative/tools/one-build.sh ./gradlew.bat :desktopNativeCore:linkDebugSharedLinuxX64 -Pfetchjet.nativeDesktop=true --offline -q
bash desktopNative/tools/one-build.sh ./gradlew.bat :desktopNativeCore:linkReleaseSharedLinuxX64 -Pfetchjet.nativeDesktop=true --offline -q
bash desktopNative/tools/one-build.sh ./gradlew.bat -p desktopNative/tests/winhelpers compileKotlinMingwX64 --offline -q
```

The Linux core is cross-compiled on Windows (debug 45 MB, release 23.4 MB).

WSL (Ubuntu 26.04, only in `~/fetchjet-native`; Windows paths through `MSYS_NO_PATHCONV=1 wsl.exe -d Ubuntu -- bash /mnt/e/...`):

```
# host (as round 1)
~/fetchjet-native/cmake/bin/cmake -S /mnt/e/tbib/FetchJet/desktopNative -B ~/fetchjet-native/host-build -G "Unix Makefiles" \
    -DCMAKE_BUILD_TYPE=Release -DSLINT_DIR=$HOME/fetchjet-native/slint -DFJ_CORE_KIND=debugShared \
    -DCMAKE_EXE_LINKER_FLAGS="-Wl,-rpath-link,$HOME/fetchjet-native/sysroot/usr/lib/x86_64-linux-gnu"
nice -n 10 ~/fetchjet-native/cmake/bin/cmake --build ~/fetchjet-native/host-build -j8
# fake yt-dlp for the media tests
bash /mnt/e/tbib/FetchJet/desktopNative/tests/fakes/build.sh          # -> tests/fakes/build/yt-dlp_linux
# torrent library (Boost headers only, from libboost1.83-dev unpacked into ~/fetchjet-native/boost-root)
~/fetchjet-native/cmake/bin/cmake -S /mnt/e/tbib/FetchJet/desktopNative/torrent -B ~/fetchjet-native/torrent-build -G "Unix Makefiles" \
    -DCMAKE_BUILD_TYPE=Release -DLIBTORRENT_SOURCE_DIR=/mnt/e/tools/libtorrent-2.1.2 \
    -DCMAKE_PREFIX_PATH=$HOME/fetchjet-native/boost-root/usr \
    -DBoost_DIR=$HOME/fetchjet-native/boost-root/usr/lib/x86_64-linux-gnu/cmake/Boost-1.83.0
nice -n 10 ~/fetchjet-native/cmake/bin/cmake --build ~/fetchjet-native/torrent-build -j8 --target fjtorrent
# torrent library and the local seeder for the tests (round 3)
nice -n 10 ~/fetchjet-native/cmake/bin/cmake --build ~/fetchjet-native/torrent-build -j8 --target fjtorrent fjtseed
# packages (FJ_TORRENT_SO adds libfjtorrent.so)
FJ_TORRENT_SO=~/fetchjet-native/torrent-build/libfjtorrent.so bash /mnt/e/tbib/FetchJet/desktopNative/packaging/linux/build-linux.sh
```

Fonts and emoji (only when the source fonts or the UI's emoji change):

```
PYTHONPATH=<fonttools wheel> python desktopNative/tools/fonts/make_fonts.py --noto <dir> --arabic <dir> \
    --dejavu DejaVuSans.ttf --dejavu-mono DejaVuSansMono.ttf --out desktopNative/res/fonts
python3 -I desktopNative/tools/emoji/extract_emoji.py --font NotoColorEmoji.ttf --out desktopNative/res/emoji \
    --source desktopNative/ui --source desktopNative/core/src
```

Torrent test on Linux: `FETCHJET_TORRENT_DLL=~/fetchjet-native/torrent-build/libfjtorrent.so
FJ_TORRENT_SEED=~/fetchjet-native/torrent-build/fjtseed python3 desktopNative/tests/corehost/test_torrent.py`
(the round 3 scripts: `E:\tools\probes\linuxport\r3\*.sh`).

Tests in WSL (from `/mnt/e/tbib/FetchJet`, `LANG=en_US.UTF-8`,
`LD_LIBRARY_PATH=~/fetchjet-native/sysroot/usr/lib/x86_64-linux-gnu`): test servers inside WSL
(HTTP 18200, HTTPS 18443 with a self-signed `tls-cert.pem`, proxies 18081 / 11081 with login
`fj` / `secret`, 18082 / 11082 open; work folder `~/fetchjet-native/work/servers`), then
`FETCHJET_CA_BUNDLE=<tls-cert.pem> SSL_CERT_FILE=<tls-cert.pem> python3 desktopNative/tests/corehost/test_*.py`.
`FJ_TEST_SERVER=https://127.0.0.1:18443` runs `test_download` over HTTPS. The scripts used this
round: `E:\tools\probes\linuxport\r2\servers-wsl.sh`, `run-test.sh`, `shots\shot.sh`.

New tests: `test_network.py` (proxies and HTTPS, both OSes; HTTPS parts Linux only because the
self-signed certificate is not trusted by WinHTTP), `test_sync_loopback.py` (Linux only: two
cores in two processes on 127.0.0.2 / 127.0.0.3, ports 28491 / 28492, real pairing and a link
send).

Tray and portal test (round 4, WSL, private session bus, nothing installed):
`dbus-run-session -- python3 -I desktopNative/tests/host/test_tray_linux.py` (script
`E:\tools\probes\r4\tray.sh`). The test owns a fake `org.kde.StatusNotifierWatcher` and a fake
`org.freedesktop.portal.Desktop` (FileChooser) through Python Gio, starts the Linux host with
`--action SettingClick chooseFolder`, then checks the registration, the item properties, the
dbusmenu layout (Show, When done radio items, separator, Close), the portal folder (an Arabic
name) landing in `settings.properties`, Activate hiding / showing the window (X11 map state)
and Close quitting with code 0.

Screenshots: `xshot.py` (ctypes `XGetImage`, X11 through WSLg, `unset WAYLAND_DISPLAY`);
`FETCHJET_THEME=dark|light` forces the host's system theme.

## 3. Results

Round 2: see `STATUS.md` "Linux port, second round". Round 3 (2026-11-10, night): `STATUS.md`
"Linux port, third round". In short: every Linux corehost suite passes (download over HTTP and
HTTPS, home, add, media, settings, links, launch, network, devices, bridge, sync loopback,
**torrent 17/0** with the Linux seeder, **proxy 13/0** with the shared loopback proxy); the
Windows suites pass on the release and the debug core; screenshots on both OSes use the bundled
font and emoji pictures.

Round 4 (2026-11-10, late night, `STATUS.md` "Linux port, fourth round"): okio file calls go
through `DesktopFileSystem` (Arabic and long paths on Windows), the tray and the portal folder
dialog run against a private session bus; every Linux suite passes again on the debug core, plus
`test_paths` 17/0 and `test_tray_linux` 20/0.

## 4. Downloads made for this work

| What | Size |
|---|---|
| Round 1: linuxX64 klibs, Slint 1.18.1 Linux, CMake 3.31.6, libinput10 and friends (`.deb`, unpacked) | see STATUS round 1 (about 81 MB) |
| Round 2: `apt-get download libboost1.83-dev` (headers only, unpacked into `~/fetchjet-native/boost-root`) | 10,731,924 B |
| Round 2: `apt-get download fonts-noto-color-emoji` (unpacked into `~/fetchjet-native/fonts-root`, emoji check) | 9,967,220 B |
| Round 3: Noto Sans Regular / Medium / SemiBold / Bold (unhinted TTF, notofonts.github.io) | 431,364 + 431,176 + 431,500 + 432,376 B |
| Round 3: Noto Sans Arabic Regular / Medium / SemiBold / Bold (unhinted TTF) | 142,140 + 142,328 + 141,856 + 141,852 B |
| Round 3: `OFL.txt` of Noto Sans | 4,396 B |
| Round 3: `fonttools-4.68.0-py3-none-any.whl` (pip download, used from the wheel, nothing installed) | 1,248,357 B |

No other downloads: libcurl, libssl / libcrypto, libdbus, gdbus, notify-send were already in WSL.
DejaVu Sans / Sans Mono (the symbol glyphs) were copied from WSL's `fonts-dejavu-core`.

## 5. Left to do, in order

Done in round 3: torrents in the app (shared loader / engine, `libfjtorrent.so`, Linux seeder,
`test_torrent` 17/0), the Windows copies (`winRunProcess`, `WinLineSplitter`, `WinZip`) deleted
and their tests retargeted, the core's last `mingwX64Main` files moved, fonts / symbols / colour
emoji bundled for both OSes, the shared loopback proxy (speed limit and SOCKS5) on Linux, portal
file dialogs (written), the `linux-native` CI job (written).

1. Tray: done in round 4 (`tray_linux.cpp`, GDBus through `dlopen`), tested in WSL against a fake
   watcher on a private session bus (`test_tray_linux.py` 20/0). Not seen on a real panel yet
   (KDE Plasma, GNOME with the AppIndicator extension): icon look, menu placement.
2. Portal dialogs: the folder dialog ran in round 4 against a fake portal (same test); a real
   xdg-desktop-portal (GNOME, KDE) is still to be tried.
3. okio file calls: done in round 4. All shared code now goes through `AppFileSystem` /
   `DesktopFileSystem` (see section 1); `winhelpers paths` proves okio's own Windows file system
   fails on long paths and `DesktopFileSystem` passes; `test_paths.py` (corehost, both OSes) runs a
   download with pause / resume into an Arabic folder longer than 260 characters.
4. Secrets in the Secret Service keyring; in-app update for the `.deb` / portable folder.
5. A real Linux desktop run: notifications, clipboard tools, dark-mode change, file dialogs,
   install of the `.deb` (needs sudo, not allowed in WSL here).
6. The `linux-native` CI job was never started (Slint SDK URL defaults to the 1.18.1 release
   tarball; set `FETCHJET_SLINT_LINUX_SDK_URL` to override). It does not build `libfjtorrent.so`
   yet (needs libtorrent 2.1.2 sources + Boost headers on the runner), like `windows-native`.
