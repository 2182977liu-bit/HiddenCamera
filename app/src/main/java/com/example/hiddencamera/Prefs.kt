package com.example.hiddencamera

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "hiddencamera_prefs"
    private const val KEY_CAMERA_LENS = "camera_lens"
    private const val KEY_RESOLUTION = "resolution"
    private const val KEY_FPS = "fps"
    private const val KEY_PREVIEW_MODE = "preview_mode"
    private const val KEY_NOTIFICATION_ACTION = "notification_action"
    private const val KEY_SHORTCUT_ENABLED = "shortcut_enabled"

    private const val DEFAULT_LENS = "back"
    private const val DEFAULT_RESOLUTION = 1
    private const val DEFAULT_FPS = 30
    private const val DEFAULT_PREVIEW_MODE = 0
    private const val DEFAULT_NOTIFICATION_ACTION = true
    private const val DEFAULT_SHORTCUT_ENABLED = true

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getCameraLens(context: Context): String =
        prefs(context).getString(KEY_CAMERA_LENS, DEFAULT_LENS) ?: DEFAULT_LENS

    fun setCameraLens(context: Context, lens: String) =
        prefs(context).edit().putString(KEY_CAMERA_LENS, lens).apply()

    fun getResolution(context: Context): Int =
        prefs(context).getInt(KEY_RESOLUTION, DEFAULT_RESOLUTION)

    fun setResolution(context: Context, res: Int) =
        prefs(context).edit().putInt(KEY_RESOLUTION, res).apply()

    fun getFps(context: Context): Int =
        prefs(context).getInt(KEY_FPS, DEFAULT_FPS)

    fun setFps(context: Context, fps: Int) =
        prefs(context).edit().putInt(KEY_FPS, fps).apply()

    fun getPreviewMode(context: Context): Int =
        prefs(context).getInt(KEY_PREVIEW_MODE, DEFAULT_PREVIEW_MODE)

    fun setPreviewMode(context: Context, mode: Int) =
        prefs(context).edit().putInt(KEY_PREVIEW_MODE, mode).apply()

    fun isNotificationActionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATION_ACTION, DEFAULT_NOTIFICATION_ACTION)

    fun setNotificationActionEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_ACTION, enabled).apply()

    fun isShortcutEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHORTCUT_ENABLED, DEFAULT_SHORTCUT_ENABLED)

    fun setShortcutEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHORTCUT_ENABLED, enabled).apply()
}
