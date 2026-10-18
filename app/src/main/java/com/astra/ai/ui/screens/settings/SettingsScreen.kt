package com.astra.ai.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.accessibility.AccessibilityState
import com.astra.ai.core.AstraConstants
import com.astra.ai.permissions.AstraPermission
import com.astra.ai.permissions.PermissionManager
import com.astra.ai.settings.SettingsRepository
import com.astra.ai.tools.ToolRegistry
import com.astra.ai.ui.components.AstraBackground
import com.astra.ai.ui.theme.AstraAmber
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraEmerald
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.ui.theme.AstraGlassSurfaceDark
import com.astra.ai.ui.theme.AstraRed
import com.astra.ai.voice.BackgroundListeningStatus
import com.astra.ai.voice.TextToSpeechManager
import com.astra.ai.voice.WakeActivationManager

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    textToSpeechManager: TextToSpeechManager,
    wakeActivationManager: WakeActivationManager,
    permissionManager: PermissionManager,
    toolRegistry: ToolRegistry,
    onNavigateToDiscoveredApps: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToAccessibilityDisclosure: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState()
    val wakeConfig by wakeActivationManager.config.collectAsState()
    val backgroundStatus by wakeActivationManager.backgroundStatus.collectAsState()
    val tools by toolRegistry.toolsListFlow.collectAsState()
    val accessibilityState by AccessibilityState.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        AccessibilityState.refreshSystemStatus(context)
    }

    var wakePhraseInput by remember { mutableStateOf(wakeConfig.wakePhrase) }
    val isMicGranted = wakeActivationManager.isMicrophoneGranted()

    AstraBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Settings & Voice Engine",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Voice activation, wake phrase & hardware limits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Voice Activation & Wake Phrase Section (Required in Part 3)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = AstraCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice Activation Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice Activation ON / OFF Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice Activation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (wakeConfig.isVoiceActivationEnabled) "Enabled (Listening enabled)" else "Disabled (Manual controls only)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (wakeConfig.isVoiceActivationEnabled) AstraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wakeConfig.isVoiceActivationEnabled,
                            onCheckedChange = { enabled ->
                                wakeActivationManager.setVoiceActivationEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AstraCyan),
                            modifier = Modifier.testTag("switch_voice_activation")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Wake Phrase Configuration
                    Text(
                        text = "Wake Phrase",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = wakePhraseInput,
                            onValueChange = {
                                wakePhraseInput = it
                                wakeActivationManager.setWakePhrase(it)
                            },
                            placeholder = { Text("e.g. Hey Astra") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AstraCyan,
                                unfocusedBorderColor = AstraGlassBorderDark
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_wake_phrase")
                        )
                    }
                    Text(
                        text = "Speak this phrase to address Astra during in-app sessions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Continuous Conversation Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Continuous Conversation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Automatically listen for follow-up speech after responding",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wakeConfig.isContinuousConversationEnabled,
                            onCheckedChange = { enabled ->
                                wakeActivationManager.setContinuousConversationEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AstraCyan),
                            modifier = Modifier.testTag("switch_continuous_conv")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Honest Android Limitation Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AstraAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android OS Security & Capabilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Background Listening Status (Available / Restricted / Disabled)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Background Listening",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (backgroundStatus) {
                                BackgroundListeningStatus.AVAILABLE -> AstraEmerald.copy(alpha = 0.18f)
                                BackgroundListeningStatus.RESTRICTED -> AstraAmber.copy(alpha = 0.18f)
                                BackgroundListeningStatus.DISABLED -> Color.White.copy(alpha = 0.1f)
                            }
                        ) {
                            Text(
                                text = backgroundStatus.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (backgroundStatus) {
                                BackgroundListeningStatus.AVAILABLE -> AstraEmerald
                                BackgroundListeningStatus.RESTRICTED -> AstraAmber
                                BackgroundListeningStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                    Text(
                        text = backgroundStatus.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Microphone Permission Status (Granted / Not granted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Microphone Permission",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isMicGranted) AstraEmerald.copy(alpha = 0.18f) else Color.Red.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = if (isMicGranted) "Granted" else "Not granted",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMicGranted) AstraEmerald else Color.Red,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hardware Limitations Note
                    Text(
                        text = "Astra Voice Assistant complies with Google Play & Android 10+ privacy bounds: microphone recording is transparently paused when the app is in the background.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. TTS Voice Parameters Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = AstraCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Speech Synthesis (TTS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Speech Rate: ${String.format("%.1fx", settings.ttsSpeechRate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = {
                            settingsRepository.updateSpeechRate(it)
                            textToSpeechManager.setSpeechRate(it)
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = AstraCyan),
                        modifier = Modifier.testTag("slider_speech_rate")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Voice Pitch: ${String.format("%.1fx", settings.ttsPitch)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Slider(
                        value = settings.ttsPitch,
                        onValueChange = {
                            settingsRepository.updatePitch(it)
                            textToSpeechManager.setPitch(it)
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = AstraCyan),
                        modifier = Modifier.testTag("slider_pitch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Execution Policy & Tool Auto-Confirm
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Trusted Automation & Safeguards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Configurable safeguards ensure routine actions run smoothly while protecting sensitive operations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Auto-confirm low-risk tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-confirm low-risk tools",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Read-only tools (battery, world clock) execute automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoConfirmLowRiskTools,
                            onCheckedChange = { settingsRepository.setAutoConfirmLowRisk(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AstraCyan),
                            modifier = Modifier.testTag("switch_auto_confirm")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = AstraGlassBorderDark.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Trusted Automation Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trusted Automation Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Execute routine actions (calling frequent contacts, drafting messages, alarms) without repeated prompts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.trustedAutomationEnabled,
                            onCheckedChange = { settingsRepository.setTrustedAutomation(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AstraCyan),
                            modifier = Modifier.testTag("switch_trusted_automation")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Remember Permission Decisions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remember legitimate approvals",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Avoid unnecessary prompts for contacts and apps you've already confirmed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.rememberPermissionDecisions,
                            onCheckedChange = { settingsRepository.setRememberPermissionDecisions(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AstraCyan),
                            modifier = Modifier.testTag("switch_remember_decisions")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Safeguard Level Selection
                    Text(
                        text = "Safeguards Policy",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AstraCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "STRICT" to "Strict (Prompt All)",
                            "BALANCED" to "Balanced",
                            "TRUSTED_AUTOMATION" to "Zero Friction"
                        ).forEach { (levelKey, label) ->
                            val isSelected = settings.safeguardLevel == levelKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { settingsRepository.setSafeguardLevel(levelKey) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AstraCyan.copy(alpha = 0.25f),
                                    selectedLabelColor = AstraCyan
                                )
                            )
                        }
                    }

                    if (settings.trustedActions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${settings.trustedActions.size} actions remembered in trust cache",
                                style = MaterialTheme.typography.bodySmall,
                                color = AstraEmerald
                            )
                            TextButton(onClick = { settingsRepository.clearTrustedActions() }) {
                                Text("Reset Trust Cache", color = AstraRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Discovered Applications (Part 5 Dynamic Discovery)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Installed Applications Registry",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "View discoverable apps, package details, capabilities & test launch intents",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToDiscoveredApps,
                        colors = ButtonDefaults.buttonColors(containerColor = AstraCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, AstraCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_view_discovered_apps")
                    ) {
                        Text(
                            text = "View Discovered Apps & Capabilities",
                            color = AstraCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Accessibility Assistant (Part 7)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Accessibility,
                                contentDescription = null,
                                tint = AstraCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Accessibility Assistant",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Automate hands-free app navigation, clicks & searches",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = accessibilityState.isSettingsEnabled,
                            onCheckedChange = {
                                AccessibilityState.openAccessibilitySettings(context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AstraCyan),
                            modifier = Modifier.testTag("switch_accessibility_service")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status indicator row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        val isEnabled = accessibilityState.isSettingsEnabled
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isEnabled) AstraEmerald.copy(alpha = 0.2f) else AstraAmber.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isEnabled) AstraEmerald else AstraAmber)
                        ) {
                            Text(
                                text = if (isEnabled) "Enabled (Active)" else "Disabled (Requires Android Settings)",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isEnabled) AstraEmerald else AstraAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "What Astra Accessibility Can Do:\n" +
                                "• Find and click buttons by semantic intent (\"Search\", \"Magnifying glass\", etc.)\n" +
                                "• Enter text into search fields and form controls\n" +
                                "• Scroll lists and pages upon voice command\n" +
                                "• Navigate back when requested",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Security & Privacy Safeguards:\n" +
                                "• Strictly user-controlled via Android system settings\n" +
                                "• Never interacts with passwords, 2FA codes, CAPTCHAs, or bank screens\n" +
                                "• Zero background screen capture or telemetry transmission",
                        style = MaterialTheme.typography.labelSmall,
                        color = AstraEmerald
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onNavigateToAccessibilityDisclosure,
                        border = BorderStroke(1.dp, AstraCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_view_accessibility_disclosure")
                    ) {
                        Text(
                            text = "View Accessibility Disclosure & Policy",
                            color = AstraCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            AccessibilityState.openAccessibilitySettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AstraCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, AstraCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_accessibility_settings")
                    ) {
                        Text(
                            text = "Open Android Accessibility Settings",
                            color = AstraCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Privacy & Security Navigation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AstraCyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Privacy, Permissions & Zero-Trust",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Transparency disclosures, data erasure & sub-system controls",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToPrivacy,
                        colors = ButtonDefaults.buttonColors(containerColor = AstraCyan.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, AstraCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_privacy_settings")
                    ) {
                        Text(
                            text = "Open Security & Privacy Hub",
                            color = AstraCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. Creator & Lead Developer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                border = BorderStroke(1.dp, AstraGlassBorderDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AstraCyan.copy(alpha = 0.18f),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AstraCyan,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Creator & Lead Developer",
                            style = MaterialTheme.typography.labelSmall,
                            color = AstraCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AstraConstants.CREATOR_NAME,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${AstraConstants.APP_NAME} • Version ${AstraConstants.APP_VERSION}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
