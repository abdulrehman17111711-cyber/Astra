package com.astra.ai.security

import com.astra.ai.assistant.ConfirmationRequest
import com.astra.ai.assistant.RiskLevel
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Categories of consequential actions that MUST require explicit user confirmation
 * to prevent unintentional side-effects, privacy leaks, or AI hallucinations.
 */
enum class ConsequentialActionType(val displayName: String, val defaultRisk: RiskLevel) {
    PHONE_CALL("Phone Call", RiskLevel.HIGH),
    SEND_SMS("Send SMS", RiskLevel.HIGH),
    SEND_WHATSAPP("Send WhatsApp Message", RiskLevel.HIGH),
    POST_COMMENT("Post Social Comment", RiskLevel.HIGH),
    DELETE_CONTENT("Delete Data/Content", RiskLevel.HIGH),
    PURCHASE("Financial Purchase/Payment", RiskLevel.HIGH),
    ACCOUNT_CHANGE("Account Setting Change", RiskLevel.HIGH),
    IRREVERSIBLE_ACTION("Irreversible System Action", RiskLevel.HIGH)
}

/**
 * Detailed confirmation pending item.
 */
data class ActionConfirmation(
    val id: String = UUID.randomUUID().toString(),
    val actionType: ConsequentialActionType,
    val title: String,
    val prompt: String,
    val targetApp: String? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val onConfirm: (suspend () -> Unit)? = null,
    val onCancel: (suspend () -> Unit)? = null
) {
    fun toConfirmationRequest(): ConfirmationRequest {
        return ConfirmationRequest(
            id = id,
            toolName = actionType.name.lowercase(),
            actionTitle = title,
            description = prompt,
            parameters = parameters,
            riskLevel = actionType.defaultRisk
        )
    }
}

/**
 * Security coordinator ensuring no consequential external action occurs without explicit confirmation.
 */
class ConfirmationManager {

    private val _pendingConfirmation = MutableStateFlow<ActionConfirmation?>(null)
    val pendingConfirmation: StateFlow<ActionConfirmation?> = _pendingConfirmation.asStateFlow()

    /**
     * Determines whether an intended tool or action is consequential and requires user confirmation.
     */
    fun isConsequentialAction(toolName: String, params: Map<String, Any?> = emptyMap()): Boolean {
        val lower = toolName.lowercase()
        return when {
            lower.contains("call") -> true
            lower.contains("sms") || lower.contains("send_message") -> true
            lower.contains("whatsapp") && params.containsKey("message") -> true
            lower.contains("delete") || lower.contains("remove") || lower.contains("clear") -> true
            lower.contains("purchase") || lower.contains("pay") || lower.contains("buy") -> true
            lower.contains("account") || lower.contains("password") -> true
            lower.contains("comment") || lower.contains("post") -> true
            lower.contains("click_ui") && isConsequentialUiClick(params) -> true
            else -> false
        }
    }

    /**
     * Builds a specific canonical key representing a tool and recipient/target for trust tracking.
     */
    fun buildActionTrustKey(toolName: String, params: Map<String, Any?> = emptyMap()): String {
        val target = (params["contactOrNumber"] ?: params["contactName"] ?: params["phoneNumber"] ?: params["recipient"] ?: params["appName"] ?: "").toString().trim().lowercase()
        return if (target.isNotBlank()) "$toolName:$target" else toolName
    }

    /**
     * Evaluates confirmation requirement taking into account user's Trusted Automation settings and safeguards.
     */
    fun isConfirmationRequired(
        toolName: String,
        params: Map<String, Any?> = emptyMap(),
        settings: com.astra.ai.settings.AstraSettings? = null
    ): Boolean {
        val lower = toolName.lowercase()
        // Strict safeguards: Irreversible actions (financial, deletion, account) always require user confirmation
        val isAlwaysStrict = lower.contains("delete") || lower.contains("remove") || lower.contains("clear") ||
                lower.contains("purchase") || lower.contains("pay") || lower.contains("buy") ||
                lower.contains("account") || lower.contains("password")
        if (isAlwaysStrict) {
            return true
        }

        if (settings != null) {
            if (settings.safeguardLevel == "STRICT") {
                return isConsequentialAction(toolName, params)
            }

            if (settings.trustedAutomationEnabled) {
                val actionKey = buildActionTrustKey(toolName, params)
                if (settings.trustedActions.contains(actionKey) || settings.trustedActions.contains(toolName)) {
                    AstraLogger.i("ConfirmationManager", "Bypassing confirmation for trusted action: $actionKey")
                    return false
                }

                if (settings.safeguardLevel == "TRUSTED_AUTOMATION") {
                    val isRoutine = lower.contains("alarm") || lower.contains("clock") || lower.contains("search") ||
                            lower.contains("open_app") || lower.contains("quick_note") || lower.contains("notification")
                    if (isRoutine) return false
                }
            }
        }

        return isConsequentialAction(toolName, params)
    }

