# Architecture Research

**Domain:** Android video/audio downloader app (yt-dlp wrapper, clean architecture refactor)
**Researched:** 2026-02-27
**Confidence:** HIGH (official Android docs + verified patterns)

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI LAYER                                 │
│  ┌──────────────────┐  ┌────────────────────────────────────┐   │
│  │   MainActivity   │  │         MainScreen (Compose)        │   │
│  │  (intent entry)  │  │  (observes ViewModel StateFlow)     │   │
│  └────────┬─────────┘  └──────────────────┬─────────────────┘   │
│           │                               │                      │
│           └──────────────┬────────────────┘                      │
│                          ↓                                        │
│              ┌───────────────────────┐                           │
│              │    MainViewModel      │                           │
│              │  (@HiltViewModel)     │                           │
│              │  exposes StateFlow    │                           │
│              └───────────┬───────────┘                           │
├──────────────────────────┼──────────────────────────────────────┤
│                    DOMAIN LAYER                                   │
│              ┌───────────┴───────────┐                           │
│              │  DownloadUseCase      │                           │
│              │  GetHistoryUseCase    │                           │
│              │  UpdatePrefsUseCase   │                           │
│              └───────────┬───────────┘                           │
├──────────────────────────┼──────────────────────────────────────┤
│                     DATA LAYER                                    │
│  ┌───────────────────────┴────────────────────────────────────┐  │
│  │              DownloadRepository (interface impl)            │  │
│  └──────────┬──────────────────────────────────┬─────────────┘  │
│             ↓                                  ↓                  │
│  ┌──────────────────────┐         ┌────────────────────────┐    │
│  │  DownloadService     │         │  Room DB               │    │
│  │  (Foreground Svc)    │         │  DownloadHistoryDao    │    │
│  │  runs yt-dlp jobs    │         │  SharedPreferences     │    │
│  └──────────────────────┘         └────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `MainActivity` | Intent entry point, permission requests, ViewModel owner | `@AndroidEntryPoint ComponentActivity` |
| `MainScreen` | Declarative UI, observes ViewModel state | Jetpack Compose `@Composable` |
| `MainViewModel` | UI state ownership, use case orchestration, intent-to-download coordination | `@HiltViewModel ViewModel`, exposes `StateFlow<UiState>` |
| `DownloadUseCase` | Orchestrate a single download job end-to-end | Kotlin class, `@Inject constructor` |
| `GetHistoryUseCase` | Query persistent download history | Kotlin class, returns `Flow<List<HistoryItem>>` |
| `DownloadRepository` | Abstract over service binding + Room | Interface in domain; impl in data |
| `DownloadService` | Run yt-dlp/FFmpeg as a foreground service, emit progress | `@AndroidEntryPoint Service`, local binder |
| `YtDlpDataSource` | Wrap `YoutubeDL.execute()` API calls | Plain Kotlin class injected into service |
| `MediaStoreDataSource` | Write finished files to `Downloads/yt2local/` via MediaStore | Plain Kotlin class |
| `DownloadHistoryDao` | Room DAO for persistent history queries | `@Dao interface` |
| `YT2LocalDatabase` | Room database definition | `@Database abstract class` |
| `AppPreferences` | Read/write SharedPreferences for format preference | Kotlin class, `@Singleton` |

---

## Recommended Project Structure

