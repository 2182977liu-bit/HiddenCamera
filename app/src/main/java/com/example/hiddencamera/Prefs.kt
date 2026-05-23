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
    private const val KEY_BEAUTY_ENABLED = "beauty_enabled"
    private const val KEY_SKIN_SMOOTH = "skin_smooth"
    private const val KEY_SKIN_WHITEN = "skin_whiten"
    private const val KEY_SKIN_ROSY = "skin_rosy"
    private const val KEY_FACE_SLIM = "face_slim"
    private const val KEY_EYE_ENLARGE = "eye_enlarge"
    private const val KEY_BODY_SLIM = "body_slim"
    private const val KEY_LEG_LENGTH = "leg_length"
    private const val KEY_WAIST_SLIM = "waist_slim"

    private const val DEFAULT_LENS = "back"
    private const val DEFAULT_RESOLUTION = 1
    private const val DEFAULT_FPS = 30
    private const val DEFAULT_PREVIEW_MODE = 0
    private const val DEFAULT_NOTIFICATION_ACTION = true
    private const val DEFAULT_SHORTCUT_ENABLED = true
    private const val DEFAULT_BEAUTY_ENABLED = false

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

    fun isBeautyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BEAUTY_ENABLED, DEFAULT_BEAUTY_ENABLED)

    fun setBeautyEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_BEAUTY_ENABLED, enabled).apply()

    fun getSkinSmooth(context: Context): Int =
        prefs(context).getInt(KEY_SKIN_SMOOTH, 50)

    fun setSkinSmooth(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_SKIN_SMOOTH, level).apply()

    fun getSkinWhiten(context: Context): Int =
        prefs(context).getInt(KEY_SKIN_WHITEN, 50)

    fun setSkinWhiten(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_SKIN_WHITEN, level).apply()

    fun getSkinRosy(context: Context): Int =
        prefs(context).getInt(KEY_SKIN_ROSY, 0)

    fun setSkinRosy(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_SKIN_ROSY, level).apply()

    fun getFaceSlim(context: Context): Int =
        prefs(context).getInt(KEY_FACE_SLIM, 0)

    fun setFaceSlim(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_FACE_SLIM, level).apply()

    fun getEyeEnlarge(context: Context): Int =
        prefs(context).getInt(KEY_EYE_ENLARGE, 0)

    fun setEyeEnlarge(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_EYE_ENLARGE, level).apply()

    fun getBodySlim(context: Context): Int =
        prefs(context).getInt(KEY_BODY_SLIM, 0)

    fun setBodySlim(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_BODY_SLIM, level).apply()

    fun getLegLength(context: Context): Int =
        prefs(context).getInt(KEY_LEG_LENGTH, 0)

    fun setLegLength(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_LEG_LENGTH, level).apply()

    fun getWaistSlim(context: Context): Int =
        prefs(context).getInt(KEY_WAIST_SLIM, 0)

    fun setWaistSlim(context: Context, level: Int) =
        prefs(context).edit().putInt(KEY_WAIST_SLIM, level).apply()
}