package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: sms_tool
 * Prepares and sends SMS messages using supported Android SMS APIs/intents.
 *
 * Example: "Ali ko message bhejo ke main 10 minute mein aa raha hoon."
 *
 * Consequential action:
 * - requiresConfirmation = true
 * - Prompts user before sending: "Ali ko ye message bhejun?"
 * - Never silently transmits external messages.
 */
class SmsTool(
    private val context: Context
) : Tool {

    override val name: String = "sms_tool"

    override val description: String =
        "Composes and sends an SMS text message to a contact or phone number. Requires user confirmation."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "recipient",
            type = ToolParamType.STRING,
            description = "Contact name (e.g. Ali, Abu, Mom) or direct phone number",
            required = true
        ),
        ToolParameter(
            name = "message",
            type = ToolParamType.STRING,
            description = "SMS message body text",
            required = true
        )
    )

    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CONTACTS
    )

    override val requiresConfirmation: Boolean = true

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val recipient = (params["recipient"] as? String)?.trim()
        val message = (params["message"] as? String)?.trim()

        if (recipient.isNullOrBlank()) {
            return ToolExecutionResult.failure("Recipient is required to compose SMS.")
        }
        if (message.isNullOrBlank()) {
            return ToolExecutionResult.failure("Message body cannot be empty.")
        }

        // 1. Resolve phone number from contact name if needed
        val resolvedNumber = resolvePhoneNumber(recipient)
        val finalTarget = if (resolvedNumber.isNotBlank()) resolvedNumber else recipient

        return try {
            // Android official SMS compose intent
            val smsUri = Uri.parse("smsto:$finalTarget")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", message)
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val elapsed = System.currentTimeMillis() - startTime
                ToolExecutionResult.success(
                    displayText = "Opened SMS composer for $recipient with message: \"$message\".",
                    output = mapOf(
                        "recipient" to recipient,
                        "phoneNumber" to finalTarget,
                        "message" to message
                    ),
                    executionTimeMs = elapsed
                )
            } else {
                // Generic send fallback
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:$finalTarget?body=${Uri.encode(message)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                val elapsed = System.currentTimeMillis() - startTime
                ToolExecutionResult.success(
                    displayText = "Prepared SMS for $recipient: \"$message\".",
                    output = mapOf("recipient" to recipient, "message" to message),
                    executionTimeMs = elapsed
                )
            }
        } catch (e: Exception) {
            AstraLogger.e("SmsTool", "Failed to dispatch SMS intent: ${e.message}")
            ToolExecutionResult.failure("Failed to send SMS to $recipient: ${e.message}")
        }
    }

    private fun resolvePhoneNumber(target: String): String {
        val digitsOnly = target.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 3) {
            return digitsOnly
        }

        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasContactPermission) {
            val contacts = ContactTool.searchContacts(context.contentResolver, target)
            if (contacts.isNotEmpty()) {
                return contacts.first().phoneNumber.filter { it.isDigit() || it == '+' }
            }
        }
        return ""
    }
}
