package com.example.hiddencamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.hiddencamera.databinding.FragmentSettingsBinding
import com.google.android.material.slider.Slider

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBeautySection()
        setupCameraSection()
        setupQualitySection()
        setupPreviewSection()
        setupMiscSwitches()
        setupSaveLocation()
        setupPermissionBadges()
    }

    override fun onResume() {
        super.onResume()
        setupPermissionBadges()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ===== 美颜美体 =====
    private fun setupBeautySection() {
        val ctx = requireContext()

        binding.switchBeauty.isChecked = Prefs.isBeautyEnabled(ctx)
        binding.switchBeauty.setOnCheckedChangeListener { _, checked ->
            Prefs.setBeautyEnabled(ctx, checked)
        }

        bindSlider(binding.seekSkinSmooth, binding.tvSkinSmooth, Prefs.getSkinSmooth(ctx), "%d") {
            Prefs.setSkinSmooth(ctx, it.toInt())
        }
        bindSlider(binding.seekSkinWhiten, binding.tvSkinWhiten, Prefs.getSkinWhiten(ctx), "%d") {
            Prefs.setSkinWhiten(ctx, it.toInt())
        }
        bindSlider(binding.seekSkinRosy, binding.tvSkinRosy, Prefs.getSkinRosy(ctx), "%d") {
            Prefs.setSkinRosy(ctx, it.toInt())
        }
        bindSlider(binding.seekFaceSlim, binding.tvFaceSlim, Prefs.getFaceSlim(ctx), "%d") {
            Prefs.setFaceSlim(ctx, it.toInt())
        }
        bindSlider(binding.seekEyeEnlarge, binding.tvEyeEnlarge, Prefs.getEyeEnlarge(ctx), "%d") {
            Prefs.setEyeEnlarge(ctx, it.toInt())
        }

        binding.switchBodyBeauty.isChecked = Prefs.getBodySlim(ctx) > 0
        binding.switchBodyBeauty.setOnCheckedChangeListener { _, checked ->
            Prefs.setBodySlim(ctx, if (checked) 50 else 0)
        }
    }

    private fun bindSlider(
        slider: Slider,
        valueView: TextView,
        defaultValue: Int,
        format: String,
        onChanged: (Float) -> Unit
    ) {
        slider.value = defaultValue.toFloat()
        valueView.text = String.format(format, defaultValue)
        slider.addOnChangeListener { _, value, _ ->
            valueView.text = String.format(format, value.toInt())
            onChanged(value)
        }
    }

    // ===== 摄像头设置 =====
    private fun setupCameraSection() {
        val ctx = requireContext()
        val lens = Prefs.getCameraLens(ctx)
        setupSelectChip(binding.chipCameraBack, lens == "back")
        setupSelectChip(binding.chipCameraFront, lens == "front")

        binding.chipCameraBack.setOnClickListener {
            Prefs.setCameraLens(ctx, "back")
            setupSelectChip(binding.chipCameraBack, true)
            setupSelectChip(binding.chipCameraFront, false)
        }
        binding.chipCameraFront.setOnClickListener {
            Prefs.setCameraLens(ctx, "front")
            setupSelectChip(binding.chipCameraBack, false)
            setupSelectChip(binding.chipCameraFront, true)
        }

        binding.switchMirror.isChecked = false
        binding.switchMirror.setOnCheckedChangeListener { _, _ ->
            Toast.makeText(ctx, R.string.coming_soon, Toast.LENGTH_SHORT).show()
        }
        bindSlider(binding.seekExposure, binding.tvExposure, 0, "%+d") {
            // 曝光补偿仅作展示，实际范围 -5..+5
        }
    }

    private fun setupSelectChip(chip: TextView, active: Boolean) {
        chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip)
        chip.setTextColor(
            ContextCompat.getColor(requireContext(), if (active) R.color.brand_ink else R.color.ink_2)
        )
    }

    // ===== 录制质量 =====
    private fun setupQualitySection() {
        val ctx = requireContext()
        val res = Prefs.getResolution(ctx)
        val fps = Prefs.getFps(ctx)

        val resChips = listOf(binding.chipRes0, binding.chipRes1, binding.chipRes2)
        resChips.forEachIndexed { index, chip ->
            setupSelectChip(chip, res == index)
            chip.setOnClickListener {
                Prefs.setResolution(ctx, index)
                resChips.forEachIndexed { i, c -> setupSelectChip(c, i == index) }
            }
        }

        val fpsChips = listOf(binding.chipFps0, binding.chipFps1, binding.chipFps2)
        val fpsValues = listOf(24, 30, 60)
        val fpsIndex = fpsValues.indexOf(fps).coerceAtLeast(0)
        fpsChips.forEachIndexed { index, chip ->
            setupSelectChip(chip, index == fpsIndex)
            chip.setOnClickListener {
                Prefs.setFps(ctx, fpsValues[index])
                fpsChips.forEachIndexed { i, c -> setupSelectChip(c, i == index) }
            }
        }

        binding.switchSegment.isChecked = false
        binding.tvSegmentDuration.text = getString(R.string.every_30min)
    }

    // ===== 预览模式 =====
    private fun setupPreviewSection() {
        val ctx = requireContext()
        val mode = Prefs.getPreviewMode(ctx)

        binding.switchPreviewLive.isChecked = mode == 0
        binding.switchPreviewStatus.isChecked = mode == 1
        binding.switchPreviewBlank.isChecked = mode == 2

        binding.switchPreviewLive.setOnCheckedChangeListener { _, checked ->
            if (checked) setPreviewMode(0)
        }
        binding.switchPreviewStatus.setOnCheckedChangeListener { _, checked ->
            if (checked) setPreviewMode(1)
        }
        binding.switchPreviewBlank.setOnCheckedChangeListener { _, checked ->
            if (checked) setPreviewMode(2)
        }
    }

    private fun setPreviewMode(mode: Int) {
        Prefs.setPreviewMode(requireContext(), mode)
        binding.switchPreviewLive.isChecked = mode == 0
        binding.switchPreviewStatus.isChecked = mode == 1
        binding.switchPreviewBlank.isChecked = mode == 2
        Toast.makeText(requireContext(), getString(R.string.settings_preview), Toast.LENGTH_SHORT).show()
    }

    // ===== 其它开关 =====
    private fun setupMiscSwitches() {
        val ctx = requireContext()

        binding.switchNoiseReduce.isChecked = true
        binding.switchNoiseReduce.setOnCheckedChangeListener { _, _ ->
            Toast.makeText(ctx, R.string.coming_soon, Toast.LENGTH_SHORT).show()
        }
        bindSlider(binding.seekVolume, binding.tvVolume, 80, "%d") {
            // 音量仅作展示
        }

        binding.switchNotificationAction.isChecked = Prefs.isNotificationActionEnabled(ctx)
        binding.switchNotificationAction.setOnCheckedChangeListener { _, checked ->
            Prefs.setNotificationActionEnabled(ctx, checked)
        }

        binding.switchShortcut.isChecked = Prefs.isShortcutEnabled(ctx)
        binding.switchShortcut.setOnCheckedChangeListener { _, checked ->
            Prefs.setShortcutEnabled(ctx, checked)
        }
        binding.btnCreateShortcut.setOnClickListener {
            Toast.makeText(ctx, R.string.create_shortcut, Toast.LENGTH_SHORT).show()
        }

        binding.switchFloating.isChecked = false
        binding.switchFloating.setOnCheckedChangeListener { _, _ ->
            Toast.makeText(ctx, R.string.coming_soon, Toast.LENGTH_SHORT).show()
        }
        bindSlider(binding.seekFloatOpacity, binding.tvFloatOpacity, 60, "%d%%") {
            // 透明度仅作展示
        }

        binding.switchCallStop.isChecked = true
        binding.switchCallStop.setOnCheckedChangeListener { _, _ ->
            Toast.makeText(ctx, R.string.coming_soon, Toast.LENGTH_SHORT).show()
        }
    }

    // ===== 保存位置 =====
    private fun setupSaveLocation() {
        binding.tvSaveLocation.text = getString(R.string.storage_path, "HiddenCamera")
    }

    // ===== 权限状态 =====
    private fun setupPermissionBadges() {
        val ctx = requireContext()
        renderPermissionBadge(binding.tvPermMic, hasPermission(ctx, Manifest.permission.RECORD_AUDIO))
        renderPermissionBadge(binding.tvPermStorage, hasStoragePermission(ctx))
    }

    private fun renderPermissionBadge(view: TextView, granted: Boolean) {
        view.text = getString(if (granted) R.string.granted else R.string.not_granted)
        view.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (granted) R.color.state_success else R.color.state_error
            )
        )
        view.setBackgroundResource(
            if (granted) R.drawable.bg_status_success else R.drawable.bg_status_warning
        )
    }

    private fun hasPermission(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasStoragePermission(ctx: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            hasPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun toggleSwitch(switch: SwitchCompat, value: Boolean) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = value
    }
}