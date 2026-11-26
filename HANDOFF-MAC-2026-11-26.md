# Mac check, 2026-11-26 (branch release-v2.1.0)

Windows builds and tests passed. These changes were written on Windows and need a Mac build and test.
Use a throw-away `--home` for the macOS app, set `FETCHJET_NO_USAGE_PING=1`. The simulator is enough for iOS.

## Build

1. `git pull`, `git submodule update --init --recursive`.
2. `./gradlew :sharedLogic:compileKotlinIosSimulatorArm64`, then `scripts/ios-dev-build.command all`.
3. `./gradlew :desktopNativeCore:linkReleaseSharedMacosArm64 -Pfetchjet.nativeDesktop=true`,
   then `desktopNative/packaging/mac/make-dmg.sh`.

## iOS (iPhone 17 + iPad simulators)

| Area | Files | What to check |
|---|---|---|
| Resume button | `TaskRowView.swift`, `TaskDetailsScreen.swift` | A paused / queued row shows a download arrow (`arrow.down.to.line`) in the circle, not ▶. A finished video still shows ▶. Details: Resume / Start now buttons have the arrow. |
| Clip time | `AddDownloadSheet.swift`, `IosAppHost.kt` (`clipRange`) | Add dialog of a YouTube video: From `00:00:12.5` To `00:00:17` downloads 12.5 to 17 (not the whole video, not from 12). End past the video gives the error; end at the video end is fine. |
| Audio / video names | `AddMediaSection.swift`, `TaskRowView.swift`, `VideoCutSheet.swift` (call `MediaExtensions.shared`) | An mp3 row: audio kind, audio cut opens. An mp4 row: video. |
| In-app page | `FJPageBrowser.swift` | Open `fetchjet://browse?url=<a page with a video>`, play it, close the sheet: the sound stops. Open again: works. |
| yt-dlp args | `MediaInspect.ios.kt`, `MediaYtDlpDownload.ios.kt`, `MediaStreamExtractorPlatform.ios.kt` (`--` before the link, `%` escaped in the output name) | YouTube `jNQXAC9IVRw` inspect and download; SoundCloud track; a video whose title has `%` (or rename the task) saves with the right name. |
| Bridge / pairing | `BridgePairing.kt` (shared) | Safari extension still sends to the app. |
| Archives | `ExternalListing.ios.kt` | Extract a zip on iOS still works. |

| Delete original | `Dialogs/FJDialogs.swift`, `Tasks/VideoCutSheet.swift`, `Tasks/FJTrimView.swift`, `Tasks/AudioCompressSheet.swift` | A finished video with no clips: Cut shows "Delete the original after cutting"; tick it, cut: the original is gone and the clip is a normal row (Cut / Subtitles offered). With one clip already: the box is not shown. Compress audio the same (the row keeps its id, the file is the compressed one). Remove a file with 2 clips: the dialog says it also removes 2, and they go. |
| Player build | `Core/FJPlayer.swift`, `Core/FJPlayerSubtitles.swift` (new look, written on Windows) | Builds with no error or new warning: `MediaPlayer` import, `asset.load(.commonMetadata)` / `AVMetadataItem.load(.dataValue)`, `BehaviorSettings.shared.playerAutoplay.value as? KotlinBoolean`, `setPlayerAutoplay(on:)`, `accessibilityAdjustableAction`, `dismantleUIView`. |
| Player controls | `Core/FJPlayer.swift` | Play a video: thin bar with times at its ends (thicker while dragging, seeks on release), one row with `1x`, Autoplay switch + label, `CC` on the other end; prev / pause / next in the middle, pause in a dark round button; dark gradients top and bottom. Everything fades after 3 s, a tap brings it back; it does not fade while dragging the bar. Pause: after 3 s only a big ▶ stays in the middle, tapping it plays. |
| Player autoplay | `Core/FJPlayer.swift` | Queue of 2 videos (a folder / Play all). Autoplay on: at the end the next one starts by itself. Off: the next one loads paused with the big ▶. The choice stays after closing and reopening the player (shared `playerAutoplay` setting). Next / previous / a queue row also follow the setting. |
| Player gestures | `Core/FJPlayer.swift` (`FJPlayerSurface.Coordinator.pan`, hidden `MPVolumeView`) | On an iPhone build if possible (the simulator has no system volume, so the volume slider may do nothing there). Drag up / down on the right half: box with the speaker icon, bar and %, the system volume HUD does NOT show, the volume really changes. Left half: sun icon, the screen brightness changes; close the player (and send the app to the background) -> brightness goes back. A horizontal drag does nothing; tap, double tap (10 s skip) and hold (2x) still work; a drag starting at the very top / bottom edge is left to the system. |
| Player audio | `Core/FJPlayer.swift` | An mp3 with embedded cover art shows the art, one without shows the purple note placeholder; title, prev / play / next under it and the same bottom bar (no CC). Controls never fade for audio. iPhone landscape: cover on the side, nothing cut off. |
| Player layout | `Core/FJPlayer.swift`, `Core/FJPlayerSubtitles.swift` | iPhone 17 portrait + landscape, iPad Pro portrait + landscape, Arabic (RTL): the seek bar, times and prev / play / next stay left-to-right, the bottom row mirrors. A subtitle (pick one with `CC`) sits above the bar while the controls show and drops down when they fade. `CC` is filled white while a subtitle is on. A hidden file: the black privacy cover in the app switcher is unchanged, and the player still closes on lock. |

## macOS native app

| Area | What to check |
|---|---|
| Resume button (Slint `TaskRow.slint`, `TaskList.slint`) | Paused row: arrow in the circle; Resume all bar button shows ⬇. |
| Private temp folder (`PrivateFolder.macos.kt`, `PathResolver.nativeDesktop.kt`) | Downloads still work; the temp folder is created with mode 0700 (`stat -f %Lp`). |
| Update check before install (`UpdateFileCheck.kt`, `PlatformUpdater.macos.kt`) | In-app update from an older local build (see CLAUDE.md "Testing an in-app update on the Mac") still installs; the `.sig` next to the update file is removed after. |
| 7-Zip listing first (`ExternalArchiver.nativeDesktop.kt`) | corehost `test_archives7z.py` and `test_extract.py` with 7-Zip from Homebrew (`7zz`): the `-slt` listing parser on 7zz output. |
| Host (C++) | `player_mpv.cpp` (no bare ffmpeg fallback, threads waited at shutdown): trim timeline thumbnails and wave still load; quit while a long wave is computing: no crash. |
| Bridge (`BridgeServer.kt`, `HttpListener.kt`) | corehost `test_bridge.py`, `test_automation.py` (automation now turned on with `fetchjet automation on`, the API cannot turn it on), `test_unpair.py`, `test_unsafe_names.py`. |
| Clip time (`AddMediaPresenter.kt`, `VideoCutPresenter.kt`) | corehost `test_add.py`, `test_cut.py`; in the window: clip picker opens at 12 for `00:00:12.500`. |

Run the whole corehost set as on 2026-11-18 (servers by hand, `FJ_TEST_RELEASE=1`).

## Not for the Mac

Android and the extension were tested on Windows. Linux: run `test_archives7z.py` once with p7zip / 7zz installed in WSL.
