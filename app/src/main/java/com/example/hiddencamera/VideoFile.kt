package com.example.hiddencamera

import android.net.Uri
import java.io.File

/** 单个录像/截图文件（含日期分组信息） */
data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateTaken: Long,
    val groupKey: String,
    val file: File,
    /** 视频宽度（像素），图片未知时为 0 */
    val width: Int = 0,
    /** 视频高度（像素），图片未知时为 0 */
    val height: Int = 0
)
