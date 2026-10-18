package com.astra.ai.ai

/**
 * Core interface for AI reasoning and tool selection.
 */
interface AiEngine {
    /**
     * Reasons about user input query and chooses appropriate action or response.
     */
    suspend fun reason(query: String, context: AiContext): AiReasoningResult
}
