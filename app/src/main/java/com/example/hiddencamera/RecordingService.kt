package com.example.hiddencamera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RecordingService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_OUTPUT_PATH = "output_path"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecording = false
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    // startForeground 必须是第一个操作（Android 12+ 限制 5 秒内调用）
                    startForegroundNotification()
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                    val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                    startRecording(outputPath)
                }
                ACTION_STOP -> {
                    stopRecording()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand 异常", e)
            notifyError("启动失败: ${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        cameraProvider = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(outputPath: String?) {
        try {
            // 使用 App 私有外部存储目录，不需要存储权限
            val storagePath = outputPath ?: getExternalFilesDir(null)?.absolutePath
                ?: filesDir.absolutePath
            val outputDir = File(storagePath, "videos")
            if (!outputDir.exists()) {
                val created = outputDir.mkdirs()
                if (!created) {
                    notifyError("无法创建存储目录: ${outputDir.absolutePath}")
                    return
                }
            }

            // 创建 .nomedia 文件，防止相册扫描
            try {
                val nomediaFile = File(outputDir, ".nomedia")
                if (!nomediaFile.exists()) {
                    nomediaFile.createNewFile()
                }
            } catch (e: Exception) {
                Log.w(TAG, "创建 .nomedia 失败", e)
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val outputFile = File(outputDir, "VID_$timestamp.mp4")

            Log.d(TAG, "准备录制: ${outputFile.absolutePath}")

            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                try {
                    cameraProvider = future.get()
                    bindCameraUseCases(outputFile)
                } catch (e: Exception) {
                    Log.e(TAG, "获取 CameraProvider 失败", e)
                    notifyError("相机初始化失败: ${e.message}")
                }
            }, cameraExecutor)
        } catch (e: Exception) {
            Log.e(TAG, "startRecording 异常", e)
            notifyError("启动录制失败: ${e.message}")
        }
    }

    private fun bindCameraUseCases(outputFile: File) {
        val provider = cameraProvider ?: return

        provider.unbindAll()

        val lensFacing = if (Prefs.getCameraLens(this) == "front") {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val quality = when (Prefs.getResolution(this)) {
            0 -> Quality.FHD
            1 -> Quality.HD
            else -> Quality.SD
        }

        val recorder = Recorder.Builder()
            .setExecutor(cameraExecutor)
            .setQualitySelector(
                QualitySelector.from(
                    quality,
                    FallbackStrategy.lowerQualityOrHigherThan(quality)
                )
            )
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        try {
            provider.bindToLifecycle(this, cameraSelector, videoCapture)
            startVideoCapture(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "绑定相机失败", e)
            notifyError("绑定相机失败: ${e.message}")
        }
    }

    private fun startVideoCapture(outputFile: File) {
        val capture = videoCapture ?: return

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .apply {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        withAudioEnabled()
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "录音权限被拒绝，仅录制视频", e)
                }
            }
            .start(cameraExecutor) { event ->
                try {
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d(TAG, "录制开始: ${outputFile.absolutePath}")
                            sendBroadcast(Intent("com.example.hiddencamera.RECORDING_STARTED"))
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            if (event.hasError()) {
                                val errorMsg = event.cause?.message ?: "未知录制错误"
                                Log.e(TAG, "录制错误: $errorMsg", event.cause)
                                notifyError("录制错误: $errorMsg")
                            } else {
                                Log.d(TAG, "录制完成: ${outputFile.absolutePath}")
                            }
                            sendBroadcast(Intent("com.example.hiddencamera.RECORDING_STOPPED"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理录制事件异常", e)
                }
            }
    }

    private fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "停止录制时异常", e)
        }
        activeRecording = null
        videoCapture = null
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "解绑相机时异常", e)
        }
        isRecording = false
    }

    private fun notifyError(message: String) {
        Log.e(TAG, message)
        try {
            val intent = Intent("com.example.hiddencamera.RECORDING_ERROR").apply {
                putExtra("error_message", message)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "发送错误广播失败", e)
        }
    }
}
