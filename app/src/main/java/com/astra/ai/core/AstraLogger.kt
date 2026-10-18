package com.astra.ai.core

import android.util.Log

/**
 * Structured logger for Astra AI operations.
 * Safe for both Android runtime and host JVM unit tests.
 */
object AstraLogger {
    private const val PREFIX = "AstraAI:"

    // Regex patterns for credentials, tokens, and secrets
    private val SENSITIVE_PATTERNS = listOf(
        Regex("AIza[0-9A-Za-z-_]{20,}"), // Google / Gemini API Keys
        Regex("Bearer\\s+[A-Za-z0-9-_.]+", RegexOption.IGNORE_CASE),
        Regex("(?i)(password|secret|apikey|token|auth|credential)\\s*[:=]\\s*['\"]?([^\\s,'\"]+)"),
        Regex("(?i)\"api_key\"\\s*:\\s*\"[^\"]+\"")
    )

    fun sanitize(message: String): String {
        var clean = message
        for (pattern in SENSITIVE_PATTERNS) {
            clean = pattern.replace(clean) { matchResult ->
                val text = matchResult.value
                if (text.contains("=")) {
                    val parts = text.split("=", limit = 2)
                    "${parts[0]}=***REDACTED***"
                } else if (text.contains(":")) {
                    val parts = text.split(":", limit = 2)
                    "${parts[0]}:***REDACTED***"
                } else {
                    "***REDACTED_CREDENTIAL***"
                }
            }
        }
        return clean
    }

    fun d(tag: String, message: String) {
        val safeMessage = sanitize(message)
        try {
            Log.d("$PREFIX$tag", safeMessage)
        } catch (_: Throwable) {
            println("DEBUG: [$PREFIX$tag] $safeMessage")
        }
    }

    fun i(tag: String, message: String) {
        val safeMessage = sanitize(message)
        try {
            Log.i("$PREFIX$tag", safeMessage)
        } catch (_: Throwable) {
            println("INFO: [$PREFIX$tag] $safeMessage")
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = sanitize(message)
        try {
            Log.w("$PREFIX$tag", safeMessage, throwable)
        } catch (_: Throwable) {
            println("WARN: [$PREFIX$tag] $safeMessage")
            throwable?.printStackTrace()
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = sanitize(message)
        try {
            Log.e("$PREFIX$tag", safeMessage, throwable)
        } catch (_: Throwable) {
            System.err.println("ERROR: [$PREFIX$tag] $safeMessage")
            throwable?.printStackTrace()
        }
    }
}
