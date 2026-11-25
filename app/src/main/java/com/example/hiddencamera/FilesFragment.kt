package com.example.hiddencamera

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hiddencamera.databinding.FragmentFilesBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileListAdapter
    private var allFiles: List<VideoFile> = emptyList()
    private var filter: Int = 0 // 0=全部 1=视频 2=截图
    private var sortBySize = false
    private var searchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        setupFilterChips()
        setupToolbarActions()
        setupSheetActions()
        loadFiles()
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }

    /** 切回本 Tab 时刷新（hide/show 不走 onResume） */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            loadFiles()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecycler() {
        adapter = FileListAdapter(
            onSelect = { file -> toggleSelection(file) },
            onLongClick = { file -> openSheet(file) }
        )
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = adapter
    }

    private fun setupFilterChips() {
        val chips = listOf(binding.chipFilterAll, binding.chipFilterVideo, binding.chipFilterScreenshot)
        chips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                filter = index
                chips.forEachIndexed { i, c -> setChipActive(c, i == index) }
                renderList()
            }
        }
        setChipActive(binding.chipFilterAll, true)
    }

    private fun setChipActive(chip: TextView, active: Boolean) {
        chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip)
        chip.setTextColor(
            ContextCompat.getColor(requireContext(), if (active) R.color.brand_ink else R.color.ink_2)
        )
    }

    private fun setupToolbarActions() {
        binding.btnSearch.setOnClickListener { showSearchDialog() }
        binding.btnSort.setOnClickListener {
            sortBySize = !sortBySize
            binding.btnSort.setText(if (sortBySize) R.string.sort_largest else R.string.sort_newest)
            renderList()
        }

        binding.btnSelectAll.setOnClickListener {
            val ids = currentItems().map { it.id }
            adapter.selectAll(ids)
            updateMultiBar()
        }
        binding.btnShare.setOnClickListener { shareFiles(getVisibleFilesForSelection()) }
        binding.btnDelete.setOnClickListener { deleteSelected(getVisibleFilesForSelection()) }
    }

    private fun showSearchDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = getString(R.string.search_hint)
            setText(searchQuery)
            setSingleLine()
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.search_title)
            .setView(input)
            .setPositiveButton(R.string.search) { _, _ ->
                searchQuery = input.text.toString().trim()
                renderList()
            }
            .setNeutralButton(R.string.search_clear) { _, _ ->
                searchQuery = ""
                renderList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupSheetActions() {
        binding.sheetScrim.setOnClickListener { hideSheet() }
        binding.sheetCancel.setOnClickListener { hideSheet() }
        binding.sheetPlay.setOnClickListener {
            sheetFile?.let { playFile(it) }
            hideSheet()
        }
        binding.sheetShare.setOnClickListener {
            val file = sheetFile
            if (file != null) {
                val selected = getVisibleFilesForSelection()
                shareFiles(if (selected.isNotEmpty()) selected else listOf(file))
            }
            hideSheet()
        }
        binding.sheetRename.setOnClickListener {
            sheetFile?.let { showRenameDialog(it) }
            hideSheet()
        }
        binding.sheetDetails.setOnClickListener {
            sheetFile?.let { showDetails(it) }
            hideSheet()
        }
        binding.sheetDelete.setOnClickListener {
            val file = sheetFile
            if (file != null) {
                deleteSelected(listOf(file))
            }
            hideSheet()
        }
    }

    private var sheetFile: VideoFile? = null

    private fun openSheet(file: VideoFile) {
        sheetFile = file
        binding.sheetFilename.text = file.name
        binding.sheetScrim.visibility = View.VISIBLE
        binding.bottomSheet.visibility = View.VISIBLE
    }

    private fun hideSheet() {
        binding.sheetScrim.visibility = View.GONE
        binding.bottomSheet.visibility = View.GONE
    }

    // ===== 多选逻辑 =====
    private fun toggleSelection(file: VideoFile) {
        adapter.setSelected(file.id, !adapter.isSelected(file.id))
        updateMultiBar()
    }

    private fun updateMultiBar() {
        val count = adapter.selectedCount()
        if (count > 0) {
            binding.multiToolbar.visibility = View.VISIBLE
            binding.tvMultiCount.text = getString(R.string.selected_count, count)
        } else {
            binding.multiToolbar.visibility = View.GONE
        }
    }

    private fun currentItems(): List<VideoFile> {
        var list = when (filter) {
            1 -> allFiles.filter { isVideoFile(it.name) }
            2 -> allFiles.filter { isImageFile(it.name) }
            else -> allFiles
        }
        if (searchQuery.isNotEmpty()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        return if (sortBySize) list.sortedByDescending { it.sizeBytes }
        else list.sortedByDescending { it.dateTaken }
    }

    private fun isVideoFile(name: String): Boolean {
        val n = name.lowercase(Locale.US)
        return n.endsWith(".mp4") || n.endsWith(".mkv") ||
            n.endsWith(".3gp") || n.endsWith(".webm")
    }

    private fun isImageFile(name: String): Boolean {
        val n = name.lowercase(Locale.US)
        return n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".png") || n.endsWith(".webp")
    }

    private fun getVisibleFilesForSelection(): List<VideoFile> =
        currentItems().filter { adapter.isSelected(it.id) }

    // ===== 播放 / 分享（FileProvider 授权，旧实现只有 Toast） =====

    private fun contentUri(file: File): Uri =
        FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)

    private fun playFile(vf: VideoFile) {
        try {
            val uri = contentUri(vf.file)
            val type = if (isImageFile(vf.name)) "image/*" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_player, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_player, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFiles(files: List<VideoFile>) {
        if (files.isEmpty()) return
        val ctx = requireContext()
        val uris = ArrayList<Uri>()
        for (vf in files) {
            try {
                uris.add(contentUri(vf.file))
            } catch (_: Exception) {}
        }
        if (uris.isEmpty()) {
            Toast.makeText(ctx, R.string.share_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val allImages = files.all { isImageFile(it.name) }
        val allVideos = files.all { isVideoFile(it.name) }
        val type = when {
            allImages -> "image/*"
            allVideos -> "video/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.type = type
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (_: Exception) {
            Toast.makeText(ctx, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ===== 重命名 / 详情 =====

    private fun showRenameDialog(vf: VideoFile) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setText(vf.name)
            setSingleLine()
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.rename_title)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName.contains('/') || newName.contains('\\')) {
                    Toast.makeText(ctx, R.string.rename_failed, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val target = File(vf.file.parentFile, newName)
                if (target.exists()) {
                    Toast.makeText(ctx, R.string.rename_exists, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val ok = try { vf.file.renameTo(target) } catch (_: Exception) { false }
                Toast.makeText(
                    ctx,
                    if (ok) R.string.rename_success else R.string.rename_failed,
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) loadFiles()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDetails(vf: VideoFile) {
        val ctx = requireContext()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(vf.dateTaken))
        val size = String.format(Locale.US, "%.1f MB", vf.sizeBytes / 1048576f)
        val duration = formatDuration(vf.durationMs)
        val resolution = if (vf.width > 0 && vf.height > 0) "${vf.width}x${vf.height}" else "-"
        val message = buildString {
            append("名称：${vf.name}\n")
            append("大小：$size\n")
            if (isVideoFile(vf.name)) {
                append("时长：$duration\n")
                append("分辨率：$resolution\n")
            }
            append("时间：$time\n")
            append("路径：${vf.file.absolutePath}")
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.details_title)
            .setMessage(message)
            .setPositiveButton(R.string.copy_path) { _, _ ->
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("path", vf.file.absolutePath))
                Toast.makeText(ctx, R.string.path_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ===== 删除 =====

    private fun deleteSelected(files: List<VideoFile>) {
        if (files.isEmpty()) return
        // 正在写入的文件不能删，否则视频损坏
        val activePath = (activity as? MainActivity)?.currentRecordingFile()?.absolutePath
        val deletable = files.filter { it.file.absolutePath != activePath }
        if (deletable.isEmpty()) {
            Toast.makeText(requireContext(), R.string.delete_skip_active, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_confirm, deletable.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                doDelete(deletable)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doDelete(files: List<VideoFile>) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            for (vf in files) {
                try {
                    vf.file.delete()
                } catch (_: Exception) {
                }
            }
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                adapter.clearSelection()
                updateMultiBar()
                loadFiles()
                Toast.makeText(ctx, R.string.delete_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== 目录扫描（直接扫文件系统，不受 .nomedia 影响） =====
    private fun loadFiles() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val files = scanMedia(ctx)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                allFiles = files
                renderList()
            }
        }
    }

    private fun scanMedia(ctx: Context): List<VideoFile> {
        val dir = RecordingService.getOutputDir()
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val files = dir.listFiles()?.filter {
            it.isFile && (isVideoFile(it.name) || isImageFile(it.name))
        } ?: return emptyList()
        val now = System.currentTimeMillis()

        return files.mapNotNull { f ->
            try {
                val dateTaken = f.lastModified().takeIf { it > 0 } ?: now
                var duration = 0L
                var width = 0
                var height = 0
                if (isVideoFile(f.name)) {
                    val meta = videoMeta(f)
                    duration = meta.first
                    width = meta.second
                    height = meta.third
                }
                VideoFile(
                    id = f.path.hashCode().toLong(),
                    uri = Uri.fromFile(f),
                    name = f.name,
                    sizeBytes = f.length(),
                    durationMs = duration,
                    dateTaken = dateTaken,
                    groupKey = groupKeyFor(dateTaken),
                    file = f,
                    width = width,
                    height = height
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.dateTaken }
    }

    /** 读取视频时长与分辨率 */
    private fun videoMeta(file: File): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Triple(ms, w, h)
        } catch (_: Exception) {
            Triple(0L, 0, 0)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun groupKeyFor(millis: Long): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val that = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diffDays = (now - that) / (24 * 60 * 60 * 1000L)
        return diffDays.toString()
    }

    private fun groupLabel(key: String): String {
        return when (key) {
            "0" -> getString(R.string.today)
            "1" -> getString(R.string.yesterday)
            else -> getString(R.string.earlier)
        }
    }

    private fun renderList() {
        val visible = currentItems()
        binding.emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE

        val flat = mutableListOf<FileListAdapter.Item>()
        var lastKey: String? = null
        for (file in visible) {
            // 按大小排序时不插入日期头
            if (!sortBySize && file.groupKey != lastKey) {
                flat.add(FileListAdapter.Item.Header(groupLabel(file.groupKey)))
                lastKey = file.groupKey
            }
            flat.add(FileListAdapter.Item.FileItem(file))
        }
        adapter.submit(flat)
        updateStorageSummary(visible)
    }

    private fun updateStorageSummary(files: List<VideoFile>) {
        val totalBytes = files.sumOf { it.sizeBytes }
        val totalDuration = files.sumOf { it.durationMs }
        val path = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        ).absolutePath
        var totalSpace: Long = 0L
        var freeSpace: Long = 0L
        try {
            val stat = StatFs(path)
            totalSpace = stat.totalBytes
            freeSpace = stat.availableBytes
        } catch (_: Exception) {
            File(path).mkdirs()
        }
        val used = if (totalSpace > 0) totalSpace - freeSpace else 0L

        binding.tvStorageValue.text =
            String.format(Locale.US, "%.1f GB / %.1f GB", used / 1e9, totalSpace / 1e9)
        // 等布局完成后再取宽度，修复首次进入进度条永远为 0 的问题
        binding.storageBar.post {
            if (_binding == null) return@post
            val width = if (totalSpace > 0)
                (binding.storageBar.width * (used.toFloat() / totalSpace)).toInt()
            else 0
            binding.storageBarFill.layoutParams = binding.storageBarFill.layoutParams.apply {
                this.width = width
            }
            binding.storageBarFill.requestLayout()
        }
        binding.tvFileCount.text = getString(R.string.file_count, files.size)
        binding.tvTotalDurationInfo.text = String.format(Locale.US, "%.1f 小时", totalDuration / 3.6e6)
        binding.tvRemainingInfo.text =
            String.format(Locale.US, "剩余 %.1f GB", freeSpace / 1e9)
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
