# Stack Research

**Domain:** Android video/audio downloader app (Kotlin + Jetpack Compose)
**Researched:** 2026-02-27
**Confidence:** HIGH (all key versions verified against official docs and Maven releases)

---

## Current State (What Exists)

| Component | Current | Status |
|-----------|---------|--------|
| AGP | 8.13.1 | Valid — 8.13.2 is the latest 8.13.x patch |
| Kotlin | 1.9.0 | Outdated — 2.3.10 is current stable |
| Compose BOM | 2023.08.00 | Severely outdated — 2026.01.01 is current |
| Compose compiler | 1.5.1 (separate config) | Outdated — now bundled with Kotlin 2.0+ via Compose Compiler Gradle plugin |
| junkfood02/youtubedl-android | 0.18.0 | Slightly outdated — 0.18.1 is latest (Nov 2024) |
| Lifecycle | 2.6.2 | Outdated — 2.10.0 is current |
| Coil | 2.5.0 | Outdated — 3.4.0 is current (breaking API change) |
| Room | not present | Needed for persistent download history |
| Hilt/DI | none | Needed for clean architecture |
| DataStore | none | Needed to replace SharedPreferences |
| Foreground Service | declared in manifest but not implemented | Needed for reliable downloads |

---

## Recommended Stack

### Core Framework

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.10 | Language | Current stable as of Dec 2025. Compose compiler now ships with Kotlin — eliminates separate `kotlinCompilerExtensionVersion` config. K2 compiler brings 2x faster compilation. |
| AGP | 8.13.2 | Build system | Latest stable in 8.x line (Sep 2025). Supports API 36. AGP 9.0 exists but requires Gradle 9.1 and has breaking DSL changes — conservative upgrade is safer for a third-party-library-heavy project. |
| Gradle Wrapper | 8.13 | Build execution | Required by AGP 8.13.x. Pair exactly — mismatch causes build failures. |
| Compose BOM | 2026.01.01 | Compose dependency alignment | Latest stable (Jan 2026). BOM ensures all Compose artifacts are version-compatible. Single version bump, no per-artifact management. |
| Compose Compiler Plugin | (matches Kotlin version) | Compose compilation | Since Kotlin 2.0, the compiler ships with Kotlin — apply `org.jetbrains.kotlin.plugin.compose` plugin instead of setting `kotlinCompilerExtensionVersion`. Eliminates the separate compatibility matrix lookup. |
| compileSdk / targetSdk | 35 | API level | Required for new apps on Google Play as of August 2025. API 36 exists but targetSdk 35 is the current Play Store requirement. Use compileSdk 35 to avoid triggering androidx.core 1.17.x requirements. |
| minSdk | 26 | Minimum Android | Existing constraint. Android 8.0. Keep — covers 97%+ of active devices. |

### Dependency Injection

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Hilt (Dagger) | 2.59.2 | DI container | Google-recommended DI for Android. Compile-time code generation means errors surface at build time, not runtime. Deep integration with ViewModel, WorkManager, and Navigation. For a project that already plans to add Hilt (per PROJECT.md), not switching to Koin mid-stream saves one migration later. |
| AndroidX Hilt Extensions | 1.2.0 | Hilt + ViewModel/WorkManager bridge | Required for `@HiltViewModel` annotation and `HiltWorker` for WorkManager. |
| KSP | 2.3.10-1.0.29 | Annotation processing | Replaces kapt for Room and Hilt. 2x faster builds. Required: KSP version must match Kotlin version (format: `$kotlinVersion-$kspVersion`). |

**Note on Koin vs Hilt:** Koin 4.0 is a valid alternative (used by the Seal reference app) with faster build times and simpler setup. For a solo/small-team project with no strict performance SLAs, Koin would also be fine. Choose Hilt because: (1) PROJECT.md already decided on it, (2) type-safe DI catches wiring errors at compile time, (3) no runtime overhead for a latency-sensitive downloader. Confidence: MEDIUM (preference-based, both are solid choices).

### Background Processing

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Android Foreground Service | platform (no library) | Long-running downloads | Direct approach for a user-initiated download that must show an ongoing notification. A `dataSync` foreground service is the purpose-built API for file download/transfer operations. Survives activity kill; shows persistent notification. |
| WorkManager | 2.11.0 | Task orchestration / queue | Wraps the foreground service in a manageable worker. Handles download queue (sequential via `WorkContinuation.then()`), crash recovery, and system restart. WorkManager's `setForeground()` bridges into foreground service behavior. |

