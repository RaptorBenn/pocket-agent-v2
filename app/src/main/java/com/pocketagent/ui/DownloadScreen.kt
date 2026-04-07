package com.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketagent.service.ModelInfo
import com.pocketagent.service.ModelManager
import com.pocketagent.service.Models
import kotlinx.coroutines.delay

@Composable
fun DownloadScreen(
    modelManager: ModelManager,
    onAllReady: () -> Unit,
) {
    var downloadIds by remember { mutableStateOf(mapOf<String, Long>()) }
    var progress by remember { mutableStateOf(mapOf<String, Float>()) }
    var started by remember { mutableStateOf(false) }

    // Poll download progress
    LaunchedEffect(downloadIds) {
        while (downloadIds.isNotEmpty()) {
            delay(1000)
            val newProgress = mutableMapOf<String, Float>()
            for ((filename, id) in downloadIds) {
                val (downloaded, total) = modelManager.getDownloadProgress(id)
                newProgress[filename] = if (total > 0) downloaded.toFloat() / total else 0f
            }
            progress = newProgress

            if (modelManager.allModelsReady()) {
                onAllReady()
                return@LaunchedEffect
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Pocket Agent",
                color = Color.White,
                fontSize = 28.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "First-time setup",
                color = Color(0xAAFFFFFF),
                fontSize = 16.sp
            )
            Spacer(Modifier.height(48.dp))

            // Show each model
            Models.ALL.forEach { model ->
                ModelRow(
                    model = model,
                    isDownloaded = modelManager.isDownloaded(model),
                    progress = progress[model.filename] ?: 0f,
                    isDownloading = downloadIds.containsKey(model.filename)
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(32.dp))

            if (!started) {
                Button(
                    onClick = {
                        started = true
                        val ids = mutableMapOf<String, Long>()
                        for (model in Models.ALL) {
                            if (!modelManager.isDownloaded(model)) {
                                ids[model.filename] = modelManager.startDownload(model)
                            }
                        }
                        downloadIds = ids
                        // If all already downloaded
                        if (modelManager.allModelsReady()) onAllReady()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text("Download Models", fontSize = 18.sp)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Total: ~6.3 GB\nRequires Wi-Fi recommended",
                    color = Color(0x88FFFFFF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            } else if (!modelManager.allModelsReady()) {
                CircularProgressIndicator(color = Color(0xFF1E88E5))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Downloading... check notification for progress",
                    color = Color(0xAAFFFFFF),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isDownloaded: Boolean,
    progress: Float,
    isDownloading: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                model.name,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                when {
                    isDownloaded -> "Ready"
                    isDownloading -> "${(progress * 100).toInt()}%"
                    else -> "${model.sizeBytes / 1_000_000}MB"
                },
                color = if (isDownloaded) Color(0xFF4CAF50) else Color(0xAAFFFFFF),
                fontSize = 14.sp
            )
        }
        if (isDownloading && !isDownloaded) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E88E5),
                trackColor = Color(0x33FFFFFF),
            )
        }
    }
}
