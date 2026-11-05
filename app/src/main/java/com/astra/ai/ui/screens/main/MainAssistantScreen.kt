package com.astra.ai.ui.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.assistant.AssistantController
import com.astra.ai.assistant.AssistantState
import com.astra.ai.assistant.AssistantStateType
import com.astra.ai.assistant.ConfirmationRequest
import com.astra.ai.assistant.RiskLevel
import com.astra.ai.ui.components.AstraBackground
import com.astra.ai.ui.components.AstraWaveform
import com.astra.ai.ui.components.ConfirmationDialog
import com.astra.ai.ui.components.getStateColor
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.ui.theme.AstraGlassSurfaceDark
import com.astra.ai.voice.VoiceSessionManager
import com.astra.ai.voice.WakeActivationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Representation of an individual message in the conversational chat feed.
 */
sealed class ChatMessage {
    abstract val id: Long
    abstract val timestamp: Long

    data class UserPrompt(
        override val id: Long,
        override val timestamp: Long,
        val text: String,
        val isVoice: Boolean = true
    ) : ChatMessage()

    data class AssistantReply(
        override val id: Long,
        override val timestamp: Long,
        val text: String,
        val toolName: String? = null,
        val query: String = ""
    ) : ChatMessage()
}

/**
 * Clean, dynamic chat-style interface for Astra AI.
 * Displays the user's voice prompt as a crisp text bubble followed by the assistant's
 * structured response. Optimized for IME software keyboard positioning with auto-scrolling
 * and 100% English UI copy.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainAssistantScreen(
    controller: AssistantController,
    voiceSessionManager: VoiceSessionManager,
    wakeActivationManager: WakeActivationManager,
    onNavigateToHistory: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // In-memory conversational stream: User messages appear first, then assistant reply
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var activePromptForReply by remember { mutableStateOf("") }

    var typedText by remember { mutableStateOf("") }
    var showStateInspector by remember { mutableStateOf(false) }

    // Dialog state controllers
    var showMicExplanationDialog by remember { mutableStateOf(false) }
    var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }
    var showAccessibilityGuideDialog by remember { mutableStateOf(false) }

    // Synchronize incoming user voice recognition into the chat stream immediately
    LaunchedEffect(state) {
        when (val current = state) {
            is AssistantState.Processing -> {
                val q = current.query.trim()
                if (q.isNotBlank()) {
                    val lastPrompt = chatMessages.lastOrNull() as? ChatMessage.UserPrompt
                    if (lastPrompt == null || lastPrompt.text != q) {
                        val userPrompt = ChatMessage.UserPrompt(
                            id = System.currentTimeMillis(),
                            timestamp = System.currentTimeMillis(),
                            text = q,
                            isVoice = true
                        )
                        chatMessages = chatMessages + userPrompt
                        activePromptForReply = q
                    }
                }
            }
            is AssistantState.Thinking -> {
                val q = current.query.trim()
                if (q.isNotBlank()) {
                    val lastPrompt = chatMessages.lastOrNull() as? ChatMessage.UserPrompt
                    if (lastPrompt == null || lastPrompt.text != q) {
                        val userPrompt = ChatMessage.UserPrompt(
                            id = System.currentTimeMillis(),
                            timestamp = System.currentTimeMillis(),
                            text = q,
                            isVoice = true
                        )
                        chatMessages = chatMessages + userPrompt
                        activePromptForReply = q
                    }
                }
            }
            is AssistantState.Success -> {
                val summary = current.summary.trim()
                if (summary.isNotBlank()) {
                    val lastReply = chatMessages.lastOrNull() as? ChatMessage.AssistantReply
                    if (lastReply == null || lastReply.text != summary) {
                        val reply = ChatMessage.AssistantReply(
                            id = System.currentTimeMillis(),
                            timestamp = System.currentTimeMillis(),
                            text = summary,
                            query = activePromptForReply,
                            toolName = (controller.state.value as? AssistantState.Executing)?.toolName
                        )
                        chatMessages = chatMessages + reply
                    }
                }
            }
            is AssistantState.Error -> {
                val err = current.errorMessage.trim()
                if (err.isNotBlank()) {
                    val lastReply = chatMessages.lastOrNull() as? ChatMessage.AssistantReply
                    if (lastReply == null || lastReply.text != err) {
                        val reply = ChatMessage.AssistantReply(
                            id = System.currentTimeMillis(),
                            timestamp = System.currentTimeMillis(),
                            text = err,
                            query = activePromptForReply,
                            toolName = "Error"
                        )
                        chatMessages = chatMessages + reply
                    }
                }
            }
            else -> {}
        }
    }

    // Lifecycle Observer: Cleanly manages recognition state on backgrounding
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    controller.onAppLifecycleChanged(isForeground = false)
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    controller.onAppLifecycleChanged(isForeground = true)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launcher for RECORD_AUDIO
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            wakeActivationManager.startBackgroundWakeWordService()
            controller.activateVoiceSession()
        } else {
            showPermanentlyDeniedDialog = true
        }
    }

    // Handle Confirmation Dialog when in WAITING_FOR_CONFIRMATION state
    if (state is AssistantState.WaitingForConfirmation) {
        val request = (state as AssistantState.WaitingForConfirmation).request
        ConfirmationDialog(
            request = request,
            onConfirm = { controller.confirmPendingAction() },
            onReject = { controller.rejectPendingAction() }
        )
    }

    // Contextual Microphone Permission Explanation Dialog
    if (showMicExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showMicExplanationDialog = false },
            containerColor = Color(0xFF111A24),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = AstraCyan
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Microphone Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Astra AI requires microphone access to hear and execute your voice commands hands-free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• No unnecessary recordings are stored\n• Activates instantly on 'Hey Astra'\n• You can toggle or revoke this anytime",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF88A0B8)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicExplanationDialog = false
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Allow", color = Color(0xFF050811), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showMicExplanationDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AstraGlassBorderDark)
                ) {
                    Text("Not Now", color = Color.White)
                }
            }
        )
    }

    // Permanently Denied Dialog
    if (showPermanentlyDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermanentlyDeniedDialog = false },
            containerColor = Color(0xFF111A24),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = AstraCyan
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Enable in Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "Microphone permission is currently disabled. Please open app Settings -> Permissions -> Microphone to allow voice commands.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermanentlyDeniedDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Settings", color = Color(0xFF050811), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPermanentlyDeniedDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // Infinix & Tecno 2-Step Accessibility Guide Dialog
    if (showAccessibilityGuideDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityGuideDialog = false },
            containerColor = Color(0xFF0E1624),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00FFA3))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Full Control Setup (Infinix & Tecno)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Follow these 2 quick steps to enable background voice automation and volume button activation on your device:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1️⃣ Tap Step 1 below -> tap the 3 dots (⋮) in the top-right corner -> select 'Allow restricted settings'.\n\n2️⃣ Tap Step 2 below -> locate 'Astra AI' -> switch the toggle ON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0C4DE),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Step 1: Allow Restricted Settings", color = Color(0xFF050811), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9D00FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Step 2: Enable Accessibility", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAccessibilityGuideDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFA3)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", color = Color(0xFF050811), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    // Auto-scroll chat to bottom smoothly when keyboard appears, message arrives, or state updates
    LaunchedEffect(chatMessages.size, state, isImeVisible) {
        val totalCount = chatMessages.size + if (state !is AssistantState.Idle) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    AstraBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Header Bar (Status, Accessibility pill, Navigation)
            TopBar(
                state = state,
                onAccessibilityClick = { showAccessibilityGuideDialog = true },
                onHistoryClick = onNavigateToHistory,
                onSettingsClick = onNavigateToSettings,
                onInfoClick = onNavigateToOnboarding,
                onToggleInspector = { showStateInspector = !showStateInspector },
                isInspectorActive = showStateInspector
            )

            // Optional State Inspector Bar for architecture verification
            AnimatedVisibility(
                visible = showStateInspector,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                StateSimulatorBar(
                    currentState = state,
                    onSimulateState = { simulated ->
                        controller.setStateForPreview(simulated)
                    }
                )
            }

            // 2. Chat Conversation Feed (Clean, dynamic chat without any shortcut buttons)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (chatMessages.isEmpty() && state is AssistantState.Idle) {
                    // Beautiful Animated Holographic Core Centerpiece
                    EmptyChatGreeting(
                        onCoreClick = {
                            if (wakeActivationManager.isMicrophoneGranted()) {
                                controller.activateVoiceSession()
                            } else {
                                showMicExplanationDialog = true
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { msg ->
                            when (msg) {
                                is ChatMessage.UserPrompt -> {
                                    UserMessageBubble(message = msg)
                                }
                                is ChatMessage.AssistantReply -> {
                                    AssistantResponseBubble(
                                        message = msg,
                                        onCopy = { text ->
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Astra AI Response", text))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onSpeak = { text ->
                                            controller.textToSpeechManager.speak(text)
                                        }
                                    )
                                }
                            }
                        }

                        // Live In-Progress Bubble at bottom of stream
                        if (state !is AssistantState.Idle) {
                            item(key = "active_state_row") {
                                ActiveInteractionBubble(
                                    state = state,
                                    onRetryClick = {
                                        controller.activateVoiceSession()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. WhatsApp & Tecno Ella Style Bottom Input Bar with Keyboard-Responsive Layout
            BottomInputBar(
                typedText = typedText,
                onTypedTextChange = { typedText = it },
                onSendMessage = {
                    val q = typedText.trim()
                    if (q.isNotEmpty()) {
                        // Immediately append user message to chat stream
                        val userPrompt = ChatMessage.UserPrompt(
                            id = System.currentTimeMillis(),
                            timestamp = System.currentTimeMillis(),
                            text = q,
                            isVoice = false
                        )
                        chatMessages = chatMessages + userPrompt
                        activePromptForReply = q

                        // Dispatch query to AI controller
                        controller.processQuery(q)
                        typedText = ""
                        keyboardController?.hide()
                    }
                },
                isListening = state is AssistantState.Listening || state is AssistantState.Activating,
                onMicClick = {
                    if (wakeActivationManager.isMicrophoneGranted()) {
                        controller.toggleListening()
                    } else {
                        showMicExplanationDialog = true
                    }
                }
            )
        }
    }
}

/**
 * Top Header Bar: Clean branding, Infinix Full Control Status pill, and navigation.
 */
