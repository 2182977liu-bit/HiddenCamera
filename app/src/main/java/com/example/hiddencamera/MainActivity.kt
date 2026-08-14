package com.example.hiddencamera

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: MaterialButton
    private lateinit var tvDuration: TextView
    private lateinit var tvStatus: TextView
    private lateinit var recordingOverlay: View
    private lateinit var indicatorPulse: View

    private var recordingService: RecordingService? = null
    private var serviceBound = false

    private val viewModel: MainViewModel by viewModels()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.LocalBinder
            recordingService = binder.getService()
            serviceBound = true
            viewModel.bindService(recordingService)
            connectPreviewToService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            serviceBound = false
            viewModel.bindService(null)
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
                Toast.makeText(this, R.string.need_storage_permission, Toast.LENGTH_LONG).show()
            }
        } else {
            doStartRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(
                ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    R.mipmap.ic_launcher,
                    getColor(R.color.primary)
                )
            )
        }

        btnRecord = findViewById(R.id.btnRecord)
        tvDuration = findViewById(R.id.tvDuration)
        tvStatus = findViewById(R.id.tvStatus)
        recordingOverlay = findViewById(R.id.recordingOverlay)
        indicatorPulse = findViewById(R.id.indicatorPulse)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        btnRecord.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isRecording || state.isStopping) {
                stopRecording()
            } else {
                checkPermissionsAndStart()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        bindService(
            Intent(this, RecordingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        observeUiState()
    }

    /**
     * 监听 ViewModel 状态并更新 UI
     *
     * 关键改进：UI 状态完全由 Service 通过 StateFlow 驱动，
     * 不再依赖 BroadcastReceiver，避免状态不一致。
     */
    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }

        // 录制时长更新协程
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val state = viewModel.uiState.value
                    if (state.isRecording) {
                        val duration = viewModel.getCurrentDuration(System.currentTimeMillis())
                        tvDuration.text = formatDuration(duration)
                    }
                    delay(500)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyPreviewMode()
        if (serviceBound) {
            connectPreviewToService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PhoneCallDetector.stop()
        if (serviceBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
            recordingService = null
        }
        viewModel.unbindService()
    }

    private fun connectPreviewToService() {
        if (Prefs.getPreviewMode(this) != 0) return
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        recordingService?.updateSurfaceProvider(previewView.surfaceProvider)
    }

    private fun applyPreviewMode() {
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        val statusOverlay = findViewById<View>(R.id.statusOverlay)
        val blankOverlay = findViewById<View>(R.id.blankOverlay)

        when (Prefs.getPreviewMode(this)) {
            0 -> {
                previewView.visibility = View.VISIBLE
                statusOverlay.visibility = View.GONE
                blankOverlay.visibility = View.GONE
            }
            1 -> {
                previewView.visibility = View.GONE
                statusOverlay.visibility = View.VISIBLE
                blankOverlay.visibility = View.GONE
            }
            2 -> {
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
                Toast.makeText(this, R.string.please_grant_file_permission, Toast.LENGTH_SHORT).show()
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
        // 录制前检查存储空间
        if (!RecordingService.hasEnoughStorage()) {
            Toast.makeText(this, R.string.storage_insufficient, Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = Constants.ACTION_START
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show()

            // 启动来电监听
            PhoneCallDetector.start(this) {
                runOnUiThread {
                    if (viewModel.uiState.value.isRecording) {
                        Toast.makeText(
                            this,
                            "检测到来电，自动停止录制",
                            Toast.LENGTH_LONG
                        ).show()
                        stopRecording()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.start_failed, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopRecording() {
        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        startService(serviceIntent)
        Toast.makeText(this, R.string.recording_stopped, Toast.LENGTH_SHORT).show()
    }

    /**
     * 根据 UI 状态更新界面
     *
     * 关键改进：UI 不再预设状态，完全由 Service 通过 StateFlow 驱动
     */
    private fun updateUI(state: MainUiState) {
        // 显示错误
        if (state.showError && state.errorMessage != null) {
            Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show()
            viewModel.errorShown()
        }

        // 更新录制按钮
        btnRecord.apply {
            isEnabled = !state.isStopping
            text = when {
                state.isStopping -> getString(R.string.recording_stopping)
                state.isRecording -> getString(R.string.stop_recording)
                else -> getString(R.string.start_recording)
            }
            setBackgroundColor(
                if (state.isRecording) getColor(R.color.recording_red)
                else getColor(R.color.primary)
            )
        }

        // 更新录制状态指示器
        val indicator = findViewById<View>(R.id.indicator)
        if (state.isRecording) {
            indicator.setBackgroundResource(R.drawable.indicator_recording)
            tvStatus.text = getString(R.string.recording_in_progress)
            recordingOverlay.visibility = View.VISIBLE
        } else if (state.isStopping) {
            indicator.setBackgroundResource(R.drawable.indicator_idle)
            tvStatus.text = getString(R.string.recording_stopping)
            recordingOverlay.visibility = View.GONE
        } else {
            indicator.setBackgroundResource(R.drawable.indicator_idle)
            tvStatus.text = getString(R.string.recording_status)
            recordingOverlay.visibility = View.GONE
            tvDuration.text = "00:00"
        }
    }

    /** 格式化时长为 mm:ss */
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
