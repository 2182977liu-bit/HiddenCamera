package com.example.hiddencamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 主界面 UI 状态
 */
data class MainUiState(
    val isRecording: Boolean = false,
    val isStopping: Boolean = false,
    val recordingStartTime: Long = 0L,
    val errorMessage: String? = null,
    val showError: Boolean = false
)

/**
 * 主界面 ViewModel
 *
 * 负责管理 UI 状态和与 Service 的交互，避免 Activity 承担过多职责。
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** 当前绑定的 Service 引用 */
    private var recordingService: RecordingService? = null

    /** 收集 Service 状态的协程 Job */
    private var collectJob: kotlinx.coroutines.Job? = null

    /**
     * 绑定 Service，开始监听状态变化
     */
    fun bindService(service: RecordingService?) {
        recordingService = service
        collectJob?.cancel()
        if (service != null) {
            collectJob = viewModelScope.launch {
                service.recordingState.collect { state ->
                    _uiState.update { currentState ->
                        when (state) {
                            is RecordingState.Idle -> currentState.copy(
                                isRecording = false,
                                isStopping = false,
                                errorMessage = null,
                                showError = false
                            )
                            is RecordingState.Recording -> currentState.copy(
                                isRecording = true,
                                isStopping = false,
                                recordingStartTime = state.startTime,
                                errorMessage = null,
                                showError = false
                            )
                            is RecordingState.Stopping -> currentState.copy(
                                isRecording = false,
                                isStopping = true,
                                errorMessage = null,
                                showError = false
                            )
                            is RecordingState.Error -> currentState.copy(
                                isRecording = false,
                                isStopping = false,
                                errorMessage = state.message,
                                showError = true
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 解绑 Service
     */
    fun unbindService() {
        collectJob?.cancel()
        collectJob = null
        recordingService = null
    }

    /**
     * 错误已显示，清除错误状态
     */
    fun errorShown() {
        _uiState.update { it.copy(showError = false) }
    }

    /**
     * 获取当前录制时长（毫秒）
     */
    fun getCurrentDuration(currentTime: Long): Long {
        val state = _uiState.value
        return if (state.isRecording && state.recordingStartTime > 0) {
            currentTime - state.recordingStartTime
        } else 0L
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
