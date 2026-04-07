package com.pocketagent.stt

object WhisperLib {
    init {
        System.loadLibrary("pocket-stt")
    }

    /** Initialize whisper context from model file. Returns context pointer (0 = failure). */
    external fun initContext(modelPath: String): Long

    /** Transcribe float PCM audio (16kHz mono). Returns transcript text. */
    external fun transcribe(contextPtr: Long, audioData: FloatArray, nThreads: Int): String

    /** Free whisper context. */
    external fun freeContext(contextPtr: Long)
}
