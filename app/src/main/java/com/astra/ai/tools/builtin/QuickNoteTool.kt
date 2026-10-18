package com.astra.ai.tools.builtin

import com.astra.ai.memory.MemoryRepository
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Built-in tool for persisting facts and quick notes into Astra AI's long-term memory.
 */
class QuickNoteTool(private val memoryRepository: MemoryRepository) : Tool {
    override val name: String = "quick_note"
    override val description: String = "Saves an important note, reminder, or personal fact into Astra AI's persistent memory."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "key",
            type = ToolParamType.STRING,
            description = "Topic, label, or key of the memory (e.g. 'wifi_password', 'favorite_coffee', 'project_deadline').",
            required = true
        ),
        ToolParameter(
            name = "value",
            type = ToolParamType.STRING,
            description = "The detailed content or value to remember.",
            required = true
        ),
        ToolParameter(
            name = "category",
            type = ToolParamType.STRING,
            description = "Optional category: 'general', 'work', 'personal', 'preference'.",
            required = false,
            defaultValue = "general"
        )
    )
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val key = params["key"]?.toString()?.trim()
        val value = params["value"]?.toString()?.trim()
        val category = params["category"]?.toString()?.trim() ?: "general"

        if (key.isNullOrBlank() || value.isNullOrBlank()) {
            return ToolExecutionResult.failure(
                errorMessage = "Both 'key' and 'value' parameters are required to save a note.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val id = memoryRepository.saveMemory(key = key, value = value, category = category)
            ToolExecutionResult.success(
                displayText = "Saved memory #$id: '$key' -> '$value' ($category)",
                output = mapOf("id" to id, "key" to key, "value" to value, "category" to category),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolExecutionResult.failure(
                errorMessage = "Failed to save memory: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
