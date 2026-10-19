package com.garmincoach.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 和网页仪表盘一致的配色
val DarkBg = Color(0xFF0F172A)
val DarkCard = Color(0xFF1E293B)
val DarkInput = Color(0xFF334155)
val Accent = Color(0xFF8B5CF6)
val AccentDim = Color(0xFF6D28D9)
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFB45309)
val Danger = Color(0xFFEF4444)
val TextPrimary = Color(0xFFE2E8F0)
val TextMuted = Color(0xFF94A3B8)
val Border = Color(0xFF334155)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDim,
    secondary = Success,
    background = DarkBg,
    surface = DarkCard,
    surfaceVariant = DarkInput,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = Danger,
)

@Composable
fun GarminCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content,
    )
}

/** 安全档位 → 颜色 */
fun safetyColor(tier: String): Color = when (tier) {
    "REST" -> Color(0xFF7F1D1D)
    "EASY" -> Color(0xFF78350F)
    "MODERATE" -> Color(0xFF1E3A5F)
    "HARD" -> Color(0xFF14532D)
    "LONG" -> Color(0xFF4C1D95)
    else -> DarkCard
}

fun safetyTextColor(tier: String): Color = when (tier) {
    "REST" -> Color(0xFFFCA5A5)
    "EASY" -> Color(0xFFFCD34D)
    "MODERATE" -> Color(0xFF93C5FD)
    "HARD" -> Color(0xFF86EFAC)
    "LONG" -> Color(0xFFC4B5FD)
    else -> TextPrimary
}

fun hrvColor(status: String?): Color = when (status) {
    "POOR", "LOW" -> Danger
    "UNBALANCED" -> Warning
    else -> TextPrimary
}
