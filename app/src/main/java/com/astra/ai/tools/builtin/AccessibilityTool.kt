package com.astra.ai.tools.builtin

import android.content.Context
import com.astra.ai.accessibility.AccessibilityState
import com.astra.ai.apps.AppManager
import com.astra.ai.automation.AndroidAutomationEngine
import com.astra.ai.automation.AutomationAction
import com.astra.ai.automation.AutomationStepResult
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool empowering Astra Voice Assistant's agent to inspect visible UI nodes and automate
 * application interactions via Android public APIs, intents, and AccessibilityService.
 */
class AccessibilityTool(
    private val context: Context,
    private val appManager: AppManager,
    private val automationEngine: AndroidAutomationEngine = AndroidAutomationEngine(context, appManager)
) : Tool {

    override val name: String = "accessibility_action"

    override val description: String =
        "Inspects visible UI elements and automates taps, typing, scrolling, multi-step navigation, and in-app interactions."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "action",
            type = ToolParamType.STRING,
            description = "Action: click, tap, type, search_and_enter, scroll_forward, scroll_backward, go_back, inspect, open_app, multi_step",
            required = true
        ),
        ToolParameter(
            name = "target",
            type = ToolParamType.STRING,
            description = "Semantic label or element description to target (e.g. search, settings, profile, video)",
            required = false
        ),
        ToolParameter(
            name = "text",
            type = ToolParamType.STRING,
            description = "Text to enter or search query",
            required = false
        ),
        ToolParameter(
            name = "app_name",
            type = ToolParamType.STRING,
            description = "Target application to launch before interacting (e.g. YouTube, Instagram, Spotify, Settings)",
            required = false
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val action = (params["action"] as? String)?.lowercase()?.trim() ?: "inspect"
        val target = params["target"] as? String ?: ""
        val text = params["text"] as? String ?: ""
        val appName = params["app_name"] as? String

        AstraLogger.i("AccessibilityTool", "Executing accessibility action: $action, target: $target, app: $appName")

        // 1. Check if Accessibility Service is enabled in Android settings (unless action is just launching app)
        val isServiceEnabled = AccessibilityState.isAccessibilityServiceEnabled(context) ||
                AccessibilityState.state.value.isServiceConnected

        if (!isServiceEnabled && action !in listOf("open_app", "launch")) {
            return ToolExecutionResult(
                success = false,
                displayText = "Astra Accessibility Service is not enabled. Please enable it in Android Settings > Accessibility > Astra Voice Assistant Accessibility to allow UI automation.",
                error = "Accessibility service disabled in settings"
            )
        }

        // 2. Optionally launch requested app first
        if (!appName.isNullOrBlank() && action !in listOf("open_app", "launch")) {
            val launchResult = automationEngine.openApp(appName)
            if (!launchResult.isSuccess) {
                val failureMsg = (launchResult as? AutomationStepResult.Failed)?.reason
                    ?: "Failed launching $appName"
                return ToolExecutionResult(
                    success = false,
                    displayText = failureMsg,
                    error = failureMsg
                )
            }
        }

        // 3. Dispatch action through AndroidAutomationEngine
        return when (action) {
            "open_app", "launch" -> {
                val targetApp = appName ?: target
                if (targetApp.isBlank()) {
                    return ToolExecutionResult.failure("Please specify an app name to open.")
                }
                val result = automationEngine.openApp(targetApp)
                if (result.isSuccess) {
                    val msg = (result as AutomationStepResult.Success).message
                    ToolExecutionResult.success(displayText = msg)
                } else {
                    val err = (result as AutomationStepResult.Failed).reason
                    ToolExecutionResult.failure(errorMessage = err)
                }
            }

            "search_and_enter", "search" -> {
                val searchQuery = text.ifBlank { target }
                if (searchQuery.isBlank()) {
                    return ToolExecutionResult.failure("Please specify a search query.")
                }

                val actions = mutableListOf<AutomationAction>()
                actions.add(AutomationAction.TapElement("search", optional = true))
                actions.add(AutomationAction.EnterText(searchQuery, submit = true))

                val report = automationEngine.executePlan("Search for \"$searchQuery\"", actions)
                if (report.isSuccess) {
                    ToolExecutionResult.success(
                        displayText = report.summary,
                        output = mapOf("query" to searchQuery, "app" to (appName ?: "active_app"))
                    )
                } else {
                    ToolExecutionResult.failure(errorMessage = report.summary)
                }
            }

            "click", "tap" -> {
                if (target.isBlank()) {
                    return ToolExecutionResult.failure("Please specify what control or button to click.")
                }
                val stepResult = automationEngine.tapElement(target)
                if (stepResult.isSuccess) {
                    val msg = (stepResult as AutomationStepResult.Success).message
                    ToolExecutionResult.success(displayText = msg)
                } else {
                    val err = (stepResult as AutomationStepResult.Failed).reason
                    ToolExecutionResult.failure(errorMessage = err)
                }
            }

            "type", "enter_text" -> {
                if (text.isBlank()) {
                    return ToolExecutionResult.failure("Please specify the text to enter.")
                }
                val stepResult = automationEngine.enterText(text, target.takeIf { it.isNotBlank() })
                if (stepResult.isSuccess) {
                    val msg = (stepResult as AutomationStepResult.Success).message
                    ToolExecutionResult.success(displayText = msg)
                } else {
                    val err = (stepResult as AutomationStepResult.Failed).reason
                    ToolExecutionResult.failure(errorMessage = err)
                }
            }

            "scroll_forward", "scroll_down", "scroll" -> {
                val stepResult = automationEngine.scroll(forward = true)
                if (stepResult.isSuccess) {
                    ToolExecutionResult.success(displayText = (stepResult as AutomationStepResult.Success).message)
                } else {
                    ToolExecutionResult.failure(errorMessage = (stepResult as AutomationStepResult.Failed).reason)
                }
            }

            "scroll_backward", "scroll_up" -> {
                val stepResult = automationEngine.scroll(forward = false)
                if (stepResult.isSuccess) {
                    ToolExecutionResult.success(displayText = (stepResult as AutomationStepResult.Success).message)
                } else {
                    ToolExecutionResult.failure(errorMessage = (stepResult as AutomationStepResult.Failed).reason)
                }
            }

            "go_back", "back" -> {
                val stepResult = automationEngine.navigateBack(1)
                if (stepResult.isSuccess) {
                    ToolExecutionResult.success(displayText = (stepResult as AutomationStepResult.Success).message)
                } else {
                    ToolExecutionResult.failure(errorMessage = (stepResult as AutomationStepResult.Failed).reason)
                }
            }

            "inspect", "read_screen" -> {
                val observation = automationEngine.inspectScreen()
                val summary = observation.summary()
                val interactive = observation.findInteractiveNodes().map { it.displayLabel }
                ToolExecutionResult.success(
                    displayText = "Current screen: $summary",
                    output = mapOf(
                        "package" to (observation.packageName ?: "unknown"),
                        "controls" to interactive
                    )
                )
            }

            "multi_step" -> {
                val actions = mutableListOf<AutomationAction>()
                if (!appName.isNullOrBlank()) {
                    actions.add(AutomationAction.LaunchApp(appName))
                }
                if (target.isNotBlank()) {
                    actions.add(AutomationAction.TapElement(target))
                }
                if (text.isNotBlank()) {
                    actions.add(AutomationAction.EnterText(text, submit = true))
                }

                val report = automationEngine.executePlan("Execute multi-step automation", actions)
                if (report.isSuccess) {
                    ToolExecutionResult.success(displayText = report.summary)
                } else {
                    ToolExecutionResult.failure(errorMessage = report.summary)
                }
            }

            else -> {
                ToolExecutionResult.failure(errorMessage = "Unsupported accessibility action: $action")
            }
        }
    }
}
