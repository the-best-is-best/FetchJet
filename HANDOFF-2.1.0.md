# Handoff for the Mac session: 2.0.0 -> 2.1.0 (iOS / macOS)

Read `CLAUDE.md` first (owner rules: Egyptian Arabic with masculine forms, short; never commit / push / reset / change branches without his word; no code comments; no speculative edits in the tree; `FETCHJET_NO_USAGE_PING=1` for every run; never run on his real `~/.fetch_jet`). Branch `release-v2.1.0`. Nothing is committed yet: the owner commits and pushes, then pull on the Mac.

Version: `APP_VERSION = 2.1.0`, `MARKETING_VERSION = 2.1.0` (build number stays 1), `EXTENSION_VERSION = 3.8.0` (the three manifests match).

## What is new in 2.1.0

1. **Hidden downloads with a PIN** (all platforms). A PIN of 4 to 6 digits protects a Hidden screen (More > Hidden). Hiding a download, a playlist group or a folder group moves its files into an app-private folder (`<settings>/hidden_files/<taskId>/`, with `.nomedia`) so they leave the gallery and the file manager. Audio and video are also ENCRYPTED (ChaCha20 + HMAC, key tied to the device through Keychain / `Secrets`) and stored as `<32hex>.fjh`; they are decrypted to a temp copy only when opened and the copy is wiped on lock. Other file types are only moved. A first-time notice explains the encryption. Hiding is instant (the item vanishes at once, files move in the background) with ONE progress line for the whole operation; unhide returns the files to their original folder and the items reappear in their group at once. Downloads started from the Hidden screen start hidden. There is no "download as hidden" switch in the Add dialog any more.
2. **One progress line** ("Moving files… 2 of 5") above the list for hide and unhide (Android, Slint, SwiftUI).
3. **Extension 3.8.0**: option "open FetchJet directly" (`openDirectly`), and an arrow button next to the page button plus an "Open in FetchJet" row that open the Add dialog with the page link (`fetchjet://download?url=`) when qualities cannot be listed (for example Udemy captured streams).
4. Row menu: one shared `RowMenu.entries` (hidden tasks only get Open, Pause, Resume, Select, Details, Unhide, Delete).

All logic lives once in `sharedLogic` (`feature/hidden/*`, `feature/queue_manager/QueueHidden.kt`, `presentation/dashboard/DashboardHidden*.kt`, `HiddenTexts.kt`); the UIs only call events and show state. Shared API the SwiftUI code uses: `DashboardUiEventOnHiddenAddDownload.shared`, `DashboardUiEventOnHideTargets(taskIds:playlistIds:folders:lockAfter:)` (all four arguments, Kotlin defaults are not exported), `OnUnhideTasks`, `OnHiddenEncryptNoticeAccepted/Dismissed`, `OnHiddenNoticeShown`, `HiddenTextsKt.hiddenWorkingText(done:total:)`, `hiddenLockedText(seconds:)`, `hiddenWrongPinText(triesLeft:)`, `hiddenOpeningText(fraction:)`, `hiddenNoticeText(notice:)`, `HiddenNotice` (COPY_LEFT, MOVE_FAILED, OPEN_FAILED, HIDDEN_DONE), `RowMenuEntry.UNHIDE`.

## Nothing of the iOS side has been compiled

This was written on Windows. Build first and fix compile errors before testing anything:

```
git submodule update --init --recursive
scripts/ios-dev-build.command all
```

Things most likely to need a fix (names guessed from Kotlin/Native rules):
- `HiddenTextsKt.*` argument labels (they follow the Kotlin parameter names).
- `DashboardUiEventOnHideTargets(taskIds:playlistIds:folders:lockAfter:)`.
- `DashboardHiddenGroupsKt.hiddenItems(...)`, `hiddenOpenPlaylist`, `hiddenOpenFolder` (extension functions on `DashboardUiState`).
- Types `Int32` / `KotlinBoolean` for `hidden.working`, `workDone`, `workTotal`, `workFraction`.
- `StoreLock` actuals for iOS (`StoreLock.ios.kt` wraps `IosSyncLock` / `NSRecursiveLock`), plus the Linux / macOS / mingw ones: run `:sharedLogic:compileKotlinIosSimulatorArm64` and `:desktopNativeCore:linkReleaseSharedMacosArm64 -Pfetchjet.nativeDesktop=true`.

## Swift files changed (all need the Mac)

- New folder `iosApp/iosApp/SwiftUI/Hidden/` (`HiddenKit.swift`, `HiddenScreen.swift`).
- `Core/FJRoot.swift` (`HiddenRootGuard`, `FJAddButton` takes an optional action), `Core/FJStore.swift`.
- `Tasks/TaskListScreen.swift` (row menu, `TaskRowActions`, `TaskHiddenWorkBanner`, Hide entries in the playlist and folder page bars), `Tasks/TaskDetailsScreen.swift`.
- `Settings/MoreScreen.swift` (Hidden entry, PIN sheet), `Dialogs/AppDialogsHost.swift` (encryption notice), `Add/AddSections.swift` and `Add/AddDownloadSheet.swift` (the hidden switch was removed).

## What to test on the iPhone / iPad simulators (local test server only, never the network)

Use `desktopNative/tools/testserver/server.py --port 18200` (files like `http://127.0.0.1:18200/file/1MB.mp4`, `.mp3`, `.zip`).

