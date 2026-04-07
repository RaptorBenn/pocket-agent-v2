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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DownloadScreen(
    modelManager: ModelManager,
    onAllReady: () -> Unit,
) {
    var progress by remember { mutableStateOf(mapOf<String, Float>()) }
    var status by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Check if already ready
    LaunchedEffect(Unit) {
        if (modelManager.allModelsReady()) onAllReady()
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
            Text("Pocket Agent", color = Color.White, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text("First-time setup", color = Color(0xAAFFFFFF), fontSize = 16.sp)
            Spacer(Modifier.height(48.dp))

            Models.ALL.forEach { model ->
                ModelRow(
                    model = model,
                    isDownloaded = modelManager.isDownloaded(model),
                    progress = progress[model.filename] ?: 0f,
                    isDownloading = downloading && !modelManager.isDownloaded(model)
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(16.dp))

            if (status.isNotBlank()) {
                Text(status, color = Color(0xAAFFFFFF), fontSize = 14.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }

            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFE53935), fontSize = 14.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }

            if (!downloading) {
                Button(
                    onClick = {
                        downloading = true
                        error = ""
                        scope.launch {
                            for (model in Models.ALL) {
                                if (modelManager.isDownloaded(model)) continue
                                status = "Downloading ${model.name}..."
                                val success = withContext(Dispatchers.IO) {
                                    modelManager.downloadModel(model) { downloaded, total ->
                                        if (total > 0) {
                                            progress = progress + (model.filename to (downloaded.toFloat() / total))
                                        }
                                    }
                                }
                                if (!success) {
                                    error = "Failed to download ${model.name}. Make sure Termux file server is running:\ncd ~/projects/models && python3 -m http.server 9090"
                                    downloading = false
                                    return@launch
                                }
                                progress = progress + (model.filename to 1f)
                            }
                            status = "All models downloaded!"
                            downloading = false
                            if (modelManager.allModelsReady()) onAllReady()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text("Download Models", fontSize = 18.sp)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Total: ~6.3 GB\nModels download from Termux localhost",
                    color = Color(0x88FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center
                )
            } else {
                CircularProgressIndicator(color = Color(0xFF1E88E5))
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
            Text(model.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
