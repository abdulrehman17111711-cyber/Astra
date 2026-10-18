package com.astra.ai.tools.builtin

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParameter

/**
 * Built-in tool to retrieve real device information and battery health.
 */
class DeviceStatusTool(private val context: Context) : Tool {
    override val name: String = "device_status"
    override val description: String = "Inspects Android device state, battery level, model name, and OS version."
    override val parameters: List<ToolParameter> = emptyList()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = batteryManager?.isCharging == true

            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            val chargingStatus = if (isCharging) "charging" else "discharging"
            val displayText = "Device: $deviceModel | $androidVersion | Battery: $batteryLevel% ($chargingStatus)"

            val output = mapOf(
                "deviceModel" to deviceModel,
                "androidVersion" to androidVersion,
                "batteryLevel" to batteryLevel,
                "isCharging" to isCharging
            )

            ToolExecutionResult.success(
                displayText = displayText,
                output = output,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolExecutionResult.failure(
                errorMessage = "Failed to query device status: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
