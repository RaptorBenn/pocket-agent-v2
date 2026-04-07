package com.pocketagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import com.pocketagent.engine.LlmEngine
import com.pocketagent.engine.SttEngine
import com.pocketagent.engine.TtsEngine
import java.io.File

class InferenceService : Service() {

    val llm = LlmEngine()
    val stt = SttEngine()
    lateinit var tts: TtsEngine
        private set

    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): InferenceService = this@InferenceService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Agent")
            .setContentText("Loading models...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        // Wake lock to prevent CPU sleep
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketAgent::Inference")
        wakeLock?.acquire()

        tts = TtsEngine(this)
        tts.init()
    }

    fun loadModels(modelsDir: File, nativeLibDir: String, onProgress: (String) -> Unit) {
        val mm = ModelManager(this)

        onProgress("Initializing LLM backend...")
        llm.init(nativeLibDir)

        val llmModel = mm.getModelFile(Models.LLM)
        onProgress("Looking for LLM at: ${llmModel.absolutePath}")
        if (llmModel.exists()) {
            onProgress("Loading LLM (this takes ~30s)...")
            if (llm.load(llmModel.absolutePath)) {
                llm.setSystemPrompt(
                    "You are Pocket Agent, a voice assistant on a phone. " +
                    "Do not use internal reasoning. Answer directly. " +
                    "Keep responses to 1-3 spoken sentences. " +
                    "No markdown, no bullet points, no special formatting."
                )
                onProgress("LLM ready")
            } else {
                onProgress("LLM failed to load")
            }
        } else {
            onProgress("LLM model not found")
        }

        val sttModel = mm.getModelFile(Models.STT)
        if (sttModel.exists()) {
            onProgress("Loading Whisper STT...")
            stt.load(sttModel.absolutePath)
            onProgress("STT ready")
        } else {
            onProgress("STT model not found")
        }

        updateNotification("Models loaded — ready")
    }

    fun voicePipeline(audioFile: File): PipelineResult {
        if (!stt.isLoaded) return PipelineResult.Error("STT model not loaded")
        if (!llm.isLoaded) return PipelineResult.Error("LLM model not loaded")

        val transcript = stt.transcribe(audioFile)
        if (transcript.isBlank()) return PipelineResult.NoSpeech

        val response = llm.chat(transcript)
        tts.speak(response)

        return PipelineResult.Success(transcript, response)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Pocket Agent", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "AI inference service" }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        wakeLock?.release()
        tts.shutdown()
        stt.unload()
        llm.shutdown()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pocket_agent_inference"
        const val NOTIFICATION_ID = 1
    }
}

sealed class PipelineResult {
    data class Success(val transcript: String, val response: String) : PipelineResult()
    object NoSpeech : PipelineResult()
    data class Error(val message: String) : PipelineResult()
}
