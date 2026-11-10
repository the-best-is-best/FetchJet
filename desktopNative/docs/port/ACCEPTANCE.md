# Native Windows app: acceptance steps for the owner

These steps are for the owner, on his own PC or in Windows Sandbox. Nothing here is done by the
tools: installing, the tray, the mouse and the keyboard stay with the owner. Tick each line, and
write the result next to it. A step that fails is a bug to report with the step number.

Status of each part and the evidence is in `STATUS.md`; the feature list against the JVM app is
in `PARITY.md`.

## 1. Development build (no install)

1. Build the EXE: `desktopNative\build.cmd debugShared` (or the release line from
   `packaging\build-release.cmd`).
2. Start `desktopNative\run-dev.cmd`. It uses its own settings folder
   (`%LOCALAPPDATA%\FetchJetDev\.fetch_jet`), bridge port 28459, and no tool downloads.
   - [ ] The window opens, the home list is there, the sidebar counts are right.
   - [ ] Add a download from a link (any file you own): it starts, the speed shows, it finishes.
   - [ ] Pause, resume, remove (with and without the file).
   - [ ] Settings: each page opens; a switch, a picker and a text field keep their value after a restart.
   - [ ] Arabic: Settings > General > Language > العربية; the layout turns right to left.
   - [ ] Dark and light theme switch.

## 2. Windows shell (the part nobody has seen on a real desktop yet)

- [ ] Close button hides the window to the tray; the tray icon stays.
- [ ] Left click on the tray icon shows the window again; double click too.
- [ ] Tray menu: Show App, When downloads finish, Close App.
- [ ] A second start with a link (open a `.torrent` or a `fetchjet://` link) hands the link to the
      running window and exits at once.
- [ ] Clipboard: copy a link with "Watch the clipboard" on; the offer appears.
- [ ] Notification when a download finishes.

## 3. Browser bridge and extension

- [ ] With the dev build running, the extension (Edge or Chrome, dev build on port 28459) can
      pair: the pairing dialog shows, Allow works, Block works.
- [ ] "Download with FetchJet" from a page sends the link to the Add dialog.

## 4. Send to device (needs a second device on the same network)

- [ ] Settings > Devices: Visible to other devices on; the other device sees this PC.
- [ ] Send a finished file to the other device; it arrives and the SHA-256 matches.
- [ ] Send an unfinished download ("Transfer what's downloaded") and check it continues there.
- [ ] Windows Firewall asks once for FetchJet on private networks: allow it.

## 5. Torrents (only after the torrent work is in the build)

- [ ] Add a `.torrent` file: the file list shows, a file can be unchecked, priorities change.
- [ ] Download finishes; the files match.
- [ ] Settings > BitTorrent: the upload limit applies.

## 6. Proxy (Settings > Network)

- [ ] HTTP proxy with login: a download goes through it (the local test proxy on 127.0.0.1 is enough).
- [ ] SOCKS5 proxy: the same.
- [ ] A wrong password fails with a message, it does not hang.

## 7. Installer (in Windows Sandbox, not on the main PC)

- [ ] `packaging\msix\build-msix.ps1` and the stage / MSI build run without errors.
- [ ] Install the MSI in the Sandbox; the app starts from the Start menu.
- [ ] Install the JVM 1.4.0 (or 1.3.1) MSI first, then the native MSI over it: the upgrade keeps the
      downloads list and the settings.
- [ ] Uninstall the native MSI: the program folder goes, the downloads folder stays.
- [ ] MSIX: install with a test certificate in the Sandbox (see `packaging\msix\`), start-at-login
      and `.torrent` / `fetchjet://` open the app.

## 8. Resource use

- [ ] Start the app, wait 1 minute idle: note the memory shown by Task Manager (Private working set).
- [ ] Download a 1 GB file: note the memory during the download. The target is under 50 MB.
- [ ] Stop the download: the memory goes back down.

## Report

For each failed line write: the step number, what you did, what you saw, and the time. Send it
with the log file `%LOCALAPPDATA%\FetchJetDev\.fetch_jet\logs\app.log` (dev build) or
`%USERPROFILE%\.fetch_jet\logs\app.log` (installed). The log has no cookies or logins (they are
redacted).
