# Phase 3: Reliability - Research

**Researched:** 2026-03-01
**Domain:** Android Foreground Service (API 34), Room Database 2.8.4, Error UX
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| REL-01 | Downloads continue when user backgrounds app (foreground service with notification) | Foreground service with `dataSync` type + `ServiceCompat.startForeground()` API fully documented |
| REL-02 | Download history persists across app restarts (Room database) | Room 2.8.4 with KSP + Flow DAO is the standard; drops in as new dependency |
| REL-04 | User can tap history item to open/play the downloaded file | `Intent(ACTION_VIEW, uri)` with `setDataAndType` — uri stored as String in Room entity |
| ARCH-03 | Errors displayed as user-friendly messages with retry option (no stack traces) | `parseError()` already exists in `VideoRepository`; gap is surfacing it properly in UI with a dedicated retry button |
| ARCH-04 | URL field preserved on download failure (user doesn't have to re-enter) | `DownloaderUiState.url` is cleared on success but not preserved on failure — one-line state fix in ViewModel |
</phase_requirements>

---

## Summary

Phase 3 introduces three distinct technical concerns that compose together cleanly. The foreground service (REL-01) is the most architecturally impactful change: the download coroutine must move out of `viewModelScope` (which dies with the app) and into a `Service` that keeps the process alive when backgrounded. Room (REL-02/REL-04) is a straightforward additive dependency — the existing `DownloadHistoryItem` data class maps directly to a Room entity with zero schema redesign required. Error UX (ARCH-03/ARCH-04) is the smallest change: the error message mapping already lives in `VideoRepository.parseError()`, and URL preservation on failure requires a single state-update fix in `MainViewModel.startDownload()`.

The most critical decision in this phase is how the ViewModel and Service communicate state. The recommended pattern is a SharedFlow or StateFlow that lives in a Hilt-injected singleton `DownloadManager` (or similar), updated by the Service and observed by the ViewModel. This avoids the complexity of bound service `ServiceConnection` patterns while keeping the architecture clean and testable.

The one latent risk is Android 15: `dataSync` foreground services now have a 6-hour cumulative runtime limit per 24-hour window. Since the app targets API 34, this limit does not apply yet, but it must be tracked for the API 35 upgrade.

**Primary recommendation:** Use a `@AndroidEntryPoint` foreground service injected via Hilt, with a `@Singleton DownloadStateHolder` (a shared `MutableStateFlow`) that both the Service and ViewModel hold a reference to. The ViewModel starts/stops the service via `context.startForegroundService(intent)`. The Service executes the download, updates `DownloadStateHolder`, and the ViewModel observes it.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.room:room-runtime` | 2.8.4 | SQLite persistence layer | Official Jetpack, current stable as of Nov 2025 |
| `androidx.room:room-compiler` (ksp) | 2.8.4 | KSP annotation processor for Room | KSP already in project; faster than KAPT |
| `androidx.room:room-ktx` | 2.8.4 | Kotlin coroutines + Flow extensions for Room | Required for `Flow<List<T>>` DAO returns |
| `ServiceCompat.startForeground()` | androidx-core 1.15.0 | Cross-version foreground service promotion | Already in project; handles API level branching |
| Hilt `@AndroidEntryPoint` on Service | 2.59.2 | Constructor injection in Service | Already in project; same annotation as Activity |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` | API 29+ | Type constant for startForeground call | Required for Android 10+ when declaring service type |
| `Intent(ACTION_VIEW).setDataAndType(uri, mimeType)` | core Android | Open downloaded file in system app | REL-04: tap-to-open history item |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Foreground Service | WorkManager foreground worker | WorkManager adds scheduling semantics that are unnecessary for a user-triggered immediate download; foreground service is more direct |
| Foreground Service | `dataSync` type (current plan) | `mediaProcessing` would also be valid for audio/video; `dataSync` is what the roadmap specifies and matches the data-transfer nature of yt-dlp |
| Room Flow DAO | LiveData DAO | Flow is idiomatic for coroutines + Compose; LiveData is legacy |
| Shared `StateFlow` singleton | Bound service `ServiceConnection` | `ServiceConnection` adds lifecycle complexity; a Hilt singleton is simpler and sufficient for a single-download-at-a-time app |

**Installation (add to `libs.versions.toml` and `app/build.gradle.kts`):**

```toml
# libs.versions.toml
[versions]
room = "2.8.4"

[libraries]
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
```

---

## Architecture Patterns

### Recommended Project Structure

```
app/src/main/java/com/example/yt2local/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt          # @Database class
│   │   ├── DownloadHistoryDao.kt   # @Dao interface
│   │   └── DownloadHistoryEntity.kt # @Entity (replaces in-memory DownloadHistoryItem)
│   └── DownloadStateHolder.kt      # @Singleton MutableStateFlow for service→ViewModel comm
├── service/
│   └── DownloadService.kt          # @AndroidEntryPoint foreground service
├── di/
│   └── AppModule.kt                # Add Room + DownloadStateHolder provisions
├── MainViewModel.kt                # Observes DownloadStateHolder, starts/stops service
└── [existing files unchanged]
```

### Pattern 1: Android 14 Three-Part Foreground Service Declaration

**What:** Declaring `dataSync` foreground service requires manifest declaration, permission, and correct `startForeground()` call simultaneously. Missing any one part causes a crash on API 34+.

**When to use:** Every foreground service targeting API 34+.

**Manifest (all three must be present together):**
```xml
<!-- Permission 1: base foreground service permission (already declared) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Permission 2: type-specific permission (NEW — must add) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Service declaration with type (NEW — must add inside <application>) -->
<service
    android:name=".service.DownloadService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

**Service (inside onStartCommand):**
```kotlin
// Source: https://developer.android.com/develop/background-work/services/fgs/launch
ServiceCompat.startForeground(
    /* service = */ this,
    /* id = */      NOTIFICATION_ID,   // must not be 0
    /* notification = */ buildNotification("Starting download…"),
    /* foregroundServiceType = */ ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
)
```

**Caller (ViewModel or Activity):**
```kotlin
val intent = Intent(context, DownloadService::class.java).apply {
    putExtra("url", url)
    putExtra("isAudio", isAudio)
}
context.startForegroundService(intent)
```

### Pattern 2: Hilt Injection in Foreground Service

**What:** `@AndroidEntryPoint` on a `Service` subclass enables field injection, same as on an `Activity`.

**When to use:** Any service that needs injected dependencies (repository, DAO, DownloadStateHolder).

```kotlin
// Source: https://developer.android.com/training/dependency-injection/hilt-android
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var repository: VideoRepository
    @Inject lateinit var downloadStateHolder: DownloadStateHolder
    @Inject lateinit var historyDao: DownloadHistoryDao

    // @Inject fields are safe to use from onStartCommand() onward, NOT in onCreate()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
```

Note: The service creates its own `CoroutineScope` (not `viewModelScope`) so coroutines survive ViewModel destruction.

### Pattern 3: Shared StateFlow for Service → ViewModel Communication

**What:** A Hilt `@Singleton` holding a `MutableStateFlow` that the Service writes to and the ViewModel observes. Avoids bound service complexity entirely.

```kotlin
// data/DownloadStateHolder.kt
class DownloadStateHolder @Inject constructor() {
    val state: MutableStateFlow<DownloadServiceState> =
        MutableStateFlow(DownloadServiceState.Idle)
}

sealed class DownloadServiceState {
    object Idle : DownloadServiceState()
    data class InProgress(val progress: Float, val status: String) : DownloadServiceState()
    data class Success(val fileName: String) : DownloadServiceState()
    data class Failed(val friendlyMessage: String, val originalUrl: String) : DownloadServiceState()
}
```

The ViewModel observes `downloadStateHolder.state` in `viewModelScope.launch { collect { … } }`. When the Service finishes, it updates the StateFlow and calls `stopSelf()`. The ViewModel reacts and updates `DownloaderUiState`.

### Pattern 4: Room Entity, DAO, and Database

**What:** Standard Room setup using KSP (already in project).

```kotlin
// DownloadHistoryEntity.kt
@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val platform: String,
    val isAudio: Boolean,
    val timestamp: Long,
    val mediaUri: String?   // MediaStore URI string — needed for tap-to-open (REL-04)
)
```

```kotlin
// DownloadHistoryDao.kt
@Dao
interface DownloadHistoryDao {
    // Returns newest-first, limited to 10 — replaces in-memory list.take(9) logic
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecent(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE id NOT IN (SELECT id FROM download_history ORDER BY timestamp DESC LIMIT 10)")
    suspend fun pruneOld()
}
```

```kotlin
// AppDatabase.kt
@Database(entities = [DownloadHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao
}
```

**AppModule provision:**
```kotlin
@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "yt2local.db").build()

@Provides
@Singleton
fun provideDownloadHistoryDao(db: AppDatabase): DownloadHistoryDao = db.downloadHistoryDao()
```

### Pattern 5: Tap-to-Open History Item (REL-04)

**What:** Store the `MediaStore` URI as a String in Room, then fire `ACTION_VIEW` intent from the UI.

The `moveToMediaStore()` method in `VideoRepository` already receives the URI from `resolver.insert()`. Return that URI in `DownloadResult` and store it in the `DownloadHistoryEntity`.

```kotlin
// In MainScreen, HistoryItem composable — add onClick:
val context = LocalContext.current
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable {
            item.mediaUri?.let { uriString ->
                val uri = Uri.parse(uriString)
                val mime = if (item.isAudio) "audio/*" else "video/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open with"))
            }
        }
) { … }
```

Note: `FLAG_GRANT_READ_URI_PERMISSION` is required for `content://` MediaStore URIs shared to other apps.

