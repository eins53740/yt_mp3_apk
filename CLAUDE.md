# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**YT2Local** is a native Android app that downloads videos from YouTube and 1000+ other platforms, converting them to local MP3 audio or MP4 video files. Supports Android Share intent for direct URL sharing from any app.

## Tech Stack

- **Kotlin 1.9.0** with Jetpack Compose (Material 3)
- **Android SDK**: Min 26, Target/Compile 34
- **Build**: Gradle 8.13.1 (Kotlin DSL) with Version Catalog
- **Architecture**: MVVM with Repository pattern
- **Key Libraries**:
  - `youtubedl-android` (v0.18.0) - yt-dlp wrapper for video downloads
  - `youtubedl-ffmpeg` (v0.18.0) - FFmpeg for audio/video conversion
  - `youtubedl-aria2c` (v0.18.0) - Aria2c for faster parallel downloads
  - `coil-compose` - Image loading

## Commands

```bash
# Build debug APK
./gradlew.bat assembleDebug

# Build release APK (minified)
./gradlew.bat assembleRelease

# Install to connected device
./gradlew.bat installDebug

# Clean build
./gradlew.bat clean

# Lint check
./gradlew.bat lint

# Tests (when added)
./gradlew.bat testDebugUnitTest
./gradlew.bat connectedAndroidTest
```

## Architecture

```
app/src/main/java/com/example/yt2local/
├── MainActivity.kt          # Entry point, handles Share/View intents
├── MainScreen.kt            # Compose UI with progress bar, platform detection
├── MainViewModel.kt         # State management, YoutubeDL/FFmpeg/Aria2c init, yt-dlp updates
├── VideoRepository.kt       # Download logic, multi-platform support, MediaStore integration
├── YT2LocalApplication.kt   # Application class
└── Theme.kt                 # Material 3 theming (YouTube-inspired colors)
```

**Data Flow**:
1. URL input (manual, Share intent, or direct VIEW intent) → ViewModel
2. ViewModel extracts URL from text, detects platform → Repository.downloadMedia()
3. Repository: yt-dlp download (with aria2c) → FFmpeg conversion → MediaStore upload
4. Files saved to `Downloads/yt2local/` with `<title>_YYYYMMDD_HHmmss.<ext>` naming

**Supported Platforms** (1000+ via yt-dlp):
- YouTube, YouTube Music, YouTube Shorts
- TikTok, Instagram, Twitter/X, Facebook
- Vimeo, Dailymotion, Twitch, Reddit
- SoundCloud, Bandcamp, Bilibili, and many more

## Dependencies

Dependencies managed via `gradle/libs.versions.toml`:
- `io.github.junkfood02.youtubedl-android:library:0.18.0`
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.0`
- `io.github.junkfood02.youtubedl-android:aria2c:0.18.0`
- Compose BOM 2023.08.00
- AndroidX Lifecycle 2.6.2

## Build Configuration

- **ABI splits**: x86, x86_64, armeabi-v7a, arm64-v8a (plus universal)
- **ProGuard**: Enabled for release builds with custom rules
- **JDK**: 17+ required
- **Java target**: 17

## Key Behaviors

- YoutubeDL, FFmpeg, and Aria2c initialize asynchronously in ViewModel
- yt-dlp auto-updates to latest version on app start
- URL extraction handles shared text with extra content (e.g., "Check out: https://...")
- Platform auto-detection from URL shows badge in UI
- Download progress with percentage and ETA displayed
- Download history (in-memory, last 10 items)
- Share intent extracts URL from `Intent.EXTRA_TEXT`
- VIEW intent handles direct YouTube/TikTok/Twitter URLs

## Intent Filters

The app registers as a handler for:
- Share intent (text/plain) from any app
- Direct URL viewing for YouTube, Vimeo, Twitter/X, TikTok domains
