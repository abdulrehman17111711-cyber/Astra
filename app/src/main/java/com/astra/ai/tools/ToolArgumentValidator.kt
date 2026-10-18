package com.astra.ai.tools

import com.astra.ai.core.AstraLogger

sealed interface ValidationResult {
    data class Valid(val sanitizedParams: Map<String, Any?>) : ValidationResult
    data class Invalid(val paramName: String, val reason: String) : ValidationResult
}

/**
 * Reusable parameter validator for Astra Voice Assistant tools.
 * Performs type-safe coercion, bounds-checking, requirement enforcement,
 * and domain-specific argument sanitization prior to tool execution.
 */
object ToolArgumentValidator {

    fun validate(tool: Tool, rawParams: Map<String, Any?>): ValidationResult {
        val sanitized = mutableMapOf<String, Any?>()

        for (param in tool.parameters) {
            val rawValue = rawParams[param.name] ?: rawParams[param.name.lowercase()]

            // 1. Requirement check
            if (rawValue == null || (rawValue is String && rawValue.isBlank())) {
                if (param.required) {
                    if (param.defaultValue != null) {
                        sanitized[param.name] = param.defaultValue
                    } else {
                        return ValidationResult.Invalid(
                            paramName = param.name,
                            reason = "Parameter '${param.name}' is required by ${tool.name} but was missing or empty."
                        )
                    }
                } else {
                    sanitized[param.name] = param.defaultValue
                    continue
                }
            } else {
                // 2. Type coercion & normalization
                val coerced = coerceValue(rawValue, param.type)
                if (coerced == null && param.required) {
                    return ValidationResult.Invalid(
                        paramName = param.name,
                        reason = "Parameter '${param.name}' expected type ${param.type.name} but received '$rawValue'."
                    )
                }

                // 3. Domain-specific validation & sanitization
                val domainError = checkDomainConstraints(tool.name, param.name, coerced)
                if (domainError != null) {
                    return ValidationResult.Invalid(param.name, domainError)
                }

                sanitized[param.name] = coerced
            }
        }

        // Include any additional pass-through parameters that might not be in tool schema
        rawParams.forEach { (k, v) ->
            if (!sanitized.containsKey(k)) {
                sanitized[k] = v
            }
        }

        AstraLogger.d("ToolArgumentValidator", "Validated arguments for ${tool.name}: $sanitized")
        return ValidationResult.Valid(sanitized)
    }

    private fun coerceValue(value: Any?, type: ToolParamType): Any? {
        if (value == null) return null
        return when (type) {
            ToolParamType.STRING -> value.toString().trim()
            ToolParamType.INT -> {
                when (value) {
                    is Number -> value.toInt()
                    is String -> value.trim().toIntOrNull()
                        ?: value.trim().toDoubleOrNull()?.toInt()
                    else -> null
                }
            }
            ToolParamType.FLOAT -> {
                when (value) {
                    is Number -> value.toFloat()
                    is String -> value.trim().toFloatOrNull()
                    else -> null
                }
            }
            ToolParamType.BOOLEAN -> {
                when (value) {
                    is Boolean -> value
                    is String -> value.trim().equals("true", ignoreCase = true)
                    is Number -> value.toInt() != 0
                    else -> null
                }
            }
            ToolParamType.OBJECT -> value
            ToolParamType.LIST -> {
                when (value) {
                    is List<*> -> value
                    is Array<*> -> value.toList()
                    is String -> listOf(value)
                    else -> listOf(value)
                }
            }
        }
    }

    private fun checkDomainConstraints(toolName: String, paramName: String, value: Any?): String? {
        when (toolName) {
            "alarm_tool" -> {
                if (paramName == "hour" && value is Int) {
                    if (value !in 0..23) {
                        return "Alarm hour must be between 0 and 23. Received: $value"
                    }
                }
                if (paramName == "minute" && value is Int) {
                    if (value !in 0..59) {
                        return "Alarm minute must be between 0 and 59. Received: $value"
                    }
                }
            }
            "call_phone", "send_sms" -> {
                if (paramName == "phoneNumber" && value is String) {
                    val digits = value.filter { it.isDigit() }
                    if (digits.length < 3) {
                        return "Phone number '$value' is invalid (must have at least 3 digits)."
                    }
                }
            }
            "youtube_search", "web_search", "search_app" -> {
                if (paramName == "query" && value is String) {
                    if (value.isBlank()) {
                        return "Search query cannot be empty."
                    }
                }
            }
        }
        return null
    }
}
