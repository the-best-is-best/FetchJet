# Native port: release build and packaging - hand-over note

Stopped 2026-11-10 01:56 (owner's stop at 02:00). Work started 01:38, so only the first part is done.
Nothing was installed, registered or written to the registry. The app EXE was never started.
No process of mine is running (my Gradle run reused a daemon that was already there; the local
test servers were already running and I only asked for their status: both left as they were).

## 1. Files created or changed

| File | State |
|---|---|
| `desktopNative/res/version.h.in` | NEW. Template for `fj_version.h` (`FJ_VERSION_MAJOR/MINOR/PATCH`, `FJ_VERSION_TEXT`, `FJ_VERSION_COPYRIGHT`). |
| `desktopNative/res/app.rc` | CHANGED. Was `1 ICON "fetchjet.ico"`; now also includes `<winver.h>` + `fj_version.h` and has the `VERSIONINFO` block (CompanyName `Michelle Raouf`, FileDescription `FetchJet High-Velocity Multi-Engine Downloader`, FileVersion / ProductVersion = version text, InternalName / OriginalFilename `FetchJet.exe`, ProductName `FetchJet`, LegalCopyright `Copyright (C) <build year>`, translation 0x409 / 1200). |
| `desktopNative/CMakeLists.txt` | CHANGED, version-resource lines only: a block after `find_package(Slint ...)` reads `APP_VERSION` from `sharedLogic/.../core/update/AppVersion.kt` (same regex as `desktopApp/build.gradle.kts`, cut at `-`, padded to 3 numbers, re-configures when the file changes), writes `<build dir>/generated/fj_version.h` with `configure_file`; plus one new line `target_include_directories(fetchjet_native PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/generated")`. |
| `desktopNative/packaging/build-release.cmd` | NEW, **written but never run**. Configures `desktopNative` into `desktopNative/build-release` with `-DFJ_CORE_KIND=releaseShared` and builds Release at low priority (`cmd /c start /low /b /wait`, MSBuild `/nodeReuse:false /lowPriority:true`); log `build-release\build.log`; drops a `.gitignore` with `*` into `build-release` so the tree does not show in `git status` (I may not edit `desktopNative/.gitignore`). |

The three version-resource edits are complete and consistent with each other (no half-applied
change). **They were not compiled by a real host build yet**, and `CMakeLists.txt` is shared: the
next debug build of another session in `desktopNative/build` will re-configure and compile
`app.rc` with them. If that build fails on `fj_version.h` / `app.rc`, the quick way back is:
`app.rc` = the single line `1 ICON "fetchjet.ico"`, and remove the block between `find_package`
and `add_executable` plus the second `target_include_directories` line.

Scratch (outside the repository):
- `E:\tools\probes\package\logs\core-release-1.log` (release link log with a time on every line)
- `E:\tools\probes\package\jvm-msi-ref\jvm.wxs` (tables of the JVM `FetchJet-1.0.9.msi`, decompiled with `dark.exe`, no binaries; reference for the MSI)
- scratchpad `...\scratchpad\vertest\` (`ver.cmake`, `app.rc`, `rctest.cmd`, `app.res`): can be deleted

## 2. What was built

- Release core: `desktopNative/core/build/bin/mingwX64/releaseShared/fjcore.dll`,
  **20,308,480 bytes** (debug: 40,312,320). **Complete**: `BUILD SUCCESSFUL in 3m 14s` at 01:44:40,
  the link task itself about 3 min 07 s (01:41:31 -> 01:44:38), the compile tasks were up to date.
  Command: `bash desktopNative/tools/one-build.sh ./gradlew.bat :desktopNativeCore:linkReleaseSharedMingwX64 -Pfetchjet.nativeDesktop=true --priority low`.
  It is the working tree of 01:41; other sessions changed sources after that, so link it again
  before packaging.
- Release host EXE: **not built** (`desktopNative/build-release` does not exist).
- Stage folder, MSI, MSIX: **not built**.

## 3. RAM numbers

**None measured.** The core tests against the release DLL and the hidden-desktop run were not
reached. Only known number (from STATUS.md, debug core): 32.9 MB private on the settings page.

## 4. Checks that ran

| Check | Result |
|---|---|
| Version parsing logic, `cmake -P` with 6 sample lines | ok: `1.4.0` -> 1.4.0, `2.10.3-beta1` -> 2.10.3, `1.4` -> 1.4.0, `7` -> 7.0.0, no `APP_VERSION` -> error path, `1.x.0` -> rejected |
| The new `app.rc` text with a hand-made `fj_version.h`, `rc.exe /I gen` (through `one-build.sh`) | exit 0, `.res` 47,136 bytes |
| Real CMake configure + host build with the new lines | **not run** |
| Version resource read back from a built EXE | **not run** |

## 5. Not written yet

- `desktopNative/packaging/stage.ps1` (stage folder `E:\tools\probes\package\stage`)
- `desktopNative/packaging/product.wxs` + MSI build script
- MSIX build script
- the check script (administrative extraction, table dump, MSIX unpack, tree compare)
- `licenses\` content beyond what exists on disk (see 7)
- everything in "RAM of the release build"

## 6. Next steps, in order

1. `bash desktopNative/tools/one-build.sh ./gradlew.bat :desktopNativeCore:linkReleaseSharedMingwX64 -Pfetchjet.nativeDesktop=true --priority low` (sources moved on).
2. `bash desktopNative/tools/one-build.sh cmd //c "desktopNative\packaging\build-release.cmd"` from the repository root. First real test of the script and of the version resource. Check `desktopNative\build-release\build.log` ends with `EXIT 0`, that `start /low /b /wait` passes the exit code on, and read the resource back:
   `powershell -NoProfile -Command "(Get-Item desktopNative\build-release\Release\FetchJet.exe).VersionInfo | Format-List"`.
3. `dumpbin /dependents` on `build-release\Release\FetchJet.exe` and `slint_cpp.dll`: confirm only `msvcp140.dll`, `vcruntime140.dll`, `vcruntime140_1.dll` are needed from the VC runtime (the redist folder also has `msvcp140_1/_2/_atomic_wait/_codecvt_ids`, `concrt140`, `vccorlib140`, `vcruntime140_threads`).
4. Write `stage.ps1`: EXE + `slint_cpp.dll` from `build-release\Release`, `fjcore.dll` from `core\build\bin\mingwX64\releaseShared` (the copy next to the EXE is only as new as the last host build), the three VC DLLs from `%VCToolsRedistDir%x64\Microsoft.VC*.CRT`, `extension\chromium\` with exactly the `bundleExtension` list (`background.js yt-core.js content.js popup.html popup.js options.html options.js icon16.png icon48.png icon128.png manifest.json platform.js i18n/** _locales/**`, and fail when `manifest.json` version != `EXTENSION_VERSION`), `licenses\`, the empty file `FetchJet.installed`, and `fjtorrent.dll` when one exists under `desktopNative/torrent` (the folder did not exist at 01:40).
5. MSI, MSIX, checks, then the RAM runs (section 7 has what I worked out for each).

## 7. What the next run needs to know

Build environment
- CMake is not in `C:\Program Files\CMake` (report 10 says so): it is the one inside Visual Studio and is on PATH only after `vcvars64.bat`. `vcvars64` prints `'vswhere.exe' is not recognized`: harmless. After it: `VCToolsRedistDir=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Redist\MSVC\14.51.36231\`, `WindowsSdkVerBinPath=C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\`.
- `one-build.sh` treats a lock older than 45 minutes as dead and takes it. A build longer than that needs `touch /e/tools/probes/build.lock` now and then. The lock is not first-come: a waiting build can be overtaken.
- `servers.sh start` begins with a stop of all servers: only use `status`, and `start` only when they are down (another session's tests use them).
- `WinApp` also treats env `FETCHJET_INSTALLED=1` as "installed": make sure it is not set in a test run.
- `deskrun` prints memory **once**, at `--after`, then stops the program. Idle and downloading are two runs (one without `--script`), or sample the process from outside.
- There is no UiAction that starts a download from a URL in one step (`shots.py` uses the debug call `addDirect`). For `--script` use three lines per download: `{"type":"SetUrl","text":"http://127.0.0.1:18200/file/90MB.bin?key=rel-a&throttle=700"}`, `{"type":"ShowAddDialog","show":true}`, `{"type":"StartDownload"}` (this order is what `test_download.py` does; the host sends one line every 500 ms, only lines that start with `{`).

MSI (read from the real JVM 1.0.9 MSI, `jvm.wxs`)
- ProductCode is a name-based id per version (`{7B684BA3-530C-3EA1-8747-C36DBFA891F8}` for 1.0.9). Upgrade table: two rows, older -> `JP_UPGRADABLE_FOUND`, newer -> `JP_DOWNGRADABLE_FOUND`, neither is detect-only; `RemoveExistingProducts` at 798. `dark` prints `IncludeMinimum` only when the bit is set, so read the `Attributes` column directly before trusting "older or equal".
- Plan for the native MSI: `Product Id="*"`, `Package InstallScope="perUser"` alone (WiX refuses it together with `InstallPrivileges`; it already means limited), `MajorUpgrade AllowDowngrades="yes" Schedule="afterInstallValidate"` (one Upgrade row, minimum 0 inclusive: any other installed version with this UpgradeCode is removed first).
- `.torrent` ProgId name in the JVM MSI is `progidb6b76d118e2c3f3e9f64d373ac4670d0` (jpackage hash of extension + launcher name, so most likely the same in every JVM version; only 1.0.9 could be read). Use this exact name so a user's "open .torrent with FetchJet" choice survives. JVM values: `.torrent` default = the ProgId, `Content Type` = `application/x-bittorrent`; ProgId default = `BitTorrent file`; `shell\open` = `Open with [ProductName]`; command = `"<exe>" "%1" %*` (report 10 section 8.3 proposes `"%1"` only); `MIME\Database\Content Type\application/x-bittorrent` `Extension` = `.torrent`. All with root HKCR.
- jpackage gives every file its own component whose key path is the same value `HKCU\Software\Michelle Raouf\FetchJet\<version>` `ProductCode` = `[ProductCode]`, plus `RemoveFolder` rows; the shortcut components carry conditions `JP_INSTALL_DESKTOP_SHORTCUT` / `JP_INSTALL_STARTMENU_SHORTCUT` (both default 1). Shortcuts: Desktop `FetchJet`, Start menu `FetchJet\FetchJet`, working dir `INSTALLDIR`, no icon attribute.
- jpackage also has `util:RemoveFolderEx` on `INSTALLDIR` (deletes the whole folder on uninstall). I would **not** copy it: it is only safe with jpackage's own "folder is not empty" dialog, and the native app writes nothing into its install folder.
- `heat.exe` cannot write registry key paths or `RemoveFolder` rows for folders that hold only folders (`extension\`, `_locales\`). Either an XSLT after heat or generate `files.wxs` from the stage folder in the script (what jpackage itself does). I was going to generate it, with ids and GUIDs derived from the relative path.
- To skip the licence page of `WixUI_InstallDir` (the JVM MSI has none): publish `WelcomeDlg/Next -> InstallDirDlg` and `InstallDirDlg/Back -> WelcomeDlg` with `Order="2"`.
- Use `Target="[INSTALLDIR]FetchJet.exe"` for shortcuts and the verb (no ICE69, no tie to generated file ids). Expect ICE91 warnings (per-user folder): `-sice:ICE91` as jpackage does. Keep ICE validation on: it is the main check available, since nothing may be installed here.
- Not decided, owner's call: `fetchjet://` inside the MSI (section 8.3 item 5), and remembering a chosen `INSTALLDIR` for `/passive` updates. A JVM install in a custom folder cannot be found by a fixed registry key (its values sit under the per-version key).

Seen on this PC (read-only `reg query`, nothing changed)
- `HKCU\Software\Michelle Raouf\FetchJet` does not exist and there is no `progidb6b7...` key, although `C:\Users\miche\AppData\Local\FetchJet\FetchJet.exe` is named in `HKCU\Software\Classes\fetchjet\DefaultIcon`. So I could not confirm the ProgId name on a newer JVM version here.
- `HKCU\Software\Classes\.torrent` default is `EzooDownload.torrent`.
- `HKCU\Software\Classes\fetchjet\shell\open\command` is `""E:\tbib\Kortex Downloader\script_run\fetchjet_launcher.bat"" ""%1""` (an old entry with doubled quotes, not the installed EXE). The app's `registerUrlScheme` would overwrite it on the first start of an installed build: one more reason never to start a staged EXE with the marker file here.

Licences on disk for the stage folder: repository `LICENSE` (GPL-3.0), `E:\tools\slint\licenses\LICENSE.md` and `THIRDPARTY.md`. A notice for the Kotlin libraries inside `fjcore.dll` (stdlib, coroutines, serialization, ktor, okio: Apache-2.0) and for the Microsoft VC runtime is not written; the exact library list should come from the Gradle dependency report, not from memory.

## 8. Not verified (must stay on the list)

- An actual install, upgrade (JVM MSI -> native MSI) or uninstall: not allowed on this PC.
- Everything in sections 5 and 6: not built, so nothing about MSI / MSIX content is checked yet.
- `app.manifest` still has the fixed `version="1.4.0.0"` (not tied to `APP_VERSION`; changing that means touching the `add_executable` line, outside my scope).
