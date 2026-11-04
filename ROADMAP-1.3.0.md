# FetchJet 1.3.0: work list

Planned after 1.2.0 (not released yet; 1.2.0 stays the active branch for now). These are the
medium / large features from the Ezoo 1.0.9 comparison (2026-11-04) that go *beyond* the
competitors, kept out of 1.2.0 because they are bigger than the quick wins there (section 8 of
`ROADMAP-1.2.0.md`). Ordered by value / cost. Mark an item `[x]` when the owner has tested it.

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