### Pattern 6: URL Preservation on Failure (ARCH-04)

**What:** Current `startDownload()` clears `url` on success but also (implicitly) leaves `url` in state during download. On failure, the `url` field remains — the only bug is that `statusMessage` shows the error. No URL clearing happens on the failure branch today.

Looking at `MainViewModel.startDownload()` failure branch (lines 257-264):
```kotlin
state.copy(
    appState = AppState.READY,
    downloadProgress = 0f,
    progressStatus = "",
    statusMessage = "Error: ${result.error}"
    // url and detectedPlatform are NOT cleared — URL is already preserved
)
```

The URL is already preserved on failure. ARCH-04 is satisfied by ensuring this behavior remains in the refactored service-based flow. The gap is in the error message format — it shows `"Error: ${result.error}"` which may include raw exception text if `parseError()` falls through to the `else` branch. The fix is ensuring the failure path in the Service always uses `parseError()` before returning the message.

### Anti-Patterns to Avoid

- **Do not launch `startDownload()` in `viewModelScope` after introducing the service.** The download must execute inside the Service, not in the ViewModel. The ViewModel should only start/stop the Service and observe state.
- **Do not use `GlobalScope` for the service coroutine.** Use a scope tied to the Service's lifetime (`CoroutineScope(SupervisorJob() + Dispatchers.IO)` cancelled in `onDestroy()`).
- **Do not call `startForeground()` later than `onStartCommand()`.** Android requires the call within a few seconds of `startForegroundService()` — do it as the first action in `onStartCommand()` before launching any coroutine.
- **Do not store `File` paths in Room.** Store `MediaStore` `content://` URIs instead. File paths break across API levels and are inaccessible to other apps.
- **Do not reuse notification ID 0.** Android rejects ID 0 for foreground service notifications.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Persistent download history | Custom SQLite queries | Room 2.8.4 | Schema migration, thread safety, Flow integration all handled |
| Cross-version foreground service start | Version-check `if (Build.VERSION.SDK_INT >= …)` branching | `ServiceCompat.startForeground()` | Handles API level differences, ForegroundServiceStartNotAllowedException |
| Error parsing from yt-dlp exceptions | New error mapper | Extend existing `VideoRepository.parseError()` | Already has 9 cases; just move into shared location if Service needs it |
| MediaStore URI → file open | Custom file access | `Intent(ACTION_VIEW).setDataAndType(uri, mime)` | System handles app chooser, permissions, MIME routing |

