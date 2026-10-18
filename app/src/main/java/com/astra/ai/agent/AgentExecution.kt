package com.astra.ai.agent

/**
 * Execution cycle model tracking active agent decision lifecycle.
 */
sealed interface AgentExecution {
    data class Planning(val query: String) : AgentExecution
    data class RequiresConfirmation(val call: ToolCall, val confirmationPrompt: String) : AgentExecution
    data class ExecutingTool(val call: ToolCall) : AgentExecution
    data class Completed(val finalResponse: String, val lastResult: ToolResult? = null) : AgentExecution
    data class Failed(val error: String) : AgentExecution
}
