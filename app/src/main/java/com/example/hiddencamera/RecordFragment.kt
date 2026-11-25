package com.example.hiddencamera

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hiddencamera.databinding.FragmentRecordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordFragment : Fragment() {

    private var _binding: FragmentRecordBinding? = null
    private val binding get() = _binding!!

    private val mainActivity get() = activity as? MainActivity

    /** 用于检测「录制刚结束」以刷新统计 */
    private var wasRecording = false

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
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        connectPreview()
        refreshStats()
    }

    /** hide/show 切换 Tab 不会走 onResume，必须在这里刷新，否则预览模式/美颜状态不生效 */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            applyPreviewMode()
            connectPreview()
            refreshBeautyUi()
            refreshStats()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 把预览表面交给服务 */
    fun connectPreview() {
        if (_binding == null) return
        if (Prefs.getPreviewMode(requireContext()) != 0) {
            mainActivity?.connectPreviewFromFragment(null)
            return
        }
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
            // 由服务在 Finalize 后自动重启，不再用固定延时（旧实现会损坏视频）
            if (mainActivity?.currentIsRecording() == true) {
                mainActivity?.switchCamera()
                Toast.makeText(ctx, R.string.switching_camera, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBeauty.setOnClickListener {
            val ctx = requireContext()
            Prefs.setBeautyEnabled(ctx, !Prefs.isBeautyEnabled(ctx))
            refreshBeautyUi()
        }

        binding.btnFlash.setOnClickListener {
            val activity = mainActivity ?: return@setOnClickListener
            val ok = activity.toggleTorch()
            val msg = when {
                !ok -> R.string.torch_unsupported
                activity.isTorchOn() -> R.string.torch_on
                else -> R.string.torch_off
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnPause.setOnClickListener {
            val activity = mainActivity ?: return@setOnClickListener
            if (!activity.currentIsRecording()) {
                Toast.makeText(requireContext(), R.string.pause_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (activity.togglePause()) {
                binding.btnPause.setImageResource(
                    if (activity.isPaused()) R.drawable.ic_play else R.drawable.ic_pause
                )
                Toast.makeText(
                    requireContext(),
                    if (activity.isPaused()) R.string.paused else R.string.resumed,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(requireContext(), R.string.pause_failed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnScreenshot.setOnClickListener { takeScreenshot() }
    }

    /** 截取当前预览画面保存为 JPG（与视频同目录，文件页「截图」筛选可见） */
    private fun takeScreenshot() {
        val bitmap = binding.previewView.bitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                val dir = RecordingService.getOutputDir()
                if (!dir.exists()) dir.mkdirs()
                val ts = SimpleDateFormat(Constants.TIMESTAMP_FORMAT, Locale.getDefault()).format(Date())
                val file = File(dir, "${Constants.IMAGE_FILE_PREFIX}$ts.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                true
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                Toast.makeText(
                    requireContext(),
                    if (ok) R.string.screenshot_saved else R.string.screenshot_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupToggleChips() {
        // 只保留真实生效的开关：录音 / 美颜
        binding.toggleAudio.setOnClickListener {
            val ctx = requireContext()
            val enabled = !Prefs.isAudioEnabled(ctx)
            Prefs.setAudioEnabled(ctx, enabled)
            setChipActive(binding.toggleAudio, enabled)
        }
        binding.toggleBeauty.setOnClickListener {
            val ctx = requireContext()
            Prefs.setBeautyEnabled(ctx, !Prefs.isBeautyEnabled(ctx))
            refreshBeautyUi()
        }
        setChipActive(binding.toggleAudio, Prefs.isAudioEnabled(requireContext()))
        refreshBeautyUi()
    }

    /** 美颜入口与快捷 chip 共用一个配置，保持同步 */
    private fun refreshBeautyUi() {
        val ctx = requireContext()
        val enabled = Prefs.isBeautyEnabled(ctx)
        binding.btnBeauty.setBackgroundResource(
            if (enabled) R.drawable.bg_chip_active else R.drawable.bg_chip
        )
        binding.btnBeautyText.setTextColor(
            ContextCompat.getColor(ctx, if (enabled) R.color.brand_ink else R.color.ink_2)
        )
        setChipActive(binding.toggleBeauty, enabled)
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

        // 录制按钮：待机显示圆形录制点，录制中显示方形停止
        binding.btnRecord.setBackgroundResource(R.drawable.bg_record_button)
        binding.recordPulseRing.visibility = if (recording) View.VISIBLE else View.GONE
        if (recording && binding.recordPulseRing.animation == null) {
            binding.recordPulseRing.startAnimation(
                AnimationUtils.loadAnimation(ctx, R.anim.record_pulse_ring)
            )
        }
        binding.btnRecord.setImageResource(
            if (recording) R.drawable.ic_square else R.drawable.ic_record_circle
        )
        binding.tvTapHint.text = getString(
            if (recording) R.string.tap_to_stop else R.string.tap_to_start
        )

        // 顶部状态点
        binding.recDot.setBackgroundResource(
            if (recording) R.drawable.indicator_recording else R.drawable.indicator_idle
        )

        // 状态徽标
        binding.recBadge.visibility = if (recording || stopping) View.VISIBLE else View.GONE
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
            binding.btnPause.setImageResource(R.drawable.ic_pause)
        }

        // 录制刚结束 → 刷新统计卡片
        if (wasRecording && !recording && !stopping) {
            refreshStats()
        }
        wasRecording = recording
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
                        // 实时文件大小（旧实现永远显示 0 MB）
                        val size = activity.currentRecordingFile()?.length() ?: 0L
                        binding.tvFileSize.text = String.format(Locale.US, "%.1f MB", size / 1048576f)
                    }
                    delay(500)
                }
            }
        }
    }

    /** 统计卡片：录制次数 / 总时长 / 占用 / 剩余空间（旧实现永远为 0） */
    private fun refreshStats() {
        if (_binding == null) return
        val ctx = requireContext()
        val resolution = Prefs.getResolution(ctx)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var videoCount = 0
            var totalDurationMs = 0L
            var totalBytes = 0L
            var freeBytes = 0L
            try {
                val dir = RecordingService.getOutputDir()
                val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
                for (f in files) {
                    val name = f.name.lowercase(Locale.US)
                    totalBytes += f.length()
                    if (name.endsWith(".mp4")) {
                        videoCount++
                        totalDurationMs += videoDurationMs(f)
                    }
                }
                val stat = StatFs(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ).absolutePath
                )
                freeBytes = stat.availableBytes
            } catch (_: Exception) {}

            // 按当前分辨率的近似码率估算可录时长
            val bitrate = when (resolution) {
                0 -> 10_000_000L
                1 -> 5_000_000L
                else -> 2_000_000L
            }
            val estimateSec = freeBytes * 8 / bitrate
            val estimateText = if (estimateSec >= 3600) {
                String.format(Locale.US, "%.1f 小时", estimateSec / 3600f)
            } else {
                "${estimateSec / 60} 分钟"
            }
            val freeText = String.format(Locale.US, "%.1f GB", freeBytes / 1e9)
            val countText = "$videoCount 次"
            val durationText = String.format(Locale.US, "%.1f 小时", totalDurationMs / 3.6e6)
            val storageText = String.format(Locale.US, "%.1f GB", totalBytes / 1e9)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.tvStatCount.text = countText
                binding.tvStatDuration.text = durationText
                binding.tvStatStorage.text = storageText
                binding.tvRemaining.text = getString(R.string.remaining_space, freeText, estimateText)
            }
        }
    }

    private fun videoDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
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
