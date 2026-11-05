package com.astra.ai.assistant

import com.astra.ai.ai.AiContext
import com.astra.ai.ai.AiEngine
import com.astra.ai.ai.AiReasoningResult
import com.astra.ai.core.AstraLogger
import com.astra.ai.core.DispatcherProvider
import com.astra.ai.memory.HistoryRepository
import com.astra.ai.memory.MemoryRepository
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolRegistry
import com.astra.ai.voice.SpeechRecognizerManager
import com.astra.ai.voice.TextToSpeechManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central orchestrator for Astra AI.
 * Coordinates Voice Input (SpeechRecognizerManager) -> AI Reasoning -> Tool Selection ->
 * Tool Execution -> Result Verification -> Response Generation -> Text-to-Speech (TextToSpeechManager).
 */
class AssistantController(
    val speechRecognizerManager: SpeechRecognizerManager,
    val textToSpeechManager: TextToSpeechManager,
    private val aiEngine: AiEngine,
    private val toolRegistry: ToolRegistry,
    private val resultVerifier: ResultVerifier,
    private val responseGenerator: ResponseGenerator,
    private val memoryRepository: MemoryRepository,
    private val historyRepository: HistoryRepository,
    private val dispatchers: DispatcherProvider,
    val confirmationManager: com.astra.ai.security.ConfirmationManager = com.astra.ai.security.ConfirmationManager(),
    val settingsRepository: com.astra.ai.settings.SettingsRepository? = null
) {
    private val scope = CoroutineScope(dispatchers.main + SupervisorJob())

    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AssistantEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<AssistantEvent> = _events.asSharedFlow()

    private var activeProcessingJob: Job? = null
    private var listeningWatchdogJob: Job? = null
    private var pendingAction: PendingAction? = null

    var onResponseSpoken: (() -> Unit)? = null

    init {
        observeVoiceInput()
    }

    private fun observeVoiceInput() {
        // Collect partial speech results for live UI feedback on Astra Core & Waveform
        scope.launch {
            speechRecognizerManager.partialResults.collect { partial ->
                val current = _state.value
                if (current is AssistantState.Listening) {
                    _state.value = AssistantState.Listening(
                        partialText = partial,
                        audioLevel = speechRecognizerManager.audioLevel.value
                    )
                }
            }
        }

        // Collect final recognized speech results and send to AI agent
        scope.launch {
            speechRecognizerManager.speechResults.collect { rawTranscript ->
                val clean = rawTranscript.trim()
                if (clean.isNotBlank()) {
                    AstraLogger.i("AssistantController", "Received recognized speech: \"$clean\"")
                    processQuery(clean)
                } else {
                    resetToIdle()
                }
            }
        }

        // Collect real-time audio RMS level updates
        scope.launch {
            speechRecognizerManager.audioLevel.collect { level ->
                val current = _state.value
                if (current is AssistantState.Listening) {
                    _state.value = current.copy(audioLevel = level)
                }
            }
        }

        // Observe recognition errors to exit Listening state cleanly
        scope.launch {
            speechRecognizerManager.recognitionErrors.collect { errorMsg ->
                val current = _state.value
                if (current is AssistantState.Listening || current is AssistantState.Activating) {
                    listeningWatchdogJob?.cancel()
                    AstraLogger.w("AssistantController", "Exiting Listening state due to recognition error: $errorMsg")
                    if (errorMsg.contains("No speech", ignoreCase = true) || errorMsg.contains("timed out", ignoreCase = true)) {
                        _state.value = AssistantState.Idle()
                    } else {
                        _state.value = AssistantState.Error(errorMessage = errorMsg, recoverySuggestion = "Tap the core to try again.")
                        scope.launch {
                            kotlinx.coroutines.delay(2500)
                            if (_state.value is AssistantState.Error) {
                                _state.value = AssistantState.Idle()
                            }
                        }
                    }
                }
            }
        }

        // Observe voiceState to reset state if recognizer returns to IDLE
        scope.launch {
            speechRecognizerManager.voiceState.collect { voiceState ->
                if (voiceState == com.astra.ai.voice.VoiceState.IDLE) {
                    val current = _state.value
                    if (current is AssistantState.Listening) {
                        listeningWatchdogJob?.cancel()
                        _state.value = AssistantState.Idle()
                    }
                }
            }
        }
    }

    /**
     * Toggles voice listening.
     */
    fun toggleListening() {
        if (_state.value is AssistantState.Listening || _state.value is AssistantState.Activating) {
            stopListening()
        } else {
            activateVoiceSession()
        }
    }

    /**
     * Activates Astra Voice Assistant with full lifecycle:
     * IDLE -> ACTIVATING -> LISTENING.
     * Prevents the orb from remaining indefinitely in Listening mode with a dedicated watchdog.
     */
    fun activateVoiceSession() {
        textToSpeechManager.stop()
        activeProcessingJob?.cancel()
        listeningWatchdogJob?.cancel()

        // 1. Activating state
        _state.value = AssistantState.Activating("Astra is listening...")
        emitEvent(AssistantEvent.StateChanged(_state.value))

        if (!speechRecognizerManager.hasRecordAudioPermission()) {
            _state.value = AssistantState.Error(
                errorMessage = "Microphone permission required.",
                recoverySuggestion = "Please enable microphone permission in settings."
            )
            emitEvent(AssistantEvent.StateChanged(_state.value))
            scope.launch {
                kotlinx.coroutines.delay(2500)
                if (_state.value is AssistantState.Error) {
                    resetToIdle()
                }
            }
            return
        }

        // 2. Listening state
        _state.value = AssistantState.Listening(partialText = "", audioLevel = 0.08f)
        speechRecognizerManager.startListening()
        emitEvent(AssistantEvent.StateChanged(_state.value))

        // 3. Controller-level watchdog to guarantee the orb NEVER remains stuck on Listening
        listeningWatchdogJob = scope.launch {
            kotlinx.coroutines.delay(7500)
            if (_state.value is AssistantState.Listening) {
                AstraLogger.w("AssistantController", "Listening watchdog expired after 7.5s, resetting to IDLE")
                resetToIdle()
            }
        }
    }

    /**
     * Starts listening for voice input.
     */
    fun startListening() {
        activateVoiceSession()
    }

    /**
     * Stops listening for voice input.
     */
    fun stopListening() {
        speechRecognizerManager.stopListening()
    }

    /**
     * Processes a user query (from voice recognition or keyboard input).
     */
    fun processQuery(query: String) {
        val normalized = com.astra.ai.voice.VoicePhoneticCorrector.applyPhoneticRules(query.trim())
        val trimmed = normalized.ifBlank { query.trim() }
        if (trimmed.isEmpty()) return

        listeningWatchdogJob?.cancel()
        speechRecognizerManager.cancel()
        activeProcessingJob?.cancel()

        activeProcessingJob = scope.launch {
            try {
                // 1. Processing state
                _state.value = AssistantState.Processing(query = trimmed)
                emitEvent(AssistantEvent.StateChanged(_state.value))

                // 2. Fetch context from memory & history
                val context = withContext(dispatchers.io) {
                    AiContext(
                        recentHistory = historyRepository.getRecentHistory(5),
                        memories = memoryRepository.getAllMemories(),
                        availableToolNames = toolRegistry.getAllTools().map { it.name }
                    )
                }

                // 3. AI Reasoning
                val reasoningResult = withContext(dispatchers.default) {
                    aiEngine.reason(trimmed, context)
                }

                when (reasoningResult) {
                    is AiReasoningResult.DirectResponse -> {
                        handleDirectResponse(trimmed, reasoningResult.text)
                    }

                    is AiReasoningResult.ToolCall -> {
                        handleToolCall(
                            query = trimmed,
                            toolName = reasoningResult.toolName,
                            params = reasoningResult.parameters
                        )
                    }

                    is AiReasoningResult.RequiresConfirmation -> {
                        val settings = settingsRepository?.settings?.value
                        val needsConfirmation = confirmationManager.isConfirmationRequired(
                            toolName = reasoningResult.toolName,
                            params = reasoningResult.parameters,
                            settings = settings
                        )
                        if (needsConfirmation) {
                            requestConfirmation(
                                query = trimmed,
                                toolName = reasoningResult.toolName,
                                actionTitle = reasoningResult.actionTitle,
                                description = reasoningResult.description,
                                parameters = reasoningResult.parameters
                            )
                        } else {
                            handleToolCall(
                                query = trimmed,
                                toolName = reasoningResult.toolName,
                                params = reasoningResult.parameters
                            )
                        }
                    }

                    is AiReasoningResult.Error -> {
                        handleError(trimmed, reasoningResult.message)
                    }
                }
            } catch (e: CancellationException) {
                AstraLogger.d("AssistantController", "Processing job was cancelled")
            } catch (e: Exception) {
                AstraLogger.e("AssistantController", "Error during query processing", e)
                handleError(trimmed, e.message ?: "Unknown processing error")
            }
        }
    }

    private suspend fun handleDirectResponse(query: String, answer: String) {
        withContext(dispatchers.io) {
            historyRepository.recordInteraction(
                query = query,
                response = answer,
                state = AssistantStateType.SUCCESS.name,
                toolName = null,
                success = true
            )
        }

        // Speaking state
        _state.value = AssistantState.Speaking(text = answer)
        emitEvent(AssistantEvent.StateChanged(_state.value))

        textToSpeechManager.speak(answer) {
            _state.value = AssistantState.Success(summary = answer)
            emitEvent(AssistantEvent.StateChanged(_state.value))
            onResponseSpoken?.invoke()
        }
    }

    private suspend fun handleToolCall(query: String, toolName: String, params: Map<String, Any?>) {
        val tool = toolRegistry.getTool(toolName)
        if (tool == null) {
            handleError(query, "Tool '$toolName' is not registered in Astra AI ToolRegistry.")
            return
        }

        // Check confirmation requirement via tool specification, ConsequentialAction classification, and Trusted Automation settings
        val settings = settingsRepository?.settings?.value
        val needsConfirmation = confirmationManager.isConfirmationRequired(toolName, params, settings)
        if (needsConfirmation) {
            val actionType = confirmationManager.classifyAction(toolName, params)
            val (title, prompt) = if (actionType != null) {
                confirmationManager.buildConfirmationPrompt(actionType, params)
            } else {
                Pair("Confirm ${tool.name}", tool.description)
            }

            requestConfirmation(
                query = query,
                toolName = toolName,
                actionTitle = title,
                description = prompt,
                parameters = params
            )
            return
        }

        executeTool(query, tool, params)
    }

    private suspend fun executeTool(query: String, tool: Tool, params: Map<String, Any?>) {
        // Executing state
        _state.value = AssistantState.Executing(
            toolName = tool.name,
            query = query,
            progressMessage = "Executing tool: ${tool.name}..."
        )
        emitEvent(AssistantEvent.StateChanged(_state.value))

        // Tool execution
        val result: ToolExecutionResult = withContext(dispatchers.io) {
            try {
                tool.execute(params)
            } catch (e: Exception) {
                ToolExecutionResult.failure("Execution failed: ${e.message}")
            }
        }

        // Result verification
        val verification = resultVerifier.verify(tool, result)
        if (verification is VerificationResult.Failed) {
            handleError(query, "Result verification failed: ${verification.reason}")
            return
        }

        // Response generation
        val finalResponse = responseGenerator.generateToolResponse(query, tool.name, result)

        withContext(dispatchers.io) {
            historyRepository.recordInteraction(
                query = query,
                response = finalResponse,
                state = if (result.success) AssistantStateType.SUCCESS.name else AssistantStateType.ERROR.name,
                toolName = tool.name,
                success = result.success
            )
        }

        emitEvent(AssistantEvent.ToolExecuted(tool.name, result.success))

        // Speaking state
        _state.value = AssistantState.Speaking(text = finalResponse)
        emitEvent(AssistantEvent.StateChanged(_state.value))

        textToSpeechManager.speak(finalResponse) {
            _state.value = AssistantState.Success(
                summary = finalResponse,
                toolResult = result
            )
            emitEvent(AssistantEvent.StateChanged(_state.value))
            onResponseSpoken?.invoke()
            scope.launch {
                kotlinx.coroutines.delay(1200)
                if (_state.value is AssistantState.Success) {
                    resetToIdle()
                }
            }
        }
    }

    private fun requestConfirmation(
        query: String,
        toolName: String,
        actionTitle: String,
        description: String,
        parameters: Map<String, Any?>
    ) {
        pendingAction = PendingAction(query, toolName, parameters)
        val request = ConfirmationRequest(
            toolName = toolName,
            actionTitle = actionTitle,
            description = description,
            parameters = parameters
        )
        _state.value = AssistantState.WaitingForConfirmation(request)
        emitEvent(AssistantEvent.StateChanged(_state.value))
    }

    /**
     * Confirms the pending tool action.
     */
    fun confirmPendingAction() {
        val action = pendingAction
        pendingAction = null
        if (action != null) {
            val settings = settingsRepository?.settings?.value
            if (settings?.rememberPermissionDecisions == true) {
                val actionKey = confirmationManager.buildActionTrustKey(action.toolName, action.params)
                settingsRepository.addTrustedAction(actionKey)
                AstraLogger.i("AssistantController", "Remembered trusted decision for: $actionKey")
            }

            val tool = toolRegistry.getTool(action.toolName)
            if (tool != null) {
                scope.launch {
                    executeTool(action.query, tool, action.params)
                }
            } else {
                handleError(action.query, "Tool '${action.toolName}' no longer available.")
            }
        } else {
            resetToIdle()
        }
    }

    /**
     * Rejects the pending action.
     */
    fun rejectPendingAction() {
        pendingAction = null
        val rejectionMessage = "Action was cancelled by user."
        _state.value = AssistantState.Success(summary = rejectionMessage)
        emitEvent(AssistantEvent.StateChanged(_state.value))
    }

    /**
     * Handles app lifecycle transitions (backgrounding, screen lock, returning to foreground).
     * Prevents the microphone from hanging open or wasting battery when minimized.
     */
    fun onAppLifecycleChanged(isForeground: Boolean) {
        if (!isForeground) {
            AstraLogger.i("AssistantController", "App backgrounded or screen locked: cancelling active session")
            listeningWatchdogJob?.cancel()
            speechRecognizerManager.onAppLifecycleChanged(false)
            textToSpeechManager.stop()
            if (_state.value is AssistantState.Listening || _state.value is AssistantState.Activating) {
                resetToIdle()
            }
        }
    }

    /**
     * Resets state back to IDLE.
     */
    fun resetToIdle() {
        listeningWatchdogJob?.cancel()
        speechRecognizerManager.cancel()
        textToSpeechManager.stop()
        activeProcessingJob?.cancel()
        pendingAction = null
        _state.value = AssistantState.Idle()
        emitEvent(AssistantEvent.StateChanged(_state.value))
    }

    /**
     * Sets a specific state for preview / testing.
     */
    fun setStateForPreview(state: AssistantState) {
        _state.value = state
        emitEvent(AssistantEvent.StateChanged(state))
    }

    private fun handleError(query: String, message: String) {
        AstraLogger.e("AssistantController", "Error: $message")
        _state.value = AssistantState.Error(errorMessage = message)
        emitEvent(AssistantEvent.StateChanged(_state.value))

        scope.launch(dispatchers.io) {
            historyRepository.recordInteraction(
                query = query,
                response = "Error: $message",
                state = AssistantStateType.ERROR.name,
                toolName = null,
                success = false
            )
        }
    }

    private fun emitEvent(event: AssistantEvent) {
        scope.launch { _events.emit(event) }
    }

    fun release() {
        activeProcessingJob?.cancel()
        speechRecognizerManager.release()
        textToSpeechManager.release()
    }

    private data class PendingAction(
        val query: String,
        val toolName: String,
        val params: Map<String, Any?>
    )
}
