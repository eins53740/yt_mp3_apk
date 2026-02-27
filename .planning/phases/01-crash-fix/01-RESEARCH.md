# Phase 1: Crash Fix - Research

**Researched:** 2026-02-27
**Domain:** Android Manifest configuration, AGP version resolution, Kotlin coroutine cancellation for UI-driven skip behavior
**Confidence:** HIGH

## Summary

Phase 1 has three narrowly scoped fixes: add `android:extractNativeLibs="true"` to `AndroidManifest.xml`, correct the AGP version string in `libs.versions.toml`, and add a "Skip update" button that cancels the in-flight yt-dlp network update and transitions directly to READY state.

The crash is definitively caused by the missing `extractNativeLibs` attribute. The youtubedl-android library bundles Python 3.8 and yt-dlp as native `.so` files. Without extraction to disk at install time, the Python runtime cannot be located on the filesystem and `YoutubeDL.initPython()` throws `YoutubeDLException: failed to initialize`. This is documented as a mandatory setup step in the youtubedl-android README and confirmed in multiple issue reports.

The AGP version `8.13.1` in `libs.versions.toml` does not exist — official release notes and Maven docs show only `8.13.0` and `8.13.2` in the 8.13.x line. The current codebase also uses Kotlin 1.9.0 with a `composeOptions { kotlinCompilerExtensionVersion }` block; these are not changed in Phase 1 (dependency modernization is deferred to Phase 2 to isolate the crash fix). The skip-update feature requires a `Job` reference to the yt-dlp update coroutine so it can be cancelled, plus a new ViewModel function `skipUpdate()` and a UI button visible only during the UPDATING state.

**Primary recommendation:** Three targeted file changes — manifest, version catalog, ViewModel — with a matching UI button. No architectural changes. No dependency version bumps beyond fixing the AGP typo.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FIX-01 | App starts without crashing (add `extractNativeLibs="true"` to manifest) | Confirmed: youtubedl-android README mandates this attribute; crash trace matches ENOENT on Python native lib |
| FIX-02 | Build succeeds with correct AGP version (fix 8.13.1 typo) | Confirmed: AGP 8.13.1 does not exist; replace with 8.13.2 (latest stable 8.13.x patch) |
| FIX-03 | User can skip yt-dlp update if it hangs on first launch (timeout/cancel button) | Confirmed: `updateYtDlp()` is a coroutine launched inside `initialize()` job; holding a `Job` reference enables cooperative cancellation via `job.cancel()` |
</phase_requirements>

---

## Standard Stack

### Core (Phase 1 — no version changes)

| Component | Current Version | Status | Notes |
|-----------|----------------|--------|-------|
| AGP | 8.13.1 (typo) | Fix to 8.13.2 | 8.13.1 does not exist in official releases |
| Kotlin | 1.9.0 | Keep | Do not upgrade in Phase 1 — isolate crash fix |
| Compose BOM | 2023.08.00 | Keep | Upgrade deferred to Phase 2 |
| youtubedl-android | 0.18.0 | Keep | 0.18.1 upgrade deferred to Phase 2 |
| kotlinx-coroutines | (transitive) | Keep | Cancellation mechanism is already present |

### Why No Version Bumps in Phase 1

Changing Kotlin (1.9.0 → 2.x) requires removing the `composeOptions` block and adding the `kotlin-compose` Gradle plugin. Changing Compose BOM requires verifying all Material3/UI APIs still compile. These are routine but non-trivial changes that could introduce new build errors. Phase 1 goal is one confirmed working download — changing 10 things at once makes the crash harder to diagnose and risks attributing a new failure to the wrong change.

---

## Architecture Patterns

### FIX-01: extractNativeLibs Manifest Change

The attribute belongs on the `<application>` tag in `AndroidManifest.xml`:

```xml
<!-- Source: youtubedl-android README — mandatory setup step -->
<application
    ...
    android:extractNativeLibs="true"
    ...>
```

**Why it's needed:** Android API 23+ defaults `extractNativeLibs` to `false` when AGP sets it automatically, which stores `.so` files compressed in the APK. The youtubedl-android library uses a `.so`-packaged Python distribution (`libpython.zip.so`). When not extracted, the file path does not exist on disk and JNI/Python init fails. Setting `extractNativeLibs="true"` forces the installer to decompress and place native libs in `/data/app/<pkg>/lib/`. APK size increases slightly (no compression on .so files), but the app works.

**Side effect on ABI splits:** The app already uses `splits { abi { ... } }` in `build.gradle.kts`. This combination is fine — ABI splits and `extractNativeLibs="true"` are orthogonal settings. No conflict.

