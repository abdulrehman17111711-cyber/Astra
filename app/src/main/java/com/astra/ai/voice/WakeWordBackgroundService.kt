package com.astra.ai.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Robust Android Foreground Service that runs SpeechRecognizer in the background
 * to listen continuously for hands-free wake words ("Hey Astra", "Astra", "ہائے ایسٹرا").
 *
 * Conforms to modern Android Foreground Service requirements:
 * - Uses foregroundServiceType="microphone"
 * - Displays a user-visible notification with controls to stop the service
 * - Recovers gracefully from recognition timeouts and audio device switches
 */
class WakeWordBackgroundService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        const val ACTION_START = "com.astra.ai.action.START_WAKE_WORD_SERVICE"
        const val ACTION_STOP = "com.astra.ai.action.STOP_WAKE_WORD_SERVICE"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "astra_wake_word_channel"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastDetectedWakePhrase = MutableStateFlow<String?>(null)
        val lastDetectedWakePhrase: StateFlow<String?> = _lastDetectedWakePhrase.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, WakeWordBackgroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AstraLogger.e(TAG, "Failed to start WakeWordBackgroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                AstraLogger.e(TAG, "Failed to stop WakeWordBackgroundService: ${e.message}")
            }
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isDestroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AstraLogger.i(TAG, "WakeWordBackgroundService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        AstraLogger.i(TAG, "WakeWordBackgroundService onStartCommand with action: $action")

        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground with persistent notification
        startForegroundWithNotification()
        _isRunning.value = true

        // Initialize and start SpeechRecognizer loop
        mainHandler.post {
            initSpeechRecognizer()
            startListeningLoop()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Astra Hands-Free Wake Word",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background listening status for Astra hands-free wake word."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Astra Hands-Free Active")
            .setContentText("Listening for \"Hey Astra\" in background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Listening", pendingStopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            AstraLogger.w(TAG, "SpeechRecognizer is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(WakeWordListener())
            }
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Error initializing SpeechRecognizer: ${e.message}")
        }
    }

    private fun startListeningLoop() {
        if (isDestroyed) return

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            AstraLogger.w(TAG, "RECORD_AUDIO permission not granted; stopping background wake service.")
            stopSelf()
            return
        }

        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            AstraLogger.d(TAG, "SpeechRecognizer listening for wake word...")
        } catch (e: Exception) {
            AstraLogger.e(TAG, "startListening error: ${e.message}")
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isDestroyed) return
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!isDestroyed) {
                startListeningLoop()
            }
        }, delayMs)
    }

    private fun handleDetectedSpeech(transcript: String) {
        val lower = transcript.lowercase().trim()
        val wakeRegex = Regex("(?i)\\b(hey astra|hello astra|ok astra|okay astra|astra|suno astra|ہائے ایسٹرا|ایسٹرا)\\b")

        if (wakeRegex.containsMatchIn(lower)) {
            AstraLogger.i(TAG, "Wake word detected in background: \"$transcript\"")
            _lastDetectedWakePhrase.value = transcript

            // Strip the wake word prefix to extract any accompanying command
            val cleanQuery = transcript
                .replace(Regex("(?i)^(hey astra|hello astra|ok astra|okay astra|astra|suno astra|ہائے ایسٹرا|ایسٹرا)\\s*[,:]?\\s*"), "")
                .trim()

            // Trigger Assistant Wakeup
            triggerAssistantWakeup(cleanQuery)

            // Pause listening briefly while assistant processes command
            scheduleRestart(2000)
        }
    }

    private fun triggerAssistantWakeup(remainingQuery: String) {
        try {
            val intent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_AUTO_ACTIVATE_VOICE", true)
                if (remainingQuery.isNotBlank()) {
                    putExtra("EXTRA_WAKE_QUERY", remainingQuery)
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Error triggering assistant wakeup: ${e.message}")
        }
    }

    private inner class WakeWordListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            AstraLogger.d(TAG, "SpeechRecognizer error: $error")
            // Error codes: 7 = ERROR_NO_MATCH, 6 = ERROR_SPEECH_TIMEOUT (normal continuous listening events)
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 250L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 600L
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stopSelf()
                    return
                }
                else -> 1000L
            }
            scheduleRestart(delay)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val best = matches.first()
                AstraLogger.d(TAG, "SpeechRecognizer final result: $best")
                handleDetectedSpeech(best)
            } else {
                scheduleRestart(250)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partials.isNullOrEmpty()) {
                val partial = partials.first()
                handleDetectedSpeech(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        AstraLogger.i(TAG, "WakeWordBackgroundService destroyed")
        isDestroyed = true
        _isRunning.value = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        stopForeground(true)
    }
}
