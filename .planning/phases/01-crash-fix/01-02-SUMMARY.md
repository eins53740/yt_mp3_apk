---
phase: 01-crash-fix
plan: 02
subsystem: ui
tags: [kotlin, compose, coroutines, job-cancellation, ux]

# Dependency graph
requires: []
provides:
  - Skip update button visible during UPDATING state in MainScreen
  - skipUpdate() function in MainViewModel cancels updateJob and transitions to READY
  - Cancellable updateJob: Job? property in MainViewModel
  - CancellationException properly re-thrown for structured concurrency
affects: [future-update-flow, foreground-service-phase]

# Tech tracking
tech-stack:
  added: []
  patterns: [cancellable-child-job, guard-state-transition]

key-files:
  created: []
  modified:
    - app/src/main/java/com/example/yt2local/MainViewModel.kt
    - app/src/main/java/com/example/yt2local/MainScreen.kt

key-decisions:
  - "Used updateJob?.join() in parent coroutine so skipUpdate() can interrupt via cancel() without withTimeout (which cannot interrupt JVM-blocking HTTP calls)"
  - "Guard READY transition with appState == UPDATING check so skipUpdate() and normal completion don't race"
  - "skipUpdate() called from onClick — no withContext(Dispatchers.Main) needed as Button onClick is already on main thread"

patterns-established:
  - "Cancellable child job pattern: launch separate Job for long-running background work, store reference, cancel via user action"
  - "State guard pattern: check current state before setting next state to avoid race conditions between cancellation and normal completion"

requirements-completed: [FIX-03]

# Metrics
duration: 2min
completed: 2026-02-27
---

# Phase 1 Plan 02: Skip Update Button Summary

**Cancellable yt-dlp update via Job reference with a TextButton escape hatch that transitions to READY in under one second**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-27T16:11:32Z
- **Completed:** 2026-02-27T16:14:08Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Added `updateJob: Job?` to MainViewModel to hold a reference to the yt-dlp update coroutine
- Restructured `initialize()` to launch update as a separate child job and await via `join()` (cancellable)
- Added `skipUpdate()` that cancels `updateJob` and immediately transitions to READY
- Added "Skip update" TextButton to MainScreen, visible only during UPDATING state
- CancellationException properly caught and re-thrown to respect structured concurrency

## Task Commits

Each task was committed atomically:

1. **Task 1: Make yt-dlp update cancellable via Job reference and add skipUpdate()** - `1c3149c` (feat)
2. **Task 2: Add Skip update button to MainScreen UI** - `3be21d2` (feat)

## Files Created/Modified
- `app/src/main/java/com/example/yt2local/MainViewModel.kt` - Added updateJob property, restructured initialize() with cancellable child job, added skipUpdate() function, removed UPDATING state from updateYtDlp()
- `app/src/main/java/com/example/yt2local/MainScreen.kt` - Added Skip update TextButton visible only during UPDATING state

## Decisions Made
- Used `updateJob?.join()` inside the parent coroutine rather than `withTimeout` because `YoutubeDL.getInstance().updateYoutubeDL()` is a JVM-blocking HTTP call — `withTimeout` cannot interrupt JVM-blocking calls reliably; `Job.cancel()` works by cancelling the coroutine that wraps the blocking call
- Added an `if (appState == AppState.UPDATING)` guard before setting READY at the end of `initialize()` to prevent a race condition where both the natural completion path and `skipUpdate()` try to set READY simultaneously
- `skipUpdate()` mutates ViewModel state directly (no `withContext`) because Button `onClick` lambdas are already on the main thread

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Skip update escape hatch is now in place; users can immediately use the app even if the network update hangs
- Both tasks (FIX-01 through FIX-03) in Phase 1 are now addressed
- Codebase is ready for Phase 2 (Hilt DI + dependency modernization)

---
*Phase: 01-crash-fix*
*Completed: 2026-02-27*

## Self-Check: PASSED

- FOUND: app/src/main/java/com/example/yt2local/MainViewModel.kt
- FOUND: app/src/main/java/com/example/yt2local/MainScreen.kt
- FOUND: .planning/phases/01-crash-fix/01-02-SUMMARY.md
- FOUND: commit 1c3149c (Task 1)
- FOUND: commit 3be21d2 (Task 2)
