package com.pocketagent.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TTS Engine using Android's built-in TTS as initial implementation.
 * TODO: Replace with sherpa-onnx Piper integration for higher quality.
 *
 * For now, uses a simple approach: call Android TTS API.
 * The sherpa-onnx AAR integration requires additional setup and will be
 * added once the core pipeline is verified working.
 */
class TtsEngine(private val context: android.content.Context) {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var isReady = false

    fun init() {
        tts = android.speech.tts.TextToSpeech(context) { status ->
            isReady = (status == android.speech.tts.TextToSpeech.SUCCESS)
        }
    }

    fun speak(text: String) {
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "pocket-agent-tts")
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
