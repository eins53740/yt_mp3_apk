# Feature Research

**Domain:** Android video/audio downloader app (yt-dlp-based)
**Researched:** 2026-02-27
**Confidence:** MEDIUM-HIGH (competitive analysis from YTDLnis/Seal source, Android official docs for architecture claims)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| URL input + paste | Every downloader has this | LOW | Already implemented. Clipboard auto-paste on resume is a nice touch. |
| MP3 audio extraction | Primary use case for music | LOW | Already implemented with metadata/thumbnail embed. |
| MP4 video download | Secondary use case | LOW | Already implemented. |
| Download progress indicator | Users need feedback on long operations | LOW | Already implemented. |
| Share intent handling (ACTION_SEND) | Core workflow: share from YouTube app → download starts | MEDIUM | Already implemented. Zero-tap is the key differentiator. |
| System notification on completion | Users leave the app during download | LOW | Already implemented. |
| File saved to Downloads folder | Standard expectation — files discoverable via Files app | LOW | Already implemented via MediaStore. |
| Error messages that are human-readable | Stack traces in UI are unacceptable | LOW | NOT implemented — currently shows raw exceptions. Critical gap. |
| App does not crash on startup | Baseline | LOW | BROKEN — yt-dlp init failure. Must fix first. |
| Downloads survive app backgrounding | Users will switch apps mid-download | HIGH | NOT implemented — ViewModel coroutine dies on activity kill. Critical gap. |
| Format preference persists across sessions | Users don't want to re-select MP3 every time | LOW | Already implemented via SharedPreferences. |
| Download history (in-session) | Users want to know what they got | LOW | Already implemented (in-memory, last 10). |
| Open downloaded file from history | "Tap to play" is expected | LOW | NOT implemented — MediaStore Uri not stored in history items. |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Zero-tap share-to-download | The defining UX feature. Share URL → instant MP3, no confirmation. Competitors require tap. | MEDIUM | Already implemented. Protect at all costs. |
| Clipboard auto-paste on resume | Reduces friction for manual URL entry | LOW | Already implemented. |
| Auto-detect platform from URL | Smarter display in UI; could be used for platform-specific yt-dlp args | LOW | Already implemented. |
| Skip yt-dlp update button | Network issues on first launch leave user stuck in UPDATING state indefinitely. Competitors don't expose this. | LOW | NOT implemented. High UX value, low effort. |
| Audio quality picker (128/192/320kbps) | Power users want control. Competitors like YTDLnis have this. | MEDIUM | NOT implemented. yt-dlp -f format string mapping needed. |
| Video quality picker (720p/1080p/Best) | Power users want control without always downloading 4K | MEDIUM | NOT implemented. Pairs with audio quality picker. |
| Persistent download history | In-memory history dies on app kill. SharedPreferences + JSON survives. | LOW | NOT implemented. Low effort, high perceived quality. |
| Download queue (multi-URL) | Share 5 songs from different tabs → all process sequentially | HIGH | NOT implemented. Requires foreground service first. |
| Metadata + thumbnail embed in MP3 | Album art and ID3 tags in the output file — competitors often omit this | MEDIUM | Already implemented. Genuine differentiator for music use. |
| Haptic feedback on completion | Tactile confirmation without looking at screen | LOW | NOT implemented. Trivial to add. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Built-in media player / playback | "All-in-one" appeal | Scope explosion. Adds 10x complexity. This is a downloader, not a media player. Competes with every existing player. | Store MediaStore URI, launch with ACTION_VIEW intent to user's preferred player. |
| Playlist/batch URL download | Power users want to download whole playlists | Massive complexity: UI for selecting items, queue management, storage estimation, cancellation mid-batch, error-per-item vs error-per-batch. YTDLnis has 50+ issues about playlist behavior. | Queue individual URLs. That solves 80% of the use case at 10% of the cost. |
| In-app browser / URL detection from browser history | Convenience of not switching apps | Privacy sensitivity (reading browser history), implementation complexity, API restrictions on modern Android. | Rely on Android share intents — the correct separation of concerns. |
| Background sync / scheduled downloads | "Download when on WiFi overnight" | Requires persistent state, battery optimization exemptions, WorkManager complexity, user mental model of "pending jobs". Rarely used by music downloader users. | Serve the real-time use case (share → download) first. |
| Custom output templates / yt-dlp passthrough | Power users want arbitrary -f format strings | YTDLnis's "terminal" and "custom command" features are rarely used and add significant testing surface. Hard to support safely. | Expose quality picker (3 audio + 3 video options) — covers 95% of real use cases. |
| Social features (sharing history, recommendations) | App store feature checklist padding | Requires server infrastructure, accounts, privacy policy changes. Zero alignment with "simplest music downloader" positioning. | None — don't build this. |
| Streaming/playback from source (without downloading) | "Stream directly without saving" | Changes the fundamental model. Different caching, buffering, networking concerns. Legal risk profile changes. | Keep it a downloader. Open downloaded files in external player. |
| Download manager with pause/resume | Users see this in browser downloaders | yt-dlp downloads are not resumable in the same way HTTP range requests are. Complex to implement correctly with aria2c integration. | Show progress, allow cancel, allow retry on failure. Don't promise pause/resume. |

