package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: send_message
 * Composes and sends a message to a recipient using WhatsApp or SMS.
 * Requires user confirmation before execution.
 */
class SendMessageTool(
    private val context: Context
) : Tool {

    override val name: String = "send_message"

    override val description: String =
        "Sends or composes a message to a recipient via WhatsApp, SMS, or system messaging apps."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "recipient",
            type = ToolParamType.STRING,
            description = "Name, contact, or phone number of the message recipient",
            required = true
        ),
        ToolParameter(
            name = "message",
            type = ToolParamType.STRING,
            description = "Body content of the message to be sent",
            required = true
        ),
        ToolParameter(
            name = "application",
            type = ToolParamType.STRING,
            description = "Target messaging application (e.g. WhatsApp, SMS, Default)",
            required = false
        )
    )

    override val requiresConfirmation: Boolean = true

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val recipient = params["recipient"] as? String ?: return ToolExecutionResult.failure("Recipient is required.")
        val message = params["message"] as? String ?: return ToolExecutionResult.failure("Message content is required.")
        val app = (params["application"] as? String) ?: "Default Messaging"

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val packageToUse = when {
                app.contains("whatsapp business", ignoreCase = true) -> "com.whatsapp.w4b"
                app.contains("whatsapp", ignoreCase = true) -> "com.whatsapp"
                app.contains("telegram", ignoreCase = true) -> "org.telegram.messenger"
                else -> null
            }

            if (packageToUse != null) {
                intent.setPackage(packageToUse)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)

                // Trigger automated send click via Accessibility if active
                val service = com.astra.ai.accessibility.AstraAccessibilityService.instance
                var autoDispatched = false
                if (service != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(1200)
                        service.performSendClick()
                    }
                    autoDispatched = true
                }

                val statusText = if (autoDispatched) {
                    "Automating message send to $recipient via $app: \"$message\""
                } else {
                    "Prepared message for $recipient via $app: \"$message\""
                }

                ToolExecutionResult.success(
                    displayText = statusText,
                    output = mapOf(
                        "recipient" to recipient,
                        "message" to message,
                        "app" to app,
                        "automated" to autoDispatched
                    ),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                ToolExecutionResult.failure("No compatible messaging app found on device for $app.")
            }
        } catch (e: Exception) {
            ToolExecutionResult.failure("Failed to send message: ${e.message}")
        }
    }
}
