---
phase: 03-reliability
plan: 02
subsystem: foreground-service
tags: [android, foreground-service, hilt, room, coroutines]
dependency_graph:
  requires: [03-01]
  provides: [DownloadStateHolder, DownloadService, Android14ForegroundServiceDeclaration]
  affects: [MainViewModel, VideoRepository, AppModule]
tech_stack:
  added: [ServiceCompat, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC]
  patterns: [foreground-service, state-holder-bridge, service-scoped-coroutine]
key_files:
  created:
    - app/src/main/java/com/example/yt2local/data/DownloadStateHolder.kt
    - app/src/main/java/com/example/yt2local/service/DownloadService.kt
  modified:
    - app/src/main/java/com/example/yt2local/VideoRepository.kt
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/example/yt2local/di/AppModule.kt
    - app/proguard-rules.pro
decisions:
  - "DownloadStateHolder uses MutableStateFlow (not bound service) — avoids ServiceConnection complexity while keeping architecture testable"
  - "Service does NOT reset state to Idle — ViewModel is responsible to prevent race where ViewModel misses terminal result"
  - "START_NOT_STICKY — service should not restart if killed (stale download would be invisible to user)"
  - "Notification ID 2001 — avoids collision with ViewModel notification ID 1001"
  - "serviceScope = SupervisorJob + Dispatchers.IO — survives ViewModel destruction, cancelled only in onDestroy"
metrics:
  duration: 3 min
  completed: "2026-03-01T21:53:24Z"
  tasks: 2
  files_created: 2
  files_modified: 4
---

# Phase 3 Plan 02: Foreground Service Infrastructure Summary

**One-liner:** Android 14 foreground service with dataSync type, DownloadStateHolder MutableStateFlow bridge, and service-scoped coroutine for download survival across Activity destruction.

## What Was Built

A complete foreground service infrastructure enabling downloads to survive app backgrounding:

1. **DownloadStateHolder** — `@Singleton` MutableStateFlow bridge between DownloadService and MainViewModel. Service writes states (`Idle`, `InProgress`, `Success`, `Failed`). ViewModel observes. No bound service needed.

2. **DownloadService** — `@AndroidEntryPoint` foreground service with `dataSync` type. Calls `ServiceCompat.startForeground()` immediately in `onStartCommand()`. Uses `CoroutineScope(SupervisorJob() + Dispatchers.IO)` — not `viewModelScope`. Injects `VideoRepository`, `DownloadStateHolder`, and `DownloadHistoryDao`. Inserts history row into Room on success. Calls `stopSelf()` in `finally` block.

3. **Android 14 three-part declaration** — All three required parts present simultaneously:
   - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions in manifest
   - `<service>` element with `foregroundServiceType="dataSync"` and `exported="false"`
   - `ServiceCompat.startForeground()` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` constant

4. **VideoRepository.DownloadResult** — Added `mediaUri: String?` field. `moveToMediaStore()` now returns `uri.toString()` from the MediaStore insertion, enabling tap-to-open in history.

5. **AppModule** — Added `provideDownloadStateHolder()` as `@Singleton` to ensure single shared instance across Service and ViewModel.

6. **ProGuard** — Keep rules for all `DownloadServiceState` sealed class variants, plus Room base class rules.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | DownloadStateHolder, DownloadResult mediaUri, manifest, ProGuard | ed1b053 | DownloadStateHolder.kt, VideoRepository.kt, AndroidManifest.xml, proguard-rules.pro |
| 2 | DownloadService foreground service + AppModule provision | 21f6385 | DownloadService.kt, AppModule.kt |

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- FOUND: app/src/main/java/com/example/yt2local/data/DownloadStateHolder.kt
- FOUND: app/src/main/java/com/example/yt2local/service/DownloadService.kt
- FOUND: commit ed1b053
- FOUND: commit 21f6385
