package com.astra.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Production Android TextToSpeech engine wrapper for Astra AI.
 */
class AndroidTtsManager(context: Context) : TtsManager {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pendingCompletionCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val result = engine.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        AstraLogger.w("TtsManager", "US English TTS language not supported/missing data")
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            val cb = pendingCompletionCallback
                            pendingCompletionCallback = null
                            cb?.invoke()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            pendingCompletionCallback = null
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            pendingCompletionCallback = null
                            AstraLogger.w("TtsManager", "TTS error code: $errorCode")
                        }
                    })
                    _isReady.value = true
                    AstraLogger.i("TtsManager", "TTS Engine initialized successfully")
                }
            } else {
                AstraLogger.e("TtsManager", "TTS Engine initialization failed with status $status")
            }
        }
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        val engine = tts
        if (engine == null || !_isReady.value || text.isBlank()) {
            onDone?.invoke()
            return
        }

        pendingCompletionCallback = onDone
        val utteranceId = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        pendingCompletionCallback = null
    }

    override fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    override fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
    }
}
