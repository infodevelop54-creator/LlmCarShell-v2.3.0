package com.example.llmcar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.llmcar.data.PresetRepository
import com.example.llmcar.data.SettingsRepository
import com.example.llmcar.service.LlmStatus
import com.example.llmcar.ui.components.OrbState
import com.example.llmcar.ui.components.SoundBars
import com.example.llmcar.ui.components.VoiceOrb
import com.example.llmcar.ui.theme.OverlayStyle
import com.example.llmcar.ui.theme.PaletteColors
import com.example.llmcar.ui.theme.VisualTheme
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
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel()

    // Отдельный экземпляр настроек — читает актуальные визуальные параметры
    // при каждой композиции ChatScreen (после возврата из настроек).
    val visualSettings = remember { SettingsRepository(context.applicationContext) }
    val palette = VisualTheme.palette(visualSettings.accentHue)

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
            // ===================== Верхняя панель =====================
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
                    Text("Режим: $presetName", fontSize = 12.sp, color = palette.accent)
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

            // ===================== Сообщения =====================
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

            // ===================== Пилюля ввода =====================
            GeminiInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    val t = inputText.trim()
                    if (t.isNotBlank()) {
                        inputText = ""
                        vm.send(t)
                    }
                },
                onVoiceClick = { overlayVisible = true },
                ttsEnabled = visualSettings.ttsEnabled,
                onTtsToggle = { visualSettings.setTtsEnabled(!visualSettings.ttsEnabled) },
                currentPresetId = visualSettings.currentPresetId,
                onPresetSelected = { id ->
                    visualSettings.setPresetId(id)
                    AppSignals.firePresetChanged()
                },
                enabled = serviceStatus is LlmStatus.Ready && !vm.isSending,
                palette = palette,
                pillHeightDp = VisualTheme.pillHeight(visualSettings.pillSize),
                presetIcon = VisualTheme.presetIcon(visualSettings.presetIconStyle)
            )
        }

        // ===================== Голосовой оверлей =====================
        AnimatedVisibility(
            visible = overlayVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            GeminiVoiceOverlay(
                serviceStatus = serviceStatus,
                settings = visualSettings,
                viewModel = vm,
                onDismiss = { overlayVisible = false },
                palette = palette,
                overlayStyle = visualSettings.overlayStyle
            )
        }
    }
}

// =============================================================
// Пилюля ввода в стиле Gemini
// =============================================================

@Composable
private fun GeminiInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    ttsEnabled: Boolean,
    onTtsToggle: () -> Unit,
    currentPresetId: String,
    onPresetSelected: (String) -> Unit,
    enabled: Boolean,
    palette: PaletteColors,
    pillHeightDp: Int,
    presetIcon: ImageVector
) {
    val hasText = inputText.isNotBlank()
    var presetMenu by remember { mutableStateOf(false) }
    val radius = pillHeightDp / 2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillHeightDp.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(palette.pillBgStart, palette.pillBgEnd)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        palette.pillBorderDim,
                        palette.pillBorderHot,
                        palette.pillBorderDim
                    )
                ),
                shape = RoundedCornerShape(radius.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- Иконка пресета слева ----
            Box {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size((pillHeightDp - 16).dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled) { presetMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = presetIcon,
                        contentDescription = "Режим",
                        tint = palette.accent.copy(alpha = 0.9f),
                        modifier = Modifier.size((pillHeightDp * 0.38f).dp)
                    )
                }
                DropdownMenu(
                    expanded = presetMenu,
                    onDismissRequest = { presetMenu = false }
                ) {
                    PresetRepository.presets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = VisualTheme.iconForPreset(p.id),
                                    contentDescription = null,
                                    tint = palette.accent
                                )
                            },
                            onClick = {
                                onPresetSelected(p.id)
                                presetMenu = false
                            }
                        )
                    }
                }
            }

            // ---- Прозрачное поле ввода ----
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                textStyle = TextStyle(
                    color = palette.inputText,
                    fontSize = (pillHeightDp * 0.27f).sp
                ),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Спросить ассистента",
                                color = palette.placeholder,
                                fontSize = (pillHeightDp * 0.27f).sp
                            )
                        }
                        inner()
                    }
                }
            )

            // ---- TTS-toggle ----
            Box(
                modifier = Modifier
                    .size((pillHeightDp - 20).dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled) { onTtsToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (ttsEnabled) Icons.Default.VolumeUp
                                  else Icons.Default.VolumeOff,
                    contentDescription = "Озвучка",
                    tint = if (ttsEnabled) palette.accent
                           else palette.placeholder.copy(alpha = 0.7f),
                    modifier = Modifier.size((pillHeightDp * 0.31f).dp)
                )
            }

            // ---- Большая кнопка: Mic ↔ Send, со свечением ----
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size((pillHeightDp - 12).dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((pillHeightDp + 14).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    palette.glow,
                                    palette.glow.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size((pillHeightDp - 16).dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(palette.btnInner, palette.btnOuter)
                            )
                        )
                        .clickable(enabled = enabled) {
                            if (hasText) onSend() else onVoiceClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasText) Icons.Default.Send
                                      else Icons.Default.Mic,
                        contentDescription = if (hasText) "Отправить" else "Голос",
                        tint = palette.accentSoft,
                        modifier = Modifier.size((pillHeightDp * 0.35f).dp)
                    )
                }
            }
        }
    }
}

