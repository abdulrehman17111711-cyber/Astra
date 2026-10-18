package com.astra.ai.tools.builtin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: device_info_tool
 * Queries real Android system APIs to provide answers for questions like:
 * - "Battery kitni hai?"
 * - "Phone ka model kya hai?"
 * - "Android version kya hai?"
 * - "Internet connected hai?"
 */
class DeviceInfoTool(
    private val context: Context
) : Tool {

    override val name: String = "device_info_tool"

    override val description: String =
        "Provides real-time device telemetry including battery level, charging state, phone model, Android OS version, and network connectivity."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "queryType",
            type = ToolParamType.STRING,
            description = "Specific inquiry: 'battery', 'model', 'android_version', 'internet', or 'all' (default: all)",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val queryType = (params["queryType"] as? String)?.lowercase() ?: "all"

        return try {
            // 1. Battery Information
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = batteryManager?.isCharging == true

            // 2. Hardware and Model
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val deviceModel = "$manufacturer $model"

            // 3. Android Version
            val androidRelease = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT
            val androidVersion = "Android $androidRelease (API $sdkInt)"

            // 4. Connectivity Check
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            val isInternetConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val connectionType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> if (isInternetConnected) "Connected" else "Disconnected"
            }

            val elapsed = System.currentTimeMillis() - startTime

            val displayText = when {
                queryType.contains("battery") -> {
                    val status = if (isCharging) "charging" else "not charging"
                    "Battery is at $batteryLevel% ($status)."
                }
                queryType.contains("model") -> {
                    "Your device model is $deviceModel."
                }
                queryType.contains("version") || queryType.contains("os") || queryType.contains("android") -> {
                    "This device is running $androidVersion."
                }
                queryType.contains("internet") || queryType.contains("network") || queryType.contains("connection") -> {
                    if (isInternetConnected) {
                        "Internet is connected via $connectionType."
                    } else {
                        "Internet is currently disconnected."
                    }
                }
                else -> {
                    "Model: $deviceModel | $androidVersion | Battery: $batteryLevel% | Internet: $connectionType"
                }
            }

            ToolExecutionResult.success(
                displayText = displayText,
                output = mapOf(
                    "batteryLevel" to batteryLevel,
                    "isCharging" to isCharging,
                    "deviceModel" to deviceModel,
                    "androidVersion" to androidVersion,
                    "isInternetConnected" to isInternetConnected,
                    "connectionType" to connectionType
                ),
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            AstraLogger.e("DeviceInfoTool", "Failed retrieving device info: ${e.message}")
            ToolExecutionResult.failure("Failed to retrieve device information: ${e.message}")
        }
    }
}
