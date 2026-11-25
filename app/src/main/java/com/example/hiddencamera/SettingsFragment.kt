package com.example.hiddencamera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.Fragment
import com.example.hiddencamera.databinding.FragmentSettingsBinding
import com.google.android.material.slider.Slider

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
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
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    override fun onResume() {
        super.onResume()
        setupPermissionBadges()
    }

    /** 切回本 Tab 时同步状态（录制页可能改过美颜开关） */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            setupPermissionBadges()
            val ctx = requireContext()
            binding.switchBeauty.isChecked = Prefs.isBeautyEnabled(ctx)
            binding.switchCallStop.isChecked = Prefs.isCallStopEnabled(ctx)
        }
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

        // 与 RecordingService 映射一致：0=1080p 1=720p 2=480p
        val resChips = listOf(binding.chipRes0, binding.chipRes1, binding.chipRes2)
        resChips.forEachIndexed { index, chip ->
            setupSelectChip(chip, res == index)
            chip.setOnClickListener {
                Prefs.setResolution(ctx, index)
                resChips.forEachIndexed { i, c -> setupSelectChip(c, i == index) }
            }
        }

        // 0=自动（不锁定帧率） 30 / 60
        val fpsChips = listOf(binding.chipFps0, binding.chipFps1, binding.chipFps2)
        val fpsValues = listOf(0, 30, 60)
        var fpsIndex = fpsValues.indexOf(fps)
        if (fpsIndex < 0) {
            // 旧版本可能存过 24 等值，归一到 30
            Prefs.setFps(ctx, 30)
            fpsIndex = 1
        }
        fpsChips.forEachIndexed { index, chip ->
            setupSelectChip(chip, index == fpsIndex)
            chip.setOnClickListener {
                Prefs.setFps(ctx, fpsValues[index])
                fpsChips.forEachIndexed { i, c -> setupSelectChip(c, i == index) }
            }
        }
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
    }

    // ===== 其它开关 =====
    private fun setupMiscSwitches() {
        val ctx = requireContext()

        // 通知栏停止按钮始终显示（安全考虑），开关仅做说明
        binding.switchNotificationAction.isChecked = true
        binding.switchNotificationAction.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                binding.switchNotificationAction.isChecked = true
                Toast.makeText(ctx, R.string.notification_action_always, Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchShortcut.isChecked = Prefs.isShortcutEnabled(ctx)
        binding.switchShortcut.setOnCheckedChangeListener { _, checked ->
            Prefs.setShortcutEnabled(ctx, checked)
        }
        binding.btnCreateShortcut.setOnClickListener { requestPinShortcut() }

        binding.switchCallStop.isChecked = Prefs.isCallStopEnabled(ctx)
        binding.switchCallStop.setOnCheckedChangeListener { _, checked ->
            Prefs.setCallStopEnabled(ctx, checked)
        }
    }

    /** 真正创建桌面固定快捷方式（旧实现只有 Toast） */
    private fun requestPinShortcut() {
        val ctx = requireContext()
        val sm = ctx.getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            Toast.makeText(ctx, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(ctx, ToggleRecordingActivity::class.java).apply {
                action = Constants.ACTION_QUICK_TOGGLE
            }
            val info = ShortcutInfo.Builder(ctx, "quick_record")
                .setShortLabel(getString(R.string.one_tap_record))
                .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                .setIntent(intent)
                .build()
            sm.requestPinShortcut(info, null)
            Toast.makeText(ctx, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(ctx, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
        }
    }

    // ===== 保存位置 =====
    private fun setupSaveLocation() {
        // 显示真实输出目录（旧实现写死 HiddenCamera，与实际不符）
        binding.tvSaveLocation.text =
            getString(R.string.storage_path, "Download/${Constants.OUTPUT_DIR_NAME}")
    }

    // ===== 权限状态 =====
    private fun setupPermissionBadges() {
        val ctx = requireContext()
        renderPermissionBadge(binding.tvPermCamera, hasPermission(ctx, Manifest.permission.CAMERA))
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
}
