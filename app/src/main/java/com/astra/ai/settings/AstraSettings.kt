package com.astra.ai.settings

import com.astra.ai.core.AstraConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings model for Astra AI.
 * Includes Voice Profile, Gemini AI Configuration, Volume Button Trigger,
 * and Subscription Management.
 */
data class AstraSettings(
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val autoConfirmLowRiskTools: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true,
    val trustedAutomationEnabled: Boolean = true,
    val rememberPermissionDecisions: Boolean = true,
    val safeguardLevel: String = "TRUSTED_AUTOMATION", // "STRICT", "BALANCED", "TRUSTED_AUTOMATION"
    val trustedActions: Set<String> = setOf("call_tool", "call_contact", "whatsapp_tool", "device_unlock"),
    val geminiApiKey: String = "",
    val ownerName: String = AstraConstants.CREATOR_NAME,
    val voiceMatchEnabled: Boolean = true,
    val voicePasscode: String = "Astra Unlock",
    val volumeButtonActivationEnabled: Boolean = true,
    val subscriptionTier: String = "Astra Pro",
    val subscriptionActive: Boolean = true,
    val developerName: String = AstraConstants.CREATOR_NAME,
    val appVersion: String = AstraConstants.APP_VERSION,
    val customContacts: Map<String, String> = emptyMap()
)

interface SettingsRepository {
    val settings: StateFlow<AstraSettings>
    fun updateSpeechRate(rate: Float)
    fun updatePitch(pitch: Float)
    fun setAutoConfirmLowRisk(enabled: Boolean)
    fun setHapticFeedback(enabled: Boolean)
    fun setDarkTheme(enabled: Boolean)
    fun setTrustedAutomation(enabled: Boolean)
    fun setRememberPermissionDecisions(enabled: Boolean)
    fun setSafeguardLevel(level: String)
    fun addTrustedAction(actionKey: String)
    fun removeTrustedAction(actionKey: String)
    fun clearTrustedActions()
    fun setGeminiApiKey(key: String)
    fun setOwnerName(name: String)
    fun setVoiceMatchEnabled(enabled: Boolean)
    fun setVoicePasscode(passcode: String)
    fun setVolumeButtonActivation(enabled: Boolean)
    fun setCustomContact(name: String, number: String)
    fun removeCustomContact(name: String)
}

class DefaultSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AstraSettings())
    override val settings: StateFlow<AstraSettings> = _settings.asStateFlow()

    override fun updateSpeechRate(rate: Float) {
        _settings.value = _settings.value.copy(ttsSpeechRate = rate)
    }

    override fun updatePitch(pitch: Float) {
        _settings.value = _settings.value.copy(ttsPitch = pitch)
    }

    override fun setAutoConfirmLowRisk(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoConfirmLowRiskTools = enabled)
    }

    override fun setHapticFeedback(enabled: Boolean) {
        _settings.value = _settings.value.copy(hapticFeedbackEnabled = enabled)
    }

    override fun setDarkTheme(enabled: Boolean) {
        _settings.value = _settings.value.copy(darkThemeEnabled = enabled)
    }

    override fun setTrustedAutomation(enabled: Boolean) {
        _settings.value = _settings.value.copy(trustedAutomationEnabled = enabled)
    }

    override fun setRememberPermissionDecisions(enabled: Boolean) {
        _settings.value = _settings.value.copy(rememberPermissionDecisions = enabled)
    }

    override fun setSafeguardLevel(level: String) {
        _settings.value = _settings.value.copy(safeguardLevel = level)
    }

    override fun addTrustedAction(actionKey: String) {
        _settings.value = _settings.value.copy(trustedActions = _settings.value.trustedActions + actionKey)
    }

    override fun removeTrustedAction(actionKey: String) {
        _settings.value = _settings.value.copy(trustedActions = _settings.value.trustedActions - actionKey)
    }

    override fun clearTrustedActions() {
        _settings.value = _settings.value.copy(trustedActions = emptySet())
    }

    override fun setGeminiApiKey(key: String) {
        _settings.value = _settings.value.copy(geminiApiKey = key.trim())
    }

    override fun setOwnerName(name: String) {
        _settings.value = _settings.value.copy(ownerName = name.trim())
    }

    override fun setVoiceMatchEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(voiceMatchEnabled = enabled)
    }

    override fun setVoicePasscode(passcode: String) {
        _settings.value = _settings.value.copy(voicePasscode = passcode.trim())
    }

    override fun setVolumeButtonActivation(enabled: Boolean) {
        _settings.value = _settings.value.copy(volumeButtonActivationEnabled = enabled)
    }

    override fun setCustomContact(name: String, number: String) {
        val updated = _settings.value.customContacts.toMutableMap()
        updated[name.trim()] = number.trim()
        _settings.value = _settings.value.copy(customContacts = updated)
    }

    override fun removeCustomContact(name: String) {
        val updated = _settings.value.customContacts.toMutableMap()
        updated.remove(name.trim())
        _settings.value = _settings.value.copy(customContacts = updated)
    }
}
