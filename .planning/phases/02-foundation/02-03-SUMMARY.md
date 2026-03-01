---
phase: 02-foundation
plan: 03
subsystem: ui
tags: [kotlin, compose, stateflow, mvvm, android, viewmodel]

# Dependency graph
requires:
  - phase: 02-foundation/02-02
    provides: Hilt DI wiring — MainViewModel @HiltViewModel with @Inject constructor, ready for refactoring
provides:
  - Single StateFlow<DownloaderUiState> replacing 11 scattered mutableStateOf fields in MainViewModel
  - collectAsStateWithLifecycle() consumption in MainScreen — lifecycle-aware, thread-safe state observation
  - Thread-safe state mutation from any dispatcher via MutableStateFlow.update{}
affects:
  - 03-foreground-service (DownloaderUiState data class will need new fields for queue/service state)
  - any-future-ui (pattern: all UI state reads go through uiState.fieldName, not viewModel.fieldName)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single StateFlow<DownloaderUiState> data class as single source of truth for all UI state"
    - "MutableStateFlow.update { it.copy(...) } for all state mutations — thread-safe, no withContext needed"
    - "collectAsStateWithLifecycle() in Composables — lifecycle-aware, pauses when backgrounded"
    - "Conditional StateFlow update: _uiState.update { state -> if (cond) state.copy(...) else state }"

key-files:
  created: []
  modified:
    - app/src/main/java/com/example/yt2local/MainViewModel.kt
    - app/src/main/java/com/example/yt2local/MainScreen.kt

key-decisions:
  - "DownloaderUiState placed above MainViewModel class, below AppState enum — logical grouping"
  - "updateStatus() converted from suspend fun to regular fun — MutableStateFlow.update is already thread-safe"
  - "startDownload() captures detectedPlatform/isAudio into stateAtCompletion before post-download update to avoid TOCTOU in history item creation"
  - "postDownloadNotification() called after _uiState.update in startDownload — notification fires after UI is in READY state"

patterns-established:
  - "StateFlow pattern: private _uiState MutableStateFlow + public uiState = _uiState.asStateFlow()"
  - "Bulk state update: single _uiState.update { it.copy(a = ..., b = ...) } instead of multiple assignments"
  - "Conditional update: _uiState.update { state -> if (state.field == value) state.copy(...) else state }"
  - "Screen collection: val uiState by viewModel.uiState.collectAsStateWithLifecycle() at top of composable"

requirements-completed:
  - ARCH-02

# Metrics
duration: 3min
completed: 2026-03-01
---

# Phase 2 Plan 3: StateFlow Migration Summary

**Single StateFlow<DownloaderUiState> replaces 11 scattered mutableStateOf fields in MainViewModel, with MainScreen consuming via collectAsStateWithLifecycle() — thread-safe updates from any dispatcher, no withContext(Dispatchers.Main) needed**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-01T21:15:51Z
- **Completed:** 2026-03-01T21:19:11Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Consolidated 11 `mutableStateOf`/`mutableFloatStateOf` fields into a single `DownloaderUiState` data class exposed as `StateFlow<DownloaderUiState>`
- Removed all `withContext(Dispatchers.Main)` wrappers around state mutations — `MutableStateFlow.update{}` is inherently thread-safe
- Updated `MainScreen` to collect state via `collectAsStateWithLifecycle()` — lifecycle-aware collection pauses observation when app is backgrounded

## Task Commits

Each task was committed atomically:

1. **Task 1: Convert MainViewModel to StateFlow** - `f91d8a8` (refactor)
2. **Task 2: Update MainScreen to consume StateFlow** - `bc0281f` (refactor)

**Plan metadata:** (docs commit)

## Files Created/Modified
- `app/src/main/java/com/example/yt2local/MainViewModel.kt` - Added DownloaderUiState data class, replaced mutableStateOf fields with MutableStateFlow, converted all state mutations to _uiState.update{}
- `app/src/main/java/com/example/yt2local/MainScreen.kt` - Added collectAsStateWithLifecycle(), replaced all viewModel.field reads with uiState.field, updated LaunchedEffect keys

## Decisions Made
- `updateStatus()` converted from `suspend fun` to regular `fun` — MutableStateFlow.update{} needs no Main dispatcher, so the suspend qualifier was unnecessary
- `startDownload()` captures `stateAtCompletion` snapshot before the final update lambda to safely read `detectedPlatform` and `isAudio` when building the history item (avoids potential TOCTOU race if state changed during download)
- `postDownloadNotification()` called after `_uiState.update {}` so notification fires after UI transitions to READY state

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- StateFlow foundation complete — ready for Phase 3 (foreground service)
- `DownloaderUiState` data class can be extended with new fields for queue state, service binding status, etc.
- ProGuard keep rule for `DownloaderUiState` already present (added in Plan 01)

## Self-Check: PASSED

- FOUND: app/src/main/java/com/example/yt2local/MainViewModel.kt
- FOUND: app/src/main/java/com/example/yt2local/MainScreen.kt
- FOUND: .planning/phases/02-foundation/02-03-SUMMARY.md
- FOUND commit f91d8a8 (Task 1 — MainViewModel StateFlow migration)
- FOUND commit bc0281f (Task 2 — MainScreen collectAsStateWithLifecycle)

---
*Phase: 02-foundation*
*Completed: 2026-03-01*
