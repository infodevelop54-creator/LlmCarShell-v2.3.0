package com.example.llmcar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.llmcar.service.LlmStatus
import com.example.llmcar.ui.components.OrbState
import com.example.llmcar.ui.components.SoundBars
import com.example.llmcar.ui.components.VoiceOrb
import com.example.llmcar.util.AppSignals
import com.example.llmcar.voice.VoiceEvent
import com.example.llmcar.voice.VoiceInputController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    serviceStatus: LlmStatus,
    voiceStatus: String,
    autoStartMic: Boolean,
    onAutoMicConsumed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    val vm: ChatViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val settings = remember { vm.settingsRepo() }

    var inputText by remember { mutableStateOf("") }
    var overlayVisible by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf(vm.activePresetName()) }

    LaunchedEffect(autoStartMic) {
        if (autoStartMic) { overlayVisible = true; onAutoMicConsumed() }
    }
    LaunchedEffect(Unit) {
        AppSignals.presetChanged.collect { presetName = vm.activePresetName() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("LLM Car Shell", fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        when (serviceStatus) {
                            is LlmStatus.Idle -> "Движок не запущен — загрузите модель в настройках"
                            is LlmStatus.Loading -> "Загрузка модели…"
                            is LlmStatus.Ready -> "Готово"
                            is LlmStatus.Error -> "Ошибка: ${serviceStatus.message}"
                        },
                        fontSize = 13.sp,
                        color = when (serviceStatus) {
                            is LlmStatus.Ready -> Color(0xFF4CAF50)
                            is LlmStatus.Error -> Color(0xFFF44336)
                            else -> Color(0xFFFFC107)
                        }
                    )
                    Text("Режим: $presetName", fontSize = 12.sp, color = Color(0xFF7C4DFF))
                    if (voiceStatus.isNotBlank() && voiceStatus != "Не активно")
                        Text(voiceStatus, fontSize = 11.sp, color = Color(0xFF9090A0))
                }
                IconButton(onClick = onOpenDashboard) {
                    Icon(Icons.Default.Speed, "Дашборд", tint = Color.White)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "Настройки", tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.messages) { m -> MessageBubble(m) }
                if (vm.liveText.isNotBlank() && vm.isSending) {
                    item { LiveStreamBubble(vm.liveText) }
                }
            }

            vm.errorText?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFFF44336))
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Введите сообщение…") },
                    enabled = serviceStatus is LlmStatus.Ready && !vm.isSending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        val t = inputText
                        if (t.isNotBlank()) { inputText = ""; vm.send(t) }
                    }),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = { overlayVisible = true },
                    enabled = serviceStatus is LlmStatus.Ready
                ) {
                    Icon(Icons.Default.Mic, "Голос", tint = Color(0xFF7C4DFF))
                }

                IconButton(onClick = { settings.setTtsEnabled(!settings.ttsEnabled) }) {
                    Icon(
                        imageVector = if (settings.ttsEnabled)
                            Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Озвучка",
                        tint = if (settings.ttsEnabled) Color(0xFF4CAF50)
                               else Color(0xFF9090A0)
                    )
                }

                FilledIconButton(
                    onClick = {
                        val t = inputText
                        if (t.isNotBlank()) { inputText = ""; vm.send(t) }
                    },
                    enabled = serviceStatus is LlmStatus.Ready
                            && !vm.isSending && inputText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, "Отправить")
                }
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            VoiceOverlay(
                serviceStatus = serviceStatus,
                settings = settings,
                viewModel = vm,
                onDismiss = { overlayVisible = false }
            )
        }
    }
}

