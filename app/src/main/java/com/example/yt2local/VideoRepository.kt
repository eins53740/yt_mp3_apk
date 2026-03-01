package com.example.yt2local

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class DownloadProgress(
    val progress: Float = 0f,
    val etaSeconds: Long = 0,
    val status: String = ""
)

data class DownloadResult(
    val success: Boolean,
    val fileName: String? = null,
    val error: String? = null,
    val videoTitle: String? = null
)

class VideoRepository @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "VideoRepository"
        private const val TEMP_DIR = "video_temp"
        private const val OUTPUT_DIR = "yt2local"

        // Supported platforms (yt-dlp supports 1000+ sites)
        val SUPPORTED_PLATFORMS = listOf(
            "YouTube", "YouTube Music", "YouTube Shorts",
            "Vimeo", "Dailymotion", "Twitch",
            "Twitter/X", "TikTok", "Instagram", "Facebook",
            "Reddit", "SoundCloud", "Bandcamp",
            "Bilibili", "Niconico", "VK",
            "Streamable", "Imgur", "Gfycat",
            "And 1000+ more sites..."
        )
    }

    suspend fun downloadMedia(
        url: String,
        isAudio: Boolean,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download: $url, isAudio: $isAudio")

            // Create and clean temp directory
            val tempDir = File(context.filesDir, TEMP_DIR)
            if (!tempDir.exists()) tempDir.mkdirs()
            cleanTempDir(tempDir)

            // Generate unique temp filename to avoid special character issues
            val tempId = UUID.randomUUID().toString().take(8)
            val tempFileTemplate = "${tempDir.absolutePath}/${tempId}.%(ext)s"

            // Build yt-dlp request with optimal settings for multi-platform support
            val request = YoutubeDLRequest(url).apply {
                // Output template
                addOption("-o", tempFileTemplate)

                // Format selection - flexible for all platforms
                if (isAudio) {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0") // Best quality (VBR ~245kbps)
                } else {
                    // Best video+audio, with fallback options
                    addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best")
                    addOption("--merge-output-format", "mp4")
                }

                // General options for reliability
                addOption("--no-playlist") // Only download single video
                addOption("--no-mtime") // Don't use video date as file mtime
                addOption("--socket-timeout", "30") // 30 second timeout
                addOption("--retries", "3") // Retry 3 times on failure
                addOption("--fragment-retries", "3") // Retry fragments
                addOption("--concurrent-fragments", "4") // Parallel fragment downloads

                // Handle geo-restrictions and age-gates where possible
                addOption("--geo-bypass")

                // Embed metadata and thumbnail for both audio and video
                addOption("--embed-thumbnail")
                addOption("--embed-metadata")

                // Use aria2c for faster downloads if available
                addOption("--downloader", "aria2c")
                addOption("--downloader-args", "aria2c:'-x 16 -s 16 -k 1M'")
            }

            // Track video title
            var videoTitle: String? = null

            // Execute download with progress callback
            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                Log.d(TAG, "Progress: $progress%, ETA: ${etaInSeconds}s, Line: $line")

                // Extract title from output if available
                if (line.contains("[download] Destination:") || line.contains("[ExtractAudio]")) {
                    // Try to parse title from the download line
                    val titleMatch = Regex("\\[download\\] Destination: .*/(.+?)\\.(mp3|mp4|m4a|webm)")
                        .find(line)
                    titleMatch?.groupValues?.getOrNull(1)?.let { videoTitle = it }
                }

                onProgress(
                    DownloadProgress(
                        progress = progress.coerceIn(0f, 100f),
                        etaSeconds = etaInSeconds,
                        status = when {
                            line.contains("[download]") -> "Downloading..."
                            line.contains("[ExtractAudio]") -> "Extracting audio..."
                            line.contains("[Merger]") -> "Merging video & audio..."
                            line.contains("[ffmpeg]") -> "Processing with FFmpeg..."
                            line.contains("[info]") -> "Fetching video info..."
                            else -> "Processing..."
                        }
                    )
                )
            }

            Log.d(TAG, "Download completed. Exit code: ${response.exitCode}")

            // Find the downloaded file
            val downloadedFile = findDownloadedFile(tempDir, tempId)
                ?: return@withContext DownloadResult(
                    success = false,
                    error = "Download completed but file not found. The video might be unavailable or protected."
                )

            Log.d(TAG, "Found downloaded file: ${downloadedFile.name}, size: ${downloadedFile.length()}")

            // Move to MediaStore
            val result = moveToMediaStore(downloadedFile, isAudio, videoTitle)

            // Cleanup temp directory
            cleanTempDir(tempDir)

            result

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            val errorMessage = parseError(e)
            DownloadResult(success = false, error = errorMessage)
        }
    }

    private fun findDownloadedFile(tempDir: File, tempId: String): File? {
        // Look for files matching our temp ID pattern
        val files = tempDir.listFiles() ?: return null

        // First, try to find by tempId prefix
        var file = files.firstOrNull { it.name.startsWith(tempId) }

        // If not found, get the most recently modified file
        if (file == null) {
            file = files.maxByOrNull { it.lastModified() }
        }

        return file?.takeIf { it.length() > 0 }
    }

    private fun moveToMediaStore(file: File, isAudio: Boolean, videoTitle: String?): DownloadResult {
        return try {
            val extension = file.extension.lowercase()
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val datetime = sdf.format(Date())

            // Create filename with title if available, otherwise use timestamp
            val safeTitle = videoTitle?.let { sanitizeFileName(it) } ?: "media"
            val fileName = "${safeTitle}_${datetime}.${extension}"
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$OUTPUT_DIR"

            val mimeType = when {
                isAudio -> when (extension) {
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "opus" -> "audio/opus"
                    "ogg" -> "audio/ogg"
                    else -> "audio/*"
                }
                else -> when (extension) {
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    else -> "video/*"
                }
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return DownloadResult(
                    success = false,
                    error = "Failed to create file in Downloads folder"
                )

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                }
            }

            file.delete()

            DownloadResult(
                success = true,
                fileName = fileName,
                videoTitle = videoTitle
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to move file to MediaStore", e)
            DownloadResult(
                success = false,
                error = "Failed to save file: ${e.message}"
            )
        }
    }

    private fun cleanTempDir(dir: File) {
        dir.listFiles()?.forEach { file ->
            try {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete temp file: ${file.name}")
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[^\\p{L}\\p{N}._\\-\\s]"), "") // Strip emoji and special unicode
            .replace(Regex("\\s+"), "_")
            .trim('_', ' ')
            .take(100) // Cap filename length
    }

    private fun parseError(e: Exception): String {
        val message = e.message ?: return "Unknown error occurred"

        return when {
            message.contains("Unsupported URL", ignoreCase = true) ->
                "This URL is not supported. Please try a different video link."

            message.contains("Video unavailable", ignoreCase = true) ||
            message.contains("Private video", ignoreCase = true) ->
                "This video is unavailable, private, or has been removed."

            message.contains("age", ignoreCase = true) ||
            message.contains("Sign in", ignoreCase = true) ->
                "This video requires age verification or sign-in."

            message.contains("copyright", ignoreCase = true) ||
            message.contains("blocked", ignoreCase = true) ->
                "This video is blocked or restricted in your region."

            message.contains("Live event", ignoreCase = true) ||
            message.contains("Premieres", ignoreCase = true) ->
                "Live streams and premieres cannot be downloaded while live."

            message.contains("HTTP Error 403", ignoreCase = true) ->
                "Access denied. The video might be geo-restricted."

            message.contains("HTTP Error 404", ignoreCase = true) ->
                "Video not found. It may have been deleted."

            message.contains("network", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ->
                "Network error. Please check your internet connection."

            message.contains("No video formats", ignoreCase = true) ||
            message.contains("format", ignoreCase = true) ->
                "No downloadable format found for this video."

            else -> "Download failed: ${message.take(100)}"
        }
    }

    fun detectPlatform(url: String): String {
        val normalizedUrl = url.lowercase()
        return when {
            normalizedUrl.contains("music.youtube.com") -> "YouTube Music"
            normalizedUrl.contains("youtube.com/shorts") -> "YouTube Shorts"
            normalizedUrl.contains("youtube.com") || normalizedUrl.contains("youtu.be") -> "YouTube"
            normalizedUrl.contains("vimeo.com") -> "Vimeo"
            normalizedUrl.contains("dailymotion.com") -> "Dailymotion"
            normalizedUrl.contains("twitch.tv") -> "Twitch"
            normalizedUrl.contains("twitter.com") || normalizedUrl.contains("x.com") -> "Twitter/X"
            normalizedUrl.contains("tiktok.com") -> "TikTok"
            normalizedUrl.contains("instagram.com") -> "Instagram"
            normalizedUrl.contains("facebook.com") || normalizedUrl.contains("fb.watch") -> "Facebook"
            normalizedUrl.contains("reddit.com") || normalizedUrl.contains("redd.it") -> "Reddit"
            normalizedUrl.contains("soundcloud.com") -> "SoundCloud"
            normalizedUrl.contains("bandcamp.com") -> "Bandcamp"
            normalizedUrl.contains("bilibili.com") -> "Bilibili"
            normalizedUrl.contains("nicovideo.jp") -> "Niconico"
            normalizedUrl.contains("vk.com") -> "VK"
            normalizedUrl.contains("streamable.com") -> "Streamable"
            normalizedUrl.contains("imgur.com") -> "Imgur"
            else -> "Unknown Platform"
        }
    }
}
