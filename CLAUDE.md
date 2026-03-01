# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**YT2Local** is a native Android app that downloads videos from YouTube and 1000+ other platforms (via yt-dlp), converting them to local MP3 audio or MP4 video files. Single-activity, single-screen app with Android Share/View intent support. Optimized for "easiest music downloader" UX — sharing a URL auto-downloads as MP3 with zero taps.

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

- **MainActivity** → Owns ViewModel via `by viewModels()`. Extracts URL from SEND/VIEW intents and calls `viewModel.setUrlFromIntent()`. Handles `onNewIntent` for already-running app. Requests POST_NOTIFICATIONS permission on Android 13+.
- **MainScreen** → Full Compose UI wrapped in Scaffold (with SnackbarHost). Observes ViewModel state directly. Handles clipboard auto-paste on resume, auto-download trigger via LaunchedEffect.
- **MainViewModel** → Owns all UI state as Compose `mutableStateOf` (not StateFlow). Manages `AppState` lifecycle, delegates downloads to `VideoRepository`. Posts download completion notifications. Persists format preference via SharedPreferences.
- **VideoRepository** → All yt-dlp/FFmpeg logic. Downloads to UUID-named temp files, then copies to MediaStore. Embeds metadata/thumbnails for both audio and video.
- **YT2LocalApplication** → Creates notification channel on startup.

### AppState Machine

`INITIALIZING → UPDATING → READY ⇄ DOWNLOADING` (with `ERROR` reachable from any state)

On startup: YoutubeDL.init → FFmpeg.init → Aria2c.init (optional, failure tolerated) → yt-dlp update → READY

### Download Flow

1. URL arrives via share intent (auto-download), clipboard paste, or manual input
2. Share intents force audio mode (MP3) and auto-start download when READY (zero taps)
3. yt-dlp downloads to `filesDir/video_temp/<uuid>.<ext>` (avoids special character issues in titles)
4. File moved to `Downloads/yt2local/` via MediaStore API with naming: `<sanitized_title>_YYYYMMDD_HHmmss.<ext>`
5. Temp directory cleaned after each download
6. Snackbar + system notification on completion

### Intent Handling

Registered in AndroidManifest.xml for:
- `ACTION_SEND` (text/plain) — Share from any app
- `ACTION_VIEW` — Direct URL handling for YouTube, Vimeo, Twitter/X, TikTok, Instagram, Reddit, SoundCloud
- Activity uses `singleTask` launch mode, handles `onNewIntent` for already-running app
- `onNewIntent` extracts URL and calls `viewModel.setUrlFromIntent(url, autoStart=true)`

### Clipboard Auto-Paste

On app resume (ON_RESUME lifecycle event), if URL field is empty, checks clipboard for recognized video platform URLs and auto-fills the field. Does NOT auto-start download from clipboard (only from intents).

## Key Implementation Details

- **State management**: All UI state uses Compose `mutableStateOf`/`mutableFloatStateOf` directly on ViewModel properties (not StateFlow)
- **Platform detection**: `VideoRepository.detectPlatform()` uses URL substring matching. Order matters: `music.youtube.com` → `youtube.com/shorts` → `youtube.com/youtu.be` → others
- **Auto-download**: `autoDownloadPending` flag in ViewModel + `LaunchedEffect` in MainScreen watching flag + appState. `consumeAutoDownload()` clears flag and starts download.
- **Format persistence**: Audio/video preference saved to SharedPreferences (`yt2local_prefs`), default is audio
- **Download history**: In-memory only (last 10 items), stored as `mutableStateOf<List<DownloadHistoryItem>>`
- **Notifications**: Channel `yt2local_downloads` created in `YT2LocalApplication`. Notification posted from ViewModel after successful download. Permission requested in `MainActivity`.
- **Metadata embedding**: `--embed-thumbnail` and `--embed-metadata` apply to both audio and video (MP3s get album art + ID3 tags)
- **Filename sanitization**: Strips `[\\/:*?"<>|]`, emoji/special unicode, collapses whitespace to `_`, caps at 100 chars
- **Paste/Clear button**: URL field has trailing icon — paste from clipboard when empty, clear (X) when filled
- **Aria2c**: Used as download accelerator (`-x 16 -s 16`); initialization failure is non-fatal
- **ProGuard**: Custom rules in `app/proguard-rules.pro` — must keep `youtubedl-android` classes and all data classes. **Add new data classes to ProGuard rules when creating them**
- **ABI splits**: Builds per-architecture APKs (x86, x86_64, armeabi-v7a, arm64-v8a) plus universal
- **Dependencies**: Managed via `gradle/libs.versions.toml`. Core library is `junkfood02/youtubedl-android` (library + ffmpeg + aria2c)

## Build Requirements

- JDK 17+
- Android SDK: compileSdk/targetSdk 34, minSdk 26
- Kotlin 1.9.0, Compose compiler 1.5.1

## Future Plans

See `PLAN_NEXT.md` for the next-phase roadmap (foreground service, download queue, quality picker, persistent history, etc.).