**Key insight:** The Room + Hilt + KSP combination is a commodity pattern in this stack. The only novel code is the Service lifecycle management and the `DownloadStateHolder` bridge.

---

## Common Pitfalls

### Pitfall 1: MissingForegroundServiceTypeException on API 34

**What goes wrong:** App crashes with `MissingForegroundServiceTypeException` when `startForeground()` is called without the correct type constant.
**Why it happens:** Android 14 made `foregroundServiceType` mandatory for apps targeting API 34+. This project targets API 34.
**How to avoid:** All three parts of the declaration must be present simultaneously — manifest permission, service element `android:foregroundServiceType`, and `ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_DATA_SYNC)`.
**Warning signs:** `IllegalArgumentException` or `MissingForegroundServiceTypeException` in logcat on first download after backgrounding.

### Pitfall 2: ForegroundServiceStartNotAllowedException (Android 12+)

**What goes wrong:** `startForegroundService()` throws `ForegroundServiceStartNotAllowedException` when called while the app is in the background.
**Why it happens:** Android 12+ restricts background-initiated foreground services. The download is triggered by user action (button tap or share intent) so the app is in the foreground — this should not occur in normal use.
**How to avoid:** Wrap `startForegroundService()` in a try/catch and log the exception. The exception is only a real risk if the download is triggered from a background context (e.g., from a BroadcastReceiver) which this app does not do.
**Warning signs:** Exception only visible in logcat when testing edge cases like starting a download from a notification action.

