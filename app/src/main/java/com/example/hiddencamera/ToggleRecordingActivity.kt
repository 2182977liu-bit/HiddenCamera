package com.example.hiddencamera

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 透明中转 Activity，用于处理桌面快捷方式的录制切换
 * 不显示任何 UI，直接启动/停止录制后立即关闭
 */
class ToggleRecordingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_TOGGLE
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        finish()
    }
}
