# FetchJet 1.3.0: work list

1.2.0 is released (2026-11-05); this is the active work list now, on branch `release-v1.3.0`. These
are the medium / large features from the Ezoo 1.0.9 comparison (2026-11-04) that go *beyond* the
competitors, kept out of 1.2.0 because they are bigger than the quick wins there. Ordered by
value / cost. Mark an item `[x]` when the owner has tested it.

## 1. Subtitles with the video (medium)

Why: a real "ahead" feature — Ezoo / IDM do not download or embed subtitles.

- [ ] List the subtitle tracks a video offers (yt-dlp `-j` already returns `subtitles` and
  `automatic_captions`): show the languages in the Add dialog, let the user pick none / one / some,
  with an "auto-generated" marker.
- [ ] Download the picked tracks next to the video, and an option to **embed** them into the
  container with ffmpeg (soft subs: `-c:s mov_text` for mp4, `srt`/`ass` for mkv). ffmpeg is
  already bundled on every platform.
- [ ] Works on desktop, Android, iOS (the resolver and the ffmpeg step are common code; only the
  tool paths differ per platform, as the media download already does).

## 2. Speed schedule by time of day (medium)

Why: throttle during the day, full speed at night — on top of the quick "turtle" toggle in 1.2.0.

- [ ] Settings: one or more time windows (from / to) each with a speed cap, plus "unlimited outside
  the windows".
- [ ] A ticker flips `_globalDownloadSpeedLimit` when a window starts / ends; the limit already
  applies live. One expect/actual ticker per platform (desktop timer, Android via the running
  service, iOS while the app / background session is alive).
- [ ] The current window shows in the UI (e.g. the turtle bar) so it is clear why the speed is
  capped.

## 3. Reorder the queue + task priority (medium)

Why: no task-level priority exists today (the `priority` field in `TaskCache` is per torrent file,
not the queue order).

- [ ] Drag to reorder the download list (Compose reorderable list; works with mouse on desktop and
  long-press-drag on phone), order persisted in `tasks_cache.json`.
- [ ] A per-task priority (High / Normal / Low) that decides which queued task starts next when a
  slot frees up.

## 4. Send a download to another device (large)

Why: FetchJet is the only cross-platform one here (Windows, macOS, Linux, Android, iOS) — "start on
the phone, continue / watch on the desktop" is a feature no competitor can match. Large because the
bridge is loopback-only today, so it needs a real transport and its own security design.

- [ ] Decide the transport: LAN discovery + direct device-to-device (no server), or an
  account / relay. Prefer LAN-only first (no cloud, no new privacy surface).
- [ ] Pair devices securely (reuse the token + pairing idea from the bridge, not trust-on-first-use)
  and encrypt the link in transit.
- [ ] "Send to <device>" on a task: the other device adds it to its own queue (the link, headers,
  cookies, file name, chosen quality). Handle the device being offline (queue and retry).
- [ ] Security review before it ships (same bar as the bridge pairing review): no un-consented
  sends, no way for a device on the LAN to push downloads without pairing.

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
