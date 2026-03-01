# Project Research Summary

**Project:** YT2Local — Android video/audio downloader
**Domain:** Android native app (Kotlin/Compose), yt-dlp wrapper, clean architecture refactor
**Researched:** 2026-02-27
**Confidence:** HIGH (stack/architecture/pitfalls from official docs; features MEDIUM from competitive analysis)

## Executive Summary

YT2Local is a working but unstable Android downloader app that uses the `youtubedl-android` library (a yt-dlp + FFmpeg + Aria2c wrapper). The app currently crashes on startup due to a single missing manifest attribute (`android:extractNativeLibs="true"`) that prevents the Python native library from being extracted to disk. There is also a likely AGP version typo (`8.13.1` does not exist; should be `8.3.1`). These two bugs block all other work and must be fixed before any feature development. Once stable, the app already delivers the core zero-tap share-to-MP3 experience that distinguishes it from competitors — and that differentiator must be protected throughout the refactor.

The recommended path is a three-phase refactor. Phase 1 fixes the crash and build system. Phase 2 upgrades dependencies to modern versions and introduces Hilt DI as the foundation for testability. Phase 3 moves download execution from `viewModelScope` coroutines (which die on activity kill) into a proper foreground service — the single most impactful reliability improvement. Throughout all phases, the clean architecture pattern (UI → Domain → Data with a foreground service in the data layer) is the right structural target: it enables the download queue and persistent history features planned for later without requiring another architectural rewrite.

The primary risks are: (1) the initialization crash being masked by secondary issues during the fix, (2) ProGuard stripping Hilt-generated code in release builds if not verified early, and (3) the foreground service requiring all three Android 14 requirements simultaneously (permission, manifest attribute, `ServiceInfo` flag) — missing any one causes silent failure on modern devices. All three risks are fully documented and preventable with the right verification steps at each phase boundary.

---

## Key Findings

### Recommended Stack

The current stack is significantly outdated: Kotlin 1.9.0 (vs 2.3.10), Compose BOM 2023.08.00 (vs 2026.01.01), and Coil 2.5.0 (vs 3.4.0 with breaking API changes). The critical addition missing is a foreground service + WorkManager pairing for reliable downloads, Room for persistent history, and DataStore to replace SharedPreferences. Hilt 2.59.2 with KSP (replacing kapt) is the right DI choice because it provides compile-time safety and is already referenced in the project's planning docs.

**Core technologies:**
- Kotlin 2.3.10 — language; K2 compiler, Compose plugin now bundled, eliminates `kotlinCompilerExtensionVersion` config
- AGP 8.13.2 + Gradle 8.13 — build system; must be paired exactly to avoid sync failures
- Compose BOM 2026.01.01 — UI; single version bump aligns all Compose artifacts
- Hilt 2.59.2 + KSP 2.3.10-1.0.29 — DI; compile-time safety, required before foreground service
- WorkManager 2.11.0 + Foreground Service (dataSync) — background processing; WorkManager orchestrates queue, service executes yt-dlp
- Room 2.8.4 — persistent download history; KSP-only codegen, required for history survival across app kills
- DataStore 1.1.2 — format preference; replaces SharedPreferences, async-safe
- junkfood02/youtubedl-android 0.18.1 — download engine; all three artifacts (library, ffmpeg, aria2c) must match version
- Coil 3.4.0 — thumbnail loading; artifact ID changed from 2.x, full migration required

**Version compatibility critical note:** KSP version prefix must exactly match Kotlin version (`2.3.10-1.0.29`). Room 2.8 requires Kotlin language version >= 2.0. Coil 3.x and 2.x artifact IDs are different — cannot mix.

### Expected Features

The app already has most table-stakes features (URL input, MP3/MP4 download, progress indicator, share intent, completion notification, MediaStore output, in-session history, format preference persistence, metadata+thumbnail embed). The critical gaps are: human-readable error messages (currently shows raw stack traces), downloads surviving app backgrounding (currently killed on activity destroy), and the ability to open a downloaded file directly from history.

**Must have (table stakes — currently missing):**
- Human-readable error messages — raw exceptions destroy user trust; map `YoutubeDLException` to actionable strings
- Downloads survive app backgrounding — most common complaint in downloader apps; requires foreground service
- Open file from history — tap to play via `ACTION_VIEW` intent; MediaStore URI must be stored in `DownloadHistoryItem`
- Skip yt-dlp update button — first-launch hangs indefinitely on slow networks; add with 30-second auto-timeout

