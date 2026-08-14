package com.example.hiddencamera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.hiddencamera.databinding.ItemDateHeaderBinding
import com.example.hiddencamera.databinding.ItemFileBinding
import java.util.Locale

/**
 * 文件列表适配器：行内嵌日期分组头。
 * [items] 为扁平列表，元素可能是 [GroupHeader] 或 [VideoFile]。
 */
class FileListAdapter(
    private val onSelect: (VideoFile) -> Unit,
    private val onLongClick: (VideoFile) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class Header(val label: String) : Item()
        data class FileItem(val file: VideoFile) : Item()
    }

    private val items = mutableListOf<Item>()
    private val selected = mutableSetOf<Long>()

    fun submit(list: List<Item>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setSelected(id: Long, selected: Boolean) {
        if (selected) this.selected.add(id) else this.selected.remove(id)
        notifyDataSetChanged()
    }

    fun selectAll(ids: List<Long>) {
        selected.clear()
        selected.addAll(ids)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    fun isSelected(id: Long): Boolean = selected.contains(id)
    fun selectedIds(): Set<Long> = selected
    fun selectedCount(): Int = selected.size

    override fun getItemViewType(position: Int): Int =
        if (items[position] is Item.Header) TYPE_HEADER else TYPE_FILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
        } else {
            FileHolder(ItemFileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderHolder).bind(item.label)
            is Item.FileItem -> (holder as FileHolder).bind(item.file)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String) {
            binding.tvDateHeader.text = label
        }
    }

    inner class FileHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(file: VideoFile) {
            binding.tvTitle.text = file.name
            binding.tvSub.text = formatSub(file)
            binding.tvDuration.text = formatDuration(file.durationMs)
            binding.root.isSelected = isSelected(file.id)
            binding.checkbox.setImageResource(
                if (isSelected(file.id)) R.drawable.ic_checkbox_checked else R.drawable.bg_checkbox_circle
            )

            binding.root.setOnClickListener {
                onSelect(file)
            }
            binding.root.setOnLongClickListener {
                onLongClick(file)
                true
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun formatSub(file: VideoFile): String {
        val sizeMb = file.sizeBytes / 1024f / 1024f
        return String.format(Locale.US, "%.1f MB", sizeMb)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FILE = 1
    }
}