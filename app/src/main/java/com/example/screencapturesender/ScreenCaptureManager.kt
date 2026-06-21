package com.example.screencapturesender

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import java.lang.IllegalStateException

class ScreenCaptureManager(
    private val context: Context,
    private val resultCode: Int,
    private val resultData: Intent,
    private val onProjectionStopped: () -> Unit
) {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    var densityDpi: Int = 0
        private set

    fun start(): Boolean {
        try {
            readDisplayMetrics()

            val projectionManager =
                context.getSystemService(MediaProjectionManager::class.java)
                    ?: return false

            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: return false

            mediaProjection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        onProjectionStopped()
                    }
                },
                Handler(Looper.getMainLooper())
            )

            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCaptureSender",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null
            )

            return virtualDisplay != null
        } catch (_: Throwable) {
            stop()
            return false
        }
    }

    fun acquireLatestImage(): Image? = imageReader?.acquireLatestImage()

    fun stop() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { mediaProjection?.stop() }

        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    private fun readDisplayMetrics() {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi

        if (width <= 0 || height <= 0) {
            throw IllegalStateException("Invalid display metrics")
        }
    }
}
