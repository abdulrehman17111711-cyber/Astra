package com.astra.ai.tools

/**
 * Structured result returned from executing a Tool in Astra AI.
 */
data class ToolExecutionResult(
    val success: Boolean,
    val output: Map<String, Any?> = emptyMap(),
    val displayText: String,
    val error: String? = null,
    val executionTimeMs: Long = 0L
) {
    companion object {
        fun success(
            displayText: String,
            output: Map<String, Any?> = emptyMap(),
            executionTimeMs: Long = 0L
        ): ToolExecutionResult = ToolExecutionResult(
            success = true,
            output = output,
            displayText = displayText,
            error = null,
            executionTimeMs = executionTimeMs
        )

        fun failure(
            errorMessage: String,
            executionTimeMs: Long = 0L
        ): ToolExecutionResult = ToolExecutionResult(
            success = false,
            output = emptyMap(),
            displayText = errorMessage,
            error = errorMessage,
            executionTimeMs = executionTimeMs
        )
    }
}
