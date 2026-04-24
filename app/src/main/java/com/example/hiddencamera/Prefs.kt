package com.example.hiddencamera

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "hiddencamera_prefs"
    private const val KEY_STORAGE_PATH = "storage_path"
    private const val KEY_CAMERA_LENS = "camera_lens"
    private const val KEY_RESOLUTION = "resolution"

    private const val DEFAULT_PATH = "/sdcard/HiddenCamera"
    private const val DEFAULT_LENS = "back"
    private const val DEFAULT_RESOLUTION = 1 // 0=480p, 1=720p, 2=1080p

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getStoragePath(context: Context): String =
        prefs(context).getString(KEY_STORAGE_PATH, DEFAULT_PATH) ?: DEFAULT_PATH

    fun setStoragePath(context: Context, path: String) =
        prefs(context).edit().putString(KEY_STORAGE_PATH, path).apply()

    fun getCameraLens(context: Context): String =
        prefs(context).getString(KEY_CAMERA_LENS, DEFAULT_LENS) ?: DEFAULT_LENS

    fun setCameraLens(context: Context, lens: String) =
        prefs(context).edit().putString(KEY_CAMERA_LENS, lens).apply()

    fun getResolution(context: Context): Int =
        prefs(context).getInt(KEY_RESOLUTION, DEFAULT_RESOLUTION)

    fun setResolution(context: Context, res: Int) =
        prefs(context).edit().putInt(KEY_RESOLUTION, res).apply()
}
