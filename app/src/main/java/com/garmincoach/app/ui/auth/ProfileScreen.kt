package com.garmincoach.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garmincoach.app.data.api.GarminApi
import com.garmincoach.app.util.SoundManager
import com.garmincoach.app.data.model.UserProfile
import com.garmincoach.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 首次登录的资料收集页。
 * 字段：身高/体重/年龄/性别（必填），体脂率/肌肉量（可选），目标、训练经验、训练频率。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onDone: () -> Unit, prefill: UserProfile? = null) {
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf(prefill?.height_cm?.toString() ?: "") }
    var weight by remember { mutableStateOf(prefill?.weight_kg?.toString() ?: "") }
    var age by remember { mutableStateOf(prefill?.age?.toString() ?: "") }
    var gender by remember { mutableStateOf(prefill?.gender ?: "") }
    var bodyFat by remember { mutableStateOf(prefill?.body_fat_pct?.toString() ?: "") }
    var muscleMass by remember { mutableStateOf(prefill?.muscle_mass_kg?.toString() ?: "") }
    var goal by remember { mutableStateOf(prefill?.goal ?: "") }
    var goalTarget by remember { mutableStateOf(prefill?.goal_target ?: "") }
    var trainingYears by remember { mutableStateOf(prefill?.training_years?.toString() ?: "") }
    var trainingFreq by remember { mutableStateOf(prefill?.training_freq_per_week?.toString() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("完善资料", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
            )
        },
        containerColor = DarkBg,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("📋 基础信息", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("这些信息用于更精准的训练建议，可稍后修改", fontSize = 12.sp, color = TextMuted)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = height, onValueChange = { height = it },
                        label = { Text("身高 (cm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = outlineFieldColors(), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text("体重 (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = outlineFieldColors(), modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age, onValueChange = { age = it },
                        label = { Text("年龄") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = outlineFieldColors(), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = trainingFreq, onValueChange = { trainingFreq = it },
                        label = { Text("每周训练次数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = outlineFieldColors(), modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Text("性别", fontSize = 13.sp, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("男", "女").forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = g },
                            label = { Text(g) },
                            colors = chipColors(),
                        )
                    }
                }
            }

            item {
                Text("目标", fontSize = 13.sp, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("半马成绩" to "half_marathon", "全马成绩" to "full_marathon", "塑形" to "shaping").forEach { (label, value) ->
                        FilterChip(
                            selected = goal == value,
                            onClick = { goal = value },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = chipColors(),
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = goalTarget, onValueChange = { goalTarget = it },
                    label = { Text("目标成绩（可选，如 2:00:00）") },
                    singleLine = true,
                    colors = outlineFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text("可选", fontSize = 13.sp, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bodyFat, onValueChange = { bodyFat = it },
                        label = { Text("体脂率 %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = outlineFieldColors(), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = muscleMass, onValueChange = { muscleMass = it },
                        label = { Text("肌肉量 kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = outlineFieldColors(), modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = trainingYears, onValueChange = { trainingYears = it },
                    label = { Text("训练经验（年，可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = outlineFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }

            if (error.isNotEmpty()) {
                item { Text(error, color = Danger, fontSize = 13.sp) }
            }

            item {
                val context = LocalContext.current
                var soundEnabled by remember { mutableStateOf(SoundManager.enabled) }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("🔊 音效", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text("点击、录音提示音", fontSize = 12.sp, color = TextMuted)
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            SoundManager.setEnabled(context, it)
                        },
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        val profile = UserProfile(
                            height_cm = height.toDoubleOrNull(),
                            weight_kg = weight.toDoubleOrNull(),
                            age = age.toIntOrNull(),
                            gender = gender.ifBlank { null },
                            body_fat_pct = bodyFat.toDoubleOrNull(),
                            muscle_mass_kg = muscleMass.toDoubleOrNull(),
                            goal = goal.ifBlank { null },
                            goal_target = goalTarget.ifBlank { null },
                            training_years = trainingYears.toDoubleOrNull(),
                            training_freq_per_week = trainingFreq.toIntOrNull(),
                            // 2026-08-20：保留已填的训练类型和比赛（prefill），避免账号页面编辑时丢字段
                            training_types = prefill?.training_types ?: emptyList(),
                            races = prefill?.races ?: emptyList(),
                        )
                        if (profile.height_cm == null && profile.weight_kg == null && profile.age == null
                            && profile.gender == null && profile.goal == null) {
                            onDone()
                            return@Button
                        }
                        scope.launch {
                            saving = true
                            GarminApi.updateUserProfile(profile).fold(
                                onSuccess = { onDone() },
                                onFailure = { e -> error = e.message ?: "保存失败"; saving = false },
                            )
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                    else Text("保存", fontSize = 15.sp)
                }
            }

            item {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("跳过", color = TextMuted)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AccentDim,
    selectedLabelColor = TextPrimary,
    containerColor = DarkInput,
    labelColor = TextMuted,
)
