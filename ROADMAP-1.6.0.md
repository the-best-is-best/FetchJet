# FetchJet 1.6.0: work list

Written 2026-11-19 after a look at Ezoo Download 1.0.12 (released 2026-11-19, Rust + Slint, Windows + Firefox only;
read from its commits and its "what's new" texts in `crates/app/lang/rs/*.json`, the app itself was not run).
Each item says what Ezoo does, so we know what "done" has to cover. FetchJet already has, from the same list: the
unpacked yt-dlp folder build, a zip unpacker that checks names and CRC, a file name box in the Add dialog.
Legend: [x] done and tested as said, [ ] not done or not tested yet.

## 1. Subtitles: one at a time, and added to a finished video [ ]

Owner's decision (2026-11-19): no "subtitles only" download. Instead: the video downloads with its subtitles as before
(one language at a time now), and the details of a finished video have "Add subtitles": the page is read again (fresh
links), the languages already in / next to the file are marked, the new ones go into the file when the task embeds them
(after the subtitle streams already there) or next to it as `name.<lang>.srt`.
Done 2026-11-19 (Windows):
- [x] `SubtitleFiles.kt` (`fetchSubtitleFiles`: 1.5 s between languages, a 429 is asked again after 5 / 15 / 30 s, then
  reported as refused; `embedSubtitleFiles` counts the subtitle streams already in the file and numbers the new ones
  after them; `placeSubtitleFilesBeside`); `processSubtitles` uses it and keeps on the task only the tracks that reached
  the file; `addSubtitlesToFinished` + `QueueManagerRepository.inspectSubtitlesOf` / `addSubtitles`;
  `DashboardSubtitles.kt` (`SubtitlesUi`, 4 events); 12 texts in 15 languages.
- [x] jvmTest `SubtitleFilesTest` 6 / 6 (order and pause, retry after 429, refused / failed kept apart, VTT -> SRT, side
  names, stream numbering); all jvmTest 214 / 0.
- [x] ffmpeg check by hand with the app's arguments: an mp4 with an English track got an Arabic track as a second stream
  (eng + ara, the probe counted 0 -> 1 -> 2). mkv / webm not run.
- [x] Compose (`SubtitlesDialog.kt`, details button): owner-tested on the A56 (2026-11-19, embed on): a YouTube video
  with one language at download and another added later from the details; the file pulled from the phone has two
  mov_text streams (ara, eng) with the right text, and the owner sees both in his player.
- [x] Add dialog: the subtitles card shows only while a quality with video is picked; an audio-only download carries no
  subtitles (`subtitlesOffered`, `anyVideoSelected`; Compose, Slint, SwiftUI). Owner-checked on the A56.
- [x] Details: "Source" row removed on Android and iOS (the desktop had only "Page"); a link that is still encrypted
  (`fjenc1:`, e.g. tasks restored by Android backup after a reinstall, the Keystore key is not in the backup) is not
  shown on any of the three.
- [x] Slint (`SubtitlesPage.slint`, channel 30 `subtitlesPage`, actions `DetailsSubtitles` / `SubtitlesToggle` /
  `SubtitlesAdd` / `SubtitlesDismiss`): core + host build; new corehost `test_subtitles.py` 22 / 0 (download with
  English next to the video, details button, page lists 3 tracks with English marked, add Arabic -> `.ar.srt`, reopen
  shows both, a plain file offers nothing); test_home 41 / 0, test_add 29 / 0, test_media 31 / 0 (2 failed once in the
  first run, 0 in the second). The real window was not opened.
- [x] Row menu (right-click / long press): `RowMenuEntry.SUBTITLES` after Compress, for a finished video; Compose,
  Slint and SwiftUI map it (iOS opens the details and the sheet there). corehost `test_rowmenu` 19 / 0 (keys list
  updated), `test_subtitles` 25 / 0 (menu offers it on the video, the pick opens the page, not on a plain file).
- [x] SRT or VTT for the separate file (as Ezoo): the Add dialog has three cards (Embed / Separate file (SRT) /
  Separate file (VTT)), the choice is saved on the task (`subtitleFormat`, `tasks_cache.json` field, "" = srt) and
  "Add subtitles" later uses it; a VTT track stays VTT, an SRT one is converted by ffmpeg. jvmTest
  `SubtitleFilesTest` 8 / 0, corehost `test_subtitles` 27 / 0 (now with VTT: `.en.vtt` at download, `.ar.vtt` added),
  test_media 31 / 0, test_add 29 / 0. Installed on the A56, not checked there yet.
- [ ] SwiftUI (`SubtitlesSheet.swift`, details button, menu entry, the three cards): written, not compiled (needs the Mac).
- [ ] Not tested: a real YouTube 429 (the server side was faked in the unit test), embedding into a real downloaded video.

Ezoo's version, for reference:

