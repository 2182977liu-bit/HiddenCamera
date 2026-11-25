package com.example.hiddencamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 轻量缩略图加载器：
 * - 视频取第一帧，图片直接解码
 * - 单线程后台执行 + 内存 LRU 缓存
 * - 用 ImageView.tag 校验，避免列表复用导致串图
 */
object ThumbnailLoader {

    private const val MAX_SIDE = 160

    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(file: File, view: ImageView) {
        val path = file.absolutePath
        view.tag = path

        val cached = cache.get(path)
        if (cached != null) {
            view.setImageBitmap(cached)
            view.visibility = View.VISIBLE
            return
        }
        view.visibility = View.GONE

        executor.execute {
            val bitmap = try {
                if (isImage(file.name)) decodeImage(path) else decodeVideoFrame(path)
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) {
                cache.put(path, bitmap)
            }
            mainHandler.post {
                if (view.tag == path && bitmap != null) {
                    view.setImageBitmap(bitmap)
                    view.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun isImage(name: String): Boolean {
        val n = name.lowercase(Locale.US)
        return n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".png") || n.endsWith(".webp")
    }

    private fun decodeVideoFrame(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val frame = retriever.frameAtTime ?: return null
            scale(frame)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun decodeImage(path: String): Bitmap? {
        // 先读尺寸，按采样率解码，避免大图占用过多内存
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        return scale(decoded)
    }

    private fun scale(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= MAX_SIDE && h <= MAX_SIDE) return src
        val ratio = minOf(MAX_SIDE.toFloat() / w, MAX_SIDE.toFloat() / h)
        val scaled = Bitmap.createScaledBitmap(
            src,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== src) src.recycle()
        return scaled
    }
}
