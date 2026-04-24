package com.example.hiddencamera

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

        // 摄像头选择
        val currentLens = Prefs.getCameraLens(this)
        if (currentLens == "front") {
            rgCamera.check(R.id.rbFront)
        } else {
            rgCamera.check(R.id.rbBack)
        }

        // 分辨率选择
        val resolutions = arrayOf("1080p (1920x1080)", "720p (1280x720)", "480p (720x480)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resolutions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerResolution.adapter = adapter
        spinnerResolution.setSelection(Prefs.getResolution(this))

        // 摄像头选择监听
        rgCamera.setOnCheckedChangeListener { _, checkedId ->
            val lens = if (checkedId == R.id.rbFront) "front" else "back"
            Prefs.setCameraLens(this, lens)
        }

        // 分辨率选择监听
        spinnerResolution.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                Prefs.setResolution(this@SettingsActivity, position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
