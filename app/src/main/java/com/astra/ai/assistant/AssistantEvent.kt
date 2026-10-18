package com.astra.ai.assistant

/**
 * Events sent to or emitted by AssistantController.
 */
sealed interface AssistantEvent {
    data object StartVoiceListening : AssistantEvent
    data object StopVoiceListening : AssistantEvent
    data class SubmitTextQuery(val query: String) : AssistantEvent
    data object ConfirmPendingAction : AssistantEvent
    data object RejectPendingAction : AssistantEvent
    data object CancelCurrentOperation : AssistantEvent
    data object ResetToIdle : AssistantEvent
    data class ExecuteToolDirectly(val toolName: String, val params: Map<String, Any?> = emptyMap()) : AssistantEvent

    // Lifecycle/notification events emitted to UI
    data class StateChanged(val newState: AssistantState) : AssistantEvent
    data class ToolExecuted(val toolName: String, val success: Boolean) : AssistantEvent
    data class MessageSpoken(val message: String) : AssistantEvent
}
