package com.pocketagent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pocketagent.service.InferenceService
import com.pocketagent.service.ModelManager
import com.pocketagent.service.PipelineResult
import com.pocketagent.ui.DownloadScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AssistantActivity : ComponentActivity() {

    private val recorder = AudioRecorder()
    private var service: InferenceService? = null
    private var bound = false
    private lateinit var modelManager: ModelManager

    private var _state = mutableStateOf<AssistantState>(AssistantState.Idle)
    private var _transcript = mutableStateOf("")
    private var _response = mutableStateOf("")
    private var _modelsReady = mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as InferenceService.LocalBinder).getService()
            bound = true
            // Load models if not already loaded
            if (service?.llm?.isLoaded != true) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val modelsDir = getExternalFilesDir(null)?.let { File(it, "models") }
                        ?: filesDir
                    service?.loadModels(modelsDir, applicationInfo.nativeLibraryDir) { status ->
                        // Could update UI with loading progress
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPipeline()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request all-files access if not granted (needed to read models from shared storage)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !android.os.Environment.isExternalStorageManager()) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }

        modelManager = ModelManager(this)
        _modelsReady.value = modelManager.allModelsReady()

        setContent {
            if (_modelsReady.value) {
                AssistantScreen(
                    state = _state.value,
                    transcript = _transcript.value,
                    response = _response.value,
                    onMicTap = { requestMicAndStart() },
                    onDismiss = { finish() }
                )
            } else {
                DownloadScreen(
                    modelManager = modelManager,
                    onAllReady = {
                        _modelsReady.value = true
                        startServiceAndListen()
                    }
                )
            }
        }

        if (_modelsReady.value) {
            startServiceAndListen()
        }
    }

    private fun startServiceAndListen() {
        val serviceIntent = Intent(this, InferenceService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        requestMicAndStart()
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        super.onDestroy()
    }

    private fun requestMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startPipeline()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startPipeline() {
        lifecycleScope.launch {
            try {
                // Record
                _state.value = AssistantState.Listening
                _transcript.value = ""
                _response.value = ""

                val audioFile = withContext(Dispatchers.IO) {
                    recorder.recordToWav(File(cacheDir, "input.wav"))
                }

                // Check service
                val svc = service
                if (svc == null || !svc.llm.isLoaded) {
                    _state.value = AssistantState.Error("Models not loaded yet")
                    return@launch
                }

                // Transcribe
                _state.value = AssistantState.Transcribing
                val result = withContext(Dispatchers.IO) {
                    svc.voicePipeline(audioFile)
                }

                when (result) {
                    is PipelineResult.Success -> {
                        _transcript.value = result.transcript
                        _response.value = result.response
                        _state.value = AssistantState.Speaking
                        // Wait for TTS to finish
                        withContext(Dispatchers.IO) {
                            while (svc.tts.isSpeaking()) Thread.sleep(100)
                        }
                        _state.value = AssistantState.Done
                    }
                    is PipelineResult.NoSpeech -> {
                        _state.value = AssistantState.Error("Didn't catch that")
                    }
                    is PipelineResult.Error -> {
                        _state.value = AssistantState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _state.value = AssistantState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class AssistantState {
    object Idle : AssistantState()
    object Listening : AssistantState()
    object Transcribing : AssistantState()
    object Thinking : AssistantState()
    object Speaking : AssistantState()
    object Done : AssistantState()
    data class Error(val message: String) : AssistantState()
}

@Composable
fun AssistantScreen(
    state: AssistantState,
    transcript: String,
    response: String,
    onMicTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val statusText = when (state) {
        AssistantState.Idle -> "Tap to speak"
        AssistantState.Listening -> "Listening..."
        AssistantState.Transcribing -> "Processing..."
        AssistantState.Thinking -> "Thinking..."
        AssistantState.Speaking -> "Speaking..."
        AssistantState.Done -> "Done"
        is AssistantState.Error -> state.message
    }

    val micColor = when (state) {
        AssistantState.Listening -> Color(0xFFE53935)
        AssistantState.Idle, AssistantState.Done -> Color(0xFF1E88E5)
        else -> Color(0xFF757575)
    }

    val isAnimating = state == AssistantState.Listening
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.2f else 1f,
        animationSpec = if (isAnimating) infiniteRepeatable(
            tween(600), RepeatMode.Reverse
        ) else tween(300),
        label = "mic_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .clickable { if (state == AssistantState.Done || state is AssistantState.Error) onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("Pocket Agent", color = Color.White, fontSize = 28.sp)
            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(micColor, CircleShape)
                    .clickable {
                        if (state == AssistantState.Idle || state == AssistantState.Done) onMicTap()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83C\uDF99", fontSize = 48.sp)
            }

            Spacer(Modifier.height(24.dp))
            Text(statusText, color = Color(0xAAFFFFFF), fontSize = 18.sp)

            if (transcript.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text("You: $transcript", color = Color(0xAAFFFFFF), fontSize = 16.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (response.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(response, color = Color.White, fontSize = 18.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
