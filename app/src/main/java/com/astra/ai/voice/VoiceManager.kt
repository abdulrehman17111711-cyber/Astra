package com.astra.ai.voice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface coordinating speech recognition / voice capture.
 */
interface VoiceManager {
    val voiceState: StateFlow<VoiceState>
    val audioLevel: StateFlow<Float> // Normalized 0.0f .. 1.0f for visualizer
    val speechResults: SharedFlow<String>
    val partialResults: SharedFlow<String>
    val errorEvents: SharedFlow<String>

    fun startListening()
    fun stopListening()
    fun cancel()
    fun isAvailable(): Boolean
    fun release()
}
