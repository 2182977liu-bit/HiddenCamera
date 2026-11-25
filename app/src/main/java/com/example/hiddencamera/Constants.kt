package com.example.hiddencamera

/**
 * 应用常量集中管理
 */
object Constants {
    /** Intent Action: 开始录制 */
    const val ACTION_START = "ACTION_START"
    /** Intent Action: 停止录制 */
    const val ACTION_STOP = "ACTION_STOP"
    /** Intent Action: 切换录制状态 */
    const val ACTION_TOGGLE = "ACTION_TOGGLE"
    /** Intent Action: 切换摄像头（停止当前录制并自动用新摄像头重新开始） */
    const val ACTION_SWITCH_CAMERA = "ACTION_SWITCH_CAMERA"
    /** 桌面快捷方式 Action */
    const val ACTION_QUICK_TOGGLE = "com.example.hiddencamera.ACTION_TOGGLE_RECORDING"

    /** 通知 Channel ID */
    const val NOTIFICATION_CHANNEL_ID = "recording_channel"
    /** 通知 ID */
    const val NOTIFICATION_ID = 1

    /** 最大录制时长（毫秒）：30 分钟 */
    const val MAX_RECORDING_DURATION_MS = 30L * 60 * 1000

    /** 录制停止超时（毫秒） */
    const val STOP_TIMEOUT_MS = 3000L

    /** 最小可用存储空间（字节）：100 MB */
    const val MIN_FREE_SPACE_BYTES = 100L * 1024 * 1024

    /** 视频输出目录名 */
    const val OUTPUT_DIR_NAME = "xcodx"

    /** 视频文件名前缀 */
    const val VIDEO_FILE_PREFIX = "VID_"

    /** 截图文件名前缀 */
    const val IMAGE_FILE_PREFIX = "IMG_"

    /** 时间戳格式 */
    const val TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss_SSS"
}