```
app/src/main/java/com/example/yt2local/
├── YT2LocalApplication.kt          # @HiltAndroidApp, notification channel creation
│
├── di/
│   ├── AppModule.kt                 # @Singleton bindings: db, prefs, data sources
│   └── DownloadModule.kt            # @Provides DownloadRepository impl
│
├── ui/
│   ├── MainActivity.kt              # @AndroidEntryPoint, intent handling
│   ├── MainScreen.kt                # Compose UI, observes ViewModel state
│   ├── MainViewModel.kt             # @HiltViewModel, StateFlow<DownloaderUiState>
│   └── UiState.kt                   # Sealed/data class: DownloaderUiState
│
├── domain/
│   ├── model/
│   │   ├── DownloadJob.kt           # URL, format, status, progress
│   │   ├── DownloadResult.kt        # success/failure + fileName
│   │   └── HistoryItem.kt           # persistent record
│   ├── repository/
│   │   └── DownloadRepository.kt    # interface
│   └── usecase/
│       ├── StartDownloadUseCase.kt
│       ├── GetDownloadHistoryUseCase.kt
│       └── UpdateFormatPreferenceUseCase.kt
│
├── data/
│   ├── repository/
│   │   └── DownloadRepositoryImpl.kt   # impl: connects service + Room
│   ├── service/
│   │   ├── DownloadService.kt           # @AndroidEntryPoint foreground service
│   │   ├── DownloadServiceConnection.kt # ServiceConnection wrapper
│   │   └── DownloadNotificationManager.kt
│   ├── local/
│   │   ├── YT2LocalDatabase.kt          # @Database Room definition
│   │   ├── DownloadHistoryDao.kt        # @Dao
│   │   └── DownloadHistoryEntity.kt     # @Entity Room table
│   ├── source/
│   │   ├── YtDlpDataSource.kt           # wraps YoutubeDL.execute()
│   │   └── MediaStoreDataSource.kt      # writes to MediaStore Downloads
│   └── prefs/
│       └── AppPreferences.kt            # SharedPreferences wrapper
│
└── util/
    ├── UrlExtractor.kt                  # extractUrl(), detectPlatform()
    └── FileNameSanitizer.kt             # sanitizeFileName()
```

### Structure Rationale

- **`di/`:** Centralises all Hilt module definitions; keeps `@Provides`/`@Binds` out of production classes
- **`ui/`:** Only Compose and ViewModel — zero data-layer imports allowed
- **`domain/`:** Pure Kotlin, no Android framework imports; maximum testability
- **`data/`:** All Android-framework-dependent code lives here (Service, Room, MediaStore)
- **`util/`:** Stateless helpers that are framework-free and easily unit-tested

---

## Architectural Patterns

### Pattern 1: Foreground Service with Local Binder

**What:** The download service runs as both a started and a bound service. The `DownloadRepositoryImpl` in the data layer maintains a `ServiceConnection`, binds to the service when a download is requested, and exposes a `StateFlow<DownloadJob>` obtained from the service's local `Binder`.

**When to use:** When the service runs in the same process (standard for this app) and the ViewModel needs live progress updates from it.

**Trade-offs:**
- Pro: Direct in-process method calls, no IPC overhead
- Pro: StateFlow emissions from service are observable from ViewModel via repository
- Con: Binding lifecycle adds complexity (must unbind on ViewModel cleared)
- Con: Service must be started separately from binding (use `startForegroundService` + `bindService`)

**Example:**
```kotlin
// Inside DownloadService
inner class DownloadBinder : Binder() {
    fun getService(): DownloadService = this@DownloadService
}

// Exposed state from service
val downloadState: StateFlow<DownloadJob?> = _downloadState.asStateFlow()

override fun onBind(intent: Intent): IBinder = DownloadBinder()

// In DownloadRepositoryImpl
private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val service = (binder as DownloadService.DownloadBinder).getService()
        _downloadState.value = service.downloadState // relay to repo's StateFlow
    }
    override fun onServiceDisconnected(name: ComponentName) {
        _downloadState.value = null
    }
}
```

### Pattern 2: UiState Sealed Class with StateFlow

**What:** ViewModel exposes a single `StateFlow<DownloaderUiState>` where `DownloaderUiState` is a sealed class covering all UI states. The UI collects this with `collectAsStateWithLifecycle()`.

**When to use:** Always — this replaces the current scattered `mutableStateOf` fields and provides a single coherent snapshot.

**Trade-offs:**
- Pro: Entire UI state is one observable, easier to reason about
- Pro: `collectAsStateWithLifecycle` stops collection when UI is in background (battery-safe)
- Con: Slightly more boilerplate than individual `mutableStateOf` fields
- Con: Sealed class must be updated for every new state variant

