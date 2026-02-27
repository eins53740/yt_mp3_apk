# YT2Local

## What This Is

A native Android app that downloads videos from YouTube and 1000+ other platforms (via yt-dlp), converting them to local MP3 audio or MP4 video files. Single-activity, single-screen app optimized for "easiest music downloader" UX — sharing a URL auto-downloads as MP3 with zero taps.

## Core Value

Sharing a URL from any app instantly downloads it as an MP3 with zero taps. If this breaks, nothing else matters.

## Requirements

### Validated

- ✓ Download YouTube videos/audio via yt-dlp — existing
- ✓ Support 1000+ platforms via yt-dlp — existing
- ✓ MP3 audio extraction with metadata/thumbnail embedding — existing
- ✓ MP4 video download with merge — existing
- ✓ Share intent handling (ACTION_SEND) for zero-tap download — existing
- ✓ VIEW intent handling for YouTube, Vimeo, Twitter/X, TikTok, Instagram, Reddit, SoundCloud — existing
- ✓ Clipboard auto-paste on app resume — existing
- ✓ Audio/video format toggle with persistent preference — existing
- ✓ MediaStore API integration (Downloads/yt2local/) — existing
- ✓ Download progress display — existing
- ✓ Download history (in-memory, last 10) — existing
- ✓ System notification on download completion — existing
- ✓ Platform detection from URL — existing

### Active

- [ ] Fix yt-dlp initialization crash (app currently broken)
- [ ] Robust error handling — graceful degradation, user-friendly messages instead of stack traces
- [ ] Dependency injection with Hilt — proper separation of concerns
- [ ] Clean ViewModel state management — extract init logic, proper state machine
- [ ] Foreground service for downloads — survive activity kill
- [ ] Download queue — share multiple URLs, process sequentially
- [ ] Persistent download history — SharedPreferences/Room, survive app kill
- [ ] Quality picker — audio bitrate (128/192/320kbps), video quality (720p/1080p/Best)
- [ ] Skip yt-dlp update button — cancel if update hangs on first launch
- [ ] Open file from history — tap to play in default media player
- [ ] Bump dependencies — Compose BOM, Kotlin, fix AGP version
- [ ] Unit tests — detectPlatform(), sanitizeFileName(), extractUrl(), state transitions

### Out of Scope

- Streaming/playback within the app — this is a downloader, not a media player
- User accounts/authentication — local-only app
- Background sync/scheduled downloads — manual trigger only
- Custom UI themes beyond dark/light — keep it simple
- Playlist download support — single URL only for v1 refactor

## Context

The app was built as a quick MVP (Claude v0.1) and has accumulated technical debt. The core download engine works but the initialization flow is broken — `YoutubeDL.initPython()` fails on startup, rendering the app unusable. The codebase has no DI, all state is packed into a single ViewModel, and the error handling exposes raw stack traces to users.

**Current state:**
- App crashes on startup with `YoutubeDLException: failed to initialize`
- Stack trace: `YoutubeDL.initPython(YoutubeDL.kt:82)` → `MainViewModel$initialize$1.invokeSuspend(MainViewModel.kt:31)`
- Likely root cause: library version issue, ProGuard stripping, or Python binary packaging
- Dependencies are outdated (Compose BOM 2023.08, AGP possibly wrong at 8.13.1)

**Existing PLAN_NEXT.md** has 11 items organized by priority (P1 reliability, P2 features, P3 polish). This roadmap absorbs and reorders those items around a refactoring-first approach.

## Constraints

- **Tech stack**: Kotlin + Jetpack Compose + yt-dlp via junkfood02/youtubedl-android library
- **Min SDK**: 26 (Android 8.0)
- **No local build environment**: No JDK/Android SDK on dev machine — code changes are verified by reading, not building
- **ProGuard**: Must add `-keepclassmembers` rules for any new data classes
- **ABI splits**: Must maintain per-architecture APK builds
- **Library constraint**: All three junkfood02 artifacts (library, ffmpeg, aria2c) must be same version

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Fix crash before refactoring | App is unusable — can't validate refactoring on broken foundation | — Pending |
| Hilt for DI | Standard Android DI, good Compose/ViewModel integration | — Pending |
| Incremental refactor over rewrite | Working code exists, preserve what works, improve incrementally | — Pending |
| Foreground service for downloads | Current approach (ViewModel coroutine) dies on activity kill | — Pending |

---
*Last updated: 2026-02-27 after initialization*
