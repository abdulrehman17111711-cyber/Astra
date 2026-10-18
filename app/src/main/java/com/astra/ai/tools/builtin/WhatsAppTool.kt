package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.astra.ai.apps.AppManager
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: whatsapp_tool
 * Manages WhatsApp interactions using supported Android mechanisms:
 * - Direct app launch
 * - Send intent (android.intent.action.SEND) targeted to WhatsApp
 * - Deep link (api.whatsapp.com/send?phone=...&text=...)
 *
 * Security & Anti-abuse:
 * - Does not bypass CAPTCHA, 2FA, login security, or privacy restrictions
 * - External messages require confirmation (requiresConfirmation = true when recipient or message specified)
 */
class WhatsAppTool(
    private val context: Context,
    private val appManager: AppManager
) : Tool {

    override val name: String = "whatsapp_tool"

    override val description: String =
        "Opens WhatsApp or sends a message to a WhatsApp recipient using supported official Android intents and deep links."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "recipient",
            type = ToolParamType.STRING,
            description = "Contact name or phone number with country code (optional, leave blank to just open WhatsApp)",
            required = false
        ),
        ToolParameter(
            name = "message",
            type = ToolParamType.STRING,
            description = "Message text content to send (optional)",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = true

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val recipient = (params["recipient"] as? String)?.trim()
        val message = (params["message"] as? String)?.trim()

        // Check if WhatsApp is installed
        val isInstalled = appManager.findAppByName("WhatsApp") != null ||
                context.packageManager.getLaunchIntentForPackage("com.whatsapp") != null ||
                context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b") != null

        if (!isInstalled) {
            val elapsed = System.currentTimeMillis() - startTime
            return ToolExecutionResult.failure(
                errorMessage = "WhatsApp is not installed on this device.",
                executionTimeMs = elapsed
            )
        }

        return try {
            val elapsed: Long
            if (recipient.isNullOrBlank() && message.isNullOrBlank()) {
                // Just launch WhatsApp
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                    ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")

                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    elapsed = System.currentTimeMillis() - startTime
                    ToolExecutionResult.success(
                        displayText = "Opened WhatsApp.",
                        output = mapOf("action" to "open", "packageName" to "com.whatsapp"),
                        executionTimeMs = elapsed
                    )
                } else {
                    elapsed = System.currentTimeMillis() - startTime
                    ToolExecutionResult.failure("Could not launch WhatsApp activity.", elapsed)
                }
            } else {
                // Compose or send message via deep link or send intent
                val cleanNumber = resolvePhoneNumber(recipient ?: "")
                if (cleanNumber.isNotBlank()) {
                    // Supported deep link: https://api.whatsapp.com/send?phone=...&text=...
                    val encodedText = Uri.encode(message ?: "")
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedText")
                    val deepLinkIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.whatsapp")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (deepLinkIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(deepLinkIntent)
                    } else {
                        // Without package lock
                        deepLinkIntent.setPackage(null)
                        context.startActivity(deepLinkIntent)
                    }
                    elapsed = System.currentTimeMillis() - startTime
                    ToolExecutionResult.success(
                        displayText = "Prepared WhatsApp message for $recipient ($cleanNumber): \"$message\". Conversation opened with text pre-filled; tap send to dispatch.",
                        output = mapOf(
                            "recipient" to recipient,
                            "phoneNumber" to cleanNumber,
                            "message" to (message ?: ""),
                            "prepared" to true,
                            "sent" to false
                        ),
                        executionTimeMs = elapsed
                    )
                } else {
                    // Share intent with message
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message ?: "")
                        setPackage("com.whatsapp")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(sendIntent)
                    elapsed = System.currentTimeMillis() - startTime
                    ToolExecutionResult.success(
                        displayText = "Prepared WhatsApp message for $recipient: \"$message\". Opened WhatsApp picker for dispatch.",
                        output = mapOf(
                            "recipient" to (recipient ?: ""),
                            "message" to (message ?: ""),
                            "prepared" to true,
                            "sent" to false
                        ),
                        executionTimeMs = elapsed
                    )
                }
            }
        } catch (e: Exception) {
            AstraLogger.e("WhatsAppTool", "Error during WhatsApp tool execution: ${e.message}")
            ToolExecutionResult.failure("Failed to send WhatsApp message: ${e.message}")
        }
    }

    private fun resolvePhoneNumber(target: String): String {
        val digitsOnly = target.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 7) {
            return digitsOnly.removePrefix("+")
        }

        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasContactPermission && target.isNotBlank()) {
            val contacts = ContactTool.searchContacts(context.contentResolver, target)
            if (contacts.isNotEmpty()) {
                val num = contacts.first().phoneNumber.filter { it.isDigit() }
                return num
            }
        }
        return ""
    }
}