**Example:**
```kotlin
sealed class DownloaderUiState {
    data object Initializing : DownloaderUiState()
    data object Updating : DownloaderUiState()
    data class Ready(
        val url: String,
        val isAudio: Boolean,
        val detectedPlatform: String,
        val history: List<HistoryItem>
    ) : DownloaderUiState()
    data class Downloading(
        val url: String,
        val progress: Float,
        val statusLine: String
    ) : DownloaderUiState()
    data class Error(val message: String) : DownloaderUiState()
}

// ViewModel
private val _uiState = MutableStateFlow<DownloaderUiState>(DownloaderUiState.Initializing)
val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

// Compose UI
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### Pattern 3: Hilt Component Scoping

**What:** Bind singleton dependencies (`YT2LocalDatabase`, `AppPreferences`, `YtDlpDataSource`) at `SingletonComponent` scope. Bind `DownloadRepository` at `SingletonComponent` so both ViewModel and Service share the same instance.

**When to use:** For this app, repository and data sources should all be `@Singleton` — there is only one download engine running at a time.

**Trade-offs:**
- Pro: Repository shared between ViewModel and Service without a global static
- Pro: Compile-time DI graph verification catches missing bindings
- Con: `@Singleton` objects live for app lifetime; must not hold Activity/View references

**Example:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): YT2LocalDatabase =
        Room.databaseBuilder(ctx, YT2LocalDatabase::class.java, "yt2local.db").build()

    @Provides
    fun provideDao(db: YT2LocalDatabase): DownloadHistoryDao = db.downloadHistoryDao()
}
```

---

## Data Flow

### Download Trigger Flow (share intent path)

```
Share Intent arrives at MainActivity.onNewIntent()
    ↓
MainActivity.handleIntent(url) → viewModel.setUrlFromIntent(url, autoStart=true)
    ↓
MainViewModel: sets url, sets autoDownloadPending = true
    ↓
Compose LaunchedEffect watches (appState == READY && autoDownloadPending)
    ↓
viewModel.consumeAutoDownload() → StartDownloadUseCase.invoke(url, isAudio)
    ↓
StartDownloadUseCase → DownloadRepository.startDownload(job)
    ↓
DownloadRepositoryImpl:
  1. context.startForegroundService(DownloadService.startIntent(job))
  2. context.bindService(DownloadService, serviceConnection, BIND_AUTO_CREATE)
    ↓
DownloadService.onStartCommand():
  1. startForeground(NOTIFICATION_ID, buildProgressNotification())
  2. launch coroutine: YtDlpDataSource.execute(job) { progress → emit to _downloadState }
    ↓
serviceConnection.onServiceConnected() → relay service StateFlow into repo's StateFlow
    ↓
DownloadRepositoryImpl emits DownloadJob(progress) via StateFlow
    ↓
StartDownloadUseCase collects, maps to domain events, emits to ViewModel
    ↓
MainViewModel collects, updates _uiState to Downloading(progress)
    ↓
MainScreen recomposes with progress bar + status text
```

### Download Completion Flow

```
YtDlpDataSource.execute() returns (yt-dlp exits)
    ↓
DownloadService: MediaStoreDataSource.write(tempFile) → fileName
    ↓
DownloadService: updates _downloadState to DownloadJob(status=COMPLETE, fileName)
    ↓
DownloadService: posts completion notification, calls stopForeground(STOP_FOREGROUND_REMOVE)
    ↓
DownloadService: stopSelf() (service ends naturally)
    ↓
DownloadRepositoryImpl: receives COMPLETE state, inserts HistoryItem via DownloadHistoryDao
    ↓
MainViewModel: collects COMPLETE event, updates _uiState to Ready + snackbar message
    ↓
MainScreen: shows Snackbar "Saved: <filename>"
```

### State Management

```
DownloadService._downloadState (MutableStateFlow)
    ↓ (via local binder)
DownloadRepositoryImpl._serviceState (StateFlow relay)
    ↓ (collected in StartDownloadUseCase)
MainViewModel._uiState (MutableStateFlow<DownloaderUiState>)
    ↓ (collectAsStateWithLifecycle)
MainScreen (Compose recomposition)
```

### Key Data Flows

