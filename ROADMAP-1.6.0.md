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
- [x] SwiftUI (`SubtitlesSheet.swift`, details button, menu entry, the three cards): Mac session 2026-11-19 on `8c4e238`,
  `ios-dev-build.command all` built with no change; iPhone 17 simulator, YouTube `jNQXAC9IVRw` (en + de): card hidden
  for Audio Only, 3 cards; VTT download `.en.vtt`; Add German -> `.de.vtt`, "Added: German"; long-press menu entry;
  embed: `ffmpeg -i` shows mov_text eng then ger; no Source row, no `fjenc1:`; Arabic arrows ↖ on both places.
- [ ] Not tested: a real YouTube 429 (the server side was faked in the unit test), embedding into a real downloaded video.

Ezoo's version, for reference:

Ezoo: two tabs in the add dialog, "With subtitles" (the video with the subtitles of one chosen language) and
"Subtitles" (all of a video's subtitles, or the chosen ones, as SRT or VTT). Languages are asked for one at a time
with a short pause; a language YouTube refuses for a moment (429) is asked for again by itself; the ones still
refused are named in a clear message ("try again in a few minutes"), what came is kept.
Here: only `embedSubtitles` (into the video). To do: subtitles-only download (SRT / VTT, choose languages), one
language at a time with retry on 429, a clear message in 15 languages. All platforms.

## 2. A file with the same name [x] (already there)

Checked 2026-11-19: FetchJet already asks before the download starts (`fileConflictAlert`, "File Already Exists":
Overwrite / Save as Copy `name (1).ext` / close and change the name), on Compose, Slint (corehost `test_media` checks
the three answers) and SwiftUI; the new file is downloaded into temporary parts and the old one is deleted only at the
end (`copyIntoPlace`). Nothing to do. Ezoo's version, for reference:


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

## 4. Playlists [x]

Ezoo: a Size column, a wider / narrower Video column, "Oldest first" numbering, videos downloaded before are not
added again, one "Download complete" window at the end of a playlist instead of one per file.
To do: size per row (we already probe sizes), oldest-first order, skip videos already in the task list, one
completion notice per playlist.

Done (2026-11-19, Windows; all three UIs):
- Size per row: each video's qualities are read in the background, two at a time in the order shown
  (`DashboardPlaylist.kt` `startPlaylistSizes`), the size follows the chosen quality (`playlistSizeFor`); "…" while
  not read. A quality change works the sizes out again from what was read. Not for a list read by the browser.
- "Oldest first": reverses the list and the numbering; the videos are queued in that order.
- Videos already in the downloads list (`url` / `originalUrl`, YouTube links compared by video id) are marked
  "Downloaded before" and left unticked; ticking one adds it again.
- One notice per playlist: playlist downloads carry `playlistId` / `playlistTitle` (saved in tasks_cache.json);
  their per-file "complete" notice is dropped, and when every video of a list that ran in this session is finished
  or failed, one `SystemNotice` says "<list>: N videos downloaded · M failed".
- Tests: corehost `test_playlist.py` 16 / 0, `test_media` updated (31 / 0). iOS SwiftUI changes need the Mac.

After c7a35ff (2026-11-19, Windows + the A56; owner's requests the same evening):
- Subtitles for a playlist and for the several-links list: the languages of the ticked videos, shown when at least
  one ticked video has tracks; "Reading the videos' subtitles… N of M" while reading. Subtitle mode as chips
  (Embed / Separate file · SRT / VTT) on Compose and SwiftUI.
- Background reading on Android: a small foreground notification (`BackgroundReading`, `ReadingForegroundService`,
  dataSync) while a playlist's videos, a link list or the extension's `/inspect` are read; before it Samsung froze the
  app and killed yt-dlp seconds after the user left it.
- A playlist is one row in the downloads list, named after the course (`PlaylistStore`, `playlists.json`, `groupDownloads`):
  open it to see its videos; "Check" reads the list again (videos already downloaded cannot be ticked); its subtitle
  languages can be changed and a language added later. Select all skips videos downloaded before.
- Group by folder (Settings > Downloads, off by default): every other download folds into its folder's row; a row goes
  away when it has no downloads left. Playlists stay their own rows.
- A download that starts opens its group (its playlist, or its folder when grouping by folder). A single video saved
  in a saved playlist's folder joins that playlist (`PlaylistStore.inFolder`), with a notice.
- Extension downloads on Android: a finished download of the same file (same link, or the same path with only the
  signed query changed, `sameFileLink`) asks "replace / save a copy" instead of downloading again (the owner got the
  same story 5 times); a link sent while the app's screen was closed is no longer lost (the cleared view model left
  its bridge handler behind); Facebook story pages are not given to yt-dlp (no extractor, the button spun for a
  minute); Facebook CDN files with code names are saved as "Facebook story / reel / video <date time>.mp4"; the
  "Started download" toast shows a short name. Folder picker buttons no longer squashed; selection bar Delete fits.
- A video the extension sends from a playlist page (`list=` in the link or the page) of a playlist saved in the app
  goes to that playlist's folder and joins it (`PlaylistStore.forLink`). The details show the playlist's link
  ("Playlist", with Copy) on all three UIs.
- The playlist page has "Open folder" on all three UIs, with or without group by folder. Downloads saved on their own
  in a saved playlist's folder become its videos (`adoptLoneVideos`, `QueueManagerRepository.setPlaylist`), on start
  and whenever a playlist is saved; only when the folder is named after the playlist (`PlaylistStore.inFolder`), so a
  list saved straight into Downloads does not take every file there. Windows tests rerun after it: all 0 failed.
- Android: a link that waited in a notification (no "display over other apps") makes the app ask once, when it is
  opened, to allow it (`PopUpAsk`, Allow / Not now; Not now holds until the app restarts).
- "Same file" with only the query changed is limited to the signing CDNs (fbcdn.net, cdninstagram.com): a first
  version for every link blocked `test_keys` / `test_integrity` (same path, the query picks the file).
- Tests (Windows, final build): jvmTest 234 / 0 (new `FacebookFileNameTest`); corehost folder_group 23, playlist_group
  30, playlist 37, home 41, add 29, addname 14, download 29, links 17, bridge 38 (its fake extension moved to 3.6.0),
  subtitles 27, media 31, drm 6, settings 87, keys 41, rowmenu 19, foldertype 8, siterules 22, automation 41,
  integrity 32, launch 33, all 0 failed. A56: background reading, playlist row, Check, one notice, Facebook stories /
  reels from Firefox. Not built: iOS (all SwiftUI changes need the Mac).

Mac check of 61b0ca1 (2026-11-19, nothing changed in the code):
- iOS: `scripts/ios-dev-build.command all` built with no Kotlin / Swift error, no fix needed. iPhone 17 simulator,
  `SIMCTL_CHILD_FETCHJET_NO_USAGE_PING=1`, the small playlist `PL59FEE129ADFF2B12` (11 videos, 3 unavailable), two
  videos downloaded at 360p (~8 MB) with English subtitles as a separate SRT file:
    - Add dialog: unavailable videos say "Not available" and are unticked; subtitle card with the videos' languages; after
      ticking one, the chips "Embed in video" / "Separate file · SRT" / "Separate file · VTT" (the last one scrolls, like
      the quality chips). Both `.en.srt` files were written next to the videos.
    - The list shows one "Test Playlist" row (LIST, "1 of 2 done · 8.3 MB", then "2 of 2 done"); it opens to its two
      videos, "Check for new videos", "Subtitles: English", "+ Add a subtitle language".
    - Check reopens the list: the two videos say "Already in this playlist" and cannot be ticked; Select none then Select
      all leaves them (and the "Downloaded before" ones) unticked.
    - Details of a playlist video: "Playlist" row with the list link; Copy put
      `https://www.youtube.com/playlist?list=PL59FEE129ADFF2B12` on the clipboard.
    - Settings > Downloads > Group downloads by folder: the list became folder rows (Sites, Music, Archives, Documents,
      Documents/Video ...) with "N files · M of N done · K failed", the playlist stayed its own row; a folder row opens to
      its downloads with Open folder / Delete all. Switched off again: plain rows.
    - Seen: the four videos of the c7a35ff test (queued before playlists were saved) sit in the same `Test Playlist`
      folder, so with grouping on there is a DIR "Test Playlist" row next to the LIST "Test Playlist" row. Old data
      only; a video added now joins the saved playlist.
    - Not checked: the iPad, "+ Add a subtitle language", "Embed in video", the Check of a list that really grew.
- macOS: `linkReleaseSharedMacosArm64` and `make-dmg.sh` built (`FetchJet-1.6.0-arm64.dmg`, 36 MB). corehost:
  folder_group 23, playlist_group 30, playlist 37, home 41, add 29, links 17, download 29, bridge 37 + 1 skip (Edge not
  installed), keys 41, integrity 32, all 0 failed. `test_playlist_group` / `test_playlist` first failed on the Mac
  (grown list, unavailable videos) because `desktopNative/tests/fakes/build/yt-dlp_linux` (used on the Mac too) was
  built on 2026-11-16; after `bash desktopNative/tests/fakes/build.sh` both passed. Rebuild the fake after a pull that
  changes `fake_ytdlp.cpp`.

Mac check of 832afcc (2026-11-19): iOS built with no error. iPhone 17: the playlist page's ⋯ menu starts with "Open
folder" (then Check for new videos, Delete playlist); it opened Files on `Media/Video/Test Playlist` (the 6 videos and 2
`.srt` files). The DIR "Test Playlist" row is still there with group by folder on: its 4 videos already carry a
`playlistId` (`pl_1795108020950_1798`, `pl_1795107967570_3144`, from the c7a35ff test) that is in no saved playlist,
and `adoptLoneVideos` skips every task whose `playlistId` is not empty. Only tasks with no `playlistId` would join.
Data like that exists only from 1.6.0 development builds (1.5.x saves no `playlistId`).
After 23cc218 (same day, iOS built with no error, relaunched with group by folder on): the DIR row is gone; the LIST
"Test Playlist" row says "6 of 6 done · 19.8 MB" and opens to all six videos (the two new ones and the four old ones).