### Pitfall 3: Room Schema Not Exported Warning

**What goes wrong:** Room emits a compile-time warning about not exporting the schema for migration testing.
**Why it happens:** Default Room behavior requires `exportSchema = true` for production-grade migration tracking.
**How to avoid:** Use `exportSchema = false` for this app (no planned migrations yet, simple schema). Add `@Database(…, exportSchema = false)` to suppress the warning explicitly.
**Warning signs:** Build warning: `Schema export directory is not provided to the annotation processor`.

### Pitfall 4: Service Coroutine Survives but ViewModel Does Not Observe

**What goes wrong:** The download completes in the Service, but the ViewModel (recreated after the user returns) doesn't show the result.
**Why it happens:** If `DownloadStateHolder` emits a terminal state before the ViewModel is ready to collect, the event is lost (StateFlow holds last value — this is actually fine for `Success`/`Failed`, but `Idle` reset timing matters).
**How to avoid:** After the Service finishes and updates `DownloadStateHolder` to `Success`/`Failed`, it should NOT reset to `Idle` immediately. The ViewModel should reset the StateHolder to `Idle` after it has consumed the terminal state. The Service just calls `stopSelf()` after updating.

### Pitfall 5: `@Inject` Fields Not Available in Service's `onCreate()`

**What goes wrong:** NullPointerException accessing `@Inject` fields in `Service.onCreate()`.
**Why it happens:** Hilt performs injection after `super.onCreate()` returns. Fields are guaranteed available from `onStartCommand()` onward.
**How to avoid:** Never access `@Inject` fields in `onCreate()`. Move all initialization logic to `onStartCommand()`.
**Warning signs:** NPE with `lateinit property X has not been initialized` in `onCreate()`.

### Pitfall 6: Android 15 dataSync 6-Hour Limit (Future Risk)

**What goes wrong:** If app is upgraded to target API 35, `dataSync` foreground services are limited to 6 cumulative hours per 24-hour period.
**Why it happens:** Android 15 enforcement of dataSync limits.
**How to avoid:** This app targets API 34 — not affected now. When upgrading to API 35, evaluate switching to `mediaProcessing` service type (for audio/video processing) or User-Initiated Data Transfer Jobs. Track as a Phase 4 / API 35 upgrade concern.
**Warning signs:** Service auto-stopped by system after 6 hours of cumulative daily runtime.