**Should have (competitive differentiators — planned):**
- Persistent download history — in-memory history dies on app kill; Room DB is the target
- Audio/video quality picker — 3-option picker (128/192/320kbps, 720p/1080p/Best) covers 95% of power user needs
- Download queue — requires foreground service stability first; defer to Phase 4

**Defer (anti-features to avoid):**
- Playlist/batch URL processing — 10x complexity for edge-case use; queue individual URLs instead
- Built-in media player — scope explosion; launch external player via `ACTION_VIEW`
- pause/resume downloads — yt-dlp not HTTP range-resumable; offer cancel + retry instead
- Custom yt-dlp passthrough / terminal mode — hard to support safely; quality picker covers the real use case

### Architecture Approach

The target architecture is three-layer clean architecture: UI layer (Compose + ViewModel with `StateFlow<DownloaderUiState>`) → Domain layer (pure Kotlin use cases, no Android imports) → Data layer (foreground service, Room, MediaStore, DataStore). The ViewModel communicates with the download service via a local `Binder` + `ServiceConnection`, relaying the service's `StateFlow<DownloadJob>` up through the repository. This pattern is well-established for Android download apps and scales cleanly to a download queue by adding a `Queue<DownloadJob>` inside the service without changing any other layer.

**Major components:**
1. `DownloadService` (foreground, dataSync type) — runs yt-dlp/FFmpeg coroutine, emits progress via `StateFlow`, posts completion notification, calls `stopSelf()` on done
2. `DownloadRepositoryImpl` — binds to service via `ServiceConnection`, relays `StateFlow`, inserts completed jobs into Room, serves as the single seam between domain and data
3. `MainViewModel` (@HiltViewModel) — owns `StateFlow<DownloaderUiState>` sealed class, orchestrates use cases, never touches `Context` or Android framework directly
4. `YtDlpDataSource` — wraps `YoutubeDL.execute()` in a testable class; init guard prevents duplicate calls
5. Room (`DownloadHistoryDao`, `YT2LocalDatabase`) — persistent history; `Flow<List<HistoryItem>>` auto-updates UI when data changes

**Build order:** domain models → Room + DataStore → data sources → foreground service → repository impl → Hilt modules → use cases → ViewModel → Compose UI

### Critical Pitfalls

1. **`extractNativeLibs="true"` missing from manifest** — `libpython.zip.so` is not a real ELF binary and must be extracted to disk on install. Without this attribute, yt-dlp init throws ENOENT and the app crashes on every launch. This is the current bug. Fix: add `android:extractNativeLibs="true"` to `<application>` in `AndroidManifest.xml`. Verify on a real device, not just in code review.

2. **AGP version `8.13.1` does not exist** — build fails at Gradle sync before any code runs. Fix: correct to `agp = "8.3.1"` in `libs.versions.toml`. Verify with a clean `./gradlew assembleDebug`.

3. **ProGuard strips Hilt-generated factories in release builds** — app works in debug, crashes in release with `ClassNotFoundException` for `_HiltModules_` classes. Fix: add Hilt ProGuard keep rules when introducing the dependency. Test a release build immediately after the first Hilt commit — not after the full refactor.

4. **Foreground service on Android 14 requires all three declarations simultaneously** — `FOREGROUND_SERVICE_DATA_SYNC` permission + `android:foregroundServiceType="dataSync"` on service element + `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` in `ServiceCompat.startForeground()`. Missing any one causes `ForegroundServiceStartNotAllowedException` on API 34 devices only.

5. **`viewModelScope` coroutines are cancelled when activity is killed** — downloads silently abort when user navigates away or system kills the task. Temp files accumulate in `filesDir/video_temp/`. Fix: move download execution to foreground service's coroutine scope. This is the single most impactful architectural change in the roadmap.

---

## Implications for Roadmap

Based on combined research, a four-phase structure is recommended. The dependency chain is strict: the crash must be fixed before anything else can be verified; Hilt must be added before the foreground service (DI makes the service injectable without manual singletons); the foreground service must be stable before a download queue is viable.

### Phase 1: Crash Fix and Build Stabilization

