package com.pocketagent.engine

import com.pocketagent.stt.WhisperLib
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SttEngine {
    private var contextPtr: Long = 0
    var isLoaded = false
        private set

    fun load(modelPath: String): Boolean {
        contextPtr = WhisperLib.initContext(modelPath)
        isLoaded = contextPtr != 0L
        return isLoaded
    }

    fun transcribe(wavFile: File, nThreads: Int = 4): String {
        if (!isLoaded) return ""
        val audioData = readWavAsFloat(wavFile)
        return WhisperLib.transcribe(contextPtr, audioData, nThreads).trim()
    }

    fun unload() {
        if (isLoaded) {
            WhisperLib.freeContext(contextPtr)
            contextPtr = 0
            isLoaded = false
        }
    }

    /** Read WAV file and return 16kHz mono float samples */
    private fun readWavAsFloat(file: File): FloatArray {
        val raf = RandomAccessFile(file, "r")
        // Skip WAV header (44 bytes)
        raf.seek(44)
        val dataSize = (raf.length() - 44).toInt()
        val bytes = ByteArray(dataSize)
        raf.readFully(bytes)
        raf.close()

        // Convert 16-bit PCM to float
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val samples = FloatArray(dataSize / 2)
        for (i in samples.indices) {
            samples[i] = buf.getShort().toFloat() / 32768.0f
        }
        return samples
    }
}
