# FetchJet 1.3.0: work list

1.2.0 is released (2026-11-05); this is the active work list now, on branch `release-v1.3.0`. These
are the medium / large features from the Ezoo 1.0.9 comparison (2026-11-04) that go *beyond* the
competitors, kept out of 1.2.0 because they are bigger than the quick wins there. Ordered by
value / cost. Mark an item `[x]` when the owner has tested it.

**State (2026-11-08):** 1, 2, 3 and 4 done. **Now: 5** (audio format conversion), then 6.

## 1. Subtitles with the video (medium) — DONE

**Done** (commit `b846cc5`, owner-noted 2026-11-07): `domain/model/SubtitleTrack.kt`,
`SubtitlesSelectionCard` in `AddDownloadMedia.kt` (language list + embed toggle), events
`OnSubtitleToggled` / `OnToggleEmbedSubtitles`, download + embed in `MediaDownloadSteps.kt`,
`subtitlesTitle` etc. in all 15 providers.

Why: a real "ahead" feature — Ezoo / IDM do not download or embed subtitles.

- [x] List the subtitle tracks a video offers (yt-dlp `-j` already returns `subtitles` and
  `automatic_captions`): show the languages in the Add dialog, let the user pick none / one / some,
  with an "auto-generated" marker.
- [x] Download the picked tracks next to the video, and an option to **embed** them into the
  container with ffmpeg (soft subs: `-c:s mov_text` for mp4, `srt`/`ass` for mkv). ffmpeg is
  already bundled on every platform.
- [x] Works on desktop, Android, iOS (the resolver and the ffmpeg step are common code; only the
  tool paths differ per platform, as the media download already does).

## 2. Speed schedule by time of day (medium) — DONE

**Done** (commits `31092b4` schedule, `36d9128` indicator, `66181fd` reset-to-unlimited,
`5c1f318` 12-hour AM/PM time picker).

Why: throttle during the day, full speed at night — on top of the quick "turtle" toggle in 1.2.0.

- [x] Settings: one or more time windows (from / to) each with a speed cap, plus "unlimited outside
  the windows".
- [x] A ticker flips `_globalDownloadSpeedLimit` when a window starts / ends; the limit already
  applies live. One expect/actual ticker per platform (desktop timer, Android via the running
  service, iOS while the app / background session is alive).
- [x] The current window shows in the UI (e.g. the turtle bar) so it is clear why the speed is
  capped.

## 3. Task priority (medium) — DONE (`9696bca`, owner-tested 2026-11-08)

Why: no task-level priority exists today (the `priority` field in `TaskCache` is per torrent file,
not the queue order).