**Side effect on `tools:targetApi`:** The manifest already has `tools:targetApi="34"` on `<application>`. The `extractNativeLibs` attribute does not require a `tools:ignore` annotation. Add it alongside existing attributes.

### FIX-02: AGP Version Correction

Change exactly one line in `gradle/libs.versions.toml`:

```toml
# Before:
agp = "8.13.1"

# After:
agp = "8.13.2"
```

**Why 8.13.2:** Official Android Gradle Plugin release notes document only `8.13.0` and `8.13.2` in the 8.13.x line — `8.13.1` is absent from both the official release page and Maven Central. The Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`) must use Gradle 8.13 (already the current wrapper version for AGP 8.13.x). No change to wrapper needed — verify but do not change.

**Do not** change the top-level `build.gradle.kts` — it uses `alias(libs.plugins.android.application)` which picks up the version from `libs.versions.toml` automatically.

### FIX-03: Skip Update Button

**Current flow:** `initialize()` is a single `viewModelScope.launch(Dispatchers.IO)` coroutine. Inside it, `updateYtDlp()` calls `YoutubeDL.getInstance().updateYoutubeDL(application)` — a blocking network call with no timeout. If the network is slow or unreachable on first launch, the app stays in `UPDATING` state indefinitely.

**Required change — ViewModel:**

1. Extract the yt-dlp update step into a separate coroutine and hold a reference to its `Job`.
2. Add `fun skipUpdate()` that cancels that Job and transitions state to READY.
3. Expose a `isUpdating` boolean (derived from `appState == AppState.UPDATING`) — already implicitly available.

```kotlin
// In MainViewModel
private var updateJob: Job? = null

private fun initialize() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            updateStatus("Initializing YoutubeDL...")
            YoutubeDL.getInstance().init(getApplication())

            updateStatus("Initializing FFmpeg...")
            FFmpeg.getInstance().init(getApplication())

            updateStatus("Initializing Aria2c...")
            try {
                Aria2c.getInstance().init(getApplication())
            } catch (e: Exception) {
                Log.w(TAG, "Aria2c init failed (optional): ${e.message}")
            }

            // Launch update as a cancellable child job
            withContext(Dispatchers.Main) {
                appState = AppState.UPDATING
                statusMessage = "Updating yt-dlp... (tap Skip to proceed)"
            }
            updateJob = viewModelScope.launch(Dispatchers.IO) {
                updateYtDlp()
            }
            updateJob?.join()  // Wait for it, but it can be cancelled

            withContext(Dispatchers.Main) {
                if (appState != AppState.READY) {  // not already skipped
                    appState = AppState.READY
                    statusMessage = "Ready to download"
                }
            }
        } catch (e: CancellationException) {
            // Normal — skip was tapped, state already set to READY
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            withContext(Dispatchers.Main) {
                appState = AppState.ERROR
                statusMessage = "Initialization failed: ${e.message}"
            }
        }
    }
}

