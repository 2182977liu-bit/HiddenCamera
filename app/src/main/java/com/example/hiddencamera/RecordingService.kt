package com.example.hiddencamera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RecordingService : LifecycleService() {

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_OUTPUT_PATH = "output_path"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecording = false
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                startForegroundService()
                startRecording(outputPath)
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        cameraExecutor.shutdown()
    }

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

    private fun startForegroundService() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startRecording(outputPath: String?) {
        val storagePath = outputPath ?: Prefs.getStoragePath(this)
        val outputDir = File(storagePath)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // 创建 .nomedia 文件，防止相册扫描
        val nomediaFile = File(outputDir, ".nomedia")
        if (!nomediaFile.exists()) {
            nomediaFile.createNewFile()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputFile = File(outputDir, "VID_$timestamp.mp4")

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCameraUseCases(outputFile)
        }, cameraExecutor)
    }

    private fun bindCameraUseCases(outputFile: File) {
        val provider = cameraProvider ?: return

        // 解绑所有用例
        provider.unbindAll()

        // 选择摄像头
        val lensFacing = if (Prefs.getCameraLens(this) == "front") {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // 设置分辨率
        val quality = when (Prefs.getResolution(this)) {
            0 -> Quality.FHD
            1 -> Quality.HD
            else -> Quality.SD
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        try {
            provider.bindToLifecycle(this, cameraSelector, videoCapture)
            startVideoCapture(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "绑定相机失败", e)
        }
    }

    private fun startVideoCapture(outputFile: File) {
        val capture = videoCapture ?: return

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    withAudioEnabled()
                }
            }
            .start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        Log.d(TAG, "录制开始: ${outputFile.absolutePath}")
                        sendBroadcast(Intent("com.example.hiddencamera.RECORDING_STARTED"))
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        if (event.hasError()) {
                            Log.e(TAG, "录制错误: ${event.cause}")
                        } else {
                            Log.d(TAG, "录制完成: ${outputFile.absolutePath}")
                        }
                        sendBroadcast(Intent("com.example.hiddencamera.RECORDING_STOPPED"))
                    }
                }
            }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        videoCapture = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        isRecording = false
    }
}
