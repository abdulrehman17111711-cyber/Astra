package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.astra.ai.apps.AppManager
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: youtube_tool
 * Launches YouTube or performs searches via official deep links and search intents.
 *
 * Example:
 * "YouTube kholo aur Android 16 search karo."
 */
class YouTubeTool(
    private val context: Context,
    private val appManager: AppManager
) : Tool {

    override val name: String = "youtube_tool"

    override val description: String =
        "Opens YouTube or searches videos on YouTube using native app intents or browser fallback."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "query",
            type = ToolParamType.STRING,
            description = "Video search query (optional, leave blank to just open YouTube)",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val rawQuery = (params["query"] as? String)?.trim()
        val query = if (rawQuery.isNullOrBlank()) {
            null
        } else if (rawQuery.lowercase() in setOf("karo", "search", "search karo", "karna", "kar do", "dhoondo", "youtube")) {
            null
        } else {
            val cleaned = YouTubeSearchParser.extractQuery(rawQuery)
            if (cleaned.isNotBlank()) cleaned else rawQuery
        }

        return try {
            val isYouTubeInstalled = appManager.findAppByName("YouTube") != null ||
                    context.packageManager.getLaunchIntentForPackage("com.google.android.youtube") != null

            if (query.isNullOrBlank()) {
                // Just open YouTube
                if (isYouTubeInstalled) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(launchIntent)
                        val elapsed = System.currentTimeMillis() - startTime
                        return ToolExecutionResult.success(
                            displayText = "Opened YouTube.",
                            output = mapOf("action" to "open", "installed" to true),
                            executionTimeMs = elapsed
                        )
                    }
                }

                // Fallback to web
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                val elapsed = System.currentTimeMillis() - startTime
                return ToolExecutionResult.success(
                    displayText = "Opened YouTube in browser.",
                    output = mapOf("action" to "open", "installed" to false),
                    executionTimeMs = elapsed
                )
            } else {
                // Search on YouTube
                if (isYouTubeInstalled) {
                    val appSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", query)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (appSearchIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(appSearchIntent)
                        val elapsed = System.currentTimeMillis() - startTime
                        return ToolExecutionResult.success(
                            displayText = "Searching YouTube for \"$query\"...",
                            output = mapOf("query" to query, "client" to "app"),
                            executionTimeMs = elapsed
                        )
                    }
                }

                // Web deep-link search fallback
                val webSearchIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webSearchIntent)
                val elapsed = System.currentTimeMillis() - startTime
                return ToolExecutionResult.success(
                    displayText = "Searching YouTube for \"$query\"...",
                    output = mapOf("query" to query, "client" to "web"),
                    executionTimeMs = elapsed
                )
            }
        } catch (e: Exception) {
            AstraLogger.e("YouTubeTool", "Failed to interact with YouTube: ${e.message}")
            ToolExecutionResult.failure("Failed to open YouTube: ${e.message}")
        }
    }
}
