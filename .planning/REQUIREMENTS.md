# Requirements: YT2Local

**Defined:** 2026-02-27
**Core Value:** Sharing a URL from any app instantly downloads it as an MP3 with zero taps

## v1 Requirements

Requirements for this refactoring milestone. Each maps to roadmap phases.

### Critical Fixes

- [x] **FIX-01**: App starts without crashing (add `extractNativeLibs="true"` to manifest)
- [x] **FIX-02**: Build succeeds with correct AGP version (fix 8.13.1 typo)
- [x] **FIX-03**: User can skip yt-dlp update if it hangs on first launch (timeout/cancel button)

### Architecture

- [x] **ARCH-01**: All dependencies injected via Hilt (ViewModel, Repository, Service)
- [x] **ARCH-02**: UI state managed via single StateFlow sealed class (replace scattered mutableStateOf) (completed 2026-03-01)
- [ ] **ARCH-03**: Errors displayed as user-friendly messages with retry option (no stack traces)
- [ ] **ARCH-04**: URL field preserved on download failure (user doesn't have to re-enter)

### Reliability

- [x] **REL-01**: Downloads continue when user backgrounds app (foreground service with notification)
- [x] **REL-02**: Download history persists across app restarts (Room database)
- [ ] **REL-03**: User can share multiple URLs and they queue for sequential download
- [x] **REL-04**: User can tap history item to open/play the downloaded file

### Modernization

- [x] **MOD-01**: Dependencies updated — Kotlin 2.2.10, Compose BOM 2026.02.01, KSP 2.3.6, Hilt 2.59.2 (completed 2026-03-01)
- [ ] **MOD-02**: Audio quality picker — user can choose bitrate (128/192/320 kbps)
- [ ] **MOD-03**: Video quality picker — user can choose resolution (720p/1080p/Best)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Testing

- **TEST-01**: Unit tests for detectPlatform(), sanitizeFileName(), extractUrl()
- **TEST-02**: Unit tests for AppState machine transitions
- **TEST-03**: Integration tests for download flow

### Polish

- **POL-01**: Custom app icon (branded music+download icon)
- **POL-02**: Haptic feedback on download completion
- **POL-03**: Monochrome notification icon for Android 13+

## Out of Scope

| Feature | Reason |
|---------|--------|
| In-app media player/streaming | This is a downloader, not a media player |
| Playlist download support | High complexity, changes product scope |
| User accounts/authentication | Local-only app |
| Background sync/scheduled downloads | Manual trigger only |
| AAB (App Bundle) builds | Permanently incompatible with youtubedl-android library |
| Custom UI themes | Dark/light mode sufficient |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FIX-01 | Phase 1 | Complete |
| FIX-02 | Phase 1 | Complete |
| FIX-03 | Phase 1 | Complete |
| ARCH-01 | Phase 2 | Complete |
| ARCH-02 | Phase 2 | Complete |
| ARCH-03 | Phase 3 | Pending |
| ARCH-04 | Phase 3 | Pending |
| REL-01 | Phase 3 | Complete |
| REL-02 | Phase 3 | Complete |
| REL-03 | Phase 4 | Pending |
| REL-04 | Phase 3 | Complete |
| MOD-01 | Phase 2 | Complete |
| MOD-02 | Phase 4 | Pending |
| MOD-03 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 14 total
- Mapped to phases: 14
- Unmapped: 0

---
*Requirements defined: 2026-02-27*
*Last updated: 2026-03-01 after 02-03 completion (ARCH-02 marked complete)*
