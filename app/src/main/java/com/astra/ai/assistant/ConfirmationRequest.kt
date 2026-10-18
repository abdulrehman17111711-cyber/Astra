package com.astra.ai.assistant

/**
 * Request emitted when a tool requires explicit user confirmation prior to execution.
 */
data class ConfirmationRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val toolName: String,
    val actionTitle: String,
    val description: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val riskLevel: RiskLevel = RiskLevel.MEDIUM
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
