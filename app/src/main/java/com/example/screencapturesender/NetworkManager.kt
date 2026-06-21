package com.example.screencapturesender

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object NetworkManager {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun uploadFrame(
        context: Context,
        endpointUrl: String,
        jpegBytes: ByteArray,
        width: Int,
        height: Int,
        batteryPercent: Int,
        quality: Int
    ): Boolean {
        return try {
            val requestBody = jpegBytes.toRequestBody("image/jpeg".toMediaType())

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("timestamp", System.currentTimeMillis().toString())
                .addFormDataPart("battery", batteryPercent.toString())
                .addFormDataPart("width", width.toString())
                .addFormDataPart("height", height.toString())
                .addFormDataPart("jpegQuality", quality.toString())
                .addFormDataPart("image", "frame.jpg", requestBody)
                .build()

            val request = Request.Builder()
                .url(endpointUrl)
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    AppLogger.i(context, "Upload OK (${response.code})")
                    true
                } else {
                    AppLogger.w(context, "Upload failed (${response.code}): ${response.message}")
                    false
                }
            }
        } catch (error: Throwable) {
            AppLogger.e(context, "Upload exception", error)
            false
        }
    }
}
