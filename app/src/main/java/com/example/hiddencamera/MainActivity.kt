package com.example.hiddencamera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var tvStoragePath: TextView
    private var isRecording = false
    private var storagePermissionGranted = false

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
            storagePermissionGranted = Environment.isExternalStorageManager()
        } else {
            storagePermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (storagePermissionGranted) {
            doStartRecording()
        } else {
            Toast.makeText(this, "需要存储权限才能保存视频到 Download/xcodx/", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        btnSettings = findViewById(R.id.btnSettings)
        tvStoragePath = findViewById(R.id.tvStoragePath)
        val btnOpenFolder = findViewById<MaterialButton>(R.id.btnOpenFolder)

        // 显示存储路径
        val videoDir = getVideoDir()
        tvStoragePath.text = videoDir.absolutePath

        // 打开文件夹
        btnOpenFolder.setOnClickListener {
            openFolder(videoDir)
        }

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
        // 从设置页返回后刷新权限状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storagePermissionGranted = Environment.isExternalStorageManager()
        } else {
            storagePermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(recordingReceiver)
        } catch (_: Exception) {}
    }

    private fun getVideoDir(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "xcodx")
    }

    private fun openFolder(dir: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(dir.absolutePath), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    setDataAndType(Uri.parse(dir.absolutePath), "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e2: Exception) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", dir.absolutePath))
                Toast.makeText(this, "已复制路径到剪贴板:\n${dir.absolutePath}", Toast.LENGTH_LONG).show()
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

        // Android 8-9 需要存储权限
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
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                doStartRecording()
            } else {
                Toast.makeText(this, "请授予文件管理权限", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 8-9 需要 WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                doStartRecording()
            } else {
                // 已在 permissionLauncher 中处理
                doStartRecording()
            }
        } else {
            // Android 10 可以直接访问公共 Download 目录
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
            findViewById<android.view.View>(R.id.indicator)
                .setBackgroundResource(R.drawable.indicator_recording)
            findViewById<TextView>(R.id.tvStatus).text = "录制状态：录制中..."
        } else {
            btnRecord.text = getString(R.string.start_recording)
            btnRecord.setBackgroundColor(getColor(R.color.primary))
            findViewById<android.view.View>(R.id.indicator)
                .setBackgroundResource(R.drawable.indicator_idle)
            findViewById<TextView>(R.id.tvStatus).text = getString(R.string.recording_status)
        }
    }
}