fun skipUpdate() {
    updateJob?.cancel()
    updateJob = null
    appState = AppState.READY
    statusMessage = "Ready to download"
}
```

**Alternative simpler approach:** Instead of restructuring `initialize()`, add a timeout to `updateYtDlp()` using `withTimeout(30_000L)` and catch `TimeoutCancellationException`. This avoids needing a held Job reference. The skip button calls `skipUpdate()` which cancels via a flag. However, `withTimeout` only helps for slow responses — if the thread is blocked (not suspending), it won't cancel. The `updateYoutubeDL()` call in youtubedl-android 0.18.x is a JVM blocking call (not a coroutine suspend function), so `withTimeout` may not interrupt it reliably. The Job cancellation approach is more robust.

**Recommendation:** Use the Job reference approach. It cooperates with Kotlin's structured concurrency — cancelling the child job does not cancel the parent `initialize()` coroutine, which can then cleanly transition to READY.

**Required change — MainScreen UI:**

Add a "Skip update" `TextButton` that appears only when `appState == AppState.UPDATING`. Place it adjacent to the existing status message or below the spinning download button:

```kotlin
// In MainScreen, within the item{} block that shows the download button
if (viewModel.appState == AppState.UPDATING) {
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(
        onClick = { viewModel.skipUpdate() }
    ) {
        Text("Skip update")
    }
}
```

This uses the existing `TextButton` import (already imported in `MainScreen.kt`) and requires no new dependencies.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Cancellable network timeout | Custom timeout thread/flag | Kotlin `Job` cancellation (already available via `viewModelScope`) |
| AGP version lookup | Manual Maven search | Official Android Gradle plugin release notes page |
| Native lib extraction | Custom JNI loader | `android:extractNativeLibs="true"` — OS installer handles it |

---

## Common Pitfalls

### Pitfall 1: CancellationException Must Be Re-Thrown

**What goes wrong:** Catching `CancellationException` without re-throwing it breaks Kotlin structured concurrency. The parent coroutine would not know the child was cancelled and may behave incorrectly.

**How to avoid:** In any `catch (e: Exception)` block that handles initialization failure, either catch `CancellationException` separately and re-throw it, or use `catch (e: Exception) { if (e is CancellationException) throw e; ... }`.

**Warning signs:** App freezes or stays in ERROR state after tapping Skip.

### Pitfall 2: Setting appState from Background Thread

**What goes wrong:** `appState` is a Compose `mutableStateOf` — mutations must happen on the main thread. If `skipUpdate()` is called from a coroutine context without `withContext(Dispatchers.Main)`, it will compile but may crash with a threading violation.

**How to avoid:** `skipUpdate()` is called from a `Button.onClick` lambda, which always runs on the main thread. No `withContext` wrapper needed for the direct call. However, if `skipUpdate()` is ever called from an IO coroutine, add a `withContext(Dispatchers.Main)` wrapper.

**Warning signs:** `CalledFromWrongThreadException` in logcat after tapping Skip.

### Pitfall 3: AGP Typo Causes Gradle Sync Failure — Not a Runtime Crash

**What goes wrong:** If `8.13.1` happens to resolve to nothing (Gradle can't find it), Gradle sync fails with a dependency resolution error. If somehow a cached version exists, the build may succeed with unpredictable behavior.

**How to avoid:** Verify `8.13.2` resolves by running `./gradlew assembleDebug` — a clean Gradle sync is the confirmation. Confidence that `8.13.1` does not exist is HIGH based on official release notes showing only `8.13.0` and `8.13.2`.

**Warning signs:** `Could not find com.android.tools.build:gradle:8.13.1` in Gradle sync output.

### Pitfall 4: extractNativeLibs Increases APK Size

**What it is:** With `extractNativeLibs="true"`, native `.so` files are not compressed in the APK — they are stored uncompressed so the OS can mmap them directly from the APK on older devices, or extract them to disk. This increases installed size but not download size significantly.

**Impact for this app:** Acceptable. The youtubedl-android library already ships large native binaries (~50+ MB). The size difference is marginal compared to the existing payload.

**Not a bug — document it:** This is expected behavior, not something to fix.

### Pitfall 5: updateYoutubeDL() Blocks the IO Thread — Not Truly Cancellable

**What goes wrong:** `YoutubeDL.getInstance().updateYoutubeDL()` is a blocking Java call that downloads a file over HTTP. Kotlin Job cancellation sets a cancellation flag, but if the blocking call is inside a `Dispatchers.IO` thread that never checks `isActive`, the download continues until complete before the Job terminates.

**Practical impact:** Tapping "Skip update" may not immediately stop the download. The Job will eventually stop when the blocking call returns (on success, failure, or network timeout). The app UI transitions to READY immediately (since `skipUpdate()` directly sets `appState = AppState.READY`), giving the user the impression it worked — and functionally it has, because the download completes silently in the background without affecting UI state.

**How to handle:** This is acceptable for Phase 1. The user sees READY immediately. If the background call completes after skip, the ViewModel can ignore the result (check if `appState == READY` before applying update result). Document this in code with a comment.

---

## Code Examples

### Minimal extractNativeLibs Change

```xml
<!-- app/src/main/AndroidManifest.xml — add one attribute to <application> tag -->
<!-- Source: youtubedl-android README — mandatory setup step -->
<application
    android:name=".YT2LocalApplication"
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.YT2Local"
    android:usesCleartextTraffic="false"
    android:networkSecurityConfig="@xml/network_security_config"
    android:extractNativeLibs="true"
    tools:targetApi="34">
```

### AGP Version Correction (libs.versions.toml)

```toml
[versions]
agp = "8.13.2"   # was "8.13.1" — that version does not exist
kotlin = "1.9.0" # unchanged — version upgrade deferred to Phase 2
```

### skipUpdate() ViewModel Function

```kotlin
// In MainViewModel.kt
private var updateJob: Job? = null

