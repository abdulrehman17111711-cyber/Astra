package com.astra.ai.assistant

import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult

/**
 * Result verification subsystem: validates that tool outputs conform to expected invariants
 * before generating user responses.
 */
class ResultVerifier {

    /**
     * Verifies that the tool execution output is well-formed and conforms to expected invariants.
     */
    fun verify(tool: Tool, result: ToolExecutionResult): VerificationResult {
        // Invariant 1: Output must provide meaningful display text or error description
        if (result.displayText.isBlank() && result.error.isNullOrBlank()) {
            AstraLogger.w("ResultVerifier", "Tool '${tool.name}' returned blank display text and no error")
            return VerificationResult.Failed("Output display text was empty")
        }

        // Invariant 2: Tool-specific internal consistency check
        val toolVerified = tool.verify(result)
        if (!toolVerified) {
            AstraLogger.w("ResultVerifier", "Tool '${tool.name}' internal validation rejected the output")
            return VerificationResult.Failed("Tool internal validation rejected the output")
        }

        if (!result.success) {
            AstraLogger.i("ResultVerifier", "Tool '${tool.name}' completed with reported failure: ${result.error}")
        }

        return VerificationResult.Passed
    }
}

sealed interface VerificationResult {
    data object Passed : VerificationResult
    data class Failed(val reason: String) : VerificationResult
}
