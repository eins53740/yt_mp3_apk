package com.example.yt2local

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class YT2LocalApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val TAG = "YT2LocalApp"
        private val _isYoutubeDLInitialized = MutableStateFlow(false)
        val isYoutubeDLInitialized: StateFlow<Boolean> = _isYoutubeDLInitialized
        
        private val _initializationError = MutableStateFlow<String?>(null)
        val initializationError: StateFlow<String?> = _initializationError
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate() called")
        initializeYoutubeDL()
    }
    
    private fun initializeYoutubeDL() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting YoutubeDL initialization...")
                
                // Initialize YoutubeDL - this can take several seconds on first launch
                YoutubeDL.getInstance().init(applicationContext)
                Log.d(TAG, "YoutubeDL initialized successfully!")
                
                // Initialize FFmpeg - required for audio extraction and video processing
                Log.d(TAG, "Starting FFmpeg initialization...")
                FFmpeg.getInstance().init(applicationContext)
                Log.d(TAG, "FFmpeg initialized successfully!")
                
                // Update YoutubeDL to latest version (optional but recommended)
                // YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                
                _isYoutubeDLInitialized.value = true
                _initializationError.value = null
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "YoutubeDLException during initialization", e)
                e.printStackTrace()
                _isYoutubeDLInitialized.value = false
                _initializationError.value = e.message ?: "Failed to initialize YoutubeDL"
            } catch (e: Exception) {
                Log.e(TAG, "Exception during initialization", e)
                e.printStackTrace()
                _isYoutubeDLInitialized.value = false
                _initializationError.value = e.message ?: "Unknown initialization error"
            }
        }
    }
}
