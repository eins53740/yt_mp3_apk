# Phase 02: Foundation - Research

**Researched:** 2026-03-01
**Domain:** Android dependency modernization (Kotlin 2.x, KSP, Compose BOM), Hilt DI, StateFlow sealed-class ViewModel state
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| MOD-01 | Dependencies updated — Kotlin 2.x, Compose BOM 2026, Coil 3.x, KSP | Version table + Compose compiler plugin migration steps in Standard Stack section |
| ARCH-01 | All dependencies injected via Hilt (ViewModel, Repository, Service) | Hilt setup pattern + module examples + ProGuard pitfalls in Architecture Patterns section |
| ARCH-02 | UI state managed via single StateFlow sealed class (replace scattered mutableStateOf) | StateFlow sealed class pattern + Compose collection method in Architecture Patterns section |
</phase_requirements>

---

## Summary

Phase 02 bundles three distinct concerns into one phase: (1) bumping core dependencies to modern stable versions, (2) introducing Hilt DI, and (3) replacing scattered `mutableStateOf` fields with a single `StateFlow<DownloaderUiState>` sealed class. All three are mechanical rewrites with no functional change — the app must behave identically before and after.

The dependency upgrade is the highest-risk step because Kotlin 2.x introduces a mandatory change to how the Compose compiler is configured: the `composeOptions { kotlinCompilerExtensionVersion }` block in `app/build.gradle.kts` must be replaced by applying the `org.jetbrains.kotlin.plugin.compose` Gradle plugin. This is a build-break change if done incorrectly. Staying on AGP 8.13.2 (already current in the project) is correct — it supports Kotlin 2.3 natively and requires Gradle 8.13 (the wrapper is already at 8.13).

Hilt adds boilerplate (Application annotation, @AndroidEntryPoint on MainActivity, @HiltViewModel on ViewModel, a module for VideoRepository) and requires KSP for code generation. The current `MainViewModel` extends `AndroidViewModel` to get `Application` context — with Hilt this becomes `@ApplicationContext context: Context` in the constructor, and the class should revert to plain `ViewModel`. ProGuard is a verified concern: a known Hilt/AGP 8.9+ issue strips @HiltViewModel constructors in release builds; a manual keep rule is required.

The StateFlow migration converts ~10 scattered `var x by mutableStateOf(...)` fields in `MainViewModel` into a single `data class DownloaderUiState(...)` exposed as `StateFlow<DownloaderUiState>`. Compose collects it with `collectAsStateWithLifecycle()`. This is the lowest-risk sub-task but requires the most ViewModel lines changed.

**Primary recommendation:** Execute the three sub-tasks as separate plans in order (02-01 deps, 02-02 Hilt, 02-03 StateFlow) so each can be verified independently. Do not combine.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.1.21 | Language; enables K2 compiler | Stable 2.1.x release; compatible with AGP 8.13.2 (requires AGP 8.6+) |
| KSP | 2.1.21-2.0.1 | Annotation processing for Hilt | Replaces kapt; faster, K2-compatible; version prefix must match Kotlin |
| Compose BOM | 2026.02.00 | Pin all Compose library versions | Maps to material3 1.4.0, runtime/ui 1.10.3; eliminates per-library version management |
| `org.jetbrains.kotlin.plugin.compose` | same as Kotlin (2.1.21) | Compose compiler plugin (Kotlin 2.0+) | Replaces the old `composeOptions` block; ships with Kotlin, always compatible |
| Hilt | 2.57.1 | Dependency injection | Google-recommended DI for Android; integrates with ViewModel lifecycle automatically |
| `hilt-android-compiler` | 2.57.1 (via KSP) | Code generation for Hilt | Must match hilt-android version exactly |
| `lifecycle-runtime-compose` | 2.10.0 | `collectAsStateWithLifecycle()` | Required to safely collect StateFlow in Compose with lifecycle awareness |
| Coil 3 | 3.4.0 | Image loading in Compose | Major rewrite from 2.x; new Maven group `io.coil-kt.coil3`; multiplatform-ready |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `hilt-navigation-compose` | 1.3.0 | `hiltViewModel()` in composables | Needed when Compose is the UI and @AndroidEntryPoint is on the Activity |
| `hilt-android-testing` | 2.57.1 | Hilt test rules | Add when unit/integration tests are introduced (Phase 2 defers tests) |
| `datastore-preferences` | 1.1.x | Async key-value storage | MOD-01 mentions DataStore; SharedPreferences still works but DataStore is modern standard |