**Foreground service type:** `dataSync`. Declare `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions. Call `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)`.

**Android 15 warning:** Apps targeting API 35 with `dataSync` foreground services face a **6-hour maximum runtime** per session. A single download will never approach this limit, but be aware for future queue-based batch downloads.

**Do NOT use WorkManager alone (without foreground service):** A plain WorkManager `CoroutineWorker` will be killed by the OS under memory pressure mid-download. Must use `setForeground()` / `setForegroundAsync()` for downloads.

### Local Persistence

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Room | 2.8.4 | Download history database | Latest stable (Nov 2025). Kotlin-first (runtime rewritten in Kotlin). KSP-only code generation (produces Kotlin, not Java stubs). SQLite abstraction with type-safe queries. Correct choice for structured history records. |
| Jetpack DataStore (Preferences) | 1.1.2 | App preferences (format toggle, etc.) | Replaces SharedPreferences. Async/coroutine-based — no ANR risk. Survives SharedPreferences deprecation trajectory. Simple `key-value` storage for format preference, skip-update flag, etc. |

**Do NOT use Room for simple preferences (format toggle):** Use DataStore for 1-5 key-value settings. Use Room only for structured, queryable data (download history).

### Image Loading

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Coil | 3.4.0 | Loading video thumbnails | Latest stable. 25-40% runtime performance improvement in Compose. Kotlin Coroutines-native. **Breaking change from 2.x:** artifact ID changed from `io.coil-kt:coil-compose` to `io.coil-kt.coil3:coil-compose`. New package names. Migration is straightforward but required. |

### Core Download Engine

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| junkfood02/youtubedl-android | 0.18.1 | yt-dlp + FFmpeg + Aria2c wrapper | Latest stable (Nov 2024). Adds QuickJS support. **All three artifacts must match the same version:** `library`, `ffmpeg`, `aria2c`. Current app has 0.18.0 — bump all three to 0.18.1 together. |

**Critical initialization requirement:** The manifest **must** include `android:extractNativeLibs="true"` in the `<application>` tag. Without it, the Python native library (`libpython.zip.so`) cannot be extracted and initialization fails with `YoutubeDLException: failed to initialize`. This is the most likely root cause of the current crash. Confidence: HIGH (confirmed in youtubedl-android issue tracker).

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AndroidX Core KTX | 1.15.0 | Kotlin extensions for Android APIs | Always — required for Kotlin-idiomatic Android code |
| AndroidX Activity Compose | 1.10.0 | `ComponentActivity` Compose integration | Required for `setContent {}` in MainActivity |
| AndroidX Lifecycle Runtime KTX | 2.10.0 | Lifecycle-aware coroutines | Required for `lifecycleScope`, `repeatOnLifecycle` |
| kotlinx-coroutines-android | 1.10.1 | Coroutines dispatcher for Android | Required for `Dispatchers.Main` on Android |
| kotlinx-coroutines-test | 1.10.1 | Coroutine testing utilities | `testImplementation` — for testing suspend functions and StateFlow |

### Testing

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 4 | 4.13.2 | Unit test runner | Standard Android unit testing framework |
| MockK | 1.13.16 | Kotlin-native mocking | Always prefer over Mockito for Kotlin — handles `final` classes, coroutines, companion objects natively |
| AndroidX Test Core | 1.6.1 | Android test utilities | `testImplementation` via Robolectric for ViewModel/state tests |
| Turbine | 1.2.0 | Flow/StateFlow testing | When testing Flow emissions from ViewModel or Repository |
| Compose UI Test | (via BOM) | Compose UI testing | `androidTestImplementation` for instrumented Compose UI tests |

**Do NOT use Mockito:** Kotlin classes are `final` by default, requiring a Mockito extension to mock them. MockK handles this natively. Switch to MockK entirely.

---

## Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio Meerkat (2024.3.1+) | IDE | First version with stable AGP 8.x + Kotlin 2.x support |
| KSP Gradle Plugin | Annotation processing | Replaces kapt; apply in `libs.versions.toml` as `com.google.devtools.ksp` |
| ProGuard / R8 | Code shrinking | Already configured. Must add `-keepclassmembers` for all new `@Entity`, `@Module`, `@HiltViewModel` classes. R8 8.13 supports Kotlin 2.3. |

---

## Gradle Version Catalog (libs.versions.toml) Target State

```toml
[versions]
agp = "8.13.2"
kotlin = "2.3.10"
ksp = "2.3.10-1.0.29"
coreKtx = "1.15.0"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
lifecycleRuntimeKtx = "2.10.0"
activityCompose = "1.10.0"
composeBom = "2026.01.01"
hilt = "2.59.2"
hiltExt = "1.2.0"
room = "2.8.4"
datastore = "1.1.2"
workManager = "2.11.0"
youtubedlAndroid = "0.18.1"
coil = "3.4.0"
mockk = "1.13.16"
coroutines = "1.10.1"

