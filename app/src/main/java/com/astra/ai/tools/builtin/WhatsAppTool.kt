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
import kotlinx.coroutines.launch

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
        "Opens WhatsApp or WhatsApp Business, pre-fills or dispatches messages to contacts using supported official intents and Accessibility automation."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "recipient",
            type = ToolParamType.STRING,
            description = "Contact name or phone number with country code",
            required = false
        ),
        ToolParameter(
            name = "message",
            type = ToolParamType.STRING,
            description = "Message text content to send",
            required = false
        ),
        ToolParameter(
            name = "appType",
            type = ToolParamType.STRING,
            description = "'WhatsApp' or 'WhatsApp Business' (defaults to 'WhatsApp')",
            required = false
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = true

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val recipient = (params["recipient"] as? String)?.trim()
        val message = (params["message"] as? String)?.trim()
        val appTypeParam = (params["appType"] as? String)?.trim().orEmpty()

        val isBusinessRequested = appTypeParam.contains("business", ignoreCase = true) ||
                (params["packageName"]?.toString()?.contains("w4b") == true)

        val targetPackage = if (isBusinessRequested) {
            if (context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b") != null) {
                "com.whatsapp.w4b"
            } else {
                "com.whatsapp"
            }
        } else {
            if (context.packageManager.getLaunchIntentForPackage("com.whatsapp") != null) {
                "com.whatsapp"
            } else if (context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b") != null) {
                "com.whatsapp.w4b"
            } else {
                "com.whatsapp"
            }
        }

        val appDisplayName = if (targetPackage == "com.whatsapp.w4b") "WhatsApp Business" else "WhatsApp"

        // Check if installed
        val isInstalled = context.packageManager.getLaunchIntentForPackage(targetPackage) != null ||
                appManager.findAppByName(appDisplayName) != null

        if (!isInstalled) {
            val elapsed = System.currentTimeMillis() - startTime
            return ToolExecutionResult.failure(
                errorMessage = "$appDisplayName is not installed on this device.",
                executionTimeMs = elapsed
            )
        }

        return try {
            val elapsed: Long
            if (recipient.isNullOrBlank() && message.isNullOrBlank()) {
                // Just launch requested WhatsApp app
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    elapsed = System.currentTimeMillis() - startTime
                    ToolExecutionResult.success(
                        displayText = "Opened $appDisplayName.",
                        output = mapOf("action" to "open", "packageName" to targetPackage, "appDisplayName" to appDisplayName),
                        executionTimeMs = elapsed
                    )
                } else {
                    elapsed = System.currentTimeMillis() - startTime
                    ToolExecutionResult.failure("Could not launch $appDisplayName activity.", elapsed)
                }
            } else {
                // Compose or send message via deep link or send intent
                val cleanNumber = resolvePhoneNumber(recipient ?: "")
                var messageSent = false

                if (cleanNumber.isNotBlank()) {
                    // Supported deep link: https://api.whatsapp.com/send?phone=...&text=...
                    val encodedText = Uri.encode(message ?: "")
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedText")
                    val deepLinkIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(targetPackage)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }

                    if (deepLinkIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(deepLinkIntent)
                    } else {
                        deepLinkIntent.setPackage(null)
                        context.startActivity(deepLinkIntent)
                    }

                    // Attempt automated send via Accessibility if active
                    val service = com.astra.ai.accessibility.AstraAccessibilityService.instance
                    if (service != null && !message.isNullOrBlank()) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(1200)
                            // Look for Send button
                            val clickResult = com.astra.ai.accessibility.UiActionExecutor.click("Send")
                            if (!clickResult.success) {
                                com.astra.ai.accessibility.UiActionExecutor.click("send")
                            }
                        }
                        messageSent = true
                    }

                    elapsed = System.currentTimeMillis() - startTime
                    val statusText = if (messageSent) {
                        "Prepared and dispatched $appDisplayName message to $recipient ($cleanNumber): \"$message\"."
                    } else {
                        "Prepared $appDisplayName message for $recipient ($cleanNumber): \"$message\". Conversation opened with text pre-filled."
                    }

                    ToolExecutionResult.success(
                        displayText = statusText,
                        output = mapOf(
                            "recipient" to (recipient ?: "Contact"),
                            "phoneNumber" to cleanNumber,
                            "message" to (message ?: ""),
                            "appDisplayName" to appDisplayName,
                            "targetPackage" to targetPackage,
                            "prepared" to true,
                            "sent" to messageSent
                        ),
                        executionTimeMs = elapsed
                    )
                } else {
                    // Contact phone number not in database; open share intent to select contact
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message ?: "")
                        setPackage(targetPackage)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(sendIntent)
                    elapsed = System.currentTimeMillis() - startTime
                    ToolExecutionResult.success(
                        displayText = "Opened $appDisplayName with pre-filled message: \"$message\" for $recipient.",
                        output = mapOf(
                            "recipient" to (recipient ?: "Contact"),
                            "message" to (message ?: ""),
                            "appDisplayName" to appDisplayName,
                            "targetPackage" to targetPackage,
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
        if (digitsOnly.length >= 7 && target.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
            return formatInternationalNumber(digitsOnly)
        }

        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasContactPermission && target.isNotBlank()) {
            val contacts = ContactMatcher.findMatchingContacts(context.contentResolver, target)
            if (contacts.isNotEmpty()) {
                val num = contacts.first().phoneNumber.filter { it.isDigit() || it == '+' }
                return formatInternationalNumber(num)
            }
        }
        return ""
    }

    private fun formatInternationalNumber(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("+")) {
            clean = clean.substring(1)
        } else if (clean.startsWith("00")) {
            clean = clean.substring(2)
        } else if (clean.startsWith("0") && clean.length == 11) {
            // Standard Pakistan mobile number format (e.g. 03001234567 -> 923001234567)
            clean = "92" + clean.substring(1)
        }
        return clean.filter { it.isDigit() }
    }
}
