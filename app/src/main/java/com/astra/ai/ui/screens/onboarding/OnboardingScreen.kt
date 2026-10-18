package com.astra.ai.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.assistant.AssistantState
import com.astra.ai.core.AstraConstants
import com.astra.ai.permissions.AstraPermission
import com.astra.ai.permissions.PermissionManager
import com.astra.ai.ui.components.AstraBackground
import com.astra.ai.ui.components.AstraCore
import com.astra.ai.ui.theme.AstraAmber
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraEmerald
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.ui.theme.AstraGlassSurfaceDark
import com.astra.ai.ui.theme.AstraMagenta
import com.astra.ai.ui.theme.AstraViolet

/**
 * 7-step interactive onboarding for Astra AI:
 * 1. Welcome to Astra AI
 * 2. Voice-first assistant introduction
 * 3. What Astra can do
 * 4. Permissions explanation
 * 5. Accessibility explanation
 * 6. AI/backend setup
 * 7. Ready screen
 */
@Composable
fun OnboardingScreen(
    permissionManager: PermissionManager,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 7

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionManager.refresh()
    }

    AstraBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Top Bar: Back & Step Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    IconButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.testTag("onboarding_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Step",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Step Progress Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 1..totalSteps) {
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(if (i == currentStep) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i == currentStep) AstraCyan
                                    else if (i < currentStep) AstraEmerald
                                    else Color.White.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                Text(
                    text = "$currentStep/$totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step Content Animated Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "OnboardingStepTransition"
                ) { step ->
                    when (step) {
                        1 -> Step1Welcome()
                        2 -> Step2VoiceFirst()
                        3 -> Step3Capabilities()
                        4 -> Step4Permissions(permissionManager = permissionManager, onGrantMic = {
                            micLauncher.launch(AstraPermission.RECORD_AUDIO.manifestPermission)
                        })
                        5 -> Step5Accessibility()
                        6 -> Step6AiArchitecture()
                        7 -> Step7Ready()
                        else -> Step1Welcome()
                    }
                }
            }

            // Bottom Navigation Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep < totalSteps) {
                    OutlinedButton(
                        onClick = onFinishOnboarding,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, AstraGlassBorderDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip")
                    }

                    Button(
                        onClick = { currentStep++ },
                        colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1.8f)
                            .height(52.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Text(
                            text = "Continue",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF050811)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF050811)
                        )
                    }
                } else {
                    Button(
                        onClick = onFinishOnboarding,
                        colors = ButtonDefaults.buttonColors(containerColor = AstraEmerald),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_finish_button")
                    ) {
                        Text(
                            text = "Launch Astra Voice Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF050811)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF050811)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step1Welcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AstraCore(
            state = AssistantState.Idle(),
            onCoreClick = {},
            size = 180.dp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Welcome to Astra",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A production-oriented Android voice assistant engineered from the ground up by ${AstraConstants.CREATOR_NAME}.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassInfoCard(
            icon = Icons.Default.Speed,
            title = "Built For Speed & Autonomy",
            description = "Unlike web chat portals, Astra Voice Assistant is embedded into your Android device with native tool execution and instant voice synthesis."
        )
    }
}

@Composable
private fun Step2VoiceFirst() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AstraCore(
            state = AssistantState.Listening(audioLevel = 0.6f),
            onCoreClick = {},
            size = 180.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Voice-First Interaction",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = AstraCyan,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Speak naturally. Astra listens with live audio RMS waveform metering and responds with crisp speech synthesis.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassInfoCard(
            icon = Icons.Default.Mic,
            title = "Zero Typing Required",
            description = "Tap the glowing core to start talking. Soundwaves expand dynamically as you speak."
        )
    }
}

@Composable
private fun Step3Capabilities() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Extensible Tools",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = AstraMagenta,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Astra doesn't just talk; it executes structured tools across your Android system.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        GlassInfoCard(
            icon = Icons.Default.Build,
            title = "Device Health & Telemetry",
            description = "Inspect real-time battery status, RAM, device model, and OS version."
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassInfoCard(
            icon = Icons.Default.Psychology,
            title = "Persistent Memory (Room)",
            description = "Save personal notes and preferences that persist across reboots."
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassInfoCard(
            icon = Icons.Default.Memory,
            title = "World Clock & Timers",
            description = "Query formatted global timezones, offsets, and calendar time."
        )
    }
}

@Composable
private fun Step4Permissions(
    permissionManager: PermissionManager,
    onGrantMic: () -> Unit
) {
    val isMicGranted = permissionManager.isPermissionGranted(AstraPermission.RECORD_AUDIO)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions & Privacy",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = AstraAmber,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Astra requests permissions only when necessary for real hardware functionality.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
            border = BorderStroke(1.dp, AstraGlassBorderDark),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = AstraCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Required for speech recognition and live voice feedback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isMicGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AstraEmerald
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Permission Granted",
                            color = AstraEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onGrantMic,
                        colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Microphone Access", color = Color(0xFF050811), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step5Accessibility() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Accessibility Contract",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = AstraViolet,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Architectural support for assistive on-screen automation and screen reading.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassInfoCard(
            icon = Icons.Default.AccessibilityNew,
            title = "AstraAccessibilityContract",
            description = "Provides future support for reading screen hierarchy, assisting users with touch automation, and navigating third-party applications."
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassInfoCard(
            icon = Icons.Default.Security,
            title = "User Sovereignty",
            description = "Accessibility features remain disabled by default and require deliberate activation in device settings."
        )
    }
}

@Composable
private fun Step6AiArchitecture() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Zero-Trust Security",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = AstraCyan,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Security policies protect your Android system during automated tool execution.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassInfoCard(
            icon = Icons.Default.Security,
            title = "High-Risk Confirmation Gates",
            description = "Any action that modifies data or triggers external events will pause and require your explicit tap confirmation."
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassInfoCard(
            icon = Icons.Default.CheckCircle,
            title = "Result Verification",
            description = "Every tool output is checked by ResultVerifier before response synthesis to prevent erroneous data."
        )
    }
}

@Composable
private fun Step7Ready() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AstraCore(
            state = AssistantState.Success("Astra Ready"),
            onCoreClick = {},
            size = 200.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "You're All Set",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = AstraEmerald,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Astra Voice Assistant is initialized and standing by to assist you. Tap below to launch your voice-first console.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AstraEmerald.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, AstraEmerald.copy(alpha = 0.35f))
        ) {
            Text(
                text = "Created by ${AstraConstants.CREATOR_NAME} • Version ${AstraConstants.APP_VERSION}",
                style = MaterialTheme.typography.labelMedium,
                color = AstraEmerald,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun GlassInfoCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
        border = BorderStroke(1.dp, AstraGlassBorderDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = AstraCyan.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AstraCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
