package com.example.yt2local

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yt2local.data.DownloadServiceState
import com.example.yt2local.data.DownloadStateHolder
import com.example.yt2local.data.db.DownloadHistoryDao
import com.example.yt2local.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppState {
    INITIALIZING,
    UPDATING,
    READY,
    DOWNLOADING,
    ERROR
}

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
class MainViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val downloadStateHolder: DownloadStateHolder,
    private val historyDao: DownloadHistoryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val prefs = context.getSharedPreferences("yt2local_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "MainViewModel"
        private const val PREF_IS_AUDIO = "is_audio"
    }

    // Cancellable job for yt-dlp update — held so skipUpdate() can cancel it
    private var updateJob: Job? = null

    private val _uiState = MutableStateFlow(
        DownloaderUiState(isAudio = prefs.getBoolean(PREF_IS_AUDIO, true))
    )
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    init {
        initialize()
        observeServiceState()
        observeHistory()
    }

    private fun observeServiceState() {
        downloadStateHolder.state.onEach { serviceState ->
            when (serviceState) {
                is DownloadServiceState.InProgress -> {
                    _uiState.update { it.copy(
                        appState = AppState.DOWNLOADING,
                        downloadProgress = serviceState.progress,
                        progressStatus = serviceState.status,
                        statusMessage = serviceState.status
                    )}
                }
                is DownloadServiceState.Success -> {
                    _uiState.update { it.copy(
                        appState = AppState.READY,
                        downloadProgress = 0f,
                        progressStatus = "",
                        statusMessage = "Saved: ${serviceState.fileName}",
                        snackbarMessage = "Saved: ${serviceState.fileName}",
                        url = "",
                        detectedPlatform = ""
                    )}
                    downloadStateHolder.state.value = DownloadServiceState.Idle
                }
                is DownloadServiceState.Failed -> {
                    _uiState.update { it.copy(
                        appState = AppState.READY,
                        downloadProgress = 0f,
                        progressStatus = "",
                        statusMessage = serviceState.friendlyMessage
                        // url and detectedPlatform NOT cleared — preserved on failure (ARCH-04)
                    )}
                    downloadStateHolder.state.value = DownloadServiceState.Idle
                }
                is DownloadServiceState.Idle -> { /* no-op */ }
            }
        }.launchIn(viewModelScope)
    }

    private fun observeHistory() {
        historyDao.getRecent()
            .map { entities ->
                entities.map { entity ->
                    DownloadHistoryItem(
                        fileName = entity.fileName,
                        platform = entity.platform,
                        isAudio = entity.isAudio,
                        timestamp = entity.timestamp,
                        mediaUri = entity.mediaUri
                    )
                }
            }
            .onEach { items ->
                _uiState.update { it.copy(downloadHistory = items) }
            }
            .launchIn(viewModelScope)
    }

    private fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateStatus("Initializing YoutubeDL...")

                // Initialize YoutubeDL
                YoutubeDL.getInstance().init(context)
                Log.d(TAG, "YoutubeDL initialized")

                // Initialize FFmpeg
                updateStatus("Initializing FFmpeg...")
                FFmpeg.getInstance().init(context)
                Log.d(TAG, "FFmpeg initialized")

                // Initialize aria2c for faster downloads
                updateStatus("Initializing Aria2c...")
                try {
                    Aria2c.getInstance().init(context)
                    Log.d(TAG, "Aria2c initialized")
                } catch (e: Exception) {
                    Log.w(TAG, "Aria2c initialization failed (optional): ${e.message}")
                }

                // Launch yt-dlp update as a cancellable child job
                _uiState.update { it.copy(appState = AppState.UPDATING, statusMessage = "Updating yt-dlp... (tap Skip to proceed)") }
                updateJob = viewModelScope.launch(Dispatchers.IO) {
                    updateYtDlp()
                }
                updateJob?.join()  // Wait for completion, but job can be cancelled by skipUpdate()

                // Transition to READY only if skipUpdate() hasn't already done it
                _uiState.update { state ->
                    if (state.appState == AppState.UPDATING) {
                        state.copy(appState = AppState.READY, statusMessage = "Ready to download")
                    } else {
                        state
                    }
                }

            } catch (e: CancellationException) {
                // Normal — skipUpdate() cancelled the parent or child job
                // State already set to READY by skipUpdate()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _uiState.update { it.copy(appState = AppState.ERROR, statusMessage = "Initialization failed: ${e.message}") }
            }
        }
    }

    private suspend fun updateYtDlp() {
        try {
            val updateResult = YoutubeDL.getInstance().updateYoutubeDL(context)

            when (updateResult.status) {
                YoutubeDL.UpdateStatus.DONE -> {
                    val version = updateResult.version ?: "Updated"
                    _uiState.update { it.copy(ytDlpVersion = version) }
                    Log.d(TAG, "yt-dlp updated to: $version")
                }
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                    val version = updateResult.version ?: "Latest"
                    _uiState.update { it.copy(ytDlpVersion = version) }
                    Log.d(TAG, "yt-dlp already up to date: $version")
                }
                else -> {
                    Log.w(TAG, "yt-dlp update status: ${updateResult.status}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update yt-dlp (will use bundled version): ${e.message}")
            _uiState.update { it.copy(ytDlpVersion = "Bundled") }
        }
    }

    private fun updateStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun onUrlChange(newUrl: String) {
        _uiState.update { it.copy(
            url = newUrl,
            detectedPlatform = if (newUrl.isNotBlank()) repository.detectPlatform(newUrl) else ""
        )}
    }

    fun onFormatChange(audio: Boolean) {
        _uiState.update { it.copy(isAudio = audio) }
        prefs.edit().putBoolean(PREF_IS_AUDIO, audio).apply()
    }

    /**
     * Called when a URL arrives from a share/view intent.
     * Forces audio mode and sets auto-download flag.
     */
    fun setUrlFromIntent(intentUrl: String, autoStart: Boolean = false) {
        _uiState.update { state ->
            state.copy(
                url = intentUrl,
                detectedPlatform = repository.detectPlatform(intentUrl),
                isAudio = if (autoStart) true else state.isAudio,
                autoDownloadPending = autoStart
            )
        }
        if (autoStart) {
            prefs.edit().putBoolean(PREF_IS_AUDIO, true).apply()
        }
    }

    /**
     * Called by UI when auto-download conditions are met (READY + flag set).
     */
    fun consumeAutoDownload() {
        if (_uiState.value.autoDownloadPending) {
            _uiState.update { it.copy(autoDownloadPending = false) }
            startDownload()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun startDownload() {
        val currentState = _uiState.value
        if (currentState.url.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please enter a URL") }
            return
        }

        // Extract URL if the input contains extra text
        val extractedUrl = extractUrl(currentState.url)
        if (extractedUrl == null) {
            _uiState.update { it.copy(statusMessage = "No valid URL found in input") }
            return
        }

        _uiState.update { it.copy(
            appState = AppState.DOWNLOADING,
            downloadProgress = 0f,
            progressStatus = "Starting download...",
            statusMessage = "Downloading from ${repository.detectPlatform(extractedUrl)}..."
        )}

        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL, extractedUrl)
            putExtra(DownloadService.EXTRA_IS_AUDIO, currentState.isAudio)
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download service", e)
            _uiState.update { it.copy(
                appState = AppState.READY,
                statusMessage = "Failed to start download. Please try again."
            )}
        }
    }

    fun retryInitialization() {
        _uiState.update { it.copy(appState = AppState.INITIALIZING, statusMessage = "Retrying initialization...") }
        initialize()
    }

    /**
     * Called from UI when user taps "Skip update" during UPDATING state.
     * Cancels the yt-dlp update download and transitions to READY immediately.
     */
    fun skipUpdate() {
        updateJob?.cancel()
        updateJob = null
        _uiState.update { it.copy(appState = AppState.READY, statusMessage = "Ready to download (update skipped)") }
    }

    fun forceUpdateYtDlp() {
        viewModelScope.launch(Dispatchers.IO) {
            updateYtDlp()
            _uiState.update { it.copy(appState = AppState.READY, statusMessage = "yt-dlp version: ${_uiState.value.ytDlpVersion}") }
        }
    }

    private fun extractUrl(input: String): String? {
        // Common URL patterns
        val urlPattern = Regex(
            """https?://[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b[-a-zA-Z0-9()@:%_+.~#?&/=]*"""
        )

        // Find all URLs in the input
        val matches = urlPattern.findAll(input)
        val urls = matches.map { it.value }.toList()

        if (urls.isEmpty()) {
            // Maybe it's just a URL without protocol
            val withProtocol = "https://$input"
            return if (urlPattern.matches(withProtocol)) withProtocol else null
        }

        // Prefer video platform URLs over others
        val videoPlatformUrl = urls.find { url ->
            val lower = url.lowercase()
            lower.contains("youtube") || lower.contains("youtu.be") ||
            lower.contains("vimeo") || lower.contains("tiktok") ||
            lower.contains("twitter") || lower.contains("x.com") ||
            lower.contains("instagram") || lower.contains("facebook") ||
            lower.contains("reddit") || lower.contains("twitch") ||
            lower.contains("dailymotion") || lower.contains("soundcloud") ||
            lower.contains("bilibili") || lower.contains("bandcamp")
        }

        return videoPlatformUrl ?: urls.firstOrNull()
    }
}

data class DownloadHistoryItem(
    val fileName: String,
    val platform: String,
    val isAudio: Boolean,
    val timestamp: Long,
    val mediaUri: String? = null
)
