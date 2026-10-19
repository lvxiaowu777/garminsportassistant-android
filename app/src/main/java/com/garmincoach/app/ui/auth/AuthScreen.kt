package com.garmincoach.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garmincoach.app.data.api.GarminApi
import com.garmincoach.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 登录 / 注册页面。
 * 手机号 + 密码，支持切换登录/注册模式。
 */
@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Logo / 标题
            Text("🏃 Garmin Coach", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                if (isLogin) "登录你的账号" else "创建新账号",
                fontSize = 14.sp,
                color = TextMuted,
            )

            // 手机号
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.take(11); error = "" },
                label = { Text("手机号") },
                placeholder = { Text("请输入手机号") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                colors = outlineFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            // 密码
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = "" },
                label = { Text("密码") },
                placeholder = { Text("至少 6 位") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = outlineFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            // 错误提示
            AnimatedVisibility(visible = error.isNotEmpty()) {
                Text(error, color = Danger, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            // 按钮
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = ""
                        val result = if (isLogin) {
                            GarminApi.login(phone, password)
                        } else {
                            GarminApi.register(phone, password)
                        }
                        loading = false
                        result.fold(
                            onSuccess = {
                                if (it.ok) onLoginSuccess()
                                else error = if (isLogin) "登录失败" else "注册失败"
                            },
                            onFailure = { e -> error = e.message ?: "网络错误" },
                        )
                    }
                },
                enabled = phone.length == 11 && password.length >= 6 && !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (isLogin) "登录" else "注册", fontSize = 16.sp)
                }
            }

            // 切换登录/注册
            TextButton(onClick = {
                isLogin = !isLogin
                error = ""
            }) {
                Text(
                    if (isLogin) "没有账号？去注册" else "已有账号？去登录",
                    color = Accent,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun outlineFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Accent,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Border,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextMuted,
    focusedContainerColor = DarkInput,
    unfocusedContainerColor = DarkInput,
)
