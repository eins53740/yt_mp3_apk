# Pitfalls Research

**Domain:** Android video/audio downloader app — yt-dlp-based, Kotlin/Compose, refactoring with Hilt + foreground service
**Researched:** 2026-02-27
**Confidence:** HIGH for library-specific issues (verified via GitHub issues), MEDIUM for Hilt/foreground service patterns (official docs + community), LOW where flagged

---

## Critical Pitfalls

### Pitfall 1: `extractNativeLibs=false` Causes `libpython.zip.so` ENOENT on Init

**What goes wrong:**
`YoutubeDL.getInstance().init()` throws `YoutubeDLException: failed to initialize` with a `FileNotFoundException` for `libpython.zip.so` at path `/data/app/.../lib/arm64-v8a/libpython.zip.so`. The app crashes immediately on startup. This is the **current crash in this project**.

**Why it happens:**
Android's default since API 23 is `android:extractNativeLibs="false"`, meaning `.so` files remain compressed inside the APK/bundle and are loaded via memory-mapping. The `youtubedl-android` library uses `libpython.zip.so` as a renamed ZIP archive (not a true `.so`). It opens this file directly via filesystem path at runtime. When `extractNativeLibs=false`, this file is never written to `/data/app/.../lib/`, so the path does not exist on disk.

**How to avoid:**
Add `android:extractNativeLibs="true"` to the `<application>` tag in `AndroidManifest.xml`. This forces Android to extract all `.so` files to the filesystem on install, making `libpython.zip.so` accessible by path.

```xml
<application
    android:extractNativeLibs="true"
    ...>
```

**Warning signs:**
- Stack trace contains `ENOENT` or `FileNotFoundException` for `libpython.zip.so` or `libffmpeg.zip.so`
- Crash happens at `YoutubeDL.initPython()` or `FFmpeg.getInstance().init()`
- Debug builds and release builds both crash (not a ProGuard issue)

**Phase to address:** Phase 1 — Fix initialization crash (prerequisite for everything else)

---

### Pitfall 2: Invalid AGP Version `8.13.1` Does Not Exist

**What goes wrong:**
The project's `libs.versions.toml` specifies `agp = "8.13.1"`. AGP 8.13.x does not exist as a stable release (the version numbering jumped from 8.x to 9.x after 8.7). The build will fail at dependency resolution with "Plugin was not found in any of the following sources". This blocks all builds.

**Why it happens:**
A typo was introduced during project setup — `8.13.1` likely meant `8.3.1` (AGP 8.3.0 released February 2024). The version string `8.13.1` was never challenged because no build was ever successfully run in the dev environment.

