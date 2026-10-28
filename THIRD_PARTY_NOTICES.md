# Third-party software in FetchJet

FetchJet is licensed under the GNU GPL v3.0 or later (see `LICENSE`). The Android build is a
derivative work of GPL-3.0 code, which is why the whole project carries the GPL.

| Component | Where | License | Notes |
|---|---|---|---|
| YTDLnis (Denis Çerri) | `sharedLogic/src/androidMain/kotlin/com/deniscerri/**` | GPL-3.0 | Copied and modified (runtime/updater helpers); each file carries an origin header. |
| youtubedl-android (JunkFood02) | Gradle dependency `io.github.junkfood02.youtubedl-android:*` | GPL-3.0 | Android Python/yt-dlp runtime. |
| ytdlnis_packages | downloaded at runtime on Android | GPL-3.0 | Python packages for the Android runtime. |
| yt-dlp | bundled (Android/iOS) and downloaded at runtime (desktop) | Unlicense | https://github.com/yt-dlp/yt-dlp |
| yt-dlp-ejs / yt-dlp-apple-webkit-jsi | downloaded at runtime (iOS plugin dir) | Unlicense / see upstream | YouTube JS challenge solving. |
| FFmpeg (ffbinaries builds on desktop; kewlbear FFmpeg-iOS-Lame on iOS) | downloaded at runtime / linked | GPL-2.0-or-later / LGPL | https://ffmpeg.org |
| libtorrent-rasterbar + libtorrent4j | `jvmAndroidMain` (desktop & Android) | BSD-3-Clause / MIT | https://github.com/aldenml/libtorrent4j |
| LibTorrent-Swift (XITRIX) | iOS `LibTorrent.xcframework` | no LICENSE file upstream (iTorrent is GPL-3.0) | Confirm with the author before public release. |
| Python (BeeWare Python-Apple-support) | iOS embedded runtime | PSF-2.0 | https://github.com/beeware/Python-Apple-support |
| PythonKit | iOS | Apache-2.0 | |
| curl_cffi (tfourj/SwiftCurlCffi-iOS payload) | iOS | MIT | browser impersonation for yt-dlp |
| OpenSSL (krzyzanowskim) | iOS | Apache-2.0 | |
| Ktor, Okio, OkHttp, kotlinx.coroutines, Compose Multiplatform, Gson, AndroidX | all platforms | Apache-2.0 | |
| Mozilla CA bundle (`cacert.pem`) | iOS | MPL-2.0 | |

The browser extension (`extension/`) is original code (GPL-3.0-or-later like the rest); its
YouTube stream handling was designed after the Ezoo Download extension's approach.
