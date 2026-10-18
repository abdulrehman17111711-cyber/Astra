package com.astra.ai.tools

/**
 * Foundational interface for all capabilities executable by Astra AI.
 * Each tool encapsulates its metadata, parameters, permission requirements,
 * confirmation policy, execution logic, result verification, and error handling.
 */
interface Tool {
    /**
     * Unique identifier for the tool (e.g. "device_status", "world_clock", "save_memory").
     */
    val name: String

    /**
     * Human and model-readable description of what the tool accomplishes.
     */
    val description: String

    /**
     * List of parameters required or accepted by this tool.
     */
    val parameters: List<ToolParameter>

    /**
     * Android runtime permissions required to execute this tool.
     * Empty if no special permissions are needed.
     */
    val requiredPermissions: List<String>
        get() = emptyList()

    /**
     * Whether this tool requires explicit user confirmation before executing
     * (e.g. sending messages, deleting data, launching sensitive actions).
     */
    val requiresConfirmation: Boolean
        get() = false

    /**
     * Executes the tool with the provided arguments.
     * Implements structured error handling to ensure crashes are caught and reported cleanly.
     *
     * @param params Map of argument name to passed value.
     * @return Structured ToolExecutionResult.
     */
    suspend fun execute(params: Map<String, Any?>): ToolExecutionResult

    /**
     * Verifies the output of the execution for consistency, completeness, and safety.
     *
     * @param result Result produced by execute().
     * @return True if verified as valid; false otherwise.
     */
    fun verify(result: ToolExecutionResult): Boolean {
        return if (result.success) {
            result.displayText.isNotBlank()
        } else {
            !result.error.isNullOrBlank() || result.displayText.isNotBlank()
        }
    }
}
