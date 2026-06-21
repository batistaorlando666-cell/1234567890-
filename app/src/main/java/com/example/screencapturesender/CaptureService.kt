package com.example.screencapturesender

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CaptureService : LifecycleService() {

    private val configManager by lazy { ConfigManager(applicationContext) }

    private var screenCaptureManager: ScreenCaptureManager? = null
    private var captureJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanup("Stop requested")
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                if (!started) {
                    startSession(intent)
                }
            }

            else -> {
                AppLogger.w(this, "Unknown action: ${intent.action}")
            }
        }

        return if (started) START_REDELIVER_INTENT else START_NOT_STICKY
    }

    private fun startSession(intent: Intent?) {
        if (intent == null) {
            AppLogger.e(this, "Missing start intent")
            cleanup("Missing start intent")
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            AppLogger.e(this, "MediaProjection permission was not granted")
            cleanup("Permission missing")
            return
        }

        val settings = configManager.loadSettings()
        if (settings.serverUrl.isBlank()) {
            AppLogger.e(this, "Server URL is empty")
            cleanup("Invalid server URL")
            return
        }

        started = true
        configManager.setRunning(true)
        configManager.setStatus("Foreground service active")

        acquireWakeLock()
        startForegroundWithNotification()

        screenCaptureManager = ScreenCaptureManager(
            context = this,
            resultCode = resultCode,
            resultData = resultData,
            onProjectionStopped = { cleanup("Projection stopped") }
        )

        if (!screenCaptureManager!!.start()) {
            AppLogger.e(this, "Unable to start ScreenCaptureManager")
            cleanup("Capture init failed")
            return
        }

        captureJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i(this@CaptureService, "Waiting ${settings.delayMs} ms before first capture")
                configManager.setStatus("Waiting ${settings.delayMs} ms before first capture")
                delay(settings.delayMs)

                configManager.setStatus("Capturing")
                while (isActive && started) {
                    val image = screenCaptureManager?.acquireLatestImage()
                    if (image == null) {
                        delay(settings.intervalMs)
                        continue
                    }

                    try {
                        val jpegBytes = ImageProcessor.imageToJpegBytes(image, settings.jpegQuality)
                        val battery = readBatteryPercent()

                        configManager.incrementCaptureCount()

                        val sent = uploadWithRetry(
                            endpointUrl = settings.serverUrl,
                            jpegBytes = jpegBytes,
                            width = screenCaptureManager?.width ?: 0,
                            height = screenCaptureManager?.height ?: 0,
                            batteryPercent = battery,
                            jpegQuality = settings.jpegQuality
                        )

                        if (sent) {
                            configManager.setLastSend(System.currentTimeMillis())
                            configManager.setStatus("Last frame sent OK")
                        } else {
                            configManager.incrementFailCount()
                            configManager.setStatus("Upload failed")
                        }
                    } catch (error: Throwable) {
                        configManager.incrementFailCount()
                        configManager.setStatus("Processing error")
                        AppLogger.e(this@CaptureService, "Capture loop error", error)
                    } finally {
                        runCatching { image.close() }
                    }

                    delay(settings.intervalMs)
                }
            } catch (error: Throwable) {
                AppLogger.e(this@CaptureService, "Capture job crashed", error)
                configManager.incrementFailCount()
                configManager.setStatus("Capture job crashed")
            } finally {
                cleanup("Capture loop finished")
            }
        }
    }

    private suspend fun uploadWithRetry(
        endpointUrl: String,
        jpegBytes: ByteArray,
        width: Int,
        height: Int,
        batteryPercent: Int,
        jpegQuality: Int
    ): Boolean {
        repeat(3) { attempt ->
            val ok = NetworkManager.uploadFrame(
                context = this,
                endpointUrl = endpointUrl,
                jpegBytes = jpegBytes,
                width = width,
                height = height,
                batteryPercent = batteryPercent,
                quality = jpegQuality
            )
            if (ok) return true
            if (attempt < 2) {
                delay(500L * (attempt + 1))
            }
        }
        return false
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, CaptureService::class.java).apply {
            action = ACTION_STOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flags)

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(this, 2, openIntent, flags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("ScreenCapture Sender V1")
            .setContentText("Captura em segundo plano")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:ScreenCaptureWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun readBatteryPercent(): Int {
        val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) return -1

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1

        return (level * 100) / scale
    }

    private fun cleanup(reason: String) {
        if (!started) {
            return
        }

        started = false
        captureJob?.cancel()
        captureJob = null

        screenCaptureManager?.stop()
        screenCaptureManager = null

        releaseWakeLock()

        configManager.setRunning(false)
        configManager.setStatus(reason)

        runCatching { stopForeground(Service.STOP_FOREGROUND_REMOVE) }
        runCatching { stopSelf() }

        AppLogger.i(this, "Service cleaned up: $reason")
    }

    override fun onDestroy() {
        cleanup("Service destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground service for screen capture"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.screencapturesender.action.START"
        const val ACTION_STOP = "com.example.screencapturesender.action.STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        private const val NOTIFICATION_CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
