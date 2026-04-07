package com.pocketagent.service

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "PocketAgent"

data class ModelInfo(
    val name: String,
    val filename: String,
    val url: String,
    val sizeBytes: Long,
)

object Models {
    // Local Termux file server: cd ~/projects/models && python3 -m http.server 9090
    private const val LOCAL = "http://127.0.0.1:9090"

    val LLM = ModelInfo(
        name = "Gemma 4 E4B Q5_K_M",
        filename = "google_gemma-4-E4B-it-Q5_K_M.gguf",
        url = "$LOCAL/google_gemma-4-E4B-it-Q5_K_M.gguf",
        sizeBytes = 5_820_000_000L,
    )
    val STT = ModelInfo(
        name = "Whisper Small",
        filename = "ggml-small.bin",
        url = "$LOCAL/ggml-small.bin",
        sizeBytes = 488_000_000L,
    )

    val ALL = listOf(LLM, STT)
}

class ModelManager(private val context: Context) {

    val modelsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "models")
            dir.mkdirs()
            return dir
        }

    fun getModelFile(model: ModelInfo): File = File(modelsDir, model.filename)

    fun isDownloaded(model: ModelInfo): Boolean {
        val file = getModelFile(model)
        val exists = file.exists() && file.length() > 1000
        Log.i(TAG, "isDownloaded(${model.filename}): path=${file.absolutePath} exists=${file.exists()} size=${file.length()} result=$exists")
        return exists
    }

    fun allModelsReady(): Boolean = Models.ALL.all { isDownloaded(it) }

    /**
     * Download a model file directly using HttpURLConnection.
     * This runs in the app's own process so it respects our cleartext network config.
     * Returns true on success.
     */
    fun downloadModel(model: ModelInfo, onProgress: (Long, Long) -> Unit): Boolean {
        val outFile = getModelFile(model)
        Log.i(TAG, "Downloading ${model.name} from ${model.url} to ${outFile.absolutePath}")

        try {
            val url = URL(model.url)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.requestMethod = "GET"

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "Download failed: HTTP $responseCode for ${model.url}")
                return false
            }

            val totalBytes = conn.contentLengthLong
            Log.i(TAG, "Download started: $totalBytes bytes")

            val input = conn.inputStream
            val output = FileOutputStream(outFile)
            val buffer = ByteArray(8192)
            var downloadedBytes = 0L

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                onProgress(downloadedBytes, totalBytes)
            }

            output.close()
            input.close()
            conn.disconnect()

            Log.i(TAG, "Download complete: ${outFile.absolutePath} (${outFile.length()} bytes)")
            return outFile.length() > 1000
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${model.name}", e)
            outFile.delete()
            return false
        }
    }
}
