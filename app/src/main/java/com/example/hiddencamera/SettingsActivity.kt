package com.example.hiddencamera

import android.os.Bundle
import android.widget.ArrayAdapter
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

        // 摄像头选择
        val currentLens = Prefs.getCameraLens(this)
        if (currentLens == "front") {
            rgCamera.check(R.id.rbFront)
        } else {
            rgCamera.check(R.id.rbBack)
        }

        // 分辨率选择
        val resolutions = arrayOf("1080p (1920x1080)", "720p (1280x720)", "480p (720x480)")
        val resAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resolutions)
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerResolution.adapter = resAdapter
        spinnerResolution.setSelection(Prefs.getResolution(this))

        // 帧率选择
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
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
