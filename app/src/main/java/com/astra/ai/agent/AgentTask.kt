package com.astra.ai.agent

import java.util.UUID

enum class AgentTaskState {
    PENDING,
    PLANNING,
    WAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    FAILED
}

data class AgentStep(
    val stepIndex: Int,
    val toolCall: ToolCall? = null,
    val toolResult: ToolResult? = null,
    val responseText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val state: AgentTaskState = AgentTaskState.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val steps: List<AgentStep> = emptyList(),
    val finalResponse: String? = null
)
