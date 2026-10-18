package com.astra.ai.tools.builtin

import android.content.Context
import com.astra.ai.apps.AppLaunchResult
import com.astra.ai.apps.AppManager
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: open_app
 * Dynamically discovers and launches installed applications on the Android device.
 * Never fakes success: verifies launchable activity existence and reports precise status.
 */
class OpenAppTool(
    private val context: Context,
    private val appManager: AppManager
) : Tool {

    override val name: String = "open_app"

    override val description: String =
        "Opens or launches any dynamically installed application on the user's Android device by app name or package name."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "appName",
            type = ToolParamType.STRING,
            description = "Common human-readable name of the application (e.g. Spotify, WhatsApp, YouTube, Camera, Instagram, Settings)",
            required = false
        ),
        ToolParameter(
            name = "packageName",
            type = ToolParamType.STRING,
            description = "Android package name if known (e.g. com.spotify.music, com.whatsapp)",
            required = false
        ),
        ToolParameter(
            name = "checkOnly",
            type = ToolParamType.BOOLEAN,
            description = "If true, checks if the app is installed without attempting to launch it",
            required = false
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val pkg = params["packageName"] as? String
        val appName = params["appName"] as? String
        val checkOnly = params["checkOnly"] == true || params["checkOnly"] == "true"

        val query = when {
            !pkg.isNullOrBlank() -> pkg
            !appName.isNullOrBlank() -> appName
            else -> null
        }

        if (query.isNullOrBlank()) {
            return ToolExecutionResult.failure("Please specify an application name or package to open.")
        }

        if (checkOnly) {
            val foundApp = appManager.findAppByName(query)
            val isInstalled = foundApp != null || context.packageManager.getLaunchIntentForPackage(query) != null
            val elapsed = System.currentTimeMillis() - startTime

            return if (isInstalled) {
                val name = foundApp?.appName ?: query
                val packageName = foundApp?.packageName ?: query
                ToolExecutionResult.success(
                    displayText = "Yes, $name is installed on your phone (package: $packageName).",
                    output = mapOf(
                        "appName" to name,
                        "packageName" to packageName,
                        "isInstalled" to true,
                        "checked" to true
                    ),
                    executionTimeMs = elapsed
                )
            } else {
                ToolExecutionResult.success(
                    displayText = "No, \"$query\" is not installed on your phone.",
                    output = mapOf(
                        "query" to query,
                        "isInstalled" to false,
                        "checked" to true
                    ),
                    executionTimeMs = elapsed
                )
            }
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
