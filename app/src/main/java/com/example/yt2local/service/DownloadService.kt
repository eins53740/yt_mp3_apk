package com.example.yt2local.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.yt2local.R
import com.example.yt2local.VideoRepository
import com.example.yt2local.YT2LocalApplication
import com.example.yt2local.data.DownloadServiceState
import com.example.yt2local.data.DownloadStateHolder
import com.example.yt2local.data.db.DownloadHistoryDao
import com.example.yt2local.data.db.DownloadHistoryEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var repository: VideoRepository
    @Inject lateinit var downloadStateHolder: DownloadStateHolder
    @Inject lateinit var historyDao: DownloadHistoryDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "DownloadService"
        const val EXTRA_URL = "url"
        const val EXTRA_IS_AUDIO = "isAudio"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground IMMEDIATELY — Android requires this within seconds of startForegroundService()
        val notification = buildNotification("Starting download...", 0)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val url = intent?.getStringExtra(EXTRA_URL)
        val isAudio = intent?.getBooleanExtra(EXTRA_IS_AUDIO, true) ?: true

        if (url.isNullOrBlank()) {
            Log.w(TAG, "No URL provided, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Starting download: url=$url, isAudio=$isAudio")

        serviceScope.launch {
            try {
                downloadStateHolder.state.value = DownloadServiceState.InProgress(0f, "Starting download...")

                val result = repository.downloadMedia(
                    url = url,
                    isAudio = isAudio,
                    onProgress = { progress ->
                        downloadStateHolder.state.value = DownloadServiceState.InProgress(
                            progress.progress,
                            progress.status
                        )
                        updateNotification(progress.status, progress.progress.toInt())
                    }
                )

                if (result.success) {
                    // Insert into Room history
                    val platform = repository.detectPlatform(url)
                    historyDao.insert(
                        DownloadHistoryEntity(
                            fileName = result.fileName ?: "Unknown",
                            platform = platform,
                            isAudio = isAudio,
                            timestamp = System.currentTimeMillis(),
                            mediaUri = result.mediaUri
                        )
                    )
                    historyDao.pruneOld()

                    downloadStateHolder.state.value = DownloadServiceState.Success(
                        result.fileName ?: "Download complete"
                    )

                    // Show completion notification
                    showCompletionNotification(result.fileName ?: "Download complete")
                } else {
                    downloadStateHolder.state.value = DownloadServiceState.Failed(
                        friendlyMessage = result.error ?: "Download failed",
                        originalUrl = url
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed in service", e)
                downloadStateHolder.state.value = DownloadServiceState.Failed(
                    friendlyMessage = "Download failed: ${e.message?.take(100) ?: "Unknown error"}",
                    originalUrl = url
                )
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(status: String, progress: Int): android.app.Notification {
        val builder = NotificationCompat.Builder(this, YT2LocalApplication.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("YT2Local")
            .setContentText(status)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)

        if (progress > 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(100, 0, true) // indeterminate
        }

        return builder.build()
    }

    private fun updateNotification(status: String, progress: Int) {
        try {
            val notification = buildNotification(status, progress)
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted — non-fatal
            Log.w(TAG, "Cannot update notification: ${e.message}")
        }
    }

    private fun showCompletionNotification(fileName: String) {
        try {
            val notification = NotificationCompat.Builder(this, YT2LocalApplication.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Download Complete")
                .setContentText(fileName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            // Use a different notification ID so it doesn't replace the ongoing one
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID + 1, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot show completion notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
