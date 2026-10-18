package com.astra.ai.ai

import com.astra.ai.core.AstraConstants
import com.astra.ai.tools.ToolRegistry

/**
 * Builds structured prompts for Astra AI reasoning.
 */
class AiPromptBuilder(private val toolRegistry: ToolRegistry) {

    fun buildSystemPrompt(): String {
        return buildString {
            appendLine("You are Astra Voice Assistant, an intelligent, voice-first Android assistant created by ${AstraConstants.CREATOR_NAME}.")
            appendLine("You specialize in executing Android tools, providing crisp spoken answers, and managing memory.")
            appendLine("Always be concise, proactive, respectful, and accurate.")
            appendLine("Registered tools: ${toolRegistry.getAllTools().map { it.name }.joinToString()}")
        }
    }
}
