package com.garmincoach.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garmincoach.app.data.TrainingRecordBus
import com.garmincoach.app.data.api.GarminApi
import com.garmincoach.app.data.model.ActivityDetail
import com.garmincoach.app.data.model.DayStats
import com.garmincoach.app.data.model.StrengthSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state

    init {
        refresh()
        // AI 记录训练后静默刷新
        viewModelScope.launch {
            TrainingRecordBus.changed.collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(loading = true)
            // 用户主动刷新（非静默）时，后台同步佳明+天气，不阻塞数据读取
            if (!silent) {
                viewModelScope.launch {
                    GarminApi.syncGarmin()
                    GarminApi.syncWeather()
                }
            }
            GarminApi.getRecentActivities(30).fold(
                onSuccess = { days ->
                    val allActivities = days.flatMap { d ->
                        d.activities.map { a ->
                            ActivityListItem(
                                activityId = a.activity_id,
                                date = d.date,
                                weekday = d.weekday,
                                type = a.type ?: "运动",
                                time = a.time,
                                distanceKm = a.distance_km,
                                durationMin = a.duration_min,
                                avgHr = a.avg_hr,
                                avgPace = a.avg_pace,
                                summary = a.summary,
                            )
                        }
                    }
                    _state.value = HistoryUiState(activities = allActivities, loading = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                },
            )
        }
    }

    fun loadActivityDetail(activityId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingDetail = true)
            GarminApi.getActivityDetail(activityId).fold(
                onSuccess = { detail ->
                    _state.value = _state.value.copy(selectedDetail = detail, loadingDetail = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loadingDetail = false, detailError = e.message)
                },
            )
        }
    }

    fun loadDetailForEdit(activityId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingDetail = true)
            GarminApi.getActivityDetail(activityId).fold(
                onSuccess = { detail ->
                    _state.value = _state.value.copy(selectedDetail = detail, editingId = activityId, loadingDetail = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loadingDetail = false, detailError = e.message)
                },
            )
        }
    }

    fun clearDetail() {
        _state.value = _state.value.copy(selectedDetail = null, detailError = null, editingId = null)
    }

    fun deleteActivity(activityId: String) {
        viewModelScope.launch {
            GarminApi.deleteActivity(activityId).fold(
                onSuccess = { refresh(silent = true) },
                onFailure = { e -> _state.value = _state.value.copy(error = e.message) },
            )
        }
    }

    fun updateActivity(activityId: String, exercises: List<StrengthSet>) {
        viewModelScope.launch {
            GarminApi.updateActivityExercises(activityId, exercises).fold(
                onSuccess = {
                    clearDetail()
                    refresh(silent = true)
                },
                onFailure = { e -> _state.value = _state.value.copy(detailError = e.message) },
            )
        }
    }
}

data class HistoryUiState(
    val activities: List<ActivityListItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selectedDetail: ActivityDetail? = null,
    val loadingDetail: Boolean = false,
    val detailError: String? = null,
    val editingId: String? = null,
)

data class ActivityListItem(
    val activityId: String,
    val date: String,
    val weekday: String,
    val type: String,
    val time: String? = null,
    val distanceKm: Double?,
    val durationMin: Double?,
    val avgHr: Int?,
    val avgPace: String?,
    val summary: String? = null,
)
