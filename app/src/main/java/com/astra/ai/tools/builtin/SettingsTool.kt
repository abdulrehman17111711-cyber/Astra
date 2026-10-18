package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: settings_tool
 * Opens specific Android settings panels using official Settings intents.
 *
 * Examples:
 * - "Wi-Fi settings kholo" -> section = "wifi"
 * - "Bluetooth kholo" -> section = "bluetooth"
 * - "Notification settings kholo" -> section = "notifications"
 * - "Application settings kholo" -> section = "apps"
 * - "Accessibility settings kholo" -> section = "accessibility"
 */
class SettingsTool(
    private val context: Context
) : Tool {

    override val name: String = "settings_tool"

    override val description: String =
        "Opens Android system settings for specific panels such as Wi-Fi, Bluetooth, Apps, Notifications, or Accessibility."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "section",
            type = ToolParamType.STRING,
            description = "Settings panel to open: 'wifi', 'bluetooth', 'apps', 'notifications', 'accessibility', 'battery', or 'general'",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val section = (params["section"] as? String)?.lowercase() ?: "general"

        return try {
            val intent = when {
                section.contains("wifi") || section.contains("wi-fi") || section.contains("internet") -> {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
                section.contains("bluetooth") -> {
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                }
                section.contains("notif") -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_SETTINGS)
                    }
                }
                section.contains("app") -> {
                    Intent(Settings.ACTION_APPLICATION_SETTINGS)
                }
                section.contains("access") -> {
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                }
                section.contains("battery") -> {
                    Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                }
                section.contains("sound") || section.contains("volume") -> {
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                }
                section.contains("display") || section.contains("screen") -> {
                    Intent(Settings.ACTION_DISPLAY_SETTINGS)
                }
                else -> {
                    Intent(Settings.ACTION_SETTINGS)
                }
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to general settings
                val generalIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(generalIntent)
            }

            val elapsed = System.currentTimeMillis() - startTime
            val readableSection = section.replaceFirstChar { it.uppercase() }

            ToolExecutionResult.success(
                displayText = "Opened $readableSection Settings.",
                output = mapOf("section" to section),
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            AstraLogger.e("SettingsTool", "Failed to launch settings: ${e.message}")
            ToolExecutionResult.failure("Failed to open Settings: ${e.message}")
        }
    }
}
