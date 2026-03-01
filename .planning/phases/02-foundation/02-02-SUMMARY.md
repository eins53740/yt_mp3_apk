---
phase: 02-foundation
plan: 02
subsystem: infra
tags: [hilt, dagger, di, android, kotlin, viewmodel, repository]

# Dependency graph
requires:
  - phase: 02-01
    provides: Hilt plugin (com.google.dagger.hilt.android), KSP, hilt-android and hilt-android-compiler dependencies in build files

provides:
  - Hilt DI fully wired: Application -> Activity -> ViewModel -> Repository
  - AppModule.kt as single source of dependency bindings
  - VideoRepository injectable as singleton via @Inject constructor
  - MainViewModel injectable via @HiltViewModel with @ApplicationContext
  - No manual dependency instantiation anywhere in the codebase

affects:
  - 03-stateflow (ViewModel refactor will extend this Hilt-injected ViewModel)
  - 04-foreground-service (foreground service requires @AndroidEntryPoint + injected dependencies)
  - 05-download-queue (WorkManager workers need Hilt injection support)

# Tech tracking
tech-stack:
  added: []  # Hilt libraries were added in 02-01; this plan wires the annotations
  patterns:
    - "@HiltAndroidApp on Application class as Hilt component tree root"
    - "@AndroidEntryPoint on Activity to enable Hilt ViewModel injection"
    - "@HiltViewModel + @Inject constructor for ViewModel with injected dependencies"
    - "@Inject constructor on Repository for Hilt discovery"
    - "@Module @InstallIn(SingletonComponent) AppModule as single source of bindings"
    - "@ApplicationContext qualifier to avoid Activity context leaks in singletons"

key-files:
  created:
    - app/src/main/java/com/example/yt2local/di/AppModule.kt
  modified:
    - app/src/main/java/com/example/yt2local/YT2LocalApplication.kt
    - app/src/main/java/com/example/yt2local/MainActivity.kt
    - app/src/main/java/com/example/yt2local/MainViewModel.kt
    - app/src/main/java/com/example/yt2local/VideoRepository.kt

key-decisions:
  - "VideoRepository scoped as @Singleton via AppModule — holds no mutable state, single instance correct for ViewModel and future Service"
  - "@ApplicationContext used for VideoRepository and MainViewModel context — avoids Activity context leaks in singletons"
  - "by viewModels() kept in MainActivity (not hiltViewModel()) — correct for Activities, hiltViewModel() is for Composables"
  - "ViewModel extends plain ViewModel (not AndroidViewModel) — AndroidViewModel was only needed for getApplication(), replaced by @ApplicationContext injection"

patterns-established:
  - "Hilt entry points: Application=@HiltAndroidApp, Activity=@AndroidEntryPoint, ViewModel=@HiltViewModel"
  - "All context access in ViewModel via injected @ApplicationContext — never getApplication()"
  - "Repository bindings always in AppModule, never instantiated directly in ViewModel"

requirements-completed: [ARCH-01]

# Metrics
duration: 2min
completed: 2026-03-01
---

# Phase 2 Plan 02: Hilt DI Wiring Summary

**Full Hilt constructor injection across Application, Activity, ViewModel, and Repository — VideoRepository is now a singleton provided by AppModule, MainViewModel uses @HiltViewModel with @ApplicationContext replacing AndroidViewModel**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-01T21:10:52Z
- **Completed:** 2026-03-01T21:12:54Z
- **Tasks:** 2
- **Files modified:** 5 (1 created, 4 modified)

## Accomplishments

- Created `di/AppModule.kt` providing VideoRepository as a Hilt singleton bound to ApplicationContext
- Annotated `YT2LocalApplication` with `@HiltAndroidApp` (Hilt component tree root)
- Converted `MainViewModel` from `AndroidViewModel` to `ViewModel` with `@HiltViewModel @Inject constructor(VideoRepository, @ApplicationContext Context)` — eliminated all `getApplication()` calls
- Annotated `MainActivity` with `@AndroidEntryPoint` enabling Hilt-backed `by viewModels()` delegation
- Added `@Inject constructor` to `VideoRepository` for Hilt discovery — no other logic changed

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Hilt module and annotate Application and Repository** - `2f008f3` (feat)
2. **Task 2: Convert MainViewModel to @HiltViewModel and annotate MainActivity** - `2761826` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `app/src/main/java/com/example/yt2local/di/AppModule.kt` - New Hilt module providing VideoRepository as singleton via @ApplicationContext
- `app/src/main/java/com/example/yt2local/YT2LocalApplication.kt` - Added @HiltAndroidApp annotation
- `app/src/main/java/com/example/yt2local/MainActivity.kt` - Added @AndroidEntryPoint annotation and dagger.hilt.android.AndroidEntryPoint import
- `app/src/main/java/com/example/yt2local/MainViewModel.kt` - Converted to @HiltViewModel with @Inject constructor; removed AndroidViewModel, all getApplication() calls replaced with injected context
- `app/src/main/java/com/example/yt2local/VideoRepository.kt` - Added @Inject to constructor and javax.inject.Inject import

## Decisions Made

- `@Singleton` scoped VideoRepository in AppModule (not on the class itself) — keeps scope declaration in the module, consistent with Hilt best practices
- `@ApplicationContext` used in both AppModule provider and MainViewModel constructor — avoids memory leaks from holding Activity context in long-lived objects
- `by viewModels()` unchanged in MainActivity — `hiltViewModel()` is for Composables only; the Activity delegate works correctly with Hilt when `@AndroidEntryPoint` is present
- Plain `ViewModel` (not `AndroidViewModel`) — `AndroidViewModel` was only needed to access `getApplication()`; constructor injection replaces that need entirely

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Hilt DI fully wired across the entire app — ready for Phase 3 (StateFlow refactor) which only needs to modify ViewModel state management
- AppModule is the single source for dependency bindings — future services (Phase 4 foreground service) can be added here
- All app behavior identical to Phase 1 — zero functional regression from Hilt introduction

## Self-Check: PASSED

- All 5 source files verified on disk
- Commits 2f008f3 and 2761826 verified in git log

---
*Phase: 02-foundation*
*Completed: 2026-03-01*