@Composable
private fun TopBar(
    state: AssistantState,
    onAccessibilityClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onInfoClick: () -> Unit,
    onToggleInspector: () -> Unit,
    isInspectorActive: Boolean
) {
    val isServiceConnected = com.astra.ai.accessibility.AstraAccessibilityService.instance != null

    Surface(
        color = Color(0xFF0C1322).copy(alpha = 0.90f),
        border = BorderStroke(1.dp, AstraGlassBorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Identity: Astra avatar & state indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00F5FF), Color(0xFF7A00FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Astra AI",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Astra AI • Voice Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(getStateColor(state.type))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when (state) {
                                is AssistantState.Listening -> "Listening..."
                                is AssistantState.Processing -> "Processing..."
                                is AssistantState.Thinking -> "Thinking..."
                                is AssistantState.Executing -> "Executing action..."
                                is AssistantState.Speaking -> "Speaking..."
                                else -> "Online • Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = AstraCyan,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Right Actions: Full Control Pill & Navigation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onAccessibilityClick,
                    shape = RoundedCornerShape(16.dp),
                    color = if (isServiceConnected) Color(0xFF00382B) else Color(0xFF38152B),
                    border = BorderStroke(1.dp, if (isServiceConnected) Color(0xFF00FFA3).copy(alpha = 0.6f) else Color(0xFFFF007F).copy(alpha = 0.7f)),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isServiceConnected) Color(0xFF00FFA3) else Color(0xFFFF007F))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isServiceConnected) "Full Control Active" else "Full Control Setup",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(onClick = onHistoryClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(onClick = onSettingsClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * Beautiful Animated Holographic Core Centerpiece displayed on empty screen.
 * Features 3 rotating orbital rings, pulsating energy core, and floating hover motion.
 */
@Composable
private fun EmptyChatGreeting(
    onCoreClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GreetingOrbitalTransition")

    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitalRingRotation"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoreBreathingPulse"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HoverFloat"
    )

    val currentFloatY = (sin(floatOffset) * 10f).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .offset { IntOffset(0, currentFloatY) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Multi-ring holographic interactive core
        Box(
            modifier = Modifier
                .size(170.dp)
                .clickable { onCoreClick() },
            contentAlignment = Alignment.Center
        ) {
            // Outer orbital ring
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(orbitRotation)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            AstraCyan.copy(alpha = 0.75f),
                            Color(0xFF00FFA3),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner counter-rotating ring
            Canvas(
                modifier = Modifier
                    .size(130.dp)
                    .rotate(-orbitRotation * 1.5f)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF7A00FF).copy(alpha = 0.6f),
                            Color.Transparent,
                            Color(0xFFFF007F).copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Central Glowing Sphere
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .scale(corePulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AstraCyan,
                                Color(0xFF0072FF),
                                Color(0xFF0A1424)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Activate Astra",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Status Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF111D2E),
            border = BorderStroke(1.dp, AstraCyan.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FFA3))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Astra AI Ready",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Say \"Hey Astra\" or tap the microphone\nto command your device",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8FA7C2),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

/**
 * User message bubble rendered on the right side of the chat feed.
 */
@Composable
private fun UserMessageBubble(message: ChatMessage.UserPrompt) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color(0xFF005C4B),
            border = BorderStroke(0.5.dp, Color(0xFF00FFA3).copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (message.isVoice) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (message.isVoice) "Voice Command" else "Text Command",
                        tint = Color(0xFF00FFA3),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (message.isVoice) "Voice Command" else "Text Command",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00FFA3),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Delivered",
                        tint = Color(0xFF53BDEB),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Assistant structured response bubble rendered on the left side of the chat feed.
 */
@Composable
private fun AssistantResponseBubble(
    message: ChatMessage.AssistantReply,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    val toolType = remember(message.toolName, message.query, message.text) {
        determineToolCategory(message.toolName, message.query, message.text)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color(0xFF162330),
            border = BorderStroke(1.dp, AstraCyan.copy(alpha = 0.35f)),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                // Header with Astra avatar and action badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF00F5FF), Color(0xFF7A00FF)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Astra AI",
                            style = MaterialTheme.typography.labelMedium,
                            color = AstraCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Tool Action Tag with specialized icon
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = toolType.badgeColor.copy(alpha = 0.18f),
                        border = BorderStroke(0.5.dp, toolType.badgeColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = toolType.icon,
                                contentDescription = null,
                                tint = toolType.badgeColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = toolType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = toolType.badgeColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Structured Action Content
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE9EDEF),
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Actions: Copy, Speak, Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onCopy(message.text) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Response",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onSpeak(message.text) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Listen Response",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private data class ToolCategoryInfo(
    val label: String,
    val icon: ImageVector,
    val badgeColor: Color
)

private fun determineToolCategory(toolName: String?, query: String, response: String): ToolCategoryInfo {
    val q = query.lowercase()
    val t = (toolName ?: "").lowercase()
    val r = response.lowercase()
    return when {
        t.contains("call") || q.contains("call") -> ToolCategoryInfo("Phone Call", Icons.Default.Call, Color(0xFF00FFA3))
        t.contains("whatsapp") || q.contains("whatsapp") || q.contains("message") || r.contains("whatsapp") -> ToolCategoryInfo("WhatsApp", Icons.AutoMirrored.Filled.Message, Color(0xFF25D366))
        t.contains("alarm") || q.contains("alarm") -> ToolCategoryInfo("Alarm Set", Icons.Default.Alarm, Color(0xFFFFB800))
        t.contains("app") || q.contains("open") || q.contains("kholo") -> ToolCategoryInfo("App Action", Icons.Default.Apps, Color(0xFF00E5FF))
        t.contains("search") || q.contains("search") || q.contains("youtube") -> ToolCategoryInfo("Search", Icons.Default.Search, Color(0xFFFF007F))
        else -> ToolCategoryInfo("Astra Response", Icons.Default.Check, AstraCyan)
    }
}

/**
 * Real-time active interaction bubble rendered when the assistant is
 * actively Listening, Thinking, Executing, or Speaking.
 */
@Composable
private fun ActiveInteractionBubble(
    state: AssistantState,
    onRetryClick: () -> Unit
) {
    when (state) {
        is AssistantState.Listening -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = Color(0xFF005C4B).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF00FFA3)),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color(0xFF00FFA3),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Listening...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00FFA3),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.partialText.ifBlank { "Listening for your voice..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontSize = 14.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AstraWaveform(
                            audioLevel = state.audioLevel,
                            baseColor = Color(0xFF00FFA3),
                            barCount = 9
                        )
                    }
                }
            }
        }

        is AssistantState.Processing,
        is AssistantState.Thinking -> {
            val queryText = if (state is AssistantState.Processing) state.query else ""
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = Color(0xFF162330),
                    border = BorderStroke(1.dp, AstraCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.widthIn(max = 290.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = AstraCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Astra AI is thinking...",
                                style = MaterialTheme.typography.labelSmall,
                                color = AstraCyan,
                                fontWeight = FontWeight.Bold
                            )
                            if (queryText.isNotBlank()) {
                                Text(
                                    text = "\"$queryText\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        is AssistantState.Executing -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = Color(0xFF162330),
                    border = BorderStroke(1.dp, Color(0xFF00FFA3).copy(alpha = 0.6f)),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF00FFA3),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Executing: ${state.toolName}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        is AssistantState.Speaking -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = Color(0xFF162330),
                    border = BorderStroke(1.dp, AstraCyan),
                    modifier = Modifier.widthIn(max = 310.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AstraCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Astra AI Speaking:",
                                style = MaterialTheme.typography.labelSmall,
                                color = AstraCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontSize = 14.5.sp
                        )
                    }
                }
            }
        }

        is AssistantState.Error -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = Color(0xFF2A151D),
                    border = BorderStroke(1.dp, Color(0xFFFF007F).copy(alpha = 0.7f)),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF6699)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap to Retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onRetryClick() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        else -> {}
    }
}

