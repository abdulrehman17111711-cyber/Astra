package com.astra.ai.tools.builtin

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: browser_tool
 * Opens URLs or conducts web searches using official Android ACTION_VIEW / ACTION_WEB_SEARCH intents.
 */
class BrowserTool(
    private val context: Context
) : Tool {

    override val name: String = "browser_tool"

    override val description: String =
        "Opens websites via URL or performs a web search in the default web browser."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "queryOrUrl",
            type = ToolParamType.STRING,
            description = "Website URL (e.g. https://android.com) or search query",
            required = true
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val input = (params["queryOrUrl"] as? String)?.trim()
        if (input.isNullOrBlank()) {
            return ToolExecutionResult.failure("Web search query or URL is required.")
        }

        return try {
            val isUrl = input.startsWith("http://", ignoreCase = true) ||
                    input.startsWith("https://", ignoreCase = true) ||
                    (input.contains(".") && !input.contains(" ") && input.length > 4)

            val intent = if (isUrl) {
                val fullUrl = if (!input.startsWith("http://", ignoreCase = true) && !input.startsWith("https://", ignoreCase = true)) {
                    "https://$input"
                } else input
                Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, input)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Guaranteed fallback
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(input)}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            }

            val elapsed = System.currentTimeMillis() - startTime
            val message = if (isUrl) "Opening website: $input" else "Searching web for: \"$input\""

            ToolExecutionResult.success(
                displayText = message,
                output = mapOf("queryOrUrl" to input, "isUrl" to isUrl),
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            AstraLogger.e("BrowserTool", "Failed to launch browser: ${e.message}")
            ToolExecutionResult.failure("Failed to open browser: ${e.message}")
        }
    }
}
