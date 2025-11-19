package com.example.yt2local

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YoutubeRepository(application)

    var url by mutableStateOf("")
    var isAudio by mutableStateOf(true) // Default to Audio
    var statusMessage by mutableStateOf("Ready")
    var isDownloading by mutableStateOf(false)
    var downloadHistory = mutableStateOf<List<String>>(emptyList())

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
