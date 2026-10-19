package com.garmincoach.app.data.model

/**
 * 所有 API 返回数据的 Kotlin 数据类。
 * 字段名和 FastAPI 返回的 JSON 一一对应。
 */

// ── 仪表盘 ──

data class DashboardData(
    val datetime: String = "",
    val period: String = "",
    val today: DayData? = null,
    val recent_7d: WeekSummary? = null,
    val daily_stats: List<DayStats> = emptyList(),
    val safety: SafetyInfo? = null,
    val charts: ChartData? = null,
)

data class DayData(
    val date: String = "",
    val weekday: String = "",
    val hrv: Int? = null,
    val hrv_status: String? = null,
    val resting_hr: Int? = null,
    val sleep_hours: Double? = null,
    val sleep_score: Int? = null,
    val sleep_deep_min: Int? = null,
    val body_battery_high: Int? = null,
    val body_battery_low: Int? = null,
    val body_battery_wake: Int? = null,
    val body_battery_now: Int? = null,
    val vo2max: Double? = null,
    val training_status: String? = null,
    val training_feedback: String? = null,
    val acwr_ratio: Double? = null,
    val fitness_age: Double? = null,
    val race_5k: String? = null,
    val race_10k: String? = null,
    val race_half: String? = null,
    val race_full: String? = null,
    val steps: Int? = null,
    val weather_desc: String? = null,
    val weather_high: Int? = null,
    val weather_low: Int? = null,
    val weather_humidity: Int? = null,
    val weather_aqi: Int? = null,
    val weather_aqi_category: String? = null,
    val weather_uv: Int? = null,
    val weather_rain_prob: Double? = null,
    val location: String? = null,
    val now_temp: Int? = null,
    val now_feels_like: Int? = null,
    val now_text: String? = null,
    val now_precip: Double? = null,
    val activities: List<ActivityBrief>? = null,
)

data class ActivityBrief(
    val activity_id: String = "",
    val type: String? = null,
    val time: String? = null,
    val distance_km: Double? = null,
    val duration_min: Double? = null,
    val avg_hr: Int? = null,
    val avg_pace: String? = null,
    val summary: String? = null,
)

data class WeekSummary(
    val total_distance_km: Double = 0.0,
    val total_duration_min: Double = 0.0,
    val run_count: Int = 0,
    val avg_hrv: Int? = null,
    val avg_resting_hr: Int? = null,
    val avg_sleep_h: Double? = null,
)

data class DayStats(
    val date: String = "",
    val weekday: String = "",
    val hrv: Int? = null,
    val resting_hr: Int? = null,
    val sleep_hours: Double? = null,
    val sleep_score: Int? = null,
    val body_battery_high: Int? = null,
    val activities: List<ActivityBrief> = emptyList(),
)

data class SafetyInfo(
    val max_tier: String = "LONG",
    val tier_description: String = "",
    val blockers: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val recent_hrv_avg: Int? = null,
    val recent_hard_days: Int = 0,
)

data class ChartData(
    val hrv_trend: List<TrendPoint> = emptyList(),
    val rhr_trend: List<TrendPoint> = emptyList(),
    val sleep_trend: List<TrendPoint> = emptyList(),
)

data class TrendPoint(
    val date: String = "",
    val value: Double = 0.0,
)

// ── 活动详情 ──

data class ActivityDetail(
    val activity_id: String = "",
    val date: String = "",
    val type: String? = null,
    val time: String? = null,
    val location: String? = null,
    val distance_km: Double? = null,
    val duration_min: Double? = null,
    val avg_pace: String? = null,
    val max_pace: String? = null,
    val avg_hr: Int? = null,
    val max_hr: Int? = null,
    val calories: Int? = null,
    val training_effect_aerobic: Double? = null,
    val training_effect_anaerobic: Double? = null,
    val cadence_avg: Double? = null,
    val elevation_gain_m: Double? = null,
    val hr_zones: Map<String, Int>? = null,
    val splits: List<Split>? = null,
    val strength_sets: List<StrengthSet>? = null,
)

data class Split(
    val km: Int = 0,
    val pace: String = "",
    val hr: Int? = null,
)

data class StrengthSet(
    val name: String = "",
    val sets: Int? = null,
    val reps: Int? = null,
    val reps_detail: String? = null,
    val weight_kg: Double? = null,
    val equipment: String? = null,
)

// ── 用户档案 ──

data class Race(
    val name: String = "",
    val date: String = "",
)

data class UserProfile(
    val height_cm: Double? = null,
    val weight_kg: Double? = null,
    val age: Int? = null,
    val gender: String? = null,
    val body_fat_pct: Double? = null,
    val muscle_mass_kg: Double? = null,
    val goal: String? = null,
    val goal_target: String? = null,
    val training_years: Double? = null,
    val training_freq_per_week: Int? = null,
    val training_types: List<String> = emptyList(),
    val races: List<Race> = emptyList(),
)

// ── 对话 ──

data class ChatRequest(
    val message: String,
    val provider: String = "",
)

data class ChatResponse(
    val reply: String = "",
    val context: Map<String, Any?>? = null,
)

data class ChatReply(
    val reply: String = "",
    val recorded: Boolean = false,
)

data class ChatHistoryItem(
    val role: String = "",
    val content: String = "",
    val type: String = "text",
    val audio_url: String = "",
    val duration_sec: Int = 0,
    val created_at: String = "",
)

data class VoiceReply(
    val text: String = "",
    val audio_url: String = "",
    val duration_sec: Int = 0,
    val reply: String = "",
    val recorded: Boolean = false,
)

// ── 鉴权 ──

data class AuthRequest(
    val phone: String,
    val password: String,
)

data class AuthResponse(
    val ok: Boolean = false,
    val user_id: Int = 0,
    val token: String = "",
)

data class WatchBinding(
    val id: Int = 0,
    val brand: String = "",
    val account: String = "",
    val enabled: Boolean = true,
    val created_at: String = "",
)

data class WatchBindRequest(
    val brand: String,
    val account: String,
    val password: String = "",
    val region: String = "cn",
)

data class BindResult(
    val ok: Boolean = false,
    val needsMfa: Boolean = false,
    val message: String = "",
)
