## What's new in 2.0.0

**A smarter Add dialog**
- **FetchJet picks the engine from the link:** a file, a magnet or a known video site goes to Direct, Torrent or Media by itself. It checks the link once first ("Checking the link…"), and the Direct / Media choice shows only for a page with a video, or a link that could not be checked.
- **No more check for every letter you type:** the check starts when you stop typing (at once for a pasted link). Changing the link cancels the old reading, so the dialog no longer stays stuck on "Finding the name…", for example with a magnet that never finds peers.

**Find files on this page**
- **New:** paste a page link and use "Find files on this page". FetchJet lists the page's files: images, links and addresses written as plain text. For links with no file name it asks the server for the real name and size. If you ask, it also reads the pages that page links to, one or two levels down.
- **Results while it reads:** files appear as the page is read, and "Stop reading" keeps what was found.
- **Choose what to download:** file types are buttons that hide or show those files, and the files you hide are not downloaded. Each row shows its type (ZIP, MP3, PNG…).
- **Saved as a group:** the files go to a folder and a group named after the page. Later, "Check for new files" marks what you already have and locks it ("N downloaded before").
- **GitHub release pages** are read in a single request.
- **Pages that cannot be read:** pages the site refused (a check or a block) are counted. Pages that build their links with JavaScript are named, with a hint to open them in your browser and use the extension's "Download all links".

**Playlists and subtitles**
- **Playlists:** videos you downloaded before are marked and counted ("N downloaded before"), and a playlist saved as a group can be checked for new videos.
- **Subtitles:** choose "Embed in video" or "Separate file", then SRT or VTT. Languages are asked for one at a time, and one the site refuses for a moment is asked again. You can also add subtitles to a finished video from its details or its row menu.
- **Send to device keeps the subtitles:** a finished video sent to another device still offers "Add subtitles" there, knows which languages are already in the file, and the subtitle files next to it travel with it.

**Videos**
- **Google Drive:** any Drive file link downloads directly, and a file whose owner turned off downloading says so.
- **Protected videos:** a video protected by DRM is named as such before the list of qualities, instead of failing later.
- **Videos that play everywhere:** the switch (now in Settings > Media tools, and on Android too) chooses H.264 + AAC files that every TV and player opens, or smaller AV1 / VP9 files.

**Settings**
- **Reorganized:** Downloads (location, connections, queue), a new Speed limits page (presets, idle limit, schedule) and a new Files & folders page (sort by type, group by folder, extract, Recycle Bin, scan, folders by site).

**Speed**
- Large files use up to 32 connections by default.
- A request that gets no data for 20 seconds is dropped and asked again, so one stuck connection no longer stalls a download for a minute.
- On short, fast transfers a slow connection is replaced sooner.
- macOS allows 64 connections per site, so a second download from the same site no longer waits.

**Smaller things**
- "When downloads finish" also waits for an archive being extracted or a file being scanned.
- A torrent with a long name no longer stretches its files card, and its file rows show the file name first.
- Links and folder paths keep their direction in Arabic and Persian.
- Android: a playlist read in the background no longer crashes the app.
- The desktop window repaints fully after it is shown or resized, with no unpainted patches.
- The macOS app is built to run on macOS 12 and newer.
- **Android: no broken list after a reinstall or a new phone:** the downloads list is no longer restored from a backup, because its links are locked to the phone and could not be read there. The files you downloaded stay in your folder.
- **Clearer subtitles message:** when the link of an old download cannot be read, "Add subtitles" says it could not read the subtitles instead of saying the video has none.
- **Faster queue:** the next waiting download starts the moment a slot frees up (50 small files: 16 s to under 1 s), and joining a file's parts is faster.
- **A More page** replaces Settings in the navigation: version and update, Settings, FetchJet on every device, Share FetchJet, and Community and help.
- **Same file name:** two downloads that end on the same name in one folder now save as "name (1).ext", and a download can no longer vanish from a long queue.
- **One font per language** on every platform (the app is about 7 MB larger).
- **Video sites remembered:** a site where a Media check found qualities is treated as a video site next time, and the sizes of HLS qualities are estimated and shown with ~.
- **The Add dialog starts empty** after you close it.
- **Windows:** no console windows flash on screen, every release has SHA256SUMS.txt, and the portable version updates itself.
- **Also from 1.5.1:** the Microsoft Store update check, Arabic file names in ZIP files made on a Mac, and torrents on iOS through a proxy.
