package com.example.hiddencamera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private var isRecording = false

    private val recordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.hiddencamera.RECORDING_STARTED" -> updateUI(true)
                "com.example.hiddencamera.RECORDING_STOPPED" -> updateUI(false)
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

        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction("com.example.hiddencamera.RECORDING_STARTED")
            addAction("com.example.hiddencamera.RECORDING_STOPPED")
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

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
        val storagePath = Prefs.getStoragePath(this)
        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_OUTPUT_PATH, storagePath)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show()
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
            findViewById<android.widget.TextView>(R.id.tvStatus).text = "录制状态：录制中..."
        } else {
            btnRecord.text = getString(R.string.start_recording)
            btnRecord.setBackgroundColor(getColor(R.color.primary))
            findViewById<android.view.View>(R.id.indicator)
                .setBackgroundResource(R.drawable.indicator_idle)
            findViewById<android.widget.TextView>(R.id.tvStatus).text = getString(R.string.recording_status)
        }
    }
}
