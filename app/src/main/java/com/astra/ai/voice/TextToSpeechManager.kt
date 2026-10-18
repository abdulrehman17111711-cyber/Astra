package com.astra.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Robust, lifecycle-safe Text-to-Speech manager using Android TextToSpeech.
 * Provides speech rate/pitch calibration, locale selection, utterance tracking,
 * and clean shutdown.
 */
class TextToSpeechManager(context: Context) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pendingCompletionCallback: (() -> Unit)? = null
    private var currentSpeechRate: Float = 1.0f
    private var currentPitch: Float = 1.0f

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    // Set default language
                    val localeResult = engine.setLanguage(Locale.US)
                    if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        AstraLogger.w("TextToSpeechManager", "Default US English TTS language not supported or missing data")
                    }

                    // Query available engine voices
                    try {
                        val voices = engine.voices?.toList() ?: emptyList()
                        _availableVoices.value = voices
                        AstraLogger.d("TextToSpeechManager", "Loaded ${voices.size} TTS voices")
                    } catch (e: Exception) {
                        AstraLogger.w("TextToSpeechManager", "Could not query TTS voices: ${e.message}")
                    }

                    // Utterance tracking
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                            AstraLogger.d("TextToSpeechManager", "Started speech utterance: $utteranceId")
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            AstraLogger.d("TextToSpeechManager", "Completed speech utterance: $utteranceId")
                            val cb = pendingCompletionCallback
                            pendingCompletionCallback = null
                            cb?.invoke()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            pendingCompletionCallback = null
                            AstraLogger.w("TextToSpeechManager", "Error during utterance: $utteranceId")
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            pendingCompletionCallback = null
                            AstraLogger.w("TextToSpeechManager", "TTS error code $errorCode on utterance $utteranceId")
                        }
                    })

                    engine.setSpeechRate(currentSpeechRate)
                    engine.setPitch(currentPitch)
                    _isReady.value = true
                    AstraLogger.i("TextToSpeechManager", "TextToSpeech Engine initialized successfully")
                }
            } else {
                AstraLogger.e("TextToSpeechManager", "TTS initialization failed with status $status")
            }
        }
    }

    /**
     * Speaks the provided text and invokes onDone when speech completes.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null || !_isReady.value || text.isBlank()) {
            AstraLogger.d("TextToSpeechManager", "Engine not ready or text blank, invoking onDone immediately")
            onDone?.invoke()
            return
        }

        stop() // Flush any ongoing speech
        pendingCompletionCallback = onDone
        val utteranceId = UUID.randomUUID().toString()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            AstraLogger.w("TextToSpeechManager", "engine.speak returned code $result")
            _isSpeaking.value = false
            pendingCompletionCallback = null
            onDone?.invoke()
        }
    }

    /**
     * Stops current speech output immediately.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        val cb = pendingCompletionCallback
        pendingCompletionCallback = null
        cb?.invoke()
    }

    /**
     * Configures speech rate (0.5x - 2.0x).
     */
    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(currentSpeechRate)
    }

    /**
     * Configures speech pitch (0.5x - 2.0x).
     */
    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(currentPitch)
    }

    /**
     * Configures a specific voice from available voices.
     */
    fun setVoice(voice: Voice) {
        try {
            tts?.voice = voice
            AstraLogger.i("TextToSpeechManager", "Voice set to: ${voice.name}")
        } catch (e: Exception) {
            AstraLogger.e("TextToSpeechManager", "Failed to set voice", e)
        }
    }

    /**
     * Releases TTS resources on application teardown.
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        AstraLogger.i("TextToSpeechManager", "TTS Engine shutdown")
    }
}
