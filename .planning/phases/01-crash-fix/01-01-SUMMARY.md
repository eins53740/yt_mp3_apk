---
phase: 01-crash-fix
plan: 01
subsystem: infra
tags: [android, gradle, agp, manifest, native-libs, youtubedl-android]

# Dependency graph
requires: []
provides:
  - "extractNativeLibs=true on AndroidManifest application tag — ensures youtubedl-android .so files are extracted at install time"
  - "AGP version fixed to 8.13.2 — Gradle sync now resolves com.android.tools.build:gradle:8.13.2"
affects: [01-crash-fix, 02-hilt-modernize, 03-foreground-service, 04-queue-polish]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "extractNativeLibs=true required for any app bundling native Python/FFmpeg .so libraries via youtubedl-android"

key-files:
  created: []
  modified:
    - app/src/main/AndroidManifest.xml
    - gradle/libs.versions.toml

key-decisions:
  - "Set extractNativeLibs=true rather than repackaging the APK — minimum change to unblock YoutubeDL.init()"
  - "Upgraded AGP to 8.13.2 (not 8.13.0) — 8.13.2 is the latest available patch release matching Gradle wrapper 8.13"
  - "Did not change Kotlin, Compose BOM, or any other dependency versions — isolating crash fix from dependency modernization (Phase 2)"

patterns-established:
  - "Minimum-change approach: touch only the two config lines required to unblock the crash"

requirements-completed: [FIX-01, FIX-02]

# Metrics
duration: 2min
completed: 2026-02-27
---

# Phase 1 Plan 01: Crash Fix — Build and Native Lib Unblock Summary

**android:extractNativeLibs="true" added to manifest and AGP bumped from non-existent 8.13.1 to 8.13.2, unblocking Gradle sync and youtubedl-android native library extraction at install time**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-02-27T16:11:26Z
- **Completed:** 2026-02-27T16:13:30Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- Fixed Gradle sync failure caused by non-existent AGP version 8.13.1
- Added `android:extractNativeLibs="true"` to prevent crash at launch due to missing Python/FFmpeg native library paths
- Confirmed diff shows exactly 1 line added in manifest and 1 line changed in version catalog — no scope creep

## Task Commits

Each task was committed atomically:

1. **Task 1: Add extractNativeLibs and fix AGP version** - `2ddd90d` (fix)

**Plan metadata:** (pending docs commit)

## Files Created/Modified
- `app/src/main/AndroidManifest.xml` - Added `android:extractNativeLibs="true"` to `<application>` tag
- `gradle/libs.versions.toml` - Changed `agp = "8.13.1"` to `agp = "8.13.2"`

## Decisions Made
- AGP bumped to 8.13.2 (not 8.13.0): 8.13.2 is the latest patch release for the 8.13.x line, which is compatible with the existing Gradle wrapper 8.13.
- `extractNativeLibs=true` is the minimum fix — no other manifest attributes changed, no `android:requestLegacyExternalStorage` added.
- Kotlin (1.9.0) and Compose BOM (2023.08.00) deliberately left unchanged — dependency modernization is scoped to Phase 2.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- App can now build with `./gradlew assembleDebug` — AGP resolves correctly
- Native .so libraries from youtubedl-android will be extracted at install time, unblocking `YoutubeDL.getInstance().init()`
- Ready to proceed to Plan 01-02 (network security config review) within Phase 1

---
*Phase: 01-crash-fix*
*Completed: 2026-02-27*
