package com.pocketagent

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

class AudioRecorder(private val sampleRate: Int = 16000) {

    /**
     * Record audio with adaptive silence detection using RMS energy.
     *
     * Phase 1 (first 500ms): Calibrate ambient noise floor
     * Phase 2: Detect speech as RMS > noiseFloor * speechMultiplier
     * Phase 3: Stop after silenceDurationMs of sub-threshold audio post-speech
     */
    fun recordToWav(
        outputFile: File,
        maxDurationMs: Long = 12000,
        silenceDurationMs: Long = 1200,
        speechMultiplier: Double = 3.0,
    ): File {
        val bufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufSize
        )

        val pcmData = ByteArrayOutputStream()
        val buffer = ByteArray(bufSize)

        recorder.startRecording()

        val startTime = System.currentTimeMillis()
        var lastSpeechTime = 0L
        var hasSpeechStarted = false
        var noiseFloor = 0.0
        var calibrationSamples = 0
        var calibrationSum = 0.0
        val calibrationMs = 400L

        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = now - startTime

            if (elapsed > maxDurationMs) break

            val read = recorder.read(buffer, 0, buffer.size)
            if (read <= 0) continue

            pcmData.write(buffer, 0, read)

            // Calculate RMS energy of this chunk
            val shortCount = read / 2
            var sumSquares = 0.0
            for (i in 0 until shortCount) {
                val sample = ((buffer[i * 2 + 1].toInt() shl 8) or
                    (buffer[i * 2].toInt() and 0xFF)).toShort().toDouble()
                sumSquares += sample * sample
            }
            val rms = sqrt(sumSquares / shortCount)

            // Phase 1: Calibrate noise floor from ambient sound
            if (elapsed < calibrationMs) {
                calibrationSum += rms
                calibrationSamples++
                noiseFloor = (calibrationSum / calibrationSamples).coerceAtLeast(200.0)
                continue
            }

            val speechThreshold = noiseFloor * speechMultiplier

            if (rms > speechThreshold) {
                lastSpeechTime = now
                if (!hasSpeechStarted) {
                    hasSpeechStarted = true
                }
            }

            // Stop conditions
            if (hasSpeechStarted && lastSpeechTime > 0 &&
                (now - lastSpeechTime) > silenceDurationMs) {
                // Speech happened, then silence — done
                break
            }

            if (!hasSpeechStarted && elapsed > 5000) {
                // Nobody spoke for 5 seconds — give up
                break
            }
        }

        recorder.stop()
        recorder.release()

        writeWav(outputFile, pcmData.toByteArray())
        return outputFile
    }

    private fun writeWav(file: File, pcmData: ByteArray) {
        val dos = DataOutputStream(FileOutputStream(file))
        val dataLen = pcmData.size
        val totalLen = dataLen + 36

        dos.writeBytes("RIFF")
        dos.writeInt(Integer.reverseBytes(totalLen))
        dos.writeBytes("WAVE")
        dos.writeBytes("fmt ")
        dos.writeInt(Integer.reverseBytes(16))
        dos.writeShort(java.lang.Short.reverseBytes(1).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(1).toInt())
        dos.writeInt(Integer.reverseBytes(sampleRate))
        dos.writeInt(Integer.reverseBytes(sampleRate * 2))
        dos.writeShort(java.lang.Short.reverseBytes(2).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(16).toInt())
        dos.writeBytes("data")
        dos.writeInt(Integer.reverseBytes(dataLen))
        dos.write(pcmData)
        dos.close()
    }
}