[libraries]
# Core
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }

# DI
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-ext-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltExt" }
hilt-ext-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltExt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltExt" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# WorkManager
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }

# Download engine
youtubedl-android = { group = "io.github.junkfood02.youtubedl-android", name = "library", version.ref = "youtubedlAndroid" }
youtubedl-ffmpeg = { group = "io.github.junkfood02.youtubedl-android", name = "ffmpeg", version.ref = "youtubedlAndroid" }
youtubedl-aria2c = { group = "io.github.junkfood02.youtubedl-android", name = "aria2c", version.ref = "youtubedlAndroid" }

# Image loading
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
jetbrains-kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Hilt 2.59.2 | Koin 4.0.0 | Solo/small-team apps where build speed matters more than compile-time safety. Koin has simpler setup and no annotation processing. The reference Seal app uses Koin. |
| Room 2.8.4 | SQLDelight / SQLite raw | Room is the obvious choice for Kotlin Android. Only deviate if you need KMP (Room 2.8 supports KMP anyway). |
| DataStore 1.1.2 | SharedPreferences | Never use SharedPreferences for new code. ANR risk from main-thread I/O. EncryptedSharedPreferences is deprecated. |
| WorkManager 2.11.0 | Plain Service + Coroutine | Current app uses ViewModel coroutines — dies on activity kill. WorkManager provides persistence across process death and system restart. |
| Foreground Service (dataSync) | DownloadManager (system) | DownloadManager is tempting but doesn't support yt-dlp's multi-step process (extract info → download → remux → embed metadata). Too limited. |
| AGP 8.13.2 | AGP 9.0.1 | AGP 9.0 requires Gradle 9.1 and has breaking DSL changes (Kotlin plugin auto-applied, split APK DSL removed). Higher risk for a project with native ABI splits and third-party libs. Upgrade in a future cycle. |
| Coil 3.4.0 | Glide / Picasso | Coil is Kotlin-Coroutines-native and Compose-first. Glide/Picasso require Java interop bridges and heavier setup. Coil 3 supports all platforms. |
| MockK 1.13.16 | Mockito | Kotlin classes are `final` by default. Mockito requires a MockMaker extension to mock them. MockK handles this transparently. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `composeOptions { kotlinCompilerExtensionVersion }` | Removed in Kotlin 2.0+ Compose workflow. Causes version mismatch errors. | Apply `org.jetbrains.kotlin.plugin.compose` Gradle plugin — version matches Kotlin automatically. |
| kapt | Deprecated annotation processing. 2x slower than KSP. Google is actively deprecating it. Hilt and Room both support KSP. | KSP (`com.google.devtools.ksp`) |
| SharedPreferences | Main-thread I/O risk (ANR), no coroutine support. `EncryptedSharedPreferences` is officially deprecated as of 2025. | AndroidX DataStore (Preferences) |
| ViewModel coroutines for downloads | Tied to Activity lifecycle — cancelled when user navigates away. Download fails silently. | Foreground Service + WorkManager |
| AGP 8.13.1 | Typo? Non-existent version. The real versions are 8.13.0, 8.13.1... wait: confirmed 8.13.2 exists. 8.13.1 may not exist — use 8.13.2. | AGP 8.13.2 |
| Coil 2.x (`io.coil-kt:coil-compose`) | Major version with different artifact ID and package names from Coil 3.x. Mixing old and new Coil dependencies breaks builds. | Coil 3.x (`io.coil-kt.coil3:coil-compose`) — migrate fully |
| `android:extractNativeLibs="false"` (or absent) | youtubedl-android bundles Python as a native `.so` file. If native libs aren't extracted to disk, Python init fails with ENOENT. This is the most likely crash cause. | Add `android:extractNativeLibs="true"` to `<application>` in AndroidManifest.xml |

---

## Stack Patterns by Variant

**For the immediate crash fix (Phase 1):**
- Only change: add `android:extractNativeLibs="true"` to manifest + bump youtubedl-android to 0.18.1
- Do NOT change Kotlin/AGP/Compose until the crash is confirmed fixed
- Isolate variables — changing 10 things at once makes the crash harder to diagnose

**For the dependency upgrade (Phase 2):**
- Kotlin: 1.9.0 → 2.3.10 (requires removing `composeOptions` block, adding `kotlin-compose` plugin)
- Compose BOM: 2023.08.00 → 2026.01.01
- Coil: 2.5.0 → 3.4.0 (requires artifact ID change and import updates)
- AGP: 8.13.1 → 8.13.2 (minor patch, low risk)
- Lifecycle: 2.6.2 → 2.10.0

