package com.garmincoach.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.garmincoach.app.data.api.GarminApi
import com.garmincoach.app.ui.theme.*
import com.garmincoach.app.ui.dashboard.DashboardScreen
import com.garmincoach.app.ui.chat.ChatScreen
import com.garmincoach.app.ui.history.HistoryScreen
import com.garmincoach.app.ui.auth.AuthScreen
import com.garmincoach.app.ui.auth.WatchBindingScreen
import com.garmincoach.app.ui.auth.AccountScreen

/**
 * Garmin Coach 原生 Android App。
 *
 * 启动流程：
 * 1. 检查 SharedPreferences 中是否有 JWT token
 * 2. 无 token → AuthScreen（登录/注册）
 * 3. 有 token → 首页（仪表盘 | 对话 | 记录）
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "garmin_coach_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        // Legacy key（保留只用于一次性的 v1 迁移）
        private const val KEY_BOUND_LEGACY = "watch_bound"
        private const val KEY_MIGRATION_DONE = "onboarding_migrated_v1"

        // Per-user watch-bound state（2026-08-20 引入，让 logout 后同账号再登录不重走 watch binding）
        fun keyBound(userId: Int) = "bound_$userId"

        private fun isBound(prefs: SharedPreferences, userId: Int): Boolean =
            if (userId > 0) prefs.getBoolean(keyBound(userId), false) else false

        /**
         * 一次性迁移：旧版本（v0）的 watch_bound 是 per-device 的，
         * v1 改成 per-user。新版本首次启动时把 legacy 单用户状态迁移到当前 savedUserId 对应的 per-user key。
         * 完成后置 onboarding_migrated_v1=true，幂等。
         *
         * 注：2026-08-20 改造后，profile 状态（goal/基础信息）由服务端 `goal IS NOT NULL` 判定，
         * 不再用客户端 prefs 追踪，所以这里不再迁移 profile_filled。
         */
        private fun runLegacyMigration(prefs: SharedPreferences) {
            if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) return
            val savedUid = prefs.getInt(KEY_USER_ID, 0)
            if (savedUid > 0) {
                val ed = prefs.edit()
                if (prefs.getBoolean(KEY_BOUND_LEGACY, false)) {
                    ed.putBoolean(keyBound(savedUid), true)
                }
                ed.remove(KEY_BOUND_LEGACY)
                ed.apply()
            }
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ 通知权限
        requestNotificationPermission()
        // 定位权限（自动显示当地天气）
        requestLocationPermission()

        // 恢复登录态
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // v0 → v1：把旧的 per-device watch_bound/profile_filled 迁移到 per-user key
        runLegacyMigration(prefs)
        val savedToken = prefs.getString(KEY_TOKEN, null)
        val savedUserId = prefs.getInt(KEY_USER_ID, 0)
        val watchBound = isBound(prefs, savedUserId)

        if (!savedToken.isNullOrEmpty()) {
            GarminApi.authToken = savedToken
            GarminApi.currentUserId = savedUserId
        }

        // 检查是否从通知跳转来的
        val initialTab = intent?.getStringExtra("tab") ?: "dashboard"

        val isLoggedIn = !savedToken.isNullOrEmpty()

        setContent {
            GarminCoachTheme {
                var showAuth by remember { mutableStateOf(!isLoggedIn) }
                var showBinding by remember { mutableStateOf(isLoggedIn && !watchBound) }
                var showMain by remember { mutableStateOf(isLoggedIn && watchBound) }

                // 用于 logout 后跳回登录页（账号页的 onLoggedOut 冒泡到这）
                val goToLogin: () -> Unit = {
                    showAuth = true; showBinding = false; showMain = false
                }
                val goToMain: () -> Unit = {
                    showAuth = false; showBinding = false; showMain = true
                }

                if (showAuth) {
                    AuthScreen(
                        onLoginSuccess = {
                            // 保存 token + user_id
                            prefs.edit().apply {
                                putString(KEY_TOKEN, GarminApi.authToken)
                                putInt(KEY_USER_ID, GarminApi.currentUserId)
                                apply()
                            }
                            // 根据"刚登录的 user_id"的 watch-bound 状态决定下一步
                            // 基础信息 onboarding 改由服务端 goal IS NOT NULL 判定（首次登录直接进 dashboard，账号页"未完善"banner 引导填）
                            val uid = GarminApi.currentUserId
                            if (isBound(prefs, uid)) goToMain()
                            else { showAuth = false; showBinding = true; showMain = false }
                        },
                    )
                } else if (showBinding) {
                    WatchBindingScreen(
                        onSkip = {
                            prefs.edit().putBoolean(keyBound(GarminApi.currentUserId), true).apply()
                            showBinding = false
                            showMain = true
                        },
                    )
                } else if (showMain) {
                    MainApp(initialTab = initialTab, onLoggedOut = goToLogin)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getStringExtra("tab") ?: "dashboard"
        setContent {
            GarminCoachTheme {
                MainApp(initialTab = tab)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (!granted) {
                        Toast.makeText(this, "可在系统设置中开启推送通知", Toast.LENGTH_LONG).show()
                    }
                }.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
}

// ── 底部导航 ──

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val navItems = listOf(
    BottomNavItem("dashboard", "数据总览", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    BottomNavItem("chat", "对话", Icons.Filled.Mic, Icons.Outlined.Mic),
    BottomNavItem("history", "记录", Icons.Filled.History, Icons.Outlined.History),
)

@Composable
fun MainApp(initialTab: String = "dashboard", onLoggedOut: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var showBinding by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }

    // 账号页（从仪表盘右上角账号图标进入）
    if (showAccount) {
        AccountScreen(
            onBack = { showAccount = false },
            onOpenBinding = { showAccount = false; showBinding = true },
            // 透传到 MainActivity 提供的 goToLogin，让外层 setContent 状态机切回 AuthScreen
            onLoggedOut = onLoggedOut,
        )
        return
    }

    // 设备管理界面（从账号页"管理绑定设备"进入）
    if (showBinding) {
        WatchBindingScreen(onSkip = { showBinding = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = DarkCard) {
                navItems.forEach { item ->
                    val selected = selectedTab == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = item.route },
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Accent.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
        containerColor = DarkBg,
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (selectedTab) {
                "dashboard" -> DashboardScreen(
                    onOpenAccount = { showAccount = true },
                )
                "chat" -> ChatScreen()
                "history" -> HistoryScreen()
            }
        }
    }
}
