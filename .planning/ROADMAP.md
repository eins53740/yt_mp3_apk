# Roadmap: YT2Local

## Overview

YT2Local is a working but unstable Android downloader app that needs a reliability-first refactor. The dependency chain is strict: fix the startup crash before anything else can be verified, modernize dependencies and introduce Hilt DI before the foreground service, stabilize the foreground service before adding a download queue. Four phases deliver a fully reliable, production-quality downloader while preserving the core zero-tap share-to-MP3 experience throughout.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Crash Fix** - Restore basic functionality so the app starts and downloads
- [ ] **Phase 2: Foundation** - Modernize dependencies, introduce Hilt DI and StateFlow state
- [ ] **Phase 3: Reliability** - Foreground service, persistent history, and production error handling
- [ ] **Phase 4: Power Features** - Download queue, audio/video quality picker

## Phase Details

### Phase 1: Crash Fix
**Goal**: The app starts without crashing and completes at least one download end-to-end
**Depends on**: Nothing (first phase)
**Requirements**: FIX-01, FIX-02, FIX-03
**Success Criteria** (what must be TRUE):
  1. App opens on a real device without throwing an exception or force-closing
  2. User can successfully download a YouTube URL as MP3 from the main screen
  3. User sees a "Skip update" button during yt-dlp initialization that cancels and proceeds if tapped
  4. Build compiles cleanly with `./gradlew assembleDebug` without Gradle sync errors
**Plans**: 2 plans (1 wave)

Plans:
- [ ] 01-01-PLAN.md — Fix extractNativeLibs manifest attribute and AGP 8.13.1 version typo
- [ ] 01-02-PLAN.md — Add cancellable yt-dlp update with Skip button in UI

### Phase 2: Foundation
**Goal**: The codebase runs on a modern, well-structured foundation with Hilt DI and StateFlow-based ViewModel state — app behavior is unchanged from Phase 1
**Depends on**: Phase 1
**Requirements**: MOD-01, ARCH-01, ARCH-02
**Success Criteria** (what must be TRUE):
  1. App builds and runs identically to Phase 1 after all dependency upgrades (zero functional regression)
  2. All production dependencies injected via Hilt — no manual `new` or `object` singletons for repository or data sources
  3. ViewModel exposes a single `StateFlow<DownloaderUiState>` sealed class — no scattered `mutableStateOf` fields
  4. Release build (`./gradlew assembleRelease`) succeeds and app runs correctly on device without crash from ProGuard stripping
**Plans**: TBD

Plans:
- [ ] 02-01: Bump Kotlin 2.x, Compose BOM, Coil 3.x, AGP 8.13.2, KSP, DataStore
- [ ] 02-02: Introduce Hilt (Application, MainActivity, ViewModel, modules)
- [ ] 02-03: Migrate ViewModel state to StateFlow sealed class

### Phase 3: Reliability
**Goal**: Downloads survive app backgrounding, history persists across restarts, and errors are always user-friendly
**Depends on**: Phase 2
**Requirements**: REL-01, REL-02, REL-04, ARCH-03, ARCH-04
**Success Criteria** (what must be TRUE):
  1. User starts a download, switches to another app, and the download completes with a system notification
  2. User kills and relaunches the app and their previous download history is still visible
  3. User taps a history item and the file opens in the device's default media player
  4. User sees a plain-language error message with a retry button when a download fails — no stack traces visible
  5. After a download failure, the URL remains in the input field so the user does not have to re-type it
**Plans**: TBD

Plans:
- [ ] 03-01: Implement DownloadService (foreground, dataSync type) with Android 14 three-part declaration
- [ ] 03-02: Implement Room database for persistent download history
- [ ] 03-03: Wire DownloadRepositoryImpl to service and Room; implement error mapping and URL preservation

### Phase 4: Power Features
**Goal**: Users can queue multiple downloads and choose audio/video quality
**Depends on**: Phase 3
**Requirements**: REL-03, MOD-02, MOD-03
**Success Criteria** (what must be TRUE):
  1. User shares three URLs in succession and all three download sequentially without any being dropped
  2. User can select audio bitrate (128 / 192 / 320 kbps) before downloading and the resulting MP3 reflects the chosen quality
  3. User can select video resolution (720p / 1080p / Best) before downloading and the resulting MP4 reflects the chosen resolution
**Plans**: TBD

Plans:
- [ ] 04-01: Implement download queue in DownloadService (sequential, in-service Queue<DownloadJob>)
- [ ] 04-02: Add audio bitrate picker and wire to yt-dlp format string
- [ ] 04-03: Add video resolution picker and wire to yt-dlp format string

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Crash Fix | 0/2 | Not started | - |
| 2. Foundation | 0/3 | Not started | - |
| 3. Reliability | 0/3 | Not started | - |
| 4. Power Features | 0/3 | Not started | - |
