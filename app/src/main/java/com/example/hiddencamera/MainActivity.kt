package com.example.hiddencamera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: MaterialButton
    private var isRecording = false
    private var recordingService: RecordingService? = null
    private var serviceBound = false

    private val recordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.hiddencamera.RECORDING_STARTED" -> updateUI(true)
                "com.example.hiddencamera.RECORDING_STOPPED" -> updateUI(false)
                "com.example.hiddencamera.RECORDING_ERROR" -> {
                    val errorMsg = intent.getStringExtra("error_message") ?: "未知错误"
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                    updateUI(false)
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.LocalBinder
            recordingService = binder.getService()
            serviceBound = true
            connectPreviewToService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            serviceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        if (cameraGranted && audioGranted && notifGranted) {
            checkStoragePermissionAndStart()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                doStartRecording()
            } else {
                Toast.makeText(this, "需要存储权限才能保存视频", Toast.LENGTH_LONG).show()
            }
        } else {
            doStartRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionsAndStart()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 绑定 Service 以获取实例（传递 SurfaceProvider）
        bindService(
            Intent(this, RecordingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        val filter = IntentFilter().apply {
            addAction("com.example.hiddencamera.RECORDING_STARTED")
            addAction("com.example.hiddencamera.RECORDING_STOPPED")
            addAction("com.example.hiddencamera.RECORDING_ERROR")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recordingReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        applyPreviewMode()
        // 每次 Resume 重新连接 Surface（防止 Surface 重建后失效）
        if (serviceBound) {
            connectPreviewToService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
            recordingService = null
        }
        try {
            unregisterReceiver(recordingReceiver)
        } catch (_: Exception) {}
    }

    /**
     * 将 PreviewView 的 SurfaceProvider 传递给 Service
     */
    private fun connectPreviewToService() {
        if (Prefs.getPreviewMode(this) != 0) return // 非预览模式不需要连接
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        recordingService?.updateSurfaceProvider(previewView.surfaceProvider)
    }

    private fun applyPreviewMode() {
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        val statusOverlay = findViewById<View>(R.id.statusOverlay)
        val blankOverlay = findViewById<View>(R.id.blankOverlay)

        when (Prefs.getPreviewMode(this)) {
            0 -> { // 实时预览
                previewView.visibility = View.VISIBLE
                statusOverlay.visibility = View.GONE
                blankOverlay.visibility = View.GONE
            }
            1 -> { // 录制状态
                previewView.visibility = View.GONE
                statusOverlay.visibility = View.VISIBLE
                blankOverlay.visibility = View.GONE
            }
            2 -> { // 空白
                previewView.visibility = View.GONE
                statusOverlay.visibility = View.GONE
                blankOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            checkStoragePermissionAndStart()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun checkStoragePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                doStartRecording()
            } else {
                Toast.makeText(this, "请授予文件管理权限", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        } else {
            doStartRecording()
        }
    }

    private fun doStartRecording() {
        try {
            val serviceIntent = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        startService(serviceIntent)
        Toast.makeText(this, R.string.recording_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun updateUI(recording: Boolean) {
        isRecording = recording
        if (recording) {
            btnRecord.text = getString(R.string.stop_recording)
            btnRecord.setBackgroundColor(getColor(R.color.recording_red))
            findViewById<View>(R.id.indicator)
                .setBackgroundResource(R.drawable.indicator_recording)
            findViewById<TextView>(R.id.tvStatus).text = "录制状态：录制中..."
        } else {
            btnRecord.text = getString(R.string.start_recording)
            btnRecord.setBackgroundColor(getColor(R.color.primary))
            findViewById<View>(R.id.indicator)
                .setBackgroundResource(R.drawable.indicator_idle)
            findViewById<TextView>(R.id.tvStatus).text = getString(R.string.recording_status)
        }
    }
}
