## What's new in 2.1.1

**Media player**
- **New:** play finished audio and video inside FetchJet on every platform: a play button on the row, Play for a whole folder, playlist or group, a queue with previous / next and the list, speeds, and hold for 2x. The last position is kept.
- **Compact controls:** the controls sit over the video and fade after 3 seconds. A thin seek bar, one small row with speed, an **Autoplay** switch and **CC**. When a video is paused, or the next one waits with Autoplay off, one large ▶ sits in the middle.
- **Autoplay:** on, the next item starts by itself; off, it loads paused.
- **Subtitles:** the CC menu shows subtitles inside the video, subtitle files next to it (your language first) and "Open subtitle file…" (SRT, VTT, ASS). The choice is kept per download. Subtitles sit at the bottom and move above the controls when they show. CC is filled while a subtitle is on.
- **Phones and tablets:** swipe up or down on the right half for volume and on the left half for brightness (the player only).
- **Computers:** a volume slider up to 200% with a mute button, and full screen. On Windows, macOS and Linux the player library downloads only the first time you play, with its progress shown, and the file opens by itself when it is ready.

**Cut, extract and compress**
- **Cut video:** from the row menu or the details, by time or on a timeline. The timeline works like a video editor: a fixed time scale, drag or scroll to move, zoom from a few seconds to the whole video, handles that scroll at the edges, thumbnails and a thin scroll bar. The clip is a new file listed under its video.
- **Cut audio:** audio files can be cut the same way, on a sound-wave timeline. MP3 parts are re-encoded at the bitrate of the source so no sound is lost and the file does not grow.
- **Extract audio:** saves the sound track of a video as its own file without re-encoding.
- **Delete the original:** when nothing else was made from a file yet, Cut and Compress offer "Delete the original". The new file then becomes the main download: you can add subtitles (their timing follows the clip), cut it or compress it again. Removing a download also removes the clips and copies made from it, and the confirmation says how many.

**PDF reader**
- **New:** open PDF files inside FetchJet: pages one under the other, page number and "Go to page", fit width, zoom, and the last page you read is kept. Android and iOS use the system PDF engine; Windows, macOS and Linux download a small PDF library the first time. A setting chooses the in-app reader, the system app, or asking.

**Hidden downloads**
- **New:** hide a download, a playlist or a folder group behind a PIN of 4 to 6 digits or a pattern (More > Hidden). Hidden items leave the list, the gallery and the file manager, and their files move into a private folder of the app. Hiding is instant and shows one progress line while the files move. Unhiding brings the files back to their folder.
- **Audio and video are encrypted:** the audio and video you hide are encrypted with a key tied to the device, their file names are scrambled, and they are decrypted only while you play them in the app's player. Other file types are only moved out of sight. A notice explains this the first time.
- **Leaving the app covers it:** it asks for your fingerprint, Face ID, Windows Hello or PIN when you come back, and a hidden video that was playing closes.
- **New downloads start hidden** when you start them from the Hidden screen (the + button). Hidden downloads show no notification.
- **Forgot the PIN:** confirm with the lock of your phone or computer and set a new PIN. Your hidden files stay. Not available on Linux.
- **Recovery code:** one code for the whole vault, shown once. With it you can reopen hidden audio and video if the device key is lost, for example after restoring to a new phone. Deleting the app or clearing its data deletes the hidden files, so unhide them before you uninstall.

**Backup and restore**
- **Back up downloads** saves the list of your downloads, folders and settings to one file, with no passwords or cookies. **Restore from backup** brings it back and merges it with what is there. The downloaded files are not inside the backup: finished downloads reconnect to their files, and unfinished ones come back paused.

**Browser and sign-ins**
- **In-app browser** (More > Browser, Android and iOS): an address bar, back / forward / reload; a file or video you open there goes to the Add dialog with the page's sign-in.
- **Sign in with the browser** (Settings > Network > Site logins): sign in to a site once and its downloads use that session. Removing the site login also clears the browser's cookies for it.

