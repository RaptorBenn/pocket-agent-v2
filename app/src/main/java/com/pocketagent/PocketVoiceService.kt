package com.pocketagent

import android.content.Intent
import android.service.voice.VoiceInteractionService
import com.pocketagent.service.InferenceService

class PocketVoiceService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Start inference service as foreground to keep models loaded
        val intent = Intent(this, InferenceService::class.java)
        startForegroundService(intent)
    }
}
