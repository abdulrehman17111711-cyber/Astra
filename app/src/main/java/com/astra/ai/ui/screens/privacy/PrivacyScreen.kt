package com.astra.ai.ui.screens.privacy

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.core.AstraConstants
import com.astra.ai.memory.HistoryRepository
import com.astra.ai.memory.MemoryRepository
import com.astra.ai.settings.SettingsRepository
import com.astra.ai.ui.components.AstraBackground
import com.astra.ai.ui.theme.AstraAmber
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.ui.theme.AstraGlassSurfaceDark
import com.astra.ai.voice.WakeActivationManager
import kotlinx.coroutines.launch

@Composable
fun PrivacyScreen(
    settingsRepository: SettingsRepository,
    memoryRepository: MemoryRepository,
    historyRepository: HistoryRepository,
    wakeActivationManager: WakeActivationManager,
    onNavigateToAccessibilityDisclosure: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val settings by settingsRepository.settings.collectAsState()
    val wakeConfig by wakeActivationManager.config.collectAsState()

    var showMemoryClearedFeedback by remember { mutableStateOf(false) }
    var showHistoryClearedFeedback by remember { mutableStateOf(false) }

    AstraBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("privacy_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Security & Privacy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                // High level statement
                Card(
                    colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                    border = BorderStroke(1.dp, AstraGlassBorderDark),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AstraCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AstraCyan,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Built with Zero-Trust Security",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Astra Voice Assistant stores personal memories on-device, enforces strict confirmation on external actions, and never embeds secrets in the APK.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Detailed Subsystem Disclosures",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Microphone
                PrivacyExplainingCard(
                    icon = Icons.Default.Mic,
                    title = "Microphone & Speech",
                    description = "Used exclusively for on-device speech-to-text recognition and wake-word activation. Audio is not secretly stored, sold, or broadcast."
                )

                // 2. Contacts
                PrivacyExplainingCard(
                    icon = Icons.Default.ContactPhone,
                    title = "Contacts (Phone & SMS)",
                    description = "Only queried locally when you request a call or message. Contact names are never uploaded or synced to external third parties."
                )

                // 3. Accessibility
                PrivacyExplainingCard(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility Service",
                    description = "Transparent and user-controlled. Operates only on visible accessible UI controls upon explicit instructions. Strictly ignores passwords and 2FA.",
                    actionLabel = "View Accessibility Disclosure",
                    onAction = onNavigateToAccessibilityDisclosure
                )

                // 4. Location
                PrivacyExplainingCard(
                    icon = Icons.Default.LocationOn,
                    title = "Location Data",
                    description = "Only used on-demand when inquiring about local weather or regional context. Continuous location tracking is never enabled."
                )

                // 5. Notifications
                PrivacyExplainingCard(
                    icon = Icons.Default.Notifications,
                    title = "Notifications & Reminders",
                    description = "Used to alert you upon background task completion, scheduled study reminders, or timers. Never used for unsolicited marketing."
                )

                // 6. Backend & AI Processing
                PrivacyExplainingCard(
                    icon = Icons.Default.CloudDone,
                    title = "Backend & Cloud AI Processing",
                    description = "Production communication strictly requires HTTPS encryption. Server-side Gemini API avoids bundling secret API keys in the APK."
                )

                // 7. Persistent Memory
                PrivacyExplainingCard(
                    icon = Icons.Default.Psychology,
                    title = "Persistent Local Memory",
                    description = "Explicitly user-managed. Only facts you instruct Astra to remember (e.g., 'Remember my name is Abdul Rehman') are saved in local Room SQLite storage."
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Controls to Disable Optional Functionality
                Text(
                    text = "Privacy & Functionality Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Wake activation toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                    border = BorderStroke(1.dp, AstraGlassBorderDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wake-Word Listening",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Allows Astra to wake on keyword when app is open.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wakeConfig.isVoiceActivationEnabled,
                            onCheckedChange = { wakeActivationManager.setVoiceActivationEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AstraCyan,
                                checkedTrackColor = AstraCyan.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("privacy_wake_toggle")
                        )
                    }
                }

                // Low-risk auto confirm toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                    border = BorderStroke(1.dp, AstraGlassBorderDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Require Confirmation for All Tools",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Always prompt before executing even low-risk commands.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = !settings.autoConfirmLowRiskTools,
                            onCheckedChange = { settingsRepository.setAutoConfirmLowRisk(!it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AstraCyan,
                                checkedTrackColor = AstraCyan.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("privacy_confirm_all_toggle")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Erase Data Controls
                Text(
                    text = "Data Retention & Erasure",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            memoryRepository.clearAll()
                            showMemoryClearedFeedback = true
                        }
                    },
                    border = BorderStroke(1.dp, AstraAmber.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("clear_memories_privacy_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AstraAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showMemoryClearedFeedback) "All Memories Cleared ✓" else "Clear All Stored Memories",
                        color = AstraAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            historyRepository.clearHistory()
                            showHistoryClearedFeedback = true
                        }
                    },
                    border = BorderStroke(1.dp, AstraAmber.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("clear_history_privacy_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AstraAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showHistoryClearedFeedback) "Interaction History Cleared ✓" else "Clear Interaction History",
                        color = AstraAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Official Privacy Policy Link Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
                    border = BorderStroke(1.dp, AstraGlassBorderDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AstraCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hosted Privacy Policy",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Configuration URL: ${AstraConstants.PRIVACY_POLICY_URL}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (AstraConstants.PRIVACY_POLICY_URL != "TODO_CONFIGURE_PRIVACY_POLICY_URL") {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AstraConstants.PRIVACY_POLICY_URL)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AstraCyan.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, AstraCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("view_privacy_policy_button")
                        ) {
                            Text(
                                text = if (AstraConstants.PRIVACY_POLICY_URL == "TODO_CONFIGURE_PRIVACY_POLICY_URL") {
                                    "Privacy Policy: Placeholder Ready"
                                } else {
                                    "Open Privacy Policy"
                                },
                                color = AstraCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PrivacyExplainingCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
        border = BorderStroke(1.dp, AstraGlassBorderDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = AstraCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AstraCyan,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onAction,
                    border = BorderStroke(1.dp, AstraCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = actionLabel, color = AstraCyan, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = AstraCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