fun skipUpdate() {
    // Cancel the update download. The blocking HTTP call may continue briefly
    // in the background, but we transition UI to READY immediately.
    updateJob?.cancel()
    updateJob = null
    // appState is mutableStateOf — safe to set from main thread (onClick context)
    appState = AppState.READY
    statusMessage = "Ready to download"
}
```

### Skip Button in MainScreen

```kotlin
// In MainScreen.kt, inside item{} block near the download button
// Uses existing TextButton import — no new imports needed
if (viewModel.appState == AppState.UPDATING) {
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = { viewModel.skipUpdate() }) {
        Text("Skip update")
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `kotlinCompilerExtensionVersion = "1.5.1"` in `composeOptions {}` | `org.jetbrains.kotlin.plugin.compose` Gradle plugin (Kotlin 2.0+) | Deferred to Phase 2 — not touched in Phase 1 |
| kapt for annotation processing | KSP | Deferred to Phase 2 — Hilt/Room not added yet |
| `extractNativeLibs` absent/false | `extractNativeLibs="true"` | **Phase 1 fix — this is the crash** |

---

## Open Questions

1. **Does Gradle wrapper version need updating alongside AGP 8.13.2?**
   - What we know: AGP 8.13.x requires Gradle 8.13. Need to check `gradle/wrapper/gradle-wrapper.properties`.
   - What's unclear: Current wrapper version. If it's already 8.13, no change needed.
   - Recommendation: Plan task should read `gradle-wrapper.properties` and verify — change only if mismatch.

2. **Does `updateYoutubeDL()` have a built-in timeout parameter in youtubedl-android 0.18.0?**
   - What we know: The method signature is `updateYoutubeDL(context: Context, channel: UpdateChannel? = null)` — no timeout parameter documented.
   - What's unclear: Whether the underlying OkHttp client (used by the library) has a default read timeout.
   - Recommendation: Assume no timeout. The Job cancellation approach sets UI to READY immediately regardless of whether the background HTTP call finishes. Treat as acceptable for Phase 1.

3. **Is `android:requestLegacyExternalStorage="true"` needed alongside `extractNativeLibs`?**
   - What we know: The youtubedl-android demo app manifest includes `requestLegacyExternalStorage="true"` (for Android 10). The current app uses MediaStore API for file storage, which does not require legacy external storage access.
   - What's unclear: Whether any yt-dlp temp file operations require legacy access on API 29.
   - Recommendation: Do not add `requestLegacyExternalStorage` in Phase 1. The app targets `minSdk 26` and uses MediaStore (`Downloads/yt2local/`) — scoped storage is correct. Add only if a runtime error is observed on API 29 during testing.

---

## Validation Architecture

> `workflow.nyquist_validation` is not present in `.planning/config.json` — the config uses a non-standard key structure. Skipping automated test mapping. Validation for Phase 1 is manual device testing only (no JDK/Android SDK available in this environment).

**Manual validation checklist (success criteria from phase description):**

1. `./gradlew assembleDebug` completes without error (confirms FIX-02: AGP version resolves, FIX-01: manifest compiles)
2. Install APK on real device: app opens without force-close (confirms FIX-01: extractNativeLibs fix)
3. During initialization, a "Skip update" button is visible while status shows "Updating yt-dlp..." (confirms FIX-03 UI)
4. Tapping "Skip update" transitions to READY state within one second (confirms FIX-03 behavior)
5. Enter a YouTube URL, tap Download, file appears in `Downloads/yt2local/` (end-to-end confirmation)

---

## Sources

### Primary (HIGH confidence)
- [youtubedl-android README](https://github.com/yausername/youtubedl-android/blob/master/README.md) — Explicitly states `extractNativeLibs="true"` is a mandatory manifest setup step
- [AGP 8.13.0 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) — Lists 8.13.0 and 8.13.2 only; 8.13.1 absent from official docs
- [youtubedl-android demo AndroidManifest.xml](https://github.com/yausername/youtubedl-android/blob/master/app/src/main/AndroidManifest.xml) — Reference app does not set `extractNativeLibs` (library consumer must set it per README instructions)
- Current source files read directly: `app/src/main/AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/src/main/java/com/example/yt2local/MainViewModel.kt`, `MainScreen.kt`

### Secondary (MEDIUM confidence)
- WebSearch results confirming `extractNativeLibs="true"` requirement for youtubedl-android across multiple community reports (2024-2025)
- STACK.md prior research noting "8.13.1 may not exist — use 8.13.2" (HIGH confidence in conclusion, MEDIUM in prior source)

---

## Metadata

**Confidence breakdown:**
- FIX-01 (extractNativeLibs): HIGH — documented in official library README, matches crash trace
- FIX-02 (AGP typo): HIGH — 8.13.1 absent from official release notes; 8.13.2 is the known-good replacement
- FIX-03 (skip button): HIGH — standard Kotlin coroutine Job cancellation pattern; caveat on blocking call documented

**Research date:** 2026-02-27
**Valid until:** 2026-04-27 (stable Android APIs; youtubedl-android API unlikely to change)