Ezoo: two tabs in the add dialog, "With subtitles" (the video with the subtitles of one chosen language) and
"Subtitles" (all of a video's subtitles, or the chosen ones, as SRT or VTT). Languages are asked for one at a time
with a short pause; a language YouTube refuses for a moment (429) is asked for again by itself; the ones still
refused are named in a clear message ("try again in a few minutes"), what came is kept.
Here: only `embedSubtitles` (into the video). To do: subtitles-only download (SRT / VTT, choose languages), one
language at a time with retry on 429, a clear message in 15 languages. All platforms.

## 2. A file with the same name [ ]

Ezoo: the Add window says a file of that name is already in the folder and offers "Replace it", "New name
(numbered)" or changing the name; "Replace it" keeps the old file until the new one is complete.
Here: always `name (1).ext` without asking. To do: the same three choices in Compose, Slint and SwiftUI; replace
only after the new file is complete.

## 3. Site Grabber in the app [ ]

Ezoo: paste a page link, the app lists the page's files: `srcset` pictures, links without quotes, `data-*`,
`base href`, `og:` tags; links with no file name are asked from the server (real name + size); pages the site
refused are counted; a page that builds its links with JavaScript is named, with a hint to use the extension.
Here: "Download all links on this page" from the extension only. To do: read the page in the app and fill the
existing link list (`linkList`, `extractLinks`), HEAD for the nameless links, the same hints.

## 4. Playlists [ ]

Ezoo: a Size column, a wider / narrower Video column, "Oldest first" numbering, videos downloaded before are not
added again, one "Download complete" window at the end of a playlist instead of one per file.
To do: size per row (we already probe sizes), oldest-first order, skip videos already in the task list, one
completion notice per playlist.

## 5. YouTube "made for kids" and faster qualities [ ]

Ezoo: videos made for children download now; qualities show up faster.
To do: check a made-for-kids video on desktop, Android and iOS first (it may already work); measure our inspect
time against theirs on the same link before changing anything.

## 6. Drag a finished file out of the list [ ]

Ezoo: drag a row to a folder, the desktop or a chat; moved on the same drive, copied to another (Shift moves,
Ctrl copies), as in Explorer.
To do: native desktop (Windows `SHDoDragDrop`, macOS / Linux their own), Slint host.

## 7. Fewer antivirus warnings [ ]

Ezoo: no hidden `reg.exe` / system tools any more (the registry through the Win32 API), every release lists the
SHA-256 of its files.
Here: `PlatformUpdater.mingw.kt` starts a hidden `powershell.exe` for the update; releases have `.sig` files but
no `SHA256SUMS.txt`. To do: replace the hidden PowerShell with direct calls, add `SHA256SUMS.txt` to the release in
CI.

## 8. Checks [ ]

- Shut down / sleep "when downloads finish" must wait for a merge in progress (merge runs while the status is still
  `DOWNLOADING`, so it should already wait: test it with a long ffmpeg merge).
- A window covered by another one, brought back from the tray or after the screen wakes up is drawn whole (Ezoo fixed
  half-empty windows; we fixed the white band at start only).

## 9. Say when a video is DRM-protected [ ]

Seen 2026-11-19 on https://bitmovin.com/demos/drm (PlayReady / Widevine): the Windows app and the extension in Edge
answer "Could not get this video, no downloadable formats found"; nothing wrong is offered, but the user is not told
why. Android offered "Best Quality (CSS)" there before `5fc2cd0`.
To do: when the page or the stream is protected (the extension sees the page ask for a key with
`requestMediaKeySystemAccess` / `encrypted`; the app sees `ContentProtection` in an `.mpd`, `#EXT-X-KEY` other than
AES-128 in an `.m3u8`, or yt-dlp's `has_drm`), say "This video is protected (DRM) and cannot be downloaded" in the
extension button list and in the Add dialog, 15 languages. Only detect and say it: no attempt to download or decrypt
it. Test pages: https://bitmovin.com/demos/drm,
https://reference.dashif.org/dash.js/latest/samples/drm/widevine.html,
https://storage.googleapis.com/shaka-demo-assets/angel-one-widevine/dash.mpd.

## 10. Uninstall removes the startup entry [ ]

Seen 2026-11-19 on the owner's PC: after the MSI / Setup.exe FetchJet was uninstalled (only the Store 1.5.0 left), every
login opened a cmd window and "Windows cannot find 'C:\Users\miche\AppData\Local\FetchJet\FetchJet.exe'". The
cause was `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` value `com.michelle.raouf.fetch_jet`
(`cmd /c start "com.michelle.raouf.fetch_jet" /D "<folder>" "<exe>" --autostart=true`, the JVM app's form; the native
`StartupLauncher.mingw.kt` writes `"<exe>" --autostart=true`). Neither `product.wxs` nor `bundle.wxs` removes it, so an
uninstall leaves it behind. Removed by hand on the owner's PC.
To do: the MSI deletes that value on uninstall (WiX custom action or `RemoveRegistryValue` in a component that is
removed on uninstall), only when it points into the install folder; check with `check-msi.ps1` and a real
install / turn autostart on / uninstall / log in. Also: the native app rewrites an old `cmd /c start` value to its own
form at start, so no cmd window shows at login (antivirus programs dislike it too, see item 7).

## 11. Languages of India [ ]

Why: on 2026-11-19 India was the second country on the usage dashboard (6 of 25 users, Egypt 7), almost all Android.
Today Hindi is in the app (`HindiProvider`), the extension (`extension/i18n/hi.js`) and the release notes
(`release-notes/<v>/hi.md`); the download page `docs/index.html` and `privacy.html` are English only.
To do:
- Hindi checked by someone who reads it (app screens, extension, store listings); fix what reads like a machine.
- The download page and the privacy page in Hindi (language switch on the page).
- New languages, by number of speakers: Bengali (bn), Marathi (mr), Telugu (te), Tamil (ta). Each = a provider in
  `core/i18n/data_provider/` (`scripts/i18n-add-strings.py` for later keys), `extension/i18n/<code>.js` + `KNOWN` in
  `i18n.js` + every list in CLAUDE.md "Extension languages", `release-notes/<v>/<code>.md`, the Settings language list,
  and the store listings (Play-style text for the Microsoft Store, AMO, Edge Add-ons).
- Check the fonts: Devanagari, Bengali, Telugu and Tamil must show on Windows / macOS / Linux in the Slint app (no
  boxes), and the Android / iOS apps.
- Digits stay Latin (as Arabic does now) unless a reader says otherwise.

## Order (proposal)

1, 2, 10, 11, 9, 7, 4, 3, 5, 6, 8.
