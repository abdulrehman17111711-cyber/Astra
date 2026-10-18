package com.astra.ai.backend

import com.astra.ai.agent.ToolCall
import com.astra.ai.agent.ToolResult
import com.astra.ai.ai.AiContext
import com.astra.ai.ai.StructuredIntent
import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface BackendPlanResponse {
    data class ToolCallAction(
        val toolCall: ToolCall,
        val structuredIntent: StructuredIntent? = null
    ) : BackendPlanResponse

    data class DirectResponse(
        val message: String,
        val structuredIntent: StructuredIntent? = null
    ) : BackendPlanResponse

    data class RequiresConfirmation(
        val toolCall: ToolCall,
        val prompt: String,
        val structuredIntent: StructuredIntent? = null
    ) : BackendPlanResponse

    data class ClarificationNeeded(
        val question: String,
        val structuredIntent: StructuredIntent? = null
    ) : BackendPlanResponse

    data class Error(val message: String) : BackendPlanResponse
}

/**
 * Robust HTTP client that communicates with the Astra Voice Assistant backend (FastAPI / Gemini API).
 * Keeps API keys and secrets entirely off the mobile device.
 * Sends tools, parameters, recent history, and persistent memory context.
 */
class AstraBackendClient(
    private val toolRegistry: ToolRegistry,
    initialBaseUrl: String = AstraConstants.LOCAL_DEV_BACKEND_HTTP_URL,
    val isProduction: Boolean = false
) {
    var baseUrl: String = initialBaseUrl
        private set

    init {
        configureEndpoint(initialBaseUrl, requireHttps = isProduction)
    }

    fun configureEndpoint(url: String, requireHttps: Boolean = isProduction) {
        val trimmed = url.trim().removeSuffix("/")
        if (requireHttps && !trimmed.startsWith("https://", ignoreCase = true)) {
            AstraLogger.w("AstraBackendClient", "Production backend requires HTTPS: $trimmed. Enforcing HTTPS.")
            this.baseUrl = trimmed.replace("http://", "https://")
        } else {
            this.baseUrl = trimmed
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            AstraLogger.d("AstraBackendClient", "Health check failed: ${e.message}")
            false
        }
    }

    suspend fun requestPlan(query: String, context: AiContext? = null): BackendPlanResponse = withContext(Dispatchers.IO) {
        try {
            val rootJson = JSONObject().apply {
                put("query", query)

                // Available tools schema
                val toolsArray = JSONArray()
                toolRegistry.getAllTools().forEach { tool ->
                    val toolObj = JSONObject().apply {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("requires_confirmation", tool.requiresConfirmation)

                        val paramsArray = JSONArray()
                        tool.parameters.forEach { p ->
                            paramsArray.put(JSONObject().apply {
                                put("name", p.name)
                                put("type", p.type.name.lowercase())
                                put("description", p.description)
                                put("required", p.required)
                            })
                        }
                        put("parameters", paramsArray)
                    }
                    toolsArray.put(toolObj)
                }
                put("available_tools", toolsArray)

                // Context: History & Memory
                if (context != null) {
                    if (context.recentHistory.isNotEmpty()) {
                        val historyArray = JSONArray()
                        context.recentHistory.forEach { h ->
                            historyArray.put(JSONObject().apply {
                                put("query", h.query)
                                put("response", h.response)
                                put("toolUsed", h.toolName.orEmpty())
                            })
                        }
                        put("history", historyArray)
                    }

                    if (context.memories.isNotEmpty()) {
                        val memoryObj = JSONObject()
                        context.memories.forEach { m ->
                            memoryObj.put(m.key, m.value)
                        }
                        put("context", memoryObj)
                    }
                }
            }

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/agent/plan")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext BackendPlanResponse.Error("Backend error code: ${response.code}")
                }

                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val type = json.optString("type", "direct_response")

                // Parse optional structured intent
                val intentJson = json.optJSONObject("structured_intent")
                val structuredIntent = if (intentJson != null) {
                    val rawParams = intentJson.optJSONObject("parameters") ?: JSONObject()
                    val paramsMap = mutableMapOf<String, Any?>()
                    rawParams.keys().forEach { k -> paramsMap[k] = rawParams.get(k) }

                    val stepsArray = intentJson.optJSONArray("execution_steps")
                    val stepsList = mutableListOf<String>()
                    if (stepsArray != null) {
                        for (i in 0 until stepsArray.length()) {
                            stepsList.add(stepsArray.getString(i))
                        }
                    }

                    StructuredIntent(
                        action = intentJson.optString("action", "CONVERSATION"),
                        targetApp = intentJson.optString("target_app").takeIf { it.isNotBlank() },
                        goal = intentJson.optString("goal", query),
                        parameters = paramsMap,
                        executionSteps = stepsList,
                        needsClarification = intentJson.optBoolean("needs_clarification", false),
                        clarificationQuestion = intentJson.optString("clarification_question").takeIf { it.isNotBlank() },
                        verificationStrategy = intentJson.optString("verification_strategy").takeIf { it.isNotBlank() }
                    )
                } else null

                when (type) {
                    "tool_call" -> {
                        val toolName = json.getString("tool")
                        val argsObj = json.optJSONObject("arguments") ?: JSONObject()
                        val argsMap = mutableMapOf<String, Any?>()
                        argsObj.keys().forEach { k -> argsMap[k] = argsObj.get(k) }

                        val toolCall = ToolCall(
                            tool = toolName,
                            arguments = argsMap,
                            requiresConfirmation = false,
                            rationale = json.optString("rationale")
                        )
                        BackendPlanResponse.ToolCallAction(toolCall, structuredIntent)
                    }

                    "confirmation_required" -> {
                        val toolName = json.getString("tool")
                        val argsObj = json.optJSONObject("arguments") ?: JSONObject()
                        val argsMap = mutableMapOf<String, Any?>()
                        argsObj.keys().forEach { k -> argsMap[k] = argsObj.get(k) }
                        val prompt = json.optString("prompt", "Confirm action for $toolName?")

                        val toolCall = ToolCall(
                            tool = toolName,
                            arguments = argsMap,
                            requiresConfirmation = true,
                            confirmationPrompt = prompt,
                            rationale = json.optString("rationale")
                        )
                        BackendPlanResponse.RequiresConfirmation(toolCall, prompt, structuredIntent)
                    }

                    "clarification_needed" -> {
                        val question = json.optString("message", "Could you please clarify your request?")
                        BackendPlanResponse.ClarificationNeeded(question, structuredIntent)
                    }

                    else -> {
                        val msg = json.optString("message", "Done.")
                        BackendPlanResponse.DirectResponse(msg, structuredIntent)
                    }
                }
            }
        } catch (e: Exception) {
            AstraLogger.d("AstraBackendClient", "Plan request network error (using local engine): ${e.message}")
            BackendPlanResponse.Error("Network failure connecting to Astra backend: ${e.message}")
        }
    }

    suspend fun sendResult(query: String, toolCall: ToolCall, result: ToolResult): String? = withContext(Dispatchers.IO) {
        try {
            val rootJson = JSONObject().apply {
                put("query", query)
                put("tool", toolCall.tool)

                val argsObj = JSONObject()
                toolCall.arguments.forEach { (k, v) -> argsObj.put(k, v ?: "") }
                put("arguments", argsObj)

                put("success", result.success)
                put("result", result.outputText)
                if (result.error != null) {
                    put("error", result.error)
                }
            }

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/agent/result")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    json.optString("message", result.outputText)
                } else {
                    result.outputText
                }
            }
        } catch (e: Exception) {
            AstraLogger.d("AstraBackendClient", "Result evaluation error: ${e.message}")
            null
        }
    }
}
