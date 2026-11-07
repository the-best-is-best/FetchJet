# FetchJet 1.3.0: work list

1.2.0 is released (2026-11-05); this is the active work list now, on branch `release-v1.3.0`. These
are the medium / large features from the Ezoo 1.0.9 comparison (2026-11-04) that go *beyond* the
competitors, kept out of 1.2.0 because they are bigger than the quick wins there. Ordered by
value / cost. Mark an item `[x]` when the owner has tested it.

## 1. Subtitles with the video (medium) — DONE

**Done** (code in tree, owner-noted 2026-11-07): `domain/model/SubtitleTrack.kt`,
`SubtitlesSelectionCard` in `AddDownloadMedia.kt` (language list + embed toggle), events
`OnSubtitleToggled` / `OnToggleEmbedSubtitles`, download + embed in `MediaDownloadSteps.kt`,
`subtitlesTitle` etc. in all 15 providers.

Why: a real "ahead" feature — Ezoo / IDM do not download or embed subtitles.

- [ ] List the subtitle tracks a video offers (yt-dlp `-j` already returns `subtitles` and
  `automatic_captions`): show the languages in the Add dialog, let the user pick none / one / some,
  with an "auto-generated" marker.
- [ ] Download the picked tracks next to the video, and an option to **embed** them into the
  container with ffmpeg (soft subs: `-c:s mov_text` for mp4, `srt`/`ass` for mkv). ffmpeg is
  already bundled on every platform.
- [ ] Works on desktop, Android, iOS (the resolver and the ffmpeg step are common code; only the
  tool paths differ per platform, as the media download already does).

## 2. Speed schedule by time of day (medium) — DONE

**Done** (commits `31092b4` schedule, `36d9128` indicator, `66181fd` reset-to-unlimited,
`5c1f318` 12-hour AM/PM time picker).

Why: throttle during the day, full speed at night — on top of the quick "turtle" toggle in 1.2.0.

- [ ] Settings: one or more time windows (from / to) each with a speed cap, plus "unlimited outside
  the windows".
- [ ] A ticker flips `_globalDownloadSpeedLimit` when a window starts / ends; the limit already
  applies live. One expect/actual ticker per platform (desktop timer, Android via the running
  service, iOS while the app / background session is alive).
- [ ] The current window shows in the UI (e.g. the turtle bar) so it is clear why the speed is
  capped.

## 3. Task priority (medium)

Why: no task-level priority exists today (the `priority` field in `TaskCache` is per torrent file,
not the queue order).