(Drag-to-reorder the list was dropped on the owner's decision, 2026-11-07 — "just list ordering,
not worth it". Do not bring it back unless the owner asks.)

- [x] A per-task priority (High / Normal / Low): shown as a tappable chip on each row (one tap
  cycles High/Normal/Low, `TaskRows.kt` `PriorityChip`), decides which queued task starts next when
  a slot frees up, AND splits the global speed limit by weight among the running downloads
  (High:Normal:Low = 3:2:1, `getEffectiveDownloadLimit`, live via the `RateLimiter`). Also: the Add
  dialog's duplicate check now compares the full file name incl. extension, so the same link with a
  different name is a separate download.

## 4. Send a download to another device (large) — DONE (owner-tested 2026-11-08)

Why: FetchJet is the only cross-platform one here (Windows, macOS, Linux, Android, iOS) — "start on
the phone, continue / watch on the desktop" is a feature no competitor can match.

Commits on `release-v1.3.0`: `2b3c84b` … `de162f6`, `f5ba90c` (iOS), `573d276`, `3c210f4`,
`a4b7b3e`, `db58363`, `721656d`, `21cf243`, `3ebbaa3`.

- [x] Transport: **LAN-only, direct device-to-device, no server, no cloud.** UDP broadcast
  discovery on port 28461 (announce every 2 s, peers expire after 8 s); the send is a direct TCP
  connection on 28462. JVM + Android: `feature/device_sync/DeviceSyncImpl.jvmAndroid.kt`.
  iOS: `DeviceSyncImpl.ios.kt` (same protocol, same frames) on a Swift bridge
  (`iosApp/iosApp/NativeDeviceSyncBridge.swift`: CryptoKit + POSIX sockets, no Bonjour so it
  meets the other platforms on the same wire), `IosDeviceSyncNative` / registry in iosMain,
  `NSLocalNetworkUsageDescription` + `NSBonjourServices` in Info.plist.
- [x] Secure pairing, **not** trust-on-first-use: ECDH P-256, both sides show the same 6-digit
  code (SHA-256 of the two public keys) and confirm it; the key is stored encrypted (`Secrets`);
  everything in transit is AES-256-GCM (`CryptoBox`; on iOS CryptoKit `SealedBox.combined`, same
  `nonce(12) ++ ct ++ tag(16)` layout, byte-checked against Android).
- [x] Pairing hardening (owner's rules): one request per device every 30 s (the other side gets
  "Wait N s"); "busy" while another device's code is on screen (a second request must not replace
  the code being read); **Block** in the pairing dialog (the blocked device gets "The other device
  blocked this one", its sends are refused, it is hidden from the send list); cancelling the code
  answers "declined" to the other side ("The other device declined the pairing"). Unpair tells the
  other device (frame `T_UNPAIR`, sealed with the shared key); a send to a device that forgot this
  one fails at once and drops the stale pairing (it used to wait 90 s with Cancel disabled).
  Incoming file names are reduced to a bare name (no `../`).
- [x] **Visible to other devices** (Settings > Devices, **off by default**): off = no announce and
  no open port, so nobody can find, pair with or send to this device; it can still see others and
  send to them. `BehaviorSettings.visibleToDevices`, `DeviceSync.setVisible`, applies live.
- [x] **Settings > Devices** section: the visibility switch, Paired devices (Unpair), Blocked
  devices (Unblock). The list summary reads Visible / Hidden + the paired count.
- [x] What can be sent (owner's rules, `DownloadTask.canSendToDevice` / `canTransferToDevice`):
  **torrent never** (no button); **resumable** (direct or media) → "Transfer what's downloaded"
  sends the scratch folder (direct `.partN` + `.chunks`; media own-engine `stream$i.part*`, HLS
  segments, yt-dlp `.part`) and the other device continues from it with the same `formatId` and
  clip settings; **finished** (any engine) → the file itself (part0 + one-piece manifest, finished
  there with no network); **non-resumable and not finished** → not sendable at all.
- [x] "Sent to <device>" / "Download received from <device>": a toast **and** a system notification
  on both devices (Android channel "Send to device", iOS local notification shown even with the app
  open via `UNUserNotificationCenterDelegate`, desktop tray). `core/notification/SystemNotice`.
- [x] Send sheet for many devices: capped scrolling list grouped Paired / Other devices, compact
  rows, search when more than 5, first device and Transfer preselected, readable in light and dark.
- [x] Owner-tested: Android ↔ iOS simulator both ways, a 29% Facebook video continued at ~29% on
  the other device, completed files land at 100%, Block / wait / declined messages, torrent button
  hidden, Visible on/off on Windows, macOS, iOS.
- [x] The local-network permission prompt shows on a real iPhone (owner's screenshot, before the
  iOS sync code; the Info.plist description was added after it).
- [x] Discovery without broadcast (`f3526b1`): a real iPhone may not send or hear UDP broadcasts
  without Apple's multicast entitlement (the simulator does not enforce it). Every device now also
  sends its announce to known devices directly, a hidden one sends a `fj-probe` that visible
  devices answer directly, and iOS sweeps its /24 every 30 s. Verified with
  `FETCHJET_SYNC_NO_BROADCAST=1` on both Windows and the iOS simulator: they found each other,
  hidden iOS still saw Windows, and a link was sent iOS → Windows.
- [ ] Not tested: a **real iPhone** (no device available, 2026-11-08). If discovery fails there,
  "Connect by IP" and sends after pairing still work (unicast).

## 5. Audio format conversion (medium)

Why: let the user grab the best-quality audio and get it as any format they want (MP3 for the car,
FLAC to keep, etc.) without a second tool. Moved out of 1.2.0 (2026-11-04, owner's decision): the
diff was getting large and it needs the HLS path fixed, so it is not a quick win.

Current state in `master`/1.2.0: the own-engine path (`MediaOwnEngine.downloadWithOwnEngine`) has a
latent MP3-only branch (`audioTarget && targetExt == "mp3"`) reached only if the output file name
ends in `.mp3`; there is **no UI** to pick a format. An earlier generalized attempt (an
`audioEncoderArgs(ext)` helper for mp3/m4a/opus/ogg/flac/wav, a "Convert to" chip picker in the Add
dialog's `FileNameSection`, and `audioConvertTo` / `audioOriginal` i18n strings in all 15 providers)
was reverted from the 1.2.0 branch and should be redone here.

**Done, Android-tested 2026-11-08** (SoundCloud → MP3 on the A56: real MP3, full 3:33). Not
checked yet: iOS (Mac build), desktop, the link-list / playlist "Convert all to".

- [x] "Convert to" chips when the selected quality is audio-only (`ui/AudioConvertSection.kt`): the
  first chip is the file's own format (e.g. M4A), selected by default = no re-encode; then the
  other formats of MP3 · M4A · OPUS · FLAC · WAV · OGG. The choice only changes the file name's
  extension (`audioConvertFormat` in the UI state, `mediaExt()` in `DashboardAddDownload.kt`).
- [x] One convert step for every engine (`MediaAudioConvert.kt`, `convertAudioIfNeeded`): after
  the download, if the file's extension differs from the name's, ffmpeg converts (tries `-c:a copy`
  first, then `audioEncoderArgs`: libmp3lame -q:a 2, aac 192k, libopus 160k, libvorbis -q:a 5,
  flac, pcm_s16le). A failed convert keeps the native file. yt-dlp no longer forces MP3 on
  Android / iOS (no `-x`), and on desktop an audio download is saved as `%(ext)s`.
- [x] HLS (SoundCloud): writes to `<name>.mka` first (`hlsOutputFor`), then converts; a failed own
  engine deletes its half-written target so yt-dlp's fallback is not mistaken for the result.
- [x] Link list and playlist ("Audio only"): the same chips as "Convert all audio to".
- [x] i18n: `audioConvertTo` / `audioConvertAllTo` / `audioOriginal` in all 16 files.
- [x] Also: Open file on a file that is gone from disk shows "File not found" with Remove from list
  (`ui/MissingFileDialog.kt`, `PathResolver.fileExists`), instead of the file manager.
  Owner-tested on Android.

## 6. Release notes in the app's language (small / medium)

Why: the in-app "update available" dialog shows the changelog in English only; the owner wants it in
the app's own language (asked 2026-11-05 while finishing 1.2.0).

- [ ] The release uploads a notes file per language: `release-notes.md` (English = default /
  fallback) plus `release-notes-<lang>.md` for the 15 app languages (en ar fr de es pt it ru tr id
  zh ja ko hi fa). CI just uploads whatever `release-notes*.md` files exist.
- [ ] `UpdateManager` fetches `release-notes-<AppLanguage code>.md` first, falls back to
  `release-notes.md` (const `RELEASE_NOTES_FILE`). Keep the `<!-- fetchjet-unchanged: ... -->`
  marker in every file.
- [ ] Translate the Highlights into the 15 languages (Claude can produce them; English stays the
  base).
- **Note:** only takes effect from the *next* release onward — the pre-update dialog is drawn by the
  already-installed (old) app, which has no language-aware fetch, so e.g. 1.2.0 → 1.3.0 still shows
  English; 1.3.0 → 1.4.0 is the first update that can use it.

## Also done in 1.3.0 (not on the list)

- [x] Theme: Settings > General > Appearance = System / Light / Dark, **default System**, follows
  the device live on Android, Windows and iOS (`1b3187b`, iOS host fix `83ed970`).
- [x] Snackbar messages show over the task details page on phones / tablets (`db58363`).
- [x] Custom `AppScrollbar` in the list views (`90c54d1`).
- [x] Settings time picker in 12-hour format with AM/PM (`5c1f318`).
- [x] "Fast" speed preset (`b846cc5`).

## Carried over from 1.2.0 (external, waiting)

- [ ] Edge Android listing: waiting for Microsoft's answer. Edge iOS: not tested
  (see `docs/edge-android-test-cases.docx`).
