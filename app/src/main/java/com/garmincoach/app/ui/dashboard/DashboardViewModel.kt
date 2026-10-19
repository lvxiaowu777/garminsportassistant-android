package com.garmincoach.app.ui.dashboard

import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garmincoach.app.data.api.GarminApi
import com.garmincoach.app.data.model.DashboardData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        refresh()
        // 每 5 分钟静默轮询一次
        viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000L)
                refresh(silent = true)
            }
        }
    }

    fun refresh(forceSync: Boolean = false, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(loading = true, error = null)
            }
            // forceSync：手动拉 Garmin（圆圈按钮触发）；静默轮询只读数据库
            if (forceSync) {
                GarminApi.syncGarmin()
            }
            syncWeather()
            kotlinx.coroutines.delay(500)
            GarminApi.getDashboard().fold(
                onSuccess = { data ->
                    _state.value = DashboardUiState(data = data, loading = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "加载失败"
                    )
                },
            )
        }
    }

    /** 定位后同步当地天气；未定位则后端用默认襄阳坐标兜底。 */
    private suspend fun syncWeather() {
        val loc = lastLocation()
        if (loc != null) GarminApi.syncWeather(loc.first, loc.second)
        else GarminApi.syncWeather()
    }

    private fun lastLocation(): Pair<Double, Double>? = try {
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        l?.let { it.longitude to it.latitude }
    } catch (e: Exception) {
        null
    }
}

data class DashboardUiState(
    val data: DashboardData? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
