package com.example.yt2local

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-download: when flag is set and app is READY, trigger download
    LaunchedEffect(uiState.autoDownloadPending, uiState.appState) {
        if (uiState.autoDownloadPending && uiState.appState == AppState.READY) {
            viewModel.consumeAutoDownload()
        }
    }

    // Snackbar on download completion
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    // Clipboard auto-paste on resume (only when URL field is empty)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.url.isBlank()) {
                val clipText = clipboardManager.getText()?.text
                if (!clipText.isNullOrBlank() && looksLikeVideoUrl(clipText)) {
                    viewModel.onUrlChange(clipText.trim())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Logo and Title
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "Logo",
                    modifier = Modifier.size(80.dp)
                )

                Text(
                    text = "YT2Local",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Download from YouTube & 1000+ sites",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // yt-dlp version + update button
                if (uiState.ytDlpVersion.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "yt-dlp: ${uiState.ytDlpVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        TextButton(
                            onClick = { viewModel.forceUpdateYtDlp() },
                            enabled = uiState.appState == AppState.READY
                        ) {
                            Text(
                                text = "Update",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))

                // URL Input with paste/clear trailing icon
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = { viewModel.onUrlChange(it) },
                    label = { Text("Paste video URL") },
                    placeholder = { Text("https://youtube.com/watch?v=...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = uiState.appState == AppState.READY,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (uiState.url.isNotBlank()) {
                            IconButton(onClick = { viewModel.onUrlChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    viewModel.onUrlChange(clipText.trim())
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste"
                                )
                            }
                        }
                    }
                )

                // Platform Detection Badge
                AnimatedVisibility(
                    visible = uiState.detectedPlatform.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = uiState.detectedPlatform,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                // Format Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (uiState.isAudio) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.isAudio,
                                onClick = { viewModel.onFormatChange(true) },
                                enabled = uiState.appState == AppState.READY,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Column {
                                Text(
                                    text = "Audio",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "MP3 (Best)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!uiState.isAudio) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !uiState.isAudio,
                                onClick = { viewModel.onFormatChange(false) },
                                enabled = uiState.appState == AppState.READY,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Column {
                                Text(
                                    text = "Video",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "MP4 (Max)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Download Progress
                AnimatedVisibility(
                    visible = uiState.appState == AppState.DOWNLOADING,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.downloadProgress.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState.progressStatus.isNotBlank()) {
                            Text(
                                text = uiState.progressStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                // Download Button
                Button(
                    onClick = { viewModel.startDownload() },
                    enabled = uiState.appState == AppState.READY && uiState.url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    when (uiState.appState) {
                        AppState.DOWNLOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Downloading...")
                        }
                        AppState.INITIALIZING, AppState.UPDATING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (uiState.appState == AppState.UPDATING) "Updating..." else "Initializing...")
                        }
                        AppState.ERROR -> {
                            Text("Retry")
                        }
                        AppState.READY -> {
                            Text(
                                text = "Download",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Error retry button
                if (uiState.appState == AppState.ERROR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.retryInitialization() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Initialization")
                    }
                }

                // Skip update button — visible only during yt-dlp update phase
                if (uiState.appState == AppState.UPDATING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.skipUpdate() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip update")
                    }
                }
            }

            item {
                // Status Message
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        uiState.statusMessage.startsWith("Error") ||
                        uiState.statusMessage.contains("failed", ignoreCase = true) ||
                        uiState.statusMessage.contains("not supported", ignoreCase = true) ||
                        uiState.statusMessage.contains("unavailable", ignoreCase = true) ||
                        uiState.statusMessage.contains("denied", ignoreCase = true) ||
                        uiState.statusMessage.contains("not found", ignoreCase = true) ->
                            MaterialTheme.colorScheme.error
                        uiState.statusMessage.startsWith("Saved") -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Retry button for download failures — URL is preserved (ARCH-04)
                if (uiState.appState == AppState.READY &&
                    uiState.url.isNotBlank() &&
                    (uiState.statusMessage.contains("failed", ignoreCase = true) ||
                     uiState.statusMessage.contains("not supported", ignoreCase = true) ||
                     uiState.statusMessage.contains("unavailable", ignoreCase = true) ||
                     uiState.statusMessage.contains("denied", ignoreCase = true) ||
                     uiState.statusMessage.contains("not found", ignoreCase = true) ||
                     uiState.statusMessage.startsWith("Error"))) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.startDownload() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Download")
                    }
                }
            }

            // Download History
            if (uiState.downloadHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Recent Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(uiState.downloadHistory) { item ->
                    HistoryItem(item = item)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Supported platforms info
                Text(
                    text = "Supports: YouTube, TikTok, Twitter/X, Instagram, Vimeo, Facebook, Reddit, SoundCloud, Twitch, and 1000+ more sites",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Quick check if clipboard text looks like a video URL.
 */
private fun looksLikeVideoUrl(text: String): Boolean {
    val lower = text.lowercase().trim()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    val videoHosts = listOf(
        "youtube.com", "youtu.be", "music.youtube.com",
        "vimeo.com", "tiktok.com", "twitter.com", "x.com",
        "instagram.com", "facebook.com", "fb.watch",
        "reddit.com", "redd.it", "soundcloud.com",
        "twitch.tv", "dailymotion.com", "bandcamp.com",
        "bilibili.com", "nicovideo.jp"
    )
    return videoHosts.any { lower.contains(it) }
}

@Composable
private fun HistoryItem(item: DownloadHistoryItem) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                item.mediaUri?.let { uriString ->
                    try {
                        val uri = Uri.parse(uriString)
                        val mime = if (item.isAudio) "audio/*" else "video/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open with"))
                    } catch (e: Exception) {
                        // File may have been deleted or URI invalid — non-fatal
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (item.isAudio) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.isAudio) "MP3" else "MP4",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isAudio) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    if (item.platform.isNotBlank()) {
                        Text(
                            text = item.platform,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = formatTimestamp(item.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
