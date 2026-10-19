package com.garmincoach.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garmincoach.app.data.model.ActivityDetail
import com.garmincoach.app.data.model.StrengthSet
import com.garmincoach.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<ActivityListItem?>(null) }
    var actionTarget by remember { mutableStateOf<ActivityListItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📋 训练记录", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
            )
        },
        containerColor = DarkBg,
    ) { padding ->
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            state.activities.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏋️", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无训练记录", fontSize = 16.sp, color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text("去对话页面跟运动助理说\"我今天练了力量\"来记录", fontSize = 13.sp, color = TextMuted)
                    }
                }
            }
            else -> {
                // 按日期分组
                val grouped = state.activities.groupBy { it.date }
                val sortedDates = grouped.keys.sortedDescending()

                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.padding(padding).fillMaxSize(),
                ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    sortedDates.forEach { date ->
                        val dayActivities = grouped[date] ?: emptyList()
                        item(key = date) {
                            DateHeader(date = date, count = dayActivities.size)
                        }
                        // 计算星期几
                        val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
                        val weekday = dayActivities.firstOrNull()?.weekday ?: ""
                        items(dayActivities.size) { index ->
                            val activity = dayActivities[index]
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                            ) {
                                ActivityCard(
                                    act = activity,
                                    weekday = weekday,
                                    onClick = { viewModel.loadActivityDetail(activity.activityId) },
                                    onLongClick = { actionTarget = activity },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
                }
            }
        }
    }

    // 详情弹窗（非编辑模式才弹）
    if (state.editingId == null) {
        state.selectedDetail?.let { detail ->
            ActivityDetailDialog(detail = detail, onDismiss = { viewModel.clearDetail() })
        }
    }

    // 长按菜单：编辑/删除
    actionTarget?.let { act ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(activityLabel(act.type), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.loadDetailForEdit(act.activityId)
                        actionTarget = null
                    }) { Text("编辑", color = Accent) }
                    TextButton(onClick = {
                        pendingDelete = act
                        actionTarget = null
                    }) { Text("删除", color = Danger) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionTarget = null }) { Text("取消", color = TextMuted) }
            },
            containerColor = DarkCard,
        )
    }

    // 编辑弹窗
    state.editingId?.let { aid ->
        ActivityEditDialog(
            initialSets = state.selectedDetail?.strength_sets ?: emptyList(),
            onDismiss = { viewModel.clearDetail() },
            onSave = { list -> viewModel.updateActivity(aid, list) },
        )
    }

    // 删除确认弹窗
    pendingDelete?.let { act ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除训练记录", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("确定删除这条「${activityLabel(act.type)}」记录吗？", color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteActivity(act.activityId)
                    pendingDelete = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消", color = TextMuted) }
            },
            containerColor = DarkCard,
        )
    }
}

// ── 日期分组标题 ──

