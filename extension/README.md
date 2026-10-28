# FetchJet browser extension

One code base for every browser:

| Browser                                                 | Manifest                | Install                                                                                                                                                                                         |
|---------------------------------------------------------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Chrome, Edge, Brave, Opera, Vivaldi, Arc (any Chromium) | `manifest.json`         | `chrome://extensions` (Edge: `edge://extensions`, Brave: `brave://extensions`, Opera: `opera://extensions`) → Developer mode → *Load unpacked* → this folder, or drop `fetchjet-chrome-<v>.zip` |
| Firefox (desktop; Android ≥ 142 with the same file)     | `manifest.firefox.json` | signed `fetchjet-firefox-<v>.xpi` (see below)                                                                                                                                                   |

The Chromium build is one package for all Chromium browsers: Edge, Brave, Opera and Vivaldi run
Chrome extensions unchanged and the Chrome Web Store listing works in all of them.

## What it does

| Feature                                                                      | How                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Takes over browser downloads                                                 | `downloads.onCreated` → cancels the browser download, sends the final URL, the file name the browser resolved, the referer and the site cookies to FetchJet. A notification offers *Download in browser instead*.                                                                                                                                                                                                                                                                 |
| Intercepts clicks on file links                                              | Content script; the extension list and excluded hosts are configurable. Hold **Alt** (configurable) while clicking to let the browser download.                                                                                                                                                                                                                                                                                                                                   |
| Detects video / audio on any page                                            | `webRequest.onHeadersReceived` sniffs MP4/WebM/MP3/HLS/DASH responses; the popup lists them with sizes and a badge shows the count.                                                                                                                                                                                                                                                                                                                                               |
| Video sites (YouTube, Facebook, Instagram, TikTok, X, Vimeo, Dailymotion, …) | When a page plays a video the extension asks the app (`GET /inspect`, yt-dlp) for the qualities and lists them in the popup and in a **Download this video** button drawn over every video on the page (also in frames and shadow DOM; draggable, ✕ hides it). Other videos on the page get their own button and are looked up when clicked; in feeds the post link next to the video is used. Picking one starts the download at once (`POST /add` with `formatId`, no dialog). |
| YouTube without yt-dlp (`yt-core.js`)                                        | The player's own `googlevideo.com/videoplayback` streams are captured (`webRequest.onBeforeRequest`), piece parameters removed, ads filtered by duration. Progressive formats (360p/720p with sound), video-only and audio-only streams are offered as plain files when the app's media extractor is not installed.                                                                                                                                                               |
| "Save as" never shows                                                        | On Chromium the decision is taken in `downloads.onDeterminingFilename`, before the browser asks where to save. If the app cannot be reached the download is handed back to the browser once.                                                                                                                                                                                                                                                                                      |
| Download all links / selected links                                          | Context menu, popup button or **Alt+Shift+L**; queued in one batch, no dialog per link.                                                                                                                                                                                                                                                                                                                                                                                           |
| Context menus                                                                | link, image, video/audio element, selection, page, toolbar icon.                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Keyboard shortcuts                                                           | Alt+Shift+D page, Alt+Shift+L all links, Alt+Shift+X toggle capture.                                                                                                                                                                                                                                                                                                                                                                                                              |
| Launches FetchJet when closed                                                | native messaging host → `fetchjet://` protocol fallback, then waits for the app and re-sends.                                                                                                                                                                                                                                                                                                                                                                                     |
| Settings page                                                                | master switch, per-feature toggles, min size, extensions, excluded hosts, hold key, cookies on/off.                                                                                                                                                                                                                                                                                                                                                                               |

## Talking to the app

The desktop app listens on `http://127.0.0.1:28450` (loopback only) and accepts requests only from
extension origins carrying the `X-FetchJet` header:

```
GET  /ping                      -> PONG
GET  /config                    -> {"app":"FetchJet","version":..,"bridge":2,"mediaExtractor":..,"torrent":..}
POST /add    {url, cookie, referer, userAgent, fileName}   -> opens the Add dialog pre-filled
POST /add    {..., formatId}  -> starts that yt-dlp format now (409 if the inspection expired)
POST /add    {..., direct:true} -> starts a plain file now, default folder, no dialog
POST /links  [ {url, cookie, referer, userAgent, fileName}, ... ] -> queues all directly
GET  /download?url=&cookie=     (legacy, still supported)
GET  /inspect?url=              -> yt-dlp qualities as JSON
```

Cookies/Referer/User-Agent are stored on the task (`DownloadTask.requestHeaders`) and replayed on
every request of that download, so files behind a login or hot-link protection work.

## Build

```
./build.sh        # Linux/macOS   → build/extension/fetchjet-chrome-<v>.zip, fetchjet-firefox-<v>.xpi
.\build.ps1       # Windows
```

### Firefox

* Temporary: `about:debugging#/runtime/this-firefox` → *Load Temporary Add-on* →
  `manifest.firefox.json`
  renamed to `manifest.json` (or use the `build/extension/firefox` folder).
* Permanent: Firefox only installs **signed** XPIs. Sign through AMO (free, unlisted channel):
  `npx web-ext sign --channel unlisted --source-dir build/extension/firefox --api-key … --api-secret …`
  The signed `.xpi` can then be attached to GitHub releases and installed by double-click.
* Firefox does not grant site access at install time; the popup shows an *Allow access to all sites*
  button the first time.

## Not (yet) ported from Ezoo

* Playlist download (`/playlist`): FetchJet has no playlist queue yet; the page URL is sent as one
  item.
* Merging a captured video-only + audio-only pair inside the app: needs an ffmpeg merge path in
  `MediaStreamDownloader`; today yt-dlp does the merging, captured streams are single files.

## Permissions explained

`downloads` capture, `cookies` pass the session to the app, `webRequest`+`webNavigation` media
detection per tab, `scripting` inject the link collector into pages opened before install,
`nativeMessaging` start the app, `<all_urls>` the above on every site. No data leaves the computer:
the only network destination is `127.0.0.1:28450`.
