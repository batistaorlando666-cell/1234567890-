package com.example.screencapturesender

import android.graphics.Bitmap
import android.media.Image
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageProcessor {

    fun imageToJpegBytes(image: Image, jpegQuality: Int): ByteArray {
        val safeQuality = jpegQuality.coerceIn(1, 100)
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = max(0, rowStride - pixelStride * image.width)
        val bitmapWidth = image.width + rowPadding / pixelStride

        val fullBitmap = Bitmap.createBitmap(
            bitmapWidth,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        try {
            fullBitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap,
                0,
                0,
                image.width,
                image.height
            )

            try {
                val output = ByteArrayOutputStream()
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, safeQuality, output)
                return output.toByteArray()
            } finally {
                croppedBitmap.recycle()
            }
        } finally {
            fullBitmap.recycle()
        }
    }
}