@Composable
fun DateHeader(date: String, count: Int) {
    // 格式化日期显示
    val parts = date.split("-")
    val display = if (parts.size == 3) "${parts[1]}月${parts[2]}日" else date
    val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
    val weekday = try {
        val cal = java.util.Calendar.getInstance()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        weekdays[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    } catch (e: Exception) { "" }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$display 周$weekday",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.width(8.dp))
        Surface(color = DarkInput, shape = RoundedCornerShape(10.dp)) {
            Text(
                "${count}次训练",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
    }
}

// ── 活动卡片 ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityCard(act: ActivityListItem, weekday: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val icon = activityIcon(act.type)
    val color = activityColor(act.type)
    val label = activityLabel(act.type)

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 类型图标圆
            Surface(
                Modifier.size(44.dp),
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 22.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            // 中间信息
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            act.time ?: "",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 11.sp,
                            color = color,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (act.type == "strength_training" && !act.summary.isNullOrBlank()) {
                        Text(act.summary, fontSize = 13.sp, color = TextMuted)
                    } else {
                        act.distanceKm?.let {
                            Text("${it}km", fontSize = 13.sp, color = TextMuted)
                        }
                        act.durationMin?.let {
                            Text(formatDuration(it), fontSize = 13.sp, color = TextMuted)
                        }
                        act.avgHr?.let {
                            Text("心率${it}", fontSize = 13.sp, color = TextMuted)
                        }
                        act.avgPace?.let {
                            Text(it, fontSize = 13.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

// ── 活动详情弹窗 ──

@Composable
fun ActivityDetailDialog(detail: ActivityDetail, onDismiss: () -> Unit) {
    val color = activityColor(detail.type)
    val icon = activityIcon(detail.type)
    val label = activityLabel(detail.type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                detail.distance_km?.let { DetailRow("距离", "${it}km") }
                detail.duration_min?.let { DetailRow("时长", formatDuration(it)) }
                detail.avg_pace?.let { DetailRow("配速", it) }
                detail.avg_hr?.let { DetailRow("心率", "${it}/${detail.max_hr ?: "?"}") }
                detail.calories?.let { DetailRow("消耗", "${it}kcal") }
                detail.training_effect_aerobic?.let {
                    DetailRow("训练效果", "有氧 $it / 无氧 ${detail.training_effect_anaerobic ?: "?"}")
                }
                detail.cadence_avg?.let { DetailRow("步频", "${it}") }

                // Zone 5 占比
                val zones = detail.hr_zones
                if (zones != null) {
                    val total = zones.values.sum()
                    val z5 = zones["z5_sec"] ?: 0
                    if (total > 0) {
                        DetailRow("Zone 5", "${(z5 * 100 / total)}%")
                    }
                }

                // 分段配速
                val splits = detail.splits
                if (!splits.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("分段配速", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    splits.forEach { s ->
                        Text(
                            "  ${s.km}km: ${s.pace} · 心率${s.hr ?: "?"}",
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                    }
                }

                // 力量训练动作明细
                val strengthSets = detail.strength_sets
                if (!strengthSets.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("动作明细", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    strengthSets.forEach { s ->
                        val line = buildString {
                            append("  ${s.name}")
                            s.equipment?.let { append(" · ${it}") }
                            s.sets?.let { append(" · ${it}组") }
                            if (!s.reps_detail.isNullOrBlank()) {
                                append(" · 每组${s.reps_detail}次")
                            } else {
                                s.reps?.let { append(" · 每组${it}次") }
                            }
                            s.weight_kg?.let { append(" · ${it}kg") }
                        }
                        Text(line, fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        containerColor = DarkCard,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row {
        Text("$label: ", fontSize = 13.sp, color = TextMuted)
        Text(value, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

// ── 活动类型映射 ──

fun activityIcon(type: String?): String = when (type) {
    "running" -> "🏃"
    "cycling" -> "🚴"
    "walking" -> "🚶"
    "strength_training" -> "💪"
    "swimming" -> "🏊"
    else -> "🏋️"
}

fun activityLabel(type: String?): String = when (type) {
    "running" -> "跑步"
    "cycling" -> "骑行"
    "walking" -> "步行"
    "strength_training" -> "力量训练"
    "swimming" -> "游泳"
    else -> type ?: "运动"
}

fun activityColor(type: String?): Color = when (type) {
    "running" -> Color(0xFF22C55E)
    "cycling" -> Color(0xFF3B82F6)
    "walking" -> Color(0xFF8B5CF6)
    "strength_training" -> Color(0xFFF59E0B)
    "swimming" -> Color(0xFF06B6D4)
    else -> Accent
}

fun formatDuration(min: Double?): String {
    if (min == null) return ""
    val totalSec = (min * 60).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return if (s == 0) "${m}分" else "${m}分${s}秒"
}

@Composable
fun ActivityEditDialog(
    initialSets: List<StrengthSet>,
    onDismiss: () -> Unit,
    onSave: (List<StrengthSet>) -> Unit,
) {
    val sets = remember { mutableStateListOf<StrengthSet>().apply { addAll(initialSets) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑动作", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sets.isEmpty()) {
                    Text("暂无动作，点下方添加", color = TextMuted, fontSize = 13.sp)
                }
                sets.forEachIndexed { i, s ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = s.name,
                                onValueChange = { sets[i] = s.copy(name = it) },
                                label = { Text("动作名") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(4.dp))
                            OutlinedTextField(
                                value = s.sets?.toString() ?: "",
                                onValueChange = { sets[i] = s.copy(sets = it.toIntOrNull()) },
                                label = { Text("组数") },
                                singleLine = true,
                                modifier = Modifier.width(72.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = s.reps_detail ?: s.reps?.toString() ?: "",
                                onValueChange = { sets[i] = s.copy(reps_detail = it, reps = null) },
                                label = { Text("每组次数") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(4.dp))
                            OutlinedTextField(
                                value = s.equipment ?: "",
                                onValueChange = { sets[i] = s.copy(equipment = it) },
                                label = { Text("器械") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { sets.removeAt(i) }) {
                                Text("✕", color = Danger, fontSize = 16.sp)
                            }
                        }
                    }
                }
                TextButton(onClick = { sets.add(StrengthSet()) }) {
                    Text("+ 添加动作", color = Accent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sets.filter { it.name.isNotBlank() }) }) {
                Text("保存", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextMuted) }
        },
        containerColor = DarkCard,
    )
}