---

## Code Examples

Verified patterns from official sources:

### Foreground Service: Manifest Declaration (Three-Part)

```xml
<!-- Source: https://developer.android.com/develop/background-work/services/fgs/service-types -->
<!-- Part 1: Base permission (already exists in manifest) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Part 2: Type-specific permission (NEW) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Part 3: Service element (NEW, inside <application>) -->
<service
    android:name=".service.DownloadService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### Foreground Service: startForeground Call

```kotlin
// Source: https://developer.android.com/develop/background-work/services/fgs/launch
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = NotificationCompat.Builder(this, YT2LocalApplication.CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Downloading…")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,   // non-zero
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )

    // Extract extras and launch download coroutine
    val url = intent?.getStringExtra("url") ?: run { stopSelf(); return START_NOT_STICKY }
    val isAudio = intent.getBooleanExtra("isAudio", true)

    serviceScope.launch {
        val result = repository.downloadMedia(url, isAudio) { progress ->
            downloadStateHolder.state.value = DownloadServiceState.InProgress(
                progress.progress, progress.status
            )
            updateNotification(progress)
        }
        if (result.success) {
            historyDao.insert(DownloadHistoryEntity(
                fileName = result.fileName ?: "Unknown",
                platform = detectPlatform(url),
                isAudio = isAudio,
                timestamp = System.currentTimeMillis(),
                mediaUri = result.mediaUri
            ))
            downloadStateHolder.state.value = DownloadServiceState.Success(result.fileName ?: "")
        } else {
            downloadStateHolder.state.value = DownloadServiceState.Failed(
                friendlyMessage = result.error ?: "Download failed",
                originalUrl = url
            )
        }
        stopSelf()
    }
    return START_NOT_STICKY
}
```

### Room: Dependency additions

```kotlin
// libs.versions.toml — add:
// [versions] room = "2.8.4"
// [libraries] room-runtime, room-compiler, room-ktx