// =============================================================
// Голосовой оверлей в стиле Gemini (полноэкранный)
// =============================================================

@Composable
private fun GeminiVoiceOverlay(
    serviceStatus: LlmStatus,
    settings: SettingsRepository,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    palette: PaletteColors,
    overlayStyle: OverlayStyle
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceController = remember { VoiceInputController(context) }

    var inputText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Готов слушать") }
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
                    is VoiceEvent.RmsChanged ->
                        rmsLevel = ((ev.rmsDb + 2f) / 14f).coerceIn(0f, 1f)
                    is VoiceEvent.Partial -> {
                        inputText = ev.text
                        statusText = ev.text
                    }
                    is VoiceEvent.Final -> {
                        isListening = false
                        inputText = ev.text
                        if (ev.text.isNotBlank() && settings.voiceAutoSend) {
                            viewModel.send(ev.text); inputText = ""
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

    // Фоновая анимация «пульсации» для стиля WAVE
    val infinite = rememberInfiniteTransition(label = "ov")
    val wavePulse by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "wave"
    )

    Surface(
        Modifier.fillMaxSize(),
        color = palette.overlayBgTop
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(palette.overlayBgTop, palette.overlayBgBottom)
                )
            )
        ) {
            Column(
                Modifier.fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Верхняя панель оверлея
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Голосовой ввод",
                        fontSize = 16.sp,
                        color = palette.accentSoft,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрыть",
                            tint = palette.accent)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Центральная область
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
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

                        when (overlayStyle) {
                            OverlayStyle.GLOW -> {
                                VoiceOrb(state = currentOrb, accentColor = palette.accent)
                                Spacer(Modifier.height(20.dp))
                                SoundBars(
                                    amplitude = rmsLevel,
                                    active = currentOrb == OrbState.Listening ||
                                            currentOrb == OrbState.Speaking
                                )
                            }
                            OverlayStyle.MINIMAL -> {
                                VoiceOrb(state = currentOrb, accentColor = palette.accent)
                            }
                            OverlayStyle.WAVE -> {
                                Box(
                                    modifier = Modifier
                                        .size((240 * wavePulse).dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VoiceOrb(state = currentOrb, accentColor = palette.accent)
                                }
                                Spacer(Modifier.height(24.dp))
                                SoundBars(
                                    amplitude = rmsLevel,
                                    active = currentOrb == OrbState.Listening ||
                                            currentOrb == OrbState.Speaking,
                                    barCount = 15
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        when {
                            viewModel.isSending -> Text(
                                "Думаю…",
                                fontSize = 16.sp,
                                color = palette.accent,
                                fontWeight = FontWeight.Medium
                            )
                            displayText.isNotBlank() -> Text(
                                displayText,
                                fontSize = 16.sp,
                                color = palette.inputText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            else -> Text(
                                statusText,
                                fontSize = 14.sp,
                                color = palette.placeholder,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Нижняя «пилюля» ввода (в том же визуальном языке)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(palette.pillBgStart, palette.pillBgEnd)
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    palette.pillBorderDim,
                                    palette.pillBorderHot,
                                    palette.pillBorderDim
                                )
                            ),
                            RoundedCornerShape(27.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        enabled = serviceStatus is LlmStatus.Ready && !viewModel.isSending,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 20.dp, end = 6.dp),
                        textStyle = TextStyle(color = palette.inputText, fontSize = 15.sp),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val t = inputText.trim()
                            if (t.isNotBlank()) {
                                inputText = ""
                                viewModel.send(t)
                            }
                        }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (inputText.isEmpty())
                                    Text("Спросить ассистента",
                                        color = palette.placeholder, fontSize = 15.sp)
                                inner()
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(palette.btnInner, palette.btnOuter)
                                )
                            )
                            .clickable(
                                enabled = serviceStatus is LlmStatus.Ready &&
                                        !viewModel.isSending
                            ) {
                                val t = inputText.trim()
                                if (t.isNotBlank()) {
                                    inputText = ""
                                    viewModel.send(t)
                                } else if (!isListening) startListening()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (inputText.isNotBlank()) Icons.Default.Send
                                          else Icons.Default.Mic,
                            contentDescription = null,
                            tint = palette.accentSoft,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Кнопка закрытия
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", color = palette.accent, fontSize = 14.sp)
                }
            }
        }
    }
}

// =============================================================
// Пузыри сообщений
// =============================================================

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