@Composable
private fun VoiceOverlay(
    serviceStatus: LlmStatus,
    settings: com.example.llmcar.data.SettingsRepository,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceController = remember { VoiceInputController(context) }

    var inputText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Говорите или введите текст") }
    var rmsLevel by remember { mutableStateOf(0f) }
    var isListening by remember { mutableStateOf(false) }
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    var ttsSpeaking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppSignals.ttsSpeaking.collect { ttsSpeaking = it }
    }

    fun startListening() {
        if (isListening || ttsSpeaking) return
        isListening = true
        statusText = "Слушаю…"
        rmsLevel = 0f
        voiceJob = scope.launch {
            voiceController.listenOnce().collect { ev ->
                when (ev) {
                    is VoiceEvent.ReadyForSpeech -> statusText = "Слушаю…"
                    is VoiceEvent.RmsChanged -> {
                        rmsLevel = ((ev.rmsDb + 2f) / 14f).coerceIn(0f, 1f)
                    }
                    is VoiceEvent.Partial -> {
                        inputText = ev.text
                        statusText = ev.text
                    }
                    is VoiceEvent.Final -> {
                        isListening = false
                        inputText = ev.text
                        if (ev.text.isNotBlank() && settings.voiceAutoSend) {
                            viewModel.send(ev.text)
                            inputText = ""
                        } else if (ev.text.isNotBlank()) {
                            statusText = "Нажмите «Отправить»"
                        }
                    }
                    is VoiceEvent.Error -> {
                        isListening = false
                        statusText = "Ошибка: ${ev.message}"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (serviceStatus is LlmStatus.Ready) startListening()
    }

    val currentOrb = when {
        isListening -> OrbState.Listening
        viewModel.isSending -> OrbState.Thinking
        ttsSpeaking -> OrbState.Speaking
        else -> OrbState.Idle
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceJob?.cancel()
            voiceController.stop()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E1A)) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0E0E1A), Color(0xFF15152A))))
                .padding(20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Голосовой ввод", fontSize = 18.sp, color = Color.White,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val displayText = when {
                        viewModel.liveText.isNotBlank() -> viewModel.liveText
                        viewModel.messages.lastOrNull()?.role == "assistant" ->
                            viewModel.messages.last().content
                        else -> ""
                    }
                    if (displayText.isNotBlank()) {
                        Text(displayText, fontSize = 16.sp, color = Color(0xFFE0E0E0),
                            modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(24.dp))
                    } else if (viewModel.isSending) {
                        Text("Думаю…", fontSize = 16.sp, color = Color(0xFFFFC107))
                        Spacer(Modifier.height(24.dp))
                    }

                    VoiceOrb(state = currentOrb)

                    Spacer(Modifier.height(20.dp))

                    SoundBars(
                        amplitude = rmsLevel,
                        active = currentOrb == OrbState.Listening ||
                                currentOrb == OrbState.Speaking
                    )

                    if (displayText.isBlank() && !viewModel.isSending) {
                        Spacer(Modifier.height(20.dp))
                        Text(statusText, fontSize = 13.sp, color = Color(0xFF9090A0))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Введите сообщение…") },
                    enabled = serviceStatus is LlmStatus.Ready && !viewModel.isSending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF7C4DFF),
                        unfocusedBorderColor = Color(0xFF3A3A5A),
                        cursorColor = Color(0xFF7C4DFF)
                    )
                )
                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (!isListening) startListening() else voiceController.stop()
                    },
                    enabled = !viewModel.isSending
                ) {
                    Icon(Icons.Default.Mic, "Микрофон",
                        tint = if (isListening) Color(0xFFF44336) else Color(0xFF7C4DFF))
                }

                FilledIconButton(
                    onClick = {
                        val t = inputText
                        if (t.isNotBlank()) { inputText = ""; viewModel.send(t) }
                    },
                    enabled = serviceStatus is LlmStatus.Ready
                            && !viewModel.isSending && inputText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, "Отправить")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.primary else Color(0xFF2A2A3E)
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Box(Modifier.padding(12.dp)) {
                Text(msg.content, color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun LiveStreamBubble(text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            color = Color(0xFF2A2A3E).copy(alpha = 0.7f),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Box(Modifier.padding(12.dp)) {
                Text(text, color = Color(0xFFE0E0E0), fontSize = 15.sp)
            }
        }
    }
}