(Drag-to-reorder the list was dropped on the owner's decision, 2026-11-07 — "just list ordering,
not worth it". Do not bring it back unless the owner asks.)

- [ ] A per-task priority (High / Normal / Low): shown as a tappable chip on each row (one tap
  cycles High/Normal/Low, `TaskRows.kt` `PriorityChip`), decides which queued task starts next when
  a slot frees up, AND splits the global speed limit by weight among the running downloads
  (High:Normal:Low = 3:2:1, `getEffectiveDownloadLimit`, live via the `RateLimiter`). Also: the Add
  dialog's duplicate check now compares the full file name incl. extension, so the same link with a
  different name is a separate download. Needs the owner's test.

## 4. Send a download to another device (large) — BUILT, waiting for the LAN test

Why: FetchJet is the only cross-platform one here (Windows, macOS, Linux, Android, iOS) — "start on
the phone, continue / watch on the desktop" is a feature no competitor can match. Large because the
bridge is loopback-only today, so it needs a real transport and its own security design.

- [x] Transport: **LAN-only, direct device-to-device, no server, no cloud.** UDP broadcast
  discovery on port 28461 (a device announces id/name/platform/tcp-port every 2 s; peers expire
  after 8 s). The actual send is a direct TCP connection to the chosen device.
  `feature/device_sync/DeviceSyncImpl.jvmAndroid.kt` (JVM + Android share one engine).
- [x] Secure pairing, **not** trust-on-first-use: first contact runs ECDH (secp256r1); both
  devices derive the same key and show the **same 6-digit code** (SHA-256 of the two public keys);
  each user confirms the code matches, then the pairing is stored. The key is kept encrypted via
  `core/security/Secrets`. Everything in transit (the link, cookies, file bytes) is AES-256-GCM
  sealed with that key (`core/security/CryptoBox`). An unpaired device is refused.
- [x] "Send to device" on a task (details pane → "Send to device"): device picker + mode +
  "delete from this device after it's sent". **Two modes:**
    - **Link only** — the other device downloads it from the start (the link, headers, cookies,
      file name, engine, chosen quality).
    - **Transfer what's downloaded** (HTTP direct only) — streams this task's `.partN` + `.chunks`
      to the other device, which drops them into its own scratch folder and **continues from the
      same %**. The receiver holds the task ~4 s so the parts land before the engine starts, then it
      resumes from the manifest.
      Delete-after-success removes the local task **only** once the receiver confirmed full receipt.
      Code: `presentation/dashboard/DashboardDeviceSync.kt` (ViewModel glue, receiver → queue),
      `ui/DeviceSendDialog.kt` (sheet + pairing dialog), events in `DashboardContract.kt`.
- Offline device: the send fails fast ("device offline or not paired"); the user re-sends when it
  is back. (No persistent outbound queue yet — add if the test shows it's needed.)
- i18n: 20 `sysDevice*` strings in all 15 providers (Arabic done; the other 13 fall back to
  English until translated).
- Android: `ACCESS_WIFI_STATE` + `CHANGE_WIFI_MULTICAST_STATE` added; a Wi-Fi multicast lock is
  held while discovering (`feature/device_sync/MulticastLock.android.kt`).
- Compiles on **desktop (JVM), Android (assembleDebug), and iOS simulator**. **Not yet tested.**
- [ ] **NEXT: LAN test, Mac desktop ↔ Android (A56) on the same Wi-Fi.** Share a download on one,
  it should appear on the other; pair with the 6-digit code; try both modes and delete-after.
- [ ] iOS phase (separate): real `CryptoBox` (CryptoKit) + LAN discovery (Bonjour/NWBrowser).
  Today iOS has a `NoopDeviceSync` + a `CryptoBox` stub, so it builds but does not discover/send.
- [ ] Security review before it ships (same bar as the bridge pairing review): no un-consented
  sends, no way for a device on the LAN to push downloads without pairing. (Design already uses
  mutual-code pairing + per-pair encryption; review the engine once the test passes.)

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

- [ ] When the selected quality is audio-only, show a "Convert to" picker whose default is
  **Original** (the native format that downloads — no re-encode, no quality loss), plus
  MP3 · M4A · OPUS · FLAC · WAV · OGG. The app downloads best-quality audio and converts with
  ffmpeg.
- [ ] Generalize the own-engine convert step to `audioEncoderArgs(targetExt)` (libmp3lame -q:a 2,
  aac 192k, libopus 160k, libvorbis -q:a 5, flac, pcm_s16le); keep the atomic-move fast path when
  the source extension already equals the target.
- [ ] **Fix the HLS path** (`HlsDownloader`, used by SoundCloud): today it only does `-c copy` into
  the output container, so a chosen audio format is ignored. Add a transcode-to-target-extension
  step (or a post-merge convert) so conversion works for HLS audio too. Verify on SoundCloud.
- [ ] Offer the picker in the link-list and playlist views as well (a global "Convert all to"
  control that rewrites each selected audio track's output extension), per the owner's request.
- [ ] i18n: `audioConvertTo` / `audioOriginal` (and any new strings) in `AppLocalization` + all 15
  providers.

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

## Carried over from 1.2.0 (external, waiting)

- [ ] Edge Android listing: waiting for Microsoft's answer. Edge iOS: not tested
  (see `docs/edge-android-test-cases.docx`).
