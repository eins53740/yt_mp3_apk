package com.example.yt2local

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YoutubeRepository(application)

    var url by mutableStateOf("")
    var isAudio by mutableStateOf(true)
    var statusMessage by mutableStateOf("Initializing...")
    var isDownloading by mutableStateOf(false)
    var downloadHistory = mutableStateOf<List<String>>(emptyList())

    val isInitializing: StateFlow<Boolean> = YT2LocalApplication.isInitializing
    val isYoutubeDLInitialized: StateFlow<Boolean> = YT2LocalApplication.isYoutubeDLInitialized
    val initializationError: StateFlow<String?> = YT2LocalApplication.initializationError

    init {
        viewModelScope.launch {
            isInitializing.collect { initializing ->
                if (initializing) {
                    statusMessage = "Initializing..."
                }
            }
        }

        viewModelScope.launch {
            isYoutubeDLInitialized.collect { initialized ->
                if (initialized) {
                    statusMessage = "Ready"
                }
            }
        }

        viewModelScope.launch {
            initializationError.collect { error ->
                if (error != null) {
                    statusMessage = "Error: $error"
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

    fun retryInitialization() {
        if (isInitializing.value || isYoutubeDLInitialized.value) {
            return
        }
        statusMessage = "Retrying initialization..."
        getApplication<YT2LocalApplication>().retryInitialization()
    }

    fun startDownload() {
        if (!isYoutubeDLInitialized.value) {
            statusMessage = if (initializationError.value != null) {
                "Initialization failed. Tap Retry Initialization."
            } else {
                "Please wait, initializing..."
            }
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
