package com.astra.ai.tools.builtin

import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Built-in tool for querying time, date, and timezones.
 */
class WorldClockTool : Tool {
    override val name: String = "world_clock"
    override val description: String = "Gets current time and date for the local device or a specified timezone."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "timezone",
            type = ToolParamType.STRING,
            description = "Optional timezone ID (e.g. 'UTC', 'GMT', 'Asia/Karachi', 'America/New_York'). Defaults to local.",
            required = false,
            defaultValue = "LOCAL"
        )
    )
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        return try {
            val tzParam = (params["timezone"] as? String)?.trim() ?: "LOCAL"
            val tz = if (tzParam.equals("LOCAL", ignoreCase = true) || tzParam.isEmpty()) {
                TimeZone.getDefault()
            } else {
                TimeZone.getTimeZone(tzParam)
            }

            val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' hh:mm:ss a z", Locale.getDefault()).apply {
                timeZone = tz
            }
            val formattedTime = formatter.format(Date())
            val displayText = "Current Time (${tz.id}): $formattedTime"

            ToolExecutionResult.success(
                displayText = displayText,
                output = mapOf(
                    "formattedTime" to formattedTime,
                    "timezoneId" to tz.id,
                    "epochMillis" to System.currentTimeMillis()
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolExecutionResult.failure(
                errorMessage = "Failed to get world clock: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
