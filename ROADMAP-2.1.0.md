# FetchJet 2.1.0: work list

Decided 2026-11-22 (owner), after 2.0.0 is released. Nothing here is started.

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
