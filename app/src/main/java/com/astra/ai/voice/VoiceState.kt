package com.astra.ai.voice

/**
 * State representing audio/voice subsystem lifecycle.
 */
enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}
