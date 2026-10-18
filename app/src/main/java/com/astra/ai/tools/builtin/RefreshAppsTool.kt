package com.astra.ai.tools.builtin

import com.astra.ai.apps.AppManager
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: refresh_apps
 * Refreshes the local application discovery registry when the user installs a new app.
 */
class RefreshAppsTool(
    private val appManager: AppManager
) : Tool {

    override val name: String = "refresh_apps"

    override val description: String =
        "Refreshes the installed applications registry to discover newly installed or removed Android apps."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "query",
            type = ToolParamType.STRING,
            description = "Optional app name being searched for after refresh",
            required = false
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val query = params["query"] as? String

        appManager.refreshInstalledApps()
        val apps = appManager.getInstalledApps()
        val elapsed = System.currentTimeMillis() - startTime

        val foundApp = if (!query.isNullOrBlank()) appManager.findAppByName(query) else null

        val message = when {
            foundApp != null -> "I refreshed the application registry and found ${foundApp.appName}."
            !query.isNullOrBlank() -> "I refreshed the application registry with ${apps.size} apps, but \"$query\" is not installed."
            else -> "Discovered ${apps.size} installed applications."
        }

        return ToolExecutionResult.success(
            displayText = message,
            output = mapOf(
                "totalApps" to apps.size,
                "foundApp" to (foundApp?.appName ?: "")
            ),
            executionTimeMs = elapsed
        )
    }
}
