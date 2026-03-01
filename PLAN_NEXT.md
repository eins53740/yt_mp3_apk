# YT2Local — Next Improvements Plan

## Priority 1: Reliability

### P1-1: Foreground Service for Downloads
Downloads die if the user leaves the app or Android kills the activity.
- Create `DownloadService` extending `Service` with `startForeground()` notification
- Move `repository.downloadMedia()` call from ViewModel into the service
- ViewModel binds to service to observe progress
- Service posts completion notification (replace current ViewModel notification)
- Update `AndroidManifest.xml` with `<service>` declaration and `FOREGROUND_SERVICE_DATA_SYNC` type

**Files:** new `DownloadService.kt`, `MainViewModel.kt`, `AndroidManifest.xml`

### P1-2: Skip Update Button
If yt-dlp update hangs on bad network, user is stuck in UPDATING state.
- Add a "Skip" `TextButton` visible when `appState == UPDATING`
- On click, cancel the update coroutine and transition to READY
- Store a `Job` reference for the update coroutine in ViewModel

**Files:** `MainViewModel.kt`, `MainScreen.kt`

### P1-3: Better Error Recovery
- If download fails, keep the URL in the field (don't clear) so user can retry
- Add "Copy error" button on error messages for bug reports
- Catch `CancellationException` separately to distinguish user-cancel from failure

**Files:** `MainViewModel.kt`, `MainScreen.kt`

---

## Priority 2: Core Features

### P2-1: Download Queue
Let users share multiple URLs and process them sequentially.
- Add `downloadQueue: MutableList<QueueItem>` to ViewModel (URL + format)
- `startDownload()` adds to queue; a loop processes one at a time
- Show queue count badge on download button ("Download (3 queued)")
- Each completed item triggers notification + history entry
- If a foreground service exists (P1-1), the queue lives there

**Files:** `MainViewModel.kt`, `MainScreen.kt`, possibly `DownloadService.kt`

### P2-2: Open File from History
Tap a history item to play the downloaded file.
- Store the MediaStore `Uri` in `DownloadHistoryItem` (returned from `moveToMediaStore`)
- On tap, launch `Intent(ACTION_VIEW)` with the URI and mime type
- Add clickable modifier to `HistoryItem` card

**Files:** `VideoRepository.kt`, `MainViewModel.kt`, `MainScreen.kt`

### P2-3: Persistent Download History
History is lost on app kill (in-memory list).
- Use SharedPreferences with JSON serialization (simple, no Room needed for 10 items)
- Load on ViewModel init, save after each download
- Add Gson or kotlinx-serialization dependency (or manual JSON)

**Files:** `MainViewModel.kt`, `build.gradle.kts` (if adding serialization lib)

### P2-4: Download Quality Picker
Let users choose quality instead of always "best".
- Audio: 128kbps / 192kbps / 320kbps (VBR best)
- Video: 720p / 1080p / Best
- Store preference in SharedPreferences
- Map to yt-dlp `-f` format strings in `VideoRepository`
- UI: dropdown or segmented button below the Audio/Video toggle

**Files:** `VideoRepository.kt`, `MainViewModel.kt`, `MainScreen.kt`

---

## Priority 3: Polish

### P3-1: Custom App Icon
Replace default Android icon with a branded one.
- Design icon: music note with download arrow, purple/blue gradient
- Generate adaptive icon (foreground + background layers)
- Replace files in `res/mipmap-*` directories
- Also create a proper monochrome notification icon (`res/drawable/ic_notification.xml`)

**Files:** `res/mipmap-*/`, `res/drawable/ic_notification.xml`, `MainViewModel.kt` (use new notification icon)

### P3-2: Bump Dependencies
Current versions are from 2023. Update:
- Compose BOM: `2023.08.00` → `2024.06.00`+
- Kotlin: `1.9.0` → `2.0.0`+ (requires compose compiler plugin change)
- Activity Compose: `1.8.1` → `1.9.0`+
- Lifecycle: `2.6.2` → `2.8.0`+
- AGP: verify `8.13.1` is intentional (unusually high — may be a typo for `8.3.1`)

**Files:** `gradle/libs.versions.toml`, `build.gradle.kts`

### P3-3: Unit Tests
Add tests for the testable logic:
- `detectPlatform()` — all platform URL variations
- `sanitizeFileName()` — emoji, special chars, length cap
- `extractUrl()` — URLs embedded in text, multiple URLs, no-protocol
- ViewModel state transitions (mock repository)

**Files:** new `app/src/test/java/com/example/yt2local/` tests

### P3-4: Haptic Feedback on Download Complete
- Call `view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` or use Compose `LocalHapticFeedback`
- Trigger alongside the snackbar

**Files:** `MainScreen.kt`

---

## Implementation Order (suggested)

1. P1-2: Skip update button (quick win, improves first-launch UX)
2. P1-3: Better error recovery (quick win)
3. P2-2: Open file from history (medium, high user value)
4. P2-3: Persistent history (medium, pairs with P2-2)
5. P2-4: Quality picker (medium)
6. P1-1: Foreground service (larger refactor, highest reliability impact)
7. P2-1: Download queue (depends on P1-1 ideally)
8. P3-1: Custom icon (visual polish)
9. P3-2: Bump dependencies (maintenance)
10. P3-3: Unit tests (quality)
11. P3-4: Haptic feedback (tiny polish)
