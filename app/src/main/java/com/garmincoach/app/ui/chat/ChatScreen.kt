package com.garmincoach.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garmincoach.app.data.api.GarminApi
import android.content.Context
import com.garmincoach.app.util.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.garmincoach.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showDateMenu by remember { mutableStateOf(false) }

    // 语音输入：按住麦克风按钮录音，松手发送语音条
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var recordStartTime by remember { mutableStateOf(0L) }
    var micPressStart by remember { mutableLongStateOf(0L) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var peakAmplitude by remember { mutableFloatStateOf(0f) }
    var ampJob by remember { mutableStateOf<Job?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            ampJob?.cancel()
        }
    }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = r
            audioFile = file
            recordStartTime = System.currentTimeMillis()
            isRecording = true
            peakAmplitude = 0f
            SoundManager.playRecordStart()
            ampJob?.cancel()
            ampJob = scope.launch {
                while (true) {
                    val a = try { (recorder?.maxAmplitude ?: 0).toFloat() } catch (_: Exception) { 0f }
                    amplitude = a
                    if (a > peakAmplitude) peakAmplitude = a
                    delay(80)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "录音启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        ampJob?.cancel()
        ampJob = null
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        isRecording = false
        SoundManager.playRecordStop()
        val file = audioFile ?: return
        audioFile = null
        // 按住但几乎没说话：不发送空音频（否则服务器 ASR 返回空 → 500）
        val heldMs = System.currentTimeMillis() - recordStartTime
        if (heldMs < 1000) {
            file.delete()
            Toast.makeText(context, "说话时间太短，请按住说话", Toast.LENGTH_SHORT).show()
            return
        }
        if (peakAmplitude < 200) {
            file.delete()
            Toast.makeText(context, "没听到声音，请对着麦克风说话", Toast.LENGTH_SHORT).show()
            return
        }
        val durationSec = (heldMs / 1000).toInt().coerceAtLeast(1)
        viewModel.sendVoice(file, durationSec)
    }

    fun cancelRecording() {
        ampJob?.cancel()
        ampJob = null
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        isRecording = false
        audioFile?.delete()
        audioFile = null
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else
            Toast.makeText(context, "需要麦克风权限", Toast.LENGTH_SHORT).show()
    }

    fun onMicPress() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        // 通话中（微信语音/视频、普通电话）麦克风被占用，录到的是空音频 → ASR 失败 → 发送失败
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = am.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            Toast.makeText(context, "正在通话中，请挂断后再说话", Toast.LENGTH_SHORT).show()
            return
        }
        startRecording()
    }

    fun playVoice(url: String) {
        // 点正在播放的语音 → 暂停
        if (playingUrl == url) {
            mediaPlayer?.pause()
            playingUrl = null
            return
        }
        try {
            mediaPlayer?.release()
            val mp = MediaPlayer().apply {
                setDataSource(GarminApi.BASE_URL + url)
                setOnPreparedListener {
                    it.start()
                    playingUrl = url
                }
                setOnCompletionListener {
                    it.release()
                    playingUrl = null
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Toast.makeText(context, "播放失败", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💬 运动助理", fontWeight = FontWeight.Bold)
                        if (state.dates.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showDateMenu = true },
                                ) {
                                    Text(
                                        if (state.selectedDate.isBlank()) "今天" else state.selectedDate,
                                        fontSize = 13.sp,
                                        color = Accent,
                                    )
                                    Icon(Icons.Default.ArrowDropDown, "选择日期", tint = Accent)
                                }
                                DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("今天", fontSize = 13.sp) },
                                        onClick = { viewModel.selectDate(""); showDateMenu = false },
                                    )
                                    state.dates.forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text(d, fontSize = 13.sp) },
                                            onClick = { viewModel.selectDate(d); showDateMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
            )
        },
        bottomBar = {
            Column {
                // 录音中：输入栏上方弹出红色提示条（真实声波 + 松手提示）
                AnimatedVisibility(
                    visible = isRecording,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    RecordingBanner(amplitude)
                }
                ChatInputBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() && !state.sending) {
                            SoundManager.playClick()
                            viewModel.send(inputText)
                            inputText = ""
                        }
                    },
                    sending = state.sending,
                    isRecording = isRecording,
                    amplitude = amplitude,
                    onMicPress = {
                        micPressStart = System.currentTimeMillis()
                        onMicPress()
                    },
                    onMicRelease = {
                        if (System.currentTimeMillis() - micPressStart < 300) cancelRecording()
                        else stopRecording()
                    },
                )
            }
        },
        containerColor = DarkBg,
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.messages.size <= 1) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("今晚跑不跑？", "状态怎么样？", "记录力量训练").forEach { q ->
                        SuggestionChip(
                            onClick = { viewModel.send(q) },
                            label = { Text(q, fontSize = 12.sp) },
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(state.messages) { index, msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    ) {
                        ChatBubble(
                            msg = msg,
                            playingUrl = playingUrl,
                            onVoiceClick = { playVoice(it.audioUrl) },
                            onVoiceLongClick = { viewModel.toggleTranscript(index) },
                            isLatestAi = index == state.messages.lastIndex && msg.role == "assistant",
                        )
                    }
                }
                if (state.sending) {
                    item {
                        Row(Modifier.padding(12.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("思考中…", fontSize = 13.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    isRecording: Boolean,
    amplitude: Float,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
) {
    Surface(color = DarkCard, shadowElevation = 8.dp, modifier = Modifier.imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("问运动助理...", color = TextMuted) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkInput,
                    unfocusedContainerColor = DarkInput,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                    focusedIndicatorColor = Accent,
                    unfocusedIndicatorColor = Border,
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            // 麦克风按钮：按住说话，松手发送
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (isRecording) Danger else DarkInput)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onMicPress()
                                tryAwaitRelease()
                                onMicRelease()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "按住说话",
                    tint = if (isRecording) Color.White else Accent,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSend, enabled = !sending) {
                Icon(
                    Icons.Default.Send, "发送",
                    tint = if (sending) TextMuted else Accent,
                )
            }
        }
    }
}

/** 录音中提示条：红色醒目背景 + 真实声波振幅 + 松手提示 */
@Composable
fun RecordingBanner(amplitude: Float) {
    // MediaRecorder 振幅范围 ~0-32767，映射为声波条高度（带一点噪声感，视觉更像真实波形）
    val level = (amplitude / 32767f).coerceIn(0f, 1f)
    Surface(
        color = Danger,
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // 真实振幅声波：5 根条，高度随音量实时变化
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(5) { i ->
                    // 相邻条相位错开，随音量整体放大/缩小
                    val h = (10 + level * 26 + (i % 3) * 3).dp
                    Box(
                        Modifier
                            .size(width = 4.dp, height = h)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "正在录音… 松开发送",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun RecordingWave(color: Color) {
    val transition = rememberInfiniteTransition(label = "recordingWave")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { i ->
            val height by transition.animateFloat(
                initialValue = 6f,
                targetValue = if (i % 2 == 0) 20f else 10f,
                animationSpec = infiniteRepeatable(
                    tween(if (i % 2 == 0) 400 else 300, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .size(width = 3.dp, height = height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun ChatBubble(
    msg: ChatMsg,
    playingUrl: String?,
    onVoiceClick: (ChatMsg) -> Unit,
    onVoiceLongClick: (ChatMsg) -> Unit,
    isLatestAi: Boolean = false,
) {
    val isUser = msg.role == "user"
    val displayContent = msg.content
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 12.dp,
                    )
                )
                .background(if (isUser) Accent else DarkCard)
                .padding(12.dp)
        ) {
            Column {
                if (msg.type == "voice") {
                    VoiceBubble(msg, isUser, playingUrl, onVoiceClick, onVoiceLongClick)
                } else {
                    Text(
                        displayContent,
                        color = if (isUser) Color.White else TextPrimary,
                        fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
                if (msg.time.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        msg.time,
                        fontSize = 10.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.6f) else TextMuted.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceBubble(
    msg: ChatMsg,
    isUser: Boolean,
    playingUrl: String?,
    onVoiceClick: (ChatMsg) -> Unit,
    onVoiceLongClick: (ChatMsg) -> Unit,
) {
    val fg = if (isUser) Color.White else TextPrimary
    val isPlaying = playingUrl == msg.audioUrl
    val rippleTransition = rememberInfiniteTransition(label = "voiceRipple")
    val rippleScale by rippleTransition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rippleScale",
    )
    val rippleAlpha by rippleTransition.animateFloat(
        initialValue = 0.45f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "rippleAlpha",
    )
    Column {
        Row(
            Modifier.combinedClickable(
                onClick = { onVoiceClick(msg) },
                onLongClick = { onVoiceLongClick(msg) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = rippleScale
                                scaleY = rippleScale
                                alpha = rippleAlpha
                            }
                            .clip(CircleShape)
                            .background(Accent)
                    )
                }
                if (isPlaying) {
                    Icon(Icons.Default.Pause, "暂停", tint = fg, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, "播放", tint = fg, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "${msg.durationSec}''",
                color = if (isPlaying) Accent else fg,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (msg.showTranscript && msg.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(msg.content, color = fg, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
