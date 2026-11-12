## What's new in 1.4.1

- **FTP and FTPS downloads (new):** links that start with ftp://, ftps:// or ftpes:// now download like any direct link, in several parts and with resume when the server allows it. Put the login in the link, save it under Settings > Network > Site logins, or leave it out for anonymous servers. Good to know: FTP links do not go through the proxy; on the desktop apps FTP over TLS uses one connection; FTP over TLS is not available on iPhone and iPad yet.
- **A file that changes on the server:** if the file is replaced on the server while it downloads, FetchJet notices. The download stops with a clear message instead of saving a mixed, broken file, and Resume downloads the new version.
- **No more waiting at the end:** the end of a download no longer waits for one slow connection. A connection that crawls at the end is replaced.
- **Audio in OGG:** choosing OGG for a YouTube (Opus) audio no longer re-encodes it. It is saved at once, at the original quality and size.
- **Compress audio:** it now offers the same formats as Convert (M4A, MP3, Opus, OGG, FLAC, WAV), keeps OGG when the file is OGG, and the estimated size is closer to the real one.
- **Browser extension:** when the browser is not connected to the app yet, the extension says so at once ("Open FetchJet and tap Allow") instead of loading forever. After you tap Allow, the qualities appear by themselves, with no reload. Reloading a video page tries again after an error.
- **Settings > Browser extension:** "Unpair all" disconnects every paired browser; each one asks for Allow again next time.
- **Update window:** the release notes follow the app's language when you change it.