1. **Intent → auto-download:** `Intent (MainActivity)` → `ViewModel.setUrlFromIntent()` → `autoDownloadPending flag` → `LaunchedEffect` → `UseCase` → `Service`
2. **Progress reporting:** `YoutubeDL.execute() callback` → `Service._downloadState` → `Repository StateFlow relay` → `ViewModel._uiState` → `Compose recomposition`
3. **History persistence:** `Service completion` → `RepositoryImpl inserts Room entity` → `GetHistoryUseCase returns Flow<List<HistoryItem>>` → `ViewModel` → `UI list`
4. **Format preference:** `ViewModel.onFormatChange()` → `UpdateFormatPreferenceUseCase` → `AppPreferences.setIsAudio()` → `SharedPreferences`

---

## Build Order

Build in this dependency order to avoid blocked work:

| Phase | Components | Why This Order |
|-------|------------|----------------|
| 1 | `domain/model/`, `domain/repository/` interface, utility classes | Zero dependencies; establish contracts everything else builds against |
| 2 | `data/local/` (Room entities, DAOs, Database), `data/prefs/` | Only depends on domain models; Room codegen runs early |
| 3 | `data/source/YtDlpDataSource`, `data/source/MediaStoreDataSource` | Wraps existing VideoRepository logic; testable in isolation |
| 4 | `data/service/DownloadService` + `DownloadNotificationManager` | Depends on data sources; central to the new architecture |
| 5 | `data/repository/DownloadRepositoryImpl` + `ServiceConnection` | Wires service + Room + data sources behind the interface |
| 6 | `di/` Hilt modules | Registers all bindings; requires all implementations to exist |
| 7 | `domain/usecase/` | Depends on repository interface; thin orchestration layer |
| 8 | `ui/MainViewModel` + `ui/UiState` | Consumes use cases; replaces current ViewModel |
| 9 | `ui/MainScreen` + `ui/MainActivity` | Final UI wiring; update Compose to collect `StateFlow` |

---

## Anti-Patterns

### Anti-Pattern 1: Download Logic in ViewModel Coroutine

**What people do:** Launch `YoutubeDL.execute()` directly inside `viewModelScope.launch {}` as the current code does.

**Why it's wrong:** `viewModelScope` is cancelled when the ViewModel is cleared (which happens when the activity is destroyed). A rotation, Back navigation, or task-switcher kill will abort the in-progress download silently.

**Do this instead:** Run the download in a foreground service. The ViewModel communicates with the service via `DownloadRepository`; the service's coroutine scope (tied to `Service.lifecycleScope`) survives activity destruction.

### Anti-Pattern 2: ViewModel Owning Android Context Directly

**What people do:** Pass `Application` context into a data operation directly in the ViewModel (current code does `VideoRepository(application)`).

**Why it's wrong:** Tight coupling between presentation and data layers; impossible to unit-test ViewModel without Android instrumentation.

**Do this instead:** Inject `DownloadRepository` (pure interface) into the ViewModel via Hilt. The repository implementation holds the context dependency; the ViewModel never touches `Context`.

### Anti-Pattern 3: Scattered `mutableStateOf` Fields

**What people do:** Expose 9+ separate `mutableStateOf` properties from the ViewModel and let the UI observe them individually (current code pattern).

**Why it's wrong:** UI can read an intermediate inconsistent state when multiple fields are updated sequentially in the same frame. Hard to track what state combinations are valid.

**Do this instead:** Model UI state as a single sealed class exposed as `StateFlow<DownloaderUiState>`. Update atomically by replacing the entire state object.

### Anti-Pattern 4: SharedPreferences Directly in ViewModel

**What people do:** Instantiate `SharedPreferences` inside `MainViewModel.init {}` and call `.edit().putBoolean()` from ViewModel methods.

**Why it's wrong:** Mixes persistence concern (data layer) into presentation layer; impossible to swap storage without changing ViewModel.

**Do this instead:** Wrap preferences in `AppPreferences` (data layer), inject via `UpdateFormatPreferenceUseCase`. ViewModel calls use case; use case calls preferences.

### Anti-Pattern 5: Binding a Foreground Service Without Starting It First

