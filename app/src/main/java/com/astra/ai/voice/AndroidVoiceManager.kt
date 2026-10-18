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
 * Production Android SpeechRecognizer implementation for Astra AI.
 */
class AndroidVoiceManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : VoiceManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    override val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _speechResults = MutableSharedFlow<String>(extraBufferCapacity = 5)
    override val speechResults: SharedFlow<String> = _speechResults.asSharedFlow()

    private val _partialResults = MutableSharedFlow<String>(extraBufferCapacity = 5)
    override val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening() {
        mainHandler.post {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                _voiceState.value = VoiceState.ERROR
                scope.launch { _errorEvents.emit("Audio recording permission not granted") }
                return@post
            }

            if (!isAvailable()) {
                _voiceState.value = VoiceState.ERROR
                scope.launch { _errorEvents.emit("Speech recognition service not available on this device") }
                return@post
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                _voiceState.value = VoiceState.LISTENING
                _audioLevel.value = 0.05f
                speechRecognizer?.startListening(intent)
                AstraLogger.i("VoiceManager", "Started speech listening")
            } catch (e: Exception) {
                _voiceState.value = VoiceState.ERROR
                scope.launch { _errorEvents.emit("Failed to start speech recognition: ${e.message}") }
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _voiceState.value = VoiceState.PROCESSING
                _audioLevel.value = 0f
            } catch (e: Exception) {
                AstraLogger.e("VoiceManager", "Error stopping listening", e)
            }
        }
    }

    override fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                _voiceState.value = VoiceState.IDLE
                _audioLevel.value = 0f
            } catch (e: Exception) {
                AstraLogger.e("VoiceManager", "Error cancelling listening", e)
            }
        }
    }

    override fun release() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                _voiceState.value = VoiceState.IDLE
                _audioLevel.value = 0f
            } catch (e: Exception) {
                AstraLogger.e("VoiceManager", "Error releasing speech recognizer", e)
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _voiceState.value = VoiceState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            _voiceState.value = VoiceState.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB typically ranges from -2 to 10
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _audioLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _voiceState.value = VoiceState.PROCESSING
            _audioLevel.value = 0f
        }

        override fun onError(error: Int) {
            _voiceState.value = VoiceState.IDLE
            _audioLevel.value = 0f
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition engine busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                else -> "Recognition error ($error)"
            }
            AstraLogger.w("VoiceManager", "Speech error: $message ($error)")
            scope.launch { _errorEvents.emit(message) }
        }

        override fun onResults(results: Bundle?) {
            _voiceState.value = VoiceState.IDLE
            _audioLevel.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
            if (recognizedText.isNotEmpty()) {
                AstraLogger.i("VoiceManager", "Speech recognized: $recognizedText")
                scope.launch { _speechResults.emit(recognizedText) }
            }
        }

        override fun onPartialResults(partialResultsBundle: Bundle?) {
            val partials = partialResultsBundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = partials?.firstOrNull()?.trim().orEmpty()
            if (partial.isNotEmpty()) {
                scope.launch { _partialResults.emit(partial) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
