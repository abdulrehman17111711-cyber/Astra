package com.astra.ai.ai

/**
 * Result produced by the AI reasoning process.
 */
sealed interface AiReasoningResult {
    /**
     * AI decided to execute a registered tool.
     */
    data class ToolCall(
        val toolName: String,
        val parameters: Map<String, Any?>,
        val rationale: String? = null
    ) : AiReasoningResult

    /**
     * AI answered the user directly without invoking a tool.
     */
    data class DirectResponse(
        val text: String
    ) : AiReasoningResult

    /**
     * Tool requires user confirmation before execution.
     */
    data class RequiresConfirmation(
        val toolName: String,
        val actionTitle: String,
        val description: String,
        val parameters: Map<String, Any?>
    ) : AiReasoningResult

    /**
     * AI could not understand or execute the request.
     */
    data class Error(
        val message: String
    ) : AiReasoningResult
}
