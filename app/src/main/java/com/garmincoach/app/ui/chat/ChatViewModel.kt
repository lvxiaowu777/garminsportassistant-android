package com.garmincoach.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garmincoach.app.data.TrainingRecordBus
import com.garmincoach.app.data.api.GarminApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    init {
        // 进入时加载日期列表 + 今天的聊天记录（持久化）
        loadDates()
        loadHistory("")
    }

    fun loadDates() {
        viewModelScope.launch {
            GarminApi.getChatDates().fold(
                onSuccess = { dates ->
                    _state.value = _state.value.copy(dates = dates)
                },
                onFailure = { },
            )
        }
    }

    fun loadHistory(date: String) {
        viewModelScope.launch {
            val history = GarminApi.getChatHistory(date).getOrNull() ?: emptyList()
            val msgs = if (history.isEmpty()) {
                listOf(ChatMsg(role = "assistant", content = "👋 我是你的运动助理。问我'今晚跑不跑？''状态怎么样？'或者任何运动和身体的问题。"))
            } else {
                history.map {
                    ChatMsg(
                        role = it.role,
                        content = it.content,
                        type = it.type,
                        audioUrl = it.audio_url,
                        durationSec = it.duration_sec,
                        time = it.created_at.takeIf { c -> c.length >= 16 }?.substring(11, 16) ?: "",
                    )
                }
            }
            _state.value = _state.value.copy(messages = msgs, selectedDate = date)
        }
    }

    fun selectDate(date: String) {
        if (date == _state.value.selectedDate) return
        loadHistory(date)
    }

    fun send(message: String) {
        if (message.isBlank()) return

        // 若正在看历史日期，切回今天再发
        if (_state.value.selectedDate.isNotBlank()) {
            _state.value = _state.value.copy(selectedDate = "", messages = emptyList())
        }

        val msgs = _state.value.messages.toMutableList()
        msgs.add(ChatMsg(role = "user", content = message, time = nowTime()))
        _state.value = _state.value.copy(messages = msgs, sending = true)

        viewModelScope.launch {
            GarminApi.sendChat(message).fold(
                onSuccess = { cr ->
                    val updated = _state.value.messages.toMutableList()
                    updated.add(ChatMsg(role = "assistant", content = cr.reply, time = nowTime(), animate = true))
                    _state.value = _state.value.copy(messages = updated, sending = false)
                    if (cr.recorded) TrainingRecordBus.notifyChanged()
                    loadDates()
                },
                onFailure = { e ->
                    val updated = _state.value.messages.toMutableList()
                    updated.add(ChatMsg(role = "assistant", content = "❌ ${e.message}"))
                    _state.value = _state.value.copy(messages = updated, sending = false)
                },
            )
        }
    }

    fun sendVoice(file: File, durationSec: Int) {
        if (_state.value.selectedDate.isNotBlank()) {
            _state.value = _state.value.copy(selectedDate = "", messages = emptyList())
        }

        // 先加一条占位语音消息，上传后回填 audioUrl
        val msgs = _state.value.messages.toMutableList()
        msgs.add(ChatMsg(role = "user", content = "", type = "voice", durationSec = durationSec, time = nowTime()))
        _state.value = _state.value.copy(messages = msgs, sending = true)

        viewModelScope.launch {
            GarminApi.sendVoice(file, durationSec).fold(
                onSuccess = { vr ->
                    val updated = _state.value.messages.toMutableList()
                    val idx = updated.indexOfLast { it.role == "user" && it.type == "voice" && it.audioUrl.isEmpty() }
                    if (idx >= 0) updated[idx] = updated[idx].copy(audioUrl = vr.audio_url, content = vr.text)
                    updated.add(ChatMsg(role = "assistant", content = vr.reply, time = nowTime(), animate = true))
                    _state.value = _state.value.copy(messages = updated, sending = false)
                    if (vr.recorded) TrainingRecordBus.notifyChanged()
                    loadDates()
                },
                onFailure = { e ->
                    val updated = _state.value.messages.toMutableList()
                    updated.add(ChatMsg(role = "assistant", content = "❌ ${e.message}"))
                    _state.value = _state.value.copy(messages = updated, sending = false)
                },
            )
            file.delete()
        }
    }

    fun toggleTranscript(index: Int) {
        val msgs = _state.value.messages.toMutableList()
        if (index in msgs.indices) {
            msgs[index] = msgs[index].copy(showTranscript = !msgs[index].showTranscript)
        }
        _state.value = _state.value.copy(messages = msgs)
    }

    fun clear() {
        _state.value = ChatUiState(messages = listOf(
            ChatMsg(role = "assistant", content = "对话已清空。有什么可以帮你的？")
        ))
    }
}

data class ChatUiState(
    val messages: List<ChatMsg> = emptyList(),
    val sending: Boolean = false,
    val dates: List<String> = emptyList(),
    val selectedDate: String = "",
)

data class ChatMsg(
    val role: String,   // "user" | "assistant"
    val content: String,
    val type: String = "text",   // "text" | "voice"
    val audioUrl: String = "",
    val durationSec: Int = 0,
    val showTranscript: Boolean = false,
    val time: String = "",  // 发送时间 HH:MM
    val animate: Boolean = false,  // 是否打字机动画（仅实时回复 true）
)

private fun nowTime(): String {
    val t = java.time.LocalTime.now()
    return "%02d:%02d".format(t.hour, t.minute)
}
