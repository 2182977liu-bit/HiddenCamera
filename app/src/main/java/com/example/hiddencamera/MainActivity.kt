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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hiddencamera.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    val viewModel: MainViewModel by viewModels()

    private var recordingService: RecordingService? = null
    private var serviceBound = false

    /** 当前活跃的录制页实例，用于连接预览表面 */
    private var recordFragment: RecordFragment? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.LocalBinder
            recordingService = binder.getService()
            serviceBound = true
            viewModel.bindService(recordingService)
            recordFragment?.connectPreview()
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.app_name),
                R.mipmap.ic_launcher,
                getColor(R.color.primary)
            )
        )

        // 底部导航：切换三个 Tab
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_record -> showFragment(RecordFragment())
                R.id.nav_files -> showFragment(FilesFragment())
                R.id.nav_settings -> showFragment(SettingsFragment())
            }
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_record

        bindService(
            Intent(this, RecordingService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        observeErrors()
    }

    override fun onResume() {
        super.onResume()
        recordFragment?.connectPreview()
    }

    override fun onPause() {
        super.onPause()
        // 息屏/退到后台时主动分离预览 surface。
        // 若任其被系统销毁，CameraX 会因 Preview surface 分离而中断整个相机会话，
        // 导致录制只停留在最后一帧。分离后 VideoCapture 仍由服务独立驱动。
        recordingService?.updateSurfaceProvider(null)
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

    /** ViewModel 观察错误，统一用 Toast 提示 */
    private fun observeErrors() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.showError && state.errorMessage != null) {
                        Toast.makeText(this@MainActivity, state.errorMessage, Toast.LENGTH_LONG).show()
                        viewModel.errorShown()
                    }
                }
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        val supportFragmentManager = supportFragmentManager
        // 清理当前栈，避免多个实例叠加
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()

        if (fragment is RecordFragment) {
            recordFragment = fragment
        }
    }

    private fun connectPreview(sp: androidx.camera.core.Preview.SurfaceProvider?) {
        recordingService?.updateSurfaceProvider(sp)
    }

    // ===== 供 Fragment 调用的公开接口 =====

    /** 录制页视图就绪后调用，把预览表面交给服务 */
    fun connectPreviewFromFragment(sp: androidx.camera.core.Preview.SurfaceProvider?) {
        connectPreview(sp)
    }

    fun currentIsRecording(): Boolean = viewModel.uiState.value.isRecording
    fun currentIsStopping(): Boolean = viewModel.uiState.value.isStopping

    fun startRecording() {
        checkPermissionsAndStart()
    }

    fun stopRecording() {
        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        startService(serviceIntent)
        Toast.makeText(this, R.string.recording_stopped, Toast.LENGTH_SHORT).show()
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
}