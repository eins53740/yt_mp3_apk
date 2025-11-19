package com.example.yt2local

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize YoutubeDL
        try {
            YoutubeDL.getInstance().init(applicationContext)
            // FFmpeg initialization is handled internally or not required explicitly in newer versions
        } catch (e: YoutubeDLException) {
            e.printStackTrace()
        }

        var sharedUrl: String? = null
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        }

        setContent {
            YT2LocalTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(initialUrl = sharedUrl)
                }
            }
        }
    }
}
