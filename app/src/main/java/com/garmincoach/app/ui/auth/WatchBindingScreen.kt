package com.garmincoach.app.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garmincoach.app.data.api.GarminApi
import com.garmincoach.app.data.model.WatchBinding
import com.garmincoach.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 手表品牌绑定页。
 * 登录后首次使用需要绑定手表，后续可在设置中管理。
 */
@Composable
fun WatchBindingScreen(
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var bindings by remember { mutableStateOf<List<WatchBinding>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedBrand by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("cn") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var bindingInProgress by remember { mutableStateOf(false) }
    var needsMfa by remember { mutableStateOf(false) }
    var mfaCode by remember { mutableStateOf("") }

    // 加载已绑定列表
    LaunchedEffect(Unit) {
        loading = true
        GarminApi.getWatchList().fold(
            onSuccess = { bindings = it },
            onFailure = { error = it.message ?: "加载失败" },
        )
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = Accent)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("⌚", fontSize = 48.sp)
                Text("绑定手表", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "连接你的手表，获取训练数据",
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )

                // 已绑定列表
                if (bindings.isNotEmpty()) {
                    Text("已绑定", fontSize = 13.sp, color = TextMuted)
                    bindings.forEach { b ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    GarminApi.selectWatch(b.brand).fold(
                                        onSuccess = {
                                            bindings = bindings.map { it.copy(enabled = it.brand == b.brand) }
                                        },
                                        onFailure = { e -> error = e.message ?: "切换失败" },
                                    )
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(brandLabel(b.brand), color = TextPrimary, fontWeight = FontWeight.Medium)
                                        Text(b.account, color = TextMuted, fontSize = 13.sp)
                                        Text(
                                            if (b.enabled) "● 当前设备" else "点击切换",
                                            color = if (b.enabled) Accent else TextMuted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    RadioButton(
                                        selected = b.enabled,
                                        onClick = {
                                            scope.launch {
                                                GarminApi.selectWatch(b.brand).fold(
                                                    onSuccess = {
                                                        bindings = bindings.map { it.copy(enabled = it.brand == b.brand) }
                                                    },
                                                    onFailure = { e -> error = e.message ?: "切换失败" },
                                                )
                                            }
                                        },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            GarminApi.unbindWatch(b.brand).fold(
                                                onSuccess = {
                                                    bindings = bindings.filter { it.brand != b.brand }
                                                },
                                                onFailure = { e -> error = e.message ?: "解绑失败" },
                                            )
                                        }
                                    }) { Text("解绑", color = Danger, fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                }

                // 添加绑定按钮
                Button(
                    onClick = { showDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("添加手表", fontSize = 15.sp) }

                // 跳过
                TextButton(onClick = onSkip) {
                    Text("稍后再说", color = TextMuted, fontSize = 14.sp)
                }
            }
        }

        // 绑定弹窗
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false; error = "" },
                containerColor = DarkCard,
                title = { Text("添加手表", color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 品牌选择
                        Text("品牌", color = TextMuted, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("garmin" to "佳明", "coros" to "高驰").forEach { (brand, label) ->
                                FilterChip(
                                    selected = selectedBrand == brand,
                                    onClick = { selectedBrand = brand; error = "" },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentDim,
                                        selectedLabelColor = TextPrimary,
                                        containerColor = DarkInput,
                                        labelColor = TextMuted,
                                    ),
                                )
                            }
                        }
                        // 区服（高驰账号按注册地区分服务器，中国账号选"中国"）
                        if (selectedBrand == "coros") {
                            Text("区服", color = TextMuted, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("cn" to "中国", "asia" to "亚洲", "eu" to "欧洲", "us" to "美国").forEach { (r, label) ->
                                    FilterChip(
                                        selected = region == r,
                                        onClick = { region = r },
                                        label = { Text(label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentDim,
                                            selectedLabelColor = TextPrimary,
                                            containerColor = DarkInput,
                                            labelColor = TextMuted,
                                        ),
                                    )
                                }
                            }
                        }
                        // 账号
                        OutlinedTextField(
                            value = account,
                            onValueChange = { account = it; error = "" },
                            label = { Text("账号") },
                            placeholder = { Text("邮箱或手机号") },
                            singleLine = true,
                            colors = outlineFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 密码（佳明/高驰需要）
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = "" },
                            label = { Text("密码") },
                            placeholder = { Text("手表账号密码") },
                            singleLine = true,
                            colors = outlineFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // MFA 验证码（后端判断需要时才显示）
                        if (needsMfa) {
                            OutlinedTextField(
                                value = mfaCode,
                                onValueChange = { mfaCode = it; error = "" },
                                label = { Text("两步验证码") },
                                placeholder = { Text("手机/邮箱收到的 6 位验证码") },
                                singleLine = true,
                                colors = outlineFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (error.isNotEmpty()) {
                            Text(error, color = Danger, fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                bindingInProgress = true
                                error = ""
                                if (needsMfa) {
                                    // 提交两步验证码
                                    GarminApi.bindWatchMfa(mfaCode).fold(
                                        onSuccess = {
                                            showDialog = false
                                            needsMfa = false
                                            mfaCode = ""
                                            account = ""
                                            password = ""
                                            selectedBrand = ""
                                            GarminApi.getWatchList().fold(
                                                onSuccess = { bindings = it },
                                                onFailure = {},
                                            )
                                        },
                                        onFailure = { e -> error = e.message ?: "验证失败" },
                                    )
                                } else {
                                    // 首次绑定
                                    GarminApi.bindWatch(selectedBrand, account, password, region).fold(
                                        onSuccess = { r ->
                                            if (r.needsMfa) {
                                                needsMfa = true
                                                error = r.message
                                            } else {
                                                showDialog = false
                                                account = ""
                                                password = ""
                                                selectedBrand = ""
                                                GarminApi.getWatchList().fold(
                                                    onSuccess = { bindings = it },
                                                    onFailure = {},
                                                )
                                            }
                                        },
                                        onFailure = { e -> error = e.message ?: "绑定失败" },
                                    )
                                }
                                bindingInProgress = false
                            }
                        },
                        enabled = if (needsMfa)
                            mfaCode.isNotEmpty() && !bindingInProgress
                        else
                            selectedBrand.isNotEmpty() && account.isNotEmpty() && !bindingInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        if (bindingInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("绑定")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false; error = "" }) {
                        Text("取消", color = TextMuted)
                    }
                },
            )
        }
    }
}

private fun brandLabel(brand: String): String = when (brand) {
    "garmin" -> "佳明 Garmin"
    "coros" -> "高驰 Coros"
    "apple" -> "Apple Watch"
    "huawei" -> "华为手表"
    "xiaomi" -> "小米手环"
    else -> brand
}
