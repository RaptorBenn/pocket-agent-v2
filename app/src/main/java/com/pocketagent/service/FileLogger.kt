package com.pocketagent.service

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes logs to a file in shared storage so Termux can read them.
 * This works around Android 13+ logcat restrictions.
 */
object FileLogger {
    private var logFile: File? = null

    fun init(context: Context) {
        // Write to shared storage where Termux can read
        val dir = File("/storage/emulated/0/PocketAgent")
        dir.mkdirs()
        logFile = File(dir, "pocket-agent.log")
        logFile?.writeText("=== Pocket Agent Log ${Date()} ===\n")
    }

    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$ts] $msg\n"
        android.util.Log.i("PocketAgent", msg)
        try {
            logFile?.appendText(line)
        } catch (_: Exception) {}
    }

    fun error(msg: String, e: Throwable? = null) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$ts] ERROR: $msg${if (e != null) "\n  ${e.stackTraceToString()}" else ""}\n"
        android.util.Log.e("PocketAgent", msg, e)
        try {
            logFile?.appendText(line)
        } catch (_: Exception) {}
    }
}
