# FetchJet 2.1.0: work list

Decided 2026-11-22 (owner), after 2.0.0 is released. Released to users as 2.1.1 (2.1.0 is the owner's own build).

## Status (2026-11-23)

- **Item 1, hidden downloads: done and more.** PIN on Android, iOS and the Slint desktop; hide one, several, a playlist or a
  folder group; instant hide with one progress line; files move to a private folder; audio and video are encrypted
  (ChaCha20 + HMAC, device-bound key) and decrypted only to a temp copy when played; + button inside Hidden starts hidden
  downloads; no notifications; one shared row menu; recovery code (one per vault); forgot PIN through the device lock;
  backup and restore of the downloads list; all-files-access request when a file another install owns cannot be read.
  Tested on the A56 (debug build) and by the corehost tests on Windows. iOS and macOS: not built yet (see HANDOFF-2.1.0.md).
- **Still open in item 1:** pattern lock (in progress), the leak audit below (in progress).
- **Item 2, extension "open the app directly": done** (extension 3.8.0, plus an arrow and an "Open in FetchJet" row on the
  page button). Not tested in a real Chrome / Edge / Firefox; the installers (MSI, MSIX, DMG, DEB) were not re-tested.

## 1. Hidden downloads (PIN, pattern later)

- Hide one download, several selected, a whole group or a playlist. Hidden ones disappear from the list, search, counters,
  total speed, the tabs, notifications (generic text, the download continues) and "Send to device".
- A "Hidden" entry in More opens them: first time the user sets a PIN (4 to 6 digits, twice), later it asks the PIN
  (wrong PIN: error, short back-off after 5 tries). Inside: unhide one / selected / all, change or remove the PIN.
- Hide only: the files stay on disk and are not encrypted (owner's decision). Encrypting the files is a later item.
- Order: PIN first, pattern lock after (keep the lock type extensible). Mobile first (Android Compose, iOS SwiftUI); the Slint
  desktop later.
- PIN stored salted and hashed in the settings folder; the `hidden` flag lives on `DownloadTask` (default false in
  `TaskCache`, so older files load), a hidden playlist hides the tasks added to it later.
- Code map found on 2026-11-22: a group / playlist is `DownloadTask.playlistId`; the UI reads `state.allTasks` (filled by
  `observeQueue`); notifications are built in `observeTasksForNotifications`; persistence is `TaskCache`.
- Must not leak: names of hidden tasks in logs, the usage ping, the Slint snapshot and the SwiftUI state.

## 2. Extension: open the app directly (all platforms)

- An option in the extension settings: "Open FetchJet directly instead of the silent send". The download button then opens the
  app with `fetchjet://download?url=` (the path `openAppAt` / `PROTOCOL` in `extension/background.js` already has, used on iOS
  and when the app is asleep).
- The silent local-server send stays the default: it carries cookies, headers and the qualities list and does not pop the app
  or ask "Open FetchJet?" in the browser each time. The direct mode loses those and the browser asks the first time per site.
- Register the scheme where it is missing: macOS (`CFBundleURLTypes` in the app's Info.plist), Linux
  (`x-scheme-handler/fetchjet` in the `.desktop` file, `Exec ... %u`), the Store MSIX (protocol extension in its manifest).
  Already registered: Android (manifest), iOS, Windows MSI / portable (written at run time by `BrowserIntegration.mingw.kt`).
  The native app already understands `fetchjet://` (`Launch.kt`).
- Touches the installers (MSI, MSIX, DMG, DEB) and the extension version (3.6.0 -> bump `EXTENSION_VERSION` and the manifests
  together). Test every installer after the change.