iPad check of 8320cf1 (2026-11-19, iPad Pro 13" simulator, light theme, its own throw-away data, no code changed): the
same small playlist; Parisian Love + Cake at 360p, then Google Goggles with English "Embed in video" (~5 MB in all).
- Playlist row "Test Playlist · 2 of 2 done · 4.0 MB", page header (LIST, title, "N of N done", Check for new videos,
  "Subtitles: none", "+ Add a subtitle language"); ⋯ menu = Open folder / Check for new videos / Delete playlist; Open
  folder opened Files on `Media/Test Playlist`.
- Add a language: "Reading the languages…", then chips (English - CC, English, English (Original)); English gave
  "Adding subtitles… 1 of 2", then "Subtitles added to 1 of 2 finished videos" and "Subtitles: English"
  (`Cake.en.auto.srt`; Parisian Love has no English track).
- Check: the two downloaded videos say "Already in this playlist", are dimmed and do not tick; Select none / Select
  all leave them unticked.
- Add sheet at iPad width: quality chips and Embed / Separate file · SRT / Separate file · VTT on one line, no overflow.
  Embed in video: `Google Goggles.mp4` has a `mov_text` (eng) subtitle stream; the video joined the playlist (3 of 3).
- Group by folder: DIR rows Media / Documents with "N files · M of N done · K failed", the playlist its own row; a
  folder page has Open folder (Files opened on Documents), Resume all, Delete all (Delete all not tapped). Off again.
- Details of a playlist video: "Playlist" row with the list link; Copy put it on the clipboard.
- Landscape: the playlist page and the folder rows lay out the same (sidebar + list). The main pass ran in portrait,
  which on this iPad is also the wide layout with the sidebar.
- Seen, not changed (wording / behaviour for the owner): with no video ticked the start button reads "Inspect Video
  First" (disabled); one ticked video reads "Download 1 videos"; Check pre-ticks every video not downloaded yet,
  including the 162 MB "Real Time Search Event"; the "Subtitles added to 1 of 2" box stays after a later download until
  ✕; before a language is picked that box has an empty line above the chips; tapping All in the sidebar while a
  playlist page is open keeps the page open (the same on the iPhone tab bar).

After 5b4f1fd (2026-11-20, Windows + the A56), from a side-by-side check with EZOO 1.0.12 on this PC:
- Direct files: 32 connections by default above 16 MB, 16 above 2 MB (was 4 / 6 / 8); parts left by versions without
  a piece map still resume with the old split (`legacyChunkCount`). 32 MB from GitHub media: EZOO 3.3-3.5 s,
  FetchJet 7-12 s before, 4.7-4.9 s after (release core, from the first byte on disk). The 429 cap still lowers it
  per host (`test_hostlimit` 8 / 0).
- A site answering yt-dlp "HTTP Error 429": the inspect stops at once (no retry without cookies, no fallbacks), says
  "<site> is limiting requests ... try again in a few minutes" (15 languages) and the site is not asked again for 3
  minutes (`SiteBusy`, Android and iOS read the 429 from yt-dlp's output). On the A56 a Drive link had run yt-dlp
  about 18 times in 4 minutes and kept Google's limit on.
- Settings > Downloads "Videos that play everywhere" (off by default): off keeps AV1 / VP9 + the best sound (smaller),
  on keeps H.264 + AAC (EZOO's choice; plays on old TVs and players). One quality offered in two codecs keeps the
  chosen one (`playsWiderThan`, all four inspect parsers); the yt-dlp -f falls back to the other sound kind, the
  video's own sound, then best (`videoWithAudioSelector`). Not seen on screen; iOS needs the Mac.
- EZOO notes: its Add URL takes a YouTube link as a web page; its extension sends only the stream being played (one
  quality); FetchJet lists every quality. EZOO idle 54 MB RAM, FetchJet release 71 MB (not measured the same way).
- Tests (Windows, release core): jvmTest 240 / 0; corehost media 31, playlist 37, settings 87, add 29, links 17,
  drm 6, subtitles 27, download 29, robust 15, slowtail 4, hostlimit 8, integrity 32, all 0 failed.

Mac check of 25d1cc9 (2026-11-20, no code changed):
- iOS: `scripts/ios-dev-build.command all` built with no Kotlin / Swift error. iPhone 17 (dark) and iPad Pro 13" (light):
  Settings order General, Downloads, Speed limits, Files & folders, Browser extension, Devices, Network, Media tools,
  BitTorrent, Updates. Downloads = save location, connections (footer "Auto picks by file size (4–32)"), queue.
  Speed limits = presets + speed schedule (no idle limit on iOS: it is native-desktop only, `UserIdle`). Files &
  folders = sort by type with its footer under it, group by folder, extract, folders by site (iOS never had Recycle
  Bin / scan rows). Media tools: "Videos that play everywhere" at the bottom, toggles, `videoPlaysEverywhere=true`
  in behavior.properties, still on after a relaunch. Nothing duplicated or missing.
- YouTube `jNQXAC9IVRw` (iOS, default 240p row): switch off 476 KB in 7 s, AV1 (395) + Opus; switch on, the same
  file on the iPhone (Re-download) and a clean download on the iPad (476 KB, 5 s): still AV1 + Opus. Cause, checked
  with `yt-dlp -F` on the Mac: the ticked default row is format 229, an m3u8 (HLS) one; `DirectMediaResolver.resolve`
  rejects it and falls back to `bestvideo[height<=240][protocol^=http]+bestaudio[protocol^=http]`, which ignores the
  switch, so yt-dlp takes 395 + 251 both times.
- Seen with it: the quality list has 15 rows of "240p (Low - MP4) · SD" / "144p (Low - MP4) · SD" repeated. In all four
  inspect parsers (`MediaInspect.ios/jvm/android/nativeDesktop.kt`) the duplicate check compares
  `it.qualityLabel == label`, but the stored label is `withQualityTag(label)` (" · SD" added), so it never matches:
  HLS rows are never replaced by the https ones and `playsWiderThan` never runs. Not fixed here (shared code, every
  platform).
- macOS: `linkReleaseSharedMacosArm64` + `make-dmg.sh` built (36 MB). corehost settings 101, folder_group 23,
  idle_limit 13, scan 23, siterules 22, dns 14, all 0 failed.
- Not checked: the 429 stop (no site answered 429), Google Drive links, 32 connections on a real >16 MB file.

Mac check of 814c7d1 (2026-11-20, no code changed): iOS built with no error. iPhone 17, `jNQXAC9IVRw`: the list now
has 6 rows (240p MP4, 240p WEBM, 144p MP4, 144p WEBM, two audio), each height once. Switch off: the 240p MP4 row is
218 KB (395); download 476 KB in about 3 s, AV1 + Opus. Switch on: the same row is 314 KB (134); download 634 KB in
about 2 s, H.264 (Main) + AAC LC. On picks 134 + 140, not 133 + 140: 134 is the first H.264 240p in the list.
macOS: `linkReleaseSharedMacosArm64` + `make-dmg.sh` built; corehost download 29 / 0, slowtail 4 / 0 (server
`--throttle 3000`, 7 s for the file).

## 5. YouTube "made for kids" and faster qualities [x]

Ezoo: videos made for children download now; qualities show up faster.
To do: check a made-for-kids video on desktop, Android and iOS first (it may already work); measure our inspect
time against theirs on the same link before changing anything.
Checked 2026-11-20 with CoComelon "Wheels on the Bus" (`e_04ZrNroTo`, made for kids):
- [x] Desktop yt-dlp (the app's own, 2026.10.26, deno runtime): lists 75 formats up to 1080p, no error. Time of the
  yt-dlp call alone: 4.4 / 4.8 s (no cache dir), 4.8 s with the app's cache dir.
- [x] Android (emulator, the app's Python yt-dlp, link shared to the app): title and 32 qualities, 1080p MP4 first.
- [x] iOS (Mac session, iPhone 17 simulator, 814c7d1): title and 22 rows (4K WEBM first), English subtitles listed; 8-10 s from the link to the list on the first inspect after launch (includes the Python start).
- [ ] Ezoo's time for the same link: not measured (its app does not take a YouTube link; its extension in a browser
  would have to be driven).

## 6. Drag a finished file out of the list [ ]

Ezoo: drag a row to a folder, the desktop or a chat; moved on the same drive, copied to another (Shift moves,
Ctrl copies), as in Explorer.
To do: native desktop (Windows `SHDoDragDrop`, macOS / Linux their own), Slint host.

## 7. Fewer antivirus warnings [x]

Ezoo: no hidden `reg.exe` / system tools any more (the registry through the Win32 API), every release lists the
SHA-256 of its files.
Here: `PlatformUpdater.mingw.kt` starts a hidden `powershell.exe` for the update; releases have `.sig` files but
no `SHA256SUMS.txt`. To do: replace the hidden PowerShell with direct calls, add `SHA256SUMS.txt` to the release in
CI.

Done (2026-11-19, Windows):
- The Windows app starts no hidden `powershell.exe` or `cmd.exe` any more. The Store update check was already done by
  the host through WinRT (`host/win/store_win.cpp`); the PowerShell fallback in `PlatformUpdater.mingw.kt` is gone.
- The update installer is started directly (`msiexec.exe /i <msi> /passive /norestart FJ_RELAUNCH=1`, or the
  Setup.exe itself) at the very end of the app's shutdown (`HostHooks.afterShutdown`, run by `NativeApp.shutdown`),
  instead of a `fetchjet-update.cmd` with `tasklist` / `timeout`. The MSI starts FetchJet again after the install when
  `FJ_RELAUNCH=1` (`FjLaunchApp` after `InstallFinalize`, `product.wxs`).
- CI: the release job writes `SHA256SUMS.txt` (sha256sum format, every file except `.sig` and the notes) from the
  SHA-256 GitHub keeps for each uploaded file, nothing downloaded again. Dry run on v1.5.0: 14 files.
- Test: `test_update_install.py` 10 / 0 (fake installer `tests/fakes/fake_installer.cpp`, build with `build.cmd`):
  not started while the app runs, started after the shutdown, no cmd / powershell, no script left. A test MSI has
  `FjLaunchApp` after `InstallFinalize` with `FJ_RELAUNCH="1" AND NOT REMOVE~="ALL"`.
- Portable Windows (owner's request, same day): it used to offer the MSI download in the browser. Now it updates
  itself: picks `FetchJet-<v>-windows-portable.zip` (signed like the other files), unpacks it into
  `<folder>\.fetchjet-update`, moves each old file aside as `<name>.fjold` (Windows allows renaming a running exe / dll)
  and puts the new one in its place (all moves undone if one fails; `data\` is never touched), then starts the new
  FetchJet after the old one closed, with the same `--home` / `--downloads` / `--bridge-port` / `--no-*` options. The
  new copy waits for the old process (`FJ_AFTER_UPDATE_PID`), then deletes the `.fjold` files and the unpack folder
  (`finish_portable_update`, `host/win/platform_win.cpp`). Test `tests/host/test_portable_update.py` 12 / 0 (real
  app on a hidden desktop; trigger `--debug` + env `FJ_TEST_INSTALL_UPDATE=<zip>`). Portable 1.5.x copies still have
  the old code: they get this from the first update after it.
- Not run: a real in-app update from an installed MSI (the first release after this one). Left: `shutdown.exe` for
  "When downloads finish > Shut down" (a signed Windows tool, not hidden; replacing it cannot be tested without
  turning the PC off), system `curl` for FTPS, Defender `MpCmdRun` for the scan.

## 8. Checks [x]

- [x] "When downloads finish" (exit / sleep / shut down) waits for a merge: the merge keeps the task `DOWNLOADING` with a
  `postProcessLabel`, so it was already waiting (read in `MediaOwnEngine.reportPostProcess`). Found a gap: an archive
  being extracted or a file being scanned after its download is `COMPLETED` with a `postProcessLabel` laid on top by
  `allTasksFlow`, so the countdown could start (and the computer shut down) in the middle of an extraction.
  `WhenDone.onTasks` now counts a task with a `postProcessLabel` as running. jvmTest `WhenDoneTest` 5 / 0 (new case: a
  merge, then an extraction, then the countdown starts only when both are over). Not run: a real long ffmpeg merge.
- [x] A window brought back after minimize or hide / show is drawn whole: checked with a script (`PrintWindow` of the
  window, count of unpainted pixels): 0 after each, and a patch painted over the window by hand is repainted within
  8 s (the host repaints the whole window every 8 s and 0.3 / 1.5 s after a size change, `repaint_window` in
  `host/app.cpp`). The half-empty window the owner saw (2026-11-20) was never reproduced; the repaint heals it
  whatever the cause. Screen wake and a window covered by another one were not run.

## 9. Say when a video is DRM-protected [x]

Done 2026-11-19 (detect and say it only; nothing is downloaded or decrypted):
- App: `MediaDrm.kt` (HLS `EXT-X-KEY` / `EXT-X-SESSION-KEY` with a method other than NONE / AES-128 or a Widevine /
  PlayReady / FairPlay key format, also in the first rendition; DASH `ContentProtection`; yt-dlp's "DRM" error).
  `inspectMediaQualities` checks a `.mpd` link and `inspectHlsVariants` a `.m3u8` before listing; the Add dialog shows
  `sysDrmProtected` (15 languages) instead of "Unable to fetch video formats". jvmTest `MediaDrmTest` 4 / 0; corehost
  `test_drm.py` 4 / 0 (local DASH with ContentProtection and HLS with SAMPLE-AES reported as protected, an AES-128 HLS
  still lists its 2 qualities); the real Shaka Widevine `dash.mpd` reported as protected; test_add 29 / 0,
  test_subtitles 27 / 0, test_media 31 / 0 (one timing check failed once, then passed twice).
- Extension: `content.js` reports `drmSeen` when a page's video or audio has `mediaKeys` or fires `encrypted`
  (works from the isolated world, no MAIN-world script); `background.js` keeps it per tab (`DRM_KEY`, cleared with
  the tab) and then the list and the page button show only "This video is protected (DRM)" (`itemDrm` /
  `itemDrmSub`, 15 languages, `check.py` ok). `chrome-check.js` 24 / 0 with 4 new checks (Clear Key page gets media
  keys, list and button say protected, a normal page does not). Owner-verified 2026-11-19 on the real Bitmovin DRM
  page in Firefox for Android on the A56 (3.6.0 loaded as a temporary add-on with `web-ext run -t firefox-android`
  over adb): the list says the video is protected. Not checked: desktop Firefox, Safari.
- Android built and installed on the A56.


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

Mac check of `aff498a` (2026-11-19, iPhone 17 simulator, macOS native, Safari with Clear Key): all passed; three
defects found and fixed on Windows after it (not re-checked on the Mac yet):
- The DRM text showed twice in the Add dialog (card + the line above the buttons): the line now skips an error the
  torrent / media card already shows (SwiftUI `inlineError`, Compose `AddDialogActions`).
- An HLS / DASH link with no title got a made-up name ("0 video 1639.mp4", `resolveTargetFileName`): now the box
  stays empty and Start waits for a typed name (`addNeedsFileName` also for Media + `.m3u8` / `.mpd`), all UIs.
  `test_drm.py` 6 / 0.
- Safari on iOS: a DRM page kept "Open this page in FetchJet" in the list and "Download this video" on the button:
  the item has `kind: "drm"`, the button says "This video is protected (DRM)" and the in-app row is left out.

## 10. Uninstall removes the startup entry [x]

Done 2026-11-19 (Windows): `FetchJet.exe --remove-startup` (host `platform_win.cpp`, Win32 registry calls, no
`reg.exe`) deletes `HKCU\...\Run\com.michelle.raouf.fetch_jet` only when it points into the exe's own folder; the MSI
runs it (`FjRemoveStartup`, type 98, sequence 3499 just before `RemoveFiles`, condition
`REMOVE~="ALL" AND NOT UPGRADINGPRODUCTCODE`, so not on an update). At every start the host rewrites an old
`cmd /c start ...` value of its own folder to `"<exe>" --autostart=true`. Tested: `--remove-startup` left a value of
another folder and removed one of its own folder (registry restored after); test MSI built, `check-msi.ps1` all
passed, the custom action and sequence read from the table dump. Real MSI test on the owner's PC (2026-11-19): the test MSI installed and uninstalled cleanly (exit 0, folder gone) and
`FjRemoveStartup` ran (now deferred + impersonated; the exe uses `RegOpenCurrentUser`), but the value written from my
tool shell was not seen by the installer: a diagnostic build showed the action runs as the same user / SID / session and
reads the real Run key (13 values, ours not among them), so writes from my sandboxed shell do not reach the real
registry. The full cycle (turn autostart on in the installed app, uninstall, log in) still needs the owner. Not run:
the rewrite at start (needs the window), Setup.exe (the bundle runs the same MSI).


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

## 11. Languages of India [x]

Done 2026-11-19 (parts 1 and 2 of the owner's choice; new languages wait for more numbers):
- [x] `docs/index.html`: English / हिन्दी menu next to the release count; Hindi when the browser language is `hi`,
  `?lang=hi`, remembered in `localStorage` (`fj.lang`); every text of the page (titles, hints, store notices, file
  labels, buttons, errors, footer) in both; dates in `hi-IN`. Checked in the browser pane with the live GitHub list
  (7 releases): Windows tab, file labels and footer in Hindi. Live only after `docs/` reaches `master`.
- [x] Owner's request (same day): the whole download page in the app's 15 languages. Texts in `docs/i18n/<code>.js`
  (40 keys each, `window.FJ_PAGE_TEXT`; English stays in the page; a file loads only when that language is picked),
  menu with the 15 native names, the browser's first matching language picked by itself, `dir=rtl` for ar / fa (tab
  counts isolated so "7 Windows" reads right), dates in the page language, and "What's new" read from
  `release-notes/<version>/<lang>.md` on `master` (raw.githubusercontent.com), English GitHub text when missing.
  Checked: a node script (all 14 files have the 40 keys, same placeholders), a local server in the browser pane: all
  15 languages switched with no error, release notes of 1.5.0 shown in each, Arabic / Persian right-to-left.
- [x] `docs/privacy.html` in all 15 languages too (owner, same day: "everything in every language"): English in the
  page, the others in `docs/i18n/privacy-<code>.js` (`window.FJ_PRIVACY`, title + article), each marked as a translation
  where the English text wins on a difference; same menu, same language pick, RTL for ar / fa. Checked on a local
  server: all 15 switch with no error, each has the 4 sections, the statistics link and the env variable.
- [x] Slint app: Hindi drawn with `FetchJet Sans` + Windows fallback, offscreen with `shot.exe` (22 / 16 / 13 / 11 px,
  regular / semibold / bold, conjuncts क्ष त्र ज्ञ श्र द्ध, all matras, Devanagari digits, mixed with Latin and Arabic): all
  correct. WSL Ubuntu 26.04 has no Devanagari font at all (`fc-list :lang=hi` empty), so the .deb now recommends
  `fonts-noto-core` (has Noto Sans Devanagari; `build-linux.sh`). Checked in WSLg (Ubuntu 26.04, the Linux core
  and host built from this tree, throw-away `--home` in Hindi, `--no-auto-downloads --no-bridge --no-focus`): with no
  Devanagari font every Hindi text of the window is simply not drawn (empty sidebar, no boxes); with a Devanagari
  font present (Nirmala copied into `~/.local/share/fonts` for the test, removed after) the whole window is right
  (sidebar, search, empty-list text, conjuncts). So Linux needs the font on the system; the app uses it by itself.
  A first run without `--no-auto-downloads` fetched 13 MB of yt-dlp before it was stopped. Not checked: a real Linux desktop, macOS (system
  Devanagari fonts, fallback not looked at), the portable .tar.gz on a system without the font.
- [x] One app font per language on every platform (owner, same day: "every language its right font, the same on all
  systems"). `make_fonts.py` now builds **FetchJet Sans** (Noto Sans + Arabic + **Devanagari** + symbols) and
  **FetchJet Sans SC / JP / KR** (the same + Noto Sans SC / JP / KR cut to the characters of that language's provider,
  CJK punctuation, kana / jamo), 4 weights each, 16 files (550 KB base, 0.96 / 1.0 / 1.2 MB CJK per weight, ~15 MB in
  all); `--check` fails when a provider text has a character missing from its font (0 missing now). One shared choice:
  `core/i18n/start/AppFont.kt` (zh -> SC, ja -> JP, ko -> KR, else base). Desktop (Slint): `uiFontFor` uses it (zh no
  longer "Microsoft YaHei UI"; file names with Han still use YaHei on Windows through `nameHan`). Android: fonts in
  `androidUi/src/androidMain/res/font` (native res, Compose Resources removed from `androidUi` at the owner's request,
  the logo moved to `res/drawable`), `FetchJetTheme` puts the language family on all Material typography; APK
  79.4 -> 93.7 MB. iOS: fonts in `iosApp/iosApp/Fonts` + `UIAppFonts`, `Font.fj(size, weight)` (`FJFont.swift`, fixed
  size like the old `.system(size:)`), ~230 call sites switched, monospaced kept. Notices: `THIRD-PARTY.txt`,
  `THIRD_PARTY_NOTICES.md`; build command in `LINUX.md`.
  Checked: offscreen render of every family and weight; the Windows app on a hidden desktop (`deskrun`) in zh / hi /
  ja / ko: every text in its font, nothing downloaded; the Linux app in WSLg with **no** Hindi or Chinese system font:
  Hindi and Chinese both drawn (Chinese first looked empty because the old `libfjcore.so` was still next to the host;
  after copying the new one it is right). Android built and installed on the A56 (screen was locked, not seen). iOS not
  compiled (needs the Mac). Text typed by the user in a CJK script outside the app's own strings falls back to the
  system fonts (Linux needs them).
- [x] Mac check of `13cad03` (2026-11-19): iOS and macOS built; all 16 fonts bundled, PostScript names match, lldb shows
  the custom fonts loaded; iPhone 17 in en / ar / hi / zh / ja / ko: every text in its FetchJet Sans family, no missing
  characters, Arabic RTL; Add subtitles still works; macOS app in zh / hi uses the bundled fonts (without them Hindi
  falls back to the system face and the ⌕ ☀ ▾ symbols vanish). Fixed after it: `AppFontWeight.value` renamed
  `weightValue` (an exported `value` made Kotlin/Native rename `StateFlow.value` to `value_` and broke Swift);
  navigation-bar titles (large and inline) now use the app font (`FJFont.applyToNavigationBars`, set at start and
  before the language rebuild). Then `.toolbarBackground` removed from Details (SwiftUI's own bar appearance put the
  system title font back and leaked it to later screens) and the toolbar button labels (All settings, Cancel, Select
  Folder) set to `.fj` (SwiftUI draws them itself). Mac check of `59c22ae`: every navigation title and bar button in
  the app font in en / ar / zh / ko, live language switch follows, Details looks the same, picker cancel / select work.
- [x] Hindi stays as it is (owner, 2026-11-20: nobody here reads Hindi; if a user opens an issue saying a text should be different, we change it to what they say). New languages (bn, mr, te, ta) still wait for more usage numbers.


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
