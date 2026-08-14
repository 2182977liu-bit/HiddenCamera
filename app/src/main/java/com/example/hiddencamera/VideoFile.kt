package com.example.hiddencamera

import android.net.Uri
import java.io.File

/** 单个录像文件（含日期分组信息） */
data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateTaken: Long,
    val groupKey: String,
    val file: File
)