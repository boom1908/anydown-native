<p align="center">
  <img src="anydown.png" alt="Anydown Logo" width="140" height="140" style="border-radius: 28px;" />
</p>

<h1 align="center">ANYDOWN</h1>

<p align="center">
  <strong>YouTube, Spotify, Instagram and 1,000+ sites — saved your way.</strong>
</p>

<p align="center">
  <a href="https://github.com/boom1908/anydown-native/releases/latest"><img src="https://img.shields.io/badge/Release-v3.0.0-F9E2AF?style=for-the-badge&logo=android&logoColor=black" alt="Release v3.0.0" /></a>
  <a href="https://anydown.vercel.app"><img src="https://img.shields.io/badge/Web-anydown.vercel.app-89B4FA?style=for-the-badge&logo=vercel&logoColor=black" alt="Website" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-A6E3A1?style=for-the-badge" alt="License" /></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-FAB387?style=for-the-badge&logo=android&logoColor=black" alt="Android 7.0+" />
</p>

---

## ⚡ Overview

**Anydown** is a modern, neo-brutalist Android application for downloading and converting media from YouTube, Spotify, Instagram, and over 1,000 supported platforms. 

Built natively with **Jetpack Compose** and powered by an on-device Python runtime via **Chaquopy**, Anydown runs **yt-dlp** and standalone **FFmpeg** binaries directly on your phone — no cloud intermediaries, no subscription gates, and zero ads.

---

## ✨ Features

- 🎬 **Best Quality Downloads (1080p / 1440p / 4K)**
  - Downloads adaptive high-resolution video and audio streams separately and merges them on-device into clean `.mp4` files using native 64-bit FFmpeg.
- 🎵 **Audio Only & Fast Downloads**
  - Download crisp M4A audio tracks or single-stream 720p videos in seconds.
- 📊 **Step-by-Step Progress & Live Telemetry**
  - Displays real-time download stages (`Step 1/2: High Quality Video` → `Step 2/2: Audio Track` → `Merging with FFmpeg`).
  - Live downloaded data size, total file size, and transfer speed in `MB/s` right in the app and status bar notification.
- 🎧 **Spotify Track Resolution**
  - Paste any Spotify track link — Anydown automatically discovers the best-matching high-fidelity stream and downloads it.
- 📋 **Batch & Playlist Support**
  - Shallow flat-extraction allows you to preview, select, and batch-download entire video playlists without app lag.
- 🛠️ **Built-in Media Tools**
  - Includes an on-device Video-to-MP3 converter with background batch processing and wake-lock management.
- 📁 **Custom Storage Destination**
  - Choose any folder on your phone or SD card using Android's Storage Access Framework (SAF), or default to system Downloads.
- 🔒 **Permanent App Signing**
  - Pre-configured release keystore ensuring future updates can be installed seamlessly in-place.

---

## 📱 Screenshots & UI

Anydown features a bold **Neo-Brutalist** design language with high-contrast surfaces, solid hard shadows, responsive haptics, and smooth aura particle transitions.

---

## 🛠️ Tech Stack & Architecture

| Layer | Technologies |
| :--- | :--- |
| **UI & Framework** | Jetpack Compose, Material 3, Coroutines, Flow |
| **Architecture** | MVVM, Single-Activity, Scoped Storage (SAF) |
| **Python Runtime** | [Chaquopy](https://chaquo.com/chaquopy/) |
| **Downloader Core** | [yt-dlp](https://github.com/yt-dlp/yt-dlp) |
| **Media Processing** | Standalone Native 64-bit FFmpeg (arm64-v8a) |
| **Image Loading** | Coil Compose |

---

## 📥 Installation

1. Go to the [**Releases**](https://github.com/boom1908/anydown-native/releases) page.
2. Download the latest `anydown-release.apk` (or `app-debug.apk`).
3. Open the downloaded file on your Android device to install.

---

## 🏗️ Building From Source

### Prerequisites
- **JDK 17** or **JDK 21**
- **Android SDK** (API Level 34 / 36)
- **Python 3.10+** (for Chaquopy build toolchain)

### Clone & Compile
```bash
# Clone the repository
git clone https://github.com/boom1908/anydown-native.git
cd anydown-native

# Build Debug APK
./gradlew assembleDebug

# Output APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/boom1908">boom1908</a>
</p>
