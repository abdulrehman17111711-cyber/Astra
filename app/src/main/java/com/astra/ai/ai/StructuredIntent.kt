package com.astra.ai.ai

/**
 * Structured AI intent extracted from natural user speech or text.
 * Captures the user's actual goal, target application or system feature,
 * cleaned parameters, execution plan, and verification strategy.
 */
data class StructuredIntent(
    val action: String,
    val targetApp: String? = null,
    val goal: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val executionSteps: List<String> = emptyList(),
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val verificationStrategy: String? = null,
    val mappedTool: String? = null,
    val mappedArguments: Map<String, Any?> = emptyMap(),
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val rationale: String? = null
) {
    companion object {
        const val ACTION_SEARCH = "SEARCH"
        const val ACTION_LAUNCH = "LAUNCH"
        const val ACTION_CALL = "CALL"
        const val ACTION_MESSAGE = "MESSAGE"
        const val ACTION_ALARM = "ALARM"
        const val ACTION_NOTE = "NOTE"
        const val ACTION_DEVICE_STATUS = "DEVICE_STATUS"
        const val ACTION_CLOCK = "CLOCK"
        const val ACTION_NOTIFICATION = "NOTIFICATION"
        const val ACTION_READ_NOTIFICATION = "READ_NOTIFICATION"
        const val ACTION_REFRESH_APPS = "REFRESH_APPS"
        const val ACTION_ACCESSIBILITY = "ACCESSIBILITY"
        const val ACTION_SYSTEM_CONTROL = "SYSTEM_CONTROL"
        const val ACTION_CONVERSATION = "CONVERSATION"
    }
}
