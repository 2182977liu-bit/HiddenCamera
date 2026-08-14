package com.example.hiddencamera

import java.io.File

/**
 * 录制状态密封类
 *
 * 用于在 Service 和 UI 之间统一传递录制状态，替代原有的 Broadcast 机制。
 */
sealed class RecordingState {

    /** 空闲状态 */
    object Idle : RecordingState()

    /**
     * 录制中状态
     * @param startTime 录制开始时间戳（毫秒）
     * @param outputFile 输出文件
     */
    data class Recording(
        val startTime: Long,
        val outputFile: File
    ) : RecordingState()

    /**
     * 录制停止中状态（等待 Finalize 回调）
     * @param pendingFile 待完成文件
     */
    data class Stopping(val pendingFile: File?) : RecordingState()

    /**
     * 录制错误状态
     * @param message 错误信息
     */
    data class Error(val message: String) : RecordingState() {
        override fun toString(): String = "Error($message)"
    }
}
