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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var tvStoragePath: TextView
    private var isRecording = false

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
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startRecording()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(recordingReceiver)
        } catch (_: Exception) {}
    }

    private fun getVideoDir(): File {
        val base = getExternalFilesDir(null) ?: filesDir
        val dir = File(base, "videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun openFolder(dir: File) {
        try {
            if (!dir.exists()) {
                Toast.makeText(this, "文件夹不存在", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                dir
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // FileProvider 方式失败，尝试用 SAF
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setData(Uri.parse(dir.absolutePath))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e2: Exception) {
                // 最终回退：复制路径到剪贴板
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

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            startRecording()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startRecording() {
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
