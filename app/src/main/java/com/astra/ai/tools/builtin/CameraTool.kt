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
 * Tool: camera_tool
 * Launches the device Camera using official Android MediaStore intent.
 * Examples:
 * "Camera kholo"
 * "Open Camera"
 * "Take photo"
 */
class CameraTool(
    private val context: Context
) : Tool {

    override val name: String = "camera_tool"

    override val description: String =
        "Opens the device camera to take photos or record videos."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "mode",
            type = ToolParamType.STRING,
            description = "Camera mode: 'photo' or 'video' (default: photo)",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val mode = (params["mode"] as? String)?.lowercase() ?: "photo"

        return try {
            val action = if (mode.contains("video")) {
                MediaStore.ACTION_VIDEO_CAPTURE
            } else {
                MediaStore.ACTION_IMAGE_CAPTURE
            }

            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Fallback to general camera intent if capture intent lacks standalone activity
            val launchIntent = if (intent.resolveActivity(context.packageManager) != null) {
                intent
            } else {
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            if (launchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(launchIntent)
                val elapsed = System.currentTimeMillis() - startTime
                ToolExecutionResult.success(
                    displayText = "Opened Camera.",
                    output = mapOf("mode" to mode),
                    executionTimeMs = elapsed
                )
            } else {
                val elapsed = System.currentTimeMillis() - startTime
                ToolExecutionResult.failure("No camera application available on this device.", elapsed)
            }
        } catch (e: Exception) {
            AstraLogger.e("CameraTool", "Failed to launch camera: ${e.message}")
            ToolExecutionResult.failure("Failed to open camera: ${e.message}")
        }
    }
}
