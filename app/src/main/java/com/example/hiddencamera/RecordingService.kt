package com.example.hiddencamera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Binder
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.Range
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
        const val ACTION_TOGGLE = "ACTION_TOGGLE"

        private fun getOutputDir(): File {
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "xcodx")
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mainHandler = Handler(Looper.getMainLooper())

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var previewUseCase: Preview? = null
    private var activeRecording: Recording? = null
    private var isRecording = false
    private var isStopping = false
    private lateinit var cameraExecutor: ExecutorService

    var previewSurfaceProvider: Preview.SurfaceProvider? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    fun updateSurfaceProvider(sp: Preview.SurfaceProvider?) {
        previewSurfaceProvider = sp
        previewUseCase?.setSurfaceProvider(sp)
    }

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
                    startForegroundNotification()
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                    startRecording()
                    updateNotification(true)
                }
                ACTION_STOP -> {
                    updateNotification(false)
                    requestStopRecording()
                }
                ACTION_TOGGLE -> {
                    if (isRecording) {
                        updateNotification(false)
                        requestStopRecording()
                    } else {
                        startForegroundNotification()
                        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                        startRecording()
                        updateNotification(true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand 异常", e)
            notifyError("启动失败: ${e.message}")
            safeStopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeRecording = null
        videoCapture = null
        previewUseCase = null
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraProvider = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

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
        val notification = buildNotification(false)
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

    private fun updateNotification(recording: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(recording))
    }

    private fun buildNotification(recording: Boolean): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                if (recording) getString(R.string.notification_recording)
                else getString(R.string.notification_title)
            )
            .setContentText(
                if (recording) getString(R.string.notification_text_recording)
                else getString(R.string.notification_text)
            )
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        if (Prefs.isNotificationActionEnabled(this)) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_recording),
                stopPendingIntent
            )
        }

        return builder.build()
    }

    private fun startRecording() {
        try {
            val outputDir = getOutputDir()
            if (!outputDir.exists()) {
                val created = outputDir.mkdirs()
                if (!created) {
                    notifyError("无法创建存储目录: ${outputDir.absolutePath}")
                    return
                }
            }

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

            mainHandler.post {
                try {
                    val future = ProcessCameraProvider.getInstance(this)
                    future.addListener({
                        try {
                            cameraProvider = future.get()
                            bindCameraUseCases(outputFile)
                        } catch (e: Exception) {
                            Log.e(TAG, "获取 CameraProvider 失败", e)
                            notifyError("相机初始化失败: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(this))
                } catch (e: Exception) {
                    Log.e(TAG, "ProcessCameraProvider.getInstance 失败", e)
                    notifyError("相机初始化失败: ${e.message}")
                }
            }
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

        val targetFps = Prefs.getFps(this)

        val recorder = Recorder.Builder()
            .setExecutor(cameraExecutor)
            .setQualitySelector(
                QualitySelector.from(
                    quality,
                    FallbackStrategy.lowerQualityOrHigherThan(quality)
                )
            )
            .build()

        val videoCaptureBuilder = VideoCapture.Builder(recorder)
        try {
            val fpsRange = Range(targetFps, targetFps)
            Camera2Interop.Extender(videoCaptureBuilder).apply {
                setCaptureRequestOption(
                    android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    fpsRange
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "设置帧率 $targetFps 失败，使用默认帧率", e)
        }

        videoCapture = videoCaptureBuilder.build()

        previewUseCase = Preview.Builder().build()
        previewSurfaceProvider?.let { sp ->
            previewUseCase?.setSurfaceProvider(sp)
        }

        try {
            provider.bindToLifecycle(this, cameraSelector, previewUseCase, videoCapture)
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
                            isStopping = false
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
                            activeRecording = null
                            sendBroadcast(Intent("com.example.hiddencamera.RECORDING_STOPPED"))

                            if (isStopping) {
                                mainHandler.post {
                                    cleanupAndStop()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理录制事件异常", e)
                }
            }
    }

    private fun requestStopRecording() {
        if (!isRecording && activeRecording == null) {
            safeStopSelf()
            return
        }

        isStopping = true
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "停止录制时异常", e)
            activeRecording = null
            cleanupAndStop()
            return
        }

        mainHandler.postDelayed({
            if (isStopping) {
                Log.w(TAG, "Finalize 超时，强制清理")
                activeRecording = null
                cleanupAndStop()
            }
        }, 3000)
    }

    private fun cleanupAndStop() {
        videoCapture = null
        previewUseCase = null
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "解绑相机时异常", e)
        }
        cameraProvider = null
        isRecording = false
        isStopping = false
        safeStopSelf()
    }

    private fun safeStopSelf() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            stopSelf()
        } catch (_: Exception) {}
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