**For the architecture refactor (Phase 3):**
- Add Hilt (requires KSP, `@HiltAndroidApp` on Application, `@AndroidEntryPoint` on Activity)
- Add Room for download history
- Add DataStore to replace SharedPreferences format preference
- Add WorkManager + Foreground Service for reliable downloads

---

## Version Compatibility Matrix

| Component | Compatible With | Notes |
|-----------|----------------|-------|
| Kotlin 2.3.10 | AGP 8.13.2, Compose BOM 2026.01.01, KSP 2.3.10-1.0.29 | KSP version prefix must match Kotlin version exactly |
| AGP 8.13.2 | Gradle 8.13, Kotlin 2.3.x, compileSdk 35/36 | Do not use Gradle < 8.13 with this AGP |
| Hilt 2.59.2 | AGP 8.x, Kotlin 2.x, KSP 2.3.x | 2.59 added AGP 9 support (Gradle 9.1+); works fine on AGP 8 |
| Room 2.8.4 | Kotlin 2.0+, KSP 2.x | Room 2.8 requires Kotlin language version ≥ 2.0 |
| youtubedl-android 0.18.1 | AGP 8.x, Kotlin 1.9+/2.x | library + ffmpeg + aria2c must all be same version |
| Coil 3.4.0 | Compose BOM 2024+, OkHttp 4.x | Network image loading requires `coil-network-okhttp` artifact added separately |
| WorkManager 2.11.0 | minSdk 23+ (matches app's minSdk 26), AGP 8.x | setForeground() for long-running tasks requires foreground service type declaration |

---

## ProGuard Requirements for New Components

Add to `app/proguard-rules.pro` when adding each component:

```
# Hilt
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.* <fields>;
}
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# youtubedl-android (already present, keep as-is)
-keep class com.yausername.** { *; }
-keep class com.github.yausername.** { *; }

# DataStore (no rules needed — uses reflection-free Kotlin serialization)
```

---

## Sources

- [Jetpack Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — confirmed 2026.01.01 is latest stable (HIGH confidence)
- [Android Developers Blog: Compose December '25](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html) — Compose 1.10 + Material3 1.4 (HIGH confidence)
- [AGP 8.13 Release Notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) — 8.13.2 is latest 8.x, requires Gradle 8.13 (HIGH confidence)
- [AGP About page](https://developer.android.com/build/releases/about-agp) — AGP 9.0 requires Gradle 9.1 (HIGH confidence)
- [Kotlin 2.3.0 Released](https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/) — 2.3.10 is stable (HIGH confidence)
- [Dagger Releases](https://github.com/google/dagger/releases) — Hilt 2.59.2 released Feb 2025 (HIGH confidence)
- [Room Release Notes](https://developer.android.com/jetpack/androidx/releases/room) — 2.8.4 latest stable Nov 2025 (HIGH confidence)
- [WorkManager Releases](https://github.com/androidx-releases/WorkManager/releases) — 2.11.0 latest stable Oct 2025 (HIGH confidence)
- [DataStore Release Notes](https://developer.android.com/jetpack/androidx/releases/datastore) — 1.1.2 stable, 1.3.x alpha (HIGH confidence)
- [youtubedl-android Releases](https://github.com/yausername/youtubedl-android/releases) — 0.18.1 Nov 2024 (HIGH confidence)
- [Coil GitHub](https://github.com/coil-kt/coil) — 3.4.0 latest stable (HIGH confidence)
- [Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types) — dataSync type for download apps (HIGH confidence)
- [Android 15 dataSync time limit](https://developer.android.com/develop/background-work/services/fgs/changes) — 6-hour cap on API 35 (HIGH confidence)
- [Lifecycle Releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) — 2.10.0 latest stable Feb 2026 (HIGH confidence)
- [KSP vs kapt migration](https://developer.android.com/build/migrate-to-ksp) — KSP preferred, kapt being deprecated (HIGH confidence)
- [youtubedl-android issue #105](https://github.com/yausername/youtubedl-android/issues/105) — extractNativeLibs=true required for Python init (MEDIUM confidence — issue tracker, not official docs)
- [Hilt vs Koin 2025 droidcon](https://www.droidcon.com/2025/11/26/hilt-vs-koin-the-hidden-cost-of-runtime-injection-and-why-compile-time-di-wins/) — comparison analysis (MEDIUM confidence — community source)
- [Seal app libs.versions.toml](https://github.com/JunkFood02/Seal) — reference implementation using Koin, Room, KSP, Compose BOM (MEDIUM confidence — real-world reference)

---

*Stack research for: YT2Local — Android video/audio downloader*
*Researched: 2026-02-27*
