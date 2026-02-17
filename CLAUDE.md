# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**YT2Local** is a native Android app that downloads videos from YouTube and 1000+ other platforms (via yt-dlp), converting them to local MP3 audio or MP4 video files. Single-activity, single-screen app with Android Share/View intent support.

## Commands

```bash
# Build debug APK
./gradlew.bat assembleDebug

# Build release APK (minified with ProGuard)
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

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

Single-activity MVVM with Compose. All source in `app/src/main/java/com/example/yt2local/`:

- **MainActivity** → Extracts URL from SEND/VIEW intents, passes to MainScreen
- **MainScreen** → Full Compose UI, observes ViewModel state directly (no navigation)
- **MainViewModel** → Owns all UI state as Compose `mutableStateOf` (not StateFlow). Manages `AppState` lifecycle and delegates downloads to `VideoRepository`
- **VideoRepository** → All yt-dlp/FFmpeg logic. Downloads to UUID-named temp files, then copies to MediaStore

### AppState Machine

`INITIALIZING → UPDATING → READY ⇄ DOWNLOADING` (with `ERROR` reachable from any state)

On startup: YoutubeDL.init → FFmpeg.init → Aria2c.init (optional, failure tolerated) → yt-dlp update → READY

### Download Flow

1. URL extracted from user input (regex extracts URLs, prefers known video platforms over generic URLs)
2. yt-dlp downloads to `filesDir/video_temp/<uuid>.<ext>` (avoids special character issues in titles)
3. File moved to `Downloads/yt2local/` via MediaStore API with naming: `<sanitized_title>_YYYYMMDD_HHmmss.<ext>`
4. Temp directory cleaned after each download

### Intent Handling

Registered in AndroidManifest.xml for:
- `ACTION_SEND` (text/plain) — Share from any app
- `ACTION_VIEW` — Direct URL handling for YouTube, Vimeo, Twitter/X, TikTok domains
- Activity uses `singleTask` launch mode, handles `onNewIntent` for already-running app

## Key Implementation Details

- **State management**: All UI state uses Compose `mutableStateOf`/`mutableFloatStateOf` directly on ViewModel properties (not StateFlow)
- **Platform detection**: `VideoRepository.detectPlatform()` uses URL substring matching. Note: "music.youtube.com" check must come before "youtube.com" (currently has a bug — the generic YouTube check matches first)
- **Download history**: In-memory only (last 10 items), stored as `mutableStateOf<List<DownloadHistoryItem>>`
- **Aria2c**: Used as download accelerator (`-x 16 -s 16`); initialization failure is non-fatal
- **ProGuard**: Custom rules in `app/proguard-rules.pro` — must keep `youtubedl-android` classes and all data classes. **Add new data classes to ProGuard rules when creating them**
- **ABI splits**: Builds per-architecture APKs (x86, x86_64, armeabi-v7a, arm64-v8a) plus universal
- **Dependencies**: Managed via `gradle/libs.versions.toml`. Core library is `junkfood02/youtubedl-android` (library + ffmpeg + aria2c)

## Build Requirements

- JDK 17+
- Android SDK: compileSdk/targetSdk 34, minSdk 26
- Kotlin 1.9.0, Compose compiler 1.5.1
