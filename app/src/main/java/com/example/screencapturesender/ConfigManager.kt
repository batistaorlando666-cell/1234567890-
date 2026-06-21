package com.example.screencapturesender

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptureSettings(
    val serverUrl: String,
    val intervalMs: Long,
    val delayMs: Long,
    val jpegQuality: Int
)

data class CaptureStats(
    val running: Boolean,
    val lastSendMs: Long,
    val failCount: Int,
    val captureCount: Int,
    val lastError: String,
    val statusMessage: String
)

class ConfigManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): CaptureSettings {
        return CaptureSettings(
            serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
            intervalMs = prefs.getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS),
            delayMs = prefs.getLong(KEY_DELAY_MS, DEFAULT_DELAY_MS),
            jpegQuality = prefs.getInt(KEY_JPEG_QUALITY, DEFAULT_JPEG_QUALITY)
        )
    }

    fun saveSettings(settings: CaptureSettings) {
        prefs.edit()
            .putString(KEY_SERVER_URL, settings.serverUrl)
            .putLong(KEY_INTERVAL_MS, settings.intervalMs)
            .putLong(KEY_DELAY_MS, settings.delayMs)
            .putInt(KEY_JPEG_QUALITY, settings.jpegQuality)
            .apply()
    }

    fun setRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_RUNNING, running).apply()
    }

    fun isRunning(): Boolean = prefs.getBoolean(KEY_RUNNING, false)

    fun setLastSend(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_SEND, epochMs).apply()
    }

    fun incrementCaptureCount() {
        val current = prefs.getInt(KEY_CAPTURE_COUNT, 0)
        prefs.edit().putInt(KEY_CAPTURE_COUNT, current + 1).apply()
    }

    fun incrementFailCount() {
        val current = prefs.getInt(KEY_FAIL_COUNT, 0)
        prefs.edit().putInt(KEY_FAIL_COUNT, current + 1).apply()
    }

    fun setLastError(message: String) {
        prefs.edit().putString(KEY_LAST_ERROR, message).apply()
    }

    fun setStatus(message: String) {
        prefs.edit().putString(KEY_STATUS_MESSAGE, message).apply()
    }

    fun loadStats(): CaptureStats {
        return CaptureStats(
            running = prefs.getBoolean(KEY_RUNNING, false),
            lastSendMs = prefs.getLong(KEY_LAST_SEND, 0L),
            failCount = prefs.getInt(KEY_FAIL_COUNT, 0),
            captureCount = prefs.getInt(KEY_CAPTURE_COUNT, 0),
            lastError = prefs.getString(KEY_LAST_ERROR, "") ?: "",
            statusMessage = prefs.getString(KEY_STATUS_MESSAGE, "Idle") ?: "Idle"
        )
    }

    fun buildStatusSummary(): String {
        val settings = loadSettings()
        val stats = loadStats()

        return buildString {
            appendLine("Service: " + if (stats.running) "ON" else "OFF")
            appendLine("Server: ${settings.serverUrl.ifBlank { "-" }}")
            appendLine("Interval: ${settings.intervalMs} ms")
            appendLine("Delay: ${settings.delayMs} ms")
            appendLine("JPEG quality: ${settings.jpegQuality}")
            appendLine("Last send: ${formatEpoch(stats.lastSendMs)}")
            appendLine("Captures: ${stats.captureCount}")
            appendLine("Fails: ${stats.failCount}")
            appendLine("Last error: ${stats.lastError.ifBlank { "-" }}")
            appendLine("Message: ${stats.statusMessage.ifBlank { "-" }}")
        }
    }

    private fun formatEpoch(epochMs: Long): String {
        if (epochMs <= 0L) return "-"
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return formatter.format(Date(epochMs))
    }

    companion object {
        private const val PREFS_NAME = "screen_capture_sender_prefs"

        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_INTERVAL_MS = "interval_ms"
        private const val KEY_DELAY_MS = "delay_ms"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"

        private const val KEY_RUNNING = "running"
        private const val KEY_LAST_SEND = "last_send"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_CAPTURE_COUNT = "capture_count"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_STATUS_MESSAGE = "status_message"

        const val DEFAULT_INTERVAL_MS = 1000L
        const val DEFAULT_DELAY_MS = 60_000L
        const val DEFAULT_JPEG_QUALITY = 60
    }
}
