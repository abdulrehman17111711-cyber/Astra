package com.astra.ai.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status of background audio listening on the current Android device/OS version.
 */
enum class BackgroundListeningStatus(
    val title: String,
    val explanation: String
) {
    AVAILABLE(
        title = "Available",
        explanation = "Background audio capture is allowed via foreground service on this OS level."
    ),
    RESTRICTED(
        title = "Restricted by Android",
        explanation = "Android 10+ (API 29+) enforces strict OS privacy controls preventing background microphone recording."
    ),
    DISABLED(
        title = "Disabled by User",
        explanation = "Voice activation and background listening have been turned off in settings."
    )
}

/**
 * Hardware capability status for external shortcuts (headsets, volume keys).
 */
data class HardwareCapability(
    val featureName: String,
    val isSupported: Boolean,
    val limitationNote: String
)

/**
 * Configuration model for voice activation and wake phrases.
 */
data class WakeActivationConfig(
    val isVoiceActivationEnabled: Boolean = true,
    val wakePhrase: String = "Hey Astra",
    val isContinuousConversationEnabled: Boolean = true,
    val isHeadsetActivationEnabled: Boolean = true
)

/**
 * Manages in-app activation, wake-phrase detection, and reports honest
 * Android hardware and security limitations.
 */
class WakeActivationManager(private val context: Context) {

    private val _config = MutableStateFlow(WakeActivationConfig())
    val config: StateFlow<WakeActivationConfig> = _config.asStateFlow()

    private val _backgroundStatus = MutableStateFlow(evaluateBackgroundListeningStatus())
    val backgroundStatus: StateFlow<BackgroundListeningStatus> = _backgroundStatus.asStateFlow()

    fun isMicrophoneGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val isBackgroundServiceRunning: StateFlow<Boolean> = WakeWordBackgroundService.isRunning

    fun startBackgroundWakeWordService() {
        WakeWordBackgroundService.start(context)
        _backgroundStatus.value = BackgroundListeningStatus.AVAILABLE
    }

    fun stopBackgroundWakeWordService() {
        WakeWordBackgroundService.stop(context)
        _backgroundStatus.value = evaluateBackgroundListeningStatus()
    }

    /**
     * Evaluates real Android OS restrictions on background microphone listening.
     */
    fun evaluateBackgroundListeningStatus(): BackgroundListeningStatus {
        if (!_config.value.isVoiceActivationEnabled) {
            return BackgroundListeningStatus.DISABLED
        }

        if (WakeWordBackgroundService.isRunning.value) {
            return BackgroundListeningStatus.AVAILABLE
        }

        return if (isMicrophoneGranted()) BackgroundListeningStatus.AVAILABLE else BackgroundListeningStatus.RESTRICTED
    }

    /**
     * Inspects audio transcript for in-app wake phrase (e.g. "Hey Astra", "Astra").
     */
    fun matchesWakePhrase(transcript: String): Boolean {
        if (!_config.value.isVoiceActivationEnabled) return false
        val clean = transcript.trim().lowercase()
        val wake = _config.value.wakePhrase.trim().lowercase()

        return clean.startsWith(wake) || clean.contains(wake) || clean.startsWith("astra") ||
                clean.contains("ایسٹرا") || clean.contains("اسٹرا") || clean.contains("آسٹرا")
    }

    /**
     * Strips wake phrase from user command.
     */
    fun stripWakePhrase(transcript: String): String {
        return transcript
            .replace(Regex("(?i)^(?:hey astra|hello astra|ok astra|okay astra|astra|suno astra|ہائے\\s*ایسٹرا|ایسٹرا|ہائے\\s*اسٹرا|اسٹرا|ہائے\\s*آسٹرا|آسٹرا)\\s*[,:]?\\s*"), "")
            .trim()
    }

    /**
     * Returns hardware limitations transparently to the user.
     */
    fun getHardwareCapabilities(): List<HardwareCapability> {
        return listOf(
            HardwareCapability(
                featureName = "In-App Voice Activation",
                isSupported = true,
                limitationNote = "Fully supported when Astra Voice Assistant is open in foreground."
            ),
            HardwareCapability(
                featureName = "Background Microphone Capture",
                isSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q,
                limitationNote = "Android 10+ OS security prevents background microphone access without user-visible foreground service."
            ),
            HardwareCapability(
                featureName = "Volume Button Interception",
                isSupported = false,
                limitationNote = "Android system disallows capturing hardware volume keys when app is minimized without Accessibility Service."
            ),
            HardwareCapability(
                featureName = "Headset / Bluetooth Media Button",
                isSupported = true,
                limitationNote = "Supported via Android MediaSession API when audio session is active."
            )
        )
    }

    fun setVoiceActivationEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(isVoiceActivationEnabled = enabled)
        _backgroundStatus.value = evaluateBackgroundListeningStatus()
        AstraLogger.i("WakeActivationManager", "Voice activation enabled: $enabled")
    }

    fun setWakePhrase(phrase: String) {
        if (phrase.isNotBlank()) {
            _config.value = _config.value.copy(wakePhrase = phrase.trim())
            AstraLogger.i("WakeActivationManager", "Wake phrase updated: ${phrase.trim()}")
        }
    }

    fun setContinuousConversationEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(isContinuousConversationEnabled = enabled)
    }

    fun setHeadsetActivationEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(isHeadsetActivationEnabled = enabled)
    }
}
