package com.example.yt2local

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.yausername.youtubedl_android.YoutubeDL

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YoutubeRepository(application)

    var url by mutableStateOf("")
    var isAudio by mutableStateOf(true) // Default to Audio
    var statusMessage by mutableStateOf("Initializing...")
    var isDownloading by mutableStateOf(false)
    var downloadHistory = mutableStateOf<List<String>>(emptyList())
    var isInitialized by mutableStateOf(false)

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(getApplication())
                isInitialized = true
                statusMessage = "Ready"
            } catch (e: Exception) {
                isInitialized = false
                statusMessage = "Error: failed to initialize"
                e.printStackTrace()
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