---

## Feature Dependencies

```
[App starts without crashing]
    └──required by──> [All other features]

[Foreground Service]
    └──required by──> [Downloads survive backgrounding]
    └──enables──> [Download Queue]
    └──enables──> [Reliable completion notification]

[MediaStore URI in HistoryItem]
    └──required by──> [Open file from history]

[Persistent history (SharedPreferences)]
    └──enhances──> [Download history]
    └──enables──> [Open file from history] (URI must survive app kill too)

[Quality picker (audio)]
    └──depends on──> [yt-dlp -f format string mapping in VideoRepository]

[Download Queue]
    └──depends on──> [Foreground Service] (queue must survive activity kill)
    └──enhances──> [Share intent handling] (share multiple URLs)

[Hilt DI]
    └──enables──> [Unit testing of Repository/ViewModel]
    └──enables──> [Foreground Service with injected Repository]

[Unit tests]
    └──depends on──> [Hilt DI] (mock injection)
    └──validates──> [detectPlatform(), sanitizeFileName(), state transitions]
```

### Dependency Notes

- **Foreground Service requires crash fix first:** The service implementation is wasted if init is broken. Fix the yt-dlp init issue before introducing service architecture.
- **Download Queue requires Foreground Service:** A queue that lives in the ViewModel dies when the activity is killed. The queue state must live in the service.
- **Hilt enables clean Foreground Service:** Without DI, injecting `VideoRepository` into `DownloadService` requires manual singleton management. Add Hilt before the service.
- **Open file from history requires URI persistence:** `DownloadHistoryItem` must carry the MediaStore URI, and that URI must survive app kill if history is persisted. Both persistent history and the URI-in-history-item features must ship together.

---

## Architecture Patterns (Error Handling, Background Processing, State Management)

These are implementation patterns, not user-facing features — but they determine whether the app is reliable.

### Error Handling

**What competitors do:** YTDLnis uses sealed class error types, shows actionable messages ("Re-download" button on failed items), logs errors separately from UI. Seal shows a failed state with an inline retry button.

**What YT2Local must do (table stakes):**
- Catch `YoutubeDLException` and map to human-readable messages (not stack traces)
- Distinguish: network error / unsupported URL / rate limit / format unavailable / storage full
- Keep URL in input field on failure (do not clear) — allow one-tap retry
- Distinguish user-cancel (`CancellationException`) from failure — no error shown for cancel

**Architecture pattern:** Use a sealed class `DownloadResult` (Success, Failure(type, message), Cancelled) returned from `VideoRepository`. ViewModel maps to UI state. No raw exceptions reach the composable layer.

### Background Processing

**Android 14+ requirements (HIGH confidence — official docs):**
- Foreground services targeting API 34+ must declare `android:foregroundServiceType` in manifest
- For downloads: `dataSync` type requires `FOREGROUND_SERVICE_DATA_SYNC` permission
- Android 15 adds a 6-hour shared time limit across all `dataSync` foreground services in an app
- `mediaProcessing` type has a hard 6-hour per 24-hour limit with `onTimeout()` callback

**Recommended approach for YT2Local:**
- Use `dataSync` foreground service type (file transfer over network — exactly what this does)
- `WorkManager` with long-running worker is the modern alternative, but adds complexity
- For single-download-at-a-time model: direct `Service` with `startForeground()` is simpler and more controllable
- For download queue: `WorkManager` with chained `OneTimeWorkRequest` per item is worth considering

**Caveat (LOW confidence):** WorkManager's expedited work enqueuing (since 2.7.0) is an option but has known issues with sequential queue management when items fail — each work item's failure needs explicit handling to not cancel the chain.

### State Management

**Current approach:** Compose `mutableStateOf` directly on ViewModel. This works but is not observable outside Compose (complicates foreground service communication).

**When foreground service is introduced:** The service needs to communicate progress/state back to the ViewModel. Pattern:
- Service exposes a `StateFlow<DownloadState>` (or uses a bound service + callback interface)
- ViewModel collects the flow and maps to Compose state
- This requires migrating from raw `mutableStateOf` on ViewModel to `StateFlow`-based state that survives service binding

**AppState machine integrity:** The `INITIALIZING → UPDATING → READY ⇄ DOWNLOADING → ERROR` machine is correct. The skip-update feature (`P1-2`) must transition from `UPDATING` to `READY` without going through `ERROR`. The state machine should be an explicit `enum` or sealed class with validated transitions.

---

## MVP Definition

### Launch With (v1 — current milestone targets)

This is a refactoring milestone, not a new product. "Launch" means "app works reliably."

