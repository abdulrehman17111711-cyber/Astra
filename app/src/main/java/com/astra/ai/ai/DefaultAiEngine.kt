package com.astra.ai.ai

import com.astra.ai.apps.AppManager
import com.astra.ai.backend.AstraBackendClient
import com.astra.ai.backend.BackendPlanResponse
import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.ToolArgumentValidator
import com.astra.ai.tools.ToolRegistry
import com.astra.ai.tools.ValidationResult

/**
 * Intelligent extensible semantic reasoning engine for Astra Voice Assistant.
 * Handles natural conversational English, Roman Urdu, Hindi, and mixed-language instructions.
 * Connects to the backend Gemini model for cloud planning when available, and seamlessly
 * falls back to a generalized on-device semantic engine when offline.
 *
 * Implements structured AI tool calling with validated arguments and connects to the
 * action executor.
 */
class DefaultAiEngine(
    private val toolRegistry: ToolRegistry,
    private val appManager: AppManager? = null,
    private val backendClient: AstraBackendClient? = null,
    private val geminiAiService: GeminiAiService = GeminiAiService(),
    private val settingsRepository: com.astra.ai.settings.SettingsRepository? = null
) : AiEngine {

    private val universalIntentEngine = UniversalSemanticIntentEngine(toolRegistry, appManager)

    override suspend fun reason(query: String, context: AiContext): AiReasoningResult {
        val trimmed = query.trim()
        AstraLogger.i("DefaultAiEngine", "Reasoning for query: \"$trimmed\"")

        // 1. Attempt Gemini Backend Plan if client is available
        if (backendClient != null) {
            val backendResponse = backendClient.requestPlan(trimmed, context)
            when (backendResponse) {
                is BackendPlanResponse.ToolCallAction -> {
                    val toolName = backendResponse.toolCall.tool
                    val tool = toolRegistry.getTool(toolName)
                    if (tool != null) {
                        val validation = ToolArgumentValidator.validate(tool, backendResponse.toolCall.arguments)
                        return when (validation) {
                            is ValidationResult.Valid -> {
                                AstraLogger.i("DefaultAiEngine", "Backend selected tool '$toolName' with validated args: ${validation.sanitizedParams}")
                                AiReasoningResult.ToolCall(
                                    toolName = tool.name,
                                    parameters = validation.sanitizedParams,
                                    rationale = backendResponse.toolCall.rationale
                                )
                            }
                            is ValidationResult.Invalid -> {
                                AstraLogger.w("DefaultAiEngine", "Argument validation failed for $toolName: ${validation.reason}")
                                AiReasoningResult.DirectResponse(
                                    "I need more details: ${validation.reason}"
                                )
                            }
                        }
                    }
                }

                is BackendPlanResponse.RequiresConfirmation -> {
                    val toolName = backendResponse.toolCall.tool
                    val tool = toolRegistry.getTool(toolName)
                    if (tool != null) {
                        val validation = ToolArgumentValidator.validate(tool, backendResponse.toolCall.arguments)
                        val sanitized = if (validation is ValidationResult.Valid) validation.sanitizedParams else backendResponse.toolCall.arguments
                        return AiReasoningResult.RequiresConfirmation(
                            toolName = tool.name,
                            actionTitle = backendResponse.prompt,
                            description = backendResponse.toolCall.rationale ?: "Astra Voice Assistant requires your confirmation to execute.",
                            parameters = sanitized
                        )
                    }
                }

                is BackendPlanResponse.ClarificationNeeded -> {
                    return AiReasoningResult.DirectResponse(backendResponse.question)
                }

                is BackendPlanResponse.DirectResponse -> {
                    return AiReasoningResult.DirectResponse(backendResponse.message)
                }

                is BackendPlanResponse.Error -> {
                    AstraLogger.d("DefaultAiEngine", "Backend unavailable or returned error (${backendResponse.message}), falling back to local universal intent engine.")
                }
            }
        }

        // 2. Local Generalized Universal Semantic Intent Engine (Offline & Resilient)
        val structuredIntent = universalIntentEngine.parseIntent(trimmed)
        AstraLogger.i(
            "DefaultAiEngine",
            "Local structured intent: action=${structuredIntent.action}, targetApp=${structuredIntent.targetApp}, goal=\"${structuredIntent.goal}\""
        )

        // Check if clarification is genuinely necessary
        if (structuredIntent.needsClarification && !structuredIntent.clarificationQuestion.isNullOrBlank()) {
            return AiReasoningResult.DirectResponse(structuredIntent.clarificationQuestion)
        }

        // Handle conversational queries without tools (Gemini Q&A integration)
        if (structuredIntent.action == StructuredIntent.ACTION_CONVERSATION || structuredIntent.mappedTool == null) {
            val lower = trimmed.lowercase()
            val responseText = when {
                lower.contains("who made you") || lower.contains("creator") ||
                        lower.contains("developer") || lower.contains("kisne banaya") ->
                    "I am Astra Voice Assistant, engineered by ${AstraConstants.CREATOR_NAME} as a production-grade, voice-first Android assistant."

                lower in listOf("hello", "hi", "hey", "astra", "hey astra", "salam", "assalam o alaikum") ->
                    "Hello! Astra Voice Assistant is listening. What would you like me to do?"

                else -> {
                    val userKey = settingsRepository?.settings?.value?.geminiApiKey
                    geminiAiService.generateAnswer(trimmed, userKey)
                }
            }
            return AiReasoningResult.DirectResponse(responseText)
        }

        // Execute mapped registered tool
        val toolName = structuredIntent.mappedTool
        val tool = toolRegistry.getTool(toolName)
        if (tool == null) {
            val fallbackMsg = when (structuredIntent.action) {
                StructuredIntent.ACTION_LAUNCH -> "I understood you want to open ${structuredIntent.targetApp}, but '$toolName' is not available."
                StructuredIntent.ACTION_CALL -> "I understood you want to call ${structuredIntent.parameters["contactName"]} via ${structuredIntent.parameters["sim"] ?: "SIM 1"}, but '$toolName' is not available."
                StructuredIntent.ACTION_MESSAGE -> "I understood you want to message ${structuredIntent.parameters["contactName"]}, but '$toolName' is not available."
                StructuredIntent.ACTION_SEARCH -> "I understood you want to search ${structuredIntent.targetApp} for \"${structuredIntent.parameters["query"]}\", but '$toolName' is not available."
                else -> "I understood you want to ${structuredIntent.goal}, but '$toolName' is not available."
            }
            return AiReasoningResult.DirectResponse(fallbackMsg)
        }

        // Validate arguments with ToolArgumentValidator
        return when (val validation = ToolArgumentValidator.validate(tool, structuredIntent.mappedArguments)) {
            is ValidationResult.Valid -> {
                if (structuredIntent.requiresConfirmation || tool.requiresConfirmation) {
                    AiReasoningResult.RequiresConfirmation(
                        toolName = tool.name,
                        actionTitle = structuredIntent.goal,
                        description = structuredIntent.confirmationPrompt
                            ?: "Astra Voice Assistant requires your confirmation to execute.",
                        parameters = validation.sanitizedParams
                    )
                } else {
                    AiReasoningResult.ToolCall(
                        toolName = tool.name,
                        parameters = validation.sanitizedParams,
                        rationale = structuredIntent.rationale
                    )
                }
            }

            is ValidationResult.Invalid -> {
                AstraLogger.w("DefaultAiEngine", "Argument validation failed for ${tool.name}: ${validation.reason}")
                AiReasoningResult.DirectResponse(
                    "Please provide a valid ${validation.paramName} to proceed."
                )
            }
        }
    }
}
