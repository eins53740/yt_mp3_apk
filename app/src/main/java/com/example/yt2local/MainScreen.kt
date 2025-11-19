package com.example.yt2local

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yt2local.R

@Composable
fun MainScreen(
    initialUrl: String? = null,
    viewModel: MainViewModel = viewModel()
) {
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            viewModel.onUrlChange(initialUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Image(painter = painterResource(id = R.mipmap.ic_launcher), contentDescription = "Logo", modifier = Modifier.size(100.dp))
        Text(text = "YT2Local", style = MaterialTheme.typography.headlineLarge)
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = viewModel.url,
            onValueChange = { viewModel.onUrlChange(it) },
            label = { Text("Video URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.isAudio,
                    onClick = { viewModel.onFormatChange(true) }
                )
                Text("Audio (MP3)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = !viewModel.isAudio,
                    onClick = { viewModel.onFormatChange(false) }
                )
                Text("Video (Max)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.startDownload() },
            enabled = !viewModel.isDownloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (viewModel.isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Processing...")
            } else {
                Text("Convert & Download")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = viewModel.statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (viewModel.statusMessage.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (viewModel.downloadHistory.value.isNotEmpty()) {
            Text("Recent Downloads:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            viewModel.downloadHistory.value.forEach { fileName ->
                Text(text = "• $fileName", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
