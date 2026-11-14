## What's new in 1.4.1

**Downloads**
- **FTP and FTPS (new):** links that start with ftp://, ftps:// or ftpes:// now download like any direct link, in several parts and with resume when the server allows it. Put the login in the link, save it under Settings > Network > Site logins, or leave it out for anonymous servers. Good to know: FTP links do not go through the proxy; on the desktop apps FTP over TLS is fetched with the system's curl; FTP over TLS is not available on iPhone and iPad yet.
- **A file that changes on the server:** if the file is replaced on the server while it downloads, FetchJet notices. The download stops with a clear message instead of saving a mixed, broken file, and Resume downloads the new version.
- **No more waiting at the end:** the end of a download no longer waits for one slow connection. A connection that crawls at the end is replaced.
- **Checks before saving:** paste a checksum (SHA-256 or MD5) in the Add dialog, or let the server's own checksum header decide. A file that does not match is not saved. ZIP files are checked entry by entry, and a damaged part is downloaded again once.
- **Server limits are remembered:** a site that allows only a few connections is remembered for a day, so the next download does not start with the connections it refuses. After a 403, FetchJet can retry over IPv4 (Windows, Android and Linux).
- **Test the link:** a button in the Add dialog tells you if the link works, its size and whether it can resume, without downloading it.
- **Folder per kind of file:** the folder you pick for videos, music or archives is remembered for that kind only.
- **Play from the list:** a finished video or audio file now shows a ▶ button that opens it in your player.
- **No made-up file names:** a link with no file name shows an empty File name box, and Start waits until you type one.
- **Fits your screen:** the desktop window now opens inside the visible screen instead of partly under the taskbar.

**Torrents**
- **Details:** share ratio, uploaded amount, pieces and piece size, trackers with their state, peers, "check again", copy the magnet link, and "first and last pieces first" for previewing videos.
- **Add a tracker** to a running torrent, and **stop sharing at a ratio** you choose (Settings > BitTorrent).
- **Safer:** a torrent whose files would be written outside its own folder is refused.
- **Behind an HTTP proxy:** torrents that only have UDP trackers cannot find peers there; Settings > Network and the error message now say so.

**Desktop**
- **Keyboard:** arrows, Shift, Home / End, Page Up / Down, Enter (Alt+Enter opens or closes the details), Ctrl+R / P / O / E / N / F / A, Delete, Esc, and F1 for the list of shortcuts. They also work with Arabic, Persian and Russian keyboards, and copy / paste in every field does too.
- **Command line:** `/d` link, `/p` folder, `/f` file name, `/a` add paused without the Add dialog, `/n` add and start at once without a window, `/s` start everything that waits.
- **Speed limit only while you use the computer,** and **wake the computer for scheduled downloads** (Windows).
- **Smaller things:** the version in the window title, Esc closes the Add dialog, file-type badges are readable in the light theme, and the Send to device title is translated everywhere.

**Audio**
- **OGG:** choosing OGG for a YouTube (Opus) audio no longer re-encodes it. It is saved at once, at the original quality and size.
- **Compress audio:** it now offers the same formats as Convert (M4A, MP3, Opus, OGG, FLAC, WAV), keeps OGG when the file is OGG, and the estimated size is closer to the real one.

**Browser extension (3.4)**
- **Connection:** when the browser is not connected to the app yet, the extension says so at once ("Open FetchJet and tap Allow") instead of loading forever. After you tap Allow, the qualities appear by themselves. If the browser asks "Open FetchJet?", the tab now stays until you answer. Settings > Browser extension has "Unpair all".
- **The browser keeps a download until the app has it,** forms you posted stay in the browser, and private windows use their own cookies.
- **Links:** a file link is taken after the page's own click code has run, so sites that used to answer 403 now work. A PDF link opens in the browser unless the page marks it as a download. "Download all links" also finds links marked as downloads and links kept in data-* attributes, and Google Drive has an "Original file" row.
- **The video button:** it works with the keyboard (Tab, Enter), sticks to an edge when you drag it there, there is one button per group of stacked videos, and none on a video hidden under the page. You can hide it on a site, and Telegram Web explains why it cannot be downloaded.
- **The extension follows the app's theme:** its popup and settings page are light or dark like the app (System follows the device), and like the browser when the app is not running.

**Updates**
- **The update window** follows the app's language, and release notes are translated.
