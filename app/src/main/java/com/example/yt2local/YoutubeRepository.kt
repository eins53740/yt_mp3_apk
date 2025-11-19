package com.example.yt2local

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class YoutubeRepository(private val context: Context) {

    suspend fun downloadVideo(url: String, isAudio: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Download to internal storage first
            val tempDir = File(context.filesDir, "yt_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            
            // Clean up old temp files
            tempDir.listFiles()?.forEach { it.delete() }

            val format = if (isAudio) "bestaudio/best" else "bestvideo+bestaudio/best"
            val request = YoutubeDLRequest(url)
            val tempFileTemplate = "${tempDir.absolutePath}/%(title)s.%(ext)s"
            request.addOption("-o", tempFileTemplate)
            request.addOption("-f", format)
            
            if (isAudio) {
                request.addOption("-x") // Extract audio
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "192K")
            }

            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                println("Progress: $progress, ETA: $etaInSeconds")
            }

            // 2. Find the downloaded file
            val downloadedFile = tempDir.listFiles()?.firstOrNull() 
                ?: return@withContext Result.failure(Exception("No file downloaded"))

            // 3. Move to MediaStore (Downloads/yt2local)
            val fileName = "yt_${System.currentTimeMillis()}_${downloadedFile.name}"
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/yt2local"
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mpeg" else "video/mp4") // Simplified mime type
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(downloadedFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // 4. Cleanup
            downloadedFile.delete()

            Result.success(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
