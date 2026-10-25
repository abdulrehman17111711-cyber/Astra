package com.astra.ai.tools.builtin

import android.content.Context
import com.astra.ai.accessibility.AccessibilityState
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: read_notifications
 * Reads and speaks aloud incoming notifications captured from apps like WhatsApp,
 * Messages, Telegram, Gmail, and System via Astra's Accessibility Service.
 */
class ReadNotificationsTool(
    private val context: Context
) : Tool {

    companion object {
        private const val TAG = "ReadNotificationsTool"
    }

    override val name: String = "read_notifications"

    override val description: String =
        "Reads recent notifications captured from other apps (WhatsApp, Messages, Telegram, Gmail, etc.) using Astra's Accessibility Service."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "appName",
            type = ToolParamType.STRING,
            description = "Optional filter for a specific app (e.g. 'WhatsApp', 'Messages', 'Gmail')",
            required = false
        ),
        ToolParameter(
            name = "limit",
            type = ToolParamType.INT,
            description = "Maximum number of notifications to read (default 5)",
            required = false
        )
    )

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val appNameFilter = (params["appName"] as? String)?.trim()
        val limit = (params["limit"] as? Number)?.toInt() ?: 5

        val isServiceConnected = AccessibilityState.state.value.isServiceConnected
        val isServiceEnabled = AccessibilityState.isAccessibilityServiceEnabled(context)

        val notifications = AccessibilityState.recentNotifications.value

        val filtered = if (!appNameFilter.isNullOrBlank()) {
            notifications.filter {
                it.appName.contains(appNameFilter, ignoreCase = true) ||
                it.packageName.contains(appNameFilter, ignoreCase = true)
            }
        } else {
            notifications
        }.take(limit)

        val elapsed = System.currentTimeMillis() - startTime

        if (filtered.isEmpty()) {
            val statusMsg = when {
                !isServiceConnected && !isServiceEnabled ->
                    "Astra Accessibility Service is not enabled. Please enable it in Settings > Accessibility to allow Astra to read incoming notifications."
                !appNameFilter.isNullOrBlank() ->
                    "You have no new notifications from $appNameFilter."
                else ->
                    "You have no new notifications right now."
            }

            return ToolExecutionResult.success(
                displayText = statusMsg,
                output = mapOf(
                    "count" to 0,
                    "serviceEnabled" to (isServiceConnected || isServiceEnabled),
                    "filter" to (appNameFilter ?: "All")
                ),
                executionTimeMs = elapsed
            )
        }

        // Format spoken-friendly notification digest
        val header = if (filtered.size == 1) {
            "You have 1 new notification:"
        } else {
            "You have ${filtered.size} new notifications:"
        }

        val itemsSummary = filtered.mapIndexed { index, notif ->
            "${index + 1}. From ${notif.title} on ${notif.appName}: \"${notif.text}\""
        }.joinToString(" ")

        val fullDigest = "$header $itemsSummary"

        AstraLogger.i(TAG, "Read ${filtered.size} notifications successfully.")

        return ToolExecutionResult.success(
            displayText = fullDigest,
            output = mapOf(
                "count" to filtered.size,
                "notifications" to filtered.map {
                    mapOf(
                        "id" to it.id,
                        "appName" to it.appName,
                        "packageName" to it.packageName,
                        "title" to it.title,
                        "text" to it.text,
                        "timestamp" to it.timestamp
                    )
                }
            ),
            executionTimeMs = elapsed
        )
    }
}
