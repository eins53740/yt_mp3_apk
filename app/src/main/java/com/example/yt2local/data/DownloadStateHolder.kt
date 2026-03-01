package com.example.yt2local.data

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

sealed class DownloadServiceState {
    data object Idle : DownloadServiceState()
    data class InProgress(val progress: Float, val status: String) : DownloadServiceState()
    data class Success(val fileName: String) : DownloadServiceState()
    data class Failed(val friendlyMessage: String, val originalUrl: String) : DownloadServiceState()
}

class DownloadStateHolder @Inject constructor() {
    val state: MutableStateFlow<DownloadServiceState> =
        MutableStateFlow(DownloadServiceState.Idle)
}
