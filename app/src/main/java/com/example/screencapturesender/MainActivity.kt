package com.example.screencapturesender

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val configManager by lazy { ConfigManager(this) }
    private val mediaProjectionManager by lazy {
        getSystemService(MediaProjectionManager::class.java)
            ?: throw IllegalStateException("MediaProjectionManager unavailable")
    }

    private lateinit var etServerUrl: EditText
    private lateinit var etIntervalMs: EditText
    private lateinit var etDelayMs: EditText
    private lateinit var etJpegQuality: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val uiHandler = Handler(Looper.getMainLooper())

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                toast("Permissao de notificacao negada. O app ainda pode tentar rodar.")
            }
            startScreenCapturePermissionFlow()
        }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                toast("Permissao de captura negada.")
                configManager.setStatus("MediaProjection negada")
                renderStatus()
                return@registerForActivityResult
            }

            startCaptureService(result.resultCode, result.data!!)
        }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            renderStatus()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadSettingsIntoForm()
        initButtons()
        renderStatus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(refreshRunnable)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun bindViews() {
        etServerUrl = findViewById(R.id.etServerUrl)
        etIntervalMs = findViewById(R.id.etIntervalMs)
        etDelayMs = findViewById(R.id.etDelayMs)
        etJpegQuality = findViewById(R.id.etJpegQuality)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
    }

    private fun initButtons() {
        btnStart.setOnClickListener { onStartPressed() }
        btnStop.setOnClickListener { stopCaptureService() }
    }

    private fun loadSettingsIntoForm() {
        val settings = configManager.loadSettings()
        etServerUrl.setText(settings.serverUrl)
        etIntervalMs.setText(settings.intervalMs.toString())
        etDelayMs.setText(settings.delayMs.toString())
        etJpegQuality.setText(settings.jpegQuality.toString())
    }

    private fun onStartPressed() {
        val settings = readSettingsFromForm() ?: return
        configManager.saveSettings(settings)
        configManager.setStatus("Preparing capture permission...")
        renderStatus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startScreenCapturePermissionFlow()
        }
    }

    private fun startScreenCapturePermissionFlow() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startCaptureService(resultCode: Int, resultData: Intent) {
        val serviceIntent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
            putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(CaptureService.EXTRA_DATA, resultData)
        }

        ContextCompat.startForegroundService(this, serviceIntent)
        configManager.setRunning(true)
        configManager.setStatus("Service started")
        toast("Captura iniciada.")
        renderStatus()
    }

    private fun stopCaptureService() {
        val stopIntent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        }
        ContextCompat.startForegroundService(this, stopIntent)
        configManager.setRunning(false)
        configManager.setStatus("Stop requested")
        toast("Parando...")
        renderStatus()
    }

    private fun readSettingsFromForm(): CaptureSettings? {
        val serverUrl = etServerUrl.text?.toString()?.trim().orEmpty()
        val intervalMs = etIntervalMs.text?.toString()?.trim()?.toLongOrNull()
        val delayMs = etDelayMs.text?.toString()?.trim()?.toLongOrNull()
        val jpegQuality = etJpegQuality.text?.toString()?.trim()?.toIntOrNull()

        if (serverUrl.isBlank()) {
            toast("Preencha a URL do servidor.")
            return null
        }

        val uri = runCatching { android.net.Uri.parse(serverUrl) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) {
            toast("URL do servidor invalida.")
            return null
        }

        if (intervalMs == null || intervalMs < 100L) {
            toast("Intervalo invalido. Use pelo menos 100 ms.")
            return null
        }

        if (delayMs == null || delayMs < 0L) {
            toast("Delay invalido.")
            return null
        }

        if (jpegQuality == null || jpegQuality !in 1..100) {
            toast("Qualidade JPEG invalida. Use 1 a 100.")
            return null
        }

        return CaptureSettings(
            serverUrl = serverUrl,
            intervalMs = intervalMs,
            delayMs = delayMs,
            jpegQuality = jpegQuality
        )
    }

    private fun renderStatus() {
        tvStatus.text = configManager.buildStatusSummary()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
