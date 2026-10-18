package com.astra.ai.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface coordinating Text-To-Speech output for Astra AI.
 */
interface TtsManager {
    val isSpeaking: StateFlow<Boolean>
    val isReady: StateFlow<Boolean>

    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun stop()
    fun setSpeechRate(rate: Float)
    fun setPitch(pitch: Float)
    fun release()
}