**Rationale:** The app is currently non-functional. Nothing else matters until it starts without crashing. This phase has the lowest risk (smallest diff, most isolated changes) and the highest user-facing impact. Fix both blocking issues before touching any other code.

**Delivers:** A working app that can complete at least one download end-to-end on a real device.

**Addresses:**
- Fix `android:extractNativeLibs="true"` in manifest (Pitfall 1)
- Fix AGP version typo `8.13.1` → `8.3.1` (Pitfall 2)
- Bump `youtubedl-android` 0.18.0 → 0.18.1 (all three artifacts together) (Pitfall 3)
- Add 30-second `withTimeout` around `updateYoutubeDL()` + "Skip update" button (Pitfall 8)
- Confirm AAB is not used; document APK-only constraint (Pitfall 9)

**Avoids:** Making other changes simultaneously — isolate variables so the crash cause is unambiguous.

**Research flag:** Standard patterns. No additional research needed. The fix is documented in the youtubedl-android issue tracker.

---

### Phase 2: Dependency Modernization and Architecture Foundation

**Rationale:** The current stack is 2+ years outdated. Kotlin 2.x changes how the Compose compiler plugin is applied (eliminating a known footgun). Hilt must be introduced now — before the foreground service — because injecting `VideoRepository` into a service without DI requires manual singleton management that creates tight coupling and is hard to test. This phase establishes the foundation everything else builds on.

**Delivers:** A modernized codebase with clean DI wiring, `StateFlow`-based ViewModel state, and DataStore replacing SharedPreferences. The app remains functionally identical to Phase 1 from the user's perspective.

**Uses:**
- Kotlin 2.3.10 (remove `composeOptions` block, add `org.jetbrains.kotlin.plugin.compose` plugin)
- Compose BOM 2026.01.01
- Coil 3.4.0 (new artifact ID: `io.coil-kt.coil3:coil-compose`)
- Hilt 2.59.2 + KSP 2.3.10-1.0.29 (replace kapt)
- DataStore 1.1.2 (replace SharedPreferences for format preference)
- Lifecycle 2.10.0

**Implements:** `@HiltAndroidApp` on Application, `@AndroidEntryPoint` on MainActivity, `@HiltViewModel` on MainViewModel, DI modules for Repository and data sources. Migrate ViewModel from scattered `mutableStateOf` fields to `StateFlow<DownloaderUiState>` sealed class.

**Avoids:** Skipping the release build test after Hilt introduction (Pitfall 4). The first deliverable of this phase must be a passing `./gradlew assembleRelease` with a full download test on device.

**Research flag:** Well-documented patterns. Kotlin 2.x + Compose migration has official Jetbrains guides. Hilt setup is official Google docs. No additional research-phase needed.

---

### Phase 3: Foreground Service and Reliability

**Rationale:** The current architecture has a fatal reliability flaw: downloads die when the user leaves the app. This is the single most impactful architectural change and the most commonly reported complaint in competing downloader apps. It requires Hilt to be in place first (Phase 2) because injecting `YtDlpDataSource` into the service cleanly requires the DI graph to exist. This phase also delivers the error handling improvements and the "open from history" feature, which together make the app feel production-quality.

**Delivers:** Downloads that complete even when the user switches apps, a persistent foreground notification with cancel action, human-readable error messages, and the ability to tap history items to open downloaded files.

**Uses:**
- Foreground Service (dataSync type) — declared in manifest with `FOREGROUND_SERVICE_DATA_SYNC` permission
- Room 2.8.4 — persistent download history; replaces in-memory list
- `DownloadService` with local `Binder` + `ServiceConnection` pattern
- `DownloadRepositoryImpl` as the single seam between domain and service

**Implements:** Full component set from ARCHITECTURE.md. `DownloadService` runs yt-dlp coroutine and emits `StateFlow<DownloadJob>`. Repository relays state to ViewModel. Room stores completed history. `DownloadHistoryItem` carries MediaStore URI for open-file support.

**Avoids:**
- Calling only `bindService()` without `startForegroundService()` first (Pitfall — Anti-Pattern 5)
- Missing all three Android 14 foreground service type declarations (Pitfall 5)
- Injecting ViewModel into Service (Integration Gotcha — use shared repository instead)

**Research flag:** Needs verification on Android 14 emulator (API 34) for foreground service type enforcement. The three-part declaration requirement (permission + manifest attribute + `ServiceInfo` flag) is easy to get partially wrong. Recommend a dedicated verification step before marking phase complete.

