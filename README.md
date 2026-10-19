# Garmin Coach — Android App（原生 Compose）

> 纯 Kotlin + Jetpack Compose 原生 UI。不套网页，所有界面都是 Kotlin 写的。
> 通过 HTTP 调 FastAPI 拿数据，FCM 收推送。

## 架构

```
App 启动
  ├── MainActivity (Compose)
  │     └── 底部导航：仪表盘 | 对话 | 记录
  ├── GarminApi (OkHttp) ─── HTTP ─── FastAPI (localhost:8766)
  └── FCMService ─── 后台推送 ─── Google FCM
```

## 源代码结构

```
app/src/main/java/com/garmincoach/app/
├── MainActivity.kt              # 入口：底部导航 + 三个 tab
├── GarminCoachApp.kt            # Application：通知渠道 + 推送初始化
│
├── data/
│   ├── model/Models.kt          # 所有数据类（DashboardData, SafetyInfo 等）
│   └── api/GarminApi.kt         # HTTP 客户端：调 FastAPI，JSON → Kotlin 对象
│
├── ui/
│   ├── theme/Theme.kt           # 配色方案（和网页版一致）
│   ├── dashboard/
│   │   ├── DashboardScreen.kt   # 仪表盘 UI：安全档位/睡眠/HRV/趋势图/天气
│   │   └── DashboardViewModel.kt
│   ├── chat/
│   │   ├── ChatScreen.kt        # 对话 UI：气泡消息 + 快捷提问
│   │   └── ChatViewModel.kt
│   └── history/
│       ├── HistoryScreen.kt     # 训练记录列表 + 详情弹窗
│       └── HistoryViewModel.kt
│
└── push/
    ├── FCMService.kt            # 后台接收 FCM 推送 → 弹出通知
    └── PushManager.kt           # FCM Token 获取 + 注册到后端
```

## 页面说明

### 📊 仪表盘
- 安全档位卡片（带颜色标识 REST/EASY/MODERATE/HARD/LONG）
- 睡眠卡片（时长/得分/深睡）
- HRV + 静息心率卡片（HRV 状态带颜色）
- 7 天 HRV/静息心率趋势图（Canvas 手绘折线）
- 本周跑量统计
- 天气（温度/湿度/AQI）
- 心肺 & 比赛预测（VO2Max/训练状态/健身年龄/半马预测）

### 💬 对话
- 聊天气泡界面（用户蓝色/Jarvis 深色）
- 快捷提问按钮
- 非流式回复（后续可改 WebSocket 流式）

### 📋 记录
- 最近 30 天活动列表
- 点击弹出详情：分段配速、心率区间

## 首次配置

### 1. 用 Android Studio 打开

```
File → Open → C:\Users\boobyboy\study\code\garmin-coach-android
```

等 Gradle sync（第一次 5-10 分钟下载依赖）。

### 2. Firebase

1. [Firebase Console](https://console.firebase.google.com/) → 创建项目
2. 添加 Android 应用 → 包名 `com.garmincoach.app`
3. 下载 `google-services.json` → 放到 `app/` 目录
4. 项目设置 → 服务账号 → 生成新私钥 → 下载 JSON
5. 放到 `C:\Users\boobyboy\study\知识库\试用\garmin-coach\config\firebase-service-account.json`

### 3. 修改服务器地址

`data/api/GarminApi.kt` 第 18 行：

```kotlin
var BASE_URL = "http://10.0.2.2:8766"  // 模拟器连宿主机
// var BASE_URL = "http://192.168.x.x:8766"  // 真机连电脑 WiFi
```

## 运行

1. 电脑启动 Python 服务：`python src/server.py`
2. Android Studio 点 ▶ Run
3. 模拟器/手机自动安装

## 推送流程

```
Python 定时任务 (每天 8:07 / 19:07)
  → check_and_notify() 检查训练数据
  → Firebase Admin SDK 发消息
  → Google FCM → 手机 FCMService
  → 通知栏弹出 → 点击 → MainActivity 跳到对应 tab
```
