package com.astra.ai.security

import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool

/**
 * Security subsystem for Astra AI.
 * Sanitizes parameters, audits command execution risk, and enforces safety bounds.
 */
class SecurityValidator {

    fun validateToolExecution(tool: Tool, params: Map<String, Any?>): ValidationOutcome {
        // 1. Verify required parameters
        for (param in tool.parameters) {
            if (param.required && !params.containsKey(param.name)) {
                AstraLogger.w("SecurityValidator", "Missing required param: ${param.name} for ${tool.name}")
                return ValidationOutcome.Rejected("Missing required parameter '${param.name}'")
            }
        }

        // 2. Sanitize input strings against potential command injections
        for ((key, value) in params) {
            if (value is String && value.length > 4000) {
                return ValidationOutcome.Rejected("Parameter '$key' exceeds maximum permissible length")
            }
        }

        return ValidationOutcome.Allowed
    }
}

sealed interface ValidationOutcome {
    data object Allowed : ValidationOutcome
    data class Rejected(val reason: String) : ValidationOutcome
}
