package com.pocketagent.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import java.io.File

data class ModelInfo(
    val name: String,
    val filename: String,
    val url: String,
    val sizeBytes: Long,
)

object Models {
    val LLM = ModelInfo(
        name = "Gemma 4 E4B Q5_K_M",
        filename = "google_gemma-4-E4B-it-Q5_K_M.gguf",
        url = "https://huggingface.co/bartowski/google_gemma-4-E4B-it-GGUF/resolve/main/google_gemma-4-E4B-it-Q5_K_M.gguf",
        sizeBytes = 5_820_000_000L,
    )
    val STT = ModelInfo(
        name = "Whisper Small",
        filename = "ggml-small.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
        sizeBytes = 488_000_000L,
    )

    val ALL = listOf(LLM, STT)
}

class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "models")
            dir.mkdirs()
            return dir
        }

    /** Search paths for models — app dir first, then shared storage fallbacks */
    private val searchPaths: List<File>
        get() = listOf(
            modelsDir,
            File("/storage/emulated/0/PocketAgent"),
            File("/storage/emulated/0/Download"),
            File("/sdcard/PocketAgent"),
            File("/sdcard/Download"),
        )

    fun getModelFile(model: ModelInfo): File {
        // Check all search paths for existing model
        for (dir in searchPaths) {
            val file = File(dir, model.filename)
            if (file.exists() && file.length() > 1000) return file
        }
        // Default to app's own dir for downloads
        return File(modelsDir, model.filename)
    }

    fun isDownloaded(model: ModelInfo): Boolean {
        for (dir in searchPaths) {
            val file = File(dir, model.filename)
            if (file.exists() && file.length() > 1000) return true
        }
        return false
    }

    fun allModelsReady(): Boolean = Models.ALL.all { isDownloaded(it) }

    fun startDownload(model: ModelInfo): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Pocket Agent: ${model.name}")
            .setDescription("Downloading ${model.filename}")
            .setDestinationUri(Uri.fromFile(getModelFile(model)))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        return dm.enqueue(request)
    }

    fun getDownloadProgress(downloadId: Long): Pair<Long, Long> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)
        if (cursor.moveToFirst()) {
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            cursor.close()
            return downloaded to total
        }
        cursor.close()
        return 0L to 0L
    }
}