**How to avoid:**
Fix `agp = "8.13.1"` to `agp = "8.3.1"` in `gradle/libs.versions.toml`. Cross-reference against the [AGP release notes](https://developer.android.com/build/releases/past-releases/agp-8-3-0-release-notes) to confirm the version exists. The correct AGP version for the current Kotlin 1.9.0 and Compose compiler 1.5.1 pairing is `8.1.x` through `8.3.x`.

**Warning signs:**
- Build fails with "Plugin not found" for `com.android.application`
- Gradle sync errors immediately after project open
- Version number has more than one digit in the minor position (e.g., `8.13` vs `8.3`)

**Phase to address:** Phase 1 — Fix initialization crash / dependency stabilization

---

### Pitfall 3: All Three `junkfood02/youtubedl-android` Artifacts Must Be Identical Version

**What goes wrong:**
Upgrading `library` to a newer version while keeping `ffmpeg` and `aria2c` on the old version (or vice versa) causes runtime crashes or mismatched native libraries. The three artifacts share native binary layers that are co-compiled.

**Why it happens:**
Developers sometimes upgrade incrementally or copy dependency snippets from different sources. The version constraint is not enforced by Gradle — it compiles but fails at runtime.

**How to avoid:**
Always update all three artifacts together in `libs.versions.toml`:
```toml
youtubedlAndroid = "0.18.1"  # applies to library, ffmpeg, and aria2c
```
Never set `youtubedl-android:library`, `youtubedl-android:ffmpeg`, and `youtubedl-android:aria2c` to different versions.

**Warning signs:**
- `UnsatisfiedLinkError` at runtime after upgrading only one artifact
- Init succeeds but FFmpeg operations crash with native errors
- Aria2c produces unexpected behavior after upgrade

**Phase to address:** Phase 1 — Dependency stabilization (when bumping from 0.18.0 to 0.18.1+)

---

### Pitfall 4: ProGuard Strips Hilt-Generated Code in Release Builds

**What goes wrong:**
The app works in debug but crashes in release with `ClassNotFoundException` or `IllegalStateException` referencing Hilt-generated factory classes. Hilt generates classes like `MainViewModel_HiltModules_KeyModule_ProvideFactory` at compile time. R8/ProGuard does not know these are needed unless explicitly kept.

**Why it happens:**
Hilt's code generation happens at compile time via annotation processing. The generated class names are not referenced directly in source code, so R8's reachability analysis marks them as dead code and removes them. This is particularly dangerous because the crash only surfaces in release builds, not in debug (where minification is off).

**How to avoid:**
Add explicit ProGuard rules for Hilt when adding the dependency:
```
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.internal.** { *; }
-keepnames class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembernames class * { @dagger.hilt.* <methods>; }
```
Note: Hilt's published AAR includes consumer ProGuard rules, so some of this is handled automatically. But custom `@Module` classes and `@Provides` methods may need explicit keeps depending on R8 version.

Always test a release build immediately after adding Hilt — not after the entire refactor is complete.

**Warning signs:**
- App works in debug, crashes immediately in release
- Stack trace mentions `_Factory` or `_HiltModules` class names
- `ClassNotFoundException` for a class that contains "Hilt" or "Dagger" in the name

**Phase to address:** Phase 2 — Hilt DI introduction; verify release build before moving to next phase

---

### Pitfall 5: Foreground Service Requires `foregroundServiceType` in Manifest for Android 14+ (targetSdk 34)

**What goes wrong:**
The project targets SDK 34. Starting with Android 14 (API 34), every foreground service must declare a `foregroundServiceType` in the manifest AND request the corresponding permission. Without it, calling `startForeground()` throws a `ForegroundServiceStartNotAllowedException` or the service is immediately killed. The manifest already declares `FOREGROUND_SERVICE` permission but lacks the type.

**Why it happens:**
Developers copy foreground service boilerplate from pre-Android-14 examples that predate the type requirement. The permission is declared but the `foregroundServiceType` attribute on the `<service>` element is omitted.

**How to avoid:**
For a download service, use `dataSync` type. Add to manifest:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".DownloadService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```
And in the service code:
```kotlin
ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
```

**Warning signs:**
- `ForegroundServiceStartNotAllowedException` in logcat on Android 14+ devices
- Service starts fine on Android 13 and below but immediately stops on Android 14+
- Manifest has `FOREGROUND_SERVICE` permission but no `FOREGROUND_SERVICE_DATA_SYNC`

**Phase to address:** Phase 3 — Foreground service implementation

---

## Moderate Pitfalls

### Pitfall 6: `mutableStateOf` Written from `Dispatchers.IO` Without `withContext(Dispatchers.Main)`

**What goes wrong:**
Compose's snapshot state system requires mutations to `mutableStateOf` to occur in a valid snapshot context on the main thread. Writing to a `var` backed by `mutableStateOf` from a background coroutine (e.g., `Dispatchers.IO`) can throw `IllegalStateException: Reading a state that was created after the snapshot was taken or in a snapshot that has not yet been applied`. This is intermittent and hard to reproduce.

**Why it happens:**
The current `MainViewModel` uses `withContext(Dispatchers.Main)` for most state updates, which is correct. But the pattern is brittle — any new code path that updates state directly from `Dispatchers.IO` without the wrapper will silently corrupt state or crash.

**How to avoid:**
All writes to `mutableStateOf` properties must happen on the main thread. The safest pattern during the Hilt refactor is to wrap all state mutations in `withContext(Dispatchers.Main) { ... }`. During the refactor, consider migrating hot state to `StateFlow` with `MutableStateFlow` in the ViewModel and `.collectAsStateWithLifecycle()` in Compose — StateFlow handles cross-thread emissions safely.

**Warning signs:**
- `IllegalStateException` mentioning snapshots in logcat
- UI state sporadically fails to update after a download completes
- Crashes only occur occasionally, not deterministically

**Phase to address:** Phase 2 — ViewModel state management cleanup

---

### Pitfall 7: `ViewModel.viewModelScope` Cancels When Activity Is Destroyed — Downloads Die

**What goes wrong:**
Downloads launched via `viewModelScope.launch { }` in `MainViewModel` are cancelled when the ViewModel is cleared (activity destroyed — not just rotated, but actually killed). If the user navigates away or the system kills the activity mid-download, the coroutine is cancelled and the partially-downloaded temp file is orphaned in `filesDir/video_temp/`.

**Why it happens:**
`viewModelScope` is scoped to the ViewModel lifecycle. While ViewModels survive rotation, they are cleared when the task is removed from recents or the system kills the process. `Dispatchers.IO` coroutines do not run as foreground services and receive no OS guarantee of survival.

**How to avoid:**
Move download execution to a foreground service. The ViewModel becomes a coordinator that starts the service and observes its published state (via a `ServiceConnection` or a shared `StateFlow` in the Application class). The download coroutine lives in the service's `CoroutineScope`, which survives activity death.

**Warning signs:**
- User reports downloads stopping when they lock screen or switch apps
- Temp files found in `filesDir/video_temp/` after app restart
- `onCleared()` log line appears before download completion log

**Phase to address:** Phase 3 — Foreground service migration

---

### Pitfall 8: `YoutubeDL.updateYoutubeDL()` Hangs Indefinitely on First Launch with No Timeout

**What goes wrong:**
On first launch or with a slow network, `updateYoutubeDL()` makes a network request with no timeout specified. The app can appear frozen on "Updating yt-dlp..." indefinitely, blocking the READY state and preventing all downloads. There is no cancellation path in the current implementation.

**Why it happens:**
The library's `updateYoutubeDL()` is a blocking call wrapped in a coroutine. If the CDN hosting the yt-dlp binary is unreachable or slow, it blocks the initialization coroutine without any watchdog.

**How to avoid:**
Wrap `updateYoutubeDL()` with `withTimeout(30_000L)` (30 seconds). Catch `TimeoutCancellationException` separately from other exceptions, proceed to READY state with the bundled version, and show a non-blocking message. Also expose a "Skip update" button in the UI that cancels the update job via a `Job` reference and proceeds to READY.

**Warning signs:**
- App hangs on "Updating yt-dlp..." for more than 30 seconds
- No timeout in the coroutine wrapping `updateYoutubeDL()`
- No "skip update" affordance visible during the UPDATING state

**Phase to address:** Phase 1 — Initialization fixes; or Phase 2 — UX improvements

---

### Pitfall 9: App Bundle (AAB) Build Breaks `youtubedl-android`

**What goes wrong:**
Building an AAB (Android App Bundle) instead of an APK causes the build to fail with `llvm-strip: error: 'libpython.zip.so' is not recognized as a valid object file`. The `.zip.so` files cannot be stripped by the NDK toolchain during AAB packaging.

**Why it happens:**
The `youtubedl-android` library packages Python as a renamed ZIP file (`libpython.zip.so`). The NDK strip tool, invoked during AAB generation, attempts to strip debug symbols from all `.so` files. Since `libpython.zip.so` is not a real ELF binary, stripping fails.

**How to avoid:**
Keep building APKs (not AAB) for this app — which is the current approach using `splits { abi { ... } }`. If AAB is ever required, add to `build.gradle.kts`:
```kotlin
android {
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```
Do not migrate to AAB until verifying the fix works with the specific NDK version in use.

**Warning signs:**
- Build error mentioning `llvm-strip` and `.zip.so`
- Error triggered only when building AAB, not APK
- Error appears after changing `buildTypes` or release configuration

**Phase to address:** Phase 1 — Dependency/build stabilization; note as permanent constraint

---

### Pitfall 10: Hilt `@HiltAndroidApp` on Application vs. Regular `Application` Subclass

**What goes wrong:**
After adding Hilt, the `YT2LocalApplication` class must be annotated with `@HiltAndroidApp`. If it remains a plain `Application` subclass, Hilt will fail at runtime with `IllegalStateException: Hilt components were not initialized. Check that you have added the HiltAndroidApp annotation to your Application`. `MainActivity` also requires `@AndroidEntryPoint`.

**Why it happens:**
Hilt requires its component injection tree to be rooted at the Application class. Forgetting `@HiltAndroidApp` is the single most common Hilt setup mistake. The error is clear, but it can be missed if incremental migration leaves the Application class untouched.

**How to avoid:**
The very first step of Hilt migration must be:
1. Add `@HiltAndroidApp` to `YT2LocalApplication`
2. Add `@AndroidEntryPoint` to `MainActivity`
3. Compile and verify these two changes alone before adding any `@Inject` constructors

**Warning signs:**
- `IllegalStateException: Hilt components were not initialized` at app startup
- Error occurs before any DI code runs
- The error message directly names the missing annotation

**Phase to address:** Phase 2 — Hilt introduction; first commit must be Application + Activity annotations only

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Keep `mutableStateOf` in ViewModel (not StateFlow) | Simpler code, no `.collectAsState()` boilerplate | Thread-safety issues when multiple coroutines write state; harder to test | Acceptable in MVP; should migrate when adding foreground service (cross-process state) |
| Instantiate `VideoRepository` directly in ViewModel constructor | No DI boilerplate needed | Cannot mock for testing; tightly coupled | Only for MVP; must change when adding Hilt |
| In-memory download history (last 10) | Zero persistence code | History lost on app kill; can't survive process death | Acceptable until foreground service phase; must persist alongside queue |
| Download in `viewModelScope` | Simple implementation | Cancelled on activity destroy | Acceptable pre-foreground-service; never acceptable as permanent solution |
| `YoutubeDL.getInstance()` singleton access throughout codebase | Consistent | Hard to inject/mock; init state not encapsulated | Acceptable — the library is designed as a singleton; wrap it in a `YtDlpManager` class for testability |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| `youtubedl-android` init | Call `init()` multiple times (e.g., on retry without checking) | Guard with a flag; the library does not deduplicate init calls gracefully |
| `youtubedl-android` init | Not calling all three inits (YoutubeDL, FFmpeg, Aria2c) before downloading | Always init all three; Aria2c failure is tolerated but FFmpeg is required for MP3 conversion |
| MediaStore API | Insert with `IS_PENDING=1` but forget to clear it | File appears in Downloads but is inaccessible to other apps; always clear `IS_PENDING` after write |
| MediaStore API | Use `MediaStore.Downloads.EXTERNAL_CONTENT_URI` without `RELATIVE_PATH` set | File lands in root of Downloads instead of `Downloads/yt2local/` |
| Foreground Service + Hilt | Inject ViewModel into Service directly | ViewModels are scoped to UI lifecycle, not service lifecycle; use a shared repository or Application-scoped state instead |
| `updateYoutubeDL()` | Call from main thread (non-coroutine context) | Always call from `Dispatchers.IO` coroutine; it makes network requests |
| `YoutubeDLRequest` with aria2c | Always pass `--downloader aria2c` even when Aria2c init failed | Check Aria2c init success before passing aria2c downloader flag; fall back to default downloader |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Temp file not cleaned on download failure | `filesDir/video_temp/` accumulates GB of partial files | Always call `cleanTempDir()` in both success and failure paths (wrap in `try/finally`) | After ~5 failed downloads on a 32GB device |
| Title parsing from yt-dlp output (regex on every line) | Minor CPU overhead per progress callback | Low severity for a single download; acceptable | Not a concern at current scale |
| Progress callback dispatched to main thread on every `onProgress` call | UI jank if progress updates are very frequent | Rate-limit updates to max 10/second using a timestamp check | Not currently observed; monitor when adding queue |
| Large files copied via 8192-byte buffer in `moveToMediaStore` | Slow copy for large video files (e.g., 1GB) | Acceptable for typical downloads (100-500MB); consider larger buffer (65536) | Files >500MB may noticeably stall the UI between "download complete" and "saved" |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| `android:usesCleartextTraffic="false"` with no exceptions | yt-dlp may attempt HTTP (not HTTPS) connections for some platforms | Current `network_security_config.xml` must allow the domains yt-dlp targets; verify it has `<domain-config cleartextTrafficPermitted="true">` for affected domains or remove the global block |
| Temp files in `filesDir` accessible without scoped storage | Low risk — `filesDir` is private to the app | Acceptable; do not move temp files to external storage |
| Exposing raw exception messages to UI | Stack traces in the "Error:" message expose internal state | Current `parseError()` function correctly sanitizes; ensure new error paths go through `parseError()` |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| App stuck on "Updating yt-dlp..." with no escape | User cannot download; must force-kill app | Add "Skip update" button visible during UPDATING state; auto-proceed after 30s timeout |
| Download cancelled silently when user navigates away | User assumes download completed; file never appears | Show persistent foreground service notification with cancel action; report result when complete |
| Auto-download from share intent triggers before READY state | Intent arrives, `autoDownloadPending=true`, app hangs in INITIALIZING for 10+ seconds | Current `LaunchedEffect` design correctly waits for READY; verify this survives rotation + Hilt migration |
| No file open affordance after download | User must navigate to Files app to find and play downloaded content | Implement `FileProvider` + `Intent.ACTION_VIEW` from notification and history list tap |
| Error message shown but URL field cleared on failure | User must re-paste URL to retry | Only clear URL field on success, not on error |

---

## "Looks Done But Isn't" Checklist

- [ ] **Init fix:** Verify `extractNativeLibs="true"` is in manifest AND that an actual device/emulator run succeeds — not just that the code looks correct
- [ ] **Release build:** Test APK installed from `assembleRelease` — not just debug. ProGuard issues only surface in release
- [ ] **Hilt migration:** Verify that `by viewModels()` still works after adding `@HiltViewModel` — the delegate must be `by viewModels()` not `by hiltNavGraphViewModels()` for single-activity apps without navigation
- [ ] **Foreground service notification:** Verify notification appears on Android 13+ (requires `POST_NOTIFICATIONS` granted) AND on Android 14 (requires correct `foregroundServiceType`)
- [ ] **Foreground service on Android 14:** Confirm `FOREGROUND_SERVICE_DATA_SYNC` permission AND `android:foregroundServiceType="dataSync"` on service element AND `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` in `startForeground()` call — all three required
- [ ] **Download queue:** Verify that cancelling queue item N does not kill the ongoing yt-dlp process for item N-1 (process management must be per-request)
- [ ] **Temp file cleanup:** Confirm `video_temp/` is empty after both successful and failed downloads (test with a URL that 404s)
- [ ] **Intent handling after Hilt:** Confirm `onNewIntent` + `setUrlFromIntent` still triggers auto-download when app is already running

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| `libpython.zip.so` ENOENT crash | LOW | Add `extractNativeLibs="true"` to manifest, rebuild APK, reinstall |
| AGP version typo `8.13.1` | LOW | Fix version in `libs.versions.toml` to `8.3.1`, sync Gradle |
| ProGuard strips Hilt classes in release | MEDIUM | Add ProGuard keep rules, rebuild release APK, test on device |
| Hilt annotation missing on Application | LOW | Add `@HiltAndroidApp`, compile fails fast with clear error |
| Foreground service crash on Android 14 | MEDIUM | Add `foregroundServiceType` to manifest + permission + `ServiceCompat.startForeground()` call, test on API 34 |
| Downloads cancelled on activity kill | HIGH (architecture) | Migrate download execution to foreground service — requires architecture change, not a quick fix |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| `libpython.zip.so` ENOENT — init crash | Phase 1: Fix initialization | Install debug APK on real device; confirm "Initializing YoutubeDL..." succeeds |
| AGP version `8.13.1` typo | Phase 1: Dependency stabilization | Successful `./gradlew assembleDebug` from clean |
| All three youtubedl artifacts same version | Phase 1: Dependency stabilization | Check `libs.versions.toml` — single version ref for all three |
| ProGuard strips Hilt classes | Phase 2: Hilt introduction | Install `assembleRelease` APK; launch app; complete one download |
| Foreground service type for Android 14 | Phase 3: Foreground service | Test on API 34 emulator; confirm service starts and notification appears |
| `mutableStateOf` written off main thread | Phase 2: ViewModel state refactor | Add StrictMode to debug builds; run full download flow |
| ViewModel coroutine cancelled on activity kill | Phase 3: Foreground service | Start download, swipe app from recents, reopen; verify download completed |
| `updateYoutubeDL()` hangs indefinitely | Phase 1/2: Init improvements | Disable network; launch app; confirm it proceeds to READY within 30s |
| AAB build breaks youtubedl-android | Phase 1: Build config | Never use `bundleRelease` task; document as permanent APK-only constraint |
| `@HiltAndroidApp` missing | Phase 2: Hilt introduction | Compile error or clear runtime error immediately after adding Hilt |
| Hilt injection in Service wrong pattern | Phase 3: Foreground service | Use `@Inject` field injection with `@AndroidEntryPoint`; verify via logcat |

---

## Sources

- [youtubedl-android Issue #105: libpython.zip.so ENOENT](https://github.com/yausername/youtubedl-android/issues/105) — HIGH confidence: official repo issue
- [youtubedl-android Issue #116: AAB build failure](https://github.com/yausername/youtubedl-android/issues/116) — HIGH confidence: official repo issue
- [youtubedl-android Releases](https://github.com/yausername/youtubedl-android/releases) — HIGH confidence: official releases, verified 0.18.1 is latest
- [Android Developers: Foreground service types required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) — HIGH confidence: official docs
- [Android Developers: Foreground service types reference](https://developer.android.com/develop/background-work/services/fgs/service-types) — HIGH confidence: official docs
- [Dagger Hilt: Android Entry Points](https://dagger.dev/hilt/android-entry-point.html) — HIGH confidence: official Dagger/Hilt docs
- [Dagger Hilt: ViewModels](https://dagger.dev/hilt/view-model.html) — HIGH confidence: official Dagger/Hilt docs
- [Dagger Hilt: Migration Guide](https://dagger.dev/hilt/migration-guide.html) — HIGH confidence: official docs
- [Android Developers: AGP 8.3.0 Release Notes](https://developer.android.com/build/releases/past-releases/agp-8-3-0-release-notes) — HIGH confidence: official docs
- [Compose + ViewModel + MutableState thread safety discussion](https://slack-chats.kotlinlang.org/t/2206468/compose-viewmodel-mutablestate-combination-is-error-prone-du) — MEDIUM confidence: community discussion, consistent with official docs
- [Why Android 14's Foreground Service Requirements Break Apps](https://medium.com/gravel-engineering/why-android-14s-foreground-service-requirements-might-break-your-app-and-how-to-fix-it-c1cbcf469b69) — MEDIUM confidence: third-party but consistent with official docs
- [R8 Compatibility FAQ](https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md) — HIGH confidence: official R8 docs
- [Android Developers: Best practices for coroutines](https://developer.android.com/kotlin/coroutines/coroutines-best-practices) — HIGH confidence: official docs

---
*Pitfalls research for: Android yt-dlp video/audio downloader refactoring (YT2Local)*
*Researched: 2026-02-27*
