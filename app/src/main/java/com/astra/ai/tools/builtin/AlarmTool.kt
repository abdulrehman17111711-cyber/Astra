package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter
import java.util.Calendar

/**
 * Tool: alarm_tool
 * Sets alarms and timers using official Android AlarmClock intents.
 * Examples:
 * "Kal subah 7 baje alarm laga do" -> hour = 7, minutes = 0, amPm = "AM"
 * "Set alarm for 8:30" -> hour = 8, minutes = 30
 */
class AlarmTool(
    private val context: Context
) : Tool {

    override val name: String = "alarm_tool"

    override val description: String =
        "Sets an alarm, countdown timer, or reminder using the Android system clock app."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "hour",
            type = ToolParamType.INT,
            description = "Hour of the day (0-23 or 1-12) for alarms",
            required = false
        ),
        ToolParameter(
            name = "minute",
            type = ToolParamType.INT,
            description = "Minute of the hour (0-59) for alarms",
            required = false
        ),
        ToolParameter(
            name = "durationSeconds",
            type = ToolParamType.INT,
            description = "Duration in seconds for countdown timers (e.g. 300 for 5 minutes)",
            required = false
        ),
        ToolParameter(
            name = "amPm",
            type = ToolParamType.STRING,
            description = "AM or PM if 12-hour format is used",
            required = false
        ),
        ToolParameter(
            name = "message",
            type = ToolParamType.STRING,
            description = "Alarm or timer label or description",
            required = false
        )
    )

    override val requiredPermissions: List<String> = listOf(
        "com.android.alarm.permission.SET_ALARM"
    )

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val label = (params["message"] as? String)?.trim() ?: "Astra Assistant"

        // 1. Check if Timer was requested
        val durationSeconds = when (val s = params["durationSeconds"] ?: params["lengthSeconds"]) {
            is Number -> s.toInt()
            is String -> s.toIntOrNull()
            else -> null
        }

        if (durationSeconds != null && durationSeconds > 0) {
            return try {
                val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                if (timerIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(timerIntent)
                } else {
                    val showIntent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (showIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(showIntent)
                    } else {
                        // Fallback to alarm
                        val fallbackAlarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            val c = Calendar.getInstance().apply { add(Calendar.SECOND, durationSeconds) }
                            putExtra(AlarmClock.EXTRA_HOUR, c.get(Calendar.HOUR_OF_DAY))
                            putExtra(AlarmClock.EXTRA_MINUTES, c.get(Calendar.MINUTE))
                            putExtra(AlarmClock.EXTRA_MESSAGE, label)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(fallbackAlarm)
                    }
                }

                val mins = durationSeconds / 60
                val secs = durationSeconds % 60
                val durationText = if (mins > 0 && secs > 0) "$mins min $secs sec" else if (mins > 0) "$mins minute${if (mins > 1) "s" else ""}" else "$secs seconds"
                val elapsed = System.currentTimeMillis() - startTime

                ToolExecutionResult.success(
                    displayText = "Timer set for $durationText with label \"$label\".",
                    output = mapOf(
                        "type" to "timer",
                        "durationSeconds" to durationSeconds,
                        "label" to label
                    ),
                    executionTimeMs = elapsed
                )
            } catch (e: Exception) {
                AstraLogger.e("AlarmTool", "Failed setting timer: ${e.message}")
                ToolExecutionResult.failure("Failed to set timer: ${e.message}")
            }
        }

        // 2. Alarm was requested
        val rawHour = when (val h = params["hour"]) {
            is Number -> h.toInt()
            is String -> h.toIntOrNull() ?: 7
            else -> 7
        }

        val rawMinute = when (val m = params["minute"]) {
            is Number -> m.toInt()
            is String -> m.toIntOrNull() ?: 0
            else -> 0
        }

        val amPm = (params["amPm"] as? String)?.uppercase()

        // Normalize hour to 24h
        var normalizedHour = rawHour
        if (amPm != null) {
            if (amPm.contains("PM") && normalizedHour in 1..11) {
                normalizedHour += 12
            } else if (amPm.contains("AM") && normalizedHour == 12) {
                normalizedHour = 0
            }
        }

        if (normalizedHour !in 0..23 || rawMinute !in 0..59) {
            return ToolExecutionResult.failure("Invalid alarm time: $rawHour:$rawMinute $amPm.")
        }

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, normalizedHour)
                putExtra(AlarmClock.EXTRA_MINUTES, rawMinute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to show alarms
                val showIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(showIntent)
            }

            val formattedTime = String.format("%02d:%02d", normalizedHour, rawMinute)
            val elapsed = System.currentTimeMillis() - startTime

            ToolExecutionResult.success(
                displayText = "Alarm set for $formattedTime with label \"$label\".",
                output = mapOf(
                    "type" to "alarm",
                    "hour" to normalizedHour,
                    "minute" to rawMinute,
                    "label" to label,
                    "timeFormatted" to formattedTime
                ),
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            AstraLogger.e("AlarmTool", "Failed setting alarm: ${e.message}")
            ToolExecutionResult.failure("Failed to set alarm: ${e.message}")
        }
    }
}
