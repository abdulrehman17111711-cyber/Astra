package com.astra.ai.agent

/**
 * Structured local result returned after a tool is executed on Android.
 * Note: The AI never assumes execution succeeded; it inspects this explicit result.
 */
data class ToolResult(
    val callId: String,
    val tool: String,
    val success: Boolean,
    val outputText: String,
    val error: String? = null,
    val executionTimeMs: Long = 0,
    val data: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(callId: String, tool: String, output: String, timeMs: Long = 0): ToolResult {
            return ToolResult(
                callId = callId,
                tool = tool,
                success = true,
                outputText = output,
                executionTimeMs = timeMs
            )
        }

        fun failure(callId: String, tool: String, error: String, timeMs: Long = 0): ToolResult {
            return ToolResult(
                callId = callId,
                tool = tool,
                success = false,
                outputText = "Error: $error",
                error = error,
                executionTimeMs = timeMs
            )
        }
    }
}
