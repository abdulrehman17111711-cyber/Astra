package com.astra.ai.core

/**
 * Global constants and metadata for Astra AI.
 * Created by Abdul Rehman.
 */
object AstraConstants {
    const val APP_NAME = "Astra Voice Assistant"
    const val BRAND_NAME = "Astra"
    const val CREATOR_NAME = "Abdul Rehman"
    const val APP_VERSION = "1.0.0-foundation"
    const val DEFAULT_LOCALE = "en-US"

    // Assistant timeouts
    const val VOICE_SILENCE_TIMEOUT_MS = 3500L
    const val TOOL_EXECUTION_TIMEOUT_MS = 15000L
    const val NETWORK_TIMEOUT_MS = 10000L

    // Tags
    const val TAG_ASSISTANT = "AstraAssistant"
    const val TAG_TOOLS = "AstraTools"
    const val TAG_VOICE = "AstraVoice"
    const val TAG_MEMORY = "AstraMemory"
    const val TAG_SECURITY = "AstraSecurity"

    // Play Store & Privacy Policy Configuration
    // Set to your hosted production privacy policy URL prior to store submission
    const val PRIVACY_POLICY_URL = "TODO_CONFIGURE_PRIVACY_POLICY_URL"
    const val PRODUCTION_BACKEND_HTTPS_URL = "https://api.astra.ai"
    const val LOCAL_DEV_BACKEND_HTTP_URL = "http://10.0.2.2:8000"
}
