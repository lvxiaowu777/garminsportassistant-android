package com.garmincoach.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 训练记录变更信号：AI 记录训练后通知记录页刷新。 */
object TrainingRecordBus {
    private val _changed = MutableStateFlow(0L)
    val changed: StateFlow<Long> = _changed

    fun notifyChanged() {
        _changed.value++
    }
}