> **DataStore note:** MOD-01 says "DataStore" but the plan list only says "02-01: Bump ... DataStore". The current code uses SharedPreferences for format preference (one boolean). Migrating to DataStore in 02-01 is optional — it adds coroutine complexity without functional gain. Flag this for planner discretion.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hilt | Koin | Koin is runtime DI (no code gen, no KSP), simpler setup, but less Android-idiomatic; Hilt is Google-recommended |
| StateFlow sealed class | `mutableStateOf` (current) | Current approach is Compose-coupled, not testable outside Compose, can't be safely exposed as read-only |
| Coil 3 | Coil 2 (current) | Coil 2 is still functional but unmaintained; Coil 3 has breaking changes (new Maven group, network artifact required) |

**Installation (libs.versions.toml additions):**

```toml
[versions]
kotlin = "2.1.21"
ksp = "2.1.21-2.0.1"
composeBom = "2026.02.00"
hilt = "2.57.1"
hiltNavigationCompose = "1.3.0"
lifecycleRuntimeCompose = "2.10.0"
coil = "3.4.0"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

[plugins]
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## Architecture Patterns

### Recommended Project Structure

No structural changes needed. All files remain in the flat package `com.example.yt2local/`. Hilt adds one new file: `di/AppModule.kt`.

```
app/src/main/java/com/example/yt2local/
├── MainActivity.kt          # Add @AndroidEntryPoint
├── MainScreen.kt            # Change: collectAsStateWithLifecycle(), hiltViewModel()
├── MainViewModel.kt         # Rewrite: @HiltViewModel, StateFlow<DownloaderUiState>
├── VideoRepository.kt       # Add @Inject constructor
├── YT2LocalApplication.kt   # Add @HiltAndroidApp
├── Theme.kt                 # Unchanged
└── di/
    └── AppModule.kt         # New: @Module @InstallIn(SingletonComponent) for VideoRepository
```

### Pattern 1: Kotlin 2.x Compose Compiler Plugin Migration

**What:** The `composeOptions { kotlinCompilerExtensionVersion }` block is removed and replaced by applying the `org.jetbrains.kotlin.plugin.compose` Gradle plugin.

**Before (Kotlin 1.9.x):**
```kotlin
// app/build.gradle.kts
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.1"
}
```

**After (Kotlin 2.x):**
```kotlin
// root build.gradle.kts
plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

// app/build.gradle.kts
plugins {
    alias(libs.plugins.compose.compiler)   // NEW — replaces composeOptions block
    alias(libs.plugins.hilt.android)       // NEW
    alias(libs.plugins.ksp)                // NEW
}
// Remove the entire composeOptions {} block
```

Source: [Compose compiler migration guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)

### Pattern 2: Hilt Application Setup

**What:** `@HiltAndroidApp` on the Application class triggers Hilt's code generation and creates the root component.

```kotlin
// YT2LocalApplication.kt
@HiltAndroidApp
class YT2LocalApplication : Application() {
    // existing notification channel setup unchanged
}
```

### Pattern 3: Hilt Module for VideoRepository

**What:** A `@Module` provides `VideoRepository` as a singleton, injecting `@ApplicationContext`.

```kotlin
// di/AppModule.kt
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVideoRepository(@ApplicationContext context: Context): VideoRepository {
        return VideoRepository(context)
    }
}
```

Source: [Dependency injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)

### Pattern 4: @HiltViewModel with ApplicationContext

**What:** Replace `AndroidViewModel(application)` with plain `ViewModel()` + injected context. Hilt provides `@ApplicationContext` as a pre-defined binding.

```kotlin
// MainViewModel.kt
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: VideoRepository,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle  // optional, but available for free
) : ViewModel() {
    // Remove: private val repository = VideoRepository(application)
    // Remove: extends AndroidViewModel
}
```

**In MainActivity:**
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()  // unchanged call, Hilt wires it
    // ...
}
```

**In MainScreen (Compose):**
```kotlin
// Option A: pass ViewModel from Activity (current pattern, works fine)
@Composable
fun MainScreen(viewModel: MainViewModel) { ... }

// Option B: use hiltViewModel() directly in composable (cleaner for navigation)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) { ... }
```

