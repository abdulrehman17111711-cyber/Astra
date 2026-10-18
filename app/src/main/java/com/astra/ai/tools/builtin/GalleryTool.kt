package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: gallery_tool
 * Launches the official Android Media Picker or Gallery using standard intent actions.
 * Examples:
 * "Gallery kholo"
 * "Open photos"
 * "View gallery"
 */
class GalleryTool(
    private val context: Context
) : Tool {

    override val name: String = "gallery_tool"

    override val description: String =
        "Opens the device photo gallery or media picker to browse photos and videos."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "mediaType",
            type = ToolParamType.STRING,
            description = "Filter type: 'images', 'videos', or 'all' (default: all)",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val mediaType = (params["mediaType"] as? String)?.lowercase() ?: "all"

        return try {
            val mimeType = when {
                mediaType.contains("video") -> "video/*"
                mediaType.contains("image") || mediaType.contains("photo") -> "image/*"
                else -> "*/*"
            }

            // Zero-permission Android media picker / gallery viewer
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = mimeType
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val targetIntent = if (intent.resolveActivity(context.packageManager) != null) {
                intent
            } else {
                // Secondary intent: Intent.ACTION_GET_CONTENT
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = mimeType
                    addCategory(Intent.CATEGORY_OPENABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            if (targetIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(targetIntent)
                val elapsed = System.currentTimeMillis() - startTime
                ToolExecutionResult.success(
                    displayText = "Opened Gallery.",
                    output = mapOf("mediaType" to mediaType),
                    executionTimeMs = elapsed
                )
            } else {
                val elapsed = System.currentTimeMillis() - startTime
                ToolExecutionResult.failure("No gallery app found on this device.", elapsed)
            }
        } catch (e: Exception) {
            AstraLogger.e("GalleryTool", "Failed to launch gallery: ${e.message}")
            ToolExecutionResult.failure("Failed to open gallery: ${e.message}")
        }
    }
}
