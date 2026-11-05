package com.astra.ai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Robust, lifecycle-safe Speech-to-Text manager using Android SpeechRecognizer.
 * Handles runtime permissions, partial streaming results, RMSdB volume metering,
 * error recovery, timeouts, and clean restarts.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _speechResults = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val speechResults: SharedFlow<String> = _speechResults.asSharedFlow()

    private val _partialResults = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    private val _recognitionErrors = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val recognitionErrors: SharedFlow<String> = _recognitionErrors.asSharedFlow()

    private var currentSessionId: Long = 0L
    private var isListeningActive = false
    private var silenceTimeoutRunnable: Runnable? = null
    private var speechMaxTimeoutRunnable: Runnable? = null
    private var sessionWatchdogRunnable: Runnable? = null
    private var processingTimeoutRunnable: Runnable? = null
    private var lastPartialTranscript: String = ""

    private val SILENCE_TIMEOUT_MS = 4000L
    private val SPEECH_MAX_DURATION_MS = 8000L
    private val SESSION_WATCHDOG_MS = 7500L
    private val PROCESSING_TIMEOUT_MS = 2500L

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts active speech recognition with partial result streaming.
     */
    fun startListening(languageLocale: Locale = Locale.getDefault()) {
        mainHandler.post {
            cancelAllTimeouts()
            lastPartialTranscript = ""

            if (!hasRecordAudioPermission()) {
                _voiceState.value = VoiceState.ERROR
                scope.launch { _recognitionErrors.emit("Microphone permission (RECORD_AUDIO) not granted.") }
                return@post
            }

            if (!isAvailable()) {
                _voiceState.value = VoiceState.ERROR
                scope.launch { _recognitionErrors.emit("Android SpeechRecognizer is unavailable on this device.") }
                return@post
            }

            try {
                currentSessionId++
                val sessionId = currentSessionId

                // Ensure fresh clean recognizer instance
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }
                recognizer?.setRecognitionListener(createRecognitionListener(sessionId))

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageLocale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    // Additional speech timeout hints
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                }

                isListeningActive = true
                _voiceState.value = VoiceState.LISTENING
                _audioLevel.value = 0.08f
                recognizer?.startListening(intent)

                // Schedule safety silence timeout and global watchdog
                scheduleSilenceTimeout()
                scheduleSessionWatchdog()

                AstraLogger.i("SpeechRecognizerManager", "Started speech recognition (Language: ${languageLocale.displayName})")
            } catch (e: Exception) {
                _voiceState.value = VoiceState.ERROR
                isListeningActive = false
                AstraLogger.e("SpeechRecognizerManager", "Failed to start listening", e)
                scope.launch { _recognitionErrors.emit("Could not start speech recognition: ${e.message}") }
            }
        }
    }

    /**
     * Stops listening and waits for final recognition result.
     */
    fun stopListening() {
        mainHandler.post {
            cancelSilenceTimeout()
            cancelSpeechMaxTimeout()
            if (!isListeningActive) return@post
            try {
                recognizer?.stopListening()
                _voiceState.value = VoiceState.PROCESSING
                _audioLevel.value = 0f
                scheduleProcessingTimeout()
                AstraLogger.d("SpeechRecognizerManager", "Stopped listening, waiting for results")
            } catch (e: Exception) {
                AstraLogger.e("SpeechRecognizerManager", "Error stopping listening", e)
                fallbackToPartialOrIdle()
            }
        }
    }

    /**
     * Cancels active recognition without emitting results.
     */
    fun cancel() {
        mainHandler.post {
            cancelAllTimeouts()
            isListeningActive = false
            lastPartialTranscript = ""
            try {
                recognizer?.cancel()
                _voiceState.value = VoiceState.IDLE
                _audioLevel.value = 0f
                AstraLogger.d("SpeechRecognizerManager", "Cancelled speech recognition")
            } catch (e: Exception) {
                AstraLogger.e("SpeechRecognizerManager", "Error cancelling speech recognition", e)
            }
        }
    }

    /**
     * Releases recognizer resources.
     */
    fun release() {
        mainHandler.post {
            cancelAllTimeouts()
            isListeningActive = false
            lastPartialTranscript = ""
            try {
                recognizer?.destroy()
                recognizer = null
                _voiceState.value = VoiceState.IDLE
                _audioLevel.value = 0f
                AstraLogger.i("SpeechRecognizerManager", "Released SpeechRecognizer resources")
            } catch (e: Exception) {
                AstraLogger.e("SpeechRecognizerManager", "Error destroying SpeechRecognizer", e)
            }
        }
    }

    private fun scheduleSilenceTimeout() {
        cancelSilenceTimeout()
        silenceTimeoutRunnable = Runnable {
            if (isListeningActive && _voiceState.value == VoiceState.LISTENING) {
                AstraLogger.w("SpeechRecognizerManager", "Silence timeout reached, stopping recognition")
                stopListening()
            }
        }
        mainHandler.postDelayed(silenceTimeoutRunnable!!, SILENCE_TIMEOUT_MS)
    }

    private fun cancelSilenceTimeout() {
        silenceTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        silenceTimeoutRunnable = null
    }

    private fun scheduleSpeechMaxTimeout() {
        cancelSpeechMaxTimeout()
        speechMaxTimeoutRunnable = Runnable {
            if (isListeningActive) {
                AstraLogger.w("SpeechRecognizerManager", "Max speech duration reached, stopping recognition")
                stopListening()
            }
        }
        mainHandler.postDelayed(speechMaxTimeoutRunnable!!, SPEECH_MAX_DURATION_MS)
    }

    private fun cancelSpeechMaxTimeout() {
        speechMaxTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        speechMaxTimeoutRunnable = null
    }

    private fun scheduleSessionWatchdog() {
        cancelSessionWatchdog()
        sessionWatchdogRunnable = Runnable {
            if (isListeningActive || _voiceState.value != VoiceState.IDLE) {
                AstraLogger.w("SpeechRecognizerManager", "Session watchdog expired, resetting recognizer")
                fallbackToPartialOrIdle()
            }
        }
        mainHandler.postDelayed(sessionWatchdogRunnable!!, SESSION_WATCHDOG_MS)
    }

    private fun cancelSessionWatchdog() {
        sessionWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        sessionWatchdogRunnable = null
    }

    private fun scheduleProcessingTimeout() {
        cancelProcessingTimeout()
        processingTimeoutRunnable = Runnable {
            if (_voiceState.value == VoiceState.PROCESSING) {
                AstraLogger.w("SpeechRecognizerManager", "Processing timeout reached")
                fallbackToPartialOrIdle()
            }
        }
        mainHandler.postDelayed(processingTimeoutRunnable!!, PROCESSING_TIMEOUT_MS)
    }

    private fun cancelProcessingTimeout() {
        processingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        processingTimeoutRunnable = null
    }

    private fun cancelAllTimeouts() {
        cancelSilenceTimeout()
        cancelSpeechMaxTimeout()
        cancelSessionWatchdog()
        cancelProcessingTimeout()
    }

    private fun fallbackToPartialOrIdle() {
        cancelAllTimeouts()
        isListeningActive = false
        _audioLevel.value = 0f
        _voiceState.value = VoiceState.IDLE

        val partial = lastPartialTranscript.trim()
        lastPartialTranscript = ""
        if (partial.isNotBlank()) {
            AstraLogger.i("SpeechRecognizerManager", "Fallback: emitting last partial transcript: \"$partial\"")
            scope.launch { _speechResults.emit(partial) }
        } else {
            AstraLogger.d("SpeechRecognizerManager", "Fallback: no transcript, resetting to IDLE")
            scope.launch { _recognitionErrors.emit("No speech detected.") }
        }
    }

    fun onAppLifecycleChanged(isForeground: Boolean) {
        if (!isForeground) {
            AstraLogger.i("SpeechRecognizerManager", "App backgrounded or screen locked, safely cancelling active recognition")
            cancel()
        }
    }

    private fun createRecognitionListener(sessionId: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (sessionId != currentSessionId) return
            _voiceState.value = VoiceState.LISTENING
            AstraLogger.d("SpeechRecognizerManager", "Ready for speech (session: $sessionId)")
        }

        override fun onBeginningOfSpeech() {
            if (sessionId != currentSessionId) return
            _voiceState.value = VoiceState.LISTENING
            cancelSilenceTimeout() // Speech begun, cancel initial silence timeout
            scheduleSpeechMaxTimeout() // Start max speech duration timer
            AstraLogger.d("SpeechRecognizerManager", "User started speaking (session: $sessionId)")
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (sessionId != currentSessionId) return
            // Normalize -2dB..10dB into 0.0f..1.0f range
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _audioLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (sessionId != currentSessionId) return
            isListeningActive = false
            _voiceState.value = VoiceState.PROCESSING
            _audioLevel.value = 0f
            cancelSpeechMaxTimeout()
            scheduleProcessingTimeout()
            AstraLogger.d("SpeechRecognizerManager", "End of user speech (session: $sessionId)")
        }

        override fun onError(error: Int) {
            if (sessionId != currentSessionId) return
            cancelAllTimeouts()
            isListeningActive = false
            _voiceState.value = VoiceState.IDLE
            _audioLevel.value = 0f

            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording failure."
                SpeechRecognizer.ERROR_CLIENT -> "Client recognition error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                SpeechRecognizer.ERROR_NETWORK -> "Network issue during speech recognition."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition engine busy. Resetting..."
                SpeechRecognizer.ERROR_SERVER -> "Server error during recognition."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                else -> "Recognition issue (code $error)"
            }

            AstraLogger.w("SpeechRecognizerManager", "Recognition error: $errorMessage (code $error, session: $sessionId)")

            // If we already captured a good partial transcript before the error, execute it!
            val partial = lastPartialTranscript.trim()
            lastPartialTranscript = ""
            if (partial.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT)) {
                AstraLogger.i("SpeechRecognizerManager", "Salvaged speech from partial results despite error code $error: \"$partial\"")
                scope.launch { _speechResults.emit(partial) }
            } else {
                scope.launch { _recognitionErrors.emit(errorMessage) }
            }
        }

        override fun onResults(results: Bundle?) {
            if (sessionId != currentSessionId) return
            cancelAllTimeouts()
            isListeningActive = false
            _voiceState.value = VoiceState.IDLE
            _audioLevel.value = 0f

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val correctedBest = VoicePhoneticCorrector.selectBestTranscript(matches, context.contentResolver)
            val textToEmit = if (correctedBest.isNotBlank()) correctedBest else VoicePhoneticCorrector.applyPhoneticRules(lastPartialTranscript.trim())
            lastPartialTranscript = ""

            if (textToEmit.isNotBlank()) {
                AstraLogger.i("SpeechRecognizerManager", "Final Speech Recognized: \"$textToEmit\" (session: $sessionId)")
                scope.launch { _speechResults.emit(textToEmit) }
            } else {
                AstraLogger.d("SpeechRecognizerManager", "Recognition finished with empty text (session: $sessionId)")
                scope.launch { _recognitionErrors.emit("No speech detected.") }
            }
        }

        override fun onPartialResults(partialResultsBundle: Bundle?) {
            if (sessionId != currentSessionId) return
            val partials = partialResultsBundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = partials?.firstOrNull()?.trim().orEmpty()
            if (partial.isNotBlank()) {
                lastPartialTranscript = partial
                AstraLogger.d("SpeechRecognizerManager", "Partial: \"$partial\" (session: $sessionId)")
                scope.launch { _partialResults.emit(partial) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
