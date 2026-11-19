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

## 5. YouTube "made for kids" and faster qualities [ ]

Ezoo: videos made for children download now; qualities show up faster.
To do: check a made-for-kids video on desktop, Android and iOS first (it may already work); measure our inspect
time against theirs on the same link before changing anything.

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

## 8. Checks [ ]

- Shut down / sleep "when downloads finish" must wait for a merge in progress (merge runs while the status is still
  `DOWNLOADING`, so it should already wait: test it with a long ffmpeg merge).
- A window covered by another one, brought back from the tray or after the screen wakes up is drawn whole (Ezoo fixed
  half-empty windows; we fixed the white band at start only).

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

## 11. Languages of India [ ]

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
- [ ] Hindi read by someone who knows it; new languages (bn, mr, te, ta).


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