- [ ] **Fix yt-dlp init crash** — app is unusable without this
- [ ] **Human-readable error messages** — raw stack traces destroy user trust
- [ ] **Downloads survive app backgrounding** (foreground service) — most common complaint in video downloader apps
- [ ] **Skip yt-dlp update button** — first-launch experience is broken on slow/no networks
- [ ] **Open file from history** — the most-requested follow-up to any download

### Add After Core Works (v1.x)

- [ ] **Persistent history** — trigger: users report losing history on app kill
- [ ] **Quality picker** — trigger: users asking for smaller file sizes or higher quality
- [ ] **Hilt DI** — trigger: before adding foreground service (makes service injectable)
- [ ] **Unit tests** — trigger: before shipping quality picker / service refactor

### Future Consideration (v2+)

- [ ] **Download queue** — depends on foreground service being stable; defer until service is proven
- [ ] **Custom app icon** — visual polish, no functional impact
- [ ] **Haptic feedback** — trivial; do it opportunistically, not as a milestone
- [ ] **Dependency bumps** — maintenance task; do when other changes require it

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Fix yt-dlp init crash | HIGH | MEDIUM | P1 |
| Human-readable error messages | HIGH | LOW | P1 |
| Foreground service for downloads | HIGH | HIGH | P1 |
| Skip yt-dlp update button | HIGH | LOW | P1 |
| Open file from history | HIGH | LOW | P1 |
| Persistent download history | MEDIUM | LOW | P2 |
| Quality picker (audio + video) | MEDIUM | MEDIUM | P2 |
| Hilt DI | LOW (internal) | MEDIUM | P2 |
| Unit tests | LOW (internal) | MEDIUM | P2 |
| Download queue | MEDIUM | HIGH | P2 |
| Custom app icon | LOW | LOW | P3 |
| Haptic feedback | LOW | LOW | P3 |
| Dependency bumps | LOW (internal) | LOW | P3 |

**Priority key:**
- P1: Must have — user-facing reliability and the core value proposition
- P2: Should have — quality and power-user features
- P3: Nice to have — polish

---

## Competitor Feature Analysis

| Feature | YTDLnis | Seal | Our Approach |
|---------|---------|------|--------------|
| Core download (audio/video) | Yes | Yes | Yes — existing |
| Share intent | Yes | Yes | Yes — existing, zero-tap is differentiator |
| Background download (foreground service) | Yes (WorkManager) | Yes | Planned (P1) |
| Download queue | Yes (pause/resume/cancel) | Yes | Planned (P2) |
| Playlist support | Yes (full) | Yes | Out of scope — anti-feature |
| Quality/format picker | Yes (granular) | Yes (templates) | Planned quality picker (P2) |
| Persistent history | Yes (Room DB) | Yes | Planned (P2, SharedPreferences) |
| Open from history | Yes | Yes | Planned (P1) |
| Metadata/thumbnail embed | Yes | Yes | Yes — existing, higher fidelity than most |
| Error recovery / retry | Yes (per-item retry) | Limited | Planned (P1) |
| Skip update / update management | No explicit skip | No | Planned (P1) — unique differentiator |
| Zero-tap share download | No (requires confirmation tap) | No (requires confirmation tap) | Yes — core differentiator |
| Custom yt-dlp commands | Yes (full terminal) | Yes (templates) | Out of scope — anti-feature |
| Scheduling | Yes | No | Out of scope — anti-feature |
| Incognito mode | Yes | No | Out of scope — complexity vs value |

---

## Sources

- [Seal Android app - XDA Forums](https://xdaforums.com/t/app-seal-video-audio-downloader-for-android-based-on-yt-dlp-designed-with-material-you.4712898/) — MEDIUM confidence (forum post, features not exhaustive)
- [Seal GitHub - JunkFood02/Seal](https://github.com/JunkFood02/Seal) — MEDIUM confidence (README, not exhaustive)
- [YTDLnis GitHub - deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis) — MEDIUM confidence (README + changelog analysis)
- [YTDLnis Official Site](https://ytdlnis.com/) — MEDIUM confidence (official feature documentation)
- [Android Foreground Service Types - Official Docs](https://developer.android.com/develop/background-work/services/fgs/service-types) — HIGH confidence
- [Android Foreground Service Types Required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) — HIGH confidence
- [Android App Architecture Patterns 2025 - Medium](https://medium.com/@androidlab/android-app-architecture-patterns-2025-building-scalable-apps-with-hilt-navigation-and-viewmodel-29d2f588d1eb) — LOW confidence (WebSearch only)
- [Foreground Service vs WorkManager - Medium](https://medium.com/@amar90aqi/foreground-service-vs-workmanager-in-android-choosing-the-right-tool-for-background-tasks-32c1242f9898) — LOW confidence (WebSearch only)
- [yt-dlp update check bug affecting downloads - YTDLnis issue #1043](https://github.com/deniscerri/ytdlnis/issues/1043) — MEDIUM confidence (confirmed issue pattern)

---
*Feature research for: Android video/audio downloader (yt-dlp-based, YT2Local refactoring milestone)*
*Researched: 2026-02-27*
