package com.astra.ai.assistant

import com.astra.ai.tools.ToolExecutionResult

/**
 * High-level state categories required by Astra AI specification.
 */
enum class AssistantStateType {
    IDLE,
    ACTIVATING,
    LISTENING,
    PROCESSING,
    THINKING,
    EXECUTING,
    SPEAKING,
    WAITING_FOR_CONFIRMATION,
    SUCCESS,
    ERROR
}

/**
 * Rich model representing Astra Voice Assistant's current operational state.
 */
sealed class AssistantState(val type: AssistantStateType) {

    data class Idle(
        val message: String = "Astra Voice Assistant ready. Tap the core or speak."
    ) : AssistantState(AssistantStateType.IDLE)

    data class Activating(
        val message: String = "Astra is listening..."
    ) : AssistantState(AssistantStateType.ACTIVATING)

    data class Listening(
        val partialText: String = "",
        val audioLevel: Float = 0f
    ) : AssistantState(AssistantStateType.LISTENING)

    data class Processing(
        val query: String,
        val summary: String = "Processing request..."
    ) : AssistantState(AssistantStateType.PROCESSING)

    data class Thinking(
        val query: String,
        val thoughtSummary: String = "Reasoning about intent and tools..."
    ) : AssistantState(AssistantStateType.THINKING)

    data class Executing(
        val toolName: String,
        val query: String,
        val progressMessage: String = "Executing tool: $toolName"
    ) : AssistantState(AssistantStateType.EXECUTING)

    data class Speaking(
        val text: String
    ) : AssistantState(AssistantStateType.SPEAKING)

    data class WaitingForConfirmation(
        val request: ConfirmationRequest
    ) : AssistantState(AssistantStateType.WAITING_FOR_CONFIRMATION)

    data class Success(
        val summary: String,
        val toolResult: ToolExecutionResult? = null
    ) : AssistantState(AssistantStateType.SUCCESS)

    data class Error(
        val errorMessage: String,
        val recoverySuggestion: String = "Please try again or rephrase your request."
    ) : AssistantState(AssistantStateType.ERROR)
}
