package com.astra.ai.tools.builtin

import android.content.Context
import com.astra.ai.apps.AppLaunchResult
import com.astra.ai.apps.AppManager
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: app_launch_tool
 * Dynamically launches any installed application using Android PackageManager & AppDiscoveryManager.
 * Meets the Part 6 AppLaunchTool specification while preserving alias compatibility with open_app.
 */
class AppLaunchTool(
    private val context: Context,
    private val appManager: AppManager
) : Tool {

    override val name: String = "app_launch_tool"

    override val description: String =
        "Dynamically finds and launches any installed application on the device by exact name, normalized name, or package name."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "appName",
            type = ToolParamType.STRING,
            description = "Application name (e.g. Spotify, WhatsApp, YouTube, Settings, Camera)",
            required = false
        ),
        ToolParameter(
            name = "packageName",
            type = ToolParamType.STRING,
            description = "Android package name (e.g. com.spotify.music)",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val pkg = params["packageName"] as? String
        val appName = params["appName"] as? String

        val query = when {
            !pkg.isNullOrBlank() -> pkg
            !appName.isNullOrBlank() -> appName
            else -> null
        }

        if (query.isNullOrBlank()) {
            return ToolExecutionResult.failure("Please specify an application name or package to open.")
        }

        val launchResult = appManager.launchAppDetailed(query)
        val elapsed = System.currentTimeMillis() - startTime

        return when (launchResult) {
            is AppLaunchResult.Success -> {
                ToolExecutionResult.success(
                    displayText = launchResult.message,
                    output = mapOf(
                        "appName" to launchResult.app.appName,
                        "packageName" to launchResult.app.packageName,
                        "launchActivity" to (launchResult.app.launchActivity ?: ""),
                        "launched" to true
                    ),
                    executionTimeMs = elapsed
                )
            }
            is AppLaunchResult.NotInstalled -> {
                ToolExecutionResult.failure(
                    errorMessage = "${launchResult.requestedName} is not installed.",
                    executionTimeMs = elapsed
                )
            }
            is AppLaunchResult.CannotLaunch -> {
                ToolExecutionResult.failure(
                    errorMessage = launchResult.message,
                    executionTimeMs = elapsed
                )
            }
            is AppLaunchResult.Error -> {
                ToolExecutionResult.failure(
                    errorMessage = launchResult.message,
                    executionTimeMs = elapsed
                )
            }
        }
    }
}
