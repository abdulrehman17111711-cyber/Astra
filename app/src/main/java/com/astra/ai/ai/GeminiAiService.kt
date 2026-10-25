package com.astra.ai.ai

import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct Gemini API Service for conversational queries, general knowledge,
 * science, programming explanations, and intelligent voice responses.
 *
 * Implements the official Gemini REST API (gemini-3.5-flash) per the gemini-api skill.
 * Formats responses specifically for natural Text-To-Speech synthesis (concise, spoken-friendly,
 * bilingual English & Urdu/Roman Urdu, no markdown asterisks or code symbols).
 */
class GeminiAiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GeminiAiService"
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Resolves the active Gemini API key from user settings, environment secrets, or BuildConfig.
     */
    fun resolveApiKey(userKey: String?): String {
        if (!userKey.isNullOrBlank()) return userKey.trim()

        return try {
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey.isNotBlank() && !buildConfigKey.startsWith("MY_GEMINI")) {
                buildConfigKey
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Queries Gemini 3.5 Flash for conversational and general knowledge answers.
     */
    suspend fun generateAnswer(
        query: String,
        userApiKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val apiKey = resolveApiKey(userApiKey)

        if (apiKey.isBlank()) {
            AstraLogger.w(TAG, "No valid Gemini API key found. Using smart offline knowledge fallback.")
            return@withContext provideOfflineKnowledgeFallback(trimmed)
        }

        try {
            val systemPrompt = "You are Astra Voice Assistant, an intelligent, voice-first Android AI companion created by Abdul Rehman. " +
                    "Your answer will be read directly by Android Text-To-Speech (TTS). Keep answers crisp, accurate, friendly, and concise (2 to 4 sentences max). " +
                    "If the user asks in Urdu or Roman Urdu, reply in natural Urdu or Roman Urdu. " +
                    "Crucial: Never use markdown symbols, asterisks, hashtags, bullets, or code blocks, as TTS will speak them literally."

            val requestBodyJson = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", trimmed) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 250)
                })
            }

            val request = Request.Builder()
                .url("$BASE_ENDPOINT?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyString.isNotBlank()) {
                    val root = JSONObject(bodyString)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            val cleanText = sanitizeForTts(text)
                            if (cleanText.isNotBlank()) {
                                return@withContext cleanText
                            }
                        }
                    }
                } else {
                    AstraLogger.w(TAG, "Gemini API HTTP ${response.code}: $bodyString")
                }
            }
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Error invoking Gemini API: ${e.message}")
        }

        // Seamless fallback if API network call fails
        provideOfflineKnowledgeFallback(trimmed)
    }

    /**
     * Strips markdown symbols, asterisks, bullet points, and code formatting so Text-To-Speech sounds completely natural.
     */
    private fun sanitizeForTts(raw: String): String {
        return raw
            .replace(Regex("[*#`_~>]"), "")
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
            .replace(Regex("\\n+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Comprehensive built-in knowledge responses for instant, zero-latency replies
     * when offline or before a Gemini API key is configured.
     */
    private fun provideOfflineKnowledgeFallback(query: String): String {
        val lower = query.lowercase().trim()

        return when {
            lower.contains("who made you") || lower.contains("creator") || lower.contains("developer") || lower.contains("kisne banaya") ->
                "I am Astra Voice Assistant, engineered by ${AstraConstants.CREATOR_NAME} as a production-grade, voice-first Android assistant."

            lower.contains("what is python") || lower.contains("python kya hai") || lower.contains("python kia hai") ->
                "Python is a versatile, high-level programming language famous for its clean, readable syntax. It is widely used in artificial intelligence, web development, data science, and automation."

            lower.contains("what is ai") || lower.contains("artificial intelligence") || lower.contains("ai kya hai") ->
                "Artificial Intelligence refers to computer systems capable of performing tasks that typically require human intelligence, such as natural speech recognition, visual perception, and logical decision making."

            lower.contains("who is abdul rehman") || lower.contains("abdul rehman") ->
                "Abdul Rehman is the visionary software engineer and creator behind the Astra Voice Assistant platform."

            lower.contains("joke") || lower.contains("latifa") ->
                "Why do programmers prefer dark mode? Because light attracts bugs!"

            lower.contains("weather") || lower.contains("mausam") ->
                "To get live hyper-local weather reports, you can ask me to open Weather or search your local forecast."

            lower in listOf("hello", "hi", "hey", "astra", "hey astra", "salam", "assalam o alaikum") ->
                "Hello! Astra Voice Assistant is listening. How can I help you today?"

            else ->
                "I am Astra Voice Assistant. I can assist with device actions, phone calls, WhatsApp messages, alarms, and search. To unlock unlimited general knowledge answers, please configure your Gemini API key in Settings."
        }
    }
}