---

### Phase 4: Power User Features and Quality

**Rationale:** With a reliable, well-architected base, add the features that differentiate from competitors: quality picker, download queue, and persistent history (now that Room is already in place from Phase 3). These are safe to build incrementally because the architecture can accommodate them without structural changes.

**Delivers:** Audio quality picker (128/192/320kbps), video quality picker (720p/1080p/Best), download queue (sequential, one at a time), and unit tests for core logic.

**Uses:**
- Room (already in place) — history is already persistent, just expose it fully in UI
- WorkManager 2.11.0 — optionally wraps the service for queue orchestration; alternatively, `Queue<DownloadJob>` inside `DownloadService` is simpler for a sequential queue
- MockK 1.13.16 + Turbine 1.2.0 — test `detectPlatform()`, `sanitizeFileName()`, state transitions

**Implements:** Quality picker UI (3 options each), yt-dlp `-f` format string mapping in `YtDlpDataSource`, queue state in `DownloadService`, unit tests for domain and utility classes.

**Avoids:** Playlist/batch download (anti-feature), built-in player (anti-feature), pause/resume (not supported by yt-dlp download model).

**Research flag:** The download queue implementation choice (WorkManager chains vs in-service queue) is a fork point. WorkManager chaining has known issues with per-item failure handling. Simple in-service `Queue<DownloadJob>` is easier to reason about for a sequential single-download-at-a-time model. Recommend evaluating this during phase planning rather than pre-committing to WorkManager.

---

### Phase Ordering Rationale

- Phase 1 before everything: the app is non-functional without it; changing other things first would make the crash harder to diagnose
- Phase 2 before Phase 3: Hilt must exist before the foreground service; migrating `VideoRepository` to an injectable interface is cleaner to do without the service already in place
- Phase 3 before Phase 4: persistent history requires Room (Phase 3); download queue requires a stable foreground service (Phase 3); quality picker could theoretically be done earlier but belongs with the service refactor since format selection affects the yt-dlp command built in `YtDlpDataSource`
- Dependency bumps (Kotlin, Compose, Coil) in Phase 2 rather than Phase 1: isolate the crash fix first, then modernize

### Research Flags

Phases needing deeper research during planning:
- **Phase 3:** Foreground service Android 14 verification. The three-part declaration requirement is well-documented but easy to partially implement. Recommend testing on API 34 emulator as a phase gate.
- **Phase 4:** WorkManager chaining vs in-service queue for download queue. Both approaches are valid; the right choice depends on whether cross-process persistence of the queue is needed. Evaluate at phase planning time.

Phases with standard patterns (skip research-phase):
- **Phase 1:** The crash fix is confirmed in the youtubedl-android issue tracker. Execution is mechanical.
- **Phase 2:** Kotlin 2.x migration and Hilt setup follow official guides exactly. No ambiguity.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified against official release notes and Maven repositories. Compatibility matrix cross-checked. Version constraints documented. |
| Features | MEDIUM-HIGH | Table stakes verified against two reference apps (YTDLnis, Seal). Anti-features well-reasoned. Competitor feature set from README/changelog — not exhaustive. |
| Architecture | HIGH | Follows official Android Architecture Guide, official Foreground Service docs, and official Hilt docs. Local binder pattern confirmed by bound services guide. |
| Pitfalls | HIGH (library-specific), MEDIUM (patterns) | `extractNativeLibs` and AAB failures verified in youtubedl-android issue tracker. Foreground service type requirement from official Android 14 docs. Hilt ProGuard behavior from R8 official docs and Hilt migration guide. |

**Overall confidence:** HIGH

### Gaps to Address

- **AGP version ambiguity in PITFALLS.md:** PITFALLS.md recommends fixing `8.13.1` → `8.3.1` while STACK.md recommends upgrading to `8.13.2`. These are not contradictory (fix the typo, then upgrade), but the roadmap phase plan should be explicit: Phase 1 fixes to `8.3.1` (matches current Kotlin/Compose), Phase 2 upgrades to `8.13.2` with full dependency modernization. Validate that `8.3.1` is the correct typo target during Phase 1 — it is possible the intent was `8.1.3` or another version.

