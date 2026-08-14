package com.example.hiddencamera

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
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

        val switchBeauty = findViewById<android.widget.Switch>(R.id.switchBeauty)

        val seekSkinSmooth = findViewById<SeekBar>(R.id.seekSkinSmooth)
        val seekSkinWhiten = findViewById<SeekBar>(R.id.seekSkinWhiten)
        val seekSkinRosy = findViewById<SeekBar>(R.id.seekSkinRosy)
        val seekFaceSlim = findViewById<SeekBar>(R.id.seekFaceSlim)
        val seekEyeEnlarge = findViewById<SeekBar>(R.id.seekEyeEnlarge)
        val seekBodySlim = findViewById<SeekBar>(R.id.seekBodySlim)
        val seekLegLength = findViewById<SeekBar>(R.id.seekLegLength)
        val seekWaistSlim = findViewById<SeekBar>(R.id.seekWaistSlim)

        val tvSkinSmooth = findViewById<TextView>(R.id.tvSkinSmooth)
        val tvSkinWhiten = findViewById<TextView>(R.id.tvSkinWhiten)
        val tvSkinRosy = findViewById<TextView>(R.id.tvSkinRosy)
        val tvFaceSlim = findViewById<TextView>(R.id.tvFaceSlim)
        val tvEyeEnlarge = findViewById<TextView>(R.id.tvEyeEnlarge)
        val tvBodySlim = findViewById<TextView>(R.id.tvBodySlim)
        val tvLegLength = findViewById<TextView>(R.id.tvLegLength)
        val tvWaistSlim = findViewById<TextView>(R.id.tvWaistSlim)

        // 摄像头选择
        val currentLens = Prefs.getCameraLens(this)
        if (currentLens == "front") rgCamera.check(R.id.rbFront)
        else rgCamera.check(R.id.rbBack)

        // 分辨率
        val resolutions = arrayOf(
            getString(R.string.resolution_1080p),
            getString(R.string.resolution_720p),
            getString(R.string.resolution_480p)
        )
        val resAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resolutions)
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerResolution.adapter = resAdapter
        spinnerResolution.setSelection(Prefs.getResolution(this))

        // 帧率
        val fpsOptions = arrayOf(
            getString(R.string.fps_auto),
            getString(R.string.fps_30),
            getString(R.string.fps_60),
            getString(R.string.fps_120)
        )
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

        switchNotificationAction.isChecked = Prefs.isNotificationActionEnabled(this)
        switchShortcut.isChecked = Prefs.isShortcutEnabled(this)
        switchBeauty.isChecked = Prefs.isBeautyEnabled(this)

        // 美颜 SeekBar 初始化
        initSeekBar(seekSkinSmooth, tvSkinSmooth,
            { Prefs.getSkinSmooth(this) },
            { Prefs.setSkinSmooth(this, it) })

        initSeekBar(seekSkinWhiten, tvSkinWhiten,
            { Prefs.getSkinWhiten(this) },
            { Prefs.setSkinWhiten(this, it) })

        initSeekBar(seekSkinRosy, tvSkinRosy,
            { Prefs.getSkinRosy(this) },
            { Prefs.setSkinRosy(this, it) })

        initSeekBar(seekFaceSlim, tvFaceSlim,
            { Prefs.getFaceSlim(this) },
            { Prefs.setFaceSlim(this, it) })

        initSeekBar(seekEyeEnlarge, tvEyeEnlarge,
            { Prefs.getEyeEnlarge(this) },
            { Prefs.setEyeEnlarge(this, it) })

        initSeekBar(seekBodySlim, tvBodySlim,
            { Prefs.getBodySlim(this) },
            { Prefs.setBodySlim(this, it) })

        initSeekBar(seekLegLength, tvLegLength,
            { Prefs.getLegLength(this) },
            { Prefs.setLegLength(this, it) })

        initSeekBar(seekWaistSlim, tvWaistSlim,
            { Prefs.getWaistSlim(this) },
            { Prefs.setWaistSlim(this, it) })

        // 监听器
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

        switchBeauty.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setBeautyEnabled(this, isChecked)
        }

        btnCreateShortcut.setOnClickListener {
            createPinnedShortcut()
        }
    }

    private fun initSeekBar(
        seek: SeekBar,
        tv: TextView,
        getter: () -> Int,
        setter: (Int) -> Unit
    ) {
        val value = getter()
        seek.progress = value
        tv.text = value.toString()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = p.toString()
                if (fromUser) setter(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun createPinnedShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(android.content.pm.ShortcutManager::class.java)
            if (shortcutManager.isRequestPinShortcutSupported) {
                val shortcutIntent = Intent(this, ToggleRecordingActivity::class.java).apply {
                    action = Constants.ACTION_QUICK_TOGGLE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val shortcut = android.content.pm.ShortcutInfo.Builder(this, "quick_record")
                    .setShortLabel(getString(R.string.quick_record_started))
                    .setLongLabel(getString(R.string.shortcut_enabled_desc))
                    .setIcon(
                        android.graphics.drawable.Icon.createWithResource(
                            this, android.R.drawable.ic_menu_preferences
                        )
                    )
                    .setIntent(shortcutIntent)
                    .build()

                shortcutManager.requestPinShortcut(shortcut, null)
                Toast.makeText(this, R.string.shortcut_created, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.android_version_too_low, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
