package com.example.hiddencamera

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rgCamera = findViewById<android.widget.RadioGroup>(R.id.rgCamera)
        val spinnerResolution = findViewById<android.widget.Spinner>(R.id.spinnerResolution)
        val spinnerFps = findViewById<android.widget.Spinner>(R.id.spinnerFps)
        val rgPreviewMode = findViewById<android.widget.RadioGroup>(R.id.rgPreviewMode)
        val switchNotificationAction = findViewById<android.widget.Switch>(R.id.switchNotificationAction)
        val switchShortcut = findViewById<android.widget.Switch>(R.id.switchShortcut)
        val btnCreateShortcut = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreateShortcut)

        // 摄像头
        val currentLens = Prefs.getCameraLens(this)
        if (currentLens == "front") rgCamera.check(R.id.rbFront)
        else rgCamera.check(R.id.rbBack)

        // 分辨率
        val resolutions = arrayOf("1080p (1920x1080)", "720p (1280x720)", "480p (720x480)")
        val resAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resolutions)
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerResolution.adapter = resAdapter
        spinnerResolution.setSelection(Prefs.getResolution(this))

        // 帧率
        val fpsOptions = arrayOf("自动", "30 FPS", "60 FPS", "120 FPS")
        val fpsValues = intArrayOf(0, 30, 60, 120)
        val fpsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fpsOptions)
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFps.adapter = fpsAdapter
        val currentFps = Prefs.getFps(this)
        val fpsIndex = fpsValues.indexOf(currentFps).let { if (it < 0) 0 else it }
        spinnerFps.setSelection(fpsIndex)

        // 预览模式
        when (Prefs.getPreviewMode(this)) {
            0 -> rgPreviewMode.check(R.id.rbPreviewLive)
            1 -> rgPreviewMode.check(R.id.rbPreviewStatus)
            2 -> rgPreviewMode.check(R.id.rbPreviewBlank)
        }

        // 通知栏快捷按钮
        switchNotificationAction.isChecked = Prefs.isNotificationActionEnabled(this)

        // 桌面快捷方式
        switchShortcut.isChecked = Prefs.isShortcutEnabled(this)

        // 监听
        rgCamera.setOnCheckedChangeListener { _, checkedId ->
            val lens = if (checkedId == R.id.rbFront) "front" else "back"
            Prefs.setCameraLens(this, lens)
        }

        spinnerResolution.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                Prefs.setResolution(this@SettingsActivity, position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerFps.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                Prefs.setFps(this@SettingsActivity, fpsValues[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        rgPreviewMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbPreviewStatus -> 1
                R.id.rbPreviewBlank -> 2
                else -> 0
            }
            Prefs.setPreviewMode(this, mode)
        }

        switchNotificationAction.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setNotificationActionEnabled(this, isChecked)
        }

        switchShortcut.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setShortcutEnabled(this, isChecked)
        }

        btnCreateShortcut.setOnClickListener {
            createPinnedShortcut()
        }
    }

    private fun createPinnedShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(android.content.pm.ShortcutManager::class.java)
            if (shortcutManager.isRequestPinShortcutSupported) {
                val toggleIntent = Intent(this, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_TOGGLE
                }
                val pendingIntent = PendingIntent.getService(
                    this, 1, toggleIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val shortcut = android.content.pm.ShortcutInfo.Builder(this, "quick_record")
                    .setShortLabel("快速录制")
                    .setLongLabel("点击开始/停止录制")
                    .setIcon(
                        android.graphics.drawable.Icon.createWithResource(
                            this, android.R.drawable.ic_menu_camera
                        )
                    )
                    .setIntent(pendingIntent)
                    .build()

                shortcutManager.requestPinShortcut(shortcut, null)
                Toast.makeText(this, R.string.shortcut_created, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "当前启动器不支持创建快捷方式", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "需要 Android 8.0 及以上版本", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
