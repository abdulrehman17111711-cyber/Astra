package com.astra.ai.agent

import java.util.UUID

/**
 * Represents a structured tool invocation requested by the AI Agent planner.
 */
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val tool: String,
    val arguments: Map<String, Any?> = emptyMap(),
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val rationale: String? = null
)
