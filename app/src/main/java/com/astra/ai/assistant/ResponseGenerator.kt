package com.astra.ai.assistant

import com.astra.ai.tools.ToolExecutionResult

/**
 * Synthesizes polished, natural conversational responses based on tool results or AI reasoning.
 */
class ResponseGenerator {

    /**
     * Generates a spoken/display response from a tool execution result.
     */
    fun generateToolResponse(query: String, toolName: String, result: ToolExecutionResult): String {
        return when {
            result.displayText.isNotBlank() -> result.displayText
            !result.error.isNullOrBlank() -> result.error
            result.success -> "I've successfully executed $toolName for you."
            else -> "I was unable to complete $toolName."
        }
    }

    /**
     * Generates a user-facing error response.
     */
    fun generateErrorResponse(query: String, error: String): String {
        return "I encountered an issue processing \"$query\": $error"
    }
}
