package com.garmincoach.app.data.api

import com.garmincoach.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * FastAPI 后端的 HTTP 客户端。
 * 所有方法都在 IO 线程跑，返回解析好的数据类。
 *
 * 修改 [BASE_URL] 可以切换连接的服务器：
 * - 模拟器连宿主机：http://10.0.2.2:8766
 * - 真机连电脑 WiFi：http://192.168.x.x:8766
 */
object GarminApi {

    // 部署到公网时改成你自己的服务器地址，例如 "https://your-domain.com"。
    // 默认值指向模拟器宿主机的 8766 端口（10.0.2.2 是 Android 模拟器里访问
    // 宿主机的固定地址），这样 clone 下来不用改代码就能连本机后端跑通。
    var BASE_URL = "http://10.0.2.2:8766"

    /** 登录后的 JWT token，由 App 在登录/注册成功后设置。 */
    var authToken: String? = null

    /** 当前用户 ID，登录后设置。 */
    var currentUserId: Int = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** 获取当前登录用户信息（手机号、注册时间）。用于"查看账号"页面。 */
    suspend fun getMe(): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/auth/me")
                .get()
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) {
                throw IOException(json.optString("detail", "获取账号失败"))
            }
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 鉴权 ──

    suspend fun register(phone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("phone", phone)
                put("password", password)
            }.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/api/auth/register")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) {
                throw IOException(json.optString("detail", "注册失败"))
            }
            val resp = AuthResponse(
                ok = json.optBoolean("ok"),
                user_id = json.optInt("user_id"),
                token = json.optString("token"),
            )
            if (resp.ok && resp.token.isNotEmpty()) {
                authToken = resp.token
                currentUserId = resp.user_id
            }
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(phone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("phone", phone)
                put("password", password)
            }.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/api/auth/login")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) {
                throw IOException(json.optString("detail", "登录失败"))
            }
            val resp = AuthResponse(
                ok = json.optBoolean("ok"),
                user_id = json.optInt("user_id"),
                token = json.optString("token"),
            )
            if (resp.ok && resp.token.isNotEmpty()) {
                authToken = resp.token
                currentUserId = resp.user_id
            }
            Result.success(resp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 退出登录，清除本地 token。 */
    fun logout() {
        authToken = null
        currentUserId = 0
    }

    suspend fun getUserProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val json = get("/api/user/profile")
            Result.success(parseUserProfile(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(profile: UserProfile): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                profile.height_cm?.let { put("height_cm", it) }
                profile.weight_kg?.let { put("weight_kg", it) }
                profile.age?.let { put("age", it) }
                profile.gender?.let { put("gender", it) }
                profile.body_fat_pct?.let { put("body_fat_pct", it) }
                profile.muscle_mass_kg?.let { put("muscle_mass_kg", it) }
                profile.goal?.let { put("goal", it) }
                profile.goal_target?.let { put("goal_target", it) }
                profile.training_years?.let { put("training_years", it) }
                profile.training_freq_per_week?.let { put("training_freq_per_week", it) }
                // 2026-08-20：始终发送数组（含空数组），让服务端能清空
                put("training_types", org.json.JSONArray(profile.training_types))
                val racesArr = org.json.JSONArray()
                profile.races.forEach { r ->
                    racesArr.put(JSONObject().apply {
                        put("name", r.name)
                        put("date", r.date)
                    })
                }
                put("races", racesArr)
            }.toString().toRequestBody(JSON)
            val request = authRequest().url("$BASE_URL/api/user/profile").post(body).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            Result.success("已保存")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseUserProfile(json: JSONObject): UserProfile {
        val typesArr = json.optJSONArray("training_types")
        val trainingTypes = if (typesArr != null) {
            (0 until typesArr.length()).map { typesArr.optString(it) }.filter { it.isNotEmpty() }
        } else emptyList()
        val racesArr = json.optJSONArray("races")
        val races = if (racesArr != null) {
            (0 until racesArr.length()).map { i ->
                val r = racesArr.getJSONObject(i)
                Race(name = r.optString("name", ""), date = r.optString("date", ""))
            }
        } else emptyList()
        return UserProfile(
            height_cm = json.optDoubleOrNull("height_cm"),
            weight_kg = json.optDoubleOrNull("weight_kg"),
            age = json.optIntOrNull("age"),
            gender = json.optString("gender", null),
            body_fat_pct = json.optDoubleOrNull("body_fat_pct"),
            muscle_mass_kg = json.optDoubleOrNull("muscle_mass_kg"),
            goal = json.optString("goal", null),
            goal_target = json.optString("goal_target", null),
            training_years = json.optDoubleOrNull("training_years"),
            training_freq_per_week = json.optIntOrNull("training_freq_per_week"),
            training_types = trainingTypes,
            races = races,
        )
    }

    val isLoggedIn: Boolean get() = authToken != null

    // ── 手表绑定 ──

    suspend fun bindWatch(brand: String, account: String, password: String = "", region: String = "cn"): Result<BindResult> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("brand", brand)
                put("account", account)
                put("password", password)
                put("region", region)
            }.toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$BASE_URL/api/watch/bind")
                .post(body)
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(req).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) throw IOException(json.optString("detail", "绑定失败"))
            Result.success(BindResult(
                ok = json.optBoolean("ok", true),
                needsMfa = json.optBoolean("needs_mfa", false),
                message = json.optString("message", "已绑定"),
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun bindWatchMfa(mfaCode: String): Result<BindResult> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("mfa_code", mfaCode) }.toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$BASE_URL/api/watch/bind-mfa")
                .post(body)
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(req).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) throw IOException(json.optString("detail", "验证码错误"))
            Result.success(BindResult(ok = true, needsMfa = false, message = json.optString("message", "已绑定")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncGarmin(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = authRequest()
                .url("$BASE_URL/api/health/sync")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) throw IOException(json.optString("detail", "同步失败"))
            Result.success(json.optInt("days", 0))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWatchList(): Result<List<WatchBinding>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/api/watch/list")
                .get()
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(req).execute()
            val arr = org.json.JSONArray(response.body?.string() ?: "[]")
            val list = (0 until arr.length()).map { i ->
                val json = arr.getJSONObject(i)
                WatchBinding(
                    id = json.optInt("id"),
                    brand = json.optString("brand"),
                    account = json.optString("account"),
                    enabled = json.optInt("enabled", 1) == 1,
                    created_at = json.optString("created_at"),
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unbindWatch(brand: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/api/watch/unbind?brand=$brand")
                .delete()
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(req).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) throw IOException(json.optString("detail", "解绑失败"))
            Result.success(json.optString("message", "已解绑"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleWatch(brand: String, enabled: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("brand", brand)
                put("enabled", enabled)
            }.toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$BASE_URL/api/watch/toggle")
                .post(body)
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(req).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) throw IOException(json.optString("detail", "操作失败"))
            Result.success(json.optString("message", "已更新"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun selectWatch(brand: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("brand", brand) }.toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$BASE_URL/api/watch/select")
                .post(body)
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(req).execute()
            val json = JSONObject(response.body?.string() ?: "")
            if (!response.isSuccessful) throw IOException(json.optString("detail", "切换失败"))
            Result.success(json.optString("active_brand", brand))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 天气同步 ──

    suspend fun syncWeather(lon: Double? = null, lat: Double? = null) {
        withContext(Dispatchers.IO) {
            try {
                val url = if (lon != null && lat != null)
                    "$BASE_URL/api/health/sync-weather?lon=$lon&lat=$lat"
                else
                    "$BASE_URL/api/health/sync-weather"
                val request = authRequest()
                    .url(url)
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {
                // 天气同步失败不影响主流程
            }
        }
    }

    // ── 仪表盘 ──

    suspend fun getDashboard(): Result<DashboardData> = withContext(Dispatchers.IO) {
        try {
            val json = get("/api/health/dashboard")
            Result.success(parseDashboard(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 安全档位 ──

    suspend fun getSafety(): Result<SafetyInfo> = withContext(Dispatchers.IO) {
        try {
            val json = get("/api/health/safety")
            Result.success(parseSafety(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 手动记录训练 ──

    suspend fun recordTraining(type: String, notes: String, durationMin: Int = 30): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("type", type)
                put("notes", notes)
                put("duration_min", durationMin)
            }.toString().toRequestBody(JSON)
            val request = authRequest()
                .url("$BASE_URL/api/health/record-training")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: "")
            Result.success(json.optString("message", "已记录"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 最近活动列表 ──

    suspend fun getRecentActivities(days: Int = 30): Result<List<DayStats>> = withContext(Dispatchers.IO) {
        try {
            val arr = getArray("/api/health/recent/$days")
            Result.success(parseDayStatsList(arr))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 单次活动详情 ──

    suspend fun getActivityDetail(activityId: String): Result<ActivityDetail> = withContext(Dispatchers.IO) {
        try {
            val json = get("/api/health/activity/$activityId")
            Result.success(parseActivityDetail(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteActivity(activityId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/api/health/activity/$activityId")
                .delete()
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(req).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            Result.success("已删除")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateActivityExercises(activityId: String, exercises: List<StrengthSet>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val arr = org.json.JSONArray()
            exercises.forEach { s ->
                val obj = JSONObject().apply {
                    put("name", s.name)
                    s.sets?.let { put("sets", it) }
                    s.reps?.let { put("reps", it) }
                    s.reps_detail?.let { put("reps_detail", it) }
                    s.weight_kg?.let { put("weight_kg", it) }
                    s.equipment?.let { put("equipment", it) }
                }
                arr.put(obj)
            }
            val body = JSONObject().apply { put("exercises", arr) }.toString().toRequestBody(JSON)
            val request = authRequest()
                .url("$BASE_URL/api/health/activity/$activityId")
                .put(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            Result.success("已保存")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 非流式对话 ──

    suspend fun sendChat(message: String): Result<ChatReply> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("message", message)
            }.toString().toRequestBody(JSON)

            val request = authRequest()
                .url("$BASE_URL/api/chat")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: "")
            Result.success(ChatReply(
                reply = json.getString("reply"),
                recorded = json.optBoolean("recorded"),
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatHistory(date: String = ""): Result<List<ChatHistoryItem>> = withContext(Dispatchers.IO) {
        try {
            val path = if (date.isBlank()) "/api/chat/history" else "/api/chat/history?date=$date"
            val arr = getArray(path)
            val list = (0 until arr.length()).map { i ->
                val json = arr.getJSONObject(i)
                ChatHistoryItem(
                    role = json.optString("role"),
                    content = json.optString("content"),
                    type = json.optString("type", "text"),
                    audio_url = json.optString("audio_url"),
                    duration_sec = json.optInt("duration_sec"),
                    created_at = json.optString("created_at"),
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatDates(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val arr = getArray("/api/chat/dates")
            Result.success((0 until arr.length()).map { arr.optString(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun asr(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/m4a".toMediaType()))
                .build()
            val request = authRequest()
                .url("$BASE_URL/api/asr")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: "{}")
            Result.success(json.optString("text"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendVoice(audioFile: File, durationSec: Int): Result<VoiceReply> = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/m4a".toMediaType()))
                .addFormDataPart("duration_sec", durationSec.toString())
                .build()
            val request = authRequest()
                .url("$BASE_URL/api/chat/voice")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: "{}")
            Result.success(VoiceReply(
                text = json.optString("text"),
                audio_url = json.optString("audio_url"),
                duration_sec = json.optInt("duration_sec"),
                reply = json.optString("reply"),
                recorded = json.optBoolean("recorded"),
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── HTTP 工具方法 ──

    private fun authRequest(): Request.Builder {
        val builder = Request.Builder()
        authToken?.let { builder.addHeader("Authorization", "Bearer $it") }
        return builder
    }

    private fun get(path: String): JSONObject {
        val request = authRequest().url("$BASE_URL$path").get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $path")
        return JSONObject(response.body?.string() ?: "{}")
    }

    private fun getArray(path: String): org.json.JSONArray {
        val request = authRequest().url("$BASE_URL$path").get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $path")
        return org.json.JSONArray(response.body?.string() ?: "[]")
    }

    // ── JSON 解析 ──

    private fun parseDashboard(json: JSONObject): DashboardData {
        val today = if (json.has("today") && !json.isNull("today"))
            parseDayData(json.getJSONObject("today")) else null
        val recent7d = if (json.has("recent_7d")) parseWeekSummary(json.getJSONObject("recent_7d")) else null
        val dailyStats = if (json.has("daily_stats")) parseDayStatsList(json.getJSONArray("daily_stats")) else emptyList()
        val safety = if (json.has("safety")) parseSafety(json.getJSONObject("safety")) else null
        val charts = if (json.has("charts")) parseChartData(json.getJSONObject("charts")) else null
        return DashboardData(
            datetime = json.optString("datetime"),
            period = json.optString("period"),
            today = today, recent_7d = recent7d,
            daily_stats = dailyStats, safety = safety, charts = charts,
        )
    }

    private fun parseDayData(json: JSONObject): DayData = DayData(
        date = json.optString("date"),
        weekday = json.optString("weekday"),
        hrv = json.optIntOrNull("hrv"),
        hrv_status = json.optString("hrv_status", null),
        resting_hr = json.optIntOrNull("resting_hr"),
        sleep_hours = json.optDoubleOrNull("sleep_hours"),
        sleep_score = json.optIntOrNull("sleep_score"),
        sleep_deep_min = json.optIntOrNull("sleep_deep_min"),
        body_battery_high = json.optIntOrNull("body_battery_high"),
        body_battery_low = json.optIntOrNull("body_battery_low"),
        body_battery_wake = json.optIntOrNull("body_battery_wake"),
        body_battery_now = json.optIntOrNull("body_battery_now"),
        vo2max = json.optDoubleOrNull("vo2max"),
        training_status = json.optString("training_status", null),
        training_feedback = json.optString("training_feedback", null),
        acwr_ratio = json.optDoubleOrNull("acwr_ratio"),
        fitness_age = json.optDoubleOrNull("fitness_age"),
        race_5k = json.optString("race_5k", null),
        race_10k = json.optString("race_10k", null),
        race_half = json.optString("race_half", null),
        race_full = json.optString("race_full", null),
        steps = json.optIntOrNull("steps"),
        weather_desc = json.optString("weather_desc", null),
        weather_high = json.optIntOrNull("weather_high"),
        weather_low = json.optIntOrNull("weather_low"),
        weather_humidity = json.optIntOrNull("weather_humidity"),
        weather_aqi = json.optIntOrNull("weather_aqi"),
        weather_aqi_category = json.optString("weather_aqi_category", null),
        weather_uv = json.optIntOrNull("weather_uv"),
        weather_rain_prob = json.optDoubleOrNull("weather_rain_prob"),
        location = json.optString("location", null),
        now_temp = json.optIntOrNull("now_temp"),
        now_feels_like = json.optIntOrNull("now_feels_like"),
        now_text = json.optString("now_text", null),
        now_precip = json.optDoubleOrNull("now_precip"),
        activities = if (json.has("activities")) parseActivityBriefList(json.getJSONArray("activities")) else null,
    )

    private fun parseWeekSummary(json: JSONObject): WeekSummary = WeekSummary(
        total_distance_km = json.optDouble("total_distance_km"),
        total_duration_min = json.optDouble("total_duration_min"),
        run_count = json.optInt("run_count"),
        avg_hrv = json.optIntOrNull("avg_hrv"),
        avg_resting_hr = json.optIntOrNull("avg_resting_hr"),
        avg_sleep_h = json.optDoubleOrNull("avg_sleep_h"),
    )

    private fun parseDayStatsList(arr: org.json.JSONArray): List<DayStats> =
        (0 until arr.length()).map { i ->
            val json = arr.getJSONObject(i)
            DayStats(
                date = json.optString("date"),
                weekday = json.optString("weekday"),
                hrv = json.optIntOrNull("hrv"),
                resting_hr = json.optIntOrNull("resting_hr"),
                sleep_hours = json.optDoubleOrNull("sleep_hours"),
                sleep_score = json.optIntOrNull("sleep_score"),
                body_battery_high = json.optIntOrNull("body_battery_high"),
                activities = if (json.has("activities")) parseActivityBriefList(json.getJSONArray("activities")) else emptyList(),
            )
        }

    private fun parseActivityBriefList(arr: org.json.JSONArray): List<ActivityBrief> =
        (0 until arr.length()).map { i ->
            val json = arr.getJSONObject(i)
            ActivityBrief(
                activity_id = json.optString("activity_id"),
                type = json.optString("type", null),
                time = json.optString("time", null),
                distance_km = json.optDoubleOrNull("distance_km"),
                duration_min = json.optDoubleOrNull("duration_min"),
                avg_hr = json.optIntOrNull("avg_hr"),
                avg_pace = json.optString("avg_pace", null),
                summary = json.optString("summary", null),
            )
        }

    private fun parseSafety(json: JSONObject): SafetyInfo = SafetyInfo(
        max_tier = json.optString("max_tier", "LONG"),
        tier_description = json.optString("tier_description"),
        blockers = jsonArrayToList(json.optJSONArray("blockers")),
        notes = jsonArrayToList(json.optJSONArray("notes")),
        recent_hrv_avg = json.optIntOrNull("recent_hrv_avg"),
        recent_hard_days = json.optInt("recent_hard_days"),
    )

    private fun parseChartData(json: JSONObject): ChartData = ChartData(
        hrv_trend = parseTrend(json.optJSONArray("hrv_trend")),
        rhr_trend = parseTrend(json.optJSONArray("rhr_trend")),
        sleep_trend = parseTrend(json.optJSONArray("sleep_trend")),
    )

    private fun parseTrend(arr: org.json.JSONArray?): List<TrendPoint> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val json = arr.getJSONObject(i)
            TrendPoint(date = json.optString("date"), value = json.optDouble("value"))
        }
    }

    private fun parseActivityDetail(json: JSONObject): ActivityDetail {
        val zonesJson = json.optJSONObject("hr_zones")
        val zones = if (zonesJson != null) {
            zonesJson.keys().asSequence().associateWith { zonesJson.optInt(it) }
        } else null

        val splitsArr = json.optJSONArray("splits")
        val splits = if (splitsArr != null) {
            (0 until splitsArr.length()).map { i ->
                val s = splitsArr.getJSONObject(i)
                Split(km = s.optInt("km"), pace = s.optString("pace"), hr = s.optIntOrNull("hr"))
            }
        } else null

        val setsArr = json.optJSONArray("strength_sets")
        val strengthSets = if (setsArr != null) {
            (0 until setsArr.length()).map { i ->
                val s = setsArr.getJSONObject(i)
                StrengthSet(
                    name = s.optString("name"),
                    sets = s.optIntOrNull("sets"),
                    reps = s.optIntOrNull("reps"),
                    reps_detail = s.optString("reps_detail", null),
                    weight_kg = s.optDoubleOrNull("weight_kg"),
                    equipment = s.optString("equipment", null),
                )
            }
        } else null

        return ActivityDetail(
            activity_id = json.optString("activity_id"),
            date = json.optString("date"),
            type = json.optString("type", null),
            time = json.optString("time", null),
            location = json.optString("location", null),
            distance_km = json.optDoubleOrNull("distance_km"),
            duration_min = json.optDoubleOrNull("duration_min"),
            avg_pace = json.optString("avg_pace", null),
            max_pace = json.optString("max_pace", null),
            avg_hr = json.optIntOrNull("avg_hr"),
            max_hr = json.optIntOrNull("max_hr"),
            calories = json.optIntOrNull("calories"),
            training_effect_aerobic = json.optDoubleOrNull("training_effect_aerobic"),
            training_effect_anaerobic = json.optDoubleOrNull("training_effect_anaerobic"),
            cadence_avg = json.optDoubleOrNull("cadence_avg"),
            elevation_gain_m = json.optDoubleOrNull("elevation_gain_m"),
            hr_zones = zones,
            splits = splits,
            strength_sets = strengthSets,
        )
    }

    // ── 工具 ──

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optString(key: String, fallback: String?): String? =
        if (has(key) && !isNull(key)) optString(key) else fallback
}