1. More > Hidden: set a PIN, wrong PIN counter, 30 s lock after 5 wrong tries, Lock button, backgrounding the app covers and locks (`HiddenRootGuard`: lock on `.background`, cover on `.inactive`).
2. Hide a zip: it leaves the list at once; the file is moved into `hidden_files/<task>/` with its own name. Hide a video or audio: the first-time notice appears; after "I understand" the file becomes `<hex>.fjh`. The set-PIN flow with the notice: after the PIN is set the hide waits for the notice, then locks (`lockAfter`).
3. Open the Hidden screen: the folder / playlist groups look like the normal list; open a group; row menu (long press) shows Open, Select, Details, Unhide, Delete only; open an encrypted video (Quick Look). `watchHiddenPreview` polls for the `QLPreviewController` (about 20 s give-up before it was seen): check that the decrypted copy is not deleted while the preview is on screen and is wiped after Lock.
4. The "+" button on the Hidden screen (`FJAddButton(action:)`, bottom right, same as home) opens the Add sheet; the download starts hidden and lands encrypted in `hidden_files`; the "Hidden." toast shows once after it started (not when the add is blocked).
5. Unhide a group: the items return in their group at once (no `task_…` rows), files return to the origin folder, the encrypted file is decrypted back.
6. The progress line ("Moving files… n of m") shows above the list while hiding / unhiding several items; no download notification or Live Activity appears for a hidden download.
7. Unhide a task deleted meanwhile and a download finishing while unhiding: no file is lost.
8. Arabic (RTL) and one other language for the new strings; iPad layout of the Hidden screen.
9. Safari extension 3.8.0: the page button arrow is NOT drawn on iOS (the iOS page button already hands over to the app); the "Open in FetchJet" row still works; `scripts/ios-dev-build.command` note about re-embedding `FetchJetSafariExtension.appex` still applies.

## Verified on other platforms (so do not redo it)

- Windows: `:sharedLogic:jvmTest` green, `assembleDebug`, native core + host, corehost `test_hidden` 120, `test_home` 41, `test_add` 33, `test_rowmenu` 19, `test_settings` 101, 0 failed.
- Android phone (A56, 2026-11-23): PIN, hide a zip (moved), hide a video (notice, then encrypted `.fjh`), "+" in Hidden (encrypted download, no public copy), row menu entries, opening an encrypted video (decrypted temp copy, wiped on Lock), unhide a group (files back, private folder empty), no download notification for hidden tasks.
- Extension: node simulations `direct.sim.js` 11/11, `arrow.sim.js`, `arrow.bg.js`, `content.sim.js`, `capture.sim.js`, `python scripts/extension-i18n/check.py` ok. NOT run in a real Chrome / Edge / Firefox / Safari.

## Known gaps

- PIN logic (set / confirm / lock countdown) is written once per platform UI (Compose, Slint, SwiftUI); the rules are in `HiddenStore`, only the rendering repeats.
- The hidden encryption key is device-bound: if the app data is cleared, hidden audio and video cannot be recovered (the notice says so).
- Torrent hide (re-add through libtorrent) and Android scoped-storage copy + delete fallback are untested.
- Linux and macOS native builds of 2.1.0 were not run.

## Added after the first version of this file (same branch)

- **Recovery code** (one code for the whole vault, shown once after the first encryption notice, also from the Hidden menu) and **restore with code**; **Forgot PIN** through the device lock (`DeviceAuth.handler`: iOS `LAContext(.deviceOwnerAuthentication)`, macOS `LAContext` in `platform_mac.mm`, Windows Hello in `host/win/auth_win.cpp`; Linux has no handler so the link is hidden); **Back up downloads / Restore from backup** (`.fjbackup`, plain JSON, no secrets, URLs stripped of `user:pass@`, merge on restore, unfinished tasks come back paused).
- **Swift files also changed:** `Hidden/HiddenKit.swift` (`FJDeviceAuth`, recovery and restore sheets, reset cancel, clipboard `.localOnly` with a 60 s expiry), `Settings/MoreScreen.swift` (Backup rows, `.fileExporter` / `.fileImporter`), `Core/FJStore.swift` (`DeviceAuth.shared.handler = FJDeviceAuth()`, backup notice toast), `Info.plist` (`NSFaceIDUsageDescription`).
- **Most likely compile problem:** Swift setting the Kotlin suspend handler. `FJDeviceAuth` assumes Kotlin/Native exports `suspend (String) -> Boolean` as `KotlinSuspendFunction1` (`invoke(p1:completionHandler:)`, result `KotlinBoolean`). If it does not compile, add a tiny helper in `iosMain` (for example `fun installDeviceAuth(check: (String, (Boolean) -> Unit) -> Unit)` wrapping `suspendCancellableCoroutine`) and call it from Swift.
- **Test these on the Mac:** hide a video, accept the notice, the recovery code sheet shows (also when the area is locked); "Forgot PIN?" in the unlock sheet (Face ID or device passcode), cancel closes the one-shot window (`OnHiddenResetCancel`), new PIN works and the hidden items stay; Backup exports a file (name keeps `.fjbackup`) and Restore (accepts `.data` and `.json`) merges into a second empty install; copy of the recovery code is local only (no Universal Clipboard) and expires.
- **macOS native:** `StoreLock.macos.kt`, `BackupPlatform` actual, `LAContext` auth in `platform_mac.mm` and the `LocalAuthentication` link in `desktopNative/CMakeLists.txt` were not compiled.
- **Android debug flavour:** `com.michelle.raouf.fetch_jet.debug`, name "FetchJet Debug", orange icon with a DEBUG ribbon; it uses its own ports (bridge 28470, sync 28471 / 28472) so it can sit next to the release build. Nothing to do on the Mac.
