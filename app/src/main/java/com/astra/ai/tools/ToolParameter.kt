package com.astra.ai.tools

/**
 * Supported data types for tool parameters.
 */
enum class ToolParamType {
    STRING,
    INT,
    FLOAT,
    BOOLEAN,
    OBJECT,
    LIST
}

/**
 * Definition of a parameter accepted by an Astra AI Tool.
 */
data class ToolParameter(
    val name: String,
    val type: ToolParamType,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null
)
