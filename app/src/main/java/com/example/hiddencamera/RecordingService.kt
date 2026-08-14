package com.example.hiddencamera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RecordingService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "RecordingService"

        private fun getOutputDir(): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Constants.OUTPUT_DIR_NAME
            )
        }

        /** 检查存储空间是否足够 */
        fun hasEnoughStorage(): Boolean {
            return try {
                val stat = StatFs(getOutputDir().absolutePath)
                stat.availableBytes >= Constants.MIN_FREE_SPACE_BYTES
            } catch (e: Exception) {
                Log.w(TAG, "检查存储空间失败", e)
                true // 检查失败时不阻止录制
            }
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mainHandler = Handler(Looper.getMainLooper())

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    // 使用 StateFlow 替代 Broadcast
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    /** 兼容旧代码的 isRecording 接口 */
    val isRecording: Boolean
        get() = _recordingState.value is RecordingState.Recording

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var previewUseCase: Preview? = null
    private var activeRecording: Recording? = null

    private var isStopping = false
    private lateinit var cameraExecutor: ExecutorService

    private var extensionsManager: ExtensionsManager? = null
    private var isExtensionAvailable = false

    private var recordingStartTime = 0L
    private var currentOutputFile: File? = null

    /** 最大时长 Runnable */
    private val maxDurationRunnable = Runnable {
        Log.d(TAG, "达到最大录制时长，自动停止")
        requestStopRecording()
    }

    /** Finalize 超时 Runnable */
    private val stopTimeoutRunnable = Runnable {
        if (isStopping) {
            Log.w(TAG, "Finalize 超时，强制清理")
            activeRecording = null
            updateState(RecordingState.Idle)
            cleanupAndStop()
        }
    }

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
                Constants.ACTION_START -> {
                    if (isRecording) {
                        Log.w(TAG, "已在录制中，忽略重复开始请求")
                        return START_NOT_STICKY
                    }
                    if (!hasEnoughStorage()) {
                        updateState(RecordingState.Error("存储空间不足，请清理后重试"))
                        safeStopSelf()
                        return START_NOT_STICKY
                    }
                    startForegroundNotification()
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                    startRecording()
                    updateNotification(true)
                }
                Constants.ACTION_STOP -> {
                    if (!isRecording && activeRecording == null) {
                        Log.w(TAG, "当前未在录制，直接停止服务")
                        updateState(RecordingState.Idle)
                        safeStopSelf()
                        return START_NOT_STICKY
                    }
                    updateNotification(false)
                    requestStopRecording()
                }
                Constants.ACTION_TOGGLE -> {
                    if (isRecording) {
                        updateNotification(false)
                        requestStopRecording()
                    } else {
                        if (!hasEnoughStorage()) {
                            updateState(RecordingState.Error("存储空间不足，请清理后重试"))
                            safeStopSelf()
                            return START_NOT_STICKY
                        }
                        startForegroundNotification()
                        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                        startRecording()
                        updateNotification(true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand 异常", e)
            updateState(RecordingState.Error("启动失败: ${e.message}"))
            safeStopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // 移除所有 Handler 回调，避免内存泄漏
        mainHandler.removeCallbacks(maxDurationRunnable)
        mainHandler.removeCallbacks(stopTimeoutRunnable)

        activeRecording = null
        videoCapture = null
        previewUseCase = null
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraProvider = null
        extensionsManager = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        try {
            cameraExecutor.shutdownNow()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
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
            startForeground(
                Constants.NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(recording: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(Constants.NOTIFICATION_ID, buildNotification(recording))
    }

    private fun buildNotification(recording: Boolean): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(
                if (recording) getString(R.string.notification_recording)
                else getString(R.string.notification_title)
            )
            .setContentText(
                if (recording) getString(R.string.notification_text_recording)
                else getString(R.string.notification_text)
            )
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        // 通知栏始终显示停止按钮（安全考虑）
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.stop_service),
            stopPendingIntent
        )

        return builder.build()
    }

    private fun initExtensionsManager(provider: ProcessCameraProvider) {
        val future: ListenableFuture<ExtensionsManager> =
            ExtensionsManager.getInstanceAsync(this, provider)
        future.addListener({
            try {
                extensionsManager = future.get()
                val lensFacing = if (Prefs.getCameraLens(this) == "front") {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                // CameraX 扩展仅支持 FACE_RETOUCH（美颜），不区分 Android 版本
                val mode = ExtensionMode.FACE_RETOUCH

                isExtensionAvailable = try {
                    extensionsManager?.isExtensionAvailable(cameraSelector, mode) ?: false
                } catch (e: Exception) {
                    Log.w(TAG, "检查扩展可用性失败", e)
                    false
                }

                if (isExtensionAvailable) {
                    Log.d(TAG, "美颜扩展可用: $mode")
                } else {
                    Log.d(TAG, "美颜扩展不可用，将使用回退方案")
                }
            } catch (e: Exception) {
                Log.w(TAG, "初始化 ExtensionsManager 失败", e)
                isExtensionAvailable = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildCameraSelector(lensFacing: Int): CameraSelector {
        val baseSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        if (!Prefs.isBeautyEnabled(this) || !isExtensionAvailable || extensionsManager == null) {
            return baseSelector
        }

        // CameraX 扩展仅支持 FACE_RETOUCH（美颜）
        val mode = ExtensionMode.FACE_RETOUCH

        return try {
            extensionsManager!!.getExtensionEnabledCameraSelector(baseSelector, mode)
        } catch (e: Exception) {
            Log.w(TAG, "获取美颜 CameraSelector 失败，使用普通模式", e)
            baseSelector
        }
    }

    private fun startRecording() {
        try {
            val outputDir = getOutputDir()
            if (!outputDir.exists()) {
                val created = outputDir.mkdirs()
                if (!created) {
                    updateState(RecordingState.Error("无法创建存储目录: ${outputDir.absolutePath}"))
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

            val timestamp = SimpleDateFormat(Constants.TIMESTAMP_FORMAT, Locale.getDefault()).format(Date())
            val outputFile = File(outputDir, "${Constants.VIDEO_FILE_PREFIX}$timestamp.mp4")
            currentOutputFile = outputFile

            Log.d(TAG, "准备录制: ${outputFile.absolutePath}")

            mainHandler.post {
                try {
                    val future = ProcessCameraProvider.getInstance(this)
                    future.addListener({
                        try {
                            cameraProvider = future.get()
                            initExtensionsManager(cameraProvider!!)
                            bindCameraUseCases(outputFile)
                        } catch (e: Exception) {
                            Log.e(TAG, "获取 CameraProvider 失败", e)
                            updateState(RecordingState.Error("相机初始化失败: ${e.message}"))
                        }
                    }, ContextCompat.getMainExecutor(this))
                } catch (e: Exception) {
                    Log.e(TAG, "ProcessCameraProvider.getInstance 失败", e)
                    updateState(RecordingState.Error("相机初始化失败: ${e.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startRecording 异常", e)
            updateState(RecordingState.Error("启动录制失败: ${e.message}"))
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

        val cameraSelector = buildCameraSelector(lensFacing)

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
        // 注意：CameraX VideoCapture 不支持 setMaxVideoDuration，
        // 最大录制时长通过 Handler.postDelayed 在 startVideoCapture 中保底触发。

        if (targetFps > 0) {
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
        }

        videoCapture = videoCaptureBuilder.build()

        // 仅在有预览表面时才创建并绑定 Preview，
        // 避免无 surface 的 Preview 在部分设备上导致相机启动失败
        val preview = previewSurfaceProvider?.let { sp ->
            Preview.Builder().build().also { it.setSurfaceProvider(sp) }
        }
        previewUseCase = preview

        try {
            if (preview != null) {
                provider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } else {
                provider.bindToLifecycle(this, cameraSelector, videoCapture)
            }
            startVideoCapture(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "绑定相机失败", e)
            updateState(RecordingState.Error("绑定相机失败: ${e.message}"))
        }
    }

    private fun startVideoCapture(outputFile: File) {
        val capture = videoCapture ?: return

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .apply {
                // 先检查录音权限，避免 withAudioEnabled 抛出 IllegalStateException
                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    this@RecordingService,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudioPermission) {
                    try {
                        withAudioEnabled()
                    } catch (e: Exception) {
                        // 捕获所有异常（IllegalStateException/SecurityException 等），
                        // 音频失败时降级为仅录制视频，不影响主流程
                        Log.w(TAG, "启用音频失败，仅录制视频", e)
                    }
                } else {
                    Log.w(TAG, "无录音权限，仅录制视频")
                }
            }
            .start(cameraExecutor) { event ->
                try {
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            recordingStartTime = System.currentTimeMillis()
                            updateState(RecordingState.Recording(recordingStartTime, outputFile))
                            // 设置最大时长保底（防止 setMaxVideoDuration 不生效）
                            mainHandler.postDelayed(maxDurationRunnable, Constants.MAX_RECORDING_DURATION_MS)
                            Log.d(TAG, "录制开始: ${outputFile.absolutePath}")
                        }
                        is VideoRecordEvent.Finalize -> {
                            // 移除最大时长回调
                            mainHandler.removeCallbacks(maxDurationRunnable)
                            mainHandler.removeCallbacks(stopTimeoutRunnable)

                            if (event.hasError()) {
                                val errorMsg = mapFinalizeError(event)
                                Log.e(TAG, "录制错误: code=${event.error} $errorMsg", event.cause)
                                updateState(RecordingState.Error(errorMsg))
                            } else {
                                Log.d(TAG, "录制完成: ${outputFile.absolutePath}")
                                updateState(RecordingState.Idle)
                            }
                            activeRecording = null
                            currentOutputFile = null

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
        isStopping = true
        // 移除最大时长回调（手动停止时无需触发）
        mainHandler.removeCallbacks(maxDurationRunnable)

        // 更新状态为 Stopping，UI 可以显示加载态
        updateState(RecordingState.Stopping(currentOutputFile))

        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "停止录制时异常", e)
            activeRecording = null
            updateState(RecordingState.Idle)
            cleanupAndStop()
            return
        }

        // 设置超时保底
        mainHandler.postDelayed(stopTimeoutRunnable, Constants.STOP_TIMEOUT_MS)
    }

    private fun cleanupAndStop() {
        // 清理所有回调
        mainHandler.removeCallbacks(maxDurationRunnable)
        mainHandler.removeCallbacks(stopTimeoutRunnable)

        videoCapture = null
        previewUseCase = null
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "解绑相机时异常", e)
        }
        cameraProvider = null
        isStopping = false
        safeStopSelf()
    }

    private fun safeStopSelf() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        try {
            stopSelf()
        } catch (_: Exception) {}
    }

    /** 将 CameraX Finalize 错误码映射为可读信息 */
    private fun mapFinalizeError(event: VideoRecordEvent.Finalize): String {
        return when (event.error) {
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ->
                "录制错误: 文件大小达到上限"
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE ->
                "录制错误: 存储空间不足"
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ->
                "录制错误: 达到时长上限"
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ->
                "录制错误: 相机源失效（可能被其他应用占用）"
            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ->
                "录制错误: 未产生有效数据"
            else -> "录制错误: ${event.cause?.message ?: "未知错误"}"
        }
    }

    /** 统一状态更新入口 */
    private fun updateState(state: RecordingState) {
        _recordingState.value = state
        Log.d(TAG, "状态更新: $state")
    }
}
