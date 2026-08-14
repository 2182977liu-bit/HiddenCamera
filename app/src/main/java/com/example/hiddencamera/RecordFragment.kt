package com.example.hiddencamera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hiddencamera.databinding.FragmentRecordBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class RecordFragment : Fragment() {

    private var _binding: FragmentRecordBinding? = null
    private val binding get() = _binding!!

    private val mainActivity get() = activity as? MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToggleChips()
        setupButtons()
        applyPreviewMode()
        connectPreview()
        observeUiState()
        startDurationTicker()
    }

    override fun onResume() {
        super.onResume()
        connectPreview()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 把预览表面交给服务 */
    fun connectPreview() {
        if (Prefs.getPreviewMode(requireContext()) != 0) return
        mainActivity?.connectPreviewFromFragment(binding.previewView.surfaceProvider)
    }

    private fun applyPreviewMode() {
        val mode = Prefs.getPreviewMode(requireContext())
        binding.previewView.visibility = if (mode == 0) View.VISIBLE else View.GONE
        binding.statusOverlay.visibility = if (mode == 1) View.VISIBLE else View.GONE
        binding.blankOverlay.visibility = if (mode == 2) View.VISIBLE else View.GONE
    }

    private fun setupButtons() {
        binding.btnRecord.setOnClickListener {
            val activity = mainActivity ?: return@setOnClickListener
            if (activity.currentIsRecording() || activity.currentIsStopping()) {
                activity.stopRecording()
            } else {
                activity.startRecording()
            }
        }

        binding.btnSwitchCamera.setOnClickListener {
            val ctx = requireContext()
            val current = Prefs.getCameraLens(ctx)
            val next = if (current == "front") "back" else "front"
            Prefs.setCameraLens(ctx, next)
            // 若正在录制，重启服务以应用新摄像头
            if (mainActivity?.currentIsRecording() == true) {
                mainActivity?.stopRecording()
                binding.previewView.postDelayed({
                    mainActivity?.startRecording()
                }, 800)
            }
        }

        binding.btnBeauty.setOnClickListener {
            val ctx = requireContext()
            val enabled = !Prefs.isBeautyEnabled(ctx)
            Prefs.setBeautyEnabled(ctx, enabled)
            binding.btnBeauty.setBackgroundResource(
                if (enabled) R.drawable.bg_chip_active else R.drawable.bg_chip
            )
            binding.btnBeauty.setTextColor(
                if (enabled) ContextCompat.getColor(ctx, R.color.brand_ink)
                else ContextCompat.getColor(ctx, R.color.ink_2)
            )
        }

        binding.btnFlash.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "闪光灯功能暂不可用", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.btnPause.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), R.string.pause_resume, android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.btnScreenshot.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), R.string.screenshot, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupToggleChips() {
        val chips = listOf(
            binding.toggleFloat,
            binding.toggleAudio,
            binding.toggleBeauty,
            binding.toggleTimer,
            binding.toggleTouch
        )
        chips.forEach { chip ->
            chip.setOnClickListener { toggleChip(chip) }
            chip.isClickable = true
        }
        // 默认激活：悬浮窗 / 内录音频 / 美颜
        setChipActive(binding.toggleFloat, true)
        setChipActive(binding.toggleAudio, true)
        setChipActive(binding.toggleBeauty, Prefs.isBeautyEnabled(requireContext()))
    }

    private fun toggleChip(chip: View) {
        val active = chip.tag as? Boolean ?: false
        setChipActive(chip, !active)
    }

    private fun setChipActive(chip: View, active: Boolean) {
        chip.tag = active
        chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip)
        (chip as android.widget.TextView).setTextColor(
            ContextCompat.getColor(requireContext(), if (active) R.color.brand_ink else R.color.ink_2)
        )
    }

    private fun observeUiState() {
        val activity = mainActivity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activity.viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: MainUiState) {
        val ctx = requireContext()
        val recording = state.isRecording
        val stopping = state.isStopping

        // 录制按钮
        binding.btnRecord.setBackgroundResource(
            if (recording) R.drawable.bg_record_button else R.drawable.bg_record_button
        )
        binding.recordPulseRing.visibility = if (recording) View.VISIBLE else View.GONE
        if (recording && binding.recordPulseRing.animation == null) {
            binding.recordPulseRing.startAnimation(
                AnimationUtils.loadAnimation(ctx, R.anim.record_pulse_ring)
            )
        }
        binding.btnRecord.setImageResource(if (recording) R.drawable.ic_pause else R.drawable.ic_record_square)
        binding.tvTapHint.text = getString(
            if (recording) R.string.tap_to_stop else R.string.tap_to_start
        )

        // 状态徽标
        binding.recBadge.visibility = if (recording) View.VISIBLE else View.GONE
        if (recording && binding.recDot.animation == null) {
            binding.recDot.startAnimation(
                AnimationUtils.loadAnimation(ctx, R.anim.rec_dot_pulse)
            )
        }
        binding.recBadgeText.text = when {
            recording -> getString(R.string.recording)
            stopping -> getString(R.string.stopping)
            else -> getString(R.string.idle)
        }
        binding.tvStatus.text = when {
            recording -> getString(R.string.recording)
            stopping -> getString(R.string.stopping)
            else -> getString(R.string.idle)
        }
        if (!recording) {
            binding.tvDuration.text = "00:00"
            binding.tvFileSize.text = "0 MB"
        }
    }

    private fun startDurationTicker() {
        val activity = mainActivity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val state = activity.viewModel.uiState.value
                    if (state.isRecording) {
                        val duration = activity.viewModel.getCurrentDuration(System.currentTimeMillis())
                        binding.tvDuration.text = formatDuration(duration)
                    }
                    delay(500)
                }
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }
}