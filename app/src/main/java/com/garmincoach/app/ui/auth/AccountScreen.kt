package com.garmincoach.app.ui.auth

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garmincoach.app.data.api.GarminApi
import com.garmincoach.app.data.model.Race
import com.garmincoach.app.data.model.UserProfile
import com.garmincoach.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 账号页（2026-08-20 重构）：
 * - 顶部"基础信息未完善"banner（如 goal 为空）
 * - 基础信息卡片（身高/体重/年龄/性别/目标/经验/频率/目标成绩）—— 全字段可编辑
 * - 训练类型（marathon / half_marathon）多选 chips
 * - 我的比赛列表（每行：名字 + 日期 + 距今天数 + 删除按钮；底部"添加比赛"按钮）
 * - 设备管理入口（跳 WatchBindingScreen）
 * - 底部"保存"按钮：调 /api/user/profile 一把存
 * - "退出登录"按钮（保留）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenBinding: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var profileCompleted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var trainingTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var races by remember { mutableStateOf<List<Race>>(emptyList()) }

    // 表单字段本地 state（双向绑定到 profile，编辑时实时同步）
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("") }
    var trainingYears by remember { mutableStateOf("") }
    var trainingFreq by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    GarminApi.getMe()
                }.fold(
                    onSuccess = { resp ->
                        profileCompleted = resp.optBoolean("profile_completed", false)
                        val typesArr = resp.optJSONArray("training_types")
                        trainingTypes = if (typesArr != null) (0 until typesArr.length()).map { typesArr.optString(it) }.filter { it.isNotEmpty() } else emptyList()
                        val racesArr = resp.optJSONArray("races")
                        races = if (racesArr != null) (0 until racesArr.length()).map { i ->
                            val r = racesArr.getJSONObject(i)
                            Race(name = r.optString("name", ""), date = r.optString("date", ""))
                        } else emptyList()
                        // 用 /api/user/profile 拿完整档案填表单
                        withContext(Dispatchers.IO) { GarminApi.getUserProfile() }.fold(
                            onSuccess = { p ->
                                profile = p
                                height = p.height_cm?.toString() ?: ""
                                weight = p.weight_kg?.toString() ?: ""
                                age = p.age?.toString() ?: ""
                                gender = p.gender ?: ""
                                goal = p.goal ?: ""
                                goalTarget = p.goal_target ?: ""
                                trainingYears = p.training_years?.toString() ?: ""
                                trainingFreq = p.training_freq_per_week?.toString() ?: ""
                                loading = false
                            },
                            onFailure = { loading = false },
                        )
                    },
                    onFailure = { loading = false },
                )
            } catch (e: Exception) {
                loading = false
            }
        }
    }

    fun saveProfile() {
        if (saving) return
        saving = true
        val updated = (profile ?: UserProfile()).copy(
            height_cm = height.toDoubleOrNull(),
            weight_kg = weight.toDoubleOrNull(),
            age = age.toIntOrNull(),
            gender = gender.ifBlank { null },
            body_fat_pct = profile?.body_fat_pct,
            muscle_mass_kg = profile?.muscle_mass_kg,
            goal = goal.ifBlank { null },
            goal_target = goalTarget.ifBlank { null },
            training_years = trainingYears.toDoubleOrNull(),
            training_freq_per_week = trainingFreq.toIntOrNull(),
            training_types = trainingTypes,
            races = races,
        )
        scope.launch {
            withContext(Dispatchers.IO) {
                GarminApi.updateUserProfile(updated)
            }.fold(
                onSuccess = {
                    profile = updated
                    profileCompleted = updated.goal != null
                    saving = false
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    saving = false
                    Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
            )
        },
        containerColor = DarkBg,
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 未完善 banner ──
            if (!profileCompleted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📋 基础信息未完善", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("完善后 AI 才能根据你的训练目标给出更精准的建议。", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }

            // ── 基础信息 ──
            Text("基础信息", fontSize = 13.sp, color = TextMuted, letterSpacing = 0.5.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = height, onValueChange = { height = it },
                            label = { Text("身高 (cm)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = weight, onValueChange = { weight = it },
                            label = { Text("体重 (kg)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = age, onValueChange = { age = it },
                            label = { Text("年龄") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = trainingFreq, onValueChange = { trainingFreq = it },
                            label = { Text("训练频率 (次/周)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 性别 chips
                    Text("性别", fontSize = 12.sp, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("男" to "male", "女" to "female").forEach { (label, value) ->
                            FilterChip(
                                selected = gender == value,
                                onClick = { gender = if (gender == value) "" else value },
                                label = { Text(label) },
                            )
                        }
                    }
                    // 目标 chips
                    Text("训练目标", fontSize = 12.sp, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "半马" to "half_marathon",
                            "全马" to "full_marathon",
                            "塑形" to "shaping",
                        ).forEach { (label, value) ->
                            FilterChip(
                                selected = goal == value,
                                onClick = { goal = if (goal == value) "" else value },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = goalTarget, onValueChange = { goalTarget = it },
                        label = { Text("目标成绩（如半马 2:00:00，可选）") }, singleLine = true,
                    )
                    OutlinedTextField(
                        value = trainingYears, onValueChange = { trainingYears = it },
                        label = { Text("训练经验（年，可选）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }

            // ── 训练类型（多选）──
            Text("训练类型（多选）", fontSize = 13.sp, color = TextMuted, letterSpacing = 0.5.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "全程马拉松" to "marathon",
                        "半程马拉松" to "half_marathon",
                    ).forEach { (label, value) ->
                        FilterChip(
                            selected = trainingTypes.contains(value),
                            onClick = {
                                trainingTypes = if (trainingTypes.contains(value)) {
                                    trainingTypes - value
                                } else {
                                    trainingTypes + value
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ── 我的比赛 ──
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("我的比赛", fontSize = 13.sp, color = TextMuted, letterSpacing = 0.5.sp)
                TextButton(onClick = { races = races + Race() }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加比赛")
                }
            }
            if (races.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "暂未添加比赛",
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        color = TextMuted, fontSize = 13.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    races.forEachIndexed { idx, race ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = race.name, onValueChange = { newName ->
                                            races = races.toMutableList().also { it[idx] = race.copy(name = newName) }
                                        },
                                        label = { Text("比赛名字") }, singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        races = races.toMutableList().also { it.removeAt(idx) }
                                    }) {
                                        Icon(Icons.Default.Delete, "删除", tint = Danger)
                                    }
                                }
                                OutlinedTextField(
                                    value = race.date, onValueChange = { newDate ->
                                        races = races.toMutableList().also { it[idx] = race.copy(date = newDate) }
                                    },
                                    label = { Text("比赛日期 (YYYY-MM-DD)") }, singleLine = true,
                                    placeholder = { Text("2026-11-30") },
                                )
                                // 倒计时
                                daysUntil(race.date)?.let { days ->
                                    val text = when {
                                        days > 0 -> "⏳ 还有 $days 天"
                                        days == 0L -> "🎯 今天比赛"
                                        else -> "✅ 已结束"
                                    }
                                    val color = if (days >= 0) Accent else TextMuted
                                    Text(text, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
                                } ?: Text(
                                    "请填入有效日期",
                                    fontSize = 12.sp, color = TextMuted,
                                )
                            }
                        }
                    }
                }
            }

            // ── 保存按钮 ──
            Button(
                onClick = { saveProfile() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = TextPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("保存中...")
                } else {
                    Text("保存基础信息", fontWeight = FontWeight.Medium)
                }
            }

            // ── 设备管理入口 ──
            Text("设备", fontSize = 13.sp, color = TextMuted, letterSpacing = 0.5.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                onClick = onOpenBinding,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Watch, null, tint = Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("管理绑定设备", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("绑定、停用、解绑手表账号", fontSize = 12.sp, color = TextMuted)
                    }
                    Text("›", fontSize = 22.sp, color = TextMuted)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 退出登录 ──
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Danger),
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("退出登录", fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("退出后需要重新登录才能继续使用，确定要退出吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        logout(context, onLoggedOut)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger),
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            },
            containerColor = DarkCard,
        )
    }
}

/** 距比赛日期的天数（今天算 0；未来正数；过去负数；格式错误返回 null） */
private fun daysUntil(dateIso: String): Long? {
    if (dateIso.isBlank()) return null
    return try {
        val target = java.time.LocalDate.parse(dateIso)
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), target)
    } catch (e: Exception) {
        null
    }
}

private fun logout(context: Context, onLoggedOut: () -> Unit) {
    val prefs = context.getSharedPreferences("garmin_coach_prefs", Context.MODE_PRIVATE)
    // 只清当前登录态：token + user_id
    // 保留 per-user onboarding 状态（bound_<uid>），同账号再登录直接跳过 onboarding
    prefs.edit()
        .remove("auth_token")
        .remove("user_id")
        .apply()
    GarminApi.authToken = null
    GarminApi.currentUserId = 0
    Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
    onLoggedOut()
}