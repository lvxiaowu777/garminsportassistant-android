package com.garmincoach.app.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garmincoach.app.data.model.*
import com.garmincoach.app.ui.theme.*
import com.valentinilk.shimmer.shimmer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onRefresh: () -> Unit = {},
    onOpenBinding: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val refreshRotation = rememberInfiniteTransition(label = "refresh").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rotation",
    )

    LaunchedEffect(state.loading) {
        if (!state.loading) onRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Garmin Coach", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        state.data?.let {
                            Text("${it.datetime} · ${it.period}", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(forceSync = true) }) {
                        Icon(
                            Icons.Default.Refresh, "同步",
                            tint = TextMuted,
                            modifier = if (state.loading) Modifier.rotate(refreshRotation.value) else Modifier,
                        )
                    }
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Default.AccountCircle, "查看账号", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
            )
        },
        containerColor = DarkBg,
    ) { padding ->
        when {
            state.loading && state.data == null -> {
                LoadingShimmer(Modifier.padding(padding))
            }
            state.error != null && state.data == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️ ${state.error}", color = TextMuted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.refresh() }) { Text("重试") }
                    }
                }
            }
            else -> {
                val data = state.data ?: return@Scaffold
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { viewModel.refresh(forceSync = true) },
                    modifier = Modifier.padding(padding),
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedCard("天气与跑步窗口", "今日天气、温度、湿度、AQI，帮你判断什么时候适合跑。") { WeatherCard(data.today) }
                        AnimatedCard { SafetyCard(data.safety) }
                        AnimatedCard("HRV 与静息心率", "心率变异性（HRV）和静息心率反映身体恢复状态，偏低说明需要休息。") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HrvCard(data.today, Modifier.weight(1f))
                                RestingHrCard(data.today, Modifier.weight(1f))
                            }
                        }
                        AnimatedCard("身体电量与步数", "身体电量当前值（醒时/当前瞬时）与今日步数。") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BodyBatteryCard(data.today, Modifier.weight(1f))
                                ActivityCard(data.today, Modifier.weight(1f))
                            }
                        }
                        AnimatedCard("昨夜睡眠", "睡眠时长、得分与深睡时长，影响今日恢复。") { SleepCard(data.today) }
                        AnimatedCard("7 天趋势", "近 7 天 HRV、静息心率、睡眠趋势。") { ChartCard(data.charts) }
                        AnimatedCard("本周摘要", "本周跑量、次数与平均指标。") { WeekSummaryCard(data.recent_7d) }
                        AnimatedCard("心肺与负荷", "VO₂Max、训练状态、ACWR 负荷，判断训练效果与受伤风险。") { FitnessCard(data.today) }
                        AnimatedCard("比赛预测", "基于历史数据预测的 5K/10K/半马/全马成绩。") { RaceCard(data.today) }
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

// ── 入场动画 + 点击缩放/水波纹 + 底部弹窗 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedCard(
    title: String? = null,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
    ) {
        if (title != null) {
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "cardScale")
            var showDetail by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .indication(interactionSource, LocalIndication.current)
                    .clickable { showDetail = true }
            ) {
                content()
            }
            if (showDetail) {
                ModalBottomSheet(
                    onDismissRequest = { showDetail = false },
                    containerColor = DarkCard,
                ) {
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        if (description != null) {
                            Text(description, fontSize = 14.sp, color = TextMuted, lineHeight = 20.sp)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        } else {
            content()
        }
    }
}

// ── 骨架屏 ──

@Composable
fun LoadingShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(16.dp).shimmer(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardShimmer()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardShimmer(Modifier.weight(1f))
            CardShimmer(Modifier.weight(1f))
        }
        CardShimmer(height = 220)
        CardShimmer(height = 80)
        CardShimmer(height = 80)
        CardShimmer(height = 80)
    }
}

@Composable
fun CardShimmer(modifier: Modifier = Modifier, height: Int = 120) {
    Card(
        modifier.fillMaxWidth().height(height.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
    ) {}
}

// ── 空状态 ──

@Composable
fun EmptyState(icon: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(text, fontSize = 12.sp, color = TextMuted)
    }
}

// ── 安全档位 ──