    private fun isConsequentialUiClick(params: Map<String, Any?>): Boolean {
        val target = (params["target"] as? String)?.lowercase().orEmpty()
        val commentKeywords = listOf("post", "comment", "send", "delete", "buy", "pay", "confirm", "share")
        return commentKeywords.any { target.contains(it) }
    }

    /**
     * Classifies a tool and arguments into a ConsequentialActionType.
     */
    fun classifyAction(toolName: String, params: Map<String, Any?>): ConsequentialActionType? {
        val lower = toolName.lowercase()
        return when {
            lower.contains("call") -> ConsequentialActionType.PHONE_CALL
            lower.contains("sms") -> ConsequentialActionType.SEND_SMS
            lower.contains("whatsapp") -> ConsequentialActionType.SEND_WHATSAPP
            lower.contains("delete") || lower.contains("clear") -> ConsequentialActionType.DELETE_CONTENT
            lower.contains("purchase") || lower.contains("pay") -> ConsequentialActionType.PURCHASE
            lower.contains("account") -> ConsequentialActionType.ACCOUNT_CHANGE
            lower.contains("comment") || (lower.contains("click") && isConsequentialUiClick(params)) -> ConsequentialActionType.POST_COMMENT
            isConsequentialAction(toolName, params) -> ConsequentialActionType.IRREVERSIBLE_ACTION
            else -> null
        }
    }

    /**
     * Generates bilingual/natural confirmation prompt (Urdu/Roman English & English)
     * adhering to Astra's conversational personality.
     */
    fun buildConfirmationPrompt(
        type: ConsequentialActionType,
        params: Map<String, Any?>
    ): Pair<String, String> {
        return when (type) {
            ConsequentialActionType.PHONE_CALL -> {
                val contact = params["contact"] ?: params["number"] ?: "contact"
                Pair(
                    "Confirm Phone Call",
                    "Kya aap $contact ko call milana chahte hain?"
                )
            }
            ConsequentialActionType.SEND_SMS -> {
                val recipient = params["recipient"] ?: params["number"] ?: "recipient"
                val body = params["message"] ?: params["body"] ?: ""
                Pair(
                    "Confirm Sending SMS",
                    "$recipient ko ye SMS bhej dun?\n\"$body\""
                )
            }
            ConsequentialActionType.SEND_WHATSAPP -> {
                val recipient = params["recipient"] ?: params["contact"] ?: "contact"
                val msg = params["message"] ?: ""
                Pair(
                    "Confirm WhatsApp Message",
                    "WhatsApp par $recipient ko ye message send karun?\n\"$msg\""
                )
            }
            ConsequentialActionType.POST_COMMENT -> {
                val app = params["app"] ?: "Instagram"
                val comment = params["comment"] ?: params["text"] ?: ""
                Pair(
                    "Confirm Posting Comment",
                    if (comment.toString().isNotBlank()) {
                        "$app par ye comment post karun?\n\"$comment\""
                    } else {
                        "$app par ye comment post karun?"
                    }
                )
            }
            ConsequentialActionType.DELETE_CONTENT -> {
                val item = params["item"] ?: params["target"] ?: "selected items"
                Pair(
                    "Confirm Deletion",
                    "Kya aap waqai $item delete karna chahte hain? Ye action wapas nahi ho sakta."
                )
            }
            ConsequentialActionType.PURCHASE -> {
                val item = params["item"] ?: "item"
                Pair(
                    "Confirm Purchase",
                    "Kya aap $item ki payment/purchase ki tasdeeq karte hain?"
                )
            }
            ConsequentialActionType.ACCOUNT_CHANGE -> {
                Pair(
                    "Confirm Account Change",
                    "Aapke account ya security settings tabdeel hone lagi hain. Kya tasdeeq karte hain?"
                )
            }
            ConsequentialActionType.IRREVERSIBLE_ACTION -> {
                Pair(
                    "Confirm Irreversible Action",
                    "Ye action irreversible hai. Kya aap isay anjaam dena chahte hain?"
                )
            }
        }
    }

    /**
     * Posts a confirmation request for user verification.
     */
    fun requestConfirmation(confirmation: ActionConfirmation) {
        AstraLogger.i("ConfirmationManager", "Requested confirmation: ${confirmation.actionType} - ${confirmation.title}")
        _pendingConfirmation.value = confirmation
    }

    /**
     * Executes the confirmation callback and resets pending state.
     */
    suspend fun confirm(id: String): Boolean {
        val current = _pendingConfirmation.value
        if (current != null && current.id == id) {
            AstraLogger.i("ConfirmationManager", "Confirmed action: ${current.actionType}")
            _pendingConfirmation.value = null
            current.onConfirm?.invoke()
            return true
        }
        return false
    }

    /**
     * Executes the cancellation callback and resets pending state.
     */
    suspend fun cancel(id: String): Boolean {
        val current = _pendingConfirmation.value
        if (current != null && current.id == id) {
            AstraLogger.i("ConfirmationManager", "Cancelled action: ${current.actionType}")
            _pendingConfirmation.value = null
            current.onCancel?.invoke()
            return true
        }
        return false
    }

    fun clear() {
        _pendingConfirmation.value = null
    }
}
