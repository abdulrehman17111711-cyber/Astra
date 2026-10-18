package com.astra.ai.automation

import com.astra.ai.ai.StructuredIntent

/**
 * Plans multi-step automation task sequences for AndroidAutomationEngine.
 * Translates conversational user goals into atomic, verifiable automation actions.
 */
object AutomationPlanGenerator {

    fun planFromIntent(intent: StructuredIntent): List<AutomationAction> {
        val actions = mutableListOf<AutomationAction>()
        val targetApp = intent.targetApp

        when (intent.action) {
            StructuredIntent.ACTION_SEARCH -> {
                val query = intent.parameters["query"]?.toString().orEmpty()
                if (!targetApp.isNullOrBlank()) {
                    actions.add(AutomationAction.LaunchApp(targetApp))
                }
                if (query.isNotBlank()) {
                    actions.add(AutomationAction.TapElement("search", optional = true))
                    actions.add(AutomationAction.EnterText(text = query, submit = true))
                }
            }

            StructuredIntent.ACTION_LAUNCH -> {
                if (!targetApp.isNullOrBlank()) {
                    actions.add(AutomationAction.LaunchApp(targetApp))
                }
            }

            StructuredIntent.ACTION_MESSAGE -> {
                val recipient = (intent.parameters["recipient"] ?: intent.parameters["contactName"])?.toString().orEmpty()
                val message = intent.parameters["message"]?.toString().orEmpty()

                if (!targetApp.isNullOrBlank()) {
                    actions.add(AutomationAction.LaunchApp(targetApp))
                }
                if (recipient.isNotBlank()) {
                    actions.add(AutomationAction.TapElement("search", optional = true))
                    actions.add(AutomationAction.EnterText(text = recipient, submit = true))
                    actions.add(AutomationAction.TapElement(recipient, optional = true))
                }
                if (message.isNotBlank()) {
                    actions.add(AutomationAction.EnterText(text = message, submit = true))
                }
            }

            else -> {
                if (!targetApp.isNullOrBlank()) {
                    actions.add(AutomationAction.LaunchApp(targetApp))
                }
            }
        }

        return actions
    }

    fun planFromQuery(query: String): List<AutomationAction> {
        val lower = query.lowercase().trim()
        val actions = mutableListOf<AutomationAction>()

        when {
            lower.contains("scroll down") || lower.contains("scroll forward") || lower.contains("neeche scroll") -> {
                actions.add(AutomationAction.Scroll(forward = true))
            }

            lower.contains("scroll up") || lower.contains("scroll backward") || lower.contains("upar scroll") -> {
                actions.add(AutomationAction.Scroll(forward = false))
            }

            lower == "go back" || lower == "back" || lower == "piche jao" || lower == "wapas" -> {
                actions.add(AutomationAction.NavigateBack(1))
            }

            lower.startsWith("tap ") || lower.startsWith("click ") -> {
                val target = query.replace(Regex("(?i)^(tap|click)\\s+"), "").trim()
                if (target.isNotBlank()) {
                    actions.add(AutomationAction.TapElement(target))
                }
            }

            lower.startsWith("type ") || lower.startsWith("enter ") -> {
                val text = query.replace(Regex("(?i)^(type|enter)\\s+"), "").trim()
                if (text.isNotBlank()) {
                    actions.add(AutomationAction.EnterText(text, submit = true))
                }
            }
        }

        return actions
    }
}
