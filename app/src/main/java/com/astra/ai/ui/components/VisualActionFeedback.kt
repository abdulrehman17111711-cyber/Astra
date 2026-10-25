package com.astra.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.assistant.AssistantState
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraEmerald
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.ui.theme.AstraGlassSurfaceDark

/**
 * Rich visual feedback card for executed assistant actions (Calls, WhatsApp, TikTok, Screen Unlock, Alarms, Battery).
 */
@Composable
fun VisualActionFeedback(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val output = when (state) {
        is AssistantState.Success -> state.toolResult?.output.orEmpty()
        is AssistantState.Executing -> {
            // Executing preview
            mapOf("toolName" to state.toolName, "query" to state.query)
        }
        else -> emptyMap()
    }

    if (output.isEmpty()) return

    AnimatedVisibility(
        visible = output.isNotEmpty(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
        exit = fadeOut(),
        modifier = modifier
    ) {
        when {
            // 1. Phone Call Visual Feedback Card
            output.containsKey("phoneNumber") || output["target"] != null || output["toolName"] == "call_tool" || output["toolName"] == "call_contact" -> {
                val target = output["target"]?.toString() ?: output["contactName"]?.toString() ?: "Contact"
                val number = output["phoneNumber"]?.toString() ?: ""
                val sim = output["simApplied"]?.toString() ?: output["simPreference"]?.toString() ?: "SIM 1"
                CallFeedbackCard(target = target, number = number, sim = sim)
            }

            // 2. WhatsApp / WhatsApp Business Feedback Card
            output.containsKey("appDisplayName") || output["targetPackage"]?.toString()?.contains("whatsapp") == true || output["toolName"] == "whatsapp_tool" -> {
                val appTitle = output["appDisplayName"]?.toString() ?: "WhatsApp"
                val recipient = output["recipient"]?.toString() ?: output["contactName"]?.toString() ?: "Recipient"
                val message = output["message"]?.toString().orEmpty()
                val isSent = output["sent"] == true
                WhatsAppFeedbackCard(appTitle = appTitle, recipient = recipient, message = message, isSent = isSent)
            }

            // 3. TikTok & Social Search Feedback Card
            output["application"]?.toString()?.contains("tiktok", ignoreCase = true) == true || output["targetApp"] == "TikTok" || output["toolName"] == "search_app_tool" -> {
                val app = output["application"]?.toString() ?: "TikTok"
                val query = output["query"]?.toString() ?: output["rawQuery"]?.toString() ?: ""
                SocialSearchFeedbackCard(app = app, query = query)
            }

            // 4. Device Unlock / Wake Card
            output.containsKey("screenOn") || output["toolName"] == "device_unlock" -> {
                DeviceUnlockFeedbackCard(
                    isSecure = output["deviceSecure"] == true,
                    isKeyguardLocked = output["keyguardLocked"] == true
                )
            }

            // 5. Battery & Telemetry Card
            output.containsKey("batteryLevel") -> {
                val battery = output["batteryLevel"]?.toString() ?: "100"
                val isCharging = output["isCharging"] == true
                val model = output["deviceModel"]?.toString() ?: "Android Phone"
                BatteryFeedbackCard(battery = battery, isCharging = isCharging, model = model)
            }

            // 6. Alarm / Timer Card
            output.containsKey("durationSeconds") || output["type"] == "timer" || output["toolName"] == "alarm_tool" -> {
                val duration = output["durationSeconds"]?.toString()?.toIntOrNull() ?: 300
                AlarmTimerFeedbackCard(durationSeconds = duration)
            }
        }
    }
}

@Composable
fun CallFeedbackCard(target: String, number: String, sim: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AstraGlassSurfaceDark,
        border = BorderStroke(1.5.dp, AstraEmerald.copy(alpha = 0.6f)),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("call_feedback_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AstraEmerald.copy(alpha = 0.2f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Calling",
                        tint = AstraEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = target,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AstraCyan.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, AstraCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = AstraCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = sim,
                                color = AstraCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (number.isNotBlank()) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Call placed • Direct hardware execution",
                    style = MaterialTheme.typography.labelSmall,
                    color = AstraEmerald,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun WhatsAppFeedbackCard(appTitle: String, recipient: String, message: String, isSent: Boolean) {
    val brandGreen = if (appTitle.contains("Business", ignoreCase = true)) Color(0xFF128C7E) else Color(0xFF25D366)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AstraGlassSurfaceDark,
        border = BorderStroke(1.5.dp, brandGreen.copy(alpha = 0.7f)),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("whatsapp_feedback_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = brandGreen.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = appTitle,
                            tint = brandGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$appTitle • $recipient",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isSent) "Dispatched via Accessibility Automation" else "Prepared in chat • Ready to send",
                        style = MaterialTheme.typography.labelSmall,
                        color = brandGreen,
                        fontSize = 10.sp
                    )
                }

                if (isSent) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Sent",
                        tint = brandGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F1E19),
                    border = BorderStroke(1.dp, brandGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"$message\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SocialSearchFeedbackCard(app: String, query: String) {
    val brandColor = Color(0xFFFE2C55) // TikTok Red/Pink

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AstraGlassSurfaceDark,
        border = BorderStroke(1.5.dp, brandColor.copy(alpha = 0.6f)),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("social_feedback_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = brandColor.copy(alpha = 0.2f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = app,
                        tint = brandColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$app Video Search",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Query: \"$query\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Opened account & latest video feed",
                    style = MaterialTheme.typography.labelSmall,
                    color = brandColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun DeviceUnlockFeedbackCard(isSecure: Boolean, isKeyguardLocked: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AstraGlassSurfaceDark,
        border = BorderStroke(1.5.dp, AstraCyan.copy(alpha = 0.7f)),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("unlock_feedback_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AstraCyan.copy(alpha = 0.2f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Screen Unlock",
                        tint = AstraCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Screen Woken & Unlock Requested",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isSecure) "Scan fingerprint or enter PIN to complete unlock" else "Device unlocked successfully",
                    style = MaterialTheme.typography.bodySmall,
                    color = AstraCyan
                )
            }
        }
    }
}

@Composable
fun BatteryFeedbackCard(battery: String, isCharging: Boolean, model: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AstraGlassSurfaceDark,
        border = BorderStroke(1.5.dp, AstraCyan.copy(alpha = 0.5f)),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("battery_feedback_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AstraCyan.copy(alpha = 0.2f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.PhoneAndroid,
                        contentDescription = "Battery",
                        tint = AstraCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$model • Battery $battery%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isCharging) "Charging active • Power connected" else "Battery level verified",
                    style = MaterialTheme.typography.labelSmall,
                    color = AstraCyan
                )
            }
        }
    }
}

@Composable
fun AlarmTimerFeedbackCard(durationSeconds: Int) {
    val minutes = durationSeconds / 60
    val secs = durationSeconds % 60
    val timeLabel = if (minutes > 0) "$minutes min ${if (secs > 0) "$secs sec" else ""}" else "$secs seconds"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AstraGlassSurfaceDark,
        border = BorderStroke(1.5.dp, AstraCyan.copy(alpha = 0.6f)),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("alarm_feedback_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AstraCyan.copy(alpha = 0.2f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Timer",
                        tint = AstraCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Timer Set: $timeLabel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Astra AlarmClock intent dispatched",
                    style = MaterialTheme.typography.labelSmall,
                    color = AstraCyan
                )
            }
        }
    }
}
