package com.example.hiddencamera

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 来电状态监听器
 *
 * 当有来电时自动触发回调，避免录制时来电导致音频中断或冲突。
 */
class PhoneCallDetector private constructor(
    context: Context,
    private val onPhoneRinging: () -> Unit
) {

    companion object {
        private const val TAG = "PhoneCallDetector"

        @Volatile
        private var instance: PhoneCallDetector? = null

        fun start(context: Context, onPhoneRinging: () -> Unit): PhoneCallDetector? {
            return try {
                stop()
                val detector = PhoneCallDetector(context.applicationContext, onPhoneRinging)
                instance = detector
                detector.telephonyManager.listen(
                    detector.listener,
                    PhoneStateListener.LISTEN_CALL_STATE
                )
                detector
            } catch (e: SecurityException) {
                Log.w(TAG, "缺少 READ_PHONE_STATE 权限，来电监听不可用", e)
                null
            } catch (e: Exception) {
                Log.w(TAG, "启动来电监听失败", e)
                null
            }
        }

        fun stop() {
            instance?.let { detector ->
                try {
                    detector.telephonyManager.listen(detector.listener, PhoneStateListener.LISTEN_NONE)
                } catch (e: Exception) {
                    Log.w(TAG, "停止来电监听失败", e)
                }
            }
            instance = null
        }
    }

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @Suppress("DEPRECATION")
    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.d(TAG, "检测到来电: $phoneNumber")
                    onPhoneRinging()
                }
                TelephonyManager.CALL_STATE_OFFHOOK,
                TelephonyManager.CALL_STATE_IDLE -> {
                    // 不处理
                }
            }
        }
    }
}
