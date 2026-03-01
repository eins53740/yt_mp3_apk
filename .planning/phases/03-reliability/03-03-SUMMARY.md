---
phase: 03-reliability
plan: 03
subsystem: ui
tags: [kotlin, compose, viewmodel, stateflow, room, foreground-service, android]

# Dependency graph
requires:
  - phase: 03-02
    provides: DownloadService, DownloadStateHolder with MutableStateFlow<DownloadServiceState>
  - phase: 03-01
    provides: Room DB, DownloadHistoryDao, DownloadHistoryEntity

provides:
  - MainViewModel delegates downloads to DownloadService via startForegroundService()
  - MainViewModel observes DownloadStateHolder for real-time download progress/result
  - MainViewModel observes Room DAO Flow for persistent history
  - URL preserved on failure (ARCH-04) — retry without re-entering
  - Clickable history items open downloaded files via ACTION_VIEW
  - Friendly error color detection covering parseError() output patterns

affects: phase-04

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "StateFlow observation via .onEach{}.launchIn(viewModelScope) for reactive service-to-UI bridge"
    - "ViewModel resets StateHolder to Idle after consuming terminal states — prevents stale state replay on recreation"
    - "Room Flow mapped to UI model in ViewModel via .map{}.onEach{}.launchIn() chain"
    - "startForegroundService() wrapped in try/catch for ForegroundServiceStartNotAllowedException"

key-files:
  created: []
  modified:
    - app/src/main/java/com/example/yt2local/MainViewModel.kt
    - app/src/main/java/com/example/yt2local/MainScreen.kt

key-decisions:
  - "ViewModel resets DownloadStateHolder to Idle after Success/Failed — prevents stale replay if ViewModel recreated while service state is terminal"
  - "Error color detection uses contains() checks matching parseError() output patterns — not just startsWith(Error)"
  - "Retry button condition: READY + url.isNotBlank() + error keyword in statusMessage — safe without extra state field"

patterns-established:
  - "Service-to-ViewModel bridge: StateHolder.state.onEach{}.launchIn(viewModelScope)"
  - "URL preservation on failure: do not copy url/detectedPlatform in Failed branch of observeServiceState()"
  - "History clickable: mediaUri nullable, try/catch around startActivity for deleted files"

requirements-completed: [ARCH-03, ARCH-04, REL-04]

# Metrics
duration: 4min
completed: 2026-03-01
---

# Phase 03 Plan 03: ViewModel-Service-Room Wiring Summary

**ViewModel delegates downloads to DownloadService via startForegroundService(), observes Room history, preserves URL on failure for one-tap retry, and history items open downloaded files via ACTION_VIEW**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-01T21:55:37Z
- **Completed:** 2026-03-01T22:00:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Removed viewModelScope download coroutine — downloads now fully owned by DownloadService
- ViewModel observes DownloadStateHolder for live progress/success/failure and resets it to Idle after consuming terminal states
- Room DAO Flow replaces in-memory download history list — history survives app restart
- URL and detectedPlatform preserved on download failure (ARCH-04) enabling one-tap retry
- History items clickable — opens file in system media player via ACTION_VIEW with FLAG_GRANT_READ_URI_PERMISSION
- Retry Download button appears after failure with preserved URL (ARCH-03 + ARCH-04 together)
- Error message color covers parseError() friendly patterns: "failed", "unavailable", "denied", "not found", "not supported"

## Task Commits

Each task was committed atomically:

1. **Task 1: Refactor MainViewModel to use DownloadService and Room history** - `66754d4` (feat)
2. **Task 2: Add clickable history items and error retry UX to MainScreen** - `db3276e` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified

- `app/src/main/java/com/example/yt2local/MainViewModel.kt` - Rewired to delegate to DownloadService; observes StateHolder + Room DAO; postDownloadNotification() removed; DownloadHistoryItem gets mediaUri field
- `app/src/main/java/com/example/yt2local/MainScreen.kt` - HistoryItem clickable with ACTION_VIEW; Retry Download button for error states; extended error color detection

## Decisions Made

- ViewModel resets DownloadStateHolder to Idle after consuming Success/Failed states — prevents stale state replay if ViewModel is recreated while service state is still terminal
- Error color detection uses `contains()` rather than `startsWith("Error")` alone — matches the friendly strings produced by VideoRepository.parseError() (e.g., "This video is unavailable", "Access denied")
- Retry button uses no extra state field — conditions derived from existing READY + url + statusMessage signals

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All three Phase 3 plans complete: Room DB (03-01), DownloadService (03-02), ViewModel wiring (03-03)
- Phase 3 gate: verify foreground service on API 34 emulator before Phase 4
- Phase 4 can proceed: WorkManager queue, quality picker, download cancellation

---
*Phase: 03-reliability*
*Completed: 2026-03-01*
