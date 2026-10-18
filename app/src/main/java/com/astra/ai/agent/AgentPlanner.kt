package com.astra.ai.agent

import com.astra.ai.backend.AstraBackendClient
import com.astra.ai.backend.BackendPlanResponse
import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.ToolRegistry

sealed interface PlanDecision {
    data class Execute(val toolCall: ToolCall) : PlanDecision
    data class Confirm(val toolCall: ToolCall, val prompt: String) : PlanDecision
    data class Answer(val message: String) : PlanDecision
    data class Failure(val error: String) : PlanDecision
}

/**
 * Intelligent Agent Planner for Astra AI.
 * Queries the FastAPI Gemini backend for dynamic tool planning and response synthesis,
 * with a resilient local semantic engine fallback for zero offline disruptions.
 */
class AgentPlanner(
    private val toolRegistry: ToolRegistry,
    private val backendClient: AstraBackendClient
) {

    suspend fun plan(query: String): PlanDecision {
        val trimmed = query.trim()
        AstraLogger.i("AgentPlanner", "Planning agent action for query: \"$trimmed\"")

        // 1. Attempt backend dynamic plan (FastAPI -> Gemini API)
        val backendResp = backendClient.requestPlan(trimmed)
        when (backendResp) {
            is BackendPlanResponse.ToolCallAction -> {
                AstraLogger.i("AgentPlanner", "Backend Gemini selected tool: ${backendResp.toolCall.tool}")
                return PlanDecision.Execute(backendResp.toolCall)
            }
            is BackendPlanResponse.RequiresConfirmation -> {
                AstraLogger.i("AgentPlanner", "Backend Gemini requires confirmation for: ${backendResp.toolCall.tool}")
                return PlanDecision.Confirm(backendResp.toolCall, backendResp.prompt)
            }
            is BackendPlanResponse.ClarificationNeeded -> {
                AstraLogger.i("AgentPlanner", "Backend Gemini requires clarification")
                return PlanDecision.Answer(backendResp.question)
            }
            is BackendPlanResponse.DirectResponse -> {
                AstraLogger.i("AgentPlanner", "Backend Gemini responded directly")
                return PlanDecision.Answer(backendResp.message)
            }
            is BackendPlanResponse.Error -> {
                AstraLogger.d("AgentPlanner", "Backend unreachable or returned error, using resilient local planner: ${backendResp.message}")
            }
        }

        // 2. Resilient Local Semantic Agent Planner (Bilingual, natural language understanding)
        return planLocally(trimmed)
    }

    suspend fun evaluateResult(query: String, toolCall: ToolCall, result: ToolResult): String {
        // Try backend result synthesis first
        val backendText = backendClient.sendResult(query, toolCall, result)
        if (!backendText.isNullOrBlank()) {
            return backendText
        }

        // Fallback local response
        return if (result.success) {
            result.outputText
        } else {
            "I couldn't complete ${toolCall.tool}: ${result.error ?: "Unknown issue"}"
        }
    }

    private fun planLocally(query: String): PlanDecision {
        val lower = query.lowercase()

        // Attribution
        if (lower.contains("who made you") || lower.contains("who created you") ||
            lower.contains("creator") || lower.contains("developer") || lower.contains("kisne banaya")
        ) {
            return PlanDecision.Answer(
                "I am Astra Voice Assistant, engineered by ${AstraConstants.CREATOR_NAME} as a production-grade, voice-first Android assistant."
            )
        }

        // Greetings
        if (lower in listOf("hello", "hi", "hey", "astra", "hey astra", "salam", "assalam o alaikum")) {
            return PlanDecision.Answer("Hello! Astra Voice Assistant is listening. How can I help with your device or apps?")
        }

        // 1. Telephony: call_contact (Requires Confirmation!)
        // e.g. "Ammi ko call karo", "SIM 1 se Ammi ko call karna", "Ammi ko SIM 1 se call kar do"
        if (lower.contains("call") || lower.contains("phone milao") || lower.contains("dial")) {
            var contact = query
            val sim = if (lower.contains("sim 1")) "SIM 1" else if (lower.contains("sim 2")) "SIM 2" else "Default SIM"

            val removeKeywords = listOf(
                "sim 1 se", "sim 2 se", "sim 1", "sim 2", "se", "ko call kar do",
                "ko call karo", "ko call karna", "call kar do", "call karo", "call karna",
                "phone milao", "call", "dial", "please", "karo", "karna"
            )
            for (kw in removeKeywords) {
                contact = contact.replace(Regex("(?i)\\b$kw\\b"), "").replace(Regex("(?i)$kw"), "")
            }
            val cleanContact = contact.trim().ifEmpty { "contact" }

            val call = ToolCall(
                tool = "call_contact",
                arguments = mapOf("contactName" to cleanContact, "simPreference" to sim),
                requiresConfirmation = true,
                confirmationPrompt = "$cleanContact ko call karun?",
                rationale = "User requested phone call with confirmation barrier"
            )
            return PlanDecision.Confirm(call, "$cleanContact ko call karun?")
        }

        // 2. Messaging: send_message (Requires Confirmation!)
        if (lower.contains("send message") || lower.contains("message bhejo") || lower.contains("text bhejo")) {
            val call = ToolCall(
                tool = "send_message",
                arguments = mapOf("application" to "WhatsApp", "recipient" to "contact", "message" to query),
                requiresConfirmation = true,
                confirmationPrompt = "Message send karun?",
                rationale = "Sending external message requires confirmation"
            )
            return PlanDecision.Confirm(call, "Message send karun?")
        }

        // 3. Search App: search_app (e.g. "YouTube kholo aur Android news search karo")
        if (lower.contains("youtube") && (lower.contains("search") || lower.contains("aur") || lower.contains("dhoondo"))) {
            val searchPart = com.astra.ai.tools.builtin.YouTubeSearchParser.extractQuery(query)

            val call = ToolCall(
                tool = "search_app",
                arguments = mapOf("application" to "YouTube", "query" to searchPart.ifEmpty { "Android news" }),
                rationale = "User requested search within YouTube"
            )
            return PlanDecision.Execute(call)
        }

        // 4. Open App: open_app (e.g. "WhatsApp kholo", "WhatsApp open kar do", "WhatsApp chalao", "Camera kholo")
        if (lower.contains("kholo") || lower.contains("open") || lower.contains("chalao") || lower.contains("launch") || lower.contains("start")) {
            val pkg = when {
                lower.contains("whatsapp") -> "com.whatsapp"
                lower.contains("youtube") -> "com.google.android.youtube"
                lower.contains("instagram") -> "com.instagram.android"
                lower.contains("settings") -> "com.android.settings"
                else -> ""
            }

            val appName = when {
                lower.contains("whatsapp") -> "WhatsApp"
                lower.contains("youtube") -> "YouTube"
                lower.contains("instagram") -> "Instagram"
                lower.contains("camera") -> "Camera"
                lower.contains("settings") -> "Settings"
                else -> query
                    .replace(Regex("(?i)^(open|launch|start|kholo|chalao)\\s+"), "")
                    .replace(Regex("(?i)\\s+(kholo|open kar do|open karo|chalao|launch|start|app|application)$"), "")
                    .trim()
            }

            val call = ToolCall(
                tool = "open_app",
                arguments = if (pkg.isNotBlank()) mapOf("packageName" to pkg, "appName" to appName)
                            else mapOf("appName" to appName),
                rationale = "App launch intent"
            )
            return PlanDecision.Execute(call)
        }

        // 5. Memory / Note: quick_note (e.g. "Remember that my name is Abdul Rehman", "Yaad rakho...")
        if (lower.startsWith("remember") || lower.startsWith("yaad rakho") || lower.contains("save note")) {
            val call = ToolCall(
                tool = "quick_note",
                arguments = mapOf("key" to "user_memory", "value" to query, "category" to "personal"),
                rationale = "Memory persistence"
            )
            return PlanDecision.Execute(call)
        }

        // 6. Device Status: device_status
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("specs") || lower.contains("device info")) {
            return PlanDecision.Execute(
                ToolCall(tool = "device_status", arguments = emptyMap(), rationale = "Device telemetry inspect")
            )
        }

        // 7. World Clock: world_clock
        if (lower.contains("time") || lower.contains("clock") || lower.contains("waqt") || lower.contains("date")) {
            return PlanDecision.Execute(
                ToolCall(tool = "world_clock", arguments = mapOf("timezone" to "LOCAL"), rationale = "Clock inspect")
            )
        }

        return PlanDecision.Answer("I received: \"$query\". I can open apps, place calls, search, or check device status.")
    }
}
