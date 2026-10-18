package com.astra.ai.ai

import com.astra.ai.memory.HistoryEntity
import com.astra.ai.memory.MemoryEntity

/**
 * Contextual information provided to the AI Engine for reasoning.
 */
data class AiContext(
    val recentHistory: List<HistoryEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val availableToolNames: List<String> = emptyList(),
    val systemPromptAddition: String? = null
)
