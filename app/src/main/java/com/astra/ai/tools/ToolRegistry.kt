package com.astra.ai.tools

import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry that dynamically manages and retrieves Astra AI tools.
 */
class ToolRegistry {
    private val toolsMap = ConcurrentHashMap<String, Tool>()
    private val _toolsListFlow = MutableStateFlow<List<Tool>>(emptyList())
    val toolsListFlow: StateFlow<List<Tool>> = _toolsListFlow.asStateFlow()

    /**
     * Registers a new tool into the registry.
     */
    fun register(tool: Tool) {
        toolsMap[tool.name] = tool
        _toolsListFlow.value = toolsMap.values.toList()
        AstraLogger.i("ToolRegistry", "Registered tool: ${tool.name}")
    }

    /**
     * Registers multiple tools at once.
     */
    fun registerAll(tools: Collection<Tool>) {
        tools.forEach { toolsMap[it.name] = it }
        _toolsListFlow.value = toolsMap.values.toList()
        AstraLogger.i("ToolRegistry", "Registered ${tools.size} tools. Total: ${toolsMap.size}")
    }

    /**
     * Unregisters a tool by name.
     */
    fun unregister(name: String): Tool? {
        val removed = toolsMap.remove(name)
        if (removed != null) {
            _toolsListFlow.value = toolsMap.values.toList()
            AstraLogger.i("ToolRegistry", "Unregistered tool: $name")
        }
        return removed
    }

    /**
     * Retrieves a tool by unique name or known canonical alias.
     */
    fun getTool(name: String): Tool? {
        toolsMap[name]?.let { return it }
        val alias = when (name) {
            "whatsapp_message" -> "whatsapp_tool"
            "whatsapp_tool" -> "whatsapp_message"
            "call_phone" -> "call_tool"
            "call_contact" -> "call_tool"
            "call_tool" -> "call_phone"
            "contact_lookup" -> "contact_tool"
            "youtube_search" -> "youtube_tool"
            "youtube_tool" -> "youtube_search"
            else -> null
        }
        return alias?.let { toolsMap[it] }
    }

    /**
     * Checks if a tool with given name is currently registered.
     */
    fun hasTool(name: String): Boolean = getTool(name) != null

    /**
     * Returns a snapshot of all registered tools.
     */
    fun getAllTools(): List<Tool> = toolsMap.values.toList()

    /**
     * Returns all tools that require specific permissions.
     */
    fun getToolsRequiringPermissions(): List<Tool> =
        toolsMap.values.filter { it.requiredPermissions.isNotEmpty() }

    /**
     * Returns all tools that require user confirmation prior to execution.
     */
    fun getToolsRequiringConfirmation(): List<Tool> =
        toolsMap.values.filter { it.requiresConfirmation }

    /**
     * Generates a schema descriptor map suitable for AI reasoning prompts.
     */
    fun getToolSchemasForPrompt(): List<Map<String, Any?>> {
        return toolsMap.values.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.parameters.map { param ->
                    mapOf(
                        "name" to param.name,
                        "type" to param.type.name,
                        "description" to param.description,
                        "required" to param.required
                    )
                },
                "requiresConfirmation" to tool.requiresConfirmation
            )
        }
    }
}
