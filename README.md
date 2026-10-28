# FetchJet

**FetchJet** is a feature-rich, high-performance cross-platform media downloader and download
manager built using **Kotlin Multiplatform** and **Compose Multiplatform**.

## ✨ Features

- **Multi-Engine Support**:
  - **Media Stream Extraction & Downloading** (powered by `yt-dlp` integration for YouTube, Vimeo,
    and hundreds of other supported video/audio platforms).
  - **Torrent Downloading** (built-in bencode parsing and torrent stream engine).
  - **HTTP Chunked Downloader** (optimized multi-threaded HTTP file downloads with rate limiting).
- **Queue & Manager**: Advanced queue manager to pause, resume, prioritize, and track download
  tasks.
- **Cross-Platform**: Native apps for **Android**, **Desktop (JVM / Windows / macOS / Linux)**, and
  **iOS**, sharing a unified, modern user interface powered by Compose Multiplatform.
- **Modern UI**: Crafted with Material Design principles using Compose Multiplatform, featuring
  dark/light themes, download speed limits, directory pickers, and comprehensive dashboard metrics.

---

## 🛠️ Tech Stack

- **Kotlin Multiplatform (KMP)**: Shared business logic, networking, and data layers.
- **Compose Multiplatform**: Declarative UI shared across Android, Desktop, and iOS.
- **Coroutines & Flow**: Asynchronous programming and reactive state flows.
- **Ktor**: HTTP client and networking stack.

---

## 📂 Project Structure

- `androidApp/`: Android application module and entry point.
- `desktopApp/`: Desktop (JVM) application module and entry point.
- `iosApp/`: iOS application module and SwiftUI/Compose entry point.
- `sharedLogic/`: Shared Kotlin Multiplatform core logic (download engines, yt-dlp integration,
  parsers, repositories, models).
- `sharedUI/`: Shared Compose Multiplatform UI components, screens, modals, and themes.

---

## 🚀 Building and Running

Use Gradle tasks or your IDE run configurations:

### Android App

```bash
./gradlew :androidApp:assembleDebug
```

### Desktop App

```bash
# Standard run
./gradlew :desktopApp:run

# Hot reload
./gradlew :desktopApp:hotRun --auto
```

### iOS App

Open the `iosApp/` directory in Xcode and run the project on your iOS simulator or device.

---

## 📄 License

This project is free software, licensed under the GNU General Public License v3.0 or later (see `LICENSE`). It bundles and links GPL-3.0 code (YTDLnis, youtubedl-android); the full list of third-party components and their licenses is in `THIRD_PARTY_NOTICES.md`.
