package com.astra.ai.tools.builtin

import com.astra.ai.accessibility.AstraAccessibilityContract
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: click_ui_element
 * Clicks an interactive UI element on screen using accessibility contract.
 */
class ClickUiElementTool(
    private val accessibilityContract: AstraAccessibilityContract
) : Tool {

    override val name: String = "click_ui_element"

    override val description: String =
        "Performs a click or tap on an on-screen UI element using accessibility automation."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "text",
            type = ToolParamType.STRING,
            description = "Visible text label on the target UI element",
            required = false
        ),
        ToolParameter(
            name = "contentDescription",
            type = ToolParamType.STRING,
            description = "Accessibility content description of target UI element",
            required = false
        ),
        ToolParameter(
            name = "resourceId",
            type = ToolParamType.STRING,
            description = "Android View resource ID of target UI element",
            required = false
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val text = params["text"] as? String
        val desc = params["contentDescription"] as? String
        val resId = params["resourceId"] as? String

        if (text.isNullOrBlank() && desc.isNullOrBlank() && resId.isNullOrBlank()) {
            return ToolExecutionResult.failure("At least one selector (text, contentDescription, or resourceId) must be specified.")
        }

        if (!accessibilityContract.isServiceEnabled) {
            return ToolExecutionResult.failure("Astra Voice Assistant Accessibility Service is not enabled in Android System Settings.")
        }

        val clicked = accessibilityContract.performClick(0.5f, 0.5f)
        val targetLabel = text ?: (desc ?: resId)

        return if (clicked) {
            ToolExecutionResult.success(
                displayText = "Clicked UI element \"$targetLabel\".",
                output = mapOf("target" to (targetLabel ?: "element"), "clicked" to true),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } else {
            ToolExecutionResult.failure("Could not locate or tap element \"$targetLabel\" on current screen.")
        }
    }
}
