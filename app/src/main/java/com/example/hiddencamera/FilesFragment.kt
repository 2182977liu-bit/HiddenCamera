package com.example.hiddencamera

import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
    private var filter: Int = 0 // 0=全部 1=视频 2=截图（截图暂等同视频展示）

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
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
        binding.btnSearch.setOnClickListener {
            Toast.makeText(requireContext(), R.string.search, Toast.LENGTH_SHORT).show()
        }
        binding.btnViewToggle.setOnClickListener {
            Toast.makeText(requireContext(), R.string.toggle_view, Toast.LENGTH_SHORT).show()
        }
        binding.btnSort.setOnClickListener {
            Toast.makeText(requireContext(), R.string.sort_newest, Toast.LENGTH_SHORT).show()
        }

        binding.btnSelectAll.setOnClickListener {
            val ids = currentItems().map { it.id }
            adapter.selectAll(ids)
            updateMultiBar()
        }
        binding.btnShare.setOnClickListener { shareSelected() }
        binding.btnMerge.setOnClickListener {
            Toast.makeText(requireContext(), R.string.merge, Toast.LENGTH_SHORT).show()
        }
        binding.btnDelete.setOnClickListener { deleteSelected(getVisibleFilesForSelection()) }
    }

    private fun setupSheetActions() {
        binding.sheetCancel.setOnClickListener { hideSheet() }
        binding.sheetPlay.setOnClickListener {
            Toast.makeText(requireContext(), R.string.play, Toast.LENGTH_SHORT).show()
            hideSheet()
        }
        binding.sheetShare.setOnClickListener {
            shareSelected()
            hideSheet()
        }
        binding.sheetRename.setOnClickListener {
            Toast.makeText(requireContext(), R.string.rename, Toast.LENGTH_SHORT).show()
            hideSheet()
        }
        binding.sheetMove.setOnClickListener {
            Toast.makeText(requireContext(), R.string.move_to, Toast.LENGTH_SHORT).show()
            hideSheet()
        }
        binding.sheetDetails.setOnClickListener {
            Toast.makeText(requireContext(), R.string.details, Toast.LENGTH_SHORT).show()
            hideSheet()
        }
        binding.sheetDelete.setOnClickListener {
            val file = sheetFile
            if (file != null) {
                adapter.setSelected(file.id, true)
                updateMultiBar()
                deleteSelected(listOf(file))
            }
            hideSheet()
        }
    }

    private var sheetFile: VideoFile? = null

    private fun openSheet(file: VideoFile) {
        sheetFile = file
        binding.sheetFilename.text = file.name
        binding.bottomSheet.visibility = View.VISIBLE
    }

    private fun hideSheet() {
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

    private fun currentItems(): List<VideoFile> =
        allFiles.filter { if (filter == 0) true else it.name.contains("VID") }

    private fun getVisibleFilesForSelection(): List<VideoFile> =
        currentItems().filter { adapter.isSelected(it.id) }

    private fun shareSelected() {
        val files = getVisibleFilesForSelection()
        if (files.isEmpty()) return
        Toast.makeText(requireContext(), getString(R.string.share), Toast.LENGTH_SHORT).show()
    }

    private fun deleteSelected(files: List<VideoFile>) {
        if (files.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_confirm, files.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                doDelete(files)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doDelete(files: List<VideoFile>) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val resolver = ctx.contentResolver
            for (file in files) {
                try {
                    resolver.delete(file.uri, null, null)
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

    // ===== MediaStore 扫描 =====
    private fun loadFiles() {
        // 先在主线程捕获 attachment 上的 context，避免 Fragment 销毁后 requireContext() 抛异常
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val files = scanVideos(ctx)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                allFiles = files
                renderList()
            }
        }
    }

    private fun scanVideos(ctx: Context): List<VideoFile> {
        val resolver = ctx.contentResolver
        val result = mutableListOf<VideoFile>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%${Constants.OUTPUT_DIR_NAME}%", "%${Constants.OUTPUT_DIR_NAME}%")

        try {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durCol)
                    val dateTaken = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    result.add(
                        VideoFile(
                            id = id,
                            uri = uri,
                            name = name,
                            sizeBytes = size,
                            durationMs = duration,
                            dateTaken = dateTaken,
                            groupKey = groupKeyFor(dateTaken)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result.sortedByDescending { it.dateTaken }
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
        val seen = mutableSetOf<String>()
        for (file in visible) {
            if (file.groupKey != lastKey) {
                flat.add(FileListAdapter.Item.Header(groupLabel(file.groupKey)))
                lastKey = file.groupKey
                seen.add(file.groupKey)
            }
            flat.add(FileListAdapter.Item.FileItem(file))
        }
        adapter.submit(flat)
        updateStorageSummary(visible)
    }

    private fun updateStorageSummary(files: List<VideoFile>) {
        val totalBytes = files.sumOf { it.sizeBytes }
        val totalDuration = files.sumOf { it.durationMs }
        // 使用已存在的标准公共目录做 StatFs，避免对不存在的子目录调用抛异常导致闪退
        val path = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        ).absolutePath
        var totalSpace: Long = 0L
        var freeSpace: Long = 0L
        try {
            val stat = android.os.StatFs(path)
            totalSpace = stat.totalBytes
            freeSpace = stat.availableBytes
        } catch (_: Exception) {
            File(path).mkdirs()
        }
        val used = if (totalSpace > 0) totalSpace - freeSpace else 0L

        binding.tvStorageValue.text =
            String.format(Locale.US, "%.1f GB / %.1f GB", used / 1e9, totalSpace / 1e9)
        binding.storageBarFill.layoutParams = binding.storageBarFill.layoutParams.apply {
            width = if (totalSpace > 0)
                (binding.storageBar.width * (used.toFloat() / totalSpace)).toInt()
            else 0
        }
        binding.tvFileCount.text = getString(R.string.file_count, files.size)
        binding.tvTotalDurationInfo.text = String.format(Locale.US, "%.1f 小时", totalDuration / 3.6e6)
        binding.tvRemainingInfo.text =
            String.format(Locale.US, "剩余 %.1f GB", freeSpace / 1e9)
    }
}