**What people do:** Call only `bindService()` expecting the service to run in the foreground.

**Why it's wrong:** A bound-only service can be killed by the OS under memory pressure without a foreground notification; it also won't survive past the last client unbind.

**Do this instead:** Always call `startForegroundService(intent)` first, then `bindService()`. Start makes the service persistent; bind gives direct in-process access. When the download completes, call `stopSelf()` from within the service.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| `youtubedl-android` (junkfood02) | `YtDlpDataSource` wraps `YoutubeDL.getInstance().execute()` | Init still runs in Application; init failure → `ERROR` state |
| `FFmpeg` (junkfood02) | Initialized in Application or service onCreate; no direct call needed — yt-dlp invokes it internally | Init failure is non-fatal for video; fatal for audio extraction |
| `Aria2c` (junkfood02) | Optional; init failure tolerated (already handled correctly) | Version must match youtubedl-android and ffmpeg artifacts |
| MediaStore | `MediaStoreDataSource.write()` called from service after download completes | API 29+ `RELATIVE_PATH` required; min SDK 26 means some API 26/27/28 devices need `File` path fallback |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `MainViewModel` ↔ `DownloadRepository` | Interface methods + `StateFlow` | ViewModel never sees Service or Room directly |
| `DownloadRepositoryImpl` ↔ `DownloadService` | Local `Binder`, `ServiceConnection` | Same-process binding; no AIDL needed |
| `DownloadService` ↔ `YtDlpDataSource` | Direct method call (both in data layer) | Service owns the coroutine scope for the download job |
| `DownloadRepositoryImpl` ↔ `Room` | `DownloadHistoryDao` suspend functions | Only called after job completes, not during download |
| `MainViewModel` ↔ `GetHistoryUseCase` | `Flow<List<HistoryItem>>` collected in `viewModelScope` | History updates automatically when Room data changes |

---

## Scaling Considerations

This is a single-user local app — "scale" means adding features without architectural debt:

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Single download (current) | Foreground service with one active job slot; service stops when job completes |
| Download queue (next phase) | Add `Queue<DownloadJob>` inside `DownloadService`; process sequentially; service stays alive until queue empty |
| Concurrent downloads (future) | Move from single `_downloadState` to `Map<jobId, DownloadJob>`; ViewModel maps to list in UI state |
| Multi-module (future) | `:domain`, `:data`, `:ui` as separate Gradle modules; current single-module structure already follows the boundaries |

### Scaling Priorities

1. **First bottleneck:** Service lifecycle — one poorly-scoped `startForegroundService` call can leave zombie services. Prevent by always calling `stopSelf()` on completion and testing the `onTaskRemoved()` path.
2. **Second bottleneck:** Room query on main thread — use `suspend` DAO functions and collect history as `Flow` to keep main thread clear.

---

## Sources

- [Android App Architecture — official guide](https://developer.android.com/topic/architecture) — HIGH confidence
- [Foreground Services — official guide](https://developer.android.com/develop/background-work/services/foreground-services) — HIGH confidence
- [Bound Services — official guide](https://developer.android.com/develop/background-work/services/bound-services) — HIGH confidence
- [Hilt Dependency Injection — official guide](https://developer.android.com/training/dependency-injection/hilt-android) — HIGH confidence
- [StateFlow and SharedFlow — official guide](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) — HIGH confidence
- [Room persistence library — official guide](https://developer.android.com/training/data-storage/room) — HIGH confidence
- [Domain Layer — official guide](https://developer.android.com/topic/architecture/domain-layer) — HIGH confidence
- [Modern Android Architecture 2025 — MVVM/MVI/Clean](https://medium.com/@androidlab/modern-android-app-architecture-in-2025-mvvm-mvi-and-clean-architecture-with-jetpack-compose-c0df3c727334) — MEDIUM confidence (community source, consistent with official docs)
- [Bound Services with MVVM example](https://github.com/mitchtabian/Bound-Services-with-MVVM) — MEDIUM confidence (practical pattern reference)

---
*Architecture research for: YT2Local — Android yt-dlp downloader clean architecture refactor*
*Researched: 2026-02-27*
