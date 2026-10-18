package com.astra.ai.tools.builtin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tool: notification_tool
 * Posts local assistant notifications and alerts to the Android status bar.
 * Requires POST_NOTIFICATIONS permission on Android 13+.
 */
class NotificationTool(
    private val context: Context
) : Tool {

    override val name: String = "notification_tool"

    override val description: String =
        "Posts an alert notification or reminder to the Android notification drawer."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "title",
            type = ToolParamType.STRING,
            description = "Notification title",
            required = true
        ),
        ToolParameter(
            name = "message",
            type = ToolParamType.STRING,
            description = "Notification body text",
            required = true
        )
    )

    override val requiredPermissions: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(android.Manifest.permission.POST_NOTIFICATIONS)
    } else emptyList()

    override val requiresConfirmation: Boolean = false

    private val notificationIdCounter = AtomicInteger(1001)

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val title = (params["title"] as? String)?.trim() ?: "Astra Voice Assistant Notification"
        val message = (params["message"] as? String)?.trim()

        if (message.isNullOrBlank()) {
            return ToolExecutionResult.failure("Notification message cannot be empty.")
        }

        // Check POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                return ToolExecutionResult.failure(
                    "POST_NOTIFICATIONS permission is not granted. Please allow notification permission."
                )
            }
        }

        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return ToolExecutionResult.failure("NotificationManager service unavailable.")

            val channelId = "astra_voice_assistant_alerts"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Astra Voice Assistant Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts and tool execution reminders from Astra Voice Assistant"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .apply {
                    pendingIntent?.let { setContentIntent(it) }
                }
                .build()

            val notifId = notificationIdCounter.incrementAndGet()
            notificationManager.notify(notifId, notification)

            val elapsed = System.currentTimeMillis() - startTime
            ToolExecutionResult.success(
                displayText = "Posted notification: \"$title - $message\".",
                output = mapOf(
                    "notificationId" to notifId,
                    "title" to title,
                    "message" to message
                ),
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            AstraLogger.e("NotificationTool", "Failed posting notification: ${e.message}")
            ToolExecutionResult.failure("Failed to post notification: ${e.message}")
        }
    }
}