/**
 * WhatsApp & Tecno Ella Style Bottom Input Bar:
 * Dynamically wraps input text from 1 to 4 lines, remains strictly elevated above
 * the soft keyboard via imePadding(), and features the multi-ripple voice mic centerpiece.
 */
@Composable
private fun BottomInputBar(
    typedText: String,
    onTypedTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "MicPulseAnim")

    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MicPulseScale"
    )

    // Animated concentric ripple expansion for hands-free centerpiece
    val ripple1 by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple1"
    )

    val ripple1Alpha by transition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Ripple1Alpha"
    )

    Surface(
        color = Color(0xFF0C1322).copy(alpha = 0.96f),
        border = BorderStroke(1.dp, AstraGlassBorderDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Input Field (Expands dynamically up to 4 lines with clean wrapping)
            OutlinedTextField(
                value = typedText,
                onValueChange = onTypedTextChange,
                placeholder = {
                    Text(
                        text = if (isListening) "Listening..." else "Type a message or speak...",
                        color = Color(0xFF70859B),
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text_field"),
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF172335),
                    unfocusedContainerColor = Color(0xFF131D2D),
                    focusedBorderColor = AstraCyan,
                    unfocusedBorderColor = Color(0xFF24364D),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendMessage() })
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Action Button: Send if text typed, or Glowing Multi-Ripple Voice Mic Centerpiece
            if (typedText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A884))
                        .clickable { onSendMessage() }
                        .testTag("chat_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Large Glowing Voice Mic Centerpiece with Multi-Layer Ripples
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isListening) {
                        // Expanding animated audio ripple
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .scale(ripple1)
                                .clip(CircleShape)
                                .background(Color(0xFF00FFA3).copy(alpha = ripple1Alpha))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .scale(if (isListening) pulseScale else 1.0f)
                            .clip(CircleShape)
                            .background(
                                if (isListening) {
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFF007F), Color(0xFF7A00FF))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color(0xFF00A884), Color(0xFF00F5FF))
                                    )
                                }
                            )
                            .clickable { onMicClick() }
                            .testTag("voice_listen_button")
                            .testTag("astra_core_centerpiece"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Speak to Astra",
                            tint = if (isListening) Color.White else Color(0xFF050811),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * State simulator bar for architecture verification.
 */
@Composable
private fun StateSimulatorBar(
    currentState: AssistantState,
    onSimulateState: (AssistantState) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
        border = BorderStroke(1.dp, AstraGlassBorderDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "State Inspector:",
                style = MaterialTheme.typography.labelSmall,
                color = AstraCyan,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistantStateType.entries.forEach { type ->
                    val isSelected = currentState.type == type
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val simulated = when (type) {
                                AssistantStateType.IDLE -> AssistantState.Idle()
                                AssistantStateType.ACTIVATING -> AssistantState.Activating("Astra is activating audio pipeline...")
                                AssistantStateType.LISTENING -> AssistantState.Listening("Listening for natural speech...", 0.7f)
                                AssistantStateType.PROCESSING -> AssistantState.Processing("Call Salman", "Processing spoken command...")
                                AssistantStateType.THINKING -> AssistantState.Thinking("Analyzing command intent...")
                                AssistantStateType.EXECUTING -> AssistantState.Executing("telephony", "Call Salman", "Placing call...")
                                AssistantStateType.SPEAKING -> AssistantState.Speaking("Calling Salman on default SIM.")
                                AssistantStateType.WAITING_FOR_CONFIRMATION -> AssistantState.WaitingForConfirmation(
                                    ConfirmationRequest(
                                        toolName = "reboot_device",
                                        actionTitle = "Execute System Action",
                                        description = "Astra requires your confirmation to execute.",
                                        riskLevel = RiskLevel.MEDIUM
                                    )
                                )
                                AssistantStateType.SUCCESS -> AssistantState.Success("Action executed successfully.")
                                AssistantStateType.ERROR -> AssistantState.Error("Microphone permission not granted.")
                            }
                            onSimulateState(simulated)
                        },
                        label = { Text(type.name, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = getStateColor(type).copy(alpha = 0.25f),
                            selectedLabelColor = getStateColor(type)
                        )
                    )
                }
            }
        }
    }
}