Source: [Use Hilt with other Jetpack libraries](https://developer.android.com/training/dependency-injection/hilt-jetpack)

### Pattern 5: StateFlow Sealed Class UiState

**What:** Replace 10 scattered `mutableStateOf` fields with a single `data class DownloaderUiState` exposed as `StateFlow`.

```kotlin
// MainViewModel.kt

data class DownloaderUiState(
    val appState: AppState = AppState.INITIALIZING,
    val url: String = "",
    val isAudio: Boolean = true,
    val statusMessage: String = "Initializing...",
    val downloadProgress: Float = 0f,
    val progressStatus: String = "",
    val detectedPlatform: String = "",
    val downloadHistory: List<DownloadHistoryItem> = emptyList(),
    val ytDlpVersion: String = "",
    val autoDownloadPending: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(...) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    // Update pattern using copy()
    private fun updateState(block: DownloaderUiState.() -> DownloaderUiState) {
        _uiState.update { it.block() }
    }

    fun onUrlChange(newUrl: String) {
        _uiState.update { it.copy(
            url = newUrl,
            detectedPlatform = if (newUrl.isNotBlank()) repository.detectPlatform(newUrl) else ""
        )}
    }
}
```

**In MainScreen (Compose):**
```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Access all state via uiState.fieldName
    // Replace: viewModel.url, viewModel.appState, etc.
    // With:    uiState.url, uiState.appState, etc.
}
```

Source: [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow), [collectAsStateWithLifecycle](https://medium.com/@android-world/power-of-collectasstatewithlifecycle-in-jetpack-compose-aa701f2270ef)

### Anti-Patterns to Avoid

- **Mixing mutableStateOf and StateFlow in the same ViewModel:** After migration, remove ALL `var x by mutableStateOf(...)` fields — don't leave any behind.
- **Using `collectAsState()` instead of `collectAsStateWithLifecycle()`:** The lifecycle-aware version pauses collection when app is backgrounded, saving resources. Requires `lifecycle-runtime-compose` dependency.
- **Injecting ActivityContext into a Singleton-scoped class:** `VideoRepository` is `@Singleton`. It must only receive `@ApplicationContext`, never `@ActivityContext`.
- **Forgetting `@AndroidEntryPoint` on MainActivity:** Without it, Hilt cannot inject the ViewModel and the app crashes with `IllegalStateException`.
- **Using `kapt` instead of `ksp` for Hilt code generation:** `kapt` does not support K2 compiler (Kotlin 2.x). Must use `ksp`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ViewModel creation with deps | Custom ViewModelFactory | `@HiltViewModel` + `by viewModels()` | Factory must handle config changes, SavedState; Hilt does this correctly |
| DI graph | Manual singletons / `object` | Hilt `@Module` + `@Provides` | Manual singletons don't survive test isolation, can't be replaced in tests |
| Lifecycle-aware state collection | `LaunchedEffect` + `collect` | `collectAsStateWithLifecycle()` | `collectAsStateWithLifecycle` correctly pauses on lifecycle stop; manual approach has subtle leaks |
| KSP annotation processor | kapt | KSP | kapt is deprecated for K2; KSP is 2x faster and K2-compatible |

**Key insight:** Hilt's complexity (annotations, modules, code gen) is paid upfront; the payoff is that Phase 3's foreground service and Phase 4's download queue can be injected cleanly without restructuring.

---

## Common Pitfalls

### Pitfall 1: Hilt @HiltViewModel Constructor Stripped by R8 in Release Builds

**What goes wrong:** Release build runs fine but crashes at runtime with `NoSuchMethodException` when Hilt tries to construct the ViewModel.
**Why it happens:** A rule that kept `@HiltViewModel`-annotated constructors was removed from Hilt's bundled ProGuard rules in Hilt 2.56.x. R8 strips the generated constructor.
**How to avoid:** Add to `app/proguard-rules.pro`:
```proguard
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
```
**Warning signs:** App works in debug, crashes on first screen load in release.

Source: [Hilt issue #4739](https://github.com/google/dagger/issues/4739)

### Pitfall 2: Coil 3 Has a Different Maven Group and Requires a Network Artifact

**What goes wrong:** Build fails because `io.coil-kt:coil-compose:3.x` does not exist (old Maven group).
**Why it happens:** Coil 3 changed the Maven group from `io.coil-kt` to `io.coil-kt.coil3` and split network loading into a separate artifact.
**How to avoid:**
- Change group: `io.coil-kt.coil3:coil-compose:3.4.0`
- Add network artifact: `io.coil-kt.coil3:coil-network-okhttp:3.4.0`
- Remove old: `io.coil-kt:coil-compose:2.x`
**Warning signs:** Build error "Could not resolve io.coil-kt:coil-compose:3.x".

Source: [Coil 3 upgrade guide](https://coil-kt.github.io/coil/upgrading_to_coil3/)

### Pitfall 3: composeOptions Block Not Removed After Adding compose-compiler Plugin

**What goes wrong:** Build fails or produces a warning about conflicting compiler configurations.
**Why it happens:** With Kotlin 2.x and the `org.jetbrains.kotlin.plugin.compose` plugin applied, the `composeOptions { kotlinCompilerExtensionVersion }` block is redundant and conflicts.
**How to avoid:** Remove the entire `composeOptions {}` block from `app/build.gradle.kts` when applying the compose-compiler plugin.
**Warning signs:** Build warning "composeOptions is not needed when using the Compose Compiler Gradle Plugin".

Source: [Compose compiler migration guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)

### Pitfall 4: KSP Version Prefix Must Match Kotlin Version

**What goes wrong:** Build fails with KSP/Kotlin version mismatch error.
**Why it happens:** KSP versions are structured as `<kotlin-version>-<ksp-version>`. The Kotlin prefix must exactly match the Kotlin version in use.
**How to avoid:** For Kotlin 2.1.21, KSP must be `2.1.21-2.0.1` (or latest 2.1.21-x patch). Verify on [KSP releases](https://github.com/google/ksp/releases).
**Warning signs:** Build error: "This version of KSP requires Kotlin X.Y.Z but this build uses Kotlin A.B.C".

### Pitfall 5: Missing @AndroidEntryPoint on MainActivity

**What goes wrong:** App crashes immediately at startup with `IllegalStateException: ... is not an @AndroidEntryPoint`.
**Why it happens:** Hilt requires the host Activity to be annotated before it can inject the ViewModel.
**How to avoid:** Add `@AndroidEntryPoint` above the `class MainActivity` declaration immediately when adding Hilt.
**Warning signs:** Crash log references `Hilt_MainActivity` or says the activity is not an entry point.

### Pitfall 6: MutableStateFlow.update() Thread Safety on Main vs IO

**What goes wrong:** State updates from background coroutines cause `IllegalStateException` or missed recompositions.
**Why it happens:** Unlike `mutableStateOf` which requires main thread, `MutableStateFlow.update {}` is thread-safe and can be called from any dispatcher.
**How to avoid:** With StateFlow, you can call `_uiState.update { ... }` directly from IO coroutines without `withContext(Dispatchers.Main)`. The current ViewModel has many `withContext(Dispatchers.Main)` wrapping state mutations — with StateFlow these become unnecessary for the state updates themselves (but keep them for any other main-thread-only operations).

---

## Code Examples

### Hilt Module providing VideoRepository

```kotlin
// Source: https://developer.android.com/training/dependency-injection/hilt-android
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVideoRepository(
        @ApplicationContext context: Context
    ): VideoRepository = VideoRepository(context)
}
```

### StateFlow update from coroutine (no withContext needed for state)

```kotlin
// Source: https://developer.android.com/kotlin/flow/stateflow-and-sharedflow
viewModelScope.launch(Dispatchers.IO) {
    // Safe: MutableStateFlow.update is thread-safe
    _uiState.update { it.copy(appState = AppState.DOWNLOADING) }

    val result = repository.downloadMedia(url, isAudio, onProgress = { progress ->
        _uiState.update { it.copy(
            downloadProgress = progress.progress,
            progressStatus = progress.status
        )}
    })

    // Still on IO thread — safe to update StateFlow
    _uiState.update { state ->
        state.copy(
            appState = AppState.READY,
            downloadProgress = 0f,
            snackbarMessage = if (result.success) "Saved: ${result.fileName}" else null
        )
    }
}
```

### Collecting StateFlow in Compose

```kotlin
// Source: https://developer.android.com/develop/ui/compose/libraries
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // LaunchedEffect uses uiState fields directly
    LaunchedEffect(uiState.autoDownloadPending, uiState.appState) {
        if (uiState.autoDownloadPending && uiState.appState == AppState.READY) {
            viewModel.consumeAutoDownload()
        }
    }

    // All UI driven by uiState fields
    when (uiState.appState) {
        AppState.INITIALIZING, AppState.UPDATING -> { /* loading UI */ }
        AppState.READY, AppState.DOWNLOADING -> { /* main UI */ }
        AppState.ERROR -> { /* error UI */ }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `composeOptions { kotlinCompilerExtensionVersion }` | `org.jetbrains.kotlin.plugin.compose` Gradle plugin | Kotlin 2.0 (May 2024) | Must change build files; cannot use Kotlin 2.x without it |
| `kapt` for Hilt | `ksp` for Hilt | ~2023; mandatory for K2 | kapt incompatible with Kotlin 2.x K2 compiler |
| `AndroidViewModel(application)` | `ViewModel()` + `@ApplicationContext context` | Hilt adoption; available since Hilt 1.0 | Cleaner, testable, no Application reference needed |
| `mutableStateOf` in ViewModel | `StateFlow<UiState>` | Industry best practice consolidated ~2022-2023 | Testable without Compose, platform-agnostic, properly read-only |
| Coil 2 (`io.coil-kt`) | Coil 3 (`io.coil-kt.coil3`) | Coil 3.0 (2024) | Breaking group name + requires network artifact |

**Deprecated/outdated:**
- `kapt`: Not compatible with K2 compiler. Must switch to KSP for Hilt code generation with Kotlin 2.x.
- `composeOptions.kotlinCompilerExtensionVersion`: Removed from requirement by Kotlin 2.x Compose plugin.
- `AndroidViewModel`: Still works but unnecessary with Hilt; `@ApplicationContext` injection is preferred.

---

## Open Questions

1. **DataStore migration scope in 02-01**
   - What we know: MOD-01 mentions DataStore; the current plan task for 02-01 lists DataStore.
   - What's unclear: The current app stores only one boolean (format preference) in SharedPreferences. Migrating to DataStore adds a coroutine-based API (Flow), which increases complexity in the ViewModel.
   - Recommendation: Make DataStore optional in 02-01. If the planner includes it, it belongs in 02-03 (StateFlow migration) where coroutine patterns are already being introduced. If left for a later phase, the SharedPreferences reference inside the ViewModel simply moves to the Hilt module as a scoped singleton.

2. **Coil 3 usage in this app**
   - What we know: `coil-compose` is in `libs.versions.toml` but no `AsyncImage` usage appears in `MainScreen.kt` (not read in research).
   - What's unclear: Whether Coil is actually used in the current UI or is vestigial.
   - Recommendation: Check `MainScreen.kt` during 02-01. If Coil is unused, remove it entirely rather than upgrading. If used, upgrade to 3.x.

3. **Kotlin target version: 2.1.21 vs 2.3.x**
   - What we know: Kotlin 2.3.10 is the latest stable. AGP 8.13.2 (current) supports Kotlin 2.3 natively. KSP 2.3.x is available.
   - What's unclear: Whether jumping directly to 2.3.x is worth the additional scope (larger diff from 1.9.0).
   - Recommendation: 2.1.21 is a conservative, well-tested target that satisfies MOD-01. Plan should use 2.1.21. Upgrading to 2.3.x can be a separate future task.

---

## Validation Architecture

> `workflow.nyquist_validation` is not present in `.planning/config.json` — skip this section.

---

## Sources

### Primary (HIGH confidence)
- [Compose compiler migration guide](https://kotlinlang.org/docs/compose-compiler-migration-guide.html) — composeOptions removal, plugin setup
- [Compose to Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) — BOM/Kotlin version compatibility
- [BOM to library version mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — exact Compose BOM 2026.02.00 library versions
- [Dependency injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android) — Hilt 2.57.1 setup
- [Use Hilt with other Jetpack libraries](https://developer.android.com/training/dependency-injection/hilt-jetpack) — @HiltViewModel + @ApplicationContext pattern
- [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) — StateFlow pattern
- [AGP, D8, and R8 versions required for Kotlin versions](https://developer.android.com/build/kotlin-support) — Kotlin 2.1 requires AGP 8.6+
- [Android Gradle Plugin 8.13.0 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) — AGP 8.13 requires Gradle 8.13
- [Coil 3 upgrade guide](https://coil-kt.github.io/coil/upgrading_to_coil3/) — breaking changes in Maven group + network artifact

### Secondary (MEDIUM confidence)
- [KSP releases on GitHub](https://github.com/google/ksp/releases) — KSP version structure `<kotlin>-<ksp>` confirmed
- [Hilt issue #4739](https://github.com/google/dagger/issues/4739) — @HiltViewModel ProGuard strip bug in AGP 8.9.2+
- [Compose and other libraries](https://developer.android.com/develop/ui/compose/libraries) — hiltViewModel() + lifecycle-runtime-compose dependency

### Tertiary (LOW confidence)
- Community articles on mutableStateOf vs StateFlow tradeoffs — multiple sources agree on the recommendation; flagged for awareness only

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified against official Android docs and release notes
- Architecture: HIGH — patterns sourced from official Android DI and Compose documentation
- Pitfalls: HIGH for ProGuard/Coil (verified against official issue tracker and upgrade guide); MEDIUM for StateFlow threading (multiple community sources agree)

**Research date:** 2026-03-01
**Valid until:** 2026-06-01 (stable libraries, 90-day validity)
