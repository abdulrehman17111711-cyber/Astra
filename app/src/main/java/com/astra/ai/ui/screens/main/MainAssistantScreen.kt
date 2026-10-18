package com.astra.ai.ui.screens.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.assistant.AssistantController
import com.astra.ai.assistant.AssistantState
import com.astra.ai.assistant.AssistantStateType
import com.astra.ai.assistant.ConfirmationRequest
import com.astra.ai.assistant.RiskLevel
import com.astra.ai.core.AstraConstants
import com.astra.ai.ui.components.AstraBackground
import com.astra.ai.ui.components.AstraBottomDock
import com.astra.ai.ui.components.AstraCore
import com.astra.ai.ui.components.AstraStatusDisplay
import com.astra.ai.ui.components.AstraWaveform
import com.astra.ai.ui.components.ConfirmationDialog
import com.astra.ai.ui.components.KeyboardInputSheet
import com.astra.ai.ui.components.getStateColor
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraEmerald
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.ui.theme.AstraGlassSurfaceDark
import com.astra.ai.voice.VoiceSessionManager
import com.astra.ai.voice.WakeActivationManager

/**
 * Premium, futuristic voice-first Android assistant interface for Astra AI.
 * Replaces traditional chat bubbles and text-input feeds with a clean,
 * built-in intelligent assistant console centered on the animated Astra Core.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    var showKeyboardSheet by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showStateInspector by remember { mutableStateOf(false) }

    // Contextual Microphone Permission Dialog States
    var showMicExplanationDialog by remember { mutableStateOf(false) }
    var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }

    // Lifecycle Observer: Safely cancel listening on pause/stop/background/lock
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

    // Activity Result Launcher for RECORD_AUDIO
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Automatically launch voice session immediately upon approval
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

    // Contextual Permission Dialog (Explains BEFORE requesting)
    if (showMicExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showMicExplanationDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Astra Voice Assistant is a voice-first assistant. To understand your spoken commands naturally, microphone access is required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Audio is processed live for speech recognition\n• No background recording is performed\n• You can toggle or disable voice activation anytime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Grant Permission", color = Color(0xFF050811), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showMicExplanationDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AstraGlassBorderDark)
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    // Permanently Denied / Settings Recovery Dialog
    if (showPermanentlyDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermanentlyDeniedDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
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
                        text = "Permission In Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "Microphone permission is currently disabled. To enable voice interaction, please grant the permission in Android App Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AstraGlassBorderDark)
                ) {
                    Text("Use Keyboard Fallback")
                }
            }
        )
    }

    // Keyboard Fallback Input Sheet & Voice-to-Text Dictation
    if (showKeyboardSheet) {
        KeyboardInputSheet(
            onDismiss = { showKeyboardSheet = false },
            onSubmitQuery = { query ->
                controller.processQuery(query)
            },
            speechRecognizerManager = controller.speechRecognizerManager
        )
    }

    AstraBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Minimal Header Bar
                TopAssistantBar(
                    state = state,
                    onInfoClick = onNavigateToOnboarding,
                    onToggleInspector = { showStateInspector = !showStateInspector },
                    isInspectorActive = showStateInspector
                )

                Spacer(modifier = Modifier.weight(0.4f))

                // Central Animated Astra Core (Tap Core directly to activate/stop)
                AstraCore(
                    state = state,
                    onCoreClick = {
                        if (wakeActivationManager.isMicrophoneGranted()) {
                            if (state is AssistantState.Listening || state is AssistantState.Activating) {
                                controller.stopListening()
                            } else {
                                controller.activateVoiceSession()
                            }
                        } else {
                            // Trigger contextual explanation before requesting permission
                            showMicExplanationDialog = true
                        }
                    },
                    size = 240.dp,
                    modifier = Modifier.testTag("astra_core_centerpiece")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Real-time Audio Waveform Indicator (shown during Listening & Speaking)
                val isAudioActive = state is AssistantState.Listening || state is AssistantState.Speaking
                Box(
                    modifier = Modifier.height(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAudioActive) {
                        val audioLevel = if (state is AssistantState.Listening) {
                            (state as AssistantState.Listening).audioLevel
                        } else 0.45f

                        AstraWaveform(
                            audioLevel = audioLevel,
                            baseColor = getStateColor(state.type),
                            barCount = 9
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Minimal, Uncluttered Status Display
                AstraStatusDisplay(
                    state = state,
                    modifier = Modifier.testTag("astra_status_display")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subtle Discovery Action Pills (Natural Bilingual Commands)
                QuickActionPills(
                    onSelectAction = { prompt ->
                        controller.processQuery(prompt)
                    }
                )

                // Optional State Inspector for testing all 8 architecture states
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

                Spacer(modifier = Modifier.weight(0.6f))

                // Floating Frosted-Glass Bottom Console Dock
                AstraBottomDock(
                    onKeyboardClick = { showKeyboardSheet = true },
                    onHistoryClick = onNavigateToHistory,
                    onMemoryClick = onNavigateToMemory,
                    onSettingsClick = onNavigateToSettings,
                    isMuted = isMuted,
                    onToggleMute = {
                        isMuted = !isMuted
                        if (isMuted) controller.resetToIdle()
                    }
                )
            }
        }
    }
}

@Composable
private fun TopAssistantBar(
    state: AssistantState,
    onInfoClick: () -> Unit,
    onToggleInspector: () -> Unit,
    isInspectorActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AstraGlassSurfaceDark,
            border = BorderStroke(1.dp, AstraGlassBorderDark),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(getStateColor(state.type))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AstraConstants.APP_NAME,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${AstraConstants.CREATOR_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AstraCyan,
                    fontSize = 11.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleInspector,
                modifier = Modifier.testTag("toggle_inspector_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Toggle State Inspector",
                    tint = if (isInspectorActive) AstraCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.testTag("top_info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Onboarding and Architecture",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActionPills(
    onSelectAction: (String) -> Unit
) {
    val naturalVoicePrompts = listOf(
        "YouTube par CID search karo",
        "Ammi ko SIM 1 se call karo",
        "WhatsApp par Ali ko message bhejo ke main 10 minute mein aa raha hoon",
        "TikTok par is account ki latest video dhoondo",
        "Kal subah 7 baje alarm laga do",
        "Check karo mere phone mein TikTok installed hai ya nahi",
        "Battery kitni hai?",
        "YouTube pe search karo CID",
        "Camera kholo",
        "Remember that my name is Abdul Rehman"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        naturalVoicePrompts.forEach { action ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AstraGlassSurfaceDark,
                border = BorderStroke(1.dp, AstraGlassBorderDark.copy(alpha = 0.4f)),
                onClick = { onSelectAction(action) }
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun StateSimulatorBar(
    currentState: AssistantState,
    onSimulateState: (AssistantState) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
        border = BorderStroke(1.dp, AstraGlassBorderDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "State Inspector (Phase 3 Voice Verification):",
                style = MaterialTheme.typography.labelSmall,
                color = AstraCyan,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
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
                                AssistantStateType.PROCESSING -> AssistantState.Processing("YouTube kholo", "Processing spoken command...")
                                AssistantStateType.THINKING -> AssistantState.Thinking("YouTube kholo aur Android news search karo")
                                AssistantStateType.EXECUTING -> AssistantState.Executing("device_status", "Check battery", "Querying device state...")
                                AssistantStateType.SPEAKING -> AssistantState.Speaking("Opening YouTube and searching for Android news.")
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
                        label = { Text(type.name, fontSize = 11.sp) },
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
