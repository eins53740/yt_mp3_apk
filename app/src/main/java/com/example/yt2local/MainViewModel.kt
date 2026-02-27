package com.example.yt2local

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppState {
    INITIALIZING,
    UPDATING,
    READY,
    DOWNLOADING,
    ERROR
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)
    private val prefs = application.getSharedPreferences("yt2local_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "MainViewModel"
        private const val PREF_IS_AUDIO = "is_audio"
        private const val NOTIFICATION_ID = 1001
    }

    // Cancellable job for yt-dlp update — held so skipUpdate() can cancel it
    private var updateJob: Job? = null

    // UI State
    var url by mutableStateOf("")
        private set
    var isAudio by mutableStateOf(prefs.getBoolean(PREF_IS_AUDIO, true))
        private set
    var statusMessage by mutableStateOf("Initializing...")
        private set
    var appState by mutableStateOf(AppState.INITIALIZING)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set
    var progressStatus by mutableStateOf("")
        private set
    var detectedPlatform by mutableStateOf("")
        private set
    var downloadHistory = mutableStateOf<List<DownloadHistoryItem>>(emptyList())
        private set
    var ytDlpVersion by mutableStateOf("")
        private set

    // Auto-download flag: set when URL comes from share/view intent
    var autoDownloadPending by mutableStateOf(false)
        private set

    // Snackbar message: set after successful download, consumed by UI
    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    val isReady: Boolean
        get() = appState == AppState.READY

    val isDownloading: Boolean
        get() = appState == AppState.DOWNLOADING

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateStatus("Initializing YoutubeDL...")

                // Initialize YoutubeDL
                YoutubeDL.getInstance().init(getApplication())
                Log.d(TAG, "YoutubeDL initialized")

                // Initialize FFmpeg
                updateStatus("Initializing FFmpeg...")
                FFmpeg.getInstance().init(getApplication())
                Log.d(TAG, "FFmpeg initialized")

                // Initialize aria2c for faster downloads
                updateStatus("Initializing Aria2c...")
                try {
                    Aria2c.getInstance().init(getApplication())
                    Log.d(TAG, "Aria2c initialized")
                } catch (e: Exception) {
                    Log.w(TAG, "Aria2c initialization failed (optional): ${e.message}")
                }

                // Launch yt-dlp update as a cancellable child job
                withContext(Dispatchers.Main) {
                    appState = AppState.UPDATING
                    statusMessage = "Updating yt-dlp... (tap Skip to proceed)"
                }
                updateJob = viewModelScope.launch(Dispatchers.IO) {
                    updateYtDlp()
                }
                updateJob?.join()  // Wait for completion, but job can be cancelled by skipUpdate()

                // Transition to READY only if skipUpdate() hasn't already done it
                withContext(Dispatchers.Main) {
                    if (appState == AppState.UPDATING) {
                        appState = AppState.READY
                        statusMessage = "Ready to download"
                    }
                }

            } catch (e: CancellationException) {
                // Normal — skipUpdate() cancelled the parent or child job
                // State already set to READY by skipUpdate()
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

    private suspend fun updateYtDlp() {
        try {
            val updateResult = YoutubeDL.getInstance().updateYoutubeDL(getApplication())

            withContext(Dispatchers.Main) {
                when (updateResult.status) {
                    YoutubeDL.UpdateStatus.DONE -> {
                        ytDlpVersion = updateResult.version ?: "Updated"
                        Log.d(TAG, "yt-dlp updated to: $ytDlpVersion")
                    }
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                        ytDlpVersion = updateResult.version ?: "Latest"
                        Log.d(TAG, "yt-dlp already up to date: $ytDlpVersion")
                    }
                    else -> {
                        Log.w(TAG, "yt-dlp update status: ${updateResult.status}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update yt-dlp (will use bundled version): ${e.message}")
            withContext(Dispatchers.Main) {
                ytDlpVersion = "Bundled"
            }
        }
    }

    private suspend fun updateStatus(message: String) {
        withContext(Dispatchers.Main) {
            statusMessage = message
        }
    }

    fun onUrlChange(newUrl: String) {
        url = newUrl
        // Detect platform from URL
        if (newUrl.isNotBlank()) {
            detectedPlatform = repository.detectPlatform(newUrl)
        } else {
            detectedPlatform = ""
        }
    }

    fun onFormatChange(audio: Boolean) {
        isAudio = audio
        prefs.edit().putBoolean(PREF_IS_AUDIO, audio).apply()
    }

    /**
     * Called when a URL arrives from a share/view intent.
     * Forces audio mode and sets auto-download flag.
     */
    fun setUrlFromIntent(intentUrl: String, autoStart: Boolean = false) {
        url = intentUrl
        detectedPlatform = repository.detectPlatform(intentUrl)
        if (autoStart) {
            isAudio = true
            prefs.edit().putBoolean(PREF_IS_AUDIO, true).apply()
            autoDownloadPending = true
        }
    }

    /**
     * Called by UI when auto-download conditions are met (READY + flag set).
     */
    fun consumeAutoDownload() {
        if (autoDownloadPending) {
            autoDownloadPending = false
            startDownload()
        }
    }

    fun clearSnackbar() {
        snackbarMessage = null
    }

    fun startDownload() {
        if (url.isBlank()) {
            statusMessage = "Please enter a URL"
            return
        }

        // Extract URL if the input contains extra text
        val extractedUrl = extractUrl(url)
        if (extractedUrl == null) {
            statusMessage = "No valid URL found in input"
            return
        }

        appState = AppState.DOWNLOADING
        downloadProgress = 0f
        progressStatus = "Starting download..."
        statusMessage = "Downloading from ${repository.detectPlatform(extractedUrl)}..."

        viewModelScope.launch {
            val result = repository.downloadMedia(
                url = extractedUrl,
                isAudio = isAudio,
                onProgress = { progress ->
                    downloadProgress = progress.progress
                    progressStatus = progress.status
                    if (progress.etaSeconds > 0) {
                        val minutes = progress.etaSeconds / 60
                        val seconds = progress.etaSeconds % 60
                        statusMessage = "${progress.status} ETA: ${minutes}m ${seconds}s"
                    } else {
                        statusMessage = progress.status
                    }
                }
            )

            withContext(Dispatchers.Main) {
                appState = AppState.READY
                downloadProgress = 0f
                progressStatus = ""

                if (result.success) {
                    statusMessage = "Saved to Downloads/yt2local/${result.fileName}"
                    snackbarMessage = "Saved: ${result.fileName}"

                    // Post notification
                    postDownloadNotification(result.fileName ?: "Download complete")

                    // Add to history
                    val historyItem = DownloadHistoryItem(
                        fileName = result.fileName ?: "Unknown",
                        platform = detectedPlatform,
                        isAudio = isAudio,
                        timestamp = System.currentTimeMillis()
                    )
                    downloadHistory.value = listOf(historyItem) + downloadHistory.value.take(9)

                    // Clear URL after successful download
                    url = ""
                    detectedPlatform = ""
                } else {
                    statusMessage = "Error: ${result.error}"
                }
            }
        }
    }

    private fun postDownloadNotification(fileName: String) {
        val app = getApplication<Application>()

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notification = NotificationCompat.Builder(app, YT2LocalApplication.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Complete")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
    }

    fun retryInitialization() {
        appState = AppState.INITIALIZING
        statusMessage = "Retrying initialization..."
        initialize()
    }

    /**
     * Called from UI when user taps "Skip update" during UPDATING state.
     * Cancels the yt-dlp update download and transitions to READY immediately.
     * The blocking HTTP call may continue briefly in the background, but the
     * UI becomes responsive immediately. Safe to call from main thread (onClick context).
     */
    fun skipUpdate() {
        updateJob?.cancel()
        updateJob = null
        appState = AppState.READY
        statusMessage = "Ready to download (update skipped)"
    }

    fun forceUpdateYtDlp() {
        viewModelScope.launch(Dispatchers.IO) {
            updateYtDlp()
            withContext(Dispatchers.Main) {
                appState = AppState.READY
                statusMessage = "yt-dlp version: $ytDlpVersion"
            }
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
    val timestamp: Long
)