**Downloads**
- **Connections chosen by itself:** FetchJet starts with 4 connections and adds more while the speed still grows, and remembers what worked for each site.
- **Clip times are exact:** a clip now starts and ends exactly at From and To, and From / To accept parts of a second (12.500). Before, a clip could start late (From 12 started at the end of 12) or early (11.5 started at 11), because the cut moved to the nearest key frame. This happened on every platform since clips were added.
- **Resume shows a download arrow** (⬇) instead of ▶, so it no longer looks like playing a video. ▶ is now only for playing a finished file.
- **Udemy whole course** also brings the course files: PDF, e-book, slide and code lectures, and the resources attached to lectures, each right after its lecture.
- **Details** show the Source link again; a link that carries a sign-in or a token shows a note instead.
- **New app icon** on every platform.

**Security and safety**
- Removing an unfinished or failed download with "also delete files" no longer deletes an older file of yours with the same name. Removing a file restored from a backup checks it is really that download's file first.
- A download that could not be moved into place no longer leaves a cut-off file under the final name, and a wrong size at the end no longer throws away the parts already downloaded.
- 7z and RAR archives are checked before they are extracted (no links, no paths that leave the folder, size limits), and the archive password is no longer visible to other programs.
- The local connection to the browser extension always needs the pairing; the automation API can only be turned on by you, not through the API.
- Settings that cannot be decrypted for a moment no longer replace the extension pairing.

**Android**
- **Only the clip works again:** since 1.5.0, "Only the clip" failed on Android with "ffmpeg exited with code 8" for every site, because the smaller ffmpeg built for the app had no network access. ffmpeg now reads the part of the video from the site again, and if that ever fails the app downloads the whole video and cuts it exactly instead of failing.
- **Joining video parts:** since 1.5.0 some sites that send a video in parts (HLS) failed with "dimensions not set". Fixed.
- A clip cut exactly on the phone is no longer much bigger than its part of the video (a 5 second clip of a small video was 1.6 MB).
- In the player, a swipe at the side edges changes volume or brightness instead of going Back, and Back closes an open menu before the player.
- Links opened from other apps or web pages always show the Add dialog first, a torrent file opened from another app is checked first, and the battery question is asked only once.

**Windows, macOS and Linux**
- The player and PDF libraries load only from FetchJet's own folders, never from the current folder or PATH.
- macOS and Linux: updates and temporary files use a folder only you can open, and an update is checked again right before it is installed.
- The Cut audio choice shows both options at full width.

**iOS**
- **Only the clip:** a clip could be saved as a tiny file that did not play. The app now notices it and downloads the whole video and cuts it exactly.
- The in-app browser stops its sound and frees its memory when you close it.

**Browser extension 3.8.0**
- **Open in FetchJet:** an arrow next to the page download button and an "Open in FetchJet" row send the page link to the app at once, and you pick the quality there. This also works on pages where the qualities cannot be listed, for example a Udemy lecture.
- **Open FetchJet directly:** a new option hands links to the app without the local bridge.
- **Canva:** each playing video gets the download button, named after its page; its video and sound are saved as one file.
- **YouTube home page:** the button on a video that plays when you point at it no longer blinks when you move onto it, and it always belongs to the video under the mouse. Before, it disappeared and came back, and could show the first video's qualities on another video.
- **Udemy:** a lecture stream is named after the lecture instead of a random code.
- **Safer:** only the extension's own pages can change its settings, a page's cookies are sent only for that same site, and Udemy course reading works only on udemy.com.

**Smaller things**
- A long press on a download in the Hidden screen opens a menu. All lists now share one row menu, in sections, with Main first.
- Android: if a file was downloaded by another install of FetchJet and cannot be read, FetchJet now opens the "Allow access to all files" page instead of failing silently.
- The Add dialog no longer has the "download as hidden" switch.
