package com.pocketagent

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService

/**
 * Stub RecognitionService required by Android's VoiceInteractionService framework.
 * Actual speech recognition is handled by our whisper.cpp HTTP backend.
 */
class PocketRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback?) {
        // Not used — we handle recording in AssistantActivity directly
    }

    override fun onCancel(callback: Callback?) {}
    override fun onStopListening(callback: Callback?) {}
}
