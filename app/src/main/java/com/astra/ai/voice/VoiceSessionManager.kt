package com.astra.ai.voice

import com.astra.ai.assistant.AssistantController
import com.astra.ai.assistant.AssistantState
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates seamless end-to-end voice conversations without forcing the user
 * to press a talk button repeatedly.
 */
class VoiceSessionManager(
    private val speechRecognizerManager: SpeechRecognizerManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val wakeActivationManager: WakeActivationManager,
    private val assistantController: AssistantController,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private var sessionJob: Job? = null

    init {
        assistantController.onResponseSpoken = {
            if (_isSessionActive.value && wakeActivationManager.config.value.isContinuousConversationEnabled) {
                scope.launch {
                    delay(400)
                    if (_isSessionActive.value && assistantController.state.value !is AssistantState.Error) {
                        AstraLogger.d("VoiceSessionManager", "Continuous conversation: triggering follow-up listening")
                        startVoiceSession()
                    }
                }
            } else {
                _isSessionActive.value = false
            }
        }
    }

    /**
     * Activates Astra Voice Assistant session with full state pipeline.
     */
    fun startVoiceSession() {
        if (!wakeActivationManager.isMicrophoneGranted()) {
            AstraLogger.w("VoiceSessionManager", "Microphone permission not granted")
            return
        }

        textToSpeechManager.stop()
        _isSessionActive.value = true
        assistantController.activateVoiceSession()
        AstraLogger.i("VoiceSessionManager", "Voice session started via activateVoiceSession")
    }

    /**
     * Stops listening and processes audio captured so far.
     */
    fun stopVoiceSession() {
        assistantController.stopListening()
    }

    /**
     * Cancels the voice session and resets to IDLE.
     */
    fun cancelVoiceSession() {
        _isSessionActive.value = false
        assistantController.resetToIdle()
        AstraLogger.i("VoiceSessionManager", "Voice session cancelled")
    }

    fun release() {
        sessionJob?.cancel()
        speechRecognizerManager.release()
        textToSpeechManager.release()
    }
}