- **`network_security_config.xml` cleartext traffic rules:** PITFALLS.md flags that yt-dlp may attempt HTTP connections for some platforms. The current `network_security_config.xml` content was not reviewed during research. Should be verified during Phase 1 to confirm it does not block yt-dlp's network access.

- **MediaStore API 26/27/28 fallback:** Architecture research notes that `RELATIVE_PATH` requires API 29+, and min SDK 26 means some devices need a `File` path fallback. Current implementation may already handle this; should be verified during Phase 3 when `MediaStoreDataSource` is extracted as a separate class.

- **WorkManager vs in-service queue for Phase 4:** Intentionally left unresolved. The right choice depends on whether cross-process queue persistence is a requirement. Evaluate at Phase 4 planning time.

---

## Sources

### Primary (HIGH confidence)
- [Jetpack Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — version confirmation, 2026.01.01 is latest stable
- [AGP 8.3.0 Release Notes](https://developer.android.com/build/releases/past-releases/agp-8-3-0-release-notes) — AGP version existence verification
- [Kotlin 2.3.0 Released](https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/) — 2.3.10 stable confirmation
- [Android App Architecture](https://developer.android.com/topic/architecture) — layer separation, component responsibilities
- [Foreground Services official guide](https://developer.android.com/develop/background-work/services/foreground-services) — foreground service lifecycle
- [Bound Services official guide](https://developer.android.com/develop/background-work/services/bound-services) — local binder pattern
- [Foreground service types required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) — three-part declaration requirement
- [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android) — setup and component scoping
- [Room persistence library](https://developer.android.com/training/data-storage/room) — KSP-only codegen, Kotlin 2.0 requirement
- [WorkManager Releases](https://github.com/androidx-releases/WorkManager/releases) — 2.11.0 confirmation
- [youtubedl-android Issue #105](https://github.com/yausername/youtubedl-android/issues/105) — `extractNativeLibs=true` requirement for Python init
- [youtubedl-android Issue #116](https://github.com/yausername/youtubedl-android/issues/116) — AAB build failure with `.zip.so` files
- [youtubedl-android Releases](https://github.com/yausername/youtubedl-android/releases) — 0.18.1 latest version
- [R8 Compatibility FAQ](https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md) — ProGuard/R8 behavior with generated code
- [KSP vs kapt migration](https://developer.android.com/build/migrate-to-ksp) — KSP preferred, kapt deprecation
- [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) — StateFlow for ViewModel state
- [Dagger Hilt: Android Entry Points](https://dagger.dev/hilt/android-entry-point.html) — @HiltAndroidApp, @AndroidEntryPoint requirements

### Secondary (MEDIUM confidence)
- [Seal GitHub — JunkFood02/Seal](https://github.com/JunkFood02/Seal) — reference implementation architecture, Koin usage
- [YTDLnis GitHub — deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis) — competitor feature set analysis
- [Modern Android Architecture 2025](https://medium.com/@androidlab/modern-android-app-architecture-in-2025-mvvm-mvi-and-clean-architecture-with-jetpack-compose-c0df3c727334) — community source, consistent with official docs
- [Hilt vs Koin comparison (droidcon 2025)](https://www.droidcon.com/2025/11/26/hilt-vs-koin-the-hidden-cost-of-runtime-injection-and-why-compile-time-di-wins/) — DI choice rationale
- [Foreground Service vs WorkManager](https://medium.com/@amar90aqi/foreground-service-vs-workmanager-in-android-choosing-the-right-tool-for-background-tasks-32c1242f9898) — background task comparison
- [Why Android 14's Foreground Service Requirements Break Apps](https://medium.com/gravel-engineering/why-android-14s-foreground-service-requirements-might-break-your-app-and-how-to-fix-it-c1cbcf469b69) — practical implementation notes

### Tertiary (LOW confidence)
- [WorkManager sequential queue failure handling](https://medium.com/@amar90aqi/foreground-service-vs-workmanager-in-android-choosing-the-right-tool-for-background-tasks-32c1242f9898) — known issues with chained work item failure; needs validation during Phase 4 planning
- [Compose + ViewModel mutableState thread safety](https://slack-chats.kotlinlang.org/t/2206468/compose-viewmodel-mutablestate-combination-is-error-prone-du) — community discussion; consistent with official docs but single source

---

*Research completed: 2026-02-27*
*Ready for roadmap: yes*
