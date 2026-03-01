package com.example.yt2local

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
<<<<<<< Updated upstream
import kotlinx.coroutines.Dispatchers
=======
import kotlinx.coroutines.flow.StateFlow
>>>>>>> Stashed changes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yausername.youtubedl_android.YoutubeDL

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YoutubeRepository(application)

    var url by mutableStateOf("")
    var isAudio by mutableStateOf(true) // Default to Audio
    var statusMessage by mutableStateOf("Initializing...")
    var isDownloading by mutableStateOf(false)
    var downloadHistory = mutableStateOf<List<String>>(emptyList())
<<<<<<< Updated upstream
    var isInitialized by mutableStateOf(false)

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(getApplication())
                withContext(Dispatchers.Main) {
                    isInitialized = true
                    statusMessage = "Ready"
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val stackTrace = android.util.Log.getStackTraceString(e)
                withContext(Dispatchers.Main) {
                    isInitialized = false
                    statusMessage = "Init Error: ${e.message}\n$stackTrace"
=======
    
    // Track YoutubeDL initialization status
    val isYoutubeDLInitialized: StateFlow<Boolean> = YT2LocalApplication.isYoutubeDLInitialized
    val initializationError: StateFlow<String?> = YT2LocalApplication.initializationError
    
    init {
        // Monitor initialization status
        viewModelScope.launch {
            YT2LocalApplication.isYoutubeDLInitialized.collect { initialized ->
                if (initialized) {
                    statusMessage = "Ready"
                }
            }
        }
        
        viewModelScope.launch {
            YT2LocalApplication.initializationError.collect { error ->
                if (error != null) {
                    statusMessage = "Error: $error"
>>>>>>> Stashed changes
                }
            }
        }
    }

    fun onUrlChange(newUrl: String) {
        url = newUrl
    }

    fun onFormatChange(audio: Boolean) {
        isAudio = audio
    }

    fun startDownload() {
        if (!isYoutubeDLInitialized.value) {
            statusMessage = "Please wait, initializing..."
            return
        }
        
        if (url.isBlank()) {
            statusMessage = "Please enter a URL"
            return
        }

        isDownloading = true
        statusMessage = "Downloading..."

        viewModelScope.launch {
            val result = repository.downloadVideo(url, isAudio)
            isDownloading = false
            if (result.isSuccess) {
                val fileName = result.getOrNull() ?: "Unknown"
                statusMessage = "Saved to Downloads: $fileName"
                downloadHistory.value = listOf(fileName) + downloadHistory.value
            } else {
                statusMessage = "Error: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}