@Composable
fun SafetyCard(safety: SafetyInfo?) {
    if (safety == null) return
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("🛡️ 安全档位", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = safetyColor(safety.max_tier),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        safety.max_tier,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = safetyTextColor(safety.max_tier),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(safety.tier_description, fontSize = 14.sp, color = TextPrimary)
            }
            safety.blockers.forEach {
                Spacer(Modifier.height(4.dp))
                Text("⚠️ $it", fontSize = 13.sp, color = Warning)
            }
            safety.notes.forEach {
                Spacer(Modifier.height(4.dp))
                Text("💡 $it", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

// ── 睡眠 ──

@Composable
fun SleepCard(today: DayData?) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("💤 昨夜睡眠", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            if (today?.sleep_hours == null) {
                EmptyState("🌙", "暂无睡眠记录，同步手表查看睡眠信息")
            } else {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${today.sleep_hours}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(" 小时", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${today.sleep_score ?: "--"}", "睡眠得分")
                    StatItem("${today.sleep_deep_min ?: "--"}min", "深睡")
                }
            }
        }
    }
}

// ── HRV ──

@Composable
fun HrvCard(today: DayData?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("🫀 HRV 心率变异性", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            if (today?.hrv == null) {
                EmptyState("🫀", "暂无HRV数据，请同步手表获取记录")
            } else {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${today.hrv}", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = hrvColor(today.hrv_status),
                    )
                    today.hrv_status?.let {
                        Text(" ${hrvStatusLabel(it)}", fontSize = 13.sp, color = hrvColor(it), modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                Text("毫秒", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

// ── 静息心率 ──

@Composable
fun RestingHrCard(today: DayData?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("❤️ 静息心率", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            if (today?.resting_hr == null) {
                EmptyState("❤️", "暂无静息心率记录")
            } else {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${today.resting_hr}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(" bpm", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

// ── 身体电量 ──

@Composable
fun BodyBatteryCard(today: DayData?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("🔋 身体电量", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            if (today?.body_battery_now == null && today?.body_battery_wake == null) {
                EmptyState("🔋", "同步手表获取身体电量数据")
            } else {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${today?.body_battery_now ?: "--"}", "当前")
                    StatItem("${today?.body_battery_wake ?: "--"}", "醒时")
                }
            }
        }
    }
}

// ── 今日步数 ──

@Composable
fun ActivityCard(today: DayData?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("👣 今日步数", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            if (today?.steps == null) {
                EmptyState("👣", "暂无当日步数记录")
            } else {
                Spacer(Modifier.height(4.dp))
                Text("${today.steps}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("步", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

// ── 7 天趋势图（Canvas 升级版：渐变填充 + 圆点标记 + 日期标签）──

@Composable
fun ChartCard(charts: ChartData?) {
    if (charts == null || charts.hrv_trend.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("📈 7 天趋势", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(8.dp), shape = RoundedCornerShape(4.dp), color = Accent) {}
                    Spacer(Modifier.width(4.dp))
                    Text("HRV", fontSize = 11.sp, color = Accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(8.dp), shape = RoundedCornerShape(4.dp), color = Success) {}
                    Spacer(Modifier.width(4.dp))
                    Text("静息心率", fontSize = 11.sp, color = Success)
                }
            }

            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val hrvPts = charts.hrv_trend.map { it.value.toFloat() }
                val rhrPts = charts.rhr_trend.map { it.value.toFloat() }
                val all = hrvPts + rhrPts
                val maxVal = all.maxOrNull()?.times(1.15f) ?: 100f
                val minVal = all.minOrNull()?.times(0.85f) ?: 0f
                val range = (maxVal - minVal).coerceAtLeast(1f)
                val w = size.width
                val h = size.height
                val n = hrvPts.size.coerceAtLeast(2)

                fun pts(values: List<Float>): List<Offset> = values.mapIndexed { i, v ->
                    Offset(w * i / (n - 1), h * (1 - (v - minVal) / range))
                }

                fun drawFilledLine(values: List<Float>, color: Color) {
                    val points = pts(values)
                    if (points.size < 2) return
                    val fillPath = Path().apply {
                        moveTo(points.first().x, h)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, h)
                        close()
                    }
                    // 渐变填充
                    drawPath(
                        fillPath,
                        brush = Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.02f)),
                            startY = 0f, endY = h,
                        ),
                    )
                    // 折线
                    val linePath = Path().apply {
                        points.forEachIndexed { i, p ->
                            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        }
                    }
                    drawPath(linePath, color, style = Stroke(width = 2.5f))
                    // 圆点
                    points.forEach { p ->
                        drawCircle(color, radius = 4f, center = p)
                        drawCircle(Color.White.copy(alpha = 0.6f), radius = 2f, center = p)
                    }
                }

                drawFilledLine(hrvPts, Accent)
                drawFilledLine(rhrPts, Success)

                // X 轴日期
                charts.hrv_trend.forEachIndexed { i, pt ->
                    if (i % 2 == 0) {
                        val x = w * i / (n - 1)
                        drawContext.canvas.nativeCanvas.drawText(
                            pt.date.takeLast(5), x, h + 14f,
                            android.graphics.Paint().apply {
                                color = 0xFF64748B.toInt()
                                textSize = 26f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── 本周跑量 ──

@Composable
fun WeekSummaryCard(summary: WeekSummary?) {
    if (summary == null) return
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("${summary.total_distance_km} km", "7天跑量")
            StatItem("${summary.run_count} 次", "跑步次数")
            StatItem("${summary.total_duration_min} min", "总时长")
            StatItem("${summary.avg_hrv ?: "?"}", "平均 HRV")
        }
    }
}

// ── 天气 ──

@Composable
fun WeatherCard(today: DayData?) {
    if (today == null) return
    val hasNow = today.now_temp != null || today.now_text != null
    val hasForecast = today.weather_desc != null
    if (!hasNow && !hasForecast) return
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("📍 ${today.location ?: "当前位置"}", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(weatherIcon(today.now_text ?: today.weather_desc), fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(today.now_text ?: "实时天气", fontSize = 12.sp, color = TextMuted)
                }
            }

            // 当前实时温度 + 体感 + 是否在下雨
            if (today.now_temp != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${today.now_temp}°", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    today.now_feels_like?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("体感 ${it}°", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    if ((today.now_precip ?: 0.0) > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("☔ 正在下雨", fontSize = 13.sp, color = Accent, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
            }

            // 当天预报 + 实时 AQI
            if (hasForecast) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(today.weather_desc ?: "--", "今天")
                    StatItem("${today.weather_low ?: "--"}~${today.weather_high ?: "--"}°C", "今日温度")
                    StatItem("${today.weather_humidity ?: "--"}%", "湿度")
                    StatItem("${today.weather_aqi ?: "--"}", "AQI")
                }
            }
        }
    }
}

// ── 心肺 ──

@Composable
fun FitnessCard(today: DayData?) {
    val trainingLabel = today?.training_feedback?.let { trainingStatusLabel(it) }
    val hasFitness = today?.vo2max != null || trainingLabel != null ||
            today?.fitness_age != null || today?.acwr_ratio != null

    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("🫁 心肺 · 负荷", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            if (!hasFitness) {
                EmptyState("🫁", "训练数据不足，暂时无法判断训练状态")
            } else {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    today?.vo2max?.let { StatItem("$it", "VO₂Max") }
                    trainingLabel?.let { StatItem(it, "训练状态") }
                    today?.fitness_age?.let { StatItem("${it.toInt()}岁", "健身年龄") }
                    today?.acwr_ratio?.let { StatItem("$it", "ACWR") }
                }
            }
        }
    }
}

// ── 比赛预测 ──

@Composable
fun RaceCard(today: DayData?) {
    val hasRace = today?.race_5k != null || today?.race_10k != null ||
            today?.race_half != null || today?.race_full != null
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("🏁 比赛预测", fontSize = 12.sp, color = TextMuted, letterSpacing = 0.5.sp)
            if (!hasRace) {
                EmptyState("🏁", "积累跑步运动数据后生成赛事成绩预测")
            } else {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    today?.race_5k?.let { StatItem(it, "5K") }
                    today?.race_10k?.let { StatItem(it, "10K") }
                    today?.race_half?.let { StatItem(it, "半马") }
                    today?.race_full?.let { StatItem(it, "全马") }
                }
            }
        }
    }
}

// ── 工具 ──

fun trainingStatusLabel(feedback: String): String = when {
    feedback.startsWith("PRODUCTIVE") -> "高效训练"
    feedback.startsWith("OVERREACHING") -> "过度训练"
    feedback.startsWith("STRAINED") -> "身体紧张"
    feedback.startsWith("MAINTAINING") -> "保持"
    feedback.startsWith("UNPRODUCTIVE") -> "无效训练"
    feedback.startsWith("PEAKING") -> "巅峰状态"
    feedback.startsWith("RECOVERY") -> "恢复中"
    feedback.startsWith("DETRAINING") -> "体能下降"
    else -> feedback
}

fun hrvStatusLabel(status: String?): String = when (status?.uppercase()) {
    "BALANCED" -> "平衡"
    "UNBALANCED" -> "失衡"
    "LOW" -> "偏低"
    "POOR" -> "较差"
    else -> status ?: ""
}

fun weatherIcon(desc: String?): String = when {
    desc.isNullOrBlank() -> ""
    desc.contains("雷") -> "⛈️"
    desc.contains("大雨") || desc.contains("暴雨") -> "🌧️"
    desc.contains("中雨") -> "🌧️"
    desc.contains("小雨") || desc.contains("雨") -> "🌦️"
    desc.contains("雪") -> "❄️"
    desc.contains("雾") || desc.contains("霾") -> "🌫️"
    desc.contains("晴") -> "☀️"
    desc.contains("多云") -> "⛅"
    desc.contains("阴") -> "☁️"
    else -> "🌤️"
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}
