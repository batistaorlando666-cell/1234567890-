package com.example.screencapturesender

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val TAG = "ScreenCaptureSender"
    private const val LOG_FILE_NAME = "capture.log"
    private const val MAX_LOG_BYTES = 512 * 1024L

    private val lock = Any()

    fun d(context: Context, message: String) = write(context, "D", message, null)
    fun i(context: Context, message: String) = write(context, "I", message, null)
    fun w(context: Context, message: String) = write(context, "W", message, null)
    fun e(context: Context, message: String, error: Throwable? = null) = write(context, "E", message, error)

    private fun write(context: Context, level: String, message: String, error: Throwable?) {
        val text = buildString {
            append(timestamp())
            append(" [")
            append(level)
            append("] ")
            append(message)
            if (error != null) {
                append(" | ")
                append(error::class.java.simpleName)
                append(": ")
                append(error.message ?: "-")
            }
        }

        when (level) {
            "D" -> Log.d(TAG, text, error)
            "I" -> Log.i(TAG, text, error)
            "W" -> Log.w(TAG, text, error)
            else -> Log.e(TAG, text, error)
        }

        synchronized(lock) {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, LOG_FILE_NAME)
            if (file.exists() && file.length() > MAX_LOG_BYTES) {
                val rotated = File(dir, "capture.log.1")
                if (rotated.exists()) rotated.delete()
                file.renameTo(rotated)
            }

            file.appendText(text + System.lineSeparator())
            if (error != null) {
                file.appendText(error.stackTraceToString() + System.lineSeparator())
            }
        }

        if (level == "E") {
            ConfigManager(context).setLastError(message)
        }
    }

    private fun timestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date())
    }
}