// app/build.gradle.kts — add:
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
```

### Room: Entity

```kotlin
// Source: https://developer.android.com/training/data-storage/room/defining-data
@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "is_audio") val isAudio: Boolean,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "media_uri") val mediaUri: String?
)
```

### ViewModel: Observing DownloadStateHolder

```kotlin
// In MainViewModel.init {}
viewModelScope.launch {
    downloadStateHolder.state.collect { serviceState ->
        when (serviceState) {
            is DownloadServiceState.InProgress -> _uiState.update { it.copy(
                appState = AppState.DOWNLOADING,
                downloadProgress = serviceState.progress,
                progressStatus = serviceState.status
            )}
            is DownloadServiceState.Success -> {
                _uiState.update { it.copy(
                    appState = AppState.READY,
                    downloadProgress = 0f,
                    progressStatus = "",
                    url = "",             // clear URL on success only
                    snackbarMessage = "Saved: ${serviceState.fileName}"
                )}
                downloadStateHolder.state.value = DownloadServiceState.Idle
                postDownloadNotification(serviceState.fileName)
            }
            is DownloadServiceState.Failed -> {
                _uiState.update { it.copy(
                    appState = AppState.READY,
                    downloadProgress = 0f,
                    progressStatus = "",
                    statusMessage = serviceState.friendlyMessage,
                    // url preserved — not cleared on failure (ARCH-04)
                )}
                downloadStateHolder.state.value = DownloadServiceState.Idle
            }
            is DownloadServiceState.Idle -> { /* no-op */ }
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `startForeground(id, notification)` no type | `ServiceCompat.startForeground(…, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` | Android 14 (API 34) | Mandatory — omitting type crashes on target 34 |
| KAPT for Room annotation processing | KSP | Room 2.6+ | Already using KSP in this project — just add Room |
| `LiveData<List<T>>` from DAO | `Flow<List<T>>` from DAO | Room 2.2 (2019) | Idiomatic for coroutines; integrates with `collectAsStateWithLifecycle` |
| `GlobalScope` in services | Service-owned `CoroutineScope` + `SupervisorJob()` | Coroutines 1.3 best practice | Proper lifecycle scoping, cancellable on `onDestroy()` |

**Deprecated/outdated:**
- `dataSync` service type: Android 15 adds 6-hour runtime limit; `mediaProcessing` or User-Initiated Data Transfer Jobs are the future direction for API 35+ apps.
- `KAPT`: deprecated in AGP 9.0 — this project already uses KSP.

---

## Open Questions

1. **Does `VideoRepository.downloadMedia()` return the MediaStore URI?**
   - What we know: `moveToMediaStore()` calls `resolver.insert()` which returns a `Uri?`. The current `DownloadResult` does not include this URI — it only returns `fileName`.
   - What's unclear: Whether the MediaStore URI will still be valid after the Service dies (it should be, since MediaStore is persistent).
   - Recommendation: Add `mediaUri: String?` field to `DownloadResult` and return `uri.toString()` from `moveToMediaStore()`. This is a one-field addition.

2. **Should `DownloadHistoryItem` data class be deleted after Room entity is introduced?**
   - What we know: `DownloadHistoryItem` is used in `DownloaderUiState.downloadHistory` and referenced in `HistoryItem()` composable. Room uses `DownloadHistoryEntity`.
   - Recommendation: Keep `DownloadHistoryItem` as a UI model (domain object) and map from `DownloadHistoryEntity` → `DownloadHistoryItem` in the ViewModel. This separates DB schema from UI concerns and minimizes changes to `MainScreen.kt`.

3. **How should progress notifications update while app is backgrounded?**
   - What we know: The existing notification only posts on completion. A progress-updating foreground notification requires `NotificationManagerCompat.notify()` calls from within the Service's download loop.
   - Recommendation: Update the service's foreground notification on each significant progress tick (e.g., every 10% or every 5 seconds). Use `NotificationCompat.Builder.setProgress(100, percent, false)`.

---

## Sources

### Primary (HIGH confidence)
- [Android Developers — Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) — three-part declaration, `FOREGROUND_SERVICE_TYPE_DATA_SYNC`, Android 14 requirements
- [Android Developers — Launch a foreground service](https://developer.android.com/develop/background-work/services/fgs/launch) — `ServiceCompat.startForeground()` exact call signature
- [Android Developers — Foreground service types are required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) — `MissingForegroundServiceTypeException` cause and fix
- [Android Developers — Room releases](https://developer.android.com/jetpack/androidx/releases/room) — confirmed version 2.8.4 stable as of Nov 2025
- [Android Developers — Hilt injection](https://developer.android.com/training/dependency-injection/hilt-android) — `@AndroidEntryPoint` on Service pattern

### Secondary (MEDIUM confidence)
- [Android Developers — Alternatives to data sync foreground services](https://developer.android.com/about/versions/15/changes/datasync-migration) — Android 15 6-hour limit confirmed; API 34 apps not affected
- [Android Developers — Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout) — timeout behavior for API 35
- [Medium — Guide to Foreground Services on Android 14](https://medium.com/@domen.lanisnik/guide-to-foreground-services-on-android-9d0127dc8f9a) — community-verified implementation walkthrough

### Tertiary (LOW confidence)
- None identified — all critical claims verified with official docs.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Room 2.8.4 and foreground service API verified against official docs
- Architecture: HIGH — Hilt `@AndroidEntryPoint` on Service is documented pattern; `DownloadStateHolder` SharedFlow bridge is a standard Hilt singleton pattern
- Pitfalls: HIGH — `MissingForegroundServiceTypeException` and Android 14 requirements verified against official Android changelog docs
- Android 15 risk: MEDIUM — confirmed from official docs, but app targets API 34 so not immediately relevant

**Research date:** 2026-03-01
**Valid until:** 2026-09-01 (stable API surface, 6 months)
